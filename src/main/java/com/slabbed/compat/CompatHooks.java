package com.slabbed.compat;

import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Central compat dispatch. All compat hooks must be subtractive-only and
 * unreachable (no-op) when their target mod is not present.
 */
public final class CompatHooks {
    private CompatHooks() {
    }

    /**
     * Test-only injectable override for {@link #shouldSkipSlabSupport}. Terrain Slabs is NOT on the
     * gametest classpath, so {@link TerrainSlabsCompat#isLoaded()} is {@code false} and the real hook
     * always returns {@code false} in tests — headless coverage of the TS-exclusion guards (sweeper
     * Finding 2) therefore needs a seam to force the "TS-owned" verdict for a stand-in block registered
     * under the {@code terrain_slabs} namespace. Mirrors the existing
     * {@code SlabAnchorAttachment.clientLoweredSlabCarrierLookup} injectable-predicate precedent. Never
     * set in production (only the gametest sets/clears it); {@code null} means "use the real hook".
     */
    public static volatile Predicate<BlockState> shouldSkipSlabSupportTestOverride = null;

    /**
     * The same seam for {@link #shouldSkipOffset}, and it exists for a specific reason: the compat mod
     * is absent in tests, so this hook always answered {@code false} and every compat-ownership branch
     * of the resolver was unreachable headlessly. A change to them could pass the whole suite while
     * being dead code — found the hard way on 2026-08-21, when a full green said nothing about a
     * compat-branch change. Never set in production (only a gametest sets and clears it); {@code null}
     * means "use the real hook".
     */
    public static volatile Predicate<BlockState> shouldSkipOffsetTestOverride = null;

    /**
     * Returns true if compat requires skipping slab offset behavior for this state.
     */
    public static boolean shouldSkipOffset(BlockState state) {
        Predicate<BlockState> override = shouldSkipOffsetTestOverride;
        if (override != null && override.test(state)) {
            return true;
        }
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
        Predicate<BlockState> override = shouldSkipSlabSupportTestOverride;
        if (override != null && override.test(state)) {
            return true;
        }
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
     * True when the compat mod itself positions this state on top of slab surfaces (model,
     * outline and raycast), so Slabbed must NOT add its own dy — adding both is the
     * double-offset "smoosh" family. False whenever the compat mod is absent.
     */
    public static boolean terrainSlabsHandlesObjectOffset(BlockState state) {
        if (TerrainSlabsCompat.isLoaded()) {
            return TerrainSlabsCompat.handlesObjectOffset(state);
        }
        return false;
    }
}
