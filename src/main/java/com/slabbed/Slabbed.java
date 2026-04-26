package com.slabbed;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

public class Slabbed implements ModInitializer {
    public static final String MOD_ID = "slabbed";
    public static final Logger LOGGER = LoggerFactory.getLogger(Slabbed.class);

    @Override
    public void onInitialize() {
        LOGGER.info("Slabbed initialized");
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            initDevFeatures();
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
