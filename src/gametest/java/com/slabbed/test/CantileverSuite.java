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
 * Cantilever suite — the same live bug class probed across three subject families: plain
 * full-block probes, CONNECTING blocks (fence/wall/pane, whose shape asks its neighbours), and
 * block-entity subjects. Merged 2026-08-07 from {@code CantileverProbeTest},
 * {@code ConnectingBlockCantileverTest}, {@code BlockEntityCantileverTest} — every test
 * preserved verbatim; the shared EPS + vanillaBottomSlab() helpers deduped to one copy.
 *
 * <p>Original class docs follow, per section.
 */
public final class CantileverSuite {

    // ═══ plain probes (CantileverProbeTest) ═══
    // /**
    //  * PROBE (not a pin): reproduce the live-reported "cantilevered slab placed 0.5 too high" and pinpoint
    //  * which config computes dy 0.0 instead of -0.5, and whether the reach-up deprecation is involved.
    //  * Each case logs its dy; the assertion documents the EXPECTED (-0.5) so a wrong value fails loudly.
    //  */

    private static final double EPS = 1.0e-6;

    private static BlockState vanillaBottomSlab() {
        return Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState topSlab() {
        return Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
    }

    // A lowered full-block support (stone on a bottom slab = -0.5), and a slab cantilevered off its
    // SIDE with air below. Expected: the cantilever slab reads -0.5 via isAdjacentSideSlabLowered.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bottomSlabCantileverOffLoweredFullBlock(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        w.setBlockState(base.down(), vanillaBottomSlab(), Block.NOTIFY_LISTENERS); // slab under support
        w.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS); // lowered -0.5 support
        BlockPos cant = base.east();
        w.setBlockState(cant, vanillaBottomSlab(), Block.NOTIFY_LISTENERS); // slab beside, AIR below
        double dy = SlabSupport.getYOffset(w, cant, w.getBlockState(cant));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "bottom slab cantilevered off a lowered full block should be -0.5; got " + dy);
        ctx.complete();
    }

    // Same, but a TOP slab (what the recorder showed).
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void topSlabCantileverOffLoweredFullBlock(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        w.setBlockState(base.down(), vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos cant = base.east();
        w.setBlockState(cant, topSlab(), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(w, cant, w.getBlockState(cant));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "TOP slab cantilevered off a lowered full block should be -0.5; got " + dy);
        ctx.complete();
    }

    // The freeze path: set up the FULL cantilever scene, THEN call freezeLoweredOnPlace (as real
    // placement would). If the geometry is right at freeze time, it must NOT freeze flat.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cantileverSlabDoesNotFreezeFlatWhenAlreadyLowered(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        w.setBlockState(base.down(), vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos cant = base.east();
        w.setBlockState(cant, topSlab(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.freezeLoweredOnPlace(w, cant, w.getBlockState(cant));
        boolean frozen = SlabAnchorAttachment.isFrozenFlat(w, cant);
        double dy = SlabSupport.getYOffset(w, cant, w.getBlockState(cant));
        ctx.assertTrue(!frozen && Math.abs(dy + 0.5) <= EPS,
                "cantilever slab beside an existing lowered block must NOT freeze flat; frozen=" + frozen + " dy=" + dy);
        ctx.complete();
    }

    // THE LIVE REPRO: a support full block lowered via ADJACENCY (air below, next to a lowered
    // neighbor — SlabSupport.java:941, "anchor=none but -0.5", matches the recorder). A slab
    // cantilevered off ITS side must be -0.5, but isLoweredSideSlabSource does not detect an
    // adjacency-lowered support → slab reads 0.0 → freezeLoweredOnPlace locks it flat.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cantileverOffAdjacencyLoweredFullBlock(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        w.setBlockState(base.down(), vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(base, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);   // dirt on slab -0.5
        com.slabbed.anchor.SlabAnchorAttachment.addAnchor(w, base, w.getBlockState(base)); // anchored (a lowered neighbor source)
        BlockPos support = base.east();                                                 // dirt, AIR below, beside anchored dirt
        w.setBlockState(support, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        double supDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        ctx.assertTrue(Math.abs(supDy + 0.5) <= EPS,
                "setup: adjacency-lowered dirt (air below, beside anchored dirt) should render -0.5, got " + supDy);
        BlockPos cant = support.east();                                                 // slab cantilevered off the adjacency-lowered dirt
        w.setBlockState(cant, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(w, cant, w.getBlockState(cant));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "slab cantilevered off an ADJACENCY-lowered full block must be -0.5 (live bug); got " + dy);
        ctx.complete();
    }

    // Chained cantilever: slab2 cantilevers off slab1 (which is itself a lowered cantilever). Both
    // should be -0.5. This is the row-of-slabs the recorder showed.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainedCantileverBothLowered(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        w.setBlockState(base.down(), vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos c1 = base.east();
        BlockPos c2 = base.east(2);
        w.setBlockState(c1, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(c2, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        double dy1 = SlabSupport.getYOffset(w, c1, w.getBlockState(c1));
        double dy2 = SlabSupport.getYOffset(w, c2, w.getBlockState(c2));
        ctx.assertTrue(Math.abs(dy1 + 0.5) <= EPS && Math.abs(dy2 + 0.5) <= EPS,
                "chained cantilever slabs should both be -0.5; got c1=" + dy1 + " c2=" + dy2);
        ctx.complete();
    }

    // ═══ connecting blocks (ConnectingBlockCantileverTest) ═══
    // /**
    //  * Live-reported WYSIWYG violation (2026-07-03): a NEW glass pane placed beside an EXISTING
    //  * lowered glass pane (air below, not on a slab itself) froze FLAT/detached (dy 0.0) instead of
    //  * inheriting the neighbour's lowered dy — the overlay showed {@code src=FROZEN-FLAT dy=0.000}
    //  * on a pane visibly beside a lowered one.
    //  *
    //  * <p>Root cause: {@code isAdjacentToLoweredFullBlock} (now {@code isAdjacentToLoweredSupport})
    //  * only ever recognised a SOLID FULL BLOCK as a cantilever-adjacency lowering source, and its
    //  * subject-side gate in {@code getYOffsetInner} required {@code state.isSolidBlock(...)} — so a
    //  * connecting block (fence/wall/pane/gate) never even reached the check, either as subject or as
    //  * a recognised neighbour. This mirrors the earlier slab-cantilever bug (`5282ebca`) one level up:
    //  * that fix widened {@code isLoweredSideSlabSource} for SLABS cantilevering off lowered supports;
    //  * this widens the underlying support-adjacency check itself so CONNECTING BLOCKS can cantilever
    //  * off (and serve as a source for) each other too.
    //  */

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

    // ═══ block entities (BlockEntityCantileverTest) ═══
    // /**
    //  * Live-reported WYSIWYG violation (2026-07-03): "a lowered chest with lowered hoppers next to
    //  * it — the next horizontally chained hopper places upward into vanilla." Same disease as
    //  * {@link ConnectingBlockCantileverTest} one category over: {@code getYOffsetInner}'s
    //  * cantilever-adjacency lane structurally excluded {@code BlockEntityProvider} both as the SUBJECT
    //  * (top-level gate) and as a recognised NEIGHBOUR ({@code isAdjacentToLoweredSupport}'s filter), so
    //  * a hopper with air below it, beside a lowered chest/hopper, never inherited the lowered dy —
    //  * placed detached at vanilla height, same failure shape as the pane bug.
    //  *
    //  * <p>Bonus: {@code qualifiesForBlockEntityLoweredAnchor}'s sole criterion is
    //  * {@code SlabSupport.getYOffset(...) < 0}, so fixing the live check ALSO fixes the persisted
    //  * anchor for a chained block entity automatically — verified directly here.
    //  */

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
