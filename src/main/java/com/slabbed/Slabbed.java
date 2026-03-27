package com.slabbed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.state.property.BooleanProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slabbed implements ModInitializer {
    public static final String MOD_ID = "slabbed";
    public static final Logger LOGGER = LoggerFactory.getLogger(Slabbed.class);

    public static final BooleanProperty IS_SLABBED = BooleanProperty.of("is_slabbed");

    @Override
    public void onInitialize() {
        LOGGER.info("Slabbed initialized");
    }
}
