package com.slabbed.devtools.recording;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One recorder-schema-6 evidence directory.
 *
 * <p>This class deliberately contains no Minecraft or Forge types. That keeps the six-file,
 * redaction, correlation, and flush laws directly executable without pretending a development
 * classpath proves the addon loaded in a packaged game.
 */
public final class Schema6Session implements AutoCloseable {
    public static final String SCHEMA_VERSION = "6";
    public static final String RECORDER_VERSION = "forge-schema6-logical-attempts-v1";
    public static final List<String> FILE_NAMES = List.of(
            "manifest.json",
            "session.jsonl",
            "actions.tsv",
            "rendered-outlines.tsv",
            "mismatches.tsv",
            "summary.md");

    public static final String ACTIONS_HEADER =
            "actionId\tcursorRowId\tactionType\tactionOrigin\theldItem\tclickedOwnerPos\tclickedFace\tplacementPos"
                    + "\ttargetDy\ttargetDyBits\ttargetPlacementPos\ttargetDySource"
                    + "\trigCaseId\trigLabel\trigExpectedDy\trigExpectedDyBits\trigFace\trigOrientation"
                    + "\texpectedAfterDy\tafterDy\texpectedAfterLaneKind\tafterLaneKind\tmarker"
                    + "\tafterStoredDy\tafterStoredDyBits\tpairPos\tpairPart\tpairState"
                    + "\tpairAfterDy\tpairStoredDy\tpairStoredDyBits"
                    + "\tlogicalAttemptId\tphase\tplayerProof";
    public static final String OUTLINES_HEADER =
            "outlineRenderId\tcursorRowId\trenderedOutlinePos\tcursorFinalHitPos\trenderedOutlineState"
                    + "\trenderedOutlineBounds\tcursorOutlineBounds\trenderedOutlineWorldBounds"
                    + "\trenderedOutlineCameraRelativeBounds\trenderedOutlineHitVec"
                    + "\trigCaseId\trigLabel\texpectedDy\texpectedDyBits"
                    + "\toutlineDy\toutlineDyBits\toutlineStatus\thitWithinOutline"
                    + "\traycastOwnerStatus\tcontactPlaneStatus\tmodelTraceStatus"
                    + "\tmodelTraceDy\tmodelTraceDyBits\tmodelStatus\tmarker";
    public static final String MISMATCHES_HEADER =
            "type\trowOrActionId\tmarker\tpos\theldItem\tfailureClasses";

    private static final Pattern SENSITIVE_LAUNCH_ARG_PATTERN = Pattern.compile(
            "(--(?:accessToken|uuid|xuid|clientId|session))(?:\\s+|=)(?:\"[^\"]*\"|'[^']*'|\\S+)",
            Pattern.CASE_INSENSITIVE);
    private static final long PAIR_WINDOW_NANOS = 1_000_000_000L;
    private static final int MAX_PENDING_CLIENT_ATTEMPTS = 256;

    private final String runId;
    private final Path directory;
    private final LinkedHashMap<String, ArrayDeque<PendingClientAttempt>> pendingClients =
            new LinkedHashMap<>();
    private final LinkedHashMap<String, LinkedHashMap<String, String>> pendingRigAttempts =
            new LinkedHashMap<>();
    private final LinkedHashMap<String, LinkedHashMap<String, String>> rigCaseEvidence =
            new LinkedHashMap<>();
    private final LinkedHashMap<String, LinkedHashMap<String, String>> rigVisualEvidence =
            new LinkedHashMap<>();
    private final LinkedHashMap<String, String> rigCaseByPlacementPos = new LinkedHashMap<>();
    private LinkedHashMap<String, String> lastCursor = new LinkedHashMap<>();
    private long nextRowId;
    private long nextLogicalAttemptId;
    private int pendingClientCount;
    private boolean closed;

    private long cursorRows;
    private long actionRows;
    private long playerAuthoredActionRows;
    private long autoUseOnProxyActionRows;
    private long gametestActionRows;
    private long renderedOutlineRows;
    private long mismatchRows;
    private long placementSideDySplitRows;
    private long placementSideCellSplitRows;
    private long targetResultDySplitRows;
    private long storedPublicationTimingRows;
    private long rigCaseRows;
    private long rigCaseExactRows;
    private long rigCaseRefusedRows;
    private long rigCaseMismatchRows;
    private long rigCaseInconclusiveRows;
    private long rigCaseVerdictRows;
    private long rigCaseGreenVerdictRows;
    private long rigCaseRedVerdictRows;
    private long rigCaseInconclusiveVerdictRows;
    private long slabcheckRuns;
    private long slabcheckFindingRows;
    private long slabcheckHardDesyncTotal;
    private long slabcheckWouldMoveTotal;
    private long slabcheckUnpinnedLoweredTotal;
    private long logicalAttemptRows;
    private long mergedClientServerAttemptRows;
    private long autoProxyLogicalAttemptRows;
    private long gametestLogicalAttemptRows;
    private long serverOnlyLogicalAttemptRows;
    private long clientOnlyLogicalAttemptRows;
    private long playerProofLogicalAttemptRows;
    private long modelStaleDivergentRows;
    private long modelStaleAbsentRows;
    private long modelStaleYellowRows;
    private long sentinelArmedTotal;
    private long sentinelSamplePasses;

    private record PendingClientAttempt(
            String logicalAttemptId,
            long recordedNanos,
            LinkedHashMap<String, String> row) {
    }

    private Schema6Session(Path requestedDirectory, Map<String, String> manifestFields)
            throws IOException {
        this.runId = UUID.randomUUID().toString();
        Files.createDirectories(requestedDirectory);
        this.directory = isolateFromExistingSession(requestedDirectory, runId);
        Files.createDirectories(directory);

        // Forge intentionally improves on the donor's lazy JSONL quirk: bootstrap always creates
        // all six promised artifacts, even if the player disconnects before the first action.
        write("session.jsonl", "", false);
        write("actions.tsv", ACTIONS_HEADER + System.lineSeparator(), false);
        write("rendered-outlines.tsv", OUTLINES_HEADER + System.lineSeparator(), false);
        write("mismatches.tsv", MISMATCHES_HEADER + System.lineSeparator(), false);
        writeManifest(manifestFields);
        writeSummary();
    }

    public static Schema6Session open(
            Path requestedDirectory,
            Map<String, String> manifestFields) throws IOException {
        return new Schema6Session(requestedDirectory, manifestFields);
    }

