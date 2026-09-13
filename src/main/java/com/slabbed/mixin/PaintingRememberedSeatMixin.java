package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.HangingSeatDyHolder;
import com.slabbed.util.SlabSupport;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A painting hangs on the face it REMEMBERS being hung on (LAW.md corollary; maintainer ruling,
 * 2026-09-13). The same mechanism as {@code ItemFramePhysicalOffsetMixin}: the server mints the seat
 * ONCE, from the backing cell's stored height and only when that cell carries provenance, keeps it in
 * synced tracked data and in NBT, and never re-reads the wall. Vanilla derives the position from the
 * box on this line, so the seat is physical: box and position move together. A painting on a legacy
 * cell keeps the vanilla box on both sides.
 */
@Mixin(PaintingEntity.class)
public abstract class PaintingRememberedSeatMixin extends AbstractDecorationEntity implements HangingSeatDyHolder {
    @Unique
    private static final TrackedData<Long> SLABBED_HANG_DY = DataTracker.registerData(
            PaintingEntity.class, TrackedDataHandlerRegistry.LONG);
    @Unique
    private static final String SLABBED_HANG_DY_KEY = "slabbed:hang_dy";

    protected PaintingRememberedSeatMixin(EntityType<? extends AbstractDecorationEntity> type, World world) {
        super(type, world);
    }

    @Override
    public double slabbed$hangSeatDy() {
        double dy = Double.longBitsToDouble(dataTracker.get(SLABBED_HANG_DY));
        return Double.isFinite(dy) ? dy : 0.0d;
    }

    @Override
    public boolean slabbed$hasHangSeat() {
        return Double.isFinite(Double.longBitsToDouble(dataTracker.get(SLABBED_HANG_DY)));
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void slabbed$initHangDy(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(SLABBED_HANG_DY, Double.doubleToRawLongBits(Double.NaN));
    }

    /** The ONE derivation, then the remembered number forever after. */
    @Inject(method = "calculateBoundingBox", at = @At("RETURN"), cancellable = true)
    private void slabbed$physicalPaintingBox(BlockPos attached, Direction facing,
                                            CallbackInfoReturnable<Box> cir) {
        double dy = Double.longBitsToDouble(dataTracker.get(SLABBED_HANG_DY));
        if (!Double.isFinite(dy)) {
            if (getWorld().isClient) {
                return;
            }
            BlockPos backing = attached.offset(facing.getOpposite());
            if (!SlabAnchorAttachment.usesFrozenPlacementHeight(getWorld(), backing)) {
                return;
            }
            dy = SlabSupport.getYOffset(getWorld(), backing, getWorld().getBlockState(backing));
            if (!Double.isFinite(dy)) {
                dy = 0.0d;
            }
            dataTracker.set(SLABBED_HANG_DY, Double.doubleToRawLongBits(dy));
        }
        if (Double.isFinite(dy) && dy != 0.0d) {
            cir.setReturnValue(cir.getReturnValue().offset(0.0d, dy, 0.0d));
        }
    }

    @Inject(method = "onTrackedDataSet", at = @At("TAIL"))
    private void slabbed$applySyncedHangDy(TrackedData<?> data, CallbackInfo ci) {
        if (SLABBED_HANG_DY.equals(data) && getWorld().isClient) {
            updateAttachmentPosition();
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void slabbed$writeHangDy(NbtCompound nbt, CallbackInfo ci) {
        double dy = Double.longBitsToDouble(dataTracker.get(SLABBED_HANG_DY));
        if (Double.isFinite(dy)) {
            nbt.putDouble(SLABBED_HANG_DY_KEY, dy);
        }
    }

    /** Restored BEFORE vanilla reads the facing and lays the box out, so the first layout is seated. */
    @Inject(method = "readCustomDataFromNbt", at = @At("HEAD"))
    private void slabbed$readHangDy(NbtCompound nbt, CallbackInfo ci) {
        double dy = nbt.contains(SLABBED_HANG_DY_KEY, 99)
                ? nbt.getDouble(SLABBED_HANG_DY_KEY) : Double.NaN;
        dataTracker.set(SLABBED_HANG_DY, Double.doubleToRawLongBits(dy));
    }
}
