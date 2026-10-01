package com.slabbed.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.shape.VoxelShape;

/**
 * A fence's central post connects across the half-cell below a top slab.
 * This changes connection geometry only; the frozen placement seat is never written (LAW.md).
 */
public final class FenceCeilingConnection {
    private static final double EPS = 1.0e-6d;

    private FenceCeilingConnection() { }

    /** Post height before applying its own frozen seat, or one for an ordinary post. */
    public static double postTop(BlockView world, BlockPos pos, BlockState state, double seat) {
        if (!(state.getBlock() instanceof FenceBlock) || !Double.isFinite(seat)) {
            return 1.0d;
        }
        BlockPos above = pos.up();
        BlockState ceiling = world.getBlockState(above);
        if (!(ceiling.getBlock() instanceof SlabBlock)
                || ceiling.get(SlabBlock.TYPE) != SlabType.TOP) {
            return 1.0d;
        }
        double top = 1.5d + SlabSupport.getYOffset(world, above, ceiling) - seat;
        return top > 1.0d + EPS && top <= 1.5d + EPS ? Math.min(1.5d, top) : 1.0d;
    }

    /** Extends only the central post in the already seated outline. */
    public static VoxelShape outline(BlockView world, BlockPos pos, BlockState state,
                                     double seat, VoxelShape seated) {
        double top = postTop(world, pos, state, seat);
        if (top <= 1.0d) {
            return seated;
        }
        return VoxelShapes.union(seated, VoxelShapes.cuboid(0.375d, 1.0d + seat, 0.375d,
                0.625d, top + seat, 0.625d));
    }
}
