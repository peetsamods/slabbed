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
 * Live-reported bug (2026-07-03): "breaking [the] middle slab pops the other one up" for a
 * cantilevered row of Terrain Slabs slabs. Recorder-traced: a TS slab at a chain's far end
 * showed {@code anchor=ANCHORED} yet its own {@code visualDy} popped from {@code -0.500} to
 * {@code 0.000} the instant its neighbour was broken — a genuine internal inconsistency (the
 * SAME (world, pos, state) snapshot reporting both "anchored" and "not lowered").
 *
 * <p>Root cause: {@code getYOffset}'s very first gate is
 * {@code shouldSkipOffset(state) && !isAdjacentCustomSideSlabLowered(...) -> return 0.0}. For a
 * TS-owned slab, {@code isAdjacentCustomSideSlabLowered} is a LIVE BFS through the connected
 * slab chain, recomputed on every call — it never reads this position's own persisted anchor.
 * So whenever that early return fires, the anchor check deeper in {@code getYOffsetInner} is
 * never reached AT ALL: breaking the chain's lowering source makes the BFS fail, and the gate
 * unconditionally returns 0.0, silently overriding an anchor that exists for exactly this
 * "survive a later neighbour change" case. This NEVER affects vanilla slabs (they are not
 * {@code shouldSkipOffset}, so they skip this early gate entirely and reach the working anchor
 * check directly) — only TS-owned slabs, matching the live report precisely.
 *
 * <p>Fix: the early-return also checks {@code state instanceof SlabBlock && isAnchored(pos)},
 * letting an anchored TS slab fall through to the SAME anchor check every other block type
 * already uses.
 */
public final class TerrainSlabsChainAnchorTest {

    private static final double EPS = 1.0e-6;

    private static BlockState tsSlab(SlabType type) {
        return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void breakingChainSourceDoesNotPopAnchoredFarSlab(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos vanillaSlabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos dirt = vanillaSlabPos.up();  // ultimate lowering source: dirt on a vanilla bottom slab
        BlockPos a = dirt.east();             // TS slab A: air below, lowered via chain reaching dirt
        BlockPos bPos = a.east();             // TS slab B: air below, lowered ONLY via the chain reaching A

        w.setBlockState(vanillaSlabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirt, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dyDirt = SlabSupport.getYOffset(w, dirt, w.getBlockState(dirt));
        ctx.assertTrue(Math.abs(dyDirt + 0.5) <= EPS,
                "setup: dirt on a vanilla bottom slab should render -0.5, got " + dyDirt);

        w.setBlockState(a, tsSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        double dyA = SlabSupport.getYOffset(w, a, w.getBlockState(a));
        ctx.assertTrue(Math.abs(dyA + 0.5) <= EPS,
                "setup: TS slab A beside lowered dirt should render -0.5 (chain BFS), got " + dyA);

        w.setBlockState(bPos, tsSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        double dyBBefore = SlabSupport.getYOffset(w, bPos, w.getBlockState(bPos));
        ctx.assertTrue(Math.abs(dyBBefore + 0.5) <= EPS,
                "setup: TS slab B beside lowered TS slab A should render -0.5 (2-hop chain BFS), got " + dyBBefore);

        // Simulate real placement-time anchoring (BlockOnPlacedAnchorMixin -> addAnchor).
        SlabAnchorAttachment.addAnchor(w, bPos, w.getBlockState(bPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, bPos),
                "setup: TS slab B must qualify for the lowered-side-slab anchor at placement time");

        // Break the chain's MIDDLE link (slab A) — Maintainer's exact repro.
        w.breakBlock(a, false);
        ctx.assertTrue(w.getBlockState(a).isAir(), "setup: slab A must actually be broken");

        double dyBAfter = SlabSupport.getYOffset(w, bPos, w.getBlockState(bPos));
        ctx.assertTrue(Math.abs(dyBAfter + 0.5) <= EPS,
                "an ANCHORED TS slab must NOT pop up when the chain's middle link is broken "
                        + "(live 'breaking middle slab pops the other one up' bug); got " + dyBAfter);
        ctx.complete();
    }

    // REGRESSION GUARD: a TS slab that is NOT anchored (never qualified — e.g. placed flat, never
    // adjacent to a lowered chain) still correctly reads flush; the fix must not force -0.5 onto
    // an unrelated, un-anchored TS slab.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unanchoredTsSlabStaysFlush(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        w.setBlockState(pos, tsSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, pos), "setup: this TS slab must not be anchored");
        double dy = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "an un-anchored, isolated TS slab must stay flush (0.0); got " + dy);
        ctx.complete();
    }
}
