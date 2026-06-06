package com.slabbed.mixin;

import com.slabbed.util.SlabbedAuditBridge;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SlabBlock.class)
public abstract class SlabBlockPlacementRecorderMixin {

    @Inject(method = "getPlacementState", at = @At("RETURN"), require = 0)
    private void slabbed$recordSlabPlacementState(
            ItemPlacementContext ctx,
            CallbackInfoReturnable<BlockState> cir
    ) {
        SlabbedAuditBridge.invokeRecorder(
                "recordSlabPlacementState",
                new Class<?>[]{ItemPlacementContext.class, BlockState.class},
                ctx,
                cir.getReturnValue());
    }
}
