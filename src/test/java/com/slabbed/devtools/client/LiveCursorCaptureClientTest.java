package com.slabbed.devtools.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class LiveCursorCaptureClientTest {
    @Test
    void sameRaySkipRequiresStrictlyNearerPickableSurface() {
        assertTrue(LiveCursorCaptureClient.replaySkippedNearerSurface(true, true, 2.0d, 3.0d));
        assertTrue(LiveCursorCaptureClient.replaySkippedNearerSurface(true, false, 2.0d, Double.NaN));
        assertFalse(LiveCursorCaptureClient.replaySkippedNearerSurface(true, true, 2.0d, 2.0d));
        assertFalse(LiveCursorCaptureClient.replaySkippedNearerSurface(true, true, 3.0d, 2.0d));
        assertFalse(LiveCursorCaptureClient.replaySkippedNearerSurface(false, true, Double.NaN, 2.0d));
    }

    @Test
    void floatNoiseAtTheSurfaceIsNeverASkip() {
        assertFalse(LiveCursorCaptureClient.replaySkippedNearerSurface(
                true, true, 2.0d, 2.0d + 1.0e-6d));
        assertTrue(LiveCursorCaptureClient.replaySkippedNearerSurface(
                true, true, 2.0d, 2.0d + 1.0e-3d));
    }
}
