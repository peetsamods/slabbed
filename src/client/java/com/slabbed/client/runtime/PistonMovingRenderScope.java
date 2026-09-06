package com.slabbed.client.runtime;

/** Marks nested pushed-block rendering whose destination dy is already owned by the dispatcher. */
public final class PistonMovingRenderScope {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private PistonMovingRenderScope() {
    }

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void exit() {
        int remaining = DEPTH.get() - 1;
        if (remaining <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(remaining);
        }
    }

    public static boolean suppressNestedDy() {
        return DEPTH.get() > 0;
    }
}
