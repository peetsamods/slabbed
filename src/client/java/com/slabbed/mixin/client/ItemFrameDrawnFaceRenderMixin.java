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
 * Draws an item frame on its support's DRAWN face (maintainer ruling, 2026-09-01, extended
 * 2026-09-13). The entity's real position deliberately stays at grid height — see
 * {@code HangingEntityRememberedSeatMixin} for why moving it corrupts the frame's derived grid
 * cell — so the render layer applies the same offset the bounding box carries; the two must stay
 * in step.
 *
 * <p>The offset is the frame's REMEMBERED seat, never a fresh read of the support: the same number
 * the bounding box carries, so box and drawing cannot disagree and neither follows a wall rebuilt
 * later. The predecessor of this mixin read the frame's OWN attached cell live; do not regress to
 * that.
 *
 * <p>Do NOT route this through {@code getRenderOffset}: on this version the frame renderer calls
 * that hook itself and translates by its negation, so the dispatcher's offset and the renderer's
 * undo cancel exactly and the drawn frame never moves. The translate below sits inside the
 * renderer's own push/pop pair, before any rotation, so the shift stays world-aligned and
 * contained.
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
        // The REMEMBERED seat (synced entity data), the same number the bounding box carries.
        // Do not re-read the support here: the seat is minted once when the frame is hung and
        // must not follow a wall rebuilt at a different height (LAW.md Law 1; ruling 2026-09-13).
        double dy = ((HangingSeatDyHolder) entity).slabbed$hangSeatDy();
        if (Math.abs(dy) >= 1.0e-6d) {
            matrices.translate(0.0d, dy, 0.0d);
        }
    }
}
