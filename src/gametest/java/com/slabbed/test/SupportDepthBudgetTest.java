package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
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
 * STAGE 3 — <b>the depth budget, and the invariant its exhaustion path was violating.</b>
 *
 * <p>{@code SlabSupport.MAX_SUPPORT_RESOLVE_DEPTH} bounds the support-of-a-support walk. Until
 * 2026-08-07 it was a bare {@code 4} justified by a sentence about a constant in a different
 * paragraph, and on exhaustion {@code loweredFollowerDy} returned a bare {@code -0.5} — the "I
 * found nothing" floor — for a walk that had in fact found four consecutive lowered courses and
 * run out of budget mid-descent. The floor is SHALLOWER than the cap, so the first course past
 * the budget could read HIGHER than the course beneath it.
 *
 * <h2>THE INVARIANT</h2>
 *
 * <blockquote><b>A course may never resolve SHALLOWER than the course below it.</b></blockquote>
 *
 * <p>A tower is built by stacking; every course rests on the one under it, so its rendered height
 * is at most its support's. Nothing in the resolver is allowed to make a block rise as the stack
 * grows. That is the property these cells assert, over the whole ladder, on towers built
 * deliberately taller than the budget.
 *
 * <h2>Two tower SHAPES, because they consume the budget differently</h2>
 *
 * <p>{@code supportSeatDy} has a half-height arm (a bottom-slab support hands its follower
 * {@code supportDy - 0.5}) and a full-height arm (any support whose top face is at its own cell
 * top passes its dy through UNCHANGED). Both spend one unit of budget per course, but only the
 * first deepens, and that difference is the whole story:
 *
 * <ul>
 *   <li><b>DROPPING tower</b> (every course a bottom slab): each course deepens by half a block,
 *       so by the time the walk unwinds back up from an exhausted read it has re-saturated at the
 *       cap and the exhaustion value is washed out. This is Stage 0's B-1 shape, and it is why
 *       B-1 measured the exhaustion return as invisible.</li>
 *   <li><b>PASS-THROUGH tower</b> (anchored full-height courses stacked over a lowered base): the
 *       walk spends budget WITHOUT deepening, so whatever exhaustion returns is handed straight up
 *       to the player's eye, unmodified, by every course above it. <b>This shape is not in Stage
 *       0's measurements, and it is where the pop is real.</b></li>
 * </ul>
 *
 * <p><b>So Stage 0's "provably invisible" finding was true of the shape it measured and not of the
 * defect.</b> Recorded here rather than smoothed over, and reported to Maintainer as a correction to
 * MEASUREMENT B rather than a quiet fix.
 *
 * <h2>⚠️ AND STAGE 3'S OWN FIX TURNS OUT TO HAVE BEEN VALUE-COINCIDENTAL (Stage 4, 2026-08-07)</h2>
 *
 * <p>Exhaustion now returns {@code MIN_RESOLVED_DY}, which is right for a DROPPING tower: you may
 * only ever round a truncated descent DOWN. But a PASS-THROUGH stack does not descend, so the
 * truthful answer for a truncated pass-through walk is <em>the value the stack is standing on</em>,
 * and exhaustion substitutes the cap for it. At the shipped {@code -1.0} cap those two numbers are
 * THE SAME — which is the only reason Stage 3 measured green here. Arm
 * {@code SlabSupport.DEEP_DY_ALPHABET} and they differ by a full block: the stack SINKS at the
 * exhaustion point, the exact mirror of the pop Stage 3 removed. Monotonicity still holds (sinking
 * is downward), which is why it needs its own assertion.
 *
 * <p><b>Characterised, not fixed</b> — see {@code KNOWN_INCOMPLETE.md} row 1o. The OFF leg keeps
 * the strict pass-through equality unchanged; the deep leg pins the substitution and asserts it is
 * a sink rather than a pop. Repairing it is a production change to {@code loweredFollowerDy}'s
 * exhaustion return and owes its own RED-first pass over both tower shapes at both caps.
 *
 * <h2>Fixtures are PRE-STORE by construction</h2>
 *
 * <p>The budget cannot be reached at all while the placement store answers: {@code addAnchor}
 * records each course's height and {@code anchoredCellDy} returns it at depth 1, terminating the
 * walk (Stage 0, B-2). Every tower below therefore keeps its anchors and CLEARS its stored
 * heights — the shape of every world saved before {@code d4f38510}, and the only shape in which
 * this constant is observable at all. Each cell hard-asserts that clearing worked, so none of them
 * can pass by silently answering from the store.
 */
