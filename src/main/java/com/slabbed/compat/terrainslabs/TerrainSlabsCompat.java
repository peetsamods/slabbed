package com.slabbed.compat.terrainslabs;

import com.slabbed.compat.CompatSlabSurfaceKind;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

/**
 * Countered Terrain Slabs compatibility: subtractive-only. When the mod is present,
 * skip slab offsets for its blocks to avoid terrain/shape artifacts. Absent the mod,
 * this class is unreachable.
 */
public final class TerrainSlabsCompat {
    private TerrainSlabsCompat() {
    }

    // MC 1.21.1 build of Countered's Terrain Slabs (terrain_slabs-fabric-3.x) uses mod id
    // + block namespace "terrain_slabs" (underscore). The 1.21.11 build (2.2.5) used
    // "terrainslabs" — this port targets the 1.21.1 mod id.
    public static final String MOD_ID = "terrain_slabs";
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

    /** Returns true if slab offsets should be skipped for this state. */
    public static boolean shouldSkipOffset(BlockState state) {
        if (!LOADED) {
            return false;
        }

        Block block = state.getBlock();
        Identifier id = Registries.BLOCK.getId(block);
        return id != null && MOD_ID.equals(id.getNamespace());
    }

    /**
     * Terrain Slabs supplies terrain-shaped slab models and culling behavior.
     * Keep those blocks out of Slabbed's generic support-source rules.
     */
    public static boolean shouldSkipSlabSupport(BlockState state) {
        return shouldSkipOffset(state);
    }

    /**
     * Blocks that Terrain Slabs already offsets itself when they sit on a slab — vegetation
     * (grass, ferns, flowers, …). Slabbed must NOT also offset these, or they double-lower and
     * clip into the slab. (Snow is already excluded by Slabbed's isThinTopLayer.)
     */
    public static boolean isNativelyOffsetOnTop(BlockState state) {
        return state != null && state.getBlock() instanceof PlantBlock;
    }

    /**
     * Additive (opt-in) direct-support classification: returns the role a proven Terrain
     * Slabs named slab surface plays for direct object support, or {@link CompatSlabSurfaceKind#NONE}.
     * Only ever recognises real {@code terrainslabs:*_slab} / {@code *_slab_bottom} states
     * (excluding waterlogged/snowy), so it cannot mis-fire on vanilla or other-mod blocks.
     */
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
        if (id == null || !MOD_ID.equals(id.getNamespace())) {
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
