package com.slabbed.command;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict append-only filesystem store for {@link SlabRigHangingDirectState.State}.
 *
 * <p>There is no mutable head pointer. Each owner has one contiguous immutable ledger; reconstruction
 * validates every authoritative file and derives the head from the largest contiguous sequence. Unique
 * temporary siblings are intentionally non-authoritative and ignored after a crash. Any malformed,
 * duplicated, missing, symlinked, or hash-mismatched authoritative entry blocks append and exposes only
 * a verified prefix through {@link CorruptLedgerException}.
 *
 * <p>Append takes a JVM lock and an OS {@link FileLock}, in global-then-owner order. The global lock also
 * reconstructs every other active owner in the same world/dimension before publication, preventing a
 * second active page or cross-owner UUID/reservation allocation. Publication is a forced unique sibling
 * followed by a same-directory no-replace hard link, exact readback, and directory fsync.
 */
public final class SlabRigHangingDirectStateStore {

    public static final String DIRECTORY = "hanging-direct-active";
    public static final String WORLD_ID_SCHEMA = "slabbed-rig-world-id-v1";

    private static final String LEDGERS = "ledgers";
    private static final String LOCKS = "locks";
    private static final String ARTIFACTS = "artifacts";
    private static final Pattern OWNER_KEY = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern STATE_FILE = Pattern.compile(
            "state-([0-9]{20})-([0-9a-f]{64})\\.tsv");
    private static final Pattern STATE_TEMP = Pattern.compile(
            "\\.state-[0-9]{20}-[0-9a-f]{64}\\.tsv\\.tmp-[0-9a-f-]{36}");
    private static final Pattern ARTIFACT_FILE = Pattern.compile("artifact-([0-9a-f]{64})\\.bin");
    private static final Pattern ARTIFACT_TEMP = Pattern.compile(
            "\\.artifact-[0-9a-f]{64}\\.bin\\.tmp-[0-9a-f-]{36}");
    private static final ConcurrentHashMap<String, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

    private final Path root;
    private final ConcurrentHashMap<String, CachedOwner> verifiedOwnerCache = new ConcurrentHashMap<>();
    private final AtomicLong verifiedPrefixReuseCount = new AtomicLong();
    private final AtomicLong directorySyncCount = new AtomicLong();

