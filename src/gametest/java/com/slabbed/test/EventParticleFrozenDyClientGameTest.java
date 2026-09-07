package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Proves that event-driven candle and redstone-torch smoke follows exact frozen dy. */
public final class EventParticleFrozenDyClientGameTest implements FabricClientGameTest {
    private static final String CLIENT_GAMETEST_PASS =
            "CLIENT_GAMETEST | EventParticleFrozenDyClientGameTest | PASS";
    private static final double FLAT_DY = 0.0d;
    private static final double LOWERED_DY = -1.5d;
    private static final double EPSILON = 1.0e-9d;
    private static final int BURNOUT_EVENT = 1502;
    private static final long BURNOUT_SEED = 26_200_904L;
    private static final ThreadLocal<List<ParticleSample>> ACTIVE_CAPTURE = new ThreadLocal<>();

    /** Called by the observation-only test mixin; it never changes the emitted particle. */
    public static void captureParticle(
            ParticleOptions options,
            double x,
            double y,
            double z,
            double xVelocity,
            double yVelocity,
            double zVelocity) {
        List<ParticleSample> capture = ACTIVE_CAPTURE.get();
        if (capture != null && options.getType() == ParticleTypes.SMOKE) {
            capture.add(new ParticleSample(
                    options, x, y, z, xVelocity, yVelocity, zVelocity));
        }
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            singleplayer.getConnection().waitForChunksDownload();
            ctx.waitFor(client -> client.level != null && client.player != null, 400);

            Fixtures fixtures = singleplayer.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = server.overworld();
                BlockPos origin = player.blockPosition().relative(player.getDirection(), 3).immutable();
                Fixtures result = new Fixtures(
                        origin,
                        origin.east(2).immutable(),
                        origin.east(5).immutable(),
                        origin.east(7).immutable());
                placeFixture(level, result.flatCandle(), candleState(), FLAT_DY);
                placeFixture(level, result.loweredCandle(), candleState(), LOWERED_DY);
                placeFixture(level, result.flatTorch(), burnedOutTorchState(), FLAT_DY);
                placeFixture(level, result.loweredTorch(), burnedOutTorchState(), LOWERED_DY);
                return result;
            });

            ctx.waitFor(client -> fixturesSynchronized(client.level, fixtures), 400);
            ctx.runOnClient(client -> runEventProof(client.level, fixtures));
            Slabbed.LOGGER.info(CLIENT_GAMETEST_PASS);
        } catch (AssertionError error) {
            throw error;
        } catch (Exception error) {
            throw new RuntimeException(
                    "EVENT_PARTICLE_FROZEN_DY_CLIENT_PROOF: "
                            + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error);
        }
    }

    private static BlockState candleState() {
        return Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true);
    }

    private static BlockState burnedOutTorchState() {
        return Blocks.REDSTONE_TORCH.defaultBlockState().setValue(BlockStateProperties.LIT, false);
    }

    private static void placeFixture(
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos,
            BlockState state,
            double dy) {
        BlockState support = state.is(Blocks.REDSTONE_TORCH)
                ? Blocks.REDSTONE_BLOCK.defaultBlockState()
                : Blocks.STONE.defaultBlockState();
        level.setBlock(pos.below(), support, Block.UPDATE_ALL);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        SlabAnchorAttachment.writePlacementDy(level, pos, dy);
        assertFixture(level, pos, state, dy, "server fixture");
    }

    private static boolean fixturesSynchronized(ClientLevel level, Fixtures fixtures) {
        return fixtureSynchronized(level, fixtures.flatCandle(), Blocks.CANDLE, true, FLAT_DY)
                && fixtureSynchronized(level, fixtures.loweredCandle(), Blocks.CANDLE, true, LOWERED_DY)
                && fixtureSynchronized(level, fixtures.flatTorch(), Blocks.REDSTONE_TORCH, false, FLAT_DY)
                && fixtureSynchronized(level, fixtures.loweredTorch(), Blocks.REDSTONE_TORCH, false, LOWERED_DY);
    }

    private static boolean fixtureSynchronized(
            BlockGetter level,
            BlockPos pos,
            Block expectedBlock,
            boolean lit,
            double expectedDy) {
        BlockState state = level.getBlockState(pos);
        SlabAnchorAttachment.PlacementDyFact fact =
                SlabAnchorAttachment.rawPlacementDyFact(level, pos);
        return state.is(expectedBlock)
                && state.getValue(BlockStateProperties.LIT) == lit
                && fact.present()
                && fact.rawBits() == Double.doubleToRawLongBits(expectedDy)
                && Double.doubleToRawLongBits(SlabSupport.getYOffset(level, pos, state))
                        == Double.doubleToRawLongBits(expectedDy);
    }

    private static void assertFixture(
            BlockGetter level,
            BlockPos pos,
            BlockState expectedState,
            double expectedDy,
            String phase) {
        if (!fixtureSynchronized(
                level,
                pos,
                expectedState.getBlock(),
                expectedState.getValue(BlockStateProperties.LIT),
                expectedDy)) {
            throw new AssertionError(
                    phase + " lacks exact state/frozen dy=" + expectedDy + " at " + pos.toShortString());
        }
    }

    private static void runEventProof(ClientLevel level, Fixtures fixtures) {
        verifyObserverActivation(level);
        List<String> mismatches = new ArrayList<>();
        verifyCandleExtinguish(level, fixtures, mismatches);
        verifyRedstoneTorchBurnout(level, fixtures, mismatches);
        if (!mismatches.isEmpty()) {
            throw new AssertionError("EVENT_PARTICLE_Y_RED " + String.join("; ", mismatches));
        }
    }

    private static void verifyCandleExtinguish(
            ClientLevel level, Fixtures fixtures, List<String> mismatches) {
        List<ParticleSample> flat = capture("flat candle extinguish", () ->
                extinguish(level, fixtures.flatCandle()));
        List<ParticleSample> lowered = capture("lowered candle extinguish", () ->
                extinguish(level, fixtures.loweredCandle()));
        requireCount("candle extinguish flat", flat, 1);
        requireCount("candle extinguish lowered", lowered, 1);
        ParticleSample flatLocal = relativeToCell(fixtures.flatCandle(), flat.getFirst());
        ParticleSample loweredLocal = relativeToCell(fixtures.loweredCandle(), lowered.getFirst());
        assertSmokeAndVelocity("candle extinguish flat", flatLocal, 0.0d, 0.10000000149011612d, 0.0d);
        assertSmokeAndVelocity("candle extinguish lowered", loweredLocal, 0.0d, 0.10000000149011612d, 0.0d);
        assertSameXz("candle extinguish", flatLocal, loweredLocal);
        double expectedY = flatLocal.y() + LOWERED_DY;
        if (Math.abs(loweredLocal.y() - expectedY) > EPSILON) {
            mismatches.add("candle flatLocal=" + coordinates(flatLocal)
                    + " loweredLocal=" + coordinates(loweredLocal)
                    + " expectedLoweredY=" + expectedY);
        }
    }

    private static void verifyRedstoneTorchBurnout(
            ClientLevel level, Fixtures fixtures, List<String> mismatches) {
        level.getRandom().setSeed(BURNOUT_SEED);
        List<ParticleSample> flat = capture("flat redstone-torch burnout", () ->
                level.levelEvent(null, BURNOUT_EVENT, fixtures.flatTorch(), 0));
        level.getRandom().setSeed(BURNOUT_SEED);
        List<ParticleSample> lowered = capture("lowered redstone-torch burnout", () ->
                level.levelEvent(null, BURNOUT_EVENT, fixtures.loweredTorch(), 0));
        requireCount("redstone-torch burnout flat", flat, 5);
        requireCount("redstone-torch burnout lowered", lowered, 5);
        for (int index = 0; index < flat.size(); index++) {
            ParticleSample flatLocal = relativeToCell(fixtures.flatTorch(), flat.get(index));
            ParticleSample loweredLocal = relativeToCell(fixtures.loweredTorch(), lowered.get(index));
            assertSmokeAndVelocity("redstone-torch burnout flat " + index, flatLocal, 0.0d, 0.0d, 0.0d);
            assertSmokeAndVelocity(
                    "redstone-torch burnout lowered " + index, loweredLocal, 0.0d, 0.0d, 0.0d);
            assertSameXz("redstone-torch burnout " + index, flatLocal, loweredLocal);
            double expectedY = flatLocal.y() + LOWERED_DY;
            if (Math.abs(loweredLocal.y() - expectedY) > EPSILON) {
                mismatches.add("burnout[" + index + "] flatLocal=" + coordinates(flatLocal)
                        + " loweredLocal=" + coordinates(loweredLocal)
                        + " expectedLoweredY=" + expectedY);
            }
        }
    }

    private static void extinguish(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        AbstractCandleBlock.extinguish(null, state, level, pos);
    }

    private static void verifyObserverActivation(ClientLevel level) {
        List<ParticleSample> probe = capture("observer activation", () -> level.addParticle(
                ParticleTypes.SMOKE, 11.25d, -22.5d, 33.75d, 0.125d, -0.25d, 0.5d));
        requireCount("observer activation", probe, 1);
        ParticleSample sample = probe.getFirst();
        if (Double.doubleToRawLongBits(sample.x()) != Double.doubleToRawLongBits(11.25d)
                || Double.doubleToRawLongBits(sample.y()) != Double.doubleToRawLongBits(-22.5d)
                || Double.doubleToRawLongBits(sample.z()) != Double.doubleToRawLongBits(33.75d)
                || Double.doubleToRawLongBits(sample.xVelocity()) != Double.doubleToRawLongBits(0.125d)
                || Double.doubleToRawLongBits(sample.yVelocity()) != Double.doubleToRawLongBits(-0.25d)
                || Double.doubleToRawLongBits(sample.zVelocity()) != Double.doubleToRawLongBits(0.5d)) {
            throw new AssertionError("particle observer changed the activation sample: " + sample);
        }
    }

    private static List<ParticleSample> capture(String label, Runnable emission) {
        if (ACTIVE_CAPTURE.get() != null) {
            throw new IllegalStateException("event particle capture already active during " + label);
        }
        ACTIVE_CAPTURE.set(new ArrayList<>());
        try {
            emission.run();
            return List.copyOf(ACTIVE_CAPTURE.get());
        } finally {
            ACTIVE_CAPTURE.remove();
        }
    }

    private static void requireCount(String label, List<ParticleSample> samples, int expected) {
        if (samples.size() != expected) {
            throw new AssertionError(label + " expected particle count=" + expected
                    + " captured=" + samples.size());
        }
    }

    private static ParticleSample relativeToCell(BlockPos pos, ParticleSample sample) {
        return new ParticleSample(
                sample.options(),
                sample.x() - pos.getX(),
                sample.y() - pos.getY(),
                sample.z() - pos.getZ(),
                sample.xVelocity(),
                sample.yVelocity(),
                sample.zVelocity());
    }

    private static void assertSmokeAndVelocity(
            String label, ParticleSample sample,
            double xVelocity, double yVelocity, double zVelocity) {
        if (sample.options().getType() != ParticleTypes.SMOKE
                || Math.abs(sample.xVelocity() - xVelocity) > EPSILON
                || Math.abs(sample.yVelocity() - yVelocity) > EPSILON
                || Math.abs(sample.zVelocity() - zVelocity) > EPSILON) {
            throw new AssertionError(label + " changed particle type or velocity: " + sample);
        }
    }

    private static void assertSameXz(String label, ParticleSample flat, ParticleSample lowered) {
        if (Math.abs(flat.x() - lowered.x()) > EPSILON
                || Math.abs(flat.z() - lowered.z()) > EPSILON) {
            throw new AssertionError(label + " changed cell-local X/Z; flat=" + flat
                    + " lowered=" + lowered);
        }
    }

    private static String coordinates(ParticleSample sample) {
        return "(" + sample.x() + "," + sample.y() + "," + sample.z() + ")";
    }

    private record Fixtures(
            BlockPos flatCandle,
            BlockPos loweredCandle,
            BlockPos flatTorch,
            BlockPos loweredTorch) {
    }

    private record ParticleSample(
            ParticleOptions options,
            double x,
            double y,
            double z,
            double xVelocity,
            double yVelocity,
            double zVelocity) {
    }
}
