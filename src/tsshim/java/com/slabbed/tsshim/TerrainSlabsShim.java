package com.slabbed.tsshim;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Headless stand-in for Countered Terrain Slabs. It claims the real mod id so the
 * compatibility gate's loaded-check leg is true inside the GameTest server, and it
 * registers one block in each namespace the gate classifies:
 *
 * <ul>
 *   <li>{@code terrain_slabs} — the canonical owned namespace (must skip offsets);</li>
 *   <li>{@code terrainslabs} — the legacy owned namespace (must skip offsets);</li>
 *   <li>{@code slabbed_ts_shim} — an identical block class outside the owned
 *       namespaces (must keep normal Slabbed behavior), so classification is proven
 *       to follow ownership rather than block class.</li>
 * </ul>
 *
 * <p>The canonical namespace also carries a second block whose registry PATH does not end in
 * {@code _slab}. The named-surface classifier matches on that suffix, so this block is the only
 * fixture that can tell a decision made on geometry apart from one made on how the other mod
 * spells its names — both are slabs, both are in the vanilla slabs tag, and only the path
 * differs.
 *
 * <p>This source set is loaded only by the GameTest run configuration and is never
 * part of a shipped archive. Do not add gameplay behavior here: the shim exists so
 * a classification regression turns the headless suite red, nothing more.
 */
@Mod(TerrainSlabsShim.CLAIMED_MOD_ID)
public final class TerrainSlabsShim {
    public static final String CLAIMED_MOD_ID = "terrain_slabs";
    public static final String LEGACY_NAMESPACE = "terrainslabs";
    public static final String CONTROL_NAMESPACE = "slabbed_ts_shim";
    public static final String BLOCK_NAME = "shim_terrain_slab";
    public static final String UNSUFFIXED_BLOCK_NAME = "shim_terrain_step";

    private static final DeferredRegister<net.minecraft.world.level.block.Block> CANONICAL_BLOCKS =
            DeferredRegister.create(Registries.BLOCK, CLAIMED_MOD_ID);
    private static final DeferredRegister<net.minecraft.world.level.block.Block> LEGACY_BLOCKS =
            DeferredRegister.create(Registries.BLOCK, LEGACY_NAMESPACE);
    private static final DeferredRegister<net.minecraft.world.level.block.Block> CONTROL_BLOCKS =
            DeferredRegister.create(Registries.BLOCK, CONTROL_NAMESPACE);

    // Items, so a row can drive a REAL held-item placement through the same transaction the
    // product uses. Without them the only reachable gesture is setBlock, which authors no
    // placement fact and therefore cannot exercise the authored-compat-surface lane at all.
    private static final DeferredRegister<net.minecraft.world.item.Item> CANONICAL_ITEMS =
            DeferredRegister.create(Registries.ITEM, CLAIMED_MOD_ID);
    private static final DeferredRegister<net.minecraft.world.item.Item> LEGACY_ITEMS =
            DeferredRegister.create(Registries.ITEM, LEGACY_NAMESPACE);
    private static final DeferredRegister<net.minecraft.world.item.Item> CONTROL_ITEMS =
            DeferredRegister.create(Registries.ITEM, CONTROL_NAMESPACE);

    static {
        var canonical = CANONICAL_BLOCKS.register(
                BLOCK_NAME, () -> new SlabBlock(BlockBehaviour.Properties.of().strength(2.0F)));
        var legacy = LEGACY_BLOCKS.register(
                BLOCK_NAME, () -> new SlabBlock(BlockBehaviour.Properties.of().strength(2.0F)));
        var control = CONTROL_BLOCKS.register(
                BLOCK_NAME, () -> new SlabBlock(BlockBehaviour.Properties.of().strength(2.0F)));
        var unsuffixed = CANONICAL_BLOCKS.register(
                UNSUFFIXED_BLOCK_NAME, () -> new SlabBlock(BlockBehaviour.Properties.of().strength(2.0F)));
        CANONICAL_ITEMS.register(BLOCK_NAME, () -> new net.minecraft.world.item.BlockItem(
                canonical.get(), new net.minecraft.world.item.Item.Properties()));
        LEGACY_ITEMS.register(BLOCK_NAME, () -> new net.minecraft.world.item.BlockItem(
                legacy.get(), new net.minecraft.world.item.Item.Properties()));
        CONTROL_ITEMS.register(BLOCK_NAME, () -> new net.minecraft.world.item.BlockItem(
                control.get(), new net.minecraft.world.item.Item.Properties()));
        CANONICAL_ITEMS.register(UNSUFFIXED_BLOCK_NAME, () -> new net.minecraft.world.item.BlockItem(
                unsuffixed.get(), new net.minecraft.world.item.Item.Properties()));
    }

    public TerrainSlabsShim(IEventBus modEventBus) {
        CANONICAL_BLOCKS.register(modEventBus);
        LEGACY_BLOCKS.register(modEventBus);
        CONTROL_BLOCKS.register(modEventBus);
        CANONICAL_ITEMS.register(modEventBus);
        LEGACY_ITEMS.register(modEventBus);
        CONTROL_ITEMS.register(modEventBus);
    }
}
