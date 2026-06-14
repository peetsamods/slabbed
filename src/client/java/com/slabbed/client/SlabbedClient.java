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
        initGapFillerOverlay();
        initTargetDyOverlay();
        initScreenshotCaptureService();
    }

    private static void initTargetDyOverlay() {
        // Always register the overlay + the /slabdy toggle (the overlay self-gates on its runtime
        // enabled flag, which starts from -Dslabbed.targetDyOverlay).
        TargetDyOverlay.init();
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, access) -> dispatcher.register(
                        net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("slabdy")
                                .executes(ctx -> {
                                    boolean on = TargetDyOverlay.toggle();
                                    ctx.getSource().sendFeedback(net.minecraft.text.Text.literal(
                                            "[slabbed] target dy overlay " + (on ? "ON" : "OFF")));
                                    return 1;
                                })));
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
