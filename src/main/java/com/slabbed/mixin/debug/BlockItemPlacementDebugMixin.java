package com.slabbed.mixin.debug;

import com.slabbed.Slabbed;
import com.slabbed.mixin.debug.accessor.ItemUsageContextInvoker;
import net.minecraft.block.ChainBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DEBUG ONLY. Logs CHAIN + hanging sign placement attempts and hit data when available.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemPlacementDebugMixin {

    private static boolean slabbed$isTraceTarget(ItemUsageContext ctx) {
        var stack = ctx.getStack();
        // CHAIN always
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ChainBlock) return true;

        // Hanging sign items (cover all wood variants via explicit list)
        return stack.isOf(Items.OAK_HANGING_SIGN)
                || stack.isOf(Items.SPRUCE_HANGING_SIGN)
                || stack.isOf(Items.BIRCH_HANGING_SIGN)
                || stack.isOf(Items.JUNGLE_HANGING_SIGN)
                || stack.isOf(Items.ACACIA_HANGING_SIGN)
                || stack.isOf(Items.DARK_OAK_HANGING_SIGN)
                || stack.isOf(Items.MANGROVE_HANGING_SIGN)
                || stack.isOf(Items.CHERRY_HANGING_SIGN)
                || stack.isOf(Items.BAMBOO_HANGING_SIGN)
                || stack.isOf(Items.CRIMSON_HANGING_SIGN)
                || stack.isOf(Items.WARPED_HANGING_SIGN);
    }

    @Inject(method = "useOnBlock", at = @At("HEAD"))
    private void slabbed$debug$useOnBlockHead(ItemUsageContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        if (!slabbed$isTraceTarget(ctx)) return;

        var stack = ctx.getStack();
        System.out.println("[slabbed][BlockItem.useOnBlock][HEAD] item=" + stack.getItem()
                + " ctxSide=" + ctx.getSide()
                + " ctxPos=" + ctx.getBlockPos()
        );

        // Chain-only hit result diagnostics with safety guard
        if (ctx.getStack().getItem() instanceof BlockItem bi && bi.getBlock() instanceof ChainBlock) {
            if (ctx instanceof ItemPlacementContext placementCtx) {
                try {
                    BlockHitResult hit = ((ItemUsageContextInvoker) (Object) ctx).slabbed$getHitResult();
                    if (hit == null) {
                        Slabbed.LOGGER.info("[SLABBED][BlockItem.useOnBlock:hit] item={} hit=null ctx.blockPos={} ctx.side={}",
                                ctx.getStack().getItem(),
                                placementCtx.getBlockPos().toShortString(),
                                placementCtx.getSide());
                        return;
                    }
                    BlockPos hitPos = hit.getBlockPos();
                    Direction hitSide = hit.getSide();
                    Vec3d hitVec = hit.getPos();
                    Slabbed.LOGGER.info("[SLABBED][BlockItem.useOnBlock:hit] item={} hitPos={} hitSide={} hitVec={} ctx.blockPos={} ctx.side={}",
                            ctx.getStack().getItem(),
                            hitPos.toShortString(),
                            hitSide,
                            hitVec,
                            placementCtx.getBlockPos().toShortString(),
                            placementCtx.getSide());
                } catch (Throwable t) {
                    Slabbed.LOGGER.info("[SLABBED][BlockItem.useOnBlock:hit] item={} hit=error type={} msg={} ctx.blockPos={} ctx.side={}",
                            ctx.getStack().getItem(),
                            t.getClass().getSimpleName(),
                            t.getMessage(),
                            placementCtx.getBlockPos().toShortString(),
                            placementCtx.getSide());
                }
            } else {
                Slabbed.LOGGER.info("[SLABBED][BlockItem.useOnBlock:hit] item={} ctx is not ItemPlacementContext; skipping hit logging",
                        ctx.getStack().getItem());
            }
        }
    }

    @Inject(method = "useOnBlock", at = @At("RETURN"))
    private void slabbed$debug$useOnBlockReturn(ItemUsageContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        if (!slabbed$isTraceTarget(ctx)) return;

        System.out.println("[slabbed][BlockItem.useOnBlock][RETURN] result=" + cir.getReturnValue());
    }
}
