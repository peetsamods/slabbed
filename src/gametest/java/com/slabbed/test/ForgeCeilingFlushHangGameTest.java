package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * THE CEILING REACH-UP KILL — Maintainer's ruling (2026-07-27, upholding her 2026-07-03 live ruling on
 * the 26.2 reference): the +0.5 reach-up for ceiling-attached objects under a FLUSH top slab is
 * dead. Everything hangs FLUSH. In live testing the reach-up smooshed objects UP into the slab;
 * flush looked better.
 *
 * <p>Ported as the donor ports it: one choke point ({@code isLoweringTopLikeCeiling}, hard false)
 * replacing exactly the three +0.5-returning gates, so the ruling can never be applied to one
 * ceiling walk and forgotten on the others — this project's recorded shared-predicate lesson.
 * Trivially reversible if live testing regresses ("subject to further review").
 *
 * <p>DELIBERATELY PRESERVED, pinned below: the +0.5 merge compensation under a LOWERED top slab
 * (Maintainer's 2026-07-01 ruling — deliberate ceiling-mount geometry, not a bug) and flush hang under
 * ordinary full ceilings.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ForgeCeilingFlushHangGameTest {

    @GameTest(template = "empty")
    public void ceilingObjectsHangFlushUnderFlushTopSlabs(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockState topSlab = Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.TOP);

        // --- KILL ROW 1: hanging lantern directly under a FLUSH top slab -> flush (was +0.5) ---
        BlockPos a = ctx.absolutePos(new BlockPos(1, 3, 1));
        world.setBlock(a.above(), topSlab, Block.UPDATE_NONE);
        world.setBlock(a, Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), Block.UPDATE_NONE);
        assertDy(ctx, world, a, 0.0d, "hanging lantern under a FLUSH top slab must hang flush");

        // --- KILL ROW 2: chain directly under a FLUSH top slab -> flush (was +0.5) ---
        BlockPos b = ctx.absolutePos(new BlockPos(3, 3, 1));
        world.setBlock(b.above(), topSlab, Block.UPDATE_NONE);
        world.setBlock(b, Blocks.CHAIN.defaultBlockState(), Block.UPDATE_NONE);
        assertDy(ctx, world, b, 0.0d, "chain under a FLUSH top slab must hang flush");

        // --- KILL ROW 3: the cascade — second dripstone segment under a flush top slab ---
        BlockPos c1 = ctx.absolutePos(new BlockPos(5, 3, 1));
        BlockPos c2 = c1.below();
        world.setBlock(c1.above(), topSlab, Block.UPDATE_NONE);
        world.setBlock(c1, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(BlockStateProperties.VERTICAL_DIRECTION, net.minecraft.core.Direction.DOWN),
                Block.UPDATE_NONE);
        world.setBlock(c2, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(BlockStateProperties.VERTICAL_DIRECTION, net.minecraft.core.Direction.DOWN),
                Block.UPDATE_NONE);
        assertDy(ctx, world, c1, 0.0d, "first dripstone under a flush top slab must hang flush");
        assertDy(ctx, world, c2, 0.0d, "cascaded dripstone under a flush top slab must hang flush");

        // --- PIN 1 (must not move): under a LOWERED top slab the merge compensation SURVIVES ---
        // Fixture from the F6 family: a cantilevered TOP slab reads -0.5; the lantern under it
        // sits at supportDy + 0.5 = 0.0, flush against the lowered underside — Maintainer's 2026-07-01
        // ruling says this is deliberate ceiling-mount geometry. The kill must not touch it.
        BlockPos eaveSupport = ctx.absolutePos(new BlockPos(7, 4, 1));
        BlockPos eave = eaveSupport.east();
        BlockPos hung = eave.below();
        world.setBlock(eaveSupport.below(), Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_NONE);
        world.setBlock(eaveSupport, topSlab, Block.UPDATE_NONE);
        world.setBlock(eave, topSlab, Block.UPDATE_NONE);
        double eaveDy = SlabSupport.getYOffset(world, eave, world.getBlockState(eave));
        world.setBlock(hung, Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), Block.UPDATE_NONE);
        double hungDy = SlabSupport.getYOffset(world, hung, world.getBlockState(hung));
        if (eaveDy < -1.0e-6d) {
            ctx.assertTrue(Double.compare(hungDy, eaveDy + 0.5d) == 0,
                    "PIN: under a LOWERED top slab (eave dy=" + eaveDy + ") the +0.5 merge "
                            + "compensation must survive the kill; lantern dy=" + hungDy);
        } else {
            // The eave fixture did not lower on this geometry — then the lantern must be flush,
            // and the pin degrades to the kill-row assertion rather than passing vacuously.
            ctx.assertTrue(Double.compare(hungDy, 0.0d) == 0,
                    "PIN(degraded): eave stayed flush (dy=" + eaveDy + "), so the lantern must "
                            + "hang flush too; got " + hungDy);
        }

        // --- PIN 2 (must not move): under an ordinary full ceiling, flush as always ---
        BlockPos d = ctx.absolutePos(new BlockPos(1, 3, 5));
        world.setBlock(d.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_NONE);
        world.setBlock(d, Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), Block.UPDATE_NONE);
        assertDy(ctx, world, d, 0.0d, "hanging lantern under full stone stays flush");

        ctx.succeed();
    }

    private static void assertDy(GameTestHelper ctx, ServerLevel world, BlockPos pos,
                                 double expected, String what) {
        double dy = SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
        ctx.assertTrue(Double.compare(dy, expected) == 0,
                what + ": expected dy=" + expected + " got dy=" + dy
                        + " state=" + world.getBlockState(pos));
    }
}
