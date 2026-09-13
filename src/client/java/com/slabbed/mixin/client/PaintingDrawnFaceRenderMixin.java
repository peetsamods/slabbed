package com.slabbed.mixin.client;

import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.entity.state.PaintingRenderState;
import net.minecraft.world.entity.decoration.painting.Painting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a painting on the face it remembers being hung on (maintainer ruling, 2026-09-13). The
 * entity's real position stays at grid height — see {@code HangingEntityRememberedSeatMixin} — so
 * the render state's Y is moved by the remembered seat, the same number the bounding box carries.
 * The extract runs on the entity side of the render-state split, where the entity is readable.
 */
@Mixin(PaintingRenderer.class)
public abstract class PaintingDrawnFaceRenderMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/painting/Painting;Lnet/minecraft/client/renderer/entity/state/PaintingRenderState;F)V",
            at = @At("TAIL"))
    private void slabbed$drawOnRememberedSeat(Painting entity, PaintingRenderState state, float tickDelta, CallbackInfo ci) {
        double dy = ((HangingSeatDyHolder) entity).slabbed$hangSeatDy();
        if (Math.abs(dy) >= 1.0e-6d) {
            state.y += dy;
        }
    }
}
