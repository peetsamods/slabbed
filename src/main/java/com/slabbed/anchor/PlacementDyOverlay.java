package com.slabbed.anchor;

import com.slabbed.Slabbed;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sequence-owned CLIENT PREDICTION OVERLAY that sits above the authoritative placement-dy store
 * without ever touching it (Slice 2i).
 *
 * <h2>The symptom this exists to remove</h2>
 * The height of a placement is decided by {@code LandingResolver} at capture time and written to the
 * chunk's {@code PLACEMENT_DY} attachment BY THE SERVER. The client runs the very same capture code
 * during vanilla's predicted use — same aim, same resolver, same number — but has nowhere to put the
 * answer, so it renders the fresh block flat until the server's attachment sync lands. The visible
 * jump is exactly {@code |dy|}: click the upper half of a lowered block and the slab appears too
 * high, then drops. This class holds the client's own answer for the few ticks in between.
 *
 * <h2>THE HARD CONSTRAINT: the client must NEVER write the client chunk's PLACEMENT_DY attachment</h2>
 * The 26.2 donor built exactly that first ("shared-map prediction") and replaced it; a direct client
 * write is a deliberately killed mutant there. The mechanism is Fabric's attachment sync: a value is
 * pushed to clients only when the SERVER's value changes ({@code AttachmentTargetsMixin.setAttached}
 * compares old against new, and {@code LevelChunkMixin.fabric_shouldTryToSync} answers
 * {@code !isClientSide}). A client-written value is therefore corrected only by coincidence — and
 * when the server REFUSES the placement it is corrected by nothing at all, leaving a permanent
 * orphan fact in that cell that no later sync will ever clear.
 *
 * <p>An OVERLAY makes refusal self-healing instead. Nothing authoritative was written, so retirement
 * is the whole recovery: the overlay retires, the read falls through to the backing store, the
 * backing store has no fact, and {@code SlabSupport.getYOffset} answers stable-flat {@code 0.0} —
 * which is precisely what the server believes about that cell.
 *
 * <h2>Why the core lives in src/main</h2>
 * This class holds the data structure and ALL of the logic and references no client class: the level
 * is an opaque {@link Object} handle supplied by the client layer, and cells are packed
 * {@code BlockPos} longs. That keeps it reachable from the server gametest harness, which is the only
 * working harness on this line. It is production-inert until the client installs its hooks (exactly
 * like {@link C3TestPhaseTrace}): with no {@link RerenderSink} and no {@link BackingFactProbe}
 * installed, nothing here is ever reached from a shipped server path. The thin client wiring —
 * level identity, chunk-unload and disconnect events, rerender scheduling, the read hook and the
 * vanilla acknowledgement mixin — lives in {@code com.slabbed.client.PlacementDyPredictionClient}.
 *
 * <h2>Read-path performance shape (non-negotiable)</h2>
 * The read is reached from {@code SlabSupport.getYOffset} for every non-air block during section
 * meshing — roughly a dozen times per block once the seam probe, the cull test and the per-quad cull
 * predicate are counted, on the chunk-mesh WORKER threads. So {@link #overlayFact} is: one volatile
 * load, one reference compare against {@link #EMPTY_OVERLAY} (the common case: no prediction in
 * flight, done), and only otherwise a {@link Arrays#binarySearch} over a sorted primitive
 * {@code long[]} whose {@link SlabAnchorAttachment.PlacementDyFact} values were all built at
 * publication time — so even an overlay HIT allocates nothing. A {@code synchronized} read here
 * would reintroduce the render-path lag class this project has already shipped twice.
 *
 * <p>The maps below are plain hash maps genuinely mutated from the client main thread while those
 * worker reads run, which is why they are never read from the worker path. Every main-thread
 * mutation happens under this class's monitor and ends with {@link #publishOverlayCellsLocked()},
 * which fills a fresh immutable snapshot and stores it into the {@code volatile} field. The volatile
 * write/read pair is the happens-before edge: a worker that sees a snapshot reference sees every
 * array element written before publication and can never observe a partially built structure.
 */
public final class PlacementDyOverlay {

    /**
     * Ticks past the vanilla acknowledgement after which a group retires even though its backing fact
     * never showed up. See {@link #onVanillaAcknowledgement} for why retirement is lazy rather than
     * exact-on-ack.
     */
    public static final int RETIREMENT_TIMEOUT_TICKS = 20;

    /**
     * Identity of one predicted placement. The owner cell and clicked face come from the immutable
     * ROOT AIM, so a group names the player action that produced it; the sequence is vanilla's own
     * predicted-action number, which is what the acknowledgement is keyed by.
     */
    public record GroupKey(long ownerPos, Direction clickedFace, int sequence) {
        public GroupKey {
            Objects.requireNonNull(clickedFace, "clickedFace");
            if (sequence < 0) {
                throw new IllegalArgumentException("negative prediction sequence");
            }
        }
    }

    /** Raw backing read seam. Never consults the overlay; installed by the client layer. */
    @FunctionalInterface
    public interface BackingFactProbe {
        SlabAnchorAttachment.PlacementDyFact backingFact(long packedPos);
    }

    /** Targeted block-rerender seam; installed by the client layer, null on a dedicated server. */
    @FunctionalInterface
    public interface RerenderSink {
        void schedule(BlockPos pos);
    }

    private static final class Group {
        final Object level;
        final long generation;
        final GroupKey key;
        /** Sorted packed positions. */
        final long[] cells;
        /** Raw {@code Double.doubleToRawLongBits} height per cell, parallel to {@link #cells}. */
        final long[] rawBits;
        boolean acknowledged;
        long ackTick;

        Group(Object level, long generation, GroupKey key, long[] cells, long[] rawBits) {
            this.level = level;
            this.generation = generation;
            this.key = key;
            this.cells = cells;
            this.rawBits = rawBits;
        }
    }

    /** The published, immutable, lock-free view consumed by the chunk-mesh worker threads. */
    private record OverlayCells(
            Object level,
            long[] keys,
            SlabAnchorAttachment.PlacementDyFact[] facts
    ) {
    }

    private static final OverlayCells EMPTY_OVERLAY =
            new OverlayCells(null, new long[0], new SlabAnchorAttachment.PlacementDyFact[0]);
    private static volatile OverlayCells overlayCells = EMPTY_OVERLAY;

    /**
     * Whole groups, in install order. WHOLE-GROUP retirement is what keeps a cross-chunk door or bed
     * pair safe: both cells enter and leave the overlay in one step, so a pair can never be seen half
     * predicted and half authoritative.
     */
    private static final LinkedHashMap<GroupKey, Group> GROUPS = new LinkedHashMap<>();

    /** Cell to the group that currently speaks for it. Primitive-keyed, never iterated in order. */
    private static final Long2ObjectOpenHashMap<GroupKey> OVERLAY_OWNER = new Long2ObjectOpenHashMap<>();

    /**
     * Per-cell high-water sequence. This is what makes rapid re-placement into one cell safe: a
     * newer install raises the mark, and any older group that later tries to speak for or retire that
     * cell finds the mark no longer its own and leaves it alone. Deliberately NOT cleared on
     * retirement — the mark is monotonic, so it also rejects an install that arrives out of order.
     */
    private static final Long2IntOpenHashMap HIGH_WATER = new Long2IntOpenHashMap();

    /**
     * Vanilla's predicted-action sequence, exposed only while that action runs. The capture seam in
     * {@code BlockItemPlacementIntentMixin} is common code, so it reads the number from here rather
     * than from any client class.
     */
    private static final ThreadLocal<Integer> CURRENT_SEQUENCE = new ThreadLocal<>();

    private static volatile BackingFactProbe backingProbe;
    private static volatile RerenderSink rerenderSink;

    private static Object currentLevel;
    private static long generation;
    private static int acknowledgedThrough = -1;
    private static long tickCounter;

    static {
        HIGH_WATER.defaultReturnValue(-1);
    }

    private PlacementDyOverlay() {
    }

    // ── client installation ──────────────────────────────────────────────────────────────────

    /** Arms the overlay. Until this is called nothing here affects any read or any render. */
    public static void installClientHooks(BackingFactProbe probe, RerenderSink sink) {
        backingProbe = probe;
        rerenderSink = sink;
    }

    // ── vanilla predicted-action sequence scope ──────────────────────────────────────────────

    public static void openSequence(int sequence) {
        CURRENT_SEQUENCE.set(sequence);
    }

    public static void closeSequence() {
        CURRENT_SEQUENCE.remove();
    }

    /** Vanilla's sequence for the action running on this thread, or {@code -1} outside any action. */
    public static int currentSequence() {
        Integer sequence = CURRENT_SEQUENCE.get();
        return sequence == null ? -1 : sequence;
    }

    // ── the read path (mesh worker threads) ──────────────────────────────────────────────────

    /**
     * The overlay's opinion about one cell, or {@code null} when it has none and the caller must read
     * the backing store. Lock-free and allocation-free on both a hit and a miss.
     *
     * <p>This is the pre-resolved form of the ownership test: a cell is in the published snapshot
     * exactly when its owner group is live on the current level and generation, the group still
     * declares that cell, {@link #OVERLAY_OWNER} names that group and {@link #HIGH_WATER} still holds
     * that group's sequence. Every edit to any of those republishes under the monitor, and
     * {@link #resetForLevel} — the sole writer of {@code currentLevel} / {@code generation} —
     * publishes {@link #EMPTY_OVERLAY}, so a non-empty snapshot always carries the current level and
     * generation by construction.
     */
    public static SlabAnchorAttachment.PlacementDyFact overlayFact(Object level, long packedPos) {
        OverlayCells overlay = overlayCells;
        if (overlay == EMPTY_OVERLAY) {
            return null;
        }
        if (level == null || level != overlay.level()) {
            return null;
        }
        int index = Arrays.binarySearch(overlay.keys(), packedPos);
        return index >= 0 ? overlay.facts()[index] : null;
    }

    /**
     * Raw authoritative backing read that explicitly does NOT consult the overlay. Debug and HUD
     * surfaces that want to compare "what the server has stored" against "what is on screen" must ask
     * through here.
     */
    public static SlabAnchorAttachment.PlacementDyFact backingFact(long packedPos) {
        BackingFactProbe probe = backingProbe;
        if (probe == null) {
            return SlabAnchorAttachment.PlacementDyFact.absent();
        }
        SlabAnchorAttachment.PlacementDyFact fact = probe.backingFact(packedPos);
        return fact == null ? SlabAnchorAttachment.PlacementDyFact.absent() : fact;
    }

    // ── install ──────────────────────────────────────────────────────────────────────────────

    /**
     * Installs one predicted placement and asks for its cells to be rebuilt.
     *
     * <p>ORDERING. {@link #installLocked} publishes the snapshot BEFORE this method schedules the
     * rerender. The mesh thread that services the rerender must already be able to see the install,
     * or the just-placed block bakes at its pre-prediction height and the snap looks unfixed.
     */
    public static void installPredictedPlacement(
            Object level,
            BlockPos ownerPos,
            Direction clickedFace,
            int sequence,
            Map<BlockPos, Long> rawBitsByPos
    ) {
        dispatchRerenders(installLocked(level, ownerPos, clickedFace, sequence, rawBitsByPos));
    }

    static synchronized List<BlockPos> installLocked(
            Object level,
            BlockPos ownerPos,
            Direction clickedFace,
            int sequence,
            Map<BlockPos, Long> rawBitsByPos
    ) {
        if (level == null || ownerPos == null || clickedFace == null || sequence < 0
                || rawBitsByPos == null || rawBitsByPos.isEmpty()) {
            return List.of();
        }
        ensureCurrentLevel(level);
        GroupKey key = new GroupKey(ownerPos.asLong(), clickedFace, sequence);

        long[] cells = new long[rawBitsByPos.size()];
        int i = 0;
        for (BlockPos pos : rawBitsByPos.keySet()) {
            cells[i++] = pos.asLong();
        }
        Arrays.sort(cells);
        long[] rawBits = new long[cells.length];
        for (int c = 0; c < cells.length; c++) {
            Long bits = rawBitsByPos.get(BlockPos.fromLong(cells[c]));
            if (bits == null || !Double.isFinite(Double.longBitsToDouble(bits))) {
                Slabbed.LOGGER.warn("[C3] prediction cell has no finite height; overlay not installed");
                return List.of();
            }
            rawBits[c] = bits;
        }

        if (GROUPS.containsKey(key)) {
            Slabbed.LOGGER.warn("[C3] duplicate prediction group {}; overlay not installed", key);
            return List.of();
        }
        for (long cell : cells) {
            if (HIGH_WATER.get(cell) > sequence) {
                Slabbed.LOGGER.warn("[C3] out-of-order prediction sequence {}; overlay not installed", sequence);
                return List.of();
            }
        }

        Group group = new Group(currentLevel, generation, key, cells, rawBits);
        GROUPS.put(key, group);
        try {
            for (long cell : cells) {
                HIGH_WATER.put(cell, sequence);
                OVERLAY_OWNER.put(cell, key);
            }
            publishOverlayCellsLocked();
        } catch (RuntimeException exception) {
            rollbackInstall(key, cells, sequence);
            Slabbed.LOGGER.warn("[C3] prediction overlay install failed", exception);
            return List.of();
        }
        ArrayList<BlockPos> rerenders = new ArrayList<>(cells.length);
        for (long cell : cells) {
            rerenders.add(BlockPos.fromLong(cell));
        }
        return List.copyOf(rerenders);
    }

    private static void rollbackInstall(GroupKey key, long[] cells, int sequence) {
        GROUPS.remove(key);
        for (long cell : cells) {
            if (key.equals(OVERLAY_OWNER.get(cell))) {
                OVERLAY_OWNER.remove(cell);
            }
            if (HIGH_WATER.get(cell) == sequence) {
                HIGH_WATER.remove(cell);
            }
        }
        publishOverlayCellsLocked();
    }

    // ── retirement ───────────────────────────────────────────────────────────────────────────

    /**
     * Vanilla acknowledged everything through {@code sequence}. Called from the RETURN of
     * {@code ClientWorld.handlePlayerActionResponse}, after vanilla's own state sync work.
     *
     * <p>WHY RETIREMENT IS LAZY RATHER THAN EXACT-ON-ACK (a deliberate change from the donor).
     * Retiring a group the instant its sequence is acknowledged would make correctness depend on the
     * attachment sync packet always arriving before the acknowledgement packet. That ordering does
     * hold today — Fabric's {@code AttachmentSync.trySync} sends synchronously from inside
     * {@code setAttached}, while the acknowledgement is only constructed later in {@code tick()} —
     * but nothing pins it. If it ever changed, an exact-on-ack retirement would silently drop the
     * prediction one frame before the real fact arrived and reintroduce the snap, in a way no test
     * would notice. So a group retires when it has been acknowledged AND its backing fact is actually
     * present, or {@link #RETIREMENT_TIMEOUT_TICKS} ticks past the acknowledgement, whichever comes
     * first. That turns the packet ordering into an optimization rather than a correctness
     * dependency, and the timeout keeps a refused placement from holding a prediction forever.
     */
    public static void onVanillaAcknowledgement(Object level, int sequence) {
        dispatchRerenders(acknowledgeLocked(level, sequence));
    }

    static synchronized List<BlockPos> acknowledgeLocked(Object level, int sequence) {
        if (level == null) {
            return List.of();
        }
        ensureCurrentLevel(level);
        if (sequence > acknowledgedThrough) {
            acknowledgedThrough = sequence;
        }
        for (Group group : GROUPS.values()) {
            if (!group.acknowledged && group.key.sequence() <= acknowledgedThrough) {
                group.acknowledged = true;
                group.ackTick = tickCounter;
            }
        }
        return retireReadyGroupsLocked();
    }

    /** One client tick. Drives the lazy-retirement re-check and the acknowledgement timeout. */
    public static void clientTick(Object level) {
        dispatchRerenders(tickLocked(level));
    }

    static synchronized List<BlockPos> tickLocked(Object level) {
        ensureCurrentLevel(level);
        tickCounter++;
        return GROUPS.isEmpty() ? List.of() : retireReadyGroupsLocked();
    }

    private static List<BlockPos> retireReadyGroupsLocked() {
        ArrayList<GroupKey> ready = null;
        for (Group group : GROUPS.values()) {
            if (!group.acknowledged) {
                continue;
            }
            boolean timedOut = (tickCounter - group.ackTick) >= RETIREMENT_TIMEOUT_TICKS;
            if (!timedOut && !backingPresentForOwnedCellsLocked(group)) {
                continue;
            }
            if (ready == null) {
                ready = new ArrayList<>();
            }
            ready.add(group.key);
        }
        if (ready == null) {
            return List.of();
        }
        ArrayList<BlockPos> rerenders = new ArrayList<>();
        for (GroupKey key : ready) {
            retireLocked(key, rerenders);
        }
        // ORDERING. The authoritative backing value is already visible when this runs — that is
        // exactly what the retirement condition above waited for — so publishing the retirement here
        // hands the reader the server's own number, and the rerender the caller schedules afterwards
        // is what puts it on screen. Publishing before the backing were visible would instead flash
        // the stale pre-placement height, which is strictly worse than the predicted one.
        publishOverlayCellsLocked();
        return List.copyOf(rerenders);
    }

    /**
     * "Backing present" for the cells this group still owns. Cells another group has taken over are
     * not this group's business; a group that owns nothing any more is ready immediately. With no
     * probe installed only the timeout can retire, which is the correct inert behaviour.
     */
    private static boolean backingPresentForOwnedCellsLocked(Group group) {
        BackingFactProbe probe = backingProbe;
        if (probe == null) {
            return false;
        }
        for (long cell : group.cells) {
            if (!group.key.equals(OVERLAY_OWNER.get(cell))
                    || HIGH_WATER.get(cell) != group.key.sequence()) {
                continue;
            }
            SlabAnchorAttachment.PlacementDyFact fact = probe.backingFact(cell);
            if (fact == null || !fact.present()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whole-group retirement. Only the cells this group still owns are released, so retiring an older
     * group can never strip a newer group's ownership of the same cell. The high-water mark stays
     * behind on purpose: it is monotonic, and it is what rejects an install that arrives out of order.
     */
    private static void retireLocked(GroupKey key, List<BlockPos> rerenders) {
        Group group = GROUPS.remove(key);
        if (group == null) {
            return;
        }
        for (long cell : group.cells) {
            if (key.equals(OVERLAY_OWNER.get(cell))) {
                OVERLAY_OWNER.remove(cell);
                rerenders.add(BlockPos.fromLong(cell));
            }
        }
    }

    // ── chunk unload / level change ──────────────────────────────────────────────────────────

    /**
     * A chunk left the client. Every group with a cell in it retires WHOLE — the other half of a
     * cross-chunk pair must not be left speaking for itself — and this chunk's ownership and
     * high-water marks are dropped, since no live group can own a cell here afterwards.
     */
    public static void onChunkUnload(Object level, int chunkX, int chunkZ) {
        dispatchRerenders(cleanupChunkLocked(level, chunkX, chunkZ));
    }

    static synchronized List<BlockPos> cleanupChunkLocked(Object level, int chunkX, int chunkZ) {
        if (level == null || level != currentLevel) {
            return List.of();
        }
        ArrayList<GroupKey> retire = new ArrayList<>();
        for (Group group : GROUPS.values()) {
            for (long cell : group.cells) {
                if (chunkOf(cell, true) == chunkX && chunkOf(cell, false) == chunkZ) {
                    retire.add(group.key);
                    break;
                }
            }
        }
        ArrayList<BlockPos> rerenders = new ArrayList<>();
        for (GroupKey key : retire) {
            retireLocked(key, rerenders);
        }
        pruneChunkLocked(chunkX, chunkZ);
        publishOverlayCellsLocked();
        rerenders.removeIf(pos -> (pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ);
        return List.copyOf(rerenders);
    }

    private static void pruneChunkLocked(int chunkX, int chunkZ) {
        LongIterator owners = OVERLAY_OWNER.keySet().iterator();
        while (owners.hasNext()) {
            long cell = owners.nextLong();
            if (chunkOf(cell, true) == chunkX && chunkOf(cell, false) == chunkZ) {
                owners.remove();
            }
        }
        LongIterator marks = HIGH_WATER.keySet().iterator();
        while (marks.hasNext()) {
            long cell = marks.nextLong();
            if (chunkOf(cell, true) == chunkX && chunkOf(cell, false) == chunkZ) {
                marks.remove();
            }
        }
    }

    private static int chunkOf(long packedPos, boolean xAxis) {
        return (xAxis ? BlockPos.unpackLongX(packedPos) : BlockPos.unpackLongZ(packedPos)) >> 4;
    }

    /**
     * Level identity changed (world change, disconnect, reconnect). Everything predicted for the old
     * level is void: the generation moves, the structures empty and the shared empty snapshot is
     * republished, so every read falls straight through to the backing store again.
     */
    public static synchronized void resetForLevel(Object level) {
        generation++;
        currentLevel = level;
        acknowledgedThrough = -1;
        tickCounter = 0L;
        GROUPS.clear();
        OVERLAY_OWNER.clear();
        HIGH_WATER.clear();
        publishOverlayCellsLocked();
    }

    private static void ensureCurrentLevel(Object level) {
        if (level != currentLevel) {
            resetForLevel(level);
        }
    }

    // ── publication ──────────────────────────────────────────────────────────────────────────

    /**
     * Rebuilds and publishes the mesh-thread snapshot. MUST run while holding this class's monitor,
     * from every path that edits {@link #GROUPS}, {@link #OVERLAY_OWNER}, {@link #HIGH_WATER},
     * {@code currentLevel} or {@code generation}. Main-thread only and rare (one placement, one
     * acknowledgement, one chunk unload), so rebuilding from scratch is free; correctness comes from
     * the predicate here being the literal transcription of the per-read ownership test.
     */
    private static void publishOverlayCellsLocked() {
        if (currentLevel == null || GROUPS.isEmpty()) {
            overlayCells = EMPTY_OVERLAY;
            return;
        }
        Long2LongOpenHashMap effective = new Long2LongOpenHashMap();
        for (Group group : GROUPS.values()) {
            if (group.level != currentLevel || group.generation != generation) {
                continue;
            }
            for (int i = 0; i < group.cells.length; i++) {
                long cell = group.cells[i];
                if (!group.key.equals(OVERLAY_OWNER.get(cell))
                        || HIGH_WATER.get(cell) != group.key.sequence()) {
                    continue;
                }
                effective.put(cell, group.rawBits[i]);
            }
        }
        if (effective.isEmpty()) {
            overlayCells = EMPTY_OVERLAY;
            return;
        }
        long[] keys = effective.keySet().toLongArray();
        Arrays.sort(keys);
        SlabAnchorAttachment.PlacementDyFact[] facts =
                new SlabAnchorAttachment.PlacementDyFact[keys.length];
        for (int i = 0; i < keys.length; i++) {
            facts[i] = new SlabAnchorAttachment.PlacementDyFact(true, effective.get(keys[i]));
        }
        // The volatile store is the publication point: everything above happened-before it.
        overlayCells = new OverlayCells(currentLevel, keys, facts);
    }

    private static void dispatchRerenders(List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return;
        }
        RerenderSink sink = rerenderSink;
        if (sink == null) {
            return;
        }
        for (BlockPos pos : positions) {
            try {
                sink.schedule(pos);
            } catch (RuntimeException exception) {
                Slabbed.LOGGER.warn("[C3] prediction rerender failed for {}", pos, exception);
            }
        }
    }

    // ── test seams ───────────────────────────────────────────────────────────────────────────

    /** The snapshot the read path would load right now. Reference identity is the assertion. */
    public static Object publishedSnapshot() {
        return overlayCells;
    }

    /** The one shared empty snapshot every no-prediction read short-circuits on. */
    public static Object sharedEmptySnapshot() {
        return EMPTY_OVERLAY;
    }

    public static synchronized int liveGroupCount() {
        return GROUPS.size();
    }

    public static synchronized int highWaterSequence(long packedPos) {
        return HIGH_WATER.get(packedPos);
    }

    public static synchronized GroupKey overlayOwner(long packedPos) {
        return OVERLAY_OWNER.get(packedPos);
    }

    /** Full teardown for tests: drops the installed hooks as well as the state. */
    public static synchronized void resetForTests() {
        backingProbe = null;
        rerenderSink = null;
        CURRENT_SEQUENCE.remove();
        resetForLevel(null);
    }
}
