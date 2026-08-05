package com.slabbed.mixin.client;

import com.slabbed.client.PlacementDyPredictionClient;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notifies the placement-dy prediction overlay only AFTER vanilla has processed its own pending
 * updates for this cumulative acknowledgement ({@code handlePlayerActionResponse} is Mojang's
 * {@code handleBlockChangedAck}). The acknowledgement alone does not retire a prediction — see
 * {@code PlacementDyOverlay#onVanillaAcknowledgement} for why retirement is lazy.
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldPlacementDyAckMixin {

    @Inject(method = "handlePlayerActionResponse(I)V", at = @At("RETURN"))
    private void slabbed$c3AfterVanillaAcknowledgement(int sequence, CallbackInfo ci) {
        PlacementDyPredictionClient.onVanillaAcknowledgement((ClientWorld) (Object) this, sequence);
    }
}
