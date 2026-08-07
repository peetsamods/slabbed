package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Live-reported bug (2026-08-05 live pass, {@code the live ledger} symptom 1): a block
 * PLACED on a support that is already lowered to -1.0 renders at -0.5, leaving a 0.5 floating gap.
 * Reproduced live across four families with identical numbers — birch_slab, birch_fence, lantern,
 * oak_sign.
 *
 * <p><b>Why the pre-existing suite never caught it.</b> Every other fixture builds its scene with
 * {@code setBlockState}, which records no placement anchor, so those scenes take the GEOMETRIC lane
 * — and the geometric lane already resolves this relationship correctly
 * ({@code OffsetRaycastTargetingTest#lanternOnCompoundMinusOneSupportInheritsMinusOne} hard-asserts
 * -1.0 and has always passed). The bug lives ONLY in the ANCHOR lane, so every cell in this class
 * is placed AND anchored via {@link SlabAnchorAttachment#addAnchor} exactly as
 * {@code Block.onPlaced} does for a real player click. A fixture that skips the anchor call
 * FALSE-GREENS here.
 *
 * <p><b>Root cause.</b> The persistent anchor is a boolean membership set, not a stored height, and
 * the anchor lanes resolved a follower's dy from the KIND of support below rather than its ACTUAL
 * dy: the slab branch returned a flat {@code -0.5}, and the non-slab lane started at {@code -0.5}
 * and compounded only via {@code loweredBottomSlabSupportDy}, which reports {@code NaN} for
 * anything that is not a vanilla BOTTOM slab (the live support was a {@code stripped_jungle_log}).
 *
 * <p><b>The scene</b> is the {@code /slabrig} {@code follower_on_minus_one} donor recipe: a source
 * column whose top full block is lowered by the bottom slab beneath it, a seat slab beside it that
 * therefore renders -0.5, and a log standing on that seat that therefore renders -1.0. The follower
 * sits on the log and must inherit -1.0. (A naive "anchored slab on anchored slab" cannot be used:
 * {@code addAnchor} rejects slabs on the direct/column lanes, so such a column silently reads -0.5
 * and builds the wrong scene.)
 *
 * <p><b>SECOND WAVE (2026-08-06, the maintainer's live pass): the same symptom
 * one level up — the SUPPORT is not a cube.</b> Everything above fixed "the support's dy was read
 * as a constant". What remained was "the support was not recognised as a support at all":
 * {@code supportSeatDy} classified a seat with {@code isSolidBlock}, a VOLUME test, when a seat
 * only needs a top-FACE test. A fence, a door, a wall and a pane draw their top face at exactly
 * their cell top and fail the volume test, so every one of them matched no arm and sent its
 * follower to {@code loweredFollowerDy}'s hardcoded {@code -0.5} floor.
 *
 * <p><b>Why this was invisible until a {@code -1.0} support existed:</b> the floor
 * COINCIDENTALLY EQUALS the right answer at {@code -0.5}. Only at {@code -1.0} does it become a
 * visible half-block gap. The rows below are therefore all built on a {@code -1.0} support on
 * purpose — a {@code -0.5} version of any of them would pass while broken.
 */
public final class AnchoredFollowerSupportDyTest {

    private static final double EPS = 1.0e-6;

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchoredSlabOnMinusOneSupportInheritsMinusOne(TestContext ctx) {
        assertFollowerInheritsMinusOne(ctx, 1, 1, "birch_slab",
                Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchoredFenceOnMinusOneSupportInheritsMinusOne(TestContext ctx) {
        assertFollowerInheritsMinusOne(ctx, 4, 1, "birch_fence",
                Blocks.BIRCH_FENCE.getDefaultState());
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchoredLanternOnMinusOneSupportInheritsMinusOne(TestContext ctx) {
        assertFollowerInheritsMinusOne(ctx, 1, 4, "lantern",
                Blocks.LANTERN.getDefaultState());
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchoredSignOnMinusOneSupportInheritsMinusOne(TestContext ctx) {
        assertFollowerInheritsMinusOne(ctx, 4, 4, "oak_sign",
                Blocks.OAK_SIGN.getDefaultState());
    }

    /**
     * The GEOMETRIC twin of {@link #anchoredSlabOnMinusOneSupportInheritsMinusOne}: the same scene
     * with no anchor anywhere. The slab branch's live derivation carried the identical flat -0.5
     * constant, so {@code /slabrig follower_on_minus_one} — which builds with {@code setBlockState}
     * — measured {@code birch_slab = -0.5} here too. Both lanes now go through one resolver, so
     * they cannot drift apart again.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unanchoredSlabOnMinusOneSupportInheritsMinusOne(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(1, 1, 1);
        BlockPos source = base.add(0, 0, 1);
        place(w, source, Blocks.STONE.getDefaultState());
        place(w, source.up(), bottomSlab(Blocks.STONE_SLAB));
        place(w, source.up(2), Blocks.STONE.getDefaultState());
        // Seat column: ground stone, AIR, then the seat slab — donor-correct cantilever shape
        // (the previous stone-under-seat was the interpenetration state outlawed by the
        // flush-seat guard, 2026-08-05).
        place(w, base, Blocks.STONE.getDefaultState());
        BlockPos seat = base.up(2);
        place(w, seat, bottomSlab(Blocks.STONE_SLAB));
        BlockPos subject = seat.up();
        place(w, subject, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());

        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, subject),
                "setup: this twin must exercise the GEOMETRIC lane — no anchor anywhere");
        double subjectDy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(subjectDy + 1.0) <= EPS,
                "fixture: the geometric subject log must render -1.0, got " + subjectDy);

        BlockPos followerPos = subject.up();
        place(w, followerPos, bottomSlab(Blocks.BIRCH_SLAB));
        double dy = SlabSupport.getYOffset(w, followerPos, w.getBlockState(followerPos));
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "unanchored birch_slab on a -1.0 support must inherit -1.0, got " + dy
                        + " (the geometric mirror of the anchored slab branch carried the same "
                        + "flat -0.5 constant)");
        ctx.complete();
    }

    /**
     * CS-CAP guard: a follower standing on a bottom slab that is ITSELF already <b>at the cap</b>
     * resolves a raw seat of {@code cap - 0.5}, which is outside this line's offset set AND outside
     * the offset-aware pick-raycast window. It must clamp at {@link SlabSupport#MIN_RESOLVED_DY},
     * not render somewhere unclickable.
     *
     * <p><b>The fixture is DEEPENED to the cap rather than written against {@code -1.0}</b> (Stage
     * 4, 2026-08-07). The intermediate slab used to be a single course, which happened to reach the
     * cap because the cap was {@code -1.0} and the anchored subject below it already read
     * {@code -1.0}. At the ruled {@code -2.0} cap one course is no longer enough and the row would
     * have measured an unclamped {@code -1.5} while still calling itself a clamp test. It now
     * LADDERS bottom-slab courses until the intermediate support saturates, asserts that it did,
     * and only then asks the follower. At the shipped cap the loop adds zero courses, so the
     * OFF-leg scene is the one that has always been built here.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deeperThanMinusOneClampsAtMinusOne(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos subject = buildAnchoredMinusOneSubject(ctx, 1, 1);

        BlockPos slab = subject.up();
        place(w, slab, bottomSlab(Blocks.BIRCH_SLAB));
        SlabAnchorAttachment.addAnchor(w, slab, w.getBlockState(slab));
        double slabDy = SlabSupport.getYOffset(w, slab, w.getBlockState(slab));
        ctx.assertTrue(Math.abs(slabDy + 1.0) <= EPS,
                "fixture: the intermediate slab must itself be at -1.0, got " + slabDy);

        // LADDER TO THE CAP. Each further bottom-slab course deepens by half a block until the
        // clamp refuses; the count is a consequence of the cap, never written down.
        StringBuilder ladder = new StringBuilder("intermediate ladder: -1.0");
        while (slabDy > SlabSupport.MIN_RESOLVED_DY + EPS) {
            slab = slab.up();
            place(w, slab, bottomSlab(Blocks.BIRCH_SLAB));
            SlabAnchorAttachment.addAnchor(w, slab, w.getBlockState(slab));
            double next = SlabSupport.getYOffset(w, slab, w.getBlockState(slab));
            ladder.append(' ').append(next);
            ctx.assertTrue(next < slabDy - EPS,
                    "fixture: each added course must actually deepen, or the ladder cannot reach "
                            + "the cap and this row would spin — " + ladder);
            slabDy = next;
        }
        ctx.assertTrue(Math.abs(slabDy - SlabSupport.MIN_RESOLVED_DY) <= EPS,
                "fixture: the intermediate support must SATURATE at the cap ("
                        + SlabSupport.MIN_RESOLVED_DY + "), or the follower above it is not handed "
                        + "a past-the-cap raw seat and this row proves nothing — " + ladder);

        double rawSeat = slabDy - 0.5;
        BlockPos top = slab.up();
        place(w, top, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
        SlabAnchorAttachment.addAnchor(w, top, w.getBlockState(top));
        double topDy = SlabSupport.getYOffset(w, top, w.getBlockState(top));
        // NON-VACUITY, asserted before the property: a raw seat that is not past the cap would let
        // the clamp assertion below pass without the clamp ever running.
        ctx.assertTrue(rawSeat < SlabSupport.MIN_RESOLVED_DY - EPS,
                "FIXTURE IS NO LONGER LOAD-BEARING: the raw seat handed to the follower is "
                        + rawSeat + ", which the cap (" + SlabSupport.MIN_RESOLVED_DY + ") does not "
                        + "refuse. Deepen the ladder — do NOT relax the assertion. " + ladder);
        ctx.assertTrue(Math.abs(topDy - SlabSupport.MIN_RESOLVED_DY) <= EPS,
                "CS-CAP: a block on a bottom slab already at the cap resolves a raw " + rawSeat
                        + " seat and must clamp to " + SlabSupport.MIN_RESOLVED_DY + ", got "
                        + topDy + " — " + ladder);
        ctx.complete();
    }

    /**
     * REGRESSION GUARD: the ordinary "anchored block directly on a FLAT bottom slab" case must stay
     * at exactly -0.5. The resolver must only deepen when the support is genuinely deeper.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchoredBlockOnFlatBottomSlabStaysAtMinusHalf(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(6, 1, 6);
        place(w, slab, bottomSlab(Blocks.STONE_SLAB));
        BlockPos block = slab.up();
        place(w, block, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
        SlabAnchorAttachment.addAnchor(w, block, w.getBlockState(block));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, block),
                "setup: a block placed directly on a bottom slab must anchor");
        double dy = SlabSupport.getYOffset(w, block, w.getBlockState(block));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "regression: anchored block on a FLAT bottom slab must stay at -0.5, got " + dy);
        ctx.complete();
    }

    // ── the support is not a cube: fence / door seats (live 2026-08-06) ──────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void signOnMinusOneFenceInheritsMinusOne(TestContext ctx) {
        assertFollowerOnFenceInheritsMinusOne(ctx, 1, 1, "oak_sign", Blocks.OAK_SIGN.getDefaultState());
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lanternOnMinusOneFenceInheritsMinusOne(TestContext ctx) {
        assertFollowerOnFenceInheritsMinusOne(ctx, 1, 1, "lantern", Blocks.LANTERN.getDefaultState());
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockOnMinusOneFenceInheritsMinusOne(TestContext ctx) {
        assertFollowerOnFenceInheritsMinusOne(ctx, 1, 1, "stripped_jungle_log",
                Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
    }

    /**
     * THE TWO LANES MUST AGREE ABOUT THE SAME CELL — the sharpest form of this bug, and the reason
     * it poisoned the placement-dy store rather than merely rendering wrong.
     *
     * <p>MEASURED before the fix: {@code geometricDy=-1.0 anchored=true anchoredDy=-0.5}. The
     * GEOMETRIC lane already read the fence correctly (its column walk reaches the fence's own
     * anchor and asks {@code anchoredCellDy}); the ANCHOR lane, which {@code getYOffsetInner}
     * consults FIRST, went through {@code supportSeatDy}, matched no arm on a fence, and returned
     * the floor. Because {@code SlabAnchorAttachment.addAnchor} evaluates its qualifiers BEFORE the
     * anchor exists and calls {@code recordPlacementDy} AFTER, a real click read {@code -1.0} to
     * decide the block qualified and then STORED {@code -0.5} — LAW 1 freezing the wrong number.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceSeatAnchorLaneAgreesWithGeometricLane(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos fence = buildAnchoredMinusOneFence(ctx, 1, 1);
        BlockPos followerPos = fence.up();
        place(w, followerPos, Blocks.OAK_SIGN.getDefaultState());
        double geometric = SlabSupport.getYOffset(w, followerPos, w.getBlockState(followerPos));
        SlabAnchorAttachment.addAnchor(w, followerPos, w.getBlockState(followerPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, followerPos),
                "setup: the sign must take the ANCHOR lane, or this test compares one lane to itself");
        double anchored = SlabSupport.getYOffset(w, followerPos, w.getBlockState(followerPos));
        ctx.assertTrue(Math.abs(geometric - anchored) <= EPS,
                "the geometric and anchor lanes must give the SAME height for a follower on a -1.0 "
                        + "fence: geometric=" + geometric + " anchored=" + anchored);
        ctx.assertTrue(Math.abs(anchored + 1.0) <= EPS,
                "and that height must be -1.0, got " + anchored);
        ctx.complete();
    }

    /**
     * The cut-in-half door. A door's UPPER half is written by {@code DoorBlock.onPlaced} with
     * {@code setBlockState}, so it never sees {@code onPlaced}, holds no anchor and no stored
     * height, and must resolve purely from the cell below it — which is the door's own LOWER half,
     * a support that is not a slab and not a solid cube.
     *
     * <p>MEASURED before the fix: {@code lowerDy=-1.0 upperDy=0.0} — the two halves a full block
     * apart. (the maintainer's live scene put the upper half on the {@code -0.5} floor rather than at
     * {@code 0.0} because its {@code shouldOffset} double-block branch found a bottom slab two
     * cells down; same defect, different fallback.)
     *
     * <p><b>What this closes and what it does NOT.</b> It closes the RESOLUTION: the upper half now
     * reads its support's real top face and lands on the lower half. It does not close the
     * multi-cell FREEZE-REGISTRATION gap — {@code freezeLoweredOnPlace}'s
     * {@code heightIsSharedWithACellThisHookNeverSees} still declines every
     * {@code DOUBLE_BLOCK_HALF} piece, so neither half records a placed height of its own and the
     * upper half remains live-derived. That gap stays open and is deliberately not touched here.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void upperDoorHalfMatchesItsMinusOneLowerHalf(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos subject = buildAnchoredMinusOneSubject(ctx, 1, 1);
        BlockPos lower = subject.up();
        place(w, lower, Blocks.OAK_DOOR.getDefaultState()
                .with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        place(w, lower.up(), Blocks.OAK_DOOR.getDefaultState()
                .with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        SlabAnchorAttachment.addAnchor(w, lower, w.getBlockState(lower));
        double lowerDy = SlabSupport.getYOffset(w, lower, w.getBlockState(lower));
        ctx.assertTrue(Math.abs(lowerDy + 1.0) <= EPS,
                "fixture: the door's LOWER half on a -1.0 log must read -1.0, got " + lowerDy);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, lower.up()),
                "fixture: the UPPER half must be un-anchored — onPlaced never fires for it, and an "
                        + "anchored upper half would test a lane real doors never take");
        double upperDy = SlabSupport.getYOffset(w, lower.up(), w.getBlockState(lower.up()));
        ctx.assertTrue(Math.abs(upperDy - lowerDy) <= EPS,
                "a door's two halves must render at the SAME height: lower=" + lowerDy
                        + " upper=" + upperDy + " (the upper half resolves from the lower half, "
                        + "which is neither a slab nor a solid cube)");
        ctx.complete();
    }

    /**
     * THE TRIAD MOVED TOGETHER, and the raw-shape probe did not switch the outline offset off.
     *
     * <p>{@code SlabSupport.presentsCellTopAsTopFace} reads a block's un-offset outline through
     * {@code IN_RAW_SHAPE_PROBE}, a flag {@code SlabSupportStateMixin} and {@code CarpetDyShapeMixin}
     * honour by returning the vanilla shape. If that flag ever leaked — set and not cleared, or
     * honoured too broadly — outlines would silently stop being offset everywhere and the model
     * would drift from the wireframe with no test noticing. This asserts the ORDINARY outline is
     * still offset by the resolved dy, on the very geometry the probe is used for.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceSeatFollowerOutlineFollowsItsDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos fence = buildAnchoredMinusOneFence(ctx, 1, 1);
        BlockPos followerPos = fence.up();
        place(w, followerPos, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
        SlabAnchorAttachment.addAnchor(w, followerPos, w.getBlockState(followerPos));
        double dy = SlabSupport.getYOffset(w, followerPos, w.getBlockState(followerPos));
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS, "fixture: the follower must be at -1.0, got " + dy);
        double outlineMinY = w.getBlockState(followerPos)
                .getOutlineShape(w, followerPos)
                .getMin(net.minecraft.util.math.Direction.Axis.Y);
        ctx.assertTrue(Math.abs(outlineMinY - dy) <= EPS,
                "the OUTLINE must be offset by the same dy the model is (" + dy + "), got minY="
                        + outlineMinY + " — a leaked raw-shape probe flag would read 0.0 here");
        ctx.complete();
    }

    /**
     * THE ACCEPT-ONLY PROPERTY, asserted rather than assumed. {@code presentsCellTopAsTopFace} must
     * admit a support ONLY when its top face really is at its own cell top. A carpet's is at
     * {@code 1/16}, so a carpet must NOT become a full-height seat — a follower on one keeps the
     * {@code -0.5} floor it has always had instead of inheriting the carpet's depth outright.
     *
     * <p>This is the guard against "fix the fence, break everything thin": the resolver's alphabet
     * is {@code {-1.0, -0.5, 0.0}} and a {@code 1/16} seat has no representation in it.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void carpetSupportIsNotPromotedToAFullHeightSeat(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos subject = buildAnchoredMinusOneSubject(ctx, 1, 1);
        BlockPos carpet = subject.up();
        place(w, carpet, Blocks.WHITE_CARPET.getDefaultState());
        SlabAnchorAttachment.addAnchor(w, carpet, w.getBlockState(carpet));
        BlockPos follower = carpet.up();
        place(w, follower, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
        SlabAnchorAttachment.addAnchor(w, follower, w.getBlockState(follower));
        double dy = SlabSupport.getYOffset(w, follower, w.getBlockState(follower));
        ctx.assertTrue(dy >= -0.5 - EPS,
                "a carpet is a 1/16 seat, not a full-height one: a follower above it must not be "
                        + "given the carpet's own depth, got " + dy);
        ctx.complete();
    }

    // ── the support is a TOP/DOUBLE SLAB (live 2026-08-06, run f37a3b2b, actions a38/a39) ──────

    /**
     * THIRD WAVE, and the ninth exclude-by-classname of this campaign. The seat resolver's
     * full-height arm rejected {@code state.getBlock() instanceof SlabBlock} outright — a CLASS
     * test standing in for the top-face question the arm exists to ask. A
     * {@code smooth_stone_slab[type=double]} is an opaque full cube whose top face is at its own
     * cell top; for seating purposes it is indistinguishable from full stone. It was rejected
     * anyway, so a block placed on one matched no arm of {@code supportSeatDy} and took
     * {@code loweredFollowerDy}'s {@code -0.5} floor.
     *
     * <p>LIVE EVIDENCE (a recorded live run): support
     * {@code 354,-56,-113 = smooth_stone_slab[type=double]}, {@code dy=-1.0000 anchored=true};
     * {@code stripped_jungle_log} placed at {@code 354,-55,-113} read {@code dyPlaceAfter=-0.5000}.
     * The same session's {@code oak_fence} (a27/a30), {@code iron_chain} (a34) and BOTTOM-slab
     * (a26) supports were all correct at {@code -1.0} — only the DOUBLE slab was wrong.
     *
     * <p><b>Built through REAL CLICKS, and that is the point of this row.</b> The value the player
     * keeps is written by {@code SlabAnchorAttachment.recordPlacementDy}, which reads
     * {@code getYOffset} at the instant the block appears — i.e. straight through the hole this
     * test names. A {@code setBlockState} fixture would exercise the resolver but not the capture,
     * and the capture is what LAW 1 freezes. MEASURED before the fix, exactly here:
     * {@code tower top = -1.0}, {@code click1 = smooth_stone_slab[type=bottom] dy=-1.0 stored},
     * {@code click2 = smooth_stone_slab[type=double] dy=-1.0 stored}, {@code follower dy=-0.5}.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockOnMinusOneDoubleSlabSupportInheritsMinusOne(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos towerTop = buildRealUseOnMinusOneTower(ctx, 3, 3);

        // Two clicks on the same cell: the first plants a BOTTOM slab on the -1.0 tower top, the
        // second is vanilla's own slab-combine, which turns that cell into a DOUBLE slab in place.
        // This is precisely how the live smooth_stone_slab[type=double] support came to exist.
        useOn(ctx, Blocks.SMOOTH_STONE_SLAB.asItem(), towerTop, Direction.UP, 0.0);
        BlockPos support = towerTop.up();
        useOn(ctx, Blocks.SMOOTH_STONE_SLAB.asItem(), support, Direction.UP, 0.0);
        BlockState supportState = w.getBlockState(support);
        ctx.assertTrue(supportState.getBlock() instanceof SlabBlock
                        && supportState.get(SlabBlock.TYPE) == SlabType.DOUBLE,
                "fixture: the two clicks must leave a DOUBLE slab in one cell, got " + supportState);
        double supportDy = SlabSupport.getYOffset(w, support, supportState);
        ctx.assertTrue(Math.abs(supportDy + 1.0) <= EPS,
                "fixture: the DOUBLE slab support must itself render -1.0 — at -0.5 this row would "
                        + "coincide with the fallback floor and prove nothing, got " + supportDy);

        useOn(ctx, Blocks.STRIPPED_JUNGLE_LOG.asItem(), support, Direction.UP, 0.0);
        BlockPos follower = support.up();
        ctx.assertTrue(w.getBlockState(follower).isOf(Blocks.STRIPPED_JUNGLE_LOG),
                "fixture: the follower must land on the double slab, got " + w.getBlockState(follower));
        double dy = SlabSupport.getYOffset(w, follower, w.getBlockState(follower));
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "a full block placed on a -1.0 DOUBLE slab must resolve -1.0, got " + dy
                        + " (the seat resolver's full-height arm rejected every slab by CLASS, so a "
                        + "double slab — an opaque full cube — matched no arm and the follower took "
                        + "the -0.5 floor)");
        ctx.complete();
    }

    /**
     * The TOP-slab half of the same hole, and the one that shows the reject really was about CLASS
     * rather than volume: a TOP slab is NOT a solid block ({@code isSolidBlock=false}, measured),
     * yet it draws its top face at exactly its cell top ({@code cullingShape maxY = 1.0},
     * {@code outline maxY = 1.0}, both measured). It is the fence case one more time, on a slab.
     *
     * <p>Built with this class's {@code setBlockState} + {@code addAnchor} idiom rather than real
     * clicks, deliberately: the two-click combine that mints a DOUBLE slab has no TOP-slab twin
     * that is stable to write here, and this row's job is the RESOLVER, which the anchored fixture
     * reaches identically. MEASURED before the fix in this exact scene: support {@code dy=-1.0},
     * follower {@code dy=-0.5}.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockOnMinusOneTopSlabSupportInheritsMinusOne(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos subjectSeat = buildAnchoredMinusOneSeat(ctx, 1, 1);

        BlockPos support = subjectSeat;
        place(w, support, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
        SlabAnchorAttachment.addAnchor(w, support, w.getBlockState(support));
        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        ctx.assertTrue(Math.abs(supportDy + 1.0) <= EPS,
                "fixture: the TOP slab support must itself render -1.0, got " + supportDy);

        BlockPos follower = support.up();
        place(w, follower, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
        SlabAnchorAttachment.addAnchor(w, follower, w.getBlockState(follower));
        double dy = SlabSupport.getYOffset(w, follower, w.getBlockState(follower));
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "a full block on a -1.0 TOP slab must resolve -1.0, got " + dy
                        + " (a TOP slab fails isSolidBlock but its top face IS at its cell top)");
        ctx.complete();
    }

    // ── the LIVE lane: the same cell with NO stored height (run e9eb0932, a8/a10/a15) ───────────

    /**
     * FOURTH WAVE — the visible SNAP-DOWN, and the same defect one lane over.
     *
     * <p>{@link #fullBlockOnMinusOneDoubleSlabSupportInheritsMinusOne} fixed the STORED answer, and
     * the maintainer's live pass confirms it: the server writes {@code -1.0}. But the block is briefly drawn
     * at {@code -0.5} and then drops. Recorder run {@code e9eb0932}, actions a8/a10/a15 —
     * {@code stripped_jungle_log} on {@code smooth_stone_slab[type=double]} at {@code dy=-1.0000}:
     *
     * <pre>
     *   CLIENT  dyPlaceBefore = -0.5000   dyPlaceAfter = -0.5000   center anchored=false
     *   SERVER  dyPlaceBefore = -0.5000   dyPlaceAfter = -1.0000   center anchored=true
     * </pre>
     *
     * <p><b>The client is not missing a fact — it is asking the wrong question.</b> The client's own
     * neighbourhood snapshot in that same frame reads {@code down=smooth_stone_slab[type=double]
     * dy=-1.0000 anchored=true}: the support's depth and its anchor were both already on the client.
     * And the control in the same session, a13, settles it — a log on a {@code -1.0} BOTTOM slab
     * read {@code -1.0000} on the CLIENT with {@code anchored=false} and no stored height at all.
     * Same side, same missing store, right answer. So the split is not sync latency; the two sides
     * disagree because the server has a stored number and the live resolver, which is all the client
     * has until that number arrives, answers {@code -0.5} for a DOUBLE seat and {@code -1.0} for a
     * BOTTOM one.
     *
     * <p><b>Root cause, and it is the same shape a THIRD time:</b> {@code slabColumnYOffset}, on
     * finding a lowered slab in the column, answered {@code isBottomSlab(cur) ? -1.0 : -0.5} — a
     * CLASS test plus two flat constants standing in for the seat's actual top face. The constants
     * are correct for a slab at {@code -0.5} and silently wrong for one at {@code -1.0}. Note the
     * server's {@code dyPlaceBefore} is {@code -0.5} too: this lane is side-independent, so it is
     * headlessly reachable and this row runs on the server exactly as the client hits it.
     *
     * <p>This row deliberately puts the subject in with {@code setBlockState}, which records no
     * anchor and no placed height — the client's prediction state, and also LAW.md lane D's
     * authored/pre-store cell. A real-click twin already exists above and passes; if this one is
     * ever changed to a real click it stops testing anything.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unstoredFullBlockOnMinusOneDoubleSlabResolvesMinusOne(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos towerTop = buildRealUseOnMinusOneTower(ctx, 3, 3);
        useOn(ctx, Blocks.SMOOTH_STONE_SLAB.asItem(), towerTop, Direction.UP, 0.0);
        BlockPos support = towerTop.up();
        useOn(ctx, Blocks.SMOOTH_STONE_SLAB.asItem(), support, Direction.UP, 0.0);
        BlockState supportState = w.getBlockState(support);
        ctx.assertTrue(supportState.getBlock() instanceof SlabBlock
                        && supportState.get(SlabBlock.TYPE) == SlabType.DOUBLE,
                "fixture: the two clicks must leave a DOUBLE slab in one cell, got " + supportState);
        double supportDy = SlabSupport.getYOffset(w, support, supportState);
        ctx.assertTrue(Math.abs(supportDy + 1.0) <= EPS,
                "fixture: the DOUBLE slab support must itself render -1.0 — at -0.5 this row would "
                        + "coincide with the constant it is testing and prove nothing, got " + supportDy);

        BlockPos subject = support.up();
        place(w, subject, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
        assertUnstoredAndUnanchored(ctx, subject, "the DOUBLE-slab subject");

        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "a full block with NO stored height, standing on a -1.0 DOUBLE slab, must resolve "
                        + "-1.0, got " + dy + " (slabColumnYOffset answered a flat -0.5 for every "
                        + "non-BOTTOM slab in the column, whatever depth that slab was actually at "
                        + "— this is the number the client draws before the stored one arrives, and "
                        + "the gap between the two IS the snap-down)");
        ctx.complete();
    }

    /**
     * THE CONTROL THAT MADE THE DIAGNOSIS POSSIBLE, pinned so it cannot rot: recorder a13, the same
     * log with no stored height on a {@code -1.0} BOTTOM slab, measured {@code -1.0} on the CLIENT.
     * That is the arm of the same ternary that happened to hold the right constant, and it is what
     * ruled out "the client cannot know yet". It must stay byte-identical across this fix.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unstoredFullBlockOnMinusOneBottomSlabStaysAtMinusOne(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos towerTop = buildRealUseOnMinusOneTower(ctx, 3, 3);
        useOn(ctx, Blocks.SMOOTH_STONE_SLAB.asItem(), towerTop, Direction.UP, 0.0);
        BlockPos support = towerTop.up();
        BlockState supportState = w.getBlockState(support);
        ctx.assertTrue(supportState.getBlock() instanceof SlabBlock
                        && supportState.get(SlabBlock.TYPE) == SlabType.BOTTOM,
                "fixture: one click must leave a BOTTOM slab, got " + supportState);
        double supportDy = SlabSupport.getYOffset(w, support, supportState);
        ctx.assertTrue(Math.abs(supportDy + 1.0) <= EPS,
                "fixture: the BOTTOM slab support must itself render -1.0, got " + supportDy);

        BlockPos subject = support.up();
        place(w, subject, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
        assertUnstoredAndUnanchored(ctx, subject, "the BOTTOM-slab control subject");

        // THE HALF-HEIGHT SEAT, CLAMPED — stated as the law rather than as the number it happens
        // to produce (Stage 4, 2026-08-07). A BOTTOM slab's top face is half a block below its own
        // grid line, so a block resting on one seats at `supportDy - 0.5`; the cap is the only
        // thing that ever stops it. At the shipped cap that arithmetic is -1.5 refused down to
        // -1.0, which is the number this control has always asserted and still asserts. At the
        // ruled -2.0 cap the same law lets the -1.5 stand, which is the WYSIWYG-correct height:
        // the support's top face is at Y-0.5 and the block above it now genuinely rests there.
        double expected = Math.max(supportDy - 0.5, SlabSupport.MIN_RESOLVED_DY);
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy - expected) <= EPS,
                "control (recorder a13): an unstored full block on a " + supportDy + " BOTTOM slab "
                        + "seats half a block below it and is refused no deeper than "
                        + SlabSupport.MIN_RESOLVED_DY + ", so it must read " + expected + ", got "
                        + dy);
        ctx.complete();
    }

    /**
     * THE OVER-DEEPENING GUARD, and the reason this fix may only ever go DOWN. A DOUBLE slab that is
     * genuinely at {@code -0.5} must keep giving its subject {@code -0.5} — the resolver must deepen
     * only when the seat is really deeper, never because it now consults the seat at all.
     *
     * <p>MEASURED shape: a DOUBLE slab standing on a FLUSH bottom slab resolves {@code -0.5} for
     * itself, so the subject above it must read {@code -0.5} both before and after.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unstoredFullBlockOnMinusHalfDoubleSlabStaysAtMinusHalf(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(8, 1, 8);
        place(w, base, Blocks.STONE.getDefaultState());
        place(w, base.up(), bottomSlab(Blocks.STONE_SLAB));
        BlockPos support = base.up(2);
        place(w, support, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE));
        SlabAnchorAttachment.addAnchor(w, support, w.getBlockState(support));
        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        ctx.assertTrue(Math.abs(supportDy + 0.5) <= EPS,
                "fixture: this DOUBLE slab must sit at -0.5, or the row cannot tell 'deepened "
                        + "correctly' from 'over-deepened', got " + supportDy);

        BlockPos subject = support.up();
        place(w, subject, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
        assertUnstoredAndUnanchored(ctx, subject, "the -0.5 DOUBLE-slab subject");

        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "an unstored full block on a -0.5 DOUBLE slab must stay at -0.5, got " + dy
                        + " (the seat lane must deepen only when the seat is genuinely deeper)");
        ctx.complete();
    }

    /**
     * A subject built with {@code setBlockState} holds neither of the two facts LAW 1 calls
     * authoritative, which is exactly what makes it exercise the LIVE lane. Asserted rather than
     * assumed: if either fact were present the row would be measuring the store instead.
     */
    private static void assertUnstoredAndUnanchored(TestContext ctx, BlockPos pos, String what) {
        ServerWorld w = ctx.getWorld();
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, pos),
                "setup: " + what + " must hold no anchor, or this row tests the anchor lane");
        ctx.assertTrue(Double.isNaN(SlabPlacementDyAttachment.storedDy(w, pos)),
                "setup: " + what + " must hold no stored height, or this row tests the store and "
                        + "cannot fail for the reason it exists");
    }

    /**
     * Real-useOn SBSB tower (ground stone, then slab/stone alternating x4) whose top stone reads
     * -1.0 — {@code NeighborUpdateInvarianceTest}'s own rig recipe, built through real clicks.
     */
    private BlockPos buildRealUseOnMinusOneTower(TestContext ctx, int x, int z) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(new BlockPos(x, 0, z));
        place(w, ground, Blocks.STONE.getDefaultState());
        Item[] tower = {
                Blocks.STONE_SLAB.asItem(), Blocks.STONE.asItem(),
                Blocks.STONE_SLAB.asItem(), Blocks.STONE.asItem()
        };
        BlockPos cursor = ground;
        for (Item item : tower) {
            useOn(ctx, item, cursor, Direction.UP, 0.0);
            cursor = cursor.up();
        }
        double topDy = SlabSupport.getYOffset(w, cursor, w.getBlockState(cursor));
        ctx.assertTrue(Math.abs(topDy + 1.0) <= EPS,
                "fixture: real-useOn SBSB tower top stone must read -1.0, got " + topDy);
        return cursor;
    }

    /** This line's real headless placement harness, shared with {@code NeighborUpdateInvarianceTest}. */
    private static void useOn(TestContext ctx, Item item, BlockPos clicked, Direction face, double yNudge) {
        ServerWorld world = ctx.getWorld();
        PlayerEntity player = UseOnCombineVsExtendPlacementTest.mockPlayerHolding(
                ctx, clicked.up(3), new ItemStack(item));
        Vec3d hit = Vec3d.ofCenter(clicked)
                .add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5 + yNudge, face.getOffsetZ() * 0.5);
        UseOnCombineVsExtendPlacementTest.useHeldItem(world, player, clicked, face, hit);
    }

    /**
     * The {@code follower_on_minus_one} donor scene stopped one cell short of
     * {@link #buildAnchoredMinusOneSubject}: returns the EMPTY cell above the -0.5 seat slab, so a
     * caller can put its own support (rather than the usual log) there.
     */
    private BlockPos buildAnchoredMinusOneSeat(TestContext ctx, int x, int z) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(x, 1, z);
        BlockPos source = base.add(0, 0, 1);
        place(w, source, Blocks.STONE.getDefaultState());
        place(w, source.up(), bottomSlab(Blocks.STONE_SLAB));
        BlockPos sourceTop = source.up(2);
        place(w, sourceTop, Blocks.STONE.getDefaultState());
        SlabAnchorAttachment.addAnchor(w, sourceTop, w.getBlockState(sourceTop));

        place(w, base, Blocks.STONE.getDefaultState());
        BlockPos seat = base.up(2);
        place(w, seat, bottomSlab(Blocks.STONE_SLAB));
        SlabAnchorAttachment.addAnchor(w, seat, w.getBlockState(seat));
        double seatDy = SlabSupport.getYOffset(w, seat, w.getBlockState(seat));
        ctx.assertTrue(Math.abs(seatDy + 0.5) <= EPS,
                "fixture: the seat slab beside the lowered source must render -0.5, got " + seatDy);
        return seat.up();
    }

    /**
     * Builds {@link #buildAnchoredMinusOneSubject}'s -1.0 log and stands an anchored birch fence on
     * it — the exact scene {@link #anchoredFenceOnMinusOneSupportInheritsMinusOne} already pins at
     * -1.0. Returns the fence cell, which is the SUPPORT under test.
     */
    private BlockPos buildAnchoredMinusOneFence(TestContext ctx, int x, int z) {
        ServerWorld w = ctx.getWorld();
        BlockPos subject = buildAnchoredMinusOneSubject(ctx, x, z);
        BlockPos fence = subject.up();
        place(w, fence, Blocks.BIRCH_FENCE.getDefaultState());
        SlabAnchorAttachment.addAnchor(w, fence, w.getBlockState(fence));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, fence),
                "fixture: the fence support must anchor (connecting structural on a lowered column)");
        double fenceDy = SlabSupport.getYOffset(w, fence, w.getBlockState(fence));
        ctx.assertTrue(Math.abs(fenceDy + 1.0) <= EPS,
                "fixture: the fence support must itself render -1.0, got " + fenceDy);
        return fence;
    }

    private void assertFollowerOnFenceInheritsMinusOne(TestContext ctx, int x, int z, String family,
                                                        BlockState follower) {
        ServerWorld w = ctx.getWorld();
        BlockPos fence = buildAnchoredMinusOneFence(ctx, x, z);
        BlockPos followerPos = fence.up();
        place(w, followerPos, follower);
        SlabAnchorAttachment.addAnchor(w, followerPos, w.getBlockState(followerPos));

        double dy = SlabSupport.getYOffset(w, followerPos, w.getBlockState(followerPos));
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                family + " resting on a -1.0 FENCE must inherit -1.0, got " + dy
                        + " (the seat resolver classifies a support by isSolidBlock, so a fence "
                        + "matches no arm and the follower falls to the hardcoded -0.5 floor)");
        ctx.complete();
    }

    // ------------------------------------------------------------------------

    private void assertFollowerInheritsMinusOne(TestContext ctx, int x, int z, String family,
                                                BlockState follower) {
        ServerWorld w = ctx.getWorld();
        BlockPos subject = buildAnchoredMinusOneSubject(ctx, x, z);

        BlockPos followerPos = subject.up();
        place(w, followerPos, follower);
        SlabAnchorAttachment.addAnchor(w, followerPos, w.getBlockState(followerPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, followerPos),
                "setup(" + family + "): the follower must take the ANCHOR lane — an un-anchored "
                        + "scene takes the geometric lane and FALSE-GREENS this test");

        double dy = SlabSupport.getYOffset(w, followerPos, w.getBlockState(followerPos));
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "anchored " + family + " on a -1.0 support must inherit -1.0, got " + dy
                        + " (the live 0.5 floating gap: the anchor lane resolves the KIND of "
                        + "support below, not its ACTUAL dy)");
        ctx.complete();
    }

    /**
     * Builds the {@code follower_on_minus_one} donor scene at plot-relative {@code (x, z)},
     * occupying {@code z} and {@code z + 1}, and returns the anchored subject that renders -1.0.
     */
    private BlockPos buildAnchoredMinusOneSubject(TestContext ctx, int x, int z) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(x, 1, z);
        BlockPos source = base.add(0, 0, 1);

        // Source column: stone / bottom slab / stone — the top stone is lowered -0.5 by the slab.
        place(w, source, Blocks.STONE.getDefaultState());
        place(w, source.up(), bottomSlab(Blocks.STONE_SLAB));
        BlockPos sourceTop = source.up(2);
        place(w, sourceTop, Blocks.STONE.getDefaultState());
        SlabAnchorAttachment.addAnchor(w, sourceTop, w.getBlockState(sourceTop));
        double sourceTopDy = SlabSupport.getYOffset(w, sourceTop, w.getBlockState(sourceTop));
        ctx.assertTrue(Math.abs(sourceTopDy + 0.5) <= EPS,
                "fixture: source top block must render -0.5, got " + sourceTopDy);

        // Seat column: ground stone, AIR, then the bottom slab beside the lowered source — the
        // seat is a legitimate cantilever (destination volume free) and reads -0.5. The previous
        // stone-under-seat shape was the interpenetration state outlawed by the flush-seat guard
        // (2026-08-05); air-under-seat is the donor-correct recipe.
        place(w, base, Blocks.STONE.getDefaultState());
        BlockPos seat = base.up(2);
        place(w, seat, bottomSlab(Blocks.STONE_SLAB));
        SlabAnchorAttachment.addAnchor(w, seat, w.getBlockState(seat));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, seat),
                "fixture: the seat slab must anchor via the lowered-side-slab lane");
        double seatDy = SlabSupport.getYOffset(w, seat, w.getBlockState(seat));
        ctx.assertTrue(Math.abs(seatDy + 0.5) <= EPS,
                "fixture: seat slab beside the lowered source must render -0.5, got " + seatDy);

        // Subject: a log standing on the -0.5 seat slab — renders -1.0 (this part already works).
        BlockPos subject = seat.up();
        place(w, subject, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
        SlabAnchorAttachment.addAnchor(w, subject, w.getBlockState(subject));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, subject),
                "fixture: the subject log must anchor on the bottom slab below it");
        double subjectDy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(subjectDy + 1.0) <= EPS,
                "fixture: the subject log must render -1.0 (the support the follower stands on), got "
                        + subjectDy);
        return subject;
    }

    private static void place(ServerWorld w, BlockPos pos, BlockState state) {
        w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }

    private static BlockState bottomSlab(Block slab) {
        return slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }
}
