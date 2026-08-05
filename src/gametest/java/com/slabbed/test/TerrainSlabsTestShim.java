package com.slabbed.test;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.Identifier;

/**
 * GAMETEST-ONLY headless shim for the Terrain Slabs compat (MC 1.21.1 port of the
 * 1.21.11 shim from commit 37928aca).
 *
 * <p>The real Terrain Slabs mod has client entrypoints that abort the headless dedicated-server
 * {@code runGameTest}, so it cannot be loaded there. This shim instead registers stand-in
 * {@link SlabBlock}s under both Terrain Slabs namespaces, and the gametest mod
 * ({@code slabbed_gametest}) declares {@code "provides": ["terrain_slabs", "terrainslabs"]} — so
 * in the gametest environment ONLY, {@code FabricLoader.isModLoaded} sees the mod ids and
 * {@code TerrainSlabsCompat.customSlabSurfaceKind} classifies the stand-ins exactly as it would
 * a real Terrain Slabs surface (path must end {@code _slab}; both ids recognised by this line's
 * dual-id gate).
 *
 * <p>1.21.1 differences from the donor: {@code AbstractBlock.Settings.registryKey(...)} does not
 * exist on this line (block-settings registry keys are 1.21.2+), so registration uses the classic
 * {@code Registry.register(Registries.BLOCK, Identifier, block)} idiom. The stand-ins also carry a
 * {@code generated} boolean property (default {@code false}) mirroring real Terrain Slabs terrain
 * slabs, so the classifier's {@code DOUBLE + generated=true → BOTTOM_LIKE} lane is testable
 * (needed by the spawn-proofing generated-double control).
 *
 * <p>Never ships: the gametest mod is not on the production or {@code runClient} classpath, so it
 * cannot clash with the real {@code terrain_slabs} mod.
 */
public final class TerrainSlabsTestShim implements ModInitializer {

    /** Mirrors real Terrain Slabs' worldgen marker; classifier matches it by name. */
    public static final BooleanProperty GENERATED = BooleanProperty.of("generated");

    public static final Identifier TEST_TS_SLAB_ID = Identifier.of("terrain_slabs", "test_slab");

    /** Namespace-matched stand-in for a real (modern) Terrain Slabs slab surface. */
    public static final Block TEST_TS_SLAB =
            new GeneratedCapableSlabBlock(AbstractBlock.Settings.create().strength(1.0f));

    /**
     * Legacy-namespace stand-in ({@code terrainslabs:grass_slab}) confirming the dual mod-id
     * gate still recognises the legacy id.
     */
    public static final Identifier LEGACY_TS_SLAB_ID = Identifier.of("terrainslabs", "grass_slab");

    public static final Block LEGACY_TS_SLAB =
            new GeneratedCapableSlabBlock(AbstractBlock.Settings.create().strength(1.0f));

    @Override
    public void onInitialize() {
        Registry.register(Registries.BLOCK, TEST_TS_SLAB_ID, TEST_TS_SLAB);
        Registry.register(Registries.BLOCK, LEGACY_TS_SLAB_ID, LEGACY_TS_SLAB);
    }

    /** Vanilla slab plus the Terrain Slabs-style {@code generated} property (default false). */
    private static final class GeneratedCapableSlabBlock extends SlabBlock {
        GeneratedCapableSlabBlock(AbstractBlock.Settings settings) {
            super(settings);
            setDefaultState(getDefaultState().with(GENERATED, false));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            super.appendProperties(builder);
            builder.add(GENERATED);
        }
    }
}
