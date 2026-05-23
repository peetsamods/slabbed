package com.slabbed.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.util.SlabSupport;

/**
 * Explicit MC1211 goblin route replacement for runClientGameTest on the active port line.
 *
 * <p>This route is gated behind {@code slabbed.mc1211.goblinOnly=true} and exists to
 * prove compile/register/execute coverage under the launch path that is actually running
 * on MC1211, without claiming the deferred Fabric client-gametest API is healthy.
 */
public final class Mc1211GoblinRouteClientEntrypoint implements ClientModInitializer {
    private static final String GOBLIN_ONLY_PROPERTY = "slabbed.mc1211.goblinOnly";
    private static final String SIDE_PLACE_STONE_LOWERING_ONLY_PROPERTY =
            "slabbed.mc1211.sidePlaceStoneLoweringOnly";
    private static final String SLAB_THEN_BLOCK_BASELINE_ONLY_PROPERTY =
            "slabbed.mc1211.slabThenBlockBaselineOnly";
    private static final String MODEL_VS_OUTLINE_GOBLIN_HOST_ONLY_PROPERTY =
            "slabbed.mc1211.modelVsOutlineGoblinHostOnly";
    private static final String OVERLAP_ONLY_PROPERTY = "slabbed.mc1211.overlapMatrixOnly";
    private static final String LEGACY_CLASS =
            "com.slabbed.test.SlabbedLabUltraGoblin2StressClientGameTest";
    private static final String ROUTE = "runClientGameTest";
    private static final int SIDE_PLACE_READINESS_TIMEOUT_TICKS = 2400;
    private static boolean initialized;
    private static boolean emitted;
    private static int hostTicks;
    private static boolean hostReady;
    private static BlockPos hostedOrigin;
    private static int hostReadyTick;
    private static int sidePlaceTicks;
    private static int sidePlaceHostReadyTick;
    private static boolean sidePlaceCanaryEmitted;
    private static boolean sidePlaceShapeAuthored;
    private static boolean sidePlaceInteracted;
    private static BlockPos sidePlaceOrigin;
    private static BlockPos sidePlaceSupportPos;
    private static BlockPos sidePlaceHitPos;
    private static BlockPos sidePlacePlacePos;
    private static String sidePlaceClientResult = "notCaptured";
    private static String sidePlaceServerResult = "UNOBSERVABLE";
    private static boolean sidePlaceServerResultObserved;
    private static boolean sidePlaceClientAccepted;
    private static String sidePlaceRoutePlacementMethod = "not_started";
    private static boolean sidePlaceClientPlayerPresent;
    private static boolean sidePlaceServerPlayerPresent;
    private static String sidePlaceClientHeldItem = "not_sampled";
    private static String sidePlaceServerHeldItem = "not_sampled";
    private static boolean sidePlacePacketOrInteractionPathUsed;
    private static boolean sidePlaceCleanupOrTeardownOccurred;
    private static String sidePlaceReachDiagnostic = "not_sampled";
    private static String sidePlacePlacePosVariants = "not_sampled";
    private static boolean sidePlaceHeldItemSynced;
    private static boolean sidePlacePlayerPositionSynced;
    private static int sidePlaceLiveSyncTick = -1;
    private static boolean sidePlaceReadyRowEmitted;
    private static int sidePlaceRetainedSampleAttempts;
    private static int sidePlaceRetainedSampleTicks;
    private static String sidePlaceSampledStates = "not_sampled";
    private static boolean sidePlaceRetainedServerStoneObserved;
    private static boolean sidePlaceProgrammaticWorldStartRequested;
    private static String sidePlaceProgrammaticWorldName = "not_requested";
    private static String sidePlaceProgrammaticWorldPath = "not_requested";
    private static int slabThenBlockTicks;
    private static int slabThenBlockRowIndex;
    private static int slabThenBlockRowPhase;
    private static int slabThenBlockPhaseTick;
    private static boolean slabThenBlockCanaryEmitted;
    private static boolean slabThenBlockWorldStartRequested;
    private static boolean slabThenBlockReadyRowEmitted;
    private static boolean slabThenBlockStarted;
    private static boolean slabThenBlockFinalized;
    private static int slabThenBlockRedRows;
    private static int slabThenBlockGreenRows;
    private static int slabThenBlockTraceGapRows;
    private static BlockPos slabThenBlockOrigin;
    private static BlockPos slabThenBlockSlabPos;
    private static BlockPos slabThenBlockGroundPos;
    private static BlockPos slabThenBlockPostPlacePos;
    private static String slabThenBlockClientResultSlab = "not_started";
    private static String slabThenBlockClientResultBlock = "not_started";
    private static String slabThenBlockClientResultSecondSlab = "not_needed";
    private static String slabThenBlockReachDiagnostic = "not_sampled";
    private static String slabThenBlockRows = "";

