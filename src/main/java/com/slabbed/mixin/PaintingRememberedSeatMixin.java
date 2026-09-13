package com.slabbed.mixin;

import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A painting's remembered seat is declared and persisted here (maintainer ruling, 2026-09-13:
 * paintings hang on the drawn face like item frames, and remember it).
 *
 * <p>The seat lives in {@code HangingEntityRememberedSeatMixin}; this class only forwards the
 * hooks that {@code HangingEntity} does not declare on this version, and persists the number, the
 * same way {@code ItemFrameWysiwygMixin} does for frames. Keep the two sets of forwarders
 * identical. A painting does NOT override the shared facing setter, so its mint is already taken
 * there and must not be forwarded a second time.
 */
@Mixin(Painting.class)
public abstract class PaintingRememberedSeatMixin extends HangingEntity {

    @Unique
    private static final String SLABBED$HANG_DY_KEY = "slabbed:hang_dy";

    protected PaintingRememberedSeatMixin(EntityType<? extends HangingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "defineSynchedData(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V", at = @At("TAIL"))
    private void slabbed$defineHangSeatField(SynchedEntityData.Builder builder, CallbackInfo ci) {
        ((HangingSeatDyHolder) this).slabbed$defineHangSeat(builder);
    }

    @Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V", at = @At("TAIL"))
    private void slabbed$relayoutOnSyncedSeat(EntityDataAccessor<?> key, CallbackInfo ci) {
        ((HangingSeatDyHolder) this).slabbed$onHangSeatDataUpdated(key);
    }

    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void slabbed$saveHangSeat(CompoundTag tag, CallbackInfo ci) {
        HangingSeatDyHolder holder = (HangingSeatDyHolder) this;
        if (holder.slabbed$hasHangSeat()) {
            tag.putDouble(SLABBED$HANG_DY_KEY, holder.slabbed$hangSeatDy());
        }
    }

    /** Vanilla re-lays the box while reading (facing); the seat arrives after, so lay it out again. */
    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void slabbed$loadHangSeat(CompoundTag tag, CallbackInfo ci) {
        if (!tag.contains(SLABBED$HANG_DY_KEY, Tag.TAG_ANY_NUMERIC)) {
            return;
        }
        double dy = tag.getDouble(SLABBED$HANG_DY_KEY);
        if (Double.isFinite(dy)) {
            ((HangingSeatDyHolder) this).slabbed$restoreHangSeatDy(dy);
            this.recalculateBoundingBox();
        }
    }
}
