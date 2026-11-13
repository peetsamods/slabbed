package com.slabbed.mixin.client;

import com.slabbed.client.LoweredBlockOverlay;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The 26.2 screen overlay follows stored block positions. */
@Mixin(ScreenEffectRenderer.class)
public abstract class LegacyBlockOverlayMixin {
    @Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
    private static void slabbed$drawnOverlay(Player player, CallbackInfoReturnable<BlockState> cir) {
        cir.setReturnValue(LoweredBlockOverlay.find(player, null));
    }
}
