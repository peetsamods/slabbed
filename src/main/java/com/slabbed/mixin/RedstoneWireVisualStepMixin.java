package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.util.WireVisualSignal;
import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A redstone wire beside a lowered half-step reads the wire drawn on the slab next to it
 * (maintainer ruling, 2026-09-28).
 *
 * <p>Vanilla's incoming-signal read has three neighbour reads per direction: the neighbour cell, the
 * cell above it when the neighbour is a conductor, or the cell below it when it is not. The third
 * read is wrapped: when the non-conducting neighbour carries a wire drawn inside it, that wire is
 * read as well, through the same signal read. On this version the read takes only a block state, so
 * the neighbour cell is rebuilt from the loop's direction (the method's only direction local) and
 * the reader's own position.
 *
 * <p>INVARIANT: vanilla's answer is kept and only raised by the drawn read; a wire drawn in its own
 * cell is unaffected. See {@link WireVisualSignal}.
 */
@Mixin(RedstoneWireBlock.class)
public abstract class RedstoneWireVisualStepMixin {

    @WrapOperation(
            method = "getReceivedRedstonePower(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)I",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/block/RedstoneWireBlock;increasePower(Lnet/minecraft/block/BlockState;)I",
                    ordinal = 2))
    private int slabbed$alsoReadTheWireDrawnBeside(RedstoneWireBlock wire, BlockState belowState,
                                                   Operation<Integer> original,
                                                   @Local(argsOnly = true) World world,
                                                   @Local(argsOnly = true) BlockPos pos,
                                                   @Local Direction direction) {
        int vanilla = original.call(wire, belowState);
        BlockPos drawn = WireVisualSignal.wireDrawnIn(world, pos, pos.offset(direction));
        if (drawn == null) {
            return vanilla;
        }
        return Math.max(vanilla, original.call(wire, world.getBlockState(drawn)));
    }
}
