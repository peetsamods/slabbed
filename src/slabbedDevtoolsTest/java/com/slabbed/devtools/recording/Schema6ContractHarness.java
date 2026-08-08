package com.slabbed.devtools.recording;

import net.minecraft.core.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Executable, framework-free contract for the addon-only schema-6 evidence engine. */
public final class Schema6ContractHarness {
    private Schema6ContractHarness() {
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 1, "expected one build/tmp root argument");
        Path requested = Path.of(args[0], "run-" + System.nanoTime()).toAbsolutePath();
        String command = "net.minecraft.client.main.Main --username TestPlayer --version 1.20.1 "
                + "--accessToken super-secret-jwt --uuid 11112222333344445555666677778888 "
                + "--xuid 9999888877776666 --clientId client-secret --session session-secret "
                + "--credential=example --uuid=equals-uuid --xuid=equals-xuid "
                + "--clientId=equals-client --session=equals-session "
                + "--userType msa";
        LinkedHashMap<String, String> manifestFields = new LinkedHashMap<>();
        manifestFields.put("javaCommand", command);
        manifestFields.put("sharePath", System.getProperty("user.home") + "/private-profile");
        manifestFields.put("coreSha256", "core-sha");
        manifestFields.put("addonSha256", "addon-sha");

        Schema6Session session = Schema6Session.open(requested, manifestFields);
        require(session.directory().equals(requested),
                "a fresh requested directory must be used directly");
        assertExactFiles(requested);
        assertManifestRedaction(requested);
        assertHeader(requested.resolve("actions.tsv"), Schema6Session.ACTIONS_HEADER);
        assertHeader(requested.resolve("rendered-outlines.tsv"), Schema6Session.OUTLINES_HEADER);
        assertHeader(requested.resolve("mismatches.tsv"), Schema6Session.MISMATCHES_HEADER);
        assertOriginScopeContract();
        require("STORED".equals(DyEvidenceSource.classify(false, true, false, false, -2.0d))
                        && "GEOMETRIC".equals(
                                DyEvidenceSource.classify(false, false, false, false, -0.5d))
                        && "-".equals(
                                DyEvidenceSource.classify(false, false, false, false, 0.0d)),
                "overlay source law must distinguish stored truth from geometry");

        LinkedHashMap<String, String> cursor = new LinkedHashMap<>();
        cursor.put("finalHitPos", "1, 2, 3");
        cursor.put("finalHitDy", "-0.5");
        cursor.put("finalHitDyBits", Long.toUnsignedString(
                Double.doubleToRawLongBits(-0.5d)));
        cursor.put("finalHitDySource", "STORED");
        cursor.put("hitFace", "up");
        cursor.put("expectedPlacementPos", "1, 3, 3");
        cursor.put("heldItem", "minecraft:stone");
        cursor.put("playerUuid", "player-one");
        cursor.put("dimensionId", "minecraft:overworld");
        session.recordCursor(cursor);

        session.recordAction(action("client", "1, 3, 3", "-0.5"), "PLAYER_AUTHORED");
        session.updateSentinelLiveness(1L, 1L);
        session.recordAction(action("server", "1, 3, 3", "-0.5"), "PLAYER_AUTHORED");
        session.recordAction(action("client", "2, 3, 3", "-0.5"), "PLAYER_AUTHORED");
        LinkedHashMap<String, String> splitServer = action("server", "2, 3, 3", "0.0");
        splitServer.put("marker", "INFO_PREEXISTING_CONTEXT");
        session.recordAction(splitServer, "PLAYER_AUTHORED");
        session.recordAction(action("client", "3, 3, 3", "-0.5"), "AUTO_USEON_PROXY");
        LinkedHashMap<String, String> proxyServer = action("server", "3, 3, 3", "0.0");
        proxyServer.put("afterStoredDy", "-0.5");
        proxyServer.put("afterStoredDyBits", Long.toUnsignedString(
                Double.doubleToRawLongBits(-0.5d)));
        session.recordAction(proxyServer, "AUTO_USEON_PROXY");
        session.recordAction(action("server", "4, 3, 3", "-0.5"), "GAMETEST");

        LinkedHashMap<String, String> publicationClient = action(
                "client", "6, 3, 3", "-0.5");
        publicationClient.put("afterStoredDy", "NaN");
        publicationClient.put("afterStoredDyBits", Long.toUnsignedString(
                Double.doubleToRawLongBits(Double.NaN)));
        session.recordAction(publicationClient, "PLAYER_AUTHORED");
        session.recordAction(action("server", "6, 3, 3", "-0.5"), "PLAYER_AUTHORED");

