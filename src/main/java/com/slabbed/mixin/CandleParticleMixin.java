package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Port of the 1.21.11 candle particle fix (donor {@code CandleParticleMixin}, commit {@code 11574591}):
 * a LOWERED candle or candle cake renders its model by dy but vanilla's flame/smoke particles spawn at
 * grid height — the flame floats 0.5 above a lowered candle. Same "particles too high" class as the
 * torch fixes; F5's per-emitter shape (no shared spawn funnel exists to hook once).
 *
 * <p>{@link AbstractCandleBlock} is shared by {@code CandleBlock} and {@code CandleCakeBlock} (neither
 * overrides {@code animateTick}), so one mixin covers both. The spawn helper
 * {@code addParticlesAndSound(Level, Vec3, RandomSource)} has no BlockPos in scope — the pos lives one
 * frame up in the private per-offset dispatcher, which under Mojang mappings is the synthetic
 * {@code lambda$animateTick$0(Level, BlockPos, RandomSource, Vec3)} (the donor targeted the same method
 * as intermediary {@code method_31611}). Redirect the helper call from inside that dispatcher, where
 * BlockPos is available to resolve dy. Vanilla's smoke-chance/jitter/sound formula is preserved
 * untouched; strict no-op when dy == 0.
 */
@Mixin(AbstractCandleBlock.class)
public abstract class CandleParticleMixin {

    @Shadow
    private static void addParticlesAndSound(Level level, Vec3 pos, RandomSource random) {
        throw new AssertionError();
    }

    @Redirect(
            method = "lambda$animateTick$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/AbstractCandleBlock;addParticlesAndSound(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/RandomSource;)V"
            )
    )
    private static void slabbed$offsetCandleParticles(Level level, Vec3 spawnPos, RandomSource random,
                                                      Level capturedLevel, BlockPos blockPos,
                                                      RandomSource capturedRandom, Vec3 flameOffset) {
        BlockState state = capturedLevel.getBlockState(blockPos);
        double dy = SlabSupport.getYOffset(capturedLevel, blockPos, state);
        addParticlesAndSound(level, dy == 0.0 ? spawnPos : spawnPos.add(0.0, dy, 0.0), random);
    }
}
