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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A lowered candle's flame must burn on its wick, not at the unlowered grid height: the
 * geometry legs all follow dy while vanilla's emitter is cell-anchored (the hovering-flame
 * class the torch mixins already correct). Targeting the abstract base covers plain, dyed,
 * and cake candles in one seam.
 */
@Mixin(AbstractCandleBlock.class)
public abstract class CandleParticleMixin {

    @Shadow
    protected abstract Iterable<Vec3> getParticleOffsets(BlockState state);

    @Shadow
    private static void addParticlesAndSound(Level level, Vec3 pos, RandomSource random) {
        throw new AssertionError("shadowed");
    }

    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void slabbed$lowerCandleParticles(
            BlockState state,
            Level level,
            BlockPos pos,
            RandomSource random,
            CallbackInfo ci
    ) {
        double dy = SlabSupport.getYOffset(level, pos, state);
        if (dy == 0.0d) {
            return;
        }
        ci.cancel();
        if (!state.getValue(AbstractCandleBlock.LIT)) {
            return;
        }
        for (Vec3 offset : getParticleOffsets(state)) {
            addParticlesAndSound(
                    level,
                    offset.add(pos.getX(), pos.getY() + dy, pos.getZ()),
                    random);
        }
    }
}
