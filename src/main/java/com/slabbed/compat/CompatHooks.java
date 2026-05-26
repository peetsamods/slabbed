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
     * Returns true if compat requires excluding this state from generic slab
     * support-source semantics.
     */
    public static boolean shouldSkipSlabSupport(BlockState state) {
        if (isModLoaded(TerrainSlabsCompat.MOD_ID)) {
            return TerrainSlabsCompat.shouldSkipSlabSupport(state);
        }
        return false;
    }

    /**
     * Named compat-only slab surface role for direct object support decisions.
     */
    public static CompatSlabSurfaceKind customSlabSurfaceKind(BlockState state) {
        if (isModLoaded(TerrainSlabsCompat.MOD_ID)) {
            return TerrainSlabsCompat.customSlabSurfaceKind(state);
        }
        return CompatSlabSurfaceKind.NONE;
    }
}
