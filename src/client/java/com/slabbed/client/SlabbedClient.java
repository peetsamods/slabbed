package com.slabbed.client;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;

public final class SlabbedClient {
    private SlabbedClient() {
    }

    public static void init(IEventBus modEventBus) {
        SlabbedModelLoadingPlugin.init(modEventBus);
        SlabAnchorClientSync.init(MinecraftForge.EVENT_BUS);
        SlabPlacementHeightClientSync.init(MinecraftForge.EVENT_BUS);
        DeepDyConsentClientSync.init(MinecraftForge.EVENT_BUS);
    }
}
