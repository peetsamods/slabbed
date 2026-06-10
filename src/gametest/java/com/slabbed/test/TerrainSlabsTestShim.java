package com.slabbed.test;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.state.StateManager;
import net.minecraft.util.Identifier;

/**
 * GAMETEST-ONLY headless shim for the Terrain Slabs compat.
 *
 * <p>The real Terrain Slabs mod has client entrypoints that abort the headless dedicated-server
 * {@code runGameTest}, so it cannot be loaded there. This shim instead registers a single vanilla
 * {@link SlabBlock} under the modern {@code terrain_slabs} namespace, and the gametest mod
 * ({@code slabbed_gametest}) declares {@code "provides": ["terrain_slabs"]} — so in the gametest
 * environment ONLY, {@code FabricLoader.isModLoaded("terrain_slabs")} is true and
 * {@code TerrainSlabsCompat.customSlabSurfaceKind} classifies {@code terrain_slabs:test_slab}
 * exactly as it would a real Terrain Slabs surface. This directly exercises the 0.3.0-beta.2
 * mod-id fix (compat must activate against the modern {@code terrain_slabs} id, not only the
 * legacy {@code terrainslabs}).
 *
 * <p>Never ships: the gametest mod is not on the production or {@code runClient} classpath, so it
 * cannot clash with the real {@code terrain_slabs} mod.
 */
public final class TerrainSlabsTestShim implements ModInitializer {

    public static final Identifier TEST_TS_SLAB_ID = Identifier.of("terrain_slabs", "test_slab");

    public static final RegistryKey<Block> TEST_TS_SLAB_KEY =
            RegistryKey.of(RegistryKeys.BLOCK, TEST_TS_SLAB_ID);

    /** Namespace-matched stand-in for a real (modern) Terrain Slabs slab surface. */
    public static final Block TEST_TS_SLAB =
            new SlabBlock(AbstractBlock.Settings.create().strength(1.0f).registryKey(TEST_TS_SLAB_KEY));

    public static final Identifier TEST_TS_PROPERTY_SLAB_ID =
            Identifier.of("terrain_slabs", "property_slab");

    public static final RegistryKey<Block> TEST_TS_PROPERTY_SLAB_KEY =
            RegistryKey.of(RegistryKeys.BLOCK, TEST_TS_PROPERTY_SLAB_ID);

    /**
     * Namespace-matched stand-in for real Terrain Slabs classes that expose
     * {@link SlabBlock#TYPE} without subclassing {@link SlabBlock}.
     */
    public static final Block TEST_TS_PROPERTY_SLAB =
            new TerrainSlabsPropertyBlock(AbstractBlock.Settings.create()
                    .strength(1.0f)
                    .registryKey(TEST_TS_PROPERTY_SLAB_KEY));

    /**
     * Legacy-namespace stand-in ({@code terrainslabs:grass_slab}) so the existing
     * {@code OffsetRaycastTargetingTest} / {@code CombinedSlabChainingMatrixTest} fixtures (which
     * look this block up by id) resolve under headless {@code runGameTest}. Also confirms the
     * 0.3.0-beta.2 mod-id fix still recognizes the legacy id.
     */
    public static final Identifier LEGACY_TS_SLAB_ID = Identifier.of("terrainslabs", "grass_slab");

    public static final RegistryKey<Block> LEGACY_TS_SLAB_KEY =
            RegistryKey.of(RegistryKeys.BLOCK, LEGACY_TS_SLAB_ID);

    public static final Block LEGACY_TS_SLAB =
            new SlabBlock(AbstractBlock.Settings.create().strength(1.0f).registryKey(LEGACY_TS_SLAB_KEY));

    @Override
    public void onInitialize() {
        Registry.register(Registries.BLOCK, TEST_TS_SLAB_KEY, TEST_TS_SLAB);
        Registry.register(Registries.BLOCK, TEST_TS_PROPERTY_SLAB_KEY, TEST_TS_PROPERTY_SLAB);
        Registry.register(Registries.BLOCK, LEGACY_TS_SLAB_KEY, LEGACY_TS_SLAB);
    }

    private static final class TerrainSlabsPropertyBlock extends Block {
        TerrainSlabsPropertyBlock(Settings settings) {
            super(settings);
            setDefaultState(getStateManager().getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            builder.add(SlabBlock.TYPE);
        }
    }
}
