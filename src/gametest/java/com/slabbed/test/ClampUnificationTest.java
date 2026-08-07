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
 * STAGE 2 — <b>the two clamps are one clamp.</b>
 *
 * <p>{@code SlabSupport} refuses to resolve a height deeper than
 * {@link SlabSupport#MIN_RESOLVED_DY} in more than one place, and until 2026-08-07 one of those
 * places did not say so. The support resolver clamps by name
 * ({@code Math.max(seat, MIN_RESOLVED_DY)}); the direct-custom surface lane in
 * {@code getYOffsetInner} — the lane a Terrain Slabs surface takes — wrote the magnitude out by
 * hand as {@code if (dy < -1.0) dy = -1.0;}. Both answer {@code -1.0} today, so nothing was
 * visibly wrong. The defect was latent: move the constant and the anonymous site keeps the old
 * cap, so a TS tower and a vanilla tower would sit at different heights in the same world for the
 * same reason. That is this project's shared-predicate half-fix shape, which it has paid for
 * repeatedly (see {@code LAW.md} and the flat-constant mirrors recorded there).
 *
 * <p><b>This cell is what makes Stage 4 a one-constant change.</b> Stage 2 itself is inert — the
 * unification is byte-identical at today's {@code -1.0}, which is exactly why it ships ahead of
 * the value change instead of with it.
 *
 * <h2>Why the tower is built the way it is</h2>
 *
 * The two lanes are mutually exclusive for any single cell — whichever branch of
 * {@code getYOffsetInner} claims a block, the other never runs — so "the same tower" cannot mean
 * "the same block resolved twice". It means: <b>one column, built once, with two subjects sitting
 * on its top course</b>, chosen so that each subject is claimed by a different clamp site while
 * both are handed the identical pre-clamp value of {@code -1.5}.
 *
 * <ul>
 *   <li>a vanilla SLAB subject is claimed by {@code getYOffsetInner}'s slab branch, which resolves
 *       through {@code loweredFollowerDy} → the NAMED clamp;</li>
 *   <li>a curated non-slab subject (a crafting table) over the same course falls through to the
 *       direct-custom surface lane → the formerly ANONYMOUS clamp.</li>
 * </ul>
 *
 * <p>Both arrive at {@code -1.5} by different arithmetic on the same numbers: the slab subject
 * takes its support's {@code -1.0} minus the half-block seat; the crafting table takes the
 * direct-custom surface's {@code -0.5} plus that same support's {@code -1.0}. Every premise is
 * hard-asserted, so neither can pass against a tower that never sank.
 *
 * <p><b>{@code -1.5} is the deepest pre-clamp value this build can present to either site</b>, and
 * that is a consequence of the clamp itself: every course of a tower is clamped as it resolves, so
 * no support can ever hand its follower a number deeper than {@code MIN_RESOLVED_DY - 0.5}. The
 * fixture asserts that the tower is deep enough to saturate rather than assuming it, so the day
 * the cap moves past {@code -1.5} this cell goes RED and says what to do about it instead of
 * passing vacuously.
 *
 * <h2>Terrain Slabs coverage — read this before citing the cell</h2>
 *
 * <p><b>This is NOT real Terrain Slabs coverage.</b> The real mod has client entrypoints that abort
 * the headless dedicated-server run, so it cannot load here at all. The TS course is
 * {@link TerrainSlabsTestShim}'s {@code terrain_slabs:test_slab} — a plain vanilla
 * {@link SlabBlock} registered under the {@code terrain_slabs} namespace, which the real
 * {@code TerrainSlabsCompat} classifier then treats as a {@code BOTTOM_LIKE} surface exactly as it
 * would the real thing. So what is exercised is the CLASSIFIER and the lane it selects, with real
 * TS geometry standing outside the test. A live pass is still the only thing that certifies the
 * mod itself. Every other TS gametest on this line carries the same limitation.
 */
public final class ClampUnificationTest {

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
            (int) Math.ceil(-SlabSupport.MIN_RESOLVED_DY / SEAT_DROP_PER_COURSE);

    /**
     * The pre-clamp value both lanes are handed by the tower below. Written down because both
     * assertions and the saturation premise depend on it, and because it is the deepest value this
     * build can produce (see the class doc): every course is itself clamped as it resolves, so the
     * deepest number any support can hand its follower is exactly one course past the cap.
     */
    private static final double RAW_TOWER_DY =
            SlabSupport.MIN_RESOLVED_DY - SEAT_DROP_PER_COURSE;

    // ─────────────────────────────────────────────────────────────────────────────
    // THE STAGE 2 CELL
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * ONE tower, two clamp sites, one answer.
     *
     * <p>Column (bottom to top): stone / {@code terrain_slabs:test_slab} BOTTOM / oak slab
     * ({@code -0.5}) / oak slab, anchored ({@code -1.0}). Two subjects rest on that top course in
     * two identical columns: an oak slab (named clamp) and a crafting table (formerly anonymous
     * clamp). Both are handed {@code -1.5}; both must report {@link SlabSupport#MIN_RESOLVED_DY}.
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
        ctx.assertTrue(Math.abs(supportDy - SlabSupport.MIN_RESOLVED_DY) <= EPS,
                "premise: the course both subjects rest on must resolve to the cap ("
                        + SlabSupport.MIN_RESOLVED_DY + ") — that is the number that makes BOTH "
                        + "pre-clamp values " + RAW_TOWER_DY + ", got " + supportDy);
        ctx.assertTrue(Math.abs(storedSupportDy - SlabSupport.MIN_RESOLVED_DY) <= EPS,
                "premise: that course must carry a STORED placement height of "
                        + SlabSupport.MIN_RESOLVED_DY + ", or the direct-custom lane reads its "
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
                + " cap=" + SlabSupport.MIN_RESOLVED_DY + " rawBoth=" + RAW_TOWER_DY;
        System.out.println("[STAGE2-CLAMP] " + measured);

        // ── THE ASSERTION THIS CELL EXISTS FOR ──────────────────────────────────────────────
        ctx.assertTrue(Math.abs(slabDy - tableDy) <= EPS,
                "THE TWO CLAMP SITES DISAGREE. The support resolver and the direct-custom surface "
                        + "lane were handed the same pre-clamp " + RAW_TOWER_DY + " for the same "
                        + "tower and returned different heights, so a Terrain Slabs tower and a "
                        + "vanilla tower now sit at different depths in the same world. Both sites "
                        + "must read SlabSupport.MIN_RESOLVED_DY — " + measured);

        // ── NON-VACUITY, asserted AFTER the property so a half-fix reports as a half-fix ─────
        // Order matters: if this guard came first, moving the cap without unifying the sites
        // would abort here and the disagreement above would never be reached — the run would
        // blame the fixture for a real product defect. Assert the property, then prove the
        // assertion was load-bearing.
        ctx.assertTrue(RAW_TOWER_DY < SlabSupport.MIN_RESOLVED_DY - EPS,
                "FIXTURE IS NO LONGER LOAD-BEARING: this cell proves the two clamps agree by "
                        + "making both saturate, and the deepest pre-clamp value this build can "
                        + "present is " + RAW_TOWER_DY + ", which is no longer past the cap ("
                        + SlabSupport.MIN_RESOLVED_DY + "). Add courses to buildTower until the "
                        + "raw value is past the new cap, and update RAW_TOWER_DY — do NOT delete "
                        + "this cell, it is what keeps the two clamp sites in step. — " + measured);

        ctx.assertTrue(Math.abs(slabDy - SlabSupport.MIN_RESOLVED_DY) <= EPS,
                "the support resolver must saturate at MIN_RESOLVED_DY — " + measured);
        ctx.assertTrue(Math.abs(tableDy - SlabSupport.MIN_RESOLVED_DY) <= EPS,
                "the direct-custom surface lane must saturate at MIN_RESOLVED_DY, not at a "
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
        double cap = SlabSupport.MIN_RESOLVED_DY;
        double targetable = SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY;
        int radius = SlabbedOffsetRaycast.WINDOW_RADIUS;
        String measured = "MIN_RESOLVED_DY=" + cap + " DEEPEST_TARGETABLE_DY=" + targetable
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
                + " cap=" + SlabSupport.MIN_RESOLVED_DY);
        System.out.println("[STAGE2-FINGERPRINT] " + fingerprint);

        ctx.assertTrue(fingerprint.equals(PINNED_FINGERPRINT),
                "STAGE 2 WAS NOT INERT, or the Stage 4 flag has leaked into a shape it must not "
                        + "reach. The resolver's answers over the 196-column battery differ from "
                        + "the values measured before the clamp sites were unified. Running cap is "
                        + SlabSupport.MIN_RESOLVED_DY + " (deepDyAlphabet="
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
     * has nothing to refuse. That is worth knowing (it is the blast radius of Maintainer's ruling stated
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
                + " deepDyAlphabet=" + deep + " cap=" + SlabSupport.MIN_RESOLVED_DY);
        System.out.println("[STAGE4-DEEPBATTERY] " + fingerprint);

        ctx.assertTrue(fingerprint.equals(pinned),
                (deep
                        ? "THE DEEP LEG MOVED since the flag was first armed. "
                        : "THE SHIPPED DEFAULT MOVED. A column deep enough for the cap to bite "
                                + "answers differently than it did before the Stage 4 flag "
                                + "existed, which is the leak this flag exists to prevent. ")
                        + "cap=" + SlabSupport.MIN_RESOLVED_DY + ", first difference at index "
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
     */
    private static final String PINNED_FINGERPRINT =
            "00H00H00H00H00H00H00H000000000000000000000000000000000000000000000000000000000000000"
            + "00000000000000000000000000000000000000000000H00F00H00H00000H00H0HF0HF0HF0HF0HH0HF0HF"
            + "0FH0FF0FH0FH0FH0FH0FH0HH0HF0HH0HH0H00HH0HH0000000000000000000000HH0HH0HH0HH0HH0HH0HH"
            + "0HH0HH0HH0HH0HH0HH0HH00H00F00H00H00000H00H00H00H00H00H00H00H00H000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "00H00F00H00H00000H00H0HF0HF0HF0HF0HH0HF0HF0FH0FF0FH0FH0FH0FH0FH0HH0HF0HH0HH0H00HH0HH"
            + "0HH0HH0HH0HH0HH0HH0HH0HH0HH0HH0HH0HH0HH0HH0HH0HH0HH0HH0HH0HH0HH00H00F00H00H00000H00H";

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
}
