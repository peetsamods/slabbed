package com.slabbed.test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabbedDebugCommandTree;
import com.slabbed.util.SlabdyRowFormatter;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The standing debug-tooling rule says {@code /slabdy} and {@code /slabdev} ship in EVERY jar,
 * default off. Maintainer's 2026-08-07 ruling fixed the reading: the command must be INVOCABLE on a
 * shipped jar, not merely present as bytes. Before this pass neither was — {@code /slabdev}
 * registered only from the development-only diagnostics companion, and {@code /slabdy} had no
 * registration anywhere on this line.
 *
 * <p>This test pins the half of that claim a headless run can actually execute: the REAL
 * {@link SlabbedDebugCommandTree} builders, registered into a REAL {@link CommandDispatcher} and
 * driven with REAL command strings. It is deliberately not a mirror of the node list — it executes
 * each node and asserts the feedback, so deleting or renaming a subcommand fails here rather than
 * silently shrinking the shipped surface.
 *
 * <p>What it cannot execute, and why the tree was split out of the client class to make the rest
 * executable: the Fabric client-command dispatcher exists only in a client run, and
 * {@code runGameTest} is a dedicated server. The remaining link — the client entrypoint calls
 * {@code SlabbedDebugCommands.register()} unconditionally — is argued from the code, not run here.
 *
 * <p>The second assertion set is the honest-degradation contract: with no debug-tool provider
 * installed (the release case), the overlay and recorder subcommands report
 * "{@code not available in this build}" and return failure instead of throwing, while
 * {@code /slabdy row} and {@code build} still do real work — they need nothing but shipped code.
 */
public final class ShippedDebugCommandsTest {

    /** A Session that records feedback, with the debug tools ABSENT — i.e. the release shape. */
    private static class ReleaseSession implements SlabbedDebugCommandTree.Session {
        private final List<String> feedback = new ArrayList<>();
        private final List<String> row;

        ReleaseSession(List<String> row) {
            this.row = row;
        }

        @Override
        public void feedback(String line) {
            feedback.add(line);
        }

        @Override
        public List<String> targetRow() {
            return row;
        }

        @Override
        public boolean overlayAvailable() {
            return false;
        }

        @Override
        public boolean overlayEnabled() {
            return false;
        }

        @Override
        public void setOverlayEnabled(boolean enabled) {
            throw new AssertionError("release build must not reach the overlay implementation");
        }

        @Override
        public boolean recorderAvailable() {
            return false;
        }

        @Override
        public boolean recorderEnabled() {
            return false;
        }

        @Override
        public void setRecorderEnabled(boolean enabled) {
            throw new AssertionError("release build must not reach the recorder implementation");
        }

        @Override
        public String recorderStatus() {
            return "";
        }

        @Override
        public String buildStamp() {
            return "sha=deadbeef built=1970-01-01T00:00:00Z";
        }
    }

    /** A Session with the debug tools PRESENT — i.e. the dev / diagnostics-companion shape. */
    private static final class DevSession extends ReleaseSession {
        private boolean overlay;
        private boolean recorder;

        DevSession() {
            super(List.of());
        }

        @Override
        public boolean overlayAvailable() {
            return true;
        }

        @Override
        public boolean overlayEnabled() {
            return overlay;
        }

        @Override
        public void setOverlayEnabled(boolean enabled) {
            overlay = enabled;
        }

        @Override
        public boolean recorderAvailable() {
            return true;
        }

        @Override
        public boolean recorderEnabled() {
            return recorder;
        }

        @Override
        public void setRecorderEnabled(boolean enabled) {
            recorder = enabled;
        }

        @Override
        public String recorderStatus() {
            return "run/slabbed-live/session.log";
        }
    }

    private static CommandDispatcher<Object> dispatcherFor(SlabbedDebugCommandTree.Session session) {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(SlabbedDebugCommandTree.slabdy(ctx -> session));
        dispatcher.register(SlabbedDebugCommandTree.slabdev(ctx -> session));
        return dispatcher;
    }

    private static int run(CommandDispatcher<Object> dispatcher, String command,
                           GameTestHelper helper, BlockPos where) {
        try {
            return dispatcher.execute(command, new Object());
        } catch (CommandSyntaxException e) {
            throw helper.assertionException(helper.relativePos(where),
                    "\"/" + command + "\" did not parse against the shipped tree: " + e.getMessage());
        }
    }

    private static void mustContain(List<String> feedback, String needle,
                                    GameTestHelper helper, BlockPos where) {
        for (String line : feedback) {
            if (line.contains(needle)) {
                return;
            }
        }
        throw helper.assertionException(helper.relativePos(where),
                "expected feedback containing \"" + needle + "\", got:\n" + String.join("\n", feedback));
    }