        // Adjacent-cell disagreement: client predicts one cell, server authors its neighbour,
        // same dy. Exact-position correlation used to miss this entirely (two separate
        // INCONCLUSIVE client-only/server-only rows, zero side-split counter). It must now
        // merge into one logical attempt and RED.
        session.recordAction(action("client", "20, 3, 3", "-0.5"), "PLAYER_AUTHORED");
        session.recordAction(action("server", "21, 3, 3", "-0.5"), "PLAYER_AUTHORED");

        // Two cells apart is NOT "adjacent" — this must stay split, not merge into the
        // unrelated pending client below it in the same identity bucket.
        session.recordAction(action("client", "50, 3, 3", "-0.5"), "PLAYER_AUTHORED");
        session.recordAction(action("server", "52, 3, 3", "-0.5"), "PLAYER_AUTHORED");

        LinkedHashMap<String, String> targetCursor = new LinkedHashMap<>(cursor);
        targetCursor.put("finalHitPos", "30, 2, 3");
        targetCursor.put("expectedPlacementPos", "30, 3, 3");
        session.recordCursor(targetCursor);
        session.recordAction(action("client", "30, 3, 3", "0.0"), "PLAYER_AUTHORED");
        session.recordAction(action("server", "30, 3, 3", "0.0"), "PLAYER_AUTHORED");

        recordRigAttempt(session, "case.exact", "7, 8, 9", "-1.0");
        recordRigAttempt(session, "case.refused", "8, 8, 9", "0.0");
        recordRigAttempt(session, "case.mismatch", "9, 8, 9", "0.0");
        recordRigAttempt(session, "case.inconclusive", "10, 8, 9", "0.0");
        recordRigAttempt(session, "case.raw-only", "11, 8, 9", "-1.0");
        session.recordRigCase(rigCase("case.exact", "EXACT", "7, 8, 9", true));
        session.recordRigCase(rigCase("case.refused", "REFUSED", "8, 8, 9", false));
        session.recordRigCase(rigCase("case.mismatch", "MISMATCH", "9, 8, 9", false));
        session.recordRigCase(rigCase("case.inconclusive", "INCONCLUSIVE", "10, 8, 9", false));
        session.recordRigCase(rigCase("case.raw-only", "EXACT", "11, 8, 9", false));

        LinkedHashMap<String, String> outline = new LinkedHashMap<>();
        outline.put("renderedOutlinePos", "7, 8, 9");
        outline.put("cursorFinalHitPos", "7, 8, 9");
        outline.put("renderedOutlineState", "Block{minecraft:stone}");
        outline.put("renderedOutlineBounds", "[0,0,0 -> 1,0.5,1]");
        outline.put("cursorOutlineBounds", "[0,0,0 -> 1,0.5,1]");
        outline.put("renderedOutlineWorldBounds", "[7,7,9 -> 8,8,10]");
        outline.put("renderedOutlineHitVec", "7.5,8.0,9.5");
        outline.put("outlineDy", "-1.0");
        outline.put("outlineDyBits", Long.toUnsignedString(
                Double.doubleToRawLongBits(-1.0d)));
        outline.put("hitWithinOutline", "true");
        outline.put("modelTraceStatus", "SEEN");
        outline.put("modelTraceDy", "-1.0");
        outline.put("modelTraceDyBits", Long.toUnsignedString(
                Double.doubleToRawLongBits(-1.0d)));
        outline.put("marker", "none");
        session.recordRenderedOutline(outline);

        LinkedHashMap<String, String> scanSummary = new LinkedHashMap<>();
        scanSummary.put("kind", "summary");
        scanSummary.put("pos", "20, 20, 20");
        scanSummary.put("hardDesync", "0");
        scanSummary.put("wouldMove", "1");
        scanSummary.put("unpinnedLowered", "0");
        scanSummary.put("marker", "LIVE_SLABCHECK_FINDINGS");
        scanSummary.put("failureClasses", "hard=0,wouldMove=1,unpinned=0");
        session.recordScanner(scanSummary);
        LinkedHashMap<String, String> scanFinding = new LinkedHashMap<>();
        scanFinding.put("kind", "finding");
        scanFinding.put("pos", "21, 20, 20");
        scanFinding.put("storedDy", "-0.5");
        scanFinding.put("authorityDy", "-0.5");
        scanFinding.put("geometricDy", "0.0");
        scanFinding.put("marker", "LIVE_SLABCHECK_WOULD_MOVE");
        scanFinding.put("failureClasses", "WOULD_MOVE");
        session.recordScanner(scanFinding);

