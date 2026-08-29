package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Direct enumeration of the CORE rows of {@code DY_SPEC.md}, asserting the implementation's
 * {@code dy} equals the row's <em>product-intent</em> value. This class pins the shared table;
 * the remaining spec rows are carried by the specialized tests each row cites — it is the
 * backbone that lets a port prove conformance on the core lanes without live testing.
 *
 * <p>SCOPE (honest): each method asserts the shared {@code SlabSupport.getYOffset} SCALAR — not
 * every consumer independently (consumer agreement is pinned by {@code P7VisualParityTest}) —
 * and builds fixtures with {@code setBlock} (the GEOMETRIC lane, no placement fact). The
 * {@code *-BOTTOM} rows therefore do not exercise the placement lane; that is proven by the
 * real held-item tests. See {@code DY_SPEC.md} footnotes.
 *
 * <p>Each method is named for its {@code SPEC-ID} in {@code DY_SPEC.md}. Terrain Slabs rows run
 * against the REAL {@code TerrainSlabsCompat} classification via the classifier shim mod that
 * claims the Terrain Slabs mod id in this run — headless, not a stub.
 */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class DySpecificationTest {
    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6;

    // ── Full blocks ──────────────────────────────────────────────────────────

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void specFbBottom(GameTestHelper ctx) { // FB-BOTTOM
        assertDy(ctx, "FB-BOTTOM",
                dyOnSupportBelow(ctx, Blocks.STONE.defaultBlockState(), vanillaSlab(SlabType.BOTTOM)), -0.5);
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void specFbFlush(GameTestHelper ctx) { // FB-FLUSH (weak control)
        assertDy(ctx, "FB-FLUSH",
                dyOnSupportBelow(ctx, Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState()), 0.0);
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void specFbTsTerrain(GameTestHelper ctx) { // FB-TS-TERRAIN
        assertDy(ctx, "FB-TS-TERRAIN",
                dyOnSupportBelow(ctx, Blocks.STONE.defaultBlockState(), shimTsSlab(ctx, SlabType.BOTTOM)), 0.0);
    }

    // ── Thin surface layers ──────────────────────────────────────────────────

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void specTlCarpet(GameTestHelper ctx) { // TL-SLAB
        assertDy(ctx, "TL-SLAB",
                dyOnSupportBelow(ctx, Blocks.WHITE_CARPET.defaultBlockState(), vanillaSlab(SlabType.BOTTOM)), -0.5);
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void specTlPowderSnow(GameTestHelper ctx) { // TL-POWDER-SNOW (no-exceptions lowering law)
        assertDy(ctx, "TL-POWDER-SNOW",
                dyOnSupportBelow(ctx, Blocks.POWDER_SNOW.defaultBlockState(), vanillaSlab(SlabType.BOTTOM)), -0.5);
    }

    // ── Connecting blocks ────────────────────────────────────────────────────

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void specCbBottom(GameTestHelper ctx) { // CB-BOTTOM
        assertDy(ctx, "CB-BOTTOM",
                dyOnSupportBelow(ctx, Blocks.OAK_FENCE.defaultBlockState(), vanillaSlab(SlabType.BOTTOM)), -0.5);
    }

    // ── Block entities ───────────────────────────────────────────────────────

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void specBeBottom(GameTestHelper ctx) { // BE-BOTTOM
        assertDy(ctx, "BE-BOTTOM",
                dyOnSupportBelow(ctx, Blocks.HOPPER.defaultBlockState(), vanillaSlab(SlabType.BOTTOM)), -0.5);
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void specBeFlat(GameTestHelper ctx) { // BE-FLAT (weak control)
        assertDy(ctx, "BE-FLAT",
                dyOnSupportBelow(ctx, Blocks.HOPPER.defaultBlockState(), Blocks.STONE.defaultBlockState()), 0.0);
    }

    // ── Ceiling-attached blocks ──────────────────────────────────────────────

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void specChVanillaTop(GameTestHelper ctx) { // CH-VANILLA-TOP (flush ruling)
        assertDy(ctx, "CH-VANILLA-TOP",
                dyUnderSupportAbove(ctx, Blocks.HANGING_ROOTS.defaultBlockState(), vanillaSlab(SlabType.TOP)), 0.0);
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void specChLoweredTop(GameTestHelper ctx) { // CH-LOWERED-TOP (merge compensation)
        ServerLevel world = ctx.getLevel();
        BlockPos subject = ctx.absolutePos(new BlockPos(3, 3, 3));
        BlockPos cap = subject.above();
        world.setBlock(cap, vanillaSlab(SlabType.TOP), Block.UPDATE_ALL);
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(world.getChunkAt(cap), cap, -2),
                "CH-LOWERED-TOP premise: the cap must accept a stored -1.0 fact");
        world.setBlock(subject, Blocks.HANGING_ROOTS.defaultBlockState(), Block.UPDATE_ALL);
        assertDy(ctx, "CH-LOWERED-TOP",
                SlabSupport.getYOffset(world, subject, world.getBlockState(subject)), -0.5);
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void specChTs(GameTestHelper ctx) { // CH-TS (L4 ceiling arm)
        assertDy(ctx, "CH-TS",
                dyUnderSupportAbove(ctx, Blocks.HANGING_ROOTS.defaultBlockState(), shimTsSlab(ctx, SlabType.TOP)), 0.0);
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void specChFlush(GameTestHelper ctx) { // CH-FLUSH (weak control)
        assertDy(ctx, "CH-FLUSH",
                dyUnderSupportAbove(ctx, Blocks.HANGING_ROOTS.defaultBlockState(), Blocks.STONE.defaultBlockState()), 0.0);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static BlockState vanillaSlab(SlabType type) {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static BlockState shimTsSlab(GameTestHelper ctx, SlabType type) {
        Block block = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("terrain_slabs", "shim_terrain_slab"));
        ctx.assertTrue(block != Blocks.AIR,
                "spec premise: the terrain_slabs classifier shim must be loaded in this run");
        return block.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    /** dy of {@code subject} resting one cell ABOVE {@code supportBelow}. */
    private static double dyOnSupportBelow(GameTestHelper ctx, BlockState subject, BlockState supportBelow) {
        ServerLevel world = ctx.getLevel();
        BlockPos pos = ctx.absolutePos(new BlockPos(3, 3, 3));
        world.setBlock(pos.below(), supportBelow, Block.UPDATE_ALL);
        world.setBlock(pos, subject, Block.UPDATE_ALL);
        return SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
    }

    /** dy of {@code subject} hanging one cell BELOW {@code supportAbove} (ceiling-hung). */
    private static double dyUnderSupportAbove(GameTestHelper ctx, BlockState subject, BlockState supportAbove) {
        ServerLevel world = ctx.getLevel();
        BlockPos pos = ctx.absolutePos(new BlockPos(3, 3, 3));
        world.setBlock(pos.above(), supportAbove, Block.UPDATE_ALL);
        world.setBlock(pos, subject, Block.UPDATE_ALL);
        return SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
    }

    private static void assertDy(GameTestHelper ctx, String specId, double actual, double expected) {
        ctx.assertTrue(Math.abs(actual - expected) <= EPS,
                specId + ": dy must be " + expected + " per DY_SPEC.md; got " + actual);
        ctx.succeed();
    }
}
