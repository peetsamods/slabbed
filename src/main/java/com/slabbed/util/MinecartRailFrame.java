package com.slabbed.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Physical/logical frame conversion for a minecart seated on a lowered rail.
 *
 * <p>PHYSICAL is where the cart really is — the rail cell plus its seat. LOGICAL is the grid frame
 * every vanilla rail computation is written in. INVARIANT: convert a POSITION, never a DIFFERENCE.
 * Converting one side of a delta silently drifts the cart by the seat every tick.
 *
 * <p>THE SEAT FOLLOWS THE DRAWN RAIL (maintainer ruling, 2026-09-28). A rail's drawn slope is
 * fitted to its neighbours ({@link RailSlopeProfile}), so the seat is not one number per cell: it
 * is the rail's stored seat plus the fitted slope's lift at the cart's place along the rail
 * ({@link #seatAt}). A cart therefore rides the slope the player sees instead of dropping half a
 * block at the cell edge, where its body would jam against the next rail's higher support.
 *
 * <p>THE COLLISION PASS RIDES THE TOP ({@link #topSeatAt}). Vanilla itself lifts a cart to the
 * high end of a ramp cell before running block collision, and only then snaps it down onto the
 * slope; a fitted rail gets the same treatment, so a cart low in a dip is never pushed into the
 * neighbouring support by the collision sweep.
 *
 * <p>The conversion lives here, in one place, because three lanes share it: the two behaviour
 * mixins and the client renderer mixin. Keeping it as pure static functions is also what lets a
 * headless server test pin the conversion against a real cart and a real rail even though the
 * renderer that composes it is client-only.
 */
public final class MinecartRailFrame {

    private static final double SEAT_EPSILON = 1.0e-6d;

    private MinecartRailFrame() {
    }

    /** The bound seat of a cart, or 0.0 for anything that is not a seated minecart. */
    public static double dyOf(Object cart) {
        if (!(cart instanceof RailSeatDyHolder holder)) {
            return 0.0d;
        }
        double dy = holder.slabbed$railSeatDy();
        return Double.isFinite(dy) ? dy : 0.0d;
    }

    /** Records the seat a cart's physical position now carries; a no-op for anything else. */
    public static void bind(Object cart, double seat) {
        if (cart instanceof RailSeatDyHolder holder) {
            holder.slabbed$bindRailSeatDy(seat);
        }
    }

    /**
     * A cell's stored seat: whatever it recorded at placement; a cell with no recorded fact resolves
     * flat and stays flat. This is a STORE READ, not a re-derivation.
     */
    public static double seatOf(BlockGetter world, BlockPos cell, BlockState state) {
        if (world == null || cell == null || state == null) {
            return 0.0d;
        }
        double dy = SlabSupport.getYOffset(world, cell, state);
        return (Double.isFinite(dy) && Math.abs(dy) > SEAT_EPSILON) ? dy : 0.0d;
    }

    /**
     * The seat of a cart standing at ({@code x}, {@code z}) in rail cell {@code cell}: the stored
     * seat plus the fitted slope's lift at that place. A rail vanilla already draws correctly (no
     * fit) gives the stored seat everywhere in the cell.
     */
    public static double seatAt(BlockGetter world, BlockPos cell, BlockState state, double x, double z) {
        double seat = seatOf(world, cell, state);
        RailSlopeProfile.Profile profile = RailSlopeProfile.resolve(world, cell, state);
        if (profile == null || profile.isVanilla()) {
            return seat;
        }
        double t = profile.axis() == Direction.Axis.Z ? z - cell.getZ() : x - cell.getX();
        return seat + profile.liftAt(Math.max(0.0d, Math.min(1.0d, t)));
    }

    /**
     * The seat that puts a cart at the TOP of the rail's drawn profile — the height the collision
     * pass runs at. For a rail without a fit this is the stored seat, so the pass is exactly
     * vanilla's.
     */
    public static double topSeatAt(BlockGetter world, BlockPos cell, BlockState state) {
        double seat = seatOf(world, cell, state);
        RailSlopeProfile.Profile profile = RailSlopeProfile.resolve(world, cell, state);
        if (profile == null || profile.isVanilla()) {
            return seat;
        }
        double vanillaTop = Math.max(profile.vanillaNegativeEnd(), profile.vanillaPositiveEnd());
        return seat + (profile.highestEnd() - vanillaTop);
    }

    /**
     * The rail cell a LOGICAL position belongs to: the cell containing it, or the one below when the
     * position sits at the top of a ramp cell — vanilla's own rail-cell derivation. Null when
     * neither holds a rail.
     */
    public static BlockPos railCellAt(BlockGetter world, double x, double logicalY, double z) {
        BlockPos cell = BlockPos.containing(x, logicalY, z);
        if (RailSlopeProfile.isRail(world.getBlockState(cell))) {
            return cell;
        }
        BlockPos below = cell.below();
        return RailSlopeProfile.isRail(world.getBlockState(below)) ? below : null;
    }

    public static double toLogicalY(Object cart, double physicalY) {
        return physicalY - dyOf(cart);
    }

    public static double toPhysicalY(Object cart, double logicalY) {
        return logicalY + dyOf(cart);
    }

    /** Null-safe: the vanilla rail helpers this wraps return null when the position is off-cell. */
    public static Vec3 toPhysical(Object cart, Vec3 logical) {
        double dy = dyOf(cart);
        return (logical == null || dy == 0.0d) ? logical : logical.add(0.0d, dy, 0.0d);
    }

    /**
     * A LOGICAL rail position to PHYSICAL with the seat the rail is drawn at THERE — the renderer's
     * snap and its front/back slope probes each land on the drawn slope, which is what tilts the
     * cart along a fitted rail. Falls back to the cart's bound seat off any rail. Null-safe.
     */
    public static Vec3 toPhysicalAt(Object cart, BlockGetter world, Vec3 logical) {
        if (logical == null) {
            return null;
        }
        BlockPos cell = world == null ? null : railCellAt(world, logical.x, logical.y, logical.z);
        double dy = cell == null ? dyOf(cart) : seatAt(world, cell, world.getBlockState(cell), logical.x, logical.z);
        return dy == 0.0d ? logical : logical.add(0.0d, dy, 0.0d);
    }

    /** Null-safe counterpart of {@link #toPhysical}. */
    public static Vec3 toLogical(Object cart, Vec3 physical) {
        double dy = dyOf(cart);
        return (physical == null || dy == 0.0d) ? physical : physical.subtract(0.0d, dy, 0.0d);
    }
}
