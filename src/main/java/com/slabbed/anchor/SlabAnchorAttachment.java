package com.slabbed.anchor;

import java.util.function.Predicate;
import com.mojang.serialization.Codec;
import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedAuditBridge;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.PaleMossCarpetBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Persistent slab-anchor registry.
 *
 * <p>When an ordinary full block is placed directly on a bottom slab, that placement is
 * recorded as an anchor on the chunk so the block keeps its lowered dy even if the
 * supporting bottom slab is later removed. Anchors are cleared when the anchored block
 * itself is broken/replaced.
 *
 * <p>Storage: per-{@link WorldChunk} {@link LongOpenHashSet} of packed {@link BlockPos}
 * longs. Persisted via Fabric data attachment, synced to all watching clients.
 *
 * <p>Scope: direct FB-on-BS, ordinary full blocks placed beside an already
 * anchored lowered full block, column-lowered ordinary full blocks, plus
 * side slabs placed into an already lowered side-slab lane. No retroactive
 * anchoring and no torch interaction.
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

    /**
     * Client-side fallback for freeze-on-place FLAT marker queries from render paths
     * (same contract as {@link #clientAnchorLookup}).
     */
    public static Predicate<BlockPos> clientFrozenFlatLookup = null;

    private static final Identifier ANCHOR_ID = Identifier.of(Slabbed.MOD_ID, "slab_anchors");
    private static final Identifier FROZEN_FLAT_ID = Identifier.of(Slabbed.MOD_ID, "frozen_flat");

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

    /**
     * FREEZE-ON-PLACE flat marker: a structural piece (ordinary full block or slab) placed at
     * dy=0 is recorded here so its flat height locks — a slab / lowered carrier placed under or
     * beside it later can no longer pull it down. The "never autonomously moves" companion of
     * {@link #ANCHOR_TYPE} (which locks the lowered case). Read as dy=0 by
     * {@code SlabSupport.getYOffsetInner}; cleared when the piece is broken.
     */
    public static final AttachmentType<LongOpenHashSet> FROZEN_FLAT_TYPE =
            AttachmentRegistry.<LongOpenHashSet>create(FROZEN_FLAT_ID, builder -> builder
                    .persistent(SET_CODEC)
                    .syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())
            );

    /**
     * Triggers static-init class loading. Call once from the mod entrypoint so the
     * attachment is registered before any chunk loads.
     */
    public static void register() {
        // Touch the class so the static field initializes and registers with Fabric.
        if (ANCHOR_TYPE == null || FROZEN_FLAT_TYPE == null) {
            throw new IllegalStateException("SlabAnchorAttachment failed to register");
        }
    }

    // ── server-side mutation ──────────────────────────────────────────

    /**
     * Records an anchor at {@code pos}. Server-side only; no-op on client world or
     * if {@code pos} does not qualify under the direct or adjacent lowered-FB anchor
     * rules.
     */
    public static void addAnchor(World world, BlockPos pos, BlockState state) {
        if (world == null || world.isClient()) {
            return;
        }
        boolean directAnchor = qualifiesForDirectAnchor(world, pos, state);
        boolean adjacentAnchor = !directAnchor && qualifiesForAdjacentLoweredFullBlockAnchor(world, pos, state);
        boolean columnAnchor = !directAnchor && !adjacentAnchor && qualifiesForColumnLoweredAnchor(world, pos, state);
        boolean sideSlabAnchor = !directAnchor
                && !adjacentAnchor
                && !columnAnchor
                && qualifiesForLoweredSideSlabAnchor(world, pos, state);
        boolean belowAnchoredAnchor = !directAnchor
                && !adjacentAnchor
                && !columnAnchor
                && !sideSlabAnchor
                && qualifiesForBelowAnchoredBlockAnchor(world, pos, state);
        boolean qualifies = directAnchor || adjacentAnchor || columnAnchor || sideSlabAnchor || belowAnchoredAnchor;
        if (TRACE) {
            Slabbed.LOGGER.info("[ANCHOR] add attempt side=SERVER pos={} state={} qualifies={} direct={} adjacent={} column={} sideSlab={} belowAnchored={}",
                    pos.toShortString(), state, qualifies, directAnchor, adjacentAnchor, columnAnchor, sideSlabAnchor, belowAnchoredAnchor);
        }
        if (!qualifies) {
            return;
        }
        WorldChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] add reject pos={} reason=chunk_null", pos.toShortString());
            }
            return;
        }
        LongOpenHashSet existing = chunk.getAttached(ANCHOR_TYPE);
        LongOpenHashSet set = existing == null ? new LongOpenHashSet() : new LongOpenHashSet(existing);
        if (set.add(pos.asLong())) {
            // setAttached triggers persistence + auto-sync for synced attachments.
            chunk.setAttached(ANCHOR_TYPE, set);
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] add success pos={} chunk={} setSize={}",
                        pos.toShortString(), chunk.getPos(), set.size());
            }
            if (SlabbedAuditBridge.isLiveTraceEnabled()) {
                BlockPos supportPos = sideSlabAnchor ? pos : pos.down();
                SlabbedAuditBridge.captureLiveTrace(world, supportPos, pos, "ANCHOR_ADDED");
            }
        }
    }

    /**
     * FREEZE-ON-PLACE (Maintainer's law — "a placed block must stay in that spot and not autonomously
     * pop"): locks the FLAT half of a placement's height at the moment it is placed. Server-side
     * only; called from {@code BlockOnPlacedAnchorMixin.onPlaced} after {@link #addAnchor}.
     *
     * <p>If a STRUCTURAL piece (ordinary full block or slab) is placed FLAT (dy ≈ 0) it records a
     * {@link #FROZEN_FLAT_TYPE} marker, so a slab / lowered carrier placed under or beside it later
     * can no longer pull it down (the exact live down-pop Maintainer reported: "I placed the slab, the
     * spruce log popped down"). No-op for decorative followers (lanterns / torches / hangers /
     * signs) so they keep tracking their supports, and for pieces already anchored or frozen.
     * Natural / setBlockState blocks never call onPlaced, so terrain stays fully geometric.
     *
     * <p>MAIN-SHAPE DECISION vs the donor (compat 8aafd1ff): the donor also records an UNCHECKED
     * flat anchor for any piece placed LOWERED (dy &lt; 0). Main's anchor read-back grammar is
     * richer — full-block anchors compound with a lowered support to -1.0, but SLAB anchors read
     * back as a flat -0.5 in the slab branch — so an unchecked anchor here would pin a right-click
     * placed TOP-slab-on-terrain (live-confirmed -1.0 flush) up at -0.5: exactly the "compound top
     * freezes at -0.5" limitation the donor documents. Main's onPlaced hook already anchor-locks
     * lowered placements through the five qualifier lanes (direct / adjacent / column / side-slab /
     * below-anchored) whose read-back reproduces the placed dy, so the lowered case stays with
     * those lanes and this hook adds only the missing FLAT half of the law.
     */
    public static void freezeLoweredOnPlace(World world, BlockPos pos, BlockState state) {
        if (world == null || world.isClient() || pos == null || state == null
                || state.isAir() || !state.getFluidState().isEmpty()) {
            return;
        }
        if (isAnchored(world, pos) || isFrozenFlat(world, pos)) {
            return;
        }
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy < -1.0e-6) {
            // Lowered placements are height-locked by the qualifier-lane anchors added in
            // addAnchor (see the MAIN-SHAPE DECISION above); nothing more to record here.
            return;
        }
        // dy ≈ 0: lock the FLAT height of a STRUCTURAL piece (ordinary full block or slab) so a
        // slab / lowered carrier placed under or beside it later can no longer pull it down.
        boolean structural = isOrdinaryAnchorCandidate(world, pos, state)
                || state.getBlock() instanceof SlabBlock;
        if (!structural) {
            return;
        }
        WorldChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return;
        }
        LongOpenHashSet existing = chunk.getAttached(FROZEN_FLAT_TYPE);
        LongOpenHashSet set = existing == null ? new LongOpenHashSet() : new LongOpenHashSet(existing);
        if (set.add(pos.asLong())) {
            // setAttached triggers persistence + auto-sync for synced attachments.
            chunk.setAttached(FROZEN_FLAT_TYPE, set);
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] frozen_flat add pos={} chunk={} setSize={}",
                        pos.toShortString(), chunk.getPos(), set.size());
            }
        }
    }

    /**
     * Clears any anchor at {@code pos}. Server-side only.
     */
    public static void removeAnchor(World world, BlockPos pos) {
        if (world == null || world.isClient()) {
            return;
        }
        WorldChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return;
        }
        // Freeze-on-place flat marker clears when the piece itself is broken/replaced
        // (onStateReplaced calls removeAnchor for every removal), so a fresh placement in
        // the same spot re-evaluates from scratch.
        LongOpenHashSet existingFrozen = chunk.getAttached(FROZEN_FLAT_TYPE);
        if (existingFrozen != null && existingFrozen.contains(pos.asLong())) {
            LongOpenHashSet frozen = new LongOpenHashSet(existingFrozen);
            frozen.remove(pos.asLong());
            if (frozen.isEmpty()) {
                chunk.removeAttached(FROZEN_FLAT_TYPE);
            } else {
                chunk.setAttached(FROZEN_FLAT_TYPE, frozen);
            }
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] frozen_flat remove pos={}", pos.toShortString());
            }
        }
        LongOpenHashSet existing = chunk.getAttached(ANCHOR_TYPE);
        if (existing == null || existing.isEmpty()) {
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] remove pos={} existed=false", pos.toShortString());
            }
            return;
        }
        LongOpenHashSet set = new LongOpenHashSet(existing);
        boolean removed = set.remove(pos.asLong());
        if (TRACE) {
            Slabbed.LOGGER.info("[ANCHOR] remove pos={} existed={}", pos.toShortString(), removed);
        }
        if (removed) {
            if (set.isEmpty()) {
                chunk.removeAttached(ANCHOR_TYPE);
            } else {
                chunk.setAttached(ANCHOR_TYPE, set);
            }
            if (SlabbedAuditBridge.isLiveTraceEnabled()) {
                BlockPos supportPos = pos.down();
                SlabbedAuditBridge.captureLiveTrace(world, supportPos, pos, "ANCHOR_REMOVED");
            }
        }
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
     * Returns true if {@code pos} carries a freeze-on-place FLAT marker — a structural piece whose
     * height was locked at 0 when placed. Safe on server and client (client mirror via
     * {@link #clientFrozenFlatLookup}); false for non-{@link World} views without a lookup.
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

    // ── qualifier ─────────────────────────────────────────────────────

    /**
     * Tight predicate matching the existing direct FB-on-BS rule:
     * <ul>
     *   <li>not air, not fluid</li>
     *   <li>not a slab, carpet, thin top layer, block-entity, bed, or double-block</li>
     *   <li>solid full block</li>
     *   <li>has a bottom slab directly below ({@link SlabSupport#hasBottomSlabBelow})</li>
     * </ul>
     *
     * <p>Strictly narrower than {@link SlabSupport#shouldOffset}: chain-of-blocks and
     * compound bed/double-half cases are intentionally excluded from anchoring v1.
     */
    public static boolean qualifiesForDirectAnchor(BlockView world, BlockPos pos, BlockState state) {
        if (!isOrdinaryAnchorCandidate(world, pos, state)) {
            return false;
        }
        return SlabSupport.hasBottomSlabBelow(world, pos);
    }

    /**
     * Anchors an ordinary full block placed in a LOWERED COLUMN — one with a
     * bottom slab or an existing anchor somewhere in the solid column directly
     * below it. Without this, such a block is lowered only by a live column walk
     * ({@code SlabSupport.hasSlabInColumn}); breaking a block lower in the column
     * opens an air gap that stops the walk, so the block above un-lowers and
     * visually JUMPS up. Anchoring it at placement makes that lowering persist
     * (matching the direct-on-slab anchor contract), so editing below it no
     * longer moves it. Natural terrain never qualifies — a column with no slab or
     * anchor below returns false.
     */
    private static boolean qualifiesForColumnLoweredAnchor(BlockView world, BlockPos pos, BlockState state) {
        if (!isOrdinaryAnchorCandidate(world, pos, state)) {
            return false;
        }
        return SlabSupport.hasLoweringSourceInColumnBelow(world, pos);
    }

    private static boolean qualifiesForAdjacentLoweredFullBlockAnchor(BlockView world, BlockPos pos, BlockState state) {
        if (!isOrdinaryAnchorCandidate(world, pos, state)) {
            return false;
        }
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(direction);
            BlockState neighbor = world.getBlockState(neighborPos);
            if (neighbor.getBlock() instanceof SlabBlock) {
                continue;
            }
            if (!neighbor.isSolidBlock(world, neighborPos)) {
                continue;
            }
            if (neighbor.getBlock() instanceof BlockEntityProvider) {
                continue;
            }
            if (SlabSupport.getYOffset(world, neighborPos, neighbor) == -0.5d) {
                return true;
            }
        }
        return false;
    }

    /**
     * Anchors an ordinary full block placed DIRECTLY BELOW an already-anchored lowered
     * full block. Handles refilling a gap inside a lowered column: e.g. TS &gt; B1 B2 B3 B4,
     * break B2+B3 (B4 keeps its anchor), then replace B3 — the column source beneath B3 is
     * now air so the direct/column checks fail, but B3 must still lower to match the
     * anchored B4 above it or it un-lowers and z-fights B4. The block above being anchored
     * proves this cell belongs to a lowered column.
     */
    private static boolean qualifiesForBelowAnchoredBlockAnchor(BlockView world, BlockPos pos, BlockState state) {
        if (!isOrdinaryAnchorCandidate(world, pos, state)) {
            return false;
        }
        BlockPos abovePos = pos.up();
        BlockState above = world.getBlockState(abovePos);
        if (above.getBlock() instanceof SlabBlock || above.getBlock() instanceof BlockEntityProvider) {
            return false;
        }
        return isAnchored(world, abovePos);
    }

    private static boolean qualifiesForLoweredSideSlabAnchor(BlockView world, BlockPos pos, BlockState state) {
        if (state == null
                || state.isAir()
                || !(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        return SlabSupport.isLoweredSideSlabVisual(world, pos, state);
    }

    private static boolean isOrdinaryAnchorCandidate(BlockView world, BlockPos pos, BlockState state) {
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
        // Connecting blocks (fences, walls, glass panes / iron bars, fence gates) are
        // STRUCTURAL for the never-pop law even though they are not full solid cubes: a
        // placed one must be height-locked so it can't pop between -0.5 and 0.0 when a
        // neighbour changes (WYSIWYG — recorder session bb138275). They fail the
        // isSolidBlock gate below, so admit them explicitly here. Decorative followers
        // (lanterns, torches, hangers, signs) are NOT connecting blocks and stay geometric.
        if (isConnectingStructural(state)) {
            return true;
        }
        if (!state.isSolidBlock(world, pos)) {
            return false;
        }
        return true;
    }

    /**
     * True if replacing the block at {@code pos} with {@code newState} should PRESERVE the
     * height-lock rather than clear it: an in-place block-KIND transform (grass→dirt,
     * log→stripped_log) to another lock-eligible block keeps the placed height so the block
     * does not un-lower/jitter (WYSIWYG). A genuine break (→air), fluid, or replacement with a
     * non-lock block (slab / carpet / thin-top / block-entity / non-solid non-connecting)
     * returns false so the cell is freed and re-evaluated.
     */
    public static boolean replacementPreservesAnchor(BlockView world, BlockPos pos, BlockState newState) {
        if (newState == null || newState.isAir() || !newState.getFluidState().isEmpty()) {
            return false;
        }
        return isOrdinaryAnchorCandidate(world, pos, newState) || isConnectingStructural(newState);
    }

    /** Fence / wall / pane / gate — connecting blocks that must be height-locked like solids. */
    public static boolean isConnectingStructural(BlockState state) {
        Block b = state.getBlock();
        return b instanceof FenceBlock
                || b instanceof WallBlock
                || b instanceof PaneBlock
                || b instanceof FenceGateBlock;
    }
}
