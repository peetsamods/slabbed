package com.slabbed.mixin.client;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side placement prediction. The instant the local client predicts a block
 * placement, optimistically apply the same anchor/carrier/freeze markers the server
 * will set (routed into the render mirror by {@link SlabAnchorAttachment#predictClientPlacement}),
 * so the offset model bakes at the correct lowered dy on the FIRST frame instead of
 * baking un-lowered and snapping down when the authoritative sync arrives a few ticks
 * later. Runs on both sides but no-ops off the client (guarded on {@code isClientSide}).
 */
@Mixin(BlockItem.class)
public abstract class BlockItemClientPredictMixin {

    @Inject(
            method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("RETURN"))
    private void slabbed$predictClientPlacement(
            BlockPlaceContext ctx,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (!cir.getReturnValue().consumesAction()) {
            return;
        }
        Level level = ctx.getLevel();
        if (!level.isClientSide()) {
            return;
        }
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        SlabAnchorAttachment.predictClientPlacement(level, pos, state);
    }
}
