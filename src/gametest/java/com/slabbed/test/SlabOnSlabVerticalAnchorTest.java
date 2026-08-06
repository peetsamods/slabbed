package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Live-reported bug (2026-07-04 recorder session, "I got a pop upon breaking at the end"): a
 * vertical stack of birch slabs — a TOP-type slab resting directly on another lowered/anchored
 * TOP-type slab below it — popped from {@code vdy=-0.500} to {@code vdy=0.000} the instant the
 * lower slab was broken, even though the upper slab itself had never moved or been touched.
 * Recorder confirmed the upper slab was {@code anchor=none} for its entire lifetime.
 *
 * <p>Root cause: {@code getYOffsetInner}'s slab branch LIVE-derives -0.5 for a slab resting on a
 * lowered support below via {@code hasLoweredNonSlabTopSupport}/{@code hasLoweredSlabSupport}
 * (the latter named {@code hasLoweredTopLikeSlabSupport} at the time of this fix) — but the
 * persistent anchor qualifier for slabs,
 * {@code qualifiesForLoweredSideSlabAnchor}, is backed solely by {@code isLoweredSideSlabVisual},
 * which only recognised {@code isAnchored(self) || isAdjacentSideSlabLowered} (HORIZONTAL
 * neighbours). It never recognised the identical VERTICAL relationship the live read already
 * uses. So a slab resting on a lowered support rendered correctly at placement time purely via
 * the live derivation, but that derivation was never persisted — breaking the support removed
 * the only path to -0.5, and with no anchor to fall back on it popped flush.
 *
 * <p>Fix: {@code isLoweredSideSlabVisual} now also recognises the vertical support relationship,
 * matching {@code isVerticallyLoweredSlabSource} (the same check {@code isLoweredSideSlabSource}
 * already uses to decide whether THIS slab is a valid lowering source for horizontal neighbours),
 * so a slab resting on a lowered support anchors at placement just like every other category.
 */
public final class SlabOnSlabVerticalAnchorTest {

    private static final double EPS = 1.0e-6;

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void breakingLowerSupportSlabDoesNotPopSlabRestingOnTop(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos vanillaBottomSlabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos dirtPos = vanillaBottomSlabPos.up();       // direct-anchor lowering source
        BlockPos supportSlabPos = dirtPos.east();            // TOP slab, lowered+anchored via HORIZONTAL adjacency (pre-existing lane)
        BlockPos upperSlabPos = supportSlabPos.up();         // TOP slab resting VERTICALLY on supportSlabPos (the new case)

        w.setBlockState(vanillaBottomSlabPos,
                Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);

        w.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtPos, w.getBlockState(dirtPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, dirtPos), "setup: dirt must anchor on the bottom slab");
        double dirtDy = SlabSupport.getYOffset(w, dirtPos, w.getBlockState(dirtPos));
        ctx.assertTrue(Math.abs(dirtDy + 0.5) <= EPS, "setup: anchored dirt should render -0.5, got " + dirtDy);

