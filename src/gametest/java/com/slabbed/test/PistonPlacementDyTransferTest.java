package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A piston moves a block; the height that block was placed at moves with it, bit for bit (LAW 1;
 * maintainer ruling, 2026-09-06).
 *
 * <p>A piston animation can end two different ways and the two write the landing cell differently —
 * the ticked landing (the ordinary completion, which carries the moved-by-piston update bit) and the
 * interrupt landing (an ordinary update, where a full cube would survive the removal hook on its own
 * but a slab or carpet would not). Both are exercised by their own rows, because a guard narrowed to
 * either branch leaves the other one red.
 *
 * <p>Every row asserts on raw bits, never an epsilon. The suite venue runs with the frozen store off,
 * so the rows that assert a GAMEPLAY consequence (a resolved height, a shape) open the store around
 * that assertion the same way the law gate's frozen-store rows do; the stored-fact assertions
 * themselves are venue-independent.
 *
 * <p>Driver: the piston is powered with a redstone block written without neighbour updates, and the
 * block event is then fired explicitly. That is deliberate — it keeps every row synchronous at the
 * move boundary and stops vanilla queueing a second event of its own for the same push. The event
 * itself is vanilla's own, and it still refuses to extend unless the piston is genuinely powered.
 */
public final class PistonPlacementDyTransferTest {

    private static final double LOWERED = -0.5d;
    private static final double DEEPER = -1.0d;
    private static final double BRANCH = -0.25d;
    private static final double SHAPE_EPS = 1.0e-6d;

    // Piston block-event types, as vanilla numbers them.
    private static final int EVENT_EXTEND = 0;
    private static final int EVENT_CONTRACT = 1;

    // ── drivers ──────────────────────────────────────────────────────────────────────────────

    private static BlockState piston(Block kind, Direction facing) {
        return kind.defaultBlockState().setValue(BlockStateProperties.FACING, facing);
    }

