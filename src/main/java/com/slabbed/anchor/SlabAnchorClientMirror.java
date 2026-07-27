package com.slabbed.anchor;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-side mirror for server-owned Slabbed anchor marker buckets.
 *
 * <p>The server {@link SlabAnchorStore} remains authoritative. This mirror is a
 * compact client read model keyed by dimension, chunk, and marker so client
 * {@code Level} queries and client non-Level render-view predicates can see the
 * same anchor truth after Forge network sync. It does not own dy or support law.</p>
 */
public final class SlabAnchorClientMirror {
    private static final ConcurrentMap<BucketKey, LongOpenHashSet> BUCKETS =
            new ConcurrentHashMap<>();

    /**
     * The mirrored placement-dy maps (STAYS Phase 5), keyed by dimension and chunk. Written on
     * the client main thread by the sync handlers; read from chunk-mesh worker threads through
     * the render seam and from client Level queries. Same discipline as BUCKETS: values are
     * replaced WHOLESALE (full sync) or COPY-ON-WRITE (deltas) — a map instance is never mutated
     * after publication, so readers always see a consistent snapshot.
     */
    private static final ConcurrentMap<DyKey, Long2DoubleOpenHashMap> DY_MAPS =
            new ConcurrentHashMap<>();

    /**
     * Client-only render-invalidation sink. Installed by the physical client
     * ({@link SlabAnchorClientMirrorEvents}) and left {@code null} on dedicated
     * servers so this common class stays server-safe. Invoked with every packed
     * {@link BlockPos} whose marker membership changed on a bucket sync, so the
     * offset models at those positions re-bake with the freshly-synced anchor
     * truth. Without this, a block placed before its anchor/marker syncs bakes
     * its model at the stale (pre-anchor) dy and never re-meshes until an
     * unrelated block change forces it — the "correct raycast, wrong model until
     * break+replace" bug.
     */
    public static volatile LongConsumer renderInvalidationSink;

    private SlabAnchorClientMirror() {
    }

    public static void applyBucket(
            ResourceLocation dimension,
            int chunkX,
            int chunkZ,
            SlabAnchorMarker marker,
            long[] positions
    ) {
        if (dimension == null || marker == null) {
            return;
        }
        BucketKey key = new BucketKey(dimension, chunkX, chunkZ, marker);
        LongOpenHashSet incoming = positions == null ? new LongOpenHashSet() : new LongOpenHashSet(positions);
        LongOpenHashSet previous = incoming.isEmpty() ? BUCKETS.remove(key) : BUCKETS.put(key, incoming);
        invalidateChangedRenders(previous, incoming);
    }

    /**
     * Marks every position whose marker membership was added or removed for a
     * client render refresh, so its offset model re-bakes with the new truth.
     * No-op on servers (sink null) or when nothing changed.
     */
    private static void invalidateChangedRenders(LongOpenHashSet previous, LongOpenHashSet incoming) {
        LongConsumer sink = renderInvalidationSink;
        if (sink == null) {
            return;
        }
        if (previous != null) {
            for (long pos : previous) {
                if (!incoming.contains(pos)) {
                    sink.accept(pos);
                }
            }
        }
        for (long pos : incoming) {
            if (previous == null || !previous.contains(pos)) {
                sink.accept(pos);
            }
        }
    }

    public static LongOpenHashSet copy(
            ResourceLocation dimension,
            int chunkX,
            int chunkZ,
            SlabAnchorMarker marker
    ) {
        if (dimension == null || marker == null) {
            return new LongOpenHashSet();
        }
        LongOpenHashSet set = BUCKETS.get(new BucketKey(dimension, chunkX, chunkZ, marker));
        return set == null ? new LongOpenHashSet() : new LongOpenHashSet(set);
    }

    public static boolean contains(
            ResourceLocation dimension,
            SlabAnchorMarker marker,
            BlockPos pos
    ) {
        if (dimension == null || marker == null || pos == null) {
            return false;
        }
        LongOpenHashSet set = BUCKETS.get(new BucketKey(
                dimension,
                pos.getX() >> 4,
                pos.getZ() >> 4,
                marker));
        return set != null && set.contains(pos.asLong());
    }

    /**
     * Test-hygiene/symmetry entry point with NO production caller — production cleanup is the
     * server-pushed empty full on unwatch plus {@link #clearAll} on login/logout. Recorded so a
     * future sweep does not misread this as a wiring gap.
     */
    public static void clearChunk(ResourceLocation dimension, int chunkX, int chunkZ) {
        if (dimension == null) {
            return;
        }
        for (SlabAnchorMarker marker : SlabAnchorMarker.values()) {
            BUCKETS.remove(new BucketKey(dimension, chunkX, chunkZ, marker));
        }
        DY_MAPS.remove(new DyKey(dimension, chunkX, chunkZ));
    }

