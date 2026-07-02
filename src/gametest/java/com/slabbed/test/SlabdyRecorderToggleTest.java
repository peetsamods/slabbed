package com.slabbed.test;

import com.slabbed.dev.audit.LiveCursorIntentRecorder;
import com.slabbed.util.SlabbedAuditBridge;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Headless proof for the {@code /slabdy record} toggle mechanics: this is exactly
 * what let the recorder down before this fix — {@link LiveCursorIntentRecorder}'s
 * enabled flag was {@code private static final}, resolved once from a JVM property at
 * class-init, so no in-game command could ever turn it on. This pins that a runtime
 * toggle now genuinely flips both {@link LiveCursorIntentRecorder#isEnabled()} AND
 * {@link SlabbedAuditBridge#isRecorderEnabled()} in lockstep (the bridge used to cache
 * its own separate, permanently-stale copy of the same JVM property — toggling the
 * recorder without also fixing the bridge would have left every mixin call site
 * silently gated shut regardless of the toggle).
 */
public final class SlabdyRecorderToggleTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void toggleFlipsBothTheRecorderAndTheBridgeTogether(TestContext ctx) {
        boolean initial = LiveCursorIntentRecorder.isEnabled();
        ctx.assertTrue(SlabbedAuditBridge.isRecorderEnabled() == initial,
                "the bridge must agree with the recorder's initial state, not a stale cached copy");

        boolean afterFirstToggle = LiveCursorIntentRecorder.toggle();
        ctx.assertTrue(afterFirstToggle != initial, "toggle() must flip the enabled state");
        ctx.assertTrue(LiveCursorIntentRecorder.isEnabled() == afterFirstToggle,
                "isEnabled() must reflect the state toggle() just returned");
        ctx.assertTrue(SlabbedAuditBridge.isRecorderEnabled() == afterFirstToggle,
                "the bridge must see the SAME live state the recorder itself reports — "
                        + "this is the exact bug the bridge fix closes");

        boolean afterSecondToggle = LiveCursorIntentRecorder.toggle();
        ctx.assertTrue(afterSecondToggle == initial, "toggling twice must return to the original state");
        ctx.assertTrue(SlabbedAuditBridge.isRecorderEnabled() == initial,
                "the bridge must track the toggle back to the original state too");

        // Leave the recorder in its original state so this test has no lasting
        // side effect on any test that runs after it in the same suite.
        ctx.complete();
    }

    /**
     * Regression pin for the runEnded-reset fix. {@code recordShutdown()} sets a
     * permanent {@code runEnded} flag and early-returns once it's set — a SECOND
     * toggle-off in the same JVM run would silently skip its flush unless
     * {@code bootstrap()} resets that flag on re-enable. This does NOT show up in
     * the enabled/disabled boolean itself (that flips correctly either way) — it
     * only shows up in whether the second "run_end" record actually gets written to
     * disk, so this test reads the real session.jsonl file rather than just
     * checking isEnabled().
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void secondToggleOffActuallyFlushesASecondRunEndRecord(TestContext ctx) throws IOException {
        boolean initial = LiveCursorIntentRecorder.isEnabled();
        if (!initial) {
            LiveCursorIntentRecorder.toggle(); // ensure ON so the first toggle below is an "off"
        }
        Path sessionPath = Path.of(LiveCursorIntentRecorder.currentLogPathDisplay()).resolve("session.jsonl");

        LiveCursorIntentRecorder.toggle(); // off #1 -> should flush run_end #1
        long runEndsAfterFirstOff = countRunEndRecords(sessionPath);
        ctx.assertTrue(runEndsAfterFirstOff >= 1, "the first toggle-off must flush at least one run_end record");

        LiveCursorIntentRecorder.toggle(); // on again
        LiveCursorIntentRecorder.toggle(); // off #2 -> should flush a SECOND run_end
        long runEndsAfterSecondOff = countRunEndRecords(sessionPath);
        ctx.assertTrue(runEndsAfterSecondOff > runEndsAfterFirstOff,
                "a second toggle-off in the same run must flush its OWN run_end record, not silently "
                        + "no-op because a stale runEnded flag was never reset — before the fix this stayed "
                        + "stuck at " + runEndsAfterFirstOff);

        // Restore original state.
        if (LiveCursorIntentRecorder.isEnabled() != initial) {
            LiveCursorIntentRecorder.toggle();
        }
        ctx.complete();
    }

    private static long countRunEndRecords(Path sessionPath) throws IOException {
        if (!Files.exists(sessionPath)) {
            return 0;
        }
        try (var lines = Files.lines(sessionPath)) {
            return lines.filter(line -> line.contains("\"stage\":\"run_end\"")).count();
        }
    }
}
