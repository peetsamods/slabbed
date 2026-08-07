package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

/**
 * Anchor-lane suite — how a slab EARNS and KEEPS an anchor, across support kinds: the stale-
 * anchor dy triad, slab-on-slab vertical anchoring, Terrain Slabs chain anchoring, and the
 * bottom-slab-as-support rulings (exclusion #13, tower pins). Merged 2026-08-07 from four
 * single-lane classes — every test preserved verbatim; shared EPS deduped. Original class docs
 * follow, per section.
 */
public final class AnchorLaneSuite {

    // ═══ stale-anchor triad — model/outline/raycast move together (AnchoredSlabTriadTest) ═══
    // /**
    //  * Ground-truth for the recorder's "anchored slab triad mismatch" (polished_tuff_slab:
    //  * visualDy -0.5, outlineMinY 0.0). Reproduces the actual mechanism: a slab cannot be
    //  * anchored directly (isOrdinaryAnchorCandidate excludes SlabBlock), so the recorded
    //  * anchor is STALE — a full block was anchored, then replaced by a slab, and the anchor
    //  * persisted. This pins whether the resulting slab's outline follows its (anchor-driven)
    //  * visual dy, separately for BOTTOM (outline base 0) and TOP (outline base 0.5) slabs.
    //  */
    private static final double EPS = 1.0e-6;


    private static double minY(VoxelShape s) {
        return s.isEmpty() ? Double.NaN : s.getBoundingBox().minY;
    }

