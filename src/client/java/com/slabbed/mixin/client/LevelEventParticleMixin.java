package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps redstone-torch burnout smoke at the torch's frozen visual height. */
@Mixin(LevelEventHandler.class)
public abstract class LevelEventParticleMixin {
    private static final int REDSTONE_TORCH_BURNOUT_EVENT = 1502;

    @Redirect(
            method = "levelEvent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
                    ordinal = 12
            ),
            require = 1,
            expect = 1,
            allow = 1
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
