package com.slabbed.compat;

import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;

/**
 * Central compat dispatch. All compat hooks must be subtractive-only and
 * unreachable when their target mod is not present.
 */
public final class CompatHooks {
    private CompatHooks() {
    }

    private static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    /**
     * Returns true if compat requires skipping slab offset behavior for this state.
     */
    public static boolean shouldSkipOffset(BlockState state) {
        if (isModLoaded(TerrainSlabsCompat.MOD_ID)) {
            return TerrainSlabsCompat.shouldSkipOffset(state);
        }
        return false;
    }

    /**
     * Returns true when a compat block should keep its own slab/support semantics
     * instead of becoming a Slabbed support source.
     */
    public static boolean shouldSkipSlabSupport(BlockState state) {
        if (isModLoaded(TerrainSlabsCompat.MOD_ID)) {
            return TerrainSlabsCompat.shouldSkipSlabSupport(state);
        }
        return false;
    }
}
