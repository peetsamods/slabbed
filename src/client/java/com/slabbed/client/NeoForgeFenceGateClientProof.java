package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;
import java.util.Locale;

/**
 * Default-off client proof for fence/fence-gate dy and triad alignment.
 *
 * <p>Enable with {@code -Dslabbed.neoforge.fenceGateClientProof=true}. It is
 * diagnostics-only and stops the client after writing GREEN/RED rows.
 */
public final class NeoForgeFenceGateClientProof {
    private static final String ENABLE_PROPERTY = "slabbed.neoforge.fenceGateClientProof";
    private static final String ROUTE = "runClient";
    private static final double EXPECTED_DY = -0.5d;
    private static final double EPSILON = 1.0e-6d;
    private static final int FRESH_WORLD_DELAY_TICKS = 1_200;
    private static final int CLIENT_SYNC_DELAY_TICKS = 60;
    private static final int RENDER_SAMPLE_TIMEOUT_TICKS = 120;
    private static final int MAX_TICKS = 2_400;

    private static boolean initialized;
    private static boolean startLogged;
    private static boolean freshWorldRequested;
    private static boolean fixtureQueued;
    private static boolean terminalLogged;
    private static int ticks;
    private static int fixtureTick;
    private static volatile ServerRow[] serverRows;
    private static volatile Throwable serverFailure;
    private static String worldSource = "unknown";
    private static boolean renderSampleRequested;
    private static int renderSampleRequestedTick;
    private static BlockPos renderSamplePos;
    private static RenderRow[] renderRows;
    private static int renderSampleIndex;

    private NeoForgeFenceGateClientProof() {
    }

