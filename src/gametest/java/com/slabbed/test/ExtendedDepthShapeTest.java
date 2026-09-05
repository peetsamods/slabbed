package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shape-consumer proof only; placement, movement and rendered frames have separate checks. */
public final class ExtendedDepthShapeTest {
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void loweredFarmlandHitAdmissionUsesItsVisibleTop(TestContext ctx) {
        BlockPos pos = new BlockPos(4, 8, 4);
        var face = net.minecraft.util.math.Direction.UP;
        var hit = new net.minecraft.util.math.Vec3d(4.5d, 8.0d - 3.0d + 15.0d / 16.0d, 4.5d);
        var soil = Blocks.FARMLAND.getDefaultState();
        for (BlockState held : List.of(Blocks.WHEAT.getDefaultState(), Blocks.STONE.getDefaultState())) {
            ctx.assertTrue(com.slabbed.placement.LandingHitValidationPolicy.shiftedCenterDy(
                    pos, soil, -3.0d, face, hit, held) == -3.0d,
                    "lowered farmland top must admit an honest placement packet");
            ctx.assertTrue(Double.isNaN(com.slabbed.placement.LandingHitValidationPolicy.shiftedCenterDy(
                    pos, soil, -3.0d, net.minecraft.util.math.Direction.EAST, hit, held)),
                    "farmland top admission must not admit a side hit");
            ctx.assertTrue(Double.isNaN(com.slabbed.placement.LandingHitValidationPolicy.shiftedCenterDy(
                    pos, soil, -3.0d, face, hit.add(0.0d, 0.25d, 0.0d), held)),
                    "farmland top admission must not admit an off-surface hit");
            ctx.assertTrue(Double.isNaN(com.slabbed.placement.LandingHitValidationPolicy.shiftedCenterDy(
                    pos, soil, 0.0d, face, hit.add(0.0d, 3.0d, 0.0d), held)),
                    "flat farmland must keep vanilla validation");
        }
        ctx.complete();
    }
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void carpetClientHeightReadsStoredDepth(TestContext ctx) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            var world = ctx.getWorld();
            BlockPos pos = ctx.getAbsolutePos(new BlockPos(4, 5, 4));
            var carpet = Blocks.WHITE_CARPET.getDefaultState();
            world.setBlockState(pos, carpet, Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.writePlacementDyBatch(world,
                    Map.of(pos, Double.doubleToRawLongBits(-3.0d)));
            ctx.assertTrue(com.slabbed.client.ClientDy.dyFor(world, pos, carpet) == -3.0d,
                    "carpet client model height must read its stored -3 depth");
            ctx.complete();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void collisionShapesTranslateStoredDepth(TestContext ctx) {
        checkShapes(ctx, true, false);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void cachedCollisionShapesTranslateStoredDepth(TestContext ctx) {
        checkShapes(ctx, true, true);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void outlineShapesTranslateStoredDepth(TestContext ctx) {
        checkShapes(ctx, false, false);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void movementFindsMinusThreeOwnerAndLeavesItsOldCellEmpty(TestContext ctx) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            var world = ctx.getWorld();
            BlockPos pos = ctx.getAbsolutePos(new BlockPos(4, 6, 4));
            world.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.writePlacementDyBatch(world,
                    Map.of(pos, Double.doubleToRawLongBits(-3.0d)));
            var visible = new net.minecraft.util.math.Box(pos).offset(0.0d, -3.0d, 0.0d).contract(0.2d);
            var nativeCell = new net.minecraft.util.math.Box(pos).contract(0.2d);
            ctx.assertTrue(!world.isSpaceEmpty(null, visible),
                    "movement must discover the solid owner three cells above its visible body");
            ctx.assertTrue(world.isSpaceEmpty(null, nativeCell),
                    "the old unshifted owner cell must not retain an invisible collision");
            ctx.complete();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    private static void checkShapes(TestContext ctx, boolean collision, boolean cached) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            var world = ctx.getWorld();
            BlockPos pos = ctx.getAbsolutePos(new BlockPos(4, 5, 4));
            List<String> failures = new ArrayList<>();
            int failureCount = 0;
            int checked = 0;
            for (Block block : Registries.BLOCK) {
                if (!"minecraft".equals(Registries.BLOCK.getId(block).getNamespace())
                        || block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
                    continue;
                }
                BlockState state = block.getDefaultState();
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.writePlacementDyBatch(world,
                        Map.of(pos, Double.doubleToRawLongBits(0.0d)));
                VoxelShape baseline = shape(state, ctx, pos, collision, cached);
                for (double depth : new double[]{-0.5d, -1.0d, -1.5d, -2.0d, -2.5d, -3.0d}) {
                    SlabAnchorAttachment.writePlacementDyBatch(world,
                            Map.of(pos, Double.doubleToRawLongBits(depth)));
                    VoxelShape actual = shape(state, ctx, pos, collision, cached);
                    checked++;
                    if (VoxelShapes.matchesAnywhere(baseline.offset(0.0d, depth, 0.0d),
                            actual, BooleanBiFunction.NOT_SAME)) {
                        failureCount++;
                        if (failures.size() < 8) {
                            failures.add(Registries.BLOCK.getId(block) + " depth=" + depth);
                        }
                    }
                }
            }
            ctx.assertTrue(failures.isEmpty(), (collision ? "collision" : "outline")
                    + " translation mismatch; checked=" + checked + " failures=" + failureCount
                    + " firstFailures=" + failures);
            com.slabbed.Slabbed.LOGGER.info("EXTENDED_DEPTH_SHAPES component={} checked={} PASS",
                    collision ? "collision" : "outline", checked);
            ctx.complete();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    private static VoxelShape shape(BlockState state, TestContext ctx, BlockPos pos,
                                    boolean collision, boolean cached) {
        return collision ? (cached ? state.getCollisionShape(ctx.getWorld(), pos)
                : state.getCollisionShape(ctx.getWorld(), pos, ShapeContext.absent()))
                : state.getOutlineShape(ctx.getWorld(), pos, ShapeContext.absent());
    }
}
