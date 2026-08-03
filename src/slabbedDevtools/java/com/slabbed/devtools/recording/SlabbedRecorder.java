package com.slabbed.devtools.recording;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabbedDiagnosticsBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Runtime owner for one schema-6 recorder session in the optional addon. */
public final class SlabbedRecorder {
    public static final String DIR_PROPERTY = "slabbed.recorderDir";
    private static final Duration EXPECTATION_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration ORIGIN_SCOPE_TIMEOUT = Duration.ofSeconds(10);
    private static final Object LOCK = new Object();

    private static volatile Schema6Session session;
    private static volatile Map<String, String> runtimeIdentity = Map.of();
    private static volatile TargetExpectation lastTarget;
    private static final ArrayDeque<OriginLease> ORIGIN_SCOPES = new ArrayDeque<>();
    private static boolean shutdownHookRegistered;

    private record TargetExpectation(
            BlockPos targetPos,
            BlockPos expectedPlacePos,
            Direction face,
            String half,
            Instant notedAt) {
    }

    private static final class OriginLease {
        private final String id;
        private final String origin;
        private final SlabbedDiagnosticsBridge.ActionOriginContext context;
        private final Instant expiresAt;
        private boolean closed;
        private boolean clientSeen;
        private boolean serverSeen;

        private OriginLease(
                String id,
                String origin,
                SlabbedDiagnosticsBridge.ActionOriginContext context,
                Instant expiresAt) {
            this.id = id;
            this.origin = origin;
            this.context = context;
            this.expiresAt = expiresAt;
        }

        private boolean matches(Map<String, String> row) {
            return context.playerUuid().equals(row.get("playerUuid"))
                    && context.dimensionId().equals(row.get("dimensionId"))
                    && context.placementPos().toShortString().equals(row.get("placementPos"));
        }

        private void observe(String side) {
            if ("client".equals(side)) {
                clientSeen = true;
            } else if ("server".equals(side)) {
                serverSeen = true;
            }
        }

        private boolean complete() {
            return clientSeen && serverSeen;
        }
    }

    private SlabbedRecorder() {
    }

    public static void configureRuntimeIdentity(Map<String, String> identity) {
        runtimeIdentity = identity == null ? Map.of() : Map.copyOf(identity);
    }

    public static boolean isEnabled() {
        return session != null;
    }

    public static Path currentLogPath() {
        Schema6Session active = session;
        return active == null ? null : active.directory();
    }

    public static boolean toggle() {
        return setEnabled(!isEnabled());
    }

    public static boolean setEnabled(boolean value) {
        synchronized (LOCK) {
            if (value && session == null) {
                return startFreshSessionLocked();
            }
            if (!value && session != null) {
                closeSessionLocked("command_off");
            }
            return session != null;
        }
    }

    /** Disconnect closes this world's evidence but does not change the launch-level desired flag. */
    public static void stopForWorldLeave() {
        synchronized (LOCK) {
            closeSessionLocked("world_leave");
        }
    }

    public static void flush() {
        synchronized (LOCK) {
            if (session == null) {
                return;
            }
            try {
                session.updateSentinelLiveness(
                        SlabModelStaleSentinel.armedTotalCount(),
                        SlabModelStaleSentinel.samplePassCount());
                session.flush();
            } catch (IOException error) {
                warn("flush", error);
            }
        }
    }

    /** Rewrites liveness counters without finalizing the one-second pairing window. */
    public static void updateSentinelLiveness() {
        Schema6Session active = session;
        if (active == null) {
            return;
        }
        try {
            active.updateSentinelLiveness(
                    SlabModelStaleSentinel.armedTotalCount(),
                    SlabModelStaleSentinel.samplePassCount());
        } catch (IOException error) {
            warn("sentinel_liveness", error);
        }
    }

    public static void recordCursor(LinkedHashMap<String, String> fields) {
        Schema6Session active = session;
        if (active == null) {
            return;
        }
        try {
            active.recordCursor(fields);
        } catch (IOException error) {
            warn("cursor", error);
        }
    }

    public static void recordAction(LinkedHashMap<String, String> fields) {
        Schema6Session active = session;
        if (active == null) {
            return;
        }
        LinkedHashMap<String, String> row = fields == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(fields);
        TargetExpectation target = currentTarget();
        if (target != null) {
            appendClientTargetDefaults(
                    row,
                    target.targetPos(),
                    target.expectedPlacePos(),
                    target.face(),
                    target.half());
        }
        String hint = row.remove("originHint");
        String origin = currentOrigin(hint, row);
        if (origin == null) {
            return;
        }
        try {
            active.recordAction(row, origin);
        } catch (IOException error) {
            warn("action", error);
        }
    }

