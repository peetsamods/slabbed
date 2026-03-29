package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabSupportClient;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockRenderInfo.class)
public abstract class BlockRenderInfoMixin implements BlockRenderInfoAcessor
{
    @Shadow @Final private BlockPos.Mutable searchPos;
    @Shadow public BlockRenderView blockView;
    @Shadow public BlockState blockState;
    @Shadow public BlockPos blockPos;
    @Shadow private boolean enableCulling;
    @Shadow private int cullCompletionFlags;
    @Shadow private int cullResultFlags;

    @Inject(
        method = "shouldDrawSide",
        at = @At(
            value = "HEAD"
        ),
        cancellable = true)
    public void slabbed$shouldDrawSide(Direction side, CallbackInfoReturnable<Boolean> cir)
    {
        if (side == null || !enableCulling)
        {
            cir.setReturnValue(true);
            return;
        }

        final int mask = 1 << side.getIndex();

        if ((cullCompletionFlags & mask) == 0)
        {
            slabbed$setCullCompletionFlags(cullCompletionFlags | mask);

            searchPos.set(blockPos, side);
            BlockState searchState = blockView.getBlockState(searchPos);

            if (Block.shouldDrawSide(blockState, searchState, side) ||
                (SlabSupportClient.HAS_SLABBED_BLOCK.get() &&
                    (SlabSupport.getYOffset(blockView, blockPos, blockState) != 0 ||
                     SlabSupport.getYOffset(blockView, searchPos, searchState) != 0)
                )
            )
            {
                slabbed$setCullResultFlags(cullResultFlags | mask);
                cir.setReturnValue(true);
            } else
            {
                cir.setReturnValue(false);
            }
        } else
        {
            cir.setReturnValue((cullResultFlags & mask) != 0);
        }
    }
}
