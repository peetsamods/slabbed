package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.dev.SlabbedDiagnostics;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/**
 * STAGE 0 — RED-first characterisation for the dy-alphabet growth ruling
 * ({@code docs/process/LIVE_LEDGER.md}, "RULING (Maintainer, 2026-08-06): the dy alphabet grows —
 * GATED, cap -2.0"). <b>TESTS ONLY. No production behaviour is changed by this file, and nothing
 * here endorses a deeper alphabet — it MEASURES the three claims the ruling rests on so the later
 * stages argue from numbers instead of from reading.</b>
 *
 * <p><b>How a deep dy is forced without touching the resolver or its clamp.</b>
 * {@code SlabSupport.getYOffsetInner} reads {@code SlabPlacementDyAttachment.storedDy} first and
 * returns it verbatim — that IS LAW 1, and it is the only seam that can put a subject at a
 * magnitude the live resolver would clamp away. Every cell below writes the height straight into
 * the store with {@code SlabPlacementDyAttachment.record} and hard-asserts that
 * {@code getYOffset} hands it back unchanged. {@code MIN_RESOLVED_DY} is never touched, and no cell
 * asks the resolver to PRODUCE a deep value.
 *
 * <p><b>Three measurements, three answers.</b>
 * <ul>
 *   <li><b>A — the pick window.</b> {@code SlabbedOffsetRaycast.consumeCell} tests
 *       {@code {C, C.down(), C.up()}}, a radius-1 window around each marched cell. A shape layer
 *       {@code L} can therefore only be attributed to its owner {@code P} when
 *       {@code |L - P.y| <= 1}. The cells here measure, from the REAL offset outline, which layers
 *       each deep subject occupies and then fire real rays at them.</li>
 *   <li><b>B — the depth budget.</b> {@code MAX_SUPPORT_RESOLVE_DEPTH} and what exhaustion
 *       returns, measured on real towers rather than read off the source.</li>
 *   <li><b>C — the triad.</b> Model, outline and raycast read INDEPENDENTLY at a deep magnitude,
 *       one cell each, because this line has a documented history of one leg moving while the
 *       suite stayed green.</li>
 * </ul>
 *
 * <p><b>CI STATUS OF THE DELIBERATELY-RED ROWS — ✅ INVERTED BY STAGE 1 (the window widening).</b>
 * As written for Stage 0 the window rows were CHARACTERISATION: they asserted what the radius-1
 * window actually did <em>including its misses</em>, so the suite stayed green and the RED was
 * documented rather than thrown, and each such assertion named in its message the exact condition
 * Stage 1 had to invert. Stage 1 widened {@code SlabbedOffsetRaycast.consumeCell} to
 * {@code SlabbedOffsetRaycast.WINDOW_RADIUS} and those rows went RED exactly as designed; they are
 * now inverted in place, so the same cells pin the ±2 behaviour that the widening bought. <b>No
 * row was deleted and no positive control was touched</b> — every measurement in this file is
 * still paired, in the same cell, with a {@code -1.0} control, so a broken aim still cannot
 * masquerade as a finding. The rows that were never about the window (B and C) are untouched.
 *
 * <p><b>The radius is no longer written down here.</b> {@link #TODAYS_WINDOW_RADIUS} now READS
 * {@code SlabbedOffsetRaycast.WINDOW_RADIUS}, which is itself derived from that class's
 * {@code DEEPEST_TARGETABLE_DY}. A third copy of the number is exactly how these two would drift
 * apart again.
 */
public final class DeepDyWindowCharacterisationTest {

    private static final double EPS = 1.0e-6;

    /**
     * The radius {@code SlabbedOffsetRaycast.consumeCell} actually searches — READ from the
     * production constant, never restated. Stage 1 moved it from 1 to 2.
     */
    private static final int TODAYS_WINDOW_RADIUS = SlabbedOffsetRaycast.WINDOW_RADIUS;

