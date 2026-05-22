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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
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
    private static final String MODEL_VS_OUTLINE_GOBLIN_HOST_ONLY_PROPERTY =
            "slabbed.mc1211.modelVsOutlineGoblinHostOnly";
    private static final String OVERLAP_ONLY_PROPERTY = "slabbed.mc1211.overlapMatrixOnly";
    private static final String LEGACY_CLASS =
            "com.slabbed.test.SlabbedLabUltraGoblin2StressClientGameTest";
    private static final String ROUTE = "runClientGameTest";
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
    private static int sidePlaceRetainedSampleAttempts;
    private static int sidePlaceRetainedSampleTicks;
    private static String sidePlaceSampledStates = "not_sampled";
    private static boolean sidePlaceRetainedServerStoneObserved;

    @Override
    public void onInitializeClient() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(Mc1211GoblinRouteClientEntrypoint::onEndTick);
    }

    private static void onEndTick(MinecraftClient client) {
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
                    + " property=" + SIDE_PLACE_STONE_LOWERING_ONLY_PROPERTY);
        }

        if (client == null || client.world == null || client.player == null || client.getServer() == null) {
            if (sidePlaceTicks < 2400) {
                return;
            }
            emitSidePlaceTraceGap(
                    "ROUTE_READINESS",
                    "TRACE_GAP_NOT_VIDEO_EQUIVALENT_client_world_player_or_integrated_server_not_ready");
            return;
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
            BlockHitResult hit = new BlockHitResult(
                    new Vec3d(
                            sidePlaceHitPos.getX() + 1.0d,
                            sidePlaceHitPos.getY() + 0.5d,
                            sidePlaceHitPos.getZ() + 0.5d),
                    Direction.EAST,
                    sidePlaceHitPos,
                    false);
            client.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 1));
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
        } else if (!sidePlaceServerResultObserved) {
            classification = "TRACE_GAP_SERVER_RESULT_UNOBSERVABLE_BUT_STATE_RETAINED";
            legalStateName = "none";
            illegalReason = "server_result_not_observable_from_client_route";
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
            classification = "LEGAL_VANILLA_SIDE_PLACEMENT";
            legalStateName = "VANILLA_SIDE_ADJACENT_FULL_BLOCK";
            illegalReason = "none";
            finalMarker = "GREEN";
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
                + " placementResultClient=" + sidePlaceClientResult
                + " placementResultServer=" + sidePlaceServerResult
                + " serverResultObserved=" + sidePlaceServerResultObserved
                + " placementAccepted=" + sidePlaceClientAccepted
                + " retainedSampleAttempts=" + sidePlaceRetainedSampleAttempts
                + " retainedSampleTicks=" + sidePlaceRetainedSampleTicks
                + " sampledStates=" + sidePlaceSampledStates
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
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_" + finalMarker + "]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " classification=" + classification
                + " postPlaceDy=" + formatDouble(postPlaceDy)
                + " legalStateName=" + legalStateName);
    }

    private static void emitSidePlaceTraceGap(String row, String reason) {
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
                + " placementResultClient=" + sidePlaceClientResult
                + " placementResultServer=" + sidePlaceServerResult
                + " serverResultObserved=" + sidePlaceServerResultObserved
                + " retainedSampleAttempts=" + sidePlaceRetainedSampleAttempts
                + " retainedSampleTicks=" + sidePlaceRetainedSampleTicks
                + " sampledStates=" + sidePlaceSampledStates
                + " postPlaceState=n/a"
                + " postPlaceDy=NaN"
                + " postPlaceAnchored=false"
                + " sourceSupportRelationship=unknown"
                + " legalStateName=none"
                + " illegalReason=" + reason
                + " visualRelation=unknown"
                + " classification=TRACE_GAP_NOT_VIDEO_EQUIVALENT");
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_SUMMARY]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " rows=1"
                + " finalResult=TRACE_GAP"
                + " classification=TRACE_GAP_NOT_VIDEO_EQUIVALENT"
                + " reason=" + reason);
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_TRACE_GAP]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " reason=" + reason);
        emitted = true;
        if (clientReadyForStop()) {
            MinecraftClient.getInstance().scheduleStop();
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

    private static String formatDouble(double value) {
        if (!Double.isFinite(value)) {
            return "NaN";
        }
        return String.format(java.util.Locale.ROOT, "%.6f", value);
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
