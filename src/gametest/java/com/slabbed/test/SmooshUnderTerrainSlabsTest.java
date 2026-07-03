package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Row G of {@code PORT_FIX_MATRIX.md} / {@code DY_SPEC.md} §CEILING-HUNG: a ceiling-hung
 * decoration (hanging roots, spore blossom, hanging sign, pale hanging moss) beneath a Terrain
 * Slabs support must hang FLUSH (dy 0.0), because TS applies its own vertical offset — adding
 * Slabbed's own +0.5 "raised-attach" baseline on top of that pushes the hanger UP into the TS
 * block (the "smoosh").
 *
 * <p>The bug was in {@code SlabSupport.ceilingHungDecorationDy}: its first-block guard skipped a
 * TS support ({@code shouldSkipOffset}), but the fallback cursor walk called
 * {@code isTopLikeCeilingSurface(cur)} with no such guard, and a TS surface classifies TOP_LIKE
 * (or DOUBLE_LIKE) — so the walk matched immediately and returned +0.5. The fix guards the walk
 * with {@code !CompatHooks.shouldSkipOffset(cur)}, enforcing the invariant the method's own
 * javadoc already states.
 *
 * <p>This exercises the REAL {@link com.slabbed.compat.terrainslabs.TerrainSlabsCompat}
 * classification through {@link TerrainSlabsTestShim} (a {@code terrain_slabs:test_slab}), not a
 * stub — so a green run is genuine TS-behavior proof, not a mock. The two vanilla cases pin that
 * the fix changes ONLY the TS path (legit "+0.5 under a vanilla top slab" is preserved).
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
        ServerWorld w = ctx.getWorld();
        BlockPos supportPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos hangerPos = supportPos.down();
        w.setBlockState(supportPos, support, Block.NOTIFY_LISTENERS);
        w.setBlockState(hangerPos, Blocks.HANGING_ROOTS.getDefaultState(), Block.NOTIFY_LISTENERS);
        return SlabSupport.getYOffset(w, hangerPos, w.getBlockState(hangerPos));
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
}
