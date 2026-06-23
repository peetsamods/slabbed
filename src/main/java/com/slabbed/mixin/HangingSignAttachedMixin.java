package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
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
@Mixin(CeilingHangingSignBlock.class)
public abstract class HangingSignAttachedMixin {

    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void slabbed$attachToTopSlab(BlockPlaceContext ctx,
                                          CallbackInfoReturnable<BlockState> cir) {
        BlockState result = cir.getReturnValue();
        if (result == null) return;
        Level world = ctx.getLevel();
        BlockPos placementPos = ctx.getClickedPos();
        BlockPos above = placementPos.above();
        BlockState stateAbove = world.getBlockState(above);
        if (SlabSupport.isTopSlab(stateAbove)) {
            // ATTACHED=false → CEILING type → double-chain model (directly under solid block)
            // ATTACHED=true  → CEILING_MIDDLE type → loop model (middle of sign chain)
            cir.setReturnValue(result.setValue(CeilingHangingSignBlock.ATTACHED, false));
        }
    }
}
