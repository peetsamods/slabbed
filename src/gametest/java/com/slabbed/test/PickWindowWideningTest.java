package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
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

import java.util.ArrayList;
import java.util.List;

/**
 * STAGE 1 — the pick window widens from radius 1 to {@link SlabbedOffsetRaycast#WINDOW_RADIUS},
 * and this file is the two things that widening owes:
 *
 * <ol>
 *   <li><b>A behaviour-neutrality MEASUREMENT, not an argument.</b> The class doc of
 *       {@code SlabbedOffsetRaycast} argues that at today's alphabet the outer ring of the window
 *       can only reach positions whose shape the ray does not intersect. This file runs one
 *       identical battery of rays through both radii over the same scene and compares every field
 *       of every result. An argument that is only written down is the shape of defect this line
 *       has shipped before.</li>
 *   <li><b>A permanent perf gate on the pick path.</b> The cost being accepted is real and
 *       forever: the crosshair raycast now probes five cells per marched DDA cell instead of three,
 *       +66%. The gate is not here to say that cost is acceptable — that is a
 *       frame-time question and only Maintainer's client can answer it. It is here so that a later
 *       change cannot quietly make it <em>worse</em> than what was signed off. This project has
 *       shipped a perf regression twice; the documented instrument for it is a counting gametest,
 *       never a wall clock.</li>
 * </ol>
 *
 * <p><b>Both cells share one scene and one ray battery</b> ({@link #buildScene},
 * {@link #rayBattery}) so the cost numbers are measured on exactly the geometry the neutrality
 * comparison covers, and neither can drift from the other.
 *
 * <p><b>Why comparing against radius 1 is legitimate and not a re-implementation.</b>
 * {@code SlabbedOffsetRaycast.raycastWithWindow} runs the production collector with an explicit
 * radius; radius 1 is literally the code that shipped before this stage, not a test-local model of
 * it. Production always goes through {@code raycast}, which fixes the radius at
 * {@code WINDOW_RADIUS}.
 */
public final class PickWindowWideningTest {

    private static final double EPS = 1.0e-6;

    /** The radius this stage replaces — what {@code consumeCell} tested before the widening. */
    private static final int PREVIOUS_WINDOW_RADIUS = 1;

    // ── The sign-off baseline ────────────────────────────────────────────────────────────────
    // MEASURED on the scene and battery in this file (258 rays, 2130 marched DDA cells) at the
    // moment Stage 1 was written. Not derived, not predicted: the [STAGE1-PERF] line prints
    // exactly these. They are pinned as ceilings so that a later change to the pick path — a new
    // probe, a weakened memo, a resolver that stops caching — trips a RED instead of being
    // absorbed silently. Raising one is a deliberate act that needs a new frame-time sign-off.
    //
    // The three counters grow very differently, and that is the finding this cell exists to keep
    // visible rather than to summarise away:
    //
    //   shape raycasts  393 ->  508  (x1.29) — the widening's headline cost is 5 cell probes per
    //                                 marched cell where there were 3 (+66%), but the per-ray
    //                                 de-duplication absorbs most of it: only positions that are
    //                                 non-air AND carry a non-zero dy ever reach a shape test.
    //   dy resolutions  326 ->  998  (x3.06) — THE LARGEST GROWTH, and it is NOT the ~66% figure
    //                                 the staged plan quoted. Each of these is a support-resolver
    //                                 walk. The outer ring reaches cells that the inner ring never
    //                                 touched and that the DDA never marches, so they are memo
    //                                 misses by construction; how many is a property of the scene
    //                                 (how much solid geometry sits exactly two cells off the ray),
    //                                 not a ratio anyone can derive from the radius.
    //   BlockPos allocs 4188 -> 8409 (x2.01) — tracks the neighbour probe count, which doubles
    //                                 exactly. This is the allocation-regression instrument.
    //
    // NONE OF THIS IS A FRAME-TIME RESULT. It is a count of operations on a headless server world.
    private static final long BASELINE_R1_CELLS_MARCHED = 2130L;
    private static final long BASELINE_R1_SHAPE_RAYCASTS = 393L;
    private static final long BASELINE_R1_DY_RESOLUTIONS = 326L;
    private static final long BASELINE_R1_POS_ALLOCATIONS = 4188L;

