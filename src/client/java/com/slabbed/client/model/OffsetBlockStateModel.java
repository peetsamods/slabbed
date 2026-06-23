package com.slabbed.client.model;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.util.RuntimeDiagnostics;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Wraps a BakedModel to apply a vertical offset to emitted quads
 * (e.g., torches on bottom slabs) without relying on PoseStack hacks.
 */
@SuppressWarnings({"RedundantSuppression", "DataFlowIssue"})
public final class OffsetBlockStateModel extends ForwardingBakedModel {
    private static volatile BlockPos slabbed$tracePos = null;
    private static volatile RenderOffsetSample slabbed$lastTrace = RenderOffsetSample.missing();
    private static volatile BlockPos slabbed$modelDyOwnerTracePos = null;
    private static volatile ModelDyOwnerSample slabbed$modelDyOwnerLastTrace = ModelDyOwnerSample.missing();
    private static volatile BlockPos slabbed$fullMeshBoundsTracePos = null;
    private static volatile FullMeshBoundsSample slabbed$fullMeshBoundsLastTrace = FullMeshBoundsSample.missing();
    private static volatile BlockPos slabbed$stepCullTracePos = null;
    private static volatile StepCullSample slabbed$stepCullLastTrace = StepCullSample.missing();
    private static final Set<String> slabbed$mc1211FullMeshBoundsSampleRows = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger slabbed$mc1211FullMeshBoundsPassSequence = new AtomicInteger();
    private static final Set<String> slabbed$mc1211LiveModelTraceRows = ConcurrentHashMap.newKeySet();
    private static volatile boolean slabbed$mc1211LiveModelTraceCanaryLogged = false;
    private static volatile boolean slabbed$mc1211LiveModelTraceParseFailureLogged = false;
    private static volatile boolean slabbed$mc1211LiveModelTraceLimitLogged = false;
    private static volatile int slabbed$mc1211LiveModelTraceRowCount = 0;
    private static volatile int slabbed$mc1211LiveModelTraceSkippedCount = 0;
    private static volatile int slabbed$mc1211LiveModelTraceSkippedByBlockFilterCount = 0;
    private static volatile int slabbed$mc1211LiveModelTraceSkippedByRadiusCount = 0;
    private static volatile int slabbed$mc1211LiveModelTraceMatchesOutlineCount = 0;
    private static volatile int slabbed$mc1211LiveModelTraceLowerThanOutlineCount = 0;
    private static volatile int slabbed$mc1211LiveModelTraceHigherThanOutlineCount = 0;
    private static volatile int slabbed$mc1211LiveModelTraceOutlineUnavailableCount = 0;
    private static volatile int slabbed$mc1211LiveModelTraceNotVideoEquivalentCount = 0;

