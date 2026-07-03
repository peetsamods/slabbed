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

import java.util.List;

/**
 * The AUTO half of RELEASE_REGRESSION_TRIGGERS.md never-pop / WYSIWYG law, as a matrix over
 * block categories so a port regression on ANY of them fails the suite with zero live testing.
 *
 * <p>Universal invariant asserted per block: whatever dy a block is placed at (after its
 * onPlaced anchor/freeze runs), it STAYS at that dy when its supporting slab is later removed —
 * it must not pop up or down. This is exactly the class of bug that keeps regressing per port
 * (fences, gates, walls); the matrix locks the whole family at once.
 */
public final class NeverPopMatrixTest {

    private static final List<BlockState> LOWERING_CANDIDATES = List.of(
            Blocks.STONE.getDefaultState(),
            Blocks.DIRT.getDefaultState(),
            Blocks.GRASS_BLOCK.getDefaultState(),
            Blocks.OAK_PLANKS.getDefaultState(),
            Blocks.OAK_FENCE.getDefaultState(),
            Blocks.SPRUCE_FENCE.getDefaultState(),
            Blocks.OAK_FENCE_GATE.getDefaultState(),
            Blocks.COBBLESTONE_WALL.getDefaultState(),
            Blocks.MOSSY_COBBLESTONE_WALL.getDefaultState(),
            Blocks.GLASS_PANE.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState()
    );

    private static BlockState bottomSlab() {
        return Blocks.POLISHED_TUFF_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static void onPlaced(ServerWorld w, BlockPos pos, BlockState state) {
        SlabAnchorAttachment.addAnchor(w, pos, state);
        SlabAnchorAttachment.freezeLoweredOnPlace(w, pos, state);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placedBlockNeverPopsWhenSupportRemoved(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos pos = slab.up();

        StringBuilder fails = new StringBuilder();
        for (BlockState state : LOWERING_CANDIDATES) {
            w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
            w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
            onPlaced(w, pos, w.getBlockState(pos));

            double dyPlaced = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
            // Remove the support: a geometric (un-locked) block would recompute and pop.
            w.setBlockState(slab, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            double dyAfter = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));

            String id = state.getBlock().getTranslationKey();
            if (Math.abs(dyAfter - dyPlaced) > 1.0e-6) {
                fails.append("\n  ").append(id).append(": placed dy=").append(fmt(dyPlaced))
                        .append(" popped to ").append(fmt(dyAfter));
            }
            // reset
            w.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        ctx.assertTrue(fails.length() == 0,
                "these placed blocks POPPED when their support was removed (never-pop / WYSIWYG):" + fails);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placedFlatBlockNeverGetsPulledDownByALaterSlab(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos pos = ground.up();

        StringBuilder fails = new StringBuilder();
        for (BlockState state : LOWERING_CANDIDATES) {
            // Placed FLAT on solid ground (dy 0).
            w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
            onPlaced(w, pos, w.getBlockState(pos));
            double dyPlaced = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));

            // Now put a bottom slab under it (replace the ground) — a live geometric block would
            // inherit -0.5; a frozen-flat one must stay put.
            w.setBlockState(ground, bottomSlab(), Block.NOTIFY_LISTENERS);
            double dyAfter = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));

            String id = state.getBlock().getTranslationKey();
            if (Math.abs(dyAfter - dyPlaced) > 1.0e-6) {
                fails.append("\n  ").append(id).append(": flat dy=").append(fmt(dyPlaced))
                        .append(" pulled down to ").append(fmt(dyAfter));
            }
            w.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        ctx.assertTrue(fails.length() == 0,
                "these flat-placed blocks got PULLED DOWN by a later slab (never-pop / WYSIWYG):" + fails);
        ctx.complete();
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }
}
