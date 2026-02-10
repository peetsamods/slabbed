package com.slabbed.mixin.debug;

import com.slabbed.Slabbed;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChainBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DEBUG ONLY: traces chain neighbor update handling to verify survival decisions.
 */
@Mixin(ChainBlock.class)
public abstract class ChainBlockNeighborUpdateDebugMixin {

    @Inject(method = "getStateForNeighborUpdate", at = @At("HEAD"))
    private void slabbed$debug$neighborHead(BlockState state, WorldView world, ScheduledTickView scheduledTickView,
                                            BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState,
                                            Random random, CallbackInfoReturnable<BlockState> cir) {
        if (!(state.getBlock() instanceof ChainBlock)) return;
        Slabbed.LOGGER.info("[SLABBED][ChainBlock][neighbor][HEAD] pos={} axis={} dir={} neighbor={} neighborPos={} state={}",
                pos.toShortString(),
                state.get(ChainBlock.AXIS),
                direction,
                neighborState.getBlock(),
                neighborPos.toShortString(),
                state.getBlock());
    }

    @Inject(method = "getStateForNeighborUpdate", at = @At("RETURN"))
    private void slabbed$debug$neighborReturn(BlockState state, WorldView world, ScheduledTickView scheduledTickView,
                                               BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState,
                                               Random random, CallbackInfoReturnable<BlockState> cir) {
        if (!(state.getBlock() instanceof ChainBlock)) return;
        BlockState result = cir.getReturnValue();
        Slabbed.LOGGER.info("[SLABBED][ChainBlock][neighbor][RETURN] pos={} axis={} dir={} neighbor={} result={} resultBlock={} resultAxis={}",
                pos.toShortString(),
                state.get(ChainBlock.AXIS),
                direction,
                neighborState.getBlock(),
                result,
                result.getBlock(),
                result.contains(ChainBlock.AXIS) ? result.get(ChainBlock.AXIS) : null);
    }
}
