package com.slabbed.anchor;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.math.BlockPos;

/**
 * Exact, compact network representation for per-chunk sets of packed block positions.
 *
 * <p>The old codec sent every position as an eight-byte global long. Fabric measures the
 * Netty backing-array capacity for a synchronized attachment, so only 2,047 entries fit:
 * the 2,048th expands the buffer to 32,768 bytes, over Fabric's 32,502-byte ceiling.
 *
 * <p>This codec groups positions by 16-cubed chunk section. Each section carries a 64-bit
 * mask identifying its non-empty 64-position words, followed only by those words. A full
 * section therefore uses 520 occupancy bytes plus bounded coordinates instead of 32,768
 * position bytes. The complete built-in Overworld height fits below the attachment limit.
 *
 * <p>The compact stream starts with a negative marker. A non-negative first VarInt is
 * decoded as the previous count-plus-longs wire format, allowing a new client to read an
 * old server's attachment packet during a mixed-version transition.
 */
final class ChunkPositionSetPacketCodec {
    private static final int COMPACT_FORMAT_MARKER = -1;
    private static final int WORDS_PER_SECTION = 64;
    private static final int MAX_SECTION_GROUPS = 4_096;

    static final PacketCodec<RegistryByteBuf, LongOpenHashSet> INSTANCE =
            PacketCodec.of(ChunkPositionSetPacketCodec::encode, ChunkPositionSetPacketCodec::decode);

    private ChunkPositionSetPacketCodec() {
    }

    private static void encode(LongOpenHashSet positions, RegistryByteBuf buf) {
        TreeMap<SectionKey, long[]> sections = new TreeMap<>();
        for (long packed : positions) {
            int x = BlockPos.unpackLongX(packed);
            int y = BlockPos.unpackLongY(packed);
            int z = BlockPos.unpackLongZ(packed);
            SectionKey key = new SectionKey(x >> 4, y >> 4, z >> 4);
            long[] words = sections.computeIfAbsent(key, ignored -> new long[WORDS_PER_SECTION]);
            int bitIndex = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
            words[bitIndex >>> 6] |= 1L << (bitIndex & 63);
        }

        buf.writeVarInt(COMPACT_FORMAT_MARKER);
        buf.writeVarInt(sections.size());
        for (Map.Entry<SectionKey, long[]> entry : sections.entrySet()) {
            SectionKey key = entry.getKey();
            long[] words = entry.getValue();
            buf.writeVarInt(key.chunkX());
            buf.writeVarInt(key.sectionY());
            buf.writeVarInt(key.chunkZ());

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
        }
    }

    private static LongOpenHashSet decode(RegistryByteBuf buf) {
        int markerOrLegacyCount = buf.readVarInt();
        if (markerOrLegacyCount >= 0) {
            return decodeLegacy(buf, markerOrLegacyCount);
        }
        if (markerOrLegacyCount != COMPACT_FORMAT_MARKER) {
            throw new IllegalArgumentException(
                    "Unsupported chunk-position attachment packet format " + markerOrLegacyCount);
        }

        int sectionCount = buf.readVarInt();
        if (sectionCount < 0 || sectionCount > MAX_SECTION_GROUPS) {
            throw new IllegalArgumentException(
                    "Invalid chunk-position attachment section count " + sectionCount);
        }

        LongOpenHashSet positions = new LongOpenHashSet();
        for (int sectionIndex = 0; sectionIndex < sectionCount; sectionIndex++) {
            int chunkX = buf.readVarInt();
            int sectionY = buf.readVarInt();
            int chunkZ = buf.readVarInt();
            long nonEmptyWordMask = buf.readLong();

            for (int wordIndex = 0; wordIndex < WORDS_PER_SECTION; wordIndex++) {
                if ((nonEmptyWordMask & (1L << wordIndex)) == 0L) {
                    continue;
                }
                long word = buf.readLong();
                while (word != 0L) {
                    int bitInWord = Long.numberOfTrailingZeros(word);
                    int bitIndex = (wordIndex << 6) | bitInWord;
                    int localX = bitIndex & 15;
                    int localZ = (bitIndex >>> 4) & 15;
                    int localY = (bitIndex >>> 8) & 15;
                    positions.add(BlockPos.asLong(
                            (chunkX << 4) + localX,
                            (sectionY << 4) + localY,
                            (chunkZ << 4) + localZ));
                    word &= word - 1;
                }
            }
        }
        return positions;
    }

    private static LongOpenHashSet decodeLegacy(RegistryByteBuf buf, int count) {
        if (count > buf.readableBytes() / Long.BYTES) {
            throw new IllegalArgumentException(
                    "Legacy chunk-position attachment count " + count
                            + " exceeds remaining packet bytes");
        }
        LongOpenHashSet positions = new LongOpenHashSet(count);
        for (int index = 0; index < count; index++) {
            positions.add(buf.readLong());
        }
        return positions;
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
