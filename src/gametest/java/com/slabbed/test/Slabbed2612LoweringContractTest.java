package com.slabbed.test;

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
}
