package com.slabbed.network;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Sends {@link FrozenDyModePayload} once per player join, so a client can tell whether it and the
 * server it joined agree about the stored-height compatibility flag.
 *
 * <p>Stateless: nothing per-player is remembered here, so there is no disconnect hook to keep in
 * step with this one.
 */
public final class FrozenDyModeServer {

    private FrozenDyModeServer() {
    }

    /**
     * The payload for the flag as it stands RIGHT NOW.
     *
     * <p><b>Must read {@code FROZEN_DY_ENABLED} live on every call.</b> Capturing it into a static at
     * class-load time would report whatever the flag was during mod init, which is the value a test
     * flipping the field in-process would no longer see — and, more importantly, is not necessarily
     * what the rest of the mod is reading by the time a player joins. Kept as its own method so the
     * read has a seam a headless test can call without a live connection.
     */
    public static FrozenDyModePayload currentPayload() {
        return new FrozenDyModePayload(SlabAnchorAttachment.FROZEN_DY_ENABLED);
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // A client without the mod (or with a build predating this payload) cannot receive it;
            // sending anyway would disconnect them.
            if (!ServerPlayNetworking.canSend(handler.player, FrozenDyModePayload.TYPE)) {
                return;
            }
            ServerPlayNetworking.send(handler.player, currentPayload());
        });
    }
}
