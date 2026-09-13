package com.slabbed.mixin;

import com.slabbed.util.HangingSeatDyHolder;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A hung decoration REMEMBERS the height of the face it was hung on (LAW 1; maintainer ruling,
 * 2026-09-13: where it is placed is where it stays, for everything hung on a wall).
 *
 * <p>The seat is MINTED ONCE - when the decoration is first told which way it faces on the server,
 * with its support loaded, which is the moment the item hangs it - from the support's stored
 * height, and is then carried in synced entity data and in save data. Every later layout reads the
 * remembered number verbatim; nothing here re-reads the support. Rebuilding the wall behind a hung
 * frame at a different height is a change to the WALL, not to the frame (LAW.md, Law 1).
 *
 * <p>The entity's REAL position deliberately stays at grid height: moving it corrupts the derived
 * grid cell ({@code BlockPos.containing} in {@code HangingEntity.setPos} and in every server
 * position packet) and {@code survives()} then judges the wrong support. Only the bounding box
 * shifts here, and the render layer applies the same remembered seat to the drawing.
 *
 * <p>A decoration saved before this seat existed carries no number; its first server layout mints
 * one from the wall it hangs on today. That is a one-time migration, not a re-derivation.
 *
 * <p>WHY THE MINT IS AT THE HEAD OF {@code setDirection} on this line: here the direction setter
 * is not raw - it ends by laying the box out. Minting at its tail would leave that first layout
 * carrying no seat. The head is the equivalent moment: the position is already set (the hang
 * constructor and the load path both set it first), the direction is the argument, and the layout
 * that follows applies the freshly minted seat. Do not move this to the layout itself: a layout
 * runs on any position change, which is a read, not a placement.
 */
@Mixin(HangingEntity.class)
public abstract class HangingEntityRememberedSeatMixin extends Entity implements HangingSeatDyHolder {

    /** Raw bits of the seat; NaN bits mean "not minted yet". Synced so the client box and drawing agree. */
    @Unique
    private static final EntityDataAccessor<Long> SLABBED$HANG_DY =
            SynchedEntityData.defineId(HangingEntity.class, EntityDataSerializers.LONG);

    @Unique
    private static final long SLABBED$UNSET = Double.doubleToRawLongBits(Double.NaN);

    @Unique
    private static final double SLABBED$EPS = 1.0e-6d;

    @Shadow
    protected BlockPos pos;

    @Shadow
    protected Direction direction;

    @Shadow
    protected abstract void recalculateBoundingBox();

    /** Holds the seated box while {@code survives()} judges the unshifted grid cells. */
    @Unique
    private AABB slabbed$seatedBoxDuringSurvival;

    protected HangingEntityRememberedSeatMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public double slabbed$hangSeatDy() {
        if (!this.getEntityData().hasItem(SLABBED$HANG_DY)) {
            return 0.0d;
        }
        double dy = Double.longBitsToDouble(this.getEntityData().get(SLABBED$HANG_DY));
        return Double.isFinite(dy) ? dy : 0.0d;
    }

    @Override
    public boolean slabbed$hasHangSeat() {
        return this.getEntityData().hasItem(SLABBED$HANG_DY)
                && Double.isFinite(Double.longBitsToDouble(this.getEntityData().get(SLABBED$HANG_DY)));
    }

    @Override
    public void slabbed$restoreHangSeatDy(double dy) {
        if (!this.getEntityData().hasItem(SLABBED$HANG_DY)) {
            return;
        }
        this.getEntityData().set(SLABBED$HANG_DY, Double.doubleToRawLongBits(Double.isFinite(dy) ? dy : 0.0d));
    }

    /**
     * The key is declared per class because the subclasses here replace {@code defineSynchedData}
     * outright. The accessor itself is a static of this mixin ON PURPOSE: merged into
     * {@code HangingEntity}'s static initializer it allocates its synced id while the superclass
     * initializes, which is before any subclass claims one. Moving it to a plain helper class
     * would allocate the id whenever that class happened to load and could hand out an id a
     * subclass already took.
     */
    @Override
    public void slabbed$declareHangSeatKey() {
        if (!this.getEntityData().hasItem(SLABBED$HANG_DY)) {
            this.getEntityData().define(SLABBED$HANG_DY, SLABBED$UNSET);
        }
    }

