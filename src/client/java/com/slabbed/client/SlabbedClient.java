package com.slabbed.client;

import com.slabbed.Slabbed;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.InvocationTargetException;

public final class SlabbedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SlabbedModelLoadingPlugin.init();
        SlabAnchorClientSync.init();
        initBsFbLiveTraceClient();
        initGapFillerOverlay();
        initTargetDyOverlay();
        initScreenshotCaptureService();
    }

    private static void initBsFbLiveTraceClient() {
        if (!Boolean.getBoolean("slabbed.bsfb.live.trace")) {
            return;
        }
        invokeStaticInit(
                "com.slabbed.client.debug.BsFbLiveTraceClient",
                "BS-FB live trace client");
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
        if (!invokeStaticInit(
                "com.slabbed.client.TargetDyOverlay",
                "target dy overlay")) {
            return;
        }
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, access) -> dispatcher.register(
                        net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("slabdy")
                                .executes(ctx -> {
                                    boolean on = invokeTargetDyOverlayToggle();
                                    ctx.getSource().sendFeedback(net.minecraft.text.Text.literal(
                                            "[slabbed] target dy overlay " + (on ? "ON" : "OFF")));
                                    return 1;
                                })));
    }

    private static void initScreenshotCaptureService() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        invokeStaticInit(
                "com.slabbed.client.ScreenshotCaptureService",
                "screenshot capture service");
    }

    private static boolean invokeStaticInit(String className, String label) {
        try {
            Class<?> hookClass = Class.forName(className);
            hookClass.getMethod("init").invoke(null);
            return true;
        } catch (ClassNotFoundException e) {
            Slabbed.LOGGER.warn("{} is unavailable in this environment", label);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError e) {
            Slabbed.LOGGER.warn("Failed to initialize {}", label, e);
        }
        return false;
    }

    private static boolean invokeTargetDyOverlayToggle() {
        try {
            Class<?> overlayClass = Class.forName("com.slabbed.client.TargetDyOverlay");
            Object result = overlayClass.getMethod("toggle").invoke(null);
            return Boolean.TRUE.equals(result);
        } catch (ClassNotFoundException e) {
            Slabbed.LOGGER.warn("target dy overlay is unavailable in this environment");
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError e) {
            Slabbed.LOGGER.warn("Failed to toggle target dy overlay", e);
        }
        return false;
    }
}
