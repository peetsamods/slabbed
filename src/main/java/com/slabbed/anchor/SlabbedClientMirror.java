package com.slabbed.anchor;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Client-side landing point for everything {@link SlabbedAnchorNetwork} sends.
 *
 * <p>A NeoForge attachment arrives with the chunk and is readable straight off the client chunk.
 * A Forge capability is not synchronized, so the client copy lives here instead of on the chunk:
 * keyed by dimension and chunk position, it does not depend on the client chunk already existing
 * when a packet lands, and it survives the chunk being dropped and re-sent.
 *
 * <p>This mirror is a render/prediction input only. It is never authoritative: server-side law
 * reads {@link SlabbedChunkStore} through the capability, never this class.
 */
public final class SlabbedClientMirror {
    private static final Map<Key, Entry> ENTRIES = new ConcurrentHashMap<>();

    @Nullable
    private static volatile DeepDyConsentAttachment.Stamp consentStamp;

    private SlabbedClientMirror() {
    }

    // ------------------------------------------------------------------ reads

    /** Mirrored bucket for the chunk, or null when nothing has been received for it. */
    @Nullable
    public static LongOpenHashSet markerOrNull(@Nullable LevelChunk chunk, SlabAnchorMarker marker) {
        Entry entry = entryFor(chunk);
        if (entry == null) {
            return null;
        }
        LongOpenHashSet set = entry.buckets.get(marker);
        return set == null || set.isEmpty() ? null : set;
    }

    /** Mirrored placement-height map for the chunk, or null when none has been received. */
    @Nullable
    public static Long2ByteOpenHashMap placementDyOrNull(@Nullable LevelChunk chunk) {
        Entry entry = entryFor(chunk);
        if (entry == null) {
            return null;
        }
        Long2ByteOpenHashMap map = entry.placementDy;
        return map == null || map.isEmpty() ? null : map;
    }

    @Nullable
    public static DeepDyConsentAttachment.Stamp consentStamp() {
        return consentStamp;
    }

    // ----------------------------------------------------------------- writes

    public static void applyBucket(
            ResourceLocation dimension, int chunkX, int chunkZ,
            SlabAnchorMarker marker, long[] positions
    ) {
        Key key = new Key(dimension, ChunkPos.asLong(chunkX, chunkZ));
        if (positions.length == 0) {
            Entry entry = ENTRIES.get(key);
            if (entry != null) {
                entry.buckets = withMarker(entry.buckets, marker, null);
                dropIfEmpty(key, entry);
            }
            return;
        }
        Entry entry = ENTRIES.computeIfAbsent(key, k -> new Entry());
        entry.buckets = withMarker(entry.buckets, marker, new LongOpenHashSet(positions));
    }

    public static void applyPlacementFull(
            ResourceLocation dimension, int chunkX, int chunkZ,
            long[] positions, byte[] halfSteps
    ) {
        Key key = new Key(dimension, ChunkPos.asLong(chunkX, chunkZ));
        if (positions.length == 0) {
            Entry entry = ENTRIES.get(key);
            if (entry != null) {
                entry.placementDy = null;
                dropIfEmpty(key, entry);
            }
            return;
        }
        Long2ByteOpenHashMap map = new Long2ByteOpenHashMap(positions.length);
        for (int i = 0; i < positions.length; i++) {
            map.put(positions[i], halfSteps[i]);
        }
        ENTRIES.computeIfAbsent(key, k -> new Entry()).placementDy = map;
    }

