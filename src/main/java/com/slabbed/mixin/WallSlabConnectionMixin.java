package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.WallShape;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
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
 * Walls must not connect to a horizontally-adjacent wall at a different visual height
 * (one lowered onto a slab, the other at grid height) — the same single-post rule as
 * {@code FencePaneSlabConnectionMixin}.
 *
 * <p>Walls use {@code WallShape} side properties, so a broken side is set to
 * {@link WallShape#NONE}. Whenever a side is broken the centre post ({@code UP}) is
 * forced on so the wall never renders a floating gap where the connection used to be.
 */
@Mixin(WallBlock.class)
public abstract class WallSlabConnectionMixin {

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
        EnumProperty<WallShape> prop = WallBlock.WALL_SHAPE_PROPERTIES_BY_DIRECTION.get(direction);
        if (prop == null || !state.contains(prop) || state.get(prop) == WallShape.NONE) {
            return state;
        }
        if (SlabSupport.isSteppedConnectingNeighbor(world, pos, state, neighborPos, neighborState)) {
            BlockState broken = state.with(prop, WallShape.NONE);
            if (broken.contains(Properties.UP)) {
                broken = broken.with(Properties.UP, true);
            }
            return broken;
        }
        return state;
    }
}
