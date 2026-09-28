package com.slabbed.mixin;

import com.slabbed.util.RailVisualSignal;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A neighbour change delivered to the cell a lowered powered rail is DRAWN in is delivered to the
 * rail as well (maintainer ruling, 2026-09-28).
 *
 * <p>Vanilla only notifies the six neighbours of a changed cell (torches and wire reach one cell
 * further on their own). A lever or a repeater beside the slab notifies the slab, not the rail one
 * cell up, so the rail would keep its old state until something else touched it. The slab's
 * notification is forwarded to the rail drawn inside it, so the rail re-evaluates exactly when a
 * vanilla rail in that row would.
 *
 * <p>INVARIANT: never forward a change the rail itself caused (its own state update notifies its
 * support), or the two would notify each other forever. Server side only: the client does not run
 * redstone.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class LoweredRailSupportNeighborMixin {

    @Inject(method = "neighborUpdate(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/block/Block;Lnet/minecraft/util/math/BlockPos;Z)V",
            at = @At("HEAD"))
    private void slabbed$forwardToTheRailDrawnHere(World world, BlockPos pos, Block sourceBlock,
                                                   BlockPos sourcePos, boolean notify,
                                                   CallbackInfo ci) {
        if (world == null || world.isClient() || sourceBlock instanceof PoweredRailBlock) {
            return;
        }
        if (RailVisualSignal.carriesADrawnPoweredRail(world, pos)) {
            world.updateNeighbor(pos.up(), sourceBlock, sourcePos);
        }
    }
}
