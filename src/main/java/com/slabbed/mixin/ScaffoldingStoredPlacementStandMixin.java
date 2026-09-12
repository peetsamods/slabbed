package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import net.minecraft.block.BlockState;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Standing and descending tests use a scaffold's stored top plane. */
@Mixin(ScaffoldingBlock.class)
public abstract class ScaffoldingStoredPlacementStandMixin {
    @WrapOperation(method = "getCollisionShape", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/ShapeContext;isAbove(Lnet/minecraft/util/shape/VoxelShape;Lnet/minecraft/util/math/BlockPos;Z)Z"))
    private boolean slabbed$storedStandingPlane(ShapeContext context, VoxelShape shape, BlockPos pos,
            boolean defaultValue, Operation<Boolean> original,
            BlockState state, BlockView world, BlockPos cell, ShapeContext ignored) {
        double dy = SlabPlacementDyAttachment.storedDy(world, cell);
        return original.call(context, Double.isFinite(dy) ? shape.offset(0.0, dy, 0.0) : shape, pos, defaultValue);
    }
}
