package com.slabbed.util;

import net.minecraft.core.BlockPos;
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

    /**
     * A cell's seat is whatever it recorded at placement; a cell with no recorded fact resolves flat
     * and stays flat. This is a STORE READ, not a re-derivation.
     */
    public static double seatOf(BlockGetter world, BlockPos cell, BlockState state) {
        if (world == null || cell == null || state == null) {
            return 0.0d;
        }
        double dy = SlabSupport.getYOffset(world, cell, state);
        return (Double.isFinite(dy) && Math.abs(dy) > SEAT_EPSILON) ? dy : 0.0d;
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

    /** Null-safe counterpart of {@link #toPhysical}. */
    public static Vec3 toLogical(Object cart, Vec3 physical) {
        double dy = dyOf(cart);
        return (physical == null || dy == 0.0d) ? physical : physical.subtract(0.0d, dy, 0.0d);
    }
}
