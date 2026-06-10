package com.slabbed.client.model;

import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.function.Supplier;

/**
 * Wraps a BakedModel to apply a vertical offset to emitted quads
 * (e.g., torches on bottom slabs) without relying on MatrixStack hacks.
 */
@SuppressWarnings({"RedundantSuppression", "DataFlowIssue"})
public final class OffsetBlockStateModel extends ForwardingBakedModel {
    public OffsetBlockStateModel(BakedModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
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
}
