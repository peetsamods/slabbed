package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/**
 * Replaced-cell placement seats — FLUSH WINS (maintainer ruling, 2026-08-17).
 *
 * <p>A placement that lands in its own aim cell REPLACED that cell's occupant (vanilla
 * {@code canReplace}: plants, one-layer snow, same-item decoration merges). The replaced occupant
 * contributes no plane of its own, so the placed block must seat on the REAL support surface below
 * the cell — flush ground yields a flush seat, and a lowered support yields its own recorded
 * plane. The owner-relative placement arithmetic describes a NEIGHBOR relationship; fed a
 * same-cell aim it degenerates by a whole cell (a full owner plane on an UP-face click, a negative
 * one on a DOWN-face click), and the minted value would freeze permanently.
 *
 * <p>A same-cell merge is the other side of the same coin: the occupant GROWS in place (a second
 * candle, an extra snow layer), so an existing stored height fact must survive byte-identically —
 * re-deriving on a merge would let a click move a placed block's height, which LAW 1 forbids.
 *
 * <p>Every row drives the REAL item path (useOnBlock, then the intent mixin, then canReplace,
 * then place); the control row pins the adjacent-cell lane so this suite cannot go green by
 * breaking ordinary placements.
 */
public final class ReplaceableCellPlacementSuite {

