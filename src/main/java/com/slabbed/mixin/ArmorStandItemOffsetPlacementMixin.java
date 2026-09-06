package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ArmorStandItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Consumer;

/** Armor stands use the stored visible support plane for collision and final placement. */
@Mixin(ArmorStandItem.class)
public abstract class ArmorStandItemOffsetPlacementMixin {
    @Unique
    private static final ThreadLocal<Double> SLABBED_PLACEMENT_Y =
            ThreadLocal.withInitial(() -> Double.NaN);

    @WrapMethod(method = "useOnBlock")
    private ActionResult slabbed$withVisiblePlacementY(
            ItemUsageContext context, Operation<ActionResult> original) {
        double previous = SLABBED_PLACEMENT_Y.get();
        SLABBED_PLACEMENT_Y.set(slabbed$visiblePlacementY(context));
        try {
            return original.call(context);
        } finally {
            if (Double.isFinite(previous)) {
                SLABBED_PLACEMENT_Y.set(previous);
            } else {
                SLABBED_PLACEMENT_Y.remove();
            }
        }
    }

    @ModifyArg(method = "useOnBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/EntityDimensions;getBoxAt(DDD)Lnet/minecraft/util/math/Box;"), index = 1)
    private double slabbed$checkVisiblePlacementBox(double vanillaY) {
        double visibleY = SLABBED_PLACEMENT_Y.get();
        return Double.isFinite(visibleY) ? visibleY : vanillaY;
    }

    @WrapOperation(method = "useOnBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/EntityType;create(Lnet/minecraft/server/world/ServerWorld;Ljava/util/function/Consumer;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/SpawnReason;ZZ)Lnet/minecraft/entity/Entity;"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Entity slabbed$createAtVisiblePlacementY(
            EntityType type,
            ServerWorld world,
            Consumer copier,
            BlockPos pos,
            SpawnReason reason,
            boolean alignPosition,
            boolean invertY,
            Operation<Entity> original) {
        double visibleY = SLABBED_PLACEMENT_Y.get();
        Consumer adjustedCopier = copier;
        if (Double.isFinite(visibleY)) {
            adjustedCopier = entity -> {
                Entity stand = (Entity) entity;
                stand.refreshPositionAndAngles(
                        stand.getX(), visibleY, stand.getZ(), stand.getYaw(), stand.getPitch());
                copier.accept(entity);
            };
        }
        return original.call(
                type, world, adjustedCopier, pos, reason,
                Double.isFinite(visibleY) ? false : alignPosition,
                Double.isFinite(visibleY) ? false : invertY);
    }

    @Unique
    private static double slabbed$visiblePlacementY(ItemUsageContext context) {
        if (context.getSide() != Direction.UP) {
            return Double.NaN;
        }
        World world = context.getWorld();
        BlockPos support = context.getBlockPos();
        if (!SlabAnchorAttachment.usesFrozenPlacementHeight(world, support)) {
            return Double.NaN;
        }
        double dy = SlabAnchorAttachment.storedPlacementDy(world, support);
        if (!Double.isFinite(dy) || dy == 0.0d) {
            return Double.NaN;
        }
        var collision = world.getBlockState(support).getCollisionShape(world, support);
        if (collision.isEmpty()) {
            return Double.NaN;
        }
        double visibleTop = collision.getMax(Direction.Axis.Y);
        return Double.isFinite(visibleTop) ? support.getY() + visibleTop : Double.NaN;
    }
}
