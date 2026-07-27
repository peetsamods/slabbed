package com.slabbed.compat.terrainslabs;

import com.slabbed.compat.CompatSlabSurfaceKind;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/**
 * Countered Terrain Slabs compatibility: subtractive-only. When the mod is present,
 * skip slab offsets for its blocks to avoid terrain/shape artifacts. Absent the mod,
 * this class is unreachable.
 *
 * <p>Generic Terrain Slabs offsets/support stay subtractive; named direct-support
 * surfaces ({@link #customSlabSurfaceKind}) are a separate, narrow opt-in used only by
 * the direct-object-support path in {@code SlabSupport} -- never by
 * {@link #shouldSkipSlabSupport}/{@link #shouldSkipOffset}, which remain a blanket
 * exclusion so TS blocks are never treated as generic vanilla-style slab support.
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
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return isTerrainSlabsId(id);
    }

    /**
     * Terrain Slabs supplies terrain-shaped slab models and culling behavior.
     * Keep those blocks out of Slabbed's generic support-source rules.
     */
    public static boolean shouldSkipSlabSupport(BlockState state) {
        return shouldSkipOffset(state);
    }

    private static boolean isTerrainSlabsId(ResourceLocation id) {
        return id != null && (MOD_ID.equals(id.getNamespace()) || LEGACY_MOD_ID.equals(id.getNamespace()));
    }

    private static boolean isLoaded(String modId) {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(modId);
    }

    /**
     * Returns the narrow direct-only custom slab surface role for proven named TS states.
     * Only a TS block whose registry path ends {@code _slab}/{@code _slab_bottom} (a named,
     * proven slab-shaped surface -- not every TS block) qualifies, and only when it is not
     * waterlogged/fluid-filled and not a snowy variant.
     */
    public static CompatSlabSurfaceKind customSlabSurfaceKind(BlockState state) {
        if (!LOADED || state == null || !state.hasProperty(SlabBlock.TYPE)) {
            return CompatSlabSurfaceKind.NONE;
        }

        Block block = state.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
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
     * True when Terrain Slabs is already lowering this object itself via its own {@code offset}
     * blockstate property set to a non-{@code none} value (e.g. a lantern/flower/mushroom/grass
     * sitting {@code offset=ontop} on a TS slab, which TS renders lowered through its own "ontop"
     * model). Slabbed must NOT add a second offset to these — doing so double-lowers the object a
     * full block into the slab (the "smoosh"). {@code offset} is a Terrain-Slabs-owned property;
     * objects TS does not wrap (full blocks, signs, vanilla slabs) simply do not carry it.
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
        return false;
    }

    private static boolean isNamedCustomSlabSurface(ResourceLocation id) {
        if (!isTerrainSlabsId(id)) {
            return false;
        }
        String path = id.getPath();
        return path.endsWith("_slab") || path.endsWith("_slab_bottom");
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
