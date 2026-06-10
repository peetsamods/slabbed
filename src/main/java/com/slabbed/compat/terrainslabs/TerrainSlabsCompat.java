package com.slabbed.compat.terrainslabs;

import com.slabbed.compat.CompatSlabSurfaceKind;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.block.enums.SlabType;

/**
 * Countered Terrain Slabs compatibility. Generic Terrain Slabs offsets and slab
 * support stay subtractive, while named direct support surfaces are opt-in.
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

    /** Returns true if slab offsets should be skipped for this state. */
    public static boolean shouldSkipOffset(BlockState state) {
        if (!LOADED) {
            return false;
        }

        Block block = state.getBlock();
        Identifier id = Registries.BLOCK.getId(block);
        return isTerrainSlabsId(id);
    }

    /** Returns true if generic Slabbed slab-support semantics should skip this state. */
    public static boolean shouldSkipSlabSupport(BlockState state) {
        return shouldSkipOffset(state);
    }

    /** Returns the narrow direct-only custom slab surface role for proven states. */
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

        SlabType type = state.get(SlabBlock.TYPE);
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
        return property.name(state.get(property));
    }
}
