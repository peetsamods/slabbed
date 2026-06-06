package com.slabbed.mixin;

import com.slabbed.util.SlabbedAuditBridge;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerRecorderMixin {

    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onPlayerInteractBlock", at = @At("HEAD"), require = 0)
    private void slabbed$recordServerInteractBlockStart(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        SlabbedAuditBridge.invokeRecorder(
                "recordServerInteract",
                new Class<?>[]{PlayerEntity.class, Hand.class, BlockHitResult.class, int.class},
                this.player,
                packet.getHand(),
                packet.getBlockHitResult(),
                packet.getSequence());
    }

    @Inject(method = "onPlayerInteractBlock", at = @At("RETURN"), require = 0)
    private void slabbed$recordServerInteractBlockReturn(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        SlabbedAuditBridge.invokeRecorder(
                "recordServerInteractResult",
                new Class<?>[]{int.class},
                packet.getSequence());
    }
}
