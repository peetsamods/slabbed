package com.slabbed.test;

import com.slabbed.dev.SlabbedLabFixtures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.util.math.BlockPos;

/**
 * Client GameTest: deterministic screenshot proof for the Slabbed Lab 3-lane fixture.
 *
 * What this test proves (no more, no less):
 *   All three fixture lanes (FULL, BOTTOM_SLAB, TOP_SLAB) rendered in the client
 *   world from a fixed south-facing, slightly-overhead viewpoint with no visual
 *   assertions — screenshot is a manual regression reference only.
 *
 * Does NOT validate visual correctness, triad logic, dy offsets, or render state.
 */
public final class SlabbedLabClientGameTest implements FabricClientGameTest {

    /**
     * Fixed high-altitude fixture origin — guaranteed all-air in any world type
     * (superflat, default, or void).
     * Fixture footprint: FULL=(0,200,0), BOTTOM_SLAB=(2,200,0), TOP_SLAB=(4,200,0).
     */
    private static final BlockPos FIXTURE_ORIGIN = new BlockPos(0, 200, 0);

    /**
     * Deterministic camera position for the screenshot.
     *
     * <p>Geometry:
     * <ul>
     *   <li>X=2.0  — centred over the fixture's X span (0..4)
     *   <li>Y=203.0 — 3 blocks above the support level (Y=200)
     *   <li>Z=8.0  — 8 blocks south (+Z) of the fixture origin (Z=0)
     *   <li>Yaw  180° — facing north (-Z), looking directly at the fixture
     *   <li>Pitch  25° — tilted downward, showing slab tops and front faces so
     *                    FULL / BOTTOM_SLAB / TOP_SLAB are visually distinct
     * </ul>
     */
    private static final double CAM_X     = 2.0;
    private static final double CAM_Y     = 203.0;
    private static final double CAM_Z     = 8.0;
    private static final float  CAM_YAW   = 180.0f;
    private static final float  CAM_PITCH = 25.0f;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {

            // Wait for initial chunk download and first full render pass.
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

            // Position the camera deterministically to frame all three lanes.
            // refreshPositionAndAngles is the canonical Yarn API for setting
            // position + orientation in one call; safe to call on the render thread.
            ctx.runOnClient(mc -> {
                if (mc.player != null) {
                    mc.player.refreshPositionAndAngles(CAM_X, CAM_Y, CAM_Z, CAM_YAW, CAM_PITCH);
                }
            });

            // Wait for the client to receive, render, and settle the newly placed
            // blocks from the repositioned camera location.
            singleplayer.getClientWorld().waitForChunksRender();

            // Capture one screenshot with a stable, descriptive name.
            // Output: build/run/clientGameTest/screenshots/0000_slabbed_lab_fixture_proof.png
            ctx.takeScreenshot("slabbed_lab_fixture_proof");
        }
    }
}
