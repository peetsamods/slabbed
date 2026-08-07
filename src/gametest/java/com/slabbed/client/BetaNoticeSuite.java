package com.slabbed.client;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;

/**
 * The beta-notice feature suite: the persisted per-world dismissal set
 * ({@link BetaNoticeDismissedWorlds}, real file I/O under the gametest run dir) and the
 * per-session show-once gate with its version predicate ({@link BetaNoticeSessionGate}).
 * In-package (com.slabbed.client) to reach both package-private APIs. Pure logic + file I/O,
 * no client/world dependency.
 *
 * <p>Merged 2026-08-07 from {@code BetaNoticeDismissedWorldsTest} + {@code BetaNoticeSessionGateTest}
 * (every test preserved verbatim; the two classes covered one feature).
 */
public final class BetaNoticeSuite {

    // ── persisted per-world dismissal set (BetaNoticeDismissedWorlds) ────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void freshKeyIsNotDismissed(TestContext ctx) {
        String key = "gametest:fresh:" + System.nanoTime();
        ctx.assertTrue(!BetaNoticeDismissedWorlds.isDismissed(key),
                "a key that was never dismissed must read as not-dismissed");
        ctx.assertTrue(!BetaNoticeDismissedWorlds.isDismissed(null),
                "a null key (unidentifiable context) must never read as dismissed");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dismissingOneKeyDoesNotAffectAnother(TestContext ctx) {
        String dismissedKey = "gametest:dismissed:" + System.nanoTime();
        String otherKey = "gametest:other:" + System.nanoTime();

        BetaNoticeDismissedWorlds.dismiss(dismissedKey);
        ctx.assertTrue(BetaNoticeDismissedWorlds.isDismissed(dismissedKey),
                "the dismissed key must now read as dismissed");
        ctx.assertTrue(!BetaNoticeDismissedWorlds.isDismissed(otherKey),
                "THE POINT of this feature: dismissing one world/server must NOT silence a "
                        + "different, never-dismissed one — got dismissed=true for an untouched key");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dismissingNullKeyIsANoOp(TestContext ctx) {
        // Must not throw, and must not somehow make every subsequent null check "dismissed".
        BetaNoticeDismissedWorlds.dismiss(null);
        ctx.assertTrue(!BetaNoticeDismissedWorlds.isDismissed(null),
                "dismissing a null key must not make null read as dismissed");
        ctx.complete();
    }

    // ── per-session show-once gate (BetaNoticeSessionGate) ───────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void freshKeyShouldShow(TestContext ctx) {
        BetaNoticeSessionGate.resetForTest();
        String key = "gametest:fresh:" + System.nanoTime();
        ctx.assertTrue(BetaNoticeSessionGate.shouldShow(key),
                "a key never shown/dismissed this session must be allowed to show");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
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
    @GameTest(structure = "fabric-gametest-api-v1:empty")
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

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dismissedKeyNeverShowsEvenIfNeverShownThisSession(TestContext ctx) {
        BetaNoticeSessionGate.resetForTest();
        String key = "gametest:dismissed:" + System.nanoTime();
        BetaNoticeDismissedWorlds.dismiss(key);
        ctx.assertTrue(!BetaNoticeSessionGate.shouldShow(key),
                "a permanently-dismissed key must never show, regardless of session state");
        ctx.complete();
    }

    // THE SECOND BUG (maintainer, 2026-08-07): mod_version reached 0.5.1 — no beta qualifier at
    // all — and "Slabbed is in beta" still fired on every world join. The fix is a predicate over
    // the version string rather than a hand-maintained switch, so it cannot drift again in EITHER
    // direction: a future 0.6.0-alpha.1 must still show the notice.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aVersionWithNoQualifierDoesNotShowTheBetaNotice(TestContext ctx) {
        ctx.assertTrue(!BetaNoticeSessionGate.isAlphaOrBetaVersion("0.5.1"),
                "0.5.1 is not a beta build and must not claim to be one — this is the reported bug");
        ctx.assertTrue(!BetaNoticeSessionGate.isAlphaOrBetaVersion("0.6.0"),
                "a plain release version must not show the notice");
        ctx.assertTrue(!BetaNoticeSessionGate.isAlphaOrBetaVersion("0.5.1+1.21.11"),
                "build metadata after '+' is not a pre-release qualifier");
        ctx.assertTrue(!BetaNoticeSessionGate.isAlphaOrBetaVersion("0.5.1+beta-build"),
                "a 'beta' inside BUILD metadata must not fake a qualifier — the qualifier is the "
                        + "semver pre-release field, everything after the first '-' and before '+'");
        ctx.assertTrue(!BetaNoticeSessionGate.isAlphaOrBetaVersion(null),
                "an unresolvable version must fail silent, not fail loud");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void alphaAndBetaQualifiersStillShowTheBetaNotice(TestContext ctx) {
        ctx.assertTrue(BetaNoticeSessionGate.isAlphaOrBetaVersion("0.5.0-beta.8"),
                "the version this line last shipped a notice on must still show it");
        ctx.assertTrue(BetaNoticeSessionGate.isAlphaOrBetaVersion("0.6.0-alpha.1"),
                "THE OTHER DIRECTION: the next planned alpha must still show the notice — "
                        + "deleting the notice outright was explicitly rejected");
        ctx.assertTrue(BetaNoticeSessionGate.isAlphaOrBetaVersion("1.0.0-BETA"),
                "the qualifier check is case-insensitive");
        ctx.complete();
    }

    // A release candidate is not "in beta", and the notice's text says exactly that. Pinned so
    // that widening the predicate has to be a deliberate edit to the notice text as well.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aReleaseCandidateDoesNotShowTheBetaNotice(TestContext ctx) {
        ctx.assertTrue(!BetaNoticeSessionGate.isAlphaOrBetaVersion("0.6.0-rc.1"),
                "'rc' is deliberately not a beta qualifier — change the notice text before "
                        + "widening this");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
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
