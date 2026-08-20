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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * The dy cap/window suite — every test of the {@code minResolvedDy()} /
 * {@code DEEPEST_TARGETABLE_DY} / {@code WINDOW_RADIUS} contract in one place: clamp-site
 * unification with its byte-identity fingerprints (both {@code slabbed.deepDyAlphabet} legs),
 * the window-radius characterisation measurements, the depth-budget derivation and its
 * exhaustion-clamp ladders, and the pick-window widening equivalence + perf baselines. Merged
 * 2026-08-07 from four stage-named classes — every test, pin and baseline preserved verbatim;
 * shared EPS and bottomSlab(Block) deduped. The dual-leg discipline is unchanged: run both legs,
 * counts must match. Original class docs follow, per section.
 */
public final class DyWindowSuite {

    // ═══ clamp-site unification + the pinned fingerprints (both legs) (ClampUnificationTest) ═══
    // /**
    //  * STAGE 2 — <b>the two clamps are one clamp.</b>
    //  *
    //  * <p>{@code SlabSupport} refuses to resolve a height deeper than
    //  * {@link SlabSupport#minResolvedDy()} in more than one place, and until 2026-08-07 one of those
    //  * places did not say so. The support resolver clamps by name
    //  * ({@code Math.max(seat, minResolvedDy())}); the direct-custom surface lane in
    //  * {@code getYOffsetInner} — the lane a Terrain Slabs surface takes — wrote the magnitude out by
    //  * hand as {@code if (dy < -1.0) dy = -1.0;}. Both answer {@code -1.0} today, so nothing was
    //  * visibly wrong. The defect was latent: move the constant and the anonymous site keeps the old
    //  * cap, so a TS tower and a vanilla tower would sit at different heights in the same world for the
    //  * same reason. That is this project's shared-predicate half-fix shape, which it has paid for
    //  * repeatedly (see {@code LAW.md} and the flat-constant mirrors recorded there).
    //  *
    //  * <p><b>This cell is what makes Stage 4 a one-constant change.</b> Stage 2 itself is inert — the
    //  * unification is byte-identical at today's {@code -1.0}, which is exactly why it ships ahead of
    //  * the value change instead of with it.
    //  *
    //  * <h2>Why the tower is built the way it is</h2>
    //  *
    //  * The two lanes are mutually exclusive for any single cell — whichever branch of
    //  * {@code getYOffsetInner} claims a block, the other never runs — so "the same tower" cannot mean
    //  * "the same block resolved twice". It means: <b>one column, built once, with two subjects sitting
    //  * on its top course</b>, chosen so that each subject is claimed by a different clamp site while
    //  * both are handed the identical pre-clamp value of {@code -1.5}.
    //  *
    //  * <ul>
    //  *   <li>a vanilla SLAB subject is claimed by {@code getYOffsetInner}'s slab branch, which resolves
    //  *       through {@code loweredFollowerDy} → the NAMED clamp;</li>
    //  *   <li>a curated non-slab subject (a crafting table) over the same course falls through to the
    //  *       direct-custom surface lane → the formerly ANONYMOUS clamp.</li>
    //  * </ul>
    //  *
    //  * <p>Both arrive at {@code -1.5} by different arithmetic on the same numbers: the slab subject
    //  * takes its support's {@code -1.0} minus the half-block seat; the crafting table takes the
    //  * direct-custom surface's {@code -0.5} plus that same support's {@code -1.0}. Every premise is
    //  * hard-asserted, so neither can pass against a tower that never sank.
    //  *
    //  * <p><b>{@code -1.5} is the deepest pre-clamp value this build can present to either site</b>, and
    //  * that is a consequence of the clamp itself: every course of a tower is clamped as it resolves, so
    //  * no support can ever hand its follower a number deeper than {@code minResolvedDy() - 0.5}. The
    //  * fixture asserts that the tower is deep enough to saturate rather than assuming it, so the day
    //  * the cap moves past {@code -1.5} this cell goes RED and says what to do about it instead of
    //  * passing vacuously.
    //  *
    //  * <h2>Terrain Slabs coverage — read this before citing the cell</h2>
    //  *
    //  * <p><b>This is NOT real Terrain Slabs coverage.</b> The real mod has client entrypoints that abort
    //  * the headless dedicated-server run, so it cannot load here at all. The TS course is
    //  * {@link TerrainSlabsTestShim}'s {@code terrain_slabs:test_slab} — a plain vanilla
    //  * {@link SlabBlock} registered under the {@code terrain_slabs} namespace, which the real
    //  * {@code TerrainSlabsCompat} classifier then treats as a {@code BOTTOM_LIKE} surface exactly as it
    //  * would the real thing. So what is exercised is the CLASSIFIER and the lane it selects, with real
    //  * TS geometry standing outside the test. A live pass is still the only thing that certifies the
    //  * mod itself. Every other TS gametest on this line carries the same limitation.
    //  */

    private static final double EPS = 1.0e-6;

    /** The half-block a bottom-slab course adds. A SHAPE, and it does not move with the cap. */
    private static final double SEAT_DROP_PER_COURSE = 0.5;

    /**
     * How many oak-slab courses the tower needs to LADDER DOWN TO THE CAP before the subject is
     * placed. Derived, never written: each course deepens by {@link #SEAT_DROP_PER_COURSE} until
     * the clamp refuses, so {@code ceil(-cap / drop)} courses saturate. Evaluated at the shipped
     * {@code -1.0} cap this is 2 — exactly the two courses this fixture has always built — and at
     * the ruled {@code -2.0} cap it is 4.
     *
     * <p>Deepening the fixture is not optional at Stage 4: the previous fixed pair of courses left
     * the pre-clamp value at {@code -1.5}, which a {@code -2.0} cap does not refuse, and the cell's
     * own non-vacuity guard said so in as many words when the flag was first armed.
     */
    private static final int SATURATING_OAK_COURSES =
            (int) Math.ceil(-SlabSupport.minResolvedDy() / SEAT_DROP_PER_COURSE);

    /**
     * The pre-clamp value both lanes are handed by the tower below. Written down because both
     * assertions and the saturation premise depend on it, and because it is the deepest value this
     * build can produce (see the class doc): every course is itself clamped as it resolves, so the
     * deepest number any support can hand its follower is exactly one course past the cap.
     */
    private static final double RAW_TOWER_DY =
            SlabSupport.minResolvedDy() - SEAT_DROP_PER_COURSE;

