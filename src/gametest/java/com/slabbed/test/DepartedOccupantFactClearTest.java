package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * An emptied cell keeps no stored height, whichever block left it and whichever update flags emptied
 * it (maintainer ruling, 2026-09-06).
 *
 * <p>This class proves the CLEAR is independent of the departing block class and of the caller's
 * update flags; {@code StateChangeAnchorTest} proves the opposite allowance, that an in-place
 * transform KEEPS. The two holes it pins are both real on this line: 28 vanilla block classes
 * override the per-block removal hook without calling the base version, and vanilla reaches that
 * hook at all only for some update-flag values, so a hook-keyed clear is blind twice over.
 *
 * <p><b>VENUE.</b> This JVM runs with the frozen store OFF, so {@code SlabSupport.getYOffset} does
 * not consult the store here. Every row therefore asserts on
 * {@code SlabAnchorAttachment.rawPlacementDyFact} — presence, and raw bits where survival is the
 * claim — and no row may be rewritten to assert on a resolved height without opening the store, or
 * it would measure the geometric lanes instead of this feature.
 *
 * <p><b>FIXTURE ORDER IS LOAD-BEARING.</b> Write the block FIRST and the stored height SECOND. The
 * seam judges the block write, so a fixture that authored the height first would have it cleared by
 * its own {@code setBlock}. The same rule governs every fact-writing fixture in the suite: no cell
 * whose stored height is later read may receive a block-KIND write after that height is authored.
 */
public final class DepartedOccupantFactClearTest {

    private static final double LOWERED = -0.5d;

    /** {@code /setblock <pos> air strict} — the flag value that reaches no removal hook at all. */
    private static final int STRICT_SETBLOCK_FLAGS =
            Block.UPDATE_SKIP_ALL_SIDEEFFECTS | Block.UPDATE_CLIENTS;

    // Piston block-event types, as vanilla numbers them.
    private static final int EVENT_EXTEND = 0;
    private static final int EVENT_CONTRACT = 1;

    // ── fixture ──────────────────────────────────────────────────────────────────────────────

