package com.slabbed.test;

import com.slabbed.command.SlabRigCaseCatalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Run-scoped, fail-closed artifact barrier for the opt-in registry sweep. */
final class RegistrySweepArtifacts {

    static final String SCHEMA = "slabbed-registry-sweep-artifacts-v4";
    static final String HEADER = "item\trig\tplaced_dy\tstored_dy\tseat\tseat_delta\tstays\tnotes";
    static final String NO_REPORT_SHA256 = "none";
    private static final List<String> RIGS = List.of("flush_ground", "lowered_stack", "marked_slab");

    record RunToken(String runId, String buildGitSha, String runtimeImplementationSha256,
                    boolean frozenDyEnabled, String catalogHash,
                    int itemCount, int expectedRows, Path runDir) {
        RunToken {
            if (!isExactSha256(runtimeImplementationSha256)) {
                throw new IllegalArgumentException(
                        "registry sweep run token requires exact runtime implementation SHA-256");
            }
        }
    }

    private static final class ActiveRun {
        private final RunToken token;
        private final boolean[] started;
        private final boolean[] completed;
        private boolean published;

        private ActiveRun(RunToken token, int shardCount) {
            this.token = token;
            this.started = new boolean[shardCount];
            this.completed = new boolean[shardCount];
        }
    }

    private final Path root;
    private final int shardCount;
    private final Object lock = new Object();
    private ActiveRun active;

    RegistrySweepArtifacts(Path root, int shardCount) {
        this.root = root.toAbsolutePath().normalize();
        this.shardCount = shardCount;
    }

    RunToken beginShard(SlabRigCaseCatalog.Snapshot snapshot, String buildGitSha,
                        String runtimeImplementationSha256, boolean frozenDyEnabled,
                        int shard) throws IOException {
        requireShard(shard);
        requireExactSha256(runtimeImplementationSha256, "runtime implementation");
        synchronized (lock) {
            if (active == null || active.published) {
                beginRun(snapshot, buildGitSha, runtimeImplementationSha256, frozenDyEnabled);
            }
            RunToken token = active.token;
            if (!token.catalogHash().equals(snapshot.catalogHash())
                    || !token.buildGitSha().equals(buildGitSha)
                    || !token.runtimeImplementationSha256().equals(runtimeImplementationSha256)
                    || token.frozenDyEnabled() != frozenDyEnabled
                    || token.itemCount() != snapshot.items().size()
                    || token.expectedRows() != Math.multiplyExact(snapshot.items().size(), RIGS.size())) {
                throw new IOException("registry sweep shard identity disagrees with active run");
            }
            if (active.started[shard]) {
                throw new IOException("registry sweep duplicate shard start: " + shard);
            }
            active.started[shard] = true;
            writeStatus(active, "RUNNING", NO_REPORT_SHA256);
            return token;
        }
    }

    boolean completeShard(RunToken token, SlabRigCaseCatalog.Snapshot snapshot,
                          boolean frozenDyEnabled, int shard, List<String> rows) throws IOException {
        requireShard(shard);
        synchronized (lock) {
            requireActiveToken(token);
            requireSnapshotIdentity(token, snapshot);
            if (token.frozenDyEnabled() != frozenDyEnabled) {
                throw new IOException("registry sweep frozen-dy mode changed during active run");
            }
            if (!active.started[shard] || active.completed[shard]) {
                throw new IOException("registry sweep shard completion is missing a unique start: " + shard);
            }
            List<String> canonicalRows = validateRows(snapshot, shard, rows);
            writeShard(token, shard, canonicalRows);
            active.completed[shard] = true;
            writeStatus(active, "RUNNING", NO_REPORT_SHA256);
            for (boolean completed : active.completed) {
                if (!completed) {
                    return false;
                }
            }
            String reportSha256 = publish(snapshot);
            writeStatus(active, "COMPLETE", reportSha256);
            readAndValidateCompleteReport(active.token, snapshot);
            active.published = true;
            return true;
        }
    }

    List<String> validateShardFileForTests(RunToken token,
                                           SlabRigCaseCatalog.Snapshot snapshot,
                                           int shard) throws IOException {
        synchronized (lock) {
            return readAndValidateShard(shardPath(token, shard), token, snapshot, shard);
        }
    }

