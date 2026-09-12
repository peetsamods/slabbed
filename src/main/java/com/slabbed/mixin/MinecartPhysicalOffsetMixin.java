package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rail calculations use cell coordinates; the entity and its passengers remain physical (LAW.md). */
@Mixin(AbstractMinecartEntity.class)
public abstract class MinecartPhysicalOffsetMixin extends VehicleEntity {
    @Unique
    private static final TrackedData<Long> SLABBED_RAIL_DY = DataTracker.registerData(
            AbstractMinecartEntity.class, TrackedDataHandlerRegistry.LONG);
    @Unique
    private static final String SLABBED_RAIL_DY_KEY = "slabbed:rail_dy";
    @Unique
    private int slabbed$logicalRailQueries;

    protected MinecartPhysicalOffsetMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void slabbed$initRailDy(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(SLABBED_RAIL_DY, Double.doubleToRawLongBits(0.0d));
    }

    @Inject(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;DDD)V", at = @At("TAIL"))
    private void slabbed$placeAtRailHeight(EntityType<?> type, World world,
                                          double x, double y, double z, CallbackInfo ci) {
        if (world.isClient) return;
        // Structure generation constructs carts off-thread; rail lookup waits for the server.
        // Defer binding until the existing server-tick hook can safely read the finished chunk.
        if (world instanceof ServerWorld serverWorld && !serverWorld.getServer().isOnThread()) return;
        BlockPos rail = slabbed$railAt(x, y, z);
        if (rail != null && SlabAnchorAttachment.usesFrozenPlacementHeight(world, rail)) {
            double dy = SlabSupport.getYOffset(world, rail, world.getBlockState(rail));
            if (Double.isFinite(dy)) {
                dataTracker.set(SLABBED_RAIL_DY, Double.doubleToRawLongBits(dy));
                setPosition(x, y + dy, z);
                prevY = y + dy;
            }
        }
    }

    @Unique
    private double slabbed$railDy() {
        double dy = Double.longBitsToDouble(dataTracker.get(SLABBED_RAIL_DY));
        return Double.isFinite(dy) ? dy : 0.0d;
    }

    @Unique
    private BlockPos slabbed$railAt(double x, double y, double z) {
        BlockPos cell = BlockPos.ofFloored(x, y, z);
        if (AbstractRailBlock.isRail(getWorld().getBlockState(cell.down()))) return cell.down();
        return AbstractRailBlock.isRail(getWorld().getBlockState(cell)) ? cell : null;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void slabbed$bindCurrentRail(CallbackInfo ci) {
        if (getWorld().isClient) return;
        double previousDy = slabbed$railDy();
        BlockPos rail = slabbed$railAt(getX(), getY() - previousDy, getZ());
        if (rail != null) {
            // A legacy rail (no modern provenance) keeps the cart at its vanilla physical height.
            double dy = SlabAnchorAttachment.usesFrozenPlacementHeight(getWorld(), rail)
                    ? SlabSupport.getYOffset(getWorld(), rail, getWorld().getBlockState(rail)) : 0.0d;
            if (Double.isFinite(dy) && dy != previousDy) {
                dataTracker.set(SLABBED_RAIL_DY, Double.doubleToRawLongBits(dy));
                setPosition(getX(), getY() + dy - previousDy, getZ());
            }
            return;
        }
        // Re-entry finds the rail's visible band without moving the entity into its logical cell.
        int radius = (int) Math.ceil(-SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY);
        BlockPos physicalCell = getBlockPos();
        for (int above = -1; above <= radius + 1; above++) {
            BlockPos candidate = physicalCell.up(above);
            BlockState state = getWorld().getBlockState(candidate);
            if (!AbstractRailBlock.isRail(state)) continue;
            double dy = SlabAnchorAttachment.usesFrozenPlacementHeight(getWorld(), candidate)
                    ? SlabSupport.getYOffset(getWorld(), candidate, state) : 0.0d;
            if (Double.isFinite(dy) && candidate.equals(slabbed$railAt(getX(), getY() - dy, getZ()))) {
                dataTracker.set(SLABBED_RAIL_DY, Double.doubleToRawLongBits(dy));
                return;
            }
        }
        dataTracker.set(SLABBED_RAIL_DY, Double.doubleToRawLongBits(0.0d));
    }

    @Redirect(method = {"tick", "moveOnRail"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;getY()D"))
    private double slabbed$railCoordinateY(AbstractMinecartEntity entity) {
        return entity.getY() - slabbed$railDy();
    }

    @Redirect(method = "moveOnRail", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;setPosition(DDD)V"))
    private void slabbed$physicalRailPosition(AbstractMinecartEntity entity, double x, double y, double z) {
        entity.setPosition(x, y + slabbed$railDy(), z);
    }

    @WrapMethod(method = "moveOnRail")
    private void slabbed$railMovement(BlockPos pos, BlockState state, Operation<Void> original) {
        slabbed$logicalRailQueries++;
        try {
            original.call(pos, state);
        } finally {
            slabbed$logicalRailQueries--;
        }
    }

    @WrapMethod(method = "snapPositionToRail")
    private Vec3d slabbed$physicalRailSnap(double x, double y, double z, Operation<Vec3d> original) {
        if (slabbed$logicalRailQueries > 0) return original.call(x, y, z);
        double dy = slabbed$railDy();
        slabbed$logicalRailQueries++;
        try {
            Vec3d snapped = original.call(x, y - dy, z);
            return snapped == null ? null : snapped.add(0.0d, dy, 0.0d);
        } finally {
            slabbed$logicalRailQueries--;
        }
    }

    @WrapMethod(method = "snapPositionToRailWithOffset")
    private Vec3d slabbed$physicalRailOffsetSnap(double x, double y, double z, double offset,
                                               Operation<Vec3d> original) {
        if (slabbed$logicalRailQueries > 0) return original.call(x, y, z, offset);
        double dy = slabbed$railDy();
        slabbed$logicalRailQueries++;
        try {
            Vec3d snapped = original.call(x, y - dy, z, offset);
            return snapped == null ? null : snapped.add(0.0d, dy, 0.0d);
        } finally {
            slabbed$logicalRailQueries--;
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void slabbed$writeRailDy(NbtCompound nbt, CallbackInfo ci) {
        double dy = Double.longBitsToDouble(dataTracker.get(SLABBED_RAIL_DY));
        if (Double.isFinite(dy) && dy != 0.0d) nbt.putDouble(SLABBED_RAIL_DY_KEY, dy);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("HEAD"))
    private void slabbed$readRailDy(NbtCompound nbt, CallbackInfo ci) {
        double dy = nbt.getDouble(SLABBED_RAIL_DY_KEY);
        dataTracker.set(SLABBED_RAIL_DY, Double.doubleToRawLongBits(Double.isFinite(dy) ? dy : 0.0d));
    }
}
