package com.slabbed.upgrade;

import net.minecraft.world.World;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Explicit, loader-owned activation seam for one saved world's recorded upgrade choice. */
public final class WorldUpgradeRuntimePolicy {
    private static final Map<World, WorldUpgradeDecision.Mode> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private WorldUpgradeRuntimePolicy() {
    }

    public static void activate(World world, WorldUpgradeDecision.Mode mode) {
        ACTIVE.put(Objects.requireNonNull(world, "world"), Objects.requireNonNull(mode, "mode"));
    }

    public static void deactivate(World world) {
        if (world != null) {
            ACTIVE.remove(world);
        }
    }

    public static boolean authorsModernPlacements(World world) {
        return world != null && ACTIVE.containsKey(world);
    }

    public static WorldUpgradeDecision.Mode mode(World world) {
        return world == null ? null : ACTIVE.get(world);
    }
}
