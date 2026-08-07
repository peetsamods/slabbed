package com.slabbed.util;

import java.lang.reflect.Method;

import com.slabbed.dev.SlabbedDiagnostics;
import net.minecraft.util.math.BlockPos;

/**
 * Reflection bridge to dev/test-only audit classes under {@code com.slabbed.dev.audit.**}
 * and {@code com.slabbed.debug.**} — both packages are excluded from the release jar (see
 * build.gradle's pre-release hygiene gate: these write files to disk and are dev/test
 * harnesses, not player-facing tooling, so they don't ship regardless of any redaction
 * fix already applied to them). Every method here is safe to call unconditionally from
 * always-shipped code — it silently no-ops if the target class is absent.
 *
 * <p>The recorder surface ({@link #isRecorderEnabled()} in particular) is polled every
 * client tick by {@code SlabdyClientCommands}, so its {@link Method} handles are resolved
 * ONCE (a cached {@code Class<?>}, not a per-call {@code Class.forName}) — a per-tick
 * reflection-lookup-by-string-name would reintroduce the same class of per-frame
 * reflection cost that has already shipped as a real lag bug twice on this project (see
 * the PERF hygiene gate memory). {@link #invoke} stays
 * Class.forName-per-call since it only fires on block-placement events, not every tick.
 */
public final class SlabbedAuditBridge {

    private static final String AUDIT_CLASS_NAME =
            "com.slabbed.dev.audit.LoweredSideLiveHitRemapRuntimeAudit";
    private static final String RECORDER_CLASS_NAME =
            "com.slabbed.dev.audit.LiveCursorIntentRecorder";

    /** Enable with JVM arg: {@code -Dslabbed.debug.sbsb=true}. */
    private static final boolean ENABLED = Boolean.getBoolean("slabbed.debug.sbsb");

    private static final Class<?> RECORDER_CLASS = resolveRecorderClass();

    private static Class<?> resolveRecorderClass() {
        try {
            return Class.forName(RECORDER_CLASS_NAME);
        } catch (ClassNotFoundException | LinkageError e) {
            // Dev-only, excluded from the release jar — this is the expected shape of a
            // release build, not an error.
            return null;
        }
    }

    private SlabbedAuditBridge() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static boolean isRecorderEnabled() {
        if (RECORDER_CLASS == null) {
            return false;
        }
        try {
            return (boolean) RECORDER_CLASS.getMethod("isEnabled").invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static void bootstrapLiveRecorder() {
        if (RECORDER_CLASS == null) {
            return;
        }
        try {
            RECORDER_CLASS.getMethod("bootstrap").invoke(null);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    /** {@code /slabdy record} toggle. Returns the new enabled state, or {@code false} if unavailable. */
    public static boolean toggleRecorder() {
        if (RECORDER_CLASS == null) {
            return false;
        }
        try {
            return (boolean) RECORDER_CLASS.getMethod("toggle").invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    /** Display path for the recorder's current log file, or a fallback message if unavailable. */
    public static String recorderLogPathDisplay() {
        if (RECORDER_CLASS == null) {
            return "unavailable (dev-only tool, not present in this build)";
        }
        try {
            return (String) RECORDER_CLASS.getMethod("currentLogPathDisplay").invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return "unavailable";
        }
    }

    /** Client-tick-frequency call: forwards to the recorder if present, else a cheap no-op. */
    public static void recordVisualDiagnostic(BlockPos pos, SlabbedDiagnostics.Sample sample) {
        if (RECORDER_CLASS == null) {
            return;
        }
        try {
            RECORDER_CLASS.getMethod("recordVisualDiagnostic", BlockPos.class, SlabbedDiagnostics.Sample.class)
                    .invoke(null, pos, sample);
        } catch (ReflectiveOperationException ignored) {
        }
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
        if (RECORDER_CLASS == null) {
            return;
        }
        try {
            Method method = RECORDER_CLASS.getMethod("isEnabled");
            if (!(boolean) method.invoke(null)) {
                return;
            }
            RECORDER_CLASS.getMethod(methodName, paramTypes).invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Recording-tool failure must never take gameplay down either way.
        }
    }

}