    public Path directory() {
        return directory;
    }

    public synchronized void recordCursor(Map<String, String> fields) throws IOException {
        requireOpen();
        LinkedHashMap<String, String> row = copy(fields);
        row.put("type", "cursor");
        row.put("rowId", nextRowId());
        row.put("recordedAt", Instant.now().toString());
        row.putIfAbsent("mismatchMarker", "none");
        lastCursor = copy(row);
        cursorRows++;
        writeSession(row);
        writeMarkers(row, row.get("mismatchMarker"));
        writeSummary();
    }

    public synchronized void recordRenderedOutline(Map<String, String> fields) throws IOException {
        requireOpen();
        LinkedHashMap<String, String> row = copy(fields);
        row.put("type", "rendered_outline");
        row.put("outlineRenderId", nextRowId());
        row.put("cursorRowId", lastCursor.getOrDefault("rowId", "0"));
        row.put("recordedAt", Instant.now().toString());
        appendCursorDefaults(row);
        row.putIfAbsent("marker", "none");
        bindRigVisualEvidence(row);
        renderedOutlineRows++;
        writeSession(row);
        write("rendered-outlines.tsv", joinTsv(row,
                "outlineRenderId", "cursorRowId", "renderedOutlinePos", "cursorFinalHitPos",
                "renderedOutlineState", "renderedOutlineBounds", "cursorOutlineBounds",
                "renderedOutlineWorldBounds", "renderedOutlineCameraRelativeBounds",
                "renderedOutlineHitVec", "rigCaseId", "rigLabel", "expectedDy",
                "expectedDyBits", "outlineDy", "outlineDyBits", "outlineStatus",
                "hitWithinOutline", "raycastOwnerStatus", "contactPlaneStatus",
                "modelTraceStatus", "modelTraceDy", "modelTraceDyBits", "modelStatus",
                "marker") + System.lineSeparator(), true);
        writeMarkers(row, row.get("marker"));
        writeSummary();
    }

    private void bindRigVisualEvidence(LinkedHashMap<String, String> row) {
        String pos = row.getOrDefault("renderedOutlinePos", "none");
        String caseId = rigCaseByPlacementPos.get(pos);
        if (caseId == null) {
            return;
        }
        Map<String, String> rig = rigCaseEvidence.get(caseId);
        if (rig == null) {
            return;
        }
        row.put("rigCaseId", caseId);
        row.put("rigLabel", rig.getOrDefault("rigLabel", "none"));
        row.put("expectedDy", rig.getOrDefault("expectedDy", "none"));
        row.put("expectedDyBits", rig.getOrDefault("expectedDyBits", "none"));

        String outlineStatus = bitsEqual(
                row.get("outlineDyBits"), rig.get("expectedDyBits"))
                ? "EXACT" : "MISMATCH";
        String raycastOwnerStatus = pos.equals(row.get("cursorFinalHitPos"))
                ? "EXACT" : "MISMATCH";
        String contactPlaneStatus = "EXACT".equals(outlineStatus)
                && "true".equals(row.get("hitWithinOutline"))
                ? "EXACT" : "MISMATCH";
        String modelStatus;
        if (!"SEEN".equals(row.get("modelTraceStatus"))) {
            modelStatus = "MISSING";
        } else {
            modelStatus = bitsEqual(
                    row.get("modelTraceDyBits"), rig.get("expectedDyBits"))
                    ? "EXACT" : "MISMATCH";
        }
        row.put("outlineStatus", outlineStatus);
        row.put("raycastOwnerStatus", raycastOwnerStatus);
        row.put("contactPlaneStatus", contactPlaneStatus);
        row.put("modelStatus", modelStatus);

        if (List.of(outlineStatus, raycastOwnerStatus, contactPlaneStatus, modelStatus)
                .contains("MISMATCH")) {
            row.put("marker", appendMarker(
                    row.get("marker"), "LIVE_RIG_CASE_VISUAL_MISMATCH"));
        } else if ("MISSING".equals(modelStatus)) {
            row.put("marker", appendMarker(
                    row.get("marker"), "YELLOW_RIG_CASE_VISUAL_INCOMPLETE"));
        }
        LinkedHashMap<String, String> previous = rigVisualEvidence.get(caseId);
        if (previous == null || visualScore(row) > visualScore(previous)) {
            rigVisualEvidence.put(caseId, copy(row));
        }
    }

    private static int visualScore(Map<String, String> row) {
        int score = 0;
        for (String key : List.of(
                "outlineStatus", "raycastOwnerStatus", "contactPlaneStatus", "modelStatus")) {
            if ("EXACT".equals(row.get(key))) {
                score++;
            }
        }
        return score;
    }

    public synchronized void recordScanner(Map<String, String> fields) throws IOException {
        requireOpen();
        LinkedHashMap<String, String> row = copy(fields);
        String kind = row.getOrDefault("kind", "finding");
        row.put("type", "summary".equals(kind) ? "slabcheck_summary" : "slabcheck_finding");
        row.put("rowId", nextRowId());
        row.put("recordedAt", Instant.now().toString());
        row.putIfAbsent("marker", "none");
        if ("summary".equals(kind)) {
            slabcheckRuns++;
            slabcheckHardDesyncTotal += nonNegativeLong(row.get("hardDesync"));
            slabcheckWouldMoveTotal += nonNegativeLong(row.get("wouldMove"));
            slabcheckUnpinnedLoweredTotal += nonNegativeLong(row.get("unpinnedLowered"));
        } else {
            slabcheckFindingRows++;
        }
        writeSession(row);
        writeMarkers(row, row.get("marker"));
        writeSummary();
    }

    public synchronized void recordSentinel(Map<String, String> fields) throws IOException {
        requireOpen();
        LinkedHashMap<String, String> row = copy(fields);
        row.put("type", "model_stale_sentinel");
        row.put("rowId", nextRowId());
        row.put("recordedAt", Instant.now().toString());
        String kind = row.getOrDefault("kind", "MODEL_STALE_NO_BAKE_YELLOW");
        boolean red = "MODEL_STALE_DIVERGENT".equals(kind)
                || "MODEL_STALE_ABSENT".equals(kind);
        String marker = (red ? "LIVE_" : "YELLOW_") + kind;
        row.put("severity", red ? "red" : "yellow");
        row.put("marker", marker);
        if ("MODEL_STALE_DIVERGENT".equals(kind)) {
            modelStaleDivergentRows++;
        } else if ("MODEL_STALE_ABSENT".equals(kind)) {
            modelStaleAbsentRows++;
        } else {
            modelStaleYellowRows++;
        }
        writeSession(row);
        if (red) {
            writeMismatch(row, marker);
        }
        writeSummary();
    }

