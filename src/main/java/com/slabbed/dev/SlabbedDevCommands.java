package com.slabbed.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.slabbed.dev.audit.CategoryAuditReport;
import com.slabbed.dev.audit.CategoryAuditRunner;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Dev-only commands gated behind op level 2.
 *
 * /slabdev audit <category>
 *   Runs a headless 3-lane audit (FULL_BLOCK / BOTTOM_SLAB / TOP_SLAB) for
 *   the named category and writes a report under:
 *     <gameDir>/run-headless/slabbed-audit/<timestamp>/<category>-audit.txt
 */
public class SlabbedDevCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerAll(dispatcher));
    }

    private static void registerAll(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("slabdev")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(literal("audit")
                                .then(argument("category", StringArgumentType.word())
                                        .executes(SlabbedDevCommands::runAudit)))
        );
    }

    private static int runAudit(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String category = StringArgumentType.getString(ctx, "category");
        ServerWorld world = source.getWorld();

        // Anchor: support goes here, candidate one block above.
        // Placed 4 blocks in front of and one below the command source to stay out of the way.
        BlockPos anchor = BlockPos.ofFloored(source.getPosition()).add(0, -1, 4);

        CategoryAuditReport report = CategoryAuditRunner.run(world, anchor, category);

        // Sanitise timestamp for use as a directory name on all platforms.
        String timestamp = Instant.now().toString()
                .replace(":", "-")
                .replace(".", "-");

        Path outputDir = FabricLoader.getInstance().getGameDir()
                .resolve("run-headless")
                .resolve("slabbed-audit")
                .resolve(timestamp);

        try {
            report.writeTo(outputDir);
        } catch (IOException e) {
            source.sendError(Text.literal("[slabdev] Failed to write report: " + e.getMessage()));
            return 0;
        }

        source.sendFeedback(() -> Text.literal(String.format(
                "[slabdev] audit %s: placement_fail=%d neighbor_fail=%d reload_fail=%d  report→ %s",
                category,
                report.placementFailures(),
                report.neighborFailures(),
                report.reloadFailures(),
                outputDir)), false);

        return 1;
    }
}
