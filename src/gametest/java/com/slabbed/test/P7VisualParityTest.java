package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class P7VisualParityTest {
    private static final String TEMPLATE = "empty";
    private static final double EPSILON = 1.0e-6d;

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void numericFactOwnsVisualInteractionCollisionAndCull(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        List<String> failures = new ArrayList<>();
        int[] halfSteps = {0, -1, -2, -4};
        int[] xs = {1, 3, 5, 7};

        for (int index = 0; index < halfSteps.length; index++) {
            BlockPos owner = ctx.absolutePos(new BlockPos(xs[index], 5, 2));
            world.setBlock(owner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                            world.getChunkAt(owner), owner, halfSteps[index]),
                    "P7 setup must author the numeric fact at " + owner);
            check(failures, !SlabAnchorAttachment.isAnchored(world, owner),
                    "dy=" + halfSteps[index] * 0.5d + " unexpectedly depended on an anchor marker");

            BlockState state = world.getBlockState(owner);
            double expectedDy = halfSteps[index] * 0.5d;
            checkNear(failures, SlabSupport.getYOffset(world, owner, state), expectedDy,
                    "resolver must return the stored P7 fact");
            checkNear(failures, ClientDy.dyFor(world, owner, state), expectedDy,
                    "model facade must consume the stored P7 fact");
            checkNear(failures, state.getShape(world, owner, CollisionContext.empty()).bounds().minY, expectedDy,
                    "outline must consume the stored P7 fact");

            double aimedY = owner.getY() + expectedDy + 0.5d;
            BlockHitResult hit = SlabbedOffsetRaycast.raycast(
                    world,
                    new Vec3(owner.getX() + 0.5d, aimedY, owner.getZ() + 2.0d),
                    new Vec3(owner.getX() + 0.5d, aimedY, owner.getZ() - 2.0d),
                    CollisionContext.empty());
            check(failures, hit.getBlockPos().equals(owner),
                    "raycast dy=" + expectedDy + " returned " + hit.getBlockPos());

            double visualCenterY = owner.getY() + expectedDy + 0.25d;
            AABB visibleBody = box(owner, visualCenterY);
            check(failures, !world.noCollision(visibleBody),
                    "collision missing inside rendered body for dy=" + expectedDy);

            if (expectedDy < 0.0d) {
                AABB phantom = box(owner, owner.getY() + 0.75d);
                check(failures, world.noCollision(phantom),
                        "phantom collision remained above rendered body for dy=" + expectedDy);
            }
        }

        BlockPos west = ctx.absolutePos(new BlockPos(2, 5, 6));
        BlockPos east = west.east();
        world.setBlock(west.below(), Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(west, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(east, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(world.getChunkAt(west), west, -1),
                "P7 cull setup must author west -0.5");
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(world.getChunkAt(east), east, -1),
                "P7 cull setup must author east -0.5");
        SlabAnchorAttachment.addAnchor(world, west, world.getBlockState(west));
        check(failures, SlabAnchorAttachment.isAnchored(world, west)
                        && !SlabAnchorAttachment.isAnchored(world, east),
                "P7 cull control requires different legacy markers");
        check(failures, !SlabSupport.isSlabHeightStepFace(
                        world, west, world.getBlockState(west), Direction.EAST),
                "different markers with equal numeric heights must remain culled");

        SlabAnchorAttachment.removeAnchor(world, west);
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(world.getChunkAt(east), east, 0),
                "P7 cull setup must change only east numeric height");
        check(failures, !SlabAnchorAttachment.isAnchored(world, west)
                        && !SlabAnchorAttachment.isAnchored(world, east),
                "P7 cull discriminator requires equal legacy markers");
        check(failures, SlabSupport.isSlabHeightStepFace(
                        world, west, world.getBlockState(west), Direction.EAST),
                "equal markers with different numeric heights must expose the west face");
        check(failures, SlabSupport.isSlabHeightStepFace(
                        world, east, world.getBlockState(east), Direction.WEST),
                "equal markers with different numeric heights must expose the east face");

        ctx.assertTrue(failures.isEmpty(), "P7 matrix failures: " + String.join(" | ", failures));
        ctx.succeed();
    }

    private static AABB box(BlockPos owner, double centerY) {
        return new AABB(
                owner.getX() + 0.35d, centerY - 0.10d, owner.getZ() + 0.35d,
                owner.getX() + 0.65d, centerY + 0.10d, owner.getZ() + 0.65d);
    }

    private static void check(List<String> failures, boolean condition, String message) {
        if (!condition) {
            failures.add(message);
        }
    }

    private static void checkNear(List<String> failures, double actual, double expected, String message) {
        check(failures, Math.abs(actual - expected) <= EPSILON,
                message + "; expected=" + expected + " actual=" + actual);
    }
}
