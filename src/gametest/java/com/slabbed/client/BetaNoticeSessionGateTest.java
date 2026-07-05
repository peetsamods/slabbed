package com.slabbed.client;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * In-package (com.slabbed.client) to reach {@link BetaNoticeSessionGate}'s package-private API.
 * Pure in-memory logic, no client/world dependency.
 */
public final class BetaNoticeSessionGateTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void freshKeyShouldShow(GameTestHelper helper) {
        BetaNoticeSessionGate.resetForTest();
        String key = "gametest:fresh:" + System.nanoTime();
        if (!BetaNoticeSessionGate.shouldShow(key)) {
            throw helper.assertionException("a key never shown/dismissed this session must be allowed to show");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sameKeyDoesNotShowTwiceInOneSession(GameTestHelper helper) {
        BetaNoticeSessionGate.resetForTest();
        String key = "gametest:repeat:" + System.nanoTime();
        if (!BetaNoticeSessionGate.shouldShow(key)) {
            throw helper.assertionException("must be allowed to show the first time");
        }
        BetaNoticeSessionGate.markShown(key);
        if (BetaNoticeSessionGate.shouldShow(key)) {
            throw helper.assertionException(
                    "the SAME key must not show again after being marked shown this session");
        }
        helper.succeed();
    }

    // THE BUG (live-reported): a global "shown this session" flag blocked every OTHER world too,
    // including a brand-new one that was never shown or dismissed. A per-key session gate must
    // not have this cross-contamination.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void markingOneKeyShownDoesNotSuppressADifferentKey(GameTestHelper helper) {
        BetaNoticeSessionGate.resetForTest();
        String keyA = "gametest:worldA:" + System.nanoTime();
        String keyB = "gametest:worldB:" + System.nanoTime();

        BetaNoticeSessionGate.markShown(keyA);
        if (BetaNoticeSessionGate.shouldShow(keyA)) {
            throw helper.assertionException("sanity: keyA now suppressed for this session");
        }
        if (!BetaNoticeSessionGate.shouldShow(keyB)) {
            throw helper.assertionException(
                    "THE FIX: a brand-new world/key that was never shown must still show, even though "
                            + "a DIFFERENT world already showed the notice this session — a single "
                            + "global flag was the reported bug");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dismissedKeyNeverShowsEvenIfNeverShownThisSession(GameTestHelper helper) {
        BetaNoticeSessionGate.resetForTest();
        String key = "gametest:dismissed:" + System.nanoTime();
        BetaNoticeDismissedWorlds.dismiss(key);
        if (BetaNoticeSessionGate.shouldShow(key)) {
            throw helper.assertionException(
                    "a permanently-dismissed key must never show, regardless of session state");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void nullKeyBucketIsCappedSeparatelyFromRealKeys(GameTestHelper helper) {
        BetaNoticeSessionGate.resetForTest();
        String realKey = "gametest:real:" + System.nanoTime();
        if (!BetaNoticeSessionGate.shouldShow(null)) {
            throw helper.assertionException("an unidentifiable context must be allowed to show once");
        }
        BetaNoticeSessionGate.markShown(null);
        if (BetaNoticeSessionGate.shouldShow(null)) {
            throw helper.assertionException("the null bucket caps at once per session too");
        }
        if (!BetaNoticeSessionGate.shouldShow(realKey)) {
            throw helper.assertionException(
                    "marking the null/unknown bucket shown must not suppress a real, identified world key");
        }
        helper.succeed();
    }
}
