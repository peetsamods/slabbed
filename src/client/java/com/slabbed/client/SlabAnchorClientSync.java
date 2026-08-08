package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Schedules a client-side block rerender for every anchored/lowered-carrier
 * position when synced chunk attachments change on the client.
 *
 * <p>Without this, the first chunk render may run before the attachment sync
 * packet arrives, causing {@code SlabSupport.getYOffset} to return 0.0 (no
 * anchor yet) and leaving the block visually un-lowered.  When the attachment
 * packet arrives moments later no rerender is otherwise triggered, so the
 * visual stays stale until something else forces a section rebuild.
 *
 * <p>Fix: hook {@code AttachmentTarget.onAttachedSet} on every newly-loaded
 * client chunk and refresh each position in the union of the old and new
 * anchor sets.
 */
@Environment(EnvType.CLIENT)
public final class SlabAnchorClientSync {

    private static final ThreadLocal<Integer> JOURNAL_AUTHORITATIVE_APPLY_DEPTH =
            ThreadLocal.withInitial(() -> 0);
    private static boolean c3RerenderProbeEnabled;
    private static final Map<Long, Integer> C3_RERENDERS_FOR_TESTS = new HashMap<>();

    private SlabAnchorClientSync() {
    }

    public static void init() {
        // Bridge: chunk render paths receive ChunkRendererRegion (not a World) and cannot
        // access chunk attachments.  Register a client-world fallback so anchor queries
        // from the model render path resolve correctly after a supporting BS is broken.
        SlabAnchorAttachment.clientAnchorLookup = pos -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) {
                return false;
            }
            LevelChunk chunk = mc.level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                return false;
            }
            LongOpenHashSet set = chunk.getAttached(SlabAnchorAttachment.ANCHOR_TYPE);
            return set != null && set.contains(pos.asLong());
        };
        SlabAnchorAttachment.clientLoweredSlabCarrierLookup = pos -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) {
                return false;
            }
            LevelChunk chunk = mc.level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                return false;
            }
            LongOpenHashSet set = chunk.getAttached(SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE);
            return set != null && set.contains(pos.asLong());
        };
        SlabAnchorAttachment.clientCompoundFullBlockAnchorLookup = pos -> {
            LongOpenHashSet set = clientAttachmentSet(pos, SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE);
            return set != null && set.contains(pos.asLong());
        };
        SlabAnchorAttachment.clientCompoundVisibleSideLowerSlabLookup = pos -> {
            LongOpenHashSet set = clientAttachmentSet(pos, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE);
            return set != null && set.contains(pos.asLong());
        };
        SlabAnchorAttachment.clientCompoundVisibleSideUpperSlabLookup = pos -> {
            LongOpenHashSet set = clientAttachmentSet(pos, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE);
            return set != null && set.contains(pos.asLong());
        };
        SlabAnchorAttachment.clientCompoundVisibleSideDoubleSlabLookup = pos -> {
            LongOpenHashSet set = clientAttachmentSet(pos, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE);
            return set != null && set.contains(pos.asLong());
        };
        SlabAnchorAttachment.clientCompoundVisibleOwnerTopSlabLookup = pos -> {
            LongOpenHashSet set = clientAttachmentSet(pos, SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE);
            return set != null && set.contains(pos.asLong());
        };
        // Freeze-on-place FLAT marker: same client-world fallback so the model render path sees the
        // frozen-flat state and keeps the piece at dy=0 (no autonomous lowering after reload).
        SlabAnchorAttachment.clientFrozenFlatLookup = pos -> {
            LongOpenHashSet set = clientAttachmentSet(pos, SlabAnchorAttachment.FROZEN_FLAT_TYPE);
            return set != null && set.contains(pos.asLong());
        };
        SlabAnchorAttachment.clientBackingPlacementDyLookup = PlacementDyPredictionJournal::backingFact;
        SlabAnchorAttachment.clientEffectivePlacementDyLookup = PlacementDyPredictionJournal::effectiveFact;

        ClientChunkEvents.CHUNK_LOAD.register(SlabAnchorClientSync::onChunkLoad);
    }

    private static void onChunkLoad(net.minecraft.client.multiplayer.ClientLevel world, LevelChunk chunk) {
        logReloadJumpSync("chunkLoad", chunk, SlabAnchorAttachment.ANCHOR_TYPE, null,
                chunk.getAttached(SlabAnchorAttachment.ANCHOR_TYPE));
        logReloadJumpSync("chunkLoad", chunk, SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE, null,
                chunk.getAttached(SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE));
        logReloadJumpSync("chunkLoad", chunk, SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE, null,
                chunk.getAttached(SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE));
        logReloadJumpSync("chunkLoad", chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE, null,
                chunk.getAttached(SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE));
        logReloadJumpSync("chunkLoad", chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE, null,
                chunk.getAttached(SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE));
        logReloadJumpSync("chunkLoad", chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE, null,
                chunk.getAttached(SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE));
        logReloadJumpSync("chunkLoad", chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE, null,
                chunk.getAttached(SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE));

        // Register listener for future attachment changes (e.g. live anchor add/remove sync).
        registerRerenderListener(chunk, SlabAnchorAttachment.ANCHOR_TYPE);
        registerRerenderListener(chunk, SlabAnchorAttachment.FROZEN_FLAT_TYPE);
        registerRerenderListener(chunk, SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE);
        registerRerenderListener(chunk, SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE);
        registerRerenderListener(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE);
        registerRerenderListener(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE);
        registerRerenderListener(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE);
        registerRerenderListener(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE);
        registerDyRerenderListener(chunk);

        // Also handle any attachment value already present at chunk-load time.
        // This covers the case where the chunk attachment sync packet arrived before
        // CHUNK_LOAD (or together with the initial chunk data), so onAttachedSet never
        // fires but the attachment is already populated.
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.ANCHOR_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.FROZEN_FLAT_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE);
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null) {
            scheduleRerendersForDyMap(mc, chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE));
        }
    }

    private static LongOpenHashSet clientAttachmentSet(
            BlockPos pos,
            AttachmentType<LongOpenHashSet> attachmentType
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || pos == null) {
            return null;
        }
        LevelChunk chunk = mc.level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getAttached(attachmentType);
    }

    private static void registerRerenderListener(
            LevelChunk chunk,
            AttachmentType<LongOpenHashSet> attachmentType
    ) {
        chunk.<LongOpenHashSet>onAttachedSet(attachmentType).register((oldSet, newSet) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.levelRenderer == null) {
                return;
            }
            logReloadJumpSync("attachedSet", chunk, attachmentType, oldSet, newSet);
            scheduleRerendersForSet(mc, oldSet, attachmentType);
            scheduleRerendersForSet(mc, newSet, attachmentType);
        });
    }

    /** FROZEN-DY (Step 0): re-mesh a section when its stored placement heights sync, like the flags. */
    private static void registerDyRerenderListener(LevelChunk chunk) {
        chunk.<it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap>onAttachedSet(SlabAnchorAttachment.PLACEMENT_DY_TYPE)
                .register((oldMap, newMap) -> {
                    if (JOURNAL_AUTHORITATIVE_APPLY_DEPTH.get() > 0) {
                        return;
                    }
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.levelRenderer == null) {
                        return;
                    }
                    scheduleRerendersForDyDiff(mc, oldMap, newMap);
                });
    }

    static void beginJournalAuthoritativeApply() {
        JOURNAL_AUTHORITATIVE_APPLY_DEPTH.set(JOURNAL_AUTHORITATIVE_APPLY_DEPTH.get() + 1);
    }

    static void endJournalAuthoritativeApply() {
        int remaining = JOURNAL_AUTHORITATIVE_APPLY_DEPTH.get() - 1;
        if (remaining <= 0) {
            JOURNAL_AUTHORITATIVE_APPLY_DEPTH.remove();
        } else {
            JOURNAL_AUTHORITATIVE_APPLY_DEPTH.set(remaining);
        }
    }

    public static void scheduleExactPlacementDyRerender(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.levelRenderer == null || pos == null
                || !mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            return;
        }
        recordC3RerenderForTests(pos);
        scheduleAnchorAttachmentRenderRefresh(mc, pos);
    }

    public static synchronized void beginC3RerenderProbeForTests() {
        c3RerenderProbeEnabled = true;
        C3_RERENDERS_FOR_TESTS.clear();
    }

    public static synchronized void resetC3RerenderProbeCountsForTests() {
        C3_RERENDERS_FOR_TESTS.clear();
    }

    public static synchronized int c3RerenderCountForTests(BlockPos pos) {
        return pos == null ? 0 : C3_RERENDERS_FOR_TESTS.getOrDefault(pos.asLong(), 0);
    }

    public static synchronized int c3RerenderCountForTests() {
        return C3_RERENDERS_FOR_TESTS.values().stream().mapToInt(Integer::intValue).sum();
    }

    public static synchronized void stopC3RerenderProbeForTests() {
        c3RerenderProbeEnabled = false;
        C3_RERENDERS_FOR_TESTS.clear();
    }

    private static synchronized void recordC3RerenderForTests(BlockPos pos) {
        if (c3RerenderProbeEnabled && pos != null) {
            C3_RERENDERS_FOR_TESTS.merge(pos.asLong(), 1, Integer::sum);
        }
    }

    private static void scheduleRerendersForDyDiff(
            Minecraft mc,
            it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap oldMap,
            it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap newMap
    ) {
        LongOpenHashSet keys = new LongOpenHashSet();
        if (oldMap != null) {
            for (long key : oldMap.keySet()) {
                keys.add(key);
            }
        }
        if (newMap != null) {
            for (long key : newMap.keySet()) {
                keys.add(key);
            }
        }
        for (long key : keys) {
            boolean oldPresent = oldMap != null && oldMap.containsKey(key);
            boolean newPresent = newMap != null && newMap.containsKey(key);
            long oldBits = oldPresent ? Double.doubleToRawLongBits(oldMap.get(key)) : 0L;
            long newBits = newPresent ? Double.doubleToRawLongBits(newMap.get(key)) : 0L;
            if (oldPresent != newPresent || oldBits != newBits) {
                scheduleExactPlacementDyRerender(BlockPos.of(key));
            }
        }
    }

    private static void scheduleRerendersForDyMap(Minecraft mc, it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (long packed : map.keySet()) {
            mutable.set(packed);
            BlockPos pos = mutable.immutable();
            if (mc.level != null && mc.level.getBlockState(pos) != null) {
                scheduleAnchorAttachmentRenderRefresh(mc, pos);
            }
        }
    }

    private static void scheduleInitialRerenders(
            LevelChunk chunk,
            AttachmentType<LongOpenHashSet> attachmentType
    ) {
        LongOpenHashSet initial = chunk.getAttached(attachmentType);
        logReloadJumpSync("initialRerenderCheck", chunk, attachmentType, null, initial);
        if (initial != null && !initial.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.levelRenderer != null) {
                scheduleRerendersForSet(mc, initial, attachmentType);
            }
        }
    }

    private static void scheduleRerendersForSet(
            Minecraft mc,
            LongOpenHashSet set,
            AttachmentType<LongOpenHashSet> attachmentType
    ) {
        if (set == null || set.isEmpty()) {
            return;
        }
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (long packed : set) {
            mutable.set(packed);
            BlockPos rerenderPos = mutable.immutable();
            BlockState current = mc.level != null
                    ? mc.level.getBlockState(rerenderPos)
                    : null;
            if (current != null) {
                if (SlabAnchorAttachment.TRACE) {
                    Slabbed.LOGGER.info("[ANCHOR] client rerender pos={} reason=attachment_sync_or_chunk_load",
                            rerenderPos.toShortString());
                }
                logReloadJumpSyncRerender(rerenderPos, current);
                if (SlabAnchorAttachment.isCompoundVisibleAttachmentType(attachmentType)) {
                    scheduleCompoundVisibleRenderRefresh(mc, rerenderPos, current, attachmentType);
                } else {
                    scheduleAnchorAttachmentRenderRefresh(mc, rerenderPos);
                }
            }
        }
    }

    /**
     * Re-mesh for a PLAIN anchor attachment change (the non-{@code COMPOUND_VISIBLE_*} branch:
     * {@link SlabAnchorAttachment#ANCHOR_TYPE}, {@link SlabAnchorAttachment#FROZEN_FLAT_TYPE},
     * {@link SlabAnchorAttachment#LOWERED_SLAB_CARRIER_TYPE},
     * {@link SlabAnchorAttachment#COMPOUND_FULL_BLOCK_ANCHOR_TYPE}).
     *
     * <p>This is the freeze-on-place mesh-staleness fix. The prior implementation called
     * {@code mc.level.setBlocksDirty(pos, current, current)} — but vanilla's
     * {@code ModelManager.requiresRender(a, b)} short-circuits to {@code false} the instant {@code a == b}
     * (BlockStates are interned singletons, so passing the same state twice is reference-equal), making that
     * call a NO-OP. Server-side freeze-on-place ({@code SlabAnchorAttachment.freezeLoweredOnPlace}) records
     * the anchor ~40&nbsp;ms AFTER placement with NO BlockState change — only the attachment (and thus the
     * baked dy) changes — so {@code onAttachedSet(ANCHOR_TYPE)} fires but the old no-op did nothing, leaving
     * the section baked at the pre-anchor height until an unrelated change or Sodium's background sweep
     * eventually rebuilt it (the live-reported "only snaps when a nearby object updates, or after a long
     * delay"). See {@code SlabGeometricRemeshScheduler.anchorAttachmentDirtySectionBox}.
     *
     * <p>Fix: dirty the anchor's own SECTION via the same IMPORTANT-priority path
     * ({@code SlabImportantRemesh.dirtyImportant}) the geometric-remesh mixin and the compound-visible
     * refresh already use, so the section re-bakes to the anchored height within a frame or two of the
     * anchor being set, regardless of whether the BlockState changed. The dirty box comes from the one
     * canonical block→section conversion shared with those paths.
     */
    private static void scheduleAnchorAttachmentRenderRefresh(Minecraft mc, BlockPos pos) {
        SlabGeometricRemeshScheduler.SectionBox box =
                SlabGeometricRemeshScheduler.anchorAttachmentDirtySectionBox(pos.getX(), pos.getY(), pos.getZ());
        SlabImportantRemesh.dirtyImportant(mc.level, box);
    }

    private static void scheduleCompoundVisibleRenderRefresh(
            Minecraft mc,
            BlockPos pos,
            BlockState state,
            AttachmentType<LongOpenHashSet> attachmentType
    ) {
        // The dirty box is in SECTION coords, not block coords (setSectionRangeDirty / the important
        // accessor forward them with no >>4 conversion — confirmed via bytecode). Feeding raw block coords
        // (the pre-4df516ab bug) dirtied a 27-section box ~16x too far from pos — e.g. block x=100 dirtied
        // SECTIONS 99..101 (blocks 1584..1631), nowhere near the actual anchor. Route through the one
        // canonical block->section conversion SlabGeometricRemeshScheduler.compoundVisibleDirtySectionBox,
        // shared with the geometric-remesh mixin, so both re-mesh paths agree on the dirty-region shape and
        // the conversion is pinned by GeometricRemeshSchedulerTest.
        //
        // Issue it as an IMPORTANT rebuild (not via setSectionRangeDirty, which hardcodes important=false):
        // under Sodium important=false defers into the per-frame-budgeted background queue — the
        // ~30-second-snap delay — while important=true reaches the near-immediate presentation path for a
        // near-camera section. See SlabGeometricRemeshScheduler.REQUEST_IMPORTANT_REBUILD.
        SlabGeometricRemeshScheduler.SectionBox box =
                SlabGeometricRemeshScheduler.compoundVisibleDirtySectionBox(pos.getX(), pos.getY(), pos.getZ());
        SlabImportantRemesh.dirtyImportant(mc.level, box);
        logCompoundVisibleRenderRefresh(mc, pos, state, attachmentType);
    }

    private static boolean reloadJumpRecorderEnabled() {
        return Boolean.getBoolean("slabbed.beta4ReloadJumpRecorder");
    }

    private static String labelForAttachment(AttachmentType<LongOpenHashSet> attachmentType) {
        if (attachmentType == SlabAnchorAttachment.ANCHOR_TYPE) {
            return "persistentFullBlockAnchor";
        }
        if (attachmentType == SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE) {
            return "persistentLoweredSlabCarrier";
        }
        if (attachmentType == SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE) {
            return "compoundFullBlockAnchor";
        }
        if (attachmentType == SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE) {
            return "compoundVisibleSideLowerSlab";
        }
        if (attachmentType == SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE) {
            return "compoundVisibleSideUpperSlab";
        }
        if (attachmentType == SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE) {
            return "compoundVisibleSideDoubleSlab";
        }
        if (attachmentType == SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE) {
            return "compoundVisibleOwnerTopSlab";
        }
        return "unknownAttachment";
    }

    private static boolean modelDyRecorderEnabled() {
        return Boolean.getBoolean("slabbed.beta4ModelDyRecorder");
    }

    private static void logReloadJumpSync(
            String event,
            LevelChunk chunk,
            AttachmentType<LongOpenHashSet> attachmentType,
            LongOpenHashSet oldSet,
            LongOpenHashSet newSet
    ) {
        String type = labelForAttachment(attachmentType);
        logCompoundVisibleClientSync(event, type, chunk, attachmentType, oldSet, newSet);
        if (!reloadJumpRecorderEnabled()) {
            logModelDyRerenderEvent(event, type, chunk, oldSet, newSet);
            return;
        }

        Slabbed.LOGGER.info(
                "[BETA4_RELOAD_JUMP_SYNC] event={} type={} chunk={} oldCount={} newCount={} rerenderTriggered={}",
                event,
                type,
                chunk.getPos(),
                oldSet == null ? 0 : oldSet.size(),
                newSet == null ? 0 : newSet.size(),
                newSet != null && !newSet.isEmpty());
        logModelDyRerenderEvent(event, type, chunk, oldSet, newSet);
    }

    private static void logReloadJumpSyncRerender(BlockPos pos, BlockState state) {
        if (!reloadJumpRecorderEnabled()) {
            logModelDyRerenderBlock(pos, state);
            return;
        }

        Slabbed.LOGGER.info(
                "[BETA4_RELOAD_JUMP_SYNC] event=scheduleBlockRerender pos={} state={} rerenderTriggered=true",
                pos.toShortString(),
                state);
        logModelDyRerenderBlock(pos, state);
    }

    private static void logModelDyRerenderEvent(
            String event,
            String type,
            LevelChunk chunk,
            LongOpenHashSet oldSet,
            LongOpenHashSet newSet
    ) {
        if (!modelDyRecorderEnabled()) {
            return;
        }
        Slabbed.LOGGER.info(
                "[BETA4_MODEL_DY_RERENDER] event={} type={} chunk={} oldCount={} newCount={} rerenderTriggered={} watchedInsideChunk={}",
                event,
                type,
                chunk.getPos(),
                oldSet == null ? 0 : oldSet.size(),
                newSet == null ? 0 : newSet.size(),
                newSet != null && !newSet.isEmpty(),
                watchedPositionsInsideChunk(chunk));
    }

    private static void logModelDyRerenderBlock(BlockPos pos, BlockState state) {
        if (!modelDyRecorderEnabled()) {
            return;
        }
        Slabbed.LOGGER.info(
                "[BETA4_MODEL_DY_RERENDER] event=scheduleBlockRerender chunk={},{} pos={} state={} rerenderTriggered=true watchedMatch={}",
                pos.getX() >> 4,
                pos.getZ() >> 4,
                pos.toShortString(),
                state,
                watchedPositionsMatching(pos));
    }

    private static void logCompoundVisibleClientSync(
            String event,
            String type,
            LevelChunk chunk,
            AttachmentType<LongOpenHashSet> attachmentType,
            LongOpenHashSet oldSet,
            LongOpenHashSet newSet
    ) {
        if (!SlabAnchorAttachment.beta4CompoundVisibleRenderTraceEnabled()
                || !SlabAnchorAttachment.isCompoundVisibleAttachmentType(attachmentType)) {
            return;
        }
        boolean oldPresent = oldSet != null && !oldSet.isEmpty();
        boolean newPresent = newSet != null && !newSet.isEmpty();
        if (!oldPresent && !newPresent) {
            return;
        }
        Slabbed.LOGGER.info(
                "[BETA4_COMPOUND_VISIBLE_RENDER_TRACE_CLIENT_SYNC] event={} pos=chunk marker={} serverMarker=n/a clientMarker={} modelViewType=World slabSupportDy=n/a clientDy=n/a candidateRerenderScheduled={} neighborRerenderScheduled={}",
                event,
                type,
                newPresent,
                newPresent,
                newPresent);
    }

    private static void logCompoundVisibleRenderRefresh(
            Minecraft mc,
            BlockPos pos,
            BlockState state,
            AttachmentType<LongOpenHashSet> attachmentType
    ) {
        if (!SlabAnchorAttachment.beta4CompoundVisibleRenderTraceEnabled()) {
            return;
        }
        String marker = SlabAnchorAttachment.compoundVisibleAttachmentLabel(attachmentType);
        boolean clientMarker = compoundVisibleClientMarker(mc, pos, state, attachmentType);
        double slabSupportDy = mc.level == null ? 0.0d : SlabSupport.getYOffset(mc.level, pos, state);
        double clientDy = mc.level == null ? 0.0d : ClientDy.dyFor(mc.level, pos, state);
        Slabbed.LOGGER.info(
                "[BETA4_COMPOUND_VISIBLE_RENDER_TRACE_RERENDER] pos={} marker={} serverMarker=n/a clientMarker={} modelViewType=World slabSupportDy={} clientDy={} candidateRerenderScheduled=true neighborRerenderScheduled=true sourceRerenderScheduled=true",
                pos.toShortString(),
                marker,
                clientMarker,
                slabSupportDy,
                clientDy);
        if (!clientMarker || Math.abs(slabSupportDy + 1.0d) > 1.0e-6d) {
            return;
        }
        Slabbed.LOGGER.info(
                "[BETA4_COMPOUND_VISIBLE_RENDER_TRACE_SUMMARY] classification=RERENDER_NOT_SCHEDULED pos={} marker={} markerSet=clientAttachment clientMarker={} modelDy={} candidateRerenderScheduled=true neighborRerenderScheduled=true releaseBlockers=MaintainerLiveRetest",
                pos.toShortString(),
                marker,
                clientMarker,
                slabSupportDy);
    }

    private static boolean compoundVisibleClientMarker(
            Minecraft mc,
            BlockPos pos,
            BlockState state,
            AttachmentType<LongOpenHashSet> attachmentType
    ) {
        if (mc.level == null) {
            return false;
        }
        if (attachmentType == SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE) {
            return SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(mc.level, pos, state);
        }
        if (attachmentType == SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE) {
            return SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(mc.level, pos, state);
        }
        if (attachmentType == SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE) {
            return SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(mc.level, pos, state);
        }
        if (attachmentType == SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE) {
            return SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(mc.level, pos, state);
        }
        return false;
    }

    private static String watchedPositionsInsideChunk(LevelChunk chunk) {
        String raw = System.getProperty("slabbed.beta4ModelDyRecorderWatch", "");
        if (raw.isBlank()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (String entry : raw.split(";")) {
            BlockPos pos = parseWatchedPos(entry);
            if (pos != null
                    && (pos.getX() >> 4) == chunk.getPos().x()
                    && (pos.getZ() >> 4) == chunk.getPos().z()) {
                if (!builder.isEmpty()) {
                    builder.append('|');
                }
                builder.append(pos.toShortString());
            }
        }
        return builder.isEmpty() ? "none" : builder.toString();
    }

    private static String watchedPositionsMatching(BlockPos rerenderPos) {
        String raw = System.getProperty("slabbed.beta4ModelDyRecorderWatch", "");
        if (raw.isBlank()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (String entry : raw.split(";")) {
            BlockPos pos = parseWatchedPos(entry);
            if (rerenderPos.equals(pos)) {
                if (!builder.isEmpty()) {
                    builder.append('|');
                }
                builder.append(pos.toShortString());
            }
        }
        return builder.isEmpty() ? "none" : builder.toString();
    }

    private static BlockPos parseWatchedPos(String raw) {
        String[] parts = raw.trim().split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
