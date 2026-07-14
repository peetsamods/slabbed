package com.slabbed.mixin.client;

import com.slabbed.client.PlacementDyPredictionJournal;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Notifies C3 only after vanilla has synchronized every prediction through this cumulative ack. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelBlockChangedAckMixin {

    @Inject(method = "handleBlockChangedAck", at = @At("RETURN"))
    private void slabbed$c3AfterVanillaAcknowledgement(int sequence, CallbackInfo ci) {
        PlacementDyPredictionJournal.onVanillaAcknowledgement((ClientLevel) (Object) this, sequence);
    }
}
