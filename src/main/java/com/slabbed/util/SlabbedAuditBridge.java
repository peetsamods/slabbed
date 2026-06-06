package com.slabbed.util;

import java.lang.reflect.Method;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Reflection bridge to the dev-only audit class
 * {@code com.slabbed.dev.audit.LoweredSideLiveHitRemapRuntimeAudit}.
 *
 * <p>The audit class is excluded from the public release jar. To keep
 * production mixins free of direct hard-link references, callers route
 * through this bridge. The bridge is gated by the {@code slabbed.debug.sbsb}
 * system property so production/default invocations are no-ops with no
 * class loading attempted.
 *
 * <p>If the audit class is missing (release jar) or any reflective failure
 * occurs, the call is silently dropped.
 */
public final class SlabbedAuditBridge {

    private static final String AUDIT_CLASS_NAME =
            "com.slabbed.dev.audit.LoweredSideLiveHitRemapRuntimeAudit";
    private static final String LIVE_RECORDER_CLASS_NAME =
            "com.slabbed.dev.audit.LiveCursorIntentRecorder";
    private static final String LIVE_TRACE_CLASS_NAME =
            "com.slabbed.debug.BsFbLiveTrace";

    /** Enable with JVM arg: {@code -Dslabbed.debug.sbsb=true}. */
    private static final boolean ENABLED = Boolean.getBoolean("slabbed.debug.sbsb");
    private static final boolean LIVE_RECORDER_ENABLED = Boolean.getBoolean("slabbed.liveCursorIntentRecorder");
    private static final boolean LIVE_TRACE_ENABLED = Boolean.getBoolean("slabbed.bsfb.live.trace");

    private SlabbedAuditBridge() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static boolean isRecorderEnabled() {
        return LIVE_RECORDER_ENABLED;
    }

    public static boolean isLiveTraceEnabled() {
        return LIVE_TRACE_ENABLED;
    }

    public static void bootstrapLiveRecorder() {
        invokeRecorder("bootstrap", new Class<?>[]{});
    }

    public static void invoke(String methodName, Class<?>[] paramTypes, Object... args) {
        if (!ENABLED) {
            return;
        }
        try {
            Class<?> auditClass = Class.forName(AUDIT_CLASS_NAME);
            Method method = auditClass.getMethod(methodName, paramTypes);
            method.invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Audit class is dev-only and excluded from the release jar.
        }
    }

    public static void invokeRecorder(String methodName, Class<?>[] paramTypes, Object... args) {
        if (!LIVE_RECORDER_ENABLED) {
            return;
        }
        try {
            Class<?> recorderClass = Class.forName(LIVE_RECORDER_CLASS_NAME);
            Method method = recorderClass.getMethod(methodName, paramTypes);
            method.invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Recorder class is dev-only and excluded from the release jar.
        }
    }

    public static void captureLiveTrace(World world, BlockPos supportPos, BlockPos fullPos, String label) {
        if (!LIVE_TRACE_ENABLED) {
            return;
        }
        try {
            Class<?> traceClass = Class.forName(LIVE_TRACE_CLASS_NAME);
            Method method = traceClass.getMethod(
                    "capture",
                    World.class,
                    BlockPos.class,
                    BlockPos.class,
                    String.class);
            method.invoke(null, world, supportPos, fullPos, label);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Live trace is dev-only and excluded from the release jar.
        }
    }
}
