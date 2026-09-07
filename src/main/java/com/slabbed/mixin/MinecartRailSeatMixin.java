package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.slabbed.util.MinecartRailFrame;
import com.slabbed.util.RailSeatDyHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A minecart really sits on the rail it is drawn on (maintainer ruling, 2026-09-06).
 *
 * <p>Rails carry no collision, so nothing ever pulled a cart down onto a rail laid on a lowered
 * block: the cart's position came straight from the rail's GRID cell and it ran half a block above
 * the rail it appeared to ride. Its hitbox, everything that could hit it, and its riders were all in
 * the wrong place.
 *
 * <p>THE FRAME SPLIT. A seated cart's entity position is PHYSICAL (rail cell + seat); every vanilla
 * rail computation stays in the LOGICAL (grid) frame. This class owns the seat itself — where it
 * comes from, when it is re-read, how it is saved — and the ONE conversion the rest of the cart
 * needs: the rail-cell derivation. The two behaviour solvers and the renderer convert at their own
 * boundaries.
 *
 * <p>INVARIANT: the seat is a property of the ENTITY, re-read each tick from the rail cell's STORED
 * placement fact. Nothing here re-derives the rail block's own height, so LAW.md is untouched — a
 * neighbour edit still cannot move a placed block.
 *
 * <p>INVARIANT: the seat is SYNCED, not a server field. {@code NewMinecartBehavior.tick} calls
 * {@code getCurrentBlockPosOrRailBelow()} on the CLIENT, so the client must see the same seat or it
 * resolves the support cell instead of the rail.
 *
 * <p>INVARIANT: the seat is re-read at tick HEAD, before the behaviour runs, so the frame is
 * constant for the whole of a tick's rail work. A mid-tick seat change would convert one side of a
 * delta and drift the cart.
 */
@Mixin(AbstractMinecart.class)
public abstract class MinecartRailSeatMixin extends Entity implements RailSeatDyHolder {

    @Shadow
    public abstract BlockPos getCurrentBlockPosOrRailBelow();

    /**
     * Raw double bits in a synced long: exact, cheap, and one network field. An external inspector
     * reading the entity data sees an opaque long; nothing shipped reads it.
     */
    @Unique
    private static final EntityDataAccessor<Long> SLABBED$RAIL_DY =
            SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.LONG);

    @Unique
    private static final String SLABBED$RAIL_DY_KEY = "slabbed:rail_dy";

    protected MinecartRailSeatMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public double slabbed$railSeatDy() {
        double dy = Double.longBitsToDouble(this.getEntityData().get(SLABBED$RAIL_DY));
        return Double.isFinite(dy) ? dy : 0.0d;
    }

    @Unique
    private void slabbed$setRailSeatDy(double dy) {
        this.getEntityData().set(SLABBED$RAIL_DY,
                Double.doubleToRawLongBits(Double.isFinite(dy) ? dy : 0.0d));
    }

    /** The seat a cart standing in {@code cell} would take, or NaN when {@code cell} holds no rail. */
    @Unique
    private double slabbed$seatAt(Level level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        return BaseRailBlock.isRail(state) ? MinecartRailFrame.seatOf(level, cell, state) : Double.NaN;
    }

    @Inject(method = "defineSynchedData(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V",
            at = @At("TAIL"))
    private void slabbed$defineRailSeatDy(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(SLABBED$RAIL_DY, Double.doubleToRawLongBits(0.0d));
    }

    /**
     * The ONE position entry point a new cart has. The (EntityType, Level, DDD) constructor reaches
     * it after {@code super(type, level)} has built {@code entityData} and assigned the final
     * behaviour, and {@code createMinecart} reaches it on a fully constructed entity BEFORE its own
     * rail-cell derivation runs. The seat is still 0 here, so the cell below resolves exactly as
     * vanilla's would.
     */
    @Inject(method = "setInitialPos(DDD)V", at = @At("TAIL"))
    private void slabbed$seatOnSpawn(double x, double y, double z, CallbackInfo ci) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos cell = this.getCurrentBlockPosOrRailBelow();
        // Never force a chunk load from a spawn: an unloaded cell simply seats nothing.
        if (!level.hasChunkAt(cell)) {
            return;
        }
        double dy = slabbed$seatAt(level, cell);
        if (!Double.isFinite(dy) || dy == 0.0d) {
            return;
        }
        slabbed$setRailSeatDy(dy);
        // snapTo sets the old position too, so a freshly placed cart never lerps down from grid
        // height on its first rendered frame.
        this.snapTo(x, y + dy, z, this.getYRot(), this.getXRot());
    }

    /**
     * Re-bind the seat before the behaviour runs.
     *
     * <p>ON A RAIL the LOGICAL height is held fixed across a seat change — the cart is moved by
     * (new - old) — so the rail geometry sees the same grid Y on both sides of the seam. That is the
     * direction that matters for crossing between a lowered run and a flush one: without the move,
     * the number would track the rail while the cart's real height did not.
     *
     * <p>OFF A RAIL the seat is cleared and the cart is deliberately NOT moved. Off-rail physics is
     * frame-free, and hoisting the cart back to grid height on the tick it leaves the track would be
     * a visible pop upward before it falls.
     *
     * <p>Do not re-add an upward re-entry scan here: it costs every off-rail cart a per-tick block
     * sweep. A cart that comes off the track and lands on a lowered rail's support re-seats when it
     * next reaches a rail whose own cell contains it.
     */
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void slabbed$rebindRailSeat(CallbackInfo ci) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos cell = this.getCurrentBlockPosOrRailBelow();
        // An unloaded cell answers nothing, so it must not be read as "no rail here" — that would
        // unbind a cart over a chunk seam.
        if (!level.hasChunkAt(cell)) {
            return;
        }
        double previous = slabbed$railSeatDy();
        double seat = slabbed$seatAt(level, cell);
        if (!Double.isFinite(seat)) {
            if (previous != 0.0d) {
                slabbed$setRailSeatDy(0.0d);
            }
            return;
        }
        if (seat == previous) {
            return;
        }
        slabbed$setRailSeatDy(seat);
        this.setPos(this.getX(), this.getY() + (seat - previous), this.getZ());
    }

    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V",
            at = @At("TAIL"))
    private void slabbed$saveRailSeatDy(ValueOutput output, CallbackInfo ci) {
        double dy = slabbed$railSeatDy();
        if (dy != 0.0d) {
            output.putDouble(SLABBED$RAIL_DY_KEY, dy);
        }
    }

    /**
     * Restore the NUMBER only. {@code Entity.load} already restores "Pos" through {@code setPosRaw}
     * and the saved position is physical, so the frame is consistent without moving anything here.
     * Without this a reloaded cart's logical frame would be wrong and it would resolve its SUPPORT
     * cell instead of its rail.
     */
    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V",
            at = @At("TAIL"))
    private void slabbed$loadRailSeatDy(ValueInput input, CallbackInfo ci) {
        slabbed$setRailSeatDy(input.getDoubleOr(SLABBED$RAIL_DY_KEY, 0.0d));
    }

    /**
     * Both Y reads in the rail-cell derivation ask "which cell am I logically in". Fixing them here
     * fixes the cell for BOTH behaviours, for {@code createMinecart}, and for the experimental
     * behaviour's client branch.
     */
    @ModifyExpressionValue(
            method = "getCurrentBlockPosOrRailBelow()Lnet/minecraft/core/BlockPos;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;getY()D"))
    private double slabbed$logicalYForRailCell(double physicalY) {
        return physicalY - slabbed$railSeatDy();
    }
}
