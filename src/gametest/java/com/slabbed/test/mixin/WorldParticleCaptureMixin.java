package com.slabbed.test.mixin;

import com.slabbed.test.ParticleCapture;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes vanilla particle calls for GameTest without cancelling or rewriting them. */
@Mixin(World.class)
public abstract class WorldParticleCaptureMixin {

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V", at = @At("HEAD"))
    private void slabbed$recordParticle(
            ParticleEffect effect,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            CallbackInfo callback
    ) {
        ParticleCapture.record(effect, false, false, x, y, z, velocityX, velocityY, velocityZ);
    }

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;ZDDDDDD)V", at = @At("HEAD"))
    private void slabbed$recordParticleWithOverride(
            ParticleEffect effect, boolean alwaysSpawn,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            CallbackInfo callback
    ) {
        ParticleCapture.record(effect, false, alwaysSpawn, x, y, z, velocityX, velocityY, velocityZ);
    }

    @Inject(method = "addImportantParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V", at = @At("HEAD"))
    private void slabbed$recordImportantParticle(
            ParticleEffect effect,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            CallbackInfo callback
    ) {
        ParticleCapture.record(effect, true, false, x, y, z, velocityX, velocityY, velocityZ);
    }

    @Inject(method = "addImportantParticle(Lnet/minecraft/particle/ParticleEffect;ZDDDDDD)V", at = @At("HEAD"))
    private void slabbed$recordImportantParticleWithOverride(
            ParticleEffect effect, boolean alwaysSpawn,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            CallbackInfo callback
    ) {
        ParticleCapture.record(effect, true, alwaysSpawn, x, y, z, velocityX, velocityY, velocityZ);
    }
}
