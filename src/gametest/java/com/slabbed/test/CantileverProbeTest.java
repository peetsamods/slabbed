package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
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
 * PROBE (not a pin): reproduce the live-reported "cantilevered slab placed 0.5 too high" and pinpoint
 * which config computes dy 0.0 instead of -0.5, and whether the reach-up deprecation is involved.
 * Each case logs its dy; the assertion documents the EXPECTED (-0.5) so a wrong value fails loudly.
 */
public final class CantileverProbeTest {

    private static final double EPS = 1.0e-6;

    private static BlockState vanillaBottomSlab() {
        return Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState topSlab() {
        return Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
    }

    // A lowered full-block support (stone on a bottom slab = -0.5), and a slab cantilevered off its
    // SIDE with air below. Expected: the cantilever slab reads -0.5 via isAdjacentSideSlabLowered.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bottomSlabCantileverOffLoweredFullBlock(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        w.setBlockState(base.down(), vanillaBottomSlab(), Block.NOTIFY_LISTENERS); // slab under support
        w.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS); // lowered -0.5 support
        BlockPos cant = base.east();
        w.setBlockState(cant, vanillaBottomSlab(), Block.NOTIFY_LISTENERS); // slab beside, AIR below
        double dy = SlabSupport.getYOffset(w, cant, w.getBlockState(cant));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "bottom slab cantilevered off a lowered full block should be -0.5; got " + dy);
        ctx.complete();
    }

    // Same, but a TOP slab (what the recorder showed).
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void topSlabCantileverOffLoweredFullBlock(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        w.setBlockState(base.down(), vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos cant = base.east();
        w.setBlockState(cant, topSlab(), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(w, cant, w.getBlockState(cant));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "TOP slab cantilevered off a lowered full block should be -0.5; got " + dy);
        ctx.complete();
    }

    // The freeze path: set up the FULL cantilever scene, THEN call freezeLoweredOnPlace (as real
    // placement would). If the geometry is right at freeze time, it must NOT freeze flat.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cantileverSlabDoesNotFreezeFlatWhenAlreadyLowered(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        w.setBlockState(base.down(), vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos cant = base.east();
        w.setBlockState(cant, topSlab(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.freezeLoweredOnPlace(w, cant, w.getBlockState(cant));
        boolean frozen = SlabAnchorAttachment.isFrozenFlat(w, cant);
        double dy = SlabSupport.getYOffset(w, cant, w.getBlockState(cant));
        ctx.assertTrue(!frozen && Math.abs(dy + 0.5) <= EPS,
                "cantilever slab beside an existing lowered block must NOT freeze flat; frozen=" + frozen + " dy=" + dy);
        ctx.complete();
    }

    // NOTE: the live cantilever bug (slab beside a dirt lowered via a COLUMN/terrain stack, not
    // directly on a slab, freezes flat) could NOT be reproduced headlessly — a full block on a
    // lowered full block renders 0.0 in a clean world (both with and without the reach-up
    // deprecation), yet the recorder shows the user's dirt at -0.5. The exact terrain-lowering
    // mechanism is unknown; tracked in KNOWN_INCOMPLETE.md L4 pending the live repro steps.

    // Chained cantilever: slab2 cantilevers off slab1 (which is itself a lowered cantilever). Both
    // should be -0.5. This is the row-of-slabs the recorder showed.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainedCantileverBothLowered(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        w.setBlockState(base.down(), vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos c1 = base.east();
        BlockPos c2 = base.east(2);
        w.setBlockState(c1, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(c2, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        double dy1 = SlabSupport.getYOffset(w, c1, w.getBlockState(c1));
        double dy2 = SlabSupport.getYOffset(w, c2, w.getBlockState(c2));
        ctx.assertTrue(Math.abs(dy1 + 0.5) <= EPS && Math.abs(dy2 + 0.5) <= EPS,
                "chained cantilever slabs should both be -0.5; got c1=" + dy1 + " c2=" + dy2);
        ctx.complete();
    }
}