    /**
     * Records one trusted action observation and performs bounded client/server correlation.
     * Caller-provided origin, row ids, logical ids, phase, and player-proof fields are ignored.
     */
    public synchronized void recordAction(Map<String, String> fields, String trustedOrigin)
            throws IOException {
        requireOpen();
        long nowNanos = System.nanoTime();
        finalizeExpiredClients(nowNanos);

        LinkedHashMap<String, String> row = copy(fields);
        String side = row.getOrDefault("side", "unknown")
                .toLowerCase(java.util.Locale.ROOT);
        if ("client".equals(side)) {
            appendCursorDefaults(row);
        }
        String origin = normalizeOrigin(trustedOrigin);
        if (origin == null) {
            return;
        }
        row.put("type", "action");
        row.put("actionId", nextRowId());
        row.put("cursorRowId", lastCursor.getOrDefault("rowId", "0"));
        row.put("recordedAt", Instant.now().toString());
        row.put("actionOrigin", origin);
        fillActionDefaults(row);

        row.put("side", side);
        String marker = row.getOrDefault("marker", "none");
        String logicalAttemptId;
        String phase;
        String playerProof;
        PendingClientAttempt client = null;

        boolean playerOrigin = "PLAYER_AUTHORED".equals(origin);
        boolean proxyOrigin = "AUTO_USEON_PROXY".equals(origin);
        if ("GAMETEST".equals(origin)) {
            logicalAttemptId = nextLogicalAttemptId();
            phase = "GAMETEST";
            playerProof = "ABSENT";
        } else if ("client".equals(side)) {
            logicalAttemptId = nextLogicalAttemptId();
            phase = proxyOrigin ? "AUTO_PROXY_CLIENT" : "CLIENT_PREDICTION";
            playerProof = proxyOrigin ? "ABSENT" : "PRESENT";
        } else {
            client = pollMatchingClient(origin, row, nowNanos);
            logicalAttemptId = client == null
                    ? nextLogicalAttemptId()
                    : client.logicalAttemptId();
            phase = proxyOrigin ? "AUTO_PROXY_SERVER" : "SERVER_AUTHORITY";
            playerProof = playerOrigin && client != null ? "PRESENT" : "ABSENT";
            if (client != null) {
                boolean liveDySplit = liveAfterDyDiffers(client.row(), row);
                boolean liveCellSplit = placementCellDiffers(client.row(), row);
                if (liveDySplit) {
                    row.put("clientAfterDy", client.row().getOrDefault("afterDy", "none"));
                    marker = appendMarker(marker, "LIVE_PLACEMENT_SIDE_DY_SPLIT");
                    placementSideDySplitRows++;
                }
                if (liveCellSplit) {
                    row.put("clientPlacementPos", client.row().getOrDefault("placementPos", "none"));
                    marker = appendMarker(marker, "LIVE_PLACEMENT_SIDE_CELL_SPLIT");
                    placementSideCellSplitRows++;
                }
                if (!liveDySplit && !liveCellSplit && storedPublicationDiffers(client.row(), row)) {
                    marker = appendMarker(marker, "INFO_STORED_PUBLICATION_TIMING");
                    storedPublicationTimingRows++;
                }
                if (playerOrigin && targetResultDyDiffers(client.row(), row)) {
                    row.put("targetDy", client.row().getOrDefault("targetDy", "none"));
                    row.put("targetDyBits", client.row().getOrDefault("targetDyBits", "none"));
                    row.put("targetPlacementPos", client.row().getOrDefault(
                            "targetPlacementPos", "none"));
                    marker = appendMarker(marker, "LIVE_TARGET_RESULT_DY_SPLIT");
                    targetResultDySplitRows++;
                }
            }
        }

        row.put("logicalAttemptId", logicalAttemptId);
        row.put("phase", phase);
        row.put("playerProof", playerProof);
        row.put("marker", marker);
        countAction(origin);
        writeSession(row);
        writeActionTsv(row);
        writeMarkers(row, marker);

        if ((playerOrigin || proxyOrigin) && "client".equals(side)) {
            enqueueClient(pendingKey(origin, row), new PendingClientAttempt(
                    logicalAttemptId, nowNanos, copy(row)));
        } else if (playerOrigin && client != null) {
            writeTerminal("MERGED_CLIENT_SERVER", logicalAttemptId, origin, "PRESENT",
                    client.row(), row, marker.contains("LIVE_") ? "RED" : "INCONCLUSIVE");
        } else if (playerOrigin) {
            writeTerminal("SERVER_ONLY", logicalAttemptId, origin, "ABSENT",
                    null, row, marker.contains("LIVE_") ? "RED" : "INCONCLUSIVE");
        } else if (proxyOrigin && hasRigCase(row)) {
            deferRigAttempt("AUTO_PROXY", logicalAttemptId, origin, "ABSENT",
                    client == null ? null : client.row(), row, marker);
        } else if (proxyOrigin) {
            writeTerminal("AUTO_PROXY", logicalAttemptId, origin, "ABSENT",
                    client == null ? null : client.row(), row,
                    marker.contains("LIVE_") ? "RED" : "INCONCLUSIVE");
        } else {
            writeTerminal("GAMETEST", logicalAttemptId, origin, "ABSENT",
                    null, row, marker.contains("LIVE_") ? "RED" : "INCONCLUSIVE");
        }
        writeSummary();
    }

