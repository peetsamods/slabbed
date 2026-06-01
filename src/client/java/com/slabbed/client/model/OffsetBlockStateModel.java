package com.slabbed.client.model;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
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

    public static void resetRenderOffsetTrace(BlockPos pos) {
        slabbed$tracePos = pos;
        slabbed$lastTrace = RenderOffsetTrace.missing();
    }

    public static RenderOffsetTrace snapshotRenderOffsetTrace() {
        return slabbed$lastTrace;
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
                // Preserve the legacy generic-slab connection guard, but do not
                // block named custom slab direct-support surfaces.
                if (!SlabSupport.isDirectCustomSlabSupportedObject(view, pos, state)
                        && (state.getBlock() instanceof FenceBlock
                        || state.getBlock() instanceof WallBlock
                        || state.getBlock() instanceof PaneBlock)) {
                    dy = 0.0f;
                }
            }
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
        fabricWrapped.emitQuads(out, view, pos, state, random,
                slabbed$cullForLowered(view, pos, state, dy, cullTest));
    }

    /**
     * Fixes the "culled face" on a lowered opaque full cube (e.g. a crafting table or
     * pumpkin lowered onto a slab in a terrace). Vanilla computes face culling at the
     * un-shifted voxel, so a horizontal side face occluded by a grid-height same-level
     * neighbour is culled even though the lowering exposes its lower half over the step.
     *
     * <p>Relax it: a lowered side face is only culled when the same-level neighbour is
     * lowered by the <em>same</em> amount (so its cube actually covers the lowered face).
     * A higher / un-lowered neighbour leaves the stepped lower half open, so the face is
     * drawn. This only ever draws MORE faces (never fewer), so it cannot create new
     * see-through; opaque neighbours still cull their own faces toward this cube, so the
     * extra draws sit flush behind solids without z-fighting. Vertical faces and
     * non-cube / non-lowered blocks are left to vanilla culling.
     */
    private static Predicate<Direction> slabbed$cullForLowered(
            BlockRenderView view, BlockPos pos, BlockState state, float dy, Predicate<Direction> cullTest) {
        if (dy >= 0.0f || !state.isOpaqueFullCube()) {
            return cullTest;
        }
        return direction -> {
            if (direction == null || !direction.getAxis().isHorizontal()) {
                return cullTest.test(direction);
            }
            if (!cullTest.test(direction)) {
                return false;
            }
            BlockPos neighborPos = pos.offset(direction);
            double neighborDy = SlabSupport.getYOffset(view, neighborPos, view.getBlockState(neighborPos));
            return Math.abs(neighborDy - dy) < 1.0e-6;
        };
    }
}
