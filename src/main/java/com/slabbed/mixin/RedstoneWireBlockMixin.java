package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redstone wire: allow slab top faces to count as valid ground for placement/survival.
 */
@Mixin(RedStoneWireBlock.class)
public abstract class RedstoneWireBlockMixin {

    @Inject(method = "canSurvive",
            at = @At("HEAD"), cancellable = true)
    private void slabbed$canPlaceAt(BlockState state, LevelReader world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SlabSupport.isRedstoneSupportTopSurface(world, pos.below())) {
            cir.setReturnValue(true);
        }
    }
}
