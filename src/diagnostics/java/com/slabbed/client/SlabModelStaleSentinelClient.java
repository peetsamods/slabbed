package com.slabbed.client;

import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.util.LiveCursorIntentRecorder;
import com.slabbed.util.SlabModelStaleSentinel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashMap;

/**
 * Client driver for the MODEL_STALE sentinel ({@link SlabModelStaleSentinel}): the tick-end sampler,
 * the suppression-window event feeds (level change, chunk unload, F3+A render invalidation), and the
 * red-row sink (recorder row + a rate-limited in-game ping so Maintainer sees a verdict WHILE PLAYING instead
 * of discovering staleness by staring at blocks — the whole point of the probe).
 *
 * <p>All judging work is gated behind an active recorder session; with the recorder off this whole
 * driver is a null-check + volatile read per tick.
 */
public final class SlabModelStaleSentinelClient {
    /** Minimum ticks between in-game pings — red rows latch per pos, but a burst (one bad placement can
     *  red several armed neighbors at once) should not spam chat; the full detail is in the recorder. */
    private static final int CHAT_PING_INTERVAL_TICKS = 40;

    private static ClientLevel lastLevel;
    private static long lastPingTick = Long.MIN_VALUE;
    private static long redRowsSinceLastPing;

    private SlabModelStaleSentinelClient() {
    }

    public static void init() {
        // The judge/baseline dy MUST be the same model-twin the mesher captures. C5 makes that twin
        // consume the canonical logical dy for carpet too; non-client views use that same authority.
        SlabModelStaleSentinel.setLiveDyPolicy((level, pos, state) ->
                level instanceof BlockAndTintGetter view
                        ? OffsetBlockStateModel.liveModelDy(view, pos, state)
                        : com.slabbed.util.SlabSupport.getYOffset(level, pos, state));
        ClientTickEvents.END_CLIENT_TICK.register(SlabModelStaleSentinelClient::onEndClientTick);
        ClientChunkEvents.CHUNK_UNLOAD.register((clientLevel, chunk) -> {
            // 26.2 ChunkPos hides x/z behind the packed-long accessors.
            long packed = chunk.getPos().pack();
            SlabModelStaleSentinel.onChunkUnload(ChunkPos.getX(packed), ChunkPos.getZ(packed));
        });
        InvalidateRenderStateCallback.EVENT.register(SlabModelStaleSentinel::onFullRenderInvalidate);
    }

    private static void onEndClientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (level != lastLevel) {
            // World join / leave / dimension change: drop all sentinel state and open the grace window —
            // join-time mesh churn must never be judged.
            lastLevel = level;
            // Ping bookkeeping is per-world game time — carrying a large lastPingTick into a world with
            // a smaller clock would silently starve every ping (adversarial review finding #3).
            lastPingTick = Long.MIN_VALUE;
            redRowsSinceLastPing = 0;
            if (level == null) {
                SlabModelStaleSentinel.resetCold();
            } else {
                SlabModelStaleSentinel.reset(level.getGameTime());
            }
            return;
        }
        if (level == null || !LiveCursorIntentRecorder.enabled()) {
            return;
        }
        long nowTick = level.getGameTime();
        SlabModelStaleSentinel.maybeSample(level, nowTick, level::hasChunkAt,
                row -> emitDiagnosticRow(client, nowTick, row));
    }

    private static void emitDiagnosticRow(Minecraft client, long nowTick, LinkedHashMap<String, String> row) {
        // Every diagnostic remains part of the session record. Only shared-policy RED rows participate
        // in burst accounting or chat; INFO/YELLOW must never impersonate a player-visible failure.
        LiveCursorIntentRecorder.recordSentinel(row);
        String kind = row.getOrDefault("kind", "unknown");
        if (SlabModelStaleSentinel.diagnosticSeverity(kind)
                != SlabModelStaleSentinel.DiagnosticSeverity.RED) {
            return;
        }
        redRowsSinceLastPing++;
        if (client.player == null || !shouldSendChatAlert(kind, nowTick, lastPingTick)) {
            return;
        }
        lastPingTick = nowTick;
        String extra = redRowsSinceLastPing > 1 ? " (+" + (redRowsSinceLastPing - 1) + " more in log)" : "";
        client.player.sendSystemMessage(Component.literal(
                "[slabbed sentinel] " + kind
                        + " at " + row.getOrDefault("pos", "?")
                        + " baked=" + row.getOrDefault("bakedDy", "?")
                        + " live=" + row.getOrDefault("liveDy", "?")
                        + extra));
        redRowsSinceLastPing = 0;
    }

    /** Pure package-visible throttle seam: safe for the Long.MIN_VALUE sentinel and world-clock resets. */
    static boolean shouldSendChatAlert(String kind, long nowTick, long previousPingTick) {
        if (SlabModelStaleSentinel.diagnosticSeverity(kind)
                != SlabModelStaleSentinel.DiagnosticSeverity.RED) {
            return false;
        }
        return previousPingTick == Long.MIN_VALUE
                || nowTick < previousPingTick
                || nowTick - previousPingTick >= CHAT_PING_INTERVAL_TICKS;
    }
}
