package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.ParticleUtil;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

/** Native shape-distributed particle proof for turtle-egg placement event 2012. */
public final class ParticleUtilParticleTest {

    private static final double DEEP_DY = -3.0d;
    private static final double EGG_HEIGHT = 7.0d / 16.0d;
    private static final double EPSILON = 1.0e-9d;

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void turtleEggParticlesStayInsideTranslatedHeight(TestContext context) {
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            ServerWorld world = context.getWorld();
            BlockPos pos = context.getAbsolutePos(new BlockPos(4, 4, 4));
            BlockState egg = Blocks.TURTLE_EGG.getDefaultState();
            world.setBlockState(pos.down(), Blocks.SAND.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(pos, egg, Block.NOTIFY_LISTENERS);

            List<ParticleCapture.Emission> flat = capture(world, pos);
            assertBand(context, flat, pos, pos.getY(), pos.getY() + EGG_HEIGHT, "flat egg");

            int writes = SlabAnchorAttachment.writePlacementDyBatch(
                    world, Map.of(pos, Double.doubleToRawLongBits(DEEP_DY)));
            context.assertTrue(writes == 1, "premise: turtle egg stored -3 exactly once");
            context.assertTrue(SlabSupport.getYOffset(world, pos, egg) == DEEP_DY,
                    "premise: turtle egg reads stored -3");

            List<ParticleCapture.Emission> deep = capture(world, pos);
            assertBand(context, deep, pos,
                    pos.getY() + DEEP_DY, pos.getY() + DEEP_DY + EGG_HEIGHT,
                    "deep egg");
        } finally {
            ParticleCapture.reset();
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
        context.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void airDistributionRemainsVanilla(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(4, 4, 4));
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        List<ParticleCapture.Emission> emissions = capture(world, pos);
        assertBand(context, emissions, pos, pos.getY(), pos.getY() + 1.0d, "air control");
        context.complete();
    }

    private static List<ParticleCapture.Emission> capture(ServerWorld world, BlockPos pos) {
        ParticleCapture.begin();
        try {
            ParticleUtil.spawnParticlesAround(world, pos, 15, ParticleTypes.HAPPY_VILLAGER);
            return ParticleCapture.finish();
        } catch (Throwable throwable) {
            ParticleCapture.reset();
            throw throwable;
        }
    }

    private static void assertBand(
            TestContext context, List<ParticleCapture.Emission> emissions, BlockPos pos,
            double minY, double maxY, String label
    ) {
        context.assertTrue(emissions.size() == 15,
                label + " must preserve count 15; got " + emissions.size());
        for (int index = 0; index < emissions.size(); index++) {
            ParticleCapture.Emission emission = emissions.get(index);
            context.assertTrue(emission.effect().getType() == ParticleTypes.HAPPY_VILLAGER,
                    label + " particle " + index + " must preserve HAPPY_VILLAGER type");
            context.assertTrue(emission.x() >= pos.getX() - EPSILON
                            && emission.x() <= pos.getX() + 1.0d + EPSILON
                            && emission.z() >= pos.getZ() - EPSILON
                            && emission.z() <= pos.getZ() + 1.0d + EPSILON,
                    label + " particle " + index + " must preserve vanilla X/Z distribution");
            context.assertTrue(emission.y() >= minY - EPSILON
                            && emission.y() <= maxY + EPSILON,
                    label + " particle " + index + " outside Y band [" + minY + "," + maxY
                            + "]: " + emission.y());
            context.assertTrue(Double.isFinite(emission.velocityX())
                            && Double.isFinite(emission.velocityY())
                            && Double.isFinite(emission.velocityZ()),
                    label + " particle " + index + " velocity must remain finite");
        }
    }
}
