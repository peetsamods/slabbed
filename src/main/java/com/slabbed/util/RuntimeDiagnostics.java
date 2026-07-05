package com.slabbed.util;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final String LIVE_CURSOR_INTENT_RECORDER_CLASS_NAME =
            "com.slabbed.dev.audit.LiveCursorIntentRecorder";
    // The enriched visual-diagnostic Sample record. Like the recorder it lives in the
    // release-stripped com.slabbed.dev tree, so an always-shipped class must never hard-import
    // it — recordVisualDiagnostic(...) takes a plain Object and resolves both classes reflectively.
    private static final String SLABBED_DIAGNOSTICS_SAMPLE_CLASS_NAME =
            "com.slabbed.dev.SlabbedDiagnostics$Sample";
    private static final boolean ENABLED = Boolean.getBoolean("slabbed.debug.sbsb");
    // Off-by-default in release. Gates the per-block model-dy recorder so the hot render
    // path never builds reflection args or dispatches a call when tracing is disabled.
    private static final boolean BETA4_MODEL_DY_RECORDER = Boolean.getBoolean("slabbed.beta4ModelDyRecorder");
    // Off-by-default in release. Gates recordModelDyTrace (called once per BlockModelRenderer
    // render call by BlockModelDyTranslateMixin) so the hot render path never builds the
    // reflective Class[]/varargs arrays when no trace lane is armed. Cached at class init:
    // nothing in the compiled gametest include list setPropertys either gate flag at runtime
    // (cache-vs-live audit 2026-07-02). Re-audit before re-including any SlabbedLab* client
    // test that drives ModelDyTranslateTraceBridge.reset/snapshot — that lane needs
    // -Dslabbed.modelDyTrace=true at JVM start once this gate is in place.
    private static final boolean MODEL_DY_TRACE = Boolean.getBoolean("slabbed.modelDyTrace");
    // Several diagnostic sidecar classes are intentionally stripped from the release jar,
    // so Class.forName(...) on them throws ClassNotFoundException. Without this cache the
    // throw happened on EVERY per-block invoke() call — a render-thread exception storm that
    // was the 0.4.0-beta.3 lag regression. Record the miss once and short-circuit thereafter.
    private static final Set<String> ABSENT_CLASSES = ConcurrentHashMap.newKeySet();

    // The LiveCursorIntentRecorder (dev-only, excluded from the release jar) is resolved ONCE, not
    // per-call: isRecorderEnabled() is polled every client tick by the /slabdy record command surface,
    // so a per-tick Class.forName-by-string would reintroduce the exact per-frame-reflection lag class
    // that has shipped twice on this project (PERF hygiene gate). A null RECORDER_CLASS is the expected
    // shape of a release build; every recorder-forwarding method below no-ops cheaply when it is null.
    private static final Class<?> RECORDER_CLASS = resolveRecorderClass();

    private static Class<?> resolveRecorderClass() {
        try {
            return Class.forName(LIVE_CURSOR_INTENT_RECORDER_CLASS_NAME);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

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

    // ---- LiveCursorIntentRecorder forwarding (dev-only; no-ops when the recorder is excluded) ----
    // Mirrors the sibling 1.21.11 branch's SlabbedAuditBridge recorder surface. The recorder gates
    // itself (its own isEnabled()/toggle()); these forward reflectively so always-shipped code carries
    // no hard reference to the release-excluded recorder class.

    /** Polled every client tick by the /slabdy record command surface — RECORDER_CLASS is cached, not re-resolved. */
    public static boolean isRecorderEnabled() {
        if (RECORDER_CLASS == null) {
            return false;
        }
        try {
            return (boolean) RECORDER_CLASS.getMethod("isEnabled").invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    public static void bootstrapLiveRecorder() {
        if (RECORDER_CLASS == null) {
            return;
        }
        try {
            RECORDER_CLASS.getMethod("bootstrap").invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    /** {@code /slabdy record} toggle. Returns the new enabled state, or {@code false} if unavailable. */
    public static boolean toggleRecorder() {
        if (RECORDER_CLASS == null) {
            return false;
        }
        try {
            return (boolean) RECORDER_CLASS.getMethod("toggle").invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    /** Display path for the recorder's current log directory, or a fallback message if unavailable. */
    public static String recorderLogPathDisplay() {
        if (RECORDER_CLASS == null) {
            return "unavailable (dev-only tool, not present in this build)";
        }
        try {
            return (String) RECORDER_CLASS.getMethod("currentLogPathDisplay").invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return "unavailable";
        }
    }

    /**
     * Additive recorder dispatch used by the trace mixins ALONGSIDE {@link #invoke} (which routes to
     * {@code LoweredSideLiveHitRemapRuntimeAudit}). Gated on the recorder's own {@code isEnabled()} so a
     * disabled recorder pays only one cached reflective read; the RECORDER_CLASS handle itself is
     * resolved once at class init, never per-call.
     */
    public static void invokeRecorder(String methodName, Class<?>[] paramTypes, Object... args) {
        if (RECORDER_CLASS == null) {
            return;
        }
        try {
            if (!(boolean) RECORDER_CLASS.getMethod("isEnabled").invoke(null)) {
                return;
            }
            RECORDER_CLASS.getMethod(methodName, paramTypes).invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Recording-tool failure must never take gameplay down.
        }
    }

    // Resolved ONCE (both classes are release-stripped; null in a release build). Caching the
    // Sample class AND the resolved Method avoids a per-client-tick Class.forName/getMethod on the
    // /slabdy record diagnostic lane — the same per-frame-reflection lag class this file already
    // guards RECORDER_CLASS against.
    private static final Class<?> DIAGNOSTICS_SAMPLE_CLASS = resolveDiagnosticsSampleClass();
    private static final Method RECORD_VISUAL_DIAGNOSTIC_METHOD = resolveRecordVisualDiagnosticMethod();

    private static Class<?> resolveDiagnosticsSampleClass() {
        try {
            return Class.forName(SLABBED_DIAGNOSTICS_SAMPLE_CLASS_NAME);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static Method resolveRecordVisualDiagnosticMethod() {
        if (RECORDER_CLASS == null || DIAGNOSTICS_SAMPLE_CLASS == null) {
            return null;
        }
        try {
            return RECORDER_CLASS.getMethod("recordVisualDiagnostic", BlockPos.class, DIAGNOSTICS_SAMPLE_CLASS);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /**
     * The enriched {@code /slabdy record} visual-diagnostic capture. {@code sample} is a
     * {@code com.slabbed.dev.SlabbedDiagnostics.Sample} passed as a plain {@link Object} so this
     * always-shipped class carries no compile-time reference to the release-stripped record type.
     * No-ops when the recorder or Sample class is absent (a release build) or the recorder is
     * disabled — the caller ({@code SlabbedClient}'s per-tick target-change poll) is the only
     * caller and already gates on {@link #isRecorderEnabled()}, but this re-checks defensively.
     */
    public static void recordVisualDiagnostic(BlockPos pos, Object sample) {
        if (RECORD_VISUAL_DIAGNOSTIC_METHOD == null || pos == null || sample == null) {
            return;
        }
        try {
            if (!(boolean) RECORDER_CLASS.getMethod("isEnabled").invoke(null)) {
                return;
            }
            RECORD_VISUAL_DIAGNOSTIC_METHOD.invoke(null, pos, sample);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            // Recording-tool failure must never take gameplay down.
        }
    }

    /** True only in a dev build where the SlabbedDiagnostics analysis layer is actually present. */
    public static boolean isVisualDiagnosticsAvailable() {
        return RECORD_VISUAL_DIAGNOSTIC_METHOD != null;
    }

    private static final Class<?> DIAGNOSTICS_CLASS = resolveDiagnosticsClass();
    private static final Method ANALYZE_METHOD = resolveAnalyzeMethod();

    private static Class<?> resolveDiagnosticsClass() {
        try {
            return Class.forName("com.slabbed.dev.SlabbedDiagnostics");
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static Method resolveAnalyzeMethod() {
        if (DIAGNOSTICS_CLASS == null) {
            return null;
        }
        try {
            return DIAGNOSTICS_CLASS.getMethod("analyze",
                    BlockView.class, BlockPos.class, BlockState.class, double.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /**
     * Build a {@code SlabbedDiagnostics.Sample} for {@code (world,pos,state)} at the given render
     * {@code modelDy}, returned as a plain {@link Object} (the record type is release-stripped).
     * Returns {@code null} in a release build or on any reflective failure — the caller then simply
     * records nothing. Both this and {@link #recordVisualDiagnostic(BlockPos, Object)} keep every
     * reference to the stripped {@code com.slabbed.dev} tree inside this always-shipped forwarder,
     * so the always-shipped {@code /slabdy record} caller stays link-clean.
     */
    public static Object analyzeVisualDiagnostic(BlockView world, BlockPos pos, BlockState state, double modelDy) {
        if (ANALYZE_METHOD == null || world == null || pos == null || state == null) {
            return null;
        }
        try {
            return ANALYZE_METHOD.invoke(null, world, pos, state, modelDy);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static final Method FLAG_SUMMARY_METHOD = resolveSampleMethod("flagSummary");
    private static final Method ANY_SUSPECT_METHOD = resolveSampleMethod("anySuspect");

    private static Method resolveSampleMethod(String name) {
        if (DIAGNOSTICS_SAMPLE_CLASS == null) {
            return null;
        }
        try {
            return DIAGNOSTICS_SAMPLE_CLASS.getMethod(name);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /**
     * The comma-separated red-flag summary of a {@code SlabbedDiagnostics.Sample} ({@code sample}
     * passed as {@link Object}), or {@code null} if the sample tripped no flags / the diagnostics
     * layer is absent. Lets the always-shipped {@code /slabdy} overlay surface flags without linking
     * the stripped record type.
     */
    public static String visualDiagnosticFlagSummary(Object sample) {
        if (FLAG_SUMMARY_METHOD == null || ANY_SUSPECT_METHOD == null || sample == null) {
            return null;
        }
        try {
            if (!(boolean) ANY_SUSPECT_METHOD.invoke(sample)) {
                return null;
            }
            return (String) FLAG_SUMMARY_METHOD.invoke(sample);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            return null;
        }
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
        // Same negative-cache rule as invoke(): the recorder is excluded from the release
        // jar, so without this check EVERY anchor add/remove event paid a fresh
        // ClassNotFoundException throw — the exact 0.4.0-beta.3 exception-storm shape.
        if (ABSENT_CLASSES.contains(BETA35_SLAB_JUMP_RECORDER_CLASS_NAME)) {
            return;
        }
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
        } catch (ClassNotFoundException notFound) {
            // Class is excluded from this jar; remember so we never pay the throw again.
            ABSENT_CLASSES.add(BETA35_SLAB_JUMP_RECORDER_CLASS_NAME);
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
        // The bridge's record() feeds two lanes: the beta4 model-dy recorder and the
        // tracePos snapshot lane. Bail before building the reflective arg arrays unless
        // one of them is armed — this runs once per block render call.
        if (!MODEL_DY_TRACE && !BETA4_MODEL_DY_RECORDER) {
            return;
        }
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
        if (!BETA4_MODEL_DY_RECORDER) {
            return;
        }
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
        if (ABSENT_CLASSES.contains(className)) {
            return null;
        }
        try {
            Class<?> targetClass = Class.forName(className);
            Method method = targetClass.getMethod(methodName, paramTypes);
            return method.invoke(null, args);
        } catch (ClassNotFoundException notFound) {
            // Class is excluded from this jar; remember so we never pay the throw again.
            ABSENT_CLASSES.add(className);
            return null;
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError ignored) {
            return null;
        }
    }
}
