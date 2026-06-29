package com.slabbed.anchor;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-side mirror for server-owned Slabbed anchor marker buckets.
 *
 * <p>The server {@link SlabAnchorStore} remains authoritative. This mirror is a
 * compact client read model keyed by dimension, chunk, and marker so client
 * {@code Level} queries can see the same anchor truth after Forge network sync.
 * Non-Level render-view predicates are intentionally not wired in this slice.</p>
 */
public final class SlabAnchorClientMirror {
    private static final ConcurrentMap<BucketKey, LongOpenHashSet> BUCKETS =
            new ConcurrentHashMap<>();

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
        LongOpenHashSet set = positions == null ? new LongOpenHashSet() : new LongOpenHashSet(positions);
        if (set.isEmpty()) {
            BUCKETS.remove(key);
        } else {
            BUCKETS.put(key, set);
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
