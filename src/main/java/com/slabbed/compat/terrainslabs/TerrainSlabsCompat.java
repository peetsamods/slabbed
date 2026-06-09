package com.slabbed.compat.terrainslabs;

import com.slabbed.compat.CompatSlabSurfaceKind;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
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
