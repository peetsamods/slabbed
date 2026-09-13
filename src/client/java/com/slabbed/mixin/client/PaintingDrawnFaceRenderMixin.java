package com.slabbed.mixin.client;

import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.client.render.entity.PaintingEntityRenderer;
import net.minecraft.client.render.entity.state.PaintingEntityRenderState;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a painting on the face it remembers being hung on (maintainer ruling, 2026-09-13). The
 * entity's real position stays at grid height — see {@code HangingEntityRememberedSeatMixin} — so
 * the render state's Y is moved by the remembered seat, the same number the bounding box carries.
 */
@Mixin(PaintingEntityRenderer.class)
public abstract class PaintingDrawnFaceRenderMixin {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/decoration/painting/PaintingEntity;Lnet/minecraft/client/render/entity/state/PaintingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void slabbed$drawOnRememberedSeat(PaintingEntity entity, PaintingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        double dy = ((HangingSeatDyHolder) entity).slabbed$hangSeatDy();
        if (Math.abs(dy) >= 1.0e-6d) {
            state.y += dy;
        }
    }
}
