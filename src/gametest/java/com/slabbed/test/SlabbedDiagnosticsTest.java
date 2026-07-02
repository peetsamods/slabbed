package com.slabbed.test;

import com.slabbed.dev.SlabbedDiagnostics;
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
 * Proves the {@link SlabbedDiagnostics} detectors actually fire — both the pure predicates
 * (fed the exact bug values) and an integration pass over a real fence-on-slab. This is the
 * headlessly-verifiable half of the enriched recorder; the live capture wiring
 * (firing on crosshair-target change, reading the client render trace) is live-only.
 */
public final class SlabbedDiagnosticsTest {

    // ── pure predicate unit tests (no world needed) ──────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void triadMismatchDetectorFiresOnTheBugValues(TestContext ctx) {
        // Consistent triad: outline offset -0.5, collision at 0, visual -0.5 → applied -0.5 == visual.
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(-0.5, -0.5, 0.0),
                "a consistent triad (outline followed the dy) must NOT flag a mismatch");
        // The bug: outline stayed at grid (0.0) while visual lowered -0.5 → applied 0 != -0.5.
        ctx.assertTrue(SlabbedDiagnostics.triadMismatch(-0.5, 0.0, 0.0),
                "outline pinned at grid while visual lowered MUST flag a triad mismatch (the fence bug)");
        // Not decidable when collision is empty (torch/lantern) → never a false positive.
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(-0.5, Double.NaN, Double.NaN),
                "empty shapes must not produce a spurious mismatch");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dodoDetectorFiresOnLoweredOpaqueCube(TestContext ctx) {
        ctx.assertTrue(SlabbedDiagnostics.dodoRisk(true, -0.5),
                "an opaque full cube at a nonzero dy is a DODO (see-through hole) risk");
        ctx.assertTrue(!SlabbedDiagnostics.dodoRisk(true, 0.0),
                "a flush opaque cube is fine");
        ctx.assertTrue(!SlabbedDiagnostics.dodoRisk(false, -0.5),
                "a non-opaque block lowered is normal, not a DODO");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void smooshDetectorFiresOnDoubleOffsetDecoration(TestContext ctx) {
        BlockState lantern = Blocks.LANTERN.getDefaultState();
        BlockState oakSlab = Blocks.OAK_SLAB.getDefaultState();
        BlockState stone = Blocks.STONE.getDefaultState();
        ctx.assertTrue(SlabbedDiagnostics.smooshRisk(lantern, -1.0),
                "a decoration lowered a FULL block is a smoosh (double-offset) risk");
        ctx.assertTrue(!SlabbedDiagnostics.smooshRisk(lantern, -0.5),
                "a decoration lowered a single half-step is normal");
        ctx.assertTrue(!SlabbedDiagnostics.smooshRisk(oakSlab, -1.0),
                "a slab is not a smoosh subject (compound stacks are legitimate)");
        ctx.assertTrue(!SlabbedDiagnostics.smooshRisk(stone, -1.0),
                "an opaque full cube is a DODO, not a smoosh (classified separately)");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void gapDetectorFiresOnChainLanternDyMismatch(TestContext ctx) {
        BlockState chain = Blocks.IRON_CHAIN.getDefaultState();
        BlockState lantern = Blocks.LANTERN.getDefaultState();
        BlockState stone = Blocks.STONE.getDefaultState();
        ctx.assertTrue(SlabbedDiagnostics.dyDiscontinuity(chain, lantern, -0.5, 0.0),
                "a chain and lantern at different dy is a visible vertical gap");
        ctx.assertTrue(!SlabbedDiagnostics.dyDiscontinuity(chain, lantern, -0.5, -0.5),
                "a chain and lantern at the SAME dy connect cleanly (no gap)");
        ctx.assertTrue(!SlabbedDiagnostics.dyDiscontinuity(chain, stone, -0.5, 0.0),
                "a chain next to a non-decoration is not a decoration-gap");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void modelMismatchDetectorFiresWhenModelDivergesFromVisual(TestContext ctx) {
        ctx.assertTrue(SlabbedDiagnostics.modelMismatch(-0.5, 0.0),
                "a model rendered at grid while visual is lowered must flag (the pre-GH#21 fence render)");
        ctx.assertTrue(!SlabbedDiagnostics.modelMismatch(-0.5, -0.5),
                "model tracking visual is clean");
        ctx.assertTrue(!SlabbedDiagnostics.modelMismatch(-0.5, Double.NaN),
                "an unknown (server-side) model dy must not flag");
        ctx.complete();
    }

    // ── integration: a real fence-on-slab must analyze clean ─────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void realFenceOnSlabAnalyzesAsAConsistentLoweredTriad(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(slab, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos fence = slab.up();
        w.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

        SlabbedDiagnostics.Sample s = SlabbedDiagnostics.analyze(w, fence, w.getBlockState(fence));

        ctx.assertTrue(s.visualDy() < -1.0e-6,
                "fence on a vanilla slab must resolve a lowered dy, got " + SlabbedDiagnostics.format(s.visualDy()));
        ctx.assertTrue(!s.triadMismatch(),
                "post-GH#21-fix, the fence outline follows the dy, so no triad mismatch — got flags "
                        + s.flagSummary());
        ctx.assertTrue(!s.dodoRisk(), "a fence is not an opaque cube, so no DODO risk");
        ctx.assertTrue(!s.anySuspect(),
                "a correctly-lowered fence must analyze completely clean, got flags: " + s.flagSummary());
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatBlockAnalyzesClean(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        SlabbedDiagnostics.Sample s = SlabbedDiagnostics.analyze(w, pos, w.getBlockState(pos));
        ctx.assertTrue(Math.abs(s.visualDy()) < 1.0e-6, "flat stone must have dy 0");
        ctx.assertTrue(!s.anySuspect(), "flat stone on the ground must analyze clean, got: " + s.flagSummary());
        ctx.complete();
    }
}