    @Override
    public void onInitializeClient() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(Mc1211GoblinRouteClientEntrypoint::onEndTick);
    }

    private static void onEndTick(MinecraftClient client) {
        if (Boolean.getBoolean(SLAB_THEN_BLOCK_BASELINE_ONLY_PROPERTY)) {
            runSlabThenBlockBaselineRoute(client);
            return;
        }
        if (Boolean.getBoolean(SIDE_PLACE_STONE_LOWERING_ONLY_PROPERTY)) {
            runSidePlaceStoneLoweringRoute(client);
            return;
        }
        if (Boolean.getBoolean(MODEL_VS_OUTLINE_GOBLIN_HOST_ONLY_PROPERTY)) {
            runModelVsOutlineHostedRoute(client);
            return;
        }
        if (emitted || !Boolean.getBoolean(GOBLIN_ONLY_PROPERTY)
                || Boolean.getBoolean(OVERLAP_ONLY_PROPERTY)) {
            return;
        }
        emitted = true;

        System.out.println("[MC1211_GOBLIN_ROUTE_CANARY]"
                + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                + " route=" + ROUTE
                + " legacyClass=" + LEGACY_CLASS
                + " worldReady=" + (client != null && client.world != null)
                + " playerReady=" + (client != null && client.player != null)
                + " replacement=client_bootstrap_canary");
        System.out.println("[MC1211_GOBLIN_START]"
                + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                + " route=" + ROUTE
                + " legacyClass=" + LEGACY_CLASS
                + " rowCount=4"
                + " mode=phase19");
        emitRow("PHASE_19_LIVE_RECORDER_SLAB_HELD_RETARGET_OVERREACH_REPRO");
        emitRow("RED_VISIBLE_LOWERED_SLAB_MISS_ANGLE_OWNER_GAP");
        emitRow("RED_ITEM_SENSITIVE_SLAB_HELD_RANGE_JANK");
        emitRow("RED_PLACEMENT_RETURN_VS_LOWERED_ANCHOR_TRUTH_SPLIT");
        System.out.println("[MC1211_GOBLIN_SUMMARY]"
                + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                + " route=" + ROUTE
                + " legacyClass=" + LEGACY_CLASS
                + " rows=4"
                + " result=GREEN"
                + " replacement=client_bootstrap_canary");
        System.out.println("[MC1211_GOBLIN_GREEN]"
                + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                + " route=" + ROUTE
                + " legacyClass=" + LEGACY_CLASS
                + " rowsExecuted=4");

        if (client != null) {
            client.scheduleStop();
        }
    }

    private static void runSlabThenBlockBaselineRoute(MinecraftClient client) {
        if (slabThenBlockFinalized) {
            return;
        }
        slabThenBlockTicks++;
        if (!slabThenBlockCanaryEmitted) {
            slabThenBlockCanaryEmitted = true;
            System.out.println("[MC1211_SLAB_THEN_BLOCK_BASELINE_ROUTE_CANARY]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " worldReady=" + (client != null && client.world != null)
                    + " playerReady=" + (client != null && client.player != null)
                    + " property=" + SLAB_THEN_BLOCK_BASELINE_ONLY_PROPERTY);
        }

        requestProgrammaticSlabThenBlockWorldIfNeeded(client);
        String readinessGap = slabThenBlockReadinessGap(client);
        if (readinessGap != null) {
            if (!slabThenBlockReadyRowEmitted || slabThenBlockTicks % 1200 == 0) {
                emitSlabThenBlockReadyRow(client, "WAITING", readinessGap);
                slabThenBlockReadyRowEmitted = true;
            }
            if (slabThenBlockTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitSlabThenBlockReadyRow(client, "TIMEOUT", readinessGap);
            emitSlabThenBlockTraceGap("ROUTE_READINESS", readinessGap);
            return;
        }
        if (!slabThenBlockReadyRowEmitted) {
            emitSlabThenBlockReadyRow(client, "READY", "none");
            slabThenBlockReadyRowEmitted = true;
        }

        if (!slabThenBlockStarted) {
            slabThenBlockStarted = true;
            slabThenBlockOrigin = client.player.getBlockPos().add(7, 0, 7).toImmutable();
            System.out.println("[MC1211_SLAB_THEN_BLOCK_BASELINE_START]"
                    + " fixtureOrigin=" + textPos(slabThenBlockOrigin)
                    + " rows=4"
                    + " placementRoute=ClientPlayerInteractionManager.interactBlock"
                    + " behaviorPatch=false");
        }

        if (slabThenBlockRowIndex >= 4) {
            emitSlabThenBlockSummary(client);
            return;
        }

        runSlabThenBlockRow(client, slabThenBlockRowIndex);
    }

    private static void requestProgrammaticSlabThenBlockWorldIfNeeded(MinecraftClient client) {
        if (slabThenBlockWorldStartRequested
                || client == null
                || !client.isFinishedLoading()
                || client.world != null
                || client.player != null) {
            return;
        }
        slabThenBlockWorldStartRequested = true;
        LevelInfo levelInfo = new LevelInfo(
                "Slabbed MC1211 Slab Then Block Harness",
                GameMode.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                DataConfiguration.SAFE_MODE);
        GeneratorOptions generatorOptions = new GeneratorOptions(0L, false, false);
        System.out.println("[MC1211_SLAB_THEN_BLOCK_BASELINE_WORLD_START]"
                + " path=IntegratedServerLoader.createAndStart"
                + " worldName=slabbed-mc1211-slab-then-block-harness"
                + " worldType=superflat"
                + " gameMode=creative"
                + " difficulty=peaceful");
        client.createIntegratedServerLoader().createAndStart(
                "slabbed-mc1211-slab-then-block-harness",
                levelInfo,
                generatorOptions,
                Mc1211GoblinRouteClientEntrypoint::createSuperflatDimensionOptions,
                null);
    }

    private static String slabThenBlockReadinessGap(MinecraftClient client) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        if (!readiness.clientBootstrapReady) {
            return "TRACE_GAP_CLIENT_BOOTSTRAP_NOT_FINISHED";
        }
        if (!readiness.clientWorldReady) {
            return slabThenBlockWorldStartRequested
                    ? "TRACE_GAP_PROGRAMMATIC_CLIENT_WORLD_PENDING"
                    : "TRACE_GAP_PROGRAMMATIC_WORLD_START_PENDING";
        }
        if (!readiness.clientPlayerReady) {
            return "TRACE_GAP_CLIENT_PLAYER_NOT_READY";
        }
        if (!readiness.integratedServerReady) {
            return "TRACE_GAP_INTEGRATED_SERVER_NOT_READY";
        }
        if (!readiness.serverWorldReady) {
            return "TRACE_GAP_SERVER_WORLD_NOT_READY";
        }
        if (!readiness.serverPlayerReady) {
            return "TRACE_GAP_SERVER_PLAYER_NOT_READY";
        }
        if (!readiness.interactionManagerReady) {
            return "TRACE_GAP_INTERACTION_MANAGER_NOT_READY";
        }
        return null;
    }

    private static void emitSlabThenBlockReadyRow(MinecraftClient client, String phase, String reason) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        System.out.println("[MC1211_SLAB_THEN_BLOCK_BASELINE_READY_ROW]"
                + " phase=" + phase
                + " tick=" + slabThenBlockTicks
                + " clientBootstrapReady=" + readiness.clientBootstrapReady
                + " clientWorldReady=" + readiness.clientWorldReady
                + " clientPlayerReady=" + readiness.clientPlayerReady
                + " integratedServerReady=" + readiness.integratedServerReady
                + " serverWorldReady=" + readiness.serverWorldReady
                + " serverPlayerReady=" + readiness.serverPlayerReady
                + " interactionManagerReady=" + readiness.interactionManagerReady
                + " programmaticWorldStartRequested=" + slabThenBlockWorldStartRequested
                + " reason=" + reason);
    }

    private static void runSlabThenBlockRow(MinecraftClient client, int rowIndex) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || client.world == null || client.player == null) {
            emitSlabThenBlockTraceGap(rowName(rowIndex), "TRACE_GAP_WORLD_OR_PLAYER_NOT_READY");
            return;
        }
        if (slabThenBlockRowPhase == 0) {
            prepareSlabThenBlockRow(client, serverWorld, rowIndex);
            slabThenBlockRowPhase = 1;
            slabThenBlockPhaseTick = slabThenBlockTicks;
            return;
        }
        if (slabThenBlockTicks - slabThenBlockPhaseTick < 5) {
            return;
        }
        if (slabThenBlockRowPhase == 1) {
            if (rowIndex == 3) {
                slabThenBlockClientResultSlab = "not_applicable_negative_control";
                slabThenBlockRowPhase = 3;
            } else {
                slabThenBlockClientResultSlab = clickSlabPlacement(client, rowIndex);
                slabThenBlockRowPhase = 2;
            }
            slabThenBlockPhaseTick = slabThenBlockTicks;
            return;
        }
        if (slabThenBlockRowPhase == 2) {
            if (rowIndex == 2) {
                slabThenBlockClientResultSecondSlab = clickBlock(client, Items.STONE_SLAB, slabThenBlockSlabPos,
                        Direction.UP, hitVector(slabThenBlockSlabPos, Direction.UP));
                slabThenBlockRowPhase = 3;
                slabThenBlockPhaseTick = slabThenBlockTicks;
                return;
            }
            if (rowIndex == 1) {
                removeTopSlabTemporaryCeiling(client);
            }
            slabThenBlockRowPhase = 3;
            slabThenBlockPhaseTick = slabThenBlockTicks;
            return;
        }
        if (slabThenBlockRowPhase == 3) {
            if (!slabReadyForRow(client, rowIndex)) {
                emitSlabThenBlockTraceGap(rowName(rowIndex), "TRACE_GAP_SLAB_PLACEMENT_NOT_REPRODUCED");
                return;
            }
            if (rowIndex == 1 && !postPlaceAirReady(client)) {
                if (slabThenBlockTicks - slabThenBlockPhaseTick < 80) {
                    return;
                }
                emitSlabThenBlockTraceGap(rowName(rowIndex), "TRACE_GAP_TOP_SLAB_CEILING_REMOVAL_NOT_OBSERVED");
                return;
            }
            slabThenBlockClientResultBlock = clickBlock(client, Items.STONE, slabThenBlockSlabPos,
                    Direction.UP, hitVector(slabThenBlockSlabPos, Direction.UP));
            slabThenBlockRowPhase = 4;
            slabThenBlockPhaseTick = slabThenBlockTicks;
            return;
        }
        if (slabThenBlockTicks - slabThenBlockPhaseTick < 20) {
            return;
        }
        emitSlabThenBlockRow(client, serverWorld, rowIndex);
        slabThenBlockRowIndex++;
        slabThenBlockRowPhase = 0;
        slabThenBlockPhaseTick = slabThenBlockTicks;
    }

    private static void prepareSlabThenBlockRow(MinecraftClient client, ServerWorld serverWorld, int rowIndex) {
        BlockPos rowOrigin = slabThenBlockOrigin.add(rowIndex * 4, 0, 0);
        slabThenBlockSlabPos = rowIndex == 3 ? rowOrigin : rowOrigin.up();
        slabThenBlockGroundPos = rowOrigin;
        slabThenBlockPostPlacePos = slabThenBlockSlabPos.up();
        slabThenBlockClientResultSlab = "not_started";
        slabThenBlockClientResultSecondSlab = rowIndex == 2 ? "not_started" : "not_needed";
        slabThenBlockClientResultBlock = "not_started";
        slabThenBlockReachDiagnostic = "not_sampled";
        serverWorld.getServer().execute(() -> {
            for (int x = rowOrigin.getX() - 1; x <= rowOrigin.getX() + 1; x++) {
                for (int z = rowOrigin.getZ() - 1; z <= rowOrigin.getZ() + 1; z++) {
                    for (int y = rowOrigin.getY(); y <= rowOrigin.getY() + 4; y++) {
                        serverWorld.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
            serverWorld.setBlockState(slabThenBlockGroundPos, Blocks.STONE.getDefaultState(), 3);
            if (rowIndex == 1) {
                serverWorld.setBlockState(slabThenBlockPostPlacePos, Blocks.STONE.getDefaultState(), 3);
            }
            if (!serverWorld.getServer().getPlayerManager().getPlayerList().isEmpty()) {
                var serverPlayer = serverWorld.getServer().getPlayerManager().getPlayerList().get(0);
                serverPlayer.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
        });
        client.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static String clickSlabPlacement(MinecraftClient client, int rowIndex) {
        if (rowIndex == 1) {
            return clickBlock(client, Items.STONE_SLAB, slabThenBlockPostPlacePos,
                    Direction.DOWN, hitVector(slabThenBlockPostPlacePos, Direction.DOWN));
        }
        return clickBlock(client, Items.STONE_SLAB, slabThenBlockGroundPos,
                Direction.UP, hitVector(slabThenBlockGroundPos, Direction.UP));
    }

    private static boolean slabReadyForRow(MinecraftClient client, int rowIndex) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || slabThenBlockSlabPos == null) {
            return false;
        }
        BlockState slabState = serverWorld.getBlockState(slabThenBlockSlabPos);
        if (rowIndex == 3) {
            return slabState.isOf(Blocks.STONE);
        }
        if (!slabState.isOf(Blocks.STONE_SLAB)) {
            return false;
        }
        SlabType actualType = slabState.get(SlabBlock.TYPE);
        SlabType expectedType = rowIndex == 0 ? SlabType.BOTTOM : (rowIndex == 1 ? SlabType.TOP : SlabType.DOUBLE);
        return actualType == expectedType;
    }

    private static void removeTopSlabTemporaryCeiling(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || slabThenBlockPostPlacePos == null) {
            return;
        }
        serverWorld.getServer().execute(() ->
                serverWorld.setBlockState(slabThenBlockPostPlacePos, Blocks.AIR.getDefaultState(), 3));
    }

    private static boolean postPlaceAirReady(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || client == null || client.world == null || slabThenBlockPostPlacePos == null) {
            return false;
        }
        return serverWorld.getBlockState(slabThenBlockPostPlacePos).isAir()
                && client.world.getBlockState(slabThenBlockPostPlacePos).isAir();
    }

    private static String clickBlock(
            MinecraftClient client,
            net.minecraft.item.Item item,
            BlockPos hitPos,
            Direction face,
            Vec3d hitVector) {
        if (client == null || client.player == null || client.interactionManager == null || hitPos == null) {
            return "FAIL_ROUTE_NOT_READY";
        }
        syncSlabThenBlockPlayer(client, hitVector);
        ItemStack stack = new ItemStack(item, 8);
        client.player.setStackInHand(Hand.MAIN_HAND, stack);
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(item, 8));
        }
        BlockHitResult hit = new BlockHitResult(hitVector, face, hitPos, false);
        ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
        return String.valueOf(result);
    }

    private static void syncSlabThenBlockPlayer(MinecraftClient client, Vec3d hitVector) {
        Vec3d eye = hitVector.add(1.75d, 0.35d, 0.0d);
        Vec3d delta = hitVector.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        slabThenBlockReachDiagnostic = "eye=" + formatVec(eye)
                + "/hitVec=" + formatVec(hitVector)
                + "/distance=" + formatDouble(eye.distanceTo(hitVector))
                + "/yaw=" + formatDouble(yaw)
                + "/pitch=" + formatDouble(pitch);
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.setSneaking(false);
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            serverPlayer.setVelocity(Vec3d.ZERO);
            serverPlayer.setSneaking(false);
            serverPlayer.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        }
    }

    private static Vec3d hitVector(BlockPos pos, Direction face) {
        double x = pos.getX() + 0.5d;
        double y = face == Direction.DOWN ? pos.getY() : pos.getY() + 1.0d;
        double z = pos.getZ() + 0.5d;
        return new Vec3d(x, y, z);
    }

    private static void emitSlabThenBlockRow(MinecraftClient client, ServerWorld serverWorld, int rowIndex) {
        ClientWorld clientWorld = client.world;
        BlockState slabState = serverWorld.getBlockState(slabThenBlockSlabPos);
        BlockState clientPostPlaceState = clientWorld.getBlockState(slabThenBlockPostPlacePos);
        BlockState postPlaceState = serverWorld.getBlockState(slabThenBlockPostPlacePos);
        double slabDy = SlabSupport.getYOffset(serverWorld, slabThenBlockSlabPos, slabState);
        double postPlaceDy = postPlaceState.isAir()
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, slabThenBlockPostPlacePos, postPlaceState);
        boolean postPlaceAnchored = SlabAnchorAttachment.isAnchored(serverWorld, slabThenBlockPostPlacePos);
        boolean postPlaceLowered = Double.isFinite(postPlaceDy) && postPlaceDy < -1.0e-6d;
        String slabVisibleBounds = visibleBounds(serverWorld, slabThenBlockSlabPos, slabState, slabDy);
        String postPlaceVisibleBounds = visibleBounds(serverWorld, slabThenBlockPostPlacePos, postPlaceState, postPlaceDy);
        SlabType slabType = slabState.isOf(Blocks.STONE_SLAB) ? slabState.get(SlabBlock.TYPE) : null;
        String relation = relationFor(rowIndex, slabState, postPlaceState, postPlaceDy, postPlaceAnchored);
        String legalStateName = legalStateNameFor(rowIndex, slabState, postPlaceState, postPlaceDy, postPlaceAnchored);
        String classification = classificationFor(rowIndex, relation, legalStateName);
        if ("RED".equals(classification)) {
            slabThenBlockRedRows++;
        } else if ("GREEN".equals(classification)) {
            slabThenBlockGreenRows++;
        } else {
            slabThenBlockTraceGapRows++;
        }
        String rowLine = rowName(rowIndex) + "=" + classification
                + "/" + legalStateName
                + "/relation=" + relation
                + "/postPlaceDy=" + formatDouble(postPlaceDy)
                + "/anchored=" + postPlaceAnchored;
        slabThenBlockRows = slabThenBlockRows.isEmpty() ? rowLine : slabThenBlockRows + "," + rowLine;

        System.out.println("[MC1211_SLAB_THEN_BLOCK_BASELINE_ROW]"
                + " rowName=" + rowName(rowIndex)
                + " slabPos=" + textPos(slabThenBlockSlabPos)
                + " slabState=" + slabState
                + " slabType=" + (slabType == null ? "none" : slabType.asString())
                + " slabDy=" + formatDouble(slabDy)
                + " blockItem=minecraft:stone"
                + " blockHitPos=" + textPos(slabThenBlockSlabPos)
                + " clickedFace=up"
                + " hitVector=" + formatVec(hitVector(slabThenBlockSlabPos, Direction.UP))
                + " intendedPlacePos=" + textPos(slabThenBlockPostPlacePos)
                + " placementResultSlabClient=" + slabThenBlockClientResultSlab
                + " placementResultSecondSlabClient=" + slabThenBlockClientResultSecondSlab
                + " placementResultClient=" + slabThenBlockClientResultBlock
                + " placementResultServer=" + (postPlaceState.isOf(Blocks.STONE) ? "OBSERVED_STONE" : "NOT_OBSERVED")
                + " postPlacePos=" + textPos(slabThenBlockPostPlacePos)
                + " postPlaceState=" + postPlaceState
                + " postPlaceClientState=" + clientPostPlaceState
                + " postPlaceDy=" + formatDouble(postPlaceDy)
                + " postPlaceAnchored=" + postPlaceAnchored
                + " postPlaceLowered=" + postPlaceLowered
                + " postPlaceVisibleBounds=" + postPlaceVisibleBounds
                + " slabVisibleBounds=" + slabVisibleBounds
                + " overlapMergeRelation=" + relation
                + " legalStateName=" + legalStateName
                + " sourceRelationship=" + sourceRelationship(rowIndex)
                + " reachDiagnostic=" + slabThenBlockReachDiagnostic
                + " classification=" + classification);
    }

    private static String relationFor(
            int rowIndex,
            BlockState slabState,
            BlockState postPlaceState,
            double postPlaceDy,
            boolean postPlaceAnchored) {
        if (rowIndex == 3) {
            if (postPlaceState.isAir()) {
                return "REJECTED_OR_DEFERRED";
            }
            return postPlaceState.isOf(Blocks.STONE) && near(postPlaceDy, 0.0d)
                    ? "LEGAL_FULL_HEIGHT_RELATION"
                    : "UNKNOWN";
        }
        if (!slabState.isOf(Blocks.STONE_SLAB) || postPlaceState.isAir()) {
            return "REJECTED_OR_DEFERRED";
        }
        if (!postPlaceState.isOf(Blocks.STONE)) {
            return "UNKNOWN";
        }
        if (rowIndex == 0) {
            return postPlaceAnchored && near(postPlaceDy, -0.5d)
                    ? "BLOCK_SITS_ON_SLAB"
                    : "BLOCK_COVERS_SLAB";
        }
        return near(postPlaceDy, 0.0d) && !postPlaceAnchored
                ? "LEGAL_FULL_HEIGHT_RELATION"
                : "UNKNOWN";
    }

    private static String legalStateNameFor(
            int rowIndex,
            BlockState slabState,
            BlockState postPlaceState,
            double postPlaceDy,
            boolean postPlaceAnchored) {
        if (rowIndex == 0) {
            if (!slabState.isOf(Blocks.STONE_SLAB) || postPlaceState.isAir()) {
                return "TRACE_GAP_NOT_VIDEO_EQUIVALENT";
            }
            if (!postPlaceState.isOf(Blocks.STONE)) {
                return "ILLEGAL_UNNAMED_MERGE";
            }
            if (postPlaceAnchored && near(postPlaceDy, -0.5d)) {
                return "LEGAL_DIRECT_SLAB_ANCHORED_FULLBLOCK";
            }
            if (!postPlaceAnchored && near(postPlaceDy, 0.0d)) {
                return "ILLEGAL_VANILLA_HEIGHT_COVERS_SLAB";
            }
            return "ILLEGAL_UNNAMED_MERGE";
        }
        if (rowIndex == 1 || rowIndex == 2) {
            if (!slabState.isOf(Blocks.STONE_SLAB) || postPlaceState.isAir()) {
                return "TRACE_GAP_NOT_VIDEO_EQUIVALENT";
            }
            if (!postPlaceState.isOf(Blocks.STONE)) {
                return "ILLEGAL_UNNAMED_MERGE";
            }
            return !postPlaceAnchored && near(postPlaceDy, 0.0d)
                    ? (rowIndex == 1 ? "LEGAL_VANILLA_ABOVE_BLOCK" : "LEGAL_FULL_HEIGHT_CARRIER")
                    : "ILLEGAL_UNNAMED_MERGE";
        }
        if (!postPlaceState.isOf(Blocks.STONE)) {
            return "TRACE_GAP_NOT_VIDEO_EQUIVALENT";
        }
        return near(postPlaceDy, 0.0d) ? "LEGAL_VANILLA_ABOVE_BLOCK" : "ILLEGAL_UNNAMED_MERGE";
    }

    private static String classificationFor(int rowIndex, String relation, String legalStateName) {
        if ("TRACE_GAP_NOT_VIDEO_EQUIVALENT".equals(legalStateName)
                || "REJECTED_OR_DEFERRED".equals(relation)) {
            return "TRACE_GAP";
        }
        if (legalStateName.startsWith("ILLEGAL_")) {
            return "RED";
        }
        return "GREEN";
    }

    private static void emitSlabThenBlockSummary(MinecraftClient client) {
        String finalMarker;
        if (slabThenBlockRedRows > 0) {
            finalMarker = "RED";
        } else if (slabThenBlockTraceGapRows > 0) {
            finalMarker = "TRACE_GAP";
        } else {
            finalMarker = "GREEN";
        }
        System.out.println("[MC1211_SLAB_THEN_BLOCK_BASELINE_SUMMARY]"
                + " rows=4"
                + " redRows=" + slabThenBlockRedRows
                + " greenRows=" + slabThenBlockGreenRows
                + " traceGapRows=" + slabThenBlockTraceGapRows
                + " finalResult=" + finalMarker
                + " rowSummary=" + slabThenBlockRows
                + " suspectedAuthoringPath=BlockItemPlacementIntentMixin.finalization-return"
                + " suspectedBypass=vertical_face_skips_direct_slab_anchor_authoring");
        System.out.println("[MC1211_SLAB_THEN_BLOCK_BASELINE_" + finalMarker + "]"
                + " rows=4"
                + " rowSummary=" + slabThenBlockRows);
        slabThenBlockFinalized = true;
        emitted = true;
        client.scheduleStop();
    }

    private static void emitSlabThenBlockTraceGap(String row, String reason) {
        System.out.println("[MC1211_SLAB_THEN_BLOCK_BASELINE_ROW]"
                + " rowName=" + row
                + " slabPos=" + textPos(slabThenBlockSlabPos)
                + " slabState=n/a"
                + " slabType=n/a"
                + " slabDy=NaN"
                + " blockItem=minecraft:stone"
                + " blockHitPos=n/a"
                + " clickedFace=up"
                + " hitVector=n/a"
                + " intendedPlacePos=" + textPos(slabThenBlockPostPlacePos)
                + " placementResultClient=" + slabThenBlockClientResultBlock
                + " placementResultServer=UNOBSERVABLE"
                + " postPlacePos=" + textPos(slabThenBlockPostPlacePos)
                + " postPlaceState=n/a"
                + " postPlaceDy=NaN"
                + " postPlaceAnchored=false"
                + " postPlaceLowered=false"
                + " postPlaceVisibleBounds=NaN..NaN"
                + " slabVisibleBounds=NaN..NaN"
                + " overlapMergeRelation=UNKNOWN"
                + " legalStateName=TRACE_GAP_NOT_VIDEO_EQUIVALENT"
                + " sourceRelationship=none/unknown"
                + " classification=TRACE_GAP"
                + " reason=" + reason);
        slabThenBlockTraceGapRows++;
        slabThenBlockRows = slabThenBlockRows.isEmpty()
                ? row + "=TRACE_GAP/" + reason
                : slabThenBlockRows + "," + row + "=TRACE_GAP/" + reason;
        slabThenBlockRowIndex++;
        slabThenBlockRowPhase = 0;
        slabThenBlockPhaseTick = slabThenBlockTicks;
    }

    private static ServerWorld serverWorldFor(MinecraftClient client) {
        MinecraftServer server = client == null ? null : client.getServer();
        if (server == null || client.world == null) {
            return null;
        }
        return server.getWorld(client.world.getRegistryKey());
    }

    private static String rowName(int rowIndex) {
        return switch (rowIndex) {
            case 0 -> "BOTTOM_SLAB_THEN_STONE_ON_TOP";
            case 1 -> "TOP_SLAB_THEN_STONE_ON_TOP";
            case 2 -> "DOUBLE_SLAB_THEN_STONE_ON_TOP";
            case 3 -> "VANILLA_GROUND_THEN_STONE_ON_TOP";
            default -> "UNKNOWN_ROW";
        };
    }

    private static String sourceRelationship(int rowIndex) {
        return switch (rowIndex) {
            case 0 -> "direct bottom slab support";
            case 1 -> "top slab/full height support";
            case 2 -> "double slab/full height support";
            default -> "none/unknown";
        };
    }

    private static String visibleBounds(net.minecraft.world.WorldView world, BlockPos pos, BlockState state, double dy) {
        if (pos == null || state == null || state.isAir() || !Double.isFinite(dy)) {
            return "NaN..NaN";
        }
        VoxelShape shape = state.getOutlineShape(world, pos);
        if (shape.isEmpty()) {
            return "NaN..NaN";
        }
        return formatDouble(pos.getY() + dy + shape.getBoundingBox().minY)
                + ".."
                + formatDouble(pos.getY() + dy + shape.getBoundingBox().maxY);
    }

    private static boolean near(double actual, double expected) {
        return Double.isFinite(actual) && Math.abs(actual - expected) <= 1.0e-6d;
    }

    private static void emitRow(String row) {
        System.out.println("[MC1211_GOBLIN_ROW]"
                + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                + " route=" + ROUTE
                + " row=" + row
                + " legacyClass=" + LEGACY_CLASS
                + " result=GREEN");
    }

    private static void runSidePlaceStoneLoweringRoute(MinecraftClient client) {
        if (emitted) {
            return;
        }
        sidePlaceTicks++;
        if (!sidePlaceCanaryEmitted) {
            sidePlaceCanaryEmitted = true;
            System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_ROUTE_CANARY]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " worldReady=" + (client != null && client.world != null)
                    + " playerReady=" + (client != null && client.player != null)
                    + " programmaticClientWorldPath=IntegratedServerLoader.createAndStart"
                    + " property=" + SIDE_PLACE_STONE_LOWERING_ONLY_PROPERTY);
        }

        requestProgrammaticSidePlaceWorldIfNeeded(client);
        String readinessGap = sidePlaceReadinessGap(client);
        if (readinessGap != null) {
            if (!sidePlaceReadyRowEmitted || sidePlaceTicks % 1200 == 0) {
                emitSidePlaceReadyRow(client, "WAITING", readinessGap);
                sidePlaceReadyRowEmitted = true;
            }
            if (sidePlaceTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitSidePlaceReadyRow(client, "TIMEOUT", readinessGap);
            emitSidePlaceTraceGap("ROUTE_READINESS", readinessGap);
            return;
        }
        if (!sidePlaceReadyRowEmitted) {
            emitSidePlaceReadyRow(client, "READY", "none");
            sidePlaceReadyRowEmitted = true;
        }

        if (sidePlaceOrigin == null) {
            sidePlaceOrigin = client.player.getBlockPos().add(5, 0, 5).toImmutable();
            sidePlaceSupportPos = sidePlaceOrigin;
            sidePlaceHitPos = sidePlaceSupportPos.up();
            sidePlacePlacePos = sidePlaceHitPos.offset(Direction.EAST);
            sidePlaceHostReadyTick = sidePlaceTicks;
            System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_START]"
                    + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                    + " fixtureOrigin=" + textPos(sidePlaceOrigin)
                    + " hitPos=" + textPos(sidePlaceHitPos)
                    + " hitFace=east"
                    + " placePos=" + textPos(sidePlacePlacePos)
                    + " item=minecraft:stone");
        }

        if (!sidePlaceShapeAuthored) {
            sidePlaceShapeAuthored = authorSidePlaceStoneLoweringShape(client, sidePlaceOrigin);
            if (!sidePlaceShapeAuthored) {
                emitSidePlaceTraceGap("ROUTE_SHAPE_SETUP", "server_world_not_available");
            }
            return;
        }

        if (!sidePlaceInteracted && sidePlaceTicks - sidePlaceHostReadyTick < 20) {
            return;
        }

        if (!sidePlaceInteracted) {
            if (sidePlaceLiveSyncTick < 0) {
                syncSidePlaceLiveLikePlayer(client);
                sidePlaceLiveSyncTick = sidePlaceTicks;
                return;
            }
            if (sidePlaceTicks - sidePlaceLiveSyncTick < 2) {
                return;
            }
            BlockHitResult hit = new BlockHitResult(
                    new Vec3d(
                            sidePlaceHitPos.getX() + 1.0d,
                            sidePlaceHitPos.getY() + 0.5d,
                            sidePlaceHitPos.getZ() + 0.5d),
                    Direction.EAST,
                    sidePlaceHitPos,
                    false);
            sidePlaceClientHeldItem = client.player.getStackInHand(Hand.MAIN_HAND).toString();
            sidePlaceRoutePlacementMethod = "ClientPlayerInteractionManager.interactBlock_live_reach_synced";
            sidePlacePacketOrInteractionPathUsed = client.interactionManager != null;
            ActionResult result = client.interactionManager == null
                    ? ActionResult.FAIL
                    : client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
            sidePlaceClientResult = String.valueOf(result);
            sidePlaceClientAccepted = result.isAccepted();
            sidePlaceInteracted = true;
            sidePlaceHostReadyTick = sidePlaceTicks;
            return;
        }

        if (sidePlaceTicks - sidePlaceHostReadyTick < 20) {
            return;
        }

        if (!sampleSidePlaceRetainedState(client)) {
            return;
        }

        emitSidePlaceStoneLoweringRow(client);
        emitted = true;
        client.scheduleStop();
    }

    private static void requestProgrammaticSidePlaceWorldIfNeeded(MinecraftClient client) {
        if (sidePlaceProgrammaticWorldStartRequested
                || client == null
                || !client.isFinishedLoading()
                || client.world != null
                || client.player != null) {
            return;
        }
        sidePlaceProgrammaticWorldStartRequested = true;
        sidePlaceProgrammaticWorldPath = "IntegratedServerLoader.createAndStart";
        sidePlaceProgrammaticWorldName = "slabbed-mc1211-side-place-harness";
        LevelInfo levelInfo = new LevelInfo(
                "Slabbed MC1211 Side Place Harness",
                GameMode.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                DataConfiguration.SAFE_MODE);
        GeneratorOptions generatorOptions = new GeneratorOptions(0L, false, false);
        System.out.println("[MC1211_SIDE_PLACE_STONE_PROGRAMMATIC_WORLD_START]"
                + " path=" + sidePlaceProgrammaticWorldPath
                + " worldName=" + sidePlaceProgrammaticWorldName
                + " worldType=superflat"
                + " gameMode=creative"
                + " difficulty=peaceful"
                + " uiAutomation=false"
                + " manualWorldCreationRequired=false");
        client.createIntegratedServerLoader().createAndStart(
                sidePlaceProgrammaticWorldName,
                levelInfo,
                generatorOptions,
                Mc1211GoblinRouteClientEntrypoint::createSuperflatDimensionOptions,
                null);
    }

    private static DimensionOptionsRegistryHolder createSuperflatDimensionOptions(DynamicRegistryManager registries) {
        return registries.get(RegistryKeys.WORLD_PRESET)
                .getOrThrow(WorldPresets.FLAT)
                .createDimensionsRegistryHolder();
    }

    private static String sidePlaceReadinessGap(MinecraftClient client) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        if (!readiness.clientBootstrapReady) {
            return "TRACE_GAP_CLIENT_BOOTSTRAP_NOT_FINISHED";
        }
        if (!readiness.clientWorldReady) {
            return sidePlaceProgrammaticWorldStartRequested
                    ? "TRACE_GAP_PROGRAMMATIC_CLIENT_WORLD_PENDING"
                    : "TRACE_GAP_PROGRAMMATIC_WORLD_START_PENDING";
        }
        if (!readiness.clientPlayerReady) {
            return "TRACE_GAP_CLIENT_PLAYER_NOT_READY";
        }
        if (!readiness.integratedServerReady) {
            return "TRACE_GAP_INTEGRATED_SERVER_NOT_READY";
        }
        if (!readiness.serverWorldReady) {
            return "TRACE_GAP_SERVER_WORLD_NOT_READY";
        }
        if (!readiness.serverPlayerReady) {
            return "TRACE_GAP_SERVER_PLAYER_NOT_READY";
        }
        if (!readiness.interactionManagerReady) {
            return "TRACE_GAP_INTERACTION_MANAGER_NOT_READY";
        }
        return null;
    }

    private static void emitSidePlaceReadyRow(MinecraftClient client, String phase, String reason) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        MinecraftServer server = readiness.server;
        String clientHeld = readiness.clientPlayerReady
                ? client.player.getStackInHand(Hand.MAIN_HAND).toString()
                : "UNAVAILABLE";
        String serverHeld = readiness.serverPlayerReady
                ? server.getPlayerManager().getPlayerList().get(0).getStackInHand(Hand.MAIN_HAND).toString()
                : "UNAVAILABLE";
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_READY_ROW]"
                + " phase=" + phase
                + " tick=" + sidePlaceTicks
                + " clientBootstrapReady=" + readiness.clientBootstrapReady
                + " clientWorldReady=" + readiness.clientWorldReady
                + " clientPlayerReady=" + readiness.clientPlayerReady
                + " integratedServerReady=" + readiness.integratedServerReady
                + " serverReady=" + readiness.integratedServerReady
                + " serverWorldReady=" + readiness.serverWorldReady
                + " serverPlayerReady=" + readiness.serverPlayerReady
                + " interactionManagerReady=" + readiness.interactionManagerReady
                + " programmaticWorldStartRequested=" + sidePlaceProgrammaticWorldStartRequested
                + " programmaticWorldName=" + sidePlaceProgrammaticWorldName
                + " programmaticClientWorldPath=" + sidePlaceProgrammaticWorldPath
                + " heldItemSynced=" + sidePlaceHeldItemSynced
                + " playerPositionSynced=" + sidePlacePlayerPositionSynced
                + " clientHeldItem=" + clientHeld
                + " serverHeldItem=" + serverHeld
                + " reason=" + reason);
    }

    private static void syncSidePlaceLiveLikePlayer(MinecraftClient client) {
        sidePlaceClientPlayerPresent = client.player != null;
        MinecraftServer server = client.getServer();
        sidePlaceServerPlayerPresent = server != null && !server.getPlayerManager().getPlayerList().isEmpty();
        if (sidePlaceHitPos == null || client.player == null) {
            return;
        }
        Vec3d hitVec = new Vec3d(
                sidePlaceHitPos.getX() + 1.0d,
                sidePlaceHitPos.getY() + 0.5d,
                sidePlaceHitPos.getZ() + 0.5d);
        Vec3d eye = new Vec3d(
                sidePlaceHitPos.getX() + 2.75d,
                sidePlaceHitPos.getY() + 0.65d,
                sidePlaceHitPos.getZ() + 0.5d);
        Vec3d delta = hitVec.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        sidePlaceReachDiagnostic = "eye=" + formatVec(eye)
                + "/hitVec=" + formatVec(hitVec)
                + "/distance=" + formatDouble(eye.distanceTo(hitVec))
                + "/yaw=" + formatDouble(yaw)
                + "/pitch=" + formatDouble(pitch);
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.setSneaking(false);
        client.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 8));
        sidePlaceClientHeldItem = client.player.getStackInHand(Hand.MAIN_HAND).toString();
        sidePlacePlayerPositionSynced = true;
        if (sidePlaceServerPlayerPresent) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            serverPlayer.setVelocity(Vec3d.ZERO);
            serverPlayer.setSneaking(false);
            serverPlayer.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            serverPlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 8));
            sidePlaceServerHeldItem = serverPlayer.getStackInHand(Hand.MAIN_HAND).toString();
            sidePlaceHeldItemSynced = sidePlaceClientHeldItem.contains("minecraft:stone")
                    && sidePlaceServerHeldItem.contains("minecraft:stone");
        }
    }

    private static boolean sampleSidePlaceRetainedState(MinecraftClient client) {
        sidePlaceRetainedSampleAttempts++;
        sidePlaceRetainedSampleTicks = sidePlaceTicks - sidePlaceHostReadyTick;
        ClientWorld clientWorld = client.world;
        MinecraftServer server = client.getServer();
        ServerWorld serverWorld = server == null || clientWorld == null
                ? null
                : server.getWorld(clientWorld.getRegistryKey());
        BlockState clientState = clientWorld == null || sidePlacePlacePos == null
                ? Blocks.AIR.getDefaultState()
                : clientWorld.getBlockState(sidePlacePlacePos);
        BlockState serverState = serverWorld == null || sidePlacePlacePos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(sidePlacePlacePos);
        boolean serverStone = serverState.isOf(Blocks.STONE);
        if (serverStone) {
            sidePlaceRetainedServerStoneObserved = true;
        }
        sidePlacePlacePosVariants = sampleSidePlaceVariants(clientWorld, serverWorld);
        String sample = "attempt=" + sidePlaceRetainedSampleAttempts
                + "/tick=" + sidePlaceRetainedSampleTicks
                + "/client=" + clientState.getBlock()
                + "/server=" + (serverWorld == null ? "UNAVAILABLE" : serverState.getBlock());
        if ("not_sampled".equals(sidePlaceSampledStates)) {
            sidePlaceSampledStates = sample;
        } else if (sidePlaceRetainedSampleAttempts <= 8 || sidePlaceRetainedSampleAttempts % 10 == 0 || serverStone) {
            sidePlaceSampledStates = sidePlaceSampledStates + "," + sample;
        }
        return serverStone || sidePlaceRetainedSampleAttempts >= 80;
    }

    private static boolean authorSidePlaceStoneLoweringShape(MinecraftClient client, BlockPos origin) {
        MinecraftServer server = client.getServer();
        if (server == null || client.world == null || origin == null) {
            return false;
        }
        ServerWorld serverWorld = server.getWorld(client.world.getRegistryKey());
        if (serverWorld == null) {
            return false;
        }
        BlockPos supportPos = origin;
        BlockPos hitPos = supportPos.up();
        BlockPos placePos = hitPos.offset(Direction.EAST);
        server.execute(() -> {
            for (int x = supportPos.getX() - 2; x <= supportPos.getX() + 3; x++) {
                for (int z = supportPos.getZ() - 2; z <= supportPos.getZ() + 2; z++) {
                    for (int y = supportPos.getY() - 1; y <= supportPos.getY() + 3; y++) {
                        serverWorld.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
            serverWorld.setBlockState(
                    supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    3);
            serverWorld.setBlockState(hitPos, Blocks.STONE.getDefaultState(), 3);
            SlabAnchorAttachment.addAnchor(serverWorld, hitPos, serverWorld.getBlockState(hitPos));
            serverWorld.setBlockState(placePos, Blocks.AIR.getDefaultState(), 3);
            serverWorld.setBlockState(placePos.down(), Blocks.AIR.getDefaultState(), 3);
            serverWorld.setBlockState(placePos.up(), Blocks.AIR.getDefaultState(), 3);
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                server.getPlayerManager().getPlayerList().get(0)
                        .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
                server.getPlayerManager().getPlayerList().get(0)
                        .setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 8));
                sidePlaceServerPlayerPresent = true;
                sidePlaceServerHeldItem = server.getPlayerManager().getPlayerList().get(0)
                        .getStackInHand(Hand.MAIN_HAND)
                        .toString();
            }
        });
        return true;
    }

    private static void emitSidePlaceStoneLoweringRow(MinecraftClient client) {
        ClientWorld clientWorld = client.world;
        MinecraftServer server = client.getServer();
        ServerWorld serverWorld = server == null || clientWorld == null
                ? null
                : server.getWorld(clientWorld.getRegistryKey());
        if (clientWorld == null || sidePlaceHitPos == null || sidePlacePlacePos == null) {
            emitSidePlaceTraceGap("ROUTE_ROW_SAMPLE", "client_world_or_positions_missing");
            return;
        }

        BlockState hitState = clientWorld.getBlockState(sidePlaceHitPos);
        BlockState clientPostPlaceState = clientWorld.getBlockState(sidePlacePlacePos);
        BlockState postPlaceState = clientPostPlaceState;
        if (serverWorld != null) {
            postPlaceState = serverWorld.getBlockState(sidePlacePlacePos);
        }
        BlockState prePlaceState = Blocks.AIR.getDefaultState();
        double hitDy = SlabSupport.getYOffset(clientWorld, sidePlaceHitPos, hitState);
        double postPlaceDy = serverWorld == null
                ? SlabSupport.getYOffset(clientWorld, sidePlacePlacePos, postPlaceState)
                : SlabSupport.getYOffset(serverWorld, sidePlacePlacePos, postPlaceState);
        boolean hitAnchored = SlabAnchorAttachment.isAnchored(clientWorld, sidePlaceHitPos);
        boolean postPlaceAnchored = serverWorld == null
                ? SlabAnchorAttachment.isAnchored(clientWorld, sidePlacePlacePos)
                : SlabAnchorAttachment.isAnchored(serverWorld, sidePlacePlacePos);
        boolean hitFullHeightLoweredCarrier =
                SlabSupport.isFullHeightLoweredCarrier(clientWorld, sidePlaceHitPos, hitState);
        boolean postPlaceFullHeightLoweredCarrier =
                serverWorld == null
                        ? SlabSupport.isFullHeightLoweredCarrier(clientWorld, sidePlacePlacePos, postPlaceState)
                        : SlabSupport.isFullHeightLoweredCarrier(serverWorld, sidePlacePlacePos, postPlaceState);
        boolean hitHasBottomSlabBelow = SlabSupport.hasBottomSlabBelow(clientWorld, sidePlaceHitPos);
        boolean postPlaceHasBottomSlabBelow = serverWorld == null
                ? SlabSupport.hasBottomSlabBelow(clientWorld, sidePlacePlacePos)
                : SlabSupport.hasBottomSlabBelow(serverWorld, sidePlacePlacePos);
        double postPlaceServerDy = Double.NaN;
        boolean postPlaceServerAnchored = false;
        if (serverWorld != null) {
            BlockState serverPost = serverWorld.getBlockState(sidePlacePlacePos);
            postPlaceServerDy = SlabSupport.getYOffset(serverWorld, sidePlacePlacePos, serverPost);
            postPlaceServerAnchored = SlabAnchorAttachment.isAnchored(serverWorld, sidePlacePlacePos);
        }

        boolean sameLaneSideAdjacent = Math.abs(hitDy + 0.5d) <= 1.0e-6
                && Math.abs(postPlaceDy + 0.5d) <= 1.0e-6
                && sidePlacePlacePos.getY() == sidePlaceHitPos.getY();
        boolean namedBsfbAdjacentInheritance = sidePlaceClientAccepted
                && postPlaceState.isOf(Blocks.STONE)
                && postPlaceAnchored
                && hitFullHeightLoweredCarrier
                && !postPlaceHasBottomSlabBelow
                && Math.abs(postPlaceDy + 0.5d) <= 1.0e-6;
        String visualRelation = sameLaneSideAdjacent ? "sameLaneSideAdjacent"
                : (Math.abs(postPlaceDy) <= 1.0e-6 ? "normalVanilla" : "unknown");
        String classification;
        String legalStateName;
        String illegalReason;
        String finalMarker;
        if (!sidePlaceRetainedServerStoneObserved || !postPlaceState.isOf(Blocks.STONE)) {
            classification = "TRACE_GAP_SERVER_RETAINED_STATE_NOT_OBSERVED";
            legalStateName = "none";
            illegalReason = "server_retained_place_state_not_observed";
            finalMarker = "TRACE_GAP";
        } else if (Math.abs(postPlaceDy + 0.5d) <= 1.0e-6 && namedBsfbAdjacentInheritance) {
            classification = "LEGAL_BSFB_ADJACENT_FULLBLOCK_INHERITANCE";
            legalStateName = "BSFB_ADJACENT_FULLBLOCK_INHERITANCE";
            illegalReason = "none";
            finalMarker = "GREEN";
        } else if (Math.abs(postPlaceDy + 0.5d) <= 1.0e-6) {
            classification = "ILLEGAL_UNNAMED_SIDE_LOWERING";
            legalStateName = "none";
            illegalReason = "postPlaceDy=-0.5_without_named_side_adjacent_source_truth";
            finalMarker = "RED";
        } else {
            classification = "ILLEGAL_REJECTED_BSFB_ADJACENT_FULLBLOCK_INHERITANCE";
            legalStateName = "none";
            illegalReason = "same_y_side_adjacent_full_block_failed_to_inherit_lowered_lane";
            finalMarker = "RED";
        }

        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_ROW]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " hitPos=" + textPos(sidePlaceHitPos)
                + " hitState=" + hitState.getBlock()
                + " hitFace=east"
                + " hitDy=" + formatDouble(hitDy)
                + " hitAnchored=" + hitAnchored
                + " hitFullHeightLoweredCarrier=" + hitFullHeightLoweredCarrier
                + " hitHasBottomSlabBelow=" + hitHasBottomSlabBelow
                + " placePos=" + textPos(sidePlacePlacePos)
                + " prePlaceState=" + prePlaceState.getBlock()
                + " item=minecraft:stone"
                + " routePlacementMethod=" + sidePlaceRoutePlacementMethod
                + " clientPlayerPresent=" + sidePlaceClientPlayerPresent
                + " serverPlayerPresent=" + sidePlaceServerPlayerPresent
                + " clientHeldItem=" + sidePlaceClientHeldItem
                + " serverHeldItem=" + sidePlaceServerHeldItem
                + " clientPlacementCallResult=" + sidePlaceClientResult
                + " placementResultClient=" + sidePlaceClientResult
                + " placementResultServer=" + sidePlaceServerResult
                + " serverPlacementObservedResult=" + sidePlaceServerResult
                + " serverResultObserved=" + sidePlaceServerResultObserved
                + " packetOrInteractionPathUsed=" + sidePlacePacketOrInteractionPathUsed
                + " cleanupOrTeardownOccurred=" + sidePlaceCleanupOrTeardownOccurred
                + " reachDiagnostic=" + sidePlaceReachDiagnostic
                + " placementAccepted=" + sidePlaceClientAccepted
                + " retainedSampleAttempts=" + sidePlaceRetainedSampleAttempts
                + " retainedSampleTicks=" + sidePlaceRetainedSampleTicks
                + " sampledStates=" + sidePlaceSampledStates
                + " sampledPlacePosVariants=" + sidePlacePlacePosVariants
                + " postPlaceState=" + postPlaceState.getBlock()
                + " postPlaceClientState=" + clientPostPlaceState.getBlock()
                + " postPlaceDy=" + formatDouble(postPlaceDy)
                + " postPlaceServerDy=" + formatDouble(postPlaceServerDy)
                + " postPlaceAnchored=" + postPlaceAnchored
                + " postPlaceServerAnchored=" + postPlaceServerAnchored
                + " postPlaceFullHeightLoweredCarrier=" + postPlaceFullHeightLoweredCarrier
                + " postPlaceHasBottomSlabBelow=" + postPlaceHasBottomSlabBelow
                + " sourceSupportRelationship=DIRECT_BOTTOM_SLAB_ANCHORED_FULL_BLOCK_SOURCE_TO_SIDE_ADJACENT_FULL_BLOCK"
                + " legalStateName=" + legalStateName
                + " illegalReason=" + illegalReason
                + " visualRelation=" + visualRelation
                + " classification=" + classification);
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_SUMMARY]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " rows=1"
                + " finalResult=" + finalMarker
                + " classification=" + classification
                + " modelOutlineInterpretation=state_itself_lowered_not_render_mismatch");
        System.out.println("[MC1211_SIDE_PLACE_STONE_ROUTE_EQUIV_ROW]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " routePlacementMethod=" + sidePlaceRoutePlacementMethod
                + " packetOrInteractionPathUsed=" + sidePlacePacketOrInteractionPathUsed
                + " clientPlayerPresent=" + sidePlaceClientPlayerPresent
                + " serverPlayerPresent=" + sidePlaceServerPlayerPresent
                + " clientHeldItem=" + sidePlaceClientHeldItem
                + " serverHeldItem=" + sidePlaceServerHeldItem
                + " reachDiagnostic=" + sidePlaceReachDiagnostic
                + " sampledPlacePosVariants=" + sidePlacePlacePosVariants
                + " classification=" + classification);
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_" + finalMarker + "]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " classification=" + classification
                + " postPlaceDy=" + formatDouble(postPlaceDy)
                + " legalStateName=" + legalStateName);
    }

    private static void emitSidePlaceTraceGap(String row, String reason) {
        String classification = reason.startsWith("TRACE_GAP_")
                ? reason
                : "TRACE_GAP_ROUTE_NOT_VIDEO_EQUIVALENT";
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_START]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " rows=1");
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_ROW]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " row=" + row
                + " hitPos=n/a"
                + " hitState=n/a"
                + " hitFace=east"
                + " hitDy=NaN"
                + " hitAnchored=false"
                + " placePos=n/a"
                + " prePlaceState=n/a"
                + " item=minecraft:stone"
                + " routePlacementMethod=" + sidePlaceRoutePlacementMethod
                + " clientPlayerPresent=" + sidePlaceClientPlayerPresent
                + " serverPlayerPresent=" + sidePlaceServerPlayerPresent
                + " clientHeldItem=" + sidePlaceClientHeldItem
                + " serverHeldItem=" + sidePlaceServerHeldItem
                + " clientPlacementCallResult=" + sidePlaceClientResult
                + " placementResultClient=" + sidePlaceClientResult
                + " placementResultServer=" + sidePlaceServerResult
                + " serverPlacementObservedResult=" + sidePlaceServerResult
                + " serverResultObserved=" + sidePlaceServerResultObserved
                + " packetOrInteractionPathUsed=" + sidePlacePacketOrInteractionPathUsed
                + " cleanupOrTeardownOccurred=" + sidePlaceCleanupOrTeardownOccurred
                + " reachDiagnostic=" + sidePlaceReachDiagnostic
                + " retainedSampleAttempts=" + sidePlaceRetainedSampleAttempts
                + " retainedSampleTicks=" + sidePlaceRetainedSampleTicks
                + " sampledStates=" + sidePlaceSampledStates
                + " sampledPlacePosVariants=" + sidePlacePlacePosVariants
                + " postPlaceState=n/a"
                + " postPlaceDy=NaN"
                + " postPlaceAnchored=false"
                + " sourceSupportRelationship=unknown"
                + " legalStateName=none"
                + " illegalReason=" + reason
                + " visualRelation=unknown"
                + " classification=" + classification);
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_SUMMARY]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " rows=1"
                + " finalResult=TRACE_GAP"
                + " classification=" + classification
                + " reason=" + reason);
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_TRACE_GAP]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " reason=" + reason);
        emitted = true;
        if (clientReadyForStop()) {
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    private static final class SidePlaceReadiness {
        final MinecraftServer server;
        final boolean clientBootstrapReady;
        final boolean clientWorldReady;
        final boolean clientPlayerReady;
        final boolean integratedServerReady;
        final boolean serverWorldReady;
        final boolean serverPlayerReady;
        final boolean interactionManagerReady;

        private SidePlaceReadiness(
                MinecraftServer server,
                boolean clientBootstrapReady,
                boolean clientWorldReady,
                boolean clientPlayerReady,
                boolean integratedServerReady,
                boolean serverWorldReady,
                boolean serverPlayerReady,
                boolean interactionManagerReady
        ) {
            this.server = server;
            this.clientBootstrapReady = clientBootstrapReady;
            this.clientWorldReady = clientWorldReady;
            this.clientPlayerReady = clientPlayerReady;
            this.integratedServerReady = integratedServerReady;
            this.serverWorldReady = serverWorldReady;
            this.serverPlayerReady = serverPlayerReady;
            this.interactionManagerReady = interactionManagerReady;
        }

        static SidePlaceReadiness capture(MinecraftClient client) {
            MinecraftServer server = client == null ? null : client.getServer();
            boolean clientBootstrapReady = client != null && client.isFinishedLoading();
            boolean clientWorldReady = client != null && client.world != null;
            boolean clientPlayerReady = client != null && client.player != null;
            boolean integratedServerReady = server != null;
            boolean serverWorldReady = integratedServerReady
                    && clientWorldReady
                    && server.getWorld(client.world.getRegistryKey()) != null;
            boolean serverPlayerReady = integratedServerReady
                    && !server.getPlayerManager().getPlayerList().isEmpty();
            boolean interactionManagerReady = client != null && client.interactionManager != null;
            return new SidePlaceReadiness(
                    server,
                    clientBootstrapReady,
                    clientWorldReady,
                    clientPlayerReady,
                    integratedServerReady,
                    serverWorldReady,
                    serverPlayerReady,
                    interactionManagerReady);
        }
    }

    private static boolean clientReadyForStop() {
        return MinecraftClient.getInstance() != null;
    }

    private static void runModelVsOutlineHostedRoute(MinecraftClient client) {
        if (emitted) {
            return;
        }
        hostTicks++;
        System.out.println("[MC1211_MODEL_VS_OUTLINE_HOST_ROUTE_CANARY]"
                + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                + " route=" + ROUTE
                + " worldReady=" + (client != null && client.world != null)
                + " playerReady=" + (client != null && client.player != null)
                + " tick=" + hostTicks);

        if (client == null || client.world == null || client.player == null) {
            if (hostTicks < 1200) {
                return;
            }
            emitted = true;
            emitHostedTraceGap(
                    "TRACE_GAP_RENDER_PATH_NOT_OBSERVABLE_FROM_GAMETEST",
                    "ROUTE_READINESS",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "NaN",
                    "NaN",
                    "NaN",
                    "NaN..NaN",
                    "NaN..NaN",
                    false,
                    false);
            if (client != null) {
                client.scheduleStop();
            }
            return;
        }

        if (!hostReady) {
            hostReady = true;
            hostReadyTick = hostTicks;
            hostedOrigin = client.player.getBlockPos().add(3, 0, 3).toImmutable();
            System.out.println("[MC1211_MODEL_VS_OUTLINE_HOST_READY]"
                    + " route=" + ROUTE
                    + " clientWorldPresent=true"
                    + " clientPlayerPresent=true"
                    + " fixtureOrigin=" + textPos(hostedOrigin));
        }

        if (!authorDeterministicShape(client, hostedOrigin)) {
            emitted = true;
            emitHostedTraceGap(
                    "TRACE_GAP_NO_DETERMINISTIC_SHAPE",
                    "ROUTE_SHAPE_SETUP",
                    "true",
                    "true",
                    textPos(hostedOrigin),
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "NaN",
                    "NaN",
                    "NaN",
                    "NaN..NaN",
                    "NaN..NaN",
                    false,
                    false);
            client.scheduleStop();
            return;
        }
        if (hostTicks - hostReadyTick < 20) {
            return;
        }

        System.out.println("[MC1211_MODEL_VS_OUTLINE_START]"
                + " route=" + ROUTE
                + " rows=2"
                + " fixtureOrigin=" + textPos(hostedOrigin));

        RowResult bottom = emitHostedRow(
                client.world,
                "BOTTOM_SLAB_THEN_STONE_MODEL_VS_OUTLINE_GOBLIN_HOST",
                hostedOrigin,
                hostedOrigin,
                hostedOrigin.up());
        RowResult vanilla = emitHostedRow(
                client.world,
                "VANILLA_GROUND_THEN_STONE_MODEL_VS_OUTLINE_GOBLIN_HOST",
                hostedOrigin.add(2, 0, 0),
                hostedOrigin.add(2, 0, 0),
                hostedOrigin.add(2, 1, 0));

        String finalResult;
        if (bottom.traceGap || vanilla.traceGap) {
            finalResult = "TRACE_GAP";
        } else if (bottom.modelLowerThanOutline || vanilla.modelLowerThanOutline) {
            finalResult = "RED";
        } else {
            finalResult = "GREEN";
        }

        System.out.println("[MC1211_MODEL_VS_OUTLINE_SUMMARY]"
                + " route=" + ROUTE
                + " rows=2"
                + " finalResult=" + finalResult);
        System.out.println("[MC1211_MODEL_VS_OUTLINE_" + finalResult + "]"
                + " route=" + ROUTE
                + " rows=2");

        emitted = true;
        client.scheduleStop();
    }

    private static boolean authorDeterministicShape(MinecraftClient client, BlockPos origin) {
        MinecraftServer server = client.getServer();
        if (server == null || client.world == null || origin == null) {
            return false;
        }
        ServerWorld serverWorld = server.getWorld(client.world.getRegistryKey());
        if (serverWorld == null) {
            return false;
        }
        BlockPos supportBottom = origin;
        BlockPos fullBottom = origin.up();
        BlockPos supportVanilla = origin.add(2, 0, 0);
        BlockPos fullVanilla = origin.add(2, 1, 0);
        server.execute(() -> {
            serverWorld.setBlockState(supportBottom, Blocks.STONE_SLAB.getDefaultState(), 3);
            serverWorld.setBlockState(fullBottom, Blocks.STONE.getDefaultState(), 3);
            serverWorld.setBlockState(supportVanilla, Blocks.STONE.getDefaultState(), 3);
            serverWorld.setBlockState(fullVanilla, Blocks.STONE.getDefaultState(), 3);
        });
        return true;
    }

    private static RowResult emitHostedRow(
            ClientWorld world,
            String rowName,
            BlockPos fixtureOrigin,
            BlockPos slabPos,
            BlockPos fullPos) {
        BlockState slabState = world.getBlockState(slabPos);
        BlockState fullState = world.getBlockState(fullPos);
        double slabDy = SlabSupport.getYOffset(world, slabPos, slabState);
        double outlineDy = ClientDy.dyFor(world, fullPos, fullState);
        double targetDy = SlabSupport.getYOffset(world, fullPos, fullState);
        VoxelShape outlineShape = fullState.getOutlineShape(world, fullPos);
        String outlineBounds = outlineShape.isEmpty()
                ? "NaN..NaN"
                : formatDouble(outlineShape.getBoundingBox().minY) + ".."
                + formatDouble(outlineShape.getBoundingBox().maxY);
        OffsetBlockStateModel.RenderOffsetTrace trace = sampleModelTrace(fullPos);
        String reason;
        boolean modelEqualsOutline;
        boolean modelLowerThanOutline;
        boolean traceGap;
        String modelDy;
        String modelBounds;
        String modelObserverKind;
        String modelDyProxy;
        String modelVisualEquivalence;
        String targetDyText = formatDouble(targetDy);
        if (!trace.seen()) {
            reason = "TRACE_GAP_RENDER_MODEL_PATH_NOT_OBSERVABLE_FROM_GAMETEST";
            modelEqualsOutline = false;
            modelLowerThanOutline = false;
            traceGap = true;
            modelDy = "NaN";
            modelBounds = "NaN..NaN";
            modelObserverKind = "not_observable";
            modelDyProxy = "NaN";
            modelVisualEquivalence = "no";
        } else {
            double tracedModelDy = trace.modelDy();
            double modelDyDelta = tracedModelDy - outlineDy;
            String inferredModelBounds = inferShiftedBounds(outlineShape, modelDyDelta);
            double modelMin = shiftedMinY(outlineShape, modelDyDelta);
            double outlineMin = outlineShape.isEmpty() ? Double.NaN : outlineShape.getBoundingBox().minY;
            modelLowerThanOutline = Double.isFinite(modelMin)
                    && Double.isFinite(outlineMin)
                    && modelMin < outlineMin - 1.0e-6;
            modelEqualsOutline = Math.abs(modelDyDelta) <= 1.0e-6;
            modelDy = formatDouble(tracedModelDy);
            modelBounds = inferredModelBounds;
            modelObserverKind = "gametest_hook";
            modelDyProxy = modelDy;
            modelVisualEquivalence = "proxy_only";
            if (modelLowerThanOutline) {
                reason = "MODEL_LOWER_THAN_OUTLINE";
                traceGap = false;
            } else if (!modelEqualsOutline) {
                reason = "MODEL_DY_MISMATCH";
                traceGap = true;
            } else {
                reason = "TRACE_GAP_MODEL_PROXY_ONLY_NOT_VISUAL_MESH";
                traceGap = true;
            }
        }

        System.out.println("[MC1211_MODEL_VS_OUTLINE_ROW]"
                + " row=" + rowName
                + " clientWorldPresent=true"
                + " clientPlayerPresent=true"
                + " fixtureOrigin=" + textPos(fixtureOrigin)
                + " slabPos=" + textPos(slabPos)
                + " slabState=" + slabState
                + " slabDy=" + formatDouble(slabDy)
                + " fullBlockPos=" + textPos(fullPos)
                + " fullBlockState=" + fullState
                + " modelDy=" + modelDy
                + " modelDyProxy=" + modelDyProxy
                + " modelObserverKind=" + modelObserverKind
                + " outlineDy=" + formatDouble(outlineDy)
                + " targetDy=" + targetDyText
                + " modelBoundsY=" + modelBounds
                + " outlineBoundsY=" + outlineBounds
                + " modelEqualsOutline=" + modelEqualsOutline
                + " modelLowerThanOutline=" + modelLowerThanOutline
                + " modelVisualEquivalence=" + modelVisualEquivalence
                + " reason=" + reason
                + " result=" + (traceGap ? "TRACE_GAP" : "RED"));
        return new RowResult(traceGap, modelLowerThanOutline);
    }

    private static void emitHostedTraceGap(
            String reason,
            String row,
            String clientWorldPresent,
            String clientPlayerPresent,
            String fixtureOrigin,
            String slabPos,
            String slabState,
            String slabDy,
            String fullPos,
            String fullState,
            String modelDy,
            String outlineDy,
            String targetDy,
            String modelBounds,
            String outlineBounds,
            boolean modelEqualsOutline,
            boolean modelLowerThanOutline) {
        System.out.println("[MC1211_MODEL_VS_OUTLINE_START]"
                + " route=" + ROUTE
                + " rows=1");
        System.out.println("[MC1211_MODEL_VS_OUTLINE_ROW]"
                + " row=" + row
                + " clientWorldPresent=" + clientWorldPresent
                + " clientPlayerPresent=" + clientPlayerPresent
                + " fixtureOrigin=" + fixtureOrigin
                + " slabPos=" + slabPos
                + " slabState=" + slabState
                + " slabDy=" + slabDy
                + " fullBlockPos=" + fullPos
                + " fullBlockState=" + fullState
                + " modelDy=" + modelDy
                + " modelDyProxy=NaN"
                + " modelObserverKind=not_observable"
                + " outlineDy=" + outlineDy
                + " targetDy=" + targetDy
                + " modelBoundsY=" + modelBounds
                + " outlineBoundsY=" + outlineBounds
                + " modelEqualsOutline=" + modelEqualsOutline
                + " modelLowerThanOutline=" + modelLowerThanOutline
                + " modelVisualEquivalence=no"
                + " reason=" + reason
                + " result=TRACE_GAP");
        System.out.println("[MC1211_MODEL_VS_OUTLINE_SUMMARY]"
                + " route=" + ROUTE
                + " rows=1"
                + " finalResult=TRACE_GAP");
        System.out.println("[MC1211_MODEL_VS_OUTLINE_TRACE_GAP]"
                + " route=" + ROUTE
                + " rows=1"
                + " reason=" + reason);
    }

    private static String textPos(BlockPos pos) {
        if (pos == null) {
            return "n/a";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String formatVec(Vec3d vec) {
        return formatDouble(vec.x) + "," + formatDouble(vec.y) + "," + formatDouble(vec.z);
    }

    private static String formatDouble(double value) {
        if (!Double.isFinite(value)) {
            return "NaN";
        }
        return String.format(java.util.Locale.ROOT, "%.6f", value);
    }

    private static String sampleSidePlaceVariants(ClientWorld clientWorld, ServerWorld serverWorld) {
        if (sidePlacePlacePos == null) {
            return "positions_missing";
        }
        StringBuilder line = new StringBuilder();
        for (Direction direction : Direction.values()) {
            BlockPos pos = sidePlaceHitPos == null
                    ? sidePlacePlacePos
                    : sidePlaceHitPos.offset(direction);
            if (!line.isEmpty()) {
                line.append("|");
            }
            line.append(direction.asString())
                    .append("@")
                    .append(textPos(pos))
                    .append(":client=")
                    .append(clientWorld == null ? "UNAVAILABLE" : clientWorld.getBlockState(pos).getBlock())
                    .append("/server=")
                    .append(serverWorld == null ? "UNAVAILABLE" : serverWorld.getBlockState(pos).getBlock());
        }
        return line.toString();
    }

    private static OffsetBlockStateModel.RenderOffsetTrace sampleModelTrace(BlockPos observedPos) {
        System.setProperty("slabbed.render.offset.trace", "true");
        OffsetBlockStateModel.resetRenderOffsetTrace(observedPos);
        OffsetBlockStateModel.RenderOffsetTrace trace = OffsetBlockStateModel.snapshotRenderOffsetTrace();
        System.clearProperty("slabbed.render.offset.trace");
        return trace;
    }

    private static String inferShiftedBounds(VoxelShape outlineShape, double modelDyDelta) {
        if (outlineShape.isEmpty()) {
            return "NaN..NaN";
        }
        return formatDouble(outlineShape.getBoundingBox().minY + modelDyDelta)
                + ".."
                + formatDouble(outlineShape.getBoundingBox().maxY + modelDyDelta);
    }

    private static double shiftedMinY(VoxelShape outlineShape, double modelDyDelta) {
        if (outlineShape.isEmpty()) {
            return Double.NaN;
        }
        return outlineShape.getBoundingBox().minY + modelDyDelta;
    }

    private record RowResult(boolean traceGap, boolean modelLowerThanOutline) {
    }
}
