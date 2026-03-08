package com.slabbed.dev.audit;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

interface CategoryProbe {
    String id();

    BlockState getProbeState(World world, BlockPos pos);

    /** Returns true if the probe block can be placed at {@code pos} given current world state. */
    boolean canAttemptPlacement(World world, BlockPos pos);

    /**
     * Returns true if the block placed at {@code pos} would survive a neighbor update.
     * The block is already set in the world when this is called.
     */
    boolean survivesAfterPlacement(World world, BlockPos pos, BlockState state);
}