    public SlabRigHangingDirectStateStore(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    public static SlabRigHangingDirectStateStore production() {
        return new SlabRigHangingDirectStateStore(
                FabricLoader.getInstance().getGameDir().resolve("slabbed-rig").resolve(DIRECTORY));
    }

    public Path root() {
        return root;
    }

    /** Monotonic read-only diagnostic used to prove the strict verified-prefix fast path. */
    public long verifiedPrefixReuseCount() {
        return verifiedPrefixReuseCount.get();
    }

    /** Monotonic read-only diagnostic proving which successful store publications forced a directory. */
    public long directorySyncCount() {
        return directorySyncCount.get();
    }

    public Path ledgerPath(SlabRigHangingDirectState.Owner owner) {
        return root.resolve(LEDGERS).resolve(owner.key());
    }

    public Path statePath(SlabRigHangingDirectState.State state) {
        return ledgerPath(state.owner()).resolve(stateFileName(state.sequence(), state.stateHash()));
    }

    public Path artifactPath(String hash) {
        requireSha256(hash, "artifact hash");
        return root.resolve(ARTIFACTS).resolve("artifact-" + hash + ".bin");
    }

    /**
     * Content-addressed evidence publication used before a state links PLANNED/IMMEDIATE/FINAL/CLEARED.
     * Identical bytes are idempotent; different bytes can never replace an existing hash path.
     */
    public WrittenArtifact writeArtifact(byte[] canonicalBytes) throws IOException {
        Objects.requireNonNull(canonicalBytes, "canonicalBytes");
        byte[] bytes = canonicalBytes.clone();
        String hash = SlabRigHangingDirectState.sha256(bytes);
        prepareRoot();
        Path directory = prepareOwnedDirectory(root.resolve(ARTIFACTS), root.toRealPath());
        Path target = artifactPath(hash);
        ReentrantLock jvm = jvmLock("global");
        jvm.lock();
        try (LockedFile ignored = lockFile(globalLockPath())) {
            publishStoreNoReplace(directory, target, bytes, ARTIFACT_TEMP);
            return new WrittenArtifact(target, hash, bytes.length);
        } finally {
            jvm.unlock();
        }
    }

    public WrittenArtifact writeArtifact(String canonicalText) throws IOException {
        return writeArtifact(canonicalText.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Publishes a related evidence batch under one global lock and one final directory sync.
     * Each content-addressed file still receives its own forced unique producer, no-replace hard link,
     * and exact readback. A partial failure can therefore leave only unreferenced idempotent artifacts;
     * no caller may append the state that links them until this method returns successfully.
     */
    public List<WrittenArtifact> writeArtifacts(List<String> canonicalTexts) throws IOException {
        Objects.requireNonNull(canonicalTexts, "canonicalTexts");
        List<byte[]> orderedBytes = new ArrayList<>(canonicalTexts.size());
        LinkedHashMap<String, byte[]> unique = new LinkedHashMap<>();
        for (String text : canonicalTexts) {
            byte[] bytes = Objects.requireNonNull(text, "canonical artifact text")
                    .getBytes(StandardCharsets.UTF_8);
            String hash = SlabRigHangingDirectState.sha256(bytes);
            byte[] collision = unique.putIfAbsent(hash, bytes);
            if (collision != null && !java.util.Arrays.equals(collision, bytes)) {
                throw new IOException("SHA-256 collision inside direct artifact batch " + hash);
            }
            orderedBytes.add(bytes);
        }
        if (orderedBytes.isEmpty()) {
            return List.of();
        }

        prepareRoot();
        Path directory = prepareOwnedDirectory(root.resolve(ARTIFACTS), root.toRealPath());
        ReentrantLock jvm = jvmLock("global");
        jvm.lock();
        try (LockedFile ignored = lockFile(globalLockPath())) {
            publishArtifactBatchNoReplace(directory, unique);
            List<WrittenArtifact> result = new ArrayList<>(orderedBytes.size());
            for (byte[] bytes : orderedBytes) {
                String hash = SlabRigHangingDirectState.sha256(bytes);
                result.add(new WrittenArtifact(artifactPath(hash), hash, bytes.length));
            }
            return List.copyOf(result);
        } finally {
            jvm.unlock();
        }
    }

    /** Exact readback. The requested hash is never trusted without recomputing the bytes. */
    public byte[] readArtifact(String hash) throws IOException {
        requireSha256(hash, "artifact hash");
        Path path = artifactPath(hash);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("missing/non-regular direct artifact " + path);
        }
        byte[] bytes = Files.readAllBytes(path);
        if (!hash.equals(SlabRigHangingDirectState.sha256(bytes))) {
            throw new IOException("direct artifact hash/readback mismatch " + path);
        }
        return bytes;
    }

    /** CAS append. Passing null means this must be the first state in a new owner ledger. */
    public WrittenState append(SlabRigHangingDirectState.State expectedPrevious,
                               SlabRigHangingDirectState.State candidate) throws IOException {
        SlabRigHangingDirectState.validateSelf(candidate);
        prepareRoot();
        ReentrantLock globalJvm = jvmLock("global");
        ReentrantLock ownerJvm = jvmLock(candidate.ownerKey());
        globalJvm.lock();
        ownerJvm.lock();
        try (LockedFile ignoredGlobal = lockFile(globalLockPath());
             LockedFile ignoredOwner = lockFile(ownerLockPath(candidate.ownerKey()))) {
            try {
                CachedOwner verifiedOwner = loadForExpected(candidate.owner(), expectedPrevious);
                Reconstruction reconstruction = verifiedOwner.reconstruction();
                Set<String> verifiedArtifacts = new HashSet<>(verifiedOwner.verifiedArtifacts());
                Map<String, VerifiedCell> verifiedCells =
                        new HashMap<>(verifiedOwner.verifiedCells());
                SlabRigHangingDirectState.State latest = reconstruction.latestOrNull();
                if (latest != null && latest.stateHash().equals(candidate.stateHash())) {
                    verifyStateFile(statePath(candidate), candidate);
                    verifyLinkedArtifacts(candidate, verifiedArtifacts, verifiedCells);
                    validateGlobalAllocation(candidate, reconstruction);
                    // A prior no-replace link may be visible even if its cleanup/directory sync
                    // threw. An idempotent retry may report success only after repairing the exact
                    // ledger directory's namespace durability.
                    forceStoreDirectory(statePath(candidate).getParent());
                    return writtenState(candidate, true);
                }
                if (expectedPrevious == null) {
                    if (latest != null || candidate.sequence() != 0
                            || !SlabRigHangingDirectState.NO_PREDECESSOR.equals(
                            candidate.predecessorHash())) {
                        throw new IOException("direct-state first append collided with an existing ledger head");
                    }
                } else {
                    SlabRigHangingDirectState.validateSelf(expectedPrevious);
                    if (latest == null || !latest.stateHash().equals(expectedPrevious.stateHash())) {
                        throw new IOException("direct-state CAS head mismatch; expected="
                                + expectedPrevious.stateHash() + " actual="
                                + (latest == null ? "NONE" : latest.stateHash()));
                    }
                    SlabRigHangingDirectState.validateTransition(latest, candidate);
                }
                verifyLinkedArtifacts(candidate, verifiedArtifacts, verifiedCells);
                validateGlobalAllocation(candidate, reconstruction);

                byte[] bytes = SlabRigHangingDirectState.canonicalTsv(candidate)
                        .getBytes(StandardCharsets.UTF_8);
                final SlabRigHangingDirectState.State semanticReadback;
                try {
                    semanticReadback = SlabRigHangingDirectState.parse(bytes);
                } catch (IllegalArgumentException malformed) {
                    throw new IOException("direct-state candidate bytes fail semantic readback", malformed);
                }
                if (!semanticReadback.equals(candidate)) {
                    throw new IOException("direct-state candidate changes under UTF-8 semantic readback");
                }
                Path directory = prepareOwnedDirectory(ledgerPath(candidate.owner()),
                        root.resolve(LEDGERS).toRealPath());
                Path target = statePath(candidate);
                publishStoreNoReplace(directory, target, bytes, STATE_TEMP);
                verifyStateFile(target, candidate);
                List<SlabRigHangingDirectState.State> states =
                        new ArrayList<>(reconstruction.states());
                states.add(candidate);
                rememberExtended(verifiedOwner, new Reconstruction(candidate.owner(), states,
                        reconstruction.ignoredTemporaryFiles()), verifiedArtifacts, verifiedCells);
                return writtenState(candidate, false);
            } catch (IOException | RuntimeException | Error failure) {
                verifiedOwnerCache.remove(candidate.ownerKey());
                throw failure;
            }
        } finally {
            ownerJvm.unlock();
            globalJvm.unlock();
        }
    }

    /** Convenience append whose predecessor is encoded by the candidate and rechecked under lock. */
    public WrittenState append(SlabRigHangingDirectState.State candidate) throws IOException {
        Reconstruction current = reconstruct(candidate.owner());
        return append(current.latestOrNull(), candidate);
    }

    /** Full authoritative-chain reconstruction in a fresh object/process context. */
    public Reconstruction reconstruct(SlabRigHangingDirectState.Owner owner) throws IOException {
        Objects.requireNonNull(owner, "owner");
        Path directory = ledgerPath(owner);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            Reconstruction empty = new Reconstruction(owner, List.of(), List.of());
            rememberVerified(empty, Set.of(), Map.of());
            return empty;
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
            throw new IOException("direct owner ledger is not a real directory " + directory);
        }
        return reconstructDirectory(owner.key(), owner, directory);
    }

    /**
     * Revalidates the exact immutable ledger shape against a previously verified in-process prefix.
     * A cache miss or any metadata disagreement falls back to full byte-for-byte reconstruction.
     */
    public Reconstruction verifyCurrent(SlabRigHangingDirectState.Owner owner,
                                        SlabRigHangingDirectState.State expected) throws IOException {
        Objects.requireNonNull(owner, "owner");
        prepareRoot();
        ReentrantLock globalJvm = jvmLock("global");
        ReentrantLock ownerJvm = jvmLock(owner.key());
        globalJvm.lock();
        ownerJvm.lock();
        try (LockedFile ignoredGlobal = lockFile(globalLockPath());
             LockedFile ignoredOwner = lockFile(ownerLockPath(owner.key()))) {
            return loadForExpected(owner, expected).reconstruction();
        } finally {
            ownerJvm.unlock();
            globalJvm.unlock();
        }
    }

    /**
     * Read every owner chain. Intended for server-start ownership reconstruction and allocation audit;
     * it never creates a path or mutates a ledger.
     */
    public List<Reconstruction> reconstructAll() throws IOException {
        return reconstructAll(null);
    }

    private List<Reconstruction> reconstructAll(Reconstruction knownOwner) throws IOException {
        Path ledgers = root.resolve(LEDGERS);
        if (!Files.exists(ledgers, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        if (!Files.isDirectory(ledgers, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(ledgers)) {
            throw new IOException("direct ledgers root is not a real directory " + ledgers);
        }
        List<Path> owners;
        try (var stream = Files.list(ledgers)) {
            owners = stream.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        List<Reconstruction> result = new ArrayList<>();
        for (Path directory : owners) {
            String key = directory.getFileName().toString();
            if (!OWNER_KEY.matcher(key).matches()
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(directory)) {
                throw new IOException("invalid direct owner ledger entry " + directory);
            }
            if (knownOwner != null && knownOwner.owner() != null
                    && knownOwner.owner().key().equals(key)) {
                result.add(knownOwner);
            } else {
                CachedOwner cached = verifiedOwnerCache.get(key);
                if (cached != null) {
                    CachedOwner extended = extendCachedDirectory(key, cached, directory);
                    if (extended != null) {
                        verifiedPrefixReuseCount.incrementAndGet();
                        result.add(extended.reconstruction());
                        continue;
                    }
                }
                result.add(reconstructDirectory(key, null, directory));
            }
        }
        return List.copyOf(result);
    }

    /** Strict create-if-absent world identity, compatible with RIG-2's existing world-key schema. */
    public static String createWorldKey(Path worldRoot) throws IOException {
        Path normalized = validateWorldRoot(worldRoot);
        Path data = prepareOwnedDirectory(normalized.resolve("data"), normalized.toRealPath());
        Path target = data.resolve("slabbed-rig-world-id.tsv");
        boolean existing = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (!existing) {
            String uuid = UUID.randomUUID().toString();
            byte[] bytes = worldIdentityBytes(uuid);
            publishNoReplace(data, target, bytes, Pattern.compile(
                    "\\.slabbed-rig-world-id\\.tsv\\.tmp-[0-9a-f-]{36}"));
        }
        String worldKey = readWorldKey(normalized);
        if (existing) {
            // Repair a prior create attempt whose link became visible before its directory sync
            // failed. The non-creating read path deliberately remains read-only.
            forceDirectory(data);
        }
        return worldKey;
    }

    /** Strict non-creating read for status/resume/clear. */
    public static String readWorldKey(Path worldRoot) throws IOException {
        Path normalized = validateWorldRoot(worldRoot);
        Path target = normalized.resolve("data").resolve("slabbed-rig-world-id.tsv");
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw new IOException("missing/non-regular Slabbed world identity " + target);
        }
        String text = Files.readString(target, StandardCharsets.UTF_8);
        String[] lines = text.split("\\n", -1);
        if (lines.length != 3 || !lines[2].isEmpty()
                || !lines[0].equals("schema\t" + WORLD_ID_SCHEMA)
                || !lines[1].startsWith("uuid\t")) {
            throw new IOException("malformed Slabbed world identity " + target);
        }
        String uuid = lines[1].substring("uuid\t".length());
        try {
            if (!UUID.fromString(uuid).toString().equals(uuid)
                    || !text.equals(new String(worldIdentityBytes(uuid), StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("non-canonical world UUID/bytes");
            }
        } catch (IllegalArgumentException failure) {
            throw new IOException("invalid Slabbed world identity " + target, failure);
        }
        return SlabRigCaseArtifacts.worldKey(normalized, uuid);
    }

    private Reconstruction reconstructDirectory(String ownerKey,
                                                SlabRigHangingDirectState.Owner expectedOwner,
                                                Path directory) throws IOException {
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        Map<Long, Path> authoritative = new LinkedHashMap<>();
        List<Path> ignoredTemps = new ArrayList<>();
        for (Path path : files) {
            String name = path.getFileName().toString();
            if (STATE_TEMP.matcher(name).matches()) {
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw corrupt("temporary-looking ledger entry is not a regular file " + path,
                            expectedOwner, List.of(), ignoredTemps);
                }
                ignoredTemps.add(path);
                continue;
            }
            Matcher matcher = STATE_FILE.matcher(name);
            if (!matcher.matches() || Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw corrupt("unknown/non-regular authoritative ledger entry " + path,
                        expectedOwner, List.of(), ignoredTemps);
            }
            long sequence;
            try {
                sequence = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException failure) {
                throw new IOException("invalid direct-state sequence filename " + path, failure);
            }
            if (authoritative.put(sequence, path) != null) {
                throw corrupt("duplicate direct-state sequence " + sequence,
                        expectedOwner, List.of(), ignoredTemps);
            }
        }

        List<SlabRigHangingDirectState.State> verified = new ArrayList<>();
        Set<String> verifiedArtifactHashes = new HashSet<>();
        Map<String, VerifiedCell> verifiedCells = new HashMap<>();
        SlabRigHangingDirectState.Owner owner = expectedOwner;
        for (long expectedSequence = 0; expectedSequence < authoritative.size(); expectedSequence++) {
            Path path = authoritative.get(expectedSequence);
            if (path == null) {
                throw corrupt("missing direct-state sequence " + expectedSequence,
                        owner, verified, ignoredTemps);
            }
            try {
                SlabRigHangingDirectState.State state = SlabRigHangingDirectState.parse(
                        Files.readAllBytes(path));
                Matcher name = STATE_FILE.matcher(path.getFileName().toString());
                if (!name.matches() || state.sequence() != expectedSequence
                        || !state.stateHash().equals(name.group(2))
                        || !state.ownerKey().equals(ownerKey)) {
                    throw new IllegalArgumentException("state filename/owner identity mismatch");
                }
                if (owner == null) {
                    owner = state.owner();
                } else if (!owner.equals(state.owner())) {
                    throw new IllegalArgumentException("owner tuple changed inside ledger");
                }
                if (!verified.isEmpty()) {
                    SlabRigHangingDirectState.validateTransition(verified.getLast(), state);
                }
                verifyLinkedArtifacts(state, verifiedArtifactHashes, verifiedCells);
                verified.add(state);
            } catch (CorruptLedgerException failure) {
                throw failure;
            } catch (IOException | RuntimeException failure) {
                throw corrupt("invalid direct-state entry " + path + ": " + failure.getMessage(),
                        owner, verified, ignoredTemps, failure);
            }
        }
        if (owner == null) {
            if (!authoritative.isEmpty()) {
                throw corrupt("could not derive owner from non-empty ledger", expectedOwner,
                        verified, ignoredTemps);
            }
            owner = expectedOwner;
        }
        Reconstruction reconstruction = new Reconstruction(owner, verified, ignoredTemps);
        rememberVerified(reconstruction, verifiedArtifactHashes, verifiedCells);
        return reconstruction;
    }

    /**
     * Verifies an immutable cached prefix and parses only newly appended authoritative states.
     * Returning {@code null} means the prefix metadata changed and requires full reconstruction.
     */
    private CachedOwner extendCachedDirectory(String ownerKey, CachedOwner cached, Path directory)
            throws IOException {
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        Map<Long, Path> authoritative = new LinkedHashMap<>();
        List<Path> ignoredTemps = new ArrayList<>();
        for (Path path : files) {
            String name = path.getFileName().toString();
            if (STATE_TEMP.matcher(name).matches()) {
                if (Files.isSymbolicLink(path)
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw corrupt("temporary-looking ledger entry is not a regular file " + path,
                            cached.reconstruction().owner(), cached.reconstruction().states(),
                            ignoredTemps);
                }
                ignoredTemps.add(path);
                continue;
            }
            Matcher matcher = STATE_FILE.matcher(name);
            if (!matcher.matches() || Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw corrupt("unknown/non-regular authoritative ledger entry " + path,
                        cached.reconstruction().owner(), cached.reconstruction().states(),
                        ignoredTemps);
            }
            long sequence;
            try {
                sequence = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException failure) {
                throw new IOException("invalid direct-state sequence filename " + path, failure);
            }
            if (authoritative.put(sequence, path) != null) {
                throw corrupt("duplicate direct-state sequence " + sequence,
                        cached.reconstruction().owner(), cached.reconstruction().states(),
                        ignoredTemps);
            }
        }

        List<SlabRigHangingDirectState.State> verified =
                new ArrayList<>(cached.reconstruction().states());
        if (authoritative.size() < verified.size()) {
            return null;
        }
        for (int sequence = 0; sequence < verified.size(); sequence++) {
            SlabRigHangingDirectState.State expected = verified.get(sequence);
            Path path = authoritative.get((long) sequence);
            String expectedName = statePath(expected).getFileName().toString();
            FileStamp priorStamp = cached.stateFiles().get(expectedName);
            if (path == null || !path.getFileName().toString().equals(expectedName)
                    || priorStamp == null) {
                return null;
            }
            FileStamp current = fileStamp(path);
            if (!priorStamp.equals(current)) {
                return null;
            }
            if (!current.hasUnforgeableChangeSignal()) {
                verifyStateFile(path, expected);
            }
        }
        for (Map.Entry<String, FileStamp> entry : cached.artifactFiles().entrySet()) {
            Path path = artifactPath(entry.getKey());
            FileStamp current;
            try {
                current = fileStamp(path);
            } catch (IOException missingOrChanged) {
                return null;
            }
            if (!entry.getValue().equals(current)) {
                return null;
            }
            if (!current.hasUnforgeableChangeSignal()) {
                readArtifact(entry.getKey());
            }
        }
        if (!cellProofsMatchState(cached.reconstruction().latestOrNull(),
                cached.verifiedCells())) {
            return null;
        }
        if (!verified.isEmpty()) {
            verifyStateFile(authoritative.get((long) verified.size() - 1), verified.getLast());
        }

        Set<String> verifiedArtifactHashes = new HashSet<>(cached.verifiedArtifacts());
        Map<String, VerifiedCell> verifiedCells = new HashMap<>(cached.verifiedCells());
        SlabRigHangingDirectState.Owner owner = cached.reconstruction().owner();
        int tailStart = verified.size();
        List<ParsedTailState> parsedTail = java.util.stream.IntStream
                .range(tailStart, authoritative.size()).parallel().mapToObj(sequence -> {
                    Path path = authoritative.get((long) sequence);
                    if (path == null) {
                        return new ParsedTailState(sequence, null, null,
                                new IOException("missing direct-state sequence " + sequence));
                    }
                    try {
                        return new ParsedTailState(sequence, path,
                                SlabRigHangingDirectState.parse(Files.readAllBytes(path)), null);
                    } catch (IOException | RuntimeException failure) {
                        return new ParsedTailState(sequence, path, null, failure);
                    }
                }).toList();
        for (ParsedTailState parsed : parsedTail) {
            long expectedSequence = parsed.sequence();
            Path path = parsed.path();
            if (parsed.failure() != null) {
                throw corrupt("invalid appended direct-state entry " + path + ": "
                                + parsed.failure().getMessage(), owner, verified, ignoredTemps,
                        parsed.failure());
            }
            try {
                SlabRigHangingDirectState.State state = parsed.state();
                Matcher name = STATE_FILE.matcher(path.getFileName().toString());
                if (!name.matches() || state.sequence() != expectedSequence
                        || !state.stateHash().equals(name.group(2))
                        || !state.ownerKey().equals(ownerKey) || !owner.equals(state.owner())) {
                    throw new IllegalArgumentException("appended state filename/owner identity mismatch");
                }
                if (!verified.isEmpty()) {
                    SlabRigHangingDirectState.validateTransition(verified.getLast(), state);
                }
                verifyLinkedArtifacts(state, verifiedArtifactHashes, verifiedCells);
                verified.add(state);
            } catch (CorruptLedgerException failure) {
                throw failure;
            } catch (IOException | RuntimeException failure) {
                throw corrupt("invalid appended direct-state entry " + path + ": "
                                + failure.getMessage(), owner, verified, ignoredTemps, failure);
            }
        }
        Reconstruction reconstruction = new Reconstruction(owner, verified, ignoredTemps);
        rememberExtended(cached, reconstruction, verifiedArtifactHashes, verifiedCells);
        return verifiedOwnerCache.get(ownerKey);
    }

    private CachedOwner loadForExpected(SlabRigHangingDirectState.Owner owner,
                                        SlabRigHangingDirectState.State expected) throws IOException {
        CachedOwner cached = verifiedOwnerCache.get(owner.key());
        if (cached != null && cached.reconstruction().owner().equals(owner)
                && sameHead(cached.reconstruction().latestOrNull(), expected)
                && cachedShapeMatches(cached)) {
            verifiedPrefixReuseCount.incrementAndGet();
            return cached;
        }
        Reconstruction reconstructed = reconstruct(owner);
        CachedOwner refreshed = verifiedOwnerCache.get(owner.key());
        if (refreshed == null || !refreshed.reconstruction().equals(reconstructed)) {
            throw new IOException("direct owner verification cache was not established");
        }
        return refreshed;
    }

    private boolean cachedShapeMatches(CachedOwner cached) {
        Reconstruction reconstruction = cached.reconstruction();
        Path directory = ledgerPath(reconstruction.owner());
        try {
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                return reconstruction.states().isEmpty();
            }
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(directory)) {
                return false;
            }
            Set<String> expectedNames = new HashSet<>();
            for (SlabRigHangingDirectState.State state : reconstruction.states()) {
                expectedNames.add(statePath(state).getFileName().toString());
            }
            Set<String> actualNames = new HashSet<>();
            try (var stream = Files.list(directory)) {
                for (Path path : stream.toList()) {
                    String name = path.getFileName().toString();
                    if (STATE_TEMP.matcher(name).matches()) {
                        if (Files.isSymbolicLink(path)
                                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                            return false;
                        }
                    } else if (STATE_FILE.matcher(name).matches()) {
                        if (Files.isSymbolicLink(path)
                                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                            return false;
                        }
                        actualNames.add(name);
                    } else {
                        return false;
                    }
                }
            }
            if (!actualNames.equals(expectedNames)) {
                return false;
            }
            for (Map.Entry<String, FileStamp> entry : cached.stateFiles().entrySet()) {
                Path path = directory.resolve(entry.getKey());
                FileStamp current = fileStamp(path);
                if (!entry.getValue().equals(current)) {
                    return false;
                }
                if (!current.hasUnforgeableChangeSignal()) {
                    SlabRigHangingDirectState.State expected = reconstruction.states().stream()
                            .filter(state -> statePath(state).getFileName().toString()
                                    .equals(entry.getKey()))
                            .findFirst().orElseThrow();
                    verifyStateFile(path, expected);
                }
            }
            for (Map.Entry<String, FileStamp> entry : cached.artifactFiles().entrySet()) {
                Path path = artifactPath(entry.getKey());
                FileStamp current = fileStamp(path);
                if (!entry.getValue().equals(current)) {
                    return false;
                }
                if (!current.hasUnforgeableChangeSignal()) {
                    readArtifact(entry.getKey());
                }
            }
            if (!cellProofsMatchState(reconstruction.latestOrNull(), cached.verifiedCells())) {
                return false;
            }
            SlabRigHangingDirectState.State latest = reconstruction.latestOrNull();
            if (latest != null) {
                verifyStateFile(statePath(latest), latest);
            }
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private void rememberVerified(Reconstruction reconstruction,
                                  Set<String> verifiedArtifacts,
                                  Map<String, VerifiedCell> verifiedCells) throws IOException {
        if (reconstruction.owner() == null) {
            return;
        }
        Map<String, FileStamp> stateFiles = new LinkedHashMap<>();
        for (SlabRigHangingDirectState.State state : reconstruction.states()) {
            Path path = statePath(state);
            stateFiles.put(path.getFileName().toString(), fileStamp(path));
        }
        Map<String, FileStamp> artifactFiles = new LinkedHashMap<>();
        for (String hash : verifiedArtifacts) {
            artifactFiles.put(hash, fileStamp(artifactPath(hash)));
        }
        verifiedOwnerCache.put(reconstruction.owner().key(), new CachedOwner(reconstruction,
                Set.copyOf(verifiedArtifacts), Map.copyOf(stateFiles), Map.copyOf(artifactFiles),
                Map.copyOf(verifiedCells)));
    }

    private void rememberExtended(CachedOwner previous, Reconstruction reconstruction,
                                  Set<String> verifiedArtifacts,
                                  Map<String, VerifiedCell> verifiedCells) throws IOException {
        Map<String, FileStamp> stateFiles = new LinkedHashMap<>(previous.stateFiles());
        for (SlabRigHangingDirectState.State state : reconstruction.states()) {
            Path path = statePath(state);
            String name = path.getFileName().toString();
            if (!stateFiles.containsKey(name)) {
                stateFiles.put(name, fileStamp(path));
            }
        }
        Map<String, FileStamp> artifactFiles = new LinkedHashMap<>(previous.artifactFiles());
        for (String hash : verifiedArtifacts) {
            if (!artifactFiles.containsKey(hash)) {
                artifactFiles.put(hash, fileStamp(artifactPath(hash)));
            }
        }
        verifiedOwnerCache.put(reconstruction.owner().key(), new CachedOwner(reconstruction,
                Set.copyOf(verifiedArtifacts), Map.copyOf(stateFiles), Map.copyOf(artifactFiles),
                Map.copyOf(verifiedCells)));
    }

    private static boolean cellProofsMatchState(SlabRigHangingDirectState.State latest,
                                                Map<String, VerifiedCell> verifiedCells) {
        if (latest == null) {
            return verifiedCells.isEmpty();
        }
        Map<SlabRigHangingDirectState.Position,
                SlabRigHangingDirectState.AttachmentOwnership> byPosition = new HashMap<>();
        latest.authoredAttachments().forEach(attachment ->
                byPosition.put(attachment.pos(), attachment));
        for (SlabRigHangingDirectState.CellOwnership cell : latest.authoredCells()) {
            SlabRigHangingDirectState.AttachmentOwnership attachment = byPosition.get(cell.pos());
            VerifiedCell proof = verifiedCells.get(cell.fingerprint());
            if (attachment == null || proof == null || !proof.position().equals(cell.pos())
                    || !proof.attachmentFingerprint().equals(attachment.fingerprint())) {
                return false;
            }
        }
        // Historical proofs intentionally remain a superset across CLEARED -> PLANNED runs.
        return true;
    }

    private static boolean sameHead(SlabRigHangingDirectState.State actual,
                                    SlabRigHangingDirectState.State expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        return actual.owner().equals(expected.owner())
                && actual.sequence() == expected.sequence()
                && actual.stateHash().equals(expected.stateHash());
    }

    private static FileStamp fileStamp(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("cached direct-state file became a symlink " + path);
        }
        BasicFileAttributes attributes = Files.readAttributes(path,
                BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw new IOException("cached direct-state file is not regular " + path);
        }
        return new FileStamp(String.valueOf(attributes.fileKey()), attributes.size(),
                attributes.lastModifiedTime(), unixChangeTime(path));
    }

    private static FileTime unixChangeTime(Path path) throws IOException {
        try {
            Object value = Files.getAttribute(path, "unix:ctime", LinkOption.NOFOLLOW_LINKS);
            if (!(value instanceof FileTime changeTime)) {
                throw new IOException("unix:ctime did not return FileTime for " + path);
            }
            return changeTime;
        } catch (UnsupportedOperationException | IllegalArgumentException unsupported) {
            // Providers without an unforgeable metadata signal use strict byte verification on
            // every cache hit instead of trusting fileKey/size/mtime alone.
            return null;
        }
    }

    private void validateGlobalAllocation(SlabRigHangingDirectState.State candidate,
                                          Reconstruction knownOwner) throws IOException {
        if (candidate.phase() == SlabRigHangingDirectState.Phase.CLEARED) {
            return;
        }
        for (Reconstruction other : reconstructAll(knownOwner)) {
            SlabRigHangingDirectState.State latest = other.latestOrNull();
            if (latest == null || latest.ownerKey().equals(candidate.ownerKey())
                    || latest.phase() == SlabRigHangingDirectState.Phase.CLEARED) {
                continue;
            }
            if (latest.owner().worldKey().equals(candidate.owner().worldKey())
                    && latest.owner().dimension().equals(candidate.owner().dimension())) {
                Set<SlabRigHangingDirectState.Position> overlap = new HashSet<>(latest.reservedCells());
                overlap.retainAll(candidate.reservedCells());
                throw new IOException("another owner already has an active direct page in this world/dimension"
                        + (overlap.isEmpty() ? "" : "; reservation overlap=" + overlap.iterator().next()));
            }
            Set<UUID> duplicateUuids = new HashSet<>(latest.entityUuidSet());
            duplicateUuids.retainAll(candidate.entityUuidSet());
            if (!duplicateUuids.isEmpty()) {
                throw new IOException("cross-owner entity UUID allocation collision "
                        + duplicateUuids.iterator().next());
            }
        }
    }

    private void verifyLinkedArtifacts(SlabRigHangingDirectState.State state,
                                       Set<String> verifiedHashes,
                                       Map<String, VerifiedCell> verifiedCells) throws IOException {
        for (String hash : linkedArtifactHashes(state)) {
            verifyArtifactOnce(hash, verifiedHashes);
        }
        Map<SlabRigHangingDirectState.Position,
                SlabRigHangingDirectState.AttachmentOwnership> attachments = new HashMap<>();
        state.authoredAttachments().forEach(attachment ->
                attachments.put(attachment.pos(), attachment));
        for (SlabRigHangingDirectState.CellOwnership cell : state.authoredCells()) {
            SlabRigHangingDirectState.AttachmentOwnership attachment = attachments.get(cell.pos());
            if (attachment == null) {
                throw new IOException("cell evidence lacks same-position attachment receipt "
                        + cell.pos());
            }
            VerifiedCell proven = verifiedCells.get(cell.fingerprint());
            if (proven != null) {
                if (!proven.position().equals(cell.pos())
                        || !proven.attachmentFingerprint().equals(attachment.fingerprint())) {
                    throw new IOException("verified cell evidence hash was aliased to a new position/attachment "
                            + cell.pos());
                }
                continue;
            }
            byte[] canonical = readArtifact(cell.fingerprint());
            verifiedHashes.add(cell.fingerprint());
            try {
                SlabRigHangingDirectEvidence.verifyCellAndAttachmentArtifact(
                        cell.pos().toBlockPos(), cell.fingerprint(), attachment.fingerprint(),
                        canonical);
                VerifiedCell conflict = verifiedCells.putIfAbsent(cell.fingerprint(),
                        new VerifiedCell(cell.pos(), attachment.fingerprint()));
                if (conflict != null && (!conflict.position().equals(cell.pos())
                        || !conflict.attachmentFingerprint().equals(attachment.fingerprint()))) {
                    throw new IllegalArgumentException("cell evidence proof collided after verification");
                }
            } catch (IllegalArgumentException failure) {
                throw new IOException("cell/attachment evidence derivation failed at "
                        + cell.pos(), failure);
            }
        }
    }

    private static List<String> linkedArtifactHashes(SlabRigHangingDirectState.State state) {
        List<String> hashes = new ArrayList<>();
        hashes.add(state.artifacts().planned());
        for (String hash : List.of(state.artifacts().immediate(), state.artifacts().finalArtifact(),
                state.artifacts().cleared())) {
            if (!SlabRigHangingDirectState.NO_VALUE.equals(hash)) {
                hashes.add(hash);
            }
        }
        for (SlabRigHangingDirectState.CaseState entry : state.cases()) {
            if (!SlabRigHangingDirectState.NO_VALUE.equals(entry.immediateObservationId())) {
                hashes.add(entry.immediateObservationId());
            }
        }
        for (SlabRigHangingDirectState.EntityOwnership entity : state.entities()) {
            hashes.add(entity.evidenceArtifact());
            if (entity.role() == SlabRigHangingDirectState.EntityRole.PAINTING
                    && entity.disposition()
                    == SlabRigHangingDirectState.EntityDisposition.REMOVED) {
                hashes.add(entity.removalArtifact());
            }
        }
        return hashes;
    }

    private byte[] verifyArtifactOnce(String hash, Set<String> verifiedHashes) throws IOException {
        if (verifiedHashes.add(hash)) {
            return readArtifact(hash);
        }
        return null;
    }

    private void verifyStateFile(Path path, SlabRigHangingDirectState.State expected) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("direct-state target is not a regular owned file " + path);
        }
        SlabRigHangingDirectState.State actual;
        try {
            actual = SlabRigHangingDirectState.parse(Files.readAllBytes(path));
        } catch (RuntimeException failure) {
            throw new IOException("direct-state readback failed " + path, failure);
        }
        if (!actual.equals(expected)) {
            throw new IOException("direct-state readback differs from candidate " + path);
        }
    }

    private WrittenState writtenState(SlabRigHangingDirectState.State state, boolean existing) {
        byte[] bytes = SlabRigHangingDirectState.canonicalTsv(state).getBytes(StandardCharsets.UTF_8);
        return new WrittenState(statePath(state), state.stateHash(), bytes.length, existing);
    }

    private void prepareRoot() throws IOException {
        prepareOwnedDirectory(root, null);
        Path rootReal = root.toRealPath();
        prepareOwnedDirectory(root.resolve(LOCKS), rootReal);
        prepareOwnedDirectory(root.resolve(LEDGERS), rootReal);
        prepareOwnedDirectory(root.resolve(ARTIFACTS), rootReal);
    }

    private static Path prepareOwnedDirectory(Path path, Path expectedParentReal) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))) {
            throw new IOException("refusing non-directory/symlinked direct-state path " + path);
        }
        Files.createDirectories(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("direct-state path is not a real directory " + path);
        }
        Path real = path.toRealPath();
        if (expectedParentReal != null
                && !real.getParent().equals(expectedParentReal)) {
            throw new IOException("direct-state directory escaped its exact parent " + path);
        }
        return path;
    }

    private void publishStoreNoReplace(Path directory, Path target, byte[] bytes,
                                       Pattern acceptedTempName) throws IOException {
        publishNoReplace(directory, target, bytes, acceptedTempName, this::forceStoreDirectory);
    }

    private static void publishNoReplace(Path directory, Path target, byte[] bytes,
                                         Pattern acceptedTempName) throws IOException {
        publishNoReplace(directory, target, bytes, acceptedTempName,
                SlabRigHangingDirectStateStore::forceDirectory);
    }

    private static void publishNoReplace(Path directory, Path target, byte[] bytes,
                                         Pattern acceptedTempName,
                                         DirectorySync directorySync) throws IOException {
        if (!target.toAbsolutePath().normalize().getParent().equals(directory.toAbsolutePath().normalize())) {
            throw new IOException("content-addressed target escaped its directory " + target);
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            verifyExactRegular(target, bytes);
            // The prior producer may have linked this exact target and then failed during temp
            // cleanup or directory sync. A successful retry repairs the namespace durability.
            directorySync.force(directory);
            return;
        }
        String tempName = '.' + target.getFileName().toString() + ".tmp-" + UUID.randomUUID();
        if (!acceptedTempName.matcher(tempName).matches()) {
            throw new IOException("internal temporary filename does not match strict grammar");
        }
        Path temporary = directory.resolve(tempName);
        boolean owned = false;
        try {
            FileChannel opened = FileChannel.open(temporary,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            owned = true;
            try (FileChannel channel = opened) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            verifyExactRegular(temporary, bytes);
            try {
                Files.createLink(target, temporary);
            } catch (FileAlreadyExistsException raced) {
                verifyExactRegular(target, bytes);
            }
            verifyExactRegular(target, bytes);
        } finally {
            if (owned) {
                Files.deleteIfExists(temporary);
                // One final namespace sync durably covers both the no-replace target link and
                // removal of its non-authoritative producer; an earlier duplicate sync adds no
                // stronger post-return guarantee and doubles every single-file append stall.
                directorySync.force(directory);
            }
        }
    }

    private void publishArtifactBatchNoReplace(Path directory,
                                               LinkedHashMap<String, byte[]> unique)
            throws IOException {
        List<Path> ownedTemps = new ArrayList<>();
        boolean directoryChanged = false;
        Throwable primary = null;
        try {
            for (Map.Entry<String, byte[]> entry : unique.entrySet()) {
                Path target = artifactPath(entry.getKey());
                byte[] bytes = entry.getValue();
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    verifyExactRegular(target, bytes);
                    continue;
                }
                String tempName = '.' + target.getFileName().toString() + ".tmp-" + UUID.randomUUID();
                if (!ARTIFACT_TEMP.matcher(tempName).matches()) {
                    throw new IOException("internal artifact-batch temporary filename is invalid");
                }
                Path temporary = directory.resolve(tempName);
                try (FileChannel channel = FileChannel.open(temporary,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    ownedTemps.add(temporary);
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    channel.force(true);
                }
                verifyExactRegular(temporary, bytes);
                try {
                    Files.createLink(target, temporary);
                    directoryChanged = true;
                } catch (FileAlreadyExistsException raced) {
                    verifyExactRegular(target, bytes);
                }
                verifyExactRegular(target, bytes);
            }
        } catch (Throwable failure) {
            primary = failure;
            throw failure;
        } finally {
            IOException cleanupFailure = null;
            for (Path temporary : ownedTemps) {
                try {
                    if (Files.deleteIfExists(temporary)) {
                        directoryChanged = true;
                    }
                } catch (IOException failure) {
                    if (cleanupFailure == null) {
                        cleanupFailure = failure;
                    } else {
                        cleanupFailure.addSuppressed(failure);
                    }
                }
            }
            if (primary == null || directoryChanged) {
                try {
                    // A successful all-existing retry must still repair a prior publication whose
                    // link was visible before its directory sync failed.
                    forceStoreDirectory(directory);
                } catch (IOException failure) {
                    if (cleanupFailure == null) {
                        cleanupFailure = failure;
                    } else {
                        cleanupFailure.addSuppressed(failure);
                    }
                }
            }
            if (cleanupFailure != null) {
                if (primary != null) {
                    primary.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
        for (Map.Entry<String, byte[]> entry : unique.entrySet()) {
            verifyExactRegular(artifactPath(entry.getKey()), entry.getValue());
        }
    }

    private static void verifyExactRegular(Path path, byte[] expected) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("content-addressed path is not a regular owned file " + path);
        }
        byte[] actual = Files.readAllBytes(path);
        if (!java.util.Arrays.equals(expected, actual)) {
            throw new IOException("content-address collision/readback mismatch " + path);
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private void forceStoreDirectory(Path directory) throws IOException {
        forceDirectory(directory);
        directorySyncCount.incrementAndGet();
    }

    @FunctionalInterface
    private interface DirectorySync {
        void force(Path directory) throws IOException;
    }

    private ReentrantLock jvmLock(String suffix) {
        return JVM_LOCKS.computeIfAbsent(root + "\0" + suffix, ignored -> new ReentrantLock());
    }

    private Path globalLockPath() {
        return root.resolve(LOCKS).resolve("global.lck");
    }

    private Path ownerLockPath(String ownerKey) {
        requireSha256(ownerKey, "owner key");
        return root.resolve(LOCKS).resolve("owner-" + ownerKey + ".lck");
    }

    private static LockedFile lockFile(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))) {
            throw new IOException("refusing non-regular/symlinked lock file " + path);
        }
        FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.lock();
            return new LockedFile(channel, lock);
        } catch (Throwable failure) {
            channel.close();
            throw failure;
        }
    }

    private static Path validateWorldRoot(Path worldRoot) throws IOException {
        Path normalized = Objects.requireNonNull(worldRoot, "worldRoot").toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized)) {
            throw new IOException("world root is not a real existing directory " + normalized);
        }
        return normalized;
    }

    private static byte[] worldIdentityBytes(String uuid) {
        return ("schema\t" + WORLD_ID_SCHEMA + "\nuuid\t" + uuid + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String stateFileName(long sequence, String hash) {
        requireSha256(hash, "state hash");
        if (sequence < 0) {
            throw new IllegalArgumentException("negative state sequence");
        }
        return String.format(java.util.Locale.ROOT, "state-%020d-%s.tsv", sequence, hash);
    }

    private static void requireSha256(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be lowercase SHA-256");
        }
    }

    private static CorruptLedgerException corrupt(String message,
                                                  SlabRigHangingDirectState.Owner owner,
                                                  List<SlabRigHangingDirectState.State> verified,
                                                  List<Path> ignored) {
        return new CorruptLedgerException(message, new Reconstruction(owner, verified, ignored));
    }

    private static CorruptLedgerException corrupt(String message,
                                                  SlabRigHangingDirectState.Owner owner,
                                                  List<SlabRigHangingDirectState.State> verified,
                                                  List<Path> ignored, Throwable cause) {
        return new CorruptLedgerException(message, new Reconstruction(owner, verified, ignored), cause);
    }

    public record WrittenState(Path path, String stateHash, long byteCount, boolean alreadyExisted) {
    }

    public record WrittenArtifact(Path path, String hash, long byteCount) {
    }

    public record Reconstruction(SlabRigHangingDirectState.Owner owner,
                                 List<SlabRigHangingDirectState.State> states,
                                 List<Path> ignoredTemporaryFiles) {
        public Reconstruction {
            states = List.copyOf(states);
            ignoredTemporaryFiles = List.copyOf(ignoredTemporaryFiles);
        }

        public SlabRigHangingDirectState.State latestOrNull() {
            return states.isEmpty() ? null : states.getLast();
        }

        public boolean isEmpty() {
            return states.isEmpty();
        }
    }

    private record CachedOwner(Reconstruction reconstruction, Set<String> verifiedArtifacts,
                               Map<String, FileStamp> stateFiles,
                               Map<String, FileStamp> artifactFiles,
                               Map<String, VerifiedCell> verifiedCells) {
    }

    private record VerifiedCell(SlabRigHangingDirectState.Position position,
                                String attachmentFingerprint) {
    }

    private record ParsedTailState(int sequence, Path path,
                                   SlabRigHangingDirectState.State state, Throwable failure) {
    }

    private record FileStamp(String fileKey, long size, FileTime lastModifiedTime,
                             FileTime unixChangeTime) {
        private boolean hasUnforgeableChangeSignal() {
            return unixChangeTime != null;
        }
    }

    /** Corruption is fatal for append, but the already verified prefix remains inspectable for status. */
    public static final class CorruptLedgerException extends IOException {
        private final Reconstruction verifiedPrefix;

        private CorruptLedgerException(String message, Reconstruction verifiedPrefix) {
            super(message);
            this.verifiedPrefix = verifiedPrefix;
        }

        private CorruptLedgerException(String message, Reconstruction verifiedPrefix, Throwable cause) {
            super(message, cause);
            this.verifiedPrefix = verifiedPrefix;
        }

        public Reconstruction verifiedPrefix() {
            return verifiedPrefix;
        }
    }

    private record LockedFile(FileChannel channel, FileLock lock) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            try {
                lock.release();
            } finally {
                channel.close();
            }
        }
    }
}
