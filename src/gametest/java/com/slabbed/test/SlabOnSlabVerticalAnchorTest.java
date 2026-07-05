package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Ported from the 1.21.11 sibling branch's {@code SlabOnSlabVerticalAnchorTest} (live-reported
 * bug, 2026-07-04/05: a slab resting VERTICALLY on a lowered TOP/DOUBLE slab support below it
 * rendered the correct lowered dy live at render time, but never PERSISTED that as a placement
 * anchor — so breaking the support later popped the slab back to full height).
 *
 * <p>API ADAPTATION NOTE: unlike the sibling branch, on THIS branch (1.21.1)
 * {@code SlabAnchorAttachment.isAnchored}/{@code addAnchor}/{@code qualifiesForAnchor} back the
 * {@code ANCHOR_TYPE} attachment, which is an ORDINARY-FULL-BLOCK-ONLY lane —
 * {@code isOrdinaryFullBlockAnchorCandidate} explicitly excludes any {@code SlabBlock}, so a slab
 * can NEVER become {@code isAnchored() == true} here. The slab-specific persistence lane on this
 * branch is a SEPARATE attachment, {@code LOWERED_SLAB_CARRIER_TYPE}, queried via
 * {@code SlabAnchorAttachment.isPersistentLoweredSlabCarrier} and populated via
 * {@code updatePersistentLoweredSlabCarrier}. This test therefore asserts
 * {@code isPersistentLoweredSlabCarrier} for slabs (verified empirically via a throwaway probe
 * gametest before this fix — see the commit message for the exact measured booleans/doubles).
 *
 * <p>Root cause (this branch, more severe than the sibling's persistence-only gap): THREE
 * layers, not one —
 * <ol>
 *   <li>{@code getYOffsetInner}'s slab branch did not even LIVE-DERIVE -0.5 for a slab resting on
 *   a lowered TOP-type slab below (only {@code SlabType.DOUBLE} was handled, via
 *   {@code isLoweredDoubleSlabCarrier}) — fixed by the new
 *   {@code SlabSupport.isLoweredTopLikeSlabCarrier}.</li>
 *   <li>{@code SlabAnchorAttachment.qualifiesForPersistentLoweredSlabCarrier}'s disjunction had no
 *   sub-lane at all for "resting vertically on a lowered TOP/DOUBLE slab support" — fixed by the
 *   new {@code qualifiesForPersistentLoweredSlabOnVerticalLoweredSlabSupport} sub-lane.</li>
 *   <li>{@code BlockItemPlacementIntentMixin}'s ONLY call site of
 *   {@code updatePersistentLoweredSlabCarrier} was gated by a BOTTOM-type-only,
 *   full-block-support-only candidate check, so a TOP-type slab placement (the exact scenario
 *   below) could never reach the persistence dispatcher at all, regardless of (1) and (2) — fixed
 *   by widening the gate to delegate to {@code qualifiesForPersistentLoweredSlabCarrier} for any
 *   slab type.</li>
 * </ol>
 */
public final class SlabOnSlabVerticalAnchorTest {

    private static final double EPS = 1.0e-6;

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void breakingLowerSupportSlabDoesNotPopSlabRestingOnTop(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos vanillaBottomSlabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos dirtPos = vanillaBottomSlabPos.up();       // direct-anchor lowering source
        BlockPos supportSlabPos = dirtPos.east();            // TOP slab, lowered+anchored via HORIZONTAL adjacency (pre-existing lane)
        BlockPos upperSlabPos = supportSlabPos.up();         // TOP slab resting VERTICALLY on supportSlabPos (the new case)

        w.setBlockState(vanillaBottomSlabPos,
                Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);

        w.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtPos, w.getBlockState(dirtPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, dirtPos), "setup: dirt must anchor on the bottom slab");
        double dirtDy = SlabSupport.getYOffset(w, dirtPos, w.getBlockState(dirtPos));
        ctx.assertTrue(Math.abs(dirtDy + 0.5) <= EPS, "setup: anchored dirt should render -0.5, got " + dirtDy);

        w.setBlockState(supportSlabPos, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        // Real placement calls SlabAnchorAttachment.updatePersistentLoweredSlabCarrier via the
        // BlockItemPlacementIntentMixin finalization hook; simulate that directly.
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, supportSlabPos, w.getBlockState(supportSlabPos));
        double supportDy = SlabSupport.getYOffset(w, supportSlabPos, w.getBlockState(supportSlabPos));
        ctx.assertTrue(Math.abs(supportDy + 0.5) <= EPS,
                "setup: support slab beside anchored dirt should render -0.5, got " + supportDy);
        ctx.assertTrue(SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, supportSlabPos, w.getBlockState(supportSlabPos)),
                "setup: support slab must anchor via the pre-existing horizontal-adjacency lane");

        w.setBlockState(upperSlabPos, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        // Simulate placement finalization: the mixin calls updatePersistentLoweredSlabCarrier for
        // every real player slab placement, exactly like this direct call.
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, upperSlabPos, w.getBlockState(upperSlabPos));
        double upperDyBefore = SlabSupport.getYOffset(w, upperSlabPos, w.getBlockState(upperSlabPos));
        ctx.assertTrue(Math.abs(upperDyBefore + 0.5) <= EPS,
                "setup: slab resting on the lowered support should render -0.5, got " + upperDyBefore);
        ctx.assertTrue(SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, upperSlabPos, w.getBlockState(upperSlabPos)),
                "THE FIX: a slab resting on a lowered/anchored support must itself anchor at "
                        + "placement time, or breaking the support later pops it back to flush "
                        + "(live-reported 'pop upon breaking at the end')");

        // Break the support slab — the upper slab must NOT pop.
        w.setBlockState(supportSlabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        double upperDyAfter = SlabSupport.getYOffset(w, upperSlabPos, w.getBlockState(upperSlabPos));
        ctx.assertTrue(Math.abs(upperDyAfter + 0.5) <= EPS,
                "never-pop violation: slab popped from -0.5 to " + upperDyAfter
                        + " after its support was broken, even though it was never re-placed");
        ctx.complete();
    }

    // REGRESSION GUARD: a flat (never-lowered) slab resting on another flat slab must never gain
    // a spurious anchor.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void flatSlabOnFlatSlabNeverAnchors(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos lower = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 8);
        BlockPos upper = lower.up();
        w.setBlockState(lower, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, lower, w.getBlockState(lower));
        ctx.assertTrue(!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, lower, w.getBlockState(lower)),
                "setup: flat slab must not anchor");

        w.setBlockState(upper, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, upper, w.getBlockState(upper));
        ctx.assertTrue(!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, upper, w.getBlockState(upper)),
                "regression: a slab resting on a FLAT (non-lowered) slab must not spuriously anchor");
        ctx.complete();
    }

    // REGRESSION GUARD: a slab resting on a BOTTOM-type support (which is not itself "sunk") must
    // not anchor from the vertical lane either — only TOP/DOUBLE supports propagate their own
    // lowering upward (matches isLoweredTopLikeSlabCarrier's BOTTOM exclusion).
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void slabOnBottomTypeSupportNeverAnchorsVertically(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 12);
        BlockPos upper = support.up();
        w.setBlockState(support, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(upper, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, upper, w.getBlockState(upper));
        ctx.assertTrue(!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, upper, w.getBlockState(upper)),
                "regression: a slab resting on a BOTTOM-type (non-sunk) support must not anchor vertically");
        double upperDy = SlabSupport.getYOffset(w, upper, w.getBlockState(upper));
        ctx.assertTrue(Math.abs(upperDy) <= EPS,
                "regression: a slab resting on a BOTTOM-type support must not live-derive lowered either, got "
                        + upperDy);
        ctx.complete();
    }
}
