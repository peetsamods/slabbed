package com.slabbed.anchor;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Server-side Forge replacement for the donor's per-chunk NeoForge data
 * attachment storage.
 *
 * <p>One {@link SlabAnchorStore} lives on one {@link LevelChunk} capability and
 * owns all eight Slabbed anchor marker buckets. Networking/client mirrors and
 * gameplay-facing attachment migration are intentionally separate Book III
 * slices.</p>
 */
public final class SlabAnchorStore {
    private final LevelChunk owner;
    private final Map<SlabAnchorMarker, LongOpenHashSet> buckets =
            new EnumMap<>(SlabAnchorMarker.class);

    /**
     * The NINTH, VALUE-carrying bucket — the STAYS law's storage (scope Phase 2).
     *
     * <p>Packed {@link BlockPos} → the exact player-authored placement dy, as a raw double. The
     * eight boolean buckets above say WHICH lane; this map says HOW FAR — the fact whose absence
     * Phase I §A proved is the root of every "my block moved" report. {@code NaN} is the absent
     * sentinel (the map's default return value); non-finite values are rejected at the door, and
     * {@code -0.0} is normalised to {@code +0.0} on write so raw-bit identity survives NBT.
     *
     * <p>DELIBERATELY INERT in Phase 2: no reader consults it (the {@code getYOffset}
     * short-circuit is Phase 3) and mutation does NOT network-sync (delta sync is Phase 5 — note
     * the asymmetry with the boolean buckets, whose mutators sync whole-bucket; the dy map must
     * get deltas, not inherit that shape). Lazy: {@code null} until the first write, so chunks
     * with no authored heights pay nothing.
     */
    private Long2DoubleOpenHashMap placementDy;

    /** NBT keys: two parallel long arrays. Positions and raw dy bits, index-aligned. */
    private static final String PLACEMENT_DY_POS_KEY = "placement_dy_pos";
    private static final String PLACEMENT_DY_BITS_KEY = "placement_dy_bits";

    public SlabAnchorStore(LevelChunk owner) {
        this.owner = owner;
    }

    /** Stored dy at the packed position, or {@code NaN} when absent. Zero-copy, zero-alloc. */
    public double getPlacementDy(long packedPos) {
        Long2DoubleOpenHashMap map = placementDy;
        return map == null ? Double.NaN : map.get(packedPos);
    }

    public boolean hasPlacementDy(long packedPos) {
        Long2DoubleOpenHashMap map = placementDy;
        return map != null && map.containsKey(packedPos);
    }

    public int placementDyCount() {
        Long2DoubleOpenHashMap map = placementDy;
        return map == null ? 0 : map.size();
    }

    /**
     * Stores the authored dy. Returns true when the stored raw bits changed. Rejects non-finite
     * ({@code NaN} is the absent sentinel and must never be storable); normalises {@code -0.0}
     * to {@code +0.0} so the raw-bits identity contract survives every codec on the route.
     * Marks the chunk unsaved; does NOT sync (Phase 5).
     */
    public boolean putPlacementDy(long packedPos, double dy) {
        if (!Double.isFinite(dy)) {
            throw new IllegalArgumentException("placement dy must be finite, got " + dy);
        }
        double normalized = dy == 0.0d ? 0.0d : dy; // -0.0 == 0.0 is true: collapses -0.0 to +0.0
        Long2DoubleOpenHashMap map = placementDy;
        if (map == null) {
            map = new Long2DoubleOpenHashMap();
            map.defaultReturnValue(Double.NaN);
            placementDy = map;
        } else if (map.containsKey(packedPos)
                && Double.doubleToRawLongBits(map.get(packedPos)) == Double.doubleToRawLongBits(normalized)) {
            return false;
        }
        map.put(packedPos, normalized);
        markUnsaved();
        return true;
    }

    /** Clears the authored dy at the position. Returns true when an entry was removed. */
    public boolean removePlacementDy(long packedPos) {
        Long2DoubleOpenHashMap map = placementDy;
        if (map == null || !map.containsKey(packedPos)) {
            return false;
        }
        map.remove(packedPos);
        if (map.isEmpty()) {
            placementDy = null;
        }
        markUnsaved();
        return true;
    }

    public boolean add(SlabAnchorMarker marker, BlockPos pos) {
        return add(marker, pos.asLong());
    }

    public boolean add(SlabAnchorMarker marker, long packedPos) {
        LongOpenHashSet set = copyBucket(marker);
        boolean changed = set.add(packedPos);
        if (changed) {
            putOrRemove(marker, set);
            markUnsaved();
            SlabAnchorNetwork.syncBucket(owner, marker, set);
        }
        return changed;
    }

