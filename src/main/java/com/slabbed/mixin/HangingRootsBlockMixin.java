package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.HangingRootsBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;
import net.minecraft.block.ShapeContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allow hanging roots to attach under TOP/DOUBLE slabs without global solidity overrides.
 */
@Mixin(HangingRootsBlock.class)
public abstract class HangingRootsBlockMixin {

    @Shadow @Final private static VoxelShape SHAPE;

    @Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
    private void slabbed$allowTopSlabCeiling(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockPos above = pos.up();
        BlockState ceiling = world.getBlockState(above);
        if (SlabSupport.isCeilingSupportBottomSurface(world, above)
                && ceiling.isSideSolidFullSquare(world, above, Direction.DOWN)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getOutlineShape", at = @At("HEAD"), cancellable = true)
    private void slabbed$forceOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
        cir.setReturnValue(SHAPE);
    }

}