    public static void clearAll() {
        BUCKETS.clear();
        DY_MAPS.clear();
    }

    /** Full per-chunk dy sync. A corrupt pair (length mismatch) is dropped WHOLE, like the store. */
    public static void applyPlacementDyFull(
            ResourceLocation dimension,
            int chunkX,
            int chunkZ,
            long[] positions,
            long[] bits
    ) {
        if (dimension == null || positions == null || bits == null
                || positions.length != bits.length) {
            return;
        }
        DyKey key = new DyKey(dimension, chunkX, chunkZ);
        Long2DoubleOpenHashMap incoming = new Long2DoubleOpenHashMap(positions.length);
        incoming.defaultReturnValue(Double.NaN);
        for (int i = 0; i < positions.length; i++) {
            incoming.put(positions[i], Double.longBitsToDouble(bits[i]));
        }
        Long2DoubleOpenHashMap previous =
                incoming.isEmpty() ? DY_MAPS.remove(key) : DY_MAPS.put(key, incoming);
        invalidateChangedDy(previous, incoming);
    }

    /**
     * One placement's delta. IDEMPOTENT: re-applying identical raw bits publishes nothing and
     * fires no re-mesh. Copy-on-write — placements are events, not a hot path.
     */
    public static void applyPlacementDyDelta(
            ResourceLocation dimension,
            long packedPos,
            boolean present,
            long rawBits
    ) {
        if (dimension == null) {
            return;
        }
        DyKey key = new DyKey(dimension, BlockPos.getX(packedPos) >> 4, BlockPos.getZ(packedPos) >> 4);
        Long2DoubleOpenHashMap current = DY_MAPS.get(key);
        if (present) {
            if (current != null && current.containsKey(packedPos)
                    && Double.doubleToRawLongBits(current.get(packedPos)) == rawBits) {
                return;
            }
            Long2DoubleOpenHashMap next = current == null
                    ? new Long2DoubleOpenHashMap() : new Long2DoubleOpenHashMap(current);
            next.defaultReturnValue(Double.NaN);
            next.put(packedPos, Double.longBitsToDouble(rawBits));
            DY_MAPS.put(key, next);
        } else {
            if (current == null || !current.containsKey(packedPos)) {
                return;
            }
            Long2DoubleOpenHashMap next = new Long2DoubleOpenHashMap(current);
            next.defaultReturnValue(Double.NaN);
            next.remove(packedPos);
            if (next.isEmpty()) {
                DY_MAPS.remove(key);
            } else {
                DY_MAPS.put(key, next);
            }
        }
        LongConsumer sink = renderInvalidationSink;
        if (sink != null) {
            sink.accept(packedPos);
        }
    }

    /**
     * The mirrored dy at the packed position, or {@code NaN} when absent. Allocation-lean by
     * review requirement: chunk coords come from the {@code BlockPos.getX/getZ(long)} statics,
     * no intermediate BlockPos — this is the designated mesher read and must stay cheap before
     * the render wiring makes it hot. The DyKey record matches the accepted BucketKey per-read
     * cost of the marker seam.
     */
    public static double placementDy(ResourceLocation dimension, long packedPos) {
        if (dimension == null) {
            return Double.NaN;
        }
        Long2DoubleOpenHashMap map = DY_MAPS.get(
                new DyKey(dimension, BlockPos.getX(packedPos) >> 4, BlockPos.getZ(packedPos) >> 4));
        return map == null ? Double.NaN : map.get(packedPos);
    }

    private static void invalidateChangedDy(
            Long2DoubleOpenHashMap previous, Long2DoubleOpenHashMap incoming) {
        LongConsumer sink = renderInvalidationSink;
        if (sink == null) {
            return;
        }
        if (previous != null) {
            for (long pos : previous.keySet()) {
                if (!incoming.containsKey(pos)
                        || Double.doubleToRawLongBits(incoming.get(pos))
                                != Double.doubleToRawLongBits(previous.get(pos))) {
                    sink.accept(pos);
                }
            }
        }
        for (long pos : incoming.keySet()) {
            if (previous == null || !previous.containsKey(pos)) {
                sink.accept(pos);
            }
        }
    }

    private record DyKey(ResourceLocation dimension, int chunkX, int chunkZ) {
    }

    private record BucketKey(
            ResourceLocation dimension,
            int chunkX,
            int chunkZ,
            SlabAnchorMarker marker
    ) {
    }
}
