package com.slabbed.client;

import com.slabbed.Slabbed;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.InvocationTargetException;

public final class SlabbedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        initRuntimeDiagnostics("logInspectSessionStart", "inspect diagnostics",
                Boolean.getBoolean("slabbed.inspect") || Boolean.getBoolean("slabbed.b2.live.trace"));
        SlabbedModelLoadingPlugin.init();
        SlabAnchorClientSync.init();
        initRuntimeDiagnostics("initBsFbLiveTraceClient", "BS/FB live trace client",
                Boolean.getBoolean("slabbed.bsfb.live.trace"));
        initGapFillerOverlay();
        initScreenshotCaptureService();
    }

    private static void initRuntimeDiagnostics(String methodName, String label, boolean enabled) {
        if (!enabled) {
            return;
        }
        invokeStaticNoArg("com.slabbed.util.RuntimeDiagnostics", methodName, label);
    }

    private static void initGapFillerOverlay() {
        if (!SlabbedClientFlags.GAP_FILL) {
            return;
        }
        invokeStaticInit(
                "com.slabbed.client.GapFillerOverlay",
                "gap filler overlay");
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
        invokeStaticNoArg(className, "init", label);
    }

    private static void invokeStaticNoArg(String className, String methodName, String label) {
        try {
            Class<?> hookClass = Class.forName(className);
            hookClass.getMethod(methodName).invoke(null);
        } catch (ClassNotFoundException e) {
            Slabbed.LOGGER.warn("{} is unavailable in this environment", label);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError e) {
            Slabbed.LOGGER.warn("Failed to initialize {}", label, e);
        }
    }
}
