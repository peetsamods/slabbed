package com.slabbed.mixin.debug;

import com.slabbed.Slabbed;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChainBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DEBUG ONLY: traces ChainBlock.canPlaceAt to see which neighbors make it true.
 */
@Mixin(AbstractBlock.class)
public abstract class ChainBlockCanPlaceAtDebugMixin {

    @Inject(method = "canPlaceAt(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;)Z", at = @At("HEAD"), require = 0)
    private void slabbed$canPlaceAtHead(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!(state.getBlock() instanceof ChainBlock)) return;
        Direction[] dirs = Direction.values();
        StringBuilder sb = new StringBuilder();
        for (Direction dir : dirs) {
            BlockPos np = pos.offset(dir);
            BlockState ns = world.getBlockState(np);
            boolean solid = ns.isSideSolidFullSquare(world, np, dir.getOpposite());
            sb.append(dir.asString()).append('=')
              .append(ns.getBlock().getTranslationKey()).append(':')
              .append(solid).append(' ');
        }
        Slabbed.LOGGER.info("[SLABBED][ChainBlock][canPlaceAt][HEAD] pos={} axis={} isClient={} neighbors={}",
                pos.toShortString(),
                state.getOrEmpty(net.minecraft.block.ChainBlock.AXIS).orElse(null),
                world.isClient(),
                sb.toString().trim());
    }

    @Inject(method = "canPlaceAt(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;)Z", at = @At("RETURN"), require = 0)
    private void slabbed$canPlaceAtReturn(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!(state.getBlock() instanceof ChainBlock)) return;
        Slabbed.LOGGER.info("[SLABBED][ChainBlock][canPlaceAt][RETURN] pos={} axis={} isClient={} result={} ",
                pos.toShortString(),
                state.getOrEmpty(net.minecraft.block.ChainBlock.AXIS).orElse(null),
                world.isClient(),
                cir.getReturnValue());
    }
}
