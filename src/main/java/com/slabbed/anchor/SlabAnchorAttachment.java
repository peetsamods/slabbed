package com.slabbed.anchor;

import java.util.function.Predicate;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.RuntimeDiagnostics;
import com.slabbed.upgrade.WorldUpgradeRuntimePolicy;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Direction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.util.shape.VoxelShape;

/**
 * Persistent slab-anchor registry.
 *
 * <p>When an ordinary full block is placed directly on a bottom slab, or on an
 * ordinary full-block chain that is already lowered by a slab anchor, that placement is
 * recorded as an anchor on the chunk so the block keeps its lowered dy even if the
 * support below is later removed. Anchors are cleared when the anchored block itself is
 * broken/replaced.
 *
 * <p>Storage: per-{@link WorldChunk} {@link LongOpenHashSet} of packed {@link BlockPos}
 * longs. Persisted via Fabric data attachment, synced to all watching clients.
 *
 * <p>Scope: ordinary full-block vertical slab chains and accepted lowered slab
 * lane states only. No retroactive anchoring and no torch interaction.
 */
public final class SlabAnchorAttachment {
    private SlabAnchorAttachment() {
    }

    public static final boolean TRACE =
            Boolean.getBoolean("slabbed.anchor.trace");
    public static final String BETA4_COMPOUND_VISIBLE_RENDER_TRACE_PROPERTY =
            "slabbed.beta4CompoundVisibleRenderTrace";
    // Read once: this is queried per block on the render path (OffsetBlockStateModel).
    private static final boolean BETA4_COMPOUND_VISIBLE_RENDER_TRACE =
            Boolean.getBoolean(BETA4_COMPOUND_VISIBLE_RENDER_TRACE_PROPERTY);

    /**
     * Client-side fallback for anchor queries issued by chunk render paths that
     * receive a non-{@link World} {@link net.minecraft.world.BlockView}
     * (e.g. {@code ChunkRendererRegion}).  Set by the client entrypoint; always
     * null on a dedicated server.  No {@code MinecraftClient} reference needed
     * in common code.
     */
    public static Predicate<BlockPos> clientAnchorLookup = null;
    public static Predicate<BlockPos> clientFrozenFlatLookup = null;
    public static Predicate<BlockPos> clientLoweredSlabCarrierLookup = null;
    public static Predicate<BlockPos> clientCompoundFullBlockAnchorLookup = null;
    public static Predicate<BlockPos> clientCompoundVisibleSideLowerSlabLookup = null;
    public static Predicate<BlockPos> clientCompoundVisibleSideUpperSlabLookup = null;
    public static Predicate<BlockPos> clientCompoundVisibleSideDoubleSlabLookup = null;
    public static Predicate<BlockPos> clientCompoundVisibleOwnerTopSlabLookup = null;
    public static Predicate<BlockPos> clientModernPlacementLookup = null;
    public static Predicate<BlockPos> clientPostPolicyPredictionLookup = null;

    /**
     * Client-side fallback for placement-dy reads issued by chunk render paths that receive a
     * non-{@link World} {@link net.minecraft.world.BlockView} (e.g. {@code ChunkRendererRegion}).
     * Same seam family as the marker lookups above; installed by {@code SlabAnchorClientSync}.
     * Without it, mesh-thread reads under frozen-ON would answer absent and render every stored
     * height flat while outline/raycast (client-World reads) honour the store — a visual-triad
     * split. Null on a dedicated server.
     */
    public interface ClientPlacementDyFactLookup {
        PlacementDyFact lookup(BlockPos pos);
    }

    public static ClientPlacementDyFactLookup clientPlacementDyLookup = null;

    /**
     * Overlay-aware client read (Slice 2i). Answers the CLIENT PREDICTION OVERLAY's value for a cell
     * whose placement the client just predicted and whose authoritative fact has not been synced yet,
     * and {@code null} for every other cell so the read falls through to the backing store.
     * Installed by {@code PlacementDyPredictionClient}; null on a dedicated server.
     *
     * <p>Deliberately a SECOND field rather than a change to {@link #clientPlacementDyLookup}:
     * {@link #rawPlacementDyFact} must stay non-overlaying so debug surfaces, and the overlay's own
     * retirement condition, can still see the raw authoritative truth.
     */
    public static ClientPlacementDyFactLookup clientEffectivePlacementDyLookup = null;

    private static final Identifier ANCHOR_ID = Identifier.of(Slabbed.MOD_ID, "slab_anchors");
    private static final Identifier FROZEN_FLAT_ID = Identifier.of(Slabbed.MOD_ID, "frozen_flat");
    private static final Identifier LOWERED_SLAB_CARRIER_ID =
            Identifier.of(Slabbed.MOD_ID, "lowered_slab_carriers");
    private static final Identifier COMPOUND_FULL_BLOCK_ANCHOR_ID =
            Identifier.of(Slabbed.MOD_ID, "compound_full_block_anchors");
    private static final Identifier COMPOUND_VISIBLE_SIDE_LOWER_SLAB_ID =
            Identifier.of(Slabbed.MOD_ID, "compound_visible_side_lower_slabs");
    private static final Identifier COMPOUND_VISIBLE_SIDE_UPPER_SLAB_ID =
            Identifier.of(Slabbed.MOD_ID, "compound_visible_side_upper_slabs");
    private static final Identifier COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_ID =
            Identifier.of(Slabbed.MOD_ID, "compound_visible_side_double_slabs");
    private static final Identifier COMPOUND_VISIBLE_OWNER_TOP_SLAB_ID =
            Identifier.of(Slabbed.MOD_ID, "compound_visible_owner_top_slabs");
    private static final Identifier PLACEMENT_DY_ID = Identifier.of(Slabbed.MOD_ID, "placement_dy");
    private static final Identifier MODERN_PLACEMENT_ID = Identifier.of(Slabbed.MOD_ID, "modern_placements");

    /**
     * Codec for the anchor set.  Backed by {@code long[]} so the NBT representation is
     * a {@code LongArrayTag}, the most compact form available.
     */
    private static final Codec<LongOpenHashSet> SET_CODEC = Codec.LONG_STREAM.xmap(
            stream -> new LongOpenHashSet(stream.toArray()),
            set -> java.util.stream.LongStream.of(set.toLongArray())
    );

    /**
     * Compact packet codec for client sync. {@link AttachmentSyncPredicate#all()} is used at
     * registration so anchors travel with the chunk packet automatically.
     *
     * <p>The persistent {@link #SET_CODEC} intentionally remains the legacy long-array format
     * for world compatibility. Only the synchronized representation is compacted (GH #38
     * parity, mirrors 1.21.11 commit 5817d264).
     */
    private static final PacketCodec<RegistryByteBuf, LongOpenHashSet> PACKET_CODEC =
            ChunkPositionSetPacketCodec.INSTANCE;

    /**
     * Package-private proof seam for the attachment-capacity regression tests.
     *
     * <p>This returns the exact codec registered below, so the test cannot accidentally
     * exercise a duplicate approximation of the production sync path.
     */
    static PacketCodec<RegistryByteBuf, LongOpenHashSet> packetCodecForTesting() {
        return PACKET_CODEC;
    }

    /**
     * Package-private proof seam for the placement-dy capacity regression test, mirroring
     * {@link #packetCodecForTesting()}. Returns the exact codec registered on
     * {@link #PLACEMENT_DY_TYPE}, so the test cannot exercise a duplicate approximation of the
     * production sync path.
     */
    static PacketCodec<RegistryByteBuf, Long2ByteOpenHashMap> dyMapPacketCodecForTesting() {
        return DY_MAP_PACKET_CODEC;
    }

