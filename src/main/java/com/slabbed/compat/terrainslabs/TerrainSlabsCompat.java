package com.slabbed.compat.terrainslabs;

import com.slabbed.compat.CompatHooks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import com.slabbed.Slabbed;

import java.util.ArrayList;
import java.util.List;

/**
 * Countered Terrain Slabs compatibility: subtractive-only. When the mod is present,
 * skip slab offsets for its blocks to avoid terrain/shape artifacts. Absent the mod,
 * this class is unreachable.
 */
public final class TerrainSlabsCompat {
    private TerrainSlabsCompat() {
    }

    public static final String MOD_ID = "terrainslabs";
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);
    private static boolean runtimeDumped;

    /** Returns true if slab offsets should be skipped for this state. */
    public static boolean shouldSkipOffset(BlockState state) {
        if (!LOADED) {
            return false;
        }

        Block block = state.getBlock();
        Identifier id = Registries.BLOCK.getId(block);
        if (id == null || !MOD_ID.equals(id.getNamespace())) {
            return false;
        }

        return !(block instanceof SlabBlock && state.contains(SlabBlock.TYPE));
    }

    public static void registerDebugDump() {
        if (!Boolean.getBoolean("slabbed.terrainSlabsCompatDump")) {
            return;
        }

        ServerLifecycleEvents.SERVER_STARTING.register(server -> debugDumpTerrainSlabsBlocks());
    }

    public static void debugDumpTerrainSlabsBlocks() {
        if (runtimeDumped) {
            return;
        }

        if (!LOADED) {
            Slabbed.LOGGER.info("TERRAIN_SLABS_COMPAT_DUMP_BEGIN terrainslabs_not_loaded");
            Slabbed.LOGGER.info("TERRAIN_SLABS_COMPAT_DUMP_END total=0");
            runtimeDumped = true;
            return;
        }

        var mod = FabricLoader.getInstance().getModContainer(MOD_ID);
        if (mod.isPresent()) {
            var meta = mod.get().getMetadata();
            Slabbed.LOGGER.info("TERRAIN_SLABS_COMPAT_DUMP_BEGIN modId={} version={} name={}",
                    meta.getId(), meta.getVersion().getFriendlyString(), meta.getName());
        } else {
            Slabbed.LOGGER.info("TERRAIN_SLABS_COMPAT_DUMP_BEGIN modId={} version=<unknown> name=<unknown>", MOD_ID);
        }

        List<String> blockIds = new ArrayList<>();
        for (Identifier id : Registries.BLOCK.getIds()) {
            if (!MOD_ID.equals(id.getNamespace())) {
                continue;
            }

            BlockState state = Registries.BLOCK.get(id).getDefaultState();
            blockIds.add(id.toString());
            Slabbed.LOGGER.info(
                    "[TerrainSlabsCompat] id={} blockClass={} className={} defaultState={} terrainSkip={} compatSkip={} isAir={}",
                    id,
                    Registries.BLOCK.get(id).getClass().getSimpleName(),
                    Registries.BLOCK.get(id).getClass().getName(),
                    state,
                    shouldSkipOffset(state),
                    CompatHooks.shouldSkipOffset(state),
                    state.isAir()
            );
        }

        Slabbed.LOGGER.info("TERRAIN_SLABS_COMPAT_DUMP_END total={}", blockIds.size());
        runtimeDumped = true;
    }
}
