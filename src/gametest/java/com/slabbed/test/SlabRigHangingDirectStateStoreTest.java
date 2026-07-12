package com.slabbed.test;

import com.slabbed.command.SlabRigHangingDirectEvidence;
import com.slabbed.command.SlabRigHangingDirectState;
import com.slabbed.command.SlabRigHangingDirectStateStore;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registered tests for the RIG-3B3A variable-page state seam and frozen RIG-3B2B1 compatibility.
 *
 * <p>The registered methods use no game-world mutation and run in the standard empty structure.
 */
public final class SlabRigHangingDirectStateStoreTest {

    private static final String NONE = SlabRigHangingDirectState.NO_VALUE;

    private static final String LEGACY_FIXTURE_RELATIVE =
            "src/gametest/resources/data/slabbed_gametest/legacy/rig3b2b1-v1";
    private static final String LEGACY_WRITER_COMMIT =
            "653fac0c79dc393ea444fe933592b87dd65a618a";
    private static final String LEGACY_CHECKSUM_MANIFEST_SHA256 =
            "90adcc386ad652b61d146edc0b3e841b3101333947a4714ef61ff26fccd10f12";
    private static final Pattern CHECKSUM_LINE =
            Pattern.compile("([0-9a-f]{64})  \\./([^\\r\\n]+)");
    private static final Pattern STATE_FILE =
            Pattern.compile("state-([0-9]{20})-([0-9a-f]{64})\\.tsv");
    private static final Pattern UUID_TEXT = Pattern.compile(
            "(?<![0-9a-fA-F])[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-"
                    + "[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}(?![0-9a-fA-F])");

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void legacyRawFixturesAreHashPinnedPrivateCompleteAndCopyable(GameTestHelper helper) {
        Path temporary = null;
        try {
            VerifiedLegacyFixtures verified = verifyLegacyFixtures();
            String sourceDigest = verified.treeDigest();
            temporary = Files.createTempDirectory("slabbed-rig3b3a-v1-fixture-verifier-");

            Path cleanCopy = temporary.resolve("clean-copy");
            copyTree(verified.fixtureRoot(), cleanCopy);
            requireFixture(sourceDigest.equals(treeDigest(cleanCopy)),
                    "byte-identical fixture copy digest disagreed");
            verifyFixtureTree(verified.projectRoot(), cleanCopy, false);

            Path extraCopy = temporary.resolve("extra-copy");
            copyTree(verified.fixtureRoot(), extraCopy);
            Files.writeString(extraCopy.resolve("unexpected-extra.bin"), "extra",
                    StandardCharsets.UTF_8);
            expectFixtureFailure(() -> verifyFixtureTree(
                    verified.projectRoot(), extraCopy, false), "extra fixture file");

            Path linkCopy = temporary.resolve("link-copy");
            copyTree(verified.fixtureRoot(), linkCopy);
            Files.createSymbolicLink(linkCopy.resolve("unexpected-link"),
                    Path.of("PROVENANCE.tsv"));
            expectFixtureFailure(() -> verifyFixtureTree(
                    verified.projectRoot(), linkCopy, false), "fixture symlink");

            Path missingCopy = temporary.resolve("missing-copy");
            copyTree(verified.fixtureRoot(), missingCopy);
            Files.delete(missingCopy.resolve("active-owned/locks/global.lck"));
            expectFixtureFailure(() -> verifyFixtureTree(
                    verified.projectRoot(), missingCopy, false), "missing fixture file");

            Path hashCopy = temporary.resolve("hash-copy");
            copyTree(verified.fixtureRoot(), hashCopy);
            Path hashTarget = hashCopy.resolve("active-owned/locks/global.lck");
            Files.writeString(hashTarget, "hash drift", StandardCharsets.UTF_8);
            expectFixtureFailure(() -> verifyFixtureTree(
                    verified.projectRoot(), hashCopy, false), "fixture checksum drift");

            Path provenanceCopy = temporary.resolve("provenance-copy");
            copyTree(verified.fixtureRoot(), provenanceCopy);
            Path provenancePath = provenanceCopy.resolve("PROVENANCE.tsv");
            String provenance = Files.readString(provenancePath, StandardCharsets.UTF_8);
            String changed = provenance.replace("authoritative_invocation\t4\n",
                    "authoritative_invocation\t5\n");
            requireFixture(!changed.equals(provenance),
                    "provenance mutation premise did not match");
            Files.writeString(provenancePath, changed, StandardCharsets.UTF_8);
            Path checksumsPath = provenanceCopy.resolve("SHA256SUMS");
            String checksums = Files.readString(checksumsPath, StandardCharsets.UTF_8);
            String oldProvenanceHash = SlabRigHangingDirectState.sha256(
                    provenance.getBytes(StandardCharsets.UTF_8));
            String newProvenanceHash = SlabRigHangingDirectState.sha256(
                    changed.getBytes(StandardCharsets.UTF_8));
            String changedChecksums = checksums.replace(
                    oldProvenanceHash + "  ./PROVENANCE.tsv",
                    newProvenanceHash + "  ./PROVENANCE.tsv");
            requireFixture(!changedChecksums.equals(checksums),
                    "provenance checksum mutation premise did not match");
            Files.writeString(checksumsPath, changedChecksums, StandardCharsets.UTF_8);
            expectFixtureFailure(() -> verifyFixtureTree(
                    verified.projectRoot(), provenanceCopy, false), "provenance field drift");

            Path sourceHashCopy = temporary.resolve("source-hash-provenance-copy");
            copyTree(verified.fixtureRoot(), sourceHashCopy);
            Path sourceHashProvenancePath = sourceHashCopy.resolve("PROVENANCE.tsv");
            String sourceHashProvenance = Files.readString(
                    sourceHashProvenancePath, StandardCharsets.UTF_8);
            String sourceHashChanged = sourceHashProvenance.replace(
                    "state_source_sha256\t"
                            + "c5e2330f52a820fbf4a8a2bc3f432e8f357801df62fe6d0a9e711e882796930c\n",
                    "state_source_sha256\t"
                            + "0000000000000000000000000000000000000000000000000000000000000000\n");
            requireFixture(!sourceHashChanged.equals(sourceHashProvenance),
                    "source-hash provenance mutation premise did not match");
            Files.writeString(sourceHashProvenancePath, sourceHashChanged,
                    StandardCharsets.UTF_8);
            Path sourceHashChecksumsPath = sourceHashCopy.resolve("SHA256SUMS");
            String sourceHashChecksums = Files.readString(
                    sourceHashChecksumsPath, StandardCharsets.UTF_8);
            String sourceHashOldProvenanceHash = SlabRigHangingDirectState.sha256(
                    sourceHashProvenance.getBytes(StandardCharsets.UTF_8));
            String sourceHashNewProvenanceHash = SlabRigHangingDirectState.sha256(
                    sourceHashChanged.getBytes(StandardCharsets.UTF_8));
            String sourceHashChangedChecksums = sourceHashChecksums.replace(
                    sourceHashOldProvenanceHash + "  ./PROVENANCE.tsv",
                    sourceHashNewProvenanceHash + "  ./PROVENANCE.tsv");
            requireFixture(!sourceHashChangedChecksums.equals(sourceHashChecksums),
                    "source-hash provenance checksum mutation premise did not match");
            Files.writeString(sourceHashChecksumsPath, sourceHashChangedChecksums,
                    StandardCharsets.UTF_8);
            Map<String, List<String>> sourceHashFields = new LinkedHashMap<>(
                    parseProvenance(sourceHashProvenancePath));
            expectFixtureFailure(() -> verifyHistoricalSource(verified.projectRoot(),
                            sourceHashFields, "state_source_path", "state_source_sha256"),
                    "historical git-show source-hash comparator");
            expectFixtureFailure(() -> verifyFixtureTree(
                    verified.projectRoot(), sourceHashCopy, false),
                    "historical source-hash provenance drift");

            Path mergeRoot = temporary.resolve("existing-store-root");
            Files.createDirectories(mergeRoot.resolve("artifacts"));
            Files.createDirectories(mergeRoot.resolve("ledgers"));
            Files.createDirectories(mergeRoot.resolve("locks"));
            mergeVerifiedScenario(verified.fixtureRoot().resolve("active-owned"), mergeRoot);
            mergeVerifiedScenario(verified.fixtureRoot().resolve("partial-clear"), mergeRoot);
            expectFixtureFailure(() -> mergeVerifiedScenario(
                    verified.fixtureRoot().resolve("active-owned"), mergeRoot),
                    "existing owner-ledger replacement");

            requireFixture(sourceDigest.equals(treeDigest(verified.fixtureRoot())),
                    "tracked fixture tree changed during copy/mutation proofs");
        } catch (IOException | InterruptedException failure) {
            throw helper.assertionException("legacy raw fixture verifier failed: " + failure);
        } finally {
            deleteTree(temporary);
        }
        helper.succeed();
    }

    /** Verified-copy seam used only by the registered real-dispatcher legacy tests. */
    static Path copyVerifiedLegacyScenario(String scenario, Path destination)
            throws IOException, InterruptedException {
        if (!Set.of("active-owned", "partial-clear").contains(scenario)) {
            throw new IOException("unknown legacy fixture scenario: " + scenario);
        }
        VerifiedLegacyFixtures verified = verifyLegacyFixtures();
        String before = verified.treeDigest();
        Path source = verified.fixtureRoot().resolve(scenario);
        mergeVerifiedScenario(source, destination);
        requireFixture(before.equals(treeDigest(verified.fixtureRoot())),
                "tracked fixture tree changed while copying a scenario");
        return destination;
    }

    private static VerifiedLegacyFixtures verifyLegacyFixtures()
            throws IOException, InterruptedException {
        Path projectRoot = locateProjectRoot();
        Path fixtureRoot = projectRoot.resolve(LEGACY_FIXTURE_RELATIVE).normalize();
        requireFixture(fixtureRoot.startsWith(projectRoot)
                        && Files.isDirectory(fixtureRoot, LinkOption.NOFOLLOW_LINKS),
                "tracked legacy fixture root is absent or escaped the project");
        String digest = treeDigest(fixtureRoot);
        verifyFixtureTree(projectRoot, fixtureRoot, true);
        requireFixture(digest.equals(treeDigest(fixtureRoot)),
                "tracked fixture tree changed during verification");
        return new VerifiedLegacyFixtures(projectRoot, fixtureRoot, digest);
    }

    private static void verifyFixtureTree(Path projectRoot, Path fixtureRoot,
                                          boolean verifyHistoricalSources)
            throws IOException, InterruptedException {
        FixtureTree tree = scanTree(fixtureRoot);
        requireFixture(tree.files().size() == 37 && tree.directories().size() == 10,
                "legacy fixture tree must contain exactly 37 files and 11 directories including root");
        requireFixture(SlabRigHangingDirectState.sha256(Files.readAllBytes(
                        fixtureRoot.resolve("SHA256SUMS")))
                        .equals(LEGACY_CHECKSUM_MANIFEST_SHA256),
                "legacy fixture checksum manifest bytes drifted");
        Map<String, String> checksums = parseChecksums(fixtureRoot.resolve("SHA256SUMS"));

        Set<String> expectedFiles = new TreeSet<>(checksums.keySet());
        requireFixture(expectedFiles.add("SHA256SUMS"),
                "SHA256SUMS must be the sole unlisted checksum file");
        requireFixture(tree.files().keySet().equals(expectedFiles),
                "fixture file set is incomplete or contains an extra/unlisted file");
        Set<String> expectedDirectories = expectedDirectories(expectedFiles);
        requireFixture(tree.directories().equals(expectedDirectories),
                "fixture directory set is incomplete or contains an extra directory");

        for (Map.Entry<String, String> checksum : checksums.entrySet()) {
            String actual = SlabRigHangingDirectState.sha256(
                    Files.readAllBytes(tree.files().get(checksum.getKey())));
            requireFixture(actual.equals(checksum.getValue()),
                    "fixture checksum drifted: " + checksum.getKey());
        }
        requireFixture(checksums.size() == 36,
                "legacy fixture checksum cardinality must be exactly 36");
        long rawFiles = checksums.keySet().stream()
                .filter(path -> path.startsWith("active-owned/")
                        || path.startsWith("partial-clear/"))
                .count();
        requireFixture(rawFiles == 34,
                "legacy fixture raw-file cardinality must be exactly 34");
        Set<String> lockFiles = checksums.keySet().stream()
                .filter(path -> path.contains("/locks/"))
                .collect(java.util.stream.Collectors.toSet());
        requireFixture(lockFiles.equals(Set.of(
                        "active-owned/locks/global.lck",
                        "active-owned/locks/owner-"
                                + "b937ea786ef33e15fe54ef742723b73d9e5cae6d83c7041e6ac3b265fc274d1e.lck",
                        "partial-clear/locks/global.lck",
                        "partial-clear/locks/owner-"
                                + "6a02298308838e4527daeacac2ded4ade2851d73499e53e1730a762eedec357e.lck")),
                "legacy fixture lock paths are not the exact four-value allowlist");
        for (String lockFile : lockFiles) {
            requireFixture(checksums.get(lockFile).equals(
                            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                            && Files.size(tree.files().get(lockFile)) == 0,
                    "legacy fixture lock file is not the exact empty regular-file identity");
        }

        Map<String, List<String>> provenance = parseProvenance(
                fixtureRoot.resolve("PROVENANCE.tsv"));
        validateProvenance(provenance);
        verifyPrivacyAndFixedUuids(tree);

        if (verifyHistoricalSources) {
            verifyHistoricalSource(projectRoot, provenance,
                    "state_source_path", "state_source_sha256");
            verifyHistoricalSource(projectRoot, provenance,
                    "store_source_path", "store_source_sha256");
        }
        Path declaredExporter = projectRoot.resolve(
                single(provenance, "exporter_path")).normalize();
        if (verifyHistoricalSources) {
            requireFixture(declaredExporter.equals(fixtureRoot.resolve("EXPORTER.java.txt")),
                    "retained exporter provenance path is not the exact tracked fixture file");
        }
        Path exporter = fixtureRoot.resolve("EXPORTER.java.txt");
        requireFixture(Files.isRegularFile(exporter, LinkOption.NOFOLLOW_LINKS),
                "retained exporter is missing from the verified fixture tree");
        requireFixture(SlabRigHangingDirectState.sha256(Files.readAllBytes(exporter))
                        .equals(single(provenance, "exporter_sha256")),
                "retained exporter hash disagreed with provenance");

        Set<String> allowedCoordinates = Set.copyOf(provenance.get("allowed_coordinate"));
        verifyScenario(fixtureRoot, provenance, "active", 6,
                SlabRigHangingDirectState.Phase.IMMEDIATE_PARTIAL,
                1, 0, 0, 0, allowedCoordinates);
        verifyScenario(fixtureRoot, provenance, "partial", 12,
                SlabRigHangingDirectState.Phase.CLEARING_CELLS,
                1, 1, 2, 1, allowedCoordinates);
    }

    private static Map<String, String> parseChecksums(Path checksumPath) throws IOException {
        List<String> lines = strictLines(checksumPath);
        Map<String, String> checksums = new TreeMap<>();
        for (String line : lines) {
            Matcher matcher = CHECKSUM_LINE.matcher(line);
            requireFixture(matcher.matches(), "non-canonical SHA256SUMS line");
            String relative = matcher.group(2);
            Path parsed = Path.of(relative);
            requireFixture(!parsed.isAbsolute() && !relative.contains("\\")
                            && !relative.startsWith(".")
                            && parsed.normalize().equals(parsed)
                            && parsed.getNameCount() > 0,
                    "checksum path is absolute, escaping, or non-canonical: " + relative);
            requireFixture(!"SHA256SUMS".equals(relative),
                    "SHA256SUMS cannot checksum itself");
            requireFixture(checksums.put(relative, matcher.group(1)) == null,
                    "duplicate checksum path: " + relative);
        }
        return Map.copyOf(checksums);
    }

    private static Map<String, List<String>> parseProvenance(Path provenancePath)
            throws IOException {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String line : strictLines(provenancePath)) {
            String[] fields = line.split("\\t", -1);
            requireFixture(fields.length == 2 && fields[0].matches("[a-z0-9_]+")
                            && !fields[1].isEmpty(),
                    "non-canonical provenance line");
            result.computeIfAbsent(fields[0], ignored -> new ArrayList<>()).add(fields[1]);
        }
        return result;
    }

