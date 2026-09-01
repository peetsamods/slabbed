package com.slabbed.anchor;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Per-chunk Slabbed storage behind one Forge capability.
 *
 * <p>This is the Forge 1.20.1 stand-in for the NeoForge data attachments the donor line stores on
 * a chunk: the eight boolean marker buckets and the value-carrying placement-height map. NeoForge
 * synchronizes an attachment on its own; a Forge capability does not, so every mutation here
 * announces itself through {@link SlabbedAnchorNetwork} and the client landing point is
 * {@link SlabbedClientMirror}. Nothing in this class decides gameplay: it stores facts other code
 * has already ruled on.
 *
 * <p>The placement map is signed half-steps in a byte, matching the 0.5.x representation. It is
 * lazily allocated, so a chunk with no authored heights costs one null field.
 */
public final class SlabbedChunkStore {
    private static final String PLACEMENT_DY_POS_KEY = "placement_dy_pos";
    private static final String PLACEMENT_DY_STEPS_KEY = "placement_dy_steps";

    private final LevelChunk owner;
    private final Map<SlabAnchorMarker, LongOpenHashSet> buckets =
            new EnumMap<>(SlabAnchorMarker.class);

    @Nullable
    private volatile Long2ByteOpenHashMap placementDy;

    public SlabbedChunkStore(LevelChunk owner) {
        this.owner = owner;
    }

    // ---------------------------------------------------------------- markers

    /** The live bucket, or null when the marker has never been written. Never mutate the result. */
    @Nullable
    public LongOpenHashSet markerOrNull(SlabAnchorMarker marker) {
        LongOpenHashSet set = buckets.get(marker);
        return set == null || set.isEmpty() ? null : set;
    }

    /**
     * Replaces a whole bucket. Copy-on-write, mirroring the donor's attachment contract: callers
     * hand over a set they built, and the store owns the copy from here on.
     */
    public void putMarker(SlabAnchorMarker marker, @Nullable LongOpenHashSet positions) {
        LongOpenHashSet set = positions == null ? new LongOpenHashSet() : new LongOpenHashSet(positions);
        if (set.isEmpty()) {
            buckets.remove(marker);
        } else {
            buckets.put(marker, set);
        }
        markUnsaved();
        SlabbedAnchorNetwork.syncBucket(owner, marker, set);
    }

    public void removeMarker(SlabAnchorMarker marker) {
        if (buckets.remove(marker) != null) {
            markUnsaved();
            SlabbedAnchorNetwork.syncBucket(owner, marker, new LongOpenHashSet());
        }
    }

    /** Bucket snapshot for the full chunk-watch sync. Empty array when unset. */
    public long[] markerPositions(SlabAnchorMarker marker) {
        LongOpenHashSet set = buckets.get(marker);
        return set == null ? new long[0] : set.toLongArray();
    }

    // ----------------------------------------------------------- placement dy

    /** The live placement map, or null when this chunk holds no authored heights. */
    @Nullable
    public Long2ByteOpenHashMap placementDyOrNull() {
        Long2ByteOpenHashMap map = placementDy;
        return map == null || map.isEmpty() ? null : map;
    }

    /**
     * Replaces the whole placement map and sends the full form.
     *
     * <p>The 0.5.x writer rebuilds the map and hands it over wholesale, which is why this exists
     * alongside the delta path. Prefer {@link #putPlacementDy} and {@link #removePlacementDy} on
     * the single-placement route: a whole-map resend per placement is the cliff the donor's
     * Phase 5 delta was written to remove.
     */
    public void putPlacementMap(@Nullable Long2ByteOpenHashMap replacement) {
        // Stored BY REFERENCE, matching the donor's whole-map setter. The caller hands the map
        // over and the store adopts it; a defensive copy here would silently detach the caller's
        // object from the store and is not the contract the suite is written against.
        placementDy = replacement == null || replacement.isEmpty() ? null : replacement;
        markUnsaved();
        SlabbedAnchorNetwork.syncPlacementFull(owner);
    }

    public void removePlacementMap() {
        if (placementDy != null) {
            placementDy = null;
            markUnsaved();
            SlabbedAnchorNetwork.syncPlacementFull(owner);
        }
    }

