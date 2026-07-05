package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.util.SlabSupport;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Cross-phase-review Finding 2 (correcting/hardening L8 {@code f70eec96}): the two vertical-support
 * carrier predicates {@code SlabSupport.isLoweredDoubleSlabCarrier} (pre-existing) and
 * {@code SlabSupport.isLoweredTopLikeSlabCarrier} (new in L8) read a slab from
 * {@code world.getBlockState(cursor.below())} and classify it as a "lowered support to inherit -0.5
 * from". Neither had the {@code CompatHooks.shouldSkipSlabSupport} TS-compat guard that the sibling
 * choke point {@code SlabSupport.hasBottomSlabBelow} already applies — so a Terrain-Slabs-owned slab
 * (a self-rendering surface TS positions itself) was misread as a Slabbed lowering support. L8
 * DOUBLED the reach of that pre-existing DOUBLE-only gap by adding the TOP surface; the fix adds the
 * guard ONCE, in a shared helper both predicates call, per the CROSS-PORT LAW ("widening what is
 * lowered leaks through unguarded consumers lacking a shouldSkipOffset guard — guard the shared
 * helper, not just the newly-touched call site").
 *
 * <p><b>Test seam:</b> Terrain Slabs is not on the gametest classpath, so
 * {@code CompatHooks.shouldSkipSlabSupport} always returns {@code false} in tests. This test forces
 * the "TS-owned" verdict for its {@code terrain_slabs}-namespaced stand-in slab via the injectable
 * {@code CompatHooks.shouldSkipSlabSupportTestOverride} seam (mirrors the existing
 * {@code SlabAnchorAttachment.clientLoweredSlabCarrierLookup} precedent), so the guard's effect is
 * provable headlessly. Empirically proven RED (guard removed => TS slab still classified as a carrier
 * with the override on) before the fix.
 */
public final class SlabVerticalSupportTerrainSlabsGuardTest {

    private static final Identifier TS_SLAB_ID =
            Identifier.fromNamespaceAndPath("terrain_slabs", "vertical_support_guard_test_slab");
    private static final ResourceKey<Block> TS_SLAB_KEY = ResourceKey.create(Registries.BLOCK, TS_SLAB_ID);
    private static final Block TS_SLAB =
            new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB).setId(TS_SLAB_KEY));

    /** Registers the TS-namespaced stand-in slab (before registry freeze, via the main entrypoint). */
    public static final class TerrainSlabsVerticalSupportGuardTestEntrypoint implements ModInitializer {
        @Override
        public void onInitialize() {
            if (!BuiltInRegistries.BLOCK.containsKey(TS_SLAB_ID)) {
                Registry.register(BuiltInRegistries.BLOCK, TS_SLAB_ID, TS_SLAB);
            }
        }
    }

    /**
     * Builds a genuinely-lowered + PERSISTED support slab of the given type at {@code (3,3,2)} beside a
     * persisted lowered BOTTOM slab, using {@code supportBlock} for the support. Returns the ABS pos.
     */
    private static BlockPos buildLoweredSupport(GameTestHelper helper, Block supportBlock, SlabType supportType) {
        ServerLevel w = helper.getLevel();
        BlockPos groundSlab = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos support = helper.absolutePos(new BlockPos(3, 3, 2));

        w.setBlock(groundSlab,
                Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(loweredBottom,
                Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));

        w.setBlock(support, supportBlock.defaultBlockState().setValue(SlabBlock.TYPE, supportType), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, support, w.getBlockState(support));
        return support;
    }

    /**
     * THE FIX: a Terrain-Slabs-owned DOUBLE/TOP support slab must NOT be classified as a lowered
     * vertical support carrier (it is a self-rendering surface TS positions itself). With the guard,
     * both predicates return false for it once it is recognised as TS-owned.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabsSupportIsNotMisreadAsLoweredVerticalCarrier(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos support = buildLoweredSupport(helper, TS_SLAB, SlabType.DOUBLE);
        BlockState supportState = w.getBlockState(support);

        if (!"terrain_slabs".equals(BuiltInRegistries.BLOCK.getKey(TS_SLAB).getNamespace())) {
            throw helper.assertionException(helper.relativePos(support),
                    "setup: the stand-in support slab must be registered under the terrain_slabs namespace");
        }

        CompatHooks.shouldSkipSlabSupportTestOverride = st ->
                "terrain_slabs".equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace());
        try {
            if (SlabSupport.isLoweredDoubleSlabCarrier(w, support, supportState)) {
                throw helper.assertionException(helper.relativePos(support),
                        "THE FIX: a Terrain-Slabs-owned DOUBLE slab must NOT be read as a lowered DOUBLE carrier "
                                + "(TS positions its own surfaces; the shouldSkipSlabSupport guard must exclude it)");
            }
            if (SlabSupport.isLoweredTopLikeSlabCarrier(w, support, supportState)) {
                throw helper.assertionException(helper.relativePos(support),
                        "THE FIX (L8 surface): a Terrain-Slabs-owned DOUBLE/TOP slab must NOT be read as a lowered "
                                + "top-like carrier — the new L8 predicate must share the same TS-exclusion guard");
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }

    /**
     * THE FIX, TOP variant: same exclusion for a TS-owned TOP support (the surface L8 newly added).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabsTopSupportIsNotMisreadAsTopLikeCarrier(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos support = buildLoweredSupport(helper, TS_SLAB, SlabType.TOP);
        BlockState supportState = w.getBlockState(support);

        CompatHooks.shouldSkipSlabSupportTestOverride = st ->
                "terrain_slabs".equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace());
        try {
            if (SlabSupport.isLoweredTopLikeSlabCarrier(w, support, supportState)) {
                throw helper.assertionException(helper.relativePos(support),
                        "THE FIX: a Terrain-Slabs-owned TOP slab must NOT be read as a lowered top-like carrier");
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }

    /**
     * ANTI-JAM REGRESSION GUARD: a genuine vanilla/Slabbed-native lowered DOUBLE support must STILL be
     * recognised as a carrier even while the TS-exclusion override is active (it only excludes
     * terrain_slabs-namespaced blocks). Proves the guard is namespace-scoped, not a blanket disable.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaLoweredDoubleSupportStillRecognisedWhileTsOverrideActive(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos support = buildLoweredSupport(helper, Blocks.BIRCH_SLAB, SlabType.DOUBLE);
        BlockState supportState = w.getBlockState(support);

        // Sanity: without any override, the vanilla DOUBLE support IS a lowered carrier.
        if (!SlabSupport.isLoweredDoubleSlabCarrier(w, support, supportState)) {
            throw helper.assertionException(helper.relativePos(support),
                    "setup: a genuine vanilla lowered DOUBLE support must be a lowered DOUBLE carrier");
        }

        CompatHooks.shouldSkipSlabSupportTestOverride = st ->
                "terrain_slabs".equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace());
        try {
            if (!SlabSupport.isLoweredDoubleSlabCarrier(w, support, supportState)) {
                throw helper.assertionException(helper.relativePos(support),
                        "ANTI-JAM: a genuine vanilla DOUBLE carrier must STILL be recognised while the TS-exclusion "
                                + "override is active — the guard must be namespace-scoped, not a blanket disable");
            }
            if (!SlabSupport.isLoweredTopLikeSlabCarrier(w, support, supportState)) {
                throw helper.assertionException(helper.relativePos(support),
                        "ANTI-JAM: a genuine vanilla DOUBLE carrier must STILL be a top-like carrier under the override");
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }
}
