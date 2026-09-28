package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.RailSlopeProfile;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;

/**
 * A straight rail's drawn slope fits the seat of the rail it connects to (maintainer ruling,
 * 2026-09-28) — {@link RailSlopeProfile}, the pure rule the client shear and the outline box are
 * both computed from.
 *
 * <p>Every row builds its scene with {@code setBlock} so vanilla's own rail-shape update decides
 * each rail's shape exactly as in play, then AUTHORS the seats and reads with the store ON (this
 * venue runs it off). Each row checks the shapes it relies on as a premise, so a scene vanilla
 * shaped differently fails loudly instead of proving the wrong thing.
 *
 * <p>MUTATIONS that must redden this class: make {@code flatLift} ignore the neighbour's seat
 * (rows 1, 9, 13 and the outline row), make {@code refitRamp} return 1.0 unconditionally (rows 2, 3,
 * 8 and the ramp outline row), drop the dip guard (row 5), drop the flat-reach test (row 7), let a
 * ramp's flat end lift (row 10), or withhold {@code BaseRailBlockSlopeShapeMixin} (outline rows).
 */
public final class RailSlopeProfileTest {

    private static final double EPS = 1.0e-6d;
    private static final double LOWERED = -0.5d;
    /** Vanilla's outline boxes: flat 2/16, full ramp 8/16 — the two anchors the fitted box interpolates. */
    private static final double FLAT_BOX = 2.0d / 16.0d;
    private static final double RAMP_BOX = 8.0d / 16.0d;

    private static final BlockPos A = new BlockPos(2, 2, 2);

    private interface FrozenBody {
        void run();
    }

    /** Reads with the store ON, the way the shipped jar is configured. */
    private static void withFrozen(FrozenBody body) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            body.run();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    /**
     * A rail on a stone support at {@code rel}, seated at {@code dy} (0 authors nothing: the control).
     * The seat is written on the rail AND its support, as a lowered rail rides a lowered support in
     * play. Vanilla's shape update runs on placement, so the resulting shape is vanilla's decision.
     *
     * @return the ABSOLUTE rail position
     */
    private static BlockPos rail(GameTestHelper helper, BlockPos rel, double dy) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(rel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(rel, Blocks.RAIL.defaultBlockState());
        BlockPos abs = helper.absolutePos(rel);
        if (dy != 0.0d) {
            SlabAnchorAttachment.writePlacementDy(level, abs.below(), dy);
            SlabAnchorAttachment.writePlacementDy(level, abs, dy);
        }
        return abs;
    }

    private static RailShape shapeAt(GameTestHelper helper, BlockPos abs) {
        BlockState state = helper.getLevel().getBlockState(abs);
        if (!(state.getBlock() instanceof RailBlock)) {
            throw helper.assertionException("premise: expected a rail at " + abs + ", found " + state);
        }
        return state.getValue(RailBlock.SHAPE);
    }

    private static void requireShape(GameTestHelper helper, BlockPos abs, RailShape expected) {
        RailShape actual = shapeAt(helper, abs);
        if (actual != expected) {
            throw helper.assertionException("premise: vanilla shaped the rail at " + abs + " as "
                    + actual + ", this row needs " + expected);
        }
    }

    private static RailSlopeProfile.Profile profileAt(GameTestHelper helper, BlockPos abs) {
        ServerLevel level = helper.getLevel();
        return RailSlopeProfile.resolve(level, abs, level.getBlockState(abs));
    }

    private static void expectProfile(GameTestHelper helper, BlockPos abs, Direction.Axis axis,
                                      double negativeEnd, double positiveEnd,
                                      double vanillaNegative, double vanillaPositive) {
        RailSlopeProfile.Profile profile = profileAt(helper, abs);
        if (profile == null) {
            throw helper.assertionException("the rail at " + abs + " resolved no profile");
        }
        if (profile.axis() != axis
                || Math.abs(profile.negativeEnd() - negativeEnd) > EPS
                || Math.abs(profile.positiveEnd() - positiveEnd) > EPS
                || Math.abs(profile.vanillaNegativeEnd() - vanillaNegative) > EPS
                || Math.abs(profile.vanillaPositiveEnd() - vanillaPositive) > EPS) {
            throw helper.assertionException("the rail at " + abs + " must draw ends (" + negativeEnd
                    + ", " + positiveEnd + ") over vanilla (" + vanillaNegative + ", " + vanillaPositive
                    + ") on axis " + axis + ", got " + profile);
        }
    }

    private static void expectVanilla(GameTestHelper helper, BlockPos abs) {
        RailSlopeProfile.Profile profile = profileAt(helper, abs);
        if (profile == null || !profile.isVanilla()) {
            throw helper.assertionException("the rail at " + abs + " must keep vanilla geometry, got "
                    + profile);
        }
    }

    private static AABB outlineAt(GameTestHelper helper, BlockPos abs) {
        ServerLevel level = helper.getLevel();
        return level.getBlockState(abs).getShape(level, abs).bounds();
    }

