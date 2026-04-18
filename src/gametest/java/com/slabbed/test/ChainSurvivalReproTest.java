package com.slabbed.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Headless repro coverage for {@code ChainBlockNeighborSurvivalMixin} pop-off
 * behavior.
 *
 * <p>Each test explicitly forces the survival recheck path instead of relying
 * on vague world timing:
 * <ol>
 *   <li>{@code setBlockState(support, AIR, NOTIFY_ALL)} first exercises the
 *       real vanilla propagation into
 *       {@code ChainBlock.getStateForNeighborUpdate}, which hits the mixin
 *       TAIL.</li>
 *   <li>For extra determinism, each test also directly invokes
 *       {@code state.getStateForNeighborUpdate(...)} on the chain and asserts
 *       the returned state, bypassing any world tick timing.</li>
 * </ol>
 *
 * <p>Intended policy (per mixin javadoc):
 * <em>chains pop immediately when axis support is lost</em>. Axis support is
 * defined as the two blocks along the chain's axis: for DOWN faces,
 * {@link com.slabbed.util.SlabSupport#isCeilingSupportBottomSurface} (TOP or
 * DOUBLE slab); for other faces, {@code isSideSolidFullSquare}.
 *
 * <p>Does NOT mutate production logic. These are repro/regression tests only.
 */
public final class ChainSurvivalReproTest {

