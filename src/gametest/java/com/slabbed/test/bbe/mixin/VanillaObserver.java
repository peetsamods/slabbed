package com.slabbed.test.bbe.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.slabbed.test.bbe.RenderCapture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntityRenderDispatcher.class, priority = 900)
public abstract class VanillaObserver {
    @Inject(method = "submit", at = @At("RETURN"))
    private void observe(BlockEntityRenderState state, PoseStack poses, SubmitNodeCollector queue,
            CameraRenderState camera, CallbackInfo ci) {
        if (!RenderCapture.watch.contains(state.blockPos)) return;
        double dy = poses.last().pose().m31() - (state.blockPos.getY() - camera.pos.y);
        RenderCapture.vanilla.put(state.blockPos.immutable(), dy);
        RenderCapture.vanillaCount.merge(state.blockPos.immutable(), 1, Integer::sum);
    }
}
