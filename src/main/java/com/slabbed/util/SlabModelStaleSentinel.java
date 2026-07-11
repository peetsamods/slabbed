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
    public static final String KIND_ENSEMBLE_INTERPENETRATION = "ENSEMBLE_INTERPENETRATION";
    public static final String KIND_ENSEMBLE_GAP = "ENSEMBLE_GAP";
    public static final String KIND_ENSEMBLE_OCCLUDED_OCCUPANCY = "ENSEMBLE_OCCLUDED_OCCUPANCY";

    /** Recorder/chat routing truth. Unknown diagnostic kinds are deliberately non-red. */
    public enum DiagnosticSeverity {
        RED("red"),
        INFO("info"),
        YELLOW("yellow");

        private final String wireName;

        DiagnosticSeverity(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }
    }

    /** One shared authority for recorder mismatch routing and client chat/burst behavior. */
    public static DiagnosticSeverity diagnosticSeverity(String kind) {
        return switch (kind == null ? "" : kind) {
            case KIND_DIVERGENT, KIND_ABSENT,
                    KIND_ENSEMBLE_INTERPENETRATION, KIND_ENSEMBLE_GAP -> DiagnosticSeverity.RED;
            case KIND_ENSEMBLE_OCCLUDED_OCCUPANCY -> DiagnosticSeverity.INFO;
            case KIND_NO_BAKE_YELLOW -> DiagnosticSeverity.YELLOW;
            default -> DiagnosticSeverity.YELLOW;
        };
    }

    public static final String REASON_PLACEMENT = "placement";
    public static final String REASON_NEIGHBORHOOD = "neighborhood";
    /** Break-neighborhood entries (Phase 1.5): ensemble-classified once + divergence-checked, but never
     *  yellow (no bake on an unchanged far neighbor is normal) and never absence-ruled (no baseline —
     *  server-armed, and TEST (6) showed 40/94 breaks touch lowered geometry the placement-only gate
     *  could not see). */
    public static final String REASON_BREAK = "break";

    private static final long[] EMPTY_SNAPSHOT = new long[0];
    private static final Object LOCK = new Object();

    /**
     * The dy function used for BOTH arming baselines and sampler live reads. This MUST be the same
     * policy the capture path records, or healthy scenes red falsely: the mesher's dy is NOT raw
     * {@link SlabSupport#getYOffset} — carpets deliberately render at {@code ClientDy.dyFor} (-0.5 on a
     * bottom slab) while the logical policy holds them at 0.0 (adversarial review finding #1: judging
     * with getYOffset while capturing the render policy guaranteed a false DIVERGENT on any placement
     * near a carpet-on-slab). The client driver injects the render-twin at init; headless defaults to
     * getYOffset, which is self-consistent because headless tests also feed bakes synthetically.
     */
    @FunctionalInterface
    public interface LiveDyPolicy {
        double dyFor(BlockGetter level, BlockPos pos, BlockState state);
    }

    private static final LiveDyPolicy DEFAULT_POLICY = SlabSupport::getYOffset;
    private static volatile LiveDyPolicy liveDyPolicy = DEFAULT_POLICY;

    /** Client init injects the render-intent dy twin; tests may inject synthetic policies. */
    public static void setLiveDyPolicy(LiveDyPolicy policy) {
        liveDyPolicy = policy == null ? DEFAULT_POLICY : policy;
    }

    /** Test seam: restore the default (getYOffset) policy. */
    public static void resetLiveDyPolicyForTest() {
        liveDyPolicy = DEFAULT_POLICY;
    }

    /** Test seam (CompatHooks.shouldSkipSlabSupportTestOverride convention): lets the headless contract
     *  suite run the sentinel without toggling the real file-writing recorder session. */
    public static volatile boolean testSessionOverride;

    // Lock-free reads for the mesh-thread fast path.
    private static volatile boolean armedNonEmpty;
    private static volatile long[] armedKeySnapshot = EMPTY_SNAPSHOT;
    private static volatile long suppressedUntilTick = Long.MIN_VALUE;
    private static volatile boolean invalidatePending;

    // Liveness counters (JVM-lifetime, surfaced in the recorder summary): a session with zero red rows
    // is only a clean bill if these prove the probe actually armed and judged (green-by-evidence).
    private static final java.util.concurrent.atomic.AtomicLong ARMED_TOTAL =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong SAMPLE_PASSES =
            new java.util.concurrent.atomic.AtomicLong();

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
        /** Ensemble-coherence classification runs once per placement entry, on its first judged pass
         *  (dys are server-settled by then) — Phase 1 of ENSEMBLE_COHERENCE_DESIGN.md. */
        boolean ensembleChecked;
        /** Separate from {@link #redLatched}: a NO_BAKE yellow must not preempt a later real divergence
         *  red if the bake finally arrives wrong (adversarial review finding #2 — an earlier version of
         *  this split was silently lost to a `git checkout` during mutation runs and the commit message
         *  over-claimed it; re-applied WITH the contract test that pins it). */
        boolean yellowLatched;

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
        BakeSample sample = new BakeSample(bakedDy, System.nanoTime());
        // Keep-max-nanos merge (adversarial review finding #5): two rebuilds of the same section can be
        // in flight on different workers; a superseded OLDER build emitting after the fresh one must not
        // overwrite the newer sample, or a stale pre-change dy with fresh-enough ordering would
        // manufacture a false DIVERGENT.
        BAKES.merge(pos.asLong(), sample, (existing, incoming) ->
                existing.nanos() >= incoming.nanos() ? existing : incoming);
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
                        float baseline = (float) liveDyPolicy.dyFor(world, cursor, state);
                        armEntry(new ArmedEntry(cursor.immutable(), REASON_NEIGHBORHOOD, state.getBlock(),
                                baseline, true, nowNanos, nowTick));
                    }
                }
            }
            publishSnapshotLocked();
        }
    }

    /**
     * Phase 1.5: arm the non-air neighborhood of a BREAK (radius 2) for ensemble classification +
     * divergence staleness. Called from the server-side break event, recorder-gated by the caller.
     * No baselines (the absence rule stays off for these — the render-twin policy is not available on
     * the server side, and a wrong baseline is worse than none).
     */
    public static void armBreakNeighborhood(BlockGetter world, BlockPos brokenPos, long nowTick) {
        if (!sessionActive()) {
            return;
        }
        long nowNanos = System.nanoTime();
        synchronized (LOCK) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int dx = -ARM_RADIUS; dx <= ARM_RADIUS; dx++) {
                for (int dy = -ARM_RADIUS; dy <= ARM_RADIUS; dy++) {
                    for (int dz = -ARM_RADIUS; dz <= ARM_RADIUS; dz++) {
                        cursor.setWithOffset(brokenPos, dx, dy, dz);
                        BlockState state = world.getBlockState(cursor);
                        if (state.isAir()) {
                            continue;
                        }
                        armEntry(new ArmedEntry(cursor.immutable(), REASON_BREAK, state.getBlock(),
                                0.0f, false, nowNanos, nowTick));
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
                float baseline = state.isAir() ? 0.0f : (float) liveDyPolicy.dyFor(world, pos, state);
                armEntry(new ArmedEntry(pos.immutable(), reason, state.getBlock(), baseline, !state.isAir(),
                        nowNanos, nowTick));
            }
            publishSnapshotLocked();
        }
    }

    private static void armEntry(ArmedEntry entry) {
        ARMED_TOTAL.incrementAndGet();
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
        SAMPLE_PASSES.incrementAndGet();
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
                float liveDy = (float) liveDyPolicy.dyFor(level, entry.pos, state);
                if (!entry.ensembleChecked
                        && (REASON_PLACEMENT.equals(entry.reason) || REASON_BREAK.equals(entry.reason))) {
                    entry.ensembleChecked = true;
                    emitEnsembleVerdicts(level, entry, liveDy, nowTick, redRowSink);
                }
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
                    // always dirties the placed pos) but ambiguous — YELLOW once, never red. Latched
                    // separately from red: a late wrong bake must still be able to red. Break-armed
                    // entries never yellow (no bake on an unchanged far neighbor is normal).
                    if (REASON_BREAK.equals(entry.reason)) {
                        continue;
                    }
                    if (!entry.yellowLatched && nowTick - entry.armedTick >= RED_PERSIST_TICKS) {
                        entry.yellowLatched = true;
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

    /**
     * Phase 1 measurement gate (ENSEMBLE_COHERENCE_DESIGN.md): classify the freshly-placed block's two
     * vertical pairs + its own occupancy visibility, once, at first judged pass. Emits ENSEMBLE_* rows
     * through the same sink as staleness reds — the class the 2026-07-07 video proved row-silent.
     */
    private static void emitEnsembleVerdicts(BlockGetter level, ArmedEntry entry, float liveDy,
                                             long nowTick, Consumer<LinkedHashMap<String, String>> sink) {
        BlockPos pos = entry.pos;
        BlockPos below = pos.below();
        BlockPos above = pos.above();
        double dyBelow = level.getBlockState(below).isAir() ? 0.0 : liveDyPolicy.dyFor(level, below, level.getBlockState(below));
        double dyAbove = level.getBlockState(above).isAir() ? 0.0 : liveDyPolicy.dyFor(level, above, level.getBlockState(above));
        // Break entries classify their pairAbove only: every neighbor of the break is itself armed, so
        // each vertical pair is judged exactly once (by its LOWER member) — no duplicate rows. The
        // placed entry keeps both pairs: its neighborhood entries never ensemble-classify.
        if (!REASON_BREAK.equals(entry.reason)) {
            SlabEnsembleCoherence.Verdict pairBelow = SlabEnsembleCoherence.classifyVerticalPair(level, below, dyBelow, liveDy);
            if (pairBelow.kind() != SlabEnsembleCoherence.Kind.COHERENT) {
                sink.accept(ensembleRow(entry, "ENSEMBLE_" + pairBelow.kind(), below, pos, dyBelow, liveDy,
                        pairBelow.depth(), level, nowTick));
            }
        }
        SlabEnsembleCoherence.Verdict pairAbove = SlabEnsembleCoherence.classifyVerticalPair(level, pos, liveDy, dyAbove);
        if (pairAbove.kind() != SlabEnsembleCoherence.Kind.COHERENT) {
            sink.accept(ensembleRow(entry, "ENSEMBLE_" + pairAbove.kind(), pos, above, liveDy, dyAbove,
                    pairAbove.depth(), level, nowTick));
        }
        if (SlabEnsembleCoherence.isOccludedOccupancy(level, pos, liveDy)) {
            sink.accept(ensembleRow(entry, "ENSEMBLE_" + SlabEnsembleCoherence.Kind.OCCLUDED_OCCUPANCY,
                    pos, pos, liveDy, liveDy, 0.0, level, nowTick));
        }
    }

    private static LinkedHashMap<String, String> ensembleRow(ArmedEntry entry, String kind,
                                                             BlockPos lowerPos, BlockPos upperPos,
                                                             double dyLower, double dyUpper, double depth,
                                                             BlockGetter level, long nowTick) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("kind", kind);
        row.put("pos", lowerPos.getX() + " " + lowerPos.getY() + " " + lowerPos.getZ());
        row.put("pairPos", upperPos.getX() + " " + upperPos.getY() + " " + upperPos.getZ());
        row.put("dyLower", Double.toString(dyLower));
        row.put("dyUpper", Double.toString(dyUpper));
        row.put("depth", Double.toString(depth));
        row.put("lowerState", level.getBlockState(lowerPos).toString());
        row.put("upperState", level.getBlockState(upperPos).toString());
        row.put("armedReason", entry.reason);
        row.put("ticksSinceArm", Long.toString(nowTick - entry.armedTick));
        return row;
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

    /** JVM-lifetime count of entries ever armed (recorder-summary liveness evidence). */
    public static long armedTotalCount() {
        return ARMED_TOTAL.get();
    }

    /** JVM-lifetime count of judging passes that ran past all gates (recorder-summary liveness). */
    public static long samplePassCount() {
        return SAMPLE_PASSES.get();
    }

    /** Armed-entry count (tests + /slabdev display). */
    public static int armedCount() {
        synchronized (LOCK) {
            return ARMED.size();
        }
    }

    /** Test seam: the latest bake sample for a position, or null if the mesher never fed one. The live
     *  self-check uses this to prove the emitQuads capture path is ALIVE — the one link the headless
     *  contract suite structurally cannot see (S7). */
    public static Float peekBakedDyForTest(long posKey) {
        BakeSample sample = BAKES.get(posKey);
        return sample == null ? null : sample.dy();
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
