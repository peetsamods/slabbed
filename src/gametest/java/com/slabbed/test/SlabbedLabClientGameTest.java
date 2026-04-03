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
 * <p>What this test proves:
 * <ol>
 *   <li><b>Raycast shape offset (client):</b> {@code SlabSupportStateMixin.slabbed$offsetRaycast}
 *       applies exactly one -0.5 offset to {@code ComposterBlock} (non-empty raycast shape)
 *       above the BOTTOM_SLAB lane: raycast minY == -0.5, not 0.0 and not -1.0.</li>
 *   <li><b>Carpet outline offset (client):</b> {@code CarpetDyShapeMixin} (sole client carpet
 *       outline path after dedupe) applies exactly one -0.5 offset to {@code WHITE_CARPET}
 *       above the BOTTOM_SLAB lane: outline minY == -0.5, not 0.0 and not -1.0.</li>
 *   <li><b>Overview screenshot</b> ({@code 0000_slabbed_lab_fixture_proof.png}): all three
 *       fixture lanes with carpet on the BOTTOM_SLAB lane, from a fixed south-facing
 *       slightly-overhead viewpoint. Manual regression reference; no pixel assertions.</li>
 *   <li><b>Model-height proof screenshot</b> ({@code 0001_slabbed_model_height_proof.png}):
 *       OAK_LOG placed one block above both the FULL lane (no dy offset) and the BOTTOM_SLAB
 *       lane (dy = -0.5 via {@code OffsetBlockStateModel}), captured from a south-facing
 *       eye-level side view. The left log (FULL, x=0) bottom sits flush at Y=201.0; the
 *       right log (BOTTOM_SLAB, x=2) bottom sits at the slab top Y=200.5. The 0.5-block
 *       vertical offset between the two logs confirms the model render offset is applied
 *       exactly once on the BOTTOM_SLAB lane.</li>
 * </ol>
 *
 * <p>Does NOT validate actual cursor-hit results or add pixel-diff comparisons.
 *
 * <p>Test block selection:
 * <ul>
 *   <li>COMPOSTER — {@code ComposterBlock.getRaycastShape} returns {@code VoxelShapes.fullCube()}
 *       (non-empty, minY=0.0 unoffset). Most solid blocks return {@code VoxelShapes.empty()},
 *       making {@code getBoundingBox()} unsafe. Same choice as {@code outlineRaycastParity}
 *       server test.</li>
 *   <li>WHITE_CARPET — thin block above bottom slab; outline offset by {@code CarpetDyShapeMixin}
 *       via {@code ClientDy.dyFor}.</li>
 *   <li>OAK_LOG — full-cube block with a distinctive wood texture; not suppressed by
 *       {@code OffsetBlockStateModel}'s fence/wall/pane guard. Visually distinguishable
 *       from the STONE and STONE_SLAB support blocks below it.</li>
 * </ul>
 *
 * <p>Sync pattern: {@code ctx.waitTick()} flushes NOTIFY_LISTENERS block-update packets
 * (processed during client game ticks, not during {@code waitForChunksRender}), then
 * {@code waitForChunksRender} settles chunk rebuilds before assertions or screenshots.
 */
public final class SlabbedLabClientGameTest implements FabricClientGameTest {

    /**
     * Fixed high-altitude fixture origin — guaranteed all-air in any world type
     * (superflat, default, or void).
     * Fixture footprint: FULL=(0,200,0), BOTTOM_SLAB=(2,200,0), TOP_SLAB=(4,200,0).
     */
    private static final BlockPos FIXTURE_ORIGIN = new BlockPos(0, 200, 0);

    // ── Overview camera (fixture proof + carpet proof screenshot) ─────────────
    // Looking north from the south, slightly overhead — all three lanes visible.
    private static final double CAM_X     = 2.0;
    private static final double CAM_Y     = 203.0;
    private static final double CAM_Z     = 8.0;
    private static final float  CAM_YAW   = 180.0f;
    private static final float  CAM_PITCH = 25.0f;

    // ── Model-height camera (side-facing, eye-level) ──────────────────────────
    // X=1: centred between FULL lane (x=0, left) and BOTTOM_SLAB lane (x=2, right).
    // Z=5: 5 blocks south of the fixture (at Z=0), looking north (yaw=180°).
    // Y=201.5: slightly above probe-block height so both side faces and top faces
    //          are visible in the same frame.
    // Pitch=20°: enough downward tilt for depth cues without hiding the side view.
    private static final double MODEL_CAM_X     = 1.0;
    private static final double MODEL_CAM_Y     = 201.5;
    private static final double MODEL_CAM_Z     = 5.0;
    private static final float  MODEL_CAM_YAW   = 180.0f;
    private static final float  MODEL_CAM_PITCH = 20.0f;

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

            // Overview screenshot: all three lanes with carpet on BOTTOM_SLAB, from
            // the fixed overhead-south viewpoint.
            // Output: build/run/clientGameTest/screenshots/0000_slabbed_lab_fixture_proof.png
            ctx.takeScreenshot("slabbed_lab_fixture_proof");

            // ── Step 3: model-height proof ────────────────────────────────────────────
            // Place OAK_LOG one block above both the FULL lane (no dy offset: getYOffset
            // returns 0 above full stone) and the BOTTOM_SLAB lane (dy = -0.5 via
            // OffsetBlockStateModel.emitQuads → SlabSupport.getYOffset).
            //
            // OAK_LOG chosen: full-cube block with a distinctive wood texture not
            // confused with the STONE and STONE_SLAB supports below; not suppressed
            // by OffsetBlockStateModel's fence/wall/pane guard.
            singleplayer.getServer().runOnServer(server -> {
                // FULL lane probe at (0,201,0): above STONE — no offset, bottom at Y=201.0.
                server.getOverworld().setBlockState(
                        FIXTURE_ORIGIN.add(0, 1, 0),
                        Blocks.OAK_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);
                // BOTTOM_SLAB lane probe at (2,201,0): replaces carpet.
                // Above STONE_SLAB (bottom) — offset -0.5, bottom at Y=200.5 (slab top).
                server.getOverworld().setBlockState(
                        probePos,
                        Blocks.OAK_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);
            });

            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            // Reposition to a side-facing, eye-level camera for model-height comparison.
            // Facing north (yaw=180°) from Z=5, X=1 centres between the two probe blocks.
            // From this angle:
            //   Left (x=0, FULL lane): OAK_LOG bottom flush with STONE top at Y=201.0.
            //   Right (x=2, BOTTOM_SLAB): OAK_LOG bottom at slab top Y=200.5 (offset -0.5).
            // The 0.5-block vertical offset between the two logs confirms OffsetBlockStateModel
            // applied the correct dy to the BOTTOM_SLAB lane and not to the FULL lane.
            ctx.runOnClient(mc -> {
                if (mc.player != null) {
                    mc.player.refreshPositionAndAngles(
                            MODEL_CAM_X, MODEL_CAM_Y, MODEL_CAM_Z, MODEL_CAM_YAW, MODEL_CAM_PITCH);
                }
            });
            singleplayer.getClientWorld().waitForChunksRender();

            // Model-height proof screenshot: side-by-side OAK_LOG blocks on FULL and
            // BOTTOM_SLAB lanes from eye level. A human reviewer can directly compare
            // the two log bases to confirm the slab offset is applied exactly once.
            // Output: build/run/clientGameTest/screenshots/0001_slabbed_model_height_proof.png
            ctx.takeScreenshot("slabbed_model_height_proof");
        }
    }
}
