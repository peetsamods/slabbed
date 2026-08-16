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
     * Compact packet codec for client sync. {@link AttachmentSyncPredicate#all()} is used at
     * registration so anchors travel with the chunk packet automatically.
     *
     * <p>The persistent {@link #SET_CODEC} intentionally remains the legacy long-array format
     * for world compatibility. Only the synchronized representation is compacted.
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
        // The magnitude store travels with the anchor set it completes, so it registers here and
        // can never be left unregistered by a new entrypoint that only knows about anchors.
        SlabPlacementDyAttachment.register();
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
        boolean blockEntityAnchor = !directAnchor
                && !adjacentAnchor
                && !columnAnchor
                && !sideSlabAnchor
                && !belowAnchoredAnchor
                && qualifiesForBlockEntityLoweredAnchor(world, pos, state);
        boolean decorativeAnchor = !directAnchor
                && !adjacentAnchor
                && !columnAnchor
                && !sideSlabAnchor
                && !belowAnchoredAnchor
                && !blockEntityAnchor
                && qualifiesForDecorativeObjectAnchor(world, pos, state);
        boolean qualifies = directAnchor || adjacentAnchor || columnAnchor || sideSlabAnchor
                || belowAnchoredAnchor || blockEntityAnchor || decorativeAnchor;
        if (TRACE) {
            Slabbed.LOGGER.info("[ANCHOR] add attempt side=SERVER pos={} state={} qualifies={} direct={} adjacent={} column={} sideSlab={} belowAnchored={} blockEntity={} decorative={}",
                    pos.toShortString(), state, qualifies, directAnchor, adjacentAnchor, columnAnchor, sideSlabAnchor,
                    belowAnchoredAnchor, blockEntityAnchor, decorativeAnchor);
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
            recordPlacementDy(world, pos, state);
        }
    }

    /**
     * LANE G ({@code LAW.md}): the anchor just recorded above says only THAT this cell is lowered.
     * Capture HOW FAR, once, right here, and hand it to {@link SlabPlacementDyAttachment}.
     *
     * <p>Read AFTER the anchor is in the set, deliberately: at this instant {@code getYOffset}
     * takes the anchor lane, so the number captured is exactly the height the player sees the
     * moment the block appears — the placed height, which is the only height the law lets this
     * cell ever have. The store holds no fact for {@code pos} yet, so this read cannot see its own
     * uninitialised value; the anchor lane resolves it from the surroundings, which is the one
     * legitimate time the surroundings get a say.
     *
     * <p>Only a LOWERED result is stored. A zero here is the seat correction that lifts a piece out
     * of a support it would otherwise sink into — a property of the shapes involved, not a placed
     * magnitude — and the flat half of the law already belongs to {@link #FROZEN_FLAT_TYPE}, whose
     * precedence must not change.
     *
     * <p><b>Seat-resolver hole, CLOSED 2026-08-06.</b> A lowered TOP or DOUBLE slab support once
     * matched none of {@code SlabSupport.supportSeatDy}'s three arms, so a piece standing on one
     * settled on the {@code -0.5} floor instead of its true depth — and because this method reads
     * {@code getYOffset} to decide the number LAW 1 will freeze, that floor was not merely drawn,
     * it was STORED (live-confirmed: a double-slab support at {@code -1.0} froze its follower at
     * {@code -0.5} permanently). The cause was an {@code instanceof SlabBlock} reject in the seat
     * resolver's full-height arm — a class test where a top-face test belonged; see
     * {@code SlabSupport.cellTopSupportDy}. Nothing in THIS method changed: it captures what
     * {@code getYOffset} says, and {@code getYOffset} now says the right thing.
     */
    private static void recordPlacementDy(World world, BlockPos pos, BlockState state) {
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy < -1.0e-6) {
            SlabPlacementDyAttachment.record(world, pos, dy);
        }
    }

    /**
     * FREEZE-ON-PLACE (LAW 1, {@code LAW.md} — a placed block must stay in that spot and not
     * autonomously pop): locks the FLAT half of a placement's height at the moment it is placed. Server-side
     * only; called from {@code BlockOnPlacedAnchorMixin.onPlaced} after {@link #addAnchor}.
     *
     * <p>If a piece is placed FLAT (dy ≈ 0) it records a {@link #FROZEN_FLAT_TYPE} marker, so a
     * slab / lowered carrier placed under or beside it later can no longer pull it down (the exact
     * live-reported down-pop: a slab placed beside a log pulled the log down). No-op for
     * pieces that must keep tracking a support ABOVE them, and for pieces already anchored or
     * frozen. Natural / setBlockState blocks never call onPlaced, so terrain stays fully geometric.
     *
     * <p><b>BOTH HALVES OF THE LAW LIVE HERE NOW (2026-08-06).</b> A placement that resolves LOWERED
     * and earned no anchor from any of {@link #addAnchor}'s qualifier lanes records its height in
     * {@link SlabPlacementDyAttachment} instead — no anchor, just the number. The header below
     * explains why the two are not the same thing; {@code LAW.md} lane B is the cell that proves
     * the anchor lanes do not cover every lowered placement.
     *
     * <p><b>ELIGIBILITY IS BEHAVIOUR, NOT BLOCK CLASS (LAW 2, 2026-08-06).</b> This gate used to be
     * an allow-list of block TYPES — ordinary anchor candidate, {@link SlabBlock},
     * {@link BlockEntityProvider} — and the sentence above it already described, almost verbatim,
     * the failure a candle was being excluded from. S-2's {@code candle_placed_flat_then_neighbored}
     * proved it live: a candle placed flat read {@code 0.0}, a lowering source appeared beside its
     * support, and the candle went to {@code -0.5} because nothing had recorded that it was placed
     * flat. The intent always covered every flat placement; only the list did not. The seventh
     * exclude-by-classname of this campaign, and the standing rule says express the rule as what
     * the block DOES.
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
        // A captured BlockItem placement is finalized by the atomic placement transaction after
        // vanilla has published every final cell. Preserve the older hook for direct/non-BlockItem
        // callers, but do not let it create a second height authority inside that transaction.
        if (SlabPlacementDyAttachment.blockItemTransactionActive()) {
            return;
        }
        if (isAnchored(world, pos) || isFrozenFlat(world, pos)) {
            return;
        }
        if (hangsFromTheCellAbove(world, pos, state) || heightIsSharedWithACellThisHookNeverSees(state)) {
            return;
        }
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy < -1.0e-6) {
            // THE LOWERED HALF, for the cells the anchor lanes miss. Reaching this line already
            // proves addAnchor declined every one of its qualifier lanes (the guard above returns
            // for an anchored cell), so this placement renders LOWERED with nothing recording how
            // far — the exact shape LAW.md lane B names and S-2's
            // cantilever_full_block_beside_minus_one proves: an ordinary full block placed beside a
            // -1.0 owner is lowered by a boolean adjacency check, while
            // qualifiesForAdjacentLoweredFullBlockAnchor demands its neighbour read EXACTLY -0.5,
            // so the cell holds -0.5 only while that neighbour stands and pops to 0.0 the moment it
            // is broken.
            //
            // Record the placed height. NO ANCHOR IS ADDED, deliberately: an anchor is PRESENCE,
            // and presence is what the column walks and the adjacency checks read as "this cell is
            // a lowering source" (hasLoweringSourceInColumnBelow, isAdjacentToLoweredSupport,
            // isLoweredSideSlabSource, hasLoweredNonSlabTopSupport, the gap-fill lane, the step-cull
            // dispatch, and qualifiesForBelowAnchoredBlockAnchor). Widening what earns an anchor has
            // caused a Terrain Slabs over-lowering regression on this line twice, and the broader
            // entry for it is still OPEN (tracked internally). A stored NUMBER has exactly
            // one consumer — getYOffsetInner's stored-height branch — and answers only for this
            // cell, so it cannot spread to a neighbour or up a column.
            //
            // Nothing here decides WHAT height the cell gets; getYOffset already did, one line
            // above, from the surroundings, which is the single moment LAW 1 allows the
            // surroundings a vote. This only writes it down.
            SlabPlacementDyAttachment.record(world, pos, dy);
            return;
        }
        // dy ≈ 0: lock the FLAT height of this placement so a slab / lowered carrier placed under
        // or beside it later can no longer pull it down.
        WorldChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return;
        }
        LongOpenHashSet existing = chunk.getAttached(FROZEN_FLAT_TYPE);
        LongOpenHashSet set = existing == null ? new LongOpenHashSet() : new LongOpenHashSet(existing);
        if (set.add(pos.asLong())) {
            // setAttached triggers persistence + auto-sync for synced attachments.
            SlabPlacementDyAttachment.noteLegacyFlatPublication();
            chunk.setAttached(FROZEN_FLAT_TYPE, set);
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] frozen_flat add pos={} chunk={} setSize={}",
                        pos.toShortString(), chunk.getPos(), set.size());
            }
        }
    }

    /**
     * THE ONE PRINCIPLED EXCLUSION from freeze-on-place: does this block, <em>in this state, right
     * now</em>, hang from the cell ABOVE it?
     *
     * <p>Such a piece must keep following that support — the standing project ruling that a hanging
     * lantern goes down when the block it hangs from goes down — so pinning it to the height it
     * happened to have at placement would be wrong. This is the exclusion the old block-class
     * allow-list was reaching for and kept missing in both directions: it excluded a FLOOR lever, a
     * standing chain and a candle (none of which hang from anything) while it had no way to say
     * what it actually meant.
     *
     * <p>{@link SlabSupport#isCeilingAttached} is a ROLE predicate as of {@code 3a7c17c0} — it asks
     * the block's own state (a lantern's {@code HANGING}, a lever's {@code BLOCK_FACE}, a bell's
     * {@code ATTACHMENT}, a stalactite's {@code VERTICAL_DIRECTION}) and, for the two families
     * vanilla gives no such property, the world above — so it can carry this exclusion honestly.
     *
     * <p>{@link SlabSupport#isAlwaysCeilingHungDecoration} is NOT subsumed by it and is still
     * needed: pale hanging moss is an intrinsic hanger that carries no {@code HANGING} property and
     * is not one of the classes the role predicate answers for, so the role predicate returns false
     * for it. The two are asked together, never one instead of the other.
     */
    private static boolean hangsFromTheCellAbove(World world, BlockPos pos, BlockState state) {
        return SlabSupport.isCeilingAttached(world, pos, state)
                || SlabSupport.isAlwaysCeilingHungDecoration(state);
    }

    /**
     * The second exclusion, and a KNOWN GAP stated rather than half-fixed: a block whose resolved
     * height is shared with a companion cell that this hook never sees.
     *
     * <p>{@code onPlaced} fires once, for the clicked cell. A bed's HEAD is written by
     * {@code BedBlock.onPlaced} <em>after</em> it calls {@code super.onPlaced}, so at this instant
     * the companion cell does not exist yet; and {@code SlabSupport.shouldOffset} resolves BOTH
     * halves of a bed from EITHER half's column. Marking the half we can see would therefore split
     * the pair — foot at 0.0, head at -0.5 — which is a worse defect than the one being closed.
     *
     * <p>{@code DOUBLE_BLOCK_HALF} is listed with it for the same reason even though no vanilla
     * member reaches here today: {@code DoorBlock.onPlaced} and {@code TallPlantBlock.onPlaced}
     * both write their upper half WITHOUT calling {@code super.onPlaced} (verified against the
     * 1.21.11 bytecode), so this hook never runs for them at all. Freezing a multi-cell piece
     * properly means marking every cell it occupies; that is a separate change and is recorded as
     * a gap, not attempted here.
     */
    private static boolean heightIsSharedWithACellThisHookNeverSees(BlockState state) {
        return state.contains(Properties.BED_PART) || state.contains(Properties.DOUBLE_BLOCK_HALF);
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
        // The stored magnitude belongs to the anchor and dies with it: the cell is being emptied or
        // handed to a piece that must be measured from scratch.
        SlabPlacementDyAttachment.clear(world, pos);
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

    /**
     * Placement-time twin of {@code SlabSupport.isAdjacentToLoweredSupport} (the live render-time
     * check) — decides whether the PERSISTED anchor should be recorded for an ordinary/connecting
     * subject placed beside a lowered neighbour. Must accept the SAME neighbour categories as the
     * live check (solid full block, connecting-structural, or block entity) or the anchor and the
     * render can disagree: the render stays correct on every call (recomputed live), but the
     * subject would never-pop-protect only against neighbours the live check would recognise —
     * without a persisted anchor it can still un-lower if a DIFFERENT, unrecognised change occurs
     * later. Widened together with the live check for the same live bugs (fence/pane and hopper/
     * chest chained beside a lowered neighbour of a different structural family).
     */
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
            boolean solidNeighbor = neighbor.isSolidBlock(world, neighborPos);
            boolean connectingNeighbor = isConnectingStructural(neighbor);
            boolean blockEntityNeighbor = neighbor.getBlock() instanceof BlockEntityProvider;
            if (!solidNeighbor && !connectingNeighbor && !blockEntityNeighbor) {
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

    /**
     * Anchors a lowered BLOCK-ENTITY block (hopper, chest, furnace, barrel, dispenser, ...) at
     * placement so it stops re-deriving its dy from the live column below — the reported "hopper
     * places too high, then snaps down when a block is placed underneath". Block entities are
     * rejected by {@link #isOrdinaryAnchorCandidate} (the ordinary lanes) yet DO lower onto slabs
     * ({@code isSlabSitCandidate} accepts every {@code BlockEntityProvider}), so their -0.5 came
     * ONLY from a live column walk that toggled whenever the cell below changed. Ceiling-hung
     * block entities (hanging signs) are excluded: they hang from ABOVE and must keep following
     * their support (getYOffsetInner also dispatches them before the anchor branch).
     */
    private static boolean qualifiesForBlockEntityLoweredAnchor(World world, BlockPos pos, BlockState state) {
        if (state == null || state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (!(state.getBlock() instanceof BlockEntityProvider)) {
            return false;
        }
        if (SlabSupport.isAlwaysCeilingHungDecoration(state)) {
            return false;
        }
        // Only lock a genuinely-lowered placement; a flat BE is covered by freezeLoweredOnPlace.
        return SlabSupport.getYOffset(world, pos, state) < -1.0e-6;
    }

    /**
     * Anchors a lowered DECORATIVE object (candle, trapdoor, button, repeater, comparator, rail,
     * ...) resting ON TOP of a lowered support at placement, so it stops re-deriving its dy
     * purely from a live read of that support — live-reported "pop upon breaking at the end" for
     * a candle on a slab and a trapdoor on a fence, both {@code anchor=none} their entire
     * lifetime, both popping from -0.5 to 0.0 the instant their support below was broken. These
     * subjects are non-solid and non-connecting, so {@link #isOrdinaryAnchorCandidate} rejects
     * them, and they are not a {@link SlabBlock} or {@link BlockEntityProvider}, so none of the
     * other qualifier lanes apply either — they had NO anchor coverage at all, unlike every other
     * subject category.
     *
     * <p>Ceiling-attached / always-ceiling-hung decorations (a lantern hanging under a support, a
     * hung chain, hanging signs, spore blossom, ...) are explicitly excluded: their whole design is
     * to keep DYNAMICALLY following the support above them (see
     * {@code SlabSupport#isLoweredUndersideHangerOwner}), so freezing them at a placement-time
     * anchor would be wrong — if their support's own dy later changes, they must follow it, not
     * stay pinned to the old value.
     *
     * <p><b>That exclusion is a ROLE test, not a classname test</b> ({@code isCeilingAttached} now
     * takes world context). A FLOOR lever, a FLOOR button, a STANDING Y-axis chain and a TOP-half
     * trapdoor with open air above it hang from nothing at all; they used to match the old
     * block-TYPE list, lose their anchor, and pop the moment the block below them was broken (S-2's
     * `chain_on_lowered_support_ceiling_scenery`). They now reach this qualifier and lock, exactly
     * like the candle and the fence trapdoor above.
     */
    private static boolean qualifiesForDecorativeObjectAnchor(BlockView world, BlockPos pos, BlockState state) {
        if (state == null || state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.getBlock() instanceof SlabBlock || state.getBlock() instanceof BlockEntityProvider) {
            return false;
        }
        if (isOrdinaryAnchorCandidate(world, pos, state)) {
            return false;
        }
        if (SlabSupport.isCeilingAttached(world, pos, state)
                || SlabSupport.isAlwaysCeilingHungDecoration(state)) {
            return false;
        }
        return SlabSupport.getYOffset(world, pos, state) < -1.0e-6;
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
