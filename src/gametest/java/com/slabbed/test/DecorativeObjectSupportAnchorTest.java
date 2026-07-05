package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * L10, ported from the 1.21.11 sibling branch's {@code DecorativeObjectSupportAnchorTest}
 * (live-reported "pop upon breaking at the end", 2026-07-04 recorder): a decorative, non-solid,
 * non-connecting object (candle / trapdoor / floor button / rail / pressure plate / sign) resting
 * on a lowered support popped from -0.5 to 0.0 the instant that support was broken.
 *
 * <p>BRANCH-SPECIFIC INVESTIGATION (verified empirically via a throwaway probe gametest before this
 * change — measured booleans below): unlike the sibling, this 1.21.1 branch does NOT have a gap for
 * ordinary floor-resting decorative objects. Its {@code freezeLoweredOnPlace} (called from
 * {@code BlockOnPlacedAnchorMixin.onPlaced} for EVERY placement) already writes an UNCHECKED anchor
 * for ANY block whose live {@code getYOffset < 0} at placement — candle, bottom trapdoor, floor
 * button, rail, pressure plate and standing sign all measured {@code isAnchored=true} after
 * placement on a lowered support, and all stayed at -0.5 after the support was broken. So the
 * sibling's {@code qualifiesForDecorativeObjectAnchor} lane would be redundant here.
 *
 * <p>What this branch's unchecked-freeze architecture created INSTEAD is a latent inverse bug the
 * sibling does not have: {@code freezeLoweredOnPlace} also froze MUST-FOLLOW ceiling decorations
 * (spore blossom / hanging roots / pale hanging moss — non-{@code HANGING}, so not early-dispatched
 * by {@code getYOffsetInner}). Once anchored, {@code getYOffsetInner}'s anchor branch (which runs
 * BEFORE the underside-owner follow branches) pins them to their placement dy, so they stop
 * tracking their support when its own dy later changes. Probe measured a spore blossom under a
 * lowered stone as {@code isAnchoredAfterFreeze=true}.
 *
 * <p>Fix: {@code SlabSupport.isMustFollowCeilingDecoration} — exclude exactly that set from
 * {@code freezeLoweredOnPlace}'s unchecked lowered-anchor path, so must-follow decorations keep
 * dynamically tracking their support while ordinary floor-resting decorations keep their never-pop
 * anchor. The set is NOT keyed on {@code isCeilingAttached} (which blanket-matches every button /
 * top trapdoor), so floor buttons / bottom trapdoors keep their correct anchor.
 */
public final class DecorativeObjectSupportAnchorTest {

    private static final double EPS = 1.0e-6;

    /** Real placement runs BlockOnPlacedAnchorMixin.onPlaced -> addAnchor + freezeLoweredOnPlace. */
    private static void placeAsIfOnPlaced(ServerWorld w, BlockPos pos) {
        SlabAnchorAttachment.addAnchor(w, pos, w.getBlockState(pos));
        SlabAnchorAttachment.freezeLoweredOnPlace(w, pos, w.getBlockState(pos));
    }

    // Builds a lowered/anchored SOLID full-block support and returns the support pos. dirt on a
    // vanilla bottom slab -> anchored (-0.5).
    private static BlockPos buildLoweredFullBlockSupport(ServerWorld w, BlockPos base) {
        BlockPos slabPos = base;
        BlockPos supportPos = slabPos.up();
        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(supportPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, supportPos, w.getBlockState(supportPos));
        return supportPos;
    }

