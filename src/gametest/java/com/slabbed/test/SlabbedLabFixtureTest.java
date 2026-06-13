package com.slabbed.test;

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

    /**
     * BUG PROOF: the ghost-window cull predicate only covers TS-direct lowering.
     *
     * <p>A full block lowered by a VANILLA bottom slab (dy=-0.5) beside a flat full block is a real
     * 0.5 horizontal height step, so its side face must be un-culled to avoid a see-through window.
     * But {@code isSlabHeightStepFace} keys on {@code isDirectCustomSlabSupportedObject} (which only
     * counts a Terrain Slabs BOTTOM_LIKE support), so it returns FALSE here and BOTH cull mechanisms
     * (BlockRenderInfoCullMixin + the YOffsetEmitter model path) leave the step face culled → an
     * unfixed vanilla-slab/compound ghost window. This test asserts the CORRECT behaviour (true);
     * it fails on the TS-only predicate and passes once the predicate is broadened to a dy-difference
     * (mirroring the 1.21.1 port).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void advVanillaSlabStepMustUnCull(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);
        // Lowered via a VANILLA bottom slab.
        BlockPos lowered = origin.up();
        world.setBlockState(origin, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        world.setBlockState(lowered, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        // Flat, grounded neighbour to the east.
        world.setBlockState(origin.east(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(origin.east().up(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        double loweredDy = SlabSupport.getYOffset(world, lowered, world.getBlockState(lowered));
        double flatDy = SlabSupport.getYOffset(world, origin.east().up(), world.getBlockState(origin.east().up()));
        ctx.assertTrue(loweredDy == -0.5, "setup: vanilla-slab-lowered stone should be -0.5; got " + loweredDy);
        ctx.assertTrue(flatDy == 0.0, "setup: grounded neighbour should be flat 0; got " + flatDy);

        boolean step = SlabSupport.isSlabHeightStepFace(world, lowered, world.getBlockState(lowered), Direction.EAST);
        ctx.assertTrue(step,
                "CULL GAP: vanilla-slab-lowered (-0.5) beside flat (0) is a real step, but the predicate returned "
                + step + " — TS-only gate misses vanilla/compound lowering, leaving an unfixed ghost window");
        ctx.complete();
    }

    /**
     * Vanilla vertical-compound stack: bottom slab / stone / bottom slab / stone. Each layer rests on
     * the rendered top of the one below, so the TOP stone must compound to dy=-1.0 to sit FLUSH on the
     * lowered L2 slab. If it reads -0.5 it FLOATS 0.5 above the slab (a visible gap). 1.21.1 produces
     * -1.0 here; this guards the 1.21.11 port of that vertical-compound handling.
     *
     * <p>Root cause of the former float: compound -1.0 was only granted when the slab below was
     * {@code isAdjacentSideSlabLowered} (side-adjacency). A vanilla slab lowered VERTICALLY (resting on
     * a lowered full block) was not side-adjacent-lowered, so the block above never compounded. Fixed by
     * reading the support slab's rendered dy via {@code loweredBottomSlabSupportDyForCompound} (mirrors
     * the 1.21.1 floorTorchBottomSlabSupportDy reader) and dropping the block an extra -0.5.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void advVanillaCompoundStackTopMustBeFlush(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);
        BlockState bs = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        world.setBlockState(base, bs, Block.NOTIFY_LISTENERS);                                     // L0 slab (air below → 0)
        world.setBlockState(base.up(1), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);   // L1 stone on slab → -0.5
        world.setBlockState(base.up(2), bs, Block.NOTIFY_LISTENERS);                               // L2 slab on lowered stone → -0.5
        world.setBlockState(base.up(3), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);   // L3 stone on lowered slab → -1.0

        double l1 = SlabSupport.getYOffset(world, base.up(1), world.getBlockState(base.up(1)));
        double l2 = SlabSupport.getYOffset(world, base.up(2), world.getBlockState(base.up(2)));
        double l3 = SlabSupport.getYOffset(world, base.up(3), world.getBlockState(base.up(3)));
        ctx.assertTrue(l1 == -0.5, "L1 stone on vanilla bottom slab should be -0.5; got " + l1);
        ctx.assertTrue(l2 == -0.5, "L2 slab on lowered stone should be -0.5; got " + l2);
        // The smoking gun: flush needs -1.0. -0.5 ⇒ float (gap 0.5).
        ctx.assertTrue(l3 == -1.0,
                "FLOAT BUG: top stone on a vertically-lowered bottom slab must compound to -1.0 (flush); got "
                + l3 + " (gap=" + ((base.up(3).getY() + l3) - (base.up(2).getY() + 0.5 + l2)) + ")");
        ctx.complete();
    }

    /**
     * Adversarial: a full block on SOLID GROUND beside a vanilla-slab-lowered block must NOT sink.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void advVanillaGroundedBesideLoweredMustNotSink(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);
        world.setBlockState(base, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        world.setBlockState(base.up(1), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(base.east(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(base.east().up(1), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        double grounded = SlabSupport.getYOffset(world, base.east().up(1), world.getBlockState(base.east().up(1)));
        ctx.assertTrue(grounded == 0.0,
                "SINK: stone on solid ground beside a lowered block must stay dy=0; got " + grounded);
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
