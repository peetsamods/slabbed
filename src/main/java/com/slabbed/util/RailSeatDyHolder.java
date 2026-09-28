package com.slabbed.util;

/**
 * A minecart's bound rail seat offset.
 *
 * <p>INVARIANT: a seated cart's ENTITY position is PHYSICAL (rail cell + seat) while every rail
 * computation runs in the LOGICAL (grid) frame. The seat is the drawn height of the rail UNDER THE
 * CART — the rail cell's STORED placement fact plus the fitted slope's lift at the cart's place
 * along it — and is never re-derived from the rail's neighbours in any way that writes back, so
 * nothing here can move a placed block.
 *
 * <p>Implemented on {@code AbstractMinecart} by {@code MinecartRailSeatMixin}; read through
 * {@link MinecartRailFrame#dyOf(Object)} so a caller never has to know that.
 */
public interface RailSeatDyHolder {

    /** The bound seat offset, or 0.0 when this cart is not seated on a lowered rail. Never NaN. */
    double slabbed$railSeatDy();

    /**
     * Records the seat the cart's physical position now carries, WITHOUT moving the cart. The rail
     * solver calls this at the moment it writes a physical position, so every later read in the same
     * tick converts with the seat that position was written with.
     */
    void slabbed$bindRailSeatDy(double dy);
}
