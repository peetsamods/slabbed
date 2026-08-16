package com.slabbed.test.mixin;

import com.slabbed.test.LeverParticleFrozenAnchorClientGameTest;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observation-only capture for the real client particle sink. */
@Mixin(ClientWorld.class)
public abstract class ClientWorldParticleCaptureMixin {

    @Inject(
            method = "addParticleClient(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V",
            at = @At("HEAD")
    )
    private void slabbed$captureLeverRedstoneParticle(
            ParticleEffect effect,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            CallbackInfo ci) {
        if (effect instanceof DustParticleEffect dust) {
            LeverParticleFrozenAnchorClientGameTest.captureDustParticle(
                    dust, x, y, z, velocityX, velocityY, velocityZ);
        }
    }
}
