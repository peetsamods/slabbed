package com.slabbed.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Client adapter for the canonical logical dy. Carpet no longer owns a separate render courtesy:
 * aimed C5 placements render from their stored dy, while natural/aimless carpet remains flush.
 */
public final class ClientDy {
    private ClientDy() {
    }

    public static double dyFor(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return 0.0;
        }
        return SlabSupport.getYOffset(world, pos, state);
    }

    /** Applies the canonical logical dy to a client-only outline/raycast adapter. */
    public static VoxelShape offsetShape(
            BlockGetter world,
            BlockPos pos,
            BlockState state,
            VoxelShape shape
    ) {
        double dy = dyFor(world, pos, state);
        return dy == 0.0 ? shape : shape.move(0.0, dy, 0.0);
    }
}
