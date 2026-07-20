package com.slabbed.util;

import com.slabbed.Slabbed;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Live cursor / placement-intent capture: a dormant, off-by-default diagnostic tool that ships in
 * every jar (never compiled out — the same "debug tooling always present, off by default,
 * player-toggleable" convention {@code /slabdev} uses on this branch). It writes a per-session trail
 * of every crosshair frame, rendered outline, and placement action, plus a redacted {@code
 * manifest.json}, under {@code <gameDir>/live-cursor-recorder/} (overridable via {@code
 * -Dslabbed.liveCursorIntentRecorderDir}).
 *
 * <p>Revived from git history: this class was a full 539-line implementation gutted to a 43-line
 * inert stub in {@code ec6e2429} purely for jar-size hygiene, whose own message said "restore from
 * git history to revive." The public API ({@link #enabled()}, {@link #lastCursorRowId()}, {@link
 * #recordCursor}, {@link #recordRenderedOutline}, {@link #recordAction}, {@link
 * #flushSummaryForTests()}, {@link #resetForTests()}) is byte-compatible with the stub, so the two
 * live mixin call sites ({@code GameRendererCrosshairRetargetMixin}, {@code
 * BlockItemPlacementIntentMixin}) needed zero changes.
 *
 * <p>Enable/disable at runtime via {@code /slabdev record on|off|toggle} — {@link #toggle()} flips a
 * {@code volatile boolean}; the JVM property {@code -Dslabbed.liveCursorIntentRecorder} only sets the
 * INITIAL value so a dev/CI launch can start already-recording. Every {@code record*} /
 * {@link #flushSummaryForTests()} method short-circuits on {@code !enabled()} as its first statement
 * with zero allocation before it — this project has shipped a per-call-site lag bug from that exact
 * mistake twice, so that ordering is load-bearing, not cosmetic.
 */
public final class LiveCursorIntentRecorder {
    public static final String ENABLE_PROPERTY = "slabbed.liveCursorIntentRecorder";
    public static final String DIR_PROPERTY = "slabbed.liveCursorIntentRecorderDir";

    private static final Object LOCK = new Object();
    private static final AtomicLong ROW_IDS = new AtomicLong();
    private static final AtomicLong LOGICAL_ATTEMPT_IDS = new AtomicLong();

    // Runtime-toggleable (see toggle()/enabled()) — the JVM property only sets the INITIAL value, so a
    // dev/CI launch can start already-enabled, but a player reaches this the same way as everything
    // else in Slabbed's debug surface: a slash command (/slabdev record), never a JVM flag.
    private static volatile boolean enabled = Boolean.getBoolean(ENABLE_PROPERTY);
    private static final String SCHEMA_VERSION = "6";
    private static final String RECORDER_VERSION =
            "26.2-recorder-truth-v8-logical-attempts";
    private static final String RUN_ID = UUID.randomUUID().toString();
    private static final String ACTIONS_HEADER =
            "actionId\tcursorRowId\tactionType\tactionOrigin\theldItem\tclickedOwnerPos\tclickedFace\tplacementPos"
                    + "\texpectedAfterDy\tafterDy\texpectedAfterLaneKind\tafterLaneKind\tmarker"
                    + "\tafterStoredDy\tafterStoredDyBits\tpairPos\tpairPart\tpairState"
                    + "\tpairAfterDy\tpairStoredDy\tpairStoredDyBits"
                    + "\tlogicalAttemptId\tphase\tplayerProof";
    private static final String[] C3_ACTION_FIELDS = {
            "afterStoredDy", "afterStoredDyBits", "pairPos", "pairPart", "pairState",
            "pairAfterDy", "pairStoredDy", "pairStoredDyBits"
    };

    /** Machine-readable authorship boundary for placement action rows. */
    public enum ActionOrigin {
        PLAYER_AUTHORED,
        AUTO_USEON_PROXY;

        public String wireName() {
            return name();
        }
    }

    /**
     * Thread-local stack rather than a single flag: command builders can nest shared proxy helpers
     * without a premature inner close relabelling the still-running outer placement as player input.
     */
    private static final ThreadLocal<ArrayDeque<ActionOrigin>> ACTION_ORIGINS =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<UsePacketScope>> USE_PACKET_SCOPES =
            ThreadLocal.withInitial(ArrayDeque::new);

    private static Path sessionDir;
    private static boolean startLogged;
    private static boolean manifestWritten;
    private static boolean shutdownHookRegistered;
    private static long cursorRows;
    private static long actionRows;
    private static long playerAuthoredActionRows;
    private static long autoUseOnProxyActionRows;
    private static long ghostSurfaceRows;
    private static long hiddenOwnerRows;
    private static long outlineRaycastSplitRows;
    private static long renderedOutlineRows;
    private static long renderedOutlineLargeBoundsRows;
    private static long renderedOutlineReplayBoundsSplitRows;
    private static long renderedOutlineTargetSplitRows;
    private static long placementExpectedDyMismatchRows;
    private static long placementUnclassifiedFailureRows;
    private static long placementExpectedLaneMismatchRows;
    private static long loweredSideSlabPlacementVanillaDyRows;
    private static long collisionIteratorTargetMissRows;
    private static long collisionIteratorTargetPresentRows;
    private static long greenCursorTriadRows;
    private static long greenPlacementAuthoringRows;
    private static long placementVerdictGreenRows;
    private static long placementVerdictRedRows;
    private static long placementVerdictInconclusiveRows;
    private static long placementVerdictExpectedRefusalRows;
    private static long placementVerdictUnclassifiedFailureRows;
    private static long logicalAttemptRows;
    private static long mergedClientServerAttemptRows;
    private static long autoProxyLogicalAttemptRows;
    private static long serverOnlyLogicalAttemptRows;
    private static long clientOnlyLogicalAttemptRows;
    private static long playerProofLogicalAttemptRows;
    private static long logicalAttemptVerdictGreenRows;
    private static long logicalAttemptVerdictRedRows;
    private static long logicalAttemptVerdictInconclusiveRows;
    private static long logicalAttemptVerdictExpectedRefusalRows;
    private static long logicalAttemptVerdictUnclassifiedFailureRows;
    private static long playerProofGreenLogicalAttemptRows;
    private static long modelStaleDivergentRows;
    private static long modelStaleAbsentRows;
    private static long modelStaleYellowRows;
    private static long breakRows;
    private static long placementSideDySplitRows;
    private static long ensembleClashRows;
    private static long ensembleOccludedOccupancyInfoRows;
    private static final long PAIR_WINDOW_NANOS = 1_000_000_000L;
    private static final int MAX_PENDING_CLIENT_ATTEMPTS = 256;
    private static final LinkedHashMap<PlacementAttemptKey, ArrayDeque<PendingClientAttempt>>
            PENDING_CLIENT_ATTEMPTS = new LinkedHashMap<>();
    private static int pendingClientAttemptCount;
    private static long lastCursorRowId;
    private static LinkedHashMap<String, String> lastCursorRow;

    private record PlacementAttemptKey(
            String packetSequence,
            String actionType,
            String heldItem,
            String clickedOwnerPos,
            String clickedFace,
            String placementPos,
            String rigCaseId,
            String playerId,
            String dimensionId) {
    }

    private record PendingClientAttempt(
            String logicalAttemptId,
            long recordedNanos,
            LinkedHashMap<String, String> row) {
    }

    private LiveCursorIntentRecorder() {
    }

    /**
     * One client or server observation of a vanilla use-item-on packet. Existing action producers
     * claim the innermost scope through {@link #recordAction}; packet-boundary hooks emit a generic
     * row only when the scope remains unclaimed.
     */
    public static final class UsePacketScope implements AutoCloseable {
        private final String side;
        private final int sequence;
        private final String playerId;
        private final String dimensionId;
        private boolean claimed;
        private boolean closed;

        private UsePacketScope(
                String side,
                int sequence,
                String playerId,
                String dimensionId) {
            this.side = side;
            this.sequence = sequence;
            this.playerId = normalizeScopeIdentity(playerId);
            this.dimensionId = normalizeScopeIdentity(dimensionId);
        }

        public boolean claimed() {
            return claimed;
        }

        @Override
        public void close() {
            if (closed) {
                throw new IllegalStateException("use packet scope already closed");
            }
            ArrayDeque<UsePacketScope> scopes = USE_PACKET_SCOPES.get();
            UsePacketScope removed = scopes.pollLast();
            if (removed != this) {
                scopes.clear();
                USE_PACKET_SCOPES.remove();
                throw new IllegalStateException("use packet scope closed out of order");
            }
            closed = true;
            if (scopes.isEmpty()) {
                USE_PACKET_SCOPES.remove();
            }
        }
    }

    /** Opens a nested-safe packet correlation scope at an active client/server use boundary. */
    public static UsePacketScope openUsePacketScope(
            String side,
            int sequence,
            String playerId,
            String dimensionId) {
        if (!"client".equals(side) && !"server".equals(side)) {
            throw new IllegalArgumentException("use packet scope side must be client or server");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("use packet sequence must be nonnegative");
        }
        UsePacketScope scope = new UsePacketScope(side, sequence, playerId, dimensionId);
        USE_PACKET_SCOPES.get().addLast(scope);
        return scope;
    }

    /** Defaults to real player input whenever no explicit synthetic command scope is active. */
    public static ActionOrigin currentActionOrigin() {
        ActionOrigin origin = ACTION_ORIGINS.get().peekLast();
        return origin == null ? ActionOrigin.PLAYER_AUTHORED : origin;
    }

    /** Runs {@code action} under a nested-safe, exception-safe machine-readable origin scope. */
    public static void withActionOrigin(ActionOrigin origin, Runnable action) {
        if (origin == null) {
            throw new IllegalArgumentException("action origin must not be null");
        }
        ArrayDeque<ActionOrigin> stack = ACTION_ORIGINS.get();
        stack.addLast(origin);
        try {
            action.run();
        } finally {
            ActionOrigin removed = stack.removeLast();
            if (removed != origin) {
                stack.clear();
                ACTION_ORIGINS.remove();
                throw new IllegalStateException("action origin scope closed out of order");
            }
            if (stack.isEmpty()) {
                ACTION_ORIGINS.remove();
            }
        }
    }

    public static boolean enabled() {
        return enabled;
    }

    /**
     * Flip the recorder on/off at runtime (the {@code /slabdev record} slash command).
     * Turning ON calls {@link #bootstrap()} so a session that never set the JVM property still gets a
     * real output directory (and a fresh {@code manifest.json}) instead of silently no-oping; turning
     * OFF calls {@link #recordShutdown()} so the summary is flushed immediately rather than waiting
     * for the JVM shutdown hook. Returns the new state.
     */
    public static boolean toggle() {
        // recordShutdown()/bootstrap() both early-return on !enabled, so the flush must happen BEFORE
        // flipping the flag off, and bootstrap AFTER flipping it on — not the other way around.
        if (enabled) {
            recordShutdown();
            enabled = false;
        } else {
            enabled = true;
            bootstrap();
        }
        return enabled;
    }

    /** Display string of the recorder's current output directory ("not started" before the first enable). */
    public static String currentLogPathDisplay() {
        synchronized (LOCK) {
            return sessionDir == null ? "not started" : sessionDir.toAbsolutePath().toString();
        }
    }

    /**
     * Lazily initialize the output directory + manifest for the current session. No-ops fast when the
     * recorder is disabled. Safe to call repeatedly — the directory/manifest are created once, and the
     * shutdown hook is registered once.
     */
    public static void bootstrap() {
        if (!enabled) {
            return;
        }
        synchronized (LOCK) {
            registerShutdownHook();
            try {
                sessionDir();
            } catch (IOException e) {
                Slabbed.LOGGER.warn("[LIVE_CURSOR_INTENT_RECORDER_IO_ERROR] bootstrap error={}", e.toString());
            }
        }
    }

    /** Flush the summary on session end / toggle-off. No-ops fast when disabled. */
    public static void recordShutdown() {
        if (!enabled) {
            return;
        }
        synchronized (LOCK) {
            finalizeAllPendingClientAttempts();
            writeSummary();
        }
    }

    public static long lastCursorRowId() {
        synchronized (LOCK) {
            return Math.max(0L, lastCursorRowId);
        }
    }

    public static void recordCursor(LinkedHashMap<String, String> fields) {
        if (!enabled()) {
            return;
        }
        LinkedHashMap<String, String> row = copy(fields);
        row.putIfAbsent("type", "cursor");
        synchronized (LOCK) {
            row.put("rowId", Long.toString(ROW_IDS.incrementAndGet()));
            row.put("recordedAt", Instant.now().toString());
            String markers = cursorMarkers(row);
            row.put("mismatchMarker", markers);
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
        synchronized (LOCK) {
            row.put("outlineRenderId", Long.toString(ROW_IDS.incrementAndGet()));
            row.put("cursorRowId", Long.toString(Math.max(0L, lastCursorRowId)));
            row.put("recordedAt", Instant.now().toString());
            appendLastCursorFields(row);
            String markers = renderedOutlineMarkers(row);
            row.put("marker", markers);
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

    /**
     * Sentinel rows ({@link SlabModelStaleSentinel}) are always session-recorded, while the shared
     * diagnostic-severity policy decides whether they also belong in the red-only mismatch stream.
     */
    public static void recordSentinel(LinkedHashMap<String, String> fields) {
        if (!enabled()) {
            return;
        }
        LinkedHashMap<String, String> row = copy(fields);
        row.putIfAbsent("type", "model_stale_sentinel");
        synchronized (LOCK) {
            row.put("rowId", Long.toString(ROW_IDS.incrementAndGet()));
            row.put("recordedAt", Instant.now().toString());
            String kind = row.getOrDefault("kind", "unknown");
            SlabModelStaleSentinel.DiagnosticSeverity severity =
                    SlabModelStaleSentinel.diagnosticSeverity(kind);
            String marker = severity == SlabModelStaleSentinel.DiagnosticSeverity.INFO
                    ? "INFO_" + kind
                    : "LIVE_" + kind;
            row.put("severity", severity.wireName());
            row.put("marker", marker);
            switch (severity) {
                case RED -> {
                    if (kind.startsWith("ENSEMBLE_")) {
                        ensembleClashRows++;
                    } else if (SlabModelStaleSentinel.KIND_DIVERGENT.equals(kind)) {
                        modelStaleDivergentRows++;
                    } else if (SlabModelStaleSentinel.KIND_ABSENT.equals(kind)) {
                        modelStaleAbsentRows++;
                    }
                }
                case INFO -> ensembleOccludedOccupancyInfoRows++;
                case YELLOW -> modelStaleYellowRows++;
            }
            writeSession(row);
            if (severity == SlabModelStaleSentinel.DiagnosticSeverity.RED) {
                writeMismatchRows(row, marker);
            }
            writeSummary();
        }
    }

    /**
     * Break capture (TEST (3)-triage upgrade): the recorder was break-blind, which caused the
     * "data-destructive downgrade" false alarm and left the tower-churn "jumping on break" report with
     * zero rows. Records the broken block + its up/down neighbors' states and dys pre-break — the
     * pop-detection cells the never-pop law cares about. Observation only; never affects the break.
     */
    public static void recordBreakEvent(net.minecraft.world.level.Level world,
                                        net.minecraft.core.BlockPos pos,
                                        net.minecraft.world.level.block.state.BlockState state,
                                        String playerName) {
        if (!enabled()) {
            return;
        }
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("type", "break");
        row.put("side", world.isClientSide() ? "client" : "server");
        row.put("player", playerName == null ? "none" : playerName);
        row.put("pos", pos.toShortString());
        row.put("state", state.toString());
        row.put("dy", String.format("%.6f", SlabSupport.getYOffset(world, pos, state)));
        net.minecraft.core.BlockPos above = pos.above();
        net.minecraft.world.level.block.state.BlockState aboveState = world.getBlockState(above);
        net.minecraft.core.BlockPos below = pos.below();
        net.minecraft.world.level.block.state.BlockState belowState = world.getBlockState(below);
        row.put("aboveState", aboveState.toString());
        row.put("aboveDy", String.format("%.6f", SlabSupport.getYOffset(world, above, aboveState)));
        row.put("belowState", belowState.toString());
        row.put("belowDy", String.format("%.6f", SlabSupport.getYOffset(world, below, belowState)));
        synchronized (LOCK) {
            row.put("rowId", Long.toString(ROW_IDS.incrementAndGet()));
            row.put("recordedAt", Instant.now().toString());
            breakRows++;
            writeSession(row);
            writeSummary();
        }
    }

    public static void recordAction(LinkedHashMap<String, String> fields) {
        if (!enabled()) {
            return;
        }
        LinkedHashMap<String, String> row = copy(fields);
        row.putIfAbsent("type", "action");
        for (String field : C3_ACTION_FIELDS) {
            row.putIfAbsent(field, "none");
        }
        synchronized (LOCK) {
            long nowNanos = System.nanoTime();
            finalizeExpiredClientAttempts(nowNanos);
            // Identity, timestamp, and shared cursor reference are one write-ordered recorder
            // transaction. Caller values are intentionally overwritten.
            row.put("actionId", Long.toString(ROW_IDS.incrementAndGet()));
            row.put("cursorRowId", Long.toString(Math.max(0L, lastCursorRowId)));
            row.put("recordedAt", Instant.now().toString());
            // Trusted scope is authoritative. A caller-provided field must never spoof a synthetic
            // command action as player-authored.
            ActionOrigin actionOrigin = currentActionOrigin();
            row.put("actionOrigin", actionOrigin.wireName());
            UsePacketScope packetScope = currentUsePacketScope();
            if (actionOrigin == ActionOrigin.PLAYER_AUTHORED && packetScope != null) {
                row.put("side", packetScope.side);
                row.put("packetSequence", Integer.toString(packetScope.sequence));
                row.put("playerUuid", packetScope.playerId);
                row.put("dimensionId", packetScope.dimensionId);
                packetScope.claimed = true;
            }
            appendActionExpectations(row);
            PlacementVerificationVerdict.Result verdict =
                    PlacementVerificationVerdict.reduce(row);
            row.putAll(verdict.canonicalFields());
            row.put("verdictMarker", verdict.finalVerdict().marker());
            String markers = actionMarkers(row);
            row.put("marker", markers);
            actionRows++;
            boolean playerAuthored = actionOrigin == ActionOrigin.PLAYER_AUTHORED;
            if (playerAuthored) {
                playerAuthoredActionRows++;
            } else {
                autoUseOnProxyActionRows++;
            }
            switch (verdict.finalVerdict()) {
                case GREEN -> placementVerdictGreenRows++;
                case RED -> placementVerdictRedRows++;
                case INCONCLUSIVE -> placementVerdictInconclusiveRows++;
                case EXPECTED_REFUSAL -> placementVerdictExpectedRefusalRows++;
                case UNCLASSIFIED_FAILURE -> placementVerdictUnclassifiedFailureRows++;
            }
            String side = row.getOrDefault("side", "");
            PlacementAttemptKey attemptKey = placementAttemptKey(row);
            PendingClientAttempt matchedClient = null;
            String logicalAttemptId;
            String phase;
            String playerProof;
            String terminalStatus = null;
            if (!playerAuthored) {
                logicalAttemptId = nextLogicalAttemptId();
                phase = "AUTO_PROXY";
                playerProof = "ABSENT";
                terminalStatus = "AUTO_PROXY";
            } else if ("client".equals(side)) {
                logicalAttemptId = nextLogicalAttemptId();
                phase = "CLIENT_PREDICTION";
                playerProof = "PRESENT";
            } else {
                matchedClient = removeMatchingClientAttempt(attemptKey, nowNanos);
                logicalAttemptId = matchedClient == null
                        ? nextLogicalAttemptId()
                        : matchedClient.logicalAttemptId();
                phase = "SERVER_AUTHORITY";
                playerProof = "PRESENT";
                terminalStatus = matchedClient == null
                        ? "SERVER_ONLY"
                        : "MERGED_CLIENT_SERVER";
            }
            // Trusted recorder state is authoritative; caller-provided correlation fields are never
            // accepted, including on synthetic proxy rows.
            row.put("logicalAttemptId", logicalAttemptId);
            row.put("phase", phase);
            row.put("playerProof", playerProof);

            if (matchedClient != null
                    && hasEvidence(matchedClient.row().get("afterDy"))
                    && hasEvidence(row.get("afterDy"))
                    && !sameEvidence(matchedClient.row().get("afterDy"), row.get("afterDy"))) {
                placementSideDySplitRows++;
                row.put("clientAfterDy", matchedClient.row().get("afterDy"));
                markers = appendMarkerToken(markers, "LIVE_PLACEMENT_SIDE_DY_SPLIT");
                row.put("marker", markers);
            }
            if (markers.contains("LIVE_PLACEMENT_EXPECTED_DY_MISMATCH")) {
                placementExpectedDyMismatchRows++;
            }
            if (markers.contains("LIVE_PLACEMENT_UNCLASSIFIED_FAILURE")) {
                placementUnclassifiedFailureRows++;
            }
            if (markers.contains("LIVE_PLACEMENT_EXPECTED_LANE_MISMATCH")) {
                placementExpectedLaneMismatchRows++;
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
            if (playerAuthored && "client".equals(side)) {
                addPendingClientAttempt(
                        attemptKey,
                        new PendingClientAttempt(logicalAttemptId, nowNanos, copy(row)));
            } else if (matchedClient != null) {
                writeLogicalAttempt(
                        matchedClient.row(),
                        row,
                        logicalAttemptId,
                        terminalStatus,
                        playerProof);
            } else {
                writeLogicalAttempt(
                        null,
                        row,
                        logicalAttemptId,
                        terminalStatus,
                        playerProof);
            }
            writeSummary();
        }
    }

    public static void flushSummaryForTests() {
        if (!enabled()) {
            return;
        }
        synchronized (LOCK) {
            finalizeAllPendingClientAttempts();
            writeSummary();
        }
    }

    public static void resetForTests() {
        synchronized (LOCK) {
            // Re-sync the volatile flag from the JVM property so a test that sets the property AFTER
            // this class was first loaded (the field is initialized once at class-init) still sees the
            // recorder as enabled. Production runtime toggling via toggle() is unaffected — tests are
            // the only caller of resetForTests().
            enabled = Boolean.getBoolean(ENABLE_PROPERTY);
            sessionDir = null;
            startLogged = false;
            manifestWritten = false;
            ROW_IDS.set(0L);
            LOGICAL_ATTEMPT_IDS.set(0L);
            cursorRows = 0L;
            actionRows = 0L;
            playerAuthoredActionRows = 0L;
            autoUseOnProxyActionRows = 0L;
            ghostSurfaceRows = 0L;
            hiddenOwnerRows = 0L;
            outlineRaycastSplitRows = 0L;
            renderedOutlineRows = 0L;
            renderedOutlineLargeBoundsRows = 0L;
            renderedOutlineReplayBoundsSplitRows = 0L;
            renderedOutlineTargetSplitRows = 0L;
            placementExpectedDyMismatchRows = 0L;
            placementUnclassifiedFailureRows = 0L;
            placementExpectedLaneMismatchRows = 0L;
            loweredSideSlabPlacementVanillaDyRows = 0L;
            collisionIteratorTargetMissRows = 0L;
            collisionIteratorTargetPresentRows = 0L;
            greenCursorTriadRows = 0L;
            greenPlacementAuthoringRows = 0L;
            placementVerdictGreenRows = 0L;
            placementVerdictRedRows = 0L;
            placementVerdictInconclusiveRows = 0L;
            placementVerdictExpectedRefusalRows = 0L;
            placementVerdictUnclassifiedFailureRows = 0L;
            logicalAttemptRows = 0L;
            mergedClientServerAttemptRows = 0L;
            autoProxyLogicalAttemptRows = 0L;
            serverOnlyLogicalAttemptRows = 0L;
            clientOnlyLogicalAttemptRows = 0L;
            playerProofLogicalAttemptRows = 0L;
            logicalAttemptVerdictGreenRows = 0L;
            logicalAttemptVerdictRedRows = 0L;
            logicalAttemptVerdictInconclusiveRows = 0L;
            logicalAttemptVerdictExpectedRefusalRows = 0L;
            logicalAttemptVerdictUnclassifiedFailureRows = 0L;
            playerProofGreenLogicalAttemptRows = 0L;
            modelStaleDivergentRows = 0L;
            modelStaleAbsentRows = 0L;
            modelStaleYellowRows = 0L;
            breakRows = 0L;
            placementSideDySplitRows = 0L;
            ensembleClashRows = 0L;
            ensembleOccludedOccupancyInfoRows = 0L;
            PENDING_CLIENT_ATTEMPTS.clear();
            pendingClientAttemptCount = 0;
            lastCursorRowId = 0L;
            lastCursorRow = null;
            ACTION_ORIGINS.remove();
            USE_PACKET_SCOPES.remove();
        }
    }

    private static LinkedHashMap<String, String> copy(LinkedHashMap<String, String> fields) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (fields != null) {
            out.putAll(fields);
        }
        return out;
    }

    private static String nextLogicalAttemptId() {
        return RUN_ID + "-attempt-" + LOGICAL_ATTEMPT_IDS.incrementAndGet();
    }

    private static PlacementAttemptKey placementAttemptKey(Map<String, String> row) {
        String packetSequence = correlationValue(row, "packetSequence");
        boolean sequenceAuthority = !"none".equals(packetSequence)
                && !"none".equals(firstCorrelationValue(
                        row, "playerUuid", "playerId", "player", "playerName"))
                && !"none".equals(firstCorrelationValue(
                        row, "dimensionId", "dimension", "level", "world"));
        return new PlacementAttemptKey(
                sequenceAuthority ? packetSequence : "none",
                sequenceAuthority ? "sequence_authority" : correlationValue(row, "actionType"),
                sequenceAuthority ? "sequence_authority" : correlationValue(row, "heldItem"),
                sequenceAuthority ? "sequence_authority" : correlationValue(row, "clickedOwnerPos"),
                sequenceAuthority ? "sequence_authority" : correlationValue(row, "clickedFace"),
                sequenceAuthority ? "sequence_authority" : correlationValue(row, "placementPos"),
                sequenceAuthority ? "sequence_authority" : correlationValue(row, "rigCaseId"),
                firstCorrelationValue(row, "playerUuid", "playerId", "player", "playerName"),
                firstCorrelationValue(row, "dimensionId", "dimension", "level", "world"));
    }

    private static UsePacketScope currentUsePacketScope() {
        return USE_PACKET_SCOPES.get().peekLast();
    }

    private static String normalizeScopeIdentity(String value) {
        return value == null || value.isBlank() ? "none" : value.trim();
    }

    private static String correlationValue(Map<String, String> row, String key) {
        String value = row.get(key);
        return hasEvidence(value) ? value.trim() : "none";
    }

    private static String firstCorrelationValue(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(key);
            if (hasEvidence(value)) {
                return value.trim();
            }
        }
        return "none";
    }

    private static void addPendingClientAttempt(
            PlacementAttemptKey key,
            PendingClientAttempt attempt) {
        while (pendingClientAttemptCount >= MAX_PENDING_CLIENT_ATTEMPTS) {
            finalizeOldestPendingClientAttempt();
        }
        PENDING_CLIENT_ATTEMPTS
                .computeIfAbsent(key, ignored -> new ArrayDeque<>())
                .addLast(attempt);
        pendingClientAttemptCount++;
    }

    private static PendingClientAttempt removeMatchingClientAttempt(
            PlacementAttemptKey key,
            long nowNanos) {
        ArrayDeque<PendingClientAttempt> attempts = PENDING_CLIENT_ATTEMPTS.get(key);
        if (attempts == null || attempts.isEmpty()) {
            return null;
        }
        PendingClientAttempt attempt = attempts.peekFirst();
        if ("none".equals(key.packetSequence())
                && nowNanos - attempt.recordedNanos() >= PAIR_WINDOW_NANOS) {
            return null;
        }
        attempts.removeFirst();
        pendingClientAttemptCount--;
        if (attempts.isEmpty()) {
            PENDING_CLIENT_ATTEMPTS.remove(key);
        }
        return attempt;
    }

    private static void finalizeExpiredClientAttempts(long nowNanos) {
        Iterator<Map.Entry<PlacementAttemptKey, ArrayDeque<PendingClientAttempt>>> entries =
                PENDING_CLIENT_ATTEMPTS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<PlacementAttemptKey, ArrayDeque<PendingClientAttempt>> entry = entries.next();
            ArrayDeque<PendingClientAttempt> attempts = entry.getValue();
            if (!"none".equals(entry.getKey().packetSequence())) {
                continue;
            }
            while (!attempts.isEmpty()
                    && nowNanos - attempts.peekFirst().recordedNanos() >= PAIR_WINDOW_NANOS) {
                PendingClientAttempt expired = attempts.removeFirst();
                pendingClientAttemptCount--;
                writeLogicalAttempt(
                        expired.row(),
                        null,
                        expired.logicalAttemptId(),
                        "CLIENT_ONLY",
                        "PRESENT");
            }
            if (attempts.isEmpty()) {
                entries.remove();
            }
        }
    }

    private static void finalizeOldestPendingClientAttempt() {
        PlacementAttemptKey oldestKey = null;
        PendingClientAttempt oldest = null;
        for (Map.Entry<PlacementAttemptKey, ArrayDeque<PendingClientAttempt>> entry
                : PENDING_CLIENT_ATTEMPTS.entrySet()) {
            PendingClientAttempt candidate = entry.getValue().peekFirst();
            if (candidate != null
                    && (oldest == null || candidate.recordedNanos() < oldest.recordedNanos())) {
                oldestKey = entry.getKey();
                oldest = candidate;
            }
        }
        if (oldest == null || oldestKey == null) {
            pendingClientAttemptCount = 0;
            PENDING_CLIENT_ATTEMPTS.clear();
            return;
        }
        ArrayDeque<PendingClientAttempt> attempts = PENDING_CLIENT_ATTEMPTS.get(oldestKey);
        attempts.removeFirst();
        pendingClientAttemptCount--;
        if (attempts.isEmpty()) {
            PENDING_CLIENT_ATTEMPTS.remove(oldestKey);
        }
        writeLogicalAttempt(
                oldest.row(),
                null,
                oldest.logicalAttemptId(),
                "CLIENT_ONLY",
                "PRESENT");
    }

    private static void finalizeAllPendingClientAttempts() {
        for (ArrayDeque<PendingClientAttempt> attempts : PENDING_CLIENT_ATTEMPTS.values()) {
            for (PendingClientAttempt attempt : attempts) {
                writeLogicalAttempt(
                        attempt.row(),
                        null,
                        attempt.logicalAttemptId(),
                        "CLIENT_ONLY",
                        "PRESENT");
            }
        }
        PENDING_CLIENT_ATTEMPTS.clear();
        pendingClientAttemptCount = 0;
    }

    private static void writeLogicalAttempt(
            LinkedHashMap<String, String> clientRow,
            LinkedHashMap<String, String> serverRow,
            String logicalAttemptId,
            String attemptStatus,
            String playerProof) {
        LinkedHashMap<String, PlacementVerificationVerdict.Component> conflicts =
                new LinkedHashMap<>();
        LinkedHashMap<String, String> evidence =
                logicalAttemptEvidence(clientRow, serverRow, conflicts);
        PlacementVerificationVerdict.Result verdict =
                PlacementVerificationVerdict.reduce(evidence);

        LinkedHashMap<String, String> terminal = new LinkedHashMap<>();
        terminal.put("type", "placement_attempt");
        terminal.put("rowId", "attempt:" + logicalAttemptId);
        terminal.put("recordedAt", Instant.now().toString());
        terminal.put("logicalAttemptId", logicalAttemptId);
        terminal.put("attemptStatus", attemptStatus);
        terminal.put("terminal", "true");
        terminal.put("clientActionId", actionIdOrNone(clientRow));
        terminal.put("serverActionId", actionIdOrNone(serverRow));
        terminal.put("actionCount", clientRow != null && serverRow != null ? "2" : "1");
        terminal.put("playerProof", playerProof);
        terminal.putAll(evidence);
        terminal.putAll(verdict.canonicalFields());
        applyLogicalAttemptConflicts(terminal, conflicts);

        PlacementVerificationVerdict.FinalVerdict finalVerdict =
                PlacementVerificationVerdict.FinalVerdict.valueOf(terminal.get("finalVerdict"));
        terminal.put("verdictMarker", finalVerdict.marker());
        terminal.put("marker", finalVerdict.marker());
        writeSession(terminal);
        if (finalVerdict == PlacementVerificationVerdict.FinalVerdict.RED
                || finalVerdict == PlacementVerificationVerdict.FinalVerdict.UNCLASSIFIED_FAILURE) {
            writeMismatchRows(terminal, finalVerdict.marker());
        }
        countLogicalAttempt(attemptStatus, playerProof, finalVerdict);
    }

    private static LinkedHashMap<String, String> logicalAttemptEvidence(
            LinkedHashMap<String, String> clientRow,
            LinkedHashMap<String, String> serverRow,
            LinkedHashMap<String, PlacementVerificationVerdict.Component> conflicts) {
        LinkedHashMap<String, String> evidence = new LinkedHashMap<>();

        for (String field : new String[]{
                "packetSequence", "playerUuid", "dimensionId",
                "actionType", "heldItem", "clickedOwnerPos", "clickedFace", "clickedHitVec",
                "placementPos",
                "rigCaseId", "placementRoute", "landingAuthority", "expectedAfterDy", "intentDy",
                "expectedAfterLaneKind", "expectedResult", "placementContract", "refusalContract",
                "expectedRefusalReason", "clickedOwnerLaneKind"
        }) {
            selectAttemptEvidence(
                    evidence,
                    field,
                    serverRow,
                    clientRow,
                    PlacementVerificationVerdict.Component.PLACED,
                    conflicts);
        }

        for (String field : new String[]{
                "beforeState", "beforeDy", "beforeStoredDy",
                "validationDecision", "handlerDecision", "functionalOutcome"
        }) {
            selectAuthoritativeAttemptObservation(evidence, field, serverRow, clientRow);
        }

        for (String field : new String[]{
                "actualResult", "actualRefusalReason", "afterDy", "afterState", "afterLaneKind",
                "stabilityVerdict"
        }) {
            PlacementVerificationVerdict.Component component =
                    "stabilityVerdict".equals(field)
                            ? PlacementVerificationVerdict.Component.STABILITY
                            : PlacementVerificationVerdict.Component.PLACED;
            selectAttemptEvidence(evidence, field, serverRow, clientRow, component, conflicts);
        }

        String serverStoredDy = firstAttemptEvidence(serverRow, "storedDy", "afterStoredDy");
        String clientStoredDy = firstAttemptEvidence(clientRow, "storedDy", "afterStoredDy");
        detectAttemptConflict(
                "storedDy",
                serverStoredDy,
                clientStoredDy,
                PlacementVerificationVerdict.Component.ANCHOR,
                conflicts);
        String storedDy = hasEvidence(serverStoredDy) ? serverStoredDy : clientStoredDy;
        if (hasEvidence(storedDy)) {
            evidence.put("afterStoredDy", storedDy);
        }

        for (String field : new String[]{
                "modelDy", "collisionDy", "raycastDy", "outlineDy",
                "expectedSupportPlane", "actualContactPlane", "seatError"
        }) {
            PlacementVerificationVerdict.Component component = switch (field) {
                case "modelDy" -> PlacementVerificationVerdict.Component.MODEL;
                case "raycastDy" -> PlacementVerificationVerdict.Component.RAYCAST;
                case "outlineDy" -> PlacementVerificationVerdict.Component.OUTLINE;
                default -> PlacementVerificationVerdict.Component.COLLISION;
            };
            selectAttemptEvidence(evidence, field, clientRow, serverRow, component, conflicts);
        }
        return evidence;
    }

    private static void selectAttemptEvidence(
            LinkedHashMap<String, String> target,
            String field,
            Map<String, String> preferred,
            Map<String, String> alternate,
            PlacementVerificationVerdict.Component component,
            LinkedHashMap<String, PlacementVerificationVerdict.Component> conflicts) {
        String preferredValue = attemptEvidence(preferred, field);
        String alternateValue = attemptEvidence(alternate, field);
        detectAttemptConflict(field, preferredValue, alternateValue, component, conflicts);
        String selected = hasEvidence(preferredValue) ? preferredValue : alternateValue;
        if (hasEvidence(selected)) {
            target.put(field, selected);
        }
    }

    private static void selectAuthoritativeAttemptObservation(
            Map<String, String> target,
            String field,
            Map<String, String> serverRow,
            Map<String, String> clientRow) {
        String selected = rawAttemptObservation(serverRow, field);
        if (selected == null) {
            selected = rawAttemptObservation(clientRow, field);
        }
        if (selected != null) {
            target.put(field, selected);
        }
    }

    private static String rawAttemptObservation(Map<String, String> row, String field) {
        if (row == null) {
            return null;
        }
        String value = row.get(field);
        return value == null || value.isBlank() ? null : value;
    }

    private static String attemptEvidence(Map<String, String> row, String field) {
        if (row == null) {
            return null;
        }
        return row.get(field);
    }

    private static String firstAttemptEvidence(Map<String, String> row, String... fields) {
        if (row == null) {
            return null;
        }
        for (String field : fields) {
            String value = row.get(field);
            if (hasEvidence(value)) {
                return value;
            }
        }
        return null;
    }

    private static void detectAttemptConflict(
            String field,
            String preferredValue,
            String alternateValue,
            PlacementVerificationVerdict.Component component,
            LinkedHashMap<String, PlacementVerificationVerdict.Component> conflicts) {
        if (hasEvidence(preferredValue)
                && hasEvidence(alternateValue)
                && !sameEvidence(preferredValue, alternateValue)) {
            conflicts.put(
                    "LOGICAL_ATTEMPT_" + field.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase()
                            + "_CONFLICT",
                    component);
        }
    }

    private static void applyLogicalAttemptConflicts(
            LinkedHashMap<String, String> terminal,
            LinkedHashMap<String, PlacementVerificationVerdict.Component> conflicts) {
        if (conflicts.isEmpty()) {
            return;
        }
        terminal.put("finalVerdict", PlacementVerificationVerdict.FinalVerdict.RED.name());
        for (PlacementVerificationVerdict.Component component : conflicts.values()) {
            terminal.put(component.fieldName(), PlacementVerificationVerdict.ComponentStatus.FAIL.name());
        }
        StringBuilder missingComponents = new StringBuilder();
        for (PlacementVerificationVerdict.Component component
                : PlacementVerificationVerdict.Component.values()) {
            String status = terminal.get(component.fieldName());
            if (PlacementVerificationVerdict.ComponentStatus.UNKNOWN.name().equals(status)
                    || PlacementVerificationVerdict.ComponentStatus.MISSING.name().equals(status)
                    || PlacementVerificationVerdict.ComponentStatus.NOT_RUN.name().equals(status)) {
                if (!missingComponents.isEmpty()) {
                    missingComponents.append(',');
                }
                missingComponents.append(component.name());
            }
        }
        terminal.put(
                "missingRequiredComponents",
                missingComponents.isEmpty() ? "none" : missingComponents.toString());
        String failureClasses = terminal.getOrDefault("failureClasses", "none");
        StringBuilder combined = new StringBuilder();
        if (hasEvidence(failureClasses)) {
            combined.append(failureClasses);
        }
        for (String conflict : conflicts.keySet()) {
            if (!combined.isEmpty()) {
                combined.append(',');
            }
            combined.append(conflict);
        }
        terminal.put("failureClasses", combined.toString());
    }

    private static String actionIdOrNone(Map<String, String> row) {
        if (row == null) {
            return "none";
        }
        String actionId = row.get("actionId");
        return hasEvidence(actionId) ? actionId : "none";
    }

    private static void countLogicalAttempt(
            String attemptStatus,
            String playerProof,
            PlacementVerificationVerdict.FinalVerdict finalVerdict) {
        logicalAttemptRows++;
        switch (attemptStatus) {
            case "MERGED_CLIENT_SERVER" -> mergedClientServerAttemptRows++;
            case "AUTO_PROXY" -> autoProxyLogicalAttemptRows++;
            case "SERVER_ONLY" -> serverOnlyLogicalAttemptRows++;
            case "CLIENT_ONLY" -> clientOnlyLogicalAttemptRows++;
            default -> throw new IllegalArgumentException(
                    "unknown logical placement-attempt status " + attemptStatus);
        }
        boolean hasPlayerProof = "PRESENT".equals(playerProof);
        if (hasPlayerProof) {
            playerProofLogicalAttemptRows++;
        }
        switch (finalVerdict) {
            case GREEN -> {
                logicalAttemptVerdictGreenRows++;
                if (hasPlayerProof) {
                    playerProofGreenLogicalAttemptRows++;
                }
            }
            case RED -> logicalAttemptVerdictRedRows++;
            case INCONCLUSIVE -> logicalAttemptVerdictInconclusiveRows++;
            case EXPECTED_REFUSAL -> logicalAttemptVerdictExpectedRefusalRows++;
            case UNCLASSIFIED_FAILURE -> logicalAttemptVerdictUnclassifiedFailureRows++;
        }
    }

    private static boolean hasEvidence(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return !normalized.equals("unknown")
                && !normalized.equals("none")
                && !normalized.equals("missing")
                && !normalized.equals("not_run")
                && !normalized.equals("not_applicable")
                && !normalized.equals("null");
    }

    private static boolean sameEvidence(String left, String right) {
        if (isFiniteDyString(left) && isFiniteDyString(right)) {
            return sameDy(left, right);
        }
        return left.equals(right);
    }

    private static String appendMarkerToken(String markers, String marker) {
        if (markers == null || markers.isEmpty() || "none".equals(markers)) {
            return marker;
        }
        for (String token : markers.split("\\|", -1)) {
            if (token.equals(marker)) {
                return markers;
            }
        }
        return marker + "|" + markers;
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
        boolean placementAction = "place_block".equals(row.get("actionType"));
        boolean horizontal = isHorizontalFace(row.get("clickedFace"));
        boolean loweredOwner = isLawfulLoweredLane(row.get("clickedOwnerLaneKind"));
        if (placementAction && slabHeld && horizontal && loweredOwner) {
            // A slab continuing a lowered side lane lands flush BESIDE the clicked owner, so its
            // expected height is the OWNER's actual dy (row "beforeDy"), not a fixed -0.5. Deep owners
            // (past -1.0, depth-cap-removal) legitimately continue the lane deeper; hardcoding -0.5
            // false-flagged those legit placements as EXPECTED_DY_MISMATCH. Falls back to -0.5 only if
            // the owner dy was not recorded.
            String ownerDy = row.getOrDefault("beforeDy", "-0.500000");
            row.putIfAbsent("expectedAfterDy",
                    isLoweredDyString(ownerDy) ? ownerDy : "-0.500000");
            // The expectation is categorical: several concrete lane authorities are lawful here.
            row.putIfAbsent("expectedAfterLaneKind", "lawful_lowered_lane");
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
        // recordAction has already appended the reducer's canonical fields. Marker routing must use
        // the same single intent authority rather than independently preferring a legacy alias.
        String expectedDy = row.getOrDefault("intentDy", "unknown");
        String afterDy = row.getOrDefault("afterDy", "unknown");
        String afterLane = row.getOrDefault("afterLaneKind", "unknown");
        boolean placementAction = "place_block".equals(row.get("actionType"));
        // Every finite expectation is an explicit oracle, including ordinary zero. The old negative-
        // only guard hid wrong-height successes whenever a live-shaped row expected 0 or another
        // non-lowered value. Unknown/non-numeric expectations remain honestly unclassified.
        boolean loweredExpected = isLoweredDyString(expectedDy);
        boolean dyMismatch = isFiniteDyString(expectedDy)
                && isFiniteDyString(afterDy)
                && !sameDy(expectedDy, afterDy);
        boolean unclassifiedFailure = PlacementVerificationVerdict.FinalVerdict.UNCLASSIFIED_FAILURE.name()
                .equals(row.get("finalVerdict"));
        // The client prediction may not yet have the server's persistent attachment and can therefore
        // report the generic slab lane. Exempt only that exact client-side, dy-correct transition; an
        // authoritative server row without lawful lowered ownership remains a real lane mismatch.
        boolean clientUnnamedDyCorrect = "client".equals(row.getOrDefault("side", ""))
                && "unnamed_or_vanilla_slab".equals(afterLane)
                && sameDy(expectedDy, afterDy);
        boolean laneMismatch = loweredExpected
                && !isLawfulLoweredLane(afterLane)
                && !clientUnnamedDyCorrect;
        StringBuilder markers = new StringBuilder();
        appendMarker(markers, Boolean.parseBoolean(row.getOrDefault("hiddenOwner", "false")),
                "LIVE_PLACEMENT_HIDDEN_OWNER");
        appendMarker(markers, dyMismatch, "LIVE_PLACEMENT_EXPECTED_DY_MISMATCH");
        appendMarker(markers, unclassifiedFailure, "LIVE_PLACEMENT_UNCLASSIFIED_FAILURE");
        appendMarker(markers, laneMismatch, "LIVE_PLACEMENT_EXPECTED_LANE_MISMATCH");
        appendMarker(markers, loweredExpected && sameDy("0.000000", afterDy),
                "LIVE_PLACEMENT_VANILLA_DY_FROM_LOWERED_OWNER");
        appendMarker(
                markers,
                PlacementVerificationVerdict.FinalVerdict.RED.name().equals(row.get("finalVerdict")),
                PlacementVerificationVerdict.FinalVerdict.RED.marker());
        boolean playerAuthored = ActionOrigin.PLAYER_AUTHORED.wireName()
                .equals(row.getOrDefault("actionOrigin", ActionOrigin.PLAYER_AUTHORED.wireName()));
        boolean reducerGreen = PlacementVerificationVerdict.FinalVerdict.GREEN.name()
                .equals(row.get("finalVerdict"));
        if (markers.isEmpty() && loweredExpected && placementAction && playerAuthored && reducerGreen) {
            markers.append("LIVE_GREEN_PLACEMENT_AUTHORING");
        }
        return markers.isEmpty() ? "none" : markers.toString();
    }

    private static void appendLastCursorFields(LinkedHashMap<String, String> row) {
        LinkedHashMap<String, String> cursor = lastCursorRow;
        if (cursor == null) {
            row.put("cursorOutlineBounds", "none");
            row.put("cursorFinalHitPos", "none");
            row.put("cursorFinalHitState", "none");
            row.put("cursorFinalHitVec", "none");
            row.put("cursorFinalHitFace", "none");
            row.put("cursorHeldItem", "none");
            return;
        }
        row.put("cursorOutlineBounds", cursor.getOrDefault("outlineBounds", "none"));
        row.put("cursorFinalHitPos", cursor.getOrDefault("finalHitPos", "none"));
        row.put("cursorFinalHitState", cursor.getOrDefault("finalHitState", "none"));
        row.put("cursorFinalHitVec", cursor.getOrDefault("finalHitVec", "none"));
        row.put("cursorFinalHitFace", cursor.getOrDefault("finalHitFace", "none"));
        row.put("cursorHeldItem", cursor.getOrDefault("heldItem", "none"));
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

    /** True for a parseable, genuinely lowered (negative) dy string — any depth, not just -0.5. */
    private static boolean isLoweredDyString(String s) {
        try {
            return Double.parseDouble(s) < -1.0e-6d;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /** True only for a parseable finite dy value; unknown/none/NaN/infinity are not oracles. */
    private static boolean isFiniteDyString(String s) {
        try {
            return Double.isFinite(Double.parseDouble(s));
        } catch (NumberFormatException ignored) {
            return false;
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
                        + '\t' + tsv(row.get("actionOrigin"))
                        + '\t' + tsv(row.get("heldItem"))
                        + '\t' + tsv(row.get("clickedOwnerPos"))
                        + '\t' + tsv(row.get("clickedFace"))
                        + '\t' + tsv(row.get("placementPos"))
                        + '\t' + tsv(row.get("expectedAfterDy"))
                        + '\t' + tsv(row.get("afterDy"))
                        + '\t' + tsv(row.get("expectedAfterLaneKind"))
                        + '\t' + tsv(row.get("afterLaneKind"))
                        + '\t' + tsv(row.get("marker"))
                        + '\t' + tsv(row.get("afterStoredDy"))
                        + '\t' + tsv(row.get("afterStoredDyBits"))
                        + '\t' + tsv(row.get("pairPos"))
                        + '\t' + tsv(row.get("pairPart"))
                        + '\t' + tsv(row.get("pairState"))
                        + '\t' + tsv(row.get("pairAfterDy"))
                        + '\t' + tsv(row.get("pairStoredDy"))
                        + '\t' + tsv(row.get("pairStoredDyBits"))
                        + '\t' + tsv(row.get("logicalAttemptId"))
                        + '\t' + tsv(row.get("phase"))
                        + '\t' + tsv(row.get("playerProof")));
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
                        + '\t' + tsv(row.getOrDefault("heldItem", row.getOrDefault("cursorHeldItem", "none")))
                        + '\t' + tsv(row.getOrDefault("failureClasses", "none")));
    }

    private static void writeSummary() {
        StringBuilder text = new StringBuilder();
        text.append("# Slabbed Live Cursor Intent Recorder Summary\n\n");
        text.append("cursorRows=").append(cursorRows).append('\n');
        text.append("actionRows=").append(actionRows).append('\n');
        text.append("playerAuthoredActionRows=").append(playerAuthoredActionRows).append('\n');
        text.append("autoUseOnProxyActionRows=").append(autoUseOnProxyActionRows).append('\n');
        text.append("ghostSurfaceRows=").append(ghostSurfaceRows).append('\n');
        text.append("hiddenOwnerRows=").append(hiddenOwnerRows).append('\n');
        text.append("outlineRaycastSplitRows=").append(outlineRaycastSplitRows).append('\n');
        text.append("renderedOutlineRows=").append(renderedOutlineRows).append('\n');
        text.append("renderedOutlineLargeBoundsRows=").append(renderedOutlineLargeBoundsRows).append('\n');
        text.append("renderedOutlineReplayBoundsSplitRows=")
                .append(renderedOutlineReplayBoundsSplitRows).append('\n');
        text.append("renderedOutlineTargetSplitRows=").append(renderedOutlineTargetSplitRows).append('\n');
        text.append("placementExpectedDyMismatchRows=").append(placementExpectedDyMismatchRows).append('\n');
        text.append("placementUnclassifiedFailureRows=")
                .append(placementUnclassifiedFailureRows).append('\n');
        text.append("placementExpectedLaneMismatchRows=").append(placementExpectedLaneMismatchRows).append('\n');
        text.append("loweredSideSlabPlacementVanillaDyRows=")
                .append(loweredSideSlabPlacementVanillaDyRows).append('\n');
        text.append("collisionIteratorTargetMissRows=").append(collisionIteratorTargetMissRows).append('\n');
        text.append("collisionIteratorTargetPresentRows=").append(collisionIteratorTargetPresentRows).append('\n');
        text.append("liveGreenCursorTriadRows=").append(greenCursorTriadRows).append('\n');
        text.append("liveGreenPlacementRows=").append(greenPlacementAuthoringRows).append('\n');
        text.append("placementVerdictGreenRows=").append(placementVerdictGreenRows).append('\n');
        text.append("placementVerdictRedRows=").append(placementVerdictRedRows).append('\n');
        text.append("placementVerdictInconclusiveRows=")
                .append(placementVerdictInconclusiveRows).append('\n');
        text.append("placementVerdictExpectedRefusalRows=")
                .append(placementVerdictExpectedRefusalRows).append('\n');
        text.append("placementVerdictUnclassifiedFailureRows=")
                .append(placementVerdictUnclassifiedFailureRows).append('\n');
        text.append("logicalAttemptRows=").append(logicalAttemptRows).append('\n');
        text.append("mergedClientServerAttemptRows=")
                .append(mergedClientServerAttemptRows).append('\n');
        text.append("autoProxyLogicalAttemptRows=").append(autoProxyLogicalAttemptRows).append('\n');
        text.append("serverOnlyLogicalAttemptRows=").append(serverOnlyLogicalAttemptRows).append('\n');
        text.append("clientOnlyLogicalAttemptRows=").append(clientOnlyLogicalAttemptRows).append('\n');
        text.append("playerProofLogicalAttemptRows=")
                .append(playerProofLogicalAttemptRows).append('\n');
        text.append("logicalAttemptVerdictGreenRows=")
                .append(logicalAttemptVerdictGreenRows).append('\n');
        text.append("logicalAttemptVerdictRedRows=")
                .append(logicalAttemptVerdictRedRows).append('\n');
        text.append("logicalAttemptVerdictInconclusiveRows=")
                .append(logicalAttemptVerdictInconclusiveRows).append('\n');
        text.append("logicalAttemptVerdictExpectedRefusalRows=")
                .append(logicalAttemptVerdictExpectedRefusalRows).append('\n');
        text.append("logicalAttemptVerdictUnclassifiedFailureRows=")
                .append(logicalAttemptVerdictUnclassifiedFailureRows).append('\n');
        text.append("playerProofGreenLogicalAttemptRows=")
                .append(playerProofGreenLogicalAttemptRows).append('\n');
        text.append("modelStaleDivergentRows=").append(modelStaleDivergentRows).append('\n');
        text.append("modelStaleAbsentRows=").append(modelStaleAbsentRows).append('\n');
        text.append("modelStaleYellowRows=").append(modelStaleYellowRows).append('\n');
        text.append("breakRows=").append(breakRows).append('\n');
        text.append("placementSideDySplitRows=").append(placementSideDySplitRows).append('\n');
        text.append("ensembleClashRows=").append(ensembleClashRows).append('\n');
        text.append("ensembleOccludedOccupancyInfoRows=")
                .append(ensembleOccludedOccupancyInfoRows).append('\n');
        // Sentinel liveness (green-by-evidence, not green-by-absence): zero red rows only counts as a
        // clean bill when these show the probe actually armed and judged during the session.
        text.append("sentinelArmedTotal=").append(SlabModelStaleSentinel.armedTotalCount()).append('\n');
        text.append("sentinelSamplePasses=").append(SlabModelStaleSentinel.samplePassCount()).append('\n');
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
            String configured = System.getProperty(DIR_PROPERTY);
            Path requested = (configured == null || configured.isBlank())
                    ? FabricLoader.getInstance().getGameDir().resolve("live-cursor-recorder")
                    : Path.of(configured);
            Files.createDirectories(requested);
            sessionDir = isolateFromExistingSession(requested);
            Files.createDirectories(sessionDir);
            if (!startLogged) {
                startLogged = true;
                Slabbed.LOGGER.info("[LIVE_CURSOR_INTENT_RECORDER_START] enabled=true dir={}",
                        sessionDir.toAbsolutePath());
            }
            writeHeaderIfMissing(sessionDir.resolve("actions.tsv"),
                    ACTIONS_HEADER + "\n");
            writeHeaderIfMissing(sessionDir.resolve("rendered-outlines.tsv"),
                    "outlineRenderId\tcursorRowId\trenderedOutlinePos\tcursorFinalHitPos\trenderedOutlineState"
                            + "\trenderedOutlineBounds\tcursorOutlineBounds\trenderedOutlineWorldBounds"
                            + "\trenderedOutlineCameraRelativeBounds\trenderedOutlineHitVec\tmarker\n");
            writeHeaderIfMissing(sessionDir.resolve("mismatches.tsv"),
                    "type\trowOrActionId\tmarker\tpos\theldItem\tfailureClasses\n");
            writeManifest();
        }
        return sessionDir;
    }

    /**
     * Never append a new run beneath a recognized old recorder schema/header. Any non-empty v2-v6
     * manifest/session/TSV/summary artifact is left byte-for-byte in place and the new run starts in a
     * uniquely named child directory. This also keeps two schema-6 process runs distinct instead of
     * silently mixing different run ids.
     */
    private static Path isolateFromExistingSession(Path requested) throws IOException {
        for (String artifact : new String[]{
                "manifest.json", "session.jsonl", "actions.tsv", "rendered-outlines.tsv",
                "mismatches.tsv", "summary.md"}) {
            Path path = requested.resolve(artifact);
            if (Files.exists(path) && Files.size(path) > 0L) {
                return requested.resolve("schema-" + SCHEMA_VERSION + "-" + RUN_ID);
            }
        }
        return requested;
    }

    private static void writeHeaderIfMissing(Path path, String header) throws IOException {
        if (!Files.exists(path) || Files.size(path) == 0L) {
            Files.writeString(path, header, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    private static void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        shutdownHookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(
                LiveCursorIntentRecorder::recordShutdown,
                "slabbed-live-cursor-recorder-shutdown"));
    }

    // SECURITY (ported verbatim from the 1.21.11 canonical branch, RECORDER_REVIEW.md H1):
    // sun.java.command is the full launcher command line, which for a real Microsoft-authenticated
    // client includes --accessToken (a live JWT), --uuid, and --xuid as plain launch arguments.
    // manifest.json is a debug file routinely shared for analysis (as every session.jsonl this
    // project uses has been), so those values must never reach disk unredacted. Matches
    // "--flag value" pairs case-insensitively; value is whatever non-whitespace token follows
    // (session tokens/UUIDs never contain spaces).
    private static final Pattern SENSITIVE_LAUNCH_ARG_PATTERN = Pattern.compile(
            "(--(?:accessToken|uuid|xuid|clientId|session))\\s+\\S+", Pattern.CASE_INSENSITIVE);

    /** Public for {@code RecorderManifestRedactionTest} coverage — a pure, stateless redaction. */
    public static String redactJavaCommand(String command) {
        if (command == null || command.isEmpty()) {
            return command;
        }
        return SENSITIVE_LAUNCH_ARG_PATTERN.matcher(command).replaceAll("$1 [REDACTED]");
    }

    private static void writeManifest() {
        if (manifestWritten || sessionDir == null) {
            return;
        }
        manifestWritten = true;
        String manifest = "{"
                + jsonPair("schemaVersion", SCHEMA_VERSION) + ","
                + jsonPair("runId", RUN_ID) + ","
                + jsonPair("recorder", "LiveCursorIntentRecorder") + ","
                + jsonPair("recorderVersion", RECORDER_VERSION) + ","
                + jsonPair("actionOriginContract", "PLAYER_AUTHORED|AUTO_USEON_PROXY") + ","
                + jsonPair("placementVerdictContract", "PlacementVerificationVerdict-v3") + ","
                + jsonPair("logicalAttemptContract", "LogicalPlacementAttempt-v1") + ","
                + jsonPair("enabled", Boolean.toString(enabled)) + ","
                + jsonPair("createdAt", Instant.now().toString()) + ","
                + jsonPair("dir", sessionDir.toAbsolutePath().toString()) + ","
                + jsonPair("gameDir", FabricLoader.getInstance().getGameDir().toAbsolutePath().toString()) + ","
                + jsonPair("javaVersion", System.getProperty("java.version", "unknown")) + ","
                + jsonPair("javaVmName", System.getProperty("java.vm.name", "unknown")) + ","
                + jsonPair("minecraftVersion", modVersion("minecraft")) + ","
                + jsonPair("fabricLoaderVersion", modVersion("fabricloader")) + ","
                + jsonPair("slabbedVersion", modVersion(Slabbed.MOD_ID)) + ","
                // Jar-identity stamp (anti-whack-a-mole audit): a session log must be attributable to an
                // exact build — version strings alone have already collided across different artifacts.
                + jsonPair("gitSha", BuildStamp.GIT_SHA) + ","
                + jsonPair("buildTime", BuildStamp.BUILD_TIME) + ","
                + jsonPair("jarFile", BuildStamp.JAR_FILE) + ","
                // Records why cursor rows may be absent (the legacy pick-TAIL cursor leg is bypassed
                // whenever the offset raycast is active) and that the mesh-staleness sentinel exists.
                + jsonPair("offsetRaycastEnabled", Boolean.toString(SlabbedOffsetRaycast.ENABLED)) + ","
                + jsonPair("modelStaleSentinel", "present") + ","
                + jsonPair("userDir", System.getProperty("user.dir", "")) + ","
                + jsonPair("javaCommand", redactJavaCommand(System.getProperty("sun.java.command", "")))
                + "}\n";
        writeFile("manifest.json", manifest, false);
    }

    private static String jsonPair(String key, String value) {
        return "\"" + escapeJson(key) + "\":\"" + escapeJson(value) + "\"";
    }

    private static String modVersion(String modId) {
        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
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
