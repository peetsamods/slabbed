package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Walls must not connect to a horizontally-adjacent wall at a different visual height (one lowered onto
 * a slab, the other at grid height) — the same single-post rule as {@code FencePaneSlabConnectionMixin}.
 *
 * <p>Walls use {@code WallSide} side properties, so a broken side is set to {@link WallSide#NONE}.
 * Whenever a side is broken the centre post ({@code UP}) is forced on so the wall never renders a
 * floating gap where the connection used to be.
 *
 * <p>26.x adaptation of the shipped 1.21.1/1.21.11 mixin: {@code getStateForNeighborUpdate}→
 * {@code updateShape}, {@code WallShape}→{@code WallSide}, {@code WALL_SHAPE_PROPERTIES_BY_DIRECTION}→
 * {@code WallBlock.PROPERTY_BY_DIRECTION}. {@code updateShape} is overloaded on {@link WallBlock}, so it
 * is targeted by full descriptor.
 */
@Mixin(WallBlock.class)
public abstract class WallSlabConnectionMixin {

    @Inject(
            method = "updateShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/ScheduledTickAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$breakSteppedNeighborConnection(
            BlockState state, LevelReader world, ScheduledTickAccess tickView, BlockPos pos,
            Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random,
            CallbackInfoReturnable<BlockState> cir) {
        if (!direction.getAxis().isHorizontal()) {
            return;
        }
        BlockState result = cir.getReturnValue();
        BlockState broken = slabbed$breakConnection(world, pos, result, direction, neighborPos, neighborState);
        if (broken != result) {
            cir.setReturnValue(broken);
        }
    }

    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void slabbed$breakSteppedPlacementConnection(
            BlockPlaceContext ctx, CallbackInfoReturnable<BlockState> cir) {
        BlockState result = cir.getReturnValue();
        if (result == null) {
            return;
        }
        LevelReader world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState changed = result;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(dir);
            changed = slabbed$breakConnection(world, pos, changed, dir, neighborPos,
                    world.getBlockState(neighborPos));
        }
        if (changed != result) {
            cir.setReturnValue(changed);
        }
    }

    @Unique
    private static BlockState slabbed$breakConnection(
            BlockGetter world, BlockPos pos, BlockState state, Direction direction,
            BlockPos neighborPos, BlockState neighborState) {
        EnumProperty<WallSide> prop = WallBlock.PROPERTY_BY_DIRECTION.get(direction);
        if (prop == null || !state.hasProperty(prop) || state.getValue(prop) == WallSide.NONE) {
            return state;
        }
        if (SlabSupport.isSteppedConnectingNeighbor(world, pos, state, neighborPos, neighborState)) {
            BlockState broken = state.setValue(prop, WallSide.NONE);
            if (broken.hasProperty(BlockStateProperties.UP)) {
                broken = broken.setValue(BlockStateProperties.UP, true);
            }
            return broken;
        }
        return state;
    }
}
