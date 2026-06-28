package com.slabbed.compat;

import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import net.minecraft.block.BlockState;

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
     * Named compat-only slab surface role for direct object support decisions.
     *
     * <p>Consulted ONLY to decide whether an OBJECT resting on a compat (Terrain
     * Slabs) slab should be lowered onto it — never to offset the compat slab
     * itself, which stays subtractive via {@link #shouldSkipOffset}.
     */
    public static CompatSlabSurfaceKind customSlabSurfaceKind(BlockState state) {
        if (TerrainSlabsCompat.isLoaded()) {
            return TerrainSlabsCompat.customSlabSurfaceKind(state);
        }
        return CompatSlabSurfaceKind.NONE;
    }

    /**
     * True only for a PLAYER-PLACED Terrain Slabs BOTTOM half-slab (type=BOTTOM, not generated
     * terrain). Used to lower a placed full cube flush onto it (build-only WYSIWYG) while NEVER
     * lowering natural terrain — keeping the world-hole DODO closed.
     */
    public static boolean isPlacedBottomHalfTerrainSlab(BlockState state) {
        if (TerrainSlabsCompat.isLoaded()) {
            return TerrainSlabsCompat.isPlacedBottomHalfSlab(state);
        }
        return false;
    }

    /** Cheap precomputed gate so per-block hot paths can skip all compat work when TS is absent. */
    public static boolean isTerrainSlabsLoaded() {
        return TerrainSlabsCompat.isLoaded();
    }
}
