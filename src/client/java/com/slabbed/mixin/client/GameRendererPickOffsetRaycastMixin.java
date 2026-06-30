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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The single ownership rule for Slabbed crosshair targeting (MC 1.21.1).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPickOffsetRaycastMixin {

    private static long slabbed$lastTraceNanos;

    @Redirect(
            method = "pick(F)V",
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

        if (SlabbedOffsetRaycast.TRACE) {
            slabbed$traceDivergence(camera, maxDistance, tickDelta, includeFluids, offset);
        }
        return offset;
    }

    private static void slabbed$traceDivergence(
            Entity camera, double maxDistance, float tickDelta, boolean includeFluids, BlockHitResult offset) {
        HitResult vanilla = camera.pick(maxDistance, tickDelta, includeFluids);
        boolean diverged = vanilla.getType() != offset.getType()
                || (vanilla instanceof BlockHitResult blockHit
                        && (!blockHit.getBlockPos().equals(offset.getBlockPos())
                                || blockHit.getDirection() != offset.getDirection()));
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
                offset.getType() == HitResult.Type.BLOCK ? offset.getBlockPos() : "-",
                offset.getType() == HitResult.Type.BLOCK ? offset.getDirection() : "-");
    }
}
