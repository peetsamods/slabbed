package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** RPB's cached member outlines already carry the owner's seat; LAW.md permits one translation. */
@Pseudo
@Mixin(targets = "com.lukeolafp.relativeblocks.blocks.BlockClusterBlock", remap = false)
public abstract class RelativeBlocksClusterShapeMixin {
    @WrapOperation(method = "combinedShape", at = @At(value = "INVOKE", target =
            "Lcom/lukeolafp/relativeblocks/blocks/BlockClusterBlock;cellShape("
                    + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
                    + "Lcom/lukeolafp/relativeblocks/blocks/BlockClusterBlockEntity;IZ)"
                    + "Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape slabbed$localMemberOutline(
            BlockGetter world, BlockPos pos, @Coerce Object cluster, int cell, boolean collision,
            Operation<VoxelShape> original) {
        VoxelShape shape = original.call(world, pos, cluster, cell, collision);
        if (collision || shape.isEmpty() || !(cluster instanceof RelativeBlocksClusterDataAccessor data)) {
            return shape;
        }
        var state = data.slabbed$clusterCell(cell);
        int layout = data.slabbed$clusterLayout();
        // RPB 2.0.x BarrierLayout.lays: split-axis layouts with arms build unshifted geometry
        // against an empty view. Only memberShape-derived cells contain the duplicate seat.
        if ((layout == 1 || layout == 2) && state != null
                && (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock
                    || state.getBlock() instanceof IronBarsBlock)) {
            return shape;
        }
        // Direct member picking and the custom wireframe keep consuming the original shifted cache.
        double dy = SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
        return dy == 0.0 ? shape : shape.move(0.0, -dy, 0.0);
    }
}
