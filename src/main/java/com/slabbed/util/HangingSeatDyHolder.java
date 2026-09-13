package com.slabbed.util;

/**
 * A hung decoration's REMEMBERED seat: the height offset of the face it was hung on, minted once
 * when it was hung and never re-derived (LAW 1 — where it is placed is where it stays; maintainer
 * ruling, 2026-09-13: this applies to everything hung on a wall, not only placed blocks).
 *
 * <p>Implemented on {@code HangingEntity} (item frames, glow frames, paintings) by
 * {@code HangingEntityRememberedSeatMixin}. The entity's real position stays at grid height; the
 * seat moves its bounding box and its drawing.
 */
public interface HangingSeatDyHolder {

    /** The remembered seat, or 0.0 while none has been minted yet. Never NaN. */
    double slabbed$hangSeatDy();

    /** True once a seat has been minted (or restored from save data). */
    boolean slabbed$hasHangSeat();

    /** Restores a saved seat verbatim; used by the per-class save-data hooks only. */
    void slabbed$restoreHangSeatDy(double dy);
}
