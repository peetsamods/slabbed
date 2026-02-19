package com.slabbed.mixin;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabbedDebug;
import net.minecraft.block.SlabBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemPlaceTraceMixin {

    @Inject(method = "place", at = @At("HEAD"))
    private void slabbed$tracePlaceHead(ItemPlacementContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        if (!SlabbedDebug.DEBUG_SBSB) return;
        BlockItem self = (BlockItem) (Object) this;
        if (!(self.getBlock() instanceof SlabBlock)) return;

        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        Slabbed.LOGGER.info("[SBSB-TRACE][HEAD] side={} block={} clickedPos={} face={} hitY={} stateAtPos={} replaceable={} stateAbove={} replaceableAbove={}",
                world.isClient() ? "CLIENT" : "SERVER",
                Registries.BLOCK.getId(self.getBlock()),
                pos,
                ctx.getSide(),
                String.format("%.4f", ctx.getHitPos().y),
                world.getBlockState(pos),
                world.getBlockState(pos).isReplaceable(),
                world.getBlockState(pos.up()),
                world.getBlockState(pos.up()).isReplaceable());
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void slabbed$tracePlaceReturn(ItemPlacementContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        if (!SlabbedDebug.DEBUG_SBSB) return;
        BlockItem self = (BlockItem) (Object) this;
        if (!(self.getBlock() instanceof SlabBlock)) return;

        World world = ctx.getWorld();
        Slabbed.LOGGER.info("[SBSB-TRACE][RETURN] side={} block={} result={}",
                world.isClient() ? "CLIENT" : "SERVER",
                Registries.BLOCK.getId(self.getBlock()),
                cir.getReturnValue());
    }
}
