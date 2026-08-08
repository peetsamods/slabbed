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
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Sweeper follow-up to {@code ba80d735} (block-entity cantilever inherit, hopper/chest snap). That
 * commit's TS-compat question — "does {@code adjacentLoweredBlockEntityMagnitude} need its own
 * {@code CompatHooks.shouldSkipSlabSupport} guard, or is it transitively covered?" — was answered
 * empirically and correctly (transitively covered, no new guard needed) but only via a throwaway
 * probe that was deleted, unlike every other TS-adjacent fix this campaign, each of which left a
 * PERMANENT regression test using the {@code shouldSkipSlabSupportTestOverride} seam (see
 * {@link CantileverBfsTerrainSlabsGuardTest}, {@link SlabVerticalSupportTerrainSlabsGuardTest},
 * {@link SlabLaneConduitTerrainSlabsGuardTest}). This class closes that gap permanently.
 *
 * <p><b>Why no new guard was needed (traced, not assumed).</b>
 * {@code adjacentLoweredBlockEntityMagnitude} (in {@code SlabSupport}) recognises a lowered
 * block-entity NEIGHBOUR (e.g. a chest) via:
 * <pre>
 *     SlabAnchorAttachment.isAnchored(world, neighborPos)
 *         || (shouldOffset(world, neighborPos, neighbor)
 *                 &amp;&amp; slabColumnYOffset(world, neighborPos) &lt; -1.0e-6d)
 * </pre>
 * and also falls through to {@code adjacentLoweredSideMagnitude} (the full-block/slab source lane)
 * on every BFS cursor. Both routes terminate FLUSH at a Terrain-Slabs-owned block already:
 * {@code slabColumnYOffset} returns {@code 0.0} the moment the column walk meets a block for which
 * {@code CompatHooks.shouldSkipSlabSupport} is true, and {@code adjacentLoweredSideMagnitude} routes
 * through {@code isAdjacentSideSlabLowered} / {@code loweredSlabMagnitude}. So a chest lowered ONLY by
 * a TS-owned slab column is not a "genuinely lowered" source by either route, and a hopper
 * cantilevered beside it must stay flush. {@code SlabAnchorAttachment.isAnchored} itself carries no TS
 * check at all — it is a persisted placement-decision marker, correctly TS-independent (an anchored
 * neighbour SHOULD still be read as lowered even with TS active, per the ba80d735 commit message).
 * (The precise guard-count/identity claim for each route is corrected below — read that note, not
 * just this paragraph, before relying on "two choke points" or "three choke points" as a count.)
 *
 * <p>Each scene below builds a chest that is lowered ONLY via the live TS-owned column (never
 * anchored), so the neighbour-recognition test exercises exactly the {@code slabColumnYOffset} route
 * above, not the anchor route. A hopper is then cantilevered beside it (air below, no slab of its
 * own — matching {@link BlockEntityCantileverTest}'s "over air" scene shape) and must read flush
 * {@code 0.0} while the TS override is active. A paired ANTI-JAM scene proves the guard is
 * namespace-scoped: a hopper beside a chest lowered by a genuine VANILLA column must still inherit
 * {@code -0.5} while the same override is active.
 *
 * <p><b>Mutation-proof note — CORRECTED 2026-07-05 by a fresh empirical re-run (see the internal notes /
 * SLABBED_SPINE.md for the full mutation table; this replaces the original "three independent choke
 * points" claim, which underspecified the picture in both directions).</b> A campaign sweeper
 * reviewing this class's original commit ({@code 6ca3f050}) traced a FOURTH candidate choke point by
 * hand — {@code SlabAnchorAttachment.isPersistentLoweredSlabCarrier}, gated by its own
 * {@code isPersistentLoweredSlabCarrierState}'s {@code !CompatHooks.shouldSkipSlabSupport(state)}
 * clause (the {@code 08dd9291} fix) — and asked whether the original "disable all 3 named guards"
 * mutation-testing narrative still holds given that 4th guard. A fresh, from-scratch mutation sweep
 * (every single guard alone, then every pair, then the original trio, then all four) measured the
 * following for {@code hopperBesideTerrainSlabsCarrierSlabStaysFlush} (the slab-carrier route):
 * <ul>
 *   <li>The original claim IS reproducible as stated: disabling exactly the three named guards —
 *       {@code loweredSlabMagnitude}'s check, {@code isAdjacentSideSlabLowered}'s check, and the
 *       {@code isCompatibleLoweredSlabLane} conduit guard inside {@code hasLoweredSlabLaneSupport} —
 *       together (and no smaller subset of those three) reliably produces RED. The 4th guard
 *       ({@code isPersistentLoweredSlabCarrierState}) does NOT need to be additionally disabled for
 *       this to break; adding it on top of the trio changes nothing for this scene (it only adds its
 *       own independent failures in {@code TerrainSlabsOwnSlabDyGuardTest}, a different scenario).</li>
 *   <li><b>However, the trio is not the ONLY minimal breaking combination.</b> {@code loweredSlabMagnitude}'s
 *       check ALONE, plus the 4th guard ALONE, together ALSO reliably produce RED — a genuinely
 *       smaller (2-guard) break than the documented 3-guard one. This works because
 *       {@code loweredSlabMagnitude}'s own check is the FIRST thing evaluated for a slab neighbour
 *       (its bypass alone is a no-op, since two alternate reads it falls through to —
 *       {@code isAdjacentSideSlabLowered} and {@code isPersistentLoweredBottomSlabCarrierNonRecursive}
 *       — are each still separately gated by the SAME {@code isPersistentLoweredSlabCarrierState}
 *       check as the 4th guard); disabling that shared state-gate directly removes both fallbacks at
 *       once, without needing the conduit guard at all.</li>
 *   <li>Neither guard alone, nor any other 2-guard pairing not containing
 *       {@code loweredSlabMagnitude}'s own check, breaks this scene (measured: {G1,G2}, {G1,G3},
 *       {G2,G3}, {G2,G4}, {G3,G4} all stay GREEN for this specific test).</li>
 * </ul>
 * <b>Bottom line: this route is protected by 4 distinct guard call sites (not 3, not merely "some
 * redundant subset of 4"), but two of them ({@code isAdjacentSideSlabLowered}'s own check and
 * {@code isPersistentLoweredSlabCarrierState}, reached via {@code isPersistentLoweredSlabCarrier}) key
 * on state that is reachable through more than one path, so more than one minimal disabling
 * combination exists — {loweredSlabMagnitude, isAdjacentSideSlabLowered, conduit-guard} and
 * {loweredSlabMagnitude, isPersistentLoweredSlabCarrierState} are BOTH independently sufficient, and
 * no proper subset of either is.</b> The block-entity-neighbour route
 * ({@code hopperBesideChestOnTerrainSlabsColumnStaysFlush}) is a separate, disjoint guard pair not
 * touched by any of the above: {@code hasSlabInColumn} and {@code slabColumnYOffset}'s OWN
 * {@code shouldSkipSlabSupport} checks (not {@code isTsExcludedFromVerticalSupport}, though it is the
 * same underlying predicate) must BOTH be disabled together — {@code hasSlabInColumn} alone is
 * insufficient because {@code shouldOffset} still resolves {@code true} via its {@code isBottomSlab}
 * fallback (which has no TS guard at all and matches a TS slab, since it extends {@code SlabBlock});
 * {@code slabColumnYOffset} alone is unreachable to matter because {@code shouldOffset}'s own
 * short-circuit (via the SAME unguarded {@code hasSlabInColumn} fallback) never lets execution reach
 * it while {@code hasSlabInColumn} is intact. This pair has no bearing from the slab-carrier route's
 * 4 guards at all (measured: neither guard here shares a choke point with {@code loweredSlabMagnitude}
 * / {@code isAdjacentSideSlabLowered} / the conduit guard / {@code isPersistentLoweredSlabCarrierState}
 * for this exact scene — the chest sits directly on the slab, never reaching
 * {@code isAdjacentSideSlabLowered}'s carrier-marker branch because {@code isBottomSlab} resolves
 * first in both {@code hasSlabInColumn} and {@code slabColumnYOffset}).
 */
public final class BlockEntityCantileverTerrainSlabsGuardTest {

    private static final double EPS = 1.0e-6;

    private static final Identifier TS_SLAB_ID =
            Identifier.fromNamespaceAndPath("terrain_slabs", "block_entity_cantilever_guard_test_slab");
    private static final ResourceKey<Block> TS_SLAB_KEY = ResourceKey.create(Registries.BLOCK, TS_SLAB_ID);
    private static final Block TS_SLAB =
            new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB).setId(TS_SLAB_KEY));

    /** Registers the TS-namespaced stand-in slab (before registry freeze, via the main entrypoint). */
    public static final class TerrainSlabsBlockEntityCantileverGuardTestEntrypoint implements ModInitializer {
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
     * Common scene: a chest sitting directly on a slab (its OWN column source, never anchored —
     * {@code addAnchor}/{@code freezeLoweredOnPlace} are deliberately NOT called, so the ONLY route by
     * which the chest itself could read lowered is the live {@code shouldOffset}/{@code slabColumnYOffset}
     * column walk). When {@code tsOwned} the slab is the {@code terrain_slabs}-namespaced stand-in;
     * otherwise a vanilla oak slab. Returns the chest's position; the hopper subject is placed one hop
     * away by the caller, over air, with no slab of its own (matching {@link BlockEntityCantileverTest}).
     */
    private static BlockPos buildChestOnColumnSourceSlab(GameTestHelper helper, boolean tsOwned) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos chestPos = slab.above();

        var slabState = (tsOwned ? TS_SLAB : Blocks.OAK_SLAB)
                .defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        w.setBlock(slab, slabState, 2);
        w.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2);
        return chestPos;
    }

    // ── neighbour-recognition route: slabColumnYOffset (transitively TS-guarded) ────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hopperBesideChestOnTerrainSlabsColumnStaysFlush(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos chestPos = buildChestOnColumnSourceSlab(helper, true);
        BlockPos hopper = chestPos.east(); // over air, no slab of its own

        w.setBlock(hopper, Blocks.HOPPER.defaultBlockState(), 2);
        double base = dyOf(w, hopper);
        if (Math.abs(base + 0.5) > EPS) {
            throw helper.assertionException(helper.relativePos(hopper),
                    "setup (RED witness): WITHOUT the TS override a hopper cantilevered beside a chest lowered "
                            + "by its own column must read -0.5 (proving adjacentLoweredBlockEntityMagnitude "
                            + "reaches this source), got " + base);
        }

        ts(helper);
        try {
            double dy = dyOf(w, hopper);
            if (Math.abs(dy) > EPS) {
                throw helper.assertionException(helper.relativePos(hopper),
                        "THE GUARD (adjacentLoweredBlockEntityMagnitude, via shouldOffset/slabColumnYOffset): "
                                + "a hopper cantilevered beside a chest lowered ONLY by a Terrain-Slabs-owned "
                                + "column must NOT smoosh onto TS's own surface (expected flush 0.0), got " + dy);
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hopperBesideChestOnVanillaColumnStillInheritsWhileTsOverrideActive(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos chestPos = buildChestOnColumnSourceSlab(helper, false);
        BlockPos hopper = chestPos.east();

        w.setBlock(hopper, Blocks.HOPPER.defaultBlockState(), 2);
        ts(helper);
        try {
            double dy = dyOf(w, hopper);
            if (Math.abs(dy + 0.5) > EPS) {
                throw helper.assertionException(helper.relativePos(hopper),
                        "ANTI-JAM: a hopper cantilevered beside a chest lowered by a genuine VANILLA column "
                                + "must STILL inherit -0.5 while the TS-exclusion override is active (expected "
                                + "-0.5), got " + dy + " — the guard must be namespace-scoped, not a blanket "
                                + "disable of adjacentLoweredBlockEntityMagnitude");
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }

    // ── source route: adjacentLoweredSideMagnitude (already covered by CantileverBfsTerrainSlabsGuardTest for
    // ── a full-block subject; re-proven here for a BLOCK-ENTITY subject through the same proven scene shape) ──

    /**
     * Identical scene shape to {@link CantileverBfsTerrainSlabsGuardTest#buildLoweredCarrierOverAir}
     * (that class's own proven TS-carrier construction), reproduced here so the block-entity subject is
     * tested through the exact same source-side setup already known to work.
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
        var carrierState = (tsOwned ? TS_SLAB : Blocks.BIRCH_SLAB)
                .defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        w.setBlock(carrier, carrierState, 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, carrier, w.getBlockState(carrier));

        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, carrier, w.getBlockState(carrier))) {
            throw helper.assertionException(helper.relativePos(carrier),
                    "setup: the " + (tsOwned ? "TS-owned" : "vanilla") + " carrier slab must persist as a lowered "
                            + "carrier (the reachable side-lane source the block-entity BFS terminates on)");
        }
        return carrier;
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hopperBesideTerrainSlabsCarrierSlabStaysFlush(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos carrier = buildLoweredCarrierOverAir(helper, true);
        BlockPos hopper = carrier.east(); // over air, adjacent to the TS-owned carrier, no slab of its own

        w.setBlock(hopper, Blocks.HOPPER.defaultBlockState(), 2);
        double base = dyOf(w, hopper);
        if (Math.abs(base + 0.5) > EPS) {
            throw helper.assertionException(helper.relativePos(hopper),
                    "setup (RED witness): WITHOUT the TS override a hopper cantilevered beside the lowered "
                            + "carrier slab must read -0.5 (proving adjacentLoweredSideMagnitude reaches this "
                            + "source via the block-entity BFS entry point), got " + base);
        }

        ts(helper);
        try {
            double dy = dyOf(w, hopper);
            if (Math.abs(dy) > EPS) {
                throw helper.assertionException(helper.relativePos(hopper),
                        "THE GUARD (adjacentLoweredBlockEntityMagnitude -> adjacentLoweredSideMagnitude): a "
                                + "hopper cantilevered beside a Terrain-Slabs-owned carrier slab must NOT smoosh "
                                + "onto TS's own surface (expected flush 0.0), got " + dy);
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hopperBesideVanillaCarrierSlabStillInheritsWhileTsOverrideActive(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos carrier = buildLoweredCarrierOverAir(helper, false);
        BlockPos hopper = carrier.east();

        w.setBlock(hopper, Blocks.HOPPER.defaultBlockState(), 2);
        ts(helper);
        try {
            double dy = dyOf(w, hopper);
            if (Math.abs(dy + 0.5) > EPS) {
                throw helper.assertionException(helper.relativePos(hopper),
                        "ANTI-JAM: a hopper cantilevered beside a genuine VANILLA lowered carrier slab must "
                                + "STILL inherit -0.5 while the TS-exclusion override is active (expected -0.5), "
                                + "got " + dy + " — the guard must be namespace-scoped, not a blanket disable");
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }
}
