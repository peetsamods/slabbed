package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redstone wire: allow slab top faces to count as valid ground for placement and survival.
 *
 * <p>Connection shape and redstone direction remain vanilla-owned. Slabbed's top-face solidity
 * hooks already let vanilla discover dust above supported slabs through its normal rise/drop
 * checks; overriding a vanilla {@code NONE} result here would bypass component and obstruction
 * rules (GH #37).
 */
@Mixin(RedstoneWireBlock.class)
public abstract class RedstoneWireBlockMixin {

    @Inject(method = "canPlaceAt(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
    private void slabbed$canPlaceAt(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SlabSupport.isRedstoneSupportTopSurface(world, pos.down())) {
            cir.setReturnValue(true);
        }
    }
}
