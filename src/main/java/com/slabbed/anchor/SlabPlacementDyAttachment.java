package com.slabbed.anchor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.slabbed.Slabbed;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * THE PLACEMENT-HEIGHT STORE — the number an anchor was never able to carry.
 *
 * <p>{@code LAW.md} lane G, proven live by {@code NeighborUpdateInvarianceTest}: a cell can hold a
 * {@link SlabAnchorAttachment#ANCHOR_TYPE} anchor, keep it across every neighbour edit, and STILL
 * change height — because the anchor is a {@code LongOpenHashSet} of positions. It records THAT a
 * cell is lowered, never HOW FAR, so the magnitude is derived afresh on every read from a support
 * that the neighbour edit may have just destroyed. Presence protection cannot protect a value.
 * This attachment stores the value.
 *
 * <p>A fact may be written after vanilla completes a block-item placement. That transaction uses
 * the immutable placement aim, publishes every linked cell together, and records flat zero just as
 * explicitly as a lowered value. Legacy anchor callers retain their narrower behavior outside a
 * captured block-item transaction.
 *
 * <p>OLD WORLDS ARE THE ABSENT CASE, BY CONSTRUCTION. A world saved before this attachment existed
 * simply has no such attachment, so {@link #storedDy} answers {@link Double#NaN} and every caller
 * takes the identical path it took before this class existed. Absence must remain indistinguishable
 * from the previous behaviour — that is the migration-safety contract, and
 * {@code PlacementDyStoreTest} pins it.
 *
 * <p>VALUES ARE QUANTISED to sixteenths of a block (the vanilla pixel grid) and held that way in
 * memory, on disk and on the wire, so a height read in-session is bit-identical to the same height
 * read after a world reload. {@code DY_SPEC.md} CS-CAP bounds the real alphabet to
 * {@code {-1.0, -0.5, 0.0}}; a height that does not land exactly on the sixteenth grid, or falls
 * outside a signed byte of sixteenths, is simply not recorded (the cell keeps its live behaviour)
 * rather than being silently rounded into a different height.
 *
 * <p>SYNC CAPACITY IS ENFORCED, NOT ASSUMED (issue #38, {@code 5817d264}). Every write asks
 * {@link ChunkPositionDyMapPacketCodec#encodedByteLength} what the chunk will cost and refuses to
 * grow past {@link #SYNC_SAFE_ENCODED_BYTES}, so a dense chunk degrades to "no new facts" instead
 * of throwing out of {@code setAttached}. {@code PlacementDyAttachmentCapacityTest} characterises
 * where that boundary sits.
 */
public final class SlabPlacementDyAttachment {

    private SlabPlacementDyAttachment() {
    }

    /** Sixteenths of a block: the vanilla pixel grid, and a power of two so the maths is exact. */
    private static final double QUANTUM_DENOMINATOR = 16.0;

    /** Sentinel for "this height cannot be represented exactly on the stored grid". */
    private static final int UNREPRESENTABLE = Integer.MIN_VALUE;

    /**
     * Largest number of bytes this attachment may encode to.
     *
     * <p>Fabric rejects a synchronized attachment whose Netty backing array exceeds 32,502 bytes,
     * and Netty rounds the backing array up to a power of two, so 16,384 is the largest capacity
     * that clears the ceiling and a write of 16,385 bytes is already over. Fabric prefixes one
     * boolean of its own, leaving 16,383 for the codec — this is exactly the arithmetic that made
     * 2,047 raw longs fit and 2,048 fail in issue #38, restated for this attachment.
     */
    static final int SYNC_SAFE_ENCODED_BYTES = 16_383;

    /**
     * Client-side fallback for placement-height queries from chunk render paths that receive a
     * non-{@link World} {@link BlockView} (e.g. {@code ChunkRendererRegion}). Same contract as
     * {@link SlabAnchorAttachment#clientAnchorLookup}: set by the client entrypoint, always null on
     * a dedicated server, answers {@link Double#NaN} when there is no fact. Without it the chunk
     * mesh would read the live height while outline and raycast read the stored one, and the three
     * would disagree — the exact split this project treats as a first-class defect.
     */
    public static ToDoubleFunction<BlockPos> clientPlacementDyLookup = null;

    /** Client-only transient prediction lookup; authoritative synchronized facts always win. */
    public static ToDoubleFunction<BlockPos> clientPredictedPlacementDyLookup = null;

    /** Client-only bridge that publishes the immutable transaction result before the first mesh. */
    public static Consumer<Map<BlockPos, Long>> clientPredictionPublisher = null;

    /** Client-only bridge used when the captured placement fails after publishing a prediction. */
    public static Consumer<Iterable<BlockPos>> clientPredictionClearer = null;

    /** One WARN per session when a chunk saturates, not one per click there. */
    private static volatile boolean budgetReported = false;

    private static final ThreadLocal<Integer> BLOCK_ITEM_TRANSACTION_DEPTH =
            ThreadLocal.withInitial(() -> 0);
    private static final AtomicLong LEGACY_RECORD_PUBLICATIONS_DURING_TRANSACTION = new AtomicLong();
    private static final AtomicLong LEGACY_FLAT_PUBLICATIONS_DURING_TRANSACTION = new AtomicLong();

    private static final Identifier PLACEMENT_DY_ID = Identifier.of(Slabbed.MOD_ID, "placement_dy");

    /**
     * Disk form: a {@code LongArrayTag} of packed positions beside an {@code IntArrayTag} of the
     * matching quantised heights, both in ascending position order so a save is stable.
     */
    private static final Codec<Long2ByteOpenHashMap> MAP_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.LONG_STREAM.fieldOf("positions")
                            .forGetter(map -> LongStream.of(sortedPositions(map))),
                    Codec.INT_STREAM.fieldOf("dy_sixteenths")
                            .forGetter(map -> IntStream.of(quantisedHeightsInPositionOrder(map)))
            ).apply(instance, SlabPlacementDyAttachment::fromStreams));

    private static final PacketCodec<RegistryByteBuf, Long2ByteOpenHashMap> PACKET_CODEC =
            ChunkPositionDyMapPacketCodec.INSTANCE;

    /**
     * Package-private proof seam for the capacity regression test, mirroring
     * {@code SlabAnchorAttachment.packetCodecForTesting} so the test cannot exercise a duplicate
     * approximation of the production sync path.
     */
    static PacketCodec<RegistryByteBuf, Long2ByteOpenHashMap> packetCodecForTesting() {
        return PACKET_CODEC;
    }

    /** Package-private proof seam: the exact size prediction the write guard consults. */
    static int encodedByteLengthForTesting(Long2ByteOpenHashMap facts) {
        return ChunkPositionDyMapPacketCodec.encodedByteLength(facts);
    }

    public static final AttachmentType<Long2ByteOpenHashMap> PLACEMENT_DY_TYPE =
            AttachmentRegistry.<Long2ByteOpenHashMap>create(PLACEMENT_DY_ID, builder -> builder
                    .persistent(MAP_CODEC)
                    .syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())
            );

    /** Touches the class so the static field registers with Fabric before any chunk loads. */
    public static void register() {
        if (PLACEMENT_DY_TYPE == null) {
            throw new IllegalStateException("SlabPlacementDyAttachment failed to register");
        }
    }

    // ── server-side mutation ──────────────────────────────────────────

    public static void beginBlockItemTransaction() {
        BLOCK_ITEM_TRANSACTION_DEPTH.set(BLOCK_ITEM_TRANSACTION_DEPTH.get() + 1);
    }

    public static void endBlockItemTransaction() {
        int depth = BLOCK_ITEM_TRANSACTION_DEPTH.get();
        if (depth <= 1) {
            BLOCK_ITEM_TRANSACTION_DEPTH.remove();
        } else {
            BLOCK_ITEM_TRANSACTION_DEPTH.set(depth - 1);
        }
    }

    public static boolean blockItemTransactionActive() {
        return BLOCK_ITEM_TRANSACTION_DEPTH.get() > 0;
    }

    public static long legacyRecordPublicationsDuringTransaction() {
        return LEGACY_RECORD_PUBLICATIONS_DURING_TRANSACTION.get();
    }

    public static long legacyFlatPublicationsDuringTransaction() {
        return LEGACY_FLAT_PUBLICATIONS_DURING_TRANSACTION.get();
    }

    public static void noteLegacyFlatPublication() {
        if (blockItemTransactionActive()) {
            LEGACY_FLAT_PUBLICATIONS_DURING_TRANSACTION.incrementAndGet();
        }
    }

    /**
     * Records {@code dy} as the placement height of {@code pos}. Server-side only. No-op when the
     * height is not exactly representable on the stored grid, or when the chunk's encoded size
     * would pass {@link #SYNC_SAFE_ENCODED_BYTES} — in both cases the cell simply has no fact and
     * behaves exactly as it did before this store existed.
     *
     * @return true if a fact was written
     */
    public static boolean record(World world, BlockPos pos, double dy) {
        if (world == null || world.isClient() || pos == null) {
            return false;
        }
        // Vanilla has not finished the captured placement yet. The transaction publishes the
        // final state and every linked cell together after this legacy hook returns.
        if (blockItemTransactionActive()) {
            return false;
        }
        int quantised = quantise(dy);
        if (quantised == UNREPRESENTABLE) {
            if (SlabAnchorAttachment.TRACE) {
                Slabbed.LOGGER.info("[PLACEMENT_DY] skip pos={} dy={} reason=not_on_sixteenth_grid",
                        pos.toShortString(), dy);
            }
            return false;
        }
        WorldChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        Long2ByteOpenHashMap existing = chunk.getAttached(PLACEMENT_DY_TYPE);
        if (existing != null && existing.containsKey(pos.asLong())
                && existing.get(pos.asLong()) == (byte) quantised) {
            return false;
        }
        Long2ByteOpenHashMap facts =
                existing == null ? new Long2ByteOpenHashMap() : new Long2ByteOpenHashMap(existing);
        facts.put(pos.asLong(), (byte) quantised);
        if (ChunkPositionDyMapPacketCodec.encodedByteLength(facts) > SYNC_SAFE_ENCODED_BYTES) {
            // Issue #38's lesson applied before the fact rather than after: refuse the write and
            // leave this cell on its pre-existing live behaviour instead of throwing out of
            // setAttached in a real world. Reported ONCE per session at WARN — a saturated chunk
            // would otherwise log on every click there, which is exactly the per-block logging the
            // pre-release hygiene gate exists to catch.
            if (!budgetReported) {
                budgetReported = true;
                Slabbed.LOGGER.warn(
                        "[PLACEMENT_DY] chunk {} reached the attachment sync budget at {} stored "
                                + "heights; further cells there keep their live height",
                        chunk.getPos(), facts.size() - 1);
            }
            if (SlabAnchorAttachment.TRACE) {
                Slabbed.LOGGER.info("[PLACEMENT_DY] skip pos={} reason=sync_budget chunk={}",
                        pos.toShortString(), chunk.getPos());
            }
            return false;
        }
        if (blockItemTransactionActive()) {
            LEGACY_RECORD_PUBLICATIONS_DURING_TRANSACTION.incrementAndGet();
        }
        chunk.setAttached(PLACEMENT_DY_TYPE, facts);
        if (SlabAnchorAttachment.TRACE) {
            Slabbed.LOGGER.info("[PLACEMENT_DY] store pos={} dy={} chunk={} facts={}",
                    pos.toShortString(), dy, chunk.getPos(), facts.size());
        }
        return true;
    }

    /**
     * Publishes one placement operation as an all-or-nothing batch.
     *
     * <p>Every value is validated and every affected chunk is checked against the sync budget
     * before the first attachment is changed. This is the transaction boundary used by linked
     * placements: a door, bed, or other multi-cell object cannot acquire only part of its frozen
     * height state.
     *
     * @param rawBitsByPos exact {@link Double#doubleToRawLongBits(double)} values by final cell
     * @return true when the complete batch was already present or was published; false when no
     *         part of the batch was accepted
     */
    public static boolean writeBatch(World world, Map<BlockPos, Long> rawBitsByPos) {
        if (world == null || world.isClient() || rawBitsByPos == null || rawBitsByPos.isEmpty()) {
            return false;
        }

        LinkedHashMap<WorldChunk, Long2ByteOpenHashMap> originals = new LinkedHashMap<>();
        LinkedHashMap<WorldChunk, Long2ByteOpenHashMap> candidates = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, Long> entry : rawBitsByPos.entrySet()) {
            BlockPos pos = entry.getKey();
            Long rawBits = entry.getValue();
            if (pos == null || rawBits == null) {
                return false;
            }
            int quantised = quantise(Double.longBitsToDouble(rawBits));
            if (quantised == UNREPRESENTABLE) {
                return false;
            }
            WorldChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                return false;
            }
            if (!candidates.containsKey(chunk)) {
                Long2ByteOpenHashMap existing = chunk.getAttached(PLACEMENT_DY_TYPE);
                originals.put(chunk, existing == null ? null : new Long2ByteOpenHashMap(existing));
                candidates.put(chunk,
                        existing == null ? new Long2ByteOpenHashMap() : new Long2ByteOpenHashMap(existing));
            }
            candidates.get(chunk).put(pos.asLong(), (byte) quantised);
        }

        for (Map.Entry<WorldChunk, Long2ByteOpenHashMap> entry : candidates.entrySet()) {
            if (ChunkPositionDyMapPacketCodec.encodedByteLength(entry.getValue())
                    > SYNC_SAFE_ENCODED_BYTES) {
                reportBudgetOnce(entry.getKey(), entry.getValue().size());
                return false;
            }
        }

        LinkedHashMap<WorldChunk, Boolean> changed = new LinkedHashMap<>();
        try {
            for (Map.Entry<WorldChunk, Long2ByteOpenHashMap> entry : candidates.entrySet()) {
                WorldChunk chunk = entry.getKey();
                Long2ByteOpenHashMap original = originals.get(chunk);
                boolean differs = original == null || !original.equals(entry.getValue());
                changed.put(chunk, differs);
                if (differs) {
                    chunk.setAttached(PLACEMENT_DY_TYPE, entry.getValue());
                }
            }
        } catch (RuntimeException failure) {
            for (Map.Entry<WorldChunk, Boolean> entry : changed.entrySet()) {
                if (!entry.getValue()) {
                    continue;
                }
                Long2ByteOpenHashMap original = originals.get(entry.getKey());
                if (original == null || original.isEmpty()) {
                    entry.getKey().removeAttached(PLACEMENT_DY_TYPE);
                } else {
                    entry.getKey().setAttached(PLACEMENT_DY_TYPE, original);
                }
            }
            Slabbed.LOGGER.warn("[PLACEMENT_DY] placement batch rolled back", failure);
            return false;
        }
        return true;
    }

    private static void reportBudgetOnce(WorldChunk chunk, int attemptedSize) {
        if (!budgetReported) {
            budgetReported = true;
            Slabbed.LOGGER.warn(
                    "[PLACEMENT_DY] chunk {} reached the attachment sync budget at {} stored "
                            + "heights; the placement batch was not published",
                    chunk.getPos(), attemptedSize);
        }
    }

    /** Clears any stored placement height at {@code pos}. Server-side only. */
    public static void clear(World world, BlockPos pos) {
        if (world == null || world.isClient() || pos == null) {
            return;
        }
        WorldChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return;
        }
        Long2ByteOpenHashMap existing = chunk.getAttached(PLACEMENT_DY_TYPE);
        if (existing == null || !existing.containsKey(pos.asLong())) {
            return;
        }
        Long2ByteOpenHashMap facts = new Long2ByteOpenHashMap(existing);
        facts.remove(pos.asLong());
        if (facts.isEmpty()) {
            chunk.removeAttached(PLACEMENT_DY_TYPE);
        } else {
            chunk.setAttached(PLACEMENT_DY_TYPE, facts);
        }
        if (SlabAnchorAttachment.TRACE) {
            Slabbed.LOGGER.info("[PLACEMENT_DY] clear pos={}", pos.toShortString());
        }
    }

    // ── shared query ──────────────────────────────────────────────────

    /**
     * The authoritative placement height for {@code pos}, or the bounded client prediction while
     * its synchronized fact is still in flight; {@link Double#NaN} when neither exists.
     *
     * <p>Safe on both server and client (the authoritative client mirror arrives by attachment
     * sync and always outranks prediction). A
     * {@link BlockView} that is not a full {@link World} defers to
     * {@link #clientPlacementDyLookup}, so the chunk mesh path sees the same fact as outline and
     * raycast.
     */
    public static double storedDy(BlockView world, BlockPos pos) {
        if (pos == null) {
            return Double.NaN;
        }
        if (!(world instanceof World w)) {
            double synced = clientPlacementDyLookup == null
                    ? Double.NaN
                    : clientPlacementDyLookup.applyAsDouble(pos);
            return Double.isFinite(synced) || clientPredictedPlacementDyLookup == null
                    ? synced
                    : clientPredictedPlacementDyLookup.applyAsDouble(pos);
        }
        WorldChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return Double.NaN;
        }
        double synced = lookup(chunk, pos);
        if (Double.isFinite(synced) || !w.isClient() || clientPredictedPlacementDyLookup == null) {
            return synced;
        }
        return clientPredictedPlacementDyLookup.applyAsDouble(pos);
    }

    public static boolean publishClientPrediction(Map<BlockPos, Long> rawBitsByPos) {
        if (clientPredictionPublisher == null || rawBitsByPos == null || rawBitsByPos.isEmpty()) {
            return false;
        }
        clientPredictionPublisher.accept(Map.copyOf(rawBitsByPos));
        return true;
    }

    public static void clearClientPrediction(Iterable<BlockPos> positions) {
        if (clientPredictionClearer != null && positions != null) {
            clientPredictionClearer.accept(positions);
        }
    }

    /** Chunk-level lookup, shared by {@link #storedDy} and the client render-path bridge. */
    public static double lookup(WorldChunk chunk, BlockPos pos) {
        if (chunk == null || pos == null) {
            return Double.NaN;
        }
        Long2ByteOpenHashMap facts = chunk.getAttached(PLACEMENT_DY_TYPE);
        if (facts == null) {
            return Double.NaN;
        }
        long packed = pos.asLong();
        if (!facts.containsKey(packed)) {
            return Double.NaN;
        }
        return facts.get(packed) / QUANTUM_DENOMINATOR;
    }

    /** True if {@code pos} carries a stored placement height. */
    public static boolean hasStoredDy(BlockView world, BlockPos pos) {
        return !Double.isNaN(storedDy(world, pos));
    }

    /** Exact presence and value snapshot for same-cell upgrades. */
    public record PlacementDyFact(boolean present, long rawBits) {
        private static final PlacementDyFact ABSENT =
                new PlacementDyFact(false, Double.doubleToRawLongBits(Double.NaN));

        public static PlacementDyFact absent() {
            return ABSENT;
        }

        public static PlacementDyFact present(double value) {
            return new PlacementDyFact(true, Double.doubleToRawLongBits(value));
        }

        public double valueOrNaN() {
            return present ? Double.longBitsToDouble(rawBits) : Double.NaN;
        }
    }

    /** Direct backing read used to preserve the exact fact during same-cell upgrades. */
    public static PlacementDyFact rawFact(BlockView world, BlockPos pos) {
        if (pos == null) {
            return PlacementDyFact.absent();
        }
        double value = storedDy(world, pos);
        return Double.isNaN(value) ? PlacementDyFact.absent() : PlacementDyFact.present(value);
    }

    // ── quantisation ──────────────────────────────────────────────────

    /**
     * {@code dy} expressed in sixteenths of a block, or {@link #UNREPRESENTABLE} when the value
     * does not land exactly on that grid or does not fit a signed byte. The exactness test is a
     * round-trip comparison, not a tolerance, so a stored height always reads back bit-identical.
     */
    private static int quantise(double dy) {
        if (!Double.isFinite(dy)) {
            return UNREPRESENTABLE;
        }
        long sixteenths = Math.round(dy * QUANTUM_DENOMINATOR);
        if (sixteenths < Byte.MIN_VALUE || sixteenths > Byte.MAX_VALUE) {
            return UNREPRESENTABLE;
        }
        int quantised = (int) sixteenths;
        if (quantised / QUANTUM_DENOMINATOR != dy) {
            return UNREPRESENTABLE;
        }
        return quantised;
    }

    // ── persistence helpers ───────────────────────────────────────────

    private static long[] sortedPositions(Long2ByteOpenHashMap map) {
        LongArrayList positions = new LongArrayList(map.keySet());
        long[] array = positions.toLongArray();
        java.util.Arrays.sort(array);
        return array;
    }

    private static int[] quantisedHeightsInPositionOrder(Long2ByteOpenHashMap map) {
        long[] positions = sortedPositions(map);
        int[] heights = new int[positions.length];
        for (int index = 0; index < positions.length; index++) {
            heights[index] = map.get(positions[index]);
        }
        return heights;
    }

    private static Long2ByteOpenHashMap fromStreams(LongStream positions, IntStream heights) {
        long[] positionArray = positions.toArray();
        int[] heightArray = heights.toArray();
        int count = Math.min(positionArray.length, heightArray.length);
        Long2ByteOpenHashMap map = new Long2ByteOpenHashMap(count);
        for (int index = 0; index < count; index++) {
            int height = heightArray[index];
            if (height < Byte.MIN_VALUE || height > Byte.MAX_VALUE) {
                continue;
            }
            map.put(positionArray[index], (byte) height);
        }
        return map;
    }
}
