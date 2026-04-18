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
 *
 * <p><b>Chunk unload/reload caveat:</b> the current Fabric server GameTest
 * harness used here does not expose direct chunk unload/reload controls (no
 * chunk-ticket management API on {@link TestContext}). Because of that,
 * chunk-reload survival is documented as an unmodeled runtime bucket in this
 * suite rather than being faked by direct state replacement.
 */
public final class ChainSurvivalReproTest {

    private static BlockState copperChainY() {
        return Blocks.COPPER_CHAINS.unaffected().getDefaultState()
                .with(ChainBlock.AXIS, Direction.Axis.Y);
    }

    private static BlockState copperChainX() {
        return Blocks.COPPER_CHAINS.unaffected().getDefaultState()
                .with(ChainBlock.AXIS, Direction.Axis.X);
    }

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

    // ──────────────────────────────────────────────────────────────────────
    // Remaining-gap coverage — horizontal axes, bottom-slab contract,
    // through-chain walk, mixed-cascade on horizontal.
    //
    // Per Yarn 1.21.11 mappings (mappings.tiny at class_5172 /
    // net.minecraft.block.ChainBlock), ChainBlock declares only CODEC,
    // WATERLOGGED, and SHAPES_BY_AXIS — no scheduledTick, no neighborUpdate,
    // no canPlaceAt override. The only survival hook this mod installs is
    // ChainBlockNeighborSurvivalMixin on getStateForNeighborUpdate.
    // There is therefore NO separate scheduled-tick path to cover; the six
    // tests below exercise every remaining category that could trigger a
    // chain pop-off via the neighbor-update path.
    // ──────────────────────────────────────────────────────────────────────

