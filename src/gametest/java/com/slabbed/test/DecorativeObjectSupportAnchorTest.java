package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Live-reported bug (2026-07-04 recorder session, "pop upon breaking at the end"): a decorative,
 * non-solid, non-connecting object resting on top of a lowered/flush-bottom-slab support popped
 * from -0.5 to 0.0 the instant that support was broken, having been {@code anchor=none} for its
 * entire lifetime.
 *
 * <ul>
 *   <li><b>Candle on a birch bottom slab</b> ({@code (1,-59,27)} in the recorder): the SLAB below
 *       was itself flush (not lowered) — a candle resting DIRECTLY on any bottom slab already
 *       gets -0.5 via {@code SlabSupport.shouldOffset}'s generic branch, this mod's oldest, most
 *       basic "object sits on a slab" mechanic. Breaking that slab popped the candle to 0.0.</li>
 *   <li><b>Birch trapdoor on a lowered/anchored spruce fence</b> ({@code (1,-58,30)}): the fence
 *       below WAS anchored/lowered (-0.5). Breaking the fence popped the trapdoor to 0.0.</li>
 * </ul>
 *
 * <p>Root cause: candles, trapdoors, and every other non-solid, non-connecting decorative object
 * are rejected by {@code isOrdinaryAnchorCandidate} (not a solid block), are not a
 * {@code SlabBlock} or {@code BlockEntityProvider}, so NONE of the existing anchor qualifier
 * lanes cover them — their -0.5 was PURELY a live read with no persisted anchor at all, unlike
 * every other subject category this mod protects.
 *
 * <p>Fix: {@code qualifiesForDecorativeObjectAnchor} — a subject not already covered by another
 * lane, not a ceiling-hanging/follow-type decoration (which must keep dynamically tracking its
 * support), whose live {@code getYOffset} is negative, now anchors at placement.
 */
public final class DecorativeObjectSupportAnchorTest {

    private static final double EPS = 1.0e-6;

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void candleRestingOnBottomSlabDoesNotPopWhenSlabBreaks(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos candlePos = slabPos.up();

        w.setBlockState(slabPos, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(candlePos, Blocks.CANDLE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, candlePos, w.getBlockState(candlePos));

        double candleDyBefore = SlabSupport.getYOffset(w, candlePos, w.getBlockState(candlePos));
        ctx.assertTrue(Math.abs(candleDyBefore + 0.5) <= EPS,
                "setup: candle resting on a bottom slab should render -0.5, got " + candleDyBefore);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, candlePos),
                "THE FIX: a candle resting on a bottom slab must anchor at placement time, or "
                        + "breaking the slab later pops it back to flush "
                        + "(live-reported 'pop upon breaking at the end')");

        w.setBlockState(slabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        double candleDyAfter = SlabSupport.getYOffset(w, candlePos, w.getBlockState(candlePos));
        ctx.assertTrue(Math.abs(candleDyAfter + 0.5) <= EPS,
                "never-pop violation: candle popped from -0.5 to " + candleDyAfter
                        + " after its support was broken, even though it was never re-placed");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void trapdoorRestingOnLoweredFenceDoesNotPopWhenFenceBreaks(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos vanillaBottomSlabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 4);
        BlockPos dirtPos = vanillaBottomSlabPos.up();
        BlockPos fencePos = dirtPos.east();
        BlockPos trapdoorPos = fencePos.up();

        w.setBlockState(vanillaBottomSlabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtPos, w.getBlockState(dirtPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, dirtPos), "setup: dirt must anchor on the bottom slab");

        w.setBlockState(fencePos, Blocks.SPRUCE_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, fencePos, w.getBlockState(fencePos));
        double fenceDy = SlabSupport.getYOffset(w, fencePos, w.getBlockState(fencePos));
        ctx.assertTrue(Math.abs(fenceDy + 0.5) <= EPS,
                "setup: fence beside anchored dirt should render -0.5, got " + fenceDy);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, fencePos), "setup: fence must anchor via the horizontal-adjacency lane");

        w.setBlockState(trapdoorPos, Blocks.BIRCH_TRAPDOOR.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, trapdoorPos, w.getBlockState(trapdoorPos));
        double trapdoorDyBefore = SlabSupport.getYOffset(w, trapdoorPos, w.getBlockState(trapdoorPos));
        ctx.assertTrue(Math.abs(trapdoorDyBefore + 0.5) <= EPS,
                "setup: trapdoor resting on the lowered fence should render -0.5, got " + trapdoorDyBefore);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, trapdoorPos),
                "THE FIX: a trapdoor resting on a lowered fence must anchor at placement time, or "
                        + "breaking the fence later pops it back to flush");

        w.setBlockState(fencePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        double trapdoorDyAfter = SlabSupport.getYOffset(w, trapdoorPos, w.getBlockState(trapdoorPos));
        ctx.assertTrue(Math.abs(trapdoorDyAfter + 0.5) <= EPS,
                "never-pop violation: trapdoor popped from -0.5 to " + trapdoorDyAfter
                        + " after its support was broken, even though it was never re-placed");
        ctx.complete();
    }

    // REGRESSION GUARD: a flat (never-lowered) candle must never gain a spurious anchor.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatCandleNeverAnchors(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 5);
        w.setBlockState(pos, Blocks.CANDLE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, pos, w.getBlockState(pos));
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, pos),
                "regression: a candle on ordinary ground (not lowered) must not anchor");
        ctx.complete();
    }

    // REGRESSION GUARD: a hanging lantern (ceiling-attached, must keep dynamically following its
    // support) must NOT be captured by this new lane, or it would freeze instead of tracking.
    // The lantern's support (the dirt above it) must itself be genuinely lowered, or the lantern's
    // own dy would already be 0 for an unrelated reason and this test would prove nothing.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingLanternNeverAnchorsViaDecorativeLane(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos vanillaBottomSlabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 6);
        BlockPos dirtAnchorPos = vanillaBottomSlabPos.up();
        BlockPos supportPos = dirtAnchorPos.east();   // stone beside anchored dirt, air below it (cantilevered)
        BlockPos lanternPos = supportPos.down();      // lantern hangs from supportPos's underside

        w.setBlockState(vanillaBottomSlabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirtAnchorPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtAnchorPos, w.getBlockState(dirtAnchorPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, dirtAnchorPos), "setup: dirt must anchor on the bottom slab");

        w.setBlockState(supportPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, supportPos, w.getBlockState(supportPos));
        double supportDy = SlabSupport.getYOffset(w, supportPos, w.getBlockState(supportPos));
        ctx.assertTrue(Math.abs(supportDy + 0.5) <= EPS, "setup: cantilevered stone beside anchored dirt should read -0.5, got " + supportDy);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, supportPos), "setup: cantilevered stone must anchor via the adjacent-lowered-full-block lane");

        w.setBlockState(lanternPos, Blocks.LANTERN.getDefaultState().with(net.minecraft.state.property.Properties.HANGING, true),
                Block.NOTIFY_LISTENERS);
        double lanternDy = SlabSupport.getYOffset(w, lanternPos, w.getBlockState(lanternPos));
        ctx.assertTrue(Math.abs(lanternDy + 0.5) <= EPS,
                "setup: hanging lantern below the anchored support must itself read -0.5 "
                        + "(hanger-follow), got " + lanternDy + " -- otherwise this test proves nothing");

        SlabAnchorAttachment.addAnchor(w, lanternPos, w.getBlockState(lanternPos));
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, lanternPos),
                "regression: a hanging lantern must keep dynamically following its support, not freeze via the decorative lane");
        ctx.complete();
    }
}