    // ─────────────────────────────────────────────────────────────────────────────
    // THE STAGE 2 CELL
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * ONE tower, two clamp sites, one answer.
     *
     * <p>Column (bottom to top): stone / {@code terrain_slabs:test_slab} BOTTOM / oak slab
     * ({@code -0.5}) / oak slab, anchored ({@code -1.0}). Two subjects rest on that top course in
     * two identical columns: an oak slab (named clamp) and a crafting table (formerly anonymous
     * clamp). Both are handed {@code -1.5}; both must report {@link SlabSupport#minResolvedDy()}.
     *
     * <p>Mutation-proved: with the cap moved to {@code -2.0} and the direct-custom site left
     * written out by hand, the slab subject reports {@code -1.5} and the crafting table reports
     * {@code -1.0} — this cell goes RED on the agreement assertion, which is the whole reason it
     * exists.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bothClampSitesAnswerTheSameForTheSameTower(TestContext ctx) {
        ServerWorld w = ctx.getWorld();

        BlockPos slabGround = ctx.getAbsolutePos(BlockPos.ORIGIN).add(1, 1, 1);
        BlockPos tableGround = ctx.getAbsolutePos(BlockPos.ORIGIN).add(6, 1, 1);

        BlockPos slabSubject = buildTower(w, slabGround, bottomSlab(Blocks.OAK_SLAB));
        BlockPos tableSubject = buildTower(w, tableGround, Blocks.CRAFTING_TABLE.getDefaultState());

        // ── PREMISES: the two columns really are the same tower, and it really sank ──────────
        assertCoursesMatch(ctx, w, slabGround, tableGround);

        BlockPos support = supportCourse(slabGround);
        double supportDy = dy(w, support);
        double storedSupportDy = SlabPlacementDyAttachment.storedDy(w, support);
        ctx.assertTrue(Math.abs(dy(w, slabGround.up(2)) + 0.5) <= EPS,
                "premise: the tower's third course must sit at -0.5 on the Terrain Slabs surface, "
                        + "got " + dy(w, slabGround.up(2)));
        ctx.assertTrue(Math.abs(supportDy - SlabSupport.minResolvedDy()) <= EPS,
                "premise: the course both subjects rest on must resolve to the cap ("
                        + SlabSupport.minResolvedDy() + ") — that is the number that makes BOTH "
                        + "pre-clamp values " + RAW_TOWER_DY + ", got " + supportDy);
        ctx.assertTrue(Math.abs(storedSupportDy - SlabSupport.minResolvedDy()) <= EPS,
                "premise: that course must carry a STORED placement height of "
                        + SlabSupport.minResolvedDy() + ", or the direct-custom lane reads its "
                        + "support through a different arm and the two pre-clamp values stop "
                        + "matching, got " + storedSupportDy);

        // ── PREMISE: each subject is claimed by the lane this cell means to test ─────────────
        BlockState slabState = w.getBlockState(slabSubject);
        BlockState tableState = w.getBlockState(tableSubject);
        ctx.assertTrue(slabState.getBlock() instanceof SlabBlock,
                "premise: the named-clamp subject must be a slab so getYOffsetInner's slab branch "
                        + "claims it before the direct-custom lane can, got " + slabState);
        ctx.assertTrue(SlabSupport.isDirectCustomSlabSupportedObject(w, tableSubject, tableState),
                "premise: the second subject must be claimed by the DIRECT-CUSTOM surface lane — "
                        + "that is the site whose clamp was written out by hand — got "
                        + tableState);
        ctx.assertTrue(!(tableState.getBlock() instanceof SlabBlock)
                        && !SlabAnchorAttachment.isAnchored(w, tableSubject),
                "premise: the direct-custom subject must be neither a slab nor anchored, or an "
                        + "earlier branch of getYOffsetInner answers instead");

        double slabDy = dy(w, slabSubject);
        double tableDy = dy(w, tableSubject);
        String measured = "namedClampLane=" + slabDy + " directCustomLane=" + tableDy
                + " cap=" + SlabSupport.minResolvedDy() + " rawBoth=" + RAW_TOWER_DY;
        System.out.println("[STAGE2-CLAMP] " + measured);

        // ── THE ASSERTION THIS CELL EXISTS FOR ──────────────────────────────────────────────
        ctx.assertTrue(Math.abs(slabDy - tableDy) <= EPS,
                "THE TWO CLAMP SITES DISAGREE. The support resolver and the direct-custom surface "
                        + "lane were handed the same pre-clamp " + RAW_TOWER_DY + " for the same "
                        + "tower and returned different heights, so a Terrain Slabs tower and a "
                        + "vanilla tower now sit at different depths in the same world. Both sites "
                        + "must read SlabSupport.minResolvedDy() — " + measured);

        // ── NON-VACUITY, asserted AFTER the property so a half-fix reports as a half-fix ─────
        // Order matters: if this guard came first, moving the cap without unifying the sites
        // would abort here and the disagreement above would never be reached — the run would
        // blame the fixture for a real product defect. Assert the property, then prove the
        // assertion was load-bearing.
        ctx.assertTrue(RAW_TOWER_DY < SlabSupport.minResolvedDy() - EPS,
                "FIXTURE IS NO LONGER LOAD-BEARING: this cell proves the two clamps agree by "
                        + "making both saturate, and the deepest pre-clamp value this build can "
                        + "present is " + RAW_TOWER_DY + ", which is no longer past the cap ("
                        + SlabSupport.minResolvedDy() + "). Add courses to buildTower until the "
                        + "raw value is past the new cap, and update RAW_TOWER_DY — do NOT delete "
                        + "this cell, it is what keeps the two clamp sites in step. — " + measured);

        ctx.assertTrue(Math.abs(slabDy - SlabSupport.minResolvedDy()) <= EPS,
                "the support resolver must saturate at minResolvedDy() — " + measured);
        ctx.assertTrue(Math.abs(tableDy - SlabSupport.minResolvedDy()) <= EPS,
                "the direct-custom surface lane must saturate at minResolvedDy(), not at a "
                        + "magnitude of its own — " + measured);

        ctx.complete();
    }

    /**
     * The resolver's cap and the pick window's contract may not move apart.
     *
     * <p>Stage 1 wrote the identity down in {@code SlabbedOffsetRaycast}'s javadoc as an
     * inequality that closes to equality at Stage 4. A javadoc is not a gate, so this asserts it:
     * the resolver may never PRODUCE a height deeper than the window undertakes to attribute, and
     * the window must be wide enough for whatever the resolver can produce. Today
     * {@code -1.0 >= -2.0} with radius 2 covering a required radius of 1 — slack on purpose.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void theCapAndTheTargetableDepthCannotMoveApart(TestContext ctx) {
        double cap = SlabSupport.minResolvedDy();
        double targetable = SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY;
        int radius = SlabbedOffsetRaycast.WINDOW_RADIUS;
        String measured = "minResolvedDy()=" + cap + " DEEPEST_TARGETABLE_DY=" + targetable
                + " WINDOW_RADIUS=" + radius;
        System.out.println("[STAGE2-IDENTITY] " + measured);

        ctx.assertTrue(cap >= targetable - EPS,
                "THE ALPHABET HAS OUTRUN THE PICK WINDOW. SlabSupport resolves heights down to "
                        + cap + " while SlabbedOffsetRaycast only undertakes to attribute "
                        + targetable + ", so a block at the cap is drawn where the player cannot "
                        + "aim at it. Move DEEPEST_TARGETABLE_DY first (the window derives its "
                        + "radius from it), then the cap — " + measured);

        ctx.assertTrue(radius >= (int) Math.ceil(-cap),
                "the pick window must be wide enough for the deepest height the resolver can "
                        + "produce: cap " + cap + " needs radius " + (int) Math.ceil(-cap)
                        + " — " + measured);

        ctx.complete();
    }

    /**
     * INERTNESS GATE — a fixed battery of columns, one resolved height per cell, encoded as a
     * single token string and pinned.
     *
     * <p>Stage 2's defining property is that it changes no answer at today's cap. That claim is
     * cheap to make and easy to be wrong about, so it is measured instead: 2 base courses x 7
     * supports x 7 subjects x {bare, anchored support} = 196 columns, three readings each, taken
     * before the unification and pinned here. The unification must reproduce the string exactly.
     *
     * <p>The battery is built one column at a time at ONE position, cleared to air with anchors,
     * frozen-flat markers and stored heights removed between builds, so no column can be
     * contaminated by its predecessor or by a lateral neighbour.
     *
     * <p>Tokens: {@code 0} = 0.0, {@code H} = -0.5, {@code F} = -1.0, {@code D} = -1.5,
     * {@code U} = +0.5. Anything outside that set is written out in full, so an unexpected value
     * can never be hidden inside a token.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void resolverAnswersAreUnchangedAcrossTheUnification(TestContext ctx) {
        String fingerprint = battery(ctx);
        System.out.println("[STAGE2-FINGERPRINT] len=" + fingerprint.length()
                + " deepDyAlphabet=" + SlabSupport.DEEP_DY_ALPHABET
                + " cap=" + SlabSupport.minResolvedDy());
        System.out.println("[STAGE2-FINGERPRINT] " + fingerprint);

        ctx.assertTrue(fingerprint.equals(PINNED_FINGERPRINT),
                "STAGE 2 WAS NOT INERT, or the Stage 4 flag has leaked into a shape it must not "
                        + "reach. The resolver's answers over the 196-column battery differ from "
                        + "the values measured before the clamp sites were unified. Running cap is "
                        + SlabSupport.minResolvedDy() + " (deepDyAlphabet="
                        + SlabSupport.DEEP_DY_ALPHABET + "). First difference at index "
                        + firstDifference(fingerprint, PINNED_FINGERPRINT)
                        + ".\n  pinned   = " + PINNED_FINGERPRINT
                        + "\n  measured = " + fingerprint);
        ctx.complete();
    }

    /**
     * <b>WHICH SHAPES THE RULING ACTUALLY MOVES — the same instrument, aimed at columns deep enough
     * for the cap to bite.</b>
     *
     * <p>MEASURED at Stage 4 (2026-08-07): the 196-column battery above is <b>cap-invariant</b>. It
     * produces a byte-identical 588-token string with the flag on and with it off, because its
     * tallest column is three courses and nothing in it ever resolves past {@code -1.0} — the cap
     * has nothing to refuse. That is worth knowing (it is the blast radius of maintainer ruling stated
     * as data: ordinary shallow scenes do not move at all), but it also means that battery cannot
     * tell the two legs apart, so on its own it could not distinguish "the OFF leg is clean" from
     * "the flag never reached the resolver".
     *
     * <p>This cell closes that gap. Same construction, same tokens, same one-column-at-a-time
     * clearing — but every column stands on a {@link #DEEP_LADDER_COURSES}-course anchored slab
     * ladder, which saturates at either cap. So the fingerprint is REQUIRED to differ between the
     * legs, and each leg is pinned:
     *
     * <ul>
     *   <li>the OFF-leg pin is the byte-identity evidence a deep column can actually carry — if
     *       arming the alphabet ever leaked into the shipped default, this string moves;</li>
     *   <li>the deep-leg pin characterises exactly what the ruling buys, index by index.</li>
     * </ul>
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deepColumnsAreTheOnlyShapesTheDeeperAlphabetMoves(TestContext ctx) {
        String fingerprint = deepBattery(ctx);
        boolean deep = SlabSupport.DEEP_DY_ALPHABET;
        String pinned = deep ? PINNED_DEEP_BATTERY_ON : PINNED_DEEP_BATTERY_OFF;
        System.out.println("[STAGE4-DEEPBATTERY] len=" + fingerprint.length()
                + " deepDyAlphabet=" + deep + " cap=" + SlabSupport.minResolvedDy());
        System.out.println("[STAGE4-DEEPBATTERY] " + fingerprint);

        ctx.assertTrue(fingerprint.equals(pinned),
                (deep
                        ? "THE DEEP LEG MOVED since the flag was first armed. "
                        : "THE SHIPPED DEFAULT MOVED. A column deep enough for the cap to bite "
                                + "answers differently than it did before the Stage 4 flag "
                                + "existed, which is the leak this flag exists to prevent. ")
                        + "cap=" + SlabSupport.minResolvedDy() + ", first difference at index "
                        + firstDifference(fingerprint, pinned)
                        + ".\n  pinned   = " + pinned
                        + "\n  measured = " + fingerprint);

        // NON-VACUITY: if the two pins were equal this cell could not tell the legs apart, and a
        // deep leg silently running the shipped cap would pass it.
        ctx.assertTrue(!PINNED_DEEP_BATTERY_OFF.equals(PINNED_DEEP_BATTERY_ON),
                "the two pinned deep-battery fingerprints are identical, so this cell can no "
                        + "longer distinguish the legs and the deep leg could be running the "
                        + "shipped cap unnoticed");
        ctx.complete();
    }

    /**
     * Measured on the tree at Stage 1 ({@code 1500840c}) with the direct-custom clamp still
     * written out by hand, i.e. BEFORE the unification. Re-pin only with a recorded reason.
     *
     * <p>Every token is {@code 0}, {@code H} or {@code F} — the whole battery lands inside the
     * {@code {-1.0, -0.5, 0.0}} alphabet, which is itself worth pinning: nothing in these 196
     * columns produces an off-alphabet height today.
     *
     * <p><b>RE-PINNED (maintainer ruling, live-confirmed 2026-08-09) — Root Cause A.</b>
     * {@code hasLoweringSourceInColumnBelow}'s explicit Terrain-Slabs {@code BOTTOM_LIKE} branch
     * was removed: it treated a TS bottom slab as a column lowering source even when found
     * DIRECTLY below (first loop iteration, not only deeper in a column), anchoring a plain solid
     * full block placed on bare TS to {@code -0.5} — live-confirmed as a visible snap-down (flush
     * on the client's first frame, then dropping once the server anchor synced), a LAW 1
     * violation. Exactly 14 of 588 tokens move, isolated to the
     * {@code (base=TS-bottom-slab, support=STONE, anchored=true)} rows across all 7 subjects —
     * every other combination is byte-identical. Both changed tokens per row move {@code H -> 0}
     * (toward flush), the correct direction: nothing in this battery gained a MORE negative
     * reading, only lost an unearned one. See {@code TerrainSlabsGuardSweepTest} and
     * {@code LIVE_LEDGER.md} (private notes) for the live test that found this.
     */
    private static final String PINNED_FINGERPRINT =
            "00H00H00H00H00H00H00H00000000000000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000H00F00H00H00000H00H0HF0HF0HF0HF0HH0HF0HF0FH0FF0FH0"
            + "FH0FH0FH0FH0HH0HF0HH0HH0H00HH0HH0000000000000000000000HH0HH0HH0HH0HH0HH0HH0HH0HH0HH0HH0HH"
            + "0HH0HH00H00F00H00H00000H00H00H00H00H00H00H00H00H00000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000000000000H00F00H00H00000H00H0HF0"
            + "HF0HF0HF0HH0HF0HF0FH0FF0FH0FH0FH0FH0FH0HH0HF0HH0HH0H00HH0HH0000000000000000000000HH0HH0HH"
            + "0HH0HH0HH0HH0HH0HH0HH0HH0HH0HH0HH00H00F00H00H00000H00H";

    /**
     * Courses of anchored bottom slab under every deep-battery column. Four is enough to saturate
     * at the ruled {@code -2.0} cap ({@code ceil(2.0 / 0.5)}) and more than enough at the shipped
     * {@code -1.0} one, so the SAME scene is built in both legs and only the resolver's answers
     * differ. Written as a fixed number rather than derived from the cap on purpose: a fixture
     * whose GEOMETRY moved with the flag would not be comparing like with like.
     */
    private static final int DEEP_LADDER_COURSES = 4;

    /**
     * The deep battery with the flag OFF. Measured 2026-08-07; this is the byte-identity evidence
     * for the shipped default on shapes the shallow battery cannot reach.
     */
    private static final String PINNED_DEEP_BATTERY_OFF =
            "FFHFFHFFHFFHFFHFFHFFHFF0FF0FF0FFFFF0FFFFF0FF0FF0FF0FFFFF0FFFFF0FFFFFFFF0FFFFF0FFFFF0FF"
            + "FFFFFF0FFFFF0FFFFF0FFFFFFFFFFFFFFFFFFFFFF0HF0FF0HF0HF00F0HF0HFFFFFFFFFFFFFFFFFFFFFFFFF"
            + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
            + "FFFFFFFFFFFFFFFFFHFFHFFHFFHFFHFFHFFH";

    /**
     * The deep battery with {@code slabbed.deepDyAlphabet=true}. Measured 2026-08-07 on the tree
     * that first armed the flag; a characterisation of the deep leg, not an endorsement of any
     * cell in it. Every index where this differs from {@link #PINNED_DEEP_BATTERY_OFF} is a shape
     * whose rendered height the ruling moves in a world holding no stored placement fact.
     */
    private static final String PINNED_DEEP_BATTERY_ON =
            "DTHDTHDTHDTHDTHDTHDTHDT0DT0DT0DTTDT0DTTDT0DT0DT0DT0DTTDT0DTTDT0DTTDTTDT0DTTDT0DTTDT0DT"
            + "TDTTDT0DTTDT0DTTDT0DTTDTTDTTDTTDTTDTTDTTD0HD0FD0HD0HD00D0HD0HDTTDTTDTTDTTDTTDTTDTTDTTD"
            + "TTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTTDTT"
            + "DTTDTTDTTDTTDTTDTHDTHDTHDTHDTHDTHDTH";

    private static int firstDifference(String a, String b) {
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return i;
            }
        }
        return a.length() == b.length() ? -1 : n;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FIXTURES
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * stone / TS BOTTOM slab / {@link #SATURATING_OAK_COURSES} oak slabs, the topmost anchored and
     * sitting AT the cap / {@code subject}. Returns the subject's position.
     *
     * <p>Every oak course is anchored, which is what a player click does and what records each
     * course's height as a stored fact. At the shipped cap this builds exactly the four courses it
     * always did.
     */
    private static BlockPos buildTower(ServerWorld w, BlockPos ground, BlockState subject) {
        place(w, ground, Blocks.STONE.getDefaultState());
        place(w, ground.up(1), tsBottomSlab());
        for (int i = 0; i < SATURATING_OAK_COURSES; i++) {
            BlockPos course = ground.up(2 + i);
            place(w, course, bottomSlab(Blocks.OAK_SLAB));
            // The real placement sequence: onPlaced -> addAnchor fires for every player click, and
            // it is what records this course's height as a stored fact.
            SlabAnchorAttachment.addAnchor(w, course, w.getBlockState(course));
        }
        BlockPos subjectPos = ground.up(2 + SATURATING_OAK_COURSES);
        place(w, subjectPos, subject);
        return subjectPos;
    }

    /** The course both subjects rest on: the last oak slab, which sits AT the cap. */
    private static BlockPos supportCourse(BlockPos ground) {
        return ground.up(1 + SATURATING_OAK_COURSES);
    }

    private static void assertCoursesMatch(TestContext ctx, ServerWorld w, BlockPos a, BlockPos b) {
        for (int i = 0; i <= 1 + SATURATING_OAK_COURSES; i++) {
            BlockState sa = w.getBlockState(a.up(i));
            BlockState sb = w.getBlockState(b.up(i));
            ctx.assertTrue(sa.equals(sb),
                    "premise: the two columns must be the SAME tower below the subject — course "
                            + i + " differs (" + sa + " vs " + sb + ")");
            double da = dy(w, a.up(i));
            double db = dy(w, b.up(i));
            ctx.assertTrue(Math.abs(da - db) <= EPS,
                    "premise: the two columns must resolve identically below the subject — course "
                            + i + " reads " + da + " vs " + db);
        }
    }

    private static String battery(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 1, 3);

        BlockState[] bases = {
                Blocks.STONE.getDefaultState(),
                tsBottomSlab(),
        };
        BlockState[] supports = {
                bottomSlab(Blocks.OAK_SLAB),
                topSlab(Blocks.OAK_SLAB),
                doubleSlab(Blocks.OAK_SLAB),
                Blocks.STONE.getDefaultState(),
                Blocks.STRIPPED_JUNGLE_LOG.getDefaultState(),
                Blocks.OAK_FENCE.getDefaultState(),
                tsBottomSlab(),
        };
        BlockState[] subjects = {
                bottomSlab(Blocks.OAK_SLAB),
                topSlab(Blocks.OAK_SLAB),
                Blocks.CRAFTING_TABLE.getDefaultState(),
                Blocks.LANTERN.getDefaultState(),
                Blocks.STONE.getDefaultState(),
                Blocks.WHITE_CARPET.getDefaultState(),
                Blocks.OAK_FENCE.getDefaultState(),
        };

        StringBuilder out = new StringBuilder();
        for (int anchored = 0; anchored <= 1; anchored++) {
            for (BlockState base : bases) {
                for (BlockState support : supports) {
                    for (BlockState subject : subjects) {
                        clearColumn(w, ground);
                        place(w, ground, Blocks.STONE.getDefaultState());
                        place(w, ground.up(1), base);
                        place(w, ground.up(2), support);
                        if (anchored == 1) {
                            SlabAnchorAttachment.addAnchor(w, ground.up(2),
                                    w.getBlockState(ground.up(2)));
                        }
                        place(w, ground.up(3), subject);
                        out.append(token(dy(w, ground.up(1))));
                        out.append(token(dy(w, ground.up(2))));
                        out.append(token(dy(w, ground.up(3))));
                    }
                }
            }
        }
        clearColumn(w, ground);
        return out.toString();
    }

