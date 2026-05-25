package com.slabbed.anchor;

import java.util.function.Predicate;
import com.mojang.serialization.Codec;
import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
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
    public static Predicate<BlockPos> clientLoweredSlabCarrierLookup = null;
    public static Predicate<BlockPos> clientCompoundFullBlockAnchorLookup = null;
    public static Predicate<BlockPos> clientCompoundVisibleSideLowerSlabLookup = null;
    public static Predicate<BlockPos> clientCompoundVisibleSideUpperSlabLookup = null;
    public static Predicate<BlockPos> clientCompoundVisibleSideDoubleSlabLookup = null;
    public static Predicate<BlockPos> clientCompoundVisibleOwnerTopSlabLookup = null;

    private static final Identifier ANCHOR_ID = Identifier.fromNamespaceAndPath(Slabbed.MOD_ID, "slab_anchors");
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
    private static final StreamCodec<RegistryFriendlyByteBuf, LongOpenHashSet> PACKET_CODEC = StreamCodec.of(
            (buf, set) -> {
                long[] arr = new long[set.size()];
            int arrIndex = 0;
            for (var arrIterator = set.iterator(); arrIterator.hasNext();) {
                arr[arrIndex++] = arrIterator.nextLong();
            }
                buf.writeVarInt(arr.length);
                for (long v : arr) {
                    buf.writeLong(v);
                }
            },
            buf -> {
                int n = buf.readVarInt();
                LongOpenHashSet s = new LongOpenHashSet(n);
                for (int i = 0; i < n; i++) {
                    s.add(buf.readLong());
                }
                return s;
            }
    );

    public static final AttachmentType<LongOpenHashSet> ANCHOR_TYPE =
            AttachmentRegistry.<LongOpenHashSet>create(ANCHOR_ID, builder -> builder
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

    /**
     * Triggers static-init class loading. Call once from the mod entrypoint so the
     * attachment is registered before any chunk loads.
     */
    public static void register() {
        // Touch the class so the static field initializes and registers with Fabric.
        if (ANCHOR_TYPE == null
                || LOWERED_SLAB_CARRIER_TYPE == null
                || COMPOUND_FULL_BLOCK_ANCHOR_TYPE == null
                || COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE == null
                || COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE == null
                || COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE == null
                || COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE == null) {
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
        if (qualifiesForCompoundFullBlockAnchor(world, pos, state)) {
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
        if (world == null || world.isClientSide()) {
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
     * Clears any anchor at {@code pos}. Server-side only.
     */
    public static void removeAnchor(Level world, BlockPos pos) {
        boolean removed = removeFromAttachment(world, pos, ANCHOR_TYPE, "anchor");
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
                "[JULIA_BETA4_COMPOUND_VISIBLE_RENDER_TRACE_MARKER_SET] action={} pos={} marker={} label={} serverMarker={} clientMarker=n/a modelViewType=Level slabSupportDy={} clientDy=n/a candidateRerenderScheduled=false neighborRerenderScheduled=false",
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
                "[JULIA_BETA4_COMPOUND_VISIBLE_RENDER_TRACE_SUPPORT_UPDATE] pos={} marker=lowered_slab_carrier serverMarker={} clientMarker=n/a modelViewType=Level slabSupportDy={} clientDy=n/a candidateRerenderScheduled=false neighborRerenderScheduled=false",
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
                || qualifiesForPersistentLoweredBottomSlabOnAdjacentLoweredBridgeSupport(world, pos, state));
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
        return state != null
                && state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getFluidState().isEmpty();
    }

    private static boolean isCompoundVisibleSideLowerSlabState(BlockState state) {
        return state != null
                && state.is(Blocks.STONE_SLAB)
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                && state.getFluidState().isEmpty();
    }

    private static boolean isCompoundVisibleSideUpperSlabState(BlockState state) {
        return state != null
                && state.is(Blocks.STONE_SLAB)
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.TOP
                && state.getFluidState().isEmpty();
    }

    private static boolean isCompoundVisibleSideDoubleSlabState(BlockState state) {
        return state != null
                && state.is(Blocks.STONE_SLAB)
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE
                && state.getFluidState().isEmpty();
    }

    private static boolean isCompoundVisibleOwnerTopSlabState(BlockState state) {
        return state != null
                && state.is(Blocks.STONE_SLAB)
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                && state.getFluidState().isEmpty();
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
        return isAnchored(world, belowPos) || SlabSupport.getYOffset(world, belowPos, below) == -0.5;
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
