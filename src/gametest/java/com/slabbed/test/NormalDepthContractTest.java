package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import com.slabbed.util.SlabbedServerHitValidation;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/** Normal placement, targeting and collision support three-block lowering. */
public final class NormalDepthContractTest {
    private static final double EPS = 1.0e-6;

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void ordinarySlabPlacementsReachMinusThreeWithoutOptIn(TestContext ctx) {
        var world = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(new BlockPos(3, 0, 3));
        world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos owner = ground;
        double ownerDy = 0.0;
        for (int course = 0; course <= 6; course++) {
            double top = course == 0 ? 1.0 : 0.5;
            var slab = course % 2 == 0 ? Blocks.OAK_SLAB : Blocks.STONE_SLAB;
            PlayerEntity player = PlacementHarness.mockPlayerHolding(ctx, owner.north(3),
                    new ItemStack(slab.asItem(), 8));
            ActionResult result = PlacementHarness.useHeldItem(world, player, owner, Direction.UP,
                    new Vec3d(owner.getX() + 0.5, owner.getY() + ownerDy + top,
                            owner.getZ() + 0.5));
            BlockPos placed = owner.up();
            double expected = -0.5 * course;
            double actual = SlabPlacementDyAttachment.storedDy(world, placed);
            System.out.println("[NORMAL_DEPTH_TEST] placement course=" + course + " expected="
                    + expected + " accepted=" + result.isAccepted() + " state="
                    + world.getBlockState(placed) + " stored=" + actual
                    + " activeCap=" + SlabSupport.minResolvedDy());
            ctx.assertTrue(result.isAccepted()
                            && world.getBlockState(placed).isOf(slab)
                            && world.getBlockState(placed).get(SlabBlock.TYPE) == SlabType.BOTTOM
                            && Math.abs(actual - expected) < EPS,
                    "normal placement must reach " + expected + "; actual=" + actual
                            + " result=" + result + " cap=" + SlabSupport.minResolvedDy());
            owner = placed;
            ownerDy = actual;
        }
        long before = Double.doubleToRawLongBits(SlabPlacementDyAttachment.storedDy(world, owner));
        world.breakBlock(owner.down(), false);
        ctx.assertTrue(Double.doubleToRawLongBits(SlabSupport.getYOffset(world, owner, world.getBlockState(owner))) == before,
                "breaking the deep slab's support must preserve its placement height");
        com.slabbed.anchor.SlabAnchorAttachment.removeAnchor(world, owner);
        ctx.assertTrue(Math.abs(SlabSupport.getYOffset(world, owner, world.getBlockState(owner)) + 3.0) > EPS,
                "removing placement protection must expose the changed support");
        ctx.assertTrue(SlabPlacementDyAttachment.record(world, owner, -3.0),
                "the exact placement fact must restore after the negative control");
        world.setBlockState(owner.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        PlayerEntity boundaryPlayer = PlacementHarness.mockPlayerHolding(ctx, owner.north(3),
                new ItemStack(Blocks.STONE.asItem(), 8));
        ActionResult belowLimit = PlacementHarness.useHeldItem(world, boundaryPlayer, owner, Direction.UP,
                new Vec3d(owner.getX() + 0.5, owner.getY() - 2.5, owner.getZ() + 0.5));
        ctx.assertTrue(!belowLimit.isAccepted() && world.getBlockState(owner.up()).isAir(),
                "a new full block below -3 must be refused without creating a cell");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void productionPickingSeesStoredMinusThreeBody(TestContext ctx) {
        var world = ctx.getWorld();
        BlockPos owner = ctx.getAbsolutePos(new BlockPos(3, 6, 3));
        world.setBlockState(owner, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabPlacementDyAttachment.record(world, owner, -3.0), "stored fixture must write");
        double actual = SlabSupport.getYOffset(world, owner, world.getBlockState(owner));
        ctx.assertTrue(Math.abs(actual + 3.0) < EPS, "stored fixture must resolve at -3, got " + actual);
        Vec3d start = new Vec3d(owner.getX() - 2.0, owner.getY() - 2.5, owner.getZ() + 0.5);
        Vec3d end = start.add(5.0, 0.0, 0.0);
        BlockHitResult production = SlabbedOffsetRaycast.raycast(world, start, end, ShapeContext.absent());
        BlockHitResult wider = SlabbedOffsetRaycast.raycastWithWindow(world, start, end, ShapeContext.absent(), 3);
        Vec3d center = SlabbedServerHitValidation.shiftedValidationCenter(world, owner);
        System.out.println("[NORMAL_DEPTH_TEST] picking production=" + production.getType()
                + " radius=" + SlabbedOffsetRaycast.WINDOW_RADIUS + " radius3=" + wider.getType()
                + " radius3Owner=" + wider.getBlockPos().equals(owner)
                + " serverCenterDy=" + (center.y - (owner.getY() + 0.5)));
        ctx.assertTrue(wider.getType() == HitResult.Type.BLOCK && wider.getBlockPos().equals(owner),
                "radius-3 control must reach the intended stored body");
        ctx.assertTrue(Math.abs(center.y - (owner.getY() - 2.5)) < EPS,
                "server hit-center helper must already follow stored -3");
        ctx.assertTrue(production.getType() == HitResult.Type.BLOCK && production.getBlockPos().equals(owner),
                "production pick window must find the -3 body; got " + production.getType());
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void nativeMovementQuerySeesStoredMinusThreeBody(TestContext ctx) {
        var world = ctx.getWorld();
        BlockPos owner = ctx.getAbsolutePos(new BlockPos(3, 6, 3));
        world.setBlockState(owner, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabPlacementDyAttachment.record(world, owner, -3.0), "stored fixture must write");
        var state = world.getBlockState(owner);
        VoxelShape outline = state.getOutlineShape(world, owner, ShapeContext.absent());
        VoxelShape collision = state.getCollisionShape(world, owner, ShapeContext.absent());
        int visible = count(world.getBlockCollisions(null, body(owner, -3.0)));
        BlockPos control = owner.east(3);
        world.setBlockState(control, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabPlacementDyAttachment.record(world, control, 0.0), "flush control must write");
        int grid = count(world.getBlockCollisions(null, body(control, 0.0)));
        System.out.println("[NORMAL_DEPTH_TEST] movement outlineMin=" + outline.getMin(Direction.Axis.Y)
                + " collisionMin=" + collision.getMin(Direction.Axis.Y)
                + " visibleBodyCollisions=" + visible + " gridBodyCollisions=" + grid);
        ctx.assertTrue(Math.abs(outline.getMin(Direction.Axis.Y) + 3.0) < EPS,
                "outline control must show the body lowered by three");
        ctx.assertTrue(grid > 0, "native movement query control must see the original full cube");
        ctx.assertTrue(visible > 0, "native movement query must see the visible -3 body");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void storedShapesShiftOnceAndUnstoredShapesStayUnchanged(TestContext ctx) {
        var world = ctx.getWorld();
        var states = new net.minecraft.block.BlockState[] {
                Blocks.STONE.getDefaultState(), Blocks.OAK_SLAB.getDefaultState(),
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Blocks.OAK_FENCE.getDefaultState(), Blocks.GLASS_PANE.getDefaultState(),
                Blocks.WHITE_CARPET.getDefaultState(), Blocks.SCAFFOLDING.getDefaultState()
        };
        for (int i = 0; i < states.length; i++) {
            BlockPos pos = ctx.getAbsolutePos(new BlockPos(3, 6 + i * 2, 3));
            var state = states[i];
            world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
            VoxelShape baseline = state.getCollisionShape(world, pos, ShapeContext.absent());
            ctx.assertTrue(!baseline.isEmpty(), "shape control must have collision: " + state);
            ctx.assertTrue(SlabPlacementDyAttachment.record(world, pos, -3.0), "shape fixture must store -3");
            VoxelShape explicit = state.getCollisionShape(world, pos, ShapeContext.absent());
            VoxelShape cached = state.getCollisionShape(world, pos);
            ctx.assertTrue(Math.abs(explicit.getMin(Direction.Axis.Y) - baseline.getMin(Direction.Axis.Y) + 3.0) < EPS
                            && Math.abs(explicit.getMax(Direction.Axis.Y) - baseline.getMax(Direction.Axis.Y) + 3.0) < EPS,
                    "stored collision must shift once: " + state + " got " + explicit.getBoundingBox());
            ctx.assertTrue(Math.abs(cached.getMin(Direction.Axis.Y) - explicit.getMin(Direction.Axis.Y)) < EPS,
                    "cached and explicit collision queries must agree: " + state);
            SlabPlacementDyAttachment.clear(world, pos);
            VoxelShape unstored = state.getCollisionShape(world, pos, ShapeContext.absent());
            ctx.assertTrue(Math.abs(unstored.getMin(Direction.Axis.Y) - baseline.getMin(Direction.Axis.Y)) < EPS,
                    "unstored collision behavior must stay unchanged: " + state);
        }
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void storedCollisionAndSupportCrossAnEmptySection(TestContext ctx) {
        var world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
        BlockPos owner = new BlockPos(origin.getX(), 32, origin.getZ());
        world.setBlockState(owner, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabPlacementDyAttachment.record(world, owner, -3.0), "section fixture must write");
        ctx.assertTrue(count(world.getBlockCollisions(null, body(owner, -3.0))) > 0,
                "stored collision must be found through the empty section below its owner");
        PlayerEntity player = PlacementHarness.mockPlayerHolding(ctx, owner.down(3), new ItemStack(Blocks.STONE));
        ctx.assertTrue(world.findSupportingBlockPos(player, body(owner, -3.0)).filter(owner::equals).isPresent(),
                "support-position query must find the same stored owner");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void playerMovementStopsAtTheStoredBody(TestContext ctx) {
        var world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
        for (int i = 0; i < 2; i++) {
            double dy = i == 0 ? 0.0 : -3.0;
            BlockPos owner = new BlockPos(origin.getX(), 32, origin.getZ() + i * 4);
            world.setBlockState(owner, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            ctx.assertTrue(SlabPlacementDyAttachment.record(world, owner, dy), "movement fixture must write");
            PlayerEntity player = PlacementHarness.mockPlayerHolding(ctx, owner.west(2), new ItemStack(Blocks.STONE));
            player.setPosition(owner.getX() - 1.5, owner.getY() + dy, owner.getZ() + 0.5);
            player.move(net.minecraft.entity.MovementType.SELF, new Vec3d(3.0, 0.0, 0.0));
            System.out.println("[NORMAL_DEPTH_TEST] player movement dy=" + dy + " maxX=" + player.getBoundingBox().maxX
                    + " wallX=" + owner.getX());
            ctx.assertTrue(player.getBoundingBox().maxX <= owner.getX() + EPS,
                    "player must stop at the visible full-block body for dy=" + dy);
            ctx.assertTrue(player.getX() > owner.getX() - 1.4,
                    "movement control must actually advance before the collision");
        }
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void scaffoldingStandingUsesTheStoredTop(TestContext ctx) {
        var world = ctx.getWorld();
        for (int i = 0; i < 2; i++) {
            double dy = i == 0 ? 0.0 : -3.0;
            BlockPos pos = ctx.getAbsolutePos(new BlockPos(3, 6 + i * 4, 3));
            var state = Blocks.SCAFFOLDING.getDefaultState()
                    .with(net.minecraft.block.ScaffoldingBlock.DISTANCE, 0)
                    .with(net.minecraft.block.ScaffoldingBlock.BOTTOM, false);
            world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
            ctx.assertTrue(SlabPlacementDyAttachment.record(world, pos, dy), "scaffold fixture must store");
            PlayerEntity player = PlacementHarness.mockPlayerHolding(ctx, pos, new ItemStack(Blocks.SCAFFOLDING));
            player.setPosition(pos.getX() + 0.5, pos.getY() + dy + 1.0, pos.getZ() + 0.5);
            VoxelShape standing = state.getCollisionShape(world, pos, ShapeContext.of(player));
            ctx.assertTrue(!standing.isEmpty() && Math.abs(standing.getMax(Direction.Axis.Y) - (1.0 + dy)) < EPS,
                    "scaffold standing layer must end at its stored top for dy=" + dy);
            player.setPosition(pos.getX() + 0.5, pos.getY() + dy + 0.2, pos.getZ() + 0.5);
            ctx.assertTrue(state.getCollisionShape(world, pos, ShapeContext.of(player)).isEmpty(),
                    "inside the scaffold must remain open for dy=" + dy);
        }
        ctx.complete();
    }

    private static Box body(BlockPos pos, double dy) {
        return new Box(pos.getX() + 0.2, pos.getY() + dy + 0.2, pos.getZ() + 0.2,
                pos.getX() + 0.8, pos.getY() + dy + 0.8, pos.getZ() + 0.8);
    }

    private static int count(Iterable<VoxelShape> shapes) {
        int count = 0;
        for (VoxelShape ignored : shapes) count++;
        return count;
    }
}
