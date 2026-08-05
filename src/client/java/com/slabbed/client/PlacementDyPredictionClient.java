package com.slabbed.client;

import com.slabbed.anchor.PlacementDyOverlay;
import com.slabbed.anchor.SlabAnchorAttachment;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Thin client wiring for the {@link PlacementDyOverlay} (Slice 2i). Everything with a decision in it
 * lives in the overlay itself, in {@code src/main}, so it stays reachable from this line's only
 * working test harness; this class supplies the four things that genuinely need a client:
 * level identity, the raw backing read, targeted rerenders, and the lifecycle events.
 *
 * <p>The client NEVER writes the chunk's {@code PLACEMENT_DY} attachment — see
 * {@link PlacementDyOverlay} for the mechanism and for why an overlay is the only shape that
 * self-heals when the server refuses a placement.
 */
@Environment(EnvType.CLIENT)
public final class PlacementDyPredictionClient {

    private static boolean initialized;

    private PlacementDyPredictionClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        PlacementDyOverlay.installClientHooks(
                PlacementDyPredictionClient::backingFact,
                PlacementDyPredictionClient::scheduleExactPlacementDyRerender);

        // The overlay-aware read seam. Answers null for every cell it has no opinion about, so the
        // backing store still resolves everything else. The level handle is the current ClientWorld:
        // a snapshot published for a different world can never be mistaken for this one.
        SlabAnchorAttachment.clientEffectivePlacementDyLookup = pos -> {
            if (pos == null) {
                return null;
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            return mc == null ? null : PlacementDyOverlay.overlayFact(mc.world, pos.asLong());
        };

        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) ->
                PlacementDyOverlay.resetForLevel(world));
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) ->
                PlacementDyOverlay.onChunkUnload(world, chunk.getPos().x, chunk.getPos().z));
        // Drives lazy retirement and its timeout. It is also the disconnect path: once the client has
        // no world, the level handle stops matching and the overlay resets itself, so a prediction
        // can never outlive the connection that made it.
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                PlacementDyOverlay.clientTick(client.world));
    }

    /** Called from the RETURN of {@code ClientWorld.handlePlayerActionResponse}. */
    public static void onVanillaAcknowledgement(ClientWorld world, int sequence) {
        PlacementDyOverlay.onVanillaAcknowledgement(world, sequence);
    }

    /**
     * Raw authoritative read, straight past the overlay. This is what the overlay's lazy retirement
     * waits on: a group only stands down once the server's own fact for its cells has actually
     * arrived (or the timeout expires).
     */
    private static SlabAnchorAttachment.PlacementDyFact backingFact(long packedPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) {
            return SlabAnchorAttachment.PlacementDyFact.absent();
        }
        return SlabAnchorAttachment.rawPlacementDyFact(mc.world, BlockPos.fromLong(packedPos));
    }

    /**
     * Rebuilds exactly the section holding {@code pos}. Skipped when the chunk is gone, so a
     * retirement that races a chunk unload cannot poke the renderer at an unloaded position.
     */
    private static void scheduleExactPlacementDyRerender(BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.worldRenderer == null || pos == null
                || !mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return;
        }
        var state = mc.world.getBlockState(pos);
        if (state != null) {
            mc.worldRenderer.scheduleBlockRerenderIfNeeded(pos, state, state);
        }
    }
}
