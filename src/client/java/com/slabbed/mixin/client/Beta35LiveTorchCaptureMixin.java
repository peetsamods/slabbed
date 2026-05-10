package com.slabbed.mixin.client;

import com.slabbed.util.Beta35LiveTorchCaptureRecorder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class Beta35LiveTorchCaptureMixin {
    @Shadow private MinecraftClient client;

    @Inject(method = "updateCrosshairTarget", at = @At("TAIL"))
    private void slabbed$beta35LiveTorchCapture(float tickProgress, CallbackInfo ci) {
        HitResult crosshairTarget = client == null ? null : client.crosshairTarget;
        Beta35LiveTorchCaptureRecorder.recordFrame(
                client == null ? null : client.world,
                client == null ? null : client.getCameraEntity(),
                client == null ? null : client.player,
                crosshairTarget,
                tickProgress
        );
    }
}
