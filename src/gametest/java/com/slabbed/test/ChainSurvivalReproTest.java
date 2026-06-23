package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
 *       {@code state.updateShape(...)} on the chain and asserts
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
 * chunk-ticket management API on {@link GameTestHelper}). Because of that,
 * chunk-reload survival is documented as an unmodeled runtime bucket in this
 * suite rather than being faked by direct state replacement.
 */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class ChainSurvivalReproTest {
    private static final Block CHAIN_BLOCK = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecraft", "chain"));
    private static final Block COPPER_CHAIN_BLOCK = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecraft", "copper_chain"));

    private static Block preferredChainBlock() {
        return COPPER_CHAIN_BLOCK == Blocks.AIR ? CHAIN_BLOCK : COPPER_CHAIN_BLOCK;
    }

    private static boolean isPreferredChain(BlockState state) {
        return state.is(preferredChainBlock());
    }

    private static BlockState copperChainY() {
        return preferredChainBlock().defaultBlockState()
                .setValue(ChainBlock.AXIS, Direction.Axis.Y);
    }

    private static BlockState copperChainX() {
        return preferredChainBlock().defaultBlockState()
                .setValue(ChainBlock.AXIS, Direction.Axis.X);
    }

    /**
     * Y-axis chain hanging from a TOP slab underside must survive an initial
     * recheck — establishes the positive baseline.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void chainUnderTopSlabSurvivesInitialRecheck(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.above();

        world.setBlock(slabPos,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Block.UPDATE_ALL);
        world.setBlock(chainPos,
                CHAIN_BLOCK.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y),
                Block.UPDATE_ALL);

        BlockState chainState = world.getBlockState(chainPos);
        ctx.assertTrue(chainState.is(CHAIN_BLOCK),
                "chain not placed at " + chainPos.toString()
                + ", found: " + chainState.getBlock().getDescriptionId());

        BlockState result = chainState.updateShape(Direction.UP, world.getBlockState(slabPos), world, chainPos, slabPos);
        ctx.assertTrue(!result.isAir(),
                "chain under TOP slab must survive initial recheck; got AIR"
                + " (slabbed$hasAxisSupport misreporting TOP slab underside)");

        ctx.succeed();
    }

    /**
     * Vanilla floating-chain policy: Y-axis chain remains when TOP slab support
     * is removed.
     *
     * <p>Asserts that after support removal, both world state and forced
     * recheck still keep chain state non-air under vanilla floating policy.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void chainUnderTopSlabRemainsWhenSlabRemoved(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.above();

        world.setBlock(slabPos,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Block.UPDATE_ALL);
        world.setBlock(chainPos,
                CHAIN_BLOCK.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y),
                Block.UPDATE_ALL);

        // Sanity: chain is placed.
        ctx.assertTrue(world.getBlockState(chainPos).is(CHAIN_BLOCK),
                "chain not placed at " + chainPos.toString());

        // Remove the TOP slab. Under vanilla floating policy, support loss
        // alone should not force chain removal.
        world.setBlock(slabPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        BlockState afterPropagation = world.getBlockState(chainPos);

        // Fallback: force the recheck path directly.
        BlockState forced = afterPropagation.updateShape(Direction.UP, Blocks.AIR.defaultBlockState(), world, chainPos, slabPos);
        ctx.assertTrue(!forced.isAir(),
                "chain should remain after TOP slab removed under vanilla floating policy; world state="
                + afterPropagation.getBlock().getDescriptionId()
                + ", forced recheck="
                + forced.getBlock().getDescriptionId()
                + " (support-loss should not force AIR)");

        ctx.succeed();
    }

    /**
     * Vanilla floating-chain policy: Y-axis chain remains when DOUBLE slab
     * support is removed.
     *
     * <p>DOUBLE slabs are a separate code path from TOP: they are full cubes
     * (so {@code isSideSolidFullSquare(DOWN)} is true) AND satisfy
     * {@code isCeilingSupportBottomSurface}. Removal must still drop the
     * chain.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void chainUnderDoubleSlabRemainsWhenSlabRemoved(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.above();

        world.setBlock(slabPos,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE),
                Block.UPDATE_ALL);
        world.setBlock(chainPos,
                CHAIN_BLOCK.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y),
                Block.UPDATE_ALL);

        ctx.assertTrue(world.getBlockState(chainPos).is(CHAIN_BLOCK),
                "chain not placed at " + chainPos.toString());

        world.setBlock(slabPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        BlockState afterPropagation = world.getBlockState(chainPos);

        BlockState forced = afterPropagation.updateShape(Direction.UP, Blocks.AIR.defaultBlockState(), world, chainPos, slabPos);
        ctx.assertTrue(!forced.isAir(),
                "chain should remain after DOUBLE slab removed under vanilla floating policy; world state="
                + afterPropagation.getBlock().getDescriptionId()
                + ", forced recheck="
                + forced.getBlock().getDescriptionId());

        ctx.succeed();
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
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void chainUnderTopSlabSurvivesUnrelatedNeighborRemoval(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.above();
        // Side neighbor one block west of the chain's horizontal neighbor —
        // NOT along the chain's Y axis, so irrelevant to support.
        BlockPos sideNeighborPos = chainPos.relative(Direction.WEST);

        world.setBlock(slabPos,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Block.UPDATE_ALL);
        world.setBlock(chainPos,
                CHAIN_BLOCK.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y),
                Block.UPDATE_ALL);
        world.setBlock(sideNeighborPos,
                Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        ctx.assertTrue(world.getBlockState(chainPos).is(CHAIN_BLOCK),
                "chain not placed at " + chainPos.toString());

        // Remove the unrelated neighbor.
        world.setBlock(sideNeighborPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        BlockState after = world.getBlockState(chainPos);
        ctx.assertTrue(after.is(CHAIN_BLOCK),
                "chain must survive removal of unrelated west neighbor; found "
                + after.getBlock().getDescriptionId()
                + " (slabbed$hasAxisSupport wrongly dropped chain when a"
                + " non-axis neighbor changed — matches live pop-off repro)");

        // Explicitly force the recheck with direction=WEST, neighborPos=sideNeighborPos.
        // This is the exact per-direction call the world makes.
        BlockState forced = after.updateShape(Direction.WEST, Blocks.AIR.defaultBlockState(), world, chainPos, sideNeighborPos);
        ctx.assertTrue(!forced.isAir(),
                "chain survival recheck (direction=WEST) must not return AIR"
                + " when TOP slab is still above; got AIR"
                + " — slabbed$hasAxisSupport Y-axis walk failed to see TOP slab");

        ctx.succeed();
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
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void chainStackUnderTopSlabSurvivesLowerChainRemoval(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos lowerChainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos upperChainPos = lowerChainPos.above();
        BlockPos slabPos = upperChainPos.above();

        BlockState chainY = CHAIN_BLOCK.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
        BlockState topSlab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);

        world.setBlock(slabPos, topSlab, Block.UPDATE_ALL);
        world.setBlock(upperChainPos, chainY, Block.UPDATE_ALL);
        world.setBlock(lowerChainPos, chainY, Block.UPDATE_ALL);

        ctx.assertTrue(world.getBlockState(upperChainPos).is(CHAIN_BLOCK),
                "upper chain not placed at " + upperChainPos.toString());
        ctx.assertTrue(world.getBlockState(lowerChainPos).is(CHAIN_BLOCK),
                "lower chain not placed at " + lowerChainPos.toString());

        // Remove the lower chain.
        world.setBlock(lowerChainPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        BlockState after = world.getBlockState(upperChainPos);
        ctx.assertTrue(after.is(CHAIN_BLOCK),
                "upper chain must survive lower-chain removal (TOP slab still above); found "
                + after.getBlock().getDescriptionId());

        // Force recheck from below.
        BlockState forced = after.updateShape(Direction.DOWN, Blocks.AIR.defaultBlockState(), world, upperChainPos, lowerChainPos);
        ctx.assertTrue(!forced.isAir(),
                "upper chain forced recheck must not return AIR; TOP slab above"
                + " should satisfy isCeilingSupportBottomSurface via Y-axis walk up");

        ctx.succeed();
    }

    /**
     * Vanilla floating-chain policy: free-floating Y-axis chain (no support
     * anywhere) survives forced recheck.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void chainWithoutAnySupportSurvivesOnRecheck(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));

        world.setBlock(chainPos,
                CHAIN_BLOCK.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y),
                Block.UPDATE_ALL);

        BlockState state = world.getBlockState(chainPos);
        ctx.assertTrue(state.is(CHAIN_BLOCK),
                "chain not placed at " + chainPos.toString());

        BlockState forced = state.updateShape(Direction.UP, world.getBlockState(chainPos.above()), world, chainPos, chainPos.above());
        ctx.assertTrue(!forced.isAir(),
                "unsupported chain forced recheck must remain non-AIR under vanilla floating policy; got "
                + forced.getBlock().getDescriptionId()
                + " (support-loss should not force chain removal)");

        ctx.succeed();
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
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void xAxisChainWithStoneEastSupportSurvives(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos supportPos = chainPos.relative(Direction.EAST);

        world.setBlock(supportPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(chainPos,
                CHAIN_BLOCK.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.X),
                Block.UPDATE_ALL);

        BlockState chainState = world.getBlockState(chainPos);
        ctx.assertTrue(chainState.is(CHAIN_BLOCK),
                "X-axis chain not placed at " + chainPos.toString());

        BlockState forced = chainState.updateShape(Direction.EAST, world.getBlockState(supportPos), world, chainPos, supportPos);
        ctx.assertTrue(!forced.isAir(),
                "X-axis chain with stone east must survive recheck; got AIR"
                + " (horizontal axis walk isSideSolidFullSquare(WEST) failed)");

        ctx.succeed();
    }

    /**
     * Vanilla floating-chain policy: X-axis chain remains when its sole
     * east-end support is removed.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void xAxisChainRemainsWhenSoleSupportRemoved(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos supportPos = chainPos.relative(Direction.EAST);

        world.setBlock(supportPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(chainPos,
                CHAIN_BLOCK.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.X),
                Block.UPDATE_ALL);
        ctx.assertTrue(world.getBlockState(chainPos).is(CHAIN_BLOCK),
                "X-axis chain not placed");

        world.setBlock(supportPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        BlockState after = world.getBlockState(chainPos);
        BlockState forced = after.updateShape(Direction.EAST, Blocks.AIR.defaultBlockState(), world, chainPos, supportPos);
        ctx.assertTrue(!forced.isAir(),
                "X-axis chain must remain after sole east support removed under vanilla floating policy; world="
                + after.getBlock().getDescriptionId()
                + ", forced=" + forced.getBlock().getDescriptionId());

        ctx.succeed();
    }

    /**
     * X-axis chain with stone east (axis support) and stone north (unrelated
     * neighbor) must SURVIVE removal of the unrelated neighbor. Closest
     * headless match for the "removing a nearby block pops my chain" live
     * class, applied to horizontal axis.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void xAxisChainSurvivesUnrelatedNeighborRemoval(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos supportPos = chainPos.relative(Direction.EAST);
        BlockPos unrelatedPos = chainPos.relative(Direction.NORTH);

        world.setBlock(supportPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(unrelatedPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(chainPos,
                CHAIN_BLOCK.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.X),
                Block.UPDATE_ALL);
        ctx.assertTrue(world.getBlockState(chainPos).is(CHAIN_BLOCK),
                "X-axis chain not placed");

        world.setBlock(unrelatedPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        BlockState after = world.getBlockState(chainPos);
        ctx.assertTrue(after.is(CHAIN_BLOCK),
                "X-axis chain must survive NORTH-neighbor removal when east"
                + " support is still present; found "
                + after.getBlock().getDescriptionId());

        // Force the exact per-direction recheck the world makes.
        BlockState forced = after.updateShape(Direction.NORTH, Blocks.AIR.defaultBlockState(), world, chainPos, unrelatedPos);
        ctx.assertTrue(!forced.isAir(),
                "X-axis chain forced recheck (direction=NORTH) must not drop"
                + " chain; east stone still provides axis support");

        ctx.succeed();
    }

    /**
     * Z-axis chain with stone south (axis support) and stone east (unrelated
     * neighbor). Symmetric to the X-axis unrelated-neighbor-removal test;
     * guards against axis-direction-specific bugs in slabbed$hasAxisSupport.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void zAxisChainSurvivesUnrelatedNeighborRemoval(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos supportPos = chainPos.relative(Direction.SOUTH);
        BlockPos unrelatedPos = chainPos.relative(Direction.EAST);

        world.setBlock(supportPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(unrelatedPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(chainPos,
                CHAIN_BLOCK.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Z),
                Block.UPDATE_ALL);
        ctx.assertTrue(world.getBlockState(chainPos).is(CHAIN_BLOCK),
                "Z-axis chain not placed");

        world.setBlock(unrelatedPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        BlockState after = world.getBlockState(chainPos);
        ctx.assertTrue(after.is(CHAIN_BLOCK),
                "Z-axis chain must survive EAST-neighbor removal when south"
                + " support is still present; found "
                + after.getBlock().getDescriptionId());

        BlockState forced = after.updateShape(Direction.EAST, Blocks.AIR.defaultBlockState(), world, chainPos, unrelatedPos);
        ctx.assertTrue(!forced.isAir(),
                "Z-axis chain forced recheck (direction=EAST) must not drop"
                + " chain; south stone still provides axis support");

        ctx.succeed();
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
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void bottomSlabAboveCeilingContract(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.above();

        world.setBlock(slabPos,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.UPDATE_ALL);
        world.setBlock(chainPos,
                CHAIN_BLOCK.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y),
                Block.UPDATE_ALL);

        // Claim 1: internal ceiling-support contract.
        ctx.assertTrue(
                !SlabSupport.isCeilingSupportBottomSurface(world, slabPos),
                "BOTTOM slab must NOT be reported as ceiling support by"
                + " SlabSupport.isCeilingSupportBottomSurface");

        // Claim 2: chain still survives (bottom slab's DOWN face is a full
        // solid square under vanilla semantics — its y=0 plane IS solid).
        BlockState chainState = world.getBlockState(chainPos);
        ctx.assertTrue(chainState.is(CHAIN_BLOCK),
                "chain under bottom slab not placed");

        BlockState forced = chainState.updateShape(Direction.UP, world.getBlockState(slabPos), world, chainPos, slabPos);
        ctx.assertTrue(!forced.isAir(),
                "Y-axis chain under BOTTOM slab must survive via generic"
                + " isSideSolidFullSquare(DOWN) — slab's y=0 face is a full"
                + " solid square; got AIR (axis walk lost vanilla-geometry"
                + " fallback)");

        ctx.succeed();
    }

    /**
     * Horizontal through-chain walk: X-axis chain A, intermediate X-axis
     * chain A+east, stone at A+2×east. Chain A must survive because the
     * axis walk traverses the intermediate same-axis chain to reach stone
     * support 2 blocks away.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void xAxisThroughChainWalkReachesDistantSupport(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chainA = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos chainB = chainA.relative(Direction.EAST);
        BlockPos supportPos = chainB.relative(Direction.EAST);

        BlockState chainX = CHAIN_BLOCK.defaultBlockState()
                .setValue(ChainBlock.AXIS, Direction.Axis.X);

        world.setBlock(supportPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(chainB, chainX, Block.UPDATE_ALL);
        world.setBlock(chainA, chainX, Block.UPDATE_ALL);

        ctx.assertTrue(world.getBlockState(chainA).is(CHAIN_BLOCK),
                "chainA not placed");
        ctx.assertTrue(world.getBlockState(chainB).is(CHAIN_BLOCK),
                "chainB not placed");

        BlockState stateA = world.getBlockState(chainA);
        BlockState forced = stateA.updateShape(Direction.WEST, world.getBlockState(chainA.relative(Direction.WEST)), world, chainA, chainA.relative(Direction.WEST));
        ctx.assertTrue(!forced.isAir(),
                "X-axis chainA must survive recheck via through-chain walk"
                + " to stone at chainA+2×east; got AIR"
                + " (slabbed$walkChainForSupport failed to traverse same-axis"
                + " chainB)");

        ctx.succeed();
    }

    /**
     * Oxidizable/copper chain mirror of the vanilla TOP-slab positive
     * baseline: copper chain under TOP slab must survive initial recheck.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void copperChainUnderTopSlabSurvivesInitialRecheck(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.above();

        world.setBlock(slabPos,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Block.UPDATE_ALL);
        world.setBlock(chainPos, copperChainY(), Block.UPDATE_ALL);

        BlockState chainState = world.getBlockState(chainPos);
        ctx.assertTrue(isPreferredChain(chainState),
                "preferred chain not placed at " + chainPos.toString()
                + ", found: " + chainState.getBlock().getDescriptionId());

        BlockState result = chainState.updateShape(Direction.UP, world.getBlockState(slabPos), world, chainPos, slabPos);
        ctx.assertTrue(!result.isAir(),
                "copper chain under TOP slab must survive initial recheck; got AIR"
                + " (possible uncovered OxidizableChainBlock path)");

        ctx.succeed();
    }

    /**
     * Oxidizable/copper mirror of vanilla floating-chain policy:
     * X-axis copper chain remains when sole support is removed.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void copperChainRemainsWhenSoleSupportRemoved(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos supportPos = chainPos.relative(Direction.EAST);

        world.setBlock(supportPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(chainPos, copperChainX(), Block.UPDATE_ALL);
        ctx.assertTrue(isPreferredChain(world.getBlockState(chainPos)),
                "X-axis preferred chain not placed");

        world.setBlock(supportPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        BlockState after = world.getBlockState(chainPos);

        BlockState forced = after.updateShape(Direction.EAST, Blocks.AIR.defaultBlockState(), world, chainPos, supportPos);
        ctx.assertTrue(!forced.isAir(),
                "X-axis copper chain must remain after sole support removed under vanilla floating policy; world="
                + after.getBlock().getDescriptionId()
                + ", forced=" + forced.getBlock().getDescriptionId());

        ctx.succeed();
    }

    /**
     * Oxidizable/copper chain mirror of unrelated-neighbor stability:
     * with east support intact, removing unrelated north neighbor must not pop.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void copperChainSurvivesUnrelatedNeighborRemoval(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos supportPos = chainPos.relative(Direction.EAST);
        BlockPos unrelatedPos = chainPos.relative(Direction.NORTH);

        world.setBlock(supportPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(unrelatedPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(chainPos, copperChainX(), Block.UPDATE_ALL);
        ctx.assertTrue(isPreferredChain(world.getBlockState(chainPos)),
                "X-axis preferred chain not placed");

        world.setBlock(unrelatedPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        BlockState after = world.getBlockState(chainPos);
        ctx.assertTrue(isPreferredChain(after),
                "X-axis preferred chain must survive unrelated NORTH update with"
                + " east support intact; found " + after.getBlock().getDescriptionId());

        BlockState forced = after.updateShape(Direction.NORTH, Blocks.AIR.defaultBlockState(), world, chainPos, unrelatedPos);
        ctx.assertTrue(!forced.isAir(),
                "X-axis copper chain forced recheck(direction=NORTH) must not drop"
                + " when east support remains");

        ctx.succeed();
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
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void chainSurvivesMultiNeighborBurstSameTick(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.above();
        BlockPos north = chainPos.north();
        BlockPos south = chainPos.south();
        BlockPos east = chainPos.east();
        BlockPos west = chainPos.west();

        world.setBlock(slabPos,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Block.UPDATE_ALL);
        world.setBlock(chainPos,
                CHAIN_BLOCK.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y),
                Block.UPDATE_ALL);
        ctx.assertTrue(world.getBlockState(chainPos).is(CHAIN_BLOCK),
                "chain not placed at " + chainPos.toString());

        // Same-tick neighbor burst: place/remove around chain while support
        // above remains unchanged.
        world.setBlock(north, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(east, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(south, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(west, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(north, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(east, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(south, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(west, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        BlockState finalState = world.getBlockState(chainPos);
        ctx.assertTrue(finalState.is(CHAIN_BLOCK),
                "chain popped after same-tick multi-neighbor burst with TOP slab"
                + " support intact; final=" + finalState.getBlock().getDescriptionId());

        BlockState forced = finalState.updateShape(Direction.NORTH, world.getBlockState(north), world, chainPos, north);
        ctx.assertTrue(!forced.isAir(),
                "forced recheck after same-tick neighbor burst returned AIR"
                + " despite TOP slab support remaining");

        ctx.succeed();
    }

    /**
     * Runtime-bucket audit: gameplay-like placement order.
     *
     * <p>Models a closer live sequence than direct survival calls:
     * place temporary side scaffolding, place top slab support, place chain,
     * remove temporary scaffolding, then pulse another nearby update.
     * Chain should remain because axis support (top slab) is still valid.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void chainPlacementOrderLikeGameplayStaysStable(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.above();
        BlockPos scaffoldPos = chainPos.west();
        BlockPos pulsePos = chainPos.east();

        // Step 1: temporary scaffold near placement area.
        world.setBlock(scaffoldPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        // Step 2: actual support for the chain.
        world.setBlock(slabPos,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Block.UPDATE_ALL);

        // Step 3: place chain in supported position.
        world.setBlock(chainPos,
                CHAIN_BLOCK.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y),
                Block.UPDATE_ALL);
        ctx.assertTrue(world.getBlockState(chainPos).is(CHAIN_BLOCK),
                "chain not placed in gameplay-order scenario");

        // Step 4: remove temporary scaffold (common live action).
        world.setBlock(scaffoldPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        // Step 5: trigger an additional nearby update in same slice.
        world.setBlock(pulsePos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(pulsePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        BlockState finalState = world.getBlockState(chainPos);
        ctx.assertTrue(finalState.is(CHAIN_BLOCK),
                "chain popped during gameplay-like placement/removal ordering; final="
                + finalState.getBlock().getDescriptionId());

        BlockState forced = finalState.updateShape(Direction.WEST, world.getBlockState(scaffoldPos), world, chainPos, scaffoldPos);
        ctx.assertTrue(!forced.isAir(),
                "forced recheck after gameplay-like ordering returned AIR"
                + " with TOP slab support still above");

        ctx.succeed();
    }

    /**
     * Cross-system (piston) nearby pulse:
     * a sticky piston extends/retracts a nearby side block, but the chain's
     * actual top-slab support remains intact throughout. Chain must survive.
     *
     * <p>Modeling boundary: this harness validates externally observable state
     * transitions only (final block states + forced neighbor recheck). It
     * does not introspect the internal vanilla BlockEvent queue directly.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void chainUnderTopSlabSurvivesNearbyPistonExtendRetract(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();

        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.above();

        BlockPos pistonPos = chainPos.west(3);
        BlockPos movablePos = chainPos.west(2);
        BlockPos powerPos = pistonPos.west();

        world.setBlock(slabPos,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Block.UPDATE_ALL);
        world.setBlock(chainPos,
                CHAIN_BLOCK.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y),
                Block.UPDATE_ALL);

        world.setBlock(pistonPos,
                Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST),
                Block.UPDATE_ALL);
        world.setBlock(movablePos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        ctx.assertTrue(world.getBlockState(chainPos).is(CHAIN_BLOCK),
                "chain not placed before nearby piston pulse");

        // Tick 1: extend piston.
        ctx.runAtTickTime(1, () -> world.setBlock(powerPos, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL));

        // Tick 3: retract piston.
        ctx.runAtTickTime(3, () -> world.setBlock(powerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));

        // Tick 8: chain should still be present; support above never changed.
        ctx.runAtTickTime(8, () -> {
            BlockState finalState = world.getBlockState(chainPos);
            ctx.assertTrue(finalState.is(CHAIN_BLOCK),
                    "chain popped after nearby piston extend/retract with TOP slab support intact; final="
                    + finalState.getBlock().getDescriptionId());

            BlockState forced = finalState.updateShape(Direction.WEST, world.getBlockState(chainPos.west()), world, chainPos, chainPos.west());
            ctx.assertTrue(!forced.isAir(),
                    "forced recheck after nearby piston pulse returned AIR despite TOP slab support");

            ctx.succeed();
        });
    }

    /**
     * Cross-system (piston) support removal under vanilla floating policy:
     * piston moves the TOP slab support away; chain still remains.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void chainRemainsWhenPistonMovesTopSlabSupportAway(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();

        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.above();
        BlockPos pistonPos = slabPos.west();
        BlockPos powerPos = pistonPos.west();

        world.setBlock(chainPos,
                CHAIN_BLOCK.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y),
                Block.UPDATE_ALL);
        world.setBlock(slabPos,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Block.UPDATE_ALL);
        world.setBlock(pistonPos,
                Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST),
                Block.UPDATE_ALL);

        ctx.assertTrue(world.getBlockState(chainPos).is(CHAIN_BLOCK),
                "chain not placed before piston-driven support move");

        // Tick 1: power piston; slab above chain is pushed east off support spot.
        ctx.runAtTickTime(1, () -> world.setBlock(powerPos, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL));

        // Tick 6: chain should remain under vanilla floating policy.
        ctx.runAtTickTime(6, () -> {
            BlockState after = world.getBlockState(chainPos);
            ctx.assertTrue(after.is(CHAIN_BLOCK),
                    "chain should remain after piston moved TOP slab support away under vanilla floating policy; found "
                    + after.getBlock().getDescriptionId());

            BlockState forced = after.updateShape(Direction.UP, world.getBlockState(slabPos), world, chainPos, slabPos);
            ctx.assertTrue(!forced.isAir(),
                    "chain should remain after piston moved TOP slab support away under vanilla floating policy; world="
                    + after.getBlock().getDescriptionId()
                    + ", forced=" + forced.getBlock().getDescriptionId());

            ctx.succeed();
        });
    }

    /**
     * Cross-system (observer/redstone-style pulse ordering):
     * nearby observer receives two deterministic block changes across ticks.
     * Chain must survive this pulse ordering while TOP slab support stays intact.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void chainUnderTopSlabSurvivesNearbyObserverPulseOrdering(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();

        BlockPos chainPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos slabPos = chainPos.above();

        BlockPos observerPos = chainPos.west();
        BlockPos observedPos = observerPos.west();

        world.setBlock(slabPos,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Block.UPDATE_ALL);
        world.setBlock(chainPos,
                CHAIN_BLOCK.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y),
                Block.UPDATE_ALL);

        world.setBlock(observerPos,
                Blocks.OBSERVER.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.WEST),
                Block.UPDATE_ALL);

        ctx.assertTrue(world.getBlockState(chainPos).is(CHAIN_BLOCK),
                "chain not placed before observer pulse ordering");

        // Deterministic nearby pulse ordering: two observed block mutations.
        ctx.runAtTickTime(1, () -> world.setBlock(observedPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL));
        ctx.runAtTickTime(3, () -> world.setBlock(observedPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));

        ctx.runAtTickTime(8, () -> {
            BlockState finalState = world.getBlockState(chainPos);
            ctx.assertTrue(finalState.is(CHAIN_BLOCK),
                    "chain popped during nearby observer pulse ordering with TOP slab support intact; final="
                    + finalState.getBlock().getDescriptionId());

            BlockState forced = finalState.updateShape(Direction.WEST, world.getBlockState(observerPos), world, chainPos, observerPos);
            ctx.assertTrue(!forced.isAir(),
                    "forced recheck after observer pulse ordering returned AIR despite TOP slab support");

            ctx.succeed();
        });
    }
}