        assertSentinelContract(session);

        // Left pending deliberately: close must emit an honest CLIENT_ONLY terminal row.
        session.recordAction(action("client", "9, 9, 9", "-0.5"), "PLAYER_AUTHORED");
        session.close();

        String jsonl = Files.readString(requested.resolve("session.jsonl"));
        require(jsonl.contains("\"attemptStatus\":\"MERGED_CLIENT_SERVER\""),
                "matching client/server observations must merge");
        require(jsonl.contains("\"attemptStatus\":\"AUTO_PROXY\""),
                "proxy evidence must remain separate");
        require(count(jsonl, "\"attemptStatus\":\"AUTO_PROXY\"") == 6,
                "one standalone proxy plus five rig cases must each form one logical attempt");
        require(jsonl.contains("\"attemptStatus\":\"GAMETEST\""),
                "GameTest evidence must remain separate");
        require(jsonl.contains("\"attemptStatus\":\"CLIENT_ONLY\""),
                "stop must finalize pending client observations");
        require(count(jsonl, "\"attemptStatus\":\"CLIENT_ONLY\"") == 2,
                "an unmatched pending client and a too-far-apart pending client must both flush"
                        + " client-only, never silently vanish or wrongly merge");
        require(count(jsonl, "\"attemptStatus\":\"MERGED_CLIENT_SERVER\"") == 5,
                "same-cell, adjacent-cell, and target-result pairs merge; two cells apart stays split");
        require(jsonl.contains("LIVE_PLACEMENT_SIDE_DY_SPLIT"),
                "raw client/server dy disagreement must be a recorded RED");
        require(jsonl.contains("LIVE_PLACEMENT_SIDE_CELL_SPLIT"),
                "an adjacent-cell client/server landing disagreement must be a recorded RED,"
                        + " not two orphaned INCONCLUSIVE rows");
        require(jsonl.contains("INFO_STORED_PUBLICATION_TIMING"),
                "transient stored publication disagreement must remain named evidence");
        require(jsonl.contains("LIVE_TARGET_RESULT_DY_SPLIT"),
                "a selected dy=-0.5 support producing dy=0 must be an explicit RED");
        require(jsonl.contains("LIVE_RIG_CASE_REFUSED")
                        && jsonl.contains("LIVE_RIG_CASE_MISMATCH")
                        && jsonl.contains("LIVE_RIG_CASE_INCONCLUSIVE")
                        && jsonl.contains("LIVE_GREEN_RIG_CASE_PROOF_COMPLETE")
                        && jsonl.contains("YELLOW_RIG_CASE_RAW_DY_EXACT_PROOF_INCOMPLETE"),
                "raw rig grades must reduce to proof-complete, RED, or honest inconclusive verdicts");
        require(count(jsonl, "\"type\":\"rig_case_verdict\"") == 5,
                "each rig case must publish exactly one terminal verdict");
        require(lineContains(jsonl, "\"rigCaseId\":\"case.exact\"", "\"finalVerdict\":\"GREEN\"")
                        && lineContains(jsonl, "\"rigCaseId\":\"case.raw-only\"",
                                "\"finalVerdict\":\"INCONCLUSIVE\""),
                "case-bound visual/contact proof may green; raw dy equality alone may not");
        require(jsonl.contains("\"type\":\"slabcheck_summary\"")
                        && jsonl.contains("\"type\":\"slabcheck_finding\""),
                "/slabcheck summary and sample findings must enter the canonical stream");
        require(jsonl.contains("LIVE_MODEL_STALE_DIVERGENT")
                        && jsonl.contains("LIVE_MODEL_STALE_ABSENT")
                        && jsonl.contains("YELLOW_MODEL_STALE_NO_BAKE_YELLOW"),
                "Sentinel red/yellow classes must reach the canonical stream");

