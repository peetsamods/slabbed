package com.slabbed.compat;

import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
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
     * Returns true when a compat mod already positions this object on its support, so Slabbed
     * must not add its own lowering and sink the block. Relation-shaped: the deciding pair can be
     * two vanilla blocks, which no per-state classifier can see.
     */
    public static boolean handlesObjectOffset(BlockGetter world, BlockPos pos, BlockState state) {
        if (TerrainSlabsCompat.isLoaded()) {
            return TerrainSlabsCompat.handlesObjectOffset(world, pos, state);
        }
        return false;
    }

    /** Named compat-only slab surface role for direct object-support decisions. */
    public static CompatSlabSurfaceKind customSlabSurfaceKind(BlockState state) {
        if (TerrainSlabsCompat.isLoaded()) {
            return TerrainSlabsCompat.customSlabSurfaceKind(state);
        }
        return CompatSlabSurfaceKind.NONE;
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
}
