package com.slabbed.client.model;

import com.slabbed.Slabbed;
import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Predicate;

/**
 * Wraps a FabricBlockStateModel to apply a vertical offset to emitted quads.
 */
@SuppressWarnings({"RedundantSuppression", "DataFlowIssue"})
public final class OffsetBlockStateModel implements BlockStateModel {
    private static final boolean CULL_TRACE = Boolean.getBoolean("slabbed.render.offset.cullTrace");

    private final BlockStateModel wrapped;
    private final FabricBlockStateModel fabricWrapped;

    public OffsetBlockStateModel(BlockStateModel wrapped) {
        this.wrapped = wrapped;
        this.fabricWrapped = wrapped;
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        wrapped.collectParts(random, parts);
    }

    @Override
    public Material.Baked particleMaterial() {
        return wrapped.particleMaterial();
    }

    @Override
    public int materialFlags() {
        return wrapped.materialFlags();
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter view, BlockPos pos, BlockState state, RandomSource random,
                          Predicate<Direction> cullTest) {
        // A Y-axis chain hanging under a slab ceiling emits extended geometry so the column connects
        // continuously to the slab (no gap) — the chainable connect rule, distinct from lantern follow.
        // Guarded: its support probe reads pos.above(), which can step outside the render-region border
        // for a block at the section's top edge (26.x throws on OOB) — fall through to normal emission.
        try {
            if (ChainCeilingGeometry.emitIfPresent(fabricWrapped, emitter, view, pos, state, random, cullTest)) {
                return;
            }
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            // fall through to the standard offset emission below (also render-region guarded)
        }
        float dy = slabbed$modelDy(view, pos, state);
        // DODO / step-face cull: a FLAT block (dy=0) adjacent to a lowered one ALSO owns a step face
        // whose cullFace must be cleared — otherwise the strip the neighbour's -0.5 offset exposes
        // culls into a see-through "ghost window". 26.1.2 previously only wrapped dy!=0 blocks, so the
        // flat side of a lowered-vs-flat seam was left culled. Wrap (clearing cullFace on mismatched-dy
        // faces, no Y shift when dy=0) whenever this block is offset OR any neighbour sits at a
        // different dy. Renderer-agnostic (edits the quad's own cullFace). Port of 1.21.1 clearStepCullFaces.
        boolean stepSeam = dy != 0.0f || slabbed$anyMismatchedNeighborDy(view, pos, dy);
        QuadEmitter out = stepSeam ? YOffsetEmitter.wrap(emitter, dy, slabbed$hasMismatchedNeighborDy(view, pos, dy)) : emitter;
        fabricWrapped.emitQuads(out, view, pos, state, random, slabbed$offsetAwareCullTest(view, pos, state, dy, cullTest));
    }

    private static float slabbed$modelDy(BlockAndTintGetter view, BlockPos pos, BlockState state) {
        // Render-region crash guard (26.x): on the chunk-meshing thread `view` is a bounds-limited
        // RenderSectionRegion that THROWS (ArrayIndexOutOfBoundsException) on a read outside its border.
        // SlabSupport.getYOffset does wide column-walk + adjacent-column side-support reads that can reach
        // past that border for a block near the region edge, so tesselating ordinary terrain at a region
        // boundary crashed the game. Treat any out-of-bounds read as "no offset" (0); the section recompiles
        // with fuller bounds once neighbouring sections load, so the real offset settles a frame later.
        // (Older MC render regions clamped OOB reads to air instead of throwing — a 26.x-specific need.)
        try {
            if (state.getBlock() instanceof CarpetBlock || state.getBlock() instanceof MossyCarpetBlock) {
                return (float) ClientDy.dyFor(view, pos, state);
            }

            float dy = (float) SlabSupport.getYOffset(view, pos, state);
            if (dy == 0.0f) {
                return 0.0f;
            }

            if (state.getBlock() instanceof FenceBlock
                    || state.getBlock() instanceof WallBlock
                    || state.getBlock() instanceof IronBarsBlock) {
                if (!SlabSupport.isBeta35FenceWallVariantContactObject(state)) {
                    return 0.0f;
                }
            }
            return dy;
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return 0.0f;
        }
    }

    /**
     * Neighbour model dy, bounds-safe for the render region. The neighbour's own block read can be the
     * first thing to step outside the region border, so guard it here too (slabbed$modelDy guards the
     * deeper resolver walk). Returns 0 (no offset) for an out-of-bounds neighbour.
     */
    private static float slabbed$neighborModelDy(BlockAndTintGetter view, BlockPos neighborPos) {
        try {
            return slabbed$modelDy(view, neighborPos, view.getBlockState(neighborPos));
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return 0.0f;
        }
    }

    private static Predicate<Direction> slabbed$offsetAwareCullTest(
            BlockAndTintGetter view,
            BlockPos pos,
            BlockState state,
            float dy,
            Predicate<Direction> cullTest
    ) {
        return direction -> {
            if (direction == null || cullTest == null) {
                return false;
            }
            BlockPos neighborPos = pos.relative(direction);
            float neighborDy = slabbed$neighborModelDy(view, neighborPos);
            if (Math.abs(neighborDy - dy) > 1.0e-6f) {
                if (CULL_TRACE) {
                    BlockState neighborState;
                    try {
                        neighborState = view.getBlockState(neighborPos);
                    } catch (IndexOutOfBoundsException outsideRenderRegion) {
                        neighborState = null;
                    }
                    boolean vanillaCull = cullTest.test(direction);
                    slabbed$traceCullDecision(pos, direction, dy, neighborPos, neighborDy, state, neighborState, vanillaCull);
                }
                return false;
            }
            return cullTest.test(direction);
        };
    }

    private static Predicate<Direction> slabbed$hasMismatchedNeighborDy(BlockAndTintGetter view, BlockPos pos, float dy) {
        return direction -> {
            float neighborDy = slabbed$neighborModelDy(view, pos.relative(direction));
            return Math.abs(neighborDy - dy) > 1.0e-6f;
        };
    }

    /** True if ANY of the 6 neighbours sits at a different model dy than this block (a lowered-vs-flat
     *  step seam). Used to clear cull faces even for a dy=0 block at a seam (the DODO ghost-window fix). */
    private static boolean slabbed$anyMismatchedNeighborDy(BlockAndTintGetter view, BlockPos pos, float dy) {
        for (Direction direction : Direction.values()) {
            float neighborDy = slabbed$neighborModelDy(view, pos.relative(direction));
            if (Math.abs(neighborDy - dy) > 1.0e-6f) {
                return true;
            }
        }
        return false;
    }

    private static void slabbed$traceCullDecision(
            BlockPos pos,
            Direction direction,
            float dy,
            BlockPos neighborPos,
            float neighborDy,
            BlockState state,
            BlockState neighborState,
            boolean vanillaCull
    ) {
        Slabbed.LOGGER.info("[slabbed.render.offset.cullTrace] dyMismatch pos={} state={} face={} dy={} neighborPos={} neighborState={} neighborDy={} vanillaCull={} forcedCull=false",
                pos.toShortString(), state, direction, dy, neighborPos.toShortString(), neighborState, neighborDy, vanillaCull);
    }
}
