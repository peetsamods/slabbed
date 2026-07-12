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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** End-to-end production-command gate for the one reviewed 6143/42/1 SBSBS painting page. */
public final class SlabRigHangingDirectExecutorTest {

    private static final Stat<Item> PAINTING_USED = Stats.ITEM_USED.get(Items.PAINTING);
    private static final String START =
            "slabrig hangs direct 6143 topology 42 paintings 1";
    private static final String FORCE = START + " force";
    private static final long START_BUDGET_MILLIS = 15_000L;
    private static final long READ_OR_CLEAR_BUDGET_MILLIS = 5_000L;

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
    }

    @GameTest(structure = "slabbed_gametest:rig3b2a_board", maxTicks = 500,
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
        assertPlannedEnvelope(helper, world, planned);
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
            assertPlannedEnvelope(helper, world, replanned);
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
            assertPlannedEnvelope(helper, world, paused);
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
            assertPlannedEnvelope(helper, world, head(helper));
        });

        // START publishes PLANNED and returns; the existing durable lifecycle advances one bounded
        // fixture/case quantum per level tick. Fifty ticks is deliberately above its 34-quantum
        // envelope without tying proof to wall-clock fsync latency.
        helper.runAtTickTime(55, () -> {
            SlabRigHangingDirectState.State waiting = head(helper);
            assertWaitingEnvelope(helper, world, waiting);
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
            Slabbed.LOGGER.info("[RIG-3B2B1-PERF] waiting_probe_tick={} max_delta={} tick101={}",
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
            assertFinalEnvelope(helper, world, finalState);

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
                if (!clearedArtifact.contains("slabbed-rig-hanging-direct-cleared-v1")) {
                    throw helper.assertionException("terminal clear artifact schema is absent");
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
                    helper.succeed();
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
        Slabbed.LOGGER.info("[RIG-3B2B1-PERF] command={} duration_ms={}",
                command, elapsedMillis);
        long budget = START.equals(command) || FORCE.equals(command)
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
        Slabbed.LOGGER.info("[RIG-3B2B1-PERF] independent_reconstruct_all duration_ms={}",
                elapsedMillis);
        if (elapsedMillis > READ_OR_CLEAR_BUDGET_MILLIS) {
            throw helper.assertionException("independent ledger reconstruction exceeded fluidity budget "
                    + elapsedMillis + "ms > " + READ_OR_CLEAR_BUDGET_MILLIS + "ms");
        }
        return result;
    }

    private static void assertPlannedEnvelope(GameTestHelper helper, ServerLevel world,
                                              SlabRigHangingDirectState.State state) {
        if (state.phase() != SlabRigHangingDirectState.Phase.PLANNED
                || state.nextCaseOrdinal() != 0
                || !state.authoredCells().isEmpty()
                || !state.authoredAttachments().isEmpty()
                || !state.entities().isEmpty()
                || !state.scheduler().credits().isEmpty()
                || state.plannedAuthoredCells().size() != 16 * 52
                || state.cases().stream().anyMatch(entry ->
                entry.phase() != SlabRigHangingDirectState.CasePhase.PENDING)
                || SlabRigHangingDirectState.NO_VALUE.equals(state.artifacts().planned())
                || !artifactText(helper, state.artifacts().planned())
                .contains("player_proof\tABSENT")) {
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
                                              SlabRigHangingDirectState.State state) {
        long placed = state.cases().stream().filter(entry -> entry.outcome()
                == SlabRigHangingDirectState.CaseOutcome.PLACED).count();
        long paintings = state.entities().stream().filter(entry -> entry.role()
                == SlabRigHangingDirectState.EntityRole.PAINTING).count();
        if (state.phase() != SlabRigHangingDirectState.Phase.WAITING_DELAYED
                || state.nextCaseOrdinal() != 16 || state.cases().size() != 16
                || placed != 16 || paintings != 16
                || state.authoredCells().size() != 16 * 52
                || state.authoredAttachments().size() != 16 * 52
                || state.scheduler().credits().size() != 16
                || !SlabRigHangingDirectState.NO_VALUE.equals(state.artifacts().immediate())
                && artifactText(helper, state.artifacts().immediate()).isEmpty()) {
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
                                            SlabRigHangingDirectState.State state) {
        if (state.phase() != SlabRigHangingDirectState.Phase.FINAL
                || SlabRigHangingDirectState.NO_VALUE.equals(state.artifacts().finalArtifact())
                || state.scheduler().credits().stream().anyMatch(credit -> !credit.loaded()
                || credit.observedEntityTicks() < SlabRigHangingDirectState.REQUIRED_ENTITY_TICKS)) {
            throw helper.assertionException("tick-102+ final envelope is incomplete: " + state.detail());
        }
        String finalArtifact = artifactText(helper, state.artifacts().finalArtifact());
        if (!finalArtifact.contains("slabbed-rig-hanging-direct-final-v1")
                || !finalArtifact.contains("player_proof\tABSENT")
                || occurrences(finalArtifact, "painting_uuid\t") != 16) {
            throw helper.assertionException("FINAL omitted exact survivor evidence");
        }
        if (livePaintings(helper, world, state).size() != 16) {
            throw helper.assertionException("FINAL does not retain all 16 live paintings");
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

    private record PlayerSnapshot(List<ItemStack> inventory, int selectedSlot, int paintingUsed) {
        private static PlayerSnapshot capture(ServerPlayer player) {
            List<ItemStack> inventory = new ArrayList<>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                inventory.add(player.getInventory().getItem(slot).copy());
            }
            return new PlayerSnapshot(List.copyOf(inventory),
                    player.getInventory().getSelectedSlot(),
                    player.getStats().getValue(PAINTING_USED));
        }

        private void assertExact(GameTestHelper helper, ServerPlayer player, String lane) {
            if (player.getInventory().getSelectedSlot() != selectedSlot
                    || player.getStats().getValue(PAINTING_USED) != paintingUsed
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
    }
}
