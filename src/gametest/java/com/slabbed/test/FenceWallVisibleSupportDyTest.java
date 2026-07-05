package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.util.SlabSupport;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * L11-sibling — the {@code beta35FenceWallVisibleSupportDy} DOUBLE-only + TS-guard gaps. The campaign's
 * own sweeper, reviewing the just-landed L11 fix ({@code 68088bc6}, which widened
 * {@code floorTorchBottomSlabSupportDy}), found the identical two-gap pattern UNFIXED in the structurally
 * parallel {@code beta35FenceWallVisibleSupportDy} — the shared support-dy reader for the
 * fence / wall / iron-bars family AND the floor-button contact lane (both call it directly or via
 * {@code beta35FenceWallVariantContactDy} / {@code beta35FloorButtonContactDy}).
 *
 * <p>Consumer graph of {@code beta35FenceWallVisibleSupportDy} (2 direct callers, sweep-verified):
 * {@code beta35FenceWallVariantContactDy} (fence/wall/iron-bars) and {@code beta35FloorButtonContactDy}
 * (floor button). Those feed six downstream sites ({@code loweredSupportedConnectingMagnitude},
 * {@code isBeta35LoweredTrapdoorOrFloorButtonVisibleOwnerTarget}, and four {@code getYOffsetInner}
 * branches), every one of which gates on {@code Double.isFinite(...) && ... < -1.0e-6} — with the sole
 * magnitude threshold ({@code getYOffsetInner}'s stacked-fence {@code < -0.5 - 1e-6}) NOT tripped by the
 * new {@code -0.5} TOP case, so a TOP carrier is handled identically to the already-correct DOUBLE case.
 *
 * <p>TWO gaps, both proven empirically reachable BEFORE the fix (throwaway probe driving a real floor
 * button through {@code getYOffset}, deleted after use):
 * <ol>
 *   <li><b>DOUBLE-only slab-below gap (reachable-and-real live fix).</b> The below-support slab lane
 *   ({@code :525}) matched {@code isLoweredDoubleSlabCarrier} (SlabType.DOUBLE) only — a lowered TOP-type
 *   support was silently ignored, so a fence/wall/bars/floor-button on a TOP/DOUBLE support resting on a
 *   lowered TOP-type column stayed flush. Probe: a floor button read {@code 0.0} where it should read
 *   {@code -0.5}. Widened to {@code || isLoweredTopLikeSlabCarrier}, the exact pattern L11 already used
 *   in {@code floorTorchBottomSlabSupportDy} and {@code getYOffsetInner} (:2219).</li>
 *   <li><b>TS-compat guard gap (reachable-and-real, end-to-end).</b> The BOTTOM-support lane already
 *   delegates to the TS-guarded {@code floorTorchBottomSlabSupportDy}, and the below-support slab lane
 *   already inherits the shared {@code isTsExcludedFromVerticalSupport} guard (both carrier predicates
 *   carry it), but the non-bottom SUBJECT-slab lanes ({@code isAdjacentSideSlabLowered} / the compound
 *   markers) had NO guard. Probe: a Terrain-Slabs-owned DOUBLE support persisted lowered via the side-lane
 *   made a floor button read {@code -0.5} EVEN WITH the TS override active (double-offset onto TS's own
 *   self-positioned surface — the "smoosh" family), because {@code beta35FloorButtonContactDy} pre-empts
 *   the already-guarded generic column path. Fixed by adding the shared
 *   {@code isTsExcludedFromVerticalSupport(state)} choke point as the first substantive check — reusing
 *   the one guard, not a new mechanism. No-op when Terrain Slabs is absent.</li>
 * </ol>
 *
 * <p>Test seam: Terrain Slabs is not on the gametest classpath, so the TS-guard test forces the
 * "TS-owned" verdict for its {@code terrain_slabs}-namespaced stand-in slab via the injectable
 * {@code CompatHooks.shouldSkipSlabSupportTestOverride} seam (same mechanism L11 established).
 */
public final class FenceWallVisibleSupportDyTest {

    private static final double EPS = 1.0e-6;

    private static final Identifier TS_SLAB_ID =
            Identifier.fromNamespaceAndPath("terrain_slabs", "fence_wall_support_guard_test_slab");
    private static final ResourceKey<Block> TS_SLAB_KEY = ResourceKey.create(Registries.BLOCK, TS_SLAB_ID);
    private static final Block TS_SLAB =
            new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB).setId(TS_SLAB_KEY));

    /** Registers the TS-namespaced stand-in slab (before registry freeze, via the main entrypoint). */
    public static final class TerrainSlabsFenceWallSupportGuardTestEntrypoint implements ModInitializer {
        @Override
        public void onInitialize() {
            if (!BuiltInRegistries.BLOCK.containsKey(TS_SLAB_ID)) {
                Registry.register(BuiltInRegistries.BLOCK, TS_SLAB_ID, TS_SLAB);
            }
        }
    }

    private static BlockState floorButton() {
        return Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
    }

    private static double buttonDy(ServerLevel w, BlockPos abs) {
        return SlabSupport.getYOffset(w, abs, w.getBlockState(abs));
    }

    /**
     * THE FIX (DOUBLE-only widening): a floor button (a live {@code beta35FenceWallVisibleSupportDy}
     * consumer via {@code beta35FloorButtonContactDy}) resting on a DOUBLE-type support slab that itself
     * rests on a lowered TOP-type carrier must follow the lowered support down. The discriminating carrier
     * below is a TOP slab persisted lowered via the L8 vertical lane (DOUBLE carrier -> TOP slab on it) —
     * {@code isLoweredTopLikeSlabCarrier=true} but {@code isLoweredDoubleSlabCarrier=false}, so ONLY the
     * widening reaches it. Reverting the widening ({@code || isLoweredTopLikeSlabCarrier}) makes the button
     * read flush (0.0) — the RED for this fix.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floorButtonOnDoubleSupportOnLoweredTopCarrierFollowsSupportDown(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos groundSlab = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos doubleCarrier = helper.absolutePos(new BlockPos(3, 3, 2));
        BlockPos topCarrier = helper.absolutePos(new BlockPos(3, 4, 2));   // lowered TOP carrier (top-like, NOT double)
        BlockPos supportSlab = helper.absolutePos(new BlockPos(3, 5, 2));  // DOUBLE support the button sits on
        BlockPos button = helper.absolutePos(new BlockPos(3, 6, 2));

        w.setBlock(groundSlab, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(loweredBottom, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));
        w.setBlock(doubleCarrier, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, doubleCarrier, w.getBlockState(doubleCarrier));
        w.setBlock(topCarrier, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, topCarrier, w.getBlockState(topCarrier));

        // The discriminating carrier below the support: top-like, NOT a double carrier.
        BlockState topState = w.getBlockState(topCarrier);
        if (!SlabSupport.isLoweredTopLikeSlabCarrier(w, topCarrier, topState)) {
            throw helper.assertionException(helper.relativePos(topCarrier),
                    "setup: the lowered TOP carrier must be recognised as a top-like carrier");
        }
        if (SlabSupport.isLoweredDoubleSlabCarrier(w, topCarrier, topState)) {
            throw helper.assertionException(helper.relativePos(topCarrier),
                    "setup: the discriminating carrier must NOT be a double carrier (else the DOUBLE branch masks the widening)");
        }

        // DOUBLE support the button sits on, resting on the lowered TOP carrier.
        w.setBlock(supportSlab, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 2);
        w.setBlock(button, floorButton(), 2);
        if (!w.getBlockState(button).is(Blocks.STONE_BUTTON)) {
            throw helper.assertionException(helper.relativePos(button), "setup: the floor button did not survive");
        }

        // support helper -> -0.5 (via the widening); button contact dy = supportDy + getSupportYOffset(DOUBLE=1.0) - 1.0 = -0.5.
        double dy = buttonDy(w, button);
        if (Math.abs(dy + 0.5) > EPS) {
            throw helper.assertionException(helper.relativePos(button),
                    "THE FIX (DOUBLE-only widening): a floor button on a DOUBLE support resting on a lowered TOP-type "
                            + "carrier must follow the support down (expected -0.5), got " + dy
                            + " — the DOUBLE-only slab-below check ignored the lowered TOP carrier");
        }
        helper.succeed();
    }

    /**
     * REGRESSION CONTROL (the already-correct DOUBLE case): the same floor button on a DOUBLE support
     * resting on a lowered DOUBLE carrier already followed the support down before the widening; it must
     * still do so. Guards against the widening accidentally narrowing the pre-existing DOUBLE path.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floorButtonOnDoubleSupportOnLoweredDoubleCarrierStillFollowsSupportDown(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos groundSlab = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos doubleCarrier = helper.absolutePos(new BlockPos(3, 3, 2));  // lowered DOUBLE carrier
        BlockPos supportSlab = helper.absolutePos(new BlockPos(3, 4, 2));    // DOUBLE support the button sits on
        BlockPos button = helper.absolutePos(new BlockPos(3, 5, 2));

        w.setBlock(groundSlab, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(loweredBottom, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));
        w.setBlock(doubleCarrier, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, doubleCarrier, w.getBlockState(doubleCarrier));
        if (!SlabSupport.isLoweredDoubleSlabCarrier(w, doubleCarrier, w.getBlockState(doubleCarrier))) {
            throw helper.assertionException(helper.relativePos(doubleCarrier),
                    "setup: the lowered DOUBLE carrier must be recognised as a double carrier");
        }

        w.setBlock(supportSlab, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 2);
        w.setBlock(button, floorButton(), 2);

        double dy = buttonDy(w, button);
        if (Math.abs(dy + 0.5) > EPS) {
            throw helper.assertionException(helper.relativePos(button),
                    "regression: a floor button on a DOUBLE support resting on a lowered DOUBLE carrier must still "
                            + "follow the support down (expected -0.5), got " + dy);
        }
        helper.succeed();
    }

    /**
     * THE FIX (TS-compat guard on the SUBJECT lane): when the DOUBLE/TOP support a floor button rests on is
     * Terrain-Slabs-owned, {@code beta35FenceWallVisibleSupportDy} must NOT report it as a lowered Slabbed
     * support — TS positions its own surface, so adding Slabbed's -0.5 double-offsets the button into it.
     * The subject support is a TS-owned DOUBLE slab persisted lowered via the side-lane (empirically the
     * reachable path: {@code beta35FloorButtonContactDy} pre-empts the already-guarded generic column
     * path). With the TS override marking the support TS-owned, the button must read flush (0.0). Reverting
     * the {@code isTsExcludedFromVerticalSupport(state)} early return makes the button read -0.5 EVEN under
     * the override (the smoosh) — the RED for this guard.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabsOwnedSupportIsNotReportedLowered(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos groundSlab = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos subject = helper.absolutePos(new BlockPos(3, 3, 2));   // TS-owned DOUBLE support, side-lane lowered
        BlockPos button = helper.absolutePos(new BlockPos(3, 4, 2));

        w.setBlock(groundSlab, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(loweredBottom, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));

        // Subject: a TS-namespaced DOUBLE slab beside the lowered bottom — persisted lowered via the side-lane
        // (the reachable non-bottom subject-lane path that bypassed the below-support guard).
        w.setBlock(subject, TS_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, subject, w.getBlockState(subject));
        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, subject, w.getBlockState(subject))) {
            throw helper.assertionException(helper.relativePos(subject),
                    "setup: the TS-namespaced subject DOUBLE slab must persist as a lowered carrier (the reachable "
                            + "path that bypassed the below-support guard)");
        }

        w.setBlock(button, floorButton(), 2);
        if (!w.getBlockState(button).is(Blocks.STONE_BUTTON)) {
            throw helper.assertionException(helper.relativePos(button), "setup: the floor button did not survive");
        }

        // Sanity: WITHOUT any override, the vanilla-recognised lowered support lowers the button to -0.5.
        double base = buttonDy(w, button);
        if (Math.abs(base + 0.5) > EPS) {
            throw helper.assertionException(helper.relativePos(button),
                    "setup: without the TS override the lowered support should lower the button to -0.5, got " + base);
        }

        CompatHooks.shouldSkipSlabSupportTestOverride = st ->
                "terrain_slabs".equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace());
        try {
            double dy = buttonDy(w, button);
            if (Math.abs(dy) > EPS) {
                throw helper.assertionException(helper.relativePos(button),
                        "THE FIX (TS subject guard): a floor button on a Terrain-Slabs-owned lowered support must NOT be "
                                + "double-offset onto TS's own surface (expected flush 0.0), got " + dy);
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }

    /**
     * ANTI-JAM REGRESSION GUARD: a genuine vanilla lowered DOUBLE support must STILL lower a floor button
     * resting on it even while the TS-exclusion override is active (it only excludes terrain_slabs blocks).
     * Proves the guard is namespace-scoped, not a blanket disable of the whole helper.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaSupportStillLowersButtonWhileTsOverrideActive(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos groundSlab = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos subject = helper.absolutePos(new BlockPos(3, 3, 2));   // vanilla birch DOUBLE support, side-lane lowered
        BlockPos button = helper.absolutePos(new BlockPos(3, 4, 2));

        w.setBlock(groundSlab, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(loweredBottom, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));
        w.setBlock(subject, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, subject, w.getBlockState(subject));
        w.setBlock(button, floorButton(), 2);

        double base = buttonDy(w, button);
        if (Math.abs(base + 0.5) > EPS) {
            throw helper.assertionException(helper.relativePos(button),
                    "setup: a floor button on a vanilla persisted-lowered DOUBLE support should read -0.5, got " + base);
        }

        CompatHooks.shouldSkipSlabSupportTestOverride = st ->
                "terrain_slabs".equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace());
        try {
            double dy = buttonDy(w, button);
            if (Math.abs(dy + 0.5) > EPS) {
                throw helper.assertionException(helper.relativePos(button),
                        "ANTI-JAM: a genuine vanilla lowered support must STILL lower the button while the TS-exclusion "
                                + "override is active (expected -0.5), got " + dy + " — the guard must be namespace-scoped");
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }
}
