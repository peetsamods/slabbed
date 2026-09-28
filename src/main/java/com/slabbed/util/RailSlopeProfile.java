package com.slabbed.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The drawn profile of a straight rail: how high each of its two ends is, in blocks, above the
 * rail's own seated base (maintainer ruling, 2026-09-28).
 *
 * <p>Vanilla picks a rail's slope from its GRID neighbours alone: a flat plane, or a full-block ramp
 * toward the rail one cell up. Under Slabbed a rail also carries a stored seat, so two connected
 * rails can sit at different drawn heights inside the same grid relation — a lowered rail beside a
 * flush one, a ramp climbing onto a lowered rail. Vanilla's plane and ramp then do not meet. This
 * class fits the rail's profile to the neighbour it actually connects to, so the drawn rails join.
 *
 * <p>INVARIANT (LAW.md): this is a READ of stored seats, never a write, and never a re-derivation of
 * any rail's own height. A rail's seat is its placed height and stays; only the SLOPE drawn between
 * two seats follows the neighbour — exactly as vanilla's own {@link RailShape} already follows the
 * neighbour under LAW 1's "genuine vanilla mechanism" clause.
 *
 * <p>THE RULE. For each end of a straight rail, find the rail vanilla connects it to, then:
 * <ul>
 *   <li>A vanilla RAMP is refitted to meet the rail it climbs to, shorter or longer as needed. Its
 *       other end stays on the support.</li>
 *   <li>A FLAT end lifts toward a connected neighbour at the same grid level that is drawn higher.
 *       A neighbour drawn lower lifts from its own side, so every seam is closed exactly once.</li>
 *   <li>A rail keeps at least one end on its support: if both flat ends would lift (a dip), neither
 *       does — the two seams stay as steps, the way vanilla leaves one seam of a grid dip unjoined.</li>
 *   <li>Curves keep vanilla geometry; a curve cannot slope in vanilla either.</li>
 * </ul>
 *
 * <p>Pure and server-loadable: the client shears the rail's quads to this profile and the shared
 * outline mixin sizes the rail's box from it, so the drawn rail, its outline and its raycast box
 * can never disagree about the slope.
 */
public final class RailSlopeProfile {

    private static final double EPS = 1.0e-6d;
    /** Vanilla's flat rail box height (2/16) and its ramp box height (8/16, for a 1.0 rise). */
    private static final double FLAT_BOX_TOP = 2.0d / 16.0d;
    private static final double RAMP_BOX_TOP = 8.0d / 16.0d;

    private RailSlopeProfile() {
    }

    /**
     * End heights of a straight rail relative to its own seated base. {@code negativeEnd} is the
     * north (Z axis) or west (X axis) edge; {@code positiveEnd} the south or east edge. The vanilla
     * pair is what the unmodified model draws: 0 for a flat end, 1 for the high end of a ramp.
     */
    public record Profile(Direction.Axis axis,
                          double negativeEnd, double positiveEnd,
                          double vanillaNegativeEnd, double vanillaPositiveEnd) {

        /** True when the fitted profile is exactly what vanilla already draws. */
        public boolean isVanilla() {
            return Math.abs(negativeEnd - vanillaNegativeEnd) <= EPS
                    && Math.abs(positiveEnd - vanillaPositiveEnd) <= EPS;
        }

        /** How far the negative-axis edge moves from where vanilla draws it. */
        public double negativeDelta() {
            return negativeEnd - vanillaNegativeEnd;
        }

        /** How far the positive-axis edge moves from where vanilla draws it. */
        public double positiveDelta() {
            return positiveEnd - vanillaPositiveEnd;
        }

        public double highestEnd() {
            return Math.max(negativeEnd, positiveEnd);
        }

        public double lowestEnd() {
            return Math.min(negativeEnd, positiveEnd);
        }
    }

    /**
     * The fitted profile of the rail at {@code pos}, or {@code null} when {@code state} is not a
     * straight rail (a curve, or not a rail at all). Reads the rail's own stored seat and the seats
     * of the rails it connects to; writes nothing.
     */
    public static Profile resolve(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return null;
        }
        RailShape shape = shapeOf(state);
        if (shape == null) {
            return null;
        }
        Direction.Axis axis = axisOf(shape);
        if (axis == null) {
            return null;
        }
        Direction negative = axis == Direction.Axis.Z ? Direction.NORTH : Direction.WEST;
        Direction positive = negative.getOpposite();
        boolean rampNegative = ascendsToward(shape, negative);
        boolean rampPositive = ascendsToward(shape, positive);
        double vanillaNegative = rampNegative ? 1.0d : 0.0d;
        double vanillaPositive = rampPositive ? 1.0d : 0.0d;
        double seat = seatOf(world, pos, state);

