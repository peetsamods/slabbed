package com.slabbed.compat;

import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
// CompatSlabSurfaceKind is in this package (com.slabbed.compat)

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

    /**
     * Additive (opt-in) named compat-only slab surface role for direct object-support
     * decisions. Returns {@link CompatSlabSurfaceKind#NONE} when the target mod is absent,
     * so all callers are inert without Terrain Slabs installed.
     */
    public static CompatSlabSurfaceKind customSlabSurfaceKind(BlockState state) {
        if (isModLoaded(TerrainSlabsCompat.MOD_ID)) {
            return TerrainSlabsCompat.customSlabSurfaceKind(state);
        }
        return CompatSlabSurfaceKind.NONE;
    }

    /**
     * True if Terrain Slabs already lowers this block on a slab itself (vegetation), so Slabbed
     * must leave it alone — otherwise it double-lowers and clips. Inert without Terrain Slabs.
     */
    public static boolean isNativelyOffsetOnTop(BlockState state) {
        if (isModLoaded(TerrainSlabsCompat.MOD_ID)) {
            return TerrainSlabsCompat.isNativelyOffsetOnTop(state);
        }
        return false;
    }
}