    List<String> validateCompleteReportForTests(RunToken token,
                                                SlabRigCaseCatalog.Snapshot snapshot)
            throws IOException {
        synchronized (lock) {
            return readAndValidateCompleteReport(token, snapshot);
        }
    }

    Path reportPath() {
        return root.resolve("slabbed-sweep.tsv");
    }

    Path statusPath() {
        return root.resolve("slabbed-sweep.status.tsv");
    }

    Path historyDir() {
        return root.resolve("slabbed-sweep-history");
    }

    Path shardPath(RunToken token, int shard) {
        return token.runDir().resolve("shard-" + shard + ".tsv");
    }

    static int expectedRowsForShard(int itemCount, int shardCount, int shard) {
        int itemRows = 0;
        for (int index = shard; index < itemCount; index += shardCount) {
            itemRows++;
        }
        return Math.multiplyExact(itemRows, RIGS.size());
    }

    private void beginRun(SlabRigCaseCatalog.Snapshot snapshot, String buildGitSha,
                          String runtimeImplementationSha256,
                          boolean frozenDyEnabled) throws IOException {
        Files.createDirectories(root);
        String runId = UUID.randomUUID().toString();
        archiveIfPresent(reportPath(), runId, "report");
        archiveIfPresent(statusPath(), runId, "status");
        RunToken token = new RunToken(runId, buildGitSha, runtimeImplementationSha256,
                frozenDyEnabled, snapshot.catalogHash(),
                snapshot.items().size(), Math.multiplyExact(snapshot.items().size(), RIGS.size()),
                root.resolve("slabbed-sweep-runs").resolve(runId));
        Files.createDirectories(token.runDir());
        active = new ActiveRun(token, shardCount);
        writeStatus(active, "RUNNING", NO_REPORT_SHA256);
    }

