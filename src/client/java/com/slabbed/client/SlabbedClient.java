package com.slabbed.client;

import net.fabricmc.api.ClientModInitializer;

public final class SlabbedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SlabbedModelLoadingPlugin.init();
    }
}
