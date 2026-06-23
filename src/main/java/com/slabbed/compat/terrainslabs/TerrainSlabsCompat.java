package com.slabbed.compat.terrainslabs;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/**
 * Countered Terrain Slabs compatibility: subtractive-only. When the mod is present,
 * skip slab offsets for its blocks to avoid terrain/shape artifacts. Absent the mod,
 * this class is unreachable.
 */
public final class TerrainSlabsCompat {
    private TerrainSlabsCompat() {
    }

    public static final String MOD_ID = "terrainslabs";
    private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);

    /** Returns true if slab offsets should be skipped for this state. */
    public static boolean shouldSkipOffset(BlockState state) {
        if (!LOADED) {
            return false;
        }

        Block block = state.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null && MOD_ID.equals(id.getNamespace());
    }

    /**
     * Terrain Slabs supplies terrain-shaped slab models and culling behavior.
     * Keep those blocks out of Slabbed's generic support-source rules.
     */
    public static boolean shouldSkipSlabSupport(BlockState state) {
        return shouldSkipOffset(state);
    }
}
