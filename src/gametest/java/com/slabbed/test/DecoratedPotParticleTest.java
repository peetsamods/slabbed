package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;

/** Native decorated-pot insertion proof for its server-origin particle request. */
public final class DecoratedPotParticleTest {

    private static final double DEEP_DY = -3.0d;

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void insertionBurstFollowsStoredDepth(TestContext context) {
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            ServerWorld world = context.getWorld();
            BlockPos relative = new BlockPos(4, 4, 4);
            BlockPos pos = context.getAbsolutePos(relative);
            BlockState pot = Blocks.DECORATED_POT.getDefaultState();
            ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();

            world.setBlockState(pos, pot, Block.NOTIFY_LISTENERS);
            ServerParticleCapture.Emission flat = insert(context, world, player, pos);

            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(pos, pot, Block.NOTIFY_LISTENERS);
            int writes = SlabAnchorAttachment.writePlacementDyBatch(
                    world, Map.of(pos, Double.doubleToRawLongBits(DEEP_DY)));
            context.assertTrue(writes == 1, "premise: decorated pot stored -3 exactly once");
            context.assertTrue(SlabSupport.getYOffset(world, pos, pot) == DEEP_DY,
                    "premise: decorated pot reads stored -3");
            ServerParticleCapture.Emission deep = insert(context, world, player, pos);

            assertVanillaRequest(context, flat, pos, pos.getY() + 1.2d, "flat");
            assertVanillaRequest(context, deep, pos, pos.getY() - 1.8d, "deep");
            context.assertTrue(same(flat.x(), deep.x()) && same(flat.z(), deep.z()),
                    "stored depth must preserve insertion X/Z");
            context.assertTrue(same(deep.y() - flat.y(), DEEP_DY),
                    "stored depth must move insertion Y exactly -3; flatY=" + flat.y()
                            + " deepY=" + deep.y());
        } finally {
            ServerParticleCapture.reset();
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
        context.complete();
    }

    private static ServerParticleCapture.Emission insert(
            TestContext context, ServerWorld world, ServerPlayerEntity player, BlockPos pos
    ) {
        ServerParticleCapture.begin();
        try {
            BlockState state = world.getBlockState(pos);
            BlockHitResult hit = new BlockHitResult(
                    Vec3d.ofCenter(pos), Direction.UP, pos, false);
            state.onUseWithItem(
                    new ItemStack(Items.DIAMOND), world, player, Hand.MAIN_HAND, hit);
            List<ServerParticleCapture.Emission> emissions = ServerParticleCapture.finish();
            context.assertTrue(emissions.size() == 1,
                    "decorated pot insertion must issue exactly one particle request; got "
                            + emissions.size());
            return emissions.getFirst();
        } catch (Throwable throwable) {
            ServerParticleCapture.reset();
            throw throwable;
        }
    }

    private static void assertVanillaRequest(
            TestContext context, ServerParticleCapture.Emission emission,
            BlockPos pos, double expectedY, String label
    ) {
        context.assertTrue(emission.effect().getType() == ParticleTypes.DUST_PLUME,
                label + " insertion must preserve DUST_PLUME type");
        context.assertTrue(emission.count() == 7,
                label + " insertion must preserve vanilla count 7; got " + emission.count());
        context.assertTrue(same(emission.x(), pos.getX() + 0.5d)
                        && same(emission.y(), expectedY)
                        && same(emission.z(), pos.getZ() + 0.5d),
                label + " insertion origin mismatch: "
                        + emission.x() + "," + emission.y() + "," + emission.z());
        context.assertTrue(same(emission.spreadX(), 0.0d)
                        && same(emission.spreadY(), 0.0d)
                        && same(emission.spreadZ(), 0.0d)
                        && same(emission.speed(), 0.0d),
                label + " insertion must preserve zero spread and speed");
    }

    private static boolean same(double first, double second) {
        return Double.doubleToRawLongBits(first) == Double.doubleToRawLongBits(second);
    }
}
