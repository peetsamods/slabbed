package com.slabbed;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slabbed implements ModInitializer {
    public static final String MOD_ID = "slabbed";
    private static final Logger LOGGER = LoggerFactory.getLogger(Slabbed.class);

    @Override
    public void onInitialize() {
        LOGGER.info("Slabbed initialized");
    }
}
