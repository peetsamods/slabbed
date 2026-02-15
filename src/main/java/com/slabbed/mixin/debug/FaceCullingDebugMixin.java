package com.slabbed.mixin.debug;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class FaceCullingDebugMixin {

    @Inject(method = "shouldDrawSide", at = @At("RETURN"))
    private static void slabbed$debugCull(BlockState state, BlockState otherState, Direction side,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!(state.getBlock() instanceof SlabBlock)) {
            return;
        }
        if (side != Direction.DOWN) {
            return;
        }

        VoxelShape neighborCulling = otherState.getCullingFace(side.getOpposite());
        boolean neighborFull = neighborCulling == VoxelShapes.fullCube();
        if (!neighborFull) {
            return;
        }

        SlabType type = state.contains(SlabBlock.TYPE) ? state.get(SlabBlock.TYPE) : null;

        System.out.println("[SLABBED][CULL] slabType=" + type
                + " slab=" + state.getBlock()
                + " neighbor=" + otherState.getBlock()
                + " side=" + side
                + " neighborFull=" + neighborFull
                + " draw=" + cir.getReturnValue());
    }
}
