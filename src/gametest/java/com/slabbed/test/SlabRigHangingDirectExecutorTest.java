package com.slabbed.test;

import com.mojang.brigadier.CommandDispatcher;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.command.SlabRigCommand;
import com.slabbed.command.SlabRigHangingDirectEvidence;
import com.slabbed.command.SlabRigHangingDirectExecutor;
import com.slabbed.command.SlabRigHangingDirectState;
import com.slabbed.command.SlabRigHangingDirectStateStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** End-to-end production-command gate for all four reviewed 6143/42 SBSBS painting pages. */
public final class SlabRigHangingDirectExecutorTest {

    private static final Stat<Item> PAINTING_USED = Stats.ITEM_USED.get(Items.PAINTING);
    private static final String START =
            "slabrig hangs direct 6143 topology 42 paintings 1";
    private static final String FORCE = START + " force";
    private static final long START_BUDGET_MILLIS = 15_000L;
    private static final long READ_OR_CLEAR_BUDGET_MILLIS = 5_000L;

    private static String startCommand(int selectorPage) {
        return "slabrig hangs direct 6143 topology 42 paintings " + selectorPage;
    }

    private static void runRemainingSelectorPages(
            GameTestHelper helper, ServerLevel world, ServerPlayer player,
            CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source,
            PlayerSnapshot playerBefore, int selectorPage, Runnable afterAllPages) {
        if (selectorPage > SlabRigHangingDirectState.MAX_SELECTOR_PAGE) {
            afterAllPages.run();
            return;
        }
        int expectedCases = selectorPage == 4 ? 4 : 16;
        PlayerSnapshot pageBefore = PlayerSnapshot.capture(player);
        requireResult(helper, execute(helper, dispatcher, source, startCommand(selectorPage)), 1,
                "real-dispatch selector page " + selectorPage + " start");
        SlabRigHangingDirectState.State planned = head(helper);
        assertPlannedEnvelope(helper, world, planned, selectorPage, expectedCases);
        if (planned.run().selectorPage() != selectorPage
                || planned.run().caseCount() != expectedCases
                || planned.reservedCells().size() != expectedCases * 68
                || planned.plannedAuthoredCells().size() != expectedCases * 52) {
            throw helper.assertionException("real dispatcher ignored selector page " + selectorPage
                    + ": " + planned.run().selectorPage() + "/" + planned.run().caseCount());
        }
        playerBefore.assertExact(helper, player, "page " + selectorPage + " PLANNED");
        pageBefore.assertExact(helper, player, "page " + selectorPage + " start");

        if (selectorPage == 2) {
            String storeBefore = storeFingerprint(helper);
            ReservationSnapshot worldBefore = reservationSnapshot(world, planned);
            requireResult(helper, execute(helper, dispatcher, source, startCommand(4)), 0,
                    "different page cannot bypass one-active-page allocation");
            if (!head(helper).stateHash().equals(planned.stateHash())
                    || !storeFingerprint(helper).equals(storeBefore)
                    || !reservationSnapshot(world, planned).equals(worldBefore)) {
                throw helper.assertionException(
                        "different valid selector page changed the active page-2 allocation");
            }
        }

        if (selectorPage == 4) {
            assertInvalidDirectFormsAreInert(helper, world, dispatcher, source, planned);
            SlabRigHangingDirectState.Owner fixedOwner = activeLegacyOwner();
            expectIllegalState(helper, () -> StoreBootstrap.override().openFixedLegacyOwner(
                    world.getServer(), fixedOwner, fixedOwner.legacyKey()),
                    "fixed child opened while a variable-page run was installed");
        }

        if (selectorPage == 3) {
            String firstRunId = planned.run().runId();
            requireResult(helper, execute(helper, dispatcher, source,
                    startCommand(3) + " force"), 1,
                    "page-3 first force arms clear only");
            SlabRigHangingDirectState.State clearing = head(helper);
            if (!clearing.run().runId().equals(firstRunId)
                    || clearing.phase() != SlabRigHangingDirectState.Phase.CLEARING_ENTITIES) {
                throw helper.assertionException(
                        "page-3 first force replaced instead of arming exact clear");
            }
            helper.runAfterDelay(50, () -> {
                if (head(helper).phase() != SlabRigHangingDirectState.Phase.CLEARED) {
                    throw helper.assertionException("page-3 force clear did not reach CLEARED");
                }
                requireResult(helper, execute(helper, dispatcher, source,
                        startCommand(3) + " force"), 1,
                        "page-3 second force starts after CLEARED");
                SlabRigHangingDirectState.State forced = head(helper);
                assertPlannedEnvelope(helper, world, forced, 3, 16);
                if (!artifactText(helper, forced.artifacts().planned())
                        .contains("force\ttrue\n")) {
                    throw helper.assertionException(
                            "page-3 second force did not persist force=true");
                }
                scheduleVariablePageConstruction(helper, world, player, dispatcher, source,
                        pageBefore, selectorPage, expectedCases, afterAllPages);
            });
            return;
        }
        scheduleVariablePageConstruction(helper, world, player, dispatcher, source,
                pageBefore, selectorPage, expectedCases, afterAllPages);
    }

    private static void scheduleVariablePageConstruction(
            GameTestHelper helper, ServerLevel world, ServerPlayer player,
            CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source,
            PlayerSnapshot pageBefore, int selectorPage, int expectedCases,
            Runnable afterAllPages) {
        helper.runAfterDelay(50, () -> {
            SlabRigHangingDirectState.State waiting = head(helper);
            assertWaitingEnvelope(helper, world, waiting, selectorPage, expectedCases);
            if (waiting.run().selectorPage() != selectorPage
                    || waiting.run().caseCount() != expectedCases) {
                throw helper.assertionException(
                        "page lifecycle changed selector/cardinality identity " + selectorPage);
            }
            if (selectorPage == 4) {
                long priorGeneration = waiting.scheduler().generation();
                ExactWorldSnapshot beforeRestart = exactWorldSnapshot(world, waiting);
                SlabRigHangingDirectExecutor.restartForSerialGameTest(world.getServer());
                waiting = head(helper);
                if (waiting.phase() != SlabRigHangingDirectState.Phase.WAITING_DELAYED
                        || waiting.run().caseCount() != 4
                        || waiting.nextCaseOrdinal() != 4
                        || waiting.scheduler().generation() != priorGeneration + 1
                        || waiting.scheduler().credits().size() != 4
                        || waiting.scheduler().credits().stream().anyMatch(
                        credit -> credit.observedEntityTicks() != 0)
                        || !beforeRestart.equals(exactWorldSnapshot(world, waiting))) {
                    throw helper.assertionException(
                            "page-4 reconstruction replayed work or weakened cardinality/tick authority");
                }
                List<String> messages = new ArrayList<>();
                requireResult(helper, execute(helper, dispatcher,
                        capturingSource(source, messages), "slabrig hangs direct status"), 1,
                        "page-4 captured waiting status");
                String status = messages.isEmpty() ? "" : messages.getLast();
                for (String required : List.of("6143/42/4", "schema=v2",
                        "phase=WAITING_DELAYED", "cases=4/4", "clearEntities=0/0",
                        "clearAttachments=0/0", "clearCells=0/0")) {
                    if (!status.contains(required)) {
                        throw helper.assertionException(
                                "page-4 status omitted " + required + ": " + status);
                    }
                }
            }
            scheduleVariablePageDelayedBoundary(helper, world, player, dispatcher, source,
                    pageBefore, selectorPage, expectedCases, afterAllPages);
        });
    }

    private static void scheduleVariablePageDelayedBoundary(
            GameTestHelper helper, ServerLevel world, ServerPlayer player,
            CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source,
            PlayerSnapshot playerBefore, int selectorPage, int expectedCases,
            Runnable afterAllPages) {
        SlabRigHangingDirectState.State waiting = head(helper);
        int maxObservedDelta = 0;
        for (SlabRigHangingDirectState.TickCredit credit : waiting.scheduler().credits()) {
            Entity live = world.getEntity(credit.paintingUuid());
            if (!(live instanceof Painting painting)) {
                throw helper.assertionException(
                        "page " + selectorPage + " delayed boundary lacks painting "
                                + credit.paintingUuid());
            }
            long delta = painting.tickCount - credit.lastObservedEntityTick();
            if (delta < 0 || delta >= 100) {
                throw helper.assertionException(
                        "page " + selectorPage + " escaped pre-boundary ticks delta=" + delta);
            }
            maxObservedDelta = Math.max(maxObservedDelta, (int) delta);
        }
        long tick101 = helper.getTick() + Math.max(1, 100 - maxObservedDelta);
        helper.runAtTickTime(tick101, () -> {
            SlabRigHangingDirectState.State at101 = head(helper);
            if (at101.phase() != SlabRigHangingDirectState.Phase.WAITING_DELAYED
                    || at101.run().selectorPage() != selectorPage
                    || at101.nextCaseOrdinal() != expectedCases
                    || at101.scheduler().credits().size() != expectedCases) {
                throw helper.assertionException(
                        "page " + selectorPage + " finalized before the tick-102 gate");
            }
            for (SlabRigHangingDirectState.TickCredit credit : at101.scheduler().credits()) {
                Entity live = world.getEntity(credit.paintingUuid());
                if (!(live instanceof Painting painting)) {
                    throw helper.assertionException(
                            "tick-101 page " + selectorPage + " painting is absent "
                                    + credit.paintingUuid());
                }
                long delta = painting.tickCount - credit.lastObservedEntityTick();
                if (!world.isPositionEntityTicking(painting.blockPosition())
                        || delta < 100 || delta >= 102) {
                    throw helper.assertionException(
                            "page " + selectorPage + " tick-101 is not an exact entity-tick boundary"
                                    + ";delta=" + delta);
                }
            }
        });
        helper.runAtTickTime(tick101 + 9, () -> {
            SlabRigHangingDirectState.State finalState = head(helper);
            assertFinalEnvelope(helper, world, finalState, selectorPage, expectedCases);
            if (finalState.run().selectorPage() != selectorPage
                    || finalState.run().caseCount() != expectedCases) {
                throw helper.assertionException(
                        "FINAL changed page/cardinality identity for page " + selectorPage);
            }
            playerBefore.assertExact(helper, player, "page " + selectorPage + " FINAL");
            if (selectorPage == 4) {
                runPageFourDropOutcomes(helper, world, player, dispatcher, source,
                        playerBefore, afterAllPages);
            } else {
                clearVariablePage(helper, world, player, dispatcher, source, playerBefore,
                        selectorPage, () -> runRemainingSelectorPages(helper, world, player,
                                dispatcher, source, playerBefore, selectorPage + 1, afterAllPages));
            }
        });
    }