    private static final double EPS = 1.0e-6d;

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stoneReplacesShortGrassUpFaceSeatsFlush(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos cell = base.up();
        world.setBlockState(base, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(cell, Blocks.SHORT_GRASS.getDefaultState(), Block.NOTIFY_LISTENERS);

        PlayerEntity player = PlacementHarness.mockPlayerHolding(
                ctx, cell.north(3), new ItemStack(Blocks.STONE.asItem(), 1));
        ActionResult result = PlacementHarness.useHeldItem(world, player, cell, Direction.UP,
                new Vec3d(cell.getX() + 0.5d, cell.getY() + 0.1d, cell.getZ() + 0.5d));

        ctx.assertTrue(result.isAccepted(),
                "R1: stone into the grass cell must place; got " + result);
        ctx.assertTrue(world.getBlockState(cell).isOf(Blocks.STONE),
                "R1: the grass cell itself must now hold the stone; got "
                        + PlacementHarness.describe(world, cell));
        assertSeat(ctx, world, cell, 0.0d, true, "R1 up-face grass replacement");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stoneReplacesShortGrassDownFaceSeatsFlush(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos cell = base.up();
        world.setBlockState(base, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(cell, Blocks.SHORT_GRASS.getDefaultState(), Block.NOTIFY_LISTENERS);

        PlayerEntity player = PlacementHarness.mockPlayerHolding(
                ctx, cell.north(3), new ItemStack(Blocks.STONE.asItem(), 1));
        ActionResult result = PlacementHarness.useHeldItem(world, player, cell, Direction.DOWN,
                new Vec3d(cell.getX() + 0.5d, cell.getY() + 0.9d, cell.getZ() + 0.5d));

        ctx.assertTrue(result.isAccepted(),
                "R2: stone into the grass cell via its underside must place; got " + result);
        ctx.assertTrue(world.getBlockState(cell).isOf(Blocks.STONE),
                "R2: the grass cell itself must now hold the stone; got "
                        + PlacementHarness.describe(world, cell));
        assertSeat(ctx, world, cell, 0.0d, true, "R2 down-face grass replacement");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stoneReplacesGrassFollowerOnStoredLoweredSupport(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos support = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos cell = support.up();
        world.setBlockState(support, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(cell, Blocks.SHORT_GRASS.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabPlacementDyAttachment.writeBatch(
                        world, Map.of(support, Double.doubleToRawLongBits(-0.5d))),
                "R3 fixture: the lowered support height must publish");
        System.out.println("[REPLACEABLE-CELL] R3 pre-click grass "
                + PlacementHarness.describe(world, cell));

        PlayerEntity player = PlacementHarness.mockPlayerHolding(
                ctx, cell.north(3), new ItemStack(Blocks.STONE.asItem(), 1));
        ActionResult result = PlacementHarness.useHeldItem(world, player, cell, Direction.UP,
                new Vec3d(cell.getX() + 0.5d, cell.getY() + 0.1d, cell.getZ() + 0.5d));

        ctx.assertTrue(result.isAccepted(),
                "R3: stone into the lowered grass cell must place; got " + result);
        ctx.assertTrue(world.getBlockState(cell).isOf(Blocks.STONE),
                "R3: the grass cell itself must now hold the stone; got "
                        + PlacementHarness.describe(world, cell));
        assertSeat(ctx, world, cell, -0.5d, false, "R3 grass replacement on a -0.5 support");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void oakSlabReplacesShortGrassSeatsFlushBottom(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos cell = base.up();
        world.setBlockState(base, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(cell, Blocks.SHORT_GRASS.getDefaultState(), Block.NOTIFY_LISTENERS);

        PlayerEntity player = PlacementHarness.mockSlabPlayer(ctx, cell.north(3));
        ActionResult result = PlacementHarness.useHeldOakSlab(world, player, cell, Direction.UP,
                new Vec3d(cell.getX() + 0.5d, cell.getY() + 0.05d, cell.getZ() + 0.5d));

        ctx.assertTrue(result.isAccepted(),
                "R4: an oak slab into the grass cell must place; got " + result);
        BlockState placed = world.getBlockState(cell);
        ctx.assertTrue(placed.isOf(Blocks.OAK_SLAB)
                        && placed.get(SlabBlock.TYPE) == SlabType.BOTTOM,
                "R4: the grass cell must now hold a BOTTOM oak slab; got "
                        + PlacementHarness.describe(world, cell));
        assertSeat(ctx, world, cell, 0.0d, true, "R4 slab-item grass replacement");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockReplacesThinSnowSeatsFlush(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos cell = base.up();
        world.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(cell, Blocks.SNOW.getDefaultState(), Block.NOTIFY_LISTENERS);

        PlayerEntity player = PlacementHarness.mockPlayerHolding(
                ctx, cell.north(3), new ItemStack(Blocks.STONE.asItem(), 1));
        ActionResult result = PlacementHarness.useHeldItem(world, player, cell, Direction.UP,
                new Vec3d(cell.getX() + 0.5d, cell.getY() + 0.12d, cell.getZ() + 0.5d));

        ctx.assertTrue(result.isAccepted(),
                "R5: stone into the one-layer snow cell must place; got " + result);
        ctx.assertTrue(world.getBlockState(cell).isOf(Blocks.STONE),
                "R5: the snow cell itself must now hold the stone; got "
                        + PlacementHarness.describe(world, cell));
        assertSeat(ctx, world, cell, 0.0d, true, "R5 thin-snow replacement");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void secondCandleMergePreservesStoredLoweredDy(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos cell = ground.up();
        world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        PlayerEntity first = PlacementHarness.mockPlayerHolding(
                ctx, cell.north(3), new ItemStack(Blocks.CANDLE.asItem(), 1));
        ActionResult firstResult = PlacementHarness.useHeldItem(world, first, ground, Direction.UP,
                new Vec3d(ground.getX() + 0.5d, ground.getY() + 1.0d, ground.getZ() + 0.5d));
        ctx.assertTrue(firstResult.isAccepted(),
                "R6 fixture: the first candle must place; got " + firstResult);
        BlockState one = world.getBlockState(cell);
        ctx.assertTrue(one.isOf(Blocks.CANDLE) && one.get(CandleBlock.CANDLES) == 1,
                "R6 fixture: one candle expected; got " + PlacementHarness.describe(world, cell));
        System.out.println("[REPLACEABLE-CELL] R6 stored after first candle = "
                + SlabPlacementDyAttachment.storedDy(world, cell));

        ctx.assertTrue(SlabPlacementDyAttachment.writeBatch(
                        world, Map.of(cell, Double.doubleToRawLongBits(-0.5d))),
                "R6 fixture: the candle cell must accept the authored -0.5 fact");
        ctx.assertTrue(Double.doubleToRawLongBits(SlabPlacementDyAttachment.storedDy(world, cell))
                        == Double.doubleToRawLongBits(-0.5d),
                "R6 fixture: the authored -0.5 fact must read back");

        PlayerEntity second = PlacementHarness.mockPlayerHolding(
                ctx, cell.north(3), new ItemStack(Blocks.CANDLE.asItem(), 1));
        ActionResult secondResult = PlacementHarness.useHeldItem(world, second, cell, Direction.UP,
                new Vec3d(cell.getX() + 0.5d, cell.getY() + 0.4d, cell.getZ() + 0.5d));

        ctx.assertTrue(secondResult.isAccepted(),
                "R6: the second candle must merge; got " + secondResult);
        BlockState two = world.getBlockState(cell);
        ctx.assertTrue(two.isOf(Blocks.CANDLE) && two.get(CandleBlock.CANDLES) == 2,
                "R6: two candles expected after the merge; got "
                        + PlacementHarness.describe(world, cell));
        assertSeat(ctx, world, cell, -0.5d, false, "R6 candle merge over a stored -0.5 fact");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void doorReplacesShortGrassSeatsBothHalvesFlush(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos lower = base.up();
        BlockPos upper = lower.up();
        world.setBlockState(base, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(lower, Blocks.SHORT_GRASS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(upper, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);

        PlayerEntity player = PlacementHarness.mockPlayerHolding(
                ctx, lower.north(3), new ItemStack(Blocks.OAK_DOOR.asItem(), 1));
        ActionResult result = PlacementHarness.useHeldItem(world, player, lower, Direction.UP,
                new Vec3d(lower.getX() + 0.5d, lower.getY() + 0.1d, lower.getZ() + 0.5d));

        ctx.assertTrue(result.isAccepted(),
                "R7: a door into the grass cell must place; got " + result);
        BlockState lowerState = world.getBlockState(lower);
        BlockState upperState = world.getBlockState(upper);
        ctx.assertTrue(lowerState.isOf(Blocks.OAK_DOOR)
                        && lowerState.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER,
                "R7: the grass cell must hold the LOWER door half; got "
                        + PlacementHarness.describe(world, lower));
        ctx.assertTrue(upperState.isOf(Blocks.OAK_DOOR)
                        && upperState.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER,
                "R7: the cell above must hold the UPPER door half; got "
                        + PlacementHarness.describe(world, upper));
        SlabPlacementDyAttachment.PlacementDyFact lowerFact =
                SlabPlacementDyAttachment.rawFact(world, lower);
        SlabPlacementDyAttachment.PlacementDyFact upperFact =
                SlabPlacementDyAttachment.rawFact(world, upper);
        ctx.assertTrue(lowerFact.present() == upperFact.present()
                        && (!lowerFact.present() || lowerFact.rawBits() == upperFact.rawBits()),
                "R7: both door halves must publish one consistent fact; got lower="
                        + lowerFact + " upper=" + upperFact);
        assertSeat(ctx, world, lower, 0.0d, true, "R7 lower door half in the grass cell");
        assertSeat(ctx, world, upper, 0.0d, true, "R7 upper door half above the grass cell");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void controlAdjacentUpFacePlacementOnStoneStaysFlush(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos cell = base.up();
        world.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(cell, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);

        PlayerEntity player = PlacementHarness.mockPlayerHolding(
                ctx, cell.north(3), new ItemStack(Blocks.STONE.asItem(), 1));
        ActionResult result = PlacementHarness.useHeldItem(world, player, base, Direction.UP,
                new Vec3d(base.getX() + 0.5d, base.getY() + 1.0d, base.getZ() + 0.5d));

        ctx.assertTrue(result.isAccepted(),
                "R8 control: the adjacent-lane placement must place; got " + result);
        ctx.assertTrue(world.getBlockState(cell).isOf(Blocks.STONE),
                "R8 control: stone must land in the cell above; got "
                        + PlacementHarness.describe(world, cell));
        assertSeat(ctx, world, cell, 0.0d, true, "R8 adjacent-lane control");
        ctx.complete();
    }

    /**
     * The triad seat assertion: the stored fact (byte-identical, or absent where flush-absent is
     * the transaction's flat representation), the resolved height, and the outline's minimum Y
     * must all present the same in-cell seat.
     */
    private static void assertSeat(
            TestContext ctx,
            ServerWorld world,
            BlockPos pos,
            double expectedDy,
            boolean absentAllowed,
            String label
    ) {
        double stored = SlabPlacementDyAttachment.storedDy(world, pos);
        boolean storedOk = Double.isNaN(stored)
                ? absentAllowed
                : Double.doubleToRawLongBits(stored) == Double.doubleToRawLongBits(expectedDy);
        ctx.assertTrue(storedOk,
                label + ": stored dy must be " + (absentAllowed ? "absent or " : "")
                        + expectedDy + "; got " + stored);
        BlockState state = world.getBlockState(pos);
        double visual = SlabSupport.getYOffset(world, pos, state);
        ctx.assertTrue(Math.abs(visual - expectedDy) <= EPS,
                label + ": resolved dy must be " + expectedDy + "; got " + visual);
        VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.absent());
        ctx.assertTrue(!outline.isEmpty()
                        && Math.abs(outline.getMin(Direction.Axis.Y) - expectedDy) <= EPS,
                label + ": outline min-Y must sit at " + expectedDy + " in the cell; got "
                        + (outline.isEmpty() ? "empty" : outline.getMin(Direction.Axis.Y)));
    }
}
