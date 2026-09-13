package com.slabbed.mixin;

import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A painting's remembered seat survives a save and reload (maintainer ruling, 2026-09-13: paintings
 * hang on the drawn face like item frames, and remember it).
 *
 * <p>The seat lives in {@code HangingEntityRememberedSeatMixin}; this class only persists it, the
 * same way {@code ItemFrameWysiwygMixin} does for frames. Keep the two hooks identical.
 */
@Mixin(PaintingEntity.class)
public abstract class PaintingRememberedSeatMixin extends AbstractDecorationEntity {

    @Unique
    private static final String SLABBED$HANG_DY_KEY = "slabbed:hang_dy";

    protected PaintingRememberedSeatMixin(EntityType<? extends AbstractDecorationEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "writeCustomData(Lnet/minecraft/storage/WriteView;)V", at = @At("TAIL"))
    private void slabbed$saveHangSeat(WriteView output, CallbackInfo ci) {
        HangingSeatDyHolder holder = (HangingSeatDyHolder) this;
        if (holder.slabbed$hasHangSeat()) {
            output.putDouble(SLABBED$HANG_DY_KEY, holder.slabbed$hangSeatDy());
        }
    }

    @Inject(method = "readCustomData(Lnet/minecraft/storage/ReadView;)V", at = @At("TAIL"))
    private void slabbed$loadHangSeat(ReadView input, CallbackInfo ci) {
        double dy = input.getDouble(SLABBED$HANG_DY_KEY, Double.NaN);
        if (Double.isFinite(dy)) {
            ((HangingSeatDyHolder) this).slabbed$restoreHangSeatDy(dy);
            this.updateAttachmentPosition();
        }
    }
}
