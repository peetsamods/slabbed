package com.slabbed.mixin.client;

import com.slabbed.client.ClientDy;
import net.minecraft.block.BlockState;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.PaleMossCarpetBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({CarpetBlock.class, PaleMossCarpetBlock.class})
public class CarpetDyShapeMixin {

    @Inject(method = "getOutlineShape", at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetCarpetOutline(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx,
                                             CallbackInfoReturnable<VoxelShape> cir) {
        double dy = ClientDy.dyFor(world, pos, state);
        if (dy != 0.0) {
            cir.setReturnValue(cir.getReturnValue().offset(0.0, dy, 0.0));
        }
    }
}