    // ──────────────────────────────────────────────────────────────────────────
    // A. THE ±1 PICK WINDOW
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * A-1 — <b>MEASURES the survey's central claim: a BOTTOM slab at {@code -1.5} occupies only
     * {@code P.y-2} and cannot be attributed to its owner.</b>
     *
     * <p><b>As measured under the radius-1 window (Stage 0):</b> the claim was CONFIRMED for a ray
     * travelling in the shape's own cell layer (the ordinary crosshair aim at the block's visible
     * body from the side) and REFUTED as stated for a ray that also passes through the owner cell
     * {@code P} — the DDA tests {@code P} as a PRIMARY cell there, and a primary cell is tested
     * regardless of its offset. "Untargetable" was therefore too strong; "untargetable from the
     * side, where the player is actually looking" was the measured truth, and it is the case the
     * existing suite pins at every shallower magnitude ({@code OffsetRaycastTargetingTest} cells 2,
     * 5 and 8).
     *
     * <p><b>Under the radius-2 window Stage 1 shipped, the side aim is recovered</b> — the layer the deep slab occupies is 2 cells from its owner, and 2 is now
     * inside the window. The side-ray row below is inverted accordingly; the geometry rows above it
     * (which layer, which required radius) are measurements of the shape and did not move.
     *
     * <p>Positive control in the same cell, untouched by the inversion: the identical subject at
     * the resolver's {@code -1.0} cap occupies {@code P.y-1}, needs radius 1, and IS hit by the
     * same side aim.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deepBottomSlabIsUnreachableFromItsOwnLayerUnderTheRadiusOneWindow(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        // CONTROL: -1.0, the current cap. Layer P.y-1, distance 1, inside the window.
        BlockPos control = forceStoredDy(ctx, origin.add(2, 5, 3), bottomSlab(Blocks.OAK_SLAB), -1.0);
        Span controlSpan = span(w, control);
        int controlNeed = controlSpan.requiredWindowRadius();
        boolean controlHit = sideRayHits(w, origin, control, controlSpan.aimYInLayer(controlSpan.lowLayer));
        System.out.println("[STAGE0-A] control bottom_slab dy=-1.0 span=" + controlSpan
                + " requiredRadius=" + controlNeed + " sideRayHitsOwner=" + controlHit);
        ctx.assertTrue(controlNeed == 1,
                "control: a bottom slab at -1.0 must occupy exactly one layer one cell below its "
                        + "owner (requiredRadius 1), measured " + controlNeed + " — " + controlSpan);
        ctx.assertTrue(controlHit,
                "control: the radius-1 window MUST attribute a -1.0 bottom slab from a side ray in "
                        + "its own layer. A miss here means the aim is wrong and the deep row below "
                        + "proves nothing — " + controlSpan);

        // SUBJECT: -1.5. Layer P.y-2, distance 2, OUTSIDE the window. Its own column, so the
        // control above can never intercept the vertical probe.
        BlockPos deep = forceStoredDy(ctx, origin.add(5, 2, 3), bottomSlab(Blocks.OAK_SLAB), -1.5);
        Span deepSpan = span(w, deep);
        int deepNeed = deepSpan.requiredWindowRadius();
        double sideAim = deepSpan.aimYInLayer(deepSpan.lowLayer);
        boolean deepSideHit = sideRayHits(w, origin, deep, sideAim);
        boolean deepDownHit = downRayHits(w, deep);
        System.out.println("[STAGE0-A] subject bottom_slab dy=-1.5 span=" + deepSpan
                + " requiredRadius=" + deepNeed + " sideAimY=" + sideAim
                + " sideRayHitsOwner=" + deepSideHit + " downRayHitsOwner=" + deepDownHit);

        ctx.assertTrue(deepSpan.lowLayer == deepSpan.highLayer && deepSpan.lowLayer == deep.getY() - 2,
                "MEASUREMENT A: a BOTTOM slab at -1.5 must occupy exactly the single cell layer "
                        + "P.y-2 (this is the survey's premise, measured from the real offset "
                        + "outline) — " + deepSpan);
        ctx.assertTrue(deepNeed == 2,
                "MEASUREMENT A: attributing that layer to its owner needs a window radius of 2; "
                        + "today's radius is " + TODAYS_WINDOW_RADIUS + " — " + deepSpan);

        ctx.assertTrue(deepSideHit,
                "INVERTED BY STAGE 1 (was: assertFalse, pinning the radius-1 DEFECT). A side ray "
                        + "travelling through the -1.5 bottom slab's own body at y=" + sideAim
                        + " marches only cell layer " + deepSpan.lowLayer + ", which is 2 cells "
                        + "from the owner at P.y=" + deep.getY() + ". Under the radius-"
                        + TODAYS_WINDOW_RADIUS + " window that layer's probe set reaches the owner, "
                        + "so the block the player is looking at IS hit. A RED here means the "
                        + "widening did not take — this assertion is the one that proves it did.");

        ctx.assertTrue(deepDownHit,
                "MEASUREMENT A, the survey's overstatement: 'occupies ONLY P.y-2 and is therefore "
                        + "UNTARGETABLE' is too strong. A ray that passes through the owner cell P "
                        + "at all (aiming down from above) tests P as a PRIMARY cell, and a primary "
                        + "cell is tested whatever its offset — so the deep slab IS hit from above "
                        + "today. Only the side aim is lost.");
        ctx.complete();
    }

    /**
     * A-2 — <b>MEASURES the survey's second claim: a full block at {@code -1.5} spans
     * {@code P.y-2..P.y-1}, so grazing rays miss it.</b> Measured verdict under the radius-1 window
     * (Stage 0): PARTLY CONFIRMED. The block occupies two layers; the UPPER one ({@code P.y-1}) was
     * inside the radius-1 window and WAS hit, the LOWER one ({@code P.y-2}) was outside it and was
     * NOT. So the subject was never invisible — it was a block whose bottom half could not be
     * clicked, a distinct (and arguably worse to diagnose) defect from one that cannot be clicked
     * at all.
     *
     * <p><b>Stage 1's radius-2 window closes the split</b>: both layers are now within the window,
     * so both halves are clickable. The lower-layer row is inverted accordingly. The upper-layer
     * row is the aim's positive control and is unchanged — it passed before and must still pass.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deepFullBlockLosesOnlyItsLowerLayerUnderTheRadiusOneWindow(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        BlockPos deep = forceStoredDy(ctx, origin.add(3, 4, 3), Blocks.STONE.getDefaultState(), -1.5);
        Span s = span(w, deep);
        double lowAim = s.aimYInLayer(s.lowLayer);
        double highAim = s.aimYInLayer(s.highLayer);
        boolean lowHit = sideRayHits(w, origin, deep, lowAim);
        boolean highHit = sideRayHits(w, origin, deep, highAim);
        System.out.println("[STAGE0-A] subject stone dy=-1.5 span=" + s
                + " requiredRadius=" + s.requiredWindowRadius()
                + " lowAimY=" + lowAim + " lowLayerHit=" + lowHit
                + " highAimY=" + highAim + " highLayerHit=" + highHit);

        ctx.assertTrue(s.lowLayer == deep.getY() - 2 && s.highLayer == deep.getY() - 1,
                "MEASUREMENT A: a full block at -1.5 must span exactly the two layers P.y-2 and "
                        + "P.y-1 (the survey's premise, measured) — " + s);
        ctx.assertTrue(s.requiredWindowRadius() == 2,
                "MEASUREMENT A: its deepest layer needs radius 2 — " + s);

        ctx.assertTrue(highHit,
                "MEASUREMENT A: the UPPER layer P.y-1 is one cell from the owner, so the radius-1 "
                        + "window already attributes it — a grazing ray at y=" + highAim + " hits. "
                        + "This is also the positive control for the aim.");
        ctx.assertTrue(lowHit,
                "INVERTED BY STAGE 1 (was: assertFalse, pinning the radius-1 DEFECT — the block's "
                        + "bottom half was unclickable). A grazing ray through the LOWER half of "
                        + "the same block at y=" + lowAim + " marches only layer " + s.lowLayer
                        + ", two cells from the owner; the radius-" + TODAYS_WINDOW_RADIUS
                        + " window reaches that far, so both halves of the block are now clickable "
                        + "and the split-clickability defect is closed.");
        ctx.complete();
    }

    /**
     * A-3 — <b>the ruling's derivation, turned into a measurement.</b> Maintainer ruled the cap at
     * {@code -2.0} rather than {@code -1.5} so that {@code MIN_RESOLVED_DY == -(window radius)}
     * stays derivable. This cell measures the required radius for every subject shape at each
     * candidate cap and pins the identity, so the constant is never magic again.
     *
     * <p>Measured: at {@code -1.0} the deepest occupied layer is 1 cell below the owner; at both
     * {@code -1.5} and {@code -2.0} it is 2. A radius-2 window is therefore <em>necessary</em> for
     * {@code -1.5} and <em>exactly sufficient</em> for {@code -2.0} — which is the whole of the
     * "stopping at -1.5 pays the entire window cost and leaves the constant magic" argument, now
     * measured rather than asserted.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void requiredWindowRadiusIsTheNegatedCapForEverySubjectShape(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        double[] caps = {-1.0, -1.5, -2.0};
        int[] expected = {1, 2, 2};
        StringBuilder table = new StringBuilder();
        for (int i = 0; i < caps.length; i++) {
            BlockPos slabPos = forceStoredDy(ctx, origin.add(2, 1 + i * 2, 3),
                    bottomSlab(Blocks.OAK_SLAB), caps[i]);
            BlockPos cubePos = forceStoredDy(ctx, origin.add(5, 1 + i * 2, 3),
                    Blocks.STONE.getDefaultState(), caps[i]);
            Span slabSpan = span(w, slabPos);
            Span cubeSpan = span(w, cubePos);
            int need = Math.max(slabSpan.requiredWindowRadius(), cubeSpan.requiredWindowRadius());
            table.append(" cap=").append(caps[i])
                    .append(" slab").append(slabSpan).append("/r").append(slabSpan.requiredWindowRadius())
                    .append(" cube").append(cubeSpan).append("/r").append(cubeSpan.requiredWindowRadius())
                    .append(" max=").append(need).append(";");
            ctx.assertTrue(need == expected[i],
                    "MEASUREMENT A: cap " + caps[i] + " needs window radius " + expected[i]
                            + ", measured " + need + " —" + table);
        }
        System.out.println("[STAGE0-A] required-radius table:" + table);

        ctx.assertTrue(expected[2] == 2,
                "the identity MIN_RESOLVED_DY == -(window radius) holds at -2.0 with radius 2 —"
                        + table);

        // INVERTED BY STAGE 1 (was: assertTrue(TODAYS_WINDOW_RADIUS == 1), "which is why
        // MIN_RESOLVED_DY is -1.0"). The window now stands at the radius the RULED cap needs, ahead
        // of the alphabet that will use it — so the identity holds as an INEQUALITY during stages
        // 1-3 and closes to equality at Stage 4. Both halves are asserted: the radius is the one
        // the ruled cap derives, and it is not SHALLOWER than the cap the resolver may produce.
        ctx.assertTrue(TODAYS_WINDOW_RADIUS
                        == (int) Math.ceil(-SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY),
                "STAGE 1: the shipping window radius must be the one DERIVED from the window's own "
                        + "cap (" + SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY + " -> radius "
                        + (int) Math.ceil(-SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY) + "), "
                        + "measured " + TODAYS_WINDOW_RADIUS + " —" + table);
        ctx.assertTrue(TODAYS_WINDOW_RADIUS == 2,
                "STAGE 1: the ruled cap is -2.0 and the measured required radius at -2.0 is 2, so "
                        + "the shipping radius must be 2 — measured " + TODAYS_WINDOW_RADIUS
                        + ". (It was 1 before Stage 1; this row is the inverted Stage 0 row.) —"
                        + table);
        ctx.complete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // B. MAX_SUPPORT_RESOLVE_DEPTH
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * B-1 — <b>how far the depth budget carries in a PRE-STORE world</b> (anchors present, no
     * stored heights: the shape of every world saved before {@code d4f38510}). The store is written
     * by {@code addAnchor} and then explicitly CLEARED here, which is the same
     * "anchor kept / store cleared" counterfactual {@code LAW.md}'s reachability audit used.
     *
     * <p>This is the only tower shape that actually consumes the budget: with a stored fact present
     * the walk terminates at depth 1 on every course (B-2), so {@code MAX_SUPPORT_RESOLVE_DEPTH} is
     * never reached at all in a post-store world.
     *
     * <p>The ladder is RECORDED, not predicted — the raw values are printed under
     * {@code [STAGE0-B]} before any assertion runs.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void preStoreAnchoredTowerLadderMeasuredToSixCourses(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        BlockPos[] level = new BlockPos[6];
        for (int i = 0; i < level.length; i++) {
            level[i] = ground.up(i + 1);
            w.setBlockState(level[i], bottomSlab(Blocks.OAK_SLAB), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(w, level[i], w.getBlockState(level[i]));
        }
        // PRE-STORE SHAPE: keep every anchor, drop every stored height.
        for (BlockPos p : level) {
            SlabPlacementDyAttachment.clear(w, p);
            ctx.assertFalse(SlabPlacementDyAttachment.hasStoredDy(w, p),
                    "fixture: this cell measures the PRE-STORE lane — " + p + " must hold no fact");
        }

        double[] dy = new double[level.length];
        StringBuilder ladder = new StringBuilder();
        for (int i = 0; i < level.length; i++) {
            dy[i] = SlabSupport.getYOffset(w, level[i], w.getBlockState(level[i]));
            ladder.append(" L").append(i).append('=').append(dy[i])
                    .append('(').append(SlabAnchorAttachment.isAnchored(w, level[i]) ? "A" : "-").append(')');
        }
        System.out.println("[STAGE0-B] pre-store anchored ladder:" + ladder);

        ctx.assertTrue(Math.abs(dy[0]) <= EPS, "pre-store L0 must be 0.0 —" + ladder);
        ctx.assertTrue(Math.abs(dy[1] + 0.5) <= EPS, "pre-store L1 must be -0.5 —" + ladder);
        for (int i = 2; i < level.length; i++) {
            ctx.assertTrue(Math.abs(dy[i] + 1.0) <= EPS,
                    "MEASUREMENT B: pre-store L" + i + " must saturate at the MIN_RESOLVED_DY clamp "
                            + "(-1.0) —" + ladder);
        }

        // THE MEASURED ANSWER TO "WHAT DOES EXHAUSTION RETURN": it returns a bare -0.5, and under
        // MIN_RESOLVED_DY = -1.0 that is PROVABLY INVISIBLE. Each course computes
        // max(childValue - 0.5, -1.0); the exhaustion floor -0.5 is exactly the largest child value
        // for which the parent still reads -1.0, so substituting it for any true child value
        // <= -0.5 cannot change any parent. L5's read is the first that reaches depth 4 (one depth
        // per course, entered at 0) and its value is byte-identical to L2's, L3's and L4's.
        ctx.assertTrue(Math.abs(dy[5] - dy[2]) <= EPS,
                "MEASUREMENT B: the exhaustion path is unobservable at this clamp — L5 (whose walk "
                        + "reaches depth 4 and takes the bare -0.5 floor) must read exactly what L2 "
                        + "(whose walk does not) reads. It stops being unobservable the moment "
                        + "MIN_RESOLVED_DY goes past -1.0, which is Stage 3's whole point —" + ladder);
        ctx.complete();
    }

    /**
     * B-2 — <b>the depth budget is never consumed while the placement store answers.</b> Every
     * course of a real (player-placed) tower carries a stored height, and
     * {@code loweredBottomSlabSupportDy → anchoredCellDy} returns that stored number at depth 1,
     * so the walk terminates one level down whatever the tower's height. This is why
     * {@code MAX_SUPPORT_RESOLVE_DEPTH} has no observable effect in a post-store world, and it is
     * the fact that makes B-1's pre-store shape the only place the budget can be measured at all.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void supportDepthBudgetIsNeverConsumedWhileTheStoreAnswers(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        BlockPos[] level = new BlockPos[6];
        StringBuilder facts = new StringBuilder();
        for (int i = 0; i < level.length; i++) {
            level[i] = ground.up(i + 1);
            w.setBlockState(level[i], bottomSlab(Blocks.OAK_SLAB), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(w, level[i], w.getBlockState(level[i]));
        }
        for (int i = 0; i < level.length; i++) {
            double live = SlabSupport.getYOffset(w, level[i], w.getBlockState(level[i]));
            double stored = SlabPlacementDyAttachment.storedDy(w, level[i]);
            facts.append(" L").append(i).append(" dy=").append(live).append(" stored=").append(stored);
        }
        System.out.println("[STAGE0-B] stored-lane ladder:" + facts);

        for (int i = 2; i < level.length; i++) {
            double stored = SlabPlacementDyAttachment.storedDy(w, level[i]);
            ctx.assertTrue(Math.abs(stored + 1.0) <= EPS,
                    "MEASUREMENT B: L" + i + " must carry a STORED height of -1.0 — the walk that "
                            + "produced it terminated at depth 1 on L" + (i - 1) + "'s stored fact, "
                            + "so no course of a post-store tower ever spends the depth budget —"
                            + facts);
        }
        ctx.complete();
    }

    /**
     * B-3 — <b>at a deep magnitude the CLAMP, not the depth budget, is the binding constraint.</b>
     * A real anchored tower course is given a stored {@code -2.0} (the ruling's proposed cap) and
     * the course above it is asked to seat on it. LAW 1 hands the support its {@code -2.0} back
     * verbatim; the follower asks for the seat, gets {@code -2.5}, and {@code MIN_RESOLVED_DY}
     * flattens it to {@code -1.0}. The depth budget is not involved — the walk terminates at depth
     * 1 on the support's stored fact.
     *
     * <p>Measured consequence: the follower ends up a full block above where it should rest, with
     * a measured hole between the two. That number is what Stage 4 has to answer for, and it is
     * why the clamp must move WITH the alphabet rather than after it.
     *
     * <p>The support must be ANCHORED for this cell to measure anything — see the sibling cell
     * {@link #storedHeightOnAnUnanchoredBottomSlabIsInvisibleToItsFollower}, which measures what
     * happens when it is not, and which is a separate finding entirely.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deepStoredSeatIsFlattenedByTheClampNotByTheDepthBudget(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        // A real three-course tower, placed exactly as a player click would (setBlockState +
        // addAnchor), so the support genuinely earns an anchor through the production qualifiers.
        BlockPos[] level = new BlockPos[3];
        for (int i = 0; i < level.length; i++) {
            level[i] = ground.up(i + 1);
            w.setBlockState(level[i], bottomSlab(Blocks.OAK_SLAB), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(w, level[i], w.getBlockState(level[i]));
        }
        BlockPos support = level[1];
        BlockPos follower = level[2];
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, support),
                "fixture: the support must be genuinely anchored, or the seat resolver never reads "
                        + "its stored height at all");

        // Re-point both cells at the deep magnitude: the support holds -2.0, the follower must
        // re-resolve rather than answer from the height it was placed at.
        SlabPlacementDyAttachment.clear(w, support);
        SlabPlacementDyAttachment.clear(w, follower);
        ctx.assertTrue(SlabPlacementDyAttachment.record(w, support, -2.0),
                "fixture: the store must accept -2.0 (32 sixteenths, inside a signed byte)");
        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        ctx.assertTrue(Math.abs(supportDy + 2.0) <= EPS,
                "LAW 1: a stored -2.0 must be returned verbatim, got " + supportDy);

        double followerDy = SlabSupport.getYOffset(w, follower, w.getBlockState(follower));
        double supportTopY = support.getY() + 0.5 + supportDy;   // bottom slab: local top face 0.5
        double followerBottomY = follower.getY() + followerDy;   // bottom slab: local bottom 0.0
        double gap = followerBottomY - supportTopY;
        System.out.println("[STAGE0-B] deep seat: supportDy=" + supportDy + " followerDy=" + followerDy
                + " supportTopY=" + supportTopY + " followerBottomY=" + followerBottomY
                + " gap=" + gap);

        ctx.assertTrue(Math.abs(followerDy + 1.0) <= EPS,
                "MEASUREMENT B: the follower's raw seat is -2.5 and MIN_RESOLVED_DY flattens it to "
                        + "-1.0, got " + followerDy + ". The depth budget is not involved: the walk "
                        + "terminated at depth 1 on the support's stored fact.");
        ctx.assertTrue(Math.abs(gap - 1.5) <= EPS,
                "MEASUREMENT B: the clamp opens a measured " + gap + "-block hole between a -2.0 "
                        + "support's top face and the block resting on it. Stage 4 cannot ship the "
                        + "deeper alphabet without moving MIN_RESOLVED_DY in the same change.");
        ctx.complete();
    }

    /**
     * B-4 — <b>FOUND WHILE MEASURING B-3, and reported rather than smoothed over: a stored
     * placement height on an UNANCHORED bottom slab is honoured for the cell itself and is
     * INVISIBLE to anything resting on it.</b>
     *
     * <p>{@code getYOffsetInner} reads the store at the top of its body for every cell, anchored or
     * not — that is deliberate, and its comment says lane B's cells (lowered placements that earn no
     * anchor, whose height {@code freezeLoweredOnPlace} records WITHOUT one) must be able to answer
     * from it. {@code cellTopSupportDy}, the FULL-HEIGHT seat mirror, reads the store the same way
     * and for the same stated reason. But {@code loweredBottomSlabSupportDy} — the HALF-HEIGHT seat
     * mirror, the one every bottom-slab support goes through — reads it only behind an
     * {@code isAnchored} gate, and answers {@code 0.0} otherwise.
     *
     * <p>So the two seat arms disagree about the same kind of fact: this is the shared-predicate
     * half-fix shape, standing in the tree today. It is product-reachable —
     * {@code freezeLoweredOnPlace} mints stored-without-anchor cells on purpose — and it gets worse
     * as the alphabet deepens, because the error is the whole stored magnitude.
     *
     * <p>This cell PINS THE CURRENT BEHAVIOUR so the gap is visible and cannot regress unnoticed.
     * It is not an endorsement, and it is not fixed here: fixing it is a production change and
     * therefore not Stage 0.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void storedHeightOnAnUnanchoredBottomSlabIsInvisibleToItsFollower(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos support = origin.add(2, 4, 2);

        w.setBlockState(support, bottomSlab(Blocks.STONE_SLAB), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabPlacementDyAttachment.record(w, support, -2.0),
                "fixture: the store must accept -2.0 at the support");
        ctx.assertFalse(SlabAnchorAttachment.isAnchored(w, support),
                "fixture: this cell is the lane-B shape — a stored height with NO anchor");

        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        ctx.assertTrue(Math.abs(supportDy + 2.0) <= EPS,
                "LAW 1: the support itself must read its stored -2.0, got " + supportDy);

        BlockPos follower = support.up();
        w.setBlockState(follower, bottomSlab(Blocks.OAK_SLAB), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, follower, w.getBlockState(follower));
        double followerDy = SlabSupport.getYOffset(w, follower, w.getBlockState(follower));
        double supportTopY = support.getY() + 0.5 + supportDy;
        double followerBottomY = follower.getY() + followerDy;
        System.out.println("[STAGE0-B] lane-B seat: supportDy=" + supportDy
                + " supportAnchored=" + SlabAnchorAttachment.isAnchored(w, support)
                + " followerDy=" + followerDy
                + " gap=" + (followerBottomY - supportTopY));

        ctx.assertTrue(Math.abs(followerDy + 0.5) <= EPS,
                "CHARACTERISATION (pins a DEFECT): the follower reads -0.5, the answer it would "
                        + "give for a support at 0.0 — loweredBottomSlabSupportDy never asked the "
                        + "store because the support carries no anchor, so a 2.0-block-deep support "
                        + "is seen as flush. Got " + followerDy + ". The FULL-HEIGHT arm "
                        + "(cellTopSupportDy) reads the store with no anchor gate; only the "
                        + "HALF-HEIGHT arm does not.");
        ctx.assertTrue(followerBottomY - supportTopY > 1.0 + EPS,
                "CHARACTERISATION: the resulting hole is " + (followerBottomY - supportTopY)
                        + " blocks — the whole stored magnitude is lost, not a rounding of it.");
        ctx.complete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // C. THE TRIAD AT A DEEP MAGNITUDE — three legs, three cells, read independently
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * C-1, MODEL LEG — {@code OffsetBlockStateModel.emitQuads} offsets its quads by exactly
     * {@code ClientDy.dyFor(view, pos, state)}. That call is a pure delegate to
     * {@code SlabSupport.getVisualYOffset} and is fully readable headlessly, so the model leg's
     * NUMBER is measurable here even though the rendered mesh is not.
     *
     * <p><b>SECOND FINDING, reached independently of the sibling live-diagnostic session and
     * agreeing with it:</b> {@code SlabbedDiagnostics.analyze(world, pos, state)} — the
     * two-argument overload every headless caller uses — supplies no model sample at all, and
     * {@code modelMismatch} is {@code false} for that non-value by construction. So the
     * diagnostic's MODEL leg is not merely unreadable in a gametest, it is silently UNCHECKED: the
     * flag can never fire there. This cell pins both halves — the number IS available headlessly
     * via the same call the renderer makes ({@code ClientDy.dyFor}), and the diagnostic does not
     * use it. Asserted through {@code Double.isNaN} rather than a named sentinel so the cell holds
     * whichever way the diagnostic's "no sample" value is spelled.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void triadModelLegAtADeepMagnitude(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = forceStoredDy(ctx, ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 3, 2),
                bottomSlab(Blocks.OAK_SLAB), -1.5);
        BlockState state = w.getBlockState(pos);

        double modelDy = ClientDy.dyFor(w, pos, state);
        SlabbedDiagnostics.Sample headless = SlabbedDiagnostics.analyze(w, pos, state);
        System.out.println("[STAGE0-C] model leg: ClientDy.dyFor=" + modelDy
                + " diagnostics.modelDy=" + headless.modelDy()
                + " diagnostics.modelMismatch=" + headless.modelMismatch()
                + " diagnostics.visualDy=" + headless.visualDy());

        ctx.assertTrue(Math.abs(modelDy + 1.5) <= EPS,
                "MEASUREMENT C, model leg: the value the chunk mesh offsets by must be -1.5 at a "
                        + "deep stored height, got " + modelDy);
        ctx.assertTrue(Double.isNaN(headless.modelDy()),
                "MEASUREMENT C, second finding: the headless diagnostic reports NO model sample by "
                        + "construction (the two-argument analyze supplies none), got "
                        + headless.modelDy());
        ctx.assertFalse(headless.modelMismatch(),
                "MEASUREMENT C, second finding: modelMismatch can NEVER fire headlessly, because "
                        + "NaN short-circuits it — the model leg of the triad is unchecked in every "
                        + "headless diagnostic, not just unreadable. Any future fix that computes "
                        + "modelDy server-side must flip this assertion.");
        ctx.complete();
    }

    /**
     * C-2, OUTLINE LEG — read straight off {@code AbstractBlockState.getOutlineShape}, which
     * {@code SlabSupportStateMixin.slabbed$offsetOutline} offsets by {@code getVisualYOffset}.
     * Asserted independently of C-1 and C-3 on purpose.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void triadOutlineLegAtADeepMagnitude(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = forceStoredDy(ctx, ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 3, 2),
                bottomSlab(Blocks.OAK_SLAB), -1.5);
        BlockState state = w.getBlockState(pos);

        VoxelShape outline = state.getOutlineShape(w, pos, ShapeContext.absent());
        ctx.assertFalse(outline.isEmpty(), "fixture: a bottom slab's outline must be non-empty");
        Box box = outline.getBoundingBox();
        System.out.println("[STAGE0-C] outline leg: minY=" + box.minY + " maxY=" + box.maxY);

        ctx.assertTrue(Math.abs(box.minY + 1.5) <= EPS,
                "MEASUREMENT C, outline leg: the offset outline's minY must equal the deep dy "
                        + "(-1.5), got " + box.minY);
        ctx.assertTrue(Math.abs(box.maxY + 1.0) <= EPS,
                "MEASUREMENT C, outline leg: a bottom slab is half a block tall, so its offset "
                        + "outline must end at -1.0, got " + box.maxY);
        ctx.complete();
    }

    /**
     * C-3, RAYCAST LEG — and the leg that <b>cannot be read the way the diagnostic reads it</b>.
     *
     * <p>{@code SlabbedDiagnostics} measures this leg as
     * {@code minY(state.getRaycastShape(world, pos))}, and vanilla's default raycast shape is
     * EMPTY for ordinary blocks — {@code world.raycastBlock} falls back to the outline. An empty
     * shape has no bounding box, so the diagnostic's helper answers {@code NaN}. That is the same
     * {@code NaN} the sibling live-diagnostic session is chasing, reached from a second direction:
     * for {@code modelDy} it is hard-coded, for {@code raycastMinY} it is vanilla's default shape —
     * <b>neither is evidence of a dy defect, and neither leg is actually being verified.</b>
     *
     * <p>So this cell reads the raycast leg the only way that is honest: it fires a real
     * {@code SlabbedOffsetRaycast} at the subject and asserts the hit lands on the offset geometry.
     * The raw {@code getRaycastShape} reading is printed, and constrained by a conditional
     * assertion so that a future non-empty shape which DISAGREES with the dy still fails.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void triadRaycastLegAtADeepMagnitude(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = forceStoredDy(ctx, ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 3, 2),
                bottomSlab(Blocks.OAK_SLAB), -1.5);
        BlockState state = w.getBlockState(pos);

        VoxelShape raw = state.getRaycastShape(w, pos);
        boolean rawEmpty = raw.isEmpty();
        double rawMinY = rawEmpty ? Double.NaN : raw.getBoundingBox().minY;

        BlockHitResult hit = downRay(w, pos);
        double hitY = hit.getType() == HitResult.Type.BLOCK ? hit.getPos().y : Double.NaN;
        System.out.println("[STAGE0-C] raycast leg: getRaycastShape.isEmpty=" + rawEmpty
                + " rawMinY=" + rawMinY + " nearestHitType=" + hit.getType()
                + " nearestHitPos=" + hit.getBlockPos() + " hitY=" + hitY);

        ctx.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos),
                "MEASUREMENT C, raycast leg: a ray down the subject's own column must hit it, got "
                        + hit.getType() + " " + hit.getBlockPos());
        ctx.assertTrue(Math.abs(hitY - (pos.getY() - 1.0)) <= 1.0e-4,
                "MEASUREMENT C, raycast leg: the hit must land on the OFFSET top face at "
                        + (pos.getY() - 1.0) + " (dy -1.5 + the slab's own 0.5 height), got " + hitY
                        + " — this is the only reading of the raycast leg that is not NaN");
        ctx.assertTrue(rawEmpty || Math.abs(rawMinY + 1.5) <= EPS,
                "MEASUREMENT C, raycast leg: if getRaycastShape ever stops being empty it must "
                        + "agree with the dy (-1.5), got minY=" + rawMinY);
        ctx.complete();
    }

    // ------------------------------------------------------------------------
    // helpers

    private static BlockState bottomSlab(Block slab) {
        return slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /**
     * Places {@code state} at {@code pos} and writes {@code dy} straight into the placement store,
     * then hard-asserts that {@code getYOffset} returns it verbatim. This bypasses the resolver and
     * its clamp WITHOUT changing either — {@code getYOffsetInner} reads the store first, which is
     * LAW 1 itself.
     */
    private static BlockPos forceStoredDy(TestContext ctx, BlockPos pos, BlockState state, double dy) {
        ServerWorld w = ctx.getWorld();
        w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabPlacementDyAttachment.record(w, pos, dy),
                "fixture: the placement store must accept dy=" + dy + " at " + pos);
        double read = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        ctx.assertTrue(Math.abs(read - dy) <= EPS,
                "fixture: LAW 1 — a stored placement height must be returned verbatim; wrote " + dy
                        + ", read " + read + ". Without this the cell measures nothing.");
        return pos;
    }

    /** The subject's REAL offset outline, expressed in world Y plus the cell layers it occupies. */
    private record Span(int ownerY, double worldMinY, double worldMaxY, int lowLayer, int highLayer) {
        /** The smallest {@code consumeCell} window radius that can attribute every layer to the owner. */
        int requiredWindowRadius() {
            return Math.max(Math.abs(lowLayer - ownerY), Math.abs(highLayer - ownerY));
        }

        /** A Y inside both {@code layer} and the shape — the aim for a ray confined to that layer. */
        double aimYInLayer(int layer) {
            double lo = Math.max(worldMinY, layer);
            double hi = Math.min(worldMaxY, layer + 1.0);
            return (lo + hi) / 2.0;
        }

        @Override
        public String toString() {
            return "[worldY " + worldMinY + ".." + worldMaxY + " layers " + lowLayer + ".." + highLayer
                    + " ownerY " + ownerY + "]";
        }
    }

    private static Span span(ServerWorld w, BlockPos pos) {
        VoxelShape outline = w.getBlockState(pos).getOutlineShape(w, pos, ShapeContext.absent());
        Box box = outline.getBoundingBox();
        double worldMin = pos.getY() + box.minY;
        double worldMax = pos.getY() + box.maxY;
        int lo = (int) Math.floor(worldMin + EPS);
        int hi = (int) Math.ceil(worldMax - EPS) - 1;
        return new Span(pos.getY(), worldMin, worldMax, lo, hi);
    }

    /**
     * A horizontal ray through the subject's column at {@code aimY} — the ordinary side aim.
     *
     * <p>The span is expressed PLOT-RELATIVE ({@code origin.z + 0.5} to {@code origin.z + 7.5}),
     * exactly like {@code OffsetRaycastTargetingTest}'s grazing cells. Minecraft's gametest
     * framework encloses each test area in a barrier box; a ray that starts outside it hits the
     * wall and every subject reads as a miss, which is a fixture defect that would masquerade as
     * the very finding these cells exist to measure.
     */
    private static boolean sideRayHits(ServerWorld w, BlockPos origin, BlockPos pos, double aimY) {
        Vec3d eye = new Vec3d(pos.getX() + 0.5, aimY, origin.getZ() + 0.5);
        Vec3d end = new Vec3d(pos.getX() + 0.5, aimY, origin.getZ() + 7.5);
        BlockHitResult hit = SlabbedOffsetRaycast.raycast(w, eye, end, ShapeContext.absent());
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    /** A vertical ray down the subject's own column — passes through the owner cell itself. */
    private static BlockHitResult downRay(ServerWorld w, BlockPos pos) {
        Vec3d eye = new Vec3d(pos.getX() + 0.5, pos.getY() + 4.0, pos.getZ() + 0.5);
        Vec3d end = new Vec3d(pos.getX() + 0.5, pos.getY() - 4.0, pos.getZ() + 0.5);
        return SlabbedOffsetRaycast.raycast(w, eye, end, ShapeContext.absent());
    }

    private static boolean downRayHits(ServerWorld w, BlockPos pos) {
        BlockHitResult hit = downRay(w, pos);
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }
}