    // Builds a lowered SOLID support that has AIR beneath it (so a decoration can hang from its
    // underside), whose -0.5 dy is a DETERMINISTIC anchor lookup — not a cantilever side-walk.
    // A support is placed on a bottom slab and anchored, then the slab is pulled: the never-pop
    // anchor holds the support at -0.5 with open air below. This is isolation-immune (no horizontal
    // adjacency walk that could read across the 8x8x8 gametest structure bound into a neighbouring
    // test — the flake that intermittently zeroed the earlier `dirt.east()` cantilever's setup dy
    // when batch composition shifted). Returns the support pos.
    private static BlockPos buildAnchoredLoweredSupportWithAirBelow(ServerWorld w, BlockPos slabPos) {
        BlockPos supportPos = slabPos.up();
        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(supportPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, supportPos, w.getBlockState(supportPos));
        placeAsIfOnPlaced(w, supportPos);
        // Pull the slab: the never-pop anchor holds the support lowered, leaving air below for the deco.
        w.setBlockState(slabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        return supportPos;
    }

    // ── never-pop for ordinary floor-resting decorative objects ──────────────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void candleOnBottomSlabDoesNotPopWhenSlabBreaks(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos candlePos = slabPos.up();
        w.setBlockState(slabPos, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(candlePos, Blocks.CANDLE.getDefaultState(), Block.NOTIFY_LISTENERS);
        placeAsIfOnPlaced(w, candlePos);

        double before = SlabSupport.getYOffset(w, candlePos, w.getBlockState(candlePos));
        ctx.assertTrue(Math.abs(before + 0.5) <= EPS, "setup: candle on a bottom slab should render -0.5, got " + before);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, candlePos),
                "a candle resting on a bottom slab must anchor at placement, or breaking the slab pops it back to flush");

