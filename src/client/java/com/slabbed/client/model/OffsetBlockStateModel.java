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
     * Fixes "culled faces" at a slab height step. Vanilla computes face culling at the
     * un-shifted voxel, so a shared horizontal face between an opaque cube and an
     * occluding same-level neighbour is culled even when one of them is lowered onto a
     * slab and the other is not — the step exposes part of the face, leaving a
     * see-through hole on either side.
     *
     * <p>Rule (symmetric): a horizontal side face of an opaque cube is culled only when
     * the occluding neighbour is at the SAME visual height; a neighbour lowered by a
     * different amount leaves part of the face open, so it is drawn. This runs for both
     * the lowered block (relax toward higher neighbours) and the grid-height block
     * (relax toward lowered neighbours), and only ever draws MORE faces, so it cannot
     * create new see-through. The extra faces in the overlap region are sandwiched
     * between the two solid bodies (back-to-back, mutually occluded), so there is no
     * z-fighting. Vertical faces and non-cube blocks are left to vanilla culling.
     *
     * <p>Perf: a lowered cube (rare) resolves the neighbour's exact dy; a grid-height
     * cube uses the cheap {@link SlabSupport#isDirectCustomSlabSupportedObject} test,
     * which returns false fast for ordinary terrain, so open terrain stays cheap.
     */
    private static Predicate<Direction> slabbed$cullForLowered(
            BlockRenderView view, BlockPos pos, BlockState state, float dy, Predicate<Direction> cullTest) {
        if (!state.isOpaqueFullCube()) {
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
            BlockState neighbor = view.getBlockState(neighborPos);
            if (dy < 0.0f) {
                // This cube is lowered: cull only if the occluding neighbour is lowered
                // by the same amount; a higher / un-lowered neighbour leaves the step open.
                double neighborDy = SlabSupport.getYOffset(view, neighborPos, neighbor);
                return Math.abs(neighborDy - dy) < 1.0e-6;
            }
            // This cube is at grid height: relax only toward a lowered custom-supported
            // neighbour (cheap check; ordinary terrain returns false fast) so the
            // neighbour's drop does not punch a hole in this cube's facing side.
            return !SlabSupport.isDirectCustomSlabSupportedObject(view, neighborPos, neighbor);
        };
    }
}
