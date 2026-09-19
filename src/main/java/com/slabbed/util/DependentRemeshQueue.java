package com.slabbed.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.util.math.ChunkSectionPos;
import java.util.BitSet;
import java.util.function.Consumer;

/**
 * Coalesces Slabbed-dependent block regions by chunk section and exposes a fixed drain budget.
 * Repeated changes in one section must produce one rebuild request with the union of the affected
 * cache region, while a burst across sections must be spread across client ticks.
 */
public final class DependentRemeshQueue {
    /** Maximum number of dependent sections refreshed during one client tick. */
    public static final int MAX_SECTION_REBUILDS_PER_TICK = 12;

    /** Slabbed rebuilds are caused by a visible player-side change and must not enter a deferred lane. */
    public static final boolean REQUEST_IMPORTANT_REBUILD = true;

    private final int perTickBudget;
    private final Long2ObjectLinkedOpenHashMap<SectionRegion> pending = new Long2ObjectLinkedOpenHashMap<>();

    public DependentRemeshQueue(int perTickBudget) {
        if (perTickBudget <= 0) {
            throw new IllegalArgumentException("perTickBudget must be positive");
        }
        this.perTickBudget = perTickBudget;
    }

    public void enqueueBlockRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int normalizedMinX = Math.min(minX, maxX);
        int normalizedMinY = Math.min(minY, maxY);
        int normalizedMinZ = Math.min(minZ, maxZ);
        int normalizedMaxX = Math.max(minX, maxX);
        int normalizedMaxY = Math.max(minY, maxY);
        int normalizedMaxZ = Math.max(minZ, maxZ);

        int minSectionX = ChunkSectionPos.getSectionCoord(normalizedMinX);
        int minSectionY = ChunkSectionPos.getSectionCoord(normalizedMinY);
        int minSectionZ = ChunkSectionPos.getSectionCoord(normalizedMinZ);
        int maxSectionX = ChunkSectionPos.getSectionCoord(normalizedMaxX);
        int maxSectionY = ChunkSectionPos.getSectionCoord(normalizedMaxY);
        int maxSectionZ = ChunkSectionPos.getSectionCoord(normalizedMaxZ);

        for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
            for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                    BlockRegion clipped = clipToSection(
                            normalizedMinX, normalizedMinY, normalizedMinZ,
                            normalizedMaxX, normalizedMaxY, normalizedMaxZ,
                            sectionX, sectionY, sectionZ);
                    long key = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ);
                    SectionRegion existing = pending.get(key);
                    if (existing == null) pending.put(key, new SectionRegion(clipped));
                    else existing.add(clipped);
                }
            }
        }
    }

    public int drain(SectionConsumer consumer) {
        int processed = 0;
        while (processed < perTickBudget && !pending.isEmpty()) {
            long section = pending.firstLongKey();
            SectionRegion region = pending.remove(section);
            consumer.accept(
                    ChunkSectionPos.unpackX(section),
                    ChunkSectionPos.unpackY(section),
                    ChunkSectionPos.unpackZ(section),
                    region);
            processed++;
        }
        return processed;
    }

    public int pendingSectionCount() {
        return pending.size();
    }

    public void clear() {
        pending.clear();
    }

    private static BlockRegion clipToSection(
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            int sectionX, int sectionY, int sectionZ
    ) {
        int sectionMinX = ChunkSectionPos.getBlockCoord(sectionX);
        int sectionMinY = ChunkSectionPos.getBlockCoord(sectionY);
        int sectionMinZ = ChunkSectionPos.getBlockCoord(sectionZ);
        int sectionMaxX = sectionMinX + 15;
        int sectionMaxY = sectionMinY + 15;
        int sectionMaxZ = sectionMinZ + 15;
        return new BlockRegion(
                Math.max(minX, sectionMinX),
                Math.max(minY, sectionMinY),
                Math.max(minZ, sectionMinZ),
                Math.min(maxX, sectionMaxX),
                Math.min(maxY, sectionMaxY),
                Math.min(maxZ, sectionMaxZ));
    }

    public record BlockRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        BlockRegion union(BlockRegion other) {
            return new BlockRegion(
                    Math.min(minX, other.minX),
                    Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ),
                    Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY),
                    Math.max(maxZ, other.maxZ));
        }
    }

    /** Exact cache coverage within one section; sparse unions do not fill unrelated cells. */
    public static final class SectionRegion {
        private BlockRegion bounds;
        private final BitSet cells = new BitSet(4096);

        private SectionRegion(BlockRegion initial) {
            bounds = initial;
            add(initial);
        }

        private void add(BlockRegion region) {
            bounds = bounds.union(region);
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    int first = ((y & 15) << 8) | ((z & 15) << 4) | (region.minX() & 15);
                    cells.set(first, first + region.maxX() - region.minX() + 1);
                }
            }
        }

        public BlockRegion bounds() { return bounds; }

        public void forEachRegion(Consumer<BlockRegion> consumer) {
            int volume = (bounds.maxX() - bounds.minX() + 1) * (bounds.maxY() - bounds.minY() + 1)
                    * (bounds.maxZ() - bounds.minZ() + 1);
            if (cells.cardinality() == volume) {
                consumer.accept(bounds);
                return;
            }
            int baseX = bounds.minX() & ~15;
            int baseY = bounds.minY() & ~15;
            int baseZ = bounds.minZ() & ~15;
            for (int first = cells.nextSetBit(0); first >= 0;) {
                int end = Math.min(cells.nextClearBit(first), (first | 15) + 1);
                int y = baseY + (first >> 8);
                int z = baseZ + ((first >> 4) & 15);
                consumer.accept(new BlockRegion(baseX + (first & 15), y, z,
                        baseX + ((end - 1) & 15), y, z));
                first = cells.nextSetBit(end);
            }
        }
    }

    @FunctionalInterface
    public interface SectionConsumer {
        void accept(int sectionX, int sectionY, int sectionZ, SectionRegion region);
    }
}
