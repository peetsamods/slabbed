package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.slabbed.client.FwaPlacementSeat;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every animation carries the placement offset the client thread last published for it. Culling and
 * rendering use those same translated bounds; the bounds getter never reads the world, because occlusion
 * culling may call it from a worker thread.
 */
@Pseudo
@Mixin(targets = "fr.madu59.fwa.anims.Animation", remap = false)
public abstract class FancyWorldAnimationsBoundsMixin implements FwaPlacementSeat {
    @Unique private volatile double slabbed$seatDy;
    @Unique private volatile boolean slabbed$seatPublished;

    @Override
    public void slabbed$publishSeat(double dy) {
        slabbed$seatDy = dy;
        slabbed$seatPublished = true;
    }

    @Override
    public double slabbed$publishedSeat() {
        return slabbed$seatPublished ? slabbed$seatDy : Double.NaN;
    }

    /** The render loop ticks each animation before its occlusion and frustum checks. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void slabbed$publishBeforeCulling(double tick, CallbackInfo ci) {
        FwaPlacementSeat.publish(this, ((FancyWorldAnimationsAccessor) this).slabbed$getPos());
    }

    @ModifyReturnValue(method = "getBoundingBox", at = @At("RETURN"))
    private AABB slabbed$placedBounds(AABB original) {
        if (original == null) return original;
        double dy = slabbed$publishedSeat();
        return Double.isNaN(dy) || dy == 0 ? original : original.move(0, dy, 0);
    }
}
