package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.upgrade.WorldUpgradeDecision;
import com.slabbed.upgrade.WorldUpgradeRuntimePolicy;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameMode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;

/** Test-only integrated-server piston handoff probe; the caller invokes {@link #tick} each client tick. */
public final class PistonLiveCycleProbe {
    private static final double DY = -3.0d;
    private static final int TIMEOUT_TICKS = 40;
    private static final double MINIMUM_FRAMING_DOT = Math.cos(Math.toRadians(32.0d));

    private static Phase phase = Phase.IDLE;
    private static int ticks;
    private static int phaseTick;
    private static int runId;
    private static List<Rig> rigs = List.of();
    private static volatile boolean serverPending;
    private static volatile ServerSnapshot serverSnapshot;
    private static Vec3d oldPlayerPos;
    private static float oldYaw;
    private static float oldPitch;
    private static Vec3d viewingPos;
    private static float viewingYaw;
    private static float viewingPitch;
    private static boolean keepMode;
    private static long renderedCameraFrame;
    private static long worldRenderEndCalls;
    private static long phaseRenderStart;
    private static long runStartNanos;
    private static boolean seededWorldRenderSeen;
    private static String lastDiagnostic = "";
    private static long poseStartFrame;
    private static boolean cameraFramed;
    private static double cameraMinimumDot = Double.NaN;
    private static String extensionScreenshot = "NOT_RUN";
    private static String retractionScreenshot = "NOT_RUN";
    private static long extensionRenderedFrames;
    private static long retractionRenderedFrames;
    private static Result terminal;

    private PistonLiveCycleProbe() {
    }

