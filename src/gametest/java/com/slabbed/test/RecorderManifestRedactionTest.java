package com.slabbed.test;

import com.slabbed.dev.audit.LiveCursorIntentRecorder;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;

/**
 * Security finding, RECORDER_REVIEW.md H1 (2026-07-03, never actually fixed until now):
 * {@code manifest.json} recorded {@code sun.java.command} — the full launcher command line —
 * completely unredacted. For a real Microsoft-authenticated client this includes
 * {@code --accessToken} (a live JWT), {@code --uuid}, and {@code --xuid} as plain launch
 * arguments. {@code manifest.json} sits in the same {@code live-cursor-recorder/} directory as
 * {@code session.jsonl}, which is routinely shared for analysis (as every recorder session this
 * project's live-test loop has used has been) — so those values must never reach disk.
 *
 * <p>Re-discovered while investigating an unrelated live-test report; the original finding had
 * been documented but never actually acted on. Fixed by redacting the value following any
 * sensitive launch flag before the manifest is written.
 */
public final class RecorderManifestRedactionTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void redactsAccessTokenUuidAndXuid(TestContext ctx) {
        String command = "net.minecraft.client.main.Main --username Maintainer --version 1.21.11 "
                + "--accessToken eyJhbGciOiJSUzI1NiJ9.super-secret-jwt-token "
                + "--uuid 11112222333344445555666677778888 "
                + "--xuid 9999888877776666 "
                + "--clientId abcdef-0123-4567-89ab-cdef01234567 "
                + "--userType msa";
        String redacted = LiveCursorIntentRecorder.redactJavaCommand(command);

        ctx.assertTrue(!redacted.contains("super-secret-jwt-token"),
                "accessToken value must be redacted, got: " + redacted);
        ctx.assertTrue(!redacted.contains("11112222333344445555666677778888"),
                "uuid value must be redacted, got: " + redacted);
        ctx.assertTrue(!redacted.contains("9999888877776666"),
                "xuid value must be redacted, got: " + redacted);
        ctx.assertTrue(!redacted.contains("abcdef-0123-4567-89ab-cdef01234567"),
                "clientId value must be redacted, got: " + redacted);
        ctx.assertTrue(redacted.contains("--accessToken [REDACTED]"), "accessToken flag itself should remain, value replaced");
        ctx.assertTrue(redacted.contains("--username Maintainer"), "non-sensitive args must be preserved");
        ctx.assertTrue(redacted.contains("--userType msa"), "non-sensitive args must be preserved");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void handlesNullAndEmptySafely(TestContext ctx) {
        ctx.assertTrue(LiveCursorIntentRecorder.redactJavaCommand(null) == null, "null in, null out");
        ctx.assertTrue("".equals(LiveCursorIntentRecorder.redactJavaCommand("")), "empty in, empty out");
        ctx.complete();
    }
}
