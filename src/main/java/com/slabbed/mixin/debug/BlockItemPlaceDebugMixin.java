package com.slabbed.mixin.debug;

import net.minecraft.block.ChainBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemPlaceDebugMixin {

    @Inject(method = "place", at = @At("HEAD"))
    private void slabbed$debug$placeHead(ItemPlacementContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        if (ctx == null) return;
        if (!(ctx.getStack().getItem() instanceof BlockItem bi && bi.getBlock() instanceof ChainBlock)) return;
        System.out.println("[slabbed][BlockItem.place][HEAD] item=chain ctxPos=" + ctx.getBlockPos()
                + " ctxSide=" + ctx.getSide());
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void slabbed$debug$placeReturn(ItemPlacementContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        if (ctx == null) return;
        if (!(ctx.getStack().getItem() instanceof BlockItem bi && bi.getBlock() instanceof ChainBlock)) return;
        System.out.println("[slabbed][BlockItem.place][RETURN] item=chain result=" + cir.getReturnValue());
    }
}