    private static void runPageFourDropOutcomes(
            GameTestHelper helper, ServerLevel world, ServerPlayer player,
            CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source,
            PlayerSnapshot playerBefore, Runnable afterAllPages) {
        SlabRigHangingDirectState.State finalState = head(helper);
        Painting first = livePaintings(helper, world, finalState).getFirst();
        UUID firstUuid = first.getUUID();
        BlockPos firstAnchor = first.getPos().immutable();
        removePaintingSupport(world, first);
        helper.runAfterDelay(125, () -> {
            SlabRigHangingDirectState.State afterDrop = head(helper);
            SlabRigHangingDirectState.EntityOwnership firstOwnership =
                    ownership(afterDrop, firstUuid);
            List<SlabRigHangingDirectState.EntityOwnership> droppedItems =
                    afterDrop.entities().stream().filter(entry -> entry.role()
                    == SlabRigHangingDirectState.EntityRole.DROPPED_ITEM).toList();
            if (afterDrop.phase() != SlabRigHangingDirectState.Phase.FINAL
                    || firstOwnership.disposition()
                    != SlabRigHangingDirectState.EntityDisposition.REMOVED
                    || firstOwnership.removalCause()
                    != SlabRigHangingDirectState.RemovalCause.SUPPORT_LOSS_DROP_EXPECTED
                    || droppedItems.size() != 1
                    || !droppedItems.getFirst().sourcePaintingUuid().equals(firstUuid.toString())
                    || droppedItems.getFirst().decision()
                    != SlabRigHangingDirectState.PreclaimDecision.CLAIM_AND_VETO
                    || world.getEntity(droppedItems.getFirst().uuid()) != null) {
                throw helper.assertionException(
                        "page-4 support-loss drop outcome was not exact");
            }
            String itemArtifact = artifactText(
                    helper, droppedItems.getFirst().evidenceArtifact());
            for (String required : List.of(
                    "schema\tslabbed-rig-hanging-direct-item-evidence-v1\n",
                    "item_id\tminecraft:painting\n", "stack_sha256\t", "entity_snbt\t")) {
                if (!itemArtifact.contains(required)) {
                    throw helper.assertionException(
                            "page-4 drop artifact omitted " + required.trim());
                }
            }
            Painting second = livePaintings(helper, world, afterDrop).getFirst();
            UUID secondUuid = second.getUUID();
            world.getGameRules().set(GameRules.ENTITY_DROPS, false, world.getServer());
            removePaintingSupport(world, second);
            helper.runAfterDelay(125, () -> {
                world.getGameRules().set(GameRules.ENTITY_DROPS, true, world.getServer());
                SlabRigHangingDirectState.State afterNoDrop = head(helper);
                SlabRigHangingDirectState.EntityOwnership secondOwnership =
                        ownership(afterNoDrop, secondUuid);
                long finalDroppedItems = afterNoDrop.entities().stream().filter(entry -> entry.role()
                        == SlabRigHangingDirectState.EntityRole.DROPPED_ITEM).count();
                if (afterNoDrop.phase() != SlabRigHangingDirectState.Phase.FINAL
                        || secondOwnership.disposition()
                        != SlabRigHangingDirectState.EntityDisposition.REMOVED
                        || secondOwnership.removalCause()
                        != SlabRigHangingDirectState.RemovalCause.SUPPORT_LOSS_NO_DROP
                        || finalDroppedItems != 1) {
                    throw helper.assertionException(
                            "page-4 ENTITY_DROPS=false outcome was not exact");
                }
                if (afterNoDrop.authoredCells().stream().anyMatch(
                        entry -> entry.pos().toBlockPos().equals(firstAnchor))) {
                    throw helper.assertionException(
                            "page-4 broad-clear sentinel unexpectedly gained cell authority");
                }
                world.setBlock(firstAnchor, Blocks.DIAMOND_BLOCK.defaultBlockState(),
                        Block.UPDATE_ALL);
                SlabAnchorAttachment.freezeLoweredOnPlace(
                        world, firstAnchor, world.getBlockState(firstAnchor));
                AABB bounds = ownedBounds(afterNoDrop);
                ItemEntity entitySentinel = new ItemEntity(world, bounds.maxX + 4.5,
                        bounds.minY + 2.0, bounds.maxZ + 4.5,
                        new ItemStack(Items.DIAMOND));
                entitySentinel.setNoGravity(true);
                if (!world.addFreshEntity(entitySentinel)
                        || !SlabAnchorAttachment.hasStoredAttachmentEvidence(world, firstAnchor)) {
                    throw helper.assertionException(
                            "page-4 broad-clear sentinels did not initialize");
                }
                clearVariablePage(helper, world, player, dispatcher, source, playerBefore, 4,
                        () -> {
                            if (!world.getBlockState(firstAnchor).is(Blocks.DIAMOND_BLOCK)
                                    || !SlabAnchorAttachment.hasStoredAttachmentEvidence(
                                    world, firstAnchor)
                                    || world.getEntity(entitySentinel.getUUID()) != entitySentinel) {
                                throw helper.assertionException(
                                        "page-4 exact clear broadened into foreign sentinels");
                            }
                            SlabAnchorAttachment.removeAnchor(world, firstAnchor);
                            world.setBlock(firstAnchor, Blocks.AIR.defaultBlockState(),
                                    Block.UPDATE_ALL);
                            entitySentinel.discard();
                            runRemainingSelectorPages(helper, world, player, dispatcher, source,
                                    playerBefore, 5, afterAllPages);
                        });
            });
        });
    }

    private static void clearVariablePage(
            GameTestHelper helper, ServerLevel world, ServerPlayer player,
            CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source,
            PlayerSnapshot playerBefore, int selectorPage, Runnable completion) {
        SlabRigHangingDirectState.State beforeClear = head(helper);
        requireResult(helper, execute(helper, dispatcher, source,
                "slabrig hangs direct clear"), 1,
                "page " + selectorPage + " exact tick-sliced clear");
        helper.runAfterDelay(50, () -> {
            SlabRigHangingDirectState.State cleared = head(helper);
            if (cleared.phase() != SlabRigHangingDirectState.Phase.CLEARED
                    || cleared.run().selectorPage() != selectorPage
                    || cleared.run().caseCount() != (selectorPage == 4 ? 4 : 16)) {
                throw helper.assertionException(
                        "page " + selectorPage + " did not reach exact CLEARED");
            }
            for (SlabRigHangingDirectState.CellOwnership cell : beforeClear.authoredCells()) {
                BlockPos pos = cell.pos().toBlockPos();
                if (!world.getBlockState(pos).isAir()
                        || SlabAnchorAttachment.hasStoredAttachmentEvidence(world, pos)) {
                    throw helper.assertionException(
                            "page " + selectorPage + " authored cell survived clear " + pos);
                }
            }
            for (SlabRigHangingDirectState.EntityOwnership entity : beforeClear.entities()) {
                if (world.getEntity(entity.uuid()) != null) {
                    throw helper.assertionException(
                            "page " + selectorPage + " owned UUID survived clear " + entity.uuid());
                }
            }
            String artifact = artifactText(helper, cleared.artifacts().cleared());
            if (!artifact.startsWith("schema\tslabbed-rig-hanging-direct-cleared-v2\n")
                    || !artifact.contains("selector_page\t" + selectorPage + "\n")
                    || !artifact.contains("case_count\t" + cleared.run().caseCount() + "\n")
                    || artifact.contains("cleared-v1")) {
                throw helper.assertionException(
                        "page " + selectorPage + " clear artifact grammar is not v2/cardinality-bound");
            }
            playerBefore.assertExact(helper, player, "page " + selectorPage + " CLEARED");
            completion.run();
        });
    }

    private static void assertInvalidDirectFormsAreInert(
            GameTestHelper helper, ServerLevel world,
            CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source,
            SlabRigHangingDirectState.State planned) {
        String durableBefore = storeFingerprint(helper);
        ReservationSnapshot worldBefore = reservationSnapshot(world, planned);
        for (String invalid : List.of(
                "slabrig hangs direct 6142 topology 42 paintings 4",
                "slabrig hangs direct 6144 topology 42 paintings 4",
                "slabrig hangs direct 6143 topology 41 paintings 4",
                "slabrig hangs direct 6143 topology 43 paintings 4",
                "slabrig hangs direct 6143 42 paintings 4",
                "slabrig hangs direct 6143 topology 42 4",
                "slabrig hangs direct 6143 topology 42 paintings 0",
                "slabrig hangs direct 6143 topology 42 paintings 5",
                "slabrig hangs direct 6143 paintings 4 topology 42",
                "slabrig hangs direct 6143 topology 42 paintings 4 junk",
                "slabrig hangs direct 6143 topology 42 paintings 4 force junk")) {
            int result = executeExpectingSyntaxRefusal(helper, dispatcher, source, invalid);
            if (result != 0
                    || !head(helper).stateHash().equals(planned.stateHash())
                    || !reservationSnapshot(world, planned).equals(worldBefore)) {
                throw helper.assertionException(
                        "invalid direct command changed durable/world state: /" + invalid);
            }
        }
        if (!storeFingerprint(helper).equals(durableBefore)) {
            throw helper.assertionException(
                    "invalid direct command set changed isolated store bytes");
        }
    }