    /**
     * The shallow battery's supports and subjects, over a {@link #DEEP_LADDER_COURSES}-course
     * anchored slab ladder instead of a single base course. Three readings per column: the top of
     * the ladder, the support, the subject.
     */
    private static String deepBattery(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(6, 1, 6);

        BlockState[] supports = {
                bottomSlab(Blocks.OAK_SLAB),
                topSlab(Blocks.OAK_SLAB),
                doubleSlab(Blocks.OAK_SLAB),
                Blocks.STONE.getDefaultState(),
                Blocks.STRIPPED_JUNGLE_LOG.getDefaultState(),
                Blocks.OAK_FENCE.getDefaultState(),
                tsBottomSlab(),
        };
        BlockState[] subjects = {
                bottomSlab(Blocks.OAK_SLAB),
                topSlab(Blocks.OAK_SLAB),
                Blocks.CRAFTING_TABLE.getDefaultState(),
                Blocks.LANTERN.getDefaultState(),
                Blocks.STONE.getDefaultState(),
                Blocks.WHITE_CARPET.getDefaultState(),
                Blocks.OAK_FENCE.getDefaultState(),
        };

        int ladderTop = DEEP_LADDER_COURSES;          // ground.up(1..DEEP_LADDER_COURSES)
        int supportLevel = ladderTop + 1;
        int subjectLevel = supportLevel + 1;

        StringBuilder out = new StringBuilder();
        for (int anchored = 0; anchored <= 1; anchored++) {
            for (BlockState support : supports) {
                for (BlockState subject : subjects) {
                    clearDeepColumn(w, ground, subjectLevel + 1);
                    place(w, ground, Blocks.STONE.getDefaultState());
                    for (int i = 1; i <= DEEP_LADDER_COURSES; i++) {
                        BlockPos course = ground.up(i);
                        place(w, course, bottomSlab(Blocks.OAK_SLAB));
                        SlabAnchorAttachment.addAnchor(w, course, w.getBlockState(course));
                    }
                    place(w, ground.up(supportLevel), support);
                    if (anchored == 1) {
                        SlabAnchorAttachment.addAnchor(w, ground.up(supportLevel),
                                w.getBlockState(ground.up(supportLevel)));
                    }
                    place(w, ground.up(subjectLevel), subject);
                    out.append(token(dy(w, ground.up(ladderTop))));
                    out.append(token(dy(w, ground.up(supportLevel))));
                    out.append(token(dy(w, ground.up(subjectLevel))));
                }
            }
        }
        clearDeepColumn(w, ground, subjectLevel + 1);
        return out.toString();
    }

