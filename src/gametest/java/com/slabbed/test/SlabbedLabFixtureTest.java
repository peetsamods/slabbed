package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.dev.SlabbedLabFixtures;
import com.slabbed.dev.SlabbedLabFixtures.LaneStatus;
import com.slabbed.dev.SlabbedLabFixtures.PlaceResult;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
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

    /**
     * Exercises the basic fixture lifecycle on all three lanes (placement assertions)
     * and the full break/restore/pulse cycle on the FULL lane.
     *
     * <p>Uses {@code fabric-gametest-api-v1:empty} (built-in 8×8×8 all-air structure).
     * Fixture footprint: X=0..4, Y=0..1, Z=0..1 (pulse at Z=1) — fits within bounds.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void labSupportCycle(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        // Map structure-relative (0,0,0) to the absolute world position for the fixture origin.
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

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
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void outlineRaycastParity(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

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
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void blockEntityFullCubeSitsOnSlab(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

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
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void solidCubeLowersOverSlab(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

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

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnAnchoredLoweredSupportInheritsDy(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos baseSlabPos = origin;
        BlockPos loweredFullPos = baseSlabPos.up();
        BlockPos loweredSupportPos = loweredFullPos.east();
        BlockPos placedSlabPos = loweredSupportPos.up();

        world.setBlockState(
                baseSlabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        world.setBlockState(loweredFullPos, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(loweredSupportPos, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(world, loweredSupportPos, world.getBlockState(loweredSupportPos));

        BlockState support = world.getBlockState(loweredSupportPos);
        ctx.assertTrue(
                SlabAnchorAttachment.isAnchored(world, loweredSupportPos),
                "fixture invalid: lowered support was not anchored");
        ctx.assertTrue(
                SlabSupport.getYOffset(world, loweredSupportPos, support) == -0.5,
                "fixture invalid: lowered support dy should be -0.5");

        world.setBlockState(
                placedSlabPos,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);

        BlockState placed = world.getBlockState(placedSlabPos);
        double dy = SlabSupport.getYOffset(world, placedSlabPos, placed);
        ctx.assertTrue(dy == -0.5,
                "oak slab above anchored lowered support should inherit dy=-0.5, got " + dy);

        VoxelShape outline = placed.getOutlineShape(world, placedSlabPos, ShapeContext.absent());
        ctx.assertTrue(outline.getBoundingBox().minY == -0.5,
                "oak slab outline minY should be -0.5, got " + outline.getBoundingBox().minY);

        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnLoweredDoubleSlabSupportInheritsDy(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos baseSlabPos = origin;
        BlockPos loweredFullPos = baseSlabPos.up();
        BlockPos loweredDoubleSlabPos = loweredFullPos.east();
        BlockPos placedSlabPos = loweredDoubleSlabPos.up();

        world.setBlockState(
                baseSlabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        world.setBlockState(loweredFullPos, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(
                loweredDoubleSlabPos,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                Block.NOTIFY_LISTENERS);

        BlockState support = world.getBlockState(loweredDoubleSlabPos);
        double supportDy = SlabSupport.getYOffset(world, loweredDoubleSlabPos, support);
        ctx.assertTrue(supportDy == -0.5,
                "fixture invalid: lowered double slab support dy should be -0.5, got " + supportDy);

        world.setBlockState(
                placedSlabPos,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);

        BlockState placed = world.getBlockState(placedSlabPos);
        double dy = SlabSupport.getYOffset(world, placedSlabPos, placed);
        ctx.assertTrue(dy == -0.5,
                "oak slab above lowered double slab should inherit dy=-0.5, got " + dy);

        VoxelShape outline = placed.getOutlineShape(world, placedSlabPos, ShapeContext.absent());
        ctx.assertTrue(outline.getBoundingBox().minY == -0.5,
                "oak slab above lowered double slab outline minY should be -0.5, got "
                        + outline.getBoundingBox().minY);

        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void perpendicularSideSlabPersistsAfterConnectorBreak(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos baseSlabPos = origin;
        BlockPos loweredFullPos = baseSlabPos.up();
        BlockPos connectorSlabPos = loweredFullPos.east();
        BlockPos perpendicularSlabPos = connectorSlabPos.south();

        world.setBlockState(
                baseSlabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        placeWithOnPlaced(world, loweredFullPos, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState());
        placeWithOnPlaced(
                world,
                connectorSlabPos,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
        placeWithOnPlaced(
                world,
                perpendicularSlabPos,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));

        BlockState connector = world.getBlockState(connectorSlabPos);
        BlockState perpendicular = world.getBlockState(perpendicularSlabPos);
        double connectorDy = SlabSupport.getYOffset(world, connectorSlabPos, connector);
        double perpendicularDy = SlabSupport.getYOffset(world, perpendicularSlabPos, perpendicular);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, connectorSlabPos),
                "fixture invalid: connector side slab should be placement-anchored");
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, perpendicularSlabPos),
                "fixture invalid: perpendicular side slab should be placement-anchored");
        ctx.assertTrue(connectorDy == -0.5,
                "fixture invalid: connector side slab should start lowered, got " + connectorDy);
        ctx.assertTrue(perpendicularDy == -0.5,
                "fixture invalid: perpendicular side slab should start lowered, got " + perpendicularDy);

        world.breakBlock(connectorSlabPos, false);

        BlockState after = world.getBlockState(perpendicularSlabPos);
        double afterDy = SlabSupport.getYOffset(world, perpendicularSlabPos, after);
        ctx.assertTrue(
                after.isOf(Blocks.OAK_SLAB)
                        && after.contains(SlabBlock.TYPE)
                        && after.get(SlabBlock.TYPE) == SlabType.TOP,
                "perpendicular slab should remain a TOP oak slab after connector break, got " + after);
        ctx.assertTrue(afterDy == -0.5,
                "perpendicular lowered side slab should persist dy=-0.5 after connector break, got " + afterDy);

        VoxelShape outline = after.getOutlineShape(world, perpendicularSlabPos, ShapeContext.absent());
        ctx.assertTrue(
                outline.getBoundingBox().minY == 0.0 && outline.getBoundingBox().maxY == 0.5,
                "perpendicular TOP slab outline should stay lowered at [0.0, 0.5] after connector break, got ["
                        + outline.getBoundingBox().minY + ", " + outline.getBoundingBox().maxY + "]");

        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void perpendicularSideSlabFromLoweredSlabEdgeInheritsDyAndPersists(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos baseSlabPos = origin;
        BlockPos loweredFullPos = baseSlabPos.up();
        BlockPos loweredTopSlabPos = loweredFullPos.up();
        BlockPos perpendicularSlabPos = loweredTopSlabPos.south();

        world.setBlockState(
                baseSlabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        placeWithOnPlaced(world, loweredFullPos, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState());
        placeWithOnPlaced(
                world,
                loweredTopSlabPos,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        placeWithOnPlaced(
                world,
                perpendicularSlabPos,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));

        BlockState loweredTopSlab = world.getBlockState(loweredTopSlabPos);
        BlockState perpendicular = world.getBlockState(perpendicularSlabPos);
        double loweredTopDy = SlabSupport.getYOffset(world, loweredTopSlabPos, loweredTopSlab);
        double perpendicularDy = SlabSupport.getYOffset(world, perpendicularSlabPos, perpendicular);
        ctx.assertTrue(loweredTopDy == -0.5,
                "fixture invalid: lowered top slab should start dy=-0.5, got " + loweredTopDy);
        ctx.assertTrue(perpendicularDy == -0.5,
                "perpendicular slab from lowered slab edge should start dy=-0.5, got " + perpendicularDy);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, perpendicularSlabPos),
                "perpendicular slab from lowered slab edge should be placement-anchored");

        world.breakBlock(loweredTopSlabPos, false);

        BlockState after = world.getBlockState(perpendicularSlabPos);
        double afterDy = SlabSupport.getYOffset(world, perpendicularSlabPos, after);
        ctx.assertTrue(after.isOf(Blocks.OAK_SLAB),
                "perpendicular slab should remain after lowered top slab break, got " + after);
        ctx.assertTrue(afterDy == -0.5,
                "perpendicular slab should persist dy=-0.5 after lowered top slab break, got " + afterDy);

        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void blockOnAnchoredBottomSideSlabKeepsCompoundDy(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos baseSlabPos = origin;
        BlockPos loweredFullPos = baseSlabPos.up();
        BlockPos bottomSideSlabPos = loweredFullPos.east();
        BlockPos topBlockPos = bottomSideSlabPos.up();

        world.setBlockState(
                baseSlabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        placeWithOnPlaced(world, loweredFullPos, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState());
        placeWithOnPlaced(
                world,
                bottomSideSlabPos,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        world.setBlockState(topBlockPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        BlockState sideSlab = world.getBlockState(bottomSideSlabPos);
        BlockState topBlock = world.getBlockState(topBlockPos);
        double sideSlabDy = SlabSupport.getYOffset(world, bottomSideSlabPos, sideSlab);
        double topBlockDy = SlabSupport.getYOffset(world, topBlockPos, topBlock);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, bottomSideSlabPos),
                "fixture invalid: bottom side slab should be placement-anchored");
        ctx.assertTrue(sideSlabDy == -0.5,
                "bottom side slab should stay lowered by dy=-0.5, got " + sideSlabDy);
        ctx.assertTrue(topBlockDy == -1.0,
                "block on lowered bottom side slab should keep compound dy=-1.0, got " + topBlockDy);

        ctx.complete();
    }

    /**
     * Maintainer's NEVER-POP law (freeze-flat): a structural block <em>placed</em> flat (dy=0) must STAY
     * at dy=0 even after a bottom slab is later placed directly under it — no autonomous down-pop,
     * no retroactively-inherited lowering. Exercises the REAL onPlaced path via
     * {@link #placeWithOnPlaced}, which {@code BlockOnPlacedAnchorMixin} intercepts to call
     * {@code SlabAnchorAttachment.freezeLoweredOnPlace}. This is the exact violation reported
     * live: "I placed the slab, the spruce log popped down."
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void frozenFlatBlockStaysFlatWhenSlabAddedBelow(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos blockPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 3, 2);
        BlockPos belowPos = blockPos.down();
        world.setBlockState(belowPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        // REAL placement with air below ⇒ dy=0 ⇒ structural stone recorded frozen-flat (not anchored).
        placeWithOnPlaced(world, blockPos, Blocks.STONE.getDefaultState());
        BlockState placed = world.getBlockState(blockPos);
        ctx.assertTrue(placed.isOf(Blocks.STONE), "stone not present at test position");
        ctx.assertTrue(SlabAnchorAttachment.isFrozenFlat(world, blockPos),
                "stone placed flat (air below) must be recorded frozen-flat by onPlaced");
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(world, blockPos),
                "flat-placed stone must NOT be anchored (it was never lowered)");
        ctx.assertTrue(SlabSupport.getYOffset(world, blockPos, placed) == 0.0,
                "flat-placed stone dy must be 0 before any slab is added");

        // THE VIOLATION: place a bottom slab directly under the now-floating block.
        world.setBlockState(belowPos, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_ALL);

        double dy = SlabSupport.getYOffset(world, blockPos, placed);
        ctx.assertTrue(dy == 0.0,
                "LAW: flat-placed stone must stay dy=0 after a bottom slab is placed under it; got dy=" + dy);

        VoxelShape outline = placed.getOutlineShape(world, blockPos, ShapeContext.absent());
        ctx.assertTrue(outline.getBoundingBox().minY == 0.0,
                "flat-placed stone outline minY must stay 0.0 after slab added; got "
                + outline.getBoundingBox().minY);
        ctx.complete();
    }

    /**
     * Negative control: a stone placed via {@code setBlockState} never runs {@code onPlaced}, so it
     * carries no frozen-flat marker (mirrors terrain / non-player blocks) and DOES lower to -0.5
     * under a bottom slab. Proves the frozen-flat marker is what suppresses the down-pop (law proof
     * is not vacuously green).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unfrozenBlockLowersWhenSlabAddedBelow(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos blockPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 3, 2);
        BlockPos belowPos = blockPos.down();
        world.setBlockState(belowPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(blockPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockState placed = world.getBlockState(blockPos);
        ctx.assertTrue(!SlabAnchorAttachment.isFrozenFlat(world, blockPos),
                "setBlockState stone must NOT be frozen-flat (no onPlaced ran)");

        world.setBlockState(belowPos, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_ALL);
        double dy = SlabSupport.getYOffset(world, blockPos, placed);
        ctx.assertTrue(dy == -0.5,
                "control: unfrozen stone over a bottom slab should lower to -0.5; got dy=" + dy);
        ctx.complete();
    }

    /**
     * Ceiling-hung decoration (hanging roots) under a FLUSH slab must stay flush (dy=0) and must NOT
     * be dragged down by a carrier lower in the column. Reproduces the live bug: a block lower in the
     * column bridged the downward walk to a slab below, lowering the roots -0.5 (a visible gap under
     * the flush slab) and letting a neighbor break pop them. The fix dispatches always-ceiling
     * decorations from the support ABOVE only.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingRootsUnderFlushSlabStayFlush(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 2, 2);
        BlockState bottomSlab = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        world.setBlockState(base.up(2), bottomSlab, Block.NOTIFY_LISTENERS);                            // flush ceiling slab
        world.setBlockState(base.up(1), Blocks.HANGING_ROOTS.getDefaultState(), Block.NOTIFY_LISTENERS); // roots hang under it
        world.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);              // bridge (non-air)
        world.setBlockState(base.down(1), bottomSlab, Block.NOTIFY_LISTENERS);                          // slab 2 cells below roots

        double ceiling = SlabSupport.getYOffset(world, base.up(2), world.getBlockState(base.up(2)));
        double roots = SlabSupport.getYOffset(world, base.up(1), world.getBlockState(base.up(1)));
        ctx.assertTrue(ceiling == 0.0, "ceiling slab should be flush; got " + ceiling);
        ctx.assertTrue(roots == 0.0,
                "GAP BUG: hanging roots under a flush slab must stay flush (0), not be dragged down by a "
                + "carrier lower in the column; got " + roots);
        ctx.complete();
    }

    private static void placeWithOnPlaced(ServerWorld world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
        state.getBlock().onPlaced(world, pos, state, null, ItemStack.EMPTY);
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
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void carpetOutlineNotDoubled(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

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
}
