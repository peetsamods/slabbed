package com.slabbed.mixin.client;

import com.slabbed.util.SlabbedAuditBridge;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionRecorderMixin {

    @Inject(method = "interactBlock", at = @At("HEAD"), require = 0)
    private void slabbed$recordInteractBlockStart(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        SlabbedAuditBridge.invokeRecorder(
                "recordClientInteract",
                new Class<?>[]{PlayerEntity.class, Hand.class, BlockHitResult.class},
                player,
                hand,
                hitResult);
    }

    @Inject(method = "interactBlock", at = @At("RETURN"), require = 0)
    private void slabbed$recordInteractBlockReturn(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        SlabbedAuditBridge.invokeRecorder(
                "recordClientInteractResult",
                new Class<?>[]{ActionResult.class},
                cir.getReturnValue());
    }
}