    private static void clearDeepColumn(ServerWorld w, BlockPos ground, int height) {
        for (int i = 0; i <= height; i++) {
            BlockPos p = ground.up(i);
            SlabAnchorAttachment.removeAnchor(w, p);
            SlabPlacementDyAttachment.clear(w, p);
            w.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.removeAnchor(w, p);
            SlabPlacementDyAttachment.clear(w, p);
        }
    }

    private static void clearColumn(ServerWorld w, BlockPos ground) {
        for (int i = 0; i <= 5; i++) {
            BlockPos p = ground.up(i);
            SlabAnchorAttachment.removeAnchor(w, p);
            w.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.removeAnchor(w, p);
        }
    }

    private static String token(double dy) {
        if (Math.abs(dy) <= EPS) {
            return "0";
        }
        if (Math.abs(dy + 0.5) <= EPS) {
            return "H";
        }
        if (Math.abs(dy + 1.0) <= EPS) {
            return "F";
        }
        if (Math.abs(dy + 1.5) <= EPS) {
            return "D";
        }
        // The two magnitudes only the deeper alphabet can mint. Named so the deep battery reads as
        // a string rather than as a list of angle-bracketed numbers; anything outside the set is
        // still written out in full, so an unexpected value can never hide inside a token.
        if (Math.abs(dy + 2.0) <= EPS) {
            return "T";
        }
        if (Math.abs(dy + 2.5) <= EPS) {
            return "X";
        }
        if (Math.abs(dy - 0.5) <= EPS) {
            return "U";
        }
        return "<" + dy + ">";
    }

    private static double dy(ServerWorld w, BlockPos pos) {
        return SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
    }

    private static void place(ServerWorld w, BlockPos pos, BlockState state) {
        w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }

    private static BlockState bottomSlab(Block slab) {
        return slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState topSlab(Block slab) {
        return slab.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
    }

    private static BlockState doubleSlab(Block slab) {
        return slab.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE);
    }

    private static BlockState tsBottomSlab() {
        return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    // ═══ window-radius characterisation (span/ray measurements) (DeepDyWindowCharacterisationTest) ═══
    // /**
    //  * STAGE 0 — RED-first characterisation for the dy-alphabet growth ruling
    //  * (the live ledger, "RULING (the maintainer, 2026-08-06): the dy alphabet grows —
    //  * GATED, cap -2.0"). <b>TESTS ONLY. No production behaviour is changed by this file, and nothing
    //  * here endorses a deeper alphabet — it MEASURES the three claims the ruling rests on so the later
    //  * stages argue from numbers instead of from reading.</b>
    //  *
    //  * <p><b>How a deep dy is forced without touching the resolver or its clamp.</b>
    //  * {@code SlabSupport.getYOffsetInner} reads {@code SlabPlacementDyAttachment.storedDy} first and
    //  * returns it verbatim — that IS LAW 1, and it is the only seam that can put a subject at a
    //  * magnitude the live resolver would clamp away. Every cell below writes the height straight into
    //  * the store with {@code SlabPlacementDyAttachment.record} and hard-asserts that
    //  * {@code getYOffset} hands it back unchanged. {@code minResolvedDy()} is never touched, and no cell
    //  * asks the resolver to PRODUCE a deep value.
    //  *
    //  * <p><b>Three measurements, three answers.</b>
    //  * <ul>
    //  *   <li><b>A — the pick window.</b> {@code SlabbedOffsetRaycast.consumeCell} tests
    //  *       {@code {C, C.down(), C.up()}}, a radius-1 window around each marched cell. A shape layer
    //  *       {@code L} can therefore only be attributed to its owner {@code P} when
    //  *       {@code |L - P.y| <= 1}. The cells here measure, from the REAL offset outline, which layers
    //  *       each deep subject occupies and then fire real rays at them.</li>
    //  *   <li><b>B — the depth budget.</b> {@code MAX_SUPPORT_RESOLVE_DEPTH} and what exhaustion
    //  *       returns, measured on real towers rather than read off the source.</li>
    //  *   <li><b>C — the triad.</b> Model, outline and raycast read INDEPENDENTLY at a deep magnitude,
    //  *       one cell each, because this line has a documented history of one leg moving while the
    //  *       suite stayed green.</li>
    //  * </ul>
    //  *
    //  * <p><b>CI STATUS OF THE DELIBERATELY-RED ROWS — ✅ INVERTED BY STAGE 1 (the window widening).</b>
    //  * As written for Stage 0 the window rows were CHARACTERISATION: they asserted what the radius-1
    //  * window actually did <em>including its misses</em>, so the suite stayed green and the RED was
    //  * documented rather than thrown, and each such assertion named in its message the exact condition
    //  * Stage 1 had to invert. Stage 1 widened {@code SlabbedOffsetRaycast.consumeCell} to
    //  * {@code SlabbedOffsetRaycast.WINDOW_RADIUS} and those rows went RED exactly as designed; they are
    //  * now inverted in place, so the same cells pin the ±2 behaviour that the widening bought. <b>No
    //  * row was deleted and no positive control was touched</b> — every measurement in this file is
    //  * still paired, in the same cell, with a {@code -1.0} control, so a broken aim still cannot
    //  * masquerade as a finding. The rows that were never about the window (B and C) are untouched.
    //  *
    //  * <p><b>The radius is no longer written down here.</b> {@link #TODAYS_WINDOW_RADIUS} now READS
    //  * {@code SlabbedOffsetRaycast.WINDOW_RADIUS}, which is itself derived from that class's
    //  * {@code DEEPEST_TARGETABLE_DY}. A third copy of the number is exactly how these two would drift
    //  * apart again.
    //  */

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
     * A-3 — <b>the ruling's derivation, turned into a measurement.</b> The maintainer ruled the cap at
     * {@code -2.0} rather than {@code -1.5} so that {@code minResolvedDy() == -(window radius)}
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
                "the identity minResolvedDy() == -(window radius) holds at -2.0 with radius 2 —"
                        + table);

        // INVERTED BY STAGE 1 (was: assertTrue(TODAYS_WINDOW_RADIUS == 1), "which is why
        // minResolvedDy() is -1.0"). The window now stands at the radius the RULED cap needs, ahead
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

