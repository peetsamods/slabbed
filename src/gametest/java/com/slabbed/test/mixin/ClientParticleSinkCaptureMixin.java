package com.slabbed.test.mixin;

import com.slabbed.test.ParticleSinkAudit;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Records final client-renderer particle coordinates without changing them. */
@Mixin(ClientWorld.class)
public abstract class ClientParticleSinkCaptureMixin {

    @Redirect(
            method = {
                    "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V",
                    "addParticle(Lnet/minecraft/particle/ParticleEffect;ZDDDDDD)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;addParticle(Lnet/minecraft/particle/ParticleEffect;ZDDDDDD)V"
            )
    )
    private void slabbed$captureParticleSink(
            WorldRenderer renderer, ParticleEffect effect, boolean alwaysSpawn,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ
    ) {
        ParticleSinkAudit.record(effect, false, alwaysSpawn,
                x, y, z, velocityX, velocityY, velocityZ);
        renderer.addParticle(effect, alwaysSpawn, x, y, z, velocityX, velocityY, velocityZ);
    }

    @Redirect(
            method = {
                    "addImportantParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V",
                    "addImportantParticle(Lnet/minecraft/particle/ParticleEffect;ZDDDDDD)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;addParticle(Lnet/minecraft/particle/ParticleEffect;ZZDDDDDD)V"
            )
    )
    private void slabbed$captureImportantParticleSink(
            WorldRenderer renderer, ParticleEffect effect, boolean alwaysSpawn, boolean important,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ
    ) {
        ParticleSinkAudit.record(effect, important, alwaysSpawn,
                x, y, z, velocityX, velocityY, velocityZ);
        renderer.addParticle(effect, alwaysSpawn, important,
                x, y, z, velocityX, velocityY, velocityZ);
    }
}