    /**
     * Records raw rig evidence. The one terminal case verdict is deliberately deferred until
     * session close so later case-bound outline/model observations can participate. Raw dy
     * equality alone is never a player-visible GREEN.
     */
    public synchronized void recordRigCase(Map<String, String> fields) throws IOException {
        requireOpen();
        LinkedHashMap<String, String> row = copy(fields);
        String caseId = row.getOrDefault("rigCaseId", "none");
        if ("none".equals(caseId) || caseId.isBlank()) {
            recordMismatch("rig_case_evidence", "LIVE_RIG_CASE_ID_MISSING", "none", "none",
                    "recordRigCase requires exact identity");
            return;
        }
        if (rigCaseEvidence.containsKey(caseId)) {
            recordMismatch("rig_case_evidence", "LIVE_DUPLICATE_RIG_CASE_RESULT",
                    row.getOrDefault("placementPos", row.getOrDefault("pos", "none")),
                    row.getOrDefault("heldItem", "none"), caseId);
            return;
        }
        String grade = row.getOrDefault("grade", "INCONCLUSIVE")
                .toUpperCase(java.util.Locale.ROOT);
        if (!List.of("EXACT", "REFUSED", "MISMATCH", "INCONCLUSIVE").contains(grade)) {
            grade = "INCONCLUSIVE";
        }
        String marker = "EXACT".equals(grade)
                ? "INFO_RIG_CASE_RAW_DY_EXACT"
                : "LIVE_RIG_CASE_RAW_" + grade;
        row.put("type", "rig_case_evidence");
        row.put("rowId", nextRowId());
        row.put("recordedAt", Instant.now().toString());
        row.put("grade", grade);
        row.put("rawDyGrade", grade);
        row.put("marker", marker);
        row.put("anchorStatus", bitsEqual(
                row.get("expectedDyBits"), row.get("observedStoredDyBits"))
                ? "EXACT" : "MISSING_OR_MISMATCH");
        row.putIfAbsent("collisionStatus", "MISSING");
        row.putIfAbsent("stabilityStatus", "MISSING");
        rigCaseRows++;
        switch (grade) {
            case "EXACT" -> rigCaseExactRows++;
            case "REFUSED" -> rigCaseRefusedRows++;
            case "MISMATCH" -> rigCaseMismatchRows++;
            default -> rigCaseInconclusiveRows++;
        }
        rigCaseEvidence.put(caseId, copy(row));
        String placementPos = row.getOrDefault(
                "placementPos", row.getOrDefault("pos", "none"));
        if (!"none".equals(placementPos)) {
            rigCaseByPlacementPos.putIfAbsent(placementPos, caseId);
        }
        writeSession(row);
        writeSummary();
    }

    public synchronized void recordMismatch(
            String type,
            String marker,
            String pos,
            String heldItem,
            String failureClasses) throws IOException {
        requireOpen();
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("type", valueOr(type, "diagnostic"));
        row.put("rowId", nextRowId());
        row.put("recordedAt", Instant.now().toString());
        row.put("marker", valueOr(marker, "UNKNOWN_DIAGNOSTIC"));
        row.put("pos", valueOr(pos, "none"));
        row.put("heldItem", valueOr(heldItem, "none"));
        row.put("failureClasses", valueOr(failureClasses, "none"));
        writeSession(row);
        writeMismatch(row, row.get("marker"));
        writeSummary();
    }

    public synchronized void updateSentinelLiveness(long armedTotal, long samplePasses)
            throws IOException {
        if (closed) {
            return;
        }
        sentinelArmedTotal = armedTotal;
        sentinelSamplePasses = samplePasses;
        writeSummary();
    }

    public synchronized void flush() throws IOException {
        if (closed) {
            return;
        }
        finalizeAllClients();
        writeSummary();
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        finalizeAllClients();
        finalizeAllRigCases();
        writeSummary();
        closed = true;
    }

    public static String redactJavaCommand(String command) {
        if (command == null || command.isEmpty()) {
            return command;
        }
        return SENSITIVE_LAUNCH_ARG_PATTERN.matcher(command).replaceAll("$1 [REDACTED]");
    }

    private void enqueueClient(String key, PendingClientAttempt pending) throws IOException {
        pendingClients.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(pending);
        pendingClientCount++;
        while (pendingClientCount > MAX_PENDING_CLIENT_ATTEMPTS) {
            Map.Entry<String, ArrayDeque<PendingClientAttempt>> eldest =
                    pendingClients.entrySet().iterator().next();
            PendingClientAttempt evicted = eldest.getValue().pollFirst();
            pendingClientCount--;
            if (eldest.getValue().isEmpty()) {
                pendingClients.remove(eldest.getKey());
            }
            finalizeClientOnly(evicted);
        }
    }

    /**
     * Finds the pending client observation this server row belongs to. Same-cell candidates
     * always win; failing that, a candidate at an adjacent cell (Chebyshev distance <= 1) is
     * accepted so a genuine client/server landing disagreement still forms one logical
     * attempt instead of two orphaned, unmarked rows. Anything farther away is left alone —
     * that is a different action, not this one landing somewhere unexpected.
     */
    private PendingClientAttempt pollMatchingClient(
            String origin, Map<String, String> serverRow, long nowNanos) {
        String key = pendingKey(origin, serverRow);
        ArrayDeque<PendingClientAttempt> queue = pendingClients.get(key);
        if (queue == null || queue.isEmpty()) {
            return null;
        }
        String serverPos = serverRow.get("placementPos");
        PendingClientAttempt exact = null;
        PendingClientAttempt adjacent = null;
        for (PendingClientAttempt candidate : queue) {
            if (nowNanos - candidate.recordedNanos() > PAIR_WINDOW_NANOS) {
                continue;
            }
            String candidatePos = candidate.row().get("placementPos");
            if (candidatePos != null && candidatePos.equals(serverPos)) {
                exact = candidate;
                break;
            }
            if (adjacent == null && isAdjacentCell(candidatePos, serverPos)) {
                adjacent = candidate;
            }
        }
        PendingClientAttempt chosen = exact != null ? exact : adjacent;
        if (chosen == null) {
            return null;
        }
        queue.remove(chosen);
        pendingClientCount--;
        if (queue.isEmpty()) {
            pendingClients.remove(key);
        }
        return chosen;
    }

    private void finalizeExpiredClients(long nowNanos) throws IOException {
        List<PendingClientAttempt> expired = new ArrayList<>();
        var iterator = pendingClients.entrySet().iterator();
        while (iterator.hasNext()) {
            ArrayDeque<PendingClientAttempt> queue = iterator.next().getValue();
            while (!queue.isEmpty()
                    && nowNanos - queue.peekFirst().recordedNanos() > PAIR_WINDOW_NANOS) {
                expired.add(queue.removeFirst());
                pendingClientCount--;
            }
            if (queue.isEmpty()) {
                iterator.remove();
            }
        }
        for (PendingClientAttempt attempt : expired) {
            finalizeClientOnly(attempt);
        }
    }

