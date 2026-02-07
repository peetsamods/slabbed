package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AbstractTorchBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractTorchBlock.class)
public abstract class TorchBlockMixin extends Block {

    protected TorchBlockMixin(AbstractBlock.Settings settings, ParticleEffect particle) {
        super(settings);
    }

    @Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
    private void slabbed$allowOnSlabs(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockPos below = pos.down();
        if (SlabSupport.canTreatAsSolidTopFace(world, below)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getStateForNeighborUpdate", at = @At("HEAD"), cancellable = true)
    private void slabbed$stayOnSlabs(BlockState state, WorldView world, ScheduledTickView scheduledTickView, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, Random random, CallbackInfoReturnable<BlockState> cir) {
        if (SlabSupport.canTreatAsSolidTopFace(world, pos.down())) {
            cir.setReturnValue(state);
            return;
        }
        if (!state.canPlaceAt(world, pos)) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
        }
    }

    @Inject(method = "getOutlineShape", at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!SlabSupport.hasBottomSlabBelow(world, pos)) {
            return;
        }
        cir.setReturnValue(cir.getReturnValue().offset(0.0, -0.5, 0.0));
    }

}
