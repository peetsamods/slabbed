package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Schedules a client-side block rerender for every anchored position when the
 * {@link SlabAnchorAttachment#ANCHOR_TYPE} chunk attachment changes on the client.
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

        ClientChunkEvents.CHUNK_LOAD.register(SlabAnchorClientSync::onChunkLoad);
    }

    private static void onChunkLoad(net.minecraft.client.world.ClientWorld world, WorldChunk chunk) {
        // Register listener for future attachment changes (e.g. live anchor add/remove sync).
        chunk.<LongOpenHashSet>onAttachedSet(SlabAnchorAttachment.ANCHOR_TYPE).register((oldSet, newSet) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.worldRenderer == null) {
                return;
            }
            scheduleRerendersForSet(mc, oldSet);
            scheduleRerendersForSet(mc, newSet);
        });

        // Also handle any attachment value already present at chunk-load time.
        // This covers the case where the chunk attachment sync packet arrived before
        // CHUNK_LOAD (or together with the initial chunk data), so onAttachedSet never
        // fires but the attachment is already populated.
        LongOpenHashSet initial = chunk.getAttached(SlabAnchorAttachment.ANCHOR_TYPE);
        if (initial != null && !initial.isEmpty()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.worldRenderer != null) {
                scheduleRerendersForSet(mc, initial);
            }
        }
    }

    private static void scheduleRerendersForSet(MinecraftClient mc, LongOpenHashSet set) {
        if (set == null || set.isEmpty() || mc.worldRenderer == null) {
            return;
        }
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (long packed : set) {
            mutable.set(packed);
            int x = mutable.getX();
            int y = mutable.getY();
            int z = mutable.getZ();
            // UNCONDITIONAL rebuild of the anchored cell + its bounded dependent
            // column. The synced anchor changes the rendered dy of this cell (and
            // of column blocks stacked above it) WITHOUT changing any BlockState,
            // so scheduleBlockRerenderIfNeeded(pos, same, same) no-ops and the
            // model stays at the stale (un-lowered) height — the visible gap.
            // scheduleBlockRenders marks the intersecting sections dirty outright.
            if (mc.world != null) {
                SlabSupport.refreshVisualYOffsetRegion(
                        mc.world,
                        x - 1, y - 1, z - 1,
                        x + 1, y + SlabSupport.chainRerenderDepth(), z + 1);
            }
            mc.worldRenderer.scheduleBlockRenders(
                    x - 1, y - 1, z - 1,
                    x + 1, y + SlabSupport.chainRerenderDepth(), z + 1);
            if (SlabAnchorAttachment.TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] client rerender region pos={} reason=attachment_sync_or_chunk_load",
                        mutable.toShortString());
            }
        }
    }
}
