package com.slabbed.anchor;

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

    public SlabAnchorStore(LevelChunk owner) {
        this.owner = owner;
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
        }
        return changed;
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
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (SlabAnchorMarker marker : SlabAnchorMarker.values()) {
            LongOpenHashSet set = buckets.get(marker);
            if (set != null && !set.isEmpty()) {
                tag.putLongArray(marker.nbtKey(), set.toLongArray());
            }
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
