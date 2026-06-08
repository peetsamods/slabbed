package com.slabbed.test;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * GAMETEST-ONLY headless shim for the Terrain Slabs compat.
 *
 * <p>The real Countered's Terrain Slabs mod has client entrypoints that abort the headless
 * dedicated-server {@code runGameTest}, so it cannot be loaded there. This shim instead
 * registers a single vanilla {@link SlabBlock} under the {@code terrain_slabs} namespace, and
 * the gametest mod ({@code slabbed_gametest}) declares {@code "provides": ["terrain_slabs"]} —
 * so in the gametest environment ONLY, {@code FabricLoader.isModLoaded("terrain_slabs")} is true
 * and {@code TerrainSlabsCompat.customSlabSurfaceKind} classifies {@code terrain_slabs:test_slab}
 * exactly as it would a real Terrain Slabs surface (BOTTOM/TOP/DOUBLE_LIKE by its SlabBlock.TYPE).
 *
 * <p>This never ships: the gametest mod is not on the production or {@code runClient} classpath
 * (verified — {@code slabbed_gametest} is absent from the runClient mod list), so it cannot clash
 * with the real {@code terrain_slabs} mod.
 */
public final class TerrainSlabsTestShim implements ModInitializer {

    /** Namespace-matched stand-in for a real Terrain Slabs slab surface. */
    public static final Block TEST_TS_SLAB =
            new SlabBlock(AbstractBlock.Settings.create().strength(1.0f));

    public static final Identifier TEST_TS_SLAB_ID = Identifier.of("terrain_slabs", "test_slab");

    @Override
    public void onInitialize() {
        Registry.register(Registries.BLOCK, TEST_TS_SLAB_ID, TEST_TS_SLAB);
    }
}
