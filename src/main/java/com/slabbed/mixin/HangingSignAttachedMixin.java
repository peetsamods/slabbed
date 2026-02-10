package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.HangingSignBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ensures hanging signs placed under a TOP slab get ATTACHED=true,
 * which renders the double-chain model instead of the loop model.
 *
 * Vanilla determines ATTACHED via Block.isFaceFullSquare on the
 * collision shape of the block above, which returns false for top
 * slabs (their DOWN face is at y=0.5, not y=0.0).
 */
@Mixin(HangingSignBlock.class)
public abstract class HangingSignAttachedMixin {

    @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
    private void slabbed$attachToTopSlab(ItemPlacementContext ctx,
                                          CallbackInfoReturnable<BlockState> cir) {
        BlockState result = cir.getReturnValue();
        if (result == null) return;
        World world = ctx.getWorld();
        BlockPos placementPos = ctx.getBlockPos();
        BlockPos above = placementPos.up();
        BlockState stateAbove = world.getBlockState(above);
        if (SlabSupport.isTopSlab(stateAbove)) {
            // ATTACHED=false → CEILING type → double-chain model (directly under solid block)
            // ATTACHED=true  → CEILING_MIDDLE type → loop model (middle of sign chain)
            cir.setReturnValue(result.with(HangingSignBlock.ATTACHED, false));
        }
    }
}
