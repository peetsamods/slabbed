package com.slabbed.client.model;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.block.BlockState;
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

    /**
     * Arm the render-path capture for {@code pos} (or disarm with {@code null}) and clear any
     * previous sample. The value lands the next time this cell's chunk section is meshed —
     * {@link #emitQuads} runs at bake time, not per frame — so an armed cell reads
     * {@code NOT_SAMPLED} until a re-mesh happens. Callers that need it promptly should schedule
     * a block re-render for the same position.
     */
    public static void resetRenderOffsetTrace(BlockPos pos) {
        slabbed$tracePos = pos;
        slabbed$lastTrace = RenderOffsetTrace.missing();
    }

    public static RenderOffsetTrace snapshotRenderOffsetTrace() {
        return slabbed$lastTrace;
    }

    /**
     * Fabric renderer entry point used by Indigo and Sodium's FRAPI-compatible renderer.
     */
    @Override
    public void emitQuads(QuadEmitter emitter, BlockRenderView view, BlockPos pos, BlockState state, Random random,
                          Predicate<Direction> cullTest) {
        // ONE dy for every block, no per-class branch. This used to fork carpets off to
        // ClientDy.dyFor's own geometric shortcut, which had no anchor logic and so drew a carpet
        // flush whenever the common authority said anything other than "the block below is a
        // half-height slab surface" (BUG A, live 2026-08-06, recorder 0ba17cf0). ClientDy.dyFor is
        // now a pure delegate to getVisualYOffset, so routing every block through it is identical
        // to calling getVisualYOffset directly — and the trace below can prove that per frame.
        //
        // Model dy tracks getVisualYOffset, matching the outline/raycast — including
        // fences/walls/panes on a VANILLA slab (previously forced to dy=0 here, which floated the
        // model above an already-correctly-lowered outline: GH #21). The resulting height-step
        // connector arm is a separate concern, broken by FencePaneSlabConnectionMixin /
        // WallSlabConnectionMixin via SlabSupport.isSteppedConnectingNeighbor — not by suppressing
        // the model dy.
        float dy = (float) ClientDy.dyFor(view, pos, state);

        // Armed-position check ONLY. This used to also demand the JVM system property
        // -Dslabbed.render.offset.trace=true, which no live client session sets — so the model leg
        // of the dy triad was structurally unreadable in the game, and every /slabdy and recorder
        // row reported it blank while the outline and collision legs reported real numbers. That is
        // the worst shape a diagnostic can have: it reads as coverage. The arm/disarm call is the
        // gate now (slabbed$tracePos is null in normal play, so this is one volatile read and a
        // null-rejecting equals — strictly cheaper on the mesher path than the System.getProperty
        // hashtable lookup it replaces).
        if (pos.equals(slabbed$tracePos)) {
            // Nothing is dy-excluded by the wrapper any more — fences/walls/panes now
            // track getVisualYOffset like every other block (GH #21). Keep the field so
            // the RenderOffsetTrace record shape and the /slabdy readout stay stable, but
            // report the truth (false) rather than the stale connection-block guess.
            boolean excluded = false;
            slabbed$lastTrace = new RenderOffsetTrace(
                    true,
                    view.getClass().getName(),
                    pos.toShortString(),
                    state.toString(),
                    dy,
                    ClientDy.dyFor(view, pos, state),
                    SlabSupport.getVisualYOffset(view, pos, state),
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
