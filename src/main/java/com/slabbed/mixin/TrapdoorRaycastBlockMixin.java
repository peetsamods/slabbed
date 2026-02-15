package com.slabbed.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class TrapdoorRaycastBlockMixin {

    @Inject(method = "getRaycastShape", at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetTrapdoorRaycast(BlockState state, BlockView world, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        if (!(state.getBlock() instanceof TrapdoorBlock)) {
            return;
        }
        BlockState below = world.getBlockState(pos.down());
        if (!(below.getBlock() instanceof SlabBlock)) {
            return;
        }
        SlabType type = below.get(SlabBlock.TYPE);
        if (type != SlabType.BOTTOM && type != SlabType.DOUBLE) {
            return;
        }
        VoxelShape shape = cir.getReturnValue();
        cir.setReturnValue(shape.offset(0.0, -0.5, 0.0));
    }
}
