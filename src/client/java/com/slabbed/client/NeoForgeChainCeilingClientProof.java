package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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

/**
 * Default-off proof for the 26.2 chain-ceiling alternate geometry port.
 */
public final class NeoForgeChainCeilingClientProof {
    private static final String ENABLE_PROPERTY = "slabbed.neoforge.chainCeilingClientProof";
    private static final String ROUTE = "runClient";
    private static final int FRESH_WORLD_DELAY_TICKS = 1_200;
    private static final int CLIENT_SYNC_DELAY_TICKS = 60;
    private static final int RENDER_SAMPLE_TIMEOUT_TICKS = 180;
    private static final int MAX_TICKS = 2_500;
    private static final double EPSILON = 1.0e-6d;

    private static boolean initialized;
    private static boolean startLogged;
    private static boolean freshWorldRequested;
    private static boolean fixtureRequestQueued;
    private static boolean renderSampleRequested;
    private static boolean terminalLogged;
    private static int ticks;
    private static int fixtureAuthoredTick;
    private static int renderSampleRequestedTick;
    private static String worldSource = "unknown";
    private static volatile ServerMeasurement serverMeasurement;
    private static volatile Throwable serverFailure;

    private NeoForgeChainCeilingClientProof() {
    }

    public static void init(IEventBus eventBus) {
        if (initialized || !Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        initialized = true;
        Slabbed.LOGGER.info(
                "[NEOFORGE_CHAIN_CEILING_CLIENT_PROOF_START] enabled=true route={} diagnosticsOnly=true semanticsChanged=false",
                ROUTE);
        eventBus.addListener(NeoForgeChainCeilingClientProof::onClientTickPost);
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
                    "[NEOFORGE_CHAIN_CEILING_CLIENT_PROOF_WAIT] route={} tick={} screen={} worldReady={} playerReady={} diagnosticsOnly=true semanticsChanged=false",
                    ROUTE,
                    ticks,
                    screenName(client.screen),
                    client.level != null,
                    client.player != null);
        }

        if (serverFailure != null) {
            logSummaryAndStop(client, "RED", "server_fixture_exception_" + serverFailure.getClass().getSimpleName());
            return;
        }
        if (ticks > MAX_TICKS) {
            logSummaryAndStop(client, "RED", "timeout_waiting_for_chain_ceiling_client_proof");
            return;
        }
        if (client.level == null || client.player == null || !client.hasSingleplayerServer()) {
            maybeCreateFreshWorld(client);
            return;
        }
        if (!fixtureRequestQueued) {
            queueServerFixture(client);
            return;
        }

        ServerMeasurement measured = serverMeasurement;
        if (measured == null || ticks - fixtureAuthoredTick < CLIENT_SYNC_DELAY_TICKS) {
            return;
        }

        ClientMeasurement clientMeasurement = measureClient(client, measured);
        RenderMeasurement renderMeasurement = measureOrRequestRender(client, measured, clientMeasurement);
        if (!renderMeasurement.ready()) {
            return;
        }

