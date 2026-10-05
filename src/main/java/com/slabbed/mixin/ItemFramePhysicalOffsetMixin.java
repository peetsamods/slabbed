package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.HangingSeatDyHolder;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The attachment owns one saved physical height; neighbor edits cannot move it (LAW.md).
 *
 * <p>The seat is minted on the server thread only, and only from chunks that are already complete:
 * entity loading may run inside the promotion of the very chunk being loaded, so the mint never asks
 * the world for a chunk that could still be pending. While save data is being read only a saved seat
 * is restored; a seat-less save mints right after the read when its chunks are ready, otherwise on a
 * later tick. Every layout starts from the grid box, so the seat is applied exactly once.
 */
@Mixin(ItemFrameEntity.class)
public abstract class ItemFramePhysicalOffsetMixin extends AbstractDecorationEntity implements HangingSeatDyHolder {
    @Unique
    private static final TrackedData<Long> SLABBED_FRAME_DY = DataTracker.registerData(
            ItemFrameEntity.class, TrackedDataHandlerRegistry.LONG);
    @Unique
    private static final String SLABBED_FRAME_DY_KEY = "slabbed:frame_dy";

    /** Save data is being read (possibly inside chunk promotion): restore only, never mint. */
    @Unique
    private boolean slabbed$readingData;

    /** A mint was skipped because a chunk was not ready or the read was running; a tick retries it. */
    @Unique
    private boolean slabbed$seatPending;

    protected ItemFramePhysicalOffsetMixin(EntityType<? extends AbstractDecorationEntity> type, World world) {
        super(type, world);
    }

    @Override
    public double slabbed$hangSeatDy() {
        double dy = Double.longBitsToDouble(dataTracker.get(SLABBED_FRAME_DY));
        return Double.isFinite(dy) ? dy : 0.0d;
    }

    @Override
    public boolean slabbed$hasHangSeat() {
        return Double.isFinite(Double.longBitsToDouble(dataTracker.get(SLABBED_FRAME_DY)));
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void slabbed$initFrameDy(CallbackInfo ci) {
        this.dataTracker.startTracking(SLABBED_FRAME_DY, Double.doubleToRawLongBits(Double.NaN));
    }

    // 1.20.1 lays the frame out in updateAttachmentPosition (position, then box); the seat is applied
    // to both at its tail. Later versions compute the box in calculateBoundingBox instead.
    @Inject(method = "updateAttachmentPosition", at = @At("TAIL"))
    private void slabbed$physicalFrameBox(CallbackInfo ci) {
        double dy = Double.longBitsToDouble(dataTracker.get(SLABBED_FRAME_DY));
        if (!Double.isFinite(dy)) {
            // The server decides once, from the backing cell's provenance; the client only applies the
            // synced value, so a frame on a legacy cell keeps the vanilla box on both sides.
            if (getWorld().isClient) {
                return;
            }
            dy = slabbed$tryMintSeat(attachmentPos, facing);
        }
        if (Double.isFinite(dy) && dy != 0.0d) {
            setPos(getX(), getY() + dy, getZ());
            setBoundingBox(getBoundingBox().offset(0.0d, dy, 0.0d));
        }
    }

    /**
     * The ONE derivation. Answers the seat minted now, or NaN when nothing was minted (seat already
     * present, legacy cell, or deferred). Never waits on a chunk: the reading and thread checks come
     * before any chunk access, both the attachment and the support chunk must already be complete,
     * and the support is read from its chunk.
     */
    @Unique
    private double slabbed$tryMintSeat(BlockPos attached, Direction facing) {
        if (slabbed$hasHangSeat()) {
            slabbed$seatPending = false;
            return Double.NaN;
        }
        if (attached == null || facing == null) {
            return Double.NaN;
        }
        if (slabbed$readingData || !(getWorld() instanceof ServerWorld world) || !world.getServer().isOnThread()) {
            slabbed$seatPending = true;
            return Double.NaN;
        }
        if (world.getChunkManager().getWorldChunk(attached.getX() >> 4, attached.getZ() >> 4) == null) {
            slabbed$seatPending = true;
            return Double.NaN;
        }
        BlockPos backing = attached.offset(facing.getOpposite());
        WorldChunk backingChunk = world.getChunkManager().getWorldChunk(backing.getX() >> 4, backing.getZ() >> 4);
        if (backingChunk == null) {
            slabbed$seatPending = true;
            return Double.NaN;
        }
        slabbed$seatPending = false;
        // Provenance and stored-height reads below touch only the backing chunk, which the readiness
        // check above has just proven complete, so they never wait. Keep every chunk access after
        // the reading and thread checks.
        if (!SlabAnchorAttachment.usesFrozenPlacementHeight(world, backing)) {
            return Double.NaN;
        }
        BlockState support = backingChunk.getBlockState(backing);
        double dy = SlabSupport.getYOffset(world, backing, support);
        if (!Double.isFinite(dy)) {
            dy = 0.0d;
        }
        dataTracker.set(SLABBED_FRAME_DY, Double.doubleToRawLongBits(dy));
        return dy;
    }

    /** Save data may be read inside chunk promotion: only restore saved seats until the read finishes. */
    @Override
    public void readNbt(NbtCompound nbt) {
        slabbed$readingData = true;
        try {
            super.readNbt(nbt);
        } finally {
            slabbed$readingData = false;
        }
        slabbed$seatPending = !getWorld().isClient && !slabbed$hasHangSeat();
        BlockPos entityPos = getBlockPos();
        if (getWorld() instanceof ServerWorld world && world.getServer().isOnThread()
                && world.getChunkManager().getWorldChunk(entityPos.getX() >> 4, entityPos.getZ() >> 4) != null
                && Double.isFinite(slabbed$tryMintSeat(attachmentPos, facing))) {
            updateAttachmentPosition();
        }
    }

    /** Retries only a deferred mint; a legacy cell that was evaluated keeps the vanilla box. */
    @Override
    public void tick() {
        if (slabbed$seatPending && Double.isFinite(slabbed$tryMintSeat(attachmentPos, facing))) {
            updateAttachmentPosition();
        }
        super.tick();
    }

    /**
     * Tracker and position packets use the attachment cell, the same basis as the spawn packet; the
     * seat reaches clients only as synced data.
     */
    @Override
    public Vec3d getSyncedPos() {
        return Vec3d.of(attachmentPos);
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
