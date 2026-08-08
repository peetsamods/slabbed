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
 * L11 (final individual-fix phase) — the {@code SlabSupport.isBottomSlabLoweredByCarrierBelow}
 * DOUBLE-only slab-below gap: the last unfixed sibling of the recurring pattern already corrected in
 * {@code floorTorchBottomSlabSupportDy} (68088bc6), {@code beta35FenceWallVisibleSupportDy} (c7a19048),
 * and {@code getYOffsetInner}'s own slab branch (f70eec96). The predicate's {@code backedByLoweredCarrier}
 * check matched {@code isLoweredDoubleSlabCarrier} (SlabType.DOUBLE) ONLY when the block directly below is
 * a slab — a lowered TOP-type support (its own recessed top surface, just as valid a vertical support to
 * inherit from) was silently ignored, so a genuinely-lowered bottom slab resting on a lowered TOP-type
 * carrier was NOT recognised as "backed by a lowered carrier". Widened to
 * {@code || isLoweredTopLikeSlabCarrier}, the exact pattern the other three siblings already use. Both
 * carrier predicates share the {@code isTsExcludedFromVerticalSupport} guard, so the widening cannot leak
 * a TS-owned support.
 *
 * <p><b>Caller context / blast radius (why this tests the predicate DIRECTLY):</b> the predicate's ONE
 * real caller, {@code SlabAnchorAttachment.qualifiesAsSideAdjacentLoweredFullAnchorSource}, is reached
 * only from {@code qualifiesForSideAdjacentLoweredFullAnchor}, which begins with an unconditional
 * {@code if (true) return false;} — the side-adjacent-lowered-full-block anchor lane is intentionally
 * DISABLED (the maintainer's "no sideways inheritance" law, port of 1.21.1 83afed84; guarded by
 * {@code Slabbed2612LoweringContractTest#sideAdjacentFullBlockMustNotInheritLowering}). So this widening
 * changes NO live authoring behaviour today; it is cross-port hygiene so the DOUBLE-only regression is not
 * reintroduced if that lane is ever re-enabled, and so the predicate is self-consistent with its three
 * already-fixed siblings. Because the production consumer is dead code, the regression is pinned by
 * asserting the predicate's own return value on the discriminating scene rather than an observable dy.
 *
 * <p>The discriminating scene (probe {@code PROBE-BSLCB}, deleted after use): a persisted-lowered BOTTOM
 * slab (so its own {@code getYOffset} is already -0.5, satisfying the predicate's second conjunct) resting
 * on a lowered TOP-type carrier that is {@code isLoweredTopLikeSlabCarrier=true /
 * isLoweredDoubleSlabCarrier=false}. Before the fix the predicate returned {@code false}; after, {@code true}.
 */
public final class BottomSlabLoweredByCarrierBelowTest {

    /**
     * Builds the shared lowered-TOP-carrier column and returns the persisted-lowered BOTTOM subject slab
     * resting directly on that TOP carrier. Mirrors {@code FloorTorchSupportDyTest}'s discriminating scene.
     * Verifies the carrier below the subject is top-like-but-not-double, and that the subject's own
     * getYOffset is -0.5 (the predicate's second conjunct), so the ONLY thing the predicate's first
     * conjunct can turn on is the DOUBLE-vs-TOP widening.
     */
    private static BlockPos buildSubjectOnTopCarrier(GameTestHelper helper, ServerLevel w) {
        BlockPos groundSlab = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos doubleSupport = helper.absolutePos(new BlockPos(3, 3, 2));
        BlockPos topOnDouble = helper.absolutePos(new BlockPos(3, 4, 2)); // lowered TOP carrier (top-like, not double)
        BlockPos subject = helper.absolutePos(new BlockPos(3, 5, 2)); // BOTTOM slab resting on the lowered TOP carrier

        w.setBlock(groundSlab, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(loweredBottom, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));
        w.setBlock(doubleSupport, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, doubleSupport, w.getBlockState(doubleSupport));
        w.setBlock(topOnDouble, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, topOnDouble, w.getBlockState(topOnDouble));

        BlockState topState = w.getBlockState(topOnDouble);
        if (!SlabSupport.isLoweredTopLikeSlabCarrier(w, topOnDouble, topState)) {
            throw helper.assertionException(helper.relativePos(topOnDouble),
                    "setup: the lowered TOP carrier below the subject must be recognised as a top-like carrier");
        }
        if (SlabSupport.isLoweredDoubleSlabCarrier(w, topOnDouble, topState)) {
            throw helper.assertionException(helper.relativePos(topOnDouble),
                    "setup: the carrier below the subject must NOT be a double carrier "
                            + "(else the DOUBLE branch masks the widening)");
        }

        w.setBlock(subject, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, subject, w.getBlockState(subject));
        BlockState subjState = w.getBlockState(subject);
        double subjDy = SlabSupport.getYOffset(w, subject, subjState);
        if (Math.abs(subjDy + 0.5) > 1.0e-6) {
            throw helper.assertionException(helper.relativePos(subject),
                    "setup: the subject bottom slab must itself already read -0.5 (the predicate's second "
                            + "conjunct getYOffset==-0.5), got " + subjDy);
        }
        return subject;
    }

    /**
     * THE FIX: a persisted-lowered BOTTOM slab resting on a lowered TOP-type carrier is "backed by a
     * lowered carrier". Reverting the {@code || isLoweredTopLikeSlabCarrier} widening makes the predicate
     * return {@code false} (the DOUBLE-only slab-below check ignores the lowered TOP support) — the RED.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bottomSlabOnLoweredTopCarrierIsBackedByLoweredCarrier(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos subject = buildSubjectOnTopCarrier(helper, w);
        if (!SlabSupport.isBottomSlabLoweredByCarrierBelow(w, subject, w.getBlockState(subject))) {
            throw helper.assertionException(helper.relativePos(subject),
                    "THE FIX (DOUBLE-only widening): a lowered bottom slab resting on a lowered TOP-type carrier "
                            + "must be recognised as backed by a lowered carrier — the DOUBLE-only slab-below check "
                            + "ignored the lowered TOP support");
        }
        helper.succeed();
    }

    /**
     * REGRESSION CONTROL (the already-correct DOUBLE case): a persisted-lowered BOTTOM slab resting on a
     * lowered DOUBLE carrier was recognised before the widening and must still be. Guards against the
     * widening accidentally narrowing the pre-existing DOUBLE path.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bottomSlabOnLoweredDoubleCarrierStillBackedByLoweredCarrier(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos groundSlab = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos doubleSupport = helper.absolutePos(new BlockPos(3, 3, 2)); // lowered DOUBLE carrier
        BlockPos subject = helper.absolutePos(new BlockPos(3, 4, 2)); // BOTTOM slab resting on the lowered DOUBLE carrier

        w.setBlock(groundSlab, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(loweredBottom, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));
        w.setBlock(doubleSupport, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, doubleSupport, w.getBlockState(doubleSupport));
        if (!SlabSupport.isLoweredDoubleSlabCarrier(w, doubleSupport, w.getBlockState(doubleSupport))) {
            throw helper.assertionException(helper.relativePos(doubleSupport),
                    "setup: the lowered DOUBLE carrier must be recognised as a double carrier");
        }

        w.setBlock(subject, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, subject, w.getBlockState(subject));
        if (!SlabSupport.isBottomSlabLoweredByCarrierBelow(w, subject, w.getBlockState(subject))) {
            throw helper.assertionException(helper.relativePos(subject),
                    "regression: a lowered bottom slab resting on a lowered DOUBLE carrier must still be "
                            + "recognised as backed by a lowered carrier");
        }
        helper.succeed();
    }

    /**
     * NEGATIVE CONTROL: a persisted-lowered BOTTOM slab resting on a NON-lowered plain slab below is NOT
     * "backed by a lowered carrier" — neither {@code isLoweredDoubleSlabCarrier} nor the new
     * {@code isLoweredTopLikeSlabCarrier} recognises an un-lowered support, so the widening must not make
     * the predicate spuriously true. Proves the widening only reaches genuinely-lowered TOP carriers.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bottomSlabOnUnloweredCarrierIsNotBackedByLoweredCarrier(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos plainTop = helper.absolutePos(new BlockPos(2, 2, 2)); // a plain, un-lowered TOP slab
        BlockPos subject = helper.absolutePos(new BlockPos(2, 3, 2)); // BOTTOM slab resting on it, persisted lowered

        // A plain TOP slab that is NOT a lowered carrier (no anchor, no adjacent lowered lane).
        w.setBlock(plainTop, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        BlockState plainState = w.getBlockState(plainTop);
        if (SlabSupport.isLoweredTopLikeSlabCarrier(w, plainTop, plainState)
                || SlabSupport.isLoweredDoubleSlabCarrier(w, plainTop, plainState)) {
            throw helper.assertionException(helper.relativePos(plainTop),
                    "setup: the plain TOP slab below must NOT be a lowered carrier of either kind");
        }

        w.setBlock(subject, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, subject, w.getBlockState(subject));
        if (SlabSupport.isBottomSlabLoweredByCarrierBelow(w, subject, w.getBlockState(subject))) {
            throw helper.assertionException(helper.relativePos(subject),
                    "negative control: a bottom slab resting on a NON-lowered support must NOT be reported as "
                            + "backed by a lowered carrier — the widening must only reach genuinely-lowered TOP carriers");
        }
        helper.succeed();
    }
}
