package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.util.SlabSupport;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * L11 — the {@code floorTorchBottomSlabSupportDy} DOUBLE-only + TS-guard gaps (ported from the sibling
 * 1.21.11 branch's shared-helper "failure mode 4" lesson; tracked for this phase in the L8 and
 * L8-corrections HANDOFF notes). {@code SlabSupport.floorTorchBottomSlabSupportDy} is the single shared
 * reader for the lowered dy of the BOTTOM slab SUPPORT a floor object rests directly on — it has
 * ELEVEN consumers (bottom trapdoor / fence-wall-bars support reader / fence gate / oak trapdoor /
 * regular door / standing sign / ordinary full block / special full block / full-block magnitude reader
 * / the getYOffsetInner floor-torch branch / the getYOffsetInner floor-top-contact branch), every one of
 * which treats a finite-negative return as "my support is lowered, follow it down".
 *
 * <p>TWO gaps were fixed together, both proven empirically reachable BEFORE the fix (throwaway probe,
 * deleted after use):
 * <ol>
 *   <li><b>DOUBLE-only slab-below gap (reachable-and-real live fix).</b> The below-support slab lane
 *   matched {@code isLoweredDoubleSlabCarrier} (SlabType.DOUBLE) only — a lowered TOP-type support (its
 *   own recessed top surface, exactly as valid a vertical support) was silently ignored, so an object on
 *   a bottom slab resting on a lowered TOP-type column stayed flush. Probe: for a support that was
 *   {@code isLoweredTopLikeSlabCarrier=true / isLoweredDoubleSlabCarrier=false}, the helper returned 0.0
 *   where it should return -0.5. Fixed by widening to {@code || isLoweredTopLikeSlabCarrier} — the exact
 *   pattern already used in {@code getYOffsetInner} (:2219) by the L8 fix.</li>
 *   <li><b>TS-compat guard gap (reachable-and-real on the SUBJECT lane).</b> The below-support lane was
 *   already TS-guarded transitively (both carrier predicates share
 *   {@code isTsExcludedFromVerticalSupport}), but the SUBJECT-slab lanes
 *   ({@code isPersistentLoweredBottomSlabCarrierNonRecursive} / {@code isAdjacentSideSlabLowered} / the
 *   compound markers) had NO guard: a Terrain-Slabs-owned bottom slab that Slabbed had persisted as a
 *   lowered carrier still reported -0.5 (probe: withOverride stayed -0.5), so an object on it would be
 *   double-offset onto TS's own self-positioned surface (the "smoosh" family). Fixed by adding the shared
 *   {@code isTsExcludedFromVerticalSupport(state)} choke point as the first substantive check — reusing
 *   the one guard the L8-corrections phase (5304e4b3) built, not a new mechanism.</li>
 * </ol>
 *
 * <p>Test seam: Terrain Slabs is not on the gametest classpath, so the TS-guard test forces the
 * "TS-owned" verdict for its {@code terrain_slabs}-namespaced stand-in slab via the injectable
 * {@code CompatHooks.shouldSkipSlabSupportTestOverride} seam (same mechanism the L8-corrections phase
 * established). The DOUBLE-widening test needs no seam — it is a pure vanilla scene.
 */
public final class FloorTorchSupportDyTest {

    private static final double EPS = 1.0e-6;

