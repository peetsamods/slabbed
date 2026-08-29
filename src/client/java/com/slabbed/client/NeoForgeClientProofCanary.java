package com.slabbed.client;

import com.slabbed.Slabbed;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Default-off NeoForge client proof route canary.
 *
 * <p>This is diagnostics-only: it proves that branch-local runClient reaches a
 * NeoForge client tick hook without changing gameplay behavior.
 */
public final class NeoForgeClientProofCanary {
    private static final String ENABLE_PROPERTY = "slabbed.neoforge.clientProofCanary";

    private static boolean initialized;
    private static boolean tickLogged;
    private static int ticks;

    private NeoForgeClientProofCanary() {
    }

    public static void init(IEventBus eventBus) {
        if (initialized || !Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        initialized = true;
        Slabbed.LOGGER.info(
                "[MC1211_NEOFORGE_CLIENT_PROOF_CANARY_START] enabled=true route=runClient diagnosticsOnly=true semanticsChanged=false");
        eventBus.addListener(NeoForgeClientProofCanary::onClientTickPost);
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        if (tickLogged) {
            return;
        }
        ticks++;
        Minecraft client = Minecraft.getInstance();
        String screen = client.screen == null ? "none" : client.screen.getClass().getName();
        boolean worldReady = client.level != null;
        boolean playerReady = client.player != null;
        Slabbed.LOGGER.info(
                "[MC1211_NEOFORGE_CLIENT_PROOF_CANARY] route=runClient event=ClientTickEvent.Post tick={} screen={} worldReady={} playerReady={} diagnosticsOnly=true semanticsChanged=false",
                ticks,
                screen,
                worldReady,
                playerReady);
        tickLogged = true;
    }
}
