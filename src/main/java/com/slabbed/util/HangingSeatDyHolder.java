package com.slabbed.util;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;

/**
 * A hung decoration's REMEMBERED seat: the height offset of the face it was hung on, minted once
 * when it was hung and never re-derived (LAW.md Law 1 — where it is placed is where it stays;
 * maintainer ruling, 2026-09-13: this applies to everything hung on a wall, not only placed
 * blocks).
 *
 * <p>Implemented on {@code HangingEntity} (item frames, glow frames, paintings) by
 * {@code HangingEntityRememberedSeatMixin}. The entity's real position stays at grid height; the
 * seat moves its bounding box and its drawing.
 *
 * <p>The last three methods are BRIDGES, and they exist because of this Minecraft version's class
 * shape: {@code HangingEntity} declares no synched-data, facing or save hook of its own, and both
 * {@code ItemFrame} and {@code Painting} override those hooks WITHOUT calling super. A shared
 * injection therefore cannot reach them; each hung class forwards to the one implementation here.
 * Keep every forwarder identical — a hung class that forwards one hook and not another gets a seat
 * that is minted but never drawn, or saved but never restored.
 */
public interface HangingSeatDyHolder {

    /** The remembered seat, or 0.0 while none has been minted yet. Never NaN. */
    double slabbed$hangSeatDy();

    /** True once a seat has been minted (or restored from save data). */
    boolean slabbed$hasHangSeat();

    /** Restores a saved seat verbatim; used by the per-class save-data hooks only. */
    void slabbed$restoreHangSeatDy(double dy);

    /** Bridge: declares the synced seat field. Called from each hung class's synched-data hook. */
    void slabbed$defineHangSeat(SynchedEntityData.Builder builder);

    /**
     * Bridge: the ONE derivation, called from each hung class's facing setter once the position
     * and the facing are both known. Does nothing when a seat already exists.
     */
    void slabbed$mintHangSeatFromWall();

    /** Bridge: re-lays the CLIENT box out when the synced seat arrives. */
    void slabbed$onHangSeatDataUpdated(EntityDataAccessor<?> key);
}
