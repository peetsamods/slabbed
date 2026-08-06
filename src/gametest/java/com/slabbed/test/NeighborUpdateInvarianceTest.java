package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * THE LAW GATE (post-mortem safeguard S-2, ported from the 26.2 donor 2026-08-06). See
 * {@code LAW.md}, which is supreme on this branch: <b>where a block is placed is where it goes and
 * STAYS — a neighbour update must never change its height (dy). No exceptions except a genuine
 * vanilla mechanic (the block itself being removed).</b>
 *
 * <p>This is the ONE test family that asserts the law directly, instead of asserting that the
 * height lanes agree with each other (which is all the pre-existing suite ever did). For each
 * SUBJECT (placed via the REAL {@code useOn} path — never {@code setBlockState} + a hand-rolled
 * {@code onPlaced}) it records the placement height exactly, then applies each NEIGHBOUR MUTATION
 * <em>without touching the subject's own cell</em> and asserts the height is byte-identical
 * afterward.
 *
 * <p><b>EXPECTED STATE ON THIS LINE: RED, by construction — this is a CHARACTERIZATION run, not a
 * RED-first fix task.</b> Per {@code LAW.md}'s "THIS LINE DOES NOT YET OBEY LAW 1" section, height
 * is recomputed live on every read; no frozen store exists yet. Each failure message enumerates
 * exactly which (subject, mutation) cells move a placed block — the punch-list for Phase 2B (the
 * frozen store). Do NOT attempt to make this class pass by patching {@code SlabSupport}; that is
 * explicitly out of scope for this task (see the handoff package
 * {@code s2-law-gate-characterization.md}). Because that RED is EXPECTED, the verdict defaults to
 * characterization — the matrix runs in full and prints its RED inventory as {@code [LAW-GATE]}
 * lines without failing the build; {@code -Dslabbed.lawGate=true} makes it blocking. See
 * {@link #LAW_GATE_PROPERTY}.
 *
 * <p><b>Port notes (semantic port from the 26.2 donor, not a diff-copy).</b> This line has none of
 * the donor's frozen-store machinery ({@code PLACEMENT_DY}, {@code FROZEN_DY_ENABLED},
 * {@code rawPlacementDyFact}, {@code addCompoundVisibleSideLowerSlab},
 * {@code addCompoundFullBlockAnchor}, {@code PlacementCaptureBoundaryGameTest.forceStore}), so the
 * donor's frozen-store toggle rows, the corruption/removal fixtures, and the door/bed pair rows
 * that depend on {@code forceStore} are NOT ported. The {@code markedSlabRig} compound-anchor
 * subject has no equivalent entry point on this line either; its slot is filled here by an
 * anchored-on-a-real -1.0 support subject built entirely from this line's own proven fixture
 * recipes ({@code AnchoredFollowerSupportDyTest}, {@code FlushSeatGuardTest},
 * {@code SlabOnLoweredBottomSlabTest}, {@code ThinTopLayerLoweringTest}), each subject placed
 * through this line's real Yarn {@code useOn} harness (reused from
 * {@link UseOnCombineVsExtendPlacementTest#mockPlayerHolding} /
 * {@link UseOnCombineVsExtendPlacementTest#useHeldItem}). The deep-rest tower subject asserts THIS
 * line's own {@code MIN_RESOLVED_DY} clamp (-1.0, pinned by {@code 76454c6d}/{@code 9e4dffb5}), not
 * the donor's uncapped -1.5.
 *
 * <p>Extending: add a SUBJECT builder or a MUTATION and every existing pairing exercises it — you
 * cannot add a height behaviour without this matrix testing whether it obeys the law.
 *
 * <p><b>VERDICT MODE — see {@link #LAW_GATE_PROPERTY}.</b> The matrix ALWAYS runs in full; only
 * what happens to the collected violations changes.
 */
public final class NeighborUpdateInvarianceTest {

    private static final double EPS = 1.0e-6;

    /**
     * Enforcement toggle, named for this codebase's existing {@code slabbed.*} sysprop convention
     * ({@code slabbed.disableStepCull}, {@code slabbed.serverHitTolerance},
     * {@code slabbed.targetDyOverlay}). Forwarded into the game JVM by {@code build.gradle}'s
     * {@code runGameTest} block, so {@code ./gradlew runGameTest -Dslabbed.lawGate=true} works.
     *
     * <p><b>OFF (default — what CI runs): CHARACTERIZATION.</b> Every subject is still built,
     * every mutation still applied, every violation still collected — byte-for-byte the same work
     * as enforcing mode. The only difference is the VERDICT: the violation inventory is PRINTED
     * (see {@link #logVerdict}) instead of thrown, so CI is green while this line is still in the
     * state {@code LAW.md} says it is in, and the characterization data — the whole value of this
     * test — is still produced on every single run.
     *
     * <p><b>ON: BLOCKING.</b> Identical run, and then the collected violations throw the exact
     * message this class has always thrown.
     *
     * <p><b>This is deliberately NOT a skip.</b> A test that returns success without doing its
     * work is the false-green class this project has been bitten by repeatedly, and it would be
     * worst here, in the one test that certifies the law. Nothing about the matrix is conditional
     * on this flag — only the throw is.
     *
     * <p><b>Flipping this default to ON is Phase 2's exit criterion</b> ({@code LAW.md}).
     */
    private static final String LAW_GATE_PROPERTY = "slabbed.lawGate";

    /** True when S-2 is a blocking gate; false (default) when it is a characterization run. */
    private static boolean enforcing() {
        return Boolean.getBoolean(LAW_GATE_PROPERTY);
    }

    // ── real-useOn placement (reuses this line's proven headless-useOn harness) ───────────────
    private static void place(TestContext ctx, Item item, BlockPos clicked, Direction face, double yNudge) {
        placeStack(ctx, new ItemStack(item), clicked, face, yNudge);
    }

    private static void placeStack(TestContext ctx, ItemStack stack, BlockPos clicked, Direction face, double yNudge) {
        ServerWorld world = ctx.getWorld();
        PlayerEntity player = UseOnCombineVsExtendPlacementTest.mockPlayerHolding(ctx, clicked.up(3), stack);
        Vec3d hit = Vec3d.ofCenter(clicked)
                .add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5 + yNudge, face.getOffsetZ() * 0.5);
        UseOnCombineVsExtendPlacementTest.useHeldItem(world, player, clicked, face, hit);
    }

    private static double dy(ServerWorld w, BlockPos p) {
        return SlabSupport.getYOffset(w, p, w.getBlockState(p));
    }

    /** Exact height identity (byte-identical intent), with -0.0 normalized to 0.0. */
    private static boolean sameHeight(double a, double b) {
        return Double.doubleToRawLongBits(a == 0.0 ? 0.0 : a)
                == Double.doubleToRawLongBits(b == 0.0 ? 0.0 : b);
    }

    private static void bslab(ServerWorld w, BlockPos p) {
        w.setBlockState(p, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
    }

    /** This suite's convention: the fabric-gametest-api-v1:empty structure's full 8x8x8 interior. */
    private static void clearArena(TestContext ctx, ServerWorld w) {
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        for (int x = 0; x <= 7; x++)
            for (int y = 0; y <= 7; y++)
                for (int z = 0; z <= 7; z++)
                    w.setBlockState(origin.add(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    // ── SUBJECTS: each builds a fresh rig via REAL useOn placement and returns the subject cell ──
    @FunctionalInterface
    private interface Subject {
        BlockPos build(TestContext ctx, ServerWorld w);
    }

    private record NamedSubject(String name, Subject builder) {
    }

    /**
     * Real-useOn SBSB tower (ground stone, then slab/stone alternating x4) whose top stone is the
     * -1.0 owner — the {@code AnchoredFollowerSupportDyTest} / donor C5 shape, built through real
     * clicks instead of {@code setBlockState}.
     */
    private static BlockPos minusOneLoweredStoneTowerRig(TestContext ctx, ServerWorld w, int x, int z) {
        BlockPos ground = ctx.getAbsolutePos(new BlockPos(x, 1, z));
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        Item[] tower = {
                Blocks.STONE_SLAB.asItem(), Blocks.STONE.asItem(),
                Blocks.STONE_SLAB.asItem(), Blocks.STONE.asItem()
        };
        BlockPos cursor = ground;
        for (Item item : tower) {
            place(ctx, item, cursor, Direction.UP, 0.0);
            cursor = cursor.up();
        }
        double topDy = dy(w, cursor);
        ctx.assertTrue(Math.abs(topDy + 1.0) <= EPS,
                "premise: real-useOn SBSB tower top stone should read -1.0, got " + topDy);
        return cursor;
    }

    /**
     * A full block that renders lowered (-0.5) with air to its NORTH — the
     * {@code FlushSeatGuardTest#cantileverWithAirBelowStillLowersAndAnchors} / donor
     * {@code loweredFullBlockWithAirWest} shape (rotated to NORTH; semantics identical).
     */
    private static BlockPos loweredFullBlockWithAirNorthRig(TestContext ctx, ServerWorld w, int x, int z) {
        BlockPos base = ctx.getAbsolutePos(new BlockPos(x, 1, z));
        w.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        bslab(w, base.up());
        BlockPos fb = base.up(2);
        w.setBlockState(fb, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        double fbDy = dy(w, fb);
        ctx.assertTrue(Math.abs(fbDy + 0.5) <= EPS,
                "premise: the lowered full block (north cell must be air) must render -0.5, got " + fbDy);
        return fb;
    }

    private static final List<NamedSubject> SUBJECTS = List.of(
            // #1 — control: flat full block on flat ground. Must be law-compliant already.
            new NamedSubject("flat_full_block_control", (ctx, w) -> {
                BlockPos ground = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
                w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                place(ctx, Blocks.STONE.asItem(), ground, Direction.UP, 0.0);
                return ground.up();
            }),
            // #2 — control: flat slab on flat ground. Must be law-compliant already.
            new NamedSubject("flat_slab_control", (ctx, w) -> {
                BlockPos ground = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
                w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                place(ctx, Blocks.STONE_SLAB.asItem(), ground, Direction.UP, 0.0);
                return ground.up();
            }),
            // #3 — an anchored full block on a real, anchored -1.0 support (translated equivalent of
            // the donor's markedSlabRig "full_block_on_lowered_stack" — this line has no compound
            // marked-slab entry point, so the deep support is a real-useOn SBSB tower instead).
            new NamedSubject("full_block_on_anchored_minus_one_support", (ctx, w) -> {
                BlockPos owner = minusOneLoweredStoneTowerRig(ctx, w, 3, 3);
                place(ctx, Blocks.STONE.asItem(), owner, Direction.UP, 0.0);
                return owner.up();
            }),
            // #4 — the legitimate cantilever (air below): FlushSeatGuardTest's own contract subject,
            // ported to real useOn. Donor's "cantilever_slab_beside_lowered_block".
            new NamedSubject("cantilever_slab_beside_lowered_block", (ctx, w) -> {
                BlockPos fb = loweredFullBlockWithAirNorthRig(ctx, w, 3, 3);
                place(ctx, Blocks.STONE_SLAB.asItem(), fb, Direction.NORTH, -0.25);
                return fb.north();
            }),
            // #5 — a slab on a lowered bottom slab (9e4dffb5's new behaviour): the seat is itself the
            // cantilever slab from SlabOnLoweredBottomSlabTest's recipe; the subject is a second slab
            // placed ON that seat via real useOn, expected to inherit -1.0. FIX (2026-08-06 S-2
            // matrix repair): the old -0.25 NORTH nudge minted a TOP seat (originalHitPos.y ==
            // targetY+0.25 >= targetY, so BlockItemPlacementIntentMixin's upperHalfIntent fires),
            // not BOTTOM, so the subject never actually built the -1.0 geometry its name claims —
            // it sat at the -0.5 floor and no mutation could ever move it. -0.75 keeps
            // originalHitPos.y (= targetY - 0.25) inside the lowered FB's LOWER visual half
            // ([targetY-0.5, targetY)), which the mixin's own comment documents as the BOTTOM lane.
            // Moving mutation: break_directly_below (removes the seat out from under the subject) —
            // whether this lands the subject on the -1.0-preserving placement-dy store or the -0.5
            // floor is exactly what this row now measures (LAW.md lane G).
            new NamedSubject("slab_on_lowered_bottom_slab", (ctx, w) -> {
                BlockPos fb = loweredFullBlockWithAirNorthRig(ctx, w, 3, 3);
                place(ctx, Blocks.STONE_SLAB.asItem(), fb, Direction.NORTH, -0.75);
                BlockPos seat = fb.north();
                ctx.assertTrue(w.getBlockState(seat).get(SlabBlock.TYPE) == SlabType.BOTTOM,
                        "premise: the seat must be a BOTTOM slab (the wrong nudge mints TOP via the intent mixin)");
                double seatDy = dy(w, seat);
                ctx.assertTrue(Math.abs(seatDy + 0.5) <= EPS,
                        "premise: the cantilever seat slab must render -0.5, got " + seatDy);
                place(ctx, Blocks.OAK_SLAB.asItem(), seat, Direction.UP, 0.0);
                return seat.up();
            }),
            // #6 — a carpet on a -1.0 owner (649f2090's fix): ThinTopLayerLoweringTest /
            // ClientCarpetDyAuthorityTest's donor evidence cell, ported to real useOn placement.
            new NamedSubject("carpet_on_minus_one_owner", (ctx, w) -> {
                BlockPos owner = minusOneLoweredStoneTowerRig(ctx, w, 3, 3);
                place(ctx, Blocks.WHITE_CARPET.asItem(), owner, Direction.UP, 0.0);
                BlockPos subject = owner.up();
                double d = dy(w, subject);
                ctx.assertTrue(Math.abs(d + 1.0) <= EPS,
                        "premise: carpet placed on the -1.0 owner should itself read -1.0, got " + d);
                return subject;
            }),
            // #7 — decoration placed flat then neighboured: the donor's
            // candle_placed_flat_then_neighbored, a clean case of live geometric re-derivation with
            // no anchor at all (LAW.md lane E/F). FIX (2026-08-06 S-2 matrix repair): the original
            // rig was vacuous — a candle has zero protection but reads its dy only through its
            // SUPPORT's neighbours (getYOffsetInner's cantilever-fallback branch calls
            // isAdjacentToLoweredSupport(pos.down()), not on the candle's own neighbours), and every
            // mutation in the matrix writes at the subject's own level or above. Plant a DORMANT
            // lowering source one cell below-and-east of the support so it is invisible at
            // construction (isAdjacentToLoweredSupport skips an air neighbour) but becomes live the
            // moment add_lowered_stack_east fills the support's east neighbour with a solid block —
            // hasLoweringSourceInColumnBelow then finds the planted slab and the candle's cantilever
            // fallback fires. Moving mutation: add_lowered_stack_east.
            new NamedSubject("candle_placed_flat_then_neighbored", (ctx, w) -> {
                BlockPos ground = ctx.getAbsolutePos(new BlockPos(3, 2, 3));   // y=2: room below for the dormant source
                w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                // Dormant at placement (ground.east() is air here, so isAdjacentToLoweredSupport
                // skips it) — add_lowered_stack_east later fills ground.east() with a solid block,
                // making it a genuine lowering source via hasLoweringSourceInColumnBelow, which is
                // what the candle's cantilever fallback actually reads.
                bslab(w, ground.east().down());
                place(ctx, Blocks.CANDLE.asItem(), ground, Direction.UP, 0.0);
                return ground.up();
            }),
            // #8 — chain/ceiling-scenery shape (today's rig investigation, LAW.md lane A/C): a
            // Y-axis chain placed via a REAL click on top of a lowered (-0.5) support, exercising the
            // isCeilingAttached classname-list lane lane C names (floor lever/button, Y-chain,
            // TOP-half trapdoor) through the real placement path this time, not a rig-authored cell.
            new NamedSubject("chain_on_lowered_support_ceiling_scenery", (ctx, w) -> {
                BlockPos ground = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
                w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                place(ctx, Blocks.STONE_SLAB.asItem(), ground, Direction.UP, 0.0);
                BlockPos slab = ground.up();
                place(ctx, Blocks.STONE.asItem(), slab, Direction.UP, 0.0);
                BlockPos support = slab.up();
                double supportDy = dy(w, support);
                ctx.assertTrue(Math.abs(supportDy + 0.5) <= EPS,
                        "premise: the chain's support must render -0.5, got " + supportDy);
                place(ctx, Blocks.IRON_CHAIN.asItem(), support, Direction.UP, 0.0);
                return support.up();
            }),
            // #9 — lane B (new, 2026-08-06 S-2 matrix repair): an ordinary full block beside a -1.0
            // owner. The live cantilever-adjacency check (isAdjacentToLoweredSupport) is a boolean —
            // "is the neighbour anchored / does it have a lowering source" — with no magnitude test,
            // so it renders this subject lowered (-0.5, the branch's own hardcoded floor) even
            // though its true neighbour sits at -1.0. The PERSISTED anchor twin
            // (qualifiesForAdjacentLoweredFullBlockAnchor) demands the neighbour's own
            // getYOffset == -0.5d EXACTLY, which a -1.0 owner never satisfies, so this subject gets
            // neither an anchor nor a frozen-flat marker — it is live-derived and unprotected while
            // still rendering lowered. Reachable only by ordinary full blocks and connecting
            // structurals (isOrdinaryAnchorCandidate filters everything else out first). The click
            // is a literal horizontal (NORTH) side click, not an up-face-edge inference, so
            // BlockItemPlacementIntentMixin's remap gate (yOffset == -0.5d at the target) does not
            // fire for a -1.0 owner — verified against the CURRENT mixin: the remap short-circuits
            // at "yOffset != -0.5d" and returns the untouched context, so the block lands via
            // vanilla's own ordinary side-placement at owner.north(). Moving mutation:
            // break_south_neighbor (breaks the owner, which sits south of the subject) — the live
            // boolean check finds no lowered neighbour once the owner is gone, and the subject
            // re-derives from 0.0-floor geometry with no anchor to hold it at -0.5.
            new NamedSubject("cantilever_full_block_beside_minus_one", (ctx, w) -> {
                BlockPos owner = minusOneLoweredStoneTowerRig(ctx, w, 3, 3);
                place(ctx, Blocks.STONE.asItem(), owner, Direction.NORTH, -0.25);
                return owner.north();
            })
    );

    // ── MUTATIONS: applied to the subject's neighbourhood, never its own cell ────────────────
    @FunctionalInterface
    private interface Mutation {
        void apply(ServerWorld w, BlockPos subject);
    }

    private record NamedMutation(String name, Mutation mutation) {
    }

    private static final List<NamedMutation> MUTATIONS = List.of(
            new NamedMutation("add_slab_north", (w, s) -> bslab(w, s.north())),
            new NamedMutation("add_slab_east", (w, s) -> bslab(w, s.east())),
            new NamedMutation("add_full_block_north",
                    (w, s) -> w.setBlockState(s.north(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS)),
            new NamedMutation("add_full_block_above",
                    (w, s) -> w.setBlockState(s.up(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS)),
            new NamedMutation("add_lowered_stack_east", (w, s) -> {
                w.setBlockState(s.east().down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                bslab(w, s.east());
            }),
            new NamedMutation("break_north_neighbor", (w, s) -> w.breakBlock(s.north(), false)),
            new NamedMutation("break_east_neighbor", (w, s) -> w.breakBlock(s.east(), false)),
            new NamedMutation("break_west_neighbor", (w, s) -> w.breakBlock(s.west(), false)),
            new NamedMutation("break_south_neighbor", (w, s) -> w.breakBlock(s.south(), false)),
            new NamedMutation("break_directly_below", (w, s) -> w.breakBlock(s.down(), false))
    );

    // ── the parametric law assertion: one test method per subject, loops all mutations ────────
    private void runSubject(TestContext ctx, NamedSubject subject) {
        ServerWorld w = ctx.getWorld();
        List<String> violations = new ArrayList<>();
        for (NamedMutation m : MUTATIONS) {
            clearArena(ctx, w);
            BlockPos subj = subject.builder().build(ctx, w);
            ctx.assertTrue(!w.getBlockState(subj).isAir(),
                    "premise: subject '" + subject.name() + "' failed to place");
            double before = dy(w, subj);
            m.mutation().apply(w, subj);
            // Vanilla-mechanic carve-out: if the mutation caused vanilla to remove the subject
            // itself (e.g. its support went away and the block can't survive), that is a genuine
            // vanilla mechanic, not a Slabbed height violation — allowed.
            if (w.getBlockState(subj).isAir()) {
                continue;
            }
            double after = dy(w, subj);
            if (!sameHeight(before, after)) {
                violations.add(m.name() + ": dy " + before + " -> " + after);
            }
        }
        // The verdict, and ONLY the verdict, depends on LAW_GATE_PROPERTY. The inventory is
        // printed either way; enforcing mode additionally throws the message it always threw.
        logVerdict(subject.name(), violations);
        if (enforcing()) {
            ctx.assertTrue(violations.isEmpty(),
                    "LAW VIOLATION — subject '" + subject.name()
                            + "' moved on neighbor edits (placed height must survive byte-identical):\n  "
                            + String.join("\n  ", violations));
        }
        ctx.complete();
    }

    /**
     * One greppable {@code [LAW-GATE]} line per SUBJECT — deliberately not per-mutation and not a
     * once-per-run aggregate.
     *
     * <p>Per-mutation would bury the signal in 80 lines of noise. A once-per-run aggregate has no
     * honest home: each subject is its own gametest method, the framework gives this class no
     * end-of-run hook, and a static tally would silently under-report whenever a batch runs a
     * subset or a sibling subject dies on a premise assert before it can report. Per-subject is
     * the largest unit that is guaranteed to be emitted for every subject that actually ran, which
     * is exactly the property that makes "the log is quiet" mean "nothing violated" rather than
     * "the test didn't run". It lives here, in the one shared runner, rather than in each of the
     * eight gametest methods, so a new subject cannot be added without reporting. (Deliberately no
     * literal annotation token in this javadoc — HANDOFF's suite-count script counts occurrences,
     * and a doc mention would inflate the expected total.)
     *
     * <p>The per-violation detail (<code>&lt;mutation&gt;: dy &lt;before&gt; -&gt; &lt;after&gt;</code>)
     * is the identical string the thrown message carries, so the Phase 2 punch-list is never lost
     * by running in the default mode.
     */
    private static void logVerdict(String subjectName, List<String> violations) {
        if (violations.isEmpty()) {
            System.out.println("[LAW-GATE] CLEAN — " + subjectName + ": all " + MUTATIONS.size()
                    + " mutations applied, 0 LAW 1 violations.");
            return;
        }
        String verdict = enforcing()
                ? "ENFORCING — 1 subject violated LAW 1 (failing the build): "
                : "CHARACTERIZATION — 1 subject violated LAW 1 (not failing the build; set -D"
                        + LAW_GATE_PROPERTY + "=true to enforce): ";
        System.out.println("[LAW-GATE] " + verdict + subjectName + ": "
                + String.join(" | ", violations));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatFullBlockControlSurvivesNeighborEdits(TestContext ctx) {
        runSubject(ctx, SUBJECTS.get(0));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatSlabControlSurvivesNeighborEdits(TestContext ctx) {
        runSubject(ctx, SUBJECTS.get(1));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockOnAnchoredMinusOneSupportSurvivesNeighborEdits(TestContext ctx) {
        runSubject(ctx, SUBJECTS.get(2));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cantileverSlabSurvivesNeighborEdits(TestContext ctx) {
        runSubject(ctx, SUBJECTS.get(3));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnLoweredBottomSlabSurvivesNeighborEdits(TestContext ctx) {
        runSubject(ctx, SUBJECTS.get(4));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void carpetOnMinusOneOwnerSurvivesNeighborEdits(TestContext ctx) {
        runSubject(ctx, SUBJECTS.get(5));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void candlePlacedFlatSurvivesNeighborEdits(TestContext ctx) {
        runSubject(ctx, SUBJECTS.get(6));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainOnLoweredSupportSurvivesNeighborEdits(TestContext ctx) {
        runSubject(ctx, SUBJECTS.get(7));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cantileverFullBlockBesideMinusOneSurvivesNeighborEdits(TestContext ctx) {
        runSubject(ctx, SUBJECTS.get(8));
    }
}
