package com.slabbed.util;

import com.slabbed.Slabbed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class LiveCursorIntentRecorder {
    public static final String ENABLE_PROPERTY = "slabbed.liveCursorIntentRecorder";
    public static final String DIR_PROPERTY = "slabbed.liveCursorIntentRecorderDir";

    private static final Object LOCK = new Object();
    private static final AtomicLong ROW_IDS = new AtomicLong();
    private static Path sessionDir;
    private static boolean startLogged;
    private static long cursorRows;
    private static long actionRows;
    private static long ghostSurfaceRows;
    private static long hiddenOwnerRows;
    private static long outlineRaycastSplitRows;
    private static long placementExpectedDyMismatchRows;
    private static long loweredSideSlabPlacementVanillaDyRows;
    private static long greenCursorTriadRows;
    private static long greenPlacementAuthoringRows;

    private LiveCursorIntentRecorder() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    public static long lastCursorRowId() {
        return Math.max(0L, ROW_IDS.get());
    }

    public static void recordCursor(LinkedHashMap<String, String> fields) {
        if (!enabled()) {
            return;
        }
        LinkedHashMap<String, String> row = copy(fields);
        row.putIfAbsent("type", "cursor");
        row.putIfAbsent("rowId", Long.toString(ROW_IDS.incrementAndGet()));
        row.putIfAbsent("recordedAt", Instant.now().toString());
        String markers = cursorMarkers(row);
        row.put("mismatchMarker", markers);
        synchronized (LOCK) {
            cursorRows++;
            if (markers.contains("LIVE_CURSOR_GHOST_SURFACE")) {
                ghostSurfaceRows++;
            }
            if (markers.contains("LIVE_CURSOR_HIDDEN_OWNER")) {
                hiddenOwnerRows++;
            }
            if (markers.contains("LIVE_CURSOR_OUTLINE_RAYCAST_SPLIT")) {
                outlineRaycastSplitRows++;
            }
            if ("LIVE_GREEN_CURSOR_TRIAD".equals(markers)) {
                greenCursorTriadRows++;
            }
            writeSession(row);
            writeMismatchRows(row, markers);
            writeSummary();
        }
    }

    public static void recordAction(LinkedHashMap<String, String> fields) {
        if (!enabled()) {
            return;
        }
        LinkedHashMap<String, String> row = copy(fields);
        row.putIfAbsent("type", "action");
        long cursorRowId = lastCursorRowId();
        row.putIfAbsent("actionId", Long.toString(ROW_IDS.incrementAndGet()));
        row.putIfAbsent("cursorRowId", Long.toString(cursorRowId));
        row.putIfAbsent("recordedAt", Instant.now().toString());
        appendActionExpectations(row);
        String markers = actionMarkers(row);
        row.put("marker", markers);
        synchronized (LOCK) {
            actionRows++;
            if (markers.contains("LIVE_PLACEMENT_EXPECTED_DY_MISMATCH")) {
                placementExpectedDyMismatchRows++;
            }
            if (markers.contains("LIVE_PLACEMENT_VANILLA_DY_FROM_LOWERED_OWNER")) {
                loweredSideSlabPlacementVanillaDyRows++;
            }
            if ("LIVE_GREEN_PLACEMENT_AUTHORING".equals(markers)) {
                greenPlacementAuthoringRows++;
            }
            writeSession(row);
            writeActionTsv(row);
            writeMismatchRows(row, markers);
            writeSummary();
        }
    }

    public static void flushSummaryForTests() {
        if (!enabled()) {
            return;
        }
        synchronized (LOCK) {
            writeSummary();
        }
    }

    public static void resetForTests() {
        synchronized (LOCK) {
            sessionDir = null;
            startLogged = false;
            ROW_IDS.set(0L);
            cursorRows = 0L;
            actionRows = 0L;
            ghostSurfaceRows = 0L;
            hiddenOwnerRows = 0L;
            outlineRaycastSplitRows = 0L;
            placementExpectedDyMismatchRows = 0L;
            loweredSideSlabPlacementVanillaDyRows = 0L;
            greenCursorTriadRows = 0L;
            greenPlacementAuthoringRows = 0L;
        }
    }

    private static LinkedHashMap<String, String> copy(LinkedHashMap<String, String> fields) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (fields != null) {
            out.putAll(fields);
        }
        return out;
    }

    private static String cursorMarkers(Map<String, String> row) {
        boolean outlineHit = isHit(row.get("finalOutlineReplayHit"));
        boolean raycastHit = isHit(row.get("finalRaycastReplayHit"));
        boolean lawfulLowered = isLawfulLoweredLane(row.get("finalOwnerLaneKind"));
        StringBuilder markers = new StringBuilder();
        appendMarker(markers, outlineHit && !raycastHit, "LIVE_CURSOR_OUTLINE_RAYCAST_SPLIT");
        appendMarker(markers, outlineHit && !raycastHit && lawfulLowered, "LIVE_CURSOR_GHOST_SURFACE");
        appendMarker(markers, Boolean.parseBoolean(row.getOrDefault("hiddenOwner", "false")), "LIVE_CURSOR_HIDDEN_OWNER");
        if (markers.isEmpty() && "BLOCK".equals(row.get("finalHitType")) && outlineHit && raycastHit) {
            markers.append("LIVE_GREEN_CURSOR_TRIAD");
        }
        return markers.isEmpty() ? "none" : markers.toString();
    }

    private static void appendActionExpectations(LinkedHashMap<String, String> row) {
        boolean slabHeld = "minecraft:stone_slab".equals(row.get("heldItem"))
                || "block.minecraft.stone_slab".equals(row.get("heldItem"));
        boolean horizontal = isHorizontalFace(row.get("clickedFace"));
        boolean loweredOwner = isLawfulLoweredLane(row.get("clickedOwnerLaneKind"));
        if (slabHeld && horizontal && loweredOwner) {
            row.putIfAbsent("expectedAfterDy", "-0.500000");
            row.putIfAbsent("expectedAfterLaneKind", "persistent_lowered_slab_carrier");
            row.putIfAbsent("expectedResult", "lowered_side_lane_continuation");
        } else if (row.getOrDefault("clickedOwnerLaneKind", "").contains("unnamed")
                || row.getOrDefault("clickedOwnerLaneKind", "").contains("vanilla")) {
            row.putIfAbsent("expectedAfterDy", "0.000000");
            row.putIfAbsent("expectedAfterLaneKind", "none");
            row.putIfAbsent("expectedResult", "vanilla_dy0");
        } else {
            row.putIfAbsent("expectedAfterDy", "unknown");
            row.putIfAbsent("expectedAfterLaneKind", "unknown");
            row.putIfAbsent("expectedResult", "unknown");
        }
    }

    private static String actionMarkers(Map<String, String> row) {
        String expectedDy = row.getOrDefault("expectedAfterDy", "unknown");
        String afterDy = row.getOrDefault("afterDy", "unknown");
        String afterLane = row.getOrDefault("afterLaneKind", "unknown");
        boolean loweredExpected = "-0.500000".equals(expectedDy);
        boolean dyMismatch = loweredExpected && !sameDy(expectedDy, afterDy);
        boolean laneMismatch = loweredExpected
                && !"persistent_lowered_slab_carrier".equals(afterLane);
        StringBuilder markers = new StringBuilder();
        appendMarker(markers, Boolean.parseBoolean(row.getOrDefault("hiddenOwner", "false")),
                "LIVE_PLACEMENT_HIDDEN_OWNER");
        appendMarker(markers, dyMismatch || laneMismatch, "LIVE_PLACEMENT_EXPECTED_DY_MISMATCH");
        appendMarker(markers, loweredExpected && sameDy("0.000000", afterDy),
                "LIVE_PLACEMENT_VANILLA_DY_FROM_LOWERED_OWNER");
        if (markers.isEmpty() && loweredExpected) {
            markers.append("LIVE_GREEN_PLACEMENT_AUTHORING");
        }
        return markers.isEmpty() ? "none" : markers.toString();
    }

    private static boolean isLawfulLoweredLane(String lane) {
        return "persistent_lowered_slab_carrier".equals(lane)
                || "compound_visible_side_lower_slab".equals(lane)
                || "compound_visible_side_upper_slab".equals(lane)
                || "compound_visible_side_double_slab".equals(lane)
                || "compound_visible_owner_top_slab".equals(lane)
                || "anchored_full_block".equals(lane);
    }

    private static boolean isHorizontalFace(String face) {
        return "NORTH".equals(face) || "SOUTH".equals(face) || "EAST".equals(face) || "WEST".equals(face)
                || "north".equals(face) || "south".equals(face) || "east".equals(face) || "west".equals(face);
    }

    private static boolean isHit(String value) {
        return value != null && value.startsWith("hit");
    }

    private static boolean sameDy(String expected, String actual) {
        try {
            return Math.abs(Double.parseDouble(expected) - Double.parseDouble(actual)) <= 1.0e-6d;
        } catch (NumberFormatException ignored) {
            return expected.equals(actual);
        }
    }

    private static void appendMarker(StringBuilder markers, boolean condition, String marker) {
        if (!condition) {
            return;
        }
        if (!markers.isEmpty()) {
            markers.append('|');
        }
        markers.append(marker);
    }

    private static void writeSession(LinkedHashMap<String, String> row) {
        appendLine("session.jsonl", toJson(row));
    }

    private static void writeActionTsv(LinkedHashMap<String, String> row) {
        appendLine("actions.tsv",
                tsv(row.get("actionId"))
                        + '\t' + tsv(row.get("cursorRowId"))
                        + '\t' + tsv(row.get("actionType"))
                        + '\t' + tsv(row.get("heldItem"))
                        + '\t' + tsv(row.get("clickedOwnerPos"))
                        + '\t' + tsv(row.get("clickedFace"))
                        + '\t' + tsv(row.get("placementPos"))
                        + '\t' + tsv(row.get("expectedAfterDy"))
                        + '\t' + tsv(row.get("afterDy"))
                        + '\t' + tsv(row.get("expectedAfterLaneKind"))
                        + '\t' + tsv(row.get("afterLaneKind"))
                        + '\t' + tsv(row.get("marker")));
    }

    private static void writeMismatchRows(LinkedHashMap<String, String> row, String markers) {
        if (markers == null || markers.equals("none") || markers.startsWith("LIVE_GREEN_")) {
            return;
        }
        appendLine("mismatches.tsv",
                tsv(row.getOrDefault("type", "unknown"))
                        + '\t' + tsv(row.getOrDefault("rowId", row.getOrDefault("actionId", "unknown")))
                        + '\t' + tsv(markers)
                        + '\t' + tsv(row.getOrDefault("finalHitPos", row.getOrDefault("clickedOwnerPos", "none")))
                        + '\t' + tsv(row.getOrDefault("heldItem", "none")));
    }

    private static void writeSummary() {
        StringBuilder text = new StringBuilder();
        text.append("# Slabbed Live Cursor Intent Recorder Summary\n\n");
        text.append("cursorRows=").append(cursorRows).append('\n');
        text.append("actionRows=").append(actionRows).append('\n');
        text.append("ghostSurfaceRows=").append(ghostSurfaceRows).append('\n');
        text.append("hiddenOwnerRows=").append(hiddenOwnerRows).append('\n');
        text.append("outlineRaycastSplitRows=").append(outlineRaycastSplitRows).append('\n');
        text.append("placementExpectedDyMismatchRows=").append(placementExpectedDyMismatchRows).append('\n');
        text.append("loweredSideSlabPlacementVanillaDyRows=")
                .append(loweredSideSlabPlacementVanillaDyRows).append('\n');
        text.append("liveGreenCursorTriadRows=").append(greenCursorTriadRows).append('\n');
        text.append("liveGreenPlacementRows=").append(greenPlacementAuthoringRows).append('\n');
        writeFile("summary.md", text.toString(), false);
    }

    private static void appendLine(String fileName, String line) {
        writeFile(fileName, line + System.lineSeparator(), true);
    }

    private static void writeFile(String fileName, String text, boolean append) {
        try {
            Path dir = sessionDir();
            Path path = dir.resolve(fileName);
            if (append) {
                Files.writeString(path, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(path, text, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            Slabbed.LOGGER.warn("[LIVE_CURSOR_INTENT_RECORDER_IO_ERROR] file={} error={}", fileName, e.toString());
        }
    }

    private static Path sessionDir() throws IOException {
        if (sessionDir == null) {
            String configured = System.getProperty(DIR_PROPERTY, "run/live-cursor-recorder");
            sessionDir = Path.of(configured);
            Files.createDirectories(sessionDir);
            if (!startLogged) {
                startLogged = true;
                Slabbed.LOGGER.info("[LIVE_CURSOR_INTENT_RECORDER_START] enabled=true dir={}",
                        sessionDir.toAbsolutePath());
            }
            writeHeaderIfMissing(sessionDir.resolve("actions.tsv"),
                    "actionId\tcursorRowId\tactionType\theldItem\tclickedOwnerPos\tclickedFace\tplacementPos"
                            + "\texpectedAfterDy\tafterDy\texpectedAfterLaneKind\tafterLaneKind\tmarker\n");
            writeHeaderIfMissing(sessionDir.resolve("mismatches.tsv"),
                    "type\trowOrActionId\tmarker\tpos\theldItem\n");
        }
        return sessionDir;
    }

    private static void writeHeaderIfMissing(Path path, String header) throws IOException {
        if (!Files.exists(path) || Files.size(path) == 0L) {
            Files.writeString(path, header, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    private static String toJson(LinkedHashMap<String, String> row) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : row.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escapeJson(entry.getKey())).append('"');
            json.append(':');
            json.append('"').append(escapeJson(entry.getValue())).append('"');
        }
        json.append('}');
        return json.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "null";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String tsv(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
