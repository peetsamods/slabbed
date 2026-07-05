package com.slabbed.test;

import com.slabbed.client.SlabGeometricRemeshScheduler;
import com.slabbed.client.SlabGeometricRemeshScheduler.SectionBox;
import com.slabbed.compat.CompatHooks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Headless proof for the mesh-staleness fix's SCHEDULING LOGIC — the pure decision of whether a client
 * block change should trigger a neighbourhood re-mesh, and over what SECTION range. This is the ONLY
 * part of that fix that is headlessly observable: gametest runs no client renderer, so whether the mesh
 * actually re-bakes to the correct height is NOT testable here (that is exactly why the original bug was
 * invisible to the headless suite — both the render leg and the raycast leg read the same live
 * {@code getYOffset} function when queried headlessly, so a headless "triad agrees" check is vacuous for
 * a mesh-staleness bug; see lesson S7). These tests pin the scheduler's decision as a pure function,
 * separate from whether a real renderer executes it. A REAL live-client A/B (place a lowered tower,
 * cantilever a block beside it across a section boundary, break the tower, confirm the model snaps to
 * match the raycast without a manual chunk reload) is still required before the fix is fully proven.
 *
 * <p>All assertions are on {@link SlabGeometricRemeshScheduler} directly; no world, level or renderer is
 * touched, so a {@code GameTestHelper} is used only as the harness entry point (the same pure-logic
 * idiom as {@code BetaNoticeSessionGateTest}).
 */
public final class GeometricRemeshSchedulerTest {

