package com.slabbed.mixin.client;

import com.slabbed.client.ClientDy;
import com.slabbed.client.model.ChainCeilingGeometry;
import com.slabbed.util.RuntimeDiagnostics;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies ClientDy translate on the main block render path so models align with outline/raycast.
 */
@Mixin(BlockModelRenderer.class)
public class BlockModelDyTranslateMixin {
    private static void slabbed$recordTrace(
            String method,
            BlockRenderView world,
            BlockPos pos,
            BlockState state,
            double dy
    ) {
        RuntimeDiagnostics.recordModelDyTrace(method, world, pos, state, dy);
    }

    /**
     * Model dy applied on the vanilla translate path. Returns 0 (no translate) when the block
     * uses chain-ceiling alternate geometry, which is emitted elongated at dy=0 by
     * {@link com.slabbed.client.model.OffsetBlockStateModel}; without this bypass the vanilla
     * path would double-shift it by +0.5.
     */
    private static double slabbed$modelDy(BlockRenderView world, BlockPos pos, BlockState state) {
        if (ChainCeilingGeometry.usesAlternateGeometry(world, pos, state)) {
            return 0.0;
        }
        return ClientDy.dyFor(world, pos, state);
    }

    @Inject(method = "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",
            at = @At("HEAD"))
    private void slabbed$pushDy(BlockRenderView world,
                                BakedModel model,
                                BlockState state,
                                BlockPos pos,
                                MatrixStack matrices,
                                VertexConsumer vertexConsumer,
                                boolean cull,
                                Random random,
                                long seed,
                                int overlay,
                                CallbackInfo ci) {
        double dy = slabbed$modelDy(world, pos, state);
        slabbed$recordTrace("render", world, pos, state, dy);
        if (dy == 0.0) {
            return;
        }
        matrices.push();
        matrices.translate(0.0, dy, 0.0);
    }

    @Inject(method = "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",
            at = @At("TAIL"))
    private void slabbed$popDy(BlockRenderView world,
                               BakedModel model,
                               BlockState state,
                               BlockPos pos,
                               MatrixStack matrices,
                               VertexConsumer vertexConsumer,
                               boolean cull,
                               Random random,
                               long seed,
                               int overlay,
                               CallbackInfo ci) {
        double dy = slabbed$modelDy(world, pos, state);
        if (dy == 0.0) {
            return;
        }
        matrices.pop();
    }

    @Inject(method = "renderSmooth(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",
            at = @At("HEAD"))
    private void slabbed$pushDySmooth(BlockRenderView world,
                                      BakedModel model,
                                      BlockState state,
                                      BlockPos pos,
                                      MatrixStack matrices,
                                      VertexConsumer vertexConsumer,
                                      boolean cull,
                                      Random random,
                                      long seed,
                                      int overlay,
                                      CallbackInfo ci) {
        double dy = slabbed$modelDy(world, pos, state);
        slabbed$recordTrace("renderSmooth", world, pos, state, dy);
        if (dy == 0.0) {
            return;
        }
        matrices.push();
        matrices.translate(0.0, dy, 0.0);
    }

    @Inject(method = "renderSmooth(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",
            at = @At("TAIL"))
    private void slabbed$popDySmooth(BlockRenderView world,
                                     BakedModel model,
                                     BlockState state,
                                     BlockPos pos,
                                     MatrixStack matrices,
                                     VertexConsumer vertexConsumer,
                                     boolean cull,
                                     Random random,
                                     long seed,
                                     int overlay,
                                     CallbackInfo ci) {
        double dy = slabbed$modelDy(world, pos, state);
        if (dy == 0.0) {
            return;
        }
        matrices.pop();
    }

    @Inject(method = "renderFlat(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",
            at = @At("HEAD"))
    private void slabbed$pushDyFlat(BlockRenderView world,
                                    BakedModel model,
                                    BlockState state,
                                    BlockPos pos,
                                    MatrixStack matrices,
                                    VertexConsumer vertexConsumer,
                                    boolean cull,
                                    Random random,
                                    long seed,
                                    int overlay,
                                    CallbackInfo ci) {
        double dy = slabbed$modelDy(world, pos, state);
        slabbed$recordTrace("renderFlat", world, pos, state, dy);
        if (dy == 0.0) {
            return;
        }
        matrices.push();
        matrices.translate(0.0, dy, 0.0);
    }

    @Inject(method = "renderFlat(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",
            at = @At("TAIL"))
    private void slabbed$popDyFlat(BlockRenderView world,
                                   BakedModel model,
                                   BlockState state,
                                   BlockPos pos,
                                   MatrixStack matrices,
                                   VertexConsumer vertexConsumer,
                                   boolean cull,
                                   Random random,
                                   long seed,
                                   int overlay,
                                   CallbackInfo ci) {
        double dy = slabbed$modelDy(world, pos, state);
        if (dy == 0.0) {
            return;
        }
        matrices.pop();
    }
}
