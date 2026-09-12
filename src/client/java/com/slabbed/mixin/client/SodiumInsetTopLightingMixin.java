package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.slabbed.client.model.YOffsetEmitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Lets lowered top faces use Sodium's inset-face ambient-occlusion interpolation. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.model.light.smooth.SmoothLightPipeline", remap = false)
public abstract class SodiumInsetTopLightingMixin {
    @ModifyExpressionValue(method = "calculate", at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/model/light/data/LightDataAccess;unpackFC(I)Z"),
            remap = false)
    private boolean slabbed$useInsetTopLighting(boolean fullCube) {
        return fullCube && !YOffsetEmitter.isShadingInsetTop();
    }
}
