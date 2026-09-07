package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps redstone-torch burnout smoke (level event 1502) at the torch's frozen visual height.
 *
 * <p>No call-site ordinal: the index of the 1502 case's {@code addParticle} among the method's
 * particle calls is not stable across Minecraft versions (12 on 26.2, 11 on 26.3-pre-2), and a
 * redirect pinned to a stale index applies cleanly to a different event's particle and silently
 * leaves the burnout smoke at grid height. Every particle call in the handler is redirected and
 * only the burnout event is shifted; the others pass through unchanged. The client proof row
 * asserts the burnout heights, so a target that stops reaching the 1502 case fails the run.
 */
@Mixin(LevelEventHandler.class)
public abstract class LevelEventParticleMixin {
    private static final int REDSTONE_TORCH_BURNOUT_EVENT = 1502;

    @Redirect(
            method = "levelEvent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
            ),
            require = 1
    )
    private void slabbed$offsetRedstoneTorchBurnoutSmoke(
            ClientLevel level, ParticleOptions options,
            double x, double y, double z,
            double xVelocity, double yVelocity, double zVelocity,
            int eventId, BlockPos pos, int data) {
        double dy = eventId == REDSTONE_TORCH_BURNOUT_EVENT
                ? SlabSupport.getYOffset(level, pos, level.getBlockState(pos))
                : 0.0d;
        level.addParticle(
                options, x, y + (Double.isFinite(dy) ? dy : 0.0d), z,
                xVelocity, yVelocity, zVelocity);
    }
}
