package com.slabbed.mixin.client;

import com.slabbed.client.IssueReportClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the notice's report action local, independent of command dispatcher availability. */
@Mixin(Screen.class)
public abstract class IssueReportClickMixin {
    @Inject(method = "clickCommandAction(Lnet/minecraft/client/player/LocalPlayer;Ljava/lang/String;Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At("HEAD"), cancellable = true)
    private static void slabbed$reportIssue(LocalPlayer player, String command, Screen parent, CallbackInfo ci) {
        if (IssueReportClient.handleNoticeClick(command)) ci.cancel();
    }
}
