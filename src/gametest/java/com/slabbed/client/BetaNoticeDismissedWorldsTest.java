package com.slabbed.client;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * In-package (com.slabbed.client) so this can reach {@link BetaNoticeDismissedWorlds}'
 * package-private API directly. Pure file I/O, no client/world dependency — exercises the
 * real config file under the gametest run directory.
 */
public final class BetaNoticeDismissedWorldsTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void freshKeyIsNotDismissed(GameTestHelper helper) {
        String key = "gametest:fresh:" + System.nanoTime();
        if (BetaNoticeDismissedWorlds.isDismissed(key)) {
            throw helper.assertionException("a key that was never dismissed must read as not-dismissed");
        }
        if (BetaNoticeDismissedWorlds.isDismissed(null)) {
            throw helper.assertionException("a null key (unidentifiable context) must never read as dismissed");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dismissingOneKeyDoesNotAffectAnother(GameTestHelper helper) {
        String dismissedKey = "gametest:dismissed:" + System.nanoTime();
        String otherKey = "gametest:other:" + System.nanoTime();

        BetaNoticeDismissedWorlds.dismiss(dismissedKey);
        if (!BetaNoticeDismissedWorlds.isDismissed(dismissedKey)) {
            throw helper.assertionException("the dismissed key must now read as dismissed");
        }
        if (BetaNoticeDismissedWorlds.isDismissed(otherKey)) {
            throw helper.assertionException(
                    "THE POINT of this feature: dismissing one world/server must NOT silence a "
                            + "different, never-dismissed one — got dismissed=true for an untouched key");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dismissingNullKeyIsANoOp(GameTestHelper helper) {
        // Must not throw, and must not somehow make every subsequent null check "dismissed".
        BetaNoticeDismissedWorlds.dismiss(null);
        if (BetaNoticeDismissedWorlds.isDismissed(null)) {
            throw helper.assertionException("dismissing a null key must not make null read as dismissed");
        }
        helper.succeed();
    }
}
