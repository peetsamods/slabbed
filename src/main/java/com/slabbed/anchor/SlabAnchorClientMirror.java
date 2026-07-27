package com.slabbed.anchor;

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

    public static void clearChunk(ResourceLocation dimension, int chunkX, int chunkZ) {
        if (dimension == null) {
            return;
        }
        for (SlabAnchorMarker marker : SlabAnchorMarker.values()) {
            BUCKETS.remove(new BucketKey(dimension, chunkX, chunkZ, marker));
        }
    }

    public static void clearAll() {
        BUCKETS.clear();
    }

    private record BucketKey(
            ResourceLocation dimension,
            int chunkX,
            int chunkZ,
            SlabAnchorMarker marker
    ) {
    }
}
