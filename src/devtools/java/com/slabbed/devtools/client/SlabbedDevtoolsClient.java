package com.slabbed.devtools.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.slabbed.util.BuildStamp;
import com.slabbed.devtools.util.LiveCursorIntentRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Client half of the dev diagnostics: the {@code /slabdev} command tree, the target-dy HUD
 * layer (a registered GUI layer — no mixin needed on this loader), and the stale-model
 * sentinel's tick/unload feeds. The F3+A full-invalidate suppression window from the donor
 * has no loader event here and is deliberately not ported; the sentinel may ping once after
 * a manual reload instead of staying silent.
 */
public final class SlabbedDevtoolsClient {
    private SlabbedDevtoolsClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(SlabbedDevtoolsClient::onRegisterGuiLayers);
        NeoForge.EVENT_BUS.addListener(SlabbedDevtoolsClient::onRegisterClientCommands);
        NeoForge.EVENT_BUS.addListener(SlabbedDevtoolsClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(SlabbedDevtoolsClient::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(SlabbedDevtoolsClient::onRenderHighlight);
        NeoForge.EVENT_BUS.addListener(SlabbedDevtoolsClient::onLoggingOut);
        SlabModelStaleSentinelClient.init();
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath("slabbed", "target_dy"),
                (guiGraphics, deltaTracker) -> TargetDyOverlay.render(guiGraphics));
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LiveCursorCaptureClient.onClientTick(minecraft);
        if (minecraft.level != null) {
            LiveCursorIntentRecorder.samplePendingPlacements(
                    minecraft.level,
                    minecraft.level.getGameTime());
        }
        SlabModelStaleSentinelClient.onEndClientTick(minecraft);
    }

    private static void onRenderHighlight(RenderHighlightEvent.Block event) {
        LiveCursorCaptureClient.onRenderHighlight(event);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        LiveCursorIntentRecorder.recordShutdown();
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() != null && event.getLevel().isClientSide()) {
            SlabModelStaleSentinelClient.onClientChunkUnload(event.getChunk().getPos());
        }
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("slabdev")
                .then(Commands.literal("debug")
                        .executes(context -> setTargetDyOverlay(!TargetDyOverlay.isEnabled()))
                        .then(Commands.literal("on")
                                .executes(context -> setTargetDyOverlay(true)))
                        .then(Commands.literal("off")
                                .executes(context -> setTargetDyOverlay(false)))
                        .then(Commands.literal("toggle")
                                .executes(context -> setTargetDyOverlay(!TargetDyOverlay.isEnabled()))))
                .then(Commands.literal("record")
                        .executes(context -> setLiveCursorRecorder(LiveCursorIntentRecorder.toggle()))
                        .then(Commands.literal("on")
                                .executes(context -> setLiveCursorRecorderTo(true)))
                        .then(Commands.literal("off")
                                .executes(context -> setLiveCursorRecorderTo(false)))
                        .then(Commands.literal("toggle")
                                .executes(context -> setLiveCursorRecorder(
                                        LiveCursorIntentRecorder.toggle()))));
        event.getDispatcher().register(root);
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
