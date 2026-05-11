package com.slabbed.mixin.torch;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.AbstractTorchBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractTorchBlock.class)
public abstract class TorchBlockMixin {

    @Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
    private void slabbed$allowTorchOnSlabTop(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SlabSupport.isRejectedFloorTorchTopFace(world, pos.down(), state)) {
            cir.setReturnValue(false);
            return;
        }
        if (SlabSupport.canTreatAsFloorTorchTopFace(world, pos.down(), state)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getStateForNeighborUpdate", at = @At("HEAD"), cancellable = true)
    private void slabbed$keepTorchOnSlabTop(BlockState state, WorldView world, ScheduledTickView scheduledTickView, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, Random random, CallbackInfoReturnable<BlockState> cir) {
        if (direction == Direction.DOWN && SlabSupport.isRejectedFloorTorchTopFace(world, pos.down(), state)) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
            return;
        }
        if (direction == Direction.DOWN && SlabSupport.canTreatAsFloorTorchTopFace(world, pos.down(), state)) {
            cir.setReturnValue(state);
            return;
        }
        if (direction == Direction.DOWN && !state.canPlaceAt(world, pos)) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
        }
    }
}
