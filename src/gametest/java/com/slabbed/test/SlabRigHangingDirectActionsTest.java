package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.command.SlabRigHangingArtifacts;
import com.slabbed.command.SlabRigHangingCatalog;
import com.slabbed.command.SlabRigHangingDirectActions;
import com.slabbed.command.SlabRigHangingDirectEntityGate;
import com.slabbed.command.SlabRigHangingDirectFixture;
import com.slabbed.command.SlabRigHangingPaintingPlan;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Field;

/** Focused real-action proof for the reviewed RIG-3B2B1 page and its null-player stack proxy. */
public final class SlabRigHangingDirectActionsTest {

    private static final Stat<Item> PAINTING_USED = Stats.ITEM_USED.get(Items.PAINTING);

    @GameTest(structure = "slabbed_gametest:rig3b2a_board")
    public void directActionsBuildExactPageAndKeepPlayerStateAcrossAllowThrowAndVeto(
            GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockServerPlayer(helper);
        SlabRigHangingDirectFixture.AbsolutePage page = exactPage(helper);
        LinkedHashSet<UUID> capturedUuids = new LinkedHashSet<>();

        assertReservedAir(helper, world, page);
        PlayerState playerBefore = PlayerState.capture(player);
        try {
            SlabRigHangingDirectActions.FixtureBuild fixture =
                    SlabRigHangingDirectActions.buildFixture(world, player, page);
            assertExactFixture(helper, world, page, fixture);
            playerBefore.assertExact(helper, player, "full fixture build");

            List<SlabRigHangingDirectFixture.AbsoluteCase> pinned = page.cases().stream()
                    .filter(entry -> entry.plan().selector().kind()
                            != SlabRigHangingPaintingPlan.SelectorKind.UNPINNED)
                    .limit(3)
                    .toList();
            if (pinned.size() != 3) {
                throw helper.assertionException("reviewed selector page did not expose three pinned cases");
            }
            SlabRigHangingDirectFixture.AbsoluteCase allowed = pinned.get(0);
            SlabRigHangingDirectFixture.AbsoluteCase throwing = pinned.get(1);
            SlabRigHangingDirectFixture.AbsoluteCase vetoed = pinned.get(2);

            Set<UUID> beforeInactive = paintingUuids(world);
            SlabRigHangingDirectActions.PaintingAttempt inactive;
            try (var ignored = SlabRigHangingDirectEntityGate.openTestHandlerDisabled(world)) {
                inactive = place(world, player, allowed, "actions-inactive");
            }
            if (!"ERROR_ENTITY_GATE_INACTIVE".equals(inactive.outcome())
                    || inactive.capture().active() || !inactive.capture().closed()
                    || !inactive.capture().entities().isEmpty()
                    || inactive.consumesAction()
                    || !beforeInactive.equals(paintingUuids(world))) {
                throw helper.assertionException(
                        "inactive ownership gate did not refuse before vanilla useOn: " + inactive);
            }
            playerBefore.assertExact(helper, player, "inactive ownership-gate refusal");

            TestHandler handler = new TestHandler(world);
            handler.decisions.put(throwing.plan().attemptId(), Decision.THROW);
            handler.decisions.put(vetoed.plan().attemptId(), Decision.CLAIM_AND_VETO);
            try (var ignored = SlabRigHangingDirectEntityGate.openTestHandlerOverride(world, handler)) {
                SlabRigHangingDirectActions.PaintingAttempt success = place(
                        world, player, allowed, "actions-allow");
                captureReturnedUuids(helper, success, capturedUuids);
                assertAllowedPinned(helper, world, playerBefore, player, allowed, success);

                SlabRigHangingDirectActions.PaintingAttempt threw = place(
                        world, player, throwing, "actions-throw");
                captureReturnedUuids(helper, threw, capturedUuids);
                assertVetoed(helper, world, playerBefore, player, throwing, threw,
                        SlabRigHangingDirectEntityGate.PreclaimStatus.EXCEPTION);

                SlabRigHangingDirectActions.PaintingAttempt veto = place(
                        world, player, vetoed, "actions-veto");
                captureReturnedUuids(helper, veto, capturedUuids);
                assertVetoed(helper, world, playerBefore, player, vetoed, veto,
                        SlabRigHangingDirectEntityGate.PreclaimStatus.CLAIMED_VETO);
            }

            if (!handler.preclaimedUuids().equals(Set.copyOf(capturedUuids))
                    || handler.confirmedUuids().size() != 1
                    || !handler.confirmedUuids().contains(
                    capturedUuid(helper, capturedUuids, 0))) {
                throw helper.assertionException(
                        "handler callbacks and returned capture UUID ledger diverged: preclaimed="
                                + handler.preclaimedUuids() + " confirmed=" + handler.confirmedUuids()
                                + " returned=" + capturedUuids);
            }
            playerBefore.assertExact(helper, player, "all direct painting outcomes");
        } finally {
            clearExactEntityFirst(helper, world, page, capturedUuids);
        }
        helper.succeed();
    }

