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
        com.slabbed.anchor.SlabAnchorAttachment.register();
        com.slabbed.util.SlabbedAuditBridge.bootstrapLiveRecorder();
        initShippedDebugCommands();
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            initDevOnlyFeatures();
        }
    }

    /**
     * Ships in EVERY jar, default off — the standing rule, and Maintainer's ruling (2026-08-07) that
     * "ships" has to mean an op can actually invoke it on a release build, not that the bytes are
     * present but unreachable. This used to sit inside {@link #initDevOnlyFeatures()} behind
     * {@code isDevelopmentEnvironment()}, so {@code SlabbedDevCommands} shipped as dead weight.
     *
     * <p>Registering it costs one Brigadier node at server start. {@code /slabdev} is gated at
     * op level 2 (GAMEMASTERS), installs no tick hook, writes nothing to the world save, and does
     * nothing at all until someone types it — the same shape {@code /slabdy} already has on the
     * client. Its {@code audit} subcommand reaches the dev-only audit harness reflectively and
     * reports "not available in this build" when that harness is absent, which is the release case.
     */
    private static void initShippedDebugCommands() {
        registerDevHook("com.slabbed.dev.SlabbedDevCommands", "register");
    }

    /**
     * Dev-environment only, and deliberately so. {@code /slabrig} is a live-test scene builder and
     * {@code SlabbedLab} is a fixture harness; both are excluded from the release jar by
     * build.gradle's pre-release hygiene gate, and the 26.2 line shipping {@code /slabrig} in a
     * release jar was logged as a defect. These do NOT follow {@code /slabdev} out of the gate.
     */
    private static void initDevOnlyFeatures() {
        registerDevHook("com.slabbed.dev.SlabbedLab", "register");
        registerDevHook("com.slabbed.command.SlabRigCommand", "register");
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
