package com.slabbed.mixin;

import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
@Mixin(Painting.class)
public abstract class PaintingRememberedSeatMixin extends HangingEntity {

    @Unique
    private static final String SLABBED$HANG_DY_KEY = "slabbed:hang_dy";

    protected PaintingRememberedSeatMixin(EntityType<? extends HangingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V", at = @At("TAIL"))
    private void slabbed$saveHangSeat(ValueOutput output, CallbackInfo ci) {
        HangingSeatDyHolder holder = (HangingSeatDyHolder) this;
        if (holder.slabbed$hasHangSeat()) {
            output.putDouble(SLABBED$HANG_DY_KEY, holder.slabbed$hangSeatDy());
        }
    }

    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V", at = @At("TAIL"))
    private void slabbed$loadHangSeat(ValueInput input, CallbackInfo ci) {
        double dy = input.getDoubleOr(SLABBED$HANG_DY_KEY, Double.NaN);
        if (Double.isFinite(dy)) {
            ((HangingSeatDyHolder) this).slabbed$restoreHangSeatDy(dy);
            this.recalculateBoundingBox();
        }
    }
}
