package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.AbstractCandleBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/** Proves that interaction-triggered candle smoke follows the candle's frozen visual height. */
public final class CandleExtinguishParticleClientGameTest implements FabricClientGameTest {
    private static final double FLAT_DY = 0.0d;
    private static final double LOWERED_DY = -1.5d;
    private static final double EPSILON = 1.0e-9d;
    private static final ThreadLocal<List<ParticleSample>> ACTIVE_CAPTURE = new ThreadLocal<>();

    /** Called by the observation-only GameTest mixin; it never changes the emitted particle. */
    public static void captureParticle(
            ParticleEffect effect,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ) {
        List<ParticleSample> capture = ACTIVE_CAPTURE.get();
        if (capture != null) {
            capture.add(new ParticleSample(effect, x, y, z, velocityX, velocityY, velocityZ));
        }
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            singleplayer.getClientWorld().waitForChunksRender();

            AtomicReference<Fixture> fixtureRef = new AtomicReference<>();
            singleplayer.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                BlockPos flatPos = player.getBlockPos().offset(player.getHorizontalFacing(), 3).toImmutable();
                Fixture fixture = new Fixture(flatPos, flatPos.east(2).toImmutable());
                placeFixture(server.getOverworld(), fixture.flatPos(), FLAT_DY);
                placeFixture(server.getOverworld(), fixture.loweredPos(), LOWERED_DY);
                fixtureRef.set(fixture);
            });

            Fixture fixture = fixtureRef.get();
            if (fixture == null) {
                throw new AssertionError("candle extinguish fixtures were not created");
            }
            ctx.waitTick();
            ctx.waitFor(client -> client.world != null && fixturesSynchronized(client.world, fixture), 400);
            ctx.runOnClient(client -> runParticleProof(client.world, fixture));
        } catch (AssertionError error) {
            throw error;
        } catch (Exception error) {
            throw new RuntimeException(
                    "CANDLE_EXTINGUISH_PARTICLE_CLIENT_PROOF: "
                            + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error);
        }
    }

    private static void placeFixture(
            net.minecraft.server.world.ServerWorld world, BlockPos pos, double dy) {
        world.setBlockState(pos.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        BlockState litCandle = Blocks.CANDLE.getDefaultState().with(Properties.LIT, true);
        world.setBlockState(pos, litCandle, Block.NOTIFY_ALL);
        if (!SlabPlacementDyAttachment.record(world, pos, dy)) {
            throw new AssertionError("fixture: candle frozen dy write was refused at " + pos.toShortString());
        }
        assertFixture(world, pos, dy, "server fixture");
    }

    private static boolean fixturesSynchronized(ClientWorld world, Fixture fixture) {
        return fixtureSynchronized(world, fixture.flatPos(), FLAT_DY)
                && fixtureSynchronized(world, fixture.loweredPos(), LOWERED_DY);
    }

    private static boolean fixtureSynchronized(BlockView world, BlockPos pos, double expectedDy) {
        BlockState state = world.getBlockState(pos);
        SlabPlacementDyAttachment.PlacementDyFact fact = SlabPlacementDyAttachment.rawFact(world, pos);
        return state.isOf(Blocks.CANDLE)
                && state.get(Properties.LIT)
                && fact.present()
                && fact.rawBits() == Double.doubleToRawLongBits(expectedDy)
                && Double.doubleToRawLongBits(SlabSupport.getVisualYOffset(world, pos, state))
                        == Double.doubleToRawLongBits(expectedDy);
    }

    private static void assertFixture(BlockView world, BlockPos pos, double expectedDy, String phase) {
        if (!fixtureSynchronized(world, pos, expectedDy)) {
            throw new AssertionError(
                    phase + " lacks a lit candle with exact frozen dy=" + expectedDy
                            + " at " + pos.toShortString());
        }
    }

    private static void runParticleProof(ClientWorld world, Fixture fixture) {
        verifyObserverActivation(world);
        assertFixture(world, fixture.flatPos(), FLAT_DY, "client flat fixture");
        assertFixture(world, fixture.loweredPos(), LOWERED_DY, "client lowered fixture");

        ParticleSample flat = captureOne("flat candle extinguish", () -> extinguish(world, fixture.flatPos()));
        ParticleSample lowered = captureOne(
                "lowered candle extinguish", () -> extinguish(world, fixture.loweredPos()));
        ParticleSample flatLocal = relativeToCell(fixture.flatPos(), flat);
        ParticleSample loweredLocal = relativeToCell(fixture.loweredPos(), lowered);

        if (flat.effect().getType() != ParticleTypes.SMOKE
                || lowered.effect().getType() != ParticleTypes.SMOKE) {
            throw new AssertionError("candle extinguish must preserve the smoke particle type");
        }
        assertVelocity("flat", flatLocal);
        assertVelocity("lowered", loweredLocal);
        if (Math.abs(flatLocal.x() - loweredLocal.x()) > EPSILON
                || Math.abs(flatLocal.z() - loweredLocal.z()) > EPSILON) {
            throw new AssertionError(
                    "candle extinguish changed cell-local X/Z; flat=" + flatLocal
                            + " lowered=" + loweredLocal);
        }
        if (flatLocal.x() < 0.0d || flatLocal.x() > 1.0d
                || flatLocal.y() < 0.0d || flatLocal.y() > 1.0d
                || flatLocal.z() < 0.0d || flatLocal.z() > 1.0d) {
            throw new AssertionError("flat candle smoke must originate inside its vanilla cell: " + flatLocal);
        }
        double expectedLoweredY = flatLocal.y() + LOWERED_DY;
        if (Math.abs(loweredLocal.y() - expectedLoweredY) > EPSILON) {
            throw new AssertionError(
                    "CANDLE_EXTINGUISH_PARTICLE_Y_RED flatLocal=("
                            + flatLocal.x() + "," + flatLocal.y() + "," + flatLocal.z()
                            + ") loweredLocal=(" + loweredLocal.x() + "," + loweredLocal.y()
                            + "," + loweredLocal.z() + ") expectedLoweredY=" + expectedLoweredY
                            + " storedDy=" + LOWERED_DY);
        }
        Slabbed.LOGGER.info(
                "CANDLE_EXTINGUISH_PARTICLE_CLIENT_PROOF | PASS | flatLocalY={} | loweredLocalY={} | storedDy={}",
                flatLocal.y(), loweredLocal.y(), LOWERED_DY);
    }

    private static void extinguish(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        AbstractCandleBlock.extinguish(null, state, world, pos);
    }

    private static void verifyObserverActivation(ClientWorld world) {
        ParticleSample probe = captureOne("observer activation", () -> world.addParticleClient(
                ParticleTypes.SMOKE, 11.25d, -22.5d, 33.75d, 0.125d, -0.25d, 0.5d));
        if (Double.doubleToRawLongBits(probe.x()) != Double.doubleToRawLongBits(11.25d)
                || Double.doubleToRawLongBits(probe.y()) != Double.doubleToRawLongBits(-22.5d)
                || Double.doubleToRawLongBits(probe.z()) != Double.doubleToRawLongBits(33.75d)
                || Double.doubleToRawLongBits(probe.velocityX()) != Double.doubleToRawLongBits(0.125d)
                || Double.doubleToRawLongBits(probe.velocityY()) != Double.doubleToRawLongBits(-0.25d)
                || Double.doubleToRawLongBits(probe.velocityZ()) != Double.doubleToRawLongBits(0.5d)) {
            throw new AssertionError("particle observer changed the activation sample: " + probe);
        }
    }

    private static ParticleSample captureOne(String label, Runnable emission) {
        if (ACTIVE_CAPTURE.get() != null) {
            throw new IllegalStateException("candle particle capture already active");
        }
        ACTIVE_CAPTURE.set(new ArrayList<>());
        List<ParticleSample> captured;
        try {
            emission.run();
        } finally {
            captured = List.copyOf(ACTIVE_CAPTURE.get());
            ACTIVE_CAPTURE.remove();
        }
        if (captured.size() != 1) {
            throw new AssertionError(label + " expected exactly one particle, captured=" + captured.size());
        }
        return captured.getFirst();
    }

    private static ParticleSample relativeToCell(BlockPos pos, ParticleSample sample) {
        return new ParticleSample(
                sample.effect(),
                sample.x() - pos.getX(),
                sample.y() - pos.getY(),
                sample.z() - pos.getZ(),
                sample.velocityX(),
                sample.velocityY(),
                sample.velocityZ());
    }

    private static void assertVelocity(String label, ParticleSample sample) {
        if (Double.doubleToRawLongBits(sample.velocityX()) != Double.doubleToRawLongBits(0.0d)
                || Math.abs(sample.velocityY() - 0.10000000149011612d) > EPSILON
                || Double.doubleToRawLongBits(sample.velocityZ()) != Double.doubleToRawLongBits(0.0d)) {
            throw new AssertionError(label + " candle extinguish changed vanilla velocity: " + sample);
        }
    }

    private record Fixture(BlockPos flatPos, BlockPos loweredPos) {
    }

    private record ParticleSample(
            ParticleEffect effect,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ) {
    }
}
