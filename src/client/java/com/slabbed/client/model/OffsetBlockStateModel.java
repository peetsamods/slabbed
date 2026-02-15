package com.slabbed.client.model;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.WallTorchBlock;
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
        float dy = (float) SlabSupport.getYOffset(view, pos, state);
        boolean fullCubeGuarded = false;
        if (dy != 0.0f && view != null && pos != null) {
            // Avoid applying slab render offset to full-cube blocks (e.g., crafting table)
            if (state.isSolidBlock(view, pos)) {
                dy = 0.0f;
                fullCubeGuarded = true;
            }
        }
        if (state.getBlock() instanceof TorchBlock || state.getBlock() instanceof WallTorchBlock) {
            Slabbed.LOGGER.info("[Slabbed] emitQuads TORCH dy={} pos={} below={}", dy, pos, view.getBlockState(pos.down()).getBlock());
        }
        if (state.isOf(Blocks.CRAFTING_TABLE)) {
            Slabbed.LOGGER.info("[Slabbed] emitQuads crafting_table dy={} fullCubeGuarded={}", dy, fullCubeGuarded);
        }
        QuadEmitter out = dy != 0.0f ? YOffsetEmitter.wrap(emitter, dy) : emitter;
        fabricWrapped.emitQuads(out, view, pos, state, random, cullTest);
    }
}