    /** Stores one authored height. Returns true when the stored half-step count changed. */
    public boolean putPlacementDy(long packedPos, byte halfSteps) {
        Long2ByteOpenHashMap current = placementDy;
        if (current != null && current.containsKey(packedPos) && current.get(packedPos) == halfSteps) {
            return false;
        }
        // Copy-on-write. A published map is never mutated again: readers hold it without a lock
        // and the render path detects change by reference identity, so an in-place put is both a
        // race and an invisible change.
        Long2ByteOpenHashMap next = current == null
                ? new Long2ByteOpenHashMap()
                : new Long2ByteOpenHashMap(current);
        next.put(packedPos, halfSteps);
        placementDy = next;
        markUnsaved();
        SlabbedAnchorNetwork.syncPlacementDelta(owner, packedPos, true, halfSteps);
        return true;
    }

    /** Clears one authored height. Returns true when an entry was removed. */
    public boolean removePlacementDy(long packedPos) {
        Long2ByteOpenHashMap current = placementDy;
        if (current == null || !current.containsKey(packedPos)) {
            return false;
        }
        Long2ByteOpenHashMap next = new Long2ByteOpenHashMap(current);
        next.remove(packedPos);
        placementDy = next.isEmpty() ? null : next;
        markUnsaved();
        SlabbedAnchorNetwork.syncPlacementDelta(owner, packedPos, false, (byte) 0);
        return true;
    }

    /** Placement map as parallel arrays [positions, halfSteps] for the full sync. */
    public long[] placementPositions() {
        Long2ByteOpenHashMap map = placementDy;
        if (map == null || map.isEmpty()) {
            return new long[0];
        }
        long[] positions = new long[map.size()];
        int i = 0;
        for (Long2ByteMap.Entry e : map.long2ByteEntrySet()) {
            positions[i++] = e.getLongKey();
        }
        return positions;
    }

    public byte[] placementHalfSteps() {
        Long2ByteOpenHashMap map = placementDy;
        if (map == null || map.isEmpty()) {
            return new byte[0];
        }
        byte[] steps = new byte[map.size()];
        int i = 0;
        for (Long2ByteMap.Entry e : map.long2ByteEntrySet()) {
            steps[i++] = e.getByteValue();
        }
        return steps;
    }

    // ------------------------------------------------------------ persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (SlabAnchorMarker marker : SlabAnchorMarker.values()) {
            LongOpenHashSet set = buckets.get(marker);
            if (set != null && !set.isEmpty()) {
                tag.putLongArray(marker.nbtKey(), set.toLongArray());
            }
        }
        Long2ByteOpenHashMap map = placementDy;
        if (map != null && !map.isEmpty()) {
            tag.putLongArray(PLACEMENT_DY_POS_KEY, placementPositions());
            tag.putByteArray(PLACEMENT_DY_STEPS_KEY, placementHalfSteps());
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        buckets.clear();
        for (SlabAnchorMarker marker : SlabAnchorMarker.values()) {
            if (tag.contains(marker.nbtKey())) {
                LongOpenHashSet set = new LongOpenHashSet(tag.getLongArray(marker.nbtKey()));
                if (!set.isEmpty()) {
                    buckets.put(marker, set);
                }
            }
        }
        placementDy = null;
        if (tag.contains(PLACEMENT_DY_POS_KEY) && tag.contains(PLACEMENT_DY_STEPS_KEY)) {
            long[] positions = tag.getLongArray(PLACEMENT_DY_POS_KEY);
            byte[] steps = tag.getByteArray(PLACEMENT_DY_STEPS_KEY);
            if (positions.length == steps.length && positions.length > 0) {
                Long2ByteOpenHashMap map = new Long2ByteOpenHashMap(positions.length);
                for (int i = 0; i < positions.length; i++) {
                    map.put(positions[i], steps[i]);
                }
                placementDy = map;
            }
            // A length mismatch means the pair is corrupt. Drop it whole rather than applying a
            // prefix: positions matched to the wrong heights would author wrong values silently,
            // which is worse than authored blocks falling back to geometric derivation.
        }
    }

    private void markUnsaved() {
        owner.setUnsaved(true);
    }
}
