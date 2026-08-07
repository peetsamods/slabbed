package com.slabbed.test;

import com.slabbed.dev.SlabbedDiagnostics;
import com.slabbed.dev.SlabbedLabFixtures;
import com.slabbed.dev.SlabbedLabFixtures.PlaceResult;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Proves the {@link SlabbedDiagnostics} detectors actually fire — both the pure predicates
 * (fed the exact bug values) and an integration pass over a real fence-on-slab. This is the
 * headlessly-verifiable half of the enriched recorder; the live capture wiring
 * (firing on crosshair-target change, reading the client render trace) is live-only.
 */
public final class SlabbedDiagnosticsTest {

    // ── pure predicate unit tests (no world needed) ──────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void outlineLegMismatchDetectorFiresOnTheBugValues(TestContext ctx) {
        BlockState fence = Blocks.OAK_FENCE.getDefaultState();
        BlockState bottomSlab = Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState topSlab = Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
        BlockState bed = Blocks.WHITE_BED.getDefaultState();
        BlockState chain = Blocks.IRON_CHAIN.getDefaultState();
        BlockState lantern = Blocks.LANTERN.getDefaultState();

        // Base-0 block, outline followed the dy → no flag.
        ctx.assertTrue(!SlabbedDiagnostics.outlineMismatch(fence, -0.5, -0.5),
                "a fence whose outline followed the dy must NOT flag");
        // Base-0 block, outline pinned at grid while visual lowered → real bug (the raycast class).
        ctx.assertTrue(SlabbedDiagnostics.outlineMismatch(fence, -0.5, 0.0),
                "a fence outline pinned at grid while visual lowered MUST flag (the raycast bug)");
        // A BOTTOM slab (base 0) is decidable and flags the bug value.
        ctx.assertTrue(SlabbedDiagnostics.outlineMismatch(bottomSlab, -0.5, 0.0),
                "a bottom slab with outline at grid while visual lowered MUST flag");
        ctx.assertTrue(!SlabbedDiagnostics.outlineMismatch(bed, -0.5, -0.5),
                "a bed whose outline tracks the dy must NOT flag");
        ctx.assertTrue(!SlabbedDiagnostics.outlineMismatch(fence, -0.5, Double.NaN),
                "an empty outline must not produce a spurious mismatch");

