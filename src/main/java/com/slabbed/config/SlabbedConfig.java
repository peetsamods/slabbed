package com.slabbed.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slabbed.Slabbed;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The mod's persistent settings: one JSON file at {@code config/slabbed.json}, read once at mod
 * init and written back whenever a setting changes.
 *
 * <p><b>Every default here must reproduce the behaviour of a jar that shipped without this file.</b>
 * A default that changes behaviour turns "the player configured nothing" into a silent feature flip.
 *
 * <p><b>This config is PLACEMENT-TIME ONLY.</b> Nothing on a read path may consult it: a placed
 * block's height is its stored fact for its whole life, and flipping an option must not move it
 * (LAW.md, LAW 1). The single production consumer is
 * {@code com.slabbed.placement.LandingResolver#potSeatAdjustedDy}, which both writers of a
 * placement transaction call and no read path calls.
 *
 * <p>Lives in {@code src/main} rather than {@code src/client} because the mint that consults it runs
 * on the logical server.
 */
public final class SlabbedConfig {

    /** Seat policy for {@code minecraft:flower_pot} ONLY (maintainer ruling, 2026-09-06). */
    public enum PotSeat {
        /** Slabbed's own behaviour: the pot seats flush on the surface it was placed against. */
        FLUSH,
        /** Vanilla's gap: the pot sits one vanilla gap above its support's visible top plane. */
        VANILLA_FLOAT
    }

    /**
     * The production literal a shipped-default row asserts against. Named for the same reason
     * {@code SlabAnchorAttachment.FROZEN_DY_SHIPPED_DEFAULT} is: a row that re-derives the
     * initializer expression proves only that it can read its own literal.
     */
    public static final String POT_SEAT_SHIPPED_DEFAULT = "FLUSH";

    /** Bumped only when the on-disk shape changes incompatibly; any other value falls back to defaults. */
    public static final int FORMAT_VERSION = 1;

    private static volatile SlabbedConfig active = defaults();

    private final PotSeat potSeat;

    private SlabbedConfig(PotSeat potSeat) {
        this.potSeat = potSeat;
    }

    /** The live config. Placement-time reads only — never a height read (LAW.md, LAW 1). */
    public static SlabbedConfig get() {
        return active;
    }

    public PotSeat potSeat() {
        return potSeat;
    }

    /** Public factory: the settings screen and the tests build a value without touching the ctor. */
    public static SlabbedConfig withPotSeat(PotSeat seat) {
        return new SlabbedConfig(seat == null ? defaults().potSeat() : seat);
    }

    public static SlabbedConfig defaults() {
        return new SlabbedConfig(PotSeat.valueOf(POT_SEAT_SHIPPED_DEFAULT));
    }

    /**
     * {@code config/slabbed.json}. Package-visible so the file rows can assert its absence without
     * duplicating the path.
     */
    static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("slabbed.json");
    }

    /**
     * Called ONCE from {@code Slabbed.onInitialize}. Never from a static initializer: class-load must
     * not touch disk.
     */
    public static void init() {
        active = readFrom(configFile());
    }

    /**
     * Missing, unreadable, malformed, or an unknown {@code formatVersion} all resolve to the defaults
     * plus exactly one log line. Catching {@code RuntimeException} as well as {@code IOException} is
     * load-bearing: an unknown enum name reaches {@code Enum.valueOf}, which throws
     * {@code IllegalArgumentException}, and a corrupt file reaches the parser, which throws
     * {@code JsonSyntaxException} — neither is an {@code IOException}.
     */
    static SlabbedConfig readFrom(Path file) {
        if (file == null || !Files.exists(file)) {
            return defaults();
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            int version = root.has("formatVersion") ? root.get("formatVersion").getAsInt() : 0;
            if (version != FORMAT_VERSION) {
                Slabbed.LOGGER.info("[slabbed] config formatVersion {} is not {}; using defaults",
                        version, FORMAT_VERSION);
                return defaults();
            }
            return new SlabbedConfig(PotSeat.valueOf(root.get("potSeat").getAsString()));
        } catch (IOException | RuntimeException exception) {
            Slabbed.LOGGER.warn("[slabbed] config unreadable; using defaults", exception);
            return defaults();
        }
    }

    /** Replaces the active config and persists it. Best-effort write; a failure must not crash. */
    public static void apply(SlabbedConfig next) {
        if (next == null) {
            return;
        }
        active = next;
        writeTo(configFile(), next);
    }

    static void writeTo(Path file, SlabbedConfig config) {
        if (file == null || config == null) {
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonObject root = new JsonObject();
            root.addProperty("formatVersion", FORMAT_VERSION);
            root.addProperty("potSeat", config.potSeat.name());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException | RuntimeException exception) {
            Slabbed.LOGGER.warn("[slabbed] config write failed", exception);
        }
    }

    /**
     * TEST SEAM, same house pattern as {@code LandingResolver.compatFinalStateTestOverride}: set it,
     * run the body, restore it in a {@code finally}.
     *
     * <p>It is a STATIC GLOBAL. The game-test runner starts several tests in one tick, so a body that
     * spans a tick would leak the override into a concurrently running row. Synchronous bodies only.
     *
     * @return the config that was active before the call, to restore.
     */
    public static SlabbedConfig setActiveForTesting(SlabbedConfig next) {
        SlabbedConfig previous = active;
        active = next == null ? defaults() : next;
        return previous;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SlabbedConfig config && config.potSeat == potSeat;
    }

    @Override
    public int hashCode() {
        return potSeat.hashCode();
    }

    @Override
    public String toString() {
        return "SlabbedConfig[potSeat=" + potSeat + "]";
    }
}
