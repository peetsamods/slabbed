package com.slabbed.client;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Locale;

/**
 * In-package (com.slabbed.client) to reach {@link BetaNoticeClient}'s package-private
 * {@code preReleaseChannel} and {@code noticeMessage}.
 *
 * <p>THE BUG (live-confirmed on a 26.2 dev client, 2026-08-20): the join notice read "Slabbed is in
 * beta" as a hardcoded literal while {@code mod_version} was {@code 0.5.0-alpha.1+26.2}. The wording
 * had drifted from the build and nothing could catch it, because no test asserted the two agreed.
 *
 * <p>These rows pin the derivation, not the current version string — they must keep passing across
 * every future version bump without edits.
 */
public final class BetaNoticeVersionChannelTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void alphaVersionYieldsAlphaChannel(GameTestHelper helper) {
        String channel = BetaNoticeClient.preReleaseChannel("0.5.1-alpha.1+26.2");
        if (!"alpha".equals(channel)) {
            throw helper.assertionException("an alpha version must yield the alpha channel — got: " + channel);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void betaVersionYieldsBetaChannel(GameTestHelper helper) {
        String channel = BetaNoticeClient.preReleaseChannel("0.5.0-beta.8+26.2");
        if (!"beta".equals(channel)) {
            throw helper.assertionException("a beta version must yield the beta channel — got: " + channel);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stableVersionYieldsNoChannel(GameTestHelper helper) {
        // A stable build has no pre-release to warn about, so the notice is skipped entirely
        // rather than inventing a word for it.
        String channel = BetaNoticeClient.preReleaseChannel("0.5.1+26.2");
        if (channel != null) {
            throw helper.assertionException("a stable version must yield no channel — got: " + channel);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unresolvableVersionYieldsNoChannel(GameTestHelper helper) {
        if (BetaNoticeClient.preReleaseChannel(null) != null) {
            throw helper.assertionException("an unresolvable version must yield no channel, never a guess");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void noticeTextCarriesTheChannelItWasGiven(GameTestHelper helper) {
        // THE ANTI-DRIFT CHECK: the rendered text must contain the channel it was handed and must
        // not contain the other one. A hardcoded word would fail exactly one of these two rows.
        String alphaText = BetaNoticeClient.noticeMessage("alpha").getString().toLowerCase(Locale.ROOT);
        if (!alphaText.contains("in alpha")) {
            throw helper.assertionException("the alpha notice must say 'in alpha' — got: " + alphaText);
        }
        if (alphaText.contains("in beta")) {
            throw helper.assertionException(
                    "the alpha notice must NOT say 'in beta' — the channel word is hardcoded again: " + alphaText);
        }
        String betaText = BetaNoticeClient.noticeMessage("beta").getString().toLowerCase(Locale.ROOT);
        if (!betaText.contains("in beta")) {
            throw helper.assertionException("the beta notice must say 'in beta' — got: " + betaText);
        }
        helper.succeed();
    }
}
