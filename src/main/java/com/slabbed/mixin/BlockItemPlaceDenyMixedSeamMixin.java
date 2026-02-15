package com.slabbed.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
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
public abstract class BlockItemPlaceDenyMixedSeamMixin {
    @Shadow public abstract Block getBlock();

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void slabbed$denyMixedSeam(ItemPlacementContext ctx, CallbackInfoReturnable<net.minecraft.util.ActionResult> cir) {
        Block placing = this.getBlock();
        if (!(placing instanceof SlabBlock)) return; // only care about slab placement for this slice

        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        Direction side = ctx.getSide();

        // focus on vertical seam placements
        if (side != Direction.UP && side != Direction.DOWN) return;

        BlockState at = world.getBlockState(pos);
        BlockState above = world.getBlockState(pos.up());
        BlockState below = world.getBlockState(pos.down());

        boolean atIsSlab = at.getBlock() instanceof SlabBlock;
        boolean aboveIsSlab = above.getBlock() instanceof SlabBlock;
        boolean belowIsSlab = below.getBlock() instanceof SlabBlock;

        boolean atFull = at.isFullCube(world, pos);
        boolean aboveFull = above.isFullCube(world, pos.up());
        boolean belowFull = below.isFullCube(world, pos.down());

        SlabType atType = atIsSlab && at.contains(SlabBlock.TYPE) ? at.get(SlabBlock.TYPE) : null;
        SlabType aboveType = aboveIsSlab && above.contains(SlabBlock.TYPE) ? above.get(SlabBlock.TYPE) : null;
        SlabType belowType = belowIsSlab && below.contains(SlabBlock.TYPE) ? below.get(SlabBlock.TYPE) : null;

        boolean window1 = belowIsSlab && atFull && aboveIsSlab;
        boolean window2 = atIsSlab && aboveFull && world.getBlockState(pos.up(2)).getBlock() instanceof SlabBlock;
        boolean likelyMixed = window1 || window2;

        if (!likelyMixed) return;

        System.out.println("[SLABBED][MIXED-SEAM][DENY] placing=" + placing
                + " side=" + side
                + " pos=" + pos
                + " at=" + at.getBlock() + " atFull=" + atFull + " atSlabType=" + atType
                + " above=" + above.getBlock() + " aboveFull=" + aboveFull + " aboveSlabType=" + aboveType
                + " below=" + below.getBlock() + " belowFull=" + belowFull + " belowSlabType=" + belowType);

        cir.setReturnValue(net.minecraft.util.ActionResult.FAIL);
    }
}
