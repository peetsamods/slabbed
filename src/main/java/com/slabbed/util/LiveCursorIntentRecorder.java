package com.slabbed.util;

import java.util.LinkedHashMap;

/**
 * Retired diagnostic recorder — reduced to an inert stub for release.
 *
 * <p>The live cursor / placement-intent capture (per-session file I/O + summary serialization) was a
 * development tool only, gated behind {@code -Dslabbed.liveCursorIntentRecorder}. Its heavy logic
 * (~500 lines) is removed so it no longer bloats the runtime jar. The public API is preserved as
 * no-ops so the gated call sites in {@code BlockItemPlacementIntentMixin} and
 * {@code GameRendererCrosshairRetargetMixin} still compile and cheaply short-circuit:
 * {@link #enabled()} returns {@code false}, so those callers early-return with zero cost. To revive
 * full recording, restore from git history (the API is unchanged).
 */
public final class LiveCursorIntentRecorder {

    private LiveCursorIntentRecorder() {
    }

    public static boolean enabled() {
        return false;
    }

    public static long lastCursorRowId() {
        return 0L;
    }

    public static void recordCursor(LinkedHashMap<String, String> fields) {
    }

    public static void recordRenderedOutline(LinkedHashMap<String, String> fields) {
    }

    public static void recordAction(LinkedHashMap<String, String> fields) {
    }

    public static void flushSummaryForTests() {
    }

    public static void resetForTests() {
    }
}
