package com.slabbed.upgrade;

import net.minecraft.world.World;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Placement provenance policy for this line: every world authors modern placements.
 *
 * <p>The world-upgrade line activates this seam per world from a recorded choice. This line ships the
 * KEEP_EXISTING contract unconditionally (maintainer ruling, 2026-09-06): a block placed by an item
 * receives modern provenance and reads its stored placement height; a cell without that provenance —
 * anything that existed before this version, worldgen, structures, {@code /setblock} — keeps the live
 * read lanes, so nothing existing moves. There is no prompt, no world record and no migration here.
 *
 * <p>{@link #activate} and {@link #deactivate} only record a mode for callers that inspect it; they
 * do not switch provenance authoring on or off.
 */
public final class WorldUpgradeRuntimePolicy {
    private static final Map<World, WorldUpgradeDecision.Mode> RECORDED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private WorldUpgradeRuntimePolicy() {
    }

    public static void activate(World world, WorldUpgradeDecision.Mode mode) {
        RECORDED.put(Objects.requireNonNull(world, "world"), Objects.requireNonNull(mode, "mode"));
    }

    public static void deactivate(World world) {
        if (world != null) {
            RECORDED.remove(world);
        }
    }

    /** True for every world: accepted item placements always receive modern provenance on this line. */
    public static boolean authorsModernPlacements(World world) {
        return world != null;
    }

    /** The recorded mode, or {@code KEEP_EXISTING} — the only mode this line implements — when none is recorded. */
    public static WorldUpgradeDecision.Mode mode(World world) {
        if (world == null) {
            return null;
        }
        return RECORDED.getOrDefault(world, WorldUpgradeDecision.Mode.KEEP_EXISTING);
    }
}
