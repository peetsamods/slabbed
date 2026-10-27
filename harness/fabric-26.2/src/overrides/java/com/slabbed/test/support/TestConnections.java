package com.slabbed.test.support;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Keeps the 26.2 proof suite compatible with both supported client-gametest API accessors. */
public final class TestConnections {
    private TestConnections() {
    }

    public static void waitForChunksDownload(TestSingleplayerContext singleplayer) {
        waitFor(singleplayer, "waitForChunksDownload");
    }

    public static void waitForChunksRender(TestSingleplayerContext singleplayer) {
        waitFor(singleplayer, "waitForChunksRender");
    }

    private static void waitFor(TestSingleplayerContext singleplayer, String operation) {
        try {
            java.lang.reflect.Method accessor;
            try {
                accessor = TestSingleplayerContext.class.getMethod("getConnection");
            } catch (NoSuchMethodException olderApi) {
                accessor = TestSingleplayerContext.class.getMethod("getClientLevel");
            }
            Object connection = accessor.invoke(singleplayer);
            accessor.getReturnType().getMethod(operation).invoke(connection);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Fabric client-gametest chunk wait failed: " + operation, failure);
        }
    }
}
