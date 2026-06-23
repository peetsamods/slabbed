package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redstone wire: allow slab top faces to count as valid ground for placement/survival and
 * down-step connectivity checks by treating them as solid UP surfaces.
 */
@Mixin(RedStoneWireBlock.class)
public abstract class RedstoneWireBlockMixin {

    @Inject(method = "canSurvive",
            at = @At("HEAD"), cancellable = true)
    private void slabbed$canPlaceAt(BlockState state, LevelReader world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SlabSupport.isRedstoneSupportTopSurface(world, pos.below())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getConnectingSide(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/level/block/state/properties/RedstoneSide;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$getRenderConnectionType3(BlockGetter world, BlockPos pos, Direction direction,
                                                  CallbackInfoReturnable<RedstoneSide> cir) {
        RedstoneSide current = cir.getReturnValue();
        if (current == RedstoneSide.NONE) {
            BlockPos sidePos = pos.relative(direction);
            if (SlabSupport.isRedstoneSupportTopSurface(world, sidePos)) {
                cir.setReturnValue(RedstoneSide.SIDE);
            }
        }
    }

    @Inject(method = "getConnectingSide(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Lnet/minecraft/world/level/block/state/properties/RedstoneSide;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$getRenderConnectionType4(BlockGetter world, BlockPos pos, Direction direction, boolean canRise,
                                                  CallbackInfoReturnable<RedstoneSide> cir) {
        RedstoneSide current = cir.getReturnValue();
        if (current == RedstoneSide.NONE) {
            BlockPos sidePos = pos.relative(direction);
            if (SlabSupport.isRedstoneSupportTopSurface(world, sidePos)) {
                cir.setReturnValue(RedstoneSide.SIDE);
            }
        }
    }
}
