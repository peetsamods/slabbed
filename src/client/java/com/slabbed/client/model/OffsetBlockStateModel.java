package com.slabbed.client.model;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.client.runtime.ModelDyTranslateTraceBridge;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;
import net.minecraft.block.ShapeContext;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Wraps a BakedModel to apply a vertical offset to emitted quads
 * (e.g., torches on bottom slabs) without relying on MatrixStack hacks.
 */
@SuppressWarnings({"RedundantSuppression", "DataFlowIssue"})
public final class OffsetBlockStateModel extends ForwardingBakedModel {
    private static volatile BlockPos slabbed$tracePos = null;
    private static volatile RenderOffsetTrace slabbed$lastTrace = RenderOffsetTrace.missing();
    private static volatile BlockPos slabbed$modelDyOwnerTracePos = null;
    private static volatile ModelDyOwnerTrace slabbed$modelDyOwnerLastTrace = ModelDyOwnerTrace.missing();
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

    public record RenderOffsetTrace(
            boolean seen,
            String viewClass,
            String pos,
            String state,
            double modelDy,
            double clientDy,
            double slabSupportDy,
            boolean excludedByWrapper
    ) {
        static RenderOffsetTrace missing() {
            return new RenderOffsetTrace(false, "none", "none", "none", 0.0, 0.0, 0.0, false);
        }
    }

    public record ModelDyOwnerTrace(
            boolean seen,
            String viewClass,
            String pos,
            String state,
            int emitCalls,
            int appliedCalls,
            double totalAppliedDy,
            double lastDy
    ) {
        static ModelDyOwnerTrace missing() {
            return new ModelDyOwnerTrace(false, "none", "none", "none", 0, 0, 0.0, 0.0);
        }
    }

    public static void resetRenderOffsetTrace(BlockPos pos) {
        slabbed$tracePos = pos;
        slabbed$lastTrace = RenderOffsetTrace.missing();
    }

    public static RenderOffsetTrace snapshotRenderOffsetTrace() {
        return slabbed$lastTrace;
    }

    public static void resetModelDyOwnerTrace(BlockPos pos) {
        slabbed$modelDyOwnerTracePos = pos == null ? null : pos.toImmutable();
        slabbed$modelDyOwnerLastTrace = ModelDyOwnerTrace.missing();
    }

    public static ModelDyOwnerTrace snapshotModelDyOwnerTrace() {
        return slabbed$modelDyOwnerLastTrace;
    }

    /**
     * Fabric renderer entry point used by Indigo/Sodium+Indium.
     */
    @Override
    public void emitBlockQuads(BlockRenderView view, BlockState state, BlockPos pos, Supplier<Random> randomSupplier,
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
                if (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof PaneBlock) {
                    if (!SlabSupport.isBeta35FenceWallVariantContactObject(state)) {
                        dy = 0.0f;
                        dySourcePath = dySourcePath + ":visualConnectionExcluded";
                    }
                }
            }
        }
        ModelDyTranslateTraceBridge.recordBeta4ModelDy("fabricEmitQuads", view, pos, state, dy);
        slabbed$logCompoundVisibleRenderTraceModelDy(view, pos, state, dy);
        slabbed$logMc1211LiveModelTrace(view, pos, state, dySourcePath, sourceDy, dy);

        BlockPos modelDyTracePos = slabbed$modelDyOwnerTracePos;
        if (modelDyTracePos != null && modelDyTracePos.equals(pos)) {
            ModelDyOwnerTrace prev = slabbed$modelDyOwnerLastTrace;
            boolean applied = dy != 0.0f;
            slabbed$modelDyOwnerLastTrace = new ModelDyOwnerTrace(
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
                    || state.getBlock() instanceof PaneBlock;
            slabbed$lastTrace = new RenderOffsetTrace(
                    true,
                    view.getClass().getName(),
                    pos.toShortString(),
                    state.toString(),
                    dy,
                    ClientDy.dyFor(view, pos, state),
                    SlabSupport.getYOffset(view, pos, state),
                    excluded);
        }

        // Prove that the render-path BlockView is not a World, causing isAnchored to return false.
        // Fires only when -Dslabbed.anchor.trace=true AND view is NOT a World instance.
        if (SlabAnchorAttachment.TRACE && !(view instanceof World)) {
            boolean anchoredViaFallback = false;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.world != null) {
                anchoredViaFallback = SlabAnchorAttachment.isAnchored(mc.world, pos);
            }
            if (anchoredViaFallback || dy != 0.0f) {
                Slabbed.LOGGER.info("[ANCHOR] model dy view={} pos={} dy={} anchoredViaWorldFallback={}",
                        view.getClass().getSimpleName(), pos.toShortString(), dy, anchoredViaFallback);
            }
        }

        if (dy == 0.0f) {
            emitWrappedBlockQuads(view, state, pos, randomSupplier, context);
            return;
        }

        final float yOffset = dy;
        context.pushTransform(quad -> {
            for (int i = 0; i < 4; i++) {
                quad.pos(i, quad.x(i), quad.y(i) + yOffset, quad.z(i));
            }
            return true;
        });
        try {
            emitWrappedBlockQuads(view, state, pos, randomSupplier, context);
        } finally {
            context.popTransform();
        }
    }

    private void emitWrappedBlockQuads(BlockRenderView view, BlockState state, BlockPos pos,
                                       Supplier<Random> randomSupplier, RenderContext context) {
        if (wrapped instanceof FabricBakedModel fabricWrapped) {
            fabricWrapped.emitBlockQuads(view, state, pos, randomSupplier, context);
            return;
        }

        context.bakedModelConsumer().accept(wrapped, state);
    }

    private static void slabbed$logCompoundVisibleRenderTraceModelDy(
            BlockRenderView view,
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

    private static String slabbed$compoundVisibleMarker(BlockRenderView view, BlockPos pos, BlockState state) {
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
            BlockRenderView view,
            BlockPos pos,
            BlockState state,
            String dySourcePath,
            float modelDyBeforeWrapper,
            float modelDyAfterWrapper
    ) {
        if (!Boolean.getBoolean("slabbed.mc1211.liveModelTrace")) {
            return;
        }

        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
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
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld clientWorld = mc == null ? null : mc.world;
        BlockState clientState = clientWorld == null ? state : clientWorld.getBlockState(pos);
        double clientWorldDy = clientWorld == null ? Double.NaN : ClientDy.dyFor(clientWorld, pos, clientState);
        boolean renderViewCarrierMismatch = clientWorld != null
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
                "[MC1211_LIVE_MODEL_TRACE_ROW] pos={} blockState={} blockId={} renderViewClass={} viewIsWorld={} clientWorldPresent={} nearFilter={} relativeToNear={} distanceFromNear={} renderSection={} modelDySourcePath={} modelDyBeforeWrapper={} modelDyAfterWrapper={} outlineDy={} clientWorldDy={} deltaModelMinusOutline={} modelLowerThanOutline={} modelTranslationApplied={} targetDy=omitted currentBounds={} inferredModelYTranslation={} reason={}",
                pos.toShortString(),
                state,
                blockId,
                view.getClass().getName(),
                view instanceof World,
                clientWorld != null,
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

    private static String slabbed$outlineBounds(BlockRenderView view, BlockPos pos, BlockState state) {
        try {
            VoxelShape outline = state.getOutlineShape(view, pos, ShapeContext.absent());
            if (outline == null || outline.isEmpty()) {
                return "empty";
            }
            Box box = outline.getBoundingBox();
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
