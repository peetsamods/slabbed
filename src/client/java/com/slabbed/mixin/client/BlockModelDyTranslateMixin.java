package com.slabbed.mixin.client;

import com.slabbed.client.ClientDy;
import com.slabbed.client.debug.ModelDyTranslateTraceBridge;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

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
        ModelDyTranslateTraceBridge.record(method, world, pos, state, dy);
    }

    @Inject(method = "render(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V",
            at = @At("HEAD"))
    private void slabbed$pushDy(BlockRenderView world,
                                List<BlockModelPart> parts,
                                BlockState state,
                                BlockPos pos,
                                MatrixStack matrices,
                                VertexConsumer vertexConsumer,
                                boolean cull,
                                int overlay,
                                CallbackInfo ci) {
        double dy = ClientDy.dyFor(world, pos, state);
        slabbed$recordTrace("render", world, pos, state, dy);
        if (dy == 0.0) {
            return;
        }
        matrices.push();
        matrices.translate(0.0, dy, 0.0);
    }

    @Inject(method = "render(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V",
            at = @At("TAIL"))
    private void slabbed$popDy(BlockRenderView world,
                               List<BlockModelPart> parts,
                               BlockState state,
                               BlockPos pos,
                               MatrixStack matrices,
                               VertexConsumer vertexConsumer,
                               boolean cull,
                               int overlay,
                               CallbackInfo ci) {
        double dy = ClientDy.dyFor(world, pos, state);
        if (dy == 0.0) {
            return;
        }
        matrices.pop();
    }

    @Inject(method = "renderSmooth(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V",
            at = @At("HEAD"))
    private void slabbed$pushDySmooth(BlockRenderView world,
                                      List<BlockModelPart> parts,
                                      BlockState state,
                                      BlockPos pos,
                                      MatrixStack matrices,
                                      VertexConsumer vertexConsumer,
                                      boolean cull,
                                      int overlay,
                                      CallbackInfo ci) {
        double dy = ClientDy.dyFor(world, pos, state);
        slabbed$recordTrace("renderSmooth", world, pos, state, dy);
        if (dy == 0.0) {
            return;
        }
        matrices.push();
        matrices.translate(0.0, dy, 0.0);
    }

    @Inject(method = "renderSmooth(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V",
            at = @At("TAIL"))
    private void slabbed$popDySmooth(BlockRenderView world,
                                     List<BlockModelPart> parts,
                                     BlockState state,
                                     BlockPos pos,
                                     MatrixStack matrices,
                                     VertexConsumer vertexConsumer,
                                     boolean cull,
                                     int overlay,
                                     CallbackInfo ci) {
        double dy = ClientDy.dyFor(world, pos, state);
        if (dy == 0.0) {
            return;
        }
        matrices.pop();
    }

    @Inject(method = "renderFlat(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V",
            at = @At("HEAD"))
    private void slabbed$pushDyFlat(BlockRenderView world,
                                    List<BlockModelPart> parts,
                                    BlockState state,
                                    BlockPos pos,
                                    MatrixStack matrices,
                                    VertexConsumer vertexConsumer,
                                    boolean cull,
                                    int overlay,
                                    CallbackInfo ci) {
        double dy = ClientDy.dyFor(world, pos, state);
        slabbed$recordTrace("renderFlat", world, pos, state, dy);
        if (dy == 0.0) {
            return;
        }
        matrices.push();
        matrices.translate(0.0, dy, 0.0);
    }

    @Inject(method = "renderFlat(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V",
            at = @At("TAIL"))
    private void slabbed$popDyFlat(BlockRenderView world,
                                   List<BlockModelPart> parts,
                                   BlockState state,
                                   BlockPos pos,
                                   MatrixStack matrices,
                                   VertexConsumer vertexConsumer,
                                   boolean cull,
                                   int overlay,
                                   CallbackInfo ci) {
        double dy = ClientDy.dyFor(world, pos, state);
        if (dy == 0.0) {
            return;
        }
        matrices.pop();
    }
}
