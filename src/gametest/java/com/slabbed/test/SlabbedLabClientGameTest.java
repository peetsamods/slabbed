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
 * Client GameTest: deterministic proofs for the Slabbed Lab 3-lane fixture.
 *
 * What this test proves:
 *   1. Raycast shape offset (client): {@code SlabSupportStateMixin.slabbed$offsetRaycast}
 *      applies exactly one -0.5 offset to {@code ComposterBlock} (non-empty raycast
 *      shape) above the BOTTOM_SLAB lane: raycast minY == -0.5, not 0.0 and not -1.0.
 *   2. Carpet outline offset (client): {@code CarpetDyShapeMixin} (sole client carpet
 *      outline path after dedupe) applies exactly one -0.5 offset to {@code WHITE_CARPET}
 *      above the BOTTOM_SLAB lane: outline minY == -0.5, not 0.0 and not -1.0.
 *   3. Screenshot of all three fixture lanes from a fixed south-facing, slightly-overhead
 *      viewpoint — manual regression reference only (no pixel assertions).
 *
 * Does NOT validate model rendering or actual cursor-hit results.
 *
 * <p>Test block selection:
 * <ul>
 *   <li>COMPOSTER — {@code ComposterBlock.getRaycastShape} returns {@code VoxelShapes.fullCube()}
 *       (non-empty, minY=0.0 unoffset). Most solid blocks (e.g. stone) return
 *       {@code VoxelShapes.empty()} for {@code getRaycastShape}, making a bounding-box
 *       assertion unsafe. Composter is the same choice used by {@code outlineRaycastParity}
 *       server test.
 *   <li>WHITE_CARPET — thin block above bottom slab; outline shape offset by
 *       {@code CarpetDyShapeMixin} via {@code ClientDy.dyFor}.
 * </ul>
 *
 * <p>Both proofs use the BOTTOM_SLAB lane at {@code FIXTURE_ORIGIN + (2,0,0)}, one block
 * above: composter first, then replaced with carpet. Sync pattern: {@code ctx.waitTick()}
 * flushes NOTIFY_LISTENERS block-update packets (processed during client game ticks, not
 * during {@code waitForChunksRender}), then {@code waitForChunksRender} settles rebuilds.
 */
public final class SlabbedLabClientGameTest implements FabricClientGameTest {

    /**
     * Fixed high-altitude fixture origin — guaranteed all-air in any world type
     * (superflat, default, or void).
     * Fixture footprint: FULL=(0,200,0), BOTTOM_SLAB=(2,200,0), TOP_SLAB=(4,200,0).
     */
    private static final BlockPos FIXTURE_ORIGIN = new BlockPos(0, 200, 0);

    /**
     * Deterministic camera position for the screenshot and block-state proofs.
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
            // update packets arrive.
            ctx.runOnClient(mc -> {
                if (mc.player != null) {
                    mc.player.refreshPositionAndAngles(CAM_X, CAM_Y, CAM_Z, CAM_YAW, CAM_PITCH);
                }
            });
            singleplayer.getClientWorld().waitForChunksRender();

            // ── Probe position: one block above the BOTTOM_SLAB lane support ────────
            // BOTTOM_SLAB support = FIXTURE_ORIGIN + (2,0,0); probe = (2,201,0).
            final BlockPos probePos = FIXTURE_ORIGIN.add(2, 1, 0);

            // ── Step 1: place fixture + COMPOSTER for raycast proof ──────────────────
            // ComposterBlock.getRaycastShape returns VoxelShapes.fullCube() (non-empty,
            // minY=0.0 unoffset). SlabSupportStateMixin.slabbed$offsetRaycast (both-env)
            // shifts it to minY=-0.5. Most other solid blocks return VoxelShapes.empty()
            // for getRaycastShape, making a getBoundingBox() assertion unsafe.
            singleplayer.getServer().runOnServer(server -> {
                SlabbedLabFixtures.PlaceResult result =
                        SlabbedLabFixtures.placeBasicFixture(server.getOverworld(), FIXTURE_ORIGIN);
                if (!result.ok()) {
                    throw new RuntimeException("placeBasicFixture failed: " + result.error());
                }
                server.getOverworld().setBlockState(
                        probePos, Blocks.COMPOSTER.getDefaultState(), Block.NOTIFY_LISTENERS);
            });

            // Flush block-update packets (processed during client game ticks) then
            // settle chunk rebuilds.
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            // ── Raycast proof (client-side) ──────────────────────────────────────────
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world is null during raycast check");
                }
                BlockState state = mc.world.getBlockState(probePos);
                if (!state.isOf(Blocks.COMPOSTER)) {
                    throw new RuntimeException(
                            "client: composter not present at " + probePos.toShortString()
                            + ", found: " + state.getBlock().getTranslationKey());
                }
                VoxelShape raycast = state.getRaycastShape(mc.world, probePos);
                double minY = raycast.getBoundingBox().minY;
                if (minY != -0.5) {
                    String diagnosis = minY == 0.0
                            ? " (offset missing — slabbed$offsetRaycast not firing)"
                            : minY == -1.0
                            ? " (double-offset — duplicate raycast path active)"
                            : "";
                    throw new RuntimeException(
                            "composter raycast minY expected -0.5, got " + minY + diagnosis);
                }
            });

            // ── Step 2: replace COMPOSTER with WHITE_CARPET for outline proof ────────
            singleplayer.getServer().runOnServer(server ->
                    server.getOverworld().setBlockState(
                            probePos, Blocks.WHITE_CARPET.getDefaultState(), Block.NOTIFY_LISTENERS));

            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            // ── Carpet outline proof (client-side) ───────────────────────────────────
            // CarpetDyShapeMixin (client-only) is the sole active carpet outline path
            // after dedupe. Assert it applies exactly one -0.5 offset.
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world is null during carpet outline check");
                }
                BlockState carpetState = mc.world.getBlockState(probePos);
                if (!carpetState.isOf(Blocks.WHITE_CARPET)) {
                    throw new RuntimeException(
                            "client: carpet not present at " + probePos.toShortString()
                            + ", found: " + carpetState.getBlock().getTranslationKey());
                }
                VoxelShape outline = carpetState.getOutlineShape(mc.world, probePos, ShapeContext.absent());
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
