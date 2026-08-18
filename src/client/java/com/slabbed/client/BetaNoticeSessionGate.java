package com.slabbed.client;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Pure show/suppress decision for the beta notice, kept separate from
 * {@link BetaNoticeClient} so it's testable without a client/world instance.
 *
 * <p>Tracks "shown this session" PER WORLD KEY, not as a single global flag — a single flag
 * was the bug (live-reported): once ANY world showed the notice, a single boolean
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

    /**
     * True if {@code version} carries an alpha or beta pre-release qualifier — the ONLY versions
     * that may show a notice whose text is literally "Slabbed is in beta".
     *
     * <p>Maintainer ruling (2026-08-07): {@code mod_version=0.5.1} shipped with no beta qualifier and
     * the notice still fired on every world join. Deleting the notice was explicitly rejected — a
     * future {@code 0.6.0-alpha.1} must still show it — so the decision is derived from the version
     * string instead of a hand-maintained boolean, and therefore cannot drift again in either
     * direction: adding a qualifier turns it back on, removing one turns it off, with no second
     * place to remember to edit.
     *
     * <p>Read as semver: everything after the first {@code -} is the pre-release qualifier, and
     * {@code +build} metadata is stripped first so a build tag can never fake a qualifier. Only
     * {@code alpha} and {@code beta} count. {@code rc} deliberately does not — a release candidate
     * is not "in beta", and the notice says what it says. Change the notice text before widening
     * this.
     */
    static boolean isAlphaOrBetaVersion(String version) {
        return preReleaseWord(version) != null;
    }

    /**
     * The word this build should call its own pre-release stage in the notice — {@code "alpha"} or
     * {@code "beta"}, or {@code null} when the version carries neither.
     *
     * <p>Derived from the same qualifier the gate above reads, for the same stated reason: there
     * must be no second copy to forget to update. The gate already followed the version while the
     * notice text was still the literal word "beta", so {@code 0.5.1-alpha.1} greeted players by
     * calling itself a beta — the exact drift the version-derived design existed to prevent, just
     * one field further along (maintainer ruling, 2026-08-18). Adding a qualifier now decides both
     * whether the notice appears AND what it says.
     */
    static String preReleaseWord(String version) {
        if (version == null) {
            return null;
        }
        int build = version.indexOf('+');
        String withoutBuildMetadata = build >= 0 ? version.substring(0, build) : version;
        int dash = withoutBuildMetadata.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String qualifier = withoutBuildMetadata.substring(dash + 1).toLowerCase(Locale.ROOT);
        if (qualifier.contains("alpha")) {
            return "alpha";
        }
        return qualifier.contains("beta") ? "beta" : null;
    }

    /** Test-only: clears session state so gametests don't leak state into each other. */
    static synchronized void resetForTest() {
        shownKeysThisSession.clear();
        shownForUnknownContextThisSession = false;
    }
}