        boolean green = measured.green()
                && clientMeasurement.green()
                && renderMeasurement.green();
        String result = green ? "GREEN" : "RED";
        String reason = green ? "chain_ceiling_model_outline_raycast_green" : mismatchReason(measured, clientMeasurement, renderMeasurement);
        logRows(measured, clientMeasurement, renderMeasurement, result, reason);
        logSummaryAndStop(client, result, reason);
    }

    private static void maybeCreateFreshWorld(Minecraft client) {
        if (freshWorldRequested || ticks < FRESH_WORLD_DELAY_TICKS || !client.isGameLoadFinished()) {
            return;
        }
        freshWorldRequested = true;
        worldSource = "fresh";
        String worldId = "slabbed-neoforge-chain-ceiling-proof-" + Long.toHexString(System.currentTimeMillis());
        LevelSettings settings = new LevelSettings(
                "Slabbed NeoForge Chain Ceiling Proof",
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(0L, false, false);
        Slabbed.LOGGER.info(
                "[NEOFORGE_CHAIN_CEILING_CLIENT_PROOF_WORLD_START] route={} worldId={} screen={} diagnosticsOnly=true semanticsChanged=false",
                ROUTE,
                worldId,
                screenName(client.screen));
        try {
            Screen lastScreen = client.screen;
            client.createWorldOpenFlows().createFreshLevel(
                    worldId,
                    settings,
                    options,
                    NeoForgeChainCeilingClientProof::flatDimensions,
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
        fixtureRequestQueued = true;
        if (!freshWorldRequested) {
            worldSource = "existing_integrated";
        }
        server.executeIfPossible(() -> {
            try {
                ServerLevel world = server.overworld();
                BlockPos chainPos = chooseChainPos(world, server);
                BlockPos ceilingPos = chainPos.above();
                clearProofVolume(world, chainPos);

                BlockState ceilingState = Blocks.STONE_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.TOP);
                BlockState chainState = Blocks.CHAIN.defaultBlockState()
                        .setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
                world.setBlock(ceilingPos, ceilingState, Block.UPDATE_ALL);
                world.setBlock(chainPos, chainState, Block.UPDATE_ALL);

                BlockState measuredCeiling = world.getBlockState(ceilingPos);
                BlockState measuredChain = world.getBlockState(chainPos);
                double serverDy = SlabSupport.getYOffset(world, chainPos, measuredChain);
                VoxelShape outline = measuredChain.getShape(world, chainPos, CollisionContext.empty());
                AABB outlineBox = outline.isEmpty() ? null : outline.bounds();
                boolean directBridge = SlabSupport.isVerticalChainDirectlyUnderCeilingSupport(world, chainPos, measuredChain);
                boolean columnMember = SlabSupport.isCeilingBridgedVerticalChainColumnMember(world, chainPos, measuredChain);
                boolean green = measuredCeiling.is(Blocks.STONE_SLAB)
                        && measuredCeiling.getValue(SlabBlock.TYPE) == SlabType.TOP
                        && measuredChain.is(Blocks.CHAIN)
                        && directBridge
                        && columnMember
                        && dyHalf(serverDy)
                        && outlineSpansBridge(outlineBox);
                serverMeasurement = new ServerMeasurement(
                        chainPos,
                        ceilingPos,
                        measuredCeiling,
                        measuredChain,
                        serverDy,
                        formatBox(outlineBox),
                        directBridge,
                        columnMember,
                        green);
                fixtureAuthoredTick = ticks;
            } catch (RuntimeException e) {
                serverFailure = e;
            }
        });
    }

    private static ClientMeasurement measureClient(Minecraft client, ServerMeasurement measured) {
        BlockState clientCeiling = client.level.getBlockState(measured.ceilingPos());
        BlockState clientChain = client.level.getBlockState(measured.chainPos());
        double clientDy = ClientDy.dyFor(client.level, measured.chainPos(), clientChain);
        VoxelShape outline = clientChain.getShape(client.level, measured.chainPos(), CollisionContext.empty());
        AABB outlineBox = outline.isEmpty() ? null : outline.bounds();
        Vec3 bridgeProbe = Vec3.atLowerCornerOf(measured.chainPos()).add(0.5d, 1.25d, 0.5d);
        Vec3 rayStart = bridgeProbe.add(0.0d, 0.0d, 3.0d);
        Vec3 rayEnd = bridgeProbe.add(0.0d, 0.0d, -3.0d);
        BlockHitResult outlineHit = outline.clip(rayStart, rayEnd, measured.chainPos());
        BlockHitResult finalHit = SlabbedOffsetRaycast.raycast(client.level, rayStart, rayEnd, CollisionContext.empty());
        String outlineOwner = ownerOf(outlineHit);
        String finalOwner = ownerOf(finalHit);
        boolean directBridge = SlabSupport.isVerticalChainDirectlyUnderCeilingSupport(client.level, measured.chainPos(), clientChain);
        boolean columnMember = SlabSupport.isCeilingBridgedVerticalChainColumnMember(client.level, measured.chainPos(), clientChain);
        boolean green = clientCeiling.is(Blocks.STONE_SLAB)
                && clientCeiling.getValue(SlabBlock.TYPE) == SlabType.TOP
                && clientChain.is(Blocks.CHAIN)
                && directBridge
                && columnMember
                && dyHalf(clientDy)
                && outlineSpansBridge(outlineBox)
                && formatPos(measured.chainPos()).equals(outlineOwner)
                && formatPos(measured.chainPos()).equals(finalOwner);
        return new ClientMeasurement(
                clientCeiling,
                clientChain,
                clientDy,
                formatBox(outlineBox),
                outlineOwner,
                finalOwner,
                finalHit == null ? "null" : finalHit.getType().name(),
                finalHit == null ? "none" : finalHit.getDirection().name(),
                finalHit == null ? "none" : formatVec(finalHit.getLocation()),
                directBridge,
                columnMember,
                green);
    }

    private static RenderMeasurement measureOrRequestRender(Minecraft client, ServerMeasurement measured, ClientMeasurement clientMeasurement) {
        BakedModel proofModel = client.getBlockRenderer().getBlockModel(clientMeasurement.chainState());
        String proofModelClass = proofModel == null ? "null" : proofModel.getClass().getName();
        boolean proofModelWrapped = proofModel instanceof OffsetBlockStateModel;
        if (!proofModelWrapped) {
            return RenderMeasurement.ready(false, "render_model_not_wrapped", proofModelClass, false, OffsetBlockStateModel.FullMeshBoundsSample.missing());
        }

        if (!renderSampleRequested) {
            renderSampleRequested = true;
            renderSampleRequestedTick = ticks;
            OffsetBlockStateModel.resetFullMeshBoundsSample(measured.chainPos());
            markRenderDirty(client, measured.chainPos());
            return RenderMeasurement.waiting("render_sample_requested", proofModelClass, true);
        }

        OffsetBlockStateModel.FullMeshBoundsSample sample = OffsetBlockStateModel.snapshotFullMeshBoundsSample();
        if (!sample.seen() && ticks - renderSampleRequestedTick < RENDER_SAMPLE_TIMEOUT_TICKS) {
            if ((ticks - renderSampleRequestedTick) % 20 == 0) {
                markRenderDirty(client, measured.chainPos());
            }
            return RenderMeasurement.waiting("waiting_for_chain_ceiling_render_sample", proofModelClass, true);
        }

        boolean green = sample.seen()
                && dyZero(sample.dy())
                && sample.verticesVisited() > 0
                && finiteAtOrBelow(sample.minAfterY(), 0.0d)
                && finiteAtOrAbove(sample.maxAfterY(), 1.5d)
                && "chain_ceiling_alternate_geometry".equals(sample.reason());
        String reason;
        if (green) {
            reason = "chain_ceiling_render_bounds_green";
        } else if (!sample.seen()) {
            reason = "chain_ceiling_render_sample_missing";
        } else if (!dyZero(sample.dy())) {
            reason = "chain_ceiling_render_dy_not_zero";
        } else if (sample.maxAfterY() < 1.5d - EPSILON) {
            reason = "chain_ceiling_render_not_extended_to_y_1_5";
        } else {
            reason = "chain_ceiling_render_unknown_mismatch";
        }
        return RenderMeasurement.ready(green, reason, proofModelClass, true, sample);
    }

    private static BlockPos chooseChainPos(ServerLevel world, MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        BlockPos preferred = players.isEmpty()
                ? new BlockPos(4, world.getMinBuildHeight() + 8, 4)
                : players.get(0).blockPosition().offset(4, 0, 4);
        for (int up = 0; up < 8; up++) {
            BlockPos candidate = preferred.above(up);
            if (world.getBlockState(candidate).isAir() && world.getBlockState(candidate.above()).isAir()) {
                return candidate.immutable();
            }
        }
        return preferred.above(4).immutable();
    }

    private static void clearProofVolume(ServerLevel world, BlockPos chainPos) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int y = -1; y <= 3; y++) {
                    world.setBlock(chainPos.offset(dx, y, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void markRenderDirty(Minecraft client, BlockPos chainPos) {
        if (client.player != null) {
            client.player.setPos(chainPos.getX() + 0.5d, chainPos.getY() + 1.25d, chainPos.getZ() + 4.0d);
            client.player.setYRot(180.0f);
            client.player.setXRot(0.0f);
        }
        if (client.levelRenderer == null) {
            return;
        }
        client.levelRenderer.setBlocksDirty(
                chainPos.getX() - 1,
                chainPos.getY() - 1,
                chainPos.getZ() - 1,
                chainPos.getX() + 1,
                chainPos.getY() + 2,
                chainPos.getZ() + 1);
    }

    private static void logRows(
            ServerMeasurement measured,
            ClientMeasurement clientMeasurement,
            RenderMeasurement renderMeasurement,
            String result,
            String reason
    ) {
        OffsetBlockStateModel.FullMeshBoundsSample sample = renderMeasurement.sample();
        Slabbed.LOGGER.info(
                "[NEOFORGE_CHAIN_CEILING_CLIENT_PROOF_ROW] route={} result={} reason={} worldSource={} chainPos={} ceilingPos={} ceilingState={} chainState={} serverDy={} clientDy={} serverBridge={} clientBridge={} serverColumn={} clientColumn={} serverOutlineBox={} clientOutlineBox={} outlineOwner={} finalOwner={} finalHitType={} finalFace={} finalHitVec={} renderModelClass={} modelWrapped={} renderSampleSeen={} renderDy={} minAfterY={} maxAfterY={} verticesVisited={} renderReason={} diagnosticsOnly=true semanticsChanged=false releaseReady=false",
                ROUTE,
                result,
                reason,
                worldSource,
                formatPos(measured.chainPos()),
                formatPos(measured.ceilingPos()),
                clientMeasurement.ceilingState(),
                clientMeasurement.chainState(),
                measured.serverDy(),
                clientMeasurement.clientDy(),
                measured.directBridge(),
                clientMeasurement.directBridge(),
                measured.columnMember(),
                clientMeasurement.columnMember(),
                measured.outlineBox(),
                clientMeasurement.outlineBox(),
                clientMeasurement.outlineOwner(),
                clientMeasurement.finalOwner(),
                clientMeasurement.finalHitType(),
                clientMeasurement.finalFace(),
                clientMeasurement.finalHitVec(),
                renderMeasurement.proofModelClass(),
                renderMeasurement.proofModelWrapped(),
                sample.seen(),
                sample.dy(),
                sample.minAfterY(),
                sample.maxAfterY(),
                sample.verticesVisited(),
                sample.reason());
    }

    private static String mismatchReason(ServerMeasurement measured, ClientMeasurement client, RenderMeasurement render) {
        if (!measured.green()) {
            return "server_chain_ceiling_bridge_not_green";
        }
        if (!client.green()) {
            return "client_chain_ceiling_outline_or_raycast_not_green";
        }
        if (!render.green()) {
            return render.reason();
        }
        return "unknown_chain_ceiling_mismatch";
    }

    private static void logSummaryAndStop(Minecraft client, String result, String reason) {
        if (terminalLogged) {
            return;
        }
        terminalLogged = true;
        int greenRows = "GREEN".equals(result) ? 1 : 0;
        int redRows = "GREEN".equals(result) ? 0 : 1;
        Slabbed.LOGGER.info(
                "[NEOFORGE_CHAIN_CEILING_CLIENT_PROOF_SUMMARY] route={} result={} reason={} rows=1 green={} red={} worldSource={} diagnosticsOnly=true semanticsChanged=false releaseReady=false",
                ROUTE,
                result,
                reason,
                greenRows,
                redRows,
                worldSource);
        client.stop();
    }

    private static boolean outlineSpansBridge(AABB box) {
        return box != null
                && finiteAtOrBelow(box.minY, 0.0d)
                && finiteAtOrAbove(box.maxY, 1.5d);
    }

    private static boolean finiteAtOrBelow(double value, double expected) {
        return Double.isFinite(value) && value <= expected + EPSILON;
    }

    private static boolean finiteAtOrAbove(double value, double expected) {
        return Double.isFinite(value) && value >= expected - EPSILON;
    }

    private static boolean dyZero(double dy) {
        return Math.abs(dy) <= EPSILON;
    }

    private static boolean dyHalf(double dy) {
        return Math.abs(dy - 0.5d) <= EPSILON;
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

    private static String formatBox(AABB box) {
        if (box == null) {
            return "empty";
        }
        return String.format(
                java.util.Locale.ROOT,
                "[%.3f,%.3f,%.3f -> %.3f,%.3f,%.3f]",
                box.minX,
                box.minY,
                box.minZ,
                box.maxX,
                box.maxY,
                box.maxZ);
    }

    private static String formatVec(Vec3 vec) {
        return String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f", vec.x, vec.y, vec.z);
    }

    private record ServerMeasurement(
            BlockPos chainPos,
            BlockPos ceilingPos,
            BlockState ceilingState,
            BlockState chainState,
            double serverDy,
            String outlineBox,
            boolean directBridge,
            boolean columnMember,
            boolean green
    ) {
    }

    private record ClientMeasurement(
            BlockState ceilingState,
            BlockState chainState,
            double clientDy,
            String outlineBox,
            String outlineOwner,
            String finalOwner,
            String finalHitType,
            String finalFace,
            String finalHitVec,
            boolean directBridge,
            boolean columnMember,
            boolean green
    ) {
    }

    private record RenderMeasurement(
            boolean ready,
            boolean green,
            String reason,
            String proofModelClass,
            boolean proofModelWrapped,
            OffsetBlockStateModel.FullMeshBoundsSample sample
    ) {
        static RenderMeasurement waiting(String reason, String proofModelClass, boolean proofModelWrapped) {
            return new RenderMeasurement(false, false, reason, proofModelClass, proofModelWrapped, OffsetBlockStateModel.FullMeshBoundsSample.missing());
        }

        static RenderMeasurement ready(
                boolean green,
                String reason,
                String proofModelClass,
                boolean proofModelWrapped,
                OffsetBlockStateModel.FullMeshBoundsSample sample
        ) {
            return new RenderMeasurement(true, green, reason, proofModelClass, proofModelWrapped, sample);
        }
    }
}
