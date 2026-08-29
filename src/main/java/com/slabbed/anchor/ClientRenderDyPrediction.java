package com.slabbed.anchor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The height a placement transaction already resolved on the client, held only until the
 * authoritative fact for that cell arrives.
 *
 * <p>The client runs the same placement transaction the server does and reaches the same height,
 * then drops it, because only the server may write a fact. Its chunk mesh then falls back to the
 * live neighbourhood lane, which deliberately answers something else - the placement answer may
 * legitimately be deeper. The block therefore draws at the wrong height until the fact syncs, and
 * visibly jumps when it lands.
 *
 * <p><b>This is not a fact and must never be read as one.</b> It is consulted from exactly one
 * place, the chunk-mesh height lookup, so collision, targeting, and every interaction keep reading
 * the authoritative store and cannot be moved by a prediction. Reading it anywhere else would make
 * a guess indistinguishable from a frozen height, which is the first law's whole subject.
 *
 * <p>Entries expire on a tick deadline whether or not a fact ever arrives, because some accepted
 * placements never produce one - a refused write, or a cell the store excludes. Without the
 * deadline those entries would live forever and become exactly the fact they must not be.
 */
public final class ClientRenderDyPrediction {
    /** Long enough to cover a sync round trip, short enough that a stale guess cannot persist. */
    private static final int LIFETIME_TICKS = 40;

    private static final Map<Long, Entry> PENDING = new ConcurrentHashMap<>();
    private static volatile int currentTick;

    private ClientRenderDyPrediction() {
    }

    private record Entry(int halfSteps, int expiresAtTick) {
    }

    /** Records what the client resolved for a cell it just placed into. */
    public static void record(long packedPos, int halfSteps) {
        PENDING.put(packedPos, new Entry(halfSteps, currentTick + LIFETIME_TICKS));
    }

    /**
     * The predicted height for a cell, or {@link SlabPlacementHeightAttachment#ABSENT_HALF_STEPS}.
     * An expired entry answers absent and is dropped, so a stale guess never renders.
     */
    public static int halfStepsOrAbsent(long packedPos) {
        Entry entry = PENDING.get(packedPos);
        if (entry == null) {
            return SlabPlacementHeightAttachment.ABSENT_HALF_STEPS;
        }
        if (entry.expiresAtTick() - currentTick <= 0) {
            PENDING.remove(packedPos, entry);
            return SlabPlacementHeightAttachment.ABSENT_HALF_STEPS;
        }
        return entry.halfSteps();
    }

    /** Drops a prediction, which the authoritative fact for that cell must do on arrival. */
    public static void forget(long packedPos) {
        PENDING.remove(packedPos);
    }

    /** True while any prediction is outstanding, so the caller can skip work when none is. */
    public static boolean isEmpty() {
        return PENDING.isEmpty();
    }

    /** Advances the deadline clock and drops everything already past it. */
    public static void advanceTick() {
        int now = ++currentTick;
        if (PENDING.isEmpty()) {
            return;
        }
        PENDING.values().removeIf(entry -> entry.expiresAtTick() - now <= 0);
    }

    /** Drops every prediction; a disconnect or a level change invalidates all of them. */
    public static void clear() {
        PENDING.clear();
    }
}