    private void finalizeAllClients() throws IOException {
        List<PendingClientAttempt> all = new ArrayList<>();
        for (ArrayDeque<PendingClientAttempt> queue : pendingClients.values()) {
            all.addAll(queue);
        }
        pendingClients.clear();
        pendingClientCount = 0;
        for (PendingClientAttempt attempt : all) {
            finalizeClientOnly(attempt);
        }
    }

    private void finalizeClientOnly(PendingClientAttempt attempt) throws IOException {
        if (attempt == null) {
            return;
        }
        String origin = attempt.row().getOrDefault("actionOrigin", "PLAYER_AUTHORED");
        String status = "AUTO_USEON_PROXY".equals(origin) ? "AUTO_PROXY" : "CLIENT_ONLY";
        if ("AUTO_USEON_PROXY".equals(origin) && hasRigCase(attempt.row())) {
            deferRigAttempt(status, attempt.logicalAttemptId(), origin, "ABSENT",
                    attempt.row(), null, "none");
            return;
        }
        writeTerminal(status, attempt.logicalAttemptId(), origin, "ABSENT",
                attempt.row(), null, "INCONCLUSIVE");
    }

    private void deferRigAttempt(
            String status,
            String logicalAttemptId,
            String origin,
            String playerProof,
            Map<String, String> client,
            Map<String, String> server,
            String marker) throws IOException {
        String caseId = evidence(server, client, "rigCaseId");
        if ("none".equals(caseId)) {
            writeTerminal(status, logicalAttemptId, origin, playerProof,
                    client, server, marker != null && marker.contains("LIVE_")
                            ? "RED" : "INCONCLUSIVE");
            return;
        }
        LinkedHashMap<String, String> attempt = new LinkedHashMap<>();
        attempt.put("attemptStatus", status);
        attempt.put("logicalAttemptId", logicalAttemptId);
        attempt.put("actionOrigin", origin);
        attempt.put("playerProof", playerProof);
        attempt.put("clientActionId", client == null
                ? "none" : client.getOrDefault("actionId", "none"));
        attempt.put("serverActionId", server == null
                ? "none" : server.getOrDefault("actionId", "none"));
        attempt.put("rigCaseId", caseId);
        attempt.put("rigLabel", evidence(server, client, "rigLabel"));
        attempt.put("marker", valueOr(marker, "none"));
        if (pendingRigAttempts.putIfAbsent(caseId, attempt) != null) {
            recordMismatch("rig_case_evidence", "LIVE_DUPLICATE_RIG_CASE_ATTEMPT",
                    evidence(server, client, "placementPos"),
                    evidence(server, client, "heldItem"), caseId);
        }
    }

    private void finalizeAllRigCases() throws IOException {
        for (Map.Entry<String, LinkedHashMap<String, String>> entry
                : rigCaseEvidence.entrySet()) {
            writeRigCaseVerdict(
                    entry.getKey(),
                    entry.getValue(),
                    pendingRigAttempts.remove(entry.getKey()),
                    rigVisualEvidence.get(entry.getKey()));
        }
        for (Map.Entry<String, LinkedHashMap<String, String>> orphan
                : pendingRigAttempts.entrySet()) {
            LinkedHashMap<String, String> row = copy(orphan.getValue());
            row.put("type", "rig_case_verdict");
            row.put("rowId", nextRowId());
            row.put("recordedAt", Instant.now().toString());
            row.put("rawDyGrade", "MISSING");
            row.put("finalVerdict", "RED");
            row.put("marker", "LIVE_RIG_CASE_EVIDENCE_MISSING");
            row.put("failureClasses", "LIVE_RIG_CASE_EVIDENCE_MISSING");
            countRigVerdict("RED");
            writeSession(row);
            writeMismatch(row, row.get("marker"));
        }
        pendingRigAttempts.clear();
    }

    private void writeRigCaseVerdict(
            String caseId,
            Map<String, String> evidenceRow,
            Map<String, String> attempt,
            Map<String, String> visual) throws IOException {
        LinkedHashMap<String, String> row = copy(evidenceRow);
        row.put("type", "rig_case_verdict");
        row.put("rowId", nextRowId());
        row.put("recordedAt", Instant.now().toString());
        row.put("rigCaseId", caseId);
        row.put("attemptStatus", attempt == null
                ? "MISSING" : attempt.getOrDefault("attemptStatus", "MISSING"));
        row.put("logicalAttemptId", attempt == null
                ? "none" : attempt.getOrDefault("logicalAttemptId", "none"));
        row.put("clientActionId", attempt == null
                ? "none" : attempt.getOrDefault("clientActionId", "none"));
        row.put("serverActionId", attempt == null
                ? "none" : attempt.getOrDefault("serverActionId", "none"));
        String attemptMarker = attempt == null ? "none" : attempt.getOrDefault("marker", "none");
        row.put("attemptMarker", attemptMarker);

        for (String key : List.of(
                "outlineStatus", "raycastOwnerStatus", "contactPlaneStatus",
                "modelTraceStatus", "modelStatus", "outlineRenderId")) {
            row.put(key, visual == null ? "MISSING" : visual.getOrDefault(key, "MISSING"));
        }

        String grade = row.getOrDefault("rawDyGrade", "INCONCLUSIVE");
        String verdict;
        String marker;
        if (!"EXACT".equals(grade)) {
            verdict = "RED";
            marker = "LIVE_RIG_CASE_" + grade;
        } else if (attempt == null) {
            verdict = "RED";
            marker = "LIVE_RIG_CASE_ACTION_EVIDENCE_MISSING";
        } else if (containsRedMarker(attemptMarker)) {
            verdict = "RED";
            marker = attemptMarker;
        } else if (rigProofComplete(row)) {
            verdict = "GREEN";
            marker = "LIVE_GREEN_RIG_CASE_PROOF_COMPLETE";
        } else {
            verdict = "INCONCLUSIVE";
            marker = "YELLOW_RIG_CASE_RAW_DY_EXACT_PROOF_INCOMPLETE";
        }
        row.put("finalVerdict", verdict);
        row.put("marker", marker);
        row.put("failureClasses", "GREEN".equals(verdict)
                ? "none" : missingRigProof(row, marker));
        countRigVerdict(verdict);
        writeSession(row);
        if ("RED".equals(verdict)) {
            writeMismatch(row, marker);
        }
    }

