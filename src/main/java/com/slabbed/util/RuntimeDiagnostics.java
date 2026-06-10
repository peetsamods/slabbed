package com.slabbed.util;

import java.lang.reflect.Method;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.BlockView;
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
    private static final String BETA4_MANUAL_LIVE_TRACE_CLASS_NAME =
            "com.slabbed.util." + "Beta4ManualLiveTrace";
    private static final String BETA35_FENCE_WALL_INSPECT_RECORDER_CLASS_NAME =
            "com.slabbed.util." + "Beta35FenceWallLiveInspectRecorder";
    private static final String BETA35_SLAB_HEIGHT_RECORDER_CLASS_NAME =
            "com.slabbed.util." + "Beta35SlabHeightHitAcceptanceRecorder";
    private static final String BETA35_LIVE_TORCH_RECORDER_CLASS_NAME =
            "com.slabbed.util." + "Beta35LiveTorchCaptureRecorder";
    private static final String BETA35_SLAB_JUMP_RECORDER_CLASS_NAME =
            "com.slabbed.util." + "Beta35SlabJumpSourceTruthRecorder";
    private static final String MODEL_DY_TRACE_BRIDGE_CLASS_NAME =
            "com.slabbed.client.runtime.ModelDyTranslateTraceBridge";
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

    public static void logManualPlacementIntent(ItemUsageContext incoming, ItemUsageContext outgoing, String reason) {
        invoke(
                BETA4_MANUAL_LIVE_TRACE_CLASS_NAME,
                "logPlacementIntent",
                new Class<?>[]{ItemUsageContext.class, ItemUsageContext.class, String.class},
                incoming,
                outgoing,
                reason);
    }

    public static void logManualServerTolerance(
            World world,
            BlockHitResult hit,
            ItemStack heldStack,
            Vec3d centerBefore,
            Vec3d centerAfter,
            String reason
    ) {
        invoke(
                BETA4_MANUAL_LIVE_TRACE_CLASS_NAME,
                "logServerTolerance",
                new Class<?>[]{World.class, BlockHitResult.class, ItemStack.class, Vec3d.class, Vec3d.class, String.class},
                world,
                hit,
                heldStack,
                centerBefore,
                centerAfter,
                reason);
    }

    public static void logSlabSupportDecision(
            BlockView world,
            BlockPos sourcePos,
            BlockState sourceState,
            Direction intendedDirection,
            Vec3d hitPos,
            Object decision
    ) {
        if (decision == null) {
            return;
        }
        invoke(
                BETA4_MANUAL_LIVE_TRACE_CLASS_NAME,
                "logSlabSupportDecision",
                new Class<?>[]{BlockView.class, BlockPos.class, BlockState.class, Direction.class, Vec3d.class, decision.getClass()},
                world,
                sourcePos,
                sourceState,
                intendedDirection,
                hitPos,
                decision);
    }

    public static void recordFenceWallClientTarget(
            World world,
            Entity camera,
            PlayerEntity player,
            Vec3d eye,
            Vec3d rayEnd,
            ItemStack held,
            HitResult initialTarget,
            HitResult finalTarget,
            String finalDecision
    ) {
        invoke(
                BETA35_FENCE_WALL_INSPECT_RECORDER_CLASS_NAME,
                "recordClientTarget",
                new Class<?>[]{World.class, Entity.class, PlayerEntity.class, Vec3d.class, Vec3d.class, ItemStack.class, HitResult.class, HitResult.class, String.class},
                world,
                camera,
                player,
                eye,
                rayEnd,
                held,
                initialTarget,
                finalTarget,
                finalDecision);
    }

    public static void logFenceWallServerTolerance(
            World world,
            PlayerEntity player,
            BlockHitResult hit,
            ItemStack held,
            Vec3d validationCenter,
            Vec3d shiftedValidationCenter
    ) {
        invoke(
                BETA35_FENCE_WALL_INSPECT_RECORDER_CLASS_NAME,
                "logServerTolerance",
                new Class<?>[]{World.class, PlayerEntity.class, BlockHitResult.class, ItemStack.class, Vec3d.class, Vec3d.class},
                world,
                player,
                hit,
                held,
                validationCenter,
                shiftedValidationCenter);
    }

    public static void recordSlabHeightClientTarget(
            World world,
            Entity camera,
            PlayerEntity player,
            Vec3d eye,
            Vec3d rayEnd,
            ItemStack held,
            HitResult initialTarget,
            HitResult finalTarget,
            String finalDecision
    ) {
        invoke(
                BETA35_SLAB_HEIGHT_RECORDER_CLASS_NAME,
                "recordClientTarget",
                new Class<?>[]{World.class, Entity.class, PlayerEntity.class, Vec3d.class, Vec3d.class, ItemStack.class, HitResult.class, HitResult.class, String.class},
                world,
                camera,
                player,
                eye,
                rayEnd,
                held,
                initialTarget,
                finalTarget,
                finalDecision);
    }

    public static void logSlabHeightServerTolerance(
            World world,
            PlayerEntity player,
            BlockHitResult hit,
            ItemStack held,
            Vec3d validationCenter,
            Vec3d shiftedValidationCenter
    ) {
        invoke(
                BETA35_SLAB_HEIGHT_RECORDER_CLASS_NAME,
                "logServerTolerance",
                new Class<?>[]{World.class, PlayerEntity.class, BlockHitResult.class, ItemStack.class, Vec3d.class, Vec3d.class},
                world,
                player,
                hit,
                held,
                validationCenter,
                shiftedValidationCenter);
    }

    public static void recordLiveTorchComfortTrace(World world, String reason, BlockPos pos, BlockPos supportPos) {
        invoke(
                BETA35_LIVE_TORCH_RECORDER_CLASS_NAME,
                "recordComfortTrace",
                new Class<?>[]{World.class, String.class, BlockPos.class, BlockPos.class},
                world,
                reason,
                pos,
                supportPos);
    }

    public static boolean beta35SlabJumpSourceTruthEnabled() {
        return Boolean.TRUE.equals(invoke(BETA35_SLAB_JUMP_RECORDER_CLASS_NAME, "isEnabled", new Class<?>[]{}));
    }

    public static void recordBeta35SlabJumpAnchorEvent(
            World world,
            String action,
            AttachmentType<LongOpenHashSet> type,
            BlockPos pos,
            BlockState oldState,
            BlockState newState
    ) {
        try {
            Class<?> targetClass = Class.forName(BETA35_SLAB_JUMP_RECORDER_CLASS_NAME);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object eventAction = Enum.valueOf((Class<Enum>) Class.forName(
                    BETA35_SLAB_JUMP_RECORDER_CLASS_NAME + "$EventAction"), action);
            Method method = targetClass.getMethod(
                    "recordAnchorEvent",
                    World.class,
                    eventAction.getClass(),
                    AttachmentType.class,
                    BlockPos.class,
                    BlockState.class,
                    BlockState.class);
            method.invoke(null, world, eventAction, type, pos, oldState, newState);
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError ignored) {
            // Recorder class is excluded from the release jar.
        }
    }

    public static void recordModelDyTrace(
            String method,
            BlockRenderView world,
            BlockPos pos,
            BlockState state,
            double dy
    ) {
        invoke(
                MODEL_DY_TRACE_BRIDGE_CLASS_NAME,
                "record",
                new Class<?>[]{String.class, BlockRenderView.class, BlockPos.class, BlockState.class, double.class},
                method,
                world,
                pos,
                state,
                dy);
    }

    public static void recordBeta4ModelDyTrace(
            String method,
            BlockRenderView world,
            BlockPos pos,
            BlockState state,
            double dy
    ) {
        invoke(
                MODEL_DY_TRACE_BRIDGE_CLASS_NAME,
                "recordBeta4ModelDy",
                new Class<?>[]{String.class, BlockRenderView.class, BlockPos.class, BlockState.class, double.class},
                method,
                world,
                pos,
                state,
                dy);
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
