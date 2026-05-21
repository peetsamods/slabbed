package com.slabbed.client.debug;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Default-off MC 1.21.1 proof for full-block/slab and exact-fixture visual overlap.
 *
 * <p>This is diagnostic only. It reads client state and logs geometry rows; it does
 * not place blocks, mutate attachments, retarget hits, schedule rerenders, or alter
 * rendering.
 */
public final class Mc1211FullBlockSlabOverlapProofClient {
    private static final double EPSILON = 1.0e-6d;
    private static final int MANUAL_INTERVAL = 10;
    private static final int CROSSHAIR_INTERVAL = 20;
    private static final int SCAN_INTERVAL = 40;
    private static final int[] DETERMINISTIC_PHASE_TICKS = {0, 1, 5, 20};
    private static final int FIXTURE_STRIDE = 5;
    private static final int FIXTURE_CLEAR_HEIGHT = 7;
    private static final int MAX_ROWS = Integer.getInteger(
            "slabbed.mc1211.fullBlockSlabOverlapProofMaxRows", 200);
    private static final int SCAN_RADIUS = Integer.getInteger(
            "slabbed.mc1211.fullBlockSlabOverlapProofRadius", 40);
    private static final int RELOAD_CHUNK_LOAD_BUDGET_TICKS = Integer.getInteger(
            "slabbed.mc1211.deterministicOverlapReloadChunkLoadBudgetTicks", 200);
    private static final int RELOAD_RENDER_WARM_TICKS = Integer.getInteger(
            "slabbed.mc1211.deterministicOverlapReloadRenderWarmTicks", 40);
    private static final String SERVER_STATE_MATRIX_PROPERTY =
            "slabbed.mc1211.serverStateOverlapMatrix";

    private static final Set<String> LOGGED_ROWS = new HashSet<>();
    private static final Map<String, Integer> FIRST_SEEN_TICK_BY_SIGNATURE = new HashMap<>();
    private static boolean initialized;
    private static boolean startLogged;
    private static boolean routeCanaryLogged;
    private static boolean serverStateMatrixRequested;
    private static volatile boolean serverStateMatrixComplete;
    private static volatile boolean serverStateMatrixFailed;
    private static volatile String serverStateMatrixFailureReason = "not_started";
    private static volatile int serverStateMatrixRows;
    private static volatile int serverStateMatrixGreenRows;
    private static volatile int serverStateMatrixRedRows;
    private static volatile int serverStateMatrixDeferredRows;
    private static volatile int serverStateMatrixInconclusiveRows;
    private static volatile String serverStateMatrixFirstRed = "none";
    private static boolean serverStateMatrixFailureThrown;
    private static boolean serverStateMatrixStopRequested;
    private static int serverStateMatrixTicks;
    private static boolean deterministicRequested;
    private static volatile boolean deterministicAuthoringComplete;
    private static volatile boolean deterministicAuthoringFailed;
    private static volatile String deterministicAuthoringFailureReason = "not_started";
    private static volatile String deterministicAuthoringMode = "n/a";
    private static volatile boolean deterministicFixtureMatched;
    private static volatile BlockPos deterministicOrigin;
    private static int deterministicAuthoringClientTick = -1;
    private static final Set<Integer> DETERMINISTIC_PHASES_EMITTED = new HashSet<>();
    private static List<FixturePair> deterministicPairs = List.of();
    private static int ticks;
    private static int rows;
    private static int greenRows;
    private static int redRows;
    private static int inconclusiveRows;
    // Reload-phase state.
    private static boolean reloadStartLogged;
    private static boolean preReloadEmitted;
    private static boolean postReloadEmitted;
    private static boolean reloadSummaryEmitted;
    private static boolean reloadInconclusiveEmitted;
    private static int reloadFirstReadyTick = -1;
    private static SignatureLoadResult loadedSignature;
    private static int reloadRows;
    private static int reloadGreenRows;
    private static int reloadRedRows;
    private static int reloadInconclusiveRows;

    private Mc1211FullBlockSlabOverlapProofClient() {
    }

    public static void init() {
        if (initialized || (!enabled() && !serverStateMatrixRouteEnabled())) {
            return;
        }
        initialized = true;
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            Slabbed.LOGGER.info(
                    "[MC1211_FULL_BLOCK_SLAB_OVERLAP_START] enabled=false reason=not_development_environment diagnosticsOnly=true");
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(Mc1211FullBlockSlabOverlapProofClient::onEndClientTick);
    }

    private static void onEndClientTick(MinecraftClient client) {
        boolean serverStateMatrixRoute = serverStateMatrixRouteEnabled();
        if (!enabled() && !serverStateMatrixRoute) {
            return;
        }
        if (serverStateMatrixRoute) {
            runServerStateMatrixRoute(client);
            if (!enabled()) {
                return;
            }
        }
        if (client == null || client.world == null || client.player == null) {
            return;
        }
        if (!startLogged) {
            startLogged = true;
            Slabbed.LOGGER.info(
                    "[MC1211_FULL_BLOCK_SLAB_OVERLAP_START] enabled=true exactFixture={} diagnosticsOnly=true radius={} maxRows={} manualAim=aim_at_full_block_slab_or_wall_lantern_fixture",
                    exactEnabled(),
                    SCAN_RADIUS,
                    MAX_ROWS);
            if (exactEnabled()) {
                Slabbed.LOGGER.info(
                        "[MC1211_EXACT_FIXTURE_OVERLAP_START] enabled=true diagnosticsOnly=true instructions=\"aim at the visually merged full-block/slab fixture; place/reload if needed\" focusedWatch=\"{}\"",
                        focusedWatchDescription());
            }
            if (deterministicEnabled()) {
                Slabbed.LOGGER.info(
                        "[MC1211_DETERMINISTIC_OVERLAP_FIXTURE_START] enabled=true diagnosticsOnly=true originProperty=\"{}\" phaseTicks={} reloadPhase=manual_not_automatic",
                        System.getProperty("slabbed.mc1211.overlapFixtureOrigin", "auto"),
                        Arrays.toString(DETERMINISTIC_PHASE_TICKS));
            }
        }
        if (reloadEnabled() && !reloadStartLogged) {
            reloadStartLogged = true;
            Slabbed.LOGGER.info(
                    "[MC1211_DETERMINISTIC_OVERLAP_RELOAD_START] enabled=true mode={} originProperty=\"{}\" signaturePathProperty=\"{}\" diagnosticsOnly=true semanticsChanged=false",
                    reloadPhaseMode(),
                    System.getProperty("slabbed.mc1211.overlapFixtureOrigin", "unset"),
                    System.getProperty("slabbed.mc1211.deterministicOverlapReloadSignaturePath", "unset"));
        }
        ticks++;
        if (deterministicEnabled()) {
            if (reloadEnabled()) {
                runDeterministicReloadFixture(client);
            } else {
                runDeterministicFixtureMatrix(client);
            }
        }
        if (reloadEnabled()) {
            // Reload mode is exclusive: skip generic crosshair/manual/scan emissions
            // so the reload proof is the only thing logged.
            return;
        }
        if (ticks % CROSSHAIR_INTERVAL == 0) {
            captureCrosshair(client, client.world);
        }
        if (exactEnabled() && ticks % MANUAL_INTERVAL == 0) {
            captureManualContext(client, client.world);
        }
        if (ticks % SCAN_INTERVAL == 5) {
            scanNearbyPairs(client, client.world);
        }
        if (exactEnabled() && ticks % CROSSHAIR_INTERVAL == 5) {
            captureFocusedWatchPositions(client.world);
        }
    }

    private static void runServerStateMatrixRoute(MinecraftClient client) {
        serverStateMatrixTicks++;
        logRouteCanary(client);
        if (serverStateMatrixComplete) {
            if (serverStateMatrixFailed && !serverStateMatrixFailureThrown) {
                serverStateMatrixFailureThrown = true;
                throw new IllegalStateException(
                        "MC1211 server-state overlap matrix failed: "
                                + serverStateMatrixFailureReason);
            }
            if (!serverStateMatrixStopRequested && client != null) {
                serverStateMatrixStopRequested = true;
                client.scheduleStop();
            }
            return;
        }
        if (serverStateMatrixRequested) {
            return;
        }
        if (client == null || client.world == null || client.player == null) {
            if (serverStateMatrixTicks > 1200) {
                markServerStateMatrixRouteFail("client_world_or_player_not_ready_within_1200_ticks");
            }
            return;
        }
        MinecraftServer server = client.getServer();
        if (server == null) {
            markServerStateMatrixRouteFail("integrated_server_unavailable");
            return;
        }
        ServerWorld serverWorld = server.getWorld(client.world.getRegistryKey());
        if (serverWorld == null) {
            markServerStateMatrixRouteFail("matching_server_world_unavailable");
            return;
        }
        BlockPos origin = chooseDeterministicOrigin(serverWorld, client.player.getBlockPos());
        if (origin == null) {
            markServerStateMatrixRouteFail("fixture_area_not_clear");
            return;
        }
        serverStateMatrixRequested = true;
        final BlockPos finalOrigin = origin;
        server.execute(() -> executeServerStateMatrix(serverWorld, finalOrigin));
    }

    private static void logRouteCanary(MinecraftClient client) {
        if (!routeCanaryLogged) {
            routeCanaryLogged = true;
            Slabbed.LOGGER.info(
                    "[MC1211_ROUTE_CANARY] class=Mc1211FullBlockSlabOverlapProofClient method=logRouteCanary route=runClientGameTest property={} tick={} worldReady={} playerReady={}",
                    serverStateMatrixRoutePropertyState(),
                    serverStateMatrixTicks,
                    client != null && client.world != null,
                    client != null && client.player != null);
        }
    }

    private static void captureCrosshair(MinecraftClient client, ClientWorld world) {
        if (rows >= MAX_ROWS || !(client.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos targetPos = hit.getBlockPos();
        BlockState targetState = world.getBlockState(targetPos);
        BlockPos belowPos = targetPos.down();
        BlockState belowState = world.getBlockState(belowPos);
        if (isPlausibleVerticalSupport(belowState)
                && isInterestingObject(world, targetPos, targetState)) {
            emitPair(world, belowPos, targetPos, "CROSSHAIR_TARGET_OBJECT_WITH_SUPPORT_BELOW",
                    crosshairInfo(world, hit));
        }

        BlockPos abovePos = targetPos.up();
        BlockState aboveState = world.getBlockState(abovePos);
        if (isPlausibleVerticalSupport(targetState)
                && isInterestingObject(world, abovePos, aboveState)) {
            emitPair(world, targetPos, abovePos, "CROSSHAIR_TARGET_SUPPORT_WITH_OBJECT_ABOVE",
                    crosshairInfo(world, hit));
        }
    }

    private static void captureManualContext(MinecraftClient client, ClientWorld world) {
        if (rows >= MAX_ROWS || !(client.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos targetPos = hit.getBlockPos();
        CrosshairInfo crosshair = crosshairInfo(world, hit);
        List<BlockPos> positions = new ArrayList<>();
        positions.add(targetPos.down());
        positions.add(targetPos);
        positions.add(targetPos.up());
        positions.add(targetPos.up(2));
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = targetPos.offset(direction);
            BlockState adjacentState = world.getBlockState(adjacent);
            if (isNoteworthyManualNeighbor(world, adjacent, adjacentState)) {
                positions.add(adjacent);
            }
        }
        for (BlockPos objectPos : positions) {
            if (rows >= MAX_ROWS) {
                return;
            }
            BlockState objectState = world.getBlockState(objectPos);
            if (objectState.isAir()) {
                continue;
            }
            emitPair(world, objectPos.down(), objectPos, "EXACT_MANUAL_CROSSHAIR_PAIR", crosshair);
            emitPair(world, objectPos, objectPos.up(), "EXACT_MANUAL_CROSSHAIR_PAIR_ABOVE", crosshair);
        }
    }

    private static void scanNearbyPairs(MinecraftClient client, ClientWorld world) {
        if (rows >= MAX_ROWS) {
            return;
        }
        BlockPos center = client.player.getBlockPos();
        int verticalRadius = exactEnabled() ? Math.min(16, SCAN_RADIUS) : Math.min(4, SCAN_RADIUS);
        for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
            for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    if (rows >= MAX_ROWS) {
                        return;
                    }
                    BlockPos supportPos = center.add(dx, dy, dz);
                    BlockState supportState = world.getBlockState(supportPos);
                    BlockPos objectPos = supportPos.up();
                    BlockState objectState = world.getBlockState(objectPos);
                    if (isPlausibleVerticalSupport(supportState)
                            && isInterestingObject(world, objectPos, objectState)
                            && isRelevantMatrixPair(world, supportPos, supportState, objectPos, objectState)) {
                        emitPair(world, supportPos, objectPos, scenarioForPair(world, supportPos, supportState,
                                objectPos, objectState), CrosshairInfo.none());
                    }
                }
            }
        }
    }

    private static void captureFocusedWatchPositions(ClientWorld world) {
        for (BlockPos pos : focusedWatchPositions()) {
            if (rows >= MAX_ROWS) {
                return;
            }
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            emitPair(world, pos.down(), pos, "FOCUSED_PRIOR_VIDEO_OR_COMPOUND_COORD", CrosshairInfo.none());
            emitPair(world, pos, pos.up(), "FOCUSED_PRIOR_VIDEO_OR_COMPOUND_COORD_ABOVE", CrosshairInfo.none());
        }
    }

    private static void runDeterministicFixtureMatrix(MinecraftClient client) {
        if (rows >= MAX_ROWS) {
            return;
        }
        if (!deterministicRequested) {
            deterministicRequested = true;
            requestDeterministicFixtureAuthoring(client);
            return;
        }
        if (deterministicAuthoringFailed) {
            logDeterministicInconclusive(deterministicAuthoringFailureReason);
            return;
        }
        if (!deterministicAuthoringComplete || deterministicOrigin == null) {
            return;
        }
        if (deterministicAuthoringClientTick < 0) {
            deterministicAuthoringClientTick = ticks;
            Slabbed.LOGGER.info(
                    "[MC1211_DETERMINISTIC_OVERLAP_FIXTURE_AUTHORED] origin={} pairCount={} diagnosticsOnly=true semanticsChanged=false reloadPhase=not-run",
                    deterministicOrigin.toShortString(),
                    deterministicPairs.size());
        }
        int ticksSinceAuthoring = ticks - deterministicAuthoringClientTick;
        for (int phaseTick : DETERMINISTIC_PHASE_TICKS) {
            if (ticksSinceAuthoring < phaseTick || DETERMINISTIC_PHASES_EMITTED.contains(phaseTick)) {
                continue;
            }
            String phase = switch (phaseTick) {
                case 0 -> "immediate";
                case 1 -> "tick1";
                case 5 -> "tick5";
                case 20 -> "tick20";
                default -> "tick" + phaseTick;
            };
            int rowsBeforePhase = rows;
            for (FixturePair pair : deterministicPairs) {
                if (rows >= MAX_ROWS) {
                    return;
                }
                emitPair(
                        client.world,
                        pair.supportPos(),
                        pair.objectPos(),
                        pair.scenario(),
                        CrosshairInfo.none(),
                        new FixtureContext(deterministicOrigin, phase, pair.authoredBy(), ticksSinceAuthoring));
            }
            if (rows > rowsBeforePhase) {
                DETERMINISTIC_PHASES_EMITTED.add(phaseTick);
            }
        }
    }

