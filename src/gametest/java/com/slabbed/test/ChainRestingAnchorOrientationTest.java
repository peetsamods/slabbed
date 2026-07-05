package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Cross-phase-review correction of {@code 27aa96e1} (L10). L10 added
 * {@link SlabSupport#isMustFollowCeilingDecoration} to keep MUST-FOLLOW ceiling decorations from
 * being frozen at a placement anchor. Its chain clause matched ANY {@code AXIS=Y}
 * {@link net.minecraft.block.ChainBlock} unconditionally — but the SAME blockstate represents both a
 * chain HANGING from a ceiling support above it (which genuinely must keep following) AND a chain
 * merely RESTING on a lowered bottom slab below it (which, exactly like a candle / floor button,
 * reaches its {@code -0.5} dy through the generic {@code hasSlabInColumn} fallback and DOES want its
 * never-pop freeze anchor). Excluding ALL Y-chains from the freeze therefore re-opened, for a
 * floor-resting chain, the exact pop L10 exists to prevent: break the slab and its dy jumps from
 * {@code -0.5} back to {@code 0.0}.
 *
 * <p>The same ambiguity was found (failure-mode-4 sweep of the other must-follow members, backed by a
 * decompiled vanilla 1.21.1 survival audit) for a SITTING lantern ({@code HANGING=false}), whose
 * vanilla {@code canPlaceAt} requires support BELOW — it is floor-resting, not ceiling-hung, and
 * likewise wants its anchor. Spore blossom / hanging roots / pale hanging moss / hanging + wall
 * hanging signs / any {@code HANGING=true} block were confirmed genuinely ceiling-ONLY in vanilla
 * (their floor-resting states cannot survive), so they stay unconditionally excluded.
 *
 * <p>The fix makes {@link SlabSupport#isMustFollowCeilingDecoration(net.minecraft.world.BlockView,
 * BlockPos, BlockState)} orientation-aware: a Y-chain is must-follow only when it is genuinely
 * ceiling-bridged ({@link SlabSupport#isCeilingBridgedVerticalChainColumnMember}); a lantern is
 * must-follow only when {@code HANGING=true}.
 */
public final class ChainRestingAnchorOrientationTest {

    private static final double EPS = 1.0e-6;

    /** Real placement runs BlockOnPlacedAnchorMixin.onPlaced -> addAnchor + freezeLoweredOnPlace. */
    private static void placeAsIfOnPlaced(ServerWorld w, BlockPos pos) {
        SlabAnchorAttachment.addAnchor(w, pos, w.getBlockState(pos));
        SlabAnchorAttachment.freezeLoweredOnPlace(w, pos, w.getBlockState(pos));
    }

    private static BlockState yChain() {
        return Blocks.CHAIN.getDefaultState().with(Properties.AXIS, Direction.Axis.Y);
    }

    // Builds a lowered SOLID support that has AIR beneath it (so a decoration can hang from its
    // underside), whose -0.5 dy is a DETERMINISTIC anchor lookup — not a cantilever side-walk. A
    // support is placed on a bottom slab and anchored, then the slab is pulled: the never-pop anchor
    // holds the support at -0.5 with open air below. This is isolation-immune (no horizontal
    // adjacency walk that could read across the 8x8x8 gametest structure bound into a neighbouring
    // test — the same flake class fixed in DecorativeObjectSupportAnchorTest via
    // buildAnchoredLoweredSupportWithAirBelow, commit 0d4f6ce4). Returns the support pos.
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

    // ── THE FIX: a Y-chain RESTING on a lowered bottom slab must anchor and never pop ────────────────
    // Vanilla chains have no support requirement (ChainBlock has no canPlaceAt check), so breaking the
    // slab below leaves the chain floating in place — the perfect never-pop probe: its dy must stay
    // -0.5 rather than popping to 0.0. (RED before the fix: the chain was excluded from the freeze
    // anchor, so breaking the slab popped it to 0.0.)
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void verticalChainRestingOnLoweredBottomSlabDoesNotPopWhenSlabBreaks(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos chainPos = slabPos.up();
        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(chainPos, yChain(), Block.NOTIFY_LISTENERS);

        // Nothing above -> not ceiling-hung; the chain rests on the slab below it.
        ctx.assertTrue(w.getBlockState(chainPos.up()).isAir(),
                "setup: a resting chain must have nothing above it -- else this test proves nothing");
        double before = SlabSupport.getYOffset(w, chainPos, w.getBlockState(chainPos));
        ctx.assertTrue(Math.abs(before + 0.5) <= EPS,
                "setup: a chain resting on a bottom slab should render -0.5, got " + before);

        placeAsIfOnPlaced(w, chainPos);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, chainPos),
                "THE FIX: a Y-chain resting on a lowered bottom slab must anchor at placement -- else "
                        + "breaking the slab pops it back to flush (L10 excluded ALL Y-chains, including "
                        + "floor-resting ones, from the freeze)");

        w.setBlockState(slabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        // The chain floats (no vanilla support requirement), so it is still present to re-read.
        BlockState after = w.getBlockState(chainPos);
        ctx.assertTrue(after.getBlock() instanceof net.minecraft.block.ChainBlock,
                "setup: a floating chain should still exist after its support slab is broken, got " + after);
        double afterDy = SlabSupport.getYOffset(w, chainPos, after);
        ctx.assertTrue(Math.abs(afterDy + 0.5) <= EPS,
                "never-pop violation: a resting Y-chain popped from -0.5 to " + afterDy
                        + " after its support slab was broken");
        ctx.complete();
    }

    // ── THE FIX (second ambiguous member): a SITTING lantern (HANGING=false) must still anchor ───────
    // Failure-mode-4 sweep result: the sitting lantern is floor-resting-capable in vanilla
    // (canPlaceAt requires support BELOW), so like a candle it wants its placement anchor. Unlike a
    // chain, a lantern is removed by vanilla neighbor-survival when its direct support breaks, so the
    // decisive assertion here is that it RECEIVES the anchor at placement (mirrors the L10 floor-button
    // regression guard). Before the fix, isLoweredUndersideHangerOwner matched lanterns regardless of
    // HANGING, so a sitting lantern was wrongly excluded from the freeze.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void sittingLanternOnLoweredBottomSlabAnchorsAtPlacement(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 7);
        BlockPos lanternPos = slabPos.up();
        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(lanternPos, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, false),
                Block.NOTIFY_LISTENERS);

        double before = SlabSupport.getYOffset(w, lanternPos, w.getBlockState(lanternPos));
        ctx.assertTrue(Math.abs(before + 0.5) <= EPS,
                "setup: a sitting lantern on a bottom slab should render -0.5, got " + before);
        ctx.assertTrue(!SlabSupport.isMustFollowCeilingDecoration(w, lanternPos, w.getBlockState(lanternPos)),
                "a HANGING=false lantern rests on the support below it -- it must not be classed as a "
                        + "must-follow ceiling decoration");

        placeAsIfOnPlaced(w, lanternPos);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, lanternPos),
                "THE FIX: a sitting lantern (HANGING=false) resting on a lowered slab must anchor at "
                        + "placement (isLoweredUndersideHangerOwner matched lanterns regardless of HANGING, "
                        + "wrongly excluding the floor-standing variant from the freeze)");
        ctx.complete();
    }

    // ── REGRESSION GUARD: a genuinely ceiling-hung Y-chain must STILL never be frozen ────────────────
    // This is the orientation L10's chain clause was originally added to protect. It must stay green
    // both before and after the fix: a chain hanging under a TOP slab reaches up (+0.5) and must keep
    // dynamically following its ceiling support, so it must not receive a placement anchor.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void ceilingHungVerticalChainUnderTopSlabIsNeverFrozen(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos topSlabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 5, 11);
        BlockPos chainPos = topSlabPos.down();
        w.setBlockState(topSlabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(chainPos, yChain(), Block.NOTIFY_LISTENERS);

        ctx.assertTrue(SlabSupport.isVerticalChainDirectlyUnderCeilingSupport(w, chainPos, w.getBlockState(chainPos)),
                "setup: the chain must be directly under a ceiling (TOP-slab) support -- else this test proves nothing");
        double before = SlabSupport.getYOffset(w, chainPos, w.getBlockState(chainPos));
        ctx.assertTrue(Math.abs(before - 0.5) <= EPS,
                "setup: a chain hanging under a top slab reaches up to dy=+0.5 (this branch's ceiling-mount "
                        + "geometry), got " + before);
        ctx.assertTrue(SlabSupport.isMustFollowCeilingDecoration(w, chainPos, w.getBlockState(chainPos)),
                "regression: a ceiling-bridged Y-chain must remain a must-follow ceiling decoration");

        placeAsIfOnPlaced(w, chainPos);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, chainPos),
                "regression: a ceiling-hung Y-chain must keep dynamically following its ceiling support, "
                        + "never be frozen via freezeLoweredOnPlace");
        ctx.complete();
    }

    // A chain in a STACK leading up to a top slab (not directly under the slab) must also be recognised
    // as ceiling-hung and stay unfrozen (isCeilingBridgedVerticalChainColumnMember walks the column).
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void ceilingBridgedChainColumnLowerMemberIsNeverFrozen(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos topSlabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 6, 15);
        BlockPos upperChain = topSlabPos.down();
        BlockPos lowerChain = upperChain.down();
        w.setBlockState(topSlabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(upperChain, yChain(), Block.NOTIFY_LISTENERS);
        w.setBlockState(lowerChain, yChain(), Block.NOTIFY_LISTENERS);

        ctx.assertTrue(!SlabSupport.isVerticalChainDirectlyUnderCeilingSupport(w, lowerChain, w.getBlockState(lowerChain)),
                "setup: the lower chain must NOT be directly under the slab -- else the column walk is untested");
        ctx.assertTrue(SlabSupport.isCeilingBridgedVerticalChainColumnMember(w, lowerChain, w.getBlockState(lowerChain)),
                "setup: the lower chain must be a ceiling-bridged column member via the chain above it");
        ctx.assertTrue(SlabSupport.isMustFollowCeilingDecoration(w, lowerChain, w.getBlockState(lowerChain)),
                "regression: a lower chain in a ceiling-bridged column must remain must-follow");

        placeAsIfOnPlaced(w, lowerChain);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, lowerChain),
                "regression: a chain in a ceiling-hung column (not directly under the slab) must not be frozen");
        ctx.complete();
    }

    // ── REGRESSION GUARD: a HANGING=true lantern must STILL never be frozen (orientation logic must ──
    // not have collapsed the HANGING lantern into the floor-resting lane).
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void hangingLanternRemainsMustFollowAndUnfrozen(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        // Deterministically-anchored lowered support with air below (isolation-immune — see
        // buildAnchoredLoweredSupportWithAirBelow; the earlier cantilevered dirt.east() form at local
        // z=19 was already outside the "empty" template's 8x8x8 structure bounds regardless of the
        // .east() hop, so it could intermittently read a neighbouring test's blocks under batch
        // repartitioning).
        BlockPos support = buildAnchoredLoweredSupportWithAirBelow(w, ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 4, 3));
        BlockPos lanternPos = support.down();    // lantern hangs from the support's underside (air below the support)

        w.setBlockState(lanternPos, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true),
                Block.NOTIFY_LISTENERS);
        double lanternDy = SlabSupport.getYOffset(w, lanternPos, w.getBlockState(lanternPos));
        ctx.assertTrue(Math.abs(lanternDy + 0.5) <= EPS,
                "setup: a hanging lantern below the lowered support must follow to -0.5, got " + lanternDy);
        ctx.assertTrue(SlabSupport.isMustFollowCeilingDecoration(w, lanternPos, w.getBlockState(lanternPos)),
                "regression: a HANGING=true lantern must remain a must-follow ceiling decoration");

        placeAsIfOnPlaced(w, lanternPos);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, lanternPos),
                "regression: a hanging lantern must keep dynamically following its support, not be frozen");
        ctx.complete();
    }

    // ── REGRESSION GUARD: a spore blossom is genuinely ceiling-ONLY -> stays excluded regardless of ──
    // any world lookup (the unconditional-ceiling-only set must not have been narrowed by the fix).
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void sporeBlossomRemainsUnconditionallyMustFollow(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 23);
        BlockPos decoPos = slabPos.up();
        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(decoPos, Blocks.SPORE_BLOSSOM.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.isMustFollowCeilingDecoration(w, decoPos, w.getBlockState(decoPos)),
                "regression: a spore blossom is ceiling-only in vanilla and must stay must-follow in any orientation");
        placeAsIfOnPlaced(w, decoPos);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, decoPos),
                "regression: a spore blossom must never be frozen (it must keep following its ceiling support)");
        ctx.complete();
    }
}