    public OffsetBlockStateModel(BakedModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    public record RenderOffsetSample(
            boolean seen,
            String viewClass,
            String pos,
            String state,
            double modelDy,
            double clientDy,
            double slabSupportDy,
            boolean excludedByWrapper
    ) {
        static RenderOffsetSample missing() {
            return new RenderOffsetSample(false, "none", "none", "none", 0.0, 0.0, 0.0, false);
        }
    }

    public record ModelDyOwnerSample(
            boolean seen,
            String viewClass,
            String pos,
            String state,
            int emitCalls,
            int appliedCalls,
            double totalAppliedDy,
            double lastDy
    ) {
        static ModelDyOwnerSample missing() {
            return new ModelDyOwnerSample(false, "none", "none", "none", 0, 0, 0.0, 0.0);
        }
    }

    public record FullMeshBoundsSample(
            boolean seen,
            String meshTraceKey,
            String matrixKey,
            String matrixRow,
            String blockId,
            String pos,
            String state,
            double dy,
            String modelClass,
            String viewClass,
            String renderOutlineBounds,
            String tickOrFrame,
            int passSequence,
            int totalQuadsSeen,
            int verticesVisited,
            double minBeforeY,
            double maxBeforeY,
            double minAfterY,
            double maxAfterY,
            String reason,
            String rowSource,
            String snapshotSource,
            String aggregateDedupKey
    ) {
        static FullMeshBoundsSample missing() {
            return new FullMeshBoundsSample(
                    false,
                    "none",
                    "none",
                    "UNKNOWN",
                    "none",
                    "none",
                    "none",
                    Double.NaN,
                    "none",
                    "none",
                    "empty",
                    "unknown",
                    0,
                    0,
                    0,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    "missing",
                    "FULL_MESH_SNAPSHOT",
                    "missing",
                    "none");
        }
    }

    public record StepCullSample(
            boolean seen,
            String viewClass,
            String pos,
            String state,
            double dy,
            boolean clearStepCullFaces,
            int totalQuadsSeen,
            int cullFacesSeen,
            int stepFacesSeen,
            int stepCullFacesCleared,
            String clearedFaces,
            String reason
    ) {
        public static StepCullSample missing() {
            return new StepCullSample(
                    false,
                    "none",
                    "none",
                    "none",
                    Double.NaN,
                    false,
                    0,
                    0,
                    0,
                    0,
                    "none",
                    "missing");
        }
    }

    public static void resetRenderOffsetSample(BlockPos pos) {
        slabbed$tracePos = pos;
        slabbed$lastTrace = RenderOffsetSample.missing();
    }

    public static RenderOffsetSample snapshotRenderOffsetSample() {
        return slabbed$lastTrace;
    }

    public static void resetModelDyOwnerSample(BlockPos pos) {
        slabbed$modelDyOwnerTracePos = pos == null ? null : pos.immutable();
        slabbed$modelDyOwnerLastTrace = ModelDyOwnerSample.missing();
    }

    public static ModelDyOwnerSample snapshotModelDyOwnerSample() {
        return slabbed$modelDyOwnerLastTrace;
    }

    public static void resetFullMeshBoundsSample(BlockPos pos) {
        slabbed$fullMeshBoundsTracePos = pos == null ? null : pos.immutable();
        slabbed$fullMeshBoundsLastTrace = FullMeshBoundsSample.missing();
    }

    public static FullMeshBoundsSample snapshotFullMeshBoundsSample() {
        return slabbed$fullMeshBoundsLastTrace;
    }

    public static void resetStepCullSample(BlockPos pos) {
        slabbed$stepCullTracePos = pos == null ? null : pos.immutable();
        slabbed$stepCullLastTrace = StepCullSample.missing();
    }

    public static StepCullSample snapshotStepCullSample() {
        return slabbed$stepCullLastTrace;
    }

    /**
     * Fabric renderer entry point used by Indigo/Sodium+Indium.
     */
    @Override
    public void emitBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier,
                               RenderContext context) {
        float sourceDy;
        String dySourcePath;
        float dy;
        if (state.getBlock() instanceof CarpetBlock) {
            sourceDy = (float) ClientDy.dyFor(view, pos, state);
            dySourcePath = "fabricEmitBlockQuads:ClientDy:carpet";
            dy = sourceDy;
        } else {
            sourceDy = (float) SlabSupport.getYOffset(view, pos, state);
            dySourcePath = "fabricEmitBlockQuads:SlabSupport";
            dy = sourceDy;
            if (dy != 0.0f) {
                // Prevent visual connection offsets for fences/walls/panes,
                // except for the explicitly proven Beta 3.5 fence/wall variants.
                if (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof IronBarsBlock) {
                    if (!SlabSupport.isBeta35FenceWallVariantContactObject(state)) {
                        dy = 0.0f;
                        dySourcePath = dySourcePath + ":visualConnectionExcluded";
                    }
                }
            }
        }
        RuntimeDiagnostics.recordBeta4ModelDyTrace("fabricEmitQuads", view, pos, state, dy);
        slabbed$logCompoundVisibleRenderTraceModelDy(view, pos, state, dy);
        slabbed$logMc1211LiveModelTrace(view, pos, state, dySourcePath, sourceDy, dy);

        BlockPos modelDyTracePos = slabbed$modelDyOwnerTracePos;
        if (modelDyTracePos != null && modelDyTracePos.equals(pos)) {
            ModelDyOwnerSample prev = slabbed$modelDyOwnerLastTrace;
            boolean applied = dy != 0.0f;
            slabbed$modelDyOwnerLastTrace = new ModelDyOwnerSample(
                    true,
                    view.getClass().getName(),
                    pos.toShortString(),
                    state.toString(),
                    prev.emitCalls() + 1,
                    prev.appliedCalls() + (applied ? 1 : 0),
                    prev.totalAppliedDy() + (applied ? dy : 0.0),
                    dy);
        }

        if (Boolean.getBoolean("slabbed.render.offset.trace")
                && state.getBlock() instanceof ChainBlock
                && pos.equals(slabbed$tracePos)) {
            boolean excluded = state.getBlock() instanceof FenceBlock
                    || state.getBlock() instanceof WallBlock
                    || state.getBlock() instanceof IronBarsBlock;
            slabbed$lastTrace = new RenderOffsetSample(
                    true,
                    view.getClass().getName(),
                    pos.toShortString(),
                    state.toString(),
                    dy,
                    ClientDy.dyFor(view, pos, state),
                    SlabSupport.getYOffset(view, pos, state),
                    excluded);
        }

        // Prove that the render-path BlockGetter is not a Level, causing isAnchored to return false.
        // Fires only when -Dslabbed.anchor.trace=true AND view is NOT a Level instance.
        if (SlabAnchorAttachment.TRACE && !(view instanceof Level)) {
            boolean anchoredViaFallback = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                anchoredViaFallback = SlabAnchorAttachment.isAnchored(mc.level, pos);
            }
            if (anchoredViaFallback || dy != 0.0f) {
                Slabbed.LOGGER.info("[ANCHOR] model dy view={} pos={} dy={} anchoredViaWorldFallback={}",
                        view.getClass().getSimpleName(), pos.toShortString(), dy, anchoredViaFallback);
            }
        }

