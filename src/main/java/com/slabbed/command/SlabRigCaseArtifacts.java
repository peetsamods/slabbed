package com.slabbed.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slabbed.util.BuildStamp;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Content-addressed RIG-2 evidence plus a small atomic, hash-bound durable resume cursor. */
public final class SlabRigCaseArtifacts {

    public static final String PROGRESS_SCHEMA = "slabbed-rig-case-progress-v3";
    public static final String WORLD_ID_SCHEMA = "slabbed-rig-world-id-v1";

    private SlabRigCaseArtifacts() {
    }

    public record Progress(String worldKey, String buildGitSha, String runtimeContentSha256,
                           boolean frozenDyEnabled,
                           String executionContract,
                           String catalogHash, int nextPage, int pageCount,
                           int lastCompletedPage, String lastManifestHash) {
    }

    /** Content identity: catalog hash or self-excluding page-manifest id, depending on artifact kind. */
    public record WrittenArtifact(Path path, String contentId) {
    }

    public static Path defaultRoot() {
        return FabricLoader.getInstance().getGameDir().resolve("slabbed-rig");
    }

    /** Tool-owned persistent save identity; a deleted/recreated same-folder world gets a new UUID. */
    public static String loadOrCreateWorldKey(Path worldRoot) throws IOException {
        Path normalizedRoot = worldRoot.toAbsolutePath().normalize();
        Path identityPath = worldIdentityPath(normalizedRoot);
        Files.createDirectories(identityPath.getParent());
        if (!Files.exists(identityPath)) {
            String uuid = UUID.randomUUID().toString();
            String text = "schema\t" + WORLD_ID_SCHEMA + "\n"
                    + "uuid\t" + uuid + "\n";
            try {
                Files.writeString(identityPath, text, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (java.nio.file.FileAlreadyExistsException raced) {
                // Another command/server thread won creation; validate the one durable winner below.
            }
        }
        List<String> lines = Files.readAllLines(identityPath, StandardCharsets.UTF_8);
        if (lines.size() != 2 || !lines.get(0).equals("schema\t" + WORLD_ID_SCHEMA)) {
            throw new IOException("malformed RIG-2 world identity; refusing stale evidence: " + identityPath);
        }
        String uuid = value(lines.get(1), "uuid");
        try {
            if (!UUID.fromString(uuid).toString().equals(uuid)) {
                throw new IllegalArgumentException("non-canonical UUID");
            }
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid RIG-2 world UUID; refusing stale evidence: " + identityPath,
                    invalid);
        }
        return worldKey(normalizedRoot, uuid);
    }

    /** Pure seam used by the same-path/different-world-UUID proof. */
    public static String worldKey(Path worldRoot, String worldUuid) {
        UUID canonical = UUID.fromString(worldUuid);
        if (!canonical.toString().equals(worldUuid)) {
            throw new IllegalArgumentException("world UUID must be canonical lower-case text");
        }
        return identityHash(WORLD_ID_SCHEMA + "\0"
                + worldRoot.toAbsolutePath().normalize() + "\0" + worldUuid);
    }

    public static Path worldIdentityPath(Path worldRoot) {
        return worldRoot.toAbsolutePath().normalize().resolve("data")
                .resolve("slabbed-rig-world-id.tsv");
    }

    public static WrittenArtifact writeCatalog(Path root, SlabRigCaseCatalog.Snapshot snapshot) throws IOException {
        byte[] bytes = SlabRigCaseCatalog.catalogTsv(snapshot).getBytes(StandardCharsets.UTF_8);
        Path path = root.resolve("catalogs").resolve("catalog-" + snapshot.catalogHash() + ".tsv");
        writeNewOrVerify(path, bytes);
        return new WrittenArtifact(path, snapshot.catalogHash());
    }

    public static WrittenArtifact writeManifest(Path root,
                                                SlabRigCasePageManifest.PageManifest manifest) throws IOException {
        if (!BuildStamp.hasExactRuntimeContent()) {
            throw new IOException("exact runtime content digest unavailable; refusing RIG-2 page evidence");
        }
        byte[] bytes = SlabRigCasePageManifest.canonicalJson(manifest).getBytes(StandardCharsets.UTF_8);
        String hash = SlabRigCasePageManifest.manifestHash(manifest);
        Path path = root.resolve("case-pages").resolve("case-page-" + hash + ".json");
        writeNewOrVerify(path, bytes);
        return new WrittenArtifact(path, hash);
    }

    public static Path progressPath(Path root, String player, String dimension, String worldKey) {
        String identity = identityHash(PROGRESS_SCHEMA + "\0" + worldKey + "\0" + player + "\0" + dimension);
        return root.resolve("progress").resolve("cursor-" + identity + ".tsv");
    }

    public static void writeProgress(Path path, Progress progress) throws IOException {
        validateProgressValues(progress);
        String text = "schema\t" + PROGRESS_SCHEMA + "\n"
                + "world_key\t" + progress.worldKey() + "\n"
                + "build_git_sha\t" + progress.buildGitSha() + "\n"
                + "runtime_content_sha256\t" + progress.runtimeContentSha256() + "\n"
                + "frozen_dy_enabled\t" + progress.frozenDyEnabled() + "\n"
                + "execution_contract\t" + progress.executionContract() + "\n"
                + "catalog_hash\t" + progress.catalogHash() + "\n"
                + "next_page\t" + progress.nextPage() + "\n"
                + "page_count\t" + progress.pageCount() + "\n"
                + "last_completed_page\t" + progress.lastCompletedPage() + "\n"
                + "last_manifest_hash\t" + progress.lastManifestHash() + "\n";
        atomicReplace(path, text.getBytes(StandardCharsets.UTF_8));
    }

    public static Progress readProgress(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.size() != 11 || !lines.get(0).equals("schema\t" + PROGRESS_SCHEMA)) {
            throw new IOException("unsupported or malformed RIG-2 progress file " + path);
        }
        Progress progress = new Progress(
                value(lines.get(1), "world_key"),
                value(lines.get(2), "build_git_sha"),
                value(lines.get(3), "runtime_content_sha256"),
                parseBoolean(value(lines.get(4), "frozen_dy_enabled"), "frozen_dy_enabled"),
                value(lines.get(5), "execution_contract"),
                value(lines.get(6), "catalog_hash"),
                parseInt(value(lines.get(7), "next_page"), "next_page"),
                parseInt(value(lines.get(8), "page_count"), "page_count"),
                parseInt(value(lines.get(9), "last_completed_page"), "last_completed_page"),
                value(lines.get(10), "last_manifest_hash"));
        try {
            validateProgressValues(progress);
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid RIG-2 progress values in " + path + ": " + e.getMessage(), e);
        }
        return progress;
    }

