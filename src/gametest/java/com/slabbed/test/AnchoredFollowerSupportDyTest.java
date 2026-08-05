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
 * Live-reported bug (2026-08-05 live pass, {@code docs/process/LIVE_LEDGER.md} symptom 1): a block
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
     * CS-CAP guard ({@code DY_SPEC.md:115}): a follower standing on a bottom slab that is ITSELF
     * already at -1.0 resolves a raw seat of -1.5, which is outside this line's offset set
     * {@code {-1.0, -0.5, 0.0}} AND outside the +/-1 offset-aware pick-raycast window. It must
     * clamp at -1.0, not render somewhere unclickable.
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

        BlockPos top = slab.up();
        place(w, top, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
        SlabAnchorAttachment.addAnchor(w, top, w.getBlockState(top));
        double topDy = SlabSupport.getYOffset(w, top, w.getBlockState(top));
        ctx.assertTrue(Math.abs(topDy + 1.0) <= EPS,
                "CS-CAP: a block on a bottom slab already at -1.0 resolves a raw -1.5 seat and must "
                        + "clamp to -1.0, got " + topDy);
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
