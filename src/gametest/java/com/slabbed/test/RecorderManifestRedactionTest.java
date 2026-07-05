package com.slabbed.test;

import com.slabbed.dev.audit.LiveCursorIntentRecorder;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/**
 * Security finding, RECORDER_REVIEW.md H1 (ported from the 1.21.11 sibling, 2026-07-05):
 * {@code manifest.json} recorded {@code sun.java.command} — the full launcher command line —
 * completely unredacted. For a real Microsoft-authenticated client this includes
 * {@code --accessToken} (a live JWT), {@code --uuid}, and {@code --xuid} as plain launch
 * arguments. {@code manifest.json} sits in the same {@code live-cursor-recorder/} directory as
 * {@code session.jsonl}, which is routinely shared for analysis — so those values must never
 * reach disk.
 *
 * <p>Fixed by redacting the value following any sensitive launch flag before the manifest is
 * written ({@link LiveCursorIntentRecorder#redactJavaCommand(String)}). This test pins the
 * redaction contract: sensitive values are stripped, non-sensitive args are preserved, and
 * null/empty inputs pass through unchanged.
 */
public final class RecorderManifestRedactionTest {

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void redactsAccessTokenUuidAndXuid(TestContext ctx) {
        String command = "net.minecraft.client.main.Main --username Maintainer --version 1.21.1 "
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

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void handlesNullAndEmptySafely(TestContext ctx) {
        ctx.assertTrue(LiveCursorIntentRecorder.redactJavaCommand(null) == null, "null in, null out");
        ctx.assertTrue("".equals(LiveCursorIntentRecorder.redactJavaCommand("")), "empty in, empty out");
        ctx.complete();
    }
}