        double negativeEnd;
        double positiveEnd;
        if (rampNegative || rampPositive) {
            // A ramp: the climbing end is refitted; the end on the support never lifts.
            negativeEnd = rampNegative ? refitRamp(world, pos, seat, negative) : 0.0d;
            positiveEnd = rampPositive ? refitRamp(world, pos, seat, positive) : 0.0d;
        } else {
            negativeEnd = flatLift(world, pos, seat, negative);
            positiveEnd = flatLift(world, pos, seat, positive);
            if (negativeEnd > EPS && positiveEnd > EPS) {
                // A rail in a dip keeps both ends on its support.
                negativeEnd = 0.0d;
                positiveEnd = 0.0d;
            }
        }
        return new Profile(axis, negativeEnd, positiveEnd, vanillaNegative, vanillaPositive);
    }

    /**
     * The outline box for a fitted profile, in the rail's own cell before its seat is applied.
     * Interpolates vanilla's own two boxes — 2/16 tall for a flat rail, 8/16 for a full ramp — so a
     * vanilla ramp yields exactly vanilla's box, and reaches down when an end is drawn below the base.
     */
    public static VoxelShape outlineShape(Profile profile) {
        double top = FLAT_BOX_TOP + (RAMP_BOX_TOP - FLAT_BOX_TOP) * Math.max(0.0d, profile.highestEnd());
        double bottom = Math.min(0.0d, profile.lowestEnd());
        return Shapes.box(0.0d, bottom, 0.0d, 1.0d, top, 1.0d);
    }

    /** The rail's own vanilla ramp toward {@code toward}, refitted to the seat of the rail it climbs to. */
    private static double refitRamp(BlockGetter world, BlockPos pos, double seat, Direction toward) {
        // Vanilla makes a rail a ramp on the mere PRESENCE of a rail one cell up in that direction,
        // so presence is also the test here; the neighbour's own shape does not enter into it.
        BlockPos up = pos.relative(toward).above();
        BlockState upState = world.getBlockState(up);
        if (!isRail(upState)) {
            return 1.0d;
        }
        return 1.0d + (seatOf(world, up, upState) - seat);
    }

    /**
     * How far a flat end lifts to meet a connected neighbour at the same grid level that is drawn
     * higher: 0 when there is no such neighbour, when it is drawn level or lower, or when the
     * neighbour's own shape does not reach back flat toward this rail.
     */
    private static double flatLift(BlockGetter world, BlockPos pos, double seat, Direction toward) {
        BlockPos same = pos.relative(toward);
        BlockState sameState = world.getBlockState(same);
        if (!isRail(sameState)) {
            return 0.0d;
        }
        RailShape neighbourShape = shapeOf(sameState);
        if (neighbourShape == null || !reachesFlat(neighbourShape, toward.getOpposite())) {
            return 0.0d;
        }
        double lift = seatOf(world, same, sameState) - seat;
        return lift > EPS ? lift : 0.0d;
    }

    /** A cell's stored seat, exactly as the minecart seat and the model read it. */
    private static double seatOf(BlockGetter world, BlockPos pos, BlockState state) {
        double dy = SlabSupport.getYOffset(world, pos, state);
        return Double.isFinite(dy) ? dy : 0.0d;
    }

    /**
     * A class test, not vanilla's tag test: {@code BaseRailBlock.isRail} reads the rails tag, which is
     * not bound yet when the block registry initialises every state's shape cache — the path the
     * outline mixin is reached from at bootstrap. Every block with the rail shape property extends
     * {@link BaseRailBlock}, so the class is the property's real domain.
     */
    public static boolean isRail(BlockState state) {
        return state != null && state.getBlock() instanceof BaseRailBlock;
    }

    /** The rail shape of a rail state, or {@code null} for anything that is not a rail. */
    public static RailShape shapeOf(BlockState state) {
        if (state == null || !(state.getBlock() instanceof BaseRailBlock rail)) {
            return null;
        }
        return state.getValue(rail.getShapeProperty());
    }

    /** The axis of a straight rail, or {@code null} for a curve. */
    public static Direction.Axis axisOf(RailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> Direction.Axis.Z;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> Direction.Axis.X;
            default -> null;
        };
    }

    /** True when {@code shape} is the ramp whose high end faces {@code direction}. */
    public static boolean ascendsToward(RailShape shape, Direction direction) {
        return switch (shape) {
            case ASCENDING_NORTH -> direction == Direction.NORTH;
            case ASCENDING_SOUTH -> direction == Direction.SOUTH;
            case ASCENDING_EAST -> direction == Direction.EAST;
            case ASCENDING_WEST -> direction == Direction.WEST;
            default -> false;
        };
    }

    /**
     * True when {@code shape} connects toward {@code direction} AT ITS OWN LEVEL. A ramp reaches its
     * low side flat and its high side one cell up, so only the low side counts here; a curve reaches
     * its two sides flat.
     */
    public static boolean reachesFlat(RailShape shape, Direction direction) {
        return switch (shape) {
            case NORTH_SOUTH -> direction == Direction.NORTH || direction == Direction.SOUTH;
            case EAST_WEST -> direction == Direction.EAST || direction == Direction.WEST;
            case ASCENDING_NORTH -> direction == Direction.SOUTH;
            case ASCENDING_SOUTH -> direction == Direction.NORTH;
            case ASCENDING_EAST -> direction == Direction.WEST;
            case ASCENDING_WEST -> direction == Direction.EAST;
            case SOUTH_EAST -> direction == Direction.SOUTH || direction == Direction.EAST;
            case SOUTH_WEST -> direction == Direction.SOUTH || direction == Direction.WEST;
            case NORTH_WEST -> direction == Direction.NORTH || direction == Direction.WEST;
            case NORTH_EAST -> direction == Direction.NORTH || direction == Direction.EAST;
        };
    }
}
