package com.slabbed.test.support;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The client-gametest API's chunk waits, behind one name per intent.
 *
 * <p>Fabric's client-gametest API 6 (26.3) exposes them on {@code getConnection()}; API 5 (26.2) on
 * {@code getClientLevel()}. The 26.2 proof harness compiles the suite against 26.2 with its own copy
 * of this class in place of this one, so every client test calls these two methods and never the
 * context accessor directly.
 */
public final class TestConnections {
    private TestConnections() {
    }

    public static void waitForChunksDownload(TestSingleplayerContext singleplayer) {
        singleplayer.getConnection().waitForChunksDownload();
    }

    public static void waitForChunksRender(TestSingleplayerContext singleplayer) {
        singleplayer.getConnection().waitForChunksRender();
    }
}
