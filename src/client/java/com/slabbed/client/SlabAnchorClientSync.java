package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

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
 * client chunk and call {@code WorldRenderer.scheduleBlockRerenderIfNeeded}
 * for each position in the union of the old and new anchor sets.
 */
@Environment(EnvType.CLIENT)
public final class SlabAnchorClientSync {

    private SlabAnchorClientSync() {
    }

    public static void init() {
        // Bridge: chunk render paths receive ChunkRendererRegion (not a World) and cannot
        // access chunk attachments.  Register a client-world fallback so anchor queries
        // from the model render path resolve correctly after a supporting BS is broken.
        SlabAnchorAttachment.clientAnchorLookup = pos -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) {
                return false;
            }
            WorldChunk chunk = mc.world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                return false;
            }
            LongOpenHashSet set = chunk.getAttached(SlabAnchorAttachment.ANCHOR_TYPE);
            return set != null && set.contains(pos.asLong());
        };
        SlabAnchorAttachment.clientLoweredSlabCarrierLookup = pos -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) {
                return false;
            }
            WorldChunk chunk = mc.world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
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

        ClientChunkEvents.CHUNK_LOAD.register(SlabAnchorClientSync::onChunkLoad);
    }

    private static void onChunkLoad(net.minecraft.client.world.ClientWorld world, WorldChunk chunk) {
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
        registerRerenderListener(chunk, SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE);
        registerRerenderListener(chunk, SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE);
        registerRerenderListener(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE);
        registerRerenderListener(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE);
        registerRerenderListener(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE);
        registerRerenderListener(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE);

        // Also handle any attachment value already present at chunk-load time.
        // This covers the case where the chunk attachment sync packet arrived before
        // CHUNK_LOAD (or together with the initial chunk data), so onAttachedSet never
        // fires but the attachment is already populated.
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.ANCHOR_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE);
    }

    private static LongOpenHashSet clientAttachmentSet(
            BlockPos pos,
            AttachmentType<LongOpenHashSet> attachmentType
    ) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || pos == null) {
            return null;
        }
        WorldChunk chunk = mc.world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getAttached(attachmentType);
    }

    private static void registerRerenderListener(
            WorldChunk chunk,
            AttachmentType<LongOpenHashSet> attachmentType
    ) {
        chunk.<LongOpenHashSet>onAttachedSet(attachmentType).register((oldSet, newSet) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.worldRenderer == null) {
                return;
            }
            logReloadJumpSync("attachedSet", chunk, attachmentType, oldSet, newSet);
            scheduleRerendersForSet(mc, oldSet);
            scheduleRerendersForSet(mc, newSet);
        });
    }

    private static void scheduleInitialRerenders(
            WorldChunk chunk,
            AttachmentType<LongOpenHashSet> attachmentType
    ) {
        LongOpenHashSet initial = chunk.getAttached(attachmentType);
        logReloadJumpSync("initialRerenderCheck", chunk, attachmentType, null, initial);
        if (initial != null && !initial.isEmpty()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.worldRenderer != null) {
                scheduleRerendersForSet(mc, initial);
            }
        }
    }

    private static void scheduleRerendersForSet(MinecraftClient mc, LongOpenHashSet set) {
        if (set == null || set.isEmpty()) {
            return;
        }
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (long packed : set) {
            mutable.set(packed);
            BlockState current = mc.world != null
                    ? mc.world.getBlockState(mutable)
                    : null;
            if (current != null) {
                if (SlabAnchorAttachment.TRACE) {
                    Slabbed.LOGGER.info("[ANCHOR] client rerender pos={} reason=attachment_sync_or_chunk_load",
                            mutable.toShortString());
                }
                logReloadJumpSyncRerender(mutable, current);
                mc.worldRenderer.scheduleBlockRerenderIfNeeded(mutable, current, current);
            }
        }
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
            WorldChunk chunk,
            AttachmentType<LongOpenHashSet> attachmentType,
            LongOpenHashSet oldSet,
            LongOpenHashSet newSet
    ) {
        String type = labelForAttachment(attachmentType);
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
            WorldChunk chunk,
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

    private static String watchedPositionsInsideChunk(WorldChunk chunk) {
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
