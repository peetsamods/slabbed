package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Direct enumeration of the CORE rows of {@code DY_SPEC.md}, asserting the implementation's
 * {@code dy} equals the row's <em>product-intent</em> value. This pins 8 rows here; the remaining
 * spec rows are carried by the specialized tests each row cites (never-pop, combine-vs-extend,
 * chaining-matrix, etc.) — this class is the shared table, not the entire proof. It is the
 * "scientific" backbone that lets a port prove conformance on the core lanes without live testing.
 *
 * <p>SCOPE (honest): each method asserts the shared {@code getYOffset} SCALAR — not the three
 * triad application sites (outline/raycast VoxelShape, model mesh) — and builds fixtures with
 * {@code setBlockState} (the GEOMETRIC lane, no placement anchor). The {@code *-BOTTOM} rows
 * therefore do not exercise the {@code SlabAnchorAttachment} never-pop lane; that is proven by the
 * {@code *NeverPopTest} classes. See {@code DY_SPEC.md} footnotes ¹²³ and the Coverage section.
 *
 * <p>Each method is named for its {@code SPEC-ID} in {@code DY_SPEC.md}. The dy values are the
 * spec's intent, cross-anchored to the specialized tests ({@code SlabbedLabFixtureTest},
 * {@code TerrainSlabsHotfixTest}, {@code FenceNeverPopTest}, {@code BlockEntityNeverPopTest},
 * {@code SmooshUnderTerrainSlabsTest}) so a divergence here is a real finding, not a mock drift.
 *
 * <p>Terrain Slabs rows run against the REAL {@code TerrainSlabsCompat} classification via
 * {@link TerrainSlabsTestShim} ({@code terrain_slabs:test_slab}) — headless, not a stub.
 */
public final class DySpecificationTest {

    private static final double EPS = 1.0e-6;

    private static BlockState vanillaSlab(SlabType type) {
        return Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
    }

    private static BlockState tsSlab(SlabType type) {
        return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
    }

    /** dy of {@code subject} resting one cell ABOVE {@code supportBelow}. */
    private static double dyOnSupportBelow(TestContext ctx, BlockState subject, BlockState supportBelow) {
        ServerWorld w = ctx.getWorld();
        BlockPos p = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        w.setBlockState(p.down(), supportBelow, Block.NOTIFY_LISTENERS);
        w.setBlockState(p, subject, Block.NOTIFY_LISTENERS);
        return SlabSupport.getYOffset(w, p, w.getBlockState(p));
    }

    /** dy of {@code subject} hanging one cell BELOW {@code supportAbove} (ceiling-hung). */
    private static double dyUnderSupportAbove(TestContext ctx, BlockState subject, BlockState supportAbove) {
        ServerWorld w = ctx.getWorld();
        BlockPos p = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        w.setBlockState(p.up(), supportAbove, Block.NOTIFY_LISTENERS);
        w.setBlockState(p, subject, Block.NOTIFY_LISTENERS);
        return SlabSupport.getYOffset(w, p, w.getBlockState(p));
    }

    private static void assertDy(TestContext ctx, String specId, double actual, double expected) {
        ctx.assertTrue(Math.abs(actual - expected) <= EPS,
                specId + ": dy must be " + expected + " per DY_SPEC.md; got " + actual);
        ctx.complete();
    }

    // ── Full blocks ───────────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void specFbBottom(TestContext ctx) { // FB-BOTTOM
        assertDy(ctx, "FB-BOTTOM", dyOnSupportBelow(ctx, Blocks.STONE.getDefaultState(), vanillaSlab(SlabType.BOTTOM)), -0.5);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void specFbFlush(TestContext ctx) { // FB-FLUSH
        assertDy(ctx, "FB-FLUSH", dyOnSupportBelow(ctx, Blocks.STONE.getDefaultState(), Blocks.STONE.getDefaultState()), 0.0);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void specFbTsTerrain(TestContext ctx) { // FB-TS-TERRAIN (L5 world-hole guard)
        assertDy(ctx, "FB-TS-TERRAIN", dyOnSupportBelow(ctx, Blocks.STONE.getDefaultState(), tsSlab(SlabType.BOTTOM)), 0.0);
    }

    // ── Connecting blocks ─────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void specCbBottom(TestContext ctx) { // CB-BOTTOM
        assertDy(ctx, "CB-BOTTOM", dyOnSupportBelow(ctx, Blocks.OAK_FENCE.getDefaultState(), vanillaSlab(SlabType.BOTTOM)), -0.5);
    }

    // ── Block entities ────────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void specBeBottom(TestContext ctx) { // BE-BOTTOM
        assertDy(ctx, "BE-BOTTOM", dyOnSupportBelow(ctx, Blocks.HOPPER.getDefaultState(), vanillaSlab(SlabType.BOTTOM)), -0.5);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void specBeFlat(TestContext ctx) { // BE-FLAT
        assertDy(ctx, "BE-FLAT", dyOnSupportBelow(ctx, Blocks.HOPPER.getDefaultState(), Blocks.STONE.getDefaultState()), 0.0);
    }

    // ── Ceiling-hung decorations ──────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void specChVanillaTop(TestContext ctx) { // CH-VANILLA-TOP
        assertDy(ctx, "CH-VANILLA-TOP", dyUnderSupportAbove(ctx, Blocks.HANGING_ROOTS.getDefaultState(), vanillaSlab(SlabType.TOP)), 0.5);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void specChFlush(TestContext ctx) { // CH-FLUSH
        assertDy(ctx, "CH-FLUSH", dyUnderSupportAbove(ctx, Blocks.HANGING_ROOTS.getDefaultState(), Blocks.STONE.getDefaultState()), 0.0);
    }
}
