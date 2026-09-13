package com.slabbed.mixin;

import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An item frame's remembered seat survives a save and reload.
 *
 * <p>The seat itself — minted once when the frame is hung, carried in entity data, applied to the
 * bounding box — lives in {@code HangingEntityRememberedSeatMixin}, shared with paintings. This
 * class only persists it: {@code HangingEntity} declares no save-data hooks, so each hung class
 * writes and reads the number itself. A frame saved before the seat existed has no key and mints
 * from its wall on its first server layout (one-time migration).
 *
 * <p>The entity's real position stays at grid height (the box moves, the position does not);
 * moving it corrupts the derived grid cell and {@code survives()} judges the wrong support.
 */
@Mixin(ItemFrame.class)
public abstract class ItemFrameWysiwygMixin extends HangingEntity {

    @Unique
    private static final String SLABBED$HANG_DY_KEY = "slabbed:hang_dy";

    protected ItemFrameWysiwygMixin(EntityType<? extends HangingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V", at = @At("TAIL"))
    private void slabbed$saveHangSeat(ValueOutput output, CallbackInfo ci) {
        HangingSeatDyHolder holder = (HangingSeatDyHolder) this;
        if (holder.slabbed$hasHangSeat()) {
            output.putDouble(SLABBED$HANG_DY_KEY, holder.slabbed$hangSeatDy());
        }
    }

    /** Vanilla re-lays the box while reading (direction); the seat arrives after, so lay it out again. */
    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V", at = @At("TAIL"))
    private void slabbed$loadHangSeat(ValueInput input, CallbackInfo ci) {
        double dy = input.getDoubleOr(SLABBED$HANG_DY_KEY, Double.NaN);
        if (Double.isFinite(dy)) {
            ((HangingSeatDyHolder) this).slabbed$restoreHangSeatDy(dy);
            this.recalculateBoundingBox();
        }
    }
}
