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
        String command = "net.minecraft.client.main.Main --username Maintainer --version 1.20.1 "
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

        LinkedHashMap<String, String> cursor = new LinkedHashMap<>();
        cursor.put("finalHitPos", "1, 2, 3");
        cursor.put("hitFace", "up");
        cursor.put("heldItem", "minecraft:stone");
        cursor.put("playerUuid", "player-one");
        cursor.put("dimensionId", "minecraft:overworld");
        session.recordCursor(cursor);

        session.recordAction(action("client", "1, 3, 3", "-0.5"), "PLAYER_AUTHORED");
        session.updateSentinelLiveness(1L, 1L);
        session.recordAction(action("server", "1, 3, 3", "-0.5"), "PLAYER_AUTHORED");
        session.recordAction(action("client", "2, 3, 3", "-0.5"), "PLAYER_AUTHORED");
        session.recordAction(action("server", "2, 3, 3", "0.0"), "PLAYER_AUTHORED");
        session.recordAction(action("client", "3, 3, 3", "-0.5"), "AUTO_USEON_PROXY");
        LinkedHashMap<String, String> proxyServer = action("server", "3, 3, 3", "0.0");
        proxyServer.put("afterStoredDy", "-0.5");
        proxyServer.put("afterStoredDyBits", Long.toUnsignedString(
                Double.doubleToRawLongBits(-0.5d)));
        session.recordAction(proxyServer, "AUTO_USEON_PROXY");
        session.recordAction(action("server", "4, 3, 3", "-0.5"), "GAMETEST");

        LinkedHashMap<String, String> outline = new LinkedHashMap<>();
        outline.put("renderedOutlinePos", "1, 3, 3");
        outline.put("cursorFinalHitPos", "1, 3, 3");
        outline.put("renderedOutlineState", "Block{minecraft:stone}");
        outline.put("renderedOutlineBounds", "[0,0,0 -> 1,0.5,1]");
        outline.put("cursorOutlineBounds", "[0,0,0 -> 1,0.5,1]");
        outline.put("marker", "none");
        session.recordRenderedOutline(outline);

        assertSentinelContract(session);

        // Left pending deliberately: close must emit an honest CLIENT_ONLY terminal row.
        session.recordAction(action("client", "9, 9, 9", "-0.5"), "PLAYER_AUTHORED");
        session.close();

        String jsonl = Files.readString(requested.resolve("session.jsonl"));
        require(jsonl.contains("\"attemptStatus\":\"MERGED_CLIENT_SERVER\""),
                "matching client/server observations must merge");
        require(jsonl.contains("\"attemptStatus\":\"AUTO_PROXY\""),
                "proxy evidence must remain separate");
        require(count(jsonl, "\"attemptStatus\":\"AUTO_PROXY\"") == 1,
                "two-sided proxy evidence must form one logical attempt");
        require(jsonl.contains("\"attemptStatus\":\"GAMETEST\""),
                "GameTest evidence must remain separate");
        require(jsonl.contains("\"attemptStatus\":\"CLIENT_ONLY\""),
                "stop must finalize pending client observations");
        require(jsonl.contains("LIVE_PLACEMENT_SIDE_DY_SPLIT"),
                "raw client/server dy disagreement must be a recorded RED");
        require(jsonl.contains("LIVE_MODEL_STALE_DIVERGENT")
                        && jsonl.contains("LIVE_MODEL_STALE_ABSENT")
                        && jsonl.contains("YELLOW_MODEL_STALE_NO_BAKE_YELLOW"),
                "Sentinel red/yellow classes must reach the canonical stream");

        String actions = Files.readString(requested.resolve("actions.tsv"));
        require(actions.contains("AUTO_USEON_PROXY") && actions.contains("GAMETEST"),
                "TSV must preserve all three trusted origins");
        String mismatches = Files.readString(requested.resolve("mismatches.tsv"));
        require(mismatches.contains("LIVE_PLACEMENT_SIDE_DY_SPLIT")
                        && mismatches.contains("LIVE_MODEL_STALE_DIVERGENT")
                        && mismatches.contains("LIVE_MODEL_STALE_ABSENT"),
                "RED evidence must reach mismatches.tsv");
        require(!mismatches.contains("YELLOW_MODEL_STALE_NO_BAKE_YELLOW"),
                "yellow liveness evidence must not impersonate a mismatch RED");
        String summary = Files.readString(requested.resolve("summary.md"));
        require(summary.contains("placementSideDySplitRows=2")
                        && summary.contains("mergedClientServerAttemptRows=2")
                        && summary.contains("autoProxyLogicalAttemptRows=1")
                        && summary.contains("gametestLogicalAttemptRows=1")
                        && summary.contains("clientOnlyLogicalAttemptRows=1")
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
                        && manifest.contains("--username Maintainer")
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
}
