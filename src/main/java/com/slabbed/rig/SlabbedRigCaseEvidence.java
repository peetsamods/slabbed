package com.slabbed.rig;

import com.slabbed.anchor.SlabAnchorAttachment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Content-addressed write-ahead evidence for the {@code /slabrig cases} executor.
 *
 * <p>A page plan and an active cursor must be durable before any fixture or proxy mutation is
 * allowed. Each completed case is checkpointed independently, so an interrupted current case is
 * never mistaken for ownership of its whole permitted effect envelope. Finalized board ownership
 * remains live until an exact content-addressed release is published after world clear.</p>
 */
public final class SlabbedRigCaseEvidence {
    public static final String SCHEMA = "slabbed-rig-case-evidence-v1";
    public static final String PLAN_SCHEMA = "slabbed-rig-case-plan-v1";
    public static final String RESULT_SCHEMA = "slabbed-rig-case-result-v1";
    public static final String FINAL_SCHEMA = "slabbed-rig-case-final-v1";
    public static final String RELEASE_SCHEMA = "slabbed-rig-case-release-v1";
    public static final String CURSOR_SCHEMA = "slabbed-rig-case-cursor-v3";
    public static final String GENESIS = "GENESIS";
    private static final String RUNTIME_CLASSES_RESOURCE =
            "META-INF/slabbed/rig-cases-runtime-classes.tsv";
    private static final String NONE = "-";
    private static final String EMPTY_OWNERSHIP_HASH = sha256(new byte[0]);
    private static final Base64.Encoder BASE64_ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();

    private SlabbedRigCaseEvidence() {
    }

    /** Opens the world-local dimension store used by the eventual packaged executor. */
    public static Store open(
            ServerLevel world,
            SlabbedRigCaseCatalog.Snapshot snapshot) {
        Objects.requireNonNull(world, "world");
        Path root = worldRoot(world);
        return open(
                root,
                world.dimension().location().toString(),
                runtimeContentHash(),
                snapshot);
    }

    /** Read-only existence check used by status/clear; it never initializes a store. */
    public static boolean hasStore(ServerLevel world) {
        Objects.requireNonNull(world, "world");
        String dimensionId = world.dimension().location().toString();
        Path cursor = dimensionRoot(worldRoot(world), dimensionId).resolve("cursor.tsv");
        return Files.isRegularFile(cursor);
    }

    private static Path worldRoot(ServerLevel world) {
        return world.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve("slabbed")
                .resolve("rig-cases");
    }

    private static Path dimensionRoot(Path root, String dimensionId) {
        return root.resolve(
                "dimension-" + sha256(dimensionId.getBytes(StandardCharsets.UTF_8))
                        .substring(0, 16));
    }

