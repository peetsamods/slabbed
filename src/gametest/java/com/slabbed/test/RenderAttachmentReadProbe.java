package com.slabbed.test;

/** A scoped worker assertion that render reads never enter live attachment backing storage. */
public final class RenderAttachmentReadProbe {
    private static final ThreadLocal<Boolean> FORBIDDEN = ThreadLocal.withInitial(() -> false);

    private RenderAttachmentReadProbe() {
    }

    public static void begin() {
        FORBIDDEN.set(true);
    }

    public static void end() {
        FORBIDDEN.remove();
    }

    public static void check() {
        if (FORBIDDEN.get()) {
            throw new AssertionError("render worker entered live attachment backing storage");
        }
    }
}