    @GameTest(structure = "slabbed_gametest:rig3b2a_board")
    public void storedAttachmentProbeDistinguishesRawPersistenceFromDerivedCarrier(
            GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockServerPlayer(helper);
        SlabRigHangingDirectFixture.AbsolutePage page = exactPage(helper);

        assertReservedAir(helper, world, page);
        try {
            assertUnloadedProbeRefusesWithoutLoading(helper, world, page);
            SlabRigHangingDirectActions.FixtureBuild fixture =
                    SlabRigHangingDirectActions.buildFixture(world, player, page);
            assertExactFixture(helper, world, page, fixture);

            BlockPos derivedCarrier = page.clearOwnedCells().stream()
                    .map(SlabRigHangingDirectFixture.AbsoluteCell::pos)
                    .filter(pos -> SlabAnchorAttachment.hasStoredAttachmentEvidence(world, pos))
                    .filter(pos -> SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                            world, pos, world.getBlockState(pos)))
                    .filter(pos -> SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(
                            world, pos, world.getBlockState(pos)))
                    .findFirst()
                    .orElseThrow(() -> helper.assertionException(
                            "reviewed fixture did not expose a raw-marked derived carrier"));

            SlabAnchorAttachment.removeAnchor(world, derivedCarrier);
            if (SlabAnchorAttachment.hasStoredAttachmentEvidence(world, derivedCarrier)) {
                throw helper.assertionException(
                        "raw attachment evidence remained after exact removal at " + derivedCarrier);
            }
            if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    world, derivedCarrier, world.getBlockState(derivedCarrier))) {
                throw helper.assertionException(
                        "derived carrier truth did not remain after raw marker removal at "
                                + derivedCarrier);
            }
        } finally {
            clearExactEntityFirst(helper, world, page, new LinkedHashSet<>());
        }
        helper.succeed();
    }

    private static void assertUnloadedProbeRefusesWithoutLoading(
            GameTestHelper helper, ServerLevel world,
            SlabRigHangingDirectFixture.AbsolutePage page) {
        BlockPos far = page.reservedCells().getFirst().offset(32_768, 0, 32_768);
        int chunkX = far.getX() >> 4;
        int chunkZ = far.getZ() >> 4;
        if (world.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
            throw helper.assertionException("far probe chunk was unexpectedly loaded at " + far);
        }
        boolean refused = false;
        try {
            SlabAnchorAttachment.hasStoredAttachmentEvidence(world, far);
        } catch (IllegalStateException expected) {
            refused = expected.getMessage() != null
                    && expected.getMessage().contains("unloaded chunk");
        }
        if (!refused || world.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
            throw helper.assertionException(
                    "raw attachment probe did not refuse an unloaded chunk without loading it at "
                            + far);
        }
    }

    private static SlabRigHangingDirectFixture.AbsolutePage exactPage(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot catalog = SlabRigHangingCatalog.snapshot();
        SlabRigHangingArtifacts.RuntimeSnapshot runtime = SlabRigHangingArtifacts.snapshot(
                catalog, helper.getLevel().registryAccess());
        SlabRigHangingPaintingPlan.Universe universe =
                SlabRigHangingPaintingPlan.snapshot(catalog, runtime);
        SlabRigHangingPaintingPlan.PagePlan plan = SlabRigHangingPaintingPlan.page(universe,
                SlabRigHangingDirectFixture.ROUTE_INDEX,
                SlabRigHangingDirectFixture.TOPOLOGY_INDEX,
                SlabRigHangingDirectFixture.SELECTOR_PAGE);
        return SlabRigHangingDirectFixture.adapt(universe, plan,
                helper.absolutePos(new BlockPos(8, 3, 8)));
    }

    private static ServerPlayer mockServerPlayer(GameTestHelper helper) {
        if (!(helper.makeMockServerPlayer(GameType.SURVIVAL) instanceof ServerPlayer player)) {
            throw helper.assertionException("GameTest did not provide a server-backed mock player");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 3));
        player.getInventory().setItem(7, new ItemStack(Items.COOKIE, 11));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COMPASS));
        player.awardStat(PAINTING_USED, 7);
        return player;
    }

    private static void assertReservedAir(GameTestHelper helper, ServerLevel world,
                                          SlabRigHangingDirectFixture.AbsolutePage page) {
        if (page.plan().routeIndex() != 6143 || page.plan().topologyIndex() != 42
                || page.plan().selectorPage() != 1 || page.cases().size() != 16
                || page.reservedCells().size() != 16 * 68
                || page.clearOwnedCells().size() != 16 * 52
                || page.entityAirCells().size() != 16 * 16) {
            throw helper.assertionException("direct Actions test did not receive the exact reviewed page");
        }
        for (BlockPos pos : page.reservedCells()) {
            if (!world.getBlockState(pos).isAir()) {
                throw helper.assertionException("reviewed page reservation was not initially air at " + pos);
            }
        }
    }

    private static void assertExactFixture(GameTestHelper helper, ServerLevel world,
                                           SlabRigHangingDirectFixture.AbsolutePage page,
                                           SlabRigHangingDirectActions.FixtureBuild fixture) {
        int expectedDirect = 0;
        int expectedProxy = 0;
        LinkedHashMap<BlockPos, String> expectedStates = new LinkedHashMap<>();
        for (SlabRigHangingDirectFixture.AbsoluteCell cell : page.clearOwnedCells()) {
            String method = cell.plan().placementMethod();
            if ("DIRECT_FIXTURE_SET".equals(method)) {
                expectedDirect++;
            } else if ("PLAYER_ITEM_USEON".equals(method)) {
                expectedProxy++;
            } else {
                throw helper.assertionException("unknown reviewed fixture method " + method);
            }
            if (expectedStates.put(cell.pos(), SlabRigHangingDirectFixture.expectedState(
                    cell.plan().stateRecipe()).toString()) != null) {
                throw helper.assertionException("duplicate exact fixture cell " + cell.pos());
            }
        }

        LinkedHashMap<BlockPos, String> evidencedStates = new LinkedHashMap<>();
        for (var evidence : fixture.cells()) {
            if (evidencedStates.put(evidence.pos(), evidence.blockState()) != null) {
                throw helper.assertionException("duplicate fixture evidence for " + evidence.pos());
            }
        }
        if (!fixture.playerInventoryAndStatsUntouched()
                || fixture.directFixtureWrites() != expectedDirect
                || fixture.playerUseOnWrites() != expectedProxy
                || expectedDirect + expectedProxy != 16 * 52
                || fixture.cells().size() != expectedStates.size()
                || !evidencedStates.equals(expectedStates)) {
            throw helper.assertionException("exact fixture write/evidence envelope drifted: direct="
                    + fixture.directFixtureWrites() + '/' + expectedDirect + " proxy="
                    + fixture.playerUseOnWrites() + '/' + expectedProxy + " evidence="
                    + evidencedStates.size() + '/' + expectedStates.size());
        }
        for (Map.Entry<BlockPos, String> expected : expectedStates.entrySet()) {
            if (!world.getBlockState(expected.getKey()).toString().equals(expected.getValue())) {
                throw helper.assertionException("fixture readback changed at " + expected.getKey());
            }
        }
        for (BlockPos pos : page.entityAirCells()) {
            if (!world.getBlockState(pos).isAir()) {
                throw helper.assertionException("fixture wrote an entity-air reservation at " + pos);
            }
        }
    }

    private static SlabRigHangingDirectActions.PaintingAttempt place(
            ServerLevel world, ServerPlayer player,
            SlabRigHangingDirectFixture.AbsoluteCase planned, String runId) {
        SlabRigHangingDirectEntityGate.CaptureKey key =
                new SlabRigHangingDirectEntityGate.CaptureKey(runId, planned.plan().attemptId());
        return SlabRigHangingDirectActions.placePainting(world, player, planned, key);
    }

    private static void assertAllowedPinned(GameTestHelper helper, ServerLevel world,
                                            PlayerState expectedPlayer, ServerPlayer player,
                                            SlabRigHangingDirectFixture.AbsoluteCase planned,
                                            SlabRigHangingDirectActions.PaintingAttempt attempt) {
        SlabRigHangingDirectEntityGate.EntityOutcome entity = onlyOutcome(helper, attempt);
        String variant = planned.plan().selector().variantId();
        if (planned.plan().selector().kind() == SlabRigHangingPaintingPlan.SelectorKind.UNPINNED
                || !"PLACED_SURVIVES".equals(attempt.outcome())
                || !attempt.consumesAction()
                || !attempt.playerInventoryAndStatsUntouched()
                || attempt.statBefore() != expectedPlayer.paintingUsed()
                || attempt.statAfter() != expectedPlayer.paintingUsed()
                || entity.preclaimStatus()
                != SlabRigHangingDirectEntityGate.PreclaimStatus.CLAIMED_ALLOW
                || !entity.confirmationAttempted() || !entity.confirmed()
                || attempt.paintings().size() != 1
                || !attempt.paintings().getFirst().uuid().equals(entity.entityUuid())
                || !attempt.paintings().getFirst().variantId().equals(variant)
                || !attempt.paintings().getFirst().componentVariantId().equals(variant)
                || !attempt.paintings().getFirst().attachment().equals(planned.anchor())
                || !attempt.paintings().getFirst().facing()
                .equals(planned.plan().clickedFace().getName())
                || world.getEntity(entity.entityUuid()) == null) {
            throw helper.assertionException("pinned allow action did not place one exact painting: "
                    + attempt);
        }
        assertSyntheticPinnedStack(helper, planned, attempt);
        expectedPlayer.assertExact(helper, player, "allowed pinned painting");
    }

    private static void assertVetoed(GameTestHelper helper, ServerLevel world,
                                     PlayerState expectedPlayer, ServerPlayer player,
                                     SlabRigHangingDirectFixture.AbsoluteCase planned,
                                     SlabRigHangingDirectActions.PaintingAttempt attempt,
                                     SlabRigHangingDirectEntityGate.PreclaimStatus expectedStatus) {
        SlabRigHangingDirectEntityGate.EntityOutcome entity = onlyOutcome(helper, attempt);
        if (!"ERROR_PRECLAIM_VETOED".equals(attempt.outcome())
                || !attempt.playerInventoryAndStatsUntouched()
                || attempt.statBefore() != expectedPlayer.paintingUsed()
                || attempt.statAfter() != expectedPlayer.paintingUsed()
                || entity.preclaimStatus() != expectedStatus
                || entity.confirmationAttempted() || entity.confirmed()
                || entity.preclaimFailure().isEmpty()
                || !attempt.paintings().isEmpty()
                || world.getEntity(entity.entityUuid()) != null) {
            throw helper.assertionException("preclaim failure/veto did not stay bounded: " + attempt);
        }
        assertSyntheticPinnedStack(helper, planned, attempt);
        expectedPlayer.assertExact(helper, player, expectedStatus + " pinned painting");
    }

    private static void assertSyntheticPinnedStack(GameTestHelper helper,
                                                   SlabRigHangingDirectFixture.AbsoluteCase planned,
                                                   SlabRigHangingDirectActions.PaintingAttempt attempt) {
        String expectedBefore = "item=minecraft:painting;count=1;minecraft:painting/variant="
                + planned.plan().selector().variantId();
        if (!expectedBefore.equals(attempt.stackBefore())) {
            throw helper.assertionException("direct action did not use an isolated pinned stack: "
                    + attempt.stackBefore());
        }
    }

    private static SlabRigHangingDirectEntityGate.EntityOutcome onlyOutcome(
            GameTestHelper helper, SlabRigHangingDirectActions.PaintingAttempt attempt) {
        if (!attempt.capture().active() || !attempt.capture().closed()
                || attempt.capture().entities().size() != 1
                || !attempt.capture().context().key().attemptId().equals(attempt.attemptId())) {
            throw helper.assertionException("direct action did not return one closed active capture: "
                    + attempt.capture());
        }
        return attempt.capture().entities().getFirst();
    }

    private static void captureReturnedUuids(GameTestHelper helper,
                                             SlabRigHangingDirectActions.PaintingAttempt attempt,
                                             LinkedHashSet<UUID> capturedUuids) {
        for (SlabRigHangingDirectEntityGate.EntityOutcome entity : attempt.capture().entities()) {
            if (!capturedUuids.add(entity.entityUuid())) {
                throw helper.assertionException("capture returned a duplicate entity UUID "
                        + entity.entityUuid());
            }
        }
    }

    private static UUID capturedUuid(GameTestHelper helper, LinkedHashSet<UUID> capturedUuids,
                                     int ordinal) {
        if (capturedUuids.size() <= ordinal) {
            throw helper.assertionException("missing returned capture UUID " + ordinal);
        }
        return capturedUuids.stream().skip(ordinal).findFirst().orElseThrow();
    }

    private static Set<UUID> paintingUuids(ServerLevel world) {
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof Painting) {
                result.add(entity.getUUID());
            }
        }
        return Set.copyOf(result);
    }

    /** Exact returned-UUID teardown first, followed only by the adapter's clear-owned cell ledger. */
    private static void clearExactEntityFirst(GameTestHelper helper, ServerLevel world,
                                              SlabRigHangingDirectFixture.AbsolutePage page,
                                              LinkedHashSet<UUID> capturedUuids) {
        for (UUID uuid : capturedUuids.stream().sorted().toList()) {
            Entity exact = world.getEntity(uuid);
            if (exact == null) {
                continue;
            }
            if (!(exact instanceof Painting)) {
                throw helper.assertionException("captured UUID changed entity type; refusing clear " + uuid);
            }
            exact.discard();
        }
        for (UUID uuid : capturedUuids) {
            if (world.getEntity(uuid) != null) {
                throw helper.assertionException("captured UUID remained after exact entity clear " + uuid);
            }
        }

        List<BlockPos> exactCells = page.clearOwnedCells().stream()
                .map(SlabRigHangingDirectFixture.AbsoluteCell::pos)
                .sorted(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed()
                        .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ))
                .toList();
        for (BlockPos pos : exactCells) {
            SlabAnchorAttachment.removeAnchor(world, pos);
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        for (BlockPos pos : exactCells) {
            if (!world.getBlockState(pos).isAir()
                    || !Double.isNaN(SlabAnchorAttachment.storedPlacementDy(world, pos))
                    || SlabAnchorAttachment.hasStoredAttachmentEvidence(world, pos)) {
                throw helper.assertionException("exact clear-owned cell did not clear at " + pos);
            }
        }
    }

    private enum Decision {
        ALLOW,
        THROW,
        CLAIM_AND_VETO
    }

    private static final class TestHandler implements SlabRigHangingDirectEntityGate.Handler {
        private final ServerLevel expectedLevel;
        private final Map<String, Decision> decisions = new HashMap<>();
        private final LinkedHashSet<UUID> preclaimed = new LinkedHashSet<>();
        private final LinkedHashSet<UUID> confirmed = new LinkedHashSet<>();

        private TestHandler(ServerLevel expectedLevel) {
            this.expectedLevel = expectedLevel;
        }

        @Override
        public Optional<SlabRigHangingDirectEntityGate.CaptureKey> beginPaintingDrop(
                Painting source, ServerLevel level) {
            return Optional.empty();
        }

        @Override
        public SlabRigHangingDirectEntityGate.PreclaimDecision preclaim(
                SlabRigHangingDirectEntityGate.CaptureContext context, Entity entity,
                ServerLevel level) {
            if (level != expectedLevel
                    || context.kind() != SlabRigHangingDirectEntityGate.CaptureKind.PLACEMENT
                    || !(entity instanceof Painting)) {
                throw new IllegalStateException("Actions test received a non-placement preclaim");
            }
            preclaimed.add(entity.getUUID());
            return switch (decisions.getOrDefault(context.key().attemptId(), Decision.ALLOW)) {
                case ALLOW -> SlabRigHangingDirectEntityGate.PreclaimDecision.CLAIM_AND_ALLOW;
                case CLAIM_AND_VETO ->
                        SlabRigHangingDirectEntityGate.PreclaimDecision.CLAIM_AND_VETO;
                case THROW -> throw new IllegalStateException("synthetic Actions preclaim failure");
            };
        }

        @Override
        public void confirm(SlabRigHangingDirectEntityGate.CaptureContext context, Entity entity,
                            ServerLevel level) {
            if (level != expectedLevel || !(entity instanceof Painting)) {
                throw new IllegalStateException("Actions test received an invalid confirmation");
            }
            confirmed.add(entity.getUUID());
        }

        private Set<UUID> preclaimedUuids() {
            return Set.copyOf(preclaimed);
        }

        private Set<UUID> confirmedUuids() {
            return Set.copyOf(confirmed);
        }
    }

    private record PlayerState(List<ItemStack> inventory, int selectedSlot, int paintingUsed,
                               String advancementProgressFingerprint) {
        private static PlayerState capture(ServerPlayer player) {
            List<ItemStack> inventory = new ArrayList<>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                inventory.add(player.getInventory().getItem(slot).copy());
            }
            return new PlayerState(List.copyOf(inventory), player.getInventory().getSelectedSlot(),
                    player.getStats().getValue(PAINTING_USED), advancementFingerprint(player));
        }

        private void assertExact(GameTestHelper helper, ServerPlayer player, String phase) {
            if (player.getInventory().getContainerSize() != inventory.size()
                    || player.getInventory().getSelectedSlot() != selectedSlot
                    || player.getStats().getValue(PAINTING_USED) != paintingUsed
                    || !advancementProgressFingerprint.equals(advancementFingerprint(player))) {
                throw helper.assertionException("player inventory/stat envelope changed after " + phase);
            }
            for (int slot = 0; slot < inventory.size(); slot++) {
                if (!ItemStack.matches(inventory.get(slot), player.getInventory().getItem(slot))) {
                    throw helper.assertionException("player inventory slot " + slot
                            + " changed after " + phase);
                }
            }
        }

        /** Read-only GameTest snapshot; production isolation comes from the null interaction player. */
        @SuppressWarnings("unchecked")
        private static String advancementFingerprint(ServerPlayer player) {
            try {
                Field field = PlayerAdvancements.class.getDeclaredField("progress");
                field.setAccessible(true);
                Map<AdvancementHolder, AdvancementProgress> progress =
                        (Map<AdvancementHolder, AdvancementProgress>) field.get(player.getAdvancements());
                StringBuilder out = new StringBuilder();
                progress.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(
                                Comparator.comparing(holder -> holder.id().toString())))
                        .forEach(entry -> {
                            out.append(entry.getKey().id()).append('|')
                                    .append(entry.getValue().isDone()).append('|');
                            List<String> completed = new ArrayList<>();
                            entry.getValue().getCompletedCriteria().forEach(completed::add);
                            completed.stream().sorted().forEach(value -> out.append("done=")
                                    .append(value).append(';'));
                            List<String> remaining = new ArrayList<>();
                            entry.getValue().getRemainingCriteria().forEach(remaining::add);
                            remaining.stream().sorted().forEach(value -> out.append("left=")
                                    .append(value).append(';'));
                            out.append('\n');
                        });
                return com.slabbed.command.SlabRigHangingDirectEvidence.sha256(out.toString());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("cannot snapshot advancement progress read-only", failure);
            }
        }
    }
}
