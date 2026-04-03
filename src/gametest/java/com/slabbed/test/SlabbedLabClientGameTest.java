package com.slabbed.test;

import com.slabbed.dev.SlabbedLabFixtures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

/**
 * Client GameTest: deterministic screenshot proof + carpet outline offset proof
 * for the Slabbed Lab 3-lane fixture.
 *
 * What this test proves:
 *   1. All three fixture lanes (FULL, BOTTOM_SLAB, TOP_SLAB) rendered in the client
 *      world from a fixed south-facing, slightly-overhead viewpoint — screenshot is
 *      a manual regression reference only.
 *   2. {@code CarpetDyShapeMixin} (sole client carpet outline path after dedupe)
 *      applies exactly one -0.5 offset to {@code WHITE_CARPET} above the BOTTOM_SLAB
 *      lane: outline minY == -0.5, not 0.0 (missing) and not -1.0 (doubled).
 *
 * Does NOT validate model rendering, raycast shapes, or pale-moss expansion.
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

            // Position the camera near the fixture BEFORE placing blocks so the chunk
            // section at Y=200 is already in the client's render view when block
            // update packets arrive. If blocks are placed while the section is outside
            // the client's view distance, the update packet may be silently dropped.
            ctx.runOnClient(mc -> {
                if (mc.player != null) {
                    mc.player.refreshPositionAndAngles(CAM_X, CAM_Y, CAM_Z, CAM_YAW, CAM_PITCH);
                }
            });

            // Wait for the chunk section at Y=200 to be fully loaded and rendered
            // before placing blocks into it.
            singleplayer.getClientWorld().waitForChunksRender();

            // Place the basic fixture and white carpet above the BOTTOM_SLAB lane on
            // the server thread. runOnServer executes synchronously and propagates
            // RuntimeException back to the test thread.
            singleplayer.getServer().runOnServer(server -> {
                SlabbedLabFixtures.PlaceResult result =
                        SlabbedLabFixtures.placeBasicFixture(
                                server.getOverworld(), FIXTURE_ORIGIN);
                if (!result.ok()) {
                    throw new RuntimeException("placeBasicFixture failed: " + result.error());
                }
                // BOTTOM_SLAB lane support = FIXTURE_ORIGIN + (2,0,0); carpet goes one above.
                BlockPos carpetPos = FIXTURE_ORIGIN.add(2, 1, 0);
                server.getOverworld().setBlockState(
                        carpetPos, Blocks.WHITE_CARPET.getDefaultState(), Block.NOTIFY_LISTENERS);
            });

            // Block update packets from NOTIFY_LISTENERS are processed during client game
            // ticks, not during waitForChunksRender(). Run one tick so the client applies
            // the block changes to mc.world, then wait for chunk rebuilds to complete.
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            // --- Carpet outline proof (client-side) ---
            // CarpetDyShapeMixin (client-only) is the sole active carpet outline path
            // after dedupe. Assert it applies exactly one -0.5 offset on the client world.
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world is null during carpet outline check");
                }
                BlockPos carpetPos = FIXTURE_ORIGIN.add(2, 1, 0);
                BlockState carpetState = mc.world.getBlockState(carpetPos);
                if (!carpetState.isOf(Blocks.WHITE_CARPET)) {
                    throw new RuntimeException(
                            "client: carpet not present at " + carpetPos.toShortString()
                            + ", found: " + carpetState.getBlock().getTranslationKey());
                }
                VoxelShape outline = carpetState.getOutlineShape(mc.world, carpetPos, ShapeContext.absent());
                double minY = outline.getBoundingBox().minY;
                if (minY != -0.5) {
                    String diagnosis = minY == 0.0
                            ? " (offset missing — CarpetDyShapeMixin not firing)"
                            : minY == -1.0
                            ? " (double-offset — duplicate path still active)"
                            : "";
                    throw new RuntimeException(
                            "carpet outline minY expected -0.5, got " + minY + diagnosis);
                }
            });

            // Capture one screenshot with a stable, descriptive name.
            // Output: build/run/clientGameTest/screenshots/0000_slabbed_lab_fixture_proof.png
            ctx.takeScreenshot("slabbed_lab_fixture_proof");
        }
    }
}
