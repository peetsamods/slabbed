package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows slab top faces to count as valid ground for redstone placement and survival.
 * Vanilla remains the sole owner of horizontal connection shape and directional power.
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
