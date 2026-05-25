package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes bottom slabs report their top face as covering a small square,
 * which is the check used by torches, flowers, and many other blocks
     * in {@code canPlaceAt} via {@link Block#canSupportCenter}.
 */
@Mixin(Block.class)
public abstract class SlabSupportBlockMixin {

    @Inject(method = "canSupportCenter", at = @At("HEAD"), cancellable = true)
    private static void slabbed$slabTopSupport(LevelReader world, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (direction == Direction.UP && SlabSupport.isBottomSlab(world.getBlockState(pos))) {
            cir.setReturnValue(true);
        }
        if (direction == Direction.DOWN && SlabSupport.isTopSlab(world.getBlockState(pos))) {
            cir.setReturnValue(true);
        }
    }
}
