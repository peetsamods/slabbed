package com.slabbed.mixin.debug;

import com.slabbed.Slabbed;
import com.slabbed.mixin.debug.accessor.ItemUsageContextInvoker;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChainBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemGetPlacementStateDebugMixin {

    @Inject(method = "getPlacementState", at = @At("HEAD"))
    private void slabbed$debug$getPlacementStateHead(ItemPlacementContext ctx, CallbackInfoReturnable<BlockState> cir) {
        if (ctx == null) return;
        if (!(ctx.getStack().getItem() instanceof BlockItem bi && bi.getBlock() instanceof ChainBlock)) return;
        BlockPos ctxPos = ctx.getBlockPos();
        Direction ctxSide = ctx.getSide();
        try {
            BlockHitResult hit = ((ItemUsageContextInvoker) (Object) ctx).slabbed$getHitResult();
            if (hit == null) {
                Slabbed.LOGGER.info("[SLABBED][BlockItem.getPlacementState][HEAD] item=chain hit=null ctxPos={} ctxSide={}",
                        ctxPos,
                        ctxSide);
                return;
            }
            BlockPos hitPos = hit.getBlockPos();
            Direction hitSide = hit.getSide();
            var world = ctx.getWorld();
            var ctxState = world.getBlockState(ctxPos);
            var hitState = world.getBlockState(hitPos);
            var belowHitState = world.getBlockState(hitPos.down());
            System.out.println("[slabbed][BlockItem.getPlacementState][HEAD] item=chain ctxPos=" + ctxPos + " ctxSide=" + ctxSide
                    + " hitPos=" + hitPos + " hitSide=" + hitSide + " hitVec=" + hit.getPos()
                    + " ctxState=" + ctxState + " hitState=" + hitState + " belowHitState=" + belowHitState);
        } catch (Throwable t) {
            Slabbed.LOGGER.info("[SLABBED][BlockItem.getPlacementState][HEAD] item=chain hit=error type={} msg={} ctxPos={} ctxSide={}",
                    t.getClass().getSimpleName(),
                    t.getMessage(),
                    ctxPos,
                    ctxSide);
        }
    }

    @Inject(method = "getPlacementState", at = @At("RETURN"))
    private void slabbed$debug$getPlacementStateReturn(ItemPlacementContext ctx, CallbackInfoReturnable<BlockState> cir) {
        if (ctx == null) return;
        if (!(ctx.getStack().getItem() instanceof BlockItem bi && bi.getBlock() instanceof ChainBlock)) return;
        BlockState state = cir.getReturnValue();
        if (state == null) {
            System.out.println("[slabbed][BlockItem.getPlacementState][RETURN] item=chain state=null");
            return;
        }
        System.out.println("[slabbed][BlockItem.getPlacementState][RETURN] item=chain state=" + state.getBlock()
                + " props=" + state.getEntries());
    }
}
