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
 * Emits the campfire's smoke column from the campfire's DRAWN height, so a lowered campfire does
 * not smoke from the air above itself (maintainer ruling, 2026-09-01: WYSIWYG applies to emitted
 * effects).
 *
 * <p>Invariant: this mixin covers {@code makeParticles} ONLY, which vanilla reaches from dousing
 * and from the block-entity tick — both outside the block display tick. The campfire's crackle is
 * emitted inside the display tick and is owned by the funnel in
 * {@code BlockDisplayParticleMixin}; do not re-add a per-block hook for it.
 */
@Mixin(CampfireBlock.class)
public abstract class CampfireParticleMixin {

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
