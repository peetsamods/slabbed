package com.slabbed.mixin;

import com.slabbed.util.HangingSeatDyHolder;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A hung decoration REMEMBERS the height of the face it was hung on (LAW.md Law 1; maintainer
 * ruling, 2026-09-13: where it is placed is where it stays, for everything hung on a wall).
 *
 * <p>The seat is MINTED ONCE — when the decoration is first told which way it faces on the server,
 * with its support loaded, which is the moment the item hangs it — from the support's stored
 * height, and is then carried in synced entity data and in save data. Every later layout reads the
 * remembered number verbatim; nothing here re-reads the support. Rebuilding the wall behind a hung
 * frame at a different height is a change to the WALL, not to the frame.
 *
 * <p>The entity's REAL position deliberately stays at grid height: moving it corrupts the derived
 * grid cell and {@code survives()} judges the wrong support. Only the bounding box shifts here, and
 * the render layer applies the same remembered seat to the drawing. Vanilla's {@code
 * recalculateBoundingBox} sets the position from the unshifted box first, so the shift is applied
 * at its TAIL and fires exactly once per layout; on this version that method is final on
 * {@code HangingEntity}, so frames and paintings share it.
 *
 * <p>A decoration saved before this seat existed carries no number; its first server layout mints
 * one from the wall it hangs on today. That is a one-time migration, not a re-derivation.
 *
 * <p>Two traps this shape avoids. (1) Minting DURING a layout locks onto the default facing: a
 * painting's variant is restored before its facing is, and that restore lays the box out with the
 * default SOUTH direction, so a mint there would read the wrong wall. The mint therefore hangs off
 * the facing setter, never off the layout. (2) Re-laying the box out when the synced value changes
 * is CLIENT-ONLY: the server's own layout after the mint already applies it.
 *
 * <p>On this version {@code HangingEntity} declares no synched-data, facing, save or data-update
 * hook of its own, and both hung classes override those without calling super. The per-class
 * mixins forward into the bridge methods below; this class stays the only implementation.
 */
@Mixin(HangingEntity.class)
public abstract class HangingEntityRememberedSeatMixin extends BlockAttachedEntity implements HangingSeatDyHolder {

    /** Raw bits of the seat; NaN bits mean "not minted yet". Synced so the client box and drawing agree. */
    @Unique
    private static final EntityDataAccessor<Long> SLABBED$HANG_DY =
            SynchedEntityData.defineId(HangingEntity.class, EntityDataSerializers.LONG);

    @Unique
    private static final long SLABBED$UNSET = Double.doubleToRawLongBits(Double.NaN);

    /**
     * Whether this instance's synched data actually carries the seat. Deliberately declared WITHOUT
     * an initializer: the declaring hook runs from the entity constructor, before an initializer
     * would reset it. A hung class that never forwards the declaration (a third-party subclass of
     * {@code HangingEntity} that writes its own synched-data hook) keeps vanilla behaviour instead
     * of reading an accessor that was never allocated.
     */
    @Unique
    private boolean slabbed$hangSeatDeclared;

    @Unique
    private AABB slabbed$shiftedBoxDuringSurvival;

