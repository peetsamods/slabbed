package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A lowered scaffolding column must still be something you stand on (LAW.md: the placed height is
 * the block's real height). Vanilla decides "is the entity above this block" against the unlowered
 * top of the cell, so a player standing on a lowered column's visible top was "inside" it and slid
 * down as soon as jump was released. The comparison shape is offset by the cell's stored height so
 * the standing layer, the descending branch and the bottom-piece branch all measure the real top.
 */
@Mixin(ScaffoldingBlock.class)
public abstract class ScaffoldingLoweredStandMixin {
    @WrapOperation(
            method = "getCollisionShape(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/block/ShapeContext;isAbove(Lnet/minecraft/util/shape/VoxelShape;Lnet/minecraft/util/math/BlockPos;Z)Z"))
    private boolean slabbed$isAboveLoweredTop(
            ShapeContext context, VoxelShape shape, BlockPos pos, boolean defaultValue, Operation<Boolean> original,
            BlockState state, BlockView world, BlockPos cell, ShapeContext ignored
    ) {
        double dy = SlabSupport.getYOffset(world, cell, state);
        VoxelShape compared = Double.isFinite(dy) && dy != 0.0d ? shape.offset(0.0d, dy, 0.0d) : shape;
        return original.call(context, compared, pos, defaultValue);
    }
}
