package com.slabbed.util;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import java.util.List;
import java.util.function.Function;

/**
 * The Brigadier node structure for the two debug commands that ship in EVERY jar, default off:
 * {@code /slabdy} and {@code /slabdev}.
 *
 * <p><b>Why the tree lives here and not next to the client wiring.</b> the maintainer's standing rule says
 * both commands ship in every jar, and her 2026-08-07 ruling fixed the reading: the command must be
 * <em>invocable</em> on a shipped jar, not merely present as bytes. A claim like that is only worth
 * what its test is worth, and a client command tree is not reachable from a headless
 * dedicated-server GameTest run. So the tree is built here — pure Brigadier, generic over the
 * command source, no Minecraft and no client type anywhere in the signature — and
 * {@code ShippedDebugCommandsTest} registers these exact builders into a real
 * {@code CommandDispatcher} and executes real command strings against them. The client class is
 * then only the wiring: read the crosshair, print to chat.
 *
 * <p><b>Inert until invoked.</b> Building the tree registers no tick hook, no lifecycle listener and
 * no world-save writer, and touches no disk. Everything below runs only inside an
 * {@code executes(...)} body — i.e. only when someone types the command. That is the whole point of
 * the ruling: {@code /slabrig} was the counter-example (unconditional server-tick, entity-load and
 * spawn-veto hooks) and it was removed from the shipped jars for exactly that reason.
 *
 * <p><b>Honest degradation.</b> The overlay and the recorder are implemented in the development-only
 * diagnostics companion, which is not in a release jar. The commands do not pretend otherwise and
 * they do not throw: a subcommand whose implementation is absent reports
 * "{@value #NOT_AVAILABLE}". The subcommands that need nothing but shipped code —
 * {@code /slabdy row} and {@code build} — work in a release jar for real.
 */
public final class SlabbedDebugCommandTree {

    /** The exact phrase a subcommand reports when its implementation is not in this build. */
    public static final String NOT_AVAILABLE = "not available in this build";

    /**
     * Everything the tree needs from the environment it was registered into. One instance is built
     * per invocation, so nothing here is held across commands.
     */
    public interface Session {
        /** Send one line back to whoever typed the command. */
        void feedback(String line);

        /**
         * The formatted diagnostic dump for the current crosshair target, or an EMPTY list when
         * there is no block target. Never null.
         */
        List<String> targetRow();

        /** Is the target-dy overlay implementation present in this build? */
        boolean overlayAvailable();

        boolean overlayEnabled();

        void setOverlayEnabled(boolean enabled);

        /** Is the live-cursor recorder implementation present in this build? */
        boolean recorderAvailable();

        boolean recorderEnabled();

        void setRecorderEnabled(boolean enabled);

        /** Where the recorder is writing, for the feedback line. Never null. */
        String recorderStatus();

        /** The jar identity stamp, so a bug report can name the exact build. */
        String buildStamp();

        /**
         * The formatted chunk-capacity gauge for the chunk the player is standing in, or an EMPTY
         * list when there is no player/world. Never null. See {@code ChunkPlacementGauge}.
         */
        List<String> chunkGauge();
    }

    private SlabbedDebugCommandTree() {
    }

    /**
     * {@code /slabdy} — the target-dy surface, named after the {@code [slabdy]} rows it prints.
     *
     * <ul>
     *   <li>bare {@code /slabdy} — toggle the target-dy overlay.</li>
     *   <li>{@code /slabdy row} — print the current crosshair target's full diagnostic dump.
     *       Needs nothing but shipped code, so it works in a release jar.</li>
     *   <li>{@code /slabdy chunk} — print the standing chunk's attachment-capacity gauge (the GH #36
     *       railing). Needs nothing but shipped code, so it works in a release jar.</li>
     *   <li>{@code /slabdy build} — print the jar identity stamp.</li>
     * </ul>
     */
    public static <S> LiteralArgumentBuilder<S> slabdy(Function<CommandContext<S>, Session> sessions) {
        return LiteralArgumentBuilder.<S>literal("slabdy")
                .executes(ctx -> overlay(sessions.apply(ctx), null))
                .then(LiteralArgumentBuilder.<S>literal("row")
                        .executes(ctx -> row(sessions.apply(ctx))))
                .then(LiteralArgumentBuilder.<S>literal("chunk")
                        .executes(ctx -> chunk(sessions.apply(ctx))))
                .then(LiteralArgumentBuilder.<S>literal("on")
                        .executes(ctx -> overlay(sessions.apply(ctx), Boolean.TRUE)))
                .then(LiteralArgumentBuilder.<S>literal("off")
                        .executes(ctx -> overlay(sessions.apply(ctx), Boolean.FALSE)))
                .then(LiteralArgumentBuilder.<S>literal("build")
                        .executes(ctx -> build(sessions.apply(ctx))));
    }

