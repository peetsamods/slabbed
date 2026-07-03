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
 * Live-reported WYSIWYG violation (2026-07-03): a NEW glass pane placed beside an EXISTING
 * lowered glass pane (air below, not on a slab itself) froze FLAT/detached (dy 0.0) instead of
 * inheriting the neighbour's lowered dy — the overlay showed {@code src=FROZEN-FLAT dy=0.000}
 * on a pane visibly beside a lowered one.
 *
 * <p>Root cause: {@code isAdjacentToLoweredFullBlock} (now {@code isAdjacentToLoweredSupport})
 * only ever recognised a SOLID FULL BLOCK as a cantilever-adjacency lowering source, and its
 * subject-side gate in {@code getYOffsetInner} required {@code state.isSolidBlock(...)} — so a
 * connecting block (fence/wall/pane/gate) never even reached the check, either as subject or as
 * a recognised neighbour. This mirrors the earlier slab-cantilever bug (`5282ebca`) one level up:
 * that fix widened {@code isLoweredSideSlabSource} for SLABS cantilevering off lowered supports;
 * this widens the underlying support-adjacency check itself so CONNECTING BLOCKS can cantilever
 * off (and serve as a source for) each other too.
 */
public final class ConnectingBlockCantileverTest {

    private static final double EPS = 1.0e-6;

    private static BlockState vanillaBottomSlab() {
        return Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    // THE LIVE REPRO: pane A on a slab (lowered -0.5, anchored). Pane B placed beside it, air
    // below B, B not itself on a slab. B must inherit A's -0.5, not freeze flat at 0.0.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void paneBesideLoweredPaneInheritsDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos paneA = slab.up();
        w.setBlockState(slab, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(paneA, Blocks.GLASS_PANE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, paneA, w.getBlockState(paneA));

        double dyA = SlabSupport.getYOffset(w, paneA, w.getBlockState(paneA));
        ctx.assertTrue(Math.abs(dyA + 0.5) <= EPS,
                "setup: pane A on a slab, anchored, should render -0.5, got " + dyA);

        BlockPos paneB = paneA.east(); // air below, no slab of its own
        w.setBlockState(paneB, Blocks.GLASS_PANE.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dyB = SlabSupport.getYOffset(w, paneB, w.getBlockState(paneB));
        ctx.assertTrue(Math.abs(dyB + 0.5) <= EPS,
                "a pane placed beside a lowered pane (air below, no slab of its own) must inherit "
                        + "-0.5 (live WYSIWYG bug: froze flat/detached); got " + dyB);
        ctx.complete();
    }

    // Same, for fences (a different connecting family, same code path).
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceBesideLoweredFenceInheritsDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos fenceA = slab.up();
        w.setBlockState(slab, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(fenceA, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, fenceA, w.getBlockState(fenceA));

        BlockPos fenceB = fenceA.east();
        w.setBlockState(fenceB, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dyB = SlabSupport.getYOffset(w, fenceB, w.getBlockState(fenceB));
        ctx.assertTrue(Math.abs(dyB + 0.5) <= EPS,
                "a fence placed beside a lowered fence must inherit -0.5; got " + dyB);
        ctx.complete();
    }

    // A solid full block (stone) beside a lowered pane must ALSO cantilever-inherit — the
    // widened neighbor-acceptance is symmetric (full block <-> connecting block, either as
    // subject or as source), not pane-specific.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockBesideLoweredPaneInheritsDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos pane = slab.up();
        w.setBlockState(slab, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(pane, Blocks.GLASS_PANE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, pane, w.getBlockState(pane));

        BlockPos stone = pane.east();
        w.setBlockState(stone, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(w, stone, w.getBlockState(stone));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "a solid full block placed beside a lowered pane must inherit -0.5; got " + dy);
        ctx.complete();
    }

    // REGRESSION GUARD: the "gap-fill from above" lane stays solid-only — a pane directly below
    // an anchored floating full block is a DIFFERENT (unverified) case and must NOT silently
    // start inheriting from this change. Documents current behavior; not a claim it is correct.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void paneBelowAnchoredBlockGapFillLaneUnaffected(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pane = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 4, 3);
        BlockPos above = pane.up();
        w.setBlockState(above, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, above, w.getBlockState(above));
        w.setBlockState(pane, Blocks.GLASS_PANE.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(w, pane, w.getBlockState(pane));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "gap-fill-from-above stays solid-subject-only (unchanged) for a pane; got " + dy);
        ctx.complete();
    }
}