    private static final long ACCEPTED_R2_SHAPE_RAYCASTS = 508L;
    private static final long ACCEPTED_R2_DY_RESOLUTIONS = 998L;
    private static final long ACCEPTED_R2_POS_ALLOCATIONS = 8409L;

    // ──────────────────────────────────────────────────────────────────────────
    // 1. BEHAVIOUR NEUTRALITY
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * <b>The widening changes no answer at today's alphabet.</b> Every ray in the battery is run at
     * the old radius and at the new one, and the two {@link BlockHitResult}s must agree on hit
     * type, owner position, reported side and hit point — the last compared with exact
     * {@code Vec3d} equality, not a tolerance, because the two runs execute the same shape test on
     * the same shape and any difference at all would be a real difference.
     *
     * <p><b>Vacuity guards.</b> A battery that hit nothing would agree trivially, so the cell also
     * requires that the rays actually target the thing under test: a floor of block hits, and at
     * least one hit on an owner resolved to {@code -0.5} <em>and</em> at least one on an owner
     * resolved to the {@code -1.0} clamp — the deepest magnitude this build can mint, and the only
     * one whose shape leaves its owner's cell at all.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void wideningThePickWindowChangesNoAnswerAtTodaysAlphabet(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        Scene scene = buildScene(ctx, w, origin);
        List<Ray> rays = rayBattery(origin);

        int blockHits = 0;
        int hitsOnHalfLowered = 0;
        int hitsOnFullLowered = 0;
        int mismatches = 0;
        String firstMismatch = null;

        for (Ray r : rays) {
            BlockHitResult before = SlabbedOffsetRaycast.raycastWithWindow(
                    w, r.start(), r.end(), ShapeContext.absent(), PREVIOUS_WINDOW_RADIUS);
            BlockHitResult after = SlabbedOffsetRaycast.raycastWithWindow(
                    w, r.start(), r.end(), ShapeContext.absent(), SlabbedOffsetRaycast.WINDOW_RADIUS);

            boolean same = before.getType() == after.getType()
                    && before.getSide() == after.getSide()
                    && before.getBlockPos().equals(after.getBlockPos())
                    && before.getPos().equals(after.getPos())
                    && before.isInsideBlock() == after.isInsideBlock();
            if (!same) {
                mismatches++;
                if (firstMismatch == null) {
                    firstMismatch = r + " radius" + PREVIOUS_WINDOW_RADIUS + "=" + describe(before)
                            + " radius" + SlabbedOffsetRaycast.WINDOW_RADIUS + "=" + describe(after);
                }
                continue;
            }
            if (after.getType() == HitResult.Type.BLOCK) {
                blockHits++;
                BlockPos owner = after.getBlockPos();
                double dy = SlabSupport.getYOffset(w, owner, w.getBlockState(owner));
                if (Math.abs(dy + 0.5) <= EPS) {
                    hitsOnHalfLowered++;
                } else if (Math.abs(dy + 1.0) <= EPS) {
                    hitsOnFullLowered++;
                }
            }
        }

        System.out.println("[STAGE1-NEUTRALITY] scene=" + scene + " rays=" + rays.size()
                + " blockHits=" + blockHits + " hitsOn-0.5=" + hitsOnHalfLowered
                + " hitsOn-1.0=" + hitsOnFullLowered + " mismatches=" + mismatches);

        ctx.assertTrue(mismatches == 0,
                "STAGE 1 NEUTRALITY: widening the pick window from radius "
                        + PREVIOUS_WINDOW_RADIUS + " to " + SlabbedOffsetRaycast.WINDOW_RADIUS
                        + " must change NO answer while every offset the build can mint stays "
                        + "within one cell of its owner. " + mismatches + " of " + rays.size()
                        + " rays disagree; first: " + firstMismatch);

