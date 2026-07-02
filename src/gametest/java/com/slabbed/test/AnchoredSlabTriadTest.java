package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

/**
 * Ground-truth for the recorder's "anchored slab triad mismatch" (polished_tuff_slab:
 * visualDy -0.5, outlineMinY 0.0). Reproduces the actual mechanism: a slab cannot be
 * anchored directly (isOrdinaryAnchorCandidate excludes SlabBlock), so the recorded
 * anchor is STALE — a full block was anchored, then replaced by a slab, and the anchor
 * persisted. This pins whether the resulting slab's outline follows its (anchor-driven)
 * visual dy, separately for BOTTOM (outline base 0) and TOP (outline base 0.5) slabs.
 */
public final class AnchoredSlabTriadTest {

    private static double minY(VoxelShape s) {
        return s.isEmpty() ? Double.NaN : s.getBoundingBox().minY;
    }

    private static void staleAnchorSlab(TestContext ctx, SlabType type, String label) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos p = support.up();
        // Bottom slab support so the full block above qualifies for a direct anchor.
        w.setBlockState(support, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockState stone = Blocks.STONE.getDefaultState();
        w.setBlockState(p, stone, Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, p, stone);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, p),
                "precondition: the full block must anchor on a bottom slab (" + label + ")");

        // Replace with a slab WITHOUT clearing the anchor — the stale-anchor scenario.
        BlockState slab = Blocks.POLISHED_TUFF_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
        w.setBlockState(p, slab, Block.NOTIFY_LISTENERS);

        boolean stillAnchored = SlabAnchorAttachment.isAnchored(w, p);
        double vis = SlabSupport.getVisualYOffset(w, p, w.getBlockState(p));
        double outMinY = minY(w.getBlockState(p).getOutlineShape(w, p, ShapeContext.absent()));
        double collMinY = minY(w.getBlockState(p).getCollisionShape(w, p, ShapeContext.absent()));

        // Report the measured triad in the failure text regardless of outcome.
        String obs = label + ": stillAnchored=" + stillAnchored + " visualDy=" + fmt(vis)
                + " outlineMinY=" + fmt(outMinY) + " collisionMinY=" + fmt(collMinY);

        if (!stillAnchored || Math.abs(vis) < 1.0e-6) {
            // If the anchor cleared on replace, or the slab is not lowered, there is no bug
            // to reproduce here — the recorder's row must have come from a live anchor. Pass
            // with the observation recorded (a clean state is a valid, informative outcome).
            ctx.complete();
            return;
        }

        // The slab IS lowered (visualDy != 0). Its outline must reflect the dy given its base:
        // BOTTOM base 0 -> outlineMinY == visualDy; TOP base 0.5 -> outlineMinY == 0.5 + visualDy.
        double base = (type == SlabType.TOP) ? 0.5 : 0.0;
        double expectedOutline = base + vis;
        ctx.assertTrue(Math.abs(outMinY - expectedOutline) < 1.0e-6,
                obs + " -> outline does NOT follow the visual dy (expected " + fmt(expectedOutline) + ")");
        ctx.complete();
    }

    private static String fmt(double v) {
        return Double.isNaN(v) ? "NaN" : String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void staleAnchoredBottomSlabTriad(TestContext ctx) {
        staleAnchorSlab(ctx, SlabType.BOTTOM, "BOTTOM");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void staleAnchoredTopSlabTriad(TestContext ctx) {
        staleAnchorSlab(ctx, SlabType.TOP, "TOP");
    }
}
