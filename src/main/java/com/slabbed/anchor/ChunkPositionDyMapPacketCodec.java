package com.slabbed.anchor;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.math.BlockPos;

/**
 * Exact, compact network representation for a per-chunk map of packed block position to a
 * placement height quantised into sixteenths of a block.
 *
 * <p>WHY THIS IS NOT {@code ChunkPositionSetPacketCodec} WITH A VALUE TACKED ON. Issue #38
 * ({@code 5817d264}) exists because a per-chunk position SET overflowed Fabric's per-attachment
 * synchronization ceiling once it grew dense: Fabric measures the Netty backing-array capacity, and
 * Netty rounds that capacity up to a power of two, so the real budget is 16,384 written bytes even
 * though the documented ceiling is 32,502. A naive {@code long -> double} map is nine bytes per
 * entry — worse than the eight-byte raw-long set that already blew the budget at 2,048 entries.
 * This codec therefore stores the OCCUPANCY exactly the way the set codec does (a 64-bit
 * non-empty-word mask per 16-cubed section, then only the non-empty words) and stores the VALUES
 * as a per-section palette, because the height alphabet on this line is tiny
 * ({@code DY_SPEC.md} CS-CAP: {@code {-1.0, -0.5, 0.0}}).
 *
 * <p>A section whose facts all share one height costs a single extra palette byte for the whole
 * section, so the realistic dense shape — a whole chunk built on one lowered surface — encodes in
 * about the same space as the plain position set. A section with a mixed alphabet spends
 * {@code ceil(log2(paletteSize))} bits per occupied position, rounded up to 1, 2, 4 or 8, so even
 * a pathological 256-value section degrades to one byte per entry rather than throwing.
 *
 * <p>MEASURED on the built-in Overworld (a chunk column holds 98,304 positions), by
 * {@code PlacementDyAttachmentCapacityTest}: every one of those positions at ONE height encodes in
 * 12,766 bytes, well inside the budget; at two mixed heights, 64,136 of them fit in 16,340 bytes,
 * which is where the boundary sits. Two thirds of a completely full chunk column is far past
 * anything a real build reaches, and past the boundary the store declines new facts rather than
 * failing.
 *
 * <p>{@link #encodedByteLength} computes the exact size this codec will write WITHOUT allocating a
 * buffer, sharing the section grouping and palette construction with {@link #encode} so the two can
 * never disagree. {@code SlabPlacementDyAttachment} consults it before every write, so a chunk that
 * cannot fit the sync budget simply records no fact (and keeps this line's pre-existing live
 * behaviour for that cell) instead of throwing out of {@code setAttached} in a real world.
 *
 * <p>The stream starts with a negative marker so a future format can be told apart from this one.
 * There is no legacy format to read: this attachment is new, and a server without it sends nothing
 * at all.
 */
final class ChunkPositionDyMapPacketCodec {
    private static final int COMPACT_FORMAT_MARKER = -1;
    private static final int WORDS_PER_SECTION = 64;
    private static final int MAX_SECTION_GROUPS = 4_096;
    private static final int MAX_PALETTE_ENTRIES = 256;

    static final PacketCodec<RegistryByteBuf, Long2ByteOpenHashMap> INSTANCE =
            PacketCodec.of(ChunkPositionDyMapPacketCodec::encode, ChunkPositionDyMapPacketCodec::decode);

    private ChunkPositionDyMapPacketCodec() {
    }

    // ── encode ────────────────────────────────────────────────────────

    private static void encode(Long2ByteMap facts, RegistryByteBuf buf) {
        TreeMap<SectionKey, long[]> sections = groupSections(facts);
        buf.writeVarInt(COMPACT_FORMAT_MARKER);
        buf.writeVarInt(sections.size());
        for (Map.Entry<SectionKey, long[]> entry : sections.entrySet()) {
            SectionKey key = entry.getKey();
            long[] words = entry.getValue();
            buf.writeVarInt(key.chunkX());
            buf.writeVarInt(key.sectionY());
            buf.writeVarInt(key.chunkZ());

            long nonEmptyWordMask = nonEmptyWordMask(words);
            buf.writeLong(nonEmptyWordMask);
            for (int wordIndex = 0; wordIndex < WORDS_PER_SECTION; wordIndex++) {
                if ((nonEmptyWordMask & (1L << wordIndex)) != 0L) {
                    buf.writeLong(words[wordIndex]);
                }
            }

            byte[] palette = paletteOf(facts, key, words);
            // Written biased by one so all 256 possible quantised heights can share a section
            // without the size byte wrapping to zero.
            buf.writeByte(palette.length - 1);
            for (byte paletteValue : palette) {
                buf.writeByte(paletteValue);
            }
            if (palette.length <= 1) {
                // Uniform section: the occupancy bitmap already carries the height.
                continue;
            }
            int bitsPerIndex = bitsPerIndex(palette.length);
            BitWriter writer = new BitWriter(buf, bitsPerIndex);
            forEachPosition(key, words, packed -> writer.write(paletteIndex(palette, facts.get(packed))));
            writer.flush();
        }
    }

