package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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

    @Inject(method = "canSupportCenter", at = @At("HEAD"), cancellable = true)
    private static void slabbed$slabTopSupport(LevelReader world, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = world.getBlockState(pos);
        if (direction == Direction.UP && SlabSupport.isBottomSlab(state)) {
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
