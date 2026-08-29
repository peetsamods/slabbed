package com.slabbed.mixin.client;

import com.slabbed.util.Beta35SlabHeightHitAcceptanceRecorder;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class Beta35SlabHeightHitAcceptanceTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void slabbed$beta35SlabHeightHitAcceptanceStartup(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        Beta35SlabHeightHitAcceptanceRecorder.logStartup(client.world, client.player);
        Beta35SlabHeightHitAcceptanceRecorder.recordNoTarget(client.world, client.player, client.crosshairTarget);
    }
}
