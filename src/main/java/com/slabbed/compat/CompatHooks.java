package com.slabbed.compat;

import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * Central compat dispatch. All compat hooks must be subtractive-only and
 * unreachable when their target mod is not present.
 */
public final class CompatHooks {
    private CompatHooks() {
    }

    private static boolean isModLoaded(String modId) {
        ModList modList = ModList.get();
        // Minecraft bootstrap can ask block-shape questions before NeoForge installs ModList.
        return modList != null && modList.isLoaded(modId);
    }

    /**
     * Returns true if compat requires skipping slab offset behavior for this state.
     */
    public static boolean shouldSkipOffset(BlockState state) {
        if (isModLoaded(TerrainSlabsCompat.MOD_ID)) {
            return TerrainSlabsCompat.shouldSkipOffset(state);
        }
        return false;
    }

    /**
     * Returns true when a compat block should keep its own slab/support semantics
     * instead of becoming a Slabbed support source.
     */
    public static boolean shouldSkipSlabSupport(BlockState state) {
        if (isModLoaded(TerrainSlabsCompat.MOD_ID)) {
            return TerrainSlabsCompat.shouldSkipSlabSupport(state);
        }
        return false;
    }
}
