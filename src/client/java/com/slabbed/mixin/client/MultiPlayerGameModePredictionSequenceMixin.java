package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.PlacementDyPredictionJournal;
import com.slabbed.network.PlacementDyPredictionBridge;
import com.slabbed.util.LiveCursorIntentRecorder;
import com.slabbed.util.SlabSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.LinkedHashMap;
import java.util.Locale;

/** Exposes vanilla's exact sequence only while PredictiveAction.predict runs. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModePredictionSequenceMixin {

    @Shadow @Final private Minecraft minecraft;

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
        LiveCursorIntentRecorder.UsePacketScope recorderScope =
                slabbed$openRecorderScope(sequence);
        try {
            packet = original.call(action, sequence);
            if (recorderScope != null
                    && !recorderScope.claimed()
                    && packet instanceof ServerboundUseItemOnPacket usePacket) {
                slabbed$recordGenericClientUse(usePacket);
            }
            completed = true;
        } finally {
            if (recorderScope != null) {
                recorderScope.close();
            }
            PlacementDyPredictionBridge.closeSequence();
            if (!completed) {
                PlacementDyPredictionJournal.abortStaged(sequence);
            }
        }
        return PlacementDyPredictionJournal.commitAfterPrediction(sequence, packet);
    }

    private LiveCursorIntentRecorder.UsePacketScope slabbed$openRecorderScope(int sequence) {
        if (!LiveCursorIntentRecorder.enabled()
                || minecraft == null
                || minecraft.player == null
                || minecraft.level == null) {
            return null;
        }
        return LiveCursorIntentRecorder.openUsePacketScope(
                "client",
                sequence,
                minecraft.player.getUUID().toString(),
                minecraft.level.dimension().identifier().toString());
    }

    private void slabbed$recordGenericClientUse(ServerboundUseItemOnPacket packet) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        BlockHitResult hit = packet.getHitResult();
        BlockPos target = hit.getBlockPos();
        BlockState afterState = minecraft.level.getBlockState(target);
        ItemStack heldStack = minecraft.player.getItemInHand(packet.getHand());
        double afterDy = SlabSupport.getYOffset(minecraft.level, target, afterState);
        double afterStoredDy =
                SlabAnchorAttachment.storedPlacementDy(minecraft.level, target);
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("actionType", "use_block");
        row.put("player", minecraft.player.getName().getString());
        row.put("heldItem", heldStack == null || heldStack.isEmpty()
                ? "empty"
                : BuiltInRegistries.ITEM.getKey(heldStack.getItem()).toString());
        row.put("clickedOwnerPos", target.toShortString());
        row.put("clickedFace", hit.getDirection().getSerializedName());
        row.put("clickedHitVec", slabbed$recorderVec(hit.getLocation()));
        row.put("placementPos", "none");
        row.put("beforeState", "unknown");
        row.put("beforeDy", "unknown");
        row.put("beforeStoredDy", "unknown");
        row.put("afterState", afterState.toString());
        row.put("afterDy", slabbed$recorderDy(afterDy));
        row.put("afterStoredDy", slabbed$recorderDy(afterStoredDy));
        row.put("actualResult", "unknown");
        row.put("validationDecision", "not_observable_client_prediction");
        row.put("handlerDecision", "prediction_returned_packet");
        row.put("functionalOutcome", "client_prediction_state_observed");
        LiveCursorIntentRecorder.recordAction(row);
    }

    private static String slabbed$recorderDy(double dy) {
        return Double.isFinite(dy) ? String.format(Locale.ROOT, "%.6f", dy) : "unknown";
    }

    private static String slabbed$recorderVec(Vec3 vec) {
        return vec == null
                ? "unknown"
                : String.format(
                        Locale.ROOT,
                        "%.6f,%.6f,%.6f",
                        vec.x,
                        vec.y,
                        vec.z);
    }
}
