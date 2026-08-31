package com.slabbed.anchor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.slabbed.Slabbed;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Arrays;
import java.util.OptionalInt;
import java.util.function.LongToIntFunction;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Persistent, synchronized storage for a placed block's canonical half-step height fact.
 *
 * <p>The map is per chunk and maps {@link BlockPos#asLong()} to a signed count of half-block
 * steps. Mutation is copy-on-write so NeoForge observes every change and synchronizes the full
 * replacement. A missing attachment or map entry is an explicit absent fact and preserves legacy
 * behavior. This class does not decide when placement earns a fact and never invokes the resolver.
 */
public final class SlabPlacementHeightAttachment {
    private static final double HALF_STEP = 0.5d;

    /** Sentinel returned by the non-level client-render lookup when no fact is present. */
    public static final int ABSENT_HALF_STEPS = Integer.MIN_VALUE;

    private static volatile LongToIntFunction clientRenderHalfStepsLookup;

    /** Maximum number of physical block cells in one legal 1.21.1 chunk column. */
    public static final int MAX_FACTS_PER_CHUNK = 16 * 16 * DimensionType.Y_SIZE;

    private static final int STREAM_BYTES_PER_FACT = Long.BYTES + Byte.BYTES;

    private static final Codec<long[]> POSITIONS_CODEC = Codec.LONG_STREAM.flatXmap(
            SlabPlacementHeightAttachment::decodePositions,
            SlabPlacementHeightAttachment::encodePositions
    );

    private static final Codec<int[]> HEIGHT_HALF_STEPS_CODEC = Codec.INT_STREAM.flatXmap(
            SlabPlacementHeightAttachment::decodeHeightHalfSteps,
            SlabPlacementHeightAttachment::encodeHeightHalfSteps
    );

    private static final Codec<EncodedFacts> ENCODED_FACTS_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    POSITIONS_CODEC.fieldOf("positions").forGetter(EncodedFacts::positions),
                    HEIGHT_HALF_STEPS_CODEC.fieldOf("height_half_steps")
                            .forGetter(EncodedFacts::heightHalfSteps)
            ).apply(instance, EncodedFacts::new)
    );

    private static final Codec<Long2ByteOpenHashMap> MAP_CODEC = ENCODED_FACTS_CODEC.flatXmap(
            SlabPlacementHeightAttachment::decodeFacts,
            SlabPlacementHeightAttachment::encodeFacts
    );


    private SlabPlacementHeightAttachment() {
    }

    /**
     * No registration of its own on Forge.
     *
     * <p>The placement map lives inside the chunk capability that {@link SlabbedCapabilities}
     * registers, and its client copy is filled by {@link SlabbedAnchorNetwork}. The method is kept
     * so the call order in {@link SlabAnchorAttachment#register} reads the same on both loaders.
     */
    public static void register(IEventBus modEventBus) {
    }

    /**
     * Converts a finite block offset to its exact canonical half-step count.
     *
     * @return an empty result when {@code dy} is not exactly representable in a signed byte
     */
    public static OptionalInt exactHalfSteps(double dy) {
        if (!Double.isFinite(dy)) {
            return OptionalInt.empty();
        }
        double scaled = dy / HALF_STEP;
        if (scaled < Byte.MIN_VALUE || scaled > Byte.MAX_VALUE) {
            return OptionalInt.empty();
        }
        long rounded = Math.round(scaled);
        if (rounded < Byte.MIN_VALUE || rounded > Byte.MAX_VALUE
                || rounded * HALF_STEP != dy) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) rounded);
    }

    /** Converts a canonical signed half-step count back to a block offset. */
    public static double offsetForHalfSteps(byte halfSteps) {
        return halfSteps * HALF_STEP;
    }

    /**
     * The placement alphabet is lowering-only on this line: every legitimately captured fact
     * lies in [{@code PlacementDepthPolicy.MIN_TARGETABLE_DY}, 0.0]. A byte outside that
     * envelope cannot have been captured through the real placement transaction, so writes
     * decline it and reads treat it as absent (the legacy lane engages). A read never repairs,
     * rewrites, or re-derives the store — the corrupt byte is left exactly as found.
     */
    private static boolean isWithinPlacementEnvelope(int halfSteps) {
        double offset = halfSteps * HALF_STEP;
        return offset >= com.slabbed.util.PlacementDepthPolicy.MIN_TARGETABLE_DY && offset <= 0.0d;
    }

    /**
     * Reads the fact already present on a chunk without creating an attachment.
     *
     * @return empty when this is an old world, the chunk has no fact map, or the position is absent
     */
    public static OptionalInt storedHalfSteps(LevelChunk chunk, BlockPos pos) {
        if (chunk == null || pos == null) {
            return OptionalInt.empty();
        }
        Long2ByteOpenHashMap facts = factsOrNull(chunk);
        if (facts == null) {
            return OptionalInt.empty();
        }
        long packed = pos.asLong();
        return facts.containsKey(packed) ? OptionalInt.of(facts.get(packed)) : OptionalInt.empty();
    }

    /**
     * Reads the canonical offset from an already-loaded chunk or client render-view bridge.
     *
     * <p>Server reads are deliberately non-loading. Chunk generation and background shape work
     * must never synchronously request a server-owned chunk just to ask whether a placement fact
     * exists. A non-level view is used by the client chunk renderer and resolves through the
     * installed bridge into the synchronized client chunk attachment.
     *
     * @return the stored offset, or {@link Double#NaN} when the fact or loaded chunk is absent
     */
    public static double storedOffset(BlockGetter world, BlockPos pos) {
        if (world == null || pos == null) {
            return Double.NaN;
        }

        int halfSteps = ABSENT_HALF_STEPS;
        if (world instanceof ServerLevel serverLevel) {
            LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null) {
                halfSteps = storedHalfSteps(chunk, pos).orElse(ABSENT_HALF_STEPS);
            }
        } else if (world instanceof Level level) {
            LevelChunk chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            halfSteps = storedHalfSteps(chunk, pos).orElse(ABSENT_HALF_STEPS);
        } else {
            // A non-Level view is the chunk mesh. Only here may an unarrived client prediction
            // stand in for a fact: every Level view above reads the authoritative store, so
            // collision, targeting and interaction cannot see one.
            LongToIntFunction lookup = clientRenderHalfStepsLookup;
            if (lookup != null) {
                halfSteps = lookup.applyAsInt(pos.asLong());
            }
            if (halfSteps == ABSENT_HALF_STEPS) {
                halfSteps = ClientRenderDyPrediction.halfStepsOrAbsent(pos.asLong());
            }
        }

        return halfSteps < Byte.MIN_VALUE || halfSteps > Byte.MAX_VALUE
                        || !isWithinPlacementEnvelope(halfSteps)
                ? Double.NaN
                : offsetForHalfSteps((byte) halfSteps);
    }

    /**
     * Installs the client chunk-render lookup and returns the previous lookup for bounded tests.
     * The lookup must return {@link #ABSENT_HALF_STEPS} when no synchronized fact is available.
     */
    public static LongToIntFunction installClientRenderHalfStepsLookup(LongToIntFunction lookup) {
        LongToIntFunction previous = clientRenderHalfStepsLookup;
        clientRenderHalfStepsLookup = lookup;
        return previous;
    }

    /**
     * Stores a canonical half-step value on an already-loaded authoritative chunk.
     *
     * <p>This method is deliberately storage-only. Invalid values and positions are declined, and
     * an identical fact is a no-op. Replacing the map rather than mutating it in place lets
     * NeoForge perform the registered synchronization automatically.
     *
     * @return {@code true} only when the attachment changed
     */
    public static boolean putHalfSteps(LevelChunk chunk, BlockPos pos, int halfSteps) {
        if (!isValidWrite(chunk, pos, halfSteps)) {
            return false;
        }
        SlabbedChunkStore store = SlabbedCapabilities.chunkStore(chunk);
        if (store == null) {
            return false;
        }
        long packed = pos.asLong();
        Long2ByteOpenHashMap existing = store.placementDyOrNull();
        if (existing != null && !existing.containsKey(packed)
                && existing.size() >= MAX_FACTS_PER_CHUNK) {
            return false;
        }
        // The store writes in place and emits a constant-size delta. NeoForge needed a whole-map
        // replacement to notice the change; a capability does not, and a per-placement whole-map
        // resend is the wire cliff the delta exists to avoid.
        return store.putPlacementDy(packed, (byte) halfSteps);
    }

    /**
     * Removes one stored fact from an already-loaded authoritative chunk.
     *
     * <p>The attachment itself is removed when its last entry disappears, keeping empty storage
     * indistinguishable from an old world that never had this attachment.
     *
     * @return {@code true} only when a fact was removed
     */
    public static boolean remove(LevelChunk chunk, BlockPos pos) {
        if (!isValidPosition(chunk, pos)) {
            return false;
        }
        SlabbedChunkStore store = SlabbedCapabilities.chunkStore(chunk);
        return store != null && store.removePlacementDy(pos.asLong());
    }

    /**
     * The authoritative fact map for a chunk, or null when there is none.
     *
     * <p>A logical-client chunk carries an empty capability, because a Forge capability does not
     * synchronize. Client reads land in {@link SlabbedClientMirror} instead, which is what the
     * chunk-render lookup installed by the client sync consults.
     */
    @Nullable
    private static Long2ByteOpenHashMap factsOrNull(LevelChunk chunk) {
        if (chunk.getLevel() != null && chunk.getLevel().isClientSide()) {
            return SlabbedClientMirror.placementDyOrNull(chunk);
        }
        SlabbedChunkStore store = SlabbedCapabilities.chunkStore(chunk);
        return store == null ? null : store.placementDyOrNull();
    }

    /** Deterministic bounded codec used by native chunk persistence. */
    public static Codec<Long2ByteOpenHashMap> codec() {
        return MAP_CODEC;
    }

    /** Deterministic bounded wire encoder. Kept public: the schema suite asserts its bounds. */
    public static void encodeStream(FriendlyByteBuf buffer, Long2ByteOpenHashMap facts) {
        writeStream(buffer, facts);
    }

    /** Deterministic bounded wire decoder, rejecting oversized, truncated and duplicated input. */
    public static Long2ByteOpenHashMap decodeStream(FriendlyByteBuf buffer) {
        return readStream(buffer);
    }

    private static boolean isValidWrite(LevelChunk chunk, BlockPos pos, int halfSteps) {
        if (!isValidPosition(chunk, pos)
                || halfSteps < Byte.MIN_VALUE || halfSteps > Byte.MAX_VALUE
                || !isWithinPlacementEnvelope(halfSteps)) {
            return false;
        }
        return true;
    }

    private static boolean isValidPosition(LevelChunk chunk, BlockPos pos) {
        if (chunk == null || pos == null || chunk.getLevel().isClientSide()) {
            return false;
        }
        return chunk.getPos().x == pos.getX() >> 4
                && chunk.getPos().z == pos.getZ() >> 4
                && !chunk.isOutsideBuildHeight(pos);
    }

    private static DataResult<long[]> decodePositions(LongStream positions) {
        long[] decoded;
        try (LongStream bounded = positions.limit((long) MAX_FACTS_PER_CHUNK + 1L)) {
            decoded = bounded.toArray();
        }
        if (decoded.length > MAX_FACTS_PER_CHUNK) {
            return DataResult.error(() -> "placement_dy positions exceed one chunk's physical capacity");
        }
        return DataResult.success(decoded);
    }

    private static DataResult<int[]> decodeHeightHalfSteps(IntStream heightHalfSteps) {
        int[] decoded;
        try (IntStream bounded = heightHalfSteps.limit((long) MAX_FACTS_PER_CHUNK + 1L)) {
            decoded = bounded.toArray();
        }
        if (decoded.length > MAX_FACTS_PER_CHUNK) {
            return DataResult.error(() -> "placement_dy height_half_steps exceed one chunk's physical capacity");
        }
        return DataResult.success(decoded);
    }

    private static DataResult<LongStream> encodePositions(long[] positions) {
        if (positions.length > MAX_FACTS_PER_CHUNK) {
            return DataResult.error(() -> "placement_dy positions exceed one chunk's physical capacity");
        }
        return DataResult.success(LongStream.of(positions));
    }

    private static DataResult<IntStream> encodeHeightHalfSteps(int[] heightHalfSteps) {
        if (heightHalfSteps.length > MAX_FACTS_PER_CHUNK) {
            return DataResult.error(() -> "placement_dy height_half_steps exceed one chunk's physical capacity");
        }
        return DataResult.success(IntStream.of(heightHalfSteps));
    }

    private static DataResult<Long2ByteOpenHashMap> decodeFacts(EncodedFacts encoded) {
        long[] positions = encoded.positions();
        int[] heights = encoded.heightHalfSteps();
        if (positions.length != heights.length) {
            return DataResult.error(() -> "placement_dy positions and height_half_steps lengths differ");
        }
        if (positions.length > MAX_FACTS_PER_CHUNK) {
            return DataResult.error(() -> "placement_dy contains more facts than one chunk can hold");
        }
        Long2ByteOpenHashMap facts = new Long2ByteOpenHashMap(positions.length);
        for (int index = 0; index < positions.length; index++) {
            int halfSteps = heights[index];
            if (halfSteps < Byte.MIN_VALUE || halfSteps > Byte.MAX_VALUE) {
                return DataResult.error(() -> "placement_dy height_half_steps value is outside signed-byte range");
            }
            long packed = positions[index];
            if (facts.containsKey(packed)) {
                return DataResult.error(() -> "placement_dy positions contain a duplicate packed position");
            }
            facts.put(packed, (byte) halfSteps);
        }
        return DataResult.success(facts);
    }

    private static DataResult<EncodedFacts> encodeFacts(Long2ByteOpenHashMap facts) {
        if (facts.size() > MAX_FACTS_PER_CHUNK) {
            return DataResult.error(() -> "placement_dy contains more facts than one chunk can hold");
        }
        long[] positions = sortedPositions(facts);
        int[] heights = new int[positions.length];
        for (int index = 0; index < positions.length; index++) {
            heights[index] = facts.get(positions[index]);
        }
        return DataResult.success(new EncodedFacts(positions, heights));
    }

    private static void writeStream(
            FriendlyByteBuf buffer,
            Long2ByteOpenHashMap facts
    ) {
        if (facts.size() > MAX_FACTS_PER_CHUNK) {
            throw new IllegalArgumentException(
                    "placement_dy contains more facts than one chunk can hold");
        }
        long[] positions = sortedPositions(facts);
        buffer.writeVarInt(positions.length);
        for (long packed : positions) {
            buffer.writeLong(packed);
            buffer.writeByte(facts.get(packed));
        }
    }

    private static Long2ByteOpenHashMap readStream(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_FACTS_PER_CHUNK) {
            throw new IllegalArgumentException("Invalid placement_dy fact count " + count);
        }
        long requiredBytes = (long) count * STREAM_BYTES_PER_FACT;
        if (requiredBytes > buffer.readableBytes()) {
            throw new IllegalArgumentException("Truncated placement_dy attachment payload");
        }

        Long2ByteOpenHashMap facts = new Long2ByteOpenHashMap(count);
        for (int index = 0; index < count; index++) {
            long packed = buffer.readLong();
            if (facts.containsKey(packed)) {
                throw new IllegalArgumentException(
                        "placement_dy payload contains a duplicate packed position");
            }
            facts.put(packed, buffer.readByte());
        }
        return facts;
    }

    private static long[] sortedPositions(Long2ByteOpenHashMap facts) {
        long[] positions = new LongArrayList(facts.keySet()).toLongArray();
        Arrays.sort(positions);
        return positions;
    }

    private record EncodedFacts(long[] positions, int[] heightHalfSteps) {
    }
}
