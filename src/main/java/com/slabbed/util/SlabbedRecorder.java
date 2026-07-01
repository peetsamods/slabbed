package com.slabbed.util;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Dev-only append-only text log for manual live-testing sessions. Replaces "take a
 * screen recording" with a plain, greppable trail of what the crosshair targeted and
 * what placements/removals happened, so a live bug can be diagnosed from a log file
 * instead of a video.
 *
 * <p>Toggled in-game via {@code /slabdy record}. Writes one line per event to
 * {@code slabbed-recorder/session-<timestamp>.log} under the current working
 * directory (the dev run/ dir, or the profile root for a packaged jar). Common code
 * (no client imports) so the action-log call sites in server-side placement/anchor
 * code can call it directly; the target/aim log call site lives in the client-only
 * {@code TargetDyOverlay}.
 */
public final class SlabbedRecorder {
    private static volatile boolean enabled;
    private static volatile Path logPath;

    // Automatic WYSIWYG placement-position check. Set every time the client logs a
    // meaningful target-row change; consumed by the next server-side place event.
    private static volatile BlockPos lastTargetPos;
    private static volatile BlockPos lastExpectedPlacePos;
    private static volatile Direction lastExpectedFace;
    private static volatile String lastExpectedHalf;
    private static volatile Instant lastTargetAt;
    private static final Duration EXPECTATION_TIMEOUT = Duration.ofSeconds(3);

    private SlabbedRecorder() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static Path currentLogPath() {
        return logPath;
    }

    /**
     * Flips the recorder on/off. Turning it on opens a fresh timestamped log file.
     * Returns the new enabled state.
     */
    public static synchronized boolean toggle() {
        lastTargetPos = null;
        lastExpectedPlacePos = null;
        lastExpectedFace = null;
        lastExpectedHalf = null;
        lastTargetAt = null;
        if (enabled) {
            enabled = false;
            return false;
        }
        Path dir = Path.of("slabbed-recorder");
        Path path = dir.resolve("session-" + Instant.now().toString().replace(':', '-') + ".log");
        try {
            Files.createDirectories(dir);
            Files.writeString(path, "", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            return false;
        }
        logPath = path;
        enabled = true;
        return true;
    }

    /**
     * Client-side: records the most recent aim's target cell, the visible half aimed
     * at, the clicked face, and where a placement is naively expected to land (target
     * cell relative to the clicked face). Consumed by {@link #checkPlacement} to
     * auto-detect both WYSIWYG placement-position violations and slab-type violations.
     */
    public static void noteTarget(BlockPos targetPos, BlockPos expectedPlacePos, Direction face, String half) {
        if (!enabled) {
            return;
        }
        lastTargetPos = targetPos;
        lastExpectedPlacePos = expectedPlacePos;
        lastExpectedFace = face;
        lastExpectedHalf = half;
        lastTargetAt = Instant.now();
    }

    /**
     * Server-side: compares an actual placement against the most recently noted target
     * expectation and logs one of three position outcomes: "match" (landed where the
     * crosshair predicted), "combine-in-place" (landed on the clicked block's OWN cell --
     * vanilla's canBeReplaced/replaceClicked slab-combine semantics), or "MISMATCH"
     * (landed somewhere else entirely -- a genuine WYSIWYG position violation). On a
     * "match" against a horizontal-face slab placement, ALSO checks the resulting slab's
     * TYPE against what the visible half implies (UPPER->TOP, LOWER->BOTTOM) and logs a
     * separate "TYPE-MISMATCH" if they disagree -- this is the check that would have
     * caught the SlabBlock.canBeReplaced dy-blindness bug automatically instead of it
     * hiding behind a confidently-labeled "combine-in-place" line.
     *
     * <p>Deliberately does NOT clear the noted expectation after one use: two placements
     * against the same still-aimed target (e.g. a fast double click before the client's
     * next target-row tick) should both be checked against the same expectation rather
     * than the second one silently getting no verdict at all. The timeout below is what
     * bounds staleness, not single-consumption.
     */
    public static void checkPlacement(BlockPos actualPos, BlockState actualState) {
        if (!enabled) {
            return;
        }
        BlockPos expected = lastExpectedPlacePos;
        BlockPos targetPos = lastTargetPos;
        Direction face = lastExpectedFace;
        String half = lastExpectedHalf;
        Instant notedAt = lastTargetAt;
        if (expected == null || notedAt == null || Duration.between(notedAt, Instant.now()).compareTo(EXPECTATION_TIMEOUT) > 0) {
            return;
        }
        if (actualPos.equals(expected)) {
            log("wysiwyg", "match pos=" + actualPos.toShortString());
            checkSlabTypeConsistency(actualPos, actualState, face, half);
        } else if (targetPos != null && actualPos.equals(targetPos)) {
            log("wysiwyg", "combine-in-place targetPos=" + targetPos.toShortString()
                    + " expectedPlace=" + expected.toShortString()
                    + " (vanilla canBeReplaced semantics -- verify this was actually intended,"
                    + " not a mistaken merge)");
        } else {
            log("wysiwyg", "MISMATCH expectedPlace=" + expected.toShortString()
                    + " actualPos=" + actualPos.toShortString()
                    + " targetPos=" + (targetPos == null ? "?" : targetPos.toShortString()));
        }
    }

    /**
     * Flags a placed slab whose TYPE disagrees with the visible half the crosshair aimed
     * at on a horizontal face (UPPER should yield TOP, LOWER should yield BOTTOM). Skips
     * vertical faces (governed by different vanilla rules, out of this check's scope) and
     * DOUBLE results (a deliberate combine, not a fresh half-driven type choice).
     */
    private static void checkSlabTypeConsistency(BlockPos pos, BlockState state, Direction face, String half) {
        if (face == null || half == null || !face.getAxis().isHorizontal()) {
            return;
        }
        if (!(state.getBlock() instanceof SlabBlock) || !state.hasProperty(SlabBlock.TYPE)) {
            return;
        }
        SlabType actual = state.getValue(SlabBlock.TYPE);
        if (actual == SlabType.DOUBLE) {
            return;
        }
        SlabType expectedType = "UPPER".equals(half) ? SlabType.TOP : "LOWER".equals(half) ? SlabType.BOTTOM : null;
        if (expectedType != null && actual != expectedType) {
            log("wysiwyg", "TYPE-MISMATCH pos=" + pos.toShortString()
                    + " expectedType=" + expectedType + " actualType=" + actual
                    + " half=" + half + " face=" + face.getName());
        }
    }

    /** Appends one line, silently no-op if disabled or on I/O failure (dev tool, never crash play). */
    public static void log(String tag, String body) {
        if (!enabled || logPath == null) {
            return;
        }
        String line = "[" + Instant.now() + "] [" + tag + "] " + body + System.lineSeparator();
        try (Writer writer = Files.newBufferedWriter(
                logPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(line);
        } catch (IOException e) {
            // Dev tool only; never let logging failures affect gameplay.
        }
    }
}
