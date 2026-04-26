package com.slabbed.dev.audit;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class TorchCategoryProbe implements CategoryProbe {

    @Override
    public String id() {
        return "torch";
    }

    @Override
    public BlockState getProbeState(World world, BlockPos pos) {
        return Blocks.TORCH.getDefaultState();
    }

    @Override
    public boolean canAttemptPlacement(World world, BlockPos pos) {
        return getProbeState(world, pos).canPlaceAt(world, pos);
    }

    @Override
    public boolean survivesAfterPlacement(World world, BlockPos pos, BlockState state) {
        return state.canPlaceAt(world, pos);
    }
}
