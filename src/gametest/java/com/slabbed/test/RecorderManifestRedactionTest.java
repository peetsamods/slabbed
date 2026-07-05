package com.slabbed.test;

import com.slabbed.util.LiveCursorIntentRecorder;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Security finding, ported from the 1.21.11 canonical branch (RECORDER_REVIEW.md H1): {@code
 * manifest.json} recorded {@code sun.java.command} — the full launcher command line — completely
 * unredacted. For a real Microsoft-authenticated client this includes {@code --accessToken} (a live
 * JWT), {@code --uuid}, and {@code --xuid} as plain launch arguments. {@code manifest.json} sits in
 * the same {@code live-cursor-recorder/} directory as {@code session.jsonl}, which is routinely
 * shared for analysis, so those values must never reach disk. Fixed by redacting the value following
 * any sensitive launch flag before the manifest is written.
 *
 * <p>On 26.2 this fix becomes reachable for the first time: Phase 6 Part 1 correctly declined to port
 * it onto the then-inert recorder stub (the manifest was never written), so the fix lands together
 * with the recorder revival. Adapted to this branch's headless {@link GameTestHelper} idiom (not the
 * Yarn {@code TestContext} shape the source branch used).
 */
public final class RecorderManifestRedactionTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void redactsAllFiveSensitiveLaunchArgs(GameTestHelper helper) {
        String command = "net.minecraft.client.main.Main --username the maintainer --version 26.2 "
                + "--accessToken eyJhbGciOiJSUzI1NiJ9.super-secret-jwt-token "
                + "--uuid 11112222333344445555666677778888 "
                + "--xuid 9999888877776666 "
                + "--clientId abcdef-0123-4567-89ab-cdef01234567 "
                + "--session live-session-token-value "
                + "--userType msa";
        String redacted = LiveCursorIntentRecorder.redactJavaCommand(command);

        // All 5 secret VALUES must be gone.
        assertGone(helper, redacted, "super-secret-jwt-token", "accessToken");
        assertGone(helper, redacted, "11112222333344445555666677778888", "uuid");
        assertGone(helper, redacted, "9999888877776666", "xuid");
        assertGone(helper, redacted, "abcdef-0123-4567-89ab-cdef01234567", "clientId");
        assertGone(helper, redacted, "live-session-token-value", "session");

        // The flag NAMES themselves must remain (value replaced with [REDACTED]).
        assertPresent(helper, redacted, "--accessToken [REDACTED]");
        assertPresent(helper, redacted, "--uuid [REDACTED]");
        assertPresent(helper, redacted, "--xuid [REDACTED]");
        assertPresent(helper, redacted, "--clientId [REDACTED]");
        assertPresent(helper, redacted, "--session [REDACTED]");

        // Non-sensitive args must be preserved verbatim.
        assertPresent(helper, redacted, "--username the maintainer");
        assertPresent(helper, redacted, "--version 26.2");
        assertPresent(helper, redacted, "--userType msa");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void handlesNullAndEmptySafely(GameTestHelper helper) {
        if (LiveCursorIntentRecorder.redactJavaCommand(null) != null) {
            throw helper.assertionException("null in must produce null out");
        }
        if (!"".equals(LiveCursorIntentRecorder.redactJavaCommand(""))) {
            throw helper.assertionException("empty in must produce empty out");
        }
        helper.succeed();
    }

    private static void assertGone(GameTestHelper helper, String redacted, String secret, String flag) {
        if (redacted.contains(secret)) {
            throw helper.assertionException(
                    "the " + flag + " value must be redacted, got: " + redacted);
        }
    }

    private static void assertPresent(GameTestHelper helper, String redacted, String needle) {
        if (!redacted.contains(needle)) {
            throw helper.assertionException(
                    "expected '" + needle + "' to remain, got: " + redacted);
        }
    }
}
