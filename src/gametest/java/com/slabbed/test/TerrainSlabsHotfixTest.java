package com.slabbed.test;

import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * 0.3.0-beta.2 hotfix verification: the Terrain Slabs compat must activate against the MODERN
 * {@code terrain_slabs} mod id (not only the legacy {@code terrainslabs}). The
 * {@link TerrainSlabsTestShim} registers {@code terrain_slabs:test_slab} and the gametest mod
 * provides {@code terrain_slabs}, so these exercise the real detection + classification + lowering
 * chain for a modern-namespace surface. Before the fix these would all read NONE / dy 0.
 */
public final class TerrainSlabsHotfixTest {

    private static final double EPS = 1.0e-6;

    private static BlockState tsBottomSlab() {
        return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState tsPropertySlab(SlabType type) {
        return TerrainSlabsTestShim.TEST_TS_PROPERTY_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
    }

    private static void placeAdjacentLoweredFullBlock(ServerWorld world, BlockPos pos) {
        world.setBlockState(pos.down(),
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        world.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    // Detection + classification: a terrain_slabs:* bottom slab classifies BOTTOM_LIKE.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void modernTerrainSlabsClassifiesBottomLike(TestContext ctx) {
        CompatSlabSurfaceKind kind = CompatHooks.customSlabSurfaceKind(tsBottomSlab());
        ctx.assertTrue(kind == CompatSlabSurfaceKind.BOTTOM_LIKE,
                "terrain_slabs:test_slab[type=bottom] must classify BOTTOM_LIKE (mod-id fix), got " + kind);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void propertyTerrainSlabsClassifiesTopLikeWithoutSlabSubclass(TestContext ctx) {
        CompatSlabSurfaceKind kind = CompatHooks.customSlabSurfaceKind(tsPropertySlab(SlabType.TOP));
        ctx.assertTrue(kind == CompatSlabSurfaceKind.TOP_LIKE,
                "terrain_slabs:property_slab[type=top] must classify TOP_LIKE without a SlabBlock subclass, got " + kind);
        ctx.complete();
    }

    // Lowering: a full block on a modern terrain_slabs BOTTOM_LIKE surface lowers -0.5.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockOnModernTerrainSlabLowers(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(base, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        BlockPos objPos = base.up();
        w.setBlockState(objPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(w, objPos, w.getBlockState(objPos));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "stone on a modern terrain_slabs BOTTOM_LIKE slab must lower -0.5 (got " + dy + ")");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingLanternUnderLoweredTerrainTopLikeSupportFollowsDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 4, 3);
        w.setBlockState(support, tsPropertySlab(SlabType.TOP), Block.NOTIFY_LISTENERS);
        placeAdjacentLoweredFullBlock(w, support.east());
        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        ctx.assertTrue(Math.abs(supportDy + 0.5) <= EPS,
                "lowered terrain_slabs TOP_LIKE support fixture must resolve dy -0.5 before testing hangers, got " + supportDy);

        BlockPos lantern = support.down();
        w.setBlockState(lantern, Blocks.LANTERN.getDefaultState().with(net.minecraft.state.property.Properties.HANGING, true),
                Block.NOTIFY_LISTENERS);

        double dy = SlabSupport.getYOffset(w, lantern, w.getBlockState(lantern));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "hanging lantern under lowered terrain_slabs TOP_LIKE support must inherit slabbed dy 0.0 (support -0.5 + attach +0.5), got " + dy);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingSignsUnderLoweredTopSupportsFollowDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        BlockPos vanillaSupport = origin.add(3, 4, 3);
        w.setBlockState(vanillaSupport, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        placeAdjacentLoweredFullBlock(w, vanillaSupport.east());
        double vanillaSupportDy = SlabSupport.getYOffset(w, vanillaSupport, w.getBlockState(vanillaSupport));
        ctx.assertTrue(Math.abs(vanillaSupportDy + 0.5) <= EPS,
                "lowered vanilla TOP slab support fixture must resolve dy -0.5 before testing hanging sign, got " + vanillaSupportDy);
        BlockPos vanillaSign = vanillaSupport.down();
        w.setBlockState(vanillaSign, Blocks.OAK_HANGING_SIGN.getDefaultState(), Block.NOTIFY_LISTENERS);
        double vanillaDy = SlabSupport.getYOffset(w, vanillaSign, w.getBlockState(vanillaSign));
        ctx.assertTrue(Math.abs(vanillaDy) <= EPS,
                "hanging sign under lowered vanilla TOP slab must follow slabbed dy 0.0, got " + vanillaDy);

        BlockPos terrainSupport = origin.add(7, 4, 3);
        w.setBlockState(terrainSupport, tsPropertySlab(SlabType.TOP), Block.NOTIFY_LISTENERS);
        placeAdjacentLoweredFullBlock(w, terrainSupport.east());
        double terrainSupportDy = SlabSupport.getYOffset(w, terrainSupport, w.getBlockState(terrainSupport));
        ctx.assertTrue(Math.abs(terrainSupportDy + 0.5) <= EPS,
                "lowered terrain_slabs TOP_LIKE support fixture must resolve dy -0.5 before testing hanging sign, got " + terrainSupportDy);
        BlockPos terrainSign = terrainSupport.down();
        w.setBlockState(terrainSign, Blocks.OAK_HANGING_SIGN.getDefaultState(), Block.NOTIFY_LISTENERS);
        double terrainDy = SlabSupport.getYOffset(w, terrainSign, w.getBlockState(terrainSign));
        ctx.assertTrue(Math.abs(terrainDy) <= EPS,
                "hanging sign under lowered terrain_slabs TOP_LIKE support must follow slabbed dy 0.0, got " + terrainDy);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainTerrainVanillaVanillaTopChainCarriesHangerDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        BlockPos terrainSupport = origin.add(3, 4, 3);
        BlockPos terrainBridge = terrainSupport.east();
        BlockPos vanillaBridgeOne = terrainBridge.east();
        BlockPos vanillaBridgeTwo = vanillaBridgeOne.east();

        w.setBlockState(terrainSupport, tsPropertySlab(SlabType.TOP), Block.NOTIFY_LISTENERS);
        w.setBlockState(terrainBridge, tsPropertySlab(SlabType.TOP), Block.NOTIFY_LISTENERS);
        w.setBlockState(vanillaBridgeOne, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(vanillaBridgeTwo, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        placeAdjacentLoweredFullBlock(w, vanillaBridgeTwo.east());

        double terrainSupportDy = SlabSupport.getYOffset(w, terrainSupport, w.getBlockState(terrainSupport));
        ctx.assertTrue(Math.abs(terrainSupportDy + 0.5) <= EPS,
                "TS-TS-VS-VS top-like chain must carry lowered dy to first terrain slab, got " + terrainSupportDy);

        BlockPos sign = terrainSupport.down();
        w.setBlockState(sign, Blocks.OAK_HANGING_SIGN.getDefaultState(), Block.NOTIFY_LISTENERS);
        double signDy = SlabSupport.getYOffset(w, sign, w.getBlockState(sign));
        ctx.assertTrue(Math.abs(signDy) <= EPS,
                "hanging sign under TS-TS-VS-VS chain must follow slabbed dy 0.0, got " + signDy);

        BlockPos lantern = terrainBridge.down();
        w.setBlockState(lantern, Blocks.LANTERN.getDefaultState().with(net.minecraft.state.property.Properties.HANGING, true),
                Block.NOTIFY_LISTENERS);
        double lanternDy = SlabSupport.getYOffset(w, lantern, w.getBlockState(lantern));
        ctx.assertTrue(Math.abs(lanternDy) <= EPS,
                "hanging lantern under TS-TS-VS-VS chain must follow slabbed dy 0.0, got " + lanternDy);
        ctx.complete();
    }
}
