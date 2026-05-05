package com.slabbed.anchor;

import java.util.function.Predicate;
import com.mojang.serialization.Codec;
import com.slabbed.Slabbed;
import com.slabbed.debug.BsFbLiveTrace;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.PaleMossCarpetBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

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

    /**
     * Client-side fallback for anchor queries issued by chunk render paths that
     * receive a non-{@link World} {@link net.minecraft.world.BlockView}
     * (e.g. {@code ChunkRendererRegion}).  Set by the client entrypoint; always
     * null on a dedicated server.  No {@code MinecraftClient} reference needed
     * in common code.
     */
    public static Predicate<BlockPos> clientAnchorLookup = null;
    public static Predicate<BlockPos> clientLoweredSlabCarrierLookup = null;

    private static final Identifier ANCHOR_ID = Identifier.of(Slabbed.MOD_ID, "slab_anchors");
    private static final Identifier LOWERED_SLAB_CARRIER_ID =
            Identifier.of(Slabbed.MOD_ID, "lowered_slab_carriers");

    /**
     * Codec for the anchor set.  Backed by {@code long[]} so the NBT representation is
     * a {@code LongArrayTag}, the most compact form available.
     */
    private static final Codec<LongOpenHashSet> SET_CODEC = Codec.LONG_STREAM.xmap(
            stream -> new LongOpenHashSet(stream.toArray()),
            set -> java.util.stream.LongStream.of(set.toLongArray())
    );

    /**
     * Packet codec for client sync. {@link AttachmentSyncPredicate#all()} is used at
     * registration so anchors travel with the chunk packet automatically.
     */
    private static final PacketCodec<RegistryByteBuf, LongOpenHashSet> PACKET_CODEC = PacketCodec.of(
            (set, buf) -> {
                long[] arr = set.toLongArray();
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
     * Triggers static-init class loading. Call once from the mod entrypoint so the
     * attachment is registered before any chunk loads.
     */
    public static void register() {
        // Touch the class so the static field initializes and registers with Fabric.
        if (ANCHOR_TYPE == null || LOWERED_SLAB_CARRIER_TYPE == null) {
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
    }

    private static void addAnchorUnchecked(World world, BlockPos pos) {
        boolean added = addToAttachment(world, pos, ANCHOR_TYPE, "anchor");
        if (added && BsFbLiveTrace.ENABLED) {
            BlockPos supportPos = pos.down();
            BsFbLiveTrace.capture(world, supportPos, pos, "ANCHOR_ADDED");
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
        if (set.add(pos.asLong())) {
            // setAttached triggers persistence + auto-sync for synced attachments.
            chunk.setAttached(type, set);
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] {} add success pos={} chunk={} setSize={}",
                        label, pos.toShortString(), chunk.getPos(), set.size());
            }
            return true;
        }
        return false;
    }

    /**
     * Clears any anchor at {@code pos}. Server-side only.
     */
    public static void removeAnchor(World world, BlockPos pos) {
        boolean removed = removeFromAttachment(world, pos, ANCHOR_TYPE, "anchor");
        if (removed && BsFbLiveTrace.ENABLED) {
            BlockPos supportPos = pos.down();
            BsFbLiveTrace.capture(world, supportPos, pos, "ANCHOR_REMOVED");
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
        }
        return removed;
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

    public static boolean isPersistentLoweredSlabCarrier(BlockView world, BlockPos pos, BlockState state) {
        if (!isPersistentLoweredSlabCarrierState(state) || pos == null) {
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
     *   <li>solid full block</li>
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
        if (block instanceof CarpetBlock || block instanceof PaleMossCarpetBlock) {
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
        if (!state.isSolidBlock(world, pos)) {
            return false;
        }
        return true;
    }

    public static boolean qualifiesForDirectAnchor(BlockView world, BlockPos pos, BlockState state) {
        return qualifiesForAnchor(world, pos, state) && SlabSupport.hasBottomSlabBelow(world, pos);
    }

    public static boolean qualifiesForPersistentLoweredSlabCarrier(BlockView world, BlockPos pos, BlockState state) {
        return isPersistentLoweredSlabCarrierState(state)
                && (SlabSupport.isLoweredSideLaneSlabCarrier(world, pos, state)
                || qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlock(world, pos, state)
                || qualifiesForPersistentLoweredBottomSlabOnAdjacentLoweredBridgeSupport(world, pos, state));
    }

    private static boolean isPersistentLoweredSlabCarrierState(BlockState state) {
        return state != null
                && state.getBlock() instanceof SlabBlock
                && state.contains(SlabBlock.TYPE)
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
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || state.get(SlabBlock.TYPE) != SlabType.BOTTOM
                || !state.getFluidState().isEmpty()
                || SlabSupport.getYOffset(world, pos, state) != -0.5) {
            return false;
        }
        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);
        if (!isOrdinaryFullBlockAnchorCandidate(world, belowPos, below)) {
            return false;
        }
        return isAnchored(world, belowPos) || SlabSupport.getYOffset(world, belowPos, below) == -0.5;
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
        if (!isOrdinaryFullBlockAnchorCandidate(world, belowPos, below)) {
            return false;
        }
        return isAnchored(world, belowPos) || SlabSupport.hasBottomSlabBelow(world, belowPos);
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
        if (!isOrdinaryFullBlockAnchorCandidate(world, pos, state)
                || !SlabSupport.isFullHeightLoweredCarrier(world, sourcePos, sourceState)) {
            return false;
        }
        int dx = Math.abs(pos.getX() - sourcePos.getX());
        int dy = Math.abs(pos.getY() - sourcePos.getY());
        int dz = Math.abs(pos.getZ() - sourcePos.getZ());
        if (dy != 0 || dx + dz != 1) {
            return false;
        }
        return SlabSupport.getYOffset(world, sourcePos, sourceState) == -0.5;
    }
}
