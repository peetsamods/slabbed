package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The combined-slab CHAIN matrix — the project lesson "always check the full combined-slab chain
 * (every ordering + stacked + 3-high) before saying a lowering fix is tested". Verifies the deep
 * stacking behaviour and the key invariant that lowering CLAMPS at -1.0 (a block never sinks past a
 * full block below grid, no matter how deep the stack). All via {@link SlabSupport#getYOffset}.
 */
public final class Slabbed2612CompoundMatrixTest {

    private static final double EPS = 1.0e-6;

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static double dy(ServerLevel level, GameTestHelper helper, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        return SlabSupport.getYOffset(level, abs, level.getBlockState(abs));
    }

    /**
     * Deep alternating stack: ground, slab/stone/slab/stone/slab/stone. Each stone on a lowered slab
     * drops one more step, but lowering must CLAMP at -1.0 (never below). Asserts the proven first
     * levels and the clamp; logs the whole column.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deepAlternatingStackClampsAtMinusOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, Blocks.STONE.defaultBlockState());          // ground
        helper.setBlock(base.above(1), bottomSlab());                    // slab0 → 0.0
        helper.setBlock(base.above(2), Blocks.STONE.defaultBlockState()); // stone1 → -0.5
        helper.setBlock(base.above(3), bottomSlab());                    // slab2 → -0.5
        helper.setBlock(base.above(4), Blocks.STONE.defaultBlockState()); // stone2 → -1.0
        helper.setBlock(base.above(5), bottomSlab());                    // slab3 → ?
        helper.setBlock(base.above(6), Blocks.STONE.defaultBlockState()); // stone3 → ? (clamp -1.0)

        double[] col = new double[7];
        for (int i = 0; i <= 6; i++) {
            col[i] = dy(level, helper, base.above(i));
        }
        Slabbed.LOGGER.info("COMPOUND-MATRIX | deep stack dy by level (0=ground..6=top): "
                + "g={} slab0={} stone1={} slab2={} stone2={} slab3={} stone3={}",
                col[0], col[1], col[2], col[3], col[4], col[5], col[6]);

        // proven first levels
        if (Math.abs(col[1] - 0.0) > EPS) throw helper.assertionException(base.above(1), "slab0 must be 0.0, got " + col[1]);
        if (Math.abs(col[2] + 0.5) > EPS) throw helper.assertionException(base.above(2), "stone1 must be -0.5, got " + col[2]);
        if (Math.abs(col[3] + 0.5) > EPS) throw helper.assertionException(base.above(3), "slab2 must be -0.5, got " + col[3]);
        if (Math.abs(col[4] + 1.0) > EPS) throw helper.assertionException(base.above(4), "stone2 must be -1.0, got " + col[4]);

        // INVARIANT: nothing sinks below -1.0 (the clamp) and nothing rises above 0.0 in this stack.
        for (int i = 1; i <= 6; i++) {
            if (col[i] < -1.0 - EPS) {
                throw helper.assertionException(base.above(i),
                        "CLAMP VIOLATED: level " + i + " dy=" + col[i] + " sank below -1.0");
            }
            if (col[i] > EPS) {
                throw helper.assertionException(base.above(i),
                        "level " + i + " dy=" + col[i] + " rose above 0.0 unexpectedly");
            }
        }
        helper.succeed();
    }

    /**
     * Ordering check: a full block stacked DIRECTLY on a lowered full block (no intervening slab) —
     * slab/stone/stone. The lower stone is -0.5; pins the upper stone's behaviour (rests on the lowered
     * stone's top → -0.5, the one-step follow). Logged + asserted.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockStackedOnLoweredFullBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, bottomSlab());                              // slab → lowers stone1
        helper.setBlock(base.above(1), Blocks.STONE.defaultBlockState()); // stone1 → -0.5
        helper.setBlock(base.above(2), Blocks.STONE.defaultBlockState()); // stone2 on stone1
        double s1 = dy(level, helper, base.above(1));
        double s2 = dy(level, helper, base.above(2));
        Slabbed.LOGGER.info("COMPOUND-MATRIX | full-on-lowered-full: stone1={} stone2={}", s1, s2);
        if (Math.abs(s1 + 0.5) > EPS) throw helper.assertionException(base.above(1), "stone1 must be -0.5, got " + s1);
        if (Math.abs(s2 + 0.5) > EPS) {
            throw helper.assertionException(base.above(2),
                    "stone2 stacked on a lowered full block rests flush on its top at -0.5, got " + s2);
        }
        helper.succeed();
    }
}