    public static void recordRigCase(LinkedHashMap<String, String> fields) {
        Schema6Session active = session;
        if (active == null) {
            return;
        }
        try {
            active.recordRigCase(fields);
        } catch (IOException error) {
            warn("rig_case", error);
        }
    }

    public static void recordRenderedOutline(LinkedHashMap<String, String> fields) {
        Schema6Session active = session;
        if (active == null) {
            return;
        }
        try {
            active.recordRenderedOutline(fields);
        } catch (IOException error) {
            warn("rendered_outline", error);
        }
    }

    public static void recordSentinel(LinkedHashMap<String, String> fields) {
        Schema6Session active = session;
        if (active == null) {
            return;
        }
        try {
            active.recordSentinel(fields);
            active.updateSentinelLiveness(
                    SlabModelStaleSentinel.armedTotalCount(),
                    SlabModelStaleSentinel.samplePassCount());
        } catch (IOException error) {
            warn("sentinel", error);
        }
    }

    public static void log(String tag, String body) {
        Schema6Session active = session;
        if (active == null) {
            return;
        }
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("tag", tag == null ? "diagnostic" : tag);
        row.put("body", body == null ? "" : body);
        try {
            // Legacy call sites remain visible without creating a seventh evidence artifact.
            if ("place_row".equals(tag)) {
                row.put("side", parseField(body, "side", "server").toLowerCase(java.util.Locale.ROOT));
                row.put("actionType", "place_block");
                row.put("heldItem", parseField(body, "heldItem", "none"));
                row.put("placementPos", parsePositionField(body, "placePos"));
                row.put("afterDy", parseField(body, "finalDy", "none"));
                row.put("afterStoredDy", parseField(body, "storedDy", "none"));
                row.put("afterStoredDyBits", rawBits(row.get("afterStoredDy")));
                row.put("originHint", normalizeLegacyOrigin(parseField(body, "origin", "player")));
                recordAction(row);
            } else if ("remove".equals(tag)) {
                LinkedHashMap<String, String> lifecycle = new LinkedHashMap<>();
                lifecycle.put("event", "remove");
                lifecycle.put("pos", parsePositionField(body, "pos"));
                lifecycle.put("detail", body == null ? "" : body);
                lifecycle.put("mismatchMarker", "none");
                active.recordCursor(lifecycle);
            } else {
                LinkedHashMap<String, String> cursor = new LinkedHashMap<>();
                cursor.put("event", tag == null ? "diagnostic" : tag);
                cursor.put("detail", body == null ? "" : body);
                active.recordCursor(cursor);
            }
        } catch (IOException error) {
            warn("legacy_" + tag, error);
        }
    }

    public static void noteTarget(
            BlockPos targetPos,
            BlockPos expectedPlacePos,
            Direction face,
            String half) {
        if (session == null) {
            return;
        }
        lastTarget = new TargetExpectation(
                targetPos.immutable(),
                expectedPlacePos.immutable(),
                face,
                half,
                Instant.now());
    }

    public static void checkPlacement(BlockPos actualPos, BlockState actualState) {
        Schema6Session active = session;
        TargetExpectation target = currentTarget();
        if (active == null || target == null) {
            return;
        }
        try {
            if (!actualPos.equals(target.expectedPlacePos())
                    && !actualPos.equals(target.targetPos())) {
                active.recordMismatch(
                        "wysiwyg",
                        "LIVE_PLACEMENT_POSITION_MISMATCH",
                        actualPos.toShortString(),
                        "none",
                        "expected=" + target.expectedPlacePos().toShortString()
                                + " target=" + target.targetPos().toShortString());
                return;
            }
            if (!actualPos.equals(target.expectedPlacePos())
                    || target.face() == null
                    || !target.face().getAxis().isHorizontal()
                    || !(actualState.getBlock() instanceof SlabBlock)
                    || !actualState.hasProperty(SlabBlock.TYPE)) {
                return;
            }
            SlabType actual = actualState.getValue(SlabBlock.TYPE);
            SlabType expected = "UPPER".equals(target.half())
                    ? SlabType.TOP
                    : "LOWER".equals(target.half()) ? SlabType.BOTTOM : null;
            if (expected != null && actual != SlabType.DOUBLE && actual != expected) {
                active.recordMismatch(
                        "wysiwyg",
                        "LIVE_PLACEMENT_TYPE_MISMATCH",
                        actualPos.toShortString(),
                        "none",
                        "expected=" + expected + " actual=" + actual);
            }
        } catch (IOException error) {
            warn("placement_check", error);
        }
    }

