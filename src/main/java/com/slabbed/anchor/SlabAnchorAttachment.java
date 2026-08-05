package com.slabbed.anchor;

import java.util.function.Predicate;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.slabbed.Slabbed;
import com.slabbed.compat.CompatHooks;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Persistent slab-anchor registry.
 *
 * <p>When an ordinary full block is placed directly on a bottom slab, or on an
 * ordinary full-block chain that is already lowered by a slab anchor, that placement is
 * recorded as an anchor on the chunk so the block keeps its lowered dy even if the
 * support below is later removed. Anchors are cleared when the anchored block itself is
 * broken/replaced.
 *
 * <p>Storage: per-{@link LevelChunk} {@link LongOpenHashSet} of packed {@link BlockPos}
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

    /**
     * Client-side fallback for anchor queries issued by chunk render paths that
     * receive a non-{@link World} {@link net.minecraft.world.BlockGetter}
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

    @FunctionalInterface
    public interface ClientPlacementDyFactLookup {
        PlacementDyFact lookup(BlockGetter view, BlockPos pos);
    }

    /** Overlay-aware client lookup used by every client frozen-dy consumer. */
    public static ClientPlacementDyFactLookup clientEffectivePlacementDyLookup = null;
    /** Non-overlaying client backing lookup used by CAS and effective-read fallback. */
    public static ClientPlacementDyFactLookup clientBackingPlacementDyLookup = null;

    private static long c3NextPublicationProbeToken = 1L;
    private static final Map<Long, Map<Long, Integer>> C3_PUBLICATION_PROBES = new HashMap<>();

    private static final Identifier ANCHOR_ID = Identifier.fromNamespaceAndPath(Slabbed.MOD_ID, "slab_anchors");
    private static final Identifier FROZEN_FLAT_ID = Identifier.fromNamespaceAndPath(Slabbed.MOD_ID, "frozen_flat");
    private static final Identifier LOWERED_SLAB_CARRIER_ID =
            Identifier.fromNamespaceAndPath(Slabbed.MOD_ID, "lowered_slab_carriers");
    private static final Identifier COMPOUND_FULL_BLOCK_ANCHOR_ID =
            Identifier.fromNamespaceAndPath(Slabbed.MOD_ID, "compound_full_block_anchors");
    private static final Identifier COMPOUND_VISIBLE_SIDE_LOWER_SLAB_ID =
            Identifier.fromNamespaceAndPath(Slabbed.MOD_ID, "compound_visible_side_lower_slabs");
    private static final Identifier COMPOUND_VISIBLE_SIDE_UPPER_SLAB_ID =
            Identifier.fromNamespaceAndPath(Slabbed.MOD_ID, "compound_visible_side_upper_slabs");
    private static final Identifier COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_ID =
            Identifier.fromNamespaceAndPath(Slabbed.MOD_ID, "compound_visible_side_double_slabs");
    private static final Identifier COMPOUND_VISIBLE_OWNER_TOP_SLAB_ID =
            Identifier.fromNamespaceAndPath(Slabbed.MOD_ID, "compound_visible_owner_top_slabs");
    private static final Identifier PLACEMENT_DY_ID =
            Identifier.fromNamespaceAndPath(Slabbed.MOD_ID, "placement_dy");

    /**
     * Codec for the anchor set.  Backed by {@code long[]} so the NBT representation is
     * a {@code LongArrayTag}, the most compact form available.
     */
    private static final Codec<LongOpenHashSet> SET_CODEC = Codec.LONG_STREAM.xmap(
            stream -> new LongOpenHashSet(stream.toArray()),
            set -> {
            java.util.stream.LongStream.Builder builder = java.util.stream.LongStream.builder();
            for (var iterator = set.iterator(); iterator.hasNext();) {
                builder.add(iterator.nextLong());
            }
            return builder.build();
        }
    );

    /**
     * Packet codec for client sync. {@link AttachmentSyncPredicate#all()} is used at
     * registration so anchors travel with the chunk packet automatically.
     */
    private static final StreamCodec<RegistryFriendlyByteBuf, LongOpenHashSet> PACKET_CODEC =
            ChunkPositionSetPacketCodec.INSTANCE;

    public static final AttachmentType<LongOpenHashSet> ANCHOR_TYPE =
            AttachmentRegistry.<LongOpenHashSet>create(ANCHOR_ID, builder -> builder
                    .persistent(SET_CODEC)
                    .syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())
            );
    /**
     * FREEZE-ON-PLACE flat marker: a structural piece (full block / slab) placed at dy=0 is
     * recorded here so its flat height locks — support placed under or beside it later can no
     * longer pull it down. The "never autonomously moves" companion of {@link #ANCHOR_TYPE}
     * (which locks the lowered case). Read as dy=0 by {@code getYOffsetInner}; cleared when the
     * piece is broken.
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

    // ── FROZEN-DY value store (LAW.md restoration, Step 0) ────────────────────────────────────────
    // The law: a block's height is decided ONCE at placement and STAYS. Unlike the presence flags
    // above, this stores the actual height (a double) per position, so a read returns the exact value
    // the player aimed at — never a value derived afresh from the current neighbours. Synced to clients
    // exactly like the flags, so the frozen height is what renders. Flag-gated (default off) for Step 0.
    private record DyEntry(long pos, double dy) {
    }

    /** Presence and raw bits are one indivisible C3 authority fact. */
    public record PlacementDyFact(boolean present, long rawBits) {
        /**
         * PERF (render-path fix-round F1): {@code absent()} is the answer for every cell in ordinary
         * terrain, and the chunk mesher asks it ~13x per non-air block per section compile. A fresh
         * record per ask was pure garbage on the mesh worker threads. The value is a constant, the
         * record is immutable, and its {@code equals} is value-based, so one shared instance is
         * indistinguishable from a fresh one at every call site.
         */
        private static final PlacementDyFact ABSENT = new PlacementDyFact(false, 0L);

        public static PlacementDyFact absent() {
            return ABSENT;
        }

        public static PlacementDyFact present(double value) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("placement dy must be finite");
            }
            return new PlacementDyFact(true, Double.doubleToRawLongBits(value));
        }

        public double valueOrNaN() {
            return present ? Double.longBitsToDouble(rawBits) : Double.NaN;
        }
    }

    private static final Codec<DyEntry> DY_ENTRY_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.LONG.fieldOf("p").forGetter(DyEntry::pos),
            Codec.DOUBLE.fieldOf("d").forGetter(DyEntry::dy)
    ).apply(inst, DyEntry::new));

    private static Long2DoubleOpenHashMap newDyMap() {
        Long2DoubleOpenHashMap m = new Long2DoubleOpenHashMap();
        m.defaultReturnValue(Double.NaN);
        return m;
    }

    private static final Codec<Long2DoubleOpenHashMap> DY_MAP_CODEC = DY_ENTRY_CODEC.listOf().xmap(
            list -> {
                Long2DoubleOpenHashMap m = newDyMap();
                for (DyEntry e : list) {
                    m.put(e.pos(), e.dy());
                }
                return m;
            },
            map -> {
                java.util.List<DyEntry> l = new java.util.ArrayList<>(map.size());
                for (var e : map.long2DoubleEntrySet()) {
                    l.add(new DyEntry(e.getLongKey(), e.getDoubleValue()));
                }
                return l;
            }
    );

    private static final StreamCodec<RegistryFriendlyByteBuf, Long2DoubleOpenHashMap> DY_MAP_PACKET_CODEC =
            StreamCodec.of(
                    (buf, map) -> {
                        buf.writeVarInt(map.size());
                        for (var e : map.long2DoubleEntrySet()) {
                            buf.writeLong(e.getLongKey());
                            buf.writeDouble(e.getDoubleValue());
                        }
                    },
                    buf -> {
                        int n = buf.readVarInt();
                        Long2DoubleOpenHashMap m = newDyMap();
                        for (int i = 0; i < n; i++) {
                            long k = buf.readLong();
                            double v = buf.readDouble();
                            m.put(k, v);
                        }
                        return m;
                    }
            );

    public static final AttachmentType<Long2DoubleOpenHashMap> PLACEMENT_DY_TYPE =
            AttachmentRegistry.<Long2DoubleOpenHashMap>create(PLACEMENT_DY_ID, builder -> builder
                    .persistent(DY_MAP_CODEC)
                    .syncWith(DY_MAP_PACKET_CODEC, AttachmentSyncPredicate.all())
            );

    /**
     * Step 0 master switch for the FROZEN-DY value store (LAW.md restoration): when true, reads route
     * through the stored placement height instead of the live read-lanes.
     *
     * <p><b>Default ON as of C1</b> (design ruling D5 — {@code docs/design/GOES-UNIFIED-LANDING-RULE.md}
     * §4.4 row C1 and §8). The system property still forces it either way:
     * {@code -Dslabbed.frozenDy=false} is the escape hatch. Legacy worlds carry no stored value for
     * blocks placed before the flip; those cells resolve to stable flat {@code 0.0} with no recovery
     * from live neighbour geometry — there is no retro-migration (design D5).
     */
    public static boolean FROZEN_DY_ENABLED =
            Boolean.parseBoolean(System.getProperty("slabbed.frozenDy", "true"));

    /**
     * Records the height this placement landed at, so every later read returns it verbatim. Server-side
     * only.
     *
     * <p><b>True ordering (design §0/§3):</b> invoked from {@code BlockOnPlacedAnchorMixin} at
     * {@code Block.setPlacedBy} HEAD, which runs <em>before</em> the compound-visible markers authored
     * at {@code BlockItemPlacementIntentMixin} place-RETURN. The captured value is therefore whatever
     * the live read-lanes ({@link SlabSupport#getYOffset}) report at {@code setPlacedBy} time — it is
     * NOT read back after those markers exist. (The prior javadoc claimed the reverse — "AFTER the
     * existing markers are written" — and was proven false; see design §0.)
     *
     * <p>Stores every non-air placement, height 0 included, so a flat placement stays flat even when a
     * lowered neighbour appears later.
     */
    public static void capturePlacementDy(Level world, BlockPos pos, BlockState state) {
        if (world == null || world.isClientSide() || pos == null || state == null || state.isAir()) {
            return;
        }
        double dy = SlabSupport.getYOffset(world, pos, state);
        writePlacementDyInternal(world, pos, dy);
    }

    /**
     * GOES landing resolver (design C2, {@code docs/design/GOES-UNIFIED-LANDING-RULE.md} §3): writes an
     * explicit landing height at {@code pos}, computed once at placement from the aim by
     * {@link com.slabbed.placement.LandingResolver}, verbatim — the single-writer capture point that
     * OVERWRITES the earlier live-lane {@link #capturePlacementDy} write (the double-capture window
     * disclosed in design §3 / review §3: the {@code setPlacedBy}-HEAD capture still runs first for
     * slabs in C2, this RETURN write replaces it; the old call is removed in C3). Server-side only.
     */
    public static void writePlacementDy(Level world, BlockPos pos, double dy) {
        if (world == null || world.isClientSide() || pos == null || Double.isNaN(dy)) {
            return;
        }
        writePlacementDyInternal(world, pos, dy);
    }

    /** Writes a server-authoritative C3 batch with at most one attachment publication per chunk. */
    public static int writePlacementDyBatch(Level world, Map<BlockPos, Long> rawBitsByPos) {
        if (world == null || world.isClientSide() || rawBitsByPos == null || rawBitsByPos.isEmpty()) {
            return 0;
        }
        LinkedHashMap<BlockPos, PlacementDyFact> facts = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, Long> entry : rawBitsByPos.entrySet()) {
            double value = Double.longBitsToDouble(entry.getValue());
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("non-finite placement dy batch value");
            }
            facts.put(entry.getKey().immutable(), new PlacementDyFact(true, entry.getValue()));
        }
        return writePlacementDyFactsInternal(world, facts);
    }

    /** Applies only validated author-only correction facts on a client. Prediction never calls this. */
    public static int applyClientAuthoritativePlacementDy(
            Level world,
            Map<BlockPos, PlacementDyFact> facts
    ) {
        if (world == null || !world.isClientSide() || facts == null || facts.isEmpty()) {
            return 0;
        }
        return writePlacementDyFactsInternal(world, facts);
    }

    private static void writePlacementDyInternal(Level world, BlockPos pos, double dy) {
        writePlacementDyFactsInternal(world, Map.of(pos.immutable(), PlacementDyFact.present(dy)));
    }

    private static int writePlacementDyFactsInternal(Level world, Map<BlockPos, PlacementDyFact> facts) {
        IdentityHashMap<LevelChunk, Long2DoubleOpenHashMap> copies = new IdentityHashMap<>();
        IdentityHashMap<LevelChunk, Boolean> changedChunks = new IdentityHashMap<>();
        int writes = 0;
        for (Map.Entry<BlockPos, PlacementDyFact> entry : facts.entrySet()) {
            BlockPos pos = entry.getKey();
            PlacementDyFact desired = entry.getValue();
            if (pos == null || desired == null) {
                continue;
            }
            LevelChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                continue;
            }
            Long2DoubleOpenHashMap map = copies.computeIfAbsent(chunk, ignored -> {
                Long2DoubleOpenHashMap existing = chunk.getAttached(PLACEMENT_DY_TYPE);
                Long2DoubleOpenHashMap copy = existing == null
                        ? newDyMap()
                        : new Long2DoubleOpenHashMap(existing);
                copy.defaultReturnValue(Double.NaN);
                return copy;
            });
            long key = pos.asLong();
            PlacementDyFact current = map.containsKey(key)
                    ? new PlacementDyFact(true, Double.doubleToRawLongBits(map.get(key)))
                    : PlacementDyFact.absent();
            if (current.equals(desired)) {
                continue;
            }
            if (desired.present()) {
                double value = Double.longBitsToDouble(desired.rawBits());
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("non-finite authoritative placement dy");
                }
                map.put(key, value);
            } else {
                map.remove(key);
            }
            changedChunks.put(chunk, Boolean.TRUE);
            writes++;
        }
        for (LevelChunk chunk : changedChunks.keySet()) {
            chunk.setAttached(PLACEMENT_DY_TYPE, copies.get(chunk));
            recordC3PublicationForTests(chunk);
        }
        return writes;
    }

    public static synchronized long beginC3PublicationProbeForTests(BlockPos... cells) {
        Map<Long, Integer> publicationsByChunk = new HashMap<>();
        if (cells != null) {
            for (BlockPos cell : cells) {
                if (cell != null) {
                    publicationsByChunk.put(
                            net.minecraft.world.level.ChunkPos.pack(cell.getX() >> 4, cell.getZ() >> 4), 0);
                }
            }
        }
        if (publicationsByChunk.isEmpty()) {
            throw new IllegalArgumentException("C3 publication probe requires at least one cell");
        }
        long token = c3NextPublicationProbeToken++;
        C3_PUBLICATION_PROBES.put(token, publicationsByChunk);
        return token;
    }

    public static synchronized int c3PublicationCountForTests(long token) {
        Map<Long, Integer> publicationsByChunk = C3_PUBLICATION_PROBES.get(token);
        return publicationsByChunk == null
                ? 0
                : publicationsByChunk.values().stream().mapToInt(Integer::intValue).sum();
    }

    public static synchronized int c3PublicationCountForTests(long token, int chunkX, int chunkZ) {
        Map<Long, Integer> publicationsByChunk = C3_PUBLICATION_PROBES.get(token);
        return publicationsByChunk == null ? 0 : publicationsByChunk.getOrDefault(
                net.minecraft.world.level.ChunkPos.pack(chunkX, chunkZ), 0);
    }

    public static synchronized void stopC3PublicationProbeForTests(long token) {
        C3_PUBLICATION_PROBES.remove(token);
    }

    private static synchronized void recordC3PublicationForTests(LevelChunk chunk) {
        if (C3_PUBLICATION_PROBES.isEmpty()) {
            return;
        }
        long key = chunk.getPos().pack();
        for (Map<Long, Integer> publicationsByChunk : C3_PUBLICATION_PROBES.values()) {
            if (publicationsByChunk.containsKey(key)) {
                publicationsByChunk.merge(key, 1, Integer::sum);
            }
        }
    }

    /**
     * PERF-gated column probe for the A-1 store-aware air-gap crossings (raycast deep probe +
     * collision walk): is there a stored owner in the vertical window {@code base.above(fromK)} ..
     * {@code base.above(maxK)} whose stored dy overflows down into {@code base}'s cell
     * ({@code storedDy < (1 - k) - eps})?
     *
     * <p>PERF (fix-round MAJOR-2, the shipped-lag-twice class): a naive per-cell
     * {@link #storedPlacementDy} loop re-does getChunk + getAttached + map lookup up to 15× for ONE
     * x,z column, on the entity-collision and per-frame ray paths, under the now-default-ON flag.
     * This helper resolves the chunk and its PLACEMENT_DY map ONCE (the whole vertical window shares
     * one chunk — same x,z), then iterates y with plain O(1) map hits — and early-outs immediately
     * when the chunk carries no PLACEMENT_DY attachment at all (the overwhelming common case:
     * ordinary terrain pays one getChunk + one getAttached total, zero allocation).
     *
     * <p>Non-{@link Level} views (client render regions) fall back to the per-pos
     * {@link #storedPlacementDy} client lookup — that path has no chunk handle to hoist.
     */
    public static boolean anyStoredOwnerOverflowsInto(BlockGetter world, BlockPos base, int fromK, int maxK) {
        if (world == null || base == null || fromK > maxK) {
            return false;
        }
        if (world instanceof Level w && !w.isClientSide()) {
            LevelChunk chunk = w.getChunk(base.getX() >> 4, base.getZ() >> 4);
            if (chunk == null) {
                return false;
            }
            Long2DoubleOpenHashMap map = chunk.getAttached(PLACEMENT_DY_TYPE);
            if (map == null || map.isEmpty()) {
                return false; // common case: no frozen entries anywhere in this chunk
            }
            for (int k = fromK; k <= maxK; k++) {
                long key = BlockPos.asLong(base.getX(), base.getY() + k, base.getZ());
                if (map.containsKey(key) && map.get(key) < (1.0 - k) - 1.0e-6d) {
                    return true;
                }
            }
            return false;
        }
        for (int k = fromK; k <= maxK; k++) {
            double sd = storedPlacementDy(world, base.above(k));
            if (!Double.isNaN(sd) && sd < (1.0 - k) - 1.0e-6d) {
                return true;
            }
        }
        return false;
    }

    /** The frozen placement height at {@code pos}, or {@link Double#NaN} if none was stored. */
    public static double storedPlacementDy(BlockGetter world, BlockPos pos) {
        if (pos == null) {
            return Double.NaN;
        }
        boolean clientView = !(world instanceof Level) || ((Level) world).isClientSide();
        if (clientView && clientEffectivePlacementDyLookup != null) {
            PlacementDyFact effective = clientEffectivePlacementDyLookup.lookup(world, pos);
            if (effective != null) {
                return effective.valueOrNaN();
            }
        }
        return rawPlacementDyFact(world, pos).valueOrNaN();
    }

    /** Direct authoritative backing read. This never invokes the overlay/effective lookup. */
    public static PlacementDyFact rawPlacementDyFact(BlockGetter world, BlockPos pos) {
        if (pos == null) {
            return PlacementDyFact.absent();
        }
        if (!(world instanceof Level w)) {
            if (clientBackingPlacementDyLookup != null) {
                PlacementDyFact fact = clientBackingPlacementDyLookup.lookup(world, pos);
                return fact == null ? PlacementDyFact.absent() : fact;
            }
            return PlacementDyFact.absent();
        }
        LevelChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return PlacementDyFact.absent();
        }
        Long2DoubleOpenHashMap map = chunk.getAttached(PLACEMENT_DY_TYPE);
        long key = pos.asLong();
        return (map != null && map.containsKey(key))
                ? new PlacementDyFact(true, Double.doubleToRawLongBits(map.get(key)))
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
                || PLACEMENT_DY_TYPE == null) {
            throw new IllegalStateException("SlabAnchorAttachment failed to register");
        }
    }

    // ── server-side mutation ──────────────────────────────────────────

    /**
     * Records an anchor at {@code pos}. Server-side only; no-op on client world or
     * if {@code pos} does not qualify under {@link #qualifiesForAnchor}.
     */
    public static void addAnchor(Level world, BlockPos pos, BlockState state) {
        if (world == null || world.isClientSide()) {
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

    /**
     * FREEZE-ON-PLACE: locks a piece's height at the moment it is placed so it NEVER autonomously
     * moves afterwards. the maintainer's law — "a placed block must stay in that spot and not autonomously
     * pop." Server-side only; called from {@code BlockOnPlacedAnchorMixin} (Block#setPlacedBy).
     *
     * <p>If the piece is placed LOWERED (dy &lt; 0) it records an anchor (read as the lowered dy by
     * {@code getYOffsetInner}), so breaking a neighbour / source can no longer pop it back up. If it
     * is placed FLAT (dy ≈ 0) and is a STRUCTURAL piece (ordinary full block or slab) it records a
     * {@link #FROZEN_FLAT_TYPE} marker, so a slab / lowered carrier placed under or beside it later
     * can no longer pull it down (the exact down-pop the maintainer reported). No-op for decorative followers
     * (lanterns / torches / hangers / signs) so they keep tracking their supports, and for pieces
     * already anchored or frozen. Natural / setBlockState blocks never call onPlaced, so terrain
     * stays fully geometric.
     */
    /**
     * WYSIWYG click follow (the maintainer's law): set by the placement-intent mixin when the player places a
     * SLAB by clicking a lowered block/slab face whose visible surface should own the new slab height.
     * The placement must then land on that lowered surface (where the crosshair clicked) instead of
     * freezing flat at grid height or merging into the underside. Stores the predicted placement cell;
     * consumed (and cleared) by {@link #freezeLoweredOnPlace}. Thread-scoped to the placement call; the
     * mixin also clears it on useOn RETURN so a cancelled/mismatched placement never leaks to the next one.
     */
    private static final ThreadLocal<BlockPos> WYSIWYG_FOLLOW_CLICKED_LOWERED_FACE = new ThreadLocal<>();

    /** Mark that the slab about to be placed at {@code placedPos} was placed by clicking a lowered block's side face. */
    public static void markWysiwygFollowClickedLoweredFace(BlockPos placedPos) {
        WYSIWYG_FOLLOW_CLICKED_LOWERED_FACE.set(placedPos == null ? null : placedPos.immutable());
    }

    /** Clear the WYSIWYG side-click follow marker (call on useOn RETURN; safe to call when unset). */
    public static void clearWysiwygFollowClickedLoweredFace() {
        WYSIWYG_FOLLOW_CLICKED_LOWERED_FACE.remove();
    }

    /**
     * F10 (audit STATE_DEFENSE_DIVERGENCE_2026-07-07): the 2b upper-visible hop retargets the
     * placement ONE CELL UP inside the same useOn — carry the follow marker along or it is orphaned
     * and the consume-side (deliberately strict-equals) misses: the hopped slab then freeze-flats at
     * grid height 0.5 above the aimed lowered side surface. Only load-bearing when the post-hop cell
     * has solid flush ground below (over air, RC2-C anchors the cantilever regardless). Thread-scoped
     * like the marker itself; no-op when no marker is set.
     */
    public static void liftWysiwygFollowMarkerForUpperVisibleHop() {
        BlockPos marked = WYSIWYG_FOLLOW_CLICKED_LOWERED_FACE.get();
        if (marked != null) {
            WYSIWYG_FOLLOW_CLICKED_LOWERED_FACE.set(marked.above());
        }
    }

    private static boolean consumeWysiwygFollowClickedLoweredFace(BlockPos pos) {
        BlockPos marked = WYSIWYG_FOLLOW_CLICKED_LOWERED_FACE.get();
        if (marked != null && marked.equals(pos)) {
            WYSIWYG_FOLLOW_CLICKED_LOWERED_FACE.remove();
            return true;
        }
        return false;
    }

    public static void freezeLoweredOnPlace(Level world, BlockPos pos, BlockState state) {
        // F5b (deep-sweep finding 1): the WRITER is fluid-blind like the reads — a slab placed INTO
        // water on a lowered support read -0.5 but was never anchored, so it popped when the support
        // broke while its dry twin held (never-pop must not depend on the water it was placed into).
        if (world == null || world.isClientSide() || pos == null || state == null || state.isAir()) {
            return;
        }
        if (isAnchored(world, pos) || isFrozenFlat(world, pos)) {
            return;
        }
        // WYSIWYG (the maintainer's law): a SLAB placed by clicking a lowered block's SIDE face must FOLLOW that
        // lowered surface — it lands where the crosshair clicked, NOT frozen flat at grid height. Anchor it
        // lowered so it both lands correctly AND holds its position if the neighbour is later removed
        // (NEVER-POP-down preserved). This overrides the side-inherited freeze-flat rail below, which only
        // applies when the slab was placed on its OWN flush ground WITHOUT clicking the lowered block.
        if (state.getBlock() instanceof SlabBlock && consumeWysiwygFollowClickedLoweredFace(pos)) {
            addAnchorUnchecked(world, pos);
            return;
        }
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy < -1.0e-6) {
            // A placed SLAB that reads lowered ONLY because of a lowered SIDE neighbour (slab-lane
            // inheritance), with no genuine lowered support directly below it, must NOT snap down to
            // that level — the maintainer's NEVER-POP law: a placed block stays where it was put. Freeze it
            // FLAT instead of anchoring -0.5 (the reported "snapped slab"). A slab lowered by genuine
            // support BELOW still follows down (anchored -0.5 in the branch below).
            if (SlabSupport.slabLoweringIsSideInheritedOnly(world, pos, state)) {
                addToAttachment(world, pos, FROZEN_FLAT_TYPE, "frozen_flat");
                return;
            }
            addAnchorUnchecked(world, pos);
            // A CONNECTING block (fence/wall) placed at COMPOUND -1.0 depth — stacked on a deeper-lowered
            // support (a -1.0 fence, or a fence on a lowered bottom slab) — STORES that depth via the
            // compound marker so it HOLDS -1.0 when the support is later removed. The regular anchor records
            // only PRESENCE, so without this the post pops UP from its placed -1.0 to the -0.5 floor the
            // instant the support is broken (the maintainer's "break-pop"). getYOffsetInner's compound sidecar then
            // preserves -1.0 across support removal (returns -1.0 for a non-slab below), exactly like a full
            // block; removeAnchor clears it when the post itself is broken (no stale marker). addAnchorUnchecked's
            // own qualifies-gate only covers full blocks, hence this connecting-block extension.
            if (dy < -0.5d - 1.0e-6d && SlabSupport.isBeta35FenceWallVariantContactObject(state)) {
                addToAttachment(world, pos, COMPOUND_FULL_BLOCK_ANCHOR_TYPE, "compound_full_block_anchor");
            }
            return;
        }
        // dy ≈ 0: lock the FLAT height of a STRUCTURAL piece (ordinary full block or slab) so a slab
        // / lowered carrier placed under or beside it later can no longer pull it down. Gated to
        // structural pieces so decorative followers stay geometric; non-structural and natural
        // (setBlockState, which never calls onPlaced) pieces are untouched.
        boolean structural = isOrdinaryFullBlockAnchorCandidate(world, pos, state)
                || state.getBlock() instanceof SlabBlock
                // A flat-placed BLOCK ENTITY (hopper / chest / furnace / barrel resting on flush ground)
                // must ALSO freeze-flat so a slab or lowered carrier shoved under it later cannot pull it
                // down to -0.5 — the reported "hopper places too high, then snaps down when a block is
                // placed underneath". Block entities are rejected by isOrdinaryFullBlockAnchorCandidate
                // (which excludes EntityBlock), yet isSlabSitCandidate lowers every EntityBlock onto a
                // slab, so a flat hopper's height came ONLY from a live column walk that toggled 0<->-0.5
                // whenever the cell below changed — that toggle IS the snap. Ceiling-hung block entities
                // (hanging signs) are excluded: they hang from ABOVE and must keep following their support
                // (getYOffsetInner also dispatches them first, before any anchor/frozen read). The LOWERED
                // block-entity case is already covered structurally by the addAnchorUnchecked branch above
                // (dy < 0), exactly like the decorative-object case — see DecorativeObjectSupportAnchorTest.
                || (state.getBlock() instanceof EntityBlock
                        && !SlabSupport.isAlwaysCeilingHungDecoration(state))
                // D4 port (donor: 1.21.11 isConnectingStructural admission; audit
                // STATE_DEFENSE_DIVERGENCE_2026-07-07): fences/walls/panes/gates placed FLAT freeze
                // like every other structural piece — without this they stayed LIVE forever and sank
                // -0.5 whenever a slab was shoved under them later (down-pop while editing beneath
                // existing builds). Lowered-placed connectors still anchor via the dy<0 branch above.
                || isConnectingStructural(state);
        if (structural) {
            addToAttachment(world, pos, FROZEN_FLAT_TYPE, "frozen_flat");
        }
    }

    /**
     * Returns true if {@code pos} carries a freeze-on-place FLAT marker — a structural piece whose
     * height was locked at 0 when placed. Safe on server and client (client mirror via
     * {@link #clientFrozenFlatLookup}); false for non-{@link Level} views.
     */
    public static boolean isFrozenFlat(BlockGetter world, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (!(world instanceof Level w)) {
            return clientFrozenFlatLookup != null && clientFrozenFlatLookup.test(pos);
        }
        LevelChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet set = chunk.getAttached(FROZEN_FLAT_TYPE);
        return set != null && set.contains(pos.asLong());
    }

    public static void addSideAdjacentLoweredFullAnchor(
            Level world,
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
            Level world,
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

    private static void addAnchorUnchecked(Level world, BlockPos pos) {
        boolean added = addToAttachment(world, pos, ANCHOR_TYPE, "anchor");
        // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
        // Beta4 sidecar: if the position currently satisfies the compound full-block
        // condition (anchored ordinary full block above a lowered bottom slab carrier),
        // also record the authored dy=-1.0 lane so it survives source slab removal.
        BlockState state = world.getBlockState(pos);
        BlockPos belowPos = pos.below();
        if (qualifiesForCompoundFullBlockAnchor(world, pos, state)
                || qualifiesForTopOfCompoundFullAnchor(world, pos, state, belowPos, world.getBlockState(belowPos))) {
            addToAttachment(world, pos, COMPOUND_FULL_BLOCK_ANCHOR_TYPE, "compound_full_block_anchor");
        }
    }

    /**
     * Records a beta4 compound full-block anchor at {@code pos}. Server-side only.
     * Idempotent; no-op if {@code pos} does not satisfy
     * {@link #qualifiesForCompoundFullBlockAnchor}.
     */
    public static void addCompoundFullBlockAnchor(Level world, BlockPos pos, BlockState state) {
        if (world == null || world.isClientSide()) {
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
    public static void removeCompoundFullBlockAnchor(Level world, BlockPos pos) {
        removeFromAttachment(world, pos, COMPOUND_FULL_BLOCK_ANCHOR_TYPE, "compound_full_block_anchor");
    }

    public static void addCompoundVisibleSideLowerSlab(
            Level world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (world == null || world.isClientSide()) {
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
            world.neighborShapeChanged(Direction.DOWN, pos.above(), pos, state, Block.UPDATE_ALL, 512);
        }
    }

    public static void addCompoundVisibleSideUpperSlab(
            Level world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (world == null || world.isClientSide()) {
            return;
        }
        if (!qualifiesForCompoundVisibleSideUpperSlab(world, pos, state, sourcePos, sourceState)) {
            return;
        }
        addToAttachment(world, pos, COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE,
                "compound_visible_side_upper_slab");
    }

    public static void addCompoundVisibleSideDoubleSlab(
            Level world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (world == null || world.isClientSide()) {
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
            Level world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        if (world == null || world.isClientSide()) {
            return;
        }
        if (!qualifiesForCompoundVisibleOwnerTopSlab(world, pos, state, sourcePos, sourceState)) {
            return;
        }
        boolean added = addToAttachment(world, pos, COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE,
                "compound_visible_owner_top_slab");
        if (added) {
            world.neighborShapeChanged(Direction.DOWN, pos.above(), pos, state, Block.UPDATE_ALL, 512);
        }
    }

    public static void updatePersistentLoweredSlabCarrier(Level world, BlockPos pos, BlockState state) {
        updatePersistentLoweredSlabCarrier(world, pos, state, false);
    }

    public static void updateClientPredictedPersistentLoweredSlabCarrier(Level world, BlockPos pos, BlockState state) {
        if (world == null || !world.isClientSide()) {
            return;
        }
        updatePersistentLoweredSlabCarrier(world, pos, state, true);
    }

    private static void updatePersistentLoweredSlabCarrier(
            Level world,
            BlockPos pos,
            BlockState state,
            boolean allowClient
    ) {
        if (world == null || (world.isClientSide() && !allowClient)) {
            return;
        }
        // F1 write-side belt: never WRITE a carrier marker onto a frozen-flat slab (and since the
        // else-branch below clears non-qualifying slabs, an existing contradictory marker self-heals
        // on the next update pass).
        boolean qualifies = !isFrozenFlat(world, pos)
                && qualifiesForPersistentLoweredSlabCarrier(world, pos, state);
        if (TRACE) {
            Slabbed.LOGGER.info("[ANCHOR] lowered slab carrier update side={} pos={} state={} qualifies={}",
                    world.isClientSide() ? "CLIENT" : "SERVER", pos.toShortString(), state, qualifies);
        }
        if (qualifies) {
            addToAttachment(world, pos, LOWERED_SLAB_CARRIER_TYPE, "lowered_slab_carrier");
        } else if (state != null && state.getBlock() instanceof SlabBlock) {
            removeFromAttachment(world, pos, LOWERED_SLAB_CARRIER_TYPE, "lowered_slab_carrier");
        }
        logCompoundVisibleRenderTraceSupportUpdate(world, pos, state, qualifies);
    }

    private static boolean addToAttachment(
            Level world,
            BlockPos pos,
            AttachmentType<LongOpenHashSet> type,
            String label
    ) {
        LevelChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] {} add reject pos={} reason=chunk_null", label, pos.toShortString());
            }
            return false;
        }
        LongOpenHashSet existing = chunk.getAttached(type);
        LongOpenHashSet set = existing == null ? new LongOpenHashSet() : new LongOpenHashSet(existing);
        BlockState stateBefore = null; // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
        if (set.add(pos.asLong())) {
            // setAttached triggers persistence + auto-sync for synced attachments.
            chunk.setAttached(type, set);
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] {} add success pos={} chunk={} setSize={}",
                        label, pos.toShortString(), chunk.getPos(), set.size());
            }
            logCompoundVisibleRenderTraceMarkerSet(world, pos, type, label, "add", true);
            // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
            return true;
        }
        return false;
    }

    /**
     * D1 port (donor: 1.21.11 {@code 78ec0ac4}, audit STATE_DEFENSE_DIVERGENCE_2026-07-07): true when
     * an in-place block-KIND replacement should KEEP the height-lock — either both occupants belong
     * to the flower-pot state family, or the new occupant is itself a lock-eligible block (grass_block
     * → dirt, log → stripped, copper oxidation). Air, fluids, and other non-lock kinds still clear.
     * Mirrors 26.2's own freeze-flat structural gate (ordinary full block / lockable block entity)
     * plus the connecting-structural family.
     */
    public static boolean replacementPreservesAnchor(
            BlockGetter world,
            BlockPos pos,
            BlockState oldState,
            BlockState newState
    ) {
        if (newState == null || newState.isAir() || !newState.getFluidState().isEmpty()) {
            return false;
        }
        boolean flowerPotStateTransition = oldState != null
                && oldState.getBlock() instanceof FlowerPotBlock
                && newState.getBlock() instanceof FlowerPotBlock;
        return flowerPotStateTransition
                || isOrdinaryFullBlockAnchorCandidate(world, pos, newState)
                || (newState.getBlock() instanceof EntityBlock
                        && !SlabSupport.isAlwaysCeilingHungDecoration(newState))
                || isConnectingStructural(newState);
    }

    /** Fence / wall / pane / gate — connecting blocks that must be height-locked like solids
     *  (donor: 1.21.11 {@code isConnectingStructural}; also the D4 seed). */
    public static boolean isConnectingStructural(BlockState state) {
        var b = state.getBlock();
        return b instanceof net.minecraft.world.level.block.FenceBlock
                || b instanceof net.minecraft.world.level.block.WallBlock
                || b instanceof net.minecraft.world.level.block.IronBarsBlock
                || b instanceof net.minecraft.world.level.block.FenceGateBlock;
    }

    /**
     * Clears any anchor at {@code pos}. Server-side only.
     */
    public static void removeAnchor(Level world, BlockPos pos) {
        boolean removed = removeFromAttachment(world, pos, ANCHOR_TYPE, "anchor");
        // Freeze-on-place flat marker clears when the piece itself is broken/replaced
        // (onStateReplaced calls removeAnchor for every removal), so a fresh placement in
        // the same spot re-evaluates from scratch.
        removeFromAttachment(world, pos, FROZEN_FLAT_TYPE, "frozen_flat");
        // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
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
        // F2 (haunted-cells audit, STATE_DEFENSE_DIVERGENCE_2026-07-07): the carrier marker must die
        // with its slab like every other attachment — it was the ONE type this method didn't clear
        // (and removePersistentLoweredSlabCarrier had zero callers), so markers outlived break/replace
        // cycles and re-lowered fresh slabs placed at old lane positions forever.
        removeFromAttachment(world, pos, LOWERED_SLAB_CARRIER_TYPE, "lowered_slab_carrier");
        // FROZEN-DY (Step 0): the stored placement height dies with the block, so a fresh placement in
        // the same cell captures its own aim from scratch.
        if (world != null && !world.isClientSide()) {
            LevelChunk dyChunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (dyChunk != null) {
                Long2DoubleOpenHashMap dyMap = dyChunk.getAttached(PLACEMENT_DY_TYPE);
                if (dyMap != null && dyMap.containsKey(pos.asLong())) {
                    Long2DoubleOpenHashMap copy = new Long2DoubleOpenHashMap(dyMap);
                    copy.defaultReturnValue(Double.NaN);
                    copy.remove(pos.asLong());
                    dyChunk.setAttached(PLACEMENT_DY_TYPE, copy);
                }
            }
        }
    }

    /**
     * Raw persistence-only probe for exact cleanup/evidence code. Unlike the gameplay carrier reads,
     * this never infers support from neighboring geometry; it reports only entries actually stored in
     * Slabbed chunk attachments at {@code pos}.
     */
    public static boolean hasStoredAttachmentEvidence(ServerLevel world, BlockPos pos) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(pos, "pos");
        LevelChunk chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            throw new IllegalStateException(
                    "refusing raw attachment probe for unloaded chunk at " + pos.toShortString());
        }
        long key = pos.asLong();
        Long2DoubleOpenHashMap dyMap = chunk.getAttached(PLACEMENT_DY_TYPE);
        return contains(chunk.getAttached(ANCHOR_TYPE), key)
                || contains(chunk.getAttached(FROZEN_FLAT_TYPE), key)
                || contains(chunk.getAttached(COMPOUND_FULL_BLOCK_ANCHOR_TYPE), key)
                || contains(chunk.getAttached(COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE), key)
                || contains(chunk.getAttached(COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE), key)
                || contains(chunk.getAttached(COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE), key)
                || contains(chunk.getAttached(COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE), key)
                || contains(chunk.getAttached(LOWERED_SLAB_CARRIER_TYPE), key)
                || dyMap != null && dyMap.containsKey(key);
    }

    private static boolean contains(LongOpenHashSet set, long key) {
        return set != null && set.contains(key);
    }

    public static void removePersistentLoweredSlabCarrier(Level world, BlockPos pos) {
        removeFromAttachment(world, pos, LOWERED_SLAB_CARRIER_TYPE, "lowered_slab_carrier");
    }

    private static boolean removeFromAttachment(
            Level world,
            BlockPos pos,
            AttachmentType<LongOpenHashSet> type,
            String label
    ) {
        if (world == null || world.isClientSide()) {
            return false;
        }
        LevelChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
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
            // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
        }
        return removed;
    }

    public static boolean beta4CompoundVisibleRenderTraceEnabled() {
        return Boolean.getBoolean(BETA4_COMPOUND_VISIBLE_RENDER_TRACE_PROPERTY);
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
            Level world,
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
                "[SLABBED_BETA4_COMPOUND_VISIBLE_RENDER_TRACE_MARKER_SET] action={} pos={} marker={} label={} serverMarker={} clientMarker=n/a modelViewType=Level slabSupportDy={} clientDy=n/a candidateRerenderScheduled=false neighborRerenderScheduled=false",
                action,
                pos.toShortString(),
                compoundVisibleAttachmentLabel(type),
                label,
                serverMarker,
                dy);
    }

    private static void logCompoundVisibleRenderTraceSupportUpdate(
            Level world,
            BlockPos pos,
            BlockState state,
            boolean qualifies
    ) {
        if (!beta4CompoundVisibleRenderTraceEnabled()) {
            return;
        }
        double dy = state == null ? 0.0d : SlabSupport.getYOffset(world, pos, state);
        Slabbed.LOGGER.info(
                "[SLABBED_BETA4_COMPOUND_VISIBLE_RENDER_TRACE_SUPPORT_UPDATE] pos={} marker=lowered_slab_carrier serverMarker={} clientMarker=n/a modelViewType=Level slabSupportDy={} clientDy=n/a candidateRerenderScheduled=false neighborRerenderScheduled=false",
                pos.toShortString(),
                qualifies,
                dy);
    }

    // ── shared query ──────────────────────────────────────────────────

    /**
     * Returns true if {@code pos} carries a persistent slab-anchor.
     *
     * <p>Safe on both server and client (client mirror is populated via attachment sync).
     * Returns false for any {@link BlockGetter} that is not a full {@link World}, so it is
     * safe to call from shape mixins that may receive partial views during chunk gen.
     */
    public static boolean isAnchored(BlockGetter world, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (!(world instanceof Level w)) {
            // Chunk render paths (e.g. ChunkRendererRegion) are not Level instances and
            // cannot access chunk attachments directly.  Delegate to the client fallback
            // hook so the model render path sees the same anchor state as outline/raycast.
            return clientAnchorLookup != null && clientAnchorLookup.test(pos);
        }
        LevelChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet set = chunk.getAttached(ANCHOR_TYPE);
        boolean anchored = set != null && set.contains(pos.asLong());
        if (TRACE && anchored) {
            Slabbed.LOGGER.info("[ANCHOR] query true side={} pos={}",
                    w.isClientSide() ? "CLIENT" : "SERVER", pos.toShortString());
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
     * <p>Mirrors the {@link #isAnchored} dispatch: server Level, client Level, and
     * non-Level render views via {@link #clientCompoundFullBlockAnchorLookup}.
     */
    public static boolean isCompoundFullBlockAnchor(BlockGetter world, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (!(world instanceof Level w)) {
            return clientCompoundFullBlockAnchorLookup != null
                    && clientCompoundFullBlockAnchorLookup.test(pos);
        }
        LevelChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet set = chunk.getAttached(COMPOUND_FULL_BLOCK_ANCHOR_TYPE);
        boolean compound = set != null && set.contains(pos.asLong());
        if (TRACE && compound) {
            Slabbed.LOGGER.info("[ANCHOR] compound_full_block query true side={} pos={}",
                    w.isClientSide() ? "CLIENT" : "SERVER", pos.toShortString());
        }
        return compound;
    }

    public static boolean isCompoundVisibleSideLowerSlab(BlockGetter world, BlockPos pos, BlockState state) {
        if (!isCompoundVisibleSideLowerSlabState(state) || pos == null) {
            return false;
        }
        if (!(world instanceof Level w)) {
            return clientCompoundVisibleSideLowerSlabLookup != null
                    && clientCompoundVisibleSideLowerSlabLookup.test(pos);
        }
        LevelChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet set = chunk.getAttached(COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE);
        boolean marked = set != null && set.contains(pos.asLong());
        if (TRACE && marked) {
            Slabbed.LOGGER.info("[ANCHOR] compound_visible_side_lower_slab query true side={} pos={}",
                    w.isClientSide() ? "CLIENT" : "SERVER", pos.toShortString());
        }
        return marked;
    }

    public static boolean isCompoundVisibleSideUpperSlab(BlockGetter world, BlockPos pos, BlockState state) {
        if (!isCompoundVisibleSideUpperSlabState(state) || pos == null) {
            return false;
        }
        if (!(world instanceof Level w)) {
            return clientCompoundVisibleSideUpperSlabLookup != null
                    && clientCompoundVisibleSideUpperSlabLookup.test(pos);
        }
        LevelChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet set = chunk.getAttached(COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE);
        boolean marked = set != null && set.contains(pos.asLong());
        if (TRACE && marked) {
            Slabbed.LOGGER.info("[ANCHOR] compound_visible_side_upper_slab query true side={} pos={}",
                    w.isClientSide() ? "CLIENT" : "SERVER", pos.toShortString());
        }
        return marked;
    }

    public static boolean isCompoundVisibleSideDoubleSlab(BlockGetter world, BlockPos pos, BlockState state) {
        if (!isCompoundVisibleSideDoubleSlabState(state) || pos == null) {
            return false;
        }
        if (!(world instanceof Level w)) {
            return clientCompoundVisibleSideDoubleSlabLookup != null
                    && clientCompoundVisibleSideDoubleSlabLookup.test(pos);
        }
        LevelChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet set = chunk.getAttached(COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE);
        boolean marked = set != null && set.contains(pos.asLong());
        if (TRACE && marked) {
            Slabbed.LOGGER.info("[ANCHOR] compound_visible_side_double_slab query true side={} pos={}",
                    w.isClientSide() ? "CLIENT" : "SERVER", pos.toShortString());
        }
        return marked;
    }

    public static boolean isCompoundVisibleOwnerTopSlab(BlockGetter world, BlockPos pos, BlockState state) {
        if (!isCompoundVisibleOwnerTopSlabState(state) || pos == null) {
            return false;
        }
        if (!(world instanceof Level w)) {
            return clientCompoundVisibleOwnerTopSlabLookup != null
                    && clientCompoundVisibleOwnerTopSlabLookup.test(pos);
        }
        LevelChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet set = chunk.getAttached(COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE);
        boolean marked = set != null && set.contains(pos.asLong());
        if (TRACE && marked) {
            Slabbed.LOGGER.info("[ANCHOR] compound_visible_owner_top_slab query true side={} pos={}",
                    w.isClientSide() ? "CLIENT" : "SERVER", pos.toShortString());
        }
        return marked;
    }

    public static boolean isPersistentLoweredSlabCarrier(BlockGetter world, BlockPos pos, BlockState state) {
        if (!isPersistentLoweredSlabCarrierState(state) || pos == null) {
            return false;
        }
        // F1 (haunted-cells audit): a FROZEN-FLAT slab renders flush — it is definitionally NOT a
        // lowered carrier. Folded into THE shared read (the shared-predicate half-fix lesson) so every
        // support reader (floorTorch family, magnitude walks, side-lane qualifiers) sees the same
        // truth; without this, objects placed on a visually-flush slab sank 0.5-1.0 into it whenever
        // the RETURN-hook qualifier had also marked it (the false-support contradiction).
        if (isFrozenFlat(world, pos)) {
            return false;
        }
        if (isCompoundVisibleOwnerTopSlab(world, pos, state)) {
            return false;
        }
        if (!(world instanceof Level w)) {
            return clientLoweredSlabCarrierLookup != null && clientLoweredSlabCarrierLookup.test(pos);
        }
        LevelChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
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
                    w.isClientSide() ? "CLIENT" : "SERVER", pos.toShortString());
        }
        return carrier;
    }

    public static boolean isPersistentLoweredBottomSlabCarrierNonRecursive(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        if (!isBottomPersistentLoweredSlabCarrierState(state) || world == null || pos == null) {
            return false;
        }
        // F1 (haunted-cells audit): a FROZEN-FLAT slab renders flush — it must not live-qualify as a
        // lowered carrier either. This NonRecursive read is consumed DIRECTLY by support readers
        // (bypassing isPersistentLoweredSlabCarrier's own gate), so the invariant must live here too.
        if (isFrozenFlat(world, pos)) {
            return false;
        }
        if (isCompoundVisibleOwnerTopSlab(world, pos, state)) {
            return false;
        }
        if (world instanceof Level w) {
            LevelChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
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
     *   <li>solid full block</li>
     *   <li>has a bottom slab directly below, or sits directly on an ordinary full
     *       block already lowered by exactly {@code -0.5}</li>
     * </ul>
     *
     * <p>Strictly narrower than {@link SlabSupport#shouldOffset}: compound
     * bed/double-half cases, side slabs, carpets, block entities, and non-full blocks
     * remain excluded.
     */
    public static boolean qualifiesForAnchor(BlockGetter world, BlockPos pos, BlockState state) {
        if (!isOrdinaryFullBlockAnchorCandidate(world, pos, state)) {
            return false;
        }
        if (SlabSupport.hasBottomSlabBelow(world, pos)) {
            return true;
        }
        BlockPos belowPos = pos.below();
        BlockState below = world.getBlockState(belowPos);
        return qualifiesAsVerticalChainSupport(world, belowPos, below);
    }

    public static boolean isOrdinaryFullBlockAnchorCandidate(BlockGetter world, BlockPos pos, BlockState state) {
        if (state == null || state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        var block = state.getBlock();
        if (block instanceof SlabBlock) {
            return false;
        }
        if (block instanceof CarpetBlock || block instanceof MossyCarpetBlock) {
            return false;
        }
        if (SlabSupport.isThinTopLayer(state)) {
            return false;
        }
        if (block instanceof EntityBlock) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return false;
        }
        if (!state.isSolidRender()) {
            return false;
        }
        return true;
    }

    public static boolean qualifiesForDirectAnchor(BlockGetter world, BlockPos pos, BlockState state) {
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
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        if (!isOrdinaryFullBlockAnchorCandidate(world, pos, state)) {
            return false;
        }
        if (world == null || pos == null) {
            return false;
        }
        BlockPos belowPos = pos.below();
        BlockState belowSlab = world.getBlockState(belowPos);
        return SlabSupport.isLoweredCompoundSourceSlab(world, belowPos, belowSlab);
    }

    private static boolean qualifiesForTopOfCompoundFullAnchor(
            BlockGetter world,
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
        if (!sourcePos.equals(pos.below())) {
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
            BlockGetter world,
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
        if (!isOrdinaryFullBlockAnchorCandidate(world, sourcePos, sourceState)
                || !isCompoundFullBlockAnchor(world, sourcePos)) {
            return false;
        }
        double sourceDy = SlabSupport.getYOffset(world, sourcePos, sourceState);
        if (Math.abs(sourceDy + 1.0d) > 1.0e-6d) {
            return false;
        }
        int dx = Math.abs(pos.getX() - sourcePos.getX());
        int dy = Math.abs(pos.getY() - sourcePos.getY());
        int dz = Math.abs(pos.getZ() - sourcePos.getZ());
        return dy == 0 && dx + dz == 1;
    }

    private static boolean qualifiesForCompoundVisibleSideUpperSlab(
            BlockGetter world,
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
        if (!isOrdinaryFullBlockAnchorCandidate(world, sourcePos, sourceState)
                || !isCompoundFullBlockAnchor(world, sourcePos)) {
            return false;
        }
        double sourceDy = SlabSupport.getYOffset(world, sourcePos, sourceState);
        if (Math.abs(sourceDy + 1.0d) > 1.0e-6d) {
            return false;
        }
        int dx = Math.abs(pos.getX() - sourcePos.getX());
        int dy = Math.abs(pos.getY() - sourcePos.getY());
        int dz = Math.abs(pos.getZ() - sourcePos.getZ());
        return dy == 0 && dx + dz == 1;
    }

    private static boolean qualifiesForCompoundVisibleSideDoubleSlab(
            BlockGetter world,
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
        if (!isOrdinaryFullBlockAnchorCandidate(world, sourcePos, sourceState)
                || !isCompoundFullBlockAnchor(world, sourcePos)) {
            return false;
        }
        double sourceDy = SlabSupport.getYOffset(world, sourcePos, sourceState);
        if (Math.abs(sourceDy + 1.0d) > 1.0e-6d) {
            return false;
        }
        int dx = Math.abs(pos.getX() - sourcePos.getX());
        int dy = Math.abs(pos.getY() - sourcePos.getY());
        int dz = Math.abs(pos.getZ() - sourcePos.getZ());
        return dy == 0 && dx + dz == 1;
    }

    public static boolean qualifiesForPersistentLoweredSlabCarrier(BlockGetter world, BlockPos pos, BlockState state) {
        return isPersistentLoweredSlabCarrierState(state)
                && !isCompoundVisibleOwnerTopSlab(world, pos, state)
                && (SlabSupport.isLoweredSideLaneSlabCarrier(world, pos, state)
                || qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlock(world, pos, state)
                || qualifiesForPersistentLoweredBottomSlabOnAdjacentLoweredBridgeSupport(world, pos, state)
                || qualifiesForPersistentLoweredSlabOnVerticalLoweredSlabSupport(world, pos, state));
    }

    /**
     * L8 vertical lane: a slab of ANY type (TOP/BOTTOM/DOUBLE) resting directly on top of a lowered
     * TOP- or DOUBLE-type slab must anchor at placement so breaking the support later cannot pop it
     * back to flush. This is the vertical counterpart of
     * {@link SlabSupport#isLoweredSideLaneSlabCarrier} (horizontal neighbour lowering) — the gap it
     * closes: {@code SlabSupport.getYOffsetInner}'s slab-below branch already live-derives -0.5 for
     * this relationship (via {@link SlabSupport#isLoweredTopLikeSlabCarrier}), but nothing previously
     * PERSISTED it, so removing the support slab removed the only path to -0.5 (live-reported "pop
     * upon breaking at the end").
     *
     * <p>Support must be TOP or DOUBLE type — a BOTTOM-type support is never a valid vertical
     * lowering source (its own top face sits at the vanilla y=0.5 plane regardless of whether the
     * BOTTOM slab itself is lowered), matching the regression guard that a slab resting on a
     * BOTTOM-type support must never anchor vertically. Delegates the TOP/DOUBLE + lowering-source
     * decision to {@link SlabSupport#isLoweredTopLikeSlabCarrier} so the persistence lane and the
     * live-dy lane share one definition of "lowered vertical support".
     */
    private static boolean qualifiesForPersistentLoweredSlabOnVerticalLoweredSlabSupport(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        if (world == null || pos == null || !isPersistentLoweredSlabCarrierState(state)) {
            return false;
        }
        BlockPos belowPos = pos.below();
        BlockState below = world.getBlockState(belowPos);
        return SlabSupport.isLoweredTopLikeSlabCarrier(world, belowPos, below);
    }

    private static boolean qualifiesForCompoundVisibleOwnerTopSlab(
            BlockGetter world,
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
        if (!pos.equals(sourcePos.above())) {
            return false;
        }
        if (!isOrdinaryFullBlockAnchorCandidate(world, sourcePos, sourceState)
                || !isCompoundFullBlockAnchor(world, sourcePos)) {
            return false;
        }
        double sourceDy = SlabSupport.getYOffset(world, sourcePos, sourceState);
        return Math.abs(sourceDy + 1.0d) <= 1.0e-6d;
    }

    private static boolean isPersistentLoweredSlabCarrierState(BlockState state) {
        // F5: fluid-blind — a waterlog flip is height-neutral; it must neither de-qualify the
        // WRITE-belt (which was silently removing the carrier on the flip) nor blind the reads.
        return state != null
                && state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                // TS-COMPAT SUBJECT GUARD (CROSS-PORT LAW / failure mode 4). A Terrain-Slabs-owned slab is
                // a SELF-RENDERING surface TS positions itself; from Slabbed's perspective it must be treated
                // as flush/vanilla-solid, FULL STOP (the world-hole invariant) — it must NEVER become a
                // Slabbed "persistent lowered slab carrier" in its own right. This is the single shared STATE
                // gate every carrier read/write path routes through: isPersistentLoweredSlabCarrier (read),
                // qualifiesForPersistentLoweredSlabCarrier (write — all four disjunction lanes),
                // qualifiesForPersistentLoweredSlabOnVerticalLoweredSlabSupport (Lane D), and via
                // isBottomPersistentLoweredSlabCarrierState the two *NonRecursive read helpers + their
                // qualifier lanes. Empirically REACHABLE before this guard (throwaway probe driving the real
                // getYOffsetInner path under the shouldSkipSlabSupportTestOverride seam): a TS-owned BOTTOM
                // slab resting on a lowered full block (Lane B/C NonRecursive), or on a lowered TOP/DOUBLE
                // support (Lane D), or side-lane beside a vanilla lowered slab, both LIVE-qualified as a
                // carrier AND its own getYOffsetInner read -0.5 — Slabbed treating a TS-positioned surface as
                // if IT were subtractively lowered. (The public getYOffset entry is separately guarded by its
                // own CompatHooks.shouldSkipOffset(state) short-circuit for a TS subject, so the render dy was
                // already flush in production; this closes the carrier-MARKER vector so no stale/inert TS
                // marker can ever be written or read by any un-shouldSkipOffset-guarded consumer — the
                // internal getYOffsetInner recursions and every direct isPersistentLoweredSlabCarrier reader.)
                // NARROWING only, keyed on the terrain_slabs/terrainslabs namespace → byte-identical without
                // Terrain Slabs loaded. Reuses the one shared shouldSkipSlabSupport choke point (no new
                // mechanism), the 5304e4b3/68088bc6/c7a19048/6a3f2859 precedent.
                && !CompatHooks.shouldSkipSlabSupport(state);
    }

    // F5 (audit STATE_DEFENSE_DIVERGENCE_2026-07-07): the marker STATE predicates are FLUID-BLIND,
    // like the anchor-family reads — a bucket/sponge flip at the cell must never change its height
    // authority. (The attachments themselves already survive the property flip; these gates were the
    // read-side half of the pop.) Waterlog is height-neutral; a slab TYPE change still re-keys.
    private static boolean isCompoundVisibleSideLowerSlabState(BlockState state) {
        return state != null
                && state.is(Blocks.STONE_SLAB)
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    private static boolean isCompoundVisibleSideUpperSlabState(BlockState state) {
        return state != null
                && state.is(Blocks.STONE_SLAB)
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.TOP;
    }

    private static boolean isCompoundVisibleSideDoubleSlabState(BlockState state) {
        return state != null
                && state.is(Blocks.STONE_SLAB)
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
    }

    private static boolean isCompoundVisibleOwnerTopSlabState(BlockState state) {
        return state != null
                && state.is(Blocks.STONE_SLAB)
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    private static boolean isBottomPersistentLoweredSlabCarrierState(BlockState state) {
        return isPersistentLoweredSlabCarrierState(state) && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    private static boolean qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlock(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(SlabBlock.TYPE)
                || state.getValue(SlabBlock.TYPE) != SlabType.BOTTOM
                || !state.getFluidState().isEmpty()
                || SlabSupport.getYOffset(world, pos, state) != -0.5) {
            return false;
        }
        BlockPos belowPos = pos.below();
        BlockState below = world.getBlockState(belowPos);
        if (!isOrdinaryFullBlockAnchorCandidate(world, belowPos, below)) {
            return false;
        }
        // CROSS-PHASE-REVIEW FIX (sweeper Finding 1, correcting L8 f70eec96): the below-support reading
        // must accept ANY lowered magnitude (< 0), not just exactly -0.5. When the L8 phase widened the
        // BlockItemPlacementIntentMixin below-support gate to delegate to
        // qualifiesForPersistentLoweredSlabCarrier (this method's caller), it replaced that mixin's OLD
        // inline check — isOrdinaryFullBlockAnchorCandidate(below) && (isAnchored(below) ||
        // getYOffset(below) < 0.0d) — so a BOTTOM slab placed for real on a full block that itself
        // compounds to -1.0 (un-anchored, e.g. a full block resting on a lowered bottom-slab stack)
        // must still qualify exactly as it did pre-L8. The exact "== -0.5" narrowed that: it rejected a
        // -1.0 support, regressing the case the old gate accepted (proven RED via a throwaway probe:
        // belowDy=-1.0, un-anchored -> OLD gate true, "== -0.5" sub-lane false). Restore the old, broader
        // "< 0" behaviour (which subsumes -0.5 and -1.0) so persistence is never dropped.
        return isAnchored(world, belowPos) || SlabSupport.getYOffset(world, belowPos, below) < 0.0d;
    }

    private static boolean qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlockNonRecursive(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        if (!isBottomPersistentLoweredSlabCarrierState(state) || world == null || pos == null) {
            return false;
        }
        BlockPos belowPos = pos.below();
        BlockState below = world.getBlockState(belowPos);
        if (!isOrdinaryFullBlockAnchorCandidate(world, belowPos, below)) {
            return false;
        }
        return isAnchored(world, belowPos) || SlabSupport.hasBottomSlabBelow(world, belowPos);
    }

    private static boolean qualifiesForPersistentLoweredBottomSlabOnAdjacentLoweredBridgeSupport(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(SlabBlock.TYPE)
                || state.getValue(SlabBlock.TYPE) != SlabType.BOTTOM
                || !state.getFluidState().isEmpty()) {
            return false;
        }

        BlockPos supportY = pos.below();
        boolean hasLoweredAnchoredBridgeNeighbor = false;
        for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = supportY.relative(dir);
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
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        if (!isBottomPersistentLoweredSlabCarrierState(state) || world == null || pos == null) {
            return false;
        }
        BlockPos supportY = pos.below();
        boolean hasLoweredAnchoredBridgeNeighbor = false;
        for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = supportY.relative(dir);
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

    private static boolean qualifiesAsVerticalChainSupport(BlockGetter world, BlockPos pos, BlockState state) {
        return SlabSupport.isFullHeightLoweredCarrier(world, pos, state);
    }

    private static boolean qualifiesForSideAdjacentLoweredFullAnchor(
            BlockGetter world,
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
        // lowering further to ITS neighbours (tree-canopy contagion). This is the maintainer's "blocks
        // should not inherit states like this" RED. Lowering for full blocks now comes only from
        // genuine support directly below (a slab, or a lowered full-block column down to a slab)
        // via qualifiesForAnchor / the column walk — never sideways. A piece genuinely
        // cantilevered over air still merges, computed LIVE via
        // SlabSupport#isCantileverLoweredFullBlock (never stale, never pops). Port of 1.21.1 83afed84.
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
        return SlabSupport.getYOffset(world, sourcePos, sourceState) < 0.0d;
    }

    private static boolean qualifiesForSideAdjacentCompoundFullAnchor(
            BlockGetter world,
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
            BlockGetter world,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        return SlabSupport.isFullHeightLoweredCarrier(world, sourcePos, sourceState)
                || SlabSupport.isLoweredSideLaneSlabCarrier(world, sourcePos, sourceState)
                || SlabSupport.isBottomSlabLoweredByCarrierBelow(world, sourcePos, sourceState);
    }
}
