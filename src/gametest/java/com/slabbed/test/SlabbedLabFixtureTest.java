package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.dev.SlabbedLabFixtures;
import com.slabbed.dev.SlabbedLabFixtures.LaneStatus;
import com.slabbed.dev.SlabbedLabFixtures.PlaceResult;
import com.slabbed.util.SlabSupport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class SlabbedLabFixtureTest {
    private static final double MC1211_SERVER_STATE_EPSILON = 1.0e-6d;
    private static final String DEFERRED_OVERLAP_SCENARIO = "LOWERED_TOP_SLAB_SIDE_LANE_STACK";
    private static final String DEFERRED_OVERLAP_REASON = "LOWERED_TOP_SLAB_SIDE_LANE_STACK_DEFERRED";
    private static final BlockPos FIXTURE_TEST_OFFSET = new BlockPos(0, 1, 0);

    private static BlockPos fixtureTestOrigin(GameTestHelper ctx) {
        return ctx.absolutePos(FIXTURE_TEST_OFFSET);
    }

    /**
     * Exercises the basic fixture lifecycle on all three lanes (placement assertions)
     * and the full break/restore/pulse cycle on the FULL lane.
     *
     * <p>Uses {@code fabric-gametest-api-v1:empty} (built-in 8×8×8 all-air structure).
     * Fixture footprint: X=0..4, Y=0..1, Z=0..1 (pulse at Z=1) — fits within bounds.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void labSupportCycle(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
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
                world.getBlockState(fullSupportPos).is(Blocks.STONE),
                "FULL support should be stone after restoreSupport");

        // --- 5. Neighbor-update pulse on FULL, then assert support is still stable ---
        PlaceResult pulse = SlabbedLabFixtures.neighborUpdatePulse(world, origin, "FULL");
        ctx.assertTrue(pulse.ok(), "neighborUpdatePulse(FULL) failed: " + pulse.error());

        LaneStatus postPulse = SlabbedLabFixtures.queryStatus(world, origin, "FULL").get(0);
        ctx.assertTrue(postPulse.supportMatch(),
                "FULL support should still match after pulse: expected "
                        + postPulse.expectedSupport() + ", got " + postPulse.actualSupport());

        ctx.succeed();
    }

    /**
     * Server-state-only MC 1.21.1 proof: deterministic authored lanes must resolve
     * to legal server dy / attachment state without full-block vertical overlap.
     *
     * <p>This deliberately does not measure model dy, renderer dy, outline,
     * raycast, client attachment sync, render-view bridge lookup, or reload.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void mc1211ServerStateOverlapMatrix(GameTestHelper ctx) {
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
            ctx.succeed();
            return;
        }

        ServerLevel world = ctx.getLevel();
        BlockPos origin = ctx.absolutePos(BlockPos.ZERO);

        List<ServerStateOverlapRow> rows = new ArrayList<>();

        BlockPos bottomSupport = origin.offset(0, 0, 0);
        BlockPos bottomObject = bottomSupport.above();
        world.setBlock(bottomSupport, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        authorBlock(world, bottomObject, Blocks.STONE.defaultBlockState());
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

        BlockPos topSupport = origin.offset(2, 0, 0);
        BlockPos topObject = topSupport.above();
        world.setBlock(topSupport, slab(SlabType.TOP), Block.UPDATE_ALL);
        authorBlock(world, topObject, Blocks.STONE.defaultBlockState());
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

        BlockPos doubleSupport = origin.offset(4, 0, 0);
        BlockPos doubleObject = doubleSupport.above();
        world.setBlock(doubleSupport, slab(SlabType.DOUBLE), Block.UPDATE_ALL);
        authorBlock(world, doubleObject, Blocks.STONE.defaultBlockState());
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

        BlockPos loweredCarrier = authorLoweredBottomCarrier(world, origin.offset(0, 0, 2));
        BlockPos compoundObject = loweredCarrier.above();
        authorBlock(world, compoundObject, Blocks.STONE.defaultBlockState());
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

        BlockPos persistedCarrier = authorLoweredBottomCarrier(world, origin.offset(2, 0, 2));
        BlockPos persistedObject = persistedCarrier.above();
        authorBlock(world, persistedObject, Blocks.STONE.defaultBlockState());
        world.setBlock(persistedCarrier, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
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

        BlockPos sideBase = origin.offset(4, 0, 2);
        world.setBlock(sideBase, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        BlockPos sideLoweredFull = sideBase.above();
        authorBlock(world, sideLoweredFull, Blocks.STONE.defaultBlockState());
        BlockPos sideLaneSupport = sideLoweredFull.east();
        world.setBlock(sideLaneSupport, slab(SlabType.DOUBLE), Block.UPDATE_ALL);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, sideLaneSupport,
                world.getBlockState(sideLaneSupport));
        BlockPos sideLaneObject = sideLaneSupport.above();
        authorBlock(world, sideLaneObject, Blocks.STONE.defaultBlockState());
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

        BlockPos decorativeCarrier = authorLoweredBottomCarrier(world, origin.offset(0, 0, 5));
        BlockPos wallPos = decorativeCarrier.above();
        world.setBlock(wallPos, Blocks.COBBLESTONE_WALL.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos lanternPos = wallPos.above();
        world.setBlock(lanternPos, Blocks.LANTERN.defaultBlockState(), Block.UPDATE_ALL);
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

        BlockPos mergeSourceSlab = origin.offset(0, 0, 7);
        BlockPos mergeSourceFull = mergeSourceSlab.above();
        world.setBlock(mergeSourceSlab, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        authorBlock(world, mergeSourceFull, Blocks.STONE.defaultBlockState());
        BlockPos vanillaSupport = mergeSourceSlab.east();
        BlockPos vanillaSupportedSlab = vanillaSupport.above();
        world.setBlock(vanillaSupport, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(vanillaSupportedSlab, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
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
        ctx.succeed();
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
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void outlineRaycastParity(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos origin = fixtureTestOrigin(ctx);

        // Place the 3-lane fixture; BOTTOM_SLAB support lands at origin+(2,0,0).
        PlaceResult placed = SlabbedLabFixtures.placeBasicFixture(world, origin);
        ctx.assertTrue(placed.ok(), "placeBasicFixture failed: " + placed.error());

        // Place a composter directly above the BOTTOM_SLAB lane support.
        // Composter.getRaycastShape returns VoxelShapes.fullCube() (non-empty, minY=0.0).
        // SlabSupport.getYOffset returns -0.5 via shouldOffset → hasSlabInColumn.
        BlockPos testPos = origin.offset(2, 1, 0);
        world.setBlock(testPos, Blocks.COMPOSTER.defaultBlockState(), Block.UPDATE_CLIENTS);

        BlockState testState = world.getBlockState(testPos);
        ctx.assertTrue(testState.is(Blocks.COMPOSTER), "composter not present at test position");

        VoxelShape outline = testState.getShape(world, testPos, CollisionContext.empty());
        VoxelShape raycast = testState.getInteractionShape(world, testPos);

        double outlineMinY = outline.bounds().minY;
        double raycastMinY = raycast.bounds().minY;

        // Prove the offset is applied (not vacuously equal at the unshifted 0.0).
        ctx.assertTrue(outlineMinY < 0.0,
                "outline not slabbed-offset: expected minY < 0, got " + outlineMinY);

        // Parity: raycast offset must equal outline offset.
        ctx.assertTrue(outlineMinY == raycastMinY,
                "outline/raycast parity broken: outline minY=" + outlineMinY
                        + ", raycast minY=" + raycastMinY);

        ctx.succeed();
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
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void blockEntityFullCubeSitsOnSlab(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos origin = fixtureTestOrigin(ctx);

        PlaceResult placed = SlabbedLabFixtures.placeBasicFixture(world, origin);
        ctx.assertTrue(placed.ok(), "placeBasicFixture failed: " + placed.error());

        BlockPos testPos = origin.offset(2, 1, 0); // above BOTTOM_SLAB lane
        world.setBlock(testPos, Blocks.JUKEBOX.defaultBlockState(), Block.UPDATE_CLIENTS);

        BlockState state = world.getBlockState(testPos);
        ctx.assertTrue(state.is(Blocks.JUKEBOX), "jukebox not present at test position");

        double dy = SlabSupport.getYOffset(world, testPos, state);
        ctx.assertTrue(dy == -0.5,
                "jukebox above BOTTOM_SLAB should lower; dy=" + dy
                + " (isSlabSitCandidate BlockEntityProvider path regressed)");

        VoxelShape outline = state.getShape(world, testPos, CollisionContext.empty());
        double minY = outline.bounds().minY;
        ctx.assertTrue(minY == -0.5,
                "jukebox outline minY should be -0.5, got " + minY);

        // Contract: isLoweredBlockEntityVisual must agree for every BE block.
        ctx.assertTrue(
                SlabSupport.isLoweredBlockEntityVisual(world, testPos, state),
                "isLoweredBlockEntityVisual must be true for jukebox above BOTTOM_SLAB");

        ctx.succeed();
    }

    /**
     * Canonical intent: ordinary solid cubes SHOULD inherit -0.5 offset from
     * the generic slab-column walk when placed above a bottom slab support.
     *
     * <p>This is the global slab support policy: ordinary full blocks anchor/lower
     * onto slabs. The previous selective-only policy that excluded solid cubes
     * has been retired.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void solidCubeLowersOverSlab(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos origin = fixtureTestOrigin(ctx);

        PlaceResult placed = SlabbedLabFixtures.placeBasicFixture(world, origin);
        ctx.assertTrue(placed.ok(), "placeBasicFixture failed: " + placed.error());

        BlockPos testPos = origin.offset(2, 1, 0); // above BOTTOM_SLAB lane
        world.setBlock(testPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);

        BlockState state = world.getBlockState(testPos);
        ctx.assertTrue(state.is(Blocks.STONE), "stone not present at test position");

        double dy = SlabSupport.getYOffset(world, testPos, state);
        ctx.assertTrue(dy == -0.5, "stone should lower over slab column; dy=" + dy);

        VoxelShape outline = state.getShape(world, testPos, CollisionContext.empty());
        ctx.assertTrue(outline.bounds().minY == -0.5,
                "stone outline minY should be -0.5, got " + outline.bounds().minY);

        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void loweredFullBlockCollisionStaysWithinCell(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos block = slab.above();

        world.setBlock(slab, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(block, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockState state = world.getBlockState(block);
        double dy = SlabSupport.getYOffset(world, block, state);
        ctx.assertTrue(dy < -MC1211_SERVER_STATE_EPSILON,
                "P26 collision setup: stone above bottom slab must lower, dy=" + dy);

        VoxelShape collision = state.getCollisionShape(world, block, CollisionContext.empty());
        double minY = collision.isEmpty() ? 0.0d : collision.min(Direction.Axis.Y);
        ctx.assertTrue(minY >= -MC1211_SERVER_STATE_EPSILON,
                "P26 collision: lowered full block collision must stay within-cell, minY=" + minY);

        AABB playerBox = new AABB(
                block.getX() + 0.2d, block.getY() + 0.01d, block.getZ() + 0.2d,
                block.getX() + 0.8d, block.getY() + 1.81d, block.getZ() + 0.8d);
        ctx.assertTrue(!world.noCollision(playerBox),
                "P26 collision: broadphase must collide with a player-sized box on the lowered block cell");

        System.out.println("[NEOFORGE_P26_LOWERED_COLLISION_ROW]"
                + " dy=" + text(dy)
                + " collisionMinY=" + text(minY)
                + " broadphaseCollision=true result=GREEN");
        System.out.println("[NEOFORGE_P26_LOWERED_COLLISION_SUMMARY]"
                + " rows=1 result=GREEN proofScope=server_lowered_full_block_collision_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void compoundMinusOneBlockIsSolidWhereDrawn(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos base = ctx.absolutePos(new BlockPos(2, 1, 2));
        world.setBlock(base, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(base.above(1), slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(base.above(3), slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        BlockPos top = base.above(4);
        world.setBlock(top, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        SlabAnchorAttachment.addAnchor(world, top, world.getBlockState(top));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(world, top, world.getBlockState(top));
        double dy = SlabSupport.getYOffset(world, top, world.getBlockState(top));
        ctx.assertTrue(near(dy, -1.0d),
                "P26 collision setup: compound block dy expected -1.0, got " + dy);

        world.setBlock(base.above(3), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        AABB inVisual = new AABB(
                top.getX() + 0.3d, top.getY() - 0.9d, top.getZ() + 0.3d,
                top.getX() + 0.7d, top.getY() - 0.6d, top.getZ() + 0.7d);
        ctx.assertTrue(!world.noCollision(inVisual),
                "P26 collision: compound -1.0 block must be solid where visually drawn");

        System.out.println("[NEOFORGE_P26_COMPOUND_COLLISION_ROW]"
                + " dy=" + text(dy)
                + " visualRegionCollision=true result=GREEN");
        System.out.println("[NEOFORGE_P26_COMPOUND_COLLISION_SUMMARY]"
                + " rows=1 result=GREEN proofScope=server_compound_minus_one_collision_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void loweredScaffoldingSideInteriorStaysPassThrough(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos scaffoldingPos = slab.above();
        world.setBlock(slab, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(scaffoldingPos, stableScaffoldingState(), Block.UPDATE_ALL);

        BlockState scaffolding = world.getBlockState(scaffoldingPos);
        double dy = SlabSupport.getYOffset(world, scaffoldingPos, scaffolding);
        ctx.assertTrue(near(dy, -0.5d),
                "P26 scaffolding setup: scaffolding over bottom slab must lower -0.5, got " + dy);

        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(scaffoldingPos.getX() + 0.5d, scaffoldingPos.getY() - 0.25d, scaffoldingPos.getZ() + 0.5d);
        AABB sideInterior = new AABB(
                scaffoldingPos.getX() + 0.25d, scaffoldingPos.getY() - 0.45d, scaffoldingPos.getZ() + 0.25d,
                scaffoldingPos.getX() + 0.75d, scaffoldingPos.getY() - 0.05d, scaffoldingPos.getZ() + 0.75d);
        boolean passThrough = world.noCollision(player, sideInterior);
        ctx.assertTrue(passThrough,
                "P26 scaffolding collision: lowered side/interior must stay pass-through");

        System.out.println("[NEOFORGE_P26_SCAFFOLDING_PASS_ROW]"
                + " dy=" + text(dy)
                + " passThrough=true result=GREEN");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void loweredScaffoldingDoesNotInjectSolidHangingCollision(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos scaffoldingPos = slab.above();
        world.setBlock(slab, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(scaffoldingPos, stableScaffoldingState(), Block.UPDATE_ALL);

        VoxelShape merged = SlabSupport.withHangingLoweredCollisionFromAbove(Shapes.empty(), world, slab);
        ctx.assertTrue(merged.isEmpty(),
                "P26 scaffolding collision: lowered scaffolding must not inject solid hanging collision");

        System.out.println("[NEOFORGE_P26_SCAFFOLDING_HELPER_ROW]"
                + " helperEmpty=true result=GREEN");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void loweredScaffoldingVisualVolumeIsClimbable(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos scaffoldingPos = slab.above();
        world.setBlock(slab, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(scaffoldingPos, stableScaffoldingState(), Block.UPDATE_ALL);

        BlockState scaffolding = world.getBlockState(scaffoldingPos);
        double dy = SlabSupport.getYOffset(world, scaffoldingPos, scaffolding);
        ctx.assertTrue(near(dy, -0.5d),
                "P26 scaffolding setup: scaffolding over bottom slab must lower -0.5, got " + dy);

        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(scaffoldingPos.getX() + 0.5d, scaffoldingPos.getY() - 0.25d, scaffoldingPos.getZ() + 0.5d);
        AABB visualScaffoldingVolume = new AABB(scaffoldingPos).move(0.0d, dy, 0.0d);
        ctx.assertTrue(player.getBoundingBox().intersects(visualScaffoldingVolume),
                "P26 scaffolding setup: mock player must intersect lowered visual scaffolding volume");
        ctx.assertTrue(player.onClimbable(),
                "P26 scaffolding collision: player inside lowered visual volume must count as climbable");

        System.out.println("[NEOFORGE_P26_SCAFFOLDING_CLIMB_ROW]"
                + " dy=" + text(dy)
                + " climbable=true result=GREEN");
        System.out.println("[NEOFORGE_P26_SCAFFOLDING_COLLISION_SUMMARY]"
                + " rows=3 result=GREEN proofScope=server_lowered_scaffolding_collision_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void loweredStairCollisionFollowsVisualStepableHeight(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos stair = slab.above();
        world.setBlock(slab, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(stair, Blocks.OAK_STAIRS.defaultBlockState(), Block.UPDATE_ALL);

        assertLoweredStairCollisionFollowsVisual(ctx, world, stair, "direct stair on bottom slab");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void chainedLoweredStairCollisionFollowsVisualStepableHeight(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos support = slab.above();
        BlockPos stair = support.above();
        world.setBlock(slab, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(support, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(stair, Blocks.OAK_STAIRS.defaultBlockState(), Block.UPDATE_ALL);

        assertBlockDy(ctx, world, support, Blocks.OAK_PLANKS, -0.5d,
                "P26 stair setup: lowered chained support");
        assertLoweredStairCollisionFollowsVisual(ctx, world, stair, "chained stair on lowered support");
        System.out.println("[NEOFORGE_P26_STAIR_COLLISION_SUMMARY]"
                + " rows=2 result=GREEN proofScope=server_lowered_stair_collision_only");
        ctx.succeed();
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
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void carpetOutlineNotDoubled(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos origin = fixtureTestOrigin(ctx);

        // Place the 3-lane fixture; BOTTOM_SLAB support lands at origin+(2,0,0).
        PlaceResult placed = SlabbedLabFixtures.placeBasicFixture(world, origin);
        ctx.assertTrue(placed.ok(), "placeBasicFixture failed: " + placed.error());

        // Place white carpet directly above the BOTTOM_SLAB lane support.
        // setBlockState bypasses canPlaceAt, so carpet lands regardless of support rules.
        BlockPos carpetPos = origin.offset(2, 1, 0);
        world.setBlock(carpetPos, Blocks.WHITE_CARPET.defaultBlockState(), Block.UPDATE_CLIENTS);

        BlockState carpetState = world.getBlockState(carpetPos);
        ctx.assertTrue(carpetState.is(Blocks.WHITE_CARPET), "white carpet not present at test position");
        ctx.assertTrue(carpetState.getBlock() instanceof CarpetBlock, "block is not a CarpetBlock instance");

        VoxelShape outline = carpetState.getShape(world, carpetPos, CollisionContext.empty());
        double minY = outline.bounds().minY;

        // Server: CarpetDyShapeMixin is client-only; CarpetBlockMixin.slabbed$offsetShape
        // is removed. No server-side offset → minY must be 0.0 (unmodified carpet shape).
        ctx.assertTrue(minY == 0.0,
                "server carpet outline should be unmodified (minY=0.0), got " + minY
                + ". If -0.5: server-side offset still active. If -1.0: double-offset.");

        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void carpetSurvivesOnSlabTops(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(2, 1, 2));
        BlockPos carpet = ctx.absolutePos(new BlockPos(2, 2, 2));

        world.setBlock(support, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(carpet, Blocks.WHITE_CARPET.defaultBlockState(), Block.UPDATE_ALL);
        ctx.assertTrue(world.getBlockState(carpet).canSurvive(world, carpet),
                "P26 carpet survival: white_carpet must survive on a bottom-slab top");

        world.setBlock(support, slab(SlabType.TOP), Block.UPDATE_ALL);
        ctx.assertTrue(world.getBlockState(carpet).canSurvive(world, carpet),
                "P26 carpet survival: white_carpet must survive on a top-slab top");

        System.out.println("[NEOFORGE_P26_CARPET_SURVIVAL_ROW]"
                + " bottomSlab=true topSlab=true result=GREEN");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void carpetDoesNotPopOnNeighbourUpdateOverSlab(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(2, 1, 2));
        BlockPos carpet = ctx.absolutePos(new BlockPos(2, 2, 2));

        world.setBlock(support, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(carpet, Blocks.WHITE_CARPET.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos north = carpet.north();
        BlockState after = world.getBlockState(carpet).updateShape(
                Direction.NORTH, world.getBlockState(north), world, carpet, north);

        ctx.assertTrue(!after.isAir(),
                "P26 carpet survival: carpet over a slab must not pop on neighbor update");
        System.out.println("[NEOFORGE_P26_CARPET_UPDATE_ROW] afterAir=false result=GREEN");
        System.out.println("[NEOFORGE_P26_CARPET_SURVIVAL_SUMMARY]"
                + " rows=2 result=GREEN proofScope=server_carpet_slab_survival_update_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void redstoneWireSurvivesOnSlabTops(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(2, 1, 2));
        BlockPos wire = ctx.absolutePos(new BlockPos(2, 2, 2));

        world.setBlock(support, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(wire, Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
        ctx.assertTrue(world.getBlockState(wire).canSurvive(world, wire),
                "P26 redstone survival: redstone_wire must survive on a bottom-slab top");

        world.setBlock(support, slab(SlabType.TOP), Block.UPDATE_ALL);
        ctx.assertTrue(world.getBlockState(wire).canSurvive(world, wire),
                "P26 redstone survival: redstone_wire must survive on a top-slab top");

        world.setBlock(support, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        ctx.assertTrue(world.getBlockState(wire).isAir(),
                "P26 redstone survival: placed redstone_wire must pop when support is removed");
        ctx.assertTrue(!Blocks.REDSTONE_WIRE.defaultBlockState().canSurvive(world, wire),
                "P26 redstone survival: fresh redstone_wire must not survive over air");

        System.out.println("[NEOFORGE_P26_REDSTONE_SURVIVAL_ROW]"
                + " bottomSlab=true topSlab=true poppedOverAir=true freshOverAir=false result=GREEN");
        System.out.println("[NEOFORGE_P26_REDSTONE_SURVIVAL_SUMMARY]"
                + " rows=1 result=GREEN proofScope=server_redstone_slab_survival_air_boundary_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void flowerPotSurvivesOnSlabTop(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(2, 1, 2));
        BlockPos pot = ctx.absolutePos(new BlockPos(2, 2, 2));

        world.setBlock(support, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(pot, Blocks.FLOWER_POT.defaultBlockState(), Block.UPDATE_ALL);
        ctx.assertTrue(world.getBlockState(pot).canSurvive(world, pot),
                "P26 flower pot survival: flower_pot must survive on a bottom-slab top");

        world.setBlock(support, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        BlockState after = world.getBlockState(pot);
        System.out.println("[NEOFORGE_P26_FLOWER_POT_SURVIVAL_ROW]"
                + " bottomSlab=true"
                + " afterSupportRemoved=" + after.getBlock()
                + " canSurviveWithAirBelow=" + after.canSurvive(world, pot)
                + " result=GREEN");
        System.out.println("[NEOFORGE_P26_FLOWER_POT_SURVIVAL_SUMMARY]"
                + " rows=1 result=GREEN proofScope=server_flower_pot_supported_survival_only");
        ctx.succeed();
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
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void frozenFlatBlockStaysFlatWhenSlabAddedBelow(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos origin = fixtureTestOrigin(ctx);

        // Floating spot with air directly below (no slab in the column at placement time).
        BlockPos blockPos = origin.offset(2, 3, 0);
        BlockPos belowPos = blockPos.below();
        world.setBlock(belowPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        // REAL placement: onPlaced → BlockOnPlacedAnchorMixin → freezeLoweredOnPlace.
        // Air below ⇒ dy=0 ⇒ structural stone recorded frozen-flat (not anchored).
        BlockState placed = authorBlock(world, blockPos, Blocks.STONE.defaultBlockState());
        ctx.assertTrue(placed.is(Blocks.STONE), "stone not present at test position");
        ctx.assertTrue(SlabAnchorAttachment.isFrozenFlat(world, blockPos),
                "stone placed flat (air below) must be recorded frozen-flat by onPlaced");
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(world, blockPos),
                "flat-placed stone must NOT be anchored (it was never lowered)");
        ctx.assertTrue(SlabSupport.getYOffset(world, blockPos, placed) == 0.0,
                "flat-placed stone dy must be 0 before any slab is added");

        // THE VIOLATION: place a bottom slab directly under the now-floating block.
        world.setBlock(belowPos, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        ctx.assertTrue(world.getBlockState(belowPos).getBlock() instanceof SlabBlock,
                "bottom slab not present below test position");

        // LAW: the placed block stays put — dy must remain 0.0 (no inherited lowering).
        double dy = SlabSupport.getYOffset(world, blockPos, placed);
        ctx.assertTrue(dy == 0.0,
                "LAW: flat-placed stone must stay at dy=0 after a bottom slab is placed under it; got dy=" + dy);

        VoxelShape outline = placed.getShape(world, blockPos, CollisionContext.empty());
        ctx.assertTrue(outline.bounds().minY == 0.0,
                "flat-placed stone outline minY must stay 0.0 after slab added; got "
                + outline.bounds().minY);

        ctx.succeed();
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
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void unfrozenBlockLowersWhenSlabAddedBelow(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos origin = fixtureTestOrigin(ctx);

        BlockPos blockPos = origin.offset(2, 3, 0);
        BlockPos belowPos = blockPos.below();
        world.setBlock(belowPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        // setBlockState bypasses onPlaced ⇒ NO frozen-flat marker.
        world.setBlock(blockPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        BlockState placed = world.getBlockState(blockPos);
        ctx.assertTrue(placed.is(Blocks.STONE), "stone not present at test position");
        ctx.assertTrue(!SlabAnchorAttachment.isFrozenFlat(world, blockPos),
                "setBlockState stone must NOT be frozen-flat (no onPlaced ran)");

        world.setBlock(belowPos, slab(SlabType.BOTTOM), Block.UPDATE_ALL);

        double dy = SlabSupport.getYOffset(world, blockPos, placed);
        ctx.assertTrue(dy == -0.5,
                "control: unfrozen stone over a bottom slab should lower to -0.5; got dy=" + dy);

        ctx.succeed();
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
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void slabHeightStepFacePredicate(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos origin = fixtureTestOrigin(ctx);

        // Row A (z+0): lowered stone-on-slab (EAST is the flat neighbour).
        BlockPos aLow = origin.offset(0, 1, 0);
        world.setBlock(origin.offset(0, 0, 0), slab(SlabType.BOTTOM), Block.UPDATE_CLIENTS);
        world.setBlock(aLow, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        BlockPos aFlat = origin.offset(1, 1, 0);
        world.setBlock(origin.offset(1, 0, 0), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        world.setBlock(aFlat, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);

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
        BlockPos bLow1 = origin.offset(0, 1, 2);
        BlockPos bLow2 = origin.offset(1, 1, 2);
        world.setBlock(origin.offset(0, 0, 2), slab(SlabType.BOTTOM), Block.UPDATE_CLIENTS);
        world.setBlock(bLow1, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        world.setBlock(origin.offset(1, 0, 2), slab(SlabType.BOTTOM), Block.UPDATE_CLIENTS);
        world.setBlock(bLow2, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        ctx.assertTrue(SlabSupport.getYOffset(world, bLow1, world.getBlockState(bLow1)) == -0.5
                        && SlabSupport.getYOffset(world, bLow2, world.getBlockState(bLow2)) == -0.5,
                "row B both stones should be lowered -0.5");
        ctx.assertTrue(
                !SlabSupport.isSlabHeightStepFace(world, bLow1, world.getBlockState(bLow1), Direction.EAST),
                "both-lowered adjacent blocks must NOT be a step face");

        // Row C (z+4): two adjacent flat stones-on-stone ⇒ no step.
        BlockPos cFlat1 = origin.offset(0, 1, 4);
        BlockPos cFlat2 = origin.offset(1, 1, 4);
        world.setBlock(origin.offset(0, 0, 4), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        world.setBlock(cFlat1, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        world.setBlock(origin.offset(1, 0, 4), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        world.setBlock(cFlat2, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        ctx.assertTrue(
                !SlabSupport.isSlabHeightStepFace(world, cFlat1, world.getBlockState(cFlat1), Direction.EAST),
                "both-flat adjacent blocks must NOT be a step face");

        ctx.succeed();
    }

    // ===== Adversarial bug-hunt (ported scenarios, run against the real 1.21.1 branch) =====
    // We are hunting BUGS: each asserts the geometrically-correct (flush, no float/sink) dy.
    // A failure = a real visual defect (gap / sink / inconsistency), not a style choice.

    /**
     * Compound vertical stack: bottom slab / stone / bottom slab / stone. Each layer rests on the
     * rendered top of the one below, so the top stone must compound to dy=-1.0 to sit FLUSH on the
     * lowered L2 slab. If it reads -0.5 it FLOATS 0.5 above the slab (a visible gap).
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void advCompoundStackTopMustBeFlush(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos base = fixtureTestOrigin(ctx);
        world.setBlock(base, slab(SlabType.BOTTOM), Block.UPDATE_CLIENTS);          // L0 slab (air below → dy 0)
        world.setBlock(base.above(1), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS); // L1 stone on slab → -0.5
        world.setBlock(base.above(2), slab(SlabType.BOTTOM), Block.UPDATE_CLIENTS);    // L2 slab on lowered stone → -0.5
        world.setBlock(base.above(3), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS); // L3 stone on lowered slab → -1.0

        double l1 = SlabSupport.getYOffset(world, base.above(1), world.getBlockState(base.above(1)));
        double l2 = SlabSupport.getYOffset(world, base.above(2), world.getBlockState(base.above(2)));
        double l3 = SlabSupport.getYOffset(world, base.above(3), world.getBlockState(base.above(3)));
        ctx.assertTrue(l1 == -0.5, "L1 stone on bottom slab should be -0.5; got " + l1);
        ctx.assertTrue(l2 == -0.5, "L2 slab on lowered stone should be -0.5; got " + l2);
        // The smoking gun: flush needs -1.0. -0.5 ⇒ float (gap 0.5).
        ctx.assertTrue(l3 == -1.0,
                "FLOAT BUG: top stone on a lowered bottom slab must compound to -1.0 (flush); got "
                + l3 + " (gap=" + ((base.above(3).getY() + l3) - (base.above(2).getY() + 0.5 + l2)) + ")");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void useOnSlabBesideCompoundOwnerPlacesClickedSideNotOpposite(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        buildCompoundMinusOne(ctx);
        BlockPos owner = ctx.absolutePos(new BlockPos(2, 5, 2));
        SlabAnchorAttachment.addAnchor(world, owner, world.getBlockState(owner));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(world, owner, world.getBlockState(owner));

        Player player = mockPlayerNear(ctx, ctx.absolutePos(new BlockPos(3, 6, 2)));
        placeSlabVia(player, owner, Direction.EAST, eastHit(owner, 0.25d));

        BlockPos eastCell = ctx.absolutePos(new BlockPos(3, 5, 2));
        BlockPos westCell = ctx.absolutePos(new BlockPos(1, 5, 2));
        boolean east = world.getBlockState(eastCell).getBlock() instanceof SlabBlock;
        boolean west = world.getBlockState(westCell).getBlock() instanceof SlabBlock;
        System.out.println("[NEOFORGE_COMPOUND_SIDE_OPPOSITE_PLACEMENT_ROW] face=EAST clicked=east"
                + " opposite=west eastSlab=" + east + " westSlab=" + west);
        ctx.assertTrue(!west,
                "P26-9: slab placed on the opposite west side of the clicked east face");
        ctx.assertTrue(east,
                "P26-9: slab clicking the east face of a -1.0 compound owner must land on the east side");
        System.out.println("[NEOFORGE_COMPOUND_SIDE_OPPOSITE_PLACEMENT_SUMMARY]"
                + " rows=1 green=1 red=0 proofScope=server_targeting_placement_authority_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void useOnSlabBesideCompoundHonestBandLowerHalf(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        buildCompoundMinusOne(ctx);
        BlockPos owner = ctx.absolutePos(new BlockPos(2, 5, 2));
        Player player = mockPlayerNear(ctx, ctx.absolutePos(new BlockPos(3, 6, 2)));
        BlockPos placed = placeSlabViaAndFindChangedSlab(
                ctx,
                player,
                owner,
                Direction.EAST,
                eastHitOffset(owner, -1.0d, 0.25d));
        BlockPos eastCell = ctx.absolutePos(new BlockPos(3, 5, 2));
        assertSlabDy(ctx, world, eastCell, -1.0d,
                "P26-10 honest-band lower-half: slab beside a -1.0 compound must land -1.0");
        System.out.println("[NEOFORGE_COMPOUND_HONEST_BAND_ROW] case=lower anchored=false placed="
                + shortPos(placed) + " expected=" + shortPos(eastCell)
                + " dy=" + SlabSupport.getYOffset(world, eastCell, world.getBlockState(eastCell)));
        ctx.assertTrue(placed.equals(eastCell),
                "P26-10 honest-band lower-half: slab landed at " + shortPos(placed)
                        + " not east cell " + shortPos(eastCell));
        System.out.println("[NEOFORGE_COMPOUND_HONEST_BAND_SUMMARY]"
                + " case=lower rows=1 green=1 red=0 proofScope=server_useon_visible_band_placement_authority_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void useOnSlabBesideCompoundHonestBandUpperHalf(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        buildCompoundMinusOne(ctx);
        BlockPos owner = ctx.absolutePos(new BlockPos(2, 5, 2));
        Player player = mockPlayerNear(ctx, ctx.absolutePos(new BlockPos(3, 6, 2)));
        BlockPos placed = placeSlabViaAndFindChangedSlab(
                ctx,
                player,
                owner,
                Direction.EAST,
                eastHitOffset(owner, -1.0d, 0.75d));
        BlockPos eastCell = ctx.absolutePos(new BlockPos(3, 5, 2));
        assertSlabDy(ctx, world, eastCell, -1.0d,
                "P26-10 honest-band upper-half: slab beside a -1.0 compound must land -1.0");
        System.out.println("[NEOFORGE_COMPOUND_HONEST_BAND_ROW] case=upper anchored=false placed="
                + shortPos(placed) + " expected=" + shortPos(eastCell)
                + " dy=" + SlabSupport.getYOffset(world, eastCell, world.getBlockState(eastCell)));
        ctx.assertTrue(placed.equals(eastCell),
                "P26-10 honest-band upper-half: slab landed at " + shortPos(placed)
                        + " not east cell " + shortPos(eastCell));
        System.out.println("[NEOFORGE_COMPOUND_HONEST_BAND_SUMMARY]"
                + " case=upper rows=1 green=1 red=0 proofScope=server_useon_visible_band_placement_authority_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void useOnSlabBesideCompoundAnchoredHonestBand(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        buildCompoundMinusOne(ctx);
        BlockPos owner = ctx.absolutePos(new BlockPos(2, 5, 2));
        SlabAnchorAttachment.addAnchor(world, owner, world.getBlockState(owner));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(world, owner, world.getBlockState(owner));
        Player player = mockPlayerNear(ctx, ctx.absolutePos(new BlockPos(3, 6, 2)));
        BlockPos placed = placeSlabViaAndFindChangedSlab(
                ctx,
                player,
                owner,
                Direction.EAST,
                eastHitOffset(owner, -1.0d, 0.25d));
        BlockPos eastCell = ctx.absolutePos(new BlockPos(3, 5, 2));
        assertSlabDy(ctx, world, eastCell, -1.0d,
                "P26-10 honest-band anchored: slab beside a -1.0 compound must land -1.0");
        System.out.println("[NEOFORGE_COMPOUND_HONEST_BAND_ROW] case=lower anchored=true placed="
                + shortPos(placed) + " expected=" + shortPos(eastCell)
                + " dy=" + SlabSupport.getYOffset(world, eastCell, world.getBlockState(eastCell)));
        ctx.assertTrue(placed.equals(eastCell),
                "P26-10 honest-band anchored: slab landed at " + shortPos(placed)
                        + " not east cell " + shortPos(eastCell));
        System.out.println("[NEOFORGE_COMPOUND_HONEST_BAND_SUMMARY]"
                + " case=anchored rows=1 green=1 red=0 proofScope=server_useon_visible_band_placement_authority_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void useOnFenceClickingLoweredFenceOverAirFollowsToMinusHalf(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        world.setBlock(ctx.absolutePos(new BlockPos(2, 2, 2)), slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(2, 3, 2)), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos loweredFence = ctx.absolutePos(new BlockPos(2, 3, 2));
        assertBlockDy(ctx, world, loweredFence, Blocks.OAK_FENCE, -0.5d,
                "P26-8 setup: existing fence on bottom slab is lowered");

        Player player = mockPlayerNear(ctx, ctx.absolutePos(new BlockPos(3, 4, 2)));
        BlockPos placed = placeBlockVia(
                player,
                loweredFence,
                Direction.EAST,
                eastHit(loweredFence, 0.75d),
                Blocks.OAK_FENCE);
        BlockPos eastCell = ctx.absolutePos(new BlockPos(3, 3, 2));
        ctx.assertTrue(placed.equals(eastCell),
                "P26-8: fence landed at " + shortPos(placed) + " not east cell " + shortPos(eastCell));
        assertBlockDy(ctx, world, eastCell, Blocks.OAK_FENCE, -0.5d,
                "P26-8 useOn: fence placed against a lowered fence over air must follow to -0.5");

        BlockState placedState = world.getBlockState(eastCell);
        ctx.assertTrue(placedState.getValue(CrossCollisionBlock.WEST),
                "P26-8 useOn: same-height lowered fence should connect WEST to the clicked lowered fence");
        System.out.println("[NEOFORGE_P26_FENCE_ROW] placed=" + shortPos(eastCell)
                + " dy=" + SlabSupport.getYOffset(world, eastCell, placedState)
                + " west=" + placedState.getValue(CrossCollisionBlock.WEST));
        System.out.println("[NEOFORGE_P26_FENCE_SUMMARY]"
                + " rows=1 green=1 red=0 proofScope=server_useon_connector_placement_authority_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void steppedGlassPaneRunBreaks(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockState after = connectorAState(ctx, world, Blocks.GLASS_PANE.defaultBlockState(), true);
        ctx.assertTrue(!after.getValue(CrossCollisionBlock.EAST),
                "P26 connector: stepped glass_pane EAST must be broken across a -0.5 step");
        System.out.println("[NEOFORGE_P26_GLASS_PANE_STEP_ROW] east=false result=GREEN");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void flatGlassPaneRunStillConnects(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockState after = connectorAState(ctx, world, Blocks.GLASS_PANE.defaultBlockState(), false);
        ctx.assertTrue(after.getValue(CrossCollisionBlock.EAST),
                "P26 connector: flat glass_pane EAST must remain connected at the same height");
        System.out.println("[NEOFORGE_P26_GLASS_PANE_FLAT_ROW] east=true result=GREEN");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void glassPaneParticipatesInLoweredConnectorVisualFamily(GameTestHelper ctx) {
        ctx.assertTrue(SlabSupport.isBeta35FenceWallVariantContactObject(Blocks.GLASS_PANE.defaultBlockState()),
                "P26 connector: glass_pane must participate in lowered connector-contact model/raycast gating");
        System.out.println("[NEOFORGE_P26_GLASS_PANE_FAMILY_ROW] member=true result=GREEN");
        System.out.println("[NEOFORGE_P26_GLASS_PANE_CONNECTOR_SUMMARY]"
                + " rows=3 result=GREEN proofScope=server_connector_glass_pane_step_family_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void steppedWallBreaksAndForcesUpPost(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockState after = connectorAState(ctx, world, Blocks.COBBLESTONE_WALL.defaultBlockState(), true);
        ctx.assertTrue(after.getValue(BlockStateProperties.EAST_WALL) == WallSide.NONE,
                "P26 connector: stepped cobblestone_wall EAST must be NONE, got "
                        + after.getValue(BlockStateProperties.EAST_WALL));
        ctx.assertTrue(after.getValue(BlockStateProperties.UP),
                "P26 connector: stepped wall with a broken side must raise the centre post");
        System.out.println("[NEOFORGE_P26_WALL_FENCE_VARIANT_ROW]"
                + " block=cobblestone_wall east=none up=true result=GREEN");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void steppedNetherBrickFenceBreaks(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockState after = connectorAState(ctx, world, Blocks.NETHER_BRICK_FENCE.defaultBlockState(), true);
        ctx.assertTrue(!after.getValue(CrossCollisionBlock.EAST),
                "P26 connector: stepped nether_brick_fence EAST must be broken");
        System.out.println("[NEOFORGE_P26_WALL_FENCE_VARIANT_ROW]"
                + " block=nether_brick_fence east=false result=GREEN");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void steppedStoneBrickWallBreaks(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockState after = connectorAState(ctx, world, Blocks.STONE_BRICK_WALL.defaultBlockState(), true);
        ctx.assertTrue(after.getValue(BlockStateProperties.EAST_WALL) == WallSide.NONE,
                "P26 connector: stepped stone_brick_wall EAST must be NONE, got "
                        + after.getValue(BlockStateProperties.EAST_WALL));
        System.out.println("[NEOFORGE_P26_WALL_FENCE_VARIANT_ROW]"
                + " block=stone_brick_wall east=none result=GREEN");
        System.out.println("[NEOFORGE_P26_WALL_FENCE_VARIANT_SUMMARY]"
                + " rows=3 result=GREEN proofScope=server_connector_wall_fence_variant_step_family_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void useOnSlabClickingLoweredFaceWithSolidGroundBelowFollowsToMinusHalf(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        world.setBlock(ctx.absolutePos(new BlockPos(2, 2, 2)), slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(2, 3, 2)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(3, 2, 2)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos lowered = ctx.absolutePos(new BlockPos(2, 3, 2));
        Player player = mockPlayerNear(ctx, ctx.absolutePos(new BlockPos(3, 4, 2)));

        BlockPos placed = placeSlabVia(player, lowered, Direction.EAST, eastHit(lowered, 0.75d));
        BlockPos eastCell = ctx.absolutePos(new BlockPos(3, 3, 2));
        ctx.assertTrue(placed.equals(eastCell),
                "P26-11: slab landed at " + shortPos(placed) + " not east cell " + shortPos(eastCell));
        assertSlabDy(ctx, world, eastCell, -0.5d,
                "P26-11 WYSIWYG: slab clicking a lowered side face must follow to -0.5 even with solid ground below");
        System.out.println("[NEOFORGE_P26_SOLID_GROUND_LOWERED_FACE_ROW] placed=" + shortPos(eastCell)
                + " dy=" + SlabSupport.getYOffset(world, eastCell, world.getBlockState(eastCell)));
        System.out.println("[NEOFORGE_P26_SOLID_GROUND_LOWERED_FACE_SUMMARY]"
                + " rows=1 green=1 red=0 proofScope=server_useon_lowered_face_placement_authority_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void useOnCantileverSlabAgainstCantileverSlabBothStayMinusHalf(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        world.setBlock(ctx.absolutePos(new BlockPos(2, 2, 2)), slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(2, 3, 2)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos lowered = ctx.absolutePos(new BlockPos(2, 3, 2));
        Player player = mockPlayerNear(ctx, ctx.absolutePos(new BlockPos(3, 5, 2)));

        BlockPos slabA = placeSlabViaAndFindChangedSlab(ctx, player, lowered, Direction.EAST,
                eastHit(lowered, 0.75d));
        BlockPos expectedA = ctx.absolutePos(new BlockPos(3, 3, 2));
        ctx.assertTrue(slabA.equals(expectedA),
                "P26-12: first slab landed at " + shortPos(slabA) + " not east cell " + shortPos(expectedA));
        assertSlabDy(ctx, world, slabA, -0.5d,
                "P26-12: first cantilever slab A must be -0.5");

        BlockPos slabB = placeSlabViaAndFindChangedSlab(ctx, player, slabA, Direction.EAST,
                eastHitOffset(slabA, -0.5d, 0.75d));
        BlockPos expectedB = ctx.absolutePos(new BlockPos(4, 3, 2));
        ctx.assertTrue(slabB.equals(expectedB),
                "P26-12: second slab landed at " + shortPos(slabB) + " not east cell " + shortPos(expectedB));
        assertSlabDy(ctx, world, slabB, -0.5d,
                "P26-12: second cantilever slab B must follow to -0.5, not freeze flat");
        assertSlabDy(ctx, world, slabA, -0.5d,
                "P26-12: placing B must not pop existing cantilever A back up to 0.0");

        System.out.println("[NEOFORGE_P26_CANTILEVER_SLAB_ROW] a=" + shortPos(slabA)
                + " aDy=" + SlabSupport.getYOffset(world, slabA, world.getBlockState(slabA))
                + " b=" + shortPos(slabB)
                + " bDy=" + SlabSupport.getYOffset(world, slabB, world.getBlockState(slabB)));
        System.out.println("[NEOFORGE_P26_CANTILEVER_SLAB_SUMMARY]"
                + " rows=1 green=1 red=0 proofScope=server_useon_cantilever_slab_chain_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void useOnCantileverSlabAnchorsAndSurvivesSourceRemoval(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        world.setBlock(ctx.absolutePos(new BlockPos(2, 2, 2)), slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(2, 3, 2)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos lowered = ctx.absolutePos(new BlockPos(2, 3, 2));
        Player player = mockPlayerNear(ctx, ctx.absolutePos(new BlockPos(3, 4, 2)));

        BlockPos placed = placeSlabVia(player, lowered, Direction.EAST, eastHit(lowered, 0.75d));
        BlockPos expected = ctx.absolutePos(new BlockPos(3, 3, 2));
        ctx.assertTrue(placed.equals(expected),
                "A8/P26: cantilever slab landed at " + shortPos(placed) + " not east cell " + shortPos(expected));
        assertSlabDy(ctx, world, placed, -0.5d,
                "A8/P26 useOn: cantilever slab beside a lowered block lands -0.5");
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, placed),
                "A8/P26: cantilever-placed slab must be anchored");

        world.setBlock(ctx.absolutePos(new BlockPos(2, 3, 2)), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(2, 2, 2)), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        assertSlabDy(ctx, world, placed, -0.5d,
                "A8/P26 useOn: after source removal the anchored slab keeps -0.5");

        System.out.println("[NEOFORGE_P26_CANTILEVER_SURVIVAL_ROW] placed=" + shortPos(placed)
                + " anchored=" + SlabAnchorAttachment.isAnchored(world, placed)
                + " dyAfterSourceRemoval=" + SlabSupport.getYOffset(world, placed, world.getBlockState(placed)));
        System.out.println("[NEOFORGE_P26_CANTILEVER_SURVIVAL_SUMMARY]"
                + " rows=1 green=1 red=0 proofScope=server_useon_cantilever_anchor_survival_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void useOnSlabOnFlushGroundBesideLoweredStaysFrozenFlat(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        world.setBlock(ctx.absolutePos(new BlockPos(2, 2, 2)), slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(2, 3, 2)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(3, 2, 2)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos ground = ctx.absolutePos(new BlockPos(3, 2, 2));
        Player player = mockPlayerNear(ctx, ctx.absolutePos(new BlockPos(3, 4, 2)));

        BlockPos placed = placeBlockVia(player, ground, Direction.UP, upHit(ground), Blocks.STONE_SLAB);
        BlockPos expected = ctx.absolutePos(new BlockPos(3, 3, 2));
        ctx.assertTrue(placed.equals(expected),
                "A1: slab landed at " + shortPos(placed) + " not top cell " + shortPos(expected));
        assertSlabDy(ctx, world, placed, 0.0d,
                "A1 useOn: slab on its own flush ground beside a lowered block must stay flat");
        ctx.assertTrue(SlabAnchorAttachment.isFrozenFlat(world, placed),
                "A1: flat-placed slab must record FROZEN_FLAT so a later neighbor cannot pull it down");

        System.out.println("[NEOFORGE_P26_FROZEN_FLAT_ROW] placed=" + shortPos(placed)
                + " dy=" + SlabSupport.getYOffset(world, placed, world.getBlockState(placed))
                + " frozenFlat=" + SlabAnchorAttachment.isFrozenFlat(world, placed));
        System.out.println("[NEOFORGE_P26_FROZEN_FLAT_SUMMARY]"
                + " rows=1 green=1 red=0 proofScope=server_useon_frozen_flat_guard_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void useOnFullBlockOnBottomSlabLowersToMinusHalf(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        world.setBlock(ctx.absolutePos(new BlockPos(2, 2, 2)), slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        BlockPos slab = ctx.absolutePos(new BlockPos(2, 2, 2));
        Player player = mockPlayerNear(ctx, ctx.absolutePos(new BlockPos(2, 4, 2)));

        BlockPos placed = placeBlockVia(player, slab, Direction.UP, upHit(slab), Blocks.STONE);
        BlockPos expected = ctx.absolutePos(new BlockPos(2, 3, 2));
        ctx.assertTrue(placed.equals(expected),
                "A2: full block landed at " + shortPos(placed) + " not top cell " + shortPos(expected));
        assertBlockDy(ctx, world, placed, Blocks.STONE, -0.5d,
                "A2 useOn: full block placed on a bottom slab must lower to -0.5");

        System.out.println("[NEOFORGE_P26_FULL_ON_BOTTOM_SLAB_ROW] placed=" + shortPos(placed)
                + " dy=" + SlabSupport.getYOffset(world, placed, world.getBlockState(placed)));
        System.out.println("[NEOFORGE_P26_FULL_ON_BOTTOM_SLAB_SUMMARY]"
                + " rows=1 green=1 red=0 proofScope=server_useon_full_block_bottom_slab_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void useOnSlabOnTopOfCompoundFollowsOneStepToMinusHalf(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        buildCompoundMinusOne(ctx);
        BlockPos top = ctx.absolutePos(new BlockPos(2, 5, 2));
        Player player = mockPlayerNear(ctx, ctx.absolutePos(new BlockPos(2, 7, 2)));

        BlockPos placed = placeSlabVia(player, top, Direction.UP, upHit(top));
        BlockPos expected = ctx.absolutePos(new BlockPos(2, 6, 2));
        ctx.assertTrue(placed.equals(expected),
                "A7: slab landed at " + shortPos(placed) + " not top cell " + shortPos(expected));
        assertSlabDy(ctx, world, placed, -0.5d,
                "A7 useOn: slab on top of a compound -1.0 stack follows one visible step to -0.5");

        System.out.println("[NEOFORGE_P26_COMPOUND_TOP_SLAB_ROW] placed=" + shortPos(placed)
                + " dy=" + SlabSupport.getYOffset(world, placed, world.getBlockState(placed)));
        System.out.println("[NEOFORGE_P26_COMPOUND_TOP_SLAB_SUMMARY]"
                + " rows=1 green=1 red=0 proofScope=server_useon_compound_top_slab_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void useOnSlabOnTopOfLoweredTopSlabPlacesAboveVisibleFace(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        world.setBlock(ctx.absolutePos(new BlockPos(2, 2, 2)), slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(2, 3, 2)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos loweredSource = ctx.absolutePos(new BlockPos(2, 3, 2));

        Player sourcePlayer = mockPlayerNear(ctx, ctx.absolutePos(new BlockPos(3, 4, 2)));
        BlockPos support = placeSlabVia(sourcePlayer, loweredSource, Direction.EAST, eastHit(loweredSource, 0.75d));
        assertSlabDy(ctx, world, support, -0.5d,
                "SETUP: side-placed top slab support should be lowered before placing on its visible UP face");
        ctx.assertTrue(world.getBlockState(support).getValue(SlabBlock.TYPE) == SlabType.TOP,
                "SETUP: upper-half side placement should create a TOP slab support");

        Player player = mockPlayerNear(ctx, ctx.absolutePos(new BlockPos(3, 5, 2)));
        BlockPos placed = placeSlabViaAndFindChangedSlab(
                ctx,
                player,
                support,
                Direction.UP,
                loweredTopSlabVisibleUpHit(support, 0.5d, 0.5d));
        BlockPos expected = support.above();
        ctx.assertTrue(placed.equals(expected),
                "WYSIWYG: UP-face placement on a lowered top slab must choose the cell above, got "
                        + shortPos(placed));
        assertSlabDy(ctx, world, placed, -0.5d,
                "WYSIWYG: slab placed on the visible top of a lowered top slab must sit on that lowered surface");
        ctx.assertTrue(world.getBlockState(support).getValue(SlabBlock.TYPE) == SlabType.TOP,
                "WYSIWYG: clicked lowered top slab must not merge into a double slab underneath");

        System.out.println("[NEOFORGE_P26_LOWERED_TOP_UP_FACE_ROW] support=" + shortPos(support)
                + " supportType=" + world.getBlockState(support).getValue(SlabBlock.TYPE)
                + " placed=" + shortPos(placed)
                + " dy=" + SlabSupport.getYOffset(world, placed, world.getBlockState(placed)));
        System.out.println("[NEOFORGE_P26_LOWERED_TOP_UP_FACE_SUMMARY]"
                + " rows=1 green=1 red=0 proofScope=server_useon_lowered_top_up_face_only");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void slabOnAnchoredCantileverBlockFollowsToMinusHalf(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        world.setBlock(ctx.absolutePos(new BlockPos(2, 2, 2)), slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(2, 3, 2)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos cantilever = ctx.absolutePos(new BlockPos(2, 3, 2));
        SlabAnchorAttachment.addAnchor(world, cantilever, world.getBlockState(cantilever));
        world.setBlock(ctx.absolutePos(new BlockPos(2, 2, 2)), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        assertBlockDy(ctx, world, cantilever, Blocks.STONE, -0.5d,
                "SETUP: anchored cantilever block should hold -0.5 after its carrier slab is removed");

        Player player = mockPlayerNear(ctx, ctx.absolutePos(new BlockPos(2, 5, 2)));
        BlockPos placed = placeSlabVia(player, cantilever, Direction.UP, upHit(cantilever));
        BlockPos expected = ctx.absolutePos(new BlockPos(2, 4, 2));
        ctx.assertTrue(placed.equals(expected),
                "RC4: slab landed at " + shortPos(placed) + " not top cell " + shortPos(expected));
        assertSlabDy(ctx, world, placed, -0.5d,
                "RC4: slab on an anchored cantilever-lowered block follows it to -0.5");

        System.out.println("[NEOFORGE_P26_ANCHORED_CANTILEVER_TOP_SLAB_ROW] placed=" + shortPos(placed)
                + " dy=" + SlabSupport.getYOffset(world, placed, world.getBlockState(placed))
                + " supportDy=" + SlabSupport.getYOffset(world, cantilever, world.getBlockState(cantilever)));
        System.out.println("[NEOFORGE_P26_ANCHORED_CANTILEVER_TOP_SLAB_SUMMARY]"
                + " rows=1 green=1 red=0 proofScope=server_useon_anchored_cantilever_top_slab_only");
        ctx.succeed();
    }

    /**
     * A full block resting on SOLID GROUND beside a lowered block must NOT sink. Only cantilevered
     * (air-below) blocks lower; a grounded neighbour must stay dy=0.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void advGroundedBesideLoweredMustNotSink(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos base = fixtureTestOrigin(ctx);
        // Lowered source: slab + stone.
        world.setBlock(base, slab(SlabType.BOTTOM), Block.UPDATE_CLIENTS);
        world.setBlock(base.above(1), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        // Grounded neighbour (east): solid support + stone on top.
        world.setBlock(base.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        world.setBlock(base.east().above(1), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);

        double src = SlabSupport.getYOffset(world, base.above(1), world.getBlockState(base.above(1)));
        double grounded = SlabSupport.getYOffset(world, base.east().above(1), world.getBlockState(base.east().above(1)));
        ctx.assertTrue(src == -0.5, "source stone on slab should be -0.5; got " + src);
        ctx.assertTrue(grounded == 0.0,
                "SINK BUG: stone on solid ground beside a lowered block must stay dy=0; got " + grounded);
        ctx.succeed();
    }

    /**
     * Cantilever propagation consistency: a 2-out air-below block connected through a 1-out block to
     * a lowered source must lower the SAME as the 1-out block (no mid-row pop). If the 2-out block
     * reads 0 while the 1-out reads -0.5, propagation "stops at one" (inconsistent canopy).
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void advCantileverTwoOutConsistent(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos base = fixtureTestOrigin(ctx);
        world.setBlock(base, slab(SlabType.BOTTOM), Block.UPDATE_CLIENTS);              // source slab
        world.setBlock(base.above(1), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS); // source stone -0.5
        world.setBlock(base.above(1).east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);      // 1-out (air below)
        world.setBlock(base.above(1).east(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);     // 2-out (air below)

        double oneOut = SlabSupport.getYOffset(world, base.above(1).east(), world.getBlockState(base.above(1).east()));
        double twoOut = SlabSupport.getYOffset(world, base.above(1).east(2), world.getBlockState(base.above(1).east(2)));
        ctx.assertTrue(oneOut == twoOut,
                "CANTILEVER INCONSISTENCY: 1-out=" + oneOut + " but 2-out=" + twoOut
                + " — a connected cantilever row must lower uniformly (no mid-row pop)");
        ctx.succeed();
    }

    /**
     * STALE ANCHOR: an anchor recorded by a real placement must be cleared when the block is
     * removed (setBlockState AIR → onStateReplaced → removeAnchor). If it lingers, a DIFFERENT block
     * later placed in that cell (with no slab support) would inherit a phantom -0.5 lowering.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void advStaleAnchorClearedAfterAirReplace(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slabPos = fixtureTestOrigin(ctx);
        BlockPos cell = slabPos.above(1);
        world.setBlock(slabPos, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        BlockState placed = authorBlock(world, cell, Blocks.STONE.defaultBlockState()); // onPlaced → anchor -0.5
        ctx.assertTrue(SlabSupport.getYOffset(world, cell, placed) == -0.5,
                "setup: stone authored on a bottom slab should anchor -0.5");
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, cell), "setup: cell should be anchored");

        world.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);   // remove → should clear anchor
        world.setBlock(slabPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); // remove the slab too
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(world, cell),
                "STALE ANCHOR: anchor must clear when the block is removed; it lingered at " + cell.toString());

        // A fresh block in the same cell, now with NO slab below, must read flat.
        world.setBlock(cell, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        double dy = SlabSupport.getYOffset(world, cell, world.getBlockState(cell));
        ctx.assertTrue(dy == 0.0,
                "STALE ANCHOR BUG: a new stone (no slab support) inherited a phantom lowering; dy=" + dy);
        ctx.succeed();
    }

    /**
     * Break + re-place in the SAME cell must not accumulate stale state: after authoring a lowered
     * stone, removing it, then authoring a stone again with the slab still present, the cell stays a
     * single correct -0.5 anchor (not corrupted/doubled).
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void advRefillSameCellNoCorruption(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slabPos = fixtureTestOrigin(ctx);
        BlockPos cell = slabPos.above(1);
        world.setBlock(slabPos, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        authorBlock(world, cell, Blocks.STONE.defaultBlockState());
        world.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        BlockState refilled = authorBlock(world, cell, Blocks.STONE.defaultBlockState());
        double dy = SlabSupport.getYOffset(world, cell, refilled);
        ctx.assertTrue(dy == -0.5,
                "refilled stone on the (still-present) slab should anchor -0.5; got " + dy);

        // Now pull the slab: the anchor should hold the placed block's frozen height (no pop), but a
        // FRESH non-placed block elsewhere must be unaffected.
        world.setBlock(slabPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        double dyAfter = SlabSupport.getYOffset(world, cell, world.getBlockState(cell));
        ctx.assertTrue(dyAfter == -0.5,
                "placed block must keep its frozen -0.5 after the slab is pulled (no pop); got " + dyAfter);
        ctx.succeed();
    }

    /**
     * Geometric recompute / no-stale: a NON-placed (setBlockState) cantilever block lowered off a
     * source must recompute to flat when the source is removed (it was never frozen/anchored).
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void advCantileverRecomputesAfterSourceBreak(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos base = fixtureTestOrigin(ctx);
        BlockPos source = base.above(1);
        BlockPos oneOut = source.east();
        world.setBlock(base, slab(SlabType.BOTTOM), Block.UPDATE_CLIENTS);
        world.setBlock(source, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);  // geometric source -0.5
        world.setBlock(oneOut, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);  // cantilever (air below) -0.5
        ctx.assertTrue(SlabSupport.getYOffset(world, oneOut, world.getBlockState(oneOut)) == -0.5,
                "setup: 1-out cantilever should be -0.5");

        world.setBlock(source, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);     // break the source
        double dy = SlabSupport.getYOffset(world, oneOut, world.getBlockState(oneOut));
        ctx.assertTrue(dy == 0.0,
                "STALE GEOMETRIC: a non-placed cantilever must recompute to 0 when its source is gone; got " + dy);
        ctx.succeed();
    }

    /**
     * Adjacent compound columns HOMOGENIZE (the consistent merge): placing a stone-on-slab column
     * beside a dy=-1.0 compound column makes the new column's slab side-merge so its top stone
     * compounds to -1.0 too — both tops end FLUSH at -1.0. No stable -1.0-vs-(-0.5) step forms, so
     * there is no compound ghost-window to miss (the predicate correctly returns FALSE: no step).
     * This is why the 1.21.11 boolean-flag "cull miss on compound step" does not apply on 1.21.1.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void advAdjacentCompoundColumnsHomogenizeFlush(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos o = fixtureTestOrigin(ctx);
        // Column A: slab/stone/slab/stone → top stone (A) = -1.0.
        world.setBlock(o, slab(SlabType.BOTTOM), Block.UPDATE_CLIENTS);
        world.setBlock(o.above(1), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        world.setBlock(o.above(2), slab(SlabType.BOTTOM), Block.UPDATE_CLIENTS);
        world.setBlock(o.above(3), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        BlockPos aTop = o.above(3);
        // Column B beside A's top: a slab (y2) + stone (y3). The slab side-merges with A's lowered slab.
        world.setBlock(aTop.below().east(), slab(SlabType.BOTTOM), Block.UPDATE_CLIENTS);
        world.setBlock(aTop.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
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
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void p26DownwardPointedDripstoneChainFollowsLoweredCeilingSupport(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos temporarySlab = ctx.absolutePos(new BlockPos(2, 3, 2));
        BlockPos support = ctx.absolutePos(new BlockPos(2, 4, 2));
        BlockPos upper = ctx.absolutePos(new BlockPos(2, 3, 2));
        BlockPos lower = ctx.absolutePos(new BlockPos(2, 2, 2));

        world.setBlock(temporarySlab, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(support, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        SlabAnchorAttachment.addAnchor(world, support, world.getBlockState(support));
        assertBlockDy(ctx, world, support, Blocks.OAK_PLANKS, -0.5,
                "P26 dripstone setup: ceiling support must be anchored lowered");

        world.setBlock(upper, pointedDripstoneDownBase(), Block.UPDATE_ALL);
        world.setBlock(lower, pointedDripstoneDownTip(), Block.UPDATE_ALL);

        assertBlockDy(ctx, world, upper, Blocks.POINTED_DRIPSTONE, -0.5,
                "P26 downward pointed dripstone upper segment must follow the lowered ceiling support");
        assertBlockDy(ctx, world, lower, Blocks.POINTED_DRIPSTONE, -0.5,
                "P26 downward pointed dripstone lower segment must inherit the same lowered ceiling support dy");
        System.out.println("[NEOFORGE_P26_DRIPSTONE_CHAIN_ROW]"
                + " case=lowered_ceiling_support"
                + " upperDy=" + text(SlabSupport.getYOffset(world, upper, world.getBlockState(upper)))
                + " lowerDy=" + text(SlabSupport.getYOffset(world, lower, world.getBlockState(lower)))
                + " result=GREEN");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void p26DownwardPointedDripstoneUnderChainFollowsLoweredCeilingSupport(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos temporarySlab = ctx.absolutePos(new BlockPos(2, 4, 2));
        BlockPos support = ctx.absolutePos(new BlockPos(2, 5, 2));
        BlockPos chain = ctx.absolutePos(new BlockPos(2, 4, 2));
        BlockPos dripstone = ctx.absolutePos(new BlockPos(2, 3, 2));

        world.setBlock(temporarySlab, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(support, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        SlabAnchorAttachment.addAnchor(world, support, world.getBlockState(support));
        assertBlockDy(ctx, world, support, Blocks.OAK_PLANKS, -0.5,
                "P26 dripstone setup: lowered support above chain must be anchored");

        world.setBlock(chain, yChain(), Block.UPDATE_ALL);
        world.setBlock(dripstone, pointedDripstoneDownBase(), Block.UPDATE_ALL);

        assertBlockDy(ctx, world, chain, Blocks.CHAIN, 0.0,
                "P26 control: chain under a lowered support keeps its own grid-height chain behavior");
        assertBlockDy(ctx, world, dripstone, Blocks.POINTED_DRIPSTONE, -0.5,
                "P26 downward pointed dripstone under a chain must inherit the lowered ceiling support dy");
        System.out.println("[NEOFORGE_P26_DRIPSTONE_CHAIN_ROW]"
                + " case=lowered_support_through_chain"
                + " chainDy=" + text(SlabSupport.getYOffset(world, chain, world.getBlockState(chain)))
                + " dripstoneDy=" + text(SlabSupport.getYOffset(world, dripstone, world.getBlockState(dripstone)))
                + " result=GREEN");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void p26DownwardPointedDripstoneUnderTopSlabBridgedChainStaysFlush(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chain = ctx.absolutePos(new BlockPos(2, 4, 2));
        BlockPos upper = ctx.absolutePos(new BlockPos(2, 3, 2));
        BlockPos lower = ctx.absolutePos(new BlockPos(2, 2, 2));

        world.setBlock(chain.above(), slab(SlabType.TOP), Block.UPDATE_ALL);
        world.setBlock(chain, yChain(), Block.UPDATE_ALL);
        world.setBlock(upper, pointedDripstoneDownBase(), Block.UPDATE_ALL);
        world.setBlock(lower, pointedDripstoneDownTip(), Block.UPDATE_ALL);

        assertBlockDy(ctx, world, chain, Blocks.CHAIN, 0.5,
                "P26 setup: direct Y-chain under a top slab keeps its ceiling-attach dy");
        assertBlockDy(ctx, world, upper, Blocks.POINTED_DRIPSTONE, 0.0,
                "P26 ceiling-bridged pointed dripstone upper segment follows the visible chain bottom");
        assertBlockDy(ctx, world, lower, Blocks.POINTED_DRIPSTONE, 0.0,
                "P26 ceiling-bridged pointed dripstone lower segment must not rise into the upper segment");
        System.out.println("[NEOFORGE_P26_DRIPSTONE_CHAIN_ROW]"
                + " case=top_slab_bridged_chain"
                + " chainDy=" + text(SlabSupport.getYOffset(world, chain, world.getBlockState(chain)))
                + " upperDy=" + text(SlabSupport.getYOffset(world, upper, world.getBlockState(upper)))
                + " lowerDy=" + text(SlabSupport.getYOffset(world, lower, world.getBlockState(lower)))
                + " result=GREEN");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void p26DownwardPointedDripstoneUnderTopSlabKeepsDescendantsGridHeight(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos upper = ctx.absolutePos(new BlockPos(2, 3, 2));
        BlockPos lower = ctx.absolutePos(new BlockPos(2, 2, 2));

        world.setBlock(upper.above(), slab(SlabType.TOP), Block.UPDATE_ALL);
        world.setBlock(upper, pointedDripstoneDownBase(), Block.UPDATE_ALL);
        world.setBlock(lower, pointedDripstoneDownTip(), Block.UPDATE_ALL);

        assertBlockDy(ctx, world, upper, Blocks.POINTED_DRIPSTONE, 0.5,
                "P26 setup: direct downward pointed-dripstone segment under a top slab attaches upward");
        assertBlockDy(ctx, world, lower, Blocks.POINTED_DRIPSTONE, 0.0,
                "P26 chained pointed-dripstone descendant must stay grid-height, not merge into the raised segment");
        System.out.println("[NEOFORGE_P26_DRIPSTONE_CHAIN_ROW]"
                + " case=direct_top_slab_descendant"
                + " upperDy=" + text(SlabSupport.getYOffset(world, upper, world.getBlockState(upper)))
                + " lowerDy=" + text(SlabSupport.getYOffset(world, lower, world.getBlockState(lower)))
                + " result=GREEN");
        System.out.println("[NEOFORGE_P26_DRIPSTONE_CHAIN_SUMMARY]"
                + " rows=4 result=GREEN proofScope=server_pointed_dripstone_chain_dy_only");
        ctx.succeed();
    }

    private static BlockState slab(SlabType type) {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static BlockState yChain() {
        return Blocks.CHAIN.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
    }

    private static BlockState pointedDripstoneDownBase() {
        return Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN)
                .setValue(BlockStateProperties.DRIPSTONE_THICKNESS, DripstoneThickness.BASE);
    }

    private static BlockState pointedDripstoneDownTip() {
        return Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN)
                .setValue(BlockStateProperties.DRIPSTONE_THICKNESS, DripstoneThickness.TIP);
    }

    private static BlockState stableScaffoldingState() {
        return Blocks.SCAFFOLDING.defaultBlockState()
                .setValue(BlockStateProperties.STABILITY_DISTANCE, 0)
                .setValue(BlockStateProperties.BOTTOM, false);
    }

    private static BlockState connectorAState(
            GameTestHelper ctx, ServerLevel world, BlockState connector, boolean stepped) {
        world.setBlock(ctx.absolutePos(new BlockPos(2, 1, 2)),
                stepped ? slab(SlabType.BOTTOM) : Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(3, 1, 2)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos a = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos b = ctx.absolutePos(new BlockPos(3, 2, 2));
        world.setBlock(a, connector, Block.UPDATE_ALL);
        world.setBlock(b, connector, Block.UPDATE_ALL);
        return connector.updateShape(Direction.EAST, world.getBlockState(b), world, a, b);
    }

    private static void buildCompoundMinusOne(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        world.setBlock(ctx.absolutePos(new BlockPos(2, 1, 2)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(2, 2, 2)), slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(2, 3, 2)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(2, 4, 2)), slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(2, 5, 2)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static Player mockPlayerNear(GameTestHelper ctx, BlockPos abs) {
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(abs.getX() + 0.5d, abs.getY() + 1.0d, abs.getZ() + 0.5d);
        return player;
    }

    private static BlockPos placeSlabVia(Player player, BlockPos clickAbs, Direction face, Vec3 hit) {
        ItemStack stack = new ItemStack(Blocks.STONE_SLAB);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult blockHit = new BlockHitResult(hit, face, clickAbs, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, blockHit));
        return clickAbs.relative(face);
    }

    private static BlockPos placeBlockVia(Player player, BlockPos clickAbs, Direction face, Vec3 hit, Block block) {
        ItemStack stack = new ItemStack(block);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult blockHit = new BlockHitResult(hit, face, clickAbs, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, blockHit));
        return clickAbs.relative(face);
    }

    private static Vec3 eastHit(BlockPos abs, double yOffset) {
        return new Vec3(abs.getX() + 1.0d, abs.getY() + yOffset, abs.getZ() + 0.5d);
    }

    private static Vec3 eastHitOffset(BlockPos abs, double sourceDy, double yOffset) {
        return new Vec3(abs.getX() + 1.0d, abs.getY() + sourceDy + yOffset, abs.getZ() + 0.5d);
    }

    private static Vec3 upHit(BlockPos abs) {
        return new Vec3(abs.getX() + 0.5d, abs.getY() + 1.0d, abs.getZ() + 0.5d);
    }

    private static Vec3 loweredTopSlabVisibleUpHit(BlockPos abs, double localX, double localZ) {
        return new Vec3(abs.getX() + localX, abs.getY() + 0.5d, abs.getZ() + localZ);
    }

    private static BlockPos placeSlabViaAndFindChangedSlab(
            GameTestHelper ctx,
            Player player,
            BlockPos clickAbs,
            Direction face,
            Vec3 hit
    ) {
        ServerLevel world = ctx.getLevel();
        BlockPos[] candidates = new BlockPos[]{
                clickAbs,
                clickAbs.relative(face),
                clickAbs.above(),
                clickAbs.below(),
                clickAbs.north(),
                clickAbs.south(),
                clickAbs.east(),
                clickAbs.west()
        };
        BlockState[] before = new BlockState[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            before[i] = world.getBlockState(candidates[i]);
        }

        placeSlabVia(player, clickAbs, face, hit);

        for (int i = 0; i < candidates.length; i++) {
            BlockState after = world.getBlockState(candidates[i]);
            if (!after.equals(before[i]) && after.getBlock() instanceof SlabBlock) {
                return candidates[i];
            }
        }
        ctx.assertTrue(false,
                "useOn did not mutate a nearby slab cell for click=" + shortPos(clickAbs)
                        + " face=" + face + " hit=" + hit);
        return clickAbs.relative(face);
    }

    private static void assertSlabDy(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos abs,
            double expectedDy,
            String message
    ) {
        BlockState state = world.getBlockState(abs);
        ctx.assertTrue(state.getBlock() instanceof SlabBlock,
                message + ": no slab placed at " + shortPos(abs) + " (got " + state.getBlock() + ")");
        double dy = SlabSupport.getYOffset(world, abs, state);
        ctx.assertTrue(Math.abs(dy - expectedDy) <= MC1211_SERVER_STATE_EPSILON,
                message + ": expected dy=" + expectedDy + " got " + dy);
    }

    private static void assertBlockDy(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos abs,
            Block expectedBlock,
            double expectedDy,
            String message
    ) {
        BlockState state = world.getBlockState(abs);
        ctx.assertTrue(state.getBlock() == expectedBlock,
                message + ": wrong block at " + shortPos(abs) + " (got " + state.getBlock() + ")");
        double dy = SlabSupport.getYOffset(world, abs, state);
        ctx.assertTrue(Math.abs(dy - expectedDy) <= MC1211_SERVER_STATE_EPSILON,
                message + ": expected dy=" + expectedDy + " got " + dy);
    }

    private static void assertLoweredStairCollisionFollowsVisual(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos abs,
            String label
    ) {
        BlockState state = world.getBlockState(abs);
        double dy = SlabSupport.getYOffset(world, abs, state);
        ctx.assertTrue(near(dy, -0.5d),
                "P26 stair setup: " + label + " expected dy=-0.5, got " + dy);
        VoxelShape collision = state.getCollisionShape(world, abs, CollisionContext.empty());
        ctx.assertTrue(!collision.isEmpty(),
                "P26 stair setup: " + label + " collision shape is empty");
        AABB bounds = collision.bounds();
        ctx.assertTrue(near(bounds.minY, dy) && near(bounds.maxY, 0.5d),
                "P26 stair collision: " + label + " boundsY=[" + bounds.minY + ", "
                        + bounds.maxY + "], expected [-0.5, 0.5]");
        System.out.println("[NEOFORGE_P26_STAIR_COLLISION_ROW]"
                + " label=" + label.replace(' ', '_')
                + " dy=" + text(dy)
                + " minY=" + text(bounds.minY)
                + " maxY=" + text(bounds.maxY)
                + " result=GREEN");
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static BlockState authorBlock(ServerLevel world, BlockPos pos, BlockState state) {
        world.setBlock(pos, state, Block.UPDATE_ALL);
        state.getBlock().setPlacedBy(world, pos, state, null, ItemStack.EMPTY);
        return world.getBlockState(pos);
    }

    private static BlockPos authorLoweredBottomCarrier(ServerLevel world, BlockPos baseSlabPos) {
        world.setBlock(baseSlabPos, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        BlockPos loweredFullPos = baseSlabPos.above();
        authorBlock(world, loweredFullPos, Blocks.STONE.defaultBlockState());
        BlockPos carrierPos = loweredFullPos.above();
        world.setBlock(carrierPos, slab(SlabType.BOTTOM), Block.UPDATE_ALL);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, carrierPos,
                world.getBlockState(carrierPos));
        return carrierPos;
    }

    private static ServerStateOverlapRow measureServerStateRow(
            ServerLevel world,
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
        if (supportState.getBlock() instanceof SlabBlock && supportState.hasProperty(SlabBlock.TYPE)) {
            return SlabSupport.getSupportYOffset(supportState);
        }
        return 1.0d;
    }

    private static String slabTypeName(BlockState state) {
        if (state != null && state.getBlock() instanceof SlabBlock && state.hasProperty(SlabBlock.TYPE)) {
            return state.getValue(SlabBlock.TYPE).name();
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
                    + " supportPos=" + supportPos.toString()
                    + " supportState=" + compact(supportState)
                    + " supportSlabType=" + supportSlabType
                    + " objectPos=" + objectPos.toString()
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
