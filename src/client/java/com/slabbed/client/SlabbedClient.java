package com.slabbed.client;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SlabbedClient {
    private SlabbedClient() {
    }

    public static void init(IEventBus modEventBus) {
        SlabbedModelLoadingPlugin.init(modEventBus);
        OffsetOutlineEvents.init(MinecraftForge.EVENT_BUS);
        OffsetTargetingEvents.init(MinecraftForge.EVENT_BUS);
        TargetDyOverlay.init(MinecraftForge.EVENT_BUS);
    }
}
