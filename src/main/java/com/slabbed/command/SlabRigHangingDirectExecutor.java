package com.slabbed.command;

import com.mojang.brigadier.context.CommandContext;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.BuildStamp;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Crash-reconstructible production executor for the one reviewed RIG-3B2B1 witness page.
 *
 * <p>The executor never discovers mutation targets from an area, proximity, entity class, or current
 * bounds. Every authored cell and entity UUID comes from the immutable page or a synchronous
 * pre-insertion claim. State publication is write-ahead for fixture/case actions; exact evidence is
 * content-addressed before the state that links it. Fixture receipts are committed in sixteen
 * dependency-complete 52-cell batches so the append-only store remains usable while preserving exact
 * confirmed authorship.
 */
public final class SlabRigHangingDirectExecutor {

    private static final int ROUTE = SlabRigHangingDirectState.ROUTE_INDEX;
    private static final int TOPOLOGY = SlabRigHangingDirectState.TOPOLOGY_INDEX;
    private static final int PAGE = SlabRigHangingDirectState.SELECTOR_PAGE;
    private static final String FACING = "west";
    private static final int CLEAR_FLAGS = Block.UPDATE_ALL;
    private static final int CLEAR_BATCH_SIZE = 64;
    private static SlabRigHangingDirectStateStore STORE =
            SlabRigHangingDirectStateStore.production();
    private static final SlabRigHangingDirectEntityGate.Handler ENTITY_HANDLER =
            new ProductionEntityHandler();
    private static final Map<String, ActiveRun> RUNS_BY_ID = new LinkedHashMap<>();
    private static final Map<OwnerLevelKey, ActiveRun> RUNS_BY_OWNER_LEVEL = new LinkedHashMap<>();
    private static final Map<EntityLevelKey, ActiveRun> RUNS_BY_ENTITY = new LinkedHashMap<>();
    private static final Map<EntityLevelKey, PendingRemoval> PENDING_REMOVALS = new LinkedHashMap<>();
    private static final Map<EntityLevelKey, DeferredRemoval> DEFERRED_REMOVALS = new LinkedHashMap<>();
    private static final Set<EntityLevelKey> CLEARING_ENTITIES = new HashSet<>();

    private static boolean registered;
    private static boolean testStoreOverrideOpen;
    private static MinecraftServer activeServer;
    private static MinecraftServer stoppingServer;
    private static String processEpoch = SlabRigHangingDirectState.NO_VALUE;

    private SlabRigHangingDirectExecutor() {
    }

    /** Isolated integration-test root; refuses while any server/run can observe the swap. */
    public static StoreOverride openTestStoreOverride(Path root) {
        Objects.requireNonNull(root, "root");
        synchronized (SlabRigHangingDirectExecutor.class) {
            if (testStoreOverrideOpen || !RUNS_BY_ID.isEmpty()) {
                throw new IllegalStateException("direct test store override requires an idle executor");
            }
            SlabRigHangingDirectStateStore replacement =
                    new SlabRigHangingDirectStateStore(root);
            if (replacement.root().equals(STORE.root())) {
                throw new IllegalArgumentException("test store root cannot equal production root");
            }
            SlabRigHangingDirectStateStore previous = STORE;
            STORE = replacement;
            testStoreOverrideOpen = true;
            return new StoreOverride(previous, replacement);
        }
    }

    /** Simulates the production SERVER_STARTED reconstruction boundary in the serialized integration test. */
    public static void restartForSerialGameTest(MinecraftServer server) {
        synchronized (SlabRigHangingDirectExecutor.class) {
            requireTestRestart(server);
            serverStarted(server);
        }
    }

    private static void requireTestRestart(MinecraftServer server) {
        if (!testStoreOverrideOpen || activeServer != server || stoppingServer != null) {
            throw new IllegalStateException(
                    "serial GameTest restart requires the active isolated test store/server");
        }
    }

    /** Installs the entity gate and all reconstruction/tick/load lifecycle callbacks exactly once. */
    public static void register() {
        synchronized (SlabRigHangingDirectExecutor.class) {
            if (registered) {
                return;
            }
            registered = true;
            SlabRigHangingDirectEntityGate.installHandler(ENTITY_HANDLER);
            ServerLifecycleEvents.SERVER_STARTED.register(SlabRigHangingDirectExecutor::serverStarted);
            ServerLifecycleEvents.SERVER_STOPPING.register(SlabRigHangingDirectExecutor::serverStopping);
            ServerLifecycleEvents.SERVER_STOPPED.register(SlabRigHangingDirectExecutor::serverStopped);
            ServerTickEvents.END_LEVEL_TICK.register(SlabRigHangingDirectExecutor::endLevelTick);
            ServerEntityEvents.ENTITY_LOAD.register(SlabRigHangingDirectExecutor::entityLoaded);
            ServerEntityEvents.ENTITY_UNLOAD.register(SlabRigHangingDirectExecutor::entityUnloaded);
        }
    }

    /** Starts the exact production address 6143/42/1; no caller-supplied address can widen it. */
    public static int start(CommandContext<CommandSourceStack> context, boolean force) {
        Objects.requireNonNull(context, "context");
        CommandSourceStack source = context.getSource();
        ActiveRun startedRun = null;
        try {
            register();
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = source.getLevel();
            requireExactPlayerLevel(player, level);
            if (SlabRigCommand.hasTrackedManifestInLevel(level.getServer(), level.dimension())) {
                throw new IllegalStateException("a volatile /slabrig manifest is active in this level; "
                        + "clear it with the legacy /slabrig clear command first");
            }
            ensureProcess(level.getServer());

            Path worldRoot = worldRoot(level.getServer());
            String worldKey = SlabRigHangingDirectStateStore.createWorldKey(worldRoot);
            SlabRigHangingDirectState.Owner owner = owner(worldKey, level, player.getUUID());
            SlabRigHangingDirectStateStore.Reconstruction prior = STORE.reconstruct(owner);
            SlabRigHangingDirectState.State previous = prior.latestOrNull();
            if (previous != null && previous.phase() != SlabRigHangingDirectState.Phase.CLEARED) {
                if (!force) {
                    throw new IllegalStateException("this player already has active direct state phase="
                            + previous.phase() + "; use status/resume/clear or literal force");
                }
                SlabRigHangingDirectState.State clearing = beginClear(level, previous);
                ActiveRun retiring = RUNS_BY_OWNER_LEVEL.get(
                        new OwnerLevelKey(level, previous.ownerKey()));
                if (retiring == null) {
                    retiring = rebuild(level, clearing, prior);
                    if (retiring == null) {
                        retiring = new ActiveRun(level.getServer(), level, clearing.owner(), null,
                                clearing, true, true);
                    }
                    installRun(retiring);
                }
                retiring.head = clearing;
                retiring.driver = null;
                retiring.clearRequested = true;
                source.sendSuccess(() -> Component.literal(statusLine(clearing)), false);
                return 1;
            }
            BlockPos groundAtFeet = player.blockPosition().below();
            BlockPos origin = groundAtFeet.east(6).north(12).immutable();
            Planning planning = plan(level, origin);
            validateReservation(level, planning.page());

            UUID nonce = UUID.randomUUID();
            String runId = SlabRigHangingDirectState.sha256(
                    SlabRigHangingDirectState.EXECUTION_CONTRACT + '\0' + worldKey + '\0'
                            + level.dimension().identifier() + '\0' + player.getUUID() + '\0'
                            + nonce + '\0' + planning.plan().planHash() + '\0' + origin.toShortString());
            SlabRigHangingDirectState.RunIdentity identity = new SlabRigHangingDirectState.RunIdentity(
                    runId, nonce, BuildStamp.GIT_SHA, planning.runtime().runtimeContentSha256(),
                    planning.runtime().minecraftVersion(), planning.catalog().catalogHash(),
                    planning.catalog().topologyCatalogHash(), planning.runtime().executionIdentity(),
                    planning.runtime().paintingRegistryHash(), planning.universe().universeHash(),
                    planning.plan().planHash(), planning.plan().semanticPageId(), ROUTE, TOPOLOGY, PAGE,
                    SlabAnchorAttachment.FROZEN_DY_ENABLED,
                    SlabRigHangingDirectState.Position.of(origin), FACING);
            List<SlabRigHangingDirectState.CaseState> cases = initialCases(planning.page());
            String plannedText = plannedArtifact(owner, identity, planning, force, cases);
            SlabRigHangingDirectStateStore.WrittenArtifact plannedArtifact =
                    writeAndReadArtifact(plannedText);
            SlabRigHangingDirectState.State planned = previous == null
                    ? SlabRigHangingDirectState.State.initial(owner, identity,
                    positions(planning.page().reservedCells()),
                    positions(planning.page().clearOwnedCells().stream()
                            .map(SlabRigHangingDirectFixture.AbsoluteCell::pos).toList()),
                    cases, plannedArtifact.hash(), "planned;force=" + force)
                    : SlabRigHangingDirectState.State.afterCleared(previous, identity,
                    positions(planning.page().reservedCells()),
                    positions(planning.page().clearOwnedCells().stream()
                            .map(SlabRigHangingDirectFixture.AbsoluteCell::pos).toList()),
                    cases, plannedArtifact.hash(), "planned;force=" + force);
            appendChecked(previous, planned);

            ActiveRun run = new ActiveRun(level.getServer(), level, owner, planning, planned,
                    force, false);
            run.driver = player;
            installRun(run);
            startedRun = run;
            source.sendSuccess(() -> Component.literal(statusLine(run.head)), false);
            return 1;
        } catch (Throwable failure) {
            if (startedRun != null) {
                try {
                    quarantine(startedRun, "start command failure: " + describe(failure));
                } catch (Throwable quarantineFailure) {
                    Slabbed.LOGGER.error("RIG-3B2B1 start quarantine append failed",
                            quarantineFailure);
                }
            }
            return fail(source, "direct start", failure);
        }
    }