    public static void start(MinecraftClient client, BlockPos basePos) {
        runId++;
        ticks = 0;
        worldRenderEndCalls = 0L;
        phaseRenderStart = 0L;
        runStartNanos = System.nanoTime();
        seededWorldRenderSeen = false;
        lastDiagnostic = "";
        phaseTick = 0;
        serverPending = false;
        serverSnapshot = null;
        terminal = null;
        keepMode = ready(client) && !SlabAnchorAttachment.FROZEN_DY_ENABLED
                && WorldUpgradeRuntimePolicy.mode(client.world) == WorldUpgradeDecision.Mode.KEEP_EXISTING;
        if (!renderDyComparisonSentinelGreen()) {
            phase = Phase.DONE;
            terminal = Result.error("render dy comparison sentinel failed");
            return;
        }
        if (!ready(client) || basePos == null
                || (!SlabAnchorAttachment.FROZEN_DY_ENABLED && !keepMode)) {
            phase = Phase.DONE;
            terminal = Result.error("client/server/base unavailable or no frozen/KEEP height authority");
            return;
        }

        rigs = List.of(
                new Rig("horizontal", basePos.toImmutable(), Direction.EAST),
                new Rig("vertical", basePos.south(5).toImmutable(), Direction.UP));
        oldPlayerPos = client.player.getPos();
        oldYaw = client.player.getYaw();
        oldPitch = client.player.getPitch();
        movePlayerNear(client, basePos);
        viewingPos = client.player.getPos();
        viewingYaw = client.player.getYaw();
        viewingPitch = client.player.getPitch();
        poseStartFrame = renderedCameraFrame;
        cameraFramed = false;
        cameraMinimumDot = Double.NaN;
        extensionScreenshot = "NOT_RUN";
        retractionScreenshot = "NOT_RUN";
        extensionRenderedFrames = 0L;
        retractionRenderedFrames = 0L;
        BlockEntityRenderAudit.reset();
        phase = Phase.WAIT_SEED;
        scheduleServer(client, world -> {
            if (!client.getServer().getPlayerManager().getPlayerList().isEmpty()) {
                var player = client.getServer().getPlayerManager().getPlayerList().get(0);
                player.refreshPositionAndAngles(
                        viewingPos.x, viewingPos.y, viewingPos.z, viewingYaw, viewingPitch);
                player.setVelocity(Vec3d.ZERO);
                player.changeGameMode(GameMode.CREATIVE);
                player.getAbilities().allowFlying = true;
                player.getAbilities().flying = true;
                player.sendAbilitiesUpdate();
            }
            if (keepMode) {
                WorldUpgradeRuntimePolicy.activate(world, WorldUpgradeDecision.Mode.KEEP_EXISTING);
            }
            for (Rig rig : rigs) {
                clearRig(world, rig);
                world.setBlockState(rig.base(), Blocks.STICKY_PISTON.getDefaultState()
                        .with(PistonBlock.FACING, rig.direction()), Block.NOTIFY_ALL);
                world.setBlockState(rig.payloadStart(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                SlabAnchorAttachment.writePlacementDyBatch(world, Map.of(
                        rig.base(), Double.doubleToRawLongBits(DY),
                        rig.payloadStart(), Double.doubleToRawLongBits(DY)));
                if (keepMode) {
                    SlabAnchorAttachment.markPostPolicyPlacements(
                            world, List.of(rig.base(), rig.payloadStart()));
                }
            }
        });
    }

    public static Result tick(MinecraftClient client) {
        if (phase == Phase.DONE) {
            return terminal;
        }
        if (!ready(client)) {
            return fail(client, "client or integrated server became unavailable");
        }
        holdClientPose(client);
        ticks++;
        if (ticks > TIMEOUT_TICKS) {
            return fail(client, "timeout in " + phase);
        }

        return switch (phase) {
            case IDLE -> Result.error("probe not started");
            case WAIT_SEED -> waitSeed(client);
            case WAIT_EXTENSION -> waitExtension(client);
            case WAIT_EXTENSION_SERVER -> waitExtensionServer(client);
            case WAIT_RETRACTION -> waitRetraction(client);
            case WAIT_FINAL_SERVER -> waitFinalServer(client);
            case DONE -> terminal;
        };
    }

    public static void onWorldRendered(Camera camera) {
        if (phase != Phase.WAIT_SEED && phase != Phase.WAIT_EXTENSION
                && phase != Phase.WAIT_RETRACTION) {
            return;
        }
        worldRenderEndCalls++;
        if (camera == null || !camera.isReady()) {
            diagnostic(null, "world_end_camera_unready", false);
            return;
        }
        renderedCameraFrame++;
        cameraMinimumDot = minimumFramingDot(camera);
        cameraFramed = Double.isFinite(cameraMinimumDot)
                && cameraMinimumDot >= MINIMUM_FRAMING_DOT;
        if (phase == Phase.WAIT_SEED) {
            seededWorldRenderSeen |= clientRigsMatch(MinecraftClient.getInstance(), Stage.SEEDED);
            diagnostic(null, "world_end_seed", false);
        } else {
            diagnostic(phase == Phase.WAIT_EXTENSION ? Stage.EXTENDED : Stage.RETRACTED,
                    "world_end_moving", false);
        }
        if (!cameraFramed) {
            return;
        }

        Stage stage = phase == Phase.WAIT_EXTENSION ? Stage.EXTENDED
                : phase == Phase.WAIT_RETRACTION ? Stage.RETRACTED : null;
        if (stage == null || !movingFrameReady(MinecraftClient.getInstance(), stage)) {
            return;
        }
        if (stage == Stage.EXTENDED && extensionScreenshot.equals("NOT_RUN")) {
            extensionScreenshot = captureMovingFrame("piston-live-extension-moving");
            logScreenshot(stage, extensionScreenshot);
        } else if (stage == Stage.RETRACTED && retractionScreenshot.equals("NOT_RUN")) {
            retractionScreenshot = captureMovingFrame("piston-live-retraction-moving");
            logScreenshot(stage, retractionScreenshot);
        }
    }

    public static boolean screenshotsCaptured() {
        return !extensionScreenshot.equals("NOT_RUN") && !extensionScreenshot.startsWith("ERROR")
                && !retractionScreenshot.equals("NOT_RUN") && !retractionScreenshot.startsWith("ERROR");
    }

    private static Result waitSeed(MinecraftClient client) {
        if (serverPending || !clientRigsMatch(client, Stage.SEEDED)
                || renderedCameraFrame <= poseStartFrame || !cameraFramed) {
            return Result.pending("seed/camera sync");
        }
        BlockEntityRenderAudit.reset();
        scheduleServer(client, world -> rigs.forEach(rig ->
                world.setBlockState(rig.power(), Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL)));
        phase = Phase.WAIT_EXTENSION;
        phaseTick = ticks;
        phaseRenderStart = worldRenderEndCalls;
        diagnostic(Stage.EXTENDED, "power_on_scheduled", true);
        return Result.pending("extension");
    }

    private static Result waitExtension(MinecraftClient client) {
        if (serverPending || !clientRigsMatch(client, Stage.EXTENDED)) {
            return Result.pending("extension frames");
        }
        diagnostic(Stage.EXTENDED, "client_final_state", true);
        String renderError = renderEvidence(Stage.EXTENDED);
        if (renderError != null) {
            return fail(client, renderError);
        }
        extensionRenderedFrames = renderedFrames(Stage.EXTENDED);
        requestSnapshot(client, Stage.EXTENDED);
        diagnostic(Stage.EXTENDED, "server_snapshot_requested", true);
        phase = Phase.WAIT_EXTENSION_SERVER;
        phaseTick = ticks;
        return Result.pending("extension server readback");
    }

    private static Result waitExtensionServer(MinecraftClient client) {
        if (serverPending || serverSnapshot == null) {
            return Result.pending("extension server readback");
        }
        String mismatch = serverSnapshot.mismatch(Stage.EXTENDED);
        if (mismatch != null) {
            return fail(client, mismatch);
        }
        serverSnapshot = null;
        BlockEntityRenderAudit.reset();
        scheduleServer(client, world -> rigs.forEach(rig ->
                world.setBlockState(rig.power(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL)));
        phase = Phase.WAIT_RETRACTION;
        phaseTick = ticks;
        phaseRenderStart = worldRenderEndCalls;
        diagnostic(Stage.RETRACTED, "power_off_scheduled", true);
        return Result.pending("retraction");
    }

    private static Result waitRetraction(MinecraftClient client) {
        if (serverPending || !clientRigsMatch(client, Stage.RETRACTED)) {
            return Result.pending("retraction frames");
        }
        diagnostic(Stage.RETRACTED, "client_final_state", true);
        String renderError = renderEvidence(Stage.RETRACTED);
        if (renderError != null) {
            return fail(client, renderError);
        }
        retractionRenderedFrames = renderedFrames(Stage.RETRACTED);
        requestSnapshot(client, Stage.RETRACTED);
        phase = Phase.WAIT_FINAL_SERVER;
        phaseTick = ticks;
        return Result.pending("final server readback");
    }

    private static Result waitFinalServer(MinecraftClient client) {
        if (serverPending || serverSnapshot == null) {
            return Result.pending("final server readback");
        }
        String mismatch = serverSnapshot.mismatch(Stage.RETRACTED);
        if (mismatch != null) {
            return fail(client, mismatch);
        }
        terminal = Result.green("two directions; extensionFrames=" + extensionRenderedFrames
                + " retractionFrames=" + retractionRenderedFrames
                + " cameraFrame=" + renderedCameraFrame + " cameraMinDot=" + cameraMinimumDot
                + " extensionScreenshot=" + extensionScreenshot
                + " retractionScreenshot=" + retractionScreenshot);
        phase = Phase.DONE;
        restorePlayer(client);
        return terminal;
    }

    private static boolean clientRigsMatch(MinecraftClient client, Stage stage) {
        for (Rig rig : rigs) {
            if (stageMismatch(client.world, rig, stage) != null) {
                return false;
            }
        }
        return true;
    }

    private static String renderEvidence(Stage stage) {
        for (Rig rig : rigs) {
            List<BlockPos> movingCells = stage == Stage.EXTENDED
                    ? List.of(rig.head(), rig.payloadExtended())
                    : List.of(rig.base(), rig.payloadStart());
            for (BlockPos pos : movingCells) {
                BlockEntityRenderAudit.Snapshot snapshot = BlockEntityRenderAudit.snapshot(pos);
                if (!snapshot.exactMatrixGreen()
                        || !same(snapshot.firstExpectedDy(), DY)
                        || !same(snapshot.minExpectedDy(), DY)
                        || !same(snapshot.maxExpectedDy(), DY)
                        || !BlockEntityRenderAudit.approximatelySameDy(snapshot.firstActualDy(), DY)
                        || !BlockEntityRenderAudit.approximatelySameDy(snapshot.minActualDy(), DY)
                        || !BlockEntityRenderAudit.approximatelySameDy(snapshot.maxActualDy(), DY)
                        || (keepMode && (!same(snapshot.firstRawDy(), DY)
                        || !same(snapshot.minRawDy(), DY)
                        || !same(snapshot.maxRawDy(), DY)
                        || snapshot.modernFrames() != snapshot.seenFrames()))) {
                    return rig.id() + " " + stage + " moving frame missing/misaligned at " + pos
                            + ": " + snapshot;
                }
            }
        }
        String screenshot = stage == Stage.EXTENDED ? extensionScreenshot : retractionScreenshot;
        if (screenshot.equals("NOT_RUN") || screenshot.startsWith("ERROR")) {
            return stage + " moving-frame screenshot missing: " + screenshot;
        }
        return null;
    }

    private static String stageMismatch(World world, Rig rig, Stage stage) {
        BlockState base = world.getBlockState(rig.base());
        BlockState first = world.getBlockState(rig.payloadStart());
        BlockState second = world.getBlockState(rig.payloadExtended());
        if (stage == Stage.SEEDED) {
            if (!base.isOf(Blocks.STICKY_PISTON) || !first.isOf(Blocks.STONE)
                    || !deep(world, rig.base()) || !deep(world, rig.payloadStart())) {
                return rig.id() + " seed mismatch";
            }
        } else if (stage == Stage.EXTENDED) {
            if (!base.isOf(Blocks.STICKY_PISTON)
                    || !base.get(PistonBlock.EXTENDED)
                    || !first.isOf(Blocks.PISTON_HEAD)
                    || !second.isOf(Blocks.STONE)
                    || !deep(world, rig.base())
                    || !deep(world, rig.head())
                    || !deep(world, rig.payloadExtended())) {
                return rig.id() + " extension mismatch";
            }
        } else if (!base.isOf(Blocks.STICKY_PISTON)
                || base.get(PistonBlock.EXTENDED)
                || !first.isOf(Blocks.STONE)
                || !second.isAir()
                || !deep(world, rig.base())
                || !deep(world, rig.payloadStart())
                || SlabAnchorAttachment.rawPlacementDyFact(world, rig.payloadExtended()).present()) {
            return rig.id() + " retraction mismatch";
        }
        return null;
    }

    private static boolean deep(World world, BlockPos pos) {
        SlabAnchorAttachment.PlacementDyFact fact =
                SlabAnchorAttachment.rawPlacementDyFact(world, pos);
        return fact.present() && same(fact.valueOrNaN(), DY)
                && (!keepMode || SlabAnchorAttachment.isModernPlacement(world, pos));
    }

    private static void requestSnapshot(MinecraftClient client, Stage stage) {
        serverSnapshot = null;
        scheduleServer(client, world -> serverSnapshot = ServerSnapshot.capture(world, stage, rigs));
    }

    private static void scheduleServer(MinecraftClient client, ServerAction action) {
        MinecraftServer server = client.getServer();
        var dimension = client.world.getRegistryKey();
        int expectedRun = runId;
        serverPending = true;
        server.execute(() -> {
            try {
                if (expectedRun != runId) {
                    return;
                }
                ServerWorld world = server.getWorld(dimension);
                if (world != null) {
                    action.run(world);
                }
            } finally {
                if (expectedRun == runId) {
                    serverPending = false;
                }
            }
        });
    }

    private static void movePlayerNear(MinecraftClient client, BlockPos base) {
        Vec3d focus = Vec3d.ofCenter(base).add(0.75d, DY + 0.75d, 2.5d);
        Vec3d eye = focus.add(3.0d, 3.0d, 8.0d), delta = focus.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        client.player.refreshPositionAndAngles(
                eye.x, eye.y - client.player.getStandingEyeHeight(), eye.z, yaw, pitch);
    }

    private static void holdClientPose(MinecraftClient client) {
        if (viewingPos == null || client.player == null) {
            return;
        }
        client.player.refreshPositionAndAngles(
                viewingPos.x, viewingPos.y, viewingPos.z, viewingYaw, viewingPitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.getAbilities().allowFlying = true;
        client.player.getAbilities().flying = true;
        client.player.sendAbilitiesUpdate();
    }

    private static boolean movingFrameReady(MinecraftClient client, Stage stage) {
        if (client == null || client.world == null) {
            return false;
        }
        for (Rig rig : rigs) {
            for (BlockPos pos : movingCells(rig, stage)) {
                if (!client.world.getBlockState(pos).isOf(Blocks.MOVING_PISTON)
                        || BlockEntityRenderAudit.snapshot(pos).seenFrames() == 0L) {
                    return false;
                }
            }
        }
        return true;
    }

    private static long renderedFrames(Stage stage) {
        long total = 0L;
        for (Rig rig : rigs) {
            for (BlockPos pos : movingCells(rig, stage)) {
                total += BlockEntityRenderAudit.snapshot(pos).seenFrames();
            }
        }
        return total;
    }

    private static List<BlockPos> movingCells(Rig rig, Stage stage) {
        return stage == Stage.EXTENDED
                ? List.of(rig.head(), rig.payloadExtended())
                : List.of(rig.base(), rig.payloadStart());
    }

    private static double minimumFramingDot(Camera camera) {
        Vec3d forward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).normalize();
        double minimum = 1.0d;
        for (Rig rig : rigs) {
            for (BlockPos pos : List.of(rig.base(), rig.payloadExtended())) {
                Vec3d point = Vec3d.ofCenter(pos).add(0.0d, DY, 0.0d);
                Vec3d delta = point.subtract(camera.getPos());
                if (delta.lengthSquared() == 0.0d) {
                    return Double.NEGATIVE_INFINITY;
                }
                minimum = Math.min(minimum, delta.normalize().dotProduct(forward));
            }
        }
        return minimum;
    }

    private static String captureMovingFrame(String label) {
        return ExtendedDepthClientProbe.screenshot(MinecraftClient.getInstance(), label);
    }

    private static void logScreenshot(Stage stage, String screenshot) {
        System.out.println("[PISTON_LIVE_FRAME] stage=" + stage + " cameraFrame="
                + renderedCameraFrame + " minDot=" + cameraMinimumDot
                + " screenshot=" + screenshot);
    }

    private static void restorePlayer(MinecraftClient client) {
        if (client.player == null || oldPlayerPos == null) {
            return;
        }
        client.player.refreshPositionAndAngles(
                oldPlayerPos.x, oldPlayerPos.y, oldPlayerPos.z, oldYaw, oldPitch);
        MinecraftServer server = client.getServer();
        Vec3d restorePos = oldPlayerPos;
        float restoreYaw = oldYaw;
        float restorePitch = oldPitch;
        if (server != null) {
            server.execute(() -> {
                if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                    var player = server.getPlayerManager().getPlayerList().get(0);
                    player.refreshPositionAndAngles(
                            restorePos.x, restorePos.y, restorePos.z, restoreYaw, restorePitch);
                }
            });
        }
    }

    private static Result fail(MinecraftClient client, String detail) {
        diagnostic(phase == Phase.WAIT_EXTENSION ? Stage.EXTENDED
                : phase == Phase.WAIT_RETRACTION ? Stage.RETRACTED : null, "failure", true);
        terminal = Result.error(detail + " at tick=" + ticks + " phaseAge=" + (ticks - phaseTick));
        phase = Phase.DONE;
        restorePlayer(client);
        return terminal;
    }

    private static void diagnostic(Stage stage, String event, boolean force) {
        MinecraftClient client = MinecraftClient.getInstance();
        StringBuilder state = new StringBuilder("seedFrame=").append(seededWorldRenderSeen);
        if (stage != null && client.world != null) {
            for (Rig rig : rigs) {
                state.append(' ').append(rig.id()).append('=');
                for (BlockPos pos : movingCells(rig, stage)) {
                    state.append(client.world.getBlockState(pos).isOf(Blocks.MOVING_PISTON) ? 'M' : '-');
                    state.append(client.world.getBlockEntity(pos) instanceof PistonBlockEntity ? 'P' : '-');
                    state.append('/').append(BlockEntityRenderAudit.snapshot(pos).seenFrames()).append(',');
                }
            }
        }
        String signature = phase + "|" + cameraFramed + "|" + state;
        long phaseFrames = worldRenderEndCalls - phaseRenderStart;
        if (!force && signature.equals(lastDiagnostic) && phaseFrames % 4L != 0L) return;
        lastDiagnostic = signature;
        System.out.println("[PISTON_WINDOW] event=" + event + " tick=" + ticks
                + " phase=" + phase + " phaseAge=" + (ticks - phaseTick)
                + " elapsedMs=" + ((System.nanoTime() - runStartNanos) / 1_000_000L)
                + " worldEnds=" + worldRenderEndCalls + " phaseWorldEnds=" + phaseFrames
                + " cameraFrame=" + renderedCameraFrame + " framed=" + cameraFramed
                + " minDot=" + cameraMinimumDot + " " + state);
    }

    private static void clearRig(ServerWorld world, Rig rig) {
        for (int along = -1; along <= 3; along++) {
            BlockPos pos = rig.base().offset(rig.direction(), along);
            if (!world.getBlockState(pos).isAir()) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        world.setBlockState(rig.power(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    private static boolean ready(MinecraftClient client) {
        return client != null && client.world != null && client.player != null && client.getServer() != null;
    }

    private static boolean same(double left, double right) {
        return Double.doubleToRawLongBits(left) == Double.doubleToRawLongBits(right);
    }

    private static boolean renderDyComparisonSentinelGreen() {
        return BlockEntityRenderAudit.approximatelySameDy(-2.999999761581421d, DY)
                && !BlockEntityRenderAudit.approximatelySameDy(0.0d, DY)
                && !BlockEntityRenderAudit.approximatelySameDy(-6.0d, DY)
                && !BlockEntityRenderAudit.approximatelySameDy(DY + (1.0d / 16.0d), DY)
                && !BlockEntityRenderAudit.approximatelySameDy(Double.NaN, DY);
    }

    public enum Status {
        PENDING,
        GREEN,
        ERROR
    }

    public record Result(Status status, String detail) {
        public static Result pending(String detail) {
            return new Result(Status.PENDING, detail);
        }

        public static Result green(String detail) {
            return new Result(Status.GREEN, detail);
        }

        public static Result error(String detail) {
            return new Result(Status.ERROR, detail);
        }
    }

    private enum Phase {
        IDLE,
        WAIT_SEED,
        WAIT_EXTENSION,
        WAIT_EXTENSION_SERVER,
        WAIT_RETRACTION,
        WAIT_FINAL_SERVER,
        DONE
    }

    private enum Stage {
        SEEDED,
        EXTENDED,
        RETRACTED
    }

    private record Rig(String id, BlockPos base, Direction direction) {
        private BlockPos payloadStart() {
            return base.offset(direction);
        }

        private BlockPos head() {
            return payloadStart();
        }

        private BlockPos payloadExtended() {
            return base.offset(direction, 2);
        }

        private BlockPos power() {
            return base.west();
        }
    }

    private record RigSnapshot(Rig rig, String mismatch) {
    }

    private record ServerSnapshot(Stage stage, List<RigSnapshot> rigs) {
        private static ServerSnapshot capture(ServerWorld world, Stage stage, List<Rig> rigs) {
            return new ServerSnapshot(stage, rigs.stream()
                    .map(rig -> new RigSnapshot(rig, stageMismatch(world, rig, stage)))
                    .toList());
        }

        private String mismatch(Stage expected) {
            if (stage != expected) {
                return "server snapshot stage=" + stage + " wanted=" + expected;
            }
            return rigs.stream()
                    .filter(snapshot -> snapshot.mismatch() != null)
                    .map(RigSnapshot::mismatch)
                    .findFirst()
                    .orElse(null);
        }
    }

    @FunctionalInterface
    private interface ServerAction {
        void run(ServerWorld world);
    }
}
