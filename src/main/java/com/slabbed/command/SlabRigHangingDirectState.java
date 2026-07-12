package com.slabbed.command;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable, self-hashed active-state model for production RIG-3B3A painting pages.
 *
 * <p>This class deliberately models transient write-ahead states as well as user-visible evidence
 * phases. A fixture reservation is never clear authority: only a confirmed authored-cell or
 * attachment fingerprint may later be cleared. Entity ownership is similarly acquired before Fabric
 * inserts an entity. The command executor can therefore append a {@link Phase#CASE_IN_FLIGHT}
 * painting preclaim from {@code ServerEntityEvents.ALLOW_LOAD}, or a same-phase
 * {@link Acquisition#DROP_PRECLAIM} receipt whose {@link PreclaimDecision#CLAIM_AND_VETO} prevents a
 * dropped item from ever entering the world.
 *
 * <p>The model contains no filesystem code and no live-world lookup. Canonical parsing, hashing, and
 * transition validation are pure so a newly constructed store/process can independently reconstruct
 * and verify a ledger without trusting prior in-memory objects.
 */
public final class SlabRigHangingDirectState {

    public static final String LEGACY_SCHEMA = "slabbed-rig-hanging-direct-state-v1";
    public static final String SCHEMA = "slabbed-rig-hanging-direct-state-v2";
    public static final String LEGACY_EXECUTION_CONTRACT =
            "rig3b2b1-route6143-topology42-selectorpage1-v1";
    public static final String EXECUTION_CONTRACT =
            "rig3b3a-route6143-topology42-selectorpages1-4-v2";
    public static final String NO_PREDECESSOR = "NONE";
    public static final String NO_VALUE = "NONE";
    public static final String PROVENANCE =
            "AUTO_ITEM_USEON_EXPLICIT_STACK_NULL_PLAYER_PROXY";
    public static final String PLAYER_PROOF = "ABSENT";
    public static final String CLIENT_PROOF = "ABSENT";
    public static final int ROUTE_INDEX = 6143;
    public static final int TOPOLOGY_INDEX = 42;
    public static final int MIN_SELECTOR_PAGE = 1;
    public static final int MAX_SELECTOR_PAGE = 4;
    public static final int LEGACY_SELECTOR_PAGE = 1;
    public static final int LEGACY_CASE_COUNT = 16;
    public static final int REQUIRED_ENTITY_TICKS = 102;

    private static final Comparator<Position> POSITION_ORDER = Comparator.naturalOrder();
    private static final Comparator<EntityOwnership> ENTITY_ORDER =
            Comparator.comparing(EntityOwnership::uuid);

    private SlabRigHangingDirectState() {
    }

    /** Exact on-disk grammar and owner-key namespace. Legacy bytes remain clear-only. */
    public enum Format {
        LEGACY_V1(LEGACY_SCHEMA, LEGACY_EXECUTION_CONTRACT, true),
        VARIABLE_V2(SCHEMA, EXECUTION_CONTRACT, false);

        private final String schema;
        private final String executionContract;
        private final boolean legacy;

        Format(String schema, String executionContract, boolean legacy) {
            this.schema = schema;
            this.executionContract = executionContract;
            this.legacy = legacy;
        }

        public String schema() {
            return schema;
        }

        public String executionContract() {
            return executionContract;
        }

        public boolean legacy() {
            return legacy;
        }

        private static Format fromSchema(String schema) {
            for (Format format : values()) {
                if (format.schema.equals(schema)) {
                    return format;
                }
            }
            throw new IllegalArgumentException("unsupported schema");
        }
    }

    /** Durable execution phases. IMMEDIATE and FINAL are named evidence boundaries, not aliases. */
    public enum Phase {
        PLANNED,
        FIXTURE_AUTHORING,
        FIXTURE_READY,
        CASE_IN_FLIGHT,
        IMMEDIATE_PARTIAL,
        IMMEDIATE,
        WAITING_DELAYED,
        FINAL,
        QUARANTINED,
        CLEARING_ENTITIES,
        CLEARING_ATTACHMENTS,
        CLEARING_CELLS,
        CLEARED
    }

    public enum CasePhase {
        PENDING,
        IN_FLIGHT,
        IMMEDIATE
    }

    /** Typed synchronous outcome; interaction-result text is never used as placement truth. */
    public enum CaseOutcome {
        NONE,
        PLACED,
        VANILLA_REFUSAL
    }

    public enum EntityRole {
        PAINTING,
        DROPPED_ITEM
    }

    /** How durable ownership relates to Fabric's pre-insertion/load callbacks. */
    public enum Acquisition {
        PRECLAIMED,
        DROP_PRECLAIM,
        LOADED
    }

    /**
     * Painting placement is allowed only after its preclaim is durable. Painting drops are claimed and
     * vetoed: the exact item evidence remains durable while the ItemEntity never becomes transferable.
     */
    public enum PreclaimDecision {
        ALLOW_AND_CONFIRM,
        CLAIM_AND_VETO
    }

    public enum EntityDisposition {
        PREINSERTION,
        IN_WORLD,
        VETOED_BEFORE_INSERTION,
        REMOVED,
        PICKED_UP,
        MERGED,
        TRANSFER_AMBIGUOUS
    }

    public enum ClearOutcome {
        REMOVED,
        ALREADY_ABSENT,
        REFUSED_FINGERPRINT
    }

    /** Typed cause bound to the content-addressed receipt captured at the removal boundary. */
    public enum RemovalCause {
        SUPPORT_LOSS_DROP_EXPECTED,
        SUPPORT_LOSS_NO_DROP,
        INTERFERENCE,
        UNEXPLAINED
    }

    public record Position(int x, int y, int z) implements Comparable<Position> {
        public static Position of(BlockPos pos) {
            return new Position(pos.getX(), pos.getY(), pos.getZ());
        }

        public BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }

        @Override
        public int compareTo(Position other) {
            int xOrder = Integer.compare(x, other.x);
            if (xOrder != 0) {
                return xOrder;
            }
            int yOrder = Integer.compare(y, other.y);
            return yOrder != 0 ? yOrder : Integer.compare(z, other.z);
        }
    }

    /** Exact IEEE-754 identity. Decimal display never enters ownership. */
    public record Vec3Bits(long xBits, long yBits, long zBits) {
        public static Vec3Bits of(Vec3 value) {
            return new Vec3Bits(Double.doubleToRawLongBits(value.x),
                    Double.doubleToRawLongBits(value.y), Double.doubleToRawLongBits(value.z));
        }
    }

    /** Exact IEEE-754 AABB identity. */
    public record BoxBits(long minXBits, long minYBits, long minZBits,
                          long maxXBits, long maxYBits, long maxZBits) {
        public static BoxBits of(AABB value) {
            return new BoxBits(Double.doubleToRawLongBits(value.minX),
                    Double.doubleToRawLongBits(value.minY), Double.doubleToRawLongBits(value.minZ),
                    Double.doubleToRawLongBits(value.maxX), Double.doubleToRawLongBits(value.maxY),
                    Double.doubleToRawLongBits(value.maxZ));
        }
    }

    /** Ledger owner. The store hashes this tuple into its only authoritative directory name. */
    public record Owner(String worldKey, String dimension, UUID playerUuid) {
        public Owner {
            requireSha256(worldKey, "worldKey");
            requireText(dimension, "dimension");
            Objects.requireNonNull(playerUuid, "playerUuid");
        }

        public String key() {
            return key(Format.VARIABLE_V2);
        }

        public String key(Format format) {
            Objects.requireNonNull(format, "format");
            return sha256(format.schema + '\0' + worldKey + '\0' + dimension + '\0' + playerUuid);
        }

        public String legacyKey() {
            return key(Format.LEGACY_V1);
        }
    }

    /** Immutable execution identity reconstructed from the live catalog/registry before resume. */
    public record RunIdentity(String runId, UUID runNonce, String buildGitSha,
                              String runtimeContentSha256, String minecraftVersion,
                              String rig3aCatalogHash, String topologyCatalogHash,
                              String rig3b1ExecutionIdentity, String paintingRegistryHash,
                              String universeHash, String planHash, String semanticPageId,
                              int routeIndex, int topologyIndex, int selectorPage, int caseCount,
                              boolean frozenDyEnabled, Position base, String facing) {
        public RunIdentity {
            requireSha256(runId, "runId");
            Objects.requireNonNull(runNonce, "runNonce");
            if (buildGitSha == null || !("unknown".equals(buildGitSha)
                    || buildGitSha.matches("[0-9a-f]{7,64}(?:-dirty)?"))) {
                throw new IllegalArgumentException("buildGitSha must be an exact lowercase Git label");
            }
            requireSha256(runtimeContentSha256, "runtimeContentSha256");
            requireText(minecraftVersion, "minecraftVersion");
            requireSha256(rig3aCatalogHash, "rig3aCatalogHash");
            requireSha256(topologyCatalogHash, "topologyCatalogHash");
            requireSha256(rig3b1ExecutionIdentity, "rig3b1ExecutionIdentity");
            requireSha256(paintingRegistryHash, "paintingRegistryHash");
            requireSha256(universeHash, "universeHash");
            requireSha256(planHash, "planHash");
            requireText(semanticPageId, "semanticPageId");
            if (routeIndex != ROUTE_INDEX || topologyIndex != TOPOLOGY_INDEX
                    || selectorPage < MIN_SELECTOR_PAGE || selectorPage > MAX_SELECTOR_PAGE) {
                throw new IllegalArgumentException(
                        "production direct state accepts only route 6143/topology 42/pages 1..4");
            }
            if (caseCount <= 0 || caseCount > SlabRigHangingPaintingPlan.PAGE_SIZE) {
                throw new IllegalArgumentException(
                        "persisted caseCount must fit one validated painting-plan page");
            }
            Objects.requireNonNull(base, "base");
            requireText(facing, "facing");
        }
    }

    /** Stable semantic identity plus current no-replay execution status for one persisted page case. */
    public record CaseState(int ordinal, String attemptId, String selectorId,
                            String componentFingerprint, CasePhase phase, CaseOutcome outcome,
                            String immediateObservationId) {
        public CaseState {
            if (ordinal < 0) {
                throw new IllegalArgumentException("case ordinal must be non-negative");
            }
            requireText(attemptId, "attemptId");
            requireText(selectorId, "selectorId");
            requireSha256(componentFingerprint, "componentFingerprint");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(outcome, "outcome");
            requireOptionalSha256(immediateObservationId, "immediateObservationId");
            if (phase == CasePhase.IMMEDIATE
                    && (outcome == CaseOutcome.NONE || NO_VALUE.equals(immediateObservationId))
                    || phase != CasePhase.IMMEDIATE
                    && (outcome != CaseOutcome.NONE || !NO_VALUE.equals(immediateObservationId))) {
                throw new IllegalArgumentException(
                        "only IMMEDIATE cases may bind a typed outcome and observation ID");
            }
        }

        public CaseState inFlight() {
            if (phase != CasePhase.PENDING) {
                throw new IllegalStateException("only a pending case may enter flight");
            }
            return new CaseState(ordinal, attemptId, selectorId, componentFingerprint,
                    CasePhase.IN_FLIGHT, CaseOutcome.NONE, NO_VALUE);
        }

        public CaseState immediate(CaseOutcome immediateOutcome, String observationId) {
            if (phase != CasePhase.IN_FLIGHT) {
                throw new IllegalStateException("only the in-flight case may become immediate");
            }
            if (immediateOutcome != CaseOutcome.PLACED
                    && immediateOutcome != CaseOutcome.VANILLA_REFUSAL) {
                throw new IllegalArgumentException("immediate outcome must be placed or vanilla refusal");
            }
            return new CaseState(ordinal, attemptId, selectorId, componentFingerprint,
                    CasePhase.IMMEDIATE, immediateOutcome, observationId);
        }
    }

    /** Planned occupancy is collision authority, never deletion authority. */
    public record CellOwnership(Position pos, String fingerprint) {
        public CellOwnership {
            Objects.requireNonNull(pos, "pos");
            requireSha256(fingerprint, "cell fingerprint");
        }
    }

    /** Confirmed attachment/store fingerprint which may later be exact-cleared. */
    public record AttachmentOwnership(Position pos, String fingerprint) {
        public AttachmentOwnership {
            Objects.requireNonNull(pos, "pos");
            requireSha256(fingerprint, "attachment fingerprint");
        }
    }

    /**
     * Exact typed UUID ownership. Fingerprints bind the full role-specific evidence; sourcePaintingUuid
     * is mandatory only for a dropped item. Transfer fields remain explicit even though this executor normally
     * vetoes the drop before insertion, so a future unexpected transfer cannot be silently treated as
     * exact-clearable.
     */
    public record EntityOwnership(UUID uuid, EntityRole role, String expectedType,
                                  int caseOrdinal, String attemptId, String sourcePaintingUuid,
                                  Acquisition acquisition, PreclaimDecision decision,
                                  EntityDisposition disposition, String fingerprint,
                                  String evidenceArtifact, Vec3Bits position, BoxBits aabb,
                                  String transferTargetUuid, String transferDetail) {
        public EntityOwnership {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(role, "role");
            requireText(expectedType, "expectedType");
            if (caseOrdinal < 0) {
                throw new IllegalArgumentException("entity case ordinal must be non-negative");
            }
            requireText(attemptId, "attemptId");
            Objects.requireNonNull(acquisition, "acquisition");
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(disposition, "disposition");
            requireSha256(fingerprint, "entity fingerprint");
            requireSha256(evidenceArtifact, "entity evidence artifact");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(aabb, "aabb");
            requireText(transferDetail, "transferDetail");
            if (role == EntityRole.PAINTING) {
                boolean durablePreclaim = acquisition == Acquisition.PRECLAIMED
                        && disposition == EntityDisposition.PREINSERTION;
                boolean confirmedLoad = acquisition == Acquisition.LOADED
                        && disposition != EntityDisposition.PREINSERTION
                        && disposition != EntityDisposition.VETOED_BEFORE_INSERTION;
                if (!"minecraft:painting".equals(expectedType)
                        || !NO_VALUE.equals(sourcePaintingUuid)
                        || acquisition == Acquisition.DROP_PRECLAIM
                        || decision != PreclaimDecision.ALLOW_AND_CONFIRM
                        || !durablePreclaim && !confirmedLoad) {
                    throw new IllegalArgumentException("invalid painting ownership shape");
                }
                if (disposition == EntityDisposition.PREINSERTION
                        || disposition == EntityDisposition.IN_WORLD) {
                    if (!NO_VALUE.equals(transferTargetUuid)
                            || !NO_VALUE.equals(transferDetail)) {
                        throw new IllegalArgumentException(
                                "active painting ownership cannot carry removal/transfer evidence");
                    }
                } else if (disposition == EntityDisposition.REMOVED) {
                    parseRemovalDetail(transferDetail);
                }
            } else {
                if (!"minecraft:item".equals(expectedType)
                        || !isUuid(sourcePaintingUuid)
                        || acquisition != Acquisition.DROP_PRECLAIM
                        || decision != PreclaimDecision.CLAIM_AND_VETO
                        || disposition != EntityDisposition.VETOED_BEFORE_INSERTION) {
                    throw new IllegalArgumentException("dropped items must be causal claim-and-veto preclaims");
                }
            }
            if (disposition == EntityDisposition.TRANSFER_AMBIGUOUS
                    || disposition == EntityDisposition.MERGED) {
                if (!isUuid(transferTargetUuid) && !NO_VALUE.equals(transferTargetUuid)) {
                    throw new IllegalArgumentException("invalid transfer target UUID");
                }
            } else if (!NO_VALUE.equals(transferTargetUuid)) {
                throw new IllegalArgumentException("non-transfer disposition cannot name a transfer target");
            }
        }

        public EntityOwnership loaded() {
            if (role != EntityRole.PAINTING || acquisition != Acquisition.PRECLAIMED
                    || disposition != EntityDisposition.PREINSERTION) {
                throw new IllegalStateException("only a painting preclaim can be load-confirmed");
            }
            return new EntityOwnership(uuid, role, expectedType, caseOrdinal, attemptId,
                    sourcePaintingUuid, Acquisition.LOADED, decision, EntityDisposition.IN_WORLD,
                    fingerprint, evidenceArtifact, position, aabb, transferTargetUuid, transferDetail);
        }

        /** Immutable IN_WORLD -> REMOVED transition with an exact content-addressed cause receipt. */
        public EntityOwnership removed(RemovalCause cause, String artifactHash) {
            if (role != EntityRole.PAINTING || acquisition != Acquisition.LOADED
                    || disposition != EntityDisposition.IN_WORLD) {
                throw new IllegalStateException("only an in-world confirmed painting may be removed");
            }
            return new EntityOwnership(uuid, role, expectedType, caseOrdinal, attemptId,
                    sourcePaintingUuid, acquisition, decision, EntityDisposition.REMOVED,
                    fingerprint, evidenceArtifact, position, aabb, NO_VALUE,
                    removalDetail(cause, artifactHash));
        }

        public RemovalCause removalCause() {
            if (role != EntityRole.PAINTING || disposition != EntityDisposition.REMOVED) {
                throw new IllegalStateException("entity is not a removed painting");
            }
            return parseRemovalDetail(transferDetail).cause();
        }

        public String removalArtifact() {
            if (role != EntityRole.PAINTING || disposition != EntityDisposition.REMOVED) {
                throw new IllegalStateException("entity is not a removed painting");
            }
            return parseRemovalDetail(transferDetail).artifactHash();
        }
    }

    /** Per-survivor entity-tick credit; unloaded/global ticks never enter this record. */
    public record TickCredit(UUID paintingUuid, int observedEntityTicks, boolean loaded,
                             int unloadResets, long lastObservedEntityTick) {
        public TickCredit {
            Objects.requireNonNull(paintingUuid, "paintingUuid");
            if (observedEntityTicks < 0 || unloadResets < 0 || lastObservedEntityTick < -1) {
                throw new IllegalArgumentException("invalid entity-tick credit");
            }
            if (loaded && lastObservedEntityTick < 0
                    || !loaded && (observedEntityTicks != 0 || lastObservedEntityTick != -1)) {
                throw new IllegalArgumentException(
                        "loaded tick credit requires a raw baseline; unloaded credit is exact zero/-1");
            }
        }
    }

    /** Process-epoch scheduler state. It carries only observed painting ticks, never world time. */
    public record Scheduler(String processEpoch, long generation, List<TickCredit> credits) {
        public Scheduler {
            if (!NO_VALUE.equals(processEpoch) && !isUuid(processEpoch)) {
                throw new IllegalArgumentException("scheduler processEpoch must be NONE or UUID");
            }
            if (generation < 0) {
                throw new IllegalArgumentException("scheduler generation must be non-negative");
            }
            credits = credits.stream().sorted(Comparator.comparing(TickCredit::paintingUuid)).toList();
            requireUnique(credits.stream().map(TickCredit::paintingUuid).toList(),
                    "scheduler painting UUID");
            if (NO_VALUE.equals(processEpoch) && (!credits.isEmpty() || generation != 0)) {
                throw new IllegalArgumentException("inactive scheduler cannot carry tick credit");
            }
        }

        public static Scheduler inactive() {
            return new Scheduler(NO_VALUE, 0, List.of());
        }
    }

    public record ArtifactLinks(String planned, String immediate, String finalArtifact,
                                String cleared) {
        public ArtifactLinks {
            requireSha256(planned, "planned artifact");
            requireOptionalSha256(immediate, "immediate artifact");
            requireOptionalSha256(finalArtifact, "final artifact");
            requireOptionalSha256(cleared, "cleared artifact");
        }

        public static ArtifactLinks planned(String planned) {
            return new ArtifactLinks(planned, NO_VALUE, NO_VALUE, NO_VALUE);
        }
    }

    /** Exact requested/result partitions. Cursors are contiguous processed-prefix lengths. */
    public record ClearProgress(boolean started,
                                List<UUID> requestedEntities, int entityCursor,
                                List<UUID> removedEntities, List<UUID> absentEntities,
                                List<UUID> refusedEntities,
                                List<Position> requestedAttachments, int attachmentCursor,
                                List<Position> clearedAttachments, List<Position> absentAttachments,
                                List<Position> refusedAttachments,
                                List<Position> requestedCells, int cellCursor,
                                List<Position> clearedCells, List<Position> absentCells,
                                List<Position> refusedCells) {
        public ClearProgress {
            requestedEntities = normalizedUuids(requestedEntities);
            removedEntities = normalizedUuids(removedEntities);
            absentEntities = normalizedUuids(absentEntities);
            refusedEntities = normalizedUuids(refusedEntities);
            requestedAttachments = normalizedPositions(requestedAttachments);
            clearedAttachments = normalizedPositions(clearedAttachments);
            absentAttachments = normalizedPositions(absentAttachments);
            refusedAttachments = normalizedPositions(refusedAttachments);
            requestedCells = normalizedTopDownPositions(requestedCells);
            clearedCells = normalizedPositions(clearedCells);
            absentCells = normalizedPositions(absentCells);
            refusedCells = normalizedPositions(refusedCells);
            validatePartitionPrefix(requestedEntities, entityCursor, removedEntities,
                    absentEntities, refusedEntities, "entity");
            validatePartitionPrefix(requestedAttachments, attachmentCursor, clearedAttachments,
                    absentAttachments, refusedAttachments, "attachment");
            validatePartitionPrefix(requestedCells, cellCursor, clearedCells,
                    absentCells, refusedCells, "cell");
        }

        public static ClearProgress none() {
            return new ClearProgress(false, List.of(), 0, List.of(), List.of(), List.of(),
                    List.of(), 0, List.of(), List.of(), List.of(),
                    List.of(), 0, List.of(), List.of(), List.of());
        }

        public static ClearProgress begin(State state) {
            return new ClearProgress(true,
                    state.entities().stream().map(EntityOwnership::uuid).toList(),
                    0, List.of(), List.of(), List.of(),
                    state.authoredAttachments().stream().map(AttachmentOwnership::pos).toList(),
                    0, List.of(), List.of(), List.of(),
                    state.authoredCells().stream().map(CellOwnership::pos).toList(),
                    0, List.of(), List.of(), List.of());
        }

        public boolean isNone() {
            return !started && requestedEntities.isEmpty() && requestedAttachments.isEmpty()
                    && requestedCells.isEmpty() && entityCursor == 0
                    && attachmentCursor == 0 && cellCursor == 0;
        }

        public boolean entitiesCompleteWithoutRefusal() {
            return entityCursor == requestedEntities.size() && refusedEntities.isEmpty();
        }

        public boolean attachmentsCompleteWithoutRefusal() {
            return attachmentCursor == requestedAttachments.size() && refusedAttachments.isEmpty();
        }

        public boolean cellsCompleteWithoutRefusal() {
            return cellCursor == requestedCells.size() && refusedCells.isEmpty();
        }
    }

    /** One immutable ledger record. Use the factories so sequence/predecessor/hash cannot drift. */
    public record State(Format format, String stateHash, long sequence, String predecessorHash,
                        Owner owner, RunIdentity run, Phase phase, int nextCaseOrdinal,
                        List<Position> reservedCells, List<Position> plannedAuthoredCells,
                        List<CellOwnership> authoredCells,
                        List<AttachmentOwnership> authoredAttachments,
                        List<CaseState> cases, List<EntityOwnership> entities,
                        Scheduler scheduler, ClearProgress clear, ArtifactLinks artifacts,
                        String detail) {
        public State {
            Objects.requireNonNull(format, "format");
            requireText(stateHash, "stateHash");
            if (sequence < 0) {
                throw new IllegalArgumentException("state sequence must be non-negative");
            }
            requireText(predecessorHash, "predecessorHash");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(phase, "phase");
            reservedCells = normalizedPositions(reservedCells);
            plannedAuthoredCells = normalizedPositions(plannedAuthoredCells);
            authoredCells = authoredCells.stream().sorted(Comparator.comparing(CellOwnership::pos)).toList();
            authoredAttachments = authoredAttachments.stream()
                    .sorted(Comparator.comparing(AttachmentOwnership::pos)).toList();
            cases = cases.stream().sorted(Comparator.comparingInt(CaseState::ordinal)).toList();
            entities = entities.stream().sorted(ENTITY_ORDER).toList();
            Objects.requireNonNull(scheduler, "scheduler");
            Objects.requireNonNull(clear, "clear");
            Objects.requireNonNull(artifacts, "artifacts");
            requireText(detail, "detail");
        }

        /** First state for an owner ledger. PLANNED itself is already published/readback evidence. */
        public static State initial(Owner owner, RunIdentity run,
                                    Collection<Position> reservedCells,
                                    Collection<Position> plannedAuthoredCells,
                                    Collection<CaseState> cases, String plannedArtifactId,
                                    String detail) {
            State seed = new State(Format.VARIABLE_V2, "PENDING", 0, NO_PREDECESSOR,
                    owner, run, Phase.PLANNED, 0,
                    List.copyOf(reservedCells), List.copyOf(plannedAuthoredCells), List.of(), List.of(),
                    List.copyOf(cases), List.of(), Scheduler.inactive(), ClearProgress.none(),
                    ArtifactLinks.planned(plannedArtifactId), detail);
            State result = withComputedHash(seed);
            validateSelf(result);
            return result;
        }

        /** New run in the same owner ledger. The predecessor must be a verified terminal CLEARED state. */
        public static State afterCleared(State previous, RunIdentity run,
                                         Collection<Position> reservedCells,
                                         Collection<Position> plannedAuthoredCells,
                                         Collection<CaseState> cases, String plannedArtifactId,
                                         String detail) {
            validateSelf(previous);
            if (previous.format != Format.VARIABLE_V2 || previous.phase != Phase.CLEARED) {
                throw new IllegalArgumentException(
                        "a new variable-page run requires a v2 CLEARED predecessor");
            }
            State seed = new State(Format.VARIABLE_V2, "PENDING",
                    Math.addExact(previous.sequence, 1),
                    previous.stateHash, previous.owner, run, Phase.PLANNED, 0,
                    List.copyOf(reservedCells), List.copyOf(plannedAuthoredCells), List.of(), List.of(),
                    List.copyOf(cases), List.of(), Scheduler.inactive(), ClearProgress.none(),
                    ArtifactLinks.planned(plannedArtifactId), detail);
            State result = withComputedHash(seed);
            validateTransition(previous, result);
            return result;
        }

        /** General successor factory used by the executor for phase, fixture, scheduler, and clear steps. */
        public State successor(Phase nextPhase, int nextCaseOrdinal,
                               Collection<CellOwnership> nextAuthoredCells,
                               Collection<AttachmentOwnership> nextAttachments,
                               Collection<CaseState> nextCases,
                               Collection<EntityOwnership> nextEntities,
                               Scheduler nextScheduler, ClearProgress nextClear,
                               ArtifactLinks nextArtifacts, String nextDetail) {
            State seed = new State(format, "PENDING", Math.addExact(sequence, 1), stateHash,
                    owner, run, nextPhase, nextCaseOrdinal, reservedCells, plannedAuthoredCells,
                    List.copyOf(nextAuthoredCells), List.copyOf(nextAttachments),
                    List.copyOf(nextCases), List.copyOf(nextEntities), nextScheduler,
                    nextClear, nextArtifacts, nextDetail);
            State result = withComputedHash(seed);
            validateAppendTransition(this, result);
            return result;
        }

        /** ALLOW_LOAD convenience: same-phase strict ownership growth before insertion. */
        public State withPreclaimedEntity(EntityOwnership ownership, String nextDetail) {
            if (ownership.acquisition != Acquisition.PRECLAIMED
                    && ownership.acquisition != Acquisition.DROP_PRECLAIM) {
                throw new IllegalArgumentException("entity preclaim must be PRECLAIMED or DROP_PRECLAIM");
            }
            List<EntityOwnership> updated = new ArrayList<>(entities);
            updated.add(ownership);
            return successor(phase, nextCaseOrdinal, authoredCells, authoredAttachments, cases,
                    updated, scheduler, clear, artifacts, nextDetail);
        }

        /** ENTITY_LOAD convenience: upgrades only the exact existing painting row. */
        public State withConfirmedEntity(UUID uuid, String nextDetail) {
            List<EntityOwnership> updated = new ArrayList<>(entities.size());
            boolean found = false;
            for (EntityOwnership ownership : entities) {
                if (ownership.uuid.equals(uuid)) {
                    updated.add(ownership.loaded());
                    found = true;
                } else {
                    updated.add(ownership);
                }
            }
            if (!found) {
                throw new IllegalArgumentException("cannot confirm an unowned entity UUID " + uuid);
            }
            return successor(phase, nextCaseOrdinal, authoredCells, authoredAttachments, cases,
                    updated, scheduler, clear, artifacts, nextDetail);
        }

        public String ownerKey() {
            return owner.key(format);
        }

        public Set<Position> reservationSet() {
            return Set.copyOf(reservedCells);
        }

        public Set<UUID> entityUuidSet() {
            return entities.stream().map(EntityOwnership::uuid).collect(
                    java.util.stream.Collectors.toUnmodifiableSet());
        }

        /** UUIDs that may lawfully correspond to a live entity during construction preflight. */
        public Set<UUID> activePaintingUuidSet() {
            return entities.stream()
                    .filter(entity -> entity.role == EntityRole.PAINTING
                            && entity.disposition == EntityDisposition.IN_WORLD)
                    .map(EntityOwnership::uuid)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    public static String canonicalTsv(State state) {
        validateSelf(state);
        String body = canonicalBody(state);
        int firstLineEnd = body.indexOf('\n') + 1;
        return body.substring(0, firstLineEnd) + "state_hash\t" + state.stateHash + '\n'
                + body.substring(firstLineEnd);
    }

    /** Parse exact canonical bytes and reject alternate encodings, unknown rows, or hash drift. */
    public static State parse(String canonicalTsv) {
        Objects.requireNonNull(canonicalTsv, "canonicalTsv");
        if (!canonicalTsv.endsWith("\n")) {
            throw new IllegalArgumentException("canonical state must end with one newline");
        }
        List<String[]> rows = canonicalTsv.lines().map(line -> line.split("\t", -1)).toList();
        RowReader reader = new RowReader(rows);
        Format format = Format.fromSchema(reader.singleton("schema"));
        String stateHash = reader.singleton("state_hash");
        long sequence = parseLong(reader.singleton("sequence"), "sequence");
        String predecessor = reader.singleton("predecessor_hash");
        reader.singleton("execution_contract", format.executionContract);
        reader.singleton("provenance", PROVENANCE);
        reader.singleton("player_proof", PLAYER_PROOF);
        reader.singleton("client_proof", CLIENT_PROOF);
        String ownerKey = reader.singleton("owner_key");
        Owner owner = new Owner(reader.singleton("world_key"), unescape(reader.singleton("dimension")),
                parseUuid(reader.singleton("player_uuid"), "player_uuid"));
        if (!owner.key(format).equals(ownerKey)) {
            throw new IllegalArgumentException("owner_key does not match owner tuple");
        }
        int selectorPage = parseInt(reader.singleton("selector_page"), "selector_page");
        int caseCount = format.legacy ? LEGACY_CASE_COUNT
                : parseInt(reader.singleton("case_count"), "case_count");
        RunIdentity run = new RunIdentity(reader.singleton("run_id"),
                parseUuid(reader.singleton("run_nonce"), "run_nonce"),
                reader.singleton("build_git_sha"), reader.singleton("runtime_content_sha256"),
                unescape(reader.singleton("minecraft_version")), reader.singleton("rig3a_catalog_hash"),
                reader.singleton("topology_catalog_hash"), reader.singleton("rig3b1_execution_identity"),
                reader.singleton("painting_registry_hash"), reader.singleton("universe_hash"),
                reader.singleton("plan_hash"), unescape(reader.singleton("semantic_page_id")),
                parseInt(reader.singleton("route_index"), "route_index"),
                parseInt(reader.singleton("topology_index"), "topology_index"),
                selectorPage, caseCount,
                parseBoolean(reader.singleton("frozen_dy_enabled"), "frozen_dy_enabled"),
                parsePosition(reader.singleton("base")), unescape(reader.singleton("facing")));
        Phase phase = parseEnum(Phase.class, reader.singleton("phase"), "phase");
        int nextCase = parseInt(reader.singleton("next_case_ordinal"), "next_case_ordinal");
        ArtifactLinks artifacts = new ArtifactLinks(reader.singleton("planned_artifact"),
                reader.singleton("immediate_artifact"), reader.singleton("final_artifact"),
                reader.singleton("cleared_artifact"));
        String processEpoch = reader.singleton("scheduler_process_epoch");
        long generation = parseLong(reader.singleton("scheduler_generation"), "scheduler_generation");
        boolean clearStarted = parseBoolean(reader.singleton("clear_started"), "clear_started");
        int entityCursor = parseInt(reader.singleton("clear_entity_cursor"), "clear_entity_cursor");
        int attachmentCursor = parseInt(reader.singleton("clear_attachment_cursor"),
                "clear_attachment_cursor");
        int cellCursor = parseInt(reader.singleton("clear_cell_cursor"), "clear_cell_cursor");
        String detail = unescape(reader.singleton("detail"));

        List<Position> reserved = reader.repeated("reserved_cell", 2).stream()
                .map(row -> parsePosition(row[1])).toList();
        List<Position> planned = reader.repeated("planned_authored_cell", 2).stream()
                .map(row -> parsePosition(row[1])).toList();
        List<CellOwnership> authored = reader.repeated("authored_cell", 3).stream()
                .map(row -> new CellOwnership(parsePosition(row[1]), row[2])).toList();
        List<AttachmentOwnership> attachments = reader.repeated("authored_attachment", 3).stream()
                .map(row -> new AttachmentOwnership(parsePosition(row[1]), row[2])).toList();
        List<CaseState> cases = reader.repeated("case", 8).stream().map(row -> new CaseState(
                parseInt(row[1], "case ordinal"), unescape(row[2]), unescape(row[3]),
                row[4], parseEnum(CasePhase.class, row[5], "case phase"),
                parseEnum(CaseOutcome.class, row[6], "case outcome"), row[7])).toList();
        List<EntityOwnership> entities = reader.repeated("entity", 16).stream().map(row ->
                new EntityOwnership(parseUuid(row[1], "entity uuid"),
                        parseEnum(EntityRole.class, row[2], "entity role"), unescape(row[3]),
                        parseInt(row[4], "entity case ordinal"), unescape(row[5]), row[6],
                        parseEnum(Acquisition.class, row[7], "acquisition"),
                        parseEnum(PreclaimDecision.class, row[8], "preclaim decision"),
                        parseEnum(EntityDisposition.class, row[9], "entity disposition"), row[10],
                        row[11], parseVec(row[12]), parseBox(row[13]), row[14],
                        unescape(row[15]))).toList();
        List<TickCredit> credits = reader.repeated("tick_credit", 6).stream().map(row ->
                new TickCredit(parseUuid(row[1], "tick painting uuid"),
                        parseInt(row[2], "observed entity ticks"),
                        parseBoolean(row[3], "tick loaded"), parseInt(row[4], "unload resets"),
                        parseLong(row[5], "last observed entity tick"))).toList();
        Scheduler scheduler = new Scheduler(processEpoch, generation, credits);
        ClearProgress clear = new ClearProgress(clearStarted,
                parseUuidRows(reader, "clear_requested_entity"), entityCursor,
                parseUuidRows(reader, "clear_removed_entity"),
                parseUuidRows(reader, "clear_absent_entity"),
                parseUuidRows(reader, "clear_refused_entity"),
                parsePositionRows(reader, "clear_requested_attachment"), attachmentCursor,
                parsePositionRows(reader, "clear_cleared_attachment"),
                parsePositionRows(reader, "clear_absent_attachment"),
                parsePositionRows(reader, "clear_refused_attachment"),
                parsePositionRows(reader, "clear_requested_cell"), cellCursor,
                parsePositionRows(reader, "clear_cleared_cell"),
                parsePositionRows(reader, "clear_absent_cell"),
                parsePositionRows(reader, "clear_refused_cell"));
        reader.rejectUnknown();

        State state = new State(format, stateHash, sequence, predecessor, owner, run, phase, nextCase,
                reserved, planned, authored, attachments, cases, entities, scheduler, clear,
                artifacts, detail);
        validateSelf(state);
        if (!canonicalTsv.equals(canonicalTsv(state))) {
            throw new IllegalArgumentException("state bytes are valid-looking but not canonical");
        }
        return state;
    }

    public static State parse(byte[] canonicalBytes) {
        Objects.requireNonNull(canonicalBytes, "canonicalBytes");
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(canonicalBytes)).toString();
            return parse(decoded);
        } catch (CharacterCodingException malformed) {
            throw new IllegalArgumentException("state bytes are not strict UTF-8", malformed);
        }
    }

    public static void validateSelf(State state) {
        Objects.requireNonNull(state, "state");
        requireSha256(state.stateHash, "stateHash");
        if (state.sequence == 0 && !NO_PREDECESSOR.equals(state.predecessorHash)
                || state.sequence > 0 && !isSha256(state.predecessorHash)) {
            throw new IllegalArgumentException("state predecessor/sequence mismatch");
        }
        if (state.sequence == 0 && state.phase != Phase.PLANNED) {
            throw new IllegalArgumentException("ledger genesis must be PLANNED");
        }
        if (!state.owner.key(state.format).equals(state.ownerKey())) {
            throw new IllegalArgumentException("owner key mismatch");
        }
        if (state.format.legacy && (state.run.selectorPage != LEGACY_SELECTOR_PAGE
                || state.run.caseCount != LEGACY_CASE_COUNT)) {
            throw new IllegalArgumentException(
                    "legacy v1 state must remain selector page 1 with exactly 16 cases");
        }
        requireUnique(state.reservedCells, "reserved cell");
        requireUnique(state.plannedAuthoredCells, "planned authored cell");
        if (!new HashSet<>(state.reservedCells).containsAll(state.plannedAuthoredCells)) {
            throw new IllegalArgumentException("planned authored cells must be reserved");
        }
        requireUnique(state.authoredCells.stream().map(CellOwnership::pos).toList(), "authored cell");
        requireUnique(state.authoredAttachments.stream().map(AttachmentOwnership::pos).toList(),
                "authored attachment");
        if (!new HashSet<>(state.plannedAuthoredCells).containsAll(
                state.authoredCells.stream().map(CellOwnership::pos).toList())) {
            throw new IllegalArgumentException("confirmed authored cell was not planned");
        }
        Set<Position> authoredCellPositions = state.authoredCells.stream()
                .map(CellOwnership::pos).collect(java.util.stream.Collectors.toSet());
        Set<Position> authoredAttachmentPositions = state.authoredAttachments.stream()
                .map(AttachmentOwnership::pos).collect(java.util.stream.Collectors.toSet());
        if (!authoredCellPositions.equals(authoredAttachmentPositions)) {
            throw new IllegalArgumentException(
                    "every confirmed cell requires one same-position attachment receipt");
        }
        validateCases(state);
        validateEntities(state);
        validateScheduler(state);
        validatePhasePayload(state);
        String expected = sha256(canonicalBody(state));
        if (!expected.equals(state.stateHash)) {
            throw new IllegalArgumentException("state hash does not match canonical body");
        }
    }

    /** Strict predecessor/no-replay/ownership transition gate used by every store append. */
    public static void validateTransition(State previous, State current) {
        validateSelf(previous);
        validateSelf(current);
        if (current.sequence != previous.sequence + 1
                || !current.predecessorHash.equals(previous.stateHash)
                || !current.owner.equals(previous.owner)
                || current.format != previous.format) {
            throw new IllegalArgumentException(
                    "state successor sequence/predecessor/owner/format mismatch");
        }
        if (previous.phase == Phase.CLEARED && current.phase == Phase.PLANNED) {
            if (previous.run.runId.equals(current.run.runId)) {
                throw new IllegalArgumentException("new run after clear must have a new run ID");
            }
            return;
        }
        if (!previous.run.equals(current.run)
                || !previous.reservedCells.equals(current.reservedCells)
                || !previous.plannedAuthoredCells.equals(current.plannedAuthoredCells)
                || !sameCaseIdentity(previous.cases, current.cases)) {
            throw new IllegalArgumentException("active run immutable identity changed");
        }
        if (!allowedPhase(previous.phase, current.phase)) {
            throw new IllegalArgumentException("invalid phase transition " + previous.phase
                    + " -> " + current.phase);
        }
        if (!isStrictSupersetOrSame(previous.authoredCells, current.authoredCells)
                || !isStrictSupersetOrSame(previous.authoredAttachments, current.authoredAttachments)) {
            throw new IllegalArgumentException("confirmed fixture ownership regressed or changed fingerprint");
        }
        boolean fixtureOwnershipChanged = !previous.authoredCells.equals(current.authoredCells)
                || !previous.authoredAttachments.equals(current.authoredAttachments);
        boolean fixtureGrowthAllowed = previous.phase == Phase.FIXTURE_AUTHORING
                && (current.phase == Phase.FIXTURE_AUTHORING
                || current.phase == Phase.FIXTURE_READY);
        if (fixtureOwnershipChanged && !fixtureGrowthAllowed) {
            throw new IllegalArgumentException(
                    "confirmed fixture authority may grow only during fixture authoring");
        }
        if (current.phase == Phase.QUARANTINED
                && (current.nextCaseOrdinal != previous.nextCaseOrdinal
                || !current.authoredCells.equals(previous.authoredCells)
                || !current.authoredAttachments.equals(previous.authoredAttachments)
                || !current.cases.equals(previous.cases)
                || !current.scheduler.equals(previous.scheduler)
                || !current.artifacts.equals(previous.artifacts))) {
            throw new IllegalArgumentException(
                    "quarantine freezes case, fixture, scheduler, and named-artifact state");
        }
        if (!isClearPhase(previous.phase) && current.phase == Phase.CLEARING_ENTITIES
                && (current.nextCaseOrdinal != previous.nextCaseOrdinal
                || !current.authoredCells.equals(previous.authoredCells)
                || !current.authoredAttachments.equals(previous.authoredAttachments)
                || !current.cases.equals(previous.cases)
                || !current.entities.equals(previous.entities)
                || !current.scheduler.equals(previous.scheduler)
                || !current.artifacts.equals(previous.artifacts)
                || !current.clear.equals(ClearProgress.begin(previous)))) {
            throw new IllegalArgumentException(
                    "clear entry may only freeze exact authority and initialize clear progress");
        }
        if (isClearPhase(previous.phase)) {
            boolean clearedArtifactOnly = current.phase == Phase.CLEARED
                    && previous.artifacts.planned.equals(current.artifacts.planned)
                    && previous.artifacts.immediate.equals(current.artifacts.immediate)
                    && previous.artifacts.finalArtifact.equals(current.artifacts.finalArtifact)
                    && !NO_VALUE.equals(current.artifacts.cleared);
            if (current.nextCaseOrdinal != previous.nextCaseOrdinal
                    || !current.authoredCells.equals(previous.authoredCells)
                    || !current.authoredAttachments.equals(previous.authoredAttachments)
                    || !current.cases.equals(previous.cases)
                    || !current.entities.equals(previous.entities)
                    || !current.scheduler.equals(previous.scheduler)
                    || !previous.artifacts.equals(current.artifacts) && !clearedArtifactOnly) {
                throw new IllegalArgumentException(
                        "clear phases freeze execution evidence and exact authority");
            }
        }
        validateCaseProgression(previous.cases, current.cases);
        validateEntityProgression(previous, current);
        validateSchedulerProgression(previous, current);
        validateArtifactProgression(previous, current);
        if (current.nextCaseOrdinal < previous.nextCaseOrdinal) {
            throw new IllegalArgumentException("case cursor regressed");
        }
        if (!clearProgressionValid(previous, current)) {
            throw new IllegalArgumentException("clear progress regressed or changed requests/results");
        }
        if (samePhaseOwnershipUpdate(previous, current)) {
            return;
        }
        if (previous.phase == current.phase && previous.equals(current)) {
            throw new IllegalArgumentException("state transition must change durable content");
        }
    }

    /** Store publication gate: old chains reconstruct fully, but new v1 writes are exact-clear-only. */
    public static void validateAppendTransition(State previous, State current) {
        validateTransition(previous, current);
        if (previous.format.legacy && !legacyClearOnlySuccessor(previous, current)) {
            throw new IllegalArgumentException(
                    "legacy v1 ledgers permit only exact-clear successors");
        }
    }

    private static boolean allowedPhase(Phase from, Phase to) {
        if (from == to) {
            return from == Phase.FIXTURE_AUTHORING || from == Phase.CASE_IN_FLIGHT
                    || from == Phase.WAITING_DELAYED || from == Phase.FINAL
                    || from == Phase.QUARANTINED
                    || from == Phase.CLEARING_ENTITIES
                    || from == Phase.CLEARING_ATTACHMENTS || from == Phase.CLEARING_CELLS;
        }
        return switch (from) {
            case PLANNED -> to == Phase.FIXTURE_AUTHORING || to == Phase.QUARANTINED
                    || to == Phase.CLEARING_ENTITIES;
            case FIXTURE_AUTHORING -> to == Phase.FIXTURE_READY || to == Phase.QUARANTINED
                    || to == Phase.CLEARING_ENTITIES;
            case FIXTURE_READY -> to == Phase.CASE_IN_FLIGHT || to == Phase.QUARANTINED
                    || to == Phase.CLEARING_ENTITIES;
            case CASE_IN_FLIGHT -> to == Phase.IMMEDIATE_PARTIAL || to == Phase.IMMEDIATE
                    || to == Phase.QUARANTINED || to == Phase.CLEARING_ENTITIES;
            case IMMEDIATE_PARTIAL -> to == Phase.CASE_IN_FLIGHT || to == Phase.QUARANTINED
                    || to == Phase.CLEARING_ENTITIES;
            case IMMEDIATE -> to == Phase.WAITING_DELAYED || to == Phase.QUARANTINED
                    || to == Phase.CLEARING_ENTITIES;
            case WAITING_DELAYED -> to == Phase.FINAL || to == Phase.QUARANTINED
                    || to == Phase.CLEARING_ENTITIES;
            case FINAL -> to == Phase.QUARANTINED || to == Phase.CLEARING_ENTITIES;
            case QUARANTINED -> to == Phase.CLEARING_ENTITIES;
            case CLEARING_ENTITIES -> to == Phase.CLEARING_ATTACHMENTS || to == Phase.QUARANTINED;
            case CLEARING_ATTACHMENTS -> to == Phase.CLEARING_CELLS || to == Phase.QUARANTINED;
            case CLEARING_CELLS -> to == Phase.CLEARED || to == Phase.QUARANTINED;
            case CLEARED -> false;
        };
    }

    private static boolean legacyClearOnlySuccessor(State previous, State current) {
        if (isClearPhase(previous.phase)) {
            return isClearPhase(current.phase) || current.phase == Phase.QUARANTINED;
        }
        if (previous.phase == Phase.QUARANTINED) {
            return current.phase == Phase.CLEARING_ENTITIES;
        }
        return current.phase == Phase.CLEARING_ENTITIES;
    }

    private static void validateCases(State state) {
        int caseCount = state.run.caseCount;
        if (state.cases.size() != caseCount) {
            throw new IllegalArgumentException(
                    "direct page case list must equal persisted caseCount " + caseCount);
        }
        for (int ordinal = 0; ordinal < caseCount; ordinal++) {
            if (state.cases.get(ordinal).ordinal != ordinal) {
                throw new IllegalArgumentException(
                        "case ordinals must be contiguous within persisted caseCount");
            }
        }
        requireUnique(state.cases.stream().map(CaseState::attemptId).toList(), "attempt ID");
        int immediate = 0;
        boolean inFlight = false;
        for (CaseState entry : state.cases) {
            if (entry.phase == CasePhase.IMMEDIATE) {
                if (inFlight || entry.ordinal != immediate) {
                    throw new IllegalArgumentException("IMMEDIATE cases must be one contiguous prefix");
                }
                immediate++;
            } else if (entry.phase == CasePhase.IN_FLIGHT) {
                if (inFlight || entry.ordinal != immediate) {
                    throw new IllegalArgumentException("only the first non-immediate case may be in flight");
                }
                inFlight = true;
            } else if (entry.ordinal < immediate || inFlight && entry.ordinal == immediate) {
                throw new IllegalArgumentException("case status ordering is not replay-safe");
            }
        }
        int expectedCursor = immediate;
        if (state.nextCaseOrdinal != expectedCursor) {
            throw new IllegalArgumentException("next case cursor disagrees with immediate prefix");
        }
    }

    private static void validatePhasePayload(State state) {
        int caseCount = state.run.caseCount;
        int immediate = (int) state.cases.stream().filter(c -> c.phase == CasePhase.IMMEDIATE).count();
        long inFlight = state.cases.stream().filter(c -> c.phase == CasePhase.IN_FLIGHT).count();
        boolean fixtureComplete = state.authoredCells.size() == state.plannedAuthoredCells.size();
        switch (state.phase) {
            case PLANNED -> requirePhase(immediate == 0 && inFlight == 0 && state.authoredCells.isEmpty(),
                    "PLANNED must precede fixture authorship");
            case FIXTURE_AUTHORING -> requirePhase(immediate == 0 && inFlight == 0 && !fixtureComplete,
                    "FIXTURE_AUTHORING must remain incomplete");
            case FIXTURE_READY -> requirePhase(fixtureComplete && immediate == 0 && inFlight == 0,
                    "FIXTURE_READY requires every planned authored fingerprint");
            case CASE_IN_FLIGHT -> requirePhase(fixtureComplete && inFlight == 1,
                    "CASE_IN_FLIGHT requires exactly one in-flight case");
            case IMMEDIATE_PARTIAL -> requirePhase(fixtureComplete && inFlight == 0
                            && immediate > 0 && immediate < caseCount,
                    "IMMEDIATE_PARTIAL requires a non-terminal immediate prefix");
            case IMMEDIATE -> requirePhase(fixtureComplete && inFlight == 0 && immediate == caseCount
                            && !NO_VALUE.equals(state.artifacts.immediate)
                            && state.scheduler.equals(Scheduler.inactive()),
                    "IMMEDIATE requires every persisted case and an explicit artifact");
            case WAITING_DELAYED -> requirePhase(immediate == caseCount
                            && !NO_VALUE.equals(state.scheduler.processEpoch),
                    "WAITING_DELAYED requires all immediate cases and an armed scheduler");
            case FINAL -> requirePhase(immediate == caseCount
                            && !NO_VALUE.equals(state.artifacts.finalArtifact)
                            && finalTickGateSatisfied(state),
                    "FINAL requires a final artifact and per-survivor 102-tick proof");
            case QUARANTINED -> {
                // Any replay-safe prefix is retainable; only clear may succeed it.
            }
            case CLEARING_ENTITIES -> requirePhase(state.clear.started,
                    "CLEARING_ENTITIES requires an initialized exact clear receipt");
            case CLEARING_ATTACHMENTS -> requirePhase(state.clear.entitiesCompleteWithoutRefusal(),
                    "attachment clear requires clean entity completion");
            case CLEARING_CELLS -> requirePhase(state.clear.entitiesCompleteWithoutRefusal()
                            && state.clear.attachmentsCompleteWithoutRefusal(),
                    "cell clear requires entity+attachment completion");
            case CLEARED -> requirePhase(state.clear.entitiesCompleteWithoutRefusal()
                            && state.clear.attachmentsCompleteWithoutRefusal()
                            && state.clear.cellsCompleteWithoutRefusal()
                            && !NO_VALUE.equals(state.artifacts.cleared),
                    "CLEARED requires exact completed partitions and artifact");
        }
        if (state.phase.ordinal() <= Phase.IMMEDIATE.ordinal()
                && !state.scheduler.equals(Scheduler.inactive())) {
            throw new IllegalArgumentException("scheduler must remain inactive through IMMEDIATE");
        }
        if (state.phase == Phase.CASE_IN_FLIGHT || state.phase == Phase.IMMEDIATE_PARTIAL
                || state.phase == Phase.IMMEDIATE || state.phase == Phase.WAITING_DELAYED
                || state.phase == Phase.FINAL) {
            validateImmediatePaintingOwnership(state);
        }
        if (!isClearPhase(state.phase) && !state.clear.isNone()) {
            throw new IllegalArgumentException("non-clear phase cannot carry clear progress");
        }
        if (isClearPhase(state.phase)) {
            Set<UUID> entityAuthority = state.entities.stream().map(EntityOwnership::uuid)
                    .collect(java.util.stream.Collectors.toSet());
            Set<Position> attachmentAuthority = state.authoredAttachments.stream()
                    .map(AttachmentOwnership::pos).collect(java.util.stream.Collectors.toSet());
            Set<Position> cellAuthority = state.authoredCells.stream().map(CellOwnership::pos)
                    .collect(java.util.stream.Collectors.toSet());
            if (!entityAuthority.equals(new HashSet<>(state.clear.requestedEntities))
                    || !attachmentAuthority.equals(new HashSet<>(state.clear.requestedAttachments))
                    || !cellAuthority.equals(new HashSet<>(state.clear.requestedCells))) {
                throw new IllegalArgumentException("clear requests exceed or omit exact durable authority");
            }
        }
    }

    private static void validateImmediatePaintingOwnership(State state) {
        for (CaseState entry : state.cases) {
            if (entry.phase != CasePhase.IMMEDIATE) {
                continue;
            }
            List<EntityOwnership> paintings = state.entities.stream()
                    .filter(entity -> entity.role == EntityRole.PAINTING
                            && entity.caseOrdinal == entry.ordinal)
                    .toList();
            if (entry.outcome == CaseOutcome.VANILLA_REFUSAL) {
                if (!paintings.isEmpty()) {
                    throw new IllegalArgumentException(
                            "vanilla refusal cannot own a painting UUID");
                }
                continue;
            }
            if (entry.outcome != CaseOutcome.PLACED || paintings.size() != 1
                    || paintings.getFirst().acquisition != Acquisition.LOADED
                    || paintings.getFirst().disposition != EntityDisposition.IN_WORLD
                    && paintings.getFirst().disposition != EntityDisposition.REMOVED) {
                throw new IllegalArgumentException(
                        "placed immediate case requires exactly one confirmed in-world/removed painting");
            }
        }
    }

    private static boolean finalTickGateSatisfied(State state) {
        Map<UUID, TickCredit> credits = new HashMap<>();
        for (TickCredit credit : state.scheduler.credits) {
            credits.put(credit.paintingUuid, credit);
        }
        for (EntityOwnership entity : state.entities) {
            if (entity.role != EntityRole.PAINTING) {
                continue;
            }
            if (entity.disposition == EntityDisposition.REMOVED) {
                long causalDrops = causalDropCount(state, entity.uuid);
                if (entity.removalCause() == RemovalCause.SUPPORT_LOSS_NO_DROP
                        && causalDrops == 0
                        || entity.removalCause() == RemovalCause.SUPPORT_LOSS_DROP_EXPECTED
                        && causalDrops == 1) {
                    continue;
                }
            }
            TickCredit credit = credits.get(entity.uuid);
            if (entity.disposition != EntityDisposition.IN_WORLD || credit == null
                    || !credit.loaded || credit.observedEntityTicks < REQUIRED_ENTITY_TICKS) {
                return false;
            }
        }
        return true;
    }

    private static long causalDropCount(State state, UUID paintingUuid) {
        String source = paintingUuid.toString();
        return state.entities.stream().filter(entity -> entity.role == EntityRole.DROPPED_ITEM
                && entity.sourcePaintingUuid.equals(source)).count();
    }

    private static void validateEntities(State state) {
        requireUnique(state.entities.stream().map(EntityOwnership::uuid).toList(), "owned entity UUID");
        Map<Integer, CaseState> cases = new HashMap<>();
        for (CaseState entry : state.cases) {
            cases.put(entry.ordinal, entry);
        }
        Map<UUID, EntityOwnership> paintings = new HashMap<>();
        for (EntityOwnership entity : state.entities) {
            CaseState owner = cases.get(entity.caseOrdinal);
            if (owner == null || !owner.attemptId.equals(entity.attemptId)) {
                throw new IllegalArgumentException("entity ownership does not bind its exact attempt");
            }
            if (entity.role == EntityRole.PAINTING) {
                paintings.put(entity.uuid, entity);
                if (owner.phase == CasePhase.PENDING) {
                    throw new IllegalArgumentException("pending case cannot own a painting");
                }
            }
        }
        for (EntityOwnership entity : state.entities) {
            if (entity.role != EntityRole.DROPPED_ITEM) {
                continue;
            }
            EntityOwnership source = paintings.get(UUID.fromString(entity.sourcePaintingUuid));
            if (source == null || source.caseOrdinal != entity.caseOrdinal
                    || !source.attemptId.equals(entity.attemptId)
                    || source.disposition != EntityDisposition.REMOVED) {
                throw new IllegalArgumentException(
                        "dropped item must bind its exact causally removed source painting");
            }
        }
        boolean unsafeRemoval = state.entities.stream()
                .filter(entity -> entity.role == EntityRole.PAINTING
                        && entity.disposition == EntityDisposition.REMOVED)
                .anyMatch(entity -> entity.removalCause() == RemovalCause.INTERFERENCE
                        || entity.removalCause() == RemovalCause.UNEXPLAINED);
        boolean incompleteSupportBoundary = state.entities.stream()
                .filter(entity -> entity.role == EntityRole.PAINTING
                        && entity.disposition == EntityDisposition.REMOVED)
                .anyMatch(entity -> entity.removalCause() == RemovalCause.SUPPORT_LOSS_DROP_EXPECTED
                        && causalDropCount(state, entity.uuid) != 1
                        || entity.removalCause() == RemovalCause.SUPPORT_LOSS_NO_DROP
                        && causalDropCount(state, entity.uuid) != 0);
        if ((unsafeRemoval || incompleteSupportBoundary)
                && state.phase != Phase.QUARANTINED && !isClearPhase(state.phase)) {
            throw new IllegalArgumentException(
                    "unsafe/incomplete painting removal boundary must be quarantined");
        }
    }

    private static void validateScheduler(State state) {
        Set<UUID> paintingUuids = state.entities.stream()
                .filter(entity -> entity.role == EntityRole.PAINTING)
                .map(EntityOwnership::uuid).collect(java.util.stream.Collectors.toSet());
        for (TickCredit credit : state.scheduler.credits) {
            if (!paintingUuids.contains(credit.paintingUuid)) {
                throw new IllegalArgumentException("scheduler credits an unowned painting");
            }
            if (!credit.loaded && credit.observedEntityTicks != 0) {
                throw new IllegalArgumentException("unloaded painting must reset entity-tick credit to zero");
            }
        }
    }

    private static void validateCaseProgression(List<CaseState> before, List<CaseState> after) {
        for (int i = 0; i < before.size(); i++) {
            CaseState old = before.get(i);
            CaseState next = after.get(i);
            if (!sameCaseIdentity(old, next)) {
                throw new IllegalArgumentException("case identity changed");
            }
            if (old.phase == CasePhase.IMMEDIATE && next.phase != CasePhase.IMMEDIATE
                    || old.phase == CasePhase.IN_FLIGHT && next.phase == CasePhase.PENDING
                    || old.phase == CasePhase.PENDING && next.phase == CasePhase.IMMEDIATE) {
                throw new IllegalArgumentException("case replay/skip transition at ordinal " + i);
            }
            if (old.phase == CasePhase.IMMEDIATE
                    && (!old.immediateObservationId.equals(next.immediateObservationId)
                    || old.outcome != next.outcome)) {
                throw new IllegalArgumentException("immediate evidence changed at ordinal " + i);
            }
        }
    }

    private static void validateEntityProgression(State previous, State current) {
        Map<UUID, EntityOwnership> oldByUuid = new LinkedHashMap<>();
        for (EntityOwnership entity : previous.entities) {
            oldByUuid.put(entity.uuid, entity);
        }
        Map<UUID, EntityOwnership> next = new LinkedHashMap<>();
        for (EntityOwnership entity : current.entities) {
            next.put(entity.uuid, entity);
        }
        for (EntityOwnership old : previous.entities) {
            EntityOwnership changed = next.get(old.uuid);
            if (changed == null) {
                throw new IllegalArgumentException("owned entity UUID disappeared from durable state");
            }
            if (old.equals(changed)) {
                continue;
            }
            boolean loadConfirmation = old.role == EntityRole.PAINTING
                    && old.acquisition == Acquisition.PRECLAIMED
                    && old.disposition == EntityDisposition.PREINSERTION
                    && changed.equals(old.loaded());
            boolean dispositionOnly = sameEntityIdentity(old, changed)
                    && allowedDisposition(old.disposition, changed.disposition);
            if (!loadConfirmation && !dispositionOnly) {
                throw new IllegalArgumentException("owned entity identity changed for " + old.uuid);
            }
        }
        for (EntityOwnership added : current.entities) {
            if (previous.entityUuidSet().contains(added.uuid)) {
                continue;
            }
            if (added.acquisition != Acquisition.PRECLAIMED
                    && added.acquisition != Acquisition.DROP_PRECLAIM) {
                throw new IllegalArgumentException("new entity ownership must begin as a preclaim");
            }
            if (added.role == EntityRole.PAINTING) {
                CaseState owner = current.cases.get(added.caseOrdinal);
                if (previous.phase != Phase.CASE_IN_FLIGHT
                        || current.phase != Phase.CASE_IN_FLIGHT
                        || owner.phase != CasePhase.IN_FLIGHT
                        || owner.ordinal != current.nextCaseOrdinal) {
                    throw new IllegalArgumentException(
                            "painting preclaim must bind the exact current in-flight case");
                }
            } else {
                EntityOwnership oldSource = oldByUuid.get(
                        UUID.fromString(added.sourcePaintingUuid));
                EntityOwnership newSource = next.get(
                        UUID.fromString(added.sourcePaintingUuid));
                boolean allowedPhase = current.phase == Phase.WAITING_DELAYED
                        || current.phase == Phase.FINAL || current.phase == Phase.QUARANTINED;
                if (!allowedPhase || oldSource == null || newSource == null
                        || oldSource.disposition != EntityDisposition.IN_WORLD
                        || newSource.disposition != EntityDisposition.REMOVED) {
                    throw new IllegalArgumentException(
                            "drop preclaim must be atomic with its owned source removal");
                }
            }
        }
    }

    private static void validateSchedulerProgression(State previous, State current) {
        Scheduler before = previous.scheduler;
        Scheduler after = current.scheduler;
        if (before.equals(after)) {
            return;
        }
        if (previous.phase == Phase.IMMEDIATE && current.phase == Phase.WAITING_DELAYED) {
            Set<UUID> expected = current.entities.stream()
                    .filter(entity -> entity.role == EntityRole.PAINTING
                            && entity.acquisition == Acquisition.LOADED
                            && entity.disposition == EntityDisposition.IN_WORLD)
                    .map(EntityOwnership::uuid).collect(java.util.stream.Collectors.toSet());
            Set<UUID> actual = after.credits.stream().map(TickCredit::paintingUuid)
                    .collect(java.util.stream.Collectors.toSet());
            if (after.generation != 1 || NO_VALUE.equals(after.processEpoch)
                    || !actual.equals(expected)
                    || after.credits.stream().anyMatch(credit -> credit.observedEntityTicks != 0)) {
                throw new IllegalArgumentException("first delayed scheduler must arm generation 1");
            }
            return;
        }
        if (previous.phase != Phase.WAITING_DELAYED
                || current.phase != Phase.WAITING_DELAYED && current.phase != Phase.FINAL) {
            throw new IllegalArgumentException("scheduler changed outside delayed observation");
        }
        if (after.generation == before.generation + 1) {
            if (after.processEpoch.equals(before.processEpoch)
                    || after.credits.stream().anyMatch(credit -> credit.observedEntityTicks != 0)
                    || !after.credits.stream().map(TickCredit::paintingUuid).collect(
                    java.util.stream.Collectors.toSet()).equals(
                    before.credits.stream().map(TickCredit::paintingUuid).collect(
                            java.util.stream.Collectors.toSet()))) {
                throw new IllegalArgumentException("reconstructed scheduler must use a fresh zero-credit epoch");
            }
            return;
        }
        if (after.generation != before.generation
                || !after.processEpoch.equals(before.processEpoch)) {
            throw new IllegalArgumentException("scheduler generation/epoch jumped");
        }
        Map<UUID, TickCredit> oldCredits = new HashMap<>();
        before.credits.forEach(credit -> oldCredits.put(credit.paintingUuid, credit));
        if (!after.credits.stream().map(TickCredit::paintingUuid).collect(
                java.util.stream.Collectors.toSet()).equals(oldCredits.keySet())) {
            throw new IllegalArgumentException("scheduler credit UUID set changed within one epoch");
        }
        for (TickCredit credit : after.credits) {
            TickCredit old = oldCredits.get(credit.paintingUuid);
            if (credit.equals(old)) {
                continue;
            }
            long observedDelta = (long) credit.observedEntityTicks - old.observedEntityTicks;
            long rawTickDelta = credit.lastObservedEntityTick - old.lastObservedEntityTick;
            boolean forward = credit.loaded && old.loaded
                    && credit.observedEntityTicks >= old.observedEntityTicks
                    && credit.unloadResets == old.unloadResets
                    && credit.lastObservedEntityTick >= old.lastObservedEntityTick
                    && observedDelta == rawTickDelta;
            boolean reset = old.loaded && !credit.loaded && credit.observedEntityTicks == 0
                    && credit.unloadResets == old.unloadResets + 1;
            boolean reload = credit.loaded && !old.loaded && credit.observedEntityTicks == 0
                    && credit.unloadResets == old.unloadResets;
            if (!forward && !reset && !reload) {
                throw new IllegalArgumentException("entity-tick credit regressed without unload reset");
            }
        }
    }

    private static void validateArtifactProgression(State previous, State current) {
        ArtifactLinks before = previous.artifacts;
        ArtifactLinks after = current.artifacts;
        if (!before.planned.equals(after.planned)) {
            throw new IllegalArgumentException("planned artifact identity changed");
        }
        validateOneWayArtifact(before.immediate, after.immediate,
                current.phase == Phase.IMMEDIATE, "IMMEDIATE");
        validateOneWayArtifact(before.finalArtifact, after.finalArtifact,
                current.phase == Phase.FINAL, "FINAL");
        validateOneWayArtifact(before.cleared, after.cleared,
                current.phase == Phase.CLEARED, "CLEARED");
    }

    private static void validateOneWayArtifact(String before, String after,
                                               boolean mayAppear, String label) {
        if (before.equals(after)) {
            return;
        }
        if (!NO_VALUE.equals(before) || NO_VALUE.equals(after) || !mayAppear) {
            throw new IllegalArgumentException(label + " artifact changed outside its named phase");
        }
    }

    private static boolean allowedDisposition(EntityDisposition from, EntityDisposition to) {
        return from == EntityDisposition.IN_WORLD
                && (to == EntityDisposition.REMOVED || to == EntityDisposition.PICKED_UP
                || to == EntityDisposition.MERGED || to == EntityDisposition.TRANSFER_AMBIGUOUS);
    }

    private static boolean sameEntityIdentity(EntityOwnership a, EntityOwnership b) {
        return a.uuid.equals(b.uuid) && a.role == b.role && a.expectedType.equals(b.expectedType)
                && a.caseOrdinal == b.caseOrdinal && a.attemptId.equals(b.attemptId)
                && a.sourcePaintingUuid.equals(b.sourcePaintingUuid)
                && a.acquisition == b.acquisition && a.decision == b.decision
                && a.fingerprint.equals(b.fingerprint)
                && a.evidenceArtifact.equals(b.evidenceArtifact)
                && a.position.equals(b.position)
                && a.aabb.equals(b.aabb);
    }

    private static boolean samePhaseOwnershipUpdate(State previous, State current) {
        return previous.phase == current.phase
                && previous.nextCaseOrdinal == current.nextCaseOrdinal
                && previous.authoredCells.equals(current.authoredCells)
                && previous.authoredAttachments.equals(current.authoredAttachments)
                && previous.cases.equals(current.cases)
                && previous.scheduler.equals(current.scheduler)
                && previous.clear.equals(current.clear)
                && previous.artifacts.equals(current.artifacts)
                && current.entities.size() >= previous.entities.size();
    }

    private static boolean clearProgressionValid(State previous, State current) {
        if (previous.clear.isNone()) {
            return current.clear.isNone() || current.phase == Phase.CLEARING_ENTITIES;
        }
        if (current.clear.isNone()) {
            return false;
        }
        return previous.clear.requestedEntities.equals(current.clear.requestedEntities)
                && previous.clear.requestedAttachments.equals(current.clear.requestedAttachments)
                && previous.clear.requestedCells.equals(current.clear.requestedCells)
                && current.clear.entityCursor >= previous.clear.entityCursor
                && current.clear.attachmentCursor >= previous.clear.attachmentCursor
                && current.clear.cellCursor >= previous.clear.cellCursor
                && current.clear.removedEntities.containsAll(previous.clear.removedEntities)
                && current.clear.absentEntities.containsAll(previous.clear.absentEntities)
                && current.clear.refusedEntities.containsAll(previous.clear.refusedEntities)
                && current.clear.clearedAttachments.containsAll(previous.clear.clearedAttachments)
                && current.clear.absentAttachments.containsAll(previous.clear.absentAttachments)
                && current.clear.refusedAttachments.containsAll(previous.clear.refusedAttachments)
                && current.clear.clearedCells.containsAll(previous.clear.clearedCells)
                && current.clear.absentCells.containsAll(previous.clear.absentCells)
                && current.clear.refusedCells.containsAll(previous.clear.refusedCells);
    }

    private static boolean isClearPhase(Phase phase) {
        return phase == Phase.CLEARING_ENTITIES || phase == Phase.CLEARING_ATTACHMENTS
                || phase == Phase.CLEARING_CELLS || phase == Phase.CLEARED;
    }

    private static String canonicalBody(State state) {
        StringBuilder out = new StringBuilder(32_000);
        append(out, "schema", state.format.schema);
        append(out, "sequence", state.sequence);
        append(out, "predecessor_hash", state.predecessorHash);
        append(out, "execution_contract", state.format.executionContract);
        append(out, "provenance", PROVENANCE);
        append(out, "player_proof", PLAYER_PROOF);
        append(out, "client_proof", CLIENT_PROOF);
        append(out, "owner_key", state.ownerKey());
        append(out, "world_key", state.owner.worldKey);
        append(out, "dimension", escape(state.owner.dimension));
        append(out, "player_uuid", state.owner.playerUuid);
        append(out, "run_id", state.run.runId);
        append(out, "run_nonce", state.run.runNonce);
        append(out, "build_git_sha", state.run.buildGitSha);
        append(out, "runtime_content_sha256", state.run.runtimeContentSha256);
        append(out, "minecraft_version", escape(state.run.minecraftVersion));
        append(out, "rig3a_catalog_hash", state.run.rig3aCatalogHash);
        append(out, "topology_catalog_hash", state.run.topologyCatalogHash);
        append(out, "rig3b1_execution_identity", state.run.rig3b1ExecutionIdentity);
        append(out, "painting_registry_hash", state.run.paintingRegistryHash);
        append(out, "universe_hash", state.run.universeHash);
        append(out, "plan_hash", state.run.planHash);
        append(out, "semantic_page_id", escape(state.run.semanticPageId));
        append(out, "route_index", state.run.routeIndex);
        append(out, "topology_index", state.run.topologyIndex);
        append(out, "selector_page", state.run.selectorPage);
        if (!state.format.legacy) {
            append(out, "case_count", state.run.caseCount);
        }
        append(out, "frozen_dy_enabled", state.run.frozenDyEnabled);
        append(out, "base", position(state.run.base));
        append(out, "facing", escape(state.run.facing));
        append(out, "phase", state.phase);
        append(out, "next_case_ordinal", state.nextCaseOrdinal);
        append(out, "planned_artifact", state.artifacts.planned);
        append(out, "immediate_artifact", state.artifacts.immediate);
        append(out, "final_artifact", state.artifacts.finalArtifact);
        append(out, "cleared_artifact", state.artifacts.cleared);
        append(out, "scheduler_process_epoch", state.scheduler.processEpoch);
        append(out, "scheduler_generation", state.scheduler.generation);
        append(out, "clear_started", state.clear.started);
        append(out, "clear_entity_cursor", state.clear.entityCursor);
        append(out, "clear_attachment_cursor", state.clear.attachmentCursor);
        append(out, "clear_cell_cursor", state.clear.cellCursor);
        append(out, "detail", escape(state.detail));
        appendPositions(out, "reserved_cell", state.reservedCells);
        appendPositions(out, "planned_authored_cell", state.plannedAuthoredCells);
        for (CellOwnership cell : state.authoredCells) {
            out.append("authored_cell\t").append(position(cell.pos)).append('\t')
                    .append(cell.fingerprint).append('\n');
        }
        for (AttachmentOwnership attachment : state.authoredAttachments) {
            out.append("authored_attachment\t").append(position(attachment.pos)).append('\t')
                    .append(attachment.fingerprint).append('\n');
        }
        for (CaseState entry : state.cases) {
            out.append("case\t").append(entry.ordinal).append('\t').append(escape(entry.attemptId))
                    .append('\t').append(escape(entry.selectorId)).append('\t')
                    .append(entry.componentFingerprint).append('\t').append(entry.phase)
                    .append('\t').append(entry.outcome).append('\t')
                    .append(entry.immediateObservationId).append('\n');
        }
        for (EntityOwnership entity : state.entities) {
            out.append("entity\t").append(entity.uuid).append('\t').append(entity.role)
                    .append('\t').append(escape(entity.expectedType)).append('\t')
                    .append(entity.caseOrdinal).append('\t').append(escape(entity.attemptId))
                    .append('\t').append(entity.sourcePaintingUuid).append('\t')
                    .append(entity.acquisition).append('\t').append(entity.decision).append('\t')
                    .append(entity.disposition).append('\t').append(entity.fingerprint).append('\t')
                    .append(entity.evidenceArtifact).append('\t')
                    .append(vec(entity.position)).append('\t').append(box(entity.aabb)).append('\t')
                    .append(entity.transferTargetUuid).append('\t').append(escape(entity.transferDetail))
                    .append('\n');
        }
        for (TickCredit credit : state.scheduler.credits) {
            out.append("tick_credit\t").append(credit.paintingUuid).append('\t')
                    .append(credit.observedEntityTicks).append('\t').append(credit.loaded)
                    .append('\t').append(credit.unloadResets).append('\t')
                    .append(credit.lastObservedEntityTick).append('\n');
        }
        appendUuids(out, "clear_requested_entity", state.clear.requestedEntities);
        appendUuids(out, "clear_removed_entity", state.clear.removedEntities);
        appendUuids(out, "clear_absent_entity", state.clear.absentEntities);
        appendUuids(out, "clear_refused_entity", state.clear.refusedEntities);
        appendPositions(out, "clear_requested_attachment", state.clear.requestedAttachments);
        appendPositions(out, "clear_cleared_attachment", state.clear.clearedAttachments);
        appendPositions(out, "clear_absent_attachment", state.clear.absentAttachments);
        appendPositions(out, "clear_refused_attachment", state.clear.refusedAttachments);
        appendPositions(out, "clear_requested_cell", state.clear.requestedCells);
        appendPositions(out, "clear_cleared_cell", state.clear.clearedCells);
        appendPositions(out, "clear_absent_cell", state.clear.absentCells);
        appendPositions(out, "clear_refused_cell", state.clear.refusedCells);
        return out.toString();
    }

    private static State withComputedHash(State seed) {
        return new State(seed.format, sha256(canonicalBody(seed)), seed.sequence, seed.predecessorHash,
                seed.owner, seed.run, seed.phase, seed.nextCaseOrdinal, seed.reservedCells,
                seed.plannedAuthoredCells, seed.authoredCells, seed.authoredAttachments,
                seed.cases, seed.entities, seed.scheduler, seed.clear, seed.artifacts, seed.detail);
    }

    private static boolean sameCaseIdentity(List<CaseState> a, List<CaseState> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!sameCaseIdentity(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameCaseIdentity(CaseState a, CaseState b) {
        return a.ordinal == b.ordinal && a.attemptId.equals(b.attemptId)
                && a.selectorId.equals(b.selectorId)
                && a.componentFingerprint.equals(b.componentFingerprint);
    }

    private static <T> boolean isStrictSupersetOrSame(List<T> before, List<T> after) {
        return after.containsAll(before);
    }

    private static <T> void validatePartitionPrefix(List<T> requested, int cursor,
                                                    List<T> first, List<T> second, List<T> third,
                                                    String label) {
        if (cursor < 0 || cursor > requested.size()) {
            throw new IllegalArgumentException(label + " clear cursor out of range");
        }
        Set<T> union = new HashSet<>();
        for (List<T> part : List.of(first, second, third)) {
            for (T value : part) {
                if (!union.add(value)) {
                    throw new IllegalArgumentException(label + " clear partitions overlap");
                }
            }
        }
        if (!union.equals(new HashSet<>(requested.subList(0, cursor)))) {
            throw new IllegalArgumentException(label + " clear results must equal processed prefix");
        }
    }

    private static void requirePhase(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static List<Position> normalizedPositions(Collection<Position> values) {
        Objects.requireNonNull(values, "positions");
        List<Position> copy = values.stream().map(value -> Objects.requireNonNull(value, "position"))
                .toList();
        requireUnique(copy, "position");
        return copy.stream().sorted(POSITION_ORDER).toList();
    }

    private static List<UUID> normalizedUuids(Collection<UUID> values) {
        Objects.requireNonNull(values, "UUIDs");
        List<UUID> copy = values.stream().map(value -> Objects.requireNonNull(value, "UUID"))
                .toList();
        requireUnique(copy, "UUID");
        return copy.stream().sorted().toList();
    }

    private static List<Position> normalizedTopDownPositions(Collection<Position> values) {
        Objects.requireNonNull(values, "positions");
        List<Position> copy = values.stream().map(value -> Objects.requireNonNull(value, "position"))
                .toList();
        requireUnique(copy, "position");
        return copy.stream().sorted(Comparator.comparingInt(Position::y).reversed()
                .thenComparingInt(Position::x).thenComparingInt(Position::z)).toList();
    }

    private static <T> void requireUnique(Collection<T> values, String label) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException("duplicate " + label);
        }
    }

    private static void append(StringBuilder out, String key, Object value) {
        out.append(key).append('\t').append(value).append('\n');
    }

    private static void appendPositions(StringBuilder out, String label, List<Position> positions) {
        for (Position pos : positions) {
            out.append(label).append('\t').append(position(pos)).append('\n');
        }
    }

    private static void appendUuids(StringBuilder out, String label, List<UUID> uuids) {
        for (UUID uuid : uuids) {
            out.append(label).append('\t').append(uuid).append('\n');
        }
    }

    private static String position(Position pos) {
        return pos.x + "," + pos.y + "," + pos.z;
    }

    private static String vec(Vec3Bits value) {
        return hex(value.xBits) + ',' + hex(value.yBits) + ',' + hex(value.zBits);
    }

    private static String box(BoxBits value) {
        return hex(value.minXBits) + ',' + hex(value.minYBits) + ',' + hex(value.minZBits)
                + ',' + hex(value.maxXBits) + ',' + hex(value.maxYBits) + ',' + hex(value.maxZBits);
    }

    private static Position parsePosition(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid position " + value);
        }
        return new Position(parseInt(parts[0], "position x"), parseInt(parts[1], "position y"),
                parseInt(parts[2], "position z"));
    }

    private static Vec3Bits parseVec(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid vector bits");
        }
        return new Vec3Bits(parseHex(parts[0]), parseHex(parts[1]), parseHex(parts[2]));
    }

    private static BoxBits parseBox(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 6) {
            throw new IllegalArgumentException("invalid AABB bits");
        }
        return new BoxBits(parseHex(parts[0]), parseHex(parts[1]), parseHex(parts[2]),
                parseHex(parts[3]), parseHex(parts[4]), parseHex(parts[5]));
    }

    private static String hex(long value) {
        return String.format(java.util.Locale.ROOT, "%016x", value);
    }

    private static long parseHex(String value) {
        if (!value.matches("[0-9a-f]{16}")) {
            throw new IllegalArgumentException("invalid raw double bits " + value);
        }
        return Long.parseUnsignedLong(value, 16);
    }

    private static List<UUID> parseUuidRows(RowReader reader, String key) {
        return reader.repeated(key, 2).stream().map(row -> parseUuid(row[1], key)).toList();
    }

    private static List<Position> parsePositionRows(RowReader reader, String key) {
        return reader.repeated(key, 2).stream().map(row -> parsePosition(row[1])).toList();
    }

    private static int parseInt(String value, String label) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid " + label, failure);
        }
    }

    private static long parseLong(String value, String label) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid " + label, failure);
        }
    }

    private static boolean parseBoolean(String value, String label) {
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("invalid " + label);
    }

    private static UUID parseUuid(String value, String label) {
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equals(value)) {
                throw new IllegalArgumentException("non-canonical UUID");
            }
            return uuid;
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid " + label, failure);
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid " + label, failure);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escaped) {
                if (c == '\\') {
                    escaped = true;
                } else {
                    out.append(c);
                }
                continue;
            }
            switch (c) {
                case '\\' -> out.append('\\');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'n' -> out.append('\n');
                default -> throw new IllegalArgumentException("invalid TSV escape \\" + c);
            }
            escaped = false;
        }
        if (escaped) {
            throw new IllegalArgumentException("trailing TSV escape");
        }
        return out.toString();
    }

    private static boolean isUuid(String value) {
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static void requireSha256(String value, String label) {
        if (!isSha256(value)) {
            throw new IllegalArgumentException(label + " must be lowercase SHA-256");
        }
    }

    private static void requireOptionalSha256(String value, String label) {
        if (!NO_VALUE.equals(value)) {
            requireSha256(value, label);
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(label + " must be non-empty");
        }
    }

    public static String removalDetail(RemovalCause cause, String artifactHash) {
        Objects.requireNonNull(cause, "cause");
        requireSha256(artifactHash, "removal artifact");
        return cause.name() + ':' + artifactHash;
    }

    private static RemovalDetail parseRemovalDetail(String detail) {
        requireText(detail, "painting removal detail");
        int separator = detail.indexOf(':');
        if (separator <= 0 || separator != detail.lastIndexOf(':')) {
            throw new IllegalArgumentException("painting removal detail has invalid grammar");
        }
        RemovalCause cause;
        try {
            cause = RemovalCause.valueOf(detail.substring(0, separator));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("unknown painting removal cause", failure);
        }
        String artifactHash = detail.substring(separator + 1);
        requireSha256(artifactHash, "removal artifact");
        if (!detail.equals(removalDetail(cause, artifactHash))) {
            throw new IllegalArgumentException("painting removal detail is not canonical");
        }
        return new RemovalDetail(cause, artifactHash);
    }

    public static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private record RemovalDetail(RemovalCause cause, String artifactHash) {
    }

    /** Small strict row index used only by the canonical parser. */
    private static final class RowReader {
        private final Map<String, List<String[]>> byKey = new LinkedHashMap<>();
        private final Set<String> consumed = new HashSet<>();

        private RowReader(List<String[]> rows) {
            for (String[] row : rows) {
                if (row.length == 0 || row[0].isEmpty()) {
                    throw new IllegalArgumentException("empty state row key");
                }
                byKey.computeIfAbsent(row[0], ignored -> new ArrayList<>()).add(row);
            }
        }

        private String singleton(String key) {
            List<String[]> rows = repeated(key, 2);
            if (rows.size() != 1) {
                throw new IllegalArgumentException("state requires exactly one " + key + " row");
            }
            return rows.getFirst()[1];
        }

        private void singleton(String key, String expected) {
            if (!expected.equals(singleton(key))) {
                throw new IllegalArgumentException("unsupported " + key);
            }
        }

        private List<String[]> repeated(String key, int fields) {
            consumed.add(key);
            List<String[]> rows = byKey.getOrDefault(key, List.of());
            for (String[] row : rows) {
                if (row.length != fields) {
                    throw new IllegalArgumentException(key + " row has wrong field count");
                }
            }
            return rows;
        }

        private List<String[]> rows(String key) {
            return byKey.getOrDefault(key, List.of());
        }

        private void rejectUnknown() {
            Set<String> unknown = new LinkedHashSet<>(byKey.keySet());
            unknown.removeAll(consumed);
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("unknown state row keys " + unknown);
            }
        }
    }
}
