package com.slabbed.client.model;

import com.slabbed.Slabbed;
import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabEnsembleCoherence;
import com.slabbed.util.SlabModelStaleSentinel;
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
                // MODEL_STALE sentinel: the bridge renders at grid height by design — record dy=0 so an
                // armed chain cell is not misread as "never re-baked" by the absence rule.
                if (SlabModelStaleSentinel.shouldCapture() && SlabModelStaleSentinel.isArmed(pos.asLong())) {
                    SlabModelStaleSentinel.recordBake(pos, 0.0f);
                }
                return;
            }
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            // fall through to the standard offset emission below (also render-region guarded)
        }
        // MODEL_STALE sentinel capture — SUBJECT emissions only (a neighbor probe from another section's
        // bake pass recomputes fresh dy and would mask that this pos's own mesh is stale), and never the
        // render-region OOB fallback (that 0.0 is a border artifact, not a dy decision — the section
        // re-bakes with fuller bounds a frame later). Gate order is load-bearing (perf contract): one
        // volatile read, then the armed-set binary search, before any other work.
        float dy;
        if (SlabModelStaleSentinel.shouldCapture() && SlabModelStaleSentinel.isArmed(pos.asLong())) {
            dy = slabbed$modelDyCaptured(view, pos, state);
        } else {
            dy = slabbed$modelDy(view, pos, state);
        }
        // DODO / step-face cull: a FLAT block (dy=0) adjacent to a lowered one ALSO owns a step face
        // whose cullFace must be cleared — otherwise the strip the neighbour's -0.5 offset exposes
        // culls into a see-through "ghost window". 26.1.2 previously only wrapped dy!=0 blocks, so the
        // flat side of a lowered-vs-flat seam was left culled. Wrap (clearing cullFace on mismatched-dy
        // faces, no Y shift when dy=0) whenever this block is offset OR any neighbour sits at a
        // different dy. Renderer-agnostic (edits the quad's own cullFace). Port of 1.21.1 clearStepCullFaces.
        boolean stepSeam = dy != 0.0f || slabbed$anyMismatchedNeighborDy(view, pos, dy);
        QuadEmitter out = stepSeam ? YOffsetEmitter.wrap(emitter, dy, slabbed$hasMismatchedNeighborDy(view, pos, dy)) : emitter;
        fabricWrapped.emitQuads(out, view, pos, state, random, slabbed$offsetAwareCullTest(view, pos, state, dy, cullTest));
        // Phase 3a band emission PULLED after live rejection (TEST (9), 2026-07-07): BAKE_LOCK_UV
        // derives UVs from vertex positions, and band tops exceed the unit square — the UVs walk off
        // the block's sprite into NEIGHBORING ATLAS SPRITES, painting alien texture strips on every
        // stack. Take-2 must use explicit uv() with V clamped/tiled inside the sprite (see design doc);
        // until then no band is emitted. The plan function + its pins remain.
        // slabbed$emitGapFillBand(out, view, pos, dy);
    }

    /**
     * Phase 3a (ENSEMBLE_COHERENCE_DESIGN.md, render tiling first tranche): when this block's
     * face-contact pair ABOVE sits higher (the measured GAP class — −1.0 under −0.5 stacks, doors over
     * lowered slabs, hopper columns), emit an additive BAND of this block's side texture spanning the
     * seam, so the visible mid-stack air strip closes. Same philosophy as the chain ceiling bridge:
     * extra geometry, nothing moves, dy laws untouched. Emitted through the SAME dy-shifted emitter, so
     * band-local y ∈ [1, 1+d] lands exactly between the two visual bodies. Vanilla-contact requirement
     * lives in the classifier (a bottom slab's by-design half-gap is never banded); TS pairs are
     * guarded there too. Region-border reads fall back to no band (the section re-bakes with fuller
     * bounds a frame later — same contract as slabbed$modelDy).
     */
    private void slabbed$emitGapFillBand(QuadEmitter out, BlockAndTintGetter view, BlockPos pos, float dy) {
        try {
            BlockPos above = pos.above();
            BlockState aboveState = view.getBlockState(above);
            if (aboveState.isAir()) {
                return;
            }
            double dyAbove = slabbed$neighborModelDy(view, above);
            double d = SlabEnsembleCoherence.gapFillBandHeight(view, pos, dy, dyAbove);
            if (d <= 1.0e-4) {
                return;
            }
            Material.Baked particle = wrapped.particleMaterial();
            if (particle == null) {
                return;
            }
            float top = 1.0f + (float) d;
            for (Direction face : Direction.values()) {
                if (face.getAxis() == Direction.Axis.Y) {
                    continue;
                }
                out.square(face, 0.0f, 1.0f, 1.0f, top, 0.0f);
                out.materialBake(particle, net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView.BAKE_LOCK_UV);
                out.color(-1, -1, -1, -1);
                out.emit();
            }
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            // no band at the region border; the re-bake with fuller bounds draws it a frame later
        }
    }

    /**
     * The render-intent dy policy, publicly callable so the MODEL_STALE sentinel judges live dy with the
     * SAME function the capture records (adversarial review finding #1: judging with raw
     * {@code SlabSupport.getYOffset} while capturing this policy guaranteed false DIVERGENT reds on
     * carpets, whose render dy deliberately differs from their logical dy).
     */
    public static float liveModelDy(BlockAndTintGetter view, BlockPos pos, BlockState state) {
        return slabbed$modelDy(view, pos, state);
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
            return slabbed$modelDyUnguarded(view, pos, state);
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return 0.0f;
        }
    }

    /** Sentinel-armed twin of {@link #slabbed$modelDy}: identical dy, but records what was baked —
     *  except the OOB fallback, which is deliberately not a recordable dy decision. */
    private static float slabbed$modelDyCaptured(BlockAndTintGetter view, BlockPos pos, BlockState state) {
        try {
            float dy = slabbed$modelDyUnguarded(view, pos, state);
            SlabModelStaleSentinel.recordBake(pos, dy);
            return dy;
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return 0.0f;
        }
    }

    private static float slabbed$modelDyUnguarded(BlockAndTintGetter view, BlockPos pos, BlockState state) {
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
