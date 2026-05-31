package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fences and glass panes / iron bars must not connect to a horizontally-adjacent
 * connector of the same family that sits at a different visual height.
 *
 * <p>When placed consecutively down a run of slabs (one lowered onto a slab surface,
 * the neighbour at grid height), a vanilla join draws a connector arm across the
 * height step. Suppressing it keeps them as clean single posts; a flat run at a
 * uniform height still connects. Consistent for vanilla and custom (Terrain Slabs)
 * supports.
 *
 * <p>{@link FenceBlock} and {@link PaneBlock} share {@code HorizontalConnectingBlock}'s
 * boolean N/E/S/W connection properties and method shapes, so one mixin covers both.
 * Walls use {@code WallShape} side properties and are handled by
 * {@code WallSlabConnectionMixin}.
 */
@Mixin({FenceBlock.class, PaneBlock.class})
public abstract class FencePaneSlabConnectionMixin {

    @Inject(method = "getStateForNeighborUpdate", at = @At("RETURN"), cancellable = true)
    private void slabbed$breakSteppedNeighborConnection(
            BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
            Direction direction, BlockPos neighborPos, BlockState neighborState, Random random,
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

    @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
    private void slabbed$breakSteppedPlacementConnection(
            ItemPlacementContext ctx, CallbackInfoReturnable<BlockState> cir) {
        BlockState result = cir.getReturnValue();
        if (result == null) {
            return;
        }
        WorldView world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        BlockState changed = result;
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(dir);
            changed = slabbed$breakConnection(world, pos, changed, dir, neighborPos,
                    world.getBlockState(neighborPos));
        }
        if (changed != result) {
            cir.setReturnValue(changed);
        }
    }

    @Unique
    private static BlockState slabbed$breakConnection(
            BlockView world, BlockPos pos, BlockState state, Direction direction,
            BlockPos neighborPos, BlockState neighborState) {
        BooleanProperty prop = ConnectingBlock.FACING_PROPERTIES.get(direction);
        if (prop == null || !state.contains(prop) || !state.get(prop)) {
            return state;
        }
        if (SlabSupport.isSteppedConnectingNeighbor(world, pos, state, neighborPos, neighborState)) {
            return state.with(prop, false);
        }
        return state;
    }
}
