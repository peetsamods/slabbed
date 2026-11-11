package com.slabbed.test.mixin;

import com.slabbed.test.TrampledSupportConversionTest;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Models compatibility that permits crops to survive on dirt, within one test action only. */
@Mixin(CropBlock.class)
public abstract class RelaxedCropSurvivalGameTestMixin {
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void allowDirtDuringFixture(BlockState state, LevelReader world, BlockPos pos,
                                       CallbackInfoReturnable<Boolean> result) {
        if (TrampledSupportConversionTest.relaxedCropSurvivalActive()
                && state.is(Blocks.WHEAT) && world.getBlockState(pos.below()).is(Blocks.DIRT)) {
            result.setReturnValue(true);
        }
    }
}
