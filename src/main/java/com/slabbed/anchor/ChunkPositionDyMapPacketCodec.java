package com.slabbed.anchor;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Exact, compact network representation for the per-chunk placement-dy map (GH #36's unfixed half).
 *
 * <p>The old codec sent every entry as an eight-byte global position plus an eight-byte double —
 * sixteen bytes per stored placement, synced whole-map. Fabric measures the complete readable
 * attachment bytes (including its one-byte presence prefix) against a 32,502-byte ceiling, so a
 * chunk died at roughly 2,030 stored placements: a 16x16 footprint with eight placement layers is
 * 2,048, a reachable build. {@code slabbed:frozen_flat} already survived exactly this failure via
 * {@link ChunkPositionSetPacketCodec}; this codec is that fix extended from a SET to a MAP.
 *
 * <p>Layout, mirroring the set codec's shell: positions are grouped by 16-cubed section, each
 * section carrying the same 64-bit word mask plus only its non-empty occupancy words. What a map
 * adds is a global PALETTE of distinct dy values keyed by {@link Double#doubleToRawLongBits} —
 * bit-exact on purpose, because LAW 1 promises every later read returns the stored value verbatim,
 * so the wire format must be bit-transparent (0.0 and -0.0 never merge; any NaN payload survives).
 * Each section then records either mode UNIFORM (every present cell shares one palette value — a
 * flattened terrace, the realistic dense case, costs ~520 bytes per section regardless of cell
 * count) or mode PER-CELL (one palette varint per present cell, ascending bit order).
 *
 * <p>Accepted bound, documented rather than hidden: a full chunk whose cells alternate between two
 * or more values still exceeds the ceiling. The set codec ships the analogous bound. Realistic
 * dense builds — thousands of placements at a handful of half-step depths — land in single-digit
 * kilobytes; the dense-uniform full-built-in-height case is pinned under the limit by
 * {@code PlacementDyAttachmentCapacityTest}.
 *
 * <p>The compact stream starts with a negative marker. A non-negative first VarInt is decoded as
 * the previous count-plus-(long,double) wire format, so a new client can read an old server's
 * attachment packet during a mixed-version transition — the same asymmetry the set codec shipped
 * (an old client reading a new server's marker as a negative count fails loudly, not silently).
 */
final class ChunkPositionDyMapPacketCodec {
    private static final int COMPACT_FORMAT_MARKER = -1;
    private static final int WORDS_PER_SECTION = 64;
    private static final int MAX_SECTION_GROUPS = 4_096;
    private static final int CELLS_PER_SECTION = 4_096;
    private static final byte MODE_UNIFORM = 0;
    private static final byte MODE_PER_CELL = 1;

    static final StreamCodec<RegistryFriendlyByteBuf, Long2DoubleOpenHashMap> INSTANCE =
            StreamCodec.of(ChunkPositionDyMapPacketCodec::encode, ChunkPositionDyMapPacketCodec::decode);

    private ChunkPositionDyMapPacketCodec() {
    }

    private static Long2DoubleOpenHashMap newDyMap(int expected) {
        Long2DoubleOpenHashMap map = new Long2DoubleOpenHashMap(Math.max(expected, 16));
        map.defaultReturnValue(Double.NaN);
        return map;
    }

    private static void encode(RegistryFriendlyByteBuf buf, Long2DoubleOpenHashMap map) {
        // Palette of distinct raw-bit values, insertion-ordered so encode is deterministic.
        Long2IntLinkedOpenHashMap paletteIndexByRawBits = new Long2IntLinkedOpenHashMap();
        paletteIndexByRawBits.defaultReturnValue(-1);
        LongArrayList paletteRawBits = new LongArrayList();

        TreeMap<SectionKey, int[]> sections = new TreeMap<>();
        for (var entry : map.long2DoubleEntrySet()) {
            long packed = entry.getLongKey();
            long rawBits = Double.doubleToRawLongBits(entry.getDoubleValue());
            int paletteIndex = paletteIndexByRawBits.get(rawBits);
            if (paletteIndex < 0) {
                paletteIndex = paletteRawBits.size();
                paletteIndexByRawBits.put(rawBits, paletteIndex);
                paletteRawBits.add(rawBits);
            }
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            SectionKey key = new SectionKey(x >> 4, y >> 4, z >> 4);
            int[] idxByBit = sections.computeIfAbsent(key, ignored -> {
                int[] fresh = new int[CELLS_PER_SECTION];
                java.util.Arrays.fill(fresh, -1);
                return fresh;
            });
            int bitIndex = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
            idxByBit[bitIndex] = paletteIndex;
        }

        buf.writeVarInt(COMPACT_FORMAT_MARKER);
        buf.writeVarInt(paletteRawBits.size());
        for (int i = 0; i < paletteRawBits.size(); i++) {
            buf.writeLong(paletteRawBits.getLong(i));
        }
        buf.writeVarInt(sections.size());
        for (Map.Entry<SectionKey, int[]> entry : sections.entrySet()) {
            SectionKey key = entry.getKey();
            int[] idxByBit = entry.getValue();
            buf.writeVarInt(key.chunkX());
            buf.writeVarInt(key.sectionY());
            buf.writeVarInt(key.chunkZ());

            long[] words = new long[WORDS_PER_SECTION];
            boolean uniform = true;
            int uniformIndex = -1;
            for (int bit = 0; bit < CELLS_PER_SECTION; bit++) {
                int idx = idxByBit[bit];
                if (idx < 0) {
                    continue;
                }
                words[bit >>> 6] |= 1L << (bit & 63);
                if (uniformIndex < 0) {
                    uniformIndex = idx;
                } else if (idx != uniformIndex) {
                    uniform = false;
                }
            }

            long nonEmptyWordMask = 0L;
            for (int wordIndex = 0; wordIndex < WORDS_PER_SECTION; wordIndex++) {
                if (words[wordIndex] != 0L) {
                    nonEmptyWordMask |= 1L << wordIndex;
                }
            }
            buf.writeLong(nonEmptyWordMask);
            for (int wordIndex = 0; wordIndex < WORDS_PER_SECTION; wordIndex++) {
                if ((nonEmptyWordMask & (1L << wordIndex)) != 0L) {
                    buf.writeLong(words[wordIndex]);
                }
            }

            if (uniform) {
                buf.writeByte(MODE_UNIFORM);
                buf.writeVarInt(uniformIndex);
            } else {
                buf.writeByte(MODE_PER_CELL);
                // Ascending bit order — identical iteration shape to the decode loop below.
                for (int wordIndex = 0; wordIndex < WORDS_PER_SECTION; wordIndex++) {
                    long word = words[wordIndex];
                    while (word != 0L) {
                        int bitInWord = Long.numberOfTrailingZeros(word);
                        buf.writeVarInt(idxByBit[(wordIndex << 6) | bitInWord]);
                        word &= word - 1;
                    }
                }
            }
        }
    }

    private static Long2DoubleOpenHashMap decode(RegistryFriendlyByteBuf buf) {
        int markerOrLegacyCount = buf.readVarInt();
        if (markerOrLegacyCount >= 0) {
            return decodeLegacy(buf, markerOrLegacyCount);
        }
        if (markerOrLegacyCount != COMPACT_FORMAT_MARKER) {
            throw new IllegalArgumentException(
                    "Unsupported placement-dy attachment packet format " + markerOrLegacyCount);
        }

        int paletteSize = buf.readVarInt();
        if (paletteSize < 0 || paletteSize > buf.readableBytes() / Long.BYTES) {
            throw new IllegalArgumentException(
                    "Invalid placement-dy attachment palette size " + paletteSize);
        }
        long[] paletteRawBits = new long[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            paletteRawBits[i] = buf.readLong();
        }

        int sectionCount = buf.readVarInt();
        if (sectionCount < 0 || sectionCount > MAX_SECTION_GROUPS) {
            throw new IllegalArgumentException(
                    "Invalid placement-dy attachment section count " + sectionCount);
        }

        Long2DoubleOpenHashMap map = newDyMap(sectionCount * 64);
        for (int sectionIndex = 0; sectionIndex < sectionCount; sectionIndex++) {
            int chunkX = buf.readVarInt();
            int sectionY = buf.readVarInt();
            int chunkZ = buf.readVarInt();
            long nonEmptyWordMask = buf.readLong();
            long[] words = new long[WORDS_PER_SECTION];
            for (int wordIndex = 0; wordIndex < WORDS_PER_SECTION; wordIndex++) {
                if ((nonEmptyWordMask & (1L << wordIndex)) != 0L) {
                    words[wordIndex] = buf.readLong();
                }
            }

            byte mode = buf.readByte();
            if (mode != MODE_UNIFORM && mode != MODE_PER_CELL) {
                throw new IllegalArgumentException(
                        "Invalid placement-dy attachment section mode " + mode);
            }
            int uniformIndex = -1;
            if (mode == MODE_UNIFORM) {
                uniformIndex = readPaletteIndex(buf, paletteSize);
            }

            for (int wordIndex = 0; wordIndex < WORDS_PER_SECTION; wordIndex++) {
                long word = words[wordIndex];
                while (word != 0L) {
                    int bitInWord = Long.numberOfTrailingZeros(word);
                    int bitIndex = (wordIndex << 6) | bitInWord;
                    int paletteIndex = mode == MODE_UNIFORM
                            ? uniformIndex
                            : readPaletteIndex(buf, paletteSize);
                    int localX = bitIndex & 15;
                    int localZ = (bitIndex >>> 4) & 15;
                    int localY = (bitIndex >>> 8) & 15;
                    map.put(
                            BlockPos.asLong(
                                    (chunkX << 4) + localX,
                                    (sectionY << 4) + localY,
                                    (chunkZ << 4) + localZ),
                            Double.longBitsToDouble(paletteRawBits[paletteIndex]));
                    word &= word - 1;
                }
            }
        }
        return map;
    }

    private static int readPaletteIndex(RegistryFriendlyByteBuf buf, int paletteSize) {
        int index = buf.readVarInt();
        if (index < 0 || index >= paletteSize) {
            throw new IllegalArgumentException(
                    "Placement-dy attachment palette index " + index
                            + " outside palette of size " + paletteSize);
        }
        return index;
    }

    private static Long2DoubleOpenHashMap decodeLegacy(RegistryFriendlyByteBuf buf, int count) {
        // Sixteen bytes per legacy entry: an eight-byte packed position plus an eight-byte double.
        if (count > buf.readableBytes() / (Long.BYTES * 2)) {
            throw new IllegalArgumentException(
                    "Legacy placement-dy attachment count " + count
                            + " exceeds remaining packet bytes");
        }
        Long2DoubleOpenHashMap map = newDyMap(count);
        for (int index = 0; index < count; index++) {
            long key = buf.readLong();
            double value = buf.readDouble();
            map.put(key, value);
        }
        return map;
    }

    private record SectionKey(int chunkX, int sectionY, int chunkZ)
            implements Comparable<SectionKey> {
        @Override
        public int compareTo(SectionKey other) {
            int byChunkX = Integer.compare(chunkX, other.chunkX);
            if (byChunkX != 0) {
                return byChunkX;
            }
            int byChunkZ = Integer.compare(chunkZ, other.chunkZ);
            if (byChunkZ != 0) {
                return byChunkZ;
            }
            return Integer.compare(sectionY, other.sectionY);
        }
    }
}
