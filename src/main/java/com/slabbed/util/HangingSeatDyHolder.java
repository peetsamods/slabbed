package com.slabbed.util;

/**
 * A hung decoration's REMEMBERED seat: the height offset of the face it was hung on, minted once
 * when it was hung and never re-derived (LAW.md; maintainer ruling, 2026-09-13: where it is placed
 * is where it stays applies to everything hung on a wall).
 *
 * <p>On this line the seat is PHYSICAL: vanilla derives a decoration's position from its box, so
 * the box and the position move together. Implemented by {@code ItemFramePhysicalOffsetMixin}
 * (frames) and {@code PaintingRememberedSeatMixin} (paintings).
 */
public interface HangingSeatDyHolder {

    /** The remembered seat, or 0.0 while none has been minted yet. Never NaN. */
    double slabbed$hangSeatDy();

    /** True once a seat has been minted (or restored from save data). */
    boolean slabbed$hasHangSeat();
}
