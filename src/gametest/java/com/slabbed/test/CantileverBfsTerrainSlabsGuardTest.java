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
 * L11-broader (task #24) — Terrain-Slabs-exclusion guard on the three nested {@code ArrayDeque} BFS
 * traversals in {@link SlabSupport}, the lowered-support-classifying paths distinct from the simple
 * column walk ({@code hasSlabInColumn} / {@code slabColumnYOffset}, which were already TS-guarded and
 * terminate FLUSH at a TS block).
 *
 * <p>The three BFS paths (all {@code MAX_CHAIN_DEPTH}-bounded) each propagate a lowered magnitude
 * through a cantilever run and TERMINATE on a lowered-source predicate applied to a horizontal NEIGHBOR:
 * <ol>
 *   <li>{@code isCantileverLoweredFullBlock} — a cantilevered solid full block over air; reached from
 *   {@code getYOffsetInner} and from {@code adjacentLoweredSideMagnitude}. Terminates on a neighbour
 *   slab via {@code isAdjacentSideSlabLowered}.</li>
 *   <li>{@code cantileverLoweredConnectingMagnitude} — a cantilevered fence / wall / iron-bars over air.
 *   Terminates on a neighbour slab via {@code isAdjacentSideSlabLowered}.</li>
 *   <li>{@code adjacentLoweredSideMagnitude} (the slab lane) — a slab cantilevered over air beside a
 *   lowered neighbour; carries the neighbour's magnitude via {@code loweredSlabMagnitude}.</li>
 * </ol>
 *
 * <p><b>THE GAP (found empirically, throwaway probe driving the real {@code getYOffset} path for a
 * fence, a full block, and a slab — deleted after use).</b> Unlike the column walk, none of the three
 * BFS paths did a {@code shouldSkipSlabSupport} check on the neighbour before classifying it: they
 * called {@code isAdjacentSideSlabLowered} / {@code loweredSlabMagnitude} directly. Those two shared
 * classifiers carried NO namespace guard on the SUBJECT slab — a Terrain-Slabs-owned slab that
 * acquired a lowered-carrier marker (or sat side-lane beside a vanilla lowered slab) was reported as a
 * lowered Slabbed source, so a fence / full block / slab cantilevered beside it read {@code -0.5} EVEN
 * with the TS-owned verdict active — double-offsetting the piece onto TS's own self-positioned surface
 * (the "smoosh" family). Measured before the fix: all three subjects read {@code -0.5} with the TS
 * override ON; after: all three read flush {@code 0.0}.
 *
 * <p><b>THE FIX.</b> Fold the invariant into the two shared choke points both used by every BFS path
 * (reusing the one {@code isTsExcludedFromVerticalSupport} guard, no new mechanism, no-op without TS):
 * <ul>
 *   <li>{@code isAdjacentSideSlabLowered} — the shared "is this SUBJECT slab lowered by a Slabbed
 *   lane" predicate (17 callers); returns false first for a TS-owned slab. NARROWING is uniformly
 *   correct — no consumer ever wants a TS-positioned surface treated as a Slabbed lowering source.
 *   Callers that already pre-guard (the column walk) are unaffected (the TS block never reaches it
 *   there).</li>
 *   <li>{@code loweredSlabMagnitude} — the RC2 slab-neighbour magnitude reader; returns NaN
 *   ("not a lowered source") first for a TS-owned slab, closing the residual {@code isAnchored} /
 *   {@code isPersistentLoweredBottomSlabCarrierNonRecursive} vectors the {@code isAdjacentSideSlabLowered}
 *   guard alone did not cover (they are read directly, not via that predicate).</li>
 * </ul>
 *
 * <p>Test seam: Terrain Slabs is not on the gametest classpath, so the TS-owned verdict is forced for
 * the {@code terrain_slabs}-namespaced stand-in slab via the injectable
 * {@code CompatHooks.shouldSkipSlabSupportTestOverride} seam (the mechanism L11 / L8-corrections
 * established). Each FIX scene proves the RED under the override (the subject would read {@code -0.5}
 * if the guard were reverted) and the GREEN with it; a paired ANTI-JAM scene proves the guard is
 * namespace-scoped (a genuine vanilla lowered source still lowers the subject while the override is
 * active).
 */
public final class CantileverBfsTerrainSlabsGuardTest {

    private static final double EPS = 1.0e-6;

    private static final Identifier TS_SLAB_ID =
            Identifier.fromNamespaceAndPath("terrain_slabs", "cantilever_bfs_guard_test_slab");
    private static final ResourceKey<Block> TS_SLAB_KEY = ResourceKey.create(Registries.BLOCK, TS_SLAB_ID);
    private static final Block TS_SLAB =
            new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB).setId(TS_SLAB_KEY));

    /** Registers the TS-namespaced stand-in slab (before registry freeze, via the main entrypoint). */
    public static final class TerrainSlabsCantileverBfsGuardTestEntrypoint implements ModInitializer {
        @Override
        public void onInitialize() {
            if (!BuiltInRegistries.BLOCK.containsKey(TS_SLAB_ID)) {
                Registry.register(BuiltInRegistries.BLOCK, TS_SLAB_ID, TS_SLAB);
            }
        }
    }

    private static double dyOf(ServerLevel w, BlockPos p) {
        return SlabSupport.getYOffset(w, p, w.getBlockState(p));
    }

    private static void ts(GameTestHelper helper) {
        CompatHooks.shouldSkipSlabSupportTestOverride = st ->
                "terrain_slabs".equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace());
    }

    /**
     * Common scene: a lowered slab carrier over solid support, cantilevered OVER AIR so the cantilever
     * candidates (which air-gate {@code pos.below()}) can be its horizontal neighbours. When {@code tsOwned}
     * the carrier is the {@code terrain_slabs}-namespaced stand-in slab; otherwise a vanilla birch slab.
     * Returns the carrier's position (subjects are placed one hop away by the caller). The carrier becomes
     * side-lane lowered by sitting directly beside a vanilla lowered bottom slab (anchored-dirt backed).
     */
    private static BlockPos buildLoweredCarrierOverAir(GameTestHelper helper, boolean tsOwned) {
        ServerLevel w = helper.getLevel();
        BlockPos groundSlab = helper.absolutePos(new BlockPos(2, 3, 3));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 4, 3));
        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 5, 3)); // vanilla side-lane owner
        BlockPos carrierGround = helper.absolutePos(new BlockPos(3, 4, 3));
        BlockPos carrier = helper.absolutePos(new BlockPos(3, 5, 3));       // side-lane lowered carrier (subject source)

        w.setBlock(groundSlab, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(loweredBottom, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));

        w.setBlock(carrierGround, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        BlockState carrierState = (tsOwned ? TS_SLAB : Blocks.BIRCH_SLAB)
                .defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        w.setBlock(carrier, carrierState, 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, carrier, w.getBlockState(carrier));

        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, carrier, w.getBlockState(carrier))) {
            throw helper.assertionException(helper.relativePos(carrier),
                    "setup: the " + (tsOwned ? "TS-owned" : "vanilla") + " carrier slab must persist as a lowered "
                            + "carrier (the reachable side-lane source the BFS terminates on)");
        }
        return carrier;
    }

    // ── BFS 2: cantilevered fence/wall/iron-bars (cantileverLoweredConnectingMagnitude) ──────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceCantileverBesideTerrainSlabsCarrierStaysFlush(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos carrier = buildLoweredCarrierOverAir(helper, true);
        BlockPos fence = carrier.east(); // over air, adjacent to the carrier

        w.setBlock(fence, Blocks.OAK_FENCE.defaultBlockState(), 2);
        double base = dyOf(w, fence);
        if (Math.abs(base + 0.5) > EPS) {
            throw helper.assertionException(helper.relativePos(fence),
                    "setup (RED witness): WITHOUT the TS override a fence cantilevered beside the lowered carrier "
                            + "must read -0.5 (proving the BFS reaches this source), got " + base);
        }
        ts(helper);
        try {
            double dy = dyOf(w, fence);
            if (Math.abs(dy) > EPS) {
                throw helper.assertionException(helper.relativePos(fence),
                        "THE FIX (BFS2, cantileverLoweredConnectingMagnitude): a fence cantilevered beside a "
                                + "Terrain-Slabs-owned carrier must NOT smoosh onto TS's own surface (expected flush 0.0), got "
                                + dy);
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceCantileverBesideVanillaCarrierStillLowersWhileTsOverrideActive(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos carrier = buildLoweredCarrierOverAir(helper, false);
        BlockPos fence = carrier.east();

        w.setBlock(fence, Blocks.OAK_FENCE.defaultBlockState(), 2);
        ts(helper);
        try {
            double dy = dyOf(w, fence);
            if (Math.abs(dy + 0.5) > EPS) {
                throw helper.assertionException(helper.relativePos(fence),
                        "ANTI-JAM (BFS2): a fence cantilevered beside a genuine VANILLA lowered carrier must STILL "
                                + "lower -0.5 while the TS-exclusion override is active (expected -0.5), got " + dy
                                + " — the guard must be namespace-scoped, not a blanket disable of the BFS");
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }

    // ── BFS 1: cantilevered solid full block (isCantileverLoweredFullBlock via adjacentLoweredSideMagnitude) ──

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockCantileverBesideTerrainSlabsCarrierStaysFlush(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos carrier = buildLoweredCarrierOverAir(helper, true);
        BlockPos fullBlock = carrier.north(); // over air, adjacent to the carrier

        w.setBlock(fullBlock, Blocks.STONE.defaultBlockState(), 2);
        double base = dyOf(w, fullBlock);
        if (Math.abs(base + 0.5) > EPS) {
            throw helper.assertionException(helper.relativePos(fullBlock),
                    "setup (RED witness): WITHOUT the TS override a full block cantilevered beside the lowered carrier "
                            + "must read -0.5 (proving the BFS/side lane reaches this source), got " + base);
        }
        ts(helper);
        try {
            double dy = dyOf(w, fullBlock);
            if (Math.abs(dy) > EPS) {
                throw helper.assertionException(helper.relativePos(fullBlock),
                        "THE FIX (BFS1, isCantileverLoweredFullBlock / adjacentLoweredSideMagnitude): a full block "
                                + "cantilevered beside a Terrain-Slabs-owned carrier must NOT smoosh onto TS's own surface "
                                + "(expected flush 0.0), got " + dy);
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockCantileverBesideVanillaCarrierStillLowersWhileTsOverrideActive(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos carrier = buildLoweredCarrierOverAir(helper, false);
        BlockPos fullBlock = carrier.north();

        w.setBlock(fullBlock, Blocks.STONE.defaultBlockState(), 2);
        ts(helper);
        try {
            double dy = dyOf(w, fullBlock);
            if (Math.abs(dy + 0.5) > EPS) {
                throw helper.assertionException(helper.relativePos(fullBlock),
                        "ANTI-JAM (BFS1): a full block cantilevered beside a genuine VANILLA lowered carrier must STILL "
                                + "lower -0.5 while the TS-exclusion override is active (expected -0.5), got " + dy);
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }

    // ── slab lane: cantilevered slab (adjacentLoweredSideMagnitude -> loweredSlabMagnitude) ──────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabCantileverBesideTerrainSlabsCarrierStaysFlush(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos carrier = buildLoweredCarrierOverAir(helper, true);
        BlockPos slab = carrier.south(); // over air, adjacent to the carrier

        w.setBlock(slab, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        double base = dyOf(w, slab);
        if (Math.abs(base + 0.5) > EPS) {
            throw helper.assertionException(helper.relativePos(slab),
                    "setup (RED witness): WITHOUT the TS override a slab cantilevered beside the lowered carrier "
                            + "must read -0.5 (proving loweredSlabMagnitude reaches this source), got " + base);
        }
        ts(helper);
        try {
            double dy = dyOf(w, slab);
            if (Math.abs(dy) > EPS) {
                throw helper.assertionException(helper.relativePos(slab),
                        "THE FIX (slab lane, loweredSlabMagnitude): a slab cantilevered beside a Terrain-Slabs-owned "
                                + "carrier must NOT smoosh onto TS's own surface (expected flush 0.0), got " + dy);
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabCantileverBesideVanillaCarrierStillLowersWhileTsOverrideActive(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos carrier = buildLoweredCarrierOverAir(helper, false);
        BlockPos slab = carrier.south();

        w.setBlock(slab, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        ts(helper);
        try {
            double dy = dyOf(w, slab);
            if (Math.abs(dy + 0.5) > EPS) {
                throw helper.assertionException(helper.relativePos(slab),
                        "ANTI-JAM (slab lane): a slab cantilevered beside a genuine VANILLA lowered carrier must STILL "
                                + "lower -0.5 while the TS-exclusion override is active (expected -0.5), got " + dy);
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }
}
