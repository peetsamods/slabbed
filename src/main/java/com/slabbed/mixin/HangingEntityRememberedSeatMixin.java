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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.phys.AABB;

/**
 * A hung decoration REMEMBERS the height of the face it was hung on (LAW 1; maintainer ruling,
 * 2026-09-13: where it is placed is where it stays, for everything hung on a wall).
 *
 * <p>The seat is MINTED ONCE — when the decoration is first told which way it faces on the server,
 * with its support loaded, which is the moment the item hangs it — from the support's stored height, and is
 * then carried in synced entity data and in save data. Every later layout reads the remembered
 * number verbatim; nothing here re-reads the support. Rebuilding the wall behind a hung frame at a
 * different height is a change to the WALL, not to the frame (LAW 1 §3).
 *
 * <p>The entity's REAL position deliberately stays at grid height: moving it corrupts the derived
 * grid cell and {@code survives()} judges the wrong support. Only the bounding box shifts here, and
 * the render layer applies the same remembered seat to the drawing. Vanilla's {@code
 * recalculateBoundingBox} sets the position from the unshifted box first, so the shift is applied at
 * its TAIL and fires exactly once per layout for frames (whose override calls into this one) and
 * paintings alike.
 *
 * <p>A decoration saved before this seat existed carries no number; after its data is restored,
 * available chunks allow a mint from its wall, otherwise a later tick retries. That is a one-time
 * migration, not a re-derivation.
 *
 * <p>Nothing here reads the world while a decoration's data is being restored: that may run inside
 * the promotion of the very chunk it would ask for. Every chunk the mint reads is taken only if
 * already complete, never requested and waited on. With the frozen store off, the support's height
 * walks neighbouring cells through the world; those cantilever walks are bounded ({@code
 * MAX_CHAIN_DEPTH}) and can cause a bounded stall on the tick path, never a self-deadlock.
 */
@Mixin(HangingEntity.class)
public abstract class HangingEntityRememberedSeatMixin extends BlockAttachedEntity implements HangingSeatDyHolder {

    /** Raw bits of the seat; NaN bits mean "not minted yet". Synced so the client box and drawing agree. */
    @Unique
    private static final EntityDataAccessor<Long> SLABBED$HANG_DY =
            SynchedEntityData.defineId(HangingEntity.class, EntityDataSerializers.LONG);

    @Unique
    private static final long SLABBED$UNSET = Double.doubleToRawLongBits(Double.NaN);

    /** True while saved data is being restored; no world or chunk access happens while it is set. */
    @Unique
    private boolean slabbed$readingData;

    protected HangingEntityRememberedSeatMixin(EntityType<? extends BlockAttachedEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public double slabbed$hangSeatDy() {
        double dy = Double.longBitsToDouble(this.getEntityData().get(SLABBED$HANG_DY));
        return Double.isFinite(dy) ? dy : 0.0d;
    }

    @Override
    public boolean slabbed$hasHangSeat() {
        return Double.isFinite(Double.longBitsToDouble(this.getEntityData().get(SLABBED$HANG_DY)));
    }

    @Override
    public void slabbed$restoreHangSeatDy(double dy) {
        this.getEntityData().set(SLABBED$HANG_DY, Double.doubleToRawLongBits(Double.isFinite(dy) ? dy : 0.0d));
    }

    @Inject(method = "defineSynchedData(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V", at = @At("TAIL"))
    private void slabbed$defineHangSeat(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(SLABBED$HANG_DY, SLABBED$UNSET);
    }

    /**
     * The ONE derivation, at the moment the decoration learns which way it faces: both the item's
     * hang path and the load path set the raw direction with the position already known, and the
     * box is laid out right after. Server thread only, with both decoration and support chunks available:
     * entity loading must never wait on the chunk whose loading it is completing. An unfinished
     * support defers the mint to a later tick, not to a guessed "flush" seat. A saved seat restored
     * by the per-class read hook wins over this mint.
     */
    @Inject(method = "setDirectionRaw(Lnet/minecraft/core/Direction;)V", at = @At("TAIL"))
    private void slabbed$mintSeatOnDirection(CallbackInfo ci) {
        this.slabbed$tryMintHangSeat();
    }

    /** NBT loading may run inside chunk promotion; only restore saved seats until it finishes. */
    @Override
    public void load(ValueInput input) {
        this.slabbed$readingData = true;
        try {
            super.load(input);
        } finally {
            this.slabbed$readingData = false;
        }
        // The restored variant can put a painting's centre in a different chunk from its attachment.
        BlockPos entityPos = this.blockPosition();
        if (this.level() instanceof ServerLevel level && level.getServer().isSameThread()
                && level.getChunkSource().getChunkNow(entityPos.getX() >> 4, entityPos.getZ() >> 4) != null
                && this.slabbed$tryMintHangSeat()) {
            this.recalculateBoundingBox();
        }
    }

    /** A seat deferred at load is minted on the first tick whose chunks are complete, and laid out once. */
    @Override
    public void tick() {
        if (this.slabbed$tryMintHangSeat()) {
            this.recalculateBoundingBox();
        }
        super.tick();
    }

    /**
     * Mints the seat from the support's height when none is remembered yet. Reads only chunks that
     * are already complete ({@code getChunkNow}); never a blocking chunk request, never a readiness
     * guess. Returns whether a seat was minted, so the caller lays the box out exactly once.
     */
    @Unique
    private boolean slabbed$tryMintHangSeat() {
        if (this.slabbed$readingData || this.slabbed$hasHangSeat() || this.pos == null || this.getDirection() == null) {
            return false;
        }
        if (!(this.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return false;
        }
        BlockPos attachedPos = this.pos;
        if (level.getChunkSource().getChunkNow(attachedPos.getX() >> 4, attachedPos.getZ() >> 4) == null) {
            return false;
        }
        BlockPos supportPos = attachedPos.relative(this.getDirection().getOpposite());
        LevelChunk supportChunk = level.getChunkSource().getChunkNow(supportPos.getX() >> 4, supportPos.getZ() >> 4);
        if (supportChunk == null) {
            return false;
        }
        BlockState support = supportChunk.getBlockState(supportPos);
        double dy = SlabSupport.getYOffset(level, supportPos, support);
        this.slabbed$restoreHangSeatDy(Double.isFinite(dy) ? dy : 0.0d);
        return true;
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
    @Unique
    private AABB slabbed$shiftedBoxDuringSurvival;

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

    /**
     * The CLIENT lays its box out from the spawn packet before the seat arrives; re-lay it when it
     * does. Server side the layout that follows the mint already applies it — relaying there would
     * nest inside that layout and shift the box twice.
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SLABBED$HANG_DY.equals(key) && this.level() != null && this.level().isClientSide()
                && this.pos != null && this.getDirection() != null) {
            this.recalculateBoundingBox();
        }
    }
}