    /**
     * Runtime resume validates the latest page and its immediate predecessor as an inductive link.
     * It deliberately does not claim full-archive closure; {@link #validateFullArchiveEvidence} is
     * the required O(N) terminal proof before a cursor may become exhausted.
     */
    public static void validateResumeEvidence(Path root, Progress progress,
                                              SlabRigCaseCatalog.Snapshot snapshot,
                                              String expectedWorldKey, String expectedBuildGitSha,
                                              String expectedRuntimeContentSha256,
                                              boolean expectedFrozenDyEnabled,
                                              String expectedPlayer, String expectedDimension)
            throws IOException {
        validateProgressIdentity(progress, snapshot, expectedWorldKey, expectedBuildGitSha,
                expectedRuntimeContentSha256, expectedFrozenDyEnabled);
        Path catalogPath = validateCatalogArtifact(root, snapshot);
        if (progress.lastCompletedPage() == 0) {
            return;
        }
        JsonObject latest = readAndValidateFinalPage(root, progress.lastManifestHash(),
                progress.lastCompletedPage(), snapshot, catalogPath, expectedWorldKey,
                expectedBuildGitSha, expectedRuntimeContentSha256, expectedFrozenDyEnabled,
                expectedPlayer, expectedDimension);
        String previousHash = stringField(latest, "previousContiguousManifestHash");
        if (progress.lastCompletedPage() == 1) {
            if (!"none".equals(previousHash)) {
                throw new IOException("page 1 manifest must begin the contiguous evidence chain");
            }
            return;
        }
        if (!previousHash.matches("[0-9a-f]{64}")) {
            throw new IOException("prior page manifest lacks an immediate predecessor hash");
        }
        readAndValidateFinalPage(root, previousHash, progress.lastCompletedPage() - 1,
                snapshot, catalogPath, expectedWorldKey, expectedBuildGitSha,
                expectedRuntimeContentSha256, expectedFrozenDyEnabled,
                expectedPlayer, expectedDimension);
    }

