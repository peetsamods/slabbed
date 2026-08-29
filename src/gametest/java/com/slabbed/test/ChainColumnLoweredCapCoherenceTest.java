package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A ceiling-bridged vertical chain column reads ONE dy for every member and for the hung
 * addendum below it (maintainer ruling, 2026-08-16): grid height while the cap holds the
 * bridge flush, and the direct lane's merge compensation ({@code capDy + 0.5}, netting
 * below 0.0) once a marked TOP-slab cap lowers past net-zero. These rows pin both sides
 * of that gate — the walk-B direct segment, the bridged-descendant segments, the net-zero
 * compensation ({@code -0.5} cap netting exactly 0.0), and the hung addendum — none of
 * which the flush-ruling port covered. A split column (merged top segment over
 * grid-height descendants) overlaps the segment models and must never come back.
 */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class ChainColumnLoweredCapCoherenceTest {
    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6;

    /**
     * A lowered cap leaves no flush gap to bridge: the merged chain's outline must be exactly
     * the normal chain box moved to the merge dy ([-0.5, +0.5], meeting the cap underside),
     * never the flush-bridge union that doubles the box to 1.5 (maintainer ruling, 2026-08-17:
     * the visual triad must agree, and a doubled outline is exactly the split this row forbids).
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void loweredCapChainOutlineIsTheMovedChainBoxWithoutTheBridgeUnion(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = buildMarkedUpperTopSlab(ctx, world);
        BlockPos chain = slab.below();
        world.setBlock(chain, yChain(), Block.UPDATE_ALL);
        BlockState state = world.getBlockState(chain);
        var outline = state.getShape(world, chain);
        double minY = outline.min(Direction.Axis.Y);
        double maxY = outline.max(Direction.Axis.Y);
        ctx.assertTrue(Math.abs(minY - (-0.5)) <= EPS,
                "merged chain outline bottom must sit at the merge dy; got " + minY);
        ctx.assertTrue(Math.abs(maxY - 0.5) <= EPS,
                "merged chain outline top must meet the lowered cap underside, not the flush"
                        + " bridge span; got " + maxY);
        ctx.succeed();
    }

    /** Direct lane: a single Y-chain directly under the -1.0 marked TOP slab merges to -0.5. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void chainDirectlyUnderLoweredMarkedTopSlabMergesAtHalf(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = buildMarkedUpperTopSlab(ctx, world);
        BlockPos chain = slab.below();
        world.setBlock(chain, yChain(), Block.UPDATE_ALL);
        ctx.assertTrue(world.getBlockState(chain).is(Blocks.CHAIN),
                "scene premise: the chain must survive under the marked top slab, got "
                        + world.getBlockState(chain));
        assertDy(ctx, world, chain, -0.5,
                "direct lane merge compensation: capDy -1.0 + 0.5 must read -0.5, flush under the lowered underside");
        ctx.succeed();
    }

    /** Column coherence: every segment of a three-chain column under the -1.0 cap reads the SAME -0.5. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void threeChainColumnUnderLoweredMarkedTopSlabReadsOneMergeValue(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = buildMarkedUpperTopSlab(ctx, world);
        BlockPos top = slab.below();
        BlockPos mid = slab.below(2);
        BlockPos bottom = slab.below(3);
        world.setBlock(top, yChain(), Block.UPDATE_ALL);
        world.setBlock(mid, yChain(), Block.UPDATE_ALL);
        world.setBlock(bottom, yChain(), Block.UPDATE_ALL);
        assertDy(ctx, world, top, -0.5,
                "column coherence: the direct top segment merges at -0.5 under the -1.0 cap");
        assertDy(ctx, world, mid, -0.5,
                "column coherence: the first descendant must follow the column merge value, "
                        + "not split to grid height under the merged segment above it");
        assertDy(ctx, world, bottom, -0.5,
                "column coherence: the deep descendant must follow the column merge value too");
        ctx.succeed();
    }

    /** Net-zero compensation: a chain under a stored -0.5 TOP slab nets exactly 0.0 — grid, no overshoot. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void chainUnderNetZeroLoweredTopSlabHangsAtGrid(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = buildStoredHalfLoweredTopSlab(ctx, world);
        BlockPos chain = slab.below();
        world.setBlock(chain, yChain(), Block.UPDATE_ALL);
        assertDy(ctx, world, chain, 0.0,
                "net-zero compensation: capDy -0.5 + 0.5 must net exactly 0.0, not overshoot below grid");
        ctx.succeed();
    }

    /** Net-zero column: the bridged column keeps its grid-height bridge when the merge nets flush. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void chainColumnUnderNetZeroLoweredTopSlabStaysGrid(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = buildStoredHalfLoweredTopSlab(ctx, world);
        BlockPos top = slab.below();
        BlockPos bottom = slab.below(2);
        world.setBlock(top, yChain(), Block.UPDATE_ALL);
        world.setBlock(bottom, yChain(), Block.UPDATE_ALL);
        assertDy(ctx, world, top, 0.0,
                "net-zero column: the direct segment stays at grid height under the -0.5 cap");
        assertDy(ctx, world, bottom, 0.0,
                "net-zero column: the descendant stays at grid height with it — no half-step sink");
        ctx.succeed();
    }

    /** The hung addendum below a merged column follows the column's merge value, not grid height. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void hangingLanternUnderMergedChainColumnFollowsColumn(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = buildMarkedUpperTopSlab(ctx, world);
        BlockPos top = slab.below();
        BlockPos bottom = slab.below(2);
        BlockPos lantern = slab.below(3);
        world.setBlock(top, yChain(), Block.UPDATE_ALL);
        world.setBlock(bottom, yChain(), Block.UPDATE_ALL);
        world.setBlock(lantern, Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), Block.UPDATE_ALL);
        ctx.assertTrue(world.getBlockState(lantern).is(Blocks.LANTERN),
                "scene premise: the hanging lantern must survive under the chain column, got "
                        + world.getBlockState(lantern));
        assertDy(ctx, world, bottom, -0.5,
                "scene premise: the carrier chain reads the column merge value -0.5");
        assertDy(ctx, world, lantern, -0.5,
                "hung addendum: the lantern below the merged column must follow the column's -0.5, "
                        + "not stay at grid height clipping into its carrier");
        ctx.succeed();
    }

    // ── rigs and helpers ──

    /** Marked side-UPPER TOP slab (-1.0, authored marker beside a genuine compound stack). */
    private static BlockPos buildMarkedUpperTopSlab(GameTestHelper ctx, ServerLevel world) {
        BlockPos base = ctx.absolutePos(new BlockPos(3, 1, 2));
        world.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        world.setBlock(base.above(1),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        world.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        world.setBlock(base.above(3),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        BlockPos fullBlock = base.above(4);
        world.setBlock(fullBlock, Blocks.STONE.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(world, fullBlock, world.getBlockState(fullBlock));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(world, fullBlock, world.getBlockState(fullBlock));
        BlockPos slab = fullBlock.west();
        world.setBlock(slab, topSlab(), 2);
        SlabAnchorAttachment.addCompoundVisibleSideUpperSlab(world, slab, world.getBlockState(slab),
                fullBlock, world.getBlockState(fullBlock));
        double slabDy = SlabSupport.getYOffset(world, slab, world.getBlockState(slab));
        ctx.assertTrue(Math.abs(slabDy + 1.0) <= EPS,
                "scene premise: the marked side-UPPER top slab must read -1.0, got " + slabDy);
        return slab;
    }

    /** TOP slab carrying a stored -0.5 placement fact — the net-zero cap. */
    private static BlockPos buildStoredHalfLoweredTopSlab(GameTestHelper ctx, ServerLevel world) {
        BlockPos slab = ctx.absolutePos(new BlockPos(2, 4, 2));
        world.setBlock(slab, topSlab(), 2);
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(world.getChunkAt(slab), slab, -1),
                "scene premise: the -0.5 placement fact must be storable on the top slab");
        assertDy(ctx, world, slab, -0.5,
                "scene premise: the stored top slab must read -0.5");
        return slab;
    }

    private static void assertDy(GameTestHelper ctx, ServerLevel world, BlockPos pos, double expected,
                                 String message) {
        double got = SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
        ctx.assertTrue(Math.abs(got - expected) <= EPS,
                message + ": expected dy " + expected + ", got " + got);
    }

    private static BlockState topSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    private static BlockState yChain() {
        return Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
    }
}
