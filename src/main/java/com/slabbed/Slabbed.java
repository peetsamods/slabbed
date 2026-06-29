package com.slabbed;

import com.slabbed.anchor.SlabAnchorCapabilities;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Slabbed.MOD_ID)
public class Slabbed {
    public static final String MOD_ID = "slabbed";
    public static final Logger LOGGER = LoggerFactory.getLogger(Slabbed.class);

    public Slabbed() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        SlabAnchorCapabilities.register(modEventBus);

        LOGGER.info("Slabbed Forge 1.20.1 scaffold initialized");
    }
}