    // ── size, computed exactly and without a buffer ───────────────────

    /**
     * Exact number of bytes {@link #encode} will write for {@code facts}. Shares
     * {@link #groupSections} and {@link #paletteOf} with the encoder so the prediction and the
     * write can never drift apart.
     */
    static int encodedByteLength(Long2ByteMap facts) {
        TreeMap<SectionKey, long[]> sections = groupSections(facts);
        int length = varIntLength(COMPACT_FORMAT_MARKER) + varIntLength(sections.size());
        for (Map.Entry<SectionKey, long[]> entry : sections.entrySet()) {
            SectionKey key = entry.getKey();
            long[] words = entry.getValue();
            length += varIntLength(key.chunkX()) + varIntLength(key.sectionY()) + varIntLength(key.chunkZ());
            length += Long.BYTES;
            length += Long.BYTES * Long.bitCount(nonEmptyWordMask(words));
            byte[] palette = paletteOf(facts, key, words);
            length += 1 + palette.length;
            if (palette.length > 1) {
                int occupied = 0;
                for (long word : words) {
                    occupied += Long.bitCount(word);
                }
                length += (occupied * bitsPerIndex(palette.length) + 7) / 8;
            }
        }
        return length;
    }

    // ── decode ────────────────────────────────────────────────────────

