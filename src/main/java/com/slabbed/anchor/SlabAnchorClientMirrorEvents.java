package com.slabbed.anchor;

import com.slabbed.Slabbed;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only lifecycle hooks for clearing the anchor mirror between network
 * sessions. The common mirror/network classes remain safe to load on dedicated
 * servers; this subscriber is registered only on the physical client.
 */
@Mod.EventBusSubscriber(modid = Slabbed.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlabAnchorClientMirrorEvents {
    private SlabAnchorClientMirrorEvents() {
    }

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        SlabAnchorClientMirror.clearAll();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SlabAnchorClientMirror.clearAll();
    }
}
