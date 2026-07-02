package com.slabbed.util;

import java.lang.reflect.Method;

import com.slabbed.dev.audit.LiveCursorIntentRecorder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Reflection bridge to the dev-only audit class
 * {@code com.slabbed.dev.audit.LoweredSideLiveHitRemapRuntimeAudit}, plus (historically)
 * a reflective path to the recorder — kept generic/reflective for the audit class and
 * {@code BsFbLiveTrace} (both {@code com/slabbed/debug/**}, still excluded from the
 * release jar), but {@link #invokeRecorder} now hard-references
 * {@link LiveCursorIntentRecorder} directly: {@code com/slabbed/dev/**} always ships
 * (see build.gradle), so there is no longer a "class might be absent" case to guard
 * against for the recorder specifically, and gating on a stale JVM-property snapshot
 * would have made {@code /slabdy record}'s runtime toggle silently do nothing.
 *
 * <p>If the audit class is missing (release jar) or any reflective failure
 * occurs, the call is silently dropped.
 */
public final class SlabbedAuditBridge {

    private static final String AUDIT_CLASS_NAME =
            "com.slabbed.dev.audit.LoweredSideLiveHitRemapRuntimeAudit";
    private static final String LIVE_TRACE_CLASS_NAME =
            "com.slabbed.debug.BsFbLiveTrace";

    /** Enable with JVM arg: {@code -Dslabbed.debug.sbsb=true}. */
    private static final boolean ENABLED = Boolean.getBoolean("slabbed.debug.sbsb");
    private static final boolean LIVE_TRACE_ENABLED = Boolean.getBoolean("slabbed.bsfb.live.trace");

    private SlabbedAuditBridge() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static boolean isRecorderEnabled() {
        return LiveCursorIntentRecorder.isEnabled();
    }

    public static boolean isLiveTraceEnabled() {
        return LIVE_TRACE_ENABLED;
    }

    public static void bootstrapLiveRecorder() {
        LiveCursorIntentRecorder.bootstrap();
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
        if (!LiveCursorIntentRecorder.isEnabled()) {
            return;
        }
        try {
            Method method = LiveCursorIntentRecorder.class.getMethod(methodName, paramTypes);
            method.invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Should not happen now that the class is a hard reference above, but a
            // recording-tool failure must never take gameplay down either way.
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
