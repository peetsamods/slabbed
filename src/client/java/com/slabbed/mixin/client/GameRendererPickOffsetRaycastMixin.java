package com.slabbed.mixin.client;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The single ownership rule for Slabbed crosshair targeting (MC 1.21.1).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPickOffsetRaycastMixin {

    private static final String SABLE_MOD_ID = "sable";
    private static long slabbed$lastTraceNanos;

    @Redirect(
            method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"
            )
    )
    private HitResult slabbed$offsetAwarePick(Entity camera, double maxDistance, float tickDelta, boolean includeFluids) {
        if (!SlabbedOffsetRaycast.ENABLED
                || includeFluids
                || !(camera.level() instanceof ClientLevel level)) {
            return camera.pick(maxDistance, tickDelta, includeFluids);
        }

        Vec3 eye = camera.getEyePosition(tickDelta);
        Vec3 look = camera.getViewVector(tickDelta);
        Vec3 end = eye.add(look.x * maxDistance, look.y * maxDistance, look.z * maxDistance);
        BlockHitResult offset = SlabbedOffsetRaycast.raycast(level, eye, end, CollisionContext.of(camera));
        boolean externalOwnerLoaded = ModList.get().isLoaded(SABLE_MOD_ID);
        HitResult externalHit = externalOwnerLoaded
                ? camera.pick(maxDistance, tickDelta, includeFluids)
                : null;
        HitResult selected = SlabbedOffsetRaycast.selectNearestOwnedHit(eye, externalHit, offset);

        if (SlabbedOffsetRaycast.TRACE) {
            HitResult traceBaseline = externalOwnerLoaded
                    ? externalHit
                    : camera.pick(maxDistance, tickDelta, includeFluids);
            slabbed$traceDivergence(traceBaseline, selected);
        }
        return selected;
    }

    private static void slabbed$traceDivergence(HitResult vanilla, HitResult offset) {
        boolean diverged = vanilla.getType() != offset.getType()
                || (vanilla instanceof BlockHitResult blockHit
                        && (!(offset instanceof BlockHitResult offsetBlock)
                                || !blockHit.getBlockPos().equals(offsetBlock.getBlockPos())
                                || blockHit.getDirection() != offsetBlock.getDirection()));
        if (!diverged) {
            return;
        }
        long now = System.nanoTime();
        if (now - slabbed$lastTraceNanos < 200_000_000L) {
            return;
        }
        slabbed$lastTraceNanos = now;
        Slabbed.LOGGER.info(
                "[offset-raycast] vanilla={} {} {} | offset={} {} {}",
                vanilla.getType(),
                vanilla instanceof BlockHitResult vanillaBlockPos ? vanillaBlockPos.getBlockPos() : "-",
                vanilla instanceof BlockHitResult vanillaBlockDir ? vanillaBlockDir.getDirection() : "-",
                offset.getType(),
                offset instanceof BlockHitResult offsetBlockPos ? offsetBlockPos.getBlockPos() : "-",
                offset instanceof BlockHitResult offsetBlockDir ? offsetBlockDir.getDirection() : "-");
    }
}
