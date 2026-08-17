package com.slabbed.compat.terrainslabs;

import com.slabbed.compat.CompatSlabSurfaceKind;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

/**
 * Countered Terrain Slabs compatibility. Generic Terrain Slabs offsets and slab
 * support stay subtractive, while named direct support surfaces are opt-in.
 */
public final class TerrainSlabsCompat {
    private TerrainSlabsCompat() {
    }

    /** Modern Terrain Slabs (3.x, multiloader). */
    public static final String MOD_ID = "terrain_slabs";
    /** Legacy Fabric-only Terrain Slabs (≤2.x). */
    public static final String LEGACY_MOD_ID = "terrainslabs";
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID)
            || FabricLoader.getInstance().isModLoaded(LEGACY_MOD_ID);
    private static final MethodHandle ON_TOP_PREDICATE = resolveOnTopPredicate();

    /** True if {@code id}'s namespace is either the modern or legacy Terrain Slabs mod id. */
    private static boolean isTerrainSlabsNamespace(Identifier id) {
        if (id == null) {
            return false;
        }
        String ns = id.getNamespace();
        return MOD_ID.equals(ns) || LEGACY_MOD_ID.equals(ns);
    }

    /** Returns true if slab offsets should be skipped for this state. */
    public static boolean shouldSkipOffset(BlockState state) {
        if (!LOADED) {
            return false;
        }

        Block block = state.getBlock();
        Identifier id = Registries.BLOCK.getId(block);
        return isTerrainSlabsNamespace(id);
    }

    /** Returns true if generic Slabbed slab-support semantics should skip this state. */
    public static boolean shouldSkipSlabSupport(BlockState state) {
        return shouldSkipOffset(state);
    }

    /** True when Terrain Slabs already owns this object's on-top offset. */
    public static boolean handlesObjectOffset(BlockState state) {
        if (!LOADED || state == null) {
            return false;
        }
        if (state.getBlock() instanceof PlantBlock) {
            return true;
        }
        if (ON_TOP_PREDICATE == null) {
            return false;
        }
        try {
            return (boolean) ON_TOP_PREDICATE.invokeExact(state);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Returns the direct-only custom slab surface role for proven Terrain Slabs states. */
    public static CompatSlabSurfaceKind customSlabSurfaceKind(BlockState state) {
        if (!LOADED || state == null || !state.contains(SlabBlock.TYPE)) {
            return CompatSlabSurfaceKind.NONE;
        }

        Block block = state.getBlock();
        Identifier id = Registries.BLOCK.getId(block);
        if (!isNamedCustomSlabSurface(id)
                || !state.getFluidState().isEmpty()
                || propertyEquals(state, "snowy", "true")) {
            return CompatSlabSurfaceKind.NONE;
        }

        return switch (state.get(SlabBlock.TYPE)) {
            case BOTTOM -> CompatSlabSurfaceKind.BOTTOM_LIKE;
            case TOP -> CompatSlabSurfaceKind.TOP_LIKE;
            case DOUBLE -> CompatSlabSurfaceKind.DOUBLE_LIKE;
        };
    }

    private static boolean isNamedCustomSlabSurface(Identifier id) {
        if (!isTerrainSlabsNamespace(id)) {
            return false;
        }
        String path = id.getPath();
        return path.endsWith("_slab")
                || path.endsWith("_slab_bottom");
    }

    private static MethodHandle resolveOnTopPredicate() {
        if (!LOADED) {
            return null;
        }
        try {
            Class<?> helper = Class.forName("net.countered.terrainslabs.util.OnTopHelper");
            return MethodHandles.publicLookup().findStatic(
                    helper,
                    "terrain_slabs$isStateValidOnTop",
                    MethodType.methodType(boolean.class, BlockState.class));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
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
        return property.name(state.get(property));
    }
}
