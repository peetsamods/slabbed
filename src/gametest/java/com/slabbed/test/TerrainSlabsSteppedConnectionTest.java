package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Headless tests for the Terrain Slabs <b>CONNECTING-BLOCK</b> lane.
 *
 * <p>A fence/wall/pane lowered onto a Terrain Slabs slab and a same-Y neighbour at full height
 * sit at different visual heights, so {@link SlabSupport#isSteppedConnectingNeighbor} must report
 * them as a stepped pair (the connector arm must NOT be drawn across the height step). Two
 * neighbours at the same height must NOT be stepped (control).
 *
 * <p>Runs against the REAL Countered's Terrain Slabs mod ({@code terrainslabs}), loaded into the
 * headless {@code runGameTest} server via {@code modLocalRuntime}.
 */
public final class TerrainSlabsSteppedConnectionTest {

    private static Block tsSlabBlock() {
        return Registries.BLOCK.get(Identifier.of("terrainslabs", "dirt_slab"));
    }

    private static BlockState tsBottomSlab() {
        return tsSlabBlock().getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    // A fence lowered onto a Terrain Slabs slab and a ground fence at the same Y are a stepped
    // pair (connection broken). Two ground fences at the same height are NOT (control).
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceConnectionBreaksAcrossTsStep(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos groundBase = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(groundBase, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos groundFence = groundBase.up();
        w.setBlockState(groundFence, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

        // Adjacent column: a TS slab + a fence lowered onto it (same Y as the ground fence).
        BlockPos tsBase = groundBase.east();
        w.setBlockState(tsBase, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        BlockPos loweredFence = tsBase.up();
        w.setBlockState(loweredFence, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

        ctx.assertTrue(
                SlabSupport.isSteppedConnectingNeighbor(w, groundFence, w.getBlockState(groundFence),
                        loweredFence, w.getBlockState(loweredFence)),
                "a ground fence and a fence lowered onto a TS slab (same Y) must be a stepped pair");

        // Control: a second ground fence at the same height must NOT be stepped.
        BlockPos groundBase2 = groundBase.west();
        w.setBlockState(groundBase2, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos groundFence2 = groundBase2.up();
        w.setBlockState(groundFence2, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(
                !SlabSupport.isSteppedConnectingNeighbor(w, groundFence, w.getBlockState(groundFence),
                        groundFence2, w.getBlockState(groundFence2)),
                "two ground fences at the same height must NOT be a stepped pair");
        ctx.complete();
    }

    // Same stepped-pair contract for glass panes (the connecting-block family covers panes too).
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void paneConnectionBreaksAcrossTsStep(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos groundBase = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(groundBase, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos groundPane = groundBase.up();
        w.setBlockState(groundPane, Blocks.GLASS_PANE.getDefaultState(), Block.NOTIFY_LISTENERS);

        BlockPos tsBase = groundBase.east();
        w.setBlockState(tsBase, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        BlockPos loweredPane = tsBase.up();
        w.setBlockState(loweredPane, Blocks.GLASS_PANE.getDefaultState(), Block.NOTIFY_LISTENERS);

        ctx.assertTrue(
                SlabSupport.isSteppedConnectingNeighbor(w, groundPane, w.getBlockState(groundPane),
                        loweredPane, w.getBlockState(loweredPane)),
                "a ground pane and a pane lowered onto a TS slab (same Y) must be a stepped pair");
        ctx.complete();
    }
}