    /**
     * {@code /slabdev} — kept EXACTLY as this line's live-confirmed spelling
     * ({@code /slabdev debug on}, {@code /slabdev record on}). Those two subcommands were previously
     * registered only by the development-only diagnostics companion, which is why the command did
     * not exist at all on a shipped jar. The tree moved here; the implementations stayed where they
     * are and are reached through a bridge, so a release build answers instead of going silent.
     */
    public static <S> LiteralArgumentBuilder<S> slabdev(Function<CommandContext<S>, Session> sessions) {
        return LiteralArgumentBuilder.<S>literal("slabdev")
                .executes(ctx -> build(sessions.apply(ctx)))
                .then(LiteralArgumentBuilder.<S>literal("debug")
                        .executes(ctx -> overlay(sessions.apply(ctx), null))
                        .then(LiteralArgumentBuilder.<S>literal("on")
                                .executes(ctx -> overlay(sessions.apply(ctx), Boolean.TRUE)))
                        .then(LiteralArgumentBuilder.<S>literal("off")
                                .executes(ctx -> overlay(sessions.apply(ctx), Boolean.FALSE)))
                        .then(LiteralArgumentBuilder.<S>literal("toggle")
                                .executes(ctx -> overlay(sessions.apply(ctx), null))))
                .then(LiteralArgumentBuilder.<S>literal("record")
                        .executes(ctx -> recorder(sessions.apply(ctx), null))
                        .then(LiteralArgumentBuilder.<S>literal("on")
                                .executes(ctx -> recorder(sessions.apply(ctx), Boolean.TRUE)))
                        .then(LiteralArgumentBuilder.<S>literal("off")
                                .executes(ctx -> recorder(sessions.apply(ctx), Boolean.FALSE)))
                        .then(LiteralArgumentBuilder.<S>literal("toggle")
                                .executes(ctx -> recorder(sessions.apply(ctx), null))))
                .then(LiteralArgumentBuilder.<S>literal("row")
                        .executes(ctx -> row(sessions.apply(ctx))))
                .then(LiteralArgumentBuilder.<S>literal("build")
                        .executes(ctx -> build(sessions.apply(ctx))));
    }

    /** @param target on/off, or null to flip whatever it currently is. */
    private static int overlay(Session session, Boolean target) {
        if (!session.overlayAvailable()) {
            session.feedback("[slabdy] target-dy overlay is " + NOT_AVAILABLE);
            return 0;
        }
        boolean next = target != null ? target : !session.overlayEnabled();
        session.setOverlayEnabled(next);
        session.feedback("[slabdy] target-dy overlay: " + (next ? "on" : "off"));
        return 1;
    }

    /** @param target on/off, or null to flip whatever it currently is. */
    private static int recorder(Session session, Boolean target) {
        if (!session.recorderAvailable()) {
            session.feedback("[slabdev] live cursor recorder is " + NOT_AVAILABLE);
            return 0;
        }
        boolean next = target != null ? target : !session.recorderEnabled();
        session.setRecorderEnabled(next);
        session.feedback("[slabdev] live cursor recorder: " + (next ? "on" : "off")
                + " (" + session.recorderStatus() + ")");
        session.feedback("[slabdev] " + session.buildStamp());
        return 1;
    }

    private static int chunk(Session session) {
        List<String> lines = session.chunkGauge();
        if (lines.isEmpty()) {
            session.feedback("[slabdy] chunk: no world");
            return 0;
        }
        for (String line : lines) {
            session.feedback(line);
        }
        return 1;
    }

    private static int row(Session session) {
        List<String> lines = session.targetRow();
        if (lines.isEmpty()) {
            session.feedback("[slabdy] target: none");
            return 0;
        }
        for (String line : lines) {
            session.feedback(line);
        }
        return 1;
    }

    private static int build(Session session) {
        session.feedback("[slabdev] " + session.buildStamp());
        return 1;
    }
}