    /**
     * Powers the piston without a neighbour update, so vanilla queues no block event of its own and
     * the explicit fire below is the only move in the row. The signal itself is read live from the
     * world, so a quiet write is enough for the piston to see it.
     */
    private static void power(ServerLevel w, BlockPos pistonPos) {
        w.setBlock(pistonPos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void unpower(ServerLevel w, BlockPos pistonPos) {
        w.setBlock(pistonPos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Vanilla's own piston block event, fired in a known tick. */
    private static void fire(ServerLevel w, BlockPos pistonPos, Direction facing, int type) {
        w.getBlockState(pistonPos).triggerEvent(w, pistonPos, type, facing.get3DDataValue());
    }

    /**
     * Reaches the INTERRUPT landing in the same tick. Player-reachable equivalent: replacing the
     * moving-piston cell, whose pre-remove side effect calls this same method. The ticked landing —
     * the normal completion — is driven by letting the block entity tick instead, and has its own
     * rows; nothing here may be read as proof of that path.
     */
    /**
     * The INTERRUPT landing: vanilla's own completion for a moving-piston cell that is replaced or
     * broken mid-animation. A pushed block lands as its moved state; an animation entity vanilla
     * flagged as the SOURCE piston (an extending head, a retracting base) lands as AIR by design, so
     * this must never be used to complete an extend where a head is expected — use
     * {@link #landTicked} for that.
     */
    private static void landNow(ServerLevel w, BlockPos... cells) {
        for (BlockPos cell : cells) {
            BlockEntity be = w.getBlockEntity(cell);
            if (be instanceof PistonMovingBlockEntity moving) {
                moving.finalTick();
            }
        }
    }

    /**
     * The ORDINARY completion: drives the moving block entity's own tick until it lands, exactly as
     * the server would over the following ticks. This is the only landing that installs an extending
     * head, because vanilla resolves that entity's interrupt landing to air.
     */
    private static void landTicked(ServerLevel w, BlockPos... cells) {
        for (BlockPos cell : cells) {
            for (int step = 0; step < 8; step++) {
                BlockEntity be = w.getBlockEntity(cell);
                if (!(be instanceof PistonMovingBlockEntity moving)) {
                    break;
                }
                PistonMovingBlockEntity.tick(w, cell, w.getBlockState(cell), moving);
            }
        }
    }

    // ── fact reads ───────────────────────────────────────────────────────────────────────────

    private static long bits(GameTestHelper h, ServerLevel w, BlockPos pos, String what) {
        SlabAnchorAttachment.PlacementDyFact fact = SlabAnchorAttachment.rawPlacementDyFact(w, pos);
        if (!fact.present()) {
            throw h.assertionException(pos, what + ": no stored height at this cell");
        }
        return fact.rawBits();
    }

    private static void requireBits(GameTestHelper h, ServerLevel w, BlockPos pos, double expected, String what) {
        long actual = bits(h, w, pos, what);
        long wanted = Double.doubleToRawLongBits(expected);
        if (actual != wanted) {
            throw h.assertionException(pos, what + ": stored height must arrive bit-identical, expected "
                    + expected + " got " + Double.longBitsToDouble(actual));
        }
    }

    private static void requireNoFact(GameTestHelper h, ServerLevel w, BlockPos pos, String what) {
        SlabAnchorAttachment.PlacementDyFact fact = SlabAnchorAttachment.rawPlacementDyFact(w, pos);
        if (fact.present()) {
            throw h.assertionException(pos, what + ": this cell must hold no stored height, found "
                    + fact.valueOrNaN());
        }
    }

    private static void requireBlock(GameTestHelper h, ServerLevel w, BlockPos pos, Block block, String what) {
        if (!w.getBlockState(pos).is(block)) {
            throw h.assertionException(pos, what + ": expected " + block + " here, found "
                    + w.getBlockState(pos));
        }
    }

    /** Runs a body under the shipped frozen-store mode while preserving the suite's frozen-off floor. */
    private static <T> T withFrozenStore(Supplier<T> body) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            return body.get();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    private static double resolvedDy(ServerLevel w, BlockPos pos) {
        return withFrozenStore(() -> SlabSupport.getYOffset(w, pos, w.getBlockState(pos)));
    }

    // ── arena ────────────────────────────────────────────────────────────────────────────────

    /**
     * Airs the working band and drops any stored height in it. The air write is deliberately quiet
     * (it must not fire vanilla's removal hook, which is one of the things under test), so the facts
     * are dropped explicitly rather than as a side effect of the write.
     */
    private static void clearBand(GameTestHelper h, ServerLevel w) {
        for (int x = 0; x <= 6; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = 1; z <= 5; z++) {
                    BlockPos p = h.absolutePos(new BlockPos(x, y, z));
                    w.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    SlabAnchorAttachment.removeAnchor(w, p);
                }
            }
        }
    }

    private record PushRig(BlockPos pistonPos, BlockPos source, BlockPos destination, BlockPos head) {
    }

    /**
     * Piston at x=1 facing east, subject at x=2, destination at x=3. The head lands in the subject's
     * old cell, which is exactly why a source cell is also a destination cell here.
     */
    private static PushRig buildPushRig(GameTestHelper h, ServerLevel w, Block pistonKind,
                                        BlockState subject, double dy) {
        BlockPos pistonPos = h.absolutePos(new BlockPos(1, 2, 3));
        BlockPos source = h.absolutePos(new BlockPos(2, 2, 3));
        BlockPos destination = h.absolutePos(new BlockPos(3, 2, 3));
        for (int x = 2; x <= 5; x++) {
            w.setBlock(h.absolutePos(new BlockPos(x, 1, 3)), Blocks.STONE.defaultBlockState(),
                    Block.UPDATE_CLIENTS);
        }
        w.setBlock(pistonPos, piston(pistonKind, Direction.EAST), Block.UPDATE_CLIENTS);
        w.setBlock(source, subject, Block.UPDATE_CLIENTS);
        SlabAnchorAttachment.writePlacementDy(w, source, dy);
        return new PushRig(pistonPos, source, destination, source);
    }

    private static BlockState bottomSlab() {
        return Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    // ── rows ─────────────────────────────────────────────────────────────────────────────────

    /**
     * The subject is a SLAB on purpose: the interrupt landing reaches the removal seam as an ordinary
     * update, where the in-place-transform allowance would keep a full cube's height without any
     * change here but never a slab's.
     *
     * <p>MUTATION that must redden this row alone: narrow the moving-piston hand-off guard in
     * {@code SlabAnchorAttachment.clearFactForDepartedOccupant} to the moved-by-piston branch only. The
     * ticked-landing row stays green.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void pushedLoweredSlabKeepsItsFactThroughTheInterruptLanding(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        PushRig rig = buildPushRig(h, w, Blocks.PISTON, bottomSlab(), LOWERED);

        power(w, rig.pistonPos());
        fire(w, rig.pistonPos(), Direction.EAST, EVENT_EXTEND);
        landNow(w, rig.destination(), rig.head());

        requireBlock(h, w, rig.destination(), Blocks.SMOOTH_STONE_SLAB, "the pushed slab");
        requireBits(h, w, rig.destination(), LOWERED, "the pushed slab's new cell");
        requireNoFact(h, w, rig.source(), "the vacated source cell");
        double resolved = resolvedDy(w, rig.destination());
        if (Double.doubleToRawLongBits(resolved) != Double.doubleToRawLongBits(LOWERED)) {
            throw h.assertionException(rig.destination(),
                    "the landed slab must RESOLVE to the height it was placed at, got " + resolved);
        }
        h.succeed();
    }

    /**
     * The subject is a full CUBE on purpose: the ordinary completion reaches the removal hook with the
     * moved-by-piston bit set, which clears unconditionally without the guard, so this row can only be
     * about that branch.
     *
     * <p>MUTATION that must redden this row alone: narrow the moving-piston guard to the ordinary-
     * update branch only. The interrupt-landing row stays green.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void pushedLoweredStoneKeepsItsFactThroughTheTickedLanding(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        PushRig rig = buildPushRig(h, w, Blocks.PISTON, Blocks.STONE.defaultBlockState(), LOWERED);

        h.startSequence()
                .thenExecute(() -> {
                    power(w, rig.pistonPos());
                    fire(w, rig.pistonPos(), Direction.EAST, EVENT_EXTEND);
                })
                .thenWaitUntil(() -> requireBlock(h, w, rig.destination(), Blocks.STONE,
                        "the pushed stone after the ordinary completion"))
                .thenExecute(() -> {
                    requireBits(h, w, rig.destination(), LOWERED, "the pushed stone's new cell");
                    double resolved = resolvedDy(w, rig.destination());
                    if (Double.doubleToRawLongBits(resolved) != Double.doubleToRawLongBits(LOWERED)) {
                        throw h.assertionException(rig.destination(),
                                "the landed stone must RESOLVE to the height it was placed at, got " + resolved);
                    }
                })
                .thenSucceed();
    }

    /**
     * Measures the batch at the move boundary itself, with the animation stand-in still occupying the
     * destination and nothing landed yet.
     *
     * <p>MUTATION that must redden this row alone: delete the push loop in the capture, so no
     * destination fact is written. Rows that move no block stay green.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void pushedLoweredStoneArrivesBitEqualAndTheSourceClears(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        PushRig rig = buildPushRig(h, w, Blocks.PISTON, Blocks.STONE.defaultBlockState(), LOWERED);

        power(w, rig.pistonPos());
        fire(w, rig.pistonPos(), Direction.EAST, EVENT_EXTEND);

        requireBlock(h, w, rig.destination(), Blocks.MOVING_PISTON,
                "premise: the destination must still hold the animation stand-in");
        requireBits(h, w, rig.destination(), LOWERED, "the destination during the animation");
        // The source cell is the head's destination here, so vanilla's own destination write clears it.
        requireNoFact(h, w, rig.source(), "the vacated source cell");
        // Nothing is carried past the cells that actually moved.
        requireNoFact(h, w, rig.destination().east(), "the cell beyond the destination");
        h.succeed();
    }

    /**
     * Scene A pins ORDER: one block's destination is the next block's source, so a smear shows up as
     * both cells reading the first value. Scene B pins TOPOLOGY: a slime branch member is not on the
     * piston axis at all, and the carry follows the resolver's own list rather than the axis, which is
     * what makes slime and honey chains work by construction.
     *
     * <p>MUTATION that must redden this row alone: move the capture from the resolve wrapper to the
     * publish handler. By then vanilla has written the stand-in over both source cells, so a late
     * capture reads nothing and writes nothing. This is the only row that pins read-before-any-write.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void twoBlockPushAndSlimeBranchCarryEveryFactInOrder(GameTestHelper h) {
        ServerLevel w = h.getLevel();

        // Scene A — two collinear blocks with distinct heights.
        BlockPos pistonPos = h.absolutePos(new BlockPos(1, 2, 3));
        BlockPos first = h.absolutePos(new BlockPos(2, 2, 3));
        BlockPos second = h.absolutePos(new BlockPos(3, 2, 3));
        w.setBlock(pistonPos, piston(Blocks.PISTON, Direction.EAST), Block.UPDATE_CLIENTS);
        w.setBlock(first, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        w.setBlock(second, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        SlabAnchorAttachment.writePlacementDy(w, first, LOWERED);
        SlabAnchorAttachment.writePlacementDy(w, second, DEEPER);
        power(w, pistonPos);
        fire(w, pistonPos, Direction.EAST, EVENT_EXTEND);
        landNow(w, first, second, second.east());

        requireBits(h, w, second, LOWERED, "the first block's new cell");
        requireBits(h, w, second.east(), DEEPER, "the second block's new cell");
        requireNoFact(h, w, first, "the vacated first source cell");

        // Scene B — a branch member off the piston axis.
        clearBand(h, w);
        BlockPos slime = h.absolutePos(new BlockPos(2, 2, 3));
        BlockPos branch = h.absolutePos(new BlockPos(2, 2, 2));
        w.setBlock(pistonPos, piston(Blocks.PISTON, Direction.EAST), Block.UPDATE_CLIENTS);
        w.setBlock(slime, Blocks.SLIME_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        w.setBlock(branch, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        SlabAnchorAttachment.writePlacementDy(w, branch, BRANCH);
        power(w, pistonPos);
        fire(w, pistonPos, Direction.EAST, EVENT_EXTEND);
        landNow(w, slime, slime.east(), branch, branch.east());

        requireBlock(h, w, branch.east(), Blocks.STONE, "the branch member's new cell");
        requireBits(h, w, branch.east(), BRANCH, "the branch member's new cell");
        requireNoFact(h, w, branch, "the vacated branch source cell");
        h.succeed();
    }

    /**
     * MUTATION that must redden this row alone: gate the capture on the extending case, skipping
     * retraction. Every extend row stays green.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void stickyPullCarriesTheFactBack(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos pistonPos = h.absolutePos(new BlockPos(1, 2, 3));
        BlockPos head = h.absolutePos(new BlockPos(2, 2, 3));
        BlockPos pulled = h.absolutePos(new BlockPos(3, 2, 3));
        w.setBlock(pistonPos, piston(Blocks.STICKY_PISTON, Direction.EAST), Block.UPDATE_CLIENTS);

        h.startSequence()
                .thenExecute(() -> {
                    power(w, pistonPos);
                    fire(w, pistonPos, Direction.EAST, EVENT_EXTEND);
                    landTicked(w, head);
                    requireBlock(h, w, head, Blocks.PISTON_HEAD, "premise: the piston must be extended");
                    w.setBlock(pulled, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    SlabAnchorAttachment.writePlacementDy(w, pulled, LOWERED);
                    unpower(w, pistonPos);
                    fire(w, pistonPos, Direction.EAST, EVENT_CONTRACT);
                })
                .thenWaitUntil(() -> requireBlock(h, w, head, Blocks.STONE,
                        "the pulled stone after the ordinary completion"))
                .thenExecute(() -> {
                    requireBits(h, w, head, LOWERED, "the pulled stone's new cell");
                    requireNoFact(h, w, pulled, "the vacated source cell");
                })
                .thenSucceed();
    }

    /**
     * The piston's own cell is never a destination and never a leftover source, so its height must
     * survive both halves of a cycle untouched.
     *
     * <p>MUTATION that must redden this row alone: re-add a clear of the piston base or head cell in
     * the publish handler — the branch this design deliberately does not write. This row is also the
     * standing tripwire for the deliberate donor divergence: if a future vanilla change ever makes the
     * extend's own write reach the removal hook, it reddens first. HONEST NOTE: this row also reddens
     * under the guard-narrowing mutation named for the ticked-landing row, so the "alone" claim rests
     * on the re-added-clear mutation.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void loweredPistonBaseKeepsItsOwnFactAcrossExtendAndRetract(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos pistonPos = h.absolutePos(new BlockPos(1, 2, 3));
        BlockPos head = h.absolutePos(new BlockPos(2, 2, 3));
        w.setBlock(pistonPos, piston(Blocks.STICKY_PISTON, Direction.EAST), Block.UPDATE_CLIENTS);
        SlabAnchorAttachment.writePlacementDy(w, pistonPos, LOWERED);

        h.startSequence()
                .thenExecute(() -> {
                    power(w, pistonPos);
                    fire(w, pistonPos, Direction.EAST, EVENT_EXTEND);
                    landTicked(w, head);
                    requireBlock(h, w, head, Blocks.PISTON_HEAD, "premise: the piston must be extended");
                    requireBits(h, w, pistonPos, LOWERED, "the piston base after extending");
                    unpower(w, pistonPos);
                    fire(w, pistonPos, Direction.EAST, EVENT_CONTRACT);
                })
                .thenWaitUntil(() -> requireBlock(h, w, pistonPos, Blocks.STICKY_PISTON,
                        "the piston base after the ordinary completion of the retract"))
                .thenExecute(() -> requireBits(h, w, pistonPos, LOWERED,
                        "the piston base after retracting"))
                .thenSucceed();
    }

    /**
     * The extended head is the base's own body, so it inherits the base's height — and this row asserts
     * the GAMEPLAY consequence the ruling signs off on, not only the stored value: while the piston
     * stays extended, the head's collision box and outline sit at the base's height, so the shape
     * agrees with the picture (maintainer ruling, 2026-09-06).
     *
     * <p>MUTATION that must redden this row alone: delete the extending head-carry clause in the
     * capture. No other row touches the head cell's own height. Its final clause — the vacated head
     * keeps no height — is also the tripwire for the removal seam at the vacated head cell: the head
     * block overrides the per-block removal hook without the base call, so only a clear at the chunk
     * write keeps the carried height from outliving the head.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void extendedHeadInheritsTheBaseFactAndItsShapes(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos pistonPos = h.absolutePos(new BlockPos(1, 2, 3));
        BlockPos head = h.absolutePos(new BlockPos(2, 2, 3));
        // A second, identical piston with NO height of its own is the shape control, so the comparison
        // is between two real extended heads rather than against a hand-computed vanilla box.
        BlockPos controlPiston = h.absolutePos(new BlockPos(1, 2, 5));
        BlockPos controlHead = h.absolutePos(new BlockPos(2, 2, 5));
        w.setBlock(pistonPos, piston(Blocks.PISTON, Direction.EAST), Block.UPDATE_CLIENTS);
        w.setBlock(controlPiston, piston(Blocks.PISTON, Direction.EAST), Block.UPDATE_CLIENTS);
        SlabAnchorAttachment.writePlacementDy(w, pistonPos, LOWERED);

        h.startSequence()
                .thenExecute(() -> {
                    power(w, pistonPos);
                    fire(w, pistonPos, Direction.EAST, EVENT_EXTEND);
                    landTicked(w, head);
                    power(w, controlPiston);
                    fire(w, controlPiston, Direction.EAST, EVENT_EXTEND);
                    landTicked(w, controlHead);
                    requireBlock(h, w, head, Blocks.PISTON_HEAD, "premise: the piston must be extended");
                    requireBlock(h, w, controlHead, Blocks.PISTON_HEAD,
                            "premise: the control piston must be extended");
                    requireNoFact(h, w, controlHead, "premise: the control head must carry no height");
                    requireBits(h, w, head, LOWERED, "the extended head cell");
                    requireShapeDrop(h, w, head, controlHead, false, "the extended head's collision box");
                    requireShapeDrop(h, w, head, controlHead, true, "the extended head's outline");
                    unpower(w, pistonPos);
                    fire(w, pistonPos, Direction.EAST, EVENT_CONTRACT);
                })
                .thenWaitUntil(() -> {
                    if (!w.getBlockState(head).isAir()) {
                        throw h.assertionException(head,
                                "the head cell must be vacated when the piston retracts, found "
                                        + w.getBlockState(head));
                    }
                })
                .thenExecute(() -> requireNoFact(h, w, head, "the vacated head cell"))
                .thenSucceed();
    }

    /**
     * Compares the same block state at two real cells — one carrying the stored height, one carrying
     * none — under the shipped frozen store, so the difference IS the stored height's effect on the
     * shape and nothing else. Shapes are cell-local, so their Y bounds are directly comparable.
     */
    private static void requireShapeDrop(GameTestHelper h, ServerLevel w, BlockPos pos, BlockPos control,
                                         boolean outline, String what) {
        BlockState state = w.getBlockState(pos);
        BlockState controlState = w.getBlockState(control);
        if (!state.equals(controlState)) {
            throw h.assertionException(pos, what + ": premise failed, the control cell must hold the"
                    + " same state, found " + controlState + " against " + state);
        }
        VoxelShape flat = withFrozenStore(() -> outline
                ? controlState.getShape(w, control, CollisionContext.empty())
                : controlState.getCollisionShape(w, control, CollisionContext.empty()));
        VoxelShape lowered = withFrozenStore(() -> outline
                ? state.getShape(w, pos, CollisionContext.empty())
                : state.getCollisionShape(w, pos, CollisionContext.empty()));
        if (flat.isEmpty() || lowered.isEmpty()) {
            throw h.assertionException(pos, what + ": premise failed, the shape must not be empty");
        }
        double drop = lowered.min(Direction.Axis.Y) - flat.min(Direction.Axis.Y);
        double dropTop = lowered.max(Direction.Axis.Y) - flat.max(Direction.Axis.Y);
        if (Math.abs(drop - LOWERED) > SHAPE_EPS || Math.abs(dropTop - LOWERED) > SHAPE_EPS) {
            throw h.assertionException(pos, what + ": must sit at the base's height, moved by "
                    + drop + " at the bottom and " + dropTop + " at the top");
        }
    }

    /**
     * A destroyed block does not arrive anywhere, so its height must not travel. The pushed block's
     * destination IS the destroyed block's old cell here, which is what makes a wrong carry visible as
     * the wrong value rather than as an extra one.
     *
     * <p>MUTATION that must redden this row alone: add the destroy list to the carry loop. No other
     * row puts a destructible block in the push path. This row is a permanent guard on a deliberate
     * omission rather than a red-first row: today nothing is carried at all, so its
     * destination-carries-nothing half is trivially satisfied and only the mutation makes it
     * informative.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void destroyedBlockCarriesNothing(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockState destructible = null;
        for (Block candidate : List.of(Blocks.TORCH, Blocks.SHORT_GRASS, Blocks.DEAD_BUSH)) {
            BlockState state = candidate.defaultBlockState();
            if (state.getPistonPushReaction() == PushReaction.POPPED) {
                destructible = state;
                break;
            }
        }
        if (destructible == null) {
            throw h.assertionException("premise: this row needs a block a piston destroys rather than"
                    + " pushes, and none of the candidates reports that push reaction");
        }
        PushRig rig = buildPushRig(h, w, Blocks.PISTON, Blocks.STONE.defaultBlockState(), LOWERED);
        w.setBlock(rig.destination(), destructible, Block.UPDATE_CLIENTS);
        SlabAnchorAttachment.writePlacementDy(w, rig.destination(), DEEPER);

        power(w, rig.pistonPos());
        fire(w, rig.pistonPos(), Direction.EAST, EVENT_EXTEND);
        landNow(w, rig.destination(), rig.head());

        requireBlock(h, w, rig.destination(), Blocks.STONE,
                "premise: the pushed stone must land in the destroyed block's old cell");
        requireBits(h, w, rig.destination(), LOWERED, "the destroyed block's old cell");
        requireNoFact(h, w, rig.destination().east(),
                "the cell a carried destroyed height would have landed in");
        h.succeed();
    }

    /**
     * The law gate's own catalogue cannot express a piston move — its contract is that the subject's
     * own cell is never touched, and a push relocates the subject — so the law obligation for a moved
     * block is discharged here instead: replay that same mutation set against the block's NEW cell.
     *
     * <p>Run under the shipped frozen store, like the law gate's frozen-store rows: the claim is about
     * the STORED height surviving neighbour edits, which is unobservable with the store off.
     *
     * <p>MUTATION that must redden this row: delete the push carry, which fails the non-zero premise.
     * Beyond that shared mutation this row is coverage, not mutation-discrimination, and is stated as
     * such.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void pushedBlockIsNeighbourInvariantAtItsNewCell(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        List<String> violations = new ArrayList<>();
        for (NamedMutation mutation : MUTATIONS) {
            clearBand(h, w);
            PushRig rig = buildPushRig(h, w, Blocks.PISTON, Blocks.STONE.defaultBlockState(), LOWERED);
            power(w, rig.pistonPos());
            fire(w, rig.pistonPos(), Direction.EAST, EVENT_EXTEND);
            landNow(w, rig.destination(), rig.head());
            BlockPos subject = rig.destination();
            requireBlock(h, w, subject, Blocks.STONE, "premise: the pushed stone must have landed");

            double before = resolvedDy(w, subject);
            // Vacuity guard: a flat cell would make every later comparison meaningless.
            if (!Double.isFinite(before) || Math.abs(before) < SHAPE_EPS) {
                throw h.assertionException(subject, "premise: the landed block must resolve to a"
                        + " finite, non-zero height before any mutation, got " + before);
            }
            mutation.mutation().apply(w, subject);
            // Vanilla-mechanic carve-out: vanilla removing the block itself is not a height violation.
            if (w.getBlockState(subject).isAir()) {
                continue;
            }
            double after = resolvedDy(w, subject);
            if (Double.doubleToRawLongBits(before) != Double.doubleToRawLongBits(after)) {
                violations.add(mutation.name() + ": dy " + before + " -> " + after);
            }
        }
        if (!violations.isEmpty()) {
            throw h.assertionException("LAW VIOLATION — a block a piston moved changed height on"
                    + " neighbour edits at its new cell:\n  " + String.join("\n  ", violations));
        }
        h.succeed();
    }

    @FunctionalInterface
    private interface Mutation {
        void apply(ServerLevel w, BlockPos subject);
    }

    private record NamedMutation(String name, Mutation mutation) {
    }

    private static void bottomSlabAt(ServerLevel w, BlockPos pos) {
        w.setBlock(pos, bottomSlab(), Block.UPDATE_CLIENTS);
    }

    /** The law gate's neighbour-edit catalogue, applied to the moved block's NEW cell. */
    private static final List<NamedMutation> MUTATIONS = List.of(
            new NamedMutation("add_slab_north", (w, s) -> bottomSlabAt(w, s.north())),
            new NamedMutation("add_slab_east", (w, s) -> bottomSlabAt(w, s.east())),
            new NamedMutation("add_full_block_north",
                    (w, s) -> w.setBlock(s.north(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS)),
            new NamedMutation("add_full_block_above",
                    (w, s) -> w.setBlock(s.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS)),
            new NamedMutation("add_lowered_stack_east", (w, s) -> {
                w.setBlock(s.east().below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                bottomSlabAt(w, s.east());
            }),
            new NamedMutation("break_north_neighbor", (w, s) -> w.destroyBlock(s.north(), false)),
            new NamedMutation("break_east_neighbor", (w, s) -> w.destroyBlock(s.east(), false)),
            new NamedMutation("break_west_neighbor", (w, s) -> w.destroyBlock(s.west(), false)),
            new NamedMutation("break_south_neighbor", (w, s) -> w.destroyBlock(s.south(), false)),
            new NamedMutation("break_directly_below", (w, s) -> w.destroyBlock(s.below(), false)),
            new NamedMutation("add_connecting_fence_east",
                    (w, s) -> w.setBlock(s.east(), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL))
    );

    /**
     * A block that arrives with no height of its own must not inherit the one the cell was holding.
     *
     * <p>MUTATION that must redden this row: NONE in this slice, stated honestly. This is an
     * environment guard on a narrow, citable vanilla dependency — the destination write carries the
     * moved-by-piston bit, which fires the removal hook and wipes the cell before the batch runs. If a
     * future vanilla flag change breaks that clear, this row reddens first and the fix is to make the
     * batch authoritative (write an explicit absent height for every destination whose source has
     * none).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void pulledFlatBlockDoesNotInheritTheVacatedHeadFact(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos pistonPos = h.absolutePos(new BlockPos(1, 2, 3));
        BlockPos head = h.absolutePos(new BlockPos(2, 2, 3));
        BlockPos pulled = h.absolutePos(new BlockPos(3, 2, 3));
        w.setBlock(pistonPos, piston(Blocks.STICKY_PISTON, Direction.EAST), Block.UPDATE_CLIENTS);
        SlabAnchorAttachment.writePlacementDy(w, pistonPos, LOWERED);

        h.startSequence()
                .thenExecute(() -> {
                    power(w, pistonPos);
                    fire(w, pistonPos, Direction.EAST, EVENT_EXTEND);
                    landTicked(w, head);
                    requireBits(h, w, head, LOWERED,
                            "premise: the extended head must have inherited the base's height");
                    // A plain stone, deliberately given no height of its own.
                    w.setBlock(pulled, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    requireNoFact(h, w, pulled, "premise: the block to be pulled must be factless");
                    unpower(w, pistonPos);
                    fire(w, pistonPos, Direction.EAST, EVENT_CONTRACT);
                })
                .thenWaitUntil(() -> requireBlock(h, w, head, Blocks.STONE,
                        "the pulled stone after the ordinary completion"))
                .thenExecute(() -> {
                    requireNoFact(h, w, head, "the cell the flat stone was pulled into");
                    double resolved = resolvedDy(w, head);
                    if (resolved != 0.0d) {
                        throw h.assertionException(head,
                                "a factless block must resolve flat, got " + resolved);
                    }
                })
                .thenSucceed();
    }

    /**
     * An empty cell must never keep a height, or the next occupant inherits it.
     *
     * <p>MUTATION that must redden this row alone: drop the not-air clause from the moving-piston
     * guard. No other row lands air in a moving-piston cell.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void interruptedSourcePistonLeavesNoFactInTheAirCell(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos pistonPos = h.absolutePos(new BlockPos(1, 2, 3));
        BlockPos head = h.absolutePos(new BlockPos(2, 2, 3));
        w.setBlock(pistonPos, piston(Blocks.STICKY_PISTON, Direction.EAST), Block.UPDATE_CLIENTS);
        SlabAnchorAttachment.writePlacementDy(w, pistonPos, LOWERED);

        power(w, pistonPos);
        fire(w, pistonPos, Direction.EAST, EVENT_EXTEND);
        landTicked(w, head);
        requireBlock(h, w, head, Blocks.PISTON_HEAD, "premise: the piston must be extended");

        unpower(w, pistonPos);
        fire(w, pistonPos, Direction.EAST, EVENT_CONTRACT);
        requireBlock(h, w, pistonPos, Blocks.MOVING_PISTON,
                "premise: the retracting base must hold the animation stand-in");
        landNow(w, pistonPos);

        if (!w.getBlockState(pistonPos).isAir()) {
            throw h.assertionException(pistonPos,
                    "premise: the interrupted source piston lands air in its own cell, found "
                            + w.getBlockState(pistonPos));
        }
        requireNoFact(h, w, pistonPos, "the emptied piston cell");
        h.succeed();
    }
}
