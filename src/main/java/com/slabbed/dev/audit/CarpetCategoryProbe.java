package com.slabbed.dev.audit;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Probes whether a carpet block can be placed and survive on each test lane.
 *
 * Placement: delegates to CarpetBlock.canPlaceAt, which Slabbed injects into
 * to accept slab top faces as valid support.
 *
 * Survival: re-checks canPlaceAt, which is exactly what CarpetBlockMixin's
 * getStateForNeighborUpdate does when deciding whether to pop the block.
 */
class CarpetCategoryProbe implements CategoryProbe {

    @Override
    public String id() {
        return "carpet";
    }

    @Override
    public BlockState getProbeState(World world, BlockPos pos) {
        return Blocks.WHITE_CARPET.getDefaultState();
    }

    @Override
    public boolean canAttemptPlacement(World world, BlockPos pos) {
        return getProbeState(world, pos).canPlaceAt(world, pos);
    }

    @Override
    public boolean survivesAfterPlacement(World world, BlockPos pos, BlockState state) {
        // canPlaceAt is the exact condition CarpetBlockMixin checks inside
        // getStateForNeighborUpdate to decide whether the block pops.
        return state.canPlaceAt(world, pos);
    }
}
