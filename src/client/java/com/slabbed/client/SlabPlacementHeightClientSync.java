package com.slabbed.client;

import com.slabbed.anchor.ClientRenderDyPrediction;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.anchor.SlabbedClientMirror;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;

/** Refreshes affected client blocks when the synchronized placement-height map changes. */
public final class SlabPlacementHeightClientSync {
    private static final Set<LevelChunk> TRACKED_CHUNKS =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<LevelChunk> INITIALIZED_CHUNKS =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<LevelChunk, Long2ByteOpenHashMap> LAST_REFERENCES =
            new IdentityHashMap<>();
    private static final Map<LevelChunk, Long2ByteOpenHashMap> SNAPSHOTS =
            new IdentityHashMap<>();
    private static ClientLevel activeClientLevel;
    private static volatile RenderSnapshotState RENDER_SNAPSHOTS =
            new RenderSnapshotState(null, Map.of());
    private static Consumer<BlockPos> proofRefreshObserver = pos -> { };
    private static IntConsumer proofResetObserver = generation -> { };
    private static int connectionGeneration;
    private static boolean initialized;

    private SlabPlacementHeightClientSync() {
    }

    public static void init(IEventBus eventBus) {
        if (initialized) {
            return;
        }
        initialized = true;
        SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(packed -> {
            RenderSnapshotState published = RENDER_SNAPSHOTS;
            if (published.level() != Minecraft.getInstance().level) {
                return SlabPlacementHeightAttachment.ABSENT_HALF_STEPS;
            }
            BlockPos pos = BlockPos.of(packed);
            Long2ByteOpenHashMap facts = published.chunks().get(
                    ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            if (facts == null || !facts.containsKey(packed)) {
                // No authoritative fact yet. This lookup serves the chunk mesh only, so a
                // prediction consulted here cannot reach collision, targeting, or any
                // interaction - those read the attachment directly through a Level view.
                return ClientRenderDyPrediction.halfStepsOrAbsent(packed);
            }
            ClientRenderDyPrediction.forget(packed);
            return facts.get(packed);
        });
        eventBus.addListener(SlabPlacementHeightClientSync::onChunkLoad);
        eventBus.addListener(SlabPlacementHeightClientSync::onChunkUnload);
        eventBus.addListener(SlabPlacementHeightClientSync::pollAttachmentChanges);
        eventBus.addListener(SlabPlacementHeightClientSync::onLoggingOut);
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getChunk() instanceof LevelChunk chunk
                && chunk.getLevel() instanceof ClientLevel level
                && level == Minecraft.getInstance().level) {
            acceptClientLevel(level);
            TRACKED_CHUNKS.add(chunk);
        }
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getChunk() instanceof LevelChunk chunk
                && chunk.getLevel() == activeClientLevel) {
            forget(chunk);
        }
    }

    private static void pollAttachmentChanges(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.levelRenderer == null) {
            return;
        }
        ClientRenderDyPrediction.advanceTick();
        acceptClientLevel(minecraft.level);
        for (LevelChunk chunk : new ArrayList<>(TRACKED_CHUNKS)) {
            if (chunk.getLevel() != minecraft.level) {
                forget(chunk);
                continue;
            }
            observeReferenceChange(minecraft, chunk);
        }
    }

    private static void observeReferenceChange(Minecraft minecraft, LevelChunk chunk) {
        Long2ByteOpenHashMap current = SlabbedClientMirror.placementDyOrNull(chunk);
        boolean wasInitialized = INITIALIZED_CHUNKS.contains(chunk);
        if (wasInitialized && current == LAST_REFERENCES.get(chunk)) {
            return;
        }

        Long2ByteOpenHashMap previousSnapshot = SNAPSHOTS.get(chunk);
        Long2ByteOpenHashMap currentSnapshot = copy(current);
        publishRenderSnapshot(chunk, currentSnapshot);
        scheduleRerenders(minecraft, chunk, previousSnapshot, currentSnapshot);

        INITIALIZED_CHUNKS.add(chunk);
        LAST_REFERENCES.put(chunk, current);
        if (currentSnapshot == null) {
            SNAPSHOTS.remove(chunk);
        } else {
            SNAPSHOTS.put(chunk, currentSnapshot);
        }
    }

    private static Long2ByteOpenHashMap copy(Long2ByteOpenHashMap facts) {
        return facts == null ? null : new Long2ByteOpenHashMap(facts);
    }

    private static void scheduleRerenders(
            Minecraft minecraft,
            LevelChunk chunk,
            Long2ByteOpenHashMap previous,
            Long2ByteOpenHashMap current
    ) {
        LongOpenHashSet affected = new LongOpenHashSet();
        if (previous != null) {
            affected.addAll(previous.keySet());
        }
        if (current != null) {
            affected.addAll(current.keySet());
        }
        for (long packed : affected) {
            BlockPos pos = BlockPos.of(packed);
            minecraft.levelRenderer.setBlocksDirty(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ());
            proofRefreshObserver.accept(pos);
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // A prediction belongs to one connection. Carrying one across would draw a height for a
        // cell in a world that never authored it.
        ClientRenderDyPrediction.clear();
        TRACKED_CHUNKS.clear();
        INITIALIZED_CHUNKS.clear();
        LAST_REFERENCES.clear();
        SNAPSHOTS.clear();
        activeClientLevel = null;
        RENDER_SNAPSHOTS = new RenderSnapshotState(null, Map.of());
        connectionGeneration++;
        proofResetObserver.accept(connectionGeneration);
    }

    private static void forget(LevelChunk chunk) {
        TRACKED_CHUNKS.remove(chunk);
        INITIALIZED_CHUNKS.remove(chunk);
        LAST_REFERENCES.remove(chunk);
        SNAPSHOTS.remove(chunk);
        publishRenderSnapshot(chunk, null);
    }

    private static void publishRenderSnapshot(LevelChunk chunk, Long2ByteOpenHashMap facts) {
        if (chunk.getLevel() != activeClientLevel) {
            return;
        }
        RenderSnapshotState published = RENDER_SNAPSHOTS;
        Map<Long, Long2ByteOpenHashMap> replacement = new HashMap<>(
                published.level() == activeClientLevel ? published.chunks() : Map.of());
        long chunkPos = chunk.getPos().toLong();
        if (facts == null || facts.isEmpty()) {
            replacement.remove(chunkPos);
        } else {
            replacement.put(chunkPos, facts);
        }
        RENDER_SNAPSHOTS = new RenderSnapshotState(activeClientLevel, Map.copyOf(replacement));
    }

    private static void acceptClientLevel(ClientLevel level) {
        if (activeClientLevel == level) {
            return;
        }
        TRACKED_CHUNKS.clear();
        INITIALIZED_CHUNKS.clear();
        LAST_REFERENCES.clear();
        SNAPSHOTS.clear();
        activeClientLevel = level;
        RENDER_SNAPSHOTS = new RenderSnapshotState(level, Map.of());
    }

    static void installProofObservers(
            Consumer<BlockPos> refreshObserver,
            IntConsumer resetObserver
    ) {
        proofRefreshObserver = refreshObserver;
        proofResetObserver = resetObserver;
    }

    static int connectionGeneration() {
        return connectionGeneration;
    }

    static boolean proofCachesEmpty() {
        return TRACKED_CHUNKS.isEmpty()
                && INITIALIZED_CHUNKS.isEmpty()
                && LAST_REFERENCES.isEmpty()
                && SNAPSHOTS.isEmpty()
                && activeClientLevel == null
                && RENDER_SNAPSHOTS.chunks().isEmpty();
    }

    private record RenderSnapshotState(
            ClientLevel level,
            Map<Long, Long2ByteOpenHashMap> chunks
    ) {
    }
}
