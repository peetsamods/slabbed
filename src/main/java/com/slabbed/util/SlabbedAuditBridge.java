package com.slabbed.util;

import java.lang.reflect.Method;

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

    /** Enable with JVM arg: {@code -Dslabbed.debug.sbsb=true}. */
    private static final boolean ENABLED = Boolean.getBoolean("slabbed.debug.sbsb");

    private SlabbedAuditBridge() {
    }

    public static boolean isEnabled() {
        return ENABLED;
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
}
