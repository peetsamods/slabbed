package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SlabBlock.class)
public abstract class SlabBlockPlacementFixMixin {

    @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
    private void slabbed$forceTopSlabOnOffsetSupport(ItemPlacementContext ctx, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state == null) return;

        if (!state.contains(SlabBlock.TYPE) || state.get(SlabBlock.TYPE) != SlabType.BOTTOM) return;
        if (ctx.getSide() != Direction.UP) return;

        BlockPos pos = ctx.getBlockPos();
        BlockPos belowPos = pos.down();
        BlockState below = ctx.getWorld().getBlockState(belowPos);

        double dyBelow = SlabSupport.getYOffset(ctx.getWorld(), belowPos, below);
        if (dyBelow == 0.0) return;

        cir.setReturnValue(state.with(SlabBlock.TYPE, SlabType.TOP));
    }
}
