package com.slabbed.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabAnchorAttachment.PlacementDyFact;
import com.slabbed.client.PlacementDyPredictionJournal;
import com.slabbed.client.PlacementDyPredictionJournalGameTestAccess;
import com.slabbed.client.SlabAnchorClientSync;
import com.slabbed.network.PlacementDyCorrectionPayload;
import com.slabbed.network.PlacementDyCorrectionServer;
import com.slabbed.network.PlacementDyPredictionBridge;
import com.slabbed.network.PlacementDyPredictionBridge.GroupSignature;
import com.slabbed.network.PlacementDyPredictionBridge.PredictedBatch;
import com.slabbed.network.PlacementDyPredictionBridge.PredictedCell;
import com.slabbed.placement.LandingResolver;
import com.slabbed.util.LiveCursorIntentRecorder;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One real entrypoint owning the frozen eighteen-case C3 client manifest. */
public final class PlacementDyPredictionClientGameTest implements FabricClientGameTest {
    private static final String CASE_SELECTOR_PROPERTY = "slabbed.c3.clientCase";
    private static final long NEGATIVE_ONE = Double.doubleToRawLongBits(-1.0d);
    private static final long POSITIVE_ZERO = Double.doubleToRawLongBits(+0.0d);
    private static final long NEGATIVE_ZERO = Double.doubleToRawLongBits(-0.0d);

