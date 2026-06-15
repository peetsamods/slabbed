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
    private static long renderedOutlineRows;
    private static long renderedOutlineLargeBoundsRows;
    private static long renderedOutlineReplayBoundsSplitRows;
    private static long renderedOutlineTargetSplitRows;
    private static long placementExpectedDyMismatchRows;
    private static long loweredSideSlabPlacementVanillaDyRows;
    private static long collisionIteratorTargetMissRows;
    private static long collisionIteratorTargetPresentRows;
    private static long greenCursorTriadRows;
    private static long greenPlacementAuthoringRows;
    private static long lastCursorRowId;
    private static LinkedHashMap<String, String> lastCursorRow;

    private LiveCursorIntentRecorder() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    public static long lastCursorRowId() {
        return Math.max(0L, lastCursorRowId);
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
            if (markers.contains("LIVE_COLLISION_ITERATOR_TARGET_MISS")) {
                collisionIteratorTargetMissRows++;
            }
            if (Boolean.parseBoolean(row.getOrDefault("playerBlockCollisionTargetIntersectsReturned", "false"))) {
                collisionIteratorTargetPresentRows++;
            }
            if ("LIVE_GREEN_CURSOR_TRIAD".equals(markers)) {
                greenCursorTriadRows++;
            }
            lastCursorRowId = parseLong(row.get("rowId"), lastCursorRowId);
            lastCursorRow = copy(row);
            writeSession(row);
            writeMismatchRows(row, markers);
            writeSummary();
        }
    }

    public static void recordRenderedOutline(LinkedHashMap<String, String> fields) {
        if (!enabled()) {
            return;
        }
        LinkedHashMap<String, String> row = copy(fields);
        row.putIfAbsent("type", "rendered_outline");
        long cursorRowId = lastCursorRowId();
        row.putIfAbsent("outlineRenderId", Long.toString(ROW_IDS.incrementAndGet()));
        row.putIfAbsent("cursorRowId", Long.toString(cursorRowId));
        row.putIfAbsent("recordedAt", Instant.now().toString());
        appendLastCursorFields(row);
        String markers = renderedOutlineMarkers(row);
        row.put("marker", markers);
        synchronized (LOCK) {
            renderedOutlineRows++;
            if (markers.contains("LIVE_RENDERED_OUTLINE_LARGE_BOUNDS")) {
                renderedOutlineLargeBoundsRows++;
            }
            if (markers.contains("LIVE_RENDERED_OUTLINE_REPLAY_BOUNDS_SPLIT")) {
                renderedOutlineReplayBoundsSplitRows++;
            }
            if (markers.contains("LIVE_RENDERED_OUTLINE_TARGET_SPLIT")) {
                renderedOutlineTargetSplitRows++;
            }
            writeSession(row);
            writeRenderedOutlineTsv(row);
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
            renderedOutlineRows = 0L;
            renderedOutlineLargeBoundsRows = 0L;
            renderedOutlineReplayBoundsSplitRows = 0L;
            renderedOutlineTargetSplitRows = 0L;
            placementExpectedDyMismatchRows = 0L;
            loweredSideSlabPlacementVanillaDyRows = 0L;
            collisionIteratorTargetMissRows = 0L;
            collisionIteratorTargetPresentRows = 0L;
            greenCursorTriadRows = 0L;
            greenPlacementAuthoringRows = 0L;
            lastCursorRowId = 0L;
            lastCursorRow = null;
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
        boolean targetInCollisionQuery = Boolean.parseBoolean(row.getOrDefault("targetCollisionIntersectsQueryBox", "false"));
        boolean targetReturnedByIterator = Boolean.parseBoolean(
                row.getOrDefault("playerBlockCollisionTargetIntersectsReturned", "false"));
        StringBuilder markers = new StringBuilder();
        appendMarker(markers, outlineHit && !raycastHit, "LIVE_CURSOR_OUTLINE_RAYCAST_SPLIT");
        appendMarker(markers, outlineHit && !raycastHit && lawfulLowered, "LIVE_CURSOR_GHOST_SURFACE");
        appendMarker(markers,
                lawfulLowered && targetInCollisionQuery && !targetReturnedByIterator,
                "LIVE_COLLISION_ITERATOR_TARGET_MISS");
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

    private static void appendLastCursorFields(LinkedHashMap<String, String> row) {
        LinkedHashMap<String, String> cursor = lastCursorRow;
        if (cursor == null) {
            row.putIfAbsent("cursorOutlineBounds", "none");
            row.putIfAbsent("cursorFinalHitPos", "none");
            row.putIfAbsent("cursorFinalHitState", "none");
            row.putIfAbsent("cursorFinalHitVec", "none");
            row.putIfAbsent("cursorFinalHitFace", "none");
            row.putIfAbsent("cursorHeldItem", "none");
            return;
        }
        row.putIfAbsent("cursorOutlineBounds", cursor.getOrDefault("outlineBounds", "none"));
        row.putIfAbsent("cursorFinalHitPos", cursor.getOrDefault("finalHitPos", "none"));
        row.putIfAbsent("cursorFinalHitState", cursor.getOrDefault("finalHitState", "none"));
        row.putIfAbsent("cursorFinalHitVec", cursor.getOrDefault("finalHitVec", "none"));
        row.putIfAbsent("cursorFinalHitFace", cursor.getOrDefault("finalHitFace", "none"));
        row.putIfAbsent("cursorHeldItem", cursor.getOrDefault("heldItem", "none"));
    }

    private static String renderedOutlineMarkers(Map<String, String> row) {
        String renderedBounds = row.getOrDefault("renderedOutlineBounds", "none");
        String cursorBounds = row.getOrDefault("cursorOutlineBounds", "none");
        String renderedPos = row.getOrDefault("renderedOutlinePos", "none");
        String cursorPos = row.getOrDefault("cursorFinalHitPos", "none");
        StringBuilder markers = new StringBuilder();
        appendMarker(markers, hasLargeBounds(renderedBounds), "LIVE_RENDERED_OUTLINE_LARGE_BOUNDS");
        appendMarker(markers, isRealBounds(renderedBounds)
                        && isRealBounds(cursorBounds)
                        && !renderedBounds.equals(cursorBounds),
                "LIVE_RENDERED_OUTLINE_REPLAY_BOUNDS_SPLIT");
        appendMarker(markers, isRealValue(renderedPos)
                        && isRealValue(cursorPos)
                        && !renderedPos.equals(cursorPos),
                "LIVE_RENDERED_OUTLINE_TARGET_SPLIT");
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

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean hasLargeBounds(String bounds) {
        if (!isRealBounds(bounds)) {
            return false;
        }
        double[] values = parseBounds(bounds);
        if (values == null) {
            return false;
        }
        return values[3] - values[0] > 2.0d
                || values[4] - values[1] > 2.0d
                || values[5] - values[2] > 2.0d;
    }

    private static boolean isRealBounds(String value) {
        return isRealValue(value) && value.startsWith("min=(") && value.contains("),max=(");
    }

    private static boolean isRealValue(String value) {
        return value != null
                && !value.isEmpty()
                && !"none".equals(value)
                && !"empty".equals(value)
                && !"null".equals(value)
                && !value.startsWith("error:");
    }

    private static double[] parseBounds(String bounds) {
        try {
            int minStart = bounds.indexOf("min=(");
            int minEnd = bounds.indexOf("),max=(");
            int maxEnd = bounds.indexOf(')', minEnd + 7);
            if (minStart < 0 || minEnd < 0 || maxEnd < 0) {
                return null;
            }
            String[] min = bounds.substring(minStart + 5, minEnd).split(",");
            String[] max = bounds.substring(minEnd + 7, maxEnd).split(",");
            if (min.length != 3 || max.length != 3) {
                return null;
            }
            return new double[]{
                    Double.parseDouble(min[0]),
                    Double.parseDouble(min[1]),
                    Double.parseDouble(min[2]),
                    Double.parseDouble(max[0]),
                    Double.parseDouble(max[1]),
                    Double.parseDouble(max[2])
            };
        } catch (RuntimeException ignored) {
            return null;
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

    private static void writeRenderedOutlineTsv(LinkedHashMap<String, String> row) {
        appendLine("rendered-outlines.tsv",
                tsv(row.get("outlineRenderId"))
                        + '\t' + tsv(row.get("cursorRowId"))
                        + '\t' + tsv(row.get("renderedOutlinePos"))
                        + '\t' + tsv(row.get("cursorFinalHitPos"))
                        + '\t' + tsv(row.get("renderedOutlineState"))
                        + '\t' + tsv(row.get("renderedOutlineBounds"))
                        + '\t' + tsv(row.get("cursorOutlineBounds"))
                        + '\t' + tsv(row.get("renderedOutlineWorldBounds"))
                        + '\t' + tsv(row.get("renderedOutlineCameraRelativeBounds"))
                        + '\t' + tsv(row.get("renderedOutlineHitVec"))
                        + '\t' + tsv(row.get("marker")));
    }

    private static void writeMismatchRows(LinkedHashMap<String, String> row, String markers) {
        if (markers == null || markers.equals("none") || markers.startsWith("LIVE_GREEN_")) {
            return;
        }
        appendLine("mismatches.tsv",
                tsv(row.getOrDefault("type", "unknown"))
                        + '\t' + tsv(row.getOrDefault(
                                "rowId",
                                row.getOrDefault("actionId", row.getOrDefault("outlineRenderId", "unknown"))))
                        + '\t' + tsv(markers)
                        + '\t' + tsv(row.getOrDefault(
                                "finalHitPos",
                                row.getOrDefault("clickedOwnerPos", row.getOrDefault("renderedOutlinePos", "none"))))
                        + '\t' + tsv(row.getOrDefault("heldItem", row.getOrDefault("cursorHeldItem", "none"))));
    }

    private static void writeSummary() {
        StringBuilder text = new StringBuilder();
        text.append("# Slabbed Live Cursor Intent Recorder Summary\n\n");
        text.append("cursorRows=").append(cursorRows).append('\n');
        text.append("actionRows=").append(actionRows).append('\n');
        text.append("ghostSurfaceRows=").append(ghostSurfaceRows).append('\n');
        text.append("hiddenOwnerRows=").append(hiddenOwnerRows).append('\n');
        text.append("outlineRaycastSplitRows=").append(outlineRaycastSplitRows).append('\n');
        text.append("renderedOutlineRows=").append(renderedOutlineRows).append('\n');
        text.append("renderedOutlineLargeBoundsRows=").append(renderedOutlineLargeBoundsRows).append('\n');
        text.append("renderedOutlineReplayBoundsSplitRows=")
                .append(renderedOutlineReplayBoundsSplitRows).append('\n');
        text.append("renderedOutlineTargetSplitRows=").append(renderedOutlineTargetSplitRows).append('\n');
        text.append("placementExpectedDyMismatchRows=").append(placementExpectedDyMismatchRows).append('\n');
        text.append("loweredSideSlabPlacementVanillaDyRows=")
                .append(loweredSideSlabPlacementVanillaDyRows).append('\n');
        text.append("collisionIteratorTargetMissRows=").append(collisionIteratorTargetMissRows).append('\n');
        text.append("collisionIteratorTargetPresentRows=").append(collisionIteratorTargetPresentRows).append('\n');
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
            writeHeaderIfMissing(sessionDir.resolve("rendered-outlines.tsv"),
                    "outlineRenderId\tcursorRowId\trenderedOutlinePos\tcursorFinalHitPos\trenderedOutlineState"
                            + "\trenderedOutlineBounds\tcursorOutlineBounds\trenderedOutlineWorldBounds"
                            + "\trenderedOutlineCameraRelativeBounds\trenderedOutlineHitVec\tmarker\n");
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
