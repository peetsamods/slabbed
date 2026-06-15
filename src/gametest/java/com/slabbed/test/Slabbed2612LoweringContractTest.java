package com.slabbed.test;

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
}
