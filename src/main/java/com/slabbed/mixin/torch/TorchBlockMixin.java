package com.slabbed.mixin.torch;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BaseTorchBlock.class)
public abstract class TorchBlockMixin {

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void slabbed$allowTorchOnSlabTop(BlockState state, LevelReader world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SlabSupport.isRejectedFloorTorchTopFace(world, pos.below(), state)) {
            cir.setReturnValue(false);
            return;
        }
        if (SlabSupport.canTreatAsFloorTorchTopFace(world, pos.below(), state)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void slabbed$keepTorchOnSlabTop(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor world,
            BlockPos pos,
            BlockPos neighborPos,
            CallbackInfoReturnable<BlockState> cir
    ) {
        if (direction == Direction.DOWN && SlabSupport.isRejectedFloorTorchTopFace(world, pos.below(), state)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
            return;
        }
        if (direction == Direction.DOWN && SlabSupport.canTreatAsFloorTorchTopFace(world, pos.below(), state)) {
            cir.setReturnValue(state);
            return;
        }
        if (direction == Direction.DOWN && !state.canSurvive(world, pos)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
