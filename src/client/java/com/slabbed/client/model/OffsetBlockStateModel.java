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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;

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
        float dy;
        if (state.getBlock() instanceof CarpetBlock) {
            dy = (float) ClientDy.dyFor(view, pos, state);
        } else {
            dy = (float) SlabSupport.getYOffset(view, pos, state);
            if (dy != 0.0f) {
                // Prevent visual connection offsets for fences/walls/panes,
                // except for the explicitly proven Beta 3.5 fence/wall variants.
                if (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof PaneBlock) {
                    if (!SlabSupport.isBeta35FenceWallVariantContactObject(state)) {
                        dy = 0.0f;
                    }
                }
            }
        }
        ModelDyTranslateTraceBridge.recordBeta4ModelDy("fabricEmitQuads", view, pos, state, dy);
        slabbed$logCompoundVisibleRenderTraceModelDy(view, pos, state, dy);

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
}
