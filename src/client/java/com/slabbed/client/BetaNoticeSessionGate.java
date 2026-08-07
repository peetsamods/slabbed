package com.slabbed.client;

import java.util.HashSet;
import java.util.Set;

/**
 * Pure show/suppress decision for the beta notice, kept separate from
 * {@link BetaNoticeClient} so it's testable without a client/world instance.
 *
 * <p>Tracks "shown this session" PER WORLD KEY, not as a single global flag — a single flag
 * was the bug (live-reported by the maintainer): once ANY world showed the notice, a single boolean
 * blocked every other world for the rest of the session, including a brand-new world that was
 * never dismissed. The whole point of per-world dismissal is defeated if a session-wide cap
 * suppresses it anyway.
 */
final class BetaNoticeSessionGate {

    private static final Set<String> shownKeysThisSession = new HashSet<>();
    private static boolean shownForUnknownContextThisSession = false;

    private BetaNoticeSessionGate() {
    }

    /**
     * True if the notice should show for {@code worldKey} right now: not permanently dismissed
     * for this key, and not already shown for this key earlier in the current session. A
     * {@code null} key (an unidentifiable context, e.g. Realms) is tracked separately from every
     * real key, capped at once per session for that bucket rather than per-world.
     */
    static synchronized boolean shouldShow(String worldKey) {
        if (BetaNoticeDismissedWorlds.isDismissed(worldKey)) {
            return false;
        }
        if (worldKey == null) {
            return !shownForUnknownContextThisSession;
        }
        return !shownKeysThisSession.contains(worldKey);
    }

    /** Records that the notice was just shown for {@code worldKey} in this session. */
    static synchronized void markShown(String worldKey) {
        if (worldKey == null) {
            shownForUnknownContextThisSession = true;
        } else {
            shownKeysThisSession.add(worldKey);
        }
    }

    /** Test-only: clears session state so gametests don't leak state into each other. */
    static synchronized void resetForTest() {
        shownKeysThisSession.clear();
        shownForUnknownContextThisSession = false;
    }
}
