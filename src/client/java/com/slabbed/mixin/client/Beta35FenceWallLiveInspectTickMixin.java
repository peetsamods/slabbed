package com.slabbed.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class Beta35FenceWallLiveInspectTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void slabbed$beta35FenceWallLiveInspectStartup(CallbackInfo ci) {
        // 26.1.2 port: diagnostic recorder deferred until client compile is restored.
    }
}