    /**
     * Explicit-root seam for deterministic Forge proof and alternate server world roots. The root
     * owns one persistent world UUID and a separate cursor per dimension identity.
     */
    public static Store open(
            Path root,
            String dimensionId,
            String executorBuildHash,
            SlabbedRigCaseCatalog.Snapshot snapshot) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(snapshot, "snapshot");
        dimensionId = requireText(dimensionId, "dimensionId");
        executorBuildHash = requireHash(executorBuildHash, "executorBuildHash");
        try {
            return Store.initialize(root.toAbsolutePath().normalize(), dimensionId,
                    executorBuildHash, snapshot);
        } catch (IdentityMismatch mismatch) {
            return Store.fault(
                    root.toAbsolutePath().normalize(),
                    ResumeStatus.IDENTITY_MISMATCH,
                    mismatch.getMessage());
        } catch (IOException | RuntimeException failure) {
            return Store.fault(
                    root.toAbsolutePath().normalize(),
                    ResumeStatus.CORRUPT,
                    failureDetail(failure));
        }
    }

    /** Hashes the exact runnable class bytes that define cases planning, evidence, and execution. */
    public static String runtimeContentHash() {
        ClassLoader loader = Objects.requireNonNull(
                SlabbedRigCaseEvidence.class.getClassLoader(), "cases class loader");
        MessageDigest digest = sha256Digest();
        for (String resource : runtimeContentResources()) {
            digest.update(resource.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (InputStream input = loader.getResourceAsStream(resource)) {
                if (input == null) {
                    throw new IllegalStateException(
                            "runtime cases class is missing: " + resource);
                }
                digest.update(input.readAllBytes());
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "could not hash runtime cases class " + resource, failure);
            }
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Exact sorted resource roster generated from the compiled core classes. */
    public static List<String> runtimeContentResources() {
        ClassLoader loader = Objects.requireNonNull(
                SlabbedRigCaseEvidence.class.getClassLoader(), "cases class loader");
        try (InputStream input = loader.getResourceAsStream(RUNTIME_CLASSES_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException(
                        "cases runtime class roster is missing: " + RUNTIME_CLASSES_RESOURCE);
            }
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            String header = "schema\tslabbed-rig-cases-runtime-classes-v1\n";
            if (!text.startsWith(header) || !text.endsWith("\n")) {
                throw new IllegalStateException("cases runtime class roster is malformed");
            }
            List<String> resources = text.substring(header.length(), text.length() - 1)
                    .lines()
                    .map(line -> {
                        if (!line.startsWith("class\t")) {
                            throw new IllegalStateException(
                                    "cases runtime roster contains an unknown row");
                        }
                        String resource = line.substring("class\t".length());
                        if (!resource.matches("[A-Za-z0-9_$/]+\\.class")) {
                            throw new IllegalStateException(
                                    "cases runtime roster contains an invalid class path");
                        }
                        return resource;
                    })
                    .toList();
            if (resources.isEmpty()
                    || !resources.equals(resources.stream().distinct().sorted().toList())) {
                throw new IllegalStateException(
                        "cases runtime class roster must be nonempty, unique, and sorted");
            }
            return resources;
        } catch (IOException failure) {
            throw new IllegalStateException("could not read cases runtime class roster", failure);
        }
    }

    public enum ResumeStatus {
        FRESH,
        READY,
        COMPLETE,
        PREPARED_NO_MUTATION,
        INTERRUPTED_UNKNOWN_OWNERSHIP,
        COMPLETED_PENDING_FINAL,
        FINAL_PENDING_CURSOR,
        IDENTITY_MISMATCH,
        CORRUPT
    }

    public enum StructureStatus {
        COMPLETE,
        INCOMPLETE,
        DEFERRED
    }

    public enum AttemptStatus {
        ATTEMPTED,
        NOT_ATTEMPTED
    }

    public enum CaseOutcome {
        PLACED,
        PRESERVED_VANILLA,
        REJECTED,
        LAW_RED,
        DEFERRED
    }

    public enum AttemptPhase {
        PREPARED,
        EXECUTING
    }

    public record RuntimeIdentity(
            UUID worldId,
            String dimensionId,
            String executorBuildHash,
            String catalogHash,
            String catalogBlobHash,
            String executionContract,
            String layoutVersion,
            int pageCount) {
        public RuntimeIdentity {
            worldId = Objects.requireNonNull(worldId, "worldId");
            dimensionId = requireText(dimensionId, "dimensionId");
            executorBuildHash = requireHash(executorBuildHash, "executorBuildHash");
            catalogHash = requireHash(catalogHash, "catalogHash");
            catalogBlobHash = requireHash(catalogBlobHash, "catalogBlobHash");
            executionContract = requireText(executionContract, "executionContract");
            layoutVersion = requireText(layoutVersion, "layoutVersion");
            if (pageCount < 1) {
                throw new IllegalArgumentException("pageCount must be positive");
            }
        }
    }

    public record ActiveAttempt(
            UUID runId,
            int page,
            String plannedHash,
            AttemptPhase phase,
            int nextCaseOrdinal,
            List<String> completedCaseHashes,
            String accumulatedOwnershipHash) {
        public ActiveAttempt {
            runId = Objects.requireNonNull(runId, "runId");
            plannedHash = requireHash(plannedHash, "plannedHash");
            phase = Objects.requireNonNull(phase, "phase");
            completedCaseHashes = List.copyOf(
                    Objects.requireNonNull(completedCaseHashes, "completedCaseHashes"));
            accumulatedOwnershipHash = requireHash(
                    accumulatedOwnershipHash, "accumulatedOwnershipHash");
            if (page < 1 || nextCaseOrdinal < 0
                    || completedCaseHashes.size() != nextCaseOrdinal
                    || completedCaseHashes.stream().anyMatch(hash -> !isHash(hash))) {
                throw new IllegalArgumentException("invalid active cases attempt");
            }
            if (phase == AttemptPhase.PREPARED
                    && (nextCaseOrdinal != 0
                            || !completedCaseHashes.isEmpty()
                            || !accumulatedOwnershipHash.equals(EMPTY_OWNERSHIP_HASH))) {
                throw new IllegalArgumentException(
                        "prepared cases attempt cannot claim execution evidence");
            }
        }
    }

    public record ProgressCursor(
            long generation,
            String identityHash,
            int nextPage,
            String lastFinalHash,
            String boardFinalHash,
            String lastReleaseHash,
            ActiveAttempt active,
            String cursorHash) {
        public ProgressCursor {
            identityHash = requireHash(identityHash, "identityHash");
            if (!GENESIS.equals(lastFinalHash)) {
                lastFinalHash = requireHash(lastFinalHash, "lastFinalHash");
            }
            if (!GENESIS.equals(boardFinalHash)) {
                boardFinalHash = requireHash(boardFinalHash, "boardFinalHash");
            }
            if (!GENESIS.equals(lastReleaseHash)) {
                lastReleaseHash = requireHash(lastReleaseHash, "lastReleaseHash");
            }
            cursorHash = requireHash(cursorHash, "cursorHash");
            if (generation < 0 || nextPage < 1) {
                throw new IllegalArgumentException("invalid cases cursor bounds");
            }
            if (active != null && active.page() != nextPage) {
                throw new IllegalArgumentException(
                        "active page must equal the next contiguous page");
            }
        }
    }

    public record PreparedPage(
            String identityHash,
            UUID runId,
            int page,
            String plannedHash,
            String admittedCursorHash) {
        public PreparedPage {
            identityHash = requireHash(identityHash, "identityHash");
            runId = Objects.requireNonNull(runId, "runId");
            plannedHash = requireHash(plannedHash, "plannedHash");
            admittedCursorHash = requireHash(admittedCursorHash, "admittedCursorHash");
            if (page < 1) {
                throw new IllegalArgumentException("prepared page must be positive");
            }
        }
    }

    public record CellEvidence(
            BlockPos pos,
            BlockState state,
            SlabAnchorAttachment.PlacementDyFact storedDy) {
        public CellEvidence {
            pos = Objects.requireNonNull(pos, "pos").immutable();
            state = Objects.requireNonNull(state, "state");
            storedDy = validateStoredFact(storedDy);
        }
    }

    public record OwnedCellEvidence(
            BlockPos pos,
            BlockState expectedState,
            SlabAnchorAttachment.PlacementDyFact expectedStoredDy,
            RigManifest.CellRole role,
            String caseId) {
        public OwnedCellEvidence {
            pos = Objects.requireNonNull(pos, "pos").immutable();
            expectedState = Objects.requireNonNull(expectedState, "expectedState");
            expectedStoredDy = validateStoredFact(expectedStoredDy);
            role = Objects.requireNonNull(role, "role");
            caseId = requireText(caseId, "caseId");
        }
    }

    public record CaseResult(
            int ordinal,
            String caseId,
            StructureStatus structureStatus,
            AttemptStatus attemptStatus,
            CaseOutcome outcome,
            boolean inventoryRestored,
            List<CellEvidence> before,
            List<CellEvidence> after,
            List<OwnedCellEvidence> ownedDelta,
            List<BlockPos> outOfEnvelope) {
        public CaseResult {
            caseId = requireText(caseId, "caseId");
            structureStatus = Objects.requireNonNull(structureStatus, "structureStatus");
            attemptStatus = Objects.requireNonNull(attemptStatus, "attemptStatus");
            outcome = Objects.requireNonNull(outcome, "outcome");
            before = List.copyOf(Objects.requireNonNull(before, "before"));
            after = List.copyOf(Objects.requireNonNull(after, "after"));
            ownedDelta = List.copyOf(Objects.requireNonNull(ownedDelta, "ownedDelta"));
            outOfEnvelope = immutableDistinctPositions(outOfEnvelope, "outOfEnvelope");
            if (ordinal < 0) {
                throw new IllegalArgumentException("case ordinal must be non-negative");
            }
            requireDistinctEvidence(before, "before");
            requireDistinctEvidence(after, "after");
            requireDistinctOwned(ownedDelta, "ownedDelta");
            if (outcome == CaseOutcome.DEFERRED) {
                if (structureStatus != StructureStatus.DEFERRED
                        || attemptStatus != AttemptStatus.NOT_ATTEMPTED
                        || !before.isEmpty() || !after.isEmpty() || !ownedDelta.isEmpty()) {
                    throw new IllegalArgumentException(
                            "deferred case result must contain no mutation evidence");
                }
            } else if (attemptStatus != AttemptStatus.ATTEMPTED
                    || structureStatus == StructureStatus.DEFERRED) {
                throw new IllegalArgumentException(
                        "AUTO case result must record an attempted non-deferred structure");
            }
            for (OwnedCellEvidence cell : ownedDelta) {
                if (!cell.caseId().equals(caseId)) {
                    throw new IllegalArgumentException(
                            "owned case evidence must name its enclosing case");
                }
            }
        }
    }

    public record FinalPage(
            String identityHash,
            String plannedHash,
            String predecessorFinalHash,
            int page,
            List<String> orderedCaseResultHashes,
            String ownershipHash,
            List<OwnedCellEvidence> ownedCells,
            boolean infrastructureComplete) {
        public FinalPage {
            identityHash = requireHash(identityHash, "identityHash");
            plannedHash = requireHash(plannedHash, "plannedHash");
            if (!GENESIS.equals(predecessorFinalHash)) {
                predecessorFinalHash = requireHash(
                        predecessorFinalHash, "predecessorFinalHash");
            }
            orderedCaseResultHashes = List.copyOf(
                    Objects.requireNonNull(orderedCaseResultHashes, "orderedCaseResultHashes"));
            ownershipHash = requireHash(ownershipHash, "ownershipHash");
            ownedCells = List.copyOf(Objects.requireNonNull(ownedCells, "ownedCells"));
            if (page < 1 || orderedCaseResultHashes.isEmpty()
                    || orderedCaseResultHashes.stream().anyMatch(hash -> !isHash(hash))) {
                throw new IllegalArgumentException("invalid final cases page");
            }
            requireDistinctOwned(ownedCells, "final ownedCells");
            if (!ownershipHash.equals(
                    SlabbedRigCaseEvidence.ownershipHash(ownedCells))) {
                throw new IllegalArgumentException(
                        "final ownership hash must bind exact ordered cells");
            }
        }
    }

    public record SealedPage(String finalHash, FinalPage finalPage) {
        public SealedPage {
            finalHash = requireHash(finalHash, "finalHash");
            finalPage = Objects.requireNonNull(finalPage, "finalPage");
        }
    }

    /** Exact durable tombstone for one board that was removed from the world. */
    public record BoardRelease(
            String identityHash,
            String boardFinalHash,
            String predecessorReleaseHash,
            ProgressCursor cursorBefore,
            String ownershipHash,
            List<OwnedCellEvidence> releasedCells) {
        public BoardRelease {
            identityHash = requireHash(identityHash, "identityHash");
            boardFinalHash = requireHash(boardFinalHash, "boardFinalHash");
            if (!GENESIS.equals(predecessorReleaseHash)) {
                predecessorReleaseHash = requireHash(
                        predecessorReleaseHash, "predecessorReleaseHash");
            }
            cursorBefore = Objects.requireNonNull(cursorBefore, "cursorBefore");
            if (!cursorBefore.identityHash().equals(identityHash)
                    || !cursorBefore.boardFinalHash().equals(boardFinalHash)
                    || !cursorBefore.lastFinalHash().equals(boardFinalHash)
                    || !cursorBefore.lastReleaseHash().equals(predecessorReleaseHash)) {
                throw new IllegalArgumentException(
                        "board release must bind its exact pre-release cursor");
            }
            ownershipHash = requireHash(ownershipHash, "ownershipHash");
            releasedCells = List.copyOf(Objects.requireNonNull(releasedCells, "releasedCells"));
            requireDistinctOwned(releasedCells, "releasedCells");
            if (!ownershipHash.equals(SlabbedRigCaseEvidence.ownershipHash(releasedCells))) {
                throw new IllegalArgumentException(
                        "board release ownership hash must bind exact ordered cells");
            }
        }

        public String cursorHashBefore() {
            return cursorBefore.cursorHash();
        }

        public String activePlannedHash() {
            return cursorBefore.active() == null
                    ? NONE : cursorBefore.active().plannedHash();
        }
    }

    public record ReleasedBoard(String releaseHash, BoardRelease release) {
        public ReleasedBoard {
            releaseHash = requireHash(releaseHash, "releaseHash");
            release = Objects.requireNonNull(release, "release");
        }
    }

    public record ResumeView(
            ResumeStatus status,
            RuntimeIdentity identity,
            ProgressCursor cursor,
            List<OwnedCellEvidence> completedOwnership,
            List<OwnedCellEvidence> presentBoardOwnership,
            String detail) {
        public ResumeView {
            status = Objects.requireNonNull(status, "status");
            completedOwnership = List.copyOf(
                    Objects.requireNonNull(completedOwnership, "completedOwnership"));
            presentBoardOwnership = List.copyOf(
                    Objects.requireNonNull(presentBoardOwnership, "presentBoardOwnership"));
            detail = requireText(detail, "detail");
        }
    }

    public static final class Store {
        private final Path root;
        private final Path dimensionRoot;
        private final RuntimeIdentity identity;
        private final String identityHash;
        private final SlabbedRigCaseCatalog.Snapshot snapshot;
        private final ResumeStatus faultStatus;
        private final String faultDetail;

        private Store(
                Path root,
                Path dimensionRoot,
                RuntimeIdentity identity,
                String identityHash,
                SlabbedRigCaseCatalog.Snapshot snapshot,
                ResumeStatus faultStatus,
                String faultDetail) {
            this.root = root;
            this.dimensionRoot = dimensionRoot;
            this.identity = identity;
            this.identityHash = identityHash;
            this.snapshot = snapshot;
            this.faultStatus = faultStatus;
            this.faultDetail = faultDetail;
        }

        private static Store initialize(
                Path root,
                String dimensionId,
                String executorBuildHash,
                SlabbedRigCaseCatalog.Snapshot snapshot) throws IOException {
            createSafeDirectory(root);
            UUID worldId = readOrCreateWorldId(root.resolve("world-id"));
            byte[] catalogBytes = SlabbedRigCaseCatalog.catalogTsv(snapshot)
                    .getBytes(StandardCharsets.UTF_8);
            String catalogBlobHash = sha256(catalogBytes);
            Path dimensionRoot = dimensionRoot(root, dimensionId);
            createSafeDirectory(dimensionRoot);
            for (String kind : List.of(
                    "catalog", "identity", "planned", "case", "final", "release")) {
                createSafeDirectory(dimensionRoot.resolve("blobs").resolve(kind));
            }
            RuntimeIdentity identity = new RuntimeIdentity(
                    worldId,
                    dimensionId,
                    executorBuildHash,
                    snapshot.catalogHash(),
                    catalogBlobHash,
                    SlabbedRigCaseCatalog.EXECUTION_CONTRACT,
                    SlabbedRigService.CASES_LAYOUT_VERSION,
                    snapshot.pageCount());
            byte[] identityBytes = serializeIdentity(identity);
            String identityHash = sha256(identityBytes);
            Store store = new Store(
                    root, dimensionRoot, identity, identityHash, snapshot, null, null);
            Path cursorPath = store.cursorPath();
            if (Files.exists(cursorPath)) {
                ProgressCursor cursor = store.readCursor();
                if (!cursor.identityHash().equals(identityHash)) {
                    throw new IdentityMismatch(
                            "stored cases cursor belongs to a different runtime/world/catalog identity");
                }
                store.readBlob("identity", identityHash, "tsv");
                store.readBlob("catalog", catalogBlobHash, "tsv");
                store.verifyReferences(cursor);
            } else {
                store.writeBlob("catalog", catalogBytes, "tsv");
                store.writeBlob("identity", identityBytes, "tsv");
                store.writeCursor(signCursor(
                        0L, identityHash, 1, GENESIS, GENESIS, GENESIS, null));
            }
            return store;
        }

        private static Store fault(
                Path root,
                ResumeStatus status,
                String detail) {
            return new Store(
                    root, root, null, null, null,
                    Objects.requireNonNull(status, "status"),
                    requireText(detail, "detail"));
        }

        public Path root() {
            return root;
        }

        public RuntimeIdentity identity() {
            return identity;
        }

        public String identityHash() {
            return identityHash;
        }

        public ResumeView inspect() {
            if (faultStatus != null) {
                return new ResumeView(
                        faultStatus, identity, null, List.of(), List.of(), faultDetail);
            }
            try {
                ProgressCursor cursor = readCursor();
                verifyReferences(cursor);
                List<OwnedCellEvidence> presentBoard = presentBoardOwnership(cursor);
                if (cursor.active() != null) {
                    ParsedPlan plan = readPlan(cursor.active().plannedHash());
                    List<OwnedCellEvidence> completed = completedOwnership(cursor.active(), plan);
                    if (cursor.active().phase() == AttemptPhase.PREPARED) {
                        return new ResumeView(
                                ResumeStatus.PREPARED_NO_MUTATION,
                                identity,
                                cursor,
                                completed,
                                presentBoard,
                                "page " + cursor.active().page()
                                        + " is durably prepared and no execution may have begun");
                    }
                    Optional<SealedPage> pending = pendingFinal(cursor, plan, completed);
                    if (pending.isPresent()) {
                        return new ResumeView(
                                ResumeStatus.FINAL_PENDING_CURSOR,
                                identity,
                                cursor,
                                completed,
                                presentBoard,
                                "page " + cursor.active().page()
                                        + " is sealed and awaits one atomic cursor advance");
                    }
                    if (cursor.active().nextCaseOrdinal() == plan.caseIds().size()) {
                        return new ResumeView(
                                ResumeStatus.COMPLETED_PENDING_FINAL,
                                identity,
                                cursor,
                                completed,
                                presentBoard,
                                "page " + cursor.active().page()
                                        + " has every durable case and awaits finalization");
                    }
                    return new ResumeView(
                            ResumeStatus.INTERRUPTED_UNKNOWN_OWNERSHIP,
                            identity,
                            cursor,
                            completed,
                            presentBoard,
                            "page " + cursor.active().page() + " has "
                                    + cursor.active().nextCaseOrdinal() + "/"
                                    + plan.caseIds().size()
                                    + " durable cases; the current case may have unknown ownership");
                }
                ResumeStatus status;
                if (cursor.nextPage() > identity.pageCount()) {
                    status = ResumeStatus.COMPLETE;
                } else if (cursor.generation() == 0L
                        && cursor.nextPage() == 1
                        && cursor.lastFinalHash().equals(GENESIS)
                        && cursor.boardFinalHash().equals(GENESIS)
                        && cursor.lastReleaseHash().equals(GENESIS)) {
                    status = ResumeStatus.FRESH;
                } else {
                    status = ResumeStatus.READY;
                }
                return new ResumeView(
                        status,
                        identity,
                        cursor,
                        presentBoard,
                        presentBoard,
                        status == ResumeStatus.COMPLETE
                                ? "all cases pages have a contiguous final chain"
                                : "page " + cursor.nextPage() + " is ready for write-ahead admission");
            } catch (IdentityMismatch mismatch) {
                return new ResumeView(
                        ResumeStatus.IDENTITY_MISMATCH,
                        identity,
                        null,
                        List.of(),
                        List.of(),
                        mismatch.getMessage());
            } catch (IOException | RuntimeException failure) {
                return new ResumeView(
                        ResumeStatus.CORRUPT,
                        identity,
                        null,
                        List.of(),
                        List.of(),
                        failureDetail(failure));
            }
        }

        /** Returns a token only after both planned bytes and the active cursor are durable. */
        public PreparedPage prepare(SlabbedRigService.CasesPagePlan plan) {
            requireHealthy();
            Objects.requireNonNull(plan, "plan");
            try {
                ProgressCursor cursor = readCursor();
                verifyReferences(cursor);
                if (cursor.active() != null
                        || cursor.nextPage() > identity.pageCount()
                        || plan.page().page() != cursor.nextPage()
                        || plan.page().pageCount() != identity.pageCount()
                        || !plan.catalogSchema().equals(SlabbedRigCaseCatalog.SCHEMA)
                        || !plan.catalogHash().equals(identity.catalogHash())
                        || !plan.layoutVersion().equals(identity.layoutVersion())) {
                    throw new IllegalStateException(
                            "cases page is not the next identity-matched contiguous plan");
                }
                UUID runId = UUID.randomUUID();
                byte[] plannedBytes = serializePlanned(
                        identityHash, runId, cursor.lastFinalHash(), plan);
                String plannedHash = writeBlob("planned", plannedBytes, "tsv");
                ActiveAttempt active = new ActiveAttempt(
                        runId,
                        plan.page().page(),
                        plannedHash,
                        AttemptPhase.PREPARED,
                        0,
                        List.of(),
                        EMPTY_OWNERSHIP_HASH);
                ProgressCursor admitted = signCursor(
                        cursor.generation() + 1,
                        identityHash,
                        cursor.nextPage(),
                        cursor.lastFinalHash(),
                        cursor.boardFinalHash(),
                        cursor.lastReleaseHash(),
                        active);
                writeCursor(admitted);
                return new PreparedPage(
                        identityHash,
                        runId,
                        plan.page().page(),
                        plannedHash,
                        admitted.cursorHash());
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "could not durably prepare cases page", failure);
            }
        }

        /**
         * Durably crosses the last boundary before fixture or proxy mutation may begin.
         * A PREPARED cursor is therefore always safe for {@link #abortPreparedPage()}.
         */
        public PreparedPage beginExecution(PreparedPage prepared) {
            requireHealthy();
            Objects.requireNonNull(prepared, "prepared");
            try {
                ProgressCursor cursor = readCursor();
                verifyReferences(cursor);
                ActiveAttempt active = requirePrepared(cursor, prepared);
                if (active.phase() != AttemptPhase.PREPARED) {
                    throw new IllegalStateException(
                            "cases page is not awaiting its execution boundary");
                }
                ActiveAttempt executing = new ActiveAttempt(
                        active.runId(),
                        active.page(),
                        active.plannedHash(),
                        AttemptPhase.EXECUTING,
                        active.nextCaseOrdinal(),
                        active.completedCaseHashes(),
                        active.accumulatedOwnershipHash());
                ProgressCursor next = signCursor(
                        cursor.generation() + 1,
                        cursor.identityHash(),
                        cursor.nextPage(),
                        cursor.lastFinalHash(),
                        cursor.boardFinalHash(),
                        cursor.lastReleaseHash(),
                        executing);
                writeCursor(next);
                return new PreparedPage(
                        prepared.identityHash(),
                        prepared.runId(),
                        prepared.page(),
                        prepared.plannedHash(),
                        next.cursorHash());
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "could not durably begin cases execution", failure);
            }
        }

        /**
         * Atomically abandons only a successor plan that is durably known never to have crossed
         * the execution boundary. Board ownership and release history remain unchanged.
         */
        public void abortPreparedPage() {
            requireHealthy();
            try {
                ProgressCursor cursor = readCursor();
                verifyReferences(cursor);
                ActiveAttempt active = cursor.active();
                if (active == null
                        || active.phase() != AttemptPhase.PREPARED
                        || active.nextCaseOrdinal() != 0
                        || !active.completedCaseHashes().isEmpty()
                        || !active.accumulatedOwnershipHash().equals(EMPTY_OWNERSHIP_HASH)) {
                    throw new IllegalStateException(
                            "only a mutation-free prepared cases page may be aborted");
                }
                ProgressCursor next = signCursor(
                        cursor.generation() + 1,
                        cursor.identityHash(),
                        cursor.nextPage(),
                        cursor.lastFinalHash(),
                        cursor.boardFinalHash(),
                        cursor.lastReleaseHash(),
                        null);
                writeCursor(next);
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "could not atomically abort prepared cases page", failure);
            }
        }

        /**
         * Publishes one terminal case before the next case may begin. An infrastructure failure
         * writes no cursor reference and therefore cannot silently advance evidence.
         */
        public String checkpoint(
                PreparedPage prepared,
                CaseResult result) {
            requireHealthy();
            Objects.requireNonNull(prepared, "prepared");
            Objects.requireNonNull(result, "result");
            try {
                ProgressCursor cursor = readCursor();
                ActiveAttempt active = requirePrepared(cursor, prepared);
                if (active.phase() != AttemptPhase.EXECUTING) {
                    throw new IllegalStateException(
                            "cases result cannot checkpoint before execution begins");
                }
                ParsedPlan plan = readPlan(active.plannedHash());
                int ordinal = active.nextCaseOrdinal();
                if (ordinal >= plan.caseIds().size()
                        || result.ordinal() != ordinal
                        || !result.caseId().equals(plan.caseIds().get(ordinal))) {
                    throw new IllegalStateException(
                            "case checkpoint is not the next exact planned ordinal");
                }
                validateResultForPlan(result, plan, ordinal);
                List<OwnedCellEvidence> previousOwnership =
                        completedOwnership(active, plan);
                Set<BlockPos> previousPositions = previousOwnership.stream()
                        .map(OwnedCellEvidence::pos)
                        .collect(java.util.stream.Collectors.toSet());
                if (result.ownedDelta().stream()
                        .map(OwnedCellEvidence::pos)
                        .anyMatch(previousPositions::contains)) {
                    throw new IllegalStateException(
                            "case checkpoint duplicates previously owned evidence");
                }
                byte[] resultBytes = serializeResult(
                        identityHash, active.plannedHash(), result);
                String resultHash = writeBlob("case", resultBytes, "tsv");
                List<String> hashes = new ArrayList<>(active.completedCaseHashes());
                hashes.add(resultHash);
                List<OwnedCellEvidence> ownership = new ArrayList<>(previousOwnership);
                ownership.addAll(result.ownedDelta());
                ActiveAttempt advanced = new ActiveAttempt(
                        active.runId(),
                        active.page(),
                        active.plannedHash(),
                        AttemptPhase.EXECUTING,
                        ordinal + 1,
                        hashes,
                        ownershipHash(ownership));
                ProgressCursor next = signCursor(
                        cursor.generation() + 1,
                        cursor.identityHash(),
                        cursor.nextPage(),
                        cursor.lastFinalHash(),
                        cursor.boardFinalHash(),
                        cursor.lastReleaseHash(),
                        advanced);
                writeCursor(next);
                return resultHash;
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "could not durably checkpoint cases result", failure);
            }
        }

        /** Writes the immutable page final but deliberately leaves the cursor active. */
        public SealedPage seal(PreparedPage prepared) {
            requireHealthy();
            Objects.requireNonNull(prepared, "prepared");
            try {
                ProgressCursor cursor = readCursor();
                ActiveAttempt active = requirePrepared(cursor, prepared);
                if (active.phase() != AttemptPhase.EXECUTING) {
                    throw new IllegalStateException(
                            "cases page cannot seal before execution begins");
                }
                ParsedPlan plan = readPlan(active.plannedHash());
                if (active.nextCaseOrdinal() != plan.caseIds().size()) {
                    throw new IllegalStateException(
                            "cases page cannot seal before every case is terminal");
                }
                List<OwnedCellEvidence> ownership = completedOwnership(active, plan);
                FinalPage finalPage = new FinalPage(
                        identityHash,
                        active.plannedHash(),
                        cursor.lastFinalHash(),
                        active.page(),
                        active.completedCaseHashes(),
                        ownershipHash(ownership),
                        ownership,
                        true);
                String finalHash = writeBlob(
                        "final", serializeFinal(finalPage), "tsv");
                return new SealedPage(finalHash, finalPage);
            } catch (IOException failure) {
                throw new IllegalStateException("could not seal cases page", failure);
            }
        }

        /** Atomically advances only the exact contiguous page named by a verified final. */
        public FinalPage advance(SealedPage sealed) {
            requireHealthy();
            Objects.requireNonNull(sealed, "sealed");
            try {
                ProgressCursor cursor = readCursor();
                verifyReferences(cursor);
                ActiveAttempt active = cursor.active();
                if (!cursor.boardFinalHash().equals(GENESIS)) {
                    throw new IllegalStateException(
                            "present cases board must be released before advancing its replacement");
                }
                if (active == null
                        || sealed.finalPage().page() != cursor.nextPage()
                        || !sealed.finalPage().identityHash().equals(identityHash)
                        || !sealed.finalPage().plannedHash().equals(active.plannedHash())
                        || !sealed.finalPage().predecessorFinalHash()
                                .equals(cursor.lastFinalHash())
                        || !sealed.finalPage().orderedCaseResultHashes()
                                .equals(active.completedCaseHashes())
                        || !sealed.finalPage().ownershipHash()
                                .equals(active.accumulatedOwnershipHash())) {
                    throw new IllegalStateException(
                            "sealed cases page does not match the active contiguous cursor");
                }
                FinalPage disk = readFinal(sealed.finalHash());
                if (!disk.equals(sealed.finalPage())) {
                    throw new IllegalStateException(
                            "sealed cases page bytes do not match the supplied final");
                }
                ProgressCursor next = signCursor(
                        cursor.generation() + 1,
                        cursor.identityHash(),
                        cursor.nextPage() + 1,
                        sealed.finalHash(),
                        sealed.finalHash(),
                        cursor.lastReleaseHash(),
                        null);
                writeCursor(next);
                return sealed.finalPage();
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "could not atomically advance cases cursor", failure);
            }
        }

        public FinalPage finish(PreparedPage prepared) {
            return advance(seal(prepared));
        }

        /** Repairs only an active page whose complete result chain is already durable. */
        public FinalPage repairCompletedPage() {
            requireHealthy();
            try {
                ProgressCursor cursor = readCursor();
                verifyReferences(cursor);
                ActiveAttempt active = cursor.active();
                if (active == null) {
                    throw new IllegalStateException("no completed cases page needs finalization");
                }
                if (active.phase() != AttemptPhase.EXECUTING) {
                    throw new IllegalStateException(
                            "prepared cases page has not begun execution");
                }
                ParsedPlan plan = readPlan(active.plannedHash());
                if (active.nextCaseOrdinal() != plan.caseIds().size()) {
                    throw new IllegalStateException(
                            "active cases page still has an unknown current case");
                }
                PreparedPage prepared = new PreparedPage(
                        identityHash,
                        active.runId(),
                        active.page(),
                        active.plannedHash(),
                        cursor.cursorHash());
                return finish(prepared);
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "could not finalize completed cases page", failure);
            }
        }

        /** Repairs only the deterministic final-written/cursor-not-advanced crash window. */
        public FinalPage repairFinalCursor() {
            requireHealthy();
            try {
                ProgressCursor cursor = readCursor();
                if (cursor.active() == null) {
                    throw new IllegalStateException("no active cases page needs final repair");
                }
                if (cursor.active().phase() != AttemptPhase.EXECUTING) {
                    throw new IllegalStateException(
                            "prepared cases page has no pending executed final");
                }
                ParsedPlan plan = readPlan(cursor.active().plannedHash());
                List<OwnedCellEvidence> ownership = completedOwnership(cursor.active(), plan);
                SealedPage sealed = pendingFinal(cursor, plan, ownership)
                        .orElseThrow(() -> new IllegalStateException(
                                "active cases page has no exact pending final"));
                return advance(sealed);
            } catch (IOException failure) {
                throw new IllegalStateException("could not repair cases cursor", failure);
            }
        }

        /**
         * Publishes the exact durable tombstone for the board the caller has already cleared.
         * This method never mutates the world and refuses to infer or broaden ownership.
         */
        public ReleasedBoard releasePresentBoard() {
            requireHealthy();
            try {
                ProgressCursor cursor = readCursor();
                verifyReferences(cursor);
                if (cursor.boardFinalHash().equals(GENESIS)) {
                    throw new IllegalStateException("no finalized cases board is present to release");
                }
                FinalPage board = readFinal(cursor.boardFinalHash());
                BoardRelease release = new BoardRelease(
                        identityHash,
                        cursor.boardFinalHash(),
                        cursor.lastReleaseHash(),
                        cursor,
                        board.ownershipHash(),
                        board.ownedCells());
                String releaseHash = writeBlob(
                        "release", serializeRelease(release), "tsv");
                ProgressCursor next = signCursor(
                        cursor.generation() + 1,
                        cursor.identityHash(),
                        cursor.nextPage(),
                        cursor.lastFinalHash(),
                        GENESIS,
                        releaseHash,
                        cursor.active());
                writeCursor(next);
                return new ReleasedBoard(releaseHash, release);
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "could not durably release exact cases board ownership", failure);
            }
        }

        /** Evidence only; an interrupted current case still blocks clear and automatic resume. */
        public List<OwnedCellEvidence> rehydrateCompletedOwnership() {
            requireHealthy();
            try {
                ProgressCursor cursor = readCursor();
                verifyReferences(cursor);
                if (cursor.active() != null) {
                    return completedOwnership(
                            cursor.active(), readPlan(cursor.active().plannedHash()));
                }
                return presentBoardOwnership(cursor);
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "could not rehydrate exact completed ownership", failure);
            }
        }

        /** Exact finalized board ownership currently claimed to remain in the world. */
        public List<OwnedCellEvidence> rehydratePresentBoardOwnership() {
            requireHealthy();
            try {
                ProgressCursor cursor = readCursor();
                verifyReferences(cursor);
                return presentBoardOwnership(cursor);
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "could not rehydrate exact present board ownership", failure);
            }
        }

        /** Returns the only page that may be prepared; interruption never auto-resumes. */
        public int resolveResumePage() {
            ResumeView view = inspect();
            if (view.status() != ResumeStatus.FRESH && view.status() != ResumeStatus.READY) {
                throw new IllegalStateException(
                        "cases store is not safe to resume automatically: " + view.status());
            }
            return view.cursor().nextPage();
        }

        private Optional<SealedPage> pendingFinal(
                ProgressCursor cursor,
                ParsedPlan plan,
                List<OwnedCellEvidence> ownership) throws IOException {
            ActiveAttempt active = cursor.active();
            if (active == null || active.nextCaseOrdinal() != plan.caseIds().size()) {
                return Optional.empty();
            }
            FinalPage finalPage = new FinalPage(
                    identityHash,
                    active.plannedHash(),
                    cursor.lastFinalHash(),
                    active.page(),
                    active.completedCaseHashes(),
                    ownershipHash(ownership),
                    ownership,
                    true);
            byte[] bytes = serializeFinal(finalPage);
            String hash = sha256(bytes);
            Path path = blobPath("final", hash, "tsv");
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            if (!java.util.Arrays.equals(bytes, readBlob("final", hash, "tsv"))) {
                throw new IllegalStateException("pending final bytes differ from exact reconstruction");
            }
            return Optional.of(new SealedPage(hash, finalPage));
        }

        private ActiveAttempt requirePrepared(
                ProgressCursor cursor,
                PreparedPage prepared) {
            ActiveAttempt active = cursor.active();
            if (!prepared.identityHash().equals(identityHash)
                    || active == null
                    || !active.runId().equals(prepared.runId())
                    || active.page() != prepared.page()
                    || !active.plannedHash().equals(prepared.plannedHash())) {
                throw new IllegalStateException(
                        "prepared token does not match the active cases cursor");
            }
            return active;
        }

        private void validateResultForPlan(
                CaseResult result,
                ParsedPlan plan,
                int ordinal) {
            PlannedCase plannedCase = plan.cases().get(ordinal);
            List<BlockPos> reserved = plannedCase.reservedCells();
            if (plannedCase.disposition()
                    == SlabbedRigCaseCatalog.Disposition.DEFERRED_ROUTE) {
                if (result.outcome() != CaseOutcome.DEFERRED
                        || result.attemptStatus() != AttemptStatus.NOT_ATTEMPTED
                        || result.structureStatus() != StructureStatus.DEFERRED
                        || !result.inventoryRestored()
                        || !result.outOfEnvelope().isEmpty()) {
                    throw new IllegalStateException(
                            "deferred case must remain named, terminal, and mutation-free");
                }
                return;
            }
            if (result.outcome() == CaseOutcome.DEFERRED
                    || result.attemptStatus() != AttemptStatus.ATTEMPTED
                    || !result.inventoryRestored()
                    || !result.outOfEnvelope().isEmpty()) {
                throw new IllegalStateException(
                        "AUTO case lacks clean terminal infrastructure evidence");
            }
            List<BlockPos> beforePositions = result.before().stream()
                    .map(CellEvidence::pos).toList();
            List<BlockPos> afterPositions = result.after().stream()
                    .map(CellEvidence::pos).toList();
            if (!beforePositions.equals(reserved) || !afterPositions.equals(reserved)) {
                throw new IllegalStateException(
                        "AUTO case before/after evidence must cover the exact reserved cells");
            }
            List<BlockPos> changed = new ArrayList<>();
            for (int index = 0; index < reserved.size(); index++) {
                CellEvidence before = result.before().get(index);
                CellEvidence after = result.after().get(index);
                if (!before.state().equals(after.state())
                        || !before.storedDy().equals(after.storedDy())) {
                    changed.add(before.pos());
                }
            }
            if (changed.stream().anyMatch(plannedCase.guardCells()::contains)) {
                throw new IllegalStateException(
                        "case changed a read-only guard cell");
            }
            List<BlockPos> ownedPositions = result.ownedDelta().stream()
                    .map(OwnedCellEvidence::pos).toList();
            if (!ownedPositions.equals(changed)) {
                throw new IllegalStateException(
                        "case ownership must equal every exact persisted changed cell");
            }
            Set<BlockPos> mutationSet = Set.copyOf(plannedCase.mutationCells());
            for (OwnedCellEvidence owned : result.ownedDelta()) {
                if (!mutationSet.contains(owned.pos())) {
                    throw new IllegalStateException(
                            "case claimed ownership outside permitted mutation cells");
                }
                CellEvidence after = result.after().stream()
                        .filter(cell -> cell.pos().equals(owned.pos()))
                        .findFirst()
                        .orElseThrow();
                if (!after.state().equals(owned.expectedState())
                        || !after.storedDy().equals(owned.expectedStoredDy())) {
                    throw new IllegalStateException(
                            "owned evidence must equal exact after-state truth");
                }
            }
        }

        private List<OwnedCellEvidence> completedOwnership(
                ActiveAttempt active,
                ParsedPlan plan) throws IOException {
            if (active.completedCaseHashes().size() > plan.caseIds().size()) {
                throw new IllegalStateException(
                        "cursor has more results than planned cases");
            }
            List<OwnedCellEvidence> ownership = new ArrayList<>();
            Set<BlockPos> positions = new HashSet<>();
            for (int ordinal = 0; ordinal < active.completedCaseHashes().size(); ordinal++) {
                StoredCaseResult stored = readResult(active.completedCaseHashes().get(ordinal));
                if (!stored.identityHash().equals(identityHash)
                        || !stored.plannedHash().equals(active.plannedHash())
                        || stored.result().ordinal() != ordinal
                        || !stored.result().caseId().equals(plan.caseIds().get(ordinal))) {
                    throw new IllegalStateException(
                            "case result chain disagrees with its planned ordinal");
                }
                validateResultForPlan(stored.result(), plan, ordinal);
                for (OwnedCellEvidence owned : stored.result().ownedDelta()) {
                    if (!positions.add(owned.pos())) {
                        throw new IllegalStateException(
                                "completed cases duplicate an owned cell");
                    }
                    ownership.add(owned);
                }
            }
            if (!ownershipHash(ownership).equals(active.accumulatedOwnershipHash())) {
                throw new IllegalStateException(
                        "active ownership hash disagrees with completed cases");
            }
            return List.copyOf(ownership);
        }

        private List<OwnedCellEvidence> presentBoardOwnership(
                ProgressCursor cursor) throws IOException {
            if (cursor.boardFinalHash().equals(GENESIS)) {
                return List.of();
            }
            return readFinal(cursor.boardFinalHash()).ownedCells();
        }

        private void verifyReferences(ProgressCursor cursor) throws IOException {
            if (!cursor.identityHash().equals(identityHash)) {
                throw new IdentityMismatch("cases cursor identity changed");
            }
            readBlob("identity", identityHash, "tsv");
            readBlob("catalog", identity.catalogBlobHash(), "tsv");
            Set<String> finalHashes = verifyFinalChain(cursor);
            Set<String> releasedBoards = verifyReleaseChain(cursor, finalHashes);
            if (cursor.boardFinalHash().equals(GENESIS)) {
                if (!cursor.lastFinalHash().equals(GENESIS)
                        && !releasedBoards.contains(cursor.lastFinalHash())) {
                    throw new IllegalStateException(
                            "latest finalized cases board is neither present nor released");
                }
            } else {
                if (!cursor.boardFinalHash().equals(cursor.lastFinalHash())
                        || !finalHashes.contains(cursor.boardFinalHash())
                        || releasedBoards.contains(cursor.boardFinalHash())) {
                    throw new IllegalStateException(
                            "present cases board disagrees with final/release history");
                }
                readFinal(cursor.boardFinalHash());
            }
            if (cursor.active() != null) {
                ParsedPlan plan = readPlan(cursor.active().plannedHash());
                if (!plan.identityHash().equals(identityHash)
                        || plan.page() != cursor.nextPage()
                        || !plan.predecessorFinalHash().equals(cursor.lastFinalHash())) {
                    throw new IllegalStateException(
                            "active plan identity/page/predecessor disagrees with cursor");
                }
                completedOwnership(cursor.active(), plan);
            }
        }

        private Set<String> verifyFinalChain(ProgressCursor cursor) throws IOException {
            String hash = cursor.lastFinalHash();
            int expectedPage = cursor.nextPage() - 1;
            Set<String> seen = new LinkedHashSet<>();
            while (!hash.equals(GENESIS)) {
                if (!seen.add(hash)) {
                    throw new IllegalStateException("cases final chain contains a cycle");
                }
                FinalPage page = readFinal(hash);
                if (!page.identityHash().equals(identityHash)
                        || page.page() != expectedPage
                        || !page.infrastructureComplete()) {
                    throw new IllegalStateException(
                            "cases final chain is not contiguous or infrastructure-clean");
                }
                ParsedPlan plan = readPlan(page.plannedHash());
                if (!plan.identityHash().equals(identityHash)
                        || plan.page() != page.page()
                        || !plan.predecessorFinalHash().equals(page.predecessorFinalHash())
                        || page.orderedCaseResultHashes().size() != plan.caseIds().size()) {
                    throw new IllegalStateException(
                            "cases final does not bind its exact plan and predecessor");
                }
                List<OwnedCellEvidence> reconstructed = completedOwnership(
                        page.plannedHash(), page.orderedCaseResultHashes(), plan);
                if (!page.ownedCells().equals(reconstructed)
                        || !page.ownershipHash().equals(ownershipHash(reconstructed))) {
                    throw new IllegalStateException(
                            "cases final ownership disagrees with its exact result chain");
                }
                hash = page.predecessorFinalHash();
                expectedPage--;
            }
            if (expectedPage != 0) {
                throw new IllegalStateException(
                        "cases final chain does not terminate at page one");
            }
            return Set.copyOf(seen);
        }

        private Set<String> verifyReleaseChain(
                ProgressCursor cursor,
                Set<String> finalHashes) throws IOException {
            String hash = cursor.lastReleaseHash();
            int expectedPage = cursor.nextPage() - 1;
            if (!cursor.boardFinalHash().equals(GENESIS)) {
                expectedPage--;
            }
            Set<String> seenReleases = new HashSet<>();
            Set<String> releasedBoards = new LinkedHashSet<>();
            while (expectedPage > 0) {
                if (hash.equals(GENESIS)) {
                    throw new IllegalStateException(
                            "cases release chain omits a finalized board");
                }
                if (!seenReleases.add(hash)) {
                    throw new IllegalStateException("cases release chain contains a cycle");
                }
                BoardRelease release = readRelease(hash);
                if (!release.identityHash().equals(identityHash)
                        || !finalHashes.contains(release.boardFinalHash())
                        || !releasedBoards.add(release.boardFinalHash())) {
                    throw new IllegalStateException(
                            "cases release does not name one unique finalized board");
                }
                FinalPage board = readFinal(release.boardFinalHash());
                if (board.page() != expectedPage
                        || !board.ownershipHash().equals(release.ownershipHash())
                        || !board.ownedCells().equals(release.releasedCells())) {
                    throw new IllegalStateException(
                            "cases release order or ownership disagrees with its exact final board");
                }
                ProgressCursor before = release.cursorBefore();
                if (before.nextPage() != board.page() + 1) {
                    throw new IllegalStateException(
                            "cases release pre-cursor disagrees with its exact final board");
                }
                if (!release.activePlannedHash().equals(NONE)) {
                    ParsedPlan replacement = readPlan(release.activePlannedHash());
                    if (!replacement.predecessorFinalHash().equals(release.boardFinalHash())
                            || replacement.page() != board.page() + 1) {
                        throw new IllegalStateException(
                                "cases release replacement plan does not follow its board");
                    }
                }
                hash = release.predecessorReleaseHash();
                expectedPage--;
            }
            if (!hash.equals(GENESIS)) {
                throw new IllegalStateException(
                        "cases release chain contains an extra or reordered board");
            }
            return Set.copyOf(releasedBoards);
        }

        private ParsedPlan readPlan(String hash) throws IOException {
            byte[] bytes = readBlob("planned", hash, "tsv");
            ParsedPlan plan = parsePlanned(bytes);
            if (!plan.identityHash().equals(identityHash)
                    || plan.pageCount() != identity.pageCount()
                    || !plan.catalogSchema().equals(snapshot.schema())
                    || !plan.catalogHash().equals(identity.catalogHash())
                    || !plan.layoutVersion().equals(identity.layoutVersion())
                    || plan.tileSpacing() != SlabbedRigService.CASES_TILE_SPACING) {
                throw new IllegalStateException(
                        "cases plan headers disagree with runtime/world/catalog identity");
            }
            SlabbedRigService.CasesPagePlan canonical = SlabbedRigService.casesPagePlan(
                    snapshot, plan.anchor(), plan.facing(), plan.page());
            byte[] expected = serializePlanned(
                    identityHash,
                    plan.runId(),
                    plan.predecessorFinalHash(),
                    canonical);
            if (!java.util.Arrays.equals(bytes, expected)) {
                throw new IllegalStateException(
                        "cases plan rows disagree with the canonical catalog/layout plan");
            }
            return plan;
        }

        private List<OwnedCellEvidence> completedOwnership(
                String plannedHash,
                List<String> resultHashes,
                ParsedPlan plan) throws IOException {
            if (resultHashes.size() != plan.caseIds().size()) {
                throw new IllegalStateException(
                        "final cases result count disagrees with its plan");
            }
            List<OwnedCellEvidence> ownership = new ArrayList<>();
            Set<BlockPos> positions = new HashSet<>();
            for (int ordinal = 0; ordinal < resultHashes.size(); ordinal++) {
                StoredCaseResult stored = readResult(resultHashes.get(ordinal));
                if (!stored.identityHash().equals(identityHash)
                        || !stored.plannedHash().equals(plannedHash)
                        || stored.result().ordinal() != ordinal
                        || !stored.result().caseId().equals(plan.caseIds().get(ordinal))) {
                    throw new IllegalStateException(
                            "final case result chain disagrees with its planned ordinal");
                }
                validateResultForPlan(stored.result(), plan, ordinal);
                for (OwnedCellEvidence owned : stored.result().ownedDelta()) {
                    if (!positions.add(owned.pos())) {
                        throw new IllegalStateException(
                                "final cases duplicate an owned cell");
                    }
                    ownership.add(owned);
                }
            }
            return List.copyOf(ownership);
        }

        private StoredCaseResult readResult(String hash) throws IOException {
            return parseResult(readBlob("case", hash, "tsv"));
        }

        private FinalPage readFinal(String hash) throws IOException {
            return parseFinal(readBlob("final", hash, "tsv"));
        }

        private BoardRelease readRelease(String hash) throws IOException {
            return parseRelease(readBlob("release", hash, "tsv"));
        }

        private ProgressCursor readCursor() throws IOException {
            Path path = cursorPath();
            requireRegularFile(path);
            return parseCursor(Files.readAllBytes(path));
        }

        private void writeCursor(ProgressCursor cursor) throws IOException {
            writeAtomicReplace(cursorPath(), serializeCursor(cursor));
        }

        private String writeBlob(String kind, byte[] bytes, String extension)
                throws IOException {
            String hash = sha256(bytes);
            Path path = blobPath(kind, hash, extension);
            if (Files.exists(path)) {
                if (!java.util.Arrays.equals(bytes, readBlob(kind, hash, extension))) {
                    throw new IllegalStateException(
                            "content-addressed blob collision for " + hash);
                }
                return hash;
            }
            writeAtomicCreate(path, bytes);
            return hash;
        }

        private byte[] readBlob(String kind, String hash, String extension)
                throws IOException {
            requireHash(hash, "blob hash");
            Path path = blobPath(kind, hash, extension);
            requireRegularFile(path);
            byte[] bytes = Files.readAllBytes(path);
            if (!sha256(bytes).equals(hash)) {
                throw new IllegalStateException(
                        "blob filename hash disagrees with its bytes: " + path.getFileName());
            }
            return bytes;
        }

        private Path blobPath(String kind, String hash, String extension) {
            return dimensionRoot.resolve("blobs").resolve(kind)
                    .resolve(hash + "." + extension);
        }

        private Path cursorPath() {
            return dimensionRoot.resolve("cursor.tsv");
        }

        private void requireHealthy() {
            if (faultStatus != null) {
                throw new IllegalStateException(
                        "cases evidence store is " + faultStatus + ": " + faultDetail);
            }
        }
    }

    private record PlannedCase(
            String caseId,
            SlabbedRigCaseCatalog.Disposition disposition,
            List<RigCase.FixtureCell> fixtures,
            List<BlockPos> effectCells,
            List<BlockPos> mutationCells,
            List<BlockPos> guardCells,
            List<BlockPos> reservedCells) {
        private PlannedCase {
            caseId = requireText(caseId, "caseId");
            disposition = Objects.requireNonNull(disposition, "disposition");
            fixtures = List.copyOf(Objects.requireNonNull(fixtures, "fixtures"));
            effectCells = immutableDistinctPositions(effectCells, "effectCells");
            mutationCells = immutableDistinctPositions(mutationCells, "mutationCells");
            guardCells = immutableDistinctPositions(guardCells, "guardCells");
            reservedCells = immutableDistinctPositions(reservedCells, "reservedCells");
            LinkedHashSet<BlockPos> fixturePositions = new LinkedHashSet<>();
            for (RigCase.FixtureCell fixture : fixtures) {
                if (!fixturePositions.add(fixture.pos())) {
                    throw new IllegalArgumentException(
                            "planned case contains duplicate fixture cells");
                }
            }
            LinkedHashSet<BlockPos> expectedMutation = new LinkedHashSet<>(fixturePositions);
            expectedMutation.addAll(effectCells);
            if (fixtures.isEmpty()
                    || !mutationCells.equals(List.copyOf(expectedMutation))
                    || guardCells.stream().anyMatch(expectedMutation::contains)) {
                throw new IllegalArgumentException(
                        "planned mutation cells must be exact and guard-disjoint");
            }
            LinkedHashSet<BlockPos> expectedReserved = new LinkedHashSet<>(expectedMutation);
            expectedReserved.addAll(guardCells);
            if (!reservedCells.equals(List.copyOf(expectedReserved))) {
                throw new IllegalArgumentException(
                        "planned reservation must equal mutation/guard union");
            }
        }
    }

    private record ParsedPlan(
            String identityHash,
            UUID runId,
            int page,
            int pageCount,
            String predecessorFinalHash,
            String catalogSchema,
            String catalogHash,
            String layoutVersion,
            int tileSpacing,
            BlockPos anchor,
            Direction facing,
            List<PlannedCase> cases) {
        private ParsedPlan {
            identityHash = requireHash(identityHash, "identityHash");
            runId = Objects.requireNonNull(runId, "runId");
            if (!GENESIS.equals(predecessorFinalHash)) {
                predecessorFinalHash = requireHash(
                        predecessorFinalHash, "predecessorFinalHash");
            }
            cases = List.copyOf(Objects.requireNonNull(cases, "cases"));
            catalogSchema = requireText(catalogSchema, "catalogSchema");
            catalogHash = requireHash(catalogHash, "catalogHash");
            layoutVersion = requireText(layoutVersion, "layoutVersion");
            anchor = Objects.requireNonNull(anchor, "anchor").immutable();
            facing = Objects.requireNonNull(facing, "facing");
            if (page < 1 || pageCount < 1 || page > pageCount
                    || tileSpacing < 1 || !facing.getAxis().isHorizontal() || cases.isEmpty()) {
                throw new IllegalArgumentException("invalid parsed cases plan");
            }
        }

        private List<String> caseIds() {
            return cases.stream().map(PlannedCase::caseId).toList();
        }
    }

    private record StoredCaseResult(
            String identityHash,
            String plannedHash,
            CaseResult result) {
        private StoredCaseResult {
            identityHash = requireHash(identityHash, "identityHash");
            plannedHash = requireHash(plannedHash, "plannedHash");
            result = Objects.requireNonNull(result, "result");
        }
    }

    private static byte[] serializeIdentity(RuntimeIdentity identity) {
        String text = "schema\t" + SCHEMA + '\n'
                + "world_id\t" + identity.worldId() + '\n'
                + "dimension_id\t" + encode(identity.dimensionId()) + '\n'
                + "executor_build_hash\t" + identity.executorBuildHash() + '\n'
                + "catalog_hash\t" + identity.catalogHash() + '\n'
                + "catalog_blob_hash\t" + identity.catalogBlobHash() + '\n'
                + "execution_contract\t" + encode(identity.executionContract()) + '\n'
                + "layout_version\t" + encode(identity.layoutVersion()) + '\n'
                + "page_count\t" + identity.pageCount() + '\n';
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] serializePlanned(
            String identityHash,
            UUID runId,
            String predecessorFinalHash,
            SlabbedRigService.CasesPagePlan plan) {
        StringBuilder text = new StringBuilder();
        text.append("schema\t").append(PLAN_SCHEMA).append('\n');
        text.append("identity_hash\t").append(identityHash).append('\n');
        text.append("run_id\t").append(runId).append('\n');
        text.append("page\t").append(plan.page().page()).append('\n');
        text.append("page_count\t").append(plan.page().pageCount()).append('\n');
        text.append("predecessor_final_hash\t").append(predecessorFinalHash).append('\n');
        text.append("catalog_schema\t").append(encode(plan.catalogSchema())).append('\n');
        text.append("catalog_hash\t").append(plan.catalogHash()).append('\n');
        text.append("layout_version\t").append(encode(plan.layoutVersion())).append('\n');
        text.append("tile_spacing\t").append(plan.tileSpacing()).append('\n');
        appendPos(text, "anchor", plan.anchor());
        text.append("facing\t").append(plan.facing().name()).append('\n');
        text.append("case_count\t").append(plan.tiles().size()).append('\n');
        for (int ordinal = 0; ordinal < plan.tiles().size(); ordinal++) {
            SlabbedRigService.CasesTilePlan tile = plan.tiles().get(ordinal);
            text.append("case\t").append(ordinal).append('\t')
                    .append(encode(tile.definition().id())).append('\t')
                    .append(encode(tile.definition().item().id())).append('\t')
                    .append(encode(tile.definition().topology().id())).append('\t')
                    .append(tile.definition().item().disposition()).append('\t')
                    .append(tile.definition().item().effectPolicy()).append('\t')
                    .append(tile.row()).append('\t').append(tile.column()).append('\t');
            appendPosInline(text, tile.base());
            text.append('\t');
            appendPosInline(text, tile.cursor());
            text.append('\t');
            appendPosInline(text, tile.target());
            text.append('\n');
            for (RigCase.FixtureCell fixture : tile.fixtures()) {
                text.append("fixture\t").append(ordinal).append('\t');
                appendPosInline(text, fixture.pos());
                text.append('\t').append(encode(canonicalState(fixture.state()))).append('\n');
            }
            appendPositions(text, "effect", ordinal, tile.effectCells());
            appendPositions(text, "mutation", ordinal, tile.mutationCells());
            appendPositions(text, "guard", ordinal, tile.guardCells());
            appendPositions(text, "reserved", ordinal, tile.reservedCells());
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static ParsedPlan parsePlanned(byte[] bytes) {
        List<String[]> lines = splitLines(bytes);
        Map<String, String> fields = singletonFields(lines);
        if (!PLAN_SCHEMA.equals(fields.get("schema"))) {
            throw new IllegalArgumentException("unexpected cases plan schema");
        }
        int count = parseInt(fields, "case_count");
        if (count < 1) {
            throw new IllegalArgumentException("cases plan must contain cases");
        }
        Map<Integer, PlannedCaseBuilder> builders = new HashMap<>();
        BlockPos anchor = null;
        for (String[] line : lines) {
            if (line[0].equals("case")) {
                if (line.length != 18) {
                    throw new IllegalArgumentException("invalid cases plan case row");
                }
                int ordinal = Integer.parseInt(line[1]);
                if (ordinal < 0 || ordinal >= count) {
                    throw new IllegalArgumentException("cases plan ordinal is out of bounds");
                }
                PlannedCaseBuilder builder = builders.computeIfAbsent(
                        ordinal, ignored -> new PlannedCaseBuilder());
                if (builder.caseId != null || builder.disposition != null) {
                    throw new IllegalArgumentException("duplicate cases plan ordinal");
                }
                builder.caseId = decode(line[2]);
                builder.disposition = SlabbedRigCaseCatalog.Disposition.valueOf(line[5]);
            } else if (line[0].equals("fixture")) {
                if (line.length != 6) {
                    throw new IllegalArgumentException("invalid cases fixture row");
                }
                int ordinal = planOrdinal(line, count, "fixture");
                builders.computeIfAbsent(ordinal, ignored -> new PlannedCaseBuilder())
                        .fixtures.add(new RigCase.FixtureCell(
                                parsePos(line, 2), parseState(decode(line[5]))));
            } else if (line[0].equals("effect")
                    || line[0].equals("mutation")
                    || line[0].equals("guard")
                    || line[0].equals("reserved")) {
                if (line.length != 5) {
                    throw new IllegalArgumentException(
                            "invalid cases " + line[0] + " row");
                }
                int ordinal = planOrdinal(line, count, line[0]);
                PlannedCaseBuilder builder = builders.computeIfAbsent(
                        ordinal, ignored -> new PlannedCaseBuilder());
                BlockPos pos = parsePos(line, 2);
                switch (line[0]) {
                    case "effect" -> builder.effects.add(pos);
                    case "mutation" -> builder.mutations.add(pos);
                    case "guard" -> builder.guards.add(pos);
                    case "reserved" -> builder.reserved.add(pos);
                    default -> throw new IllegalStateException("unreachable planned row");
                }
            } else if (line[0].equals("anchor")) {
                if (line.length != 4 || anchor != null) {
                    throw new IllegalArgumentException("invalid or duplicate cases anchor row");
                }
                anchor = parsePos(line, 1);
            }
        }
        List<PlannedCase> cases = new ArrayList<>(count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            PlannedCaseBuilder builder = builders.get(ordinal);
            if (builder == null || builder.caseId == null || builder.disposition == null) {
                throw new IllegalArgumentException("cases plan ordinals are not dense");
            }
            cases.add(new PlannedCase(
                    builder.caseId,
                    builder.disposition,
                    builder.fixtures,
                    builder.effects,
                    builder.mutations,
                    builder.guards,
                    builder.reserved));
        }
        if (anchor == null) {
            throw new IllegalArgumentException("cases plan is missing its anchor");
        }
        return new ParsedPlan(
                fields.get("identity_hash"),
                UUID.fromString(fields.get("run_id")),
                parseInt(fields, "page"),
                parseInt(fields, "page_count"),
                fields.get("predecessor_final_hash"),
                decode(fields.get("catalog_schema")),
                fields.get("catalog_hash"),
                decode(fields.get("layout_version")),
                parseInt(fields, "tile_spacing"),
                anchor,
                Direction.valueOf(fields.get("facing")),
                cases);
    }

    private static int planOrdinal(String[] line, int count, String kind) {
        int ordinal = Integer.parseInt(line[1]);
        if (ordinal < 0 || ordinal >= count) {
            throw new IllegalArgumentException(
                    "cases " + kind + " ordinal is out of bounds");
        }
        return ordinal;
    }

    private static final class PlannedCaseBuilder {
        private String caseId;
        private SlabbedRigCaseCatalog.Disposition disposition;
        private final List<RigCase.FixtureCell> fixtures = new ArrayList<>();
        private final List<BlockPos> effects = new ArrayList<>();
        private final List<BlockPos> mutations = new ArrayList<>();
        private final List<BlockPos> guards = new ArrayList<>();
        private final List<BlockPos> reserved = new ArrayList<>();
    }

    private static byte[] serializeResult(
            String identityHash,
            String plannedHash,
            CaseResult result) {
        StringBuilder text = new StringBuilder();
        text.append("schema\t").append(RESULT_SCHEMA).append('\n');
        text.append("identity_hash\t").append(identityHash).append('\n');
        text.append("planned_hash\t").append(plannedHash).append('\n');
        text.append("ordinal\t").append(result.ordinal()).append('\n');
        text.append("case_id\t").append(encode(result.caseId())).append('\n');
        text.append("structure_status\t").append(result.structureStatus()).append('\n');
        text.append("attempt_status\t").append(result.attemptStatus()).append('\n');
        text.append("outcome\t").append(result.outcome()).append('\n');
        text.append("inventory_restored\t").append(result.inventoryRestored()).append('\n');
        appendCellEvidence(text, "before", result.before());
        appendCellEvidence(text, "after", result.after());
        appendOwnedEvidence(text, result.ownedDelta());
        appendPositions(text, "out_of_envelope", -1, result.outOfEnvelope());
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static StoredCaseResult parseResult(byte[] bytes) {
        List<String[]> lines = splitLines(bytes);
        Map<String, String> fields = singletonFields(lines);
        if (!RESULT_SCHEMA.equals(fields.get("schema"))) {
            throw new IllegalArgumentException("unexpected cases result schema");
        }
        List<CellEvidence> before = new ArrayList<>();
        List<CellEvidence> after = new ArrayList<>();
        List<OwnedCellEvidence> owned = new ArrayList<>();
        List<BlockPos> outside = new ArrayList<>();
        for (String[] line : lines) {
            switch (line[0]) {
                case "before" -> before.add(parseCellEvidence(line));
                case "after" -> after.add(parseCellEvidence(line));
                case "owned" -> owned.add(parseOwnedEvidence(line));
                case "out_of_envelope" -> outside.add(parsePos(line, 2));
                default -> { }
            }
        }
        CaseResult result = new CaseResult(
                parseInt(fields, "ordinal"),
                decode(fields.get("case_id")),
                StructureStatus.valueOf(fields.get("structure_status")),
                AttemptStatus.valueOf(fields.get("attempt_status")),
                CaseOutcome.valueOf(fields.get("outcome")),
                Boolean.parseBoolean(fields.get("inventory_restored")),
                before,
                after,
                owned,
                outside);
        return new StoredCaseResult(
                fields.get("identity_hash"), fields.get("planned_hash"), result);
    }

    private static byte[] serializeFinal(FinalPage finalPage) {
        StringBuilder text = new StringBuilder();
        text.append("schema\t").append(FINAL_SCHEMA).append('\n');
        text.append("identity_hash\t").append(finalPage.identityHash()).append('\n');
        text.append("planned_hash\t").append(finalPage.plannedHash()).append('\n');
        text.append("predecessor_final_hash\t")
                .append(finalPage.predecessorFinalHash()).append('\n');
        text.append("page\t").append(finalPage.page()).append('\n');
        text.append("infrastructure_complete\t")
                .append(finalPage.infrastructureComplete()).append('\n');
        text.append("ownership_hash\t").append(finalPage.ownershipHash()).append('\n');
        for (String hash : finalPage.orderedCaseResultHashes()) {
            text.append("case_result_hash\t").append(hash).append('\n');
        }
        appendOwnedEvidence(text, finalPage.ownedCells());
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static FinalPage parseFinal(byte[] bytes) {
        List<String[]> lines = splitLines(bytes);
        Map<String, String> fields = singletonFields(lines);
        if (!FINAL_SCHEMA.equals(fields.get("schema"))) {
            throw new IllegalArgumentException("unexpected cases final schema");
        }
        List<String> hashes = new ArrayList<>();
        List<OwnedCellEvidence> owned = new ArrayList<>();
        for (String[] line : lines) {
            if (line[0].equals("case_result_hash")) {
                hashes.add(line[1]);
            } else if (line[0].equals("owned")) {
                owned.add(parseOwnedEvidence(line));
            }
        }
        return new FinalPage(
                fields.get("identity_hash"),
                fields.get("planned_hash"),
                fields.get("predecessor_final_hash"),
                parseInt(fields, "page"),
                hashes,
                fields.get("ownership_hash"),
                owned,
                Boolean.parseBoolean(fields.get("infrastructure_complete")));
    }

    private static byte[] serializeRelease(BoardRelease release) {
        StringBuilder text = new StringBuilder();
        text.append("schema\t").append(RELEASE_SCHEMA).append('\n');
        text.append("identity_hash\t").append(release.identityHash()).append('\n');
        text.append("board_final_hash\t").append(release.boardFinalHash()).append('\n');
        text.append("predecessor_release_hash\t")
                .append(release.predecessorReleaseHash()).append('\n');
        text.append("cursor_before\t")
                .append(encode(new String(
                        serializeCursor(release.cursorBefore()), StandardCharsets.UTF_8)))
                .append('\n');
        text.append("cursor_hash_before\t").append(release.cursorHashBefore()).append('\n');
        text.append("active_planned_hash\t").append(release.activePlannedHash()).append('\n');
        text.append("ownership_hash\t").append(release.ownershipHash()).append('\n');
        appendOwnedEvidence(text, release.releasedCells());
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static BoardRelease parseRelease(byte[] bytes) {
        List<String[]> lines = splitLines(bytes);
        Map<String, String> fields = singletonFields(lines);
        if (!RELEASE_SCHEMA.equals(fields.get("schema"))) {
            throw new IllegalArgumentException("unexpected cases release schema");
        }
        List<OwnedCellEvidence> released = new ArrayList<>();
        for (String[] line : lines) {
            if (line[0].equals("owned")) {
                released.add(parseOwnedEvidence(line));
            }
        }
        BoardRelease release = new BoardRelease(
                fields.get("identity_hash"),
                fields.get("board_final_hash"),
                fields.get("predecessor_release_hash"),
                parseCursor(decode(fields.get("cursor_before"))
                        .getBytes(StandardCharsets.UTF_8)),
                fields.get("ownership_hash"),
                released);
        if (!release.cursorHashBefore().equals(fields.get("cursor_hash_before"))
                || !release.activePlannedHash().equals(fields.get("active_planned_hash"))) {
            throw new IllegalArgumentException(
                    "cases release summaries disagree with its exact pre-release cursor");
        }
        return release;
    }

    private static ProgressCursor signCursor(
            long generation,
            String identityHash,
            int nextPage,
            String lastFinalHash,
            String boardFinalHash,
            String lastReleaseHash,
            ActiveAttempt active) {
        String core = cursorCore(
                generation,
                identityHash,
                nextPage,
                lastFinalHash,
                boardFinalHash,
                lastReleaseHash,
                active);
        return new ProgressCursor(
                generation,
                identityHash,
                nextPage,
                lastFinalHash,
                boardFinalHash,
                lastReleaseHash,
                active,
                sha256(core.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] serializeCursor(ProgressCursor cursor) {
        String core = cursorCore(
                cursor.generation(),
                cursor.identityHash(),
                cursor.nextPage(),
                cursor.lastFinalHash(),
                cursor.boardFinalHash(),
                cursor.lastReleaseHash(),
                cursor.active());
        return (core + "cursor_hash\t" + cursor.cursorHash() + '\n')
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String cursorCore(
            long generation,
            String identityHash,
            int nextPage,
            String lastFinalHash,
            String boardFinalHash,
            String lastReleaseHash,
            ActiveAttempt active) {
        StringBuilder text = new StringBuilder();
        text.append("schema\t").append(CURSOR_SCHEMA).append('\n');
        text.append("generation\t").append(generation).append('\n');
        text.append("identity_hash\t").append(identityHash).append('\n');
        text.append("next_page\t").append(nextPage).append('\n');
        text.append("last_final_hash\t").append(lastFinalHash).append('\n');
        text.append("board_final_hash\t").append(boardFinalHash).append('\n');
        text.append("last_release_hash\t").append(lastReleaseHash).append('\n');
        if (active == null) {
            text.append("active_run_id\t").append(NONE).append('\n');
            text.append("active_page\t0\n");
            text.append("active_planned_hash\t").append(NONE).append('\n');
            text.append("active_phase\t").append(NONE).append('\n');
            text.append("active_next_ordinal\t0\n");
            text.append("active_result_hashes\t").append(NONE).append('\n');
            text.append("active_ownership_hash\t").append(EMPTY_OWNERSHIP_HASH).append('\n');
        } else {
            text.append("active_run_id\t").append(active.runId()).append('\n');
            text.append("active_page\t").append(active.page()).append('\n');
            text.append("active_planned_hash\t").append(active.plannedHash()).append('\n');
            text.append("active_phase\t").append(active.phase()).append('\n');
            text.append("active_next_ordinal\t")
                    .append(active.nextCaseOrdinal()).append('\n');
            text.append("active_result_hashes\t")
                    .append(active.completedCaseHashes().isEmpty()
                            ? NONE : String.join(",", active.completedCaseHashes()))
                    .append('\n');
            text.append("active_ownership_hash\t")
                    .append(active.accumulatedOwnershipHash()).append('\n');
        }
        return text.toString();
    }

    private static ProgressCursor parseCursor(byte[] bytes) {
        List<String[]> lines = splitLines(bytes);
        Map<String, String> fields = singletonFields(lines);
        if (!CURSOR_SCHEMA.equals(fields.get("schema"))) {
            throw new IllegalArgumentException("unexpected cases cursor schema");
        }
        long generation = Long.parseLong(fields.get("generation"));
        String identityHash = fields.get("identity_hash");
        int nextPage = parseInt(fields, "next_page");
        String lastFinalHash = fields.get("last_final_hash");
        String boardFinalHash = fields.get("board_final_hash");
        String lastReleaseHash = fields.get("last_release_hash");
        ActiveAttempt active = null;
        if (NONE.equals(fields.get("active_run_id"))) {
            if (parseInt(fields, "active_page") != 0
                    || !NONE.equals(fields.get("active_planned_hash"))
                    || !NONE.equals(fields.get("active_phase"))
                    || parseInt(fields, "active_next_ordinal") != 0
                    || !NONE.equals(fields.get("active_result_hashes"))
                    || !EMPTY_OWNERSHIP_HASH.equals(fields.get("active_ownership_hash"))) {
                throw new IllegalArgumentException(
                        "inactive cases cursor contains active attempt data");
            }
        } else {
            String rawHashes = fields.get("active_result_hashes");
            List<String> hashes = NONE.equals(rawHashes)
                    ? List.of() : List.of(rawHashes.split(",", -1));
            active = new ActiveAttempt(
                    UUID.fromString(fields.get("active_run_id")),
                    parseInt(fields, "active_page"),
                    fields.get("active_planned_hash"),
                    AttemptPhase.valueOf(fields.get("active_phase")),
                    parseInt(fields, "active_next_ordinal"),
                    hashes,
                    fields.get("active_ownership_hash"));
        }
        String expected = sha256(cursorCore(
                generation,
                identityHash,
                nextPage,
                lastFinalHash,
                boardFinalHash,
                lastReleaseHash,
                active)
                .getBytes(StandardCharsets.UTF_8));
        if (!expected.equals(fields.get("cursor_hash"))) {
            throw new IllegalArgumentException("cases cursor hash is corrupt");
        }
        return new ProgressCursor(
                generation,
                identityHash,
                nextPage,
                lastFinalHash,
                boardFinalHash,
                lastReleaseHash,
                active,
                expected);
    }

    private static void appendCellEvidence(
            StringBuilder text,
            String label,
            List<CellEvidence> cells) {
        for (CellEvidence cell : cells) {
            text.append(label).append('\t');
            appendPosInline(text, cell.pos());
            text.append('\t').append(encode(canonicalState(cell.state()))).append('\t')
                    .append(cell.storedDy().present()).append('\t')
                    .append(rawHex(cell.storedDy())).append('\n');
        }
    }

    private static void appendOwnedEvidence(
            StringBuilder text,
            List<OwnedCellEvidence> cells) {
        for (OwnedCellEvidence cell : cells) {
            text.append("owned\t");
            appendPosInline(text, cell.pos());
            text.append('\t').append(encode(canonicalState(cell.expectedState()))).append('\t')
                    .append(cell.expectedStoredDy().present()).append('\t')
                    .append(rawHex(cell.expectedStoredDy())).append('\t')
                    .append(cell.role()).append('\t')
                    .append(encode(cell.caseId())).append('\n');
        }
    }

    private static CellEvidence parseCellEvidence(String[] line) {
        if (line.length != 7) {
            throw new IllegalArgumentException("invalid cell evidence row");
        }
        return new CellEvidence(
                parsePos(line, 1),
                parseState(decode(line[4])),
                parseStoredFact(line[5], line[6]));
    }

    private static OwnedCellEvidence parseOwnedEvidence(String[] line) {
        if (line.length != 9) {
            throw new IllegalArgumentException("invalid owned evidence row");
        }
        return new OwnedCellEvidence(
                parsePos(line, 1),
                parseState(decode(line[4])),
                parseStoredFact(line[5], line[6]),
                RigManifest.CellRole.valueOf(line[7]),
                decode(line[8]));
    }

    private static String ownershipHash(List<OwnedCellEvidence> cells) {
        StringBuilder text = new StringBuilder();
        appendOwnedEvidence(text, cells);
        return sha256(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String canonicalState(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) {
            throw new IllegalArgumentException("unregistered block state in cases evidence");
        }
        List<Property<?>> properties = state.getProperties().stream()
                .sorted(Comparator.comparing(Property::getName))
                .toList();
        if (properties.isEmpty()) {
            return id.toString();
        }
        String values = properties.stream()
                .map(property -> property.getName() + "=" + propertyValue(state, property))
                .collect(java.util.stream.Collectors.joining(","));
        return id + "[" + values + "]";
    }

    private static BlockState parseState(String value) {
        int bracket = value.indexOf('[');
        String idText = bracket < 0 ? value : value.substring(0, bracket);
        ResourceLocation id = ResourceLocation.tryParse(idText);
        if (id == null) {
            throw new IllegalArgumentException("invalid block id in cases evidence: " + idText);
        }
        BlockState state = BuiltInRegistries.BLOCK.getOptional(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown block id in cases evidence: " + id))
                .defaultBlockState();
        if (bracket < 0) {
            return state;
        }
        if (!value.endsWith("]")) {
            throw new IllegalArgumentException("invalid block properties in cases evidence");
        }
        String body = value.substring(bracket + 1, value.length() - 1);
        if (body.isEmpty()) {
            return state;
        }
        for (String assignment : body.split(",", -1)) {
            String[] pair = assignment.split("=", 2);
            if (pair.length != 2) {
                throw new IllegalArgumentException("invalid block property assignment");
            }
            Property<?> property = state.getBlock().getStateDefinition().getProperty(pair[0]);
            if (property == null) {
                throw new IllegalArgumentException(
                        "unknown block property " + pair[0] + " for " + id);
            }
            state = setProperty(state, property, pair[1]);
        }
        return state;
    }

    private static <T extends Comparable<T>> String propertyValue(
            BlockState state,
            Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static <T extends Comparable<T>> BlockState setProperty(
            BlockState state,
            Property<T> property,
            String value) {
        T parsed = property.getValue(value)
                .orElseThrow(() -> new IllegalArgumentException(
                        "invalid value " + value + " for property " + property.getName()));
        return state.setValue(property, parsed);
    }

    private static SlabAnchorAttachment.PlacementDyFact parseStoredFact(
            String presentText,
            String rawText) {
        boolean present = Boolean.parseBoolean(presentText);
        long rawBits = Long.parseUnsignedLong(rawText, 16);
        if (!present) {
            if (rawBits != 0L) {
                throw new IllegalArgumentException("absent stored fact must carry zero raw bits");
            }
            return SlabAnchorAttachment.PlacementDyFact.absent();
        }
        return SlabAnchorAttachment.PlacementDyFact.present(
                Double.longBitsToDouble(rawBits));
    }

    private static SlabAnchorAttachment.PlacementDyFact validateStoredFact(
            SlabAnchorAttachment.PlacementDyFact fact) {
        Objects.requireNonNull(fact, "storedDy");
        if (!fact.present()) {
            if (fact.rawBits() != 0L) {
                throw new IllegalArgumentException(
                        "absent stored fact must carry zero raw bits");
            }
            return fact;
        }
        if (!Double.isFinite(Double.longBitsToDouble(fact.rawBits()))) {
            throw new IllegalArgumentException("stored fact must be finite");
        }
        return fact;
    }

    private static String rawHex(SlabAnchorAttachment.PlacementDyFact fact) {
        return String.format(java.util.Locale.ROOT, "%016x", fact.rawBits());
    }

    private static void appendPositions(
            StringBuilder text,
            String label,
            int ordinal,
            List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            text.append(label).append('\t').append(ordinal).append('\t');
            appendPosInline(text, pos);
            text.append('\n');
        }
    }

    private static void appendPos(
            StringBuilder text,
            String label,
            BlockPos pos) {
        text.append(label).append('\t');
        appendPosInline(text, pos);
        text.append('\n');
    }

    private static void appendPosInline(StringBuilder text, BlockPos pos) {
        text.append(pos.getX()).append('\t')
                .append(pos.getY()).append('\t')
                .append(pos.getZ());
    }

    private static BlockPos parsePos(String[] line, int offset) {
        if (line.length < offset + 3) {
            throw new IllegalArgumentException("position row is truncated");
        }
        return new BlockPos(
                Integer.parseInt(line[offset]),
                Integer.parseInt(line[offset + 1]),
                Integer.parseInt(line[offset + 2]));
    }

    private static List<String[]> splitLines(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.endsWith("\n")) {
            throw new IllegalArgumentException("cases evidence must end with LF");
        }
        List<String[]> lines = new ArrayList<>();
        for (String line : text.substring(0, text.length() - 1).split("\n", -1)) {
            if (line.isEmpty() || line.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("cases evidence contains a malformed line");
            }
            lines.add(line.split("\t", -1));
        }
        return List.copyOf(lines);
    }

    private static Map<String, String> singletonFields(List<String[]> lines) {
        Set<String> repeated = Set.of(
                "case", "fixture", "effect", "mutation", "guard", "reserved",
                "anchor", "before", "after", "owned", "out_of_envelope",
                "case_result_hash");
        Map<String, String> fields = new LinkedHashMap<>();
        for (String[] line : lines) {
            if (!repeated.contains(line[0])) {
                if (line.length != 2 || fields.putIfAbsent(line[0], line[1]) != null) {
                    throw new IllegalArgumentException(
                            "duplicate or malformed singleton evidence field " + line[0]);
                }
            }
        }
        return fields;
    }

    private static int parseInt(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing evidence field " + key);
        }
        return Integer.parseInt(value);
    }

    private static String encode(String value) {
        return BASE64_ENCODER.encodeToString(
                requireText(value, "encoded value").getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        try {
            return new String(BASE64_DECODER.decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid base64 evidence value", failure);
        }
    }

    private static UUID readOrCreateWorldId(Path path) throws IOException {
        if (Files.exists(path)) {
            return readWorldId(path);
        }
        UUID created = UUID.randomUUID();
        try {
            writeAtomicCreate(
                    path,
                    (created + "\n").getBytes(StandardCharsets.UTF_8));
            return created;
        } catch (FileAlreadyExistsException race) {
            return readWorldId(path);
        }
    }

    private static UUID readWorldId(Path path) throws IOException {
        requireRegularFile(path);
        String value = Files.readString(path, StandardCharsets.UTF_8);
        if (!value.endsWith("\n") || value.indexOf('\n') != value.length() - 1) {
            throw new IllegalStateException("world-id is malformed");
        }
        return UUID.fromString(value.substring(0, value.length() - 1));
    }

    private static void createSafeDirectory(Path path) throws IOException {
        Files.createDirectories(path);
        if (!Files.isDirectory(path) || Files.isSymbolicLink(path)) {
            throw new IllegalStateException(
                    "cases evidence directory is not a real directory: " + path);
        }
    }

    private static void requireRegularFile(Path path) {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            throw new IllegalStateException(
                    "cases evidence path is not a regular file: " + path);
        }
    }

    private static void writeAtomicCreate(Path target, byte[] bytes) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            writeForced(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target);
            } catch (FileAlreadyExistsException race) {
                Files.deleteIfExists(temp);
                byte[] existing = Files.readAllBytes(target);
                if (!java.util.Arrays.equals(existing, bytes)) {
                    throw race;
                }
            }
            forceDirectory(target.getParent());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void writeAtomicReplace(Path target, byte[] bytes) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            writeForced(temp, bytes);
            // The cursor is the only mutable publication point. If the filesystem cannot
            // replace it atomically, fail closed rather than exposing a missing/partial cursor.
            Files.move(
                    temp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            forceDirectory(target.getParent());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void writeForced(Path path, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static void requireDistinctEvidence(
            List<CellEvidence> cells,
            String name) {
        Set<BlockPos> positions = new HashSet<>();
        if (cells.stream().map(CellEvidence::pos).anyMatch(pos -> !positions.add(pos))) {
            throw new IllegalArgumentException(name + " contains duplicate positions");
        }
    }

    private static void requireDistinctOwned(
            List<OwnedCellEvidence> cells,
            String name) {
        Set<BlockPos> positions = new HashSet<>();
        if (cells.stream().map(OwnedCellEvidence::pos).anyMatch(pos -> !positions.add(pos))) {
            throw new IllegalArgumentException(name + " contains duplicate positions");
        }
    }

    private static List<BlockPos> immutableDistinctPositions(
            List<BlockPos> positions,
            String name) {
        Objects.requireNonNull(positions, name);
        LinkedHashSet<BlockPos> distinct = new LinkedHashSet<>();
        for (BlockPos pos : positions) {
            if (!distinct.add(Objects.requireNonNull(pos, name + " position").immutable())) {
                throw new IllegalArgumentException(name + " contains duplicate positions");
            }
        }
        return List.copyOf(distinct);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String requireHash(String value, String name) {
        if (!isHash(value)) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
        return value;
    }

    private static boolean isHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM lacks SHA-256", impossible);
        }
    }

    private static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(sha256Digest().digest(bytes));
    }

    private static String failureDetail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getClass().getSimpleName() + ": " + message;
    }

    private static final class IdentityMismatch extends IllegalStateException {
        private IdentityMismatch(String message) {
            super(message);
        }
    }
}
