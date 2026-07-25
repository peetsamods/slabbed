package com.slabbed.test.mixin;

import com.slabbed.test.LeverParticleFrozenAnchorClientGameTest;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observation-only TEST31 probe for the real client particle sink. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelParticleCaptureMixin {
    @Inject(
            method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD")
    )
    private void slabbed$captureLeverRedstoneParticle(
            ParticleOptions options,
            double x,
            double y,
            double z,
            double xVelocity,
            double yVelocity,
            double zVelocity,
            CallbackInfo ci) {
        if (options instanceof DustParticleOptions dust) {
            LeverParticleFrozenAnchorClientGameTest.captureDustParticle(
                    dust, x, y, z, xVelocity, yVelocity, zVelocity);
        }
    }
}
