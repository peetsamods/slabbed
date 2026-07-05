package com.slabbed.client;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

/**
 * A brief, one-time chat notice ("Slabbed is in beta...") with a click-to-dismiss link.
 *
 * <p>Shown at most once per client session PER WORLD (hopping back into the SAME world in one
 * sitting doesn't repeat it — see {@link BetaNoticeSessionGate}), AND permanently skipped for
 * any specific world/server the player has clicked "don't show this again" for — but a brand
 * new world/server the player hasn't dismissed will still show it, even if they've dismissed it
 * elsewhere. That's a deliberate choice (the maintainer's explicit request): this is a per-world
 * preference, not a single global switch.
 */
public final class BetaNoticeClient {

    private BetaNoticeClient() {
    }

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("slabbed_dismiss_beta_notice")
                        .executes(BetaNoticeClient::runDismiss)));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String worldKey = currentWorldKey(client);
            if (!BetaNoticeSessionGate.shouldShow(worldKey)) {
                return;
            }
            if (client.player == null) {
                return;
            }
            BetaNoticeSessionGate.markShown(worldKey);
            client.player.sendSystemMessage(
                    Component.literal("Slabbed is in beta — expect some rough edges while it's being developed. ")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
                            .append(Component.literal("[Don't show again]")
                                    .withStyle(ChatFormatting.GRAY, ChatFormatting.UNDERLINE)
                                    .withStyle(style -> style.withClickEvent(
                                            new ClickEvent.RunCommand("/slabbed_dismiss_beta_notice")))));
        });
    }

    private static int runDismiss(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft client = Minecraft.getInstance();
        String worldKey = currentWorldKey(client);
        BetaNoticeDismissedWorlds.dismiss(worldKey);
        ctx.getSource().sendFeedback(Component.literal("Won't show the beta notice again for this world.")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        return 1;
    }

    /**
     * A stable identity for "this world" — the save folder for singleplayer (unique; Minecraft
     * de-dupes save-folder names on its own), the server address for multiplayer. Returns
     * {@code null} for a context this can't identify (e.g. a Realms world without a resolvable
     * address) — a null key is never dismissible and never considered dismissed, so the notice
     * simply behaves as session-only there instead of persisting incorrectly.
     */
    private static String currentWorldKey(Minecraft client) {
        if (client == null) {
            return null;
        }
        ServerData serverData = client.getCurrentServer();
        if (serverData != null && serverData.ip != null) {
            return "server:" + serverData.ip;
        }
        if (client.isLocalServer() && client.getSingleplayerServer() != null) {
            try {
                return "singleplayer:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
                        .toAbsolutePath()
                        .normalize();
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }
}
