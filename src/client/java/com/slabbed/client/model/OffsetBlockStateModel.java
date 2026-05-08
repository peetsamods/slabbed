package com.slabbed.client.model;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.client.runtime.ModelDyTranslateTraceBridge;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.block.BlockState;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.PaleMossCarpetBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Predicate;

/**
 * Wraps a FabricBlockStateModel to apply a vertical offset to emitted quads
 * (e.g., torches on bottom slabs) without relying on MatrixStack hacks.
 */
@SuppressWarnings({"RedundantSuppression", "DataFlowIssue"})
public final class OffsetBlockStateModel implements BlockStateModel, FabricBlockStateModel {
    private static volatile BlockPos slabbed$tracePos = null;
    private static volatile RenderOffsetTrace slabbed$lastTrace = RenderOffsetTrace.missing();
    private static volatile BlockPos slabbed$modelDyOwnerTracePos = null;
    private static volatile ModelDyOwnerTrace slabbed$modelDyOwnerLastTrace = ModelDyOwnerTrace.missing();

    private final BlockStateModel wrapped;
    private final FabricBlockStateModel fabricWrapped;

    public OffsetBlockStateModel(BlockStateModel wrapped) {
        this.wrapped = wrapped;
        this.fabricWrapped = (FabricBlockStateModel) wrapped;
    }

    @Override
    public void addParts(Random random, List<BlockModelPart> parts) {
        wrapped.addParts(random, parts);
    }

    @Override
    public List<BlockModelPart> getParts(Random random) {
        return wrapped.getParts(random);
    }

    @Override
    public Sprite particleSprite() {
        return wrapped.particleSprite();
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
    public void emitQuads(QuadEmitter emitter, BlockRenderView view, BlockPos pos, BlockState state, Random random,
                          Predicate<Direction> cullTest) {
        float dy;
        if (state.getBlock() instanceof CarpetBlock || state.getBlock() instanceof PaleMossCarpetBlock) {
            dy = (float) ClientDy.dyFor(view, pos, state);
        } else {
            dy = (float) SlabSupport.getYOffset(view, pos, state);
            if (dy != 0.0f) {
                // Prevent visual connection offsets for fences/walls/panes
                if (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof PaneBlock) {
                    dy = 0.0f;
                }
            }
        }
        ModelDyTranslateTraceBridge.recordBeta4ModelDy("fabricEmitQuads", view, pos, state, dy);

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

        QuadEmitter out = dy != 0.0f ? YOffsetEmitter.wrap(emitter, dy) : emitter;
        fabricWrapped.emitQuads(out, view, pos, state, random, cullTest);
    }
}
