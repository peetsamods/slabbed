package com.slabbed.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.PaleMossCarpetBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/**
 * Client-only dy policy for visual alignment of thin carpet layers on bottom slabs.
 */
public final class ClientDy {
    private ClientDy() {
    }

    public static double dyFor(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return 0.0;
        }

        Block block = state.getBlock();
        if (!(block instanceof CarpetBlock || block instanceof PaleMossCarpetBlock)) {
            return 0.0;
        }

        return SlabSupport.hasBottomSlabBelow(world, pos) ? -0.5 : 0.0;
    }
}
