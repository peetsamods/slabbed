package com.slabbed.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.world.level.block.state.BlockState;
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

        return SlabSupport.getYOffset(world, pos, state);
    }
}
