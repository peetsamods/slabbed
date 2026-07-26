package com.slabbed.client;

import com.slabbed.util.BuildStamp;
import com.slabbed.util.LiveCursorIntentRecorder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class SlabbedDiagnosticsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        registerSlabdev();
        SlabModelStaleSentinelClient.init();
    }

    private static void registerSlabdev() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("slabdev")
                        .then(ClientCommands.literal("debug")
                                .executes(context -> setTargetDyOverlay(!TargetDyOverlay.isEnabled()))
                                .then(ClientCommands.literal("on")
                                        .executes(context -> setTargetDyOverlay(true)))
                                .then(ClientCommands.literal("off")
                                        .executes(context -> setTargetDyOverlay(false)))
                                .then(ClientCommands.literal("toggle")
                                        .executes(context -> setTargetDyOverlay(!TargetDyOverlay.isEnabled()))))
                        .then(ClientCommands.literal("record")
                                .executes(context -> setLiveCursorRecorder(LiveCursorIntentRecorder.toggle()))
                                .then(ClientCommands.literal("on")
                                        .executes(context -> setLiveCursorRecorderTo(true)))
                                .then(ClientCommands.literal("off")
                                        .executes(context -> setLiveCursorRecorderTo(false)))
                                .then(ClientCommands.literal("toggle")
                                        .executes(context -> setLiveCursorRecorder(
                                                LiveCursorIntentRecorder.toggle()))))));
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

    private static int setLiveCursorRecorderTo(boolean target) {
        if (LiveCursorIntentRecorder.enabled() != target) {
            LiveCursorIntentRecorder.toggle();
        } else if (target) {
            LiveCursorIntentRecorder.bootstrap();
        }
        return setLiveCursorRecorder(target);
    }

    private static int setLiveCursorRecorder(boolean enabled) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "[slabdev] live cursor recorder: " + (enabled ? "on" : "off")
                            + " (" + LiveCursorIntentRecorder.currentLogPathDisplay() + ")"));
            client.player.sendSystemMessage(Component.literal(
                    "[slabdev] " + BuildStamp.describeShort()));
        }
        return 1;
    }
}
