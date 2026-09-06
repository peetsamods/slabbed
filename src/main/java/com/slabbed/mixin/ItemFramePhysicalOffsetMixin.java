package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
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

/** The attachment owns one saved physical height; neighbor edits cannot move it (LAW.md). */
@Mixin(ItemFrameEntity.class)
public abstract class ItemFramePhysicalOffsetMixin extends AbstractDecorationEntity {
    @Unique
    private static final TrackedData<Long> SLABBED_FRAME_DY = DataTracker.registerData(
            ItemFrameEntity.class, TrackedDataHandlerRegistry.LONG);
    @Unique
    private static final String SLABBED_FRAME_DY_KEY = "slabbed:frame_dy";

    protected ItemFramePhysicalOffsetMixin(EntityType<? extends AbstractDecorationEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void slabbed$initFrameDy(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(SLABBED_FRAME_DY, Double.doubleToRawLongBits(Double.NaN));
    }

    @Inject(method = "calculateBoundingBox", at = @At("RETURN"), cancellable = true)
    private void slabbed$physicalFrameBox(BlockPos attached, Direction facing,
                                         CallbackInfoReturnable<Box> cir) {
        double dy = Double.longBitsToDouble(dataTracker.get(SLABBED_FRAME_DY));
        if (!Double.isFinite(dy)) {
            // The server decides once, from the backing cell's provenance; the client only applies the
            // synced value, so a frame on a legacy cell keeps the vanilla box on both sides.
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
            dataTracker.set(SLABBED_FRAME_DY, Double.doubleToRawLongBits(dy));
        }
        if (Double.isFinite(dy) && dy != 0.0d) {
            cir.setReturnValue(cir.getReturnValue().offset(0.0d, dy, 0.0d));
        }
    }

    @Inject(method = "onTrackedDataSet", at = @At("TAIL"))
    private void slabbed$applySyncedFrameDy(TrackedData<?> data, CallbackInfo ci) {
        if (SLABBED_FRAME_DY.equals(data) && getWorld().isClient) {
            updateAttachmentPosition();
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void slabbed$writeFrameDy(NbtCompound nbt, CallbackInfo ci) {
        double dy = Double.longBitsToDouble(dataTracker.get(SLABBED_FRAME_DY));
        if (Double.isFinite(dy)) {
            nbt.putDouble(SLABBED_FRAME_DY_KEY, dy);
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("HEAD"))
    private void slabbed$readFrameDy(NbtCompound nbt, CallbackInfo ci) {
        double dy = nbt.contains(SLABBED_FRAME_DY_KEY, 99)
                ? nbt.getDouble(SLABBED_FRAME_DY_KEY) : Double.NaN;
        dataTracker.set(SLABBED_FRAME_DY, Double.doubleToRawLongBits(dy));
    }
}
