package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.util.BlockDisplayParticleContext;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redstone wire: allow slab top faces to count as valid ground for placement/survival, and keep
 * powered dust on the wire's frozen visual height.
 */
@Mixin(RedstoneWireBlock.class)
public abstract class RedstoneWireBlockMixin {

    @Inject(method = "canSurvive",
            at = @At("HEAD"), cancellable = true)
    private void slabbed$canPlaceAt(BlockState state, LevelReader world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SlabSupport.isRedstoneSupportTopSurface(world, pos.below())) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Invariant: {@code spawnParticlesAlongLine} is private static and every one of its call sites
     * is inside the wire's block display tick, so this hook owns the emission and declares it — the
     * display-tick funnel in {@code BlockDisplayParticleMixin} must not translate it a second time.
     * It reads the same {@code SlabSupport.getYOffset} path as every other particle hook, so one
     * read path answers for all of them (maintainer ruling, 2026-09-06).
     */
    @WrapOperation(
            method = "spawnParticlesAlongLine",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addParticle"
                            + "(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
            ),
            require = 1
    )
    private static void slabbed$translatePoweredDustParticleY(
            Level level, ParticleOptions options,
            double x, double y, double z,
            double vx, double vy, double vz,
            Operation<Void> original,
            @Local(argsOnly = true) BlockPos pos) {
        double frozenDy = SlabSupport.getYOffset(level, pos, level.getBlockState(pos));
        double shiftedY = Double.isFinite(frozenDy) ? y + frozenDy : y;
        BlockDisplayParticleContext.beginOwnedEmission();
        try {
            original.call(level, options, x, shiftedY, z, vx, vy, vz);
        } finally {
            BlockDisplayParticleContext.endOwnedEmission();
        }
    }
}