    private static Long2ByteOpenHashMap decode(RegistryByteBuf buf) {
        int marker = buf.readVarInt();
        if (marker != COMPACT_FORMAT_MARKER) {
            throw new IllegalArgumentException(
                    "Unsupported chunk-position-dy attachment packet format " + marker);
        }
        int sectionCount = buf.readVarInt();
        if (sectionCount < 0 || sectionCount > MAX_SECTION_GROUPS) {
            throw new IllegalArgumentException(
                    "Invalid chunk-position-dy attachment section count " + sectionCount);
        }

        Long2ByteOpenHashMap facts = new Long2ByteOpenHashMap();
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

            int paletteSize = buf.readUnsignedByte() + 1;
            if (paletteSize < 1 || paletteSize > MAX_PALETTE_ENTRIES) {
                throw new IllegalArgumentException(
                        "Invalid chunk-position-dy attachment palette size " + paletteSize);
            }
            byte[] palette = new byte[paletteSize];
            for (int index = 0; index < paletteSize; index++) {
                palette[index] = buf.readByte();
            }

            SectionKey key = new SectionKey(chunkX, sectionY, chunkZ);
            if (paletteSize == 1) {
                byte uniform = palette[0];
                forEachPosition(key, words, packed -> facts.put(packed, uniform));
                continue;
            }
            BitReader reader = new BitReader(buf, bitsPerIndex(paletteSize));
            forEachPosition(key, words, packed -> {
                int index = reader.read();
                if (index >= palette.length) {
                    throw new IllegalArgumentException(
                            "chunk-position-dy attachment palette index " + index + " out of range");
                }
                facts.put(packed, palette[index]);
            });
        }
        return facts;
    }

    // ── shared structure ──────────────────────────────────────────────

    private static TreeMap<SectionKey, long[]> groupSections(Long2ByteMap facts) {
        TreeMap<SectionKey, long[]> sections = new TreeMap<>();
        for (long packed : facts.keySet()) {
            int x = BlockPos.unpackLongX(packed);
            int y = BlockPos.unpackLongY(packed);
            int z = BlockPos.unpackLongZ(packed);
            SectionKey key = new SectionKey(x >> 4, y >> 4, z >> 4);
            long[] words = sections.computeIfAbsent(key, ignored -> new long[WORDS_PER_SECTION]);
            int bitIndex = bitIndex(x, y, z);
            words[bitIndex >>> 6] |= 1L << (bitIndex & 63);
        }
        return sections;
    }

    private static long nonEmptyWordMask(long[] words) {
        long mask = 0L;
        for (int wordIndex = 0; wordIndex < WORDS_PER_SECTION; wordIndex++) {
            if (words[wordIndex] != 0L) {
                mask |= 1L << wordIndex;
            }
        }
        return mask;
    }

    /**
     * The distinct quantised heights present in one section, in the canonical position order the
     * encoder and decoder both walk. Never empty for a section that exists.
     */
    private static byte[] paletteOf(Long2ByteMap facts, SectionKey key, long[] words) {
        byte[] scratch = new byte[MAX_PALETTE_ENTRIES];
        int[] size = new int[1];
        forEachPosition(key, words, packed -> {
            byte value = facts.get(packed);
            for (int index = 0; index < size[0]; index++) {
                if (scratch[index] == value) {
                    return;
                }
            }
            if (size[0] < MAX_PALETTE_ENTRIES) {
                scratch[size[0]++] = value;
            }
        });
        byte[] palette = new byte[Math.max(1, size[0])];
        System.arraycopy(scratch, 0, palette, 0, size[0]);
        return palette;
    }

    private static int paletteIndex(byte[] palette, byte value) {
        for (int index = 0; index < palette.length; index++) {
            if (palette[index] == value) {
                return index;
            }
        }
        return 0;
    }

    /** 1, 2, 4 or 8 — the smallest power-of-two bit width that addresses the palette. */
    private static int bitsPerIndex(int paletteSize) {
        if (paletteSize <= 2) {
            return 1;
        }
        if (paletteSize <= 4) {
            return 2;
        }
        if (paletteSize <= 16) {
            return 4;
        }
        return 8;
    }

    /**
     * Walks a section's occupied positions in THE canonical order — word index ascending, then bit
     * index ascending inside the word. Encoder, decoder, palette construction and size computation
     * all use this one walk, so the packed value stream can never be misaligned against the
     * occupancy bitmap.
     */
    private static void forEachPosition(SectionKey key, long[] words, PositionSink sink) {
        for (int wordIndex = 0; wordIndex < WORDS_PER_SECTION; wordIndex++) {
            long word = words[wordIndex];
            while (word != 0L) {
                int bitInWord = Long.numberOfTrailingZeros(word);
                int bitIndex = (wordIndex << 6) | bitInWord;
                sink.accept(BlockPos.asLong(
                        (key.chunkX() << 4) + (bitIndex & 15),
                        (key.sectionY() << 4) + ((bitIndex >>> 8) & 15),
                        (key.chunkZ() << 4) + ((bitIndex >>> 4) & 15)));
                word &= word - 1;
            }
        }
    }

    private static int bitIndex(int x, int y, int z) {
        return ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
    }

    /** Vanilla's own VarInt width formula, inlined so the size prediction needs no buffer. */
    private static int varIntLength(int value) {
        for (int width = 1; width < 5; width++) {
            if ((value & (-1 << (width * 7))) == 0) {
                return width;
            }
        }
        return 5;
    }

    @FunctionalInterface
    private interface PositionSink {
        void accept(long packedPosition);
    }

    /** Least-significant-bit-first packer for 1/2/4/8-bit palette indices. */
    private static final class BitWriter {
        private final RegistryByteBuf buf;
        private final int bits;
        private int accumulator;
        private int filled;

        BitWriter(RegistryByteBuf buf, int bits) {
            this.buf = buf;
            this.bits = bits;
        }

        void write(int value) {
            accumulator |= (value & ((1 << bits) - 1)) << filled;
            filled += bits;
            if (filled == 8) {
                buf.writeByte(accumulator);
                accumulator = 0;
                filled = 0;
            }
        }

        void flush() {
            if (filled > 0) {
                buf.writeByte(accumulator);
                accumulator = 0;
                filled = 0;
            }
        }
    }

    /** The {@link BitWriter} mirror. */
    private static final class BitReader {
        private final RegistryByteBuf buf;
        private final int bits;
        private int accumulator;
        private int remaining;

        BitReader(RegistryByteBuf buf, int bits) {
            this.buf = buf;
            this.bits = bits;
        }

        int read() {
            if (remaining == 0) {
                accumulator = buf.readUnsignedByte();
                remaining = 8;
            }
            int value = accumulator & ((1 << bits) - 1);
            accumulator >>>= bits;
            remaining -= bits;
            return value;
        }
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
