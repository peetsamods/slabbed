package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.runtime.PistonMovingRenderScope;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.PistonBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;

/** Gives the piston dispatcher exclusive ownership of stored dy during nested model rendering. */
@Mixin(PistonBlockEntityRenderer.class)
public abstract class PistonBlockEntityRenderScopeMixin {

    @WrapMethod(
            method = "render(Lnet/minecraft/block/entity/PistonBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V"
    )
    private void slabbed$scopeNestedPistonModel(
            PistonBlockEntity blockEntity,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay,
            Operation<Void> original
    ) {
        boolean suppressNestedDy = blockEntity != null
                && SlabAnchorAttachment.usesFrozenPlacementHeight(
                        blockEntity.getWorld(), blockEntity.getPos());
        if (suppressNestedDy) {
            PistonMovingRenderScope.enter();
        }
        try {
            original.call(blockEntity, tickDelta, matrices, vertexConsumers, light, overlay);
        } finally {
            if (suppressNestedDy) {
                PistonMovingRenderScope.exit();
            }
        }
    }
}