    public static void applyPlacementDelta(
            ResourceLocation dimension, long packedPos, boolean present, byte halfSteps
    ) {
        long chunkKey = ChunkPos.asLong(
                net.minecraft.core.BlockPos.getX(packedPos) >> 4,
                net.minecraft.core.BlockPos.getZ(packedPos) >> 4);
        Key key = new Key(dimension, chunkKey);
        if (!present) {
            Entry entry = ENTRIES.get(key);
            if (entry != null && entry.placementDy != null
                    && entry.placementDy.containsKey(packedPos)) {
                Long2ByteOpenHashMap next = new Long2ByteOpenHashMap(entry.placementDy);
                next.remove(packedPos);
                entry.placementDy = next.isEmpty() ? null : next;
                dropIfEmpty(key, entry);
            }
            return;
        }
        Entry entry = ENTRIES.computeIfAbsent(key, k -> new Entry());
        Long2ByteOpenHashMap current = entry.placementDy;
        if (current != null && current.containsKey(packedPos)
                && current.get(packedPos) == halfSteps) {
            return;
        }
        // Copy-on-write, deliberately. A published map is never mutated again: the render sync
        // detects change by reference identity, and the packet handler and the mesh reader touch
        // this map from different threads. Mutating in place breaks both at once.
        Long2ByteOpenHashMap next = current == null
                ? new Long2ByteOpenHashMap()
                : new Long2ByteOpenHashMap(current);
        next.put(packedPos, halfSteps);
        entry.placementDy = next;
    }

    public static void applyConsent(@Nullable DeepDyConsentAttachment.Stamp stamp) {
        consentStamp = stamp;
        DeepDyConsentAttachment.acceptClientStamp(stamp);
    }

    /**
     * Drops every lane for one chunk.
     *
     * <p>The unwatch clear. It is what lets the watch send skip empty lanes: a chunk the mirror
     * holds nothing for reads as absent, so the next watch only has to name what is there.
     */
    public static void clearChunk(ResourceLocation dimension, int chunkX, int chunkZ) {
        ENTRIES.remove(new Key(dimension, ChunkPos.asLong(chunkX, chunkZ)));
    }

    /** Drops everything. Called on disconnect so a second world never reads the first one's facts. */
    public static void clear() {
        ENTRIES.clear();
        consentStamp = null;
    }

    // --------------------------------------------------------------- internals

    @Nullable
    private static Entry entryFor(@Nullable LevelChunk chunk) {
        if (chunk == null) {
            return null;
        }
        Level level = chunk.getLevel();
        if (level == null) {
            return null;
        }
        return ENTRIES.get(new Key(level.dimension().location(), chunk.getPos().toLong()));
    }

    /**
     * The bucket map with one marker replaced, or removed when {@code set} is null. Returns a
     * fresh map every time: the caller publishes it by assigning {@link Entry#buckets}, and the
     * published map is never touched again. Bounded by the eight marker kinds.
     */
    private static Map<SlabAnchorMarker, LongOpenHashSet> withMarker(
            Map<SlabAnchorMarker, LongOpenHashSet> current,
            SlabAnchorMarker marker,
            @Nullable LongOpenHashSet set
    ) {
        EnumMap<SlabAnchorMarker, LongOpenHashSet> next = new EnumMap<>(SlabAnchorMarker.class);
        next.putAll(current);
        if (set == null) {
            next.remove(marker);
        } else {
            next.put(marker, set);
        }
        return next.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(next);
    }

    private static void dropIfEmpty(Key key, Entry entry) {
        if (entry.buckets.isEmpty() && entry.placementDy == null) {
            ENTRIES.remove(key, entry);
        }
    }

    private static final class Entry {
        /**
         * Swapped wholesale, never mutated after publication. The packet handler writes this on
         * the client main thread while the render fallback lookups read it from mesh-builder
         * worker threads, and nothing else orders the two. Putting into the map in place
         * publishes both the map slot and the freshly built set unsafely, so a worker can read a
         * half-built bucket; this volatile write paired with the reader's volatile read is the
         * whole of the ordering. Same discipline as {@link #placementDy}.
         */
        private volatile Map<SlabAnchorMarker, LongOpenHashSet> buckets = Collections.emptyMap();
        @Nullable
        private volatile Long2ByteOpenHashMap placementDy;
    }

    private record Key(ResourceLocation dimension, long chunkPos) {
    }
}
