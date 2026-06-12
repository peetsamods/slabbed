package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.dev.SlabbedLabFixtures;
import com.slabbed.dev.SlabbedLabFixtures.LaneStatus;
import com.slabbed.dev.SlabbedLabFixtures.PlaceResult;
import com.slabbed.util.SlabSupport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.test.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

/**
 * Server GameTest exercising the basic Slabbed Lab fixture lifecycle.
 *
 * Reuses the canonical {@link SlabbedLabFixtures} public API directly —
 * no fixture logic is duplicated here.
 *
 * Lifecycle covered:
 *   1. place fixture (all 3 lanes)
 *   2. assert FULL, BOTTOM_SLAB, TOP_SLAB each placed with exact expected state
 *   3. break FULL support → assert air
 *   4. restore FULL support → assert stone
 *   5. neighbor-update pulse on FULL → assert FULL support still matches post-pulse
 */
public final class SlabbedLabFixtureTest {
    private static final double MC1211_SERVER_STATE_EPSILON = 1.0e-6d;
    private static final String DEFERRED_OVERLAP_SCENARIO = "LOWERED_TOP_SLAB_SIDE_LANE_STACK";
    private static final String DEFERRED_OVERLAP_REASON = "LOWERED_TOP_SLAB_SIDE_LANE_STACK_DEFERRED";
    private static final BlockPos FIXTURE_TEST_OFFSET = new BlockPos(0, 1, 0);

    private static BlockPos fixtureTestOrigin(TestContext ctx) {
        return ctx.getAbsolutePos(FIXTURE_TEST_OFFSET);
    }

