package com.slabbed.mixin.debug;

import com.slabbed.Slabbed;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChainBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DEBUG ONLY: traces scheduled ticks for chain blocks to see if removal runs.
 */
@Mixin(net.minecraft.block.Block.class)
public abstract class ChainBlockScheduledTickDebugMixin {

    @Inject(method = "scheduledTick", at = @At("HEAD"))
    private void slabbed$debug$scheduledTickHead(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (!(state.getBlock() instanceof ChainBlock)) return;
        boolean ok = state.canPlaceAt(world, pos);
        Slabbed.LOGGER.info("[SLABBED][ChainBlock][scheduledTick][HEAD] pos={} axis={} ok={} isClient={}",
                pos.toShortString(),
                state.getOrEmpty(net.minecraft.block.ChainBlock.AXIS).orElse(null),
                ok,
                world.isClient());
    }

    @Inject(method = "scheduledTick", at = @At("RETURN"))
    private void slabbed$debug$scheduledTickReturn(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (!(state.getBlock() instanceof ChainBlock)) return;
        Slabbed.LOGGER.info("[SLABBED][ChainBlock][scheduledTick][RETURN] pos={} axis={} isClient={}",
                pos.toShortString(),
                state.getOrEmpty(net.minecraft.block.ChainBlock.AXIS).orElse(null),
                world.isClient());
    }
}