        // REGRESSION (recorder session c5ab15ce, 2026-07-02) — the recorded false positives:
        // a lowered TOP slab has outline base 0.5, so outlineMinY 0.0 at visualDy -0.5 is
        // CORRECT, not a mismatch (proven in AnchoredSlabTriadTest). Top slabs and hanging
        // decorations (chain ~0.41, lantern ~0.06 base) are excluded from this check entirely.
        ctx.assertTrue(!SlabbedDiagnostics.outlineMismatch(topSlab, -0.5, 0.0),
                "a lowered TOP slab (base 0.5 -> outlineMinY 0.0) must NOT flag — recorded false positive");
        ctx.assertTrue(!SlabbedDiagnostics.outlineMismatch(chain, -0.5, -0.094),
                "a chain (nonzero outline base) must NOT flag — this was a false positive");
        ctx.assertTrue(!SlabbedDiagnostics.outlineMismatch(lantern, 0.5, 0.563),
                "a hanging lantern (nonzero base) must NOT flag");
        ctx.assertTrue(!SlabbedDiagnostics.outlineMismatch(chain, -0.5, 0.0),
                "even an extreme chain value must not flag — chains are not decidable here");
        ctx.complete();
    }

    /**
     * The DODO SHAPE PRECONDITION — unchanged assertions, honest name. Every row here was written
     * against the old {@code dodoRisk(boolean, double)}, which is now named for what it is: a
     * necessary first gate, not the flag. The flag itself is measured in
     * {@link #dodoFlagIsNarrowedToUnmitigatedStepsAndStillFires}.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dodoDetectorFiresOnLoweredOpaqueCube(TestContext ctx) {
        ctx.assertTrue(SlabbedDiagnostics.dodoShapePrecondition(true, -0.5),
                "an opaque full cube at a nonzero dy is a DODO (see-through hole) risk");
        ctx.assertTrue(!SlabbedDiagnostics.dodoShapePrecondition(true, 0.0),
                "a flush opaque cube is fine");
        ctx.assertTrue(!SlabbedDiagnostics.dodoShapePrecondition(false, -0.5),
                "a non-opaque block lowered is normal, not a DODO");
        ctx.complete();
    }

    /**
     * Same four assertions this cell has always made, with the magnitudes DERIVED from
     * {@link SlabSupport#MIN_RESOLVED_DY} instead of written as the literal {@code -1.0}.
     *
     * <p>The literal was not merely untidy: {@code smooshRisk}'s threshold is the resolver's floor,
     * and the alphabet work moves that floor to {@code -2.0}. Written down twice, this cell would
     * have gone RED at the cap change while the predicate was behaving correctly — or, worse,
     * stayed green against a predicate that had silently started flagging the new normal envelope.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void smooshDetectorFiresOnDoubleOffsetDecoration(TestContext ctx) {
        BlockState lantern = Blocks.LANTERN.getDefaultState();
        BlockState oakSlab = Blocks.OAK_SLAB.getDefaultState();
        BlockState stone = Blocks.STONE.getDefaultState();
        double floor = SlabSupport.MIN_RESOLVED_DY;
        double oneStepShort = floor / 2.0d;
        ctx.assertTrue(SlabbedDiagnostics.smooshRisk(lantern, floor),
                "a decoration lowered to the resolver floor (" + floor + ") is a smoosh "
                        + "(double-offset) risk");
        ctx.assertTrue(!SlabbedDiagnostics.smooshRisk(lantern, oneStepShort),
                "a decoration lowered short of the floor (" + oneStepShort + ") is normal");
        ctx.assertTrue(!SlabbedDiagnostics.smooshRisk(oakSlab, floor),
                "a slab is not a smoosh subject (compound stacks are legitimate)");
        ctx.assertTrue(!SlabbedDiagnostics.smooshRisk(stone, floor),
                "an opaque full cube is a DODO, not a smoosh (classified separately)");
        ctx.complete();
    }

    /**
     * THE AIR NARROWING, measured. {@code smooshRisk} classified "not a slab, not an opaque cube"
     * as a decoration, and an air cell's {@code visualDy} reads the pre-placement lane's value —
     * so air flagged. Measured over the recorder: <b>4 of 6</b> SMOOSH rows in run
     * {@code 9e925ab0} were {@code minecraft:air}, <b>7 of 13</b> in {@code b5d717d9}, <b>2 of
     * 17</b> in {@code ba008bca}.
     *
     * <p>Both directions are asserted in the same cell so the gate cannot be "fixed" by making the
     * predicate quiet: air at the floor must NOT flag, and a real decoration at the same value
     * MUST still flag.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void smooshIgnoresAirButStillFiresOnRealDecorations(TestContext ctx) {
        double floor = SlabSupport.MIN_RESOLVED_DY;
        ctx.assertTrue(!SlabbedDiagnostics.smooshRisk(Blocks.AIR.getDefaultState(), floor),
                "an air cell has no geometry to smoosh — it must NOT flag (4 of 6 SMOOSH rows in "
                        + "a recorded live run were air)");
        ctx.assertTrue(!SlabbedDiagnostics.smooshRisk(Blocks.CAVE_AIR.getDefaultState(), floor),
                "cave air is air too — the gate is isAir(), not an id comparison");
        ctx.assertTrue(SlabbedDiagnostics.smooshRisk(Blocks.LANTERN.getDefaultState(), floor),
                "THE GATE MUST NOT SILENCE THE FLAG: a real decoration at the floor still flags");
        ctx.assertTrue(SlabbedDiagnostics.smooshRisk(Blocks.IRON_CHAIN.getDefaultState(), floor),
                "the two non-air SMOOSH rows of run 9e925ab0 were iron_chain — they must survive "
                        + "the narrowing");
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
        ctx.assertTrue(!SlabbedDiagnostics.outlineMismatch(fence, -0.5, SlabbedDiagnostics.MEASURED_EMPTY),
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
                        + "exactly what made a recorded live run unreadable");
        ctx.assertTrue(SlabbedDiagnostics.isMeasured(s.outlineMinY()),
                "the outline leg must still carry a real number, got "
                        + SlabbedDiagnostics.format(s.outlineMinY()));
        ctx.assertTrue(Double.isNaN(s.modelDy()),
                "a server-side analyze has no render sample, so the model leg must read NOT_SAMPLED");
        ctx.complete();
    }

    // ── the triad flag now covers three legs, and says which ─────────

    /**
     * <b>THE DEFECT, pinned from all three directions.</b> {@code triadMismatch} used to compare
     * the OUTLINE against {@code visualDy} and nothing else, so {@code TRIAD_MISMATCH: 0} over a
     * whole recorder run certified one leg of three on a line whose documented failure mode is
     * exactly one leg moving while the others do not.
     *
     * <p>Each leg is given a disagreement ALONE, with the other two clean, and the umbrella must
     * fire on each. Under the old predicate the raycast row could not fire at all (no flag
     * existed) and the model row did not raise {@code TRIAD_MISMATCH}.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void triadUmbrellaFiresFromEveryOneOfTheThreeLegs(TestContext ctx) {
        SlabbedDiagnostics.Sample outlineOnly = sample(-0.5, 0.0, -0.5, -0.5);
        SlabbedDiagnostics.Sample raycastOnly = sample(-0.5, -0.5, 0.0, -0.5);
        SlabbedDiagnostics.Sample modelOnly = sample(-0.5, -0.5, -0.5, 0.0);
        SlabbedDiagnostics.Sample clean = sample(-0.5, -0.5, -0.5, -0.5);

        ctx.assertTrue(outlineOnly.triadMismatch() && outlineOnly.outlineMismatch(),
                "OUTLINE leg alone must raise the triad flag, got " + outlineOnly.flagSummary());
        ctx.assertTrue(raycastOnly.triadMismatch() && raycastOnly.raycastMismatch(),
                "RAYCAST leg alone must raise the triad flag — under the old predicate this leg had "
                        + "NO FLAG AT ALL, got " + raycastOnly.flagSummary());
        ctx.assertTrue(modelOnly.triadMismatch() && modelOnly.modelMismatch(),
                "MODEL leg alone must raise the triad flag — the old predicate left it out of the "
                        + "counter entirely, got " + modelOnly.flagSummary());
        ctx.assertTrue(!clean.triadMismatch(),
                "three agreeing legs must not flag, got " + clean.flagSummary());
        ctx.assertTrue(clean.triadLegsVerified() == 3,
                "and on that clean row all three legs must be CHECKED, else the green means "
                        + "nothing — got " + clean.triadCoverage());
        ctx.complete();
    }

    /**
     * <b>A flag that cannot fire must never read as a flag that fired and found nothing.</b>
     *
     * <p>{@code SlabbedDiagnostics.analyze(world, pos, state)} supplies no model sample, so
     * {@code modelMismatch} is {@code false} by construction there — Stage 0 measured this and
     * {@code DeepDyWindowCharacterisationTest#triadModelLegAtADeepMagnitude} pins it. That
     * {@code false} is now accompanied by an explicit, visible coverage state, so the row reports
     * "1 of 3 legs verified, model NOT_SAMPLED" instead of a silent clean bill of health.
     *
     * <p>The sample is NOT re-derived from the caller's world on purpose: {@code ClientDy.dyFor}
     * is a pure delegate to {@code getVisualYOffset}, which is the same call {@code visualDy}
     * makes, so doing that would make the flag green by construction — a second flag that cannot
     * fire, wearing the badge of a fix.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void modelLegInabilityToSampleIsExplicitNotSilent(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(slab, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos stone = slab.up();
        w.setBlockState(stone, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        SlabbedDiagnostics.Sample headless = SlabbedDiagnostics.analyze(w, stone, w.getBlockState(stone));
        ctx.assertTrue(headless.modelLeg() == SlabbedDiagnostics.LegCheck.NOT_SAMPLED,
                "a headless analyze has no independent model sample, and that must be STATED, got "
                        + headless.triadCoverage());
        ctx.assertTrue(!headless.modelMismatch(),
                "it must still not INVENT a mismatch out of the missing sample");
        ctx.assertTrue(headless.triadLegsVerified() < 3,
                "so this row must NOT count as three-leg coverage, got "
                        + headless.triadLegsVerified() + "/3 — " + headless.triadCoverage());
        ctx.assertTrue(headless.triadCoverage().contains("model=NOT_SAMPLED"),
                "and the reason must be legible in the recorder row, got " + headless.triadCoverage());

        // Hand it a real, DIVERGENT sample: the same leg must fire. A flag that only ever reports
        // "unchecked" would be no better than the silent false it replaces.
        SlabbedDiagnostics.Sample supplied =
                SlabbedDiagnostics.analyze(w, stone, w.getBlockState(stone), 0.0);
        ctx.assertTrue(Math.abs(supplied.visualDy()) > SlabbedDiagnostics.EPS,
                "fixture: stone on a bottom slab must be lowered, else the row below is vacuous, got "
                        + SlabbedDiagnostics.format(supplied.visualDy()));
        ctx.assertTrue(supplied.modelLeg() == SlabbedDiagnostics.LegCheck.CHECKED
                        && supplied.modelMismatch() && supplied.triadMismatch(),
                "a supplied model sample of 0.0 against a lowered visual dy MUST fire, got "
                        + supplied.flagSummary() + " — " + supplied.triadCoverage());
        ctx.complete();
    }

    /**
     * The RAYCAST leg: a real mismatch fires, and {@code EMPTY} — the correct answer for almost
     * every block — is reported as coverage the leg does NOT have rather than as a clean pass.
     *
     * <p>Compared against the OUTLINE, not against {@code visualDy}, because
     * {@code getRaycastShape} is a side refinement layered on the outline hit; that also makes the
     * leg decidable for blocks whose base is not 0, where a raw dy comparison is not.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void raycastLegFiresOnDisagreementAndReportsEmptyAsUnverified(TestContext ctx) {
        ctx.assertTrue(SlabbedDiagnostics.raycastMismatch(0.0, -0.5),
                "a targeting shape pinned at grid while the outline is lowered MUST fire");
        ctx.assertTrue(!SlabbedDiagnostics.raycastMismatch(-0.5, -0.5),
                "a targeting shape tracking the outline is clean");
        ctx.assertTrue(!SlabbedDiagnostics.raycastMismatch(
                        SlabbedDiagnostics.MEASURED_EMPTY, -0.5),
                "an EMPTY targeting shape is CORRECT for stone/slabs/chains — inventing a mismatch "
                        + "there would be a phantom");
        ctx.assertTrue(SlabbedDiagnostics.raycastLeg(SlabbedDiagnostics.MEASURED_EMPTY, -0.5)
                        == SlabbedDiagnostics.LegCheck.EMPTY_BY_DESIGN,
                "but EMPTY verifies no dy, so it must not be reported as a CHECKED leg");
        ctx.assertTrue(SlabbedDiagnostics.raycastLeg(SlabbedDiagnostics.NOT_SAMPLED, -0.5)
                        == SlabbedDiagnostics.LegCheck.NOT_SAMPLED,
                "and 'never read' must stay distinguishable from 'read, and empty'");
        ctx.complete();
    }

    /**
     * The leg that actually decides targeting is the EFFECTIVE HIT, and {@code analyze} cannot
     * sample it — a per-cell probe has no ray. So it is measured here the way Stage 0's
     * {@code DeepDyWindowCharacterisationTest} established: by firing a real
     * {@link SlabbedOffsetRaycast} and reading where it lands.
     *
     * <p>The cell also pins the honest bookkeeping: on the very same block the diagnostic reports
     * the raycast leg as {@code EMPTY_BY_DESIGN}, so a reader of the recorder cannot mistake
     * "vanilla has no targeting shape here" for "targeting was verified here".
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void effectiveHitLegTracksTheVisualDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 3, 2);
        BlockState slab = Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        w.setBlockState(pos, slab, Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabPlacementDyAttachment.record(w, pos, -1.0),
                "fixture: the placement store must accept the forced dy");
        double dy = SlabSupport.getVisualYOffset(w, pos, w.getBlockState(pos));
        ctx.assertTrue(Math.abs(dy + 1.0) <= SlabbedDiagnostics.EPS,
                "fixture: the subject must actually read -1.0, got " + dy);

        Vec3d eye = new Vec3d(pos.getX() + 0.5, pos.getY() + 4.0, pos.getZ() + 0.5);
        Vec3d end = new Vec3d(pos.getX() + 0.5, pos.getY() - 4.0, pos.getZ() + 0.5);
        BlockHitResult hit = SlabbedOffsetRaycast.raycast(w, eye, end, ShapeContext.absent());

        ctx.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos),
                "the effective hit must land on the subject, got " + hit.getType() + " "
                        + hit.getBlockPos());
        // A bottom slab is half a block tall, so its offset top face is at dy + 0.5.
        double expected = pos.getY() + 0.5 + dy;
        ctx.assertTrue(Math.abs(hit.getPos().y - expected) <= 1.0e-4,
                "the effective hit must land on the OFFSET top face at " + expected + ", got "
                        + hit.getPos().y + " — if this drifts from the visual dy the crosshair and "
                        + "the geometry have separated, which is the whole point of the triad law");

        SlabbedDiagnostics.Sample s = SlabbedDiagnostics.analyze(w, pos, w.getBlockState(pos));
        ctx.assertTrue(s.raycastLeg() == SlabbedDiagnostics.LegCheck.EMPTY_BY_DESIGN,
                "a slab has no getRaycastShape of its own, so the diagnostic's raycast leg must "
                        + "report EMPTY_BY_DESIGN rather than a verified pass, got " + s.triadCoverage());
        SlabPlacementDyAttachment.clear(w, pos);
        ctx.complete();
    }

    /**
     * <b>THE DODO NARROWING, measured in-world in both directions.</b>
     *
     * <p>Over a recorded live run the old predicate selected exactly
     * {@code {opaqueFullCube && |visualDy| > EPS}} — <b>36 of 55 rows</b> ({@code stone} x17,
     * {@code stripped_jungle_log} x10, {@code smooth_stone_slab} x9) — the mod's entire normal
     * operating envelope, and this campaign read signal into it. It was blind to
     * {@code BlockRenderInfoCullMixin}'s per-face mitigation via
     * {@code SlabSupport.isSlabHeightStepFace}.
     *
     * <p>Subject A is a lowered cube with no exposed step at all: the old predicate fired on it,
     * the narrowed flag does not. Subject B is a real, unmitigated vertical step: the narrowed
     * flag still fires. Both are asserted in one cell so the narrowing cannot be satisfied by a
     * predicate that has simply gone quiet.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dodoFlagIsNarrowedToUnmitigatedStepsAndStillFires(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        // A — lowered opaque cube, nothing adjacent to expose a step (its support is a SLAB, which
        // does not occlude the shared face, and every other side is air).
        BlockPos slabA = origin.add(2, 2, 2);
        w.setBlockState(slabA, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos cubeA = slabA.up();
        w.setBlockState(cubeA, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabbedDiagnostics.Sample a = SlabbedDiagnostics.analyze(w, cubeA, w.getBlockState(cubeA));

        ctx.assertTrue(SlabbedDiagnostics.dodoShapePrecondition(true, a.visualDy()),
                "fixture: subject A must satisfy the OLD predicate, else the narrowing below is "
                        + "vacuous — dy=" + SlabbedDiagnostics.format(a.visualDy()));
        ctx.assertTrue(!a.dodoRisk(),
                "NARROWED: a lowered cube with no occluding neighbour at a different height exposes "
                        + "no hole, so it must NOT flag. This is the 36-of-55 class. Got flags: "
                        + a.flagSummary());

        // B — a real vertical step between two opaque cubes. The cull mixin is horizontal-only, so
        // nothing mitigates this one and it must still flag.
        BlockPos low = origin.add(5, 2, 5);
        BlockPos high = low.up();
        w.setBlockState(low, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(high, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabPlacementDyAttachment.record(w, low, -1.0)
                        && SlabPlacementDyAttachment.record(w, high, -0.5),
                "fixture: the placement store must accept both forced heights");
        double lowDy = SlabSupport.getVisualYOffset(w, low, w.getBlockState(low));
        double highDy = SlabSupport.getVisualYOffset(w, high, w.getBlockState(high));
        ctx.assertTrue(highDy - lowDy > SlabbedDiagnostics.EPS,
                "fixture: the block above must sit HIGHER than the subject for a gap to exist, got "
                        + lowDy + " below " + highDy);

        SlabbedDiagnostics.Sample b = SlabbedDiagnostics.analyze(w, low, w.getBlockState(low));
        ctx.assertTrue(b.dodoRisk(),
                "THE FLAG MUST STILL FIRE: a " + (highDy - lowDy) + "-block vertical gap under an "
                        + "opaque cube whose top face the mesher culls is a see-through hole, and "
                        + "the per-face mitigation is horizontal-only so nothing covers it. Got "
                        + "flags: " + b.flagSummary());

        // ... and it is the STEP that fires it, not the lowering: level the pair and it goes quiet.
        ctx.assertTrue(SlabPlacementDyAttachment.record(w, high, -1.0),
                "fixture: re-record the upper block level with the subject");
        SlabbedDiagnostics.Sample levelled = SlabbedDiagnostics.analyze(w, low, w.getBlockState(low));
        ctx.assertTrue(!levelled.dodoRisk(),
                "two equally-lowered stacked cubes have no gap between them, so the flag must go "
                        + "quiet — got flags: " + levelled.flagSummary());

        SlabPlacementDyAttachment.clear(w, low);
        SlabPlacementDyAttachment.clear(w, high);
        ctx.complete();
    }

    /** A Sample carrying only the four dy legs — the rest is padding the triad rows do not read. */
    private static SlabbedDiagnostics.Sample sample(double visualDy, double outlineMinY,
                                                    double raycastMinY, double modelDy) {
        BlockState stone = Blocks.STONE.getDefaultState();
        return new SlabbedDiagnostics.Sample(
                "minecraft:stone", visualDy, outlineMinY, raycastMinY, 0.0, modelDy,
                true, false, "none", "minecraft:air", 0.0, "minecraft:air", 0.0,
                SlabbedDiagnostics.outlineMismatch(stone, visualDy, outlineMinY),
                false, false, false, false, false,
                SlabbedDiagnostics.modelMismatch(visualDy, modelDy),
                SlabbedDiagnostics.raycastMismatch(raycastMinY, outlineMinY),
                SlabbedDiagnostics.outlineLeg(stone, outlineMinY),
                SlabbedDiagnostics.raycastLeg(raycastMinY, outlineMinY),
                SlabbedDiagnostics.modelLeg(modelDy));
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
