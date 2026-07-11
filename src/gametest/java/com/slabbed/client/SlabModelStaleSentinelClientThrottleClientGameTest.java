package com.slabbed.client;

import com.slabbed.util.SlabModelStaleSentinel;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Pure mutation pins for the sentinel chat throttle; no world or renderer is needed. */
public final class SlabModelStaleSentinelClientThrottleClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        String red = SlabModelStaleSentinel.KIND_DIVERGENT;
        expect(true, SlabModelStaleSentinelClient.shouldSendChatAlert(red, 1_000L, Long.MIN_VALUE),
                "first real red must alert without Long.MIN_VALUE overflow");
        expect(false, SlabModelStaleSentinelClient.shouldSendChatAlert(red, 1_039L, 1_000L),
                "a red inside the 39-tick window must stay throttled");
        expect(true, SlabModelStaleSentinelClient.shouldSendChatAlert(red, 1_040L, 1_000L),
                "a red at the 40-tick boundary must alert");
        expect(true, SlabModelStaleSentinelClient.shouldSendChatAlert(red, 10L, 1_000L),
                "a world-clock reset must reopen the alert gate");
        expect(false, SlabModelStaleSentinelClient.shouldSendChatAlert(
                        "ENSEMBLE_OCCLUDED_OCCUPANCY", 2_000L, Long.MIN_VALUE),
                "informational occupancy must never chat");
        expect(false, SlabModelStaleSentinelClient.shouldSendChatAlert(
                        SlabModelStaleSentinel.KIND_NO_BAKE_YELLOW, 2_000L, Long.MIN_VALUE),
                "NO_BAKE yellow must never chat");
        expect(false, SlabModelStaleSentinelClient.shouldSendChatAlert(
                        "UNKNOWN_DIAGNOSTIC", 2_000L, Long.MIN_VALUE),
                "unknown diagnostics must never chat");
    }

    private static void expect(boolean expected, boolean actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