    /**
     * The ONE derivation, at the moment the decoration learns which way it faces: both the item's
     * hang path and the load path set the direction with the position already known, and the box
     * is laid out right after. Server thread only (a hang from any other thread would answer
     * through the server and could wait on it); support loaded (an unloaded support answers
     * nothing, not "flush"). A saved seat restored by the per-class read hook wins over this mint.
     */
    @Override
    public void slabbed$mintHangSeatFor(Direction facing) {
        if (facing == null || this.pos == null || this.slabbed$hasHangSeat()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return;
        }
        BlockPos supportPos = this.pos.relative(facing.getOpposite());
        if (!level.hasChunkAt(supportPos)) {
            return;
        }
        BlockState support = level.getBlockState(supportPos);
        double dy = SlabSupport.getYOffset(level, supportPos, support);
        this.slabbed$restoreHangSeatDy(Double.isFinite(dy) ? dy : 0.0d);
    }

    /** Apply the remembered seat to the freshly laid-out box; the position set just before stays on the grid. */
    @Override
    public void slabbed$seatHangBox() {
        double dy = this.slabbed$hangSeatDy();
        if (Math.abs(dy) >= SLABBED$EPS) {
            this.setBoundingBox(this.getBoundingBox().move(0.0d, dy, 0.0d));
        }
    }

    /**
     * The CLIENT lays its box out from the spawn packet before the seat arrives; re-lay it when it
     * does. Server side the layout that follows the mint already applies it - relaying there would
     * nest inside that layout and shift the box twice.
     */
    @Override
    public void slabbed$relayoutOnSyncedSeat(EntityDataAccessor<?> key) {
        if (SLABBED$HANG_DY.equals(key) && this.level() != null && this.level().isClientSide()
                && this.pos != null && this.direction != null) {
            this.recalculateBoundingBox();
        }
    }

    @Inject(method = "defineSynchedData()V", at = @At("TAIL"))
    private void slabbed$defineHangSeat(CallbackInfo ci) {
        this.slabbed$declareHangSeatKey();
    }

    @Inject(method = "setDirection(Lnet/minecraft/core/Direction;)V", at = @At("HEAD"))
    private void slabbed$mintSeatOnDirection(Direction facing, CallbackInfo ci) {
        this.slabbed$mintHangSeatFor(facing);
    }

    @Inject(method = "recalculateBoundingBox()V", at = @At("TAIL"))
    private void slabbed$hangBoxOnRememberedSeat(CallbackInfo ci) {
        this.slabbed$seatHangBox();
    }

    /**
     * Popping law is untouched: a painting judges the GRID cells behind it, where its wall actually
     * is, not the cells behind its drawn box. The box is unshifted for the check and restored
     * after. (Item frames replace {@code survives} on their own grid cell and never reach this.)
     */
    @Inject(method = "survives()Z", at = @At("HEAD"))
    private void slabbed$judgeSurvivalOnGridCells(CallbackInfoReturnable<Boolean> cir) {
        double dy = this.slabbed$hangSeatDy();
        if (Math.abs(dy) >= SLABBED$EPS) {
            this.slabbed$seatedBoxDuringSurvival = this.getBoundingBox();
            this.setBoundingBox(this.slabbed$seatedBoxDuringSurvival.move(0.0d, -dy, 0.0d));
        }
    }

    @Inject(method = "survives()Z", at = @At("RETURN"))
    private void slabbed$restoreSeatedBoxAfterSurvival(CallbackInfoReturnable<Boolean> cir) {
        if (this.slabbed$seatedBoxDuringSurvival != null) {
            this.setBoundingBox(this.slabbed$seatedBoxDuringSurvival);
            this.slabbed$seatedBoxDuringSurvival = null;
        }
    }

    /** Reached by any hung subclass that does not replace it; frames and paintings both do. */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        this.slabbed$relayoutOnSyncedSeat(key);
    }
}