        // Slab-height step-face cull relaxation (renderer-agnostic — works under Indigo AND
        // Sodium because it edits the emitted quad's OWN cullFace, which every renderer
        // honours, rather than a per-renderer cull gate). Clear cullFace on faces that sit at
        // a lowered-vs-flat seam so the strip exposed by the model offset is drawn instead of
        // culled into a see-through "ghost window". A FLAT block (dy=0) adjacent to a lowered
        // one ALSO owns a step face, so the transform must run even when dy==0.
        // Mirrors the 1.21.11 model-path fix; see docs/CULL-WINDOW-FIX-DESIGN.md.
        final boolean clearStepCullFaces = slabbed$hasLoweredStepFace(view, pos, state);
        final boolean observeStepCull = slabbed$isStepCullTracePos(pos);

        if (dy == 0.0f && !clearStepCullFaces) {
            slabbed$recordMc1211FullMeshBoundsSample(view, pos, state, wrapped, dy,
                    0, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    "dy_zero_no_transform");
            if (observeStepCull) {
                slabbed$recordStepCullSample(
                        view,
                        pos,
                        state,
                        dy,
                        clearStepCullFaces,
                        0,
                        0,
                        0,
                        0,
                        "none",
                        "dy_zero_no_step_faces");
            }
            emitWrappedBlockQuads(view, state, pos, randomSupplier, context);
            return;
        }

