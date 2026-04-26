// package com.slabbed.mixin.debug;
//
// import com.slabbed.Slabbed;
// import net.minecraft.block.BlockState;
// import net.minecraft.block.ChainBlock;
// import net.minecraft.block.AbstractChainBlock;
// import net.minecraft.util.math.BlockPos;
// import net.minecraft.world.WorldView;
// import org.spongepowered.asm.mixin.Mixin;
// import org.spongepowered.asm.mixin.injection.At;
// import org.spongepowered.asm.mixin.injection.Inject;
// import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//
// /**
//  * DEBUG ONLY: traces chain canPlaceAt decisions with surrounding support states.
//  * TEMPORARILY DISABLED.
//  */
// @Mixin(AbstractChainBlock.class)
// public abstract class BlockStateCanPlaceAtChainDebugMixin {
//
//     @Inject(method = "canPlaceAt(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;)Z", at = @At("HEAD"))
//     private void slabbed$debug$canPlaceAtHead(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
//         if (!(state.getBlock() instanceof ChainBlock)) return;
//
//         BlockState up = world.getBlockState(pos.up());
//         BlockState down = world.getBlockState(pos.down());
//         BlockState north = world.getBlockState(pos.north());
//         BlockState south = world.getBlockState(pos.south());
//         BlockState east = world.getBlockState(pos.east());
//         BlockState west = world.getBlockState(pos.west());
//         Slabbed.LOGGER.info("[SLABBED][canPlaceAt][CHAIN][HEAD] pos={} axis={} up={} down={} north={} south={} east={} west={}",
//                 pos.toShortString(),
//                 state.getOrEmpty(net.minecraft.block.ChainBlock.AXIS).orElse(null),
//                 up.getBlock(),
//                 down.getBlock(),
//                 north.getBlock(),
//                 south.getBlock(),
//                 east.getBlock(),
//                 west.getBlock());
//     }
//
//     @Inject(method = "canPlaceAt(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;)Z", at = @At("RETURN"))
//     private void slabbed$debug$canPlaceAtReturn(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
//         if (!(state.getBlock() instanceof ChainBlock)) return;
//         Slabbed.LOGGER.info("[SLABBED][canPlaceAt][CHAIN][RETURN] pos={} axis={} result={}",
//                 pos.toShortString(),
//                 state.getOrEmpty(net.minecraft.block.ChainBlock.AXIS).orElse(null),
//                 cir.getReturnValue());
//     }
// }
