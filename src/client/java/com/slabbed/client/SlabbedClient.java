package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.util.RuntimeDiagnostics;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.InvocationTargetException;

public final class SlabbedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RuntimeDiagnostics.logInspectSessionStart();
        SlabbedModelLoadingPlugin.init();
        SlabAnchorClientSync.init();
        RuntimeDiagnostics.initBsFbLiveTraceClient();
        initGapFillerOverlay();
        initTargetDyOverlay();
        initScreenshotCaptureService();
    }

    private static void initGapFillerOverlay() {
        if (!SlabbedClientFlags.GAP_FILL) {
            return;
        }
        invokeStaticInit(
                "com.slabbed.client.GapFillerOverlay",
                "gap filler overlay");
    }

    private static void initTargetDyOverlay() {
        if (!SlabbedClientFlags.TARGET_DY_OVERLAY) {
            return;
        }
        invokeStaticInit(
                "com.slabbed.client.TargetDyOverlay",
                "target dy overlay");
    }

    private static void initScreenshotCaptureService() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        invokeStaticInit(
                "com.slabbed.client.ScreenshotCaptureService",
                "screenshot capture service");
    }

    private static void invokeStaticInit(String className, String label) {
        try {
            Class<?> hookClass = Class.forName(className);
            hookClass.getMethod("init").invoke(null);
        } catch (ClassNotFoundException e) {
            Slabbed.LOGGER.warn("{} is unavailable in this environment", label);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError e) {
            Slabbed.LOGGER.warn("Failed to initialize {}", label, e);
        }
    }
}
