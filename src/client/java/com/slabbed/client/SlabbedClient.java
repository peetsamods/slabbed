package com.slabbed.client;

import com.slabbed.client.dev.TargetInspectorOverlay;
import net.fabricmc.api.ClientModInitializer;

public final class SlabbedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SlabbedModelLoadingPlugin.init();
        TargetInspectorOverlay.register();
    }
}
