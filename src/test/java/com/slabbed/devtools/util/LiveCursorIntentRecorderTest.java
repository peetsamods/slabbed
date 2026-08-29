package com.slabbed.devtools.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LiveCursorIntentRecorderTest {
    private static final String ENABLE_PROPERTY = "slabbed.liveCursorIntentRecorder";
    private static final String DIR_PROPERTY = "slabbed.liveCursorIntentRecorderDir";
    private static final BlockPos PLACED_POS = new BlockPos(1, 3, 3);
    private static final String PLACED_POS_TEXT = PLACED_POS.toShortString();

    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void resetRecorder() {
        System.clearProperty(ENABLE_PROPERTY);
        System.clearProperty(DIR_PROPERTY);
        LiveCursorIntentRecorder.resetForTests();
    }

    @Test
    void writesExactIdentityAndStructuredTriage() throws Exception {
        startRecorder();
        LiveCursorIntentRecorder.bootstrap();
        LiveCursorIntentRecorder.flushSummaryForTests();

        String manifest = Files.readString(temporaryDirectory.resolve("manifest.json"));
        String summary = Files.readString(temporaryDirectory.resolve("summary.json"));
        assertTrue(manifest.matches("(?s).*\\\"gitSha\\\":\\\"[0-9a-f]{7,40}(?:-dirty)?\\\".*"));
        assertTrue(manifest.matches("(?s).*\\\"runtimeContentSha256\\\":\\\"[0-9a-f]{64}\\\".*"));
        assertTrue(manifest.matches("(?s).*\\\"recorderContentSha256\\\":\\\"[0-9a-f]{64}\\\".*"));
        assertTrue(summary.contains("\"schemaVersion\":\"9\""));
        assertTrue(summary.matches("(?s).*\\\"captureId\\\":\\\"[^\\\"]+\\\".*"));
        assertTrue(summary.contains("\"runEnded\":true"));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("triage.md")));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("triage.json")));
    }

    @Test
    void correlatesClientServerPlacementAndKeepsNamedEvidence() throws Exception {
        startRecorder();
        try (LiveCursorIntentRecorder.UsePacketScope ignored =
                     LiveCursorIntentRecorder.openUsePacketScope(
                             "client", 11, "player-a", "dimension-a")) {
            LiveCursorIntentRecorder.recordAction(completePlacementRow());
        }
        try (LiveCursorIntentRecorder.UsePacketScope ignored =
                     LiveCursorIntentRecorder.openUsePacketScope(
                             "server", 11, "player-a", "dimension-a")) {
            LiveCursorIntentRecorder.recordAction(completePlacementRow());
        }
        assertEquals(1, LiveCursorIntentRecorder.pendingVerificationCountForTests());
        BlockPos placedPos = PLACED_POS;
        LiveCursorIntentRecorder.recordModelObservation(placedPos, -0.5f);
        assertEquals(
                "-0.5",
                LiveCursorIntentRecorder.pendingEvidenceForTests(placedPos, "modelDy"),
                "requested=" + PLACED_POS_TEXT
                        + " session=" + Files.readString(temporaryDirectory.resolve("session.jsonl")));
        LiveCursorIntentRecorder.recordCursor(cursorRow());
        assertEquals("-0.500000", LiveCursorIntentRecorder.pendingEvidenceForTests(placedPos, "raycastDy"));
        LiveCursorIntentRecorder.recordRenderedOutline(outlineRow());
        LiveCursorIntentRecorder.recordStabilityObservation(placedPos, true);
        LiveCursorIntentRecorder.flushSummaryForTests();

        String actions = Files.readString(temporaryDirectory.resolve("actions.tsv"));
        String session = Files.readString(temporaryDirectory.resolve("session.jsonl"));
        String summary = Files.readString(temporaryDirectory.resolve("summary.json"));
        assertTrue(actions.lines().findFirst().orElseThrow().contains("finalVerdict"));
        assertTrue(actions.lines().findFirst().orElseThrow().contains("missingRequiredComponents"));
        assertTrue(session.contains("\"attemptStatus\":\"MERGED_CLIENT_SERVER\""));
        assertTrue(session.contains("\"finalVerdict\":\"GREEN\""), session);
        assertTrue(session.contains("\"placementRoute\":\"block_item_use_on\""));
        assertTrue(session.contains("\"intentDy\":\"-0.500000\""));
        assertTrue(summary.contains("\"logicalAttemptRows\":1"));
        assertTrue(summary.contains("\"cursorRows\":1"));
        assertTrue(summary.contains("\"renderedOutlineRows\":1"));
        assertEquals(2L, actions.lines().skip(1).count());
        assertFalse(Files.readString(temporaryDirectory.resolve("mismatches.tsv"))
                .contains("LIVE_PLACEMENT_VERDICT_RED"));
    }

    /**
     * A side split is a disagreement about what the two sides SHOW. The client's afterDy is a
     * Level-view read, and a client prediction is deliberately invisible to Level views so a guess
     * can never reach collision or targeting - so until the fact syncs, afterDy reports the
     * pre-placement geometry on EVERY placement while the mesh already draws the resolved height.
     * Splitting on that number reported ordinary placements as client/server disagreements.
     *
     * <p>Both directions in one test, deliberately: the identical afterDy gap must clear when the
     * drawn height agrees and must still fire when it does not. An exemption proved only by its
     * quiet half is indistinguishable from deleting the marker.
     */
    @Test
    void aSideSplitIsMeasuredAgainstWhatTheClientDraws() throws Exception {
        startRecorder();

        LinkedHashMap<String, String> agreeing = completePlacementRow();
        agreeing.put("afterDy", "0.000000");
        agreeing.put("afterLaneKind", "client_pending_server_fact");
        agreeing.put("clientDrawnDy", "-0.500000");
        recordClient(agreeing, 11);
        recordServer(completePlacementRow(), 11);

        LinkedHashMap<String, String> disagreeing = completePlacementRow();
        disagreeing.put("afterDy", "0.000000");
        disagreeing.put("afterLaneKind", "client_pending_server_fact");
        disagreeing.put("clientDrawnDy", "-1.500000");
        recordClient(disagreeing, 12);
        recordServer(completePlacementRow(), 12);

        LiveCursorIntentRecorder.flushSummaryForTests();
        String session = Files.readString(temporaryDirectory.resolve("session.jsonl"));
        assertEquals(
                1L,
                session.lines().filter(line -> line.contains("LIVE_PLACEMENT_SIDE_DY_SPLIT")).count(),
                "exactly the pair whose DRAWN height differs may be marked a split; session="
                        + session);
        assertTrue(session.contains("\"clientAfterDy\":\"-1.500000\""),
                "and the marked row must carry the drawn height it was judged on; session="
                        + session);
    }

    /**
     * A client row holding an unsynced prediction cannot observe post-placement geometry, so the
     * capture reports afterDy as unknown there and clientDrawnDy carries the only height that side
     * can claim. Every height marker must therefore judge the drawn number: testing the lane
     * exemption on afterDy denies it to exactly the rows it exists for, and the mismatch then fires
     * on correct placements.
     *
     * <p>Both directions in one test, deliberately: the exemption must clear when the drawn height
     * matches the intent and must still let the mismatch fire when it does not. An exemption proved
     * only by its quiet half is indistinguishable from deleting the marker.
     */
    @Test
    void aPendingClientRowIsJudgedOnTheHeightItDraws() throws Exception {
        startRecorder();

        LinkedHashMap<String, String> agreeing = completePlacementRow();
        agreeing.put("afterDy", "unknown");
        agreeing.put("afterLaneKind", "client_pending_server_fact");
        agreeing.put("clientDrawnDy", "-0.500000");
        recordClient(agreeing, 21);

        LinkedHashMap<String, String> disagreeing = completePlacementRow();
        disagreeing.put("afterDy", "unknown");
        disagreeing.put("afterLaneKind", "client_pending_server_fact");
        disagreeing.put("clientDrawnDy", "-1.500000");
        recordClient(disagreeing, 22);

        LiveCursorIntentRecorder.flushSummaryForTests();
        String session = Files.readString(temporaryDirectory.resolve("session.jsonl"));
        assertEquals(
                1L,
                session.lines()
                        .filter(line -> line.contains("LIVE_PLACEMENT_EXPECTED_LANE_MISMATCH"))
                        .count(),
                "exactly the row whose DRAWN height differs from intent may be marked; session="
                        + session);
        assertTrue(
                session.lines()
                        .filter(line -> line.contains("\"clientDrawnDy\":\"-1.500000\""))
                        .anyMatch(line -> line.contains("LIVE_PLACEMENT_EXPECTED_LANE_MISMATCH")),
                "and the marked row must be the one that drew the wrong height; session=" + session);
    }

    /**
     * The unknown afterDy on a pending client row is an honest absence, not a value: no height
     * lane may convert it into a mismatch. This is the half that keeps the capture readable — a
     * lane that reddens every unsynced placement reports the instrument, not the placement.
     */
    @Test
    void anUnobservableHeightNeverBecomesAMismatch() throws Exception {
        startRecorder();

        LinkedHashMap<String, String> pending = completePlacementRow();
        pending.put("afterDy", "unknown");
        pending.put("afterLaneKind", "client_pending_server_fact");
        pending.put("clientDrawnDy", "-0.500000");
        recordClient(pending, 31);

        LiveCursorIntentRecorder.flushSummaryForTests();
        String session = Files.readString(temporaryDirectory.resolve("session.jsonl"));
        assertFalse(session.contains("LIVE_PLACEMENT_EXPECTED_DY_MISMATCH"),
                "an unobservable height is not a wrong height; session=" + session);
        String summary = Files.readString(temporaryDirectory.resolve("summary.md"));
        assertTrue(summary.contains("placementExpectedDyMismatchRows=0"),
                "and the counter must agree with the marker; summary=" + summary);
    }

    @Test
    void ensembleMeasurementsRemainWarningsUntilIndependentGeometryFails() {
        assertEquals(
                SlabModelStaleSentinel.DiagnosticSeverity.YELLOW,
                SlabModelStaleSentinel.diagnosticSeverity(
                        SlabModelStaleSentinel.KIND_ENSEMBLE_INTERPENETRATION));
        assertEquals(
                SlabModelStaleSentinel.DiagnosticSeverity.YELLOW,
                SlabModelStaleSentinel.diagnosticSeverity(
                        SlabModelStaleSentinel.KIND_ENSEMBLE_GAP));
        assertEquals(
                SlabModelStaleSentinel.DiagnosticSeverity.RED,
                SlabModelStaleSentinel.diagnosticSeverity(
                        SlabModelStaleSentinel.KIND_DIVERGENT));
        assertEquals(
                SlabModelStaleSentinel.DiagnosticSeverity.RED,
                SlabModelStaleSentinel.diagnosticSeverity(
                        SlabModelStaleSentinel.KIND_ABSENT));
    }

    @Test
    void missingLiveObservationsStayInconclusive() throws Exception {
        startRecorder();
        recordClientServerPair(completePlacementRow());
        LiveCursorIntentRecorder.flushSummaryForTests();

        String session = Files.readString(temporaryDirectory.resolve("session.jsonl"));
        assertTrue(session.contains("\"attemptStatus\":\"MERGED_CLIENT_SERVER\""));
        assertTrue(session.contains("\"finalVerdict\":\"INCONCLUSIVE\""));
        assertTrue(session.contains("\"missingRequiredComponents\":"));
    }

    @Test
    void ensembleCandidatePromotesOnlyAfterCorrelatedModelFailure() throws Exception {
        startRecorder();
        recordClientServerPair(completePlacementRow());

        LinkedHashMap<String, String> candidate = new LinkedHashMap<>();
        candidate.put("kind", SlabModelStaleSentinel.KIND_ENSEMBLE_INTERPENETRATION);
        candidate.put("pos", PLACED_POS_TEXT);
        candidate.put("pairPos", new BlockPos(1, 4, 3).toShortString());
        LiveCursorIntentRecorder.recordSentinel(candidate);
        assertEquals(1L, Files.readAllLines(temporaryDirectory.resolve("mismatches.tsv")).size());

        LiveCursorIntentRecorder.recordModelObservation(PLACED_POS, 0.0f);
        LiveCursorIntentRecorder.flushSummaryForTests();

        String mismatches = Files.readString(temporaryDirectory.resolve("mismatches.tsv"));
        assertTrue(mismatches.contains("ensemble_promoted"), mismatches);
        assertTrue(mismatches.contains("LIVE_ENSEMBLE_INTERPENETRATION"));
        assertTrue(mismatches.contains("MODEL_DY_MISMATCH"));
    }

    @Test
    void keepsClientObservationsThatArriveBeforeServerAuthority() throws Exception {
        startRecorder();
        recordClient(completePlacementRow(), 21);
        LiveCursorIntentRecorder.recordModelObservation(PLACED_POS, -0.5f);
        LiveCursorIntentRecorder.recordCursor(cursorRow());
        LiveCursorIntentRecorder.recordRenderedOutline(outlineRow());
        recordServer(completePlacementRow(), 21);
        LiveCursorIntentRecorder.recordStabilityObservation(PLACED_POS, true);
        LiveCursorIntentRecorder.flushSummaryForTests();

        String session = Files.readString(temporaryDirectory.resolve("session.jsonl"));
        assertTrue(session.contains("\"attemptStatus\":\"MERGED_CLIENT_SERVER\""));
        assertTrue(session.contains("\"finalVerdict\":\"GREEN\""), session);
    }

    @Test
    void raycastReplayMissForcesRed() throws Exception {
        startRecorder();
        recordClientServerPair(completePlacementRow());
        LiveCursorIntentRecorder.recordModelObservation(PLACED_POS, -0.5f);
        LinkedHashMap<String, String> cursor = cursorRow();
        cursor.put("raycastReplayHit", "miss");
        cursor.put("raycastVerdict", "FAIL");
        LiveCursorIntentRecorder.recordCursor(cursor);
        LiveCursorIntentRecorder.recordRenderedOutline(outlineRow());
        LiveCursorIntentRecorder.recordStabilityObservation(PLACED_POS, true);
        LiveCursorIntentRecorder.flushSummaryForTests();

        String session = Files.readString(temporaryDirectory.resolve("session.jsonl"));
        assertTrue(session.contains("\"finalVerdict\":\"RED\""));
        assertTrue(session.contains("RAYCAST_COMPONENT_FAILURE"));
    }

    @Test
    void renderedOutlineSplitForcesRed() throws Exception {
        startRecorder();
        recordClientServerPair(completePlacementRow());
        LiveCursorIntentRecorder.recordModelObservation(PLACED_POS, -0.5f);
        LiveCursorIntentRecorder.recordCursor(cursorRow());
        LinkedHashMap<String, String> outline = outlineRow();
        outline.put("renderedOutlineBounds", "min=(0,0,0),max=(2,0.5,1)");
        LiveCursorIntentRecorder.recordRenderedOutline(outline);
        LiveCursorIntentRecorder.recordStabilityObservation(PLACED_POS, true);
        LiveCursorIntentRecorder.flushSummaryForTests();

        String session = Files.readString(temporaryDirectory.resolve("session.jsonl"));
        assertTrue(session.contains("\"finalVerdict\":\"RED\""));
        assertTrue(session.contains("OUTLINE_COMPONENT_FAILURE"));
    }

    @Test
    void rapidSameCellAttemptsDoNotShareObservations() throws Exception {
        startRecorder();
        recordClient(completePlacementRow(), 31);
        recordClient(completePlacementRow(), 32);
        LiveCursorIntentRecorder.recordModelObservation(PLACED_POS, -0.5f);
        LiveCursorIntentRecorder.recordCursor(cursorRow());
        LiveCursorIntentRecorder.recordRenderedOutline(outlineRow());
        recordServer(completePlacementRow(), 31);
        recordServer(completePlacementRow(), 32);
        LiveCursorIntentRecorder.recordStabilityObservation(PLACED_POS, true);
        LiveCursorIntentRecorder.flushSummaryForTests();

        var sessionRows = Files.readAllLines(temporaryDirectory.resolve("session.jsonl"));
        long green = sessionRows.stream()
                .filter(line -> line.contains("\"type\":\"placement_attempt\""))
                .filter(line -> line.contains("\"finalVerdict\":\"GREEN\""))
                .count();
        long inconclusive = sessionRows.stream()
                .filter(line -> line.contains("\"type\":\"placement_attempt\""))
                .filter(line -> line.contains("\"finalVerdict\":\"INCONCLUSIVE\""))
                .count();
        assertEquals(1L, green);
        assertEquals(1L, inconclusive);
    }

    @Test
    void connectorPropertyChangeDoesNotFailStableAuthoredLane() {
        assertEquals(
                LiveCursorIntentRecorder.StabilityObservation.PASS,
                LiveCursorIntentRecorder.classifyStabilityObservation(
                        "minecraft:birch_fence",
                        "minecraft:birch_fence",
                        "-0.500000",
                        "-0.5",
                        "-0.500000",
                        "-0.5"));
        assertEquals(
                LiveCursorIntentRecorder.StabilityObservation.FAIL,
                LiveCursorIntentRecorder.classifyStabilityObservation(
                        "minecraft:birch_fence",
                        "minecraft:air",
                        "-0.500000",
                        "0.0",
                        "-0.500000",
                        "unknown"));
    }

    @Test
    void proxyAimIsNotAnOracleButProductFloorStillIs() throws Exception {
        startRecorder();
        LinkedHashMap<String, String> legal = completePlacementRow();
        legal.put("intentDy", "0.000000");
        legal.put("expectedAfterDy", "0.000000");
        legal.put("afterDy", "-1.000000");
        legal.put("afterStoredDy", "-1.000000");
        legal.put("resolvedFloorDy", "-1.000000");
        LiveCursorIntentRecorder.withActionOrigin(
                LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                () -> LiveCursorIntentRecorder.recordAction(legal));

        LinkedHashMap<String, String> belowFloor = new LinkedHashMap<>(legal);
        belowFloor.put("afterDy", "-1.500000");
        belowFloor.put("afterStoredDy", "none");
        LiveCursorIntentRecorder.withActionOrigin(
                LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                () -> LiveCursorIntentRecorder.recordAction(belowFloor));
        LiveCursorIntentRecorder.flushSummaryForTests();

        var attempts = Files.readAllLines(temporaryDirectory.resolve("session.jsonl")).stream()
                .filter(line -> line.contains("\"type\":\"placement_attempt\""))
                .toList();
        assertEquals(2, attempts.size());
        assertTrue(attempts.get(0).contains("\"finalVerdict\":\"INCONCLUSIVE\""));
        assertFalse(attempts.get(0).contains("PLACED_ACTION_DY_MISMATCH"));
        assertTrue(attempts.get(1).contains("\"finalVerdict\":\"RED\""));
        assertTrue(attempts.get(1).contains("RESOLVED_DY_BELOW_PRODUCT_FLOOR"));
    }

    @Test
    void cursorTriadGreenIsReachableFromCaptureFieldNames() throws Exception {
        startRecorder();
        LiveCursorIntentRecorder.recordCursor(cursorRow());
        LiveCursorIntentRecorder.flushSummaryForTests();

        String summary = Files.readString(temporaryDirectory.resolve("summary.md"));
        String session = Files.readString(temporaryDirectory.resolve("session.jsonl"));
        assertTrue(summary.contains("liveGreenCursorTriadRows=1"), summary);
        assertTrue(session.contains("\"mismatchMarker\":\"LIVE_GREEN_CURSOR_TRIAD\""), session);
    }

    @Test
    void cursorOutlineRaycastSplitRequiresSameRayProof() throws Exception {
        startRecorder();
        LinkedHashMap<String, String> skew = cursorRow();
        skew.put("raycastReplayHit", "miss");
        skew.put("raycastVerdict", "unknown");
        skew.put("raycastSkippedNearerSurface", "false");
        LiveCursorIntentRecorder.recordCursor(skew);

        LinkedHashMap<String, String> ghost = cursorRow();
        ghost.put("raycastReplayHit", "miss");
        ghost.put("raycastVerdict", "FAIL");
        ghost.put("raycastSkippedNearerSurface", "true");
        LiveCursorIntentRecorder.recordCursor(ghost);
        LiveCursorIntentRecorder.flushSummaryForTests();

        String summary = Files.readString(temporaryDirectory.resolve("summary.md"));
        assertTrue(summary.contains("outlineRaycastSplitRows=1"), summary);
        assertTrue(summary.contains("ghostSurfaceRows=1"), summary);
        String session = Files.readString(temporaryDirectory.resolve("session.jsonl"));
        assertTrue(session.contains("LIVE_CURSOR_OUTLINE_RAYCAST_SPLIT"), session);
    }

    @Test
    void renderedOutlinePanSkewIsInfoNotRed() throws Exception {
        startRecorder();
        LinkedHashMap<String, String> cursor = cursorRow();
        cursor.put("finalHitPos", new BlockPos(9, 3, 3).toShortString());
        LiveCursorIntentRecorder.recordCursor(cursor);
        LinkedHashMap<String, String> outline = outlineRow();
        outline.put("frameGamePickPos", PLACED_POS_TEXT);
        outline.put("frameRaycastVerdict", "PASS");
        LiveCursorIntentRecorder.recordRenderedOutline(outline);
        LiveCursorIntentRecorder.flushSummaryForTests();

        String summary = Files.readString(temporaryDirectory.resolve("summary.md"));
        String session = Files.readString(temporaryDirectory.resolve("session.jsonl"));
        String mismatches = Files.readString(temporaryDirectory.resolve("mismatches.tsv"));
        assertTrue(session.contains("INFO_RENDERED_OUTLINE_TICK_SKEW"), session);
        assertTrue(summary.contains("renderedOutlineTargetSplitRows=0"), summary);
        assertTrue(summary.contains("renderedOutlineTickSkewInfoRows=1"), summary);
        assertTrue(summary.contains("renderedOutlineFrameTriadGreenRows=1"), summary);
        assertFalse(mismatches.contains("RENDERED_OUTLINE"), mismatches);
    }

    @Test
    void renderedOutlineFrameTargetSplitStaysRed() throws Exception {
        startRecorder();
        LiveCursorIntentRecorder.recordCursor(cursorRow());
        LinkedHashMap<String, String> outline = outlineRow();
        outline.put("frameGamePickPos", new BlockPos(9, 9, 9).toShortString());
        outline.put("frameRaycastVerdict", "PASS");
        LiveCursorIntentRecorder.recordRenderedOutline(outline);
        LiveCursorIntentRecorder.flushSummaryForTests();

        String summary = Files.readString(temporaryDirectory.resolve("summary.md"));
        String mismatches = Files.readString(temporaryDirectory.resolve("mismatches.tsv"));
        assertTrue(summary.contains("renderedOutlineFrameTargetSplitRows=1"), summary);
        assertTrue(mismatches.contains("LIVE_FRAME_OUTLINE_TARGET_SPLIT"), mismatches);
    }

    @Test
    void renderedOutlineReplayBoundsSplitRequiresSamePos() throws Exception {
        startRecorder();
        LiveCursorIntentRecorder.recordCursor(cursorRow());
        LinkedHashMap<String, String> samePos = outlineRow();
        samePos.put("renderedOutlineBounds", "min=(0.000000,-0.500000,0.000000),max=(1.000000,0.000000,1.000000)");
        samePos.put("frameGamePickPos", PLACED_POS_TEXT);
        samePos.put("frameRaycastVerdict", "PASS");
        LiveCursorIntentRecorder.recordRenderedOutline(samePos);

        LinkedHashMap<String, String> movedCursor = cursorRow();
        movedCursor.put("finalHitPos", new BlockPos(9, 3, 3).toShortString());
        movedCursor.put("outlineBounds", "min=(0,0,0),max=(1,1,1)");
        LiveCursorIntentRecorder.recordCursor(movedCursor);
        LinkedHashMap<String, String> crossPos = outlineRow();
        crossPos.put("frameGamePickPos", PLACED_POS_TEXT);
        crossPos.put("frameRaycastVerdict", "PASS");
        LiveCursorIntentRecorder.recordRenderedOutline(crossPos);
        LiveCursorIntentRecorder.flushSummaryForTests();

        String summary = Files.readString(temporaryDirectory.resolve("summary.md"));
        assertTrue(summary.contains("renderedOutlineReplayBoundsSplitRows=1"), summary);
        assertTrue(summary.contains("renderedOutlineTickSkewInfoRows=1"), summary);
    }

    @Test
    void frameRaycastSplitForcesRed() throws Exception {
        startRecorder();
        recordClientServerPair(completePlacementRow());
        LiveCursorIntentRecorder.recordModelObservation(PLACED_POS, -0.5f);
        LiveCursorIntentRecorder.recordCursor(cursorRow());
        LinkedHashMap<String, String> outline = outlineRow();
        outline.put("frameGamePickPos", PLACED_POS_TEXT);
        outline.put("frameRaycastVerdict", "FAIL");
        LiveCursorIntentRecorder.recordRenderedOutline(outline);
        LiveCursorIntentRecorder.recordStabilityObservation(PLACED_POS, true);
        LiveCursorIntentRecorder.flushSummaryForTests();

        String session = Files.readString(temporaryDirectory.resolve("session.jsonl"));
        assertTrue(session.contains("\"finalVerdict\":\"RED\""), session);
        assertTrue(session.contains("RAYCAST_COMPONENT_FAILURE"), session);
    }

    @Test
    void frameRaycastFailLatchesAgainstLaterTickPass() throws Exception {
        startRecorder();
        recordClientServerPair(completePlacementRow());
        LiveCursorIntentRecorder.recordModelObservation(PLACED_POS, -0.5f);
        LinkedHashMap<String, String> outline = outlineRow();
        outline.put("frameGamePickPos", PLACED_POS_TEXT);
        outline.put("frameRaycastVerdict", "FAIL");
        LiveCursorIntentRecorder.recordRenderedOutline(outline);
        LiveCursorIntentRecorder.recordCursor(cursorRow());
        LiveCursorIntentRecorder.recordStabilityObservation(PLACED_POS, true);
        LiveCursorIntentRecorder.flushSummaryForTests();

        String session = Files.readString(temporaryDirectory.resolve("session.jsonl"));
        assertTrue(session.contains("\"finalVerdict\":\"RED\""), session);
        assertTrue(session.contains("RAYCAST_COMPONENT_FAILURE"), session);
    }

    @Test
    void skewedOutlinePairNeverLendsANeighborCellsDyToThePlacement() throws Exception {
        startRecorder();
        recordClientServerPair(completePlacementRow());
        LiveCursorIntentRecorder.recordModelObservation(PLACED_POS, -0.5f);
        LinkedHashMap<String, String> neighborCursor = cursorRow();
        neighborCursor.put("finalHitPos", new BlockPos(9, 3, 3).toShortString());
        neighborCursor.put("outlineDy", "-1.000000");
        LiveCursorIntentRecorder.recordCursor(neighborCursor);
        LinkedHashMap<String, String> outline = outlineRow();
        outline.put("frameGamePickPos", PLACED_POS_TEXT);
        outline.put("frameRaycastVerdict", "PASS");
        LiveCursorIntentRecorder.recordRenderedOutline(outline);
        LiveCursorIntentRecorder.recordStabilityObservation(PLACED_POS, true);
        LiveCursorIntentRecorder.flushSummaryForTests();

        String session = Files.readString(temporaryDirectory.resolve("session.jsonl"));
        assertFalse(session.contains("OUTLINE_DY_MISMATCH"), session);
        assertFalse(session.contains("\"finalVerdict\":\"RED\""), session);
    }

    @Test
    void posSplitWithoutAFrameReplayVerdictKeepsTheLegacyRed() throws Exception {
        startRecorder();
        LinkedHashMap<String, String> cursor = cursorRow();
        cursor.put("finalHitPos", new BlockPos(9, 3, 3).toShortString());
        LiveCursorIntentRecorder.recordCursor(cursor);
        LinkedHashMap<String, String> outline = outlineRow();
        outline.put("frameGamePickPos", PLACED_POS_TEXT);
        outline.put("frameRaycastVerdict", "not_run");
        LiveCursorIntentRecorder.recordRenderedOutline(outline);
        LiveCursorIntentRecorder.flushSummaryForTests();

        String summary = Files.readString(temporaryDirectory.resolve("summary.md"));
        assertTrue(summary.contains("renderedOutlineTargetSplitRows=1"), summary);
        assertTrue(summary.contains("renderedOutlineTickSkewInfoRows=0"), summary);
        String mismatches = Files.readString(temporaryDirectory.resolve("mismatches.tsv"));
        assertTrue(mismatches.contains("LIVE_RENDERED_OUTLINE_TARGET_SPLIT"), mismatches);
    }

    @Test
    void coherentOutlineRowCarriesTheFrameTriadGreenMarker() throws Exception {
        startRecorder();
        LiveCursorIntentRecorder.recordCursor(cursorRow());
        LinkedHashMap<String, String> outline = outlineRow();
        outline.put("frameGamePickPos", PLACED_POS_TEXT);
        outline.put("frameRaycastVerdict", "PASS");
        LiveCursorIntentRecorder.recordRenderedOutline(outline);
        LiveCursorIntentRecorder.flushSummaryForTests();

        String session = Files.readString(temporaryDirectory.resolve("session.jsonl"));
        assertTrue(session.contains("\"marker\":\"LIVE_GREEN_FRAME_TRIAD\""), session);
        String mismatches = Files.readString(temporaryDirectory.resolve("mismatches.tsv"));
        assertFalse(mismatches.contains("LIVE_GREEN_FRAME_TRIAD"), mismatches);
    }

    @Test
    void tickSkewUnknownRaycastDoesNotForceRed() throws Exception {
        startRecorder();
        recordClientServerPair(completePlacementRow());
        LiveCursorIntentRecorder.recordModelObservation(PLACED_POS, -0.5f);
        LinkedHashMap<String, String> cursor = cursorRow();
        cursor.put("raycastReplayHit", "miss");
        cursor.put("raycastVerdict", "unknown");
        cursor.put("raycastSkippedNearerSurface", "false");
        LiveCursorIntentRecorder.recordCursor(cursor);
        LinkedHashMap<String, String> outline = outlineRow();
        outline.put("frameGamePickPos", PLACED_POS_TEXT);
        outline.put("frameRaycastVerdict", "PASS");
        LiveCursorIntentRecorder.recordRenderedOutline(outline);
        LiveCursorIntentRecorder.recordStabilityObservation(PLACED_POS, true);
        LiveCursorIntentRecorder.flushSummaryForTests();

        String session = Files.readString(temporaryDirectory.resolve("session.jsonl"));
        assertTrue(session.contains("\"finalVerdict\":\"GREEN\""), session);
        assertFalse(session.contains("RAYCAST_COMPONENT_FAILURE"), session);
    }

    private void startRecorder() {
        System.setProperty(ENABLE_PROPERTY, "true");
        System.setProperty(DIR_PROPERTY, temporaryDirectory.toString());
        LiveCursorIntentRecorder.resetForTests();
    }

    private static void recordClientServerPair(LinkedHashMap<String, String> row) {
        recordClient(row, 11);
        recordServer(row, 11);
    }

    private static void recordClient(LinkedHashMap<String, String> row, int sequence) {
        try (LiveCursorIntentRecorder.UsePacketScope ignored =
                     LiveCursorIntentRecorder.openUsePacketScope(
                             "client", sequence, "player-a", "dimension-a")) {
            LiveCursorIntentRecorder.recordAction(new LinkedHashMap<>(row));
        }
    }

    private static void recordServer(LinkedHashMap<String, String> row, int sequence) {
        try (LiveCursorIntentRecorder.UsePacketScope ignored =
                     LiveCursorIntentRecorder.openUsePacketScope(
                             "server", sequence, "player-a", "dimension-a")) {
            LiveCursorIntentRecorder.recordAction(new LinkedHashMap<>(row));
        }
    }

    private static LinkedHashMap<String, String> completePlacementRow() {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("actionType", "place_block");
        row.put("heldItem", "minecraft:stone");
        row.put("clickedOwnerPos", new BlockPos(1, 2, 3).toShortString());
        row.put("clickedFace", "up");
        row.put("placementPos", PLACED_POS_TEXT);
        row.put("placedBlockId", "minecraft:stone");
        row.put("intentDy", "-0.500000");
        row.put("expectedAfterDy", "-0.500000");
        row.put("afterDy", "-0.500000");
        row.put("afterStoredDy", "-0.500000");
        row.put("afterLaneKind", "stored_placement_height");
        row.put("stabilityVerdict", "NOT_RUN");
        row.put("actualResult", "SUCCESS");
        row.put("placementRoute", "block_item_use_on");
        row.put("landingAuthority", "root_placement_aim");
        return row;
    }

    private static LinkedHashMap<String, String> cursorRow() {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("heldItem", "minecraft:stone");
        row.put("finalHitType", "BLOCK");
        row.put("finalHitPos", PLACED_POS_TEXT);
        row.put("finalHitFace", "up");
        row.put("finalHitState", "minecraft:stone_slab");
        row.put("finalDy", "-0.500000");
        row.put("finalOwnerLaneKind", "stored_placement_height");
        row.put("outlineDy", "-0.500000");
        row.put("outlineReplayHit", "hit");
        row.put("outlineBounds", "min=(0,0,0),max=(1,0.5,1)");
        row.put("raycastDy", "-0.500000");
        row.put("raycastReplayHit", "hit");
        row.put("raycastVerdict", "PASS");
        row.put("collisionDy", "-0.500000");
        row.put("collisionVerdict", "PASS");
        return row;
    }

    private static LinkedHashMap<String, String> outlineRow() {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("renderedOutlinePos", PLACED_POS_TEXT);
        row.put("renderedOutlineState", "minecraft:stone_slab");
        row.put("renderedOutlineBounds", "min=(0,0,0),max=(1,0.5,1)");
        return row;
    }
}
