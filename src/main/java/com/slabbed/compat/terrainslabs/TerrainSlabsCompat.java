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
 * Countered Terrain Slabs compatibility. Generic Terrain Slabs offsets and slab
 * support stay subtractive; named direct-support surfaces are opt-in.
 */
public final class TerrainSlabsCompat {
    private TerrainSlabsCompat() {
    }

    public static final String MOD_ID = "terrain_slabs";
    public static final String LEGACY_MOD_ID = "terrainslabs";
    private static final boolean LOADED = isModLoaded(MOD_ID) || isModLoaded(LEGACY_MOD_ID);

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

    /** Returns the narrow direct-only custom slab surface role for proven Terrain Slabs states. */
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
     * True when Terrain Slabs is already lowering this object itself via its own {@code offset}
     * blockstate property set to a non-{@code none} value (e.g. a lantern/flower/mushroom/grass
     * sitting {@code offset=ontop} on a TS slab, which TS renders lowered through its own "ontop"
     * model). Slabbed must NOT add a second offset to these — doing so double-lowers the object a
     * full block into the slab (the "smoosh"). {@code offset} is a Terrain-Slabs-owned property
     * carried by the wrapped object's own state (a capability probe on the state, not a
     * namespace/id check); objects TS does not wrap (full blocks, signs, vanilla slabs) simply do
     * not carry it, and no vanilla block has a blockstate property named {@code offset}.
     */
    public static boolean handlesObjectOffset(BlockState state) {
        if (!LOADED || state == null) {
            return false;
        }
        for (Property<?> property : state.getProperties()) {
            if ("offset".equals(property.getName())) {
                return !"none".equals(String.valueOf(state.get(property)));
            }
        }
        return false;
    }

    private static boolean isNamedCustomSlabSurface(Identifier id) {
        if (!isTerrainSlabsId(id)) {
            return false;
        }
        String path = id.getPath();
        return path.endsWith("_slab") || path.endsWith("_slab_bottom");
    }

    private static boolean isTerrainSlabsId(Identifier id) {
        return id != null && (MOD_ID.equals(id.getNamespace()) || LEGACY_MOD_ID.equals(id.getNamespace()));
    }

    private static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    private static boolean propertyEquals(BlockState state, String propertyName, String expectedValue) {
        for (Property<?> property : state.getProperties()) {
            if (propertyName.equals(property.getName())) {
                return expectedValue.equals(String.valueOf(state.get(property)));
            }
        }
        return false;
    }
}
