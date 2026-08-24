package com.slabbed.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.BuildStamp;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /slabbedver} — answers "is the dev client actually running the pass I just built".
 *
 * <p><b>Why {@code /slabdev build} does not already answer this.</b> {@link BuildStamp#GIT_SHA}
 * reads {@code unknown} on a dev launch — it comes from a jar manifest, and a {@code runClient}
 * launch runs from a classes directory with no manifest at all. {@link
 * BuildStamp#RUNTIME_CONTENT_SHA256} DOES work in dev (it hashes the classes/resources tree
 * instead), but a hex digest cannot be eyeballed against "did I actually rebuild after the last
 * edit" — it can only be diffed against another hash. This command adds the one thing neither
 * gives: a human-readable name for the change under test right now.
 *
 * <p>{@link #ACTIVE_PASS} is a plain source constant, updated by hand at the start of each
 * live-testing pass. It is deliberately NOT derived from git log or commit messages — this line's
 * discretion law keeps session/process narrative out of the tracked tree, and a constant naming
 * only the CHANGE under test (never a date, a session, or who is testing it) stays on the correct
 * side of that line, same as a `CHANGELOG.md` entry would.
 *
 * <p>Same family as {@code /slabrig}/{@code /slabkit}/{@code /slabcheck}: dev-gated in {@code
 * Slabbed.initDevFeatures}, and excluded from both release artifacts by the existing
 * {@code com/slabbed/command/**} entry in {@code releaseArtifactExclusions} — nothing new needed
 * there. Never in a release jar, not merely default-off.
 */
public final class SlabbedVerCommand {

    /** Update this at the start of each live-testing pass. Name the CHANGE, nothing else. */
    private static final String ACTIVE_PASS =
            "Phase 4: relocated placement aim (scaffolding column seat)";

    private SlabbedVerCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("slabbedver")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(SlabbedVerCommand::report));
    }

    private static int report(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("[slabbedver] testing: " + ACTIVE_PASS), false);
        source.sendSuccess(() -> Component.literal("[slabbedver] " + BuildStamp.describeShort()), false);
        source.sendSuccess(() -> Component.literal("[slabbedver] runtime content sha256: "
                + BuildStamp.RUNTIME_CONTENT_SHA256
                + (BuildStamp.hasExactRuntimeContent() ? "" : " (not exact — best-effort identity only)")),
                false);
        source.sendSuccess(() -> Component.literal("[slabbedver] frozenDy="
                + SlabAnchorAttachment.FROZEN_DY_ENABLED
                + " dev=" + FabricLoader.getInstance().isDevelopmentEnvironment()), false);
        return 1;
    }
}