    protected HangingEntityRememberedSeatMixin(EntityType<? extends BlockAttachedEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public double slabbed$hangSeatDy() {
        if (!this.slabbed$hangSeatDeclared) {
            return 0.0d;
        }
        double dy = Double.longBitsToDouble(this.getEntityData().get(SLABBED$HANG_DY));
        return Double.isFinite(dy) ? dy : 0.0d;
    }

    @Override
    public boolean slabbed$hasHangSeat() {
        return this.slabbed$hangSeatDeclared
                && Double.isFinite(Double.longBitsToDouble(this.getEntityData().get(SLABBED$HANG_DY)));
    }

    @Override
    public void slabbed$restoreHangSeatDy(double dy) {
        if (!this.slabbed$hangSeatDeclared) {
            return;
        }
        this.getEntityData().set(SLABBED$HANG_DY, Double.doubleToRawLongBits(Double.isFinite(dy) ? dy : 0.0d));
    }

    @Override
    public void slabbed$defineHangSeat(SynchedEntityData.Builder builder) {
        builder.define(SLABBED$HANG_DY, SLABBED$UNSET);
        this.slabbed$hangSeatDeclared = true;
    }

    /**
     * The ONE derivation, at the moment the decoration learns which way it faces: both the item's
     * hang path and the load path set the facing with the position already known. Server thread
     * only (a hang from any other thread would answer through the server and could wait on it);
     * support loaded (an unloaded support answers nothing, not "flush"). A saved seat restored by
     * the per-class read hook wins over this mint.
     *
     * <p>On this version the facing setter lays the box out BEFORE its tail, so a freshly minted
     * seat needs one layout of its own. That layout recomputes the box from the cell and the facing
     * and then applies the seat once — it does not stack onto the box already there.
     */
    @Override
    public void slabbed$mintHangSeatFromWall() {
        if (this.slabbed$hasHangSeat() || this.pos == null || this.getDirection() == null) {
            return;
        }
        if (!(this.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return;
        }
        BlockPos supportPos = this.pos.relative(this.getDirection().getOpposite());
        if (!level.hasChunkAt(supportPos)) {
            return;
        }
        BlockState support = level.getBlockState(supportPos);
        double dy = SlabSupport.getYOffset(level, supportPos, support);
        this.slabbed$restoreHangSeatDy(Double.isFinite(dy) ? dy : 0.0d);
        this.recalculateBoundingBox();
    }

    /**
     * The CLIENT lays its box out from the spawn packet before the seat arrives; re-lay it when it
     * does. Server side the layout that follows the mint already applies it.
     */
    @Override
    public void slabbed$onHangSeatDataUpdated(EntityDataAccessor<?> key) {
        if (SLABBED$HANG_DY.equals(key) && this.level() != null && this.level().isClientSide()
                && this.pos != null && this.getDirection() != null) {
            this.recalculateBoundingBox();
        }
    }

    /** A painting reaches the shared facing setter; an item frame overrides it and forwards itself. */
    @Inject(method = "setDirection(Lnet/minecraft/core/Direction;)V", at = @At("TAIL"))
    private void slabbed$mintSeatOnDirection(CallbackInfo ci) {
        this.slabbed$mintHangSeatFromWall();
    }

    /** Apply the remembered seat to the freshly laid-out box; the position set just before stays on the grid. */
    @Inject(method = "recalculateBoundingBox()V", at = @At("TAIL"))
    private void slabbed$hangBoxOnRememberedSeat(CallbackInfo ci) {
        double dy = this.slabbed$hangSeatDy();
        if (Math.abs(dy) >= 1.0e-6d) {
            this.setBoundingBox(this.getBoundingBox().move(0.0d, dy, 0.0d));
        }
    }

    /**
     * Popping law is untouched: a painting judges the GRID cells behind it, where its wall actually
     * is, not the cells behind its drawn box. The box is unshifted for the check and restored after.
     * (Item frames override {@code survives} on their own grid cell and never reach this.)
     */
    @Inject(method = "survives()Z", at = @At("HEAD"))
    private void slabbed$judgeSurvivalOnGridCells(CallbackInfoReturnable<Boolean> cir) {
        double dy = this.slabbed$hangSeatDy();
        if (Math.abs(dy) >= 1.0e-6d) {
            slabbed$shiftedBoxDuringSurvival = this.getBoundingBox();
            this.setBoundingBox(slabbed$shiftedBoxDuringSurvival.move(0.0d, -dy, 0.0d));
        }
    }

    @Inject(method = "survives()Z", at = @At("RETURN"))
    private void slabbed$restoreShiftedBoxAfterSurvival(CallbackInfoReturnable<Boolean> cir) {
        if (slabbed$shiftedBoxDuringSurvival != null) {
            this.setBoundingBox(slabbed$shiftedBoxDuringSurvival);
            slabbed$shiftedBoxDuringSurvival = null;
        }
    }
}
