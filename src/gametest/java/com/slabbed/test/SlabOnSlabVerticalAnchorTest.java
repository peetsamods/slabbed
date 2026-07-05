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
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * L8 — ported from the 1.21.11/1.21.1 sibling branches' {@code SlabOnSlabVerticalAnchorTest}
 * (live-reported bug, 2026-07-04/05: a slab resting VERTICALLY on a lowered TOP/DOUBLE slab support
 * below it rendered the correct lowered dy live, but never PERSISTED that as a placement anchor — so
 * breaking the support later popped the slab back to full height, "pop upon breaking at the end").
 *
 * <p>On THIS branch (26.2) the gap was the same THREE layers the 1.21.1 sibling found, confirmed by
 * a throwaway empirical probe against real production methods before any fix code was written
 * (measured: {@code upperDyLiveBefore=0.0}, {@code upperQualifies=false}, {@code upperPersistAfterDispatch=false}):
 * <ol>
 *   <li>{@code SlabSupport.getYOffsetInner}'s slab-below branch did not LIVE-DERIVE -0.5 for a slab
 *   resting on a lowered TOP-type slab below (only {@code SlabType.DOUBLE} was handled, via
 *   {@code isLoweredDoubleSlabCarrier}) — fixed by the new
 *   {@link SlabSupport#isLoweredTopLikeSlabCarrier}.</li>
 *   <li>{@code SlabAnchorAttachment.qualifiesForPersistentLoweredSlabCarrier}'s disjunction had no
 *   sub-lane for "resting vertically on a lowered TOP/DOUBLE slab support" — fixed by the new
 *   {@code qualifiesForPersistentLoweredSlabOnVerticalLoweredSlabSupport} sub-lane.</li>
 *   <li>{@code BlockItemPlacementIntentMixin}'s below-support candidate gate was BOTTOM-type-only,
 *   full-block-support-only, so a TOP/DOUBLE slab placement could never reach the persistence
 *   dispatcher at all — fixed by widening it to delegate to
 *   {@code qualifiesForPersistentLoweredSlabCarrier} for any slab type.</li>
 * </ol>
 *
 * <p>API ADAPTATION NOTE: on this branch (as on 1.21.1)
 * {@code SlabAnchorAttachment.isAnchored}/{@code addAnchor} back the {@code ANCHOR_TYPE} attachment,
 * an ORDINARY-FULL-BLOCK-ONLY lane that excludes any {@code SlabBlock} — a slab can never become
 * {@code isAnchored() == true}. The slab-specific persistence lane is a SEPARATE attachment,
 * {@code LOWERED_SLAB_CARRIER_TYPE}, queried via
 * {@link SlabAnchorAttachment#isPersistentLoweredSlabCarrier} and populated via
 * {@code updatePersistentLoweredSlabCarrier}; this test asserts THAT predicate for slabs (verified
 * empirically via the throwaway probe before this fix).
 *
 * <p>Coordinates are deliberately small (base near (2,1,2), matching the sibling's post-hardening
 * fix and this branch's other gametests): every local coord stays well within the
 * {@code fabric-gametest-api-v1:empty} template's small bounds, and each {@code @GameTest} method
 * gets its own independent structure instance, so methods do not need to be offset from each other.
 */
public final class SlabOnSlabVerticalAnchorTest {

    private static final double EPS = 1.0e-6;

    private static BlockState topSlab() {
        return Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void breakingLowerSupportSlabDoesNotPopSlabRestingOnTop(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        // Build a genuinely lowered + PERSISTED TOP-like support, exactly the way real gameplay does
        // (not by hand-forcing an attachment): a lowered full block (anchored dirt) carries a lowered
        // BOTTOM slab, and a DOUBLE support slab placed beside that persisted lowered slab inherits +
        // persists its own lowering via the horizontal slab side-lane. THEN the upper slab rests on
        // that lowered DOUBLE support — the L8 vertical case.
        BlockPos groundSlabRel = new BlockPos(2, 1, 2);  // vanilla ground slab (support for the dirt)
        BlockPos dirtRel = groundSlabRel.above();         // anchored lowered full block
        BlockPos loweredBottomRel = dirtRel.above();      // BOTTOM slab ON dirt -> persists lowered
        BlockPos supportRel = loweredBottomRel.east();    // DOUBLE support beside the persisted lowered slab
        BlockPos upperRel = supportRel.above();           // slab resting VERTICALLY on the support (the L8 case)

        BlockPos groundSlab = helper.absolutePos(groundSlabRel);
        BlockPos dirt = helper.absolutePos(dirtRel);
        BlockPos loweredBottom = helper.absolutePos(loweredBottomRel);
        BlockPos support = helper.absolutePos(supportRel);
        BlockPos upper = helper.absolutePos(upperRel);

        w.setBlock(groundSlab,
                Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);

        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        if (!SlabAnchorAttachment.isAnchored(w, dirt)) {
            throw helper.assertionException(dirtRel, "setup: dirt must anchor on the ground slab");
        }
        double dirtDy = SlabSupport.getYOffset(w, dirt, w.getBlockState(dirt));
        if (Math.abs(dirtDy + 0.5) > EPS) {
            throw helper.assertionException(dirtRel, "setup: anchored dirt should render -0.5, got " + dirtDy);
        }

        // Lowered BOTTOM slab directly on the anchored dirt — persists via the bottom-on-lowered-FB lane.
        w.setBlock(loweredBottom, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));
        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom))) {
            throw helper.assertionException(loweredBottomRel,
                    "setup: BOTTOM slab on anchored dirt must persist as a lowered carrier");
        }

        // Support: a DOUBLE slab beside the persisted lowered BOTTOM slab — inherits + persists its
        // own lowering via the horizontal slab side-lane, giving a genuinely lowered TOP-like support.
        w.setBlock(support, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, support, w.getBlockState(support));
        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        if (Math.abs(supportDy + 0.5) > EPS) {
            throw helper.assertionException(supportRel,
                    "setup: DOUBLE support beside the lowered slab should render -0.5, got " + supportDy);
        }
        // The support qualifies as a lowered TOP-like carrier (persisted), which the upper slab inherits from.
        if (!SlabSupport.isLoweredTopLikeSlabCarrier(w, support, w.getBlockState(support))) {
            throw helper.assertionException(supportRel,
                    "setup: lowered DOUBLE support must be recognised as a top-like carrier");
        }

        // Upper: a TOP slab resting ON the lowered support. Simulate real placement finalization,
        // which calls updatePersistentLoweredSlabCarrier for every player slab placement.
        w.setBlock(upper, topSlab(), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, upper, w.getBlockState(upper));
        double upperDyBefore = SlabSupport.getYOffset(w, upper, w.getBlockState(upper));
        if (Math.abs(upperDyBefore + 0.5) > EPS) {
            throw helper.assertionException(upperRel,
                    "slab resting on the lowered support should render -0.5, got " + upperDyBefore);
        }
        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, upper, w.getBlockState(upper))) {
            throw helper.assertionException(upperRel,
                    "THE FIX: a slab resting on a lowered TOP/DOUBLE support must itself persist a "
                            + "lowered-slab-carrier anchor at placement, or breaking the support later "
                            + "pops it back to flush (live-reported 'pop upon breaking at the end')");
        }

        // Break the support slab — the upper slab must NOT pop back to flush.
        w.setBlock(support, Blocks.AIR.defaultBlockState(), 2);
        double upperDyAfter = SlabSupport.getYOffset(w, upper, w.getBlockState(upper));
        if (Math.abs(upperDyAfter + 0.5) > EPS) {
            throw helper.assertionException(upperRel,
                    "never-pop violation: slab popped from -0.5 to " + upperDyAfter
                            + " after its support was broken, even though it was never re-placed");
        }
        helper.succeed();
    }

    /**
     * Cross-phase-review Finding 1 (correcting L8 {@code f70eec96}): a BOTTOM slab placed on a full
     * block that itself compounds to {@code -1.0} (a full block resting on a lowered bottom-slab stack,
     * un-anchored) must PERSIST as a lowered carrier and must NOT pop when the {@code -1.0} support is
     * broken. L8's mixin-gate widening delegated the below-support decision to
     * {@code qualifiesForPersistentLoweredSlabCarrier}, whose sub-lane
     * {@code qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlock} checked the support reads
     * exactly {@code -0.5} (the OLD mixin gate used {@code < 0.0d}) — narrowing the accepted magnitude
     * and dropping the {@code -1.0} case. The fix restores {@code < 0.0d}.
     *
     * <p>HONEST SCOPE NOTE: end-to-end this exact scenario is ALSO covered by the more-lenient
     * {@code isLoweredSideLaneSlabCarrier} / non-recursive-carrier lanes (both accept {@code -1.0} via
     * {@code hasBottomSlabBelow}), so this test PASSES both before and after the sub-lane fix — it is a
     * standing never-pop invariant pin, NOT the discriminating RED for the fix line. The sub-lane
     * regression itself was proven directly (throwaway probe: for a {@code -1.0} un-anchored support the
     * OLD {@code < 0.0d} gate returned true while the narrowed {@code == -0.5} sub-lane returned false);
     * the fix removes that latent inconsistency so persistence never depends on the masking lane.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bottomSlabOnCompoundMinusOneFullBlockPersistsAndDoesNotPop(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        // Compound -1.0 full block (un-anchored, geometric): stone / bottom slab / stone / bottom slab /
        // stone -> the top stone reads -1.0.
        w.setBlock(helper.absolutePos(new BlockPos(2, 1, 2)), Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(helper.absolutePos(new BlockPos(2, 2, 2)),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(helper.absolutePos(new BlockPos(2, 3, 2)), Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(helper.absolutePos(new BlockPos(2, 4, 2)),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        BlockPos fb = helper.absolutePos(new BlockPos(2, 5, 2));
        BlockPos upperRel = new BlockPos(2, 6, 2);
        BlockPos upper = helper.absolutePos(upperRel);
        w.setBlock(fb, Blocks.STONE.defaultBlockState(), 2);
        double fbDy = SlabSupport.getYOffset(w, fb, w.getBlockState(fb));
        if (Math.abs(fbDy + 1.0) > EPS) {
            throw helper.assertionException(new BlockPos(2, 5, 2),
                    "setup: the compound full block must read -1.0, got " + fbDy);
        }
        if (SlabAnchorAttachment.isAnchored(w, fb)) {
            throw helper.assertionException(new BlockPos(2, 5, 2),
                    "setup: the compound full block must be UN-anchored (purely geometric -1.0)");
        }

        // BOTTOM slab on top of the -1.0 full block; simulate real placement finalization.
        w.setBlock(upper, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, upper, w.getBlockState(upper));
        double upperDy = SlabSupport.getYOffset(w, upper, w.getBlockState(upper));
        if (Math.abs(upperDy + 0.5) > EPS) {
            throw helper.assertionException(upperRel,
                    "a BOTTOM slab on a -1.0 compound full block should render -0.5, got " + upperDy);
        }
        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, upper, w.getBlockState(upper))) {
            throw helper.assertionException(upperRel,
                    "Finding 1: a BOTTOM slab on a lowered (-1.0) full-block support must persist as a lowered "
                            + "carrier (the OLD < 0.0d gate accepted a -1.0 support; the L8 == -0.5 sub-lane narrowed it)");
        }

        // Break the -1.0 support column; the upper slab must NOT pop back to flush.
        w.setBlock(fb, Blocks.AIR.defaultBlockState(), 2);
        double upperDyAfter = SlabSupport.getYOffset(w, upper, w.getBlockState(upper));
        if (Math.abs(upperDyAfter + 0.5) > EPS) {
            throw helper.assertionException(upperRel,
                    "never-pop violation: slab on a -1.0 support popped from -0.5 to " + upperDyAfter
                            + " after the support was broken");
        }
        helper.succeed();
    }

    // REGRESSION GUARD: a flat (never-lowered) slab resting on another flat slab must never gain a
    // spurious anchor, nor live-derive a lowered dy.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatSlabOnFlatSlabNeverAnchors(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos lowerRel = new BlockPos(2, 1, 2);
        BlockPos upperRel = lowerRel.above();
        BlockPos lower = helper.absolutePos(lowerRel);
        BlockPos upper = helper.absolutePos(upperRel);

        w.setBlock(lower, topSlab(), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, lower, w.getBlockState(lower));
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, lower, w.getBlockState(lower))) {
            throw helper.assertionException(lowerRel, "setup: a flat TOP slab on the ground must not anchor");
        }

        w.setBlock(upper, topSlab(), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, upper, w.getBlockState(upper));
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, upper, w.getBlockState(upper))) {
            throw helper.assertionException(upperRel,
                    "regression: a slab resting on a FLAT (non-lowered) slab must not spuriously anchor");
        }
        double upperDy = SlabSupport.getYOffset(w, upper, w.getBlockState(upper));
        if (Math.abs(upperDy) > EPS) {
            throw helper.assertionException(upperRel,
                    "regression: a slab on a flat slab must not live-derive lowered, got " + upperDy);
        }
        helper.succeed();
    }

    // REGRESSION GUARD: a slab resting on a BOTTOM-type support (whose top face sits at the vanilla
    // y=0.5 plane regardless of its own lowering) must not anchor from the vertical lane, and must
    // not live-derive lowered — only TOP/DOUBLE supports propagate their recessed top upward
    // (matches isLoweredTopLikeSlabCarrier's BOTTOM exclusion).
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnBottomTypeSupportNeverAnchorsVertically(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos supportRel = new BlockPos(2, 1, 2);
        BlockPos upperRel = supportRel.above();
        BlockPos support = helper.absolutePos(supportRel);
        BlockPos upper = helper.absolutePos(upperRel);

        w.setBlock(support, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(upper, topSlab(), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, upper, w.getBlockState(upper));
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, upper, w.getBlockState(upper))) {
            throw helper.assertionException(upperRel,
                    "regression: a slab resting on a BOTTOM-type (non-sunk) support must not anchor vertically");
        }
        double upperDy = SlabSupport.getYOffset(w, upper, w.getBlockState(upper));
        if (Math.abs(upperDy) > EPS) {
            throw helper.assertionException(upperRel,
                    "regression: a slab on a BOTTOM-type support must not live-derive lowered, got " + upperDy);
        }
        helper.succeed();
    }
}
