package com.slabbed.client;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SlabbedClient {
    private SlabbedClient() {
    }

    public static void init(IEventBus modEventBus) {
        SlabbedModelLoadingPlugin.init(modEventBus);
        OffsetOutlineEvents.init(MinecraftForge.EVENT_BUS);
        // Crosshair targeting is now owned by GameRendererPickOffsetRaycastMixin (the single
        // offset-aware nearest-hit pick authority). The old OffsetTargetingEvents post-hoc
        // retarget hack is intentionally not registered — it raced GameRenderer.pick and only
        // rescued ordinary full blocks, causing fence/partial-block fall-through and triad drift.
        TargetDyOverlay.init(MinecraftForge.EVENT_BUS);
    }
}
