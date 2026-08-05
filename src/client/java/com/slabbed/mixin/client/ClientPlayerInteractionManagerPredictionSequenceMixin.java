package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.anchor.PlacementDyOverlay;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Exposes vanilla's exact predicted-action sequence for the duration of that action, and nothing
 * else. The client's copy of {@code BlockItem.place} runs inside this call — vanilla's own
 * client-side prediction of the use — so the placement-capture seam can read the number here and key
 * its overlay group by it.
 *
 * <p>The sequence has to be vanilla's, not one of ours: retirement is driven by
 * {@code ClientWorld.handlePlayerActionResponse}, whose numbering this is.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerPredictionSequenceMixin {

    @WrapOperation(
            method = "sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/SequencedPacketCreator;predict(I)Lnet/minecraft/network/packet/Packet;")
    )
    private Packet<ServerPlayPacketListener> slabbed$c3SequenceScope(
            SequencedPacketCreator creator,
            int sequence,
            Operation<Packet<ServerPlayPacketListener>> original
    ) {
        PlacementDyOverlay.openSequence(sequence);
        try {
            return original.call(creator, sequence);
        } finally {
            PlacementDyOverlay.closeSequence();
        }
    }
}
