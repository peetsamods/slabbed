package com.slabbed.devtools.util;

import com.slabbed.util.BuildStamp;
import com.slabbed.util.PlacementVerificationVerdict;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import com.slabbed.anchor.SlabPlacementHeightAttachment;

import com.slabbed.Slabbed;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Live cursor / placement-intent capture for development and GameTest runtimes. It writes a per-session trail
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
    private static final String SCHEMA_VERSION = "9";
    private static final String RECORDER_VERSION =
            "neoforge-1.21.1-recorder-truth-v2";
    private static final String CAPTURE_UUID = UUID.randomUUID().toString();
    private static final String ACTIONS_HEADER =
            "actionId\tcursorRowId\tactionType\tactionOrigin\theldItem\tclickedOwnerPos\tclickedFace\tplacementPos\tplacedBlockId"
                    + "\texpectedAfterDy\tafterDy\texpectedAfterLaneKind\tafterLaneKind\tmarker"
                    + "\tafterStoredDy\tafterStoredDyBits\tpairPos\tpairPart\tpairState"
                    + "\tpairAfterDy\tpairStoredDy\tpairStoredDyBits"
                    + "\tlogicalAttemptId\tphase\tplayerProof"
                    + "\tactualResult\tfinalVerdict\tplacedVerdict\tanchorVerdict"
                    + "\tmodelVerdict\tcollisionVerdict\traycastVerdict\toutlineVerdict"
                    + "\tstabilityVerdict\tmissingRequiredComponents\tfailureClasses"
                    + "\tintentDy\tmodelDy\tcollisionDy\traycastDy\toutlineDy"
                    + "\tplacementRoute\tlandingAuthority\tresolvedFloorDy";
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
    private static long renderedOutlineFrameTargetSplitRows;
    private static long renderedOutlineFrameRaycastSplitRows;
    private static long renderedOutlineTickSkewInfoRows;
    private static long renderedOutlineFrameTriadGreenRows;
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
    private static long ensembleCandidateRows;
    private static long ensembleOccludedOccupancyInfoRows;
    private static final long PAIR_WINDOW_NANOS = 1_000_000_000L;
    private static final long SUMMARY_WRITE_INTERVAL_NANOS = 1_000_000_000L;
    private static final long VERIFICATION_SETTLE_NANOS = 250_000_000L;
    private static final long VERIFICATION_TIMEOUT_NANOS = 120_000_000_000L;
    private static final int MAX_PENDING_CLIENT_ATTEMPTS = 256;
    private static final LinkedHashMap<PlacementAttemptKey, ArrayDeque<PendingClientAttempt>>
            PENDING_CLIENT_ATTEMPTS = new LinkedHashMap<>();
    private static final LinkedHashMap<String, PendingVerification> PENDING_VERIFICATIONS =
            new LinkedHashMap<>();
    private static int pendingClientAttemptCount;
    private static long lastCursorRowId;
    private static LinkedHashMap<String, String> lastCursorRow;
    private static long lastSummaryWriteNanos;

    private static final class PendingVerification {
        final String logicalAttemptId;
        String attemptStatus;
        final String playerProof;
        final LinkedHashMap<String, String> clientRow;
        LinkedHashMap<String, String> serverRow;
        final String placementPos;
        final long createdNanos;
        final ArrayList<LinkedHashMap<String, String>> ensembleCandidates = new ArrayList<>();
        long lifecycleNanos;

        PendingVerification(
                String logicalAttemptId,
                String attemptStatus,
                String playerProof,
                LinkedHashMap<String, String> clientRow,
                LinkedHashMap<String, String> serverRow,
                long createdNanos) {
            this.logicalAttemptId = logicalAttemptId;
            this.attemptStatus = attemptStatus;
            this.playerProof = playerProof;
            this.clientRow = clientRow;
            this.serverRow = serverRow;
            this.placementPos = firstCorrelationValue(
                    serverRow == null ? clientRow : serverRow,
                    "placementPos");
            this.createdNanos = createdNanos;
        }
    }

    enum StabilityObservation {
        PASS,
        FAIL,
        WAIT
    }

    /**
     * Identifies the OBSERVATION SUBJECT of an action row, never the transport that carried it.
     *
     * <p>The packet sequence used to be the whole key whenever it was present, which merged rows that
     * describe different cells. One vanilla use packet produces up to three rows: the client packet-
     * boundary observation of the CLICKED OWNER ({@code placementPos=none}), the client placement
     * observation of the PLACED cell, and the server placement observation of the PLACED cell. Only
     * the last two are the same subject. Worse, the client placement row is recorded after the
     * prediction scope has closed and therefore carries no {@code packetSequence} at all, so the
     * sequence key bound the server placement to the boundary row and orphaned every real client
     * placement (schema-6 live run: {@code mergedClientServerAttemptRows=102} boundary merges and
     * {@code clientOnlyLogicalAttemptRows=67}, exactly the count of client placement rows). Keying on
     * the subject makes those 67 pair correctly and makes a boundary/placement merge impossible.
     */
    private record PlacementAttemptKey(
            String actionType,
            String heldItem,
            String clickedOwnerPos,
            String clickedFace,
            String placementPos,
            String rigCaseId) {
    }

    private record PendingClientAttempt(
            String logicalAttemptId,
            String packetSequence,
            String playerUuid,
            String playerName,
            String dimensionId,
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

    /** Opens a nested-safe machine-readable origin scope owned by the calling bridge. */
    public static ActionOriginScope enterActionOrigin(ActionOrigin origin) {
        if (origin == null) {
            throw new IllegalArgumentException("action origin must not be null");
        }
        ArrayDeque<ActionOrigin> stack = ACTION_ORIGINS.get();
        stack.addLast(origin);
        return new ActionOriginScope(Thread.currentThread(), stack, origin);
    }

    /** Diagnostics-test convenience built on the same nested scope API used by the real provider. */
    public static void withActionOrigin(ActionOrigin origin, Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        try (ActionOriginScope ignored = enterActionOrigin(origin)) {
            action.run();
        }
    }

    public static final class ActionOriginScope implements AutoCloseable {
        private final Thread owner;
        private final ArrayDeque<ActionOrigin> stack;
        private final ActionOrigin origin;
        private boolean closed;

        private ActionOriginScope(
                Thread owner,
                ArrayDeque<ActionOrigin> stack,
                ActionOrigin origin) {
            this.owner = owner;
            this.stack = stack;
            this.origin = origin;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("action origin scope closed on another thread");
            }
            ActionOrigin removed = stack.pollLast();
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
            finalizeAllPendingVerifications();
            writeSummary(true);
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
            enrichPendingFromCursorLocked(row);
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
            boolean red = hasRedMarker(markers);
            boolean frameTriadGreen = !red
                    && "PASS".equals(row.getOrDefault("frameRaycastVerdict", "none"))
                    && row.getOrDefault("frameGamePickPos", "none")
                            .equals(row.getOrDefault("renderedOutlinePos", "missing"));
            if (frameTriadGreen && "none".equals(markers)) {
                markers = "LIVE_GREEN_FRAME_TRIAD";
            }
            row.put("marker", markers);
            row.put("outlineVerdict", red ? "FAIL" : "PASS");
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
            if (markers.contains("LIVE_FRAME_OUTLINE_TARGET_SPLIT")) {
                renderedOutlineFrameTargetSplitRows++;
            }
            if (markers.contains("LIVE_FRAME_RAYCAST_SPLIT")) {
                renderedOutlineFrameRaycastSplitRows++;
            }
            if (markers.contains("INFO_RENDERED_OUTLINE_TICK_SKEW")) {
                renderedOutlineTickSkewInfoRows++;
            }
            if (frameTriadGreen) {
                renderedOutlineFrameTriadGreenRows++;
            }
            enrichPendingFromOutlineLocked(row);
            writeSession(row);
            writeRenderedOutlineTsv(row);
            if (red) {
                writeMismatchRows(row, markers);
            }
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
                case YELLOW -> {
                    if (kind.startsWith("ENSEMBLE_")) {
                        ensembleCandidateRows++;
                    } else {
                        modelStaleYellowRows++;
                    }
                }
            }
            if (severity == SlabModelStaleSentinel.DiagnosticSeverity.YELLOW
                    && kind.startsWith("ENSEMBLE_")) {
                attachEnsembleCandidateLocked(row);
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
            markPendingLifecycleBreakLocked(pos);
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
            if (actionOrigin == ActionOrigin.AUTO_USEON_PROXY) {
                // A synthetic builder proves the production use path, not player aim. Unless the
                // rig supplies its own typed oracle, root-hit-derived intent would turn every
                // deliberately lowered variant into a false placement mismatch.
                row.put("intentDy", "unknown");
                row.put("expectedAfterDy", "unknown");
                row.put("expectedSupportPlane", "unknown");
                row.put("actualContactPlane", "unknown");
                row.put("seatError", "unknown");
                row.put("landingAuthority", "auto_proxy_undeclared");
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
                matchedClient = removeMatchingClientAttempt(
                        row, attemptKey, correlationValue(row, "packetSequence"), nowNanos);
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

            // A split is a disagreement about what the two sides SHOW, so the client's number
            // must be the one it draws. Its afterDy is a Level-view read, and a client prediction
            // is deliberately invisible to Level views (so a guess can never reach collision or
            // targeting), which means afterDy reports the pre-fact geometry for the whole window
            // before the fact syncs - on every placement, not only wrong ones. Comparing that
            // against the server's stored answer called ordinary placements a split. Prefer
            // clientDrawnDy whenever the client holds a prediction; fall back to afterDy only
            // when it holds none, which is exactly when afterDy IS what it draws.
            if (matchedClient != null) {
                String clientDrawn = matchedClient.row().get("clientDrawnDy");
                String clientShown = hasEvidence(clientDrawn)
                        ? clientDrawn
                        : matchedClient.row().get("afterDy");
                if (hasEvidence(clientShown)
                        && hasEvidence(row.get("afterDy"))
                        && !sameEvidence(clientShown, row.get("afterDy"))) {
                    placementSideDySplitRows++;
                    row.put("clientAfterDy", clientShown);
                    markers = appendMarkerToken(markers, "LIVE_PLACEMENT_SIDE_DY_SPLIT");
                    row.put("marker", markers);
                }
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
                        new PendingClientAttempt(
                                logicalAttemptId,
                                correlationValue(row, "packetSequence"),
                                firstCorrelationValue(row, "playerUuid", "playerId"),
                                firstCorrelationValue(row, "player", "playerName"),
                                firstCorrelationValue(
                                        row, "dimensionId", "dimension", "level", "world"),
                                nowNanos,
                                copy(row)));
                addPendingVerification(
                        logicalAttemptId,
                        "CLIENT_PENDING",
                        playerProof,
                        row,
                        null,
                        nowNanos);
            } else if (playerAuthored) {
                addPendingVerification(
                        logicalAttemptId,
                        terminalStatus,
                        playerProof,
                        matchedClient == null ? null : matchedClient.row(),
                        row,
                        nowNanos);
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

    /** Joins the actual model-owner dy emitted by the chunk-mesh path to a pending placement. */
    public static void recordModelObservation(BlockPos pos, float modelDy) {
        if (!enabled() || pos == null || !Float.isFinite(modelDy)) {
            return;
        }
        synchronized (LOCK) {
            String subject = pos.toShortString();
            PendingVerification pending = newestPendingForSubject(subject);
            if (pending != null) {
                targetObservationRow(pending).put("modelDy", Float.toString(modelDy));
                tryFinalizePendingLocked(pending, false);
            }
            writeSummary();
        }
    }

    /** Testable observation seam used by the later-tick stability sampler. */
    public static void recordStabilityObservation(BlockPos pos, boolean stable) {
        if (!enabled() || pos == null) {
            return;
        }
        synchronized (LOCK) {
            applyStabilityObservationLocked(pos.toShortString(), stable);
            writeSummary();
        }
    }

    public static String pendingEvidenceForTests(BlockPos pos, String field) {
        synchronized (LOCK) {
            PendingVerification pending = pos == null ? null : newestPendingForSubject(pos.toShortString());
            if (pending == null || field == null) {
                return "none";
            }
            String value = targetObservationRow(pending).get(field);
            return value == null ? "none" : value;
        }
    }

    public static int pendingVerificationCountForTests() {
        synchronized (LOCK) {
            return PENDING_VERIFICATIONS.size();
        }
    }

    /** Settles pending placement stability from a later client tick; never mutates the world. */
    public static void samplePendingPlacements(net.minecraft.world.level.Level level, long nowTick) {
        if (!enabled() || level == null) {
            return;
        }
        synchronized (LOCK) {
            long nowNanos = System.nanoTime();
            for (PendingVerification pending : new ArrayList<>(PENDING_VERIFICATIONS.values())) {
                if (pending.serverRow != null
                        && pending.lifecycleNanos != 0L
                        && nowNanos - pending.lifecycleNanos >= VERIFICATION_SETTLE_NANOS
                        && !hasEvidence(pending.serverRow.get("stabilityVerdict"))) {
                    BlockPos pos = parseShortPos(pending.placementPos);
                    if (pos != null && level.hasChunkAt(pos)) {
                        BlockState state = level.getBlockState(pos);
                        String expectedState = pending.serverRow.getOrDefault("afterState", "unknown");
                        String expectedBlockId = pending.serverRow.getOrDefault("placedBlockId", "unknown");
                        String actualBlockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                .getKey(state.getBlock()).toString();
                        String expectedDy = pending.serverRow.getOrDefault("afterDy", "unknown");
                        String actualDy = Double.toString(SlabSupport.getYOffset(level, pos, state));
                        String expectedStored = pending.serverRow.getOrDefault("afterStoredDy", "unknown");
                        double actualStoredValue = SlabPlacementHeightAttachment.storedOffset(level, pos);
                        String actualStored = Double.isFinite(actualStoredValue)
                                ? Double.toString(actualStoredValue)
                                : "unknown";
                        StabilityObservation observation = classifyStabilityObservation(
                                expectedBlockId,
                                actualBlockId,
                                expectedDy,
                                actualDy,
                                expectedStored,
                                actualStored);
                        if (observation != StabilityObservation.WAIT) {
                            if (!state.toString().equals(expectedState)) {
                                pending.serverRow.put("stabilityPropertyChange", "observed");
                            }
                            applyStabilityObservationLocked(
                                    pending.placementPos,
                                    observation == StabilityObservation.PASS);
                        }
                    }
                }
                boolean timedOut = nowNanos - pending.createdNanos >= VERIFICATION_TIMEOUT_NANOS;
                tryFinalizePendingLocked(pending, timedOut);
            }
            writeSummary();
        }
    }

    private static void applyStabilityObservationLocked(String subject, boolean stable) {
        PendingVerification pending = newestPendingForSubject(subject);
        if (pending != null && pending.serverRow != null) {
            pending.serverRow.put("stabilityVerdict", stable ? "PASS" : "FAIL");
            if (!stable) {
                pending.serverRow.put("stabilityFailure", "post_placement_state_or_dy_changed");
            }
            tryFinalizePendingLocked(pending, false);
        }
    }

    private static void markPendingLifecycleBreakLocked(BlockPos brokenPos) {
        long nowNanos = System.nanoTime();
        for (PendingVerification pending : PENDING_VERIFICATIONS.values()) {
            if (pending.serverRow == null) {
                continue;
            }
            BlockPos placedPos = parseShortPos(pending.placementPos);
            if (placedPos == null || placedPos.equals(brokenPos)) {
                continue;
            }
            if (Math.abs(placedPos.getX() - brokenPos.getX()) <= 1
                    && Math.abs(placedPos.getY() - brokenPos.getY()) <= 1
                    && Math.abs(placedPos.getZ() - brokenPos.getZ()) <= 1) {
                pending.lifecycleNanos = nowNanos;
                pending.serverRow.put("stabilityVerdict", "NOT_RUN");
            }
        }
    }

    static StabilityObservation classifyStabilityObservation(
            String expectedBlockId,
            String actualBlockId,
            String expectedDy,
            String actualDy,
            String expectedStoredDy,
            String actualStoredDy
    ) {
        if (!hasEvidence(expectedBlockId)
                || !hasEvidence(actualBlockId)
                || !sameEvidence(expectedBlockId, actualBlockId)
                || !isFiniteDyString(expectedDy)
                || !isFiniteDyString(actualDy)
                || !sameEvidence(expectedDy, actualDy)) {
            return StabilityObservation.FAIL;
        }
        if (isFiniteDyString(expectedStoredDy)) {
            if (!isFiniteDyString(actualStoredDy)) {
                return StabilityObservation.WAIT;
            }
            if (!sameEvidence(expectedStoredDy, actualStoredDy)) {
                return StabilityObservation.FAIL;
            }
        }
        return StabilityObservation.PASS;
    }

    public static void flushSummaryForTests() {
        if (!enabled()) {
            return;
        }
        synchronized (LOCK) {
            finalizeAllPendingClientAttempts();
            finalizeAllPendingVerifications();
            writeSummary(true);
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
            renderedOutlineFrameTargetSplitRows = 0L;
            renderedOutlineFrameRaycastSplitRows = 0L;
            renderedOutlineTickSkewInfoRows = 0L;
            renderedOutlineFrameTriadGreenRows = 0L;
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
            ensembleCandidateRows = 0L;
            ensembleOccludedOccupancyInfoRows = 0L;
            PENDING_CLIENT_ATTEMPTS.clear();
            PENDING_VERIFICATIONS.clear();
            pendingClientAttemptCount = 0;
            lastCursorRowId = 0L;
            lastCursorRow = null;
            lastSummaryWriteNanos = 0L;
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
        return CAPTURE_UUID + "-attempt-" + LOGICAL_ATTEMPT_IDS.incrementAndGet();
    }

    private static PlacementAttemptKey placementAttemptKey(Map<String, String> row) {
        return new PlacementAttemptKey(
                correlationValue(row, "actionType"),
                correlationValue(row, "heldItem"),
                correlationValue(row, "clickedOwnerPos"),
                correlationValue(row, "clickedFace"),
                correlationValue(row, "placementPos"),
                correlationValue(row, "rigCaseId"));
    }

    /**
     * Player/dimension identity is a compatibility CHECK, never part of the key, and each identity
     * lane is compared only against its own kind. The two sides of one interaction resolve identity
     * from different sources — a packet-scoped row carries the player UUID and dimension id, an
     * unscoped one carries only the player name — so a single "first non-empty identity" value
     * compared a UUID against a display name and split every real client/server pair apart. A lane
     * conflicts only when both sides actually resolved that same lane.
     */
    private static boolean compatibleAttemptIdentity(
            Map<String, String> row,
            PendingClientAttempt candidate) {
        return compatibleIdentityValue(
                        firstCorrelationValue(row, "playerUuid", "playerId"),
                        candidate.playerUuid())
                && compatibleIdentityValue(
                        firstCorrelationValue(row, "player", "playerName"),
                        candidate.playerName())
                && compatibleIdentityValue(
                        firstCorrelationValue(row, "dimensionId", "dimension", "level", "world"),
                        candidate.dimensionId());
    }

    private static boolean compatibleIdentityValue(String left, String right) {
        return "none".equals(left) || "none".equals(right) || left.equals(right);
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

    /**
     * Same subject only. Within a subject an exact packet-sequence match wins outright and ignores
     * age — the sequence is an unambiguous correlation token. Failing that, the oldest observation
     * still inside the pair window is taken, so a stale client row can never be merged into an
     * unrelated later placement. Two rows that BOTH carry a sequence and disagree are definitively
     * different interactions and are never paired by the age fallback; subject keying is what makes
     * that pairing reachable at all, so the guard belongs here.
     */
    private static PendingClientAttempt removeMatchingClientAttempt(
            Map<String, String> row,
            PlacementAttemptKey key,
            String packetSequence,
            long nowNanos) {
        ArrayDeque<PendingClientAttempt> attempts = PENDING_CLIENT_ATTEMPTS.get(key);
        if (attempts == null || attempts.isEmpty()) {
            return null;
        }
        PendingClientAttempt matched = null;
        for (PendingClientAttempt candidate : attempts) {
            if (!compatibleAttemptIdentity(row, candidate)) {
                continue;
            }
            boolean bothSequenced = !"none".equals(packetSequence)
                    && !"none".equals(candidate.packetSequence());
            if (bothSequenced) {
                if (packetSequence.equals(candidate.packetSequence())) {
                    matched = candidate;
                    break;
                }
                continue;
            }
            if (matched == null && nowNanos - candidate.recordedNanos() < PAIR_WINDOW_NANOS) {
                matched = candidate;
            }
        }
        if (matched == null) {
            return null;
        }
        for (Iterator<PendingClientAttempt> candidates = attempts.iterator();
                candidates.hasNext(); ) {
            if (candidates.next() == matched) {
                candidates.remove();
                break;
            }
        }
        pendingClientAttemptCount--;
        if (attempts.isEmpty()) {
            PENDING_CLIENT_ATTEMPTS.remove(key);
        }
        return matched;
    }

    /**
     * The pair window bounds CONTENT-based guessing only. A pending attempt that carries a real
     * packet sequence is an exact correlation token and must never be aged out: its server partner
     * can legitimately arrive much later (a slow tick, a loaded server), and merging it then is still
     * unambiguous. It is finalized at flush/shutdown, or by the capacity bound, but never by time.
     *
     * <p>This exemption used to live on {@code PlacementAttemptKey.packetSequence()}; when the key
     * became subject-based the exemption had to move onto the entry, and losing it in transit is what
     * broke the same-sequence-beyond-the-window contract. The scan is per-entry rather than head-only
     * because a subject bucket may now hold a mix, and an exempt head must not pin aged sequence-less
     * attempts behind it.
     */
    private static void finalizeExpiredClientAttempts(long nowNanos) {
        Iterator<Map.Entry<PlacementAttemptKey, ArrayDeque<PendingClientAttempt>>> entries =
                PENDING_CLIENT_ATTEMPTS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<PlacementAttemptKey, ArrayDeque<PendingClientAttempt>> entry = entries.next();
            ArrayDeque<PendingClientAttempt> attempts = entry.getValue();
            for (Iterator<PendingClientAttempt> candidates = attempts.iterator();
                    candidates.hasNext(); ) {
                PendingClientAttempt candidate = candidates.next();
                if (!"none".equals(candidate.packetSequence())
                        || nowNanos - candidate.recordedNanos() < PAIR_WINDOW_NANOS) {
                    continue;
                }
                candidates.remove();
                pendingClientAttemptCount--;
                finalizeClientOnlyAttempt(candidate);
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
        finalizeClientOnlyAttempt(oldest);
    }

    private static void finalizeAllPendingClientAttempts() {
        for (ArrayDeque<PendingClientAttempt> attempts : PENDING_CLIENT_ATTEMPTS.values()) {
            for (PendingClientAttempt attempt : attempts) {
                finalizeClientOnlyAttempt(attempt);
            }
        }
        PENDING_CLIENT_ATTEMPTS.clear();
        pendingClientAttemptCount = 0;
    }

    private static void finalizeClientOnlyAttempt(PendingClientAttempt attempt) {
        PendingVerification pending = PENDING_VERIFICATIONS.get(attempt.logicalAttemptId());
        if (pending != null) {
            pending.attemptStatus = "CLIENT_ONLY";
            tryFinalizePendingLocked(pending, true);
            return;
        }
        writeLogicalAttempt(
                attempt.row(),
                null,
                attempt.logicalAttemptId(),
                "CLIENT_ONLY",
                "PRESENT");
    }

    private static void finalizeAllPendingVerifications() {
        for (PendingVerification pending : new ArrayList<>(PENDING_VERIFICATIONS.values())) {
            tryFinalizePendingLocked(pending, true);
        }
    }

    private static void addPendingVerification(
            String logicalAttemptId,
            String attemptStatus,
            String playerProof,
            LinkedHashMap<String, String> clientRow,
            LinkedHashMap<String, String> serverRow,
            long nowNanos
    ) {
        PendingVerification existing = PENDING_VERIFICATIONS.get(logicalAttemptId);
        if (existing != null) {
            if (serverRow != null) {
                existing.serverRow = copy(serverRow);
                existing.attemptStatus = attemptStatus;
            }
            tryFinalizePendingLocked(existing, false);
            return;
        }
        if (PENDING_VERIFICATIONS.size() >= MAX_PENDING_CLIENT_ATTEMPTS) {
            PendingVerification oldest = PENDING_VERIFICATIONS.values().iterator().next();
            tryFinalizePendingLocked(oldest, true);
        }
        PendingVerification pending = new PendingVerification(
                logicalAttemptId,
                attemptStatus,
                playerProof,
                clientRow == null ? null : copy(clientRow),
                serverRow == null ? null : copy(serverRow),
                nowNanos);
        PENDING_VERIFICATIONS.put(logicalAttemptId, pending);
        tryFinalizePendingLocked(pending, false);
    }

    private static void enrichPendingFromCursorLocked(Map<String, String> cursor) {
        String subject = correlationValue(cursor, "finalHitPos");
        if ("none".equals(subject)) {
            return;
        }
        PendingVerification pending = newestPendingForSubject(subject);
        if (pending != null) {
            LinkedHashMap<String, String> target = targetObservationRow(pending);
            copyEvidence(cursor, target, "raycastDy");
            copyEvidence(cursor, target, "collisionDy");
            // An explicit raycast FAIL latches: a later cross-time tick PASS is the weaker sample
            // and must not erase same-input failure evidence from either capture layer.
            String tickRaycastVerdict = cursor.get("raycastVerdict");
            if (hasEvidence(tickRaycastVerdict)
                    && !"FAIL".equals(target.get("raycastVerdict"))) {
                target.put("raycastVerdict", tickRaycastVerdict);
            }
            copyEvidence(cursor, target, "collisionVerdict");
            tryFinalizePendingLocked(pending, false);
        }
    }

    private static void enrichPendingFromOutlineLocked(Map<String, String> outline) {
        String subject = correlationValue(outline, "renderedOutlinePos");
        if ("none".equals(subject)) {
            return;
        }
        PendingVerification pending = newestPendingForSubject(subject);
        if (pending != null) {
            LinkedHashMap<String, String> target = targetObservationRow(pending);
            // The paired cursor row is tick-sampled and may target a different cell than this
            // frame's outline (pan skew); its dy is evidence for the placement only when both
            // samples name the placement's own cell.
            if (subject.equals(correlationValue(outline, "cursorFinalHitPos"))) {
                copyEvidence(outline, target, "cursorOutlineDy", "outlineDy");
            }
            copyEvidence(outline, target, "outlineVerdict");
            // The frame replay is the only same-input raycast comparison the live capture owns, so
            // its PASS/FAIL is the raycast component's explicit authority. Skew-limited tick rows
            // deliberately carry no copyable verdict and must not regain one.
            String frameRaycastVerdict = outline.get("frameRaycastVerdict");
            if ("FAIL".equals(frameRaycastVerdict)
                    || ("PASS".equals(frameRaycastVerdict)
                            && !"FAIL".equals(target.get("raycastVerdict")))) {
                target.put("raycastVerdict", frameRaycastVerdict);
            }
            target.put("outlineRenderRoute", outline.getOrDefault(
                    "outlineRenderRoute", "unknown"));
            tryFinalizePendingLocked(pending, false);
        }
    }

    private static void attachEnsembleCandidateLocked(LinkedHashMap<String, String> candidate) {
        String first = correlationValue(candidate, "pos");
        String second = correlationValue(candidate, "pairPos");
        PendingVerification pending = newestPendingForSubject(first);
        if (pending == null) {
            pending = newestPendingForSubject(second);
        }
        if (pending != null) {
            pending.ensembleCandidates.add(copy(candidate));
        }
    }

    private static PendingVerification newestPendingForSubject(String subject) {
        if (!hasEvidence(subject)) {
            return null;
        }
        PendingVerification newest = null;
        for (PendingVerification candidate : PENDING_VERIFICATIONS.values()) {
            if (!sameSubject(subject, candidate.placementPos)) {
                continue;
            }
            if (newest == null || candidate.createdNanos > newest.createdNanos) {
                newest = candidate;
            }
        }
        return newest;
    }

    private static boolean sameSubject(String first, String second) {
        BlockPos firstPos = parseShortPos(first);
        BlockPos secondPos = parseShortPos(second);
        if (firstPos != null && secondPos != null) {
            return firstPos.equals(secondPos);
        }
        return first != null && first.equals(second);
    }

    private static LinkedHashMap<String, String> targetObservationRow(PendingVerification pending) {
        return pending.clientRow == null ? pending.serverRow : pending.clientRow;
    }

    private static void copyEvidence(
            Map<String, String> source,
            Map<String, String> target,
            String field
    ) {
        copyEvidence(source, target, field, field);
    }

    private static void copyEvidence(
            Map<String, String> source,
            Map<String, String> target,
            String sourceField,
            String targetField
    ) {
        String value = source.get(sourceField);
        if (hasEvidence(value)) {
            target.put(targetField, value);
        }
    }

    private static void tryFinalizePendingLocked(PendingVerification pending, boolean force) {
        if (!PENDING_VERIFICATIONS.containsKey(pending.logicalAttemptId)) {
            return;
        }
        if (pending.serverRow == null && !force) {
            return;
        }
        if (force && pending.serverRow == null && "CLIENT_PENDING".equals(pending.attemptStatus)) {
            pending.attemptStatus = "CLIENT_ONLY";
        }
        LinkedHashMap<String, PlacementVerificationVerdict.Component> conflicts =
                new LinkedHashMap<>();
        LinkedHashMap<String, String> evidence = logicalAttemptEvidence(
                pending.clientRow, pending.serverRow, conflicts);
        PlacementVerificationVerdict.Result preview = PlacementVerificationVerdict.reduce(evidence);
        if (!force && preview.finalVerdict() == PlacementVerificationVerdict.FinalVerdict.INCONCLUSIVE) {
            return;
        }
        PENDING_VERIFICATIONS.remove(pending.logicalAttemptId);
        PlacementVerificationVerdict.FinalVerdict finalVerdict = writeLogicalAttempt(
                pending.clientRow,
                pending.serverRow,
                pending.logicalAttemptId,
                pending.attemptStatus,
                pending.playerProof);
        if (finalVerdict == PlacementVerificationVerdict.FinalVerdict.RED) {
            for (LinkedHashMap<String, String> candidate : pending.ensembleCandidates) {
                LinkedHashMap<String, String> promoted = copy(candidate);
                promoted.put("type", "ensemble_promoted");
                promoted.put("severity", "red");
                promoted.put("marker", "LIVE_" + promoted.getOrDefault("kind", "ENSEMBLE_UNKNOWN"));
                promoted.put("failureClasses", String.join(",", preview.failureClasses()));
                writeSession(promoted);
                writeMismatchRows(promoted, promoted.get("marker"));
            }
        }
    }

    private static BlockPos parseShortPos(String value) {
        if (!hasEvidence(value)) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static PlacementVerificationVerdict.FinalVerdict writeLogicalAttempt(
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
        return finalVerdict;
    }

    private static LinkedHashMap<String, String> logicalAttemptEvidence(
            LinkedHashMap<String, String> clientRow,
            LinkedHashMap<String, String> serverRow,
            LinkedHashMap<String, PlacementVerificationVerdict.Component> conflicts) {
        LinkedHashMap<String, String> evidence = new LinkedHashMap<>();

        for (String field : new String[]{
                "packetSequence", "playerUuid", "dimensionId",
                "actionType", "heldItem", "clickedOwnerPos", "clickedFace", "clickedHitVec",
                "placementPos", "placedBlockId",
                "rigCaseId", "placementRoute", "landingAuthority", "resolvedFloorDy",
                "expectedAfterDy", "intentDy",
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
                "validationDecision", "handlerDecision", "functionalOutcome",
                "stabilityPropertyChange", "stabilityFailure"
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
        for (PlacementVerificationVerdict.Component component : new PlacementVerificationVerdict.Component[]{
                PlacementVerificationVerdict.Component.MODEL,
                PlacementVerificationVerdict.Component.COLLISION,
                PlacementVerificationVerdict.Component.RAYCAST,
                PlacementVerificationVerdict.Component.OUTLINE,
                PlacementVerificationVerdict.Component.STABILITY}) {
            selectAttemptEvidence(
                    evidence,
                    component.fieldName(),
                    clientRow,
                    serverRow,
                    component,
                    conflicts);
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

    /**
     * The cursor row samples {@code minecraft.hitResult} at tick time while the pick itself is a
     * frame product, so a bare replay/pick disagreement is expected whenever the camera moves
     * between the two samples. A red split therefore requires same-ray proof: the raycast must have
     * skipped a strictly nearer pickable surface on the exact ray it was handed
     * ({@code raycastSkippedNearerSurface}). Do not re-add a cross-time equality as a red trigger.
     */
    private static String cursorMarkers(Map<String, String> row) {
        boolean outlineHit = isHit(row.get("outlineReplayHit"));
        boolean raycastHit = isHit(row.get("raycastReplayHit"));
        boolean skippedNearerSurface = Boolean.parseBoolean(
                row.getOrDefault("raycastSkippedNearerSurface", "false"));
        boolean lawfulLowered = isLawfulLoweredLane(row.get("finalOwnerLaneKind"));
        boolean targetInCollisionQuery = Boolean.parseBoolean(row.getOrDefault("targetCollisionIntersectsQueryBox", "false"));
        boolean targetReturnedByIterator = Boolean.parseBoolean(
                row.getOrDefault("playerBlockCollisionTargetIntersectsReturned", "false"));
        StringBuilder markers = new StringBuilder();
        appendMarker(markers, outlineHit && !raycastHit && skippedNearerSurface,
                "LIVE_CURSOR_OUTLINE_RAYCAST_SPLIT");
        appendMarker(markers, outlineHit && !raycastHit && skippedNearerSurface && lawfulLowered,
                "LIVE_CURSOR_GHOST_SURFACE");
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
        } else {
            // There is deliberately NO "clicked owner is an unnamed/vanilla slab therefore the new
            // block lands at dy 0" branch. That oracle encoded the pre-WYSIWYG assumption that a
            // vanilla owner implies a vanilla grid landing, and it is false for the mod's entire
            // reason to exist: a block placed onto a lowered/half owner seats on that owner's REAL
            // top surface, so dy -0.5, -1.0, -2.5 ... are all correct outcomes there. In the schema-6
            // live capture it produced 144 bogus expectations and every one of the 117 red action
            // rows, including live-confirmed pointed-dripstone continuations. The recorder has no
            // independent height model (recomputing one here would either be a second copy of the
            // resolver or, as before, simply wrong), so an undeclared expectation stays honestly
            // unknown; the frozen-anchor readback lane in PlacementVerificationVerdict is what
            // certifies these rows instead.
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
        // The exemption must judge the height this side actually SHOWS. A client row holding a
        // prediction reports afterDy as unknown by construction, so testing the exemption on afterDy
        // would deny it to exactly the rows it exists for and let the lane mismatch fire on correct
        // placements. clientDrawnDy is the drawn height; where the client holds none, afterDy IS what
        // it draws and remains the right operand.
        String clientDrawnDy = row.getOrDefault("clientDrawnDy", "none");
        String clientShownDy = hasEvidence(clientDrawnDy) ? clientDrawnDy : afterDy;
        boolean clientUnnamedDyCorrect = "client".equals(row.getOrDefault("side", ""))
                && ("unnamed_or_vanilla_slab".equals(afterLane)
                        || "client_pending_server_fact".equals(afterLane))
                && sameDy(expectedDy, clientShownDy);
        boolean laneMismatch = loweredExpected
                && !isLawfulLoweredLane(afterLane)
                && !clientUnnamedDyCorrect;
        StringBuilder markers = new StringBuilder();
        appendMarker(markers, Boolean.parseBoolean(row.getOrDefault("hiddenOwner", "false")),
                "LIVE_PLACEMENT_HIDDEN_OWNER");
        appendMarker(markers, dyMismatch, "LIVE_PLACEMENT_EXPECTED_DY_MISMATCH");
        appendMarker(markers, unclassifiedFailure, "LIVE_PLACEMENT_UNCLASSIFIED_FAILURE");
        appendMarker(markers, laneMismatch, "LIVE_PLACEMENT_EXPECTED_LANE_MISMATCH");
        // Judged on the shown height for the same reason as the exemption above: a lowered owner
        // that DRAWS a vanilla 0 is exactly this marker's subject, and afterDy cannot see it.
        appendMarker(markers, loweredExpected && sameDy("0.000000", clientShownDy),
                "LIVE_PLACEMENT_VANILLA_DY_FROM_LOWERED_OWNER");
        appendMarker(
                markers,
                PlacementVerificationVerdict.FinalVerdict.RED.name().equals(row.get("finalVerdict")),
                PlacementVerificationVerdict.FinalVerdict.RED.marker());
        boolean playerAuthored = ActionOrigin.PLAYER_AUTHORED.wireName()
                .equals(row.getOrDefault("actionOrigin", ActionOrigin.PLAYER_AUTHORED.wireName()));
        // Green-by-observed-evidence, never green-by-absence. This marker certifies exactly the two
        // lanes a live placement row actually observes: the player authored a placement that
        // succeeded (PLACED) and the placed cell's FROZEN stored dy is what the live geometry read
        // returns (ANCHOR — LAW.md clause 2). It deliberately does NOT stand in for
        // finalVerdict=GREEN, which additionally requires the model/collision/raycast/outline/
        // stability lanes; those are never captured on a placement row and are unobservable server
        // side, so that counter stays the full-rig gate. Requiring both component PASSes (not merely
        // the absence of a FAIL) is what keeps an evidence-free row out: with no afterStoredDy the
        // anchor lane is MISSING, not PASS, and no marker is written.
        boolean placedPass = PlacementVerificationVerdict.ComponentStatus.PASS.name()
                .equals(row.get(PlacementVerificationVerdict.Component.PLACED.fieldName()));
        boolean anchorPass = PlacementVerificationVerdict.ComponentStatus.PASS.name()
                .equals(row.get(PlacementVerificationVerdict.Component.ANCHOR.fieldName()));
        if (markers.isEmpty() && placementAction && playerAuthored && placedPass && anchorPass) {
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
            row.put("cursorOutlineDy", "unknown");
            return;
        }
        row.put("cursorOutlineBounds", cursor.getOrDefault("outlineBounds", "none"));
        row.put("cursorFinalHitPos", cursor.getOrDefault("finalHitPos", "none"));
        row.put("cursorFinalHitState", cursor.getOrDefault("finalHitState", "none"));
        row.put("cursorFinalHitVec", cursor.getOrDefault("finalHitVec", "none"));
        row.put("cursorFinalHitFace", cursor.getOrDefault("finalHitFace", "none"));
        row.put("cursorHeldItem", cursor.getOrDefault("heldItem", "none"));
        row.put("cursorOutlineDy", cursor.getOrDefault("outlineDy", "unknown"));
    }

    /**
     * The outline event is frame-sampled while its paired cursor row is tick-sampled, so a bare
     * position disagreement between the two is pan skew, not a defect. Red requires same-frame
     * incoherence: the event target disagreeing with the game's own frame pick, the frame replay
     * raycast disagreeing with the event target, or a same-cell shape change between the tick and
     * frame samples (a stale-shape signal). Cross-time position splits downgrade to INFO whenever
     * the frame itself was coherent; rows with no frame evidence keep the legacy red so a degraded
     * capture cannot silently pass. Do not re-add a cross-cell bounds comparison as a red trigger.
     */
    private static String renderedOutlineMarkers(Map<String, String> row) {
        String renderedBounds = row.getOrDefault("renderedOutlineBounds", "none");
        String cursorBounds = row.getOrDefault("cursorOutlineBounds", "none");
        String renderedPos = row.getOrDefault("renderedOutlinePos", "none");
        String cursorPos = row.getOrDefault("cursorFinalHitPos", "none");
        String framePos = row.getOrDefault("frameGamePickPos", "none");
        String frameRaycastVerdict = row.getOrDefault("frameRaycastVerdict", "none");
        boolean frameEvidence = isRealValue(framePos);
        boolean frameTargetSplit = frameEvidence && !framePos.equals(renderedPos);
        boolean frameRaycastSplit = "FAIL".equals(frameRaycastVerdict);
        // The event target and the frame pick are the same object on the first-party path, so bare
        // pos equality is plumbing verification, not exoneration. Downgrading a cross-time split to
        // INFO requires the frame replay to have actually run and passed; a not_run replay (third
        // person, degraded capture) leaves the legacy red in place, fail-closed.
        boolean frameCoherent = frameEvidence
                && !frameTargetSplit
                && "PASS".equals(frameRaycastVerdict);
        boolean pairedPosSplit = isRealValue(renderedPos)
                && isRealValue(cursorPos)
                && !renderedPos.equals(cursorPos);
        StringBuilder markers = new StringBuilder();
        appendMarker(markers, hasLargeBounds(renderedBounds), "LIVE_RENDERED_OUTLINE_LARGE_BOUNDS");
        appendMarker(markers, frameTargetSplit, "LIVE_FRAME_OUTLINE_TARGET_SPLIT");
        appendMarker(markers, frameRaycastSplit, "LIVE_FRAME_RAYCAST_SPLIT");
        appendMarker(markers, !pairedPosSplit
                        && isRealBounds(renderedBounds)
                        && isRealBounds(cursorBounds)
                        && !renderedBounds.equals(cursorBounds),
                "LIVE_RENDERED_OUTLINE_REPLAY_BOUNDS_SPLIT");
        appendMarker(markers, pairedPosSplit
                        && !frameCoherent
                        && !frameTargetSplit
                        && !frameRaycastSplit,
                "LIVE_RENDERED_OUTLINE_TARGET_SPLIT");
        appendMarker(markers, pairedPosSplit && frameCoherent,
                "INFO_RENDERED_OUTLINE_TICK_SKEW");
        return markers.isEmpty() ? "none" : markers.toString();
    }

    /** True when any marker token is a red {@code LIVE_} class (greens and INFO are not red). */
    private static boolean hasRedMarker(String markers) {
        if (markers == null || markers.isEmpty() || "none".equals(markers)) {
            return false;
        }
        for (String token : markers.split("\\|", -1)) {
            if (token.startsWith("LIVE_") && !token.startsWith("LIVE_GREEN_")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLawfulLoweredLane(String lane) {
        return "stored_placement_height".equals(lane)
                || "persistent_lowered_slab_carrier".equals(lane)
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
                        + '\t' + tsv(row.get("placedBlockId"))
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
                        + '\t' + tsv(row.get("playerProof"))
                        + '\t' + tsv(row.get("actualResult"))
                        + '\t' + tsv(row.get("finalVerdict"))
                        + '\t' + tsv(row.get("placedVerdict"))
                        + '\t' + tsv(row.get("anchorVerdict"))
                        + '\t' + tsv(row.get("modelVerdict"))
                        + '\t' + tsv(row.get("collisionVerdict"))
                        + '\t' + tsv(row.get("raycastVerdict"))
                        + '\t' + tsv(row.get("outlineVerdict"))
                        + '\t' + tsv(row.get("stabilityVerdict"))
                        + '\t' + tsv(row.get("missingRequiredComponents"))
                        + '\t' + tsv(row.get("failureClasses"))
                        + '\t' + tsv(row.get("intentDy"))
                        + '\t' + tsv(row.get("modelDy"))
                        + '\t' + tsv(row.get("collisionDy"))
                        + '\t' + tsv(row.get("raycastDy"))
                        + '\t' + tsv(row.get("outlineDy"))
                        + '\t' + tsv(row.get("placementRoute"))
                        + '\t' + tsv(row.get("landingAuthority"))
                        + '\t' + tsv(row.get("resolvedFloorDy")));
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
                        + '\t' + tsv(row.get("marker"))
                        + '\t' + tsv(row.get("frameGamePickPos"))
                        + '\t' + tsv(row.get("frameReplayPos"))
                        + '\t' + tsv(row.get("frameRaycastVerdict")));
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
        long now = System.nanoTime();
        if (lastSummaryWriteNanos != 0L
                && now - lastSummaryWriteNanos < SUMMARY_WRITE_INTERVAL_NANOS) {
            return;
        }
        lastSummaryWriteNanos = now;
        writeSummary(false);
    }

    private static void writeSummary(boolean runEnded) {
        String generatedAt = Instant.now().toString();
        StringBuilder text = new StringBuilder();
        text.append("# Slabbed Live Cursor Intent Recorder Summary\n\n");
        text.append("schemaVersion=").append(SCHEMA_VERSION).append('\n');
        text.append("recorderVersion=").append(RECORDER_VERSION).append('\n');
        text.append("generatedAt=").append(generatedAt).append('\n');
        text.append("runEnded=").append(runEnded).append('\n');
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
        text.append("renderedOutlineFrameTargetSplitRows=")
                .append(renderedOutlineFrameTargetSplitRows).append('\n');
        text.append("renderedOutlineFrameRaycastSplitRows=")
                .append(renderedOutlineFrameRaycastSplitRows).append('\n');
        text.append("renderedOutlineTickSkewInfoRows=")
                .append(renderedOutlineTickSkewInfoRows).append('\n');
        text.append("renderedOutlineFrameTriadGreenRows=")
                .append(renderedOutlineFrameTriadGreenRows).append('\n');
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
        text.append("ensembleCandidateRows=").append(ensembleCandidateRows).append('\n');
        text.append("ensembleOccludedOccupancyInfoRows=")
                .append(ensembleOccludedOccupancyInfoRows).append('\n');
        // Sentinel liveness (green-by-evidence, not green-by-absence): zero red rows only counts as a
        // clean bill when these show the probe actually armed and judged during the session.
        text.append("sentinelArmedTotal=").append(SlabModelStaleSentinel.armedTotalCount()).append('\n');
        text.append("sentinelSamplePasses=").append(SlabModelStaleSentinel.samplePassCount()).append('\n');
        String markdown = text.toString();
        writeFile("summary.md", markdown, false);
        writeFile("triage.md", markdown, false);

        String summaryJson = "{"
                + jsonPair("schemaVersion", SCHEMA_VERSION) + ","
                + jsonPair("recorderVersion", RECORDER_VERSION) + ","
                + jsonPair("captureId", CAPTURE_UUID) + ","
                + jsonPair("generatedAt", generatedAt) + ","
                + "\"runEnded\":" + runEnded + ","
                + jsonPair("gitSha", BuildStamp.GIT_SHA) + ","
                + jsonPair("runtimeContentSha256", BuildStamp.RUNTIME_CONTENT_SHA256) + ","
                + jsonPair("recorderContentSha256", recorderContentSha256()) + ","
                + "\"redCounters\":{"
                + "\"placementVerdictRedRows\":" + placementVerdictRedRows + ","
                + "\"placementVerdictUnclassifiedFailureRows\":"
                + placementVerdictUnclassifiedFailureRows + ","
                + "\"logicalAttemptVerdictRedRows\":" + logicalAttemptVerdictRedRows + ","
                + "\"logicalAttemptVerdictUnclassifiedFailureRows\":"
                + logicalAttemptVerdictUnclassifiedFailureRows + ","
                + "\"modelStaleDivergentRows\":" + modelStaleDivergentRows + ","
                + "\"modelStaleAbsentRows\":" + modelStaleAbsentRows + ","
                + "\"renderedOutlineFrameTargetSplitRows\":"
                + renderedOutlineFrameTargetSplitRows + ","
                + "\"renderedOutlineFrameRaycastSplitRows\":"
                + renderedOutlineFrameRaycastSplitRows + ","
                + "\"renderedOutlineReplayBoundsSplitRows\":"
                + renderedOutlineReplayBoundsSplitRows + ","
                + "\"renderedOutlineTargetSplitRows\":" + renderedOutlineTargetSplitRows
                + "},"
                + "\"warningCounters\":{"
                + "\"placementVerdictInconclusiveRows\":" + placementVerdictInconclusiveRows + ","
                + "\"logicalAttemptVerdictInconclusiveRows\":"
                + logicalAttemptVerdictInconclusiveRows + ","
                + "\"modelStaleYellowRows\":" + modelStaleYellowRows + ","
                + "\"ensembleCandidateRows\":" + ensembleCandidateRows + ","
                + "\"renderedOutlineTickSkewInfoRows\":" + renderedOutlineTickSkewInfoRows
                + "},"
                + "\"health\":{"
                + "\"cursorRows\":" + cursorRows + ","
                + "\"renderedOutlineRows\":" + renderedOutlineRows + ","
                + "\"renderedOutlineFrameTriadGreenRows\":"
                + renderedOutlineFrameTriadGreenRows + ","
                + "\"actionRows\":" + actionRows + ","
                + "\"logicalAttemptRows\":" + logicalAttemptRows + ","
                + "\"breakRows\":" + breakRows + ","
                + "\"sentinelArmedTotal\":" + SlabModelStaleSentinel.armedTotalCount() + ","
                + "\"sentinelSamplePasses\":" + SlabModelStaleSentinel.samplePassCount()
                + "}"
                + "}\n";
        writeFile("summary.json", summaryJson, false);
        writeFile("triage.json", summaryJson, false);
    }

    private static String recorderContentSha256() {
        return RecorderIdentity.CONTENT_SHA256;
    }

    private static final class RecorderIdentity {
        private static final String CONTENT_SHA256 = BuildStamp.extendRuntimeContentSha256(
                LiveCursorIntentRecorder.class,
                SlabModelStaleSentinel.class,
                PlacementVerificationVerdict.class,
                com.slabbed.util.SlabEnsembleCoherence.class,
                com.slabbed.devtools.SlabbedDevtools.class,
                com.slabbed.devtools.client.SlabbedDevtoolsClient.class,
                com.slabbed.devtools.client.LiveCursorCaptureClient.class);
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
                    ? net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("live-cursor-recorder")
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
                            + "\trenderedOutlineCameraRelativeBounds\trenderedOutlineHitVec\tmarker"
                            + "\tframeGamePickPos\tframeReplayPos\tframeRaycastVerdict\n");
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
                "mismatches.tsv", "summary.md", "summary.json", "triage.md", "triage.json"}) {
            Path path = requested.resolve(artifact);
            if (Files.exists(path) && Files.size(path) > 0L) {
                return requested.resolve("schema-" + SCHEMA_VERSION + "-" + CAPTURE_UUID);
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
                + jsonPair("runId", CAPTURE_UUID) + ","
                + jsonPair("recorder", "LiveCursorIntentRecorder") + ","
                + jsonPair("recorderVersion", RECORDER_VERSION) + ","
                + jsonPair("actionOriginContract", "PLAYER_AUTHORED|AUTO_USEON_PROXY") + ","
                + jsonPair("placementVerdictContract", "PlacementVerificationVerdict-v3") + ","
                + jsonPair("logicalAttemptContract", "LogicalPlacementAttempt-v1") + ","
                + jsonPair("enabled", Boolean.toString(enabled)) + ","
                + jsonPair("createdAt", Instant.now().toString()) + ","
                + jsonPair("dir", sessionDir.toAbsolutePath().toString()) + ","
                + jsonPair("gameDir", gameDirDisplay()) + ","
                + jsonPair("javaVersion", System.getProperty("java.version", "unknown")) + ","
                + jsonPair("javaVmName", System.getProperty("java.vm.name", "unknown")) + ","
                + jsonPair("minecraftVersion", modVersion("minecraft")) + ","
                + jsonPair("fabricLoaderVersion", modVersion("fabricloader")) + ","
                + jsonPair("neoForgeVersion", modVersion("neoforge")) + ","
                + jsonPair("slabbedVersion", modVersion(Slabbed.MOD_ID)) + ","
                // Jar-identity stamp (anti-whack-a-mole audit): a session log must be attributable to an
                // exact build — version strings alone have already collided across different artifacts.
                + jsonPair("gitSha", BuildStamp.GIT_SHA) + ","
                + jsonPair("buildTime", BuildStamp.BUILD_TIME) + ","
                + jsonPair("jarFile", BuildStamp.JAR_FILE) + ","
                + jsonPair("runtimeContentSha256", BuildStamp.RUNTIME_CONTENT_SHA256) + ","
                + jsonPair("recorderContentSha256", recorderContentSha256()) + ","
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
        try {
            net.neoforged.fml.ModList modList = net.neoforged.fml.ModList.get();
            return modList == null
                    ? "unknown"
                    : modList.getModContainerById(modId)
                            .map(container -> container.getModInfo().getVersion().toString())
                            .orElse("unknown");
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    private static String gameDirDisplay() {
        try {
            Path gameDir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
            if (gameDir != null) {
                return gameDir.toAbsolutePath().toString();
            }
        } catch (RuntimeException ignored) {
            // A pure unit test has no loader-owned game directory.
        }
        return sessionDir == null ? "unknown" : sessionDir.toAbsolutePath().toString();
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