        String actions = Files.readString(requested.resolve("actions.tsv"));
        require(actions.contains("AUTO_USEON_PROXY") && actions.contains("GAMETEST"),
                "TSV must preserve all three trusted origins");
        String mismatches = Files.readString(requested.resolve("mismatches.tsv"));
        require(mismatches.contains("LIVE_PLACEMENT_SIDE_DY_SPLIT")
                        && mismatches.contains("LIVE_PLACEMENT_SIDE_CELL_SPLIT")
                        && mismatches.contains("LIVE_TARGET_RESULT_DY_SPLIT")
                        && mismatches.contains("LIVE_MODEL_STALE_DIVERGENT")
                        && mismatches.contains("LIVE_MODEL_STALE_ABSENT")
                        && mismatches.contains("LIVE_RIG_CASE_REFUSED")
                        && mismatches.contains("LIVE_RIG_CASE_MISMATCH")
                        && mismatches.contains("LIVE_RIG_CASE_INCONCLUSIVE")
                        && mismatches.contains("LIVE_SLABCHECK_FINDINGS")
                        && mismatches.contains("LIVE_SLABCHECK_WOULD_MOVE"),
                "RED evidence must reach mismatches.tsv");
        require(!mismatches.contains("INFO_STORED_PUBLICATION_TIMING"),
                "stored publication timing must not impersonate a live dy split");
        require(!mismatches.contains("YELLOW_MODEL_STALE_NO_BAKE_YELLOW"),
                "yellow liveness evidence must not impersonate a mismatch RED");
        String summary = Files.readString(requested.resolve("summary.md"));
        require(summary.contains("placementSideDySplitRows=2")
                        && summary.contains("placementSideCellSplitRows=1")
                        && summary.contains("targetResultDySplitRows=1")
                        && summary.contains("storedPublicationTimingRows=1")
                        && summary.contains("rigCaseRows=5")
                        && summary.contains("rigCaseExactRows=2")
                        && summary.contains("rigCaseRefusedRows=1")
                        && summary.contains("rigCaseMismatchRows=1")
                        && summary.contains("rigCaseInconclusiveRows=1")
                        && summary.contains("rigCaseVerdictRows=5")
                        && summary.contains("rigCaseGreenVerdictRows=1")
                        && summary.contains("rigCaseRedVerdictRows=3")
                        && summary.contains("rigCaseInconclusiveVerdictRows=1")
                        && summary.contains("rigCasePendingVerdictRows=0")
                        && summary.contains("slabcheckRuns=1")
                        && summary.contains("slabcheckFindingRows=1")
                        && summary.contains("slabcheckWouldMoveTotal=1")
                        && summary.contains("mergedClientServerAttemptRows=5")
                        && summary.contains("autoProxyLogicalAttemptRows=6")
                        && summary.contains("gametestLogicalAttemptRows=1")
                        && summary.contains("clientOnlyLogicalAttemptRows=2")
                        && summary.contains("modelStaleDivergentRows=3")
                        && summary.contains("modelStaleAbsentRows=1"),
                "summary triage counters must match the recorded evidence");

        // A new launch may never append to old evidence, even when both sessions are schema 6.
        Schema6Session isolated = Schema6Session.open(requested, manifestFields);
        require(!isolated.directory().equals(requested)
                        && isolated.directory().getParent().equals(requested),
                "existing evidence must force a schema-6 child session");
        assertExactFiles(isolated.directory());
        isolated.close();

        Path occupied = Path.of(args[0], "occupied-" + System.nanoTime()).toAbsolutePath();
        Files.createDirectories(occupied);
        Files.writeString(occupied.resolve("unrelated.txt"), "keep");
        Schema6Session isolatedFromUnrelated = Schema6Session.open(occupied, manifestFields);
        require(!isolatedFromUnrelated.directory().equals(occupied),
                "a nonempty requested directory must never receive recorder artifacts");
        assertExactFiles(isolatedFromUnrelated.directory());
        require(Files.readString(occupied.resolve("unrelated.txt")).equals("keep"),
                "session isolation must preserve unrelated evidence");
        isolatedFromUnrelated.close();

        Path identityRoot = Path.of(args[0], "identity-" + System.nanoTime()).toAbsolutePath();
        Schema6Session identity = Schema6Session.open(identityRoot, manifestFields);
        identity.recordCursor(cursor);
        identity.recordAction(action("client", "5, 5, 5", "-0.5"), "PLAYER_AUTHORED");
        LinkedHashMap<String, String> otherPlayerServer = action("server", "5, 5, 5", "-0.5");
        otherPlayerServer.put("playerUuid", "player-two");
        otherPlayerServer.remove("clickedOwnerPos");
        otherPlayerServer.remove("clickedFace");
        identity.recordAction(otherPlayerServer, "PLAYER_AUTHORED");
        identity.close();
        String identityJsonl = Files.readString(identity.directory().resolve("session.jsonl"));
        require(!identityJsonl.contains("\"attemptStatus\":\"MERGED_CLIENT_SERVER\"")
                        && identityJsonl.contains("\"attemptStatus\":\"SERVER_ONLY\"")
                        && identityJsonl.contains("\"attemptStatus\":\"CLIENT_ONLY\"")
                        && identityJsonl.contains("\"playerUuid\":\"player-two\"")
                        && identityJsonl.contains("\"clickedOwnerPos\":\"none\""),
                "server identity must remain authoritative and must not borrow the local cursor");

