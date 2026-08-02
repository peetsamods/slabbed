package com.slabbed.devtools.recording;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Bounded schema-6 probe for a rendered model that was not rebuilt after logical dy changed.
 * Arming and judging run on the client thread; model bake capture is lock-free after two cheap
 * gates and may run on Forge's chunk-mesh workers.
 */
public final class SlabModelStaleSentinel {
    public static final float DY_EPSILON = 1.0e-4f;
    public static final int SAMPLE_INTERVAL_TICKS = 20;
    public static final int RED_PERSIST_TICKS = 100;
    public static final int MIN_MISMATCH_SAMPLES = 2;
    public static final int ARM_RADIUS = 2;
    public static final int ARMED_CAP = 512;
    public static final int ARMED_TTL_TICKS = 1200;
    public static final int LEVEL_CHANGE_GRACE_TICKS = 200;
    public static final int INVALIDATE_GRACE_TICKS = 60;

    public static final String KIND_DIVERGENT = "MODEL_STALE_DIVERGENT";
    public static final String KIND_ABSENT = "MODEL_STALE_ABSENT";
    public static final String KIND_NO_BAKE_YELLOW = "MODEL_STALE_NO_BAKE_YELLOW";

    private static final Object LOCK = new Object();
    private static final long[] EMPTY_SNAPSHOT = new long[0];
    private static final LinkedHashMap<Long, ArmedEntry> ARMED = new LinkedHashMap<>();
    private static final ConcurrentHashMap<Long, BakeSample> BAKES = new ConcurrentHashMap<>();
    private static final AtomicLong ARMED_TOTAL = new AtomicLong();
    private static final AtomicLong SAMPLE_PASSES = new AtomicLong();

    private static volatile boolean armedNonEmpty;
    private static volatile long[] armedKeySnapshot = EMPTY_SNAPSHOT;
    private static volatile long suppressedUntilTick = Long.MIN_VALUE;

    private record BakeSample(float dy, long nanos) {
    }

    private static final class ArmedEntry {
        final BlockPos pos;
        final String reason;
        final Block armedBlock;
        final float baselineDy;
        final boolean hasBaseline;
        final long armedNanos;
        final long armedTick;
        int mismatchSamples;
        long firstMismatchTick;
        String mismatchKind;
        boolean redLatched;
        boolean yellowLatched;

        ArmedEntry(
                BlockPos pos,
                String reason,
                Block armedBlock,
                float baselineDy,
                boolean hasBaseline,
                long armedNanos,
                long armedTick) {
            this.pos = pos;
            this.reason = reason;
            this.armedBlock = armedBlock;
            this.baselineDy = baselineDy;
            this.hasBaseline = hasBaseline;
            this.armedNanos = armedNanos;
            this.armedTick = armedTick;
            this.firstMismatchTick = Long.MIN_VALUE;
        }
    }

    private SlabModelStaleSentinel() {
    }

    /** First hot-path gate: one volatile read and one live recorder-state read. */
    public static boolean shouldCapture() {
        return armedNonEmpty && SlabbedRecorder.isEnabled();
    }

    /** Lock-free membership lookup over a published immutable sorted snapshot. */
    public static boolean isArmed(long posKey) {
        long[] snapshot = armedKeySnapshot;
        return snapshot.length > 0 && Arrays.binarySearch(snapshot, posKey) >= 0;
    }

    /** Newest bake wins when multiple mesh workers complete out of order. */
    public static void recordBake(BlockPos pos, float bakedDy) {
        BakeSample sample = new BakeSample(bakedDy, System.nanoTime());
        BAKES.merge(pos.asLong(), sample,
                (existing, incoming) -> existing.nanos() >= incoming.nanos()
                        ? existing : incoming);
    }

