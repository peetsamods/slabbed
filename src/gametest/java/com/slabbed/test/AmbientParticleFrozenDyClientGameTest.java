package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Proves that the particles a block emits during its own display tick follow its frozen placement
 * dy, driven through the REAL client display-tick path rather than a direct block call.
 *
 * <p>The oracle is vanilla itself. A direct {@code Block.animateTick} call never enters the client's
 * display-tick driver, so the production funnel is provably inert for it and that run IS the vanilla
 * baseline. The same seed produces a bit-identical emission through the driver because each fixture
 * block reads ONLY the passed {@link RandomSource}, and a drive range of one pins the driver's own
 * position draws to zero, landing its reused cursor exactly on the fixture cell. Nothing here
 * replicates a vanilla coordinate formula or hardcodes an emission count.
 *
 * <p>The lever pair is the ownership row: the lever's own particle hook translates BOTH its click
 * and its ambient route, so driving a lowered lever through the display tick must produce a
 * bit-identical emission to calling it directly — one translation, never two.
 */
public final class AmbientParticleFrozenDyClientGameTest implements FabricClientGameTest {

    /**
     * Positive execution evidence. The client suite has no per-entrypoint count gate the way the
     * server suite does, so an entrypoint that never runs is indistinguishable from one that passed.
     * A green run is proof only when the log carries one line per client entrypoint.
     */
    private static final String CLIENT_GAMETEST_PASS =
            "CLIENT_GAMETEST | AmbientParticleFrozenDyClientGameTest | PASS";
    private static final double FLAT_DY = 0.0d;
    private static final double LOWERED_DY = -1.5d;
    private static final double EPSILON = 1.0e-9d;

    /**
     * A drive range of one makes both of the driver's per-axis draws zero, so its reused cursor
     * lands EXACTLY on the fixture cell whatever state the client's own random is in.
     */
    private static final int DRIVE_RANGE = 1;

    private static final long MAX_SEED = 4096L;
    private static final int ENVIRONMENT_PROBE_ATTEMPTS = 64;

    /**
     * Never placed by this test, so the driver's block-marker tail stays silent at every fixture AND
     * at the air probe. Air would NOT be safe here: it matches at the probe.
     */
    private static final Block SENTINEL_BLOCK = Blocks.BARRIER;

    private static final Set<ParticleType<?>> END_ROD_TYPES =
            Set.<ParticleType<?>>of(ParticleTypes.END_ROD);
    private static final Set<ParticleType<?>> TORCH_TYPES =
            Set.<ParticleType<?>>of(ParticleTypes.SMOKE, ParticleTypes.FLAME);
    private static final Set<ParticleType<?>> ENDER_CHEST_TYPES =
            Set.<ParticleType<?>>of(ParticleTypes.PORTAL);
    private static final Set<ParticleType<?>> LEVER_TYPES =
            Set.<ParticleType<?>>of(ParticleTypes.DUST);

    private static final ThreadLocal<List<ParticleSample>> ACTIVE_CAPTURE = new ThreadLocal<>();

    /** Called by the observation-only sink mixin, downstream of every Y translation. */
    public static void captureParticle(
            ParticleOptions options,
            double x,
            double y,
            double z,
            double xVelocity,
            double yVelocity,
            double zVelocity) {
        List<ParticleSample> capture = ACTIVE_CAPTURE.get();
        if (capture != null) {
            capture.add(new ParticleSample(options, x, y, z, xVelocity, yVelocity, zVelocity));
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
                ServerLevel level = server.overworld();
                BlockPos origin = player.blockPosition().relative(player.getDirection(), 3).immutable();
                Fixtures result = new Fixtures(
                        origin,
                        origin.east(2).immutable(),
                        origin.east(4).immutable(),
                        origin.east(6).immutable(),
                        origin.east(8).immutable(),
                        origin.east(10).immutable(),
                        origin.east(12).immutable(),
                        origin.east(14).immutable(),
                        origin.east(16).immutable());
                placeFixture(level, result.flatEndRod(), endRodState(), FLAT_DY);
                placeFixture(level, result.loweredEndRod(), endRodState(), LOWERED_DY);
                placeFixture(level, result.flatTorch(), torchState(), FLAT_DY);
                placeFixture(level, result.loweredTorch(), torchState(), LOWERED_DY);
                placeFixture(level, result.flatEnderChest(), enderChestState(), FLAT_DY);
                placeFixture(level, result.loweredEnderChest(), enderChestState(), LOWERED_DY);
                placeFixture(level, result.flatLever(), leverState(), FLAT_DY);
                placeFixture(level, result.loweredLever(), leverState(), LOWERED_DY);
                clearEnvironmentProbe(level, result.environmentProbe());
                return result;
            });

