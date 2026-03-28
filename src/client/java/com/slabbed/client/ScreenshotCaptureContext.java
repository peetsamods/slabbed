package com.slabbed.client;

public final class ScreenshotCaptureContext {
    private static final ThreadLocal<State> STATE = new ThreadLocal<>();

    private ScreenshotCaptureContext() {
    }

    public static void begin(CaptureProfile profile) {
        STATE.set(new State(profile));
    }

    public static void end() {
        STATE.remove();
    }

    public static boolean captureActive() {
        return STATE.get() != null;
    }

    public static CaptureProfile currentProfile() {
        State state = STATE.get();
        return state == null ? null : state.profile();
    }

    private record State(CaptureProfile profile) {
    }
}
