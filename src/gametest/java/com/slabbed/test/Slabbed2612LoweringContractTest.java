package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Deterministic dy/lowering contract tests for the 26.1.2 port's forward-ported
 * June fix families (Mojang-mapped server gametests).
 */
public final class Slabbed2612LoweringContractTest {

    private static final double EPS = 1.0e-6;

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static void assertDy(GameTestHelper helper, ServerLevel level, BlockPos rel,
                                 double expected, String what) {
        BlockPos abs = helper.absolutePos(rel);
        double dy = SlabSupport.getYOffset(level, abs, level.getBlockState(abs));
        if (Math.abs(dy - expected) > EPS) {
            throw helper.assertionException(rel,
                    what + ": expected dy=" + expected + " got " + dy);
        }
    }

    /**
     * compound-float (port of 21af4243): a full block on a bottom slab that is
     * itself lowered by a carrier BELOW it (the vertical slab/stone/slab/stone
     * case) must compound to -1.0 (flush), not float at -0.5.
     *
     * <p>Stack (each setBlock): base STONE, L0 bottom slab (dy 0), L1 STONE
     * (-0.5, on slab), L2 bottom slab (-0.5, on lowered stone via
     * hasLoweredCarrierBelow), L3 STONE (-1.0, on vertically-lowered slab).
     * RED before the fix: L3 reads -0.5 (floats 0.5 above L2's lowered top).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaVerticalCompoundStackTopMustBeFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = new BlockPos(2, 1, 2);

        helper.setBlock(base, Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(1), bottomSlab());                       // L0
        helper.setBlock(base.above(2), Blocks.STONE.defaultBlockState());   // L1
        helper.setBlock(base.above(3), bottomSlab());                       // L2
        helper.setBlock(base.above(4), Blocks.STONE.defaultBlockState());   // L3

        assertDy(helper, level, base.above(1), 0.0, "L0 bottom slab on solid ground");
        assertDy(helper, level, base.above(2), -0.5, "L1 stone on bottom slab");
        assertDy(helper, level, base.above(3), -0.5, "L2 bottom slab on lowered stone (vertical carrier)");
        assertDy(helper, level, base.above(4), -1.0, "L3 stone on vertically-lowered slab MUST be flush (-1.0)");

        helper.succeed();
    }

    /**
     * Authors a block via the real {@code setPlacedBy} path so
     * {@code BlockOnPlacedAnchorMixin} fires (addAnchor + freezeLoweredOnPlace).
     * Plain {@code helper.setBlock} is the terrain ({@code setBlockState}) path
     * that never calls onPlaced and stays geometric.
     */
    private static void authorBlock(GameTestHelper helper, ServerLevel level, BlockPos rel, BlockState state) {
        BlockPos abs = helper.absolutePos(rel);
        level.setBlock(abs, state, Block.UPDATE_ALL);
        state.getBlock().setPlacedBy(level, abs, level.getBlockState(abs), null, ItemStack.EMPTY);
    }

