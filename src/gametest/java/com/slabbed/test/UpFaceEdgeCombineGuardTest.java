package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Top-face edge clicks stay top placement; literal horizontal clicks keep vanilla semantics. */
public final class UpFaceEdgeCombineGuardTest {

    private static final double EPS = 1.0e-6d;

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void topFaceEdgeClicksOnLoweredFullBlockPlaceAbove(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        double supportDy = SlabSupport.minResolvedDy() <= -1.5d ? -1.5d : -1.0d;
        Direction[] edges = {
                Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH
        };

        for (int i = 0; i < edges.length; i++) {
            Direction edge = edges[i];
            BlockPos support = origin.add(2 + (i % 2) * 3, 4, 2 + (i / 2) * 3);
            BlockPos above = support.up();
            BlockPos side = support.offset(edge);
            world.setBlockState(support, Blocks.STRIPPED_OAK_WOOD.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(above, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(side, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            ctx.assertTrue(SlabPlacementDyAttachment.writeBatch(
                            world, Map.of(support, Double.doubleToRawLongBits(supportDy))),
                    "fixture: lowered support height must publish for " + edge);

            PlayerEntity player = PlacementHarness.mockSlabPlayer(ctx, support.north(3));
            Vec3d hit = topEdgeHit(support, supportDy, edge);
            ActionResult result = PlacementHarness.useHeldOakSlab(
                    world, player, support, Direction.UP, hit);

            ctx.assertTrue(result.isAccepted(),
                    "top-face " + edge + " edge click must place, got " + result);
            BlockState aboveState = world.getBlockState(above);
            ctx.assertTrue(aboveState.isOf(Blocks.OAK_SLAB)
                            && aboveState.get(SlabBlock.TYPE) == SlabType.BOTTOM,
                    "top-face " + edge + " edge click must place a BOTTOM slab above the support; got "
                            + PlacementHarness.describe(world, above));
            ctx.assertTrue(world.getBlockState(side).isAir(),
                    "top-face " + edge + " edge click must not place beside the support; got "
                            + PlacementHarness.describe(world, side));
            assertFrozenDy(ctx, world, above, supportDy,
                    "top-face " + edge + " edge placement");

            world.setBlockState(support, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            ctx.assertTrue(world.getBlockState(above).isOf(Blocks.OAK_SLAB),
                    "removing the aimed support must not remove the placed slab for " + edge);
            assertFrozenDy(ctx, world, above, supportDy,
                    "top-face " + edge + " edge placement after support removal");
        }
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void topFaceEdgeNearOccupiedSlabStillPlacesAbove(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos support = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 4, 3);
        BlockPos above = support.up();
        BlockPos neighbor = support.west();
        double supportDy = SlabSupport.minResolvedDy() <= -1.5d ? -1.5d : -1.0d;
        world.setBlockState(support, Blocks.STRIPPED_OAK_WOOD.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(neighbor,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabPlacementDyAttachment.writeBatch(
                        world, Map.of(support, Double.doubleToRawLongBits(supportDy))),
                "fixture: lowered support height must publish");

        PlayerEntity player = PlacementHarness.mockSlabPlayer(ctx, support.north(3));
        ActionResult result = PlacementHarness.useHeldOakSlab(
                world, player, support, Direction.UP,
                topEdgeHit(support, supportDy, Direction.WEST));

        ctx.assertTrue(result.isAccepted(), "occupied-neighbor top-edge click must place, got " + result);
        BlockState aboveState = world.getBlockState(above);
        ctx.assertTrue(aboveState.isOf(Blocks.OAK_SLAB)
                        && aboveState.get(SlabBlock.TYPE) == SlabType.BOTTOM,
                "occupied-neighbor top-edge click must still place above; got "
                        + PlacementHarness.describe(world, above));
        BlockState neighborAfter = world.getBlockState(neighbor);
        ctx.assertTrue(neighborAfter.isOf(Blocks.OAK_SLAB)
                        && neighborAfter.get(SlabBlock.TYPE) == SlabType.BOTTOM,
                "the existing neighbor must remain a single BOTTOM slab; got "
                        + PlacementHarness.describe(world, neighbor));
        assertFrozenDy(ctx, world, above, supportDy, "occupied-neighbor top-edge placement");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void literalHorizontalClickStillCombinesNormally(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(5, 2, 1);
        world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos slab = ground.up();
        world.setBlockState(slab,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);

        PlayerEntity player = PlacementHarness.mockSlabPlayer(ctx, slab.north(3));
        Vec3d hit = new Vec3d(slab.getX() + 0.5d, slab.getY() + 0.75d, slab.getZ());
        ActionResult result = PlacementHarness.useHeldOakSlab(
                world, player, slab, Direction.NORTH, hit);

        ctx.assertTrue(result.isAccepted(), "literal horizontal-face click must place, got " + result);
        BlockState after = world.getBlockState(slab);
        ctx.assertTrue(after.get(SlabBlock.TYPE) == SlabType.DOUBLE,
                "literal horizontal click must keep vanilla same-cell combine behavior; got "
                        + PlacementHarness.describe(world, slab));
        ctx.complete();
    }

    private static Vec3d topEdgeHit(BlockPos support, double supportDy, Direction edge) {
        double x = support.getX() + 0.5d;
        double z = support.getZ() + 0.5d;
        if (edge == Direction.WEST) {
            x = support.getX() + 0.05d;
        } else if (edge == Direction.EAST) {
            x = support.getX() + 0.95d;
        } else if (edge == Direction.NORTH) {
            z = support.getZ() + 0.05d;
        } else if (edge == Direction.SOUTH) {
            z = support.getZ() + 0.95d;
        }
        return new Vec3d(x, support.getY() + 1.0d + supportDy, z);
    }

    private static void assertFrozenDy(
            TestContext ctx,
            ServerWorld world,
            BlockPos pos,
            double expectedDy,
            String label
    ) {
        double stored = SlabPlacementDyAttachment.storedDy(world, pos);
        ctx.assertTrue(Double.doubleToRawLongBits(stored)
                        == Double.doubleToRawLongBits(expectedDy),
                label + " must freeze dy=" + expectedDy + "; got " + stored);
        double visual = SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
        ctx.assertTrue(Math.abs(visual - expectedDy) <= EPS,
                label + " visual dy must remain " + expectedDy + "; got " + visual);
    }
}