    /** Reports the exact reconstructed owner ledger; it never creates world identity or state. */
    public static int status(CommandContext<CommandSourceStack> context) {
        Objects.requireNonNull(context, "context");
        CommandSourceStack source = context.getSource();
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = source.getLevel();
            requireExactPlayerLevel(player, level);
            SlabRigHangingDirectState.State state = reconstructOwner(level, player, false).latestOrNull();
            if (state == null) {
                source.sendSuccess(() -> Component.literal("No /slabrig hangs direct ledger for this player/world."), false);
                return 0;
            }
            source.sendSuccess(() -> Component.literal(statusLine(state)), false);
            return 1;
        } catch (Throwable failure) {
            return fail(source, "direct status", failure);
        }
    }

    /** Continues only replay-safe phases for the exact command owner and current runtime identity. */
    public static int resume(CommandContext<CommandSourceStack> context) {
        Objects.requireNonNull(context, "context");
        CommandSourceStack source = context.getSource();
        try {
            register();
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = source.getLevel();
            requireExactPlayerLevel(player, level);
            if (SlabRigCommand.hasTrackedManifestInLevel(level.getServer(), level.dimension())) {
                source.sendFailure(Component.literal("A volatile /slabrig manifest is active in this "
                        + "level; clear it with the legacy /slabrig clear command before direct resume."));
                return 0;
            }
            ensureProcess(level.getServer());
            SlabRigHangingDirectStateStore.Reconstruction reconstruction =
                    reconstructOwner(level, player, false);
            SlabRigHangingDirectState.State state = reconstruction.latestOrNull();
            if (state == null) {
                throw new IllegalStateException("no direct state to resume");
            }
            if (state.phase() == SlabRigHangingDirectState.Phase.CLEARED) {
                throw new IllegalStateException("latest direct state is already cleared; start a new run");
            }
            ActiveRun run = activeOrRebuild(level, state, reconstruction);
            if (run == null) {
                throw new IllegalStateException("stored run identity does not match this exact runtime; clear only");
            }
            if (run.head.phase() == SlabRigHangingDirectState.Phase.CASE_IN_FLIGHT) {
                quarantine(run, "resume refused replay of CASE_IN_FLIGHT");
                throw new IllegalStateException("in-flight case cannot be replayed; state quarantined for clear");
            }
            if (run.head.phase() == SlabRigHangingDirectState.Phase.QUARANTINED) {
                throw new IllegalStateException("run is quarantined; clear is the only allowed mutation");
            }
            if (isClearingPhase(run.head.phase())) {
                throw new IllegalStateException(
                        "run is already clearing; use clear to continue the exact durable cursor");
            }
            run.driver = null;
            if (isConstructionPhase(run.head.phase())) {
                preflightConstructionQuantum(run);
                run.driver = player;
            }
            source.sendSuccess(() -> Component.literal(statusLine(run.head)), false);
            return 1;
        } catch (Throwable failure) {
            quarantineOwnedIfPossible(source, failure);
            return fail(source, "direct resume", failure);
        }
    }

    /** Exact entity-first, attachment-second, cell-third clear from durable ownership only. */
    public static int clear(CommandContext<CommandSourceStack> context) {
        Objects.requireNonNull(context, "context");
        CommandSourceStack source = context.getSource();
        try {
            register();
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = source.getLevel();
            requireExactPlayerLevel(player, level);
            if (SlabRigCommand.hasTrackedManifestInLevel(level.getServer(), level.dimension())) {
                source.sendFailure(Component.literal("A volatile /slabrig manifest is active in this "
                        + "level; clear it with the legacy /slabrig clear command before direct clear."));
                return 0;
            }
            SlabRigHangingDirectStateStore.Reconstruction reconstruction =
                    reconstructOwner(level, player, false);
            SlabRigHangingDirectState.State state = reconstruction.latestOrNull();
            if (state == null) {
                throw new IllegalStateException("no direct state to clear");
            }
            if (state.phase() == SlabRigHangingDirectState.Phase.CLEARED) {
                source.sendSuccess(() -> Component.literal(statusLine(state)), false);
                return 1;
            }
            SlabRigHangingDirectState.State clearing = beginClear(level, state);
            ActiveRun run = RUNS_BY_OWNER_LEVEL.get(new OwnerLevelKey(level, state.ownerKey()));
            if (run == null) {
                run = rebuild(level, clearing, reconstruction);
                if (run == null) {
                    run = new ActiveRun(level.getServer(), level, clearing.owner(), null,
                            clearing, false, true);
                }
                installRun(run);
            }
            run.head = clearing;
            run.driver = null;
            run.clearRequested = true;
            source.sendSuccess(() -> Component.literal(statusLine(clearing)), false);
            return 1;
        } catch (Throwable failure) {
            return fail(source, "direct clear", failure);
        }
    }

    /** Advances exactly one existing durable construction boundary; caller invokes it once per tick. */
    private static void advanceConstructionOne(ActiveRun run, ServerPlayer player) throws Exception {
        if (run.clearOnly || run.planning == null) {
            throw new IllegalStateException("stale-runtime shell is clear-only");
        }
        if (run.head.phase() == SlabRigHangingDirectState.Phase.PLANNED
                || run.head.phase() == SlabRigHangingDirectState.Phase.FIXTURE_AUTHORING) {
            authorFixture(run, player);
        } else if (run.head.phase() == SlabRigHangingDirectState.Phase.FIXTURE_READY
                || run.head.phase() == SlabRigHangingDirectState.Phase.IMMEDIATE_PARTIAL) {
            executeNextCase(run, player);
        } else {
            throw new IllegalStateException("construction tick received non-construction phase "
                    + run.head.phase());
        }
    }

    /** Zero-mutation boundary recheck before every tick-sliced fixture/case mutation. */
    private static void preflightConstructionQuantum(ActiveRun run) throws IOException {
        if (run.clearOnly || run.planning == null || !isConstructionPhase(run.head.phase())) {
            throw new IOException("construction preflight received an inactive runtime/phase");
        }
        SlabRigHangingDirectState.State state = run.head;
        Map<SlabRigHangingDirectState.Position,
                SlabRigHangingDirectState.AttachmentOwnership> attachments = new HashMap<>();
        state.authoredAttachments().forEach(entry -> attachments.put(entry.pos(), entry));
        Set<SlabRigHangingDirectState.Position> authored = new HashSet<>();
        for (SlabRigHangingDirectState.CellOwnership cell : state.authoredCells()) {
            authored.add(cell.pos());
            SlabRigHangingDirectState.AttachmentOwnership attachment = attachments.get(cell.pos());
            if (attachment == null) {
                throw new IOException("construction boundary lacks attachment receipt " + cell.pos());
            }
            SlabRigHangingDirectEvidence.CellEvidence live =
                    SlabRigHangingDirectEvidence.cell(run.level, cell.pos().toBlockPos());
            if (!cell.fingerprint().equals(
                    SlabRigHangingDirectEvidence.cellIdentityFingerprint(live))
                    || !attachment.fingerprint().equals(
                    SlabRigHangingDirectEvidence.attachmentIdentityFingerprint(live))) {
                throw new IOException("construction boundary authored fingerprint drift " + cell.pos());
            }
        }
        for (SlabRigHangingDirectState.Position reserved : state.reservedCells()) {
            if (authored.contains(reserved)) {
                continue;
            }
            BlockPos pos = reserved.toBlockPos();
            SlabRigHangingDirectEvidence.CellEvidence live =
                    SlabRigHangingDirectEvidence.cell(run.level, pos);
            if (!run.level.getBlockState(pos).isAir()
                    || SlabRigHangingDirectEvidence.hasAttachmentEvidence(live)) {
                throw new IOException("construction boundary unowned reservation drift " + reserved);
            }
        }
        // Historical ownership is not live authority. A removed painting or vetoed item UUID
        // reused by any live entity inside the reservation must still trip the foreign barrier.
        Set<UUID> ownedUuids = state.activePaintingUuidSet();
        List<Entity> foreign = run.level.getEntities((Entity) null, reservedBounds(state),
                entity -> !entity.isRemoved() && !ownedUuids.contains(entity.getUUID()));
        if (!foreign.isEmpty()) {
            throw new IOException("construction boundary foreign entity in reserved volume "
                    + foreign.stream().map(entity -> entity.getUUID().toString()).sorted()
                    .findFirst().orElse("unknown"));
        }
        for (SlabRigHangingDirectState.EntityOwnership ownership : state.entities()) {
            if (ownership.role() != SlabRigHangingDirectState.EntityRole.PAINTING
                    || ownership.disposition()
                    == SlabRigHangingDirectState.EntityDisposition.REMOVED) {
                continue;
            }
            Entity live = run.level.getEntity(ownership.uuid());
            if (!(live instanceof Painting painting)
                    || !paintingFingerprintMatches(run.level, painting, ownership)) {
                throw new IOException("construction boundary owned painting drift "
                        + ownership.uuid());
            }
        }
    }

    private static boolean isConstructionPhase(SlabRigHangingDirectState.Phase phase) {
        return phase == SlabRigHangingDirectState.Phase.PLANNED
                || phase == SlabRigHangingDirectState.Phase.FIXTURE_AUTHORING
                || phase == SlabRigHangingDirectState.Phase.FIXTURE_READY
                || phase == SlabRigHangingDirectState.Phase.IMMEDIATE_PARTIAL;
    }

    private static boolean isClearingPhase(SlabRigHangingDirectState.Phase phase) {
        return phase == SlabRigHangingDirectState.Phase.CLEARING_ENTITIES
                || phase == SlabRigHangingDirectState.Phase.CLEARING_ATTACHMENTS
                || phase == SlabRigHangingDirectState.Phase.CLEARING_CELLS;
    }

    /**
     * Authors one complete planner case at a time. The current batch is rolled back on any failure;
     * only its complete exact evidence set becomes durable ownership.
     */
    private static void authorFixture(ActiveRun run, ServerPlayer player) throws Exception {
        SlabRigHangingDirectState.State state = run.head;
        if (state.phase() == SlabRigHangingDirectState.Phase.PLANNED) {
            state = state.successor(SlabRigHangingDirectState.Phase.FIXTURE_AUTHORING, 0,
                    state.authoredCells(), state.authoredAttachments(), state.cases(), state.entities(),
                    state.scheduler(), state.clear(), state.artifacts(), "fixture-authoring;batch=0");
            appendRun(run, state);
            return;
        }

        Map<SlabRigHangingDirectState.Position, SlabRigHangingDirectState.CellOwnership> confirmed =
                new HashMap<>();
        for (SlabRigHangingDirectState.CellOwnership ownership : run.head.authoredCells()) {
            confirmed.put(ownership.pos(), ownership);
            SlabRigHangingDirectEvidence.CellEvidence live =
                    SlabRigHangingDirectEvidence.cell(run.level, ownership.pos().toBlockPos());
            if (!ownership.fingerprint().equals(
                    SlabRigHangingDirectEvidence.cellIdentityFingerprint(live))) {
                quarantine(run, "confirmed fixture fingerprint changed at " + ownership.pos());
                throw new IllegalStateException("confirmed fixture changed; clear-only quarantine");
            }
        }

        for (int batchOrdinal = 0; batchOrdinal < run.planning.page().cases().size(); batchOrdinal++) {
            SlabRigHangingDirectFixture.AbsoluteCase fixtureCase =
                    run.planning.page().cases().get(batchOrdinal);
            List<SlabRigHangingDirectFixture.AbsoluteCell> batch =
                    SlabRigHangingDirectActions.fixtureCellsInAuthoringOrder(run.planning.page()).stream()
                            .filter(cell -> fixtureCase.authoredCells().stream()
                                    .anyMatch(expected -> expected.pos().equals(cell.pos())))
                            .toList();
            long durable = batch.stream().filter(cell -> confirmed.containsKey(
                    SlabRigHangingDirectState.Position.of(cell.pos()))).count();
            if (durable == batch.size()) {
                continue;
            }
            if (durable != 0) {
                quarantine(run, "partial durable fixture batch " + batchOrdinal);
                throw new IllegalStateException("fixture batch ownership is non-atomic");
            }

            List<BatchWrite> writes = new ArrayList<>();
            try {
                for (SlabRigHangingDirectFixture.AbsoluteCell cell : batch) {
                    BlockState before = run.level.getBlockState(cell.pos());
                    SlabRigHangingDirectEvidence.CellEvidence beforeEvidence =
                            SlabRigHangingDirectEvidence.cell(run.level, cell.pos());
                    if (!before.isAir()
                            || SlabRigHangingDirectEvidence.hasAttachmentEvidence(beforeEvidence)) {
                        throw new IllegalStateException("fixture batch refuses occupied/owned cell "
                                + cell.pos());
                    }
                    try {
                        SlabRigHangingDirectActions.FixtureCellWrite write =
                                SlabRigHangingDirectActions.authorFixtureCell(run.level, player, cell);
                        writes.add(new BatchWrite(cell.pos(), before, write.evidence()));
                    } catch (Throwable actionFailure) {
                        SlabRigHangingDirectEvidence.CellEvidence afterFailure =
                                SlabRigHangingDirectEvidence.cell(run.level, cell.pos());
                        if (before.isAir()
                                && !SlabRigHangingDirectEvidence.hasAttachmentEvidence(beforeEvidence)
                                && run.level.getBlockState(cell.pos()).equals(
                                SlabRigHangingDirectFixture.expectedState(
                                        cell.plan().stateRecipe()))) {
                            writes.add(new BatchWrite(cell.pos(), before, afterFailure));
                        }
                        throw actionFailure;
                    }
                }

                List<SlabRigHangingDirectState.CellOwnership> cells =
                        new ArrayList<>(run.head.authoredCells());
                List<SlabRigHangingDirectState.AttachmentOwnership> attachments =
                        new ArrayList<>(run.head.authoredAttachments());
                List<String> identityArtifacts = new ArrayList<>(writes.size() * 2);
                for (BatchWrite write : writes) {
                    SlabRigHangingDirectEvidence.CellEvidence evidence = write.evidence();
                    String cellFingerprint =
                            SlabRigHangingDirectEvidence.cellIdentityFingerprint(evidence);
                    String cellIdentity = SlabRigHangingDirectEvidence.cellIdentityCanonical(evidence);
                    requireIdentityHash(cellIdentity, cellFingerprint, "cell", evidence.pos());
                    identityArtifacts.add(cellIdentity);
                    cells.add(new SlabRigHangingDirectState.CellOwnership(
                            SlabRigHangingDirectState.Position.of(write.pos()), cellFingerprint));
                    String attachmentFingerprint =
                            SlabRigHangingDirectEvidence.attachmentIdentityFingerprint(evidence);
                    String attachmentIdentity =
                            SlabRigHangingDirectEvidence.attachmentIdentityCanonical(evidence);
                    requireIdentityHash(attachmentIdentity, attachmentFingerprint,
                            "attachment", evidence.pos());
                    attachments.add(new SlabRigHangingDirectState.AttachmentOwnership(
                            SlabRigHangingDirectState.Position.of(write.pos()),
                            attachmentFingerprint));
                }
                writeAndReadArtifacts(identityArtifacts);
                boolean last = batchOrdinal == run.planning.page().cases().size() - 1;
                SlabRigHangingDirectState.State next = run.head.successor(last
                                ? SlabRigHangingDirectState.Phase.FIXTURE_READY
                                : SlabRigHangingDirectState.Phase.FIXTURE_AUTHORING,
                        0, cells, attachments, run.head.cases(), run.head.entities(),
                        run.head.scheduler(), run.head.clear(), run.head.artifacts(),
                        "fixture-batch=" + batchOrdinal + ";cells=" + writes.size());
                appendRun(run, next);
                cells.forEach(cell -> confirmed.put(cell.pos(), cell));
                return;
            } catch (Throwable failure) {
                rollbackBatch(run.level, writes);
                quarantine(run, "fixture batch " + batchOrdinal + " failed: " + describe(failure));
                throw failure;
            }
        }
        quarantine(run, "fixture-authoring state has no incomplete deterministic batch");
        throw new IllegalStateException("fixture-authoring state has no incomplete batch");
    }

    private static void executeNextCase(ActiveRun run, ServerPlayer player) throws Exception {
        int ordinal = run.head.nextCaseOrdinal();
        if (ordinal < 0 || ordinal >= SlabRigHangingDirectState.CASE_COUNT) {
            throw new IllegalStateException("direct case cursor escaped 0..15: " + ordinal);
        }
        List<SlabRigHangingDirectState.CaseState> inFlightCases = new ArrayList<>(run.head.cases());
        SlabRigHangingDirectState.CaseState plannedCase = inFlightCases.get(ordinal);
        inFlightCases.set(ordinal, plannedCase.inFlight());
        SlabRigHangingDirectState.State inFlight = run.head.successor(
                SlabRigHangingDirectState.Phase.CASE_IN_FLIGHT, ordinal,
                run.head.authoredCells(), run.head.authoredAttachments(), inFlightCases,
                run.head.entities(), run.head.scheduler(), run.head.clear(), run.head.artifacts(),
                "case-in-flight=" + ordinal + ";attempt=" + plannedCase.attemptId());
        appendRun(run, inFlight);

        SlabRigHangingDirectFixture.AbsoluteCase fixtureCase = run.planning.page().cases().get(ordinal);
        SlabRigHangingDirectActions.PaintingAttempt attempt =
                SlabRigHangingDirectActions.placePainting(run.level, player, fixtureCase,
                        new SlabRigHangingDirectEntityGate.CaptureKey(
                                run.head.run().runId(), plannedCase.attemptId()));
        refreshHead(run);
        String observationText = observationArtifact(run.head, ordinal, attempt);
        SlabRigHangingDirectStateStore.WrittenArtifact observation =
                writeAndReadArtifact(observationText);

        if (run.head.phase() == SlabRigHangingDirectState.Phase.QUARANTINED) {
            return;
        }

        List<SlabRigHangingDirectState.CaseState> completed = new ArrayList<>(run.head.cases());
        SlabRigHangingDirectState.CaseState current = completed.get(ordinal);
        if (current.phase() != SlabRigHangingDirectState.CasePhase.IN_FLIGHT) {
            throw new IllegalStateException("entity gate changed case phase unexpectedly");
        }
        boolean serious = isSeriousAttemptFailure(attempt);
        if (serious) {
            SlabRigHangingDirectState.State quarantined = run.head.successor(
                    SlabRigHangingDirectState.Phase.QUARANTINED, ordinal,
                    run.head.authoredCells(), run.head.authoredAttachments(), run.head.cases(),
                    run.head.entities(), run.head.scheduler(), run.head.clear(), run.head.artifacts(),
                    "case-quarantined=" + ordinal + ";outcome=" + attempt.outcome()
                            + ";artifact=" + observation.hash());
            appendRun(run, quarantined);
            return;
        }
        SlabRigHangingDirectState.CaseOutcome caseOutcome =
                "VANILLA_REFUSAL".equals(attempt.outcome())
                        ? SlabRigHangingDirectState.CaseOutcome.VANILLA_REFUSAL
                        : SlabRigHangingDirectState.CaseOutcome.PLACED;
        completed.set(ordinal, current.immediate(caseOutcome, observation.hash()));
        SlabRigHangingDirectState.Phase nextPhase;
        SlabRigHangingDirectState.ArtifactLinks artifacts = run.head.artifacts();
        if (ordinal + 1 == SlabRigHangingDirectState.CASE_COUNT) {
            String immediateText = immediateArtifact(run.head, completed);
            SlabRigHangingDirectStateStore.WrittenArtifact immediate =
                    writeAndReadArtifact(immediateText);
            artifacts = new SlabRigHangingDirectState.ArtifactLinks(artifacts.planned(),
                    immediate.hash(), artifacts.finalArtifact(), artifacts.cleared());
            nextPhase = SlabRigHangingDirectState.Phase.IMMEDIATE;
        } else {
            nextPhase = SlabRigHangingDirectState.Phase.IMMEDIATE_PARTIAL;
        }
        SlabRigHangingDirectState.State next = run.head.successor(nextPhase, ordinal + 1,
                run.head.authoredCells(), run.head.authoredAttachments(), completed,
                run.head.entities(), run.head.scheduler(), run.head.clear(), artifacts,
                "case-observed=" + ordinal + ";outcome=" + attempt.outcome()
                        + ";artifact=" + observation.hash());
        appendRun(run, next);
    }

    private static void armScheduler(ActiveRun run, boolean reconstruction) throws Exception {
        ensureProcess(run.server);
        SlabRigHangingDirectState.State state = run.head;
        List<SlabRigHangingDirectState.TickCredit> credits = new ArrayList<>();
        if (reconstruction) {
            for (SlabRigHangingDirectState.TickCredit old : state.scheduler().credits()) {
                SlabRigHangingDirectState.EntityOwnership ownership = entity(state, old.paintingUuid());
                Entity live = run.level.getEntity(old.paintingUuid());
                if (ownership.disposition() == SlabRigHangingDirectState.EntityDisposition.REMOVED) {
                    credits.add(new SlabRigHangingDirectState.TickCredit(old.paintingUuid(), 0,
                            false, old.unloadResets(), -1));
                } else if (live instanceof Painting painting
                        && paintingFingerprintMatches(run.level, painting, ownership)) {
                    credits.add(new SlabRigHangingDirectState.TickCredit(old.paintingUuid(), 0,
                            true, old.unloadResets(), painting.tickCount));
                } else if (live == null) {
                    credits.add(new SlabRigHangingDirectState.TickCredit(old.paintingUuid(), 0,
                            false, old.unloadResets(), -1));
                } else {
                    throw new IllegalStateException("reconstructed painting fingerprint mismatch "
                            + old.paintingUuid());
                }
            }
        } else {
            for (SlabRigHangingDirectState.EntityOwnership ownership : state.entities()) {
                if (ownership.role() != SlabRigHangingDirectState.EntityRole.PAINTING
                        || ownership.disposition()
                        != SlabRigHangingDirectState.EntityDisposition.IN_WORLD) {
                    continue;
                }
                Entity live = run.level.getEntity(ownership.uuid());
                if (!(live instanceof Painting painting)
                        || !paintingFingerprintMatches(run.level, painting, ownership)) {
                    quarantine(run, "painting absent/mismatched while arming " + ownership.uuid());
                    throw new IllegalStateException("cannot arm unexplained painting absence");
                }
                credits.add(new SlabRigHangingDirectState.TickCredit(ownership.uuid(), 0,
                        true, 0, painting.tickCount));
            }
        }
        long generation = reconstruction ? state.scheduler().generation() + 1 : 1;
        SlabRigHangingDirectState.Scheduler scheduler =
                new SlabRigHangingDirectState.Scheduler(processEpoch, generation, credits);
        SlabRigHangingDirectState.State waiting = state.successor(
                SlabRigHangingDirectState.Phase.WAITING_DELAYED, state.nextCaseOrdinal(),
                state.authoredCells(), state.authoredAttachments(), state.cases(), state.entities(),
                scheduler, state.clear(), state.artifacts(), reconstruction
                        ? "delayed-rearmed;generation=" + generation
                        : "delayed-armed;generation=1");
        appendRun(run, waiting);
        run.driver = null;
    }

    private static void endLevelTick(ServerLevel level) {
        if (level.getServer() != activeServer || level.getServer() == stoppingServer) {
            return;
        }
        drainDeferredRemovals(level);
        List<ActiveRun> runs = RUNS_BY_ID.values().stream()
                .filter(run -> run.level == level)
                .toList();
        for (ActiveRun run : runs) {
            try {
                SlabRigHangingDirectState.Phase phase = run.head.phase();
                if (isClearingPhase(phase)) {
                    if (run.clearRequested) {
                        run.head = advanceClearOne(level, run.head);
                        if (run.head.phase() == SlabRigHangingDirectState.Phase.CLEARED) {
                            run.clearRequested = false;
                            uninstallRun(run);
                        }
                    }
                } else if (run.clearOnly) {
                    continue;
                } else if (phase == SlabRigHangingDirectState.Phase.WAITING_DELAYED) {
                    if (run.head.scheduler().processEpoch().equals(processEpoch)) {
                        tickDelayed(run);
                    } else {
                        armScheduler(run, true);
                    }
                } else if (phase == SlabRigHangingDirectState.Phase.IMMEDIATE) {
                    armScheduler(run, false);
                } else if (phase == SlabRigHangingDirectState.Phase.PLANNED
                        || phase == SlabRigHangingDirectState.Phase.FIXTURE_AUTHORING
                        || phase == SlabRigHangingDirectState.Phase.FIXTURE_READY
                        || phase == SlabRigHangingDirectState.Phase.IMMEDIATE_PARTIAL) {
                    ServerPlayer driver = exactDriver(run);
                    if (driver != null) {
                        preflightConstructionQuantum(run);
                        advanceConstructionOne(run, driver);
                    }
                }
            } catch (Throwable failure) {
                run.driver = null;
                Slabbed.LOGGER.error("RIG-3B2B1 lifecycle tick failed run={}",
                        run.head.run().runId(), failure);
                if (isClearingPhase(run.head.phase())) {
                    // Preserve the exact durable cursor and require an explicit clear command to
                    // resume; never retry a failed deletion quantum automatically.
                    run.clearRequested = false;
                    continue;
                }
                try {
                    quarantine(run, "lifecycle tick failed: " + describe(failure));
                } catch (Throwable quarantineFailure) {
                    Slabbed.LOGGER.error("RIG-3B2B1 quarantine append also failed run={}",
                            run.head.run().runId(), quarantineFailure);
                }
            }
        }
    }

    private static ServerPlayer exactDriver(ActiveRun run) {
        ServerPlayer player = run.driver;
        if (player == null || player.isRemoved() || player.hasDisconnected()
                || player.level() != run.level
                || !player.getUUID().equals(run.owner.playerUuid())) {
            run.driver = null;
            return null;
        }
        return player;
    }

    private static void tickDelayed(ActiveRun run) throws Exception {
        SlabRigHangingDirectState.State state = run.head;
        List<SlabRigHangingDirectState.TickCredit> updated = new ArrayList<>();
        boolean allReady = true;
        for (SlabRigHangingDirectState.TickCredit credit : state.scheduler().credits()) {
            SlabRigHangingDirectState.EntityOwnership ownership = entity(state, credit.paintingUuid());
            if (ownership.disposition() == SlabRigHangingDirectState.EntityDisposition.REMOVED) {
                updated.add(credit);
                continue;
            }
            if (!credit.loaded()) {
                BlockPos anchor = run.planning.page().cases().get(
                        ownership.caseOrdinal()).anchor();
                if (run.level.hasChunkAt(anchor)
                        && run.level.getEntity(credit.paintingUuid()) == null) {
                    recordUnexplainedAbsence(run, ownership,
                            "owned painting absent after its fixture chunk loaded");
                    return;
                }
                updated.add(credit);
                allReady = false;
                continue;
            }
            Entity live = run.level.getEntity(credit.paintingUuid());
            if (!(live instanceof Painting painting)) {
                recordUnexplainedAbsence(run, ownership,
                        "loaded painting disappeared without unload event");
                return;
            }
            if (!paintingFingerprintMatches(run.level, painting, ownership)) {
                throw new IllegalStateException("painting WYSIWYG identity changed during delayed gate "
                        + credit.paintingUuid());
            }
            long actual = painting.tickCount;
            if (actual < credit.lastObservedEntityTick()) {
                throw new IllegalStateException("painting tickCount regressed without unload "
                        + credit.paintingUuid());
            }
            long delta = actual - credit.lastObservedEntityTick();
            int observed = Math.toIntExact(Math.addExact(
                    (long) credit.observedEntityTicks(), delta));
            SlabRigHangingDirectState.TickCredit next = new SlabRigHangingDirectState.TickCredit(
                    credit.paintingUuid(), observed, true, credit.unloadResets(), actual);
            updated.add(next);
            if (observed < SlabRigHangingDirectState.REQUIRED_ENTITY_TICKS) {
                allReady = false;
            }
        }
        if (!allReady) {
            return;
        }
        SlabRigHangingDirectState.Scheduler scheduler = new SlabRigHangingDirectState.Scheduler(
                state.scheduler().processEpoch(), state.scheduler().generation(), updated);
        if (!finalGateSatisfied(state, scheduler)) {
            throw new IllegalStateException("local final gate disagrees with exact survivor credits");
        }
        String finalText = finalArtifact(run, state, scheduler);
        SlabRigHangingDirectStateStore.WrittenArtifact finalArtifact =
                writeAndReadArtifact(finalText);
        SlabRigHangingDirectState.ArtifactLinks artifacts =
                new SlabRigHangingDirectState.ArtifactLinks(state.artifacts().planned(),
                        state.artifacts().immediate(), finalArtifact.hash(),
                        state.artifacts().cleared());
        SlabRigHangingDirectState.State next = state.successor(
                SlabRigHangingDirectState.Phase.FINAL, state.nextCaseOrdinal(),
                state.authoredCells(), state.authoredAttachments(), state.cases(), state.entities(),
                scheduler, state.clear(), artifacts, "final;artifact=" + finalArtifact.hash());
        appendRun(run, next);
    }

    private static void recordUnexplainedAbsence(
            ActiveRun run, SlabRigHangingDirectState.EntityOwnership ownership,
            String boundary) throws Exception {
        SlabRigHangingDirectStateStore.WrittenArtifact artifact = writeAndReadArtifact(
                unexplainedRemovalArtifact(run.head, ownership, boundary));
        SlabRigHangingDirectState.EntityOwnership removed = ownership.removed(
                SlabRigHangingDirectState.RemovalCause.UNEXPLAINED, artifact.hash());
        List<SlabRigHangingDirectState.EntityOwnership> entities =
                replaceEntity(run.head.entities(), removed);
        appendRun(run, run.head.successor(SlabRigHangingDirectState.Phase.QUARANTINED,
                run.head.nextCaseOrdinal(), run.head.authoredCells(),
                run.head.authoredAttachments(), run.head.cases(), entities,
                run.head.scheduler(), run.head.clear(), run.head.artifacts(),
                "unexplained-removal=" + ownership.uuid() + ";artifact=" + artifact.hash()));
        RUNS_BY_ENTITY.remove(new EntityLevelKey(run.level, ownership.uuid()));
    }

    private static boolean finalGateSatisfied(SlabRigHangingDirectState.State state,
                                              SlabRigHangingDirectState.Scheduler scheduler) {
        Map<UUID, SlabRigHangingDirectState.TickCredit> credits = new HashMap<>();
        scheduler.credits().forEach(credit -> credits.put(credit.paintingUuid(), credit));
        for (SlabRigHangingDirectState.EntityOwnership ownership : state.entities()) {
            if (ownership.role() != SlabRigHangingDirectState.EntityRole.PAINTING
                    || ownership.disposition() == SlabRigHangingDirectState.EntityDisposition.REMOVED) {
                continue;
            }
            SlabRigHangingDirectState.TickCredit credit = credits.get(ownership.uuid());
            if (ownership.disposition() != SlabRigHangingDirectState.EntityDisposition.IN_WORLD
                    || credit == null || !credit.loaded()
                    || credit.observedEntityTicks()
                    < SlabRigHangingDirectState.REQUIRED_ENTITY_TICKS) {
                return false;
            }
        }
        return true;
    }

    private static void serverStarted(MinecraftServer server) {
        clearProcessMaps();
        activeServer = server;
        stoppingServer = null;
        processEpoch = UUID.randomUUID().toString();
        try {
            String worldKey = SlabRigHangingDirectStateStore.readWorldKey(worldRoot(server));
            Map<String, ServerLevel> levels = new HashMap<>();
            server.getAllLevels().forEach(level ->
                    levels.put(level.dimension().identifier().toString(), level));
            for (SlabRigHangingDirectStateStore.Reconstruction reconstruction : STORE.reconstructAll()) {
                SlabRigHangingDirectState.State state = reconstruction.latestOrNull();
                if (state == null || state.phase() == SlabRigHangingDirectState.Phase.CLEARED
                        || !state.owner().worldKey().equals(worldKey)) {
                    continue;
                }
                ServerLevel level = levels.get(state.owner().dimension());
                if (level == null) {
                    continue;
                }
                ActiveRun run = rebuild(level, state, reconstruction);
                if (run == null) {
                    Slabbed.LOGGER.warn("RIG-3B2B1 active state is clear-only after runtime mismatch owner={}",
                            state.ownerKey());
                    run = new ActiveRun(level.getServer(), level, state.owner(), null, state,
                            false, true);
                }
                installRun(run);
                if (run.clearOnly) {
                    continue;
                } else if (run.head.phase() == SlabRigHangingDirectState.Phase.CASE_IN_FLIGHT) {
                    quarantine(run, "process reconstruction refused CASE_IN_FLIGHT replay");
                } else if (run.head.phase() == SlabRigHangingDirectState.Phase.IMMEDIATE) {
                    armScheduler(run, false);
                } else if (run.head.phase()
                        == SlabRigHangingDirectState.Phase.WAITING_DELAYED) {
                    armScheduler(run, true);
                }
            }
        } catch (IOException missingOrCorrupt) {
            Slabbed.LOGGER.info("RIG-3B2B1 startup reconstruction unavailable: {}",
                    missingOrCorrupt.getMessage());
        } catch (Throwable failure) {
            Slabbed.LOGGER.error("RIG-3B2B1 startup reconstruction failed", failure);
        }
    }

    private static void serverStopping(MinecraftServer server) {
        if (server == activeServer) {
            stoppingServer = server;
            // Shutdown chunk eviction is not gameplay unload evidence. Durable WAITING state is
            // intentionally re-armed from exact UUIDs and fresh baselines on the next process.
            clearProcessMaps();
        }
    }

    private static void serverStopped(MinecraftServer server) {
        if (server == activeServer || server == stoppingServer) {
            clearProcessMaps();
            activeServer = null;
            stoppingServer = null;
            processEpoch = SlabRigHangingDirectState.NO_VALUE;
        }
    }

    private static void entityLoaded(Entity loaded, ServerLevel level) {
        if (level.getServer() == stoppingServer) {
            return;
        }
        ActiveRun run = RUNS_BY_ENTITY.get(new EntityLevelKey(level, loaded.getUUID()));
        if (run == null || !(loaded instanceof Painting painting)
                || run.clearOnly
                || run.head.phase() != SlabRigHangingDirectState.Phase.WAITING_DELAYED) {
            return;
        }
        try {
            SlabRigHangingDirectState.EntityOwnership ownership = entity(run.head, loaded.getUUID());
            if (!paintingFingerprintMatches(level, painting, ownership)) {
                throw new IllegalStateException("reloaded painting fingerprint mismatch "
                        + loaded.getUUID());
            }
            List<SlabRigHangingDirectState.TickCredit> credits = new ArrayList<>();
            boolean changed = false;
            for (SlabRigHangingDirectState.TickCredit credit : run.head.scheduler().credits()) {
                if (credit.paintingUuid().equals(loaded.getUUID()) && !credit.loaded()) {
                    credits.add(new SlabRigHangingDirectState.TickCredit(credit.paintingUuid(), 0,
                            true, credit.unloadResets(), painting.tickCount));
                    changed = true;
                } else {
                    credits.add(credit);
                }
            }
            if (changed) {
                SlabRigHangingDirectState.Scheduler scheduler =
                        new SlabRigHangingDirectState.Scheduler(run.head.scheduler().processEpoch(),
                                run.head.scheduler().generation(), credits);
                appendRun(run, run.head.successor(run.head.phase(), run.head.nextCaseOrdinal(),
                        run.head.authoredCells(), run.head.authoredAttachments(), run.head.cases(),
                        run.head.entities(), scheduler, run.head.clear(), run.head.artifacts(),
                        "painting-reloaded=" + loaded.getUUID()));
            }
        } catch (Throwable failure) {
            lifecycleFailure(run, "entity reload", failure);
        }
    }

    private static void entityUnloaded(Entity unloaded, ServerLevel level) {
        if (level.getServer() == stoppingServer) {
            return;
        }
        EntityLevelKey key = new EntityLevelKey(level, unloaded.getUUID());
        if (CLEARING_ENTITIES.contains(key)) {
            return;
        }
        ActiveRun run = RUNS_BY_ENTITY.get(key);
        if (run == null || !(unloaded instanceof Painting painting)) {
            return;
        }
        try {
            refreshHead(run);
            if (PENDING_REMOVALS.containsKey(key)) {
                // The exact DROP preclaim will atomically publish source removal + vetoed item.
                return;
            }
            SlabRigHangingDirectState.EntityOwnership ownership = entity(run.head, unloaded.getUUID());
            if (ownership.disposition() == SlabRigHangingDirectState.EntityDisposition.REMOVED) {
                RUNS_BY_ENTITY.remove(key);
                return;
            }
            String reason = String.valueOf(unloaded.getRemovalReason());
            boolean transientUnload = unloaded.getRemovalReason() == null
                    || "UNLOADED_TO_CHUNK".equals(reason);
            if (transientUnload) {
                if (run.clearOnly) {
                    return;
                }
                if (run.head.phase() == SlabRigHangingDirectState.Phase.WAITING_DELAYED) {
                    resetUnloadedCredit(run, unloaded.getUUID());
                } else if (run.head.phase().ordinal()
                        < SlabRigHangingDirectState.Phase.WAITING_DELAYED.ordinal()) {
                    quarantine(run, "painting unloaded before delayed scheduler "
                            + unloaded.getUUID());
                }
                return;
            }
            SlabRigHangingDirectEvidence.PaintingEvidence evidence =
                    SlabRigHangingDirectEvidence.painting(level, painting);
            if ("DISCARDED".equals(reason)) {
                DeferredRemoval deferred = new DeferredRemoval(run, unloaded.getUUID(), painting,
                        evidence, reason);
                DeferredRemoval collision = DEFERRED_REMOVALS.putIfAbsent(key, deferred);
                if (collision != null && collision.run() != run) {
                    throw new IllegalStateException("discarded painting removal collided across runs "
                            + unloaded.getUUID());
                }
                // Vanilla calls Painting.dropItem immediately after discard. Its exact spawn wrapper
                // must get the first opportunity to atomically bind source removal plus item UUID.
                return;
            }
            SlabRigHangingDirectState.RemovalCause cause = run.clearOnly
                    ? SlabRigHangingDirectState.RemovalCause.UNEXPLAINED
                    : evidence.survives()
                    ? SlabRigHangingDirectState.RemovalCause.INTERFERENCE
                    : SlabRigHangingDirectState.RemovalCause.SUPPORT_LOSS_NO_DROP;
            SlabRigHangingDirectStateStore.WrittenArtifact artifact = writeAndReadArtifact(
                    removalArtifact(run.head, ownership, cause, evidence, level, painting,
                            "entity-unload;reason=" + reason));
            SlabRigHangingDirectState.EntityOwnership removed =
                    ownership.removed(cause, artifact.hash());
            List<SlabRigHangingDirectState.EntityOwnership> entities =
                    replaceEntity(run.head.entities(), removed);
            SlabRigHangingDirectState.Phase phase = cause
                    == SlabRigHangingDirectState.RemovalCause.SUPPORT_LOSS_NO_DROP
                    ? run.head.phase() : SlabRigHangingDirectState.Phase.QUARANTINED;
            appendRun(run, run.head.successor(phase, run.head.nextCaseOrdinal(),
                    run.head.authoredCells(), run.head.authoredAttachments(), run.head.cases(),
                    entities, run.head.scheduler(), run.head.clear(), run.head.artifacts(),
                    "painting-removed=" + unloaded.getUUID() + ";cause=" + cause
                            + ";artifact=" + artifact.hash()));
            RUNS_BY_ENTITY.remove(key);
        } catch (Throwable failure) {
            lifecycleFailure(run, "entity unload", failure);
        }
    }

    private static void drainDeferredRemovals(ServerLevel level) {
        List<Map.Entry<EntityLevelKey, DeferredRemoval>> deferred = DEFERRED_REMOVALS.entrySet()
                .stream().filter(entry -> entry.getKey().level() == level).toList();
        for (Map.Entry<EntityLevelKey, DeferredRemoval> entry : deferred) {
            EntityLevelKey key = entry.getKey();
            DeferredRemoval removal = entry.getValue();
            if (PENDING_REMOVALS.containsKey(key)) {
                continue;
            }
            ActiveRun run = removal.run();
            try {
                refreshHead(run);
                SlabRigHangingDirectState.EntityOwnership ownership =
                        entity(run.head, removal.sourceUuid());
                if (ownership.disposition() == SlabRigHangingDirectState.EntityDisposition.REMOVED) {
                    continue;
                }
                boolean dropsDisabled = !level.getGameRules().get(GameRules.ENTITY_DROPS);
                boolean typedNoDrop = dropsDisabled && !removal.evidence().survives();
                SlabRigHangingDirectState.RemovalCause cause = typedNoDrop
                        ? SlabRigHangingDirectState.RemovalCause.SUPPORT_LOSS_NO_DROP
                        : removal.evidence().survives()
                        ? SlabRigHangingDirectState.RemovalCause.INTERFERENCE
                        : SlabRigHangingDirectState.RemovalCause.UNEXPLAINED;
                SlabRigHangingDirectStateStore.WrittenArtifact artifact = writeAndReadArtifact(
                        removalArtifact(run.head, ownership, cause, removal.evidence(), level,
                                removal.painting(),
                                "deferred-entity-unload;reason=" + removal.reason()));
                SlabRigHangingDirectState.EntityOwnership removed =
                        ownership.removed(cause, artifact.hash());
                List<SlabRigHangingDirectState.EntityOwnership> entities =
                        replaceEntity(run.head.entities(), removed);
                SlabRigHangingDirectState.Phase phase = typedNoDrop
                        ? run.head.phase() : SlabRigHangingDirectState.Phase.QUARANTINED;
                appendRun(run, run.head.successor(phase, run.head.nextCaseOrdinal(),
                        run.head.authoredCells(), run.head.authoredAttachments(), run.head.cases(),
                        entities, run.head.scheduler(), run.head.clear(), run.head.artifacts(),
                        "deferred-painting-removed=" + removal.sourceUuid() + ";cause=" + cause
                                + ";artifact=" + artifact.hash()));
            } catch (Throwable failure) {
                lifecycleFailure(run, "deferred entity unload", failure);
            } finally {
                DEFERRED_REMOVALS.remove(key, removal);
                RUNS_BY_ENTITY.remove(key, run);
            }
        }
    }

    private static void resetUnloadedCredit(ActiveRun run, UUID uuid) throws Exception {
        List<SlabRigHangingDirectState.TickCredit> credits = new ArrayList<>();
        boolean found = false;
        for (SlabRigHangingDirectState.TickCredit credit : run.head.scheduler().credits()) {
            if (credit.paintingUuid().equals(uuid)) {
                if (!credit.loaded()) {
                    return;
                }
                credits.add(new SlabRigHangingDirectState.TickCredit(uuid, 0, false,
                        credit.unloadResets() + 1, -1));
                found = true;
            } else {
                credits.add(credit);
            }
        }
        if (!found) {
            throw new IllegalStateException("unload UUID lacks scheduler credit " + uuid);
        }
        SlabRigHangingDirectState.Scheduler scheduler = new SlabRigHangingDirectState.Scheduler(
                run.head.scheduler().processEpoch(), run.head.scheduler().generation(), credits);
        appendRun(run, run.head.successor(run.head.phase(), run.head.nextCaseOrdinal(),
                run.head.authoredCells(), run.head.authoredAttachments(), run.head.cases(),
                run.head.entities(), scheduler, run.head.clear(), run.head.artifacts(),
                "painting-unloaded=" + uuid));
    }

    /** Detaches any construction driver before preflight, then publishes the exact clear lane. */
    private static SlabRigHangingDirectState.State beginClear(
            ServerLevel level, SlabRigHangingDirectState.State start) throws Exception {
        ActiveRun active = RUNS_BY_OWNER_LEVEL.get(new OwnerLevelKey(level, start.ownerKey()));
        if (active != null) {
            active.driver = null;
            active.clearRequested = false;
        }
        if (start.phase() == SlabRigHangingDirectState.Phase.CLEARED
                || isClearingPhase(start.phase())) {
            return start;
        }
        preflightEntireClear(level, start);
        SlabRigHangingDirectState.ClearProgress clear =
                SlabRigHangingDirectState.ClearProgress.begin(start);
        SlabRigHangingDirectState.State clearing = start.successor(
                SlabRigHangingDirectState.Phase.CLEARING_ENTITIES,
                start.nextCaseOrdinal(), start.authoredCells(), start.authoredAttachments(),
                start.cases(), start.entities(), start.scheduler(), clear, start.artifacts(),
                "clear-entities-begin");
        appendChecked(start, clearing);
        return clearing;
    }

    /** Advances one existing entity/attachment/cell clear batch or one phase boundary. */
    private static SlabRigHangingDirectState.State advanceClearOne(
            ServerLevel level, SlabRigHangingDirectState.State state) throws Exception {
        if (state.phase() == SlabRigHangingDirectState.Phase.CLEARING_ENTITIES) {
            if (state.clear().entityCursor() < state.clear().requestedEntities().size()) {
                return clearEntityBatch(level, state);
            }
            if (!state.clear().entitiesCompleteWithoutRefusal()) {
                throw new IllegalStateException(
                        "entity clear refused exact fingerprint; state remains blocked");
            }
            SlabRigHangingDirectState.State next = state.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_ATTACHMENTS,
                    state.nextCaseOrdinal(), state.authoredCells(), state.authoredAttachments(),
                    state.cases(), state.entities(), state.scheduler(), state.clear(),
                    state.artifacts(), "clear-attachments-begin");
            appendChecked(state, next);
            return next;
        }
        if (state.phase() == SlabRigHangingDirectState.Phase.CLEARING_ATTACHMENTS) {
            if (state.clear().attachmentCursor() < state.clear().requestedAttachments().size()) {
                return clearAttachmentBatch(level, state);
            }
            if (!state.clear().attachmentsCompleteWithoutRefusal()) {
                throw new IllegalStateException(
                        "attachment clear refused exact fingerprint; state remains blocked");
            }
            SlabRigHangingDirectState.State next = state.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_CELLS,
                    state.nextCaseOrdinal(), state.authoredCells(), state.authoredAttachments(),
                    state.cases(), state.entities(), state.scheduler(), state.clear(),
                    state.artifacts(), "clear-cells-begin");
            appendChecked(state, next);
            return next;
        }
        if (state.phase() != SlabRigHangingDirectState.Phase.CLEARING_CELLS) {
            throw new IllegalStateException("clear quantum received non-clearing phase " + state.phase());
        }
        if (state.clear().cellCursor() < state.clear().requestedCells().size()) {
            return clearCellBatch(level, state);
        }
        if (!state.clear().cellsCompleteWithoutRefusal()) {
            throw new IllegalStateException("cell clear refused exact fingerprint; state remains blocked");
        }
        String clearedText = clearedArtifact(state);
        SlabRigHangingDirectStateStore.WrittenArtifact cleared = writeAndReadArtifact(clearedText);
        SlabRigHangingDirectState.ArtifactLinks artifacts =
                new SlabRigHangingDirectState.ArtifactLinks(state.artifacts().planned(),
                        state.artifacts().immediate(), state.artifacts().finalArtifact(), cleared.hash());
        SlabRigHangingDirectState.State terminal = state.successor(
                SlabRigHangingDirectState.Phase.CLEARED, state.nextCaseOrdinal(),
                state.authoredCells(), state.authoredAttachments(), state.cases(), state.entities(),
                state.scheduler(), state.clear(), artifacts, "cleared;artifact=" + cleared.hash());
        appendChecked(state, terminal);
        return terminal;
    }

    /** Zero-mutation sentinel gate: one mismatch refuses before any clear phase or world write. */
    private static void preflightEntireClear(ServerLevel level,
                                             SlabRigHangingDirectState.State state) throws IOException {
        Set<UUID> ownedUuids = state.entityUuidSet();
        List<Entity> foreign = level.getEntities((Entity) null, reservedBounds(state),
                entity -> !entity.isRemoved() && !ownedUuids.contains(entity.getUUID()));
        if (!foreign.isEmpty()) {
            throw new IOException("clear preflight found foreign entity in reserved volume "
                    + foreign.stream().map(entity -> entity.getUUID().toString()).sorted()
                    .findFirst().orElse("unknown"));
        }
        for (SlabRigHangingDirectState.EntityOwnership ownership : state.entities()) {
            Entity live = level.getEntity(ownership.uuid());
            if (live != null && !entityFingerprintMatches(level, live, ownership)) {
                throw new IOException("clear preflight entity fingerprint mismatch "
                        + ownership.uuid());
            }
        }
        Map<SlabRigHangingDirectState.Position, SlabRigHangingDirectState.AttachmentOwnership>
                attachments = new HashMap<>();
        state.authoredAttachments().forEach(entry -> attachments.put(entry.pos(), entry));
        for (SlabRigHangingDirectState.CellOwnership cell : state.authoredCells()) {
            SlabRigHangingDirectState.AttachmentOwnership attachment = attachments.get(cell.pos());
            if (attachment == null) {
                throw new IOException("clear preflight missing attachment receipt " + cell.pos());
            }
            SlabRigHangingDirectEvidence.CellEvidence live =
                    SlabRigHangingDirectEvidence.cell(level, cell.pos().toBlockPos());
            boolean whollyAbsent = level.getBlockState(cell.pos().toBlockPos()).isAir()
                    && !SlabAnchorAttachment.hasStoredAttachmentEvidence(
                    level, cell.pos().toBlockPos());
            if (!whollyAbsent && (!cell.fingerprint().equals(
                    SlabRigHangingDirectEvidence.cellIdentityFingerprint(live))
                    || !attachment.fingerprint().equals(
                    SlabRigHangingDirectEvidence.attachmentIdentityFingerprint(live)))) {
                throw new IOException("clear preflight cell/attachment fingerprint mismatch "
                        + cell.pos());
            }
        }
    }

    private static AABB reservedBounds(SlabRigHangingDirectState.State state) {
        if (state.reservedCells().isEmpty()) {
            throw new IllegalArgumentException("direct state has no reserved cells");
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (SlabRigHangingDirectState.Position pos : state.reservedCells()) {
            minX = Math.min(minX, pos.x());
            minY = Math.min(minY, pos.y());
            minZ = Math.min(minZ, pos.z());
            maxX = Math.max(maxX, pos.x());
            maxY = Math.max(maxY, pos.y());
            maxZ = Math.max(maxZ, pos.z());
        }
        return new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
    }

    private static SlabRigHangingDirectState.State clearEntityBatch(ServerLevel level,
                                                                    SlabRigHangingDirectState.State state)
            throws Exception {
        SlabRigHangingDirectState.ClearProgress progress = state.clear();
        int first = progress.entityCursor();
        int limit = Math.min(progress.requestedEntities().size(), first + CLEAR_BATCH_SIZE);
        while (progress.entityCursor() < limit) {
            UUID uuid = progress.requestedEntities().get(progress.entityCursor());
            SlabRigHangingDirectState.EntityOwnership ownership = entity(state, uuid);
            Entity live = level.getEntity(uuid);
            SlabRigHangingDirectState.ClearOutcome outcome;
            if (live == null) {
                outcome = SlabRigHangingDirectState.ClearOutcome.ALREADY_ABSENT;
            } else if (!entityFingerprintMatches(level, live, ownership)) {
                outcome = SlabRigHangingDirectState.ClearOutcome.REFUSED_FINGERPRINT;
            } else {
                EntityLevelKey key = new EntityLevelKey(level, uuid);
                CLEARING_ENTITIES.add(key);
                try {
                    live.discard();
                } finally {
                    CLEARING_ENTITIES.remove(key);
                }
                outcome = live.isRemoved() ? SlabRigHangingDirectState.ClearOutcome.REMOVED
                        : SlabRigHangingDirectState.ClearOutcome.REFUSED_FINGERPRINT;
            }
            progress = entityClearReceipt(progress, uuid, outcome);
        }
        SlabRigHangingDirectState.State next = state.successor(state.phase(),
                state.nextCaseOrdinal(), state.authoredCells(), state.authoredAttachments(),
                state.cases(), state.entities(), state.scheduler(), progress, state.artifacts(),
                "clear-entity-batch=" + first + ".." + (progress.entityCursor() - 1));
        appendChecked(state, next);
        return next;
    }

    private static SlabRigHangingDirectState.State clearAttachmentBatch(
            ServerLevel level, SlabRigHangingDirectState.State state) throws Exception {
        SlabRigHangingDirectState.ClearProgress progress = state.clear();
        int first = progress.attachmentCursor();
        int limit = Math.min(progress.requestedAttachments().size(), first + CLEAR_BATCH_SIZE);
        while (progress.attachmentCursor() < limit) {
            SlabRigHangingDirectState.Position position =
                    progress.requestedAttachments().get(progress.attachmentCursor());
            SlabRigHangingDirectState.AttachmentOwnership ownership =
                    state.authoredAttachments().stream()
                    .filter(entry -> entry.pos().equals(position)).findFirst().orElseThrow();
            SlabRigHangingDirectEvidence.CellEvidence live =
                    SlabRigHangingDirectEvidence.cell(level, position.toBlockPos());
            boolean present = SlabAnchorAttachment.hasStoredAttachmentEvidence(
                    level, position.toBlockPos());
            SlabRigHangingDirectState.ClearOutcome outcome;
            if (!present) {
                outcome = SlabRigHangingDirectState.ClearOutcome.ALREADY_ABSENT;
            } else {
                SlabRigHangingDirectState.CellOwnership cell = state.authoredCells().stream()
                        .filter(entry -> entry.pos().equals(position)).findFirst().orElseThrow();
                boolean exactAttachment = ownership.fingerprint().equals(
                        SlabRigHangingDirectEvidence.attachmentIdentityFingerprint(live));
                // Preflight proved the complete page before mutation. Earlier owned attachment
                // removals may legitimately change derived live-dy for neighboring cells, so the
                // in-lane guard retains exact block/BE + current attachment identity but ignores
                // only that derived live read.
                boolean exactCell = cellStaticPayloadMatchesOriginal(live, cell.fingerprint());
                if (!exactAttachment || !exactCell) {
                    outcome = SlabRigHangingDirectState.ClearOutcome.REFUSED_FINGERPRINT;
                } else {
                    SlabAnchorAttachment.removeAnchor(level, position.toBlockPos());
                    outcome = SlabAnchorAttachment.hasStoredAttachmentEvidence(
                            level, position.toBlockPos())
                            ? SlabRigHangingDirectState.ClearOutcome.REFUSED_FINGERPRINT
                            : SlabRigHangingDirectState.ClearOutcome.REMOVED;
                }
            }
            progress = attachmentClearReceipt(progress, position, outcome);
        }
        SlabRigHangingDirectState.State next = state.successor(state.phase(),
                state.nextCaseOrdinal(), state.authoredCells(), state.authoredAttachments(),
                state.cases(), state.entities(), state.scheduler(), progress, state.artifacts(),
                "clear-attachment-batch=" + first + ".." + (progress.attachmentCursor() - 1));
        appendChecked(state, next);
        return next;
    }

    private static SlabRigHangingDirectState.State clearCellBatch(
            ServerLevel level, SlabRigHangingDirectState.State state) throws Exception {
        SlabRigHangingDirectState.ClearProgress progress = state.clear();
        int first = progress.cellCursor();
        int limit = Math.min(progress.requestedCells().size(), first + CLEAR_BATCH_SIZE);
        while (progress.cellCursor() < limit) {
            SlabRigHangingDirectState.Position position =
                    progress.requestedCells().get(progress.cellCursor());
            SlabRigHangingDirectState.CellOwnership ownership = state.authoredCells().stream()
                    .filter(entry -> entry.pos().equals(position)).findFirst().orElseThrow();
            SlabRigHangingDirectEvidence.CellEvidence live =
                    SlabRigHangingDirectEvidence.cell(level, position.toBlockPos());
            SlabRigHangingDirectState.ClearOutcome outcome;
            if (level.getBlockState(position.toBlockPos()).isAir()
                    && !SlabAnchorAttachment.hasStoredAttachmentEvidence(
                    level, position.toBlockPos())) {
                outcome = SlabRigHangingDirectState.ClearOutcome.ALREADY_ABSENT;
            } else if (!cellStaticPayloadMatchesOriginal(live, ownership.fingerprint())
                    || SlabAnchorAttachment.hasStoredAttachmentEvidence(
                    level, position.toBlockPos())) {
                outcome = SlabRigHangingDirectState.ClearOutcome.REFUSED_FINGERPRINT;
            } else {
                level.setBlock(position.toBlockPos(), Blocks.AIR.defaultBlockState(), CLEAR_FLAGS);
                outcome = level.getBlockState(position.toBlockPos()).isAir()
                        && !SlabAnchorAttachment.hasStoredAttachmentEvidence(
                        level, position.toBlockPos())
                        ? SlabRigHangingDirectState.ClearOutcome.REMOVED
                        : SlabRigHangingDirectState.ClearOutcome.REFUSED_FINGERPRINT;
            }
            progress = cellClearReceipt(progress, position, outcome);
        }
        SlabRigHangingDirectState.State next = state.successor(state.phase(),
                state.nextCaseOrdinal(), state.authoredCells(), state.authoredAttachments(),
                state.cases(), state.entities(), state.scheduler(), progress, state.artifacts(),
                "clear-cell-batch=" + first + ".." + (progress.cellCursor() - 1));
        appendChecked(state, next);
        return next;
    }

    private static final class ProductionEntityHandler
            implements SlabRigHangingDirectEntityGate.Handler {
        @Override
        public boolean beginFailure(Painting source, ServerLevel level, Throwable failure) {
            ActiveRun run = RUNS_BY_ENTITY.get(new EntityLevelKey(level, source.getUUID()));
            if (run == null) {
                return false;
            }
            try {
                EntityLevelKey sourceKey = new EntityLevelKey(level, source.getUUID());
                PENDING_REMOVALS.remove(sourceKey);
                DEFERRED_REMOVALS.remove(sourceKey);
                quarantineHandlerFailure(run, source, level,
                        "begin painting drop callback failure", failure);
            } catch (Throwable quarantineFailure) {
                Slabbed.LOGGER.error("RIG-3B2B1 beginFailure quarantine failed run={}",
                        run.head.run().runId(), quarantineFailure);
            }
            return true;
        }

        @Override
        public Optional<SlabRigHangingDirectEntityGate.CaptureKey> beginPaintingDrop(
                Painting source, ServerLevel level) throws Exception {
            ActiveRun run = RUNS_BY_ENTITY.get(new EntityLevelKey(level, source.getUUID()));
            if (run == null) {
                return Optional.empty();
            }
            EntityLevelKey pendingKey = new EntityLevelKey(level, source.getUUID());
            try {
                DeferredRemoval deferred = DEFERRED_REMOVALS.get(pendingKey);
                if (deferred == null || deferred.run() != run) {
                    throw new IllegalStateException(
                            "owned painting drop lacks its exact preceding discard boundary");
                }
                refreshHead(run);
                if (run.head.phase() == SlabRigHangingDirectState.Phase.CLEARED
                        || run.head.phase() == SlabRigHangingDirectState.Phase.CLEARING_ENTITIES
                        || run.head.phase() == SlabRigHangingDirectState.Phase.CLEARING_ATTACHMENTS
                        || run.head.phase() == SlabRigHangingDirectState.Phase.CLEARING_CELLS) {
                    throw new IllegalStateException("owned painting drop in clear/terminal phase "
                            + run.head.phase());
                }
                SlabRigHangingDirectState.EntityOwnership ownership =
                        entity(run.head, source.getUUID());
                if (ownership.role() != SlabRigHangingDirectState.EntityRole.PAINTING
                        || ownership.disposition()
                        != SlabRigHangingDirectState.EntityDisposition.IN_WORLD
                        || !paintingFingerprintMatches(level, source, ownership)) {
                    throw new IllegalStateException("owned painting drop identity mismatch "
                            + source.getUUID());
                }
                SlabRigHangingDirectEvidence.PaintingEvidence evidence =
                        SlabRigHangingDirectEvidence.painting(level, source);
                SlabRigHangingDirectState.RemovalCause cause = run.clearOnly
                        ? SlabRigHangingDirectState.RemovalCause.UNEXPLAINED
                        : evidence.survives()
                        ? SlabRigHangingDirectState.RemovalCause.INTERFERENCE
                        : SlabRigHangingDirectState.RemovalCause.SUPPORT_LOSS_DROP_EXPECTED;
                SlabRigHangingDirectStateStore.WrittenArtifact removal = writeAndReadArtifact(
                        removalArtifact(run.head, ownership, cause, evidence, level, source,
                                "painting-drop-boundary"));
                PendingRemoval pending = new PendingRemoval(run, source.getUUID(), cause,
                        removal.hash());
                if (PENDING_REMOVALS.putIfAbsent(pendingKey, pending) != null) {
                    throw new IllegalStateException("duplicate pending painting removal "
                            + source.getUUID());
                }
                return Optional.of(new SlabRigHangingDirectEntityGate.CaptureKey(
                        run.head.run().runId(), ownership.attemptId()));
            } catch (Throwable failure) {
                PENDING_REMOVALS.remove(pendingKey);
                quarantineHandlerFailure(run, source, level,
                        "begin painting drop failed", failure);
                SlabRigHangingDirectState.EntityOwnership ownership =
                        entity(run.head, source.getUUID());
                // Keep the owned source inside a DROP scope. Missing pending evidence makes
                // preclaim reject/veto, and completion records the failure without escaping tick.
                return Optional.of(new SlabRigHangingDirectEntityGate.CaptureKey(
                        run.head.run().runId(), ownership.attemptId()));
            }
        }

        @Override
        public SlabRigHangingDirectEntityGate.PreclaimDecision preclaim(
                SlabRigHangingDirectEntityGate.CaptureContext context, Entity entity,
                ServerLevel level) throws Exception {
            ActiveRun run = RUNS_BY_ID.get(context.key().runId());
            if (run == null || run.level != level || run.server != level.getServer()
                    || !context.dimension().equals(level.dimension())) {
                return SlabRigHangingDirectEntityGate.PreclaimDecision.REJECT;
            }
            try {
                refreshHead(run);
                int ordinal = caseOrdinal(run.head, context.key().attemptId());
                if (context.kind() == SlabRigHangingDirectEntityGate.CaptureKind.PLACEMENT) {
                    if (!(entity instanceof Painting painting) || run.clearOnly
                            || run.head.phase() != SlabRigHangingDirectState.Phase.CASE_IN_FLIGHT
                            || run.head.cases().get(ordinal).phase()
                            != SlabRigHangingDirectState.CasePhase.IN_FLIGHT) {
                        return SlabRigHangingDirectEntityGate.PreclaimDecision.REJECT;
                    }
                    SlabRigHangingDirectEvidence.PaintingEvidence evidence =
                            SlabRigHangingDirectEvidence.painting(level, painting);
                    SlabRigHangingDirectStateStore.WrittenArtifact evidenceArtifact =
                            writeAndReadArtifact(paintingEvidenceArtifact(evidence, level, painting));
                    SlabRigHangingDirectState.EntityOwnership ownership = paintingOwnership(
                            evidence, evidenceArtifact.hash(), ordinal,
                            context.key().attemptId());
                    appendRun(run, run.head.withPreclaimedEntity(ownership,
                            "painting-preclaim=" + entity.getUUID()));
                    RUNS_BY_ENTITY.put(new EntityLevelKey(level, entity.getUUID()), run);
                    return SlabRigHangingDirectEntityGate.PreclaimDecision.CLAIM_AND_ALLOW;
                }
                if (!(entity instanceof ItemEntity item)
                        || context.sourcePaintingUuid().isEmpty()) {
                    return SlabRigHangingDirectEntityGate.PreclaimDecision.REJECT;
                }
                UUID sourceUuid = context.sourcePaintingUuid().orElseThrow();
                EntityLevelKey pendingKey = new EntityLevelKey(level, sourceUuid);
                PendingRemoval pending = PENDING_REMOVALS.get(pendingKey);
                SlabRigHangingDirectState.EntityOwnership source = entity(run.head, sourceUuid);
                if (pending == null || pending.run() != run
                        || source.role() != SlabRigHangingDirectState.EntityRole.PAINTING
                        || source.disposition()
                        != SlabRigHangingDirectState.EntityDisposition.IN_WORLD
                        || !source.attemptId().equals(context.key().attemptId())) {
                    return SlabRigHangingDirectEntityGate.PreclaimDecision.REJECT;
                }
                SlabRigHangingDirectEvidence.ItemEvidence evidence =
                        SlabRigHangingDirectEvidence.item(level, item);
                SlabRigHangingDirectStateStore.WrittenArtifact evidenceArtifact =
                        writeAndReadArtifact(itemEvidenceArtifact(evidence, level, item));
                SlabRigHangingDirectState.EntityOwnership removed =
                        source.removed(pending.cause(), pending.artifactHash());
                SlabRigHangingDirectState.EntityOwnership dropped = itemOwnership(
                        evidence, evidenceArtifact.hash(), removed,
                        context.key().attemptId());
                List<SlabRigHangingDirectState.EntityOwnership> entities =
                        replaceEntity(run.head.entities(), removed);
                entities.add(dropped);
                SlabRigHangingDirectState.Phase phase = pending.cause()
                        == SlabRigHangingDirectState.RemovalCause.INTERFERENCE
                        || pending.cause() == SlabRigHangingDirectState.RemovalCause.UNEXPLAINED
                        ? SlabRigHangingDirectState.Phase.QUARANTINED : run.head.phase();
                SlabRigHangingDirectState.State next = run.head.successor(phase,
                        run.head.nextCaseOrdinal(), run.head.authoredCells(),
                        run.head.authoredAttachments(), run.head.cases(), entities,
                        run.head.scheduler(), run.head.clear(), run.head.artifacts(),
                        "drop-claim-veto=" + item.getUUID() + ";source=" + sourceUuid
                                + ";cause=" + pending.cause());
                appendRun(run, next);
                PENDING_REMOVALS.remove(pendingKey, pending);
                DEFERRED_REMOVALS.remove(pendingKey);
                RUNS_BY_ENTITY.remove(pendingKey);
                return SlabRigHangingDirectEntityGate.PreclaimDecision.CLAIM_AND_VETO;
            } catch (Throwable failure) {
                quarantineHandlerFailure(run, null, level, "entity preclaim failed", failure);
                throw new IllegalStateException("owned entity preclaim failed closed", failure);
            }
        }

        @Override
        public void confirm(SlabRigHangingDirectEntityGate.CaptureContext context, Entity entity,
                            ServerLevel level) throws Exception {
            ActiveRun run = RUNS_BY_ID.get(context.key().runId());
            if (run == null || run.level != level || !(entity instanceof Painting painting)
                    || context.kind() != SlabRigHangingDirectEntityGate.CaptureKind.PLACEMENT) {
                throw new IllegalStateException("painting confirmation context mismatch");
            }
            try {
                refreshHead(run);
                SlabRigHangingDirectState.EntityOwnership ownership =
                        entity(run.head, entity.getUUID());
                if (!ownership.attemptId().equals(context.key().attemptId())
                        || !paintingFingerprintMatches(level, painting, ownership)) {
                    throw new IllegalStateException("painting confirmation identity mismatch "
                            + entity.getUUID());
                }
                appendRun(run, run.head.withConfirmedEntity(entity.getUUID(),
                        "painting-confirmed=" + entity.getUUID()));
            } catch (Throwable failure) {
                quarantineHandlerFailure(run, painting, level,
                        "painting confirmation failed", failure);
                throw new IllegalStateException("painting confirmation failed closed", failure);
            }
        }

        @Override
        public void complete(SlabRigHangingDirectEntityGate.CaptureResult result,
                             ServerLevel level) {
            if (result.context().kind() != SlabRigHangingDirectEntityGate.CaptureKind.DROP) {
                return;
            }
            ActiveRun run = RUNS_BY_ID.get(result.context().key().runId());
            if (run == null) {
                return;
            }
            try {
                boolean healthy = result.closed() && result.active()
                        && result.entities().size() == 1
                        && result.entities().getFirst().preclaimStatus()
                        == SlabRigHangingDirectEntityGate.PreclaimStatus.CLAIMED_VETO
                        && !result.entities().getFirst().confirmationAttempted()
                        && !result.entities().getFirst().confirmed()
                        && result.entities().getFirst().confirmationFailure().isEmpty();
                if (!healthy) {
                    result.context().sourcePaintingUuid().ifPresent(uuid ->
                            PENDING_REMOVALS.remove(new EntityLevelKey(level, uuid)));
                    lifecycleFailure(run, "drop capture completion",
                            new IllegalStateException("unexpected drop result " + result));
                }
            } catch (Throwable failure) {
                lifecycleFailure(run, "drop capture completion callback", failure);
            }
        }
    }

    private static void validateReservation(ServerLevel level,
                                            SlabRigHangingDirectFixture.AbsolutePage page) {
        for (BlockPos pos : page.reservedCells()) {
            if (!level.isInWorldBounds(pos)) {
                throw new IllegalStateException("direct page escapes world bounds at " + pos);
            }
            SlabRigHangingDirectEvidence.CellEvidence evidence =
                    SlabRigHangingDirectEvidence.cell(level, pos);
            boolean occupied = !level.getBlockState(pos).isAir();
            boolean attached = SlabRigHangingDirectEvidence.hasAttachmentEvidence(evidence);
            if (occupied || attached) {
                throw new IllegalStateException("direct reservation is not empty at " + pos
                        + "; force never authorizes foreign overwrite");
            }
        }
        List<Entity> collisions = level.getEntities((Entity) null, page.bounds().aabb(),
                entity -> !entity.isRemoved());
        if (!collisions.isEmpty()) {
            throw new IllegalStateException("direct reservation contains a pre-existing entity UUID="
                    + collisions.stream().map(entity -> entity.getUUID().toString())
                    .sorted().findFirst().orElse("unknown"));
        }
    }

    private static Planning plan(ServerLevel level, BlockPos origin) {
        SlabRigHangingCatalog.Snapshot catalog = SlabRigHangingCatalog.snapshot();
        SlabRigHangingArtifacts.RuntimeSnapshot runtime =
                SlabRigHangingArtifacts.snapshot(catalog, level.registryAccess());
        SlabRigHangingPaintingPlan.Universe universe =
                SlabRigHangingPaintingPlan.snapshot(catalog, runtime);
        SlabRigHangingPaintingPlan.PagePlan pagePlan =
                SlabRigHangingPaintingPlan.page(universe, ROUTE, TOPOLOGY, PAGE);
        SlabRigHangingDirectFixture.AbsolutePage page =
                SlabRigHangingDirectFixture.adapt(universe, pagePlan, origin);
        return new Planning(catalog, runtime, universe, pagePlan, page);
    }

    private static ActiveRun rebuild(ServerLevel level, SlabRigHangingDirectState.State state,
                                     SlabRigHangingDirectStateStore.Reconstruction reconstruction) {
        try {
            if (!FACING.equals(state.run().facing())
                    || !state.run().buildGitSha().equals(BuildStamp.GIT_SHA)) {
                return null;
            }
            Planning planning = plan(level, state.run().base().toBlockPos());
            if (!runtimeMatches(state.run(), planning)) {
                return null;
            }
            boolean force = reconstruction.states().stream()
                    .filter(entry -> entry.run().runId().equals(state.run().runId())
                            && entry.phase() == SlabRigHangingDirectState.Phase.PLANNED)
                    .findFirst().map(entry -> entry.detail().equals("planned;force=true"))
                    .orElse(false);
            return new ActiveRun(level.getServer(), level, state.owner(), planning, state,
                    force, false);
        } catch (Throwable mismatch) {
            Slabbed.LOGGER.warn("RIG-3B2B1 runtime reconstruction refused run={}: {}",
                    state.run().runId(), mismatch.getMessage());
            return null;
        }
    }

    private static boolean runtimeMatches(SlabRigHangingDirectState.RunIdentity run,
                                          Planning planning) {
        return run.buildGitSha().equals(BuildStamp.GIT_SHA)
                && run.runtimeContentSha256().equals(planning.runtime().runtimeContentSha256())
                && run.minecraftVersion().equals(planning.runtime().minecraftVersion())
                && run.rig3aCatalogHash().equals(planning.catalog().catalogHash())
                && run.topologyCatalogHash().equals(planning.catalog().topologyCatalogHash())
                && run.rig3b1ExecutionIdentity().equals(planning.runtime().executionIdentity())
                && run.paintingRegistryHash().equals(planning.runtime().paintingRegistryHash())
                && run.universeHash().equals(planning.universe().universeHash())
                && run.planHash().equals(planning.plan().planHash())
                && run.semanticPageId().equals(planning.plan().semanticPageId())
                && run.routeIndex() == ROUTE && run.topologyIndex() == TOPOLOGY
                && run.selectorPage() == PAGE
                && run.frozenDyEnabled() == SlabAnchorAttachment.FROZEN_DY_ENABLED;
    }

    private static ActiveRun activeOrRebuild(ServerLevel level,
                                             SlabRigHangingDirectState.State state,
                                             SlabRigHangingDirectStateStore.Reconstruction reconstruction) {
        ActiveRun existing = RUNS_BY_OWNER_LEVEL.get(new OwnerLevelKey(level, state.ownerKey()));
        if (existing != null) {
            existing.head = state;
            return existing.clearOnly ? null : existing;
        }
        ActiveRun rebuilt = rebuild(level, state, reconstruction);
        if (rebuilt != null) {
            installRun(rebuilt);
        }
        return rebuilt;
    }

    private static void installRun(ActiveRun run) {
        RUNS_BY_ID.put(run.head.run().runId(), run);
        RUNS_BY_OWNER_LEVEL.put(new OwnerLevelKey(run.level, run.owner.key()), run);
        for (SlabRigHangingDirectState.EntityOwnership ownership : run.head.entities()) {
            if (ownership.role() == SlabRigHangingDirectState.EntityRole.PAINTING
                    && ownership.disposition()
                    != SlabRigHangingDirectState.EntityDisposition.REMOVED) {
                RUNS_BY_ENTITY.put(new EntityLevelKey(run.level, ownership.uuid()), run);
            }
        }
    }

    private static void uninstallRun(ActiveRun run) {
        RUNS_BY_ID.remove(run.head.run().runId(), run);
        RUNS_BY_OWNER_LEVEL.remove(new OwnerLevelKey(run.level, run.owner.key()), run);
        RUNS_BY_ENTITY.entrySet().removeIf(entry -> entry.getValue() == run);
    }

    private static void refreshHead(ActiveRun run) throws IOException {
        SlabRigHangingDirectState.State latest =
                STORE.verifyCurrent(run.owner, run.head).latestOrNull();
        if (latest == null || !latest.run().runId().equals(run.head.run().runId())) {
            throw new IOException("active direct ledger head/run disappeared");
        }
        run.head = latest;
    }

    private static void appendRun(ActiveRun run, SlabRigHangingDirectState.State candidate)
            throws IOException {
        appendChecked(run.head, candidate);
        run.head = candidate;
    }

    private static void appendChecked(SlabRigHangingDirectState.State previous,
                                      SlabRigHangingDirectState.State candidate) throws IOException {
        SlabRigHangingDirectStateStore.WrittenState written = STORE.append(previous, candidate);
        if (!written.stateHash().equals(candidate.stateHash())) {
            throw new IOException("direct state append/readback hash mismatch");
        }
    }

    private static SlabRigHangingDirectStateStore.WrittenArtifact writeAndReadArtifact(String text)
            throws IOException {
        SlabRigHangingDirectStateStore.WrittenArtifact written = STORE.writeArtifact(text);
        byte[] readback = STORE.readArtifact(written.hash());
        if (!text.equals(new String(readback, StandardCharsets.UTF_8))) {
            throw new IOException("direct artifact canonical readback mismatch " + written.hash());
        }
        return written;
    }

    private static List<SlabRigHangingDirectStateStore.WrittenArtifact> writeAndReadArtifacts(
            List<String> texts) throws IOException {
        List<SlabRigHangingDirectStateStore.WrittenArtifact> written = STORE.writeArtifacts(texts);
        if (written.size() != texts.size()) {
            throw new IOException("direct artifact batch changed cardinality");
        }
        for (int index = 0; index < texts.size(); index++) {
            SlabRigHangingDirectStateStore.WrittenArtifact artifact = written.get(index);
            byte[] readback = STORE.readArtifact(artifact.hash());
            if (!texts.get(index).equals(new String(readback, StandardCharsets.UTF_8))) {
                throw new IOException("direct artifact batch canonical readback mismatch "
                        + artifact.hash());
            }
        }
        return written;
    }

    private static void quarantine(ActiveRun run, String detail) throws IOException {
        refreshHead(run);
        if (run.head.phase() == SlabRigHangingDirectState.Phase.QUARANTINED
                || run.head.phase() == SlabRigHangingDirectState.Phase.CLEARED
                || run.head.phase() == SlabRigHangingDirectState.Phase.CLEARING_ENTITIES
                || run.head.phase() == SlabRigHangingDirectState.Phase.CLEARING_ATTACHMENTS
                || run.head.phase() == SlabRigHangingDirectState.Phase.CLEARING_CELLS) {
            return;
        }
        SlabRigHangingDirectState.State quarantined = run.head.successor(
                SlabRigHangingDirectState.Phase.QUARANTINED, run.head.nextCaseOrdinal(),
                run.head.authoredCells(), run.head.authoredAttachments(), run.head.cases(),
                run.head.entities(), run.head.scheduler(), run.head.clear(), run.head.artifacts(),
                "quarantined;" + detail);
        appendRun(run, quarantined);
    }

    private static void quarantineOwnedIfPossible(CommandSourceStack source, Throwable failure) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = source.getLevel();
            String worldKey = SlabRigHangingDirectStateStore.readWorldKey(worldRoot(level.getServer()));
            SlabRigHangingDirectState.Owner owner = owner(worldKey, level, player.getUUID());
            ActiveRun run = RUNS_BY_OWNER_LEVEL.get(new OwnerLevelKey(level, owner.key()));
            if (run != null && !run.clearOnly) {
                run.driver = null;
                quarantine(run, "command failure: " + describe(failure));
            }
        } catch (Throwable ignored) {
            // The original command error remains authoritative; never widen mutation to find a run.
        }
    }

    private static void lifecycleFailure(ActiveRun run, String lane, Throwable failure) {
        run.driver = null;
        Slabbed.LOGGER.error("RIG-3B2B1 {} failed run={}", lane, run.head.run().runId(), failure);
        try {
            quarantine(run, lane + " failed: " + describe(failure));
        } catch (Throwable quarantineFailure) {
            Slabbed.LOGGER.error("RIG-3B2B1 quarantine append failed after {}", lane,
                    quarantineFailure);
        }
    }

    private static void quarantineHandlerFailure(ActiveRun run, Painting painting,
                                                 ServerLevel level, String lane,
                                                 Throwable failure) {
        try {
            refreshHead(run);
            if (painting != null) {
                SlabRigHangingDirectState.EntityOwnership ownership = run.head.entities().stream()
                        .filter(entry -> entry.uuid().equals(painting.getUUID()))
                        .findFirst().orElse(null);
                if (ownership != null && ownership.disposition()
                        == SlabRigHangingDirectState.EntityDisposition.IN_WORLD) {
                    SlabRigHangingDirectEvidence.PaintingEvidence evidence =
                            SlabRigHangingDirectEvidence.painting(level, painting);
                    SlabRigHangingDirectStateStore.WrittenArtifact artifact = writeAndReadArtifact(
                            removalArtifact(run.head, ownership,
                                    SlabRigHangingDirectState.RemovalCause.UNEXPLAINED,
                                    evidence, level, painting, lane));
                    SlabRigHangingDirectState.EntityOwnership removed = ownership.removed(
                            SlabRigHangingDirectState.RemovalCause.UNEXPLAINED, artifact.hash());
                    List<SlabRigHangingDirectState.EntityOwnership> entities =
                            replaceEntity(run.head.entities(), removed);
                    appendRun(run, run.head.successor(
                            SlabRigHangingDirectState.Phase.QUARANTINED,
                            run.head.nextCaseOrdinal(), run.head.authoredCells(),
                            run.head.authoredAttachments(), run.head.cases(), entities,
                            run.head.scheduler(), run.head.clear(), run.head.artifacts(),
                            "quarantined;" + lane + ":" + describe(failure)));
                    RUNS_BY_ENTITY.remove(new EntityLevelKey(level, painting.getUUID()));
                    return;
                }
            }
            quarantine(run, lane + ":" + describe(failure));
        } catch (Throwable quarantineFailure) {
            Slabbed.LOGGER.error("RIG-3B2B1 handler quarantine failed lane={} run={}", lane,
                    run.head.run().runId(), quarantineFailure);
        }
    }

    private static void ensureProcess(MinecraftServer server) {
        if (activeServer == null) {
            activeServer = server;
            processEpoch = UUID.randomUUID().toString();
        } else if (activeServer != server) {
            throw new IllegalStateException("direct executor is bound to another server process");
        }
    }

    private static SlabRigHangingDirectStateStore.Reconstruction reconstructOwner(
            ServerLevel level, ServerPlayer player, boolean createWorldIdentity) throws IOException {
        Path root = worldRoot(level.getServer());
        String worldKey = createWorldIdentity
                ? SlabRigHangingDirectStateStore.createWorldKey(root)
                : SlabRigHangingDirectStateStore.readWorldKey(root);
        SlabRigHangingDirectState.Owner owner = owner(worldKey, level, player.getUUID());
        ActiveRun active = RUNS_BY_OWNER_LEVEL.get(new OwnerLevelKey(level, owner.key()));
        if (active != null) {
            // The in-process head is only an expected CAS identity. verifyCurrent still checks the
            // complete immutable ledger shape and cached evidence metadata, and falls back to full
            // reconstruction if any authoritative file changed.
            return STORE.verifyCurrent(owner, active.head);
        }
        return STORE.reconstruct(owner);
    }

    private static Path worldRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    }

    private static SlabRigHangingDirectState.Owner owner(String worldKey, ServerLevel level,
                                                         UUID playerUuid) {
        return new SlabRigHangingDirectState.Owner(worldKey,
                level.dimension().identifier().toString(), playerUuid);
    }

    private static void requireExactPlayerLevel(ServerPlayer player, ServerLevel level) {
        if (player.level() != level) {
            throw new IllegalArgumentException("direct command requires the exact server player/level");
        }
    }

    private static void clearProcessMaps() {
        RUNS_BY_ID.clear();
        RUNS_BY_OWNER_LEVEL.clear();
        RUNS_BY_ENTITY.clear();
        PENDING_REMOVALS.clear();
        DEFERRED_REMOVALS.clear();
        CLEARING_ENTITIES.clear();
    }

    private static int fail(CommandSourceStack source, String lane, Throwable failure) {
        source.sendFailure(Component.literal("Slabbed " + lane + " failed: " + describe(failure)));
        Slabbed.LOGGER.error("Slabbed {} failed", lane, failure);
        return 0;
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null ? "" : ":" + message);
    }

    private static String statusLine(SlabRigHangingDirectState.State state) {
        int minTicks = state.scheduler().credits().stream()
                .filter(SlabRigHangingDirectState.TickCredit::loaded)
                .mapToInt(SlabRigHangingDirectState.TickCredit::observedEntityTicks)
                .min().orElse(0);
        return "Slabbed direct 6143/42/1 phase=" + state.phase()
                + " run=" + state.run().runId().substring(0, 12)
                + " cases=" + state.nextCaseOrdinal() + "/16"
                + " cells=" + state.authoredCells().size() + "/" + state.plannedAuthoredCells().size()
                + " entities=" + state.entities().size() + " delayedMin=" + minTicks + "/102"
                + " detail=" + state.detail();
    }

    private static List<SlabRigHangingDirectState.CaseState> initialCases(
            SlabRigHangingDirectFixture.AbsolutePage page) {
        List<SlabRigHangingDirectState.CaseState> result = new ArrayList<>();
        for (int ordinal = 0; ordinal < page.cases().size(); ordinal++) {
            SlabRigHangingPaintingPlan.CasePlan entry = page.cases().get(ordinal).plan();
            String component = SlabRigHangingDirectState.sha256(
                    entry.selector().componentType() + '\0' + entry.selector().componentValue());
            result.add(new SlabRigHangingDirectState.CaseState(ordinal, entry.attemptId(),
                    entry.selector().semanticId(), component,
                    SlabRigHangingDirectState.CasePhase.PENDING,
                    SlabRigHangingDirectState.CaseOutcome.NONE,
                    SlabRigHangingDirectState.NO_VALUE));
        }
        return List.copyOf(result);
    }

    private static List<SlabRigHangingDirectState.Position> positions(List<BlockPos> positions) {
        return positions.stream().map(SlabRigHangingDirectState.Position::of).toList();
    }

    private static String plannedArtifact(SlabRigHangingDirectState.Owner owner,
                                          SlabRigHangingDirectState.RunIdentity run,
                                          Planning planning, boolean force,
                                          List<SlabRigHangingDirectState.CaseState> cases) {
        StringBuilder out = new StringBuilder("schema\tslabbed-rig-hanging-direct-planned-v1\n");
        field(out, "player_proof", SlabRigHangingDirectState.PLAYER_PROOF);
        field(out, "execution_contract", SlabRigHangingDirectState.EXECUTION_CONTRACT);
        field(out, "owner_key", owner.key());
        field(out, "world_key", owner.worldKey());
        field(out, "dimension", owner.dimension());
        field(out, "player_uuid", owner.playerUuid().toString());
        field(out, "run_id", run.runId());
        field(out, "run_nonce", run.runNonce().toString());
        field(out, "build_git_sha", run.buildGitSha());
        field(out, "runtime_content_sha256", run.runtimeContentSha256());
        field(out, "catalog_hash", run.rig3aCatalogHash());
        field(out, "topology_catalog_hash", run.topologyCatalogHash());
        field(out, "rig3b1_identity", run.rig3b1ExecutionIdentity());
        field(out, "painting_registry_hash", run.paintingRegistryHash());
        field(out, "universe_hash", run.universeHash());
        field(out, "plan_hash", run.planHash());
        field(out, "semantic_page_id", run.semanticPageId());
        field(out, "address", ROUTE + "/" + TOPOLOGY + "/" + PAGE);
        field(out, "origin", run.base().toString());
        field(out, "facing", run.facing());
        field(out, "force", Boolean.toString(force));
        field(out, "reserved_count", Integer.toString(planning.page().reservedCells().size()));
        field(out, "authored_count", Integer.toString(planning.page().clearOwnedCells().size()));
        for (BlockPos pos : planning.page().reservedCells()) {
            out.append("reserved\t").append(pos.getX()).append('\t').append(pos.getY())
                    .append('\t').append(pos.getZ()).append('\n');
        }
        for (SlabRigHangingDirectState.CaseState entry : cases) {
            out.append("case\t").append(entry.ordinal()).append('\t')
                    .append(escape(entry.attemptId())).append('\t')
                    .append(escape(entry.selectorId())).append('\t')
                    .append(entry.componentFingerprint()).append('\n');
        }
        return out.toString();
    }

    private static String observationArtifact(SlabRigHangingDirectState.State state, int ordinal,
                                              SlabRigHangingDirectActions.PaintingAttempt attempt) {
        StringBuilder out = new StringBuilder("schema\tslabbed-rig-hanging-direct-observation-v1\n");
        field(out, "run_id", state.run().runId());
        field(out, "ordinal", Integer.toString(ordinal));
        field(out, "attempt_id", attempt.attemptId());
        field(out, "interaction", attempt.interactionResult());
        field(out, "consumes", Boolean.toString(attempt.consumesAction()));
        field(out, "stack_before", attempt.stackBefore());
        field(out, "stack_after", attempt.stackAfter());
        field(out, "stat_before", Integer.toString(attempt.statBefore()));
        field(out, "stat_after", Integer.toString(attempt.statAfter()));
        field(out, "player_untouched", Boolean.toString(attempt.playerInventoryAndStatsUntouched()));
        field(out, "outcome", attempt.outcome());
        field(out, "detail", attempt.detail());
        for (SlabRigHangingDirectEntityGate.EntityOutcome entity : attempt.capture().entities()) {
            out.append("capture\t").append(entity.entityUuid()).append('\t')
                    .append(entity.preclaimStatus()).append('\t')
                    .append(entity.confirmed()).append('\n');
        }
        for (SlabRigHangingDirectEvidence.PaintingEvidence painting : attempt.paintings()) {
            out.append("painting\t").append(painting.uuid()).append('\t')
                    .append(painting.identityFingerprint()).append('\t')
                    .append(escape(painting.variantId())).append('\t')
                    .append(escape(painting.attachment().toShortString())).append('\t')
                    .append(escape(painting.facing())).append('\t')
                    .append(painting.survives()).append('\n');
        }
        return out.toString();
    }

    private static String immediateArtifact(SlabRigHangingDirectState.State state,
                                            List<SlabRigHangingDirectState.CaseState> cases) {
        StringBuilder out = new StringBuilder("schema\tslabbed-rig-hanging-direct-immediate-v1\n");
        field(out, "run_id", state.run().runId());
        for (SlabRigHangingDirectState.CaseState entry : cases) {
            out.append("case\t").append(entry.ordinal()).append('\t')
                    .append(entry.immediateObservationId()).append('\n');
        }
        state.entities().stream().sorted(Comparator.comparing(entry -> entry.uuid().toString()))
                .forEach(entry -> out.append("entity\t").append(entry.uuid()).append('\t')
                        .append(entry.role()).append('\t').append(entry.fingerprint()).append('\n'));
        return out.toString();
    }

    private static String finalArtifact(ActiveRun run, SlabRigHangingDirectState.State state,
                                        SlabRigHangingDirectState.Scheduler scheduler) {
        StringBuilder out = new StringBuilder("schema\tslabbed-rig-hanging-direct-final-v1\n");
        field(out, "player_proof", SlabRigHangingDirectState.PLAYER_PROOF);
        field(out, "run_id", state.run().runId());
        field(out, "process_epoch", scheduler.processEpoch());
        field(out, "generation", Long.toString(scheduler.generation()));
        for (SlabRigHangingDirectState.TickCredit credit : scheduler.credits()) {
            out.append("tick\t").append(credit.paintingUuid()).append('\t')
                    .append(credit.observedEntityTicks()).append('\t').append(credit.loaded())
                    .append('\t').append(credit.unloadResets()).append('\t')
                    .append(credit.lastObservedEntityTick()).append('\n');
        }
        for (SlabRigHangingDirectState.EntityOwnership ownership : state.entities()) {
            out.append("owned\t").append(ownership.uuid()).append('\t')
                    .append(ownership.role()).append('\t').append(ownership.disposition())
                    .append('\t').append(ownership.fingerprint()).append('\n');
            if (ownership.role() == SlabRigHangingDirectState.EntityRole.PAINTING
                    && ownership.disposition()
                    == SlabRigHangingDirectState.EntityDisposition.IN_WORLD) {
                Entity live = run.level.getEntity(ownership.uuid());
                if (!(live instanceof Painting painting)
                        || !paintingFingerprintMatches(run.level, painting, ownership)) {
                    throw new IllegalStateException("final painting identity mismatch "
                            + ownership.uuid());
                }
                appendPaintingEvidence(out,
                        SlabRigHangingDirectEvidence.painting(run.level, painting),
                        run.level, painting, "survivor");
            }
        }
        return out.toString();
    }

    private static String removalArtifact(SlabRigHangingDirectState.State state,
                                          SlabRigHangingDirectState.EntityOwnership ownership,
                                          SlabRigHangingDirectState.RemovalCause cause,
                                          SlabRigHangingDirectEvidence.PaintingEvidence evidence,
                                          ServerLevel level, Painting painting, String boundary) {
        StringBuilder out = new StringBuilder("schema\tslabbed-rig-hanging-direct-removal-v1\n");
        field(out, "run_id", state.run().runId());
        field(out, "source_uuid", ownership.uuid().toString());
        field(out, "case_ordinal", Integer.toString(ownership.caseOrdinal()));
        field(out, "attempt_id", ownership.attemptId());
        field(out, "cause", cause.name());
        field(out, "boundary", boundary);
        appendPaintingEvidence(out, evidence, level, painting, "removed");
        return out.toString();
    }

    private static String unexplainedRemovalArtifact(SlabRigHangingDirectState.State state,
                                                     SlabRigHangingDirectState.EntityOwnership ownership,
                                                     String boundary) {
        StringBuilder out = new StringBuilder("schema\tslabbed-rig-hanging-direct-removal-v1\n");
        field(out, "run_id", state.run().runId());
        field(out, "source_uuid", ownership.uuid().toString());
        field(out, "case_ordinal", Integer.toString(ownership.caseOrdinal()));
        field(out, "attempt_id", ownership.attemptId());
        field(out, "cause", SlabRigHangingDirectState.RemovalCause.UNEXPLAINED.name());
        field(out, "boundary", boundary);
        field(out, "live_entity", "ABSENT");
        field(out, "owned_fingerprint", ownership.fingerprint());
        return out.toString();
    }

    private static String paintingEvidenceArtifact(
            SlabRigHangingDirectEvidence.PaintingEvidence evidence,
            ServerLevel level, Painting painting) {
        StringBuilder out = new StringBuilder("schema\tslabbed-rig-hanging-direct-painting-evidence-v1\n");
        appendPaintingEvidence(out, evidence, level, painting, "painting");
        return out.toString();
    }

    private static String itemEvidenceArtifact(SlabRigHangingDirectEvidence.ItemEvidence evidence,
                                               ServerLevel level, ItemEntity item) {
        StringBuilder out = new StringBuilder("schema\tslabbed-rig-hanging-direct-item-evidence-v1\n");
        field(out, "role", "item");
        field(out, "uuid", evidence.uuid().toString());
        field(out, "type", evidence.type());
        field(out, "item_id", evidence.itemId());
        field(out, "count", Integer.toString(evidence.count()));
        field(out, "stack_sha256", evidence.stackSha256());
        field(out, "stack_components", item.getItem().getComponentsPatch().toString());
        field(out, "position_bits", evidence.position().toString());
        field(out, "aabb_bits", evidence.bounds().toString());
        field(out, "alive", Boolean.toString(evidence.alive()));
        field(out, "removed", Boolean.toString(evidence.removed()));
        field(out, "removal_reason", evidence.removalReason());
        field(out, "nbt_sha256", evidence.nbtSha256());
        field(out, "identity_fingerprint", evidence.identityFingerprint());
        field(out, "entity_snbt", SlabRigHangingDirectEvidence.sortedSnbt(
                SlabRigHangingDirectEvidence.saveEntity(level, item)));
        return out.toString();
    }

    private static void appendPaintingEvidence(StringBuilder out,
                                               SlabRigHangingDirectEvidence.PaintingEvidence evidence,
                                               ServerLevel level, Painting painting, String role) {
        field(out, "painting_role", role);
        field(out, "painting_uuid", evidence.uuid().toString());
        field(out, "painting_type", evidence.type());
        field(out, "painting_variant", evidence.variantId());
        field(out, "painting_component_variant", evidence.componentVariantId());
        field(out, "painting_attachment", evidence.attachment().toShortString());
        field(out, "painting_facing", evidence.facing());
        field(out, "painting_position_bits", evidence.position().toString());
        field(out, "painting_aabb_bits", evidence.bounds().toString());
        field(out, "painting_survives", Boolean.toString(evidence.survives()));
        field(out, "painting_alive", Boolean.toString(evidence.alive()));
        field(out, "painting_removed", Boolean.toString(evidence.removed()));
        field(out, "painting_removal_reason", evidence.removalReason());
        field(out, "painting_nbt_sha256", evidence.nbtSha256());
        field(out, "painting_identity_fingerprint", evidence.identityFingerprint());
        field(out, "painting_entity_snbt", SlabRigHangingDirectEvidence.sortedSnbt(
                SlabRigHangingDirectEvidence.saveEntity(level, painting)));
    }

    private static String clearedArtifact(SlabRigHangingDirectState.State state) {
        StringBuilder out = new StringBuilder("schema\tslabbed-rig-hanging-direct-cleared-v1\n");
        field(out, "run_id", state.run().runId());
        SlabRigHangingDirectState.ClearProgress clear = state.clear();
        field(out, "entity_cursor", Integer.toString(clear.entityCursor()));
        field(out, "attachment_cursor", Integer.toString(clear.attachmentCursor()));
        field(out, "cell_cursor", Integer.toString(clear.cellCursor()));
        clear.removedEntities().forEach(uuid -> out.append("entity_removed\t").append(uuid).append('\n'));
        clear.absentEntities().forEach(uuid -> out.append("entity_absent\t").append(uuid).append('\n'));
        clear.clearedAttachments().forEach(pos -> out.append("attachment_cleared\t").append(pos).append('\n'));
        clear.absentAttachments().forEach(pos -> out.append("attachment_absent\t").append(pos).append('\n'));
        clear.clearedCells().forEach(pos -> out.append("cell_cleared\t").append(pos).append('\n'));
        clear.absentCells().forEach(pos -> out.append("cell_absent\t").append(pos).append('\n'));
        return out.toString();
    }

    private static void field(StringBuilder out, String key, String value) {
        out.append(key).append('\t').append(escape(value)).append('\n');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static boolean isSeriousAttemptFailure(
            SlabRigHangingDirectActions.PaintingAttempt attempt) {
        return !attempt.playerInventoryAndStatsUntouched()
                || attempt.outcome().startsWith("ERROR_")
                || attempt.outcome().startsWith("QUARANTINED_")
                || attempt.outcome().startsWith("BOUNDED_FAILURE_");
    }

    private static void requireIdentityHash(String canonical, String expectedHash,
                                            String kind, BlockPos pos) throws IOException {
        String actual = SlabRigHangingDirectState.sha256(
                canonical.getBytes(StandardCharsets.UTF_8));
        if (!actual.equals(expectedHash)) {
            throw new IOException(kind + " evidence artifact/fingerprint disagreement at " + pos);
        }
    }

    private static boolean cellStaticPayloadMatchesOriginal(
            SlabRigHangingDirectEvidence.CellEvidence live, String ownershipFingerprint)
            throws IOException {
        byte[] bytes = STORE.readArtifact(ownershipFingerprint);
        SlabRigHangingDirectEvidence.StoredCellIdentity stored;
        try {
            stored = SlabRigHangingDirectEvidence.parseCellIdentityArtifact(bytes);
        } catch (IllegalArgumentException failure) {
            throw new IOException("stored cell identity failed strict inverse", failure);
        }
        return live.pos().equals(stored.pos())
                && live.blockState().equals(stored.blockState())
                && live.blockEntityType().equals(stored.blockEntityType())
                && live.blockEntityNbtSha256().equals(stored.blockEntityNbtSha256());
    }

    private static void rollbackBatch(ServerLevel level, List<BatchWrite> writes) {
        List<BatchWrite> reverse = new ArrayList<>(writes);
        Collections.reverse(reverse);
        for (BatchWrite write : reverse) {
            try {
                SlabRigHangingDirectEvidence.CellEvidence live =
                        SlabRigHangingDirectEvidence.cell(level, write.pos());
                if (!SlabRigHangingDirectEvidence.cellIdentityFingerprint(live).equals(
                        SlabRigHangingDirectEvidence.cellIdentityFingerprint(write.evidence()))) {
                    Slabbed.LOGGER.error("RIG-3B2B1 rollback refused changed current-batch cell {}",
                            write.pos());
                    continue;
                }
                SlabAnchorAttachment.removeAnchor(level, write.pos());
                level.setBlock(write.pos(), write.before(), CLEAR_FLAGS);
            } catch (Throwable rollbackFailure) {
                Slabbed.LOGGER.error("RIG-3B2B1 rollback failed at {}", write.pos(), rollbackFailure);
            }
        }
    }

    private static int caseOrdinal(SlabRigHangingDirectState.State state, String attemptId) {
        for (SlabRigHangingDirectState.CaseState entry : state.cases()) {
            if (entry.attemptId().equals(attemptId)) {
                return entry.ordinal();
            }
        }
        throw new IllegalArgumentException("capture attempt is not in exact page " + attemptId);
    }

    private static SlabRigHangingDirectState.EntityOwnership entity(
            SlabRigHangingDirectState.State state, UUID uuid) {
        return state.entities().stream().filter(entry -> entry.uuid().equals(uuid))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "entity UUID is not durably owned " + uuid));
    }

    private static List<SlabRigHangingDirectState.EntityOwnership> replaceEntity(
            List<SlabRigHangingDirectState.EntityOwnership> entities,
            SlabRigHangingDirectState.EntityOwnership replacement) {
        List<SlabRigHangingDirectState.EntityOwnership> updated = new ArrayList<>(entities.size());
        boolean found = false;
        for (SlabRigHangingDirectState.EntityOwnership entry : entities) {
            if (entry.uuid().equals(replacement.uuid())) {
                updated.add(replacement);
                found = true;
            } else {
                updated.add(entry);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("replacement entity UUID is not owned "
                    + replacement.uuid());
        }
        return updated;
    }

    private static SlabRigHangingDirectState.EntityOwnership paintingOwnership(
            SlabRigHangingDirectEvidence.PaintingEvidence evidence, String evidenceArtifact,
            int ordinal, String attemptId) {
        return new SlabRigHangingDirectState.EntityOwnership(evidence.uuid(),
                SlabRigHangingDirectState.EntityRole.PAINTING, evidence.type(), ordinal, attemptId,
                SlabRigHangingDirectState.NO_VALUE,
                SlabRigHangingDirectState.Acquisition.PRECLAIMED,
                SlabRigHangingDirectState.PreclaimDecision.ALLOW_AND_CONFIRM,
                SlabRigHangingDirectState.EntityDisposition.PREINSERTION,
                evidence.identityFingerprint(), evidenceArtifact, stateVec(evidence.position()),
                stateBox(evidence.bounds()), SlabRigHangingDirectState.NO_VALUE,
                SlabRigHangingDirectState.NO_VALUE);
    }

    private static SlabRigHangingDirectState.EntityOwnership itemOwnership(
            SlabRigHangingDirectEvidence.ItemEvidence evidence,
            String evidenceArtifact,
            SlabRigHangingDirectState.EntityOwnership source, String attemptId) {
        return new SlabRigHangingDirectState.EntityOwnership(evidence.uuid(),
                SlabRigHangingDirectState.EntityRole.DROPPED_ITEM, evidence.type(),
                source.caseOrdinal(), attemptId, source.uuid().toString(),
                SlabRigHangingDirectState.Acquisition.DROP_PRECLAIM,
                SlabRigHangingDirectState.PreclaimDecision.CLAIM_AND_VETO,
                SlabRigHangingDirectState.EntityDisposition.VETOED_BEFORE_INSERTION,
                evidence.identityFingerprint(), evidenceArtifact, stateVec(evidence.position()),
                stateBox(evidence.bounds()), SlabRigHangingDirectState.NO_VALUE,
                "drop-claim-and-veto");
    }

    private static SlabRigHangingDirectState.Vec3Bits stateVec(
            SlabRigHangingDirectEvidence.VecBits bits) {
        return new SlabRigHangingDirectState.Vec3Bits(bits.x(), bits.y(), bits.z());
    }

    private static SlabRigHangingDirectState.BoxBits stateBox(
            SlabRigHangingDirectEvidence.BoxBits bits) {
        return new SlabRigHangingDirectState.BoxBits(bits.minX(), bits.minY(), bits.minZ(),
                bits.maxX(), bits.maxY(), bits.maxZ());
    }

    private static boolean paintingFingerprintMatches(ServerLevel level, Painting painting,
                                                      SlabRigHangingDirectState.EntityOwnership ownership) {
        SlabRigHangingDirectEvidence.PaintingEvidence evidence =
                SlabRigHangingDirectEvidence.painting(level, painting);
        return ownership.role() == SlabRigHangingDirectState.EntityRole.PAINTING
                && ownership.expectedType().equals(evidence.type())
                && ownership.fingerprint().equals(evidence.identityFingerprint())
                && ownership.position().equals(stateVec(evidence.position()))
                && ownership.aabb().equals(stateBox(evidence.bounds()));
    }

    private static boolean entityFingerprintMatches(ServerLevel level, Entity live,
                                                    SlabRigHangingDirectState.EntityOwnership ownership) {
        if (!live.getUUID().equals(ownership.uuid())) {
            return false;
        }
        if (live instanceof Painting painting
                && ownership.role() == SlabRigHangingDirectState.EntityRole.PAINTING) {
            return paintingFingerprintMatches(level, painting, ownership);
        }
        if (live instanceof ItemEntity item
                && ownership.role() == SlabRigHangingDirectState.EntityRole.DROPPED_ITEM) {
            SlabRigHangingDirectEvidence.ItemEvidence evidence =
                    SlabRigHangingDirectEvidence.item(level, item);
            return ownership.expectedType().equals(evidence.type())
                    && ownership.fingerprint().equals(evidence.identityFingerprint())
                    && ownership.position().equals(stateVec(evidence.position()))
                    && ownership.aabb().equals(stateBox(evidence.bounds()));
        }
        return false;
    }

    private static SlabRigHangingDirectState.ClearProgress entityClearReceipt(
            SlabRigHangingDirectState.ClearProgress clear, UUID uuid,
            SlabRigHangingDirectState.ClearOutcome outcome) {
        List<UUID> removed = new ArrayList<>(clear.removedEntities());
        List<UUID> absent = new ArrayList<>(clear.absentEntities());
        List<UUID> refused = new ArrayList<>(clear.refusedEntities());
        switch (outcome) {
            case REMOVED -> removed.add(uuid);
            case ALREADY_ABSENT -> absent.add(uuid);
            case REFUSED_FINGERPRINT -> refused.add(uuid);
        }
        return new SlabRigHangingDirectState.ClearProgress(true, clear.requestedEntities(),
                clear.entityCursor() + 1, removed, absent, refused,
                clear.requestedAttachments(), clear.attachmentCursor(),
                clear.clearedAttachments(), clear.absentAttachments(), clear.refusedAttachments(),
                clear.requestedCells(), clear.cellCursor(), clear.clearedCells(),
                clear.absentCells(), clear.refusedCells());
    }

    private static SlabRigHangingDirectState.ClearProgress attachmentClearReceipt(
            SlabRigHangingDirectState.ClearProgress clear,
            SlabRigHangingDirectState.Position position,
            SlabRigHangingDirectState.ClearOutcome outcome) {
        List<SlabRigHangingDirectState.Position> removed =
                new ArrayList<>(clear.clearedAttachments());
        List<SlabRigHangingDirectState.Position> absent =
                new ArrayList<>(clear.absentAttachments());
        List<SlabRigHangingDirectState.Position> refused =
                new ArrayList<>(clear.refusedAttachments());
        switch (outcome) {
            case REMOVED -> removed.add(position);
            case ALREADY_ABSENT -> absent.add(position);
            case REFUSED_FINGERPRINT -> refused.add(position);
        }
        return new SlabRigHangingDirectState.ClearProgress(true, clear.requestedEntities(),
                clear.entityCursor(), clear.removedEntities(), clear.absentEntities(),
                clear.refusedEntities(), clear.requestedAttachments(),
                clear.attachmentCursor() + 1, removed, absent, refused,
                clear.requestedCells(), clear.cellCursor(), clear.clearedCells(),
                clear.absentCells(), clear.refusedCells());
    }

    private static SlabRigHangingDirectState.ClearProgress cellClearReceipt(
            SlabRigHangingDirectState.ClearProgress clear,
            SlabRigHangingDirectState.Position position,
            SlabRigHangingDirectState.ClearOutcome outcome) {
        List<SlabRigHangingDirectState.Position> removed = new ArrayList<>(clear.clearedCells());
        List<SlabRigHangingDirectState.Position> absent = new ArrayList<>(clear.absentCells());
        List<SlabRigHangingDirectState.Position> refused = new ArrayList<>(clear.refusedCells());
        switch (outcome) {
            case REMOVED -> removed.add(position);
            case ALREADY_ABSENT -> absent.add(position);
            case REFUSED_FINGERPRINT -> refused.add(position);
        }
        return new SlabRigHangingDirectState.ClearProgress(true, clear.requestedEntities(),
                clear.entityCursor(), clear.removedEntities(), clear.absentEntities(),
                clear.refusedEntities(), clear.requestedAttachments(), clear.attachmentCursor(),
                clear.clearedAttachments(), clear.absentAttachments(), clear.refusedAttachments(),
                clear.requestedCells(), clear.cellCursor() + 1, removed, absent, refused);
    }

    private record Planning(SlabRigHangingCatalog.Snapshot catalog,
                            SlabRigHangingArtifacts.RuntimeSnapshot runtime,
                            SlabRigHangingPaintingPlan.Universe universe,
                            SlabRigHangingPaintingPlan.PagePlan plan,
                            SlabRigHangingDirectFixture.AbsolutePage page) {
    }

    private record BatchWrite(BlockPos pos, BlockState before,
                              SlabRigHangingDirectEvidence.CellEvidence evidence) {
        private BatchWrite {
            pos = pos.immutable();
        }
    }

    private record OwnerLevelKey(ServerLevel level, String ownerKey) {
    }

    private record EntityLevelKey(ServerLevel level, UUID uuid) {
    }

    private record PendingRemoval(ActiveRun run, UUID sourceUuid,
                                  SlabRigHangingDirectState.RemovalCause cause,
                                  String artifactHash) {
    }

    private record DeferredRemoval(ActiveRun run, UUID sourceUuid, Painting painting,
                                   SlabRigHangingDirectEvidence.PaintingEvidence evidence,
                                   String reason) {
    }

    private static final class ActiveRun {
        private final MinecraftServer server;
        private final ServerLevel level;
        private final SlabRigHangingDirectState.Owner owner;
        private final Planning planning;
        private final boolean force;
        private final boolean clearOnly;
        private SlabRigHangingDirectState.State head;
        private ServerPlayer driver;
        private boolean clearRequested;

        private ActiveRun(MinecraftServer server, ServerLevel level,
                          SlabRigHangingDirectState.Owner owner, Planning planning,
                          SlabRigHangingDirectState.State head, boolean force,
                          boolean clearOnly) {
            this.server = server;
            this.level = level;
            this.owner = owner;
            this.planning = planning;
            this.head = head;
            this.force = force;
            this.clearOnly = clearOnly;
        }
    }

    /** Auto-closeable token for the isolated integration-test store swap. */
    public static final class StoreOverride implements AutoCloseable {
        private final SlabRigHangingDirectStateStore previous;
        private final SlabRigHangingDirectStateStore replacement;
        private boolean closed;

        private StoreOverride(SlabRigHangingDirectStateStore previous,
                              SlabRigHangingDirectStateStore replacement) {
            this.previous = previous;
            this.replacement = replacement;
        }

        @Override
        public void close() {
            synchronized (SlabRigHangingDirectExecutor.class) {
                if (closed) {
                    return;
                }
                if (STORE != replacement || !RUNS_BY_ID.isEmpty()
                        || !PENDING_REMOVALS.isEmpty() || !DEFERRED_REMOVALS.isEmpty()) {
                    throw new IllegalStateException("direct test store override closed while in use");
                }
                STORE = previous;
                testStoreOverrideOpen = false;
                closed = true;
            }
        }
    }
}
