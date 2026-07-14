package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.client.PlacementDyPredictionJournal;
import com.slabbed.network.PlacementDyPredictionBridge;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Exposes vanilla's exact sequence only while PredictiveAction.predict runs. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModePredictionSequenceMixin {

    @WrapOperation(
            method = "startPrediction",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/prediction/PredictiveAction;predict(I)Lnet/minecraft/network/protocol/Packet;")
    )
    private Packet<ServerGamePacketListener> slabbed$c3SequenceAndDeclaration(
            PredictiveAction action,
            int sequence,
            Operation<Packet<ServerGamePacketListener>> original
    ) {
        Packet<ServerGamePacketListener> packet;
        boolean completed = false;
        PlacementDyPredictionBridge.openSequence(sequence);
        try {
            packet = original.call(action, sequence);
            completed = true;
        } finally {
            PlacementDyPredictionBridge.closeSequence();
            if (!completed) {
                PlacementDyPredictionJournal.abortStaged(sequence);
            }
        }
        return PlacementDyPredictionJournal.commitAfterPrediction(sequence, packet);
    }
}
