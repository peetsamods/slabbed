package com.slabbed.util;

public final class RaycastOffsetContext {
    private RaycastOffsetContext() {
    }

    private static final ThreadLocal<Boolean> IN_RAYCAST = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static void enter() {
        IN_RAYCAST.set(Boolean.TRUE);
    }

    public static void exit() {
        IN_RAYCAST.set(Boolean.FALSE);
    }

    public static boolean isInRaycast() {
        return IN_RAYCAST.get();
    }
}
