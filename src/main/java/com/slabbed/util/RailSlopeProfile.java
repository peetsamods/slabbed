package com.slabbed.util;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * The drawn profile of a straight rail: how high each of its two ends is, in blocks, above the
 * rail's own seated base (maintainer ruling, 2026-09-28).
 *
 * <p>Vanilla picks a rail's slope from its GRID neighbours alone: a flat plane, or a full-block ramp
 * toward the rail one cell up. Under Slabbed a rail also carries a seat, so two connected
 * rails can sit at different drawn heights inside the same grid relation — a lowered rail beside a
 * flush one, a ramp climbing onto a lowered rail. Vanilla's plane and ramp then do not meet. This
 * class fits the rail's profile to the neighbour it actually connects to, so the drawn rails join.
 *
 * <p>INVARIANT (LAW.md): this is a READ of seats, never a write, and never a re-derivation of
 * any rail's own height. A rail's seat is its placed height and stays; only the SLOPE drawn between
 * two seats follows the neighbour — the same class of neighbour-driven shape as vanilla's own
 * {@link RailShape}, which vanilla rewrites on the rail already in place when a new rail connects to
 * it (LAW 1's "genuine vanilla mechanism" clause).
 *
 * <p>INVARIANT: EACH END IS LOCAL TO ITS OWN SEAM. An end's height depends on this rail's seat and
 * shape and on the rail at that end, and on nothing else — never on the rail at the opposite end.
 * So an edit at one end of a placed rail can move only the end that faces it. A rail whose two flat
 * ends both lift is drawn as a V: each half rises from the support at the middle, and each seam
 * still closes on its own. Do not re-add a cross-end rule (a dip that flattens both ends, a
 * precedence between ends): that is exactly the coupling this invariant forbids.
 *
 * <p>THE RULE. For each end of a straight rail, find the rail vanilla connects it to, then:
 * <ul>
 *   <li>A vanilla RAMP is refitted to meet the rail it climbs to, shorter or longer as needed, but
 *       never below its own support (a rail it "climbs" to that is drawn lower stays a step) and
 *       never past {@link #MAX_RISE}. Its other end stays on the support.</li>
 *   <li>A FLAT end lifts, up to {@link #MAX_RISE}, toward a connected neighbour at the same grid
 *       level that is drawn higher. A neighbour drawn lower lifts from its own side when it can.</li>
 *   <li>Curves keep vanilla geometry; a curve cannot slope in vanilla either.</li>
 * </ul>
 *
 * <p>SEAMS THAT STAY STEPS, by design: a ramp's low end and a curve never lift, so a flat rail drawn
 * higher than either keeps its step (as vanilla drew it); a rise past {@link #MAX_RISE} in one cell
 * would be a wall, not a rail, and keeps the remaining step.
 *
 * <p>Pure and server-loadable: the client draws the rail from this profile and the shared outline
 * mixin sizes the rail's box from it, so the drawn rail, its outline and its raycast box can never
 * disagree about the slope.
 */
public final class RailSlopeProfile {

    /**
     * The most a rail may rise across one cell, in blocks: the lowered-ramp-onto-flush-rail case
     * (one block plus a half-block seat). Beyond that the drawn rail would be a near-vertical wall
     * no cart could follow, so the seam keeps a step instead.
     */
    public static final double MAX_RISE = 1.5d;

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
     *
     * <p>The drawn height along the rail is {@link #heightAt}: one plane from end to end, except a
     * {@link #kinked} profile (both flat ends lifted), which is two planes meeting on the support at
     * the middle of the cell.
     */
    public record Profile(Direction.Axis axis,
                          double negativeEnd, double positiveEnd,
                          double vanillaNegativeEnd, double vanillaPositiveEnd) {

        /** True when the fitted profile is exactly what vanilla already draws. */
        public boolean isVanilla() {
            return Math.abs(negativeEnd - vanillaNegativeEnd) <= EPS
                    && Math.abs(positiveEnd - vanillaPositiveEnd) <= EPS;
        }

        /**
         * True when both ends of a flat rail lift: the rail is drawn as a V resting on its support
         * at the middle of the cell, so that each seam closes without depending on the other. Only
         * a flat rail can kink — a ramp always keeps one end on the support.
         */
        public boolean kinked() {
            return vanillaNegativeEnd == 0.0d && vanillaPositiveEnd == 0.0d
                    && negativeEnd > EPS && positiveEnd > EPS;
        }

        /**
         * The drawn height above the seated base at fraction {@code t} along the axis, from the
         * negative edge ({@code t = 0}) to the positive edge ({@code t = 1}).
         */
        public double heightAt(double t) {
            if (kinked()) {
                return t <= 0.5d ? negativeEnd * (1.0d - 2.0d * t) : positiveEnd * (2.0d * t - 1.0d);
            }
            return negativeEnd * (1.0d - t) + positiveEnd * t;
        }

        /** What vanilla's own model draws at fraction {@code t}: a plane between its two ends. */
        public double vanillaHeightAt(double t) {
            return vanillaNegativeEnd * (1.0d - t) + vanillaPositiveEnd * t;
        }

        /** How far a vanilla vertex at fraction {@code t} moves to land on the fitted profile. */
        public double liftAt(double t) {
            return heightAt(t) - vanillaHeightAt(t);
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
     * straight rail (a curve, or not a rail at all). Reads the rail's own seat and the seats
     * of the rails it connects to; writes nothing.
     */
    public static Profile resolve(BlockView world, BlockPos pos, BlockState state) {
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
            // Each flat end decides for itself; two lifted ends make a V (see the class invariant).
            negativeEnd = flatLift(world, pos, seat, negative);
            positiveEnd = flatLift(world, pos, seat, positive);
        }
        return new Profile(axis, negativeEnd, positiveEnd, vanillaNegative, vanillaPositive);
    }

    /**
     * The outline box for a fitted profile, in the rail's own cell before its seat is applied.
     * Interpolates vanilla's own two boxes — 2/16 tall for a flat rail, 8/16 for a full ramp — so a
     * vanilla ramp yields exactly vanilla's box.
     */
    public static VoxelShape outlineShape(Profile profile) {
        double top = FLAT_BOX_TOP + (RAMP_BOX_TOP - FLAT_BOX_TOP) * Math.max(0.0d, profile.highestEnd());
        double bottom = Math.min(0.0d, profile.lowestEnd());
        return VoxelShapes.cuboid(0.0d, bottom, 0.0d, 1.0d, top, 1.0d);
    }

    /**
     * The rail's own vanilla ramp toward {@code toward}, refitted to the seat of the rail it climbs
     * to: never below the support it rests on, never past {@link #MAX_RISE}.
     */
    private static double refitRamp(BlockView world, BlockPos pos, double seat, Direction toward) {
        // Vanilla makes a rail a ramp on the mere PRESENCE of a rail one cell up in that direction,
        // so presence is also the test here; the neighbour's own shape does not enter into it.
        BlockPos up = pos.offset(toward).up();
        BlockState upState = world.getBlockState(up);
        if (!isRail(upState)) {
            return 1.0d;
        }
        double rise = 1.0d + (seatOf(world, up, upState) - seat);
        return Math.min(MAX_RISE, Math.max(0.0d, rise));
    }

    /**
     * How far a flat end lifts to meet a connected neighbour at the same grid level that is drawn
     * higher: 0 when there is no such neighbour, when it is drawn level or lower, or when the
     * neighbour's own shape does not reach back flat toward this rail; at most {@link #MAX_RISE}.
     */
    private static double flatLift(BlockView world, BlockPos pos, double seat, Direction toward) {
        BlockPos same = pos.offset(toward);
        BlockState sameState = world.getBlockState(same);
        if (!isRail(sameState)) {
            return 0.0d;
        }
        RailShape neighbourShape = shapeOf(sameState);
        if (neighbourShape == null || !reachesFlat(neighbourShape, toward.getOpposite())) {
            return 0.0d;
        }
        double lift = seatOf(world, same, sameState) - seat;
        return lift > EPS ? Math.min(MAX_RISE, lift) : 0.0d;
    }

    /**
     * A cell's seat, exactly as the minecart seat and the block model read it:
     * {@link SlabSupport#getYOffset}, which answers a cell's stored height and, for a cell placed
     * before the height store existed, the same live reading that cell has always had.
     */
    private static double seatOf(BlockView world, BlockPos pos, BlockState state) {
        double dy = SlabSupport.getYOffset(world, pos, state);
        return Double.isFinite(dy) ? dy : 0.0d;
    }

    /**
     * A class test, not vanilla's tag test: {@code AbstractRailBlock.isRail(BlockState)} reads the
     * rails tag, which is not bound yet when the block registry initialises every state's shape
     * cache — the path the outline mixin is reached from at bootstrap. Every block with the rail
     * shape property extends {@link AbstractRailBlock}, so the class is the property's real domain.
     */
    public static boolean isRail(BlockState state) {
        return state != null && state.getBlock() instanceof AbstractRailBlock;
    }

    /** The rail shape of a rail state, or {@code null} for anything that is not a rail. */
    public static RailShape shapeOf(BlockState state) {
        if (state == null || !(state.getBlock() instanceof AbstractRailBlock rail)) {
            return null;
        }
        return state.get(rail.getShapeProperty());
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
