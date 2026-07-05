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
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * L11-broader (task #25) — Terrain-Slabs-exclusion guard on the PASS-THROUGH / candidate gate of the
 * {@link SlabSupport} {@code hasLoweredSlabLaneSupport} BFS (a slab lane walked via
 * {@code isAdjacentSideSlabLowered} / {@code slabLoweringIsSideInheritedOnly}), closing the third BFS
 * path {@code 6a3f2859} claimed but did NOT actually touch.
 *
 * <p><b>The distinction from the two prior TS-guard commits.</b>
 * <ul>
 *   <li>{@code 6a3f2859} guarded the SUBJECT slab: {@code isAdjacentSideSlabLowered} returns false for a
 *   TS subject BEFORE the BFS is even entered (and {@code loweredSlabMagnitude} returns NaN for a TS
 *   neighbour). That commit's audit conflated "the slab-lane inner walk" with a different, simpler
 *   single-hop function — the actual BFS with that shape, {@code hasLoweredSlabLaneSupport}, was never
 *   touched.</li>
 *   <li>{@code 08dd9291} guarded the far-end lane OWNER: a TS slab can no longer live-qualify or read as a
 *   {@code isPersistentLoweredSlabCarrier} in its own right, so the BFS's owner-return gate at
 *   {@code isLoweredSlabLaneOwnerForSideInheritance} rejects a TS owner.</li>
 * </ul>
 * NEITHER guarded a TS slab sitting in the MIDDLE of a chain of slabs as a pure CONDUIT. The BFS's
 * propagate/candidate gate ({@code isCompatibleLoweredSlabLane(BlockState, BlockState)}) was a pure
 * {@link SlabType} compatibility check with no namespace exclusion, so a 3-slab horizontal run —
 * <em>vanilla subject | TS conduit | genuine vanilla lane owner</em> — let the lowered-lane status
 * propagate ACROSS the TS slab from the far end to the vanilla subject on the near end. Terrain Slabs
 * owns its own visual positioning and is not part of Slabbed's lowered-lane semantics at all, so a TS
 * slab must TERMINATE the lane walk (break the chain), not be treated as a valid pass-through link.
 *
 * <p><b>Measured (throwaway probe driving the real {@code slabLoweringIsSideInheritedOnly} path under the
 * seam, deleted after use).</b> Before the fix the vanilla subject read {@code sideInherited=true} with
 * the TS override ON — the same as with it OFF — the TS conduit did NOT break the chain (the
 * double-offset "smoosh" family). After: {@code true} with the override OFF (a real vanilla lane),
 * {@code false} with it ON (the TS conduit now terminates the lane). An all-vanilla 3-slab chain still
 * propagates in BOTH states (anti-jam: the guard is namespace-scoped, not a blanket disable).
 *
 * <p><b>The fix</b> folds {@code isTsExcludedFromVerticalSupport} into that ONE private
 * {@code isCompatibleLoweredSlabLane(BlockState, BlockState)} overload (the sole consumer being this
 * BFS — both its enqueue gate and its subject-vs-cursor return gate route through it), reusing the one
 * shared {@code CompatHooks.shouldSkipSlabSupport} choke point. Excluding either endpoint terminates
 * that specific link WITHOUT poisoning the whole walk, so a legitimate all-vanilla parallel lane is
 * still found. No-op without Terrain Slabs.
 *
 * <p>Test seam: Terrain Slabs is not on the gametest classpath, so the TS-owned verdict is forced for
 * the {@code terrain_slabs}-namespaced stand-in conduit slab via the injectable
 * {@code CompatHooks.shouldSkipSlabSupportTestOverride} seam.
 */
public final class SlabLaneConduitTerrainSlabsGuardTest {

    private static final Identifier TS_SLAB_ID =
            Identifier.fromNamespaceAndPath("terrain_slabs", "slab_lane_conduit_guard_test_slab");
    private static final ResourceKey<Block> TS_SLAB_KEY = ResourceKey.create(Registries.BLOCK, TS_SLAB_ID);
    private static final Block TS_SLAB =
            new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB).setId(TS_SLAB_KEY));

    /** Registers the TS-namespaced stand-in conduit slab (before registry freeze, via the main entrypoint). */
    public static final class TerrainSlabsSlabLaneConduitGuardTestEntrypoint implements ModInitializer {
        @Override
        public void onInitialize() {
            if (!BuiltInRegistries.BLOCK.containsKey(TS_SLAB_ID)) {
                Registry.register(BuiltInRegistries.BLOCK, TS_SLAB_ID, TS_SLAB);
            }
        }
    }

    private static void ts(GameTestHelper helper) {
        CompatHooks.shouldSkipSlabSupportTestOverride = st ->
                "terrain_slabs".equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace());
    }

    private static boolean sideInherited(ServerLevel w, BlockPos p) {
        return SlabSupport.slabLoweringIsSideInheritedOnly(w, p, w.getBlockState(p));
    }

    /**
     * Builds a genuine vanilla persistent lowered-slab carrier ("lane owner") at {@code owner}, made a
     * side-lane carrier by a vanilla lowered bottom slab (anchored-dirt backed) one hop to its WEST — the
     * proven-persisting structure from {@link CantileverBfsTerrainSlabsGuardTest}. The owner sits on its
     * own solid ground (not over air). Asserts the owner really persisted as a carrier (the reachable far
     * end the BFS must terminate on).
     */
    private static void buildVanillaLaneOwner(GameTestHelper helper, BlockPos owner) {
        ServerLevel w = helper.getLevel();
        BlockPos west = owner.west();
        BlockPos backingSlab = west;                 // vanilla lowered bottom slab beside the owner
        BlockPos backingDirt = west.below();          // anchored full block under it
        BlockPos backingGround = west.below().below(); // slab ground so the backer's below-stack is solid
        BlockPos ownerGround = owner.below();          // solid ground under the owner

        w.setBlock(backingGround, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(backingDirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, backingDirt, w.getBlockState(backingDirt));
        w.setBlock(backingSlab, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, backingSlab, w.getBlockState(backingSlab));

        w.setBlock(ownerGround, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(owner, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, owner, w.getBlockState(owner));

        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, owner, w.getBlockState(owner))) {
            throw helper.assertionException(helper.relativePos(owner),
                    "setup: the vanilla lane-owner slab must persist as a lowered carrier (the reachable far "
                            + "end the BFS terminates on) — otherwise the scene proves nothing");
        }
    }

    /**
     * THE FIX. A 3-slab horizontal run — vanilla subject | TS conduit | vanilla lane owner — must NOT let
     * the lowered lane propagate across the TS conduit onto the subject when the TS-owned verdict is
     * active. RED witness: without the override the subject legitimately side-inherits (proving the lane
     * really reaches it). GREEN: with the override the TS conduit breaks the chain and the subject reads
     * flush (no side inheritance).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabSubjectDoesNotInheritLoweredLaneThroughTerrainSlabsConduit(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();

        BlockPos owner = helper.absolutePos(new BlockPos(3, 5, 3)); // genuine vanilla lane owner (far end)
        BlockPos conduit = helper.absolutePos(new BlockPos(4, 5, 3)); // TS conduit (middle)
        BlockPos subject = helper.absolutePos(new BlockPos(5, 5, 3)); // vanilla subject (near end)
        BlockPos conduitGround = helper.absolutePos(new BlockPos(4, 4, 3));
        BlockPos subjectGround = helper.absolutePos(new BlockPos(5, 4, 3));

        buildVanillaLaneOwner(helper, owner);
        w.setBlock(conduitGround, Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(conduit, TS_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(subjectGround, Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(subject, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);

        // Control: the TS conduit must NOT itself be a carrier (else the scene would prove owner-return, not
        // pass-through). 08dd9291 already guarantees this; assert it so the pass-through claim is isolated.
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, conduit, w.getBlockState(conduit))) {
            throw helper.assertionException(helper.relativePos(conduit),
                    "setup: the TS conduit must not be a lowered carrier in its own right (this scene isolates "
                            + "the PASS-THROUGH gate, not the owner-return gate)");
        }

        if (!sideInherited(w, subject)) {
            throw helper.assertionException(helper.relativePos(subject),
                    "setup (RED witness): WITHOUT the TS override the vanilla subject must side-inherit the lowered "
                            + "lane through the middle slab (proving the BFS lane reaches it), got false");
        }
        ts(helper);
        try {
            if (sideInherited(w, subject)) {
                throw helper.assertionException(helper.relativePos(subject),
                        "THE FIX (hasLoweredSlabLaneSupport pass-through gate): a Terrain-Slabs-owned slab in the "
                                + "MIDDLE of a slab chain must TERMINATE the lowered-lane walk, not conduct the lane "
                                + "across it onto the vanilla subject (expected no side inheritance), got true");
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }

    /**
     * ANTI-JAM. Identical geometry but the MIDDLE slab is VANILLA (not TS): the all-vanilla 3-slab chain
     * must STILL propagate the lowered lane onto the subject, even while the TS-exclusion override is
     * active — the guard is namespace-scoped, not a blanket disable of the BFS.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabSubjectStillInheritsLoweredLaneThroughVanillaMidSlabWhileTsOverrideActive(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();

        BlockPos owner = helper.absolutePos(new BlockPos(3, 5, 3));
        BlockPos mid = helper.absolutePos(new BlockPos(4, 5, 3)); // VANILLA mid slab
        BlockPos subject = helper.absolutePos(new BlockPos(5, 5, 3));
        BlockPos midGround = helper.absolutePos(new BlockPos(4, 4, 3));
        BlockPos subjectGround = helper.absolutePos(new BlockPos(5, 4, 3));

        buildVanillaLaneOwner(helper, owner);
        w.setBlock(midGround, Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(mid, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(subjectGround, Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(subject, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);

        ts(helper);
        try {
            if (!sideInherited(w, subject)) {
                throw helper.assertionException(helper.relativePos(subject),
                        "ANTI-JAM: an all-vanilla 3-slab chain (owner | vanilla mid | subject) must STILL propagate "
                                + "the lowered lane onto the subject while the TS-exclusion override is active "
                                + "(expected side inheritance), got false — the guard must be namespace-scoped, not a "
                                + "blanket disable of the lane walk");
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }
}
