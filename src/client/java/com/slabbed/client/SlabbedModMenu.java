package com.slabbed.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Optional entrypoint loaded by Mod Menu; reporting itself has no Mod Menu dependency. */
public final class SlabbedModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return IssueReportClient::createScreen;
    }
}
