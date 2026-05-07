package com.slabbed.util;

import java.lang.reflect.Method;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
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
    private static final String DEBUG_PACKAGE_NAME = "com.slabbed." + "debug.";
    private static final String INSPECT_CLASS_NAME = DEBUG_PACKAGE_NAME + "SlabbedInspect";
    private static final String BS_FB_TRACE_CLASS_NAME = DEBUG_PACKAGE_NAME + "BsFbLiveTrace";
    private static final String BS_FB_TRACE_CLIENT_CLASS_NAME = "com.slabbed.client.debug.BsFbLiveTraceClient";

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
        invoke(AUDIT_CLASS_NAME, methodName, paramTypes, args);
    }

    public static boolean isInspectEnabled() {
        return Boolean.getBoolean("slabbed.inspect") || Boolean.getBoolean("slabbed.b2.live.trace");
    }

    public static boolean isBsFbLiveTraceEnabled() {
        return Boolean.getBoolean("slabbed.bsfb.live.trace");
    }

    public static void logInspectSessionStart() {
        if (!isInspectEnabled()) {
            return;
        }
        invoke(INSPECT_CLASS_NAME, "logSessionStart", new Class<?>[]{});
    }

    public static void logInspectClientTarget(
            World world,
            Vec3d eye,
            Vec3d rayEnd,
            float yaw,
            float pitch,
            ItemStack held,
            HitResult initialTarget,
            HitResult finalTarget,
            String decision,
            boolean sideSlabRetargetFired
    ) {
        if (!isInspectEnabled()) {
            return;
        }
        invoke(
                INSPECT_CLASS_NAME,
                "logClientTarget",
                new Class<?>[]{
                        World.class,
                        Vec3d.class,
                        Vec3d.class,
                        float.class,
                        float.class,
                        ItemStack.class,
                        HitResult.class,
                        HitResult.class,
                        String.class,
                        boolean.class
                },
                world,
                eye,
                rayEnd,
                yaw,
                pitch,
                held,
                initialTarget,
                finalTarget,
                decision,
                sideSlabRetargetFired);
    }

    public static void logInspectIntent(ItemUsageContext incoming, ItemUsageContext outgoing, String reason) {
        if (!isInspectEnabled()) {
            return;
        }
        invoke(
                INSPECT_CLASS_NAME,
                "logIntent",
                new Class<?>[]{ItemUsageContext.class, ItemUsageContext.class, String.class},
                incoming,
                outgoing,
                reason);
    }

    public static void logInspectClickPair(ItemUsageContext context, Identifier itemId) {
        if (!isInspectEnabled()) {
            return;
        }
        invoke(
                INSPECT_CLASS_NAME,
                "logClickPair",
                new Class<?>[]{ItemUsageContext.class, Identifier.class},
                context,
                itemId);
    }

    public static void clearInspectClickPair() {
        if (!isInspectEnabled()) {
            return;
        }
        invoke(INSPECT_CLASS_NAME, "clearClickPair", new Class<?>[]{});
    }

    public static void logInspectPlacement(
            String stage,
            World world,
            Identifier itemId,
            ItemPlacementContext ctx,
            BlockPos hitPos,
            BlockPos placePos,
            ActionResult result
    ) {
        if (!isInspectEnabled()) {
            return;
        }
        invoke(
                INSPECT_CLASS_NAME,
                "logPlacement",
                new Class<?>[]{
                        String.class,
                        World.class,
                        Identifier.class,
                        ItemPlacementContext.class,
                        BlockPos.class,
                        BlockPos.class,
                        ActionResult.class
                },
                stage,
                world,
                itemId,
                ctx,
                hitPos,
                placePos,
                result);
    }

    public static void logInspectPlacementNoReturn(
            World world,
            Identifier itemId,
            Direction face,
            BlockPos hitPos,
            BlockPos placePos
    ) {
        if (!isInspectEnabled()) {
            return;
        }
        invoke(
                INSPECT_CLASS_NAME,
                "logPlacementNoReturn",
                new Class<?>[]{World.class, Identifier.class, Direction.class, BlockPos.class, BlockPos.class},
                world,
                itemId,
                face,
                hitPos,
                placePos);
    }

    public static void initBsFbLiveTraceClient() {
        if (!isBsFbLiveTraceEnabled()) {
            return;
        }
        invoke(BS_FB_TRACE_CLIENT_CLASS_NAME, "init", new Class<?>[]{});
    }

    public static void captureBsFbLiveTrace(World world, BlockPos supportPos, BlockPos fullPos, String label) {
        if (!isBsFbLiveTraceEnabled()) {
            return;
        }
        invoke(
                BS_FB_TRACE_CLASS_NAME,
                "capture",
                new Class<?>[]{World.class, BlockPos.class, BlockPos.class, String.class},
                world,
                supportPos,
                fullPos,
                label);
    }

    public static void captureClientBsFbLiveTrace(BlockPos supportPos, BlockPos fullPos, String label) {
        if (!isBsFbLiveTraceEnabled()) {
            return;
        }
        invoke(
                BS_FB_TRACE_CLASS_NAME,
                "captureClient",
                new Class<?>[]{BlockPos.class, BlockPos.class, String.class},
                supportPos,
                fullPos,
                label);
    }

    private static void invoke(String className, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Class<?> auditClass = Class.forName(className);
            Method method = auditClass.getMethod(methodName, paramTypes);
            method.invoke(null, args);
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError ignored) {
            // Audit class is dev-only and excluded from the release jar.
        }
    }
}