    /** Arms the placement cell and non-air radius-2 neighbors before client mutation. */
    public static void armPlacement(BlockGetter world, BlockPos placementPos, long nowTick) {
        if (!SlabbedRecorder.isEnabled()) {
            return;
        }
        long nowNanos = System.nanoTime();
        synchronized (LOCK) {
            arm(new ArmedEntry(
                    placementPos.immutable(), "placement", null, 0.0f, false,
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
                        arm(new ArmedEntry(
                                cursor.immutable(),
                                "neighborhood",
                                state.getBlock(),
                                (float) SlabSupport.getYOffset(world, cursor, state),
                                true,
                                nowNanos,
                                nowTick));
                    }
                }
            }
            publishSnapshot();
        }
    }

    public static void maybeSample(
            BlockGetter world,
            long nowTick,
            Predicate<BlockPos> chunkLoaded) {
        if (nowTick % SAMPLE_INTERVAL_TICKS != 0L) {
            return;
        }
        samplePass(world, nowTick, chunkLoaded, SlabbedRecorder::recordSentinel);
    }

    public static void samplePass(
            BlockGetter world,
            long nowTick,
            Predicate<BlockPos> chunkLoaded,
            Consumer<LinkedHashMap<String, String>> rowSink) {
        if (!SlabbedRecorder.isEnabled() || nowTick < suppressedUntilTick) {
            return;
        }
        SAMPLE_PASSES.incrementAndGet();
        synchronized (LOCK) {
            Iterator<Map.Entry<Long, ArmedEntry>> iterator = ARMED.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, ArmedEntry> mapEntry = iterator.next();
                ArmedEntry entry = mapEntry.getValue();
                if (nowTick - entry.armedTick > ARMED_TTL_TICKS) {
                    iterator.remove();
                    BAKES.remove(mapEntry.getKey());
                    continue;
                }
                if (!chunkLoaded.test(entry.pos)) {
                    continue;
                }
                BlockState state = world.getBlockState(entry.pos);
                if (state.isAir()
                        || entry.armedBlock != null && state.getBlock() != entry.armedBlock) {
                    iterator.remove();
                    BAKES.remove(mapEntry.getKey());
                    continue;
                }
                float liveDy = (float) SlabSupport.getYOffset(world, entry.pos, state);
                judge(entry, BAKES.get(mapEntry.getKey()), liveDy, nowTick, rowSink);
            }
            publishSnapshot();
        }
        // Rows are already synchronously durable. Update liveness only; a general flush here would
        // prematurely finalize client attempts before their one-second server pairing window.
        SlabbedRecorder.updateSentinelLiveness();
    }

    public static void onWorldJoin(long nowTick) {
        resetCold();
        suppressedUntilTick = nowTick + LEVEL_CHANGE_GRACE_TICKS;
    }

    public static void onWorldLeave() {
        resetCold();
    }

    public static void onFullRenderInvalidation(long nowTick) {
        BAKES.clear();
        suppressedUntilTick = nowTick + INVALIDATE_GRACE_TICKS;
    }

    public static void onChunkUnload(int chunkX, int chunkZ) {
        synchronized (LOCK) {
            Iterator<Map.Entry<Long, ArmedEntry>> iterator = ARMED.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, ArmedEntry> entry = iterator.next();
                BlockPos pos = entry.getValue().pos;
                if ((pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ) {
                    iterator.remove();
                    BAKES.remove(entry.getKey());
                }
            }
            publishSnapshot();
        }
    }

    public static long armedTotalCount() {
        return ARMED_TOTAL.get();
    }

    public static long samplePassCount() {
        return SAMPLE_PASSES.get();
    }

    public static void resetCold() {
        synchronized (LOCK) {
            ARMED.clear();
            BAKES.clear();
            armedNonEmpty = false;
            armedKeySnapshot = EMPTY_SNAPSHOT;
            suppressedUntilTick = Long.MIN_VALUE;
            ARMED_TOTAL.set(0L);
            SAMPLE_PASSES.set(0L);
        }
    }

    // Pure contract seams used only by the addon verifier harness.
    static void armForTest(BlockPos pos, Float baselineDy, long armedTick) {
        synchronized (LOCK) {
            arm(new ArmedEntry(
                    pos.immutable(),
                    baselineDy == null ? "placement" : "neighborhood",
                    null,
                    baselineDy == null ? 0.0f : baselineDy,
                    baselineDy != null,
                    1L,
                    armedTick));
            publishSnapshot();
        }
    }

    static void recordBakeForTest(BlockPos pos, float dy, long nanos) {
        BAKES.merge(pos.asLong(), new BakeSample(dy, nanos),
                (existing, incoming) -> existing.nanos() >= incoming.nanos()
                        ? existing : incoming);
    }

    static void sampleForTest(
            BlockPos pos,
            float liveDy,
            long nowTick,
            Consumer<LinkedHashMap<String, String>> rowSink) {
        synchronized (LOCK) {
            ArmedEntry entry = ARMED.get(pos.asLong());
            if (entry != null) {
                SAMPLE_PASSES.incrementAndGet();
                judge(entry, BAKES.get(pos.asLong()), liveDy, nowTick, rowSink);
            }
        }
    }

    private static void judge(
            ArmedEntry entry,
            BakeSample bake,
            float liveDy,
            long nowTick,
            Consumer<LinkedHashMap<String, String>> rowSink) {
        BakeSample freshBake = bake != null && bake.nanos() >= entry.armedNanos ? bake : null;
        String kind = null;
        float bakedDy = Float.NaN;
        if (freshBake != null && differs(freshBake.dy(), liveDy)) {
            kind = KIND_DIVERGENT;
            bakedDy = freshBake.dy();
        } else if (freshBake == null && entry.hasBaseline && differs(entry.baselineDy, liveDy)) {
            kind = KIND_ABSENT;
        }

        if (kind == null) {
            entry.mismatchSamples = 0;
            entry.firstMismatchTick = Long.MIN_VALUE;
            entry.mismatchKind = null;
            if (freshBake == null
                    && !entry.hasBaseline
                    && !entry.yellowLatched
                    && nowTick - entry.armedTick >= RED_PERSIST_TICKS) {
                entry.yellowLatched = true;
                rowSink.accept(row(entry, KIND_NO_BAKE_YELLOW, Float.NaN, liveDy, nowTick));
            }
            return;
        }

        if (!kind.equals(entry.mismatchKind)) {
            entry.mismatchKind = kind;
            entry.mismatchSamples = 1;
            entry.firstMismatchTick = nowTick;
        } else {
            entry.mismatchSamples++;
        }
        if (!entry.redLatched
                && entry.mismatchSamples >= MIN_MISMATCH_SAMPLES
                && nowTick - entry.firstMismatchTick >= RED_PERSIST_TICKS) {
            entry.redLatched = true;
            rowSink.accept(row(entry, kind, bakedDy, liveDy, nowTick));
        }
    }

    private static LinkedHashMap<String, String> row(
            ArmedEntry entry,
            String kind,
            float bakedDy,
            float liveDy,
            long nowTick) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("kind", kind);
        row.put("pos", entry.pos.toShortString());
        row.put("reason", entry.reason);
        row.put("armedTick", Long.toString(entry.armedTick));
        row.put("sampleTick", Long.toString(nowTick));
        row.put("baselineDy", entry.hasBaseline ? Float.toString(entry.baselineDy) : "none");
        row.put("bakedDy", Float.isFinite(bakedDy) ? Float.toString(bakedDy) : "none");
        row.put("liveDy", Float.toString(liveDy));
        row.put("mismatchSamples", Integer.toString(entry.mismatchSamples));
        return row;
    }

    private static boolean differs(float first, float second) {
        return Math.abs(first - second) > DY_EPSILON;
    }

    private static void arm(ArmedEntry entry) {
        ARMED_TOTAL.incrementAndGet();
        long key = entry.pos.asLong();
        ARMED.remove(key);
        ARMED.put(key, entry);
        BAKES.remove(key);
        if (ARMED.size() > ARMED_CAP) {
            Iterator<Long> iterator = ARMED.keySet().iterator();
            Long evicted = iterator.next();
            iterator.remove();
            BAKES.remove(evicted);
        }
    }

    private static void publishSnapshot() {
        long[] keys = new long[ARMED.size()];
        int index = 0;
        for (Long key : ARMED.keySet()) {
            keys[index++] = key;
        }
        Arrays.sort(keys);
        armedKeySnapshot = keys.length == 0 ? EMPTY_SNAPSHOT : keys;
        armedNonEmpty = keys.length > 0;
    }
}