    private static void validateProvenance(Map<String, List<String>> actual)
            throws IOException {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("schema", "slabbed-rig3b3a-legacy-v1-fixture-provenance-v1");
        expected.put("writer_commit", LEGACY_WRITER_COMMIT);
        expected.put("state_source_path",
                "src/main/java/com/slabbed/command/SlabRigHangingDirectState.java");
        expected.put("state_source_sha256",
                "c5e2330f52a820fbf4a8a2bc3f432e8f357801df62fe6d0a9e711e882796930c");
        expected.put("store_source_path",
                "src/main/java/com/slabbed/command/SlabRigHangingDirectStateStore.java");
        expected.put("store_source_sha256",
                "4e5294e15f51b5087990079c002a849849298d92380908177546c0a29147b8dc");
        expected.put("exporter_path", LEGACY_FIXTURE_RELATIVE + "/EXPORTER.java.txt");
        expected.put("exporter_sha256",
                "98df5ab0062333c45ca25dac3084511712b40b2d87c2de2e45fd83a049f3e085");
        expected.put("evidence_root", "tmp/rig3b3a-20260712T044815Z-f43bba02");
        expected.put("authoritative_export_subpath", "legacy-v1-export");
        expected.put("authoritative_invocation", "4");
        expected.put("normalized_generation_command",
                "JAVA_TOOL_OPTIONS='-Dslabbed.rig3b3a.fixtureExportRoot="
                        + "tmp/rig3b3a-20260712T044815Z-f43bba02/legacy-v1-export "
                        + "-Dfabric-api.gametest.filter=slabbed_gametest:"
                        + "slab_rig_hanging_direct_state_store_test_legacy_fixture_export_test' "
                        + "JAVA_HOME=$(/usr/libexec/java_home -v 25) "
                        + "./gradlew --no-daemon runGameTest --console=plain");
        expected.put("raw_file_count", "34");
        expected.put("fixed_world_key",
                "e984d84acbed5af5211a045f130d9d74e816f96da5a07095658aeb6cb3cf34bf");
        expected.put("fixed_dimension", "minecraft:overworld");
        expected.put("active_scenario", "active-owned");
        expected.put("active_player_uuid", "316f2c48-8754-5f9d-975d-984e406f8ea3");
        expected.put("active_owner_key",
                "b937ea786ef33e15fe54ef742723b73d9e5cae6d83c7041e6ac3b265fc274d1e");
        expected.put("active_run_id",
                "ea9af5cf8f64a083245be3b22b8f80a1bcef6e1cbe6a321fe1d326af4ed64f28");
        expected.put("active_run_nonce", "b87fe380-0b2d-3309-9cbd-3b4dd78a789a");
        expected.put("active_entity_uuid", "f83a1777-007d-3093-b166-af82eb65275b");
        expected.put("active_latest_state",
                "state-00000000000000000006-1cb35598639ddeff8e63994b06437a5274aa047e660d8f0de9f71efe79a6f42a.tsv");
        expected.put("active_phase", "IMMEDIATE_PARTIAL");
        expected.put("active_next_case_ordinal", "1");
        expected.put("active_clear_cursors", "0,0,0");
        expected.put("partial_scenario", "partial-clear");
        expected.put("partial_player_uuid", "bc6fda81-b928-54c7-8b48-34eadba544a9");
        expected.put("partial_owner_key",
                "6a02298308838e4527daeacac2ded4ade2851d73499e53e1730a762eedec357e");
        expected.put("partial_run_id",
                "d2747b19952f0f8379d7a100cc2ec6a910a64129866917c92bd8dfd06457c06a");
        expected.put("partial_run_nonce", "06d46aaf-9675-3cc4-8073-57cdc02e237b");
        expected.put("partial_entity_uuid", "87808b7e-2b3e-3536-a129-0c876d4eec6a");
        expected.put("partial_latest_state",
                "state-00000000000000000012-0ab8f3dc0fe993da118a56a4886dbbc8dae26ed862ebb80f5bbb3b219d7013b1.tsv");
        expected.put("partial_phase", "CLEARING_CELLS");
        expected.put("partial_next_case_ordinal", "1");
        expected.put("partial_clear_cursors", "1,2,1");
        expected.put("partial_processed_cell", "8,65,8");
        expected.put("privacy_policy", "no-user-path-or-live-profile-identifiers");
        expected.put("superseded_evidence",
                "failed-export-run2,superseded-export-run3-one-owner-unbounded-base");

        Set<String> expectedKeys = new LinkedHashSet<>(expected.keySet());
        expectedKeys.add("allowed_coordinate");
        requireFixture(actual.keySet().equals(expectedKeys),
                "provenance key set/cardinality drifted");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            requireFixture(actual.get(entry.getKey()).equals(List.of(entry.getValue())),
                    "provenance field drifted: " + entry.getKey());
        }
        requireFixture(actual.get("allowed_coordinate").equals(
                        List.of("8,64,8", "8,65,8", "9,65,8")),
                "provenance allowed-coordinate set/order drifted");
    }

    private static void verifyHistoricalSource(Path projectRoot,
                                               Map<String, List<String>> provenance,
                                               String pathKey, String hashKey)
            throws IOException, InterruptedException {
        String sourcePath = single(provenance, pathKey);
        Process process = new ProcessBuilder("git", "-C", projectRoot.toString(),
                "show", "--no-ext-diff", LEGACY_WRITER_COMMIT + ":" + sourcePath).start();
        byte[] source = process.getInputStream().readAllBytes();
        byte[] errors = process.getErrorStream().readAllBytes();
        int exit = process.waitFor();
        requireFixture(exit == 0,
                "git show failed for historical source " + sourcePath + ": "
                        + new String(errors, StandardCharsets.UTF_8));
        requireFixture(SlabRigHangingDirectState.sha256(source)
                        .equals(single(provenance, hashKey)),
                "historical git-show source hash drifted: " + sourcePath);
    }

    private static void verifyPrivacyAndFixedUuids(FixtureTree tree) throws IOException {
        List<String> forbidden = List.of(
                "/" + "Users" + "/",
                "jool" + "mac",
                "Modrinth" + "App",
                "SLABBED" + "-MC 26.2",
                "Universal Testing" + " World",
                "TEST" + " 19V",
                "TEST" + " (19V)");
        Set<String> foundUuids = new TreeSet<>();
        for (Map.Entry<String, Path> file : tree.files().entrySet()) {
            String bytes = (file.getKey() + "\0" + new String(
                    Files.readAllBytes(file.getValue()), StandardCharsets.ISO_8859_1))
                    .toLowerCase(java.util.Locale.ROOT);
            for (String token : forbidden) {
                requireFixture(!bytes.contains(token.toLowerCase(java.util.Locale.ROOT)),
                        "fixture contains a forbidden private/live token: " + file.getKey());
            }
            Matcher matcher = UUID_TEXT.matcher(bytes);
            while (matcher.find()) {
                foundUuids.add(matcher.group().toLowerCase(java.util.Locale.ROOT));
            }
        }
        Set<String> expectedUuids = Set.of(
                "316f2c48-8754-5f9d-975d-984e406f8ea3",
                "bc6fda81-b928-54c7-8b48-34eadba544a9",
                "b87fe380-0b2d-3309-9cbd-3b4dd78a789a",
                "06d46aaf-9675-3cc4-8073-57cdc02e237b",
                "f83a1777-007d-3093-b166-af82eb65275b",
                "87808b7e-2b3e-3536-a129-0c876d4eec6a");
        requireFixture(foundUuids.equals(expectedUuids),
                "fixture UUID set is not the exact deterministic six-value allowlist");
    }

    private static void verifyScenario(Path fixtureRoot, Map<String, List<String>> provenance,
                                       String prefix, int finalSequence,
                                       SlabRigHangingDirectState.Phase finalPhase,
                                       int nextCaseOrdinal, int entityCursor,
                                       int attachmentCursor, int cellCursor,
                                       Set<String> allowedCoordinates) throws IOException {
        String scenario = single(provenance, prefix + "_scenario");
        String ownerKey = single(provenance, prefix + "_owner_key");
        String playerUuid = single(provenance, prefix + "_player_uuid");
        String runId = single(provenance, prefix + "_run_id");
        String runNonce = single(provenance, prefix + "_run_nonce");
        String entityUuid = single(provenance, prefix + "_entity_uuid");
        String latestName = single(provenance, prefix + "_latest_state");
        Path scenarioRoot = fixtureRoot.resolve(scenario);
        Path ledgerRoot = scenarioRoot.resolve("ledgers").resolve(ownerKey);
        requireFixture(Files.isDirectory(ledgerRoot, LinkOption.NOFOLLOW_LINKS),
                "scenario ledger directory is missing: " + scenario);

        List<Path> statePaths;
        try (var stream = Files.list(ledgerRoot)) {
            statePaths = stream.sorted().toList();
        }
        requireFixture(statePaths.size() == finalSequence + 1,
                "scenario state sequence cardinality drifted: " + scenario);
        Set<String> observedCoordinates = new TreeSet<>();
        Set<String> referencedArtifacts = new TreeSet<>();
        SlabRigHangingDirectState.State previous = null;
        SlabRigHangingDirectState.State latest = null;
        for (int sequence = 0; sequence < statePaths.size(); sequence++) {
            Path statePath = statePaths.get(sequence);
            Matcher name = STATE_FILE.matcher(statePath.getFileName().toString());
            requireFixture(name.matches() && Long.parseLong(name.group(1)) == sequence,
                    "scenario state filename/sequence drifted: " + statePath.getFileName());
            String raw = Files.readString(statePath, StandardCharsets.UTF_8);
            SlabRigHangingDirectState.State state;
            try {
                state = SlabRigHangingDirectState.parse(raw);
            } catch (IllegalArgumentException | IllegalStateException malformed) {
                throw new IOException("raw legacy state failed strict parsing: " + statePath, malformed);
            }
            requireFixture(raw.equals(SlabRigHangingDirectState.canonicalTsv(state)),
                    "raw legacy state is not byte-canonical: " + statePath.getFileName());
            requireFixture(state.sequence() == sequence && state.stateHash().equals(name.group(2)),
                    "raw legacy state filename disagrees with content");
            requireFixture(previous == null
                            ? SlabRigHangingDirectState.NO_PREDECESSOR.equals(state.predecessorHash())
                            : previous.stateHash().equals(state.predecessorHash()),
                    "raw legacy state predecessor chain drifted");
            requireFixture(raw.startsWith("schema\tslabbed-rig-hanging-direct-state-v1\n")
                            && raw.contains("\nexecution_contract\t"
                            + "rig3b2b1-route6143-topology42-selectorpage1-v1\n")
                            && raw.contains("\nowner_key\t" + ownerKey + "\n"),
                    "raw legacy schema/contract/owner-key identity drifted");
            requireFixture(state.owner().worldKey().equals(single(provenance, "fixed_world_key"))
                            && state.owner().dimension().equals(single(provenance, "fixed_dimension"))
                            && state.owner().playerUuid().toString().equals(playerUuid),
                    "raw legacy owner identity drifted");
            requireFixture(state.run().runId().equals(runId)
                            && state.run().runNonce().toString().equals(runNonce)
                            && state.run().routeIndex() == 6143
                            && state.run().topologyIndex() == 42
                            && state.run().selectorPage() == 1
                            && coordinate(state.run().base()).equals("8,64,8")
                            && state.cases().size() == 16,
                    "raw legacy run/cardinality identity drifted");
            for (SlabRigHangingDirectState.EntityOwnership entity : state.entities()) {
                requireFixture(entity.uuid().toString().equals(entityUuid),
                        "raw legacy scenario contains a non-allowlisted owned entity");
                requireFixture(entity.position().equals(
                                new SlabRigHangingDirectState.Vec3Bits(0, 0, 0))
                                && entity.aabb().equals(new SlabRigHangingDirectState.BoxBits(
                                0, 0, 0, 1, 1, 1)),
                        "raw legacy entity position/bounds drifted from the fixed synthetic identity");
                addArtifact(referencedArtifacts, entity.evidenceArtifact());
                if (entity.disposition() == SlabRigHangingDirectState.EntityDisposition.REMOVED) {
                    addArtifact(referencedArtifacts, entity.removalArtifact());
                }
            }
            collectStateCoordinates(state, observedCoordinates);
            for (String coordinate : observedCoordinates) {
                requireFixture(allowedCoordinates.contains(coordinate),
                        "raw legacy state contains a non-allowlisted coordinate: " + coordinate);
            }
            addArtifact(referencedArtifacts, state.artifacts().planned());
            addArtifact(referencedArtifacts, state.artifacts().immediate());
            addArtifact(referencedArtifacts, state.artifacts().finalArtifact());
            addArtifact(referencedArtifacts, state.artifacts().cleared());
            for (SlabRigHangingDirectState.CaseState caseState : state.cases()) {
                addArtifact(referencedArtifacts, caseState.immediateObservationId());
            }
            for (SlabRigHangingDirectState.CellOwnership cell : state.authoredCells()) {
                addArtifact(referencedArtifacts, cell.fingerprint());
                SlabRigHangingDirectState.AttachmentOwnership attachment =
                        state.authoredAttachments().stream()
                                .filter(candidate -> candidate.pos().equals(cell.pos()))
                                .findFirst().orElseThrow(() -> new IOException(
                                        "owned legacy cell lacks its attachment receipt"));
                Path artifact = scenarioRoot.resolve("artifacts/artifact-"
                        + cell.fingerprint() + ".bin");
                try {
                    SlabRigHangingDirectEvidence.verifyCellAndAttachmentArtifact(
                            cell.pos().toBlockPos(), cell.fingerprint(), attachment.fingerprint(),
                            Files.readAllBytes(artifact));
                } catch (IllegalArgumentException malformed) {
                    throw new IOException("legacy cell/attachment artifact proof drifted", malformed);
                }
            }
            previous = state;
            latest = state;
        }
        List<SlabRigHangingDirectStateStore.Reconstruction> reconstructed =
                new SlabRigHangingDirectStateStore(scenarioRoot).reconstructAll();
        requireFixture(reconstructed.size() == 1,
                "old Store reconstruction did not find exactly one legacy owner");
        SlabRigHangingDirectStateStore.Reconstruction storeProof = reconstructed.getFirst();
        requireFixture(storeProof.states().size() == finalSequence + 1
                        && storeProof.owner().worldKey().equals(
                        single(provenance, "fixed_world_key"))
                        && storeProof.owner().dimension().equals(
                        single(provenance, "fixed_dimension"))
                        && storeProof.owner().playerUuid().toString().equals(playerUuid)
                        && storeProof.latestOrNull() != null
                        && latest != null
                        && storeProof.latestOrNull().stateHash().equals(latest.stateHash())
                        && storeProof.latestOrNull().phase() == finalPhase,
                "old Store reconstruction disagreed with the exact legacy chain/latest state");
        requireFixture(observedCoordinates.equals(allowedCoordinates),
                "scenario did not realize the exact three-coordinate fixture envelope");
        requireFixture(latest != null
                        && statePaths.getLast().getFileName().toString().equals(latestName)
                        && latest.phase() == finalPhase
                        && latest.nextCaseOrdinal() == nextCaseOrdinal
                        && latest.clear().entityCursor() == entityCursor
                        && latest.clear().attachmentCursor() == attachmentCursor
                        && latest.clear().cellCursor() == cellCursor,
                "scenario latest phase/cursor identity drifted: " + scenario);
        if ("partial".equals(prefix)) {
            requireFixture(latest.clear().clearedCells().stream().map(
                            SlabRigHangingDirectStateStoreTest::coordinate).toList()
                            .equals(List.of(single(provenance, "partial_processed_cell"))),
                    "partial-clear processed-cell cursor identity drifted");
        }

        Set<String> actualArtifacts = new TreeSet<>();
        Set<String> artifactCoordinates = new TreeSet<>();
        Path artifactRoot = scenarioRoot.resolve("artifacts");
        try (var stream = Files.list(artifactRoot)) {
            for (Path artifact : stream.sorted().toList()) {
                String filename = artifact.getFileName().toString();
                requireFixture(filename.matches("artifact-[0-9a-f]{64}\\.bin"),
                        "legacy artifact filename is non-canonical");
                String hash = filename.substring("artifact-".length(),
                        filename.length() - ".bin".length());
                byte[] bytes = Files.readAllBytes(artifact);
                requireFixture(SlabRigHangingDirectState.sha256(bytes).equals(hash),
                        "legacy artifact filename hash drifted");
                actualArtifacts.add(hash);
                try {
                    artifactCoordinates.add(coordinate(
                            SlabRigHangingDirectEvidence.parseCellIdentityArtifact(bytes).pos()));
                } catch (IllegalArgumentException notCellEvidence) {
                    // Planned/entity/observation artifacts use other exact canonical grammars.
                }
            }
        }
        requireFixture(actualArtifacts.equals(referencedArtifacts),
                "legacy artifact set is not exactly the state-linked set");
        requireFixture(artifactCoordinates.equals(Set.of("8,64,8", "8,65,8")),
                "legacy full-cell artifact coordinate set drifted");
    }

    private static void collectStateCoordinates(SlabRigHangingDirectState.State state,
                                                Set<String> coordinates) {
        coordinates.add(coordinate(state.run().base()));
        state.reservedCells().forEach(pos -> coordinates.add(coordinate(pos)));
        state.plannedAuthoredCells().forEach(pos -> coordinates.add(coordinate(pos)));
        state.authoredCells().forEach(cell -> coordinates.add(coordinate(cell.pos())));
        state.authoredAttachments().forEach(attachment ->
                coordinates.add(coordinate(attachment.pos())));
        SlabRigHangingDirectState.ClearProgress clear = state.clear();
        clear.requestedAttachments().forEach(pos -> coordinates.add(coordinate(pos)));
        clear.clearedAttachments().forEach(pos -> coordinates.add(coordinate(pos)));
        clear.absentAttachments().forEach(pos -> coordinates.add(coordinate(pos)));
        clear.refusedAttachments().forEach(pos -> coordinates.add(coordinate(pos)));
        clear.requestedCells().forEach(pos -> coordinates.add(coordinate(pos)));
        clear.clearedCells().forEach(pos -> coordinates.add(coordinate(pos)));
        clear.absentCells().forEach(pos -> coordinates.add(coordinate(pos)));
        clear.refusedCells().forEach(pos -> coordinates.add(coordinate(pos)));
    }

    private static String coordinate(SlabRigHangingDirectState.Position pos) {
        return pos.x() + "," + pos.y() + "," + pos.z();
    }

    private static String coordinate(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static void addArtifact(Set<String> artifacts, String value) {
        if (!NONE.equals(value)) {
            artifacts.add(value);
        }
    }

    private static FixtureTree scanTree(Path root) throws IOException {
        requireFixture(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS),
                "fixture tree root is not a real directory");
        Map<String, Path> files = new TreeMap<>();
        Set<String> directories = new TreeSet<>();
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted().toList()) {
                BasicFileAttributes attributes = Files.readAttributes(
                        path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                requireFixture(!attributes.isSymbolicLink() && !attributes.isOther(),
                        "fixture tree contains a link/non-regular entry: " + path);
                if (path.equals(root)) {
                    requireFixture(attributes.isDirectory(),
                            "fixture tree root changed type");
                    continue;
                }
                String relative = unix(root.relativize(path));
                if (attributes.isDirectory()) {
                    directories.add(relative);
                } else {
                    requireFixture(attributes.isRegularFile(),
                            "fixture tree contains a non-regular file: " + relative);
                    requireFixture(files.put(relative, path) == null,
                            "fixture tree contains a duplicate path: " + relative);
                }
            }
        }
        return new FixtureTree(Map.copyOf(files), Set.copyOf(directories));
    }

    private static Set<String> expectedDirectories(Set<String> files) {
        Set<String> expected = new TreeSet<>();
        for (String file : files) {
            Path parent = Path.of(file).getParent();
            while (parent != null) {
                expected.add(unix(parent));
                parent = parent.getParent();
            }
        }
        return expected;
    }

    private static String treeDigest(Path root) throws IOException {
        FixtureTree tree = scanTree(root);
        StringBuilder canonical = new StringBuilder();
        for (String directory : new TreeSet<>(tree.directories())) {
            canonical.append("D\0").append(directory).append('\0');
        }
        for (Map.Entry<String, Path> file : new TreeMap<>(tree.files()).entrySet()) {
            canonical.append("F\0").append(file.getKey()).append('\0')
                    .append(SlabRigHangingDirectState.sha256(
                            Files.readAllBytes(file.getValue())))
                    .append('\0');
        }
        return SlabRigHangingDirectState.sha256(canonical.toString());
    }

    private static void mergeVerifiedScenario(Path source, Path destination)
            throws IOException {
        FixtureTree tree = scanTree(source);
        requireFixture(tree.directories().containsAll(
                        Set.of("artifacts", "ledgers", "locks"))
                        && tree.directories().stream().allMatch(path ->
                        path.equals("artifacts") || path.startsWith("artifacts/")
                                || path.equals("ledgers") || path.startsWith("ledgers/")
                                || path.equals("locks") || path.startsWith("locks/")),
                "legacy scenario contains a path outside artifacts/ledgers/locks");
        List<String> ownerLedgers = tree.directories().stream()
                .filter(path -> path.startsWith("ledgers/")
                        && Path.of(path).getNameCount() == 2)
                .sorted().toList();
        requireFixture(ownerLedgers.size() == 1,
                "legacy scenario must contain exactly one owner-ledger directory");

        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            BasicFileAttributes attributes = Files.readAttributes(
                    destination, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            requireFixture(attributes.isDirectory() && !attributes.isSymbolicLink(),
                    "legacy scenario destination is not a real directory");
        }
        Path ownerDestination = destination.resolve(ownerLedgers.getFirst());
        requireFixture(!Files.exists(ownerDestination, LinkOption.NOFOLLOW_LINKS),
                "legacy owner ledger already exists; replacement is forbidden");

        for (String directory : tree.directories()) {
            Path target = destination.resolve(directory);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                BasicFileAttributes attributes = Files.readAttributes(
                        target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                requireFixture(attributes.isDirectory() && !attributes.isSymbolicLink(),
                        "legacy scenario merge encountered a non-directory/link: " + directory);
            }
        }
        for (Map.Entry<String, Path> file : tree.files().entrySet()) {
            Path target = destination.resolve(file.getKey());
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            requireFixture(file.getKey().startsWith("artifacts/")
                            || file.getKey().startsWith("locks/"),
                    "legacy owner-ledger file replacement is forbidden: " + file.getKey());
            BasicFileAttributes attributes = Files.readAttributes(
                    target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            requireFixture(attributes.isRegularFile() && !attributes.isSymbolicLink()
                            && java.util.Arrays.equals(Files.readAllBytes(file.getValue()),
                            Files.readAllBytes(target)),
                    "existing shared artifact/lock is not byte/type-identical: " + file.getKey());
        }

        if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(destination);
        }
        for (String directory : new TreeSet<>(tree.directories())) {
            Path target = destination.resolve(directory);
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(target);
            }
        }
        for (Map.Entry<String, Path> file : tree.files().entrySet()) {
            Path target = destination.resolve(file.getKey());
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.copy(file.getValue(), target);
            }
        }
        for (String directory : tree.directories()) {
            BasicFileAttributes attributes = Files.readAttributes(
                    destination.resolve(directory), BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            requireFixture(attributes.isDirectory() && !attributes.isSymbolicLink(),
                    "legacy scenario merge changed a destination directory type");
        }
        for (Map.Entry<String, Path> file : tree.files().entrySet()) {
            Path target = destination.resolve(file.getKey());
            BasicFileAttributes attributes = Files.readAttributes(
                    target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            requireFixture(attributes.isRegularFile() && !attributes.isSymbolicLink()
                            && java.util.Arrays.equals(Files.readAllBytes(file.getValue()),
                            Files.readAllBytes(target)),
                    "legacy scenario merge did not preserve exact source bytes");
        }
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        requireFixture(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS),
                "verified fixture copy destination already exists");
        FixtureTree tree = scanTree(source);
        Files.createDirectories(destination);
        for (String directory : new TreeSet<>(tree.directories())) {
            Files.createDirectories(destination.resolve(directory));
        }
        for (Map.Entry<String, Path> file : tree.files().entrySet()) {
            Path target = destination.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.copy(file.getValue(), target, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static List<String> strictLines(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        requireFixture(text.endsWith("\n") && !text.contains("\r"),
                "fixture metadata must be LF-terminated strict UTF-8");
        String[] split = text.split("\n", -1);
        requireFixture(split.length > 1 && split[split.length - 1].isEmpty(),
                "fixture metadata line framing drifted");
        List<String> lines = List.of(split).subList(0, split.length - 1);
        requireFixture(lines.stream().noneMatch(String::isEmpty),
                "fixture metadata contains an empty line");
        return lines;
    }

    private static String single(Map<String, List<String>> fields, String key)
            throws IOException {
        List<String> values = fields.get(key);
        requireFixture(values != null && values.size() == 1,
                "fixture provenance field is missing/repeated: " + key);
        return values.getFirst();
    }

    private static Path locateProjectRoot() throws IOException, InterruptedException {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path gitMarker = current.resolve(".git");
            if (Files.exists(gitMarker, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(gitMarker)
                    && Files.isRegularFile(current.resolve("gradlew"), LinkOption.NOFOLLOW_LINKS)
                    && Files.isRegularFile(current.resolve("settings.gradle"),
                    LinkOption.NOFOLLOW_LINKS)) {
                Process process = new ProcessBuilder("git", "-C", current.toString(),
                        "rev-parse", "--show-toplevel").redirectErrorStream(true).start();
                String output = new String(process.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8).trim();
                int exit = process.waitFor();
                if (exit == 0 && !output.isEmpty()
                        && Path.of(output).toAbsolutePath().normalize().equals(current)) {
                    return current;
                }
            }
            current = current.getParent();
        }
        throw new IOException("could not locate the project root from the GameTest process");
    }

    private static String unix(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static void expectFixtureFailure(InterruptibleIoAction action, String label)
            throws IOException, InterruptedException {
        try {
            action.run();
        } catch (IOException expected) {
            // Expected mutation kill.
            return;
        }
        throw new FixtureVerificationFailure(
                label + " unexpectedly passed the fail-closed verifier");
    }

    private static void requireFixture(boolean condition, String message)
            throws FixtureVerificationFailure {
        if (!condition) {
            throw new FixtureVerificationFailure(message);
        }
    }

    private record VerifiedLegacyFixtures(Path projectRoot, Path fixtureRoot,
                                          String treeDigest) {
    }

    private record FixtureTree(Map<String, Path> files, Set<String> directories) {
    }

    private static final class FixtureVerificationFailure extends IOException {
        private FixtureVerificationFailure(String message) {
            super(message);
        }
    }

    @FunctionalInterface
    private interface InterruptibleIoAction {
        void run() throws IOException, InterruptedException;
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void variableV2PageFourUsesPersistedFourCaseBoundary(GameTestHelper helper) {
        SlabRigHangingDirectState.Owner owner = owner(404);
        SlabRigHangingDirectState.RunIdentity identity = new SlabRigHangingDirectState.RunIdentity(
                sha("run-page-four"),
                UUID.nameUUIDFromBytes("nonce-page-four".getBytes(StandardCharsets.UTF_8)),
                "unknown", sha("runtime-page-four"), "26.2", sha("catalog-page-four"),
                sha("topologies-page-four"), sha("rig3b1-page-four"),
                sha("painting-registry-page-four"), sha("universe-page-four"),
                sha("plan-page-four"), "painting-page-v1:sha256:" + sha("semantic-page-four"),
                6143, 42, 4, 4, true,
                new SlabRigHangingDirectState.Position(44, 64, 44), "west");
        List<SlabRigHangingDirectState.Position> plannedCells = List.of(
                new SlabRigHangingDirectState.Position(0, 64, 0),
                new SlabRigHangingDirectState.Position(0, 65, 0));
        List<SlabRigHangingDirectState.Position> reserved = List.of(
                plannedCells.get(0), plannedCells.get(1),
                new SlabRigHangingDirectState.Position(1, 65, 0));
        SlabRigHangingDirectState.State state = SlabRigHangingDirectState.State.initial(
                owner, identity, reserved, plannedCells, pendingCases(4),
                sha("planned-page-four"), "planned page four");
        String canonical = SlabRigHangingDirectState.canonicalTsv(state);
        if (state.format() != SlabRigHangingDirectState.Format.VARIABLE_V2
                || !canonical.startsWith("schema\tslabbed-rig-hanging-direct-state-v2\n")
                || !canonical.contains("\ncase_count\t4\n")
                || !state.equals(SlabRigHangingDirectState.parse(canonical))) {
            throw helper.assertionException("page-four v2 schema/caseCount did not round-trip");
        }

        state = fixtureReady(state);
        for (int ordinal = 0; ordinal < 3; ordinal++) {
            state = beginCase(state, ordinal);
            SlabRigHangingDirectState.EntityOwnership painting = paintingPreclaim(state, ordinal);
            state = state.withPreclaimedEntity(painting, "page-four preclaim " + ordinal);
            state = state.withConfirmedEntity(painting.uuid(), "page-four load " + ordinal);
            state = completeCase(state, ordinal, sha("page-four-observation-" + ordinal));
        }
        if (state.phase() != SlabRigHangingDirectState.Phase.IMMEDIATE_PARTIAL
                || state.nextCaseOrdinal() != 3) {
            throw helper.assertionException(
                    "page four did not remain partial after ordinal 2/before ordinal 3");
        }
        SlabRigHangingDirectState.State ordinalTwo = state;
        SlabRigHangingDirectState.ArtifactLinks prematureArtifacts =
                new SlabRigHangingDirectState.ArtifactLinks(
                        state.artifacts().planned(), sha("premature-page-four-immediate"),
                        state.artifacts().finalArtifact(), state.artifacts().cleared());
        expectRuntimeFailure(helper, () -> ordinalTwo.successor(
                        SlabRigHangingDirectState.Phase.IMMEDIATE, 3,
                        ordinalTwo.authoredCells(), ordinalTwo.authoredAttachments(),
                        ordinalTwo.cases(), ordinalTwo.entities(), ordinalTwo.scheduler(),
                        ordinalTwo.clear(), prematureArtifacts,
                        "must not finalize after only three page-four cases"),
                "page-four premature completion after ordinal 2");

        state = beginCase(state, 3);
        SlabRigHangingDirectState.EntityOwnership fourth = paintingPreclaim(state, 3);
        state = state.withPreclaimedEntity(fourth, "page-four fourth preclaim");
        state = state.withConfirmedEntity(fourth.uuid(), "page-four fourth load");
        state = completeCase(state, 3, sha("page-four-observation-3"));
        if (state.phase() != SlabRigHangingDirectState.Phase.IMMEDIATE
                || state.nextCaseOrdinal() != 4 || state.cases().size() != 4) {
            throw helper.assertionException(
                    "page-four ordinal 3 did not reach the exact four-case IMMEDIATE boundary");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void legacyV1StoreIsDiscoverableIdempotentAndAppendClearOnly(GameTestHelper helper) {
        Path root = null;
        Path rejectedGenesisRoot = null;
        Path emptyCrashRoot = null;
        try {
            root = Files.createTempDirectory("slabbed-legacy-v1-store-");
            Path storeRoot = root.resolve("store");
            copyVerifiedLegacyScenario("active-owned", storeRoot);
            SlabRigHangingDirectState.Owner owner = new SlabRigHangingDirectState.Owner(
                    "e984d84acbed5af5211a045f130d9d74e816f96da5a07095658aeb6cb3cf34bf",
                    "minecraft:overworld",
                    UUID.fromString("316f2c48-8754-5f9d-975d-984e406f8ea3"));
            SlabRigHangingDirectStateStore store = new SlabRigHangingDirectStateStore(storeRoot);
            SlabRigHangingDirectStateStore.Reconstruction legacy = store.reconstructLegacy(owner);
            if (legacy.format() != SlabRigHangingDirectState.Format.LEGACY_V1
                    || !legacy.ownerKey().equals(owner.legacyKey())
                    || legacy.states().size() != 7
                    || !store.reconstruct(owner).isEmpty()) {
                throw helper.assertionException(
                        "legacy and variable owner namespaces were not exact/disjoint");
            }
            SlabRigHangingDirectState.State head = legacy.latestOrNull();
            SlabRigHangingDirectStateStore.WrittenState idempotent = store.append(
                    legacy.states().get(legacy.states().size() - 2), head);
            if (!idempotent.alreadyExisted()) {
                throw helper.assertionException("exact existing legacy state was not idempotent");
            }

            SlabRigHangingDirectState.State variableGenesis =
                    initial(head.artifacts().planned(), owner);
            expectIoFailure(helper, () -> store.append(null, variableGenesis),
                    "variable genesis over active legacy allocation");
            Path variableLedger = store.ledgerPath(owner);
            long variableStateFiles = 0;
            if (Files.isDirectory(variableLedger)) {
                try (var files = Files.list(variableLedger)) {
                    variableStateFiles = files.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().startsWith("state-"))
                            .count();
                }
            }
            if (!store.reconstruct(owner).isEmpty() || variableStateFiles != 0) {
                throw helper.assertionException(
                        "active legacy allocation refusal still published variable state bytes");
            }

            List<SlabRigHangingDirectState.CaseState> replay = new ArrayList<>(head.cases());
            replay.set(head.nextCaseOrdinal(), replay.get(head.nextCaseOrdinal()).inFlight());
            expectRuntimeFailure(helper, () -> head.successor(
                            SlabRigHangingDirectState.Phase.CASE_IN_FLIGHT,
                            head.nextCaseOrdinal(), head.authoredCells(),
                            head.authoredAttachments(), replay, head.entities(), head.scheduler(),
                            head.clear(), head.artifacts(), "legacy replay must refuse"),
                    "legacy resume successor");

            SlabRigHangingDirectState.ClearProgress clear =
                    SlabRigHangingDirectState.ClearProgress.begin(head);
            SlabRigHangingDirectState.State clearing = head.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_ENTITIES,
                    head.nextCaseOrdinal(), head.authoredCells(), head.authoredAttachments(),
                    head.cases(), head.entities(), head.scheduler(), clear, head.artifacts(),
                    "legacy exact clear entry");
            store.append(head, clearing);
            String clearBytes = SlabRigHangingDirectState.canonicalTsv(clearing);
            if (!clearBytes.startsWith("schema\tslabbed-rig-hanging-direct-state-v1\n")
                    || clearBytes.contains("\ncase_count\t")
                    || store.reconstructLegacy(owner).latestOrNull().phase()
                    != SlabRigHangingDirectState.Phase.CLEARING_ENTITIES) {
                throw helper.assertionException(
                        "legacy clear successor changed v1 bytes or failed reconstruction");
            }

            SlabRigHangingDirectState.ClearProgress entitiesDone =
                    new SlabRigHangingDirectState.ClearProgress(true,
                            clear.requestedEntities(), clear.requestedEntities().size(),
                            List.of(), clear.requestedEntities(), List.of(),
                            clear.requestedAttachments(), 0, List.of(), List.of(), List.of(),
                            clear.requestedCells(), 0, List.of(), List.of(), List.of());
            SlabRigHangingDirectState.State entityReceipts = clearing.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_ENTITIES,
                    clearing.nextCaseOrdinal(), clearing.authoredCells(),
                    clearing.authoredAttachments(), clearing.cases(), clearing.entities(),
                    clearing.scheduler(), entitiesDone, clearing.artifacts(),
                    "legacy historical entity receipts");
            SlabRigHangingDirectState.State clearingAttachments = entityReceipts.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_ATTACHMENTS,
                    entityReceipts.nextCaseOrdinal(), entityReceipts.authoredCells(),
                    entityReceipts.authoredAttachments(), entityReceipts.cases(),
                    entityReceipts.entities(), entityReceipts.scheduler(), entitiesDone,
                    entityReceipts.artifacts(), "legacy historical attachment clear");
            SlabRigHangingDirectState.ClearProgress attachmentsDone =
                    new SlabRigHangingDirectState.ClearProgress(true,
                            clear.requestedEntities(), clear.requestedEntities().size(),
                            List.of(), clear.requestedEntities(), List.of(),
                            clear.requestedAttachments(), clear.requestedAttachments().size(),
                            List.of(), clear.requestedAttachments(), List.of(),
                            clear.requestedCells(), 0, List.of(), List.of(), List.of());
            SlabRigHangingDirectState.State attachmentReceipts = clearingAttachments.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_ATTACHMENTS,
                    clearingAttachments.nextCaseOrdinal(), clearingAttachments.authoredCells(),
                    clearingAttachments.authoredAttachments(), clearingAttachments.cases(),
                    clearingAttachments.entities(), clearingAttachments.scheduler(), attachmentsDone,
                    clearingAttachments.artifacts(), "legacy historical attachment receipts");
            SlabRigHangingDirectState.State clearingCells = attachmentReceipts.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_CELLS,
                    attachmentReceipts.nextCaseOrdinal(), attachmentReceipts.authoredCells(),
                    attachmentReceipts.authoredAttachments(), attachmentReceipts.cases(),
                    attachmentReceipts.entities(), attachmentReceipts.scheduler(), attachmentsDone,
                    attachmentReceipts.artifacts(), "legacy historical cell clear");
            SlabRigHangingDirectState.ClearProgress cellsDone =
                    new SlabRigHangingDirectState.ClearProgress(true,
                            clear.requestedEntities(), clear.requestedEntities().size(),
                            List.of(), clear.requestedEntities(), List.of(),
                            clear.requestedAttachments(), clear.requestedAttachments().size(),
                            List.of(), clear.requestedAttachments(), List.of(),
                            clear.requestedCells(), clear.requestedCells().size(),
                            List.of(), clear.requestedCells(), List.of());
            SlabRigHangingDirectState.ArtifactLinks clearedArtifacts =
                    new SlabRigHangingDirectState.ArtifactLinks(
                            clearingCells.artifacts().planned(),
                            clearingCells.artifacts().immediate(),
                            clearingCells.artifacts().finalArtifact(),
                            sha("legacy historical cleared artifact"));
            SlabRigHangingDirectState.State cleared = clearingCells.successor(
                    SlabRigHangingDirectState.Phase.CLEARED,
                    clearingCells.nextCaseOrdinal(), clearingCells.authoredCells(),
                    clearingCells.authoredAttachments(), clearingCells.cases(),
                    clearingCells.entities(), clearingCells.scheduler(), cellsDone,
                    clearedArtifacts, "legacy historical cleared");
            SlabRigHangingDirectState.RunIdentity oldRun = cleared.run();
            SlabRigHangingDirectState.RunIdentity nextOldRun =
                    new SlabRigHangingDirectState.RunIdentity(
                            sha("legacy historical second run"),
                            UUID.nameUUIDFromBytes("legacy-historical-second-nonce"
                                    .getBytes(StandardCharsets.UTF_8)),
                            oldRun.buildGitSha(), oldRun.runtimeContentSha256(),
                            oldRun.minecraftVersion(), oldRun.rig3aCatalogHash(),
                            oldRun.topologyCatalogHash(), oldRun.rig3b1ExecutionIdentity(),
                            oldRun.paintingRegistryHash(), oldRun.universeHash(), oldRun.planHash(),
                            oldRun.semanticPageId(), oldRun.routeIndex(), oldRun.topologyIndex(),
                            oldRun.selectorPage(), oldRun.caseCount(), oldRun.frozenDyEnabled(),
                            oldRun.base(), oldRun.facing());
            List<SlabRigHangingDirectState.CaseState> pending = cleared.cases().stream()
                    .map(entry -> new SlabRigHangingDirectState.CaseState(
                            entry.ordinal(), entry.attemptId(), entry.selectorId(),
                            entry.componentFingerprint(), SlabRigHangingDirectState.CasePhase.PENDING,
                            SlabRigHangingDirectState.CaseOutcome.NONE,
                            SlabRigHangingDirectState.NO_VALUE)).toList();
            SlabRigHangingDirectState.State historicalSeed = new SlabRigHangingDirectState.State(
                    SlabRigHangingDirectState.Format.LEGACY_V1, "PENDING",
                    cleared.sequence() + 1, cleared.stateHash(), cleared.owner(), nextOldRun,
                    SlabRigHangingDirectState.Phase.PLANNED, 0, cleared.reservedCells(),
                    cleared.plannedAuthoredCells(), List.of(), List.of(), pending, List.of(),
                    SlabRigHangingDirectState.Scheduler.inactive(),
                    SlabRigHangingDirectState.ClearProgress.none(),
                    SlabRigHangingDirectState.ArtifactLinks.planned(
                            sha("legacy historical second planned artifact")),
                    "legacy historical second planned");
            SlabRigHangingDirectState.State historicalReplanned =
                    historicalStateWithComputedHash(historicalSeed);
            SlabRigHangingDirectState.validateTransition(cleared, historicalReplanned);
            expectRuntimeFailure(helper, () -> SlabRigHangingDirectState
                            .validateAppendTransition(cleared, historicalReplanned),
                    "new append of historical legacy CLEARED-to-PLANNED transition");

            rejectedGenesisRoot = Files.createTempDirectory("slabbed-legacy-v1-genesis-");
            SlabRigHangingDirectStateStore rejectedGenesis =
                    new SlabRigHangingDirectStateStore(rejectedGenesisRoot);
            SlabRigHangingDirectState.State genesis = legacy.states().getFirst();
            expectIoFailure(helper, () -> rejectedGenesis.append(null, genesis),
                    "new legacy-v1 genesis publication");
            if (Files.exists(rejectedGenesis.statePath(genesis))) {
                throw helper.assertionException("refused legacy genesis still published bytes");
            }

            emptyCrashRoot = Files.createTempDirectory("slabbed-empty-ledger-crash-");
            String emptyKey = sha("crash-left-empty-owner-directory");
            Files.createDirectories(emptyCrashRoot.resolve("ledgers").resolve(emptyKey));
            List<SlabRigHangingDirectStateStore.Reconstruction> inert =
                    new SlabRigHangingDirectStateStore(emptyCrashRoot).reconstructAll();
            if (inert.size() != 1 || !inert.getFirst().ownerKey().equals(emptyKey)
                    || inert.getFirst().owner() != null || inert.getFirst().format() != null
                    || !inert.getFirst().isEmpty()) {
                throw helper.assertionException(
                        "crash-left empty owner directory was not reconstructed as inert");
            }
        } catch (IOException | InterruptedException failure) {
            throw helper.assertionException("legacy-v1 dual-store proof failed: " + failure);
        } finally {
            deleteTree(root);
            deleteTree(rejectedGenesisRoot);
            deleteTree(emptyCrashRoot);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directStateCanonicalRoundTripAcceptsExactDevSentinel(GameTestHelper helper) {
        SlabRigHangingDirectState.State state = initial(sha("planned"), owner(1));
        String canonical = SlabRigHangingDirectState.canonicalTsv(state);
        SlabRigHangingDirectState.State parsed = SlabRigHangingDirectState.parse(canonical);
        if (!state.equals(parsed) || !"unknown".equals(parsed.run().buildGitSha())
                || !canonical.equals(SlabRigHangingDirectState.canonicalTsv(parsed))) {
            throw helper.assertionException("canonical state/dev provenance did not round-trip exactly");
        }
        expectRuntimeFailure(helper,
                () -> SlabRigHangingDirectState.parse(new byte[]{(byte) 0xc3, (byte) 0x28}),
                "malformed UTF-8 ledger bytes");
        String forgedGenesisBody = canonical
                .replaceFirst("state_hash\\t[0-9a-f]{64}\\n", "")
                .replace("phase\tPLANNED\n", "phase\tQUARANTINED\n");
        String forgedGenesis = forgedGenesisBody.replaceFirst("\\n", "\nstate_hash\t"
                + sha(forgedGenesisBody) + "\n");
        expectRuntimeFailure(helper, () -> SlabRigHangingDirectState.parse(forgedGenesis),
                "non-PLANNED sequence-zero genesis");
        SlabRigHangingDirectEvidence.CellEvidence cellEvidence = syntheticCell(
                new SlabRigHangingDirectState.Position(22, 64, -7));
        byte[] cellCanonical = SlabRigHangingDirectEvidence.cellIdentityCanonical(cellEvidence)
                .getBytes(StandardCharsets.UTF_8);
        String cellHash = SlabRigHangingDirectEvidence.cellIdentityFingerprint(cellEvidence);
        String attachmentHash =
                SlabRigHangingDirectEvidence.attachmentIdentityFingerprint(cellEvidence);
        SlabRigHangingDirectEvidence.verifyCellAndAttachmentArtifact(cellEvidence.pos(),
                cellHash, attachmentHash, cellCanonical);
        expectRuntimeFailure(helper, () -> SlabRigHangingDirectEvidence
                        .verifyCellAndAttachmentArtifact(cellEvidence.pos().east(), cellHash,
                                attachmentHash, cellCanonical),
                "full cell artifact cannot derive an attachment for the wrong position");
        expectRuntimeFailure(helper, () -> SlabRigHangingDirectEvidence
                        .verifyCellAndAttachmentArtifact(cellEvidence.pos(), cellHash,
                                sha("wrong attachment"), cellCanonical),
                "full cell artifact cannot derive the wrong attachment hash");
        byte[] malformedCell = new byte[]{(byte) 0xc3, (byte) 0x28};
        expectRuntimeFailure(helper, () -> SlabRigHangingDirectEvidence
                        .verifyCellAndAttachmentArtifact(cellEvidence.pos(),
                                SlabRigHangingDirectState.sha256(malformedCell), attachmentHash,
                                malformedCell),
                "full cell artifact requires strict UTF-8");
        SlabRigHangingDirectState.CaseState exactCase = state.cases().getFirst();
        expectRuntimeFailure(helper, () -> new SlabRigHangingDirectState.CaseState(
                        exactCase.ordinal(), exactCase.attemptId(), exactCase.selectorId(),
                        "not-a-hash\twith-a-column-break", exactCase.phase(), exactCase.outcome(),
                        exactCase.immediateObservationId()),
                "non-canonical case component fingerprint");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directStoreAppendsChainAndFreshProcessIgnoresOnlyTempRemainder(GameTestHelper helper) {
        Path root = null;
        try {
            root = Files.createTempDirectory("slabbed-direct-store-chain-");
            SlabRigHangingDirectStateStore store = new SlabRigHangingDirectStateStore(root);
            String planned = store.writeArtifact("planned-chain").hash();
            List<SlabRigHangingDirectStateStore.WrittenArtifact> batch = store.writeArtifacts(
                    List.of("batch-artifact-a", "batch-artifact-b", "batch-artifact-a"));
            if (batch.size() != 3 || !batch.get(0).hash().equals(batch.get(2).hash())
                    || batch.get(0).hash().equals(batch.get(1).hash())
                    || !new String(store.readArtifact(batch.get(1).hash()), StandardCharsets.UTF_8)
                    .equals("batch-artifact-b")) {
                throw helper.assertionException("batched artifact publication changed order/identity");
            }
            SlabRigHangingDirectState.State first = initial(planned, owner(2));
            store.append(null, first);
            SlabRigHangingDirectState.State authoring = fixtureAuthoring(first);
            store.append(first, authoring);

            Path ledger = store.ledgerPath(first.owner());
            Path temp = ledger.resolve("." + store.statePath(authoring).getFileName()
                    + ".tmp-00000000-0000-0000-0000-000000000002");
            Files.writeString(temp, "orphaned producer temp", StandardCharsets.UTF_8);

            // New object is the process-reconstruction proof; no prior State/Store cache is reused.
            SlabRigHangingDirectStateStore fresh = new SlabRigHangingDirectStateStore(root);
            SlabRigHangingDirectStateStore.Reconstruction rebuilt = fresh.reconstruct(first.owner());
            if (rebuilt.states().size() != 2 || rebuilt.ignoredTemporaryFiles().size() != 1
                    || !rebuilt.latestOrNull().stateHash().equals(authoring.stateHash())) {
                throw helper.assertionException("fresh-process ledger reconstruction lost chain/temp boundary");
            }
            SlabRigHangingDirectState.State quarantined = authoring.successor(
                    SlabRigHangingDirectState.Phase.QUARANTINED,
                    authoring.nextCaseOrdinal(), authoring.authoredCells(),
                    authoring.authoredAttachments(), authoring.cases(), authoring.entities(),
                    authoring.scheduler(), authoring.clear(), authoring.artifacts(),
                    "independent verifier appended-tail proof");
            store.append(authoring, quarantined);
            long beforeTailReuse = fresh.verifiedPrefixReuseCount();
            List<SlabRigHangingDirectStateStore.Reconstruction> extended = fresh.reconstructAll();
            if (extended.size() != 1 || !quarantined.equals(extended.getFirst().latestOrNull())
                    || fresh.verifiedPrefixReuseCount() != beforeTailReuse + 1) {
                throw helper.assertionException(
                        "independent reconstructAll did not verify cached prefix plus appended tail");
            }

            Path emptyPrefixRoot = root.resolve("empty-prefix-store");
            SlabRigHangingDirectStateStore writer =
                    new SlabRigHangingDirectStateStore(emptyPrefixRoot);
            SlabRigHangingDirectStateStore emptyPrefixVerifier =
                    new SlabRigHangingDirectStateStore(emptyPrefixRoot);
            SlabRigHangingDirectState.Owner emptyPrefixOwner = owner(33);
            if (!emptyPrefixVerifier.reconstruct(emptyPrefixOwner).isEmpty()) {
                throw helper.assertionException("empty-prefix verifier premise was not empty");
            }
            SlabRigHangingDirectState.State genesis = initial(
                    writer.writeArtifact("empty-prefix-genesis").hash(), emptyPrefixOwner);
            writer.append(null, genesis);
            long beforeGenesisExtension = emptyPrefixVerifier.verifiedPrefixReuseCount();
            List<SlabRigHangingDirectStateStore.Reconstruction> genesisExtension =
                    emptyPrefixVerifier.reconstructAll();
            if (genesisExtension.size() != 1
                    || !genesis.equals(genesisExtension.getFirst().latestOrNull())
                    || emptyPrefixVerifier.verifiedPrefixReuseCount()
                    != beforeGenesisExtension + 1) {
                throw helper.assertionException(
                        "empty cached prefix could not extend through an independently appended genesis");
            }

            Path world = Files.createDirectory(root.resolve("world"));
            String created = SlabRigHangingDirectStateStore.createWorldKey(world);
            if (!created.equals(SlabRigHangingDirectStateStore.readWorldKey(world))) {
                throw helper.assertionException("strict create/read world identity disagreed");
            }

            SlabRigHangingDirectStateStore malformedStore =
                    new SlabRigHangingDirectStateStore(root.resolve("malformed-store"));
            String malformedPlanned = malformedStore.writeArtifact(
                    "planned-malformed-unicode").hash();
            List<SlabRigHangingDirectState.Position> malformedCells = List.of(
                    new SlabRigHangingDirectState.Position(22, 64, 22));
            SlabRigHangingDirectState.State malformed = SlabRigHangingDirectState.State.initial(
                    owner(22), run(22), malformedCells, malformedCells, pendingCases(),
                    malformedPlanned, "unpaired-surrogate-\ud800");
            try {
                malformedStore.append(null, malformed);
                throw helper.assertionException(
                        "candidate that changes under UTF-8 encoding unexpectedly published");
            } catch (IOException expected) {
                if (!expected.getMessage().contains("semantic readback")) {
                    throw helper.assertionException(
                            "malformed candidate failed for the wrong reason: " + expected);
                }
            }
            if (Files.exists(malformedStore.statePath(malformed))
                    || malformedStore.reconstruct(malformed.owner()).latestOrNull() != null) {
                throw helper.assertionException(
                        "semantic readback failure published an authoritative sequence");
            }
        } catch (IOException failure) {
            throw helper.assertionException("direct store chain proof failed: " + failure);
        } finally {
            deleteTree(root);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directStoreSuccessfulExistingPublicationRetriesRepairDirectoryDurability(
            GameTestHelper helper) {
        Path root = null;
        try {
            root = Files.createTempDirectory("slabbed-direct-store-retry-sync-");
            SlabRigHangingDirectStateStore store = new SlabRigHangingDirectStateStore(root);

            SlabRigHangingDirectStateStore.WrittenArtifact single =
                    store.writeArtifact("retry-single");
            long afterSingleCreate = store.directorySyncCount();
            store.writeArtifact("retry-single");
            if (store.directorySyncCount() != afterSingleCreate + 1) {
                throw helper.assertionException(
                        "existing single-artifact retry returned without repairing directory durability");
            }

            store.writeArtifacts(List.of("retry-batch-a", "retry-batch-b", "retry-batch-a"));
            long afterBatchCreate = store.directorySyncCount();
            store.writeArtifacts(List.of("retry-batch-a", "retry-batch-b", "retry-batch-a"));
            if (store.directorySyncCount() != afterBatchCreate + 1) {
                throw helper.assertionException(
                        "all-existing artifact-batch retry returned without repairing directory durability");
            }

            SlabRigHangingDirectState.State state = initial(single.hash(), owner(30));
            store.append(null, state);
            long afterStateCreate = store.directorySyncCount();
            store.append(null, state);
            if (store.directorySyncCount() != afterStateCreate + 1) {
                throw helper.assertionException(
                        "existing state retry returned without repairing ledger directory durability");
            }
        } catch (IOException failure) {
            throw helper.assertionException("existing-publication retry proof failed: " + failure);
        } finally {
            deleteTree(root);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directStoreCollisionTamperSymlinkAndVerifiedPrefixFailClosed(GameTestHelper helper) {
        Path root = null;
        Path symlinkRoot = null;
        try {
            root = Files.createTempDirectory("slabbed-direct-store-corrupt-");
            SlabRigHangingDirectStateStore store = new SlabRigHangingDirectStateStore(root);
            byte[] intended = "artifact-collision".getBytes(StandardCharsets.UTF_8);
            String intendedHash = SlabRigHangingDirectState.sha256(intended);
            Files.createDirectories(root.resolve("artifacts"));
            Files.writeString(store.artifactPath(intendedHash), "wrong bytes", StandardCharsets.UTF_8);
            expectIoFailure(helper, () -> store.writeArtifact(intended), "artifact collision");
            Files.delete(store.artifactPath(intendedHash));

            String planned = store.writeArtifact("planned-corrupt").hash();
            SlabRigHangingDirectState.State first = initial(planned, owner(3));
            SlabRigHangingDirectState.State second = fixtureAuthoring(first);
            store.append(null, first);
            store.append(first, second);
            Files.writeString(store.statePath(second), "tampered authoritative tail", StandardCharsets.UTF_8);
            try {
                new SlabRigHangingDirectStateStore(root).reconstruct(first.owner());
                throw helper.assertionException("tampered authoritative tail was accepted");
            } catch (SlabRigHangingDirectStateStore.CorruptLedgerException expected) {
                if (expected.verifiedPrefix().states().size() != 1
                        || !expected.verifiedPrefix().latestOrNull().equals(first)) {
                    throw helper.assertionException("corruption did not preserve exact verified prefix");
                }
            }

            SlabRigHangingDirectStateStore cachedStore =
                    new SlabRigHangingDirectStateStore(root.resolve("cached-artifact-tamper"));
            String cachedPlanned = cachedStore.writeArtifact("cached-planned").hash();
            SlabRigHangingDirectState.State cachedFirst = initial(cachedPlanned, owner(31));
            SlabRigHangingDirectState.State cachedSecond = fixtureAuthoring(cachedFirst);
            cachedStore.append(null, cachedFirst);
            cachedStore.append(cachedFirst, cachedSecond);
            SlabRigHangingDirectState.State cachedCandidate = cachedSecond.successor(
                    SlabRigHangingDirectState.Phase.QUARANTINED,
                    cachedSecond.nextCaseOrdinal(), cachedSecond.authoredCells(),
                    cachedSecond.authoredAttachments(), cachedSecond.cases(), cachedSecond.entities(),
                    cachedSecond.scheduler(), cachedSecond.clear(), cachedSecond.artifacts(),
                    "cached artifact tamper must block append");
            Files.delete(cachedStore.artifactPath(cachedPlanned));
            expectIoFailure(helper, () -> cachedStore.append(cachedSecond, cachedCandidate),
                    "cached linked-artifact deletion");
            if (Files.exists(cachedStore.statePath(cachedCandidate))) {
                throw helper.assertionException(
                        "cached linked-artifact deletion still published a new state");
            }

            symlinkRoot = Files.createTempDirectory("slabbed-direct-store-symlink-");
            Path outside = Files.createDirectory(symlinkRoot.resolve("outside"));
            Path escapedRoot = Files.createDirectory(symlinkRoot.resolve("store"));
            Files.createSymbolicLink(escapedRoot.resolve("artifacts"), outside);
            SlabRigHangingDirectStateStore escaped = new SlabRigHangingDirectStateStore(escapedRoot);
            expectIoFailure(helper, () -> escaped.writeArtifact("must-refuse"), "symlinked artifact directory");
        } catch (IOException failure) {
            throw helper.assertionException("collision/tamper/symlink setup failed: " + failure);
        } finally {
            deleteTree(root);
            deleteTree(symlinkRoot);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directStoreCacheRejectsSameSizeRestoredMtimeArtifactTamper(GameTestHelper helper) {
        Path root = null;
        try {
            root = Files.createTempDirectory("slabbed-direct-store-cache-ctime-");
            SlabRigHangingDirectStateStore store = new SlabRigHangingDirectStateStore(root);
            String planned = store.writeArtifact("cached-planned-same-size").hash();
            SlabRigHangingDirectState.State first = initial(planned, owner(32));
            SlabRigHangingDirectState.State second = fixtureAuthoring(first);
            store.append(null, first);
            store.append(first, second);

            long beforeReconstructAllReuse = store.verifiedPrefixReuseCount();
            List<SlabRigHangingDirectStateStore.Reconstruction> cachedAll = store.reconstructAll();
            if (cachedAll.size() != 1 || !second.equals(cachedAll.getFirst().latestOrNull())
                    || store.verifiedPrefixReuseCount() != beforeReconstructAllReuse + 1) {
                throw helper.assertionException(
                        "reconstructAll did not reuse the exact verified owner cache");
            }

            Path artifact = store.artifactPath(planned);
            BasicFileAttributes before = Files.readAttributes(artifact, BasicFileAttributes.class);
            byte[] tampered = Files.readAllBytes(artifact);
            tampered[tampered.length / 2] ^= 0x01;
            Files.write(artifact, tampered);
            Files.setLastModifiedTime(artifact, before.lastModifiedTime());
            BasicFileAttributes after = Files.readAttributes(artifact, BasicFileAttributes.class);
            if (!String.valueOf(before.fileKey()).equals(String.valueOf(after.fileKey()))
                    || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
                throw helper.assertionException(
                        "test filesystem could not preserve the legacy fileKey/size/mtime cache stamp");
            }

            SlabRigHangingDirectState.State candidate = second.successor(
                    SlabRigHangingDirectState.Phase.QUARANTINED,
                    second.nextCaseOrdinal(), second.authoredCells(), second.authoredAttachments(),
                    second.cases(), second.entities(), second.scheduler(), second.clear(),
                    second.artifacts(), "same-size restored-mtime tamper must block append");
            expectIoFailure(helper, store::reconstructAll,
                    "same-size restored-mtime cached reconstructAll tamper");
            expectIoFailure(helper, () -> store.append(second, candidate),
                    "same-size restored-mtime cached artifact tamper");
            if (Files.exists(store.statePath(candidate))) {
                throw helper.assertionException(
                        "same-size restored-mtime tamper still published a new state");
            }
        } catch (IOException failure) {
            throw helper.assertionException("same-size cache tamper proof failed: " + failure);
        } finally {
            deleteTree(root);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directStateRejectsReplayDuplicateOwnershipAndForeignClearAuthority(GameTestHelper helper) {
        SlabRigHangingDirectState.State ready = fixtureReady(initial(sha("planned-safety"), owner(4)));
        SlabRigHangingDirectState.State inFlight = beginCase(ready, 0);
        List<SlabRigHangingDirectState.CaseState> missingOwnership = new ArrayList<>(inFlight.cases());
        missingOwnership.set(0, missingOwnership.getFirst().immediate(
                SlabRigHangingDirectState.CaseOutcome.PLACED, sha("missing-ownership")));
        expectRuntimeFailure(helper, () -> inFlight.successor(
                        SlabRigHangingDirectState.Phase.IMMEDIATE_PARTIAL, 1,
                        inFlight.authoredCells(), inFlight.authoredAttachments(), missingOwnership,
                        inFlight.entities(), inFlight.scheduler(), inFlight.clear(),
                        inFlight.artifacts(), "must reject missing ownership"),
                "immediate case cannot omit its painting preclaim/load confirmation");
        List<SlabRigHangingDirectState.CaseState> lawfulRefusal = new ArrayList<>(inFlight.cases());
        lawfulRefusal.set(0, lawfulRefusal.getFirst().immediate(
                SlabRigHangingDirectState.CaseOutcome.VANILLA_REFUSAL,
                sha("vanilla-refusal-observation")));
        SlabRigHangingDirectState.State refused = inFlight.successor(
                SlabRigHangingDirectState.Phase.IMMEDIATE_PARTIAL, 1,
                inFlight.authoredCells(), inFlight.authoredAttachments(), lawfulRefusal,
                inFlight.entities(), inFlight.scheduler(), inFlight.clear(),
                inFlight.artifacts(), "lawful zero-UUID vanilla refusal");
        if (refused.cases().getFirst().outcome()
                != SlabRigHangingDirectState.CaseOutcome.VANILLA_REFUSAL) {
            throw helper.assertionException("lawful vanilla refusal outcome was not retained");
        }
        SlabRigHangingDirectState.EntityOwnership painting = paintingPreclaim(inFlight, 0);
        expectRuntimeFailure(helper, () -> new SlabRigHangingDirectState.EntityOwnership(
                        painting.uuid(), painting.role(), painting.expectedType(), painting.caseOrdinal(),
                        painting.attemptId(), painting.sourcePaintingUuid(),
                        SlabRigHangingDirectState.Acquisition.PRECLAIMED, painting.decision(),
                        SlabRigHangingDirectState.EntityDisposition.IN_WORLD, painting.fingerprint(),
                        painting.evidenceArtifact(), painting.position(), painting.aabb(),
                        painting.transferTargetUuid(), painting.transferDetail()),
                "painting preclaim cannot skip load confirmation");
        SlabRigHangingDirectState.State preclaimed = inFlight.withPreclaimedEntity(painting, "preclaim");
        expectRuntimeFailure(helper, () -> preclaimed.successor(
                        SlabRigHangingDirectState.Phase.IMMEDIATE_PARTIAL, 1,
                        preclaimed.authoredCells(), preclaimed.authoredAttachments(), missingOwnership,
                        preclaimed.entities(), preclaimed.scheduler(), preclaimed.clear(),
                        preclaimed.artifacts(), "must reject unconfirmed ownership"),
                "immediate case cannot retain a PREINSERTION painting");
        expectRuntimeFailure(helper,
                () -> preclaimed.withPreclaimedEntity(painting, "duplicate"),
                "duplicate entity UUID");

        SlabRigHangingDirectState.State quarantined = inFlight.successor(
                SlabRigHangingDirectState.Phase.QUARANTINED, 0,
                inFlight.authoredCells(), inFlight.authoredAttachments(), inFlight.cases(),
                inFlight.entities(), inFlight.scheduler(), inFlight.clear(), inFlight.artifacts(),
                "clear-only quarantine");
        expectRuntimeFailure(helper, () -> quarantined.withPreclaimedEntity(painting,
                        "foreign UUID adoption after reconstruction"),
                "quarantine cannot adopt a painting UUID");
        List<SlabRigHangingDirectState.CaseState> quarantinedReplay =
                new ArrayList<>(quarantined.cases());
        quarantinedReplay.set(0, quarantinedReplay.getFirst().immediate(
                SlabRigHangingDirectState.CaseOutcome.VANILLA_REFUSAL,
                sha("quarantined replay")));
        expectRuntimeFailure(helper, () -> quarantined.successor(
                        SlabRigHangingDirectState.Phase.QUARANTINED, 1,
                        quarantined.authoredCells(), quarantined.authoredAttachments(),
                        quarantinedReplay, quarantined.entities(), quarantined.scheduler(),
                        quarantined.clear(), quarantined.artifacts(), "forbidden replay"),
                "quarantine cannot advance a case");
        expectRuntimeFailure(helper, () -> quarantined.successor(
                        SlabRigHangingDirectState.Phase.CLEARING_ENTITIES, 1,
                        quarantined.authoredCells(), quarantined.authoredAttachments(),
                        quarantinedReplay, quarantined.entities(), quarantined.scheduler(),
                        SlabRigHangingDirectState.ClearProgress.begin(quarantined),
                        quarantined.artifacts(), "forbidden replay hidden in clear entry"),
                "quarantine clear entry cannot replay or advance a case");
        expectRuntimeFailure(helper, () -> inFlight.successor(
                        SlabRigHangingDirectState.Phase.CLEARING_ENTITIES, 1,
                        inFlight.authoredCells(), inFlight.authoredAttachments(),
                        quarantinedReplay, inFlight.entities(), inFlight.scheduler(),
                        SlabRigHangingDirectState.ClearProgress.begin(inFlight),
                        inFlight.artifacts(), "forbidden in-flight advancement hidden in clear entry"),
                "active clear entry cannot replay or advance a case");

        List<SlabRigHangingDirectState.CaseState> replayed = new ArrayList<>(preclaimed.cases());
        replayed.set(0, pendingCase(0));
        expectRuntimeFailure(helper, () -> preclaimed.successor(
                SlabRigHangingDirectState.Phase.CASE_IN_FLIGHT, 0,
                preclaimed.authoredCells(), preclaimed.authoredAttachments(), replayed,
                preclaimed.entities(), preclaimed.scheduler(), preclaimed.clear(),
                preclaimed.artifacts(), "replay"), "in-flight replay");

        UUID foreign = UUID.nameUUIDFromBytes("foreign-clear".getBytes(StandardCharsets.UTF_8));
        SlabRigHangingDirectState.ClearProgress bad = new SlabRigHangingDirectState.ClearProgress(true,
                List.of(painting.uuid(), foreign), 0, List.of(), List.of(), List.of(),
                preclaimed.authoredAttachments().stream()
                        .map(SlabRigHangingDirectState.AttachmentOwnership::pos).toList(),
                0, List.of(), List.of(), List.of(),
                List.of(new SlabRigHangingDirectState.Position(999, 99, 999)),
                0, List.of(), List.of(), List.of());
        expectRuntimeFailure(helper, () -> preclaimed.successor(
                SlabRigHangingDirectState.Phase.CLEARING_ENTITIES, 0,
                preclaimed.authoredCells(), preclaimed.authoredAttachments(), preclaimed.cases(),
                preclaimed.entities(), preclaimed.scheduler(), bad, preclaimed.artifacts(),
                "foreign clear"), "foreign clear authority");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directFixtureAuthorityEmptyClearAndTopDownOrderAreExact(GameTestHelper helper) {
        SlabRigHangingDirectState.State initial = initial(sha("planned-fixture"), owner(5));
        SlabRigHangingDirectState.State authoring = fixtureAuthoring(initial);
        List<SlabRigHangingDirectState.CellOwnership> cells = confirmedCells(initial);
        SlabRigHangingDirectState.Position manufacturedPos = initial.plannedAuthoredCells().getFirst();
        SlabRigHangingDirectState.CellOwnership manufacturedCell =
                new SlabRigHangingDirectState.CellOwnership(manufacturedPos, sha("manufactured cell"));
        SlabRigHangingDirectState.AttachmentOwnership manufacturedAttachment =
                new SlabRigHangingDirectState.AttachmentOwnership(
                        manufacturedPos, sha("manufactured attachment"));
        expectRuntimeFailure(helper, () -> initial.successor(
                        SlabRigHangingDirectState.Phase.QUARANTINED, 0,
                        List.of(manufacturedCell), List.of(manufacturedAttachment), initial.cases(),
                        List.of(), initial.scheduler(), initial.clear(), initial.artifacts(),
                        "manufactured post-plan clear authority"),
                "non-authoring phase cannot promote a reservation into clear authority");
        SlabRigHangingDirectState.AttachmentOwnership foreignAttachment =
                new SlabRigHangingDirectState.AttachmentOwnership(
                        new SlabRigHangingDirectState.Position(100, 100, 100), sha("foreign attachment"));
        expectRuntimeFailure(helper, () -> authoring.successor(
                SlabRigHangingDirectState.Phase.FIXTURE_READY, 0, cells,
                List.of(foreignAttachment), authoring.cases(), List.of(),
                authoring.scheduler(), authoring.clear(), authoring.artifacts(), "foreign attachment"),
                "unplanned attachment");
        expectRuntimeFailure(helper, () -> authoring.successor(
                        SlabRigHangingDirectState.Phase.FIXTURE_READY, 0, cells,
                        List.of(), authoring.cases(), List.of(), authoring.scheduler(),
                        authoring.clear(), authoring.artifacts(), "missing attachments"),
                "confirmed cells cannot omit same-position attachment receipts");

        SlabRigHangingDirectState.ClearProgress empty = SlabRigHangingDirectState.ClearProgress.begin(initial);
        SlabRigHangingDirectState.State clearingEntities = initial.successor(
                SlabRigHangingDirectState.Phase.CLEARING_ENTITIES, 0, List.of(), List.of(),
                initial.cases(), List.of(), initial.scheduler(), empty, initial.artifacts(), "empty entities");
        List<SlabRigHangingDirectState.CaseState> clearReplay =
                new ArrayList<>(clearingEntities.cases());
        clearReplay.set(0, clearReplay.getFirst().inFlight());
        expectRuntimeFailure(helper, () -> clearingEntities.successor(
                        SlabRigHangingDirectState.Phase.CLEARING_ENTITIES, 0, List.of(), List.of(),
                        clearReplay, List.of(), clearingEntities.scheduler(), empty,
                        clearingEntities.artifacts(), "forbidden execution during clear"),
                "clear phase cannot mutate execution evidence");
        SlabRigHangingDirectState.State clearingAttachments = clearingEntities.successor(
                SlabRigHangingDirectState.Phase.CLEARING_ATTACHMENTS, 0, List.of(), List.of(),
                clearingEntities.cases(), List.of(), clearingEntities.scheduler(), empty,
                clearingEntities.artifacts(), "empty attachments");
        SlabRigHangingDirectState.State clearingCells = clearingAttachments.successor(
                SlabRigHangingDirectState.Phase.CLEARING_CELLS, 0, List.of(), List.of(),
                clearingAttachments.cases(), List.of(), clearingAttachments.scheduler(), empty,
                clearingAttachments.artifacts(), "empty cells");
        SlabRigHangingDirectState.ArtifactLinks clearedLinks = new SlabRigHangingDirectState.ArtifactLinks(
                initial.artifacts().planned(), NONE, NONE, sha("empty cleared"));
        SlabRigHangingDirectState.State cleared = clearingCells.successor(
                SlabRigHangingDirectState.Phase.CLEARED, 0, List.of(), List.of(),
                clearingCells.cases(), List.of(), clearingCells.scheduler(), empty,
                clearedLinks, "empty clear complete");
        if (cleared.phase() != SlabRigHangingDirectState.Phase.CLEARED) {
            throw helper.assertionException("zero-ownership PLANNED state was not safely retireable");
        }

        SlabRigHangingDirectState.State ready = fixtureReady(initial(sha("planned-order"), owner(6)));
        List<SlabRigHangingDirectState.Position> requested =
                SlabRigHangingDirectState.ClearProgress.begin(ready).requestedCells();
        if (requested.size() < 2 || requested.get(0).y() <= requested.get(1).y()) {
            throw helper.assertionException("confirmed cell clear order is not top-down");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directSchedulerResetsOnUnloadAndFinalStillCapturesCausalDrop(GameTestHelper helper) {
        SlabRigHangingDirectState.State immediate = completePage(
                fixtureReady(initial(sha("planned-delay"), owner(7))));
        List<SlabRigHangingDirectState.TickCredit> baseline = new ArrayList<>();
        List<UUID> paintings = immediate.entities().stream()
                .filter(entity -> entity.role() == SlabRigHangingDirectState.EntityRole.PAINTING)
                .map(SlabRigHangingDirectState.EntityOwnership::uuid).sorted().toList();
        for (int i = 0; i < paintings.size(); i++) {
            baseline.add(new SlabRigHangingDirectState.TickCredit(paintings.get(i), 0,
                    i != 0, 0, i == 0 ? -1 : 0));
        }
        SlabRigHangingDirectState.Scheduler armed = new SlabRigHangingDirectState.Scheduler(
                UUID.nameUUIDFromBytes("epoch-1".getBytes(StandardCharsets.UTF_8)).toString(),
                1, baseline);
        expectRuntimeFailure(helper, () -> new SlabRigHangingDirectState.TickCredit(
                        paintings.getFirst(), 0, true, 0, -1),
                "loaded painting cannot use unloaded -1 raw-tick sentinel");
        SlabRigHangingDirectState.State waiting = immediate.successor(
                SlabRigHangingDirectState.Phase.WAITING_DELAYED, 16,
                immediate.authoredCells(), immediate.authoredAttachments(), immediate.cases(),
                immediate.entities(), armed, immediate.clear(), immediate.artifacts(), "armed after reconstruction");

        List<SlabRigHangingDirectState.TickCredit> reloaded = new ArrayList<>(baseline);
        reloaded.set(0, new SlabRigHangingDirectState.TickCredit(paintings.get(0), 0, true, 0, 0));
        SlabRigHangingDirectState.State loaded = waiting.successor(
                SlabRigHangingDirectState.Phase.WAITING_DELAYED, 16,
                waiting.authoredCells(), waiting.authoredAttachments(), waiting.cases(), waiting.entities(),
                new SlabRigHangingDirectState.Scheduler(armed.processEpoch(), 1, reloaded),
                waiting.clear(), waiting.artifacts(), "reload baseline");

        List<SlabRigHangingDirectState.TickCredit> forgedGlobalCredit = new ArrayList<>(reloaded);
        SlabRigHangingDirectState.TickCredit unchangedRaw = forgedGlobalCredit.getFirst();
        forgedGlobalCredit.set(0, new SlabRigHangingDirectState.TickCredit(
                unchangedRaw.paintingUuid(), 102, true, unchangedRaw.unloadResets(),
                unchangedRaw.lastObservedEntityTick()));
        expectRuntimeFailure(helper, () -> loaded.successor(
                        SlabRigHangingDirectState.Phase.WAITING_DELAYED, 16,
                        loaded.authoredCells(), loaded.authoredAttachments(), loaded.cases(),
                        loaded.entities(), new SlabRigHangingDirectState.Scheduler(
                                armed.processEpoch(), 1, forgedGlobalCredit),
                        loaded.clear(), loaded.artifacts(), "forged global tick credit"),
                "entity credit cannot advance when raw tickCount is unchanged");

        List<SlabRigHangingDirectState.TickCredit> advanced = allCredits(reloaded, 10, true, 10);
        SlabRigHangingDirectState.State ten = loaded.successor(
                SlabRigHangingDirectState.Phase.WAITING_DELAYED, 16, loaded.authoredCells(),
                loaded.authoredAttachments(), loaded.cases(), loaded.entities(),
                new SlabRigHangingDirectState.Scheduler(armed.processEpoch(), 1, advanced),
                loaded.clear(), loaded.artifacts(), "ten entity ticks");
        List<SlabRigHangingDirectState.TickCredit> unloaded = new ArrayList<>(advanced);
        unloaded.set(0, new SlabRigHangingDirectState.TickCredit(paintings.get(0), 0, false, 1, -1));
        SlabRigHangingDirectState.State paused = ten.successor(
                SlabRigHangingDirectState.Phase.WAITING_DELAYED, 16, ten.authoredCells(),
                ten.authoredAttachments(), ten.cases(), ten.entities(),
                new SlabRigHangingDirectState.Scheduler(armed.processEpoch(), 1, unloaded),
                ten.clear(), ten.artifacts(), "unload reset");
        List<SlabRigHangingDirectState.TickCredit> duplicateReset = new ArrayList<>(unloaded);
        duplicateReset.set(0, new SlabRigHangingDirectState.TickCredit(
                paintings.get(0), 0, false, 2, -1));
        expectRuntimeFailure(helper, () -> paused.successor(
                        SlabRigHangingDirectState.Phase.WAITING_DELAYED, 16,
                        paused.authoredCells(), paused.authoredAttachments(), paused.cases(),
                        paused.entities(), new SlabRigHangingDirectState.Scheduler(
                                armed.processEpoch(), 1, duplicateReset),
                        paused.clear(), paused.artifacts(), "duplicate unload reset"),
                "already-paused credit cannot gain a duplicate unload reset");
        List<SlabRigHangingDirectState.TickCredit> twiceUnloaded = new ArrayList<>(unloaded);
        twiceUnloaded.set(1, new SlabRigHangingDirectState.TickCredit(
                paintings.get(1), 0, false, 1, -1));
        SlabRigHangingDirectState.State twoPaused = paused.successor(
                SlabRigHangingDirectState.Phase.WAITING_DELAYED, 16, paused.authoredCells(),
                paused.authoredAttachments(), paused.cases(), paused.entities(),
                new SlabRigHangingDirectState.Scheduler(armed.processEpoch(), 1, twiceUnloaded),
                paused.clear(), paused.artifacts(), "second unload preserves first paused row");
        if (twoPaused.scheduler().credits().stream().filter(credit -> !credit.loaded()).count() != 2) {
            throw helper.assertionException("sequential unload did not preserve two paused credits");
        }
        List<SlabRigHangingDirectState.TickCredit> restarted = new ArrayList<>(unloaded);
        restarted.set(0, new SlabRigHangingDirectState.TickCredit(paintings.get(0), 0, true, 1, 0));
        SlabRigHangingDirectState.State restartedState = paused.successor(
                SlabRigHangingDirectState.Phase.WAITING_DELAYED, 16, paused.authoredCells(),
                paused.authoredAttachments(), paused.cases(), paused.entities(),
                new SlabRigHangingDirectState.Scheduler(armed.processEpoch(), 1, restarted),
                paused.clear(), paused.artifacts(), "raw entity tick restarts at zero");

        List<SlabRigHangingDirectState.TickCredit> satisfied = allCredits(restarted, 102, true, 102);
        SlabRigHangingDirectState.ArtifactLinks finalLinks = new SlabRigHangingDirectState.ArtifactLinks(
                immediate.artifacts().planned(), immediate.artifacts().immediate(), sha("final"), NONE);
        SlabRigHangingDirectState.EntityOwnership unexplainedSource = restartedState.entities().stream()
                .filter(entity -> entity.uuid().equals(paintings.getFirst())).findFirst().orElseThrow()
                .removed(SlabRigHangingDirectState.RemovalCause.UNEXPLAINED,
                        sha("unexplained removal receipt"));
        expectRuntimeFailure(helper, () -> restartedState.successor(
                        SlabRigHangingDirectState.Phase.FINAL, 16, restartedState.authoredCells(),
                        restartedState.authoredAttachments(), restartedState.cases(),
                        replaceEntity(restartedState.entities(), unexplainedSource),
                        new SlabRigHangingDirectState.Scheduler(armed.processEpoch(), 1, satisfied),
                        restartedState.clear(), finalLinks, "unexplained removal false final"),
                "unexplained removal cannot bypass FINAL tick proof");
        SlabRigHangingDirectState.State finalState = restartedState.successor(
                SlabRigHangingDirectState.Phase.FINAL, 16, restartedState.authoredCells(),
                restartedState.authoredAttachments(), restartedState.cases(), restartedState.entities(),
                new SlabRigHangingDirectState.Scheduler(armed.processEpoch(), 1, satisfied),
                restartedState.clear(), finalLinks, "final");
        SlabRigHangingDirectState.EntityOwnership source = finalState.entities().stream()
                .filter(entity -> entity.role() == SlabRigHangingDirectState.EntityRole.PAINTING)
                .findFirst().orElseThrow();
        SlabRigHangingDirectState.EntityOwnership prematureDrop = dropPreclaim(finalState, source);
        expectRuntimeFailure(helper, () -> finalState.withPreclaimedEntity(prematureDrop,
                        "drop whose source remains in world"),
                "drop preclaim requires a causally removed source painting");
        SlabRigHangingDirectState.EntityOwnership removedSource = source.removed(
                SlabRigHangingDirectState.RemovalCause.SUPPORT_LOSS_DROP_EXPECTED,
                sha("support-loss removal receipt"));
        expectRuntimeFailure(helper, () -> finalState.successor(
                        SlabRigHangingDirectState.Phase.FINAL, 16, finalState.authoredCells(),
                        finalState.authoredAttachments(), finalState.cases(),
                        replaceEntity(finalState.entities(), removedSource), finalState.scheduler(),
                        finalState.clear(), finalState.artifacts(), "missing expected drop row"),
                "support-loss drop expectation cannot reach FINAL without its causal item row");
        SlabRigHangingDirectState.EntityOwnership noDropSource = source.removed(
                SlabRigHangingDirectState.RemovalCause.SUPPORT_LOSS_NO_DROP,
                sha("entity-drops-false removal receipt"));
        SlabRigHangingDirectState.State noDropFinal = finalState.successor(
                SlabRigHangingDirectState.Phase.FINAL, 16, finalState.authoredCells(),
                finalState.authoredAttachments(), finalState.cases(),
                replaceEntity(finalState.entities(), noDropSource), finalState.scheduler(),
                finalState.clear(), finalState.artifacts(), "ENTITY_DROPS=false no-item outcome");
        if (noDropFinal.phase() != SlabRigHangingDirectState.Phase.FINAL) {
            throw helper.assertionException("typed no-drop support loss did not remain FINAL");
        }
        SlabRigHangingDirectState.EntityOwnership drop = dropPreclaim(finalState, removedSource);
        List<SlabRigHangingDirectState.EntityOwnership> atomicRemoval =
                new ArrayList<>(replaceEntity(finalState.entities(), removedSource));
        atomicRemoval.add(drop);
        SlabRigHangingDirectState.State postFinalDrop = finalState.successor(
                SlabRigHangingDirectState.Phase.FINAL, 16, finalState.authoredCells(),
                finalState.authoredAttachments(), finalState.cases(), atomicRemoval,
                finalState.scheduler(), finalState.clear(), finalState.artifacts(),
                "atomic post-final removal plus claim-and-veto drop");
        if (postFinalDrop.phase() != SlabRigHangingDirectState.Phase.FINAL
                || postFinalDrop.entities().stream().noneMatch(entity -> entity.uuid().equals(drop.uuid()))) {
            throw helper.assertionException("post-FINAL causal drop escaped same-phase durable ownership");
        }
        if (postFinalDrop.activePaintingUuidSet().contains(removedSource.uuid())
                || postFinalDrop.activePaintingUuidSet().contains(drop.uuid())
                || postFinalDrop.activePaintingUuidSet().size()
                != postFinalDrop.entities().stream().filter(entity -> entity.role()
                == SlabRigHangingDirectState.EntityRole.PAINTING
                && entity.disposition()
                == SlabRigHangingDirectState.EntityDisposition.IN_WORLD).count()) {
            throw helper.assertionException(
                    "inactive removed/vetoed UUID still received live construction authority");
        }
        SlabRigHangingDirectState.State quarantinedFinal = finalState.successor(
                SlabRigHangingDirectState.Phase.QUARANTINED, 16, finalState.authoredCells(),
                finalState.authoredAttachments(), finalState.cases(), finalState.entities(),
                finalState.scheduler(), finalState.clear(), finalState.artifacts(),
                "clear-only quarantine before later exact drop");
        SlabRigHangingDirectState.State quarantinedDrop = quarantinedFinal.successor(
                SlabRigHangingDirectState.Phase.QUARANTINED, 16,
                quarantinedFinal.authoredCells(), quarantinedFinal.authoredAttachments(),
                quarantinedFinal.cases(), atomicRemoval, quarantinedFinal.scheduler(),
                quarantinedFinal.clear(), quarantinedFinal.artifacts(),
                "atomic post-quarantine removal plus claim-and-veto drop");
        if (quarantinedDrop.entities().stream()
                .noneMatch(entity -> entity.uuid().equals(drop.uuid()))) {
            throw helper.assertionException(
                    "post-QUARANTINE exact causal drop escaped durable ownership");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directStoreReusesVerifiedCellProofsAcrossClearedToPlanned(GameTestHelper helper) {
        Path root = null;
        try {
            root = Files.createTempDirectory("slabbed-direct-store-reuse-");
            SlabRigHangingDirectStateStore store = new SlabRigHangingDirectStateStore(root);
            String firstPlannedArtifact = store.writeArtifact("planned-cache-run-one").hash();
            SlabRigHangingDirectState.State first = initial(firstPlannedArtifact, owner(41));
            store.append(null, first);
            SlabRigHangingDirectState.State authoring = fixtureAuthoring(first);
            store.append(first, authoring);
            for (SlabRigHangingDirectState.Position position : authoring.plannedAuthoredCells()) {
                store.writeArtifact(SlabRigHangingDirectEvidence.cellIdentityCanonical(
                        syntheticCell(position)));
            }
            SlabRigHangingDirectState.State ready = fixtureReadyFromAuthoring(authoring);
            store.append(authoring, ready);

            SlabRigHangingDirectState.ClearProgress started =
                    SlabRigHangingDirectState.ClearProgress.begin(ready);
            SlabRigHangingDirectState.State clearingEntities = ready.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_ENTITIES, ready.nextCaseOrdinal(),
                    ready.authoredCells(), ready.authoredAttachments(), ready.cases(),
                    ready.entities(), ready.scheduler(), started, ready.artifacts(),
                    "cache proof clear entities");
            store.append(ready, clearingEntities);
            SlabRigHangingDirectState.State clearingAttachments = clearingEntities.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_ATTACHMENTS,
                    clearingEntities.nextCaseOrdinal(), clearingEntities.authoredCells(),
                    clearingEntities.authoredAttachments(), clearingEntities.cases(),
                    clearingEntities.entities(), clearingEntities.scheduler(), started,
                    clearingEntities.artifacts(), "cache proof clear attachments");
            store.append(clearingEntities, clearingAttachments);

            SlabRigHangingDirectState.ClearProgress attachmentsDone =
                    new SlabRigHangingDirectState.ClearProgress(true,
                            started.requestedEntities(), started.requestedEntities().size(),
                            List.of(), started.requestedEntities(), List.of(),
                            started.requestedAttachments(), started.requestedAttachments().size(),
                            started.requestedAttachments(), List.of(), List.of(),
                            started.requestedCells(), 0, List.of(), List.of(), List.of());
            SlabRigHangingDirectState.State attachmentReceipts = clearingAttachments.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_ATTACHMENTS,
                    clearingAttachments.nextCaseOrdinal(), clearingAttachments.authoredCells(),
                    clearingAttachments.authoredAttachments(), clearingAttachments.cases(),
                    clearingAttachments.entities(), clearingAttachments.scheduler(), attachmentsDone,
                    clearingAttachments.artifacts(), "cache proof attachments complete");
            store.append(clearingAttachments, attachmentReceipts);
            SlabRigHangingDirectState.State clearingCells = attachmentReceipts.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_CELLS,
                    attachmentReceipts.nextCaseOrdinal(), attachmentReceipts.authoredCells(),
                    attachmentReceipts.authoredAttachments(), attachmentReceipts.cases(),
                    attachmentReceipts.entities(), attachmentReceipts.scheduler(), attachmentsDone,
                    attachmentReceipts.artifacts(), "cache proof clear cells");
            store.append(attachmentReceipts, clearingCells);

            SlabRigHangingDirectState.ClearProgress allDone =
                    new SlabRigHangingDirectState.ClearProgress(true,
                            started.requestedEntities(), started.requestedEntities().size(),
                            List.of(), started.requestedEntities(), List.of(),
                            started.requestedAttachments(), started.requestedAttachments().size(),
                            started.requestedAttachments(), List.of(), List.of(),
                            started.requestedCells(), started.requestedCells().size(),
                            started.requestedCells(), List.of(), List.of());
            SlabRigHangingDirectState.State cellReceipts = clearingCells.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_CELLS,
                    clearingCells.nextCaseOrdinal(), clearingCells.authoredCells(),
                    clearingCells.authoredAttachments(), clearingCells.cases(),
                    clearingCells.entities(), clearingCells.scheduler(), allDone,
                    clearingCells.artifacts(), "cache proof cells complete");
            store.append(clearingCells, cellReceipts);
            String clearedArtifact = store.writeArtifact("cleared-cache-run-one").hash();
            SlabRigHangingDirectState.ArtifactLinks clearedLinks =
                    new SlabRigHangingDirectState.ArtifactLinks(
                            cellReceipts.artifacts().planned(), cellReceipts.artifacts().immediate(),
                            cellReceipts.artifacts().finalArtifact(), clearedArtifact);
            SlabRigHangingDirectState.State cleared = cellReceipts.successor(
                    SlabRigHangingDirectState.Phase.CLEARED, cellReceipts.nextCaseOrdinal(),
                    cellReceipts.authoredCells(), cellReceipts.authoredAttachments(),
                    cellReceipts.cases(), cellReceipts.entities(), cellReceipts.scheduler(),
                    allDone, clearedLinks, "cache proof cleared");
            store.append(cellReceipts, cleared);

            String secondPlannedArtifact = store.writeArtifact("planned-cache-run-two").hash();
            SlabRigHangingDirectState.State replanned = SlabRigHangingDirectState.State.afterCleared(
                    cleared, run(42), first.reservedCells(), first.plannedAuthoredCells(),
                    pendingCases(), secondPlannedArtifact, "cache proof replanned");
            long beforeReuse = store.verifiedPrefixReuseCount();
            store.append(cleared, replanned);
            SlabRigHangingDirectState.State secondAuthoring = fixtureAuthoring(replanned);
            store.append(replanned, secondAuthoring);
            SlabRigHangingDirectState.State secondReady =
                    fixtureReadyFromAuthoring(secondAuthoring);
            store.append(secondAuthoring, secondReady);
            long afterReuse = store.verifiedPrefixReuseCount();
            if (afterReuse - beforeReuse != 3L) {
                throw helper.assertionException(
                        "CLEARED->PLANNED same-position proof did not use three exact cache hits: "
                                + beforeReuse + " -> " + afterReuse);
            }
            SlabRigHangingDirectState.State rebuilt =
                    new SlabRigHangingDirectStateStore(root).reconstruct(first.owner()).latestOrNull();
            if (!secondReady.equals(rebuilt)) {
                throw helper.assertionException(
                        "fresh reconstruction disagreed after cached same-position proof reuse");
            }
        } catch (IOException failure) {
            throw helper.assertionException("CLEARED->PLANNED cache proof failed: " + failure);
        } finally {
            deleteTree(root);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directStoreRejectsCrossOwnerPageAndMissingObservationKeepsPrefix(GameTestHelper helper) {
        Path root = null;
        try {
            root = Files.createTempDirectory("slabbed-direct-store-global-");
            SlabRigHangingDirectStateStore store = new SlabRigHangingDirectStateStore(root);
            String planned = store.writeArtifact("planned-global").hash();
            SlabRigHangingDirectState.State firstOwner = initial(planned, owner(8));
            store.append(null, firstOwner);
            SlabRigHangingDirectState.State secondOwner = initial(planned, owner(9));
            expectIoFailure(helper, () -> store.append(null, secondOwner),
                    "cross-owner world/dimension allocation");

            SlabRigHangingDirectState.State authoring = fixtureAuthoring(firstOwner);
            store.append(firstOwner, authoring);
            SlabRigHangingDirectState.State ready = fixtureReadyFromAuthoring(authoring);
            expectIoFailure(helper, () -> store.append(authoring, ready),
                    "missing linked confirmed-cell receipt");
            for (SlabRigHangingDirectState.CellOwnership cell : ready.authoredCells()) {
                store.writeArtifact(SlabRigHangingDirectEvidence.cellIdentityCanonical(
                        syntheticCell(cell.pos())));
            }
            List<SlabRigHangingDirectState.CellOwnership> aliasedCells =
                    new ArrayList<>(ready.authoredCells());
            List<SlabRigHangingDirectState.AttachmentOwnership> aliasedAttachments =
                    new ArrayList<>(ready.authoredAttachments());
            aliasedCells.set(1, new SlabRigHangingDirectState.CellOwnership(
                    aliasedCells.get(1).pos(), aliasedCells.getFirst().fingerprint()));
            aliasedAttachments.set(1, new SlabRigHangingDirectState.AttachmentOwnership(
                    aliasedAttachments.get(1).pos(), aliasedAttachments.getFirst().fingerprint()));
            SlabRigHangingDirectState.State aliasedReady = authoring.successor(
                    SlabRigHangingDirectState.Phase.FIXTURE_READY, 0, aliasedCells,
                    aliasedAttachments, ready.cases(), ready.entities(), ready.scheduler(),
                    ready.clear(), ready.artifacts(), "copied cell proof must refuse");
            expectIoFailure(helper, () -> store.append(authoring, aliasedReady),
                    "cell/attachment hash copied to a second position");
            if (Files.exists(store.statePath(aliasedReady))) {
                throw helper.assertionException("aliased cell proof published a state");
            }

            List<SlabRigHangingDirectState.CellOwnership> genericHashCells =
                    new ArrayList<>(ready.authoredCells());
            genericHashCells.set(0, new SlabRigHangingDirectState.CellOwnership(
                    genericHashCells.getFirst().pos(), planned));
            SlabRigHangingDirectState.State genericHashReady = authoring.successor(
                    SlabRigHangingDirectState.Phase.FIXTURE_READY, 0, genericHashCells,
                    ready.authoredAttachments(), ready.cases(), ready.entities(), ready.scheduler(),
                    ready.clear(), ready.artifacts(), "generic artifact role reuse must refuse");
            expectIoFailure(helper, () -> store.append(authoring, genericHashReady),
                    "planned artifact hash reused as cell proof");
            if (Files.exists(store.statePath(genericHashReady))) {
                throw helper.assertionException("generic artifact role reuse published a state");
            }

            List<SlabRigHangingDirectState.AttachmentOwnership> wrongAttachments =
                    new ArrayList<>(ready.authoredAttachments());
            SlabRigHangingDirectState.AttachmentOwnership firstAttachment =
                    wrongAttachments.getFirst();
            wrongAttachments.set(0, new SlabRigHangingDirectState.AttachmentOwnership(
                    firstAttachment.pos(), sha("wrong derived attachment")));
            SlabRigHangingDirectState.State wrongReady = authoring.successor(
                    SlabRigHangingDirectState.Phase.FIXTURE_READY, 0,
                    ready.authoredCells(), wrongAttachments, ready.cases(), ready.entities(),
                    ready.scheduler(), ready.clear(), ready.artifacts(),
                    "wrong derived attachment must refuse");
            expectIoFailure(helper, () -> store.append(authoring, wrongReady),
                    "wrong derived attachment receipt");
            store.append(authoring, ready);
            SlabRigHangingDirectState.State inFlight = beginCase(ready, 0);
            store.append(ready, inFlight);
            SlabRigHangingDirectState.State preclaimed = inFlight.withPreclaimedEntity(
                    paintingPreclaim(inFlight, 0), "preclaimed");
            expectIoFailure(helper, () -> store.append(inFlight, preclaimed),
                    "missing linked full entity-evidence receipt");
            SlabRigHangingDirectState.EntityOwnership preclaimedPainting =
                    preclaimed.entities().getFirst();
            SlabRigHangingDirectStateStore.WrittenArtifact entityEvidence = store.writeArtifact(
                    "painting-evidence-" + preclaimedPainting.uuid());
            if (!entityEvidence.hash().equals(preclaimedPainting.evidenceArtifact())) {
                throw helper.assertionException("test entity-evidence hash disagreed with state link");
            }
            store.append(inFlight, preclaimed);
            SlabRigHangingDirectState.State confirmed = preclaimed.withConfirmedEntity(
                    preclaimed.entities().getFirst().uuid(), "confirmed");
            store.append(preclaimed, confirmed);

            SlabRigHangingDirectStateStore.WrittenArtifact observation =
                    store.writeArtifact("case-zero-observation");
            SlabRigHangingDirectState.State partial = completeCase(confirmed, 0, observation.hash());
            store.append(confirmed, partial);
            SlabRigHangingDirectState.EntityOwnership removed = partial.entities().getFirst()
                    .removed(SlabRigHangingDirectState.RemovalCause.SUPPORT_LOSS_DROP_EXPECTED,
                            sha("missing removal artifact"));
            SlabRigHangingDirectState.State removalQuarantine = partial.successor(
                    SlabRigHangingDirectState.Phase.QUARANTINED, 1,
                    partial.authoredCells(), partial.authoredAttachments(), partial.cases(),
                    replaceEntity(partial.entities(), removed), partial.scheduler(), partial.clear(),
                    partial.artifacts(), "removal receipt must exist");
            expectIoFailure(helper, () -> store.append(partial, removalQuarantine),
                    "missing linked painting-removal receipt");
            Files.delete(store.artifactPath(observation.hash()));
            try {
                new SlabRigHangingDirectStateStore(root).reconstruct(firstOwner.owner());
                throw helper.assertionException("missing linked case observation was accepted");
            } catch (SlabRigHangingDirectStateStore.CorruptLedgerException expected) {
                if (expected.verifiedPrefix().latestOrNull() == null
                        || !expected.verifiedPrefix().latestOrNull().stateHash()
                        .equals(confirmed.stateHash())) {
                    throw helper.assertionException("missing observation lost the exact verified prefix");
                }
            }
        } catch (IOException failure) {
            throw helper.assertionException("global/observation proof failed: " + failure);
        } finally {
            deleteTree(root);
        }
        helper.succeed();
    }

    private static SlabRigHangingDirectState.Owner owner(int id) {
        // Same exact world/dimension intentionally lets the global allocation test compare two players.
        return new SlabRigHangingDirectState.Owner(sha("world"), "minecraft:overworld",
                UUID.nameUUIDFromBytes(("player-" + id).getBytes(StandardCharsets.UTF_8)));
    }

    private static SlabRigHangingDirectState.RunIdentity run(int id) {
        return new SlabRigHangingDirectState.RunIdentity(sha("run-" + id),
                UUID.nameUUIDFromBytes(("nonce-" + id).getBytes(StandardCharsets.UTF_8)),
                "unknown", sha("runtime"), "26.2", sha("catalog"), sha("topologies"),
                sha("rig3b1"), sha("painting registry"), sha("universe"), sha("plan"),
                "painting-page-v1:sha256:" + sha("semantic"), 6143, 42, 1, 16, true,
                new SlabRigHangingDirectState.Position(10 + id, 64, 10), "west");
    }

    private static SlabRigHangingDirectState.State initial(String planned,
                                                            SlabRigHangingDirectState.Owner owner) {
        List<SlabRigHangingDirectState.Position> plannedCells = List.of(
                new SlabRigHangingDirectState.Position(0, 64, 0),
                new SlabRigHangingDirectState.Position(0, 65, 0));
        List<SlabRigHangingDirectState.Position> reserved = List.of(
                plannedCells.get(0), plannedCells.get(1),
                new SlabRigHangingDirectState.Position(1, 65, 0));
        int id = Math.abs(owner.playerUuid().hashCode());
        return SlabRigHangingDirectState.State.initial(owner, run(id), reserved, plannedCells,
                pendingCases(), planned, "planned");
    }

    private static List<SlabRigHangingDirectState.CaseState> pendingCases() {
        return pendingCases(16);
    }

    private static List<SlabRigHangingDirectState.CaseState> pendingCases(int caseCount) {
        List<SlabRigHangingDirectState.CaseState> result = new ArrayList<>();
        for (int ordinal = 0; ordinal < caseCount; ordinal++) {
            result.add(pendingCase(ordinal));
        }
        return List.copyOf(result);
    }

    private static SlabRigHangingDirectState.CaseState pendingCase(int ordinal) {
        return new SlabRigHangingDirectState.CaseState(ordinal, "attempt-" + ordinal,
                "selector-" + ordinal, sha("component-" + ordinal),
                SlabRigHangingDirectState.CasePhase.PENDING,
                SlabRigHangingDirectState.CaseOutcome.NONE, NONE);
    }

    private static SlabRigHangingDirectState.State fixtureAuthoring(
            SlabRigHangingDirectState.State state) {
        return state.successor(SlabRigHangingDirectState.Phase.FIXTURE_AUTHORING, 0,
                List.of(), List.of(), state.cases(), state.entities(), state.scheduler(), state.clear(),
                state.artifacts(), "fixture authoring");
    }

    private static SlabRigHangingDirectState.State fixtureReady(
            SlabRigHangingDirectState.State initial) {
        return fixtureReadyFromAuthoring(fixtureAuthoring(initial));
    }

    private static SlabRigHangingDirectState.State fixtureReadyFromAuthoring(
            SlabRigHangingDirectState.State authoring) {
        List<SlabRigHangingDirectState.CellOwnership> cells = confirmedCells(authoring);
        List<SlabRigHangingDirectState.AttachmentOwnership> attachments =
                authoring.plannedAuthoredCells().stream().map(pos ->
                        new SlabRigHangingDirectState.AttachmentOwnership(
                                pos, SlabRigHangingDirectEvidence.attachmentIdentityFingerprint(
                                syntheticCell(pos)))).toList();
        return authoring.successor(SlabRigHangingDirectState.Phase.FIXTURE_READY, 0,
                cells, attachments, authoring.cases(), authoring.entities(), authoring.scheduler(),
                authoring.clear(), authoring.artifacts(), "fixture ready");
    }

    private static List<SlabRigHangingDirectState.CellOwnership> confirmedCells(
            SlabRigHangingDirectState.State state) {
        return state.plannedAuthoredCells().stream().map(pos ->
                new SlabRigHangingDirectState.CellOwnership(pos,
                        SlabRigHangingDirectEvidence.cellIdentityFingerprint(
                                syntheticCell(pos)))).toList();
    }

    private static SlabRigHangingDirectEvidence.CellEvidence syntheticCell(
            SlabRigHangingDirectState.Position pos) {
        return new SlabRigHangingDirectEvidence.CellEvidence(pos.toBlockPos(),
                "Block{minecraft:stone}", "NONE", sha("NONE"),
                Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(Double.NaN),
                "anchored=false,frozen=false,compound=false,sideLower=false,sideUpper=false,"
                        + "sideDouble=false,ownerTop=false,persistentCarrier=false");
    }

    private static SlabRigHangingDirectState.State beginCase(
            SlabRigHangingDirectState.State state, int ordinal) {
        List<SlabRigHangingDirectState.CaseState> cases = new ArrayList<>(state.cases());
        cases.set(ordinal, cases.get(ordinal).inFlight());
        return state.successor(SlabRigHangingDirectState.Phase.CASE_IN_FLIGHT, ordinal,
                state.authoredCells(), state.authoredAttachments(), cases, state.entities(),
                state.scheduler(), state.clear(), state.artifacts(), "case " + ordinal + " in flight");
    }

    private static SlabRigHangingDirectState.State completeCase(
            SlabRigHangingDirectState.State state, int ordinal, String observationId) {
        List<SlabRigHangingDirectState.CaseState> cases = new ArrayList<>(state.cases());
        cases.set(ordinal, cases.get(ordinal).immediate(
                SlabRigHangingDirectState.CaseOutcome.PLACED, observationId));
        boolean last = ordinal + 1 == state.run().caseCount();
        SlabRigHangingDirectState.ArtifactLinks artifacts = last
                ? new SlabRigHangingDirectState.ArtifactLinks(state.artifacts().planned(),
                sha("immediate-page"), NONE, NONE)
                : state.artifacts();
        return state.successor(last ? SlabRigHangingDirectState.Phase.IMMEDIATE
                        : SlabRigHangingDirectState.Phase.IMMEDIATE_PARTIAL,
                ordinal + 1, state.authoredCells(), state.authoredAttachments(), cases,
                state.entities(), state.scheduler(), state.clear(), artifacts,
                "case " + ordinal + " immediate");
    }

    private static SlabRigHangingDirectState.State completePage(
            SlabRigHangingDirectState.State ready) {
        SlabRigHangingDirectState.State state = ready;
        for (int ordinal = 0; ordinal < ready.run().caseCount(); ordinal++) {
            state = beginCase(state, ordinal);
            SlabRigHangingDirectState.EntityOwnership preclaim = paintingPreclaim(state, ordinal);
            state = state.withPreclaimedEntity(preclaim, "painting preclaim");
            state = state.withConfirmedEntity(preclaim.uuid(), "painting loaded");
            state = completeCase(state, ordinal, sha("observation-" + ordinal));
        }
        return state;
    }

    private static SlabRigHangingDirectState.EntityOwnership paintingPreclaim(
            SlabRigHangingDirectState.State state, int ordinal) {
        UUID uuid = UUID.nameUUIDFromBytes((state.run().runId() + "|painting|" + ordinal)
                .getBytes(StandardCharsets.UTF_8));
        return new SlabRigHangingDirectState.EntityOwnership(uuid,
                SlabRigHangingDirectState.EntityRole.PAINTING, "minecraft:painting", ordinal,
                state.cases().get(ordinal).attemptId(), NONE,
                SlabRigHangingDirectState.Acquisition.PRECLAIMED,
                SlabRigHangingDirectState.PreclaimDecision.ALLOW_AND_CONFIRM,
                SlabRigHangingDirectState.EntityDisposition.PREINSERTION,
                sha("painting-fingerprint-" + uuid),
                sha("painting-evidence-" + uuid),
                new SlabRigHangingDirectState.Vec3Bits(0, 0, 0),
                new SlabRigHangingDirectState.BoxBits(0, 0, 0, 1, 1, 1),
                NONE, NONE);
    }

    private static SlabRigHangingDirectState.EntityOwnership dropPreclaim(
            SlabRigHangingDirectState.State state,
            SlabRigHangingDirectState.EntityOwnership source) {
        UUID uuid = UUID.nameUUIDFromBytes((state.run().runId() + "|drop|" + source.uuid())
                .getBytes(StandardCharsets.UTF_8));
        return new SlabRigHangingDirectState.EntityOwnership(uuid,
                SlabRigHangingDirectState.EntityRole.DROPPED_ITEM, "minecraft:item",
                source.caseOrdinal(), source.attemptId(), source.uuid().toString(),
                SlabRigHangingDirectState.Acquisition.DROP_PRECLAIM,
                SlabRigHangingDirectState.PreclaimDecision.CLAIM_AND_VETO,
                SlabRigHangingDirectState.EntityDisposition.VETOED_BEFORE_INSERTION,
                sha("drop-fingerprint-" + uuid), sha("drop-evidence-" + uuid),
                source.position(), source.aabb(), NONE,
                "vetoed before insertion; no pickup/merge/container transfer possible");
    }

    private static List<SlabRigHangingDirectState.TickCredit> allCredits(
            List<SlabRigHangingDirectState.TickCredit> source, int ticks, boolean loaded,
            long lastTick) {
        return source.stream().map(credit -> new SlabRigHangingDirectState.TickCredit(
                credit.paintingUuid(), ticks, loaded, credit.unloadResets(),
                lastTick)).toList();
    }

    private static List<SlabRigHangingDirectState.EntityOwnership> replaceEntity(
            List<SlabRigHangingDirectState.EntityOwnership> entities,
            SlabRigHangingDirectState.EntityOwnership replacement) {
        return entities.stream().map(entity -> entity.uuid().equals(replacement.uuid())
                ? replacement : entity).toList();
    }

    private static String sha(String value) {
        return SlabRigHangingDirectState.sha256(value);
    }

    private static SlabRigHangingDirectState.State historicalStateWithComputedHash(
            SlabRigHangingDirectState.State seed) {
        try {
            var method = SlabRigHangingDirectState.class.getDeclaredMethod(
                    "withComputedHash", SlabRigHangingDirectState.State.class);
            method.setAccessible(true);
            return (SlabRigHangingDirectState.State) method.invoke(null, seed);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                    "could not create a test-only self-hashed historical legacy state", failure);
        }
    }

    private static void expectRuntimeFailure(GameTestHelper helper, Runnable action, String label) {
        try {
            action.run();
            throw helper.assertionException(label + " unexpectedly succeeded");
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // Expected fail-closed contract.
        }
    }

    private static void expectIoFailure(GameTestHelper helper, IoAction action, String label) {
        try {
            action.run();
            throw helper.assertionException(label + " unexpectedly succeeded");
        } catch (IOException expected) {
            // Expected fail-closed filesystem contract.
        }
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Test cleanup must not mask the assertion that already failed.
        }
    }
}
