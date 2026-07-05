package com.slabbed.client;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Locale;

/**
 * In-package (com.slabbed.client) to reach {@link BetaNoticeClient}'s package-private
 * {@code dismissFeedbackMessage} and {@link BetaNoticeDismissedWorlds}'s dismiss-result contract.
 *
 * <p>THE BUG (found by cross-phase sweeper reviewing {@code eb66f399}): the dismiss-link handler
 * sent the "won't show again" success message unconditionally, even when the world key was
 * unresolvable (e.g. an unidentifiable Realms context) and {@link BetaNoticeDismissedWorlds#dismiss}
 * silently did nothing — so the player was told the preference was saved when it was not, and the
 * notice would reappear next session, contradicting the message they just read.
 */
public final class BetaNoticeDismissFeedbackTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dismissingAnUnresolvableKeyDoesNotPersist(GameTestHelper helper) {
        boolean persisted = BetaNoticeDismissedWorlds.dismiss(null);
        if (persisted) {
            throw helper.assertionException(
                    "dismiss(null) must report that nothing was persisted (it's a documented no-op)");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dismissingARealKeyReportsPersisted(GameTestHelper helper) {
        String key = "gametest:feedback-real:" + System.nanoTime();
        boolean persisted = BetaNoticeDismissedWorlds.dismiss(key);
        if (!persisted) {
            throw helper.assertionException(
                    "dismiss(realKey) must report success — a real, identifiable key is always persisted");
        }
        if (!BetaNoticeDismissedWorlds.isDismissed(key)) {
            throw helper.assertionException("a key reported as persisted must actually read back as dismissed");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void feedbackMessageForUnpersistedDismissDoesNotClaimSuccess(GameTestHelper helper) {
        String text = BetaNoticeClient.dismissFeedbackMessage(false).getString().toLowerCase(Locale.ROOT);
        // THE HONESTY CHECK: when nothing was persisted, the message must not say the notice
        // "won't show again" — that would be a false success claim the player has no way to detect.
        if (text.contains("won't show") || text.contains("wont show")) {
            throw helper.assertionException(
                    "the unpersisted-dismiss feedback message must NOT claim the notice won't show "
                            + "again — got: " + text);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void feedbackMessageForPersistedDismissStillConfirmsSuccess(GameTestHelper helper) {
        String text = BetaNoticeClient.dismissFeedbackMessage(true).getString().toLowerCase(Locale.ROOT);
        // The happy path must be unchanged: a real, persisted dismissal still confirms success.
        if (!text.contains("won't show") && !text.contains("wont show")) {
            throw helper.assertionException(
                    "the persisted-dismiss feedback message must still confirm success — got: " + text);
        }
        helper.succeed();
    }
}
