package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * TEST31: lever dust follows the production-synchronized frozen placement dy and survives
 * harmless neighbour updates.
 *
 * <p>This is deliberately a real client-world path: vanilla {@link LeverBlock#animateTick} and
 * {@link BlockState#useWithoutItem} both reach {@code ClientLevel.addParticle}; the test-only
 * mixin observes that sink without changing the particle or its arguments.
 */
public final class LeverParticleFrozenAnchorClientGameTest implements FabricClientGameTest {
    private static final double BASELINE_DY = 0.0d;
    private static final double LOWERED_DY = -1.5d;
    private static final double EPSILON = 1.0e-9d;
    private static final int EXPECTED_CASES = 24;
    private static final ThreadLocal<List<ParticleSample>> ACTIVE_CAPTURE = new ThreadLocal<>();

    /** Called by the observation-only TEST31 mixin; it never changes the emitted particle. */
    public static void captureDustParticle(
            DustParticleOptions dust,
            double x,
            double y,
            double z,
            double xVelocity,
            double yVelocity,
            double zVelocity) {
        List<ParticleSample> capture = ACTIVE_CAPTURE.get();
        if (capture != null) {
            var color = dust.getColor();
            capture.add(new ParticleSample(
                    x, y, z,
                    xVelocity, yVelocity, zVelocity,
                    color.x(), color.y(), color.z(), dust.getScale()));
        }
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            singleplayer.getClientLevel().waitForChunksDownload();
            ctx.waitFor(client -> client.level != null && client.player != null, 400);

            List<LeverFixture> fixtures = singleplayer.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = server.overworld();
                BlockPos origin = player.blockPosition().relative(player.getDirection(), 3).immutable();
                List<LeverFixture> result = createFixtures(origin);
                for (LeverFixture fixture : result) {
                    placeFixturePair(level, fixture);
                }
                return result;
            });

            ctx.waitFor(client -> allFixturesSynchronized(client.level, fixtures), 400);
            ctx.runOnClient(client -> assertClientFixturesSynchronized(client.level, fixtures,
                    "initial production sync"));

            singleplayer.getServer().computeOnServer(server -> {
                var level = server.overworld();
                for (LeverFixture fixture : fixtures) {
                    BlockPos neighbor = fixture.loweredPos().relative(fixture.facing().getClockWise());
                    level.setBlock(neighbor, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                    assertFixture(level, fixture.loweredPos(), LOWERED_DY,
                            fixture.name() + " server lowered fixture after neighbour edit");
                }
                return null;
            });

            ctx.waitFor(client -> allLoweredFixturesRemainSynchronized(client.level, fixtures), 400);
            ctx.runOnClient(client -> {
                assertClientLoweredFixturesRemainSynchronized(client.level, fixtures);
                runProductionSynchronizedDyNeighborInvariance24Cases(client.level, client.player, fixtures);
            });
        } catch (AssertionError error) {
            throw error;
        } catch (Exception error) {
            throw new RuntimeException("TEST31_PRODUCTION_SYNCHRONIZED_DY_NEIGHBOR_INVARIANCE_24_CASES: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage(), error);
        }
    }

    private static List<LeverFixture> createFixtures(BlockPos origin) {
        List<LeverFixture> fixtures = new ArrayList<>();
        int index = 0;
        for (AttachFace face : List.of(AttachFace.FLOOR, AttachFace.WALL, AttachFace.CEILING)) {
            for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
                for (EmissionRoute route : List.of(EmissionRoute.AMBIENT, EmissionRoute.CLICK)) {
                    BlockPos baselinePos = origin.offset((index % 6) * 6, 0, (index / 6) * 6).immutable();
                    fixtures.add(new LeverFixture(
                            face.getSerializedName() + " " + facing.getSerializedName() + " "
                                    + route.getSerializedName(),
                            baselinePos,
                            baselinePos.east(2).immutable(),
                            face,
                            facing,
                            route));
                    index++;
                }
            }
        }
        if (fixtures.size() != EXPECTED_CASES) {
            throw new AssertionError("TEST31 expected " + EXPECTED_CASES + " fixtures, created=" + fixtures.size());
        }
        return List.copyOf(fixtures);
    }

    private static void placeFixturePair(net.minecraft.server.level.ServerLevel level, LeverFixture fixture) {
        BlockState state = leverState(fixture.face(), fixture.facing(), fixture.route() == EmissionRoute.AMBIENT);
        placeValidLever(level, fixture.baselinePos(), state);
        placeValidLever(level, fixture.loweredPos(), state);
        SlabAnchorAttachment.writePlacementDy(level, fixture.baselinePos(), BASELINE_DY);
        SlabAnchorAttachment.writePlacementDy(level, fixture.loweredPos(), LOWERED_DY);
        assertFixture(level, fixture.baselinePos(), BASELINE_DY, fixture.name() + " server baseline fixture");
        assertFixture(level, fixture.loweredPos(), LOWERED_DY, fixture.name() + " server lowered fixture");
    }

    private static void placeValidLever(net.minecraft.server.level.ServerLevel level, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.values()) {
            level.setBlock(pos.relative(direction), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (!state.canSurvive(level, pos)) {
            throw new AssertionError("TEST31 fixture lacks a valid lever support at " + pos.toShortString()
                    + " state=" + state);
        }
    }

    private static void runProductionSynchronizedDyNeighborInvariance24Cases(
            ClientLevel level, Player player, List<LeverFixture> fixtures) {
        verifyObserverActivation(level);
        List<String> yMismatches = new ArrayList<>();
        int executedCases = 0;
        for (LeverFixture fixture : fixtures) {
            ParticlePair pair = switch (fixture.route()) {
                case AMBIENT -> captureAmbientPair(level, fixture);
                case CLICK -> captureClickPair(level, player, fixture);
            };
            String yMismatch = validateParticlePair(fixture, pair);
            if (yMismatch != null) {
                yMismatches.add(yMismatch);
            }
            recordExecutedCase(fixture, pair);
            executedCases++;
        }
        if (executedCases != EXPECTED_CASES) {
            throw new AssertionError("TEST31_PRODUCTION_SYNCHRONIZED_DY_NEIGHBOR_INVARIANCE_24_CASES: "
                    + "expected=" + EXPECTED_CASES + " executed=" + executedCases);
        }
        if (!yMismatches.isEmpty()) {
            throw new AssertionError(
                    "TEST31_LEVER_PARTICLE_RED: all 24 real client lever emission cases executed "
                            + "with production-synchronized exact frozen dy=-1.5, but anchored particle Y "
                            + "stayed at independently correct flat vanilla Y instead of flat Y-1.5; "
                            + String.join("; ", yMismatches));
        }
        Slabbed.LOGGER.info(
                "TEST31_PRODUCTION_SYNCHRONIZED_DY_NEIGHBOR_INVARIANCE_24_CASES | PASS | executedCases={}",
                executedCases);
    }

    private static ParticlePair captureAmbientPair(ClientLevel level, LeverFixture fixture) {
        BlockState baseline = level.getBlockState(fixture.baselinePos());
        BlockState lowered = level.getBlockState(fixture.loweredPos());
        assertExpectedLeverState(baseline, fixture, "ambient baseline");
        assertExpectedLeverState(lowered, fixture, "ambient lowered");
        return new ParticlePair(
                captureOne(fixture.name() + " ambient baseline", () ->
                        ((LeverBlock) baseline.getBlock()).animateTick(
                                baseline, level, fixture.baselinePos(), RandomSource.create(4096L))),
                captureOne(fixture.name() + " ambient lowered", () ->
                        ((LeverBlock) lowered.getBlock()).animateTick(
                                lowered, level, fixture.loweredPos(), RandomSource.create(4096L))));
    }

    private static ParticlePair captureClickPair(ClientLevel level, Player player, LeverFixture fixture) {
        BlockState baseline = level.getBlockState(fixture.baselinePos());
        BlockState lowered = level.getBlockState(fixture.loweredPos());
        assertExpectedLeverState(baseline, fixture, "click baseline");
        assertExpectedLeverState(lowered, fixture, "click lowered");
        return new ParticlePair(
                captureOne(fixture.name() + " click baseline", () ->
                        baseline.useWithoutItem(level, player, hit(fixture.baselinePos()))),
                captureOne(fixture.name() + " click lowered", () ->
                        lowered.useWithoutItem(level, player, hit(fixture.loweredPos()))));
    }

    private static void assertExpectedLeverState(BlockState state, LeverFixture fixture, String route) {
        if (!state.is(Blocks.LEVER)
                || state.getValue(BlockStateProperties.ATTACH_FACE) != fixture.face()
                || state.getValue(BlockStateProperties.HORIZONTAL_FACING) != fixture.facing()
                || state.getValue(BlockStateProperties.POWERED) != (fixture.route() == EmissionRoute.AMBIENT)) {
            throw new AssertionError("TEST31 " + fixture.name() + " " + route
                    + " changed before client particle emission: " + state);
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
            throw new AssertionError("TEST31 " + fixture.name()
                    + " changed dust colour between baseline and lowered fixture; baseline=" + baseline
                    + " lowered=" + lowered);
        }
        if (Math.abs(baseline.x() - lowered.x()) > EPSILON
                || Math.abs(baseline.z() - lowered.z()) > EPSILON) {
            throw new AssertionError("TEST31 " + fixture.name()
                    + " changed cell-local X/Z between baseline and lowered fixture; baseline=" + baseline
                    + " lowered=" + lowered);
        }
        assertVelocityPreserved(fixture, baseline, lowered);
        double expectedY = baseline.y() + LOWERED_DY;
        if (Math.abs(lowered.y() - expectedY) > EPSILON) {
            return fixture.name() + " flatVanillaY=" + baseline.y()
                    + " anchoredY=" + lowered.y() + " expectedAnchoredY=" + expectedY;
        }
        return null;
    }

    private static ParticleSample relativeToCell(BlockPos pos, ParticleSample sample) {
        return new ParticleSample(
                sample.x() - pos.getX(), sample.y() - pos.getY(), sample.z() - pos.getZ(),
                sample.xVelocity(), sample.yVelocity(), sample.zVelocity(),
                sample.red(), sample.green(), sample.blue(), sample.scale());
    }

    private static void assertExactRedAndScale(String caseName, ParticleSample sample, float expectedScale) {
        if (Float.floatToRawIntBits(sample.red()) != Float.floatToRawIntBits(1.0f)
                || Float.floatToRawIntBits(sample.green()) != Float.floatToRawIntBits(0.0f)
                || Float.floatToRawIntBits(sample.blue()) != Float.floatToRawIntBits(0.0f)
                || Float.floatToRawIntBits(sample.scale()) != Float.floatToRawIntBits(expectedScale)) {
            throw new AssertionError("TEST31 " + caseName + " expected exact red scale=" + expectedScale
                    + ", captured=" + sample);
        }
    }

    private static void assertFlatVanillaCoordinateOracle(LeverFixture fixture, ParticleSample baseline) {
        Direction facingOffset = fixture.facing().getOpposite();
        Direction attachmentOffset = switch (fixture.face()) {
            case FLOOR -> Direction.DOWN;
            case WALL -> fixture.facing().getOpposite();
            case CEILING -> Direction.UP;
        };
        double expectedX = 0.5d
                + 0.1d * facingOffset.getStepX()
                + 0.2d * attachmentOffset.getStepX();
        double expectedY = 0.5d
                + 0.1d * facingOffset.getStepY()
                + 0.2d * attachmentOffset.getStepY();
        double expectedZ = 0.5d
                + 0.1d * facingOffset.getStepZ()
                + 0.2d * attachmentOffset.getStepZ();
        if (Math.abs(baseline.x() - expectedX) > EPSILON
                || Math.abs(baseline.y() - expectedY) > EPSILON
                || Math.abs(baseline.z() - expectedZ) > EPSILON
                || Double.doubleToRawLongBits(baseline.xVelocity())
                != Double.doubleToRawLongBits(0.0d)
                || Double.doubleToRawLongBits(baseline.yVelocity())
                != Double.doubleToRawLongBits(0.0d)
                || Double.doubleToRawLongBits(baseline.zVelocity())
                != Double.doubleToRawLongBits(0.0d)) {
            throw new AssertionError("TEST31 " + fixture.name()
                    + " flat control disagrees with independent vanilla coordinate/velocity oracle; "
                    + "expected=(" + expectedX + "," + expectedY + "," + expectedZ
                    + "; velocity=0,0,0) captured=" + baseline);
        }
    }

    private static void assertVelocityPreserved(
            LeverFixture fixture, ParticleSample baseline, ParticleSample lowered) {
        if (Double.doubleToRawLongBits(baseline.xVelocity())
                != Double.doubleToRawLongBits(lowered.xVelocity())
                || Double.doubleToRawLongBits(baseline.yVelocity())
                != Double.doubleToRawLongBits(lowered.yVelocity())
                || Double.doubleToRawLongBits(baseline.zVelocity())
                != Double.doubleToRawLongBits(lowered.zVelocity())) {
            throw new AssertionError("TEST31 " + fixture.name()
                    + " changed particle velocity between flat and anchored fixtures; baseline=" + baseline
                    + " lowered=" + lowered);
        }
    }

    private static boolean allFixturesSynchronized(ClientLevel level, List<LeverFixture> fixtures) {
        return fixtures.stream().allMatch(fixture ->
                fixtureSynchronized(level, fixture.baselinePos(), BASELINE_DY)
                        && fixtureSynchronized(level, fixture.loweredPos(), LOWERED_DY));
    }

    private static boolean allLoweredFixturesRemainSynchronized(ClientLevel level, List<LeverFixture> fixtures) {
        return fixtures.stream().allMatch(fixture -> fixtureSynchronized(level, fixture.loweredPos(), LOWERED_DY));
    }

    private static boolean fixtureSynchronized(BlockGetter level, BlockPos pos, double expectedDy) {
        if (!level.getBlockState(pos).is(Blocks.LEVER)) {
            return false;
        }
        SlabAnchorAttachment.PlacementDyFact backing = SlabAnchorAttachment.rawPlacementDyFact(level, pos);
        return backing.present()
                && backing.rawBits() == Double.doubleToRawLongBits(expectedDy)
                && Double.doubleToRawLongBits(SlabSupport.getYOffset(level, pos, level.getBlockState(pos)))
                == Double.doubleToRawLongBits(expectedDy);
    }

    private static void assertClientFixturesSynchronized(
            ClientLevel level, List<LeverFixture> fixtures, String phase) {
        for (LeverFixture fixture : fixtures) {
            assertFixture(level, fixture.baselinePos(), BASELINE_DY, fixture.name() + " " + phase + " baseline");
            assertFixture(level, fixture.loweredPos(), LOWERED_DY, fixture.name() + " " + phase + " lowered");
        }
    }

    private static void assertClientLoweredFixturesRemainSynchronized(ClientLevel level, List<LeverFixture> fixtures) {
        for (LeverFixture fixture : fixtures) {
            assertFixture(level, fixture.loweredPos(), LOWERED_DY,
                    fixture.name() + " client lowered fixture after neighbour edit");
        }
        Slabbed.LOGGER.info("TEST31_NEIGHBOR_INVARIANCE | PASS | loweredFixtures={}", fixtures.size());
    }

    private static void assertFixture(BlockGetter level, BlockPos pos, double expectedDy, String caseName) {
        BlockState state = level.getBlockState(pos);
        SlabAnchorAttachment.PlacementDyFact backing = SlabAnchorAttachment.rawPlacementDyFact(level, pos);
        double effective = SlabSupport.getYOffset(level, pos, state);
        if (!state.is(Blocks.LEVER)
                || !backing.present()
                || backing.rawBits() != Double.doubleToRawLongBits(expectedDy)
                || Double.doubleToRawLongBits(effective) != Double.doubleToRawLongBits(expectedDy)) {
            throw new AssertionError("TEST31 " + caseName + " lacks production-synchronized backing dy="
                    + expectedDy + "; state=" + state + " backing=" + backing + " effective=" + effective);
        }
    }

    private static void verifyObserverActivation(ClientLevel level) {
        DustParticleOptions probe = new DustParticleOptions(0x123456, 0.75f);
        double x = 11.25d;
        double y = -22.5d;
        double z = 33.75d;
        double xVelocity = 0.125d;
        double yVelocity = -0.25d;
        double zVelocity = 0.5d;
        ParticleSample captured = captureOne("observer activation", () ->
                level.addParticle(probe, x, y, z, xVelocity, yVelocity, zVelocity));
        var color = probe.getColor();
        if (Double.doubleToRawLongBits(captured.x()) != Double.doubleToRawLongBits(x)
                || Double.doubleToRawLongBits(captured.y()) != Double.doubleToRawLongBits(y)
                || Double.doubleToRawLongBits(captured.z()) != Double.doubleToRawLongBits(z)
                || Double.doubleToRawLongBits(captured.xVelocity())
                != Double.doubleToRawLongBits(xVelocity)
                || Double.doubleToRawLongBits(captured.yVelocity())
                != Double.doubleToRawLongBits(yVelocity)
                || Double.doubleToRawLongBits(captured.zVelocity())
                != Double.doubleToRawLongBits(zVelocity)
                || Float.floatToRawIntBits(captured.red()) != Float.floatToRawIntBits(color.x())
                || Float.floatToRawIntBits(captured.green()) != Float.floatToRawIntBits(color.y())
                || Float.floatToRawIntBits(captured.blue()) != Float.floatToRawIntBits(color.z())
                || Float.floatToRawIntBits(captured.scale()) != Float.floatToRawIntBits(probe.getScale())) {
            throw new AssertionError("TEST31 observer activation changed coordinates/velocity/color/scale; "
                    + "expected=(" + x + "," + y + "," + z + "; velocity="
                    + xVelocity + "," + yVelocity + "," + zVelocity + "; "
                    + color + "," + probe.getScale()
                    + ") captured=" + captured);
        }
        Slabbed.LOGGER.info("TEST31_OBSERVER_ACTIVATION | PASS | sample={}", captured);
    }

    private static void recordExecutedCase(LeverFixture fixture, ParticlePair pair) {
        Slabbed.LOGGER.info(
                "TEST31_CASE_EXECUTED | {} | baseline={} | lowered={}",
                fixture.name(),
                relativeToCell(fixture.baselinePos(), pair.baseline()),
                relativeToCell(fixture.loweredPos(), pair.lowered()));
    }

    private static ParticleSample captureOne(String caseName, Runnable emission) {
        if (ACTIVE_CAPTURE.get() != null) {
            throw new IllegalStateException("TEST31 capture already active");
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
            throw new AssertionError("TEST31 " + caseName
                    + " expected exactly one redstone particle at ClientLevel.addParticle, captured="
                    + captured.size());
        }
        return captured.getFirst();
    }

    private static BlockState leverState(AttachFace face, Direction facing, boolean powered) {
        return Blocks.LEVER.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, face)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.POWERED, powered);
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
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

        String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private record LeverFixture(
            String name,
            BlockPos baselinePos,
            BlockPos loweredPos,
            AttachFace face,
            Direction facing,
            EmissionRoute route) {
    }

    private record ParticleSample(
            double x,
            double y,
            double z,
            double xVelocity,
            double yVelocity,
            double zVelocity,
            float red,
            float green,
            float blue,
            float scale) {
    }

    private record ParticlePair(ParticleSample baseline, ParticleSample lowered) {
    }
}
