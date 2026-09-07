package com.slabbed.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.test.AmbientParticleFrozenDyClientGameTest;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Observation-only probe for the client particle sink, placed DOWNSTREAM of every Y translation.
 *
 * <p>Invariant: this is a SEPARATE probe from {@code ClientLevelParticleCaptureMixin}, which sits at
 * the head of the public particle entry point and is therefore upstream of the sink's
 * camera-distance and particle-status culls. Do not merge the two: the upstream probe is what lets
 * the older client proofs use fixtures far from the camera, and this one is what lets a proof
 * observe a value the sink itself produced (maintainer ruling, 2026-09-06).
 *
 * <p>The wrapped call has two occurrences in the sink, one per branch, and both are real emissions,
 * so this carries no ordinal and no raised require — either branch must be observed.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelSinkParticleCaptureMixin {

    @WrapOperation(
            method = "doAddParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleEngine;createParticle("
                            + "Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)"
                            + "Lnet/minecraft/client/particle/Particle;"
            )
    )
    private Particle slabbed$captureSinkParticle(
            ParticleEngine engine, ParticleOptions options,
            double x, double y, double z,
            double xVelocity, double yVelocity, double zVelocity,
            Operation<Particle> original) {
        AmbientParticleFrozenDyClientGameTest.captureParticle(
                options, x, y, z, xVelocity, yVelocity, zVelocity);
        return original.call(engine, options, x, y, z, xVelocity, yVelocity, zVelocity);
    }
}
