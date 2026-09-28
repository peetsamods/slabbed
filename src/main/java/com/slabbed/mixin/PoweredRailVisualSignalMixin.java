package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.util.RailVisualSignal;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A powered rail seated on a lowered block is powered by what visibly touches it (maintainer
 * ruling, 2026-09-28).
 *
 * <p>Both of vanilla's input reads are widened: the rail's own state update, and the rail-to-rail
 * chain check that asks whether a neighbouring powered rail is itself fed. Widening only the first
 * would power the lowered rail and still leave the rails chained to it dark, because the chain asks
 * the grid-cell question again on each link.
 *
 * <p>INVARIANT: vanilla's answer is kept and only OR-ed with the drawn-cell read; a rail drawn in its
 * own cell is unaffected. See {@link RailVisualSignal}.
 */
@Mixin(PoweredRailBlock.class)
public abstract class PoweredRailVisualSignalMixin {

    @ModifyExpressionValue(
            method = {
                    "updateBlockState(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;"
                            + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;)V",
                    "isPoweredByOtherRails(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;ZI"
                            + "Lnet/minecraft/block/enums/RailShape;)Z"
            },
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;isReceivingRedstonePower(Lnet/minecraft/util/math/BlockPos;)Z"))
    private boolean slabbed$alsoListenAtTheDrawnCell(boolean gridCell,
                                                    @Local(argsOnly = true) World world,
                                                    @Local(argsOnly = true) BlockPos pos) {
        return gridCell || RailVisualSignal.hasDrawnCellSignal(world, pos);
    }
}
