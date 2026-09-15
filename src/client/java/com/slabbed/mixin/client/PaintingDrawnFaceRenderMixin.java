package com.slabbed.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.world.entity.decoration.Painting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a painting on the face it remembers being hung on (maintainer ruling, 2026-09-13). The
 * entity's real position stays at grid height — see {@code HangingEntityRememberedSeatMixin} — so
 * the drawing is moved by the remembered seat, the same number the bounding box carries.
 *
 * <p>Same mechanism as {@code ItemFrameDrawnFaceRenderMixin}: one world-aligned translate inside
 * the renderer's own push/pop pair, applied before the facing rotation so it stays vertical, and
 * popped before the name-tag pass. Keep the two mixins on the same mechanism.
 */
@Mixin(PaintingRenderer.class)
public abstract class PaintingDrawnFaceRenderMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/decoration/Painting;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                    ordinal = 0,
                    shift = At.Shift.AFTER))
    private void slabbed$drawOnRememberedSeat(Painting entity,
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