        // Six courses as measured for Stage 0; DEEPENED at Stage 4 so there are still courses past
        // saturation once the cap can be -2.0 (saturation moves from index 2 to index 4). The
        // historical six is a floor, so no course this row ever measured is removed.
        BlockPos[] level =
                new BlockPos[Math.max(6, (int) Math.ceil(-SlabSupport.minResolvedDy() / 0.5) + 3)];
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
        // STATED AGAINST THE CAP (Stage 4, 2026-08-07): each course deepens half a block until the
        // clamp refuses, so course i reads max(-0.5*i, cap) and saturation lands at index
        // ceil(-cap / 0.5). At the shipped -1.0 cap that is index 2 and every course from L2 up
        // reads -1.0 — the exact assertion this loop has always made. At the ruled -2.0 cap the
        // ladder runs 0.0 / -0.5 / -1.0 / -1.5 / -2.0 / -2.0, which is what MEASUREMENT B becomes.
        for (int i = 2; i < level.length; i++) {
            double expected = Math.max(-0.5 * i, SlabSupport.minResolvedDy());
            ctx.assertTrue(Math.abs(dy[i] - expected) <= EPS,
                    "MEASUREMENT B: pre-store L" + i + " must read max(-0.5*" + i + ", cap) = "
                            + expected + " (cap " + SlabSupport.minResolvedDy() + ") —" + ladder);
        }
        int saturatedIndex = (int) Math.ceil(-SlabSupport.minResolvedDy() / 0.5);
        ctx.assertTrue(saturatedIndex < level.length,
                "fixture: the ladder must be tall enough to SATURATE, or 'pre-store saturates at "
                        + "the clamp' is untested — saturation needs index " + saturatedIndex
                        + " and the tower is " + level.length + " courses —" + ladder);

        // THE MEASURED ANSWER TO "WHAT DOES EXHAUSTION RETURN", AS MEASURED FOR STAGE 0: it
        // returned a bare -0.5, and IN THIS TOWER SHAPE that was PROVABLY INVISIBLE. Every course
        // here is a bottom slab, so every course computes max(childValue - 0.5, -1.0), and the
        // exhaustion floor -0.5 is exactly the largest child value for which the parent still
        // reads -1.0 — substituting it for any true child value <= -0.5 cannot change any parent.
        // At the depth budget of 4 this file was written against, L5's read was the first to reach
        // exhaustion (one depth per course, entered at 0); Stage 3 raised the budget, so no course
        // of a tower this height reaches it any more. The assertion is unchanged and still passes,
        // because what it pins is that the top of this ladder equals its saturated middle.
        //
        // ⚠️ THE FINDING WAS TRUE OF THIS SHAPE, NOT OF THE DEFECT (corrected by Stage 3,
        // 2026-08-07). A DROPPING tower deepens half a block per course, so it re-saturates on the
        // way back up and washes the exhaustion value out. A PASS-THROUGH tower — anchored
        // full-height courses over a lowered base — spends budget WITHOUT deepening and hands the
        // exhaustion value straight to the top of the stack unmodified. Measured there, the bare
        // -0.5 popped a course UP half a block at TODAY's -1.0 cap; see SupportDepthBudgetTest,
        // which builds both shapes side by side.
        int top = level.length - 1;
        ctx.assertTrue(Math.abs(dy[top] - dy[saturatedIndex]) <= EPS,
                "MEASUREMENT B: in a DROPPING tower the exhaustion path is unobservable at this "
                        + "clamp — the top course L" + top + " must read exactly what the first "
                        + "saturated course L" + saturatedIndex + " reads. This shape washes the "
                        + "exhaustion value out whatever it is; the PASS-THROUGH shape in "
                        + "SupportDepthBudgetTest is the one that does not —" + ladder);
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

