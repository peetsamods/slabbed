package com.slabbed.anchor;

import java.util.Arrays;

/**
 * Immutable attachment values published to render readers. A captured view never reads a live
 * chunk or a mutable attachment map; writers copy only the changed chunk and the chunk index.
 */
public final class PublishedRenderAttachments {
    private static final long[] NO_KEYS = new long[0];
    private static final SlabAnchorAttachment.PlacementDyFact[] NO_FACTS =
            new SlabAnchorAttachment.PlacementDyFact[0];

    private record Chunk(Object owner, long[] positions,
                         SlabAnchorAttachment.PlacementDyFact[] facts, long[][] markers) {
    }

    public static final class View {
        private final Object level;
        private final long[] keys;
        private final Chunk[] chunks;

        private View(Object level, long[] keys, Chunk[] chunks) {
            this.level = level;
            this.keys = keys;
            this.chunks = chunks;
        }

        public Object level() {
            return level;
        }

        private Chunk chunk(Object expectedLevel, long key) {
            if (expectedLevel != level || level == null) {
                return null;
            }
            int index = Arrays.binarySearch(keys, key);
            return index < 0 ? null : chunks[index];
        }

        public SlabAnchorAttachment.PlacementDyFact fact(Object expectedLevel, long chunkKey, long position) {
            Chunk chunk = chunk(expectedLevel, chunkKey);
            if (chunk != null) {
                int index = Arrays.binarySearch(chunk.positions(), position);
                if (index >= 0) {
                    return chunk.facts()[index];
                }
            }
            return SlabAnchorAttachment.PlacementDyFact.absent();
        }

        public boolean contains(Object expectedLevel, long chunkKey, int marker, long position) {
            Chunk chunk = chunk(expectedLevel, chunkKey);
            return chunk != null && marker >= 0 && marker < chunk.markers().length
                    && Arrays.binarySearch(chunk.markers()[marker], position) >= 0;
        }
    }

    private volatile View published = new View(null, NO_KEYS, new Chunk[0]);

    public View capture() {
        return published;
    }

    public synchronized void reset(Object level) {
        published = new View(level, NO_KEYS, new Chunk[0]);
    }

    /** Repeated activation of the same level must retain chunks already published at load time. */
    public synchronized void activate(Object level) {
        if (published.level != level) {
            reset(level);
        }
    }

    public synchronized void load(Object level, long key, Object owner,
                                  long[] positions, long[] rawBits, long[][] markers) {
        View prior = published;
        if (prior.level != level || level == null) {
            return;
        }
        Chunk chunk = withDy(owner, positions, rawBits, copyMarkers(markers));
        int index = Arrays.binarySearch(prior.keys, key);
        if (index >= 0) {
            replace(prior, index, chunk);
            return;
        }
        index = -index - 1;
        long[] keys = new long[prior.keys.length + 1];
        Chunk[] chunks = new Chunk[keys.length];
        System.arraycopy(prior.keys, 0, keys, 0, index);
        System.arraycopy(prior.chunks, 0, chunks, 0, index);
        keys[index] = key;
        chunks[index] = chunk;
        System.arraycopy(prior.keys, index, keys, index + 1, prior.keys.length - index);
        System.arraycopy(prior.chunks, index, chunks, index + 1, prior.chunks.length - index);
        published = new View(level, keys, chunks);
    }

    public synchronized boolean updateDy(Object level, long key, Object owner, long[] positions, long[] rawBits) {
        View prior = published;
        int index = ownedIndex(prior, level, key, owner);
        if (index >= 0) {
            replace(prior, index, withDy(owner, positions, rawBits, prior.chunks[index].markers()));
            return true;
        }
        return false;
    }

    public synchronized boolean updateMarker(Object level, long key, Object owner, int marker, long[] positions) {
        View prior = published;
        int index = ownedIndex(prior, level, key, owner);
        if (index >= 0) {
            Chunk old = prior.chunks[index];
            long[][] markers = old.markers().clone();
            markers[marker] = sortedCopy(positions);
            replace(prior, index, new Chunk(owner, old.positions(), old.facts(), markers));
            return true;
        }
        return false;
    }

    public synchronized void unload(Object level, long key, Object owner) {
        View prior = published;
        int index = ownedIndex(prior, level, key, owner);
        if (index < 0) {
            return;
        }
        long[] keys = new long[prior.keys.length - 1];
        Chunk[] chunks = new Chunk[keys.length];
        System.arraycopy(prior.keys, 0, keys, 0, index);
        System.arraycopy(prior.chunks, 0, chunks, 0, index);
        System.arraycopy(prior.keys, index + 1, keys, index, keys.length - index);
        System.arraycopy(prior.chunks, index + 1, chunks, index, chunks.length - index);
        published = new View(level, keys, chunks);
    }

    private static int ownedIndex(View view, Object level, long key, Object owner) {
        if (level == null || level != view.level) {
            return -1;
        }
        int index = Arrays.binarySearch(view.keys, key);
        return index >= 0 && view.chunks[index].owner() == owner ? index : -1;
    }

    private void replace(View prior, int index, Chunk chunk) {
        Chunk[] chunks = prior.chunks.clone();
        chunks[index] = chunk;
        published = new View(prior.level, prior.keys, chunks);
    }

    private static Chunk withDy(Object owner, long[] positions, long[] rawBits, long[][] markers) {
        if (positions.length != rawBits.length) {
            throw new IllegalArgumentException("placement positions and facts differ in length");
        }
        long[] keys = sortedCopy(positions);
        SlabAnchorAttachment.PlacementDyFact[] facts = keys.length == 0 ? NO_FACTS
                : new SlabAnchorAttachment.PlacementDyFact[keys.length];
        for (int i = 0; i < positions.length; i++) {
            facts[Arrays.binarySearch(keys, positions[i])] =
                    new SlabAnchorAttachment.PlacementDyFact(true, rawBits[i]);
        }
        return new Chunk(owner, keys, facts, markers);
    }

    private static long[] sortedCopy(long[] positions) {
        if (positions.length == 0) {
            return NO_KEYS;
        }
        long[] copy = positions.clone();
        Arrays.sort(copy);
        return copy;
    }

    private static long[][] copyMarkers(long[][] markers) {
        long[][] copy = new long[markers.length][];
        for (int i = 0; i < markers.length; i++) {
            copy[i] = sortedCopy(markers[i]);
        }
        return copy;
    }
}
