package com.slabbed.compat.terrainslabs;

import com.slabbed.compat.CompatSlabSurfaceKind;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Countered Terrain Slabs compatibility (26.1.2, Mojang-mapped port of the shipped 1.21.1/1.21.11
 * compat). Generic Terrain Slabs offsets and slab support stay subtractive (Slabbed never re-lowers
 * a TS block that TS already positions); named direct-support surfaces are opt-in.
 *
 * <p><b>Dual mod-id gate (the headline fix, port of 42002295):</b> a modern Terrain Slabs build
 * registers under {@code terrain_slabs}; older builds under {@code terrainslabs}. The previous 26.1.2
 * stub only checked the legacy id, so a modern TS build short-circuited every compat hook to false and
 * Slabbed treated TS slabs as vanilla — tearing see-through world holes in natural terrain. The
 * loaded-check and every namespace check now accept BOTH ids.
 *
 * <p>When neither mod is loaded this class is effectively inert ({@link #isLoaded()} false → every hook
 * returns the no-op value), so the non-TS path is byte-for-byte the old behaviour.
 */
public final class TerrainSlabsCompat {
    private TerrainSlabsCompat() {
    }

    public static final String MOD_ID = "terrain_slabs";
    public static final String LEGACY_MOD_ID = "terrainslabs";
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID)
            || FabricLoader.getInstance().isModLoaded(LEGACY_MOD_ID);

    /**
     * TS's own "does TS position this state on top of its slabs?" predicate
     * ({@code OnTopHelper.terrain_slabs$isStateValidOnTop}), resolved ONCE at class init (never
     * per-block — the CNFE-storm lesson) and only when TS is loaded. {@code null} when TS is absent
     * or a TS build without the helper is installed; callers then fall back to the verified 3.3.1
     * wrap-family mirror in {@link #handlesObjectOffset}.
     */
    private static final MethodHandle ON_TOP_PROBE = resolveOnTopProbe();

    public static boolean isLoaded() {
        return LOADED;
    }

    /** Returns true if slab offsets should be skipped for this state (a TS-owned block). */
    public static boolean shouldSkipOffset(BlockState state) {
        if (!LOADED) {
            return false;
        }
        Block block = state.getBlock();
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return isTerrainSlabsId(id);
    }

    /** Returns true if generic Slabbed slab-support semantics should skip this state. */
    public static boolean shouldSkipSlabSupport(BlockState state) {
        return shouldSkipOffset(state);
    }

    /** Returns the narrow direct-only custom slab surface role for proven named TS states. */
    public static CompatSlabSurfaceKind customSlabSurfaceKind(BlockState state) {
        if (!LOADED || state == null || !state.hasProperty(SlabBlock.TYPE)) {
            return CompatSlabSurfaceKind.NONE;
        }

        Block block = state.getBlock();
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (!isNamedCustomSlabSurface(id)
                || !state.getFluidState().isEmpty()
                || propertyEquals(state, "snowy", "true")) {
            return CompatSlabSurfaceKind.NONE;
        }

        SlabType type = state.getValue(SlabBlock.TYPE);
        if (type == SlabType.BOTTOM) {
            return CompatSlabSurfaceKind.BOTTOM_LIKE;
        }
        if (type == SlabType.DOUBLE && propertyEquals(state, "generated", "true")) {
            return CompatSlabSurfaceKind.BOTTOM_LIKE;
        }
        return CompatSlabSurfaceKind.NONE;
    }

    /**
     * True when Terrain Slabs itself positions this object on a TS surface, so Slabbed must NOT
     * add its own -0.5 (the double-offset "smoosh"/sink). Role/capability probe, not a per-family
     * class list (donor concept: the Forge 1.20.1 {@code handlesObjectOffset}):
     *
     * <ol>
     *   <li><b>{@code offset} blockstate property probe</b> (Forge-style TS): TS adds an
     *       {@code offset} property to wrapped objects; any non-{@code none} value means TS renders
     *       the object lowered through its own model. Loader-agnostic and per-state; Fabric TS 3.3.1
     *       does not carry the property, so this lane is inert there (verified from the live-rig jar).</li>
     *   <li><b>TS's own on-top predicate</b> (Fabric TS 3.x): delegate to
     *       {@code OnTopHelper.terrain_slabs$isStateValidOnTop} — the exact predicate TS's
     *       {@code SlabOffsetModel}/placement mixins use (ON_TOP_BLOCKS registry + VegetationBlock).
     *       Tracks runtime registry additions ({@code addOnTopBlock} is public API) automatically.</li>
     *   <li><b>Fallback mirror</b> when the helper cannot be resolved (future TS internals change):
     *       {@code VegetationBlock} — the verified 3.3.1 wrap family, preserving the live-confirmed
     *       vegetation-flush behaviour (Maintainer 2026-06-19).</li>
     * </ol>
     *
     * <p>No-op ({@code false}) when TS is not loaded.
     */
    public static boolean handlesObjectOffset(BlockState state) {
        if (!LOADED || state == null) {
            return false;
        }
        for (Property<?> property : state.getProperties()) {
            if ("offset".equals(property.getName())) {
                String value = propertyValueName(state, property);
                return value != null && !"none".equals(value);
            }
        }
        MethodHandle probe = ON_TOP_PROBE;
        if (probe != null) {
            try {
                return (boolean) probe.invokeExact(state);
            } catch (Throwable ignored) {
                // fall through to the verified wrap-family mirror
            }
        }
        return state.getBlock() instanceof VegetationBlock;
    }

    private static MethodHandle resolveOnTopProbe() {
        if (!LOADED) {
            return null;
        }
        try {
            Class<?> helper = Class.forName("net.countered.terrainslabs.util.OnTopHelper");
            return MethodHandles.publicLookup().findStatic(
                    helper,
                    "terrain_slabs$isStateValidOnTop",
                    MethodType.methodType(boolean.class, BlockState.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static boolean isNamedCustomSlabSurface(Identifier id) {
        if (!isTerrainSlabsId(id)) {
            return false;
        }
        String path = id.getPath();
        return path.endsWith("_slab")
                || path.endsWith("_slab_bottom");
    }

    private static boolean isTerrainSlabsId(Identifier id) {
        return id != null && (MOD_ID.equals(id.getNamespace()) || LEGACY_MOD_ID.equals(id.getNamespace()));
    }

    private static boolean propertyEquals(BlockState state, String propertyName, String expectedValue) {
        for (Property<?> property : state.getProperties()) {
            if (propertyName.equals(property.getName())) {
                return expectedValue.equals(propertyValueName(state, property));
            }
        }
        return false;
    }

    private static <T extends Comparable<T>> String propertyValueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