        w.setBlockState(slabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        double after = SlabSupport.getYOffset(w, candlePos, w.getBlockState(candlePos));
        ctx.assertTrue(Math.abs(after + 0.5) <= EPS,
                "never-pop violation: candle popped from -0.5 to " + after + " after its support was broken");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void bottomTrapdoorOnLoweredSupportDoesNotPopWhenSupportBreaks(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = buildLoweredFullBlockSupport(w, ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 7));
        BlockPos trapPos = support.up();
        w.setBlockState(trapPos, Blocks.BIRCH_TRAPDOOR.getDefaultState().with(Properties.BLOCK_HALF, BlockHalf.BOTTOM),
                Block.NOTIFY_LISTENERS);
        placeAsIfOnPlaced(w, trapPos);

        double before = SlabSupport.getYOffset(w, trapPos, w.getBlockState(trapPos));
        ctx.assertTrue(Math.abs(before + 0.5) <= EPS, "setup: bottom trapdoor on a lowered support should render -0.5, got " + before);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, trapPos),
                "a bottom trapdoor resting on a lowered support must anchor at placement");

        w.setBlockState(support, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        double after = SlabSupport.getYOffset(w, trapPos, w.getBlockState(trapPos));
        ctx.assertTrue(Math.abs(after + 0.5) <= EPS,
                "never-pop violation: bottom trapdoor popped from -0.5 to " + after + " after its support was broken");
        ctx.complete();
    }

    // A stone button cannot vanilla-survive its support being removed (it auto-breaks), so a
    // break-and-re-read never-pop assertion is not applicable to it. What matters for L10 here is
    // that a FLOOR button still RECEIVES its placement anchor — i.e. the must-follow exclusion is
    // NOT keyed on isCeilingAttached (which blanket-matches every ButtonBlock). If it were, a floor
    // button would lose its anchor and pop whenever a survivable support change occurred.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void floorButtonOnBottomSlabStillAnchorsAtPlacement(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 11);
        BlockPos buttonPos = slabPos.up();
        w.setBlockState(slabPos, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(buttonPos, Blocks.STONE_BUTTON.getDefaultState().with(Properties.BLOCK_FACE, BlockFace.FLOOR),
                Block.NOTIFY_LISTENERS);
        placeAsIfOnPlaced(w, buttonPos);

        double before = SlabSupport.getYOffset(w, buttonPos, w.getBlockState(buttonPos));
        ctx.assertTrue(Math.abs(before + 0.5) <= EPS, "setup: floor button on a bottom slab should render -0.5, got " + before);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, buttonPos),
                "REGRESSION GUARD (not on the sibling): a FLOOR button must keep its placement anchor — "
                        + "the must-follow exclusion must not be keyed on isCeilingAttached, which blanket-matches all buttons");
        ctx.complete();
    }

    // ── regression: a flat (never-lowered) decorative object must never gain a spurious anchor ────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void flatCandleNeverAnchors(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 15);
        BlockPos candlePos = ground.up();
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(candlePos, Blocks.CANDLE.getDefaultState(), Block.NOTIFY_LISTENERS);
        placeAsIfOnPlaced(w, candlePos);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, candlePos),
                "regression: a candle on ordinary (non-lowered) ground must not anchor");
        ctx.complete();
    }

    // ── THE L10 FIX (branch-specific): must-follow ceiling decorations must NOT freeze ────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void sporeBlossomUnderLoweredSupportKeepsFollowingNotFrozen(TestContext ctx) {
        assertUndersideOwnerFollowsNotFrozen(ctx, Blocks.SPORE_BLOSSOM.getDefaultState(), "spore blossom", 19);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void hangingRootsUnderLoweredSupportKeepsFollowingNotFrozen(TestContext ctx) {
        assertUndersideOwnerFollowsNotFrozen(ctx, Blocks.HANGING_ROOTS.getDefaultState(), "hanging roots", 23);
    }

    private void assertUndersideOwnerFollowsNotFrozen(TestContext ctx, net.minecraft.block.BlockState deco,
                                                      String label, int z) {
        ServerWorld w = ctx.getWorld();
        // Lowered SOLID full-block support ABOVE the decoration (underside-owner full-block path).
        BlockPos support = buildAnchoredLoweredSupportWithAirBelow(w, ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 4, z));
        BlockPos decoPos = support.down();               // decoration hangs under the lowered stone (air below the support)
        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        ctx.assertTrue(Math.abs(supportDy + 0.5) <= EPS,
                "setup: anchored stone support should render -0.5, got " + supportDy + " -- else this test proves nothing");

        w.setBlockState(decoPos, deco, Block.NOTIFY_LISTENERS);
        double decoDyBefore = SlabSupport.getYOffset(w, decoPos, w.getBlockState(decoPos));
        ctx.assertTrue(Math.abs(decoDyBefore + 0.5) <= EPS,
                "setup: " + label + " under the lowered support must itself follow to -0.5, got " + decoDyBefore
                        + " -- else this test proves nothing");
        placeAsIfOnPlaced(w, decoPos);

        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, decoPos),
                "THE FIX: a " + label + " is a must-follow ceiling decoration and must NOT be frozen at a "
                        + "placement anchor -- getYOffsetInner's anchor branch would then pin it and it would stop "
                        + "tracking its support (stale gap when the support's own dy later changes)");
        ctx.complete();
    }

    // ── regression: a hanging lantern (HANGING) must keep following, never freeze via the freeze path ─

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void hangingLanternNeverAnchorsViaFreezePath(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        // Deterministically-anchored lowered support with air below (isolation-immune — see
        // buildAnchoredLoweredSupportWithAirBelow; the earlier cantilevered dirt.east() form flaked
        // its setup dy when batch composition shifted the structure layout).
        BlockPos support = buildAnchoredLoweredSupportWithAirBelow(w, ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 4, 27));
        BlockPos lanternPos = support.down();    // lantern hangs from support's underside (air below the support)

        w.setBlockState(lanternPos, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true),
                Block.NOTIFY_LISTENERS);
        double lanternDy = SlabSupport.getYOffset(w, lanternPos, w.getBlockState(lanternPos));
        ctx.assertTrue(Math.abs(lanternDy + 0.5) <= EPS,
                "setup: hanging lantern below the lowered support must follow to -0.5, got " + lanternDy
                        + " -- else this test proves nothing");
        placeAsIfOnPlaced(w, lanternPos);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, lanternPos),
                "regression: a hanging lantern must keep dynamically following its support, not be frozen via freezeLoweredOnPlace");
        ctx.complete();
    }
}
