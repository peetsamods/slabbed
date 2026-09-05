package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.particle.BlockDisplayParticleContext;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Translates only the smoke emitted by redstone-torch burnout world event 1502. */
@Mixin(WorldRenderer.class)
public abstract class RedstoneBurnoutParticleMixin {

    @WrapOperation(
            method = "processWorldEvent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/world/ClientWorld;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
            )
    )
    private void slabbed$offsetBurnoutSmoke(
            ClientWorld world, ParticleEffect effect,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            Operation<Void> original,
            @Local(argsOnly = true, ordinal = 0) int eventId,
            @Local(argsOnly = true) BlockPos pos
    ) {
        double translatedY = eventId == 1502
                ? BlockDisplayParticleContext.translateY(
                        world, pos, world.getBlockState(pos), y)
                : y;
        original.call(world, effect, x, translatedY, z, velocityX, velocityY, velocityZ);
    }
}
