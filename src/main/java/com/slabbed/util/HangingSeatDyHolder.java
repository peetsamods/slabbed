package com.slabbed.util;

import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;

/**
 * A hung decoration's REMEMBERED seat: the height offset of the face it was hung on, minted once
 * when it was hung and never re-derived (LAW 1 - where it is placed is where it stays; maintainer
 * ruling, 2026-09-13: this applies to everything hung on a wall, not only placed blocks).
 *
 * <p>Implemented on {@code HangingEntity} (item frames, glow frames, paintings) by
 * {@code HangingEntityRememberedSeatMixin}. The entity's real position stays at grid height; the
 * seat moves its bounding box and its drawing.
 *
 * <p>On this line {@code HangingEntity} is the only shared ancestor, and its subclasses REPLACE
 * rather than extend the methods this seat rides on: {@code defineSynchedData},
 * {@code setDirection}, {@code recalculateBoundingBox} and {@code onSyncedDataUpdated} are each
 * re-implemented without a {@code super} call. The relay methods below are the single
 * implementation of each step, so a per-class hook adds a CALL and never a second copy of the
 * logic. Keep it that way: a second copy is a second place the law can drift.
 */
public interface HangingSeatDyHolder {

    /** The remembered seat, or 0.0 while none has been minted yet. Never NaN. */
    double slabbed$hangSeatDy();

    /** True once a seat has been minted (or restored from save data). */
    boolean slabbed$hasHangSeat();

    /** Restores a saved seat verbatim; used by the per-class save-data hooks only. */
    void slabbed$restoreHangSeatDy(double dy);

    /** Declares the synced seat key; called from every {@code defineSynchedData} that skips super. */
    void slabbed$declareHangSeatKey();

    /** The ONE derivation; called from every {@code setDirection} that skips super, before layout. */
    void slabbed$mintHangSeatFor(Direction direction);

    /** Applies the remembered seat to a freshly laid-out box; called at every layout's tail. */
    void slabbed$seatHangBox();

    /** Client-only relayout when the synced seat arrives; called from {@code onSyncedDataUpdated}. */
    void slabbed$relayoutOnSyncedSeat(EntityDataAccessor<?> key);
}