    private static int executeExpectingSyntaxRefusal(
            GameTestHelper helper, CommandDispatcher<CommandSourceStack> dispatcher,
            CommandSourceStack source, String command) {
        try {
            return dispatcher.execute(command, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
            return 0;
        } catch (Exception failure) {
            throw helper.assertionException(
                    "invalid direct command failed outside Brigadier: /" + command + ":" + failure);
        }
    }

    private static void runLegacyDispatcherFixtures(
            GameTestHelper helper, ServerLevel world, ServerPlayer player,
            CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source,
            PlayerSnapshot playerBefore) {
        SlabRigHangingDirectState.Owner owner = activeLegacyOwner();
        try {
            SlabRigHangingDirectStateStoreTest.copyVerifiedLegacyScenario(
                    "active-owned", StoreBootstrap.root());
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw helper.assertionException(
                    "could not copy frozen active-owned legacy fixture: " + failure);
        }
        SlabRigHangingDirectExecutor.FixedOwnerOverride child =
                StoreBootstrap.override().openFixedLegacyOwner(
                        world.getServer(), owner, owner.legacyKey());
        expectIllegalState(helper, () -> StoreBootstrap.override().openFixedLegacyOwner(
                world.getServer(), owner, owner.legacyKey()),
                "a second fixed legacy child opened over the current child");
        expectIllegalState(helper, () -> StoreBootstrap.override().close(),
                "parent store override closed over an open fixed child");
        List<String> messages = new ArrayList<>();
        CommandSourceStack fixedSource = capturingSource(source, messages);
        requireResult(helper, execute(helper, dispatcher, fixedSource,
                "slabrig hangs direct status"), 1, "frozen active legacy status");
        String activeStatus = messages.isEmpty() ? "" : messages.getLast();
        for (String required : List.of("6143/42/1", "schema=legacy-v1-clear-only",
                "phase=IMMEDIATE_PARTIAL", "cases=1/16", "clearEntities=0/0",
                "clearAttachments=0/0", "clearCells=0/0")) {
            if (!activeStatus.contains(required)) {
                throw helper.assertionException(
                        "active legacy status omitted " + required + ": " + activeStatus);
            }
        }
        SlabRigHangingDirectState.State active = legacyHead(helper, owner);
        String activeHash = active.stateHash();
        String activeStore = storeFingerprint(helper);
        messages.clear();
        requireResult(helper, execute(helper, dispatcher, fixedSource,
                "slabrig hangs direct resume"), 0, "frozen active legacy resume refusal");
        for (int page = 1; page <= 4; page++) {
            requireResult(helper, execute(helper, dispatcher, fixedSource, startCommand(page)), 0,
                    "frozen active legacy new-page refusal " + page);
        }
        if (!legacyHead(helper, owner).stateHash().equals(activeHash)
                || !storeFingerprint(helper).equals(activeStore)) {
            throw helper.assertionException(
                    "legacy resume/new-page refusal changed durable store bytes");
        }

        BlockPos lower = new BlockPos(8, 64, 8);
        BlockPos upper = new BlockPos(8, 65, 8);
        prepareRawStone(world, lower);
        prepareRawStone(world, upper);
        assertRawOwnedCellsMatch(helper, world, active);
        BlockPos sentinelSupport = new BlockPos(9, 64, 8);
        BlockPos sentinelBlock = new BlockPos(9, 65, 8);
        SlabAnchorAttachment.removeAnchor(world, sentinelSupport);
        SlabAnchorAttachment.removeAnchor(world, sentinelBlock);
        world.setBlock(sentinelSupport, Blocks.STONE_SLAB.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(sentinelBlock, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        SlabAnchorAttachment.addAnchor(world, sentinelBlock, world.getBlockState(sentinelBlock));
        ItemEntity entitySentinel = new ItemEntity(world, player.getX() + 20.0,
                player.getY() + 2.0, player.getZ(),
                new ItemStack(Items.DIAMOND));
        entitySentinel.setNoGravity(true);
        if (!SlabAnchorAttachment.hasStoredAttachmentEvidence(world, sentinelBlock)
                || !world.addFreshEntity(entitySentinel)) {
            throw helper.assertionException(
                    "active legacy foreign block/attachment/entity sentinels did not initialize");
        }
        requireResult(helper, execute(helper, dispatcher, fixedSource,
                "slabrig hangs direct clear"), 1, "frozen active legacy exact clear");
        helper.runAfterDelay(10, () -> {
            SlabRigHangingDirectState.State cleared = legacyHead(helper, owner);
            if (cleared.phase() != SlabRigHangingDirectState.Phase.CLEARED
                    || !world.getBlockState(lower).isAir()
                    || !world.getBlockState(upper).isAir()
                    || !world.getBlockState(sentinelBlock).is(Blocks.DIAMOND_BLOCK)
                    || !SlabAnchorAttachment.hasStoredAttachmentEvidence(world, sentinelBlock)
                    || world.getEntity(entitySentinel.getUUID()) != entitySentinel) {
                throw helper.assertionException(
                        "frozen active legacy clear widened or left exact owned cells"
                                + ";lower=" + world.getBlockState(lower)
                                + ";upper=" + world.getBlockState(upper)
                                + ";sentinel=" + world.getBlockState(sentinelBlock)
                                + ";sentinelAttachment="
                                + SlabAnchorAttachment.hasStoredAttachmentEvidence(
                                world, sentinelBlock)
                                + ";entity=" + world.getEntity(entitySentinel.getUUID())
                                + ";sameEntity="
                                + (world.getEntity(entitySentinel.getUUID()) == entitySentinel));
            }
            String artifact = artifactText(helper, cleared.artifacts().cleared());
            if (!artifact.equals(expectedActiveLegacyClearedArtifact())) {
                throw helper.assertionException(
                        "active legacy clear artifact bytes changed:\n" + artifact);
            }
            child.close();
            SlabAnchorAttachment.removeAnchor(world, sentinelBlock);
            world.setBlock(sentinelBlock, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            world.setBlock(sentinelSupport, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            entitySentinel.discard();
            runPartialLegacyDispatcherFixture(
                    helper, world, player, dispatcher, source, playerBefore);
        });
    }

    private static void runPartialLegacyDispatcherFixture(
            GameTestHelper helper, ServerLevel world, ServerPlayer player,
            CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source,
            PlayerSnapshot playerBefore) {
        SlabRigHangingDirectState.Owner owner = partialLegacyOwner();
        try {
            SlabRigHangingDirectStateStoreTest.copyVerifiedLegacyScenario(
                    "partial-clear", StoreBootstrap.root());
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw helper.assertionException(
                    "could not copy frozen partial-clear legacy fixture: " + failure);
        }
        SlabRigHangingDirectExecutor.FixedOwnerOverride child =
                StoreBootstrap.override().openFixedLegacyOwner(
                        world.getServer(), owner, owner.legacyKey());
        List<String> messages = new ArrayList<>();
        CommandSourceStack fixedSource = capturingSource(source, messages);
        requireResult(helper, execute(helper, dispatcher, fixedSource,
                "slabrig hangs direct status"), 1, "frozen partial legacy status");
        String status = messages.isEmpty() ? "" : messages.getLast();
        for (String required : List.of("6143/42/1", "schema=legacy-v1-clear-only",
                "phase=CLEARING_CELLS", "cases=1/16", "clearEntities=1/1",
                "clearAttachments=2/2", "clearCells=1/2")) {
            if (!status.contains(required)) {
                throw helper.assertionException(
                        "partial legacy status omitted " + required + ": " + status);
            }
        }
        SlabRigHangingDirectState.State partial = legacyHead(helper, owner);
        String partialHash = partial.stateHash();
        String partialStore = storeFingerprint(helper);
        messages.clear();
        requireResult(helper, execute(helper, dispatcher, fixedSource,
                "slabrig hangs direct resume"), 0, "frozen partial legacy resume refusal");
        for (int page = 1; page <= 4; page++) {
            requireResult(helper, execute(helper, dispatcher, fixedSource, startCommand(page)), 0,
                    "frozen partial legacy new-page refusal " + page);
        }
        if (!legacyHead(helper, owner).stateHash().equals(partialHash)
                || !storeFingerprint(helper).equals(partialStore)) {
            throw helper.assertionException(
                    "partial legacy refusal replayed or changed its durable cursor");
        }

        BlockPos remaining = new BlockPos(8, 64, 8);
        BlockPos processed = new BlockPos(8, 65, 8);
        prepareRawStone(world, remaining);
        SlabAnchorAttachment.removeAnchor(world, processed);
        world.setBlock(processed, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        SlabAnchorAttachment.freezeLoweredOnPlace(
                world, processed, world.getBlockState(processed));
        ItemEntity entitySentinel = new ItemEntity(world, player.getX() + 20.0,
                player.getY() + 2.0, player.getZ() + 2.0,
                new ItemStack(Items.EMERALD));
        entitySentinel.setNoGravity(true);
        if (!SlabAnchorAttachment.hasStoredAttachmentEvidence(world, processed)
                || !world.addFreshEntity(entitySentinel)) {
            throw helper.assertionException(
                    "partial legacy processed-cell/entity sentinels did not initialize");
        }
        requireResult(helper, execute(helper, dispatcher, fixedSource,
                "slabrig hangs direct clear"), 1, "frozen partial legacy cursor continuation");
        helper.runAfterDelay(5, () -> {
            SlabRigHangingDirectState.State cleared = legacyHead(helper, owner);
            if (cleared.phase() != SlabRigHangingDirectState.Phase.CLEARED
                    || !world.getBlockState(remaining).isAir()
                    || !world.getBlockState(processed).is(Blocks.DIAMOND_BLOCK)
                    || !SlabAnchorAttachment.hasStoredAttachmentEvidence(world, processed)
                    || world.getEntity(entitySentinel.getUUID()) != entitySentinel) {
                throw helper.assertionException(
                        "partial legacy cursor replayed processed authority or missed remaining cell");
            }
            String artifact = artifactText(helper, cleared.artifacts().cleared());
            if (!artifact.equals(expectedPartialLegacyClearedArtifact())) {
                throw helper.assertionException(
                        "partial legacy clear artifact bytes changed:\n" + artifact);
            }
            child.close();
            SlabRigHangingDirectState.State normal = assertNormalOwnerRestored(
                    helper, world, player, dispatcher, source);
            SlabAnchorAttachment.removeAnchor(world, processed);
            world.setBlock(processed, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            entitySentinel.discard();
            playerBefore.assertExact(helper, player, "legacy active/partial dispatcher clears");
            runMissingWorldIdentityStartupSemantics(helper, world, player, dispatcher, source,
                    normal, playerBefore, () -> runDualActiveStartupRefusal(
                            helper, world, player, dispatcher, source, playerBefore));
        });
    }

    private static void runMissingWorldIdentityStartupSemantics(
            GameTestHelper helper, ServerLevel world, ServerPlayer player,
            CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source,
            SlabRigHangingDirectState.State clearedHistory, PlayerSnapshot playerBefore,
            Runnable completion) {
        if (clearedHistory.phase() != SlabRigHangingDirectState.Phase.CLEARED) {
            throw helper.assertionException(
                    "startup world-key classifier requires exact cleared-only history premise");
        }
        Path base = StoreBootstrap.root().resolve(
                "startup-world-key-classifier-" + UUID.randomUUID());
        try {
            Path absent = base.resolve("absent");
            Files.createDirectories(absent);
            if (SlabRigHangingDirectExecutor.classifyStartupWorldKeyForSerialGameTest(
                    absent, false).isPresent()) {
                throw helper.assertionException(
                        "absent identity with cleared-only/no active history did not classify empty");
            }
            expectStartupWorldKeyFailure(helper, () ->
                            SlabRigHangingDirectExecutor.classifyStartupWorldKeyForSerialGameTest(
                                    absent, true),
                    "absent identity with active ledger");

            Path malformed = base.resolve("malformed");
            Files.createDirectories(malformed.resolve("data"));
            Files.writeString(malformed.resolve("data/slabbed-rig-world-id.tsv"),
                    "schema\twrong\nuuid\tnot-a-uuid\n", StandardCharsets.UTF_8);
            expectStartupWorldKeyFailure(helper, () ->
                            SlabRigHangingDirectExecutor.classifyStartupWorldKeyForSerialGameTest(
                                    malformed, false),
                    "existing malformed identity");

            Path nonRegular = base.resolve("non-regular");
            Files.createDirectories(nonRegular.resolve("data/slabbed-rig-world-id.tsv"));
            expectStartupWorldKeyFailure(helper, () ->
                            SlabRigHangingDirectExecutor.classifyStartupWorldKeyForSerialGameTest(
                                    nonRegular, false),
                    "existing non-regular identity");

            Path symlinked = base.resolve("symlinked");
            Files.createDirectories(symlinked.resolve("data"));
            Path symlinkSource = symlinked.resolve("identity-source.tsv");
            Files.writeString(symlinkSource,
                    "schema\t" + SlabRigHangingDirectStateStore.WORLD_ID_SCHEMA
                            + "\nuuid\t00000000-0000-0000-0000-000000000001\n",
                    StandardCharsets.UTF_8);
            Files.createSymbolicLink(
                    symlinked.resolve("data/slabbed-rig-world-id.tsv"), symlinkSource);
            expectStartupWorldKeyFailure(helper, () ->
                            SlabRigHangingDirectExecutor.classifyStartupWorldKeyForSerialGameTest(
                                    symlinked, false),
                    "existing symlinked identity");

            Path valid = base.resolve("valid");
            Files.createDirectories(valid);
            String expected = SlabRigHangingDirectStateStore.createWorldKey(valid);
            for (boolean activePresent : List.of(false, true)) {
                String actual = SlabRigHangingDirectExecutor
                        .classifyStartupWorldKeyForSerialGameTest(valid, activePresent)
                        .orElseThrow();
                if (!actual.equals(expected)) {
                    throw helper.assertionException(
                            "valid startup world identity changed with active=" + activePresent);
                }
            }
            playerBefore.assertExact(helper, player,
                    "read-only startup world-key classifier");
            completion.run();
        } catch (IOException failure) {
            throw helper.assertionException(
                    "startup world-key classifier regression failed: " + failure);
        }
    }

    private static void expectStartupWorldKeyFailure(
            GameTestHelper helper, StartupWorldKeyProbe probe, String label) {
        try {
            probe.run();
        } catch (IOException expected) {
            return;
        }
        throw helper.assertionException(label + " unexpectedly classified as usable");
    }

    @FunctionalInterface
    private interface StartupWorldKeyProbe {
        void run() throws IOException;
    }

    private static void runDualActiveStartupRefusal(
            GameTestHelper helper, ServerLevel world, ServerPlayer player,
            CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source,
            PlayerSnapshot playerBefore) {
        StoreBootstrap.rotateToFreshStore("dual-active");
        SlabRigHangingDirectState.Owner owner = activeLegacyOwner();
        SlabRigHangingDirectExecutor.FixedOwnerOverride child =
                StoreBootstrap.override().openFixedLegacyOwner(
                        world.getServer(), owner, owner.legacyKey());
        List<String> messages = new ArrayList<>();
        CommandSourceStack fixedSource = capturingSource(source, messages);
        requireResult(helper, execute(helper, dispatcher, fixedSource, startCommand(4)), 1,
                "dual-active synthetic v2 start");
        SlabRigHangingDirectState.State variable = variableHead(helper, owner);
        assertPlannedEnvelope(helper, world, variable, 4, 4);
        ReservationSnapshot worldBefore = reservationSnapshot(world, variable);
        String variableHash = variable.stateHash();
        try {
            SlabRigHangingDirectStateStoreTest.copyVerifiedLegacyScenario(
                    "active-owned", StoreBootstrap.root());
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw helper.assertionException(
                    "could not merge frozen legacy bytes beside active v2: " + failure);
        }
        SlabRigHangingDirectState.State legacy = legacyHead(helper, owner);
        String legacyHash = legacy.stateHash();
        SlabRigHangingDirectExecutor.restartForSerialGameTest(world.getServer());
        String startupRefusal = SlabRigHangingDirectExecutor
                .startupRefusalForSerialGameTest(world.getServer());
        if (!startupRefusal.contains("multiple active direct pages")) {
            throw helper.assertionException(
                    "dual-active startup audit did not report the allocation conflict: "
                            + startupRefusal);
        }
        messages.clear();
        requireResult(helper, execute(helper, dispatcher, fixedSource,
                "slabrig hangs direct status"), 0, "dual-active status refusal");
        if (messages.stream().noneMatch(message -> message.contains(
                "both v2 and legacy-v1 direct ledgers are active"))) {
            throw helper.assertionException(
                    "dual-active command refusal did not expose both schema namespaces: " + messages);
        }
        String blockedStore = storeFingerprint(helper);
        for (String mutation : List.of(startCommand(2), startCommand(4) + " force",
                "slabrig hangs direct resume", "slabrig hangs direct clear")) {
            requireResult(helper, execute(helper, dispatcher, fixedSource, mutation), 0,
                    "startup-audit mutation gate " + mutation);
            if (!variableHead(helper, owner).stateHash().equals(variableHash)
                    || !legacyHead(helper, owner).stateHash().equals(legacyHash)
                    || !reservationSnapshot(world, variable).equals(worldBefore)) {
                throw helper.assertionException(
                        "startup-audit gate allowed state/world mutation: /" + mutation);
            }
        }
        if (!storeFingerprint(helper).equals(blockedStore)) {
            throw helper.assertionException(
                    "startup-audit gate allowed durable store mutation");
        }
        child.close();
        helper.runAfterDelay(10, () -> {
            if (!variableHead(helper, owner).stateHash().equals(variableHash)
                    || !legacyHead(helper, owner).stateHash().equals(legacyHash)
                    || !reservationSnapshot(world, variable).equals(worldBefore)) {
                throw helper.assertionException(
                        "dual-active startup installed/armed a run or mutated world/state");
            }
            playerBefore.assertExact(helper, player, "dual-active startup refusal");
            runDuplicateRunIdStartupRefusal(
                    helper, world, player, dispatcher, source, variable, playerBefore);
        });
    }

    private static void runDuplicateRunIdStartupRefusal(
            GameTestHelper helper, ServerLevel world, ServerPlayer player,
            CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source,
            SlabRigHangingDirectState.State template, PlayerSnapshot playerBefore) {
        String plannedText = artifactText(helper, template.artifacts().planned());
        StoreBootstrap.rotateToFreshStore("duplicate-run-id");
        try {
            String plannedHash = store().writeArtifact(plannedText).hash();
            SlabRigHangingDirectState.RunIdentity prior = template.run();
            SlabRigHangingDirectState.RunIdentity duplicateRun =
                    new SlabRigHangingDirectState.RunIdentity(
                            "ea9af5cf8f64a083245be3b22b8f80a1bcef6e1cbe6a321fe1d326af4ed64f28",
                            prior.runNonce(), prior.buildGitSha(), prior.runtimeContentSha256(),
                            prior.minecraftVersion(), prior.rig3aCatalogHash(),
                            prior.topologyCatalogHash(), prior.rig3b1ExecutionIdentity(),
                            prior.paintingRegistryHash(), prior.universeHash(), prior.planHash(),
                            prior.semanticPageId(), prior.routeIndex(), prior.topologyIndex(),
                            prior.selectorPage(), prior.caseCount(), prior.frozenDyEnabled(),
                            prior.base(), prior.facing());
            SlabRigHangingDirectState.Owner firstOwner =
                    new SlabRigHangingDirectState.Owner(
                            SlabRigHangingDirectState.sha256("duplicate-run-id-world-a"),
                            "minecraft:overworld", UUID.nameUUIDFromBytes(
                            "duplicate-run-id-player-a".getBytes(StandardCharsets.UTF_8)));
            SlabRigHangingDirectState.Owner secondOwner =
                    new SlabRigHangingDirectState.Owner(
                            SlabRigHangingDirectState.sha256("duplicate-run-id-world-b"),
                            "minecraft:the_nether", UUID.nameUUIDFromBytes(
                            "duplicate-run-id-player-b".getBytes(StandardCharsets.UTF_8)));
            SlabRigHangingDirectState.State first = SlabRigHangingDirectState.State.initial(
                    firstOwner, duplicateRun, template.reservedCells(),
                    template.plannedAuthoredCells(), template.cases(), plannedHash,
                    "synthetic duplicate-run-id first");
            SlabRigHangingDirectState.State second = SlabRigHangingDirectState.State.initial(
                    secondOwner, duplicateRun, template.reservedCells(),
                    template.plannedAuthoredCells(), template.cases(), plannedHash,
                    "synthetic duplicate-run-id second");
            store().append(null, first);
            store().append(null, second);
            ReservationSnapshot worldBefore = reservationSnapshot(world, first);
            SlabRigHangingDirectExecutor.restartForSerialGameTest(world.getServer());
            String refusal = SlabRigHangingDirectExecutor
                    .startupRefusalForSerialGameTest(world.getServer());
            if (!refusal.contains("duplicate active direct run id")) {
                throw helper.assertionException(
                        "startup duplicate-run-id audit did not report its exact conflict: "
                                + refusal);
            }
            String blockedStore = storeFingerprint(helper);
            ReservationSnapshot blockedWorld = reservationSnapshot(world, first);
            List<String> messages = new ArrayList<>();
            CommandSourceStack capturedSource = capturingSource(source, messages);
            for (String mutation : List.of(startCommand(1),
                    "slabrig hangs direct resume", "slabrig hangs direct clear")) {
                messages.clear();
                requireResult(helper, execute(helper, dispatcher, capturedSource, mutation), 0,
                        "duplicate-run-id startup mutation gate " + mutation);
                if (messages.stream().noneMatch(message -> message.contains(
                        "direct mutation is blocked by startup audit"))) {
                    throw helper.assertionException(
                            "duplicate-run-id mutation did not hit startup gate: " + messages);
                }
            }
            if (!storeFingerprint(helper).equals(blockedStore)
                    || !reservationSnapshot(world, first).equals(blockedWorld)) {
                throw helper.assertionException(
                        "duplicate-run-id mutation commands changed store/world");
            }
            helper.runAfterDelay(10, () -> {
                try {
                    SlabRigHangingDirectState.State firstAfter =
                            store().reconstruct(firstOwner).latestOrNull();
                    SlabRigHangingDirectState.State secondAfter =
                            store().reconstruct(secondOwner).latestOrNull();
                    if (firstAfter == null || secondAfter == null
                            || !firstAfter.stateHash().equals(first.stateHash())
                            || !secondAfter.stateHash().equals(second.stateHash())
                            || !reservationSnapshot(world, first).equals(worldBefore)) {
                        throw helper.assertionException(
                                "duplicate-run-id startup installed/armed or changed state/world");
                    }
                    playerBefore.assertExact(helper, player,
                            "duplicate-run-id startup refusal");
                    helper.succeed();
                } catch (IOException failure) {
                    throw helper.assertionException(
                            "duplicate-run-id readback failed: " + failure);
                }
            });
        } catch (IOException failure) {
            throw helper.assertionException(
                    "could not create duplicate-run-id startup fixture: " + failure);
        }
    }

    private static SlabRigHangingDirectState.State assertNormalOwnerRestored(
            GameTestHelper helper, ServerLevel world, ServerPlayer player,
            CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source) {
        try {
            String worldKey = SlabRigHangingDirectStateStore.readWorldKey(
                    world.getServer().getWorldPath(LevelResource.ROOT));
            SlabRigHangingDirectState.Owner expected = new SlabRigHangingDirectState.Owner(
                    worldKey, world.dimension().identifier().toString(), player.getUUID());
            SlabRigHangingDirectState.State normal = store().reconstruct(expected).latestOrNull();
            if (normal == null || !normal.owner().equals(expected)
                    || normal.format() != SlabRigHangingDirectState.Format.VARIABLE_V2
                    || normal.phase() != SlabRigHangingDirectState.Phase.CLEARED) {
                throw helper.assertionException(
                        "fixed child close did not restore actual save/dimension/player owner identity");
            }
            List<String> messages = new ArrayList<>();
            requireResult(helper, execute(helper, dispatcher, capturingSource(source, messages),
                    "slabrig hangs direct status"), 1, "normal owner status after fixed child close");
            String status = messages.isEmpty() ? "" : messages.getLast();
            if (!status.contains("schema=v2")
                    || !status.contains("run=" + normal.run().runId().substring(0, 12))) {
                throw helper.assertionException(
                        "normal command owner still exposed fixed legacy state: " + status);
            }
            return normal;
        } catch (IOException failure) {
            throw helper.assertionException(
                    "could not verify restored normal command owner: " + failure);
        }
    }

    private static SlabRigHangingDirectState.Owner activeLegacyOwner() {
        return new SlabRigHangingDirectState.Owner(
                "e984d84acbed5af5211a045f130d9d74e816f96da5a07095658aeb6cb3cf34bf",
                "minecraft:overworld", UUID.fromString("316f2c48-8754-5f9d-975d-984e406f8ea3"));
    }

    private static SlabRigHangingDirectState.Owner partialLegacyOwner() {
        return new SlabRigHangingDirectState.Owner(
                "e984d84acbed5af5211a045f130d9d74e816f96da5a07095658aeb6cb3cf34bf",
                "minecraft:overworld", UUID.fromString("bc6fda81-b928-54c7-8b48-34eadba544a9"));
    }

    private static SlabRigHangingDirectState.State legacyHead(
            GameTestHelper helper, SlabRigHangingDirectState.Owner owner) {
        try {
            SlabRigHangingDirectState.State state = store().reconstructLegacy(owner).latestOrNull();
            if (state == null) {
                throw helper.assertionException("expected frozen legacy head " + owner.legacyKey());
            }
            return state;
        } catch (IOException failure) {
            throw helper.assertionException("legacy reconstruction failed: " + failure);
        }
    }

    private static SlabRigHangingDirectState.State variableHead(
            GameTestHelper helper, SlabRigHangingDirectState.Owner owner) {
        try {
            SlabRigHangingDirectState.State state = store().reconstruct(owner).latestOrNull();
            if (state == null) {
                throw helper.assertionException("expected variable head " + owner.key());
            }
            return state;
        } catch (IOException failure) {
            throw helper.assertionException("variable reconstruction failed: " + failure);
        }
    }

    private static void prepareRawStone(ServerLevel world, BlockPos pos) {
        SlabAnchorAttachment.removeAnchor(world, pos);
        world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        SlabAnchorAttachment.removeAnchor(world, pos);
    }

    private static void assertRawOwnedCellsMatch(
            GameTestHelper helper, ServerLevel world, SlabRigHangingDirectState.State state) {
        Map<SlabRigHangingDirectState.Position,
                SlabRigHangingDirectState.AttachmentOwnership> attachments = new HashMap<>();
        state.authoredAttachments().forEach(entry -> attachments.put(entry.pos(), entry));
        for (SlabRigHangingDirectState.CellOwnership cell : state.authoredCells()) {
            SlabRigHangingDirectEvidence.CellEvidence live =
                    SlabRigHangingDirectEvidence.cell(world, cell.pos().toBlockPos());
            SlabRigHangingDirectState.AttachmentOwnership attachment =
                    attachments.get(cell.pos());
            if (!cell.fingerprint().equals(
                    SlabRigHangingDirectEvidence.cellIdentityFingerprint(live))
                    || attachment == null || !attachment.fingerprint().equals(
                    SlabRigHangingDirectEvidence.attachmentIdentityFingerprint(live))) {
                throw helper.assertionException(
                        "raw legacy cell/attachment fingerprint premise failed at " + cell.pos());
            }
        }
    }

    private static String expectedActiveLegacyClearedArtifact() {
        return "schema\tslabbed-rig-hanging-direct-cleared-v1\n"
                + "run_id\tea9af5cf8f64a083245be3b22b8f80a1bcef6e1cbe6a321fe1d326af4ed64f28\n"
                + "entity_cursor\t1\nattachment_cursor\t2\ncell_cursor\t2\n"
                + "entity_absent\tf83a1777-007d-3093-b166-af82eb65275b\n"
                + "attachment_absent\tPosition[x=8, y=64, z=8]\n"
                + "attachment_absent\tPosition[x=8, y=65, z=8]\n"
                + "cell_cleared\tPosition[x=8, y=64, z=8]\n"
                + "cell_cleared\tPosition[x=8, y=65, z=8]\n";
    }

    private static String expectedPartialLegacyClearedArtifact() {
        return "schema\tslabbed-rig-hanging-direct-cleared-v1\n"
                + "run_id\td2747b19952f0f8379d7a100cc2ec6a910a64129866917c92bd8dfd06457c06a\n"
                + "entity_cursor\t1\nattachment_cursor\t2\ncell_cursor\t2\n"
                + "entity_removed\t87808b7e-2b3e-3536-a129-0c876d4eec6a\n"
                + "attachment_cleared\tPosition[x=8, y=64, z=8]\n"
                + "attachment_cleared\tPosition[x=8, y=65, z=8]\n"
                + "cell_cleared\tPosition[x=8, y=64, z=8]\n"
                + "cell_cleared\tPosition[x=8, y=65, z=8]\n";
    }

    private static CommandSourceStack capturingSource(
            CommandSourceStack source, List<String> messages) {
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                messages.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        return source.withSource(capture);
    }

    private static void expectIllegalState(
            GameTestHelper helper, Runnable action, String falseGreen) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw helper.assertionException(falseGreen);
    }

    private static String storeFingerprint(GameTestHelper helper) {
        Path root = StoreBootstrap.root();
        if (!Files.exists(root)) {
            return SlabRigHangingDirectState.sha256("ABSENT");
        }
        try (var paths = Files.walk(root)) {
            StringBuilder canonical = new StringBuilder();
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                canonical.append(root.relativize(path)).append('\t')
                        .append(Files.size(path)).append('\t')
                        .append(SlabRigHangingDirectState.sha256(Files.readAllBytes(path)))
                        .append('\n');
            }
            return SlabRigHangingDirectState.sha256(canonical.toString());
        } catch (IOException failure) {
            throw helper.assertionException("could not fingerprint isolated direct store: " + failure);
        }
    }

    private static ReservationSnapshot reservationSnapshot(
            ServerLevel world, SlabRigHangingDirectState.State state) {
        Map<SlabRigHangingDirectState.Position, String> cells = new HashMap<>();
        for (SlabRigHangingDirectState.Position position : state.reservedCells()) {
            SlabRigHangingDirectEvidence.CellEvidence live =
                    SlabRigHangingDirectEvidence.cell(world, position.toBlockPos());
            cells.put(position, SlabRigHangingDirectEvidence.cellIdentityFingerprint(live)
                    + ':' + SlabRigHangingDirectEvidence.attachmentIdentityFingerprint(live));
        }
        List<String> entities = world.getEntities((Entity) null, ownedBounds(state),
                        entity -> !entity.isRemoved()).stream()
                .map(entity -> entity.getUUID() + "|" + entity.getClass().getName() + "|"
                        + entity.position() + "|" + entity.getBoundingBox())
                .sorted().toList();
        return new ReservationSnapshot(Map.copyOf(cells), List.copyOf(entities));
    }

    /**
     * Installs an isolated direct-ledger root before SERVER_STARTED. The production callbacks still run;
     * only their filesystem destination is swapped, and the swap closes after executor shutdown clears
     * every in-memory ownership index.
     */
    public static final class StoreBootstrap implements ModInitializer {
        private static Path root;
        private static SlabRigHangingDirectExecutor.StoreOverride override;
        private static SlabRigHangingDirectStateStore independentVerifier;

        @Override
        public void onInitialize() {
            root = FabricLoader.getInstance().getGameDir().resolve("slabbed-rig-integration-")
                    .resolve(UUID.randomUUID().toString());
            override = SlabRigHangingDirectExecutor.openTestStoreOverride(root);
            independentVerifier = new SlabRigHangingDirectStateStore(root);
            ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
                if (override != null) {
                    override.close();
                    override = null;
                }
                independentVerifier = null;
            });
        }

        private static Path root() {
            if (root == null) {
                throw new IllegalStateException("direct integration store bootstrap did not run");
            }
            return root;
        }

        private static SlabRigHangingDirectStateStore independentVerifier() {
            if (independentVerifier == null) {
                throw new IllegalStateException("direct integration verifier bootstrap did not run");
            }
            return independentVerifier;
        }

        private static SlabRigHangingDirectExecutor.StoreOverride override() {
            if (override == null) {
                throw new IllegalStateException("direct integration store override is closed");
            }
            return override;
        }

        private static void rotateToFreshStore(String suffix) {
            if (override == null || root == null) {
                throw new IllegalStateException("direct integration store cannot rotate while closed");
            }
            Path replacementRoot = root.resolveSibling(root.getFileName() + "-" + suffix);
            if (Files.exists(replacementRoot)) {
                throw new IllegalStateException("fresh direct integration root already exists "
                        + replacementRoot);
            }
            override.close();
            root = replacementRoot;
            override = SlabRigHangingDirectExecutor.openTestStoreOverride(root);
            independentVerifier = new SlabRigHangingDirectStateStore(root);
        }
    }

    @GameTest(structure = "slabbed_gametest:rig3b2a_board", maxTicks = 1800,
            environment = "slabbed_gametest:hanging_direct_serial")
    public void productionDirectPagePersistsTicksDropsAndClearsExactly(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockPlayer(helper);
        // Keep the complete 28x26 reserved envelope inside the 48x48 GameTest ticketed region.
        // A mock player does not create normal PlayerList chunk tickets, so the tiny empty template
        // can leave otherwise-loaded paintings outside entity-ticking coverage.
        BlockPos feet = helper.absolutePos(new BlockPos(4, 3, 16));
        player.setPos(Vec3.atBottomCenterOf(feet));
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        SlabRigCommand.register(dispatcher);
        CommandSourceStack source = world.getServer().createCommandSourceStack()
                .withLevel(world)
                .withEntity(player)
                .withPosition(player.position())
                .withPermission(PermissionSet.ALL_PERMISSIONS);
        PlayerSnapshot playerBefore = PlayerSnapshot.capture(player);

        // This test owns a one-test serialized environment. Minecraft's headless runner preserves static
        // Java state across environments, so if any earlier environment left volatile test manifests,
        // retire only those GameTest-server entries before proving the real collision guard with this
        // test's own rows. Safety does not depend on environment ordering.
        SlabRigCommand.clearStaleManifestsForSerialGameTest(
                world.getServer(), world.dimension());
        if (!SlabRigHangingDirectState.NO_VALUE.equals(
                SlabRigHangingDirectExecutor.startupRefusalForSerialGameTest(
                        world.getServer()))) {
            throw helper.assertionException(
                    "empty first-use store was incorrectly blocked for missing world identity");
        }

        // A volatile legacy rig must refuse direct start before a ledger/world mutation.
        requireResult(helper, execute(helper, dispatcher, source, "slabrig rows 1"), 1,
                "legacy rows setup");
        requireResult(helper, execute(helper, dispatcher, source, START), 0,
                "direct start with volatile manifest");
        if (!ledgers(helper).isEmpty()) {
            throw helper.assertionException("volatile-manifest refusal published a direct ledger");
        }
        requireResult(helper, execute(helper, dispatcher, source, "slabrig clear"), 1,
                "legacy exact clear");

        requireResult(helper, execute(helper, dispatcher, source, START), 1,
                "exact direct start");
        SlabRigHangingDirectState.State planned = head(helper);
        assertPlannedEnvelope(helper, world, planned, 1, 16);
        String plannedHash = planned.stateHash();
        requireResult(helper, execute(helper, dispatcher, source,
                "slabrig hangs direct status"), 1, "direct status");
        if (!head(helper).stateHash().equals(plannedHash)) {
            throw helper.assertionException("read-only status changed the durable head");
        }
        playerBefore.assertExact(helper, player, "direct start/status");

        // Literal force is deliberately two-stage: the first invocation only arms bounded clear.
        // It must never hide an unbounded clear-and-restart loop inside the command callback.
        requireResult(helper, execute(helper, dispatcher, source, FORCE), 1,
                "force arms tick-sliced clear");
        SlabRigHangingDirectState.State forceClearing = head(helper);
        if (forceClearing.phase() != SlabRigHangingDirectState.Phase.CLEARING_ENTITIES) {
            throw helper.assertionException("force did not return at its durable clear lane");
        }
        assertAllPlannedCellsEmpty(helper, world, forceClearing);
        String forceClearingHash = forceClearing.stateHash();
        requireResult(helper, execute(helper, dispatcher, source,
                "slabrig hangs direct resume"), 0, "resume refuses active clear lane");
        if (!head(helper).stateHash().equals(forceClearingHash)) {
            throw helper.assertionException("resume during clear changed the durable cursor");
        }

        helper.runAtTickTime(5, () -> {
            if (head(helper).phase() != SlabRigHangingDirectState.Phase.CLEARED) {
                throw helper.assertionException("force tick-sliced clear did not reach CLEARED");
            }
            requireResult(helper, execute(helper, dispatcher, source, FORCE), 1,
                    "second force starts only after CLEARED readback");
            SlabRigHangingDirectState.State replanned = head(helper);
            assertPlannedEnvelope(helper, world, replanned, 1, 16);
            DropSequence.pausedPlannedHash = replanned.stateHash();

            // A refused clear must pause construction before preflight. Keep one foreign entity
            // inside the reservation for five ticks and prove no planned cell advances.
            ItemEntity clearPause = foreignReservationSentinel(helper, world, replanned, 0.25);
            DropSequence.clearPauseUuid = clearPause.getUUID();
            requireResult(helper, execute(helper, dispatcher, source,
                    "slabrig hangs direct clear"), 0, "foreign-reservation clear refusal");
            if (!head(helper).stateHash().equals(DropSequence.pausedPlannedHash)) {
                throw helper.assertionException("refused clear changed the PLANNED head");
            }
        });

        helper.runAtTickTime(10, () -> {
            SlabRigHangingDirectState.State paused = head(helper);
            if (!paused.stateHash().equals(DropSequence.pausedPlannedHash)
                    || world.getEntity(DropSequence.clearPauseUuid) == null) {
                throw helper.assertionException(
                        "refused clear did not freeze construction and preserve the foreign entity");
            }
            assertPlannedEnvelope(helper, world, paused, 1, 16);
            world.getEntity(DropSequence.clearPauseUuid).discard();
            requireResult(helper, execute(helper, dispatcher, source,
                    "slabrig hangs direct resume"), 1, "explicit paused-construction resume");

            // Interference after resume but before the level-tick quantum must be caught by the
            // zero-mutation construction boundary, not converted into a placement/refusal result.
            ItemEntity interference = foreignReservationSentinel(helper, world, paused, 0.75);
            DropSequence.quantumInterferenceUuid = interference.getUUID();
        });

        helper.runAtTickTime(11, () -> {
            SlabRigHangingDirectState.State quarantined = head(helper);
            if (quarantined.phase() != SlabRigHangingDirectState.Phase.QUARANTINED
                    || !quarantined.authoredCells().isEmpty()
                    || world.getEntity(DropSequence.quantumInterferenceUuid) == null) {
                throw helper.assertionException(
                        "pre-quantum foreign interference was not fail-closed before world mutation");
            }
            assertAllPlannedCellsEmpty(helper, world, quarantined);
            world.getEntity(DropSequence.quantumInterferenceUuid).discard();
            requireResult(helper, execute(helper, dispatcher, source,
                    "slabrig hangs direct clear"), 1, "empty quarantined tick-sliced clear");
            if (head(helper).phase() != SlabRigHangingDirectState.Phase.CLEARING_ENTITIES) {
                throw helper.assertionException("clear command did not return at durable clear lane");
            }
        });

        helper.runAtTickTime(15, () -> {
            if (head(helper).phase() != SlabRigHangingDirectState.Phase.CLEARED) {
                throw helper.assertionException("empty tick-sliced clear did not reach CLEARED");
            }
            requireResult(helper, execute(helper, dispatcher, source, START), 1,
                    "second exact direct start after interference proof");
            assertPlannedEnvelope(helper, world, head(helper), 1, 16);
        });

        // START publishes PLANNED and returns; the existing durable lifecycle advances one bounded
        // fixture/case quantum per level tick. Fifty ticks is deliberately above its 34-quantum
        // envelope without tying proof to wall-clock fsync latency.
        helper.runAtTickTime(55, () -> {
            SlabRigHangingDirectState.State waiting = head(helper);
            assertWaitingEnvelope(helper, world, waiting, 1, 16);
            String waitingHash = waiting.stateHash();
            requireResult(helper, execute(helper, dispatcher, source,
                    "slabrig hangs direct status"), 1, "direct waiting status");
            if (!head(helper).stateHash().equals(waitingHash)) {
                throw helper.assertionException("waiting status changed the durable head");
            }
            playerBefore.assertExact(helper, player, "tick-sliced start/waiting status");

            ExactWorldSnapshot beforeRestart = exactWorldSnapshot(world, waiting);
            long priorGeneration = waiting.scheduler().generation();
            SlabRigHangingDirectExecutor.restartForSerialGameTest(world.getServer());
            SlabRigHangingDirectState.State reconstructed = head(helper);
            if (reconstructed.phase() != SlabRigHangingDirectState.Phase.WAITING_DELAYED
                    || reconstructed.nextCaseOrdinal() != 16
                    || reconstructed.authoredCells().size() != 832
                    || reconstructed.entities().size() != 16
                    || reconstructed.scheduler().generation() != priorGeneration + 1
                    || reconstructed.scheduler().credits().stream().anyMatch(
                    credit -> credit.observedEntityTicks() != 0)
                    || !beforeRestart.equals(exactWorldSnapshot(world, reconstructed))) {
                throw helper.assertionException(
                        "process reconstruction replayed work or failed to reset tick authority");
            }
            String reconstructedHash = reconstructed.stateHash();
            requireResult(helper, execute(helper, dispatcher, source,
                    "slabrig hangs direct status"), 1, "process-reconstructed status");
            requireResult(helper, execute(helper, dispatcher, source,
                    "slabrig hangs direct resume"), 1, "process-reconstructed waiting resume");
            if (!head(helper).stateHash().equals(reconstructedHash)) {
                throw helper.assertionException(
                        "reconstructed waiting status/resume changed the durable head");
            }
            SlabRigHangingDirectState.TickCredit unloadTarget = reconstructed.scheduler()
                    .credits().getFirst();
            Entity unloadLive = world.getEntity(unloadTarget.paintingUuid());
            if (!(unloadLive instanceof Painting unloadPainting)) {
                throw helper.assertionException("unload/reload target painting is absent");
            }
            ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(unloadPainting, world);
            SlabRigHangingDirectState.State unloaded = head(helper);
            SlabRigHangingDirectState.TickCredit unloadedCredit = unloaded.scheduler().credits()
                    .stream().filter(credit -> credit.paintingUuid().equals(unloadTarget.paintingUuid()))
                    .findFirst().orElseThrow();
            if (unloadedCredit.loaded() || unloadedCredit.observedEntityTicks() != 0
                    || unloadedCredit.unloadResets() != unloadTarget.unloadResets() + 1) {
                throw helper.assertionException(
                        "entity-unload lifecycle did not pause/reset exact tick authority");
            }
            ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(unloadPainting, world);
            reconstructed = head(helper);
            SlabRigHangingDirectState.TickCredit reloadedCredit = reconstructed.scheduler().credits()
                    .stream().filter(credit -> credit.paintingUuid().equals(unloadTarget.paintingUuid()))
                    .findFirst().orElseThrow();
            if (!reloadedCredit.loaded() || reloadedCredit.observedEntityTicks() != 0
                    || reloadedCredit.unloadResets() != unloadedCredit.unloadResets()) {
                throw helper.assertionException(
                        "entity-reload lifecycle did not rearm from a fresh zero baseline");
            }
            waiting = reconstructed;

            int maxObservedDelta = 0;
            for (SlabRigHangingDirectState.TickCredit credit : waiting.scheduler().credits()) {
                Entity live = world.getEntity(credit.paintingUuid());
                if (!(live instanceof Painting painting)) {
                    throw helper.assertionException(
                            "waiting probe lacks a live painting " + credit.paintingUuid());
                }
                long delta = painting.tickCount - credit.lastObservedEntityTick();
                if (delta < 0 || delta >= 100) {
                    throw helper.assertionException(
                            "waiting probe escaped pre-boundary ticks;delta=" + delta);
                }
                maxObservedDelta = Math.max(maxObservedDelta, (int) delta);
            }
            long tick101 = helper.getTick() + Math.max(1, 100 - maxObservedDelta);
            Slabbed.LOGGER.info("[RIG-3B3A-PERF] waiting_probe_tick={} max_delta={} tick101={}",
                    helper.getTick(), maxObservedDelta, tick101);

        helper.runAtTickTime(tick101, () -> {
            SlabRigHangingDirectState.State at101 = head(helper);
            if (at101.phase() != SlabRigHangingDirectState.Phase.WAITING_DELAYED
                    || at101.scheduler().credits().size() != 16) {
                throw helper.assertionException(
                        "tick 101 finalized early or lost exact survivor credits: " + at101.phase());
            }
            for (SlabRigHangingDirectState.TickCredit credit : at101.scheduler().credits()) {
                Entity live = world.getEntity(credit.paintingUuid());
                if (!(live instanceof Painting painting)) {
                    throw helper.assertionException(
                            "tick-101 boundary lacks a live painting " + credit.paintingUuid());
                }
                long delta = painting.tickCount - credit.lastObservedEntityTick();
                if (!world.isPositionEntityTicking(painting.blockPosition())
                        || delta < 100 || delta >= 102) {
                    throw helper.assertionException(
                            "tick-101 boundary is not a real 100..101 entity-tick observation "
                                    + credit.paintingUuid() + ";delta=" + delta
                                    + ";entityTicking="
                                    + world.isPositionEntityTicking(painting.blockPosition()));
                }
            }
        });

        helper.runAtTickTime(tick101 + 9, () -> {
            SlabRigHangingDirectState.State finalState = head(helper);
            assertFinalEnvelope(helper, world, finalState, 1, 16);

            // A changed exact owned painting must make clear a zero-mutation refusal.
            Painting moved = livePaintings(helper, world, finalState).get(2);
            moved.rotate(Rotation.CLOCKWISE_90);
            String beforeRefusalHash = head(helper).stateHash();
            ExactWorldSnapshot beforeRefusal = exactWorldSnapshot(world, finalState);
            requireResult(helper, execute(helper, dispatcher, source,
                    "slabrig hangs direct clear"), 0, "changed-UUID clear refusal");
            if (!head(helper).stateHash().equals(beforeRefusalHash)
                    || !exactWorldSnapshot(world, finalState).equals(beforeRefusal)) {
                throw helper.assertionException(
                        "clear preflight mismatch changed an exact cell/attachment/entity fingerprint");
            }
            moved.rotate(Rotation.COUNTERCLOCKWISE_90);
            SlabRigHangingDirectState.EntityOwnership movedOwnership = ownership(
                    finalState, moved.getUUID());
            if (!SlabRigHangingDirectEvidence.painting(world, moved)
                    .identityFingerprint().equals(movedOwnership.fingerprint())) {
                throw helper.assertionException("test could not restore the exact moved painting");
            }

            Painting dropping = livePaintings(helper, world, finalState).getFirst();
            BlockPos droppingAnchor = dropping.getPos().immutable();
            removePaintingSupport(world, dropping);
            DropSequence.firstUuid = dropping.getUUID();
            DropSequence.firstAnchor = droppingAnchor;
        });

        helper.runAtTickTime(tick101 + 124, () -> {
            SlabRigHangingDirectState.State afterDrop = head(helper);
            SlabRigHangingDirectState.EntityOwnership removed = ownership(
                    afterDrop, DropSequence.firstUuid);
            List<SlabRigHangingDirectState.EntityOwnership> items = afterDrop.entities().stream()
                    .filter(entry -> entry.role()
                            == SlabRigHangingDirectState.EntityRole.DROPPED_ITEM)
                    .toList();
            if (afterDrop.phase() != SlabRigHangingDirectState.Phase.FINAL
                    || removed.disposition()
                    != SlabRigHangingDirectState.EntityDisposition.REMOVED
                    || removed.removalCause()
                    != SlabRigHangingDirectState.RemovalCause.SUPPORT_LOSS_DROP_EXPECTED
                    || items.size() != 1
                    || !items.getFirst().sourcePaintingUuid().equals(
                    DropSequence.firstUuid.toString())
                    || items.getFirst().decision()
                    != SlabRigHangingDirectState.PreclaimDecision.CLAIM_AND_VETO
                    || world.getEntity(items.getFirst().uuid()) != null) {
                throw helper.assertionException(
                        "support-loss drop was not atomically evidenced and vetoed: " + afterDrop.detail());
            }
            String itemArtifact = artifactText(helper, items.getFirst().evidenceArtifact());
            if (!itemArtifact.contains("slabbed-rig-hanging-direct-item-evidence-v1")
                    || !itemArtifact.contains("item_id\tminecraft:painting")
                    || !itemArtifact.contains("stack_sha256\t")
                    || !itemArtifact.contains("entity_snbt\t")) {
                throw helper.assertionException("drop evidence artifact omitted exact item payload");
            }

            Painting noDrop = livePaintings(helper, world, afterDrop).getFirst();
            DropSequence.secondUuid = noDrop.getUUID();
            world.getGameRules().set(GameRules.ENTITY_DROPS, false, world.getServer());
            removePaintingSupport(world, noDrop);
        });

        helper.runAtTickTime(tick101 + 239, () -> {
            world.getGameRules().set(GameRules.ENTITY_DROPS, true, world.getServer());
            SlabRigHangingDirectState.State beforeClear = head(helper);
            SlabRigHangingDirectState.EntityOwnership noDrop = ownership(
                    beforeClear, DropSequence.secondUuid);
            long droppedItems = beforeClear.entities().stream().filter(entry -> entry.role()
                    == SlabRigHangingDirectState.EntityRole.DROPPED_ITEM).count();
            if (noDrop.disposition() != SlabRigHangingDirectState.EntityDisposition.REMOVED
                    || noDrop.removalCause()
                    != SlabRigHangingDirectState.RemovalCause.SUPPORT_LOSS_NO_DROP
                    || droppedItems != 1) {
                throw helper.assertionException(
                        "ENTITY_DROPS=false did not retain the typed no-item outcome");
            }

            // Reserved-only and foreign-entity sentinels are not clear authority.
            BlockPos blockSentinel = DropSequence.firstAnchor;
            if (beforeClear.authoredCells().stream()
                    .anyMatch(entry -> entry.pos().toBlockPos().equals(blockSentinel))) {
                throw helper.assertionException("chosen reserved-only sentinel gained cell authority");
            }
            world.setBlock(blockSentinel, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            AABB bounds = ownedBounds(beforeClear);
            ItemEntity entitySentinel = new ItemEntity(world, bounds.maxX + 4.5,
                    bounds.minY + 2.0, bounds.maxZ + 4.5, new ItemStack(Items.DIAMOND));
            if (!world.addFreshEntity(entitySentinel)) {
                throw helper.assertionException("could not insert foreign entity sentinel");
            }

            requireResult(helper, execute(helper, dispatcher, source,
                    "slabrig hangs direct clear"), 1, "exact batched direct clear");
            SlabRigHangingDirectState.State clearing = head(helper);
            if (clearing.phase() != SlabRigHangingDirectState.Phase.CLEARING_ENTITIES
                    || !world.getBlockState(blockSentinel).is(Blocks.DIAMOND_BLOCK)
                    || world.getEntity(entitySentinel.getUUID()) != entitySentinel) {
                throw helper.assertionException(
                        "clear command did not return at its durable lane or changed a foreign sentinel");
            }

            helper.runAfterDelay(50, () -> {
                SlabRigHangingDirectState.State cleared = head(helper);
                if (cleared.phase() != SlabRigHangingDirectState.Phase.CLEARED
                        || !world.getBlockState(blockSentinel).is(Blocks.DIAMOND_BLOCK)
                        || world.getEntity(entitySentinel.getUUID()) != entitySentinel) {
                    throw helper.assertionException(
                            "tick-sliced exact clear retired incorrectly or changed a foreign sentinel");
                }
                for (SlabRigHangingDirectState.CellOwnership cell : beforeClear.authoredCells()) {
                    BlockPos pos = cell.pos().toBlockPos();
                    if (!world.getBlockState(pos).isAir()
                            || !Double.isNaN(SlabAnchorAttachment.storedPlacementDy(world, pos))) {
                        throw helper.assertionException("exact authored cell survived clear at " + pos);
                    }
                }
                for (SlabRigHangingDirectState.EntityOwnership entity : beforeClear.entities()) {
                    if (world.getEntity(entity.uuid()) != null) {
                        throw helper.assertionException(
                                "exact owned UUID survived clear " + entity.uuid());
                    }
                }
                String clearedArtifact = artifactText(helper, cleared.artifacts().cleared());
                if (!clearedArtifact.startsWith(
                        "schema\tslabbed-rig-hanging-direct-cleared-v2\n")
                        || !clearedArtifact.contains("selector_page\t1\n")
                        || !clearedArtifact.contains("case_count\t16\n")
                        || clearedArtifact.contains("cleared-v1")) {
                    throw helper.assertionException(
                            "variable-page terminal clear artifact grammar is not exact");
                }
                playerBefore.assertExact(helper, player, "full production direct lifecycle");
                SlabRigHangingDirectState.State stale = appendStalePlannedRun(helper, cleared);
                SlabRigHangingDirectExecutor.restartForSerialGameTest(world.getServer());
                requireResult(helper, execute(helper, dispatcher, source,
                        "slabrig hangs direct status"), 1, "production stale-runtime status");
                requireResult(helper, execute(helper, dispatcher, source,
                        "slabrig hangs direct resume"), 0,
                        "production stale-runtime clear-only resume refusal");
                if (!head(helper).stateHash().equals(stale.stateHash())) {
                    throw helper.assertionException(
                            "production stale-runtime classification changed durable head");
                }
                requireResult(helper, execute(helper, dispatcher, source,
                        "slabrig hangs direct clear"), 1,
                        "production stale-runtime clear-only exact clear");
                helper.runAfterDelay(5, () -> {
                    if (head(helper).phase() != SlabRigHangingDirectState.Phase.CLEARED) {
                        throw helper.assertionException(
                                "production stale-runtime clear-only shell did not reach CLEARED");
                    }
                    SlabAnchorAttachment.removeAnchor(world, blockSentinel);
                    world.setBlock(blockSentinel, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    entitySentinel.discard();
                    runRemainingSelectorPages(helper, world, player, dispatcher, source,
                            playerBefore, 2, () -> runLegacyDispatcherFixtures(
                                    helper, world, player, dispatcher, source, playerBefore));
                });
            });
        });
        });
    }

    private static SlabRigHangingDirectState.State appendStalePlannedRun(
            GameTestHelper helper, SlabRigHangingDirectState.State cleared) {
        try {
            SlabRigHangingDirectState.RunIdentity prior = cleared.run();
            UUID nonce = UUID.nameUUIDFromBytes((cleared.stateHash() + "|stale-runtime")
                    .getBytes(StandardCharsets.UTF_8));
            String runId = SlabRigHangingDirectState.sha256(
                    prior.runId() + '\0' + nonce + "\0stale-runtime");
            SlabRigHangingDirectState.RunIdentity staleRun =
                    new SlabRigHangingDirectState.RunIdentity(runId, nonce, "0000000",
                            prior.runtimeContentSha256(), prior.minecraftVersion(),
                            prior.rig3aCatalogHash(), prior.topologyCatalogHash(),
                            prior.rig3b1ExecutionIdentity(), prior.paintingRegistryHash(),
                            prior.universeHash(), prior.planHash(), prior.semanticPageId(),
                            prior.routeIndex(), prior.topologyIndex(), prior.selectorPage(),
                            prior.caseCount(),
                            prior.frozenDyEnabled(), prior.base(), prior.facing());
            List<SlabRigHangingDirectState.CaseState> pending = cleared.cases().stream()
                    .map(entry -> new SlabRigHangingDirectState.CaseState(entry.ordinal(),
                            entry.attemptId(), entry.selectorId(), entry.componentFingerprint(),
                            SlabRigHangingDirectState.CasePhase.PENDING,
                            SlabRigHangingDirectState.CaseOutcome.NONE,
                            SlabRigHangingDirectState.NO_VALUE))
                    .toList();
            SlabRigHangingDirectStateStore writer =
                    new SlabRigHangingDirectStateStore(StoreBootstrap.root());
            String plannedArtifact = writer.writeArtifact(
                    "schema\tslabbed-rig-test-stale-runtime-v1\nrun_id\t" + runId + "\n").hash();
            SlabRigHangingDirectState.State stale = SlabRigHangingDirectState.State.afterCleared(
                    cleared, staleRun, cleared.reservedCells(), cleared.plannedAuthoredCells(),
                    pending, plannedArtifact, "planned;force=false");
            writer.append(cleared, stale);
            return stale;
        } catch (IOException failure) {
            throw helper.assertionException("could not append exact stale-runtime test state: " + failure);
        }
    }

    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        if (!(helper.makeMockServerPlayer(GameType.SURVIVAL) instanceof ServerPlayer player)) {
            throw helper.assertionException("GameTest did not provide a server-backed mock player");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 3));
        player.getInventory().setItem(7, new ItemStack(Items.COOKIE, 11));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COMPASS));
        player.awardStat(PAINTING_USED, 7);
        return player;
    }

    private static int execute(GameTestHelper helper,
                               CommandDispatcher<CommandSourceStack> dispatcher,
                               CommandSourceStack source, String command) {
        long started = System.nanoTime();
        int result;
        try {
            result = dispatcher.execute(command, source);
        } catch (Exception failure) {
            throw helper.assertionException("/" + command + " threw: " + failure);
        }
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        Slabbed.LOGGER.info("[RIG-3B3A-PERF] command={} duration_ms={}",
                command, elapsedMillis);
        long budget = command.matches(
                "slabrig hangs direct 6143 topology 42 paintings [1-4]( force)?")
                ? START_BUDGET_MILLIS : READ_OR_CLEAR_BUDGET_MILLIS;
        if (elapsedMillis > budget) {
            throw helper.assertionException("/" + command + " exceeded fluidity budget "
                    + elapsedMillis + "ms > " + budget + "ms");
        }
        return result;
    }

    private static void requireResult(GameTestHelper helper, int actual, int expected, String lane) {
        if (actual != expected) {
            throw helper.assertionException(lane + " returned " + actual + " instead of " + expected);
        }
    }

    private static SlabRigHangingDirectStateStore store() {
        return StoreBootstrap.independentVerifier();
    }

    private static SlabRigHangingDirectState.State head(GameTestHelper helper) {
        List<SlabRigHangingDirectStateStore.Reconstruction> ledgers = ledgers(helper);
        if (ledgers.size() != 1 || ledgers.getFirst().latestOrNull() == null) {
            throw helper.assertionException(
                    "expected one exact direct ledger, found " + ledgers.size());
        }
        return ledgers.getFirst().latestOrNull();
    }

    private static List<SlabRigHangingDirectStateStore.Reconstruction> ledgers(
            GameTestHelper helper) {
        long started = System.nanoTime();
        List<SlabRigHangingDirectStateStore.Reconstruction> result;
        try {
            result = store().reconstructAll();
        } catch (IOException failure) {
            throw helper.assertionException("direct integration reconstruction failed: " + failure);
        }
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        Slabbed.LOGGER.info("[RIG-3B3A-PERF] independent_reconstruct_all duration_ms={}",
                elapsedMillis);
        if (elapsedMillis > READ_OR_CLEAR_BUDGET_MILLIS) {
            throw helper.assertionException("independent ledger reconstruction exceeded fluidity budget "
                    + elapsedMillis + "ms > " + READ_OR_CLEAR_BUDGET_MILLIS + "ms");
        }
        return result;
    }

    private static void assertPlannedEnvelope(GameTestHelper helper, ServerLevel world,
                                              SlabRigHangingDirectState.State state,
                                              int expectedPage, int expectedCount) {
        String plannedArtifact = SlabRigHangingDirectState.NO_VALUE.equals(
                state.artifacts().planned()) ? "" : artifactText(
                helper, state.artifacts().planned());
        if (state.phase() != SlabRigHangingDirectState.Phase.PLANNED
                || state.format() != SlabRigHangingDirectState.Format.VARIABLE_V2
                || state.run().selectorPage() != expectedPage
                || state.run().caseCount() != expectedCount
                || state.cases().size() != expectedCount
                || state.nextCaseOrdinal() != 0
                || !state.authoredCells().isEmpty()
                || !state.authoredAttachments().isEmpty()
                || !state.entities().isEmpty()
                || !state.scheduler().credits().isEmpty()
                || state.reservedCells().size() != expectedCount * 68
                || state.plannedAuthoredCells().size() != expectedCount * 52
                || state.cases().stream().anyMatch(entry ->
                entry.phase() != SlabRigHangingDirectState.CasePhase.PENDING)
                || !plannedArtifact.startsWith(
                "schema\tslabbed-rig-hanging-direct-planned-v2\n")
                || !plannedArtifact.contains("player_proof\tABSENT\n")
                || !plannedArtifact.contains("execution_contract\t"
                + SlabRigHangingDirectState.EXECUTION_CONTRACT + "\n")
                || !plannedArtifact.contains("address\t6143/42/" + expectedPage + "\n")
                || !plannedArtifact.contains("case_count\t" + expectedCount + "\n")
                || occurrences(plannedArtifact, "case\t") != expectedCount) {
            throw helper.assertionException("production START did not return at exact PLANNED: "
                    + state.detail());
        }
        assertAllPlannedCellsEmpty(helper, world, state);
    }

    private static void assertAllPlannedCellsEmpty(GameTestHelper helper, ServerLevel world,
                                                   SlabRigHangingDirectState.State state) {
        for (SlabRigHangingDirectState.Position planned : state.plannedAuthoredCells()) {
            BlockPos pos = planned.toBlockPos();
            if (!world.getBlockState(pos).isAir()
                    || !Double.isNaN(SlabAnchorAttachment.storedPlacementDy(world, pos))) {
                throw helper.assertionException(
                        "PLANNED return already mutated a fixture cell " + planned);
            }
        }
    }

    private static ItemEntity foreignReservationSentinel(GameTestHelper helper, ServerLevel world,
                                                         SlabRigHangingDirectState.State state,
                                                         double offset) {
        AABB bounds = ownedBounds(state);
        ItemEntity sentinel = new ItemEntity(world, bounds.minX + 0.5 + offset,
                bounds.minY + 2.0, bounds.minZ + 0.5 + offset,
                new ItemStack(Items.DIAMOND));
        sentinel.setNoGravity(true);
        if (!world.addFreshEntity(sentinel)) {
            throw helper.assertionException("could not insert reserved-volume foreign sentinel");
        }
        return sentinel;
    }

    private static void assertWaitingEnvelope(GameTestHelper helper, ServerLevel world,
                                              SlabRigHangingDirectState.State state,
                                              int expectedPage, int expectedCount) {
        long placed = state.cases().stream().filter(entry -> entry.outcome()
                == SlabRigHangingDirectState.CaseOutcome.PLACED).count();
        long paintings = state.entities().stream().filter(entry -> entry.role()
                == SlabRigHangingDirectState.EntityRole.PAINTING).count();
        String immediateArtifact = SlabRigHangingDirectState.NO_VALUE.equals(
                state.artifacts().immediate()) ? "" : artifactText(
                helper, state.artifacts().immediate());
        if (state.phase() != SlabRigHangingDirectState.Phase.WAITING_DELAYED
                || state.format() != SlabRigHangingDirectState.Format.VARIABLE_V2
                || state.run().selectorPage() != expectedPage
                || state.run().caseCount() != expectedCount
                || state.nextCaseOrdinal() != expectedCount
                || state.cases().size() != expectedCount
                || placed != expectedCount || paintings != expectedCount
                || state.authoredCells().size() != expectedCount * 52
                || state.authoredAttachments().size() != expectedCount * 52
                || state.scheduler().credits().size() != expectedCount
                || !immediateArtifact.startsWith(
                "schema\tslabbed-rig-hanging-direct-immediate-v2\n")
                || !immediateArtifact.contains(
                "selector_page\t" + state.run().selectorPage() + "\n")
                || !immediateArtifact.contains(
                "case_count\t" + state.run().caseCount() + "\n")
                || occurrences(immediateArtifact, "case\t") != expectedCount) {
            throw helper.assertionException("production start envelope drifted: " + state.detail());
        }
        for (SlabRigHangingDirectState.CaseState entry : state.cases()) {
            if (entry.phase() != SlabRigHangingDirectState.CasePhase.IMMEDIATE) {
                throw helper.assertionException("case did not reach IMMEDIATE " + entry.ordinal());
            }
        }
        Map<SlabRigHangingDirectState.Position,
                SlabRigHangingDirectState.AttachmentOwnership> attachments = new HashMap<>();
        state.authoredAttachments().forEach(attachment ->
                attachments.put(attachment.pos(), attachment));
        for (SlabRigHangingDirectState.CellOwnership cell : state.authoredCells()) {
            SlabRigHangingDirectState.AttachmentOwnership attachment = attachments.get(cell.pos());
            if (attachment == null) {
                throw helper.assertionException("cell lacks same-position attachment receipt " + cell.pos());
            }
            try {
                SlabRigHangingDirectEvidence.verifyCellAndAttachmentArtifact(
                        cell.pos().toBlockPos(), cell.fingerprint(), attachment.fingerprint(),
                        artifactBytes(helper, cell.fingerprint()));
            } catch (IllegalArgumentException failure) {
                throw helper.assertionException("cell/attachment evidence inverse failed at "
                        + cell.pos() + ": " + failure);
            }
        }
        for (SlabRigHangingDirectState.EntityOwnership entity : state.entities()) {
            if (entity.acquisition() != SlabRigHangingDirectState.Acquisition.LOADED
                    || entity.disposition()
                    != SlabRigHangingDirectState.EntityDisposition.IN_WORLD
                    || !(world.getEntity(entity.uuid()) instanceof Painting)) {
                throw helper.assertionException("painting ownership/load envelope drifted " + entity.uuid());
            }
            String evidence = artifactText(helper, entity.evidenceArtifact());
            if (!evidence.contains("slabbed-rig-hanging-direct-painting-evidence-v1")
                    || !evidence.contains("painting_nbt_sha256\t")
                    || !evidence.contains("painting_aabb_bits\t")) {
                throw helper.assertionException("painting evidence artifact is incomplete " + entity.uuid());
            }
        }
    }

    private static void assertFinalEnvelope(GameTestHelper helper, ServerLevel world,
                                            SlabRigHangingDirectState.State state,
                                            int expectedPage, int expectedCount) {
        if (state.phase() != SlabRigHangingDirectState.Phase.FINAL
                || state.run().selectorPage() != expectedPage
                || state.run().caseCount() != expectedCount
                || SlabRigHangingDirectState.NO_VALUE.equals(state.artifacts().finalArtifact())
                || state.scheduler().credits().stream().anyMatch(credit -> !credit.loaded()
                || credit.observedEntityTicks() < SlabRigHangingDirectState.REQUIRED_ENTITY_TICKS)) {
            throw helper.assertionException("tick-102+ final envelope is incomplete: " + state.detail());
        }
        String finalArtifact = artifactText(helper, state.artifacts().finalArtifact());
        if (!finalArtifact.startsWith(
                "schema\tslabbed-rig-hanging-direct-final-v2\n")
                || !finalArtifact.contains("player_proof\tABSENT")
                || !finalArtifact.contains("selector_page\t" + state.run().selectorPage() + "\n")
                || !finalArtifact.contains("case_count\t" + state.run().caseCount() + "\n")
                || occurrences(finalArtifact, "painting_uuid\t") != expectedCount) {
            throw helper.assertionException("FINAL omitted exact survivor evidence");
        }
        if (livePaintings(helper, world, state).size() != expectedCount) {
            throw helper.assertionException("FINAL does not retain every live painting");
        }
    }

    private static List<Painting> livePaintings(GameTestHelper helper, ServerLevel world,
                                                SlabRigHangingDirectState.State state) {
        List<Painting> result = new ArrayList<>();
        state.entities().stream()
                .filter(entry -> entry.role() == SlabRigHangingDirectState.EntityRole.PAINTING
                        && entry.disposition()
                        == SlabRigHangingDirectState.EntityDisposition.IN_WORLD)
                .sorted(Comparator.comparing(entry -> entry.uuid().toString()))
                .forEach(entry -> {
                    Entity entity = world.getEntity(entry.uuid());
                    if (!(entity instanceof Painting painting)) {
                        throw helper.assertionException("owned live painting UUID is absent " + entry.uuid());
                    }
                    result.add(painting);
                });
        return List.copyOf(result);
    }

    private static SlabRigHangingDirectState.EntityOwnership ownership(
            SlabRigHangingDirectState.State state, UUID uuid) {
        return state.entities().stream().filter(entry -> entry.uuid().equals(uuid))
                .findFirst().orElseThrow(() -> new IllegalStateException("missing ownership " + uuid));
    }

    private static void removePaintingSupport(ServerLevel world, Painting painting) {
        BlockPos support = painting.getPos().relative(painting.getDirection().getOpposite());
        world.setBlock(support, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static ExactWorldSnapshot exactWorldSnapshot(
            ServerLevel world, SlabRigHangingDirectState.State state) {
        Map<SlabRigHangingDirectState.Position, String> cells = new HashMap<>();
        for (SlabRigHangingDirectState.CellOwnership cell : state.authoredCells()) {
            SlabRigHangingDirectEvidence.CellEvidence live =
                    SlabRigHangingDirectEvidence.cell(world, cell.pos().toBlockPos());
            cells.put(cell.pos(), SlabRigHangingDirectEvidence.cellIdentityFingerprint(live)
                    + ":" + SlabRigHangingDirectEvidence.attachmentIdentityFingerprint(live));
        }
        Map<UUID, String> entities = new HashMap<>();
        for (SlabRigHangingDirectState.EntityOwnership ownership : state.entities()) {
            Entity live = world.getEntity(ownership.uuid());
            if (live == null) {
                entities.put(ownership.uuid(), "ABSENT");
            } else if (live instanceof Painting painting) {
                entities.put(ownership.uuid(),
                        SlabRigHangingDirectEvidence.painting(world, painting).toString());
            } else if (live instanceof ItemEntity item) {
                entities.put(ownership.uuid(),
                        SlabRigHangingDirectEvidence.item(world, item).toString());
            } else {
                throw new IllegalStateException("owned UUID changed entity class "
                        + ownership.uuid() + ":" + live.getClass().getName());
            }
        }
        return new ExactWorldSnapshot(Map.copyOf(cells), Map.copyOf(entities));
    }

    private static AABB ownedBounds(SlabRigHangingDirectState.State state) {
        int minX = state.reservedCells().stream().mapToInt(SlabRigHangingDirectState.Position::x)
                .min().orElseThrow();
        int minY = state.reservedCells().stream().mapToInt(SlabRigHangingDirectState.Position::y)
                .min().orElseThrow();
        int minZ = state.reservedCells().stream().mapToInt(SlabRigHangingDirectState.Position::z)
                .min().orElseThrow();
        int maxX = state.reservedCells().stream().mapToInt(SlabRigHangingDirectState.Position::x)
                .max().orElseThrow();
        int maxY = state.reservedCells().stream().mapToInt(SlabRigHangingDirectState.Position::y)
                .max().orElseThrow();
        int maxZ = state.reservedCells().stream().mapToInt(SlabRigHangingDirectState.Position::z)
                .max().orElseThrow();
        return new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
    }

    private static String artifactText(GameTestHelper helper, String hash) {
        return new String(artifactBytes(helper, hash), StandardCharsets.UTF_8);
    }

    private static byte[] artifactBytes(GameTestHelper helper, String hash) {
        try {
            return store().readArtifact(hash);
        } catch (IOException failure) {
            throw helper.assertionException("linked artifact failed readback " + hash + ": " + failure);
        }
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int index = 0; (index = text.indexOf(needle, index)) >= 0;
             index += needle.length()) {
            count++;
        }
        return count;
    }

    private static final class DropSequence {
        private static UUID firstUuid;
        private static UUID secondUuid;
        private static BlockPos firstAnchor;
        private static String pausedPlannedHash;
        private static UUID clearPauseUuid;
        private static UUID quantumInterferenceUuid;
    }

    private record ExactWorldSnapshot(
            Map<SlabRigHangingDirectState.Position, String> cellAndAttachmentFingerprints,
            Map<UUID, String> entityEvidence) {
    }

    private record ReservationSnapshot(
            Map<SlabRigHangingDirectState.Position, String> cellAndAttachmentFingerprints,
            List<String> entityEvidence) {
    }

    private record PlayerSnapshot(List<ItemStack> inventory, int selectedSlot, int paintingUsed,
                                  String advancementProgressFingerprint) {
        private static PlayerSnapshot capture(ServerPlayer player) {
            List<ItemStack> inventory = new ArrayList<>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                inventory.add(player.getInventory().getItem(slot).copy());
            }
            return new PlayerSnapshot(List.copyOf(inventory),
                    player.getInventory().getSelectedSlot(),
                    player.getStats().getValue(PAINTING_USED), advancementFingerprint(player));
        }

        private void assertExact(GameTestHelper helper, ServerPlayer player, String lane) {
            if (player.getInventory().getSelectedSlot() != selectedSlot
                    || player.getStats().getValue(PAINTING_USED) != paintingUsed
                    || !advancementProgressFingerprint.equals(advancementFingerprint(player))
                    || player.getInventory().getContainerSize() != inventory.size()) {
                throw helper.assertionException("player envelope changed after " + lane);
            }
            for (int slot = 0; slot < inventory.size(); slot++) {
                if (!ItemStack.matches(inventory.get(slot), player.getInventory().getItem(slot))) {
                    throw helper.assertionException(
                            "player inventory slot " + slot + " changed after " + lane);
                }
            }
        }

        /** Read-only snapshot; production proxy actions never receive the command player. */
        @SuppressWarnings("unchecked")
        private static String advancementFingerprint(ServerPlayer player) {
            try {
                Field field = PlayerAdvancements.class.getDeclaredField("progress");
                field.setAccessible(true);
                Map<AdvancementHolder, AdvancementProgress> progress =
                        (Map<AdvancementHolder, AdvancementProgress>) field.get(
                                player.getAdvancements());
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
                return SlabRigHangingDirectEvidence.sha256(out.toString());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(
                        "cannot snapshot advancement progress read-only", failure);
            }
        }
    }
}
