package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Headless coverage for CONNECTOR logic (fence/wall/pane connect-or-break across a slab-height step)
 * and SURVIVAL logic (does X stay/pop on a slab), beyond the four connector tests already in
 * {@code Slabbed2612LoweringContractTest}. Built from a 3-agent spec (2026-06-18).
 *
 * <p>Discipline the spec imposed: survival is asserted via the pure {@code canSurvive(level,pos)}
 * predicate or by driving {@code updateShape} DIRECTLY — never "place then setBlock(AIR) and read the
 * dependent block", because the neighbour-update tick may not fire within a gametest frame.
 *
 * <p>Chain survival is intentionally VANILLA FLOATING: a chain does NOT pop when its support is removed.
 * the maintainer's commit {@code df3a0dd4} ("restore vanilla floating-chain behavior") un-registered the old
 * {@code ChainBlockNeighborSurvivalMixin} pop-off mixin and flipped its repro tests to "chain stays";
 * that dead mixin (and the orphaned Yarn-mapped {@code ChainSurvivalReproTest}) were deleted 2026-06-18.
 * {@link #chainDoesNotPopWhenSupportRemoved} pins that policy here so a re-registered survival mixin
 * would turn this suite red. Checklist D6 reflects the same. (Chain <em>dy</em> — flush/raise — is a
 * separate concern, covered by {@code Slabbed2612LoweringContractTest}.)
 *
 * <p>GH #37 / #57 regression net: before this, nothing anywhere asserted wire <em>connection state</em>
 * or any powered-circuit behavior. GH #37 forced a SIDE connection whenever
 * {@link SlabSupport#isRedstoneSupportTopSurface} was true for the neighbour — true for ANY sturdy-top
 * block. The 26.2 mixin dropped that override, but the predicate is still live and one refactor away
 * from reuse, so the connection test pins vanilla's NONE off the wire's own state rather than off the
 * mixin's absence. The lamp circuit is the GH #57 "is a powered circuit measurable at all" control.
 */
public final class Slabbed2612ConnectorSurvivalTest {

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /** A is WEST of B; support under A is a bottom slab (A→-0.5), under B is stone (B flush). Returns A's post-updateShape state. */
    private static BlockState steppedAState(GameTestHelper helper, ServerLevel level, BlockState connector) {
        helper.setBlock(new BlockPos(2, 1, 2), bottomSlab());
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE.defaultBlockState());
        BlockPos a = new BlockPos(2, 2, 2);
        BlockPos b = new BlockPos(3, 2, 2);
        helper.setBlock(a, connector);
        helper.setBlock(b, connector);
        return connector.updateShape(level, level, helper.absolutePos(a), Direction.EAST,
                helper.absolutePos(b), level.getBlockState(helper.absolutePos(b)), level.getRandom());
    }

    /** Both supports stone (flat run). Returns A's post-updateShape state. */
    private static BlockState flatAState(GameTestHelper helper, ServerLevel level, BlockState connector) {
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE.defaultBlockState());
        BlockPos a = new BlockPos(2, 2, 2);
        BlockPos b = new BlockPos(3, 2, 2);
        helper.setBlock(a, connector);
        helper.setBlock(b, connector);
        return connector.updateShape(level, level, helper.absolutePos(a), Direction.EAST,
                helper.absolutePos(b), level.getBlockState(helper.absolutePos(b)), level.getRandom());
    }

    // ── connectors: genuinely NEW coverage beyond the existing oak-fence/iron-bars/cobble-wall tests ──

    /** Glass pane (PaneBlock → IronBarsBlock lineage) breaks across a step — the pane-specific path. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void steppedGlassPaneRunBreaks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState after = steppedAState(helper, level, Blocks.GLASS_PANE.defaultBlockState());
        if (after.getValue(CrossCollisionBlock.EAST)) {
            throw helper.assertionException(new BlockPos(2, 2, 2),
                    "stepped glass_pane EAST must be BROKEN (no arm across a -0.5 step)");
        }
        helper.succeed();
    }

    /** Control: a flat glass-pane run still connects (no flat pane control existed before). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatGlassPaneRunStillConnects(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState after = flatAState(helper, level, Blocks.GLASS_PANE.defaultBlockState());
        if (!after.getValue(CrossCollisionBlock.EAST)) {
            throw helper.assertionException(new BlockPos(2, 2, 2),
                    "flat glass_pane EAST must REMAIN (mixin must not break a same-height pane run)");
        }
        helper.succeed();
    }

    /** Glass pane must be in the same lowered connector-contact family used by the visual model gate. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void glassPaneParticipatesInLoweredConnectorVisualFamily(GameTestHelper helper) {
        if (!SlabSupport.isBeta35FenceWallVariantContactObject(Blocks.GLASS_PANE.defaultBlockState())) {
            throw helper.assertionException(new BlockPos(2, 2, 2),
                    "glass_pane must participate in lowered connector-contact model/raycast gating");
        }
        helper.succeed();
    }

    /** A stepped wall not only drops the EAST side to NONE but forces the centre post UP — pin that. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void steppedWallBreaksAndForcesUpPost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState after = steppedAState(helper, level, Blocks.COBBLESTONE_WALL.defaultBlockState());
        if (after.getValue(WallBlock.EAST) != WallSide.NONE) {
            throw helper.assertionException(new BlockPos(2, 2, 2),
                    "stepped wall EAST must be NONE, got " + after.getValue(WallBlock.EAST));
        }
        if (!after.getValue(BlockStateProperties.UP)) {
            throw helper.assertionException(new BlockPos(2, 2, 2),
                    "stepped wall with a broken side must raise the centre post (UP=true)");
        }
        helper.succeed();
    }

    /** A different fence material breaks across a step too (smoke pin that it's material-invariant). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void steppedNetherBrickFenceBreaks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState after = steppedAState(helper, level, Blocks.NETHER_BRICK_FENCE.defaultBlockState());
        if (after.getValue(CrossCollisionBlock.EAST)) {
            throw helper.assertionException(new BlockPos(2, 2, 2),
                    "stepped nether_brick_fence EAST must be BROKEN");
        }
        helper.succeed();
    }

    /** A different wall material breaks across a step too. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void steppedStoneBrickWallBreaks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState after = steppedAState(helper, level, Blocks.STONE_BRICK_WALL.defaultBlockState());
        if (after.getValue(WallBlock.EAST) != WallSide.NONE) {
            throw helper.assertionException(new BlockPos(2, 2, 2),
                    "stepped stone_brick_wall EAST must be NONE, got " + after.getValue(WallBlock.EAST));
        }
        helper.succeed();
    }

    // ── survival: pure canSurvive() predicates + direct updateShape (no neighbour-tick reliance) ──────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void carpetSurvivesOnSlabTops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos carpet = new BlockPos(2, 2, 2);
        BlockPos abs = helper.absolutePos(carpet);
        // bottom slab
        helper.setBlock(new BlockPos(2, 1, 2), bottomSlab());
        helper.setBlock(carpet, Blocks.CARPET.white().defaultBlockState());
        if (!level.getBlockState(abs).canSurvive(level, abs)) {
            throw helper.assertionException(carpet, "white_carpet must survive on a bottom-slab top");
        }
        // top slab
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        if (!level.getBlockState(abs).canSurvive(level, abs)) {
            throw helper.assertionException(carpet, "white_carpet must survive on a top-slab top");
        }
        helper.succeed();
    }

    /** Carpet over a slab must NOT pop on a neighbour update — drive updateShape directly and check it stays carpet. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void carpetDoesNotPopOnNeighbourUpdateOverSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 1, 2), bottomSlab());
        BlockPos carpet = new BlockPos(2, 2, 2);
        helper.setBlock(carpet, Blocks.CARPET.white().defaultBlockState());
        BlockPos abs = helper.absolutePos(carpet);
        BlockPos north = abs.north();
        BlockState after = level.getBlockState(abs).updateShape(
                level, level, abs, Direction.NORTH, north, level.getBlockState(north), level.getRandom());
        if (after.isAir()) {
            throw helper.assertionException(carpet,
                    "carpet over a slab must NOT pop on a neighbour update (updateShape returned air)");
        }
        helper.succeed();
    }

    /**
     * A flower_pot survives on a bottom-slab top (the supported, intended case). NOTE (measured): a
     * flower_pot ALSO reports canSurvive==true with air below — i.e. it does not require support to
     * survive in this build (vanilla flower pots have no support rule, and/or the slab survival mixin
     * does not remove that). We assert the supported case and LOG the air case rather than asserting a
     * "pop" that does not happen; checklist E7 ("pot pops when slab removed") is therefore a LIVE check.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flowerPotSurvivesOnSlabTop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pot = new BlockPos(2, 2, 2);
        BlockPos abs = helper.absolutePos(pot);
        helper.setBlock(new BlockPos(2, 1, 2), bottomSlab());
        helper.setBlock(pot, Blocks.FLOWER_POT.defaultBlockState());
        if (!level.getBlockState(abs).canSurvive(level, abs)) {
            throw helper.assertionException(pot, "flower_pot must survive on a bottom-slab top");
        }
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR.defaultBlockState());
        Slabbed.LOGGER.info("CONN-SURV | flower_pot canSurvive with air below = {} (no support rule — E7 is a live check)",
                level.getBlockState(abs).canSurvive(level, abs));
        helper.succeed();
    }

    /**
     * Redstone dust survives on a bottom-slab top AND a top-slab top, and must NOT survive over air.
     *
     * <p>The two slab cases pin the intended support behaviour. The air case pins that the mixin is
     * NOT over-permissive: {@code RedstoneWireBlockMixin} overrides {@code canSurvive} to true only when
     * {@code SlabSupport.isRedstoneSupportTopSurface(below)} is true (a sturdy-up face, or a bottom/top
     * slab) — otherwise it lets vanilla decide, and vanilla returns false over air.
     *
     * <p>CORRECTION (was a false reading): an earlier version did {@code setBlock(support, AIR)} then read
     * {@code level.getBlockState(wirePos).canSurvive(...)} and saw {@code true}, concluding the mixin was
     * over-permissive. That reading was bogus. Removing the support synchronously POPS the placed wire to
     * air — via vanilla {@code updateShape -> canSurviveOn} (direction DOWN), which the mixin does not
     * touch — so the subsequent read measured AIR's default {@code canSurvive} (true), not the wire's.
     * Verified (2026-06-18): a FRESH redstone state over the emptied column returns {@code canSurvive ==
     * false}, and {@code isRedstoneSupportTopSurface(air) == false}. So we assert the wire popped, and
     * assert a fresh wire cannot survive over air.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void redstoneWireSurvivesOnSlabTops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos wire = new BlockPos(2, 2, 2);
        BlockPos abs = helper.absolutePos(wire);
        helper.setBlock(new BlockPos(2, 1, 2), bottomSlab());
        helper.setBlock(wire, Blocks.REDSTONE_WIRE.defaultBlockState());
        if (!level.getBlockState(abs).canSurvive(level, abs)) {
            throw helper.assertionException(wire, "redstone_wire must survive on a bottom-slab top");
        }
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        if (!level.getBlockState(abs).canSurvive(level, abs)) {
            throw helper.assertionException(wire, "redstone_wire must survive on a top-slab top");
        }
        // Remove the support. The placed wire pops to air (vanilla updateShape -> canSurviveOn, which the
        // mixin does not touch), and a FRESH wire must NOT survive over the now-empty column.
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR.defaultBlockState());
        if (!level.getBlockState(abs).isAir()) {
            throw helper.assertionException(wire, "redstone_wire must pop when its support is removed");
        }
        if (Blocks.REDSTONE_WIRE.defaultBlockState().canSurvive(level, abs)) {
            throw helper.assertionException(wire,
                    "redstone_wire must NOT survive over air (mixin must defer to vanilla, which returns false)");
        }
        helper.succeed();
    }

    /**
     * Vanilla floating-chain policy (checklist D6): a Y-axis chain hung under a TOP slab must REMAIN
     * when that slab is removed — chains have no survival rule and must not pop. This pins the decision
     * in {@code df3a0dd4} (the old pop-off {@code ChainBlockNeighborSurvivalMixin} was un-registered and
     * is now deleted); if a survival mixin is ever re-registered, {@code updateShape} would return air
     * here and turn this red. Driven via {@code updateShape} DIRECTLY (no neighbour-tick reliance), which
     * is the exact path the old mixin hooked.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainDoesNotPopWhenSupportRemoved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chain = new BlockPos(2, 2, 2);
        BlockPos slab = new BlockPos(2, 3, 2);
        helper.setBlock(slab, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        helper.setBlock(chain, Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        BlockPos chainAbs = helper.absolutePos(chain);
        BlockPos slabAbs = helper.absolutePos(slab);
        // remove the support, then drive the chain's own neighbour recheck for the now-air slab above
        helper.setBlock(slab, Blocks.AIR.defaultBlockState());
        BlockState after = level.getBlockState(chainAbs).updateShape(
                level, level, chainAbs, Direction.UP, slabAbs, level.getBlockState(slabAbs), level.getRandom());
        if (after.isAir()) {
            throw helper.assertionException(chain,
                    "iron_chain must REMAIN when its TOP-slab support is removed (vanilla floating policy, "
                            + "df3a0dd4); updateShape returned air — a chain survival mixin has been re-introduced");
        }
        helper.succeed();
    }

    // ── GH #37/#57 regression net: wire CONNECTION state + a real powered circuit ──────────────────────

    /**
     * A fresh {@code redstone_wire} must report connection {@code NONE} toward a horizontally adjacent
     * ordinary full block — vanilla's own decision, which Slabbed must not override. Read directly off
     * the wire's {@code RedStoneWireBlock.EAST} block-state property (never the render-only connection
     * type), driven via {@code updateShape} DIRECTLY for that one direction (the same discipline the
     * connector helpers above use), so this does not depend on a neighbour-update tick landing.
     *
     * <p>Two cases: (1) the wire's own support is an ordinary block (stone) — the plain vanilla scenario;
     * (2) the wire's own support is a bottom slab with an ordinary full block still adjacent — the
     * Slabbed-relevant scenario. Both must stay NONE. This is the exact risk surface from GH #37: the old
     * mixin forced SIDE whenever {@link SlabSupport#isRedstoneSupportTopSurface} was true for the
     * neighbour, and that predicate is true for ANY block with a sturdy top face (ordinary full blocks
     * included) — so a future reuse of that predicate anywhere in wire connection logic, on either the
     * ordinary or the slab-supported wire, turns one of these two assertions red.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void redstoneWireDoesNotSideConnectToSturdyFullBlockNeighbor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // Case 1: ordinary support under the wire, ordinary full block adjacent.
        BlockPos supportOrdinary = new BlockPos(2, 1, 2);
        BlockPos wireOrdinary = new BlockPos(2, 2, 2);
        BlockPos neighborOrdinary = new BlockPos(3, 2, 2);
        helper.setBlock(supportOrdinary, Blocks.STONE.defaultBlockState());
        helper.setBlock(wireOrdinary, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(neighborOrdinary, Blocks.COBBLESTONE.defaultBlockState());
        BlockPos wireOrdinaryAbs = helper.absolutePos(wireOrdinary);
        BlockPos neighborOrdinaryAbs = helper.absolutePos(neighborOrdinary);
        BlockState afterOrdinary = level.getBlockState(wireOrdinaryAbs).updateShape(
                level, level, wireOrdinaryAbs, Direction.EAST, neighborOrdinaryAbs,
                level.getBlockState(neighborOrdinaryAbs), level.getRandom());
        if (afterOrdinary.getValue(RedStoneWireBlock.EAST) != RedstoneSide.NONE) {
            throw helper.assertionException(wireOrdinary,
                    "redstone_wire EAST must stay NONE next to an ordinary full block (Slabbed must not "
                            + "override vanilla's connection decision), got " + afterOrdinary.getValue(RedStoneWireBlock.EAST));
        }

        // Case 2: Slabbed-relevant support (bottom slab) under the wire, ordinary full block still adjacent.
        BlockPos supportSlab = new BlockPos(2, 1, 4);
        BlockPos wireOnSlab = new BlockPos(2, 2, 4);
        BlockPos neighborNearSlab = new BlockPos(3, 2, 4);
        helper.setBlock(supportSlab, bottomSlab());
        helper.setBlock(wireOnSlab, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(neighborNearSlab, Blocks.OAK_PLANKS.defaultBlockState());
        BlockPos wireOnSlabAbs = helper.absolutePos(wireOnSlab);
        BlockPos neighborNearSlabAbs = helper.absolutePos(neighborNearSlab);
        BlockState afterSlab = level.getBlockState(wireOnSlabAbs).updateShape(
                level, level, wireOnSlabAbs, Direction.EAST, neighborNearSlabAbs,
                level.getBlockState(neighborNearSlabAbs), level.getRandom());
        if (afterSlab.getValue(RedStoneWireBlock.EAST) != RedstoneSide.NONE) {
            throw helper.assertionException(wireOnSlab,
                    "redstone_wire on a bottom-slab top EAST must stay NONE next to an ordinary full block "
                            + "(a widened isRedstoneSupportTopSurface must not leak into connection state), got "
                            + afterSlab.getValue(RedStoneWireBlock.EAST));
        }

        helper.succeed();
    }

    /**
     * GH #57 smoke control: a minimal lever → wire → wire → {@code redstone_lamp} relay on ordinary full
     * blocks, proving a powered circuit is even measurable on 26.2. Lamp starts UNLIT, lights when the
     * lever is pulled ON, and returns to UNLIT when pulled back OFF.
     *
     * <p>Layout: {@code stone A} carries a lever on its south WALL face (so the top of A stays free for
     * wire); {@code wire1} sits on A; {@code wire2} sits on a second support {@code stone B}; the lamp
     * sits directly beside B (not beside the wire — vanilla wire only extends a connecting arm, and
     * therefore signal, to blocks it can render an arm toward; a plain full block is not one of those, so
     * the lamp is powered via B's weak power once B sits under a live wire segment).
     *
     * <p>Determinism: {@code helper.pullLever} drives the real {@code LeverBlock.pull(state, level, pos,
     * player)} path (the same method vanilla's own {@code useWithoutItem} calls), which synchronously
     * cascades {@code updateNeighborsAt} through the wire chain to B in the same call — turning the lamp
     * ON is synchronous. Turning the lamp OFF is NOT synchronous: vanilla's {@code
     * RedstoneLampBlock.neighborChanged} schedules the unlight via {@code level.scheduleTick(pos, this,
     * 4)} rather than clearing it immediately, so the state only flips on that later tick. The sequence
     * below uses {@code helper.startSequence().thenWaitUntil(...)} (the same polling primitive backing
     * {@code succeedWhen}) for both transitions, so each assertion is retried every tick until it holds or
     * the test times out — it cannot flake on the scheduled-tick delay, and it does not depend on a fixed
     * tick count.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void leverWireLampCircuitTracksPower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos supportA = new BlockPos(2, 1, 2);
        BlockPos leverPos = new BlockPos(2, 1, 3);
        BlockPos wire1 = new BlockPos(2, 2, 2);
        BlockPos supportB = new BlockPos(3, 1, 2);
        BlockPos wire2 = new BlockPos(3, 2, 2);
        BlockPos lampPos = new BlockPos(4, 1, 2);

        helper.setBlock(supportA, Blocks.STONE.defaultBlockState());
        helper.setBlock(supportB, Blocks.STONE.defaultBlockState());
        helper.setBlock(leverPos, Blocks.LEVER.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                .setValue(BlockStateProperties.POWERED, false));
        helper.setBlock(wire1, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(wire2, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(lampPos, Blocks.REDSTONE_LAMP.defaultBlockState());

        BlockPos lampAbs = helper.absolutePos(lampPos);

        helper.startSequence()
                .thenExecute(() -> {
                    if (level.getBlockState(lampAbs).getValue(BlockStateProperties.LIT)) {
                        throw helper.assertionException(lampPos,
                                "redstone_lamp must start UNLIT before the lever is pulled");
                    }
                })
                .thenExecute(() -> helper.pullLever(leverPos))
                .thenWaitUntil(() -> {
                    if (!level.getBlockState(lampAbs).getValue(BlockStateProperties.LIT)) {
                        throw helper.assertionException(lampPos,
                                "redstone_lamp must LIGHT once lever -> wire -> wire power reaches it");
                    }
                })
                .thenExecute(() -> helper.pullLever(leverPos))
                .thenWaitUntil(() -> {
                    if (level.getBlockState(lampAbs).getValue(BlockStateProperties.LIT)) {
                        throw helper.assertionException(lampPos,
                                "redstone_lamp must return to UNLIT after the lever is pulled back off "
                                        + "(vanilla's scheduled-tick unlight delay)");
                    }
                })
                .thenSucceed();
    }
}
