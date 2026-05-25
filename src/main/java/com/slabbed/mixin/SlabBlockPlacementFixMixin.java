package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SlabBlock.class)
public abstract class SlabBlockPlacementFixMixin {

    @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
    private void slabbed$forceTopSlabOnOffsetSupport(BlockPlaceContext ctx, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state == null) return;

        if (!state.hasProperty(SlabBlock.TYPE) || state.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) return;
        if (ctx.getClickedFace() != Direction.UP) return;

        BlockPos pos = ctx.getClickedPos();
        BlockPos belowPos = pos.below();
        BlockState below = ctx.getLevel().getBlockState(belowPos);

        double dyBelow = SlabSupport.getYOffset(ctx.getLevel(), belowPos, below);
        if (dyBelow == 0.0) return;

        cir.setReturnValue(state.setValue(SlabBlock.TYPE, SlabType.TOP));
    }
}