    private static BlockState bottomSlab() {
        return Blocks.POLISHED_TUFF_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private record Cell(BlockPos support, BlockPos cell) {
    }

    /**
     * A lowered occupant on a bottom slab, carrying a stored height. Block first, height second.
     */
    private static Cell build(GameTestHelper helper, ServerLevel w, BlockState occupant) {
        BlockPos support = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos cell = support.above();
        w.setBlock(support, bottomSlab(), Block.UPDATE_CLIENTS);
        w.setBlock(cell, occupant, Block.UPDATE_ALL);
        SlabAnchorAttachment.writePlacementDy(w, cell, LOWERED);
        requireFact(helper, w, cell, "precondition: the cell carries a stored height");
        return new Cell(support, cell);
    }

    // ── fact reads ───────────────────────────────────────────────────────────────────────────

    private static void requireFact(GameTestHelper helper, ServerLevel w, BlockPos pos, String what) {
        if (!SlabAnchorAttachment.rawPlacementDyFact(w, pos).present()) {
            throw helper.assertionException(pos, what);
        }
    }

    private static void requireNoFact(GameTestHelper helper, ServerLevel w, BlockPos pos, String what) {
        SlabAnchorAttachment.PlacementDyFact fact = SlabAnchorAttachment.rawPlacementDyFact(w, pos);
        if (fact.present()) {
            throw helper.assertionException(pos,
                    what + ": an emptied cell must keep no stored height, found " + fact.valueOrNaN());
        }
    }

    private static void requireBits(GameTestHelper helper, ServerLevel w, BlockPos pos,
                                    double expected, String what) {
        SlabAnchorAttachment.PlacementDyFact fact = SlabAnchorAttachment.rawPlacementDyFact(w, pos);
        if (!fact.present()) {
            throw helper.assertionException(pos, what + ": the stored height is gone entirely");
        }
        if (fact.rawBits() != Double.doubleToRawLongBits(expected)) {
            throw helper.assertionException(pos, what + ": the stored height must survive bit-identical,"
                    + " expected " + expected + " got " + fact.valueOrNaN());
        }
    }

    private static void requireAir(GameTestHelper helper, ServerLevel w, BlockPos pos, String what) {
        if (!w.getBlockState(pos).isAir()) {
            throw helper.assertionException(pos,
                    what + ": expected an empty cell, found " + w.getBlockState(pos));
        }
    }

    // ── piston drivers (same shape as PistonPlacementDyTransferTest) ─────────────────────────

    private static void power(ServerLevel w, BlockPos pistonPos) {
        w.setBlock(pistonPos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void unpower(ServerLevel w, BlockPos pistonPos) {
        w.setBlock(pistonPos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void fire(ServerLevel w, BlockPos pistonPos, Direction facing, int type) {
        w.getBlockState(pistonPos).triggerEvent(w, pistonPos, type, facing.get3DDataValue());
    }

    /** The ordinary completion: drives the animation entity's own tick until it lands. */
    private static void landTicked(ServerLevel w, BlockPos... cells) {
        for (BlockPos cell : cells) {
            for (int step = 0; step < 8; step++) {
                net.minecraft.world.level.block.entity.BlockEntity be = w.getBlockEntity(cell);
                if (!(be instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity moving)) {
                    break;
                }
                net.minecraft.world.level.block.piston.PistonMovingBlockEntity.tick(
                        w, cell, w.getBlockState(cell), moving);
            }
        }
    }

    /** The interrupt completion: vanilla's own landing for a stand-in cell that is replaced. */
    private static void landNow(ServerLevel w, BlockPos... cells) {
        for (BlockPos cell : cells) {
            net.minecraft.world.level.block.entity.BlockEntity be = w.getBlockEntity(cell);
            if (be instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity moving) {
                moving.finalTick();
            }
        }
    }

    // ── rows: the clear ──────────────────────────────────────────────────────────────────────

    /**
     * A broken chest leaves nothing behind. RED before the change: {@code ChestBlock} overrides the
     * per-block removal hook and calls only {@code Containers.updateNeighboursAfterDestroy}, never
     * the base version, so nothing cleared the emptied cell.
     *
     * <p>MUTATION that reddens this row alone: add {@code if (oldState.hasBlockEntity()) return;} to
     * {@code SlabAnchorAttachment.clearFactForDepartedOccupant} — a plausible "do not touch block
     * entities" guard. The chest is the only departing occupant in this class that carries a block
     * entity, and every keep row expects no clear.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void brokenChestLeavesNoStoredHeight(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        Cell rig = build(helper, w, Blocks.CHEST.defaultBlockState());

        // The player-break route: Level.destroyBlock -> setBlock(pos, fluidLegacy, 3).
        w.destroyBlock(rig.cell(), false);

        requireAir(helper, w, rig.cell(), "the broken chest's cell");
        requireNoFact(helper, w, rig.cell(), "broken chest");
        helper.succeed();
    }

    /**
     * A lever popped by its support breaking leaves nothing behind. RED before the change:
     * {@code LeverBlock} overrides the per-block removal hook and calls only its own
     * {@code updateNeighbours}, never the base version. This row's value is ROUTE coverage — the
     * shape-update pop (support removal, {@code updateShape} returns air, {@code updateOrDestroy}
     * destroys the lever) rather than a direct break — and it also pins LAW 1 from the other side:
     * the lever's height is still there right up until the lever's OWN cell is emptied, so the
     * support's removal never reached it.
     *
     * <p>MUTATION: NO mutation of the seam reddens this row alone, and this row does not claim one.
     * The nearest is {@code if (oldState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
     * .isEmpty()) return;}, which also reddens {@link #waterTakingTheCellClearsStoredHeight} — and
     * the two cannot be separated by re-picking that row's block, because a block water washes away
     * must be non-motion-blocking, which is the same population that has an empty collision shape.
     * The reason is structural and is the design's own central claim: the seam is deliberately
     * ROUTE-BLIND, so no mutation of it can tell "a player broke it" from "a shape update popped it"
     * — both arrive as (oldState, AIR, flags 3). The pair is still discriminated in one direction:
     * the water row has a mutation that reddens the water row alone.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void poppedLeverLeavesNoStoredHeight(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        Cell rig = build(helper, w, Blocks.LEVER.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));

        requireFact(helper, w, rig.cell(),
                "premise: the lever still carries its height with its support intact");
        w.destroyBlock(rig.support(), false);

        requireAir(helper, w, rig.cell(), "the popped lever's cell");
        requireNoFact(helper, w, rig.cell(), "popped lever");
        helper.succeed();
    }

    /**
     * Water taking the cell clears it. RED before the change: {@code BaseRailBlock} is one of the 28
     * classes that override the per-block removal hook without the base call.
     *
     * <p>The write below IS vanilla's terminal write verbatim, not an approximation of a flow:
     * {@code FlowingFluid.spreadTo} calls {@code beforeDestroyingBlock} and then
     * {@code setBlockAndUpdate(pos, createLegacyBlock())}, which is {@code setBlock(..., 3)}. This
     * row drives that write; it does not drive a whole spread.
     *
     * <p>MUTATION that reddens this row alone: add {@code if (!newState.isAir()) return;} to
     * {@code clearFactForDepartedOccupant} — a plausible "only a genuine emptying clears" narrowing.
     * This is the only clear row whose new occupant is not air.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void waterTakingTheCellClearsStoredHeight(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        Cell rig = build(helper, w, Blocks.RAIL.defaultBlockState());

        w.setBlock(rig.cell(), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);

        requireNoFact(helper, w, rig.cell(), "rail washed away by water");
        helper.succeed();
    }

    /**
     * The strict form of {@code /setblock} clears too. STONE is deliberate: this row proves the hole
     * is in the UPDATE FLAGS, not in any block class. RED before the change: at flags 818 neither the
     * chunk's own gated call (the neighbours bit is clear and so is the moved bit) nor
     * {@code ServerLevel.updateNeighboursOnBlockSet} (which the strict form skips) ever runs, for any
     * block whatsoever.
     *
     * <p>MUTATION that reddens this row alone: restore vanilla's flag gate to the seam,
     * {@code if ((flags & Block.UPDATE_NEIGHBORS) == 0 && !movedByPiston) return;}. Every other clear
     * row writes at flags 3, and the keep rows expect no clear.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void strictSetBlockAirClearsStoredHeight(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        Cell rig = build(helper, w, Blocks.STONE.defaultBlockState());

        w.setBlock(rig.cell(), Blocks.AIR.defaultBlockState(), STRICT_SETBLOCK_FLAGS);

        requireAir(helper, w, rig.cell(), "the strictly emptied cell");
        requireNoFact(helper, w, rig.cell(), "strict setblock to air");
        helper.succeed();
    }

    // ── rows: the keep ───────────────────────────────────────────────────────────────────────

    /**
     * An in-place transform keeps its height at LOW flags too. The widening newly reaches the
     * decision on writes that carry neither the neighbours bit nor the moved bit, and this row is the
     * guard on that newly-reached path (D1: grass to dirt, a log being stripped, copper oxidising).
     *
     * <p>MUTATION: delete the {@code !movedByPiston && replacementPreservesAnchor(...)} early return
     * from {@code clearFactForDepartedOccupant}. Unique within this class. HONEST NOTE: across the
     * suite it is SHARED with {@code StateChangeAnchorTest.inPlaceTransformKeepsAnchorAndDy}, which
     * writes with UPDATE_ALL and so cannot reach the low-flag path this row covers.
     *
     * <p>NOT red before the change: at flags 2 the decision was never reached at all, so the lock was
     * kept by accident rather than by rule. Stated as a guard against the widening, not a fix.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lowFlagInPlaceTransformKeepsStoredHeight(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        Cell rig = build(helper, w, Blocks.GRASS_BLOCK.defaultBlockState());

        w.setBlock(rig.cell(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);

        requireBits(helper, w, rig.cell(), LOWERED, "grass transformed in place to dirt at flags 2");
        helper.succeed();
    }

    /**
     * A same-block PROPERTY rewrite never touches the height. The block is still standing there, so
     * moving its height would be the LAW 1 violation.
     *
     * <p>DO NOT re-add vanilla's own {@code || newBlock instanceof BaseRailBlock} clause to the seam's
     * gate. Vanilla widens its removal-hook call that way, and copying it is the one place where
     * following vanilla would create a LAW 1 violation: the seam would then judge a rail on its own
     * shape rewrite, {@code replacementPreservesAnchor} would answer false for it (a rail is not a
     * full block, not full-footprint, not connecting-structural, not a block entity), and a rail that
     * never moved would lose its height.
     *
     * <p>MUTATION that reddens this row alone: add that clause,
     * {@code if (oldState.is(newState.getBlock()) && !(newState.getBlock() instanceof BaseRailBlock))
     * return;}. NOT red before the change — the widening is what makes same-block rewrites reachable
     * at all, and nothing else in the suite pins that they are skipped (the law gate's door and bed
     * rows toggle properties on the same block, so they skip by coincidence rather than by pin).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void railShapeRewriteKeepsStoredHeight(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        Cell rig = build(helper, w, Blocks.RAIL.defaultBlockState()
                .setValue(BlockStateProperties.RAIL_SHAPE, RailShape.NORTH_SOUTH));

        w.setBlock(rig.cell(), Blocks.RAIL.defaultBlockState()
                .setValue(BlockStateProperties.RAIL_SHAPE, RailShape.EAST_WEST), Block.UPDATE_ALL);

        requireBits(helper, w, rig.cell(), LOWERED, "a rail rewritten to a different shape in place");
        helper.succeed();
    }

    /**
     * A piston that carries its own height keeps it through a whole cycle, and the head cell it
     * vacates keeps none.
     *
     * <p>The BASE half is the one genuinely new input this widening creates: vanilla's retract writes
     * the piston's own cell from PISTON to MOVING_PISTON at flags 276, which reaches no removal hook
     * today and which the seam judges for the first time. The arrival guard is what keeps it. The
     * HEAD half is what justifies deleting the dedicated piston-head removal mixin: the seam covers
     * the same cell at the write instead.
     *
     * <p>MUTATION: delete the {@code newState.is(Blocks.MOVING_PISTON) && !movedByPiston} arrival
     * guard from {@code clearFactForDepartedOccupant}. HONEST NOTE: it is NOT unique in the suite —
     * it also reddens {@code PistonPlacementDyTransferTest.loweredPistonBaseKeepsItsOwnFactAcross
     * ExtendAndRetract}, which drives the same vanilla write. Within this class it is unique: no
     * other row here writes MOVING_PISTON without the moved bit, and the carry destinations that DO
     * carry that bit must still clear, which this mutation leaves untouched.
     *
     * <p>NOT red before the change: the base hazard does not exist until the flag gate is dropped,
     * and the head half is green today via the mixin being deleted. The row is what proves that
     * coverage survives the deletion.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void retractingPistonKeepsBaseAndEmptiesHeadCell(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos pistonPos = helper.absolutePos(new BlockPos(1, 2, 3));
        BlockPos head = helper.absolutePos(new BlockPos(2, 2, 3));
        w.setBlock(pistonPos, Blocks.PISTON.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST), Block.UPDATE_CLIENTS);
        SlabAnchorAttachment.writePlacementDy(w, pistonPos, LOWERED);

        helper.startSequence()
                .thenExecute(() -> {
                    power(w, pistonPos);
                    fire(w, pistonPos, Direction.EAST, EVENT_EXTEND);
                    landTicked(w, head);
                    if (!w.getBlockState(head).is(Blocks.PISTON_HEAD)) {
                        throw helper.assertionException(head,
                                "premise: the piston must be extended, found " + w.getBlockState(head));
                    }
                    requireBits(helper, w, head, LOWERED,
                            "premise: the extended head must carry the base's height");
                    requireBits(helper, w, pistonPos, LOWERED, "the piston base while extended");
                    unpower(w, pistonPos);
                    fire(w, pistonPos, Direction.EAST, EVENT_CONTRACT);
                })
                .thenWaitUntil(() -> {
                    if (!w.getBlockState(pistonPos).is(Blocks.PISTON)) {
                        throw helper.assertionException(pistonPos,
                                "the piston base must return after the retract completes, found "
                                        + w.getBlockState(pistonPos));
                    }
                })
                .thenExecute(() -> {
                    requireBits(helper, w, pistonPos, LOWERED, "the piston base after retracting");
                    requireAir(helper, w, head, "the vacated head cell");
                    requireNoFact(helper, w, head, "the vacated head cell");
                })
                .thenSucceed();
    }

    /**
     * Handing a stand-in cell off to AIR is a real departure and still clears. This is the
     * interrupted source piston: the animation entity vanilla flagged as the source lands air in its
     * own cell, through {@code setBlockAndUpdate} at flags 3.
     *
     * <p>MUTATION: drop the {@code && !newState.isAir()} qualifier from the hand-off guard, making it
     * a blanket {@code if (oldState.is(Blocks.MOVING_PISTON)) return;}. HONEST NOTE: it is NOT unique
     * in the suite — it also reddens {@code PistonPlacementDyTransferTest.interruptedSourcePiston
     * LeavesNoFactInTheAirCell}, which names the same mutation for the same vanilla route. This row
     * re-pins that half of the guard at the seam that now owns it, after the mixin that owned it is
     * deleted; it is not new coverage, and it is not claimed as such.
     *
     * <p>NOT red before the change.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void movingPistonHandoffToAirClearsStoredHeight(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos pistonPos = helper.absolutePos(new BlockPos(1, 2, 3));
        BlockPos head = helper.absolutePos(new BlockPos(2, 2, 3));
        w.setBlock(pistonPos, Blocks.STICKY_PISTON.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST), Block.UPDATE_CLIENTS);
        SlabAnchorAttachment.writePlacementDy(w, pistonPos, LOWERED);

        power(w, pistonPos);
        fire(w, pistonPos, Direction.EAST, EVENT_EXTEND);
        landTicked(w, head);
        if (!w.getBlockState(head).is(Blocks.PISTON_HEAD)) {
            throw helper.assertionException(head,
                    "premise: the piston must be extended, found " + w.getBlockState(head));
        }

        unpower(w, pistonPos);
        fire(w, pistonPos, Direction.EAST, EVENT_CONTRACT);
        if (!w.getBlockState(pistonPos).is(Blocks.MOVING_PISTON)) {
            throw helper.assertionException(pistonPos,
                    "premise: the retracting base must hold the animation stand-in, found "
                            + w.getBlockState(pistonPos));
        }
        requireFact(helper, w, pistonPos,
                "premise: the stand-in cell must still carry the height it was given");
        landNow(w, pistonPos);

        requireAir(helper, w, pistonPos, "the interrupted source piston's own cell");
        requireNoFact(helper, w, pistonPos, "stand-in handed off to air");
        helper.succeed();
    }
}
