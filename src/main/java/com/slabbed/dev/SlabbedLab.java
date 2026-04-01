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
 * /slablab fixture clear  — removes the fixture at the deterministic origin
 * /slablab fixture reset  — clears then rebuilds the fixture
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
                                        .executes(SlabbedLab::placeBasicFixture))
                                .then(literal("clear")
                                        .executes(SlabbedLab::clearFixture))
                                .then(literal("reset")
                                        .executes(SlabbedLab::resetFixture)))
        );
    }

    // -------------------------------------------------------------------------
    // Shared origin — all fixture commands must use this method.
    // Mirrors the audit runner's offset style: 1 below feet, 3 in +Z.
    // -------------------------------------------------------------------------

    private static BlockPos fixtureOrigin(ServerCommandSource source) {
        return BlockPos.ofFloored(source.getPosition()).add(0, -1, 3);
    }

    // -------------------------------------------------------------------------
    // Command handlers
    // -------------------------------------------------------------------------

    private static int placeBasicFixture(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos origin = fixtureOrigin(source);

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

    private static int clearFixture(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos origin = fixtureOrigin(source);

        SlabbedLabFixtures.PlaceResult result = SlabbedLabFixtures.clearBasicFixture(world, origin);
        if (!result.ok()) {
            source.sendError(Text.literal("[slablab] " + result.error()));
            return 0;
        }

        StringBuilder msg = new StringBuilder("[slablab] basic fixture cleared (dev-only).\n");
        for (Map.Entry<String, BlockPos> e : result.positions().entrySet()) {
            msg.append("  ").append(e.getKey()).append(" \u2192 ").append(e.getValue().toShortString()).append(" removed\n");
        }
        msg.append("  origin: ").append(origin.toShortString()).append(".");

        String finalMsg = msg.toString();
        source.sendFeedback(() -> Text.literal(finalMsg), false);
        return 1;
    }

    private static int resetFixture(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos origin = fixtureOrigin(source);

        // Step 1: verify and clear existing fixture.
        SlabbedLabFixtures.PlaceResult clearResult = SlabbedLabFixtures.clearBasicFixture(world, origin);
        if (!clearResult.ok()) {
            source.sendError(Text.literal("[slablab] reset aborted — clear failed: " + clearResult.error()));
            return 0;
        }

        // Step 2: rebuild fixture at the same origin.
        SlabbedLabFixtures.PlaceResult placeResult = SlabbedLabFixtures.placeBasicFixture(world, origin);
        if (!placeResult.ok()) {
            source.sendError(Text.literal("[slablab] reset: cleared but rebuild failed: " + placeResult.error()));
            return 0;
        }

        StringBuilder msg = new StringBuilder("[slablab] basic fixture reset (dev-only).\n");
        for (Map.Entry<String, BlockPos> e : placeResult.positions().entrySet()) {
            msg.append("  ").append(e.getKey()).append(" \u2192 ").append(e.getValue().toShortString()).append("\n");
        }
        msg.append("  Candidate space: one block above each support.");

        String finalMsg = msg.toString();
        source.sendFeedback(() -> Text.literal(finalMsg), false);
        return 1;
    }
}