            ctx.waitFor(client -> fixturesSynchronized(client.level, fixtures), 400);
            ctx.runOnClient(client ->
                    withParticlesAll(client, () -> runAmbientProof(client.level, fixtures)));
            Slabbed.LOGGER.info(CLIENT_GAMETEST_PASS);
        } catch (AssertionError error) {
            throw error;
        } catch (Exception error) {
            throw new RuntimeException(
                    "AMBIENT_PARTICLE_FROZEN_DY_CLIENT_PROOF: "
                            + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error);
        }
    }

    // ---------------------------------------------------------------- fixtures

    /**
     * Every fixture block emits from the passed random alone and carries no block entity that could
     * rewrite its state out from under the proof. That last point rules out a lit furnace: its block
     * entity clears the lit property on the next server tick and replaces the block state.
     */
    private static BlockState endRodState() {
        return Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
    }

    private static BlockState torchState() {
        return Blocks.TORCH.defaultBlockState();
    }

    private static BlockState enderChestState() {
        return Blocks.ENDER_CHEST.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    private static BlockState leverState() {
        return Blocks.LEVER.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.POWERED, true);
    }

    private static void placeFixture(ServerLevel level, BlockPos pos, BlockState state, double dy) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        SlabAnchorAttachment.writePlacementDy(level, pos, dy);
        if (!fixtureSynchronized(level, pos, state, dy)) {
            throw new AssertionError("server fixture lacks exact state/frozen dy=" + dy
                    + " at " + pos.toShortString());
        }
    }

    private static void clearEnvironmentProbe(ServerLevel level, BlockPos pos) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static boolean fixturesSynchronized(ClientLevel level, Fixtures fixtures) {
        return level != null
                && fixtureSynchronized(level, fixtures.flatEndRod(), endRodState(), FLAT_DY)
                && fixtureSynchronized(level, fixtures.loweredEndRod(), endRodState(), LOWERED_DY)
                && fixtureSynchronized(level, fixtures.flatTorch(), torchState(), FLAT_DY)
                && fixtureSynchronized(level, fixtures.loweredTorch(), torchState(), LOWERED_DY)
                && fixtureSynchronized(level, fixtures.flatEnderChest(), enderChestState(), FLAT_DY)
                && fixtureSynchronized(level, fixtures.loweredEnderChest(), enderChestState(), LOWERED_DY)
                && fixtureSynchronized(level, fixtures.flatLever(), leverState(), FLAT_DY)
                && fixtureSynchronized(level, fixtures.loweredLever(), leverState(), LOWERED_DY)
                && level.getBlockState(fixtures.environmentProbe()).isAir();
    }

    private static boolean fixtureSynchronized(
            BlockGetter level, BlockPos pos, BlockState expectedState, double expectedDy) {
        BlockState state = level.getBlockState(pos);
        SlabAnchorAttachment.PlacementDyFact fact = SlabAnchorAttachment.rawPlacementDyFact(level, pos);
        return state == expectedState
                && fact.present()
                && fact.rawBits() == Double.doubleToRawLongBits(expectedDy)
                && Double.doubleToRawLongBits(SlabSupport.getYOffset(level, pos, state))
                        == Double.doubleToRawLongBits(expectedDy);
    }

    // ---------------------------------------------------------------- proof

    /**
     * The client particle sink returns early on the minimal particle setting, and the level it
     * computes consumes the client's own random on any setting other than ALL. The world builder's
     * consistent-settings flag is a world setting and pins neither, so the proof forces ALL for its
     * own duration and restores whatever was there.
     */
    private static void withParticlesAll(Minecraft client, Runnable proof) {
        OptionInstance<ParticleStatus> option = client.options.particles();
        ParticleStatus previous = option.get();
        if (previous != ParticleStatus.ALL) {
            option.set(ParticleStatus.ALL);
        }
        if (option.get() != ParticleStatus.ALL) {
            throw new AssertionError("AMBIENT_PARTICLE_PROOF_UNARMED: particle status is "
                    + option.get() + "; the client particle sink culls before the capture point");
        }
        try {
            proof.run();
        } finally {
            if (previous != ParticleStatus.ALL) {
                option.set(previous);
            }
        }
    }

    private static void runAmbientProof(ClientLevel level, Fixtures fixtures) {
        verifyObserverActivation(level, fixtures.flatTorch());
        verifyNoEnvironmentAmbience(level, fixtures.environmentProbe());
        List<String> mismatches = new ArrayList<>();
        assertFunnelAgainstVanilla("end rod flat",
                level, fixtures.flatEndRod(), FLAT_DY, END_ROD_TYPES, mismatches);
        assertFunnelAgainstVanilla("end rod lowered",
                level, fixtures.loweredEndRod(), LOWERED_DY, END_ROD_TYPES, mismatches);
        assertFunnelAgainstVanilla("torch flat",
                level, fixtures.flatTorch(), FLAT_DY, TORCH_TYPES, mismatches);
        assertFunnelAgainstVanilla("torch lowered",
                level, fixtures.loweredTorch(), LOWERED_DY, TORCH_TYPES, mismatches);
        assertFunnelAgainstVanilla("ender chest flat",
                level, fixtures.flatEnderChest(), FLAT_DY, ENDER_CHEST_TYPES, mismatches);
        assertFunnelAgainstVanilla("ender chest lowered",
                level, fixtures.loweredEnderChest(), LOWERED_DY, ENDER_CHEST_TYPES, mismatches);
        assertOwnedEmissionShiftedOnce(level, fixtures, mismatches);
        if (!mismatches.isEmpty()) {
            throw new AssertionError("AMBIENT_PARTICLE_Y_RED " + String.join("; ", mismatches));
        }
    }

    /**
     * Proves the sink probe is live at a coordinate the sink will not cull, and that the display-tick
     * translation is inert outside a display tick: a bare emission must arrive unchanged.
     */
    private static void verifyObserverActivation(ClientLevel level, BlockPos near) {
        double x = near.getX() + 0.25d;
        double y = near.getY() + 0.5d;
        double z = near.getZ() + 0.75d;
        List<ParticleSample> probe = captureAll("observer activation", () ->
                level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.125d, -0.25d, 0.5d));
        requireCount("observer activation", probe, 1);
        ParticleSample sample = probe.getFirst();
        if (sample.options().getType() != ParticleTypes.SMOKE
                || Double.doubleToRawLongBits(sample.x()) != Double.doubleToRawLongBits(x)
                || Double.doubleToRawLongBits(sample.y()) != Double.doubleToRawLongBits(y)
                || Double.doubleToRawLongBits(sample.z()) != Double.doubleToRawLongBits(z)
                || Double.doubleToRawLongBits(sample.xVelocity()) != Double.doubleToRawLongBits(0.125d)
                || Double.doubleToRawLongBits(sample.yVelocity()) != Double.doubleToRawLongBits(-0.25d)
                || Double.doubleToRawLongBits(sample.zVelocity()) != Double.doubleToRawLongBits(0.5d)) {
            throw new AssertionError(
                    "AMBIENT_PARTICLE_PROOF_UNARMED: the sink probe changed the activation sample: "
                            + sample);
        }
    }

    /**
     * Contamination check, and a real instrument test. The display-tick driver also runs this
     * version's environment ambient-particle loop for any state that is not a full collision block,
     * so driving an AIR cell with a block that is never placed must capture NOTHING. A non-empty
     * result means this world's biome emits ambience into the drive and the fixtures must move; it
     * fails loudly rather than being filtered away.
     */
    private static void verifyNoEnvironmentAmbience(ClientLevel level, BlockPos airProbe) {
        if (!level.getBlockState(airProbe).isAir()) {
            throw new AssertionError("AMBIENT_PARTICLE_PROOF_UNARMED: the environment probe cell at "
                    + airProbe.toShortString() + " is not air, so it measures nothing");
        }
        for (int attempt = 0; attempt < ENVIRONMENT_PROBE_ATTEMPTS; attempt++) {
            long seed = attempt;
            List<ParticleSample> probe = captureAll("environment ambience probe " + attempt, () ->
                    driveDisplayTick(level, airProbe, seed));
            if (!probe.isEmpty()) {
                throw new AssertionError("AMBIENT_PARTICLE_ENVIRONMENT_CONTAMINATION: driving an air "
                        + "cell captured " + probe.size() + " particle(s) of environment ambience, "
                        + "which no fixture row could separate from a block's own emission; first="
                        + probe.getFirst());
            }
        }
    }

    /**
     * Per fixture: a direct block call is the vanilla baseline, the same seed driven through the
     * real display-tick path is production. X, Z and all three velocities must be bit-identical; a
     * flat fixture's Y must be bit-identical too, because the translation is a strict no-op at dy
     * zero; a lowered fixture's Y must be the baseline plus exactly the stored dy.
     */
    private static void assertFunnelAgainstVanilla(
            String label,
            ClientLevel level,
            BlockPos pos,
            double expectedDy,
            Set<ParticleType<?>> wanted,
            List<String> out) {
        BlockState state = level.getBlockState(pos);
        long seed = firstSeedThatEmits(label, level, pos, state);
        List<ParticleSample> vanilla = captureAll(label + " vanilla", () ->
                state.getBlock().animateTick(state, level, pos, RandomSource.create(seed)));
        List<ParticleSample> driven = captureAll(label + " display tick", () ->
                driveDisplayTick(level, pos, seed));
        assertOnlyExpectedTypes(label + " vanilla", vanilla, wanted);
        assertOnlyExpectedTypes(label + " display tick", driven, wanted);
        requireNonEmpty(label + " vanilla", vanilla);
        requireSameSize(label, vanilla, driven, seed);
        for (int index = 0; index < vanilla.size(); index++) {
            ParticleSample baseline = vanilla.get(index);
            ParticleSample production = driven.get(index);
            assertOnlyYMoved(label + "[" + index + "]", baseline, production);
            if (expectedDy == 0.0d) {
                if (Double.doubleToRawLongBits(baseline.y())
                        != Double.doubleToRawLongBits(production.y())) {
                    out.add(label + "[" + index + "] display tick altered a dy=0 emission: vanillaY="
                            + baseline.y() + " drivenY=" + production.y());
                }
            } else if (Math.abs(production.y() - (baseline.y() + expectedDy)) > EPSILON) {
                out.add(label + "[" + index + "] vanillaY=" + baseline.y()
                        + " drivenY=" + production.y()
                        + " expectedDrivenY=" + (baseline.y() + expectedDy));
            }
        }
    }

    /**
     * The ownership row. A lever's particle hook translates both its click route and its ambient
     * route, so the lowered lever's direct call is ALREADY shifted; driving the same lever through
     * the display tick must therefore reproduce it bit for bit. If the hook stopped declaring its
     * ownership, the display tick would translate the emission a second time and the second half of
     * this row goes red; if the hook stopped translating at all, the first half does.
     */
    private static void assertOwnedEmissionShiftedOnce(
            ClientLevel level, Fixtures fixtures, List<String> out) {
        BlockPos flatPos = fixtures.flatLever();
        BlockPos loweredPos = fixtures.loweredLever();
        BlockState flatState = level.getBlockState(flatPos);
        BlockState loweredState = level.getBlockState(loweredPos);
        long seed = firstSeedThatEmitsAtBoth("lever", level, flatPos, flatState, loweredPos, loweredState);

        List<ParticleSample> flatDirect = captureAll("lever flat vanilla", () ->
                flatState.getBlock().animateTick(flatState, level, flatPos, RandomSource.create(seed)));
        List<ParticleSample> loweredDirect = captureAll("lever lowered vanilla", () ->
                loweredState.getBlock().animateTick(
                        loweredState, level, loweredPos, RandomSource.create(seed)));
        List<ParticleSample> loweredDriven = captureAll("lever lowered display tick", () ->
                driveDisplayTick(level, loweredPos, seed));

        assertOnlyExpectedTypes("lever flat vanilla", flatDirect, LEVER_TYPES);
        assertOnlyExpectedTypes("lever lowered vanilla", loweredDirect, LEVER_TYPES);
        assertOnlyExpectedTypes("lever lowered display tick", loweredDriven, LEVER_TYPES);
        requireNonEmpty("lever flat vanilla", flatDirect);
        requireSameSize("lever flat vs lowered", flatDirect, loweredDirect, seed);
        requireSameSize("lever owned emission", loweredDirect, loweredDriven, seed);

        for (int index = 0; index < loweredDirect.size(); index++) {
            ParticleSample flatLocal = relativeToCell(flatPos, flatDirect.get(index));
            ParticleSample loweredLocal = relativeToCell(loweredPos, loweredDirect.get(index));
            if (Math.abs(loweredLocal.y() - (flatLocal.y() + LOWERED_DY)) > EPSILON) {
                out.add("lever owned hook[" + index + "] did not translate its own emission: flatY="
                        + flatLocal.y() + " loweredY=" + loweredLocal.y()
                        + " expectedLoweredY=" + (flatLocal.y() + LOWERED_DY));
            }
            ParticleSample owned = loweredDirect.get(index);
            ParticleSample driven = loweredDriven.get(index);
            assertOnlyYMoved("lever owned emission[" + index + "]", owned, driven);
            if (Double.doubleToRawLongBits(owned.y()) != Double.doubleToRawLongBits(driven.y())) {
                out.add("lever owned emission[" + index + "] moved twice: ownedY=" + owned.y()
                        + " drivenY=" + driven.y() + " (the display tick must stand down for an "
                        + "emission a per-block hook already translated)");
            }
        }
    }

    // ---------------------------------------------------------------- drive and capture

    private static void driveDisplayTick(ClientLevel level, BlockPos pos, long seed) {
        level.doAnimateTick(
                pos.getX(), pos.getY(), pos.getZ(), DRIVE_RANGE,
                RandomSource.create(seed), SENTINEL_BLOCK, new BlockPos.MutableBlockPos());
    }

    /**
     * Spreads probe seeds instead of walking consecutive small integers. The legacy random's FIRST
     * draw is an affine function of the seed's low bits, so for seeds 0..4095 that draw stays inside
     * a narrow band (about 0.58 to 0.95) and a first-draw gate such as the lever's 25% chance can
     * never open — the probe would then report "no emission" for a block that emits in play. Later
     * draws are well mixed, which is why gates on a later draw were never affected. A 64-bit mixing
     * step gives every candidate an unrelated first draw.
     */
    private static long spreadSeed(long index) {
        long z = index + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static long firstSeedThatEmits(
            String label, ClientLevel level, BlockPos pos, BlockState state) {
        for (long index = 0L; index < MAX_SEED; index++) {
            long candidate = spreadSeed(index);
            List<ParticleSample> emission = captureAll(label + " seed probe " + candidate, () ->
                    state.getBlock().animateTick(state, level, pos, RandomSource.create(candidate)));
            if (!emission.isEmpty()) {
                return candidate;
            }
        }
        throw new AssertionError("AMBIENT_PARTICLE_NO_EMISSION: " + label + " emitted nothing for " + MAX_SEED
                + " spread candidate seeds at " + pos.toShortString() + "; the row would measure nothing");
    }

    private static long firstSeedThatEmitsAtBoth(
            String label,
            ClientLevel level,
            BlockPos flatPos,
            BlockState flatState,
            BlockPos loweredPos,
            BlockState loweredState) {
        for (long index = 0L; index < MAX_SEED; index++) {
            long candidate = spreadSeed(index);
            List<ParticleSample> flat = captureAll(label + " flat seed probe " + candidate, () ->
                    flatState.getBlock().animateTick(
                            flatState, level, flatPos, RandomSource.create(candidate)));
            List<ParticleSample> lowered = captureAll(label + " lowered seed probe " + candidate, () ->
                    loweredState.getBlock().animateTick(
                            loweredState, level, loweredPos, RandomSource.create(candidate)));
            if (flat.size() != lowered.size()) {
                throw new AssertionError("AMBIENT_PARTICLE_RANDOM_DIVERGENCE: " + label + " emitted "
                        + flat.size() + " particle(s) flat and " + lowered.size() + " lowered for the "
                        + "same seed=" + candidate + "; the pair is not comparable");
            }
            if (!flat.isEmpty()) {
                return candidate;
            }
        }
        throw new AssertionError("AMBIENT_PARTICLE_NO_EMISSION: " + label + " emitted nothing for " + MAX_SEED
                + " spread candidate seeds; the row would measure nothing");
    }

    private static List<ParticleSample> captureAll(String label, Runnable emission) {
        if (ACTIVE_CAPTURE.get() != null) {
            throw new IllegalStateException("ambient particle capture already active during " + label);
        }
        ACTIVE_CAPTURE.set(new ArrayList<>());
        try {
            emission.run();
            return List.copyOf(ACTIVE_CAPTURE.get());
        } finally {
            ACTIVE_CAPTURE.remove();
        }
    }

    // ---------------------------------------------------------------- assertions

    /**
     * A foreign particle type is a hard failure, never a silent filter: dropping it would let an
     * unrelated emission hide inside a passing row.
     */
    private static void assertOnlyExpectedTypes(
            String label, List<ParticleSample> samples, Set<ParticleType<?>> wanted) {
        for (ParticleSample sample : samples) {
            if (!wanted.contains(sample.options().getType())) {
                throw new AssertionError("AMBIENT_PARTICLE_FOREIGN_TYPE: " + label
                        + " captured an unexpected particle: " + sample);
            }
        }
    }

    private static void assertOnlyYMoved(String label, ParticleSample baseline, ParticleSample driven) {
        if (baseline.options().getType() != driven.options().getType()
                || Double.doubleToRawLongBits(baseline.x()) != Double.doubleToRawLongBits(driven.x())
                || Double.doubleToRawLongBits(baseline.z()) != Double.doubleToRawLongBits(driven.z())
                || Double.doubleToRawLongBits(baseline.xVelocity())
                        != Double.doubleToRawLongBits(driven.xVelocity())
                || Double.doubleToRawLongBits(baseline.yVelocity())
                        != Double.doubleToRawLongBits(driven.yVelocity())
                || Double.doubleToRawLongBits(baseline.zVelocity())
                        != Double.doubleToRawLongBits(driven.zVelocity())) {
            throw new AssertionError("AMBIENT_PARTICLE_NOT_ONLY_Y: " + label
                    + " changed an observable other than the vertical anchor translation; vanilla="
                    + baseline + " driven=" + driven);
        }
    }

    private static void requireNonEmpty(String label, List<ParticleSample> samples) {
        if (samples.isEmpty()) {
            throw new AssertionError("AMBIENT_PARTICLE_EMPTY_ROW: " + label + " captured no particle, "
                    + "so the row proves nothing");
        }
    }

    private static void requireSameSize(
            String label, List<ParticleSample> baseline, List<ParticleSample> driven, long seed) {
        if (baseline.size() != driven.size()) {
            throw new AssertionError("AMBIENT_PARTICLE_COUNT_MISMATCH: " + label + " emitted "
                    + baseline.size() + " particle(s) on the vanilla route and " + driven.size()
                    + " on the production route for seed=" + seed);
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

    private record Fixtures(
            BlockPos flatEndRod,
            BlockPos loweredEndRod,
            BlockPos flatTorch,
            BlockPos loweredTorch,
            BlockPos flatEnderChest,
            BlockPos loweredEnderChest,
            BlockPos flatLever,
            BlockPos loweredLever,
            BlockPos environmentProbe) {
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