    /**
     * NEVER-POP freeze law (port of 8aafd1ff): a structural block AUTHORED flat
     * (dy=0) records FROZEN_FLAT and must stay flat when a bottom slab is later
     * placed directly under it — it must NOT autonomously pop down to -0.5.
     * RED before the freeze law: the stone recomputes geometrically and lowers.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void frozenFlatBlockStaysFlatWhenSlabAddedBelow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos blockRel = new BlockPos(2, 3, 2);

        // Author a flat stone in mid-air (real setPlacedBy path) → records FROZEN_FLAT at dy=0.
        authorBlock(helper, level, blockRel, Blocks.STONE.defaultBlockState());
        assertDy(helper, level, blockRel, 0.0, "authored flat stone before slab");

        // Terrain-place a bottom slab directly UNDER it (setBlockState, no onPlaced).
        helper.setBlock(blockRel.below(), bottomSlab());

        assertDy(helper, level, blockRel, 0.0,
                "frozen-flat stone MUST stay flat (0.0) after a slab is placed under it (Julia's NEVER-POP law)");
        helper.succeed();
    }

    /**
     * Control: a terrain ({@code setBlockState}) stone has NO FROZEN_FLAT marker,
     * so it still lowers geometrically to -0.5 on a bottom slab — proving the
     * freeze is gated to placed pieces and natural terrain stays geometric.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unfrozenBlockLowersWhenSlabAddedBelow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos blockRel = new BlockPos(2, 3, 2);

        helper.setBlock(blockRel.below(), bottomSlab());
        helper.setBlock(blockRel, Blocks.STONE.defaultBlockState());

        assertDy(helper, level, blockRel, -0.5,
                "unfrozen (terrain) stone on a bottom slab lowers to -0.5 (control)");
        helper.succeed();
    }

    /**
     * ceiling-hanger (port of 2a50335e): an always-ceiling-hung decoration (hanging
     * roots) takes its dy SOLELY from the support ABOVE. A flush support above must
     * keep it flush (0.0) even when a carrier lower in the column bridges down to a
     * slab — it must NOT be dragged down to -0.5. RED before the fix: the hanger
     * falls through the column walk and lowers.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingRootsUnderFlushSupportStayFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rootsRel = new BlockPos(2, 3, 2);

        // Flush CEILING SLAB directly above the hanger (a bottom slab stays flush, dy=0).
        helper.setBlock(rootsRel.above(), bottomSlab());
        // The hanger, hanging under the flush ceiling slab.
        helper.setBlock(rootsRel, Blocks.HANGING_ROOTS.defaultBlockState());
        // Carrier bridge (non-air) directly below the roots, then a bottom slab 2 cells below — the
        // exact configuration that bridged the downward column walk to a slab and wrongly lowered the
        // hanger -0.5 (a visible gap under the flush ceiling).
        helper.setBlock(rootsRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(rootsRel.below(2), bottomSlab());

        assertDy(helper, level, rootsRel.above(), 0.0, "ceiling slab should be flush");
        assertDy(helper, level, rootsRel, 0.0,
                "hanging roots under a flush ceiling slab MUST stay flush (0.0), not dragged down by a carrier below");
        helper.succeed();
    }

    /**
     * P2 (hanger underside-follow) — the always-ceiling-hung family. An always-ceiling-hung decoration
     * (hanging roots/spore/sign) under a LOWERED support must FOLLOW it down so it stays attached to the
     * support's lowered underside (no gap / no smoosh) — the same rule the HANGING lantern follows, here
     * for the roots-droop / sign-smoosh family. 26.1.2 unifies this through {@code ceilingHungDecorationDy}
     * (reads the support ABOVE's dy), so no separate 1.21.1-style underside reader is needed — this test
     * confirms that coverage. RED if the decoration were not routed through the ceiling-hung path: it
     * would read 0.0 while its support reads -0.5 (a visible gap, the reported droop).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingRootsFollowLoweredSupportAbove(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Lowered, anchored stone support with AIR below (so the hanger can occupy that cell).
        BlockPos support = new BlockPos(2, 3, 2);
        helper.setBlock(support.below(), bottomSlab());                    // temp carrier
        helper.setBlock(support, Blocks.STONE.defaultBlockState());        // stone -> -0.5
        BlockPos supportAbs = helper.absolutePos(support);
        SlabAnchorAttachment.addAnchor(level, supportAbs, level.getBlockState(supportAbs));
        helper.setBlock(support.below(), Blocks.AIR.defaultBlockState());  // remove carrier; anchor holds -0.5
        assertDy(helper, level, support, -0.5, "SETUP: anchored stone support stays lowered -0.5 with air below");

        BlockPos rootsRel = support.below();
        helper.setBlock(rootsRel, Blocks.HANGING_ROOTS.defaultBlockState());
        assertDy(helper, level, rootsRel, -0.5,
                "hanging roots under a LOWERED support MUST follow it down (-0.5), staying attached to its "
                + "lowered underside (no droop/gap) — same rule as the HANGING lantern");
        helper.succeed();
    }

    /**
     * powder-snow (port of da8cc3cb): powder snow is a FULL CUBE but natural terrain
     * fill — it must NEVER offset onto a slab, so it stays flush with neighbouring
     * powder snow on full ground (no -0.5 step / snowy-terrain DODO). It is NOT a
     * SnowBlock so isThinTopLayer never excluded it. RED before the fix: -0.5.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void powderSnowOnSlabStaysFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos snowRel = new BlockPos(2, 3, 2);
        helper.setBlock(snowRel.below(), bottomSlab());
        helper.setBlock(snowRel, Blocks.POWDER_SNOW.defaultBlockState());
        assertDy(helper, level, snowRel, 0.0,
                "powder snow (natural terrain full cube) MUST stay flush (0.0) on a slab, not step -0.5");
        helper.succeed();
    }

    /**
     * COLLISION-FOLLOW (BlockCollisionsLoweredAboveMixin): a lowered slab must be SOLID where it is
     * drawn. Its per-state collision is vanilla (cell's upper half) but the visual is the lower half,
     * so a box inside the visible lower-half (not reaching the slab's own cell) would pass straight
     * through without the broadphase above-check. RED before the mixin: noCollision=true (clip-in).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredSlabIsSolidAtVisualLowerHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Build a lowered slab, ANCHOR it via the real placement path (freeze law), then remove the
        // support below so it CANTILEVERS — lowered -0.5 with AIR below it (Julia's exact case).
        BlockPos base = new BlockPos(2, 2, 2);
        helper.setBlock(base, bottomSlab());                              // carrier
        helper.setBlock(base.above(), Blocks.STONE.defaultBlockState());  // lowered stone
        authorBlock(helper, level, base.above(2), bottomSlab());          // slab -> lowered -0.5 -> anchored
        BlockPos slabAbs = helper.absolutePos(base.above(2));

        // Remove the support below: the slab cantilevers (freeze-anchor keeps it -0.5; air below).
        helper.setBlock(base.above(), Blocks.AIR.defaultBlockState());
        helper.setBlock(base, Blocks.AIR.defaultBlockState());

        double dy = SlabSupport.getYOffset(level, slabAbs, level.getBlockState(slabAbs));
        if (Math.abs(dy + 0.5) > EPS) {
            throw helper.assertionException(base.above(2), "SETUP: cantilevered slab dy expected -0.5, got " + dy);
        }

        // A small box entirely inside the slab's lowered VISUAL lower-half [N-0.5, N] — now AIR (the
        // cell below was cleared), so ONLY the slab's hanging collision can block it. Vanilla
        // per-state collision is [N, N+0.5] (the cell's upper half), so without the broadphase
        // above-check this box passes straight through the visible slab.
        int n = slabAbs.getY();
        AABB inVisual = new AABB(slabAbs.getX() + 0.3, n - 0.4, slabAbs.getZ() + 0.3,
                slabAbs.getX() + 0.7, n - 0.1, slabAbs.getZ() + 0.7);
        if (level.noCollision(inVisual)) {
            throw helper.assertionException(base.above(2),
                    "CLIP-THROUGH: a box inside the cantilevered lowered slab's visible lower-half has NO "
                    + "collision — player clips through. The broadphase must yield the slab's hanging collision.");
        }
        helper.succeed();
    }

    /**
     * SNAP fix: a slab PLACED beside a lowered block but on flush ground must STAY flush — it must
     * NOT inherit the neighbour's lowered position (Julia's no-side-contagion law). RED before the
     * fix: the placed slab snaps to -0.5 (isAdjacentSideSlabLowered, then frozen by the anchor).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placedSlabBesideLoweredBlockStaysFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A lowered full block: stone on a bottom slab.
        BlockPos carrier = new BlockPos(2, 2, 2);
        helper.setBlock(carrier, bottomSlab());
        helper.setBlock(carrier.above(), Blocks.STONE.defaultBlockState());
        BlockPos loweredStone = carrier.above();

        // AUTHOR a bottom slab BESIDE the lowered stone, on FLUSH ground (the user's snap case).
        BlockPos beside = loweredStone.east();
        helper.setBlock(beside.below(), Blocks.STONE.defaultBlockState());
        authorBlock(helper, level, beside, bottomSlab());

        assertDy(helper, level, beside, 0.0,
                "slab placed beside a lowered block on flush ground MUST stay flush (no side-contagion snap)");
        helper.succeed();
    }

    /**
     * No-regression companion: a slab PLACED on top of a lowered carrier legitimately follows it
     * down to -0.5 (support-following, not side-contagion). The snap fix must NOT break this.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placedSlabOnLoweredCarrierStaysLowered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos carrier = new BlockPos(2, 2, 2);
        helper.setBlock(carrier, bottomSlab());
        helper.setBlock(carrier.above(), Blocks.STONE.defaultBlockState());

        BlockPos onTop = carrier.above(2);
        authorBlock(helper, level, onTop, bottomSlab());

        assertDy(helper, level, onTop, -0.5,
                "slab placed ON a lowered carrier follows it down (-0.5)");
        helper.succeed();
    }

    /**
     * SIDE-CONTAGION / "inheritances" (port of 83afed84): a FULL BLOCK placed against the
     * horizontal face of a lowered full-block carrier, but standing on its OWN flush ground,
     * must NOT inherit lowering. The old side-adjacent anchor (driven by
     * {@code BlockItemPlacementIntentMixin}) gave it a persistent stale anchor → it sank into
     * the ground and spread lowering onward (tree-canopy contagion). RED before the disable:
     * after the placement-intent call the block reads dy=-0.5 (anchored).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sideAdjacentFullBlockMustNotInheritLowering(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // A genuinely lowered full-block carrier: stone on a bottom slab (dy=-0.5).
        BlockPos carrier = new BlockPos(2, 2, 2);
        helper.setBlock(carrier, bottomSlab());
        helper.setBlock(carrier.above(), Blocks.STONE.defaultBlockState());
        BlockPos sourcePos = helper.absolutePos(carrier.above());
        BlockState sourceState = level.getBlockState(sourcePos);

        // A NEW full block beside it (east), standing on its OWN flush ground (stone below).
        BlockPos besideRel = carrier.above().east();
        helper.setBlock(besideRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(besideRel, Blocks.STONE.defaultBlockState());
        BlockPos besideAbs = helper.absolutePos(besideRel);
        BlockState besideState = level.getBlockState(besideAbs);

        // Drive the exact side-placement path BlockItemPlacementIntentMixin uses on a horizontal click.
        SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(level, besideAbs, besideState, sourcePos, sourceState);

        assertDy(helper, level, besideRel, 0.0,
                "full block placed beside a lowered carrier on its own flush ground MUST stay flush (0.0); "
                + "it must NOT inherit a side-adjacent anchor (Julia's no-inheritance law)");
        helper.succeed();
    }

    /**
     * CANTILEVER MERGE / "the consistent merge" (port of 9a24670c): a full block cantilevered
     * over AIR and connected horizontally to a genuinely lowered tower lowers -0.5 to merge,
     * computed live (never stale). Replaces the removed side-adjacent anchor with geometry.
     * RED before the feature: the cantilever block reads dy=0.0 (floats above the lowered tower).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cantileverFullBlockOverAirMergesWithLoweredTower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // Genuinely lowered tower: stone on a bottom slab (slab-below → genuine lowered source).
        BlockPos slab = new BlockPos(2, 2, 2);
        helper.setBlock(slab, bottomSlab());
        helper.setBlock(slab.above(), Blocks.STONE.defaultBlockState());
        assertDy(helper, level, slab.above(), -0.5, "tower stone on a bottom slab is lowered (source)");

        // Cantilever stone beside the lowered stone, with AIR directly below it (nothing placed at
        // slab.above().east().below()). It hangs out over empty space, connected to the tower.
        BlockPos cantileverRel = slab.above().east();
        helper.setBlock(cantileverRel, Blocks.STONE.defaultBlockState());

        assertDy(helper, level, cantileverRel, -0.5,
                "full block cantilevered over air, connected to a lowered tower, MUST merge to -0.5");
        helper.succeed();
    }

    /**
     * No-sink guard for the cantilever feature: a full block on its OWN solid ground beside a
     * lowered tower must STAY flush (air-gating) — only air-below blocks cantilever-merge. Must
     * hold both before and after 9a24670c (it is the safety rail, not a RED).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockOnSolidGroundBesideLoweredTowerStaysFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos slab = new BlockPos(2, 2, 2);
        helper.setBlock(slab, bottomSlab());
        helper.setBlock(slab.above(), Blocks.STONE.defaultBlockState());

        // Beside stone WITH its own solid ground below (so it is NOT a cantilever candidate).
        BlockPos besideRel = slab.above().east();
        helper.setBlock(besideRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(besideRel, Blocks.STONE.defaultBlockState());

        assertDy(helper, level, besideRel, 0.0,
                "full block on its OWN solid ground beside a lowered tower MUST stay flush (no sink)");
        helper.succeed();
    }

    /**
     * MERGE / stale compound anchor (the "log sinks into the flat slab below" + "anti-inheritance
     * violated"): a full block carrying a compound anchor (-1.0) must FOLLOW the slab directly below
     * it. If that slab is later flushed to 0.0, the block must sit on it at -0.5 — it must NOT stay
     * stuck at -1.0 and sink into the flush slab. RED before the fix: the stale compound sidecar
     * returns -1.0 unconditionally while the slab below reads 0.0 (the visible merge).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void compoundAnchorBlockMustFollowSlabBelowNotSinkWhenFlushed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = new BlockPos(2, 1, 2);

        // Genuine compound stack: ground, slab(0), stone(-0.5), slab(-0.5), log(-1.0).
        helper.setBlock(base, Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(1), bottomSlab());                       // L0 slab dy 0
        helper.setBlock(base.above(2), Blocks.STONE.defaultBlockState());   // L1 stone dy -0.5
        helper.setBlock(base.above(3), bottomSlab());                       // L2 slab dy -0.5
        BlockPos logRel = base.above(4);
        helper.setBlock(logRel, Blocks.SPRUCE_LOG.defaultBlockState());     // L3 log
        BlockPos logAbs = helper.absolutePos(logRel);

        // Record the compound anchor the real placement path would record (log on a lowered slab).
        SlabAnchorAttachment.addAnchor(level, logAbs, level.getBlockState(logAbs));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(level, logAbs, level.getBlockState(logAbs));
        assertDy(helper, level, logRel, -1.0, "SETUP: compound log starts lowered -1.0 on the lowered slab");

        // Now FLUSH the slab directly below the log: remove its lowering source (L1) so L2 -> 0.0.
        helper.setBlock(base.above(2), Blocks.AIR.defaultBlockState());

        BlockPos slabRel = base.above(3);
        double slabDy = SlabSupport.getYOffset(level, helper.absolutePos(slabRel), level.getBlockState(helper.absolutePos(slabRel)));
        double logDy = SlabSupport.getYOffset(level, logAbs, level.getBlockState(logAbs));
        // The block must sit on the slab below: logDy == slabDy - 0.5. Slab flushed to 0.0 => log -0.5.
        if (Math.abs(logDy - (slabDy - 0.5)) > EPS) {
            throw helper.assertionException(logRel,
                    "MERGE: log dy=" + logDy + " but the slab directly below reads dy=" + slabDy
                    + " (the log must follow it, dy=" + (slabDy - 0.5) + "). A stale compound anchor sinks "
                    + "the log -1.0 into the flushed slab.");
        }
        helper.succeed();
    }

    private static BlockState hangingLantern() {
        return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
    }

    /**
     * LANTERN SMOOSH (port of bbe3deb9): a HANGING lantern hangs from the block ABOVE it, so it must
     * take its dy from that support. When the support is lowered, the lantern must FOLLOW it down —
     * otherwise the lantern stays at the grid height and its chain/top pokes UP into the lowered
     * support (the "smoosh"). RED before the fix: the hanging lantern is not routed through the
     * ceiling-hung path and reads dy=0.0 while its support reads -0.5.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingLanternFollowsLoweredSupportAbove(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // Build a lowered, ANCHORED stone support with AIR below it (so the lantern can hang there):
        // place stone on a temp slab (-> -0.5), anchor it, then remove the slab (freeze keeps -0.5).
        BlockPos support = new BlockPos(2, 3, 2);
        helper.setBlock(support.below(), bottomSlab());                          // temp carrier
        helper.setBlock(support, Blocks.STONE.defaultBlockState());             // stone -> -0.5
        BlockPos supportAbs = helper.absolutePos(support);
        SlabAnchorAttachment.addAnchor(level, supportAbs, level.getBlockState(supportAbs));
        helper.setBlock(support.below(), Blocks.AIR.defaultBlockState());        // remove carrier; anchor holds -0.5
        assertDy(helper, level, support, -0.5, "SETUP: anchored stone support stays lowered -0.5 with air below");

        // Hanging lantern directly under the lowered support.
        BlockPos lanternRel = support.below();
        helper.setBlock(lanternRel, hangingLantern());

        assertDy(helper, level, lanternRel, -0.5,
                "HANGING lantern under a lowered support MUST follow it down (-0.5), not stay at 0.0 and "
                + "smoosh up into the lowered support");
        helper.succeed();
    }

    /**
     * No-regression companion: a HANGING lantern under a FLUSH support stays flush (0.0) — no -0.5
     * gap. Holds before and after the fix (ceilingHungDecorationDy returns 0.0 for a flush support).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingLanternUnderFlushSupportStaysFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = new BlockPos(2, 4, 2);
        helper.setBlock(support, Blocks.STONE.defaultBlockState());   // flush support
        BlockPos lanternRel = support.below();
        helper.setBlock(lanternRel, hangingLantern());
        assertDy(helper, level, lanternRel, 0.0,
                "HANGING lantern under a flush support stays flush (0.0), no -0.5 gap");
        helper.succeed();
    }

    /**
     * SNAP probe: a bottom slab AUTHORED (real placement path) on its own flush ground, but beside a
     * NAMED lowered slab lane, must STAY flush (Julia's NEVER-POP). If it snaps to -0.5 at placement
     * it has inherited the neighbor's lowering — a violation. (Diagnostic for "snapping down slab".)
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabPlacedBesideLoweredSlabLaneOnFlushGroundStaysFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // A NAMED lowered slab: bottom slab authored on a lowered stone -> anchored -0.5.
        BlockPos lane = new BlockPos(3, 3, 2);
        helper.setBlock(lane.below(2), bottomSlab());                          // (3,1,2)
        helper.setBlock(lane.below(), Blocks.STONE.defaultBlockState());       // (3,2,2) stone -0.5
        authorBlock(helper, level, lane, bottomSlab());                        // (3,3,2) slab -> anchored -0.5
        assertDy(helper, level, lane, -0.5, "SETUP: lane slab is a lowered (anchored) carrier");

        // Author a slab BESIDE the lane on its OWN flush ground (the player places it flat).
        BlockPos placed = new BlockPos(2, 3, 2);
        helper.setBlock(placed.below(), Blocks.STONE.defaultBlockState());     // (2,2,2) flush ground
        authorBlock(helper, level, placed, bottomSlab());

        assertDy(helper, level, placed, 0.0,
                "slab placed on its own flush ground beside a lowered slab lane MUST stay flush (NEVER-POP); "
                + "if it reads -0.5 it has snapped down / inherited the lane's lowering");
        helper.succeed();
    }

    /**
     * Adversarial pin for the compound-merge fix: a compound-anchored block on a slab that STAYS
     * genuinely lowered (-0.5) must keep reading -1.0. The fix must not collapse a real compound.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void compoundAnchorBlockStaysMinusOneOnGenuinelyLoweredSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(1), bottomSlab());                       // L0 slab 0
        helper.setBlock(base.above(2), Blocks.STONE.defaultBlockState());   // L1 stone -0.5
        helper.setBlock(base.above(3), bottomSlab());                       // L2 slab -0.5 (stays lowered)
        BlockPos logRel = base.above(4);
        helper.setBlock(logRel, Blocks.SPRUCE_LOG.defaultBlockState());
        BlockPos logAbs = helper.absolutePos(logRel);
        SlabAnchorAttachment.addAnchor(level, logAbs, level.getBlockState(logAbs));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(level, logAbs, level.getBlockState(logAbs));
        assertDy(helper, level, logRel, -1.0,
                "compound log on a genuinely lowered slab (-0.5) MUST stay -1.0 (real compound preserved)");
        helper.succeed();
    }

    /**
     * Adversarial pin for the compound-merge fix: when the slab below is REMOVED ENTIRELY (air below),
     * the authored compound -1.0 is preserved (NEVER-POP survive-removal) — the fix only follows a
     * slab that is still present but flushed, it does not pop the lane up on source removal.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void compoundAnchorBlockKeepsMinusOneWhenSlabBelowRemoved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(1), bottomSlab());                       // L0 slab 0
        helper.setBlock(base.above(2), Blocks.STONE.defaultBlockState());   // L1 stone -0.5
        helper.setBlock(base.above(3), bottomSlab());                       // L2 slab -0.5
        BlockPos logRel = base.above(4);
        helper.setBlock(logRel, Blocks.SPRUCE_LOG.defaultBlockState());
        BlockPos logAbs = helper.absolutePos(logRel);
        SlabAnchorAttachment.addAnchor(level, logAbs, level.getBlockState(logAbs));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(level, logAbs, level.getBlockState(logAbs));
        assertDy(helper, level, logRel, -1.0, "SETUP: compound log -1.0");

        // Remove the slab DIRECTLY below the log entirely (air below the log).
        helper.setBlock(base.above(3), Blocks.AIR.defaultBlockState());
        assertDy(helper, level, logRel, -1.0,
                "compound log with the slab below REMOVED (air) keeps -1.0 (survive-removal; no pop-up)");
        helper.succeed();
    }

    /**
     * RC2-A (WYSIWYG, supersedes the old SNAPPED-SLAB stay-flat decision at -2,-56,-1): a slab AUTHORED
     * cantilevered over AIR beside a lowered full block must FOLLOW to -0.5 to land exactly where the
     * crosshair aimed (the lowered surface). This REVERSES the pre-RC2 "stay flush" assertion — there is
     * no own flush ground here (air below), so WYSIWYG wins over NEVER-POP. The NEVER-POP rail is kept
     * for the SOLID-ground case (see rc2SlabOnSolidGroundBesideLoweredFullBlockStaysFlush).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabCantileveredBesideLoweredCarrierFollowsToMinusHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A lowered full block: stone on a bottom slab (dy=-0.5).
        BlockPos carrier = new BlockPos(2, 2, 2);
        helper.setBlock(carrier, bottomSlab());
        helper.setBlock(carrier.above(), Blocks.STONE.defaultBlockState());
        BlockPos loweredStone = carrier.above();
        assertDy(helper, level, loweredStone, -0.5, "SETUP: lowered carrier stone");

        // Author a slab BESIDE the lowered stone, cantilevered over AIR (nothing below it).
        BlockPos beside = loweredStone.east();          // (3,3,2); below (3,2,2) is air
        authorBlock(helper, level, beside, bottomSlab());

        assertDy(helper, level, beside, -0.5,
                "RC2-A: slab cantilevered over air beside a lowered full block FOLLOWS to -0.5 (WYSIWYG); "
                + "it lands flush with the aimed lowered surface");
        helper.succeed();
    }

    /**
     * RC2-C (WYSIWYG, supersedes the old SNAPPED-SLAB freeze-flat decision at -2,-56,-1): a slab
     * cantilevered (air below) beside a lowered SLAB lowers geometrically to -0.5; an AUTHORED (placed)
     * one must now ANCHOR -0.5 (Part C: slabLoweringIsSideInheritedOnly returns false when pos.below()
     * is air), NOT freeze FLAT. The control terrain slab (-0.5) is unchanged; the authored slab assert
     * is REVERSED from 0.0 to -0.5. The NEVER-POP rail survives for the SOLID-ground case
     * (see rc2cSlabOnSolidGroundBesideLoweredLaneFreezesFlat).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabBesideLoweredSlabColumnAuthoredFollowsToMinusHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Lowered slab column: ground, slab, stone(-0.5), slab(-0.5 via hasLoweredCarrierBelow).
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(1), bottomSlab());
        helper.setBlock(base.above(2), Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(3), bottomSlab());
        BlockPos loweredSlab = base.above(3);
        assertDy(helper, level, loweredSlab, -0.5, "SETUP: column top slab is lowered");

        BlockPos arm = loweredSlab.east();                 // cantilevered beside it, air below

        // Control: a TERRAIN slab here geometrically lowers -0.5 (the snap config is real).
        helper.setBlock(arm, bottomSlab());
        assertDy(helper, level, arm, -0.5,
                "CONTROL: terrain slab beside a lowered slab lowers geometrically to -0.5");

        // Authored (real placement path): Part C anchors it -0.5 (no longer frozen flat).
        helper.setBlock(arm, Blocks.AIR.defaultBlockState());
        authorBlock(helper, level, arm, bottomSlab());
        assertDy(helper, level, arm, -0.5,
                "RC2-C: AUTHORED slab cantilevered over air beside a lowered slab ANCHORS -0.5 (WYSIWYG), "
                + "no longer freezes flat");
        helper.succeed();
    }

    private static BlockState yChain() {
        return Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Y);
    }

    /**
     * Chain-vs-lantern distinction probe: a Y-axis CHAIN under a LOWERED support must NOT follow it
     * down the way a hanging lantern does — chains are "chainables" that keep their own connect
     * behavior (1.21.11 explicitly excludes ChainBlock from the hanger-follow). This documents the
     * CURRENT 26.1.2 chain dy so we can reason about the gap, and proves the lantern fix did not
     * sweep chains into the follow-down (which would smoosh/move them).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainUnderLoweredSupportDoesNotFollowDownLikeLantern(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = new BlockPos(2, 3, 2);
        helper.setBlock(support.below(), bottomSlab());
        helper.setBlock(support, Blocks.STONE.defaultBlockState());
        BlockPos supportAbs = helper.absolutePos(support);
        SlabAnchorAttachment.addAnchor(level, supportAbs, level.getBlockState(supportAbs));
        helper.setBlock(support.below(), Blocks.AIR.defaultBlockState());
        assertDy(helper, level, support, -0.5, "SETUP: lowered anchored support");

        helper.setBlock(support.below(), yChain());
        assertDy(helper, level, support.below(), 0.0,
                "Y-axis chain under a lowered support does NOT follow it down (chains keep connect-up; "
                + "unlike a hanging lantern which follows to -0.5)");
        helper.succeed();
    }

    /**
     * Documents that a Y-axis chain under a TOP slab raises +0.5 (the "connect up" behavior the user
     * sees as src=geometric dy=+0.500) — identical in 26.1.2 and 1.21.11.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainUnderTopSlabRaisesHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chain = new BlockPos(2, 3, 2);
        helper.setBlock(chain.above(), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        helper.setBlock(chain, yChain());
        assertDy(helper, level, chain, 0.5,
                "Y-axis chain directly under a TOP slab raises +0.5 to connect up (vanilla connect-up; same as 1.21.11)");
        helper.succeed();
    }

    /**
     * P26-8 targeting guard: the ceiling-bridged top chain model renders at the grid cell while the
     * normal +0.5 ceiling-attach outline shifts upward. The selection proxy must still cover the
     * visible lower end of the extended chain model.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void ceilingBridgedChainSelectionExtendsToVisibleBridge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chain = new BlockPos(2, 3, 2);
        helper.setBlock(chain.above(), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        helper.setBlock(chain, yChain());
        assertDy(helper, level, chain, 0.5,
                "P26-8 setup: direct Y-chain under a TOP slab still reports +0.5 for ceiling attach");

        BlockPos abs = helper.absolutePos(chain);
        BlockState actual = level.getBlockState(abs);
        VoxelShape outline = actual.getShape(level, abs, CollisionContext.empty());
        Vec3 from = new Vec3(abs.getX() + 0.5d, abs.getY() + 0.25d, abs.getZ() - 0.5d);
        Vec3 to = new Vec3(abs.getX() + 0.5d, abs.getY() + 0.25d, abs.getZ() + 1.5d);

        BlockHitResult outlineHit = outline.clip(from, to, abs);
        if (outlineHit == null || !outlineHit.getBlockPos().equals(abs)) {
            throw helper.assertionException(chain,
                    "P26-8: ceiling-bridged iron_chain is visible at local y=0.25 but the outline/raycast proxy misses");
        }
        helper.succeed();
    }

    /**
     * P26-6 render-path guard: the direct chain under a TOP slab is emitted through the extended
     * chain-ceiling model, which already bridges the half-block up to the slab. Descendant chains must
     * therefore stay at grid height; if they also inherit +0.5, they overlap the extended top chain and
     * visually merge.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainColumnUnderTopSlabKeepsDescendantsGridHeight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos upperChain = new BlockPos(2, 3, 2);
        BlockPos lowerChain = upperChain.below();
        helper.setBlock(upperChain.above(), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        helper.setBlock(upperChain, yChain());
        helper.setBlock(lowerChain, yChain());
        assertDy(helper, level, upperChain, 0.5,
                "P26-6 setup: direct Y-chain under a TOP slab still reports +0.5 for ceiling attach");
        assertDy(helper, level, lowerChain, 0.0,
                "P26-6: lower Y-chain under the extended top-chain model must stay grid-height, not overlap it");
        helper.succeed();
    }

    /**
     * P26-7 render-path guard: a hanging lantern under a ceiling-bridged chain column follows the
     * visible chain bottom, not the TOP-slab +0.5 inherited through the chain column. Otherwise the
     * lantern rises into the chain and looks merged/smooshed.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingLanternUnderCeilingBridgedChainStaysGridHeight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chain = new BlockPos(2, 3, 2);
        BlockPos lantern = chain.below();
        helper.setBlock(chain.above(), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        helper.setBlock(chain, yChain());
        helper.setBlock(lantern, hangingLantern());
        assertDy(helper, level, chain, 0.5,
                "P26-7 setup: direct Y-chain under a TOP slab still reports +0.5 for ceiling attach");
        assertDy(helper, level, lantern, 0.0,
                "P26-7: hanging lantern under the extended top-chain model must stay grid-height, not merge into the chain");
        helper.succeed();
    }

    // ── Connecting-block break-across-step (fence / pane / wall) — P1 ──────────
    // Port of the shipped 1.21.1/1.21.11 rule: a connector must not draw an arm across a slab-height
    // STEP (one member lowered onto a slab, the neighbour at grid height). These drive the real
    // updateShape mixin path: vanilla updateShape adds the connection, the mixin RETURN-injects and
    // breaks it iff the pair are stepped. The FLAT controls prove the mixin is conservative (a same-
    // height run still connects). A is WEST of B, so A's EAST side faces B.

    /** Stepped fence run: fence on a bottom slab (-0.5) beside a fence on stone (0.0) — EAST breaks. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void steppedFenceRunBreaksConnection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 1, 2), bottomSlab());                       // support under A → lowers A
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE.defaultBlockState());   // support under B → flush
        BlockPos a = new BlockPos(2, 2, 2);
        BlockPos b = new BlockPos(3, 2, 2);
        helper.setBlock(a, Blocks.OAK_FENCE.defaultBlockState());
        helper.setBlock(b, Blocks.OAK_FENCE.defaultBlockState());
        assertDy(helper, level, a, -0.5, "SETUP fence A on bottom slab is lowered");
        assertDy(helper, level, b, 0.0, "SETUP fence B on stone is flush");

        BlockState aAfter = Blocks.OAK_FENCE.defaultBlockState().updateShape(
                level, level, helper.absolutePos(a), net.minecraft.core.Direction.EAST,
                helper.absolutePos(b), level.getBlockState(helper.absolutePos(b)), level.getRandom());
        if (aAfter.getValue(CrossCollisionBlock.EAST)) {
            throw helper.assertionException(a,
                    "stepped fence A→B EAST connection must be BROKEN (no arm across a -0.5 step)");
        }
        helper.succeed();
    }

    /** Control: a flat fence run (both flush on stone) still connects — mixin only breaks across a step. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatFenceRunStillConnects(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE.defaultBlockState());
        BlockPos a = new BlockPos(2, 2, 2);
        BlockPos b = new BlockPos(3, 2, 2);
        helper.setBlock(a, Blocks.OAK_FENCE.defaultBlockState());
        helper.setBlock(b, Blocks.OAK_FENCE.defaultBlockState());
        assertDy(helper, level, a, 0.0, "SETUP flat fence A");
        assertDy(helper, level, b, 0.0, "SETUP flat fence B");

        BlockState aAfter = Blocks.OAK_FENCE.defaultBlockState().updateShape(
                level, level, helper.absolutePos(a), net.minecraft.core.Direction.EAST,
                helper.absolutePos(b), level.getBlockState(helper.absolutePos(b)), level.getRandom());
        if (!aAfter.getValue(CrossCollisionBlock.EAST)) {
            throw helper.assertionException(a,
                    "flat fence A→B EAST connection must REMAIN (mixin must not break a same-height run)");
        }
        helper.succeed();
    }

    /** Stepped iron-bars (pane family) run: same CrossCollisionBlock boolean side — EAST breaks. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void steppedIronBarsRunBreaksConnection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 1, 2), bottomSlab());
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE.defaultBlockState());
        BlockPos a = new BlockPos(2, 2, 2);
        BlockPos b = new BlockPos(3, 2, 2);
        helper.setBlock(a, Blocks.IRON_BARS.defaultBlockState());
        helper.setBlock(b, Blocks.IRON_BARS.defaultBlockState());
        assertDy(helper, level, a, -0.5, "SETUP iron-bars A on bottom slab is lowered");
        assertDy(helper, level, b, 0.0, "SETUP iron-bars B on stone is flush");

        BlockState aAfter = Blocks.IRON_BARS.defaultBlockState().updateShape(
                level, level, helper.absolutePos(a), net.minecraft.core.Direction.EAST,
                helper.absolutePos(b), level.getBlockState(helper.absolutePos(b)), level.getRandom());
        if (aAfter.getValue(CrossCollisionBlock.EAST)) {
            throw helper.assertionException(a,
                    "stepped iron-bars A→B EAST connection must be BROKEN (no arm across a -0.5 step)");
        }
        helper.succeed();
    }

    /** Stepped wall run: walls use WallSide side properties — broken side becomes WallSide.NONE. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void steppedWallRunBreaksConnection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 1, 2), bottomSlab());
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE.defaultBlockState());
        BlockPos a = new BlockPos(2, 2, 2);
        BlockPos b = new BlockPos(3, 2, 2);
        helper.setBlock(a, Blocks.COBBLESTONE_WALL.defaultBlockState());
        helper.setBlock(b, Blocks.COBBLESTONE_WALL.defaultBlockState());
        assertDy(helper, level, a, -0.5, "SETUP wall A on bottom slab is lowered");
        assertDy(helper, level, b, 0.0, "SETUP wall B on stone is flush");

        BlockState aAfter = Blocks.COBBLESTONE_WALL.defaultBlockState().updateShape(
                level, level, helper.absolutePos(a), net.minecraft.core.Direction.EAST,
                helper.absolutePos(b), level.getBlockState(helper.absolutePos(b)), level.getRandom());
        if (aAfter.getValue(WallBlock.EAST) != WallSide.NONE) {
            throw helper.assertionException(a,
                    "stepped wall A→B EAST side must be NONE (no arm across a -0.5 step), got "
                    + aAfter.getValue(WallBlock.EAST));
        }
        helper.succeed();
    }

    // ════════════════════════════════════════════════════════════════════════════════════════════
    // RC2 (WYSIWYG cantilever side-merge): a structural/connecting block placed CANTILEVERED over AIR
    // beside a LOWERED neighbour gets a geometric dy of -0.5 (Parts A/B/C). Every clause is air-gated:
    // the same block on SOLID ground beside a lowered lane keeps dy=0 / FROZEN_FLAT (NEVER-POP rail).
    // ════════════════════════════════════════════════════════════════════════════════════════════

    private static BlockState topSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    /** Builds a genuinely-lowered full-block tower at {@code carrierRel} (bottom slab + stone); returns the lowered stone rel. */
    private static BlockPos loweredTower(GameTestHelper helper, BlockPos carrierRel) {
        helper.setBlock(carrierRel, bottomSlab());
        helper.setBlock(carrierRel.above(), Blocks.STONE.defaultBlockState());
        return carrierRel.above();
    }