public final class SupportDepthBudgetTest {

    private static final double EPS = 1.0e-6;

    /**
     * Courses built beyond the budget. Three is enough for the exhausted read to be several
     * courses below the top of the tower in both shapes.
     */
    private static final int COURSES_PAST_THE_BUDGET = 3;

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. THE DERIVATION
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * <b>The budget is DERIVED, and the number it is derived from is MEASURED.</b>
     *
     * <p>{@code MAX_SUPPORT_RESOLVE_DEPTH} is
     * {@code ceil(-DEEPEST_TARGETABLE_DY / dropPerCourse) + 2}. Two of those three terms are
     * public constants and can be read directly. The third — the half-block seat drop one course
     * contributes — is a SHAPE, written inside {@code supportSeatDy}'s half-height arm and
     * deliberately NOT exposed as a cap-like constant (moving it with the cap is the mistake this
     * file's siblings exist to prevent). So it is measured from a real ladder here instead: build
     * a tower, read what one course actually costs, and re-derive the budget from that.
     *
     * <p>That closes the loop the old javadoc left open. Nothing can now change the per-course
     * drop, the window's contract depth, or the budget without one of them going RED.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void theBudgetIsDerivedFromTheCapAndTheMeasuredPerCourseDrop(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);

        // A two-course pre-store slab tower is the smallest fixture that exhibits one course's
        // drop: L0 seats flush on stone, L1 seats on L0's top face.
        BlockPos[] level = buildPreStoreSlabTower(ctx, w, ground, 2);
        double l0 = dy(w, level[0]);
        double l1 = dy(w, level[1]);
        double measuredDropPerCourse = l0 - l1;

        int cap = SlabSupport.MAX_SUPPORT_RESOLVE_DEPTH;
        double targetable = SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY;
        double resolved = SlabSupport.MIN_RESOLVED_DY;
        String measured = "L0=" + l0 + " L1=" + l1 + " dropPerCourse=" + measuredDropPerCourse
                + " MAX_SUPPORT_RESOLVE_DEPTH=" + cap
                + " DEEPEST_TARGETABLE_DY=" + targetable
                + " MIN_RESOLVED_DY=" + resolved;
        System.out.println("[STAGE3-DERIVATION] " + measured);

        ctx.assertTrue(Math.abs(l0) <= EPS,
                "premise: the bottom course seats flush on the stone below it — " + measured);
        ctx.assertTrue(measuredDropPerCourse > EPS,
                "premise: one course must actually cost something, or the derivation below divides "
                        + "by nothing — " + measured);

        int derived = (int) Math.ceil(-targetable / measuredDropPerCourse) + 2;
        ctx.assertTrue(cap == derived,
                "the depth budget must equal ceil(-DEEPEST_TARGETABLE_DY / measuredDropPerCourse) "
                        + "+ 2 = " + derived + ", got " + cap + ". Either the window's contract "
                        + "depth moved without the budget, or supportSeatDy's half-height arm now "
                        + "drops a different amount than DEEPEST_SEAT_DROP_PER_COURSE says — "
                        + measured);

        // SUFFICIENCY AGAINST THE CAP THAT IS ACTUALLY IN FORCE. MIN_RESOLVED_DY >=
        // DEEPEST_TARGETABLE_DY is an inequality during Stages 1-3 and closes at Stage 4, so a
        // budget sized for the deeper of the two is necessarily enough for the shallower.
        int neededForTodaysCap = (int) Math.ceil(-resolved / measuredDropPerCourse) + 2;
        ctx.assertTrue(cap >= neededForTodaysCap,
                "the budget must be at least what today's IN-FORCE cap needs (" + neededForTodaysCap
                        + "), got " + cap + " — " + measured);
        ctx.assertTrue(resolved >= targetable - EPS,
                "the standing identity MIN_RESOLVED_DY >= DEEPEST_TARGETABLE_DY must hold, or the "
                        + "budget is sized from the wrong end — " + measured);
        ctx.complete();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. MONOTONICITY — the dropping shape
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * <b>DROPPING tower, taller than the budget: the ladder never rises.</b>
     *
     * <p>Every course is a bottom slab, so each one deepens the walk by half a block. The tower is
     * built {@link #COURSES_PAST_THE_BUDGET} courses beyond {@code MAX_SUPPORT_RESOLVE_DEPTH}, so
     * the topmost courses genuinely exhaust the budget.
     *
     * <p><b>This cell is expected to be GREEN both before and after Stage 3</b>, and it is kept
     * precisely for that: it is the shape Stage 0 measured, it is where the exhaustion return was
     * proved invisible, and it is the control that stops the sibling cell below from being read as
     * "any deep tower was broken". If this one ever moves, the change was not the exhaustion path.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void droppingTowerPastTheBudgetNeverRises(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);
        int courses = SlabSupport.MAX_SUPPORT_RESOLVE_DEPTH + COURSES_PAST_THE_BUDGET;

        BlockPos[] level = buildPreStoreSlabTower(ctx, w, ground, courses);
        double[] dy = readLadder(w, level);
        String ladder = format("dropping", level, dy);
        System.out.println("[STAGE3-MONOTONIC] " + ladder);

        assertNonIncreasing(ctx, dy, ladder);
        ctx.assertTrue(Math.abs(dy[courses - 1] - SlabSupport.MIN_RESOLVED_DY) <= EPS,
                "premise: the top course of a tower this tall must have saturated at the cap, or "
                        + "the tower is not deep enough to reach the budget at all — " + ladder);
        ctx.complete();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3. MONOTONICITY — the pass-through shape. THIS IS THE CELL THAT BITES.
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * <b>PASS-THROUGH tower, taller than the budget: the ladder never rises.</b>
     *
     * <p>Column, bottom to top: stone / oak slab (seats flush, {@code 0.0}) / oak slab
     * ({@code -0.5}) / then a stack of anchored full-height blocks. A full-height support passes
     * its dy through unchanged, so every block in that stack must read the same depth as the
     * course under it, forever — the stack cannot climb back out of the hole it is standing in.
     *
     * <p><b>RED before Stage 3, at TODAY's cap.</b> With the bare {@code -0.5} exhaustion return
     * the walk spent its budget without deepening, hit the floor, and handed {@code -0.5} straight
     * up: the courses above the exhaustion point read half a block HIGHER than the courses below
     * them. Mutation-proved — see the class doc and the Stage 3 report for the observed ladder.
     *
     * <p>This is why the exhaustion return had to become the cap rather than the floor: exhaustion
     * means "at least this deep, and I stopped counting", and the only safe direction to round a
     * walk you truncated is DOWN.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void passThroughTowerPastTheBudgetNeverRises(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);
        int stack = SlabSupport.MAX_SUPPORT_RESOLVE_DEPTH + COURSES_PAST_THE_BUDGET;

        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos[] level = new BlockPos[2 + stack];
        for (int i = 0; i < level.length; i++) {
            level[i] = ground.up(i + 1);
            BlockState state = i < 2
                    ? bottomSlab(Blocks.OAK_SLAB)
                    : Blocks.STRIPPED_JUNGLE_LOG.getDefaultState();
            w.setBlockState(level[i], state, Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(w, level[i], w.getBlockState(level[i]));
        }
        clearStoredHeights(ctx, w, level);

        double[] dy = readLadder(w, level);
        String ladder = format("pass-through", level, dy);
        System.out.println("[STAGE3-MONOTONIC] " + ladder);

        // ── PREMISES: this really is a pass-through stack standing in a real hole ────────────
        ctx.assertTrue(Math.abs(dy[1] + 0.5) <= EPS,
                "premise: the second slab course must sit at -0.5, or the stack above it is not "
                        + "standing on a lowered support at all — " + ladder);
        for (int i = 2; i < level.length; i++) {
            ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, level[i]),
                    "premise: full-height course L" + i + " must be genuinely anchored, or the "
                            + "resolver never walks through it and this cell measures nothing — "
                            + ladder);
        }

        // ── THE INVARIANT ───────────────────────────────────────────────────────────────────
        assertNonIncreasing(ctx, dy, ladder);

        // ── AND THE VALUE: a pass-through course reads exactly what it is standing on ───────
        if (!SlabSupport.DEEP_DY_ALPHABET) {
            for (int i = 3; i < level.length; i++) {
                ctx.assertTrue(Math.abs(dy[i] - dy[2]) <= EPS,
                        "a full-height support passes its dy through UNCHANGED, so every course of "
                                + "this stack must read what the first one reads (" + dy[2] + "); L"
                                + i + " reads " + dy[i] + ". A course that differs is the depth "
                                + "budget leaking into the answer — " + ladder);
            }
            ctx.complete();
            return;
        }

        // ── THE DEEP LEG: STAGE 3'S EXHAUSTION FIX WAS VALUE-COINCIDENTAL AT THE SHIPPED CAP ──
        //
        // ⚠️ CHARACTERISATION, NOT ENDORSEMENT. This is a REAL residual found by arming Stage 4's
        // flag, reported rather than patched, and it is deliberately NOT fixed here: repairing it
        // means changing loweredFollowerDy's exhaustion return, which is Stage 3's site and a
        // production behaviour change that owes its own RED-first pass. See KNOWN_INCOMPLETE row
        // 1o.
        //
        // WHAT WAS MEASURED. Exhaustion returns MIN_RESOLVED_DY (Stage 3 changed it from a bare
        // -0.5 for a good reason: in a DROPPING tower, truncating a descent may only ever round
        // DOWN). But a PASS-THROUGH stack does not descend — every full-height course hands its
        // support's dy up unchanged — so the truthful answer for a truncated pass-through walk is
        // the value the stack is standing on, and exhaustion substitutes the cap for it. At the
        // shipped -1.0 cap those two numbers are THE SAME (the base of this fixture is at -1.0), so
        // Stage 3 measured green and the substitution was invisible. At the ruled -2.0 cap they
        // differ by a full block and the stack SINKS at the exhaustion point — the exact mirror of
        // the pop Stage 3 removed, in the other direction.
        //
        // The MONOTONICITY invariant above still holds (sinking is downward, and a course may
        // always resolve deeper than its support), which is why this needs its own assertion.
        int firstDivergent = -1;
        for (int i = 3; i < level.length; i++) {
            if (Math.abs(dy[i] - dy[2]) > EPS) {
                firstDivergent = i;
                break;
            }
        }
        System.out.println("[STAGE4-PASSTHROUGH] firstDivergentCourse=" + firstDivergent
                + " passThroughValue=" + dy[2] + " " + ladder);

        ctx.assertTrue(firstDivergent > 0,
                "the deep leg was expected to REPRODUCE the exhaustion substitution and did not. "
                        + "If the exhaustion path has been repaired, delete this branch and let the "
                        + "unconditional pass-through assertion above run in both legs — do not "
                        + "leave a characterisation standing over a fixed defect — " + ladder);
        ctx.assertTrue(Math.abs(dy[firstDivergent] - SlabSupport.MIN_RESOLVED_DY) <= EPS,
                "CHARACTERISED (Stage 4): the first course past the depth budget must read the "
                        + "exhaustion return, which is MIN_RESOLVED_DY ("
                        + SlabSupport.MIN_RESOLVED_DY + "); it reads " + dy[firstDivergent]
                        + ". If this is neither the pass-through value nor the cap, the exhaustion "
                        + "path has changed shape and this row no longer describes it — " + ladder);
        for (int i = firstDivergent; i < level.length; i++) {
            ctx.assertTrue(Math.abs(dy[i] - SlabSupport.MIN_RESOLVED_DY) <= EPS,
                    "CHARACTERISED (Stage 4): once the exhaustion value enters a pass-through "
                            + "stack every course above carries it unchanged, so L" + i
                            + " must equal the cap; it reads " + dy[i] + " — " + ladder);
        }
        ctx.assertTrue(dy[firstDivergent] < dy[firstDivergent - 1] - EPS,
                "CHARACTERISED (Stage 4): the substitution must be a SINK, not a pop. L"
                        + firstDivergent + " reading shallower than the course below it would be "
                        + "the never-pop violation Stage 3 removed, returning at a deeper cap — "
                        + ladder);
        ctx.complete();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * THE INVARIANT, in one place so both shapes assert the identical thing: no course may resolve
     * shallower than the course below it.
     */
    private static void assertNonIncreasing(TestContext ctx, double[] dy, String ladder) {
        for (int i = 1; i < dy.length; i++) {
            ctx.assertTrue(dy[i] <= dy[i - 1] + EPS,
                    "MONOTONICITY: L" + i + " (" + dy[i] + ") resolves SHALLOWER than the course it "
                            + "rests on, L" + (i - 1) + " (" + dy[i - 1] + ") — a tower that steps "
                            + "down and then pops back UP by " + (dy[i] - dy[i - 1]) + ". A course "
                            + "can never rise above its own support — " + ladder);
        }
    }

    private static BlockPos[] buildPreStoreSlabTower(TestContext ctx, ServerWorld w, BlockPos ground,
                                                     int courses) {
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos[] level = new BlockPos[courses];
        for (int i = 0; i < courses; i++) {
            level[i] = ground.up(i + 1);
            w.setBlockState(level[i], bottomSlab(Blocks.OAK_SLAB), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(w, level[i], w.getBlockState(level[i]));
        }
        clearStoredHeights(ctx, w, level);
        return level;
    }

    /**
     * PRE-STORE SHAPE: keep every anchor, drop every stored height, and prove the drop took. With
     * a stored fact present the walk answers at depth 1 and the budget is never reached, so a
     * silent failure here would leave every cell in this file passing vacuously.
     */
    private static void clearStoredHeights(TestContext ctx, ServerWorld w, BlockPos[] level) {
        for (BlockPos p : level) {
            SlabPlacementDyAttachment.clear(w, p);
            ctx.assertFalse(SlabPlacementDyAttachment.hasStoredDy(w, p),
                    "fixture: these cells measure the PRE-STORE lane — " + p + " must hold no "
                            + "stored height, or the depth budget is never reached at all");
        }
    }

    private static double[] readLadder(ServerWorld w, BlockPos[] level) {
        double[] dy = new double[level.length];
        for (int i = 0; i < level.length; i++) {
            dy[i] = dy(w, level[i]);
        }
        return dy;
    }

    private static String format(String shape, BlockPos[] level, double[] dy) {
        StringBuilder sb = new StringBuilder(shape).append(" ladder (budget=")
                .append(SlabSupport.MAX_SUPPORT_RESOLVE_DEPTH).append(", cap=")
                .append(SlabSupport.MIN_RESOLVED_DY).append("):");
        for (int i = 0; i < level.length; i++) {
            sb.append(" L").append(i).append('=').append(dy[i]);
        }
        return sb.toString();
    }

    private static double dy(ServerWorld w, BlockPos pos) {
        return SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
    }

    private static BlockState bottomSlab(Block slab) {
        return slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }
}
