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
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 * Julia's commit {@code df3a0dd4} ("restore vanilla floating-chain behavior") un-registered the old
 * {@code ChainBlockNeighborSurvivalMixin} pop-off mixin and flipped its repro tests to "chain stays";
 * that dead mixin (and the orphaned Yarn-mapped {@code ChainSurvivalReproTest}) were deleted 2026-06-18.
 * {@link #chainDoesNotPopWhenSupportRemoved} pins that policy here so a re-registered survival mixin
 * would turn this suite red. Checklist D6 reflects the same. (Chain <em>dy</em> — flush/raise — is a
 * separate concern, covered by {@code Slabbed2612LoweringContractTest}.)
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
}
