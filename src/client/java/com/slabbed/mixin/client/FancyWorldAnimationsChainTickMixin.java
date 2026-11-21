package com.slabbed.mixin.client;

import com.slabbed.client.FwaPlacementSeat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hanging columns override the base tick, so they publish their placement offset here. */
@Pseudo
@Mixin(targets = "fr.madu59.fwa.anims.ChainAnimation", remap = false)
public abstract class FancyWorldAnimationsChainTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void slabbed$publishBeforeCulling(double tick, CallbackInfo ci) {
        FwaPlacementSeat.publish(this, ((FancyWorldAnimationsAccessor) this).slabbed$getPos());
    }
}
