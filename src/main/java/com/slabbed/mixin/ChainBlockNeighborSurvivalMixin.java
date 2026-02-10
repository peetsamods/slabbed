package com.slabbed.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChainBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.tick.ScheduledTickView;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ensures chains pop immediately when their support is lost.
 *
 * Verified via javap on 1.21.11 Yarn-mapped common-unpicked.jar:
 * AbstractBlock.canPlaceAt is {@code iconst_1; ireturn} (always true),
 * and neither PillarBlock nor ChainBlock override it.  Chains therefore
 * have no vanilla survival restriction.
 *
 * This mixin adds an axis-aware support check: a chain survives only if
 * at least one of the two blocks along its axis provides
 * isSideSolidFullSquare on the face toward the chain.
 */
@Mixin(ChainBlock.class)
public abstract class ChainBlockNeighborSurvivalMixin {

    @Inject(method = "getStateForNeighborUpdate", at = @At("RETURN"), cancellable = true)
    private void slabbed$survivalGuard(BlockState state, WorldView world, ScheduledTickView scheduledTickView,
                                       BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState,
                                       Random random, CallbackInfoReturnable<BlockState> cir) {
        BlockState result = cir.getReturnValue();
        if (result.isAir()) return;
        if (!slabbed$hasAxisSupport(state, world, pos)) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
        }
    }

    @Unique
    private static final int MAX_CHAIN_WALK = 16;

    @Unique
    private static boolean slabbed$hasAxisSupport(BlockState state, WorldView world, BlockPos pos) {
        Direction.Axis axis = state.get(ChainBlock.AXIS);
        Direction positive = Direction.from(axis, Direction.AxisDirection.POSITIVE);
        Direction negative = Direction.from(axis, Direction.AxisDirection.NEGATIVE);

        // walk positive direction through same-axis chains until we find solid support
        if (slabbed$walkChainForSupport(world, pos, axis, positive, negative)) {
            return true;
        }
        // walk negative direction
        return slabbed$walkChainForSupport(world, pos, axis, negative, positive);
    }

    @Unique
    private static boolean slabbed$walkChainForSupport(WorldView world, BlockPos pos,
                                                        Direction.Axis axis, Direction walkDir, Direction faceDir) {
        BlockPos cursor = pos.offset(walkDir);
        for (int i = 0; i < MAX_CHAIN_WALK; i++) {
            BlockState cur = world.getBlockState(cursor);
            // found solid support
            if (cur.isSideSolidFullSquare(world, cursor, faceDir)) {
                return true;
            }
            // continue through same-axis chains
            if (cur.getBlock() instanceof ChainBlock
                    && cur.contains(ChainBlock.AXIS)
                    && cur.get(ChainBlock.AXIS) == axis) {
                cursor = cursor.offset(walkDir);
                continue;
            }
            break;
        }
        return false;
    }
}
