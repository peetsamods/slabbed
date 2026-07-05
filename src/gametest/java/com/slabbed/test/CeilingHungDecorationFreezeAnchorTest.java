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
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * VERIFIED-HARMLESS PIN (Phase 5, sweeper agent {@code a0557ec561eab297f}'s L10 residual finding).
 *
 * <p><b>The reported gap:</b> {@code SlabAnchorAttachment.freezeLoweredOnPlace}'s {@code dy < -1e-6}
 * branch calls {@code addAnchorUnchecked} with NO exclusion for the always-ceiling-hung decoration
 * family ({@code isAlwaysCeilingHungDecoration}: hanging roots, spore blossom, hanging sign). The one
 * carve-out that exists there, {@code SlabSupport.slabLoweringIsSideInheritedOnly}, is gated to
 * {@code instanceof SlabBlock}, so it gives zero protection to a non-slab decoration. Therefore a
 * ceiling-hung decoration placed under a genuinely LOWERED (non-flush) support DOES acquire a
 * persistent {@code ANCHOR_TYPE} marker through the real placement hook — the pre-existing
 * {@code DecorativeObjectSupportAnchorTest.hangingLanternNeverAnchorsViaFreeze} guard only covered the
 * FLUSH case ({@code dy=0}, the branch is never entered), so nothing pinned this LOWERED combination.
 *
 * <p><b>Empirically measured (throwaway probe, built + run + deleted 2026-07-05) for all three
 * decorations, identical results — spore blossom / hanging roots / hanging sign under an anchored
 * {@code -0.5} stone support with air below (the
 * {@code Slabbed2612LoweringContractTest.hangingRootsFollowLoweredSupportAbove} scene):</b>
 * <pre>
 *   supportDy=-0.5  dyBeforeFreeze=-0.5  anchoredAfterFreeze=TRUE  dyAfterFreeze=-0.5
 *   nativeRaycastEmpty=false  solidRender=false  dyAfterSupportBreak=0.0
 * </pre>
 *
 * <p><b>Why the spurious anchor is provably HARMLESS (both {@code isAnchored} consumers ruled out):</b>
 * <ol>
 *   <li><b>Rendered dy is a dead read.</b> {@code getYOffsetInner} dispatches the ceiling-hung family
 *       (line ~2119) BEFORE any anchor branch, so dy is a pure function of the support ABOVE and never
 *       consults the marker. Proven by {@code dyAfterSupportBreak=0.0}: after the lowered support is
 *       removed the decoration correctly re-reads flush — the stale anchor does NOT freeze it lowered.</li>
 *   <li><b>Server/shared raycast-basis consumer ruled out.</b> The single behavioral {@code isAnchored}
 *       read in {@code SlabSupportStateMixin} lives in {@code slabbed$needsLoweredFullBlockRaycastBasis},
 *       which returns false unless the block's native interaction shape is EMPTY. Ceiling-hung
 *       decorations have a non-empty interaction shape ({@code nativeRaycastEmpty=false}), so the
 *       {@code isAnchored} read is never reached for them.</li>
 *   <li><b>Client crosshair-retarget consumers ruled out.</b> Every BEHAVIORAL {@code isAnchored} read
 *       in {@code GameRendererCrosshairRetargetMixin} (the two shared predicates
 *       {@code slabbed$isAnchoredLoweredFullBlock} / {@code slabbed$isAnchoredOrLoweredFullBlock} and
 *       the raw {@code slabbed$anchoredFbCandidateHitAt} / {@code slabbed$anchoredFbCandidateAt} sites)
 *       first requires {@code state.isSolidRender()}; the rest are diagnostic string builders with no
 *       effect. Ceiling-hung decorations are not solid-render ({@code solidRender=false}), so no
 *       targeting decision acts on the marker.</li>
 * </ol>
 *
 * <p>Because both consumers are structurally guarded and the render read is dead, the marker changes
 * NOTHING today. Adding an {@code isAlwaysCeilingHungDecoration} exclusion would be pure dead code
 * guarding against consumers that are already guarded — so this is pinned as harmless (the
 * {@code 41ce9f8a}-style precedent) rather than "fixed". This test asserts the ACTUAL measured state
 * so a regression is caught in EITHER direction:
 * <ul>
 *   <li>if a future change makes a ceiling-hung decoration solid-render or gives it an empty native
 *       raycast, its guard assertion here goes RED — signalling the marker has become LIVE and now
 *       genuinely needs the exclusion;</li>
 *   <li>if the {@code getYOffsetInner} ceiling-hung dispatch is reordered so the dead anchor starts
 *       holding dy lowered after the support is gone, {@code dyAfterSupportBreak} goes RED.</li>
 * </ul>
 */
public final class CeilingHungDecorationFreezeAnchorTest {

    private static final double EPS = 1.0e-6;

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /**
     * Builds the genuinely-lowered scene, drives the REAL placement hook, and pins the measured
     * anchored-but-harmless state and both consumer guards.
     */
    private void assertAnchorsButIsHarmless(GameTestHelper helper, String label, BlockState decoState) {
        ServerLevel w = helper.getLevel();

        // An ANCHORED stone support at -0.5 with AIR below it, so the decoration can hang in the cell
        // directly below and genuinely follow the lowered underside (the reference lowered-support scene).
        BlockPos support = new BlockPos(2, 3, 2);
        BlockPos supportAbs = helper.absolutePos(support);
        w.setBlock(helper.absolutePos(support.below()), bottomSlab(), 2);            // temp carrier
        w.setBlock(supportAbs, Blocks.STONE.defaultBlockState(), 2);                  // stone -> -0.5
        SlabAnchorAttachment.addAnchor(w, supportAbs, w.getBlockState(supportAbs));
        w.setBlock(helper.absolutePos(support.below()), Blocks.AIR.defaultBlockState(), 2); // anchor holds -0.5
        double supportDy = SlabSupport.getYOffset(w, supportAbs, w.getBlockState(supportAbs));
        if (Math.abs(supportDy + 0.5) > EPS) {
            throw helper.assertionException(support,
                    "SETUP: anchored stone support must stay lowered -0.5 with air below, got " + supportDy);
        }

        // Place the decoration in the cell below the lowered support, then fire the REAL placement
        // finalization hook (Block#setPlacedBy HEAD delegates to freezeLoweredOnPlace exactly like this).
        BlockPos decoRel = support.below();
        BlockPos deco = helper.absolutePos(decoRel);
        w.setBlock(deco, decoState, 2);
        BlockState placed = w.getBlockState(deco);
        if (placed.getBlock() != decoState.getBlock()) {
            throw helper.assertionException(decoRel,
                    "SETUP: " + label + " must stay placed in the cell below the lowered support (got "
                            + placed.getBlock() + ")");
        }
        double dyBefore = SlabSupport.getYOffset(w, deco, placed);
        if (Math.abs(dyBefore + 0.5) > EPS) {
            throw helper.assertionException(decoRel,
                    "SETUP: " + label + " under a -0.5 support must render -0.5 (ceiling-hung follow), got "
                            + dyBefore);
        }

        SlabAnchorAttachment.freezeLoweredOnPlace(w, deco, placed);

        // PIN 1 — the known-spurious marker: freezeLoweredOnPlace's unchecked dy<0 branch DOES anchor a
        // ceiling-hung decoration (no isAlwaysCeilingHungDecoration carve-out). Documented, not a bug.
        if (!SlabAnchorAttachment.isAnchored(w, deco)) {
            throw helper.assertionException(decoRel,
                    "PIN CHANGED: " + label + " no longer anchors via freezeLoweredOnPlace's unchecked dy<0 "
                            + "branch. If an isAlwaysCeilingHungDecoration exclusion was added, this pin should be "
                            + "re-scoped to assert NOT-anchored instead — re-read the class doc.");
        }

        // PIN 2 — the marker is a DEAD read for rendered dy: the ceiling-hung dispatch in getYOffsetInner
        // runs before any anchor branch, so the correct dy is produced regardless of the anchor.
        double dyAfterFreeze = SlabSupport.getYOffset(w, deco, w.getBlockState(deco));
        if (Math.abs(dyAfterFreeze + 0.5) > EPS) {
            throw helper.assertionException(decoRel,
                    "HARMLESS-DY: " + label + " must still render -0.5 after the freeze anchor (dy is a pure "
                            + "function of the support above, not the anchor), got " + dyAfterFreeze);
        }

        // PIN 3 — CONSUMER GUARD A (client crosshair retarget): every behavioral isAnchored read there is
        // gated on isSolidRender(). A ceiling-hung decoration is NOT solid-render, so none of them act on
        // the marker. If this flips to true the marker becomes live for targeting -> add the exclusion.
        if (placed.isSolidRender()) {
            throw helper.assertionException(decoRel,
                    "CONSUMER GUARD A BROKEN: " + label + " is now solidRender=true, so the client crosshair "
                            + "retarget mixin's isAnchored reads would ACT on the spurious anchor. The "
                            + "freezeLoweredOnPlace exclusion for isAlwaysCeilingHungDecoration is now REQUIRED.");
        }

        // PIN 4 — CONSUMER GUARD B (server/shared raycast-basis): SlabSupportStateMixin's lone behavioral
        // isAnchored read (slabbed$needsLoweredFullBlockRaycastBasis) only fires when the native
        // interaction shape is EMPTY. A ceiling-hung decoration's is non-empty, so the read is never
        // reached. If this shape becomes empty the marker becomes live -> add the exclusion.
        VoxelShape nativeRaycast = placed.getInteractionShape(w, deco);
        boolean nativeRaycastEmpty = nativeRaycast == null || nativeRaycast.isEmpty();
        if (nativeRaycastEmpty) {
            throw helper.assertionException(decoRel,
                    "CONSUMER GUARD B BROKEN: " + label + " now has an EMPTY native interaction shape, so "
                            + "slabbed$needsLoweredFullBlockRaycastBasis's isAnchored read would ACT on the "
                            + "spurious anchor. The freezeLoweredOnPlace exclusion is now REQUIRED.");
        }

        // PIN 5 — the anchor genuinely does nothing: break the lowered support and confirm the decoration
        // re-reads FLUSH (the anchor does NOT sticky-hold it lowered — proving the marker is inert). This
        // is the behavioral opposite of a real full-block anchor, which WOULD hold -0.5 across the break.
        w.setBlock(supportAbs, Blocks.AIR.defaultBlockState(), 2);
        SlabAnchorAttachment.removeAnchor(w, supportAbs);
        double dyAfterSupportBreak = SlabSupport.getYOffset(w, deco, w.getBlockState(deco));
        if (Math.abs(dyAfterSupportBreak) > EPS) {
            throw helper.assertionException(decoRel,
                    "MARKER NO LONGER INERT: " + label + " read " + dyAfterSupportBreak + " (not 0.0) after its "
                            + "support was removed — the spurious freeze anchor is now sticky-holding it lowered "
                            + "via some anchor-consulting dy path, which is a real 'pops/sticks' bug. Investigate "
                            + "and add the isAlwaysCeilingHungDecoration exclusion to freezeLoweredOnPlace.");
        }

        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sporeBlossomUnderLoweredSupportAnchorsButHarmless(GameTestHelper helper) {
        assertAnchorsButIsHarmless(helper, "spore_blossom", Blocks.SPORE_BLOSSOM.defaultBlockState());
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingRootsUnderLoweredSupportAnchorsButHarmless(GameTestHelper helper) {
        assertAnchorsButIsHarmless(helper, "hanging_roots", Blocks.HANGING_ROOTS.defaultBlockState());
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingSignUnderLoweredSupportAnchorsButHarmless(GameTestHelper helper) {
        assertAnchorsButIsHarmless(helper, "hanging_sign", Blocks.OAK_HANGING_SIGN.defaultBlockState());
    }
}
