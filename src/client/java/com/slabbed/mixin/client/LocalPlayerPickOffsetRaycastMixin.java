package com.slabbed.mixin.client;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Installs the offset-aware nearest-hit block raycast as the single ownership rule for
 * Slabbed crosshair targeting on MC 26.2.
 *
 * <p>The vanilla crosshair pick is {@code Minecraft.pick(float)} -&gt;
 * {@code LocalPlayer.raycastHitResult(float, Entity)} -&gt; the private static
 * {@code LocalPlayer.pick(Entity, double, double, float)}, where the block portion is a
 * single {@code entity.pick(maxDistance, tickProgress, false)} call. That call delegates to
 * {@code Level.clip}, whose voxel DDA returns the <em>first cell</em> hit rather than the
 * nearest hit, which mistargets / misses vertically offset blocks (see
 * {@link SlabbedOffsetRaycast}).
 *
 * <p>This redirect replaces <em>only</em> that block raycast with the offset-aware
 * nearest-hit raycast, preserving the vanilla block-vs-entity merge, reach clamp, and
 * fluid handling around it. With it active, {@code Minecraft.hitResult} is the honest
 * visible-owner hit directly, so the outline renderer, the {@code [slabdy]} HUD, the pick
 * owner, and the placement face all agree (WYSIWYG). The legacy post-hoc retarget
 * ({@code GameRendererCrosshairRetargetMixin}) is disabled while this is enabled.
 *
 * <p>Gated by {@link SlabbedOffsetRaycast#ENABLED} ({@code -Dslabbed.offsetRaycast=false}
 * restores the legacy retarget lanes for live A/B).
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerPickOffsetRaycastMixin {

    private static long slabbed$lastTraceNanos;

    @Redirect(
            method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"))
    private static HitResult slabbed$offsetBlockPick(
            Entity entity, double maxDistance, float tickProgress, boolean includeFluids) {
        if (!SlabbedOffsetRaycast.ENABLED
                || includeFluids
                || entity == null
                || !(entity.level() instanceof ClientLevel level)) {
            return entity.pick(maxDistance, tickProgress, includeFluids);
        }

        Vec3 eye = entity.getEyePosition(tickProgress);
        Vec3 view = entity.getViewVector(tickProgress);
        Vec3 end = eye.add(view.x * maxDistance, view.y * maxDistance, view.z * maxDistance);
        BlockHitResult offset = SlabbedOffsetRaycast.raycast(level, eye, end, CollisionContext.of(entity));

        if (SlabbedOffsetRaycast.TRACE) {
            slabbed$traceDivergence(entity, maxDistance, tickProgress, includeFluids, offset);
        }
        return offset;
    }

    private static void slabbed$traceDivergence(
            Entity entity, double maxDistance, float tickProgress, boolean includeFluids, BlockHitResult offset) {
        HitResult vanilla = entity.pick(maxDistance, tickProgress, includeFluids);
        boolean diverged = vanilla.getType() != offset.getType()
                || (vanilla instanceof BlockHitResult vb
                        && (!vb.getBlockPos().equals(offset.getBlockPos())
                                || vb.getDirection() != offset.getDirection()));
        if (!diverged) {
            return;
        }
        long now = System.nanoTime();
        if (now - slabbed$lastTraceNanos < 200_000_000L) {
            return; // throttle to ~5 lines/sec
        }
        slabbed$lastTraceNanos = now;
        Slabbed.LOGGER.info(
                "[offset-raycast] vanilla={} {} {} | offset={} {} {}",
                vanilla.getType(),
                vanilla instanceof BlockHitResult vb2 ? vb2.getBlockPos() : "-",
                vanilla instanceof BlockHitResult vb3 ? vb3.getDirection() : "-",
                offset.getType(),
                offset.getType() == HitResult.Type.BLOCK ? offset.getBlockPos() : "-",
                offset.getType() == HitResult.Type.BLOCK ? offset.getDirection() : "-");
    }
}