        // Vacuity: the battery has to be aimed at the geometry under test.
        ctx.assertTrue(blockHits >= rays.size() / 4,
                "vacuity guard: a battery that mostly misses would agree trivially — only "
                        + blockHits + " of " + rays.size() + " rays hit a block");
        ctx.assertTrue(hitsOnHalfLowered > 0,
                "vacuity guard: no ray in the battery hit an owner resolved to -0.5, so the "
                        + "comparison never exercised an offset shape at all");
        ctx.assertTrue(hitsOnFullLowered > 0,
                "vacuity guard: no ray hit an owner resolved to the -1.0 clamp — that is the ONLY "
                        + "magnitude whose shape leaves its owner's cell, so without it the "
                        + "neutrality comparison proves nothing about the window at all");
        ctx.complete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. THE PERF GATE
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * <b>Pins what the widening costs, relative to the window it replaced.</b> No wall-clock, no
     * absolute budget: the same battery is measured at both radii and the gate is stated as
     * relationships between the two counts, so it holds on any machine and cannot be satisfied by
     * a faster CPU.
     *
     * <p>What it pins, and why each one is the assertion that would catch a specific regression:
     *
     * <ul>
     *   <li><b>The DDA is untouched.</b> {@code cellsMarched} must be IDENTICAL at both radii. A
     *       widening that also marched more cells would be a different and much larger cost than
     *       the one signed off.</li>
     *   <li><b>The window is exactly as wide as it claims.</b> {@code neighborProbes} must equal
     *       {@code 2 * radius * cellsMarched} exactly, at BOTH radii. This is the assertion that
     *       fails the day someone adds a horizontal probe, a second pass, or a "just one more
     *       cell" — the sort of change that is individually cheap and collectively how a pick path
     *       degrades.</li>
     *   <li><b>The accepted cost is 5 probes per marched cell where there were 3</b> — the ~66%
     *       figure — pinned as an exact identity rather than a remembered number.</li>
     *   <li><b>Real work is pinned at the numbers measured at sign-off.</b> Shape raycasts, dy
     *       resolutions and {@code BlockPos} allocations are all de-duplicated per ray, so how much
     *       of the extra probing becomes real work is a property of the scene and cannot be derived
     *       from the radius — they are therefore pinned as MEASURED ceilings, not as a formula (see
     *       the {@code BASELINE_*} constants, which also record why the three grow so differently).
     *       A change that weakened the de-duplication or the memo would leave the probe identities
     *       above green and trip exactly these.</li>
     * </ul>
     *
     * <p><b>An honest correction the measurement forced.</b> The staged plan costed this stage as
     * "~66% more shape raycasts per marched DDA cell". The +66% is real and exact, but it is the
     * <em>cell probe</em> count; the shape raycasts themselves grew only ~29% on this scene because
     * of the de-duplication, while the <b>support-resolver walks grew ~206%</b> — the largest cost
     * of the widening is on an axis the plan did not name. That number is reported, not smoothed.
     *
     * <p><b>This cell does not certify that the cost is acceptable.</b> It certifies what the cost
     * IS, in operations. Frame time is a live question and Maintainer's gate alone.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void pickWindowCostIsPinnedRelativeToTheWindowItReplaced(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        Scene scene = buildScene(ctx, w, origin);
        List<Ray> rays = rayBattery(origin);
        int radius = SlabbedOffsetRaycast.WINDOW_RADIUS;

        Totals before = total(w, rays, PREVIOUS_WINDOW_RADIUS);
        Totals after = total(w, rays, radius);
        System.out.println("[STAGE1-PERF] scene=" + scene + " rays=" + rays.size()
                + "\n[STAGE1-PERF]   radius " + PREVIOUS_WINDOW_RADIUS + " (replaced): " + before
                + "\n[STAGE1-PERF]   radius " + radius + " (shipping):  " + after
                + "\n[STAGE1-PERF]   probesPerMarchedCell " + before.probesPerCell()
                + " -> " + after.probesPerCell()
                + "  shapeRaycasts x" + ratio(after.shapeRaycasts, before.shapeRaycasts)
                + "  dyResolutions x" + ratio(after.dyResolutions, before.dyResolutions)
                + "  posAllocations x" + ratio(after.posAllocations, before.posAllocations));

