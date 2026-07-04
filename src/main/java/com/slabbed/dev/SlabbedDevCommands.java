package com.slabbed.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
 *
 * The actual audit classes ({@code com.slabbed.dev.audit.CategoryAuditRunner}/{@code
 * CategoryAuditReport}) live in a dev/test-only package excluded from the release jar
 * (a file-writing test harness, not player-facing tooling — see build.gradle's
 * pre-release hygiene gate), so this command reaches them via reflection and reports
 * "unavailable" instead of failing to load when they're absent.
 */
public class SlabbedDevCommands {

    private static final String RUNNER_CLASS_NAME = "com.slabbed.dev.audit.CategoryAuditRunner";

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerAll(dispatcher));
    }

    private static void registerAll(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("slabdev")
                        .requires(src -> src.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS)))
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

        Object report;
        try {
            Class<?> runnerClass = Class.forName(RUNNER_CLASS_NAME);
            Method run = runnerClass.getMethod("run", ServerWorld.class, BlockPos.class, String.class);
            report = run.invoke(null, world, anchor, category);
        } catch (ClassNotFoundException e) {
            source.sendError(Text.literal(
                    "[slabdev] audit is not available in this build (dev-only tool, excluded from release)."));
            return 0;
        } catch (ReflectiveOperationException e) {
            Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null
                    ? ite.getCause() : e;
            source.sendError(Text.literal("[slabdev] audit failed: " + cause.getMessage()));
            return 0;
        }

        // Sanitise timestamp for use as a directory name on all platforms.
        String timestamp = Instant.now().toString()
                .replace(":", "-")
                .replace(".", "-");

        Path outputDir = FabricLoader.getInstance().getGameDir()
                .resolve("run-headless")
                .resolve("slabbed-audit")
                .resolve(timestamp);

        try {
            Method writeTo = report.getClass().getMethod("writeTo", Path.class);
            writeTo.invoke(report, outputDir);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null
                    ? ite.getCause() : e;
            source.sendError(Text.literal("[slabdev] Failed to write report: " + cause.getMessage()));
            return 0;
        }

        try {
            Method placementFailures = report.getClass().getMethod("placementFailures");
            Method neighborFailures = report.getClass().getMethod("neighborFailures");
            Method reloadFailures = report.getClass().getMethod("reloadFailures");
            source.sendFeedback(() -> Text.literal(String.format(
                    "[slabdev] audit %s: placement_fail=%s neighbor_fail=%s reload_fail=%s  report→ %s",
                    category,
                    invokeQuiet(placementFailures, report),
                    invokeQuiet(neighborFailures, report),
                    invokeQuiet(reloadFailures, report),
                    outputDir)), false);
        } catch (ReflectiveOperationException e) {
            source.sendFeedback(() -> Text.literal("[slabdev] audit " + category + " complete, report→ " + outputDir), false);
        }

        return 1;
    }

    private static Object invokeQuiet(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException e) {
            return "?";
        }
    }
}