    @FunctionalInterface
    private interface CaseBody {
        void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, int index)
                throws Exception;
    }

    private record ClientCase(String name, CaseBody body) {
    }

    private static final List<ClientCase> CASES = List.of(
            new ClientCase("accepted_and_equivalent_authority",
                    PlacementDyPredictionClientGameTest::acceptedAndEquivalentAuthority),
            new ClientCase("ack_before_correction",
                    (ctx, sp, index) -> reconciliationOrder(ctx, index, false)),
            new ClientCase("actual_network_round_trip",
                    PlacementDyPredictionClientGameTest::actualNetworkRoundTrip),
            new ClientCase("bridge_transaction_and_lifecycle_cleanup",
                    PlacementDyPredictionClientGameTest::bridgeTransactionAndLifecycleCleanup),
            new ClientCase("correction_before_ack",
                    (ctx, sp, index) -> reconciliationOrder(ctx, index, true)),
            new ClientCase("cross_chunk_pair_application",
                    PlacementDyPredictionClientGameTest::crossChunkPairApplication),
            new ClientCase("cross_dimension_declaration_purge",
                    PlacementDyPredictionClientGameTest::crossDimensionDeclarationPurge),
            new ClientCase("exact_fact_absent_to_present_positive_zero",
                    (ctx, sp, index) -> exactFact(ctx, index,
                            "exact_fact_absent_to_present_positive_zero",
                            PlacementDyFact.absent(), PlacementDyFact.present(+0.0d),
                            PlacementDyPredictionJournal.ReconcileBranch.CAPTURED_PRIOR_APPLIED,
                            1, PlacementDyFact.present(+0.0d))),
            new ClientCase("exact_fact_already_equal_present_positive_zero",
                    (ctx, sp, index) -> exactFact(ctx, index,
                            "exact_fact_already_equal_present_positive_zero",
                            PlacementDyFact.present(+0.0d), PlacementDyFact.present(+0.0d),
                            PlacementDyPredictionJournal.ReconcileBranch.ALREADY_EQUAL,
                            0, PlacementDyFact.present(+0.0d))),
            new ClientCase("exact_fact_present_negative_zero_third_state",
                    (ctx, sp, index) -> exactFact(ctx, index,
                            "exact_fact_present_negative_zero_third_state",
                            PlacementDyFact.present(-0.0d), PlacementDyFact.present(+0.0d),
                            PlacementDyPredictionJournal.ReconcileBranch.THIRD_STATE_PRESERVED,
                            0, PlacementDyFact.present(-0.0d))),
            new ClientCase("incomplete_correction_fails_closed",
                    PlacementDyPredictionClientGameTest::incompleteCorrectionFailsClosed),
            new ClientCase("malformed_pair_explicit_absence",
                    PlacementDyPredictionClientGameTest::malformedPairExplicitAbsence),
            new ClientCase("overlay_aware_root_aim_authority",
                    PlacementDyPredictionClientGameTest::overlayAwareRootAimAuthority),
            new ClientCase("post_install_recorder_real_door_bed_and_failure",
                    PlacementDyPredictionClientGameTest::postInstallRecorderRealDoorBedAndFailure),
            new ClientCase("refusal_double_to_single_and_nonadjacent_scaffolding_authority",
                    PlacementDyPredictionClientGameTest::refusalAndNonadjacentAuthority),
            new ClientCase("repeated_identical_newer_sequence_wins",
                    PlacementDyPredictionClientGameTest::repeatedIdenticalNewerSequenceWins),
            new ClientCase("same_chunk_unrelated_prediction_isolation",
                    PlacementDyPredictionClientGameTest::sameChunkUnrelatedPredictionIsolation),
            new ClientCase("third_state_authoritative_correction",
                    PlacementDyPredictionClientGameTest::thirdStateAuthoritativeCorrection));

    @Override
    public void runTest(ClientGameTestContext context) {
        int selectedCaseIndex = selectedCaseIndex();
        int caseCount = selectedCaseIndex < 0 ? CASES.size() : 1;
        Slabbed.LOGGER.info("C3_CLIENT_ENTRYPOINT | START | cases={}", caseCount);
        TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(true)
                .create();
        try {
            singleplayer.getClientLevel().waitForChunksDownload();
            context.waitFor(client -> client.level != null
                    && client.player != null
                    && client.gameMode != null, 400);
            for (int index = 0; index < CASES.size(); index++) {
                if (selectedCaseIndex >= 0 && index != selectedCaseIndex) {
                    continue;
                }
                ClientCase testCase = CASES.get(index);
                Slabbed.LOGGER.info("C3_CLIENT_CASE | {} | START", testCase.name());
                context.runOnClient(client ->
                        PlacementDyPredictionJournalGameTestAccess.beginTestProbe(client.level));
                try {
                    testCase.body().run(context, singleplayer, index);
                    Slabbed.LOGGER.info("C3_CLIENT_CASE | {} | PASS", testCase.name());
                } finally {
                    PlacementDyPredictionBridge.endTestWireTrace();
                    PlacementDyCorrectionServer.endCorrectionSendTraceForTests();
                    context.runOnClient(client ->
                            PlacementDyPredictionJournalGameTestAccess.endTestProbe(client.level));
                }
            }
            Slabbed.LOGGER.info("C3_CLIENT_ENTRYPOINT | PASS | cases={}", caseCount);
        } catch (Throwable throwable) {
            throw new AssertionError("C3 client manifest failed", throwable);
        } finally {
            singleplayer.close();
        }
    }

    private static int selectedCaseIndex() {
        for (int left = 0; left < CASES.size(); left++) {
            for (int right = left + 1; right < CASES.size(); right++) {
                if (CASES.get(left).name().equals(CASES.get(right).name())) {
                    throw rejectSelector("ambiguous_manifest");
                }
            }
        }

        String requested = System.getProperty(CASE_SELECTOR_PROPERTY);
        if (requested == null) {
            return -1;
        }
        if (requested.isEmpty()) {
            throw rejectSelector("empty_case_request");
        }
        if (requested.indexOf(',') >= 0) {
            String[] requestedCases = requested.split(",", -1);
            for (int left = 0; left < requestedCases.length; left++) {
                for (int right = left + 1; right < requestedCases.length; right++) {
                    if (requestedCases[left].equals(requestedCases[right])) {
                        throw rejectSelector("duplicate_case_request");
                    }
                }
            }
            throw rejectSelector("multiple_case_request");
        }
        if (!requested.matches("[a-z0-9_]+")) {
            throw rejectSelector("invalid_case_request");
        }

        for (int index = 0; index < CASES.size(); index++) {
            if (CASES.get(index).name().equals(requested)) {
                Slabbed.LOGGER.info("C3_CLIENT_SELECTOR | SELECT | case={} | index={}", requested, index);
                return index;
            }
        }

        int prefixMatches = 0;
        for (ClientCase testCase : CASES) {
            if (testCase.name().startsWith(requested)) {
                prefixMatches++;
            }
        }
        throw rejectSelector(prefixMatches > 1 ? "ambiguous_case_request" : "unknown_case_request");
    }

    private static IllegalArgumentException rejectSelector(String reason) {
        Slabbed.LOGGER.error("C3_CLIENT_SELECTOR | REJECT | reason={}", reason);
        return new IllegalArgumentException("Rejected C3 client case selector: " + reason);
    }

    private static void acceptedAndEquivalentAuthority(
            ClientGameTestContext context, TestSingleplayerContext ignored, int index
    ) throws Exception {
        context.runOnClient(client -> {
            ClientLevel level = client.level;
            BlockPos pos = testPos(client, index);
            int sequence = sequence(index, 0);
            PredictedBatch batch = batch(level, sequence, List.of(pos), NEGATIVE_ONE);
            PlacementDyPredictionJournalGameTestAccess.installBatchForTests(level, batch);
            SlabAnchorClientSync.resetC3RerenderProbeCountsForTests();
            PlacementDyPredictionJournal.onCorrection(level, correction(batch,
                    Map.of(pos, PlacementDyFact.present(-1.0d))));
            expect(PlacementDyPredictionJournal.debugCell(pos).groupPresent(),
                    "correction-first equivalent authority retired before ack");
            PlacementDyPredictionJournal.onVanillaAcknowledgement(level, sequence);
            expectFact(PlacementDyPredictionJournal.backingFact(level, pos), true, NEGATIVE_ONE,
                    "equivalent authority backing");
            expectFact(PlacementDyPredictionJournal.effectiveFact(level, pos), true, NEGATIVE_ONE,
                    "equivalent authority effective");
            expect(!PlacementDyPredictionJournal.debugCell(pos).groupPresent(),
                    "equivalent authority group not retired");
        });
    }

    private static void reconciliationOrder(
            ClientGameTestContext context, int index, boolean correctionFirst
    ) throws Exception {
        String caseName = correctionFirst ? "correction_before_ack" : "ack_before_correction";
        context.runOnClient(client -> {
            ClientLevel level = client.level;
            BlockPos pos = testPos(client, index);
            int sequence = sequence(index, 0);
            PredictedBatch batch = batch(level, sequence, List.of(pos), NEGATIVE_ONE);
            PlacementDyPredictionJournalGameTestAccess.installBatchForTests(level, batch);
            SlabAnchorClientSync.resetC3RerenderProbeCountsForTests();
            PlacementDyCorrectionPayload correction = correction(batch,
                    Map.of(pos, PlacementDyFact.present(-1.0d)));
            if (correctionFirst) {
                PlacementDyPredictionJournal.onCorrection(level, correction);
            } else {
                PlacementDyPredictionJournal.onVanillaAcknowledgement(level, sequence);
            }
            PlacementDyPredictionJournal.CellDebug first = PlacementDyPredictionJournal.debugCell(pos);
            PlacementDyPredictionJournalGameTestAccess.TestSnapshot firstSnapshot =
                    PlacementDyPredictionJournalGameTestAccess.testSnapshot();
            expect(first.groupPresent() && first.overlayOwned(), "first signal lost overlay ownership");
            expect(first.highWaterSequence() == sequence, "first signal changed high-water sequence");
            expect(first.correctionBuffered() == correctionFirst,
                    "first signal correction-buffer state mismatch");
            expect(first.acknowledged() != correctionFirst,
                    "first signal acknowledgement state mismatch");
            expect(firstSnapshot.backingWrites() == 0, "first signal wrote authoritative backing");
            expectFact(first.effective(), true, NEGATIVE_ONE, "first-signal effective overlay");
            expectFact(first.backing(), false, 0L, "first-signal backing");
            Slabbed.LOGGER.info("C3_RECONCILE | {} | FIRST_SIGNAL_RETAINED", caseName);

            if (correctionFirst) {
                PlacementDyPredictionJournal.onVanillaAcknowledgement(level, sequence);
            } else {
                PlacementDyPredictionJournal.onCorrection(level, correction);
            }
            PlacementDyPredictionJournalGameTestAccess.TestSnapshot reconciled =
                    PlacementDyPredictionJournalGameTestAccess.testSnapshot();
            expect(reconciled.authorityObservedWhileOwned(),
                    "authority was not observed while matching overlay ownership existed");
            expect(reconciled.backingWrites() == 1, "differing authority did not perform one write");
            Slabbed.LOGGER.info("C3_RECONCILE | {} | AUTHORITY_APPLIED_WHILE_OWNED", caseName);

            PlacementDyPredictionJournal.CellDebug retired = PlacementDyPredictionJournal.debugCell(pos);
            expect(!retired.groupPresent() && !retired.overlayOwned(), "matching group not retired");
            expect(retired.highWaterSequence() == sequence, "normal retirement lost high-water sequence");
            expect(reconciled.activeGroups() == 0 && reconciled.bufferedCorrections() == 0,
                    "matching journal/correction buffers survived retirement");
            Slabbed.LOGGER.info(
                    "C3_RECONCILE | {} | GROUP_RETIRED_BUFFERS_CLEARED_HIGH_WATER_RETAINED",
                    caseName);

            expectFact(retired.backing(), true, NEGATIVE_ONE, "retired authoritative backing");
            expectFact(retired.effective(), true, NEGATIVE_ONE, "retired effective authority");
            expect(SlabAnchorClientSync.c3RerenderCountForTests(pos) == 1,
                    "retired cell did not receive exactly one rerender");
            Slabbed.LOGGER.info(
                    "C3_RECONCILE | {} | EFFECTIVE_AUTHORITY_VISIBLE_RERENDERED", caseName);
        });
    }

    private static void actualNetworkRoundTrip(
            ClientGameTestContext context, TestSingleplayerContext singleplayer, int ignored
    ) throws Exception {
        PlacementDyCorrectionServer.beginCorrectionSendTraceForTests();
        BlockPos owner = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            BlockPos pos = player.blockPosition().relative(player.getDirection(), 2).below().immutable();
            player.level().setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            player.level().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            player.level().setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            SlabAnchorAttachment.writePlacementDy(player.level(), pos, -1.0d);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_DOOR));
            player.inventoryMenu.sendAllDataToRemote();
            return pos;
        });
        context.waitFor(client -> client.player.getMainHandItem().is(Items.OAK_DOOR)
                && PlacementDyPredictionJournal.backingFact(client.level, owner).equals(
                PlacementDyFact.present(-1.0d)), 400);
        context.runOnClient(client -> {
            BlockPos target = owner.above();
            setBacking(client.level, Map.of(target, PlacementDyFact.present(+0.0d)));
            PlacementDyPredictionBridge.beginTestWireTrace();
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(owner).add(0.0d, 0.5d, 0.0d),
                    Direction.UP,
                    owner,
                    false);
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        });
        context.waitFor(client -> PlacementDyPredictionBridge.snapshotTestWirePhases().size() >= 3, 400);
        context.runOnClient(client -> {
            BlockPos target = owner.above();
            expect(PlacementDyPredictionBridge.snapshotTestWirePhases().equals(
                            List.of("SEND", "RECEIVE", "APPLY")),
                    "real correction wire phases were not SEND/RECEIVE/APPLY exactly once");
            expect(PlacementDyPredictionJournalGameTestAccess.testSnapshot().backingWrites() > 0,
                    "real wire case had no payload-driven differing backing write");
            expectFact(PlacementDyPredictionJournal.backingFact(client.level, target),
                    false, 0L, "real refusal correction removed stale client backing");
            expect(!PlacementDyPredictionJournal.debugCell(target).groupPresent(),
                    "real wire group was not retired after correction and ack");
            expect(PlacementDyCorrectionServer.correctionSendCountForTests() == 1,
                    "declared refusal did not send exactly one correction");
            expect(PlacementDyCorrectionServer.correctionFinishOutcomesForTests().equals(
                            List.of("DECLARED_SENT")),
                    "declared refusal did not finish as one declaration-owned correction");
            Slabbed.LOGGER.info("C3_DECLARATION_GATE | declared_refusal | corrections=1 | PASS");
        });
    }

    private static void bridgeTransactionAndLifecycleCleanup(
            ClientGameTestContext context, TestSingleplayerContext ignored, int index
    ) throws Exception {
        context.runOnClient(client -> {
            ClientLevel level = client.level;
            BlockPos pos = testPos(client, index);
            int sequence = sequence(index, 0);
            PredictedBatch batch = batch(level, sequence, List.of(pos), NEGATIVE_ONE);
            PlacementDyPredictionBridge.openSequence(sequence);
            try {
                expect(PlacementDyPredictionBridge.publishClientBatch(batch),
                        "installed bridge callback rejected a valid transaction");
            } finally {
                PlacementDyPredictionBridge.closeSequence();
            }
            expect(PlacementDyPredictionJournalGameTestAccess.testSnapshot().stagedGroups() == 1,
                    "bridge transaction did not stage exactly one group");
            expectFact(PlacementDyPredictionJournal.backingFact(level, pos), false, 0L,
                    "prediction-time authoritative backing");
            PlacementDyPredictionJournalGameTestAccess.commitStagedBatchForTests(level, sequence);
            expectFact(PlacementDyPredictionJournal.effectiveFact(level, pos), true, NEGATIVE_ONE,
                    "committed bridge overlay");
            expectFact(PlacementDyPredictionJournal.backingFact(level, pos), false, 0L,
                    "committed bridge backing unchanged");
            PlacementDyPredictionJournalGameTestAccess.resetForTests(level);
            expect(PlacementDyPredictionJournalGameTestAccess.testSnapshot().activeGroups() == 0,
                    "world/connection reset left an active group");
            expectFact(PlacementDyPredictionJournal.backingFact(level, pos), false, 0L,
                    "world/connection cleanup edited authoritative backing");
        });
        oneSidedUnload(context, index, true);
        oneSidedUnload(context, index, false);
    }

    private static void oneSidedUnload(
            ClientGameTestContext context, int index, boolean correctionFirst
    ) throws Exception {
        String order = correctionFirst ? "correction_first" : "ack_first";
        context.runOnClient(client -> {
            ClientLevel level = client.level;
            PlacementDyPredictionJournalGameTestAccess.beginTestProbe(level);
            BlockPos[] cross = crossChunkPair(client, index + (correctionFirst ? 20 : 30));
            BlockPos unloading = cross[0];
            BlockPos survivor = cross[1];
            BlockPos unrelated = survivor.east(2);
            int sequence = sequence(index, correctionFirst ? 20 : 30);
            PredictedBatch pair = batch(level, sequence, List.of(unloading, survivor), NEGATIVE_ONE);
            PredictedBatch other = batch(level, sequence + 1, List.of(unrelated), NEGATIVE_ZERO);
            PlacementDyPredictionJournalGameTestAccess.installBatchForTests(level, pair);
            PlacementDyPredictionJournalGameTestAccess.installBatchForTests(level, other);
            setBacking(level, Map.of(survivor, PlacementDyFact.present(+0.0d)));
            PlacementDyFact before = PlacementDyPredictionJournal.backingFact(level, survivor);
            if (correctionFirst) {
                PlacementDyPredictionJournal.onCorrection(level, correction(pair,
                        Map.of(unloading, PlacementDyFact.absent(),
                                survivor, PlacementDyFact.present(+0.0d))));
            } else {
                PlacementDyPredictionJournal.onVanillaAcknowledgement(level, sequence);
            }
            SlabAnchorClientSync.resetC3RerenderProbeCountsForTests();
            PlacementDyPredictionJournalGameTestAccess.unloadChunkForTests(
                    level, unloading.getX() >> 4, unloading.getZ() >> 4);
            PlacementDyFact after = PlacementDyPredictionJournal.backingFact(level, survivor);
            expect(before.equals(after), "one-sided unload edited survivor authoritative backing");
            Slabbed.LOGGER.info(
                    "C3_GROUP_CLEANUP | one_sided_cross_chunk_unload | {} | AUTHORITATIVE_BACKING_UNCHANGED | PASS",
                    order);
            expectFact(PlacementDyPredictionJournal.effectiveFact(level, survivor),
                    true, POSITIVE_ZERO, "one-sided unload survivor effective authority");
            Slabbed.LOGGER.info(
                    "C3_GROUP_CLEANUP | one_sided_cross_chunk_unload | {} | SURVIVING_CELL_EFFECTIVE_AUTHORITY | PASS",
                    order);
            expect(SlabAnchorClientSync.c3RerenderCountForTests(survivor) == 1,
                    "one-sided unload did not rerender surviving loaded cell exactly once");
            Slabbed.LOGGER.info(
                    "C3_GROUP_CLEANUP | one_sided_cross_chunk_unload | {} | SURVIVING_CELL_RERENDERED | PASS",
                    order);
            expect(!PlacementDyPredictionJournal.debugCell(survivor).groupPresent(),
                    "one-sided unload left partial pair ownership");
            expect(PlacementDyPredictionJournal.debugCell(unrelated).groupPresent(),
                    "one-sided unload retired unrelated newer group");
            expect(PlacementDyPredictionJournalGameTestAccess.testSnapshot().bufferedCorrections() == 0,
                    "one-sided unload left pair correction provenance");
            Slabbed.LOGGER.info(
                    "C3_GROUP_CLEANUP | one_sided_cross_chunk_unload | {} | PASS", order);
        });
    }

    private static void crossChunkPairApplication(
            ClientGameTestContext context, TestSingleplayerContext ignored, int index
    ) throws Exception {
        context.runOnClient(client -> {
            ClientLevel level = client.level;
            BlockPos[] positions = crossChunkPair(client, index);
            int sequence = sequence(index, 0);
            PredictedBatch batch = batch(level, sequence, List.of(positions), NEGATIVE_ONE);
            PlacementDyPredictionJournalGameTestAccess.installBatchForTests(level, batch);
            PlacementDyPredictionJournal.onCorrection(level, correction(batch, Map.of(
                    positions[0], PlacementDyFact.present(-1.0d),
                    positions[1], PlacementDyFact.present(-1.0d))));
            PlacementDyPredictionJournal.onVanillaAcknowledgement(level, sequence);
            expect(PlacementDyPredictionJournalGameTestAccess.testSnapshot().backingWrites() == 2,
                    "cross-chunk pair did not apply two differing facts");
            for (BlockPos pos : positions) {
                expectFact(PlacementDyPredictionJournal.effectiveFact(level, pos),
                        true, NEGATIVE_ONE, "cross-chunk effective authority");
            }
        });
    }

    private static void exactFact(
            ClientGameTestContext context,
            int index,
            String caseName,
            PlacementDyFact current,
            PlacementDyFact payload,
            PlacementDyPredictionJournal.ReconcileBranch expectedBranch,
            int expectedWrites,
            PlacementDyFact expectedEffective
    ) throws Exception {
        context.runOnClient(client -> {
            ClientLevel level = client.level;
            BlockPos pos = testPos(client, index);
            int sequence = sequence(index, 0);
            PredictedBatch batch = batch(level, sequence, List.of(pos), POSITIVE_ZERO);
            PlacementDyPredictionJournalGameTestAccess.installBatchForTests(level, batch);
            setBacking(level, Map.of(pos, current));
            SlabAnchorClientSync.resetC3RerenderProbeCountsForTests();
            PlacementDyPredictionJournal.onCorrection(level, correction(batch, Map.of(pos, payload)));
            PlacementDyPredictionJournal.onVanillaAcknowledgement(level, sequence);
            PlacementDyPredictionJournalGameTestAccess.TestSnapshot snapshot =
                    PlacementDyPredictionJournalGameTestAccess.testSnapshot();
            expect(snapshot.branches().get(pos.asLong()) == expectedBranch,
                    "exact-fact branch mismatch for " + caseName);
            expect(snapshot.backingWrites() == expectedWrites,
                    "exact-fact backing-write count mismatch for " + caseName);
            expect(!PlacementDyPredictionJournal.debugCell(pos).groupPresent(),
                    "exact-fact matching group not retired for " + caseName);
            expect(PlacementDyPredictionJournal.debugCell(pos).highWaterSequence() == sequence,
                    "exact-fact normal retirement lost high-water for " + caseName);
            expect(PlacementDyPredictionJournal.effectiveFact(level, pos).equals(expectedEffective),
                    "exact-fact effective authority mismatch for " + caseName);
            expect(SlabAnchorClientSync.c3RerenderCountForTests(pos) == 1,
                    "exact-fact rerender count mismatch for " + caseName);
            Slabbed.LOGGER.info(
                    "C3_EXACT_FACT | {} | PRIOR=absent:0000000000000000 | PAYLOAD=present:0000000000000000 | CURRENT={} | BRANCH={} | BACKING_WRITES={} | GROUP_RETIRED=1 | EFFECTIVE={} | RERENDERS=1 | PASS",
                    caseName,
                    factText(current),
                    expectedBranch,
                    expectedWrites,
                    factText(expectedEffective));
        });
    }

    private static void malformedPairExplicitAbsence(
            ClientGameTestContext context, TestSingleplayerContext ignored, int index
    ) throws Exception {
        context.runOnClient(client -> {
            ClientLevel level = client.level;
            BlockPos first = testPos(client, index);
            BlockPos second = first.above();
            int sequence = sequence(index, 0);
            PredictedBatch batch = batch(level, sequence, List.of(first, second), NEGATIVE_ONE);
            PlacementDyPredictionJournalGameTestAccess.installBatchForTests(level, batch);
            PlacementDyPredictionJournal.onCorrection(level, correction(batch, Map.of(
                    first, PlacementDyFact.absent(), second, PlacementDyFact.absent())));
            PlacementDyPredictionJournal.onVanillaAcknowledgement(level, sequence);
            expectFact(PlacementDyPredictionJournal.effectiveFact(level, first), false, 0L,
                    "malformed pair first explicit absence");
            expectFact(PlacementDyPredictionJournal.effectiveFact(level, second), false, 0L,
                    "malformed pair second explicit absence");
            expect(PlacementDyPredictionJournalGameTestAccess.testSnapshot().activeGroups() == 0,
                    "malformed pair explicit absence did not retire matching group");
        });
    }

    private static void crossDimensionDeclarationPurge(
            ClientGameTestContext context, TestSingleplayerContext ignored, int index
    ) throws Exception {
        context.runOnClient(client -> {
            String currentDimension = client.level.dimension().toString();
            int incomingSequence = sequence(index, 0);
            GroupSignature staleDimensionLow = signatureForTest(
                    currentDimension + "#stale", incomingSequence - 1, index);
            GroupSignature staleDimensionHigh = signatureForTest(
                    currentDimension + "#stale", incomingSequence + 100, index + 1);
            GroupSignature staleSequence = signatureForTest(
                    currentDimension, incomingSequence - 1, index + 2);
            GroupSignature retained = signatureForTest(
                    currentDimension, incomingSequence, index + 3);

            List<GroupSignature> actual = PlacementDyCorrectionServer.retainedDeclarationSignaturesForTests(
                    List.of(staleDimensionLow, staleDimensionHigh, staleSequence, retained),
                    currentDimension,
                    incomingSequence);
            expect(actual.equals(List.of(retained)),
                    "cross-dimension declarations survived the pre-capacity purge: " + actual);
        });
    }

    private static void incompleteCorrectionFailsClosed(
            ClientGameTestContext context, TestSingleplayerContext ignored, int index
    ) throws Exception {
        context.runOnClient(client -> {
            ClientLevel level = client.level;
            BlockPos first = testPos(client, index);
            BlockPos second = first.above();
            int sequence = sequence(index, 0);
            PredictedBatch batch = batch(level, sequence, List.of(first, second), NEGATIVE_ONE);
            PlacementDyPredictionJournalGameTestAccess.installBatchForTests(level, batch);
            SlabAnchorClientSync.resetC3RerenderProbeCountsForTests();

            PlacementDyPredictionJournal.onCorrection(level, correction(batch, Map.of(
                    first, PlacementDyFact.present(-1.0d))));

            PlacementDyPredictionJournalGameTestAccess.TestSnapshot snapshot =
                    PlacementDyPredictionJournalGameTestAccess.testSnapshot();
            expect(snapshot.activeGroups() == 0 && snapshot.overlayOwners() == 0
                            && snapshot.bufferedCorrections() == 0,
                    "incomplete correction left prediction ownership active");
            expectFact(PlacementDyPredictionJournal.effectiveFact(level, first),
                    false, 0L, "incomplete correction first fail-closed authority");
            expectFact(PlacementDyPredictionJournal.effectiveFact(level, second),
                    false, 0L, "incomplete correction second fail-closed authority");
            expect(PlacementDyPredictionJournal.debugCell(first).highWaterSequence() == sequence
                            && PlacementDyPredictionJournal.debugCell(second).highWaterSequence() == sequence,
                    "incomplete correction discarded normal high-water protection");
            expect(SlabAnchorClientSync.c3RerenderCountForTests(first) == 1
                            && SlabAnchorClientSync.c3RerenderCountForTests(second) == 1,
                    "incomplete correction did not rerender each retired owned cell exactly once");
        });
    }

    private static void overlayAwareRootAimAuthority(
            ClientGameTestContext context, TestSingleplayerContext ignored, int index
    ) throws Exception {
        context.runOnClient(client -> {
            ClientLevel level = client.level;
            BlockPos owner = testPos(client, index);
            level.setBlock(owner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            setBacking(level, Map.of(owner, PlacementDyFact.absent()));
            PredictedBatch overlay = batch(level, sequence(index, 0), List.of(owner), NEGATIVE_ONE);
            PlacementDyPredictionJournalGameTestAccess.installBatchForTests(level, overlay);

            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(owner), Direction.UP, owner, false);
            double captured = LandingResolver.captureAim(
                    new UseOnContext(client.player, InteractionHand.MAIN_HAND, hit)).ownerVisibleDy();
            expectFact(PlacementDyPredictionJournal.backingFact(level, owner),
                    false, 0L, "root-aim raw backing remains absent");
            expect(Double.doubleToRawLongBits(captured) == NEGATIVE_ONE,
                    "root aim bypassed active overlay; captured=" + captured);
        });
    }

    private record RecorderFixture(BlockPos doorOwner, BlockPos bedOwner, BlockPos failedOwner) {
    }

    private static void postInstallRecorderRealDoorBedAndFailure(
            ClientGameTestContext context, TestSingleplayerContext singleplayer, int ignored
    ) throws Exception {
        Path recorderDir = Path.of("build", "c3-client-recorder-post-install", "run-" + System.nanoTime());
        context.runOnClient(client -> {
            System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
            System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, recorderDir.toString());
            LiveCursorIntentRecorder.resetForTests();
        });
        PlacementDyCorrectionServer.beginCorrectionSendTraceForTests();
        try {
            RecorderFixture fixture = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                Direction forward = player.getDirection();
                BlockPos doorOwner = player.blockPosition().relative(forward, 2).below().immutable();
                BlockPos bedOwner = player.blockPosition().relative(forward.getClockWise(), 2).below().immutable();
                BlockPos failedOwner = player.blockPosition().relative(forward.getCounterClockWise(), 2)
                        .below().immutable();
                prepareRecorderOwner(player, doorOwner);
                prepareRecorderOwner(player, failedOwner);
                prepareRecorderOwner(player, bedOwner);
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    prepareRecorderOwner(player, bedOwner.relative(direction));
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_DOOR, 4));
                player.inventoryMenu.sendAllDataToRemote();
                return new RecorderFixture(doorOwner, bedOwner, failedOwner);
            });

            waitForHeldAndOwner(context, Items.OAK_DOOR, fixture.doorOwner());
            useOnRecorderOwner(context, fixture.doorOwner());
            context.waitFor(client -> recorderAction(
                    recorderDir, "client", "minecraft:oak_door", fixture.doorOwner()) != null, 400);
            context.runOnClient(client -> assertRecorderPair(
                    recorderAction(recorderDir, "client", "minecraft:oak_door", fixture.doorOwner()),
                    "upper",
                    "real client door"));
            context.runOnClient(client -> expect(recorderActionCount(
                            recorderDir, "client", "minecraft:oak_door", fixture.doorOwner()) == 1,
                    "real client door recorder action was duplicated"));
            context.waitFor(client -> PlacementDyCorrectionServer.correctionFinishOutcomesForTests().size() >= 1,
                    400);
            context.runOnClient(client -> expect(
                    PlacementDyCorrectionServer.correctionFinishOutcomesForTests().equals(
                            List.of("DECLARED_SENT")),
                    "real door placement did not finish as one declaration-owned correction"));

            BlockPos door = fixture.doorOwner().above();
            useOnDoor(context, door);
            context.waitFor(client -> client.level.getBlockState(door).hasProperty(BlockStateProperties.OPEN)
                    && client.level.getBlockState(door).getValue(BlockStateProperties.OPEN), 400);
            context.waitFor(client -> PlacementDyCorrectionServer.correctionFinishOutcomesForTests().size() >= 2,
                    400);
            context.runOnClient(client -> expect(
                    PlacementDyCorrectionServer.correctionFinishOutcomesForTests().equals(
                            List.of("DECLARED_SENT", "UNDECLARED_NO_SCOPE")),
                    "held-door open sent a correction without a client declaration: "
                            + PlacementDyCorrectionServer.correctionFinishOutcomesForTests()));
            useOnDoor(context, door);
            context.waitFor(client -> client.level.getBlockState(door).hasProperty(BlockStateProperties.OPEN)
                    && !client.level.getBlockState(door).getValue(BlockStateProperties.OPEN), 400);
            context.waitFor(client -> PlacementDyCorrectionServer.correctionFinishOutcomesForTests().size() >= 3,
                    400);
            context.runOnClient(client -> {
                expect(PlacementDyCorrectionServer.correctionFinishOutcomesForTests().equals(List.of(
                                "DECLARED_SENT", "UNDECLARED_NO_SCOPE", "UNDECLARED_NO_SCOPE")),
                        "held-door close sent a correction without a client declaration: "
                                + PlacementDyCorrectionServer.correctionFinishOutcomesForTests());
                Slabbed.LOGGER.info(
                        "C3_DECLARATION_GATE | held_door_open_close | corrections_unchanged=1 | PASS");
            });

            singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BED.red(), 4));
                player.inventoryMenu.sendAllDataToRemote();
                return true;
            });
            waitForHeldAndOwner(context, Items.BED.red(), fixture.bedOwner());
            useOnRecorderOwner(context, fixture.bedOwner());
            context.waitFor(client -> recorderAction(
                    recorderDir, "client", "minecraft:red_bed", fixture.bedOwner()) != null, 400);
            context.runOnClient(client -> assertRecorderPair(
                    recorderAction(recorderDir, "client", "minecraft:red_bed", fixture.bedOwner()),
                    "head",
                    "real client bed"));
            context.runOnClient(client -> expect(recorderActionCount(
                            recorderDir, "client", "minecraft:red_bed", fixture.bedOwner()) == 1,
                    "real client bed recorder action was duplicated"));
            context.waitFor(client -> PlacementDyCorrectionServer.correctionFinishOutcomesForTests().size() >= 4,
                    400);
            context.runOnClient(client -> expect(
                    PlacementDyCorrectionServer.correctionFinishOutcomesForTests().equals(List.of(
                            "DECLARED_SENT", "UNDECLARED_NO_SCOPE", "UNDECLARED_NO_SCOPE", "DECLARED_SENT")),
                    "real bed placement did not finish as the second declaration-owned correction: "
                            + PlacementDyCorrectionServer.correctionFinishOutcomesForTests()));

            singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_DOOR, 4));
                player.inventoryMenu.sendAllDataToRemote();
                return true;
            });
            waitForHeldAndOwner(context, Items.OAK_DOOR, fixture.failedOwner());
            context.runOnClient(client ->
                    PlacementDyPredictionJournalGameTestAccess.failNextDeclarationForTests());
            useOnRecorderOwner(context, fixture.failedOwner());
            context.waitFor(client -> recorderAction(
                    recorderDir, "server", "minecraft:oak_door", fixture.failedOwner()) != null, 400);
            context.waitFor(client -> PlacementDyCorrectionServer.correctionFinishOutcomesForTests().size() >= 5,
                    400);
            context.runOnClient(client -> {
                assertRecorderPair(
                        recorderAction(recorderDir, "server", "minecraft:oak_door", fixture.failedOwner()),
                        "upper",
                        "real server door after client declaration failure");
                expect(recorderAction(
                                recorderDir, "client", "minecraft:oak_door", fixture.failedOwner()) == null,
                        "failed client declaration emitted a false client success recorder row");
                expect(recorderActionCount(
                                recorderDir, "server", "minecraft:oak_door", fixture.failedOwner()) == 1,
                        "server recorder action after client declaration failure was duplicated");
                expect(PlacementDyCorrectionServer.correctionSendCountForTests() == 2,
                        "failed client declaration still armed an unsolicited correction");
                expect(PlacementDyCorrectionServer.correctionFinishOutcomesForTests().equals(List.of(
                                "DECLARED_SENT", "UNDECLARED_NO_SCOPE", "UNDECLARED_NO_SCOPE",
                                "DECLARED_SENT", "UNDECLARED_NO_SCOPE")),
                        "failed client declaration did not finish without a correction scope: "
                                + PlacementDyCorrectionServer.correctionFinishOutcomesForTests());
                Slabbed.LOGGER.info(
                        "C3_DECLARATION_GATE | no_declaration | corrections_unchanged=2 | PASS");
            });
        } finally {
            context.runOnClient(client -> {
                LiveCursorIntentRecorder.flushSummaryForTests();
                System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
                System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
                LiveCursorIntentRecorder.resetForTests();
            });
        }
    }

    private static void prepareRecorderOwner(ServerPlayer player, BlockPos owner) {
        player.level().setBlock(owner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        player.level().setBlock(owner.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        player.level().setBlock(owner.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        SlabAnchorAttachment.writePlacementDy(player.level(), owner, -1.0d);
    }

    private static void waitForHeldAndOwner(
            ClientGameTestContext context,
            net.minecraft.world.item.Item item,
            BlockPos owner
    ) throws Exception {
        context.waitFor(client -> client.player.getMainHandItem().is(item)
                && PlacementDyPredictionJournal.backingFact(client.level, owner)
                .equals(PlacementDyFact.present(-1.0d)), 400);
    }

    private static void useOnRecorderOwner(ClientGameTestContext context, BlockPos owner) throws Exception {
        context.runOnClient(client -> client.gameMode.useItemOn(
                client.player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(owner).add(0.0d, -0.5d, 0.0d),
                        Direction.UP, owner, false)));
    }

    private static void useOnDoor(ClientGameTestContext context, BlockPos door) throws Exception {
        context.runOnClient(client -> client.gameMode.useItemOn(
                client.player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(door), Direction.NORTH, door, false)));
    }

    private static JsonObject recorderAction(
            Path recorderDir,
            String side,
            String heldItem,
            BlockPos clickedOwner
    ) {
        Path session = recorderDir.resolve("session.jsonl");
        if (!Files.isRegularFile(session)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(session)) {
                try {
                    JsonObject row = JsonParser.parseString(line).getAsJsonObject();
                    if (side.equals(jsonString(row, "side"))
                            && heldItem.equals(jsonString(row, "heldItem"))
                            && clickedOwner.toShortString().equals(jsonString(row, "clickedOwnerPos"))
                            && "place_block".equals(jsonString(row, "actionType"))) {
                        return row;
                    }
                } catch (RuntimeException ignored) {
                    // A concurrent writer may leave the final line temporarily incomplete; retry via waitFor.
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    private static int recorderActionCount(
            Path recorderDir,
            String side,
            String heldItem,
            BlockPos clickedOwner
    ) {
        Path session = recorderDir.resolve("session.jsonl");
        if (!Files.isRegularFile(session)) {
            return 0;
        }
        int count = 0;
        try {
            for (String line : Files.readAllLines(session)) {
                try {
                    JsonObject row = JsonParser.parseString(line).getAsJsonObject();
                    if (side.equals(jsonString(row, "side"))
                            && heldItem.equals(jsonString(row, "heldItem"))
                            && clickedOwner.toShortString().equals(jsonString(row, "clickedOwnerPos"))
                            && "place_block".equals(jsonString(row, "actionType"))) {
                        count++;
                    }
                } catch (RuntimeException ignored) {
                    // A concurrent writer may leave the final line temporarily incomplete.
                }
            }
        } catch (IOException ignored) {
            return 0;
        }
        return count;
    }

    private static void assertRecorderPair(JsonObject row, String pairPart, String label) {
        expect(row != null, label + " recorder row missing");
        double live = Double.parseDouble(jsonString(row, "afterDy"));
        double stored = Double.parseDouble(jsonString(row, "afterStoredDy"));
        double pairLive = Double.parseDouble(jsonString(row, "pairAfterDy"));
        double pairStored = Double.parseDouble(jsonString(row, "pairStoredDy"));
        long storedBits = Long.parseUnsignedLong(jsonString(row, "afterStoredDyBits"), 16);
        long pairStoredBits = Long.parseUnsignedLong(jsonString(row, "pairStoredDyBits"), 16);
        expect(Double.doubleToRawLongBits(live) == storedBits
                        && Double.doubleToRawLongBits(stored) == storedBits
                        && Double.doubleToRawLongBits(pairLive) == storedBits
                        && Double.doubleToRawLongBits(pairStored) == storedBits
                        && pairStoredBits == storedBits,
                label + " did not record one exact post-install live/stored raw fact: " + row);
        expect(pairPart.equals(jsonString(row, "pairPart"))
                        && !"none".equals(jsonString(row, "pairPos")),
                label + " did not record the reciprocal pair: " + row);
    }

    private static String jsonString(JsonObject row, String key) {
        return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsString() : "none";
    }

    private static void refusalAndNonadjacentAuthority(
            ClientGameTestContext context, TestSingleplayerContext ignored, int index
    ) throws Exception {
        context.runOnClient(client -> {
            BlockPos clicked = testPos(client, index);
            BlockPos transformed = clicked.relative(Direction.EAST, 7);
            BlockPos horizontalOutOfBounds = clicked.relative(Direction.EAST, 8);
            BlockPos verticalBeyondSeven = clicked.above(12);
            ItemStack scaffolding = new ItemStack(Items.SCAFFOLDING);
            ClientLevel level = client.level;
            List<Long> withoutDeclaration = PlacementDyCorrectionServer.responseCellCensusForTests(
                    level, clicked, Direction.UP, scaffolding, true, false, false, Direction.EAST, List.of());
            List<Long> withDeclaration = PlacementDyCorrectionServer.responseCellCensusForTests(
                    level, clicked, Direction.UP, scaffolding, true, false, false, Direction.EAST,
                    List.of(transformed.asLong()));
            expect(!withoutDeclaration.contains(transformed.asLong()),
                    "non-adjacent Scaffolding target leaked into local census without declaration");
            expect(withDeclaration.contains(transformed.asLong()),
                    "client-declared non-adjacent Scaffolding target missing from response union");
            expect(!PlacementDyCorrectionServer.responseCellCensusForTests(
                            level, clicked, Direction.UP, scaffolding, true, false, false, Direction.EAST,
                            List.of(horizontalOutOfBounds.asLong())).contains(horizontalOutOfBounds.asLong()),
                    "horizontal Scaffolding route accepted distance eight");
            expect(PlacementDyCorrectionServer.responseCellCensusForTests(
                            level, clicked, Direction.EAST, scaffolding, true, false, false, Direction.NORTH,
                            List.of(verticalBeyondSeven.asLong())).contains(verticalBeyondSeven.asLong()),
                    "vertical Scaffolding route rejected a world-bounded target beyond distance seven");
            expect(PlacementDyCorrectionServer.responseCellCensusForTests(
                            level, clicked, Direction.WEST, scaffolding, true, true, false, Direction.NORTH,
                            List.of(clicked.relative(Direction.WEST, 7).asLong()))
                            .contains(clicked.relative(Direction.WEST, 7).asLong()),
                    "secondary-use outside did not follow the clicked face");
            expect(PlacementDyCorrectionServer.responseCellCensusForTests(
                            level, clicked, Direction.WEST, scaffolding, true, true, true, Direction.NORTH,
                            List.of(clicked.relative(Direction.EAST, 7).asLong()))
                            .contains(clicked.relative(Direction.EAST, 7).asLong()),
                    "secondary-use inside did not follow the opposite face");

            int verticalSequence = sequence(index, 0);
            PredictedBatch verticalBatch = batch(
                    level, verticalSequence, List.of(verticalBeyondSeven), NEGATIVE_ONE);
            PlacementDyPredictionJournalGameTestAccess.installBatchForTests(level, verticalBatch);
            PlacementDyPredictionJournal.onCorrection(level, correction(
                    verticalBatch, Map.of(verticalBeyondSeven, PlacementDyFact.absent())));
            PlacementDyPredictionJournal.onVanillaAcknowledgement(level, verticalSequence);
            expectFact(PlacementDyPredictionJournal.effectiveFact(level, verticalBeyondSeven),
                    false, 0L, "refused vertical Scaffolding explicit authority");
            expect(!PlacementDyPredictionJournal.debugCell(verticalBeyondSeven).groupPresent(),
                    "refused vertical Scaffolding prediction group did not retire");

            BlockPos primary = clicked.above();
            BlockPos rejectedPair = primary.above();
            int sequence = sequence(index, 1);
            PredictedBatch batch = batch(level, sequence, List.of(primary, rejectedPair), NEGATIVE_ONE);
            PlacementDyPredictionJournalGameTestAccess.installBatchForTests(level, batch);
            PlacementDyPredictionJournal.onCorrection(level, correction(batch, Map.of(
                    primary, PlacementDyFact.present(-1.0d),
                    rejectedPair, PlacementDyFact.absent())));
            PlacementDyPredictionJournal.onVanillaAcknowledgement(level, sequence);
            expectFact(PlacementDyPredictionJournal.effectiveFact(level, primary),
                    true, NEGATIVE_ONE, "double-to-single primary authority");
            expectFact(PlacementDyPredictionJournal.effectiveFact(level, rejectedPair),
                    false, 0L, "double-to-single rejected-pair authority");
            Slabbed.LOGGER.info("C3_SERVER_ENVELOPE | nonadjacent_scaffolding_refusal | PASS");
        });
    }

    private static void repeatedIdenticalNewerSequenceWins(
            ClientGameTestContext context, TestSingleplayerContext ignored, int index
    ) throws Exception {
        context.runOnClient(client -> {
            ClientLevel level = client.level;
            BlockPos pos = testPos(client, index);
            int oldSequence = sequence(index, 0);
            int newSequence = oldSequence + 1;
            PredictedBatch oldBatch = batch(level, oldSequence, List.of(pos), NEGATIVE_ONE);
            PredictedBatch newBatch = batch(level, newSequence, List.of(pos), NEGATIVE_ONE);
            PlacementDyPredictionJournalGameTestAccess.installBatchForTests(level, oldBatch);
            PlacementDyPredictionJournalGameTestAccess.installBatchForTests(level, newBatch);
            PlacementDyPredictionJournal.onCorrection(level, correction(oldBatch,
                    Map.of(pos, PlacementDyFact.present(+0.0d))));
            PlacementDyPredictionJournal.onVanillaAcknowledgement(level, oldSequence);
            PlacementDyPredictionJournal.CellDebug afterOld = PlacementDyPredictionJournal.debugCell(pos);
            expect(afterOld.groupPresent() && afterOld.highWaterSequence() == newSequence,
                    "older identical response displaced newer ownership");
            expectFact(afterOld.effective(), true, NEGATIVE_ONE,
                    "newer identical overlay after older response");
            PlacementDyPredictionJournal.onCorrection(level, correction(newBatch,
                    Map.of(pos, PlacementDyFact.present(-1.0d))));
            PlacementDyPredictionJournal.onVanillaAcknowledgement(level, newSequence);
            expectFact(PlacementDyPredictionJournal.effectiveFact(level, pos),
                    true, NEGATIVE_ONE, "newer identical final authority");
        });
    }

    private static void sameChunkUnrelatedPredictionIsolation(
            ClientGameTestContext context, TestSingleplayerContext ignored, int index
    ) throws Exception {
        context.runOnClient(client -> {
            ClientLevel level = client.level;
            BlockPos first = testPos(client, index);
            BlockPos unrelated = first.east(2);
            int firstSequence = sequence(index, 0);
            int secondSequence = firstSequence + 1;
            PredictedBatch firstBatch = batch(level, firstSequence, List.of(first), NEGATIVE_ONE);
            PredictedBatch secondBatch = batch(level, secondSequence, List.of(unrelated), NEGATIVE_ZERO);
            PlacementDyPredictionJournalGameTestAccess.installBatchForTests(level, firstBatch);
            PlacementDyPredictionJournalGameTestAccess.installBatchForTests(level, secondBatch);
            PlacementDyPredictionJournal.onCorrection(level, correction(firstBatch,
                    Map.of(first, PlacementDyFact.present(-1.0d))));
            PlacementDyPredictionJournal.onVanillaAcknowledgement(level, firstSequence);
            expect(PlacementDyPredictionJournal.debugCell(unrelated).groupPresent(),
                    "same-chunk unrelated group was coupled to first reconciliation");
            expectFact(PlacementDyPredictionJournal.effectiveFact(level, unrelated),
                    true, NEGATIVE_ZERO, "same-chunk unrelated effective overlay");
            PlacementDyPredictionJournal.onCorrection(level, correction(secondBatch,
                    Map.of(unrelated, PlacementDyFact.present(-0.0d))));
            PlacementDyPredictionJournal.onVanillaAcknowledgement(level, secondSequence);
            expectFact(PlacementDyPredictionJournal.effectiveFact(level, unrelated),
                    true, NEGATIVE_ZERO, "same-chunk unrelated final authority");
        });
    }

    private static void thirdStateAuthoritativeCorrection(
            ClientGameTestContext context, TestSingleplayerContext ignored, int index
    ) throws Exception {
        context.runOnClient(client -> {
            ClientLevel level = client.level;
            BlockPos pos = testPos(client, index);
            int sequence = sequence(index, 0);
            PredictedBatch batch = batch(level, sequence, List.of(pos), NEGATIVE_ONE);
            PlacementDyPredictionJournalGameTestAccess.installBatchForTests(level, batch);
            setBacking(level, Map.of(pos, PlacementDyFact.present(-0.0d)));
            PlacementDyPredictionJournal.onCorrection(level, correction(batch,
                    Map.of(pos, PlacementDyFact.present(-1.0d))));
            PlacementDyPredictionJournal.onVanillaAcknowledgement(level, sequence);
            expect(PlacementDyPredictionJournalGameTestAccess.testSnapshot().branches().get(pos.asLong())
                            == PlacementDyPredictionJournal.ReconcileBranch.THIRD_STATE_PRESERVED,
                    "third-state correction did not take protected branch");
            expectFact(PlacementDyPredictionJournal.effectiveFact(level, pos),
                    true, NEGATIVE_ZERO, "third-state preserved effective authority");
        });
    }

    private static PredictedBatch batch(
            ClientLevel level, int sequence, List<BlockPos> positions, long predictedBits
    ) {
        GroupSignature signature = new GroupSignature(
                level.dimension().toString(),
                sequence,
                InteractionHand.MAIN_HAND,
                "minecraft:stone",
                positions.getFirst().below().asLong(),
                Direction.UP);
        ArrayList<PredictedCell> cells = new ArrayList<>();
        for (BlockPos pos : positions) {
            cells.add(new PredictedCell(
                    pos,
                    level.getBlockState(pos),
                    PlacementDyFact.absent(),
                    Blocks.STONE.defaultBlockState(),
                    predictedBits));
        }
        return new PredictedBatch(signature, cells);
    }

    private static GroupSignature signatureForTest(String dimension, int sequence, int salt) {
        return new GroupSignature(
                dimension,
                sequence,
                InteractionHand.MAIN_HAND,
                "minecraft:stone",
                BlockPos.ZERO.offset(salt, 0, 0).asLong(),
                Direction.UP);
    }

    private static PlacementDyCorrectionPayload correction(
            PredictedBatch batch, Map<BlockPos, PlacementDyFact> facts
    ) {
        ArrayList<PlacementDyCorrectionPayload.CellFact> wire = new ArrayList<>();
        for (Map.Entry<BlockPos, PlacementDyFact> entry : facts.entrySet()) {
            PlacementDyFact fact = entry.getValue();
            wire.add(new PlacementDyCorrectionPayload.CellFact(
                    entry.getKey().asLong(), fact.present(), fact.rawBits()));
        }
        return new PlacementDyCorrectionPayload(batch.signature(), wire);
    }

    private static void setBacking(ClientLevel level, Map<BlockPos, PlacementDyFact> facts) {
        SlabAnchorAttachment.applyClientAuthoritativePlacementDy(level, new LinkedHashMap<>(facts));
    }

    private static BlockPos testPos(Minecraft client, int index) {
        BlockPos origin = client.player.blockPosition();
        return origin.offset(3 + (index % 5) * 3, 8 + (index / 5) * 2, 3).immutable();
    }

    private static BlockPos[] crossChunkPair(Minecraft client, int salt) {
        BlockPos origin = client.player.blockPosition();
        int chunkX = origin.getX() >> 4;
        int x = (chunkX << 4) + 15;
        int z = origin.getZ() + 2 + (salt % 4);
        int y = origin.getY() + 10 + (salt % 3);
        return new BlockPos[]{new BlockPos(x, y, z), new BlockPos(x + 1, y, z)};
    }

    private static int sequence(int index, int offset) {
        return 1_000 + index * 100 + offset;
    }

    private static String factText(PlacementDyFact fact) {
        return (fact.present() ? "present:" : "absent:")
                + String.format(java.util.Locale.ROOT, "%016x", fact.rawBits());
    }

    private static void expectFact(
            PlacementDyFact actual, boolean present, long rawBits, String label
    ) {
        expect(actual != null && actual.present() == present && actual.rawBits() == rawBits,
                label + " expected=" + factText(new PlacementDyFact(present, rawBits))
                        + " actual=" + (actual == null ? "null" : factText(actual)));
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
