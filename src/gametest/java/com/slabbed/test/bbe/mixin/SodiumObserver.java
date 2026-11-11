package com.slabbed.test.bbe.mixin;

import com.slabbed.test.bbe.RenderCapture;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumObserver {
    @Shadow private Vector3f posOffset;

    @Inject(method = "bufferQuad", at = @At("HEAD"), remap = false)
    private void observe(@Coerce Object quad, float[] brightness, @Coerce Object material, CallbackInfo ci) {
        RenderCapture.mesh(this, quad, posOffset);
    }
}
