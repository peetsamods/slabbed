package com.slabbed.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * MODEL_STALE sentinel — the render-truth probe (anti-whack-a-mole audit, 2026-07-06).
 *
 * <p>THE FAMILY THIS CATCHES: the chunk mesher bakes a SNAPSHOT of {@link SlabSupport#getYOffset} on a
 * worker thread; when no re-mesh fires (a missed dirty call, a no-op {@code setBlocksDirty(pos, current,
 * current)}, a wrong-section dirty box, a deferred Sodium rebuild), the on-screen model diverges from the
 * live logical dy — and headless gametests are STRUCTURALLY blind to it (no renderer; both triad legs
 * read the same live function headlessly — lesson S7). Five such sub-bugs shipped on this branch in one
 * 48-hour window (b94b48ae, 38a9fcdf, 4df516ab, 05a4e36c, 51cc4cb4), each discoverable only by a human
 * live round. This sentinel moves that discovery into recorded red rows produced during normal play.
 *
 * <p>HOW (per the adversarially-refereed design — both refuters ENDORSE_WITH_CONDITIONS):
 * <ul>
 *   <li><b>Arm at placement-HEAD (pre-mutation):</b> the placed cell plus its {@link #ARM_RADIUS}
 *       neighborhood, capturing each non-air neighbor's PRE-placement logical dy as a baseline. The
 *       refuters proved neighborhood arming + baselines are REQUIRED: in the expensive incidents the
 *       mesher never runs for the affected NEIGHBOR at all, so a probe armed only on the placed pos
 *       records nothing there and absence-of-entry is ambiguous with nothing-needed-to-bake.</li>
 *   <li><b>Capture at bake:</b> {@code OffsetBlockStateModel.emitQuads} records (pos → bakedDy) for armed
 *       positions only — subject emissions only, never neighbor probes (a neighbor probe from another
 *       section's bake pass recomputes FRESH dy and would mask that the pos's own mesh is stale), and
 *       never the render-region OOB fallback (not a real dy decision).</li>
 *   <li><b>Judge on client tick:</b> {@link #samplePass}. DIVERGENCE rule: a bake recorded since arming
 *       disagrees with live dy. ABSENCE rule: no bake since arming AND live dy has moved off the armed
 *       baseline — the mesh still shows baseline-era geometry ({@link #RED_PERSIST_TICKS} is far below
 *       Sodium's ~600-tick deferred heal, so the 30-second-float class reds while still visible).
 *       Both rules require the mismatch to persist across {@link #MIN_MISMATCH_SAMPLES} passes over
 *       {@link #RED_PERSIST_TICKS}, so legitimate settle windows (region-border re-bakes "a frame
 *       later") never fire. No baseline and no bake is at most a YELLOW row, never red.</li>
 * </ul>
 *
 * <p>PERF CONTRACT (the per-frame-diagnostic storm family has shipped lag to users TWICE — the gate
 * order here is load-bearing, matching the recorder's own convention): the mesh-thread fast path is
 * {@link #shouldCapture()} = one volatile read + one live recorder-enabled read (deliberately NOT a
 * cached static — gametests and the /slabdev command flip it at runtime), then a binary search over a
 * published long[] snapshot. Zero allocation while disarmed or while the armed set is empty; allocation
 * only on actual bake capture of an armed position (a rare, real event). An allocation-regression
 * gametest pins this.
 *
 * <p>Threading: arming and sampling happen on the client main thread (and gametest threads) under
 * {@link #LOCK}; bake capture happens on chunk-mesh worker threads via a lock-free snapshot read +
 * {@link ConcurrentHashMap} write. {@code System.nanoTime()} orders bakes relative to arming.
 *
 * <p>This class is deliberately main-source-set and Minecraft-client-free so the FULL rule engine is
 * headlessly provable ({@code ModelStaleSentinelContractTest}); only the bake-event feed and the tick
 * driver are client-side ({@code SlabModelStaleSentinelClient}, {@code OffsetBlockStateModel}).
 */
public final class SlabModelStaleSentinel {
    /** dy values are multiples of 0.5 — anything past this is a real divergence. */
    public static final float DY_EPSILON = 1.0e-4f;
    /** Sampler cadence (ticks). Driven by the client wiring; tests call {@link #samplePass} directly. */
    public static final int SAMPLE_INTERVAL_TICKS = 20;
    /** A mismatch must persist this long before a red row — legitimate mesh lag is a frame or two. */
    public static final int RED_PERSIST_TICKS = 100;
    /** ...and be observed on at least this many distinct sampler passes. */
    public static final int MIN_MISMATCH_SAMPLES = 2;
    /** Chebyshev radius armed around a placement. Subjects beyond it are documented as uncovered. */
    public static final int ARM_RADIUS = 2;
    /** Hard cap on armed entries; oldest are evicted (bounded-memory contract). */
    public static final int ARMED_CAP = 512;
    /** Armed entries expire after this long — long enough to see Sodium's ~600-tick deferred heal. */
    public static final int ARMED_TTL_TICKS = 1200;
    /** Grace after level change / reset before any judging resumes (world-join mesh churn). */
    public static final int LEVEL_CHANGE_GRACE_TICKS = 200;
    /** Grace after a full render invalidation (F3+A / resource reload) — old bakes are meaningless. */
    public static final int INVALIDATE_GRACE_TICKS = 60;

    public static final String KIND_DIVERGENT = "MODEL_STALE_DIVERGENT";
    public static final String KIND_ABSENT = "MODEL_STALE_ABSENT";
    public static final String KIND_NO_BAKE_YELLOW = "MODEL_STALE_NO_BAKE_YELLOW";

    public static final String REASON_PLACEMENT = "placement";
    public static final String REASON_NEIGHBORHOOD = "neighborhood";

    private static final long[] EMPTY_SNAPSHOT = new long[0];
    private static final Object LOCK = new Object();

    /** Test seam (CompatHooks.shouldSkipSlabSupportTestOverride convention): lets the headless contract
     *  suite run the sentinel without toggling the real file-writing recorder session. */
    public static volatile boolean testSessionOverride;

    // Lock-free reads for the mesh-thread fast path.
    private static volatile boolean armedNonEmpty;
    private static volatile long[] armedKeySnapshot = EMPTY_SNAPSHOT;
    private static volatile long suppressedUntilTick = Long.MIN_VALUE;
    private static volatile boolean invalidatePending;

    // Guarded by LOCK (client main thread + gametest threads).
    private static final LinkedHashMap<Long, ArmedEntry> ARMED = new LinkedHashMap<>();
    // Written by mesh worker threads, read/purged under LOCK on the sampling thread.
    private static final ConcurrentHashMap<Long, BakeSample> BAKES = new ConcurrentHashMap<>();

    private record BakeSample(float dy, long nanos) {
    }

    private static final class ArmedEntry {
        final BlockPos pos;
        final String reason;
        /** Block present at arm time for neighborhood entries (disarm when it changes — that is a break/
         *  replace, which is NOT this family; the recorder's break-blindness false alarm is the lesson).
         *  Null for placement entries (the placed cell's pre-place block is intentionally irrelevant). */
        final Block armedBlock;
        final float baselineDy;
        final boolean hasBaseline;
        final long armedNanos;
        final long armedTick;
        int mismatchSamples;
        long firstMismatchTick;
        boolean redLatched;

        ArmedEntry(BlockPos pos, String reason, Block armedBlock, float baselineDy, boolean hasBaseline,
                   long armedNanos, long armedTick) {
            this.pos = pos;
            this.reason = reason;
            this.armedBlock = armedBlock;
            this.baselineDy = baselineDy;
            this.hasBaseline = hasBaseline;
            this.armedNanos = armedNanos;
            this.armedTick = armedTick;
        }
    }

    private SlabModelStaleSentinel() {
    }

    // ── mesh-thread fast path ────────────────────────────────────────────────────────────────────────

    /**
     * FIRST statement of every capture call site. One volatile read, then the live recorder flag —
     * never a cached static (the shipped-twice perf-storm lesson: gate BEFORE work, but read the flag
     * live). Zero allocation on any path.
     */
    public static boolean shouldCapture() {
        return armedNonEmpty && sessionActive();
    }

    /** Lock-free membership test against the published sorted snapshot. Zero allocation. */
    public static boolean isArmed(long posKey) {
        long[] snapshot = armedKeySnapshot;
        return snapshot.length > 0 && Arrays.binarySearch(snapshot, posKey) >= 0;
    }

    /**
     * Record what the mesher actually baked for an armed position. Callers gate with
     * {@link #shouldCapture()} + {@link #isArmed(long)} first; allocation here is fine — it is a rare,
     * real event (an armed position re-baking), not steady-state work.
     */
    public static void recordBake(BlockPos pos, float bakedDy) {
        BAKES.put(pos.asLong(), new BakeSample(bakedDy, System.nanoTime()));
    }

    // ── arming (client main thread; placement-HEAD, pre-mutation) ───────────────────────────────────

    /**
     * Arm the placement cell + its non-air neighborhood with pre-placement dy baselines. Called from
     * {@code BlockItem.place} HEAD on the CLIENT side only, before the world mutates. No-ops unless a
     * recorder session is active — normal play pays exactly one volatile read per placement.
     */
    public static void armPlacement(BlockGetter world, BlockPos placementPos, long nowTick) {
        if (!sessionActive()) {
            return;
        }
        long nowNanos = System.nanoTime();
        synchronized (LOCK) {
            armEntry(new ArmedEntry(placementPos.immutable(), REASON_PLACEMENT, null, 0.0f, false,
                    nowNanos, nowTick));
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int dx = -ARM_RADIUS; dx <= ARM_RADIUS; dx++) {
                for (int dy = -ARM_RADIUS; dy <= ARM_RADIUS; dy++) {
                    for (int dz = -ARM_RADIUS; dz <= ARM_RADIUS; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        cursor.setWithOffset(placementPos, dx, dy, dz);
                        BlockState state = world.getBlockState(cursor);
                        if (state.isAir()) {
                            continue;
                        }
                        float baseline = (float) SlabSupport.getYOffset(world, cursor, state);
                        armEntry(new ArmedEntry(cursor.immutable(), REASON_NEIGHBORHOOD, state.getBlock(),
                                baseline, true, nowNanos, nowTick));
                    }
                }
            }
            publishSnapshotLocked();
        }
    }

    /** Test seam: arm a single entry directly (same bookkeeping path as {@link #armPlacement}). */
    public static void armForTest(BlockGetter world, BlockPos pos, String reason, long nowTick) {
        long nowNanos = System.nanoTime();
        synchronized (LOCK) {
            if (REASON_PLACEMENT.equals(reason)) {
                armEntry(new ArmedEntry(pos.immutable(), reason, null, 0.0f, false, nowNanos, nowTick));
            } else {
                BlockState state = world.getBlockState(pos);
                float baseline = state.isAir() ? 0.0f : (float) SlabSupport.getYOffset(world, pos, state);
                armEntry(new ArmedEntry(pos.immutable(), reason, state.getBlock(), baseline, !state.isAir(),
                        nowNanos, nowTick));
            }
            publishSnapshotLocked();
        }
    }

    private static void armEntry(ArmedEntry entry) {
        Long key = entry.pos.asLong();
        // Re-arming an existing key restarts its window (fresh baseline/reason) — deliberate: the newest
        // placement is the interesting event.
        ARMED.remove(key);
        ARMED.put(key, entry);
        if (ARMED.size() > ARMED_CAP) {
            Iterator<Long> eldest = ARMED.keySet().iterator();
            Long evicted = eldest.next();
            eldest.remove();
            BAKES.remove(evicted);
        }
    }

    // ── judging (client tick / gametest thread) ──────────────────────────────────────────────────────

    /**
     * Cadenced production entry: runs {@link #samplePass} every {@link #SAMPLE_INTERVAL_TICKS}.
     */
    public static void maybeSample(BlockGetter level, long nowTick, Predicate<BlockPos> chunkLoaded,
                                   Consumer<LinkedHashMap<String, String>> redRowSink) {
        if (nowTick % SAMPLE_INTERVAL_TICKS != 0L) {
            return;
        }
        samplePass(level, nowTick, chunkLoaded, redRowSink);
    }

    /**
     * One judging pass over the armed set. Public so the headless contract suite drives the REAL
     * bookkeeping (arming, streaks, latching, eviction) — not a parallel reimplementation.
     */
    public static void samplePass(BlockGetter level, long nowTick, Predicate<BlockPos> chunkLoaded,
                                  Consumer<LinkedHashMap<String, String>> redRowSink) {
        if (!sessionActive()) {
            return;
        }
        if (invalidatePending) {
            // F3+A / resource reload: every old bake sample describes a mesh that no longer exists.
            invalidatePending = false;
            BAKES.clear();
            suppressedUntilTick = nowTick + INVALIDATE_GRACE_TICKS;
            synchronized (LOCK) {
                for (ArmedEntry entry : ARMED.values()) {
                    entry.mismatchSamples = 0;
                }
            }
            return;
        }
        if (nowTick < suppressedUntilTick) {
            return;
        }
        synchronized (LOCK) {
            boolean removedAny = false;
            Iterator<Map.Entry<Long, ArmedEntry>> it = ARMED.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, ArmedEntry> mapEntry = it.next();
                ArmedEntry entry = mapEntry.getValue();
                if (nowTick - entry.armedTick >= ARMED_TTL_TICKS) {
                    it.remove();
                    removedAny = true;
                    continue;
                }
                if (!chunkLoaded.test(entry.pos)) {
                    // Not render-loaded: logical dy of an unloaded pos is meaningless against a stale
                    // bake sample — skip without touching the streak (unload eviction is event-driven).
                    continue;
                }
                BlockState state = level.getBlockState(entry.pos);
                if (entry.armedBlock != null && state.getBlock() != entry.armedBlock) {
                    // The block itself changed (broken/replaced). That is a BREAK-family event, not a
                    // mesh-staleness event — disarm silently (the double-slab false alarm lesson).
                    it.remove();
                    removedAny = true;
                    continue;
                }
                if (state.isAir()) {
                    // Placement entry whose cell is now air: failed placement or broken since. Without
                    // break capture these are indistinguishable — never a red row.
                    it.remove();
                    removedAny = true;
                    continue;
                }
                float liveDy = (float) SlabSupport.getYOffset(level, entry.pos, state);
                BakeSample bake = BAKES.get(mapEntry.getKey());
                boolean bakedSinceArm = bake != null && bake.nanos >= entry.armedNanos;

                boolean mismatch;
                String kind;
                if (bakedSinceArm) {
                    mismatch = Math.abs(bake.dy - liveDy) > DY_EPSILON;
                    kind = KIND_DIVERGENT;
                } else if (entry.hasBaseline) {
                    // No re-bake since arming: the mesh still shows baseline-era geometry. If live dy has
                    // moved off the baseline, the screen is stale.
                    mismatch = Math.abs(entry.baselineDy - liveDy) > DY_EPSILON;
                    kind = KIND_ABSENT;
                } else {
                    // Placed cell that never baked: suspicious after the persistence window (vanilla
                    // always dirties the placed pos) but ambiguous — YELLOW once, never red.
                    if (!entry.redLatched && nowTick - entry.armedTick >= RED_PERSIST_TICKS) {
                        entry.redLatched = true;
                        redRowSink.accept(row(entry, KIND_NO_BAKE_YELLOW, null, liveDy, state, nowTick));
                    }
                    continue;
                }

                if (!mismatch) {
                    entry.mismatchSamples = 0;
                    continue;
                }
                entry.mismatchSamples++;
                if (entry.mismatchSamples == 1) {
                    entry.firstMismatchTick = nowTick;
                }
                if (!entry.redLatched
                        && entry.mismatchSamples >= MIN_MISMATCH_SAMPLES
                        && nowTick - entry.firstMismatchTick >= RED_PERSIST_TICKS) {
                    entry.redLatched = true;
                    redRowSink.accept(row(entry, kind, bakedSinceArm ? bake.dy : null, liveDy, state, nowTick));
                }
            }
            if (removedAny) {
                BAKES.keySet().retainAll(ARMED.keySet());
                publishSnapshotLocked();
            }
        }
    }

    private static LinkedHashMap<String, String> row(ArmedEntry entry, String kind, Float bakedDy,
                                                     float liveDy, BlockState state, long nowTick) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("kind", kind);
        row.put("pos", entry.pos.getX() + " " + entry.pos.getY() + " " + entry.pos.getZ());
        row.put("section", SectionPos.blockToSectionCoord(entry.pos.getX())
                + "," + SectionPos.blockToSectionCoord(entry.pos.getY())
                + "," + SectionPos.blockToSectionCoord(entry.pos.getZ()));
        row.put("bakedDy", bakedDy == null ? "NO_BAKE" : Float.toString(bakedDy));
        row.put("baselineDy", entry.hasBaseline ? Float.toString(entry.baselineDy) : "none");
        row.put("liveDy", Float.toString(liveDy));
        row.put("blockState", state.toString());
        row.put("armedReason", entry.reason);
        row.put("ticksSinceArm", Long.toString(nowTick - entry.armedTick));
        row.put("mismatchSamples", Integer.toString(entry.mismatchSamples));
        return row;
    }

    // ── lifecycle / suppression (client wiring + tests) ─────────────────────────────────────────────

    /** Level change (join/dimension switch): drop everything, judge nothing during the mesh churn. */
    public static void reset(long nowTick) {
        synchronized (LOCK) {
            ARMED.clear();
            BAKES.clear();
            suppressedUntilTick = nowTick + LEVEL_CHANGE_GRACE_TICKS;
            publishSnapshotLocked();
        }
    }

    /** Level went away entirely (title screen): clear state; the next {@link #reset} sets the grace. */
    public static void resetCold() {
        synchronized (LOCK) {
            ARMED.clear();
            BAKES.clear();
            suppressedUntilTick = Long.MIN_VALUE;
            publishSnapshotLocked();
        }
    }

    /** F3+A / resource reload — applied lazily by the next {@link #samplePass} (fires off-tick). */
    public static void onFullRenderInvalidate() {
        invalidatePending = true;
    }

    /** Client chunk unload: armed positions there are unjudgeable — evict rather than skip forever. */
    public static void onChunkUnload(int chunkX, int chunkZ) {
        synchronized (LOCK) {
            boolean removedAny = ARMED.entrySet().removeIf(e -> {
                BlockPos pos = e.getValue().pos;
                return (pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ;
            });
            if (removedAny) {
                BAKES.keySet().retainAll(ARMED.keySet());
                publishSnapshotLocked();
            }
        }
    }

    /** Armed-entry count (tests + /slabdev display). */
    public static int armedCount() {
        synchronized (LOCK) {
            return ARMED.size();
        }
    }

    private static boolean sessionActive() {
        return LiveCursorIntentRecorder.enabled() || testSessionOverride;
    }

    private static void publishSnapshotLocked() {
        if (ARMED.isEmpty()) {
            armedKeySnapshot = EMPTY_SNAPSHOT;
            armedNonEmpty = false;
            return;
        }
        long[] keys = new long[ARMED.size()];
        int i = 0;
        for (Long key : ARMED.keySet()) {
            keys[i++] = key;
        }
        Arrays.sort(keys);
        armedKeySnapshot = keys;
        armedNonEmpty = true;
    }
}
