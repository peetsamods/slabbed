package com.slabbed.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemPlaceFailLogMixin {
    @Shadow public abstract Block getBlock();

    @Inject(method = "place", at = @At("RETURN"))
    private void slabbed$logPlaceFail(ItemPlacementContext ctx, CallbackInfoReturnable<net.minecraft.util.ActionResult> cir) {
        if (cir.getReturnValue().isAccepted()) {
            return; // only failures
        }

        World world = ctx.getWorld();
        if (world.isClient()) {
            return; // server-only to avoid duplicates
        }

        Block placing = this.getBlock();
        BlockPos pos = ctx.getBlockPos();
        Direction side = ctx.getSide();

        BlockState at = world.getBlockState(pos);
        BlockState above = world.getBlockState(pos.up());
        BlockState below = world.getBlockState(pos.down());

        System.out.println("[SLABBED][PLACE-FAIL] placing=" + placing
                + " side=" + side
                + " pos=" + pos
                + " at=" + at.getBlock()
                + " above=" + above.getBlock()
                + " below=" + below.getBlock());
    }
}
