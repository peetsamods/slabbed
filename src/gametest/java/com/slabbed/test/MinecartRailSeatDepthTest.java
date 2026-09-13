package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.MinecartRailFrame;
import com.slabbed.util.RailSeatDyHolder;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A minecart on a lowered rail really is where it is drawn (maintainer ruling, 2026-09-06).
 *
 * <p>Rails carry no collision, so nothing ever pulled a cart down onto a rail laid on a lowered
 * block: the cart's position came from the rail's GRID cell and the mod only PAINTED it lower. These
 * rows pin the real position — the entity, its box, what can hit it and where a rider sits — plus the
 * seat's behaviour across a seam, across a save/load round trip, and in the render-side conversion.
 *
 * <p>FIXTURE NOTE. Two things about this suite's venue shape the fixtures. First, the gametest JVM
 * runs with the frozen store OFF, so a cell that must read a recorded height has to AUTHOR that fact
 * and read it back with the store forced ON — the established pattern on this line. Second, every row
 * is SYNCHRONOUS: where a row needs the cart to run, it calls the cart's own tick directly rather
 * than waiting on server ticks, so the store window never spans a tick boundary and the row is
 * deterministic. Driving the tick by hand exercises the real path — the seat rebind and the real rail
 * solver both run — but it does NOT exercise entity scheduling, and the ride feel itself stays a live
 * observation.
 */
public final class MinecartRailSeatDepthTest {

    private static final double EPS = 1.0e-6d;
    /** Vanilla's on-rail lift: the cart rides this far above its rail cell's floor. */
    private static final double RAIL_LIFT = 0.0625d;
    private static final double LOWERED = -0.5d;

    private interface FrozenBody {
        void run();
    }