    public boolean remove(SlabAnchorMarker marker, BlockPos pos) {
        return remove(marker, pos.asLong());
    }

    public boolean remove(SlabAnchorMarker marker, long packedPos) {
        LongOpenHashSet existing = buckets.get(marker);
        if (existing == null || existing.isEmpty()) {
            return false;
        }

        LongOpenHashSet set = new LongOpenHashSet(existing);
        boolean changed = set.remove(packedPos);
        if (changed) {
            putOrRemove(marker, set);
            markUnsaved();
            SlabAnchorNetwork.syncBucket(owner, marker, set);
        }
        return changed;
    }

    public void replace(SlabAnchorMarker marker, LongOpenHashSet positions) {
        LongOpenHashSet set = positions == null ? new LongOpenHashSet() : new LongOpenHashSet(positions);
        putOrRemove(marker, set);
        markUnsaved();
        SlabAnchorNetwork.syncBucket(owner, marker, set);
    }

    public void clear(SlabAnchorMarker marker) {
        if (buckets.remove(marker) != null) {
            markUnsaved();
            SlabAnchorNetwork.syncBucket(owner, marker, new LongOpenHashSet());
        }
    }

    public boolean contains(SlabAnchorMarker marker, BlockPos pos) {
        return contains(marker, pos.asLong());
    }

    public boolean contains(SlabAnchorMarker marker, long packedPos) {
        LongOpenHashSet set = buckets.get(marker);
        return set != null && set.contains(packedPos);
    }

    public LongOpenHashSet copy(SlabAnchorMarker marker) {
        return copyBucket(marker);
    }

    public boolean isEmpty(SlabAnchorMarker marker) {
        LongOpenHashSet set = buckets.get(marker);
        return set == null || set.isEmpty();
    }

    public boolean isEmpty() {
        for (LongOpenHashSet set : buckets.values()) {
            if (set != null && !set.isEmpty()) {
                return false;
            }
        }
        return placementDy == null || placementDy.isEmpty();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (SlabAnchorMarker marker : SlabAnchorMarker.values()) {
            LongOpenHashSet set = buckets.get(marker);
            if (set != null && !set.isEmpty()) {
                tag.putLongArray(marker.nbtKey(), set.toLongArray());
            }
        }
        Long2DoubleOpenHashMap dyMap = placementDy;
        if (dyMap != null && !dyMap.isEmpty()) {
            long[] positions = new long[dyMap.size()];
            long[] bits = new long[dyMap.size()];
            int i = 0;
            for (Long2DoubleMap.Entry e : dyMap.long2DoubleEntrySet()) {
                positions[i] = e.getLongKey();
                bits[i] = Double.doubleToRawLongBits(e.getDoubleValue());
                i++;
            }
            tag.putLongArray(PLACEMENT_DY_POS_KEY, positions);
            tag.putLongArray(PLACEMENT_DY_BITS_KEY, bits);
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
        if (tag.contains(PLACEMENT_DY_POS_KEY) && tag.contains(PLACEMENT_DY_BITS_KEY)) {
            long[] positions = tag.getLongArray(PLACEMENT_DY_POS_KEY);
            long[] bits = tag.getLongArray(PLACEMENT_DY_BITS_KEY);
            if (positions.length == bits.length && positions.length > 0) {
                Long2DoubleOpenHashMap map = new Long2DoubleOpenHashMap(positions.length);
                map.defaultReturnValue(Double.NaN);
                for (int i = 0; i < positions.length; i++) {
                    map.put(positions[i], Double.longBitsToDouble(bits[i]));
                }
                placementDy = map;
            }
            // Length mismatch: the pair is corrupt — drop it WHOLE. A half-applied dy store
            // (positions matched to the wrong bits) would author wrong heights silently, which
            // is strictly worse than authored blocks reverting to geometric derivation.
        }
    }

    private LongOpenHashSet copyBucket(SlabAnchorMarker marker) {
        LongOpenHashSet existing = buckets.get(marker);
        return existing == null ? new LongOpenHashSet() : new LongOpenHashSet(existing);
    }

    private void putOrRemove(SlabAnchorMarker marker, LongOpenHashSet set) {
        if (set.isEmpty()) {
            buckets.remove(marker);
        } else {
            buckets.put(marker, set);
        }
    }

    private void markUnsaved() {
        owner.setUnsaved(true);
    }
}