        w.setBlockState(supportSlabPos, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, supportSlabPos, w.getBlockState(supportSlabPos));
        double supportDy = SlabSupport.getYOffset(w, supportSlabPos, w.getBlockState(supportSlabPos));
        ctx.assertTrue(Math.abs(supportDy + 0.5) <= EPS,
                "setup: support slab beside anchored dirt should render -0.5, got " + supportDy);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, supportSlabPos),
                "setup: support slab must anchor via the pre-existing horizontal-adjacency lane");

        w.setBlockState(upperSlabPos, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        // Simulate placement: Block.onPlaced -> SlabAnchorAttachment.addAnchor fires for every
        // real player placement, exactly like this direct call.
        SlabAnchorAttachment.addAnchor(w, upperSlabPos, w.getBlockState(upperSlabPos));
        double upperDyBefore = SlabSupport.getYOffset(w, upperSlabPos, w.getBlockState(upperSlabPos));
        ctx.assertTrue(Math.abs(upperDyBefore + 0.5) <= EPS,
                "setup: slab resting on the lowered support should render -0.5, got " + upperDyBefore);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, upperSlabPos),
                "THE FIX: a slab resting on a lowered/anchored support must itself anchor at "
                        + "placement time, or breaking the support later pops it back to flush "
                        + "(live-reported 'pop upon breaking at the end')");

        // Break the support slab — the upper slab must NOT pop.
        w.setBlockState(supportSlabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        double upperDyAfter = SlabSupport.getYOffset(w, upperSlabPos, w.getBlockState(upperSlabPos));
        ctx.assertTrue(Math.abs(upperDyAfter + 0.5) <= EPS,
                "never-pop violation: slab popped from -0.5 to " + upperDyAfter
                        + " after its support was broken, even though it was never re-placed");
        ctx.complete();
    }

    // REGRESSION GUARD: a flat (never-lowered) slab resting on another flat slab must never gain
    // a spurious anchor.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatSlabOnFlatSlabNeverAnchors(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos lower = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 4);
        BlockPos upper = lower.up();
        w.setBlockState(lower, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, lower, w.getBlockState(lower));
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, lower), "setup: flat slab must not anchor");

        w.setBlockState(upper, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, upper, w.getBlockState(upper));
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, upper),
                "regression: a slab resting on a FLAT (non-lowered) slab must not spuriously anchor");
        ctx.complete();
    }

    // REVERSED 2026-08-06 — this cell used to assert that a slab on a PLAIN (non-sunk) BOTTOM-type
    // support must NOT anchor, mirroring hasLoweredSlabSupport's old "qualifies only if the support
    // is ACTUALLY SUNK" condition. It now asserts the opposite. This is NOT a new product decision;
    // it is FORCED by two things already settled:
    //   (1) Maintainer's ruling of 2026-08-06 (exclusion #13, WYSIWYG law) — a slab resting on a plain
    //       bottom slab LOWERS, because that support's top face is half a block below the grid
    //       whether or not the support is itself sunk. See
    //       SlabOnLoweredBottomSlabTest#slabOnFlatBottomSlabSeatsOnItsTopFace.
    //   (2) the standing NEVER-POP law — a slab that RENDERS at -0.5 but records no anchor pops
    //       flush the instant its support is broken, even though it was never re-placed. Never-pop
    //       forbids exactly that.
    // Rendering and persistence therefore have to share the predicate, which is the whole reason
    // the shared-predicate law exists at this call site: the render lane (getYOffsetInner's slab
    // branch) and the persistence qualifier (isVerticallyLoweredSlabSource ->
    // isLoweredSideSlabVisual -> qualifiesForLoweredSideSlabAnchor) both read hasLoweredSlabSupport.
    // The never-pop consequence is PROVEN, not merely asserted here — see the cell below.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnBottomTypeSupportAnchorsVertically(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 5);
        BlockPos upper = support.up();
        w.setBlockState(support, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(upper, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, upper, w.getBlockState(upper));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, upper),
                "a slab resting on a PLAIN BOTTOM-type support must anchor vertically: under Maintainer's "
                        + "2026-08-06 ruling it renders lowered, and never-pop then requires the "
                        + "anchor that keeps it there when the support is broken");
        ctx.complete();
    }

    // THE PROOF for the flip above. Places a slab on a PLAIN (never-sunk) bottom slab through the
    // real placement sequence, asserts it renders lowered, then BREAKS the support and asserts it
    // KEEPS that height. Without the anchor the live derivation is the only path to -0.5, so
    // removing the support would drop this slab straight back to 0.0 — the exact "pop upon breaking
    // at the end" this class was opened for, reproduced on the plain-support shape the ruling just
    // brought into scope. Every premise is hard-asserted so the cell cannot pass vacuously.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void breakingPlainBottomSlabSupportDoesNotPopTheSlabAboveIt(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 1, 7);
        BlockPos support = ground.up();
        BlockPos subject = support.up();

        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(support, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        ctx.assertTrue(Math.abs(supportDy) <= EPS,
                "fixture: the support must be a PLAIN (never-sunk) bottom slab rendering 0.0, got "
                        + supportDy);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, support),
                "fixture: the plain support must carry no anchor of its own");

        // The real placement sequence: Block.onPlaced -> SlabAnchorAttachment.addAnchor.
        w.setBlockState(subject, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, subject, w.getBlockState(subject));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, subject),
                "premise: the slab must record an anchor at placement — that anchor IS the never-pop "
                        + "guarantee this cell exists to prove");
        double before = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(before + 0.5) <= EPS,
                "premise: a slab on a plain bottom slab must render -0.5 (Maintainer's ruling 2026-08-06), "
                        + "got " + before);

        // Break the support out from under it.
        w.setBlockState(support, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        double after = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(after + 0.5) <= EPS,
                "NEVER-POP: the slab popped from -0.5 to " + after + " after its plain bottom-slab "
                        + "support was broken, even though it was never re-placed. This is exactly "
                        + "why the anchor lane had to widen alongside the render lane");
        ctx.complete();
    }

    // Same-session recorder finding (actionId a14/a15, counter "loweredDyLostAfterDoubleTarget"):
    // a Terrain-Slabs-owned slab placed vertically on top of an ANCHORED Terrain-Slabs DOUBLE
    // slab rendered vanilla dy=0.0 instead of -0.5. TS-owned slabs are gated by
    // CompatHooks.shouldSkipOffset at the TOP of getYOffset — the ecdf8931 fix already lets an
    // ANCHORED TS slab bypass that gate, so this is the SAME missing-vertical-anchor-lane bug as
    // above, just requiring the anchor to exist before the skip-offset gate opens at all. Proves
    // (rather than assumes) that the isLoweredSideSlabVisual fix also closes this finding.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabsOwnedSlabRestingOnAnchoredDoubleSupportAnchorsAndRenders(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos vanillaBottomSlabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 6);
        BlockPos dirtPos = vanillaBottomSlabPos.up();
        BlockPos supportPos = dirtPos.east();   // TS DOUBLE slab, lowered+anchored via horizontal adjacency to dirt
        BlockPos upperPos = supportPos.up();

        w.setBlockState(vanillaBottomSlabPos,
                Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtPos, w.getBlockState(dirtPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, dirtPos), "setup: dirt must anchor on the bottom slab");

        w.setBlockState(supportPos,
                TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, supportPos, w.getBlockState(supportPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, supportPos),
                "setup: support TS double slab beside anchored dirt must anchor");

        w.setBlockState(upperPos,
                TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, upperPos, w.getBlockState(upperPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, upperPos),
                "a TS slab resting on an anchored TS DOUBLE support must itself anchor "
                        + "(recorder actionId a14: 'lowered double-slab target produced vanilla-dy placed slab')");
        double upperDy = SlabSupport.getYOffset(w, upperPos, w.getBlockState(upperPos));
        ctx.assertTrue(Math.abs(upperDy + 0.5) <= EPS,
                "TS slab resting on a lowered TS support should render -0.5, got " + upperDy);
        ctx.complete();
    }
}
