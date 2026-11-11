package com.slabbed.compat;

import com.slabbed.util.SlabSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Table scene geometry reads the same saved placement height as the block it represents. */
public final class CinematicTableOffset {
    private CinematicTableOffset() {}

    public static double at(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        return level == null ? 0.0d : SlabSupport.getYOffset(level, pos, level.getBlockState(pos));
    }
}