    // ── reload-phase orchestration ───────────────────────────────────

    private static void runDeterministicReloadFixture(MinecraftClient client) {
        if (rows >= MAX_ROWS) {
            return;
        }
        String mode = reloadPhaseMode();
        if ("preReload".equalsIgnoreCase(mode)) {
            runReloadPreReload(client);
        } else if ("postReload".equalsIgnoreCase(mode)) {
            runReloadPostReload(client);
        } else if (!reloadInconclusiveEmitted) {
            reloadInconclusiveEmitted = true;
            emitReloadFixtureLevelInconclusive(
                    "unset",
                    "reload_phase_property_unset_or_invalid:" + mode);
        }
    }

    private static void runReloadPreReload(MinecraftClient client) {
        if (preReloadEmitted) {
            return;
        }
        if (!deterministicRequested) {
            deterministicRequested = true;
            requestDeterministicReloadFixtureAuthoring(client);
            return;
        }
        if (deterministicAuthoringFailed) {
            if (!reloadInconclusiveEmitted) {
                reloadInconclusiveEmitted = true;
                emitReloadFixtureLevelInconclusive("preReload", deterministicAuthoringFailureReason);
            }
            return;
        }
        if (!deterministicAuthoringComplete || deterministicOrigin == null) {
            return;
        }
        if (deterministicAuthoringClientTick < 0) {
            deterministicAuthoringClientTick = ticks;
            Slabbed.LOGGER.info(
                    "[MC1211_DETERMINISTIC_OVERLAP_FIXTURE_AUTHORED] origin={} pairCount={} diagnosticsOnly=true semanticsChanged=false reloadPhase=preReload authoredBy={} fixtureMatched={}",
                    deterministicOrigin.toShortString(),
                    deterministicPairs.size(),
                    deterministicAuthoringMode,
                    deterministicFixtureMatched);
        }
        if (client.world == null) {
            return;
        }
        if (!isOriginChunkLoaded(client.world, deterministicOrigin)) {
            if (reloadFirstReadyTick < 0) {
                reloadFirstReadyTick = ticks;
            } else if (ticks - reloadFirstReadyTick > RELOAD_CHUNK_LOAD_BUDGET_TICKS) {
                if (!reloadInconclusiveEmitted) {
                    reloadInconclusiveEmitted = true;
                    emitReloadFixtureLevelInconclusive("preReload", "chunk_not_loaded_within_budget");
                }
            }
            return;
        }
        if (reloadFirstReadyTick < 0) {
            reloadFirstReadyTick = ticks;
        }
        if (ticks - reloadFirstReadyTick < RELOAD_RENDER_WARM_TICKS) {
            return;
        }

        Map<String, ScenarioMeasurement> measurements = new LinkedHashMap<>();
        for (FixturePair pair : deterministicPairs) {
            if (rows >= MAX_ROWS) {
                return;
            }
            ScenarioMeasurement m = measureScenario(client.world, pair);
            emitPreReloadRow(pair, m);
            measurements.put(pair.scenario(), m);
        }

        Path sigPath = signaturePath();
        boolean signatureWritten = false;
        String signatureWriteReason;
        if (sigPath == null) {
            signatureWriteReason = "signature_path_property_not_set";
        } else {
            try {
                if (sigPath.getParent() != null) {
                    Files.createDirectories(sigPath.getParent());
                }
                String body = serializeSignature(
                        deterministicOrigin,
                        measurements,
                        deterministicAuthoringMode,
                        deterministicFixtureMatched);
                Files.writeString(sigPath, body, StandardCharsets.UTF_8);
                signatureWritten = true;
                signatureWriteReason = "ok";
            } catch (IOException ex) {
                signatureWriteReason = "io_error:" + ex.getClass().getSimpleName();
                Slabbed.LOGGER.error(
                        "[MC1211_DETERMINISTIC_OVERLAP_RELOAD_SIGNATURE_ERROR] failed to write signature path={} reason={}",
                        sigPath,
                        signatureWriteReason,
                        ex);
            }
        }
        Slabbed.LOGGER.info(
                "[MC1211_DETERMINISTIC_OVERLAP_RELOAD_SIGNATURE_WRITTEN] path={} written={} reason={} pairCount={} authoredBy={} fixtureMatched={}",
                sigPath == null ? "unset" : sigPath.toString(),
                signatureWritten,
                signatureWriteReason,
                measurements.size(),
                deterministicAuthoringMode,
                deterministicFixtureMatched);
        Slabbed.LOGGER.info(
                "[MC1211_DETERMINISTIC_OVERLAP_RELOAD_SAVE_AND_QUIT_INSTRUCTION] origin={} action=use_save_and_quit_to_title_then_relaunch_with_postReload_phase_against_same_world authoredBy={} fixtureMatched={} signaturePath={} signatureWritten={}",
                deterministicOrigin.toShortString(),
                deterministicAuthoringMode,
                deterministicFixtureMatched,
                sigPath == null ? "unset" : sigPath.toString(),
                signatureWritten);

        emitReloadSummary("preReload");
        preReloadEmitted = true;
    }

    private static void runReloadPostReload(MinecraftClient client) {
        if (postReloadEmitted) {
            return;
        }
        if (loadedSignature == null) {
            Path sigPath = signaturePath();
            if (sigPath == null) {
                if (!reloadInconclusiveEmitted) {
                    reloadInconclusiveEmitted = true;
                    emitReloadFixtureLevelInconclusive("postReload", "signature_path_property_not_set");
                }
                return;
            }
            loadedSignature = readSignature(sigPath);
            Slabbed.LOGGER.info(
                    "[MC1211_DETERMINISTIC_OVERLAP_RELOAD_SIGNATURE_READ] path={} success={} reason={} originFromSignature={} pairCount={}",
                    sigPath,
                    loadedSignature.success(),
                    loadedSignature.reason(),
                    loadedSignature.origin() == null ? "n/a" : loadedSignature.origin().toShortString(),
                    loadedSignature.measurements().size());
            if (!loadedSignature.success()) {
                if (!reloadInconclusiveEmitted) {
                    reloadInconclusiveEmitted = true;
                    emitReloadFixtureLevelInconclusive("postReload", loadedSignature.reason());
                }
                return;
            }
            BlockPos propertyOrigin = parseOriginPropertyForReload();
            if (propertyOrigin != null && !propertyOrigin.equals(loadedSignature.origin())) {
                Slabbed.LOGGER.warn(
                        "[MC1211_DETERMINISTIC_OVERLAP_RELOAD_ORIGIN_MISMATCH] property={} signature={} usingSignatureOrigin=true",
                        propertyOrigin.toShortString(),
                        loadedSignature.origin().toShortString());
            }
            deterministicOrigin = loadedSignature.origin();
            deterministicPairs = buildFixturePairs(deterministicOrigin);
        }
        if (client.world == null) {
            return;
        }
        if (!isOriginChunkLoaded(client.world, deterministicOrigin)) {
            if (reloadFirstReadyTick < 0) {
                reloadFirstReadyTick = ticks;
            } else if (ticks - reloadFirstReadyTick > RELOAD_CHUNK_LOAD_BUDGET_TICKS) {
                if (!reloadInconclusiveEmitted) {
                    reloadInconclusiveEmitted = true;
                    emitReloadFixtureLevelInconclusive("postReload", "chunk_not_loaded_within_budget");
                }
            }
            return;
        }
        if (reloadFirstReadyTick < 0) {
            reloadFirstReadyTick = ticks;
        }
        if (ticks - reloadFirstReadyTick < RELOAD_RENDER_WARM_TICKS) {
            return;
        }

        BlockState originState = client.world.getBlockState(deterministicOrigin);
        BlockState originAboveState = client.world.getBlockState(deterministicOrigin.up());
        if (originState.isAir() && originAboveState.isAir()) {
            if (!reloadInconclusiveEmitted) {
                reloadInconclusiveEmitted = true;
                emitReloadFixtureLevelInconclusive("postReload", "fixture_missing_after_reload");
            }
            return;
        }

        for (FixturePair pair : deterministicPairs) {
            if (rows >= MAX_ROWS) {
                return;
            }
            ScenarioMeasurement current = measureScenario(client.world, pair);
            emitPostReloadRow(pair, current);
            ScenarioMeasurement baseline = loadedSignature.measurements().get(pair.scenario());
            emitDeltaRow(pair, baseline, current);
        }
        emitReloadSummary("postReload");
        postReloadEmitted = true;
    }

    private static void requestDeterministicReloadFixtureAuthoring(MinecraftClient client) {
        MinecraftServer server = client.getServer();
        if (server == null || client.world == null || client.player == null) {
            deterministicAuthoringFailed = true;
            deterministicAuthoringFailureReason = "integrated_server_unavailable";
            return;
        }
        ServerWorld serverWorld = server.getWorld(client.world.getRegistryKey());
        if (serverWorld == null) {
            deterministicAuthoringFailed = true;
            deterministicAuthoringFailureReason = "matching_server_world_unavailable";
            return;
        }
        BlockPos origin = parseOriginPropertyForReload();
        if (origin == null) {
            origin = chooseDeterministicOrigin(serverWorld, client.player.getBlockPos());
            if (origin == null) {
                deterministicAuthoringFailed = true;
                deterministicAuthoringFailureReason = "fixture_area_not_clear_and_no_pinned_origin";
                return;
            }
        }
        deterministicOrigin = origin;
        deterministicPairs = buildFixturePairs(origin);
        final BlockPos finalOrigin = origin;
        server.execute(() -> {
            try {
                FixtureAuthoringResult result = ensureFixtureForReload(serverWorld, finalOrigin);
                if (!result.success()) {
                    deterministicAuthoringFailureReason = result.reason();
                    deterministicAuthoringFailed = true;
                    return;
                }
                deterministicAuthoringComplete = true;
            } catch (RuntimeException ex) {
                deterministicAuthoringFailureReason = "exception:" + ex.getClass().getSimpleName();
                deterministicAuthoringFailed = true;
                Slabbed.LOGGER.error(
                        "[MC1211_DETERMINISTIC_OVERLAP_RELOAD_FIXTURE_ERROR] reload authoring failed",
                        ex);
            }
        });
    }

    private static FixtureAuthoringResult ensureFixtureForReload(ServerWorld world, BlockPos origin) {
        Map<BlockPos, BlockState> expected = expectedAuthoredLayout(origin);
        boolean anyExpectedPresent = false;
        boolean anyMismatch = false;
        boolean allMatch = true;
        for (Map.Entry<BlockPos, BlockState> entry : expected.entrySet()) {
            BlockState actual = world.getBlockState(entry.getKey());
            if (actual.equals(entry.getValue())) {
                anyExpectedPresent = true;
            } else if (!actual.isAir()) {
                anyExpectedPresent = true;
                anyMismatch = true;
                allMatch = false;
            } else {
                allMatch = false;
            }
        }
        if (anyMismatch) {
            deterministicAuthoringMode = "n/a";
            deterministicFixtureMatched = false;
            return new FixtureAuthoringResult(false, "area_contains_nonmatching_blocks");
        }
        if (allMatch) {
            deterministicAuthoringMode = "existing";
            deterministicFixtureMatched = true;
            applyExpectedAttachments(world, origin);
            return new FixtureAuthoringResult(true, "existing_match");
        }
        if (anyExpectedPresent) {
            deterministicAuthoringMode = "n/a";
            deterministicFixtureMatched = false;
            return new FixtureAuthoringResult(false, "area_contains_partial_fixture");
        }
        FixtureAuthoringResult inner = authorFixture(world, origin);
        if (inner.success()) {
            deterministicAuthoringMode = "fixture";
            deterministicFixtureMatched = false;
        } else {
            deterministicAuthoringMode = "n/a";
            deterministicFixtureMatched = false;
        }
        return inner;
    }