    private void countRigVerdict(String verdict) {
        rigCaseVerdictRows++;
        logicalAttemptRows++;
        autoProxyLogicalAttemptRows++;
        switch (verdict) {
            case "GREEN" -> rigCaseGreenVerdictRows++;
            case "RED" -> rigCaseRedVerdictRows++;
            default -> rigCaseInconclusiveVerdictRows++;
        }
    }

    private static boolean rigProofComplete(Map<String, String> row) {
        return "EXACT".equals(row.get("anchorStatus"))
                && "EXACT".equals(row.get("outlineStatus"))
                && "EXACT".equals(row.get("raycastOwnerStatus"))
                && "EXACT".equals(row.get("contactPlaneStatus"))
                && "EXACT".equals(row.get("modelStatus"))
                && "EXACT".equals(row.get("collisionStatus"))
                && "EXACT".equals(row.get("stabilityStatus"));
    }

    private static String missingRigProof(Map<String, String> row, String fallback) {
        List<String> missing = new ArrayList<>();
        for (String key : List.of(
                "anchorStatus", "outlineStatus", "raycastOwnerStatus", "contactPlaneStatus",
                "modelStatus", "collisionStatus", "stabilityStatus")) {
            if (!"EXACT".equals(row.get(key))) {
                missing.add(key + "=" + row.getOrDefault(key, "MISSING"));
            }
        }
        return missing.isEmpty() ? fallback : String.join("|", missing);
    }

    private static boolean containsRedMarker(String markers) {
        if (markers == null || markers.isBlank() || "none".equals(markers)) {
            return false;
        }
        for (String marker : markers.split("\\|")) {
            if (marker.startsWith("LIVE_") && !marker.startsWith("LIVE_GREEN_")) {
                return true;
            }
        }
        return false;
    }

    private void writeTerminal(
            String status,
            String logicalAttemptId,
            String origin,
            String playerProof,
            Map<String, String> client,
            Map<String, String> server,
            String verdict) throws IOException {
        LinkedHashMap<String, String> terminal = new LinkedHashMap<>();
        terminal.put("type", "placement_attempt");
        terminal.put("rowId", nextRowId());
        terminal.put("recordedAt", Instant.now().toString());
        terminal.put("logicalAttemptId", logicalAttemptId);
        terminal.put("attemptStatus", status);
        terminal.put("actionOrigin", origin);
        terminal.put("playerProof", playerProof);
        terminal.put("clientActionId", client == null ? "none" : client.getOrDefault("actionId", "none"));
        terminal.put("serverActionId", server == null ? "none" : server.getOrDefault("actionId", "none"));
        terminal.put("rigCaseId", evidence(server, client, "rigCaseId"));
        terminal.put("rigLabel", evidence(server, client, "rigLabel"));
        terminal.put("finalVerdict", verdict);
        terminal.put("failureClasses", "RED".equals(verdict)
                ? evidence(server, client, "marker") : "none");
        logicalAttemptRows++;
        switch (status) {
            case "MERGED_CLIENT_SERVER" -> mergedClientServerAttemptRows++;
            case "AUTO_PROXY" -> autoProxyLogicalAttemptRows++;
            case "GAMETEST" -> gametestLogicalAttemptRows++;
            case "SERVER_ONLY" -> serverOnlyLogicalAttemptRows++;
            case "CLIENT_ONLY" -> clientOnlyLogicalAttemptRows++;
            default -> { }
        }
        if ("PRESENT".equals(playerProof)) {
            playerProofLogicalAttemptRows++;
        }
        writeSession(terminal);
    }

    private void countAction(String origin) {
        actionRows++;
        switch (origin) {
            case "PLAYER_AUTHORED" -> playerAuthoredActionRows++;
            case "AUTO_USEON_PROXY" -> autoUseOnProxyActionRows++;
            case "GAMETEST" -> gametestActionRows++;
            default -> { }
        }
    }

    private void writeActionTsv(Map<String, String> row) throws IOException {
        write("actions.tsv", joinTsv(row,
                "actionId", "cursorRowId", "actionType", "actionOrigin", "heldItem",
                "clickedOwnerPos", "clickedFace", "placementPos", "targetDy", "targetDyBits",
                "targetPlacementPos", "targetDySource", "rigCaseId", "rigLabel",
                "rigExpectedDy", "rigExpectedDyBits", "rigFace", "rigOrientation",
                "expectedAfterDy", "afterDy",
                "expectedAfterLaneKind", "afterLaneKind", "marker", "afterStoredDy",
                "afterStoredDyBits", "pairPos", "pairPart", "pairState", "pairAfterDy",
                "pairStoredDy", "pairStoredDyBits", "logicalAttemptId", "phase", "playerProof")
                + System.lineSeparator(), true);
    }

    private void writeMarkers(Map<String, String> row, String markers) throws IOException {
        if (markers == null || markers.isBlank() || "none".equals(markers)) {
            return;
        }
        for (String marker : markers.split("\\|")) {
            if (marker.startsWith("LIVE_") && !marker.startsWith("LIVE_GREEN_")) {
                writeMismatch(row, marker);
            }
        }
    }

    private void writeMismatch(Map<String, String> row, String marker) throws IOException {
        LinkedHashMap<String, String> mismatch = copy(row);
        mismatch.put("marker", marker);
        mismatch.put("rowOrActionId", row.getOrDefault(
                "rowId",
                row.getOrDefault("actionId", row.getOrDefault("outlineRenderId", "unknown"))));
        mismatch.putIfAbsent("pos", row.getOrDefault("clickedOwnerPos",
                row.getOrDefault("renderedOutlinePos", row.getOrDefault("placementPos", "none"))));
        mismatch.putIfAbsent("heldItem", "none");
        mismatch.putIfAbsent("failureClasses", marker);
        write("mismatches.tsv", joinTsv(mismatch,
                "type", "rowOrActionId", "marker", "pos", "heldItem", "failureClasses")
                + System.lineSeparator(), true);
        mismatchRows++;
    }

    private void writeSession(Map<String, String> row) throws IOException {
        write("session.jsonl", toJson(row) + System.lineSeparator(), true);
    }

