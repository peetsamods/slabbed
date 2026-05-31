package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.block.FenceBlock;
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
 * Fences must not connect to a horizontally-adjacent fence that sits at a
 * different visual height.
 *
 * <p>When fences are placed consecutively down a run of slabs (one fence
 * lowered onto a slab surface, the neighbour at grid height), vanilla would
 * still connect them and draw two connector arms at mismatched heights — a
 * broken-looking join. Suppressing the connection keeps them as clean single
 * posts. A flat fence run at a uniform height (e.g. all on the same slab
 * surface, or all on the ground) still connects normally.
 *
 * <p>Height is compared using the fence's <em>visual</em> dy, mirroring
 * {@code OffsetBlockStateModel}: fences are only visually lowered on custom
 * (Terrain Slabs) direct-support surfaces, not on vanilla slab supports, so
 * the rule stays consistent for both vanilla and custom slabs.
 */
@Mixin(FenceBlock.class)
public abstract class FenceSlabConnectionMixin {

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
        // Only break fence-to-fence joins; fence-to-solid-block connections are left alone.
        if (!(neighborState.getBlock() instanceof FenceBlock)) {
            return state;
        }
        double selfDy = slabbed$visualDy(world, pos, state);
        double neighborDy = slabbed$visualDy(world, neighborPos, neighborState);
        if (Math.abs(selfDy - neighborDy) > 1.0e-6) {
            return state.with(prop, false);
        }
        return state;
    }

    @Unique
    private static double slabbed$visualDy(BlockView world, BlockPos pos, BlockState state) {
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy != 0.0 && !SlabSupport.isDirectCustomSlabSupportedObject(world, pos, state)) {
            // fence on a vanilla slab support is not visually lowered (matches OffsetBlockStateModel)
            return 0.0;
        }
        return dy;
    }
}
