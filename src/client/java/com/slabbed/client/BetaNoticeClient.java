package com.slabbed.client;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;

/**
 * A brief, one-time chat notice ("Slabbed is in beta...") with a click-to-dismiss link.
 *
 * <p>Shown at most once per client session (hopping between worlds in one sitting doesn't
 * repeat it), AND permanently skipped for any specific world/server the player has clicked
 * "don't show this again" for — but a brand new world/server the player hasn't dismissed
 * will still show it, even if they've dismissed it elsewhere. That's a deliberate choice
 * (Maintainer's explicit request): this is a per-world preference, not a single global switch.
 */
public final class BetaNoticeClient {

    private static boolean shownThisSession = false;

    private BetaNoticeClient() {
    }

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("slabbed_dismiss_beta_notice")
                        .executes(BetaNoticeClient::runDismiss)));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (shownThisSession) {
                return;
            }
            String worldKey = currentWorldKey(client);
            if (BetaNoticeDismissedWorlds.isDismissed(worldKey)) {
                return;
            }
            shownThisSession = true;
            if (client.player == null) {
                return;
            }
            client.player.sendMessage(
                    Text.literal("Slabbed is in beta — expect some rough edges while it's being developed. ")
                            .formatted(Formatting.GRAY, Formatting.ITALIC)
                            .append(Text.literal("[Don't show again]")
                                    .formatted(Formatting.GRAY, Formatting.UNDERLINE)
                                    .styled(style -> style.withClickEvent(
                                            new ClickEvent.RunCommand("/slabbed_dismiss_beta_notice")))),
                    false);
        });
    }

    private static int runDismiss(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        String worldKey = currentWorldKey(client);
        BetaNoticeDismissedWorlds.dismiss(worldKey);
        ctx.getSource().sendFeedback(Text.literal("Won't show the beta notice again for this world.")
                .formatted(Formatting.GRAY, Formatting.ITALIC));
        return 1;
    }

    /**
     * A stable identity for "this world" — the save folder for singleplayer (unique; Minecraft
     * de-dupes save-folder names on its own), the server address for multiplayer. Returns
     * {@code null} for a context this can't identify (e.g. a Realms world without a resolvable
     * address) — a null key is never dismissible and never considered dismissed, so the notice
     * simply behaves as session-only there instead of persisting incorrectly.
     */
    private static String currentWorldKey(MinecraftClient client) {
        if (client == null) {
            return null;
        }
        ServerInfo serverInfo = client.getCurrentServerEntry();
        if (serverInfo != null && serverInfo.address != null) {
            return "server:" + serverInfo.address;
        }
        if (client.isIntegratedServerRunning() && client.getServer() != null) {
            try {
                return "singleplayer:" + client.getServer().getSavePath(WorldSavePath.ROOT)
                        .toAbsolutePath()
                        .normalize();
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }
}
