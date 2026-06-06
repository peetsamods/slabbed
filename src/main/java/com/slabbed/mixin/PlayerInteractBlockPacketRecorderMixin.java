package com.slabbed.mixin;

import com.slabbed.util.SlabbedAuditBridge;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInteractBlockC2SPacket.class)
public abstract class PlayerInteractBlockPacketRecorderMixin {

    @Inject(method = "<init>(Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;I)V", at = @At("RETURN"), require = 0)
    private void slabbed$recordPacketSequence(Hand hand, BlockHitResult blockHitResult, int sequence, CallbackInfo ci) {
        SlabbedAuditBridge.invokeRecorder(
                "recordPacketSequence",
                new Class<?>[]{Hand.class, BlockHitResult.class, int.class},
                hand,
                blockHitResult,
                sequence);
    }
}