        ctx.assertTrue(radius == PREVIOUS_WINDOW_RADIUS + 1,
                "fixture: this cell measures the radius-1 -> radius-2 step; WINDOW_RADIUS is "
                        + radius + ". Re-derive the accepted cost before changing it.");
        ctx.assertTrue(before.cellsMarched > 0 && before.shapeRaycasts > 0,
                "fixture: the battery must do real work — " + before);

        ctx.assertTrue(after.cellsMarched == before.cellsMarched,
                "PERF GATE: widening the window must not change the DDA — cells marched went from "
                        + before.cellsMarched + " to " + after.cellsMarched + ". A widening that "
                        + "also marches more cells is a different cost from the one signed off.");

        ctx.assertTrue(before.neighborProbes == 2L * PREVIOUS_WINDOW_RADIUS * before.cellsMarched,
                "PERF GATE: the replaced window must probe exactly 2 neighbours per marched cell, "
                        + "measured " + before.neighborProbes + " over " + before.cellsMarched
                        + " cells — if this is wrong the comparison has no baseline.");
        ctx.assertTrue(after.neighborProbes == 2L * radius * after.cellsMarched,
                "PERF GATE: the shipping window must probe exactly " + (2 * radius)
                        + " neighbours per marched cell (2 * WINDOW_RADIUS), measured "
                        + after.neighborProbes + " over " + after.cellsMarched + " cells. THIS IS "
                        + "THE ASSERTION THAT CATCHES AN EXTRA PROBE being added to the pick path "
                        + "— a horizontal neighbour, a second pass, one more cell of headroom.");

        ctx.assertTrue(before.probesPerCell() == 3 && after.probesPerCell() == 2 * radius + 1,
                "PERF GATE: the accepted cost of this stage is " + (2 * radius + 1) + " cell "
                        + "probes per marched DDA cell where there were 3 — measured "
                        + before.probesPerCell() + " -> " + after.probesPerCell() + ".");

        // The real work — shape tests, resolver walks, allocations — is de-duplicated per ray, so
        // it does NOT grow by a ratio anyone can derive from the radius. It is pinned at the
        // numbers MEASURED on this fixed scene and battery when Stage 1 was signed off. See
        // BASELINE_* for why each is a ceiling rather than a formula.
        ctx.assertTrue(before.shapeRaycasts == BASELINE_R1_SHAPE_RAYCASTS
                        && before.dyResolutions == BASELINE_R1_DY_RESOLUTIONS
                        && before.posAllocations == BASELINE_R1_POS_ALLOCATIONS
                        && before.cellsMarched == BASELINE_R1_CELLS_MARCHED,
                "PERF GATE, BASELINE FINGERPRINT: the replaced radius-1 window's work on this exact "
                        + "scene and battery is the reference every ceiling below is stated against, "
                        + "so it is pinned too. Recorded " + BASELINE_R1_CELLS_MARCHED + "/"
                        + BASELINE_R1_SHAPE_RAYCASTS + "/" + BASELINE_R1_DY_RESOLUTIONS + "/"
                        + BASELINE_R1_POS_ALLOCATIONS + " (cells/shapes/dys/allocs), measured "
                        + before + ". If you changed the scene or the battery this is EXPECTED — "
                        + "re-measure both radii from the [STAGE1-PERF] line and rebaseline all "
                        + "eight numbers deliberately, in one commit, with the new ratios stated.");

        assertAtOrUnder(ctx, "shape raycasts", after.shapeRaycasts, ACCEPTED_R2_SHAPE_RAYCASTS,
                before.shapeRaycasts);
        assertAtOrUnder(ctx, "dy resolutions (support-resolver walks)", after.dyResolutions,
                ACCEPTED_R2_DY_RESOLUTIONS, before.dyResolutions);
        assertAtOrUnder(ctx, "BlockPos allocations", after.posAllocations,
                ACCEPTED_R2_POS_ALLOCATIONS, before.posAllocations);

