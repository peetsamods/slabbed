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
 * LAW 1 (LAW.md) item #1 ("everything should be able to lower; no exceptions", 2026-08-06):
 * <b>a slab resting on a LOWERED bottom slab had no lane at all.</b>
 *
 * <p><b>Live evidence.</b> {@code (157,-58,-10) oak_slab dy=0.000 src=FROZEN-FLAT} sitting on
 * {@code (157,-59,-10) stone_slab dy=-0.500 ANCHORED}. The correct value is {@code -1.0} (mega
 * row 2's {@code MEGA_ROW_DY[2]}); the slab was stuck flat on a visibly sunk support.
 *
 * <p><b>Root cause.</b> The case fell between two TYPE-based rejects, neither of which asked
 * whether the support was actually sunk:
 * <ul>
 *   <li>{@code hasLoweredNonSlabTopSupport} rejects any support that is {@code instanceof
 *       SlabBlock};</li>
 *   <li>{@code hasLoweredTopLikeSlabSupport} rejected any support where {@code isBottomSlab(state)}
 *       — unconditionally, on type;</li>
 *   <li>{@code shouldOffset} never offsets slabs, so slabs have no generic-grammar fallback —
 *       their only lanes are {@code getYOffsetInner}'s slab branch.</li>
 * </ul>
 * Everything then fell to the class-based flush guard, which returns a hardcoded {@code 0.0} for
 * {@code SlabBlock}. The resolver already computed the right answer: had any lane reached
 * {@code loweredFollowerDy}, it would resolve {@code supportSeatDy} → {@code
 * loweredBottomSlabSupportDy} ({@code -0.5}) {@code - 0.5} = {@code -1.0}. <b>The gate was broken,
 * not the arithmetic</b> — no new depth math is introduced by the fix.
 *
 * <p><b>Why the pre-existing suite never caught it.</b>
 * {@code SlabOnSlabVerticalAnchorTest#slabOnBottomTypeSupportNeverAnchorsVertically} builds a
 * <b>non-lowered</b> birch bottom slab as its support, so it only ever defended the plain case.
 * Its premise ({@code KNOWN_INCOMPLETE.md} L8 — "a BOTTOM slab isn't itself 'sunk', so nothing
 * should propagate upward from it") was <b>overruled entirely</b> on 2026-08-06 (exclusion #13):
 * a bottom slab's top face IS half a block below the grid, so a slab resting on it seats there
 * whether or not the support is itself sunk. {@link #slabOnFlatBottomSlabSeatsOnItsTopFace} pins
 * that plain case here, and {@link #slabTowerLaddersToTheClampThenGaps} pins the stacked ladder
 * including where the {@code MIN_RESOLVED_DY} clamp bites.
 *
 * <p><b>The scene</b> is the {@code /slabrig} {@code seatMinusOne} shape: a source column beside
 * the seat (stone / bottom slab / stone) whose top stone renders {@code -0.5}, making the bottom
 * slab beside it a legitimate air-below cantilever seat at {@code -0.5}. Every fixture premise —
 * <b>including the support's own {@code -0.5}</b> — is hard-asserted, so the cell cannot pass
 * vacuously against a seat that never sank.
 */
public final class SlabOnLoweredBottomSlabTest {

    private static final double EPS = 1.0e-6;

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
