package net.countered.terrainslabs.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;

/** GameTest stand-in for Terrain Slabs' cached on-top object authority. */
public final class OnTopHelper {
    private OnTopHelper() {
    }

    public static boolean terrain_slabs$isStateValidOnTop(BlockState state) {
        return state != null
                && (state.isOf(Blocks.SNOW) || state.getBlock() instanceof PlantBlock);
    }
}
