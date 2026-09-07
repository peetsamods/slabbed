package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.network.FrozenDyModeMessages;
import com.slabbed.network.FrozenDyModePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Receives the server's stored-height compatibility flag on join and, only when it disagrees with
 * this client's own flag, logs one warning and tells the player once.
 *
 * <p>Receive-only: this class never sends. Joining a vanilla or non-Slabbed server simply produces
 * no callback.
 *
 * <p><b>Once per CONNECTION, not once per launch.</b> The gate is cleared on both connection init
 * and disconnect, so a player who fixes nothing and reconnects is told again — the mismatch is still
 * there, and a warning shown once and then permanently silenced would read as resolved.
 *
 * <p>No behaviour is changed here or anywhere else by the reported value; the two sides keep running
 * whatever their own launch settings say (maintainer ruling, 2026-09-06).
 */
public final class FrozenDyModeClient {

    private static volatile boolean warnedThisConnection;

    private FrozenDyModeClient() {
    }

    public static void init() {
        ClientPlayConnectionEvents.INIT.register((handler, client) -> warnedThisConnection = false);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> warnedThisConnection = false);
        ClientPlayNetworking.registerGlobalReceiver(FrozenDyModePayload.TYPE, (payload, context) -> {
            boolean serverEnabled = payload.frozenDyEnabled();
            boolean clientEnabled = SlabAnchorAttachment.FROZEN_DY_ENABLED;
            if (!FrozenDyModeMessages.sidesMismatch(serverEnabled, clientEnabled) || warnedThisConnection) {
                return;
            }
            warnedThisConnection = true;
            Slabbed.LOGGER.warn(
                    "Stored-height compatibility flag differs between the sides (server={}, client={}); "
                            + "drawn heights and the heights the server resolves against can disagree",
                    serverEnabled, clientEnabled);
            Component message = FrozenDyModeMessages.mismatchMessage(serverEnabled, clientEnabled);
            Minecraft client = context.client();
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendSystemMessage(message);
                }
            });
        });
    }
}