    // ── RC2-A: slab cantilevered over air beside a lowered FULL BLOCK → -0.5, BOTH halves ─────────

    /** RC2-A: a BOTTOM slab (lower-half aim) authored cantilevered over air beside a lowered full block → -0.5. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rc2aBottomSlabCantileverBesideLoweredFullBlockLowers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lowered = loweredTower(helper, new BlockPos(2, 2, 2));
        assertDy(helper, level, lowered, -0.5, "SETUP lowered full block");
        BlockPos arm = lowered.east();                       // below(arm) is air (cantilever)
        authorBlock(helper, level, arm, bottomSlab());
        assertDy(helper, level, arm, -0.5,
                "RC2-A: BOTTOM slab cantilevered over air beside a lowered full block lands -0.5 (WYSIWYG)");
        helper.succeed();
    }

    /** RC2-A: a TOP slab (upper-half aim) authored cantilevered over air beside a lowered full block also → -0.5. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rc2aTopSlabCantileverBesideLoweredFullBlockLowers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lowered = loweredTower(helper, new BlockPos(2, 2, 2));
        BlockPos arm = lowered.east();
        authorBlock(helper, level, arm, topSlab());
        assertDy(helper, level, arm, -0.5,
                "RC2-A: TOP slab (upper-half aim) cantilevered over air beside a lowered full block also lands -0.5 "
                + "(dy lowers BOTH types equally; the half only chose the TYPE at placement)");
        helper.succeed();
    }

    /** RC2-A NEVER-POP rail: a slab on its OWN solid ground beside a lowered full block stays flush (0.0). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rc2SlabOnSolidGroundBesideLoweredFullBlockStaysFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lowered = loweredTower(helper, new BlockPos(2, 2, 2));
        BlockPos arm = lowered.east();
        helper.setBlock(arm.below(), Blocks.STONE.defaultBlockState());   // OWN solid ground → not a cantilever
        authorBlock(helper, level, arm, bottomSlab());
        assertDy(helper, level, arm, 0.0,
                "RC2-A rail: slab on its own solid ground beside a lowered full block MUST stay flush (NEVER-POP)");
        helper.succeed();
    }

    // ── RC2-B: fence / wall / iron-bars cantilevered over air beside a lowered neighbour → -0.5 ───

    /** RC2-B: a fence cantilevered over air beside a lowered full block → -0.5. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rc2bFenceCantileverBesideLoweredFullBlockLowers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lowered = loweredTower(helper, new BlockPos(2, 2, 2));
        BlockPos arm = lowered.east();
        authorBlock(helper, level, arm, Blocks.OAK_FENCE.defaultBlockState());
        assertDy(helper, level, arm, -0.5,
                "RC2-B: fence cantilevered over air beside a lowered full block lands -0.5 (WYSIWYG)");
        helper.succeed();
    }

    /** RC2-B: a wall cantilevered over air beside a lowered full block → -0.5. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rc2bWallCantileverBesideLoweredFullBlockLowers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lowered = loweredTower(helper, new BlockPos(2, 2, 2));
        BlockPos arm = lowered.east();
        authorBlock(helper, level, arm, Blocks.COBBLESTONE_WALL.defaultBlockState());
        assertDy(helper, level, arm, -0.5,
                "RC2-B: wall cantilevered over air beside a lowered full block lands -0.5 (WYSIWYG)");
        helper.succeed();
    }

    /** RC2-B: iron-bars cantilevered over air beside a lowered full block → -0.5 (excluded from the old fence/wall reader). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rc2bIronBarsCantileverBesideLoweredFullBlockLowers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lowered = loweredTower(helper, new BlockPos(2, 2, 2));
        BlockPos arm = lowered.east();
        authorBlock(helper, level, arm, Blocks.IRON_BARS.defaultBlockState());
        assertDy(helper, level, arm, -0.5,
                "RC2-B: iron-bars cantilevered over air beside a lowered full block lands -0.5 (WYSIWYG)");
        helper.succeed();
    }

    /** RC2-B: a fence cantilevered over air beside a lowered SLAB lane → -0.5. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rc2bFenceCantileverBesideLoweredSlabLowers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(1), bottomSlab());
        helper.setBlock(base.above(2), Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(3), bottomSlab());                     // lowered slab -0.5
        assertDy(helper, level, base.above(3), -0.5, "SETUP lowered slab lane");
        BlockPos arm = base.above(3).east();                              // air below
        authorBlock(helper, level, arm, Blocks.OAK_FENCE.defaultBlockState());
        assertDy(helper, level, arm, -0.5,
                "RC2-B: fence cantilevered over air beside a lowered slab lane lands -0.5 (WYSIWYG)");
        helper.succeed();
    }

    /** RC2-B NEVER-POP rail: a fence on its OWN solid ground beside a lowered neighbour stays flush (0.0). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rc2bFenceOnSolidGroundBesideLoweredFullBlockStaysFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lowered = loweredTower(helper, new BlockPos(2, 2, 2));
        BlockPos arm = lowered.east();
        helper.setBlock(arm.below(), Blocks.STONE.defaultBlockState());   // own solid ground
        authorBlock(helper, level, arm, Blocks.OAK_FENCE.defaultBlockState());
        assertDy(helper, level, arm, 0.0,
                "RC2-B rail: fence on its own solid ground beside a lowered block MUST stay flush (NEVER-POP)");
        helper.succeed();
    }

    // ── RC2-C: freeze ANCHORS -0.5 (does not freeze FLAT) for an over-air cantilever slab ─────────

    /**
     * RC2-C: a slab AUTHORED cantilevered over air beside a lowered full block must ANCHOR -0.5
     * (freeze's dy<0 anchor branch), NOT freeze FLAT. Proven by removing the lowered source AFTER
     * placement: an ANCHORED slab holds -0.5; a FROZEN_FLAT one would already have read 0.0 at placement.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rc2cCantileverSlabAnchorsNotFreezesFlat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lowered = loweredTower(helper, new BlockPos(2, 2, 2));
        BlockPos arm = lowered.east();
        authorBlock(helper, level, arm, bottomSlab());
        assertDy(helper, level, arm, -0.5, "RC2-C: cantilever slab anchored -0.5 at placement (not frozen flat)");
        BlockPos armAbs = helper.absolutePos(arm);
        if (!SlabAnchorAttachment.isAnchored(level, armAbs)) {
            throw helper.assertionException(arm, "RC2-C: cantilever slab must be ANCHORED at placement");
        }
        if (SlabAnchorAttachment.isFrozenFlat(level, armAbs)) {
            throw helper.assertionException(arm, "RC2-C: cantilever slab must NOT be FROZEN_FLAT");
        }
        // Break the lowered source: an ANCHORED slab holds -0.5 (NEVER-POP on source removal).
        helper.setBlock(lowered, Blocks.AIR.defaultBlockState());
        assertDy(helper, level, arm, -0.5,
                "RC2-C: the cantilever slab was ANCHORED -0.5 (it holds after the source is removed)");
        helper.succeed();
    }

    /**
     * RC2-C NEVER-POP rail (must stay green): a slab authored on its OWN solid ground beside a lowered
     * slab LANE still freezes FLAT (0.0) — slabLoweringIsSideInheritedOnly returns true for the
     * solid-below case, so freeze records FROZEN_FLAT (Julia's law for a block on its own flush ground).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rc2cSlabOnSolidGroundBesideLoweredLaneFreezesFlat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(1), bottomSlab());
        helper.setBlock(base.above(2), Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(3), bottomSlab());
        assertDy(helper, level, base.above(3), -0.5, "SETUP lowered lane slab");
        BlockPos arm = base.above(3).east();
        helper.setBlock(arm.below(), Blocks.STONE.defaultBlockState());   // OWN solid ground
        authorBlock(helper, level, arm, bottomSlab());
        assertDy(helper, level, arm, 0.0,
                "RC2-C rail: slab on its own solid ground beside a lowered lane freezes FLAT (NEVER-POP)");
        BlockPos armAbs = helper.absolutePos(arm);
        if (!SlabAnchorAttachment.isFrozenFlat(level, armAbs)) {
            throw helper.assertionException(arm, "RC2-C rail: solid-ground slab must be FROZEN_FLAT");
        }
        helper.succeed();
    }

    // ── Compound -1.0 regression: RC2 must NOT intercept a compound hit ───────────────────────────

    /**
     * Compound -1.0 regression for RC2: the vertical compound stack top (L3 stone on a vertically-
     * lowered slab) must STILL read -1.0 — RC2-A/B sit AFTER the compound markers / anchor branch in
     * getYOffsetInner, so they can never down-shift a compound hit. (Pins the ordering constraint.)
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rc2CompoundStackTopStillMinusOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(1), bottomSlab());                       // L0 = 0
        helper.setBlock(base.above(2), Blocks.STONE.defaultBlockState());   // L1 = -0.5
        helper.setBlock(base.above(3), bottomSlab());                       // L2 = -0.5
        helper.setBlock(base.above(4), Blocks.STONE.defaultBlockState());   // L3 = -1.0
        assertDy(helper, level, base.above(1), 0.0, "L0");
        assertDy(helper, level, base.above(2), -0.5, "L1");
        assertDy(helper, level, base.above(3), -0.5, "L2");
        assertDy(helper, level, base.above(4), -1.0,
                "RC2 regression: vertical compound stack top still -1.0 (RC2 clauses run after compound markers)");
        helper.succeed();
    }

    // ════════════════════════════════════════════════════════════════════════════════════════════
    // RC2 GAP-1 (compound -1.0 magnitude) + GAP-2 (bare single lowered slab): the cantilever clauses
    // must carry the NEIGHBOUR's ACTUAL lowered dy out (-1.0 beside a compound stack; -0.5 beside a
    // bare lowered slab), not a hardcoded -0.5 / 0.0. Each scenario has a solid-ground NEVER-POP rail.
    // ════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Builds a GEOMETRIC vertical compound stack at {@code baseRel} (STONE / slab / STONE / slab /
     * STONE) whose TOP full block reads dy=-1.0, and returns that top rel. Same construction as
     * {@link #rc2CompoundStackTopStillMinusOne} (no anchors) — the -1.0 here comes from the
     * pure-geometric compound path (shouldOffset + bottom slab below that is itself a lowered lane),
     * the harder case that loweredFullBlockMagnitude must detect (an anchor-only reader would mis-read
     * it as -0.5).
     */
    private static BlockPos compoundLoweredTower(GameTestHelper helper, BlockPos baseRel) {
        helper.setBlock(baseRel, Blocks.STONE.defaultBlockState());
        helper.setBlock(baseRel.above(1), bottomSlab());                       // 0
        helper.setBlock(baseRel.above(2), Blocks.STONE.defaultBlockState());   // -0.5
        helper.setBlock(baseRel.above(3), bottomSlab());                       // -0.5
        helper.setBlock(baseRel.above(4), Blocks.STONE.defaultBlockState());   // -1.0 (compound top)
        return baseRel.above(4);
    }

