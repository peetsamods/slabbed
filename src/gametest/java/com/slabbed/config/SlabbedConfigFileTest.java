package com.slabbed.config;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * The config file's own contract. In-package (com.slabbed.config) so it reaches
 * {@link SlabbedConfig}'s package-private file API directly — the same reason
 * {@code BetaNoticeDismissedWorldsTest} lives in {@code com.slabbed.client}.
 *
 * <p><b>No row here writes {@link SlabbedConfig#configFile()}.</b> The shipped-default row's premise
 * is that the real file is ABSENT in an automated venue, so every round-trip row uses a temp path.
 * {@code BetaNoticeDismissedWorldsTest} does write the real config directory; that is the precedent
 * deliberately NOT copied here, because copying it would poison the default row order-dependently.
 */
public final class SlabbedConfigFileTest {

    private static Path tempConfigFile() throws IOException {
        return Files.createTempDirectory("slabbed-config-row").resolve("slabbed.json");
    }

    private static void deleteTree(Path file) {
        if (file == null) {
            return;
        }
        Path root = file.getParent();
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort: a leaked temp directory must not fail a row about config parsing.
                }
            });
        } catch (IOException ignored) {
            // Same.
        }
    }

    /**
     * The SHIPPED default of the flower-pot seat is FLUSH — the behaviour of a jar that shipped
     * before this setting existed.
     *
     * <p>Three clauses. (A) premise: the real config file must be ABSENT in this venue, so the row is
     * measuring the jar and not a file some other row wrote. (B) the PRODUCTION literal
     * {@link SlabbedConfig#POT_SEAT_SHIPPED_DEFAULT} parses to {@code FLUSH}. (C) drift:
     * {@link SlabbedConfig#defaults()} agrees with that literal. Clause C deliberately reads
     * {@code defaults()} and NOT {@code get()}: {@code get()} is a mutable static that a concurrently
     * running row may have overridden inside its own body, and reading it here would make this row
     * flaky rather than strict.
     *
     * <p>MUTATION that must redden this row alone: flip {@code POT_SEAT_SHIPPED_DEFAULT} to
     * {@code "VANILLA_FLOAT"}. It is isolated only because every other pot-placing row pins the
     * option explicitly.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void shippedPotSeatDefaultIsFlush(GameTestHelper helper) {
        Path real = SlabbedConfig.configFile();
        if (real != null && Files.exists(real)) {
            throw helper.assertionException("premise drift: this venue now has a real config file at "
                    + real.getFileName() + ", so this row would measure the venue instead of the jar. "
                    + "Find the row that writes it and give it a temp path.");
        }

        SlabbedConfig.PotSeat shipped = SlabbedConfig.PotSeat.valueOf(SlabbedConfig.POT_SEAT_SHIPPED_DEFAULT);
        if (shipped != SlabbedConfig.PotSeat.FLUSH) {
            throw helper.assertionException("the shipped flower-pot seat must be FLUSH, but the production "
                    + "literal SlabbedConfig.POT_SEAT_SHIPPED_DEFAULT is \""
                    + SlabbedConfig.POT_SEAT_SHIPPED_DEFAULT + "\". Shipping the other value changes where "
                    + "pots land for every player who configured nothing.");
        }
        if (SlabbedConfig.defaults().potSeat() != shipped) {
            throw helper.assertionException("SlabbedConfig.defaults() answers "
                    + SlabbedConfig.defaults().potSeat() + " but the shipped literal says " + shipped
                    + " — the default and the literal have drifted apart.");
        }
        helper.succeed();
    }

    /**
     * A written config reads back as the same value, in BOTH directions so the row cannot pass by
     * hardcoding one of them.
     *
     * <p>MUTATION: drop the {@code potSeat} property from {@code writeTo} (or ignore it in
     * {@code readFrom}). No other row round-trips through disk.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void configRoundTripsThroughDisk(GameTestHelper helper) {
        Path file = null;
        try {
            file = tempConfigFile();
            for (SlabbedConfig.PotSeat seat : SlabbedConfig.PotSeat.values()) {
                SlabbedConfig.writeTo(file, SlabbedConfig.withPotSeat(seat));
                SlabbedConfig loaded = SlabbedConfig.readFrom(file);
                if (loaded.potSeat() != seat) {
                    throw helper.assertionException("a config written with potSeat=" + seat
                            + " read back as " + loaded.potSeat() + "; the setting does not survive disk");
                }
                if (!loaded.equals(SlabbedConfig.withPotSeat(seat))) {
                    throw helper.assertionException("the round-tripped config is not equal to the value "
                            + "written: wrote " + SlabbedConfig.withPotSeat(seat) + ", read " + loaded);
                }
            }
        } catch (IOException exception) {
            throw helper.assertionException("could not create the temp config file: " + exception);
        } finally {
            deleteTree(file);
        }
        helper.succeed();
    }

    /**
     * A corrupt config falls back to the defaults and lets nothing escape.
     *
     * <p>Two arms, because they exercise two different catch clauses: (a) syntactically invalid JSON,
     * (b) well-formed JSON whose {@code potSeat} is not a known value — that one reaches
     * {@code Enum.valueOf}, which throws {@code IllegalArgumentException}, and is precisely the arm a
     * {@code catch (IOException)} alone would miss.
     *
     * <p>MUTATION: narrow {@code readFrom}'s catch to {@code IOException}. Nothing else parses config
     * JSON.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void corruptConfigFallsBackToDefaultsWithoutThrowing(GameTestHelper helper) {
        Path file = null;
        try {
            file = tempConfigFile();
            Files.createDirectories(file.getParent());

            Files.writeString(file, "{ this is not json");
            SlabbedConfig broken = SlabbedConfig.readFrom(file);
            if (!broken.equals(SlabbedConfig.defaults())) {
                throw helper.assertionException("unparseable JSON must load as the defaults, got " + broken);
            }

            Files.writeString(file,
                    "{\"formatVersion\": " + SlabbedConfig.FORMAT_VERSION + ", \"potSeat\": \"NOT_A_VALUE\"}");
            SlabbedConfig unknownValue = SlabbedConfig.readFrom(file);
            if (!unknownValue.equals(SlabbedConfig.defaults())) {
                throw helper.assertionException("a well-formed config naming an unknown potSeat must load as "
                        + "the defaults, got " + unknownValue
                        + " — this is the arm a catch(IOException) alone would miss");
            }
        } catch (IOException exception) {
            throw helper.assertionException("could not write the temp config file: " + exception);
        } finally {
            deleteTree(file);
        }
        helper.succeed();
    }

    /**
     * The {@code formatVersion} field is load-bearing: a file from a shape this build does not
     * understand loads as the defaults rather than being half-read.
     *
     * <p>MUTATION: delete the version comparison from {@code readFrom}.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unknownFormatVersionFallsBackToDefaults(GameTestHelper helper) {
        Path file = null;
        try {
            file = tempConfigFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, "{\"formatVersion\": " + (SlabbedConfig.FORMAT_VERSION + 1)
                    + ", \"potSeat\": \"VANILLA_FLOAT\"}");
            SlabbedConfig loaded = SlabbedConfig.readFrom(file);
            if (!loaded.equals(SlabbedConfig.defaults())) {
                throw helper.assertionException("a config whose formatVersion this build does not know must "
                        + "load as the defaults, got " + loaded);
            }
        } catch (IOException exception) {
            throw helper.assertionException("could not write the temp config file: " + exception);
        } finally {
            deleteTree(file);
        }
        helper.succeed();
    }
}
