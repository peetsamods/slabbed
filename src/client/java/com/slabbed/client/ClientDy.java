package com.slabbed.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * Client-only dy policy for visual alignment of thin carpet layers on bottom slabs.
 */
public final class ClientDy {
    private ClientDy() {
    }

    public static double dyFor(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return 0.0;
        }

        Block block = state.getBlock();
        if (block instanceof CarpetBlock) {
            // Carpet special case: simple geometric check without anchor logic
            return SlabSupport.hasBottomSlabBelow(world, pos) ? -0.5 : 0.0;
        }

        // For all other blocks, use the full SlabSupport policy including persistent anchors
        return SlabSupport.getYOffset(world, pos, state);
    }
}
