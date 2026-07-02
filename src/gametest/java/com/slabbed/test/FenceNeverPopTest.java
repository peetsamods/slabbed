package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * WYSIWYG / never-pop for fences, walls, panes and gates. Recorder session bb138275 caught
 * these popping between dy -0.5 and 0.0 as neighbours changed — because they are not solid
 * blocks, {@code isOrdinaryAnchorCandidate} excluded them, so they were never anchored or
 * frozen and their dy stayed live (geometric). A placed connecting block must stay put.
 */
public final class FenceNeverPopTest {

    private static BlockState bottomSlab() {
        return Blocks.POLISHED_TUFF_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /** Simulate what BlockOnPlacedAnchorMixin.onPlaced does, server-side. */
    private static void onPlaced(ServerWorld w, BlockPos pos, BlockState state) {
        SlabAnchorAttachment.addAnchor(w, pos, state);
        SlabAnchorAttachment.freezeLoweredOnPlace(w, pos, state);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fencePlacedLoweredOnSlabIsHeightLocked(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos fence = slab.up();
        w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, fence, w.getBlockState(fence));

        double dyPlaced = SlabSupport.getYOffset(w, fence, w.getBlockState(fence));
        ctx.assertTrue(Math.abs(dyPlaced + 0.5) < 1.0e-6, "a fence on a bottom slab is placed lowered -0.5, got " + dyPlaced);
        ctx.assertTrue(
                SlabAnchorAttachment.isAnchored(w, fence) || SlabAnchorAttachment.isFrozenFlat(w, fence),
                "a fence placed lowered MUST be height-locked (anchored or frozen), else it pops (WYSIWYG)");

        // The actual pop scenario: remove the slab below. A geometric fence would recompute to 0.0
        // and pop UP; a height-locked one stays at -0.5.
        w.setBlockState(slab, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dyAfter = SlabSupport.getYOffset(w, fence, w.getBlockState(fence));
        ctx.assertTrue(Math.abs(dyAfter + 0.5) < 1.0e-6,
                "the placed fence must STAY at -0.5 after its support changes, got " + dyAfter + " (it popped)");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void wallPlacedLoweredOnSlabIsHeightLocked(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos wall = slab.up();
        w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(wall, Blocks.COBBLESTONE_WALL.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, wall, w.getBlockState(wall));
        ctx.assertTrue(
                SlabAnchorAttachment.isAnchored(w, wall) || SlabAnchorAttachment.isFrozenFlat(w, wall),
                "a wall placed lowered MUST be height-locked");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchoredFenceSurvivesAConnectionStateUpdate(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos fence = slab.up();
        w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, fence, w.getBlockState(fence));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, fence) || SlabAnchorAttachment.isFrozenFlat(w, fence),
                "precondition: fence is height-locked after placement");

        // Mutate a connection property (what a neighbour update does) — same block kind.
        BlockState connected = w.getBlockState(fence).with(Properties.NORTH, true);
        w.setBlockState(fence, connected, Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, fence) || SlabAnchorAttachment.isFrozenFlat(w, fence),
                "the height-lock MUST survive a fence connection-state update (property-only change)");
        double dy = SlabSupport.getYOffset(w, fence, w.getBlockState(fence));
        ctx.assertTrue(Math.abs(dy + 0.5) < 1.0e-6, "still -0.5 after the connection update, got " + dy);
        ctx.complete();
    }
}
