package com.slabbed.compat;

import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Central compat dispatch. All compat hooks must be subtractive-only and
 * unreachable (no-op) when their target mod is not present.
 */
public final class CompatHooks {
    private CompatHooks() {
    }

    /**
     * Returns true if compat requires skipping slab offset behavior for this state.
     */
    public static boolean shouldSkipOffset(BlockState state) {
        if (TerrainSlabsCompat.isLoaded()) {
            return TerrainSlabsCompat.shouldSkipOffset(state);
        }
        return false;
    }

    /**
     * Returns true if compat requires excluding this state from generic slab
     * support-source semantics.
     */
    public static boolean shouldSkipSlabSupport(BlockState state) {
        if (TerrainSlabsCompat.isLoaded()) {
            return TerrainSlabsCompat.shouldSkipSlabSupport(state);
        }
        return false;
    }

    /**
     * Named compat-only slab surface role for direct object support decisions.
     */
    public static CompatSlabSurfaceKind customSlabSurfaceKind(BlockState state) {
        if (TerrainSlabsCompat.isLoaded()) {
            return TerrainSlabsCompat.customSlabSurfaceKind(state);
        }
        return CompatSlabSurfaceKind.NONE;
    }

    /**
     * Returns true if Terrain Slabs itself positions this object on its surfaces (its "ontop"
     * system), so Slabbed must defer — adding Slabbed's own -0.5 on top of TS's offset would
     * double-lower the object (the "smoosh"/sink). No-op (false) when TS is not loaded.
     */
    public static boolean terrainSlabsHandlesObjectOffset(BlockState state) {
        if (TerrainSlabsCompat.isLoaded()) {
            return TerrainSlabsCompat.handlesObjectOffset(state);
        }
        return false;
    }
}