        final float yOffset = dy;
        final BakedModel traceModel = wrapped;
        final int[] totalQuadsSeen = {0};
        final int[] cullFacesSeen = {0};
        final int[] stepFacesSeen = {0};
        final int[] stepCullFacesCleared = {0};
        final int[] verticesVisited = {0};
        final StringBuilder clearedFaces = new StringBuilder();
        final double[] meshBounds = {
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY};
        context.pushTransform(quad -> {
            totalQuadsSeen[0]++;
            Direction cullFace = quad.cullFace();
            if (cullFace != null) {
                cullFacesSeen[0]++;
            }
            // Un-cull lowered-step seam faces so the strip exposed by the offset is drawn,
            // not culled into a see-through window. Preserve nominalFace so lighting/orientation
            // are unchanged. Only flips cull->draw, so it cannot remove geometry or z-fight
            // (the opposite-facing coplanar seam face is GPU back-face-culled).
            if (clearStepCullFaces) {
                if (cullFace != null && SlabSupport.isSlabHeightStepFace(view, pos, state, cullFace)) {
                    stepFacesSeen[0]++;
                    if (clearedFaces.length() > 0) {
                        clearedFaces.append(',');
                    }
                    clearedFaces.append(cullFace.getName());
                    stepCullFacesCleared[0]++;
                    quad.cullFace(null);
                    quad.nominalFace(cullFace);
                }
            }
            for (int i = 0; i < 4; i++) {
                verticesVisited[0]++;
                float beforeY = quad.y(i);
                float afterY = beforeY + yOffset;
                meshBounds[0] = Math.min(meshBounds[0], beforeY);
                meshBounds[1] = Math.max(meshBounds[1], beforeY);
                meshBounds[2] = Math.min(meshBounds[2], afterY);
                meshBounds[3] = Math.max(meshBounds[3], afterY);
                quad.pos(i, quad.x(i), afterY, quad.z(i));
            }
            return true;
        });
        try {
            emitWrappedBlockQuads(view, state, pos, randomSupplier, context);
            slabbed$recordMc1211FullMeshBoundsSample(view, pos, state, traceModel, dy,
                    totalQuadsSeen[0],
                    verticesVisited[0],
                    meshBounds[0],
                    meshBounds[1],
                    meshBounds[2],
                    meshBounds[3],
                    "quad_transform_aggregate");
            if (observeStepCull) {
                slabbed$recordStepCullSample(
                        view,
                        pos,
                        state,
                        dy,
                        clearStepCullFaces,
                        totalQuadsSeen[0],
                        cullFacesSeen[0],
                        stepFacesSeen[0],
                        stepCullFacesCleared[0],
                        clearedFaces.length() == 0 ? "none" : clearedFaces.toString(),
                        "quad_transform_aggregate");
            }
        } finally {
            context.popTransform();
        }
    }

    /** True if any horizontal side face of {@code pos} sits at a slab-height step (see
     * {@link SlabSupport#isSlabHeightStepFace}). Drives the cull relaxation for BOTH the
     * lowered block and its flat neighbour, so neither side leaves a see-through seam. */
    private static boolean slabbed$hasLoweredStepFace(BlockAndTintGetter view, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (SlabSupport.isSlabHeightStepFace(view, pos, state, direction)) {
                return true;
            }
        }
        return false;
    }

    private static boolean slabbed$isStepCullTracePos(BlockPos pos) {
        BlockPos observedPos = slabbed$stepCullTracePos;
        return observedPos != null && observedPos.equals(pos);
    }

    private static void slabbed$recordStepCullSample(
            BlockAndTintGetter view,
            BlockPos pos,
            BlockState state,
            float dy,
            boolean clearStepCullFaces,
            int totalQuadsSeen,
            int cullFacesSeen,
            int stepFacesSeen,
            int stepCullFacesCleared,
            String clearedFaces,
            String reason
    ) {
        slabbed$stepCullLastTrace = new StepCullSample(
                true,
                view == null ? "null" : view.getClass().getName(),
                pos == null ? "null" : pos.toShortString(),
                state == null ? "null" : state.toString(),
                dy,
                clearStepCullFaces,
                totalQuadsSeen,
                cullFacesSeen,
                stepFacesSeen,
                stepCullFacesCleared,
                clearedFaces,
                reason);
    }

    private void emitWrappedBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos,
                                       Supplier<RandomSource> randomSupplier, RenderContext context) {
        if (wrapped instanceof FabricBakedModel fabricWrapped) {
            fabricWrapped.emitBlockQuads(view, state, pos, randomSupplier, context);
            return;
        }

        context.bakedModelConsumer().accept(wrapped, state);
    }

    private static void slabbed$logCompoundVisibleRenderTraceModelDy(
            BlockAndTintGetter view,
            BlockPos pos,
            BlockState state,
            float modelDy
    ) {
        if (!SlabAnchorAttachment.beta4CompoundVisibleRenderTraceEnabled()) {
            return;
        }
        String marker = slabbed$compoundVisibleMarker(view, pos, state);
        if ("none".equals(marker)) {
            return;
        }
        double clientDy = ClientDy.dyFor(view, pos, state);
        double slabSupportDy = SlabSupport.getYOffset(view, pos, state);
        Slabbed.LOGGER.info(
                "[JULIA_BETA4_COMPOUND_VISIBLE_RENDER_TRACE_MODEL_DY] pos={} marker={} serverMarker=n/a clientMarker=true modelViewType={} modelDy={} slabSupportDy={} clientDy={} candidateRerenderScheduled=n/a neighborRerenderScheduled=n/a",
                pos.toShortString(),
                marker,
                view.getClass().getSimpleName(),
                modelDy,
                slabSupportDy,
                clientDy);
    }

    private static String slabbed$compoundVisibleMarker(BlockAndTintGetter view, BlockPos pos, BlockState state) {
        if (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(view, pos, state)) {
            return "lower";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(view, pos, state)) {
            return "upper";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(view, pos, state)) {
            return "double";
        }
        if (SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(view, pos, state)) {
            return "top";
        }
        return "none";
    }

    private static void slabbed$logMc1211LiveModelTrace(
            BlockAndTintGetter view,
            BlockPos pos,
            BlockState state,
            String dySourcePath,
            float modelDyBeforeWrapper,
            float modelDyAfterWrapper
    ) {
        if (!Boolean.getBoolean("slabbed.mc1211.liveModelTrace")) {
            return;
        }

        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        String blockFilter = System.getProperty("slabbed.mc1211.liveModelTraceBlock", "minecraft:stone").trim();
        if (blockFilter.isEmpty()) {
            blockFilter = "minecraft:stone";
        }
        BlockPos near = slabbed$mc1211LiveModelTraceNearPos();
        int radius = slabbed$mc1211LiveModelTraceRadius();
        boolean parseFailed = near == null && !System.getProperty("slabbed.mc1211.liveModelTraceNear", "").isBlank();
        slabbed$logMc1211LiveModelTraceCanary(blockFilter, near, radius, parseFailed);
        if (parseFailed) {
            return;
        }
        if (!blockId.equals(blockFilter)) {
            slabbed$mc1211LiveModelTraceSkippedCount++;
            slabbed$mc1211LiveModelTraceSkippedByBlockFilterCount++;
            return;
        }
        int distanceFromNear = near == null ? -1 : slabbed$mc1211ManhattanDistance(pos, near);
        if (near != null && distanceFromNear > radius) {
            slabbed$mc1211LiveModelTraceSkippedCount++;
            slabbed$mc1211LiveModelTraceSkippedByRadiusCount++;
            return;
        }

        String rowKey = blockId + "@" + pos.toShortString();
        if (slabbed$mc1211LiveModelTraceRows.size() >= 128) {
            slabbed$mc1211LiveModelTraceSkippedCount++;
            if (!slabbed$mc1211LiveModelTraceLimitLogged) {
                slabbed$mc1211LiveModelTraceLimitLogged = true;
                slabbed$logMc1211LiveModelTraceSummary("row_limit_reached");
            }
            return;
        }
        if (!slabbed$mc1211LiveModelTraceRows.add(rowKey)) {
            return;
        }

        double outlineDy = ClientDy.dyFor(view, pos, state);
        Minecraft mc = Minecraft.getInstance();
        ClientLevel clientLevel = mc == null ? null : mc.level;
        BlockState clientState = clientLevel == null ? state : clientLevel.getBlockState(pos);
        double clientWorldDy = clientLevel == null ? Double.NaN : ClientDy.dyFor(clientLevel, pos, clientState);
        boolean renderViewCarrierMismatch = clientLevel != null
                && Math.abs(outlineDy - clientWorldDy) > 1.0e-6;
        boolean translationApplied = modelDyAfterWrapper != 0.0f;
        String reason = slabbed$mc1211LiveModelTraceReason(
                modelDyBeforeWrapper,
                modelDyAfterWrapper,
                outlineDy,
                renderViewCarrierMismatch);
        slabbed$mc1211RecordReason(reason);
        boolean modelLowerThanOutline = "MODEL_TRACE_LOWER_THAN_OUTLINE".equals(reason);
        double deltaModelMinusOutline = modelDyAfterWrapper - outlineDy;
        String bounds = slabbed$outlineBounds(view, pos, state);
        String nearFilter = near == null ? "none" : near.toShortString();
        String relativePos = near == null ? "n/a" : slabbed$relativePos(pos, near);
        String renderSection = slabbed$renderSection(pos);
        int rowCount = ++slabbed$mc1211LiveModelTraceRowCount;

        Slabbed.LOGGER.info(
                "[MC1211_LIVE_MODEL_TRACE_ROW] pos={} blockState={} blockId={} renderViewClass={} viewIsLevel={} clientWorldPresent={} nearFilter={} relativeToNear={} distanceFromNear={} renderSection={} modelDySourcePath={} modelDyBeforeWrapper={} modelDyAfterWrapper={} outlineDy={} clientWorldDy={} deltaModelMinusOutline={} modelLowerThanOutline={} modelTranslationApplied={} targetDy=omitted currentBounds={} inferredModelYTranslation={} reason={}",
                pos.toShortString(),
                state,
                blockId,
                view.getClass().getName(),
                view instanceof Level,
                clientLevel != null,
                nearFilter,
                relativePos,
                distanceFromNear < 0 ? "n/a" : Integer.toString(distanceFromNear),
                renderSection,
                dySourcePath,
                slabbed$formatTraceDouble(modelDyBeforeWrapper),
                slabbed$formatTraceDouble(modelDyAfterWrapper),
                slabbed$formatTraceDouble(outlineDy),
                slabbed$formatTraceDouble(clientWorldDy),
                slabbed$formatTraceDouble(deltaModelMinusOutline),
                modelLowerThanOutline,
                translationApplied,
                bounds,
                slabbed$formatTraceDouble(modelDyAfterWrapper),
                reason);
        if (rowCount == 1 || rowCount % 25 == 0) {
            slabbed$logMc1211LiveModelTraceSummary("rows_observed");
        }
    }

    private static void slabbed$recordMc1211FullMeshBoundsSample(
            BlockAndTintGetter view,
            BlockPos pos,
            BlockState state,
            BakedModel model,
            float dy,
            int totalQuadsSeen,
            int verticesVisited,
            double minBeforeY,
            double maxBeforeY,
            double minAfterY,
            double maxAfterY,
            String reason
    ) {
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        String matrixKey = blockId + "@" + pos.toShortString();
        String matrixRow = slabbed$mc1211FamilyMatrixRow(blockId);
        String modelClass = model == null ? "null" : model.getClass().getName();
        String viewClass = view == null ? "null" : view.getClass().getName();
        String tickOrFrame = slabbed$mc1211ClientTickOrUnknown();
        int passSequence = slabbed$mc1211FullMeshBoundsPassSequence.incrementAndGet();
        String meshTraceKey = matrixKey + "|pass=" + passSequence;
        BlockPos observedPos = slabbed$fullMeshBoundsTracePos;
        boolean observedProofRow = observedPos != null && observedPos.equals(pos);
        boolean fullMeshBoundsTraceEnabled = Boolean.getBoolean("slabbed.mc1211.fullMeshBoundsTrace");
        boolean aggregateTraceCandidate = fullMeshBoundsTraceEnabled
                && slabbed$shouldLogMc1211FullMeshBoundsSample(pos, blockId, dy);
        String renderOutlineBounds = observedProofRow || aggregateTraceCandidate
                ? slabbed$outlineBounds(view, pos, state)
                : "not_sampled";
        String aggregateDedupKey = (observedProofRow ? meshTraceKey : matrixKey)
                + "|" + slabbed$formatTraceDouble(dy)
                + "|" + renderOutlineBounds
                + "|" + reason;
        boolean finiteBounds = Double.isFinite(minBeforeY)
                && Double.isFinite(maxBeforeY)
                && Double.isFinite(minAfterY)
                && Double.isFinite(maxAfterY);

        if (observedProofRow) {
            slabbed$fullMeshBoundsLastTrace = new FullMeshBoundsSample(
                    true,
                    meshTraceKey,
                    matrixKey,
                    matrixRow,
                    blockId,
                    pos.toShortString(),
                    state.toString(),
                    dy,
                    modelClass,
                    viewClass,
                    renderOutlineBounds,
                    tickOrFrame,
                    passSequence,
                    totalQuadsSeen,
                    verticesVisited,
                    finiteBounds ? minBeforeY : Double.NaN,
                    finiteBounds ? maxBeforeY : Double.NaN,
                    finiteBounds ? minAfterY : Double.NaN,
                    finiteBounds ? maxAfterY : Double.NaN,
                    reason,
                    "FULL_MESH_SNAPSHOT",
                    "observed_pos",
                    aggregateDedupKey);
            slabbed$logMc1211FullMeshBoundsSnapshotTrace(
                    slabbed$fullMeshBoundsLastTrace,
                    state,
                    finiteBounds);
        }

        if (!aggregateTraceCandidate) {
            return;
        }
        if (!slabbed$mc1211FullMeshBoundsSampleRows.add(aggregateDedupKey)) {
            return;
        }
        double expectedMinAfterY = finiteBounds ? minBeforeY + dy : Double.NaN;
        double expectedMaxAfterY = finiteBounds ? maxBeforeY + dy : Double.NaN;
        Slabbed.LOGGER.info(
                "[MC1211_FULL_MESH_BOUNDS_TRACE] meshTraceKey={} matrixKey={} matrixRow={} blockId={} pos={} state={} dy={} modelClass={} tickOrFrame={} passSequence={} rowSource={} quadsVisited={} verticesVisited={} minBeforeY={} maxBeforeY={} minAfterY={} maxAfterY={} expectedMinAfterY={} expectedMaxAfterY={} viewClass={} renderOutlineBounds={} reason={} snapshotSource={} aggregateDedupKey={}",
                meshTraceKey,
                matrixKey,
                matrixRow,
                blockId,
                pos.toShortString(),
                state,
                slabbed$formatTraceDouble(dy),
                modelClass,
                tickOrFrame,
                passSequence,
                "FULL_MESH_AGGREGATE",
                totalQuadsSeen,
                verticesVisited,
                finiteBounds ? slabbed$formatTraceDouble(minBeforeY) : "NaN",
                finiteBounds ? slabbed$formatTraceDouble(maxBeforeY) : "NaN",
                finiteBounds ? slabbed$formatTraceDouble(minAfterY) : "NaN",
                finiteBounds ? slabbed$formatTraceDouble(maxAfterY) : "NaN",
                slabbed$formatTraceDouble(expectedMinAfterY),
                slabbed$formatTraceDouble(expectedMaxAfterY),
                viewClass,
                renderOutlineBounds,
                reason,
                "aggregate_emit",
                aggregateDedupKey);
    }

    private static void slabbed$logMc1211FullMeshBoundsSnapshotTrace(
            FullMeshBoundsSample trace,
            BlockState state,
            boolean finiteBounds
    ) {
        if (!Boolean.getBoolean("slabbed.mc1211.fullMeshBoundsTrace")) {
            return;
        }
        Slabbed.LOGGER.info(
                "[MC1211_FULL_MESH_BOUNDS_TRACE] meshTraceKey={} matrixKey={} matrixRow={} blockId={} pos={} state={} dy={} modelClass={} tickOrFrame={} passSequence={} rowSource={} quadsVisited={} verticesVisited={} minBeforeY={} maxBeforeY={} minAfterY={} maxAfterY={} expectedMinAfterY={} expectedMaxAfterY={} viewClass={} renderOutlineBounds={} reason={} snapshotSource={} aggregateDedupKey={}",
                trace.meshTraceKey(),
                trace.matrixKey(),
                trace.matrixRow(),
                trace.blockId(),
                trace.pos(),
                state,
                slabbed$formatTraceDouble(trace.dy()),
                trace.modelClass(),
                trace.tickOrFrame(),
                trace.passSequence(),
                trace.rowSource(),
                trace.totalQuadsSeen(),
                trace.verticesVisited(),
                finiteBounds ? slabbed$formatTraceDouble(trace.minBeforeY()) : "NaN",
                finiteBounds ? slabbed$formatTraceDouble(trace.maxBeforeY()) : "NaN",
                finiteBounds ? slabbed$formatTraceDouble(trace.minAfterY()) : "NaN",
                finiteBounds ? slabbed$formatTraceDouble(trace.maxAfterY()) : "NaN",
                finiteBounds ? slabbed$formatTraceDouble(trace.minBeforeY() + trace.dy()) : "NaN",
                finiteBounds ? slabbed$formatTraceDouble(trace.maxBeforeY() + trace.dy()) : "NaN",
                trace.viewClass(),
                trace.renderOutlineBounds(),
                trace.reason(),
                trace.snapshotSource(),
                trace.aggregateDedupKey());
    }

    private static boolean slabbed$shouldLogMc1211FullMeshBoundsSample(BlockPos pos, String blockId, float dy) {
        BlockPos observedPos = slabbed$fullMeshBoundsTracePos;
        if (observedPos != null && observedPos.equals(pos)) {
            return true;
        }
        return slabbed$isMc1211FamilyMatrixTraceCandidateBlockId(blockId)
                && Math.abs(dy) > 1.0e-6f;
    }

    private static boolean slabbed$isMc1211FamilyMatrixTraceCandidateBlockId(String blockId) {
        return "minecraft:stone".equals(blockId)
                || "minecraft:oak_log".equals(blockId)
                || "minecraft:oak_planks".equals(blockId)
                || "minecraft:crafting_table".equals(blockId)
                || "minecraft:stone_wall".equals(blockId)
                || "minecraft:cobblestone_wall".equals(blockId)
                || "minecraft:stone_stairs".equals(blockId)
                || "minecraft:oak_fence".equals(blockId)
                || "minecraft:stone_slab".equals(blockId);
    }

    private static String slabbed$mc1211FamilyMatrixRow(String blockId) {
        return switch (blockId) {
            case "minecraft:stone" -> "STONE";
            case "minecraft:oak_log", "minecraft:oak_planks" -> "KNOWN_GOOD_1";
            case "minecraft:crafting_table" -> "KNOWN_GOOD_2";
            case "minecraft:stone_wall" -> "STONE_WALL";
            case "minecraft:cobblestone_wall" -> "COBBLESTONE_WALL";
            case "minecraft:oak_fence" -> "OAK_FENCE";
            case "minecraft:stone_stairs" -> "STONE_STAIRS";
            default -> "UNKNOWN";
        };
    }

    private static String slabbed$mc1211ClientTickOrUnknown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return "unknown";
        }
        return Long.toString(mc.level.getGameTime());
    }

    private static void slabbed$logMc1211LiveModelTraceCanary(String blockFilter, BlockPos near, int radius, boolean parseFailed) {
        if (slabbed$mc1211LiveModelTraceCanaryLogged) {
            return;
        }
        slabbed$mc1211LiveModelTraceCanaryLogged = true;
        Slabbed.LOGGER.info(
                "[MC1211_LIVE_MODEL_TRACE_CANARY] enabled=true traceRunsByDefault=false blockFilter={} nearFilter={} radius={} parseFailed={} path=OffsetBlockStateModel.emitBlockQuads behaviorChanged=false",
                blockFilter,
                near == null ? "none" : near.toShortString(),
                radius,
                parseFailed);
        if (parseFailed && !slabbed$mc1211LiveModelTraceParseFailureLogged) {
            slabbed$mc1211LiveModelTraceParseFailureLogged = true;
            Slabbed.LOGGER.info(
                    "[MC1211_LIVE_MODEL_TRACE_SUMMARY] reason=parse_failure rows=0 uniquePositions=0 skipped={} skippedByBlockFilter={} skippedByRadius={} parseFailed=true rawNearFilter={}",
                    slabbed$mc1211LiveModelTraceSkippedCount,
                    slabbed$mc1211LiveModelTraceSkippedByBlockFilterCount,
                    slabbed$mc1211LiveModelTraceSkippedByRadiusCount,
                    System.getProperty("slabbed.mc1211.liveModelTraceNear", ""));
        }
    }

    private static void slabbed$logMc1211LiveModelTraceSummary(String reason) {
        Slabbed.LOGGER.info(
                "[MC1211_LIVE_MODEL_TRACE_SUMMARY] reason={} rows={} uniquePositions={} skipped={} skippedByBlockFilter={} skippedByRadius={} reasonMatchesOutline={} reasonLowerThanOutline={} reasonHigherThanOutline={} reasonOutlineUnavailable={} reasonNotVideoEquivalent={}",
                reason,
                slabbed$mc1211LiveModelTraceRowCount,
                slabbed$mc1211LiveModelTraceRows.size(),
                slabbed$mc1211LiveModelTraceSkippedCount,
                slabbed$mc1211LiveModelTraceSkippedByBlockFilterCount,
                slabbed$mc1211LiveModelTraceSkippedByRadiusCount,
                slabbed$mc1211LiveModelTraceMatchesOutlineCount,
                slabbed$mc1211LiveModelTraceLowerThanOutlineCount,
                slabbed$mc1211LiveModelTraceHigherThanOutlineCount,
                slabbed$mc1211LiveModelTraceOutlineUnavailableCount,
                slabbed$mc1211LiveModelTraceNotVideoEquivalentCount);
    }

    private static BlockPos slabbed$mc1211LiveModelTraceNearPos() {
        String raw = System.getProperty("slabbed.mc1211.liveModelTraceNear", "");
        if (raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int slabbed$mc1211ManhattanDistance(BlockPos pos, BlockPos near) {
        return Math.abs(pos.getX() - near.getX())
                + Math.abs(pos.getY() - near.getY())
                + Math.abs(pos.getZ() - near.getZ());
    }

    private static int slabbed$mc1211LiveModelTraceRadius() {
        String raw = System.getProperty("slabbed.mc1211.liveModelTraceRadius", "3").trim();
        if (raw.isEmpty()) {
            return 3;
        }
        try {
            int parsed = Integer.parseInt(raw);
            return Math.max(0, Math.min(parsed, 32));
        } catch (NumberFormatException ignored) {
            return 3;
        }
    }

    private static String slabbed$mc1211LiveModelTraceReason(
            double modelDyBeforeWrapper,
            double modelDyAfterWrapper,
            double outlineDy,
            boolean renderViewCarrierMismatch
    ) {
        if (renderViewCarrierMismatch) {
            return "MODEL_TRACE_NOT_VIDEO_EQUIVALENT";
        }
        if (Double.isNaN(outlineDy)) {
            return "MODEL_TRACE_OUTLINE_UNAVAILABLE";
        }
        if (Math.abs(modelDyAfterWrapper - outlineDy) <= 1.0e-6) {
            return "MODEL_TRACE_MATCHES_OUTLINE";
        }
        if (modelDyAfterWrapper < outlineDy - 1.0e-6) {
            return "MODEL_TRACE_LOWER_THAN_OUTLINE";
        }
        if (modelDyAfterWrapper > outlineDy + 1.0e-6) {
            return "MODEL_TRACE_HIGHER_THAN_OUTLINE";
        }
        return "MODEL_TRACE_NOT_VIDEO_EQUIVALENT";
    }

    private static void slabbed$mc1211RecordReason(String reason) {
        switch (reason) {
            case "MODEL_TRACE_MATCHES_OUTLINE" -> slabbed$mc1211LiveModelTraceMatchesOutlineCount++;
            case "MODEL_TRACE_LOWER_THAN_OUTLINE" -> slabbed$mc1211LiveModelTraceLowerThanOutlineCount++;
            case "MODEL_TRACE_HIGHER_THAN_OUTLINE" -> slabbed$mc1211LiveModelTraceHigherThanOutlineCount++;
            case "MODEL_TRACE_OUTLINE_UNAVAILABLE" -> slabbed$mc1211LiveModelTraceOutlineUnavailableCount++;
            default -> slabbed$mc1211LiveModelTraceNotVideoEquivalentCount++;
        }
    }

    private static String slabbed$relativePos(BlockPos pos, BlockPos near) {
        int dx = pos.getX() - near.getX();
        int dy = pos.getY() - near.getY();
        int dz = pos.getZ() - near.getZ();
        return dx + "," + dy + "," + dz;
    }

    private static String slabbed$renderSection(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        int sectionY = pos.getY() >> 4;
        return "chunk(" + chunkX + "," + chunkZ + ")/sectionY(" + sectionY + ")";
    }

    private static String slabbed$outlineBounds(BlockAndTintGetter view, BlockPos pos, BlockState state) {
        try {
            VoxelShape outline = state.getShape(view, pos, CollisionContext.empty());
            if (outline == null || outline.isEmpty()) {
                return "empty";
            }
            AABB box = outline.bounds();
            return String.format(
                    Locale.ROOT,
                    "[%.6f,%.6f]",
                    box.minY,
                    box.maxY);
        } catch (RuntimeException e) {
            return "error:" + e.getClass().getSimpleName();
        }
    }

    private static String slabbed$formatTraceDouble(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
