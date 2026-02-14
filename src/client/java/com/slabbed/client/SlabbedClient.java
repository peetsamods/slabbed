package com.slabbed.client;

import net.fabricmc.api.ClientModInitializer;
import com.slabbed.client.debug.TargetDebugLogger;

public final class SlabbedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SlabbedModelLoadingPlugin.init();
        TargetDebugLogger.init();
    }
}