    /** Walk every immutable page and plan link; required before catalog enumeration can close. */
    public static void validateFullArchiveEvidence(Path root, Progress progress,
                                                   SlabRigCaseCatalog.Snapshot snapshot,
                                                   String expectedWorldKey, String expectedBuildGitSha,
                                                   String expectedRuntimeContentSha256,
                                                   boolean expectedFrozenDyEnabled,
                                                   String expectedPlayer, String expectedDimension)
            throws IOException {
        validateProgressIdentity(progress, snapshot, expectedWorldKey, expectedBuildGitSha,
                expectedRuntimeContentSha256, expectedFrozenDyEnabled);
        if (progress.nextPage() != 0 || progress.lastCompletedPage() != snapshot.pageCount()) {
            throw new IOException("full archive audit requires an exhausted contiguous cursor");
        }
        Path catalogPath = validateCatalogArtifact(root, snapshot);
        String hash = progress.lastManifestHash();
        Set<String> seen = new HashSet<>();
        for (int page = snapshot.pageCount(); page >= 1; page--) {
            if (!seen.add(hash)) {
                throw new IOException("full archive audit found a manifest cycle at page " + page);
            }
            JsonObject manifest = readAndValidateFinalPage(root, hash, page, snapshot, catalogPath,
                    expectedWorldKey, expectedBuildGitSha, expectedRuntimeContentSha256,
                    expectedFrozenDyEnabled, expectedPlayer, expectedDimension);
            String previous = stringField(manifest, "previousContiguousManifestHash");
            if (page == 1) {
                if (!"none".equals(previous)) {
                    throw new IOException("full archive audit: page 1 predecessor must be none");
                }
            } else if (!previous.matches("[0-9a-f]{64}")) {
                throw new IOException("full archive audit: page " + page + " predecessor is invalid");
            }
            hash = previous;
        }
        if (!"none".equals(hash) || seen.size() != snapshot.pageCount()) {
            throw new IOException("full archive audit did not terminate at the unique page 1 root");
        }
    }

    private static void validateProgressIdentity(Progress progress,
                                                 SlabRigCaseCatalog.Snapshot snapshot,
                                                 String expectedWorldKey,
                                                 String expectedBuildGitSha,
                                                 String expectedRuntimeContentSha256,
                                                 boolean expectedFrozenDyEnabled) throws IOException {
        if (!progress.worldKey().equals(expectedWorldKey)
                || !progress.buildGitSha().equals(expectedBuildGitSha)
                || !progress.runtimeContentSha256().equals(expectedRuntimeContentSha256)
                || progress.frozenDyEnabled() != expectedFrozenDyEnabled
                || !progress.executionContract().equals(SlabRigCaseCatalog.EXECUTION_CONTRACT)
                || !progress.catalogHash().equals(snapshot.catalogHash())
                || progress.pageCount() != snapshot.pageCount()) {
            throw new IOException(
                    "resume cursor world/build/runtime/frozen-mode/execution/catalog identity mismatch");
        }
    }

    private static Path validateCatalogArtifact(Path root,
                                                SlabRigCaseCatalog.Snapshot snapshot) throws IOException {
        Path catalogPath = root.resolve("catalogs").resolve("catalog-" + snapshot.catalogHash() + ".tsv");
        byte[] expectedCatalog = SlabRigCaseCatalog.catalogTsv(snapshot).getBytes(StandardCharsets.UTF_8);
        if (!Files.isRegularFile(catalogPath)
                || !MessageDigest.isEqual(Files.readAllBytes(catalogPath), expectedCatalog)) {
            throw new IOException("exact catalog artifact missing or changed: " + catalogPath);
        }
        return catalogPath;
    }

