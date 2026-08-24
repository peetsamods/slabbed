package com.slabbed.compat.terrainslabs;

import com.slabbed.compat.CompatSlabSurfaceKind;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

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

    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * Terrain Slabs' OWN "on-top" capability predicate, resolved ONCE at class init (never per
     * block — the per-block-reflection lag class). Fabric TS 3.x positions a set of blocks on
     * slab surfaces itself (its "ontop" system shifts model + outline shape + raycast −0.5 when
     * the support below is a BOTTOM slab); that set is
     * {@code OnTopHelper.terrain_slabs$isStateValidOnTop} = the runtime-extensible
     * {@code ModOnTopBlocksRegistry} (snow layers by default; other mods can add blocks)
     * ∪ {@code instanceof VegetationBlock}. Verified against terrain_slabs-fabric-3.3.1 bytecode.
     * {@code null} when TS is absent or its internals moved (→ VegetationBlock-role fallback).
     */
    private static final MethodHandle TS_IS_STATE_VALID_ON_TOP = resolveOnTopProbe();

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
        } catch (Throwable t) {
            // TS build without this internal (e.g. legacy terrainslabs 2.x) — fall back to the
            // VegetationBlock role below; never crash compat resolution.
            return null;
        }
    }

    /**
     * True when Terrain Slabs itself positions this state on top of a slab surface (its "ontop"
     * system already renders it −0.5, outline + raycast included). Slabbed must NOT add its own
     * dy to these states — a second −0.5 sinks the object a full block into the slab (the
     * double-offset "smoosh" family; same law as the shipped Forge 1.20.1
     * {@code handlesObjectOffset} deferral, re-derived for Fabric TS 3.x internals).
     *
     * <p>Role/capability gate, not a class list: beyond the {@link VegetationBlock} base role
     * (cheap instanceof fast-path, no invoke) it asks TS's OWN predicate, so blocks that other
     * mods register into TS's on-top registry at runtime are covered too. No-op when TS is not
     * loaded.
     */
    public static boolean handlesObjectOffset(BlockState state) {
        if (!LOADED || state == null) {
            return false;
        }
        if (state.getBlock() instanceof VegetationBlock) {
            // TS's own predicate includes this instanceof — short-circuit the common family
            // without touching the MethodHandle.
            return true;
        }
        MethodHandle probe = TS_IS_STATE_VALID_ON_TOP;
        if (probe == null) {
            return false;
        }
        try {
            return (boolean) probe.invokeExact(state);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Returns true if slab offsets should be skipped for this state (a TS-owned block).
     *
     * <p>SUBORDINATED, not authoritative: every read path consults the frozen store BEFORE this
     * predicate, and the placement gate carves TAGGED SLABS out of it entirely — a slab a player
     * places is an ordinary slab whoever registered it (LAW.md clause 2), and TS measurably applies
     * no offset of its own to any slab (its offsets cover only on-top subjects, verified against the
     * 3.3.2 jar: the model wrap and the raytrace both gate on the on-top predicate). What remains
     * for this namespace rule is the FACTLESS fall-through: worldgen TS terrain never runs a
     * placement transaction, carries no fact, and must stay flush — the world-hole pin. Do not
     * delete it, and do not restore it above any store read.
     */
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
