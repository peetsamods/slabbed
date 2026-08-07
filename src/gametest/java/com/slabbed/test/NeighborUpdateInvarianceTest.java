package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementDyAttachment;
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
 * <p><b>STATUS (2026-08-06): PASSING, BLOCKING BY DEFAULT.</b> This class landed as a
 * characterization run because height was recomputed live on every read and RED was expected by
 * construction. The placement-dy store ({@code d4f38510}), the ceiling role predicate
 * ({@code 3a7c17c0}), and the two closing fixes ({@code 7756d152}, {@code c51ec869}) closed every
 * subject this matrix could reach. The verdict now defaults to blocking — see
 * {@link #LAW_GATE_PROPERTY} — and any future RED here is a real regression, not an expected
 * characterization finding. Lanes A–F in {@code LAW.md} beyond this matrix's 11 subjects are not
 * proven closed by this test passing; see its reachability table before assuming full coverage.
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
 * <p><b>VACUITY RULE ({@code LAW.md}, standing): every subject must name, in a comment, the
 * mutation that would move it.</b> A green row here is proof ONLY if at least one mutation
 * provably reaches the subject's resolver; a row nothing can move reads as coverage while
 * asserting nothing, which is worse than no row at all. All eleven subjects below carry that comment
 * and every claim in one was MEASURED, not reasoned: build the subject, strip its protection
 * ({@code SlabAnchorAttachment.removeAnchor} clears the anchor, the frozen-flat marker and the
 * stored magnitude in one call), apply the mutation, and see whether the resolver's answer moves.
 * If it does not move even bare, the mutation cannot reach the subject and the row is decorative.
 * Re-run that measurement before trusting any row you did not write.
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
     * {@code runGameTest} block.
     *
     * <p><b>DEFAULT (2026-08-06, flipped by the maintainer): ON — BLOCKING.</b> This line now passes S-2
     * with all 9 subjects CLEAN ({@code 7756d152}, {@code c51ec869}), so {@code LAW.md}'s Phase 2
     * exit criterion is met and the default flips. {@code ./gradlew build runGameTest} — no flag —
     * now enforces the law directly: every subject is built, every mutation applied, and any
     * violation throws the exact message this class has always thrown.
     *
     * <p><b>{@code -Dslabbed.lawGate=false}: CHARACTERIZATION.</b> Identical run — nothing about
     * the matrix is conditional on this flag, only the verdict — but a violation is PRINTED (see
     * {@link #logVerdict}) instead of thrown. This is the escape hatch for a session that
     * deliberately introduces a new RED subject or mutation and needs to see the inventory without
     * failing the build while it is being fixed forward — not a way to silence a real regression.
     *
     * <p><b>This was deliberately never a skip, in either direction.</b> A test that returns
     * success without doing its work is the false-green class this project has been bitten by
     * repeatedly, and it would be worst here, in the one test that certifies the law.
     */
    private static final String LAW_GATE_PROPERTY = "slabbed.lawGate";

    /** True (default) when S-2 is a blocking gate; false only when explicitly disabled. */
    private static boolean enforcing() {
        return Boolean.parseBoolean(System.getProperty(LAW_GATE_PROPERTY, "true"));
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
     * The same SBSB tower as {@link #minusOneLoweredStoneTowerRig}, built one cell LOWER (ground at
     * plot y=0 instead of y=1) so a subject two cells above its top still leaves the
     * {@code add_full_block_above} mutation a cell inside the 8x8x8 plot. Same premise assert, same
     * real-useOn clicks; the only difference is the base height.
     */
    private static BlockPos minusOneLoweredStoneTowerRigAtGroundLevel(TestContext ctx, ServerWorld w,
                                                                      int x, int z) {
        BlockPos ground = ctx.getAbsolutePos(new BlockPos(x, 0, z));
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
                "premise: ground-level SBSB tower top stone should read -1.0, got " + topDy);
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
            // #1 — control: a flat-placed full block, now held flat under REAL PRESSURE.
            // REPAIR (2026-08-06, S-2 matrix audit, second pass): the original rig — stone on flat
            // ground — was VACUOUS. Measured with every protection stripped, 0 of 10 mutations
            // moved it, so its green row proved only that the harness executed. A control that
            // nothing can move is not a control.
            //
            // It now runs the candle's own technique (a DORMANT lowering source, planted so it is
            // invisible at construction and becomes live only when the mutation fires) against a
            // PROTECTED subject, which is the whole point: same class of pressure, opposite
            // outcome, because this subject goes through freezeLoweredOnPlace and the candle does
            // not (isOrdinaryAnchorCandidate accepts a solid full block and rejects a candle).
            //
            // Geometry: the subject is placed FLAT with AIR BELOW (a cantilever). That is not
            // decoration — it is the ONLY geometry in which any mutation can reach a full block's
            // resolver at all. Resting on solid ground, every lowering lane is closed by
            // construction: the gap-fill / cantilever-adjacency branch is gated on
            // pos.down().isAir(), shouldOffset's column walk stops dead at the opaque support
            // (hasSlabInColumn's natural-terrain stop), and isClassFlushPinnedSubject pins any
            // opaque full cube at 0.0 before the follow-your-support lanes are ever reached.
            //
            // Moving mutation: add_full_block_north. It fills the north cell with a SOLID block,
            // which hasLoweringSourceInColumnBelow then resolves as lowered via the planted slab
            // beneath it, so isAdjacentToLoweredSupport(subject) turns true and getYOffsetInner's
            // cantilever branch returns -0.5. MEASURED with the frozen-flat marker removed:
            // 0.0 -> -0.5. With the marker: 0.0 -> 0.0. The marker is the load-bearing part, so
            // this CLEAN row now asserts LAW 1's flat half — "a block placed flat stays flat even
            // when a lowering source appears beside it" — instead of asserting nothing.
            // NOT add_lowered_stack_east (the candle's mutation): it plants a SLAB at the
            // subject's own level, and isAdjacentToLoweredSupport skips slab neighbours outright,
            // so it is provably inert against ANY full block — measured 0.0 -> 0.0 with protection
            // stripped. The two controls therefore take different mutations on purpose.
            new NamedSubject("flat_full_block_control", (ctx, w) -> {
                BlockPos subject = ctx.getAbsolutePos(new BlockPos(3, 3, 3));
                // The block clicked to place the subject: plain, flat, no lowering source of its
                // own (air below it too, so it can never become one).
                w.setBlockState(subject.west(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                // DORMANT: subject.north() is air at construction and isAdjacentToLoweredSupport
                // skips air neighbours, so this slab is invisible to every lane until
                // add_full_block_north fills the cell above it.
                bslab(w, subject.north().down());
                place(ctx, Blocks.STONE.asItem(), subject.west(), Direction.EAST, 0.0);
                ctx.assertTrue(w.getBlockState(subject).isOf(Blocks.STONE),
                        "premise: the control block must land in the cantilever cell, got "
                                + w.getBlockState(subject));
                ctx.assertTrue(w.getBlockState(subject.down()).isAir(),
                        "premise: the control must be a CANTILEVER (air below) or no mutation can "
                                + "reach a full block's resolver at all");
                double d = dy(w, subject);
                ctx.assertTrue(Math.abs(d) <= EPS,
                        "premise: the control must be placed FLAT (the dormant source must still "
                                + "be dormant), got " + d);
                ctx.assertTrue(SlabAnchorAttachment.isFrozenFlat(w, subject),
                        "premise: this row's protection IS freezeLoweredOnPlace's FROZEN_FLAT "
                                + "marker — without the marker the row proves nothing");
                return subject;
            }),
            // #2 — control: a flat-placed SLAB, held flat under the candle's own mutation.
            // REPAIR (2026-08-06, S-2 matrix audit, second pass): same vacuity as #1 — measured
            // 0 of 10 mutations moved the old ground-resting rig with protection stripped.
            //
            // Geometry: air below, for a different reason than #1. A BOTTOM slab resting on flush
            // ground can never take the side-adjacency lane at all: isAdjacentSideSlabLowered's
            // FLUSH-SEAT GUARD refuses (followerSinksIntoFlushSeat is true for a BOTTOM slab, and
            // flush stone is a flush seat), and hasLoweredNonSlabTopSupport does not recognise an
            // adjacency-lowered support either. With air below, the guard passes and the lane is
            // live.
            //
            // Moving mutation: add_lowered_stack_east — the CANDLE'S mutation, the contrast the
            // whole repair is for. It plants a bottom slab at the subject's own level whose new
            // stone support sits on the DORMANT slab planted two cells down, so
            // isVerticallyLoweredSlabSource sees it, isAdjacentSideSlabLowered's BFS finds it, and
            // getYOffsetInner's slab branch would return -0.5. MEASURED with the frozen-flat
            // marker removed: 0.0 -> -0.5. With it: 0.0 -> 0.0, because the slab branch reads
            // isFrozenFlat BEFORE any geometric inheritance walk. Same scene, same mutation as
            // the candle; the candle has no protection and goes RED, this one is protected and
            // stays CLEAN. That contrast is the proof.
            new NamedSubject("flat_slab_control", (ctx, w) -> {
                BlockPos subject = ctx.getAbsolutePos(new BlockPos(3, 3, 3));
                w.setBlockState(subject.west(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                // DORMANT: two cells below-and-east, so nothing at the subject's level or its
                // support's level sees it until add_lowered_stack_east builds the stack on top.
                bslab(w, subject.east().down().down());
                place(ctx, Blocks.STONE_SLAB.asItem(), subject.west(), Direction.EAST, -0.25);
                var placed = w.getBlockState(subject);
                ctx.assertTrue(placed.getBlock() instanceof SlabBlock
                                && placed.get(SlabBlock.TYPE) == SlabType.BOTTOM,
                        "premise: the control must be a BOTTOM slab in the cantilever cell, got "
                                + placed);
                ctx.assertTrue(w.getBlockState(subject.down()).isAir(),
                        "premise: the control must be a CANTILEVER (air below) — on a flush seat "
                                + "the flush-seat guard closes the only lane that can reach it");
                double d = dy(w, subject);
                ctx.assertTrue(Math.abs(d) <= EPS,
                        "premise: the control must be placed FLAT (the dormant source must still "
                                + "be dormant), got " + d);
                ctx.assertTrue(SlabAnchorAttachment.isFrozenFlat(w, subject),
                        "premise: this row's protection IS freezeLoweredOnPlace's FROZEN_FLAT "
                                + "marker — without the marker the row proves nothing");
                return subject;
            }),
            // #3 — an anchored full block on a real, anchored -1.0 support (translated equivalent of
            // the donor's markedSlabRig "full_block_on_lowered_stack" — this line has no compound
            // marked-slab entry point, so the deep support is a real-useOn SBSB tower instead).
            // Moving mutation: break_directly_below — it destroys the -1.0 support this subject's
            // magnitude was derived from. VERIFIED reachable (2026-08-06 audit, second pass): with
            // the anchor and the store stripped this subject reads -1.0 -> 0.0. It holds -1.0
            // because SlabPlacementDyAttachment stores the number, and -1.0 is NOT the value any
            // fallback would produce (loweredFollowerDy floors at -0.5), so this row discriminates
            // the STORE from both the floor and the live walk. This is LAW.md lane G's own subject.
            new NamedSubject("full_block_on_anchored_minus_one_support", (ctx, w) -> {
                BlockPos owner = minusOneLoweredStoneTowerRig(ctx, w, 3, 3);
                place(ctx, Blocks.STONE.asItem(), owner, Direction.UP, 0.0);
                return owner.up();
            }),
            // #4 — the legitimate cantilever (air below): FlushSeatGuardTest's own contract subject,
            // ported to real useOn. Donor's "cantilever_slab_beside_lowered_block".
            // Moving mutation: break_south_neighbor — it breaks `fb`, the lowered full block that
            // is this subject's ONLY lowering source (the subject cantilevers off it with air
            // below, so nothing else can hold it down). VERIFIED reachable (2026-08-06 audit,
            // second pass), and the row is a real never-pop assertion: with the anchor stripped
            // the subject reads -0.5 -> 0.0, so the persisted side-slab anchor is what holds it.
            // HONEST LIMIT OF THIS ROW, stated rather than implied: it does NOT discriminate the
            // placement-dy STORE. The stored value is -0.5 and loweredFollowerDy's floor is also
            // -0.5 (supportSeatDy over the now-empty cell below returns NaN), so measured with the
            // store cleared but the anchor kept, this subject still reads -0.5. This row proves
            // the ANCHOR is load-bearing; only #3, #5 and #6 prove the store's magnitude is.
            // Note the geometry the -0.25 NORTH nudge actually builds: the intent mixin's
            // upperHalfIntent gate fires on a -0.5 target (originalHitPos.y == fb.y + 0.25 >=
            // fb.y), so the subject is a TOP slab. That is the FlushSeatGuardTest shape and is
            // correct here — a TOP slab is the one follower exempt from followerSinksIntoFlushSeat
            // — but it is written down so it cannot be mistaken for the BOTTOM-slab lane #5 tests.
            new NamedSubject("cantilever_slab_beside_lowered_block", (ctx, w) -> {
                BlockPos fb = loweredFullBlockWithAirNorthRig(ctx, w, 3, 3);
                place(ctx, Blocks.STONE_SLAB.asItem(), fb, Direction.NORTH, -0.25);
                BlockPos subject = fb.north();
                ctx.assertTrue(w.getBlockState(subject).getBlock() instanceof SlabBlock
                                && w.getBlockState(subject).get(SlabBlock.TYPE) == SlabType.TOP,
                        "premise: the -0.25 nudge on a -0.5 target mints a TOP slab via the intent "
                                + "mixin's upperHalfIntent gate — got " + w.getBlockState(subject));
                return subject;
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
            // #6 — a carpet whose SUPPORT changes height underneath it while staying standing.
            // REPAIR (2026-08-06, S-2 matrix audit, second pass) — and a RENAME, because the old
            // name described geometry this subject no longer has. Was `carpet_on_minus_one_owner`:
            // a carpet on the -1.0 tower top. LAW.md flagged it vacuous and the measurement agreed
            // — its ONLY live mutation was break_directly_below, which removes the carpet's
            // support, so vanilla removes the carpet, the runner takes its legitimate
            // vanilla-mechanic carve-out, and the subject contributed ZERO assertions. The other
            // nine mutations could not touch it: a carpet on an anchored -1.0 tower resolves
            // through the anchor branch, and nothing at or above the subject's level changes what
            // that branch reads.
            //
            // The fix the law actually needs is a support that CHANGES HEIGHT WITHOUT BEING
            // DESTROYED. The mutation set makes exactly one such move available, so this rig is
            // built around it: add_lowered_stack_east writes at s.east().down(), which is at the
            // SUPPORT'S level, not the subject's. Everything else (break_{north,east,west,south})
            // acts at the subject's own level and can never reach a support's lateral
            // relationships; only break_directly_below reaches the support at all, and that
            // destroys it. So the support is a BOTTOM SLAB with air beneath it (a floating seat —
            // legal for a slab, and required so isAdjacentSideSlabLowered's flush-seat guard does
            // not refuse), and the lowering source that will reach it is planted DORMANT two cells
            // below-and-east.
            //
            // Moving mutation: add_lowered_stack_east. Its new stone lands beside the support slab
            // and rests on the planted slab, so isLoweredSideSlabSource sees it,
            // isAdjacentSideSlabLowered turns the support from flat to -0.5, and the carpet's own
            // live lane (shouldOffset -> "bottom slab below that is adjacent-side-slab lowered")
            // would resolve -1.0. MEASURED: with all protection stripped, -0.5 -> -1.0.
            //
            // Result: CLEAN, and this is the STRONGEST discriminator in the matrix. Measured with
            // the decorative anchor KEPT but the placement-dy store CLEARED, the subject still
            // moves -0.5 -> -1.0 (loweredBottomSlabSupportDy reports the support's new -0.5, so
            // supportSeatDy compounds to -1.0). The anchor alone does NOT hold this cell: the
            // stored NUMBER does. That is LAW.md lane G's thesis — "an anchor is a boolean fact,
            // not a stored number" — asserted directly, on a carpet, from the flat side.
            //
            // The -1.0 carpet evidence cell the old rig carried is not lost: it is the standing
            // subject of ThinTopLayerLoweringTest and ClientCarpetDyAuthorityTest, both green.
            new NamedSubject("carpet_on_laterally_lowered_slab_support", (ctx, w) -> {
                BlockPos subject = ctx.getAbsolutePos(new BlockPos(3, 3, 3));
                // The support: a floating BOTTOM slab. Floating on purpose — a flush seat under it
                // would make isFlushSeat refuse the side-adjacency lane and the rig would go inert.
                bslab(w, subject.down());
                // DORMANT: two cells below-and-east. Nothing at the support's level sees it until
                // add_lowered_stack_east builds the stone on top of it.
                bslab(w, subject.east().down().down());
                place(ctx, Blocks.WHITE_CARPET.asItem(), subject.down(), Direction.UP, 0.0);
                ctx.assertTrue(w.getBlockState(subject).isOf(Blocks.WHITE_CARPET),
                        "premise: the carpet must land on the slab support, got "
                                + w.getBlockState(subject));
                ctx.assertTrue(w.getBlockState(subject.down(2)).isAir(),
                        "premise: the support slab must FLOAT (air below) or the flush-seat guard "
                                + "closes the lane the moving mutation needs");
                double supportDy = dy(w, subject.down());
                ctx.assertTrue(Math.abs(supportDy) <= EPS,
                        "premise: the support must start FLAT — the dormant source must still be "
                                + "dormant, got " + supportDy);
                double d = dy(w, subject);
                ctx.assertTrue(Math.abs(d + 0.5) <= EPS,
                        "premise: a carpet on a flat bottom slab reads -0.5, got " + d);
                ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, subject)
                                && SlabPlacementDyAttachment.hasStoredDy(w, subject),
                        "premise: this row's protection IS the decorative anchor PLUS the stored "
                                + "magnitude — the anchor alone measurably does not hold this cell");
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
            // Moving mutation: break_directly_below — it destroys the -0.5 support the standing
            // chain is following. RE-VERIFIED post-lane-C (2026-08-06 audit, second pass), rather
            // than inferred from the historical RED: the chain SURVIVES the break (vanilla imposes
            // no support requirement on a chain, so the carve-out does not fire and the row really
            // does assert), and with the anchor stripped it reads -0.5 -> 0.0 — byte-identical to
            // the RED this subject reported before 3a7c17c0 taught isCeilingAttached to ask the
            // ROLE instead of the block TYPE. So the decorative-object anchor is what holds it,
            // and this row is the standing regression test for that fix.
            // Like #4, it does NOT discriminate the store: measured with the store cleared and the
            // anchor kept it still reads -0.5, because loweredFollowerDy's floor over the emptied
            // cell below is also -0.5.
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
            }),
            // #10 — THE MATRIX'S COVERAGE BOUNDARY, closed (2026-08-06, the maintainer's live pass): every
            // subject above rests on a SLAB or a SOLID CUBE. Nothing in this matrix has ever rested
            // a block on a support that is neither, which is exactly why S-2 stayed 9/9 CLEAN
            // through a live bug in which a sign, a lantern and a log all sat half a block above a
            // birch_fence at -1.0. supportSeatDy classified a seat with isSolidBlock — a VOLUME
            // test — so a fence matched no arm and everything on it fell to loweredFollowerDy's
            // hardcoded -0.5 floor, which was invisible for as long as -0.5 was also the right
            // answer.
            //
            // Geometry: a real-useOn SBSB tower whose top stone is the -1.0 owner, a FENCE placed
            // on it (which therefore also reads -1.0), and the subject placed on the fence. Built
            // at ground level so the subject sits at plot y=6 and add_full_block_above still writes
            // inside the 8x8x8 plot. The subject is a full block, not a decoration, deliberately:
            // break_directly_below removes a decoration by vanilla mechanic and the runner's
            // carve-out would skip the row, so the one mutation that reaches this subject would
            // assert nothing.
            //
            // Moving mutation: break_directly_below — it destroys the FENCE the subject's
            // magnitude comes from. MEASURED (2026-08-06, by the protection-stripping method the
            // 49691609 audit established — probe added, run, and removed):
            //   fully protected            : -1.0 -> -1.0, subject still standing (this row, CLEAN)
            //   anchor+frozen+store stripped: -1.0 ->  0.0   (the mutation provably reaches it)
            //   store cleared, anchor kept : -1.0 -> -0.5   (the anchor alone gives only the floor)
            // So this is one of the STRONG rows: it discriminates the stored NUMBER from both the
            // anchor boolean and the -0.5 fallback, on the exact support shape the matrix had never
            // covered.
            new NamedSubject("full_block_on_minus_one_fence_support", (ctx, w) -> {
                BlockPos owner = minusOneLoweredStoneTowerRigAtGroundLevel(ctx, w, 3, 3);
                place(ctx, Blocks.BIRCH_FENCE.asItem(), owner, Direction.UP, 0.0);
                BlockPos fence = owner.up();
                ctx.assertTrue(w.getBlockState(fence).isOf(Blocks.BIRCH_FENCE),
                        "premise: the fence support must land on the -1.0 tower top, got "
                                + w.getBlockState(fence));
                double fenceDy = dy(w, fence);
                ctx.assertTrue(Math.abs(fenceDy + 1.0) <= EPS,
                        "premise: the FENCE support must itself render -1.0 — a -0.5 fence would "
                                + "make this row coincide with the fallback floor and prove nothing, "
                                + "got " + fenceDy);
                place(ctx, Blocks.STONE.asItem(), fence, Direction.UP, 0.0);
                BlockPos subject = fence.up();
                ctx.assertTrue(w.getBlockState(subject).isOf(Blocks.STONE),
                        "premise: the subject must land on the fence, got " + w.getBlockState(subject));
                double d = dy(w, subject);
                ctx.assertTrue(Math.abs(d + 1.0) <= EPS,
                        "premise: a block resting on a -1.0 fence must be placed at -1.0 (this is "
                                + "the live bug this subject exists for), got " + d);
                return subject;
            }),
            // #11 — THE COVERAGE BOUNDARY MOVED AGAIN (2026-08-06, live run f37a3b2b, actions
            // a38/a39). #10 closed "the support is neither a slab nor a solid cube". This closes
            // the one shape still missing on the other side: the support IS a slab, but a
            // FULL-HEIGHT one. supportSeatDy's full-height arm rejected
            // `state.getBlock() instanceof SlabBlock` outright — a CLASS test standing in for the
            // top-face question the arm asks — so a smooth_stone_slab[type=double] at -1.0, which
            // is an opaque full cube whose top face is at its own cell top, matched no arm and
            // everything placed on it took loweredFollowerDy's -0.5 floor. Live: support
            // 354,-56,-113 double slab dy=-1.0000 anchored, stripped_jungle_log placed above it
            // captured dyPlaceAfter=-0.5000, while the same session's oak_fence, iron_chain and
            // BOTTOM-slab supports were all correct at -1.0.
            //
            // Geometry: a real-useOn SBSB tower at ground level (subject lands at plot y=6, so
            // add_full_block_above still writes inside the 8x8x8 plot), then TWO clicks on the
            // cell above its -1.0 top — the first plants a BOTTOM slab, the second is vanilla's
            // own slab-combine, which is exactly how the live double slab came to exist — then
            // the subject placed on that double slab.
            //
            // Moving mutation: break_directly_below — it destroys the DOUBLE slab the subject's
            // magnitude came from. MEASURED by the protection-stripping method the 49691609 audit
            // established (probe added, run, removed):
            //   fully protected            : -1.0 -> -1.0, subject still standing (this row, CLEAN)
            //   anchor+frozen+store stripped: -0.5 ->  0.0   (the mutation provably reaches it)
            //   store cleared, anchor kept : -1.0 -> -0.5   (the anchor alone gives only the floor)
            // So this is a STRONG row like #3/#5/#6/#10: it discriminates the stored NUMBER from
            // both the anchor boolean and the -0.5 fallback. The other nine mutations were all
            // measured inert here (-1.0 -> -1.0 in every mode), which is why break_directly_below
            // is the one named.
            new NamedSubject("full_block_on_minus_one_double_slab_support", (ctx, w) -> {
                BlockPos owner = minusOneLoweredStoneTowerRigAtGroundLevel(ctx, w, 3, 3);
                place(ctx, Blocks.SMOOTH_STONE_SLAB.asItem(), owner, Direction.UP, 0.0);
                BlockPos support = owner.up();
                place(ctx, Blocks.SMOOTH_STONE_SLAB.asItem(), support, Direction.UP, 0.0);
                var supportState = w.getBlockState(support);
                ctx.assertTrue(supportState.getBlock() instanceof SlabBlock
                                && supportState.get(SlabBlock.TYPE) == SlabType.DOUBLE,
                        "premise: the two clicks must combine into a DOUBLE slab in ONE cell — a "
                                + "BOTTOM slab here would test the half-height arm instead, got "
                                + supportState);
                double supportDy = dy(w, support);
                ctx.assertTrue(Math.abs(supportDy + 1.0) <= EPS,
                        "premise: the DOUBLE slab support must itself render -1.0 — a -0.5 support "
                                + "would make this row coincide with the fallback floor and prove "
                                + "nothing, got " + supportDy);
                place(ctx, Blocks.STONE.asItem(), support, Direction.UP, 0.0);
                BlockPos subject = support.up();
                ctx.assertTrue(w.getBlockState(subject).isOf(Blocks.STONE),
                        "premise: the subject must land on the double slab, got "
                                + w.getBlockState(subject));
                double d = dy(w, subject);
                ctx.assertTrue(Math.abs(d + 1.0) <= EPS,
                        "premise: a block resting on a -1.0 DOUBLE slab must be placed at -1.0 "
                                + "(this is the live bug this subject exists for), got " + d);
                return subject;
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
     * per-subject gametest methods, so a new subject cannot be added without reporting. (Deliberately no
     * literal annotation token in this javadoc — tools/expected-gametest-count.py counts occurrences,
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
    public void carpetOnLaterallyLoweredSlabSupportSurvivesNeighborEdits(TestContext ctx) {
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

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockOnMinusOneFenceSupportSurvivesNeighborEdits(TestContext ctx) {
        runSubject(ctx, SUBJECTS.get(9));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockOnMinusOneDoubleSlabSupportSurvivesNeighborEdits(TestContext ctx) {
        runSubject(ctx, SUBJECTS.get(10));
    }
}
