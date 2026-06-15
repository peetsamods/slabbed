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
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

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

}
