package com.slabbed.client.model;
import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.block.BlockState;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.PaleMossCarpetBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.List;
import java.util.function.Predicate;

/**
 * Wraps a FabricBlockStateModel to apply a vertical offset to emitted quads
 * (e.g., torches on bottom slabs) without relying on MatrixStack hacks.
 */
@SuppressWarnings({"RedundantSuppression", "DataFlowIssue"})
public final class OffsetBlockStateModel implements BlockStateModel, FabricBlockStateModel {
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

        QuadEmitter out = dy != 0.0f ? YOffsetEmitter.wrap(emitter, dy) : emitter;
        fabricWrapped.emitQuads(out, view, pos, state, random, cullTest);
    }
}
