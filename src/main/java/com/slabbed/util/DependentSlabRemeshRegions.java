package com.slabbed.util;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.SlabBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import java.util.ArrayDeque;

/** Selects the cache and mesh regions affected by a lowering-context edit. */
public final class DependentSlabRemeshRegions {
    private DependentSlabRemeshRegions() { }

    public static void enqueue(BlockView world, BlockPos changed, int depth, DependentRemeshQueue queue) {
        int minY = changed.getY() - depth;
        int maxY = changed.getY() + depth;
        queue.enqueueBlockRegion(changed.getX() - 2, minY, changed.getZ() - 2,
                changed.getX() + 2, maxY, changed.getZ() + 2);

        var checked = new LongOpenHashSet();
        var columns = new LongOpenHashSet();
        var pending = new ArrayDeque<Step>();
        for (BlockPos pos : BlockPos.iterate(changed.add(-2, -depth, -2), changed.add(2, depth, 2))) {
            checked.add(pos.asLong());
            if (world.getBlockState(pos).getBlock() instanceof SlabBlock)
                pending.addLast(new Step(pos.toImmutable(), 0));
        }

        while (!pending.isEmpty()) {
            Step step = pending.removeFirst();
            BlockPos pos = step.pos();
            // Adjacent occupied columns may contain geometric followers or faces exposed by
            // the slab's new height. Empty remote sections have no geometry to rebuild.
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                int x = pos.getX() + dx;
                int z = pos.getZ() + dz;
                if (Math.abs(x - changed.getX()) > 2 || Math.abs(z - changed.getZ()) > 2)
                    columns.add(BlockPos.asLong(x, 0, z));
            }
            if (step.distance() == depth) continue;
            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockPos neighbor = pos.offset(direction);
                if (Math.abs(neighbor.getX() - changed.getX()) > depth
                        || Math.abs(neighbor.getZ() - changed.getZ()) > depth
                        || !checked.add(neighbor.asLong())) continue;
                if (world.getBlockState(neighbor).getBlock() instanceof SlabBlock)
                    pending.addLast(new Step(neighbor, step.distance() + 1));
            }
        }

        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (long packed : columns) {
            BlockPos column = BlockPos.fromLong(packed);
            for (int y = minY; y <= maxY; y++) {
                cursor.set(column.getX(), y, column.getZ());
                if (world.getBlockState(cursor).isAir()) continue;
                queue.enqueueBlockRegion(cursor.getX() - 1, y - 1, cursor.getZ() - 1,
                        cursor.getX() + 1, y + 1, cursor.getZ() + 1);
            }
        }
    }

    private record Step(BlockPos pos, int distance) { }
}
