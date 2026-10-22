package com.slabbed.client;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.util.ArrayList;

public final class IssueReportClient {
    private record PendingRequest(Screen screen, Object level) { }
    private static PendingRequest pendingChatRequest;

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(ClientCommands.literal("slabbed_report_issue").executes(context -> {
                    queueReview();
                    return 1;
                })));
        // Defer until the click/command handler finishes. RunCommand may retain ChatScreen,
        // while a typed command may close it; both must reach the same review. A replacement
        // screen or disconnected/replaced world invalidates the one-tick request.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PendingRequest request = pendingChatRequest;
            pendingChatRequest = null;
            if (request == null) return;
            Screen current = client.gui.screen();
            if (IssueReportSession.canOpenRequest(request.level(), client.level,
                    request.screen(), current, client.player != null)) {
                open(client, current);
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            boolean titleMenu = screen instanceof TitleScreen;
            if (!titleMenu && (!(screen instanceof PauseScreen pause) || !pause.showsPauseMenu())) return;
            Button button = Button.builder(Component.translatable("slabbed.report.entry"),
                    ignored -> open(client, screen)).bounds(0, 0, 176, 20).build();
            button.setTooltip(Tooltip.create(Component.translatable("slabbed.report.entry")));
            var positioner = new MenuEntryPositioner(screen, button, titleMenu);
            positioner.update();
            Screens.getWidgets(screen).add(button);
            // Recheck after other callbacks/mods have added their controls.
            ScreenEvents.beforeExtract(screen).register((current, graphics, mouseX, mouseY, delta) ->
                    positioner.update());
        });
    }

    /** Consume only the notice's exact local action before vanilla can forward a command. */
    public static boolean handleNoticeClick(String command) {
        if (!IssueReportSession.isReportCommand(command)) return false;
        queueReview();
        return true;
    }

    private static void queueReview() {
        Minecraft client = Minecraft.getInstance();
        pendingChatRequest = new PendingRequest(client.gui.screen(), client.level);
    }

    private static final class MenuEntryPositioner {
        private final Screen screen;
        private final Button button;
        private final boolean titleMenu;
        private java.util.List<IssueReportPlacement.Box> previous = java.util.List.of();

        MenuEntryPositioner(Screen screen, Button button, boolean titleMenu) {
            this.screen = screen;
            this.button = button;
            this.titleMenu = titleMenu;
        }

        void update() {
            var geometry = new ArrayList<IssueReportPlacement.Box>();
            geometry.add(new IssueReportPlacement.Box(0, 0, screen.width, screen.height));
            // Keep title artwork/footer or the pause title clear as well as all controls.
            if (titleMenu) {
                geometry.add(new IssueReportPlacement.Box(screen.width / 2 - 155, 30, 310, 90));
                geometry.add(new IssueReportPlacement.Box(0, screen.height - 14, screen.width, 14));
            } else {
                geometry.add(new IssueReportPlacement.Box(screen.width / 2 - 100, 32, 200, 20));
            }
            int preferredY = titleMenu ? screen.height - 24 : screen.height / 4 + 132;
            for (var child : screen.children()) {
                if (child != button && child instanceof AbstractWidget widget && widget.visible) {
                    geometry.add(new IssueReportPlacement.Box(widget.getX(), widget.getY(),
                            widget.getWidth(), widget.getHeight()));
                    if (!titleMenu && (widget.getMessage().equals(CommonComponents.disconnectButtonLabel(true))
                            || widget.getMessage().equals(CommonComponents.disconnectButtonLabel(false)))) {
                        preferredY = widget.getY() + widget.getHeight() + 8;
                    }
                }
            }
            if (geometry.equals(previous)) return;
            previous = java.util.List.copyOf(geometry);
            var occupied = geometry.subList(1, geometry.size());
            var place = IssueReportPlacement.findNear(screen.width, screen.height, 176, preferredY, occupied);
            button.setMessage(Component.translatable("slabbed.report.entry"));
            if (place.isEmpty()) {
                // A crowded modded menu may have room for the compact label and full tooltip.
                place = IssueReportPlacement.findNear(screen.width, screen.height, 100, preferredY, occupied);
                button.setMessage(Component.translatable("slabbed.report.compact_entry"));
            }
            button.visible = place.isPresent();
            place.ifPresent(box -> {
                button.setX(box.x());
                button.setY(box.y());
                button.setWidth(box.width());
            });
        }
    }

    private static void open(Minecraft client, Screen parent) {
        client.gui.setScreen(new IssueReportScreen(parent, new IssueReport(
                version("minecraft"), version("fabricloader"), version("slabbed"), version("fabric-api"))));
    }

    private static String version(String id) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(null);
    }

    static void openBrowser(URI uri) throws ReflectiveOperationException {
        // The native URI opener moved in 26.3. Resolve only this vanilla compatibility seam;
        // both implementations may report OS failure asynchronously, so Copy link stays visible.
        if ("26.2".equals(version("minecraft"))) {
            Object platform = net.minecraft.util.Util.getPlatform();
            platform.getClass().getMethod("openUri", URI.class).invoke(platform, uri);
        } else {
            Class.forName("com.mojang.blaze3d.Blaze3D").getMethod("openUri", URI.class).invoke(null, uri);
        }
    }

    private IssueReportClient() { }
}
