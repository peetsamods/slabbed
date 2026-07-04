package com.slabbed.client;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;

/**
 * In-package (com.slabbed.client) so this can reach {@link BetaNoticeDismissedWorlds}'
 * package-private API directly. Pure file I/O, no client/world dependency — exercises the
 * real config file under the gametest run directory.
 */
public final class BetaNoticeDismissedWorldsTest {

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
}
