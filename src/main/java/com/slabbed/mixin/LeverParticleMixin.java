package com.slabbed.mixin;

import com.slabbed.particle.BlockDisplayParticleContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeverBlock;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps both ambient and click dust attached to the lever's stored visual height. */
@Mixin(LeverBlock.class)
public abstract class LeverParticleMixin {

    @Redirect(
            method = "spawnParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/WorldAccess;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
            )
    )
    private static void slabbed$offsetLeverDust(
            WorldAccess redirectWorld, ParticleEffect effect,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            BlockState state, WorldAccess world, BlockPos pos, float scale
    ) {
        double translatedY = BlockDisplayParticleContext.isActive()
                ? y
                : BlockDisplayParticleContext.translateY(world, pos, state, y);
        redirectWorld.addParticle(effect, x, translatedY, z, velocityX, velocityY, velocityZ);
    }
}
