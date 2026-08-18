package com.slabbed.client;

import com.slabbed.mixin.client.WorldRendererImportantRemeshInvoker;
import com.slabbed.util.DependentRemeshQueue;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

/** Client-thread owner for coalesced, bounded, important dependent-section rebuilds. */
public final class SlabDependentRemeshScheduler {
    private static final DependentRemeshQueue PENDING =
            new DependentRemeshQueue(DependentRemeshQueue.MAX_SECTION_REBUILDS_PER_TICK);
    private static ClientWorld queuedWorld;

    private SlabDependentRemeshScheduler() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(SlabDependentRemeshScheduler::drain);
        // The shared visual-dy cache is keyed by packed BlockPos with no world discriminator,
        // so it MUST be emptied whenever the client world changes or goes away — a retained
        // entry is indistinguishable from the same coordinates in the new world, and the
        // render-region read path prefers it over a fresh computation.
        // VisualDyCacheWorldChangeClientGameTest pins this.
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(
                (client, world) -> SlabSupport.clearVisualYOffsetCache());
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> SlabSupport.clearVisualYOffsetCache());
    }

    public static void enqueue(
            ClientWorld world,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ
    ) {
        if (world == null) {
            return;
        }
        if (world != queuedWorld) {
            PENDING.clear();
            queuedWorld = world;
        }
        PENDING.enqueueBlockRegion(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void drain(MinecraftClient client) {
        if (client == null || client.world == null || client.worldRenderer == null) {
            PENDING.clear();
            queuedWorld = null;
            return;
        }
        if (client.world != queuedWorld) {
            PENDING.clear();
            queuedWorld = client.world;
            return;
        }

        PENDING.drain((sectionX, sectionY, sectionZ, region) -> {
            SlabSupport.refreshVisualYOffsetRegion(
                    client.world,
                    region.minX(), region.minY(), region.minZ(),
                    region.maxX(), region.maxY(), region.maxZ());
            ((WorldRendererImportantRemeshInvoker) client.worldRenderer).slabbed$scheduleChunkRender(
                    sectionX,
                    sectionY,
                    sectionZ,
                    DependentRemeshQueue.REQUEST_IMPORTANT_REBUILD);
        });
    }
}