        // The stored fact IS the resolved height at placement time, so it follows the same
        // max(-0.5*i, cap) ladder as the live read. Written that way at Stage 4 rather than as a
        // literal -1.0; the values at the shipped cap are unchanged.
        for (int i = 2; i < level.length; i++) {
            double expected = Math.max(-0.5 * i, SlabSupport.minResolvedDy());
            double stored = SlabPlacementDyAttachment.storedDy(w, level[i]);
            ctx.assertTrue(Math.abs(stored - expected) <= EPS,
                    "MEASUREMENT B: L" + i + " must carry a STORED height of " + expected
                            + " — the walk that produced it terminated at depth 1 on L" + (i - 1)
                            + "'s stored fact, so no course of a post-store tower ever spends the "
                            + "depth budget —" + facts);
        }
        ctx.complete();
    }

    /**
     * B-3 — <b>at a deep magnitude the CLAMP, not the depth budget, is the binding constraint.</b>
     * A real anchored tower course is given a stored {@code -2.0} (the ruling's proposed cap) and
     * the course above it is asked to seat on it. LAW 1 hands the support its {@code -2.0} back
     * verbatim; the follower asks for the seat, gets {@code -2.5}, and {@code minResolvedDy()}
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

        // THE MEASUREMENT, STATED AGAINST THE CAP (Stage 4, 2026-08-07). The raw seat is fixed by
        // the fixture at -2.5 (a stored -2.0 support, half-height arm); what the cap does with it
        // is the variable. At the shipped -1.0 cap the follower is flattened to -1.0 and the
        // measured hole is 1.5 blocks — the number Stage 0 reported and the reason the ruling
        // exists. With DEEP_DY_ALPHABET armed the cap refuses at -2.0 instead and the SAME fixture
        // measures a 0.5 hole: that shrinkage is exactly what Stage 4 buys, and it is measured here
        // rather than claimed.
        double rawSeat = supportDy - 0.5;
        double expectedFollower = Math.max(rawSeat, SlabSupport.minResolvedDy());
        double expectedGap = expectedFollower - rawSeat;
        ctx.assertTrue(rawSeat < SlabSupport.minResolvedDy() - EPS,
                "FIXTURE IS NO LONGER LOAD-BEARING: the raw seat " + rawSeat + " is not past the "
                        + "cap (" + SlabSupport.minResolvedDy() + "), so this cell would measure no "
                        + "clamp at all. Deepen the stored support — do not relax the row.");
        ctx.assertTrue(Math.abs(followerDy - expectedFollower) <= EPS,
                "MEASUREMENT B: the follower's raw seat is " + rawSeat + " and minResolvedDy() ("
                        + SlabSupport.minResolvedDy() + ") flattens it to " + expectedFollower
                        + ", got " + followerDy + ". The depth budget is not involved: the walk "
                        + "terminated at depth 1 on the support's stored fact.");
        ctx.assertTrue(Math.abs(gap - expectedGap) <= EPS,
                "MEASUREMENT B: the clamp opens a measured " + gap + "-block hole between a -2.0 "
                        + "support's top face and the block resting on it; at cap "
                        + SlabSupport.minResolvedDy() + " that hole must be " + expectedGap
                        + ". Stage 4 cannot ship the deeper alphabet without moving "
                        + "minResolvedDy() in the same change.");
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

    // ═══ depth budget derivation + exhaustion clamp ladders (SupportDepthBudgetTest) ═══
    // /**
    //  * STAGE 3 — <b>the depth budget, and the invariant its exhaustion path was violating.</b>
    //  *
    //  * <p>{@code SlabSupport.MAX_SUPPORT_RESOLVE_DEPTH} bounds the support-of-a-support walk. Until
    //  * 2026-08-07 it was a bare {@code 4} justified by a sentence about a constant in a different
    //  * paragraph, and on exhaustion {@code loweredFollowerDy} returned a bare {@code -0.5} — the "I
    //  * found nothing" floor — for a walk that had in fact found four consecutive lowered courses and
    //  * run out of budget mid-descent. The floor is SHALLOWER than the cap, so the first course past
    //  * the budget could read HIGHER than the course beneath it.
    //  *
    //  * <h2>THE INVARIANT</h2>
    //  *
    //  * <blockquote><b>A course may never resolve SHALLOWER than the course below it.</b></blockquote>
    //  *
    //  * <p>A tower is built by stacking; every course rests on the one under it, so its rendered height
    //  * is at most its support's. Nothing in the resolver is allowed to make a block rise as the stack
    //  * grows. That is the property these cells assert, over the whole ladder, on towers built
    //  * deliberately taller than the budget.
    //  *
    //  * <h2>Two tower SHAPES, because they consume the budget differently</h2>
    //  *
    //  * <p>{@code supportSeatDy} has a half-height arm (a bottom-slab support hands its follower
    //  * {@code supportDy - 0.5}) and a full-height arm (any support whose top face is at its own cell
    //  * top passes its dy through UNCHANGED). Both spend one unit of budget per course, but only the
    //  * first deepens, and that difference is the whole story:
    //  *
    //  * <ul>
    //  *   <li><b>DROPPING tower</b> (every course a bottom slab): each course deepens by half a block,
    //  *       so by the time the walk unwinds back up from an exhausted read it has re-saturated at the
    //  *       cap and the exhaustion value is washed out. This is Stage 0's B-1 shape, and it is why
    //  *       B-1 measured the exhaustion return as invisible.</li>
    //  *   <li><b>PASS-THROUGH tower</b> (anchored full-height courses stacked over a lowered base): the
    //  *       walk spends budget WITHOUT deepening, so whatever exhaustion returns is handed straight up
    //  *       to the player's eye, unmodified, by every course above it. <b>This shape is not in Stage
    //  *       0's measurements, and it is where the pop is real.</b></li>
    //  * </ul>
    //  *
    //  * <p><b>So Stage 0's "provably invisible" finding was true of the shape it measured and not of the
    //  * defect.</b> Recorded here rather than smoothed over, and reported to the maintainer as a correction to
    //  * MEASUREMENT B rather than a quiet fix.
    //  *
    //  * <h2>⚠️ AND STAGE 3'S OWN FIX TURNS OUT TO HAVE BEEN VALUE-COINCIDENTAL (Stage 4, 2026-08-07)</h2>
    //  *
    //  * <p>Exhaustion now returns {@code minResolvedDy()}, which is right for a DROPPING tower: you may
    //  * only ever round a truncated descent DOWN. But a PASS-THROUGH stack does not descend, so the
    //  * truthful answer for a truncated pass-through walk is <em>the value the stack is standing on</em>,
    //  * and exhaustion substitutes the cap for it. At the shipped {@code -1.0} cap those two numbers are
    //  * THE SAME — which is the only reason Stage 3 measured green here. Arm
    //  * {@code SlabSupport.DEEP_DY_ALPHABET} and they differ by a full block: the stack SINKS at the
    //  * exhaustion point, the exact mirror of the pop Stage 3 removed. Monotonicity still holds (sinking
    //  * is downward), which is why it needs its own assertion.
    //  *
    //  * <p><b>✅ CLOSED (maintainer ruling, 2026-08-19) — and the repair was not a better exhaustion
    //  * VALUE.</b> The paragraph above is kept because its diagnosis was right while its search for a
    //  * correct substitute was the wrong question: no constant can be right, because the fact that
    //  * decides a pass-through tower's height sits arbitrarily far below the truncated frame — which
    //  * is exactly what {@code identicalTruncationFramesWouldNeedDifferentExhaustionValues} proves,
    //  * and still proves. What was actually wrong is that the budget was being SPENT by a course that
    //  * descends nothing. It now belongs to descent alone — {@code supportSeatDy}'s half-height arm
    //  * spends it, the full-height arm does not — so a pass-through tower never exhausts it and never
    //  * needs a substitute at all. {@code SlabSupport.MAX_SUPPORT_WALK_STEPS} keeps the walk finite on
    //  * its own terms and answers the {@code -0.5} floor, because a walk that spent no descent has no
    //  * depth to report. Both deep-leg characterisation branches were deleted per the instruction
    //  * their own failure messages carried, and both rows now run ENFORCING at every cap.
    //  *
    //  * <h2>Fixtures are PRE-STORE by construction</h2>
    //  *
    //  * <p>The budget cannot be reached at all while the placement store answers: {@code addAnchor}
    //  * records each course's height and {@code anchoredCellDy} returns it at depth 1, terminating the
    //  * walk (Stage 0, B-2). Every tower below therefore keeps its anchors and CLEARS its stored
    //  * heights — the shape of every world saved before {@code d4f38510}, and the only shape in which
    //  * this constant is observable at all. Each cell hard-asserts that clearing worked, so none of them
    //  * can pass by silently answering from the store.
    //  */

    /**
     * Courses built beyond the budget. Three is enough for the exhausted read to be several
     * courses below the top of the tower in both shapes.
     */
    private static final int COURSES_PAST_THE_BUDGET = 3;

    /**
     * A DELIBERATELY TALLER pass-through fixture, used only by the cliff cell below.
     *
     * <p>It is a separate constant, and the cliff cell builds its own tower rather than growing
     * {@link #COURSES_PAST_THE_BUDGET}, so the two characterisation rows above keep measuring the
     * exact ladder their own messages describe. Seven courses past the budget makes the truncation
     * point travel far enough up the stack that the DISTANCE from it down to the bottom of the
     * tower — the lookahead an exhaustion rule would need in order to answer correctly — is
     * visibly different for each course, which is the point the cell measures.
     */
    private static final int TALL_COURSES_PAST_THE_BUDGET = 7;

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
        double resolved = SlabSupport.minResolvedDy();
        String measured = "L0=" + l0 + " L1=" + l1 + " dropPerCourse=" + measuredDropPerCourse
                + " MAX_SUPPORT_RESOLVE_DEPTH=" + cap
                + " DEEPEST_TARGETABLE_DY=" + targetable
                + " minResolvedDy()=" + resolved;
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

        // SUFFICIENCY AGAINST THE CAP THAT IS ACTUALLY IN FORCE. minResolvedDy() >=
        // DEEPEST_TARGETABLE_DY is an inequality during Stages 1-3 and closes at Stage 4, so a
        // budget sized for the deeper of the two is necessarily enough for the shallower.
        int neededForTodaysCap = (int) Math.ceil(-resolved / measuredDropPerCourse) + 2;
        ctx.assertTrue(cap >= neededForTodaysCap,
                "the budget must be at least what today's IN-FORCE cap needs (" + neededForTodaysCap
                        + "), got " + cap + " — " + measured);
        ctx.assertTrue(resolved >= targetable - EPS,
                "the standing identity minResolvedDy() >= DEEPEST_TARGETABLE_DY must hold, or the "
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
        ctx.assertTrue(Math.abs(dy[courses - 1] - SlabSupport.minResolvedDy()) <= EPS,
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
     *
     * <p><b>RED AGAIN IN THE DEEP LEG, for the mirror reason, until 2026-08-19.</b> That exhaustion
     * value is right for a DROPPING tower and wrong for this one, which descends nothing — so the
     * stack SANK a full block at the exhaustion point instead of popping up half of one, and the
     * two numbers were only equal at the shipped cap. The repair was not a third value: the budget
     * stopped being charged to courses that do not descend, so this shape no longer exhausts it.
     * The assertion below is unconditional again as a result — if it ever needs a leg-specific
     * branch, the budget is being spent by something that goes nowhere.
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
        for (int i = 3; i < level.length; i++) {
            ctx.assertTrue(Math.abs(dy[i] - dy[2]) <= EPS,
                    "a full-height support passes its dy through UNCHANGED, so every course of "
                            + "this stack must read what the first one reads (" + dy[2] + "); L"
                            + i + " reads " + dy[i] + ". A course that differs is the depth "
                            + "budget leaking into the answer — " + ladder);
        }

        ctx.complete();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 4. THE CLIFF — monotonicity is NECESSARY BUT NOT SUFFICIENT, and this is why
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * <b>No course may sit further below the course under it than that course's own seat can
     * lower it.</b>
     *
     * <p>{@link #assertNonIncreasing} is the guard Stage 3 shipped, and it is the guard that MISSED
     * the defect Stage 4 found: a ladder that steps down and then falls off a cliff is still
     * non-increasing. Monotonicity bounds the ladder on ONE side only. This cell adds the other
     * side, and the bound it uses is not a constant — it is read off the SEAT each course actually
     * rests on, because that is what decides how far one course may lower the next:
     *
     * <ul>
     *   <li>a course seated on a BOTTOM slab takes the half-height arm, so it may sit up to half a
     *       block below its support;</li>
     *   <li>a course seated on a FULL-HEIGHT top face takes the pass-through arm, which lowers it
     *       by NOTHING — it must read exactly what it is standing on.</li>
     * </ul>
     *
     * <p>Together with monotonicity that is a two-sided band, and a walk that substitutes a value
     * it did not measure falls outside it in whichever direction the substitution errs. The band is
     * the same shape in both directions on purpose: the pop Stage 3 removed and the sink Stage 4
     * found are the SAME defect seen from either end, and a repair that trades one for the other
     * must not be able to read as green here.
     *
     * <h2>✅ WAS RED IN THE DEEP LEG UNTIL 2026-08-19</h2>
     *
     * <p>Measured with {@code slabbed.deepDyAlphabet=true}: {@code L6 = -1.0}, {@code L7 = -2.0},
     * across a pass-through seat whose own arm may lower nothing at all — a full-block cliff where
     * the band allows zero. The cliff was the depth budget being spent by courses that descend
     * nothing; the budget now belongs to descent alone, so no course of this tower truncates and
     * the band runs ENFORCING in both legs.
     *
     * <p><b>And the cell MEASURES why no exhaustion return value can close it.</b> The walk for
     * course {@code k} truncates at course {@code k - MAX_SUPPORT_RESOLVE_DEPTH}, and the value it
     * has to invent there is that course's own resolved height — which is decided by the courses
     * BELOW the truncation point, {@code k - MAX_SUPPORT_RESOLVE_DEPTH} of them. That distance
     * grows with every course added to the stack, so the printed table below is the evidence that
     * the information the exhaustion path is missing is not one lookahead away, or two, but
     * unboundedly many. That measurement is what ruled out repairing this by choosing a better
     * exhaustion value, and it is why the repair instead stopped the budget from being spent by a
     * course that descends nothing — see {@code SlabSupport.MAX_SUPPORT_WALK_STEPS}.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void passThroughTowerNeverCliffsPastWhatItsSeatCanLower(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);
        int budget = SlabSupport.MAX_SUPPORT_RESOLVE_DEPTH;

        BlockPos[] level = buildPreStorePassThroughTower(ctx, w, ground,
                budget + TALL_COURSES_PAST_THE_BUDGET);
        double[] dy = readLadder(w, level);
        String ladder = format("tall pass-through", level, dy);
        System.out.println("[STAGE5-CLIFF] " + ladder);

        // ── PREMISES ────────────────────────────────────────────────────────────────────────
        ctx.assertTrue(Math.abs(dy[1] + 0.5) <= EPS,
                "premise: the second slab course must sit at -0.5, or this stack is not standing "
                        + "in a hole at all — " + ladder);
        for (int i = 2; i < level.length; i++) {
            ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, level[i]),
                    "premise: full-height course L" + i + " must be genuinely anchored, or the "
                            + "resolver never walks through it and this cell measures nothing — "
                            + ladder);
        }
        ctx.assertTrue(level.length > budget + 2,
                "premise: the tower must be taller than the budget plus the two slab courses, or "
                        + "no course truncates and the band is never tested where it matters — "
                        + ladder);

        // ── MONOTONICITY still holds, and is still asserted: this cell ADDS a bound, it does
        //    not replace the one Stage 3 shipped. Stage 3's pop returning must go RED here too.
        assertNonIncreasing(ctx, dy, ladder);

        double[] allowed = allowedDropPerCourse(w, level);
        assertWithinOneSeatsDrop(ctx, dy, allowed, ladder);

        // AND THE OTHER DIRECTION: whatever the exhaustion path does, it may never make a course
        // read SHALLOWER than what its own seat hands it. This is Stage 3's pop, and it stays
        // RED-able at every cap.
        for (int i = 1; i < dy.length; i++) {
            ctx.assertTrue(dy[i] <= dy[i - 1] + EPS,
                    "L" + i + " (" + dy[i] + ") reads SHALLOWER than the course it rests on, L"
                            + (i - 1) + " (" + dy[i - 1] + ") — Stage 3's pop, returning at the "
                            + "deeper cap — " + ladder);
        }
        ctx.complete();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 5. WHY NO EXHAUSTION VALUE CAN CLOSE IT — the fact that decides the answer is
    //    below the horizon, and this cell builds the two frames that prove it
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * <b>Two truncation frames that are INDISTINGUISHABLE to the exhaustion path, and that need
     * DIFFERENT answers.</b>
     *
     * <p>This cell exists so nobody spends another session looking for the right exhaustion
     * CONSTANT. There is not one, and this is the reason rather than the symptom.
     *
     * <p>When the walk runs out of budget at some cell, everything it is allowed to know is that
     * cell and its immediate surroundings. Build two towers:
     *
     * <ul>
     *   <li><b>A</b> — stone, a bottom slab, a second bottom slab, then anchored full-height
     *       courses. Its first full-height course rests on a slab that is itself lowered.</li>
     *   <li><b>B</b> — stone, ONE bottom slab, then anchored full-height courses. Its first
     *       full-height course rests on a slab that is flush.</li>
     * </ul>
     *
     * <p>The two cells this compares hold the SAME block, in the SAME anchor state, with the SAME
     * absence of a stored height, resting on the SAME kind of seat — and they resolve half a block
     * apart, because the thing that separates them is the slab TWO courses down. Push the towers
     * taller and that separating fact moves further below the horizon without bound while the two
     * frames stay identical. So the exhaustion return cannot be a constant, and it cannot be a
     * function of what the truncated frame can see either.
     *
     * <p><b>Neither leg characterises anything here.</b> These are plain resolved heights on towers
     * short enough that the budget is never reached, so this row is a statement about what the
     * right answers ARE, and it must stay green through any repair of the exhaustion path. If it
     * ever goes RED, the repair changed a value that was already correct.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void identicalTruncationFramesWouldNeedDifferentExhaustionValues(TestContext ctx) {
        ServerWorld w = ctx.getWorld();

        // Deliberately SHORT towers: three full-height courses cannot reach the budget, so every
        // number below is a fully resolved height and not a truncated one.
        BlockPos[] a = buildPreStorePassThroughTower(ctx, w,
                ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2), 3);
        BlockPos[] b = buildPreStoreOneSlabPassThroughTower(ctx, w,
                ctx.getAbsolutePos(BlockPos.ORIGIN).add(5, 1, 2), 3);

        // The two frames being compared: the first FULL-HEIGHT course of each tower.
        BlockPos frameA = a[2];
        BlockPos frameB = b[1];
        BlockPos seatA = a[1];
        BlockPos seatB = b[0];

        double dyA = dy(w, frameA);
        double dyB = dy(w, frameB);
        double seatDyA = dy(w, seatA);
        double seatDyB = dy(w, seatB);
        String measured = "A: frame=" + w.getBlockState(frameA).getBlock() + " dy=" + dyA
                + " seat=" + w.getBlockState(seatA).getBlock() + " seatDy=" + seatDyA
                + " | B: frame=" + w.getBlockState(frameB).getBlock() + " dy=" + dyB
                + " seat=" + w.getBlockState(seatB).getBlock() + " seatDy=" + seatDyB;
        System.out.println("[STAGE5-HORIZON] " + measured);

        // ── THE FRAMES ARE THE SAME, in every term the exhaustion path could branch on ──────
        ctx.assertTrue(w.getBlockState(frameA) == w.getBlockState(frameB),
                "premise: the two frames must hold the identical block state, or they are "
                        + "distinguishable and this cell proves nothing — " + measured);
        ctx.assertTrue(w.getBlockState(seatA) == w.getBlockState(seatB),
                "premise: the two frames must rest on the identical seat state — " + measured);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, frameA)
                        && SlabAnchorAttachment.isAnchored(w, frameB),
                "premise: both frames must be anchored, in the same way — " + measured);
        ctx.assertFalse(SlabPlacementDyAttachment.hasStoredDy(w, frameA)
                        || SlabPlacementDyAttachment.hasStoredDy(w, frameB),
                "premise: both frames must be PRE-STORE, or the walk terminates at depth 1 and "
                        + "the exhaustion path is never consulted — " + measured);

        // ── AND THEY RESOLVE HALF A BLOCK APART ─────────────────────────────────────────────
        ctx.assertTrue(Math.abs(dyA - dyB) > EPS,
                "the whole point of this cell: two frames the exhaustion path cannot tell apart "
                        + "must nevertheless resolve to different heights. They read the same ("
                        + dyA + "), so the fixture no longer separates them and the impossibility "
                        + "argument against a constant exhaustion value has lost its evidence — " + measured);

        // ── AND THE FACT THAT SEPARATES THEM IS ONE FURTHER LEVEL DOWN ──────────────────────
        ctx.assertTrue(Math.abs(seatDyA - seatDyB) > EPS,
                "the separating fact must live in the SEAT, one level below the frames — if the "
                        + "seats agree, the difference came from somewhere this cell does not "
                        + "name and the story is wrong — " + measured);
        ctx.assertTrue(Math.abs((dyA - seatDyA) - (dyB - seatDyB)) <= EPS,
                "both frames must take the SAME arm — each sits one half-height seat below its "
                        + "own support — so the only thing that differs is how deep that support "
                        + "already was, which is exactly what a truncated walk cannot see — "
                        + measured);
        ctx.complete();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * How far each course's own SEAT is entitled to lower it, read off the block it rests on
     * rather than written down as a constant. A bottom-slab seat takes the half-height arm (half a
     * block); anything else this fixture builds presents its cell top and takes the pass-through
     * arm (nothing at all). Index 0 is unused — the bottom course has no course below it.
     */
    private static double[] allowedDropPerCourse(ServerWorld w, BlockPos[] level) {
        double[] allowed = new double[level.length];
        for (int i = 1; i < level.length; i++) {
            BlockState seat = w.getBlockState(level[i - 1]);
            boolean halfHeight = seat.getBlock() instanceof SlabBlock
                    && seat.contains(SlabBlock.TYPE)
                    && seat.get(SlabBlock.TYPE) == SlabType.BOTTOM;
            allowed[i] = halfHeight ? 0.5 : 0.0;
        }
        return allowed;
    }

    /**
     * THE BAND, in one place: a course may sit below the course under it, but never by more than
     * that course's seat can lower it, and never above it at all.
     */
    private static void assertWithinOneSeatsDrop(TestContext ctx, double[] dy, double[] allowed,
                                                 String ladder) {
        for (int i = 1; i < dy.length; i++) {
            ctx.assertTrue(dy[i] >= dy[i - 1] - allowed[i] - EPS,
                    "CLIFF: L" + i + " (" + dy[i] + ") sits " + (dy[i - 1] - dy[i]) + " below the "
                            + "course it rests on, L" + (i - 1) + " (" + dy[i - 1] + "), but that "
                            + "seat may only lower it by " + allowed[i] + ". Monotonicity does not "
                            + "see this — a ladder that steps down and then falls off a cliff is "
                            + "still non-increasing — " + ladder);
            ctx.assertTrue(dy[i] <= dy[i - 1] + EPS,
                    "POP: L" + i + " (" + dy[i] + ") reads SHALLOWER than the course it rests on, "
                            + "L" + (i - 1) + " (" + dy[i - 1] + ") — " + ladder);
        }
    }

    /**
     * The pass-through shape, taller: stone / two bottom slabs / a stack of anchored full-height
     * courses. Identical in construction to the fixture inside
     * {@link #passThroughTowerPastTheBudgetNeverRises}, extracted so the cliff cell can build a
     * different HEIGHT without touching that cell's characterisation rows.
     */
    private static BlockPos[] buildPreStorePassThroughTower(TestContext ctx, ServerWorld w,
                                                            BlockPos ground, int fullHeightCourses) {
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos[] level = new BlockPos[2 + fullHeightCourses];
        for (int i = 0; i < level.length; i++) {
            level[i] = ground.up(i + 1);
            BlockState state = i < 2
                    ? bottomSlab(Blocks.OAK_SLAB)
                    : Blocks.STRIPPED_JUNGLE_LOG.getDefaultState();
            w.setBlockState(level[i], state, Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(w, level[i], w.getBlockState(level[i]));
        }
        clearStoredHeights(ctx, w, level);
        return level;
    }

    /**
     * The pass-through shape with only ONE bottom-slab course under the stack, so the stack stands
     * in a half-block hole instead of a full-block one. Same construction as
     * {@link #buildPreStorePassThroughTower} in every other respect.
     */
    private static BlockPos[] buildPreStoreOneSlabPassThroughTower(TestContext ctx, ServerWorld w,
                                                                   BlockPos ground,
                                                                   int fullHeightCourses) {
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos[] level = new BlockPos[1 + fullHeightCourses];
        for (int i = 0; i < level.length; i++) {
            level[i] = ground.up(i + 1);
            BlockState state = i < 1
                    ? bottomSlab(Blocks.OAK_SLAB)
                    : Blocks.STRIPPED_JUNGLE_LOG.getDefaultState();
            w.setBlockState(level[i], state, Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(w, level[i], w.getBlockState(level[i]));
        }
        clearStoredHeights(ctx, w, level);
        return level;
    }

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
                .append(SlabSupport.minResolvedDy()).append("):");
        for (int i = 0; i < level.length; i++) {
            sb.append(" L").append(i).append('=').append(dy[i]);
        }
        return sb.toString();
    }



    // ═══ pick-window widening: identical-ray equivalence + perf baselines (PickWindowWideningTest) ═══
    // /**
    //  * STAGE 1 — the pick window widens from radius 1 to {@link SlabbedOffsetRaycast#WINDOW_RADIUS},
    //  * and this file is the two things that widening owes:
    //  *
    //  * <ol>
    //  *   <li><b>A behaviour-neutrality MEASUREMENT, not an argument.</b> The class doc of
    //  *       {@code SlabbedOffsetRaycast} argues that at today's alphabet the outer ring of the window
    //  *       can only reach positions whose shape the ray does not intersect. This file runs one
    //  *       identical battery of rays through both radii over the same scene and compares every field
    //  *       of every result. An argument that is only written down is the shape of defect this line
    //  *       has shipped before.</li>
    //  *   <li><b>A permanent perf gate on the pick path.</b> The cost being accepted is real and
    //  *       forever: the crosshair raycast now probes five cells per marched DDA cell instead of three,
    //  *       +66%. The gate is not here to say that cost is acceptable — that is a
    //  *       frame-time question and only the maintainer's client can answer it. It is here so that a later
    //  *       change cannot quietly make it <em>worse</em> than what was signed off. This project has
    //  *       shipped a perf regression twice; the documented instrument for it is a counting gametest,
    //  *       never a wall clock.</li>
    //  * </ol>
    //  *
    //  * <p><b>Both cells share one scene and one ray battery</b> ({@link #buildScene},
    //  * {@link #rayBattery}) so the cost numbers are measured on exactly the geometry the neutrality
    //  * comparison covers, and neither can drift from the other.
    //  *
    //  * <p><b>Why comparing against radius 1 is legitimate and not a re-implementation.</b>
    //  * {@code SlabbedOffsetRaycast.raycastWithWindow} runs the production collector with an explicit
    //  * radius; radius 1 is literally the code that shipped before this stage, not a test-local model of
    //  * it. Production always goes through {@code raycast}, which fixes the radius at
    //  * {@code WINDOW_RADIUS}.
    //  */

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
        int recoveries = 0;
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
                // A mismatch is a RECOVERY when the wider window found a block the narrow one lost,
                // or found one strictly nearer the eye. That is the only direction the widening is
                // ever allowed to move an answer, at any cap.
                boolean recovered = after.getType() == HitResult.Type.BLOCK
                        && (before.getType() != HitResult.Type.BLOCK
                                || after.getPos().squaredDistanceTo(r.start())
                                        < before.getPos().squaredDistanceTo(r.start()) + EPS);
                if (recovered) {
                    recoveries++;
                }
                if (firstMismatch == null) {
                    firstMismatch = r + " radius" + PREVIOUS_WINDOW_RADIUS + "=" + describe(before)
                            + " radius" + SlabbedOffsetRaycast.WINDOW_RADIUS + "=" + describe(after)
                            + " recovered=" + recovered;
                }
                continue;
            }
            if (after.getType() == HitResult.Type.BLOCK) {
                blockHits++;
                BlockPos owner = after.getBlockPos();
                double dy = SlabSupport.getYOffset(w, owner, w.getBlockState(owner));
                if (Math.abs(dy + 0.5) <= EPS) {
                    hitsOnHalfLowered++;
                } else if (dy <= -1.0 + EPS) {
                    // -1.0 OR DEEPER. At the shipped cap that is exactly the old `dy == -1.0`
                    // test, since nothing deeper exists; with the deeper alphabet armed it also
                    // counts the -1.5 and -2.0 owners, which are the ones whose shape leaves the
                    // owner's cell by two.
                    hitsOnFullLowered++;
                }
            }
        }

        System.out.println("[STAGE1-NEUTRALITY] scene=" + scene + " rays=" + rays.size()
                + " blockHits=" + blockHits + " hitsOn-0.5=" + hitsOnHalfLowered
                + " hitsOnCapOrDeeper=" + hitsOnFullLowered + " mismatches=" + mismatches
                + " recoveries=" + recoveries
                + " deepDyAlphabet=" + SlabSupport.DEEP_DY_ALPHABET
                + " cap=" + SlabSupport.minResolvedDy());

        if (SlabSupport.DEEP_DY_ALPHABET) {
            // THE NEUTRALITY PREMISE IS FALSE AT THE DEEP CAP, AND THAT IS THE POINT (Stage 4,
            // 2026-08-07). Stage 1's claim was explicitly conditional — "while every offset the
            // build can mint stays within one cell of its owner". Arming the deeper alphabet is
            // exactly the change that makes it false: a shape at -1.5 or -2.0 occupies a layer TWO
            // cells from its owner, which is the reason the window was widened in the first place.
            // So in this leg the wider window MUST change answers, and every answer it changes must
            // be a RECOVERY — a block found where the narrow window lost it, or a nearer one. A
            // mismatch in the other direction would mean the widening had taken a target AWAY.
            ctx.assertTrue(mismatches > 0,
                    "THE WIDENING IS DOING NOTHING AT THE DEEP CAP. With the deeper alphabet armed "
                            + "the radius-1 window is supposed to lose targets the radius-"
                            + SlabbedOffsetRaycast.WINDOW_RADIUS + " window keeps, and none of the "
                            + rays.size() + " rays disagreed at all — so either the flag is not "
                            + "reaching the resolver or the scene grew no deep geometry, and this "
                            + "leg proves nothing about the window.");
            ctx.assertTrue(recoveries == mismatches,
                    "THE WIDENING LOST A TARGET. " + (mismatches - recoveries) + " of " + mismatches
                            + " disagreements are not recoveries: the wider window returned a MISS "
                            + "or a FARTHER hit than the narrow one. Widening may only ever add or "
                            + "improve a hit. first: " + firstMismatch);
        } else {
            ctx.assertTrue(mismatches == 0,
                    "STAGE 1 NEUTRALITY: widening the pick window from radius "
                            + PREVIOUS_WINDOW_RADIUS + " to " + SlabbedOffsetRaycast.WINDOW_RADIUS
                            + " must change NO answer while every offset the build can mint stays "
                            + "within one cell of its owner. " + mismatches + " of " + rays.size()
                            + " rays disagree; first: " + firstMismatch);
        }

        // Vacuity: the battery has to be aimed at the geometry under test.
        ctx.assertTrue(blockHits >= rays.size() / 4,
                "vacuity guard: a battery that mostly misses would agree trivially — only "
                        + blockHits + " of " + rays.size() + " rays hit a block");
        ctx.assertTrue(hitsOnHalfLowered > 0,
                "vacuity guard: no ray in the battery hit an owner resolved to -0.5, so the "
                        + "comparison never exercised an offset shape at all");
        ctx.assertTrue(hitsOnFullLowered > 0,
                "vacuity guard: no ray hit an owner resolved to -1.0 or deeper — those are the "
                        + "ONLY magnitudes whose shape leaves its owner's cell, so without one the "
                        + "comparison proves nothing about the window at all");
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
     * IS, in operations. Frame time is a live question and the maintainer's gate alone.
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
