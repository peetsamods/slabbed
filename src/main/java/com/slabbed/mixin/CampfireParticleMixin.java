package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Emits campfire particles from the campfire's DRAWN height. Torch, lever and candle particles
 * already follow their block down; the campfire's crackle ({@code animateTick}) and smoke column
 * ({@code makeParticles}) spawned at grid height, so a lowered campfire smoked from the air
 * above itself.
 */
@Mixin(CampfireBlock.class)
public abstract class CampfireParticleMixin {

    @WrapOperation(
            method = "animateTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private static void slabbed$offsetCrackle(
            Level level, ParticleOptions type,
            double x, double y, double z, double vx, double vy, double vz,
            Operation<Void> original,
            @Local(argsOnly = true) BlockPos pos
    ) {
        original.call(level, type, x, y + slabbed$campfireDy(level, pos), z, vx, vy, vz);
    }

    @WrapOperation(
            method = "makeParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"))
    private static void slabbed$offsetCosySmoke(
            Level level, ParticleOptions type, boolean alwaysVisible,
            double x, double y, double z, double vx, double vy, double vz,
            Operation<Void> original,
            @Local(argsOnly = true) BlockPos pos
    ) {
        original.call(level, type, alwaysVisible,
                x, y + slabbed$campfireDy(level, pos), z, vx, vy, vz);
    }

    @WrapOperation(
            method = "makeParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private static void slabbed$offsetExtraSmoke(
            Level level, ParticleOptions type,
            double x, double y, double z, double vx, double vy, double vz,
            Operation<Void> original,
            @Local(argsOnly = true) BlockPos pos
    ) {
        original.call(level, type, x, y + slabbed$campfireDy(level, pos), z, vx, vy, vz);
    }

    private static double slabbed$campfireDy(Level level, BlockPos pos) {
        double dy = SlabSupport.getYOffset(level, pos, level.getBlockState(pos));
        return Double.isFinite(dy) ? dy : 0.0d;
    }
}
