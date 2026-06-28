package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes bottom slabs report their top face as covering a small square,
 * which is the check used by torches, flowers, and many other blocks
 * in {@code canPlaceAt} via {@link Block#sideCoversSmallSquare}.
 */
@Mixin(Block.class)
public abstract class SlabSupportBlockMixin {

    @Inject(method = "sideCoversSmallSquare", at = @At("HEAD"), cancellable = true)
    private static void slabbed$slabTopSupport(WorldView world, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        net.minecraft.block.BlockState state = world.getBlockState(pos);
        if (direction == Direction.UP
                && (SlabSupport.isBottomSlab(state) || SlabSupport.isDirectCustomBottomLikeSurface(state))) {
            cir.setReturnValue(true);
        }
        // Lowered-lane bottom slabs visually expose an underside support face
        // at the lowered elevation; allow underside small-square support there.
        if (direction == Direction.DOWN
                && (SlabSupport.isTopSlab(state)
                || (SlabSupport.isBottomSlab(state) && SlabSupport.getYOffset(world, pos, state) < 0.0d))) {
            cir.setReturnValue(true);
        }
    }
}
