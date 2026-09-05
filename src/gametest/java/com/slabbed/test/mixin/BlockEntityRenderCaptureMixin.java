package com.slabbed.test.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.test.BlockEntityRenderAudit;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Observes the real block-entity dispatch matrices without changing render arguments. */
@Mixin(value = BlockEntityRenderDispatcher.class, priority = 500)
public abstract class BlockEntityRenderCaptureMixin {

    @WrapMethod(
            method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V"
    )
    private <E extends BlockEntity> void slabbed$captureInitialMatrix(
            E blockEntity, float tickDelta, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, Operation<Void> original
    ) {
        BlockEntityRenderAudit.begin(blockEntity, matrices);
        try {
            original.call(blockEntity, tickDelta, matrices, vertexConsumers);
        } finally {
            BlockEntityRenderAudit.end();
        }
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V"
            )
    )
    private static <T extends BlockEntity> void slabbed$captureRendererMatrix(
            BlockEntityRenderer<T> renderer, T blockEntity, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, int overlay
    ) {
        BlockEntityRenderAudit.observe(blockEntity, matrices);
        renderer.render(blockEntity, tickDelta, matrices, vertexConsumers, light, overlay);
    }
}
