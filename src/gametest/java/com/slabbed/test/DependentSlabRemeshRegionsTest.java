package com.slabbed.test;

import com.slabbed.util.DependentRemeshQueue;
import com.slabbed.util.DependentSlabRemeshRegions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;

/** Bounds ordinary edits while retaining connected slabs and occupied dependent columns. */
public final class DependentSlabRemeshRegionsTest {
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void isolatedSlabKeepsTheSmallRefreshRegion(TestContext ctx) {
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(2, 200, 2));
        ctx.getWorld().setBlockState(pos, Blocks.SPRUCE_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);
        var regions = regions(ctx, pos);
        long cells = cells(regions);
        System.out.println("DEPENDENT_REGIONS isolated cells=" + cells + " ranges=" + regions.size());
        ctx.assertTrue(cells == 825, "isolated edit must retain the 5x33x5 region, got " + cells);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void connectedRunIncludesFarColumnsButNotUnrelatedSlabs(TestContext ctx) {
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(2, 240, 2));
        for (int x = 1; x <= 16; x++)
            ctx.getWorld().setBlockState(pos.east(x), Blocks.SPRUCE_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos dependent = pos.east(16).up(8);
        ctx.getWorld().setBlockState(dependent, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos unrelated = pos.south(12);
        ctx.getWorld().setBlockState(unrelated, Blocks.SPRUCE_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);
        var regions = regions(ctx, pos);
        ctx.assertTrue(contains(regions, pos.east(16)) && contains(regions, dependent),
                "the far connected slab and occupied vertical dependent must refresh");
        ctx.assertTrue(!contains(regions, unrelated), "an unrelated slab must not expand the refresh");
        ctx.assertTrue(cells(regions) < 8000, "a thin run must not expand to a whole cube");
        System.out.println("DEPENDENT_REGIONS run cells=" + cells(regions) + " ranges=" + regions.size());
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void wideFloorDoesNotRefreshEmptyVerticalSections(TestContext ctx) {
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(2, 280, 2));
        for (int x = -18; x <= 18; x++) for (int z = -18; z <= 18; z++)
            ctx.getWorld().setBlockState(pos.add(x, 0, z), Blocks.SPRUCE_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);
        var regions = regions(ctx, pos);
        ctx.assertTrue(contains(regions, pos.east(16)), "the full horizontal resolver reach must be covered");
        ctx.assertTrue(!contains(regions, pos.east(12).up(10)), "air above the remote floor must not be refreshed");
        ctx.assertTrue(cells(regions) < 8000, "a floor must not refresh a 33-cubed volume");
        System.out.println("DEPENDENT_REGIONS floor cells=" + cells(regions) + " ranges=" + regions.size());
        ctx.complete();
    }

    private static List<DependentRemeshQueue.BlockRegion> regions(TestContext ctx, BlockPos pos) {
        var queue = new DependentRemeshQueue(1000);
        DependentSlabRemeshRegions.enqueue(ctx.getWorld(), pos, 16, queue);
        var result = new ArrayList<DependentRemeshQueue.BlockRegion>();
        queue.drain((x, y, z, region) -> region.forEachRegion(result::add));
        return result;
    }

    private static long cells(List<DependentRemeshQueue.BlockRegion> regions) {
        return regions.stream().mapToLong(r -> (long) (r.maxX() - r.minX() + 1)
                * (r.maxY() - r.minY() + 1) * (r.maxZ() - r.minZ() + 1)).sum();
    }

    private static boolean contains(List<DependentRemeshQueue.BlockRegion> regions, BlockPos pos) {
        return regions.stream().anyMatch(r -> pos.getX() >= r.minX() && pos.getX() <= r.maxX()
                && pos.getY() >= r.minY() && pos.getY() <= r.maxY()
                && pos.getZ() >= r.minZ() && pos.getZ() <= r.maxZ());
    }
}
