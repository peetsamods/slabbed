package com.slabbed.test.support;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Minecraft 26.2 copy of the shared {@code TestConnections}: Fabric's client-gametest API 5 exposes the
 * chunk waits on {@code getClientLevel()} rather than API 6's {@code getConnection()}. The 26.2 harness
 * compiles the shared suite with this file in place of the shared one; keep the two method sets equal.
 */
public final class TestConnections {
    private TestConnections() {
    }

    public static void waitForChunksDownload(TestSingleplayerContext singleplayer) {
        singleplayer.getClientLevel().waitForChunksDownload();
    }

    public static void waitForChunksRender(TestSingleplayerContext singleplayer) {
        singleplayer.getClientLevel().waitForChunksRender();
    }
}
