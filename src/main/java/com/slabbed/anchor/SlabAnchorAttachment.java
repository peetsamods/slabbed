package com.slabbed.anchor;

import java.util.function.Predicate;
import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.RuntimeDiagnostics;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Persistent slab-anchor registry.
 *
 * <p>When an ordinary full block is placed directly on a bottom slab, or on an
 * ordinary full-block chain that is already lowered by a slab anchor, that placement is
 * recorded as an anchor on the chunk so the block keeps its lowered dy even if the
 * support below is later removed. Anchors are cleared when the anchored block itself is
 * broken/replaced.
 *
 * <p>Storage: per-{@link LevelChunk} Forge capability store of packed
 * {@link BlockPos} longs. Client sync is intentionally a later Forge slice.
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
    // Read once at class-load: this flag is queried per block on the render path
    // (OffsetBlockStateModel -> logCompoundVisibleRenderTraceModelDy). Launch -D flag, so caching is
    // exact and removes a per-block Boolean.getBoolean (system-properties lock). Mirrors TRACE above.
    private static final boolean BETA4_COMPOUND_VISIBLE_RENDER_TRACE =
            Boolean.getBoolean(BETA4_COMPOUND_VISIBLE_RENDER_TRACE_PROPERTY);

    /**
     * Client-side fallback for anchor queries issued by chunk render paths that
     * receive a non-{@link Level} {@link BlockGetter}
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

    public static final SlabAnchorMarker ANCHOR_TYPE = SlabAnchorMarker.ANCHOR;
    /**
     * FREEZE-ON-PLACE flat marker: a structural piece (full block / slab) placed at
     * dy=0 is recorded here so its flat height locks — support placed under or beside
     * it later can no longer pull it down. The "never autonomously moves" companion of
     * {@link #ANCHOR_TYPE} (which locks the lowered case). Read as dy=0 by
     * {@code getYOffsetInner}; cleared when the piece is broken.
     */
    public static final SlabAnchorMarker FROZEN_FLAT_TYPE = SlabAnchorMarker.FROZEN_FLAT;
    public static final SlabAnchorMarker LOWERED_SLAB_CARRIER_TYPE = SlabAnchorMarker.LOWERED_SLAB_CARRIER;
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
    public static final SlabAnchorMarker COMPOUND_FULL_BLOCK_ANCHOR_TYPE =
            SlabAnchorMarker.COMPOUND_FULL_BLOCK_ANCHOR;
    public static final SlabAnchorMarker COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE =
            SlabAnchorMarker.COMPOUND_VISIBLE_SIDE_LOWER_SLAB;
    public static final SlabAnchorMarker COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE =
            SlabAnchorMarker.COMPOUND_VISIBLE_SIDE_UPPER_SLAB;
    public static final SlabAnchorMarker COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE =
            SlabAnchorMarker.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB;
    public static final SlabAnchorMarker COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE =
            SlabAnchorMarker.COMPOUND_VISIBLE_OWNER_TOP_SLAB;

    /**
     * Registers the Forge anchor store capability before any chunk loads.
     */
    public static void register(net.minecraftforge.eventbus.api.IEventBus modEventBus) {
        SlabAnchorCapabilities.register(modEventBus);
    }

    private static SlabAnchorStore getAnchorStore(LevelChunk chunk) {
        return chunk.getCapability(SlabAnchorCapabilities.SLAB_ANCHOR_STORE).resolve().orElse(null);
    }

    private static LongOpenHashSet getAttachment(
            LevelChunk chunk,
            SlabAnchorMarker type
    ) {
        SlabAnchorStore store = getAnchorStore(chunk);
        return store == null ? null : store.copy(type);
    }

    private static void setAttachment(
            LevelChunk chunk,
            SlabAnchorMarker type,
            LongOpenHashSet set
    ) {
        SlabAnchorStore store = getAnchorStore(chunk);
        if (store != null) {
            store.replace(type, set);
        }
    }

    private static void removeAttachment(
            LevelChunk chunk,
            SlabAnchorMarker type
    ) {
        SlabAnchorStore store = getAnchorStore(chunk);
        if (store != null) {
            store.clear(type);
        }
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
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
                    shortPos(pos), state, qualifies);
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
        if (added && RuntimeDiagnostics.isBsFbLiveTraceEnabled()) {
            BlockPos supportPos = pos.below();
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
     * NEVER autonomously moves afterwards. Julia's law — "a placed block must stay in
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
    private static final ThreadLocal<BlockPos> WYSIWYG_FOLLOW_CLICKED_LOWERED_FACE = new ThreadLocal<>();

    public static void markWysiwygFollowClickedLoweredFace(BlockPos placedPos) {
        WYSIWYG_FOLLOW_CLICKED_LOWERED_FACE.set(placedPos == null ? null : placedPos.immutable());
    }

    public static void clearWysiwygFollowClickedLoweredFace() {
        WYSIWYG_FOLLOW_CLICKED_LOWERED_FACE.remove();
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
        if (world == null || world.isClientSide() || pos == null || state == null
                || state.isAir() || !state.getFluidState().isEmpty()) {
            return;
        }
        if (isAnchored(world, pos) || isFrozenFlat(world, pos)) {
            return;
        }
        if ((state.getBlock() instanceof SlabBlock || SlabSupport.isBeta35FenceWallVariantContactObject(state))
                && consumeWysiwygFollowClickedLoweredFace(pos)) {
            addAnchorUnchecked(world, pos);
            return;
        }
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy < -1.0e-6) {
            // addAnchorUnchecked records ANCHOR_TYPE (read as -0.5) and adds the compound
            // sidecar (-1.0) when the piece qualifies, so both lowered lanes freeze.
            addAnchorUnchecked(world, pos);
            return;
        }
        // dy ≈ 0: lock the FLAT height of a STRUCTURAL piece (ordinary full block, slab,
        // or connector post) so a slab / lowered carrier placed under or beside it later
        // can no longer pull it down. Gated to structural pieces so decorative followers
        // (lanterns/torches/hangers/signs) keep tracking their supports. Non-structural
        // and natural (terrain / setBlockState, which never call onPlaced) pieces stay
        // fully geometric.
        boolean structural = isOrdinaryFullBlockAnchorCandidate(world, pos, state)
                || state.getBlock() instanceof SlabBlock
                || SlabSupport.isBeta35FenceWallVariantContactObject(state);
        if (structural) {
            addToAttachment(world, pos, FROZEN_FLAT_TYPE, "frozen_flat");
        }
    }

    /**
     * Returns true if {@code pos} carries a freeze-on-place FLAT marker — a structural
     * piece whose height was locked at 0 when placed. Safe on server and client (client
     * mirror via {@link #clientFrozenFlatLookup}); false for non-{@link Level} views.
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
        LongOpenHashSet set = getAttachment(chunk, FROZEN_FLAT_TYPE);
        return set != null && set.contains(pos.asLong());
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
            world.neighborShapeChanged(Direction.DOWN, state, pos.above(), pos, Block.UPDATE_ALL, 512);
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
            world.neighborShapeChanged(Direction.DOWN, state, pos.above(), pos, Block.UPDATE_ALL, 512);
        }
    }

    public static void updatePersistentLoweredSlabCarrier(Level world, BlockPos pos, BlockState state) {
        if (world == null || world.isClientSide()) {
            return;
        }
        boolean qualifies = qualifiesForPersistentLoweredSlabCarrier(world, pos, state);
        if (TRACE) {
            Slabbed.LOGGER.info("[ANCHOR] lowered slab carrier update side=SERVER pos={} state={} qualifies={}",
                    shortPos(pos), state, qualifies);
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
            SlabAnchorMarker type,
            String label
    ) {
        LevelChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] {} add reject pos={} reason=chunk_null", label, shortPos(pos));
            }
            return false;
        }
        LongOpenHashSet existing = getAttachment(chunk, type);
        LongOpenHashSet set = existing == null ? new LongOpenHashSet() : new LongOpenHashSet(existing);
        BlockState stateBefore = RuntimeDiagnostics.beta35SlabJumpSourceTruthEnabled()
                ? world.getBlockState(pos) : null;
        if (set.add(pos.asLong())) {
            // setData marks the chunk unsaved and triggers auto-sync for synced attachments.
            setAttachment(chunk, type, set);
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] {} add success pos={} chunk={} setSize={}",
                        label, shortPos(pos), chunk.getPos(), set.size());
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
    public static void removeAnchor(Level world, BlockPos pos) {
        boolean removed = removeFromAttachment(world, pos, ANCHOR_TYPE, "anchor");
        if (removed && RuntimeDiagnostics.isBsFbLiveTraceEnabled()) {
            BlockPos supportPos = pos.below();
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
    }

    public static void removePersistentLoweredSlabCarrier(Level world, BlockPos pos) {
        removeFromAttachment(world, pos, LOWERED_SLAB_CARRIER_TYPE, "lowered_slab_carrier");
    }

    private static boolean removeFromAttachment(
            Level world,
            BlockPos pos,
            SlabAnchorMarker type,
            String label
    ) {
        if (world == null || world.isClientSide()) {
            return false;
        }
        LevelChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet existing = getAttachment(chunk, type);
        if (existing == null || existing.isEmpty()) {
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] {} remove pos={} existed=false", label, shortPos(pos));
            }
            return false;
        }
        LongOpenHashSet set = new LongOpenHashSet(existing);
        boolean removed = set.remove(pos.asLong());
        if (TRACE) {
            Slabbed.LOGGER.info("[ANCHOR] {} remove pos={} existed={}", label, shortPos(pos), removed);
        }
        if (removed) {
            if (set.isEmpty()) {
                removeAttachment(chunk, type);
            } else {
                setAttachment(chunk, type, set);
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

    public static boolean isCompoundVisibleAttachmentType(SlabAnchorMarker type) {
        return type == COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE
                || type == COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE
                || type == COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE
                || type == COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE;
    }

    public static String compoundVisibleAttachmentLabel(SlabAnchorMarker type) {
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
            SlabAnchorMarker type,
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
                shortPos(pos),
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
                shortPos(pos),
                qualifies,
                dy);
    }

    // ── shared query ──────────────────────────────────────────────────

    /**
     * Returns true if {@code pos} carries a persistent slab-anchor.
     *
     * <p>Safe on both server and client (client mirror is populated via attachment sync).
     * Returns false for any {@link BlockGetter} that is not a full {@link Level}, so it is
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
        LongOpenHashSet set = getAttachment(chunk, ANCHOR_TYPE);
        boolean anchored = set != null && set.contains(pos.asLong());
        if (TRACE && anchored) {
            Slabbed.LOGGER.info("[ANCHOR] query true side={} pos={}",
                    w.isClientSide() ? "CLIENT" : "SERVER", shortPos(pos));
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
        LongOpenHashSet set = getAttachment(chunk, COMPOUND_FULL_BLOCK_ANCHOR_TYPE);
        boolean compound = set != null && set.contains(pos.asLong());
        if (TRACE && compound) {
            Slabbed.LOGGER.info("[ANCHOR] compound_full_block query true side={} pos={}",
                    w.isClientSide() ? "CLIENT" : "SERVER", shortPos(pos));
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
        LongOpenHashSet set = getAttachment(chunk, COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE);
        boolean marked = set != null && set.contains(pos.asLong());
        if (TRACE && marked) {
            Slabbed.LOGGER.info("[ANCHOR] compound_visible_side_lower_slab query true side={} pos={}",
                    w.isClientSide() ? "CLIENT" : "SERVER", shortPos(pos));
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
        LongOpenHashSet set = getAttachment(chunk, COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE);
        boolean marked = set != null && set.contains(pos.asLong());
        if (TRACE && marked) {
            Slabbed.LOGGER.info("[ANCHOR] compound_visible_side_upper_slab query true side={} pos={}",
                    w.isClientSide() ? "CLIENT" : "SERVER", shortPos(pos));
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
        LongOpenHashSet set = getAttachment(chunk, COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE);
        boolean marked = set != null && set.contains(pos.asLong());
        if (TRACE && marked) {
            Slabbed.LOGGER.info("[ANCHOR] compound_visible_side_double_slab query true side={} pos={}",
                    w.isClientSide() ? "CLIENT" : "SERVER", shortPos(pos));
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
        LongOpenHashSet set = getAttachment(chunk, COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE);
        boolean marked = set != null && set.contains(pos.asLong());
        if (TRACE && marked) {
            Slabbed.LOGGER.info("[ANCHOR] compound_visible_owner_top_slab query true side={} pos={}",
                    w.isClientSide() ? "CLIENT" : "SERVER", shortPos(pos));
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
        LongOpenHashSet set = getAttachment(chunk, LOWERED_SLAB_CARRIER_TYPE);
        boolean carrier = set != null && set.contains(pos.asLong());
        if (!carrier && isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state)) {
            carrier = true;
        }
        if (TRACE && carrier) {
            Slabbed.LOGGER.info("[ANCHOR] lowered slab carrier query true side={} pos={}",
                    w.isClientSide() ? "CLIENT" : "SERVER", shortPos(pos));
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
                LongOpenHashSet set = getAttachment(chunk, LOWERED_SLAB_CARRIER_TYPE);
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
        if (block instanceof CarpetBlock || isPaleMossCarpet(block)) {
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
        return isOrdinaryFullBlockAnchorCarrierBounds(world, pos, state);
    }

    private static boolean isPaleMossCarpet(Block block) {
        return block == BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecraft", "pale_moss_carpet"));
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
        if (!isCompoundVisibleSideSource(world, sourcePos, sourceState)) {
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
        if (!isCompoundVisibleSideSource(world, sourcePos, sourceState)) {
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
        if (!isCompoundVisibleSideSource(world, sourcePos, sourceState)) {
            return false;
        }
        int dx = Math.abs(pos.getX() - sourcePos.getX());
        int dy = Math.abs(pos.getY() - sourcePos.getY());
        int dz = Math.abs(pos.getZ() - sourcePos.getZ());
        return dy == 0 && dx + dz == 1;
    }

    private static boolean isCompoundVisibleSideSource(
            BlockGetter world,
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
        return SlabSupport.isCompoundVisibleFullBlockSource(world, sourcePos, sourceState);
    }

    public static boolean qualifiesForPersistentLoweredSlabCarrier(BlockGetter world, BlockPos pos, BlockState state) {
        return isPersistentLoweredSlabCarrierState(state)
                && !isCompoundVisibleOwnerTopSlab(world, pos, state)
                && (SlabSupport.isLoweredSideLaneSlabCarrier(world, pos, state)
                || SlabSupport.hasLoweredSideLaneCarrierAuthoringSupport(world, pos, state)
                || qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlock(world, pos, state)
                || qualifiesForPersistentLoweredBottomSlabOnAdjacentLoweredBridgeSupport(world, pos, state));
    }

    public static boolean isLoweredFullBlockSlabCarrierSupport(BlockGetter world, BlockPos pos, BlockState state) {
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
                && state.getBlock() instanceof SlabBlock
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
        return isLoweredFullBlockSlabCarrierSupport(world, belowPos, below);
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
            BlockGetter world,
            BlockPos pos,
            BlockState state,
            double expectedDy
    ) {
        if (world == null || pos == null || !isNonSlabNonFluidCarrierSupportState(state)) {
            return false;
        }
        if (state.isSolidRender(world, pos)) {
            return true;
        }
        return hasFullHeightCarrierBounds(world, pos, state, expectedDy);
    }

    private static boolean isOrdinaryFullBlockAnchorCarrierBounds(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        if (world == null || pos == null || !isNonSlabNonFluidCarrierSupportState(state)) {
            return false;
        }
        if (state.isSolidRender(world, pos)) {
            return true;
        }
        // Candidate checks can run after Slabbed has shifted non-solid full-height
        // carriers into their visible lane; accept only the named legal anchor lanes.
        return hasFullHeightCarrierBounds(world, pos, state, 0.0d)
                || hasFullHeightCarrierBounds(world, pos, state, -0.5d)
                || hasFullHeightCarrierBounds(world, pos, state, -1.0d);
    }

    private static boolean hasFullHeightCarrierBounds(
            BlockGetter world,
            BlockPos pos,
            BlockState state,
            double expectedDy
    ) {
        VoxelShape outline = state.getShape(world, pos);
        if (outline == null || outline.isEmpty()) {
            return false;
        }
        AABB bounds = outline.bounds();
        return near(bounds.minX, 0.0d)
                && near(bounds.maxX, 1.0d)
                && near(bounds.minZ, 0.0d)
                && near(bounds.maxZ, 1.0d)
                && (unitYAt(bounds, 0.0d) || unitYAt(bounds, expectedDy));
    }

    private static boolean unitYAt(AABB bounds, double minY) {
        return near(bounds.minY, minY) && near(bounds.maxY, minY + 1.0d);
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) <= 1.0e-6d;
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
        for (var dir : Direction.Plane.HORIZONTAL) {
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
        for (var dir : Direction.Plane.HORIZONTAL) {
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
