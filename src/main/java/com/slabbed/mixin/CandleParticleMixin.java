package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.AbstractCandleBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A LOWERED candle or candle cake renders its model by dy (OffsetBlockStateModel applies
 * getVisualYOffset to every block) but vanilla's smoke/flame particles spawn at grid height —
 * the same "particles too high" bug class fixed for redstone torches (RedstoneTorchParticleMixin).
 *
 * <p>{@link AbstractCandleBlock} is shared by both {@code CandleBlock} and {@code CandleCakeBlock}
 * (neither overrides {@code randomDisplayTick}), so one mixin here covers both. Vanilla's actual
 * particle-spawning helper, {@code spawnCandleParticles(World, Vec3d, Random)}, has no
 * BlockState/BlockPos in scope — only an already-absolute {@code Vec3d}. The real {@code BlockPos}
 * lives one call up, in the private per-offset dispatcher (yarn: {@code method_31611(World,
 * BlockPos, Random, Vec3d)}, invoked once per candle flame from {@code randomDisplayTick}'s
 * {@code forEach}), so this redirects the {@code spawnCandleParticles} call from inside THAT method,
 * where {@code BlockPos} is available to resolve dy.
 *
 * <p>Same safe approach as the torch mixins: redirect and add {@code dy} to Y, never re-implement
 * vanilla's smoke-chance/jitter/sound-gate formula. Strict no-op when dy == 0.
 */
@Mixin(AbstractCandleBlock.class)
public abstract class CandleParticleMixin {

    @Shadow
    private static void spawnCandleParticles(World world, Vec3d pos, Random random) {
        throw new AssertionError();
    }

    @Redirect(
            method = "method_31611",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/AbstractCandleBlock;spawnCandleParticles(Lnet/minecraft/world/World;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/random/Random;)V"
            )
    )
    private static void slabbed$offsetCandleParticles(World world, Vec3d pos, Random random,
                                                       World capturedWorld, BlockPos blockPos,
                                                       Random capturedRandom, Vec3d offset) {
        BlockState state = capturedWorld.getBlockState(blockPos);
        double dy = SlabSupport.getVisualYOffset(capturedWorld, blockPos, state);
        spawnCandleParticles(world, dy == 0.0 ? pos : pos.add(0.0, dy, 0.0), random);
    }
}
