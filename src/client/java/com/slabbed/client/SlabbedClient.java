package com.slabbed.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

public final class SlabbedClient {
    private SlabbedClient() {
    }

    public static void init(IEventBus modEventBus) {
        SlabbedModelLoadingPlugin.init(modEventBus);
        SlabAnchorClientSync.init(NeoForge.EVENT_BUS);
    }
}
