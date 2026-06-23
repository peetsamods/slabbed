package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

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
public final class SlabAnchorClientSync {
    private static final Map<Long, LevelChunk> TRACKED_CHUNKS = new HashMap<>();
    private static final Map<AttachmentSnapshotKey, LongOpenHashSet> ATTACHMENT_SNAPSHOTS = new HashMap<>();
    private static boolean initialized;

    private record AttachmentSnapshotKey(long chunkPos, AttachmentType<LongOpenHashSet> attachmentType) {
    }

    private SlabAnchorClientSync() {
    }

    public static void init(IEventBus eventBus) {
        if (initialized) {
            return;
        }
        initialized = true;

        // Bridge: chunk render paths receive ChunkRendererRegion (not a Level) and cannot
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
            LongOpenHashSet set = chunk.getExistingDataOrNull(SlabAnchorAttachment.ANCHOR_TYPE.get());
            return set != null && set.contains(pos.asLong());
        };
        SlabAnchorAttachment.clientFrozenFlatLookup = pos -> {
            LongOpenHashSet set = clientAttachmentSet(pos, SlabAnchorAttachment.FROZEN_FLAT_TYPE);
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
            LongOpenHashSet set = chunk.getExistingDataOrNull(SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE.get());
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

        eventBus.addListener(SlabAnchorClientSync::onChunkLoad);
        eventBus.addListener(SlabAnchorClientSync::onChunkUnload);
        eventBus.addListener(SlabAnchorClientSync::pollAttachmentChanges);
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ClientLevel)) {
            return;
        }
        TRACKED_CHUNKS.put(chunk.getPos().toLong(), chunk);

        logReloadJumpSync("chunkLoad", chunk, SlabAnchorAttachment.ANCHOR_TYPE, null,
                chunk.getExistingDataOrNull(SlabAnchorAttachment.ANCHOR_TYPE.get()));
        logReloadJumpSync("chunkLoad", chunk, SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE, null,
                chunk.getExistingDataOrNull(SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE.get()));
        logReloadJumpSync("chunkLoad", chunk, SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE, null,
                chunk.getExistingDataOrNull(SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE.get()));
        logReloadJumpSync("chunkLoad", chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE, null,
                chunk.getExistingDataOrNull(SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE.get()));
        logReloadJumpSync("chunkLoad", chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE, null,
                chunk.getExistingDataOrNull(SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE.get()));
        logReloadJumpSync("chunkLoad", chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE, null,
                chunk.getExistingDataOrNull(SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE.get()));
        logReloadJumpSync("chunkLoad", chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE, null,
                chunk.getExistingDataOrNull(SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE.get()));

        // Also handle any attachment value already present at chunk-load time.
        // This covers the case where the chunk attachment sync packet arrived before
        // CHUNK_LOAD (or together with the initial chunk data), so a later poll never
        // fires but the attachment is already populated.
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.ANCHOR_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.FROZEN_FLAT_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE);

        snapshotAttachment(chunk, SlabAnchorAttachment.ANCHOR_TYPE);
        snapshotAttachment(chunk, SlabAnchorAttachment.FROZEN_FLAT_TYPE);
        snapshotAttachment(chunk, SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE);
        snapshotAttachment(chunk, SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE);
        snapshotAttachment(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE);
        snapshotAttachment(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE);
        snapshotAttachment(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE);
        snapshotAttachment(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE);
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ClientLevel)) {
            return;
        }
        long chunkPos = chunk.getPos().toLong();
        TRACKED_CHUNKS.remove(chunkPos);
        ATTACHMENT_SNAPSHOTS.keySet().removeIf(key -> key.chunkPos() == chunkPos);
    }

    private static LongOpenHashSet clientAttachmentSet(
            BlockPos pos,
            Supplier<AttachmentType<LongOpenHashSet>> attachmentType
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || pos == null) {
            return null;
        }
        LevelChunk chunk = mc.level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getExistingDataOrNull(attachmentType.get());
    }

    private static void pollAttachmentChanges(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRACKED_CHUNKS.clear();
            ATTACHMENT_SNAPSHOTS.clear();
            return;
        }
        if (mc.levelRenderer == null) {
            return;
        }

        for (LevelChunk chunk : new ArrayList<>(TRACKED_CHUNKS.values())) {
            pollAttachmentChange(mc, chunk, SlabAnchorAttachment.ANCHOR_TYPE);
            pollAttachmentChange(mc, chunk, SlabAnchorAttachment.FROZEN_FLAT_TYPE);
            pollAttachmentChange(mc, chunk, SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE);
            pollAttachmentChange(mc, chunk, SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE);
            pollAttachmentChange(mc, chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE);
            pollAttachmentChange(mc, chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE);
            pollAttachmentChange(mc, chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE);
            pollAttachmentChange(mc, chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE);
        }
    }

    private static void pollAttachmentChange(
            Minecraft mc,
            LevelChunk chunk,
            Supplier<AttachmentType<LongOpenHashSet>> attachmentType
    ) {
        AttachmentSnapshotKey key = new AttachmentSnapshotKey(chunk.getPos().toLong(), attachmentType.get());
        LongOpenHashSet oldSet = ATTACHMENT_SNAPSHOTS.get(key);
        LongOpenHashSet newSet = copyAttachmentSet(chunk.getExistingDataOrNull(attachmentType.get()));
        if (attachmentSetsEqual(oldSet, newSet)) {
            return;
        }

        logReloadJumpSync("attachedSetPoll", chunk, attachmentType, oldSet, newSet);
        scheduleRerendersForSet(mc, oldSet, attachmentType);
        scheduleRerendersForSet(mc, newSet, attachmentType);
        ATTACHMENT_SNAPSHOTS.put(key, newSet);
    }

    private static void snapshotAttachment(
            LevelChunk chunk,
            Supplier<AttachmentType<LongOpenHashSet>> attachmentType
    ) {
        AttachmentSnapshotKey key = new AttachmentSnapshotKey(chunk.getPos().toLong(), attachmentType.get());
        ATTACHMENT_SNAPSHOTS.put(key, copyAttachmentSet(chunk.getExistingDataOrNull(attachmentType.get())));
    }

    private static LongOpenHashSet copyAttachmentSet(LongOpenHashSet set) {
        return set == null ? null : new LongOpenHashSet(set);
    }

    private static boolean attachmentSetsEqual(LongOpenHashSet left, LongOpenHashSet right) {
        boolean leftEmpty = left == null || left.isEmpty();
        boolean rightEmpty = right == null || right.isEmpty();
        if (leftEmpty || rightEmpty) {
            return leftEmpty == rightEmpty;
        }
        return left.equals(right);
    }

    private static void scheduleInitialRerenders(
            LevelChunk chunk,
            Supplier<AttachmentType<LongOpenHashSet>> attachmentType
    ) {
        LongOpenHashSet initial = chunk.getExistingDataOrNull(attachmentType.get());
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
            Supplier<AttachmentType<LongOpenHashSet>> attachmentType
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
                if (SlabAnchorAttachment.isCompoundVisibleAttachmentType(attachmentType.get())) {
                    scheduleCompoundVisibleRenderRefresh(mc, rerenderPos, current, attachmentType);
                } else {
                    mc.levelRenderer.setBlockDirty(rerenderPos, current, current);
                }
            }
        }
    }

    private static void scheduleCompoundVisibleRenderRefresh(
            Minecraft mc,
            BlockPos pos,
            BlockState state,
            Supplier<AttachmentType<LongOpenHashSet>> attachmentType
    ) {
        mc.levelRenderer.setBlocksDirty(
                pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        logCompoundVisibleRenderRefresh(mc, pos, state, attachmentType);
    }

    private static boolean reloadJumpRecorderEnabled() {
        return Boolean.getBoolean("slabbed.beta4ReloadJumpRecorder");
    }

    private static String labelForAttachment(Supplier<AttachmentType<LongOpenHashSet>> attachmentType) {
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
            Supplier<AttachmentType<LongOpenHashSet>> attachmentType,
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
            Supplier<AttachmentType<LongOpenHashSet>> attachmentType,
            LongOpenHashSet oldSet,
            LongOpenHashSet newSet
    ) {
        if (!SlabAnchorAttachment.beta4CompoundVisibleRenderTraceEnabled()
                || !SlabAnchorAttachment.isCompoundVisibleAttachmentType(attachmentType.get())) {
            return;
        }
        boolean oldPresent = oldSet != null && !oldSet.isEmpty();
        boolean newPresent = newSet != null && !newSet.isEmpty();
        if (!oldPresent && !newPresent) {
            return;
        }
        Slabbed.LOGGER.info(
                "[JULIA_BETA4_COMPOUND_VISIBLE_RENDER_TRACE_CLIENT_SYNC] event={} pos=chunk marker={} serverMarker=n/a clientMarker={} modelViewType=Level slabSupportDy=n/a clientDy=n/a candidateRerenderScheduled={} neighborRerenderScheduled={}",
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
            Supplier<AttachmentType<LongOpenHashSet>> attachmentType
    ) {
        if (!SlabAnchorAttachment.beta4CompoundVisibleRenderTraceEnabled()) {
            return;
        }
        String marker = SlabAnchorAttachment.compoundVisibleAttachmentLabel(attachmentType.get());
        boolean clientMarker = compoundVisibleClientMarker(mc, pos, state, attachmentType);
        double slabSupportDy = mc.level == null ? 0.0d : SlabSupport.getYOffset(mc.level, pos, state);
        double clientDy = mc.level == null ? 0.0d : ClientDy.dyFor(mc.level, pos, state);
        Slabbed.LOGGER.info(
                "[JULIA_BETA4_COMPOUND_VISIBLE_RENDER_TRACE_RERENDER] pos={} marker={} serverMarker=n/a clientMarker={} modelViewType=Level slabSupportDy={} clientDy={} candidateRerenderScheduled=true neighborRerenderScheduled=true sourceRerenderScheduled=true",
                pos.toShortString(),
                marker,
                clientMarker,
                slabSupportDy,
                clientDy);
        if (!clientMarker || Math.abs(slabSupportDy + 1.0d) > 1.0e-6d) {
            return;
        }
        Slabbed.LOGGER.info(
                "[JULIA_BETA4_COMPOUND_VISIBLE_RENDER_TRACE_SUMMARY] classification=RERENDER_NOT_SCHEDULED pos={} marker={} markerSet=clientAttachment clientMarker={} modelDy={} candidateRerenderScheduled=true neighborRerenderScheduled=true releaseBlockers=JuliaLiveRetest",
                pos.toShortString(),
                marker,
                clientMarker,
                slabSupportDy);
    }

    private static boolean compoundVisibleClientMarker(
            Minecraft mc,
            BlockPos pos,
            BlockState state,
            Supplier<AttachmentType<LongOpenHashSet>> attachmentType
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
                    && (pos.getX() >> 4) == chunk.getPos().x
                    && (pos.getZ() >> 4) == chunk.getPos().z) {
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