        // The one ratio that IS derivable, restated as the sanity bound on the whole gate: real
        // work can never outgrow the probe count, because every unit of work is caused by a probe.
        ctx.assertTrue(after.shapeRaycasts * before.neighborProbes
                        <= before.shapeRaycasts * after.neighborProbes,
                "PERF GATE: shape raycasts (" + before.shapeRaycasts + " -> " + after.shapeRaycasts
                        + ") outgrew the neighbour probe count (" + before.neighborProbes + " -> "
                        + after.neighborProbes + "). Every shape test is caused by a probe, so this "
                        + "cannot happen unless the de-duplication set stopped working.");
        ctx.complete();
    }

    private static void assertAtOrUnder(TestContext ctx, String what, long measured, long accepted,
                                        long baseline) {
        ctx.assertTrue(measured >= baseline,
                "fixture: " + what + " cannot FALL when the window widens (" + baseline + " -> "
                        + measured + ") — that means the measurement is wrong, not the code.");
        ctx.assertTrue(measured <= accepted,
                "PERF GATE: " + what + " on the shipping pick path is " + measured + ", past the "
                        + accepted + " accepted when Stage 1 was signed off (radius-1 baseline "
                        + baseline + ", accepted growth x"
                        + String.format("%.3f", (double) accepted / (double) baseline)
                        + "). The pick path got MORE expensive than the widening that was live "
                        + "sign-off'd. This is the assertion that exists because this project has "
                        + "shipped a perf regression twice. Do not raise the number to make it "
                        + "green — find what added the work, or take a new frame-time sign-off.");
    }

    private static String ratio(long after, long before) {
        return before == 0 ? "n/a" : String.format("%.3f", (double) after / (double) before);
    }

    // ------------------------------------------------------------------------
    // scene + battery, shared by both cells

    /**
     * Six columns, each duplicated at two depths so a ray meets more than one candidate and the
     * nearest-hit rule is actually exercised. Every dy in today's alphabet appears, resolved by the
     * live resolver rather than written into the placement store — this scene must be a scene the
     * shipping build can actually produce.
     */
    private static Scene buildScene(TestContext ctx, ServerWorld w, BlockPos origin) {
        int lowered = 0;
        int clamped = 0;
        for (int z : new int[]{3, 5}) {
            // x=1 — a flush full cube (dy 0.0): the vanilla-parity control.
            w.setBlockState(origin.add(1, 1, z), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

            // x=2 — a full cube on a flush bottom slab: resolves to -0.5.
            w.setBlockState(origin.add(2, 1, z), bottomSlab(Blocks.STONE_SLAB), Block.NOTIFY_LISTENERS);
            w.setBlockState(origin.add(2, 2, z), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

            // x=3 — an anchored slab tower: the ladder that reaches the -1.0 clamp.
            w.setBlockState(origin.add(3, 1, z), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            for (int i = 2; i <= 5; i++) {
                BlockPos p = origin.add(3, i, z);
                w.setBlockState(p, bottomSlab(Blocks.OAK_SLAB), Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.addAnchor(w, p, w.getBlockState(p));
            }

            // x=4 — a thin, non-cube shape (fence) over a lowered support.
            w.setBlockState(origin.add(4, 1, z), bottomSlab(Blocks.STONE_SLAB), Block.NOTIFY_LISTENERS);
            w.setBlockState(origin.add(4, 2, z), Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

            // x=5 — a plain two-high wall of full cubes: nothing offset anywhere near it.
            w.setBlockState(origin.add(5, 1, z), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            w.setBlockState(origin.add(5, 2, z), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

            // x=6 — deliberately empty: rays that march air the whole way.
        }

        for (int x = 1; x <= 6; x++) {
            for (int y = 1; y <= 6; y++) {
                for (int z : new int[]{3, 5}) {
                    BlockPos p = origin.add(x, y, z);
                    BlockState s = w.getBlockState(p);
                    if (s.isAir()) {
                        continue;
                    }
                    double dy = SlabSupport.getYOffset(w, p, s);
                    if (Math.abs(dy + 0.5) <= EPS) {
                        lowered++;
                    } else if (Math.abs(dy + 1.0) <= EPS) {
                        clamped++;
                    }
                }
            }
        }
        ctx.assertTrue(lowered > 0,
                "fixture: the scene must contain at least one cell resolved to -0.5, found none");
        ctx.assertTrue(clamped > 0,
                "fixture: the scene must contain at least one cell resolved to the -1.0 clamp — "
                        + "that is the only magnitude whose shape leaves its owner's cell, so "
                        + "without it neither cell in this file measures the window");
        return new Scene(lowered, clamped);
    }

    /**
     * A fixed, deterministic battery: a fine vertical sweep of horizontal aims through every
     * column (the aim the player actually uses, and the one an offset shape can hide from), plus a
     * straight-down aim and a steep diagonal per column.
     */
    private static List<Ray> rayBattery(BlockPos origin) {
        List<Ray> rays = new ArrayList<>();
        double z0 = origin.getZ() + 0.5;
        double z1 = origin.getZ() + 7.5;
        for (int x = 1; x <= 6; x++) {
            double cx = origin.getX() + x + 0.5;
            for (int step = 0; step <= 40; step++) {
                double y = origin.getY() + 1.0 + step * 0.125;
                rays.add(new Ray(new Vec3d(cx, y, z0), new Vec3d(cx, y, z1)));
            }
            rays.add(new Ray(new Vec3d(cx, origin.getY() + 6.5, origin.getZ() + 3.5),
                    new Vec3d(cx, origin.getY() + 0.5, origin.getZ() + 3.5)));
            rays.add(new Ray(new Vec3d(cx, origin.getY() + 6.5, z0),
                    new Vec3d(cx, origin.getY() + 1.0, z1)));
        }
        return rays;
    }

    private static Totals total(ServerWorld w, List<Ray> rays, int radius) {
        long cells = 0;
        long probes = 0;
        long allocs = 0;
        long dys = 0;
        long shapes = 0;
        for (Ray r : rays) {
            SlabbedOffsetRaycast.Cost c =
                    SlabbedOffsetRaycast.measureCost(w, r.start(), r.end(), ShapeContext.absent(), radius);
            cells += c.cellsMarched();
            probes += c.neighborProbes();
            allocs += c.posAllocations();
            dys += c.dyResolutions();
            shapes += c.shapeRaycasts();
        }
        return new Totals(radius, cells, probes, allocs, dys, shapes);
    }

    private static BlockState bottomSlab(Block slab) {
        return slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static String describe(BlockHitResult hit) {
        return hit.getType() + "@" + hit.getBlockPos() + " side=" + hit.getSide()
                + " pos=" + hit.getPos() + " inside=" + hit.isInsideBlock();
    }

    private record Ray(Vec3d start, Vec3d end) {
        @Override
        public String toString() {
            return "ray " + start + " -> " + end;
        }
    }

    private record Scene(int cellsAtHalf, int cellsAtClamp) {
        @Override
        public String toString() {
            return "[cells at -0.5: " + cellsAtHalf + ", at -1.0: " + cellsAtClamp + "]";
        }
    }

    private record Totals(int radius, long cellsMarched, long neighborProbes, long posAllocations,
                          long dyResolutions, long shapeRaycasts) {
        /** Cell probes per marched DDA cell, primary included — the number this stage grows. */
        long probesPerCell() {
            return (cellsMarched + neighborProbes) / cellsMarched;
        }

        @Override
        public String toString() {
            return "cellsMarched=" + cellsMarched + " neighborProbes=" + neighborProbes
                    + " posAllocations=" + posAllocations + " dyResolutions=" + dyResolutions
                    + " shapeRaycasts=" + shapeRaycasts;
        }
    }
}
