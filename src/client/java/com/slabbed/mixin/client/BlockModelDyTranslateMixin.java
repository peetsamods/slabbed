package com.slabbed.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.slabbed.client.ClientDy;
import com.slabbed.client.model.ChainCeilingGeometry;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.util.RuntimeDiagnostics;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies ClientDy translate on the main block render path so models align with outline/raycast.
 */
@Mixin(ModelBlockRenderer.class)
public class BlockModelDyTranslateMixin {
    private static void slabbed$recordTrace(
            String method,
            BlockAndTintGetter world,
            BlockPos pos,
            BlockState state,
            double dy
    ) {
        RuntimeDiagnostics.recordModelDyTrace(method, world, pos, state, dy);
    }

    private static double slabbed$modelDy(BakedModel model, BlockAndTintGetter world, BlockPos pos, BlockState state) {
        if (model instanceof OffsetBlockStateModel) {
            return 0.0d;
        }
        if (ChainCeilingGeometry.usesAlternateGeometry(world, pos, state)) {
            return 0.0d;
        }
        return ClientDy.dyFor(world, pos, state);
    }

    @Inject(method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JI)V",
            at = @At("HEAD"))
    private void slabbed$pushDy(BlockAndTintGetter world,
                                BakedModel model,
                                BlockState state,
                                BlockPos pos,
                                PoseStack matrices,
                                VertexConsumer vertexConsumer,
                                boolean cull,
                                RandomSource random,
                                long seed,
                                int overlay,
                                CallbackInfo ci) {
        double dy = slabbed$modelDy(model, world, pos, state);
        slabbed$recordTrace("tesselateBlock", world, pos, state, dy);
        if (dy == 0.0) {
            return;
        }
        matrices.pushPose();
        matrices.translate(0.0, dy, 0.0);
    }

    @Inject(method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JI)V",
            at = @At("TAIL"))
    private void slabbed$popDy(BlockAndTintGetter world,
                               BakedModel model,
                               BlockState state,
                               BlockPos pos,
                               PoseStack matrices,
                               VertexConsumer vertexConsumer,
                               boolean cull,
                               RandomSource random,
                               long seed,
                               int overlay,
                               CallbackInfo ci) {
        double dy = slabbed$modelDy(model, world, pos, state);
        if (dy == 0.0) {
            return;
        }
        matrices.popPose();
    }

    @Inject(method = "tesselateWithAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JI)V",
            at = @At("HEAD"))
    private void slabbed$pushDySmooth(BlockAndTintGetter world,
                                      BakedModel model,
                                      BlockState state,
                                      BlockPos pos,
                                      PoseStack matrices,
                                      VertexConsumer vertexConsumer,
                                      boolean cull,
                                      RandomSource random,
                                      long seed,
                                      int overlay,
                                      CallbackInfo ci) {
        double dy = slabbed$modelDy(model, world, pos, state);
        slabbed$recordTrace("tesselateWithAO", world, pos, state, dy);
        if (dy == 0.0) {
            return;
        }
        matrices.pushPose();
        matrices.translate(0.0, dy, 0.0);
    }

    @Inject(method = "tesselateWithAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JI)V",
            at = @At("TAIL"))
    private void slabbed$popDySmooth(BlockAndTintGetter world,
                                     BakedModel model,
                                     BlockState state,
                                     BlockPos pos,
                                     PoseStack matrices,
                                     VertexConsumer vertexConsumer,
                                     boolean cull,
                                     RandomSource random,
                                     long seed,
                                     int overlay,
                                     CallbackInfo ci) {
        double dy = slabbed$modelDy(model, world, pos, state);
        if (dy == 0.0) {
            return;
        }
        matrices.popPose();
    }

    @Inject(method = "tesselateWithoutAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JI)V",
            at = @At("HEAD"))
    private void slabbed$pushDyFlat(BlockAndTintGetter world,
                                    BakedModel model,
                                    BlockState state,
                                    BlockPos pos,
                                    PoseStack matrices,
                                    VertexConsumer vertexConsumer,
                                    boolean cull,
                                    RandomSource random,
                                    long seed,
                                    int overlay,
                                    CallbackInfo ci) {
        double dy = slabbed$modelDy(model, world, pos, state);
        slabbed$recordTrace("tesselateWithoutAO", world, pos, state, dy);
        if (dy == 0.0) {
            return;
        }
        matrices.pushPose();
        matrices.translate(0.0, dy, 0.0);
    }

    @Inject(method = "tesselateWithoutAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JI)V",
            at = @At("TAIL"))
    private void slabbed$popDyFlat(BlockAndTintGetter world,
                                   BakedModel model,
                                   BlockState state,
                                   BlockPos pos,
                                   PoseStack matrices,
                                   VertexConsumer vertexConsumer,
                                   boolean cull,
                                   RandomSource random,
                                   long seed,
                                   int overlay,
                                   CallbackInfo ci) {
        double dy = slabbed$modelDy(model, world, pos, state);
        if (dy == 0.0) {
            return;
        }
        matrices.popPose();
    }
}