    /**
     * Builds a BARE single lowered SLAB (anchored -0.5) with AIR directly below it — no full-block
     * column under it — and returns the bare-slab rel. The slab is authored CANTILEVERED over air
     * beside a lowered full-block tower (the proven RC2-A anchoring path of
     * {@link #rc2cCantileverSlabAnchorsNotFreezesFlat}: a cantilever slab ANCHORS -0.5, it does NOT
     * freeze flat), then the tower is REMOVED so the slab survives purely on its -0.5 anchor with air
     * below it. This is Julia's scenario #3 source in pure form: a lone lowered slab over air, with no
     * column the cantilever bridge could otherwise find.
     */
    private static BlockPos bareLoweredSlab(GameTestHelper helper, ServerLevel level, BlockPos bareRel) {
        BlockPos tower = bareRel.west();                                       // tower beside the bare slab
        loweredTower(helper, tower.below());                                   // genuine lowered full block at `tower`
        authorBlock(helper, level, bareRel, bottomSlab());                     // cantilever beside tower → anchors -0.5
        helper.setBlock(tower, Blocks.AIR.defaultBlockState());                // remove the source; the anchor holds
        helper.setBlock(tower.below(), Blocks.AIR.defaultBlockState());        // and its carrier slab
        return bareRel;
    }

