package com.slabbed.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.slabbed.util.SlabSupport;
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
 * Draws an item frame on its support's DRAWN face (maintainer ruling, 2026-09-01). The
 * entity's real position deliberately stays at grid height — see {@code ItemFrameWysiwygMixin}
 * for why moving it corrupts the frame's derived grid cell — so the render layer applies the
 * same support-cell offset the bounding box carries; the two must stay in step.
 *
 * <p>The offset reads the SUPPORT cell, {@code pos.relative(direction.getOpposite())}. A
 * predecessor of this mixin read the frame's OWN cell — usually air — and therefore never
 * fired for wall frames at all; do not regress to {@code entity.getPos()} alone.
 */
@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameDrawnFaceRenderMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/decoration/ItemFrame;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER))
    private void slabbed$drawOnDrawnFace(ItemFrame entity,
                                         float yaw,
                                         float tickDelta,
                                         PoseStack matrices,
                                         MultiBufferSource vertexConsumers,
                                         int light,
                                         CallbackInfo ci) {
        Level world = entity.level();
        BlockPos framePos = entity.getPos();
        if (world == null || framePos == null || entity.getDirection() == null) {
            return;
        }
        BlockPos supportPos = framePos.relative(entity.getDirection().getOpposite());
        if (!world.hasChunkAt(supportPos)) {
            return;
        }
        BlockState support = world.getBlockState(supportPos);
        double dy = SlabSupport.getYOffset(world, supportPos, support);
        if (Double.isFinite(dy) && Math.abs(dy) >= 1.0e-6d) {
            matrices.translate(0.0d, dy, 0.0d);
        }
    }
}
