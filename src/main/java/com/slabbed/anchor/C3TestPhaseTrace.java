package com.slabbed.anchor;

import java.util.ArrayList;
import java.util.List;

/**
 * Inert-by-default ordering sink for the C3 placement capture path.
 *
 * <p>Production plumbing, not debug tooling: {@code BlockItemPlacementIntentMixin} calls
 * {@link #markTestPhase(String)} at the two ordering-critical points of a placement (marker authoring
 * finished, placement fact published). While no trace span is open the call is a single boolean read
 * and returns immediately, so the shipped path pays nothing.
 *
 * <p>It lives in {@code src/main} because a {@code src/gametest} class is not visible to the
 * production mixin. A gametest opens a span with {@link #beginTestPhaseTrace()}, performs one
 * placement, reads {@link #snapshotTestPhaseTrace()} and closes the span in a {@code finally}.
 *
 * <p>This is the 1.21.1 stand-in for the 26.2 donor's {@code PlacementDyPredictionBridge} trace
 * seam. That class also carries client-prediction wiring; this line has no prediction, so only the
 * ordering sink is ported.
 */
public final class C3TestPhaseTrace {

    private static final List<String> PHASES = new ArrayList<>();
    private static boolean enabled;

    private C3TestPhaseTrace() {
    }

    public static synchronized void beginTestPhaseTrace() {
        PHASES.clear();
        enabled = true;
    }

    public static synchronized void markTestPhase(String phase) {
        if (enabled) {
            PHASES.add(phase);
        }
    }

    public static synchronized List<String> snapshotTestPhaseTrace() {
        return List.copyOf(PHASES);
    }

    public static synchronized void endTestPhaseTrace() {
        enabled = false;
        PHASES.clear();
    }
}
