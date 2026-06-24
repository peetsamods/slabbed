package com.slabbed.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.slabbed.client.ClientDy;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Offsets item frame rendering when the block the frame is attached to is Slabbed-lowered.
 */
@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameRenderOffsetMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/decoration/ItemFrame;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER))
    private void slabbed$adjustItemFrameOffset(ItemFrame entity,
                                               float yaw,
                                               float tickDelta,
                                               PoseStack matrices,
                                               MultiBufferSource vertexConsumers,
                                               int light,
                                               CallbackInfo ci) {
        Level world = entity.level();
        if (world == null) {
            return;
        }

        BlockPos attachedPos = entity.getPos();
        if (attachedPos == null) {
            return;
        }

        BlockState attachedState = world.getBlockState(attachedPos);
        double dy = ClientDy.dyFor(world, attachedPos, attachedState);
        if (dy != 0.0) {
            matrices.translate(0.0, dy, 0.0);
        }
    }
}