    // A terrain_slabs-namespaced stand-in so the TS-source branch of the gate can be exercised via the
    // CompatHooks.shouldSkipSlabSupportTestOverride seam (TS itself is not on the gametest classpath).
    private static final Identifier TS_SLAB_ID =
            Identifier.fromNamespaceAndPath("terrain_slabs", "geometric_remesh_scheduler_test_slab");
    private static final ResourceKey<Block> TS_SLAB_KEY = ResourceKey.create(Registries.BLOCK, TS_SLAB_ID);
    private static final Block TS_SLAB =
            new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB).setId(TS_SLAB_KEY));

    /** Registers the TS-namespaced stand-in slab before registry freeze (via the main entrypoint). */
    public static final class GeometricRemeshSchedulerTestEntrypoint implements ModInitializer {
        @Override
        public void onInitialize() {
            if (!BuiltInRegistries.BLOCK.containsKey(TS_SLAB_ID)) {
                Registry.register(BuiltInRegistries.BLOCK, TS_SLAB_ID, TS_SLAB);
            }
        }
    }

    private static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    // ── gate: which changes must trigger a re-mesh ──────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabPlacedIntoAirTriggersRemesh(GameTestHelper helper) {
        // The canonical bug scenario: a support slab appears where there was air. A geometric subject up
        // to MAX_CHAIN_DEPTH away can now be lowered, so its neighbourhood MUST re-mesh.
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        if (!SlabGeometricRemeshScheduler.shouldRemeshNeighborhood(air(), slab)) {
            throw helper.assertionException(
                    "placing a bottom slab (air -> slab) is a lowering source appearing and MUST schedule a re-mesh");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabBrokenToAirTriggersRemesh(GameTestHelper helper) {
        // The NEVER-POP-relevant inverse: breaking a support. A cantilever subject that inherited from it
        // must re-mesh (it will settle to its anchored floor, not pop — but its section must still rebake).
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        if (!SlabGeometricRemeshScheduler.shouldRemeshNeighborhood(slab, air())) {
            throw helper.assertionException(
                    "breaking a bottom slab (slab -> air) removes a lowering source and MUST schedule a re-mesh");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullCubePlacedTriggersRemesh(GameTestHelper helper) {
        // A full opaque cube is a cantilever/column full-block source and a support-below in the column
        // walk. Its appearance/removal can change a neighbour's dy.
        if (!SlabGeometricRemeshScheduler.shouldRemeshNeighborhood(air(), Blocks.STONE.defaultBlockState())) {
            throw helper.assertionException(
                    "placing a full opaque cube (air -> stone) is a potential lowering source and MUST schedule a re-mesh");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void blockEntityPlacedTriggersRemesh(GameTestHelper helper) {
        // Hopper/chest participate in the block-entity cantilever lane (adjacentLoweredBlockEntityMagnitude).
        if (!SlabGeometricRemeshScheduler.shouldRemeshNeighborhood(air(), Blocks.CHEST.defaultBlockState())) {
            throw helper.assertionException(
                    "placing a block entity (air -> chest) participates in the cantilever lane and MUST schedule a re-mesh");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabsSurfaceTriggersRemesh(GameTestHelper helper) {
        // A TS-owned surface terminates a column walk flush; its presence/absence changes a subject's dy.
        // Force the TS-owned verdict via the same seam the TS-guard tests use.
        CompatHooks.shouldSkipSlabSupportTestOverride = st ->
                "terrain_slabs".equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace());
        try {
            BlockState tsSlab = TS_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            if (!SlabGeometricRemeshScheduler.shouldRemeshNeighborhood(air(), tsSlab)) {
                throw helper.assertionException(
                        "a Terrain-Slabs-owned surface appearing MUST schedule a re-mesh (it terminates a column walk flush)");
            }
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void nonSourceChangesDoNotTriggerRemesh(GameTestHelper helper) {
        // The cost gate: decorations that can never be a lowering source/support must short-circuit with
        // NO section work, so the added per-block-change cost on ordinary gameplay churn is near zero.
        BlockState[] nonSources = {
                air(),
                Blocks.DANDELION.defaultBlockState(),
                Blocks.TORCH.defaultBlockState(),
                Blocks.REDSTONE_WIRE.defaultBlockState(),
                Blocks.WHEAT.defaultBlockState(),
                Blocks.SNOW.defaultBlockState(),          // thin top layer
                Blocks.OAK_SAPLING.defaultBlockState(),
        };
        for (BlockState a : nonSources) {
            for (BlockState b : nonSources) {
                if (SlabGeometricRemeshScheduler.shouldRemeshNeighborhood(a, b)) {
                    throw helper.assertionException(
                            "a change between two non-source blocks (" + a.getBlock() + " -> " + b.getBlock()
                                    + ") must NOT schedule a re-mesh — that would re-mesh on every torch/flower/"
                                    + "redstone/crop change in the game");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sourceOnEitherSideTriggersRemesh(GameTestHelper helper) {
        // A non-source replaced by a source (or vice-versa) must still fire — the gate is an OR over both
        // endpoints, not "the new state only". E.g. a flower replaced by a slab.
        BlockState flower = Blocks.DANDELION.defaultBlockState();
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        if (!SlabGeometricRemeshScheduler.shouldRemeshNeighborhood(flower, slab)) {
            throw helper.assertionException("flower -> slab (source appears) must schedule a re-mesh");
        }
        if (!SlabGeometricRemeshScheduler.shouldRemeshNeighborhood(slab, flower)) {
            throw helper.assertionException("slab -> flower (source disappears) must schedule a re-mesh");
        }
        helper.succeed();
    }

    // ── dirty section box: correct scope, covers the full MAX_CHAIN_DEPTH reach ──────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dirtyBoxIsSectionRadiusOneAroundTheChangedSection(GameTestHelper helper) {
        // A block mid-section (x=40 -> section 2, y=72 -> section 4, z=8 -> section 0). The box must be
        // that section ±1 on every axis, in SECTION coordinates.
        int bx = 40, by = 72, bz = 8;
        SectionBox box = SlabGeometricRemeshScheduler.dirtySectionBox(bx, by, bz);
        int sx = SectionPos.blockToSectionCoord(bx);
        int sy = SectionPos.blockToSectionCoord(by);
        int sz = SectionPos.blockToSectionCoord(bz);
        assertBox(helper, box, sx - 1, sy - 1, sz - 1, sx + 1, sy + 1, sz + 1,
                "mid-section change");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dirtyBoxHandlesNegativeCoordinatesViaFloorDivision(GameTestHelper helper) {
        // Section coords use floor division (block >> 4), not integer truncation: block -1 is in section
        // -1, not 0. A bug that used bx/16 would put -1 in section 0 and mis-locate the box.
        int bx = -1, by = -20, bz = -33;
        SectionBox box = SlabGeometricRemeshScheduler.dirtySectionBox(bx, by, bz);
        int sx = SectionPos.blockToSectionCoord(bx); // -1
        int sy = SectionPos.blockToSectionCoord(by); // -2
        int sz = SectionPos.blockToSectionCoord(bz); // -3
        assertBox(helper, box, sx - 1, sy - 1, sz - 1, sx + 1, sy + 1, sz + 1,
                "negative-coordinate change");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dirtyBoxCoversFullMaxChainDepthReachInEveryDirection(GameTestHelper helper) {
        // THE CORRECTNESS INVARIANT the radius exists to guarantee: a subject up to MAX_CHAIN_DEPTH (16)
        // blocks from the changed support — in ANY axis, from ANY intra-section alignment of the support —
        // must land inside the dirtied SECTION box. This is what makes ±1 section (not a small block
        // radius) the right scope: 16 blocks can straddle exactly one section boundary. Proven exhaustively
        // over every within-section offset of the support and the 6 axial extremes of the subject.
        final int maxChainDepth = 16;
        for (int off = 0; off < 16; off++) {
            int supportX = 1000 + off; // arbitrary base, sweep the support across a whole section
            int supportY = 40 + off;
            int supportZ = -500 + off;
            SectionBox box = SlabGeometricRemeshScheduler.dirtySectionBox(supportX, supportY, supportZ);
            int[][] subjects = {
                    {supportX - maxChainDepth, supportY, supportZ},
                    {supportX + maxChainDepth, supportY, supportZ},
                    {supportX, supportY - maxChainDepth, supportZ},
                    {supportX, supportY + maxChainDepth, supportZ},
                    {supportX, supportY, supportZ - maxChainDepth},
                    {supportX, supportY, supportZ + maxChainDepth},
            };
            for (int[] s : subjects) {
                int ssx = SectionPos.blockToSectionCoord(s[0]);
                int ssy = SectionPos.blockToSectionCoord(s[1]);
                int ssz = SectionPos.blockToSectionCoord(s[2]);
                boolean inside = ssx >= box.minX() && ssx <= box.maxX()
                        && ssy >= box.minY() && ssy <= box.maxY()
                        && ssz >= box.minZ() && ssz <= box.maxZ();
                if (!inside) {
                    throw helper.assertionException(
                            "a subject " + maxChainDepth + " blocks from a support at block ("
                                    + supportX + "," + supportY + "," + supportZ + ") landed in section ("
                                    + ssx + "," + ssy + "," + ssz + ") OUTSIDE the dirtied box " + box
                                    + " — SECTION_RADIUS=" + SlabGeometricRemeshScheduler.SECTION_RADIUS
                                    + " does not cover the full MAX_CHAIN_DEPTH reach; if MAX_CHAIN_DEPTH grew "
                                    + "past one section width the radius must grow with it");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void schedulingDecisionIsPureAndStableUnderRepetition(GameTestHelper helper) {
        // Structural proof of the near-zero-cost claim for the irrelevant-change fast path: the decision is
        // a pure function (no side effects, no world read), so N repeated calls return the identical result
        // and never throw. This is the property that lets the mixin gate every single client block change
        // cheaply. (A wall-clock micro-benchmark would be flaky in CI; purity + allocation-free instanceof
        // checks are the real guarantee, asserted here as invariance under repetition.)
        BlockState flower = Blocks.DANDELION.defaultBlockState();
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        boolean firstNonSource = SlabGeometricRemeshScheduler.shouldRemeshNeighborhood(flower, flower);
        boolean firstSource = SlabGeometricRemeshScheduler.shouldRemeshNeighborhood(air(), slab);
        for (int i = 0; i < 100_000; i++) {
            if (SlabGeometricRemeshScheduler.shouldRemeshNeighborhood(flower, flower) != firstNonSource
                    || SlabGeometricRemeshScheduler.shouldRemeshNeighborhood(air(), slab) != firstSource) {
                throw helper.assertionException(
                        "the scheduling decision must be a stable pure function across repeated calls (iteration " + i + ")");
            }
        }
        if (firstNonSource) {
            throw helper.assertionException("sanity: flower->flower must be a non-source (false) decision");
        }
        if (!firstSource) {
            throw helper.assertionException("sanity: air->slab must be a source (true) decision");
        }
        helper.succeed();
    }

    private static void assertBox(GameTestHelper helper, SectionBox box,
                                  int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String scene) {
        if (box.minX() != minX || box.minY() != minY || box.minZ() != minZ
                || box.maxX() != maxX || box.maxY() != maxY || box.maxZ() != maxZ) {
            throw helper.assertionException(
                    scene + ": expected section box [" + minX + "," + minY + "," + minZ + " .. "
                            + maxX + "," + maxY + "," + maxZ + "] but got " + box);
        }
    }
}
