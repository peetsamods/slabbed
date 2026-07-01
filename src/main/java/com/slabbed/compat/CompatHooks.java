package com.slabbed.compat;

import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
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
        if (TerrainSlabsCompat.isLoaded()) {
            return TerrainSlabsCompat.shouldSkipOffset(state);
        }
        return false;
    }

    /**
     * Returns true when a compat block should keep its own slab/support semantics
     * instead of becoming a Slabbed support source.
     */
    public static boolean shouldSkipSlabSupport(BlockState state) {
        if (TerrainSlabsCompat.isLoaded()) {
            return TerrainSlabsCompat.shouldSkipSlabSupport(state);
        }
        return false;
    }

    /**
     * Returns the narrow direct-only custom slab surface role for this state (NONE when no
     * compat mod is loaded or the state does not qualify). Separate from
     * {@link #shouldSkipSlabSupport}: this is an additive opt-in for named/proven surfaces,
     * never a change to the blanket generic-support exclusion.
     */
    public static CompatSlabSurfaceKind customSlabSurfaceKind(BlockState state) {
        if (TerrainSlabsCompat.isLoaded()) {
            return TerrainSlabsCompat.customSlabSurfaceKind(state);
        }
        return CompatSlabSurfaceKind.NONE;
    }

    /**
     * Returns true when a compat mod is ALREADY applying its own vertical offset to this object
     * (so Slabbed must not add a second offset and double-lower it). False when no compat mod is
     * loaded or the object is not compat-offset.
     */
    public static boolean terrainSlabsHandlesObjectOffset(BlockState state) {
        if (TerrainSlabsCompat.isLoaded()) {
            return TerrainSlabsCompat.handlesObjectOffset(state);
        }
        return false;
    }
}
