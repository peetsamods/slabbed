package com.slabbed.client;

import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/**
 * In-package (com.slabbed.client) to reach {@link BetaNoticeSessionGate}'s package-private API.
 * Pure in-memory logic, no client/world dependency.
 */
public final class BetaNoticeSessionGateTest {

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void freshKeyShouldShow(TestContext ctx) {
        BetaNoticeSessionGate.resetForTest();
        String key = "gametest:fresh:" + System.nanoTime();
        ctx.assertTrue(BetaNoticeSessionGate.shouldShow(key),
                "a key never shown/dismissed this session must be allowed to show");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void sameKeyDoesNotShowTwiceInOneSession(TestContext ctx) {
        BetaNoticeSessionGate.resetForTest();
        String key = "gametest:repeat:" + System.nanoTime();
        ctx.assertTrue(BetaNoticeSessionGate.shouldShow(key), "must be allowed to show the first time");
        BetaNoticeSessionGate.markShown(key);
        ctx.assertTrue(!BetaNoticeSessionGate.shouldShow(key),
                "the SAME key must not show again after being marked shown this session");
        ctx.complete();
    }

    // THE BUG (live-reported): a global "shown this session" flag blocked every OTHER world too,
    // including a brand-new one that was never shown or dismissed. A per-key session gate must
    // not have this cross-contamination.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void markingOneKeyShownDoesNotSuppressADifferentKey(TestContext ctx) {
        BetaNoticeSessionGate.resetForTest();
        String keyA = "gametest:worldA:" + System.nanoTime();
        String keyB = "gametest:worldB:" + System.nanoTime();

        BetaNoticeSessionGate.markShown(keyA);
        ctx.assertTrue(!BetaNoticeSessionGate.shouldShow(keyA), "sanity: keyA now suppressed for this session");
        ctx.assertTrue(BetaNoticeSessionGate.shouldShow(keyB),
                "THE FIX: a brand-new world/key that was never shown must still show, even though "
                        + "a DIFFERENT world already showed the notice this session — a single "
                        + "global flag was the reported bug");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void dismissedKeyNeverShowsEvenIfNeverShownThisSession(TestContext ctx) {
        BetaNoticeSessionGate.resetForTest();
        String key = "gametest:dismissed:" + System.nanoTime();
        BetaNoticeDismissedWorlds.dismiss(key);
        ctx.assertTrue(!BetaNoticeSessionGate.shouldShow(key),
                "a permanently-dismissed key must never show, regardless of session state");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void nullKeyBucketIsCappedSeparatelyFromRealKeys(TestContext ctx) {
        BetaNoticeSessionGate.resetForTest();
        String realKey = "gametest:real:" + System.nanoTime();
        ctx.assertTrue(BetaNoticeSessionGate.shouldShow(null), "an unidentifiable context must be allowed to show once");
        BetaNoticeSessionGate.markShown(null);
        ctx.assertTrue(!BetaNoticeSessionGate.shouldShow(null), "the null bucket caps at once per session too");
        ctx.assertTrue(BetaNoticeSessionGate.shouldShow(realKey),
                "marking the null/unknown bucket shown must not suppress a real, identified world key");
        ctx.complete();
    }
}
