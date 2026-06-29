package com.slabbed.client;

import net.minecraftforge.eventbus.api.IEventBus;

public final class SlabbedClient {
    private SlabbedClient() {
    }

    public static void init(IEventBus modEventBus) {
        SlabbedModelLoadingPlugin.init(modEventBus);
    }
}
