package com.slabbed.dev;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Dev-only bootstrap for Slabbed Lab.
 * /slablab — status probe only; no gameplay behavior.
 */
public final class SlabbedLab {

    private SlabbedLab() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerAll(dispatcher));
    }

    private static void registerAll(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("slablab")
                        .requires(src -> src.getPermissions().hasPermission(
                                new Permission.Level(PermissionLevel.GAMEMASTERS)))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(
                                    () -> Text.literal("[slablab] slabbed lab bootstrap ready."),
                                    false
                            );
                            return 1;
                        })
        );
    }
}
