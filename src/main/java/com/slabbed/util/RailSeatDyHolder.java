package com.slabbed.util;

/**
 * A minecart's bound rail seat offset.
 *
 * <p>INVARIANT: a seated cart's ENTITY position is PHYSICAL (rail cell + seat) while every rail
 * computation runs in the LOGICAL (grid) frame. The seat is re-read each tick from the rail cell's
 * STORED placement fact and is never re-derived from the rail's neighbours, so nothing here can move
 * a placed block.
 *
 * <p>Implemented on {@code AbstractMinecart} by {@code MinecartRailSeatMixin}; read through
 * {@link MinecartRailFrame#dyOf(Object)} so a caller never has to know that.
 */
public interface RailSeatDyHolder {

    /** The bound seat offset, or 0.0 when this cart is not seated on a lowered rail. Never NaN. */
    double slabbed$railSeatDy();
}
