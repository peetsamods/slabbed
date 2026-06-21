package com.slabbed.mixin.client;

import com.slabbed.client.TargetDyOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the {@code /slabdy}-style target-dy overlay at the tail of the vanilla
 * HUD extract pass. Uses a direct {@code Gui} mixin instead of Fabric's
 * {@code HudRenderCallback} because this port's non-standard Loom setup does
 * not expose {@code fabric-rendering-v1} to the client source set.
 */
@Mixin(Hud.class)
public class TargetDyHudMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL"))
    private void slabbed$renderTargetDyOverlay(GuiGraphicsExtractor extractor, DeltaTracker delta, CallbackInfo ci) {
        TargetDyOverlay.render(extractor);
    }
}
