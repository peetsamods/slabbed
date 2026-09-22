package com.slabbed.compat.sable;

import net.minecraft.core.BlockPos;

/** Implemented on Sable's physics system by the compatibility mixin; vanilla types only. */
public interface SablePlacementRefresh {
    /** Re-bakes the cell at {@code pos} and the {@code depth} cells below it that it can hang into. */
    void slabbed$refreshPlacementHeight(BlockPos pos, int depth);
}
