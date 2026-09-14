package com.slabbed.test;

import static com.slabbed.test.PlacementHarness.describe;
import static com.slabbed.test.PlacementHarness.mockPlayerHolding;
import static com.slabbed.test.PlacementHarness.row;
import static com.slabbed.test.PlacementHarness.useHeldItem;

import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * A cell whose occupant changes KIND in place, at the same position, without changing SHAPE.
 *
 * <p>{@code LAW.md} LAW 1: the height a block is placed at is frozen at placement and every later
 * read hands back that stored value. The in-place replacement rule decides whether a cell's stored
 * height belongs to the arriving occupant or died with the departing one, and it used to answer
 * purely from the arriving state — so a slab swapped for another slab of the same half was read as
 * the slab leaving its cell, and the stored height was thrown away.
 */
public final class StateChangeAnchorTest {
    private static final double EPS = 1.0e-6;

    /**
     * A lowered SLAB that changes kind in place keeps its height. A Terrain Slabs grass slab turns
     * into its dirt slab when something covers it, the way vanilla grass turns to dirt; on a
     * lowered stack the converted slab popped up to grid height and sank into the block above it
     * (live on 1.21.1, maintainer ruling 2026-09-13). Vanilla stands in for the compat pair here:
     * a placed oak slab becomes a birch slab of the same half and the same shape.
     *
     * <p>MUTATION that must redden this row alone: drop the {@code isSameShapeTransform} clause
     * from {@code SlabAnchorAttachment.replacementPreservesAnchor}.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void inPlaceSlabKindChangeWithTheSameShapeKeepsItsStoredHeight(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);
        BlockPos source = base.up();
        BlockPos support = base.up(2);
        BlockPos subject = base.up(3);

        w.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(source, bottomSlab(Blocks.SMOOTH_STONE_SLAB), Block.NOTIFY_LISTENERS);
        w.setBlockState(support, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        ctx.assertTrue(Math.abs(supportDy + 0.5) <= EPS,
                "fixture: the support standing on a bottom slab must read -0.5, got " + supportDy);

        // The REAL useOn chain places the subject, so the seat under test is the one a player's
        // click actually leaves behind rather than a hand-written fact.
        PlayerEntity player = mockPlayerHolding(
                ctx, support.north(3), new ItemStack(Blocks.OAK_SLAB.asItem(), 16));
        Vec3d hit = new Vec3d(support.getX() + 0.5, support.getY() + 0.5, support.getZ() + 0.5);
        ActionResult result = useHeldItem(w, player, support, Direction.UP, hit);
        row("stateChange.sameShapeKindChange.place", w, support, subject, result);
        ctx.assertTrue(result.isAccepted(),
                "fixture: the oak slab must place on the lowered support's up face, got " + result);
        BlockState placed = w.getBlockState(subject);
        ctx.assertTrue(placed.isOf(Blocks.OAK_SLAB)
                        && placed.contains(SlabBlock.TYPE)
                        && placed.get(SlabBlock.TYPE) == SlabType.BOTTOM,
                "fixture: a BOTTOM oak slab must land in the cell above the support, got "
                        + describe(w, subject));
        ctx.assertTrue(SlabPlacementDyAttachment.hasStoredDy(w, subject),
                "fixture: the placed slab must carry a stored placement height, or this row proves "
                        + "nothing about keeping one");

        // Take the lowering source away FIRST, so the stored seat is saying something no live lane
        // at this cell can say any more. Both assertions below are then satisfiable only by the
        // stored height surviving the kind change.
        w.breakBlock(source, false);
        double before = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(before + 0.5) <= EPS,
                "fixture: with the source below it gone the slab must still read -0.5 from its "
                        + "stored seat, got " + before);

        // Oak slab -> birch slab: a block-KIND change at the SAME position, same half, same shape.
        w.setBlockState(subject, bottomSlab(Blocks.BIRCH_SLAB), Block.NOTIFY_ALL);

        boolean kept = SlabPlacementDyAttachment.hasStoredDy(w, subject);
        double after = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(kept,
                "a slab that changes kind in place with the same shape must KEEP its stored seat "
                        + "— throwing it away is what sends the converted slab back up to grid "
                        + "height (the Terrain Slabs grass-slab-to-dirt-slab report)");
        ctx.assertTrue(Math.abs(after - before) <= EPS,
                "the converted slab must not move: before=" + before + " after=" + after + " ("
                        + describe(w, subject) + ")");
        ctx.complete();
    }

    private static BlockState bottomSlab(Block block) {
        return block.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }
}