    // ── GAP-1: cantilever beside a COMPOUND -1.0 stack → -1.0 (slab AND fence) ─────────────────────

    /** GAP-1: a SLAB cantilevered over air beside a compound -1.0 stack must land -1.0 (not -0.5). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void gap1SlabCantileverBesideCompoundMinusOneStackLowersFull(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = compoundLoweredTower(helper, new BlockPos(2, 1, 2));
        assertDy(helper, level, top, -1.0, "SETUP: compound stack top must be -1.0");
        BlockPos arm = top.east();                                            // below(arm) is air (cantilever)
        authorBlock(helper, level, arm, bottomSlab());
        assertDy(helper, level, arm, -1.0,
                "GAP-1: slab cantilevered over air beside a compound -1.0 stack lands -1.0 (WYSIWYG — matches "
                + "the aimed -1.0 surface, not half a block above at -0.5)");
        helper.succeed();
    }

    /** GAP-1: a FENCE cantilevered over air beside a compound -1.0 stack must land -1.0 (RC2-B magnitude). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void gap1FenceCantileverBesideCompoundMinusOneStackLowersFull(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = compoundLoweredTower(helper, new BlockPos(2, 1, 2));
        assertDy(helper, level, top, -1.0, "SETUP: compound stack top must be -1.0");
        BlockPos arm = top.east();
        authorBlock(helper, level, arm, Blocks.OAK_FENCE.defaultBlockState());
        assertDy(helper, level, arm, -1.0,
                "GAP-1: fence cantilevered over air beside a compound -1.0 stack lands -1.0 (RC2-B carries the "
                + "source magnitude out of the BFS, not -0.5)");
        helper.succeed();
    }

    /** GAP-1 NEVER-POP rail: a slab on its OWN solid ground beside a compound -1.0 stack stays flush (0.0). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void gap1SlabOnSolidGroundBesideCompoundStackStaysFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = compoundLoweredTower(helper, new BlockPos(2, 1, 2));
        BlockPos arm = top.east();
        helper.setBlock(arm.below(), Blocks.STONE.defaultBlockState());       // OWN solid ground → not a cantilever
        authorBlock(helper, level, arm, bottomSlab());
        assertDy(helper, level, arm, 0.0,
                "GAP-1 rail: slab on its own solid ground beside a compound -1.0 stack MUST stay flush (NEVER-POP)");
        helper.succeed();
    }

    /** GAP-1 NEVER-POP rail (fence): a fence on its OWN solid ground beside a compound -1.0 stack stays flush (0.0). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void gap1FenceOnSolidGroundBesideCompoundStackStaysFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = compoundLoweredTower(helper, new BlockPos(2, 1, 2));
        BlockPos arm = top.east();
        helper.setBlock(arm.below(), Blocks.STONE.defaultBlockState());       // OWN solid ground
        authorBlock(helper, level, arm, Blocks.OAK_FENCE.defaultBlockState());
        assertDy(helper, level, arm, 0.0,
                "GAP-1 rail: fence on its own solid ground beside a compound -1.0 stack MUST stay flush (NEVER-POP)");
        helper.succeed();
    }

    // ── GAP-2: slab cantilevered over air beside a BARE single lowered SLAB (no column) → -0.5 ─────

    /**
     * GAP-2 (Julia's scenario #3 in pure form): a slab cantilevered over air beside a BARE single
     * lowered SLAB — anchored -0.5 with AIR below it, no full-block column under the neighbour — must
     * land -0.5, not 0.0. Pre-fix isAdjacentLoweredFullBlockSource 'continue'd on the slab neighbour
     * and no column bridged it, so the cantilever slab read 0.0 (floated half a block too high).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void gap2SlabCantileverBesideBareLoweredSlabLowers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bare = bareLoweredSlab(helper, level, new BlockPos(2, 3, 2));
        assertDy(helper, level, bare, -0.5, "SETUP: bare single lowered slab (anchored -0.5, air below it)");
        if (!level.getBlockState(helper.absolutePos(bare.below())).isAir()) {
            throw helper.assertionException(bare, "SETUP: bare slab must have AIR (no column) directly below it");
        }
        BlockPos arm = bare.east();                                           // below(arm) is air (cantilever)
        authorBlock(helper, level, arm, bottomSlab());
        assertDy(helper, level, arm, -0.5,
                "GAP-2: slab cantilevered over air beside a BARE single lowered slab (no column) lands -0.5 "
                + "(the neighbour scan now accepts a lowered slab neighbour, not just a full block)");
        helper.succeed();
    }

    /** GAP-2 NEVER-POP rail: a slab on its OWN solid ground beside a bare lowered slab stays flush (0.0). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void gap2SlabOnSolidGroundBesideBareLoweredSlabStaysFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bare = bareLoweredSlab(helper, level, new BlockPos(2, 3, 2));
        assertDy(helper, level, bare, -0.5, "SETUP: bare single lowered slab");
        BlockPos arm = bare.east();
        helper.setBlock(arm.below(), Blocks.STONE.defaultBlockState());       // OWN solid ground → not a cantilever
        authorBlock(helper, level, arm, bottomSlab());
        assertDy(helper, level, arm, 0.0,
                "GAP-2 rail: slab on its own solid ground beside a bare lowered slab MUST stay flush (NEVER-POP)");
        helper.succeed();
    }

}
