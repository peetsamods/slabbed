package com.slabbed.client;

import com.slabbed.anchor.SlabPlacementDyAttachment;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/** Bounded client mirror of immutable placement-transaction height decisions. */
public final class ClientPlacementDyPrediction {
    private static final long LIFETIME_NANOS = 2_000_000_000L;
    private static final ConcurrentHashMap<Long, Prediction> PREDICTIONS = new ConcurrentHashMap<>();

    private ClientPlacementDyPrediction() {
    }

    public static void init() {
        SlabPlacementDyAttachment.clientPredictedPlacementDyLookup = ClientPlacementDyPrediction::lookup;
        SlabPlacementDyAttachment.clientPredictionPublisher = ClientPlacementDyPrediction::publish;
        SlabPlacementDyAttachment.clientPredictionClearer = ClientPlacementDyPrediction::clear;
        ClientTickEvents.END_CLIENT_TICK.register(client -> pruneExpired());
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> clearAll());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearAll());
    }

    public static void publish(Map<BlockPos, Long> rawBitsByPos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || rawBitsByPos == null || rawBitsByPos.isEmpty()) {
            return;
        }
        long expiresAt = System.nanoTime() + LIFETIME_NANOS;
        for (Map.Entry<BlockPos, Long> entry : rawBitsByPos.entrySet()) {
            BlockPos pos = entry.getKey();
            Long rawBits = entry.getValue();
            if (pos == null || rawBits == null) {
                continue;
            }
            double dy = Double.longBitsToDouble(rawBits);
            if (!Double.isFinite(dy)) {
                continue;
            }
            BlockState expectedState = client.world.getBlockState(pos);
            PREDICTIONS.put(pos.asLong(), new Prediction(expectedState, dy, expiresAt));
        }
    }

    public static double lookup(BlockPos pos) {
        if (pos == null) {
            return Double.NaN;
        }
        Prediction prediction = PREDICTIONS.get(pos.asLong());
        if (prediction == null) {
            return Double.NaN;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (prediction.expiresAtNanos() <= System.nanoTime()
                || client == null
                || client.world == null
                || !client.world.getBlockState(pos).equals(prediction.expectedState())) {
            PREDICTIONS.remove(pos.asLong(), prediction);
            return Double.NaN;
        }
        return prediction.dy();
    }

    public static void clear(Iterable<BlockPos> positions) {
        if (positions == null) {
            return;
        }
        for (BlockPos pos : positions) {
            if (pos != null) {
                PREDICTIONS.remove(pos.asLong());
            }
        }
    }

    public static void clearPacked(Iterable<Long> packedPositions) {
        if (packedPositions == null) {
            return;
        }
        for (Long packed : packedPositions) {
            if (packed != null) {
                PREDICTIONS.remove(packed.longValue());
            }
        }
    }

    public static void clearAll() {
        PREDICTIONS.clear();
    }

    private static void pruneExpired() {
        long now = System.nanoTime();
        PREDICTIONS.entrySet().removeIf(entry -> entry.getValue().expiresAtNanos() <= now);
    }

    private record Prediction(BlockState expectedState, double dy, long expiresAtNanos) {
    }
}