        SlabModelStaleSentinel.resetCold();
        require(SlabModelStaleSentinel.armedTotalCount() == 0L
                        && SlabModelStaleSentinel.samplePassCount() == 0L,
                "fresh recorder sessions must begin with session-local Sentinel counters");

        System.out.println("SLABBED_SCHEMA6_CONTRACT PASS files=6 correlation=paired "
                + "origins=3 redaction=5 sentinel=divergent,absent,yellow flush=client-only");
    }

    private static LinkedHashMap<String, String> action(
            String side,
            String placementPos,
            String afterDy) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("side", side);
        row.put("actionType", "place_block");
        row.put("heldItem", "minecraft:stone");
        row.put("clickedOwnerPos", "1, 2, 3");
        row.put("clickedFace", "up");
        row.put("placementPos", placementPos);
        row.put("playerUuid", "player-one");
        row.put("dimensionId", "minecraft:overworld");
        row.put("afterDy", afterDy);
        row.put("afterStoredDy", afterDy);
        row.put("afterStoredDyBits", Long.toUnsignedString(
                Double.doubleToRawLongBits(Double.parseDouble(afterDy))));
        row.put("marker", "none");
        return row;
    }

    private static void recordRigAttempt(
            Schema6Session session,
            String id,
            String placementPos,
            String afterDy) throws Exception {
        LinkedHashMap<String, String> row = action("server", placementPos, afterDy);
        row.put("rigCaseId", id);
        row.put("rigLabel", "R00/C00");
        row.put("rigExpectedDy", "-1.0");
        row.put("rigExpectedDyBits", Long.toUnsignedString(
                Double.doubleToRawLongBits(-1.0d)));
        row.put("rigFace", "up");
        row.put("rigOrientation", "floor_up");
        session.recordAction(row, "AUTO_USEON_PROXY");
    }

    private static LinkedHashMap<String, String> rigCase(
            String id,
            String grade,
            String placementPos,
            boolean completeCoreProof) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("rigCaseId", id);
        row.put("rigLabel", "R00/C00");
        row.put("heldItem", "minecraft:stone");
        row.put("placementPos", placementPos);
        row.put("expectedDy", "-1.0");
        row.put("expectedDyBits", Long.toUnsignedString(
                Double.doubleToRawLongBits(-1.0d)));
        row.put("face", "up");
        row.put("orientation", "floor_up");
        row.put("grade", grade);
        row.put("observedStoredDy", "EXACT".equals(grade) ? "-1.0" : "absent");
        row.put("observedStoredDyBits", "EXACT".equals(grade)
                ? Long.toUnsignedString(Double.doubleToRawLongBits(-1.0d)) : "absent");
        row.put("collisionStatus", completeCoreProof ? "EXACT" : "MISSING");
        row.put("stabilityStatus", completeCoreProof ? "EXACT" : "MISSING");
        row.put("reason", "contract_" + grade.toLowerCase(java.util.Locale.ROOT));
        return row;
    }

    private static void assertSentinelContract(Schema6Session session) throws Exception {
        SlabModelStaleSentinel.resetCold();
        BlockPos divergent = new BlockPos(10, 20, 30);
        List<LinkedHashMap<String, String>> rows = new ArrayList<>();
        SlabModelStaleSentinel.armForTest(divergent, null, 0L);
        SlabModelStaleSentinel.recordBakeForTest(divergent, 0.0f, 10L);
        SlabModelStaleSentinel.sampleForTest(divergent, -0.5f, 100L, rows::add);
        SlabModelStaleSentinel.sampleForTest(divergent, -0.5f, 120L, rows::add);
        require(countKind(rows, SlabModelStaleSentinel.KIND_DIVERGENT) == 0,
                "a late mismatch must not borrow persistence time from arming");
        SlabModelStaleSentinel.sampleForTest(divergent, -0.5f, 200L, rows::add);
        require(countKind(rows, SlabModelStaleSentinel.KIND_DIVERGENT) == 1,
                "persistent divergent bake must emit one RED");
        SlabModelStaleSentinel.sampleForTest(divergent, -0.5f, 220L, rows::add);
        require(countKind(rows, SlabModelStaleSentinel.KIND_DIVERGENT) == 1,
                "a latched RED must dedupe");

        BlockPos absent = new BlockPos(11, 20, 30);
        SlabModelStaleSentinel.armForTest(absent, 0.0f, 0L);
        SlabModelStaleSentinel.sampleForTest(absent, -0.5f, 100L, rows::add);
        SlabModelStaleSentinel.sampleForTest(absent, -0.5f, 120L, rows::add);
        SlabModelStaleSentinel.sampleForTest(absent, -0.5f, 200L, rows::add);
        require(countKind(rows, SlabModelStaleSentinel.KIND_ABSENT) == 1,
                "moved baseline with no new bake must emit one RED");

        BlockPos yellowThenRed = new BlockPos(12, 20, 30);
        SlabModelStaleSentinel.armForTest(yellowThenRed, null, 0L);
        SlabModelStaleSentinel.sampleForTest(yellowThenRed, -0.5f, 100L, rows::add);
        require(countKind(rows, SlabModelStaleSentinel.KIND_NO_BAKE_YELLOW) == 1,
                "no-baseline/no-bake must be yellow, not red");
        SlabModelStaleSentinel.recordBakeForTest(yellowThenRed, 0.0f, 20L);
        SlabModelStaleSentinel.sampleForTest(yellowThenRed, -0.5f, 120L, rows::add);
        SlabModelStaleSentinel.sampleForTest(yellowThenRed, -0.5f, 140L, rows::add);
        require(countKind(rows, SlabModelStaleSentinel.KIND_DIVERGENT) == 1,
                "yellow time must not count toward a later mismatch persistence window");
        SlabModelStaleSentinel.sampleForTest(yellowThenRed, -0.5f, 220L, rows::add);
        require(countKind(rows, SlabModelStaleSentinel.KIND_DIVERGENT) == 2,
                "yellow must not suppress a later real RED");

        BlockPos clearedStreak = new BlockPos(13, 20, 30);
        SlabModelStaleSentinel.armForTest(clearedStreak, null, 0L);
        SlabModelStaleSentinel.recordBakeForTest(clearedStreak, 0.0f, 10L);
        SlabModelStaleSentinel.sampleForTest(clearedStreak, -0.5f, 100L, rows::add);
        SlabModelStaleSentinel.recordBakeForTest(clearedStreak, -0.5f, 20L);
        SlabModelStaleSentinel.sampleForTest(clearedStreak, -0.5f, 120L, rows::add);
        SlabModelStaleSentinel.recordBakeForTest(clearedStreak, 0.0f, 30L);
        SlabModelStaleSentinel.sampleForTest(clearedStreak, -0.5f, 140L, rows::add);
        SlabModelStaleSentinel.sampleForTest(clearedStreak, -0.5f, 220L, rows::add);
        require(countKind(rows, SlabModelStaleSentinel.KIND_DIVERGENT) == 2,
                "a cleared mismatch must start a fresh persistence window");
        SlabModelStaleSentinel.sampleForTest(clearedStreak, -0.5f, 240L, rows::add);
        require(countKind(rows, SlabModelStaleSentinel.KIND_DIVERGENT) == 3,
                "a reappearing mismatch may RED only after its fresh full window");

        BlockPos newestWins = new BlockPos(14, 20, 30);
        int before = rows.size();
        SlabModelStaleSentinel.armForTest(newestWins, null, 0L);
        SlabModelStaleSentinel.recordBakeForTest(newestWins, 0.0f, 10L);
        SlabModelStaleSentinel.recordBakeForTest(newestWins, -0.5f, 20L);
        SlabModelStaleSentinel.sampleForTest(newestWins, -0.5f, 100L, rows::add);
        SlabModelStaleSentinel.sampleForTest(newestWins, -0.5f, 120L, rows::add);
        require(rows.size() == before, "the latest captured bake must win for one armed cell");

        for (LinkedHashMap<String, String> row : rows) {
            session.recordSentinel(row);
        }
        session.updateSentinelLiveness(
                SlabModelStaleSentinel.armedTotalCount(),
                SlabModelStaleSentinel.samplePassCount());
        SlabModelStaleSentinel.resetCold();
    }

    private static long countKind(List<LinkedHashMap<String, String>> rows, String kind) {
        return rows.stream().filter(row -> kind.equals(row.get("kind"))).count();
    }

    private static void assertExactFiles(Path directory) throws Exception {
        List<String> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }
        List<String> expected = Schema6Session.FILE_NAMES.stream().sorted().toList();
        require(files.equals(expected), "expected exactly six schema artifacts, got " + files);
    }

    private static void assertManifestRedaction(Path directory) throws Exception {
        String manifest = Files.readString(directory.resolve("manifest.json"));
        require(manifest.contains("\"schemaVersion\":\"6\"")
                        && manifest.contains("--accessToken [REDACTED]")
                        && manifest.contains("--uuid [REDACTED]")
                        && manifest.contains("--xuid [REDACTED]")
                        && manifest.contains("--clientId [REDACTED]")
                        && manifest.contains("--session [REDACTED]")
                        && manifest.contains("--username TestPlayer")
                        && manifest.contains("[HOME]/private-profile"),
                "manifest must preserve useful identity while redacting secrets and home path");
        for (String secret : List.of(
                "super-secret-jwt",
                "11112222333344445555666677778888",
                "9999888877776666",
                "client-secret",
                "session-secret",
                "equals-jwt",
                "equals-uuid",
                "equals-xuid",
                "equals-client",
                "equals-session",
                System.getProperty("user.home") + "/private-profile")) {
            require(!manifest.contains(secret), "manifest leaked secret/path: " + secret);
        }
    }

    private static void assertHeader(Path path, String expected) throws Exception {
        String first = Files.readAllLines(path).get(0);
        require(expected.equals(first), "header mismatch for " + path.getFileName());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertOriginScopeContract() {
        SlabbedRecorder.resetOriginScopesForTest();
        var first = new com.slabbed.util.SlabbedDiagnosticsBridge.ActionOriginContext(
                "player-one", "minecraft:overworld", new BlockPos(30, 40, 50));
        var second = new com.slabbed.util.SlabbedDiagnosticsBridge.ActionOriginContext(
                "player-two", "minecraft:the_nether", new BlockPos(31, 40, 50));
        var firstScope = SlabbedRecorder.enterActionOrigin("AUTO_USEON_PROXY", first);
        var secondScope = SlabbedRecorder.enterActionOrigin("AUTO_USEON_PROXY", second);
        require("PLAYER_AUTHORED".equals(SlabbedRecorder.resolveOriginForTest(
                        "PLAYER_AUTHORED",
                        originRow("client", "unrelated", "minecraft:overworld", "30, 40, 50"))),
                "a proxy scope must not relabel another player's action");
        require("AUTO_USEON_PROXY".equals(SlabbedRecorder.resolveOriginForTest(
                        "PLAYER_AUTHORED",
                        originRow("client", "player-one", "minecraft:overworld", "30, 40, 50"))),
                "an older overlapping scope must still match its exact client action");
        firstScope.close();
        require("AUTO_USEON_PROXY".equals(SlabbedRecorder.resolveOriginForTest(
                        "PLAYER_AUTHORED",
                        originRow("server", "player-one", "minecraft:overworld", "30, 40, 50"))),
                "closing the UI scope must retain the exact delayed server half");
        require("AUTO_USEON_PROXY".equals(SlabbedRecorder.resolveOriginForTest(
                        "PLAYER_AUTHORED",
                        originRow("client", "player-two", "minecraft:the_nether", "31, 40, 50"))),
                "the newest overlapping scope must match its own action");
        secondScope.close();
        require("AUTO_USEON_PROXY".equals(SlabbedRecorder.resolveOriginForTest(
                        "PLAYER_AUTHORED",
                        originRow("server", "player-two", "minecraft:the_nether", "31, 40, 50"))),
                "a second delayed server half must retain its own scoped origin");
        require(SlabbedRecorder.resolveOriginForTest(
                        "unknown", originRow("server", "other", "other", "0, 0, 0")) == null,
                "unknown origins must not default to player-authored proof");
        SlabbedRecorder.resetOriginScopesForTest();

        var rigContext = new com.slabbed.util.SlabbedDiagnosticsBridge.ActionOriginContext(
                "rig-player",
                "minecraft:overworld",
                new BlockPos(70, 80, 90),
                "mega.minecraft.stone.dy_n1p0.face_up",
                "R02/C07",
                Double.doubleToRawLongBits(-1.0d),
                net.minecraft.core.Direction.UP,
                "floor_up");
        var rigScope = SlabbedRecorder.enterActionOrigin("AUTO_USEON_PROXY", rigContext);
        LinkedHashMap<String, String> rigRow = originRow(
                "server", "rig-player", "minecraft:overworld", "70, 80, 90");
        require("AUTO_USEON_PROXY".equals(SlabbedRecorder.resolveOriginForTest(
                        "PLAYER_AUTHORED", rigRow))
                        && "mega.minecraft.stone.dy_n1p0.face_up".equals(
                                rigRow.get("rigCaseId"))
                        && "R02/C07".equals(rigRow.get("rigLabel"))
                        && "up".equals(rigRow.get("rigFace"))
                        && "floor_up".equals(rigRow.get("rigOrientation")),
                "an exact proxy lease must enrich its server row with durable rig identity");
        rigScope.close();
        SlabbedRecorder.resetOriginScopesForTest();

        BlockPos replacementCell = new BlockPos(40, 50, 60);
        var replacement = new com.slabbed.util.SlabbedDiagnosticsBridge.ActionOriginContext(
                "replacement-player", "minecraft:overworld", replacementCell);
        var replacementScope = SlabbedRecorder.enterActionOrigin("AUTO_USEON_PROXY", replacement);
        require("PLAYER_AUTHORED".equals(SlabbedRecorder.resolveOriginForTest(
                        "PLAYER_AUTHORED",
                        originRow("client", "replacement-player", "minecraft:overworld", "40, 51, 60"))),
                "a replace-in-place proxy lease must not bind the old adjacent-cell guess");
        require("AUTO_USEON_PROXY".equals(SlabbedRecorder.resolveOriginForTest(
                        "PLAYER_AUTHORED",
                        originRow("client", "replacement-player", "minecraft:overworld", "40, 50, 60"))),
                "a replace-in-place row must match its resolved BlockPlaceContext cell");
        replacementScope.close();
        require("AUTO_USEON_PROXY".equals(SlabbedRecorder.resolveOriginForTest(
                        "PLAYER_AUTHORED",
                        originRow("server", "replacement-player", "minecraft:overworld", "40, 50, 60"))),
                "the authoritative replacement-cell row must keep the proxy origin");
        SlabbedRecorder.resetOriginScopesForTest();

        var synchronous = new com.slabbed.util.SlabbedDiagnosticsBridge.ActionOriginContext(
                "sync-player", "minecraft:overworld", new BlockPos(41, 50, 60));
        var synchronousScope = SlabbedRecorder.enterActionOrigin("AUTO_USEON_PROXY", synchronous);
        require("AUTO_USEON_PROXY".equals(SlabbedRecorder.resolveOriginForTest(
                        "PLAYER_AUTHORED",
                        originRow("server", "sync-player", "minecraft:overworld", "41, 50, 60"))),
                "a synchronous server proxy row must receive its scoped origin");
        synchronousScope.close();
        require("PLAYER_AUTHORED".equals(SlabbedRecorder.resolveOriginForTest(
                        "PLAYER_AUTHORED",
                        originRow("server", "sync-player", "minecraft:overworld", "41, 50, 60"))),
                "a closed server-complete proxy scope must not leak onto a later manual action");
        SlabbedRecorder.resetOriginScopesForTest();

        LinkedHashMap<String, String> serverAim = new LinkedHashMap<>();
        serverAim.put("side", "server");
        serverAim.put("clickedOwnerPos", "none");
        serverAim.put("clickedFace", "none");
        SlabbedRecorder.appendClientTargetDefaults(
                serverAim,
                new BlockPos(1, 2, 3),
                new BlockPos(1, 3, 3),
                net.minecraft.core.Direction.UP,
                "UPPER");
        require("none".equals(serverAim.get("clickedOwnerPos"))
                        && "none".equals(serverAim.get("clickedFace")),
                "an authoritative server row must never borrow the global client aim");
        LinkedHashMap<String, String> clientAim = new LinkedHashMap<>();
        clientAim.put("side", "client");
        SlabbedRecorder.appendClientTargetDefaults(
                clientAim,
                new BlockPos(1, 2, 3),
                new BlockPos(1, 3, 3),
                net.minecraft.core.Direction.UP,
                "UPPER");
        require("1, 2, 3".equals(clientAim.get("clickedOwnerPos"))
                        && "up".equals(clientAim.get("clickedFace")),
                "client-only target enrichment must remain available");
    }

    private static LinkedHashMap<String, String> originRow(
            String side,
            String playerUuid,
            String dimensionId,
            String placementPos) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("side", side);
        row.put("playerUuid", playerUuid);
        row.put("dimensionId", dimensionId);
        row.put("placementPos", placementPos);
        return row;
    }

    private static int count(String haystack, String needle) {
        int found = 0;
        int from = 0;
        while ((from = haystack.indexOf(needle, from)) >= 0) {
            found++;
            from += needle.length();
        }
        return found;
    }

    private static boolean lineContains(String text, String first, String second) {
        for (String line : text.split("\\R")) {
            if (line.contains(first) && line.contains(second)) {
                return true;
            }
        }
        return false;
    }
}
