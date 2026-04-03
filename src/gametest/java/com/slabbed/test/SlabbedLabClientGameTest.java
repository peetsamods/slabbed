package com.slabbed.test;

import com.slabbed.dev.SlabbedLabFixtures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.util.math.BlockPos;

/**
 * Minimal client GameTest for Slabbed Lab.
 *
 * Proves the path works end-to-end:
 *   1. singleplayer test world starts
 *   2. canonical SlabbedLabFixtures.placeBasicFixture runs on the server thread
 *   3. client chunks render with the placed fixture
 *   4. screenshot saves to disk
 *
 * Does NOT validate visual correctness, triad logic, dy offsets, or render state.
 * That is deferred to future slices once this pipe is confirmed stable.
 */
public final class SlabbedLabClientGameTest implements FabricClientGameTest {

    /**
     * Fixed high-altitude fixture origin — guaranteed all-air in any world type
     * (superflat, default, or void). Fixture footprint: X=0..4, Y=0..1, Z=0..1.
     */
    private static final BlockPos FIXTURE_ORIGIN = new BlockPos(0, 200, 0);

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {

            // Wait for initial chunk download and first full render pass
            singleplayer.getClientWorld().waitForChunksRender();

            // Place the basic fixture on the server thread via canonical helper.
            // runOnServer executes synchronously on the MC server thread and
            // propagates RuntimeException back to the test thread.
            singleplayer.getServer().runOnServer(server -> {
                SlabbedLabFixtures.PlaceResult result =
                        SlabbedLabFixtures.placeBasicFixture(
                                server.getOverworld(), FIXTURE_ORIGIN);
                if (!result.ok()) {
                    throw new RuntimeException("placeBasicFixture failed: " + result.error());
                }
            });

            // Wait for the client to receive and render the newly placed blocks
            singleplayer.getClientWorld().waitForChunksRender();

            // Save one screenshot to prove the pipe works.
            // No comparison or threshold yet — this slice only proves the path.
            ctx.takeScreenshot("slabbed_lab_fixture");
        }
    }
}
