package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * L10 — PINNING TEST for a bug that is ALREADY CORRECT on this branch (26.2). Ported as a permanent
 * regression guard from the 1.21.11 sibling's {@code DecorativeObjectSupportAnchorTest}, where the
 * bug WAS real and required a dedicated {@code qualifiesForDecorativeObjectAnchor} anchor lane.
 *
 * <p>The 1.21.11-reported bug (2026-07-04 recorder session, "pop upon breaking at the end"): a
 * decorative, non-solid, non-connecting object (candle, floor button, bottom trapdoor, lever…)
 * resting on a lowered/flush bottom slab popped from -0.5 back to 0.0 the instant that support was
 * broken, because it had been {@code anchor=none} for its whole lifetime — its -0.5 was a pure live
 * read with no persisted placement anchor.
 *
 * <p><b>Why this branch does NOT have the bug (the mechanism this test pins):</b> the 1.21.11
 * sibling's {@code freezeLoweredOnPlace} no-ops on a lowered placement (it delegates lowered-anchor
 * recording to {@code addAnchor}'s explicit per-lane qualifier disjunction), so a decorative object
 * — rejected by every qualifier lane — needed a NEW dedicated {@code qualifiesForDecorativeObjectAnchor}
 * lane. THIS branch's {@code freezeLoweredOnPlace} instead calls {@code addAnchorUnchecked} directly
 * for ANY genuinely-lowered placement ({@code dy < -1e-6}, and not side-inherited-only) — decorations
 * included — with no per-lane qualifier gate. {@code getYOffsetInner}'s generic
 * "{@code !(instanceof SlabBlock) && isAnchored}" branch then reads that persisted anchor back as a
 * lowered dy after the support is gone. So the decorative-object case is covered structurally here by
 * the UNCHECKED freeze; adding a {@code qualifiesForDecorativeObjectAnchor} lane would be redundant.
 * Verified empirically (throwaway probe, built + run + deleted) against the REAL placement
 * finalization hook {@code freezeLoweredOnPlace} (fired from {@code Block#setPlacedBy} HEAD): a candle
 * / trapdoor / lever on a bottom slab measured {@code dyLive=-0.5}, {@code anchoredAfterFreeze=true},
 * and {@code dyAfterBreak=-0.5} (stayed lowered, no pop).
 *
 * <p>This test pins BOTH halves of that mechanism so a future refactor of the freeze path (e.g. a
 * naive port of the sibling's delegate-to-qualifier-lanes shape that drops the unchecked
 * {@code addAnchorUnchecked}) is caught RED here rather than shipping the 1.21.11 pop bug onto 26.2.
 *
 * <p>NOTE ON REALISM: the tests use {@code setBlock(..., 2)} (client-notify only, no neighbour
 * updates / survival checks), so a decoration that would physically drop when unsupported (floor
 * button, flower pot) is NOT removed — the test can therefore isolate the DY behaviour. Candle,
 * trapdoor and lever survive the flag-2 break with the block intact, so they exercise the true
 * "stayed placed, did its dy pop?" invariant directly.
 */
public final class DecorativeObjectSupportAnchorTest {

    private static final double EPS = 1.0e-6;

    /**
     * The core L10 scenario, exactly matching the 1.21.11 sibling's first case: a decorative object
     * resting directly on a bottom slab lowers -0.5 (this mod's oldest "object sits on a slab"
     * mechanic), gets ANCHORED at placement by {@code freezeLoweredOnPlace}'s unchecked freeze, and
     * therefore does NOT pop back to flush when the slab is broken.
     */
    private void assertDecorationOnSlabAnchorsAndDoesNotPop(GameTestHelper helper, String label, BlockState decoState) {
        ServerLevel w = helper.getLevel();
        BlockPos slabRel = new BlockPos(2, 1, 2);
        BlockPos decoRel = slabRel.above();
        BlockPos slab = helper.absolutePos(slabRel);
        BlockPos deco = helper.absolutePos(decoRel);

        // A plain (flush) bottom slab — the object on top of it still lowers -0.5.
        w.setBlock(slab, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);

        // Place the decoration and fire the REAL placement finalization hook (Block#setPlacedBy HEAD).
        w.setBlock(deco, decoState, 2);
        double dyBefore = SlabSupport.getYOffset(w, deco, w.getBlockState(deco));
        if (Math.abs(dyBefore + 0.5) > EPS) {
            throw helper.assertionException(decoRel,
                    "setup: " + label + " resting on a bottom slab should render -0.5, got " + dyBefore);
        }
        SlabAnchorAttachment.freezeLoweredOnPlace(w, deco, w.getBlockState(deco));
        if (!SlabAnchorAttachment.isAnchored(w, deco)) {
            throw helper.assertionException(decoRel,
                    "THE MECHANISM (unchecked freeze): a " + label + " resting lowered on a bottom slab MUST be "
                            + "anchored by freezeLoweredOnPlace's dy<0 addAnchorUnchecked branch, or breaking the "
                            + "slab later pops it back to flush (the 1.21.11 'pop upon breaking at the end')");
        }

        // Break the slab (flag 2 — no survival check, so the decoration stays placed and we isolate dy).
        w.setBlock(slab, Blocks.AIR.defaultBlockState(), 2);
        BlockState decoAfter = w.getBlockState(deco);
        if (decoAfter.getBlock() != decoState.getBlock()) {
            throw helper.assertionException(decoRel,
                    "test invariant: " + label + " must stay placed across a flag-2 support break so the "
                            + "dy pop can be measured (got " + decoAfter.getBlock() + ")");
        }
        double dyAfter = SlabSupport.getYOffset(w, deco, decoAfter);
        if (Math.abs(dyAfter + 0.5) > EPS) {
            throw helper.assertionException(decoRel,
                    "never-pop violation: " + label + " popped from -0.5 to " + dyAfter
                            + " after its support was broken, even though it was never re-placed");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void candleOnSlabAnchorsAndDoesNotPop(GameTestHelper helper) {
        assertDecorationOnSlabAnchorsAndDoesNotPop(helper, "candle", Blocks.CANDLE.defaultBlockState());
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bottomTrapdoorOnSlabAnchorsAndDoesNotPop(GameTestHelper helper) {
        assertDecorationOnSlabAnchorsAndDoesNotPop(helper, "bottom_trapdoor",
                Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HALF, Half.BOTTOM));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floorLeverOnSlabAnchorsAndDoesNotPop(GameTestHelper helper) {
        assertDecorationOnSlabAnchorsAndDoesNotPop(helper, "lever", Blocks.LEVER.defaultBlockState());
    }

    /**
     * REGRESSION GUARD (matches the sibling's {@code flatCandleNeverAnchors}): a decorative object on
     * ordinary flush ground (NOT lowered) must never gain a spurious anchor from the freeze path —
     * otherwise it could refuse to follow a slab shoved under it later (the maintainer's WYSIWYG law).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatCandleNeverAnchors(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos groundRel = new BlockPos(2, 1, 2);
        BlockPos candleRel = groundRel.above();
        BlockPos candle = helper.absolutePos(candleRel);

        w.setBlock(helper.absolutePos(groundRel), Blocks.DIRT.defaultBlockState(), 2);
        w.setBlock(candle, Blocks.CANDLE.defaultBlockState(), 2);
        double dy = SlabSupport.getYOffset(w, candle, w.getBlockState(candle));
        if (Math.abs(dy) > EPS) {
            throw helper.assertionException(candleRel,
                    "setup: a candle on flush ground must render 0.0 (not lowered), got " + dy);
        }
        SlabAnchorAttachment.freezeLoweredOnPlace(w, candle, w.getBlockState(candle));
        if (SlabAnchorAttachment.isAnchored(w, candle)) {
            throw helper.assertionException(candleRel,
                    "regression: a candle on ordinary (non-lowered) ground must NOT anchor — only a "
                            + "genuinely lowered placement freezes an anchor");
        }
        helper.succeed();
    }

    /**
     * REGRESSION GUARD: the unchecked freeze must only fire on a GENUINELY lowered ({@code dy<0})
     * placement. A ceiling-hung follower (a HANGING lantern) whose support is flush reads dy=0 and so
     * is never anchored — it must keep dynamically tracking its support, never freeze. (On this branch
     * the HANGING dispatch in {@code getYOffsetInner} runs BEFORE any anchor read, so a hanging lantern
     * is a pure function of the support above regardless; this guard pins that it also never acquires a
     * stale freeze anchor via the placement hook.)
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingLanternNeverAnchorsViaFreeze(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos lanternRel = new BlockPos(2, 2, 2);
        BlockPos supportAboveRel = lanternRel.above();
        BlockPos lantern = helper.absolutePos(lanternRel);
        BlockPos supportAbove = helper.absolutePos(supportAboveRel);

        // A flush support directly above; the lantern hangs from it.
        w.setBlock(supportAbove, Blocks.DIRT.defaultBlockState(), 2);
        w.setBlock(lantern, Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), 2);
        double dy = SlabSupport.getYOffset(w, lantern, w.getBlockState(lantern));
        if (Math.abs(dy) > EPS) {
            throw helper.assertionException(lanternRel,
                    "setup: a hanging lantern under a flush support should render 0.0, got " + dy);
        }
        SlabAnchorAttachment.freezeLoweredOnPlace(w, lantern, w.getBlockState(lantern));
        if (SlabAnchorAttachment.isAnchored(w, lantern)) {
            throw helper.assertionException(lanternRel,
                    "regression: a hanging lantern (dy=0 under a flush support) must NOT freeze an anchor — "
                            + "ceiling followers keep dynamically tracking their support");
        }
        helper.succeed();
    }
}
