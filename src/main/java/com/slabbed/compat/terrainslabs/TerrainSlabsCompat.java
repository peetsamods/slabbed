package com.slabbed.compat.terrainslabs;

import com.slabbed.compat.CompatSlabSurfaceKind;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

/**
 * Countered Terrain Slabs compatibility: subtractive-only. When the mod is present,
 * skip slab offsets for its blocks to avoid terrain/shape artifacts. Absent the mod,
 * this class is unreachable.
 *
 * <p>Recognises both the current mod id {@code terrain_slabs} (primary, v3.1.2+) and the
 * legacy {@code terrainslabs} id. Terrain Slabs is fully invisible to Slabbed's dy/support:
 * no offsets, no support promotion, no holes — purely subtractive.
 */
public final class TerrainSlabsCompat {
    private TerrainSlabsCompat() {
    }

    public static final String MOD_ID = "terrain_slabs";
    public static final String LEGACY_MOD_ID = "terrainslabs";
    private static final boolean LOADED = isLoaded(MOD_ID) || isLoaded(LEGACY_MOD_ID);

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

    /**
     * Terrain Slabs supplies terrain-shaped slab models and culling behavior.
     * Keep those blocks out of Slabbed's generic support-source rules.
     */
    public static boolean shouldSkipSlabSupport(BlockState state) {
        return shouldSkipOffset(state);
    }

    /**
     * Returns the narrow direct-only custom slab surface role for proven states.
     *
     * <p>Consulted ONLY to lower an OBJECT resting on a Terrain Slabs slab — never to
     * offset the TS slab itself (that stays subtractive via {@link #shouldSkipOffset}).
     * A named TS slab with {@code type=BOTTOM}, or a {@code type=DOUBLE} with
     * {@code generated=true}, presents a flush half-height/full top surface that an
     * object sits on, so it reports {@link CompatSlabSurfaceKind#BOTTOM_LIKE}.
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

        SlabType type = state.get(SlabBlock.TYPE);
        if (type == SlabType.BOTTOM) {
            return CompatSlabSurfaceKind.BOTTOM_LIKE;
        }
        if (type == SlabType.DOUBLE && propertyEquals(state, "generated", "true")) {
            return CompatSlabSurfaceKind.BOTTOM_LIKE;
        }
        return CompatSlabSurfaceKind.NONE;
    }

    /**
     * True only for a PLAYER-PLACED Terrain Slabs BOTTOM half-slab: a recognised TS surface,
     * {@code type=BOTTOM}, and NOT {@code generated} (i.e. not natural worldgen terrain). This is the
     * strict gate for lowering a placed full cube flush onto it; natural terrain (generated bottom
     * slabs and generated doubles) is deliberately excluded so full cubes there stay at grid height
     * and never tear see-through world holes.
     */
    public static boolean isPlacedBottomHalfSlab(BlockState state) {
        if (customSlabSurfaceKind(state) != CompatSlabSurfaceKind.BOTTOM_LIKE) {
            return false;
        }
        return state.get(SlabBlock.TYPE) == SlabType.BOTTOM
                && !propertyEquals(state, "generated", "true");
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
        return id != null
                && (MOD_ID.equals(id.getNamespace()) || LEGACY_MOD_ID.equals(id.getNamespace()));
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

    private static boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