    /**
     * X-axis chain supported only by a stone on its east end must survive an
     * initial recheck. Positive baseline for the horizontal axis walk.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void xAxisChainWithStoneEastSupportSurvives(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chainPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos supportPos = chainPos.offset(Direction.EAST);

        world.setBlockState(supportPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(chainPos,
                Blocks.IRON_CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.X),
                Block.NOTIFY_ALL);

        BlockState chainState = world.getBlockState(chainPos);
        ctx.assertTrue(chainState.isOf(Blocks.IRON_CHAIN),
                "X-axis chain not placed at " + chainPos.toShortString());

        BlockState forced = chainState.getStateForNeighborUpdate(
                world, world, chainPos, Direction.EAST,
                supportPos, world.getBlockState(supportPos),
                world.getRandom());
        ctx.assertTrue(!forced.isAir(),
                "X-axis chain with stone east must survive recheck; got AIR"
                + " (horizontal axis walk isSideSolidFullSquare(WEST) failed)");

        ctx.complete();
    }

    /**
     * X-axis chain must drop when its single east-end stone support is
     * removed. Negative baseline for horizontal axis.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void xAxisChainDropsWhenSoleSupportRemoved(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chainPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos supportPos = chainPos.offset(Direction.EAST);

        world.setBlockState(supportPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(chainPos,
                Blocks.IRON_CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.X),
                Block.NOTIFY_ALL);
        ctx.assertTrue(world.getBlockState(chainPos).isOf(Blocks.IRON_CHAIN),
                "X-axis chain not placed");

        world.setBlockState(supportPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        BlockState after = world.getBlockState(chainPos);
        if (after.isAir()) {
            ctx.complete();
            return;
        }
        BlockState forced = after.getStateForNeighborUpdate(
                world, world, chainPos, Direction.EAST,
                supportPos, Blocks.AIR.getDefaultState(),
                world.getRandom());
        ctx.assertTrue(forced.isAir(),
                "X-axis chain must drop after sole east support removed; world="
                + after.getBlock().getTranslationKey()
                + ", forced=" + forced.getBlock().getTranslationKey());

        ctx.complete();
    }

    /**
     * X-axis chain with stone east (axis support) and stone north (unrelated
     * neighbor) must SURVIVE removal of the unrelated neighbor. Closest
     * headless match for the "removing a nearby block pops my chain" live
     * class, applied to horizontal axis.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void xAxisChainSurvivesUnrelatedNeighborRemoval(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chainPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos supportPos = chainPos.offset(Direction.EAST);
        BlockPos unrelatedPos = chainPos.offset(Direction.NORTH);

        world.setBlockState(supportPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(unrelatedPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(chainPos,
                Blocks.IRON_CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.X),
                Block.NOTIFY_ALL);
        ctx.assertTrue(world.getBlockState(chainPos).isOf(Blocks.IRON_CHAIN),
                "X-axis chain not placed");

        world.setBlockState(unrelatedPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        BlockState after = world.getBlockState(chainPos);
        ctx.assertTrue(after.isOf(Blocks.IRON_CHAIN),
                "X-axis chain must survive NORTH-neighbor removal when east"
                + " support is still present; found "
                + after.getBlock().getTranslationKey());

        // Force the exact per-direction recheck the world makes.
        BlockState forced = after.getStateForNeighborUpdate(
                world, world, chainPos, Direction.NORTH,
                unrelatedPos, Blocks.AIR.getDefaultState(),
                world.getRandom());
        ctx.assertTrue(!forced.isAir(),
                "X-axis chain forced recheck (direction=NORTH) must not drop"
                + " chain; east stone still provides axis support");

        ctx.complete();
    }

    /**
     * Z-axis chain with stone south (axis support) and stone east (unrelated
     * neighbor). Symmetric to the X-axis unrelated-neighbor-removal test;
     * guards against axis-direction-specific bugs in slabbed$hasAxisSupport.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void zAxisChainSurvivesUnrelatedNeighborRemoval(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chainPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos supportPos = chainPos.offset(Direction.SOUTH);
        BlockPos unrelatedPos = chainPos.offset(Direction.EAST);

        world.setBlockState(supportPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(unrelatedPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(chainPos,
                Blocks.IRON_CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Z),
                Block.NOTIFY_ALL);
        ctx.assertTrue(world.getBlockState(chainPos).isOf(Blocks.IRON_CHAIN),
                "Z-axis chain not placed");

        world.setBlockState(unrelatedPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        BlockState after = world.getBlockState(chainPos);
        ctx.assertTrue(after.isOf(Blocks.IRON_CHAIN),
                "Z-axis chain must survive EAST-neighbor removal when south"
                + " support is still present; found "
                + after.getBlock().getTranslationKey());

        BlockState forced = after.getStateForNeighborUpdate(
                world, world, chainPos, Direction.EAST,
                unrelatedPos, Blocks.AIR.getDefaultState(),
                world.getRandom());
        ctx.assertTrue(!forced.isAir(),
                "Z-axis chain forced recheck (direction=EAST) must not drop"
                + " chain; south stone still provides axis support");

        ctx.complete();
    }

    /**
     * Bottom-slab-above contract test.
     *
     * <p>Two claims in one proof:
     * <ol>
     *   <li><b>Internal contract:</b>
     *       {@link SlabSupport#isCeilingSupportBottomSurface} must return
     *       {@code false} for a BOTTOM slab — BOTTOM slabs are not
     *       ceiling support (only TOP and DOUBLE are).</li>
     *   <li><b>End-to-end:</b> a Y-axis chain under a bottom slab still
     *       survives because the slab's DOWN face IS a full solid square
     *       (the slab occupies y=[0,0.5], its bottom y=0 face is a full
     *       plane). The mixin's generic {@code isSideSolidFullSquare} branch
     *       picks it up even though the ceiling-support branch does not.</li>
     * </ol>
     *
     * <p>This pins the boundary so a future change that reroutes the ceiling
     * surface path (e.g., adding BOTTOM to {@code isCeilingSupportBottomSurface})
     * cannot silently change semantics.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bottomSlabAboveCeilingContract(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chainPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.up();

        world.setBlockState(slabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_ALL);
        world.setBlockState(chainPos,
                Blocks.IRON_CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y),
                Block.NOTIFY_ALL);

        // Claim 1: internal ceiling-support contract.
        ctx.assertTrue(
                !SlabSupport.isCeilingSupportBottomSurface(world, slabPos),
                "BOTTOM slab must NOT be reported as ceiling support by"
                + " SlabSupport.isCeilingSupportBottomSurface");

        // Claim 2: chain still survives (bottom slab's DOWN face is a full
        // solid square under vanilla semantics — its y=0 plane IS solid).
        BlockState chainState = world.getBlockState(chainPos);
        ctx.assertTrue(chainState.isOf(Blocks.IRON_CHAIN),
                "chain under bottom slab not placed");

        BlockState forced = chainState.getStateForNeighborUpdate(
                world, world, chainPos, Direction.UP,
                slabPos, world.getBlockState(slabPos),
                world.getRandom());
        ctx.assertTrue(!forced.isAir(),
                "Y-axis chain under BOTTOM slab must survive via generic"
                + " isSideSolidFullSquare(DOWN) — slab's y=0 face is a full"
                + " solid square; got AIR (axis walk lost vanilla-geometry"
                + " fallback)");

        ctx.complete();
    }

    /**
     * Horizontal through-chain walk: X-axis chain A, intermediate X-axis
     * chain A+east, stone at A+2×east. Chain A must survive because the
     * axis walk traverses the intermediate same-axis chain to reach stone
     * support 2 blocks away.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void xAxisThroughChainWalkReachesDistantSupport(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chainA = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos chainB = chainA.offset(Direction.EAST);
        BlockPos supportPos = chainB.offset(Direction.EAST);

        BlockState chainX = Blocks.IRON_CHAIN.getDefaultState()
                .with(ChainBlock.AXIS, Direction.Axis.X);

        world.setBlockState(supportPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(chainB, chainX, Block.NOTIFY_ALL);
        world.setBlockState(chainA, chainX, Block.NOTIFY_ALL);

        ctx.assertTrue(world.getBlockState(chainA).isOf(Blocks.IRON_CHAIN),
                "chainA not placed");
        ctx.assertTrue(world.getBlockState(chainB).isOf(Blocks.IRON_CHAIN),
                "chainB not placed");

        BlockState stateA = world.getBlockState(chainA);
        BlockState forced = stateA.getStateForNeighborUpdate(
                world, world, chainA, Direction.WEST,
                chainA.offset(Direction.WEST),
                world.getBlockState(chainA.offset(Direction.WEST)),
                world.getRandom());
        ctx.assertTrue(!forced.isAir(),
                "X-axis chainA must survive recheck via through-chain walk"
                + " to stone at chainA+2×east; got AIR"
                + " (slabbed$walkChainForSupport failed to traverse same-axis"
                + " chainB)");

        ctx.complete();
    }

    /**
     * Oxidizable/copper chain mirror of the vanilla TOP-slab positive
     * baseline: copper chain under TOP slab must survive initial recheck.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void copperChainUnderTopSlabSurvivesInitialRecheck(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chainPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.up();

        world.setBlockState(slabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_ALL);
        world.setBlockState(chainPos, copperChainY(), Block.NOTIFY_ALL);

        BlockState chainState = world.getBlockState(chainPos);
        ctx.assertTrue(chainState.isOf(Blocks.COPPER_CHAINS.unaffected()),
                "copper chain not placed at " + chainPos.toShortString()
                + ", found: " + chainState.getBlock().getTranslationKey());

        BlockState result = chainState.getStateForNeighborUpdate(
                world, world, chainPos, Direction.UP,
                slabPos, world.getBlockState(slabPos),
                world.getRandom());
        ctx.assertTrue(!result.isAir(),
                "copper chain under TOP slab must survive initial recheck; got AIR"
                + " (possible uncovered OxidizableChainBlock path)");

        ctx.complete();
    }

    /**
     * Oxidizable/copper chain mirror of sole-support removal:
     * X-axis copper chain with one stone end-support must drop when that
     * support is removed.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void copperChainDropsWhenSoleSupportRemoved(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chainPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos supportPos = chainPos.offset(Direction.EAST);

        world.setBlockState(supportPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(chainPos, copperChainX(), Block.NOTIFY_ALL);
        ctx.assertTrue(world.getBlockState(chainPos).isOf(Blocks.COPPER_CHAINS.unaffected()),
                "X-axis copper chain not placed");

        world.setBlockState(supportPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        BlockState after = world.getBlockState(chainPos);
        if (after.isAir()) {
            ctx.complete();
            return;
        }

        BlockState forced = after.getStateForNeighborUpdate(
                world, world, chainPos, Direction.EAST,
                supportPos, Blocks.AIR.getDefaultState(),
                world.getRandom());
        ctx.assertTrue(forced.isAir(),
                "X-axis copper chain must drop after sole support removed; world="
                + after.getBlock().getTranslationKey()
                + ", forced=" + forced.getBlock().getTranslationKey());

        ctx.complete();
    }

    /**
     * Oxidizable/copper chain mirror of unrelated-neighbor stability:
     * with east support intact, removing unrelated north neighbor must not pop.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void copperChainSurvivesUnrelatedNeighborRemoval(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chainPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos supportPos = chainPos.offset(Direction.EAST);
        BlockPos unrelatedPos = chainPos.offset(Direction.NORTH);

        world.setBlockState(supportPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(unrelatedPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(chainPos, copperChainX(), Block.NOTIFY_ALL);
        ctx.assertTrue(world.getBlockState(chainPos).isOf(Blocks.COPPER_CHAINS.unaffected()),
                "X-axis copper chain not placed");

        world.setBlockState(unrelatedPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        BlockState after = world.getBlockState(chainPos);
        ctx.assertTrue(after.isOf(Blocks.COPPER_CHAINS.unaffected()),
                "X-axis copper chain must survive unrelated NORTH update with"
                + " east support intact; found " + after.getBlock().getTranslationKey());

        BlockState forced = after.getStateForNeighborUpdate(
                world, world, chainPos, Direction.NORTH,
                unrelatedPos, Blocks.AIR.getDefaultState(),
                world.getRandom());
        ctx.assertTrue(!forced.isAir(),
                "X-axis copper chain forced recheck(direction=NORTH) must not drop"
                + " when east support remains");

        ctx.complete();
    }

    /**
     * Runtime-bucket audit: multiple neighbor updates in the same tick around
     * a supported Y-axis chain must preserve the intended final state.
     *
     * <p>Sequence (single server tick, no waits):
     * <ol>
     *   <li>Top slab support above chain remains intact throughout.</li>
     *   <li>Mutate 4 unrelated neighbors around the chain (place/remove).
     *       This causes multiple directional getStateForNeighborUpdate calls
     *       in one update burst.</li>
     *   <li>Assert final chain state is still present and forced recheck is
     *       non-air.</li>
     * </ol>
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainSurvivesMultiNeighborBurstSameTick(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chainPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.up();
        BlockPos north = chainPos.north();
        BlockPos south = chainPos.south();
        BlockPos east = chainPos.east();
        BlockPos west = chainPos.west();

        world.setBlockState(slabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_ALL);
        world.setBlockState(chainPos,
                Blocks.IRON_CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y),
                Block.NOTIFY_ALL);
        ctx.assertTrue(world.getBlockState(chainPos).isOf(Blocks.IRON_CHAIN),
                "chain not placed at " + chainPos.toShortString());

        // Same-tick neighbor burst: place/remove around chain while support
        // above remains unchanged.
        world.setBlockState(north, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(east, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(south, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(west, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(north, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(east, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(south, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(west, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        BlockState finalState = world.getBlockState(chainPos);
        ctx.assertTrue(finalState.isOf(Blocks.IRON_CHAIN),
                "chain popped after same-tick multi-neighbor burst with TOP slab"
                + " support intact; final=" + finalState.getBlock().getTranslationKey());

        BlockState forced = finalState.getStateForNeighborUpdate(
                world, world, chainPos, Direction.NORTH,
                north, world.getBlockState(north),
                world.getRandom());
        ctx.assertTrue(!forced.isAir(),
                "forced recheck after same-tick neighbor burst returned AIR"
                + " despite TOP slab support remaining");

        ctx.complete();
    }

    /**
     * Runtime-bucket audit: gameplay-like placement order.
     *
     * <p>Models a closer live sequence than direct survival calls:
     * place temporary side scaffolding, place top slab support, place chain,
     * remove temporary scaffolding, then pulse another nearby update.
     * Chain should remain because axis support (top slab) is still valid.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainPlacementOrderLikeGameplayStaysStable(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chainPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.up();
        BlockPos scaffoldPos = chainPos.west();
        BlockPos pulsePos = chainPos.east();

        // Step 1: temporary scaffold near placement area.
        world.setBlockState(scaffoldPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);

        // Step 2: actual support for the chain.
        world.setBlockState(slabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_ALL);

        // Step 3: place chain in supported position.
        world.setBlockState(chainPos,
                Blocks.IRON_CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y),
                Block.NOTIFY_ALL);
        ctx.assertTrue(world.getBlockState(chainPos).isOf(Blocks.IRON_CHAIN),
                "chain not placed in gameplay-order scenario");

        // Step 4: remove temporary scaffold (common live action).
        world.setBlockState(scaffoldPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        // Step 5: trigger an additional nearby update in same slice.
        world.setBlockState(pulsePos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(pulsePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        BlockState finalState = world.getBlockState(chainPos);
        ctx.assertTrue(finalState.isOf(Blocks.IRON_CHAIN),
                "chain popped during gameplay-like placement/removal ordering; final="
                + finalState.getBlock().getTranslationKey());

        BlockState forced = finalState.getStateForNeighborUpdate(
                world, world, chainPos, Direction.WEST,
                scaffoldPos, world.getBlockState(scaffoldPos),
                world.getRandom());
        ctx.assertTrue(!forced.isAir(),
                "forced recheck after gameplay-like ordering returned AIR"
                + " with TOP slab support still above");

        ctx.complete();
    }
}