    private static JsonObject readAndValidateFinalPage(Path root, String manifestHash, int expectedPage,
                                                       SlabRigCaseCatalog.Snapshot snapshot,
                                                       Path catalogPath, String expectedWorldKey,
                                                       String expectedBuildGitSha,
                                                       String expectedRuntimeContentSha256,
                                                       boolean expectedFrozenDyEnabled,
                                                       String expectedPlayer,
                                                       String expectedDimension) throws IOException {
        Path manifestPath = root.resolve("case-pages").resolve("case-page-" + manifestHash + ".json");
        if (!Files.isRegularFile(manifestPath)) {
            throw new IOException("contiguous page manifest missing: " + manifestPath);
        }
        String json = Files.readString(manifestPath, StandardCharsets.UTF_8);
        if (!manifestSelfHash(json).equals(manifestHash)) {
            throw new IOException("page manifest self-hash mismatch: " + manifestPath);
        }
        JsonObject rootJson = parseObject(json, "page manifest " + manifestPath);
        String status = stringField(rootJson, "status");
        String expectedId = "sha256:" + manifestHash;
        if (!SlabRigCasePageManifest.SCHEMA.equals(stringField(rootJson, "schema"))
                || !expectedId.equals(stringField(rootJson, "manifestId"))
                || !("FINALIZED".equals(status) || "FINALIZED_WITH_REDS".equals(status))
                || !snapshot.catalogHash().equals(stringField(rootJson, "catalogHash"))
                || !SlabRigCaseCatalog.EXECUTION_CONTRACT.equals(stringField(rootJson, "executionContract"))
                || !SlabRigCasePageManifest.RESUME_CONTRACT.equals(stringField(rootJson, "resumeContract"))
                || !expectedBuildGitSha.equals(stringField(rootJson, "buildGitSha"))
                || !expectedRuntimeContentSha256.equals(stringField(rootJson, "runtimeContentSha256"))
                || booleanField(rootJson, "frozenDyEnabled") != expectedFrozenDyEnabled
                || !expectedWorldKey.equals(stringField(rootJson, "worldKey"))
                || !expectedPlayer.equals(stringField(rootJson, "player"))
                || !expectedDimension.equals(stringField(rootJson, "dimension"))
                || intField(rootJson, "page") != expectedPage
                || rootJson.get("pageCount").getAsInt() != snapshot.pageCount()
                || !catalogPath.toString().equals(stringField(rootJson, "catalogArtifact"))) {
            throw new IOException("page manifest identity does not match resume cursor/page " + expectedPage);
        }
        String previousHash = stringField(rootJson, "previousContiguousManifestHash");
        boolean linkShape = expectedPage == 1
                ? "none".equals(previousHash) : previousHash.matches("[0-9a-f]{64}");
        if (!linkShape) {
            throw new IOException("page manifest predecessor link shape is invalid for page " + expectedPage);
        }
        validateFinalCases(rootJson, status, snapshot, expectedPage);
        validatePlannedLink(root, rootJson, snapshot, expectedPage, catalogPath,
                expectedWorldKey, expectedBuildGitSha, expectedRuntimeContentSha256,
                expectedFrozenDyEnabled, expectedPlayer, expectedDimension);
        return rootJson;
    }

