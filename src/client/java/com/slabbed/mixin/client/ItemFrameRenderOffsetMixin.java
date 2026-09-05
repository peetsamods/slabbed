package com.slabbed.mixin.client;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps an item frame's rendered body at the frozen height of its backing support block. */
@Mixin(ItemFrameEntityRenderer.class)
public abstract class ItemFrameRenderOffsetMixin {

    @Inject(method = "render(Lnet/minecraft/entity/decoration/ItemFrameEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void slabbed$translateStoredItemFrame(
            ItemFrameEntity entity,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci) {
        if (!SlabAnchorAttachment.FROZEN_DY_ENABLED) {
            return;
        }
        World world = entity.getEntityWorld();
        BlockPos attachedPos = entity.getAttachedBlockPos();
        if (world == null || attachedPos == null) {
            return;
        }
        BlockPos backingPos = attachedPos.offset(entity.getHorizontalFacing().getOpposite());
        BlockState backingState = world.getBlockState(backingPos);
        double dy = SlabSupport.getYOffset(world, backingPos, backingState);
        if (Double.isFinite(dy) && dy != 0.0d) {
            // The dispatcher adds getPositionOffset and this renderer subtracts it again before
            // drawing. Translate the actual renderer body so the two vanilla terms still cancel.
            matrices.translate(0.0d, dy, 0.0d);
        }
    }

    @Inject(method = "getPositionOffset(Lnet/minecraft/entity/decoration/ItemFrameEntity;F)Lnet/minecraft/util/math/Vec3d;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$adjustItemFrameOffset(ItemFrameEntity entity,
                                               float tickDelta,
                                               CallbackInfoReturnable<Vec3d> cir) {
        if (SlabAnchorAttachment.FROZEN_DY_ENABLED) {
            return;
        }
        World world = entity.getEntityWorld();
        if (world == null) {
            return;
        }

        BlockPos attachedPos = entity.getAttachedBlockPos();
        if (attachedPos == null) {
            return;
        }

        BlockState attachedState = world.getBlockState(attachedPos);

        if (SlabSupport.shouldOffset(world, attachedPos, attachedState)) {
            Vec3d current = cir.getReturnValue();
            cir.setReturnValue((current == null ? Vec3d.ZERO : current).add(0.0, -0.5, 0.0));
        }
    }
}
