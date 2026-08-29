package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Snow layers seat on a slab's real top surface exactly like carpet: vanilla's sturdiness
 * check sees no full face at the cell boundary and refuses the placement outright, so the
 * lowered-support survival exception carpet already carries applies here verbatim.
 */
@Mixin(SnowLayerBlock.class)
public abstract class SnowLayerBlockMixin extends Block {

    protected SnowLayerBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void slabbed$allowOnSlabs(BlockState state, LevelReader world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockPos below = pos.below();
        // A waterlogged slab is a fluid surface, not a seat: the seat resolver refuses fluid
        // supports, so granting survival here would float snow at grid height over water.
        if (SlabSupport.canTreatAsSolidTopFace(world, below)
                && world.getBlockState(below).getFluidState().isEmpty()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void slabbed$stayOnSlabs(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor world,
            BlockPos pos,
            BlockPos neighborPos,
            CallbackInfoReturnable<BlockState> cir
    ) {
        if (SlabSupport.canTreatAsSolidTopFace(world, pos.below())
                && world.getBlockState(pos.below()).getFluidState().isEmpty()) {
            cir.setReturnValue(state);
            return;
        }
        if (!state.canSurvive(world, pos)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

}