    private static final Identifier TS_SLAB_ID =
            Identifier.fromNamespaceAndPath("terrain_slabs", "floor_torch_support_guard_test_slab");
    private static final ResourceKey<Block> TS_SLAB_KEY = ResourceKey.create(Registries.BLOCK, TS_SLAB_ID);
    private static final Block TS_SLAB =
            new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB).setId(TS_SLAB_KEY));

    /** Registers the TS-namespaced stand-in slab (before registry freeze, via the main entrypoint). */
    public static final class TerrainSlabsFloorTorchSupportGuardTestEntrypoint implements ModInitializer {
        @Override
        public void onInitialize() {
            if (!BuiltInRegistries.BLOCK.containsKey(TS_SLAB_ID)) {
                Registry.register(BuiltInRegistries.BLOCK, TS_SLAB_ID, TS_SLAB);
            }
        }
    }

    private static double signDy(ServerLevel w, BlockPos abs) {
        return SlabSupport.getYOffset(w, abs, w.getBlockState(abs));
    }

    /**
     * GOES C2 (TEST 18 finding, ledger 2026-07-10): the SLAB torch-comfort OVERLAY is removed. A bottom
     * slab with a lowered floor torch directly above it must return a SLAB-ONLY outline — no torch post
     * unioned into the slab's own selection shape. Pre-C2 the overlay unioned {@code SLABBED$COMFORT_TORCH_
     * SHAPE.move(0, 1+torchDy, 0)} into the slab outline (so DDA produced a slab hit the dead legacy
     * retargeter would bounce to the torch), which under the shipping offset raycast only made the slab
     * STEAL torch clicks and MERGE the two outlines.
     *
     * <p>Scene: a bottom slab and a floor torch above it, both force-stored lowered (-0.5) with the frozen
     * store ON so the removed overlay's precondition (torchDy &lt; 0 directly above a slab) is satisfied.
     * The slab's own lowered outline tops out at world-relative Y=0.0 (slab maxY 0.5 shifted by -0.5); the
     * removed torch overlay would have pushed the outline up past Y=1.0. Asserting the outline stays at or
     * below the slab's own top proves the overlay is gone.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabUnderLoweredTorchReturnsSlabOnlyOutline(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos torch = slab.above();
        w.setBlock(slab.below(), Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(slab, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(torch, Blocks.TORCH.defaultBlockState(), 2);
        SlabAnchorAttachment.writePlacementDy(w, slab, -0.5);
        SlabAnchorAttachment.writePlacementDy(w, torch, -0.5);

        boolean prev = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            if (SlabSupport.getYOffset(w, torch, w.getBlockState(torch)) >= 0.0) {
                throw helper.assertionException(helper.relativePos(torch),
                        "setup: the floor torch above the slab must read lowered (< 0)");
            }
            VoxelShape outline = w.getBlockState(slab).getShape(w, slab, CollisionContext.empty());
            double maxY = outline.max(Direction.Axis.Y);
            // slab lowered -0.5 -> its own outline maxY = 0.5 + (-0.5) = 0.0; the REMOVED torch overlay would
            // have unioned a post reaching ~Y=1.125. Anything above the slab's own top means the overlay leaked.
            if (maxY > 0.5 + EPS) {
                throw helper.assertionException(helper.relativePos(slab),
                        "GOES C2 (TEST 18): a slab under a lowered floor torch must return a SLAB-ONLY outline; "
                                + "outline maxY=" + maxY + " (the removed torch comfort overlay leaked back in)");
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = prev;
        }
        helper.succeed();
    }

    /**
     * THE FIX (DOUBLE-only widening): a standing sign (one of the eleven floorTorchBottomSlabSupportDy
     * consumers) resting on a bottom slab that itself rests on a lowered TOP-type carrier must follow the
     * lowered support down. The discriminating support is a TOP slab persisted lowered via the L8
     * vertical lane (DOUBLE support -> TOP slab on it) — {@code isLoweredTopLikeSlabCarrier=true} but
     * {@code isLoweredDoubleSlabCarrier=false}, so ONLY the widening reaches it. Reverting the widening
     * ({@code || isLoweredTopLikeSlabCarrier}) makes the sign read flush (0.0) — the RED for this fix.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void signOnBottomSlabOnLoweredTopCarrierFollowsSupportDown(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos groundSlab = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos doubleSupport = helper.absolutePos(new BlockPos(3, 3, 2));
        BlockPos topOnDouble = helper.absolutePos(new BlockPos(3, 4, 2)); // lowered TOP carrier (top-like, not double)
        BlockPos supportSlab = helper.absolutePos(new BlockPos(3, 5, 2)); // bottom slab the sign sits on
        BlockPos sign = helper.absolutePos(new BlockPos(3, 6, 2));

        w.setBlock(groundSlab, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(loweredBottom, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));
        w.setBlock(doubleSupport, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, doubleSupport, w.getBlockState(doubleSupport));
        w.setBlock(topOnDouble, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, topOnDouble, w.getBlockState(topOnDouble));

        // The discriminating support: top-like carrier, NOT a double carrier.
        BlockState topState = w.getBlockState(topOnDouble);
        if (!SlabSupport.isLoweredTopLikeSlabCarrier(w, topOnDouble, topState)) {
            throw helper.assertionException(helper.relativePos(topOnDouble),
                    "setup: the lowered TOP support must be recognised as a top-like carrier");
        }
        if (SlabSupport.isLoweredDoubleSlabCarrier(w, topOnDouble, topState)) {
            throw helper.assertionException(helper.relativePos(topOnDouble),
                    "setup: the discriminating support must NOT be a double carrier (else the DOUBLE branch masks the widening)");
        }

        // Bottom slab the sign sits on, resting on the lowered TOP carrier.
        w.setBlock(supportSlab, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(sign, Blocks.OAK_SIGN.defaultBlockState(), 2);
        if (!w.getBlockState(sign).is(Blocks.OAK_SIGN)) {
            throw helper.assertionException(helper.relativePos(sign), "setup: the standing sign did not survive");
        }

        double dy = signDy(w, sign);
        // supportSlab helper -> -0.5 (via the widening); sign contact dy = supportDy - 0.5 = -1.0.
        if (Math.abs(dy + 1.0) > EPS) {
            throw helper.assertionException(helper.relativePos(sign),
                    "THE FIX (DOUBLE-only widening): a sign on a bottom slab resting on a lowered TOP-type carrier "
                            + "must follow the support down (expected -1.0), got " + dy
                            + " — the DOUBLE-only slab-below check ignored the lowered TOP support");
        }
        helper.succeed();
    }

    /**
     * REGRESSION CONTROL (the already-correct DOUBLE case): the same object on a bottom slab resting on a
     * lowered DOUBLE carrier already followed the support down before the widening; it must still do so.
     * Guards against the widening accidentally narrowing the pre-existing DOUBLE path.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void signOnBottomSlabOnLoweredDoubleCarrierStillFollowsSupportDown(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos groundSlab = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos doubleSupport = helper.absolutePos(new BlockPos(3, 3, 2));
        BlockPos supportSlab = helper.absolutePos(new BlockPos(3, 4, 2)); // bottom slab the sign sits on
        BlockPos sign = helper.absolutePos(new BlockPos(3, 5, 2));

        w.setBlock(groundSlab, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(loweredBottom, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));
        w.setBlock(doubleSupport, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, doubleSupport, w.getBlockState(doubleSupport));
        if (!SlabSupport.isLoweredDoubleSlabCarrier(w, doubleSupport, w.getBlockState(doubleSupport))) {
            throw helper.assertionException(helper.relativePos(doubleSupport),
                    "setup: the lowered DOUBLE support must be recognised as a double carrier");
        }

        w.setBlock(supportSlab, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(sign, Blocks.OAK_SIGN.defaultBlockState(), 2);

        double dy = signDy(w, sign);
        if (Math.abs(dy + 1.0) > EPS) {
            throw helper.assertionException(helper.relativePos(sign),
                    "regression: a sign on a bottom slab resting on a lowered DOUBLE carrier must still follow the "
                            + "support down (expected -1.0), got " + dy);
        }
        helper.succeed();
    }

    /**
     * THE FIX (TS-compat guard on the SUBJECT lane): when the bottom slab an object rests on is
     * Terrain-Slabs-owned, {@code floorTorchBottomSlabSupportDy} must NOT report it as a lowered Slabbed
     * support — TS positions its own surface, so adding Slabbed's -0.5 double-offsets the object into it.
     * The subject slab is persisted lowered via the side-lane (empirically the reachable path that
     * bypassed the below-support guard); with the TS override marking it TS-owned, the object must read
     * flush (0.0). Reverting the {@code isTsExcludedFromVerticalSupport(state)} early return makes the
     * object read -1.0 (support -0.5 + object -0.5) — the RED for this guard.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabsOwnedSubjectSupportIsNotReportedLowered(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos groundSlab = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos subject = helper.absolutePos(new BlockPos(3, 3, 2)); // TS-owned bottom slab, persisted lowered
        BlockPos sign = helper.absolutePos(new BlockPos(3, 4, 2));

        w.setBlock(groundSlab, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(loweredBottom, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));

        // Subject: a TS-namespaced BOTTOM slab beside the lowered bottom — persisted lowered via side-lane.
        w.setBlock(subject, TS_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, subject, w.getBlockState(subject));
        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, subject, w.getBlockState(subject))) {
            throw helper.assertionException(helper.relativePos(subject),
                    "setup: the TS-namespaced subject bottom slab must persist as a lowered carrier (the reachable "
                            + "path that bypassed the below-support guard)");
        }

        w.setBlock(sign, Blocks.OAK_SIGN.defaultBlockState(), 2);

        CompatHooks.shouldSkipSlabSupportTestOverride = st ->
                "terrain_slabs".equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace());
        try {
            double dy = signDy(w, sign);
            if (Math.abs(dy) > EPS) {
                throw helper.assertionException(helper.relativePos(sign),
                        "THE FIX (TS subject guard): an object on a Terrain-Slabs-owned bottom slab must NOT be "
                                + "double-offset onto TS's own surface (expected flush 0.0), got " + dy);
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }

    /**
     * ANTI-JAM REGRESSION GUARD: a genuine vanilla lowered bottom slab support must STILL lower an object
     * resting on it even while the TS-exclusion override is active (it only excludes terrain_slabs blocks).
     * Proves the guard is namespace-scoped, not a blanket disable of the whole helper.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaSubjectSupportStillLowersObjectWhileTsOverrideActive(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos groundSlab = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos subject = helper.absolutePos(new BlockPos(3, 3, 2)); // vanilla birch bottom slab, persisted lowered
        BlockPos sign = helper.absolutePos(new BlockPos(3, 4, 2));

        w.setBlock(groundSlab, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(loweredBottom, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));
        w.setBlock(subject, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, subject, w.getBlockState(subject));
        w.setBlock(sign, Blocks.OAK_SIGN.defaultBlockState(), 2);

        // Sanity: without any override, the vanilla support lowers the sign to -1.0.
        double base = signDy(w, sign);
        if (Math.abs(base + 1.0) > EPS) {
            throw helper.assertionException(helper.relativePos(sign),
                    "setup: a sign on a vanilla persisted-lowered bottom slab should read -1.0, got " + base);
        }

        CompatHooks.shouldSkipSlabSupportTestOverride = st ->
                "terrain_slabs".equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace());
        try {
            double dy = signDy(w, sign);
            if (Math.abs(dy + 1.0) > EPS) {
                throw helper.assertionException(helper.relativePos(sign),
                        "ANTI-JAM: a genuine vanilla lowered support must STILL lower the object while the TS-exclusion "
                                + "override is active (expected -1.0), got " + dy + " — the guard must be namespace-scoped");
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }
}
