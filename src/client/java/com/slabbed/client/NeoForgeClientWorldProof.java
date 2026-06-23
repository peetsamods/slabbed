package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
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

/**
 * Default-off NeoForge client proof route for a real integrated-world dy check.
 */
public final class NeoForgeClientWorldProof {
    private static final String ENABLE_PROPERTY = "slabbed.neoforge.clientWorldProof";
    private static final String ROUTE = "runClient";
    private static final int FRESH_WORLD_DELAY_TICKS = 1_200;
    private static final int CLIENT_SYNC_DELAY_TICKS = 60;
    private static final int RENDER_CULL_SAMPLE_TIMEOUT_TICKS = 160;
    private static final int MAX_TICKS = 2_400;
    private static final double EXPECTED_DY = -0.5d;
    private static final double EPSILON = 1.0e-6d;

    private static boolean initialized;
    private static boolean startLogged;
    private static boolean freshWorldRequested;
    private static boolean fixtureRequestQueued;
    private static boolean renderCullSampleRequested;
    private static boolean terminalLogged;
    private static int ticks;
    private static int fixtureAuthoredTick;
    private static int renderCullSampleRequestedTick;
    private static String worldSource = "unknown";
    private static volatile ServerMeasurement serverMeasurement;
    private static volatile Throwable serverFailure;

    private NeoForgeClientWorldProof() {
    }

