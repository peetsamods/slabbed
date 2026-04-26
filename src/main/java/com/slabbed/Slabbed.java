package com.slabbed;

import com.slabbed.dev.SlabbedDevCommands;
import com.slabbed.dev.SlabbedLab;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slabbed implements ModInitializer {
    public static final String MOD_ID = "slabbed";
    public static final Logger LOGGER = LoggerFactory.getLogger(Slabbed.class);

    @Override
    public void onInitialize() {
        LOGGER.info("Slabbed initialized");
        SlabbedDevCommands.register();
        SlabbedLab.register();
    }
}
