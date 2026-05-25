package com.slabbed.compat;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Central compat dispatch. All compat hooks must be subtractive-only and
 * unreachable when their target mod is not present.
 */
public final class CompatHooks {
    private CompatHooks() {
    }

    /**
     * Returns true if compat requires skipping slab offset behavior for this state.
     */
    public static boolean shouldSkipOffset(BlockState state) {
        // 26.1.2 port: Countered Terrain Slabs compat is deferred until core Slabbed compiles.
        return false;
    }
}