    private void writeSummary() throws IOException {
        String summary = "# Slabbed Schema 6 Recorder Summary\n\n"
                + "schemaVersion=6\n"
                + "cursorRows=" + cursorRows + "\n"
                + "actionRows=" + actionRows + "\n"
                + "playerAuthoredActionRows=" + playerAuthoredActionRows + "\n"
                + "autoUseOnProxyActionRows=" + autoUseOnProxyActionRows + "\n"
                + "gametestActionRows=" + gametestActionRows + "\n"
                + "renderedOutlineRows=" + renderedOutlineRows + "\n"
                + "mismatchRows=" + mismatchRows + "\n"
                + "placementSideDySplitRows=" + placementSideDySplitRows + "\n"
                + "placementSideCellSplitRows=" + placementSideCellSplitRows + "\n"
                + "targetResultDySplitRows=" + targetResultDySplitRows + "\n"
                + "storedPublicationTimingRows=" + storedPublicationTimingRows + "\n"
                + "rigCaseRows=" + rigCaseRows + "\n"
                + "rigCaseExactRows=" + rigCaseExactRows + "\n"
                + "rigCaseRefusedRows=" + rigCaseRefusedRows + "\n"
                + "rigCaseMismatchRows=" + rigCaseMismatchRows + "\n"
                + "rigCaseInconclusiveRows=" + rigCaseInconclusiveRows + "\n"
                + "rigCaseVerdictRows=" + rigCaseVerdictRows + "\n"
                + "rigCaseGreenVerdictRows=" + rigCaseGreenVerdictRows + "\n"
                + "rigCaseRedVerdictRows=" + rigCaseRedVerdictRows + "\n"
                + "rigCaseInconclusiveVerdictRows=" + rigCaseInconclusiveVerdictRows + "\n"
                + "rigCasePendingVerdictRows=" + Math.max(0L, rigCaseRows - rigCaseVerdictRows) + "\n"
                + "slabcheckRuns=" + slabcheckRuns + "\n"
                + "slabcheckFindingRows=" + slabcheckFindingRows + "\n"
                + "slabcheckHardDesyncTotal=" + slabcheckHardDesyncTotal + "\n"
                + "slabcheckWouldMoveTotal=" + slabcheckWouldMoveTotal + "\n"
                + "slabcheckUnpinnedLoweredTotal=" + slabcheckUnpinnedLoweredTotal + "\n"
                + "logicalAttemptRows=" + logicalAttemptRows + "\n"
                + "mergedClientServerAttemptRows=" + mergedClientServerAttemptRows + "\n"
                + "autoProxyLogicalAttemptRows=" + autoProxyLogicalAttemptRows + "\n"
                + "gametestLogicalAttemptRows=" + gametestLogicalAttemptRows + "\n"
                + "serverOnlyLogicalAttemptRows=" + serverOnlyLogicalAttemptRows + "\n"
                + "clientOnlyLogicalAttemptRows=" + clientOnlyLogicalAttemptRows + "\n"
                + "playerProofLogicalAttemptRows=" + playerProofLogicalAttemptRows + "\n"
                + "modelStaleDivergentRows=" + modelStaleDivergentRows + "\n"
                + "modelStaleAbsentRows=" + modelStaleAbsentRows + "\n"
                + "modelStaleYellowRows=" + modelStaleYellowRows + "\n"
                + "sentinelArmedTotal=" + sentinelArmedTotal + "\n"
                + "sentinelSamplePasses=" + sentinelSamplePasses + "\n";
        write("summary.md", summary, false);
    }

    private void writeManifest(Map<String, String> supplied) throws IOException {
        LinkedHashMap<String, String> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", SCHEMA_VERSION);
        manifest.put("runId", runId);
        manifest.put("recorder", "SlabbedRecorder");
        manifest.put("recorderVersion", RECORDER_VERSION);
        manifest.put("actionOriginContract", "PLAYER_AUTHORED|AUTO_USEON_PROXY|GAMETEST");
        manifest.put("logicalAttemptContract", "LogicalPlacementAttempt-v1");
        manifest.put("createdAt", Instant.now().toString());
        manifest.put("dir", "[RECORDER_DIR]");
        if (supplied != null) {
            for (Map.Entry<String, String> entry : supplied.entrySet()) {
                String value = "javaCommand".equals(entry.getKey())
                        ? redactHome(redactJavaCommand(entry.getValue()))
                        : redactHome(entry.getValue());
                manifest.put(entry.getKey(), valueOr(value, "unknown"));
            }
        }
        write("manifest.json", toJson(manifest) + System.lineSeparator(), false);
    }

    private static Path isolateFromExistingSession(Path requested, String runId) throws IOException {
        try (var entries = Files.list(requested)) {
            if (entries.findAny().isPresent()) {
                return requested.resolve("schema-" + SCHEMA_VERSION + "-" + runId);
            }
        }
        return requested;
    }

    private static String pendingKey(String origin, Map<String, String> row) {
        return origin + "|" + correlationKey(row);
    }

    private static String correlationKey(Map<String, String> row) {
        String sequence = row.getOrDefault("packetSequence", "none");
        if (!"none".equals(sequence) && !sequence.isBlank()) {
            return String.join("|", "packet", sequence,
                    row.getOrDefault("playerUuid", "none"),
                    row.getOrDefault("dimensionId", "none"));
        }
        // Deliberately excludes placementPos. An adjacent-cell client/server disagreement
        // (the client predicts one cell, the server authors its neighbour) is exactly the
        // case this correlation must still pair up so recordAction can grade it as one
        // logical attempt — not silently split into two INCONCLUSIVE client-only/server-only
        // rows that hide the disagreement from every counter. Position is compared
        // afterward, in pollMatchingClient, to keep merges scoped to the same or an
        // adjacent cell rather than any same-identity action in the pairing window.
        return String.join("|", "fallback",
                row.getOrDefault("actionType", "none"),
                row.getOrDefault("heldItem", "none"),
                row.getOrDefault("rigCaseId", "none"),
                row.getOrDefault("playerUuid", "none"),
                row.getOrDefault("dimensionId", "none"));
    }