    public static void init(IEventBus eventBus) {
        if (initialized || !Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        initialized = true;
        Slabbed.LOGGER.info(
                "[NEOFORGE_FENCE_GATE_CLIENT_PROOF_START] route={} enabled=true diagnosticsOnly=true semanticsChanged=false",
                ROUTE);
        eventBus.addListener(NeoForgeFenceGateClientProof::onClientTickPost);
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        if (terminalLogged) {
            return;
        }
        ticks++;
        Minecraft client = Minecraft.getInstance();
        if (!startLogged) {
            startLogged = true;
            Slabbed.LOGGER.info(
                    "[NEOFORGE_FENCE_GATE_CLIENT_PROOF_WAIT] route={} tick={} screen={} worldReady={} playerReady={} diagnosticsOnly=true",
                    ROUTE,
                    ticks,
                    screenName(client.screen),
                    client.level != null,
                    client.player != null);
        }
        if (serverFailure != null) {
            logSummaryAndStop(client, "RED", "server_fixture_exception_" + serverFailure.getClass().getSimpleName(), 0, 1);
            return;
        }
        if (ticks > MAX_TICKS) {
            logSummaryAndStop(client, "RED", "timeout_waiting_for_client_world_or_sync", 0, 1);
            return;
        }
        if (client.level == null || client.player == null || !client.hasSingleplayerServer()) {
            maybeCreateFreshWorld(client);
            return;
        }
        if (!fixtureQueued) {
            queueServerFixture(client);
            return;
        }
        ServerRow[] measuredRows = serverRows;
        if (measuredRows == null || ticks - fixtureTick < CLIENT_SYNC_DELAY_TICKS) {
            return;
        }

        ClientRow[] clientRows = new ClientRow[measuredRows.length];
        for (int i = 0; i < measuredRows.length; i++) {
            clientRows[i] = measureClientRow(client, measuredRows[i]);
        }

        RenderRow[] rowRenders = measureAllOrRequestRender(client, measuredRows, clientRows);
        if (rowRenders == null) {
            return;
        }

        int green = 0;
        int red = 0;
        String firstRed = "none";
        for (int i = 0; i < measuredRows.length; i++) {
            ServerRow row = measuredRows[i];
            ClientRow clientRow = clientRows[i];
            RenderRow rowRender = rowRenders[i];
            boolean rowGreen = clientRow.green() && rowRender.greenOrNotMeasured();
            if (rowGreen) {
                green++;
            } else {
                red++;
                if ("none".equals(firstRed)) {
                    firstRed = clientRow.green() ? rowRender.reason() : clientRow.reason();
                }
            }
            logRow(row, clientRow, rowRender, rowGreen);
        }
        logSummaryAndStop(client, red == 0 ? "GREEN" : "RED", red == 0 ? "fence_gate_client_triad_green" : firstRed, green, red);
    }

    private static RenderRow[] measureAllOrRequestRender(Minecraft client, ServerRow[] rows, ClientRow[] clientRows) {
        if (renderRows == null || renderRows.length != rows.length) {
            renderRows = new RenderRow[rows.length];
            renderSampleIndex = 0;
            renderSampleRequested = false;
            renderSamplePos = null;
        }

        while (renderSampleIndex < rows.length) {
            RenderRow cached = renderRows[renderSampleIndex];
            if (cached != null && cached.ready()) {
                renderSampleIndex++;
                renderSampleRequested = false;
                renderSamplePos = null;
                continue;
            }

            RenderRow measured = measureOrRequestRender(client, rows[renderSampleIndex], clientRows[renderSampleIndex]);
            if (!measured.ready()) {
                return null;
            }
            renderRows[renderSampleIndex] = measured;
            renderSampleIndex++;
            renderSampleRequested = false;
            renderSamplePos = null;
        }
        return renderRows;
    }

    private static void maybeCreateFreshWorld(Minecraft client) {
        if (freshWorldRequested || ticks < FRESH_WORLD_DELAY_TICKS || !client.isGameLoadFinished()) {
            return;
        }
        freshWorldRequested = true;
        worldSource = "fresh";
        String worldId = "slabbed-neoforge-fence-gate-proof-" + Long.toHexString(System.currentTimeMillis());
        LevelSettings settings = new LevelSettings(
                "Slabbed NeoForge Fence Gate Proof",
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(0L, false, false);
        Slabbed.LOGGER.info(
                "[NEOFORGE_FENCE_GATE_CLIENT_PROOF_WORLD_START] route={} worldId={} screen={} diagnosticsOnly=true",
                ROUTE,
                worldId,
                screenName(client.screen));
        try {
            Screen lastScreen = client.screen;
            client.createWorldOpenFlows().createFreshLevel(
                    worldId,
                    settings,
                    options,
                    NeoForgeFenceGateClientProof::flatDimensions,
                    lastScreen);
        } catch (RuntimeException e) {
            serverFailure = e;
        }
    }

    private static WorldDimensions flatDimensions(RegistryAccess registryAccess) {
        return registryAccess.registryOrThrow(Registries.WORLD_PRESET)
                .getHolderOrThrow(WorldPresets.FLAT)
                .value()
                .createWorldDimensions();
    }

    private static void queueServerFixture(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        fixtureQueued = true;
        if (!freshWorldRequested) {
            worldSource = "existing_integrated";
        }
        server.executeIfPossible(() -> {
            try {
                ServerLevel world = server.overworld();
                BlockPos base = chooseBasePos(world, server);
                clearProofVolume(world, base);
                serverRows = new ServerRow[] {
                        authorRow(world, base, "oak_fence", Blocks.OAK_FENCE),
                        authorRow(world, base.east(2), "oak_fence_gate", Blocks.OAK_FENCE_GATE),
                        authorRow(world, base.east(4), "spruce_fence", Blocks.SPRUCE_FENCE),
                        authorRow(world, base.east(6), "spruce_fence_gate", Blocks.SPRUCE_FENCE_GATE)
                };
                fixtureTick = ticks;
            } catch (RuntimeException e) {
                serverFailure = e;
            }
        });
    }

    private static ServerRow authorRow(ServerLevel world, BlockPos supportPos, String name, Block block) {
        BlockPos objectPos = supportPos.above();
        BlockState support = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState object = block.defaultBlockState();
        world.setBlock(supportPos, support, Block.UPDATE_ALL);
        world.setBlock(objectPos, object, Block.UPDATE_ALL);
        object.getBlock().setPlacedBy(world, objectPos, object, null, ItemStack.EMPTY);
        BlockState measuredObject = world.getBlockState(objectPos);
        double serverDy = SlabSupport.getYOffset(world, objectPos, measuredObject);
        boolean green = measuredObject.is(block) && dyMatches(serverDy);
        return new ServerRow(name, supportPos, objectPos, block, world.getBlockState(supportPos), measuredObject, serverDy, green);
    }

    private static BlockPos chooseBasePos(ServerLevel world, MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        BlockPos preferred = players.isEmpty()
                ? new BlockPos(4, world.getMinBuildHeight() + 8, 4)
                : players.get(0).blockPosition().offset(4, 0, 4);
        for (int up = 0; up < 8; up++) {
            BlockPos candidate = preferred.above(up);
            boolean clear = true;
            for (int x = 0; x <= 7; x++) {
                clear &= world.getBlockState(candidate.offset(x, 0, 0)).isAir();
                clear &= world.getBlockState(candidate.offset(x, 1, 0)).isAir();
            }
            if (clear) {
                return candidate.immutable();
            }
        }
        return preferred.above(4).immutable();
    }

    private static void clearProofVolume(ServerLevel world, BlockPos base) {
        for (int dx = -1; dx <= 8; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    world.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static ClientRow measureClientRow(Minecraft client, ServerRow row) {
        BlockState supportState = client.level.getBlockState(row.supportPos());
        BlockState objectState = client.level.getBlockState(row.objectPos());
        boolean supportSynced = supportState.is(Blocks.OAK_SLAB)
                && supportState.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
        boolean objectSynced = objectState.is(row.block());
        double clientDy = ClientDy.dyFor(client.level, row.objectPos(), objectState);
        VoxelShape outline = objectState.getShape(client.level, row.objectPos(), CollisionContext.empty());
        AABB outlineBox = outline.isEmpty() ? null : outline.bounds();
        double outlineMinY = outlineBox == null ? Double.NaN : outlineBox.minY;
        Vec3 visualCenter = Vec3.atLowerCornerOf(row.objectPos()).add(0.5d, clientDy + 0.5d, 0.5d);
        Vec3 rayStart = visualCenter.add(0.0d, 0.0d, 3.0d);
        Vec3 rayEnd = visualCenter.add(0.0d, 0.0d, -3.0d);
        BlockHitResult finalHit = SlabbedOffsetRaycast.raycast(client.level, rayStart, rayEnd, CollisionContext.empty());
        String finalOwner = ownerOf(finalHit);
        double targetDy = "MISS".equals(finalOwner)
                ? Double.NaN
                : ClientDy.dyFor(client.level, finalHit.getBlockPos(), client.level.getBlockState(finalHit.getBlockPos()));
        boolean green = row.serverGreen()
                && supportSynced
                && objectSynced
                && dyMatches(clientDy)
                && dyMatches(outlineMinY)
                && dyMatches(targetDy)
                && formatPos(row.objectPos()).equals(finalOwner);
        String reason;
        if (!row.serverGreen()) {
            reason = "server_dy_not_lowered";
        } else if (!supportSynced) {
            reason = "client_support_not_synced";
        } else if (!objectSynced) {
            reason = "client_object_not_synced";
        } else if (!dyMatches(clientDy)) {
            reason = "client_dy_not_lowered";
        } else if (!dyMatches(outlineMinY)) {
            reason = "outline_not_lowered";
        } else if (!dyMatches(targetDy)) {
            reason = "target_dy_not_lowered";
        } else if (!formatPos(row.objectPos()).equals(finalOwner)) {
            reason = "raycast_owner_mismatch";
        } else {
            reason = "green";
        }
        return new ClientRow(
                supportState,
                objectState,
                supportSynced,
                objectSynced,
                clientDy,
                outlineMinY,
                targetDy,
                finalOwner,
                finalHit == null ? "null" : finalHit.getType().name(),
                finalHit == null ? "none" : finalHit.getDirection().name(),
                finalHit == null ? "none" : formatVec(finalHit.getLocation()),
                outlineBox == null ? "empty" : formatBox(outlineBox),
                green,
                reason);
    }

    private static RenderRow measureOrRequestRender(Minecraft client, ServerRow row, ClientRow clientRow) {
        BakedModel proofModel = client.getBlockRenderer().getBlockModel(clientRow.objectState());
        String proofModelClass = proofModel == null ? "null" : proofModel.getClass().getName();
        boolean proofModelWrapped = proofModel instanceof OffsetBlockStateModel;
        if (!proofModelWrapped) {
            return RenderRow.ready(false, "render_model_not_wrapped", proofModelClass, false, OffsetBlockStateModel.FullMeshBoundsSample.missing());
        }

        if (!renderSampleRequested || renderSamplePos == null || !renderSamplePos.equals(row.objectPos())) {
            renderSampleRequested = true;
            renderSampleRequestedTick = ticks;
            renderSamplePos = row.objectPos().immutable();
            OffsetBlockStateModel.resetFullMeshBoundsSample(row.objectPos());
            markRenderDirty(client, row.objectPos());
            return RenderRow.waiting("render_sample_requested", proofModelClass, true);
        }

        OffsetBlockStateModel.FullMeshBoundsSample sample = OffsetBlockStateModel.snapshotFullMeshBoundsSample();
        if (!sample.seen() && ticks - renderSampleRequestedTick < RENDER_SAMPLE_TIMEOUT_TICKS) {
            if ((ticks - renderSampleRequestedTick) % 20 == 0) {
                markRenderDirty(client, row.objectPos());
            }
            return RenderRow.waiting("waiting_for_fence_render_sample", proofModelClass, true);
        }

        boolean green = sample.seen()
                && dyMatches(sample.dy())
                && sample.verticesVisited() > 0
                && renderBoundsShiftedByExpectedDy(sample);
        String reason;
        if (green) {
            reason = "fence_model_bounds_green";
        } else if (!sample.seen()) {
            reason = "fence_render_sample_missing";
        } else if (!dyMatches(sample.dy())) {
            reason = "fence_render_dy_not_lowered";
        } else if (sample.verticesVisited() <= 0) {
            reason = "fence_render_vertices_missing";
        } else if (!renderBoundsShiftedByExpectedDy(sample)) {
            reason = "fence_model_bounds_not_shifted_by_expected_dy";
        } else {
            reason = "fence_model_unknown_mismatch";
        }
        return RenderRow.ready(green, reason, proofModelClass, true, sample);
    }

    private static boolean renderBoundsShiftedByExpectedDy(OffsetBlockStateModel.FullMeshBoundsSample sample) {
        return Double.isFinite(sample.minBeforeY())
                && Double.isFinite(sample.maxBeforeY())
                && Double.isFinite(sample.minAfterY())
                && Double.isFinite(sample.maxAfterY())
                && Math.abs((sample.minAfterY() - sample.minBeforeY()) - EXPECTED_DY) <= EPSILON
                && Math.abs((sample.maxAfterY() - sample.maxBeforeY()) - EXPECTED_DY) <= EPSILON;
    }

    private static void markRenderDirty(Minecraft client, BlockPos objectPos) {
        if (client.player != null) {
            client.player.setPos(objectPos.getX() + 0.5d, objectPos.getY() + 0.5d, objectPos.getZ() + 4.0d);
            client.player.setYRot(180.0f);
            client.player.setXRot(0.0f);
        }
        if (client.levelRenderer == null) {
            return;
        }
        client.levelRenderer.setBlocksDirty(
                objectPos.getX() - 1,
                objectPos.getY() - 1,
                objectPos.getZ() - 1,
                objectPos.getX() + 1,
                objectPos.getY() + 2,
                objectPos.getZ() + 1);
    }

    private static void logRow(ServerRow row, ClientRow client, RenderRow render, boolean rowGreen) {
        OffsetBlockStateModel.FullMeshBoundsSample sample = render.sample();
        Slabbed.LOGGER.info(
                "[NEOFORGE_FENCE_GATE_CLIENT_TRIAD_ROW] route={} row={} supportPos={} objectPos={} supportState={} objectState={} serverDy={} clientDy={} outlineMinY={} targetDy={} finalOwner={} finalHitType={} finalFace={} finalHitVec={} outlineBox={} supportSynced={} objectSynced={} renderMeasured={} renderModelClass={} renderModelWrapped={} renderSampleSeen={} renderDy={} renderMinAfterY={} renderMaxAfterY={} renderVertices={} renderReason={} result={} reason={} worldSource={} diagnosticsOnly=true semanticsChanged=false releaseReady=false",
                ROUTE,
                row.name(),
                formatPos(row.supportPos()),
                formatPos(row.objectPos()),
                client.supportState(),
                client.objectState(),
                formatDouble(row.serverDy()),
                formatDouble(client.clientDy()),
                formatDouble(client.outlineMinY()),
                formatDouble(client.targetDy()),
                client.finalOwner(),
                client.finalHitType(),
                client.finalFace(),
                client.finalHitVec(),
                client.outlineBox(),
                client.supportSynced(),
                client.objectSynced(),
                render.measured(),
                render.proofModelClass(),
                render.proofModelWrapped(),
                sample.seen(),
                formatDouble(sample.dy()),
                formatDouble(sample.minAfterY()),
                formatDouble(sample.maxAfterY()),
                sample.verticesVisited(),
                render.reason(),
                rowGreen ? "GREEN" : "RED",
                rowGreen ? "green" : (client.green() ? render.reason() : client.reason()),
                worldSource);
    }

    private static void logSummaryAndStop(Minecraft client, String result, String reason, int green, int red) {
        if (terminalLogged) {
            return;
        }
        terminalLogged = true;
        Slabbed.LOGGER.info(
                "[NEOFORGE_FENCE_GATE_CLIENT_TRIAD_SUMMARY] route={} result={} reason={} rows={} green={} red={} worldSource={} diagnosticsOnly=true semanticsChanged=false releaseReady=false",
                ROUTE,
                result,
                reason,
                green + red,
                green,
                red,
                worldSource);
        client.stop();
    }

    private static boolean dyMatches(double dy) {
        return Math.abs(dy - EXPECTED_DY) <= EPSILON;
    }

    private static String screenName(Screen screen) {
        return screen == null ? "none" : screen.getClass().getName();
    }

    private static String ownerOf(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            return "MISS";
        }
        return formatPos(blockHit.getBlockPos());
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String formatDouble(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.6f", value) : "NaN";
    }

    private static String formatBox(AABB box) {
        return String.format(
                Locale.ROOT,
                "[%.3f,%.3f,%.3f -> %.3f,%.3f,%.3f]",
                box.minX,
                box.minY,
                box.minZ,
                box.maxX,
                box.maxY,
                box.maxZ);
    }

    private static String formatVec(Vec3 vec) {
        return String.format(Locale.ROOT, "%.3f,%.3f,%.3f", vec.x, vec.y, vec.z);
    }

    private record ServerRow(
            String name,
            BlockPos supportPos,
            BlockPos objectPos,
            Block block,
            BlockState supportState,
            BlockState objectState,
            double serverDy,
            boolean serverGreen
    ) {
    }

    private record ClientRow(
            BlockState supportState,
            BlockState objectState,
            boolean supportSynced,
            boolean objectSynced,
            double clientDy,
            double outlineMinY,
            double targetDy,
            String finalOwner,
            String finalHitType,
            String finalFace,
            String finalHitVec,
            String outlineBox,
            boolean green,
            String reason
    ) {
    }

    private record RenderRow(
            boolean ready,
            boolean measured,
            boolean green,
            String reason,
            String proofModelClass,
            boolean proofModelWrapped,
            OffsetBlockStateModel.FullMeshBoundsSample sample
    ) {
        static RenderRow waiting(String reason, String proofModelClass, boolean proofModelWrapped) {
            return new RenderRow(false, true, false, reason, proofModelClass, proofModelWrapped, OffsetBlockStateModel.FullMeshBoundsSample.missing());
        }

        static RenderRow ready(
                boolean green,
                String reason,
                String proofModelClass,
                boolean proofModelWrapped,
                OffsetBlockStateModel.FullMeshBoundsSample sample
        ) {
            return new RenderRow(true, true, green, reason, proofModelClass, proofModelWrapped, sample);
        }

        static RenderRow notMeasured() {
            return new RenderRow(true, false, true, "not_measured", "n/a", false, OffsetBlockStateModel.FullMeshBoundsSample.missing());
        }

        boolean greenOrNotMeasured() {
            return !measured || green;
        }
    }
}
