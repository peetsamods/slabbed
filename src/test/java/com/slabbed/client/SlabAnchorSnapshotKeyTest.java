package com.slabbed.client;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class SlabAnchorSnapshotKeyTest {
    private static Object key(int x, int z, AttachmentType<LongOpenHashSet> type) throws Exception {
        Class<?> keyClass = Class.forName(
                "com.slabbed.client.SlabAnchorClientSync$AttachmentSnapshotKey", false,
                SlabAnchorSnapshotKeyTest.class.getClassLoader());
        Constructor<?> constructor = keyClass.getDeclaredConstructor(long.class, AttachmentType.class);
        constructor.setAccessible(true);
        return constructor.newInstance(ChunkPos.asLong(x, z), type);
    }

    @Test
    void nearbyChunkCoordinatesDoNotCollapseIntoLinearSearchBins() throws Exception {
        AttachmentType<LongOpenHashSet> type = AttachmentType.builder(() -> new LongOpenHashSet()).build();
        // A render-distance-sized square must not fold both chunk axes with XOR.
        // Equal-hash record keys are not Comparable, so HashMap tree bins search recursively.
        for (int center : new int[] {0, 4096, -4096}) {
            Map<Integer, Integer> populations = new HashMap<>();
            int count = 0;
            for (int x = center - 16; x <= center + 16; x++) {
                for (int z = center - 16; z <= center + 16; z++) {
                    populations.merge(key(x, z, type).hashCode(), 1, Integer::sum);
                    count++;
                }
            }
            int largest = populations.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
            System.out.println("chunk keys=" + count + " distinct hashes=" + populations.size()
                    + " largest equal-hash group=" + largest);
            assertTrue(largest <= 4, "nearby chunks must not share large equal-hash groups: " + largest);
        }
    }

    @Test
    void lookupReplacementAndRemovalKeepBothKeyComponents() throws Exception {
        AttachmentType<LongOpenHashSet> first = AttachmentType.builder(() -> new LongOpenHashSet()).build();
        AttachmentType<LongOpenHashSet> second = AttachmentType.builder(() -> new LongOpenHashSet()).build();
        Map<Object, LongOpenHashSet> snapshots = new HashMap<>();
        LongOpenHashSet original = new LongOpenHashSet(new long[] {11L});
        LongOpenHashSet replacement = new LongOpenHashSet(new long[] {12L});
        for (int coordinate : new int[] {0, -1, 1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            Object stored = key(coordinate, coordinate, first);
            Object equal = key(coordinate, coordinate, first);
            assertEquals(stored, equal);
            assertEquals(stored.hashCode(), equal.hashCode());
            assertNotEquals(stored, key(coordinate, coordinate, second));
            assertNotEquals(stored, key(coordinate, ~coordinate, first));
            snapshots.put(stored, original);
            assertSame(original, snapshots.get(equal));
            assertSame(original, snapshots.put(equal, replacement));
            assertSame(replacement, snapshots.remove(stored));
            snapshots.put(stored, null);
            assertTrue(snapshots.containsKey(equal));
            snapshots.clear();
            assertFalse(snapshots.containsKey(equal));
        }
    }

    @Test
    void eightAttachmentTypesKeepPollingLookupWorkBounded() throws Exception {
        int[] comparisons = {0};
        Map<CountedKey, Integer> snapshots = new HashMap<>();
        int[] baselineComparisons = {0};
        Map<CountedKey, Integer> baseline = new HashMap<>();
        java.util.List<Integer> baselineHashes = new java.util.ArrayList<>();
        java.util.List<Object> keys = new java.util.ArrayList<>();
        for (int typeIndex = 0; typeIndex < 8; typeIndex++) {
            AttachmentType<LongOpenHashSet> type = AttachmentType.builder(() -> new LongOpenHashSet()).build();
            for (int x = -16; x <= 16; x++) {
                for (int z = -16; z <= 16; z++) {
                    Object actual = key(x, z, type);
                    int originalHash = 31 * Long.hashCode(ChunkPos.asLong(x, z)) + type.hashCode();
                    snapshots.put(new CountedKey(actual, actual.hashCode(), comparisons), 1);
                    baseline.put(new CountedKey(actual, originalHash, baselineComparisons), 1);
                    keys.add(key(x, z, type));
                    baselineHashes.add(originalHash);
                }
            }
        }
        comparisons[0] = 0;
        baselineComparisons[0] = 0;
        for (int i = 0; i < keys.size(); i++) {
            Object key = keys.get(i);
            assertEquals(1, snapshots.get(new CountedKey(key, key.hashCode(), comparisons)));
            assertEquals(1, baseline.get(new CountedKey(key, baselineHashes.get(i), baselineComparisons)));
        }
        System.out.println("poll lookups=" + keys.size() + " equality comparisons=" + comparisons[0]
                + " original-hash comparisons=" + baselineComparisons[0]);
        assertTrue(comparisons[0] <= keys.size() * 2,
                "equal-hash collisions must not multiply polling lookup work");
    }

    private record CountedKey(Object actual, int hash, int[] comparisons) {
        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            comparisons[0]++;
            return other instanceof CountedKey key && actual.equals(key.actual);
        }
    }

    @Test
    void contentSnapshotsStillDetectInPlaceChanges() throws Exception {
        var copy = SlabAnchorClientSync.class.getDeclaredMethod("copyAttachmentSet", LongOpenHashSet.class);
        var equal = SlabAnchorClientSync.class.getDeclaredMethod(
                "attachmentSetsEqual", LongOpenHashSet.class, LongOpenHashSet.class);
        copy.setAccessible(true);
        equal.setAccessible(true);
        LongOpenHashSet live = new LongOpenHashSet(new long[] {1L});
        LongOpenHashSet snapshot = (LongOpenHashSet) copy.invoke(null, live);
        assertNotSame(live, snapshot);
        assertEquals(true, equal.invoke(null, snapshot, live));
        assertEquals(true, equal.invoke(null, snapshot, new LongOpenHashSet(live)));
        live.add(2L);
        assertEquals(false, equal.invoke(null, snapshot, live));
        live.remove(2L);
        live.remove(1L);
        assertEquals(false, equal.invoke(null, snapshot, live));
        assertEquals(true, equal.invoke(null, null, live));
        assertEquals(false, equal.invoke(null, snapshot, null));
    }


    @Test
    void nullTypeAndDistantHashCollisionsKeepDistinctSnapshots() throws Exception {
        Object first = key(0, 0, null);
        Object second = key(0, 65536, null);
        assertNotEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode(), "exercise actual equal-hash keys");
        Map<Object, Integer> snapshots = new HashMap<>();
        snapshots.put(first, 1);
        snapshots.put(second, 2);
        assertEquals(1, snapshots.get(key(0, 0, null)));
        assertEquals(2, snapshots.put(key(0, 65536, null), 3));
        assertEquals(1, snapshots.remove(key(0, 0, null)));
        assertEquals(3, snapshots.get(key(0, 65536, null)));
    }

}