    public static void init(IEventBus eventBus) {
        if (initialized || !Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        initialized = true;
        Slabbed.LOGGER.info(
                "[MC1211_NEOFORGE_CLIENT_WORLD_PROOF_START] enabled=true route={} diagnosticsOnly=true semanticsChanged=false",
                ROUTE);
        eventBus.addListener(NeoForgeClientWorldProof::onClientTickPost);
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
                    "[MC1211_NEOFORGE_CLIENT_WORLD_PROOF_WAIT] route={} tick={} screen={} worldReady={} playerReady={} diagnosticsOnly=true semanticsChanged=false",
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
            logSummaryAndStop(client, "RED", "timeout_waiting_for_client_world_or_sync");
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

        ClientMeasurement clientMeasurement = measureClientState(
                client,
                measured.supportPos(),
                measured.objectPos(),
                measured.flatObjectPos());
        TriadMeasurement triadMeasurement = measureTriad(client, measured.objectPos(), clientMeasurement.objectState());
        boolean triadGreen = measured.green()
                && clientMeasurement.supportSynced()
                && clientMeasurement.objectSynced()
                && clientMeasurement.flatObjectSynced()
                && dyMatches(clientMeasurement.clientDy())
                && dyZero(clientMeasurement.flatClientDy())
                && clientMeasurement.loweredStepFaceEast()
                && clientMeasurement.flatStepFaceWest()
                && triadMeasurement.green();
        if (triadGreen) {
            RenderCullMeasurement renderCullMeasurement = measureOrRequestRenderCull(client, measured, clientMeasurement);
            if (!renderCullMeasurement.ready()) {
                return;
            }
            String result = renderCullMeasurement.green() ? "GREEN" : "RED";
            String reason = renderCullMeasurement.green()
                    ? "client_direct_bottom_slab_dy_triad_and_step_cull_match"
                    : renderCullMeasurement.reason();
            logWorldAndTriadRows(measured, clientMeasurement, triadMeasurement, result, reason);
            logRenderCullSummary(measured, clientMeasurement, renderCullMeasurement, result, reason);
            logSummaryAndStop(client, result, reason);
            return;
        }
        String result = "RED";
        String reason = mismatchReason(measured, clientMeasurement, triadMeasurement);
        logWorldAndTriadRows(measured, clientMeasurement, triadMeasurement, result, reason);
        logSummaryAndStop(client, result, reason);
    }

    private static void logWorldAndTriadRows(
            ServerMeasurement measured,
            ClientMeasurement clientMeasurement,
            TriadMeasurement triadMeasurement,
            String result,
            String reason
    ) {
        Slabbed.LOGGER.info(
                "[MC1211_NEOFORGE_CLIENT_WORLD_PROOF_ROW] route={} scenario=DIRECT_BOTTOM_SLAB_ANCHORED_FULL_BLOCK worldSource={} supportPos={} objectPos={} serverSupport={} serverObject={} clientSupport={} clientObject={} serverAnchor={} clientAnchor={} serverDy={} clientDy={} modelDy={} outlineDy={} targetDy={} outlineBox={} outlineOwner={} finalOwner={} finalHitType={} finalHitVec={} result={} reason={} diagnosticsOnly=true semanticsChanged=false",
                ROUTE,
                worldSource,
                formatPos(measured.supportPos()),
                formatPos(measured.objectPos()),
                measured.supportState(),
                measured.objectState(),
                clientMeasurement.supportState(),
                clientMeasurement.objectState(),
                measured.serverAnchor(),
                clientMeasurement.clientAnchor(),
                measured.serverDy(),
                clientMeasurement.clientDy(),
                triadMeasurement.modelDy(),
                triadMeasurement.outlineDy(),
                triadMeasurement.targetDy(),
                triadMeasurement.outlineBox(),
                triadMeasurement.outlineOwner(),
                triadMeasurement.finalOwner(),
                triadMeasurement.finalHitType(),
                triadMeasurement.finalHitVec(),
                result,
                reason);
        Slabbed.LOGGER.info(
                "[MC1211_NEOFORGE_CLIENT_TRIAD_PROOF_SUMMARY] route={} scenario=DIRECT_BOTTOM_SLAB_ANCHORED_FULL_BLOCK result={} reason={} pos={} state={} supportSource=bottom_slab worldDy={} modelDy={} outlineDy={} targetDy={} outlineOwner={} finalOwner={} heldItem=none face={} hitVec={} diagnosticsOnly=true semanticsChanged=false releaseReady=false",
                ROUTE,
                result,
                reason,
                formatPos(measured.objectPos()),
                clientMeasurement.objectState(),
                clientMeasurement.clientDy(),
                triadMeasurement.modelDy(),
                triadMeasurement.outlineDy(),
                triadMeasurement.targetDy(),
                triadMeasurement.outlineOwner(),
                triadMeasurement.finalOwner(),
                triadMeasurement.finalFace(),
                triadMeasurement.finalHitVec());
    }

    private static void maybeCreateFreshWorld(Minecraft client) {
        if (freshWorldRequested || ticks < FRESH_WORLD_DELAY_TICKS || !client.isGameLoadFinished()) {
            return;
        }
        freshWorldRequested = true;
        worldSource = "fresh";
        String worldId = "slabbed-neoforge-client-world-proof-" + Long.toHexString(System.currentTimeMillis());
        LevelSettings settings = new LevelSettings(
                "Slabbed NeoForge Client World Proof",
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(0L, false, false);
        Slabbed.LOGGER.info(
                "[MC1211_NEOFORGE_CLIENT_WORLD_PROOF_WORLD_START] route={} worldId={} screen={} diagnosticsOnly=true semanticsChanged=false",
                ROUTE,
                worldId,
                screenName(client.screen));
        try {
            Screen lastScreen = client.screen;
            client.createWorldOpenFlows().createFreshLevel(
                    worldId,
                    settings,
                    options,
                    NeoForgeClientWorldProof::flatDimensions,
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
                BlockPos supportPos = chooseSupportPos(world, server);
                BlockPos objectPos = supportPos.above();
                BlockPos flatSupportPos = supportPos.east();
                BlockPos flatObjectPos = objectPos.east();
                clearProofVolume(world, supportPos);

                BlockState supportState = Blocks.STONE_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
                world.setBlock(flatSupportPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                world.setBlock(flatObjectPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                world.setBlock(supportPos, supportState, Block.UPDATE_ALL);
                authorBlock(world, objectPos, Blocks.STONE.defaultBlockState());

                BlockState measuredSupport = world.getBlockState(supportPos);
                BlockState measuredObject = world.getBlockState(objectPos);
                BlockState measuredFlatObject = world.getBlockState(flatObjectPos);
                double serverDy = SlabSupport.getYOffset(world, objectPos, measuredObject);
                double flatServerDy = SlabSupport.getYOffset(world, flatObjectPos, measuredFlatObject);
                boolean serverAnchor = SlabAnchorAttachment.isAnchored(world, objectPos);
                boolean loweredStepFaceEast = SlabSupport.isSlabHeightStepFace(world, objectPos, measuredObject, Direction.EAST);
                boolean flatStepFaceWest = SlabSupport.isSlabHeightStepFace(world, flatObjectPos, measuredFlatObject, Direction.WEST);
                boolean green = measuredSupport.is(Blocks.STONE_SLAB)
                        && measuredSupport.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                        && measuredObject.is(Blocks.STONE)
                        && measuredFlatObject.is(Blocks.STONE)
                        && serverAnchor
                        && dyMatches(serverDy)
                        && dyZero(flatServerDy)
                        && loweredStepFaceEast
                        && flatStepFaceWest;
                serverMeasurement = new ServerMeasurement(
                        supportPos,
                        objectPos,
                        flatObjectPos,
                        measuredSupport,
                        measuredObject,
                        measuredFlatObject,
                        serverAnchor,
                        serverDy,
                        flatServerDy,
                        loweredStepFaceEast,
                        flatStepFaceWest,
                        green);
                fixtureAuthoredTick = ticks;
            } catch (RuntimeException e) {
                serverFailure = e;
            }
        });
    }

    private static BlockPos chooseSupportPos(ServerLevel world, MinecraftServer server) {
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

    private static void clearProofVolume(ServerLevel world, BlockPos supportPos) {
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int y = -1; y <= 3; y++) {
                    world.setBlock(supportPos.offset(dx, y, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void authorBlock(ServerLevel world, BlockPos pos, BlockState state) {
        world.setBlock(pos, state, Block.UPDATE_ALL);
        state.getBlock().setPlacedBy(world, pos, state, null, ItemStack.EMPTY);
    }

    private static ClientMeasurement measureClientState(Minecraft client, BlockPos supportPos, BlockPos objectPos, BlockPos flatObjectPos) {
        BlockState clientSupport = client.level.getBlockState(supportPos);
        BlockState clientObject = client.level.getBlockState(objectPos);
        BlockState clientFlatObject = client.level.getBlockState(flatObjectPos);
        double clientDy = ClientDy.dyFor(client.level, objectPos, clientObject);
        double flatClientDy = ClientDy.dyFor(client.level, flatObjectPos, clientFlatObject);
        boolean clientAnchor = SlabAnchorAttachment.isAnchored(client.level, objectPos);
        boolean supportSynced = clientSupport.is(Blocks.STONE_SLAB)
                && clientSupport.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
        boolean objectSynced = clientObject.is(Blocks.STONE);
        boolean flatObjectSynced = clientFlatObject.is(Blocks.STONE);
        boolean loweredStepFaceEast = SlabSupport.isSlabHeightStepFace(client.level, objectPos, clientObject, Direction.EAST);
        boolean flatStepFaceWest = SlabSupport.isSlabHeightStepFace(client.level, flatObjectPos, clientFlatObject, Direction.WEST);
        return new ClientMeasurement(
                clientSupport,
                clientObject,
                clientFlatObject,
                clientAnchor,
                clientDy,
                flatClientDy,
                supportSynced,
                objectSynced,
                flatObjectSynced,
                loweredStepFaceEast,
                flatStepFaceWest);
    }

    private static TriadMeasurement measureTriad(Minecraft client, BlockPos objectPos, BlockState objectState) {
        if (client.level == null || objectState == null || objectState.isAir()) {
            return TriadMeasurement.missing("client_world_or_object_missing");
        }

        double modelDy = ClientDy.dyFor(client.level, objectPos, objectState);
        VoxelShape outlineShape = objectState.getShape(client.level, objectPos, CollisionContext.empty());
        AABB outlineBox = outlineShape.isEmpty() ? null : outlineShape.bounds();
        double outlineDy = outlineBox == null ? Double.NaN : outlineBox.minY;
        Vec3 visualCenter = Vec3.atLowerCornerOf(objectPos).add(0.5d, modelDy + 0.5d, 0.5d);
        Vec3 rayStart = visualCenter.add(0.0d, 0.0d, 3.0d);
        Vec3 rayEnd = visualCenter.add(0.0d, 0.0d, -3.0d);
        BlockHitResult outlineHit = outlineShape.clip(rayStart, rayEnd, objectPos);
        BlockHitResult finalHit = SlabbedOffsetRaycast.raycast(
                client.level,
                rayStart,
                rayEnd,
                CollisionContext.empty());
        String outlineOwner = ownerOf(outlineHit);
        String finalOwner = ownerOf(finalHit);
        double targetDy = "MISS".equals(finalOwner)
                ? Double.NaN
                : ClientDy.dyFor(client.level, finalHit.getBlockPos(), client.level.getBlockState(finalHit.getBlockPos()));
        boolean green = dyMatches(modelDy)
                && dyMatches(outlineDy)
                && dyMatches(targetDy)
                && formatPos(objectPos).equals(outlineOwner)
                && formatPos(objectPos).equals(finalOwner);
        return new TriadMeasurement(
                modelDy,
                outlineDy,
                targetDy,
                outlineBox == null ? "empty" : formatBox(outlineBox),
                outlineOwner,
                finalOwner,
                finalHit == null ? "null" : finalHit.getType().name(),
                finalHit == null ? "none" : finalHit.getDirection().name(),
                finalHit == null ? "none" : formatVec(finalHit.getLocation()),
                green);
    }

    private static String mismatchReason(ServerMeasurement server, ClientMeasurement client, TriadMeasurement triad) {
        if (!server.green()) {
            if (!server.loweredStepFaceEast() || !server.flatStepFaceWest()) {
                return "server_lowered_vs_flat_step_faces_not_green";
            }
            if (!dyZero(server.flatServerDy())) {
                return "server_flat_neighbor_not_flat";
            }
            return "server_direct_bottom_slab_lane_not_green";
        }
        if (!client.supportSynced()) {
            return "client_support_state_not_synced";
        }
        if (!client.objectSynced()) {
            return "client_object_state_not_synced";
        }
        if (!client.flatObjectSynced()) {
            return "client_flat_neighbor_state_not_synced";
        }
        if (!dyMatches(client.clientDy())) {
            return "client_dy_not_lowered";
        }
        if (!dyZero(client.flatClientDy())) {
            return "client_flat_neighbor_not_flat";
        }
        if (!client.loweredStepFaceEast() || !client.flatStepFaceWest()) {
            return "client_lowered_vs_flat_step_faces_not_green";
        }
        if (!triad.green()) {
            return "client_visual_triad_not_aligned";
        }
        return "unknown_mismatch";
    }

    private static RenderCullMeasurement measureOrRequestRenderCull(
            Minecraft client,
            ServerMeasurement measured,
            ClientMeasurement clientMeasurement
    ) {
        BakedModel proofModel = client.getBlockRenderer().getBlockModel(clientMeasurement.objectState());
        String proofModelClass = proofModel == null ? "null" : proofModel.getClass().getName();
        boolean proofModelWrapped = proofModel instanceof OffsetBlockStateModel;
        if (!proofModelWrapped) {
            return new RenderCullMeasurement(
                    true,
                    false,
                    "render_step_cull_model_not_wrapped",
                    OffsetBlockStateModel.StepCullSample.missing(),
                    proofModelClass,
                    false);
        }

        if (!renderCullSampleRequested) {
            renderCullSampleRequested = true;
            renderCullSampleRequestedTick = ticks;
            OffsetBlockStateModel.resetStepCullSample(measured.objectPos());
            markRenderDirty(client, measured.objectPos(), measured.flatObjectPos());
            return RenderCullMeasurement.waiting("render_step_cull_sample_requested", proofModelClass, true);
        }

        OffsetBlockStateModel.StepCullSample sample = OffsetBlockStateModel.snapshotStepCullSample();
        if (!sample.seen() && ticks - renderCullSampleRequestedTick < RENDER_CULL_SAMPLE_TIMEOUT_TICKS) {
            if ((ticks - renderCullSampleRequestedTick) % 20 == 0) {
                markRenderDirty(client, measured.objectPos(), measured.flatObjectPos());
            }
            return RenderCullMeasurement.waiting("waiting_for_render_step_cull_sample", proofModelClass, true);
        }

        boolean green = sample.seen()
                && clientMeasurement.loweredStepFaceEast()
                && clientMeasurement.flatStepFaceWest()
                && sample.clearStepCullFaces()
                && sample.stepFacesSeen() > 0
                && sample.stepCullFacesCleared() > 0;
        String reason;
        if (green) {
            reason = "render_step_cull_face_cleared_for_lowered_vs_flat_seam";
        } else if (!sample.seen()) {
            reason = "render_step_cull_sample_missing";
        } else if (!sample.clearStepCullFaces()) {
            reason = "render_step_cull_clear_flag_false";
        } else if (sample.stepFacesSeen() <= 0) {
            reason = "render_step_cull_step_face_not_seen";
        } else if (sample.stepCullFacesCleared() <= 0) {
            reason = "render_step_cull_face_not_cleared";
        } else {
            reason = "render_step_cull_unknown_mismatch";
        }
        return new RenderCullMeasurement(true, green, reason, sample, proofModelClass, true);
    }

    private static void markRenderDirty(Minecraft client, BlockPos objectPos, BlockPos flatObjectPos) {
        if (client.player != null) {
            client.player.setPos(objectPos.getX() + 0.5d, objectPos.getY() + 0.75d, objectPos.getZ() + 4.0d);
            client.player.setYRot(180.0f);
            client.player.setXRot(0.0f);
        }
        if (client.levelRenderer == null) {
            return;
        }
        int minX = Math.min(objectPos.getX(), flatObjectPos.getX()) - 1;
        int maxX = Math.max(objectPos.getX(), flatObjectPos.getX()) + 1;
        int minY = Math.min(objectPos.getY(), flatObjectPos.getY()) - 1;
        int maxY = Math.max(objectPos.getY(), flatObjectPos.getY()) + 1;
        int minZ = Math.min(objectPos.getZ(), flatObjectPos.getZ()) - 1;
        int maxZ = Math.max(objectPos.getZ(), flatObjectPos.getZ()) + 1;
        client.levelRenderer.setBlocksDirty(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void logRenderCullSummary(
            ServerMeasurement measured,
            ClientMeasurement clientMeasurement,
            RenderCullMeasurement renderCullMeasurement,
            String result,
            String reason
    ) {
        OffsetBlockStateModel.StepCullSample sample = renderCullMeasurement.sample();
        Slabbed.LOGGER.info(
                "[MC1211_NEOFORGE_CLIENT_CULL_PROOF_SUMMARY] route={} scenario=LOWERED_VS_FLAT_FULL_BLOCK_SEAM result={} reason={} loweredPos={} flatPos={} loweredDy={} flatDy={} loweredStepFaceEast={} flatStepFaceWest={} modelClass={} modelWrapped={} sampleSeen={} clearStepCullFaces={} totalQuadsSeen={} cullFacesSeen={} stepFacesSeen={} stepCullFacesCleared={} clearedFaces={} sampleReason={} viewClass={} diagnosticsOnly=true semanticsChanged=false releaseReady=false",
                ROUTE,
                result,
                reason,
                formatPos(measured.objectPos()),
                formatPos(measured.flatObjectPos()),
                clientMeasurement.clientDy(),
                clientMeasurement.flatClientDy(),
                clientMeasurement.loweredStepFaceEast(),
                clientMeasurement.flatStepFaceWest(),
                renderCullMeasurement.proofModelClass(),
                renderCullMeasurement.proofModelWrapped(),
                sample.seen(),
                sample.clearStepCullFaces(),
                sample.totalQuadsSeen(),
                sample.cullFacesSeen(),
                sample.stepFacesSeen(),
                sample.stepCullFacesCleared(),
                sample.clearedFaces(),
                sample.reason(),
                sample.viewClass());
    }

    private static boolean dyMatches(double dy) {
        return Math.abs(dy - EXPECTED_DY) <= EPSILON;
    }

    private static boolean dyZero(double dy) {
        return Math.abs(dy) <= EPSILON;
    }

    private static void logSummaryAndStop(Minecraft client, String result, String reason) {
        if (terminalLogged) {
            return;
        }
        terminalLogged = true;
        int greenRows = "GREEN".equals(result) ? 1 : 0;
        int redRows = "GREEN".equals(result) ? 0 : 1;
        Slabbed.LOGGER.info(
                "[MC1211_NEOFORGE_CLIENT_WORLD_PROOF_SUMMARY] route={} result={} reason={} rows=1 green={} red={} worldSource={} diagnosticsOnly=true semanticsChanged=false releaseReady=false",
                ROUTE,
                result,
                reason,
                greenRows,
                redRows,
                worldSource);
        client.stop();
    }

    private static String screenName(Screen screen) {
        return screen == null ? "none" : screen.getClass().getName();
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String ownerOf(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            return "MISS";
        }
        return formatPos(blockHit.getBlockPos());
    }

    private static String formatBox(AABB box) {
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
            BlockPos supportPos,
            BlockPos objectPos,
            BlockPos flatObjectPos,
            BlockState supportState,
            BlockState objectState,
            BlockState flatObjectState,
            boolean serverAnchor,
            double serverDy,
            double flatServerDy,
            boolean loweredStepFaceEast,
            boolean flatStepFaceWest,
            boolean green
    ) {
    }

    private record ClientMeasurement(
            BlockState supportState,
            BlockState objectState,
            BlockState flatObjectState,
            boolean clientAnchor,
            double clientDy,
            double flatClientDy,
            boolean supportSynced,
            boolean objectSynced,
            boolean flatObjectSynced,
            boolean loweredStepFaceEast,
            boolean flatStepFaceWest
    ) {
    }

    private record RenderCullMeasurement(
            boolean ready,
            boolean green,
            String reason,
            OffsetBlockStateModel.StepCullSample sample,
            String proofModelClass,
            boolean proofModelWrapped
    ) {
        static RenderCullMeasurement waiting(String reason, String proofModelClass, boolean proofModelWrapped) {
            return new RenderCullMeasurement(false, false, reason, OffsetBlockStateModel.StepCullSample.missing(), proofModelClass, proofModelWrapped);
        }
    }

    private record TriadMeasurement(
            double modelDy,
            double outlineDy,
            double targetDy,
            String outlineBox,
            String outlineOwner,
            String finalOwner,
            String finalHitType,
            String finalFace,
            String finalHitVec,
            boolean green
    ) {
        static TriadMeasurement missing(String reason) {
            return new TriadMeasurement(
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    reason,
                    "MISS",
                    "MISS",
                    "MISS",
                    "none",
                    "none",
                    false);
        }
    }
}
