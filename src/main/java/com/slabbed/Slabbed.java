package com.slabbed;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

@Mod(Slabbed.MOD_ID)
public class Slabbed {
    public static final String MOD_ID = "slabbed";
    public static final Logger LOGGER = LoggerFactory.getLogger(Slabbed.class);

    public Slabbed(IEventBus modEventBus) {
        LOGGER.info("Slabbed initialized");
        com.slabbed.anchor.SlabAnchorAttachment.register(modEventBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            initClientFeatures(modEventBus);
        }
        if (!FMLLoader.isProduction()) {
            initDevFeatures();
        }
    }

    private static void initClientFeatures(IEventBus modEventBus) {
        try {
            Class<?> hookClass = Class.forName("com.slabbed.client.SlabbedClient");
            hookClass.getMethod("init", IEventBus.class).invoke(null, modEventBus);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Client hook is unavailable in this environment");
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError e) {
            LOGGER.warn("Failed to initialize client hook", e);
        }
    }

    private static void initDevFeatures() {
        registerDevHook("com.slabbed.dev.SlabbedDevCommands", "register");
        registerDevHook("com.slabbed.dev.SlabbedLab", "register");
    }

    private static void registerDevHook(String className, String methodName) {
        try {
            Class<?> hookClass = Class.forName(className);
            hookClass.getMethod(methodName).invoke(null);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Dev hook {} is unavailable in this environment", className);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError e) {
            LOGGER.warn("Failed to initialize dev hook {}", className, e);
        }
    }
}