    private static void expectOutlineY(GameTestHelper helper, BlockPos abs, double minY, double maxY) {
        AABB box = outlineAt(helper, abs);
        if (Math.abs(box.minY - minY) > EPS || Math.abs(box.maxY - maxY) > EPS) {
            throw helper.assertionException("the outline of the rail at " + abs + " must span Y "
                    + minY + ".." + maxY + " (cell-relative), got " + box.minY + ".." + box.maxY);
        }
    }

    // ── row 1: a flat rail lifts toward a higher neighbour; the higher one stays ─────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatRailLiftsTowardAHigherNeighbour(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos a = rail(helper, A, LOWERED);
            BlockPos b = rail(helper, A.south(), 0.0d);
            requireShape(helper, a, RailShape.NORTH_SOUTH);
            requireShape(helper, b, RailShape.NORTH_SOUTH);
            expectProfile(helper, a, Direction.Axis.Z, 0.0d, 0.5d, 0.0d, 0.0d);
            expectVanilla(helper, b);
            helper.succeed();
        });
    }

    // ── row 2: a ramp climbing onto a lowered rail is refitted shorter ───────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rampRefitsShorterOntoALoweredRail(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos b = rail(helper, A.south().above(), LOWERED);
            BlockPos a = rail(helper, A, 0.0d);
            requireShape(helper, a, RailShape.ASCENDING_SOUTH);
            requireShape(helper, b, RailShape.NORTH_SOUTH);
            expectProfile(helper, a, Direction.Axis.Z, 0.0d, 0.5d, 0.0d, 1.0d);
            expectVanilla(helper, b);
            helper.succeed();
        });
    }

    // ── row 3: a lowered ramp climbing onto a flush rail is refitted longer ─────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rampRefitsLongerOntoAFlushRail(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos b = rail(helper, A.south().above(), 0.0d);
            BlockPos a = rail(helper, A, LOWERED);
            requireShape(helper, a, RailShape.ASCENDING_SOUTH);
            expectProfile(helper, a, Direction.Axis.Z, 0.0d, 1.5d, 0.0d, 1.0d);
            expectVanilla(helper, b);
            helper.succeed();
        });
    }

    // ── row 4: a ramp that already meets its neighbour is left exactly vanilla ──────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rampMeetingItsNeighbourStaysVanilla(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos b = rail(helper, A.south().above(), LOWERED);
            BlockPos a = rail(helper, A, LOWERED);
            requireShape(helper, a, RailShape.ASCENDING_SOUTH);
            expectProfile(helper, a, Direction.Axis.Z, 0.0d, 1.0d, 0.0d, 1.0d);
            expectVanilla(helper, a);
            expectVanilla(helper, b);
            helper.succeed();
        });
    }

    // ── row 5: a rail in a dip keeps both ends on its support ───────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aRailInADipKeepsBothEndsOnItsSupport(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos a = rail(helper, A, LOWERED);
            BlockPos n = rail(helper, A.north(), 0.0d);
            BlockPos s = rail(helper, A.south(), 0.0d);
            requireShape(helper, a, RailShape.NORTH_SOUTH);
            expectProfile(helper, a, Direction.Axis.Z, 0.0d, 0.0d, 0.0d, 0.0d);
            expectVanilla(helper, n);
            expectVanilla(helper, s);
            helper.succeed();
        });
    }

    // ── row 6: a curve keeps vanilla geometry, box included ──────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aCurveKeepsVanillaGeometry(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos a = rail(helper, A, LOWERED);
            rail(helper, A.south(), 0.0d);
            rail(helper, A.east(), 0.0d);
            requireShape(helper, a, RailShape.SOUTH_EAST);
            if (profileAt(helper, a) != null) {
                throw helper.assertionException("a curve must resolve no profile, got " + profileAt(helper, a));
            }
            // Vanilla's flat box, moved by the seat and nothing else.
            expectOutlineY(helper, a, LOWERED, LOWERED + FLAT_BOX);
            helper.succeed();
        });
    }

    // ── row 7: a neighbour whose shape does not reach back is not a seam ─────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anUnconnectedNeighbourIsIgnored(GameTestHelper helper) {
        withFrozen(() -> {
            // An east-west run south of A: its middle rail touches A's cell but reaches east and west.
            rail(helper, A.south().west(), 0.0d);
            rail(helper, A.south().east(), 0.0d);
            BlockPos b = rail(helper, A.south(), 0.0d);
            BlockPos a = rail(helper, A, LOWERED);
            requireShape(helper, b, RailShape.EAST_WEST);
            requireShape(helper, a, RailShape.NORTH_SOUTH);
            expectVanilla(helper, a);
            helper.succeed();
        });
    }

    // ── row 8: a lower ramp meets a lowered rail from its own side ───────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aLowerRampRefitsFromItsOwnSide(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos a = rail(helper, A.above(), LOWERED);
            BlockPos b = rail(helper, A.south(), 0.0d);
            requireShape(helper, a, RailShape.NORTH_SOUTH);
            requireShape(helper, b, RailShape.ASCENDING_NORTH);
            // A's south end stays on its support: the ramp below it does the meeting.
            expectVanilla(helper, a);
            expectProfile(helper, b, Direction.Axis.Z, 0.5d, 0.0d, 1.0d, 0.0d);
            helper.succeed();
        });
    }

    // ── row 9: deeper seats follow the same rule ─────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deeperSeatsFollowTheSameRule(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos a = rail(helper, A, -1.0d);
            BlockPos b = rail(helper, A.south(), 0.0d);
            requireShape(helper, a, RailShape.NORTH_SOUTH);
            expectProfile(helper, a, Direction.Axis.Z, 0.0d, 1.0d, 0.0d, 0.0d);
            expectVanilla(helper, b);
            // A full-block lift on a flat rail gets exactly vanilla's ramp box, moved by the seat.
            expectOutlineY(helper, a, -1.0d, -1.0d + RAMP_BOX);
            helper.succeed();
        });
    }

    // ── row 10: a ramp's flat end never lifts, even toward a higher neighbour ────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aRampsFlatEndNeverLifts(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos b = rail(helper, A.south().above(), LOWERED);
            BlockPos c = rail(helper, A.north(), 0.0d);
            BlockPos a = rail(helper, A, LOWERED);
            requireShape(helper, a, RailShape.ASCENDING_SOUTH);
            requireShape(helper, c, RailShape.NORTH_SOUTH);
            expectProfile(helper, a, Direction.Axis.Z, 0.0d, 1.0d, 0.0d, 1.0d);
            expectVanilla(helper, b);
            helper.succeed();
        });
    }

    // ── row 11: the outline box follows the profile ──────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void theOutlineFollowsTheProfile(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos a = rail(helper, A, LOWERED);
            BlockPos b = rail(helper, A.south(), 0.0d);
            requireShape(helper, a, RailShape.NORTH_SOUTH);
            // Half a lift: halfway between vanilla's flat and ramp boxes, moved by the seat.
            expectOutlineY(helper, a, LOWERED, LOWERED + FLAT_BOX + (RAMP_BOX - FLAT_BOX) * 0.5d);
            // The flush neighbour keeps vanilla's flat box.
            expectOutlineY(helper, b, 0.0d, FLAT_BOX);
            helper.succeed();
        });
    }

    // ── row 12: a refitted ramp's box follows too ────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aRefittedRampsOutlineFollowsToo(GameTestHelper helper) {
        withFrozen(() -> {
            rail(helper, A.south().above(), 0.0d);
            BlockPos a = rail(helper, A, LOWERED);
            requireShape(helper, a, RailShape.ASCENDING_SOUTH);
            expectOutlineY(helper, a, LOWERED, LOWERED + FLAT_BOX + (RAMP_BOX - FLAT_BOX) * 1.5d);
            helper.succeed();
        });
    }

    // ── row 13: the east-west axis mirrors north-south, dip included ─────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void eastWestAxisMirrorsNorthSouth(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos a = rail(helper, A, LOWERED);
            BlockPos b = rail(helper, A.east(), 0.0d);
            requireShape(helper, a, RailShape.EAST_WEST);
            requireShape(helper, b, RailShape.EAST_WEST);
            expectProfile(helper, a, Direction.Axis.X, 0.0d, 0.5d, 0.0d, 0.0d);
            rail(helper, A.west(), 0.0d);
            requireShape(helper, a, RailShape.EAST_WEST);
            expectProfile(helper, a, Direction.Axis.X, 0.0d, 0.0d, 0.0d, 0.0d);
            helper.succeed();
        });
    }

    // ── row 14 (LAW.md pin): the slope follows the neighbour, the seat never does ────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aNeighbourEditNeverMovesTheSeat(GameTestHelper helper) {
        withFrozen(() -> {
            ServerLevel level = helper.getLevel();
            BlockPos a = rail(helper, A, LOWERED);
            double before = SlabSupport.getYOffset(level, a, level.getBlockState(a));
            if (Math.abs(before - LOWERED) > EPS) {
                throw helper.assertionException("premise: the rail must read its authored seat " + LOWERED
                        + ", got " + before);
            }
            BlockPos b = rail(helper, A.south(), 0.0d);
            expectProfile(helper, a, Direction.Axis.Z, 0.0d, 0.5d, 0.0d, 0.0d);
            double withNeighbour = SlabSupport.getYOffset(level, a, level.getBlockState(a));
            helper.setBlock(A.south(), Blocks.AIR.defaultBlockState());
            expectVanilla(helper, a);
            double afterRemoval = SlabSupport.getYOffset(level, a, level.getBlockState(a));
            if (Math.abs(withNeighbour - LOWERED) > EPS || Math.abs(afterRemoval - LOWERED) > EPS) {
                throw helper.assertionException("LAW.md: a neighbour edit moved the rail's seat: "
                        + before + " -> " + withNeighbour + " -> " + afterRemoval + " (b was " + b + ")");
            }
            helper.succeed();
        });
    }
}
