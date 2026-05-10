package com.slabbed.mixin.client;

import com.slabbed.util.Beta4ManualLiveTrace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class Beta4ManualLiveInteractionTraceMixin {
    private static final ThreadLocal<Beta4ManualLiveTrace.ClickSnapshot> SLABBED$BETA4_MANUAL_CLICK =
            new ThreadLocal<>();

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void slabbed$beta4ManualLiveClickStart(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client == null ? null : client.world;
        if (!Beta4ManualLiveTrace.enabled() || player == null || world == null || hand != Hand.MAIN_HAND) {
            return;
        }
        SLABBED$BETA4_MANUAL_CLICK.set(Beta4ManualLiveTrace.startClientClick(
                world,
                player.getStackInHand(hand),
                hitResult,
                client == null ? null : client.crosshairTarget));
    }

    @Inject(method = "interactBlock", at = @At("RETURN"))
    private void slabbed$beta4ManualLiveClickReturn(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        try {
            Beta4ManualLiveTrace.ClickSnapshot snapshot = SLABBED$BETA4_MANUAL_CLICK.get();
            MinecraftClient client = MinecraftClient.getInstance();
            ClientWorld world = client == null ? null : client.world;
            if (snapshot != null && player != null && world != null) {
                Beta4ManualLiveTrace.finishClientClick(world, snapshot, cir.getReturnValue());
            }
        } finally {
            SLABBED$BETA4_MANUAL_CLICK.remove();
        }
    }
}
