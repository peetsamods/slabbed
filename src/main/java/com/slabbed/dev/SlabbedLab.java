package com.slabbed.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Dev-only bootstrap for Slabbed Lab.
 *
 * /slablab                — status probe
 * /slablab fixture basic  — places a 3-lane repro pad (dev-only fixture)
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
                        .then(literal("fixture")
                                .then(literal("basic")
                                        .executes(SlabbedLab::placeBasicFixture)))
        );
    }

    private static int placeBasicFixture(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();

        // Origin: 1 block below source feet, 3 blocks in +Z — mirrors audit runner offset style.
        BlockPos origin = BlockPos.ofFloored(source.getPosition()).add(0, -1, 3);

        SlabbedLabFixtures.PlaceResult result = SlabbedLabFixtures.placeBasicFixture(world, origin);
        if (!result.ok()) {
            source.sendError(Text.literal("[slablab] " + result.error()));
            return 0;
        }

        StringBuilder msg = new StringBuilder("[slablab] basic fixture placed (dev-only).\n");
        for (Map.Entry<String, BlockPos> e : result.positions().entrySet()) {
            msg.append("  ").append(e.getKey()).append(" \u2192 ").append(e.getValue().toShortString()).append("\n");
        }
        msg.append("  Candidate space: one block above each support.");

        String finalMsg = msg.toString();
        source.sendFeedback(() -> Text.literal(finalMsg), false);
        return 1;
    }
}
