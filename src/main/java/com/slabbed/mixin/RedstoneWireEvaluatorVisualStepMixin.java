package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.util.WireVisualSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.RedstoneWireEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A redstone wire beside a lowered half-step reads the wire drawn on the slab next to it
 * (maintainer ruling, 2026-09-28).
 *
 * <p>Vanilla's incoming-signal read has three neighbour reads per direction: the neighbour cell, the
 * cell above it when the neighbour is a conductor, or the cell below it when it is not. The third
 * read is wrapped: when the non-conducting neighbour carries a wire drawn inside it, that wire is
 * read as well. Both wire evaluators (default and experimental) share this method, and the wrapped
 * call dispatches to the running evaluator's own signal read, so the experimental evaluator's
 * in-flight power values are honoured.
 *
 * <p>INVARIANT: vanilla's answer is kept and only raised by the drawn read; a wire drawn in its own
 * cell is unaffected. See {@link WireVisualSignal}.
 */
@Mixin(RedstoneWireEvaluator.class)
public abstract class RedstoneWireEvaluatorVisualStepMixin {

    @WrapOperation(
            method = "getIncomingWireSignal(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/redstone/RedstoneWireEvaluator;getWireSignal"
                            + "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)I",
                    ordinal = 2))
    private int slabbed$alsoReadTheWireDrawnBeside(RedstoneWireEvaluator evaluator, BlockPos belowNeighbor,
                                                   BlockState belowState, Operation<Integer> original,
                                                   @Local(argsOnly = true) Level level,
                                                   @Local(argsOnly = true) BlockPos pos) {
        int vanilla = original.call(evaluator, belowNeighbor, belowState);
        BlockPos drawn = WireVisualSignal.wireDrawnIn(level, pos, belowNeighbor.above());
        if (drawn == null) {
            return vanilla;
        }
        return Math.max(vanilla, original.call(evaluator, drawn, level.getBlockState(drawn)));
    }
}
