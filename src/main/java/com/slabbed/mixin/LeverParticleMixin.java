package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A lowered lever's redstone particle must appear at the lever, not at its unlowered grid
 * height. Same hovering-emitter class the torch and candle mixins already correct: the geometry
 * legs follow dy while the emitter stays cell-anchored.
 *
 * <p>Wrapping the emit rather than the tick is deliberate. The position is built by one private
 * helper with two callers - the ambient tick and the flash when the lever is thrown - so an
 * injection on the tick would correct the ambient particle and leave the flash behind.
 */
@Mixin(LeverBlock.class)
public abstract class LeverParticleMixin {

    @WrapOperation(
            method = "makeParticle",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;addParticle"
                            + "(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
            )
    )
    private static void slabbed$lowerLeverParticle(
            LevelAccessor level,
            ParticleOptions options,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            Operation<Void> original,
            @Local(argsOnly = true) BlockState state,
            @Local(argsOnly = true) BlockPos pos
    ) {
        double dy = SlabSupport.getYOffset(level, pos, state);
        original.call(level, options, x, y + dy, z, xSpeed, ySpeed, zSpeed);
    }
}