    /**
     * Cross-thread, time-bounded scope for a single explicit TEST proxy action. The scope is global
     * because Forge's client key click and integrated-server placement event run on different
     * threads; its short lifetime and explicit command boundary prevent proxy rows counting as
     * player-authored evidence.
     */
    public static SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(
            String origin,
            SlabbedDiagnosticsBridge.ActionOriginContext context) {
        String normalized = normalizeOrigin(origin);
        if (!SlabbedDiagnosticsBridge.AUTO_USEON_PROXY.equals(normalized)) {
            return () -> { };
        }
        OriginLease lease = new OriginLease(
                UUID.randomUUID().toString(),
                normalized,
                context,
                Instant.now().plus(ORIGIN_SCOPE_TIMEOUT));
        synchronized (LOCK) {
            pruneOriginScopes();
            ORIGIN_SCOPES.addLast(lease);
        }
        return () -> {
            synchronized (LOCK) {
                lease.closed = true;
                // A synchronous server observation is the authoritative end of a server-run
                // proxy action. Retain only a client-only lease for its delayed server half;
                // otherwise a closed Mega lease could relabel a later manual action at that cell.
                if (!lease.clientSeen || lease.serverSeen) {
                    ORIGIN_SCOPES.remove(lease);
                }
            }
        };
    }

    private static boolean startFreshSessionLocked() {
        try {
            registerShutdownHookLocked();
            String configured = System.getProperty(DIR_PROPERTY, "").trim();
            Path base = configured.isEmpty()
                    ? FMLPaths.GAMEDIR.get().resolve("slabbed-recorder")
                    : Path.of(configured);
            LinkedHashMap<String, String> manifest = new LinkedHashMap<>(runtimeIdentity);
            manifest.put("javaVersion", System.getProperty("java.version", "unknown"));
            manifest.put("javaVmName", System.getProperty("java.vm.name", "unknown"));
            manifest.put("javaCommand", System.getProperty("sun.java.command", ""));
            manifest.put("debugDefault", "on");
            manifest.put("recordDefault", "on");
            manifest.put("modelStaleSentinel", "present");
            session = Schema6Session.open(base, manifest);
            lastTarget = null;
            Slabbed.LOGGER.info(
                    "[SLABBED_DEVTOOLS_RECORDER_START] schema=6 defaults=debug:on,record:on dir={}",
                    session.directory().toAbsolutePath().normalize());
            return true;
        } catch (IOException | RuntimeException error) {
            session = null;
            warn("start", error);
            return false;
        }
    }

    private static void closeSessionLocked(String reason) {
        Schema6Session active = session;
        if (active == null) {
            return;
        }
        try {
            active.updateSentinelLiveness(
                    SlabModelStaleSentinel.armedTotalCount(),
                    SlabModelStaleSentinel.samplePassCount());
            active.close();
            Slabbed.LOGGER.info(
                    "[SLABBED_DEVTOOLS_RECORDER_STOP] reason={} dir={}",
                    reason,
                    active.directory().toAbsolutePath().normalize());
        } catch (IOException error) {
            warn("stop_" + reason, error);
        } finally {
            session = null;
            lastTarget = null;
            ORIGIN_SCOPES.clear();
        }
    }

    private static TargetExpectation currentTarget() {
        TargetExpectation target = lastTarget;
        if (target == null
                || Duration.between(target.notedAt(), Instant.now()).compareTo(EXPECTATION_TIMEOUT) > 0) {
            return null;
        }
        return target;
    }

    private static String currentOrigin(String hint, Map<String, String> row) {
        String normalizedHint = normalizeOrigin(hint);
        if (SlabbedDiagnosticsBridge.GAMETEST.equals(normalizedHint)) {
            return normalizedHint;
        }
        synchronized (LOCK) {
            pruneOriginScopes();
            var iterator = ORIGIN_SCOPES.descendingIterator();
            while (iterator.hasNext()) {
                OriginLease lease = iterator.next();
                if (!lease.matches(row)) {
                    continue;
                }
                appendRigIdentity(row, lease.context);
                lease.observe(row.get("side"));
                if (lease.complete()) {
                    iterator.remove();
                }
                return lease.origin;
            }
            return normalizedHint;
        }
    }

