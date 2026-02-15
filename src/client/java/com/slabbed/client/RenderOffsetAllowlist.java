package com.slabbed.client;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;

public final class RenderOffsetAllowlist {
    private RenderOffsetAllowlist() {
    }

    public static boolean allows(BlockState state) {
        return state.isOf(Blocks.TORCH)
                || state.isOf(Blocks.WALL_TORCH)
                || state.isOf(Blocks.REDSTONE_TORCH)
                || state.isOf(Blocks.REDSTONE_WALL_TORCH)
                || state.isOf(Blocks.SOUL_TORCH)
                || state.isOf(Blocks.SOUL_WALL_TORCH)
                || state.isOf(Blocks.REDSTONE_WIRE)
                || state.isIn(BlockTags.WOOL_CARPETS);
    }
}
