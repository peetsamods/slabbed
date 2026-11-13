package com.slabbed.mixin.client;

import com.slabbed.client.LoweredBlockOverlay;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The 26.3 screen overlay retains near-plane eligibility in the stored block frame. */
@Mixin(LevelExtractor.class)
public abstract class ModernBlockOverlayMixin {
    @Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
    private static void slabbed$drawnOverlay(LocalPlayer player, Frustum frustum,
                                           CallbackInfoReturnable<BlockState> cir) {
        cir.setReturnValue(LoweredBlockOverlay.find(player,
                frustum.getNearPlaneBounds().move(player.getEyePosition())));
    }
}
