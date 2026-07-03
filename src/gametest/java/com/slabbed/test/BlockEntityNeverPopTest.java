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
 * The reported hopper snap: a block-entity block (hopper/chest/furnace) placed lowered on a slab
 * re-derived its dy from a live column walk (it was excluded from the never-pop anchor lanes), so
 * editing the column below toggled it between 0 and -0.5 — "places too high, then snaps down when
 * a block is placed underneath". A placed block entity must be height-locked like any other block.
 * Ceiling-hung block entities (hanging signs) must stay EXCLUDED — they follow their support.
 */
public final class BlockEntityNeverPopTest {

    private static BlockState bottomSlab() {
        return Blocks.POLISHED_TUFF_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static void onPlaced(ServerWorld w, BlockPos pos, BlockState state) {
        SlabAnchorAttachment.addAnchor(w, pos, state);
        SlabAnchorAttachment.freezeLoweredOnPlace(w, pos, state);
    }

    private static void loweredBeIsLocked(TestContext ctx, BlockState be, String label) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos pos = slab.up();
        w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(pos, be, Block.NOTIFY_LISTENERS);
        onPlaced(w, pos, w.getBlockState(pos));

        double placed = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        ctx.assertTrue(placed < -1.0e-6, label + " on a bottom slab must place lowered, got " + placed);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, pos),
                label + " placed lowered MUST be height-locked (else it snaps when the column changes)");

        // Edit the column below WITHOUT touching the block entity — a geometric one would toggle.
        w.setBlockState(slab, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        double after = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        ctx.assertTrue(Math.abs(after - placed) < 1.0e-6,
                label + " must STAY at " + placed + " when the cell below changes, got " + after + " (snap)");

        w.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(slab, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hopperLoweredOnSlabIsHeightLocked(TestContext ctx) {
        loweredBeIsLocked(ctx, Blocks.HOPPER.getDefaultState(), "hopper");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chestAndFurnaceLoweredOnSlabAreHeightLocked(TestContext ctx) {
        loweredBeIsLocked(ctx, Blocks.CHEST.getDefaultState(), "chest");
        loweredBeIsLocked(ctx, Blocks.FURNACE.getDefaultState(), "furnace");
        loweredBeIsLocked(ctx, Blocks.BARREL.getDefaultState(), "barrel");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatHopperIsNotPulledDownByASlabShovedUnder(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos pos = ground.up();
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(pos, Blocks.HOPPER.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, pos, w.getBlockState(pos));
        double placed = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        ctx.assertTrue(Math.abs(placed) < 1.0e-6, "hopper placed flat must be dy 0, got " + placed);

        w.setBlockState(ground, bottomSlab(), Block.NOTIFY_LISTENERS);
        double after = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        ctx.assertTrue(Math.abs(after) < 1.0e-6,
                "a flat-placed hopper must NOT be pulled down by a slab added under it, got " + after);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingSignBlockEntityIsNotAnchored(TestContext ctx) {
        // Regression guard: a hanging sign is a block entity too, but it HANGS and must keep
        // following its support — it must NOT be height-locked by the new block-entity lane.
        ServerWorld w = ctx.getWorld();
        BlockPos support = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos pos = support.down();
        w.setBlockState(support, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(pos, Blocks.OAK_HANGING_SIGN.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, pos, w.getBlockState(pos));
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, pos),
                "a hanging sign (ceiling-hung block entity) must NOT be anchored — it follows its support");
        ctx.assertTrue(!SlabAnchorAttachment.isFrozenFlat(w, pos),
                "a hanging sign must NOT be frozen-flat either");
        ctx.complete();
    }
}
