package com.slabbed.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.LiveCursorIntentRecorder;
import com.slabbed.util.SlabbedOffsetRaycast;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class SlabbedLabLiveCursorIntentRecorderContractClientGameTest implements FabricClientGameTest {

    /**
     * Positive execution evidence. The client suite has no per-entrypoint count gate the way the server
     * suite does, so an entrypoint that never runs is indistinguishable from one that passed: the task
     * simply reports success. Every client entrypoint emits this on its success path, and a green run is
     * proof only when the log carries one line per {@code fabric-client-gametest} entry.
     */
    private static final String CLIENT_GAMETEST_PASS = "CLIENT_GAMETEST | SlabbedLabLiveCursorIntentRecorderContractClientGameTest | PASS";
    private static final String TEST23_CASE_PROPERTY = "slabbed.test23.recorderCase";
    private static final String EMPTY_HAND_USE_PACKET_CAPTURE = "empty_hand_use_packet_capture";
    private static final String CURSOR_PRODUCTION_HOOK = "cursor_production_hook";
    private static final String RENDERED_OUTLINE_PRODUCTION_HOOK =
            "rendered_outline_production_hook";
    private static final String TEST28_CASE_PROPERTY = "slabbed.test28.deepHeldUseCase";
    private static final String DEEP_HELD_USE_PACKET_RED = "gate_lever_packet_red";
    private static final int DEEP_HELD_USE_STARTING_COUNT = 4;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        String test28Case = System.getProperty(TEST28_CASE_PROPERTY);
        if (test28Case != null) {
            switch (test28Case) {
                case DEEP_HELD_USE_PACKET_RED -> runDeepHeldUsePacketRed(ctx);
                default -> throw new IllegalArgumentException(
                        "Unknown TEST 28 deep-held-use case: " + test28Case);
            }
            return;
        }

        String test23Case = System.getProperty(TEST23_CASE_PROPERTY);
        if (test23Case != null) {
            switch (test23Case) {
                case EMPTY_HAND_USE_PACKET_CAPTURE -> runEmptyHandUsePacketCapture(ctx);
                case CURSOR_PRODUCTION_HOOK -> runCursorProductionHook(ctx);
                case RENDERED_OUTLINE_PRODUCTION_HOOK ->
                        runRenderedOutlineProductionHook(ctx);
                default -> throw new IllegalArgumentException(
                        "Unknown TEST 23 recorder case: " + test23Case);
            }
            return;
        }

        try {
            Path evidenceRoot = Path.of(System.getProperty(
                    "slabbed.liveCursorIntentRecorderContractDir",
                    "tmp/live-cursor-intent-recorder-contract"));
            Path evidenceDir = freshEvidenceDir(
                    evidenceRoot.resolve("contract-" + System.nanoTime()));
            System.setProperty("slabbed.liveCursorIntentRecorder", "true");
            System.setProperty("slabbed.liveCursorIntentRecorderDir", evidenceDir.toString());
            LiveCursorIntentRecorder.resetForTests();

            // This fixture writes recorder API rows directly. It checks serialization and reduction,
            // not whether the production raycast or rendered-outline hooks are active.
            LinkedHashMap<String, String> cursor = new LinkedHashMap<>();
            cursor.put("tick", "1");
            cursor.put("time", "contract");
            cursor.put("heldItem", "minecraft:stone");
            cursor.put("finalHitType", "BLOCK");
            cursor.put("finalHitPos", "4,-60,30");
            cursor.put("finalHitFace", "EAST");
            cursor.put("finalHitState", "minecraft:stone_slab[type=bottom]");
            cursor.put("finalDy", "-0.500000");
            cursor.put("finalOwnerLaneKind", "persistent_lowered_slab_carrier");
            cursor.put("finalOutlineReplayHit", "hit=true pos=4,-60,30 side=east");
            cursor.put("finalRaycastReplayHit", "miss(empty)");
            cursor.put("outlineBounds", "min=(0.000000,0.000000,0.000000),max=(1.000000,1.000000,1.000000)");
            cursor.put("finalHitVec", "4.500000,-60.000000,30.500000");
            LiveCursorIntentRecorder.recordCursor(cursor);

            LinkedHashMap<String, String> renderedOutline = new LinkedHashMap<>();
            renderedOutline.put("renderedOutlinePos", "4,-60,30");
            renderedOutline.put("renderedOutlineState", "minecraft:stone_slab[type=bottom]");
            renderedOutline.put("renderedOutlineBounds",
                    "min=(0.000000,0.000000,0.000000),max=(3.000000,1.000000,1.000000)");
            renderedOutline.put("renderedOutlineWorldBounds",
                    "min=(4.000000,-60.000000,30.000000),max=(7.000000,-59.000000,31.000000)");
            renderedOutline.put("renderedOutlineCameraRelativeBounds",
                    "min=(1.000000,-1.000000,2.000000),max=(4.000000,0.000000,3.000000)");
            renderedOutline.put("renderedOutlineHitVec", "4.500000,-60.000000,30.500000");
            LiveCursorIntentRecorder.recordRenderedOutline(renderedOutline);

            // Exact TEST 19R shape: the client prediction can report the generic slab lane while the
            // authoritative server reports the anchored lane. Both are geometrically lawful at dy=-2.
            LiveCursorIntentRecorder.recordAction(loweredSideAction(
                    "client", "104,-39,30", "105,-39,30", "-2.000000", "unnamed_or_vanilla_slab"));
            LiveCursorIntentRecorder.recordAction(loweredSideAction(
                    "server", "114,-39,30", "115,-39,30", "-2.000000", "anchored_full_block"));

            LinkedHashMap<String, String> cleanProxy = loweredSideAction(
                    "server", "116,-39,30", "117,-39,30", "-2.000000", "anchored_full_block");
            cleanProxy.put("actionOrigin", "PLAYER_AUTHORED"); // hostile caller spoof attempt
            LiveCursorIntentRecorder.withActionOrigin(
                    LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                    () -> LiveCursorIntentRecorder.recordAction(cleanProxy));
            LinkedHashMap<String, String> redProxy = loweredSideAction(
                    "server", "118,-39,30", "119,-39,30", "-1.500000", "anchored_full_block");
            redProxy.put("actionOrigin", "BOGUS_ORIGIN"); // unknown values cannot fall through as proxy
            LiveCursorIntentRecorder.withActionOrigin(
                    LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                    () -> LiveCursorIntentRecorder.recordAction(redProxy));

            // Same dy on an authoritative server row is still lane-red when the server has no lawful
            // lowered ownership. This must be a LANE mismatch only, never mislabeled as a dy mismatch.
            LiveCursorIntentRecorder.recordAction(loweredSideAction(
                    "server", "124,-39,30", "125,-39,30", "-2.000000", "unnamed_or_vanilla_slab"));

            // A real dy error in an otherwise lawful lane remains a dy-only verdict.
            LiveCursorIntentRecorder.recordAction(loweredSideAction(
                    "server", "134,-39,30", "135,-39,30", "-1.500000", "anchored_full_block"));

            // Preserve the stronger vanilla-height marker alongside the dy mismatch.
            LiveCursorIntentRecorder.recordAction(loweredSideAction(
                    "server", "144,-39,30", "145,-39,30", "0.000000", "anchored_full_block"));

            // A non-consuming use row may share the same geometry snapshot, but it did not author a
            // placement and must never inflate the green-placement counter.
            LinkedHashMap<String, String> refused = loweredSideAction(
                    "client", "154,-39,30", "155,-39,30", "-2.000000", "unnamed_or_vanilla_slab");
            refused.put("actionType", "use_block");
            refused.put("actualResult", "PASS");
            LiveCursorIntentRecorder.recordAction(refused);

            // Live-shaped C4 truth rows. The recorder must honor any trustworthy finite numeric
            // expectation, including zero, instead of limiting mismatch detection to negative dy.
            LiveCursorIntentRecorder.recordAction(ordinaryAction(
                    "minecraft:bamboo_button", "164,-39,30", "164,-38,30",
                    "-1.500000", "-0.500000", "SUCCESS"));
            LiveCursorIntentRecorder.recordAction(ordinaryAction(
                    "minecraft:flower_pot", "174,-39,30", "174,-38,30",
                    "-1.500000", "-1.000000", "SUCCESS"));
            LiveCursorIntentRecorder.withActionOrigin(
                    LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                    () -> LiveCursorIntentRecorder.recordAction(ordinaryAction(
                            "minecraft:oak_fence", "184,-39,30", "184,-38,30",
                            "0.000000", "-0.500000", "SUCCESS")));
            LiveCursorIntentRecorder.withActionOrigin(
                    LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                    () -> LiveCursorIntentRecorder.recordAction(ordinaryAction(
                            "minecraft:conduit", "194,-39,30", "194,-38,30",
                            "-0.500000", "0.000000", "SUCCESS")));

            // Unknown expectations stay unknown and cannot be promoted to a generic green.
            LinkedHashMap<String, String> unknown = ordinaryAction(
                    "minecraft:lantern", "204,-39,30", "204,-38,30",
                    "unknown", "-1.000000", "SUCCESS");
            unknown.remove("expectedAfterDy");
            LiveCursorIntentRecorder.recordAction(unknown);

            // Schema 5 has a typed verdict contract, but this Fail[] row declares no expected refusal.
            // It therefore remains UNCLASSIFIED_FAILURE when no placement snapshot exists.
            LinkedHashMap<String, String> failed = ordinaryAction(
                    "minecraft:stone", "214,-39,30", "none",
                    "unknown", "none", "Fail[]");
            failed.put("actionType", "use_block");
            failed.put("afterState", "none");
            failed.put("afterLaneKind", "none");
            LiveCursorIntentRecorder.withActionOrigin(
                    LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                    () -> LiveCursorIntentRecorder.recordAction(failed));

            LiveCursorIntentRecorder.recordSentinel(sentinel(
                    "ENSEMBLE_OCCLUDED_OCCUPANCY", "200 -39 30"));
            LiveCursorIntentRecorder.recordSentinel(sentinel("ENSEMBLE_GAP", "210 -39 30"));
            LiveCursorIntentRecorder.recordSentinel(sentinel("ENSEMBLE_INTERPENETRATION", "215 -39 30"));
            LiveCursorIntentRecorder.recordSentinel(sentinel("MODEL_STALE_DIVERGENT", "216 -39 30"));
            LiveCursorIntentRecorder.recordSentinel(sentinel("MODEL_STALE_ABSENT", "217 -39 30"));
            LiveCursorIntentRecorder.recordSentinel(sentinel(
                    "MODEL_STALE_NO_BAKE_YELLOW", "220 -39 30"));
            LiveCursorIntentRecorder.recordSentinel(sentinel("UNKNOWN_DIAGNOSTIC", "230 -39 30"));
            LiveCursorIntentRecorder.flushSummaryForTests();

            assertContains(evidenceDir.resolve("session.jsonl"), "\"type\":\"cursor\"");
            assertContains(evidenceDir.resolve("session.jsonl"), "\"type\":\"rendered_outline\"");
            assertContains(evidenceDir.resolve("session.jsonl"), "LIVE_CURSOR_GHOST_SURFACE");
            assertContains(evidenceDir.resolve("rendered-outlines.tsv"), "LIVE_RENDERED_OUTLINE_LARGE_BOUNDS");
            assertContains(evidenceDir.resolve("rendered-outlines.tsv"), "LIVE_RENDERED_OUTLINE_REPLAY_BOUNDS_SPLIT");
            assertContains(evidenceDir.resolve("manifest.json"), "\"schemaVersion\":\"6\"");
            assertContains(evidenceDir.resolve("manifest.json"),
                    "\"recorderVersion\":\"26.2-recorder-truth-v8-logical-attempts\"");
            assertContains(evidenceDir.resolve("manifest.json"),
                    "\"actionOriginContract\":\"PLAYER_AUTHORED|AUTO_USEON_PROXY\"");
            assertContains(evidenceDir.resolve("manifest.json"),
                    "\"placementVerdictContract\":\"PlacementVerificationVerdict-v3\"");
            assertContains(evidenceDir.resolve("manifest.json"),
                    "\"logicalAttemptContract\":\"LogicalPlacementAttempt-v1\"");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "actionId\tcursorRowId\tactionType\tactionOrigin\theldItem");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "logicalAttemptId\tphase\tplayerProof");
            assertContains(evidenceDir.resolve("actions.tsv"), "3\t1\tplace_block\tPLAYER_AUTHORED");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "place_block\tAUTO_USEON_PROXY\tminecraft:stone_slab\t116,-39,30");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "117,-39,30\t-2.000000\t-2.000000\tlawful_lowered_lane\tanchored_full_block\tnone");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "place_block\tAUTO_USEON_PROXY\tminecraft:stone_slab\t118,-39,30");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "119,-39,30\t-2.000000\t-1.500000\tlawful_lowered_lane\tanchored_full_block\tLIVE_PLACEMENT_EXPECTED_DY_MISMATCH");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "105,-39,30\t-2.000000\t-2.000000\tlawful_lowered_lane\tunnamed_or_vanilla_slab\tnone");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "115,-39,30\t-2.000000\t-2.000000\tlawful_lowered_lane\tanchored_full_block\tnone");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "125,-39,30\t-2.000000\t-2.000000\tlawful_lowered_lane\tunnamed_or_vanilla_slab\tLIVE_PLACEMENT_EXPECTED_LANE_MISMATCH");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "135,-39,30\t-2.000000\t-1.500000\tlawful_lowered_lane\tanchored_full_block\tLIVE_PLACEMENT_EXPECTED_DY_MISMATCH");
            assertActionOnlyInconclusive(evidenceDir.resolve("session.jsonl"), "3");
            assertActionOnlyInconclusive(evidenceDir.resolve("session.jsonl"), "4");
            assertActionOnlyInconclusive(evidenceDir.resolve("session.jsonl"), "5");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "155,-39,30\tunknown\t-2.000000\tunknown\tunnamed_or_vanilla_slab\tnone");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "minecraft:bamboo_button\t164,-39,30\tUP\t164,-38,30\t-1.500000\t-0.500000");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "minecraft:flower_pot\t174,-39,30\tUP\t174,-38,30\t-1.500000\t-1.000000");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "minecraft:oak_fence\t184,-39,30\tUP\t184,-38,30\t0.000000\t-0.500000");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "minecraft:conduit\t194,-39,30\tUP\t194,-38,30\t-0.500000\t0.000000");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "minecraft:lantern\t204,-39,30\tUP\t204,-38,30\tunknown\t-1.000000"
                            + "\tunknown\tanchored_full_block\tnone");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "minecraft:stone\t214,-39,30\tUP\tnone\tunknown\tnone\tunknown\tnone"
                            + "\tLIVE_PLACEMENT_UNCLASSIFIED_FAILURE");
            assertLineContains(evidenceDir.resolve("session.jsonl"), "\"actionId\":\"16\"",
                    "\"finalVerdict\":\"UNCLASSIFIED_FAILURE\"",
                    "\"placedVerdict\":\"FAIL\"",
                    "\"anchorVerdict\":\"NOT_APPLICABLE\"",
                    "\"failureClasses\":\"UNDECLARED_PLACEMENT_FAILURE\"",
                    "\"verdictMarker\":\"LIVE_PLACEMENT_VERDICT_UNCLASSIFIED_FAILURE\"");
            assertContains(evidenceDir.resolve("actions.tsv"), "LIVE_PLACEMENT_VANILLA_DY_FROM_LOWERED_OWNER");
            assertOccurrences(evidenceDir.resolve("actions.tsv"), "LIVE_PLACEMENT_VERDICT_RED", 7);
            assertNotContains(evidenceDir.resolve("actions.tsv"), "LIVE_GREEN_PLACEMENT_AUTHORING");
            assertContains(evidenceDir.resolve("mismatches.tsv"),
                    "type\trowOrActionId\tmarker\tpos\theldItem\tfailureClasses");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_PLACEMENT_EXPECTED_DY_MISMATCH");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_PLACEMENT_EXPECTED_LANE_MISMATCH");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_PLACEMENT_UNCLASSIFIED_FAILURE");
            assertOccurrences(evidenceDir.resolve("mismatches.tsv"), "LIVE_PLACEMENT_VERDICT_RED", 14);
            assertNotContains(evidenceDir.resolve("mismatches.tsv"), "116,-39,30");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "118,-39,30");
            assertNotContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_GREEN_PLACEMENT_AUTHORING");
            assertOccurrences(evidenceDir.resolve("session.jsonl"), "\"finalVerdict\":\"RED\"", 14);
            assertNotContains(evidenceDir.resolve("session.jsonl"), "LIVE_GREEN_PLACEMENT_AUTHORING");
            assertContains(evidenceDir.resolve("session.jsonl"),
                    "\"severity\":\"info\",\"marker\":\"INFO_ENSEMBLE_OCCLUDED_OCCUPANCY\"");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_ENSEMBLE_GAP");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_ENSEMBLE_INTERPENETRATION");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_MODEL_STALE_DIVERGENT");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_MODEL_STALE_ABSENT");
            assertNotContains(evidenceDir.resolve("mismatches.tsv"), "ENSEMBLE_OCCLUDED_OCCUPANCY");
            assertNotContains(evidenceDir.resolve("mismatches.tsv"), "MODEL_STALE_NO_BAKE_YELLOW");
            assertNotContains(evidenceDir.resolve("mismatches.tsv"), "UNKNOWN_DIAGNOSTIC");
            assertContains(evidenceDir.resolve("summary.md"), "ghostSurfaceRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "renderedOutlineRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "renderedOutlineLargeBoundsRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "renderedOutlineReplayBoundsSplitRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "loweredSideSlabPlacementVanillaDyRows=2");
            assertContains(evidenceDir.resolve("summary.md"), "placementExpectedDyMismatchRows=7");
            assertContains(evidenceDir.resolve("summary.md"), "placementUnclassifiedFailureRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "placementExpectedLaneMismatchRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "liveGreenPlacementRows=0");
            assertContains(evidenceDir.resolve("summary.md"), "placementVerdictGreenRows=0");
            assertContains(evidenceDir.resolve("summary.md"), "placementVerdictRedRows=7");
            assertContains(evidenceDir.resolve("summary.md"), "placementVerdictInconclusiveRows=6");
            assertContains(evidenceDir.resolve("summary.md"), "placementVerdictExpectedRefusalRows=0");
            assertContains(evidenceDir.resolve("summary.md"), "placementVerdictUnclassifiedFailureRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "logicalAttemptRows=14");
            assertContains(evidenceDir.resolve("summary.md"), "mergedClientServerAttemptRows=0");
            assertContains(evidenceDir.resolve("summary.md"), "autoProxyLogicalAttemptRows=5");
            assertContains(evidenceDir.resolve("summary.md"), "serverOnlyLogicalAttemptRows=7");
            assertContains(evidenceDir.resolve("summary.md"), "clientOnlyLogicalAttemptRows=2");
            assertContains(evidenceDir.resolve("summary.md"), "playerProofLogicalAttemptRows=9");
            assertContains(evidenceDir.resolve("summary.md"), "logicalAttemptVerdictGreenRows=0");
            assertContains(evidenceDir.resolve("summary.md"), "logicalAttemptVerdictRedRows=7");
            assertContains(evidenceDir.resolve("summary.md"),
                    "logicalAttemptVerdictInconclusiveRows=6");
            assertContains(evidenceDir.resolve("summary.md"),
                    "logicalAttemptVerdictExpectedRefusalRows=0");
            assertContains(evidenceDir.resolve("summary.md"),
                    "logicalAttemptVerdictUnclassifiedFailureRows=1");
            assertContains(evidenceDir.resolve("summary.md"),
                    "playerProofGreenLogicalAttemptRows=0");
            assertContains(evidenceDir.resolve("summary.md"), "playerAuthoredActionRows=9");
            assertContains(evidenceDir.resolve("summary.md"), "autoUseOnProxyActionRows=5");
            assertContains(evidenceDir.resolve("summary.md"), "modelStaleDivergentRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "modelStaleAbsentRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "ensembleOccludedOccupancyInfoRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "ensembleClashRows=2");
            Slabbed.LOGGER.info(CLIENT_GAMETEST_PASS);
        } catch (Exception e) {
            throw new RuntimeException("[LIVE_CURSOR_INTENT_RECORDER_CONTRACT_RED] " + e.getMessage(), e);
        } finally {
            System.clearProperty("slabbed.liveCursorIntentRecorder");
            System.clearProperty("slabbed.liveCursorIntentRecorderDir");
            LiveCursorIntentRecorder.resetForTests();
        }
    }

    private static void runEmptyHandUsePacketCapture(ClientGameTestContext ctx) {
        Path evidenceDir = Path.of(
                "build", "test23-production-hook-empty-hand-use", "run-" + System.nanoTime());
        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, evidenceDir.toString());
        LiveCursorIntentRecorder.resetForTests();
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            singleplayer.getClientLevel().waitForChunksDownload();
            ctx.waitFor(client -> client.level != null
                    && client.player != null
                    && client.gameMode != null, 400);

            BlockPos target = singleplayer.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos pos = player.blockPosition().relative(player.getDirection(), 2)
                        .below().immutable();
                player.level().setBlock(
                        pos,
                        Blocks.OAK_FENCE_GATE.defaultBlockState(),
                        Block.UPDATE_ALL);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.inventoryMenu.sendAllDataToRemote();
                return pos;
            });

            ctx.waitFor(client -> client.player.getMainHandItem().isEmpty()
                    && client.level.getBlockState(target).is(Blocks.OAK_FENCE_GATE)
                    && !client.level.getBlockState(target).getValue(BlockStateProperties.OPEN), 400);
            ctx.runOnClient(client -> client.gameMode.useItemOn(
                    client.player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                            Vec3.atCenterOf(target),
                            Direction.UP,
                            target,
                            false)));
            ctx.waitFor(client -> client.level.getBlockState(target)
                    .getValue(BlockStateProperties.OPEN), 400);

            int authoritativeOpenAfterTicks = -1;
            int authoritativeOpenTimeoutTicks = 200;
            for (int elapsedTicks = 0; elapsedTicks < authoritativeOpenTimeoutTicks; elapsedTicks++) {
                boolean serverGateOpen = singleplayer.getServer().computeOnServer(server ->
                        server.overworld().getBlockState(target)
                                .getValue(BlockStateProperties.OPEN));
                if (serverGateOpen) {
                    authoritativeOpenAfterTicks = elapsedTicks;
                    break;
                }
                ctx.waitTick();
            }
            boolean serverGateOpen = authoritativeOpenAfterTicks >= 0;
            if (!serverGateOpen) {
                throw new AssertionError(
                        "TEST23_PRODUCTION_HOOK_WRONG_RED: real empty-hand use did not reach "
                                + "server fence-gate handling at " + target.toShortString());
            }
            System.out.println("[TEST23_GATE_A] authoritativeServerGateOpen=true"
                    + " target=" + target.toShortString()
                    + " observedAfterTicks=" + authoritativeOpenAfterTicks
                    + " timeoutTicks=" + authoritativeOpenTimeoutTicks);

            BlockPos rejectedTarget = target.offset(0, 0, 8);
            singleplayer.getServer().computeOnServer(server -> {
                server.overworld().setBlock(
                        rejectedTarget,
                        Blocks.OAK_FENCE_GATE.defaultBlockState(),
                        Block.UPDATE_ALL);
                return null;
            });
            ctx.waitFor(client -> client.level.getBlockState(rejectedTarget)
                    .is(Blocks.OAK_FENCE_GATE), 400);
            ctx.runOnClient(client -> client.gameMode.useItemOn(
                    client.player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                            Vec3.atCenterOf(rejectedTarget),
                            Direction.UP,
                            rejectedTarget,
                            false)));
            for (int tick = 0; tick < 20; tick++) {
                ctx.waitTick();
            }
            boolean rejectedServerGateClosed =
                    singleplayer.getServer().computeOnServer(server ->
                            !server.overworld().getBlockState(rejectedTarget)
                                    .getValue(BlockStateProperties.OPEN));
            if (!rejectedServerGateClosed) {
                throw new AssertionError(
                        "TEST23_GATE_B_WRONG_GREEN: distance-rejected server fence gate changed at "
                                + rejectedTarget.toShortString());
            }

            ctx.runOnClient(client -> LiveCursorIntentRecorder.flushSummaryForTests());
            Path session = evidenceDir.resolve("session.jsonl");
            List<JsonObject> rows = readJsonRows(session);
            assertMergedUseAttempt(
                    rows,
                    target,
                    "target_state_changed",
                    "TEST23_PRODUCTION_HOOK_RED: real empty-hand use opened the server fence gate "
                            + "but did not produce one correlated player-authored use attempt");
            assertMergedUseAttempt(
                    rows,
                    rejectedTarget,
                    "no_target_state_change_observed",
                    "TEST23_GATE_B_REJECTED_USE_RED: distance-rejected use did not produce one "
                            + "correlated player-authored use attempt");
        } catch (AssertionError error) {
            throw error;
        } catch (Exception error) {
            throw new RuntimeException("TEST23_PRODUCTION_HOOK_WRONG_RED: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage(), error);
        } finally {
            System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
            System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
            LiveCursorIntentRecorder.resetForTests();
        }
    }

    private static void runCursorProductionHook(ClientGameTestContext ctx) {
        runProductionVisualEvidenceHook(ctx, false);
    }

    /**
     * TEST 28 red: an ordinary held BlockItem must not make an already-lowered interactive target
     * fall back to vanilla's unshifted use validation. This deliberately exercises the real client
     * packet route; it does not call either target block's use method directly.
     */
    private static void runDeepHeldUsePacketRed(ClientGameTestContext ctx) {
        Path evidenceDir = Path.of(
                "tmp",
                "live-triage-20260723-124056",
                "test28-red",
                "deep-held-use-packet-" + System.nanoTime());
        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, evidenceDir.toString());
        LiveCursorIntentRecorder.resetForTests();
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            singleplayer.getClientLevel().waitForChunksDownload();
            ctx.waitFor(client -> client.level != null
                    && client.player != null
                    && client.gameMode != null, 400);

            List<BlockPos> targets = singleplayer.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos gate = player.blockPosition().relative(player.getDirection(), 2).immutable();
                BlockPos lever = gate.relative(player.getDirection().getClockWise()).immutable();
                var level = server.overworld();
                level.setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(lever, Blocks.LEVER.defaultBlockState(), Block.UPDATE_ALL);
                SlabAnchorAttachment.writePlacementDy(level, gate, -1.5d);
                SlabAnchorAttachment.writePlacementDy(level, lever, -1.5d);
                assertExactLoweredTarget(level, gate, "oak fence gate");
                assertExactLoweredTarget(level, lever, "lever");
                player.setItemInHand(InteractionHand.MAIN_HAND,
                        new ItemStack(Blocks.CORNFLOWER, DEEP_HELD_USE_STARTING_COUNT));
                player.inventoryMenu.sendAllDataToRemote();
                return List.of(gate, lever);
            });
            BlockPos gate = targets.getFirst();
            BlockPos lever = targets.get(1);

            ctx.waitFor(client -> client.player.getMainHandItem().is(Blocks.CORNFLOWER.asItem())
                    && client.level.getBlockState(gate).is(Blocks.OAK_FENCE_GATE)
                    && client.level.getBlockState(lever).is(Blocks.LEVER), 400);
            ctx.runOnClient(client -> client.gameMode.useItemOn(
                    client.player,
                    InteractionHand.MAIN_HAND,
                    translatedTargetCellHit(gate)));
            for (int tick = 0; tick < 20; tick++) {
                ctx.waitTick();
            }
            boolean gateOpen = singleplayer.getServer().computeOnServer(server ->
                    server.overworld().getBlockState(gate).getValue(BlockStateProperties.OPEN));
            assertHeldItemAndPlacementCellUnchanged(
                    singleplayer, gate, DEEP_HELD_USE_STARTING_COUNT, "gate");

            ctx.runOnClient(client -> client.gameMode.useItemOn(
                    client.player,
                    InteractionHand.MAIN_HAND,
                    translatedTargetCellHit(lever)));
            for (int tick = 0; tick < 20; tick++) {
                ctx.waitTick();
            }
            boolean leverPowered = singleplayer.getServer().computeOnServer(server ->
                    server.overworld().getBlockState(lever).getValue(BlockStateProperties.POWERED));
            assertHeldItemAndPlacementCellUnchanged(
                    singleplayer, lever, DEEP_HELD_USE_STARTING_COUNT, "lever");

            ctx.runOnClient(client -> LiveCursorIntentRecorder.flushSummaryForTests());
            List<JsonObject> rows = readJsonRows(evidenceDir.resolve("session.jsonl"));
            assertMergedHeldBlockUseAttempt(rows, gate, "oak fence gate");
            assertMergedHeldBlockUseAttempt(rows, lever, "lever");

            if (!gateOpen || !leverPowered) {
                throw new AssertionError(
                        "TEST28_DEEP_HELD_USE_RED: real held-cornflower packet reached lowered targets "
                                + "at stored/live dy=-1.5 but authoritative target use remained inactive; "
                                + "gateOpen=" + gateOpen + " leverPowered=" + leverPowered
                                + " gate=" + gate.toShortString()
                                + " lever=" + lever.toShortString());
            }
        } catch (AssertionError error) {
            throw error;
        } catch (Exception error) {
            throw new RuntimeException("TEST28_DEEP_HELD_USE_WRONG_RED: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage(), error);
        } finally {
            System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
            System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
            LiveCursorIntentRecorder.resetForTests();
        }
    }

    private static BlockHitResult translatedTargetCellHit(BlockPos target) {
        return new BlockHitResult(
                new Vec3(target.getX() + 0.5d, target.getY() - 1.0d, target.getZ() + 0.5d),
                Direction.UP,
                target,
                false);
    }

    private static void assertExactLoweredTarget(
            net.minecraft.server.level.ServerLevel level,
            BlockPos target,
            String targetName) {
        double storedDy = SlabAnchorAttachment.storedPlacementDy(level, target);
        double liveDy = SlabSupport.getYOffset(level, target, level.getBlockState(target));
        if (Double.doubleToRawLongBits(storedDy) != Double.doubleToRawLongBits(-1.5d)
                || Double.doubleToRawLongBits(liveDy) != Double.doubleToRawLongBits(-1.5d)) {
            throw new AssertionError("TEST28_DEEP_HELD_USE_WRONG_RED: " + targetName
                    + " setup did not retain stored/live dy=-1.5; stored=" + storedDy
                    + " live=" + liveDy);
        }
    }

    private static void assertHeldItemAndPlacementCellUnchanged(
            TestSingleplayerContext singleplayer,
            BlockPos target,
            int expectedCount,
            String targetName) {
        singleplayer.getServer().computeOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var held = player.getMainHandItem();
            var placementCell = target.above();
            var placementState = server.overworld().getBlockState(placementCell);
            if (!held.is(Blocks.CORNFLOWER.asItem()) || held.getCount() != expectedCount) {
                throw new AssertionError("TEST28_DEEP_HELD_USE_PLACEMENT_THEFT: " + targetName
                        + " held stack changed; expected minecraft:cornflower x" + expectedCount
                        + " but was " + held);
            }
            if (!placementState.isAir() || placementState.is(Blocks.CORNFLOWER)) {
                throw new AssertionError("TEST28_DEEP_HELD_USE_PLACEMENT_THEFT: " + targetName
                        + " potential placement cell " + placementCell.toShortString()
                        + " is not air/no-cornflower; state=" + placementState);
            }
            return null;
        });
    }

    private static void runRenderedOutlineProductionHook(ClientGameTestContext ctx) {
        runProductionVisualEvidenceHook(ctx, true);
    }

    private static void runProductionVisualEvidenceHook(
            ClientGameTestContext ctx,
            boolean requireRenderedOutline) {
        String evidenceCase = requireRenderedOutline ? "rendered-outline" : "cursor";
        String wrongRed = requireRenderedOutline
                ? "TEST23_RENDERED_OUTLINE_PRODUCTION_HOOK_WRONG_RED"
                : "TEST23_CURSOR_PRODUCTION_HOOK_WRONG_RED";
        String intendedRed = requireRenderedOutline
                ? "TEST23_RENDERED_OUTLINE_PRODUCTION_HOOK_RED"
                : "TEST23_CURSOR_PRODUCTION_HOOK_RED";
        String requiredType = requireRenderedOutline ? "rendered_outline" : "cursor";
        String summaryCounter =
                requireRenderedOutline ? "renderedOutlineRows=0" : "cursorRows=0";
        Path evidenceDir = Path.of(
                "tmp",
                "test23-gate-c-cursor-outline-red-20260720",
                evidenceCase + "-run-" + System.nanoTime());
        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, evidenceDir.toString());
        LiveCursorIntentRecorder.resetForTests();
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            singleplayer.getClientLevel().waitForChunksDownload();
            ctx.waitFor(client -> client.level != null && client.player != null, 400);

            BlockPos target = singleplayer.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos feet = player.blockPosition().immutable();
                for (int forward = 1; forward <= 2; forward++) {
                    for (int vertical = 0; vertical <= 2; vertical++) {
                        server.overworld().setBlock(
                                feet.offset(0, vertical, forward),
                                Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL);
                    }
                }
                BlockPos targetPos = feet.offset(0, 1, 3).immutable();
                server.overworld().setBlock(
                        targetPos,
                        Blocks.STONE.defaultBlockState(),
                        Block.UPDATE_ALL);
                return targetPos;
            });
            ctx.waitFor(client -> client.level.getBlockState(target).is(Blocks.STONE), 400);

            if (!SlabbedOffsetRaycast.ENABLED) {
                throw new AssertionError(
                        wrongRed + ": shipped offset-aware raycast is disabled");
            }
            ctx.runOnClient(client -> aimLocalPlayerAt(client, target, wrongRed));
            ctx.waitFor(client -> isExactBlockHit(client.hitResult, target), 400);

            BlockHitResult picked = ctx.computeOnClient(client ->
                    client.hitResult instanceof BlockHitResult blockHit ? blockHit : null);
            if (!isExactBlockHit(picked, target)) {
                throw new AssertionError(
                        wrongRed + ": normal Minecraft pick did not retain target "
                                + target.toShortString());
            }
            System.out.println("[TEST23_CURSOR_ROUTE_WITNESS]"
                    + " offsetRaycastEnabled=" + SlabbedOffsetRaycast.ENABLED
                    + " route=Minecraft.pick->LocalPlayer.raycastHitResult"
                    + "->LocalPlayerPickOffsetRaycastMixin"
                    + " hitType=" + picked.getType()
                    + " target=" + picked.getBlockPos().toShortString()
                    + " face=" + picked.getDirection().getName()
                    + " hit=" + picked.getLocation());

            if (requireRenderedOutline) {
                singleplayer.getClientLevel().waitForChunksRender();
                ctx.waitFor(client -> {
                    BlockOutlineRenderState outline = renderedOutlineState(client);
                    return outline != null && outline.pos().equals(target);
                }, 400);
                BlockOutlineRenderState outline =
                        ctx.computeOnClient(SlabbedLabLiveCursorIntentRecorderContractClientGameTest
                                ::renderedOutlineState);
                if (outline == null || !outline.pos().equals(target)) {
                    throw new AssertionError(
                            wrongRed + ": vanilla block-outline state did not retain target "
                                    + target.toShortString());
                }
                System.out.println("[TEST23_RENDERED_OUTLINE_ROUTE_WITNESS]"
                        + " route=LevelExtractor.extractBlockOutline"
                        + "->LevelRenderer.submitBlockOutline"
                        + " target=" + outline.pos().toShortString()
                        + " shapeEmpty=" + outline.shape().isEmpty());
            }

            ctx.runOnClient(client -> LiveCursorIntentRecorder.flushSummaryForTests());
            Path summary = evidenceDir.resolve("summary.md");
            if (!Files.isRegularFile(summary)) {
                throw new AssertionError(
                        wrongRed + ": recorder flush did not create " + summary);
            }
            List<JsonObject> rows =
                    readJsonRowsIfPresent(evidenceDir.resolve("session.jsonl"));
            boolean productionRowExists = rows.stream()
                    .anyMatch(row -> jsonEquals(row, "type", requiredType));
            if (!productionRowExists) {
                String summaryText = Files.readString(summary);
                if (!summaryText.contains(summaryCounter)) {
                    throw new AssertionError(
                            wrongRed + ": missing " + requiredType
                                    + " row without the expected zero counter");
                }
                throw new AssertionError(
                        intendedRed + ": real production route reached "
                                + target.toShortString()
                                + " but recorder emitted no type=" + requiredType + " row");
            }
        } catch (AssertionError error) {
            throw error;
        } catch (Exception error) {
            throw new RuntimeException(
                    wrongRed + ": " + error.getClass().getSimpleName()
                            + ": " + error.getMessage(),
                    error);
        } finally {
            System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
            System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
            LiveCursorIntentRecorder.resetForTests();
        }
    }

    private static void aimLocalPlayerAt(
            Minecraft client,
            BlockPos target,
            String wrongRed) {
        if (client.player == null) {
            throw new AssertionError(wrongRed + ": local player is unavailable");
        }
        Vec3 delta = Vec3.atCenterOf(target).subtract(client.player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        client.player.setYRot(yaw);
        client.player.setYHeadRot(yaw);
        client.player.setYBodyRot(yaw);
        client.player.setXRot(pitch);
    }

    private static boolean isExactBlockHit(HitResult hit, BlockPos target) {
        return hit instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(target);
    }

    private static BlockOutlineRenderState renderedOutlineState(Minecraft client) {
        try {
            Field field = LevelRenderer.class.getDeclaredField("levelRenderState");
            field.setAccessible(true);
            LevelRenderState state = (LevelRenderState) field.get(client.levelRenderer);
            return state.blockOutlineRenderState;
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(
                    "cannot inspect active LevelRenderer.levelRenderState", error);
        }
    }

    private static List<JsonObject> readJsonRowsIfPresent(Path session) throws Exception {
        if (!Files.exists(session)) {
            return List.of();
        }
        return readJsonRows(session);
    }

    private static List<JsonObject> readJsonRows(Path session) throws Exception {
        if (!Files.isRegularFile(session)) {
            throw new AssertionError("missing recorder session " + session);
        }
        ArrayList<JsonObject> rows = new ArrayList<>();
        for (String line : Files.readAllLines(session)) {
            if (!line.isBlank()) {
                rows.add(JsonParser.parseString(line).getAsJsonObject());
            }
        }
        return rows;
    }

    private static void assertMergedUseAttempt(
            List<JsonObject> rows,
            BlockPos target,
            String expectedFunctionalOutcome,
            String failurePrefix) {
        String targetText = target.toShortString();
        ArrayList<JsonObject> actions = new ArrayList<>();
        for (JsonObject row : rows) {
            if (jsonEquals(row, "type", "action")
                    && jsonEquals(row, "actionType", "use_block")
                    && jsonEquals(row, "actionOrigin", "PLAYER_AUTHORED")
                    && jsonEquals(row, "clickedOwnerPos", targetText)) {
                actions.add(row);
            }
        }
        JsonObject client = actions.stream()
                .filter(row -> jsonEquals(row, "side", "client"))
                .findFirst()
                .orElse(null);
        JsonObject server = actions.stream()
                .filter(row -> jsonEquals(row, "side", "server"))
                .findFirst()
                .orElse(null);
        String logicalAttemptId = jsonString(client, "logicalAttemptId");
        String sequence = jsonString(client, "packetSequence");
        List<JsonObject> terminals = rows.stream()
                .filter(row -> jsonEquals(row, "type", "placement_attempt"))
                .filter(row -> jsonEquals(row, "logicalAttemptId", logicalAttemptId))
                .filter(row -> jsonEquals(row, "attemptStatus", "MERGED_CLIENT_SERVER"))
                .filter(row -> jsonEquals(row, "actionCount", "2"))
                .toList();
        JsonObject terminal = terminals.size() == 1 ? terminals.getFirst() : null;
        boolean exactContract = actions.size() == 2
                && client != null
                && server != null
                && sequence != null
                && sequence.equals(jsonString(server, "packetSequence"))
                && logicalAttemptId != null
                && logicalAttemptId.equals(jsonString(server, "logicalAttemptId"))
                && terminal != null
                && jsonEquals(client, "heldItem", "empty")
                && jsonEquals(server, "heldItem", "empty")
                && jsonEquals(client, "clickedFace", "up")
                && jsonEquals(server, "clickedFace", "up")
                && hasTruthfulValue(client, "clickedHitVec")
                && hasTruthfulValue(server, "clickedHitVec")
                && jsonEquals(client, "beforeState", "unknown")
                && hasTruthfulValue(client, "afterState")
                && hasTruthfulValue(server, "beforeState")
                && hasTruthfulValue(server, "afterState")
                && hasTruthfulValue(server, "beforeDy")
                && hasTruthfulValue(server, "afterDy")
                && jsonEquals(server, "actualResult", "unknown")
                && jsonEquals(server, "validationDecision", "unknown_at_handler_boundary")
                && jsonEquals(server, "handlerDecision", "returned")
                && jsonEquals(server, "functionalOutcome", expectedFunctionalOutcome)
                && sameJsonField(server, terminal, "clickedHitVec")
                && sameJsonField(server, terminal, "beforeState")
                && sameJsonField(server, terminal, "beforeDy")
                && sameJsonField(server, terminal, "beforeStoredDy")
                && sameJsonField(server, terminal, "validationDecision")
                && sameJsonField(server, terminal, "handlerDecision")
                && sameJsonField(server, terminal, "functionalOutcome");
        if (!exactContract) {
            throw new AssertionError(
                    failurePrefix + " at " + targetText
                            + "; actions=" + actions
                            + ", terminals=" + terminals);
        }
    }

    private static void assertMergedHeldBlockUseAttempt(
            List<JsonObject> rows,
            BlockPos target,
            String targetName) {
        String targetText = target.toShortString();
        ArrayList<JsonObject> actions = new ArrayList<>();
        for (JsonObject row : rows) {
            if (jsonEquals(row, "type", "action")
                    && jsonEquals(row, "actionType", "use_block")
                    && jsonEquals(row, "actionOrigin", "PLAYER_AUTHORED")
                    && jsonEquals(row, "clickedOwnerPos", targetText)) {
                actions.add(row);
            }
        }
        JsonObject client = actions.stream()
                .filter(row -> jsonEquals(row, "side", "client"))
                .findFirst()
                .orElse(null);
        JsonObject server = actions.stream()
                .filter(row -> jsonEquals(row, "side", "server"))
                .findFirst()
                .orElse(null);
        String logicalAttemptId = jsonString(client, "logicalAttemptId");
        List<JsonObject> terminals = rows.stream()
                .filter(row -> jsonEquals(row, "type", "placement_attempt"))
                .filter(row -> jsonEquals(row, "logicalAttemptId", logicalAttemptId))
                .filter(row -> jsonEquals(row, "attemptStatus", "MERGED_CLIENT_SERVER"))
                .filter(row -> jsonEquals(row, "actionCount", "2"))
                .toList();
        JsonObject terminal = terminals.size() == 1 ? terminals.getFirst() : null;
        boolean exactContract = actions.size() == 2
                && client != null
                && server != null
                && jsonEquals(client, "heldItem", "minecraft:cornflower")
                && jsonEquals(server, "heldItem", "minecraft:cornflower")
                && jsonString(client, "packetSequence") != null
                && jsonString(client, "packetSequence").equals(jsonString(server, "packetSequence"))
                && logicalAttemptId != null
                && logicalAttemptId.equals(jsonString(server, "logicalAttemptId"))
                && terminal != null
                && hasTruthfulValue(client, "clickedHitVec")
                && hasTruthfulValue(server, "clickedHitVec")
                && hasTruthfulValue(server, "beforeState")
                && hasTruthfulValue(server, "afterState")
                && hasTruthfulValue(server, "functionalOutcome")
                && jsonEquals(server, "handlerDecision", "returned");
        if (!exactContract) {
            throw new AssertionError("TEST28_DEEP_HELD_USE_WRONG_RED: " + targetName
                    + " packet did not produce one merged player-authored attempt at " + targetText
                    + "; actions=" + actions + ", terminals=" + terminals);
        }
    }

    private static boolean sameJsonField(
            JsonObject expectedRow,
            JsonObject actualRow,
            String field) {
        String expected = jsonString(expectedRow, field);
        return expected != null && expected.equals(jsonString(actualRow, field));
    }

    private static boolean hasTruthfulValue(JsonObject row, String field) {
        String value = jsonString(row, field);
        return value != null
                && !value.isBlank()
                && !value.equals("none")
                && !value.equals("unknown")
                && !value.equals("not_run");
    }

    private static boolean jsonEquals(JsonObject row, String field, String expected) {
        return expected != null && expected.equals(jsonString(row, field));
    }

    private static String jsonString(JsonObject row, String field) {
        if (row == null || !row.has(field) || !row.get(field).isJsonPrimitive()) {
            return null;
        }
        return row.get(field).getAsString();
    }

    private static void assertContains(Path path, String needle) throws Exception {
        String text = Files.readString(path);
        if (!text.contains(needle)) {
            throw new RuntimeException("missing '" + needle + "' in " + path);
        }
    }

    private static void assertNotContains(Path path, String needle) throws Exception {
        String text = Files.readString(path);
        if (text.contains(needle)) {
            throw new RuntimeException("unexpected '" + needle + "' in " + path);
        }
    }

    private static void assertActionOnlyInconclusive(Path path, String actionId) throws Exception {
        assertLineContains(path, "\"actionId\":\"" + actionId + "\"",
                "\"finalVerdict\":\"INCONCLUSIVE\"",
                "\"placedVerdict\":\"PASS\"",
                "\"anchorVerdict\":\"MISSING\"",
                "\"modelVerdict\":\"MISSING\"",
                "\"collisionVerdict\":\"MISSING\"",
                "\"raycastVerdict\":\"MISSING\"",
                "\"outlineVerdict\":\"MISSING\"",
                "\"stabilityVerdict\":\"NOT_RUN\"",
                "\"intentDy\":\"-2.000000\"",
                "\"storedDy\":\"unknown\"",
                "\"modelDy\":\"unknown\"",
                "\"collisionDy\":\"unknown\"",
                "\"raycastDy\":\"unknown\"",
                "\"outlineDy\":\"unknown\"",
                "\"expectedSupportPlane\":\"unknown\"",
                "\"actualContactPlane\":\"unknown\"",
                "\"seatError\":\"unknown\"",
                "\"placementRoute\":\"unknown\"",
                "\"landingAuthority\":\"unknown\"",
                "\"rigCaseId\":\"unknown\"",
                "\"expectedRefusalReason\":\"unknown\"",
                "\"actualRefusalReason\":\"unknown\"",
                "\"missingRequiredComponents\":\"ANCHOR,MODEL,COLLISION,RAYCAST,OUTLINE,STABILITY\"",
                "\"failureClasses\":\"none\"",
                "\"verdictMarker\":\"LIVE_PLACEMENT_VERDICT_INCONCLUSIVE\"",
                "\"marker\":\"none\"");
    }

    private static void assertLineContains(Path path, String rowNeedle, String... needles) throws Exception {
        String row = Files.readString(path).lines()
                .filter(line -> line.contains(rowNeedle))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("missing row '" + rowNeedle + "' in " + path));
        for (String needle : needles) {
            if (!row.contains(needle)) {
                throw new RuntimeException("row '" + rowNeedle + "' missing '" + needle + "' in " + path);
            }
        }
    }

    private static void assertOccurrences(Path path, String needle, int expected) throws Exception {
        String text = Files.readString(path);
        int actual = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            actual++;
            index += needle.length();
        }
        if (actual != expected) {
            throw new RuntimeException(
                    "expected " + expected + " occurrences of '" + needle + "' in " + path + " but found " + actual);
        }
    }

    private static LinkedHashMap<String, String> loweredSideAction(
            String side, String ownerPos, String placementPos, String afterDy, String afterLane) {
        LinkedHashMap<String, String> action = new LinkedHashMap<>();
        action.put("actionType", "place_block");
        action.put("side", side);
        action.put("heldItem", "minecraft:stone_slab");
        action.put("clickedOwnerPos", ownerPos);
        action.put("clickedFace", "SOUTH");
        action.put("clickedOwnerLaneKind", "anchored_full_block");
        action.put("beforeDy", "-2.000000");
        action.put("placementPos", placementPos);
        action.put("afterState", "minecraft:stone_slab[type=bottom]");
        action.put("afterDy", afterDy);
        action.put("afterLaneKind", afterLane);
        action.put("actualResult", "SUCCESS");
        return action;
    }

    private static LinkedHashMap<String, String> ordinaryAction(
            String heldItem,
            String ownerPos,
            String placementPos,
            String expectedDy,
            String afterDy,
            String actualResult) {
        LinkedHashMap<String, String> action = new LinkedHashMap<>();
        action.put("actionType", "place_block");
        action.put("side", "server");
        action.put("heldItem", heldItem);
        action.put("clickedOwnerPos", ownerPos);
        action.put("clickedFace", "UP");
        action.put("clickedOwnerLaneKind", "anchored_full_block");
        action.put("beforeDy", "-1.500000");
        action.put("placementPos", placementPos);
        action.put("expectedAfterDy", expectedDy);
        action.put("expectedAfterLaneKind", "unknown");
        action.put("afterState", "Block{" + heldItem + "}");
        action.put("afterDy", afterDy);
        action.put("afterLaneKind", "anchored_full_block");
        action.put("actualResult", actualResult);
        return action;
    }

    private static LinkedHashMap<String, String> sentinel(String kind, String pos) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("kind", kind);
        row.put("pos", pos);
        return row;
    }

    private static Path freshEvidenceDir(Path evidenceDir) throws Exception {
        Files.createDirectories(evidenceDir);
        for (String artifact : new String[]{
                "manifest.json", "session.jsonl", "actions.tsv", "rendered-outlines.tsv",
                "mismatches.tsv", "summary.md"}) {
            Files.deleteIfExists(evidenceDir.resolve(artifact));
        }
        return evidenceDir;
    }
}
