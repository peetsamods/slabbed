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

        ClientChunkEvents.CHUNK_LOAD.register(SlabAnchorClientSync::onChunkLoad);
    }

    private static void onChunkLoad(net.minecraft.client.world.ClientWorld world, WorldChunk chunk) {
        // Register listener for future attachment changes (e.g. live anchor add/remove sync).
        registerRerenderListener(chunk, SlabAnchorAttachment.ANCHOR_TYPE);
        registerRerenderListener(chunk, SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE);

        // Also handle any attachment value already present at chunk-load time.
        // This covers the case where the chunk attachment sync packet arrived before
        // CHUNK_LOAD (or together with the initial chunk data), so onAttachedSet never
        // fires but the attachment is already populated.
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.ANCHOR_TYPE);
        scheduleInitialRerenders(chunk, SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE);
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
            scheduleRerendersForSet(mc, oldSet);
            scheduleRerendersForSet(mc, newSet);
        });
    }

    private static void scheduleInitialRerenders(
            WorldChunk chunk,
            AttachmentType<LongOpenHashSet> attachmentType
    ) {
        LongOpenHashSet initial = chunk.getAttached(attachmentType);
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
                mc.worldRenderer.scheduleBlockRerenderIfNeeded(mutable, current, current);
            }
        }
    }
}
