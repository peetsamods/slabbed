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
        if (ChainCeilingGeometry.emitIfPresent(fabricWrapped, emitter, view, pos, state, random, cullTest)) {
            return;
        }
        float dy = slabbed$modelDy(view, pos, state);
        QuadEmitter out = dy != 0.0f ? YOffsetEmitter.wrap(emitter, dy, slabbed$hasMismatchedNeighborDy(view, pos, dy)) : emitter;
        fabricWrapped.emitQuads(out, view, pos, state, random, slabbed$offsetAwareCullTest(view, pos, state, dy, cullTest));
    }

    private static float slabbed$modelDy(BlockAndTintGetter view, BlockPos pos, BlockState state) {
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
            BlockState neighborState = view.getBlockState(neighborPos);
            float neighborDy = slabbed$modelDy(view, neighborPos, neighborState);
            if (Math.abs(neighborDy - dy) > 1.0e-6f) {
                if (CULL_TRACE) {
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
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = view.getBlockState(neighborPos);
            float neighborDy = slabbed$modelDy(view, neighborPos, neighborState);
            return Math.abs(neighborDy - dy) > 1.0e-6f;
        };
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