    public static final AttachmentType<LongOpenHashSet> ANCHOR_TYPE =
            AttachmentRegistry.<LongOpenHashSet>create(ANCHOR_ID, builder -> builder
                    .persistent(SET_CODEC)
                    .syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())
            );
    /**
     * FREEZE-ON-PLACE flat marker: a structural piece (full block / slab) placed at
     * dy=0 is recorded here so its flat height locks — support placed under or beside
     * it later can no longer pull it down. The "never autonomously moves" companion of
     * {@link #ANCHOR_TYPE} (which locks the lowered case). Read as dy=0 by
     * {@code getYOffsetInner}; cleared when the piece is broken.
     */
    public static final AttachmentType<LongOpenHashSet> FROZEN_FLAT_TYPE =
            AttachmentRegistry.<LongOpenHashSet>create(FROZEN_FLAT_ID, builder -> builder
                    .persistent(SET_CODEC)
                    .syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())
            );
    public static final AttachmentType<LongOpenHashSet> LOWERED_SLAB_CARRIER_TYPE =
            AttachmentRegistry.<LongOpenHashSet>create(LOWERED_SLAB_CARRIER_ID, builder -> builder
                    .persistent(SET_CODEC)
                    .syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())
            );
    /**
     * Beta4 sidecar attachment that records authored compound ordinary full-block
     * anchors at lane {@code dy=-1.0}. Additive to {@link #ANCHOR_TYPE}: a position
     * may be in both (compound block also has the ordinary anchor), and the sidecar
     * preserves authored depth across source slab removal so {@code getYOffsetInner}
     * can return {@code dy=-1.0} without re-deriving from the now-missing slab below.
     *
     * <p>Beta4-narrow: compound only, no slab lane grammar, no recursion below
     * {@code -1.0}. See {@code docs/beta4-compound-source-mode-design.md}.
     */
    public static final AttachmentType<LongOpenHashSet> COMPOUND_FULL_BLOCK_ANCHOR_TYPE =
            AttachmentRegistry.<LongOpenHashSet>create(COMPOUND_FULL_BLOCK_ANCHOR_ID, builder -> builder
                    .persistent(SET_CODEC)
                    .syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())
            );
    public static final AttachmentType<LongOpenHashSet> COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE =
            AttachmentRegistry.<LongOpenHashSet>create(COMPOUND_VISIBLE_SIDE_LOWER_SLAB_ID, builder -> builder
                    .persistent(SET_CODEC)
                    .syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())
            );
    public static final AttachmentType<LongOpenHashSet> COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE =
            AttachmentRegistry.<LongOpenHashSet>create(COMPOUND_VISIBLE_SIDE_UPPER_SLAB_ID, builder -> builder
                    .persistent(SET_CODEC)
                    .syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())
            );
    public static final AttachmentType<LongOpenHashSet> COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE =
            AttachmentRegistry.<LongOpenHashSet>create(COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_ID, builder -> builder
                    .persistent(SET_CODEC)
                    .syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())
            );
    public static final AttachmentType<LongOpenHashSet> COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE =
            AttachmentRegistry.<LongOpenHashSet>create(COMPOUND_VISIBLE_OWNER_TOP_SLAB_ID, builder -> builder
                    .persistent(SET_CODEC)
                    .syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())
            );
    public static final AttachmentType<LongOpenHashSet> MODERN_PLACEMENT_TYPE =
            AttachmentRegistry.<LongOpenHashSet>create(MODERN_PLACEMENT_ID, builder -> builder
                    .persistent(SET_CODEC)
                    .syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())
            );

    // ── FROZEN-DY value store (LAW.md restoration, Slice 2b) ──────────────────────────
    // The law: a block's height is decided ONCE at placement and STAYS. Unlike the presence flags
    // above, this records the actual height (a double) per position, so a read answers with the exact
    // value the player aimed at — never a value derived afresh from the current neighbours. Synced to
    // clients exactly like the flags, so the frozen height is what renders.

    private record DyEntry(long pos, double dy) {
    }

    /** Presence and raw bits are one indivisible C3 authority fact. */
    public record PlacementDyFact(boolean present, long rawBits) {
        /**
         * PERF: {@code absent()} is the answer for every cell in ordinary terrain, and the chunk
         * mesher asks it many times per non-air block per section compile. A fresh record per ask is
         * pure garbage on the mesh worker threads. The value is a constant, the record is immutable,
         * and its {@code equals} is value-based, so one shared instance is indistinguishable from a
         * fresh one at every call site.
         */
        private static final PlacementDyFact ABSENT = new PlacementDyFact(false, 0L);

        public static PlacementDyFact absent() {
            return ABSENT;
        }

        /**
         * The ONE door from a stored byte of sixteenths back to a height. Every reader of
         * {@link #PLACEMENT_DY_TYPE} — the direct chunk read and the client mesh bridge alike —
         * comes through here. Do not add a factory that takes a {@code double} height: a stored
         * byte passed to it widens silently (-8 sixteenths reads as -8.0 blocks), the compiler
         * says nothing, and the block is drawn eight blocks underground while every direct read
         * stays right. {@code storedByteReadsAsTheSameHeightThroughEveryDoor} pins this.
         */
        public static PlacementDyFact fromStoredSixteenths(byte sixteenths) {
            return new PlacementDyFact(true, Double.doubleToRawLongBits(dequantiseDy(sixteenths)));
        }

        public double valueOrNaN() {
            return present ? Double.longBitsToDouble(rawBits) : Double.NaN;
        }
    }

    /**
     * Sixteenths of a block: the vanilla pixel grid, and a power of two so the maths is exact.
     *
     * <p>Ported from the Fabric 1.21.11 line 2026-09-03 together with
     * {@link ChunkPositionDyMapPacketCodec}. Before it, this store held a raw {@code double} per
     * cell and synchronized sixteen bytes per stored height with no grouping and no write guard —
     * measurably worse than the eight-byte marker sets that already had to be compacted for issue
     * #38. {@code PlacementDyAttachmentCapacityTest} proved the consequence: 1,024 stored heights
     * in one chunk (four tiled sixteen-by-sixteen layers, an ordinary afternoon of building)
     * encoded to 32,768 bytes and threw straight out of {@code setAttached}.
     */
    private static final double QUANTUM_DENOMINATOR = 16.0;

    /** Sentinel for "this height cannot be represented exactly on the stored grid". */
    public static final int UNREPRESENTABLE = Integer.MIN_VALUE;

    /**
     * Largest number of bytes this attachment may encode to.
     *
     * <p>Fabric rejects a synchronized attachment whose Netty backing array exceeds 32,502 bytes,
     * and Netty rounds that array up to a power of two, so 16,384 is the largest capacity that
     * clears the ceiling and a write of 16,385 bytes is already over. Fabric prefixes one boolean
     * of its own, leaving 16,383 for the codec — the same arithmetic that made 2,047 raw longs fit
     * and 2,048 fail in issue #38, restated for this attachment.
     */
    static final int SYNC_SAFE_ENCODED_BYTES = 16_383;

    /** One WARN per session when a chunk saturates, not one per click there. */
    private static volatile boolean dyBudgetReported = false;

    /**
     * The exact sixteenth-grid value for {@code dy}, or {@link #UNREPRESENTABLE} when the height is
     * not finite or does not land exactly on the grid. An off-grid height is DECLINED, never
     * rounded: rounding would silently move a placed block, which is the one thing LAW.md forbids.
     */
    public static int quantiseDy(double dy) {
        if (!Double.isFinite(dy)) {
            return UNREPRESENTABLE;
        }
        double sixteenths = dy * QUANTUM_DENOMINATOR;
        int rounded = (int) Math.rint(sixteenths);
        if (Math.abs(sixteenths - rounded) > 1.0e-9d) {
            return UNREPRESENTABLE;
        }
        if (rounded < Byte.MIN_VALUE || rounded > Byte.MAX_VALUE) {
            return UNREPRESENTABLE;
        }
        return rounded;
    }

    /** The stored grid value back as a height. Exact: both sides are powers of two. */
    public static double dequantiseDy(byte sixteenths) {
        return sixteenths / QUANTUM_DENOMINATOR;
    }

    private static Long2ByteOpenHashMap newDyMap() {
        return new Long2ByteOpenHashMap();
    }

    /**
     * Disk form: packed positions beside their quantised heights, both in ascending position order
     * so a save is stable.
     */
    private static final Codec<Long2ByteOpenHashMap> DY_MAP_CODEC = RecordCodecBuilder.create(
            inst -> inst.group(
                    Codec.LONG_STREAM.fieldOf("positions")
                            .forGetter(map -> java.util.stream.LongStream.of(sortedDyPositions(map))),
                    Codec.INT_STREAM.fieldOf("dy_sixteenths")
                            .forGetter(map -> java.util.stream.IntStream.of(
                                    dyHeightsInPositionOrder(map)))
            ).apply(inst, SlabAnchorAttachment::dyMapFromStreams));

    private static long[] sortedDyPositions(Long2ByteOpenHashMap map) {
        long[] positions = map.keySet().toLongArray();
        java.util.Arrays.sort(positions);
        return positions;
    }

    private static int[] dyHeightsInPositionOrder(Long2ByteOpenHashMap map) {
        long[] positions = sortedDyPositions(map);
        int[] heights = new int[positions.length];
        for (int i = 0; i < positions.length; i++) {
            heights[i] = map.get(positions[i]);
        }
        return heights;
    }

    private static Long2ByteOpenHashMap dyMapFromStreams(
            java.util.stream.LongStream positions, java.util.stream.IntStream heights) {
        long[] p = positions.toArray();
        int[] h = heights.toArray();
        Long2ByteOpenHashMap map = newDyMap();
        int n = Math.min(p.length, h.length);
        for (int i = 0; i < n; i++) {
            map.put(p[i], (byte) h[i]);
        }
        return map;
    }

    /**
     * Wire form: {@link ChunkPositionDyMapPacketCodec}, which groups by 16-cubed section, stores
     * occupancy as a non-empty-word mask, and stores heights as a per-section palette. The height
     * alphabet a real build produces is tiny, so a section built on one lowered surface costs a
     * single palette byte for the whole section.
     */
    private static final PacketCodec<RegistryByteBuf, Long2ByteOpenHashMap> DY_MAP_PACKET_CODEC =
            ChunkPositionDyMapPacketCodec.INSTANCE;

    public static final AttachmentType<Long2ByteOpenHashMap> PLACEMENT_DY_TYPE =
            AttachmentRegistry.<Long2ByteOpenHashMap>create(PLACEMENT_DY_ID, builder -> builder
                    .persistent(DY_MAP_CODEC)
                    .syncWith(DY_MAP_PACKET_CODEC, AttachmentSyncPredicate.all())
            );

    /**
     * Whole-world override for the stored-height reader (LAW.md restoration): when true, EVERY cell
     * reads its stored placement height and a missing fact resolves stable-flat, regardless of
     * provenance. Shipped default false.
     *
     * <p>The shipped mode is per-cell provenance — see {@link #usesFrozenPlacementHeight}: a cell
     * placed by an item under this line's placement policy carries a modern marker and reads its
     * stored height; a cell without one (blocks from before this version, worldgen, structures,
     * {@code /setblock}) keeps the live read lanes, so upgrading a world moves nothing. There is no
     * retro-migration on this line: a legacy cell never acquires a stored value from its live geometry.
     *
     * <p>{@code -Dslabbed.frozenDy=true} remains the opt-in for whole-world frozen reads. Deliberately
     * MUTABLE: fixtures and law rows flip it in-process to run a single scenario under whole-world
     * frozen reads while the rest of the suite stays on the shipped configuration.
     */
    public static boolean FROZEN_DY_ENABLED =
            Boolean.parseBoolean(System.getProperty("slabbed.frozenDy", "false"));

    /**
     * Writes a server-authoritative C3 batch with at most one attachment publication per chunk.
     *
     * @param rawBitsByPos raw {@code Double.doubleToRawLongBits} height per cell
     * @return the number of cells whose stored fact actually changed
     */
    public static int writePlacementDyBatch(World world, Map<BlockPos, Long> rawBitsByPos) {
        if (world == null || world.isClient() || rawBitsByPos == null || rawBitsByPos.isEmpty()) {
            return 0;
        }
        LinkedHashMap<BlockPos, PlacementDyFact> facts = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, Long> entry : rawBitsByPos.entrySet()) {
            double value = Double.longBitsToDouble(entry.getValue());
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("non-finite placement dy batch value");
            }
            facts.put(entry.getKey().toImmutable(), new PlacementDyFact(true, entry.getValue()));
        }
        return writePlacementDyFactsInternal(world, facts);
    }

    /**
     * COPY-ON-WRITE: the attached map is never mutated in place (a live map handed out to a render
     * thread must stay stable), and each touched chunk publishes exactly ONCE, at the end. An
     * unchanged value short-circuits before the chunk is marked, so an idempotent rewrite publishes
     * nothing at all.
     */
    private static int writePlacementDyFactsInternal(World world, Map<BlockPos, PlacementDyFact> facts) {
        IdentityHashMap<WorldChunk, Long2ByteOpenHashMap> copies = new IdentityHashMap<>();
        IdentityHashMap<WorldChunk, Boolean> changedChunks = new IdentityHashMap<>();
        int writes = 0;
        for (Map.Entry<BlockPos, PlacementDyFact> entry : facts.entrySet()) {
            BlockPos pos = entry.getKey();
            PlacementDyFact desired = entry.getValue();
            if (pos == null || desired == null) {
                continue;
            }
            WorldChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                continue;
            }
            Long2ByteOpenHashMap map = copies.computeIfAbsent(chunk, ignored -> {
                Long2ByteOpenHashMap existing = chunk.getAttached(PLACEMENT_DY_TYPE);
                return existing == null ? newDyMap() : new Long2ByteOpenHashMap(existing);
            });
            long key = pos.asLong();
            PlacementDyFact current = map.containsKey(key)
                    ? PlacementDyFact.fromStoredSixteenths(map.get(key))
                    : PlacementDyFact.absent();
            if (current.equals(desired)) {
                continue;
            }
            if (desired.present()) {
                double value = Double.longBitsToDouble(desired.rawBits());
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("non-finite authoritative placement dy");
                }
                int quantised = quantiseDy(value);
                if (quantised == UNREPRESENTABLE) {
                    // DECLINED, never rounded: a rounded height is a moved block, and LAW.md
                    // forbids a placed block moving. The cell keeps whatever the live lanes
                    // answer for it, exactly as it would with no fact at all.
                    if (TRACE) {
                        Slabbed.LOGGER.info(
                                "[PLACEMENT_DY] declined pos={} dy={} reason=not_on_sixteenth_grid",
                                pos.toShortString(), value);
                    }
                    continue;
                }
                Long2ByteOpenHashMap candidate = new Long2ByteOpenHashMap(map);
                candidate.put(key, (byte) quantised);
                if (ChunkPositionDyMapPacketCodec.encodedByteLength(candidate)
                        > SYNC_SAFE_ENCODED_BYTES) {
                    // The chunk is saturated. Decline the new fact rather than let setAttached
                    // throw and take the whole chunk's synchronization down with it; the cell
                    // falls back to the live lanes, which is this line's pre-store behaviour.
                    if (!dyBudgetReported) {
                        dyBudgetReported = true;
                        Slabbed.LOGGER.warn(
                                "[PLACEMENT_DY] chunk {},{} is at its synchronization budget ({} "
                                        + "bytes); further placement heights there are not stored. "
                                        + "This message appears once per session.",
                                chunk.getPos().x, chunk.getPos().z, SYNC_SAFE_ENCODED_BYTES);
                    }
                    continue;
                }
                map.put(key, (byte) quantised);
            } else {
                map.remove(key);
            }
            changedChunks.put(chunk, Boolean.TRUE);
            writes++;
        }
        for (WorldChunk chunk : changedChunks.keySet()) {
            chunk.setAttached(PLACEMENT_DY_TYPE, copies.get(chunk));
        }
        return writes;
    }

    /**
     * The frozen placement height at {@code pos}, or {@link Double#NaN} if none is known.
     *
     * <p>On a CLIENT view the prediction overlay is authoritative ahead of the backing store: a cell
     * whose placement this client just predicted has no synced fact yet, and answering absent for it
     * is exactly the "renders flat, then snaps down" transient (Slice 2i). Every other cell, and
     * every server view, reads the backing store unchanged.
     */
    public static double storedPlacementDy(BlockView world, BlockPos pos) {
        if (pos == null) {
            return Double.NaN;
        }
        boolean clientView = !(world instanceof World w) || w.isClient();
        if (clientView) {
            ClientPlacementDyFactLookup overlay = clientEffectivePlacementDyLookup;
            if (overlay != null) {
                PlacementDyFact effective = overlay.lookup(pos);
                if (effective != null) {
                    return effective.valueOrNaN();
                }
            }
        }
        return rawPlacementDyFact(world, pos).valueOrNaN();
    }

    /** True only for a cell placed by an item under the placement policy (modern provenance). */
    public static boolean isModernPlacement(BlockView world, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (!(world instanceof World w)) {
            return clientModernPlacementLookup != null && clientModernPlacementLookup.test(pos);
        }
        WorldChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        LongOpenHashSet set = chunk == null ? null : chunk.getAttached(MODERN_PLACEMENT_TYPE);
        return set != null && set.contains(pos.asLong());
    }

    /**
     * Selects the frozen reader for a cell: every cell under the whole-world override; otherwise a
     * cell with modern provenance, or — on the client only — a cell the prediction overlay owns.
     */
    public static boolean usesFrozenPlacementHeight(BlockView world, BlockPos pos) {
        if (FROZEN_DY_ENABLED) {
            return true;
        }
        if (isModernPlacement(world, pos)) {
            return true;
        }
        // A server never consults the integrated client's prediction overlay. Only the client World
        // or its render-region bridge may select the modern path before provenance sync arrives.
        if (world instanceof World w && !w.isClient()) {
            return false;
        }
        return clientPostPolicyPredictionLookup != null && clientPostPolicyPredictionLookup.test(pos)
                && clientEffectivePlacementDyLookup != null
                && clientEffectivePlacementDyLookup.lookup(pos) != null;
    }

    /** Marks accepted item-placement cells with modern provenance (the policy is unconditional here). */
    public static int markPostPolicyPlacements(World world, Iterable<BlockPos> positions) {
        if (world == null || world.isClient() || positions == null
                || !WorldUpgradeRuntimePolicy.authorsModernPlacements(world)) {
            return 0;
        }
        int writes = 0;
        for (BlockPos pos : positions) {
            if (pos != null && addToAttachment(world, pos, MODERN_PLACEMENT_TYPE, "modern_placement")) {
                writes++;
            }
        }
        return writes;
    }

    /** Restores modern provenance captured before an existing block entered piston movement. */
    public static int restoreTransferredModernPlacements(World world, Iterable<BlockPos> positions) {
        if (world == null || world.isClient() || positions == null) {
            return 0;
        }
        int writes = 0;
        for (BlockPos pos : positions) {
            if (pos != null && addToAttachment(world, pos, MODERN_PLACEMENT_TYPE, "modern_placement")) {
                writes++;
            }
        }
        return writes;
    }

    /**
     * Direct authoritative backing read. A non-{@link World} view (a client render region) has no
     * chunk handle, so it resolves through {@link #clientPlacementDyLookup} — the same client-world
     * bridge the marker lookups use — and answers absent only when no bridge is installed
     * (dedicated server) or the client world has no fact.
     */
    public static PlacementDyFact rawPlacementDyFact(BlockView world, BlockPos pos) {
        if (pos == null) {
            return PlacementDyFact.absent();
        }
        if (!(world instanceof World w)) {
            ClientPlacementDyFactLookup lookup = clientPlacementDyLookup;
            if (lookup != null) {
                PlacementDyFact fact = lookup.lookup(pos);
                if (fact != null) {
                    return fact;
                }
            }
            return PlacementDyFact.absent();
        }
        WorldChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return PlacementDyFact.absent();
        }
        Long2ByteOpenHashMap map = chunk.getAttached(PLACEMENT_DY_TYPE);
        long key = pos.asLong();
        return (map != null && map.containsKey(key))
                ? PlacementDyFact.fromStoredSixteenths(map.get(key))
                : PlacementDyFact.absent();
    }

    /**
     * Triggers static-init class loading. Call once from the mod entrypoint so the
     * attachment is registered before any chunk loads.
     */
    public static void register() {
        // Touch the class so the static field initializes and registers with Fabric.
        if (ANCHOR_TYPE == null
                || FROZEN_FLAT_TYPE == null
                || LOWERED_SLAB_CARRIER_TYPE == null
                || COMPOUND_FULL_BLOCK_ANCHOR_TYPE == null
                || COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE == null
                || COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE == null
                || COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE == null
                || COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE == null
                || MODERN_PLACEMENT_TYPE == null
                || PLACEMENT_DY_TYPE == null) {
            throw new IllegalStateException("SlabAnchorAttachment failed to register");
        }
    }

    // ── server-side mutation ──────────────────────────────────────────

    /**
     * Records an anchor at {@code pos}. Server-side only; no-op on client world or
     * if {@code pos} does not qualify under {@link #qualifiesForAnchor}.
     */
    public static void addAnchor(World world, BlockPos pos, BlockState state) {
        if (world == null || world.isClient()) {
            return;
        }
        boolean qualifies = qualifiesForAnchor(world, pos, state);
        if (TRACE) {
            Slabbed.LOGGER.info("[ANCHOR] add attempt side=SERVER pos={} state={} qualifies={}",
                    pos.toShortString(), state, qualifies);
        }
        if (!qualifies) {
            return;
        }
        addAnchorUnchecked(world, pos);
    }

    public static void addSideAdjacentLoweredFullAnchor(
            World world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (world == null) {
            return;
        }
        if (!qualifiesForSideAdjacentLoweredFullAnchor(world, pos, state, sourcePos, sourceState)) {
            return;
        }
        addAnchorUnchecked(world, pos);
        if (qualifiesForSideAdjacentCompoundFullAnchor(world, pos, state, sourcePos, sourceState)) {
            addToAttachment(world, pos, COMPOUND_FULL_BLOCK_ANCHOR_TYPE, "compound_full_block_anchor");
        }
    }

    public static void addTopOfCompoundFullAnchor(
            World world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (world == null) {
            return;
        }
        if (!qualifiesForTopOfCompoundFullAnchor(world, pos, state, sourcePos, sourceState)) {
            return;
        }
        addAnchorUnchecked(world, pos);
        addToAttachment(world, pos, COMPOUND_FULL_BLOCK_ANCHOR_TYPE, "compound_full_block_anchor");
    }

    private static void addAnchorUnchecked(World world, BlockPos pos) {
        boolean added = addToAttachment(world, pos, ANCHOR_TYPE, "anchor");
        if (added && RuntimeDiagnostics.isBsFbLiveTraceEnabled()) {
            BlockPos supportPos = pos.down();
            RuntimeDiagnostics.captureBsFbLiveTrace(world, supportPos, pos, "ANCHOR_ADDED");
        }
        // Beta4 sidecar: if the position currently satisfies the compound full-block
        // condition (anchored ordinary full block above a lowered bottom slab carrier),
        // also record the authored dy=-1.0 lane so it survives source slab removal.
        BlockState state = world.getBlockState(pos);
        if (qualifiesForCompoundFullBlockAnchor(world, pos, state)) {
            addToAttachment(world, pos, COMPOUND_FULL_BLOCK_ANCHOR_TYPE, "compound_full_block_anchor");
        }
    }

    /**
     * FREEZE-ON-PLACE: locks a piece's lowered height at the moment it is placed so it
     * NEVER autonomously moves afterwards. LAW 1 (the placement law) — "a placed block must stay in
     * that spot and not autonomously pop." Once a piece is recorded here, the lowered
     * dy is read from the persistent anchor and {@code getYOffsetInner} never recomputes
     * it, so breaking a neighbour / source can no longer un-lower it (the pop) and the
     * value can no longer drift from the rendered mesh (the render-lag).
     *
     * <p>Server-side only. No-op if the piece is not lowered (so a block placed on solid
     * ground or in mid-air stays at 0) or is already anchored by the direct-support /
     * compound paths (so this only fills the previously-unfrozen cases: cantilevered full
     * blocks and adjacent-side-merged slabs). The live geometric paths still compute the
     * value used here and act as the first-frame fallback before the anchor syncs.
     */
    public static void freezeLoweredOnPlace(World world, BlockPos pos, BlockState state) {
        if (world == null || world.isClient() || pos == null || state == null
                || state.isAir() || !state.getFluidState().isEmpty()) {
            return;
        }
        if (isAnchored(world, pos) || isFrozenFlat(world, pos)) {
            return;
        }
        // SUBJECT reading: the placement fact for THIS cell is published only after
        // {@code BlockItem.place} returns, so at {@code onPlaced} time the public read still answers
        // with the stable-flat stand-in and every genuinely lowered placement would classify as FLAT.
        // Take the explicit placement-time reading instead — the documented read-only entry for
        // exactly this seam. Under frozen-OFF the two entries are provably the same code, so this is
        // byte-identical there. SUPPORT / neighbour terms below stay on the public read: those are
        // different cells whose facts already exist, or fact-less scenery that must keep reading
        // exactly as it is drawn.
        double dy = SlabSupport.getUnstoredYOffset(world, pos, state);
        // Only STRUCTURAL pieces (ordinary full blocks and slabs) freeze. Decorative followers
        // (lanterns, torches, hangers, signs, the crafting-table/BE "objects", …) must stay fully
        // GEOMETRIC so they always track their support's current surface — exactly the anchor
        // system's documented scope ("ordinary full-block vertical slab chains … No torch
        // interaction"). Applies to BOTH the lowered (dy<0) and flat (dy≈0) cases: freezing a
        // follower's lowered state pins it with a persistent anchor that goes STALE when the
        // support surface later changes (e.g. a Terrain-Slabs / vanilla BOTTOM slab retyped to a
        // flush TOP surface), leaving the follower smooshed -0.5 into it instead of recomputing
        // to flush. Structural cantilevered full blocks and adjacent-side-merged slabs still
        // freeze via this gate (both are full-height / SlabBlock candidates).
        boolean structural = isOrdinaryFullBlockAnchorCandidate(world, pos, state)
                || state.getBlock() instanceof SlabBlock;
        if (!structural) {
            return;
        }
        if (dy < -1.0e-6) {
            // addAnchorUnchecked records ANCHOR_TYPE (read as -0.5) and adds the compound
            // sidecar (-1.0) when the piece qualifies, so both lowered lanes freeze.
            addAnchorUnchecked(world, pos);
            return;
        }
        // dy ≈ 0: lock the FLAT height of a structural piece so a slab / lowered carrier placed
        // under or beside it later can no longer pull it down (the maintainer's "placed slab under a
        // floating block must not lower it"). Natural (terrain / setBlockState, which never calls
        // onPlaced) pieces stay fully geometric.
        addToAttachment(world, pos, FROZEN_FLAT_TYPE, "frozen_flat");
    }

    /**
     * Returns true if {@code pos} carries a freeze-on-place FLAT marker — a structural
     * piece whose height was locked at 0 when placed. Safe on server and client (client
     * mirror via {@link #clientFrozenFlatLookup}); false for non-{@link World} views.
     */
    public static boolean isFrozenFlat(BlockView world, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (!(world instanceof World w)) {
            return clientFrozenFlatLookup != null && clientFrozenFlatLookup.test(pos);
        }
        WorldChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet set = chunk.getAttached(FROZEN_FLAT_TYPE);
        return set != null && set.contains(pos.asLong());
    }

    /**
     * Records a beta4 compound full-block anchor at {@code pos}. Server-side only.
     * Idempotent; no-op if {@code pos} does not satisfy
     * {@link #qualifiesForCompoundFullBlockAnchor}.
     */
    public static void addCompoundFullBlockAnchor(World world, BlockPos pos, BlockState state) {
        if (world == null || world.isClient()) {
            return;
        }
        if (!qualifiesForCompoundFullBlockAnchor(world, pos, state)) {
            return;
        }
        addToAttachment(world, pos, COMPOUND_FULL_BLOCK_ANCHOR_TYPE, "compound_full_block_anchor");
    }

    /**
     * Clears any beta4 compound full-block anchor at {@code pos}. Server-side only.
     */
    public static void removeCompoundFullBlockAnchor(World world, BlockPos pos) {
        removeFromAttachment(world, pos, COMPOUND_FULL_BLOCK_ANCHOR_TYPE, "compound_full_block_anchor");
    }

    public static void addCompoundVisibleSideLowerSlab(
            World world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (world == null || world.isClient()) {
            return;
        }
        if (!qualifiesForCompoundVisibleSideLowerSlab(world, pos, state, sourcePos, sourceState)) {
            return;
        }
        boolean added = addToAttachment(world, pos, COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE,
                "compound_visible_side_lower_slab");
        if (added) {
            // Trigger getStateForNeighborUpdate(DOWN) on the block above so any stale floor
            // torch that was placed before this compound mark is written gets revalidated and
            // removed by TorchBlockMixin.getStateForNeighborUpdate.
            world.replaceWithStateForNeighborUpdate(Direction.DOWN, state, pos.up(), pos, Block.NOTIFY_ALL, 512);
        }
    }

    public static void addCompoundVisibleSideUpperSlab(
            World world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (world == null || world.isClient()) {
            return;
        }
        if (!qualifiesForCompoundVisibleSideUpperSlab(world, pos, state, sourcePos, sourceState)) {
            return;
        }
        addToAttachment(world, pos, COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE,
                "compound_visible_side_upper_slab");
    }

    public static void addCompoundVisibleSideDoubleSlab(
            World world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (world == null || world.isClient()) {
            return;
        }
        if (!qualifiesForCompoundVisibleSideDoubleSlab(world, pos, state, sourcePos, sourceState)) {
            return;
        }
        removeFromAttachment(world, pos, COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE,
                "compound_visible_side_lower_slab");
        removeFromAttachment(world, pos, COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE,
                "compound_visible_side_upper_slab");
        addToAttachment(world, pos, COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE,
                "compound_visible_side_double_slab");
    }

    public static void addCompoundVisibleOwnerTopSlab(
            World world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (world == null || world.isClient()) {
            return;
        }
        if (!qualifiesForCompoundVisibleOwnerTopSlab(world, pos, state, sourcePos, sourceState)) {
            return;
        }
        boolean added = addToAttachment(world, pos, COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE,
                "compound_visible_owner_top_slab");
        if (added) {
            world.replaceWithStateForNeighborUpdate(Direction.DOWN, state, pos.up(), pos, Block.NOTIFY_ALL, 512);
        }
    }

    public static void updatePersistentLoweredSlabCarrier(World world, BlockPos pos, BlockState state) {
        if (world == null || world.isClient()) {
            return;
        }
        boolean qualifies = qualifiesForPersistentLoweredSlabCarrier(world, pos, state);
        if (TRACE) {
            Slabbed.LOGGER.info("[ANCHOR] lowered slab carrier update side=SERVER pos={} state={} qualifies={}",
                    pos.toShortString(), state, qualifies);
        }
        if (qualifies) {
            addToAttachment(world, pos, LOWERED_SLAB_CARRIER_TYPE, "lowered_slab_carrier");
        } else if (state != null && state.getBlock() instanceof SlabBlock) {
            removeFromAttachment(world, pos, LOWERED_SLAB_CARRIER_TYPE, "lowered_slab_carrier");
        }
        logCompoundVisibleRenderTraceSupportUpdate(world, pos, state, qualifies);
    }

    private static boolean addToAttachment(
            World world,
            BlockPos pos,
            AttachmentType<LongOpenHashSet> type,
            String label
    ) {
        WorldChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] {} add reject pos={} reason=chunk_null", label, pos.toShortString());
            }
            return false;
        }
        LongOpenHashSet existing = chunk.getAttached(type);
        LongOpenHashSet set = existing == null ? new LongOpenHashSet() : new LongOpenHashSet(existing);
        BlockState stateBefore = RuntimeDiagnostics.beta35SlabJumpSourceTruthEnabled()
                ? world.getBlockState(pos) : null;
        if (set.add(pos.asLong())) {
            // setAttached triggers persistence + auto-sync for synced attachments.
            chunk.setAttached(type, set);
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] {} add success pos={} chunk={} setSize={}",
                        label, pos.toShortString(), chunk.getPos(), set.size());
            }
            logCompoundVisibleRenderTraceMarkerSet(world, pos, type, label, "add", true);
            RuntimeDiagnostics.recordBeta35SlabJumpAnchorEvent(
                    world,
                    "ADD",
                    type, pos, stateBefore, stateBefore);
            return true;
        }
        return false;
    }

    /**
     * Clears any anchor at {@code pos}. Server-side only.
     */
    public static void removeAnchor(World world, BlockPos pos) {
        boolean removed = removeFromAttachment(world, pos, ANCHOR_TYPE, "anchor");
        if (removed && RuntimeDiagnostics.isBsFbLiveTraceEnabled()) {
            BlockPos supportPos = pos.down();
            RuntimeDiagnostics.captureBsFbLiveTrace(world, supportPos, pos, "ANCHOR_REMOVED");
        }
        // Freeze-on-place flat marker clears when the piece itself is broken/replaced
        // (onStateReplaced calls removeAnchor for every removal), so a fresh placement in
        // the same spot re-evaluates from scratch.
        removeFromAttachment(world, pos, FROZEN_FLAT_TYPE, "frozen_flat");
        // Beta4 sidecar travels with the ordinary anchor: when the compound block
        // itself is broken/replaced, clear the authored compound truth too.
        removeFromAttachment(world, pos, COMPOUND_FULL_BLOCK_ANCHOR_TYPE, "compound_full_block_anchor");
        removeFromAttachment(world, pos, COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE,
                "compound_visible_side_lower_slab");
        removeFromAttachment(world, pos, COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE,
                "compound_visible_side_upper_slab");
        removeFromAttachment(world, pos, COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE,
                "compound_visible_side_double_slab");
        removeFromAttachment(world, pos, COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE,
                "compound_visible_owner_top_slab");
        removeFromAttachment(world, pos, MODERN_PLACEMENT_TYPE, "modern_placement");
        // FROZEN-DY: the stored placement height dies with the block, so a fresh placement in the same
        // cell captures its own aim from scratch. Copy-on-write, exactly like the writer.
        if (world != null && !world.isClient()) {
            WorldChunk dyChunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (dyChunk != null) {
                Long2ByteOpenHashMap dyMap = dyChunk.getAttached(PLACEMENT_DY_TYPE);
                if (dyMap != null && dyMap.containsKey(pos.asLong())) {
                    Long2ByteOpenHashMap copy = new Long2ByteOpenHashMap(dyMap);
                    copy.remove(pos.asLong());
                    dyChunk.setAttached(PLACEMENT_DY_TYPE, copy);
                }
            }
        }
    }

    public static void removePersistentLoweredSlabCarrier(World world, BlockPos pos) {
        removeFromAttachment(world, pos, LOWERED_SLAB_CARRIER_TYPE, "lowered_slab_carrier");
    }

    private static boolean removeFromAttachment(
            World world,
            BlockPos pos,
            AttachmentType<LongOpenHashSet> type,
            String label
    ) {
        if (world == null || world.isClient()) {
            return false;
        }
        WorldChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet existing = chunk.getAttached(type);
        if (existing == null || existing.isEmpty()) {
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] {} remove pos={} existed=false", label, pos.toShortString());
            }
            return false;
        }
        LongOpenHashSet set = new LongOpenHashSet(existing);
        boolean removed = set.remove(pos.asLong());
        if (TRACE) {
            Slabbed.LOGGER.info("[ANCHOR] {} remove pos={} existed={}", label, pos.toShortString(), removed);
        }
        if (removed) {
            if (set.isEmpty()) {
                chunk.removeAttached(type);
            } else {
                chunk.setAttached(type, set);
            }
            logCompoundVisibleRenderTraceMarkerSet(world, pos, type, label, "remove", false);
            RuntimeDiagnostics.recordBeta35SlabJumpAnchorEvent(
                    world,
                    "REMOVE",
                    type, pos, world.getBlockState(pos), world.getBlockState(pos));
        }
        return removed;
    }

    public static boolean beta4CompoundVisibleRenderTraceEnabled() {
        return BETA4_COMPOUND_VISIBLE_RENDER_TRACE;
    }

    public static boolean isCompoundVisibleAttachmentType(AttachmentType<LongOpenHashSet> type) {
        return type == COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE
                || type == COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE
                || type == COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE
                || type == COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE;
    }

    public static String compoundVisibleAttachmentLabel(AttachmentType<LongOpenHashSet> type) {
        if (type == COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE) {
            return "lower";
        }
        if (type == COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE) {
            return "upper";
        }
        if (type == COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE) {
            return "double";
        }
        if (type == COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE) {
            return "top";
        }
        if (type == LOWERED_SLAB_CARRIER_TYPE) {
            return "lowered_slab_carrier";
        }
        if (type == COMPOUND_FULL_BLOCK_ANCHOR_TYPE) {
            return "compound_full_block_anchor";
        }
        if (type == ANCHOR_TYPE) {
            return "anchor";
        }
        return "unknown";
    }

    private static void logCompoundVisibleRenderTraceMarkerSet(
            World world,
            BlockPos pos,
            AttachmentType<LongOpenHashSet> type,
            String label,
            String action,
            boolean serverMarker
    ) {
        if (!beta4CompoundVisibleRenderTraceEnabled() || !isCompoundVisibleAttachmentType(type)) {
            return;
        }
        BlockState state = world.getBlockState(pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        Slabbed.LOGGER.info(
                "[MAINTAINER_BETA4_COMPOUND_VISIBLE_RENDER_TRACE_MARKER_SET] action={} pos={} marker={} label={} serverMarker={} clientMarker=n/a modelViewType=World slabSupportDy={} clientDy=n/a candidateRerenderScheduled=false neighborRerenderScheduled=false",
                action,
                pos.toShortString(),
                compoundVisibleAttachmentLabel(type),
                label,
                serverMarker,
                dy);
    }

    private static void logCompoundVisibleRenderTraceSupportUpdate(
            World world,
            BlockPos pos,
            BlockState state,
            boolean qualifies
    ) {
        if (!beta4CompoundVisibleRenderTraceEnabled()) {
            return;
        }
        double dy = state == null ? 0.0d : SlabSupport.getYOffset(world, pos, state);
        Slabbed.LOGGER.info(
                "[MAINTAINER_BETA4_COMPOUND_VISIBLE_RENDER_TRACE_SUPPORT_UPDATE] pos={} marker=lowered_slab_carrier serverMarker={} clientMarker=n/a modelViewType=World slabSupportDy={} clientDy=n/a candidateRerenderScheduled=false neighborRerenderScheduled=false",
                pos.toShortString(),
                qualifies,
                dy);
    }

    // ── shared query ──────────────────────────────────────────────────

    /**
     * Returns true if {@code pos} carries a persistent slab-anchor.
     *
     * <p>Safe on both server and client (client mirror is populated via attachment sync).
     * Returns false for any {@link BlockView} that is not a full {@link World}, so it is
     * safe to call from shape mixins that may receive partial views during chunk gen.
     */
    public static boolean isAnchored(BlockView world, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (!(world instanceof World w)) {
            // Chunk render paths (e.g. ChunkRendererRegion) are not World instances and
            // cannot access chunk attachments directly.  Delegate to the client fallback
            // hook so the model render path sees the same anchor state as outline/raycast.
            return clientAnchorLookup != null && clientAnchorLookup.test(pos);
        }
        WorldChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet set = chunk.getAttached(ANCHOR_TYPE);
        boolean anchored = set != null && set.contains(pos.asLong());
        if (TRACE && anchored) {
            Slabbed.LOGGER.info("[ANCHOR] query true side={} pos={}",
                    w.isClient() ? "CLIENT" : "SERVER", pos.toShortString());
        }
        return anchored;
    }

    /**
     * Returns true if {@code pos} carries a beta4 sidecar compound full-block anchor.
     *
     * <p>Independent of {@link #isAnchored}: a pos may be anchored without being a
     * compound anchor (ordinary {@code dy=-0.5}). This sidecar is the authored truth
     * for {@code dy=-1.0} compound lane and survives source slab removal.
     *
     * <p>Mirrors the {@link #isAnchored} dispatch: server World, client World, and
     * non-World render views via {@link #clientCompoundFullBlockAnchorLookup}.
     */
    public static boolean isCompoundFullBlockAnchor(BlockView world, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (!(world instanceof World w)) {
            return clientCompoundFullBlockAnchorLookup != null
                    && clientCompoundFullBlockAnchorLookup.test(pos);
        }
        WorldChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet set = chunk.getAttached(COMPOUND_FULL_BLOCK_ANCHOR_TYPE);
        boolean compound = set != null && set.contains(pos.asLong());
        if (TRACE && compound) {
            Slabbed.LOGGER.info("[ANCHOR] compound_full_block query true side={} pos={}",
                    w.isClient() ? "CLIENT" : "SERVER", pos.toShortString());
        }
        return compound;
    }

    public static boolean isCompoundVisibleSideLowerSlab(BlockView world, BlockPos pos, BlockState state) {
        if (!isCompoundVisibleSideLowerSlabState(state) || pos == null) {
            return false;
        }
        if (!(world instanceof World w)) {
            return clientCompoundVisibleSideLowerSlabLookup != null
                    && clientCompoundVisibleSideLowerSlabLookup.test(pos);
        }
        WorldChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet set = chunk.getAttached(COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE);
        boolean marked = set != null && set.contains(pos.asLong());
        if (TRACE && marked) {
            Slabbed.LOGGER.info("[ANCHOR] compound_visible_side_lower_slab query true side={} pos={}",
                    w.isClient() ? "CLIENT" : "SERVER", pos.toShortString());
        }
        return marked;
    }

    public static boolean isCompoundVisibleSideUpperSlab(BlockView world, BlockPos pos, BlockState state) {
        if (!isCompoundVisibleSideUpperSlabState(state) || pos == null) {
            return false;
        }
        if (!(world instanceof World w)) {
            return clientCompoundVisibleSideUpperSlabLookup != null
                    && clientCompoundVisibleSideUpperSlabLookup.test(pos);
        }
        WorldChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet set = chunk.getAttached(COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE);
        boolean marked = set != null && set.contains(pos.asLong());
        if (TRACE && marked) {
            Slabbed.LOGGER.info("[ANCHOR] compound_visible_side_upper_slab query true side={} pos={}",
                    w.isClient() ? "CLIENT" : "SERVER", pos.toShortString());
        }
        return marked;
    }

    public static boolean isCompoundVisibleSideDoubleSlab(BlockView world, BlockPos pos, BlockState state) {
        if (!isCompoundVisibleSideDoubleSlabState(state) || pos == null) {
            return false;
        }
        if (!(world instanceof World w)) {
            return clientCompoundVisibleSideDoubleSlabLookup != null
                    && clientCompoundVisibleSideDoubleSlabLookup.test(pos);
        }
        WorldChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet set = chunk.getAttached(COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE);
        boolean marked = set != null && set.contains(pos.asLong());
        if (TRACE && marked) {
            Slabbed.LOGGER.info("[ANCHOR] compound_visible_side_double_slab query true side={} pos={}",
                    w.isClient() ? "CLIENT" : "SERVER", pos.toShortString());
        }
        return marked;
    }

    public static boolean isCompoundVisibleOwnerTopSlab(BlockView world, BlockPos pos, BlockState state) {
        if (!isCompoundVisibleOwnerTopSlabState(state) || pos == null) {
            return false;
        }
        if (!(world instanceof World w)) {
            return clientCompoundVisibleOwnerTopSlabLookup != null
                    && clientCompoundVisibleOwnerTopSlabLookup.test(pos);
        }
        WorldChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet set = chunk.getAttached(COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE);
        boolean marked = set != null && set.contains(pos.asLong());
        if (TRACE && marked) {
            Slabbed.LOGGER.info("[ANCHOR] compound_visible_owner_top_slab query true side={} pos={}",
                    w.isClient() ? "CLIENT" : "SERVER", pos.toShortString());
        }
        return marked;
    }

    public static boolean isPersistentLoweredSlabCarrier(BlockView world, BlockPos pos, BlockState state) {
        if (!isPersistentLoweredSlabCarrierState(state) || pos == null) {
            return false;
        }
        if (isCompoundVisibleOwnerTopSlab(world, pos, state)) {
            return false;
        }
        if (!(world instanceof World w)) {
            return clientLoweredSlabCarrierLookup != null && clientLoweredSlabCarrierLookup.test(pos);
        }
        WorldChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state);
        }
        LongOpenHashSet set = chunk.getAttached(LOWERED_SLAB_CARRIER_TYPE);
        boolean carrier = set != null && set.contains(pos.asLong());
        if (!carrier && isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state)) {
            carrier = true;
        }
        if (TRACE && carrier) {
            Slabbed.LOGGER.info("[ANCHOR] lowered slab carrier query true side={} pos={}",
                    w.isClient() ? "CLIENT" : "SERVER", pos.toShortString());
        }
        return carrier;
    }

    public static boolean isPersistentLoweredBottomSlabCarrierNonRecursive(
            BlockView world,
            BlockPos pos,
            BlockState state
    ) {
        if (!isBottomPersistentLoweredSlabCarrierState(state) || world == null || pos == null) {
            return false;
        }
        if (isCompoundVisibleOwnerTopSlab(world, pos, state)) {
            return false;
        }
        if (world instanceof World w) {
            WorldChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null) {
                LongOpenHashSet set = chunk.getAttached(LOWERED_SLAB_CARRIER_TYPE);
                if (set != null && set.contains(pos.asLong())) {
                    return true;
                }
            }
        } else if (clientLoweredSlabCarrierLookup != null && clientLoweredSlabCarrierLookup.test(pos)) {
            return true;
        }
        return qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlockNonRecursive(world, pos, state)
                || qualifiesForPersistentLoweredBottomSlabOnAdjacentLoweredBridgeSupportNonRecursive(world, pos, state);
    }

    // ── qualifier ─────────────────────────────────────────────────────

    /**
     * Tight predicate matching persistent ordinary full-block slab-chain rules:
     * <ul>
     *   <li>not air, not fluid</li>
     *   <li>not a slab, carpet, thin top layer, block-entity, bed, or double-block</li>
     *   <li>solid full block, or a non-solid block with full-height carrier bounds
     *       in a named Slabbed anchor lane</li>
     *   <li>has a bottom slab directly below, or sits directly on an ordinary full
     *       block already lowered by exactly {@code -0.5}</li>
     * </ul>
     *
     * <p>Strictly narrower than {@link SlabSupport#shouldOffset}: compound
     * bed/double-half cases, side slabs, carpets, block entities, and non-full blocks
     * remain excluded.
     */
    public static boolean qualifiesForAnchor(BlockView world, BlockPos pos, BlockState state) {
        if (!isOrdinaryFullBlockAnchorCandidate(world, pos, state)) {
            return false;
        }
        if (SlabSupport.hasBottomSlabBelow(world, pos)) {
            return true;
        }
        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);
        return qualifiesAsVerticalChainSupport(world, belowPos, below);
    }

    public static boolean isOrdinaryFullBlockAnchorCandidate(BlockView world, BlockPos pos, BlockState state) {
        if (state == null || state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        var block = state.getBlock();
        if (block instanceof SlabBlock) {
            return false;
        }
        if (block instanceof CarpetBlock || isPaleMossCarpet(block)) {
            return false;
        }
        if (SlabSupport.isThinTopLayer(state)) {
            return false;
        }
        if (block instanceof BlockEntityProvider) {
            return false;
        }
        if (state.contains(Properties.BED_PART)) {
            return false;
        }
        if (state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return false;
        }
        return isOrdinaryFullBlockAnchorCarrierBounds(world, pos, state);
    }

    private static boolean isPaleMossCarpet(Block block) {
        return block == Registries.BLOCK.get(Identifier.of("minecraft", "pale_moss_carpet"));
    }

    public static boolean qualifiesForDirectAnchor(BlockView world, BlockPos pos, BlockState state) {
        return qualifiesForAnchor(world, pos, state) && SlabSupport.hasBottomSlabBelow(world, pos);
    }

    /**
     * Beta4 sidecar predicate: the position is a legal compound ordinary full-block
     * authoring at lane {@code dy=-1.0}, i.e. it is a normal anchor candidate
     * (ordinary full block) AND the slab directly below is the lowered compound
     * source slab (a bottom slab classified by
     * {@link SlabSupport#isLoweredCompoundSourceSlab}).
     *
     * <p>Excludes ordinary {@code dy=-0.5} anchors over a vanilla bottom slab,
     * slab blocks, non-full blocks, beds, double-blocks, etc. — exactly the
     * scope listed in the beta4 source-mode design.
     */
    public static boolean qualifiesForCompoundFullBlockAnchor(
            BlockView world,
            BlockPos pos,
            BlockState state
    ) {
        if (!isOrdinaryFullBlockAnchorCandidate(world, pos, state)) {
            return false;
        }
        if (world == null || pos == null) {
            return false;
        }
        BlockPos belowPos = pos.down();
        BlockState belowSlab = world.getBlockState(belowPos);
        return SlabSupport.isLoweredCompoundSourceSlab(world, belowPos, belowSlab);
    }

    private static boolean qualifiesForTopOfCompoundFullAnchor(
            BlockView world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (!isOrdinaryFullBlockAnchorCandidate(world, pos, state)) {
            return false;
        }
        if (world == null || pos == null || sourcePos == null || sourceState == null) {
            return false;
        }
        if (!sourcePos.equals(pos.down())) {
            return false;
        }
        if (sourceState.getBlock() instanceof SlabBlock) {
            return false;
        }
        if (!isCompoundFullBlockAnchor(world, sourcePos)) {
            return false;
        }
        double sourceDy = SlabSupport.getYOffset(world, sourcePos, sourceState);
        return Math.abs(sourceDy + 1.0d) <= 1.0e-6d;
    }

    private static boolean qualifiesForCompoundVisibleSideLowerSlab(
            BlockView world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (!isCompoundVisibleSideLowerSlabState(state)
                || world == null
                || pos == null
                || sourcePos == null
                || sourceState == null) {
            return false;
        }
        if (!isCompoundVisibleSideSource(world, sourcePos, sourceState)) {
            return false;
        }
        int dx = Math.abs(pos.getX() - sourcePos.getX());
        int dy = Math.abs(pos.getY() - sourcePos.getY());
        int dz = Math.abs(pos.getZ() - sourcePos.getZ());
        return dy == 0 && dx + dz == 1;
    }

    private static boolean qualifiesForCompoundVisibleSideUpperSlab(
            BlockView world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (!isCompoundVisibleSideUpperSlabState(state)
                || world == null
                || pos == null
                || sourcePos == null
                || sourceState == null) {
            return false;
        }
        if (!isCompoundVisibleSideSource(world, sourcePos, sourceState)) {
            return false;
        }
        int dx = Math.abs(pos.getX() - sourcePos.getX());
        int dy = Math.abs(pos.getY() - sourcePos.getY());
        int dz = Math.abs(pos.getZ() - sourcePos.getZ());
        return dy == 0 && dx + dz == 1;
    }

    private static boolean qualifiesForCompoundVisibleSideDoubleSlab(
            BlockView world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (!isCompoundVisibleSideDoubleSlabState(state)
                || world == null
                || pos == null
                || sourcePos == null
                || sourceState == null) {
            return false;
        }
        if (!isCompoundVisibleSideSource(world, sourcePos, sourceState)) {
            return false;
        }
        int dx = Math.abs(pos.getX() - sourcePos.getX());
        int dy = Math.abs(pos.getY() - sourcePos.getY());
        int dz = Math.abs(pos.getZ() - sourcePos.getZ());
        return dy == 0 && dx + dz == 1;
    }

    private static boolean isCompoundVisibleSideSource(
            BlockView world,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (world == null || sourcePos == null || sourceState == null) {
            return false;
        }
        double sourceDy = SlabSupport.getYOffset(world, sourcePos, sourceState);
        if (Math.abs(sourceDy + 1.0d) > 1.0e-6d) {
            return false;
        }
        if (sourceState.getBlock() instanceof SlabBlock) {
            return SlabSupport.isCompoundVisibleSlabLaneOwner(world, sourcePos, sourceState);
        }
        return isOrdinaryFullBlockAnchorCandidate(world, sourcePos, sourceState)
                && isCompoundFullBlockAnchor(world, sourcePos);
    }

    public static boolean qualifiesForPersistentLoweredSlabCarrier(BlockView world, BlockPos pos, BlockState state) {
        return isPersistentLoweredSlabCarrierState(state)
                && !isCompoundVisibleOwnerTopSlab(world, pos, state)
                && (SlabSupport.isLoweredSideLaneSlabCarrier(world, pos, state)
                || qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlock(world, pos, state)
                || qualifiesForPersistentLoweredBottomSlabOnAdjacentLoweredBridgeSupport(world, pos, state));
    }

    public static boolean isLoweredFullBlockSlabCarrierSupport(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isNonSlabNonFluidCarrierSupportState(state)) {
            return false;
        }
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (!near(dy, -0.5d)) {
            return false;
        }
        return isFullHeightNonSlabCarrierSupport(world, pos, state, dy);
    }

    private static boolean qualifiesForCompoundVisibleOwnerTopSlab(
            BlockView world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (!isCompoundVisibleOwnerTopSlabState(state)
                || world == null
                || pos == null
                || sourcePos == null
                || sourceState == null) {
            return false;
        }
        if (!pos.equals(sourcePos.up())) {
            return false;
        }
        if (!isOrdinaryFullBlockAnchorCandidate(world, sourcePos, sourceState)
                || !(isCompoundFullBlockAnchor(world, sourcePos) || isAnchored(world, sourcePos))) {
            return false;
        }
        double sourceDy = SlabSupport.getYOffset(world, sourcePos, sourceState);
        if (isCompoundFullBlockAnchor(world, sourcePos)) {
            return Math.abs(sourceDy + 1.0d) <= 1.0e-6d;
        }
        return Math.abs(sourceDy + 0.5d) <= 1.0e-6d;
    }

    private static boolean isPersistentLoweredSlabCarrierState(BlockState state) {
        return state != null
                && state.getBlock() instanceof SlabBlock
                && state.contains(SlabBlock.TYPE)
                && state.getFluidState().isEmpty();
    }

    private static boolean isCompoundVisibleSideLowerSlabState(BlockState state) {
        return state != null
                && state.isOf(Blocks.STONE_SLAB)
                && state.contains(SlabBlock.TYPE)
                && state.get(SlabBlock.TYPE) == SlabType.BOTTOM
                && state.getFluidState().isEmpty();
    }

    private static boolean isCompoundVisibleSideUpperSlabState(BlockState state) {
        return state != null
                && state.isOf(Blocks.STONE_SLAB)
                && state.contains(SlabBlock.TYPE)
                && state.get(SlabBlock.TYPE) == SlabType.TOP
                && state.getFluidState().isEmpty();
    }

    private static boolean isCompoundVisibleSideDoubleSlabState(BlockState state) {
        return state != null
                && state.isOf(Blocks.STONE_SLAB)
                && state.contains(SlabBlock.TYPE)
                && state.get(SlabBlock.TYPE) == SlabType.DOUBLE
                && state.getFluidState().isEmpty();
    }

    private static boolean isCompoundVisibleOwnerTopSlabState(BlockState state) {
        return state != null
                && state.getBlock() instanceof SlabBlock
                && state.contains(SlabBlock.TYPE)
                && state.get(SlabBlock.TYPE) == SlabType.BOTTOM
                && state.getFluidState().isEmpty();
    }

    private static boolean isBottomPersistentLoweredSlabCarrierState(BlockState state) {
        return isPersistentLoweredSlabCarrierState(state) && state.get(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    private static boolean qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlock(
            BlockView world,
            BlockPos pos,
            BlockState state
    ) {
        // SUBJECT reading (same seam as freezeLoweredOnPlace): this qualifier judges the cell being
        // placed RIGHT NOW, and that cell's fact publishes only after {@code BlockItem.place} returns.
        // The public read would answer with the stable-flat stand-in and disqualify every genuine
        // -0.5 landing, so the subject term takes the explicit placement-time reading. The SUPPORT
        // term below stays on the public read — a different cell whose fact already exists. Under
        // frozen-OFF the two entries are provably the same code, so this is byte-identical there.
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || state.get(SlabBlock.TYPE) != SlabType.BOTTOM
                || !state.getFluidState().isEmpty()
                || SlabSupport.getUnstoredYOffset(world, pos, state) != -0.5) {
            return false;
        }
        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);
        return isLoweredFullBlockSlabCarrierSupport(world, belowPos, below);
    }

    private static boolean qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlockNonRecursive(
            BlockView world,
            BlockPos pos,
            BlockState state
    ) {
        if (!isBottomPersistentLoweredSlabCarrierState(state) || world == null || pos == null) {
            return false;
        }
        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);
        if (!isNonSlabNonFluidCarrierSupportState(below)
                || !isFullHeightNonSlabCarrierSupport(world, belowPos, below, -0.5d)) {
            return false;
        }
        return isAnchored(world, belowPos) || SlabSupport.hasBottomSlabBelow(world, belowPos);
    }

    private static boolean isNonSlabNonFluidCarrierSupportState(BlockState state) {
        return state != null
                && !state.isAir()
                && !(state.getBlock() instanceof SlabBlock)
                && state.getFluidState().isEmpty();
    }

    private static boolean isFullHeightNonSlabCarrierSupport(
            BlockView world,
            BlockPos pos,
            BlockState state,
            double expectedDy
    ) {
        if (world == null || pos == null || !isNonSlabNonFluidCarrierSupportState(state)) {
            return false;
        }
        if (state.isSolidBlock(world, pos)) {
            return true;
        }
        return hasFullHeightCarrierBounds(world, pos, state, expectedDy);
    }

    private static boolean isOrdinaryFullBlockAnchorCarrierBounds(
            BlockView world,
            BlockPos pos,
            BlockState state
    ) {
        if (world == null || pos == null || !isNonSlabNonFluidCarrierSupportState(state)) {
            return false;
        }
        if (state.isSolidBlock(world, pos)) {
            return true;
        }
        // Candidate checks can run after Slabbed has shifted non-solid full-height
        // carriers into their visible lane; accept only the named legal anchor lanes.
        return hasFullHeightCarrierBounds(world, pos, state, 0.0d)
                || hasFullHeightCarrierBounds(world, pos, state, -0.5d)
                || hasFullHeightCarrierBounds(world, pos, state, -1.0d);
    }

    private static boolean hasFullHeightCarrierBounds(
            BlockView world,
            BlockPos pos,
            BlockState state,
            double expectedDy
    ) {
        VoxelShape outline = state.getOutlineShape(world, pos);
        if (outline == null || outline.isEmpty()) {
            return false;
        }
        Box bounds = outline.getBoundingBox();
        return near(bounds.minX, 0.0d)
                && near(bounds.maxX, 1.0d)
                && near(bounds.minZ, 0.0d)
                && near(bounds.maxZ, 1.0d)
                && (unitYAt(bounds, 0.0d) || unitYAt(bounds, expectedDy));
    }

    private static boolean unitYAt(Box bounds, double minY) {
        return near(bounds.minY, minY) && near(bounds.maxY, minY + 1.0d);
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) <= 1.0e-6d;
    }

    private static boolean qualifiesForPersistentLoweredBottomSlabOnAdjacentLoweredBridgeSupport(
            BlockView world,
            BlockPos pos,
            BlockState state
    ) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || state.get(SlabBlock.TYPE) != SlabType.BOTTOM
                || !state.getFluidState().isEmpty()) {
            return false;
        }

        BlockPos supportY = pos.down();
        boolean hasLoweredAnchoredBridgeNeighbor = false;
        for (var dir : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = supportY.offset(dir);
            BlockState neighbor = world.getBlockState(neighborPos);
            if (!isOrdinaryFullBlockAnchorCandidate(world, neighborPos, neighbor)) {
                continue;
            }
            if (!isAnchored(world, neighborPos)) {
                continue;
            }
            if (SlabSupport.getYOffset(world, neighborPos, neighbor) != -0.5d) {
                continue;
            }
            hasLoweredAnchoredBridgeNeighbor = true;
            break;
        }
        if (!hasLoweredAnchoredBridgeNeighbor) {
            return false;
        }

        BlockState below = world.getBlockState(supportY);
        if (isOrdinaryFullBlockAnchorCandidate(world, supportY, below)
                && (isAnchored(world, supportY) || SlabSupport.getYOffset(world, supportY, below) == -0.5d)) {
            return true;
        }
        return below.isAir();
    }

    private static boolean qualifiesForPersistentLoweredBottomSlabOnAdjacentLoweredBridgeSupportNonRecursive(
            BlockView world,
            BlockPos pos,
            BlockState state
    ) {
        if (!isBottomPersistentLoweredSlabCarrierState(state) || world == null || pos == null) {
            return false;
        }
        BlockPos supportY = pos.down();
        boolean hasLoweredAnchoredBridgeNeighbor = false;
        for (var dir : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = supportY.offset(dir);
            BlockState neighbor = world.getBlockState(neighborPos);
            if (!isOrdinaryFullBlockAnchorCandidate(world, neighborPos, neighbor)) {
                continue;
            }
            if (!(isAnchored(world, neighborPos) || SlabSupport.hasBottomSlabBelow(world, neighborPos))) {
                continue;
            }
            hasLoweredAnchoredBridgeNeighbor = true;
            break;
        }
        if (!hasLoweredAnchoredBridgeNeighbor) {
            return false;
        }
        BlockState below = world.getBlockState(supportY);
        if (isOrdinaryFullBlockAnchorCandidate(world, supportY, below)
                && (isAnchored(world, supportY) || SlabSupport.hasBottomSlabBelow(world, supportY))) {
            return true;
        }
        return below.isAir();
    }

    private static boolean qualifiesAsVerticalChainSupport(BlockView world, BlockPos pos, BlockState state) {
        return SlabSupport.isFullHeightLoweredCarrier(world, pos, state);
    }

    private static boolean qualifiesForSideAdjacentLoweredFullAnchor(
            BlockView world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        // A full block must NOT inherit lowering from a horizontal neighbour. Side-adjacent
        // anchoring lowered a freestanding full block purely because the block beside it was
        // lowered, with no slab/lowered support of its own — a persistent anchor that then
        // (a) sank the block into the ground/air, (b) went stale (it stayed lowered after the
        // source carrier was removed, since the anchor never recomputes), and (c) spread the
        // lowering further to ITS neighbours. Live-confirmed 2026-06-11: this is the "blocks
        // inheriting states from neighbours" / tree-canopy contagion. Lowering for full blocks
        // now comes only from genuine support directly below (a slab, or a lowered full-block
        // column down to a slab) via qualifiesForAnchor — never sideways.
        if (true) {
            return false;
        }
        if (!isOrdinaryFullBlockAnchorCandidate(world, pos, state)
                || !qualifiesAsSideAdjacentLoweredFullAnchorSource(world, sourcePos, sourceState)) {
            return false;
        }
        int dx = Math.abs(pos.getX() - sourcePos.getX());
        int dy = Math.abs(pos.getY() - sourcePos.getY());
        int dz = Math.abs(pos.getZ() - sourcePos.getZ());
        if (dy != 0 || dx + dz != 1) {
            return false;
        }
        double sourceDy = SlabSupport.getYOffset(world, sourcePos, sourceState);
        return sourceDy < 0.0d;
    }

    private static boolean qualifiesForSideAdjacentCompoundFullAnchor(
            BlockView world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (world == null || pos == null || sourcePos == null) {
            return false;
        }
        if (!isOrdinaryFullBlockAnchorCandidate(world, pos, state)
                || !isOrdinaryFullBlockAnchorCandidate(world, sourcePos, sourceState)) {
            return false;
        }
        if (!isCompoundFullBlockAnchor(world, sourcePos)) {
            return false;
        }
        if (SlabSupport.getYOffset(world, sourcePos, sourceState) != -1.0d) {
            return false;
        }
        int dx = Math.abs(pos.getX() - sourcePos.getX());
        int dy = Math.abs(pos.getY() - sourcePos.getY());
        int dz = Math.abs(pos.getZ() - sourcePos.getZ());
        return dy == 0 && dx + dz == 1;
    }

    private static boolean qualifiesAsSideAdjacentLoweredFullAnchorSource(
            BlockView world,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        return SlabSupport.isFullHeightLoweredCarrier(world, sourcePos, sourceState)
                || SlabSupport.isLoweredSideLaneSlabCarrier(world, sourcePos, sourceState)
                || SlabSupport.isBottomSlabLoweredByCarrierBelow(world, sourcePos, sourceState);
    }
}
