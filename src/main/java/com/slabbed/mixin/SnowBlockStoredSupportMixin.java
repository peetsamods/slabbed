package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.SnowBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Snow's local support-face test must not mistake stored translation for a missing face (LAW.md). */
@Mixin(SnowBlock.class)
public abstract class SnowBlockStoredSupportMixin {
    @WrapOperation(method = "canPlaceAt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/BlockState;getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;"))
    private VoxelShape slabbed$localSupportShape(BlockState state, BlockView world, BlockPos pos,
                                                Operation<VoxelShape> original) {
        VoxelShape shape = original.call(state, world, pos);
        double dy = SlabAnchorAttachment.usesFrozenPlacementHeight(world, pos)
                ? SlabSupport.getYOffset(world, pos, state) : 0.0d;
        return dy == 0.0d ? shape : shape.offset(0.0d, -dy, 0.0d);
    }
}