    /** Reads and exercises with the store ON, the way the shipped jar is configured. */
    private static void withFrozen(FrozenBody body) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            body.run();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    private static BlockState eastWestRail() {
        return Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST);
    }

    /**
     * A straight east-west rail run on solid ground. {@code dy} is written on each RAIL cell — the
     * rail is the cell whose recorded height a cart's seat comes from — AND on the support beneath
     * it: in play a lowered rail rides a lowered support, and a rail lowered over a flush block
     * would seat the cart inside the stone, so a momentum row would then measure a collision, not
     * the seat. A dy of 0 authors nothing, so the run reads flush and is the control.
     *
     * @return the ABSOLUTE position of the westmost rail
     */
    private static BlockPos railRun(GameTestHelper helper, BlockPos startRel, int length, double dy) {
        ServerLevel level = helper.getLevel();
        BlockPos first = null;
        for (int i = 0; i < length; i++) {
            BlockPos rel = startRel.east(i);
            helper.setBlock(rel.below(), Blocks.STONE.defaultBlockState());
            helper.setBlock(rel, eastWestRail());
            BlockPos abs = helper.absolutePos(rel);
            if (dy != 0.0d) {
                SlabAnchorAttachment.writePlacementDy(level, abs.below(), dy);
                SlabAnchorAttachment.writePlacementDy(level, abs, dy);
            }
            if (first == null) {
                first = abs;
            }
        }
        return first;
    }

    /** A cart placed exactly the way the item places one: on the rail, at vanilla's on-rail lift. */
    private static AbstractMinecart spawnCartOn(GameTestHelper helper, BlockPos railAbs) {
        ServerLevel level = helper.getLevel();
        AbstractMinecart cart = AbstractMinecart.createMinecart(
                level,
                railAbs.getX() + 0.5d, railAbs.getY() + RAIL_LIFT, railAbs.getZ() + 0.5d,
                EntityTypes.MINECART, EntitySpawnReason.SPAWN_ITEM_USE, ItemStack.EMPTY, null);
        if (cart == null) {
            throw new IllegalStateException("premise: could not create a minecart");
        }
        level.addFreshEntity(cart);
        return cart;
    }

    private static double seatOf(AbstractMinecart cart) {
        return ((RailSeatDyHolder) cart).slabbed$railSeatDy();
    }

    private static void requireOldBehaviour(GameTestHelper helper, AbstractMinecart cart) {
        if (!(cart.getBehavior() instanceof OldMinecartBehavior)) {
            throw helper.assertionException("premise: this row exercises the DEFAULT rail solver, but "
                    + "the cart is using " + cart.getBehavior().getClass().getSimpleName()
                    + " — the minecart_improvements experiment is on and the row is measuring a path "
                    + "it was not written for");
        }
    }

    /**
     * A cart spawned on a lowered rail sits, is hittable, and carries its rider at the DRAWN rail
     * top. Asserted synchronously so only the spawn seat is under test.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aCartSpawnedOnALoweredRailSitsRidesAndCollidesAtTheDrawnRailTop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        withFrozen(() -> {
            BlockPos loweredRail = railRun(helper, new BlockPos(1, 3, 2), 3, LOWERED);
            BlockPos flushRail = railRun(helper, new BlockPos(1, 3, 5), 3, 0.0d);

            AbstractMinecart lowered = spawnCartOn(helper, loweredRail);
            AbstractMinecart control = spawnCartOn(helper, flushRail);

            double gridSeat = flushRail.getY() + RAIL_LIFT;
            double drawnSeat = loweredRail.getY() + RAIL_LIFT + LOWERED;

            if (Math.abs(control.getY() - gridSeat) > EPS) {
                throw helper.assertionException("control: a cart on a flush rail must sit at the rail "
                        + "cell's on-rail height " + gridSeat + ", got " + control.getY());
            }
            if (Math.abs(lowered.getY() - drawnSeat) > EPS) {
                throw helper.assertionException("a cart spawned on a lowered rail must sit on the DRAWN "
                        + "rail top " + drawnSeat + ", got " + lowered.getY()
                        + " (the cart is being painted lower than it really is)");
            }
            if (Math.abs(seatOf(lowered) - LOWERED) > EPS || seatOf(control) != 0.0d) {
                throw helper.assertionException("the bound seat must be " + LOWERED + " on the lowered run "
                        + "and 0.0 on the control; got " + seatOf(lowered) + " / " + seatOf(control));
            }

            // The REAL box follows the position, and it is the control's box moved down by the seat.
            AABB loweredBox = lowered.getBoundingBox();
            AABB controlBox = control.getBoundingBox();
            if (Math.abs(loweredBox.minY - lowered.getY()) > EPS) {
                throw helper.assertionException("a cart's collision box must start at its own position; "
                        + "box minY " + loweredBox.minY + " vs position " + lowered.getY());
            }
            if (Math.abs((controlBox.minY - loweredBox.minY) - (-LOWERED)) > EPS
                    || Math.abs((controlBox.maxY - loweredBox.maxY) - (-LOWERED)) > EPS) {
                throw helper.assertionException("the lowered cart's box must be the control's box moved "
                        + "down by " + LOWERED + "; minY delta " + (controlBox.minY - loweredBox.minY)
                        + " maxY delta " + (controlBox.maxY - loweredBox.maxY));
            }

            // AIM: something aimed at the drawn seat finds the cart, and the band just above the
            // drawn body — where a grid-seated cart would still be solid — finds nothing.
            AABB atDrawnBody = thinBandAt(lowered, loweredBox.minY + 0.05d, loweredBox.minY + 0.15d);
            AABB aboveDrawnBody = thinBandAt(lowered, loweredBox.maxY + 0.35d, loweredBox.maxY + 0.45d);
            if (!containsCart(level, atDrawnBody, lowered)) {
                throw helper.assertionException("a hit aimed at the cart's drawn body must find it");
            }
            if (containsCart(level, aboveDrawnBody, lowered)) {
                throw helper.assertionException("nothing must be solid above the cart's drawn body — "
                        + "the cart is still occupying the grid band it is not drawn in");
            }

            // The rider follows the cart: the attachment is a LOCAL offset, so the delta is identical
            // and the absolute seat is exactly the seat lower.
            Entity riderProbe = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
            if (riderProbe == null) {
                throw helper.assertionException("premise: could not create the rider probe");
            }
            double loweredRiderDelta = lowered.getPassengerRidingPosition(riderProbe).y - lowered.getY();
            double controlRiderDelta = control.getPassengerRidingPosition(riderProbe).y - control.getY();
            if (Math.abs(loweredRiderDelta - controlRiderDelta) > EPS) {
                throw helper.assertionException("a rider's offset above its cart must be untouched; "
                        + "lowered " + loweredRiderDelta + " vs control " + controlRiderDelta);
            }
            double riderDrop = control.getPassengerRidingPosition(riderProbe).y
                    - lowered.getPassengerRidingPosition(riderProbe).y;
            if (Math.abs(riderDrop - (-LOWERED)) > EPS) {
                throw helper.assertionException("a rider on the lowered run must sit exactly "
                        + (-LOWERED) + " lower than the control's rider; got " + riderDrop);
            }
        });
        helper.succeed();
    }

    /** A cart driven along a uniformly lowered run keeps its drawn seat and keeps reading the RAIL. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aCartRunningAlongALoweredRailKeepsItsDrawnSeat(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos firstRail = railRun(helper, new BlockPos(1, 3, 2), 5, LOWERED);
            AbstractMinecart cart = spawnCartOn(helper, firstRail);
            requireOldBehaviour(helper, cart);

            double drawnSeat = firstRail.getY() + RAIL_LIFT + LOWERED;
            double previousX = cart.getX();
            for (int tick = 0; tick < 12; tick++) {
                // Re-apply the push each tick so the row measures the SEAT, not vanilla's slowdown.
                cart.setDeltaMovement(new Vec3(0.2d, 0.0d, 0.0d));
                cart.tick();

                if (Math.abs(cart.getY() - drawnSeat) > EPS) {
                    throw helper.assertionException("tick " + tick + ": a cart running on a lowered rail "
                            + "must stay on its drawn seat " + drawnSeat + ", got " + cart.getY()
                            + " (the rail solver is computing in the wrong frame)");
                }
                if (!(cart.getX() > previousX + EPS)) {
                    throw helper.assertionException("tick " + tick + ": the cart must keep moving east; "
                            + "x went " + previousX + " -> " + cart.getX());
                }
                previousX = cart.getX();
                if (cart.getCurrentBlockPosOrRailBelow().getY() != firstRail.getY()) {
                    throw helper.assertionException("tick " + tick + ": the cart must still resolve the "
                            + "RAIL cell (y=" + firstRail.getY() + "), got "
                            + cart.getCurrentBlockPosOrRailBelow().getY() + " — it is reading the support");
                }
                if (cart.getX() > firstRail.getX() + 4.5d) {
                    throw helper.assertionException("premise: the cart left the five-rail run at tick "
                            + tick + "; the row measured less than it claims");
                }
            }
        });
        helper.succeed();
    }

    /**
     * Crossing a lowered/flush seam re-binds the seat in BOTH directions, holding the logical height
     * fixed so the cart steps onto the rail it arrives at.
     *
     * <p>The cart is moved horizontally by hand rather than by momentum: what is under test is the
     * seat change at the seam, and a momentum-driven crossing would also depend on how far a cart
     * travels per tick.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aCartCrossingBetweenLoweredAndFlushRailRebindsItsSeat(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos loweredStart = railRun(helper, new BlockPos(1, 3, 2), 2, LOWERED);
            BlockPos flushStart = railRun(helper, new BlockPos(3, 3, 2), 2, 0.0d);

            AbstractMinecart cart = spawnCartOn(helper, loweredStart);
            requireOldBehaviour(helper, cart);

            double gridSeat = loweredStart.getY() + RAIL_LIFT;
            double drawnSeat = gridSeat + LOWERED;
            if (Math.abs(cart.getY() - drawnSeat) > EPS) {
                throw helper.assertionException("premise: the cart must start on the lowered run's drawn "
                        + "seat " + drawnSeat + ", got " + cart.getY());
            }

            // Outbound: step onto the flush run. The seat clears and the cart rises to grid height.
            cart.setPos(flushStart.getX() + 0.5d, cart.getY(), cart.getZ());
            cart.tick();
            if (seatOf(cart) != 0.0d || Math.abs(cart.getY() - gridSeat) > EPS) {
                throw helper.assertionException("crossing onto a flush rail must clear the seat and step "
                        + "the cart up to " + gridSeat + "; seat " + seatOf(cart) + " y " + cart.getY()
                        + " (the seat number tracked the rail while the cart's real height did not)");
            }

            // Return: step back onto the lowered run. The seat binds and the cart drops to the drawn top.
            cart.setPos(loweredStart.getX() + 0.5d, cart.getY(), cart.getZ());
            cart.tick();
            if (Math.abs(seatOf(cart) - LOWERED) > EPS || Math.abs(cart.getY() - drawnSeat) > EPS) {
                throw helper.assertionException("crossing back onto a lowered rail must re-bind the seat "
                        + "and drop the cart to " + drawnSeat + "; seat " + seatOf(cart)
                        + " y " + cart.getY());
            }
        });
        helper.succeed();
    }

    /**
     * A saved and reloaded cart keeps its seat NUMBER, and therefore still resolves its RAIL cell
     * rather than the support under it. The seat is bound here by the per-tick rebind, deliberately
     * not by the spawn hook, so the row stands on its own.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aReloadedCartKeepsItsSeatAndStillReadsTheRailCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        withFrozen(() -> {
            BlockPos rail = railRun(helper, new BlockPos(2, 3, 2), 1, LOWERED);
            AbstractMinecart original = spawnCartOn(helper, rail);
            original.setDeltaMovement(Vec3.ZERO);
            original.tick();
            if (Math.abs(seatOf(original) - LOWERED) > EPS) {
                throw helper.assertionException("premise: the cart must be seated before the round trip; "
                        + "seat " + seatOf(original));
            }

            TagValueOutput output =
                    TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            original.saveWithoutId(output);
            CompoundTag saved = output.buildResult();

            AbstractMinecart reloaded = EntityTypes.MINECART.create(level, EntitySpawnReason.LOAD);
            if (reloaded == null) {
                throw helper.assertionException("premise: could not create the reloaded cart");
            }
            ValueInput input =
                    TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved);
            reloaded.load(input);
            level.addFreshEntity(reloaded);

            if (Double.doubleToRawLongBits(seatOf(reloaded))
                    != Double.doubleToRawLongBits(seatOf(original))) {
                throw helper.assertionException("a reloaded cart must keep its seat exactly; original "
                        + seatOf(original) + " reloaded " + seatOf(reloaded));
            }
            if (Math.abs(reloaded.getY() - original.getY()) > EPS) {
                throw helper.assertionException("a reloaded cart's saved position is already physical; "
                        + "original y " + original.getY() + " reloaded " + reloaded.getY());
            }
            BlockPos resolved = reloaded.getCurrentBlockPosOrRailBelow();
            if (!resolved.equals(rail)) {
                throw helper.assertionException("a reloaded cart must still resolve its RAIL cell " + rail
                        + ", got " + resolved + " — without the restored seat it reads the support below");
            }
        });
        helper.succeed();
    }

    /**
     * The render-side rail resolution works only through the frame conversion.
     *
     * <p>The renderer does its OWN rail-cell derivation from the cart's physical position, so for a
     * seated cart the raw call answers null and the rail snap and slope tilt vanish. This row asserts
     * that defect directly as its premise, then pins that the conversion resolves it.
     *
     * <p>HONEST BOUNDARY: this proves the CONVERSION, not the WIRING. That the renderer actually
     * calls it is proved by {@code MinecartRenderRailSnapClientGameTest} and by live observation.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void theRenderRailSnapResolvesWhereRawVanillaReturnsNull(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos rail = railRun(helper, new BlockPos(2, 3, 2), 3, LOWERED);
            AbstractMinecart cart = spawnCartOn(helper, rail.east());
            requireOldBehaviour(helper, cart);
            OldMinecartBehavior behavior = (OldMinecartBehavior) cart.getBehavior();

            double drawnSeat = rail.getY() + RAIL_LIFT + LOWERED;
            if (Math.abs(cart.getY() - drawnSeat) > EPS) {
                throw helper.assertionException("premise: the cart must be seated at " + drawnSeat
                        + ", got " + cart.getY());
            }

            // THE DEFECT, asserted directly.
            if (behavior.getPos(cart.getX(), cart.getY(), cart.getZ()) != null) {
                throw helper.assertionException("premise: the raw rail resolution is expected to FAIL for "
                        + "a seated cart's physical position — if it now succeeds, the renderer no longer "
                        + "needs the conversion and this row is measuring nothing");
            }

            double logicalY = MinecartRailFrame.toLogicalY(cart, cart.getY());
            Vec3 snapped = MinecartRailFrame.toPhysical(
                    cart, behavior.getPos(cart.getX(), logicalY, cart.getZ()));
            if (snapped == null) {
                throw helper.assertionException("the converted rail resolution must find the rail");
            }
            if (Math.abs(snapped.y - drawnSeat) > EPS) {
                throw helper.assertionException("the snapped rail position must be the drawn seat "
                        + drawnSeat + ", got " + snapped.y);
            }
            Vec3 front = MinecartRailFrame.toPhysical(
                    cart, behavior.getPosOffs(cart.getX(), logicalY, cart.getZ(), 0.3d));
            Vec3 back = MinecartRailFrame.toPhysical(
                    cart, behavior.getPosOffs(cart.getX(), logicalY, cart.getZ(), -0.3d));
            if (front == null || back == null) {
                throw helper.assertionException("both slope probes must resolve; front " + front
                        + " back " + back + " (a null probe is what flattens the cart on slopes)");
            }
            if (Math.abs((front.y + back.y) / 2.0d - drawnSeat) > EPS) {
                throw helper.assertionException("on a flat run the slope probes must average to the drawn "
                        + "seat " + drawnSeat + ", got " + ((front.y + back.y) / 2.0d));
            }
        });
        helper.succeed();
    }

    /**
     * The experimental rail solver computes in the same logical frame.
     *
     * <p>WHAT THIS ROW IS AND IS NOT. The behaviour a cart uses is fixed by the world's feature flags
     * when the cart is constructed, and this suite's world does not enable minecart_improvements — so
     * there is no way to get a cart that USES this solver here. The row instead drives the real solver
     * directly on a real cart over a real rail, and compares a seated cart against a flush control
     * given the same LOGICAL input. It proves the frame split; it does not prove the experiment's own
     * dispatch, which stays a live observation.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void theExperimentalRailSolverAlsoComputesInTheLogicalFrame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        withFrozen(() -> {
            BlockPos loweredRail = railRun(helper, new BlockPos(1, 3, 2), 3, LOWERED);
            BlockPos flushRail = railRun(helper, new BlockPos(1, 3, 5), 3, 0.0d);

            AbstractMinecart lowered = spawnCartOn(helper, loweredRail.east());
            AbstractMinecart control = spawnCartOn(helper, flushRail.east());

            BlockPos loweredCell = loweredRail.east();
            BlockPos flushCell = flushRail.east();
            double logicalStart = loweredCell.getY() + RAIL_LIFT;
            if (Math.abs(MinecartRailFrame.toLogicalY(lowered, lowered.getY()) - logicalStart) > EPS
                    || Math.abs(control.getY() - logicalStart) > EPS) {
                throw helper.assertionException("premise: both carts must start from the same LOGICAL "
                        + "height; lowered " + MinecartRailFrame.toLogicalY(lowered, lowered.getY())
                        + " control " + control.getY());
            }

            double controlBefore = control.getY();
            NewMinecartBehavior loweredSolver = new NewMinecartBehavior(lowered);
            NewMinecartBehavior controlSolver = new NewMinecartBehavior(control);
            loweredSolver.adjustToRails(loweredCell, level.getBlockState(loweredCell), true);
            controlSolver.adjustToRails(flushCell, level.getBlockState(flushCell), true);

            // The solver snaps a cart to its rail cell's floor, so the control MUST have moved off the
            // on-rail lift. Without this, a solver that wrote nothing would leave the two carts at
            // their starting offset and the row would pass having measured nothing.
            if (Math.abs(control.getY() - controlBefore) < EPS) {
                throw helper.assertionException("premise: the solver did not move the control cart at all "
                        + "(still " + controlBefore + ") — this row would be measuring the fixture");
            }

            double drop = control.getY() - lowered.getY();
            if (Math.abs(drop - (-LOWERED)) > EPS) {
                throw helper.assertionException("the experimental solver must leave a seated cart exactly "
                        + (-LOWERED) + " below the flush control it started level with; got " + drop
                        + " (lowered y " + lowered.getY() + " control y " + control.getY() + ")");
            }
        });
        helper.succeed();
    }

    /**
     * A cart positioned on a WORKER thread — the way mineshaft generation places its chest carts —
     * must not read the world from there: that read is answered by the server thread, which during
     * generation may be waiting on this very worker, and the two wait on each other forever. The row
     * holds the server thread while a worker positions a cart over a lowered rail, so a synchronous
     * world read from the worker times out deterministically. The seat is then bound by the first
     * server tick, exactly as the spawn hook would have bound it.
     *
     * <p>MUTATION that must redden this row alone: remove the {@code isSameThread} early return from
     * {@code MinecartRailSeatMixin.slabbed$seatOnSpawn}.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aCartPositionedOnAWorkerThreadNeverWaitsForTheServer(GameTestHelper helper) throws InterruptedException {
        ServerLevel level = helper.getLevel();
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            BlockPos rail = railRun(helper, new BlockPos(1, 3, 2), 3, LOWERED);
            double gridSeat = rail.getY() + RAIL_LIFT;
            java.util.concurrent.atomic.AtomicReference<AbstractMinecart> cart = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            Thread worker = new Thread(() -> {
                try {
                    AbstractMinecart created = EntityTypes.CHEST_MINECART.create(level, EntitySpawnReason.STRUCTURE);
                    if (created == null) {
                        throw new IllegalStateException("premise: could not create a chest minecart");
                    }
                    created.setInitialPos(rail.getX() + 0.5d, gridSeat, rail.getZ() + 0.5d);
                    cart.set(created);
                } catch (Throwable failure) {
                    error.set(failure);
                } finally {
                    done.countDown();
                }
            }, "slabbed-minecart-worker-row");
            worker.setDaemon(true);
            worker.start();
            // This thread IS the server thread; blocking it here is what turns a cross-thread world
            // read into a bounded, observable wait instead of a live-game deadlock.
            if (!done.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                throw helper.assertionException("BACKGROUND_MINECART_POSITION_RED: positioning a cart on a "
                        + "worker thread waited for the server thread (a world read leaked off-thread)");
            }
            if (error.get() != null || cart.get() == null) {
                throw helper.assertionException("worker-thread cart positioning failed: " + error.get());
            }
            AbstractMinecart created = cart.get();
            if (Math.abs(created.getY() - gridSeat) > EPS || seatOf(created) != 0.0d) {
                throw helper.assertionException("a cart positioned off-thread must keep vanilla's grid "
                        + "position and an unbound seat until the server tick; got y=" + created.getY()
                        + " seat=" + seatOf(created));
            }
            level.addFreshEntity(created);
            created.tick();
            double drawnSeat = gridSeat + LOWERED;
            if (Math.abs(seatOf(created) - LOWERED) > EPS || Math.abs(created.getY() - drawnSeat) > EPS) {
                throw helper.assertionException("the first server tick must bind the deferred lowered seat: "
                        + "expected seat " + LOWERED + " at y=" + drawnSeat + ", got seat " + seatOf(created)
                        + " at y=" + created.getY());
            }
            System.out.println("[MINECART_WORKER_ROW] deferred seat bound on the first server tick: y=" + created.getY());
            helper.succeed();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    private static AABB thinBandAt(AbstractMinecart cart, double minY, double maxY) {
        return new AABB(cart.getX() - 0.1d, minY, cart.getZ() - 0.1d,
                cart.getX() + 0.1d, maxY, cart.getZ() + 0.1d);
    }

    private static boolean containsCart(ServerLevel level, AABB probe, AbstractMinecart cart) {
        List<AbstractMinecart> found = level.getEntitiesOfClass(AbstractMinecart.class, probe);
        return found.contains(cart);
    }
}
