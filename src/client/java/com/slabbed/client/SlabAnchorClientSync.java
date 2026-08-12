package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.anchor.DeepDyConsentAttachment;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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

        // Same client-world fallback for the freeze-on-place FLAT marker, so the model render path
        // sees the frozen-flat state and keeps the piece at dy=0 (no autonomous lowering).
        SlabAnchorAttachment.clientFrozenFlatLookup = pos -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) {
                return false;
            }
            WorldChunk chunk = mc.world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                return false;
            }
            LongOpenHashSet set = chunk.getAttached(SlabAnchorAttachment.FROZEN_FLAT_TYPE);
            return set != null && set.contains(pos.asLong());
        };

        // Same client-world fallback for the PLACEMENT-HEIGHT store (LAW.md lane G). Without it the
        // chunk mesh would resolve an anchored cell's height live while outline and raycast — which
        // do get a World — read the stored placement height, and the three would draw the same
        // block at two different heights.
        SlabPlacementDyAttachment.clientPlacementDyLookup = pos -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) {
                return Double.NaN;
            }
            return SlabPlacementDyAttachment.lookup(
                    mc.world.getChunk(pos.getX() >> 4, pos.getZ() >> 4), pos);
        };

        ClientChunkEvents.CHUNK_LOAD.register(SlabAnchorClientSync::onChunkLoad);
        initDeepDyConsent();
    }

    /**
     * THE CLIENT LEARNS THE dy CAP FROM THE SERVER AND HAS NO OTHER WAY TO KNOW IT.
     *
     * <p>{@link DeepDyConsentAttachment} is a per-world fact that decides where blocks resolve. A
     * client that guessed it would draw blocks somewhere the server does not have them, then snap
     * them — the defect fixed at {@code 4e55cc4c}. So the client has exactly one writer for the
     * cached cap, and the only value it can write is the one that arrived in the synced world
     * attachment.
     *
     * <p>Three cases, all covered:
     *
     * <ul>
     *   <li><b>Integrated server (singleplayer, open-to-LAN).</b> The client does not touch it at
     *       all: the server in this same JVM already armed the one shared field from the save's own
     *       stamp, and there is no second value to disagree with.</li>
     *   <li><b>Remote server.</b> Adopt on world change (absent attachment ⇒ the shipped cap), then
     *       adopt again when the attachment arrives. Fabric sends a world's attachments from its
     *       player-join hook, which is enqueued before the first chunk packet, so nothing has been
     *       meshed by the time the value lands.</li>
     *   <li><b>Leaving.</b> Reset on disconnect. An ABSENT attachment sends no change, so a client
     *       coming from a consented world would otherwise carry that cap into a legacy one.</li>
     * </ul>
     */
    private static void initDeepDyConsent() {
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
            if (client != null && client.isIntegratedServerRunning()) {
                return;
            }
            DeepDyConsentAttachment.installClientListener(world);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (client != null && client.isIntegratedServerRunning()) {
                return;
            }
            DeepDyConsentAttachment.resetToLegacyDefault();
        });
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

        // FROZEN_FLAT marker: same live-sync + initial-population rerender handling as ANCHOR_TYPE,
        // so on reload a flat-frozen piece renders at dy=0 even when a slab/carrier sits below it.
        chunk.<LongOpenHashSet>onAttachedSet(SlabAnchorAttachment.FROZEN_FLAT_TYPE).register((oldSet, newSet) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.worldRenderer == null) {
                return;
            }
            scheduleRerendersForSet(mc, oldSet);
            scheduleRerendersForSet(mc, newSet);
        });
        LongOpenHashSet initialFrozenFlat = chunk.getAttached(SlabAnchorAttachment.FROZEN_FLAT_TYPE);
        if (initialFrozenFlat != null && !initialFrozenFlat.isEmpty()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.worldRenderer != null) {
                scheduleRerendersForSet(mc, initialFrozenFlat);
            }
        }

        // PLACEMENT-HEIGHT store: same live-sync + initial-population rerender handling. The stored
        // height changes what a cell draws at without changing any BlockState, so nothing else
        // would ever mark the section dirty.
        chunk.<Long2ByteOpenHashMap>onAttachedSet(SlabPlacementDyAttachment.PLACEMENT_DY_TYPE)
                .register((oldFacts, newFacts) -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.worldRenderer == null) {
                        return;
                    }
                    scheduleRerendersForPositions(mc, oldFacts);
                    scheduleRerendersForPositions(mc, newFacts);
                });
        Long2ByteOpenHashMap initialPlacementDy = chunk.getAttached(SlabPlacementDyAttachment.PLACEMENT_DY_TYPE);
        if (initialPlacementDy != null && !initialPlacementDy.isEmpty()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.worldRenderer != null) {
                scheduleRerendersForPositions(mc, initialPlacementDy);
            }
        }
    }

    private static void scheduleRerendersForPositions(MinecraftClient mc, Long2ByteOpenHashMap facts) {
        if (facts == null || facts.isEmpty()) {
            return;
        }
        scheduleRerendersForSet(mc, new LongOpenHashSet(facts.keySet()));
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
