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
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drops a wall connection toward a same-family neighbour that Slabbed renders at a different
 * visual height (one lowered onto a slab, the other not). The side shape is forced to
 * {@link WallShape#NONE} and the centre post is forced up so the wall reads as a standalone
 * pillar instead of an arm bridging the height step. Mirrors the 1.21.11 compat fix, adapted
 * to the 1.21.1 6-arg {@code getStateForNeighborUpdate} signature.
 */
@Mixin(WallBlock.class)
public abstract class WallSlabConnectionMixin {

    @Inject(method = "getStateForNeighborUpdate", at = @At("RETURN"), cancellable = true)
    private void slabbed$breakSteppedNeighborConnection(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            WorldAccess world,
            BlockPos pos,
            BlockPos neighborPos,
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
    private static EnumProperty<WallShape> slabbed$wallShapeProperty(Direction direction) {
        switch (direction) {
            case NORTH: return Properties.NORTH_WALL_SHAPE;
            case EAST:  return Properties.EAST_WALL_SHAPE;
            case SOUTH: return Properties.SOUTH_WALL_SHAPE;
            case WEST:  return Properties.WEST_WALL_SHAPE;
            default:    return null;
        }
    }

    @Unique
    private static BlockState slabbed$breakConnection(
            BlockView world, BlockPos pos, BlockState state, Direction direction,
            BlockPos neighborPos, BlockState neighborState) {
        EnumProperty<WallShape> prop = slabbed$wallShapeProperty(direction);
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
