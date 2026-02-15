package com.slabbed.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
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

@Mixin(TrapdoorBlock.class)
public abstract class TrapdoorBlockMixin {

    private static boolean slabbed$isOnBottomSlabTop(BlockView world, BlockPos pos) {
        BlockState below = world.getBlockState(pos.down());
        if (!(below.getBlock() instanceof SlabBlock)) return false;
        SlabType type = below.get(SlabBlock.TYPE);
        return type == SlabType.BOTTOM || type == SlabType.DOUBLE;
    }

    @Inject(method = "getOutlineShape", at = @At("RETURN"), cancellable = true)
    private void slabbed$outlineDown(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
        if (!slabbed$isOnBottomSlabTop(world, pos)) {
            return;
        }
        VoxelShape shape = cir.getReturnValue();
        System.out.println("[SLABBED][TRAPDOOR_MIXIN] outline hit pos=" + pos);
        cir.setReturnValue(shape.offset(0.0, -0.5, 0.0));
    }
}
