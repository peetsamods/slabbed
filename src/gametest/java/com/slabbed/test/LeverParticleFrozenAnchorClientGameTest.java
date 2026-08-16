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
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;

/**
 * Proves that both lever-dust routes follow the exact frozen visual height for every attachment
 * face and horizontal facing, without changing vanilla's cell-local X/Z, colour, scale, or
 * velocity. The lowered fixtures use dy {@code -1.5} and must retain it across a harmless
 * neighbour update.
 */
public final class LeverParticleFrozenAnchorClientGameTest implements FabricClientGameTest {
    private static final double BASELINE_DY = 0.0d;
    private static final double LOWERED_DY = -1.5d;
    private static final double EPSILON = 1.0e-9d;
    private static final int EXPECTED_CASES = 24;
    private static final ThreadLocal<List<ParticleSample>> ACTIVE_CAPTURE = new ThreadLocal<>();

    /** Called by the observation-only GameTest mixin; it never changes the emitted particle. */
    public static void captureDustParticle(
            DustParticleEffect dust,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ) {
        List<ParticleSample> capture = ACTIVE_CAPTURE.get();
        if (capture == null) {
            return;
        }
        var color = dust.getColor();
        capture.add(new ParticleSample(
                x,
                y,
                z,
                velocityX,
                velocityY,
                velocityZ,
                color.x(),
                color.y(),
                color.z(),
                dust.getScale()));
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            singleplayer.getClientWorld().waitForChunksRender();

            AtomicReference<List<LeverFixture>> fixtureRef = new AtomicReference<>();
            singleplayer.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                BlockPos origin = player.getBlockPos()
                        .offset(player.getHorizontalFacing(), 3)
                        .toImmutable();
                List<LeverFixture> fixtures = createFixtures(origin);
                for (LeverFixture fixture : fixtures) {
                    placeFixturePair(server.getOverworld(), fixture);
                }
                fixtureRef.set(fixtures);
            });

            List<LeverFixture> fixtures = fixtureRef.get();
            if (fixtures == null) {
                throw new AssertionError("lever particle fixtures were not created");
            }
            ctx.waitTick();
            ctx.waitFor(client -> client.world != null
                    && allFixturesSynchronized(client.world, fixtures), 400);
            ctx.runOnClient(client -> assertClientFixturesSynchronized(
                    client.world, fixtures, "initial synchronization"));

            singleplayer.getServer().runOnServer(server -> {
                var world = server.getOverworld();
                for (LeverFixture fixture : fixtures) {
                    BlockPos neighbor = fixture.loweredPos().offset(fixture.facing().rotateYClockwise());
                    world.setBlockState(neighbor, Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
                    assertFixture(
                            world,
                            fixture.loweredPos(),
                            LOWERED_DY,
                            fixture.name() + " server fixture after neighbour update");
                }
            });

            ctx.waitTick();
            ctx.waitFor(client -> client.world != null
                    && allLoweredFixturesRemainSynchronized(client.world, fixtures), 400);
            ctx.runOnClient(client -> {
                assertClientLoweredFixturesRemainSynchronized(client.world, fixtures);
                runParticleMatrix(client.world, client.player, fixtures);
            });
        } catch (AssertionError error) {
            throw error;
        } catch (Exception error) {
            throw new RuntimeException(
                    "LEVER_PARTICLE_CLIENT_PROOF: "
                            + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error);
        }
    }

    private static List<LeverFixture> createFixtures(BlockPos origin) {
        List<LeverFixture> fixtures = new ArrayList<>();
        int index = 0;
        for (BlockFace face : List.of(BlockFace.FLOOR, BlockFace.WALL, BlockFace.CEILING)) {
            for (Direction facing : List.of(
                    Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
                for (EmissionRoute route : List.of(EmissionRoute.AMBIENT, EmissionRoute.CLICK)) {
                    BlockPos baselinePos = origin.add((index % 6) * 6, 0, (index / 6) * 6)
                            .toImmutable();
                    fixtures.add(new LeverFixture(
                            face.asString() + " " + facing.asString() + " " + route.asString(),
                            baselinePos,
                            baselinePos.east(2).toImmutable(),
                            face,
                            facing,
                            route));
                    index++;
                }
            }
        }
        if (fixtures.size() != EXPECTED_CASES) {
            throw new AssertionError(
                    "expected " + EXPECTED_CASES + " lever particle fixtures, created=" + fixtures.size());
        }
        return List.copyOf(fixtures);
    }

    private static void placeFixturePair(
            net.minecraft.server.world.ServerWorld world, LeverFixture fixture) {
        BlockState state = leverState(
                fixture.face(),
                fixture.facing(),
                fixture.route() == EmissionRoute.AMBIENT);
        placeValidLever(world, fixture.baselinePos(), state);
        placeValidLever(world, fixture.loweredPos(), state);
        SlabPlacementDyAttachment.record(world, fixture.baselinePos(), BASELINE_DY);
        SlabPlacementDyAttachment.record(world, fixture.loweredPos(), LOWERED_DY);
        assertFixture(
                world,
                fixture.baselinePos(),
                BASELINE_DY,
                fixture.name() + " server baseline fixture");
        assertFixture(
                world,
                fixture.loweredPos(),
                LOWERED_DY,
                fixture.name() + " server lowered fixture");
    }

    private static void placeValidLever(
            net.minecraft.server.world.ServerWorld world, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.values()) {
            world.setBlockState(pos.offset(direction), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        }
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
        if (!state.canPlaceAt(world, pos)) {
            throw new AssertionError(
                    "lever fixture lacks valid support at " + pos.toShortString() + " state=" + state);
        }
    }

    private static void runParticleMatrix(
            ClientWorld world, PlayerEntity player, List<LeverFixture> fixtures) {
        if (player == null) {
            throw new AssertionError("client player missing for lever particle proof");
        }
        verifyObserverActivation(world);
        List<String> mismatches = new ArrayList<>();
        int executedCases = 0;
        for (LeverFixture fixture : fixtures) {
            ParticlePair pair = switch (fixture.route()) {
                case AMBIENT -> captureAmbientPair(world, fixture);
                case CLICK -> captureClickPair(world, player, fixture);
            };
            String mismatch = validateParticlePair(fixture, pair);
            if (mismatch != null) {
                mismatches.add(mismatch);
            }
            executedCases++;
        }
        if (executedCases != EXPECTED_CASES) {
            throw new AssertionError(
                    "LEVER_PARTICLE_CASE_COUNT_RED expected="
                            + EXPECTED_CASES + " executed=" + executedCases);
        }
        if (!mismatches.isEmpty()) {
            throw new AssertionError(
                    "LEVER_PARTICLE_Y_RED: all 24 routes ran at frozen dy=-1.5; "
                            + String.join("; ", mismatches));
        }
        Slabbed.LOGGER.info(
                "LEVER_PARTICLE_CLIENT_PROOF | PASS | executedCases={} | loweredDy={}",
                executedCases,
                LOWERED_DY);
    }

    private static ParticlePair captureAmbientPair(ClientWorld world, LeverFixture fixture) {
        BlockState baseline = world.getBlockState(fixture.baselinePos());
        BlockState lowered = world.getBlockState(fixture.loweredPos());
        assertExpectedLeverState(baseline, fixture, "ambient baseline");
        assertExpectedLeverState(lowered, fixture, "ambient lowered");
        return new ParticlePair(
                captureOne(fixture.name() + " ambient baseline", () ->
                        ((LeverBlock) baseline.getBlock()).randomDisplayTick(
                                baseline, world, fixture.baselinePos(), Random.create(4096L))),
                captureOne(fixture.name() + " ambient lowered", () ->
                        ((LeverBlock) lowered.getBlock()).randomDisplayTick(
                                lowered, world, fixture.loweredPos(), Random.create(4096L))));
    }

    private static ParticlePair captureClickPair(
            ClientWorld world, PlayerEntity player, LeverFixture fixture) {
        BlockState baseline = world.getBlockState(fixture.baselinePos());
        BlockState lowered = world.getBlockState(fixture.loweredPos());
        assertExpectedLeverState(baseline, fixture, "click baseline");
        assertExpectedLeverState(lowered, fixture, "click lowered");
        return new ParticlePair(
                captureOne(fixture.name() + " click baseline", () ->
                        baseline.onUse(world, player, hit(fixture.baselinePos()))),
                captureOne(fixture.name() + " click lowered", () ->
                        lowered.onUse(world, player, hit(fixture.loweredPos()))));
    }

    private static void assertExpectedLeverState(
            BlockState state, LeverFixture fixture, String route) {
        if (!state.isOf(Blocks.LEVER)
                || state.get(Properties.BLOCK_FACE) != fixture.face()
                || state.get(Properties.HORIZONTAL_FACING) != fixture.facing()
                || state.get(Properties.POWERED) != (fixture.route() == EmissionRoute.AMBIENT)) {
            throw new AssertionError(
                    fixture.name() + " " + route + " changed before particle emission: " + state);
        }
    }

    private static String validateParticlePair(LeverFixture fixture, ParticlePair pair) {
        ParticleSample baseline = relativeToCell(fixture.baselinePos(), pair.baseline());
        ParticleSample lowered = relativeToCell(fixture.loweredPos(), pair.lowered());
        assertExactRedAndScale(fixture.name() + " baseline", baseline, fixture.route().scale());
        assertExactRedAndScale(fixture.name() + " lowered", lowered, fixture.route().scale());
        assertFlatVanillaCoordinateOracle(fixture, baseline);
        if (Float.floatToRawIntBits(baseline.red()) != Float.floatToRawIntBits(lowered.red())
                || Float.floatToRawIntBits(baseline.green()) != Float.floatToRawIntBits(lowered.green())
                || Float.floatToRawIntBits(baseline.blue()) != Float.floatToRawIntBits(lowered.blue())) {
            throw new AssertionError(
                    fixture.name() + " changed dust colour between baseline and lowered fixtures");
        }
        if (Math.abs(baseline.x() - lowered.x()) > EPSILON
                || Math.abs(baseline.z() - lowered.z()) > EPSILON) {
            throw new AssertionError(
                    fixture.name() + " changed cell-local X/Z between baseline and lowered fixtures");
        }
        assertVelocityPreserved(fixture, baseline, lowered);
        double expectedY = baseline.y() + LOWERED_DY;
        if (Math.abs(lowered.y() - expectedY) > EPSILON) {
            return fixture.name() + " baselineY=" + baseline.y()
                    + " loweredY=" + lowered.y() + " expected=" + expectedY;
        }
        return null;
    }

    private static ParticleSample relativeToCell(BlockPos pos, ParticleSample sample) {
        return new ParticleSample(
                sample.x() - pos.getX(),
                sample.y() - pos.getY(),
                sample.z() - pos.getZ(),
                sample.velocityX(),
                sample.velocityY(),
                sample.velocityZ(),
                sample.red(),
                sample.green(),
                sample.blue(),
                sample.scale());
    }

    private static void assertExactRedAndScale(
            String caseName, ParticleSample sample, float expectedScale) {
        if (Float.floatToRawIntBits(sample.red()) != Float.floatToRawIntBits(1.0f)
                || Float.floatToRawIntBits(sample.green()) != Float.floatToRawIntBits(0.0f)
                || Float.floatToRawIntBits(sample.blue()) != Float.floatToRawIntBits(0.0f)
                || Float.floatToRawIntBits(sample.scale()) != Float.floatToRawIntBits(expectedScale)) {
            throw new AssertionError(
                    caseName + " expected exact red scale=" + expectedScale + ", captured=" + sample);
        }
    }

    private static void assertFlatVanillaCoordinateOracle(
            LeverFixture fixture, ParticleSample baseline) {
        Direction facingOffset = fixture.facing().getOpposite();
        Direction attachmentOffset = switch (fixture.face()) {
            case FLOOR -> Direction.DOWN;
            case WALL -> fixture.facing().getOpposite();
            case CEILING -> Direction.UP;
        };
        double expectedX = 0.5d
                + 0.1d * facingOffset.getOffsetX()
                + 0.2d * attachmentOffset.getOffsetX();
        double expectedY = 0.5d
                + 0.1d * facingOffset.getOffsetY()
                + 0.2d * attachmentOffset.getOffsetY();
        double expectedZ = 0.5d
                + 0.1d * facingOffset.getOffsetZ()
                + 0.2d * attachmentOffset.getOffsetZ();
        if (Math.abs(baseline.x() - expectedX) > EPSILON
                || Math.abs(baseline.y() - expectedY) > EPSILON
                || Math.abs(baseline.z() - expectedZ) > EPSILON
                || Double.doubleToRawLongBits(baseline.velocityX())
                        != Double.doubleToRawLongBits(0.0d)
                || Double.doubleToRawLongBits(baseline.velocityY())
                        != Double.doubleToRawLongBits(0.0d)
                || Double.doubleToRawLongBits(baseline.velocityZ())
                        != Double.doubleToRawLongBits(0.0d)) {
            throw new AssertionError(
                    fixture.name() + " disagrees with vanilla's coordinate and velocity formula; "
                            + "expected=(" + expectedX + "," + expectedY + "," + expectedZ
                            + "; velocity=0,0,0) captured=" + baseline);
        }
    }

    private static void assertVelocityPreserved(
            LeverFixture fixture, ParticleSample baseline, ParticleSample lowered) {
        if (Double.doubleToRawLongBits(baseline.velocityX())
                        != Double.doubleToRawLongBits(lowered.velocityX())
                || Double.doubleToRawLongBits(baseline.velocityY())
                        != Double.doubleToRawLongBits(lowered.velocityY())
                || Double.doubleToRawLongBits(baseline.velocityZ())
                        != Double.doubleToRawLongBits(lowered.velocityZ())) {
            throw new AssertionError(
                    fixture.name() + " changed particle velocity between baseline and lowered fixtures");
        }
    }

    private static boolean allFixturesSynchronized(
            ClientWorld world, List<LeverFixture> fixtures) {
        return fixtures.stream().allMatch(fixture ->
                fixtureSynchronized(world, fixture.baselinePos(), BASELINE_DY)
                        && fixtureSynchronized(world, fixture.loweredPos(), LOWERED_DY));
    }

    private static boolean allLoweredFixturesRemainSynchronized(
            ClientWorld world, List<LeverFixture> fixtures) {
        return fixtures.stream().allMatch(fixture ->
                fixtureSynchronized(world, fixture.loweredPos(), LOWERED_DY));
    }

    private static boolean fixtureSynchronized(BlockView world, BlockPos pos, double expectedDy) {
        if (!world.getBlockState(pos).isOf(Blocks.LEVER)) {
            return false;
        }
        SlabPlacementDyAttachment.PlacementDyFact backing =
                SlabPlacementDyAttachment.rawFact(world, pos);
        return backing.present()
                && backing.rawBits() == Double.doubleToRawLongBits(expectedDy)
                && Double.doubleToRawLongBits(
                                SlabSupport.getYOffset(world, pos, world.getBlockState(pos)))
                        == Double.doubleToRawLongBits(expectedDy);
    }

    private static void assertClientFixturesSynchronized(
            ClientWorld world, List<LeverFixture> fixtures, String phase) {
        if (world == null) {
            throw new AssertionError("client world missing during " + phase);
        }
        for (LeverFixture fixture : fixtures) {
            assertFixture(
                    world,
                    fixture.baselinePos(),
                    BASELINE_DY,
                    fixture.name() + " " + phase + " baseline");
            assertFixture(
                    world,
                    fixture.loweredPos(),
                    LOWERED_DY,
                    fixture.name() + " " + phase + " lowered");
        }
    }

    private static void assertClientLoweredFixturesRemainSynchronized(
            ClientWorld world, List<LeverFixture> fixtures) {
        if (world == null) {
            throw new AssertionError("client world missing after neighbour update");
        }
        for (LeverFixture fixture : fixtures) {
            assertFixture(
                    world,
                    fixture.loweredPos(),
                    LOWERED_DY,
                    fixture.name() + " client fixture after neighbour update");
        }
    }

    private static void assertFixture(
            BlockView world, BlockPos pos, double expectedDy, String caseName) {
        BlockState state = world.getBlockState(pos);
        SlabPlacementDyAttachment.PlacementDyFact backing =
                SlabPlacementDyAttachment.rawFact(world, pos);
        double effective = SlabSupport.getYOffset(world, pos, state);
        if (!state.isOf(Blocks.LEVER)
                || !backing.present()
                || backing.rawBits() != Double.doubleToRawLongBits(expectedDy)
                || Double.doubleToRawLongBits(effective) != Double.doubleToRawLongBits(expectedDy)) {
            throw new AssertionError(
                    caseName + " lacks exact frozen dy=" + expectedDy
                            + "; state=" + state + " backing=" + backing + " effective=" + effective);
        }
    }

    private static void verifyObserverActivation(ClientWorld world) {
        DustParticleEffect probe = new DustParticleEffect(0x123456, 0.75f);
        ParticleSample captured = captureOne("observer activation", () ->
                world.addParticleClient(probe, 11.25d, -22.5d, 33.75d, 0.125d, -0.25d, 0.5d));
        var color = probe.getColor();
        if (Double.doubleToRawLongBits(captured.x()) != Double.doubleToRawLongBits(11.25d)
                || Double.doubleToRawLongBits(captured.y()) != Double.doubleToRawLongBits(-22.5d)
                || Double.doubleToRawLongBits(captured.z()) != Double.doubleToRawLongBits(33.75d)
                || Double.doubleToRawLongBits(captured.velocityX())
                        != Double.doubleToRawLongBits(0.125d)
                || Double.doubleToRawLongBits(captured.velocityY())
                        != Double.doubleToRawLongBits(-0.25d)
                || Double.doubleToRawLongBits(captured.velocityZ())
                        != Double.doubleToRawLongBits(0.5d)
                || Float.floatToRawIntBits(captured.red()) != Float.floatToRawIntBits(color.x())
                || Float.floatToRawIntBits(captured.green()) != Float.floatToRawIntBits(color.y())
                || Float.floatToRawIntBits(captured.blue()) != Float.floatToRawIntBits(color.z())
                || Float.floatToRawIntBits(captured.scale())
                        != Float.floatToRawIntBits(probe.getScale())) {
            throw new AssertionError("particle observer changed the activation sample: " + captured);
        }
    }

    private static ParticleSample captureOne(String caseName, Runnable emission) {
        if (ACTIVE_CAPTURE.get() != null) {
            throw new IllegalStateException("lever particle capture already active");
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
            throw new AssertionError(
                    caseName + " expected exactly one redstone particle, captured=" + captured.size());
        }
        return captured.getFirst();
    }

    private static BlockState leverState(BlockFace face, Direction facing, boolean powered) {
        return Blocks.LEVER.getDefaultState()
                .with(Properties.BLOCK_FACE, face)
                .with(Properties.HORIZONTAL_FACING, facing)
                .with(Properties.POWERED, powered);
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false, false);
    }

    private enum EmissionRoute {
        AMBIENT(0.5f),
        CLICK(1.0f);

        private final float scale;

        EmissionRoute(float scale) {
            this.scale = scale;
        }

        float scale() {
            return scale;
        }

        String asString() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private record LeverFixture(
            String name,
            BlockPos baselinePos,
            BlockPos loweredPos,
            BlockFace face,
            Direction facing,
            EmissionRoute route) {
    }

    private record ParticleSample(
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            float red,
            float green,
            float blue,
            float scale) {
    }

    private record ParticlePair(ParticleSample baseline, ParticleSample lowered) {
    }
}
