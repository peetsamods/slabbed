package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Live-reported WYSIWYG violation (2026-07-03): "a lowered chest with lowered hoppers next to
 * it — the next horizontally chained hopper places upward into vanilla." Same disease as
 * {@link ConnectingBlockCantileverTest} one category over: {@code getYOffsetInner}'s
 * cantilever-adjacency lane structurally excluded {@code BlockEntityProvider} both as the SUBJECT
 * (top-level gate) and as a recognised NEIGHBOUR ({@code isAdjacentToLoweredSupport}'s filter), so
 * a hopper with air below it, beside a lowered chest/hopper, never inherited the lowered dy —
 * placed detached at vanilla height, same failure shape as the pane bug.
 *
 * <p>Bonus: {@code qualifiesForBlockEntityLoweredAnchor}'s sole criterion is
 * {@code SlabSupport.getYOffset(...) < 0}, so fixing the live check ALSO fixes the persisted
 * anchor for a chained block entity automatically — verified directly here.
 */
public final class BlockEntityCantileverTest {

    private static final double EPS = 1.0e-6;

    private static net.minecraft.block.BlockState vanillaBottomSlab() {
        return net.minecraft.block.Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    // THE LIVE REPRO: chest on a slab (lowered -0.5, anchored). Hopper #1 placed beside the
    // chest, air below, no slab of its own. Hopper #1 must inherit -0.5, not place at vanilla 0.0.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hopperBesideLoweredChestInheritsDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos chestPos = slab.up();
        w.setBlockState(slab, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(chestPos, net.minecraft.block.Blocks.CHEST.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, chestPos, w.getBlockState(chestPos));

        double chestDy = SlabSupport.getYOffset(w, chestPos, w.getBlockState(chestPos));
        ctx.assertTrue(Math.abs(chestDy + 0.5) <= EPS,
                "setup: chest on a slab, anchored, should render -0.5, got " + chestDy);

        BlockPos hopper1 = chestPos.east(); // air below, no slab of its own
        w.setBlockState(hopper1, net.minecraft.block.Blocks.HOPPER.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dy1 = SlabSupport.getYOffset(w, hopper1, w.getBlockState(hopper1));
        ctx.assertTrue(Math.abs(dy1 + 0.5) <= EPS,
                "a hopper placed beside a lowered chest (air below, no slab of its own) must inherit "
                        + "-0.5 (live bug: placed upward into vanilla); got " + dy1);
        ctx.complete();
    }

    // THE CHAIN: a second hopper beside the first (adjacency-lowered, not yet anchored at the
    // moment this reads) must also inherit -0.5 — the reported "NEXT horizontally chained hopper".
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void secondHopperChainedBeyondFirstInheritsDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos chestPos = slab.up();
        w.setBlockState(slab, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(chestPos, net.minecraft.block.Blocks.CHEST.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, chestPos, w.getBlockState(chestPos));

        BlockPos hopper1 = chestPos.east();
        w.setBlockState(hopper1, net.minecraft.block.Blocks.HOPPER.getDefaultState(), Block.NOTIFY_LISTENERS);
        // hopper1 is live-lowered by adjacency to the chest; anchor it as real placement would.
        SlabAnchorAttachment.addAnchor(w, hopper1, w.getBlockState(hopper1));

        BlockPos hopper2 = hopper1.east();
        w.setBlockState(hopper2, net.minecraft.block.Blocks.HOPPER.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dy2 = SlabSupport.getYOffset(w, hopper2, w.getBlockState(hopper2));
        ctx.assertTrue(Math.abs(dy2 + 0.5) <= EPS,
                "the SECOND chained hopper (beside the first, anchored, hopper) must also inherit "
                        + "-0.5; got " + dy2);
        ctx.complete();
    }

    // THE PERSISTED ANCHOR: qualifiesForBlockEntityLoweredAnchor's sole criterion is
    // getYOffset < 0, so fixing the live check must ALSO fix the recorded anchor — verify addAnchor
    // actually persists ANCHORED for a hopper placed via cantilever adjacency (not just direct/slab).
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cantileverHopperGetsPersistedAnchor(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos chestPos = slab.up();
        w.setBlockState(slab, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(chestPos, net.minecraft.block.Blocks.CHEST.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, chestPos, w.getBlockState(chestPos));

        BlockPos hopper1 = chestPos.east();
        w.setBlockState(hopper1, net.minecraft.block.Blocks.HOPPER.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, hopper1, w.getBlockState(hopper1));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, hopper1),
                "a cantilever-lowered hopper beside a lowered chest must get a PERSISTED anchor "
                        + "(qualifiesForBlockEntityLoweredAnchor delegates to the now-fixed getYOffset)");
        ctx.complete();
    }
}
