package com.slabbed.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * A single, brief action-bar notice ("Slabbed is in beta...") shown once per client
 * session — not once per world-join, so hopping between worlds/servers in the same
 * session doesn't repeat it. Action bar (not chat) so it fades on its own and never
 * clutters the chat log.
 */
public final class BetaNoticeClient {

    private static boolean shown = false;

    private BetaNoticeClient() {
    }

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (shown) {
                return;
            }
            shown = true;
            if (client.player != null) {
                client.player.sendMessage(
                        Text.literal("Slabbed is in beta — expect some rough edges while it's being developed.")
                                .formatted(Formatting.GRAY, Formatting.ITALIC),
                        true);
            }
        });
    }
}
