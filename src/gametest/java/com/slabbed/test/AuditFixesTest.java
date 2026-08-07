package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Regression pins for the fixes driven by the 2026-07-03 rigor audit (the "half-fix disease" pass).
 * Each test targets a specific uncovered case the audit found and constructs it RED-first.
 */
public final class AuditFixesTest {

    private static final double EPS = 1.0e-6;

    private static BlockState tsBottomSlab() {
        return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState vanillaBottomSlab() {
        return Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    // GH#22 audit: a full cube on a lowered NON-log curated carrier (jukebox / crafting table) must
    // share the carrier dy, not stay flush. RED (pre-fix): grass on a lowered jukebox = 0.0.
    private static void fullCubeSharesCarrierDy(TestContext ctx, BlockState carrier, String name) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos carrierPos = slab.up();
        BlockPos top = slab.up(2);
        w.setBlockState(slab, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(carrierPos, carrier, Block.NOTIFY_LISTENERS);
        w.setBlockState(top, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);

        double carrierDy = SlabSupport.getYOffset(w, carrierPos, w.getBlockState(carrierPos));
        ctx.assertTrue(Math.abs(carrierDy + 0.5) <= EPS,
                "setup: " + name + " on a TS slab should lower to -0.5, got " + carrierDy);
        double topDy = SlabSupport.getYOffset(w, top, w.getBlockState(top));
        ctx.assertTrue(Math.abs(topDy - carrierDy) <= EPS,
                "a full cube on a lowered " + name + " must share its dy " + carrierDy
                        + " (GH#22 curated-carrier gap); got " + topDy);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockOnTsLoweredJukeboxSharesLoweredDy(TestContext ctx) {
        fullCubeSharesCarrierDy(ctx, Blocks.JUKEBOX.getDefaultState(), "jukebox");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockOnTsLoweredCraftingTableSharesLoweredDy(TestContext ctx) {
        fullCubeSharesCarrierDy(ctx, Blocks.CRAFTING_TABLE.getDefaultState(), "crafting table");
    }

    // GH#21 audit: a fence GATE neighbor at a different visual height must be recognized as a
    // stepped connecting neighbor (so the fence/wall arm toward it is broken). RED (pre-fix):
    // isSteppedConnectingNeighbor returned false because the gate was dropped from the family.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceGateIsAsteppedConnectingNeighbor(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos fencePos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos gatePos = fencePos.east();
        // fence at grid (solid ground below), gate lowered onto a bottom slab in its own column.
        w.setBlockState(fencePos.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(fencePos, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(gatePos.down(), vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(gatePos, Blocks.OAK_FENCE_GATE.getDefaultState(), Block.NOTIFY_LISTENERS);

        double fenceDy = SlabSupport.getYOffset(w, fencePos, w.getBlockState(fencePos));
        double gateDy = SlabSupport.getYOffset(w, gatePos, w.getBlockState(gatePos));
        ctx.assertTrue(Math.abs(fenceDy) <= EPS && Math.abs(gateDy + 0.5) <= EPS,
                "setup: fence should be flush 0.0 (got " + fenceDy + ") and gate lowered -0.5 (got " + gateDy + ")");
        ctx.assertTrue(
                SlabSupport.isSteppedConnectingNeighbor(w, fencePos, w.getBlockState(fencePos),
                        gatePos, w.getBlockState(gatePos)),
                "a fence next to a height-stepped fence GATE must be a stepped connecting neighbor "
                        + "(gate was silently dropped from the connecting family)");
        ctx.complete();
    }

    // (A fence-gate flush-guard test was attempted here and REMOVED: its fixture did not reproduce
    //  a lowered support, so it passed even with the fix reverted — a vacuous test. The flush-guard
    //  gate fix is deferred to the internal notes until a valid headless fixture exists, per G3.)
}
