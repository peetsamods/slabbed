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
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * State-change jitter (Maintainer's grass-tower report): a grass block anchored/lowered on a slab
 * converts to DIRT in place (a block-KIND change firing onStateReplaced). The anchor must
 * SURVIVE an in-place transform to another anchor-eligible block, or the dirt un-lowers and
 * jitters/merges/jumps. A genuine break (-> air) must still clear the anchor.
 */
public final class StateChangeAnchorTest {

    private static BlockState bottomSlab() {
        return Blocks.POLISHED_TUFF_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static void onPlaced(ServerWorld w, BlockPos pos, BlockState state) {
        SlabAnchorAttachment.addAnchor(w, pos, state);
        SlabAnchorAttachment.freezeLoweredOnPlace(w, pos, state);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void inPlaceTransformKeepsAnchorAndDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos block = slab.up();
        w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(block, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, block, w.getBlockState(block));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, block),
                "precondition: grass block on a bottom slab is anchored");
        double before = SlabSupport.getYOffset(w, block, w.getBlockState(block));

        // Grass -> dirt: a block-KIND change at the SAME position (the tower conversion).
        w.setBlockState(block, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);

        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, block),
                "the anchor MUST survive an in-place grass->dirt transform (WYSIWYG, no jitter)");
        double after = SlabSupport.getYOffset(w, block, w.getBlockState(block));
        ctx.assertTrue(Math.abs(after - before) < 1.0e-6,
                "dy must not jump on the in-place transform: before=" + before + " after=" + after);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void genuineBreakToAirClearsTheAnchor(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos block = slab.up();
        w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(block, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, block, w.getBlockState(block));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, block), "precondition: stone anchored");

        // A real break MUST clear the anchor so a fresh placement re-evaluates. Use breakBlock
        // (the player-break path that actually fires onStateReplaced), not a raw setBlockState.
        w.breakBlock(block, false);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, block),
                "breaking the block MUST clear its anchor");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void replaceWithNonAnchorBlockClearsTheAnchor(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos block = slab.up();
        w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(block, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, block, w.getBlockState(block));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, block), "precondition: stone anchored");

        // Break, then place a non-ordinary block (a slab) — the anchor must not linger stale.
        w.breakBlock(block, false);
        w.setBlockState(block, bottomSlab(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, block),
                "after a break the anchor must be gone (no stale anchor under the new slab)");
        ctx.complete();
    }
}
