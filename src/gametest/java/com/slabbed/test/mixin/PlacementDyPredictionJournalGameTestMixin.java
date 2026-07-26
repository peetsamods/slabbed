package com.slabbed.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.client.PlacementDyPredictionJournal;
import com.slabbed.client.PlacementDyPredictionJournalGameTestAccess;
import com.slabbed.network.PlacementDyPredictionBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GameTest-only access and observation of the real client prediction journal. */
@Mixin(PlacementDyPredictionJournal.class)
public abstract class PlacementDyPredictionJournalGameTestMixin {
    @WrapOperation(
            method = "commitAfterPrediction",
            at = @At(value = "INVOKE",
                    target = "Lnet/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking;canSend(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;)Z"))
    private static boolean slabbed$failDeclarationForGameTest(
            CustomPacketPayload.Type<?> type,
            Operation<Boolean> original) {
        if (PlacementDyPredictionJournalGameTestAccess.consumeFailNextDeclaration()) {
            return false;
        }
        return original.call(type);
    }

    @Inject(
            method = "reconcile",
            at = @At(value = "INVOKE",
                    target = "Lcom/slabbed/client/PlacementDyPredictionJournal;placementEquivalent(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private static void slabbed$captureBranch(
            PlacementDyPredictionBridge.GroupSignature signature,
            CallbackInfo ci,
            @Local long key,
            @Local PlacementDyPredictionJournal.ReconcileBranch branch) {
        PlacementDyPredictionJournalGameTestAccess.recordBranch(key, branch);
    }

    @Inject(
            method = "reconcile",
            at = @At(value = "INVOKE",
                    target = "Lcom/slabbed/network/PlacementDyPredictionBridge;traceCorrectionWire(Ljava/lang/String;Lcom/slabbed/network/PlacementDyPredictionBridge$GroupSignature;)V"))
    private static void slabbed$captureApply(
            PlacementDyPredictionBridge.GroupSignature signature,
            CallbackInfo ci,
            @Local int appliedWrites,
            @Local ArrayList<BlockPos> ownedCells) {
        PlacementDyPredictionJournalGameTestAccess.recordApply(
                signature,
                appliedWrites,
                ownedCells,
                PlacementDyPredictionJournalGameTestAccess.overlayOwnersForMixin());
    }
}