    private static void appendRigIdentity(
            Map<String, String> row,
            SlabbedDiagnosticsBridge.ActionOriginContext context) {
        if (!context.hasRigCase()) {
            return;
        }
        row.put("rigCaseId", context.rigCaseId());
        row.put("rigLabel", context.rigLabel());
        row.put("rigExpectedDy", Double.toString(
                Double.longBitsToDouble(context.rigExpectedDyBits())));
        row.put("rigExpectedDyBits", Long.toUnsignedString(context.rigExpectedDyBits()));
        row.put("rigFace", context.rigFace().getName());
        row.put("rigOrientation", context.rigOrientation());
    }

    private static void putIfMissingEvidence(
            Map<String, String> row,
            String key,
            String value) {
        String current = row.get(key);
        if (current == null || current.isBlank() || "none".equals(current)) {
            row.put(key, value);
        }
    }

    /** Pure seam used by the schema harness to pin the no-client-aim-on-server law. */
    static void appendClientTargetDefaults(
            Map<String, String> row,
            BlockPos targetPos,
            BlockPos expectedPlacePos,
            Direction face,
            String half) {
        if (!"client".equalsIgnoreCase(row.getOrDefault("side", ""))) {
            return;
        }
        putIfMissingEvidence(row, "clickedOwnerPos", targetPos.toShortString());
        putIfMissingEvidence(row, "clickedFace", face.getName());
        putIfMissingEvidence(row, "expectedPlacementPos", expectedPlacePos.toShortString());
        putIfMissingEvidence(row, "targetHalf", half);
    }

    private static void pruneOriginScopes() {
        Instant now = Instant.now();
        ORIGIN_SCOPES.removeIf(scope -> scope.expiresAt.isBefore(now));
    }

    private static String normalizeOrigin(String origin) {
        return switch (origin == null ? "" : origin) {
            case SlabbedDiagnosticsBridge.PLAYER_AUTHORED -> SlabbedDiagnosticsBridge.PLAYER_AUTHORED;
            case SlabbedDiagnosticsBridge.AUTO_USEON_PROXY -> SlabbedDiagnosticsBridge.AUTO_USEON_PROXY;
            case SlabbedDiagnosticsBridge.GAMETEST -> SlabbedDiagnosticsBridge.GAMETEST;
            default -> null;
        };
    }

    static void resetOriginScopesForTest() {
        synchronized (LOCK) {
            ORIGIN_SCOPES.clear();
        }
    }

    static String resolveOriginForTest(String hint, Map<String, String> row) {
        return currentOrigin(hint, row);
    }

    private static String normalizeLegacyOrigin(String origin) {
        return "fake_player".equals(origin)
                ? SlabbedDiagnosticsBridge.GAMETEST
                : SlabbedDiagnosticsBridge.PLAYER_AUTHORED;
    }

    private static String parseField(String body, String key, String fallback) {
        if (body == null) {
            return fallback;
        }
        String needle = key + "=";
        int start = body.indexOf(needle);
        if (start < 0) {
            return fallback;
        }
        start += needle.length();
        int end = body.indexOf(' ', start);
        return end < 0 ? body.substring(start) : body.substring(start, end);
    }

    private static String parsePositionField(String body, String key) {
        if (body == null) {
            return "none";
        }
        String needle = key + "=";
        int start = body.indexOf(needle);
        if (start < 0) {
            return "none";
        }
        start += needle.length();
        int firstSpace = body.indexOf(' ', start);
        if (firstSpace < 0) {
            return body.substring(start);
        }
        int secondSpace = body.indexOf(' ', firstSpace + 1);
        if (secondSpace < 0) {
            return body.substring(start);
        }
        return body.substring(start, secondSpace);
    }

    private static String rawBits(String value) {
        try {
            return Long.toUnsignedString(Double.doubleToRawLongBits(Double.parseDouble(value)));
        } catch (RuntimeException ignored) {
            return "none";
        }
    }

    private static void registerShutdownHookLocked() {
        if (shutdownHookRegistered) {
            return;
        }
        shutdownHookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> {
                    synchronized (LOCK) {
                        closeSessionLocked("jvm_shutdown");
                    }
                },
                "slabbed-schema6-recorder-shutdown"));
    }

    private static void warn(String operation, Throwable error) {
        Slabbed.LOGGER.warn(
                "[SLABBED_DEVTOOLS_RECORDER_IO_ERROR] operation={} error={}",
                operation,
                error.toString());
    }
}
