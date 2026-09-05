package com.slabbed.mixin;

import com.slabbed.particle.BlockDisplayParticleContext;
import net.minecraft.block.AbstractCandleBlock;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps extinguish smoke at the candle's stored visual height. */
@Mixin(AbstractCandleBlock.class)
public abstract class CandleExtinguishParticleMixin {

    @Redirect(
            method = "method_35244",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/WorldAccess;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
            )
    )
    private static void slabbed$offsetExtinguishSmoke(
            WorldAccess redirectWorld, ParticleEffect effect,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            WorldAccess world, BlockPos pos, Vec3d flameOffset
    ) {
        redirectWorld.addParticle(effect, x,
                BlockDisplayParticleContext.translateY(
                        world, pos, world.getBlockState(pos), y),
                z, velocityX, velocityY, velocityZ);
    }
}
