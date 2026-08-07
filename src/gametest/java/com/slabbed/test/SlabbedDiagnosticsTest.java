package com.slabbed.test;

import com.slabbed.dev.SlabbedDiagnostics;
import com.slabbed.dev.SlabbedLabFixtures;
import com.slabbed.dev.SlabbedLabFixtures.PlaceResult;
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
 * Proves the {@link SlabbedDiagnostics} detectors actually fire — both the pure predicates
 * (fed the exact bug values) and an integration pass over a real fence-on-slab. This is the
 * headlessly-verifiable half of the enriched recorder; the live capture wiring
 * (firing on crosshair-target change, reading the client render trace) is live-only.
 */
public final class SlabbedDiagnosticsTest {

    // ── pure predicate unit tests (no world needed) ──────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void triadMismatchDetectorFiresOnTheBugValues(TestContext ctx) {
        BlockState fence = Blocks.OAK_FENCE.getDefaultState();
        BlockState bottomSlab = Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState topSlab = Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
        BlockState bed = Blocks.WHITE_BED.getDefaultState();
        BlockState chain = Blocks.IRON_CHAIN.getDefaultState();
        BlockState lantern = Blocks.LANTERN.getDefaultState();

        // Base-0 block, outline followed the dy → no flag.
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(fence, -0.5, -0.5),
                "a fence whose outline followed the dy must NOT flag");
        // Base-0 block, outline pinned at grid while visual lowered → real bug (the raycast class).
        ctx.assertTrue(SlabbedDiagnostics.triadMismatch(fence, -0.5, 0.0),
                "a fence outline pinned at grid while visual lowered MUST flag (the raycast bug)");
        // A BOTTOM slab (base 0) is decidable and flags the bug value.
        ctx.assertTrue(SlabbedDiagnostics.triadMismatch(bottomSlab, -0.5, 0.0),
                "a bottom slab with outline at grid while visual lowered MUST flag");
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(bed, -0.5, -0.5),
                "a bed whose outline tracks the dy must NOT flag");
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(fence, -0.5, Double.NaN),
                "an empty outline must not produce a spurious mismatch");

        // REGRESSION (recorder session c5ab15ce, 2026-07-02) — the recorded false positives:
        // a lowered TOP slab has outline base 0.5, so outlineMinY 0.0 at visualDy -0.5 is
        // CORRECT, not a mismatch (proven in AnchoredSlabTriadTest). Top slabs and hanging
        // decorations (chain ~0.41, lantern ~0.06 base) are excluded from this check entirely.
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(topSlab, -0.5, 0.0),
                "a lowered TOP slab (base 0.5 -> outlineMinY 0.0) must NOT flag — recorded false positive");
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(chain, -0.5, -0.094),
                "a chain (nonzero outline base) must NOT flag — this was a false positive");
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(lantern, 0.5, 0.563),
                "a hanging lantern (nonzero base) must NOT flag");
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(chain, -0.5, 0.0),
                "even an extreme chain value must not flag — chains are not decidable here");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dodoDetectorFiresOnLoweredOpaqueCube(TestContext ctx) {
        ctx.assertTrue(SlabbedDiagnostics.dodoRisk(true, -0.5),
                "an opaque full cube at a nonzero dy is a DODO (see-through hole) risk");
        ctx.assertTrue(!SlabbedDiagnostics.dodoRisk(true, 0.0),
                "a flush opaque cube is fine");
        ctx.assertTrue(!SlabbedDiagnostics.dodoRisk(false, -0.5),
                "a non-opaque block lowered is normal, not a DODO");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void smooshDetectorFiresOnDoubleOffsetDecoration(TestContext ctx) {
        BlockState lantern = Blocks.LANTERN.getDefaultState();
        BlockState oakSlab = Blocks.OAK_SLAB.getDefaultState();
        BlockState stone = Blocks.STONE.getDefaultState();
        ctx.assertTrue(SlabbedDiagnostics.smooshRisk(lantern, -1.0),
                "a decoration lowered a FULL block is a smoosh (double-offset) risk");
        ctx.assertTrue(!SlabbedDiagnostics.smooshRisk(lantern, -0.5),
                "a decoration lowered a single half-step is normal");
        ctx.assertTrue(!SlabbedDiagnostics.smooshRisk(oakSlab, -1.0),
                "a slab is not a smoosh subject (compound stacks are legitimate)");
        ctx.assertTrue(!SlabbedDiagnostics.smooshRisk(stone, -1.0),
                "an opaque full cube is a DODO, not a smoosh (classified separately)");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void gapDetectorFiresOnChainLanternDyMismatch(TestContext ctx) {
        BlockState chain = Blocks.IRON_CHAIN.getDefaultState();
        BlockState lantern = Blocks.LANTERN.getDefaultState();
        BlockState stone = Blocks.STONE.getDefaultState();
        ctx.assertTrue(SlabbedDiagnostics.dyDiscontinuity(chain, lantern, -0.5, 0.0),
                "a chain and lantern at different dy is a visible vertical gap");
        ctx.assertTrue(!SlabbedDiagnostics.dyDiscontinuity(chain, lantern, -0.5, -0.5),
                "a chain and lantern at the SAME dy connect cleanly (no gap)");
        ctx.assertTrue(!SlabbedDiagnostics.dyDiscontinuity(chain, stone, -0.5, 0.0),
                "a chain next to a non-decoration is not a decoration-gap");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void modelMismatchDetectorFiresWhenModelDivergesFromVisual(TestContext ctx) {
        ctx.assertTrue(SlabbedDiagnostics.modelMismatch(-0.5, 0.0),
                "a model rendered at grid while visual is lowered must flag (the pre-GH#21 fence render)");
        ctx.assertTrue(!SlabbedDiagnostics.modelMismatch(-0.5, -0.5),
                "model tracking visual is clean");
        ctx.assertTrue(!SlabbedDiagnostics.modelMismatch(-0.5, Double.NaN),
                "an unknown (server-side) model dy must not flag");
        ctx.complete();
    }

    /**
     * The two "no number" answers must never be the same token.
     *
     * <p>Recorder run {@code 9e925ab0} reported the model and raycast legs blank on every single
     * sampled row while outline and collision carried real values, and the blank was one shared
     * NaN — so a reader could not tell "this leg cannot be read in this context" from "this leg was
     * read and is empty". Two of the three dy legs were therefore unreadable in a diagnostic whose
     * entire purpose is to prove the three move together.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unsampledAndEmptyLegsAreDistinguishableSentinels(TestContext ctx) {
        BlockState fence = Blocks.OAK_FENCE.getDefaultState();

        ctx.assertTrue(!SlabbedDiagnostics.isMeasured(SlabbedDiagnostics.NOT_SAMPLED),
                "NOT_SAMPLED must not count as a measurement");
        ctx.assertTrue(!SlabbedDiagnostics.isMeasured(SlabbedDiagnostics.MEASURED_EMPTY),
                "MEASURED_EMPTY has no minY, so it must not count as a measurement either");
        ctx.assertTrue(SlabbedDiagnostics.isMeasured(-0.5),
                "a real number must count as a measurement");

        String unsampled = SlabbedDiagnostics.format(SlabbedDiagnostics.NOT_SAMPLED);
        String empty = SlabbedDiagnostics.format(SlabbedDiagnostics.MEASURED_EMPTY);
        ctx.assertTrue(!unsampled.equals(empty),
                "THE FIX: 'not measurable here' and 'measured, and empty' must print as DIFFERENT "
                        + "tokens — both read '" + unsampled + "'");
        ctx.assertTrue(!unsampled.equals(SlabbedDiagnostics.format(-0.5))
                        && !empty.equals(SlabbedDiagnostics.format(-0.5)),
                "neither sentinel may be mistaken for a number");

        // Every predicate stays silent on BOTH sentinels: an unread leg is not evidence of a bug.
        ctx.assertTrue(!SlabbedDiagnostics.modelMismatch(-0.5, SlabbedDiagnostics.MEASURED_EMPTY),
                "an empty model leg must not flag a mismatch");
        ctx.assertTrue(!SlabbedDiagnostics.modelMismatch(-0.5, SlabbedDiagnostics.NOT_SAMPLED),
                "an unsampled model leg must not flag a mismatch");
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(fence, -0.5, SlabbedDiagnostics.MEASURED_EMPTY),
                "an empty outline must not flag a triad mismatch");
        ctx.assertTrue(!SlabbedDiagnostics.collisionFollowsVisual(
                        -0.5, SlabbedDiagnostics.MEASURED_EMPTY, -0.5),
                "an empty outline leg cannot support a collision-tracking claim");
        ctx.complete();
    }

    /**
     * The raycast leg reports a REAL NUMBER wherever one exists, and the EMPTY sentinel — never the
     * unsampled one — wherever vanilla genuinely has no targeting shape.
     *
     * <p>{@code AbstractBlock.getRaycastShape} returns {@code VoxelShapes.empty()} unless a block
     * overrides it, because that shape only refines the reported SIDE on top of the outline hit in
     * {@code BlockView.raycastBlock}. Composter is one of the few that overrides it, so it is the
     * subject that proves the leg is wired at all; stone is the subject that proves an empty answer
     * is reported as a measurement rather than as a blank.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void raycastLegReportsANumberWhereverVanillaHasATargetingShape(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        PlaceResult placed = SlabbedLabFixtures.placeBasicFixture(w, origin);
        ctx.assertTrue(placed.ok(), "placeBasicFixture failed: " + placed.error());

        // Same geometry SlabbedLabFixtureTest#outlineRaycastParity uses: the BOTTOM_SLAB lane
        // support, whose occupant is proven to be offset (so this is not vacuous at dy 0).
        BlockPos composter = origin.add(2, 1, 0);
        w.setBlockState(composter, Blocks.COMPOSTER.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabbedDiagnostics.Sample c = SlabbedDiagnostics.analyze(w, composter, w.getBlockState(composter));

        ctx.assertTrue(SlabbedDiagnostics.isMeasured(c.raycastMinY()),
                "a composter overrides getRaycastShape, so the raycast leg MUST report a number, got "
                        + SlabbedDiagnostics.format(c.raycastMinY()));
        ctx.assertTrue(c.raycastMinY() < -1.0e-6,
                "the offset must be visible on the raycast leg (else this assertion is vacuous at 0), got "
                        + SlabbedDiagnostics.format(c.raycastMinY()));
        ctx.assertTrue(Math.abs(c.raycastMinY() - c.outlineMinY()) < SlabbedDiagnostics.EPS,
                "outline and raycast legs disagree: outline=" + SlabbedDiagnostics.format(c.outlineMinY())
                        + " raycast=" + SlabbedDiagnostics.format(c.raycastMinY()));

        // Stone: vanilla's default empty raycast shape. Reported as a measurement, not as a blank.
        BlockPos stone = origin.add(0, 1, 0);
        w.setBlockState(stone, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabbedDiagnostics.Sample s = SlabbedDiagnostics.analyze(w, stone, w.getBlockState(stone));

        ctx.assertTrue(s.raycastMinY() == SlabbedDiagnostics.MEASURED_EMPTY,
                "stone has no targeting shape of its own, so the raycast leg must read EMPTY, got "
                        + SlabbedDiagnostics.format(s.raycastMinY()));
        ctx.assertTrue(!Double.isNaN(s.raycastMinY()),
                "an empty raycast shape must NOT share the unsampled sentinel — that conflation is "
                        + "exactly what made recorder run 9e925ab0 unreadable");
        ctx.assertTrue(SlabbedDiagnostics.isMeasured(s.outlineMinY()),
                "the outline leg must still carry a real number, got "
                        + SlabbedDiagnostics.format(s.outlineMinY()));
        ctx.assertTrue(Double.isNaN(s.modelDy()),
                "a server-side analyze has no render sample, so the model leg must read NOT_SAMPLED");
        ctx.complete();
    }

    // ── integration: a real fence-on-slab must analyze clean ─────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void realFenceOnSlabAnalyzesAsAConsistentLoweredTriad(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(slab, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos fence = slab.up();
        w.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

        SlabbedDiagnostics.Sample s = SlabbedDiagnostics.analyze(w, fence, w.getBlockState(fence));

        ctx.assertTrue(s.visualDy() < -1.0e-6,
                "fence on a vanilla slab must resolve a lowered dy, got " + SlabbedDiagnostics.format(s.visualDy()));
        ctx.assertTrue(!s.triadMismatch(),
                "post-GH#21-fix, the fence outline follows the dy, so no triad mismatch — got flags "
                        + s.flagSummary());
        ctx.assertTrue(!s.dodoRisk(), "a fence is not an opaque cube, so no DODO risk");
        ctx.assertTrue(!s.anySuspect(),
                "a correctly-lowered fence must analyze completely clean, got flags: " + s.flagSummary());
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatBlockAnalyzesClean(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        SlabbedDiagnostics.Sample s = SlabbedDiagnostics.analyze(w, pos, w.getBlockState(pos));
        ctx.assertTrue(Math.abs(s.visualDy()) < 1.0e-6, "flat stone must have dy 0");
        ctx.assertTrue(!s.anySuspect(), "flat stone on the ground must analyze clean, got: " + s.flagSummary());
        ctx.complete();
    }
}