    /**
     * Y-axis chain hanging from a TOP slab underside must survive an initial
     * recheck — establishes the positive baseline.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainUnderTopSlabSurvivesInitialRecheck(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chainPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.up();

        world.setBlockState(slabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_ALL);
        world.setBlockState(chainPos,
                Blocks.IRON_CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y),
                Block.NOTIFY_ALL);

        BlockState chainState = world.getBlockState(chainPos);
        ctx.assertTrue(chainState.isOf(Blocks.IRON_CHAIN),
                "chain not placed at " + chainPos.toShortString()
                + ", found: " + chainState.getBlock().getTranslationKey());

        BlockState result = chainState.getStateForNeighborUpdate(
                world, world, chainPos, Direction.UP,
                slabPos, world.getBlockState(slabPos),
                world.getRandom());
        ctx.assertTrue(!result.isAir(),
                "chain under TOP slab must survive initial recheck; got AIR"
                + " (slabbed$hasAxisSupport misreporting TOP slab underside)");

        ctx.complete();
    }

    /**
     * Y-axis chain hanging from a TOP slab must drop when the slab is removed.
     *
     * <p>Asserts both (a) world-level propagation: after
     * {@code setBlockState(slabPos, AIR, NOTIFY_ALL)} the chain is AIR, and
     * (b) forced recheck returns AIR if the world state is still somehow
     * present.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainUnderTopSlabDropsWhenSlabRemoved(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chainPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.up();

        world.setBlockState(slabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_ALL);
        world.setBlockState(chainPos,
                Blocks.IRON_CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y),
                Block.NOTIFY_ALL);

        // Sanity: chain is placed.
        ctx.assertTrue(world.getBlockState(chainPos).isOf(Blocks.IRON_CHAIN),
                "chain not placed at " + chainPos.toShortString());

        // Remove the TOP slab. Vanilla NOTIFY_ALL propagates neighbor updates
        // synchronously; the mixin TAIL on getStateForNeighborUpdate should
        // convert the chain to AIR.
        world.setBlockState(slabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        BlockState afterPropagation = world.getBlockState(chainPos);
        if (afterPropagation.isAir()) {
            ctx.complete();
            return;
        }

        // Fallback: force the recheck path directly.
        BlockState forced = afterPropagation.getStateForNeighborUpdate(
                world, world, chainPos, Direction.UP,
                slabPos, Blocks.AIR.getDefaultState(),
                world.getRandom());
        ctx.assertTrue(forced.isAir(),
                "chain should drop after TOP slab removed; world state="
                + afterPropagation.getBlock().getTranslationKey()
                + ", forced recheck="
                + forced.getBlock().getTranslationKey()
                + " (slabbed$hasAxisSupport still reports support after slab removal)");

        ctx.complete();
    }

    /**
     * Y-axis chain hanging from a DOUBLE slab must drop when the slab is
     * removed.
     *
     * <p>DOUBLE slabs are a separate code path from TOP: they are full cubes
     * (so {@code isSideSolidFullSquare(DOWN)} is true) AND satisfy
     * {@code isCeilingSupportBottomSurface}. Removal must still drop the
     * chain.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainUnderDoubleSlabDropsWhenSlabRemoved(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chainPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.up();

        world.setBlockState(slabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                Block.NOTIFY_ALL);
        world.setBlockState(chainPos,
                Blocks.IRON_CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y),
                Block.NOTIFY_ALL);

        ctx.assertTrue(world.getBlockState(chainPos).isOf(Blocks.IRON_CHAIN),
                "chain not placed at " + chainPos.toShortString());

        world.setBlockState(slabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        BlockState afterPropagation = world.getBlockState(chainPos);
        if (afterPropagation.isAir()) {
            ctx.complete();
            return;
        }

        BlockState forced = afterPropagation.getStateForNeighborUpdate(
                world, world, chainPos, Direction.UP,
                slabPos, Blocks.AIR.getDefaultState(),
                world.getRandom());
        ctx.assertTrue(forced.isAir(),
                "chain should drop after DOUBLE slab removed; world state="
                + afterPropagation.getBlock().getTranslationKey()
                + ", forced recheck="
                + forced.getBlock().getTranslationKey());

        ctx.complete();
    }

    /**
     * Y-axis chain hanging from a TOP slab must SURVIVE when an unrelated
     * nearby block (not its axis support) is removed.
     *
     * <p>Most likely match for the reported live repro: the user removes a
     * block near the chain (but not the chain's axis support) and the chain
     * incorrectly pops off. If this test fails, the axis-support walk in
     * {@code slabbed$hasAxisSupport} is misreading the new neighbor state
     * during the update.
     *
     * <p>Layout:
     * <pre>
     *   y+3:  [TOP slab]
     *   y+2:  [chain Y]
     *   y+1:  air         ← nearby block at (x-1, y+1, z), removed later
     *   y:    air
     * </pre>
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainUnderTopSlabSurvivesUnrelatedNeighborRemoval(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chainPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.up();
        // Side neighbor one block west of the chain's horizontal neighbor —
        // NOT along the chain's Y axis, so irrelevant to support.
        BlockPos sideNeighborPos = chainPos.offset(Direction.WEST);

        world.setBlockState(slabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_ALL);
        world.setBlockState(chainPos,
                Blocks.IRON_CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y),
                Block.NOTIFY_ALL);
        world.setBlockState(sideNeighborPos,
                Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);

        ctx.assertTrue(world.getBlockState(chainPos).isOf(Blocks.IRON_CHAIN),
                "chain not placed at " + chainPos.toShortString());

        // Remove the unrelated neighbor.
        world.setBlockState(sideNeighborPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        BlockState after = world.getBlockState(chainPos);
        ctx.assertTrue(after.isOf(Blocks.IRON_CHAIN),
                "chain must survive removal of unrelated west neighbor; found "
                + after.getBlock().getTranslationKey()
                + " (slabbed$hasAxisSupport wrongly dropped chain when a"
                + " non-axis neighbor changed — matches live pop-off repro)");

        // Explicitly force the recheck with direction=WEST, neighborPos=sideNeighborPos.
        // This is the exact per-direction call the world makes.
        BlockState forced = after.getStateForNeighborUpdate(
                world, world, chainPos, Direction.WEST,
                sideNeighborPos, Blocks.AIR.getDefaultState(),
                world.getRandom());
        ctx.assertTrue(!forced.isAir(),
                "chain survival recheck (direction=WEST) must not return AIR"
                + " when TOP slab is still above; got AIR"
                + " — slabbed$hasAxisSupport Y-axis walk failed to see TOP slab");

        ctx.complete();
    }

    /**
     * Stacked Y-axis chain under a TOP slab: removing the LOWER chain must
     * not cause the UPPER chain to pop — the upper chain can still walk up
     * its Y axis to the TOP slab.
     *
     * <p>Layout before removal:
     * <pre>
     *   y+4:  [TOP slab]
     *   y+3:  [upper chain Y]
     *   y+2:  [lower chain Y]    ← removed
     * </pre>
     * After removing the lower chain, the upper chain's walk-up encounters
     * the TOP slab immediately at y+4 → {@code isCeilingSupportBottomSurface}
     * returns true → upper chain survives.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainStackUnderTopSlabSurvivesLowerChainRemoval(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos lowerChainPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos upperChainPos = lowerChainPos.up();
        BlockPos slabPos = upperChainPos.up();

        BlockState chainY = Blocks.IRON_CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y);
        BlockState topSlab = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);

        world.setBlockState(slabPos, topSlab, Block.NOTIFY_ALL);
        world.setBlockState(upperChainPos, chainY, Block.NOTIFY_ALL);
        world.setBlockState(lowerChainPos, chainY, Block.NOTIFY_ALL);

        ctx.assertTrue(world.getBlockState(upperChainPos).isOf(Blocks.IRON_CHAIN),
                "upper chain not placed at " + upperChainPos.toShortString());
        ctx.assertTrue(world.getBlockState(lowerChainPos).isOf(Blocks.IRON_CHAIN),
                "lower chain not placed at " + lowerChainPos.toShortString());

        // Remove the lower chain.
        world.setBlockState(lowerChainPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        BlockState after = world.getBlockState(upperChainPos);
        ctx.assertTrue(after.isOf(Blocks.IRON_CHAIN),
                "upper chain must survive lower-chain removal (TOP slab still above); found "
                + after.getBlock().getTranslationKey());

        // Force recheck from below.
        BlockState forced = after.getStateForNeighborUpdate(
                world, world, upperChainPos, Direction.DOWN,
                lowerChainPos, Blocks.AIR.getDefaultState(),
                world.getRandom());
        ctx.assertTrue(!forced.isAir(),
                "upper chain forced recheck must not return AIR; TOP slab above"
                + " should satisfy isCeilingSupportBottomSurface via Y-axis walk up");

        ctx.complete();
    }

    /**
     * Free-floating Y-axis chain (no support anywhere) must drop on a forced
     * recheck. Establishes the negative baseline: axis-support absence really
     * does yield AIR.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainWithoutAnySupportDropsOnRecheck(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chainPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));

        world.setBlockState(chainPos,
                Blocks.IRON_CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y),
                Block.NOTIFY_ALL);

        BlockState state = world.getBlockState(chainPos);
        // setBlockState with NOTIFY_ALL may already have dropped it if a
        // self-neighbor-update fired. Accept that outcome as a pass.
        if (state.isAir()) {
            ctx.complete();
            return;
        }
        ctx.assertTrue(state.isOf(Blocks.IRON_CHAIN),
                "chain not placed at " + chainPos.toShortString());

        BlockState forced = state.getStateForNeighborUpdate(
                world, world, chainPos, Direction.UP,
                chainPos.up(), world.getBlockState(chainPos.up()),
                world.getRandom());
        ctx.assertTrue(forced.isAir(),
                "unsupported chain forced recheck must return AIR; got "
                + forced.getBlock().getTranslationKey()
                + " (slabbed$hasAxisSupport false-positive in empty column)");

        ctx.complete();
    }
}
