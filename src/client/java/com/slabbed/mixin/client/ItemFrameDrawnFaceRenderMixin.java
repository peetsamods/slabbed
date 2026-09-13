package com.slabbed.mixin.client;

import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.client.render.entity.state.ItemFrameEntityRenderState;
import net.minecraft.entity.decoration.ItemFrameEntity;
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
 * <p>The offset is the frame's REMEMBERED seat (see {@code HangingEntityRememberedSeatMixin}),
 * never a fresh read of the support: the same number the bounding box carries, so box and drawing
 * cannot disagree and neither follows a wall rebuilt later. The predecessor of this mixin
 * (removed) read the frame's OWN cell live via {@code SlabSupport.shouldOffset} and applied a
 * hardcoded {@code positionOffset} of -0.5; do not regress to either.
 */
@Mixin(ItemFrameEntityRenderer.class)
public abstract class ItemFrameDrawnFaceRenderMixin {

    /**
     * Same mechanism as {@code PaintingDrawnFaceRenderMixin}: move the render state's Y by the
     * remembered seat at update time. Do NOT route this through {@code positionOffset}: that was
     * the predecessor mixin's mechanism and left the frame drawn at grid height while its box was
     * lowered.
     */
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/decoration/ItemFrameEntity;Lnet/minecraft/client/render/entity/state/ItemFrameEntityRenderState;F)V",
            at = @At("TAIL"))
    private void slabbed$drawOnRememberedSeat(ItemFrameEntity entity,
                                              ItemFrameEntityRenderState state,
                                              float tickDelta,
                                              CallbackInfo ci) {
        // The REMEMBERED seat (synced entity data), the same number the bounding box carries.
        // Do not re-read the support here: the seat is minted once when the frame is hung and
        // must not follow a wall rebuilt at a different height (LAW 1; ruling 2026-09-13).
        double dy = ((HangingSeatDyHolder) entity).slabbed$hangSeatDy();
        if (Math.abs(dy) >= 1.0e-6d) {
            state.y += dy;
        }
    }
}