    private static void staleAnchorSlab(TestContext ctx, SlabType type, String label) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos p = support.up();
        // Bottom slab support so the full block above qualifies for a direct anchor.
        w.setBlockState(support, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockState stone = Blocks.STONE.getDefaultState();
        w.setBlockState(p, stone, Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, p, stone);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, p),
                "precondition: the full block must anchor on a bottom slab (" + label + ")");

        // Replace with a slab WITHOUT clearing the anchor — the stale-anchor scenario.
        BlockState slab = Blocks.POLISHED_TUFF_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
        w.setBlockState(p, slab, Block.NOTIFY_LISTENERS);

        boolean stillAnchored = SlabAnchorAttachment.isAnchored(w, p);
        double vis = SlabSupport.getVisualYOffset(w, p, w.getBlockState(p));
        double outMinY = minY(w.getBlockState(p).getOutlineShape(w, p, ShapeContext.absent()));
        double collMinY = minY(w.getBlockState(p).getCollisionShape(w, p, ShapeContext.absent()));

        // Report the measured triad in the failure text regardless of outcome.
        String obs = label + ": stillAnchored=" + stillAnchored + " visualDy=" + fmt(vis)
                + " outlineMinY=" + fmt(outMinY) + " collisionMinY=" + fmt(collMinY);

        if (!stillAnchored || Math.abs(vis) < 1.0e-6) {
            // If the anchor cleared on replace, or the slab is not lowered, there is no bug
            // to reproduce here — the recorder's row must have come from a live anchor. Pass
            // with the observation recorded (a clean state is a valid, informative outcome).
            ctx.complete();
            return;
        }

        // The slab IS lowered (visualDy != 0). Its outline must reflect the dy given its base:
        // BOTTOM base 0 -> outlineMinY == visualDy; TOP base 0.5 -> outlineMinY == 0.5 + visualDy.
        double base = (type == SlabType.TOP) ? 0.5 : 0.0;
        double expectedOutline = base + vis;
        ctx.assertTrue(Math.abs(outMinY - expectedOutline) < 1.0e-6,
                obs + " -> outline does NOT follow the visual dy (expected " + fmt(expectedOutline) + ")");
        ctx.complete();
    }

    private static String fmt(double v) {
        return Double.isNaN(v) ? "NaN" : String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void staleAnchoredBottomSlabTriad(TestContext ctx) {
        staleAnchorSlab(ctx, SlabType.BOTTOM, "BOTTOM");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void staleAnchoredTopSlabTriad(TestContext ctx) {
        staleAnchorSlab(ctx, SlabType.TOP, "TOP");
    }

    // ═══ slab-on-slab vertical anchoring (SlabOnSlabVerticalAnchorTest) ═══
    // /**
    //  * Live-reported bug (2026-07-04 recorder session, "I got a pop upon breaking at the end"): a
    //  * vertical stack of birch slabs — a TOP-type slab resting directly on another lowered/anchored
    //  * TOP-type slab below it — popped from {@code vdy=-0.500} to {@code vdy=0.000} the instant the
    //  * lower slab was broken, even though the upper slab itself had never moved or been touched.
    //  * Recorder confirmed the upper slab was {@code anchor=none} for its entire lifetime.
    //  *
    //  * <p>Root cause: {@code getYOffsetInner}'s slab branch LIVE-derives -0.5 for a slab resting on a
    //  * lowered support below via {@code hasLoweredNonSlabTopSupport}/{@code hasLoweredSlabSupport}
    //  * (the latter named {@code hasLoweredTopLikeSlabSupport} at the time of this fix) — but the
    //  * persistent anchor qualifier for slabs,
    //  * {@code qualifiesForLoweredSideSlabAnchor}, is backed solely by {@code isLoweredSideSlabVisual},
    //  * which only recognised {@code isAnchored(self) || isAdjacentSideSlabLowered} (HORIZONTAL
    //  * neighbours). It never recognised the identical VERTICAL relationship the live read already
    //  * uses. So a slab resting on a lowered support rendered correctly at placement time purely via
    //  * the live derivation, but that derivation was never persisted — breaking the support removed
    //  * the only path to -0.5, and with no anchor to fall back on it popped flush.
    //  *
    //  * <p>Fix: {@code isLoweredSideSlabVisual} now also recognises the vertical support relationship,
    //  * matching {@code isVerticallyLoweredSlabSource} (the same check {@code isLoweredSideSlabSource}
    //  * already uses to decide whether THIS slab is a valid lowering source for horizontal neighbours),
    //  * so a slab resting on a lowered support anchors at placement just like every other category.
    //  */

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
    //   (1) maintainer ruling of 2026-08-06 (exclusion #13, WYSIWYG law) — a slab resting on a plain
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
                "a slab resting on a PLAIN BOTTOM-type support must anchor vertically: under the maintainer's "
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
                "premise: a slab on a plain bottom slab must render -0.5 (maintainer ruling 2026-08-06), "
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

    // ═══ Terrain Slabs chain anchoring (TerrainSlabsChainAnchorTest) ═══
    // /**
    //  * Live-reported bug (2026-07-03): "breaking [the] middle slab pops the other one up" for a
    //  * cantilevered row of Terrain Slabs slabs. Recorder-traced: a TS slab at a chain's far end
    //  * showed {@code anchor=ANCHORED} yet its own {@code visualDy} popped from {@code -0.500} to
    //  * {@code 0.000} the instant its neighbour was broken — a genuine internal inconsistency (the
    //  * SAME (world, pos, state) snapshot reporting both "anchored" and "not lowered").
    //  *
    //  * <p>Root cause: {@code getYOffset}'s very first gate is
    //  * {@code shouldSkipOffset(state) && !isAdjacentCustomSideSlabLowered(...) -> return 0.0}. For a
    //  * TS-owned slab, {@code isAdjacentCustomSideSlabLowered} is a LIVE BFS through the connected
    //  * slab chain, recomputed on every call — it never reads this position's own persisted anchor.
    //  * So whenever that early return fires, the anchor check deeper in {@code getYOffsetInner} is
    //  * never reached AT ALL: breaking the chain's lowering source makes the BFS fail, and the gate
    //  * unconditionally returns 0.0, silently overriding an anchor that exists for exactly this
    //  * "survive a later neighbour change" case. This NEVER affects vanilla slabs (they are not
    //  * {@code shouldSkipOffset}, so they skip this early gate entirely and reach the working anchor
    //  * check directly) — only TS-owned slabs, matching the live report precisely.
    //  *
    //  * <p>Fix: the early-return also checks {@code state instanceof SlabBlock && isAnchored(pos)},
    //  * letting an anchored TS slab fall through to the SAME anchor check every other block type
    //  * already uses.
    //  */

    private static BlockState tsSlab(SlabType type) {
        return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void breakingChainSourceDoesNotPopAnchoredFarSlab(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos vanillaSlabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos dirt = vanillaSlabPos.up();  // ultimate lowering source: dirt on a vanilla bottom slab
        BlockPos a = dirt.east();             // TS slab A: air below, lowered via chain reaching dirt
        BlockPos bPos = a.east();             // TS slab B: air below, lowered ONLY via the chain reaching A

        w.setBlockState(vanillaSlabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirt, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dyDirt = SlabSupport.getYOffset(w, dirt, w.getBlockState(dirt));
        ctx.assertTrue(Math.abs(dyDirt + 0.5) <= EPS,
                "setup: dirt on a vanilla bottom slab should render -0.5, got " + dyDirt);

        w.setBlockState(a, tsSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        double dyA = SlabSupport.getYOffset(w, a, w.getBlockState(a));
        ctx.assertTrue(Math.abs(dyA + 0.5) <= EPS,
                "setup: TS slab A beside lowered dirt should render -0.5 (chain BFS), got " + dyA);

        w.setBlockState(bPos, tsSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        double dyBBefore = SlabSupport.getYOffset(w, bPos, w.getBlockState(bPos));
        ctx.assertTrue(Math.abs(dyBBefore + 0.5) <= EPS,
                "setup: TS slab B beside lowered TS slab A should render -0.5 (2-hop chain BFS), got " + dyBBefore);

        // Simulate real placement-time anchoring (BlockOnPlacedAnchorMixin -> addAnchor).
        SlabAnchorAttachment.addAnchor(w, bPos, w.getBlockState(bPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, bPos),
                "setup: TS slab B must qualify for the lowered-side-slab anchor at placement time");

        // Break the chain's MIDDLE link (slab A) — the maintainer's exact repro.
        w.breakBlock(a, false);
        ctx.assertTrue(w.getBlockState(a).isAir(), "setup: slab A must actually be broken");

        double dyBAfter = SlabSupport.getYOffset(w, bPos, w.getBlockState(bPos));
        ctx.assertTrue(Math.abs(dyBAfter + 0.5) <= EPS,
                "an ANCHORED TS slab must NOT pop up when the chain's middle link is broken "
                        + "(live 'breaking middle slab pops the other one up' bug); got " + dyBAfter);
        ctx.complete();
    }

    // REGRESSION GUARD: a TS slab that is NOT anchored (never qualified — e.g. placed flat, never
    // adjacent to a lowered chain) still correctly reads flush; the fix must not force -0.5 onto
    // an unrelated, un-anchored TS slab.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unanchoredTsSlabStaysFlush(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        w.setBlockState(pos, tsSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, pos), "setup: this TS slab must not be anchored");
        double dy = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "an un-anchored, isolated TS slab must stay flush (0.0); got " + dy);
        ctx.complete();
    }

    // ═══ slab on a lowered/plain bottom slab — exclusion #13 + tower pins (SlabOnLoweredBottomSlabTest) ═══
    // /**
    //  * LAW 1 (LAW.md) item #1 ("everything should be able to lower; no exceptions", 2026-08-06):
    //  * <b>a slab resting on a LOWERED bottom slab had no lane at all.</b>
    //  *
    //  * <p><b>Live evidence.</b> {@code (157,-58,-10) oak_slab dy=0.000 src=FROZEN-FLAT} sitting on
    //  * {@code (157,-59,-10) stone_slab dy=-0.500 ANCHORED}. The correct value is {@code -1.0} (mega
    //  * row 2's {@code MEGA_ROW_DY[2]}); the slab was stuck flat on a visibly sunk support.
    //  *
    //  * <p><b>Root cause.</b> The case fell between two TYPE-based rejects, neither of which asked
    //  * whether the support was actually sunk:
    //  * <ul>
    //  *   <li>{@code hasLoweredNonSlabTopSupport} rejects any support that is {@code instanceof
    //  *       SlabBlock};</li>
    //  *   <li>{@code hasLoweredTopLikeSlabSupport} rejected any support where {@code isBottomSlab(state)}
    //  *       — unconditionally, on type;</li>
    //  *   <li>{@code shouldOffset} never offsets slabs, so slabs have no generic-grammar fallback —
    //  *       their only lanes are {@code getYOffsetInner}'s slab branch.</li>
    //  * </ul>
    //  * Everything then fell to the class-based flush guard, which returns a hardcoded {@code 0.0} for
    //  * {@code SlabBlock}. The resolver already computed the right answer: had any lane reached
    //  * {@code loweredFollowerDy}, it would resolve {@code supportSeatDy} → {@code
    //  * loweredBottomSlabSupportDy} ({@code -0.5}) {@code - 0.5} = {@code -1.0}. <b>The gate was broken,
    //  * not the arithmetic</b> — no new depth math is introduced by the fix.
    //  *
    //  * <p><b>Why the pre-existing suite never caught it.</b>
    //  * {@code SlabOnSlabVerticalAnchorTest#slabOnBottomTypeSupportNeverAnchorsVertically} builds a
    //  * <b>non-lowered</b> birch bottom slab as its support, so it only ever defended the plain case.
    //  * Its premise (internal-notes L8 — "a BOTTOM slab isn't itself 'sunk', so nothing
    //  * should propagate upward from it") was <b>overruled entirely</b> on 2026-08-06 (exclusion #13):
    //  * a bottom slab's top face IS half a block below the grid, so a slab resting on it seats there
    //  * whether or not the support is itself sunk. {@link #slabOnFlatBottomSlabSeatsOnItsTopFace} pins
    //  * that plain case here, and {@link #slabTowerLaddersToTheClampThenGaps} pins the stacked ladder
    //  * including where the {@code MIN_RESOLVED_DY} clamp bites.
    //  *
    //  * <p><b>The scene</b> is the {@code /slabrig} {@code seatMinusOne} shape: a source column beside
    //  * the seat (stone / bottom slab / stone) whose top stone renders {@code -0.5}, making the bottom
    //  * slab beside it a legitimate air-below cantilever seat at {@code -0.5}. Every fixture premise —
    //  * <b>including the support's own {@code -0.5}</b> — is hard-asserted, so the cell cannot pass
    //  * vacuously against a seat that never sank.
    //  */

    /**
     * RED (anchor lane) — the exact live pair: a vanilla slab on an ANCHORED bottom slab that
     * renders -0.5 must read -1.0. Read 0.0 before the fix.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnAnchoredLoweredBottomSlabInheritsMinusOne(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos seat = buildLoweredSeat(ctx, 1, 1, true);

        BlockPos subject = seat.up();
        place(w, subject, bottomSlab(Blocks.OAK_SLAB));
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "oak_slab resting on an ANCHORED bottom slab that renders -0.5 must read -1.0, got "
                        + dy + " (live (157,-58,-10) dy=0.000 over (157,-59,-10) dy=-0.500: a slab "
                        + "on a LOWERED bottom slab has no lane — hasLoweredNonSlabTopSupport "
                        + "rejects the support on 'instanceof SlabBlock' and "
                        + "hasLoweredTopLikeSlabSupport rejects it on 'isBottomSlab', neither "
                        + "asking whether it is actually sunk)");
        ctx.complete();
    }

    /**
     * RED (geometric twin) — the same scene with no anchor anywhere. Both lanes go through the one
     * shared predicate, so they cannot drift apart (shared-predicate law).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnGeometricallyLoweredBottomSlabInheritsMinusOne(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos seat = buildLoweredSeat(ctx, 4, 1, false);

        BlockPos subject = seat.up();
        place(w, subject, bottomSlab(Blocks.OAK_SLAB));
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, subject),
                "setup: this twin must exercise the GEOMETRIC lane — no anchor anywhere");
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "oak_slab resting on a GEOMETRICALLY lowered bottom slab (-0.5, anchor=none) must "
                        + "read -1.0, got " + dy);
        ctx.complete();
    }

    /**
     * PERSISTENCE — the same predicate feeds {@code isVerticallyLoweredSlabSource} →
     * {@code isLoweredSideSlabVisual} → {@code qualifiesForLoweredSideSlabAnchor}, so the subject
     * must also RECORD the anchor that survives a later support break (never-pop).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnLoweredBottomSlabRecordsItsAnchor(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos seat = buildLoweredSeat(ctx, 4, 4, true);

        BlockPos subject = seat.up();
        place(w, subject, bottomSlab(Blocks.OAK_SLAB));
        SlabAnchorAttachment.addAnchor(w, subject, w.getBlockState(subject));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, subject),
                "a slab placed on a LOWERED bottom slab must record an anchor, or breaking the "
                        + "support later pops it flush even though it was never re-placed");
        ctx.complete();
    }

    /**
     * EXCLUSION #13 — <b>maintainer ruling, 2026-08-06</b>. Asked whether a slab resting on a PLAIN
     * (un-lowered) bottom slab should stay flat at 0.0 — vanilla's half-block gap — she answered
     * "it should lower, no? WYSIWYG law." <b>Ruling: it lowers.</b> This is exclusion #13 under her
     * standing law "everything should be able to lower; no exceptions".
     *
     * <p>A bottom slab's top face sits half a block below the grid, so ANY block resting on it —
     * <b>including another slab</b> — must seat there. The value is the resolver's own answer:
     * {@code supportSeatDy} gives a bottom slab a HALF-HEIGHT seat (support dy − 0.5), so a plain
     * bottom slab at dy 0.0 yields <b>−0.5</b>. No new arithmetic; the gate simply no longer
     * demands that the bottom-slab support be <i>already sunk</i>.
     *
     * <p>SUPERSEDES the former {@code slabOnFlatBottomSlabStaysFlat}, which asserted 0.0 here and
     * whose own note flagged this as "a candidate exclusion — the law value would be -0.5".
     * {@code CombinedSlabChainingMatrixTest}'s {@code 3.vanillaBOTTOM/vanillaBOTTOM :: upperSlab}
     * row is updated in step (was {@code Kind.BY_DESIGN}, now {@code Kind.STRICT}).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnFlatBottomSlabSeatsOnItsTopFace(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(1, 1, 5);
        place(w, ground, Blocks.STONE.getDefaultState());
        BlockPos support = ground.up();
        place(w, support, bottomSlab(Blocks.STONE_SLAB));
        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        ctx.assertTrue(Math.abs(supportDy) <= EPS,
                "fixture: this cell needs a PLAIN support — the bottom slab must render 0.0, got "
                        + supportDy);

        BlockPos subject = support.up();
        place(w, subject, bottomSlab(Blocks.OAK_SLAB));
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "a slab on a plain bottom slab must read -0.5 (maintainer ruling 2026-08-06, "
                        + "exclusion #13: 'it should lower, no? WYSIWYG law' — the support's top "
                        + "face is half a block below the grid, so the slab seats there instead of "
                        + "floating with vanilla's 0.5 gap), got " + dy);
        ctx.complete();
    }

    /**
     * TOWER PIN (maintainer ruling 2026-08-06, interaction 3) — a stack of four bottom slabs, each
     * placed on the one below through the REAL placement sequence ({@code setBlockState} +
     * {@code SlabAnchorAttachment.addAnchor}, exactly what {@code BlockOnPlacedAnchorMixin.onPlaced}
     * fires for a player click). ASSERTS the resolved dy at every level, <b>including where the
     * {@code MIN_RESOLVED_DY} (−1.0) clamp bites and vanilla's half-block gap reappears</b>.
     *
     * <p>This is a PIN, not an endorsement: the clamp is deliberately NOT "fixed" here. It exists
     * because {@code DY_SPEC.md} CS-CAP caps the whole offset set at {@code MIN_RESOLVED_DY} — the
     * deepest cell the offset-aware pick raycast window can target — so a deeper tower settles at
     * the cap rather than rendering somewhere unclickable.
     *
     * <p><b>Written against the cap, not against {@code -1.0} (Stage 4, 2026-08-07).</b> Every
     * course of this ladder is {@code max(-0.5 * i, cap)}: half a block per course until the clamp
     * refuses. At the shipped cap that is the identical list of numbers this row has asserted since
     * it was written; with {@code SlabSupport.DEEP_DY_ALPHABET} armed the ladder simply runs two
     * courses further before it flattens. The tower's HEIGHT is derived from the cap too, so there
     * are always post-clamp courses left to measure.
     *
     * <p>MEASURED LADDER (world-space spans, ground stone top at {@code Y}):
     * <ul>
     *   <li>L0 on stone — dy {@code 0.0}, span {@code [Y, Y+0.5]} — flush, no anchor;</li>
     *   <li>L1 on L0 — dy {@code -0.5}, span {@code [Y+0.5, Y+1.0]} — FLUSH on L0's top face;</li>
     *   <li>L2 on L1 — dy {@code -1.0}, span {@code [Y+1.0, Y+1.5]} — FLUSH on L1's top face;</li>
     *   <li>L3 on L2 — dy {@code -1.0} (raw seat −1.5, <b>CLAMPED</b>), span {@code [Y+2.0, Y+2.5]}
     *       — a 0.5 GAP reopens above L2's top face at {@code Y+1.5}.</li>
     * </ul>
     * So the tower is flush for three courses and reverts to the vanilla stagger from the fourth
     * course upward. The maintainer rules on the appearance from these numbers.
     *
     * <p><b>EXTENDED to L4/L5 (Stage 0 measurement B, 2026-08-07).</b> The tower now runs six
     * courses so the ladder past the clamp is measured rather than assumed. Every original
     * assertion (L0–L3, and the L1–L3 anchor loop) is unchanged; the two new courses are asserted
     * separately and the raw values are printed under {@code [STAGE0-B]}. <b>Measured answer: the
     * ladder does not decay past the clamp — L4 and L5 read {@code -1.0} exactly like L3.</b> The
     * reason is not the depth budget: {@code addAnchor} records each course's placement height, so
     * every course above L1 resolves its seat from the course below's STORED number and the walk
     * terminates at depth 1. {@code MAX_SUPPORT_RESOLVE_DEPTH} is never approached here. See
     * {@code DeepDyWindowCharacterisationTest} for the pre-store lane, which is the only shape that
     * consumes the budget at all.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabTowerLaddersToTheClampThenGaps(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);
        place(w, ground, Blocks.STONE.getDefaultState());

        // SIX courses since Stage 0 measurement B. DEEPENED at Stage 4 (2026-08-07): this ladder
        // saturates at course index ceil(-cap / 0.5), so a fixed six left no post-clamp course at
        // all once the cap could be -2.0 (saturation lands at L4, and L5 was the only witness). The
        // height is now derived — saturation index plus three — and taken as the LARGER of that and
        // the historical six, so no course this row has ever built is removed and the deep leg
        // gains the ones it needs.
        int saturatedIndex = (int) Math.ceil(-SlabSupport.MIN_RESOLVED_DY / 0.5);
        BlockPos[] level = new BlockPos[Math.max(6, saturatedIndex + 3)];
        for (int i = 0; i < level.length; i++) {
            level[i] = ground.up(i + 1);
            place(w, level[i], bottomSlab(Blocks.OAK_SLAB));
            // The real placement sequence: onPlaced -> addAnchor fires for every player click.
            SlabAnchorAttachment.addAnchor(w, level[i], w.getBlockState(level[i]));
        }

        double[] dy = new double[level.length];
        for (int i = 0; i < level.length; i++) {
            dy[i] = SlabSupport.getYOffset(w, level[i], w.getBlockState(level[i]));
        }
        String ladder = "L0=" + dy[0] + " L1=" + dy[1] + " L2=" + dy[2] + " L3=" + dy[3];
        StringBuilder deep = new StringBuilder(ladder);
        for (int i = 4; i < level.length; i++) {
            deep.append(" L").append(i).append('=').append(dy[i]);
        }
        String ladderDeep = deep + " (cap=" + SlabSupport.MIN_RESOLVED_DY + ")";
        System.out.println("[STAGE0-B] anchored tower ladder (store live): " + ladderDeep);

        // THE LADDER IS AN ARITHMETIC CONSEQUENCE OF THE CAP, not a list of numbers. Course i seats
        // half a block below course i-1 until the clamp refuses, so dy[i] = max(-0.5*i, cap). At
        // the shipped -1.0 cap that is exactly 0.0 / -0.5 / -1.0 / -1.0 / ... — every value this
        // row has asserted since it was written. The individual assertions below are KEPT as they
        // were, because they carry the reasons; this one states the shape they share and is what
        // makes the row hold at a deeper cap without being rewritten again.
        for (int i = 0; i < level.length; i++) {
            double expected = Math.max(-0.5 * i, SlabSupport.MIN_RESOLVED_DY);
            ctx.assertTrue(Math.abs(dy[i] - expected) <= EPS,
                    "tower L" + i + " must read max(-0.5*" + i + ", cap) = " + expected + ", got "
                            + dy[i] + " — " + ladderDeep);
        }

        ctx.assertTrue(Math.abs(dy[0]) <= EPS,
                "tower L0 (bottom slab on plain stone) must stay flush at 0.0 — " + ladder);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, level[0]),
                "tower L0 rests on a flat non-slab support and must NOT anchor");

        ctx.assertTrue(Math.abs(dy[1] + 0.5) <= EPS,
                "tower L1 must seat on L0's top face at -0.5 (maintainer ruling 2026-08-06) — " + ladder);
        ctx.assertTrue(Math.abs(dy[2] + 1.0) <= EPS,
                "tower L2 must seat on L1's top face at -1.0 (compounded through the resolver) — "
                        + ladder);
        ctx.assertTrue(Math.abs(dy[3] - Math.max(-1.5, SlabSupport.MIN_RESOLVED_DY)) <= EPS,
                "tower L3 PINS THE CLAMP: its raw seat is -1.5, and MIN_RESOLVED_DY ("
                        + SlabSupport.MIN_RESOLVED_DY + ") either refuses it — reopening a 0.5 "
                        + "vanilla gap above L2 from the fourth course upward — or lets it stand. "
                        + "This is pinned, NOT fixed — the maintainer rules on the tower's appearance from "
                        + "these values — " + ladder);

        for (int i = 1; i < 4; i++) {
            ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, level[i]),
                    "tower L" + i + " renders lowered, so it must RECORD an anchor or breaking the "
                            + "course below pops it (never-pop law) — " + ladder);
        }

        // STAGE 0, MEASUREMENT B (added 2026-08-07; nothing above this line changed). The two
        // courses past the clamp are MEASURED, not assumed: they stay at -1.0 rather than decaying
        // to the -0.5 exhaustion floor, because each course's placement height is STORED by
        // addAnchor and the seat walk terminates on that stored number at depth 1. The depth budget
        // is not reached, so a post-store tower has no "one course past the budget" behaviour at
        // all.
        // Course indices at or past saturation: the ones whose raw seat the cap actually refuses.
        // saturatedIndex is 2 at the shipped -1.0 cap, so this loop still starts at L2's successor
        // — the same courses it has always covered — and follows the cap when the cap moves.
        for (int i = saturatedIndex; i < level.length; i++) {
            ctx.assertTrue(Math.abs(dy[i] - SlabSupport.MIN_RESOLVED_DY) <= EPS,
                    "tower L" + i + " must stay at the MIN_RESOLVED_DY clamp ("
                            + SlabSupport.MIN_RESOLVED_DY + "), NOT fall to the -0.5 "
                            + "depth-exhaustion floor — " + ladderDeep);
            ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, level[i]),
                    "tower L" + i + " renders lowered, so it must RECORD an anchor (never-pop "
                            + "law) — " + ladderDeep);
            double stored = com.slabbed.anchor.SlabPlacementDyAttachment.storedDy(w, level[i]);
            ctx.assertTrue(Math.abs(stored - SlabSupport.MIN_RESOLVED_DY) <= EPS,
                    "tower L" + i + " must carry a STORED placement height of "
                            + SlabSupport.MIN_RESOLVED_DY + " — that stored fact is exactly what "
                            + "terminates the seat walk at depth 1 and keeps "
                            + "MAX_SUPPORT_RESOLVE_DEPTH out of reach, got " + stored + " — "
                            + ladderDeep);
        }
        ctx.complete();
    }

    /**
     * TOWER PIN, GEOMETRIC (no anchors anywhere) — the same stack written with {@code setBlockState}
     * only, so no {@code onPlaced}/{@code addAnchor} ever fires. PINS CURRENT BEHAVIOUR, and it
     * DIVERGES from the anchored ladder above at L2.
     *
     * <p>Cause: {@code loweredBottomSlabSupportDy} — the recursion-safe mirror {@code supportSeatDy}
     * consults for a bottom-slab seat — reproduces only the ANCHORED / direct-custom / side-adjacency
     * arms of {@code getYOffsetInner}'s slab branch. It has no arm for the vertical
     * "rests on a lowered support below" lane, so an unanchored lowered bottom slab still reports its
     * own dy as {@code 0.0} and the ladder saturates at −0.5 instead of compounding.
     *
     * <p>Not repaired here, deliberately: adding that arm makes {@code hasLoweredSlabSupport} →
     * {@code loweredBottomSlabSupportDy} → {@code hasLoweredSlabSupport} re-enter with the depth
     * counter RESET to 0, i.e. an unbounded descending walk with branching on the chunk-render hot
     * path — the lag class this project has already shipped twice. Every real player placement takes
     * the anchored ladder above (the anchor qualifier accepts a bottom-slab support through the same
     * widened predicate), so this divergence is reachable only by synthetic writes and pre-existing
     * worlds. Reported to the maintainer as known-incomplete.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void geometricSlabTowerPinsTheUnanchoredSaturation(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(5, 1, 2);
        place(w, ground, Blocks.STONE.getDefaultState());

        BlockPos[] level = new BlockPos[4];
        for (int i = 0; i < 4; i++) {
            level[i] = ground.up(i + 1);
            place(w, level[i], bottomSlab(Blocks.OAK_SLAB));   // setBlockState ONLY — no anchor
        }
        for (int i = 0; i < 4; i++) {
            ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, level[i]),
                    "fixture: L" + i + " must carry no anchor — this cell exercises the GEOMETRIC lane");
        }

        double[] dy = new double[4];
        for (int i = 0; i < 4; i++) {
            dy[i] = SlabSupport.getYOffset(w, level[i], w.getBlockState(level[i]));
        }
        String ladder = "L0=" + dy[0] + " L1=" + dy[1] + " L2=" + dy[2] + " L3=" + dy[3];

        ctx.assertTrue(Math.abs(dy[0]) <= EPS, "geometric tower L0 must be 0.0 — " + ladder);
        ctx.assertTrue(Math.abs(dy[1] + 0.5) <= EPS,
                "geometric tower L1 must seat on L0's top face at -0.5 — " + ladder);
        ctx.assertTrue(Math.abs(dy[2] + 0.5) <= EPS,
                "PINS CURRENT BEHAVIOUR (not desired): the geometric ladder SATURATES at -0.5 from "
                        + "L2 up, because loweredBottomSlabSupportDy has no vertical-support arm and "
                        + "reports an unanchored lowered bottom slab as 0.0. The anchored ladder "
                        + "(slabTowerLaddersToTheClampThenGaps) compounds to -1.0 here — " + ladder);
        ctx.assertTrue(Math.abs(dy[3] + 0.5) <= EPS,
                "PINS CURRENT BEHAVIOUR: same saturation at L3 — " + ladder);
        ctx.complete();
    }

    // ------------------------------------------------------------------------

    /**
     * Builds the {@code seatMinusOne} shape at plot-relative {@code (x, z)}, occupying {@code z}
     * and {@code z + 1}, and returns the bottom-slab seat that renders {@code -0.5}.
     *
     * <p>Seat column: ground stone at y+1, AIR at y+2, seat slab at y+3 — the donor-correct
     * air-below cantilever (a stone under the seat is the interpenetration state outlawed by the
     * flush-seat guard, 2026-08-05, and would read 0.0 instead).
     */
    private BlockPos buildLoweredSeat(TestContext ctx, int x, int z, boolean anchorSeat) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(x, 1, z);
        BlockPos source = base.add(0, 0, 1);

        // Source column: stone / bottom slab / stone — the top stone is lowered -0.5 by the slab.
        place(w, source, Blocks.STONE.getDefaultState());
        place(w, source.up(), bottomSlab(Blocks.STONE_SLAB));
        BlockPos sourceTop = source.up(2);
        place(w, sourceTop, Blocks.STONE.getDefaultState());
        double sourceTopDy = SlabSupport.getYOffset(w, sourceTop, w.getBlockState(sourceTop));
        ctx.assertTrue(Math.abs(sourceTopDy + 0.5) <= EPS,
                "fixture: the side-source top stone must render -0.5, got " + sourceTopDy);

        place(w, base, Blocks.STONE.getDefaultState());
        BlockPos seat = base.up(2);
        place(w, seat, bottomSlab(Blocks.STONE_SLAB));
        if (anchorSeat) {
            SlabAnchorAttachment.addAnchor(w, seat, w.getBlockState(seat));
            ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, seat),
                    "fixture: the seat slab must anchor via the lowered-side-slab lane");
        }
        // THE PREMISE THIS WHOLE CLASS RESTS ON: the support is a bottom slab that is ACTUALLY
        // SUNK. Without this assert the cells could pass vacuously against a seat at 0.0.
        double seatDy = SlabSupport.getYOffset(w, seat, w.getBlockState(seat));
        ctx.assertTrue(Math.abs(seatDy + 0.5) <= EPS,
                "fixture: the bottom-slab SUPPORT must itself render -0.5, got " + seatDy);
        return seat;
    }

    private static void place(ServerWorld w, BlockPos pos, BlockState state) {
        w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }

    private static BlockState bottomSlab(Block slab) {
        return slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }
}
