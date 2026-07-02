package com.slabbed.test;

import com.slabbed.dev.SlabbedLabFixtures;
import com.slabbed.dev.SlabbedLabFixtures.LaneStatus;
import com.slabbed.dev.SlabbedLabFixtures.PlaceResult;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.item.ItemStack;
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

    /** Drives the REAL production placement path: setBlockState + Block.onPlaced, which the
     *  BlockOnPlacedAnchorMixin intercepts to call SlabAnchorAttachment.freezeLoweredOnPlace. */
    private static BlockState authorBlock(ServerWorld world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
        state.getBlock().onPlaced(world, pos, state, null, ItemStack.EMPTY);
        return world.getBlockState(pos);
    }

    /**
     * Maintainer's NEVER-POP law (freeze-flat): a structural block <em>placed</em> flat (dy=0) must STAY
     * at dy=0 even after a bottom slab is later placed directly under it — no autonomous down-pop, no
     * retroactively-inherited lowering. Exercises the REAL onPlaced path via {@link #authorBlock}.
     * This is the exact violation reported live: "I placed the slab, the spruce log popped down."
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void frozenFlatBlockStaysFlatWhenSlabAddedBelow(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos blockPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 3, 2);
        BlockPos belowPos = blockPos.down();
        world.setBlockState(belowPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        // REAL placement with air below ⇒ dy=0 ⇒ structural stone recorded frozen-flat (not anchored).
        BlockState placed = authorBlock(world, blockPos, Blocks.STONE.getDefaultState());
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
     * carries no frozen-flat marker (mirrors terrain / non-player blocks) and DOES lower to -0.5 under
     * a bottom slab. Proves the frozen-flat marker is what suppresses the down-pop (law proof is not
     * vacuously green).
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
     * COMPOUND SIDECAR (port of the 1.21.1 {@code isCompoundFullBlockAnchor} lane): a full block
     * right-click-PLACED on a LOWERED bottom slab is geometrically at -1.0 the instant it is placed,
     * but the flat anchor read froze it at -0.5 — popping it up 0.5 on the next read (the HANDOFF
     * "compound -1.0 on right-click placement" gap; setBlockState twins already read -1.0 via
     * {@code advVanillaCompoundStackTopMustBeFlush}). The sidecar records the authored -1.0 truth at
     * placement, and — per the freeze law — it must SURVIVE removal of the source slab below.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placedCompoundStackTopFreezesAtMinusOne(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);
        BlockState bs = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        world.setBlockState(base, bs, Block.NOTIFY_LISTENERS);                                     // L0 slab (0)
        world.setBlockState(base.up(1), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);   // L1 stone (-0.5)
        world.setBlockState(base.up(2), bs, Block.NOTIFY_LISTENERS);                               // L2 slab (-0.5, LOWERED)

        // REAL placement path (onPlaced → addAnchor + freezeLoweredOnPlace): the compound stack top.
        BlockPos topPos = base.up(3);
        BlockState top = authorBlock(world, topPos, Blocks.STONE.getDefaultState());
        ctx.assertTrue(top.isOf(Blocks.STONE), "authored stone not present at stack top");
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, topPos),
                "placed stone on a bottom slab must be anchored");
        ctx.assertTrue(SlabAnchorAttachment.isCompoundFullBlockAnchor(world, topPos),
                "placed stone on a LOWERED bottom slab must carry the compound sidecar");

        double dy = SlabSupport.getYOffset(world, topPos, top);
        ctx.assertTrue(dy == -1.0,
                "PLACED compound stack top must freeze at the -1.0 it was placed at (WYSIWYG), not the "
                + "flat -0.5 anchor read; got dy=" + dy);

        // NEVER-POP: breaking the source slab below must not move the placed piece (sidecar survives).
        world.setBlockState(base.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        double dyAfter = SlabSupport.getYOffset(world, topPos, world.getBlockState(topPos));
        ctx.assertTrue(dyAfter == -1.0,
                "LAW: compound-frozen top must stay at -1.0 after its source slab is broken; got dy=" + dyAfter);
        ctx.complete();
    }

    /**
     * Negative control for the compound sidecar: a full block placed on a FLUSH bottom slab is the
     * ordinary -0.5 anchor lane — it must NOT pick up the sidecar (over-broad authoring would sink
     * every placed block on a plain slab to -1.0).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placedFullBlockOnFlushSlabStillFreezesAtMinusHalf(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 2, 2);
        world.setBlockState(slabPos, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);                                                           // flush slab (0)
        BlockPos stonePos = slabPos.up();
        BlockState stone = authorBlock(world, stonePos, Blocks.STONE.getDefaultState());
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, stonePos),
                "placed stone on a flush bottom slab must be anchored");
        ctx.assertTrue(!SlabAnchorAttachment.isCompoundFullBlockAnchor(world, stonePos),
                "flush-slab anchor must NOT carry the compound sidecar");
        double dy = SlabSupport.getYOffset(world, stonePos, stone);
        ctx.assertTrue(dy == -0.5,
                "control: placed stone on a flush bottom slab stays at -0.5; got dy=" + dy);
        ctx.complete();
    }

    /**
     * Follower parity on the compound top: an object resting ON a compound-frozen full block must
     * track its authored -1.0 surface (visual triad — the follower and its support may not disagree).
     * Before the sidecar the pair sat flush at the WRONG height (-0.5/-0.5); with it they sit flush
     * at the placed height (-1.0/-1.0). A 0.5 float here means the column walk missed the sidecar.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void torchOnPlacedCompoundTopSitsFlush(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);
        BlockState bs = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        world.setBlockState(base, bs, Block.NOTIFY_LISTENERS);                                     // L0 slab (0)
        world.setBlockState(base.up(1), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);   // L1 stone (-0.5)
        world.setBlockState(base.up(2), bs, Block.NOTIFY_LISTENERS);                               // L2 slab (-0.5)
        BlockPos topPos = base.up(3);
        authorBlock(world, topPos, Blocks.STONE.getDefaultState());                                // L3 compound top (-1.0)
        ctx.assertTrue(SlabAnchorAttachment.isCompoundFullBlockAnchor(world, topPos),
                "precondition: stack top must carry the compound sidecar");

        BlockPos torchPos = topPos.up();
        world.setBlockState(torchPos, Blocks.TORCH.getDefaultState(), Block.NOTIFY_LISTENERS);
        double topDy = SlabSupport.getYOffset(world, topPos, world.getBlockState(topPos));
        double torchDy = SlabSupport.getYOffset(world, torchPos, world.getBlockState(torchPos));
        ctx.assertTrue(topDy == -1.0, "precondition: compound top reads -1.0; got " + topDy);
        ctx.assertTrue(torchDy == -1.0,
                "torch on a compound-frozen top must follow its -1.0 surface (flush); got dy=" + torchDy
                + " (float gap=" + (torchDy - topDy) + ")");
        ctx.complete();
    }

    /**
     * DODO guard: powder snow (a full cube, NOT a SnowBlock) must stay FLUSH on a slab — it's natural
     * terrain fill, never offset. Lowering it -0.5 stepped it below neighbouring powder snow on full
     * ground (the pulled-hotfix snowy-terrain DODO). PowderSnowBlock isn't a SnowBlock, so isThinTopLayer
     * never excluded it, and it's a slab-sit candidate (non-opaque), so the directCustom lane would lower it.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void powderSnowOnSlabStaysFlush(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);
        world.setBlockState(base, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        world.setBlockState(base.up(1), Blocks.POWDER_SNOW.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(world, base.up(1), world.getBlockState(base.up(1)));
        ctx.assertTrue(dy == 0.0, "powder snow on a slab must stay flush (0), not step -0.5; got " + dy);
        ctx.complete();
    }

    /**
     * DODO guard: a natural (setBlockState, non-anchored) opaque full cube resting on another solid cube
     * must NOT lower -0.5 just because a slab sits deeper in the column — walking through solid terrain to
     * a slab is exactly what tore see-through world holes across Terrain Slabs terrain. The column walk
     * stops at the solid cube. (A genuine PLACED tower still chains via its per-block anchor.)
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void naturalCubeOverSolidDoesNotLowerThroughToSlab(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);
        world.setBlockState(base, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);                                                           // L0 slab
        world.setBlockState(base.up(1), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);   // L1 stone on slab
        world.setBlockState(base.up(2), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);   // L2 stone on L1 (solid ground)
        double l2 = SlabSupport.getYOffset(world, base.up(2), world.getBlockState(base.up(2)));
        ctx.assertTrue(l2 == 0.0,
                "DODO: a stone resting on a solid cube must stay flush, not lower -0.5 through it to a slab below; got " + l2);
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

    // ══════════════════════════════════════════════════════════════════════════
    // Offset-aware nearest-hit raycast (targeting overhaul port from 1.21.11 main,
    // origin 39a345e7). These tests call SlabbedOffsetRaycast.raycast directly on
    // the test ServerWorld (whose outline shapes are dy-offset by the common
    // SlabSupportStateMixin), so the targeting geometry is verified with no rendered
    // client and no rescue heuristics. Tests that assert the fix also assert the
    // matching VANILLA world.raycast result to document the exact divergence the
    // offset-aware raycast corrects.
    // ══════════════════════════════════════════════════════════════════════════

    private static final double RAY_EPS = 1.0e-6;

    /** Offset-aware nearest-hit raycast (the system under test). */
    private static net.minecraft.util.hit.BlockHitResult slabbedRay(
            ServerWorld world, net.minecraft.util.math.Vec3d eye, net.minecraft.util.math.Vec3d end) {
        return com.slabbed.util.SlabbedOffsetRaycast.raycast(world, eye, end, ShapeContext.absent());
    }

    /** Stock vanilla outline raycast (no fluids), exactly as the pick path would do it. */
    private static net.minecraft.util.hit.HitResult vanillaRay(
            ServerWorld world, net.minecraft.util.math.Vec3d eye, net.minecraft.util.math.Vec3d end) {
        return world.raycast(new net.minecraft.world.RaycastContext(
                eye, end,
                net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                ShapeContext.absent()));
    }

    private static net.minecraft.util.math.Vec3d rayV(BlockPos origin, double dx, double dy, double dz) {
        return new net.minecraft.util.math.Vec3d(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
    }

    /**
     * Build a lowered full block: a bottom slab with a solid full block directly on top.
     * The full block resolves to dy=-0.5 via live {@link SlabSupport#getYOffset}
     * (FB-on-bottom-slab), so its outline spans world Y [F.y-0.5, F.y+0.5].
     * Returns the full-block position.
     */
    private static BlockPos buildLoweredFullBlock(ServerWorld world, BlockPos slabPos) {
        world.setBlockState(slabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos fullPos = slabPos.up();
        world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        return fullPos;
    }

    // 1. Sanity: aiming straight down at a lowered block's visual top hits it.
    //    (Vanilla already gets this right — the visual top lies inside the logical
    //    cell — so it doubles as a parity check.)
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void offsetRaycastLoweredFullBlockTopFaceTargetsBlock(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos full = buildLoweredFullBlock(world, origin.add(3, 2, 3));

        ctx.assertTrue(SlabSupport.getYOffset(world, full, world.getBlockState(full)) == -0.5,
                "fixture invalid: full block should be lowered -0.5");

        // Aim straight down at the visual top centre (world Y = full.y + 0.5).
        net.minecraft.util.math.Vec3d eye = rayV(origin, 3.5, 6.0, 3.5);
        net.minecraft.util.math.Vec3d end = rayV(origin, 3.5, 0.0, 3.5);
        net.minecraft.util.hit.BlockHitResult hit = slabbedRay(world, eye, end);

        ctx.assertTrue(hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK, "expected a block hit");
        ctx.assertTrue(hit.getBlockPos().equals(full),
                "expected hit on lowered full block " + full + ", got " + hit.getBlockPos());
        ctx.assertTrue(hit.getSide() == Direction.UP, "expected UP face, got " + hit.getSide());
        ctx.assertTrue(Math.abs(hit.getPos().y - (full.getY() + 0.5)) < 1.0e-4,
                "expected hit Y at visual top " + (full.getY() + 0.5) + ", got " + hit.getPos().y);
        ctx.complete();
    }

    // 2. THE BUG, FIXED: a near-horizontal ray at the lowered block's visual
    //    mid-height never enters the block's logical cell, so vanilla's voxel DDA
    //    cannot see it (returns MISS / the slab). The offset-aware raycast tests the
    //    up-neighbour and hits the block the player is actually looking at.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void offsetRaycastLoweredFullBlockMidHeightHorizontalRay(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos full = buildLoweredFullBlock(world, origin.add(3, 2, 3));

        // Horizontal ray at world Y = full.y - 0.25 (inside the lowered outline
        // [full.y-0.5, full.y+0.5], but in the cell layer of the slab BELOW).
        double y = full.getY() - 0.25;
        net.minecraft.util.math.Vec3d eye = rayV(origin, 3.5, y - origin.getY(), 0.5);
        net.minecraft.util.math.Vec3d end = rayV(origin, 3.5, y - origin.getY(), 7.5);

        net.minecraft.util.hit.BlockHitResult hit = slabbedRay(world, eye, end);
        ctx.assertTrue(hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK,
                "offset-aware raycast should hit the lowered block, got " + hit.getType());
        ctx.assertTrue(hit.getBlockPos().equals(full),
                "offset-aware raycast should target the lowered full block " + full
                        + ", got " + hit.getBlockPos());
        ctx.assertTrue(hit.getSide() == Direction.NORTH,
                "expected NORTH face (player-facing), got " + hit.getSide());

        // Negative control: stock vanilla cannot see the block at this aim.
        net.minecraft.util.hit.HitResult van = vanillaRay(world, eye, end);
        boolean vanillaSawBlock = van.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                && ((net.minecraft.util.hit.BlockHitResult) van).getBlockPos().equals(full);
        ctx.assertFalse(vanillaSawBlock,
                "control failed: vanilla DDA unexpectedly hit the lowered block — bug geometry invalid");
        ctx.complete();
    }

    // 3. NO REGRESSION: for ordinary (non-offset) blocks the offset-aware raycast
    //    returns exactly what vanilla returns, across several aim directions.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void offsetRaycastNonOffsetBlockMatchesVanilla(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos p = origin.add(4, 3, 4); // isolated, air all around -> dy = 0
        world.setBlockState(p, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.getYOffset(world, p, world.getBlockState(p)) == 0.0,
                "fixture invalid: isolated block must have dy 0");

        net.minecraft.util.math.Vec3d centre = rayV(origin, 4.5, 3.5, 4.5);
        net.minecraft.util.math.Vec3d[] eyes = {
                rayV(origin, 4.5, 6.5, 4.5),   // straight down -> UP face
                rayV(origin, 4.5, 3.5, 1.0),   // horizontal -> NORTH face
                rayV(origin, 1.0, 3.5, 4.5),   // horizontal -> WEST face
                rayV(origin, 1.0, 6.0, 1.0),   // diagonal from above
                rayV(origin, 7.5, 3.5, 7.5),   // horizontal from +X+Z corner
        };
        for (int i = 0; i < eyes.length; i++) {
            net.minecraft.util.math.Vec3d eye = eyes[i];
            net.minecraft.util.math.Vec3d end = centre.add(centre.subtract(eye).normalize().multiply(0.5));
            net.minecraft.util.hit.BlockHitResult mine = slabbedRay(world, eye, end);
            net.minecraft.util.hit.HitResult van = vanillaRay(world, eye, end);
            ctx.assertTrue(van.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK,
                    "ray " + i + " control: vanilla should hit the block");
            ctx.assertTrue(mine.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK,
                    "ray " + i + ": offset raycast should hit the block");
            net.minecraft.util.hit.BlockHitResult vanBlock = (net.minecraft.util.hit.BlockHitResult) van;
            ctx.assertTrue(mine.getBlockPos().equals(vanBlock.getBlockPos()),
                    "ray " + i + ": pos mismatch mine=" + mine.getBlockPos() + " vanilla=" + vanBlock.getBlockPos());
            ctx.assertTrue(mine.getSide() == vanBlock.getSide(),
                    "ray " + i + ": side mismatch mine=" + mine.getSide() + " vanilla=" + vanBlock.getSide());
            ctx.assertTrue(mine.getPos().squaredDistanceTo(vanBlock.getPos()) < RAY_EPS,
                    "ray " + i + ": hit point mismatch mine=" + mine.getPos() + " vanilla=" + vanBlock.getPos());
        }
        ctx.complete();
    }

    // 4. NEAREST WINS: a plain block in front of a lowered block must be selected —
    //    the offset-aware raycast must not over-eagerly grab the offset block.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void offsetRaycastNearestVisualSurfaceWins(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos full = buildLoweredFullBlock(world, origin.add(3, 2, 5));   // lowered block, far (+Z)
        BlockPos near = origin.add(3, 2, 2);                                  // plain block, near (-Z), dy 0
        world.setBlockState(near, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        // Same mid-height horizontal ray as test 2; the near block intersects first.
        double y = full.getY() - 0.25; // 0.75 within the near block's [y,y+1]
        net.minecraft.util.math.Vec3d eye = rayV(origin, 3.5, y - origin.getY(), 0.5);
        net.minecraft.util.math.Vec3d end = rayV(origin, 3.5, y - origin.getY(), 7.5);

        net.minecraft.util.hit.BlockHitResult hit = slabbedRay(world, eye, end);
        ctx.assertTrue(hit.getBlockPos().equals(near),
                "nearest block should win: expected " + near + ", got " + hit.getBlockPos());
        ctx.complete();
    }

    // 5. LOWERED SIDE SLAB: a bottom slab beside a lowered full block inherits
    //    dy=-0.5; aiming at its visual body targets it, where vanilla again cannot
    //    at mid-height.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void offsetRaycastLoweredSideSlabTargeted(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos full = buildLoweredFullBlock(world, origin.add(3, 2, 3));
        BlockPos sideSlab = full.east(); // (4, full.y, 3)
        world.setBlockState(sideSlab,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);

        double slabDy = SlabSupport.getYOffset(world, sideSlab, world.getBlockState(sideSlab));
        ctx.assertTrue(slabDy == -0.5,
                "fixture invalid: side slab beside lowered FB should inherit dy=-0.5, got " + slabDy);

        // Lowered bottom slab outline spans world Y [slab.y-0.5, slab.y]; aim at its
        // east face at mid-height (slab.y - 0.25), a layer vanilla DDA skips.
        double y = sideSlab.getY() - 0.25;
        net.minecraft.util.math.Vec3d eye = rayV(origin, 7.5, y - origin.getY(), 3.5);
        net.minecraft.util.math.Vec3d end = rayV(origin, 4.0, y - origin.getY(), 3.5);

        net.minecraft.util.hit.BlockHitResult hit = slabbedRay(world, eye, end);
        ctx.assertTrue(hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                        && hit.getBlockPos().equals(sideSlab),
                "offset-aware raycast should target the lowered side slab " + sideSlab
                        + ", got " + hit.getType() + " " + hit.getBlockPos());
        ctx.assertTrue(hit.getSide() == Direction.EAST,
                "expected EAST face, got " + hit.getSide());

        net.minecraft.util.hit.HitResult van = vanillaRay(world, eye, end);
        boolean vanillaSawSlab = van.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                && ((net.minecraft.util.hit.BlockHitResult) van).getBlockPos().equals(sideSlab);
        ctx.assertFalse(vanillaSawSlab,
                "control failed: vanilla unexpectedly hit the lowered side slab at mid-height");
        ctx.complete();
    }

    // 6. COMPAT-BRANCH TRIAD (deliberate divergence from the 1.21.11 main donor):
    //    on this branch a fence on a VANILLA slab RENDERS lowered (GH #21, 92516668 —
    //    OffsetBlockStateModel dy always tracks getYOffset for connecting blocks), so
    //    its outline IS offset and the nearest-hit raycast must target the fence at
    //    its lowered position. Main instead zeroes connecting-block render dy and
    //    gates the outline to match; porting that gate here would break the triad.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void offsetRaycastLoweredFenceOutlineOffsetAndTargeted(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos slab = origin.add(3, 2, 3);
        world.setBlockState(slab,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos fence = slab.up();
        world.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

        double dy = SlabSupport.getYOffset(world, fence, world.getBlockState(fence));
        ctx.assertTrue(dy == -0.5,
                "fixture: fence on a vanilla bottom slab must be lowered -0.5 on this branch (GH #21), got " + dy);

        VoxelShape outline = world.getBlockState(fence).getOutlineShape(world, fence, ShapeContext.absent());
        ctx.assertFalse(outline.isEmpty(), "fence outline should be non-empty");
        double minY = outline.getBoundingBox().minY;
        ctx.assertTrue(Math.abs(minY - (-0.5)) < RAY_EPS,
                "fence-on-slab outline must be offset -0.5 to match its lowered render (GH #21), got minY=" + minY);

        // Aim horizontally at the fence post at the lowered mid-height (fence.y - 0.25),
        // a layer inside the slab-below's cell that vanilla DDA never resolves to the fence.
        double y = fence.getY() - 0.25;
        net.minecraft.util.math.Vec3d eye = rayV(origin, 3.5, y - origin.getY(), 0.5);
        net.minecraft.util.math.Vec3d end = rayV(origin, 3.5, y - origin.getY(), 7.5);
        net.minecraft.util.hit.BlockHitResult hit = slabbedRay(world, eye, end);
        ctx.assertTrue(hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                        && hit.getBlockPos().equals(fence),
                "offset-aware raycast should target the lowered fence " + fence
                        + ", got " + hit.getType() + " " + hit.getBlockPos());

        net.minecraft.util.hit.HitResult van = vanillaRay(world, eye, end);
        boolean vanillaSawFence = van.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                && ((net.minecraft.util.hit.BlockHitResult) van).getBlockPos().equals(fence);
        ctx.assertFalse(vanillaSawFence,
                "control failed: vanilla unexpectedly resolved the lowered fence at mid-height");
        ctx.complete();
    }

    // 7. TORCH via its OWN offset shape (proves the slab-side comfort union is not
    //    needed once the nearest-hit raycast is authoritative).
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void offsetRaycastLoweredFloorTorchTargetedViaOwnShape(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos slab = origin.add(3, 2, 3);
        world.setBlockState(slab,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos torch = slab.up();
        world.setBlockState(torch, Blocks.TORCH.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(world.getBlockState(torch).isOf(Blocks.TORCH), "fixture: torch must survive on slab top");

        double dy = SlabSupport.getYOffset(world, torch, world.getBlockState(torch));
        ctx.assertTrue(dy == -0.5, "fixture: floor torch on slab should be lowered -0.5, got " + dy);

        // Aim horizontally at the torch column at the lowered comfort-post mid-height.
        double y = torch.getY() - 0.25;
        net.minecraft.util.math.Vec3d eye = rayV(origin, 3.5, y - origin.getY(), 0.5);
        net.minecraft.util.math.Vec3d end = rayV(origin, 3.5, y - origin.getY(), 7.5);
        net.minecraft.util.hit.BlockHitResult hit = slabbedRay(world, eye, end);
        ctx.assertTrue(hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                        && hit.getBlockPos().equals(torch),
                "offset-aware raycast should target the lowered floor torch " + torch
                        + ", got " + hit.getType() + " " + hit.getBlockPos());
        ctx.complete();
    }

    // 8. -1.0 COMPOUND owner (±1 window lower extreme): a full block on a lowered
    //    side slab renders at [P.y-1.0, P.y], entirely in the cell below; the window
    //    must still find it.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void offsetRaycastCompoundMinusOneOwnerTargeted(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos baseSlab = origin.add(3, 2, 3);
        BlockPos loweredFull = buildLoweredFullBlock(world, baseSlab); // dy -0.5 at baseSlab.up()
        BlockPos sideSlab = loweredFull.east();                       // adjacent -> dy -0.5
        world.setBlockState(sideSlab,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos compound = sideSlab.up();                            // full block above lowered side slab
        world.setBlockState(compound, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        double dy = SlabSupport.getYOffset(world, compound, world.getBlockState(compound));
        ctx.assertTrue(dy == -1.0,
                "fixture: full block above lowered side slab should be compound dy=-1.0, got " + dy);

        // Compound outline spans [compound.y-1.0, compound.y]; aim at its upper region
        // (compound.y-0.25), which lies in the cell BELOW compound's logical cell.
        double y = compound.getY() - 0.25;
        net.minecraft.util.math.Vec3d eye = rayV(origin, 7.5, y - origin.getY(), sideSlab.getZ() + 0.5 - origin.getZ());
        net.minecraft.util.math.Vec3d end = rayV(origin, sideSlab.getX() + 0.0 - origin.getX(), y - origin.getY(),
                sideSlab.getZ() + 0.5 - origin.getZ());
        net.minecraft.util.hit.BlockHitResult hit = slabbedRay(world, eye, end);
        ctx.assertTrue(hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                        && hit.getBlockPos().equals(compound),
                "±1 window should find the -1.0 compound owner " + compound
                        + " from the cell below, got " + hit.getType() + " " + hit.getBlockPos());

        net.minecraft.util.hit.HitResult van = vanillaRay(world, eye, end);
        boolean vanillaSawCompound = van.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                && ((net.minecraft.util.hit.BlockHitResult) van).getBlockPos().equals(compound);
        ctx.assertFalse(vanillaSawCompound,
                "control: vanilla should not see the compound owner at this sub-cell aim");
        ctx.complete();
    }

    // 9. +0.5 CEILING owner (±1 window upper extreme): a hanging lantern under a top
    //    slab floats up +0.5; aim derived from its actual offset outline so the test
    //    is robust to the lantern's exact shape.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void offsetRaycastCeilingPlusHalfOwnerTargeted(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos topSlab = origin.add(3, 4, 3);
        world.setBlockState(topSlab,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        BlockPos lantern = topSlab.down();
        world.setBlockState(lantern,
                Blocks.LANTERN.getDefaultState().with(net.minecraft.state.property.Properties.HANGING, true),
                Block.NOTIFY_LISTENERS);

        double dy = SlabSupport.getYOffset(world, lantern, world.getBlockState(lantern));
        ctx.assertTrue(dy == 0.5,
                "fixture: hanging lantern under top slab should be +0.5, got " + dy);

        // Aim horizontally at the centre of the lantern's actual offset outline.
        VoxelShape outline = world.getBlockState(lantern).getOutlineShape(world, lantern, ShapeContext.absent());
        ctx.assertFalse(outline.isEmpty(), "lantern outline non-empty");
        double midY = lantern.getY() + (outline.getBoundingBox().minY + outline.getBoundingBox().maxY) / 2.0;
        net.minecraft.util.math.Vec3d eye = rayV(origin, 3.5, midY - origin.getY(), 0.5);
        net.minecraft.util.math.Vec3d end = rayV(origin, 3.5, midY - origin.getY(), 7.5);
        net.minecraft.util.hit.BlockHitResult hit = slabbedRay(world, eye, end);
        ctx.assertTrue(hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                        && hit.getBlockPos().equals(lantern),
                "offset-aware raycast should target the +0.5 hanging lantern " + lantern
                        + ", got " + hit.getType() + " " + hit.getBlockPos());
        ctx.complete();
    }

    // 10. PARITY on more non-offset geometry: double slab (full cube) and stairs
    //     (non-empty getRaycastShape) must match vanilla field-by-field.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void offsetRaycastDoubleSlabAndStairsMatchVanilla(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos dbl = origin.add(2, 3, 2);
        world.setBlockState(dbl,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                Block.NOTIFY_LISTENERS);
        BlockPos stair = origin.add(5, 3, 5);
        world.setBlockState(stair, Blocks.STONE_STAIRS.getDefaultState(), Block.NOTIFY_LISTENERS);

        BlockPos[] targets = { dbl, stair };
        for (BlockPos t : targets) {
            ctx.assertTrue(SlabSupport.getYOffset(world, t, world.getBlockState(t)) == 0.0,
                    "fixture: " + t + " must be non-offset");
            net.minecraft.util.math.Vec3d centre = rayV(origin,
                    t.getX() + 0.5 - origin.getX(), t.getY() + 0.5 - origin.getY(), t.getZ() + 0.5 - origin.getZ());
            net.minecraft.util.math.Vec3d[] eyes = {
                    centre.add(0, 3.0, 0),
                    centre.add(0, 0, -3.0),
                    centre.add(-3.0, 0, 0),
                    centre.add(2.5, 1.5, 2.5),
            };
            for (int i = 0; i < eyes.length; i++) {
                net.minecraft.util.math.Vec3d eye = eyes[i];
                net.minecraft.util.math.Vec3d end = centre.add(centre.subtract(eye).normalize().multiply(0.5));
                net.minecraft.util.hit.BlockHitResult mine = slabbedRay(world, eye, end);
                net.minecraft.util.hit.HitResult van = vanillaRay(world, eye, end);
                if (van.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) {
                    // If vanilla misses (grazing past a non-cube shape), only require the
                    // offset raycaster to also not invent a different block.
                    ctx.assertTrue(mine.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK
                                    || mine.getBlockPos().equals(t),
                            t + " ray " + i + ": unexpected hit " + mine.getBlockPos());
                    continue;
                }
                net.minecraft.util.hit.BlockHitResult vanBlock = (net.minecraft.util.hit.BlockHitResult) van;
                ctx.assertTrue(mine.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                                && mine.getBlockPos().equals(vanBlock.getBlockPos())
                                && mine.getSide() == vanBlock.getSide()
                                && mine.getPos().squaredDistanceTo(vanBlock.getPos()) < RAY_EPS,
                        t + " ray " + i + ": parity mismatch mine=(" + mine.getBlockPos() + "," + mine.getSide()
                                + ") vanilla=(" + vanBlock.getBlockPos() + "," + vanBlock.getSide() + ")");
            }
        }
        ctx.complete();
    }
}
