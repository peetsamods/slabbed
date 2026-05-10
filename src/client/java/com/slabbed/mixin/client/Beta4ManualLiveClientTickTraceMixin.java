package com.slabbed.mixin.client;

import com.slabbed.util.Beta4ManualLiveTrace;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class Beta4ManualLiveClientTickTraceMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void slabbed$beta4ManualLiveDelayedFinalTick(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.world != null) {
            Beta4ManualLiveTrace.tickClient(client.world);
        }
    }
}
