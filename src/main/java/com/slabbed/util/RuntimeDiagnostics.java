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

public final class RuntimeDiagnostics {
    private static final String AUTHOR_RECORDER_CLASS_NAME =
            "com.slabbed.util." + "Beta4PlacementAuthorRecorder";
    private static final String AUDIT_CLASS_NAME =
            "com.slabbed.dev.audit.LoweredSideLiveHitRemapRuntimeAudit";
    private static final String DEBUG_PACKAGE_NAME = "com.slabbed." + "debug.";
    private static final String INSPECT_CLASS_NAME = DEBUG_PACKAGE_NAME + "SlabbedInspect";
    private static final String BS_FB_TRACE_CLASS_NAME = DEBUG_PACKAGE_NAME + "BsFbLiveTrace";
    private static final String BS_FB_TRACE_CLIENT_CLASS_NAME = "com.slabbed.client.debug.BsFbLiveTraceClient";
    private static final boolean ENABLED = Boolean.getBoolean("slabbed.debug.sbsb");

    private RuntimeDiagnostics() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static boolean isInspectEnabled() {
        return Boolean.getBoolean("slabbed.inspect") || Boolean.getBoolean("slabbed.b2.live.trace");
    }

    public static boolean isBsFbLiveTraceEnabled() {
        return Boolean.getBoolean("slabbed.bsfb.live.trace");
    }

    public static boolean compoundLivePathEnabled() {
        return Boolean.TRUE.equals(invoke(AUTHOR_RECORDER_CLASS_NAME, "compoundLivePathEnabled", new Class<?>[]{}));
    }

    public static void invoke(String methodName, Class<?>[] paramTypes, Object... args) {
        if (!ENABLED) {
            return;
        }
        invoke(AUDIT_CLASS_NAME, methodName, paramTypes, args);
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

    public static void recordUseHead(Identifier blockItemId, boolean heldIsSlab, ItemUsageContext context) {
        invoke(
                AUTHOR_RECORDER_CLASS_NAME,
                "recordUseHead",
                new Class<?>[]{Identifier.class, boolean.class, ItemUsageContext.class},
                blockItemId,
                heldIsSlab,
                context);
    }

    public static void recordPlace(
            String phase,
            Identifier blockItemId,
            boolean heldIsSlab,
            ItemPlacementContext context,
            ActionResult result,
            String finalization
    ) {
        invoke(
                AUTHOR_RECORDER_CLASS_NAME,
                "recordPlace",
                new Class<?>[]{String.class, Identifier.class, boolean.class, ItemPlacementContext.class, ActionResult.class, String.class},
                phase,
                blockItemId,
                heldIsSlab,
                context,
                result,
                finalization);
    }

    public static void recordAfterTick(
            Identifier blockItemId,
            boolean heldIsSlab,
            ItemPlacementContext context,
            ActionResult result,
            String finalization
    ) {
        invoke(
                AUTHOR_RECORDER_CLASS_NAME,
                "recordAfterTick",
                new Class<?>[]{Identifier.class, boolean.class, ItemPlacementContext.class, ActionResult.class, String.class},
                blockItemId,
                heldIsSlab,
                context,
                result,
                finalization);
    }

    public static void recordCompoundFinalization(
            String phase,
            Identifier blockItemId,
            boolean heldIsSlab,
            ItemPlacementContext context,
            ActionResult result,
            String branch,
            BlockPos sourcePos,
            boolean compoundSidecarBefore,
            boolean compoundSidecarAfter,
            boolean persistentAnchorBefore,
            boolean persistentAnchorAfter,
            String reason
    ) {
        invoke(
                AUTHOR_RECORDER_CLASS_NAME,
                "recordCompoundFinalization",
                new Class<?>[]{
                        String.class,
                        Identifier.class,
                        boolean.class,
                        ItemPlacementContext.class,
                        ActionResult.class,
                        String.class,
                        BlockPos.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        String.class
                },
                phase,
                blockItemId,
                heldIsSlab,
                context,
                result,
                branch,
                sourcePos,
                compoundSidecarBefore,
                compoundSidecarAfter,
                persistentAnchorBefore,
                persistentAnchorAfter,
                reason);
    }

    private static Object invoke(String className, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Class<?> targetClass = Class.forName(className);
            Method method = targetClass.getMethod(methodName, paramTypes);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError ignored) {
            return null;
        }
    }
}