    private void archiveIfPresent(Path source, String runId, String kind) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        Files.createDirectories(historyDir());
        Path target = historyDir().resolve("before-" + runId + "-" + kind + ".tsv");
        atomicMove(source, target);
    }

    private void writeShard(RunToken token, int shard, List<String> rows) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("# schema\t").append(SCHEMA).append('\n');
        out.append("# run_id\t").append(token.runId()).append('\n');
        out.append("# build_git_sha\t").append(token.buildGitSha()).append('\n');
        out.append("# runtime_implementation_sha256\t")
                .append(token.runtimeImplementationSha256()).append('\n');
        out.append("# frozen_dy_enabled\t").append(token.frozenDyEnabled()).append('\n');
        out.append("# catalog_hash\t").append(token.catalogHash()).append('\n');
        out.append("# shard\t").append(shard).append('/').append(shardCount).append('\n');
        out.append("# expected_rows\t")
                .append(expectedRowsForShard(token.itemCount(), shardCount, shard)).append('\n');
        out.append(HEADER).append('\n');
        for (String row : rows) {
            out.append(row).append('\n');
        }
        atomicWrite(shardPath(token, shard), out.toString());
    }

    private List<String> readAndValidateShard(Path path, RunToken token,
                                              SlabRigCaseCatalog.Snapshot snapshot,
                                              int shard) throws IOException {
        requireSnapshotIdentity(token, snapshot);
        if (!Files.isRegularFile(path)) {
            throw new IOException("current registry sweep shard missing: " + path);
        }
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        int expectedRows = expectedRowsForShard(token.itemCount(), shardCount, shard);
        List<String> prefix = List.of(
                "# schema\t" + SCHEMA,
                "# run_id\t" + token.runId(),
                "# build_git_sha\t" + token.buildGitSha(),
                "# runtime_implementation_sha256\t" + token.runtimeImplementationSha256(),
                "# frozen_dy_enabled\t" + token.frozenDyEnabled(),
                "# catalog_hash\t" + token.catalogHash(),
                "# shard\t" + shard + "/" + shardCount,
                "# expected_rows\t" + expectedRows,
                HEADER);
        if (lines.size() != prefix.size() + expectedRows
                || !lines.subList(0, prefix.size()).equals(prefix)) {
            throw new IOException("registry sweep shard metadata/row count mismatch: " + path);
        }
        return validateRows(snapshot, shard, lines.subList(prefix.size(), lines.size()));
    }

    private List<String> validateRows(SlabRigCaseCatalog.Snapshot snapshot,
                                      int shard, List<String> rows) throws IOException {
        Set<String> expectedKeys = new HashSet<>();
        for (int index = shard; index < snapshot.items().size(); index += shardCount) {
            String item = snapshot.items().get(index).id();
            for (String rig : RIGS) {
                expectedKeys.add(item + "\t" + rig);
            }
        }
        if (rows.size() != expectedKeys.size()) {
            throw new IOException("registry sweep shard " + shard + " row count " + rows.size()
                    + " != expected " + expectedKeys.size());
        }
        Set<String> actualKeys = new HashSet<>();
        List<String> canonical = new ArrayList<>(rows);
        for (String row : canonical) {
            String[] fields = row.split("\t", -1);
            if (fields.length != 8 || !actualKeys.add(fields[0] + "\t" + fields[1])) {
                throw new IOException("registry sweep shard contains malformed/duplicate row: " + row);
            }
        }
        if (!actualKeys.equals(expectedKeys)) {
            throw new IOException("registry sweep shard key universe mismatch at shard " + shard);
        }
        canonical.sort(Comparator.naturalOrder());
        return List.copyOf(canonical);
    }

    private String publish(SlabRigCaseCatalog.Snapshot snapshot) throws IOException {
        List<String> all = new ArrayList<>();
        for (int shard = 0; shard < shardCount; shard++) {
            all.addAll(readAndValidateShard(shardPath(active.token, shard),
                    active.token, snapshot, shard));
        }
        Set<String> keys = new HashSet<>();
        for (String row : all) {
            String[] fields = row.split("\t", -1);
            if (!keys.add(fields[0] + "\t" + fields[1])) {
                throw new IOException("registry sweep aggregate contains duplicate key "
                        + fields[0] + "\t" + fields[1]);
            }
        }
        if (all.size() != active.token.expectedRows() || keys.size() != active.token.expectedRows()) {
            throw new IOException("registry sweep aggregate is incomplete: " + all.size()
                    + "/" + active.token.expectedRows());
        }
        all.sort(Comparator.naturalOrder());
        byte[] reportBytes = reportText(active.token, all).getBytes(StandardCharsets.UTF_8);
        atomicWrite(reportPath(), reportBytes);
        return sha256(reportBytes);
    }

    private List<String> readAndValidateCompleteReport(RunToken token,
                                                       SlabRigCaseCatalog.Snapshot snapshot)
            throws IOException {
        requireSnapshotIdentity(token, snapshot);
        if (!Files.isRegularFile(reportPath()) || !Files.isRegularFile(statusPath())) {
            throw new IOException("registry sweep COMPLETE report/status pair is missing");
        }
        byte[] reportBytes = Files.readAllBytes(reportPath());
        String reportSha256 = sha256(reportBytes);
        String expectedStatus = statusText(token, "COMPLETE", shardCount, reportSha256);
        byte[] statusBytes = Files.readAllBytes(statusPath());
        if (!MessageDigest.isEqual(statusBytes, expectedStatus.getBytes(StandardCharsets.UTF_8))) {
            throw new IOException("registry sweep COMPLETE status metadata/hash mismatch");
        }

        List<String> lines = Files.readAllLines(reportPath(), StandardCharsets.UTF_8);
        List<String> prefix = reportPrefix(token);
        if (lines.size() != prefix.size() + token.expectedRows()
                || !lines.subList(0, prefix.size()).equals(prefix)) {
            throw new IOException("registry sweep COMPLETE report metadata/row count mismatch");
        }
        List<String> rawRows = lines.subList(prefix.size(), lines.size());
        List<String> canonicalRows = validateAggregateRows(snapshot, rawRows);
        byte[] canonicalBytes = reportText(token, canonicalRows).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(reportBytes, canonicalBytes)) {
            throw new IOException("registry sweep COMPLETE report bytes are not canonical");
        }
        return canonicalRows;
    }

    private List<String> validateAggregateRows(SlabRigCaseCatalog.Snapshot snapshot,
                                                List<String> rows) throws IOException {
        Set<String> expectedKeys = new HashSet<>();
        for (SlabRigCaseCatalog.CatalogItem item : snapshot.items()) {
            for (String rig : RIGS) {
                expectedKeys.add(item.id() + "\t" + rig);
            }
        }
        if (rows.size() != expectedKeys.size()) {
            throw new IOException("registry sweep aggregate row count " + rows.size()
                    + " != expected " + expectedKeys.size());
        }
        Set<String> actualKeys = new HashSet<>();
        List<String> canonical = new ArrayList<>(rows);
        for (String row : canonical) {
            String[] fields = row.split("\t", -1);
            if (fields.length != 8 || !actualKeys.add(fields[0] + "\t" + fields[1])) {
                throw new IOException("registry sweep aggregate contains malformed/duplicate row: " + row);
            }
        }
        if (!actualKeys.equals(expectedKeys)) {
            throw new IOException("registry sweep aggregate key universe mismatch");
        }
        canonical.sort(Comparator.naturalOrder());
        return List.copyOf(canonical);
    }

    private List<String> reportPrefix(RunToken token) {
        return List.of(
                "# schema\t" + SCHEMA,
                "# status\tCOMPLETE",
                "# run_id\t" + token.runId(),
                "# build_git_sha\t" + token.buildGitSha(),
                "# runtime_implementation_sha256\t" + token.runtimeImplementationSha256(),
                "# frozen_dy_enabled\t" + token.frozenDyEnabled(),
                "# catalog_hash\t" + token.catalogHash(),
                "# runtime_block_items\t" + token.itemCount(),
                "# expected_rows\t" + token.expectedRows(),
                "# completed_unique_shards\t" + shardCount + "/" + shardCount,
                HEADER);
    }

    private String reportText(RunToken token, List<String> rows) {
        StringBuilder report = new StringBuilder();
        for (String line : reportPrefix(token)) {
            report.append(line).append('\n');
        }
        for (String row : rows) {
            report.append(row).append('\n');
        }
        return report.toString();
    }

    private void writeStatus(ActiveRun run, String status, String reportSha256) throws IOException {
        int completed = 0;
        for (boolean value : run.completed) {
            if (value) {
                completed++;
            }
        }
        if ("RUNNING".equals(status)) {
            if (!NO_REPORT_SHA256.equals(reportSha256)) {
                throw new IOException("RUNNING registry sweep status cannot bind a report digest");
            }
        } else if ("COMPLETE".equals(status)) {
            requireExactSha256(reportSha256, "complete report");
            if (completed != shardCount) {
                throw new IOException("COMPLETE registry sweep status requires every unique shard");
            }
        } else {
            throw new IOException("unknown registry sweep status: " + status);
        }
        atomicWrite(statusPath(), statusText(run.token, status, completed, reportSha256));
    }

    private String statusText(RunToken token, String status, int completed,
                              String reportSha256) {
        return "schema\t" + SCHEMA + "\n"
                + "status\t" + status + "\n"
                + "run_id\t" + token.runId() + "\n"
                + "build_git_sha\t" + token.buildGitSha() + "\n"
                + "runtime_implementation_sha256\t" + token.runtimeImplementationSha256() + "\n"
                + "frozen_dy_enabled\t" + token.frozenDyEnabled() + "\n"
                + "catalog_hash\t" + token.catalogHash() + "\n"
                + "runtime_block_items\t" + token.itemCount() + "\n"
                + "expected_rows\t" + token.expectedRows() + "\n"
                + "completed_unique_shards\t" + completed + "/" + shardCount + "\n"
                + "report_sha256\t" + reportSha256 + "\n";
    }

    private void requireActiveToken(RunToken token) throws IOException {
        if (active == null || !active.token.equals(token)) {
            throw new IOException("registry sweep stale/foreign run token");
        }
    }

    private void requireSnapshotIdentity(RunToken token,
                                         SlabRigCaseCatalog.Snapshot snapshot) throws IOException {
        if (!token.catalogHash().equals(snapshot.catalogHash())
                || token.itemCount() != snapshot.items().size()
                || token.expectedRows() != Math.multiplyExact(snapshot.items().size(), RIGS.size())) {
            throw new IOException("registry sweep token/catalog snapshot identity mismatch");
        }
    }

    private void requireShard(int shard) {
        if (shard < 0 || shard >= shardCount) {
            throw new IllegalArgumentException("shard must be 0.." + (shardCount - 1));
        }
    }

    private static void requireExactSha256(String value, String label) throws IOException {
        if (!isExactSha256(value)) {
            throw new IOException("registry sweep requires exact 64-hex " + label + " SHA-256");
        }
    }

    private static boolean isExactSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void atomicWrite(Path path, String text) throws IOException {
        atomicWrite(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void atomicWrite(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp-" + UUID.randomUUID());
        Files.write(temp, bytes,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        atomicMove(temp, path);
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
