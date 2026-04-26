package com.slabbed.mixin.debug;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import com.slabbed.debug.DebugTraceContext;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.SideShapeType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class BlockStateSideSolidDebugMixin {
    private static int slabbed$logCount = 0;
    private static final int slabbed$logLimit = 30;

    @Inject(method = "isSideSolid", at = @At("RETURN"))
    private void slabbed$logSideSolid(BlockView world, BlockPos pos, Direction direction, SideShapeType shapeType,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (direction != Direction.DOWN || shapeType != SideShapeType.CENTER) return;
        if (!DebugTraceContext.isActive()) return;
        if (slabbed$logCount++ >= slabbed$logLimit) return;
        BlockState state = world.getBlockState(pos);
        if (!SlabSupport.isSupportingSlab(state)) return;
        boolean result = cir.getReturnValue();
        if (result) return;
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        StackTraceElement[] snippet = Arrays.copyOfRange(trace, Math.min(4, trace.length), Math.min(10, trace.length));
        Slabbed.LOGGER.info("[SLABBED][debug][isSideSolid] item={} pos={} slabType={} dir=DOWN shape=CENTER result={} stack={}",
                DebugTraceContext.getActiveItem(),
                pos.toShortString(),
                state.contains(net.minecraft.block.SlabBlock.TYPE) ? state.get(net.minecraft.block.SlabBlock.TYPE) : null,
                result,
                Arrays.toString(snippet));
    }
}
