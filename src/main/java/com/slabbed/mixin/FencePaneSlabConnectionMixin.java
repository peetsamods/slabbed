package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fences and iron bars / glass panes must not connect to a horizontally-adjacent connector of the
 * same family that sits at a different visual height.
 *
 * <p>When placed consecutively down a run of slabs (one lowered onto a slab surface, the neighbour at
 * grid height), a vanilla join draws a connector arm across the height step. Suppressing it keeps them
 * as clean single posts; a flat run at a uniform height still connects. Consistent for vanilla and
 * custom (Terrain Slabs) supports.
 *
 * <p>{@link FenceBlock} and {@link IronBarsBlock} (Mojang name for the old {@code PaneBlock}) share
 * {@link CrossCollisionBlock}'s boolean N/E/S/W connection properties and method shapes, so one mixin
 * covers both. Walls use {@code WallSide} side properties and are handled by
 * {@code WallSlabConnectionMixin}.
 *
 * <p>26.x adaptation of the shipped 1.21.1/1.21.11 mixin: {@code getStateForNeighborUpdate}→
 * {@code updateShape}, {@code ItemPlacementContext}→{@code BlockPlaceContext},
 * {@code ConnectingBlock.FACING_PROPERTIES}→{@code CrossCollisionBlock.PROPERTY_BY_DIRECTION}.
 */
@Mixin({FenceBlock.class, IronBarsBlock.class})
public abstract class FencePaneSlabConnectionMixin {

    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
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
        BooleanProperty prop = CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(direction);
        if (prop == null || !state.hasProperty(prop) || !state.getValue(prop)) {
            return state;
        }
        if (SlabSupport.isSteppedConnectingNeighbor(world, pos, state, neighborPos, neighborState)) {
            return state.setValue(prop, false);
        }
        return state;
    }
}
