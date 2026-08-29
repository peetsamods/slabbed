package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Step-face cull matrix, ported from the donor's thirteen-row suite onto this line's
 * height-comparison predicate. This line's {@code SlabSupport.isSlabHeightStepFace} compares the
 * two neighbours' actual resolved heights, so it is magnitude-correct by construction — these
 * rows pin that property across the fact-authored, geometric, cube, and slab families so a
 * future refactor to a boolean lowered/not-lowered form (the donor's original magnitude-blind
 * bug) turns the matrix red. Every scene premise-asserts its heights first, so no row can pass
 * against a fixture that stopped producing the intended step.
 *
 * <p>Two deliberate departures from the donor, both this-line geometry: a slab standing BESIDE a
 * geometrically lowered full block follows it down on this line (the side-slab lane), so that
 * seam CLOSES here rather than redrawing; and the donor's tiered fast-path counter row is not
 * ported — this line's predicate has no tier machinery, and its hot-path cost is gated by the
 * allocation-regression test instead.
 */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class SlabHeightStepCullTest {
    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6;

    /** A fact-lowered cube beside a flush cube exposes a 0.5 seam: both faces redraw. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void factLoweredCubeBesideFlushCubeRedrawsSteppedFace(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos lowered = buildFactCube(ctx, new BlockPos(1, 3, 1), -1, -0.5);
        BlockPos flush = groundedCube(ctx, lowered.east(), 0.0);
        assertStep(ctx, world, lowered, Direction.EAST, true, "fact-lowered cube toward flush cube");
        assertStep(ctx, world, flush, Direction.WEST, true, "flush cube back toward the lowered cube");
        ctx.succeed();
    }

    /** Two flush cubes share a fully covered face: never redraw. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void twoFlushCubesNeverRedraw(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos a = groundedCube(ctx, ctx.absolutePos(new BlockPos(1, 3, 3)), 0.0);
        groundedCube(ctx, a.east(), 0.0);
        assertStep(ctx, world, a, Direction.EAST, false, "two flush cubes");
        ctx.succeed();
    }

    /** Two cubes lowered by the SAME stored magnitude have no seam: never redraw. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void twoEquallyFactLoweredCubesNeverRedraw(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos a = buildFactCube(ctx, new BlockPos(1, 3, 5), -1, -0.5);
        buildFactCube(ctx, new BlockPos(2, 3, 5), -1, -0.5);
        assertStep(ctx, world, a, Direction.EAST, false, "two equally fact-lowered cubes");
        ctx.succeed();
    }

    /**
     * The standing grounded-beside-lowered law: a grounded TOP slab beside a GEOMETRICALLY
     * lowered full block must not sink, so the 0.5 seam is real and both faces redraw.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void topSlabBesideGeometricLoweredFullBlockRedrawsSteppedFace(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos base = ctx.absolutePos(new BlockPos(1, 2, 7));
        world.setBlock(base, bottomSlab(), Block.UPDATE_ALL);
        BlockPos lowered = base.above();
        world.setBlock(lowered, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        assertExactDy(ctx, world, lowered, -0.5, "the geometric full block must lower");
        BlockPos slab = lowered.east();
        world.setBlock(slab,
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), Block.UPDATE_ALL);
        assertExactDy(ctx, world, slab, 0.0, "a grounded slab beside a geometric lowered block must not sink");
        assertStep(ctx, world, lowered, Direction.EAST, true, "geometric-lowered cube toward the grounded slab");
        assertStep(ctx, world, slab, Direction.WEST, true, "grounded slab back toward the lowered cube");
        ctx.succeed();
    }

    /**
     * The authored visual-fill family: a TOP slab beside a FACT-lowered cube follows it down
     * on this line, so the seam closes and neither face redraws.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void sideFollowingTopSlabBesideFactLoweredCubeClosesTheSeam(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos lowered = buildFactCube(ctx, new BlockPos(4, 3, 7), -1, -0.5);
        BlockPos slab = lowered.east();
        world.setBlock(slab.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(slab.below(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(slab,
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), Block.UPDATE_ALL);
        assertExactDy(ctx, world, slab, -0.5, "the side slab follows the fact-lowered cube on this line");
        assertStep(ctx, world, lowered, Direction.EAST, false, "seam closed by the side-following slab");
        assertStep(ctx, world, slab, Direction.WEST, false, "closed seam, slab side");
        ctx.succeed();
    }

    /** Two flush TOP slabs: never redraw. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void twoFlushTopSlabsNeverRedraw(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos a = ctx.absolutePos(new BlockPos(1, 3, 9));
        BlockPos b = a.east();
        for (BlockPos pos : new BlockPos[]{a, b}) {
            world.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            world.setBlock(pos,
                    Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), Block.UPDATE_ALL);
            assertExactDy(ctx, world, pos, 0.0, "flush slab fixture");
        }
        assertStep(ctx, world, a, Direction.EAST, false, "two flush top slabs");
        ctx.succeed();
    }

    /** Two TOP slabs lowered by the same stored magnitude: never redraw. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void twoEquallyLoweredTopSlabsNeverRedraw(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos a = ctx.absolutePos(new BlockPos(4, 3, 9));
        BlockPos b = a.east();
        for (BlockPos pos : new BlockPos[]{a, b}) {
            world.setBlock(pos,
                    Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), Block.UPDATE_ALL);
            ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(world.getChunkAt(pos), pos, -1),
                    "fixture: the slab must accept a -0.5 fact");
            assertExactDy(ctx, world, pos, -0.5, "equally lowered slab fixture");
        }
        assertStep(ctx, world, a, Direction.EAST, false, "two equally lowered top slabs");
        ctx.succeed();
    }

    /** THE magnitude row: -1.0 beside -0.5 is a real 0.5 seam even though BOTH are lowered. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void minusOneCubeBesideMinusHalfCubeRedrawsSteppedFace(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos deep = buildFactCube(ctx, new BlockPos(1, 3, 11), -2, -1.0);
        BlockPos shallow = buildFactCube(ctx, new BlockPos(2, 3, 11), -1, -0.5);
        assertStep(ctx, world, deep, Direction.EAST, true,
                "a -1.0 cube beside a -0.5 cube exposes a 0.5 seam even though both are lowered"
                        + " — a boolean lowered/not-lowered predicate is magnitude-blind here");
        assertStep(ctx, world, shallow, Direction.WEST, true, "the -0.5 side of the same seam");
        ctx.succeed();
    }

    /** Magnitude row with a slab subject: -1.0 cube beside a -0.5 BOTTOM slab redraws both ways. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void minusOneCubeBesideMinusHalfBottomSlabRedrawsSteppedFace(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos deep = buildFactCube(ctx, new BlockPos(1, 3, 13), -2, -1.0);
        BlockPos slab = deep.east();
        world.setBlock(slab,
                Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(world.getChunkAt(slab), slab, -1),
                "fixture: the slab must accept a -0.5 fact");
        assertExactDy(ctx, world, slab, -0.5, "lowered bottom-slab fixture");
        assertStep(ctx, world, slab, Direction.WEST, true, "the slab's face toward the deeper cube");
        assertStep(ctx, world, deep, Direction.EAST, true, "the deep cube's face toward the slab");
        ctx.succeed();
    }

    /** Two equally GEOMETRICALLY lowered cubes (no facts anywhere): never redraw. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void twoEquallyGeometricallyLoweredCubesNeverRedraw(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos a = geometricLoweredCube(ctx, new BlockPos(1, 2, 15));
        geometricLoweredCube(ctx, new BlockPos(2, 2, 15));
        assertStep(ctx, world, a, Direction.EAST, false, "two equally geometric-lowered cubes");
        ctx.succeed();
    }

    /** A geometric-lowered cube beside genuinely flush terrain redraws both ways. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void geometricLoweredCubeBesideFlushCubeRedrawsSteppedFace(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos lowered = geometricLoweredCube(ctx, new BlockPos(5, 2, 15));
        BlockPos flush = groundedCube(ctx, lowered.east(), 0.0);
        assertStep(ctx, world, lowered, Direction.EAST, true, "geometric-lowered cube toward flush terrain");
        assertStep(ctx, world, flush, Direction.WEST, true, "flush terrain back toward the lowered cube");
        ctx.succeed();
    }

    // ── fixtures and helpers ─────────────────────────────────────────────────

    /** A stone cube with an authored stored fact, premise-asserted at its expected height. */
    private static BlockPos buildFactCube(GameTestHelper ctx, BlockPos relative, int halfSteps, double expectedDy) {
        ServerLevel world = ctx.getLevel();
        BlockPos pos = ctx.absolutePos(relative);
        world.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(world.getChunkAt(pos), pos, halfSteps),
                "fixture: the cube must accept a " + expectedDy + " fact");
        assertExactDy(ctx, world, pos, expectedDy, "fact cube fixture");
        return pos;
    }

    /** A stone cube standing on solid ground, premise-asserted flush (or at the given dy). */
    private static BlockPos groundedCube(GameTestHelper ctx, BlockPos absolute, double expectedDy) {
        ServerLevel world = ctx.getLevel();
        world.setBlock(absolute.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(absolute, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        assertExactDy(ctx, world, absolute, expectedDy, "grounded cube fixture");
        return absolute;
    }

    /** A stone cube lowered purely by geometry: stone on a bottom slab on solid ground. */
    private static BlockPos geometricLoweredCube(GameTestHelper ctx, BlockPos relative) {
        ServerLevel world = ctx.getLevel();
        BlockPos base = ctx.absolutePos(relative);
        world.setBlock(base, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(base.above(), bottomSlab(), Block.UPDATE_ALL);
        BlockPos subject = base.above(2);
        world.setBlock(subject, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        assertExactDy(ctx, world, subject, -0.5, "geometric-lowered cube fixture");
        return subject;
    }

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static void assertExactDy(GameTestHelper ctx, ServerLevel world, BlockPos pos,
                                      double expected, String message) {
        double got = SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
        ctx.assertTrue(Math.abs(got - expected) <= EPS,
                message + ": expected dy " + expected + ", got " + got);
    }

    private static void assertStep(GameTestHelper ctx, ServerLevel world, BlockPos pos,
                                   Direction direction, boolean expected, String message) {
        boolean got = SlabSupport.isSlabHeightStepFace(world, pos, world.getBlockState(pos), direction);
        ctx.assertTrue(got == expected,
                message + ": isSlabHeightStepFace(" + direction + ") expected " + expected + ", got " + got);
    }
}
