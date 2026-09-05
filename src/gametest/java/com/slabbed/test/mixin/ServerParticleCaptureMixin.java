package com.slabbed.test.mixin;

import com.slabbed.test.ServerParticleCapture;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes server-origin particle requests without changing them. */
@Mixin(ServerWorld.class)
public abstract class ServerParticleCaptureMixin {

    @Inject(
            method = "spawnParticles(Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I",
            at = @At("HEAD")
    )
    private void slabbed$captureServerParticles(
            ParticleEffect effect, double x, double y, double z, int count,
            double spreadX, double spreadY, double spreadZ, double speed,
            CallbackInfoReturnable<Integer> callback
    ) {
        ServerParticleCapture.record(effect, x, y, z, count,
                spreadX, spreadY, spreadZ, speed);
    }
}
