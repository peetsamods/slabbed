package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.particle.BlockDisplayParticleContext;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.AbstractCandleBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.List;
import java.util.Map;

/**
 * Exercises real vanilla display-tick emitters and compares their captured calls at flat and
 * stored deep heights. The server-only harness captures vanilla coordinates unchanged, then checks
 * the shared arithmetic used by the client sink. Client dispatcher wiring remains a live-client gate.
 */
public final class ParticleDisplayCoverageTest {

    private static final double DEEP_DY = -3.0d;
    private static final double EPSILON = 1.0e-9d;

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void candleFlameAndSmokeFollowStoredDepth(TestContext context) {
        assertDisplayEmitterFollowsDepth(context, "candle",
                Blocks.CANDLE.getDefaultState().with(Properties.LIT, true));
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void campfireSmokeFollowsStoredDepth(TestContext context) {
        assertDisplayEmitterFollowsDepth(context, "campfire",
                Blocks.CAMPFIRE.getDefaultState().with(Properties.LIT, true));
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void redstoneTorchDustFollowsStoredDepth(TestContext context) {
        assertDisplayEmitterFollowsDepth(context, "redstone torch",
                Blocks.REDSTONE_TORCH.getDefaultState().with(Properties.LIT, true));
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void genericVanillaDisplayEmitterFollowsStoredDepth(TestContext context) {
        assertDisplayEmitterFollowsDepth(context, "end rod", Blocks.END_ROD.getDefaultState());
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void candleExtinguishSmokeFollowsStoredDepth(TestContext context) {
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            ServerWorld world = context.getWorld();
            BlockPos pos = context.getAbsolutePos(new BlockPos(4, 4, 4));
            BlockState lit = Blocks.CANDLE.getDefaultState().with(Properties.LIT, true);

            world.setBlockState(pos, lit, Block.NOTIFY_LISTENERS);
            List<ParticleCapture.Emission> flat = captureCandleExtinguish(world, pos, lit);

            world.setBlockState(pos, lit, Block.NOTIFY_LISTENERS);
            writeDepth(context, world, pos, lit);
            List<ParticleCapture.Emission> deep = captureCandleExtinguish(world, pos, lit);

            context.assertTrue(!flat.isEmpty() && deep.size() == flat.size(),
                    "candle extinguish must preserve its nonzero smoke count; flat=" + flat.size()
                            + " deep=" + deep.size());
            for (int index = 0; index < flat.size(); index++) {
                assertActualEmissionShifted(context, "candle extinguish", index,
                        flat.get(index), deep.get(index));
            }
        } finally {
            ParticleCapture.reset();
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
        context.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void nonEmitterStaysSilentAtStoredDepth(TestContext context) {
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            ServerWorld world = context.getWorld();
            BlockPos pos = context.getAbsolutePos(new BlockPos(4, 4, 4));
            BlockState state = Blocks.STONE.getDefaultState();
            world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);

            List<ParticleCapture.Emission> flat = capture(world, pos, state, 0L);
            writeDepth(context, world, pos, state);
            List<ParticleCapture.Emission> deep = capture(world, pos, state, 0L);

            context.assertTrue(flat.isEmpty() && deep.isEmpty(),
                    "a non-emitting vanilla block must stay silent at flat and stored deep heights");
        } finally {
            ParticleCapture.reset();
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
        context.complete();
    }

    private static void assertDisplayEmitterFollowsDepth(
            TestContext context, String label, BlockState state
    ) {
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            ServerWorld world = context.getWorld();
            BlockPos pos = context.getAbsolutePos(new BlockPos(4, 4, 4));
            world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);

            Sample flat = firstEmissionSample(context, world, pos, state, label);
            writeDepth(context, world, pos, state);
            List<ParticleCapture.Emission> deep = capture(world, pos, state, flat.seed());

            context.assertTrue(deep.size() == flat.emissions().size(),
                    label + " must preserve vanilla particle count; flat=" + flat.emissions().size()
                            + " deep=" + deep.size());
            for (int index = 0; index < flat.emissions().size(); index++) {
                assertSameEmissionShifted(context, world, pos, state, label, index,
                        flat.emissions().get(index), deep.get(index));
            }
        } finally {
            ParticleCapture.reset();
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
        context.complete();
    }

    private static Sample firstEmissionSample(
            TestContext context, ServerWorld world, BlockPos pos, BlockState state, String label
    ) {
        for (long seed = 0L; seed < 4096L; seed++) {
            List<ParticleCapture.Emission> emissions = capture(world, pos, state, seed);
            if (!emissions.isEmpty()) {
                return new Sample(seed, emissions);
            }
        }
        context.throwGameTestException(label + " did not emit for any deterministic seed in range");
        throw new AssertionError("unreachable");
    }

    private static List<ParticleCapture.Emission> capture(
            ServerWorld world, BlockPos pos, BlockState state, long seed
    ) {
        ParticleCapture.begin();
        try {
            BlockDisplayParticleContext.runBlockDisplayTick(
                    state.getBlock(), state, world, pos, Random.create(seed));
            return ParticleCapture.finish();
        } catch (Throwable throwable) {
            ParticleCapture.reset();
            throw throwable;
        }
    }

    private static List<ParticleCapture.Emission> captureCandleExtinguish(
            ServerWorld world, BlockPos pos, BlockState litState
    ) {
        ParticleCapture.begin();
        try {
            AbstractCandleBlock.extinguish(null, litState, world, pos);
            return ParticleCapture.finish();
        } catch (Throwable throwable) {
            ParticleCapture.reset();
            throw throwable;
        }
    }

    private static void writeDepth(
            TestContext context, ServerWorld world, BlockPos pos, BlockState state
    ) {
        int writes = SlabAnchorAttachment.writePlacementDyBatch(
                world, Map.of(pos, Double.doubleToRawLongBits(DEEP_DY)));
        context.assertTrue(writes == 1, "premise: stored -3 height must be written exactly once");
        context.assertTrue(SlabSupport.getYOffset(world, pos, state) == DEEP_DY,
                "premise: public height must read the stored -3 value");
    }

    private static void assertSameEmissionShifted(
            TestContext context, ServerWorld world, BlockPos pos, BlockState state,
            String label, int index,
            ParticleCapture.Emission flat, ParticleCapture.Emission deep
    ) {
        context.assertTrue(flat.effect().getType() == deep.effect().getType(),
                label + " particle " + index + " must preserve its type");
        context.assertTrue(same(flat.x(), deep.x())
                        && same(flat.y(), deep.y())
                        && same(flat.z(), deep.z()),
                label + " particle " + index + " vanilla origin must be unchanged before transform");
        context.assertTrue(same(flat.velocityX(), deep.velocityX())
                        && same(flat.velocityY(), deep.velocityY())
                        && same(flat.velocityZ(), deep.velocityZ()),
                label + " particle " + index + " must preserve velocity");
        context.assertTrue(flat.important() == deep.important()
                        && flat.alwaysSpawn() == deep.alwaysSpawn(),
                label + " particle " + index + " must preserve dispatch flags");
        context.assertTrue(flat.activeOwner() == null && same(flat.activeDy(), 0.0d),
                label + " flat emission must not carry an offset scope");
        context.assertTrue(pos.equals(deep.activeOwner()) && same(deep.activeDy(), DEEP_DY),
                label + " deep emission must carry its exact owner and stored -3 scope");
        double translatedDeepY = BlockDisplayParticleContext.translateY(world, pos, state, deep.y());
        context.assertTrue(Math.abs((translatedDeepY - flat.y()) - DEEP_DY) <= EPSILON,
                label + " particle " + index + " production transform must move exactly -3; flatY="
                        + flat.y() + " transformedDeepY=" + translatedDeepY);
    }

    private static boolean same(double first, double second) {
        return Double.doubleToRawLongBits(first) == Double.doubleToRawLongBits(second);
    }

    private static void assertActualEmissionShifted(
            TestContext context, String label, int index,
            ParticleCapture.Emission flat, ParticleCapture.Emission deep
    ) {
        context.assertTrue(flat.effect().getType() == deep.effect().getType(),
                label + " particle " + index + " must preserve its type");
        context.assertTrue(same(flat.x(), deep.x()) && same(flat.z(), deep.z()),
                label + " particle " + index + " must preserve X/Z");
        context.assertTrue(same(flat.velocityX(), deep.velocityX())
                        && same(flat.velocityY(), deep.velocityY())
                        && same(flat.velocityZ(), deep.velocityZ()),
                label + " particle " + index + " must preserve velocity");
        context.assertTrue(Math.abs((deep.y() - flat.y()) - DEEP_DY) <= EPSILON,
                label + " particle " + index + " must move exactly -3; flatY=" + flat.y()
                        + " deepY=" + deep.y());
    }

    private record Sample(long seed, List<ParticleCapture.Emission> emissions) {
    }
}
