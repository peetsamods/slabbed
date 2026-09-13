package com.slabbed.mixin;

import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.world.World;
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
 * class only persists it: {@code AbstractDecorationEntity} declares no save-data hooks, so each hung
 * class writes and reads the number itself. A frame saved before the seat existed has no key and
 * mints from its wall on its first server layout (one-time migration).
 *
 * <p>The entity's real position stays at grid height (the box moves, the position does not);
 * moving it corrupts the derived grid cell and {@code canStayAttached} judges the wrong support.
 */
@Mixin(ItemFrameEntity.class)
public abstract class ItemFrameWysiwygMixin extends AbstractDecorationEntity {

    @Unique
    private static final String SLABBED$HANG_DY_KEY = "slabbed:hang_dy";

    protected ItemFrameWysiwygMixin(EntityType<? extends AbstractDecorationEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "writeCustomData(Lnet/minecraft/storage/WriteView;)V", at = @At("TAIL"))
    private void slabbed$saveHangSeat(WriteView output, CallbackInfo ci) {
        HangingSeatDyHolder holder = (HangingSeatDyHolder) this;
        if (holder.slabbed$hasHangSeat()) {
            output.putDouble(SLABBED$HANG_DY_KEY, holder.slabbed$hangSeatDy());
        }
    }

    /** Vanilla re-lays the box while reading (direction); the seat arrives after, so lay it out again. */
    @Inject(method = "readCustomData(Lnet/minecraft/storage/ReadView;)V", at = @At("TAIL"))
    private void slabbed$loadHangSeat(ReadView input, CallbackInfo ci) {
        double dy = input.getDouble(SLABBED$HANG_DY_KEY, Double.NaN);
        if (Double.isFinite(dy)) {
            ((HangingSeatDyHolder) this).slabbed$restoreHangSeatDy(dy);
            this.updateAttachmentPosition();
        }
    }
}
