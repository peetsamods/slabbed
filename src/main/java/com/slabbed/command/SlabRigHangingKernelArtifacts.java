package com.slabbed.command;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Content-addressed RIG-3B2A phase artifacts for the disposable headless painting kernel.
 *
 * <p>B2A can publish only {@link Phase#PLANNED}, {@link Phase#IMMEDIATE}, and
 * {@link Phase#KERNEL_CLEARED}. Delayed/final vocabulary is reserved so a B2A artifact can never be
 * mistaken for lifecycle, restart, client, or player proof.
 */
public final class SlabRigHangingKernelArtifacts {

    public static final String SCHEMA = "slabbed-rig-hanging-kernel-page-v1";
    public static final String EXECUTION_CONTRACT = "rig3b2a-headless-test-kernel-v1";
    public static final String PROOF_SCOPE = "HEADLESS_TEST_KERNEL";
    public static final String PLAYER_PROOF = "ABSENT";
    public static final String PRODUCTION_COMMAND = "ABSENT";
    public static final String NO_PREDECESSOR = "NONE";
    private static final String DIRECTORY = "hanging-page-artifacts";
    private static final String PREFIX = "hanging-page-";
    private static final String SUFFIX = ".tsv";
    private static final Comparator<Position> POSITION_ORDER = Comparator.naturalOrder();

    private SlabRigHangingKernelArtifacts() {
    }

    public enum Phase {
        PLANNED("planned"),
        IMMEDIATE("immediate"),
        KERNEL_CLEARED("kernel-cleared"),
        DELAYED("delayed"),
        FINAL("final");

        private final String directory;

        Phase(String directory) {
            this.directory = directory;
        }

        public String directory() {
            return directory;
        }
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
            int xCompare = Integer.compare(x, other.x);
            if (xCompare != 0) {
                return xCompare;
            }
            int yCompare = Integer.compare(y, other.y);
            return yCompare != 0 ? yCompare : Integer.compare(z, other.z);
        }
    }

    /** Exact raw IEEE-754 identity; decimal rendering never enters canonical evidence. */
    public record Vec3Bits(long xBits, long yBits, long zBits) {
        public static Vec3Bits of(Vec3 value) {
            return of(value.x, value.y, value.z);
        }

        public static Vec3Bits of(double x, double y, double z) {
            return new Vec3Bits(Double.doubleToRawLongBits(x),
                    Double.doubleToRawLongBits(y), Double.doubleToRawLongBits(z));
        }
    }

    /** Exact raw IEEE-754 AABB identity. */
    public record BoxBits(long minXBits, long minYBits, long minZBits,
                          long maxXBits, long maxYBits, long maxZBits) {
        public static BoxBits of(AABB box) {
            return of(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        }

        public static BoxBits of(double minX, double minY, double minZ,
                                 double maxX, double maxY, double maxZ) {
            return new BoxBits(Double.doubleToRawLongBits(minX),
                    Double.doubleToRawLongBits(minY), Double.doubleToRawLongBits(minZ),
                    Double.doubleToRawLongBits(maxX), Double.doubleToRawLongBits(maxY),
                    Double.doubleToRawLongBits(maxZ));
        }
    }

    public record RunIdentity(String plannerIdentity, String buildGitSha,
                              String runtimeContentSha256, String minecraftVersion,
                              String rig3aCatalogHash, String rig3b1ExecutionIdentity,
                              String paintingRegistryHash, String worldKey, String dimension,
                              UUID playerUuid, boolean frozenDyEnabled) {
        public RunIdentity {
            requireText(plannerIdentity, "plannerIdentity");
            requireText(buildGitSha, "buildGitSha");
            requireText(runtimeContentSha256, "runtimeContentSha256");
            requireText(minecraftVersion, "minecraftVersion");
            requireText(rig3aCatalogHash, "rig3aCatalogHash");
            requireText(rig3b1ExecutionIdentity, "rig3b1ExecutionIdentity");
            requireText(paintingRegistryHash, "paintingRegistryHash");
            requireText(worldKey, "worldKey");
            requireText(dimension, "dimension");
            Objects.requireNonNull(playerUuid, "playerUuid");
        }
    }

    public record CasePlan(long executionIndex, String executionCaseId, String rig3aCaseId,
                           String routeId, String topologyId, String inputModeId,
                           Position tileBase, Position clicked, String clickedFace,
                           String hitVector, Position attachmentPos,
                           List<Position> plannedStructureCells,
                           List<Position> plannedBackingCells,
                           List<Position> reservedCells, BoxBits effectBox) {
        public CasePlan {
            requireText(executionCaseId, "executionCaseId");
            requireText(rig3aCaseId, "rig3aCaseId");
            requireText(routeId, "routeId");
            requireText(topologyId, "topologyId");
            requireText(inputModeId, "inputModeId");
            Objects.requireNonNull(tileBase, "tileBase");
            Objects.requireNonNull(clicked, "clicked");
            requireText(clickedFace, "clickedFace");
            requireText(hitVector, "hitVector");
            Objects.requireNonNull(attachmentPos, "attachmentPos");
            plannedStructureCells = normalizedPositions(plannedStructureCells);
            plannedBackingCells = normalizedPositions(plannedBackingCells);
            reservedCells = normalizedPositions(reservedCells);
            Objects.requireNonNull(effectBox, "effectBox");
            Set<Position> authored = new HashSet<>(plannedStructureCells);
            authored.addAll(plannedBackingCells);
            if (authored.size() != plannedStructureCells.size() + plannedBackingCells.size()
                    || !new HashSet<>(reservedCells).containsAll(authored)) {
                throw new IllegalArgumentException("planned authored cells must be unique and reserved");
            }
        }

        public List<Position> plannedAuthoredCells() {
            List<Position> cells = new ArrayList<>(plannedStructureCells);
            cells.addAll(plannedBackingCells);
            cells.sort(POSITION_ORDER);
            return List.copyOf(cells);
        }
    }

    public record PagePlan(String pageIdentity, int page, int pageCount, String routeId,
                           Position base, String facing, List<CasePlan> cases) {
        public PagePlan {
            requireText(pageIdentity, "pageIdentity");
            if (page < 1 || pageCount < page) {
                throw new IllegalArgumentException("invalid page counters");
            }
            requireText(routeId, "routeId");
            Objects.requireNonNull(base, "base");
            requireText(facing, "facing");
            cases = cases.stream()
                    .sorted(Comparator.comparingLong(CasePlan::executionIndex)
                            .thenComparing(CasePlan::executionCaseId))
                    .toList();
            if (cases.isEmpty() || cases.size() > SlabRigHangingPaintingPlan.PAGE_SIZE
                    || cases.stream().map(CasePlan::executionCaseId).distinct().count() != cases.size()
                    || cases.stream().map(CasePlan::executionIndex).distinct().count() != cases.size()) {
                throw new IllegalArgumentException("invalid or duplicate page cases");
            }
            Set<Position> reserved = new HashSet<>();
            for (CasePlan plan : cases) {
                for (Position position : plan.reservedCells()) {
                    if (!reserved.add(position)) {
                        throw new IllegalArgumentException(
                                "page case reservations overlap at " + position);
                    }
                }
            }
        }
    }

    public record CellObservation(Position pos, String blockState, String blockEntityType,
                                  String canonicalBlockEntityNbtSha256,
                                  long liveDyBits, long storedDyBits,
                                  String markerFingerprint) {
        public CellObservation {
            Objects.requireNonNull(pos, "pos");
            requireText(blockState, "blockState");
            requireText(blockEntityType, "blockEntityType");
            requireText(canonicalBlockEntityNbtSha256, "canonicalBlockEntityNbtSha256");
            requireText(markerFingerprint, "markerFingerprint");
        }

        public static CellObservation of(Position pos, String state, String blockEntityType,
                                         String nbtSha256, double liveDy, double storedDy,
                                         String markerFingerprint) {
            return new CellObservation(pos, state, blockEntityType, nbtSha256,
                    Double.doubleToRawLongBits(liveDy), Double.doubleToRawLongBits(storedDy),
                    markerFingerprint);
        }
    }

    public record EntityObservation(UUID uuid, String type, String variantId,
                                    String componentVariantId, Position attachmentPos,
                                    String facing, Vec3Bits position, BoxBits aabb,
                                    boolean survives, boolean alive, boolean removed,
                                    String canonicalExecutionNbtSha256) {
        public EntityObservation {
            Objects.requireNonNull(uuid, "uuid");
            requireText(type, "type");
            requireText(variantId, "variantId");
            requireText(componentVariantId, "componentVariantId");
            Objects.requireNonNull(attachmentPos, "attachmentPos");
            requireText(facing, "facing");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(aabb, "aabb");
            requireText(canonicalExecutionNbtSha256, "canonicalExecutionNbtSha256");
            if (!isSha256(canonicalExecutionNbtSha256) || alive && removed) {
                throw new IllegalArgumentException("invalid entity observation identity/state");
            }
        }
    }

    public record Ownership(List<Position> authoredCells, List<Position> attachmentCells,
                            List<Position> clearOwnedCells, List<UUID> ownedEntityUuids) {
        public Ownership {
            authoredCells = normalizedPositions(authoredCells);
            attachmentCells = normalizedPositions(attachmentCells);
            clearOwnedCells = normalizedPositions(clearOwnedCells);
            ownedEntityUuids = normalizedUuids(ownedEntityUuids);
            if (!new HashSet<>(authoredCells).containsAll(clearOwnedCells)) {
                throw new IllegalArgumentException("clear-owned cells must be authored cells");
            }
        }

        public static Ownership empty() {
            return new Ownership(List.of(), List.of(), List.of(), List.of());
        }

        public static Ownership merge(Collection<Ownership> parts) {
            List<Position> authored = new ArrayList<>();
            List<Position> attachments = new ArrayList<>();
            List<Position> clear = new ArrayList<>();
            List<UUID> entities = new ArrayList<>();
            for (Ownership part : parts) {
                authored.addAll(part.authoredCells());
                attachments.addAll(part.attachmentCells());
                clear.addAll(part.clearOwnedCells());
                entities.addAll(part.ownedEntityUuids());
            }
            return new Ownership(authored, attachments, clear, entities);
        }

        public boolean isEmpty() {
            return authoredCells.isEmpty() && attachmentCells.isEmpty()
                    && clearOwnedCells.isEmpty() && ownedEntityUuids.isEmpty();
        }
    }

    public record CaseObservation(String executionCaseId,
                                  List<CellObservation> beforeCells,
                                  List<EntityObservation> beforeEntities,
                                  String actionResult, boolean consumesAction,
                                  String stackBeforeWithComponents,
                                  String stackAfterWithComponents,
                                  List<UUID> addedEntityUuids,
                                  List<UUID> removedPreexistingEntityUuids,
                                  List<CellObservation> immediateCells,
                                  List<EntityObservation> immediateEntities,
                                  Ownership ownership, String outcome, String detail) {
        public CaseObservation {
            requireText(executionCaseId, "executionCaseId");
            beforeCells = normalizedCells(beforeCells);
            beforeEntities = normalizedEntities(beforeEntities);
            requireText(actionResult, "actionResult");
            requireText(stackBeforeWithComponents, "stackBeforeWithComponents");
            requireText(stackAfterWithComponents, "stackAfterWithComponents");
            addedEntityUuids = normalizedUuids(addedEntityUuids);
            removedPreexistingEntityUuids = normalizedUuids(removedPreexistingEntityUuids);
            immediateCells = normalizedCells(immediateCells);
            immediateEntities = normalizedEntities(immediateEntities);
            Objects.requireNonNull(ownership, "ownership");
            requireText(outcome, "outcome");
            requireText(detail, "detail");
        }
    }

    public record ClearResult(List<UUID> requestedEntityUuids,
                              List<UUID> removedEntityUuids,
                              List<UUID> alreadyAbsentEntityUuids,
                              List<Position> requestedClearOwnedCells,
                              List<Position> clearedCells,
                              List<Position> alreadyAirCells,
                              List<Position> requestedAttachmentCells,
                              List<Position> clearedAttachmentCells,
                              List<Position> alreadyAbsentAttachmentCells) {
        public ClearResult {
            requestedEntityUuids = normalizedUuids(requestedEntityUuids);
            removedEntityUuids = normalizedUuids(removedEntityUuids);
            alreadyAbsentEntityUuids = normalizedUuids(alreadyAbsentEntityUuids);
            requestedClearOwnedCells = normalizedPositions(requestedClearOwnedCells);
            clearedCells = normalizedPositions(clearedCells);
            alreadyAirCells = normalizedPositions(alreadyAirCells);
            requestedAttachmentCells = normalizedPositions(requestedAttachmentCells);
            clearedAttachmentCells = normalizedPositions(clearedAttachmentCells);
            alreadyAbsentAttachmentCells = normalizedPositions(alreadyAbsentAttachmentCells);
        }
    }

    public record PhaseManifest(Phase phase, String artifactId, RunIdentity run, PagePlan page,
                                String predecessorArtifactId, String proofScope,
                                String playerProof, String productionCommand,
                                boolean progressEligible, Ownership ownership,
                                List<CaseObservation> observations, ClearResult clearResult) {
        public PhaseManifest {
            Objects.requireNonNull(phase, "phase");
            requireText(artifactId, "artifactId");
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(page, "page");
            requireText(predecessorArtifactId, "predecessorArtifactId");
            requireText(proofScope, "proofScope");
            requireText(playerProof, "playerProof");
            requireText(productionCommand, "productionCommand");
            Objects.requireNonNull(ownership, "ownership");
            observations = observations.stream()
                    .sorted(Comparator.comparing(CaseObservation::executionCaseId)).toList();
        }
    }

    public record WrittenArtifact(Path path, String artifactId, String fileSha256, long byteCount) {
    }

    public static PhaseManifest planned(RunIdentity run, PagePlan page) {
        PhaseManifest seed = new PhaseManifest(Phase.PLANNED, "PENDING", run, page,
                NO_PREDECESSOR, PROOF_SCOPE, PLAYER_PROOF, PRODUCTION_COMMAND, false,
                Ownership.empty(), List.of(), null);
        PhaseManifest result = withComputedId(seed);
        validateSelf(result);
        return result;
    }

    public static PhaseManifest immediate(PhaseManifest planned,
                                          List<CaseObservation> observations) {
        validateSelf(planned);
        if (planned.phase() != Phase.PLANNED) {
            throw new IllegalArgumentException("IMMEDIATE requires a PLANNED predecessor");
        }
        Ownership ownership = Ownership.merge(observations.stream()
                .map(CaseObservation::ownership).toList());
        PhaseManifest seed = new PhaseManifest(Phase.IMMEDIATE, "PENDING", planned.run(),
                planned.page(), planned.artifactId(), PROOF_SCOPE, PLAYER_PROOF,
                PRODUCTION_COMMAND, false, ownership, observations, null);
        PhaseManifest result = withComputedId(seed);
        validateTransition(planned, result);
        return result;
    }

    public static PhaseManifest kernelCleared(PhaseManifest immediate, ClearResult clearResult) {
        validateSelf(immediate);
        if (immediate.phase() != Phase.IMMEDIATE) {
            throw new IllegalArgumentException("KERNEL_CLEARED requires an IMMEDIATE predecessor");
        }
        PhaseManifest seed = new PhaseManifest(Phase.KERNEL_CLEARED, "PENDING", immediate.run(),
                immediate.page(), immediate.artifactId(), PROOF_SCOPE, PLAYER_PROOF,
                PRODUCTION_COMMAND, false, immediate.ownership(), immediate.observations(), clearResult);
        PhaseManifest result = withComputedId(seed);
        validateTransition(immediate, result);
        return result;
    }

    public static void validateSelf(PhaseManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        if (manifest.phase() == Phase.DELAYED || manifest.phase() == Phase.FINAL) {
            throw new IllegalArgumentException(manifest.phase() + " is reserved for RIG-3B2B");
        }
        if (!PROOF_SCOPE.equals(manifest.proofScope())
                || !PLAYER_PROOF.equals(manifest.playerProof())
                || !PRODUCTION_COMMAND.equals(manifest.productionCommand())
                || manifest.progressEligible()) {
            throw new IllegalArgumentException("false B2A proof/progress claim");
        }
        validateRun(manifest.run());
        if (!manifest.run().plannerIdentity().equals(manifest.page().pageIdentity())) {
            throw new IllegalArgumentException("run planner identity does not bind the exact page plan");
        }
        String expectedId = sha256(canonicalBody(manifest));
        if (!expectedId.equals(manifest.artifactId())) {
            throw new IllegalArgumentException("artifact identity does not match canonical body");
        }
        Map<String, CasePlan> plans = casePlans(manifest.page());
        switch (manifest.phase()) {
            case PLANNED -> {
                if (!NO_PREDECESSOR.equals(manifest.predecessorArtifactId())
                        || !manifest.ownership().isEmpty() || !manifest.observations().isEmpty()
                        || manifest.clearResult() != null) {
                    throw new IllegalArgumentException("invalid PLANNED phase payload");
                }
            }
            case IMMEDIATE -> validateImmediate(manifest, plans);
            case KERNEL_CLEARED -> {
                validateImmediateHistory(manifest, plans);
                validateClear(manifest.ownership(), manifest.clearResult());
            }
            default -> throw new IllegalArgumentException("unsupported B2A phase " + manifest.phase());
        }
    }

    public static void validateTransition(PhaseManifest predecessor, PhaseManifest current) {
        validateSelf(predecessor);
        validateSelf(current);
        if (!predecessor.artifactId().equals(current.predecessorArtifactId())
                || !predecessor.run().equals(current.run())
                || !predecessor.page().equals(current.page())) {
            throw new IllegalArgumentException("phase predecessor or immutable identity mismatch");
        }
        if (predecessor.phase() == Phase.PLANNED && current.phase() == Phase.IMMEDIATE) {
            return;
        }
        if (predecessor.phase() == Phase.IMMEDIATE
                && current.phase() == Phase.KERNEL_CLEARED
                && predecessor.ownership().equals(current.ownership())
                && predecessor.observations().equals(current.observations())) {
            return;
        }
        throw new IllegalArgumentException("invalid B2A phase transition "
                + predecessor.phase() + " -> " + current.phase());
    }

    public static String contentId(PhaseManifest manifest) {
        validateSelf(manifest);
        return manifest.artifactId();
    }

    public static String canonicalTsv(PhaseManifest manifest) {
        validateSelf(manifest);
        String body = canonicalBody(manifest);
        int firstLineEnd = body.indexOf('\n') + 1;
        return body.substring(0, firstLineEnd) + "artifact_id\t" + manifest.artifactId() + '\n'
                + body.substring(firstLineEnd);
    }

    /** Strict test-root-selected writer; no production command can select or invoke this destination. */
    public static WrittenArtifact write(Path testSelectedRoot, PhaseManifest manifest) throws IOException {
        Objects.requireNonNull(testSelectedRoot, "testSelectedRoot");
        validateSelf(manifest);
        byte[] expected = canonicalTsv(manifest).getBytes(StandardCharsets.UTF_8);
        Path root = testSelectedRoot.toAbsolutePath().normalize();
        prepareDirectory(root);
        Path artifactDirectory = root.resolve(DIRECTORY);
        prepareDirectory(artifactDirectory);
        Path phaseDirectory = artifactDirectory.resolve(manifest.phase().directory());
        prepareDirectory(phaseDirectory);
        if (Files.isSymbolicLink(root) || Files.isSymbolicLink(artifactDirectory)
                || Files.isSymbolicLink(phaseDirectory)) {
            throw new IOException("refusing symlinked hanging-page artifact path");
        }
        Path rootReal = root.toRealPath();
        Path artifactReal = artifactDirectory.toRealPath();
        Path phaseReal = phaseDirectory.toRealPath();
        if (!artifactReal.equals(rootReal.resolve(DIRECTORY))
                || !phaseReal.equals(artifactReal.resolve(manifest.phase().directory()))) {
            throw new IOException("hanging-page artifact directory escapes exact test root");
        }

        String name = PREFIX + manifest.phase().directory() + '-' + manifest.artifactId() + SUFFIX;
        Path target = phaseDirectory.resolve(name).toAbsolutePath().normalize();
        if (!phaseDirectory.equals(target.getParent()) || !name.equals(target.getFileName().toString())) {
            throw new IOException("invalid hanging-page target path");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            verifyExisting(target, expected);
            return written(target, manifest, expected);
        }

        Path temporary = phaseDirectory.resolve('.' + name + ".tmp-" + UUID.randomUUID())
                .toAbsolutePath().normalize();
        boolean temporaryOwned = false;
        try {
            FileChannel opened = FileChannel.open(temporary,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            temporaryOwned = true;
            try (FileChannel channel = opened) {
                ByteBuffer buffer = ByteBuffer.wrap(expected);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            verifyRegularExact(temporary, expected, "temporary hanging-page artifact");
            try {
                Files.createLink(target, temporary);
            } catch (FileAlreadyExistsException raced) {
                verifyExisting(target, expected);
            }
            verifyRegularExact(target, expected, "published hanging-page artifact");
            return written(target, manifest, expected);
        } finally {
            if (temporaryOwned) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static PhaseManifest withComputedId(PhaseManifest seed) {
        String id = sha256(canonicalBody(seed));
        return new PhaseManifest(seed.phase(), id, seed.run(), seed.page(),
                seed.predecessorArtifactId(), seed.proofScope(), seed.playerProof(),
                seed.productionCommand(), seed.progressEligible(), seed.ownership(),
                seed.observations(), seed.clearResult());
    }

    private static void validateImmediate(PhaseManifest manifest, Map<String, CasePlan> plans) {
        if (!isSha256(manifest.predecessorArtifactId()) || manifest.clearResult() != null) {
            throw new IllegalArgumentException("invalid IMMEDIATE predecessor/clear payload");
        }
        validateImmediateHistory(manifest, plans);
    }

    private static void validateImmediateHistory(PhaseManifest manifest,
                                                 Map<String, CasePlan> plans) {
        if (manifest.observations().size() != plans.size()) {
            throw new IllegalArgumentException("IMMEDIATE must observe every planned case exactly once");
        }
        Set<String> seen = new HashSet<>();
        Set<UUID> pageOwnedEntityUuids = new HashSet<>();
        List<Ownership> ownershipParts = new ArrayList<>();
        for (CaseObservation observation : manifest.observations()) {
            CasePlan plan = plans.get(observation.executionCaseId());
            if (plan == null || !seen.add(observation.executionCaseId())) {
                throw new IllegalArgumentException("unknown or duplicate case observation");
            }
            Set<UUID> before = entityIds(observation.beforeEntities());
            Set<UUID> after = entityIds(observation.immediateEntities());
            Map<UUID, EntityObservation> immediateByUuid = new HashMap<>();
            for (EntityObservation entity : observation.immediateEntities()) {
                immediateByUuid.put(entity.uuid(), entity);
            }
            Set<UUID> added = new HashSet<>(after);
            added.removeAll(before);
            Set<UUID> removed = new HashSet<>(before);
            removed.removeAll(after);
            if (!added.equals(new HashSet<>(observation.addedEntityUuids()))
                    || !removed.equals(new HashSet<>(observation.removedPreexistingEntityUuids()))
                    || !removed.isEmpty()
                    || !added.equals(new HashSet<>(observation.ownership().ownedEntityUuids()))) {
                throw new IllegalArgumentException("case UUID delta/ownership mismatch");
            }
            for (UUID uuid : observation.ownership().ownedEntityUuids()) {
                if (!pageOwnedEntityUuids.add(uuid)) {
                    throw new IllegalArgumentException(
                            "painting UUID is owned by more than one page case: " + uuid);
                }
            }
            Set<Position> planned = new HashSet<>(plan.plannedAuthoredCells());
            Set<Position> authored = new HashSet<>(observation.ownership().authoredCells());
            Set<Position> clearOwned = new HashSet<>(observation.ownership().clearOwnedCells());
            if (!planned.equals(authored) || !planned.equals(clearOwned)
                    || !planned.containsAll(observation.ownership().attachmentCells())) {
                throw new IllegalArgumentException("case ownership is not the exact planned authored set");
            }
            Set<Position> plannedBacking = new HashSet<>(plan.plannedBackingCells());
            Set<Position> beforeCells = observation.beforeCells().stream()
                    .map(CellObservation::pos).collect(java.util.stream.Collectors.toSet());
            Set<Position> immediateCells = observation.immediateCells().stream()
                    .map(CellObservation::pos).collect(java.util.stream.Collectors.toSet());
            if (!plannedBacking.equals(beforeCells) || !plannedBacking.equals(immediateCells)) {
                throw new IllegalArgumentException(
                        "case observations do not cover the exact planned backing cells");
            }
            for (UUID uuid : added) {
                EntityObservation entity = immediateByUuid.get(uuid);
                if (entity == null || !"minecraft:painting".equals(entity.type())
                        || !entity.variantId().equals(entity.componentVariantId())
                        || !entity.survives() || !entity.alive() || entity.removed()) {
                    throw new IllegalArgumentException(
                            "added painting lacks exact holder/component/survival evidence: " + uuid);
                }
            }
            String expectedOutcome = added.size() == 1 ? "PLACED_SURVIVES"
                    : added.isEmpty() ? "VANILLA_REFUSAL"
                    : "BOUNDED_FAILURE_MULTIPLE_ENTITIES";
            if (!expectedOutcome.equals(observation.outcome())) {
                throw new IllegalArgumentException("case outcome disagrees with exact UUID delta");
            }
            ownershipParts.add(observation.ownership());
        }
        if (!Ownership.merge(ownershipParts).equals(manifest.ownership())) {
            throw new IllegalArgumentException("page ownership is not exact case union");
        }
    }

    private static void validateClear(Ownership ownership, ClearResult clear) {
        if (clear == null) {
            throw new IllegalArgumentException("KERNEL_CLEARED lacks exact clear result");
        }
        requirePartition(new HashSet<>(clear.requestedEntityUuids()),
                new HashSet<>(clear.removedEntityUuids()),
                new HashSet<>(clear.alreadyAbsentEntityUuids()), "entity UUID");
        if (!new HashSet<>(ownership.ownedEntityUuids())
                .equals(new HashSet<>(clear.requestedEntityUuids()))) {
            throw new IllegalArgumentException("clear requested wrong entity UUIDs");
        }
        requirePartition(new HashSet<>(clear.requestedClearOwnedCells()),
                new HashSet<>(clear.clearedCells()), new HashSet<>(clear.alreadyAirCells()),
                "clear-owned cell");
        if (!new HashSet<>(ownership.clearOwnedCells())
                .equals(new HashSet<>(clear.requestedClearOwnedCells()))) {
            throw new IllegalArgumentException("clear requested wrong authored cells");
        }
        requirePartition(new HashSet<>(clear.requestedAttachmentCells()),
                new HashSet<>(clear.clearedAttachmentCells()),
                new HashSet<>(clear.alreadyAbsentAttachmentCells()), "attachment cell");
        if (!new HashSet<>(ownership.attachmentCells())
                .equals(new HashSet<>(clear.requestedAttachmentCells()))) {
            throw new IllegalArgumentException("clear requested wrong attachment cells");
        }
    }

    private static <T> void requirePartition(Set<T> requested, Set<T> changed,
                                             Set<T> alreadyAbsent, String label) {
        Set<T> overlap = new HashSet<>(changed);
        overlap.retainAll(alreadyAbsent);
        Set<T> union = new HashSet<>(changed);
        union.addAll(alreadyAbsent);
        if (!overlap.isEmpty() || !union.equals(requested)) {
            throw new IllegalArgumentException(label + " clear result is not an exact partition");
        }
    }

    private static Map<String, CasePlan> casePlans(PagePlan page) {
        Map<String, CasePlan> result = new HashMap<>();
        for (CasePlan plan : page.cases()) {
            if (result.put(plan.executionCaseId(), plan) != null) {
                throw new IllegalArgumentException("duplicate planned case ID");
            }
        }
        return result;
    }

    private static Set<UUID> entityIds(List<EntityObservation> observations) {
        Set<UUID> result = new HashSet<>();
        for (EntityObservation observation : observations) {
            if (!result.add(observation.uuid())) {
                throw new IllegalArgumentException("duplicate observed UUID");
            }
        }
        return result;
    }

    private static void validateRun(RunIdentity run) {
        if (!isSha256(run.plannerIdentity()) || !isSha256(run.runtimeContentSha256())
                || !isSha256(run.rig3aCatalogHash()) || !isSha256(run.rig3b1ExecutionIdentity())
                || !isSha256(run.paintingRegistryHash())
                || !run.buildGitSha().matches("[0-9a-f]{7,64}")) {
            throw new IllegalArgumentException("run identity has malformed hash/build fields");
        }
    }

    private static String canonicalBody(PhaseManifest manifest) {
        StringBuilder out = new StringBuilder(32_000);
        out.append("schema\t").append(SCHEMA).append('\n');
        out.append("phase\t").append(manifest.phase()).append('\n');
        out.append("execution_contract\t").append(EXECUTION_CONTRACT).append('\n');
        out.append("proof_scope\t").append(escape(manifest.proofScope())).append('\n');
        out.append("player_proof\t").append(escape(manifest.playerProof())).append('\n');
        out.append("production_command\t").append(escape(manifest.productionCommand())).append('\n');
        out.append("progress_eligible\t").append(manifest.progressEligible()).append('\n');
        out.append("predecessor_artifact_id\t").append(manifest.predecessorArtifactId()).append('\n');
        appendRun(out, manifest.run());
        appendPage(out, manifest.page());
        appendOwnership(out, "page", manifest.ownership());
        for (CaseObservation observation : manifest.observations()) {
            appendObservation(out, observation);
        }
        if (manifest.clearResult() != null) {
            appendClear(out, manifest.clearResult());
        }
        return out.toString();
    }

    private static void appendRun(StringBuilder out, RunIdentity run) {
        out.append("planner_identity\t").append(run.plannerIdentity()).append('\n');
        out.append("build_git_sha\t").append(run.buildGitSha()).append('\n');
        out.append("runtime_content_sha256\t").append(run.runtimeContentSha256()).append('\n');
        out.append("minecraft_version\t").append(escape(run.minecraftVersion())).append('\n');
        out.append("rig3a_catalog_hash\t").append(run.rig3aCatalogHash()).append('\n');
        out.append("rig3b1_execution_identity\t").append(run.rig3b1ExecutionIdentity()).append('\n');
        out.append("painting_registry_hash\t").append(run.paintingRegistryHash()).append('\n');
        out.append("world_key\t").append(escape(run.worldKey())).append('\n');
        out.append("dimension\t").append(escape(run.dimension())).append('\n');
        out.append("player_uuid\t").append(run.playerUuid()).append('\n');
        out.append("frozen_dy_enabled\t").append(run.frozenDyEnabled()).append('\n');
    }

    private static void appendPage(StringBuilder out, PagePlan page) {
        out.append("page_identity\t").append(page.pageIdentity()).append('\n');
        out.append("page\t").append(page.page()).append('\n');
        out.append("page_count\t").append(page.pageCount()).append('\n');
        out.append("route_id\t").append(escape(page.routeId())).append('\n');
        out.append("base\t").append(position(page.base())).append('\n');
        out.append("facing\t").append(escape(page.facing())).append('\n');
        out.append("case_count\t").append(page.cases().size()).append('\n');
        for (CasePlan plan : page.cases()) {
            out.append("plan_case\t").append(plan.executionIndex()).append('\t')
                    .append(plan.executionCaseId()).append('\t').append(plan.rig3aCaseId()).append('\t')
                    .append(escape(plan.routeId())).append('\t').append(escape(plan.topologyId()))
                    .append('\t').append(plan.inputModeId()).append('\t')
                    .append(position(plan.tileBase())).append('\t').append(position(plan.clicked()))
                    .append('\t').append(escape(plan.clickedFace())).append('\t')
                    .append(escape(plan.hitVector())).append('\t').append(position(plan.attachmentPos()))
                    .append('\t').append(box(plan.effectBox())).append('\n');
            appendPositions(out, "plan_structure", plan.executionCaseId(), plan.plannedStructureCells());
            appendPositions(out, "plan_backing", plan.executionCaseId(), plan.plannedBackingCells());
            appendPositions(out, "plan_reserved", plan.executionCaseId(), plan.reservedCells());
        }
    }

    private static void appendObservation(StringBuilder out, CaseObservation observation) {
        String id = observation.executionCaseId();
        out.append("observation\t").append(id).append('\t')
                .append(escape(observation.actionResult())).append('\t')
                .append(observation.consumesAction()).append('\t')
                .append(escape(observation.stackBeforeWithComponents())).append('\t')
                .append(escape(observation.stackAfterWithComponents())).append('\t')
                .append(escape(observation.outcome())).append('\t')
                .append(escape(observation.detail())).append('\n');
        appendCells(out, "before_cell", id, observation.beforeCells());
        appendEntities(out, "before_entity", id, observation.beforeEntities());
        appendUuids(out, "added_uuid", id, observation.addedEntityUuids());
        appendUuids(out, "removed_preexisting_uuid", id,
                observation.removedPreexistingEntityUuids());
        appendCells(out, "immediate_cell", id, observation.immediateCells());
        appendEntities(out, "immediate_entity", id, observation.immediateEntities());
        appendOwnership(out, id, observation.ownership());
    }

    private static void appendOwnership(StringBuilder out, String owner, Ownership ownership) {
        appendPositions(out, "owned_authored", owner, ownership.authoredCells());
        appendPositions(out, "owned_attachment", owner, ownership.attachmentCells());
        appendPositions(out, "owned_clear", owner, ownership.clearOwnedCells());
        appendUuids(out, "owned_uuid", owner, ownership.ownedEntityUuids());
    }

    private static void appendCells(StringBuilder out, String label, String owner,
                                    List<CellObservation> cells) {
        for (CellObservation cell : cells) {
            out.append(label).append('\t').append(owner).append('\t').append(position(cell.pos()))
                    .append('\t').append(escape(cell.blockState())).append('\t')
                    .append(escape(cell.blockEntityType())).append('\t')
                    .append(cell.canonicalBlockEntityNbtSha256()).append('\t')
                    .append(hex(cell.liveDyBits())).append('\t').append(hex(cell.storedDyBits()))
                    .append('\t').append(escape(cell.markerFingerprint())).append('\n');
        }
    }

    private static void appendEntities(StringBuilder out, String label, String owner,
                                       List<EntityObservation> entities) {
        for (EntityObservation entity : entities) {
            out.append(label).append('\t').append(owner).append('\t').append(entity.uuid())
                    .append('\t').append(escape(entity.type())).append('\t')
                    .append(escape(entity.variantId())).append('\t')
                    .append(escape(entity.componentVariantId())).append('\t')
                    .append(position(entity.attachmentPos())).append('\t')
                    .append(escape(entity.facing())).append('\t').append(vec(entity.position()))
                    .append('\t').append(box(entity.aabb())).append('\t').append(entity.survives())
                    .append('\t').append(entity.alive()).append('\t').append(entity.removed()).append('\t')
                    .append(entity.canonicalExecutionNbtSha256()).append('\n');
        }
    }

    private static void appendClear(StringBuilder out, ClearResult clear) {
        appendUuids(out, "clear_requested_uuid", "clear", clear.requestedEntityUuids());
        appendUuids(out, "clear_removed_uuid", "clear", clear.removedEntityUuids());
        appendUuids(out, "clear_already_absent_uuid", "clear", clear.alreadyAbsentEntityUuids());
        appendPositions(out, "clear_requested_cell", "clear", clear.requestedClearOwnedCells());
        appendPositions(out, "clear_cleared_cell", "clear", clear.clearedCells());
        appendPositions(out, "clear_already_air_cell", "clear", clear.alreadyAirCells());
        appendPositions(out, "clear_requested_attachment", "clear", clear.requestedAttachmentCells());
        appendPositions(out, "clear_cleared_attachment", "clear", clear.clearedAttachmentCells());
        appendPositions(out, "clear_absent_attachment", "clear",
                clear.alreadyAbsentAttachmentCells());
    }

    private static void appendPositions(StringBuilder out, String label, String owner,
                                        List<Position> positions) {
        for (Position pos : positions) {
            out.append(label).append('\t').append(owner).append('\t')
                    .append(position(pos)).append('\n');
        }
    }

    private static void appendUuids(StringBuilder out, String label, String owner,
                                    List<UUID> uuids) {
        for (UUID uuid : uuids) {
            out.append(label).append('\t').append(owner).append('\t').append(uuid).append('\n');
        }
    }

    private static String position(Position pos) {
        return pos.x() + "," + pos.y() + "," + pos.z();
    }

    private static String vec(Vec3Bits value) {
        return hex(value.xBits()) + ',' + hex(value.yBits()) + ',' + hex(value.zBits());
    }

    private static String box(BoxBits value) {
        return hex(value.minXBits()) + ',' + hex(value.minYBits()) + ',' + hex(value.minZBits())
                + ".." + hex(value.maxXBits()) + ',' + hex(value.maxYBits()) + ','
                + hex(value.maxZBits());
    }

    private static String hex(long bits) {
        return String.format(java.util.Locale.ROOT, "%016x", bits);
    }

    private static List<Position> normalizedPositions(Collection<Position> input) {
        Objects.requireNonNull(input, "positions");
        return input.stream().map(pos -> Objects.requireNonNull(pos, "position"))
                .distinct().sorted(POSITION_ORDER).toList();
    }

    private static List<UUID> normalizedUuids(Collection<UUID> input) {
        Objects.requireNonNull(input, "uuids");
        return input.stream().map(uuid -> Objects.requireNonNull(uuid, "uuid"))
                .distinct().sorted().toList();
    }

    private static List<CellObservation> normalizedCells(Collection<CellObservation> input) {
        Objects.requireNonNull(input, "cells");
        List<CellObservation> cells = input.stream().sorted(Comparator.comparing(CellObservation::pos))
                .toList();
        if (cells.stream().map(CellObservation::pos).distinct().count() != cells.size()) {
            throw new IllegalArgumentException("duplicate cell observation");
        }
        return cells;
    }

    private static List<EntityObservation> normalizedEntities(Collection<EntityObservation> input) {
        Objects.requireNonNull(input, "entities");
        List<EntityObservation> entities = input.stream()
                .sorted(Comparator.comparing(EntityObservation::uuid)).toList();
        if (entities.stream().map(EntityObservation::uuid).distinct().count() != entities.size()) {
            throw new IllegalArgumentException("duplicate entity observation");
        }
        return entities;
    }

    private static void prepareDirectory(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path))) {
            throw new IOException("refusing non-directory or symlinked artifact path: " + path);
        }
        Files.createDirectories(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("artifact path is not a real directory: " + path);
        }
    }

    private static void verifyExisting(Path target, byte[] expected) throws IOException {
        if (Files.isSymbolicLink(target)
                || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("content-address target is not a regular owned file: " + target);
        }
        verifyRegularExact(target, expected, "existing hanging-page artifact");
    }

    private static void verifyRegularExact(Path path, byte[] expected, String label) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " is not a regular owned file: " + path);
        }
        byte[] actual = Files.readAllBytes(path);
        if (!java.util.Arrays.equals(expected, actual)) {
            throw new IOException(label + " byte collision/readback mismatch: " + path);
        }
    }

    private static WrittenArtifact written(Path target, PhaseManifest manifest, byte[] bytes) {
        return new WrittenArtifact(target, manifest.artifactId(), sha256(bytes), bytes.length);
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must be non-empty");
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