    /**
     * Exercises the basic fixture lifecycle on all three lanes (placement assertions)
     * and the full break/restore/pulse cycle on the FULL lane.
     *
     * <p>Uses {@code fabric-gametest-api-v1:empty} (built-in 8×8×8 all-air structure).
     * Fixture footprint: X=0..4, Y=0..1, Z=0..1 (pulse at Z=1) — fits within bounds.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void labSupportCycle(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        // Use a y+1 fixture origin to avoid template-floor occupancy in MC1211 empty templates.
        BlockPos origin = fixtureTestOrigin(ctx);

        // --- 1. Place the basic fixture (all 3 lanes, pre-verified air) ---
        PlaceResult placed = SlabbedLabFixtures.placeBasicFixture(world, origin);
        ctx.assertTrue(placed.ok(), "placeBasicFixture failed: " + placed.error());

        // --- 2. Assert each lane placed with its exact expected state ---

        LaneStatus fullInit = SlabbedLabFixtures.queryStatus(world, origin, "FULL").get(0);
        ctx.assertTrue(fullInit.supportMatch(),
                "FULL initial mismatch: expected " + fullInit.expectedSupport()
                        + ", got " + fullInit.actualSupport());

        LaneStatus bottomInit = SlabbedLabFixtures.queryStatus(world, origin, "BOTTOM_SLAB").get(0);
        ctx.assertTrue(bottomInit.supportMatch(),
                "BOTTOM_SLAB initial mismatch: expected " + bottomInit.expectedSupport()
                        + ", got " + bottomInit.actualSupport());

        LaneStatus topInit = SlabbedLabFixtures.queryStatus(world, origin, "TOP_SLAB").get(0);
        ctx.assertTrue(topInit.supportMatch(),
                "TOP_SLAB initial mismatch: expected " + topInit.expectedSupport()
                        + ", got " + topInit.actualSupport());

        // --- 3. Break FULL lane support (stone → air, NOTIFY_ALL) ---
        PlaceResult broke = SlabbedLabFixtures.breakSupport(world, origin, "FULL");
        ctx.assertTrue(broke.ok(), "breakSupport(FULL) failed: " + broke.error());

        BlockPos fullSupportPos = origin; // FULL lane = origin + (0,0,0)
        ctx.assertTrue(
                world.getBlockState(fullSupportPos).isAir(),
                "FULL support should be air after breakSupport");

        // --- 4. Restore FULL lane support (air → stone, NOTIFY_ALL) ---
        PlaceResult restored = SlabbedLabFixtures.restoreSupport(world, origin, "FULL");
        ctx.assertTrue(restored.ok(), "restoreSupport(FULL) failed: " + restored.error());

        ctx.assertTrue(
                world.getBlockState(fullSupportPos).isOf(Blocks.STONE),
                "FULL support should be stone after restoreSupport");

        // --- 5. Neighbor-update pulse on FULL, then assert support is still stable ---
        PlaceResult pulse = SlabbedLabFixtures.neighborUpdatePulse(world, origin, "FULL");
        ctx.assertTrue(pulse.ok(), "neighborUpdatePulse(FULL) failed: " + pulse.error());

        LaneStatus postPulse = SlabbedLabFixtures.queryStatus(world, origin, "FULL").get(0);
        ctx.assertTrue(postPulse.supportMatch(),
                "FULL support should still match after pulse: expected "
                        + postPulse.expectedSupport() + ", got " + postPulse.actualSupport());

        ctx.complete();
    }

    /**
     * Server-state-only MC 1.21.1 proof: deterministic authored lanes must resolve
     * to legal server dy / attachment state without full-block vertical overlap.
     *
     * <p>This deliberately does not measure model dy, renderer dy, outline,
     * raycast, client attachment sync, render-view bridge lookup, or reload.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void mc1211ServerStateOverlapMatrix(TestContext ctx) {
        boolean goblinOnly = Boolean.getBoolean("slabbed.mc1211.goblinOnly");
        boolean sidePlaceStoneLoweringOnly = Boolean.getBoolean("slabbed.mc1211.sidePlaceStoneLoweringOnly");
        boolean sidePlaceStoneLiveTruthOnly = Boolean.getBoolean("slabbed.mc1211.sidePlaceStoneLiveTruthOnly");
        boolean sameSpotAfterSlabBreakOnly = Boolean.getBoolean("slabbed.mc1211.sameSpotAfterSlabBreakOnly");
        boolean slabThenBlockBaselineOnly = Boolean.getBoolean("slabbed.mc1211.slabThenBlockBaselineOnly");
        boolean wallFenceProductRedOnly = Boolean.getBoolean("slabbed.mc1211.wallFenceProductRedOnly");
        boolean sbsTopSlabCombinationRed = Boolean.getBoolean("slabbed.mc1211.sbsTopSlabCombinationRed");
        boolean overlapOnly = Boolean.getBoolean("slabbed.mc1211.overlapMatrixOnly");
        if ((goblinOnly
                || sidePlaceStoneLoweringOnly
                || sidePlaceStoneLiveTruthOnly
                || sameSpotAfterSlabBreakOnly
                || slabThenBlockBaselineOnly
                || wallFenceProductRedOnly
                || sbsTopSlabCombinationRed)
                && !overlapOnly) {
            System.out.println("[MC1211_SERVER_STATE_OVERLAP_MATRIX_SKIPPED]"
                    + " route=runClientGameTest"
                    + " reason=client_route_only"
                    + " property=" + (slabThenBlockBaselineOnly
                    ? "slabbed.mc1211.slabThenBlockBaselineOnly"
                    : wallFenceProductRedOnly
                    ? "slabbed.mc1211.wallFenceProductRedOnly"
                    : sbsTopSlabCombinationRed
                    ? "slabbed.mc1211.sbsTopSlabCombinationRed"
                    : sameSpotAfterSlabBreakOnly
                    ? "slabbed.mc1211.sameSpotAfterSlabBreakOnly"
                    : sidePlaceStoneLiveTruthOnly
                    ? "slabbed.mc1211.sidePlaceStoneLiveTruthOnly"
                    : sidePlaceStoneLoweringOnly
                    ? "slabbed.mc1211.sidePlaceStoneLoweringOnly"
                    : "slabbed.mc1211.goblinOnly"));
            ctx.complete();
            return;
        }

        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        List<ServerStateOverlapRow> rows = new ArrayList<>();

        BlockPos bottomSupport = origin.add(0, 0, 0);
        BlockPos bottomObject = bottomSupport.up();
        world.setBlockState(bottomSupport, slab(SlabType.BOTTOM), Block.NOTIFY_ALL);
        authorBlock(world, bottomObject, Blocks.STONE.getDefaultState());
        rows.add(measureServerStateRow(
                world,
                "A_ordinary_bottom_slab_full_block",
                bottomSupport,
                bottomObject,
                "DIRECT_BOTTOM_SLAB_ANCHORED_FULL_BLOCK",
                -0.5d,
                true,
                false,
                false,
                false,
                false,
                true,
                "bottom_slab_authors_ordinary_full_block_anchor"));

        BlockPos topSupport = origin.add(2, 0, 0);
        BlockPos topObject = topSupport.up();
        world.setBlockState(topSupport, slab(SlabType.TOP), Block.NOTIFY_ALL);
        authorBlock(world, topObject, Blocks.STONE.getDefaultState());
        rows.add(measureServerStateRow(
                world,
                "B_top_slab_full_block",
                topSupport,
                topObject,
                "VANILLA_TOP_SLAB_FULL_BLOCK",
                0.0d,
                false,
                true,
                false,
                true,
                false,
                true,
                "top_slab_must_not_author_lowered_anchor"));

        BlockPos doubleSupport = origin.add(4, 0, 0);
        BlockPos doubleObject = doubleSupport.up();
        world.setBlockState(doubleSupport, slab(SlabType.DOUBLE), Block.NOTIFY_ALL);
        authorBlock(world, doubleObject, Blocks.STONE.getDefaultState());
        rows.add(measureServerStateRow(
                world,
                "C_double_slab_full_block",
                doubleSupport,
                doubleObject,
                "VANILLA_DOUBLE_SLAB_FULL_BLOCK",
                0.0d,
                false,
                true,
                false,
                true,
                false,
                true,
                "double_slab_must_not_author_lowered_anchor"));

        BlockPos loweredCarrier = authorLoweredBottomCarrier(world, origin.add(0, 0, 2));
        BlockPos compoundObject = loweredCarrier.up();
        authorBlock(world, compoundObject, Blocks.STONE.getDefaultState());
        rows.add(measureServerStateRow(
                world,
                "D_lowered_bottom_carrier_full_block",
                loweredCarrier,
                compoundObject,
                "COMPOUND_DY_NEG_1_FULL_BLOCK_ON_LOWERED_BOTTOM_CARRIER",
                -1.0d,
                true,
                false,
                true,
                false,
                true,
                true,
                "lowered_bottom_carrier_authors_compound_full_block_lane"));

        BlockPos persistedCarrier = authorLoweredBottomCarrier(world, origin.add(2, 0, 2));
        BlockPos persistedObject = persistedCarrier.up();
        authorBlock(world, persistedObject, Blocks.STONE.getDefaultState());
        world.setBlockState(persistedCarrier, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        rows.add(measureServerStateRow(
                world,
                "E_compound_dy_neg_1_full_block_persisted_after_source_removed",
                persistedCarrier,
                persistedObject,
                "PERSISTED_COMPOUND_DY_NEG_1_FULL_BLOCK",
                -1.0d,
                true,
                false,
                false,
                false,
                true,
                false,
                "compound_full_block_anchor_must_preserve_dy_without_live_source_slab"));

        BlockPos sideBase = origin.add(4, 0, 2);
        world.setBlockState(sideBase, slab(SlabType.BOTTOM), Block.NOTIFY_ALL);
        BlockPos sideLoweredFull = sideBase.up();
        authorBlock(world, sideLoweredFull, Blocks.STONE.getDefaultState());
        BlockPos sideLaneSupport = sideLoweredFull.east();
        world.setBlockState(sideLaneSupport, slab(SlabType.DOUBLE), Block.NOTIFY_ALL);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, sideLaneSupport,
                world.getBlockState(sideLaneSupport));
        BlockPos sideLaneObject = sideLaneSupport.up();
        authorBlock(world, sideLaneObject, Blocks.STONE.getDefaultState());
        rows.add(measureServerStateRow(
                world,
                "F_lowered_slab_lane_carrier_stack",
                sideLaneSupport,
                sideLaneObject,
                "LOWERED_SIDE_LANE_DOUBLE_CARRIER_STACK",
                -0.5d,
                true,
                false,
                true,
                false,
                false,
                true,
                "lowered_side_lane_double_carrier_supports_full_block_stack"));

        BlockPos decorativeCarrier = authorLoweredBottomCarrier(world, origin.add(0, 0, 5));
        BlockPos wallPos = decorativeCarrier.up();
        world.setBlockState(wallPos, Blocks.COBBLESTONE_WALL.getDefaultState(), Block.NOTIFY_ALL);
        BlockPos lanternPos = wallPos.up();
        world.setBlockState(lanternPos, Blocks.LANTERN.getDefaultState(), Block.NOTIFY_ALL);
        rows.add(measureServerStateRow(
                world,
                "G_wall_lantern_decorative_stack_state",
                wallPos,
                lanternPos,
                "DECORATIVE_WALL_LANTERN_CURRENT_SERVER_LAW",
                -1.0d,
                false,
                true,
                false,
                false,
                false,
                false,
                "decorative_state_only_no_model_outline_raycast_targeting_claim"));

        BlockPos mergeSourceSlab = origin.add(0, 0, 7);
        BlockPos mergeSourceFull = mergeSourceSlab.up();
        world.setBlockState(mergeSourceSlab, slab(SlabType.BOTTOM), Block.NOTIFY_ALL);
        authorBlock(world, mergeSourceFull, Blocks.STONE.getDefaultState());
        BlockPos vanillaSupport = mergeSourceSlab.east();
        BlockPos vanillaSupportedSlab = vanillaSupport.up();
        world.setBlockState(vanillaSupport, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(vanillaSupportedSlab, slab(SlabType.BOTTOM), Block.NOTIFY_ALL);
        rows.add(measureServerStateRow(
                world,
                "H_vb_vs_supported_slab_isolated_from_adjacent_vs_vb",
                vanillaSupportedSlab,
                vanillaSupportedSlab,
                "VANILLA_SUPPORTED_SLAB_NOT_SIDE_LANE_CARRIER",
                0.0d,
                false,
                true,
                false,
                true,
                false,
                false,
                "vb_vs_vertical_support_wins_over_adjacent_lowered_lane"));

        int green = 0;
        int red = 0;
        int deferred = 0;
        int inconclusive = 0;
        ServerStateOverlapRow firstRed = null;
        ServerStateOverlapRow firstDeferred = null;
        for (ServerStateOverlapRow row : rows) {
            System.out.println(row.toMarkerLine());
            if ("GREEN".equals(row.result())) {
                green++;
            } else if ("DEFERRED".equals(row.result())) {
                deferred++;
                if (firstDeferred == null) {
                    firstDeferred = row;
                }
            } else if ("RED".equals(row.result())) {
                red++;
                if (firstRed == null) {
                    firstRed = row;
                }
            } else {
                inconclusive++;
            }
        }

        System.out.println("[MC1211_SERVER_STATE_OVERLAP_MATRIX_SUMMARY]"
                + " totalRows=" + rows.size()
                + " green=" + green
                + " red=" + red
                + " deferred=" + deferred
                + " inconclusive=" + inconclusive
                + " surfacesNotCovered=model/render,outline,raycast,client_sync_render_view_bridge,reload"
                + " proofScope=server_placement_state_authority_only");

        ctx.assertTrue(red == 0,
                "MC1211 server-state overlap matrix has RED rows; firstRed="
                        + (firstRed == null ? "none" : firstRed.scenario() + ":" + firstRed.reason()));
        ctx.assertTrue(
                deferred <= 1,
                "MC1211 server-state overlap matrix has too many DEFERRED rows; firstDeferred="
                        + (firstDeferred == null ? "none" : firstDeferred.scenario() + ":" + firstDeferred.reason())
                        + ", deferred=" + deferred);
        ctx.complete();
    }

    /**
     * Regression guard: proves that outline and raycast shapes share the same
     * Slabbed Y-offset for a block placed above a bottom-slab lane.
     *
     * <p>Uses {@link net.minecraft.block.ComposterBlock} because it is one of
     * the few vanilla blocks that overrides {@code getRaycastShape} with a
     * non-empty shape ({@code VoxelShapes.fullCube()}), making the parity
     * assertion meaningful. Most solid blocks (e.g. stone) return
     * {@code VoxelShapes.empty()} for {@code getRaycastShape}, causing the
     * game to fall back to the outline shape for targeting anyway; the
     * asymmetry only manifests for blocks with a non-empty raycast shape.
     *
     * <p>Before the {@code getRaycastShape} injection was added, the outline
     * shape was correctly offset to minY=-0.5 while the raycast shape remained
     * at minY=0.0. This test fails against that regressed state and passes
     * once parity is restored.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void outlineRaycastParity(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = fixtureTestOrigin(ctx);

        // Place the 3-lane fixture; BOTTOM_SLAB support lands at origin+(2,0,0).
        PlaceResult placed = SlabbedLabFixtures.placeBasicFixture(world, origin);
        ctx.assertTrue(placed.ok(), "placeBasicFixture failed: " + placed.error());

        // Place a composter directly above the BOTTOM_SLAB lane support.
        // Composter.getRaycastShape returns VoxelShapes.fullCube() (non-empty, minY=0.0).
        // SlabSupport.getYOffset returns -0.5 via shouldOffset → hasSlabInColumn.
        BlockPos testPos = origin.add(2, 1, 0);
        world.setBlockState(testPos, Blocks.COMPOSTER.getDefaultState(), Block.NOTIFY_LISTENERS);

        BlockState testState = world.getBlockState(testPos);
        ctx.assertTrue(testState.isOf(Blocks.COMPOSTER), "composter not present at test position");

        VoxelShape outline = testState.getOutlineShape(world, testPos, ShapeContext.absent());
        VoxelShape raycast = testState.getRaycastShape(world, testPos);

        double outlineMinY = outline.getBoundingBox().minY;
        double raycastMinY = raycast.getBoundingBox().minY;

        // Prove the offset is applied (not vacuously equal at the unshifted 0.0).
        ctx.assertTrue(outlineMinY < 0.0,
                "outline not slabbed-offset: expected minY < 0, got " + outlineMinY);

        // Parity: raycast offset must equal outline offset.
        ctx.assertTrue(outlineMinY == raycastMinY,
                "outline/raycast parity broken: outline minY=" + outlineMinY
                        + ", raycast minY=" + raycastMinY);

        ctx.complete();
    }

    /**
     * Regression guard: full-cube {@link net.minecraft.block.BlockEntityProvider}
     * blocks (jukebox, spawner, end portal frame, …) must still sit on slabs
     * with {@code dy=-0.5} and an outline offset by -0.5.
     *
     * <p>The {@code !state.isSolidBlock} gate in {@code SlabSupport.shouldOffset}
     * alone excludes full-cube BEs because {@code Jukebox.isSolidBlock == true}
     * — which in turn breaks the {@link SlabSupport#isLoweredBlockEntityVisual}
     * contract that covers every BE block. The {@code isSlabSitCandidate}
     * helper restores them via an explicit {@code BlockEntityProvider}
     * category check without re-opening the generic solid-cube fallback.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void blockEntityFullCubeSitsOnSlab(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = fixtureTestOrigin(ctx);

        PlaceResult placed = SlabbedLabFixtures.placeBasicFixture(world, origin);
        ctx.assertTrue(placed.ok(), "placeBasicFixture failed: " + placed.error());

        BlockPos testPos = origin.add(2, 1, 0); // above BOTTOM_SLAB lane
        world.setBlockState(testPos, Blocks.JUKEBOX.getDefaultState(), Block.NOTIFY_LISTENERS);

        BlockState state = world.getBlockState(testPos);
        ctx.assertTrue(state.isOf(Blocks.JUKEBOX), "jukebox not present at test position");

        double dy = SlabSupport.getYOffset(world, testPos, state);
        ctx.assertTrue(dy == -0.5,
                "jukebox above BOTTOM_SLAB should lower; dy=" + dy
                + " (isSlabSitCandidate BlockEntityProvider path regressed)");

        VoxelShape outline = state.getOutlineShape(world, testPos, ShapeContext.absent());
        double minY = outline.getBoundingBox().minY;
        ctx.assertTrue(minY == -0.5,
                "jukebox outline minY should be -0.5, got " + minY);

        // Contract: isLoweredBlockEntityVisual must agree for every BE block.
        ctx.assertTrue(
                SlabSupport.isLoweredBlockEntityVisual(world, testPos, state),
                "isLoweredBlockEntityVisual must be true for jukebox above BOTTOM_SLAB");

        ctx.complete();
    }

    /**
     * Canonical intent: ordinary solid cubes SHOULD inherit -0.5 offset from
     * the generic slab-column walk when placed above a bottom slab support.
     *
     * <p>This is the global slab support policy: ordinary full blocks anchor/lower
     * onto slabs. The previous selective-only policy that excluded solid cubes
     * has been retired.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void solidCubeLowersOverSlab(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = fixtureTestOrigin(ctx);

        PlaceResult placed = SlabbedLabFixtures.placeBasicFixture(world, origin);
        ctx.assertTrue(placed.ok(), "placeBasicFixture failed: " + placed.error());

        BlockPos testPos = origin.add(2, 1, 0); // above BOTTOM_SLAB lane
        world.setBlockState(testPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        BlockState state = world.getBlockState(testPos);
        ctx.assertTrue(state.isOf(Blocks.STONE), "stone not present at test position");

        double dy = SlabSupport.getYOffset(world, testPos, state);
        ctx.assertTrue(dy == -0.5, "stone should lower over slab column; dy=" + dy);

        VoxelShape outline = state.getOutlineShape(world, testPos, ShapeContext.absent());
        ctx.assertTrue(outline.getBoundingBox().minY == -0.5,
                "stone outline minY should be -0.5, got " + outline.getBoundingBox().minY);

        ctx.complete();
    }

    /**
     * Regression guard: proves that carpet outline offset is applied exactly once
     * (not doubled) for a carpet placed above a bottom-slab lane on the SERVER.
     *
     * <p>After dedupe: {@code CarpetBlockMixin.slabbed$offsetShape} is removed.
     * Server-side {@code getOutlineShape} for carpet returns the unmodified shape
     * (minY == 0.0). Client-side, {@code CarpetDyShapeMixin.slabbed$offsetCarpetOutline}
     * (client-only mixin) provides the single -0.5 offset.
     *
     * <p>Before the dedupe, {@code CarpetBlockMixin.slabbed$offsetShape} (both-env)
     * was the sole effective handler on the server (due to cancellation short-circuit).
     * This test fails against double-offset state (minY == -1.0) and trivially
     * confirms the server path produces 0.0 post-dedupe.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void carpetOutlineNotDoubled(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = fixtureTestOrigin(ctx);

        // Place the 3-lane fixture; BOTTOM_SLAB support lands at origin+(2,0,0).
        PlaceResult placed = SlabbedLabFixtures.placeBasicFixture(world, origin);
        ctx.assertTrue(placed.ok(), "placeBasicFixture failed: " + placed.error());

        // Place white carpet directly above the BOTTOM_SLAB lane support.
        // setBlockState bypasses canPlaceAt, so carpet lands regardless of support rules.
        BlockPos carpetPos = origin.add(2, 1, 0);
        world.setBlockState(carpetPos, Blocks.WHITE_CARPET.getDefaultState(), Block.NOTIFY_LISTENERS);

        BlockState carpetState = world.getBlockState(carpetPos);
        ctx.assertTrue(carpetState.isOf(Blocks.WHITE_CARPET), "white carpet not present at test position");
        ctx.assertTrue(carpetState.getBlock() instanceof CarpetBlock, "block is not a CarpetBlock instance");

        VoxelShape outline = carpetState.getOutlineShape(world, carpetPos, ShapeContext.absent());
        double minY = outline.getBoundingBox().minY;

        // Server: CarpetDyShapeMixin is client-only; CarpetBlockMixin.slabbed$offsetShape
        // is removed. No server-side offset → minY must be 0.0 (unmodified carpet shape).
        ctx.assertTrue(minY == 0.0,
                "server carpet outline should be unmodified (minY=0.0), got " + minY
                + ". If -0.5: server-side offset still active. If -1.0: double-offset.");

        ctx.complete();
    }

    /**
     * Julia's law proof: a structural full block <em>placed</em> flat (dy=0) must STAY at
     * dy=0 even after a bottom slab is later placed directly under it. No autonomous pop,
     * no retroactively-inherited lowering.
     *
     * <p>Exercises the REAL production placement path: {@link #authorBlock} invokes
     * {@code Block.onPlaced}, which the {@code BlockOnPlacedAnchorMixin} intercepts to call
     * {@link SlabAnchorAttachment#freezeLoweredOnPlace}. With air below at placement, dy=0,
     * so the structural block is recorded FROZEN_FLAT. Adding a bottom slab below afterward
     * normally drives {@code shouldOffset → hasSlabInColumn → -0.5} (see
     * {@link #unfrozenBlockLowersWhenSlabAddedBelow} for that uncovered control), but the
     * frozen-flat marker is read first in {@code getYOffsetInner} and pins dy at 0.0.
     *
     * <p>This is the exact violation Julia reported: "Placing that bottom slab under a
     * floating block caused the block to inherit a lowered position. That is against the law."
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void frozenFlatBlockStaysFlatWhenSlabAddedBelow(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = fixtureTestOrigin(ctx);

        // Floating spot with air directly below (no slab in the column at placement time).
        BlockPos blockPos = origin.add(2, 3, 0);
        BlockPos belowPos = blockPos.down();
        world.setBlockState(belowPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        // REAL placement: onPlaced → BlockOnPlacedAnchorMixin → freezeLoweredOnPlace.
        // Air below ⇒ dy=0 ⇒ structural stone recorded frozen-flat (not anchored).
        BlockState placed = authorBlock(world, blockPos, Blocks.STONE.getDefaultState());
        ctx.assertTrue(placed.isOf(Blocks.STONE), "stone not present at test position");
        ctx.assertTrue(SlabAnchorAttachment.isFrozenFlat(world, blockPos),
                "stone placed flat (air below) must be recorded frozen-flat by onPlaced");
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(world, blockPos),
                "flat-placed stone must NOT be anchored (it was never lowered)");
        ctx.assertTrue(SlabSupport.getYOffset(world, blockPos, placed) == 0.0,
                "flat-placed stone dy must be 0 before any slab is added");

        // THE VIOLATION: place a bottom slab directly under the now-floating block.
        world.setBlockState(belowPos, slab(SlabType.BOTTOM), Block.NOTIFY_ALL);
        ctx.assertTrue(world.getBlockState(belowPos).getBlock() instanceof SlabBlock,
                "bottom slab not present below test position");

        // LAW: the placed block stays put — dy must remain 0.0 (no inherited lowering).
        double dy = SlabSupport.getYOffset(world, blockPos, placed);
        ctx.assertTrue(dy == 0.0,
                "LAW: flat-placed stone must stay at dy=0 after a bottom slab is placed under it; got dy=" + dy);

        VoxelShape outline = placed.getOutlineShape(world, blockPos, ShapeContext.absent());
        ctx.assertTrue(outline.getBoundingBox().minY == 0.0,
                "flat-placed stone outline minY must stay 0.0 after slab added; got "
                + outline.getBoundingBox().minY);

        ctx.complete();
    }

    /**
     * Negative control for {@link #frozenFlatBlockStaysFlatWhenSlabAddedBelow}: proves the
     * slab-below lowering mechanism is real, so the law proof is not vacuously green.
     *
     * <p>A stone placed via {@code setBlockState} never runs {@code onPlaced}, so it carries
     * no frozen-flat marker (this mirrors terrain / non-player blocks). Adding a bottom slab
     * directly below then lowers it to -0.5 via {@code shouldOffset → hasSlabInColumn}. The
     * frozen-flat marker is precisely what suppresses this for player-placed blocks.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void unfrozenBlockLowersWhenSlabAddedBelow(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = fixtureTestOrigin(ctx);

        BlockPos blockPos = origin.add(2, 3, 0);
        BlockPos belowPos = blockPos.down();
        world.setBlockState(belowPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        // setBlockState bypasses onPlaced ⇒ NO frozen-flat marker.
        world.setBlockState(blockPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockState placed = world.getBlockState(blockPos);
        ctx.assertTrue(placed.isOf(Blocks.STONE), "stone not present at test position");
        ctx.assertTrue(!SlabAnchorAttachment.isFrozenFlat(world, blockPos),
                "setBlockState stone must NOT be frozen-flat (no onPlaced ran)");

        world.setBlockState(belowPos, slab(SlabType.BOTTOM), Block.NOTIFY_ALL);

        double dy = SlabSupport.getYOffset(world, blockPos, placed);
        ctx.assertTrue(dy == -0.5,
                "control: unfrozen stone over a bottom slab should lower to -0.5; got dy=" + dy);

        ctx.complete();
    }

    /**
     * Truth table for {@link SlabSupport#isSlabHeightStepFace} — the renderer-agnostic predicate
     * a future cull mixin will use to force-draw the see-through "ghost window" seam (see
     * docs/CULL-WINDOW-FIX-DESIGN.md). Pure logic; no render wiring calls it yet.
     *
     * <ul>
     *   <li>lowered (stone-on-slab, dy=-0.5) | flat (stone-on-stone, dy=0), horizontal seam → TRUE (both directions)</li>
     *   <li>both lowered → FALSE (no step)</li>
     *   <li>both flat → FALSE (no step)</li>
     *   <li>vertical face (DOWN onto the slab) → FALSE (horizontal-only by design)</li>
     *   <li>air neighbour → FALSE</li>
     * </ul>
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void slabHeightStepFacePredicate(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = fixtureTestOrigin(ctx);

        // Row A (z+0): lowered stone-on-slab (EAST is the flat neighbour).
        BlockPos aLow = origin.add(0, 1, 0);
        world.setBlockState(origin.add(0, 0, 0), slab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        world.setBlockState(aLow, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos aFlat = origin.add(1, 1, 0);
        world.setBlockState(origin.add(1, 0, 0), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(aFlat, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        double lowDy = SlabSupport.getYOffset(world, aLow, world.getBlockState(aLow));
        double flatDy = SlabSupport.getYOffset(world, aFlat, world.getBlockState(aFlat));
        ctx.assertTrue(lowDy == -0.5, "row A lowered stone-on-slab should be dy=-0.5; got " + lowDy);
        ctx.assertTrue(flatDy == 0.0, "row A flat stone-on-stone should be dy=0; got " + flatDy);

        ctx.assertTrue(
                SlabSupport.isSlabHeightStepFace(world, aLow, world.getBlockState(aLow), Direction.EAST),
                "lowered|flat horizontal seam must be a step face (EAST from the lowered block)");
        ctx.assertTrue(
                SlabSupport.isSlabHeightStepFace(world, aFlat, world.getBlockState(aFlat), Direction.WEST),
                "lowered|flat horizontal seam must be a step face (WEST from the flat block)");

        // Vertical face: DOWN from the lowered stone onto its slab — different heights, but
        // horizontal-only ⇒ FALSE.
        ctx.assertTrue(
                !SlabSupport.isSlabHeightStepFace(world, aLow, world.getBlockState(aLow), Direction.DOWN),
                "vertical (DOWN) face must never be a step face (horizontal-only)");
        // Air neighbour (SOUTH into the empty gap between rows) ⇒ FALSE.
        ctx.assertTrue(
                !SlabSupport.isSlabHeightStepFace(world, aLow, world.getBlockState(aLow), Direction.SOUTH),
                "air neighbour must not be a step face");

        // Row B (z+2): two adjacent lowered stones-on-slabs ⇒ no step.
        BlockPos bLow1 = origin.add(0, 1, 2);
        BlockPos bLow2 = origin.add(1, 1, 2);
        world.setBlockState(origin.add(0, 0, 2), slab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        world.setBlockState(bLow1, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(origin.add(1, 0, 2), slab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        world.setBlockState(bLow2, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.getYOffset(world, bLow1, world.getBlockState(bLow1)) == -0.5
                        && SlabSupport.getYOffset(world, bLow2, world.getBlockState(bLow2)) == -0.5,
                "row B both stones should be lowered -0.5");
        ctx.assertTrue(
                !SlabSupport.isSlabHeightStepFace(world, bLow1, world.getBlockState(bLow1), Direction.EAST),
                "both-lowered adjacent blocks must NOT be a step face");

        // Row C (z+4): two adjacent flat stones-on-stone ⇒ no step.
        BlockPos cFlat1 = origin.add(0, 1, 4);
        BlockPos cFlat2 = origin.add(1, 1, 4);
        world.setBlockState(origin.add(0, 0, 4), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(cFlat1, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(origin.add(1, 0, 4), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(cFlat2, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(
                !SlabSupport.isSlabHeightStepFace(world, cFlat1, world.getBlockState(cFlat1), Direction.EAST),
                "both-flat adjacent blocks must NOT be a step face");

        ctx.complete();
    }

    // ===== Adversarial bug-hunt (ported scenarios, run against the real 1.21.1 branch) =====
    // We are hunting BUGS: each asserts the geometrically-correct (flush, no float/sink) dy.
    // A failure = a real visual defect (gap / sink / inconsistency), not a style choice.

    /**
     * Compound vertical stack: bottom slab / stone / bottom slab / stone. Each layer rests on the
     * rendered top of the one below, so the top stone must compound to dy=-1.0 to sit FLUSH on the
     * lowered L2 slab. If it reads -0.5 it FLOATS 0.5 above the slab (a visible gap).
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void advCompoundStackTopMustBeFlush(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = fixtureTestOrigin(ctx);
        world.setBlockState(base, slab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);          // L0 slab (air below → dy 0)
        world.setBlockState(base.up(1), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS); // L1 stone on slab → -0.5
        world.setBlockState(base.up(2), slab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);    // L2 slab on lowered stone → -0.5
        world.setBlockState(base.up(3), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS); // L3 stone on lowered slab → -1.0

        double l1 = SlabSupport.getYOffset(world, base.up(1), world.getBlockState(base.up(1)));
        double l2 = SlabSupport.getYOffset(world, base.up(2), world.getBlockState(base.up(2)));
        double l3 = SlabSupport.getYOffset(world, base.up(3), world.getBlockState(base.up(3)));
        ctx.assertTrue(l1 == -0.5, "L1 stone on bottom slab should be -0.5; got " + l1);
        ctx.assertTrue(l2 == -0.5, "L2 slab on lowered stone should be -0.5; got " + l2);
        // The smoking gun: flush needs -1.0. -0.5 ⇒ float (gap 0.5).
        ctx.assertTrue(l3 == -1.0,
                "FLOAT BUG: top stone on a lowered bottom slab must compound to -1.0 (flush); got "
                + l3 + " (gap=" + ((base.up(3).getY() + l3) - (base.up(2).getY() + 0.5 + l2)) + ")");
        ctx.complete();
    }

    /**
     * A full block resting on SOLID GROUND beside a lowered block must NOT sink. Only cantilevered
     * (air-below) blocks lower; a grounded neighbour must stay dy=0.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void advGroundedBesideLoweredMustNotSink(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = fixtureTestOrigin(ctx);
        // Lowered source: slab + stone.
        world.setBlockState(base, slab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        world.setBlockState(base.up(1), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        // Grounded neighbour (east): solid support + stone on top.
        world.setBlockState(base.east(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(base.east().up(1), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        double src = SlabSupport.getYOffset(world, base.up(1), world.getBlockState(base.up(1)));
        double grounded = SlabSupport.getYOffset(world, base.east().up(1), world.getBlockState(base.east().up(1)));
        ctx.assertTrue(src == -0.5, "source stone on slab should be -0.5; got " + src);
        ctx.assertTrue(grounded == 0.0,
                "SINK BUG: stone on solid ground beside a lowered block must stay dy=0; got " + grounded);
        ctx.complete();
    }

    /**
     * Cantilever propagation consistency: a 2-out air-below block connected through a 1-out block to
     * a lowered source must lower the SAME as the 1-out block (no mid-row pop). If the 2-out block
     * reads 0 while the 1-out reads -0.5, propagation "stops at one" (inconsistent canopy).
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void advCantileverTwoOutConsistent(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = fixtureTestOrigin(ctx);
        world.setBlockState(base, slab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);              // source slab
        world.setBlockState(base.up(1), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS); // source stone -0.5
        world.setBlockState(base.up(1).east(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);      // 1-out (air below)
        world.setBlockState(base.up(1).east(2), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);     // 2-out (air below)

        double oneOut = SlabSupport.getYOffset(world, base.up(1).east(), world.getBlockState(base.up(1).east()));
        double twoOut = SlabSupport.getYOffset(world, base.up(1).east(2), world.getBlockState(base.up(1).east(2)));
        ctx.assertTrue(oneOut == twoOut,
                "CANTILEVER INCONSISTENCY: 1-out=" + oneOut + " but 2-out=" + twoOut
                + " — a connected cantilever row must lower uniformly (no mid-row pop)");
        ctx.complete();
    }

    /**
     * STALE ANCHOR: an anchor recorded by a real placement must be cleared when the block is
     * removed (setBlockState AIR → onStateReplaced → removeAnchor). If it lingers, a DIFFERENT block
     * later placed in that cell (with no slab support) would inherit a phantom -0.5 lowering.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void advStaleAnchorClearedAfterAirReplace(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos slabPos = fixtureTestOrigin(ctx);
        BlockPos cell = slabPos.up(1);
        world.setBlockState(slabPos, slab(SlabType.BOTTOM), Block.NOTIFY_ALL);
        BlockState placed = authorBlock(world, cell, Blocks.STONE.getDefaultState()); // onPlaced → anchor -0.5
        ctx.assertTrue(SlabSupport.getYOffset(world, cell, placed) == -0.5,
                "setup: stone authored on a bottom slab should anchor -0.5");
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, cell), "setup: cell should be anchored");

        world.setBlockState(cell, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);   // remove → should clear anchor
        world.setBlockState(slabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL); // remove the slab too
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(world, cell),
                "STALE ANCHOR: anchor must clear when the block is removed; it lingered at " + cell.toShortString());

        // A fresh block in the same cell, now with NO slab below, must read flat.
        world.setBlockState(cell, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        double dy = SlabSupport.getYOffset(world, cell, world.getBlockState(cell));
        ctx.assertTrue(dy == 0.0,
                "STALE ANCHOR BUG: a new stone (no slab support) inherited a phantom lowering; dy=" + dy);
        ctx.complete();
    }

    /**
     * Break + re-place in the SAME cell must not accumulate stale state: after authoring a lowered
     * stone, removing it, then authoring a stone again with the slab still present, the cell stays a
     * single correct -0.5 anchor (not corrupted/doubled).
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void advRefillSameCellNoCorruption(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos slabPos = fixtureTestOrigin(ctx);
        BlockPos cell = slabPos.up(1);
        world.setBlockState(slabPos, slab(SlabType.BOTTOM), Block.NOTIFY_ALL);
        authorBlock(world, cell, Blocks.STONE.getDefaultState());
        world.setBlockState(cell, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        BlockState refilled = authorBlock(world, cell, Blocks.STONE.getDefaultState());
        double dy = SlabSupport.getYOffset(world, cell, refilled);
        ctx.assertTrue(dy == -0.5,
                "refilled stone on the (still-present) slab should anchor -0.5; got " + dy);

        // Now pull the slab: the anchor should hold the placed block's frozen height (no pop), but a
        // FRESH non-placed block elsewhere must be unaffected.
        world.setBlockState(slabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        double dyAfter = SlabSupport.getYOffset(world, cell, world.getBlockState(cell));
        ctx.assertTrue(dyAfter == -0.5,
                "placed block must keep its frozen -0.5 after the slab is pulled (no pop); got " + dyAfter);
        ctx.complete();
    }

    /**
     * Geometric recompute / no-stale: a NON-placed (setBlockState) cantilever block lowered off a
     * source must recompute to flat when the source is removed (it was never frozen/anchored).
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void advCantileverRecomputesAfterSourceBreak(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = fixtureTestOrigin(ctx);
        BlockPos source = base.up(1);
        BlockPos oneOut = source.east();
        world.setBlockState(base, slab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        world.setBlockState(source, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);  // geometric source -0.5
        world.setBlockState(oneOut, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);  // cantilever (air below) -0.5
        ctx.assertTrue(SlabSupport.getYOffset(world, oneOut, world.getBlockState(oneOut)) == -0.5,
                "setup: 1-out cantilever should be -0.5");

        world.setBlockState(source, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);     // break the source
        double dy = SlabSupport.getYOffset(world, oneOut, world.getBlockState(oneOut));
        ctx.assertTrue(dy == 0.0,
                "STALE GEOMETRIC: a non-placed cantilever must recompute to 0 when its source is gone; got " + dy);
        ctx.complete();
    }

    /**
     * Adjacent compound columns HOMOGENIZE (the consistent merge): placing a stone-on-slab column
     * beside a dy=-1.0 compound column makes the new column's slab side-merge so its top stone
     * compounds to -1.0 too — both tops end FLUSH at -1.0. No stable -1.0-vs-(-0.5) step forms, so
     * there is no compound ghost-window to miss (the predicate correctly returns FALSE: no step).
     * This is why the 1.21.11 boolean-flag "cull miss on compound step" does not apply on 1.21.1.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void advAdjacentCompoundColumnsHomogenizeFlush(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos o = fixtureTestOrigin(ctx);
        // Column A: slab/stone/slab/stone → top stone (A) = -1.0.
        world.setBlockState(o, slab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        world.setBlockState(o.up(1), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(o.up(2), slab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        world.setBlockState(o.up(3), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos aTop = o.up(3);
        // Column B beside A's top: a slab (y2) + stone (y3). The slab side-merges with A's lowered slab.
        world.setBlockState(aTop.down().east(), slab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        world.setBlockState(aTop.east(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos bTop = aTop.east();

        double aDy = SlabSupport.getYOffset(world, aTop, world.getBlockState(aTop));
        double bDy = SlabSupport.getYOffset(world, bTop, world.getBlockState(bTop));
        ctx.assertTrue(aDy == -1.0, "column A top should be compound -1.0; got " + aDy);
        ctx.assertTrue(bDy == -1.0,
                "MERGE BUG: a column placed beside a -1.0 compound column must homogenize to -1.0 (flush), got " + bDy);
        // Flush tops ⇒ no step ⇒ predicate must NOT relax the cull (no phantom window).
        ctx.assertTrue(
                !SlabSupport.isSlabHeightStepFace(world, aTop, world.getBlockState(aTop), Direction.EAST),
                "no step between two flush -1.0 tops; predicate must return false");
        ctx.complete();
    }

    private static BlockState slab(SlabType type) {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
    }

    private static BlockState authorBlock(ServerWorld world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
        state.getBlock().onPlaced(world, pos, state, null, ItemStack.EMPTY);
        return world.getBlockState(pos);
    }

    private static BlockPos authorLoweredBottomCarrier(ServerWorld world, BlockPos baseSlabPos) {
        world.setBlockState(baseSlabPos, slab(SlabType.BOTTOM), Block.NOTIFY_ALL);
        BlockPos loweredFullPos = baseSlabPos.up();
        authorBlock(world, loweredFullPos, Blocks.STONE.getDefaultState());
        BlockPos carrierPos = loweredFullPos.up();
        world.setBlockState(carrierPos, slab(SlabType.BOTTOM), Block.NOTIFY_ALL);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, carrierPos,
                world.getBlockState(carrierPos));
        return carrierPos;
    }

    private static ServerStateOverlapRow measureServerStateRow(
            ServerWorld world,
            String scenario,
            BlockPos supportPos,
            BlockPos objectPos,
            String expectedLaneName,
            double expectedServerDy,
            boolean requireAnchor,
            boolean forbidAnchor,
            boolean requireCarrier,
            boolean forbidCarrier,
            boolean requireCompoundAnchor,
            boolean requireFullBlockNoOverlap,
            String baseReason
    ) {
        BlockState supportState = world.getBlockState(supportPos);
        BlockState objectState = world.getBlockState(objectPos);
        double serverDy = objectState.isAir() ? Double.NaN : SlabSupport.getYOffset(world, objectPos, objectState);
        double supportDy = supportState.isAir() ? Double.NaN : SlabSupport.getYOffset(world, supportPos, supportState);
        double supportSurfaceOffset = supportSurfaceOffset(supportState);
        double supportTopWorldY = Double.isFinite(supportDy) && Double.isFinite(supportSurfaceOffset)
                ? supportPos.getY() + supportDy + supportSurfaceOffset
                : Double.NaN;
        double objectBottomWorldY = Double.isFinite(serverDy)
                ? objectPos.getY() + serverDy
                : Double.NaN;
        double serverOverlap = Double.isFinite(supportTopWorldY) && Double.isFinite(objectBottomWorldY)
                ? supportTopWorldY - objectBottomWorldY
                : Double.NaN;
        boolean anchorServer = SlabAnchorAttachment.isAnchored(world, objectPos);
        boolean compoundAnchorServer = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, objectPos);
        boolean carrierServer = supportState.getBlock() instanceof SlabBlock
                && (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, supportPos, supportState)
                || SlabSupport.isLoweredSideLaneSlabCarrier(world, supportPos, supportState));

        List<String> failures = new ArrayList<>();
        if (!near(serverDy, expectedServerDy)) {
            failures.add("serverDy_expected_" + text(expectedServerDy) + "_got_" + text(serverDy));
        }
        if (requireAnchor && !anchorServer) {
            failures.add("anchorServer_absent");
        }
        if (forbidAnchor && anchorServer) {
            failures.add("anchorServer_unexpected");
        }
        if (requireCarrier && !carrierServer) {
            failures.add("carrierServer_absent");
        }
        if (forbidCarrier && carrierServer) {
            failures.add("carrierServer_unexpected");
        }
        if (requireCompoundAnchor && !compoundAnchorServer) {
            failures.add("compoundAnchorServer_absent");
        }
        if (requireFullBlockNoOverlap) {
            if (!Double.isFinite(serverOverlap)) {
                failures.add("serverOverlap_unreadable");
            } else if (serverOverlap > MC1211_SERVER_STATE_EPSILON) {
                failures.add("serverOverlap_positive_" + text(serverOverlap));
            }
        }

        boolean laneLegal = failures.isEmpty();
        boolean deferredPolicy = DEFERRED_OVERLAP_SCENARIO.equals(scenario);
        String result;
        String reason;
        if (laneLegal) {
            result = "GREEN";
            reason = baseReason;
        } else if (deferredPolicy) {
            result = "DEFERRED";
            reason = baseReason
                    + ";deferredScenario=" + DEFERRED_OVERLAP_REASON
                    + ";failures=" + String.join(",", failures);
        } else {
            result = "RED";
            reason = baseReason + ";failures=" + String.join(",", failures);
        }

        return new ServerStateOverlapRow(
                scenario,
                supportPos,
                supportState.toString(),
                slabTypeName(supportState),
                objectPos,
                objectState.toString(),
                expectedLaneName,
                serverDy,
                supportDy,
                supportSurfaceOffset,
                supportTopWorldY,
                objectBottomWorldY,
                serverOverlap,
                anchorServer,
                compoundAnchorServer,
                carrierServer,
                laneLegal,
                result,
                reason);
    }

    private static double supportSurfaceOffset(BlockState supportState) {
        if (supportState == null || supportState.isAir()) {
            return Double.NaN;
        }
        if (supportState.getBlock() instanceof SlabBlock && supportState.contains(SlabBlock.TYPE)) {
            return SlabSupport.getSupportYOffset(supportState);
        }
        return 1.0d;
    }

    private static String slabTypeName(BlockState state) {
        if (state != null && state.getBlock() instanceof SlabBlock && state.contains(SlabBlock.TYPE)) {
            return state.get(SlabBlock.TYPE).name();
        }
        return "n/a";
    }

    private static boolean near(double actual, double expected) {
        return Double.isFinite(actual)
                && Double.isFinite(expected)
                && Math.abs(actual - expected) <= MC1211_SERVER_STATE_EPSILON;
    }

    private static String text(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        return String.format(java.util.Locale.ROOT, "%.6f", value);
    }

    private record ServerStateOverlapRow(
            String scenario,
            BlockPos supportPos,
            String supportState,
            String supportSlabType,
            BlockPos objectPos,
            String objectState,
            String expectedLaneName,
            double serverDy,
            double supportDy,
            double supportSurfaceOffset,
            double supportTopWorldY,
            double objectBottomWorldY,
            double serverOverlap,
            boolean anchorServer,
            boolean compoundAnchorServer,
            boolean carrierServer,
            boolean laneLegal,
            String result,
            String reason
    ) {
        private String toMarkerLine() {
            return "[MC1211_SERVER_STATE_OVERLAP_MATRIX_ROW]"
                    + " scenario=" + scenario
                    + " supportPos=" + supportPos.toShortString()
                    + " supportState=" + compact(supportState)
                    + " supportSlabType=" + supportSlabType
                    + " objectPos=" + objectPos.toShortString()
                    + " objectState=" + compact(objectState)
                    + " expectedLaneName=" + expectedLaneName
                    + " serverDy=" + text(serverDy)
                    + " supportDy=" + text(supportDy)
                    + " supportSurfaceOffset=" + text(supportSurfaceOffset)
                    + " supportTopWorldY=" + text(supportTopWorldY)
                    + " objectBottomWorldY=" + text(objectBottomWorldY)
                    + " serverOverlap=" + text(serverOverlap)
                    + " anchorServer=" + anchorServer
                    + " compoundAnchorServer=" + compoundAnchorServer
                    + " carrierServer=" + carrierServer
                    + " laneLegal=" + laneLegal
                    + " result=" + result
                    + " reason=" + compact(reason);
        }

        private static String compact(String value) {
            return value == null ? "null" : value.replace(' ', '_');
        }
    }
}
