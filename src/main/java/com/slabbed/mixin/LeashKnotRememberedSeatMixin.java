package com.slabbed.mixin;

import com.slabbed.util.HangingSeatDyHolder;
import com.slabbed.util.SlabSupport;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A leash knot remembers its fence's placed seat (LAW.md), without moving its grid attachment. */
@Mixin(LeashFenceKnotEntity.class)
public abstract class LeashKnotRememberedSeatMixin extends BlockAttachedEntity implements HangingSeatDyHolder {
    @Unique private static final EntityDataAccessor<Long> SLABBED$LEASH_DY =
            SynchedEntityData.defineId(LeashFenceKnotEntity.class, EntityDataSerializers.LONG);
    @Unique private static final long SLABBED$UNSET = Double.doubleToRawLongBits(Double.NaN);
    @Unique private static final String SLABBED$SEAT_KEY = "slabbed:leash_dy";
    @Unique private boolean slabbed$readingData;
    @Unique private boolean slabbed$constructed;

    protected LeashKnotRememberedSeatMixin(EntityType<? extends BlockAttachedEntity> type, Level level) {
        super(type, level);
    }

    /** Entity's superclass constructor recalculates at origin before the attachment is assigned. */
    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", at = @At("TAIL"))
    private void slabbed$positionedConstruction(CallbackInfo ci) {
        this.slabbed$constructed = true;
        this.recalculateBoundingBox();
    }

    /** Spawn/load construction has no attachment yet; the later packet/load/position path lays it out. */
    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", at = @At("TAIL"))
    private void slabbed$unpositionedConstruction(CallbackInfo ci) {
        this.slabbed$constructed = true;
    }

    @Override public double slabbed$hangSeatDy() {
        double dy = Double.longBitsToDouble(this.getEntityData().get(SLABBED$LEASH_DY));
        return Double.isFinite(dy) ? dy : 0.0d;
    }

    @Override public boolean slabbed$hasHangSeat() {
        return Double.isFinite(Double.longBitsToDouble(this.getEntityData().get(SLABBED$LEASH_DY)));
    }

    @Override public void slabbed$restoreHangSeatDy(double dy) {
        this.getEntityData().set(SLABBED$LEASH_DY,
                Double.doubleToRawLongBits(Double.isFinite(dy) ? dy : 0.0d));
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void slabbed$defineSeat(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(SLABBED$LEASH_DY, SLABBED$UNSET);
    }

    /** Never wait for a chunk during entity loading, and never re-derive an existing seat. */
    @Unique private boolean slabbed$tryMintSeat() {
        if (!this.slabbed$constructed || this.slabbed$readingData || this.slabbed$hasHangSeat() || this.pos == null
                || !(this.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return false;
        }
        var chunk = level.getChunkSource().getChunkNow(this.pos.getX() >> 4, this.pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        this.slabbed$restoreHangSeatDy(SlabSupport.getYOffset(level, this.pos, chunk.getBlockState(this.pos)));
        return true;
    }

    @Inject(method = "recalculateBoundingBox", at = @At("TAIL"))
    private void slabbed$lowerBox(CallbackInfo ci) {
        this.slabbed$tryMintSeat();
        this.setBoundingBox(this.getBoundingBox().move(0.0d, this.slabbed$hangSeatDy(), 0.0d));
    }

    /** Deeply lowered boxes must remain discoverable by the unchanged exact grid-pos filter. */
    @ModifyArg(method = "getKnot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            index = 1)
    private static AABB slabbed$includeLoweredKnots(AABB bounds) {
        return bounds.expandTowards(0.0d, com.slabbed.util.ManualDyEnvelope.MIN_DY, 0.0d);
    }

    @Inject(method = "getRopeHoldPosition", at = @At("RETURN"), cancellable = true)
    private void slabbed$lowerRopeEndpoint(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(cir.getReturnValue().add(0.0d, this.slabbed$hangSeatDy(), 0.0d));
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void slabbed$saveSeat(ValueOutput output, CallbackInfo ci) {
        if (this.slabbed$hasHangSeat()) {
            output.putDouble(SLABBED$SEAT_KEY, this.slabbed$hangSeatDy());
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void slabbed$readSeat(ValueInput input, CallbackInfo ci) {
        double dy = input.getDoubleOr(SLABBED$SEAT_KEY, Double.NaN);
        if (Double.isFinite(dy)) {
            this.slabbed$restoreHangSeatDy(dy);
        }
    }

    @Override public void load(ValueInput input) {
        this.slabbed$readingData = true;
        try {
            super.load(input);
        } finally {
            this.slabbed$readingData = false;
        }
        if (this.pos != null) {
            this.recalculateBoundingBox();
        }
    }

    @Override public void tick() {
        if (this.slabbed$tryMintSeat()) {
            this.recalculateBoundingBox();
        }
        super.tick();
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SLABBED$LEASH_DY.equals(key) && this.level().isClientSide() && this.pos != null) {
            this.recalculateBoundingBox();
        }
    }
}
