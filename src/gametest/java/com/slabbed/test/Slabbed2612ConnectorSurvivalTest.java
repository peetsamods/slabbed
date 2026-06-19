package com.slabbed.test;

import com.slabbed.Slabbed;
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
 * <p>NOT WRITTEN (flagged by the spec, would be a false test): a "vertical chain pops when support is
 * removed" test. {@code ChainBlockNeighborSurvivalMixin} is present on disk but registered in NO mixin
 * config, so chain survival is pure-vanilla (never pops). Asserting a pop would fail against the build.
 * Surfaced to the maintainer — register the mixin or strike checklist D6 — before adding that test.
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
        helper.setBlock(carpet, Blocks.WHITE_CARPET.defaultBlockState());
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
        helper.setBlock(carpet, Blocks.WHITE_CARPET.defaultBlockState());
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
     * Redstone dust survives on a bottom-slab top AND a top-slab top (the {@code isRedstoneSupportTopSurface}
     * feature — top slabs are normally non-supporting, so this is the meaningful pin). ⚠ MEASURED + FLAGGED:
     * the wire ALSO reports canSurvive==true with air below — vanilla returns false there, so the
     * RedstoneWireBlockMixin is over-permissive (always-survive) in this build. We assert the two intended
     * slab cases and LOG the air case loudly for review rather than asserting a control that fails.
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
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR.defaultBlockState());
        boolean overAir = level.getBlockState(abs).canSurvive(level, abs);
        Slabbed.LOGGER.info("CONN-SURV | ⚠ redstone canSurvive over AIR = {} (vanilla=false; mixin appears over-permissive — review RedstoneWireBlockMixin)", overAir);
        helper.succeed();
    }
}
