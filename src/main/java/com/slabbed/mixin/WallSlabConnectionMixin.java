package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
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
 * Drops a wall connection toward a same-family neighbour that Slabbed renders at a different
 * visual height (one lowered onto a slab, the other not). The side shape is forced to
 * {@link WallSide#NONE} and the centre post is forced up so the wall reads as a standalone
 * pillar instead of an arm bridging the height step. Mirrors the 1.21.11 compat fix, adapted
 * to the 1.21.1 6-arg {@code updateShape} signature.
 */
@Mixin(WallBlock.class)
public abstract class WallSlabConnectionMixin {

    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
    private void slabbed$breakSteppedNeighborConnection(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor world,
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
    private static EnumProperty<WallSide> slabbed$wallShapeProperty(Direction direction) {
        switch (direction) {
            case NORTH: return BlockStateProperties.NORTH_WALL;
            case EAST:  return BlockStateProperties.EAST_WALL;
            case SOUTH: return BlockStateProperties.SOUTH_WALL;
            case WEST:  return BlockStateProperties.WEST_WALL;
            default:    return null;
        }
    }

    @Unique
    private static BlockState slabbed$breakConnection(
            BlockGetter world, BlockPos pos, BlockState state, Direction direction,
            BlockPos neighborPos, BlockState neighborState) {
        EnumProperty<WallSide> prop = slabbed$wallShapeProperty(direction);
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
