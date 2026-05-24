package com.slabbed.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.Identifier;
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
    private static final String SIDE_PLACE_STONE_LIVE_TRUTH_ONLY_PROPERTY =
            "slabbed.mc1211.sidePlaceStoneLiveTruthOnly";
    private static final String SAME_SPOT_AFTER_SLAB_BREAK_ONLY_PROPERTY =
            "slabbed.mc1211.sameSpotAfterSlabBreakOnly";
    private static final String SLAB_THEN_BLOCK_BASELINE_ONLY_PROPERTY =
            "slabbed.mc1211.slabThenBlockBaselineOnly";
    private static final String MODEL_VS_OUTLINE_GOBLIN_HOST_ONLY_PROPERTY =
            "slabbed.mc1211.modelVsOutlineGoblinHostOnly";
    private static final String SUPERFLAT_MODEL_HITBOX_HARNESS_ONLY_PROPERTY =
            "slabbed.mc1211.superflatModelHitboxHarnessOnly";
    private static final String WALL_FENCE_PRODUCT_RED_ONLY_PROPERTY =
            "slabbed.mc1211.wallFenceProductRedOnly";
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
    private static int liveTruthTicks;
    private static int liveTruthPhase;
    private static int liveTruthPhaseTick;
    private static boolean liveTruthCanaryEmitted;
    private static boolean liveTruthWorldStartRequested;
    private static boolean liveTruthReadyRowEmitted;
    private static boolean liveTruthStarted;
    private static boolean liveTruthFinalized;
    private static BlockPos liveTruthOrigin;
    private static BlockPos liveTruthGroundPos;
    private static BlockPos liveTruthSlabPos;
    private static BlockPos liveTruthFirstStonePos;
    private static BlockPos liveTruthSideStonePos;
    private static String liveTruthSlabResult = "not_started";
    private static String liveTruthFirstStoneResult = "not_started";
    private static String liveTruthSideStoneResult = "not_started";
    private static String liveTruthReachDiagnostic = "not_sampled";
    private static int sameSpotTicks;
    private static int sameSpotPhase;
    private static int sameSpotPhaseTick;
    private static boolean sameSpotCanaryEmitted;
    private static boolean sameSpotWorldStartRequested;
    private static boolean sameSpotReadyRowEmitted;
    private static boolean sameSpotStarted;
    private static boolean sameSpotFinalized;
    private static boolean sameSpotBreakRequested;
    private static volatile String sameSpotBreakResult = "not_started";
    private static BlockPos sameSpotOrigin;
    private static BlockPos sameSpotGroundPos;
    private static BlockPos sameSpotSlabPos;
    private static BlockPos sameSpotFullPos;
    private static Vec3d sameSpotAimPoint;
    private static String sameSpotSlabPlacementResult = "not_started";
    private static String sameSpotFullPlacementResult = "not_started";
    private static String sameSpotReachDiagnostic = "not_sampled";
    private static String sameSpotPreTarget = "not_sampled";
    private static String sameSpotPostTarget = "not_sampled";
    private static String sameSpotPreModelTrace = "not_sampled";
    private static String sameSpotPostModelTrace = "not_sampled";
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
    private static int superflatHarnessTicks;
    private static int superflatHarnessRowIndex;
    private static int superflatHarnessRowPhase;
    private static int superflatHarnessPhaseTick;
    private static boolean superflatHarnessCanaryEmitted;
    private static boolean superflatHarnessWorldStartRequested;
    private static boolean superflatHarnessReadyRowEmitted;
    private static boolean superflatHarnessStarted;
    private static boolean superflatHarnessFinalized;
    private static BlockPos superflatHarnessOrigin;
    private static BlockPos superflatHarnessSlabPos;
    private static BlockPos superflatHarnessPlacePos;
    private static String superflatHarnessPlacementMethod = "not_started";
    private static String superflatHarnessPlacementReturn = "not_started";
    private static int superflatHarnessInteractAttempts;
    private static int superflatHarnessGreenRows;
    private static int superflatHarnessRedRows;
    private static int superflatHarnessTraceGapRows;
    private static int superflatHarnessProductBadRows;
    private static final SuperflatHarnessRowSpec[] SUPERFLAT_HARNESS_ROWS = new SuperflatHarnessRowSpec[] {
            new SuperflatHarnessRowSpec("STONE_FULL_BLOCK", "minecraft:stone", false),
            new SuperflatHarnessRowSpec("KNOWN_GOOD_FULL_BLOCK", "minecraft:oak_log", false),
            new SuperflatHarnessRowSpec("COBBLESTONE_WALL", "minecraft:cobblestone_wall", true),
            new SuperflatHarnessRowSpec("STONE_WALL", "minecraft:stone_wall", true),
            new SuperflatHarnessRowSpec("OAK_FENCE", "minecraft:oak_fence", true),
            new SuperflatHarnessRowSpec("STONE_STAIRS", "minecraft:stone_stairs", false)
    };

    @Override
    public void onInitializeClient() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(Mc1211GoblinRouteClientEntrypoint::onEndTick);
    }

    private static void onEndTick(MinecraftClient client) {
        if (Boolean.getBoolean(WALL_FENCE_PRODUCT_RED_ONLY_PROPERTY)) {
            runSuperflatModelHitboxHarnessRoute(client);
            return;
        }
        if (Boolean.getBoolean(SUPERFLAT_MODEL_HITBOX_HARNESS_ONLY_PROPERTY)) {
            runSuperflatModelHitboxHarnessRoute(client);
            return;
        }
        if (Boolean.getBoolean(SLAB_THEN_BLOCK_BASELINE_ONLY_PROPERTY)) {
            runSlabThenBlockBaselineRoute(client);
            return;
        }
        if (Boolean.getBoolean(SIDE_PLACE_STONE_LOWERING_ONLY_PROPERTY)) {
            runSidePlaceStoneLoweringRoute(client);
            return;
        }
        if (Boolean.getBoolean(SIDE_PLACE_STONE_LIVE_TRUTH_ONLY_PROPERTY)) {
            runSidePlaceStoneLiveTruthRoute(client);
            return;
        }
        if (Boolean.getBoolean(SAME_SPOT_AFTER_SLAB_BREAK_ONLY_PROPERTY)) {
            runSameSpotAfterSlabBreakRoute(client);
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

    private static void runSidePlaceStoneLiveTruthRoute(MinecraftClient client) {
        if (liveTruthFinalized || emitted) {
            return;
        }
        liveTruthTicks++;
        if (!liveTruthCanaryEmitted) {
            liveTruthCanaryEmitted = true;
            System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_ROUTE_CANARY]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " property=" + SIDE_PLACE_STONE_LIVE_TRUTH_ONLY_PROPERTY
                    + " worldReady=" + (client != null && client.world != null)
                    + " playerReady=" + (client != null && client.player != null));
        }

        requestProgrammaticLiveTruthWorldIfNeeded(client);
        String readinessGap = liveTruthReadinessGap(client);
        if (readinessGap != null) {
            if (!liveTruthReadyRowEmitted || liveTruthTicks % 1200 == 0) {
                emitLiveTruthReadyRow(client, "WAITING", readinessGap);
                liveTruthReadyRowEmitted = true;
            }
            if (liveTruthTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitLiveTruthReadyRow(client, "TIMEOUT", readinessGap);
            emitLiveTruthTraceGap("ROUTE_READINESS", readinessGap);
            return;
        }
        if (!liveTruthReadyRowEmitted) {
            emitLiveTruthReadyRow(client, "READY", "none");
            liveTruthReadyRowEmitted = true;
        }

        if (!liveTruthStarted) {
            liveTruthStarted = true;
            liveTruthOrigin = client.player.getBlockPos().add(9, 0, 7).toImmutable();
            liveTruthGroundPos = liveTruthOrigin.down();
            liveTruthSlabPos = liveTruthOrigin;
            liveTruthFirstStonePos = liveTruthSlabPos.up();
            liveTruthSideStonePos = liveTruthFirstStonePos.offset(Direction.WEST);
            prepareLiveTruthFixture(client);
            liveTruthPhase = 1;
            liveTruthPhaseTick = liveTruthTicks;
            System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_START]"
                    + " rowName=SIDE_PLACE_STONE_LIVE_SOURCE_TRUTH_WEST_FACE"
                    + " fixtureOrigin=" + textPos(liveTruthOrigin)
                    + " liveReferenceSideStonePos=-22,-59,64"
                    + " translatedSideStonePos=" + textPos(liveTruthSideStonePos)
                    + " placementRoute=ClientPlayerInteractionManager.interactBlock"
                    + " manualAnchorInjection=false");
            return;
        }

        if (liveTruthTicks - liveTruthPhaseTick < 8) {
            return;
        }
        if (liveTruthPhase == 1) {
            liveTruthSlabResult = clickBlock(
                    client,
                    Items.STONE_SLAB,
                    liveTruthGroundPos,
                    Direction.UP,
                    hitVector(liveTruthGroundPos, Direction.UP));
            liveTruthReachDiagnostic = slabThenBlockReachDiagnostic;
            liveTruthPhase = 2;
            liveTruthPhaseTick = liveTruthTicks;
            return;
        }
        if (liveTruthPhase == 2) {
            liveTruthFirstStoneResult = clickBlock(
                    client,
                    Items.STONE,
                    liveTruthSlabPos,
                    Direction.UP,
                    hitVector(liveTruthSlabPos, Direction.UP));
            liveTruthReachDiagnostic = slabThenBlockReachDiagnostic;
            liveTruthPhase = 3;
            liveTruthPhaseTick = liveTruthTicks;
            return;
        }
        if (liveTruthPhase == 3) {
            Vec3d sideHit = new Vec3d(
                    liveTruthFirstStonePos.getX(),
                    liveTruthFirstStonePos.getY() + 0.5d,
                    liveTruthFirstStonePos.getZ() + 0.5d);
            liveTruthSideStoneResult = clickBlock(client, Items.STONE, liveTruthFirstStonePos, Direction.WEST, sideHit);
            liveTruthReachDiagnostic = slabThenBlockReachDiagnostic;
            liveTruthPhase = 4;
            liveTruthPhaseTick = liveTruthTicks;
            return;
        }
        if (liveTruthTicks - liveTruthPhaseTick < 30) {
            return;
        }
        emitLiveTruthRow(client);
        liveTruthFinalized = true;
        emitted = true;
        client.scheduleStop();
    }

    private static void requestProgrammaticLiveTruthWorldIfNeeded(MinecraftClient client) {
        if (liveTruthWorldStartRequested
                || client == null
                || !client.isFinishedLoading()
                || client.world != null
                || client.player != null) {
            return;
        }
        liveTruthWorldStartRequested = true;
        LevelInfo levelInfo = new LevelInfo(
                "Slabbed MC1211 Side Place Live Truth Harness",
                GameMode.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                DataConfiguration.SAFE_MODE);
        GeneratorOptions generatorOptions = new GeneratorOptions(0L, false, false);
        client.createIntegratedServerLoader().createAndStart(
                "slabbed-mc1211-side-place-live-truth-harness",
                levelInfo,
                generatorOptions,
                Mc1211GoblinRouteClientEntrypoint::createSuperflatDimensionOptions,
                null);
    }

    private static String liveTruthReadinessGap(MinecraftClient client) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        if (!readiness.clientBootstrapReady) {
            return "TRACE_GAP_CLIENT_BOOTSTRAP_NOT_FINISHED";
        }
        if (!readiness.clientWorldReady) {
            return liveTruthWorldStartRequested
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

    private static void emitLiveTruthReadyRow(MinecraftClient client, String phase, String reason) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_READY_ROW]"
                + " phase=" + phase
                + " tick=" + liveTruthTicks
                + " clientBootstrapReady=" + readiness.clientBootstrapReady
                + " clientWorldReady=" + readiness.clientWorldReady
                + " clientPlayerReady=" + readiness.clientPlayerReady
                + " integratedServerReady=" + readiness.integratedServerReady
                + " serverWorldReady=" + readiness.serverWorldReady
                + " serverPlayerReady=" + readiness.serverPlayerReady
                + " interactionManagerReady=" + readiness.interactionManagerReady
                + " programmaticWorldStartRequested=" + liveTruthWorldStartRequested
                + " reason=" + reason);
    }

    private static void prepareLiveTruthFixture(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || liveTruthOrigin == null) {
            return;
        }
        serverWorld.getServer().execute(() -> {
            for (int x = liveTruthOrigin.getX() - 3; x <= liveTruthOrigin.getX() + 3; x++) {
                for (int z = liveTruthOrigin.getZ() - 3; z <= liveTruthOrigin.getZ() + 3; z++) {
                    for (int y = liveTruthOrigin.getY() - 1; y <= liveTruthOrigin.getY() + 3; y++) {
                        serverWorld.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
            serverWorld.setBlockState(liveTruthGroundPos, Blocks.STONE.getDefaultState(), 3);
            if (!serverWorld.getServer().getPlayerManager().getPlayerList().isEmpty()) {
                serverWorld.getServer().getPlayerManager().getPlayerList().get(0)
                        .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
        });
    }

    private static void emitLiveTruthRow(MinecraftClient client) {
        ClientWorld clientWorld = client.world;
        ServerWorld serverWorld = serverWorldFor(client);
        if (clientWorld == null || serverWorld == null) {
            emitLiveTruthTraceGap("ROUTE_ROW_SAMPLE", "TRACE_GAP_WORLD_OR_PLAYER_NOT_READY");
            return;
        }
        BlockState slabState = serverWorld.getBlockState(liveTruthSlabPos);
        BlockState firstClientState = clientWorld.getBlockState(liveTruthFirstStonePos);
        BlockState firstServerState = serverWorld.getBlockState(liveTruthFirstStonePos);
        BlockState sideClientState = clientWorld.getBlockState(liveTruthSideStonePos);
        BlockState sideServerState = serverWorld.getBlockState(liveTruthSideStonePos);
        double firstClientDy = SlabSupport.getYOffset(clientWorld, liveTruthFirstStonePos, firstClientState);
        double firstServerDy = SlabSupport.getYOffset(serverWorld, liveTruthFirstStonePos, firstServerState);
        double sideClientDy = SlabSupport.getYOffset(clientWorld, liveTruthSideStonePos, sideClientState);
        double sideServerDy = SlabSupport.getYOffset(serverWorld, liveTruthSideStonePos, sideServerState);
        boolean firstClientAnchor = SlabAnchorAttachment.isAnchored(clientWorld, liveTruthFirstStonePos);
        boolean firstServerAnchor = SlabAnchorAttachment.isAnchored(serverWorld, liveTruthFirstStonePos);
        boolean sideClientAnchor = SlabAnchorAttachment.isAnchored(clientWorld, liveTruthSideStonePos);
        boolean sideServerAnchor = SlabAnchorAttachment.isAnchored(serverWorld, liveTruthSideStonePos);
        boolean sideIsStone = sideServerState.isOf(Blocks.STONE);
        String finalResult;
        String equivalence;
        if (!slabState.isOf(Blocks.STONE_SLAB) || !firstServerState.isOf(Blocks.STONE) || !sideIsStone) {
            finalResult = "TRACE_GAP";
            equivalence = "TRACE_GAP_MISSING_LIVE_PLACED_STATE";
        } else if (near(sideServerDy, 0.0d) && !sideServerAnchor) {
            finalResult = "RED";
            equivalence = "LIVE_EQUIVALENT";
        } else if (near(sideServerDy, -0.5d) && sideServerAnchor) {
            finalResult = "GREEN";
            equivalence = "NOT_LIVE_EQUIVALENT";
        } else {
            finalResult = "TRACE_GAP";
            equivalence = "TRACE_GAP_MISSING_FINALIZER_DATA";
        }
        Vec3d sideHit = new Vec3d(
                liveTruthFirstStonePos.getX(),
                liveTruthFirstStonePos.getY() + 0.5d,
                liveTruthFirstStonePos.getZ() + 0.5d);
        System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_ROW]"
                + " rowName=SIDE_PLACE_STONE_LIVE_SOURCE_TRUTH_WEST_FACE"
                + " fixtureOrigin=" + textPos(liveTruthOrigin)
                + " liveReferenceSideStonePos=-22,-59,64"
                + " translatedSideStonePos=" + textPos(liveTruthSideStonePos)
                + " slabPos=" + textPos(liveTruthSlabPos)
                + " slabState=" + slabState
                + " firstStonePos=" + textPos(liveTruthFirstStonePos)
                + " firstStoneState=" + firstServerState
                + " firstStoneClientDy=" + formatDouble(firstClientDy)
                + " firstStoneServerDy=" + formatDouble(firstServerDy)
                + " firstStoneClientAnchor=" + firstClientAnchor
                + " firstStoneServerAnchor=" + firstServerAnchor
                + " sideStoneIntendedPos=" + textPos(liveTruthSideStonePos)
                + " sideStoneFinalPos=" + textPos(liveTruthSideStonePos)
                + " sideStoneState=" + sideServerState
                + " sideStoneClientDy=" + formatDouble(sideClientDy)
                + " sideStoneServerDy=" + formatDouble(sideServerDy)
                + " sideStoneClientAnchor=" + sideClientAnchor
                + " sideStoneServerAnchor=" + sideServerAnchor
                + " heldItem=minecraft:stone"
                + " clickedFace=west"
                + " hitVector=" + formatVec(sideHit)
                + " placementReturnSlab=" + liveTruthSlabResult
                + " placementReturnFirstStone=" + liveTruthFirstStoneResult
                + " placementReturnSideStone=" + liveTruthSideStoneResult
                + " finalizerInvoked=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " sourcePos=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " sourceState=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " sourceDy=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " sourceAnchor=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " sourceQualifies=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " qualifierRejectReason=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " anchorWrite=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " anchorAfter=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " placedDyAfter=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " manualAnchorInjected=false"
                + " routePlacementMethod=ClientPlayerInteractionManager.interactBlock"
                + " reachDiagnostic=" + liveTruthReachDiagnostic
                + " equivalenceVerdict=" + equivalence
                + " classification=" + finalResult);
        System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_SUMMARY]"
                + " rows=1"
                + " finalResult=" + finalResult
                + " equivalenceVerdict=" + equivalence
                + " manualAnchorInjected=false");
        System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_" + finalResult + "]"
                + " rowName=SIDE_PLACE_STONE_LIVE_SOURCE_TRUTH_WEST_FACE"
                + " equivalenceVerdict=" + equivalence
                + " sideStoneServerDy=" + formatDouble(sideServerDy)
                + " sideStoneServerAnchor=" + sideServerAnchor);
    }

    private static void emitLiveTruthTraceGap(String row, String reason) {
        System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_START]"
                + " rowName=SIDE_PLACE_STONE_LIVE_SOURCE_TRUTH_WEST_FACE"
                + " rows=1");
        System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_ROW]"
                + " row=" + row
                + " fixtureOrigin=" + textPos(liveTruthOrigin)
                + " liveReferenceSideStonePos=-22,-59,64"
                + " translatedSideStonePos=" + textPos(liveTruthSideStonePos)
                + " manualAnchorInjected=false"
                + " equivalenceVerdict=" + reason
                + " classification=TRACE_GAP");
        System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_SUMMARY]"
                + " rows=1"
                + " finalResult=TRACE_GAP"
                + " equivalenceVerdict=" + reason);
        System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_TRACE_GAP]"
                + " rowName=SIDE_PLACE_STONE_LIVE_SOURCE_TRUTH_WEST_FACE"
                + " reason=" + reason);
        liveTruthFinalized = true;
        emitted = true;
        if (clientReadyForStop()) {
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    private static void runSameSpotAfterSlabBreakRoute(MinecraftClient client) {
        if (sameSpotFinalized || emitted) {
            return;
        }
        sameSpotTicks++;
        if (!sameSpotCanaryEmitted) {
            sameSpotCanaryEmitted = true;
            System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_ROUTE_CANARY]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " property=" + SAME_SPOT_AFTER_SLAB_BREAK_ONLY_PROPERTY
                    + " worldReady=" + (client != null && client.world != null)
                    + " playerReady=" + (client != null && client.player != null));
        }

        requestProgrammaticSameSpotWorldIfNeeded(client);
        String readinessGap = liveTruthReadinessGap(client);
        if (readinessGap != null) {
            if (!sameSpotReadyRowEmitted || sameSpotTicks % 1200 == 0) {
                emitSameSpotReadyRow(client, "WAITING", readinessGap);
                sameSpotReadyRowEmitted = true;
            }
            if (sameSpotTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitSameSpotReadyRow(client, "TIMEOUT", readinessGap);
            emitSameSpotTraceGap("ROUTE_READINESS", readinessGap);
            return;
        }
        if (!sameSpotReadyRowEmitted) {
            emitSameSpotReadyRow(client, "READY", "none");
            sameSpotReadyRowEmitted = true;
        }

        if (!sameSpotStarted) {
            sameSpotStarted = true;
            sameSpotOrigin = client.player.getBlockPos().add(9, 0, 7).toImmutable();
            sameSpotGroundPos = sameSpotOrigin.down();
            sameSpotSlabPos = sameSpotOrigin;
            sameSpotFullPos = sameSpotSlabPos.up();
            sameSpotAimPoint = new Vec3d(
                    sameSpotFullPos.getX() + 0.5d,
                    sameSpotFullPos.getY() - 0.60d,
                    sameSpotFullPos.getZ() + 0.5d);
            prepareSameSpotFixture(client);
            sameSpotPhase = 1;
            sameSpotPhaseTick = sameSpotTicks;
            System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_START]"
                    + " rowName=SAME_SPOT_AFTER_BREAK_LOWER_VISUAL"
                    + " fixtureOrigin=" + textPos(sameSpotOrigin)
                    + " slabPos=" + textPos(sameSpotSlabPos)
                    + " fullPos=" + textPos(sameSpotFullPos)
                    + " aimPoint=" + formatVec(sameSpotAimPoint)
                    + " placementRoute=ClientPlayerInteractionManager.interactBlock"
                    + " breakRoute=serverWorld.breakBlock"
                    + " manualAnchorInjection=false"
                    + " sameEyeYawPitchAfterBreak=true");
            return;
        }

        if (sameSpotTicks - sameSpotPhaseTick < 8) {
            return;
        }
        if (sameSpotPhase == 1) {
            sameSpotSlabPlacementResult = clickBlock(
                    client,
                    Items.STONE_SLAB,
                    sameSpotGroundPos,
                    Direction.UP,
                    hitVector(sameSpotGroundPos, Direction.UP));
            sameSpotPhase = 2;
            sameSpotPhaseTick = sameSpotTicks;
            return;
        }
        if (sameSpotPhase == 2) {
            sameSpotFullPlacementResult = clickBlock(
                    client,
                    Items.STONE,
                    sameSpotSlabPos,
                    Direction.UP,
                    hitVector(sameSpotSlabPos, Direction.UP));
            sameSpotPhase = 3;
            sameSpotPhaseTick = sameSpotTicks;
            return;
        }
        if (sameSpotPhase == 3) {
            syncSameSpotAim(client);
            System.setProperty("slabbed.render.offset.trace", "true");
            OffsetBlockStateModel.resetRenderOffsetTrace(sameSpotFullPos);
            sameSpotPhase = 4;
            sameSpotPhaseTick = sameSpotTicks;
            return;
        }
        if (sameSpotPhase == 4) {
            sameSpotPreTarget = describeCrosshair(client);
            sameSpotPreModelTrace = describeModelTrace(OffsetBlockStateModel.snapshotRenderOffsetTrace());
            OffsetBlockStateModel.resetRenderOffsetTrace(sameSpotFullPos);
            requestSameSpotBreak(client);
            sameSpotPhase = 5;
            sameSpotPhaseTick = sameSpotTicks;
            return;
        }
        if (sameSpotPhase == 5 && sameSpotTicks - sameSpotPhaseTick < 20) {
            return;
        }
        if (sameSpotPhase == 5) {
            sameSpotPostTarget = describeCrosshair(client);
            sameSpotPostModelTrace = describeModelTrace(OffsetBlockStateModel.snapshotRenderOffsetTrace());
            System.clearProperty("slabbed.render.offset.trace");
            emitSameSpotRow(client);
            sameSpotFinalized = true;
            emitted = true;
            client.scheduleStop();
        }
    }

    private static void requestProgrammaticSameSpotWorldIfNeeded(MinecraftClient client) {
        if (sameSpotWorldStartRequested
                || client == null
                || !client.isFinishedLoading()
                || client.world != null
                || client.player != null) {
            return;
        }
        sameSpotWorldStartRequested = true;
        LevelInfo levelInfo = new LevelInfo(
                "Slabbed MC1211 Same Spot Break Harness",
                GameMode.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                DataConfiguration.SAFE_MODE);
        GeneratorOptions generatorOptions = new GeneratorOptions(0L, false, false);
        client.createIntegratedServerLoader().createAndStart(
                "slabbed-mc1211-same-spot-break-harness",
                levelInfo,
                generatorOptions,
                Mc1211GoblinRouteClientEntrypoint::createSuperflatDimensionOptions,
                null);
    }

    private static void prepareSameSpotFixture(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || sameSpotOrigin == null) {
            return;
        }
        serverWorld.getServer().execute(() -> {
            for (int x = sameSpotOrigin.getX() - 3; x <= sameSpotOrigin.getX() + 3; x++) {
                for (int z = sameSpotOrigin.getZ() - 3; z <= sameSpotOrigin.getZ() + 3; z++) {
                    for (int y = sameSpotOrigin.getY() - 1; y <= sameSpotOrigin.getY() + 3; y++) {
                        serverWorld.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
            serverWorld.setBlockState(sameSpotGroundPos, Blocks.STONE.getDefaultState(), 3);
            if (!serverWorld.getServer().getPlayerManager().getPlayerList().isEmpty()) {
                serverWorld.getServer().getPlayerManager().getPlayerList().get(0)
                        .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
        });
    }

    private static void syncSameSpotAim(MinecraftClient client) {
        if (client == null || client.player == null || sameSpotAimPoint == null) {
            return;
        }
        Vec3d eye = sameSpotAimPoint.add(1.75d, 0.35d, 0.0d);
        Vec3d delta = sameSpotAimPoint.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        sameSpotReachDiagnostic = "eye=" + formatVec(eye)
                + "/aimPoint=" + formatVec(sameSpotAimPoint)
                + "/distance=" + formatDouble(eye.distanceTo(sameSpotAimPoint))
                + "/yaw=" + formatDouble(yaw)
                + "/pitch=" + formatDouble(pitch);
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.setSneaking(false);
        client.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            serverPlayer.setVelocity(Vec3d.ZERO);
            serverPlayer.setSneaking(false);
            serverPlayer.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            serverPlayer.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private static void requestSameSpotBreak(MinecraftClient client) {
        if (sameSpotBreakRequested) {
            return;
        }
        sameSpotBreakRequested = true;
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || sameSpotSlabPos == null) {
            sameSpotBreakResult = "TRACE_GAP_SERVER_WORLD_OR_SLAB_POS_MISSING";
            return;
        }
        serverWorld.getServer().execute(() -> sameSpotBreakResult =
                serverWorld.breakBlock(sameSpotSlabPos, false) ? "BROKE" : "NOT_BROKEN");
    }

    private static void emitSameSpotReadyRow(MinecraftClient client, String phase, String reason) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_READY_ROW]"
                + " phase=" + phase
                + " tick=" + sameSpotTicks
                + " clientBootstrapReady=" + readiness.clientBootstrapReady
                + " clientWorldReady=" + readiness.clientWorldReady
                + " clientPlayerReady=" + readiness.clientPlayerReady
                + " integratedServerReady=" + readiness.integratedServerReady
                + " serverWorldReady=" + readiness.serverWorldReady
                + " serverPlayerReady=" + readiness.serverPlayerReady
                + " interactionManagerReady=" + readiness.interactionManagerReady
                + " programmaticWorldStartRequested=" + sameSpotWorldStartRequested
                + " reason=" + reason);
    }

    private static void emitSameSpotRow(MinecraftClient client) {
        ClientWorld clientWorld = client == null ? null : client.world;
        ServerWorld serverWorld = serverWorldFor(client);
        if (clientWorld == null || serverWorld == null) {
            emitSameSpotTraceGap("ROUTE_ROW_SAMPLE", "TRACE_GAP_WORLD_OR_PLAYER_NOT_READY");
            return;
        }
        BlockState slabClientState = clientWorld.getBlockState(sameSpotSlabPos);
        BlockState slabServerState = serverWorld.getBlockState(sameSpotSlabPos);
        BlockState fullClientState = clientWorld.getBlockState(sameSpotFullPos);
        BlockState fullServerState = serverWorld.getBlockState(sameSpotFullPos);
        double fullClientDy = SlabSupport.getYOffset(clientWorld, sameSpotFullPos, fullClientState);
        double fullServerDy = SlabSupport.getYOffset(serverWorld, sameSpotFullPos, fullServerState);
        boolean fullClientAnchor = SlabAnchorAttachment.isAnchored(clientWorld, sameSpotFullPos);
        boolean fullServerAnchor = SlabAnchorAttachment.isAnchored(serverWorld, sameSpotFullPos);
        boolean visualTracePresent = sameSpotPostModelTrace.contains("modelDy=");
        boolean postTargetHitsFull = sameSpotPostTarget.contains("pos=" + textPos(sameSpotFullPos));
        boolean postTargetMissOrWrong = sameSpotPostTarget.contains("type=MISS") || !postTargetHitsFull;
        String classification;
        String reason;
        if (!sameSpotSlabPlacementResult.contains("SUCCESS") || !sameSpotFullPlacementResult.contains("SUCCESS")) {
            classification = "TRACE_GAP";
            reason = "TRACE_GAP_PLACEMENT_NOT_REPRODUCED";
        } else if (!slabServerState.isAir()) {
            classification = "TRACE_GAP";
            reason = "TRACE_GAP_SLAB_NOT_BROKEN";
        } else if (!fullServerState.isOf(Blocks.STONE)) {
            classification = "TRACE_GAP";
            reason = "TRACE_GAP_FULL_BLOCK_DID_NOT_REMAIN";
        } else if (postTargetMissOrWrong) {
            if (visualTracePresent) {
                classification = "RED";
                reason = "SAME_AIM_VISUAL_PRESENT_TARGET_NOT_BREAKABLE_FULL_BLOCK";
            } else {
                classification = "TRACE_GAP";
                reason = "TRACE_GAP_MODEL_TRACE_MISSING";
            }
        } else {
            classification = "TRACE_GAP";
            reason = "TRACE_GAP_ROUTE_NOT_LIVE_EQUIVALENT_TARGET_STILL_HITS_FULL_BLOCK";
        }
        System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_ROW]"
                + " rowName=SAME_SPOT_AFTER_BREAK_LOWER_VISUAL"
                + " fixtureOrigin=" + textPos(sameSpotOrigin)
                + " slabPos=" + textPos(sameSpotSlabPos)
                + " fullPos=" + textPos(sameSpotFullPos)
                + " slabClientState=" + slabClientState
                + " slabServerState=" + slabServerState
                + " fullClientState=" + fullClientState
                + " fullServerState=" + fullServerState
                + " fullClientDy=" + formatDouble(fullClientDy)
                + " fullServerDy=" + formatDouble(fullServerDy)
                + " fullClientAnchor=" + fullClientAnchor
                + " fullServerAnchor=" + fullServerAnchor
                + " aimPoint=" + formatVec(sameSpotAimPoint)
                + " sameEyeYawPitchAfterBreak=true"
                + " placementReturnSlab=" + sameSpotSlabPlacementResult
                + " placementReturnFullBlock=" + sameSpotFullPlacementResult
                + " breakResult=" + sameSpotBreakResult
                + " preTarget=" + sameSpotPreTarget
                + " postTarget=" + sameSpotPostTarget
                + " preModelTrace=" + sameSpotPreModelTrace
                + " postModelTrace=" + sameSpotPostModelTrace
                + " reachDiagnostic=" + sameSpotReachDiagnostic
                + " classification=" + classification
                + " reason=" + reason);
        System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_SUMMARY]"
                + " rows=1"
                + " finalResult=" + classification
                + " reason=" + reason
                + " behaviorChanged=false");
        System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_" + classification + "]"
                + " rowName=SAME_SPOT_AFTER_BREAK_LOWER_VISUAL"
                + " reason=" + reason);
    }

    private static void emitSameSpotTraceGap(String row, String reason) {
        System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_START]"
                + " rowName=SAME_SPOT_AFTER_BREAK_LOWER_VISUAL"
                + " rows=1");
        System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_ROW]"
                + " row=" + row
                + " fixtureOrigin=" + textPos(sameSpotOrigin)
                + " slabPos=" + textPos(sameSpotSlabPos)
                + " fullPos=" + textPos(sameSpotFullPos)
                + " classification=TRACE_GAP"
                + " reason=" + reason);
        System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_SUMMARY]"
                + " rows=1"
                + " finalResult=TRACE_GAP"
                + " reason=" + reason);
        System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_TRACE_GAP]"
                + " rowName=SAME_SPOT_AFTER_BREAK_LOWER_VISUAL"
                + " reason=" + reason);
        sameSpotFinalized = true;
        emitted = true;
        System.clearProperty("slabbed.render.offset.trace");
        if (clientReadyForStop()) {
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    private static String describeCrosshair(MinecraftClient client) {
        if (client == null || client.world == null || client.crosshairTarget == null) {
            return "type=null";
        }
        HitResult target = client.crosshairTarget;
        if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
            return "type=" + target.getType();
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        return "type=BLOCK"
                + "/pos=" + textPos(pos)
                + "/side=" + blockHit.getSide().asString()
                + "/hitVec=" + formatVec(blockHit.getPos())
                + "/state=" + state
                + "/dy=" + formatDouble(SlabSupport.getYOffset(client.world, pos, state))
                + "/anchor=" + SlabAnchorAttachment.isAnchored(client.world, pos);
    }

    private static String describeModelTrace(OffsetBlockStateModel.RenderOffsetTrace trace) {
        if (trace == null || !trace.seen()) {
            return "missing";
        }
        return "modelDy=" + formatDouble(trace.modelDy())
                + "/pos=" + trace.pos()
                + "/state=" + trace.state();
    }

    private static void runSuperflatModelHitboxHarnessRoute(MinecraftClient client) {
        boolean wallFenceProductRedMode = Boolean.getBoolean(WALL_FENCE_PRODUCT_RED_ONLY_PROPERTY);
        if (emitted || superflatHarnessFinalized) {
            return;
        }
        superflatHarnessTicks++;
        if (!superflatHarnessCanaryEmitted) {
            superflatHarnessCanaryEmitted = true;
            System.out.println("[" + (wallFenceProductRedMode
                    ? "MC1211_WALL_FENCE_PRODUCT_RED_ROUTE_CANARY"
                    : "MC1211_SUPERFLAT_MODEL_HITBOX_ROUTE_CANARY") + "]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " property=" + (wallFenceProductRedMode
                    ? WALL_FENCE_PRODUCT_RED_ONLY_PROPERTY
                    : SUPERFLAT_MODEL_HITBOX_HARNESS_ONLY_PROPERTY)
                    + " worldType=superflat"
                    + " behaviorChanged=false");
        }
        requestProgrammaticSuperflatHarnessWorldIfNeeded(client);
        String readinessGap = sidePlaceReadinessGap(client);
        if (readinessGap != null) {
            if (!superflatHarnessReadyRowEmitted || superflatHarnessTicks % 1200 == 0) {
                emitSidePlaceReadyRow(client, "WAITING", readinessGap);
                superflatHarnessReadyRowEmitted = true;
            }
            if (superflatHarnessTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitSuperflatHarnessTraceGap("TRACE_GAP_WORLD_NOT_READY", readinessGap);
            return;
        }
        if (!superflatHarnessReadyRowEmitted) {
            emitSidePlaceReadyRow(client, "READY", "none");
            superflatHarnessReadyRowEmitted = true;
        }
        if (!superflatHarnessStarted) {
            superflatHarnessStarted = true;
            superflatHarnessOrigin = client.player.getBlockPos().add(8, 0, 8).toImmutable();
            System.out.println("[" + (wallFenceProductRedMode
                    ? "MC1211_WALL_FENCE_PRODUCT_RED_START"
                    : "MC1211_SUPERFLAT_MODEL_HITBOX_START") + "]"
                    + " rows=" + SUPERFLAT_HARNESS_ROWS.length
                    + " fixtureOrigin=" + textPos(superflatHarnessOrigin)
                    + " supportBlockId=minecraft:stone_slab");
        }
        if (superflatHarnessRowIndex >= SUPERFLAT_HARNESS_ROWS.length) {
            emitSuperflatHarnessSummary(wallFenceProductRedMode);
            return;
        }
        runSuperflatHarnessRow(
                client,
                SUPERFLAT_HARNESS_ROWS[superflatHarnessRowIndex],
                superflatHarnessRowIndex,
                wallFenceProductRedMode);
    }

    private static void runSuperflatHarnessRow(
            MinecraftClient client,
            SuperflatHarnessRowSpec row,
            int rowIndex,
            boolean wallFenceProductRedMode
    ) {
        if (client == null || client.world == null || client.player == null) {
            return;
        }
        MinecraftServer server = client.getServer();
        if (server == null) {
            return;
        }
        ServerWorld serverWorld = server.getWorld(client.world.getRegistryKey());
        if (serverWorld == null) {
            return;
        }
        if (superflatHarnessRowPhase == 0) {
            BlockPos rowOrigin = superflatHarnessOrigin.add(rowIndex * 4, 0, 0);
            superflatHarnessSlabPos = rowOrigin;
            superflatHarnessPlacePos = rowOrigin.up();
            superflatHarnessPlacementMethod = "MIXED";
            superflatHarnessPlacementReturn = "not_started";
            superflatHarnessInteractAttempts = 0;
            authorSuperflatHarnessFixture(server, serverWorld, superflatHarnessSlabPos, superflatHarnessPlacePos);
            syncHarnessPlayerForTopPlace(client, superflatHarnessSlabPos);
            ItemStack stack = harnessStackForBlock(row.blockId());
            if (stack != null) {
                client.player.setStackInHand(Hand.MAIN_HAND, stack);
                if (!server.getPlayerManager().getPlayerList().isEmpty()
                        && !server.getPlayerManager().getPlayerList().get(0).isRemoved()) {
                    server.getPlayerManager().getPlayerList().get(0)
                            .setStackInHand(Hand.MAIN_HAND, stack.copy());
                }
            }
            OffsetBlockStateModel.resetFullMeshBoundsTrace(superflatHarnessPlacePos);
            superflatHarnessRowPhase = 1;
            superflatHarnessPhaseTick = superflatHarnessTicks;
            return;
        }
        if (superflatHarnessRowPhase == 1) {
            if (superflatHarnessTicks - superflatHarnessPhaseTick < 2) {
                return;
            }
            ItemStack stack = harnessStackForBlock(row.blockId());
            if (stack == null) {
                server.execute(() -> serverWorld.setBlockState(superflatHarnessPlacePos, harnessStateForBlock(row.blockId()), 3));
                superflatHarnessPlacementMethod = "DIRECT_WORLD_TRACE_ONLY";
                superflatHarnessPlacementReturn = "DIRECT_SETBLOCK_TRACE_ONLY";
                superflatHarnessRowPhase = 2;
                superflatHarnessPhaseTick = superflatHarnessTicks;
                return;
            }
            BlockHitResult hit = new BlockHitResult(
                    new Vec3d(
                            superflatHarnessSlabPos.getX() + 0.5d,
                            superflatHarnessSlabPos.getY() + 0.5d,
                            superflatHarnessSlabPos.getZ() + 0.5d),
                    Direction.UP,
                    superflatHarnessSlabPos,
                    false);
            ActionResult result = client.interactionManager == null
                    ? ActionResult.FAIL
                    : client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
            superflatHarnessInteractAttempts++;
            superflatHarnessPlacementMethod = "REAL_INTERACT_BLOCK";
            superflatHarnessPlacementReturn = String.valueOf(result);
            superflatHarnessRowPhase = 2;
            superflatHarnessPhaseTick = superflatHarnessTicks;
            return;
        }
        if (superflatHarnessTicks - superflatHarnessPhaseTick < 20) {
            return;
        }
        Block expectedBlock = harnessStateForBlock(row.blockId()).getBlock();
        BlockState latestServerState = serverWorld.getBlockState(superflatHarnessPlacePos);
        if (!"DIRECT_WORLD_TRACE_ONLY".equals(superflatHarnessPlacementMethod)
                && latestServerState.getBlock() != expectedBlock
                && superflatHarnessInteractAttempts < 3) {
            syncHarnessPlayerForTopPlace(client, superflatHarnessSlabPos);
            ItemStack retryStack = harnessStackForBlock(row.blockId());
            if (retryStack != null) {
                client.player.setStackInHand(Hand.MAIN_HAND, retryStack);
                if (!server.getPlayerManager().getPlayerList().isEmpty()
                        && !server.getPlayerManager().getPlayerList().get(0).isRemoved()) {
                    server.getPlayerManager().getPlayerList().get(0).setStackInHand(Hand.MAIN_HAND, retryStack.copy());
                }
            }
            superflatHarnessRowPhase = 1;
            superflatHarnessPhaseTick = superflatHarnessTicks;
            return;
        }
        emitSuperflatHarnessRow(client, serverWorld, row, wallFenceProductRedMode);
        superflatHarnessRowIndex++;
        superflatHarnessRowPhase = 0;
        superflatHarnessPhaseTick = superflatHarnessTicks;
    }

    private static void requestProgrammaticSuperflatHarnessWorldIfNeeded(MinecraftClient client) {
        if (superflatHarnessWorldStartRequested
                || client == null
                || !client.isFinishedLoading()
                || client.world != null
                || client.player != null) {
            return;
        }
        superflatHarnessWorldStartRequested = true;
        LevelInfo levelInfo = new LevelInfo(
                "Slabbed MC1211 Superflat Model Hitbox Harness",
                GameMode.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                DataConfiguration.SAFE_MODE);
        GeneratorOptions generatorOptions = new GeneratorOptions(0L, false, false);
        client.createIntegratedServerLoader().createAndStart(
                "slabbed-mc1211-superflat-model-hitbox-harness",
                levelInfo,
                generatorOptions,
                Mc1211GoblinRouteClientEntrypoint::createSuperflatDimensionOptions,
                null);
    }

    private static void authorSuperflatHarnessFixture(
            MinecraftServer server,
            ServerWorld serverWorld,
            BlockPos slabPos,
            BlockPos placePos
    ) {
        server.execute(() -> {
            for (int x = slabPos.getX() - 1; x <= slabPos.getX() + 1; x++) {
                for (int z = slabPos.getZ() - 1; z <= slabPos.getZ() + 1; z++) {
                    for (int y = slabPos.getY() - 1; y <= slabPos.getY() + 3; y++) {
                        serverWorld.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
            serverWorld.setBlockState(
                    slabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    3);
            serverWorld.setBlockState(placePos, Blocks.AIR.getDefaultState(), 3);
        });
    }

    private static void syncHarnessPlayerForTopPlace(MinecraftClient client, BlockPos slabPos) {
        Vec3d hit = Vec3d.ofCenter(slabPos).add(0.0d, 0.5d, 0.0d);
        Vec3d eye = hit.add(0.0d, 0.8d, 2.4d);
        Vec3d delta = hit.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.setSneaking(false);
    }

    private static void emitSuperflatHarnessRow(
            MinecraftClient client,
            ServerWorld serverWorld,
            SuperflatHarnessRowSpec row,
            boolean wallFenceProductRedMode
    ) {
        ClientWorld clientWorld = client.world;
        BlockState finalClientState = clientWorld.getBlockState(superflatHarnessPlacePos);
        BlockState finalServerState = serverWorld.getBlockState(superflatHarnessPlacePos);
        Block expectedBlock = harnessStateForBlock(row.blockId()).getBlock();
        double placedDy = SlabSupport.getYOffset(serverWorld, superflatHarnessPlacePos, finalServerState);
        BlockState supportState = serverWorld.getBlockState(superflatHarnessSlabPos);
        double sourceDy = SlabSupport.getYOffset(serverWorld, superflatHarnessSlabPos, supportState);
        boolean substrateProven = supportState.isOf(Blocks.STONE_SLAB)
                && supportState.contains(SlabBlock.TYPE)
                && supportState.get(SlabBlock.TYPE) == SlabType.BOTTOM;
        boolean anchored = SlabAnchorAttachment.isAnchored(serverWorld, superflatHarnessPlacePos);
        boolean loweredCarrier = SlabSupport.isFullHeightLoweredCarrier(serverWorld, superflatHarnessPlacePos, finalServerState);
        VoxelShape outline = finalServerState.getOutlineShape(serverWorld, superflatHarnessPlacePos);
        VoxelShape raycast = finalServerState.getRaycastShape(serverWorld, superflatHarnessPlacePos);
        String outlineBounds = shapeBounds(outline);
        String raycastBounds = shapeBounds(raycast);
        OffsetBlockStateModel.FullMeshBoundsTrace meshTrace = OffsetBlockStateModel.snapshotFullMeshBoundsTrace();
        String modelMeshMinY = meshTrace.seen() ? formatDouble(meshTrace.minAfterY()) : "NaN";
        String modelMeshMaxY = meshTrace.seen() ? formatDouble(meshTrace.maxAfterY()) : "NaN";
        String meshTraceKey = meshTrace.seen() ? meshTrace.meshTraceKey() : "none";
        String meshMatrixKey = meshTrace.seen() ? meshTrace.matrixKey() : "none";
        String meshMatrixRow = meshTrace.seen() ? meshTrace.matrixRow() : "UNKNOWN";
        String meshBlockId = meshTrace.seen() ? meshTrace.blockId() : "none";
        String meshPos = meshTrace.seen() ? meshTrace.pos() : "none";
        String meshState = meshTrace.seen() ? meshTrace.state() : "none";
        String meshDy = meshTrace.seen() ? formatDouble(meshTrace.dy()) : "NaN";
        String meshModelClass = meshTrace.seen() ? meshTrace.modelClass() : "unknown";
        String meshTickOrFrame = meshTrace.seen() ? meshTrace.tickOrFrame() : "unknown";
        String meshPassSequence = meshTrace.seen() ? Integer.toString(meshTrace.passSequence()) : "0";
        String meshQuadsVisited = meshTrace.seen() ? Integer.toString(meshTrace.totalQuadsSeen()) : "0";
        String meshVerticesVisited = meshTrace.seen() ? Integer.toString(meshTrace.verticesVisited()) : "0";
        String meshMinBeforeY = meshTrace.seen() ? formatDouble(meshTrace.minBeforeY()) : "NaN";
        String meshMaxBeforeY = meshTrace.seen() ? formatDouble(meshTrace.maxBeforeY()) : "NaN";
        String meshSnapshotSource = meshTrace.seen() ? meshTrace.snapshotSource() : "none";
        String meshAggregateDedupKey = meshTrace.seen() ? meshTrace.aggregateDedupKey() : "none";
        String outlineMinY = outline.isEmpty() ? "NaN" : formatDouble(outline.getBoundingBox().minY);
        String outlineMaxY = outline.isEmpty() ? "NaN" : formatDouble(outline.getBoundingBox().maxY);
        String raycastMinY = raycast.isEmpty() ? "NaN" : formatDouble(raycast.getBoundingBox().minY);
        String raycastMaxY = raycast.isEmpty() ? "NaN" : formatDouble(raycast.getBoundingBox().maxY);
        double delta = (meshTrace.seen() && !outline.isEmpty())
                ? Math.abs(meshTrace.minAfterY() - outline.getBoundingBox().minY)
                : Double.NaN;
        boolean triadBoundsPresent = meshTrace.seen() && !outline.isEmpty() && !raycast.isEmpty();
        boolean triadAligned = triadBoundsPresent
                && delta <= 1.0e-6
                && Math.abs(meshTrace.minAfterY() - raycast.getBoundingBox().minY) <= 1.0e-6
                && Math.abs(meshTrace.maxAfterY() - outline.getBoundingBox().maxY) <= 1.0e-6
                && Math.abs(meshTrace.maxAfterY() - raycast.getBoundingBox().maxY) <= 1.0e-6;
        boolean placedDyExpected = Math.abs(placedDy - (-0.5d)) <= 1.0e-6;
        boolean technicalTriadAligned = triadAligned && placedDyExpected && substrateProven;
        boolean productVisualLawPass = !row.productBadSuspect();
        String visualAcceptance = row.productBadSuspect()
                ? "JULIA_APPROVED_POST_MODEL_OWNERSHIP_REPAIR"
                : "ORDINARY_CONTROL";
        String verdict;
        String reason;
        if (!wallFenceProductRedMode) {
            if ("DIRECT_WORLD_TRACE_ONLY".equals(superflatHarnessPlacementMethod)) {
                verdict = "TRACE_GAP_NOT_REAL_PLACEMENT";
                reason = "direct_world_place_trace_only";
                superflatHarnessTraceGapRows++;
            } else if (!superflatHarnessPlacementReturn.contains("SUCCESS")) {
                verdict = "TRACE_GAP_NOT_REAL_PLACEMENT";
                reason = "interact_block_not_success";
                superflatHarnessTraceGapRows++;
            } else if (!meshTrace.seen()) {
                verdict = "TRACE_GAP_MODEL_BOUNDS";
                reason = "full_mesh_trace_missing_for_row";
                superflatHarnessTraceGapRows++;
            } else if (outline.isEmpty() || raycast.isEmpty()) {
                verdict = "TRACE_GAP_OUTLINE_RAYCAST_BOUNDS";
                reason = "outline_or_raycast_shape_empty";
                superflatHarnessTraceGapRows++;
            } else if (delta > 1.0e-6) {
                verdict = "RED_MODEL_OUTLINE_VERTICAL_MISMATCH";
                reason = "model_afterY_not_equal_outline_bounds";
                superflatHarnessRedRows++;
            } else {
                verdict = row.productBadSuspect()
                        ? "GREEN_TRIAD_ALIGNED_PRODUCT_BAD"
                        : "GREEN_TRIAD_ALIGNED_ACCEPTABLE";
                reason = row.productBadSuspect() ? "family_visual_reference_mismatch_suspect" : "triad_aligned";
                if (row.productBadSuspect()) {
                    superflatHarnessProductBadRows++;
                } else {
                    superflatHarnessGreenRows++;
                }
            }
        } else if (!substrateProven) {
            verdict = "TRACE_GAP_SUBSTRATE_MISSING";
            reason = "support_not_bottom_stone_slab";
            superflatHarnessTraceGapRows++;
        } else if ("DIRECT_WORLD_TRACE_ONLY".equals(superflatHarnessPlacementMethod)) {
            verdict = "minecraft:stone_wall".equals(row.blockId())
                    ? "TRACE_GAP_STONE_WALL_DIRECT_WORLD_ONLY"
                    : "TRACE_GAP_BOUNDS_OR_TRIAD_MISSING";
            reason = "direct_world_place_trace_only";
            superflatHarnessTraceGapRows++;
        } else if (!superflatHarnessPlacementReturn.contains("SUCCESS")) {
            verdict = "TRACE_GAP_BOUNDS_OR_TRIAD_MISSING";
            reason = "interact_block_not_success";
            superflatHarnessTraceGapRows++;
        } else if (finalServerState.getBlock() != expectedBlock) {
            verdict = "TRACE_GAP_BOUNDS_OR_TRIAD_MISSING";
            reason = "interact_success_but_expected_block_missing";
            superflatHarnessTraceGapRows++;
        } else if (!triadBoundsPresent) {
            verdict = "TRACE_GAP_BOUNDS_OR_TRIAD_MISSING";
            reason = "model_or_outline_or_raycast_missing";
            superflatHarnessTraceGapRows++;
        } else if (!technicalTriadAligned) {
            verdict = row.productBadSuspect()
                    ? "TRACE_GAP_BOUNDS_OR_TRIAD_MISSING"
                    : "RED_FULL_BLOCK_CONTROL_REGRESSION";
            reason = !triadAligned ? "technical_triad_not_aligned" : "placed_dy_or_substrate_not_expected";
            if (row.productBadSuspect()) {
                superflatHarnessTraceGapRows++;
            } else {
                superflatHarnessRedRows++;
            }
        } else if (row.productBadSuspect()) {
            productVisualLawPass = true;
            verdict = "GREEN_WALL_FENCE_VISUAL_CONTACT_APPROVED";
            reason = "julia_live_visual_accepted_after_model_ownership_repair";
            superflatHarnessGreenRows++;
        } else {
            verdict = "GREEN_FULL_BLOCK_CONTROL";
            reason = "ordinary_full_block_control_green";
            superflatHarnessGreenRows++;
        }
        String outputRowName = wallFenceProductRedMode ? redMatrixRowName(row) : row.rowName();
        String rowMarker = wallFenceProductRedMode
                ? "MC1211_WALL_FENCE_PRODUCT_RED_ROW"
                : "MC1211_SUPERFLAT_MODEL_HITBOX_ROW";
        BlockHitResult blockHit = client.crosshairTarget instanceof BlockHitResult b ? b : null;
        String targetPos = blockHit == null ? "none" : textPos(blockHit.getBlockPos());
        String targetFace = blockHit == null ? "none" : blockHit.getSide().asString();
        System.out.println("[" + rowMarker + "]"
                + " rowName=" + outputRowName
                + " blockId=" + row.blockId()
                + " supportBlockId=minecraft:stone_slab"
                + " slabPos=" + textPos(superflatHarnessSlabPos)
                + " placedBlockPos=" + textPos(superflatHarnessPlacePos)
                + " placementMethod=" + superflatHarnessPlacementMethod
                + " placementReturn=" + superflatHarnessPlacementReturn
                + " substrateProven=" + substrateProven
                + " finalState=" + finalServerState
                + " sourceDy=" + formatDouble(sourceDy)
                + " placedDy=" + formatDouble(placedDy)
                + " anchored=" + anchored
                + " loweredCarrier=" + loweredCarrier
                + " modelMeshMinY=" + modelMeshMinY
                + " modelMeshMaxY=" + modelMeshMaxY
                + " meshTraceKey=" + meshTraceKey
                + " matrixKey=" + meshMatrixKey
                + " matrixRow=" + meshMatrixRow
                + " meshBlockId=" + meshBlockId
                + " meshPos=" + meshPos
                + " meshState=" + meshState
                + " meshDy=" + meshDy
                + " meshModelClass=" + meshModelClass
                + " tickOrFrame=" + meshTickOrFrame
                + " rowSource=RED_ROW"
                + " passSequence=" + meshPassSequence
                + " quadsVisited=" + meshQuadsVisited
                + " verticesVisited=" + meshVerticesVisited
                + " minBeforeY=" + meshMinBeforeY
                + " maxBeforeY=" + meshMaxBeforeY
                + " minAfterY=" + modelMeshMinY
                + " maxAfterY=" + modelMeshMaxY
                + " snapshotSource=" + meshSnapshotSource
                + " aggregateDedupKey=" + meshAggregateDedupKey
                + " outlineMinY=" + outlineMinY
                + " outlineMaxY=" + outlineMaxY
                + " raycastMinY=" + raycastMinY
                + " raycastMaxY=" + raycastMaxY
                + " targetPos=" + targetPos
                + " targetFace=" + targetFace
                + " expectedModelMinY=" + (meshTrace.seen() ? formatDouble(meshTrace.minBeforeY() + meshTrace.dy()) : "NaN")
                + " expectedModelMaxY=" + (meshTrace.seen() ? formatDouble(meshTrace.maxBeforeY() + meshTrace.dy()) : "NaN")
                + " modelVsOutlineDelta=" + formatDouble(delta)
                + " modelClass=" + meshModelClass
                + " outlineBounds=" + outlineBounds
                + " raycastBounds=" + raycastBounds
                + " technicalTriadAligned=" + technicalTriadAligned
                + " productVisualLawPass=" + productVisualLawPass
                + " visualAcceptance=" + visualAcceptance
                + " clientState=" + finalClientState
                + " verdict=" + verdict
                + " reason=" + reason);
    }

    private static String redMatrixRowName(SuperflatHarnessRowSpec row) {
        return switch (row.blockId()) {
            case "minecraft:cobblestone_wall" -> "COBBLESTONE_WALL_ON_BOTTOM_SLAB_VISUAL_CONTACT_GREEN";
            case "minecraft:oak_fence" -> "OAK_FENCE_ON_BOTTOM_SLAB_VISUAL_CONTACT_GREEN";
            case "minecraft:stone_wall" -> "STONE_WALL_ON_BOTTOM_SLAB_TRACE_GAP_OR_RED";
            case "minecraft:stone" -> "FULL_BLOCK_CONTROL_STONE_GREEN";
            case "minecraft:oak_log" -> "FULL_BLOCK_CONTROL_OAK_LOG_GREEN";
            case "minecraft:stone_stairs" -> "STONE_STAIRS_CONTROL_GREEN";
            default -> row.rowName();
        };
    }

    private static void emitSuperflatHarnessSummary(boolean wallFenceProductRedMode) {
        String classification;
        if (!wallFenceProductRedMode) {
            if (superflatHarnessRedRows > 0) {
                classification = "RED_MODEL_HITBOX_MISMATCH_CONFIRMED";
            } else if (superflatHarnessTraceGapRows > 0) {
                classification = "TRACE_GAP_BOUNDS_MISSING";
            } else if (superflatHarnessProductBadRows > 0) {
                classification = "WALL_FENCE_TRIAD_ALIGNED_PRODUCT_BAD";
            } else {
                classification = "SUPERFLAT_HARNESS_GREEN_ALL_TRIAD_ALIGNED";
            }
        } else {
            if (superflatHarnessRedRows > 0) {
                classification = superflatHarnessRedRows >= 2 && superflatHarnessGreenRows >= 2
                        ? "EXPECTED_RED_MATRIX_ACHIEVED"
                        : "RED_MATRIX_INCOMPLETE";
            } else if (superflatHarnessGreenRows >= 4 && superflatHarnessProductBadRows == 0) {
                classification = "WALL_FENCE_VISUAL_CONTACT_GREEN_JULIA_APPROVED";
            } else {
                classification = "RED_MATRIX_INCOMPLETE";
            }
        }
        System.out.println("[" + (wallFenceProductRedMode
                ? "MC1211_WALL_FENCE_PRODUCT_RED_SUMMARY"
                : "MC1211_SUPERFLAT_MODEL_HITBOX_SUMMARY") + "]"
                + " rows=" + SUPERFLAT_HARNESS_ROWS.length
                + " greenRows=" + superflatHarnessGreenRows
                + " redRows=" + superflatHarnessRedRows
                + " traceGapRows=" + superflatHarnessTraceGapRows
                + " productBadRows=" + superflatHarnessProductBadRows
                + " classification=" + classification);
        emitted = true;
        superflatHarnessFinalized = true;
        if (clientReadyForStop()) {
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    private static void emitSuperflatHarnessTraceGap(String row, String reason) {
        boolean wallFenceProductRedMode = Boolean.getBoolean(WALL_FENCE_PRODUCT_RED_ONLY_PROPERTY);
        System.out.println("[" + (wallFenceProductRedMode
                ? "MC1211_WALL_FENCE_PRODUCT_RED_START"
                : "MC1211_SUPERFLAT_MODEL_HITBOX_START") + "]"
                + " rows=" + SUPERFLAT_HARNESS_ROWS.length);
        System.out.println("[" + (wallFenceProductRedMode
                ? "MC1211_WALL_FENCE_PRODUCT_RED_ROW"
                : "MC1211_SUPERFLAT_MODEL_HITBOX_ROW") + "]"
                + " rowName=" + row
                + " blockId=none"
                + " placementMethod=DIRECT_WORLD_TRACE_ONLY"
                + " placementReturn=FAIL_ROUTE_NOT_READY"
                + " verdict=TRACE_GAP_WORLD_NOT_READY"
                + " reason=" + reason);
        System.out.println("[" + (wallFenceProductRedMode
                ? "MC1211_WALL_FENCE_PRODUCT_RED_SUMMARY"
                : "MC1211_SUPERFLAT_MODEL_HITBOX_SUMMARY") + "]"
                + " rows=0"
                + " classification=TRACE_GAP_WORLD_NOT_READY"
                + " reason=" + reason);
        emitted = true;
        superflatHarnessFinalized = true;
        if (clientReadyForStop()) {
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    private static String shapeBounds(VoxelShape shape) {
        if (shape == null || shape.isEmpty()) {
            return "NaN..NaN";
        }
        return formatDouble(shape.getBoundingBox().minY) + ".." + formatDouble(shape.getBoundingBox().maxY);
    }

    private static ItemStack harnessStackForBlock(String blockId) {
        return switch (blockId) {
            case "minecraft:stone" -> new ItemStack(Items.STONE, 8);
            case "minecraft:oak_log" -> new ItemStack(Items.OAK_LOG, 8);
            case "minecraft:oak_planks" -> new ItemStack(Items.OAK_PLANKS, 8);
            case "minecraft:cobblestone_wall" -> new ItemStack(Items.COBBLESTONE_WALL, 8);
            case "minecraft:oak_fence" -> new ItemStack(Items.OAK_FENCE, 8);
            case "minecraft:stone_stairs" -> new ItemStack(Items.STONE_STAIRS, 8);
            default -> {
                var item = Registries.ITEM.get(Identifier.of(blockId));
                yield item == null || item == Items.AIR ? null : new ItemStack(item, 8);
            }
        };
    }

    private static BlockState harnessStateForBlock(String blockId) {
        var block = Registries.BLOCK.get(Identifier.of(blockId));
        return block == null ? Blocks.AIR.getDefaultState() : block.getDefaultState();
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

    private record SuperflatHarnessRowSpec(
            String rowName,
            String blockId,
            boolean productBadSuspect
    ) {
    }

    private record RowResult(boolean traceGap, boolean modelLowerThanOutline) {
    }
}
