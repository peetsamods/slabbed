package com.slabbed.mixin;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.enums.WireConnection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redstone wire: allow slab top faces to count as valid ground for placement/survival and
 * down-step connectivity checks by treating them as solid UP surfaces.
 */
@Mixin(RedstoneWireBlock.class)
public abstract class RedstoneWireBlockMixin {

    @Inject(method = "canPlaceAt(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
    private void slabbed$canPlaceAt(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Slabbed.LOGGER.info("[SLABBED] Redstone mixin fired: canPlaceAt pos={} below={}", pos, world.getBlockState(pos.down()).getBlock());
        if (SlabSupport.isRedstoneSupportTopSurface(world, pos.down())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getRenderConnectionType(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Lnet/minecraft/block/enums/WireConnection;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$getRenderConnectionType3(BlockView world, BlockPos pos, Direction direction,
                                                  CallbackInfoReturnable<WireConnection> cir) {
        Slabbed.LOGGER.info("[SLABBED] Redstone mixin fired: getRenderConnectionType(3) pos={} dir={} result={}", pos, direction, cir.getReturnValue());
        WireConnection current = cir.getReturnValue();
        if (current == WireConnection.NONE) {
            BlockPos sidePos = pos.offset(direction);
            if (SlabSupport.isRedstoneSupportTopSurface(world, sidePos)) {
                cir.setReturnValue(WireConnection.SIDE);
            }
        }
    }

    @Inject(method = "getRenderConnectionType(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;Z)Lnet/minecraft/block/enums/WireConnection;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$getRenderConnectionType4(BlockView world, BlockPos pos, Direction direction, boolean canRise,
                                                  CallbackInfoReturnable<WireConnection> cir) {
        Slabbed.LOGGER.info("[SLABBED] Redstone mixin fired: getRenderConnectionType(4) pos={} dir={} result={} canRise={}", pos, direction, cir.getReturnValue(), canRise);
        WireConnection current = cir.getReturnValue();
        if (current == WireConnection.NONE) {
            BlockPos sidePos = pos.offset(direction);
            if (SlabSupport.isRedstoneSupportTopSurface(world, sidePos)) {
                cir.setReturnValue(WireConnection.SIDE);
            }
        }
    }
}
