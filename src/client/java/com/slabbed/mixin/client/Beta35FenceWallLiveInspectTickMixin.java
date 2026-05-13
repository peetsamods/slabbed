package com.slabbed.mixin.client;

import com.slabbed.util.Beta35FenceWallLiveInspectRecorder;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class Beta35FenceWallLiveInspectTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void slabbed$beta35FenceWallLiveInspectStartup(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        Beta35FenceWallLiveInspectRecorder.logStartup(client.world, client.player);
        Beta35FenceWallLiveInspectRecorder.recordNoTarget(
                client.world,
                client.player,
                client.crosshairTarget);
    }
}
