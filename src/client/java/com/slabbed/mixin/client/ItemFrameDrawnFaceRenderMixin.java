package com.slabbed.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws an item frame on the face it REMEMBERS being hung on (maintainer ruling, 2026-09-01 for
 * frames; 2026-09-13 for the remembered seat). The entity's real position deliberately stays at
 * grid height - see {@code ItemFrameWysiwygMixin} for why moving it corrupts the frame's derived
 * grid cell - so the render layer applies the same seat the bounding box carries; the two must
 * stay in step.
 *
 * <p>The offset is the frame's REMEMBERED seat (see {@code HangingEntityRememberedSeatMixin}),
 * never a fresh read of the support: the same number the bounding box carries, so box and drawing
 * cannot disagree and neither follows a wall rebuilt later.
 *
 * <p>The hook is a pose translate inside {@code render}, right after vanilla's own first
 * translate. Do NOT route this through {@code getRenderOffset}: on this version the frame renderer
 * subtracts that vector again a few lines into {@code render}, so anything added there cancels out
 * and the frame draws at grid height while its box sits on the seat (live, 2026-09-14: the frame
 * appeared a block above the slab it was hung on).
 */
@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameDrawnFaceRenderMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/decoration/ItemFrame;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER))
    private void slabbed$drawOnRememberedSeat(ItemFrame entity,
                                              float yaw,
                                              float tickDelta,
                                              PoseStack matrices,
                                              MultiBufferSource vertexConsumers,
                                              int light,
                                              CallbackInfo ci) {
        double dy = ((HangingSeatDyHolder) entity).slabbed$hangSeatDy();
        if (Math.abs(dy) >= 1.0e-6d) {
            matrices.translate(0.0d, dy, 0.0d);
        }
    }
}
