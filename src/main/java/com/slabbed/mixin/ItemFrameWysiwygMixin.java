package com.slabbed.mixin;

import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An item frame's remembered seat is declared, minted and persisted here; glow frames inherit
 * every hook.
 *
 * <p>The seat itself — the one derivation, the synced field, the shift applied to the bounding box
 * — lives in {@code HangingEntityRememberedSeatMixin}, shared with paintings. This class only
 * forwards the hooks that {@code HangingEntity} does not declare on this version, and persists the
 * number. A frame saved before the seat existed has no key and mints from its wall on its first
 * server layout (one-time migration). Keep these forwarders identical to the painting's.
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

    @Inject(method = "defineSynchedData(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V", at = @At("TAIL"))
    private void slabbed$defineHangSeatField(SynchedEntityData.Builder builder, CallbackInfo ci) {
        ((HangingSeatDyHolder) this).slabbed$defineHangSeat(builder);
    }

    /** The frame overrides the shared facing setter, so the mint is forwarded from here. */
    @Inject(method = "setDirection(Lnet/minecraft/core/Direction;)V", at = @At("TAIL"))
    private void slabbed$mintHangSeat(CallbackInfo ci) {
        ((HangingSeatDyHolder) this).slabbed$mintHangSeatFromWall();
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
