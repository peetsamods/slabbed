package com.slabbed.client;

import com.slabbed.Slabbed;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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
        initTargetDyCommand();
        initScreenshotCaptureService();
        initDyFingerprintDump();
    }

    private static void initTargetDyCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("slabdev")
                        .then(ClientCommands.literal("debug")
                                .executes(context -> setTargetDyOverlay(!TargetDyOverlay.isEnabled()))
                                .then(ClientCommands.literal("on")
                                        .executes(context -> setTargetDyOverlay(true)))
                                .then(ClientCommands.literal("off")
                                        .executes(context -> setTargetDyOverlay(false)))
                                .then(ClientCommands.literal("toggle")
                                        .executes(context -> setTargetDyOverlay(!TargetDyOverlay.isEnabled()))))));
    }

    private static int setTargetDyOverlay(boolean enabled) {
        TargetDyOverlay.setEnabled(enabled);
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "[slabdev] debug overlay: " + (enabled ? "on" : "off")));
        }
        return 1;
    }

    private static void initDyFingerprintDump() {
        // Tier-2 client dy-fingerprint dump (RELEASE_SANITY_CHECKLIST §3); dev-only, excluded from the jar.
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        invokeStaticInit("com.slabbed.client.DyFingerprintDump", "dy fingerprint dump");
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
