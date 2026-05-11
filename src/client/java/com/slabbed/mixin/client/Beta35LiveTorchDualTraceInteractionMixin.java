package com.slabbed.mixin.client;

import com.slabbed.util.Beta35LiveTorchCaptureRecorder;
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
public abstract class Beta35LiveTorchDualTraceInteractionMixin {
    private static final ThreadLocal<Beta35LiveTorchCaptureRecorder.PlacementAttemptSnapshot>
            SLABBED$BETA35_LIVE_TORCH_ATTEMPT = new ThreadLocal<>();

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void slabbed$beta35LiveTorchDualTraceStart(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client == null ? null : client.world;
        if (!Beta35LiveTorchCaptureRecorder.dualTraceEnabled()
                || player == null
                || world == null
                || hand != Hand.MAIN_HAND) {
            return;
        }
        SLABBED$BETA35_LIVE_TORCH_ATTEMPT.set(Beta35LiveTorchCaptureRecorder.startPlacementAttempt(
                world,
                player,
                player.getStackInHand(hand),
                hitResult,
                client == null ? null : client.crosshairTarget));
    }

    @Inject(method = "interactBlock", at = @At("RETURN"))
    private void slabbed$beta35LiveTorchDualTraceReturn(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        try {
            Beta35LiveTorchCaptureRecorder.PlacementAttemptSnapshot snapshot =
                    SLABBED$BETA35_LIVE_TORCH_ATTEMPT.get();
            MinecraftClient client = MinecraftClient.getInstance();
            ClientWorld world = client == null ? null : client.world;
            if (snapshot != null && world != null) {
                Beta35LiveTorchCaptureRecorder.finishPlacementAttempt(world, snapshot, cir.getReturnValue());
            }
        } finally {
            SLABBED$BETA35_LIVE_TORCH_ATTEMPT.remove();
        }
    }
}
