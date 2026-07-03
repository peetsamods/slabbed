package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Row G of {@code PORT_FIX_MATRIX.md} / {@code DY_SPEC.md} law L4: NO ceiling-attached block may
 * be treated as raised (+0.5) by a Terrain Slabs support — it must hang FLUSH (dy 0.0), because TS
 * applies its own vertical offset and stacking Slabbed's +0.5 on top pushes the hanger UP into the
 * flush TS block (the "smoosh").
 *
 * <p>L4 covers the WHOLE {@code isCeilingAttached} family — hanging roots / spore blossom / hanging
 * sign / pale moss (the "always-hung" decorations, routed through {@code ceilingHungDecorationDy}),
 * AND hanging lanterns, Y-axis chains, pointed dripstone, cave vines, top-half trapdoors,
 * bells/levers/buttons (routed through {@code getYOffsetInner}'s two ceiling walks). The FIRST fix
 * (commit 3d9be2e8) guarded only the {@code ceilingHungDecorationDy} walk, so an adversarial review
 * found lanterns/chains/dripstone STILL smooshed — the always-hung test was green over a
 * half-applied fix. The completion folds the guard into a single {@code isLoweringTopLikeCeiling}
 * helper used at all three dy walks, and THIS test now exercises a representative block from each
 * routing lane so the gap cannot reopen silently.
 *
 * <p>Exercises the REAL {@link com.slabbed.compat.terrainslabs.TerrainSlabsCompat} classification
 * through {@link TerrainSlabsTestShim} (a {@code terrain_slabs:test_slab}), not a stub. Vanilla
 * controls pin that the fix changes ONLY the TS path (legit "+0.5 under a vanilla top slab" is
 * preserved). NOTE: this asserts the {@code getYOffset} scalar, not the rendered outline/raycast —
 * the render triad is a separate (narrow) coverage gap noted in {@code DY_SPEC.md}.
 */
public final class SmooshUnderTerrainSlabsTest {

    private static final double EPS = 1.0e-6;

    private static net.minecraft.block.BlockState tsSlab(SlabType type) {
        return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
    }

    private static net.minecraft.block.BlockState vanillaSlab(SlabType type) {
        return Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
    }

    private static double hangerDyUnder(TestContext ctx, net.minecraft.block.BlockState support) {
        return ceilingBlockDyUnder(ctx, Blocks.HANGING_ROOTS.getDefaultState(), support);
    }

    /** dy of an arbitrary ceiling-attached {@code subject} hanging one cell below {@code support}. */
    private static double ceilingBlockDyUnder(TestContext ctx, BlockState subject, BlockState support) {
        ServerWorld w = ctx.getWorld();
        BlockPos supportPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos hangerPos = supportPos.down();
        w.setBlockState(supportPos, support, Block.NOTIFY_LISTENERS);
        w.setBlockState(hangerPos, subject, Block.NOTIFY_LISTENERS);
        return SlabSupport.getYOffset(w, hangerPos, w.getBlockState(hangerPos));
    }

    private static BlockState hangingLantern() {
        return Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true);
    }

    private static BlockState yAxisChain() {
        return Blocks.IRON_CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y);
    }

    // THE FIX (RED without it = 0.5): hanging roots under a TS TOP slab hang flush.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingRootsUnderTsTopSlabStayFlush(TestContext ctx) {
        double dy = hangerDyUnder(ctx, tsSlab(SlabType.TOP));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "hanging roots under a Terrain Slabs TOP slab must hang FLUSH (0.0) — TS owns its "
                        + "own offset; +0.5 smooshes the hanger up into the TS block; got " + dy);
        ctx.complete();
    }

    // THE FIX, DOUBLE branch (RED without it = 0.5): TS DOUBLE surface also classifies as a
    // top-like ceiling surface, so it needs the same shouldSkipOffset guard.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingRootsUnderTsDoubleSlabStayFlush(TestContext ctx) {
        double dy = hangerDyUnder(ctx, tsSlab(SlabType.DOUBLE));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "hanging roots under a Terrain Slabs DOUBLE slab must hang FLUSH (0.0); got " + dy);
        ctx.complete();
    }

    // REGRESSION GUARD: under a VANILLA top slab the hanger keeps its legit +0.5 raised-attach
    // baseline (the slab's underside sits half a block above the hanger's natural attach). The fix
    // must NOT touch this — a vanilla top slab is not shouldSkipOffset, so the walk still fires.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingRootsUnderVanillaTopSlabKeepRaisedAttach(TestContext ctx) {
        double dy = hangerDyUnder(ctx, vanillaSlab(SlabType.TOP));
        ctx.assertTrue(Math.abs(dy - 0.5) <= EPS,
                "hanging roots under a VANILLA top slab must keep +0.5 raised-attach (unchanged by "
                        + "the TS fix); got " + dy);
        ctx.complete();
    }

    // REGRESSION GUARD: under a plain flush full block the hanger is flush (0.0), same before and
    // after the fix — confirms the guard didn't collapse the ordinary no-op case.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingRootsUnderFlushFullBlockStayFlush(TestContext ctx) {
        double dy = hangerDyUnder(ctx, Blocks.STONE.getDefaultState());
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "hanging roots under a flush full block must hang FLUSH (0.0); got " + dy);
        ctx.complete();
    }

    // ── The completion: the OTHER isCeilingAttached routing lane (getYOffsetInner walks). ──
    // These were STILL broken after the first fix (which only covered ceilingHungDecorationDy).
    // RED without the isLoweringTopLikeCeiling guard = 0.5 (smoosh); GREEN = 0.0 (flush).

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingLanternUnderTsTopSlabStayFlush(TestContext ctx) {
        double dy = ceilingBlockDyUnder(ctx, hangingLantern(), tsSlab(SlabType.TOP));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "a hanging lantern under a Terrain Slabs TOP slab must hang FLUSH (0.0); got " + dy);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingLanternUnderTsDoubleSlabStayFlush(TestContext ctx) {
        double dy = ceilingBlockDyUnder(ctx, hangingLantern(), tsSlab(SlabType.DOUBLE));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "a hanging lantern under a Terrain Slabs DOUBLE slab must hang FLUSH (0.0); got " + dy);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void yAxisChainUnderTsTopSlabStayFlush(TestContext ctx) {
        double dy = ceilingBlockDyUnder(ctx, yAxisChain(), tsSlab(SlabType.TOP));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "a Y-axis chain under a Terrain Slabs TOP slab must hang FLUSH (0.0); got " + dy);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void pointedDripstoneUnderTsTopSlabStayFlush(TestContext ctx) {
        double dy = ceilingBlockDyUnder(ctx, Blocks.POINTED_DRIPSTONE.getDefaultState(), tsSlab(SlabType.TOP));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "pointed dripstone under a Terrain Slabs TOP slab must hang FLUSH (0.0); got " + dy);
        ctx.complete();
    }

    // VANILLA CONTROLS — the getYOffsetInner walk still fires for a non-TS top slab, so these
    // keep their legit +0.5 raised-attach; proves the completion changed ONLY the TS path.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingLanternUnderVanillaTopSlabKeepRaisedAttach(TestContext ctx) {
        double dy = ceilingBlockDyUnder(ctx, hangingLantern(), vanillaSlab(SlabType.TOP));
        ctx.assertTrue(Math.abs(dy - 0.5) <= EPS,
                "a hanging lantern under a VANILLA top slab keeps +0.5 (unchanged by the TS fix); got " + dy);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void pointedDripstoneUnderVanillaTopSlabKeepRaisedAttach(TestContext ctx) {
        double dy = ceilingBlockDyUnder(ctx, Blocks.POINTED_DRIPSTONE.getDefaultState(), vanillaSlab(SlabType.TOP));
        ctx.assertTrue(Math.abs(dy - 0.5) <= EPS,
                "pointed dripstone under a VANILLA top slab keeps +0.5 (unchanged by the TS fix); got " + dy);
        ctx.complete();
    }
}