    /**
     * Every node of both shipped trees parses and executes. This is the "invocable, not just
     * present" assertion, minus the client dispatcher the headless run has no access to.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void everyShippedSubcommandParsesAndExecutes(GameTestHelper helper) {
        BlockPos where = helper.absolutePos(new BlockPos(1, 1, 1));
        DevSession session = new DevSession();
        CommandDispatcher<Object> dispatcher = dispatcherFor(session);

        String[] commands = {
                "slabdy",
                "slabdy row",
                "slabdy on",
                "slabdy off",
                "slabdy build",
                "slabdev",
                "slabdev debug",
                "slabdev debug on",
                "slabdev debug off",
                "slabdev debug toggle",
                "slabdev record",
                "slabdev record on",
                "slabdev record off",
                "slabdev record toggle",
                "slabdev row",
                "slabdev build"
        };
        for (String command : commands) {
            run(dispatcher, command, helper, where);
        }

        // The toggles are real state changes, not decoration: drive them to a known value and read
        // it back through the same seam the shipped client wiring uses.
        run(dispatcher, "slabdev debug on", helper, where);
        if (!session.overlayEnabled()) {
            throw helper.assertionException(helper.relativePos(where),
                    "/slabdev debug on did not enable the overlay");
        }
        run(dispatcher, "slabdev debug off", helper, where);
        if (session.overlayEnabled()) {
            throw helper.assertionException(helper.relativePos(where),
                    "/slabdev debug off did not disable the overlay");
        }
        run(dispatcher, "slabdev record on", helper, where);
        if (!session.recorderEnabled()) {
            throw helper.assertionException(helper.relativePos(where),
                    "/slabdev record on did not enable the recorder");
        }
        run(dispatcher, "slabdev record off", helper, where);
        if (session.recorderEnabled()) {
            throw helper.assertionException(helper.relativePos(where),
                    "/slabdev record off did not disable the recorder");
        }

        // Bare /slabdy flips the overlay from whatever it is now (off).
        run(dispatcher, "slabdy", helper, where);
        if (!session.overlayEnabled()) {
            throw helper.assertionException(helper.relativePos(where),
                    "bare /slabdy did not toggle the overlay on");
        }

        helper.succeed();
    }

    /**
     * The release shape: no debug-tool provider installed. The tool-backed subcommands must say so
     * in plain words and must NOT touch the missing implementation (the ReleaseSession setters throw
     * if they are called), while {@code build} still answers.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void toolBackedSubcommandsDegradeHonestlyWithoutThrowing(GameTestHelper helper) {
        BlockPos where = helper.absolutePos(new BlockPos(1, 1, 1));
        ReleaseSession session = new ReleaseSession(List.of());
        CommandDispatcher<Object> dispatcher = dispatcherFor(session);

        for (String command : new String[]{"slabdy", "slabdy on", "slabdev debug on", "slabdev record on"}) {
            session.feedback.clear();
            int result = run(dispatcher, command, helper, where);
            if (result != 0) {
                throw helper.assertionException(helper.relativePos(where),
                        "\"/" + command + "\" must report failure when its implementation is absent");
            }
            mustContain(session.feedback, SlabbedDebugCommandTree.NOT_AVAILABLE, helper, where);
        }

        // build needs nothing but shipped code and must still work.
        session.feedback.clear();
        run(dispatcher, "slabdev build", helper, where);
        mustContain(session.feedback, "sha=deadbeef", helper, where);

        // No block target -> an honest "none", not an exception and not an empty response.
        session.feedback.clear();
        run(dispatcher, "slabdy row", helper, where);
        mustContain(session.feedback, "[slabdy] target: none", helper, where);

        helper.succeed();
    }

    /**
     * {@code /slabdy row} is the subcommand that does real work in a release jar: it runs the
     * shipped headless formatter over a real world. Asserted end-to-end through the command, on a
     * genuinely lowered cell, so an exclusion of the formatter from the release artifacts (it was
     * excluded until this pass) cannot come back without this going red.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabdyRowPrintsTheRealFormatterOutputForALoweredTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos below = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2));
        // Same scene shape as SlabdyRowFormatterFieldsTest: a carrier slab beneath, then an
        // anchored full block on top, which is this line's simplest reliable "own dy is -0.5" cell.
        level.setBlock(below, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        level.setBlock(target, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(level, target, level.getBlockState(target));

        BlockState state = level.getBlockState(target);
        double dy = com.slabbed.util.SlabSupport.getYOffset(level, target, state);
        if (Math.abs(dy + 0.5) > 1.0e-6) {
            throw helper.assertionException(helper.relativePos(target),
                    "setup: the anchored target must read -0.5 (got " + dy + ") for the row to say LOWERED");
        }
        Vec3 hit = new Vec3(target.getX() + 0.25, target.getY() + 0.5, target.getZ() + 0.75);
        List<String> row = SlabdyRowFormatter.formatRow(
                level, target, state, Direction.UP, hit, new ItemStack(Items.STONE));

        ReleaseSession session = new ReleaseSession(row);
        CommandDispatcher<Object> dispatcher = dispatcherFor(session);
        run(dispatcher, "slabdy row", helper, target);

        mustContain(session.feedback, "[slabdy] target=" + target.toShortString(), helper, target);
        mustContain(session.feedback, "dy=-0.500 LOWERED", helper, target);
        mustContain(session.feedback, "src=ANCHORED", helper, target);
        if (session.feedback.size() != row.size()) {
            throw helper.assertionException(helper.relativePos(target),
                    "/slabdy row must print every formatter line: expected " + row.size()
                            + ", printed " + session.feedback.size());
        }

        helper.succeed();
    }
}