    private static boolean dyDiffers(String first, String second) {
        try {
            double a = Double.parseDouble(first);
            double b = Double.parseDouble(second);
            return Double.doubleToRawLongBits(a) != Double.doubleToRawLongBits(b);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean liveAfterDyDiffers(
            Map<String, String> client,
            Map<String, String> server) {
        return dyDiffers(client.get("afterDy"), server.get("afterDy"));
    }

    private static boolean targetResultDyDiffers(
            Map<String, String> client,
            Map<String, String> server) {
        String expectedPos = client.getOrDefault("targetPlacementPos", "none");
        String actualPos = server.getOrDefault("placementPos", "none");
        if ("none".equals(expectedPos) || !expectedPos.equals(actualPos)) {
            return false;
        }
        String targetBits = client.getOrDefault("targetDyBits", "none");
        if ("none".equals(targetBits)) {
            targetBits = rawBits(client.get("targetDy"));
        }
        String resultBits = rawBits(server.get("afterDy"));
        return !"none".equals(targetBits)
                && !"none".equals(resultBits)
                && !targetBits.equals(resultBits);
    }

    private static boolean placementCellDiffers(
            Map<String, String> client,
            Map<String, String> server) {
        String clientPos = client.getOrDefault("placementPos", "none");
        String serverPos = server.getOrDefault("placementPos", "none");
        return !"none".equals(clientPos)
                && !"none".equals(serverPos)
                && !clientPos.equals(serverPos);
    }

    private static boolean isAdjacentCell(String first, String second) {
        int[] a = parsePos(first);
        int[] b = parsePos(second);
        if (a == null || b == null) {
            return false;
        }
        return Math.abs(a[0] - b[0]) <= 1
                && Math.abs(a[1] - b[1]) <= 1
                && Math.abs(a[2] - b[2]) <= 1;
    }

    /** Parses a BlockPos.toShortString()-style "x, y, z" (comma- or space-separated) triple. */
    private static int[] parsePos(String value) {
        if (value == null || value.isBlank() || "none".equals(value)) {
            return null;
        }
        String[] parts = value.trim().split("[,\\s]+");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[] {
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean storedPublicationDiffers(
            Map<String, String> client,
            Map<String, String> server) {
        String clientBits = client.getOrDefault("afterStoredDyBits", "none");
        String serverBits = server.getOrDefault("afterStoredDyBits", "none");
        return !"none".equals(clientBits)
                && !"none".equals(serverBits)
                && !clientBits.equals(serverBits);
    }

    private static boolean hasRigCase(Map<String, String> row) {
        String caseId = row.getOrDefault("rigCaseId", "none");
        return !"none".equals(caseId) && !caseId.isBlank();
    }

    private static boolean bitsEqual(String first, String second) {
        return first != null && second != null
                && !"none".equals(first) && !"absent".equals(first)
                && first.equals(second);
    }

    private static String rawBits(String value) {
        try {
            return Long.toUnsignedString(Double.doubleToRawLongBits(Double.parseDouble(value)));
        } catch (RuntimeException ignored) {
            return "none";
        }
    }

    private static long nonNegativeLong(String value) {
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static String evidence(
            Map<String, String> primary,
            Map<String, String> secondary,
            String key) {
        if (primary != null) {
            String value = primary.get(key);
            if (value != null && !value.isBlank() && !"none".equals(value)) {
                return value;
            }
        }
        return secondary == null ? "none" : secondary.getOrDefault(key, "none");
    }

    private static void fillActionDefaults(LinkedHashMap<String, String> row) {
        for (String field : ACTIONS_HEADER.split("\\t")) {
            row.putIfAbsent(field, "none");
        }
        row.putIfAbsent("side", "unknown");
        row.putIfAbsent("marker", "none");
    }

    private void appendCursorDefaults(LinkedHashMap<String, String> row) {
        putIfEvidenceMissing(row, "clickedOwnerPos", lastCursor.get("finalHitPos"));
        putIfEvidenceMissing(row, "clickedFace", lastCursor.get("hitFace"));
        putIfEvidenceMissing(row, "targetDy", lastCursor.get("finalHitDy"));
        putIfEvidenceMissing(row, "targetDyBits", lastCursor.get("finalHitDyBits"));
        putIfEvidenceMissing(row, "targetPlacementPos", lastCursor.get("expectedPlacementPos"));
        putIfEvidenceMissing(row, "targetDySource", lastCursor.get("finalHitDySource"));
        putIfEvidenceMissing(row, "heldItem", lastCursor.get("heldItem"));
        putIfEvidenceMissing(row, "dimensionId", lastCursor.get("dimensionId"));
        putIfEvidenceMissing(row, "playerUuid", lastCursor.get("playerUuid"));
    }

    private static void putIfEvidenceMissing(Map<String, String> row, String key, String value) {
        String current = row.get(key);
        if ((current == null || current.isBlank() || "none".equals(current))
                && value != null && !value.isBlank()) {
            row.put(key, value);
        }
    }

    private static String normalizeOrigin(String origin) {
        return switch (origin == null ? "" : origin) {
            case "PLAYER_AUTHORED" -> "PLAYER_AUTHORED";
            case "AUTO_USEON_PROXY" -> "AUTO_USEON_PROXY";
            case "GAMETEST" -> "GAMETEST";
            default -> null;
        };
    }

    private static String appendMarker(String existing, String marker) {
        if (existing == null || existing.isBlank() || "none".equals(existing)) {
            return marker;
        }
        return existing.contains(marker) ? existing : existing + "|" + marker;
    }

    private String nextRowId() {
        return Long.toString(++nextRowId);
    }

    private String nextLogicalAttemptId() {
        return Long.toString(++nextLogicalAttemptId);
    }

    private void requireOpen() throws IOException {
        if (closed) {
            throw new IOException("schema-6 recorder session is closed");
        }
    }

    private void write(String fileName, String text, boolean append) throws IOException {
        Path path = directory.resolve(fileName);
        if (append) {
            Files.writeString(path, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            Files.writeString(path, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private static String joinTsv(Map<String, String> row, String... fields) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                out.append('\t');
            }
            out.append(tsv(row.get(fields[i])));
        }
        return out.toString();
    }

    private static String tsv(String value) {
        return valueOr(value, "none")
                .replace('\t', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ');
    }

    private static LinkedHashMap<String, String> copy(Map<String, String> fields) {
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach((key, value) -> copy.put(key, valueOr(value, "none")));
        }
        return copy;
    }

    private static String toJson(Map<String, String> fields) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escapeJson(entry.getKey())).append("\":\"")
                    .append(escapeJson(entry.getValue())).append('"');
        }
        return json.append('}').toString();
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < valueOr(value, "").length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private static String redactHome(String value) {
        if (value == null) {
            return null;
        }
        String home = System.getProperty("user.home", "");
        return home.isBlank() ? value : value.replace(home, "[HOME]");
    }

    private static String valueOr(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
