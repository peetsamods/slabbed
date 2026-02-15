package com.slabbed.mixin.client;

import com.slabbed.util.RaycastOffsetContext;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "updateCrosshairTarget", at = @At("HEAD"))
    private void slabbed$enterRaycast(float tickProgress, CallbackInfo ci) {
        RaycastOffsetContext.enter();
    }

    @Inject(method = "updateCrosshairTarget", at = @At("RETURN"))
    private void slabbed$exitRaycast(float tickProgress, CallbackInfo ci) {
        RaycastOffsetContext.exit();
    }
}