    private static void validateFinalCases(JsonObject manifest, String status,
                                           SlabRigCaseCatalog.Snapshot snapshot,
                                           int expectedPage) throws IOException {
        SlabRigCaseCatalog.CasePage page = SlabRigCaseCatalog.page(snapshot, expectedPage);
        JsonArray rows = arrayField(manifest, "cases");
        if (rows.size() != page.cases().size()) {
            throw new IOException("finalized page has wrong case-row cardinality at page " + expectedPage);
        }
        int executed = 0;
        int deferred = 0;
        int refused = 0;
        int changed = 0;
        int topologyLawReds = 0;
        int placedThenVanished = 0;
        int externalGuardCells = 0;
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < rows.size(); index++) {
            JsonObject row = objectElement(rows.get(index), "cases[" + index + "]");
            SlabRigCaseCatalog.CaseDefinition expected = page.cases().get(index);
            String caseId = stringField(row, "caseId");
            if (!ids.add(caseId)
                    || !expected.id().equals(caseId)
                    || intField(row, "globalCaseIndex") != expected.index()
                    || !expected.item().id().equals(stringField(row, "itemId"))
                    || !expected.topology().id().equals(stringField(row, "topologyId"))
                    || !expected.placementMode().equals(stringField(row, "placementMode"))) {
                throw new IOException("finalized page case identity/order mismatch at row " + index);
            }
            String attemptStatus = stringField(row, "attemptStatus");
            String structureStatus = stringField(row, "structureStatus");
            String actionOrigin = stringField(row, "actionOrigin");
            String outcome = stringField(row, "outcome");
            String interactionResult = stringField(row, "interactionResult");
            boolean interactionConsumesAction = booleanField(row, "interactionConsumesAction");
            String stackItemBefore = stringField(row, "stackItemBefore");
            int stackBefore = intField(row, "stackBefore");
            String stackItemAfter = stringField(row, "stackItemAfter");
            int stackAfter = intField(row, "stackAfter");
            boolean persistentSubjectPresent = booleanField(row, "persistentSubjectPresent");
            JsonArray actualChanges = arrayField(row, "actualChanges");
            JsonArray plannedStructure = arrayField(row, "plannedStructureCells");
            JsonArray actualStructure = arrayField(row, "actualStructureCells");
            JsonArray postActionStructure = arrayField(row, "postActionStructureCells");
            String postActionStructureStatus = stringField(row, "postActionStructureStatus");
            stringField(row, "postActionStructureDetail");
            if ("EXECUTED".equals(attemptStatus)) {
                executed++;
                if (!"AUTO_USEON_PROXY".equals(actionOrigin)
                        || outcome.startsWith("PLANNED") || outcome.startsWith("ERROR")
                        || outcome.startsWith("INTERRUPTED")) {
                    throw new IOException("executed case has false provenance/outcome at " + caseId);
                }
                if (!("VERIFIED".equals(structureStatus) || "LAW_RED".equals(structureStatus))) {
                    throw new IOException("executed case lacks verified/law-red structure at " + caseId);
                }
                if ("NOT_RUN".equals(interactionResult)
                        || !expected.item().id().equals(stackItemBefore)
                        || stackBefore != 1 || stackAfter < 0
                        || "NOT_RUN".equals(stackItemAfter)) {
                    throw new IOException("executed case lacks exact action/stack identity at " + caseId);
                }
                String absentClassification = SlabRigCommand.classifyAbsentSubject(
                        interactionConsumesAction, stackItemBefore, stackBefore,
                        stackItemAfter, stackAfter, !actualChanges.isEmpty());
                if ("PLACED_THEN_VANISHED".equals(outcome)) {
                    if (persistentSubjectPresent
                            || !"PLACED_THEN_VANISHED".equals(absentClassification)) {
                        throw new IOException("vanished case contradicts its action/subject evidence at " + caseId);
                    }
                } else if ("REFUSED_NO_CHANGE".equals(outcome)) {
                    if (persistentSubjectPresent || !actualChanges.isEmpty()
                            || !"REFUSED_NO_CHANGE".equals(absentClassification)) {
                        throw new IOException("refused case hides consumed/transformed/change evidence at " + caseId);
                    }
                } else if (outcome.startsWith("PLACED_")) {
                    if (!persistentSubjectPresent || actualChanges.isEmpty()) {
                        throw new IOException("placed case lacks a persistent subject/change at " + caseId);
                    }
                } else {
                    throw new IOException("executed case has unsupported outcome " + outcome + " at " + caseId);
                }
                validateCompleteStructureObservation(plannedStructure, actualStructure,
                        caseId + " pre-action");
                validateCompleteStructureObservation(plannedStructure, postActionStructure,
                        caseId + " post-action");
                boolean topologyChanged = !actualStructure.equals(postActionStructure);
                if (topologyChanged) {
                    if (!"LAW_RED".equals(postActionStructureStatus)
                            || !"LAW_RED".equals(structureStatus)) {
                        throw new IOException(
                                "post-action topology change is not an explicit law red at " + caseId);
                    }
                } else if (!"STABLE".equals(postActionStructureStatus)) {
                    throw new IOException(
                            "stable post-action topology has false status at " + caseId);
                }
            } else if ("DEFERRED".equals(attemptStatus)) {
                deferred++;
                if (!"NOT_RUN".equals(actionOrigin) || !outcome.startsWith("DEFERRED")) {
                    throw new IOException("deferred case has false provenance/outcome at " + caseId);
                }
                if (!("VERIFIED".equals(structureStatus) || "LAW_RED".equals(structureStatus)
                        || "DEFERRED_EXTERNAL_GUARD_CONTEXT".equals(structureStatus))) {
                    throw new IOException("deferred case has invalid structure status at " + caseId);
                }
                if (!"NOT_RUN".equals(interactionResult) || interactionConsumesAction
                        || !"NOT_RUN".equals(stackItemBefore) || stackBefore != 0
                        || !"NOT_RUN".equals(stackItemAfter) || stackAfter != 0
                        || persistentSubjectPresent || !actualChanges.isEmpty()) {
                    throw new IOException("deferred case contains false post-action evidence at " + caseId);
                }
                if (!"NOT_RUN".equals(postActionStructureStatus)
                        || !postActionStructure.isEmpty()) {
                    throw new IOException("deferred case contains false topology-after-action evidence at "
                            + caseId);
                }
                if (!actualStructure.isEmpty()) {
                    validateCompleteStructureObservation(plannedStructure, actualStructure,
                            caseId + " deferred pre-action");
                }
            } else {
                throw new IOException("progress-ineligible attempt status " + attemptStatus + " at " + caseId);
            }
            if ("LAW_RED".equals(structureStatus)) {
                topologyLawReds++;
            }
            if (outcome.startsWith("REFUSED")) {
                refused++;
            }
            if ("PLACED_THEN_VANISHED".equals(outcome)) {
                placedThenVanished++;
            }
            if (!actualChanges.isEmpty()) {
                changed++;
            }
            externalGuardCells += arrayField(row, "externalGuardContext").size();
        }
        int lawReds = topologyLawReds + placedThenVanished;
        if (("FINALIZED".equals(status) && lawReds != 0)
                || ("FINALIZED_WITH_REDS".equals(status) && lawReds == 0)) {
            throw new IOException("finalized status disagrees with topology/vanished law-red count");
        }
        JsonObject coverage = objectField(manifest, "coverage");
        if (!status.equals(stringField(coverage, "pageStatus"))
                || intField(coverage, "casesMaterialized") != rows.size()
                || intField(coverage, "planned") != 0
                || intField(coverage, "proxyExecuted") != executed
                || intField(coverage, "deferred") != deferred
                || intField(coverage, "changed") != changed
                || intField(coverage, "refused") != refused
                || intField(coverage, "errors") != 0
                || intField(coverage, "interrupted") != 0
                || intField(coverage, "topologyLawReds") != topologyLawReds
                || intField(coverage, "placedThenVanished") != placedThenVanished
                || intField(coverage, "externalGuardCells") != externalGuardCells
                || intField(coverage, "playerAuthoredPaired") != 0
                || executed + deferred != rows.size()) {
            throw new IOException("finalized page coverage counters/eligibility are inconsistent");
        }
    }

    private static void validatePlannedLink(Path root, JsonObject finalized,
                                            SlabRigCaseCatalog.Snapshot snapshot,
                                            int expectedPage, Path catalogPath,
                                            String expectedWorldKey, String expectedBuildGitSha,
                                            String expectedRuntimeContentSha256,
                                            boolean expectedFrozenDyEnabled,
                                            String expectedPlayer, String expectedDimension)
            throws IOException {
        Path artifactRoot = root.toAbsolutePath().normalize().resolve("case-pages");
        Path plannedPath;
        try {
            plannedPath = Path.of(stringField(finalized, "plannedArtifact")).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            throw new IOException("finalized page plannedArtifact is not a valid path", e);
        }
        if (!plannedPath.startsWith(artifactRoot) || !artifactRoot.equals(plannedPath.getParent())
                || !Files.isRegularFile(plannedPath)) {
            throw new IOException("planned artifact is missing or outside the exact artifact root: " + plannedPath);
        }
        String name = plannedPath.getFileName().toString();
        if (!name.matches("case-page-[0-9a-f]{64}\\.json")) {
            throw new IOException("planned artifact filename is not content-addressed: " + plannedPath);
        }
        String expectedHash = name.substring("case-page-".length(), name.length() - ".json".length());
        String json = Files.readString(plannedPath, StandardCharsets.UTF_8);
        if (!expectedHash.equals(manifestSelfHash(json))) {
            throw new IOException("planned artifact self-hash mismatch: " + plannedPath);
        }
        JsonObject planned = parseObject(json, "planned page manifest " + plannedPath);
        if (!SlabRigCasePageManifest.SCHEMA.equals(stringField(planned, "schema"))
                || !("sha256:" + expectedHash).equals(stringField(planned, "manifestId"))
                || !"PLANNED".equals(stringField(planned, "status"))
                || !"self:content-addressed-after-serialization".equals(
                stringField(planned, "plannedArtifact"))
                || !snapshot.catalogHash().equals(stringField(planned, "catalogHash"))
                || !SlabRigCaseCatalog.EXECUTION_CONTRACT.equals(stringField(planned, "executionContract"))
                || !SlabRigCasePageManifest.RESUME_CONTRACT.equals(stringField(planned, "resumeContract"))
                || !expectedBuildGitSha.equals(stringField(planned, "buildGitSha"))
                || !expectedRuntimeContentSha256.equals(stringField(planned, "runtimeContentSha256"))
                || booleanField(planned, "frozenDyEnabled") != expectedFrozenDyEnabled
                || !expectedWorldKey.equals(stringField(planned, "worldKey"))
                || !expectedPlayer.equals(stringField(planned, "player"))
                || !expectedDimension.equals(stringField(planned, "dimension"))
                || intField(planned, "page") != expectedPage
                || intField(planned, "pageCount") != snapshot.pageCount()
                || !catalogPath.toString().equals(stringField(planned, "catalogArtifact"))) {
            throw new IOException("planned artifact identity/status does not match finalized page");
        }
        for (String key : List.of("schema", "buildGitSha", "runtimeContentSha256", "frozenDyEnabled",
                "jarFile", "minecraftVersion",
                "catalogSchema", "catalogHash", "executionContract", "runtimeBlockItems",
                "explicitNonBlockItems", "topologyCount", "totalCases", "page", "pageCount",
                "itemGroup", "topologyGroup", "pageGeometry", "worldKey", "dimension", "player",
                "base", "facing", "catalogArtifact", "previousContiguousManifestHash",
                "placementMode", "effectObservationPolicy", "resumeContract", "hangingCoverage",
                "playerProof")) {
            requireSameField(planned, finalized, key, "planned/final identity");
        }
        JsonArray plannedRows = arrayField(planned, "cases");
        JsonArray finalRows = arrayField(finalized, "cases");
        if (plannedRows.size() != finalRows.size()) {
            throw new IOException("planned/final case-row count mismatch");
        }
        List<String> planningFields = List.of("globalCaseIndex", "caseId", "itemIndex", "itemId",
                "categories", "disposition", "effectPolicy", "topologyIndex", "topologyId",
                "topologyRecipe", "placementMode", "tileBase", "plannedStructureCells",
                "reservedCells", "effectCells", "clicked", "face", "hitVector", "plannedTarget");
        for (int index = 0; index < plannedRows.size(); index++) {
            JsonObject plannedRow = objectElement(plannedRows.get(index), "planned cases[" + index + "]");
            JsonObject finalRow = objectElement(finalRows.get(index), "final cases[" + index + "]");
            for (String key : planningFields) {
                requireSameField(plannedRow, finalRow, key, "planned/final case " + index);
            }
            if (!"PLANNED".equals(stringField(plannedRow, "structureStatus"))
                    || !"PLANNED".equals(stringField(plannedRow, "attemptStatus"))
                    || !"NOT_RUN".equals(stringField(plannedRow, "actionOrigin"))
                    || !"PLANNED".equals(stringField(plannedRow, "outcome"))
                    || !"NOT_RUN".equals(stringField(plannedRow, "interactionResult"))
                    || booleanField(plannedRow, "interactionConsumesAction")
                    || !"NOT_RUN".equals(stringField(plannedRow, "stackItemBefore"))
                    || intField(plannedRow, "stackBefore") != 0
                    || !"NOT_RUN".equals(stringField(plannedRow, "stackItemAfter"))
                    || intField(plannedRow, "stackAfter") != 0
                    || booleanField(plannedRow, "persistentSubjectPresent")
                    || !"NOT_RUN".equals(stringField(plannedRow, "postActionStructureStatus"))
                    || !arrayField(plannedRow, "actualStructureCells").isEmpty()
                    || !arrayField(plannedRow, "postActionStructureCells").isEmpty()
                    || !arrayField(plannedRow, "externalGuardContext").isEmpty()
                    || !arrayField(plannedRow, "actualChanges").isEmpty()) {
                throw new IOException("planned artifact contains post-mutation evidence at row " + index);
            }
        }
        JsonObject coverage = objectField(planned, "coverage");
        if (!"PLANNED".equals(stringField(coverage, "pageStatus"))
                || intField(coverage, "casesMaterialized") != plannedRows.size()
                || intField(coverage, "planned") != plannedRows.size()
                || intField(coverage, "proxyExecuted") != 0
                || intField(coverage, "deferred") != 0
                || intField(coverage, "changed") != 0
                || intField(coverage, "errors") != 0
                || intField(coverage, "interrupted") != 0
                || intField(coverage, "topologyLawReds") != 0
                || intField(coverage, "placedThenVanished") != 0
                || intField(coverage, "externalGuardCells") != 0
                || intField(coverage, "playerAuthoredPaired") != 0) {
            throw new IOException("planned artifact coverage is not a pure pre-mutation plan");
        }
    }

    public static String identityHash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void validateCompleteStructureObservation(JsonArray planned, JsonArray observed,
                                                             String label) throws IOException {
        if (observed.size() != planned.size() || observed.isEmpty()) {
            throw new IOException(label + " structure cardinality is incomplete");
        }
        Set<String> positions = new HashSet<>();
        for (int index = 0; index < planned.size(); index++) {
            if (!planned.get(index).isJsonPrimitive()) {
                throw new IOException(label + " planned position is malformed at " + index);
            }
            String plannedPos = planned.get(index).getAsString();
            JsonObject cell = objectElement(observed.get(index), label + "[" + index + "]");
            String observedPos = stringField(cell, "pos");
            if (!plannedPos.equals(observedPos) || !positions.add(observedPos)
                    || stringField(cell, "state").isBlank()
                    || stringField(cell, "markers").isBlank()) {
                throw new IOException(label + " structure identity/order mismatch at " + index);
            }
            validateDyEvidence(stringField(cell, "liveDy"), false,
                    label + " liveDy at " + observedPos);
            validateDyEvidence(stringField(cell, "storedDy"), true,
                    label + " storedDy at " + observedPos);
        }
    }

    private static void validateDyEvidence(String text, boolean allowNaN, String label)
            throws IOException {
        if ("NaN".equals(text)) {
            if (allowNaN) {
                return;
            }
            throw new IOException(label + " cannot be NaN");
        }
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value) || !Double.toString(value).equals(text)) {
                throw new IOException(label + " is not canonical finite double evidence: " + text);
            }
        } catch (NumberFormatException e) {
            throw new IOException(label + " is not double evidence: " + text, e);
        }
    }

    private static void validateProgressValues(Progress progress) {
        if (!progress.worldKey().matches("[0-9a-f]{64}")
                || !progress.catalogHash().matches("[0-9a-f]{64}")
                || !progress.runtimeContentSha256().matches("[0-9a-f]{64}")
                || progress.buildGitSha().isBlank() || containsSeparator(progress.buildGitSha())
                || progress.executionContract().isBlank() || containsSeparator(progress.executionContract())
                || progress.pageCount() < 1
                || progress.nextPage() < 0 || progress.nextPage() > progress.pageCount()
                || progress.lastCompletedPage() < 0
                || progress.lastCompletedPage() > progress.pageCount()) {
            throw new IllegalArgumentException("invalid identity or page bounds");
        }
        if (progress.lastCompletedPage() == 0) {
            if (progress.nextPage() != 1 || !"none".equals(progress.lastManifestHash())) {
                throw new IllegalArgumentException("empty prefix requires next=1 and manifest=none");
            }
        } else {
            if (!progress.lastManifestHash().matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("completed prefix needs a manifest hash");
            }
            int expectedNext = progress.lastCompletedPage() == progress.pageCount()
                    ? 0 : progress.lastCompletedPage() + 1;
            if (progress.nextPage() != expectedNext) {
                throw new IllegalArgumentException("next page is not contiguous with completed prefix");
            }
        }
    }

    private static boolean containsSeparator(String value) {
        return value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }

    private static String manifestSelfHash(String json) throws IOException {
        int firstNewline = json.indexOf('\n');
        int secondNewline = firstNewline < 0 ? -1 : json.indexOf('\n', firstNewline + 1);
        if (firstNewline < 0 || secondNewline < 0
                || !json.substring(firstNewline + 1, secondNewline).contains("\"manifestId\"")) {
            throw new IOException("manifestId line missing from canonical page JSON");
        }
        String body = json.substring(0, firstNewline + 1) + json.substring(secondNewline + 1);
        return identityHash(body);
    }

    private static JsonObject parseObject(String json, String label) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IOException(label + " is not a JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException(label + " is invalid JSON", e);
        }
    }

    private static JsonObject objectElement(JsonElement element, String label) throws IOException {
        if (element == null || !element.isJsonObject()) {
            throw new IOException(label + " is not an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonObject objectField(JsonObject object, String key) throws IOException {
        if (!object.has(key) || !object.get(key).isJsonObject()) {
            throw new IOException("page manifest missing object field " + key);
        }
        return object.getAsJsonObject(key);
    }

    private static JsonArray arrayField(JsonObject object, String key) throws IOException {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            throw new IOException("page manifest missing array field " + key);
        }
        return object.getAsJsonArray(key);
    }

    private static int intField(JsonObject object, String key) throws IOException {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.get(key).getAsJsonPrimitive().isNumber()) {
            throw new IOException("page manifest missing numeric field " + key);
        }
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException e) {
            throw new IOException("page manifest field " + key + " is not an exact int", e);
        }
    }

    private static boolean booleanField(JsonObject object, String key) throws IOException {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.get(key).getAsJsonPrimitive().isBoolean()) {
            throw new IOException("page manifest missing boolean field " + key);
        }
        return object.get(key).getAsBoolean();
    }

    private static void requireSameField(JsonObject first, JsonObject second,
                                         String key, String label) throws IOException {
        if (!first.has(key) || !second.has(key) || !first.get(key).equals(second.get(key))) {
            throw new IOException(label + " mismatch at field " + key);
        }
    }

    private static String stringField(JsonObject object, String key) throws IOException {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new IOException("page manifest missing field " + key);
        }
        return object.get(key).getAsString();
    }

    private static String value(String line, String key) throws IOException {
        String prefix = key + "\t";
        if (!line.startsWith(prefix) || line.length() == prefix.length()) {
            throw new IOException("missing " + key + " in RIG-2 progress");
        }
        return line.substring(prefix.length());
    }

    private static int parseInt(String value, String field) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IOException("invalid " + field + " in RIG-2 progress", e);
        }
    }

    private static boolean parseBoolean(String value, String field) throws IOException {
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IOException("invalid " + field + " in RIG-2 progress");
    }

    private static void writeNewOrVerify(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        try {
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (java.nio.file.FileAlreadyExistsException exists) {
            byte[] prior = Files.readAllBytes(path);
            if (!MessageDigest.isEqual(prior, bytes)) {
                throw new IOException("content-address collision/refusal at " + path);
            }
        }
    }

    private static void atomicReplace(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(temp, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

}