    private static Map<BlockPos, BlockState> expectedAuthoredLayout(BlockPos origin) {
        BlockState stone = Blocks.STONE.getDefaultState();
        BlockState wall = Blocks.STONE_BRICK_WALL.getDefaultState();
        BlockState lantern = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false);
        BlockState bottomSlab = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState topSlab = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
        BlockState doubleSlab = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE);
        Map<BlockPos, BlockState> map = new LinkedHashMap<>();
        map.put(lane(origin, 0), bottomSlab);
        map.put(lane(origin, 0).up(), stone);
        map.put(lane(origin, 1), topSlab);
        map.put(lane(origin, 1).up(), stone);
        map.put(lane(origin, 2), doubleSlab);
        map.put(lane(origin, 2).up(), stone);
        BlockPos compoundRoot = lane(origin, 3);
        BlockPos compoundBaseFull = compoundRoot.up();
        BlockPos loweredCarrier = compoundBaseFull.up();
        BlockPos compoundFull = loweredCarrier.up();
        map.put(compoundRoot, bottomSlab);
        map.put(compoundBaseFull, stone);
        map.put(loweredCarrier, bottomSlab);
        map.put(compoundFull, stone);
        BlockPos sideSupport = compoundFull.east().down();
        BlockPos sideTopSlab = sideSupport.up();
        map.put(sideSupport, stone);
        map.put(sideTopSlab, topSlab);
        map.put(lane(origin, 5), bottomSlab);
        map.put(lane(origin, 5).up(), stone);
        map.put(lane(origin, 5).up(2), wall);
        map.put(lane(origin, 5).up(3), lantern);
        return map;
    }

    private static void applyExpectedAttachments(ServerWorld world, BlockPos origin) {
        BlockState stone = Blocks.STONE.getDefaultState();
        BlockState bottomSlab = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState topSlab = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
        BlockPos ordinaryFull = lane(origin, 0).up();
        SlabAnchorAttachment.addAnchor(world, ordinaryFull, stone);
        BlockPos compoundBaseFull = lane(origin, 3).up();
        BlockPos loweredCarrier = compoundBaseFull.up();
        BlockPos compoundFull = loweredCarrier.up();
        SlabAnchorAttachment.addAnchor(world, compoundBaseFull, stone);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, loweredCarrier, bottomSlab);
        SlabAnchorAttachment.addAnchor(world, compoundFull, stone);
        SlabAnchorAttachment.addCompoundFullBlockAnchor(world, compoundFull, stone);
        BlockPos sideTopSlab = compoundFull.east();
        SlabAnchorAttachment.addCompoundVisibleSideUpperSlab(world, sideTopSlab, topSlab, compoundFull, stone);
        BlockPos decorativeFull = lane(origin, 5).up();
        SlabAnchorAttachment.addAnchor(world, decorativeFull, stone);
    }

    private static BlockPos parseOriginPropertyForReload() {
        return parseOriginProperty();
    }

    private static boolean isOriginChunkLoaded(net.minecraft.world.World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        try {
            return world.getChunk(pos.getX() >> 4, pos.getZ() >> 4) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void requestDeterministicFixtureAuthoring(MinecraftClient client) {
        MinecraftServer server = client.getServer();
        if (server == null || client.world == null || client.player == null) {
            deterministicAuthoringFailed = true;
            deterministicAuthoringFailureReason = "integrated_server_unavailable";
            return;
        }
        ServerWorld serverWorld = server.getWorld(client.world.getRegistryKey());
        if (serverWorld == null) {
            deterministicAuthoringFailed = true;
            deterministicAuthoringFailureReason = "matching_server_world_unavailable";
            return;
        }
        BlockPos origin = chooseDeterministicOrigin(serverWorld, client.player.getBlockPos());
        if (origin == null) {
            deterministicAuthoringFailed = true;
            deterministicAuthoringFailureReason = "fixture_area_not_clear";
            return;
        }
        deterministicOrigin = origin;
        deterministicPairs = buildFixturePairs(origin);
        server.execute(() -> {
            try {
                FixtureAuthoringResult result = authorFixture(serverWorld, origin);
                if (!result.success()) {
                    deterministicAuthoringFailureReason = result.reason();
                    deterministicAuthoringFailed = true;
                    return;
                }
                deterministicAuthoringComplete = true;
            } catch (RuntimeException ex) {
                deterministicAuthoringFailureReason = "exception:" + ex.getClass().getSimpleName();
                deterministicAuthoringFailed = true;
                Slabbed.LOGGER.error("[MC1211_DETERMINISTIC_OVERLAP_FIXTURE_ERROR] authoring failed", ex);
            }
        });
    }

    private static FixtureAuthoringResult authorFixture(ServerWorld world, BlockPos origin) {
        Set<BlockPos> footprint = fixtureFootprint(origin);
        for (BlockPos pos : footprint) {
            if (!world.getBlockState(pos).isAir()) {
                return new FixtureAuthoringResult(false, "area_occupied_at_" + pos.toShortString());
            }
        }

        BlockState stone = Blocks.STONE.getDefaultState();
        BlockState wall = Blocks.STONE_BRICK_WALL.getDefaultState();
        BlockState lantern = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false);
        BlockState bottomSlab = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState topSlab = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
        BlockState doubleSlab = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE);

        BlockPos ordinarySupport = lane(origin, 0);
        BlockPos ordinaryFull = ordinarySupport.up();
        set(world, ordinarySupport, bottomSlab);
        set(world, ordinaryFull, stone);
        SlabAnchorAttachment.addAnchor(world, ordinaryFull, stone);

        BlockPos topSupport = lane(origin, 1);
        set(world, topSupport, topSlab);
        set(world, topSupport.up(), stone);

        BlockPos doubleSupport = lane(origin, 2);
        set(world, doubleSupport, doubleSlab);
        set(world, doubleSupport.up(), stone);

        BlockPos compoundRootSupport = lane(origin, 3);
        BlockPos compoundBaseFull = compoundRootSupport.up();
        BlockPos loweredCarrierSlab = compoundBaseFull.up();
        BlockPos compoundFull = loweredCarrierSlab.up();
        set(world, compoundRootSupport, bottomSlab);
        set(world, compoundBaseFull, stone);
        SlabAnchorAttachment.addAnchor(world, compoundBaseFull, stone);
        set(world, loweredCarrierSlab, bottomSlab);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, loweredCarrierSlab, bottomSlab);
        set(world, compoundFull, stone);
        SlabAnchorAttachment.addAnchor(world, compoundFull, stone);
        SlabAnchorAttachment.addCompoundFullBlockAnchor(world, compoundFull, stone);

        BlockPos loweredSideSupport = compoundFull.east().down();
        BlockPos loweredSideTopSlab = loweredSideSupport.up();
        set(world, loweredSideSupport, stone);
        set(world, loweredSideTopSlab, topSlab);
        SlabAnchorAttachment.addCompoundVisibleSideUpperSlab(
                world, loweredSideTopSlab, topSlab, compoundFull, stone);

        BlockPos decorativeSupport = lane(origin, 5);
        BlockPos decorativeFull = decorativeSupport.up();
        BlockPos decorativeWall = decorativeFull.up();
        BlockPos decorativeLantern = decorativeWall.up();
        set(world, decorativeSupport, bottomSlab);
        set(world, decorativeFull, stone);
        SlabAnchorAttachment.addAnchor(world, decorativeFull, stone);
        set(world, decorativeWall, wall);
        set(world, decorativeLantern, lantern);

        return new FixtureAuthoringResult(true, "authored");
    }

    private static void set(ServerWorld world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
    }

    private static List<FixturePair> buildFixturePairs(BlockPos origin) {
        List<FixturePair> pairs = new ArrayList<>();
        pairs.add(new FixturePair("ORDINARY_BOTTOM_SLAB_FULL_BLOCK", lane(origin, 0), lane(origin, 0).up(), "fixture"));
        pairs.add(new FixturePair("TOP_SLAB_FULL_BLOCK", lane(origin, 1), lane(origin, 1).up(), "direct"));
        pairs.add(new FixturePair("DOUBLE_SLAB_FULL_BLOCK", lane(origin, 2), lane(origin, 2).up(), "direct"));

        BlockPos compoundRootSupport = lane(origin, 3);
        BlockPos compoundBaseFull = compoundRootSupport.up();
        BlockPos loweredCarrierSlab = compoundBaseFull.up();
        BlockPos compoundFull = loweredCarrierSlab.up();
        pairs.add(new FixturePair("LOWERED_BOTTOM_SLAB_CARRIER_FULL_BLOCK", loweredCarrierSlab, compoundFull, "fixture"));
        pairs.add(new FixturePair("COMPOUND_DY_MINUS_ONE_FULL_BLOCK", loweredCarrierSlab, compoundFull, "fixture"));
        pairs.add(new FixturePair("LOWERED_BOTTOM_CARRIER_ON_LOWERED_FULL_BLOCK", compoundBaseFull, loweredCarrierSlab, "fixture"));
        pairs.add(new FixturePair("LOWERED_TOP_SLAB_SIDE_LANE_STACK", compoundFull, compoundFull.east(), "fixture"));

        BlockPos decorativeSupport = lane(origin, 5);
        BlockPos decorativeFull = decorativeSupport.up();
        BlockPos decorativeWall = decorativeFull.up();
        BlockPos decorativeLantern = decorativeWall.up();
        pairs.add(new FixturePair("WALL_LANTERN_DECORATIVE_STACK_WALL", decorativeFull, decorativeWall, "direct"));
        pairs.add(new FixturePair("WALL_LANTERN_DECORATIVE_STACK_LANTERN", decorativeWall, decorativeLantern, "direct"));
        return pairs;
    }

    private static void executeServerStateMatrix(ServerWorld world, BlockPos origin) {
        Slabbed.LOGGER.info(
                "[MC1211_SERVER_STATE_OVERLAP_MATRIX_START] route=runClientGameTest origin={} diagnosticsOnly=true semanticsChanged=false proofScope=server_placement_state_authority_only surfacesNotCovered=model/render,outline,raycast,client_sync,reload",
                posText(origin));
        try {
            FixtureAuthoringResult authored = authorFixture(world, origin);
            if (!authored.success()) {
                emitServerStateMatrixSummary(0, 0, 0, 0, 0, "none");
                markServerStateMatrixRouteFail("fixture_authoring_failed:" + authored.reason());
                return;
            }

            List<ServerStateOverlapRow> matrixRows = new ArrayList<>();
            for (FixturePair pair : buildFixturePairs(origin)) {
                matrixRows.add(measureServerStateRow(world, pair));
            }

            int green = 0;
            int red = 0;
            int deferred = 0;
            int inconclusive = 0;
            String firstRed = "none";
            for (ServerStateOverlapRow row : matrixRows) {
                Slabbed.LOGGER.info(row.toMarkerLine());
                if ("GREEN".equals(row.result())) {
                    green++;
                } else if ("DEFERRED".equals(row.result())) {
                    deferred++;
                } else if ("RED".equals(row.result())) {
                    red++;
                    if ("none".equals(firstRed)) {
                        firstRed = row.scenario() + ":" + row.reason();
                    }
                } else {
                    inconclusive++;
                }
            }

            serverStateMatrixRows = matrixRows.size();
            serverStateMatrixGreenRows = green;
            serverStateMatrixRedRows = red;
            serverStateMatrixDeferredRows = deferred;
            serverStateMatrixInconclusiveRows = inconclusive;
            serverStateMatrixFirstRed = firstRed;

            if (matrixRows.isEmpty()) {
                emitServerStateMatrixSummary(0, 0, 0, 0, 0, "none");
                markServerStateMatrixRouteFail("no-rows");
                return;
            }

            emitServerStateMatrixSummary(matrixRows.size(), green, red, deferred, inconclusive, firstRed);
            if (red > 0) {
                serverStateMatrixFailed = true;
                serverStateMatrixFailureReason = "red_rows:" + firstRed;
            } else {
                serverStateMatrixFailureReason = "ok";
            }
            serverStateMatrixComplete = true;
        } catch (RuntimeException ex) {
            serverStateMatrixFailed = true;
            serverStateMatrixFailureReason = "exception:" + ex.getClass().getSimpleName();
            Slabbed.LOGGER.error(
                    "[MC1211_SERVER_STATE_OVERLAP_MATRIX_ROUTE_FAIL] reason={} route=runClientGameTest rows={}",
                    serverStateMatrixFailureReason,
                    serverStateMatrixRows,
                    ex);
            serverStateMatrixComplete = true;
        }
    }

    private static void emitServerStateMatrixSummary(
            int totalRows,
            int green,
            int red,
            int deferred,
            int inconclusive,
            String firstRed
    ) {
        Slabbed.LOGGER.info(
                "[MC1211_SERVER_STATE_OVERLAP_MATRIX_SUMMARY] totalRows={} green={} red={} deferred={} inconclusive={} firstRed={} surfacesCovered=server_placement_state surfacesNotCovered=model/render,outline,raycast,client_sync,reload proofScope=server_placement_state_authority_only releaseReady=false diagnosticsOnly=true semanticsChanged=false",
                totalRows,
                green,
                red,
                deferred,
                inconclusive,
                markerSafe(firstRed));
    }

    private static ServerStateOverlapRow measureServerStateRow(ServerWorld world, FixturePair pair) {
        ServerStateExpectation expected = serverStateExpectation(pair.scenario());
        BlockState supportState = world.getBlockState(pair.supportPos());
        BlockState objectState = world.getBlockState(pair.objectPos());
        double serverDy = objectState.isAir() ? Double.NaN : SlabSupport.getYOffset(world, pair.objectPos(), objectState);
        double supportDy = supportState.isAir() ? Double.NaN : SlabSupport.getYOffset(world, pair.supportPos(), supportState);
        double supportSurfaceOffset = supportSurfaceOffset(supportState);
        if ("LOWERED_TOP_SLAB_SIDE_LANE_STACK".equals(pair.scenario())) {
            supportSurfaceOffset = 0.5d;
        }
        double supportTopWorldY = finite(supportDy)
                ? pair.supportPos().getY() + supportDy + supportSurfaceOffset
                : Double.NaN;
        double objectBottomWorldY = finite(serverDy)
                ? pair.objectPos().getY() + serverDy
                : Double.NaN;
        double serverOverlap = finite(supportTopWorldY) && finite(objectBottomWorldY)
                ? supportTopWorldY - objectBottomWorldY
                : Double.NaN;

        boolean anchorServer = SlabAnchorAttachment.isAnchored(world, pair.objectPos());
        boolean compoundAnchorServer = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pair.objectPos());
        boolean carrierServer = isServerCarrier(world, pair.supportPos(), supportState)
                || isServerCarrier(world, pair.objectPos(), objectState);
        boolean sideUpperServer = SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(
                world, pair.objectPos(), objectState);

        List<String> failures = new ArrayList<>();
        if (objectState.isAir()) {
            failures.add("object_state_missing");
        }
        if (!close(serverDy, expected.serverDy())) {
            failures.add("serverDy_expected_" + fmt(expected.serverDy()) + "_got_" + fmt(serverDy));
        }
        if (!finite(serverOverlap)) {
            failures.add("serverOverlap_unreadable");
        } else if (serverOverlap > EPSILON) {
            failures.add("serverOverlap_positive_" + fmt(serverOverlap));
        }
        if (expected.requireAnchor() && !anchorServer) {
            failures.add("anchorServer_absent");
        }
        if (expected.forbidAnchor() && anchorServer) {
            failures.add("anchorServer_unexpected");
        }
        if (expected.requireCarrier() && !carrierServer) {
            failures.add("carrierServer_absent");
        }
        if (expected.forbidCarrier() && carrierServer) {
            failures.add("carrierServer_unexpected");
        }
        if (expected.requireCompoundAnchor() && !compoundAnchorServer) {
            failures.add("compoundAnchorServer_absent");
        }
        if (expected.requireSideUpper() && !sideUpperServer) {
            failures.add("compoundVisibleSideUpperServer_absent");
        }

        boolean deferredPolicy = "LOWERED_TOP_SLAB_SIDE_LANE_STACK".equals(pair.scenario());
        boolean laneLegal = failures.isEmpty();
        String result;
        String reason;
        if (deferredPolicy) {
            result = "DEFERRED";
            laneLegal = false;
            reason = expected.greenReason()
                    + ";laneStatus=illegal-for-current-release"
                    + ";deferred=true"
                    + ";deferredScenario=LOWERED_TOP_SLAB_SIDE_LANE_STACK_DEFERRED"
                    + ";policy=compound_dy_minus_one_top_slab_side_lane_not_legalized_for_mc1211_release_scope";
            if (failures.isEmpty()) {
                reason = reason + ";failures=none";
            } else {
                reason = reason + ";failures=" + String.join(",", failures);
            }
        } else {
            result = laneLegal ? "GREEN" : "RED";
            reason = laneLegal
                    ? expected.greenReason()
                    : expected.greenReason() + ";failures=" + String.join(",", failures);
        }

        return new ServerStateOverlapRow(
                pair.scenario(),
                pair.supportPos(),
                supportState.toString(),
                slabType(supportState),
                pair.objectPos(),
                objectState.toString(),
                expected.laneName(),
                serverDy,
                supportDy,
                supportSurfaceOffset,
                supportTopWorldY,
                objectBottomWorldY,
                serverOverlap,
                anchorServer,
                carrierServer,
                laneLegal,
                result,
                reason);
    }

    private static ServerStateExpectation serverStateExpectation(String scenario) {
        return switch (scenario) {
            case "ORDINARY_BOTTOM_SLAB_FULL_BLOCK" -> new ServerStateExpectation(
                    "ORDINARY_BOTTOM_SLAB_FULL_BLOCK_BASELINE",
                    -0.5d,
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "ordinary_bottom_slab_full_block_has_server_anchor_and_no_overlap");
            case "TOP_SLAB_FULL_BLOCK" -> new ServerStateExpectation(
                    "TOP_SLAB_FULL_BLOCK",
                    0.0d,
                    false,
                    true,
                    false,
                    true,
                    false,
                    false,
                    "top_slab_full_block_remains_vanilla_lane_no_overlap");
            case "DOUBLE_SLAB_FULL_BLOCK" -> new ServerStateExpectation(
                    "DOUBLE_SLAB_FULL_BLOCK",
                    0.0d,
                    false,
                    true,
                    false,
                    true,
                    false,
                    false,
                    "double_slab_full_block_remains_vanilla_lane_no_overlap");
            case "LOWERED_BOTTOM_SLAB_CARRIER_FULL_BLOCK", "COMPOUND_DY_MINUS_ONE_FULL_BLOCK" -> new ServerStateExpectation(
                    "LOWERED_BOTTOM_SLAB_CARRIER_FULL_BLOCK_COMPOUND_DY_MINUS_ONE",
                    -1.0d,
                    true,
                    false,
                    true,
                    false,
                    true,
                    false,
                    "lowered_bottom_slab_carrier_full_block_has_compound_server_anchor_and_no_overlap");
            case "LOWERED_BOTTOM_CARRIER_ON_LOWERED_FULL_BLOCK" -> new ServerStateExpectation(
                    "LOWERED_SLAB_CARRIER_DY_MINUS_HALF",
                    -0.5d,
                    false,
                    false,
                    true,
                    false,
                    false,
                    false,
                    "lowered_slab_carrier_keeps_server_dy_minus_half_and_no_overlap");
            case "LOWERED_TOP_SLAB_SIDE_LANE_STACK" -> new ServerStateExpectation(
                    "LOWERED_SLAB_LANE_COMPOUND_VISIBLE_SIDE_UPPER",
                    -1.0d,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    "lowered_side_lane_top_slab_keeps_server_dy_minus_one_and_no_overlap");
            case "WALL_LANTERN_DECORATIVE_STACK_WALL" -> new ServerStateExpectation(
                    "DECORATIVE_WALL_POST_SERVER_CONTACT",
                    -0.5d,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "decorative_wall_server_state_contact_only_no_client_surface_claim");
            case "WALL_LANTERN_DECORATIVE_STACK_LANTERN" -> new ServerStateExpectation(
                    "DECORATIVE_LANTERN_ON_WALL_SERVER_CONTACT",
                    -0.5d,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "decorative_lantern_server_state_contact_only_no_client_surface_claim");
            default -> new ServerStateExpectation(
                    "UNKNOWN",
                    Double.NaN,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "unknown_scenario");
        };
    }

    private static boolean isServerCarrier(ServerWorld world, BlockPos pos, BlockState state) {
        return state != null
                && !state.isAir()
                && (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)
                || SlabSupport.isLoweredSideLaneSlabCarrier(world, pos, state));
    }

    private static void markServerStateMatrixRouteFail(String reason) {
        if (serverStateMatrixComplete) {
            return;
        }
        serverStateMatrixFailed = true;
        serverStateMatrixFailureReason = reason;
        Slabbed.LOGGER.error(
                "[MC1211_SERVER_STATE_OVERLAP_MATRIX_ROUTE_FAIL] reason={} route=runClientGameTest rows={} diagnosticsOnly=true semanticsChanged=false",
                reason,
                serverStateMatrixRows);
        serverStateMatrixComplete = true;
    }

    private static Set<BlockPos> fixtureFootprint(BlockPos origin) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (int lane = 0; lane <= 5; lane++) {
            BlockPos base = lane(origin, lane);
            for (int y = -1; y <= FIXTURE_CLEAR_HEIGHT; y++) {
                positions.add(base.up(y));
            }
            positions.add(base.east());
            positions.add(base.east().up());
            positions.add(base.east().up(2));
            positions.add(base.east().up(3));
        }
        return positions;
    }

    private static BlockPos lane(BlockPos origin, int lane) {
        return origin.add(lane * FIXTURE_STRIDE, 0, 0);
    }

    private static BlockPos chooseDeterministicOrigin(ServerWorld world, BlockPos playerPos) {
        BlockPos configured = parseOriginProperty();
        if (configured != null) {
            return isFixtureAreaClear(world, configured) ? configured : null;
        }
        int bottom = world.getBottomY();
        int topExclusive = bottom + world.getHeight();
        int y = Math.max(bottom + 8, Math.min(playerPos.getY() + 12, topExclusive - 12));
        int[][] offsets = {
                {24, 0}, {-24, 0}, {0, 24}, {0, -24}, {24, 24}, {-24, 24}, {24, -24}, {-24, -24}
        };
        for (int[] offset : offsets) {
            BlockPos candidate = new BlockPos(playerPos.getX() + offset[0], y, playerPos.getZ() + offset[1]);
            if (isFixtureAreaClear(world, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isFixtureAreaClear(ServerWorld world, BlockPos origin) {
        for (BlockPos pos : fixtureFootprint(origin)) {
            if (pos.getY() < world.getBottomY() || pos.getY() >= world.getBottomY() + world.getHeight()) {
                return false;
            }
            if (!world.getBlockState(pos).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static BlockPos parseOriginProperty() {
        String prop = System.getProperty("slabbed.mc1211.overlapFixtureOrigin");
        if (prop == null || prop.isBlank()) {
            return null;
        }
        String[] parts = prop.trim().split(",");
        if (parts.length != 3) {
            deterministicAuthoringFailureReason = "malformed_origin_property";
            return null;
        }
        try {
            return new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException ignored) {
            deterministicAuthoringFailureReason = "malformed_origin_property";
            return null;
        }
    }

    private static void logDeterministicInconclusive(String reason) {
        if (!LOGGED_ROWS.add("DETERMINISTIC_AUTHORING_INCONCLUSIVE|" + reason)) {
            return;
        }
        rows++;
        inconclusiveRows++;
        Slabbed.LOGGER.info(
                "[MC1211_DETERMINISTIC_OVERLAP_FIXTURE_ROW] scenario=FIXTURE_AUTHORING phase=immediate origin={} supportPos=n/a supportState=n/a supportSlabType=n/a supportDy=NaN supportSurfaceOffset=NaN supportTopWorldY=NaN supportLaneName=FIXTURE_NOT_AUTHORED supportLaneLegal=false objectPos=n/a objectState=n/a objectBlockClass=n/a objectDyClient=NaN objectDyModel=NaN objectOutlineMinYLocal=NaN objectOutlineMaxYLocal=NaN objectOutlineMinYWorld=NaN objectOutlineMaxYWorld=NaN objectTargetMinYWorld=NaN objectVisualBottomWorldY=NaN modelOverlap=NaN outlineOverlap=NaN targetOverlap=NaN anchorClient=false loweredCarrierClient=false compoundFullBlockAnchorClient=false compoundVisibleOwnerTopSlabClient=false compoundVisibleSideLowerSlabClient=false compoundVisibleSideUpperSlabClient=false compoundVisibleSideDoubleSlabClient=false renderViewBridgeSeen=false authoredBy=fixture ticksSinceAuthoring=-1 reloadPhase=not-run result=INCONCLUSIVE reason={}",
                deterministicOrigin == null ? "n/a" : deterministicOrigin.toShortString(),
                reason);
    }

    private static void emitPair(
            ClientWorld world,
            BlockPos supportPos,
            BlockPos objectPos,
            String scenario,
            CrosshairInfo crosshair
    ) {
        emitPair(world, supportPos, objectPos, scenario, crosshair, null);
    }

    private static void emitPair(
            ClientWorld world,
            BlockPos supportPos,
            BlockPos objectPos,
            String scenario,
            CrosshairInfo crosshair,
            FixtureContext fixtureContext
    ) {
        if (rows >= MAX_ROWS) {
            return;
        }

        BlockState supportState = world.getBlockState(supportPos);
        BlockState objectState = world.getBlockState(objectPos);
        if (objectState.isAir()) {
            return;
        }

        String phaseKey = fixtureContext == null ? coarsePhaseKey(objectPos) : fixtureContext.phase();
        String signature = scenario + "|" + supportPos.asLong() + "|" + stateId(supportState) + "|"
                + objectPos.asLong() + "|" + stateId(objectState) + "|" + phaseKey;
        if (!LOGGED_ROWS.add(signature)) {
            return;
        }

        BlockView renderLikeView = renderLikeView(world);
        ShapeMetrics supportMetrics = metrics(world, renderLikeView, supportPos, supportState);
        ShapeMetrics objectMetrics = metrics(world, renderLikeView, objectPos, objectState);

        String phase = fixtureContext == null ? phaseFor(signature, scenario) : fixtureContext.phase();
        String pairRole = pairRole(world, supportPos, supportState, objectPos, objectState, scenario, fixtureContext);
        String supportSlabType = slabType(supportState);
        double supportSurfaceOffset = supportSurfaceOffset(supportState);
        if (fixtureContext != null && "LOWERED_TOP_SLAB_SIDE_LANE_STACK".equals(scenario)) {
            supportSurfaceOffset = 0.5d;
        }
        double supportTopWorldY = supportTopWorldY(supportPos, supportState, supportMetrics, supportSurfaceOffset);
        if (fixtureContext != null && "LOWERED_TOP_SLAB_SIDE_LANE_STACK".equals(scenario)) {
            supportTopWorldY = supportPos.getY() + supportSurfaceOffset + supportMetrics.dyModel;
        }
        double modelOverlap = supportTopWorldY - objectMetrics.visualBottomWorldY;
        double outlineOverlap = supportTopWorldY - objectMetrics.outlineMinYWorld;
        double targetOverlap = supportTopWorldY - objectMetrics.targetMinYWorld;

        AttachmentFlags supportFlags = attachmentFlags(world, renderLikeView, supportPos, supportState);
        AttachmentFlags objectFlags = attachmentFlags(world, renderLikeView, objectPos, objectState);
        boolean renderViewBridgeSeen = close(objectMetrics.dyClient, objectMetrics.dyModel)
                && supportFlags.renderBridgeSeen()
                && objectFlags.renderBridgeSeen();

        Lane lane = laneName(
                world,
                supportPos,
                supportState,
                objectPos,
                objectState,
                pairRole,
                supportSlabType,
                supportMetrics,
                objectMetrics,
                supportFlags,
                objectFlags,
                supportSurfaceOffset,
                supportTopWorldY);
        Verdict verdict = classify(
                pairRole,
                lane,
                modelOverlap,
                outlineOverlap,
                targetOverlap,
                objectMetrics.visualBottomWorldY,
                objectMetrics.outlineMinYWorld,
                objectMetrics.targetMinYWorld,
                renderViewBridgeSeen);

        rows++;
        if (verdict.result.equals("GREEN")) {
            greenRows++;
        } else if (verdict.result.equals("RED")) {
            redRows++;
        } else {
            inconclusiveRows++;
        }

        int ticksSincePlacement = fixtureContext == null
                ? ticksSinceFirstSeen(signature)
                : fixtureContext.ticksSinceAuthoring();
        String reloadPhase = fixtureContext == null ? phase : "not-run";
        Slabbed.LOGGER.info(
                "[MC1211_EXACT_FIXTURE_OVERLAP_ROW] scenario={} phase={} pairRole={} supportPos={} supportState={} supportSlabType={} supportDy={} supportSurfaceOffset={} supportTopWorldY={} supportLaneName={} supportLaneLegal={} objectPos={} objectState={} objectBlockClass={} objectDyClient={} objectDyModel={} objectOutlineMinYLocal={} objectOutlineMaxYLocal={} objectOutlineMinYWorld={} objectOutlineMaxYWorld={} objectTargetMinYWorld={} objectVisualBottomWorldY={} modelOverlap={} outlineOverlap={} targetOverlap={} anchorClient={} loweredCarrierClient={} compoundFullBlockAnchorClient={} compoundVisibleOwnerTopSlabClient={} compoundVisibleSideLowerSlabClient={} compoundVisibleSideUpperSlabClient={} compoundVisibleSideDoubleSlabClient={} renderViewBridgeSeen={} freshPlacementDy=n/a ticksSincePlacement={} currentCrosshairTarget={} reloadPhase={} result={} reason={}",
                scenario,
                phase,
                pairRole,
                supportPos.toShortString(),
                supportState,
                supportSlabType,
                fmt(supportMetrics.dyClient),
                fmt(supportSurfaceOffset),
                fmt(supportTopWorldY),
                lane.name,
                lane.legal,
                objectPos.toShortString(),
                objectState,
                objectState.getBlock().getClass().getSimpleName(),
                fmt(objectMetrics.dyClient),
                fmt(objectMetrics.dyModel),
                fmt(objectMetrics.outlineMinYLocal),
                fmt(objectMetrics.outlineMaxYLocal),
                fmt(objectMetrics.outlineMinYWorld),
                fmt(objectMetrics.outlineMaxYWorld),
                fmt(objectMetrics.targetMinYWorld),
                fmt(objectMetrics.visualBottomWorldY),
                fmt(modelOverlap),
                fmt(outlineOverlap),
                fmt(targetOverlap),
                objectFlags.anchorClient,
                objectFlags.loweredCarrierClient,
                objectFlags.compoundFullBlockAnchorClient,
                objectFlags.compoundVisibleOwnerTopSlabClient,
                objectFlags.compoundVisibleSideLowerSlabClient,
                objectFlags.compoundVisibleSideUpperSlabClient,
                objectFlags.compoundVisibleSideDoubleSlabClient,
                renderViewBridgeSeen,
                ticksSincePlacement,
                crosshair.describe(),
                reloadPhase,
                verdict.result,
                verdict.reason);
        if (fixtureContext != null) {
            Slabbed.LOGGER.info(
                    "[MC1211_DETERMINISTIC_OVERLAP_FIXTURE_ROW] scenario={} phase={} origin={} supportPos={} supportState={} supportSlabType={} supportDy={} supportSurfaceOffset={} supportTopWorldY={} supportLaneName={} supportLaneLegal={} objectPos={} objectState={} objectBlockClass={} objectDyClient={} objectDyModel={} objectOutlineMinYLocal={} objectOutlineMaxYLocal={} objectOutlineMinYWorld={} objectOutlineMaxYWorld={} objectTargetMinYWorld={} objectVisualBottomWorldY={} modelOverlap={} outlineOverlap={} targetOverlap={} anchorClient={} loweredCarrierClient={} compoundFullBlockAnchorClient={} compoundVisibleOwnerTopSlabClient={} compoundVisibleSideLowerSlabClient={} compoundVisibleSideUpperSlabClient={} compoundVisibleSideDoubleSlabClient={} renderViewBridgeSeen={} authoredBy={} ticksSinceAuthoring={} reloadPhase=not-run result={} reason={}",
                    scenario,
                    phase,
                    fixtureContext.origin().toShortString(),
                    supportPos.toShortString(),
                    supportState,
                    supportSlabType,
                    fmt(supportMetrics.dyClient),
                    fmt(supportSurfaceOffset),
                    fmt(supportTopWorldY),
                    lane.name,
                    lane.legal,
                    objectPos.toShortString(),
                    objectState,
                    objectState.getBlock().getClass().getSimpleName(),
                    fmt(objectMetrics.dyClient),
                    fmt(objectMetrics.dyModel),
                    fmt(objectMetrics.outlineMinYLocal),
                    fmt(objectMetrics.outlineMaxYLocal),
                    fmt(objectMetrics.outlineMinYWorld),
                    fmt(objectMetrics.outlineMaxYWorld),
                    fmt(objectMetrics.targetMinYWorld),
                    fmt(objectMetrics.visualBottomWorldY),
                    fmt(modelOverlap),
                    fmt(outlineOverlap),
                    fmt(targetOverlap),
                    objectFlags.anchorClient,
                    objectFlags.loweredCarrierClient,
                    objectFlags.compoundFullBlockAnchorClient,
                    objectFlags.compoundVisibleOwnerTopSlabClient,
                    objectFlags.compoundVisibleSideLowerSlabClient,
                    objectFlags.compoundVisibleSideUpperSlabClient,
                    objectFlags.compoundVisibleSideDoubleSlabClient,
                    renderViewBridgeSeen,
                    fixtureContext.authoredBy(),
                    fixtureContext.ticksSinceAuthoring(),
                    verdict.result,
                    verdict.reason);
        }
        Slabbed.LOGGER.info(
                "[MC1211_EXACT_FIXTURE_OVERLAP_SUMMARY] rows={} green={} red={} inconclusive={} releaseReady=false diagnosticsOnly=true semanticsChanged=false",
                rows,
                greenRows,
                redRows,
                inconclusiveRows);

        if ("fullBlockOnSupport".equals(pairRole) && isSlab(supportState)) {
            emitLegacyFullBlockRow(
                    scenario,
                    phase,
                    supportPos,
                    supportState,
                    supportSlabType,
                    supportMetrics,
                    supportSurfaceOffset,
                    supportTopWorldY,
                    objectPos,
                    objectState,
                    objectMetrics,
                    modelOverlap,
                    outlineOverlap,
                    targetOverlap,
                    supportFlags,
                    objectFlags,
                    renderViewBridgeSeen,
                    lane,
                    verdict);
        }
    }

    private static void emitLegacyFullBlockRow(
            String scenario,
            String phase,
            BlockPos supportPos,
            BlockState supportState,
            String supportSlabType,
            ShapeMetrics supportMetrics,
            double supportSurfaceOffset,
            double supportTopWorldY,
            BlockPos objectPos,
            BlockState objectState,
            ShapeMetrics objectMetrics,
            double modelOverlap,
            double outlineOverlap,
            double targetOverlap,
            AttachmentFlags supportFlags,
            AttachmentFlags objectFlags,
            boolean renderViewBridgeSeen,
            Lane lane,
            Verdict verdict
    ) {
        Slabbed.LOGGER.info(
                "[MC1211_FULL_BLOCK_SLAB_OVERLAP_ROW] scenario={} phase={} supportPos={} supportState={} supportSlabType={} supportDy={} supportSurfaceOffset={} supportTopWorldY={} fullPos={} fullState={} fullDyClient={} fullDyModel={} fullOutlineMinYLocal={} fullOutlineMinYWorld={} fullTargetMinYWorld={} fullVisualBottomWorldY={} modelOverlap={} outlineOverlap={} targetOverlap={} anchorServer=n/a anchorClient={} loweredCarrierClient={} supportLoweredCarrierClient={} renderViewBridgeSeen={} placementDy=n/a lane={} result={} reason={}",
                scenario,
                phase,
                supportPos.toShortString(),
                supportState,
                supportSlabType,
                fmt(supportMetrics.dyClient),
                fmt(supportSurfaceOffset),
                fmt(supportTopWorldY),
                objectPos.toShortString(),
                objectState,
                fmt(objectMetrics.dyClient),
                fmt(objectMetrics.dyModel),
                fmt(objectMetrics.outlineMinYLocal),
                fmt(objectMetrics.outlineMinYWorld),
                fmt(objectMetrics.targetMinYWorld),
                fmt(objectMetrics.visualBottomWorldY),
                fmt(modelOverlap),
                fmt(outlineOverlap),
                fmt(targetOverlap),
                objectFlags.anchorClient,
                objectFlags.loweredCarrierClient,
                supportFlags.loweredCarrierClient,
                renderViewBridgeSeen,
                lane.name,
                verdict.result,
                verdict.reason);
    }

    private static Verdict classify(
            String pairRole,
            Lane lane,
            double modelOverlap,
            double outlineOverlap,
            double targetOverlap,
            double objectVisualBottomWorldY,
            double objectOutlineMinYWorld,
            double objectTargetMinYWorld,
            boolean renderViewBridgeSeen
    ) {
        if ("contextProbe".equals(pairRole)) {
            return new Verdict("INCONCLUSIVE", "context_probe_not_support_pair");
        }
        if (!lane.legal) {
            return new Verdict("RED", "support_lane_cannot_be_named:" + lane.name);
        }
        if (!renderViewBridgeSeen) {
            return new Verdict("RED", "render_view_bridge_missing_or_stale");
        }
        if (finite(modelOverlap) && modelOverlap > EPSILON) {
            return new Verdict("RED", "model_visual_bottom_below_support_top");
        }
        if (finite(outlineOverlap) && outlineOverlap > EPSILON) {
            return new Verdict("RED", "outline_bottom_below_support_top");
        }
        if (finite(targetOverlap) && targetOverlap > EPSILON) {
            return new Verdict("RED", "target_bottom_below_support_top");
        }
        if (finite(objectOutlineMinYWorld) && !close(objectVisualBottomWorldY, objectOutlineMinYWorld)) {
            return new Verdict("RED", "model_outline_bottom_disagree");
        }
        if (finite(objectTargetMinYWorld) && !close(objectVisualBottomWorldY, objectTargetMinYWorld)) {
            return new Verdict("RED", "model_target_bottom_disagree");
        }
        return new Verdict("GREEN", "support_top_matches_model_outline_target_bottom");
    }

    private static Lane laneName(
            ClientWorld world,
            BlockPos supportPos,
            BlockState supportState,
            BlockPos objectPos,
            BlockState objectState,
            String pairRole,
            String supportSlabType,
            ShapeMetrics supportMetrics,
            ShapeMetrics objectMetrics,
            AttachmentFlags supportFlags,
            AttachmentFlags objectFlags,
            double supportSurfaceOffset,
            double supportTopWorldY
    ) {
        if ("contextProbe".equals(pairRole)) {
            return new Lane("CONTEXT_PROBE", false);
        }
        if (!close(objectMetrics.dyClient, objectMetrics.dyModel)) {
            return new Lane("UNKNOWN_MODEL_CLIENT_DY_DISAGREE", false);
        }
        if ("fullBlockOnSupport".equals(pairRole) && isSlab(supportState)) {
            if ("BOTTOM".equals(supportSlabType)
                    && close(supportMetrics.dyClient, 0.0d)
                    && close(supportSurfaceOffset, 0.5d)
                    && close(objectMetrics.dyModel, -0.5d)) {
                return new Lane(objectFlags.anchorClient
                        ? "ORDINARY_BOTTOM_SLAB_FULL_BLOCK_BASELINE"
                        : "ORDINARY_BOTTOM_SLAB_DYNAMIC_FULL_BLOCK", true);
            }
            if ("BOTTOM".equals(supportSlabType)
                    && close(supportMetrics.dyClient, -0.5d)
                    && close(objectMetrics.dyModel, -1.0d)
                    && (supportFlags.loweredCarrierClient
                    || SlabSupport.isLoweredCompoundSourceSlab(world, supportPos, supportState)
                    || objectFlags.compoundFullBlockAnchorClient)) {
                return new Lane("LOWERED_BOTTOM_SLAB_CARRIER_FULL_BLOCK_COMPOUND_DY_MINUS_ONE", true);
            }
            if ("TOP".equals(supportSlabType)
                    && close(supportMetrics.dyClient, 0.0d)
                    && close(supportSurfaceOffset, 1.0d)
                    && close(objectMetrics.dyModel, 0.0d)) {
                return new Lane("TOP_SLAB_FULL_BLOCK", true);
            }
            if ("DOUBLE".equals(supportSlabType)
                    && close(supportMetrics.dyClient, 0.0d)
                    && close(supportSurfaceOffset, 1.0d)
                    && close(objectMetrics.dyModel, 0.0d)) {
                return new Lane("DOUBLE_SLAB_FULL_BLOCK", true);
            }
            if (objectFlags.compoundFullBlockAnchorClient && close(objectMetrics.dyModel, -1.0d)) {
                return new Lane("COMPOUND_DY_MINUS_ONE_FULL_BLOCK", true);
            }
        }
        if ("slabLane".equals(pairRole)) {
            if (objectFlags.compoundVisibleSideLowerSlabClient) {
                return new Lane("LOWERED_SLAB_LANE_COMPOUND_VISIBLE_SIDE_LOWER", true);
            }
            if (objectFlags.compoundVisibleSideUpperSlabClient) {
                return new Lane("LOWERED_SLAB_LANE_COMPOUND_VISIBLE_SIDE_UPPER", true);
            }
            if (objectFlags.compoundVisibleSideDoubleSlabClient) {
                return new Lane("LOWERED_SLAB_LANE_COMPOUND_VISIBLE_SIDE_DOUBLE", true);
            }
            if (objectFlags.compoundVisibleOwnerTopSlabClient) {
                return new Lane("LOWERED_SLAB_LANE_COMPOUND_VISIBLE_OWNER_TOP", true);
            }
            if (objectFlags.loweredCarrierClient && close(objectMetrics.dyModel, -0.5d)) {
                return new Lane("LOWERED_SLAB_CARRIER_DY_MINUS_HALF", true);
            }
            if (SlabSupport.isLoweredSideLaneSlabCarrier(world, objectPos, objectState)
                    && close(objectMetrics.dyModel, -0.5d)) {
                return new Lane("LOWERED_SLAB_SIDE_LANE_" + slabType(objectState) + "_DY_MINUS_HALF", true);
            }
        }
        if ("fullBlockOnSupport".equals(pairRole)
                && !isSlab(supportState)
                && close(supportTopWorldY, objectMetrics.visualBottomWorldY)) {
            return new Lane("FULL_BLOCK_ON_LOWERED_OR_FULL_HEIGHT_SUPPORT", true);
        }
        if ("wallPostOnSupport".equals(pairRole) && close(supportTopWorldY, objectMetrics.visualBottomWorldY)) {
            return new Lane("DECORATIVE_WALL_POST_VISUAL_CONTACT", true);
        }
        if ("lanternOnWall".equals(pairRole) && close(supportTopWorldY, objectMetrics.visualBottomWorldY)) {
            return new Lane("DECORATIVE_LANTERN_ON_WALL_OR_SUPPORT_CONTACT", true);
        }
        if ("decorativeObjectOnSupport".equals(pairRole) && close(supportTopWorldY, objectMetrics.visualBottomWorldY)) {
            return new Lane("DECORATIVE_OBJECT_VISUAL_CONTACT", true);
        }
        return new Lane("UNKNOWN_UNNAMED_LANE", false);
    }

    private static String scenarioForPair(
            ClientWorld world,
            BlockPos supportPos,
            BlockState supportState,
            BlockPos objectPos,
            BlockState objectState
    ) {
        double supportDy = ClientDy.dyFor(world, supportPos, supportState);
        double objectDy = ClientDy.dyFor(world, objectPos, objectState);
        if (isLikelyFullBlock(world, objectPos, objectState) && isSlab(supportState)) {
            String type = slabType(supportState);
            if ("BOTTOM".equals(type) && close(supportDy, 0.0d) && close(objectDy, -0.5d)) {
                return "ORDINARY_BOTTOM_SLAB_FULL_BLOCK_BASELINE";
            }
            if ("TOP".equals(type)) {
                return "TOP_SLAB_FULL_BLOCK";
            }
            if ("DOUBLE".equals(type)) {
                return "DOUBLE_SLAB_FULL_BLOCK";
            }
            if ("BOTTOM".equals(type) && close(supportDy, -0.5d) && close(objectDy, -1.0d)) {
                return "LOWERED_BOTTOM_SLAB_CARRIER_FULL_BLOCK";
            }
            if (close(objectDy, -1.0d)) {
                return "COMPOUND_DY_MINUS_ONE_FULL_BLOCK";
            }
        }
        if (isSlab(objectState) && objectDy < -EPSILON) {
            return "LOWERED_SLAB_LANE_CARRIER_CASE";
        }
        if (isWallLike(objectState)) {
            return "WALL_POST_DECORATIVE_STACK";
        }
        if (isLanternLike(objectState)) {
            return "LANTERN_ON_WALL_DECORATIVE_STACK";
        }
        return "MATRIX_VERTICAL_PAIR";
    }

    private static String pairRole(
            ClientWorld world,
            BlockPos supportPos,
            BlockState supportState,
            BlockPos objectPos,
            BlockState objectState,
            String scenario,
            FixtureContext fixtureContext
    ) {
        if (fixtureContext != null && "LOWERED_TOP_SLAB_SIDE_LANE_STACK".equals(scenario)) {
            return "slabLane";
        }
        if (!objectPos.equals(supportPos.up())) {
            return "contextProbe";
        }
        if (supportState.isAir()) {
            return "contextProbe";
        }
        if (isLikelyFullBlock(world, objectPos, objectState)) {
            return "fullBlockOnSupport";
        }
        if (isSlab(objectState) && isSlabLaneCandidate(world, supportPos, supportState, objectPos, objectState)) {
            if (objectPos.equals(supportPos.up())
                    && (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, objectPos, objectState)
                    || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, objectPos, objectState)
                    || SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, objectPos, objectState))) {
                return "contextProbe";
            }
            return "slabLane";
        }
        if (isWallLike(objectState)) {
            return "wallPostOnSupport";
        }
        if (isLanternLike(objectState) && isWallLike(supportState)) {
            return "lanternOnWall";
        }
        if (isDecorativeLike(objectState)) {
            return "decorativeObjectOnSupport";
        }
        return "contextProbe";
    }

    private static ShapeMetrics metrics(BlockView world, BlockView renderLikeView, BlockPos pos, BlockState state) {
        double dyClient = ClientDy.dyFor(world, pos, state);
        double dyModel = ClientDy.dyFor(renderLikeView, pos, state);
        VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.absent());
        double outlineMinYLocal = minY(outline);
        double outlineMaxYLocal = maxY(outline);
        double outlineMinYWorld = localToWorld(pos, outlineMinYLocal);
        double outlineMaxYWorld = localToWorld(pos, outlineMaxYLocal);
        VoxelShape targetShape = state.getRaycastShape(world, pos);
        double targetMinYLocal = minY(targetShape);
        double targetMinYWorld = localToWorld(pos, targetMinYLocal);
        double unshiftedModelMinYLocal = finite(outlineMinYLocal) ? outlineMinYLocal - dyClient : 0.0d;
        double unshiftedModelMaxYLocal = finite(outlineMaxYLocal) ? outlineMaxYLocal - dyClient : 1.0d;
        double visualBottomWorldY = pos.getY() + unshiftedModelMinYLocal + dyModel;
        double visualTopWorldY = pos.getY() + unshiftedModelMaxYLocal + dyModel;
        return new ShapeMetrics(
                dyClient,
                dyModel,
                outlineMinYLocal,
                outlineMaxYLocal,
                outlineMinYWorld,
                outlineMaxYWorld,
                targetMinYWorld,
                visualBottomWorldY,
                visualTopWorldY);
    }

    private static AttachmentFlags attachmentFlags(
            BlockView world,
            BlockView renderLikeView,
            BlockPos pos,
            BlockState state
    ) {
        return new AttachmentFlags(
                SlabAnchorAttachment.isAnchored(world, pos),
                SlabAnchorAttachment.isAnchored(renderLikeView, pos),
                SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state),
                SlabAnchorAttachment.isPersistentLoweredSlabCarrier(renderLikeView, pos, state),
                SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos),
                SlabAnchorAttachment.isCompoundFullBlockAnchor(renderLikeView, pos),
                SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state),
                SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(renderLikeView, pos, state),
                SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state),
                SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(renderLikeView, pos, state),
                SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state),
                SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(renderLikeView, pos, state),
                SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state),
                SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(renderLikeView, pos, state));
    }

    private static CrosshairInfo crosshairInfo(ClientWorld world, BlockHitResult hit) {
        BlockPos targetPos = hit.getBlockPos();
        BlockState targetState = world.getBlockState(targetPos);
        return new CrosshairInfo(
                targetPos.toShortString(),
                stateId(targetState),
                hit.getSide().asString(),
                ownerName(world, targetPos, targetState));
    }

    private static String ownerName(BlockView world, BlockPos pos, BlockState state) {
        if (SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)) {
            return "COMPOUND_FULL_BLOCK";
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            return "ANCHORED_FULL_BLOCK";
        }
        if (SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)) {
            return "COMPOUND_VISIBLE_OWNER_TOP_SLAB";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)) {
            return "COMPOUND_VISIBLE_SIDE_LOWER_SLAB";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)) {
            return "COMPOUND_VISIBLE_SIDE_UPPER_SLAB";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)) {
            return "COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB";
        }
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)) {
            return "LOWERED_SLAB_CARRIER";
        }
        if (isSlab(state)) {
            return "SLAB";
        }
        if (isWallLike(state)) {
            return "WALL";
        }
        if (isLanternLike(state)) {
            return "LANTERN";
        }
        return "OTHER";
    }

    private static double supportTopWorldY(
            BlockPos supportPos,
            BlockState supportState,
            ShapeMetrics supportMetrics,
            double supportSurfaceOffset
    ) {
        if (isSlab(supportState)) {
            return supportPos.getY() + supportSurfaceOffset + supportMetrics.dyModel;
        }
        return supportMetrics.visualTopWorldY;
    }

    private static String phaseFor(String signature, String scenario) {
        int firstSeen = FIRST_SEEN_TICK_BY_SIGNATURE.computeIfAbsent(signature, ignored -> ticks);
        if (ticks < 100) {
            return "postReload";
        }
        if (scenario.contains("MANUAL")) {
            return "manual";
        }
        if (ticks - firstSeen <= 1) {
            return "fresh";
        }
        if (ticks - firstSeen > 40) {
            return "postDelay";
        }
        return "preExisting";
    }

    private static int ticksSinceFirstSeen(String signature) {
        Integer firstSeen = FIRST_SEEN_TICK_BY_SIGNATURE.get(signature);
        return firstSeen == null ? -1 : ticks - firstSeen;
    }

    private static String coarsePhaseKey(BlockPos objectPos) {
        if (ticks < 100) {
            return "postReload";
        }
        int bucket = ticks / 40;
        return objectPos.asLong() + "@" + bucket;
    }

    private static boolean isInterestingObject(ClientWorld world, BlockPos pos, BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return isLikelyFullBlock(world, pos, state)
                || isSlab(state)
                || isWallLike(state)
                || isDecorativeLike(state);
    }

    private static boolean isRelevantMatrixPair(
            ClientWorld world,
            BlockPos supportPos,
            BlockState supportState,
            BlockPos objectPos,
            BlockState objectState
    ) {
        if (isSlab(supportState) || isDecorativeLike(supportState) || isDecorativeLike(objectState)) {
            return true;
        }
        double supportDy = ClientDy.dyFor(world, supportPos, supportState);
        double objectDy = ClientDy.dyFor(world, objectPos, objectState);
        if (isSlab(objectState)) {
            return isSlabLaneCandidate(world, supportPos, supportState, objectPos, objectState);
        }
        if (Math.abs(supportDy) > EPSILON || Math.abs(objectDy) > EPSILON) {
            return true;
        }
        return SlabAnchorAttachment.isAnchored(world, supportPos)
                || SlabAnchorAttachment.isAnchored(world, objectPos)
                || SlabAnchorAttachment.isCompoundFullBlockAnchor(world, supportPos)
                || SlabAnchorAttachment.isCompoundFullBlockAnchor(world, objectPos)
                || SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, supportPos, supportState)
                || SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, objectPos, objectState);
    }

    private static boolean isPlausibleVerticalSupport(BlockState state) {
        return state != null && !state.isAir();
    }

    private static boolean isNoteworthyManualNeighbor(ClientWorld world, BlockPos pos, BlockState state) {
        return state != null && !state.isAir()
                && (isSlab(state)
                || isWallLike(state)
                || isLanternLike(state)
                || Math.abs(ClientDy.dyFor(world, pos, state)) > EPSILON
                || SlabAnchorAttachment.isAnchored(world, pos)
                || SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos));
    }

    private static boolean isSlabLaneCandidate(
            ClientWorld world,
            BlockPos supportPos,
            BlockState supportState,
            BlockPos objectPos,
            BlockState objectState
    ) {
        if (!isSlab(objectState)) {
            return false;
        }
        double supportDy = ClientDy.dyFor(world, supportPos, supportState);
        double objectDy = ClientDy.dyFor(world, objectPos, objectState);
        return Math.abs(supportDy) > EPSILON
                || Math.abs(objectDy) > EPSILON
                || SlabAnchorAttachment.isAnchored(world, supportPos)
                || SlabAnchorAttachment.isCompoundFullBlockAnchor(world, supportPos)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, objectPos, objectState)
                || SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, objectPos, objectState)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, objectPos, objectState)
                || SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, objectPos, objectState)
                || SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, objectPos, objectState);
    }

    private static boolean isLikelyFullBlock(ClientWorld world, BlockPos pos, BlockState state) {
        if (state == null || state.isAir() || state.getBlock() instanceof SlabBlock || isDecorativeLike(state)) {
            return false;
        }
        VoxelShape shape = state.getOutlineShape(world, pos, ShapeContext.absent());
        if (shape == null || shape.isEmpty()) {
            return false;
        }
        Box box = shape.getBoundingBox();
        return box.maxX - box.minX >= 1.0d - EPSILON
                && box.maxY - box.minY >= 1.0d - EPSILON
                && box.maxZ - box.minZ >= 1.0d - EPSILON;
    }

    private static boolean isDecorativeLike(BlockState state) {
        return isWallLike(state) || isLanternLike(state) || stateId(state).contains("torch");
    }

    private static boolean isWallLike(BlockState state) {
        return state != null && (state.getBlock() instanceof WallBlock || stateId(state).contains("_wall"));
    }

    private static boolean isLanternLike(BlockState state) {
        String id = stateId(state);
        return id.equals("minecraft:lantern") || id.equals("minecraft:soul_lantern");
    }

    private static boolean isSlab(BlockState state) {
        return state != null && state.getBlock() instanceof SlabBlock;
    }

    private static String slabType(BlockState state) {
        if (!isSlab(state) || !state.contains(SlabBlock.TYPE)) {
            return "n/a";
        }
        return state.get(SlabBlock.TYPE).name();
    }

    private static double supportSurfaceOffset(BlockState state) {
        if (isSlab(state) && state.contains(SlabBlock.TYPE)) {
            SlabType type = state.get(SlabBlock.TYPE);
            return type == SlabType.BOTTOM ? 0.5d : 1.0d;
        }
        return 1.0d;
    }

    private static double minY(VoxelShape shape) {
        if (shape == null || shape.isEmpty()) {
            return Double.NaN;
        }
        return shape.getBoundingBox().minY;
    }

    private static double maxY(VoxelShape shape) {
        if (shape == null || shape.isEmpty()) {
            return Double.NaN;
        }
        return shape.getBoundingBox().maxY;
    }

    private static double localToWorld(BlockPos pos, double value) {
        return finite(value) ? pos.getY() + value : Double.NaN;
    }

    private static boolean close(double left, double right) {
        return finite(left) && finite(right) && Math.abs(left - right) <= EPSILON;
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    private static String fmt(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.6f", value) : "NaN";
    }

    private static String posText(BlockPos pos) {
        return pos == null ? "null" : pos.toShortString().replace(" ", "");
    }

    private static String markerSafe(String value) {
        return value == null ? "null" : value.replace(' ', '_');
    }

    private static String stateId(BlockState state) {
        if (state == null) {
            return "null";
        }
        return Registries.BLOCK.getId(state.getBlock()).toString();
    }

    private static boolean enabled() {
        return Boolean.getBoolean("slabbed.mc1211.fullBlockSlabOverlapProof");
    }

    private static boolean serverStateMatrixRouteEnabled() {
        return Boolean.getBoolean(SERVER_STATE_MATRIX_PROPERTY)
                || FabricLoader.getInstance().isModLoaded("slabbed_gametest");
    }

    private static String serverStateMatrixRoutePropertyState() {
        if (Boolean.getBoolean(SERVER_STATE_MATRIX_PROPERTY)) {
            return SERVER_STATE_MATRIX_PROPERTY + "=true";
        }
        return FabricLoader.getInstance().isModLoaded("slabbed_gametest")
                ? SERVER_STATE_MATRIX_PROPERTY + "=auto-gametest-mod"
                : SERVER_STATE_MATRIX_PROPERTY + "=false";
    }

    private static boolean exactEnabled() {
        return enabled() && Boolean.getBoolean("slabbed.mc1211.exactFixtureOverlapProof");
    }

    private static boolean deterministicEnabled() {
        return enabled() && Boolean.getBoolean("slabbed.mc1211.deterministicOverlapFixtureMatrix");
    }

    private static boolean reloadEnabled() {
        return deterministicEnabled() && Boolean.getBoolean("slabbed.mc1211.deterministicOverlapReloadProof");
    }

    private static String reloadPhaseMode() {
        String mode = System.getProperty("slabbed.mc1211.deterministicOverlapReloadPhase");
        if (mode == null) {
            return "unset";
        }
        String trimmed = mode.trim();
        if (trimmed.isEmpty()) {
            return "unset";
        }
        return trimmed;
    }

    private static Path signaturePath() {
        String prop = System.getProperty("slabbed.mc1211.deterministicOverlapReloadSignaturePath");
        if (prop == null || prop.isBlank()) {
            return null;
        }
        try {
            return Paths.get(prop.trim()).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String focusedWatchDescription() {
        String prop = System.getProperty("slabbed.mc1211.exactFixtureWatch");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        return "-18,-60,2;-18,-59,2;-15,-60,32;-15,-59,32;-26,81,22;-26,82,22";
    }

    private static List<BlockPos> focusedWatchPositions() {
        String source = focusedWatchDescription();
        List<BlockPos> positions = new ArrayList<>();
        for (String raw : source.split(";")) {
            String[] parts = raw.trim().split(",");
            if (parts.length != 3) {
                continue;
            }
            try {
                positions.add(new BlockPos(
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim())));
            } catch (NumberFormatException ignored) {
                // Ignore malformed optional proof coordinates.
            }
        }
        return positions;
    }

    private static BlockView renderLikeView(ClientWorld world) {
        return new BlockView() {
            @Override
            public BlockState getBlockState(BlockPos pos) {
                return world.getBlockState(pos);
            }

            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return world.getBlockEntity(pos);
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return world.getFluidState(pos);
            }

            @Override
            public int getHeight() {
                return world.getHeight();
            }

            @Override
            public int getBottomY() {
                return world.getBottomY();
            }
        };
    }

    // ── reload-phase measurement and emission ───────────────────────

    private static ScenarioMeasurement measureScenario(ClientWorld world, FixturePair pair) {
        BlockState supportState = world.getBlockState(pair.supportPos());
        BlockState objectState = world.getBlockState(pair.objectPos());
        BlockView renderLikeView = renderLikeView(world);
        ShapeMetrics supportMetrics = metrics(world, renderLikeView, pair.supportPos(), supportState);
        ShapeMetrics objectMetrics = metrics(world, renderLikeView, pair.objectPos(), objectState);

        String supportSlabType = slabType(supportState);
        double supportSurfaceOffset = supportSurfaceOffset(supportState);
        if ("LOWERED_TOP_SLAB_SIDE_LANE_STACK".equals(pair.scenario())) {
            supportSurfaceOffset = 0.5d;
        }
        double supportTopWorldY = supportTopWorldY(pair.supportPos(), supportState, supportMetrics, supportSurfaceOffset);
        if ("LOWERED_TOP_SLAB_SIDE_LANE_STACK".equals(pair.scenario())) {
            supportTopWorldY = pair.supportPos().getY() + supportSurfaceOffset + supportMetrics.dyModel;
        }
        double modelOverlap = supportTopWorldY - objectMetrics.visualBottomWorldY;
        double outlineOverlap = supportTopWorldY - objectMetrics.outlineMinYWorld;
        double targetOverlap = supportTopWorldY - objectMetrics.targetMinYWorld;

        AttachmentFlags supportFlags = attachmentFlags(world, renderLikeView, pair.supportPos(), supportState);
        AttachmentFlags objectFlags = attachmentFlags(world, renderLikeView, pair.objectPos(), objectState);
        boolean renderViewBridgeSeen = close(objectMetrics.dyClient, objectMetrics.dyModel)
                && supportFlags.renderBridgeSeen()
                && objectFlags.renderBridgeSeen();

        FixtureContext fxContext = new FixtureContext(deterministicOrigin, "reload", pair.authoredBy(), 0);
        String pairRoleStr = pairRole(world, pair.supportPos(), supportState, pair.objectPos(), objectState,
                pair.scenario(), fxContext);
        Lane lane = laneName(world, pair.supportPos(), supportState, pair.objectPos(), objectState,
                pairRoleStr, supportSlabType, supportMetrics, objectMetrics, supportFlags, objectFlags,
                supportSurfaceOffset, supportTopWorldY);
        Verdict verdict = classify(pairRoleStr, lane, modelOverlap, outlineOverlap, targetOverlap,
                objectMetrics.visualBottomWorldY, objectMetrics.outlineMinYWorld, objectMetrics.targetMinYWorld,
                renderViewBridgeSeen);

        return new ScenarioMeasurement(
                pair.scenario(),
                pair.supportPos(),
                supportState.toString(),
                supportSlabType,
                supportMetrics.dyClient,
                supportSurfaceOffset,
                supportTopWorldY,
                lane.name,
                lane.legal,
                pair.objectPos(),
                objectState.toString(),
                objectState.getBlock().getClass().getSimpleName(),
                objectMetrics.dyClient,
                objectMetrics.dyModel,
                objectMetrics.outlineMinYLocal,
                objectMetrics.outlineMaxYLocal,
                objectMetrics.outlineMinYWorld,
                objectMetrics.outlineMaxYWorld,
                objectMetrics.targetMinYWorld,
                objectMetrics.visualBottomWorldY,
                modelOverlap,
                outlineOverlap,
                targetOverlap,
                objectFlags.anchorClient,
                objectFlags.loweredCarrierClient,
                objectFlags.compoundFullBlockAnchorClient,
                objectFlags.compoundVisibleOwnerTopSlabClient,
                objectFlags.compoundVisibleSideLowerSlabClient,
                objectFlags.compoundVisibleSideUpperSlabClient,
                objectFlags.compoundVisibleSideDoubleSlabClient,
                renderViewBridgeSeen,
                verdict.result,
                verdict.reason);
    }

    private static void emitPreReloadRow(FixturePair pair, ScenarioMeasurement m) {
        countReloadRow(m.result());
        emitReloadRowLine("preReload", m, null);
    }

    private static void emitPostReloadRow(FixturePair pair, ScenarioMeasurement m) {
        countReloadRow(m.result());
        emitReloadRowLine("postReload", m, null);
    }

    private static void emitDeltaRow(FixturePair pair, ScenarioMeasurement baseline, ScenarioMeasurement current) {
        if (baseline == null) {
            countReloadRow("INCONCLUSIVE");
            Slabbed.LOGGER.info(
                    "[MC1211_DETERMINISTIC_OVERLAP_RELOAD_ROW] phase=delta scenario={} origin={} laneName={} laneLegal={} dyDelta=NaN supportTopWorldYDelta=NaN objectVisualBottomWorldYDelta=NaN modelOverlapDelta=NaN outlineOverlapDelta=NaN targetOverlapDelta=NaN laneChanged=true laneLegalChanged=false anchorClientChanged=false loweredCarrierClientChanged=false compoundFullBlockAnchorClientChanged=false compoundVisibleSideUpperSlabClientChanged=false renderViewBridgeSeenChanged=false authoredBy={} fixtureMatched={} result=INCONCLUSIVE reason=no_baseline_for_scenario",
                    pair.scenario(),
                    deterministicOrigin == null ? "n/a" : deterministicOrigin.toShortString(),
                    current.supportLaneName(),
                    current.supportLaneLegal(),
                    deterministicAuthoringMode,
                    deterministicFixtureMatched);
            return;
        }
        StringBuilder reasons = new StringBuilder();
        String verdict = "GREEN";

        boolean laneChanged = !equalsOrBothNull(baseline.supportLaneName(), current.supportLaneName());
        boolean laneLegalChanged = baseline.supportLaneLegal() != current.supportLaneLegal();
        if (laneChanged) {
            verdict = "RED";
            reasons.append("lane_name_changed:").append(baseline.supportLaneName()).append("->").append(current.supportLaneName()).append(";");
        }
        if (laneLegalChanged) {
            verdict = "RED";
            reasons.append("lane_legal_flipped;");
        }

        double dyDelta = computeDelta(baseline.objectDyClient(), current.objectDyClient());
        double supportTopDelta = computeDelta(baseline.supportTopWorldY(), current.supportTopWorldY());
        double objBotDelta = computeDelta(baseline.objectVisualBottomWorldY(), current.objectVisualBottomWorldY());
        double modelDelta = computeDelta(baseline.modelOverlap(), current.modelOverlap());
        double outlineDelta = computeDelta(baseline.outlineOverlap(), current.outlineOverlap());
        double targetDelta = computeDelta(baseline.targetOverlap(), current.targetOverlap());

        verdict = applyScalarRule(verdict, reasons, "dy", baseline.objectDyClient(), current.objectDyClient(), dyDelta);
        verdict = applyScalarRule(verdict, reasons, "support_top_world_y", baseline.supportTopWorldY(), current.supportTopWorldY(), supportTopDelta);
        verdict = applyScalarRule(verdict, reasons, "object_visual_bottom_world_y", baseline.objectVisualBottomWorldY(), current.objectVisualBottomWorldY(), objBotDelta);

        verdict = applyOverlapRule(verdict, reasons, "model", baseline.modelOverlap(), current.modelOverlap());
        verdict = applyOverlapRule(verdict, reasons, "outline", baseline.outlineOverlap(), current.outlineOverlap());
        verdict = applyOverlapRule(verdict, reasons, "target", baseline.targetOverlap(), current.targetOverlap());

        boolean anchorChanged = baseline.anchorClient() != current.anchorClient();
        boolean loweredCarrierChanged = baseline.loweredCarrierClient() != current.loweredCarrierClient();
        boolean compoundFullChanged = baseline.compoundFullBlockAnchorClient() != current.compoundFullBlockAnchorClient();
        boolean compoundSideUpperChanged = baseline.compoundVisibleSideUpperSlabClient() != current.compoundVisibleSideUpperSlabClient();
        boolean renderBridgeChanged = baseline.renderViewBridgeSeen() != current.renderViewBridgeSeen();

        if (baseline.anchorClient() && !current.anchorClient()) {
            verdict = "RED";
            reasons.append("anchor_client_disappeared;");
        }
        if (baseline.loweredCarrierClient() && !current.loweredCarrierClient()) {
            verdict = "RED";
            reasons.append("lowered_carrier_client_disappeared;");
        }
        if (baseline.compoundFullBlockAnchorClient() && !current.compoundFullBlockAnchorClient()) {
            verdict = "RED";
            reasons.append("compound_full_block_anchor_client_disappeared;");
        }
        if (baseline.compoundVisibleSideUpperSlabClient() && !current.compoundVisibleSideUpperSlabClient()) {
            verdict = "RED";
            reasons.append("compound_visible_side_upper_slab_client_disappeared;");
        }
        if (renderBridgeChanged) {
            if (!"RED".equals(verdict)) {
                verdict = "INCONCLUSIVE";
            }
            reasons.append("render_view_bridge_seen_flipped_classified_inconclusive;");
        }

        if ("RED".equals(current.result())) {
            verdict = "RED";
            reasons.append("post_reload_row_red:").append(safe(current.reason())).append(";");
        } else if ("INCONCLUSIVE".equals(current.result())) {
            if (!"RED".equals(verdict)) {
                verdict = "INCONCLUSIVE";
            }
            reasons.append("post_reload_row_inconclusive:").append(safe(current.reason())).append(";");
        }

        if (reasons.length() == 0) {
            reasons.append("scenario_persisted_across_reload");
        }

        countReloadRow(verdict);
        Slabbed.LOGGER.info(
                "[MC1211_DETERMINISTIC_OVERLAP_RELOAD_ROW] phase=delta scenario={} origin={} laneName={} laneLegal={} dyDelta={} supportTopWorldYDelta={} objectVisualBottomWorldYDelta={} modelOverlapDelta={} outlineOverlapDelta={} targetOverlapDelta={} laneChanged={} laneLegalChanged={} anchorClientChanged={} loweredCarrierClientChanged={} compoundFullBlockAnchorClientChanged={} compoundVisibleSideUpperSlabClientChanged={} renderViewBridgeSeenChanged={} authoredBy={} fixtureMatched={} result={} reason={}",
                current.scenario(),
                deterministicOrigin == null ? "n/a" : deterministicOrigin.toShortString(),
                current.supportLaneName(),
                current.supportLaneLegal(),
                fmt(dyDelta),
                fmt(supportTopDelta),
                fmt(objBotDelta),
                fmt(modelDelta),
                fmt(outlineDelta),
                fmt(targetDelta),
                laneChanged,
                laneLegalChanged,
                anchorChanged,
                loweredCarrierChanged,
                compoundFullChanged,
                compoundSideUpperChanged,
                renderBridgeChanged,
                deterministicAuthoringMode,
                deterministicFixtureMatched,
                verdict,
                reasons.toString());
    }

    private static String applyScalarRule(String verdict, StringBuilder reasons, String name, double pre, double post, double delta) {
        if (Double.isFinite(pre) && Double.isFinite(post)) {
            if (Math.abs(delta) > EPSILON) {
                reasons.append(name).append("_delta_exceeds_epsilon:").append(fmt(delta)).append(";");
                return "RED";
            }
        } else {
            reasons.append(name).append("_field_inconclusive_nan;");
            if (!"RED".equals(verdict)) {
                return "INCONCLUSIVE";
            }
        }
        return verdict;
    }

    private static String applyOverlapRule(String verdict, StringBuilder reasons, String name, double pre, double post) {
        if (Double.isFinite(post) && post > EPSILON) {
            reasons.append(name).append("_overlap_finite_positive_post:").append(fmt(post)).append(";");
            return "RED";
        }
        if (Double.isFinite(pre) && pre > EPSILON) {
            reasons.append(name).append("_overlap_was_finite_positive_pre:").append(fmt(pre)).append(";");
            return "RED";
        }
        if (!Double.isFinite(pre) || !Double.isFinite(post)) {
            reasons.append(name).append("_overlap_inconclusive_nan;");
            if (!"RED".equals(verdict)) {
                return "INCONCLUSIVE";
            }
        }
        return verdict;
    }

    private static double computeDelta(double pre, double post) {
        if (!Double.isFinite(pre) || !Double.isFinite(post)) {
            return Double.NaN;
        }
        return post - pre;
    }

    private static boolean equalsOrBothNull(String a, String b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static void countReloadRow(String verdict) {
        rows++;
        reloadRows++;
        if ("GREEN".equals(verdict)) {
            greenRows++;
            reloadGreenRows++;
        } else if ("RED".equals(verdict)) {
            redRows++;
            reloadRedRows++;
        } else {
            inconclusiveRows++;
            reloadInconclusiveRows++;
        }
    }

    private static void emitReloadRowLine(String phase, ScenarioMeasurement m, FixturePair pair) {
        Slabbed.LOGGER.info(
                "[MC1211_DETERMINISTIC_OVERLAP_RELOAD_ROW] phase={} scenario={} origin={} supportPos={} supportState={} supportSlabType={} supportDy={} supportSurfaceOffset={} supportTopWorldY={} supportLaneName={} supportLaneLegal={} objectPos={} objectState={} objectBlockClass={} objectDyClient={} objectDyModel={} objectOutlineMinYLocal={} objectOutlineMaxYLocal={} objectOutlineMinYWorld={} objectOutlineMaxYWorld={} objectTargetMinYWorld={} objectVisualBottomWorldY={} modelOverlap={} outlineOverlap={} targetOverlap={} anchorClient={} loweredCarrierClient={} compoundFullBlockAnchorClient={} compoundVisibleOwnerTopSlabClient={} compoundVisibleSideLowerSlabClient={} compoundVisibleSideUpperSlabClient={} compoundVisibleSideDoubleSlabClient={} renderViewBridgeSeen={} authoredBy={} fixtureMatched={} result={} reason={}",
                phase,
                m.scenario(),
                deterministicOrigin == null ? "n/a" : deterministicOrigin.toShortString(),
                m.supportPos().toShortString(),
                m.supportStateRepr(),
                m.supportSlabType(),
                fmt(m.supportDy()),
                fmt(m.supportSurfaceOffset()),
                fmt(m.supportTopWorldY()),
                m.supportLaneName(),
                m.supportLaneLegal(),
                m.objectPos().toShortString(),
                m.objectStateRepr(),
                m.objectBlockClass(),
                fmt(m.objectDyClient()),
                fmt(m.objectDyModel()),
                fmt(m.objectOutlineMinYLocal()),
                fmt(m.objectOutlineMaxYLocal()),
                fmt(m.objectOutlineMinYWorld()),
                fmt(m.objectOutlineMaxYWorld()),
                fmt(m.objectTargetMinYWorld()),
                fmt(m.objectVisualBottomWorldY()),
                fmt(m.modelOverlap()),
                fmt(m.outlineOverlap()),
                fmt(m.targetOverlap()),
                m.anchorClient(),
                m.loweredCarrierClient(),
                m.compoundFullBlockAnchorClient(),
                m.compoundVisibleOwnerTopSlabClient(),
                m.compoundVisibleSideLowerSlabClient(),
                m.compoundVisibleSideUpperSlabClient(),
                m.compoundVisibleSideDoubleSlabClient(),
                m.renderViewBridgeSeen(),
                deterministicAuthoringMode,
                deterministicFixtureMatched,
                m.result(),
                safe(m.reason()));
    }

    private static void emitReloadFixtureLevelInconclusive(String phase, String reason) {
        countReloadRow("INCONCLUSIVE");
        Slabbed.LOGGER.info(
                "[MC1211_DETERMINISTIC_OVERLAP_RELOAD_ROW] phase={} scenario=FIXTURE_LEVEL origin={} authoredBy={} fixtureMatched={} result=INCONCLUSIVE reason={}",
                phase,
                deterministicOrigin == null ? "n/a" : deterministicOrigin.toShortString(),
                deterministicAuthoringMode,
                deterministicFixtureMatched,
                reason);
        if (!reloadSummaryEmitted) {
            emitReloadSummary(phase);
        }
    }

    private static void emitReloadSummary(String phase) {
        reloadSummaryEmitted = true;
        Slabbed.LOGGER.info(
                "[MC1211_DETERMINISTIC_OVERLAP_RELOAD_SUMMARY] phase={} reloadRows={} reloadGreen={} reloadRed={} reloadInconclusive={} authoredBy={} fixtureMatched={} releaseReady=false diagnosticsOnly=true semanticsChanged=false",
                phase,
                reloadRows,
                reloadGreenRows,
                reloadRedRows,
                reloadInconclusiveRows,
                deterministicAuthoringMode,
                deterministicFixtureMatched);
    }

    private static String serializeSignature(BlockPos origin, Map<String, ScenarioMeasurement> measurements,
                                             String authoringMode, boolean fixtureMatched) {
        StringBuilder sb = new StringBuilder();
        sb.append("# MC1211 deterministic-overlap-reload signature\n");
        sb.append("# format: key=value (escaped \\n, \\\\, \\=)\n");
        sb.append("schemaVersion=1\n");
        sb.append("authoredAtMillis=").append(System.currentTimeMillis()).append("\n");
        sb.append("authoredBy=").append(authoringMode).append("\n");
        sb.append("fixtureMatched=").append(fixtureMatched).append("\n");
        sb.append("origin.x=").append(origin.getX()).append("\n");
        sb.append("origin.y=").append(origin.getY()).append("\n");
        sb.append("origin.z=").append(origin.getZ()).append("\n");
        sb.append("scenarioCount=").append(measurements.size()).append("\n");
        for (Map.Entry<String, ScenarioMeasurement> entry : measurements.entrySet()) {
            String prefix = "preReload." + entry.getKey() + ".";
            ScenarioMeasurement m = entry.getValue();
            sb.append(prefix).append("scenario=").append(m.scenario()).append("\n");
            sb.append(prefix).append("supportPos=").append(m.supportPos().getX()).append(",").append(m.supportPos().getY()).append(",").append(m.supportPos().getZ()).append("\n");
            sb.append(prefix).append("supportState=").append(escape(m.supportStateRepr())).append("\n");
            sb.append(prefix).append("supportSlabType=").append(m.supportSlabType()).append("\n");
            sb.append(prefix).append("supportDy=").append(serFmt(m.supportDy())).append("\n");
            sb.append(prefix).append("supportSurfaceOffset=").append(serFmt(m.supportSurfaceOffset())).append("\n");
            sb.append(prefix).append("supportTopWorldY=").append(serFmt(m.supportTopWorldY())).append("\n");
            sb.append(prefix).append("supportLaneName=").append(m.supportLaneName()).append("\n");
            sb.append(prefix).append("supportLaneLegal=").append(m.supportLaneLegal()).append("\n");
            sb.append(prefix).append("objectPos=").append(m.objectPos().getX()).append(",").append(m.objectPos().getY()).append(",").append(m.objectPos().getZ()).append("\n");
            sb.append(prefix).append("objectState=").append(escape(m.objectStateRepr())).append("\n");
            sb.append(prefix).append("objectBlockClass=").append(m.objectBlockClass()).append("\n");
            sb.append(prefix).append("objectDyClient=").append(serFmt(m.objectDyClient())).append("\n");
            sb.append(prefix).append("objectDyModel=").append(serFmt(m.objectDyModel())).append("\n");
            sb.append(prefix).append("objectOutlineMinYLocal=").append(serFmt(m.objectOutlineMinYLocal())).append("\n");
            sb.append(prefix).append("objectOutlineMaxYLocal=").append(serFmt(m.objectOutlineMaxYLocal())).append("\n");
            sb.append(prefix).append("objectOutlineMinYWorld=").append(serFmt(m.objectOutlineMinYWorld())).append("\n");
            sb.append(prefix).append("objectOutlineMaxYWorld=").append(serFmt(m.objectOutlineMaxYWorld())).append("\n");
            sb.append(prefix).append("objectTargetMinYWorld=").append(serFmt(m.objectTargetMinYWorld())).append("\n");
            sb.append(prefix).append("objectVisualBottomWorldY=").append(serFmt(m.objectVisualBottomWorldY())).append("\n");
            sb.append(prefix).append("modelOverlap=").append(serFmt(m.modelOverlap())).append("\n");
            sb.append(prefix).append("outlineOverlap=").append(serFmt(m.outlineOverlap())).append("\n");
            sb.append(prefix).append("targetOverlap=").append(serFmt(m.targetOverlap())).append("\n");
            sb.append(prefix).append("anchorClient=").append(m.anchorClient()).append("\n");
            sb.append(prefix).append("loweredCarrierClient=").append(m.loweredCarrierClient()).append("\n");
            sb.append(prefix).append("compoundFullBlockAnchorClient=").append(m.compoundFullBlockAnchorClient()).append("\n");
            sb.append(prefix).append("compoundVisibleOwnerTopSlabClient=").append(m.compoundVisibleOwnerTopSlabClient()).append("\n");
            sb.append(prefix).append("compoundVisibleSideLowerSlabClient=").append(m.compoundVisibleSideLowerSlabClient()).append("\n");
            sb.append(prefix).append("compoundVisibleSideUpperSlabClient=").append(m.compoundVisibleSideUpperSlabClient()).append("\n");
            sb.append(prefix).append("compoundVisibleSideDoubleSlabClient=").append(m.compoundVisibleSideDoubleSlabClient()).append("\n");
            sb.append(prefix).append("renderViewBridgeSeen=").append(m.renderViewBridgeSeen()).append("\n");
            sb.append(prefix).append("result=").append(m.result()).append("\n");
            sb.append(prefix).append("reason=").append(escape(safe(m.reason()))).append("\n");
        }
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("=", "\\=");
    }

    private static String unescape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == 'n') {
                    sb.append('\n');
                    i += 2;
                    continue;
                }
                if (n == '=') {
                    sb.append('=');
                    i += 2;
                    continue;
                }
                if (n == '\\') {
                    sb.append('\\');
                    i += 2;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static String serFmt(double d) {
        return Double.isFinite(d) ? String.format(Locale.ROOT, "%.10f", d) : "NaN";
    }

    private static SignatureLoadResult readSignature(Path path) {
        if (path == null) {
            return new SignatureLoadResult(false, "signature_path_null", null, new LinkedHashMap<>());
        }
        if (!Files.exists(path)) {
            return new SignatureLoadResult(false, "signature_file_missing:" + path, null, new LinkedHashMap<>());
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            Map<String, String> kv = new LinkedHashMap<>();
            for (String line : lines) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String k = line.substring(0, eq).trim();
                String v = line.substring(eq + 1);
                kv.put(k, v);
            }
            Integer ox = parseIntOrNull(kv.get("origin.x"));
            Integer oy = parseIntOrNull(kv.get("origin.y"));
            Integer oz = parseIntOrNull(kv.get("origin.z"));
            if (ox == null || oy == null || oz == null) {
                return new SignatureLoadResult(false, "missing_origin_in_signature", null, new LinkedHashMap<>());
            }
            BlockPos origin = new BlockPos(ox, oy, oz);
            Set<String> scenarios = new LinkedHashSet<>();
            for (String k : kv.keySet()) {
                if (k.startsWith("preReload.")) {
                    String rest = k.substring("preReload.".length());
                    int dot = rest.indexOf('.');
                    if (dot > 0) {
                        scenarios.add(rest.substring(0, dot));
                    }
                }
            }
            Map<String, ScenarioMeasurement> measurements = new LinkedHashMap<>();
            for (String s : scenarios) {
                String prefix = "preReload." + s + ".";
                BlockPos sup = parsePosTriple(kv.get(prefix + "supportPos"));
                BlockPos obj = parsePosTriple(kv.get(prefix + "objectPos"));
                if (sup == null || obj == null) {
                    continue;
                }
                ScenarioMeasurement m = new ScenarioMeasurement(
                        kv.getOrDefault(prefix + "scenario", s),
                        sup,
                        unescape(kv.getOrDefault(prefix + "supportState", "")),
                        kv.getOrDefault(prefix + "supportSlabType", "n/a"),
                        parseDoubleSafe(kv.get(prefix + "supportDy")),
                        parseDoubleSafe(kv.get(prefix + "supportSurfaceOffset")),
                        parseDoubleSafe(kv.get(prefix + "supportTopWorldY")),
                        kv.getOrDefault(prefix + "supportLaneName", "UNKNOWN"),
                        parseBoolSafe(kv.get(prefix + "supportLaneLegal")),
                        obj,
                        unescape(kv.getOrDefault(prefix + "objectState", "")),
                        kv.getOrDefault(prefix + "objectBlockClass", "n/a"),
                        parseDoubleSafe(kv.get(prefix + "objectDyClient")),
                        parseDoubleSafe(kv.get(prefix + "objectDyModel")),
                        parseDoubleSafe(kv.get(prefix + "objectOutlineMinYLocal")),
                        parseDoubleSafe(kv.get(prefix + "objectOutlineMaxYLocal")),
                        parseDoubleSafe(kv.get(prefix + "objectOutlineMinYWorld")),
                        parseDoubleSafe(kv.get(prefix + "objectOutlineMaxYWorld")),
                        parseDoubleSafe(kv.get(prefix + "objectTargetMinYWorld")),
                        parseDoubleSafe(kv.get(prefix + "objectVisualBottomWorldY")),
                        parseDoubleSafe(kv.get(prefix + "modelOverlap")),
                        parseDoubleSafe(kv.get(prefix + "outlineOverlap")),
                        parseDoubleSafe(kv.get(prefix + "targetOverlap")),
                        parseBoolSafe(kv.get(prefix + "anchorClient")),
                        parseBoolSafe(kv.get(prefix + "loweredCarrierClient")),
                        parseBoolSafe(kv.get(prefix + "compoundFullBlockAnchorClient")),
                        parseBoolSafe(kv.get(prefix + "compoundVisibleOwnerTopSlabClient")),
                        parseBoolSafe(kv.get(prefix + "compoundVisibleSideLowerSlabClient")),
                        parseBoolSafe(kv.get(prefix + "compoundVisibleSideUpperSlabClient")),
                        parseBoolSafe(kv.get(prefix + "compoundVisibleSideDoubleSlabClient")),
                        parseBoolSafe(kv.get(prefix + "renderViewBridgeSeen")),
                        kv.getOrDefault(prefix + "result", "UNKNOWN"),
                        unescape(kv.getOrDefault(prefix + "reason", "")));
                measurements.put(s, m);
            }
            return new SignatureLoadResult(true, "ok", origin, measurements);
        } catch (IOException ex) {
            return new SignatureLoadResult(false, "io_error:" + ex.getClass().getSimpleName(), null, new LinkedHashMap<>());
        }
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double parseDoubleSafe(String s) {
        if (s == null) {
            return Double.NaN;
        }
        String t = s.trim();
        if (t.isEmpty() || "NaN".equalsIgnoreCase(t)) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private static boolean parseBoolSafe(String s) {
        if (s == null) {
            return false;
        }
        return Boolean.parseBoolean(s.trim());
    }

    private static BlockPos parsePosTriple(String s) {
        if (s == null) {
            return null;
        }
        String[] parts = s.trim().split(",");
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

    private record ShapeMetrics(
            double dyClient,
            double dyModel,
            double outlineMinYLocal,
            double outlineMaxYLocal,
            double outlineMinYWorld,
            double outlineMaxYWorld,
            double targetMinYWorld,
            double visualBottomWorldY,
            double visualTopWorldY
    ) {
    }

    private record AttachmentFlags(
            boolean anchorClient,
            boolean anchorRender,
            boolean loweredCarrierClient,
            boolean loweredCarrierRender,
            boolean compoundFullBlockAnchorClient,
            boolean compoundFullBlockAnchorRender,
            boolean compoundVisibleOwnerTopSlabClient,
            boolean compoundVisibleOwnerTopSlabRender,
            boolean compoundVisibleSideLowerSlabClient,
            boolean compoundVisibleSideLowerSlabRender,
            boolean compoundVisibleSideUpperSlabClient,
            boolean compoundVisibleSideUpperSlabRender,
            boolean compoundVisibleSideDoubleSlabClient,
            boolean compoundVisibleSideDoubleSlabRender
    ) {
        boolean renderBridgeSeen() {
            return anchorClient == anchorRender
                    && loweredCarrierClient == loweredCarrierRender
                    && compoundFullBlockAnchorClient == compoundFullBlockAnchorRender
                    && compoundVisibleOwnerTopSlabClient == compoundVisibleOwnerTopSlabRender
                    && compoundVisibleSideLowerSlabClient == compoundVisibleSideLowerSlabRender
                    && compoundVisibleSideUpperSlabClient == compoundVisibleSideUpperSlabRender
                    && compoundVisibleSideDoubleSlabClient == compoundVisibleSideDoubleSlabRender;
        }
    }

    private record CrosshairInfo(String pos, String state, String face, String owner) {
        static CrosshairInfo none() {
            return new CrosshairInfo("none", "none", "none", "none");
        }

        String describe() {
            return "pos=" + pos + ",state=" + state + ",face=" + face + ",owner=" + owner;
        }
    }

    private record Lane(String name, boolean legal) {
    }

    private record Verdict(String result, String reason) {
    }

    private record ServerStateExpectation(
            String laneName,
            double serverDy,
            boolean requireAnchor,
            boolean forbidAnchor,
            boolean requireCarrier,
            boolean forbidCarrier,
            boolean requireCompoundAnchor,
            boolean requireSideUpper,
            String greenReason
    ) {
    }

    private record ServerStateOverlapRow(
            String scenario,
            BlockPos supportPos,
            String supportState,
            String supportSlabType,
            BlockPos objectPos,
            String objectState,
            String expectedLaneName,
            double serverDy,
            double supportDy,
            double supportSurfaceOffset,
            double supportTopWorldY,
            double objectBottomWorldY,
            double serverOverlap,
            boolean anchorServer,
            boolean carrierServer,
            boolean laneLegal,
            String result,
            String reason
    ) {
        private String toMarkerLine() {
            return "[MC1211_SERVER_STATE_OVERLAP_MATRIX_ROW]"
                    + " scenario=" + markerSafe(scenario)
                    + " supportPos=" + posText(supportPos)
                    + " supportState=" + markerSafe(supportState)
                    + " supportSlabType=" + supportSlabType
                    + " objectPos=" + posText(objectPos)
                    + " objectState=" + markerSafe(objectState)
                    + " expectedLaneName=" + markerSafe(expectedLaneName)
                    + " serverDy=" + fmt(serverDy)
                    + " supportDy=" + fmt(supportDy)
                    + " supportSurfaceOffset=" + fmt(supportSurfaceOffset)
                    + " supportTopWorldY=" + fmt(supportTopWorldY)
                    + " objectBottomWorldY=" + fmt(objectBottomWorldY)
                    + " serverOverlap=" + fmt(serverOverlap)
                    + " anchorServer=" + anchorServer
                    + " carrierServer=" + carrierServer
                    + " laneStatus=" + ("DEFERRED".equals(result) ? "deferred_illegal-for-current-release" : (laneLegal ? "legal" : "illegal"))
                    + " laneLegal=" + laneLegal
                    + " result=" + result
                    + " reason=" + markerSafe(reason);
        }
    }

    private record FixturePair(String scenario, BlockPos supportPos, BlockPos objectPos, String authoredBy) {
    }

    private record FixtureContext(BlockPos origin, String phase, String authoredBy, int ticksSinceAuthoring) {
    }

    private record FixtureAuthoringResult(boolean success, String reason) {
    }

    private record ScenarioMeasurement(
            String scenario,
            BlockPos supportPos,
            String supportStateRepr,
            String supportSlabType,
            double supportDy,
            double supportSurfaceOffset,
            double supportTopWorldY,
            String supportLaneName,
            boolean supportLaneLegal,
            BlockPos objectPos,
            String objectStateRepr,
            String objectBlockClass,
            double objectDyClient,
            double objectDyModel,
            double objectOutlineMinYLocal,
            double objectOutlineMaxYLocal,
            double objectOutlineMinYWorld,
            double objectOutlineMaxYWorld,
            double objectTargetMinYWorld,
            double objectVisualBottomWorldY,
            double modelOverlap,
            double outlineOverlap,
            double targetOverlap,
            boolean anchorClient,
            boolean loweredCarrierClient,
            boolean compoundFullBlockAnchorClient,
            boolean compoundVisibleOwnerTopSlabClient,
            boolean compoundVisibleSideLowerSlabClient,
            boolean compoundVisibleSideUpperSlabClient,
            boolean compoundVisibleSideDoubleSlabClient,
            boolean renderViewBridgeSeen,
            String result,
            String reason
    ) {
    }

    private record SignatureLoadResult(
            boolean success,
            String reason,
            BlockPos origin,
            Map<String, ScenarioMeasurement> measurements
    ) {
    }
}
