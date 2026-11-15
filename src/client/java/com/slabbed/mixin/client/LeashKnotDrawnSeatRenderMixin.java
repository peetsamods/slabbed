package com.slabbed.mixin.client;

import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The knot's mesh shares its remembered bounding-box and rope seat; grid attachment stays vanilla. */
@Mixin(EntityRenderer.class)
public abstract class LeashKnotDrawnSeatRenderMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void slabbed$drawKnotAtRememberedSeat(Entity entity, EntityRenderState state,
                                                float partialTick, CallbackInfo ci) {
        if (entity instanceof LeashFenceKnotEntity) {
            state.y += ((HangingSeatDyHolder) entity).slabbed$hangSeatDy();
        }
    }
}
