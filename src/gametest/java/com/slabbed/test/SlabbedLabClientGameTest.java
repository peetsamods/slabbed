package com.slabbed.test;

import com.slabbed.dev.SlabbedLabFixtures;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BedBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.BedPart;
import net.minecraft.util.Hand;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * Canonical proof ladder for the lowered-side-slab client harness.
     *
     * <p>These ids must continue to emit both screenshots and notes, and must remain
     * represented in the written run manifest.
     */
    private static final List<String> LOWERED_SIDE_SLAB_EXPECTED_PROOF_IDS = List.of(
            "fb_on_bs_lower_half_owner_targeting",
            "fb_on_bs_lower_half_side_slab_intent",
            "fb_on_bs_repeat_click_no_ghost_face",
            "torch_on_fb_on_bs_rescue_targeting",
            "bed_on_bs_rescue_targeting",
            "full_block_on_full_block_baseline",
            "slab_on_normal_vanilla_face_baseline",
            "chain_on_fb_on_bs_no_rescue_targeting",
            "crafting_table_on_bs_no_rescue_targeting");

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
            Path screenshotDir = resolveClientGameTestScreenshotDir();
            Set<String> knownScreenshotFiles = listScreenshotFileNames(screenshotDir);
            List<ManifestArtifact> artifacts = new ArrayList<>();

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
            captureScreenshotAndRecord(
                    ctx,
                    "slabbed_lab_fixture_proof",
                    screenshotDir,
                    knownScreenshotFiles,
                    artifacts);

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
            captureScreenshotAndRecord(
                    ctx,
                    "slabbed_model_height_proof",
                    screenshotDir,
                    knownScreenshotFiles,
                    artifacts);

            // ── Step 4: chest crosshair retarget proof (client) ──────────────────────
            // Replace the OAK_LOG on the BOTTOM_SLAB lane with a CHEST. Proves the
            // retarget path after the outline-shape correction:
            //   (a) SlabSupport.isLoweredBlockEntityVisual returns true for CHEST
            //       above BOTTOM_SLAB — the single ownership helper.
            //   (b) The chest's OUTLINE shape (via ShapeContext.of(player)) — the
            //       same shape vanilla crosshair targeting uses — intersects a ray
            //       aimed at the visually lowered lower half AND its hit is at a
            //       distance ≤ the slab-below hit. That inequality is exactly what
            //       GameRendererCrosshairRetargetMixin tests before replacing
            //       client.crosshairTarget, so a live crosshair on this region
            //       resolves to the chest at probePos, not the slab at probePos.down().
            //   (c) The resolved BlockHitResult.getBlockPos() equals probePos,
            //       proving ownership semantics end-to-end.
            singleplayer.getServer().runOnServer(server ->
                    server.getOverworld().setBlockState(
                            probePos, Blocks.CHEST.getDefaultState(), Block.NOTIFY_LISTENERS));

            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world is null during chest retarget check");
                }
                if (mc.player == null) {
                    throw new RuntimeException("client player is null during chest retarget check");
                }
                BlockState chestState = mc.world.getBlockState(probePos);
                if (!chestState.isOf(Blocks.CHEST)) {
                    throw new RuntimeException(
                            "client: chest not present at " + probePos.toShortString()
                            + ", found: " + chestState.getBlock().getTranslationKey());
                }

                // (a) ownership predicate: isLoweredBlockEntityVisual == true
                if (!SlabSupport.isLoweredBlockEntityVisual(mc.world, probePos, chestState)) {
                    throw new RuntimeException(
                            "SlabSupport.isLoweredBlockEntityVisual=false for CHEST above BOTTOM_SLAB"
                            + " at " + probePos.toShortString()
                            + "; retarget predicate broken");
                }

                // (b) + (c): outline-shape ray hit at chest's visually lowered lower half.
                // Use a horizontal ray at Y = probePos.y - 0.3 (world ≈ 200.7), which
                // lies inside the chest's outline shape offset to [200.5, 201.375] and
                // strictly above the slab's native top face at Y=200.5.
                Vec3d eye = new Vec3d(
                        probePos.getX() + 0.5,
                        probePos.getY() - 0.3,
                        probePos.getZ() + 1.5);
                Vec3d end = new Vec3d(
                        probePos.getX() + 0.5,
                        probePos.getY() - 0.3,
                        probePos.getZ() - 1.0);

                // Mirror the production mixin: outline shape, ShapeContext of the camera entity.
                VoxelShape chestOutline =
                        chestState.getOutlineShape(mc.world, probePos, ShapeContext.of(mc.player));
                if (chestOutline.isEmpty()) {
                    throw new RuntimeException(
                            "CHEST outline shape is empty at " + probePos.toShortString()
                            + " — slabbed$offsetOutline or vanilla chest outline unexpectedly empty");
                }
                BlockHitResult chestHit = chestOutline.raycast(eye, end, probePos);
                if (chestHit == null) {
                    throw new RuntimeException(
                            "chest outline shape did not intersect lower-half ray at Y="
                            + (probePos.getY() - 0.3)
                            + " — retarget will silently miss");
                }
                if (!chestHit.getBlockPos().equals(probePos)) {
                    throw new RuntimeException(
                            "chest outline raycast hit wrong BlockPos: expected "
                            + probePos.toShortString()
                            + ", got " + chestHit.getBlockPos().toShortString());
                }

                // Compare distance against slab below. If slab isn't hit at this ray Y,
                // the retarget trivially dominates (chest strictly owns).
                BlockPos slabPos = probePos.down();
                BlockState slabState = mc.world.getBlockState(slabPos);
                VoxelShape slabOutline =
                        slabState.getOutlineShape(mc.world, slabPos, ShapeContext.of(mc.player));
                if (!slabOutline.isEmpty()) {
                    BlockHitResult slabHit = slabOutline.raycast(eye, end, slabPos);
                    if (slabHit != null) {
                        double dChest = chestHit.getPos().squaredDistanceTo(eye);
                        double dSlab = slabHit.getPos().squaredDistanceTo(eye);
                        if (dChest > dSlab + 1.0e-6) {
                            throw new RuntimeException(
                                    "chest retarget hit must be ≤ slab hit; dChest=" + dChest
                                    + ", dSlab=" + dSlab
                                    + " — GameRendererCrosshairRetargetMixin will not fire");
                        }
                    }
                }
            });

            // Lower-half aim screenshot: human-reviewable reference for the chest
            // retarget surface. Camera is placed across the fixture at the chest
            // lower-half eye height; yaw aims at the chest.
            ctx.runOnClient(mc -> {
                if (mc.player != null) {
                    mc.player.refreshPositionAndAngles(
                            probePos.getX() - 1.5,
                            probePos.getY() - 0.92, // feet s.t. eye ≈ probe.y - 0.3
                            probePos.getZ() + 4.0,
                            90.0f, // yaw east: chest is east of camera
                            0.0f);
                }
            });
            singleplayer.getClientWorld().waitForChunksRender();
            captureScreenshotAndRecord(
                    ctx,
                    "slabbed_chest_lower_half_proof",
                    screenshotDir,
                    knownScreenshotFiles,
                    artifacts);

            // ── Step 4b: stacked-chest retarget proof (client) ──────────────────────
            // Layout: lower chest at probePos (above BOTTOM_SLAB) and a second chest
            // stacked directly above at probePos.up(). Because SlabSupport.shouldOffset
            // uses hasSlabInColumn (a downward walk through non-air/non-slab blocks),
            // BOTH chests qualify as isLoweredBlockEntityVisual: the lower chest
            // directly, the upper chest via the chain through the lower chest.
            //
            // What we prove: a probe ray aimed at the visually lowered LOWER half
            // of the LOWER chest resolves to the lower chest BlockPos under the
            // exact path vanilla crosshair uses + the GameRendererCrosshairRetargetMixin
            // TAIL. Not the slab below. Not the upper chest.
            //
            // The simulation here mirrors the production code verbatim:
            //   1) vanilla `world.raycast` with ShapeType.OUTLINE — same shape
            //      selection vanilla crosshair targeting uses.
            //   2) mixin replay: if vanillaHit is a BLOCK and the block above is
            //      isLoweredBlockEntityVisual, re-test that above-state's outline
            //      shape with ShapeContext.of(player) on an extended ray; replace
            //      only when the lowered shape's hit is at ≤ the original distance.
            final BlockPos upperChestPos = probePos.up();
            singleplayer.getServer().runOnServer(server -> {
                server.getOverworld().setBlockState(
                        probePos, Blocks.CHEST.getDefaultState(), Block.NOTIFY_LISTENERS);
                server.getOverworld().setBlockState(
                        upperChestPos, Blocks.CHEST.getDefaultState(), Block.NOTIFY_LISTENERS);
            });

            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world is null during stacked chest check");
                }
                if (mc.player == null) {
                    throw new RuntimeException("client player is null during stacked chest check");
                }
                BlockState lowerState = mc.world.getBlockState(probePos);
                BlockState upperState = mc.world.getBlockState(upperChestPos);
                if (!lowerState.isOf(Blocks.CHEST)) {
                    throw new RuntimeException(
                            "lower chest not present at " + probePos.toShortString()
                            + ", found: " + lowerState.getBlock().getTranslationKey());
                }
                if (!upperState.isOf(Blocks.CHEST)) {
                    throw new RuntimeException(
                            "upper chest not present at " + upperChestPos.toShortString()
                            + ", found: " + upperState.getBlock().getTranslationKey());
                }

                // Sanity: per shouldOffset's hasSlabInColumn walk, BOTH chests are
                // treated as lowered block-entity visuals. If either flips to false,
                // the cascade-lowering behaviour has changed upstream and this
                // proof's assumptions must be re-checked.
                if (!SlabSupport.isLoweredBlockEntityVisual(mc.world, probePos, lowerState)) {
                    throw new RuntimeException("lower chest should be isLoweredBlockEntityVisual");
                }
                if (!SlabSupport.isLoweredBlockEntityVisual(mc.world, upperChestPos, upperState)) {
                    throw new RuntimeException(
                            "upper chest should be isLoweredBlockEntityVisual (cascade via hasSlabInColumn)");
                }

                // Realistic side-aim at the lower chest's visually lowered lower
                // half. Eye is just above slab-top height across the fixture;
                // target is the mid-height of the chest's lower-half overflow
                // region (world Y = probePos.y - 0.3 ≈ 200.7 for probePos.y=201).
                // This ray direction reaches the slab voxel before any chest voxel
                // along DDA so the retarget path is exercised end-to-end.
                Vec3d eye = new Vec3d(
                        probePos.getX() + 0.5,
                        probePos.getY() + 1.12,
                        probePos.getZ() + 3.0);
                Vec3d target = new Vec3d(
                        probePos.getX() + 0.5,
                        probePos.getY() - 0.3,
                        probePos.getZ() + 0.5);
                Vec3d dir = target.subtract(eye).normalize();
                Vec3d end = eye.add(dir.multiply(6.0));

                // (1) Vanilla DDA raycast — ShapeType.OUTLINE mirrors the crosshair.
                BlockHitResult vanillaHit = mc.world.raycast(new RaycastContext(
                        eye, end,
                        RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE,
                        mc.player));

                // (2) Mixin retarget replay. One-level-up, outline shape,
                // ShapeContext.of(player), ≤ distance predicate.
                BlockHitResult finalHit = vanillaHit;
                if (vanillaHit.getType() == HitResult.Type.BLOCK) {
                    BlockPos hitPos = vanillaHit.getBlockPos();
                    BlockPos abovePos = hitPos.up();
                    BlockState aboveState = mc.world.getBlockState(abovePos);
                    if (SlabSupport.isLoweredBlockEntityVisual(mc.world, abovePos, aboveState)) {
                        double origDist = vanillaHit.getPos().subtract(eye).length();
                        if (origDist > 0.0) {
                            Vec3d extEnd = eye.add(dir.multiply(origDist + 0.5));
                            VoxelShape aboveOutline = aboveState.getOutlineShape(
                                    mc.world, abovePos, ShapeContext.of(mc.player));
                            if (!aboveOutline.isEmpty()) {
                                BlockHitResult retargetHit =
                                        aboveOutline.raycast(eye, extEnd, abovePos);
                                if (retargetHit != null) {
                                    double dRetarget = retargetHit.getPos().squaredDistanceTo(eye);
                                    double dOrig = vanillaHit.getPos().squaredDistanceTo(eye);
                                    if (dRetarget <= dOrig + 1.0e-6) {
                                        finalHit = retargetHit;
                                    }
                                }
                            }
                        }
                    }
                }

                // (3) Ownership assertion: the final resolved hit must be the
                // LOWER chest, not the slab below and not the upper chest.
                if (finalHit.getType() != HitResult.Type.BLOCK) {
                    throw new RuntimeException(
                            "stacked chest lower-half aim resolved to " + finalHit.getType()
                            + "; expected BLOCK at " + probePos.toShortString()
                            + ". vanillaHit=" + vanillaHit.getType()
                            + (vanillaHit.getType() == HitResult.Type.BLOCK
                                    ? " at " + vanillaHit.getBlockPos().toShortString() : ""));
                }
                BlockPos resolvedPos = finalHit.getBlockPos();
                if (!resolvedPos.equals(probePos)) {
                    BlockPos slabPos = probePos.down();
                    String diagnosis;
                    if (resolvedPos.equals(slabPos)) {
                        diagnosis = " (retarget failed to fire — lower chest not promoted over slab)";
                    } else if (resolvedPos.equals(upperChestPos)) {
                        diagnosis = " (retarget incorrectly chose upper chest — single-level scan promoted past the intended lower-half owner)";
                    } else {
                        diagnosis = " (unexpected third-party pos)";
                    }
                    BlockState resolvedState = mc.world.getBlockState(resolvedPos);
                    throw new RuntimeException(
                            "stacked chest lower-half aim resolved to "
                            + resolvedPos.toShortString()
                            + " (" + resolvedState.getBlock().getTranslationKey() + ")"
                            + "; expected lower chest at " + probePos.toShortString()
                            + ". vanillaHit="
                            + (vanillaHit.getType() == HitResult.Type.BLOCK
                                    ? vanillaHit.getBlockPos().toShortString() : "MISS")
                            + diagnosis);
                }
            });

            // ── Step 5: solid cube above slab column must NOT lower (fix f9be295) ───
            // Replace the chest with STONE. With the fix landed, the generic
            // hasSlabInColumn fallback in SlabSupport.shouldOffset is gated by
            // !state.isSolidBlock, so full solid cubes return dy=0.0 and their
            // outline stays at minY=0.0.
            singleplayer.getServer().runOnServer(server ->
                    server.getOverworld().setBlockState(
                            probePos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS));

            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world is null during stone no-lower check");
                }
                BlockState stoneState = mc.world.getBlockState(probePos);
                if (!stoneState.isOf(Blocks.STONE)) {
                    throw new RuntimeException(
                            "client: stone not present at " + probePos.toShortString()
                            + ", found: " + stoneState.getBlock().getTranslationKey());
                }

                double dy = SlabSupport.getYOffset(mc.world, probePos, stoneState);
                if (dy != 0.0) {
                    throw new RuntimeException(
                            "STONE above BOTTOM_SLAB must not lower; got dy=" + dy
                            + " (fix f9be295 regression — isSolidBlock guard bypassed)");
                }

                VoxelShape outline = stoneState.getOutlineShape(mc.world, probePos, ShapeContext.absent());
                if (outline.isEmpty()) {
                    throw new RuntimeException("STONE outline unexpectedly empty");
                }
                double minY = outline.getBoundingBox().minY;
                if (minY != 0.0) {
                    throw new RuntimeException(
                            "STONE outline minY expected 0.0, got " + minY
                            + " — SlabSupportStateMixin.slabbed$offsetOutline still lowering solid cubes");
                }
            });

            // ── Step 6: full-cube BlockEntityProvider slab-sit proof (client) ────────
            // Regression coverage for the f9be295 → 7f92501 category boundary.
            //
            // JUKEBOX is a BlockEntityProvider AND a full solid cube, so the
            // !state.isSolidBlock gate alone would exclude it from dy=-0.5,
            // silently contradicting the isLoweredBlockEntityVisual contract
            // (which covers every BE block regardless of cube shape).
            // isSlabSitCandidate's explicit BlockEntityProvider allowlist
            // restores it.
            //
            // Place JUKEBOX on the FULL lane (x=0, no offset: dy==0.0) and the
            // BOTTOM_SLAB lane (x=2, dy==-0.5). Assert the two outcomes side by
            // side and capture a screenshot from the model-height eye-level
            // camera so a reviewer can see the BOTTOM_SLAB jukebox visibly
            // sitting on the slab top while the FULL jukebox sits flush at
            // the stone top.
            final BlockPos jukeboxFullPos = FIXTURE_ORIGIN.add(0, 1, 0);
            singleplayer.getServer().runOnServer(server -> {
                // Clear the upper chest left behind by Step 4b so the screenshot
                // is a clean side-by-side comparison.
                server.getOverworld().setBlockState(
                        upperChestPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                // FULL lane: replaces OAK_LOG with JUKEBOX.
                server.getOverworld().setBlockState(
                        jukeboxFullPos, Blocks.JUKEBOX.getDefaultState(), Block.NOTIFY_LISTENERS);
                // BOTTOM_SLAB lane: replaces STONE with JUKEBOX.
                server.getOverworld().setBlockState(
                        probePos, Blocks.JUKEBOX.getDefaultState(), Block.NOTIFY_LISTENERS);
            });

            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world is null during jukebox slab-sit check");
                }
                BlockState fullJukebox = mc.world.getBlockState(jukeboxFullPos);
                BlockState slabJukebox = mc.world.getBlockState(probePos);
                if (!fullJukebox.isOf(Blocks.JUKEBOX)) {
                    throw new RuntimeException(
                            "client: FULL-lane jukebox missing at " + jukeboxFullPos.toShortString()
                            + ", found: " + fullJukebox.getBlock().getTranslationKey());
                }
                if (!slabJukebox.isOf(Blocks.JUKEBOX)) {
                    throw new RuntimeException(
                            "client: BOTTOM_SLAB-lane jukebox missing at " + probePos.toShortString()
                            + ", found: " + slabJukebox.getBlock().getTranslationKey());
                }

                // FULL lane: no slab below → dy must be 0.0, outline minY == 0.0.
                double dyFull = SlabSupport.getYOffset(mc.world, jukeboxFullPos, fullJukebox);
                if (dyFull != 0.0) {
                    throw new RuntimeException(
                            "FULL-lane JUKEBOX must not lower; got dy=" + dyFull
                            + " (isSlabSitCandidate firing without a slab in column?)");
                }

                // BOTTOM_SLAB lane: BlockEntityProvider + bottom slab below →
                // dy must be -0.5 via the isSlabSitCandidate BE allowlist.
                double dySlab = SlabSupport.getYOffset(mc.world, probePos, slabJukebox);
                if (dySlab != -0.5) {
                    throw new RuntimeException(
                            "BOTTOM_SLAB-lane JUKEBOX must lower; got dy=" + dySlab
                            + " (isSlabSitCandidate BlockEntityProvider allowlist regressed —"
                            + " full-cube BE blocks no longer sit on slabs)");
                }

                VoxelShape slabOutline = slabJukebox.getOutlineShape(
                        mc.world, probePos, ShapeContext.absent());
                if (slabOutline.isEmpty()) {
                    throw new RuntimeException("JUKEBOX outline unexpectedly empty");
                }
                double slabMinY = slabOutline.getBoundingBox().minY;
                if (slabMinY != -0.5) {
                    throw new RuntimeException(
                            "BOTTOM_SLAB-lane JUKEBOX outline minY expected -0.5, got " + slabMinY
                            + " — offsetOutline mixin disagrees with shouldOffset");
                }

                // Contract: isLoweredBlockEntityVisual covers every BE block.
                if (!SlabSupport.isLoweredBlockEntityVisual(mc.world, probePos, slabJukebox)) {
                    throw new RuntimeException(
                            "isLoweredBlockEntityVisual=false for BOTTOM_SLAB JUKEBOX"
                            + " — BE contract broken");
                }
            });

            // Reuse the model-height side view (same eye-level as OAK_LOG proof)
            // so the reviewer can visually verify: left jukebox (FULL lane)
            // sits flush at the stone top Y=201.0; right jukebox (BOTTOM_SLAB
            // lane) sits on the slab top Y=200.5. The 0.5-block vertical offset
            // is the visible proof that the full-cube BE slab-sit path is live.
            ctx.runOnClient(mc -> {
                if (mc.player != null) {
                    mc.player.refreshPositionAndAngles(
                            MODEL_CAM_X, MODEL_CAM_Y, MODEL_CAM_Z, MODEL_CAM_YAW, MODEL_CAM_PITCH);
                }
            });
            singleplayer.getClientWorld().waitForChunksRender();
            captureScreenshotAndRecord(
                    ctx,
                    "slabbed_jukebox_slabsit_proof",
                    screenshotDir,
                    knownScreenshotFiles,
                    artifacts);

            // ── Step 7: focused lowered-full-block side-slab repro ───────────────
            // Exact bug class under test:
            //   bottom slab support block
            //   full block visually lowered on it
            //   slab-item use on the lower-half horizontal face of the lowered block
            //   repeat the same click path
            //
            // PASS criteria:
            //   1) first click lands in the intended lowered-space side branch
            //   2) second identical click combines in-place instead of climbing upward
            //   3) no upward-stack or ghost-face relapse remains in the final frame
            runLoweredSideSlabPlacementRepro(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

            writeRunManifest(screenshotDir, artifacts);
            assertLoweredSideSlabProofArtifacts(screenshotDir);
            writeProofSummary(screenshotDir);
            writeProofIndex(screenshotDir);
            writeLatestProofRun(screenshotDir);
            writeProofReceipt(screenshotDir);
            assertLoweredSideSlabProofBundle(screenshotDir);
        }
    }

    private static void captureScreenshotAndRecord(
            ClientGameTestContext ctx,
            String proofId,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        ctx.takeScreenshot(proofId);
        String label = labelForProofId(proofId);

        String resolvedFile = resolveScreenshotFileNameForProofId(screenshotDir, proofId);
        if (resolvedFile != null) {
            artifacts.add(new ManifestArtifact(resolvedFile, proofId, label));
        }

        Set<String> afterCapture = listScreenshotFileNames(screenshotDir);
        for (String fileName : afterCapture) {
            if (!knownScreenshotFiles.contains(fileName)) {
                artifacts.add(new ManifestArtifact(fileName, proofId, label));
            }
        }
        knownScreenshotFiles.clear();
        knownScreenshotFiles.addAll(afterCapture);
    }

    static void runLoweredSideSlabPlacementRepro(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        // ownership
        runLowerHalfOwnershipProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

        // placement-branch
        runRepeatClickPlacementBranchProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

        // rescue guard
        runTorchRescueGuardProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

        // rescue guard
        runBedRescueGuardProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

        // rescue guard no-go
        runChainNoRescueTargetingProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

        // rescue guard no-go
        runCraftingTableNoRescueTargetingProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

        // baseline guard
        runFullBlockBaselineGuardProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

        // baseline guard
        runVanillaSlabBaselineGuardProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);
    }

    static void runLowerHalfOwnershipProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        final String testId = "fb_on_bs_lower_half_owner_targeting";
        final BlockPos supportPos = FIXTURE_ORIGIN.add(12, 0, 0);
        final BlockPos fullPos = supportPos.up();
        final BlockPos placePos = fullPos.east();
        final BlockHitResult hit = new BlockHitResult(
                new Vec3d(fullPos.getX() + 1.0, fullPos.getY() - 0.25, fullPos.getZ() + 0.5),
                Direction.EAST,
                fullPos,
                false,
                false);
        AtomicReference<String> winningTarget = new AtomicReference<>();
        AtomicReference<String> winningState = new AtomicReference<>();
        AtomicReference<String> actionResult = new AtomicReference<>();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer server player list is empty for ownership proof");
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 4));
        });

        ctx.runOnClient(mc -> {
            if (mc.player == null) {
                throw new RuntimeException("client player is null during ownership proof setup");
            }
            mc.player.refreshPositionAndAngles(
                    fullPos.getX() + 0.5,
                    fullPos.getY() + 1.95,
                    fullPos.getZ() + 3.25,
                    180.0f,
                    24.0f);
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 4));
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        captureScreenshotAndRecord(
                ctx,
                testId + "_setup",
                screenshotDir,
                knownScreenshotFiles,
                artifacts);

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for ownership interact proof");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            actionResult.set(result.toString());
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world is null during ownership assertion");
            }
            BlockState supportState = mc.world.getBlockState(supportPos);
            BlockState placedState = mc.world.getBlockState(placePos);
            if (!supportState.isOf(Blocks.STONE_SLAB)
                    || !supportState.contains(SlabBlock.TYPE)
                    || supportState.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException(
                        "ownership proof failed: support slab changed unexpectedly at "
                        + supportPos.toShortString() + " to " + supportState);
            }
            if (!placedState.isOf(Blocks.STONE_SLAB)
                    || !placedState.contains(SlabBlock.TYPE)
                    || placedState.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException(
                        "ownership proof failed: expected side placement at "
                        + placePos.toShortString() + " from full-block target, found " + placedState);
            }
            winningTarget.set(fullPos.toShortString());
            winningState.set(placedState.toString());
        });

        captureScreenshotAndRecord(
                ctx,
                testId,
                screenshotDir,
                knownScreenshotFiles,
                artifacts);

        writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "ownership",
                "Lower-half targeting resolves to lowered full block and not to support slab.",
                testId + "_setup",
                testId,
                List.of(
                        new NoteField("supportPos", supportPos.toShortString()),
                        new NoteField("placePos", placePos.toShortString()),
                        new NoteField("expectedWinningTarget", fullPos.toShortString()),
                        new NoteField("observedWinningTarget", nullToEmpty(winningTarget.get())),
                        new NoteField("observedWinningState", nullToEmpty(winningState.get())),
                        new NoteField("actionResult", nullToEmpty(actionResult.get()))
                ),
                true);
    }

    static void runRepeatClickPlacementBranchProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        final BlockPos reproSupportPos = FIXTURE_ORIGIN.add(8, 0, 0);
        final BlockPos reproFullPos = reproSupportPos.up();
        final BlockPos reproPlacePos = reproFullPos.east();
        final BlockHitResult reproHit = new BlockHitResult(
                new Vec3d(
                        reproFullPos.getX() + 1.0,
                        reproFullPos.getY() - 0.25,
                        reproFullPos.getZ() + 0.5),
                Direction.EAST,
                reproFullPos,
                false,
                false);
        final double camX = reproFullPos.getX() + 0.5;
        final double camY = reproFullPos.getY() + 1.95;
        final double camZ = reproFullPos.getZ() + 3.25;
        final float camYaw = 180.0f;
        final float camPitch = 24.0f;

        AtomicReference<String> firstClickState = new AtomicReference<>();
        AtomicReference<String> firstClickAboveState = new AtomicReference<>();
        AtomicReference<String> firstClickResult = new AtomicReference<>();
        AtomicReference<String> secondClickState = new AtomicReference<>();
        AtomicReference<String> secondClickAboveState = new AtomicReference<>();
        AtomicReference<String> secondClickResult = new AtomicReference<>();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    reproSupportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(reproFullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(reproPlacePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(reproPlacePos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);

            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer server player list is empty for slab repro");
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });

        ctx.runOnClient(mc -> {
            if (mc.player == null) {
                throw new RuntimeException("client player is null during lowered slab repro setup");
            }
            mc.player.refreshPositionAndAngles(camX, camY, camZ, camYaw, camPitch);
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        captureScreenshotAndRecord(
                ctx,
                "fb_on_bs_lower_half_side_slab_intent_setup",
                screenshotDir,
                knownScreenshotFiles,
                artifacts);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for lowered slab first click");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, reproHit);
            firstClickResult.set(result.toString());
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world is null during lowered slab first assertion");
            }
            BlockState placed = mc.world.getBlockState(reproPlacePos);
            BlockState above = mc.world.getBlockState(reproPlacePos.up());
            if (!placed.isOf(Blocks.STONE_SLAB)) {
                throw new RuntimeException(
                        "first click expected STONE_SLAB at " + reproPlacePos.toShortString()
                        + ", found " + placed.getBlock().getTranslationKey());
            }
            if (!placed.contains(SlabBlock.TYPE) || placed.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException(
                        "first click expected bottom slab at " + reproPlacePos.toShortString()
                        + ", found " + placed);
            }
            if (!above.isAir()) {
                throw new RuntimeException(
                        "first click must not upward-stack; expected air at "
                        + reproPlacePos.up().toShortString()
                        + ", found " + above.getBlock().getTranslationKey());
            }
            firstClickState.set(placed.toString());
            firstClickAboveState.set(above.toString());
        });

        captureScreenshotAndRecord(
                ctx,
                "fb_on_bs_lower_half_side_slab_intent",
                screenshotDir,
                knownScreenshotFiles,
                artifacts);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for lowered slab repeat click");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, reproHit);
            secondClickResult.set(result.toString());
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world is null during lowered slab repeat assertion");
            }
            BlockState placed = mc.world.getBlockState(reproPlacePos);
            BlockState above = mc.world.getBlockState(reproPlacePos.up());
            if (!placed.isOf(Blocks.STONE_SLAB)) {
                throw new RuntimeException(
                        "repeat click expected STONE_SLAB at " + reproPlacePos.toShortString()
                        + ", found " + placed.getBlock().getTranslationKey());
            }
            if (!placed.contains(SlabBlock.TYPE) || placed.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException(
                        "repeat click expected same-position combine to DOUBLE slab at "
                        + reproPlacePos.toShortString() + ", found " + placed);
            }
            if (!above.isAir()) {
                throw new RuntimeException(
                        "repeat click must not upward-stack or leave a ghost face; expected air at "
                        + reproPlacePos.up().toShortString()
                        + ", found " + above.getBlock().getTranslationKey());
            }
            secondClickState.set(placed.toString());
            secondClickAboveState.set(above.toString());
        });

        captureScreenshotAndRecord(
                ctx,
                "fb_on_bs_repeat_click_no_ghost_face",
                screenshotDir,
                knownScreenshotFiles,
                artifacts);

        writeLoweredSideSlabPlacementNotes(
                screenshotDir,
                reproSupportPos,
                reproFullPos,
                reproPlacePos,
                firstClickResult.get(),
                firstClickState.get(),
                firstClickAboveState.get(),
                secondClickResult.get(),
                secondClickState.get(),
                secondClickAboveState.get());

        writeRepeatClickNoGhostFaceNotes(
                screenshotDir,
                reproSupportPos,
                reproFullPos,
                reproPlacePos,
                firstClickResult.get(),
                firstClickState.get(),
                firstClickAboveState.get(),
                secondClickResult.get(),
                secondClickState.get(),
                secondClickAboveState.get());
    }

    static void runTorchRescueGuardProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        final String testId = "torch_on_fb_on_bs_rescue_targeting";
        final BlockPos supportPos = FIXTURE_ORIGIN.add(16, 0, 0);
        final BlockPos fullPos = supportPos.up();
        final BlockPos placePos = fullPos.east();
        final BlockHitResult hit = new BlockHitResult(
                new Vec3d(fullPos.getX() + 1.0, fullPos.getY() - 0.20, fullPos.getZ() + 0.5),
                Direction.EAST,
                fullPos,
                false,
                false);
        AtomicReference<String> placeState = new AtomicReference<>();
        AtomicReference<String> actionResult = new AtomicReference<>();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer server player list is empty for torch proof");
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.TORCH, 4));
        });

        ctx.runOnClient(mc -> {
            if (mc.player == null) {
                throw new RuntimeException("client player is null during torch proof setup");
            }
            mc.player.refreshPositionAndAngles(
                    fullPos.getX() + 0.5,
                    fullPos.getY() + 1.9,
                    fullPos.getZ() + 3.0,
                    180.0f,
                    24.0f);
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.TORCH, 4));
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        captureScreenshotAndRecord(
                ctx,
                testId + "_setup",
                screenshotDir,
                knownScreenshotFiles,
                artifacts);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for torch rescue proof");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            actionResult.set(result.toString());
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world is null during torch rescue assertion");
            }
            BlockState placed = mc.world.getBlockState(placePos);
            if (!placed.isOf(Blocks.WALL_TORCH)) {
                throw new RuntimeException(
                        "torch rescue proof expected WALL_TORCH at " + placePos.toShortString()
                        + ", found " + placed);
            }
            placeState.set(placed.toString());
        });

        captureScreenshotAndRecord(
                ctx,
                testId,
                screenshotDir,
                knownScreenshotFiles,
                artifacts);

        writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "rescue guard",
                "Torch targeting/placement remains correct on lowered full-block-over-slab stack.",
                testId + "_setup",
                testId,
                List.of(
                        new NoteField("targetPos", fullPos.toShortString()),
                        new NoteField("placePos", placePos.toShortString()),
                        new NoteField("actionResult", nullToEmpty(actionResult.get())),
                        new NoteField("observedState", nullToEmpty(placeState.get()))
                ),
                true);
    }

    static void runBedRescueGuardProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        final String testId = "bed_on_bs_rescue_targeting";
        final BlockPos supportPos = FIXTURE_ORIGIN.add(20, 0, 0);
        final BlockPos fullPos = supportPos.up();
        final BlockPos topSupportSouth = fullPos.south();
        final BlockPos footPos = fullPos.up();
        final BlockHitResult hit = new BlockHitResult(
                new Vec3d(fullPos.getX() + 0.5, fullPos.getY() + 1.0, fullPos.getZ() + 0.5),
                Direction.UP,
                fullPos,
                false,
                false);
        AtomicReference<String> actionResult = new AtomicReference<>();
        AtomicReference<String> footStateText = new AtomicReference<>();
        AtomicReference<String> headPosText = new AtomicReference<>();
        AtomicReference<String> headStateText = new AtomicReference<>();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(topSupportSouth, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(footPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(footPos.north(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(footPos.south(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(footPos.east(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(footPos.west(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer server player list is empty for bed proof");
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.RED_BED, 2));
        });

        ctx.runOnClient(mc -> {
            if (mc.player == null) {
                throw new RuntimeException("client player is null during bed proof setup");
            }
            mc.player.refreshPositionAndAngles(
                    fullPos.getX() + 0.5,
                    fullPos.getY() + 2.1,
                    fullPos.getZ() + 3.0,
                    180.0f,
                    18.0f);
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.RED_BED, 2));
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        captureScreenshotAndRecord(
                ctx,
                testId + "_setup",
                screenshotDir,
                knownScreenshotFiles,
                artifacts);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for bed rescue proof");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            actionResult.set(result.toString());
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world is null during bed rescue assertion");
            }
            BlockState footState = mc.world.getBlockState(footPos);
            if (!footState.isOf(Blocks.RED_BED) || !footState.contains(BedBlock.PART)
                    || footState.get(BedBlock.PART) != BedPart.FOOT) {
                throw new RuntimeException(
                        "bed rescue proof expected RED_BED FOOT at " + footPos.toShortString()
                        + ", found " + footState);
            }
            Direction bedFacing = footState.contains(BedBlock.FACING)
                    ? footState.get(BedBlock.FACING)
                    : Direction.NORTH;
            BlockPos expectedHeadPos = footPos.offset(bedFacing);
            BlockState expectedHeadState = mc.world.getBlockState(expectedHeadPos);
            if (!expectedHeadState.isOf(Blocks.RED_BED)
                    || !expectedHeadState.contains(BedBlock.PART)
                    || expectedHeadState.get(BedBlock.PART) != BedPart.HEAD) {
                throw new RuntimeException(
                        "bed rescue proof expected RED_BED HEAD at "
                        + expectedHeadPos.toShortString()
                        + " from facing " + bedFacing
                        + ", found " + expectedHeadState);
            }
            footStateText.set(footState.toString());
            headPosText.set(expectedHeadPos.toShortString());
            headStateText.set(expectedHeadState.toString());
        });

        captureScreenshotAndRecord(
                ctx,
                testId,
                screenshotDir,
                knownScreenshotFiles,
                artifacts);

        writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "rescue guard",
                "Bed targeting/placement remains correct on lowered full-block-over-slab stack.",
                testId + "_setup",
                testId,
                List.of(
                        new NoteField("targetPos", fullPos.toShortString()),
                        new NoteField("footPos", footPos.toShortString()),
                        new NoteField("headPos", nullToEmpty(headPosText.get())),
                        new NoteField("actionResult", nullToEmpty(actionResult.get())),
                        new NoteField("footState", nullToEmpty(footStateText.get())),
                        new NoteField("headState", nullToEmpty(headStateText.get()))
                ),
                true);
    }

    static void runChainNoRescueTargetingProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        runNoRescueTargetingProof(
                ctx,
                singleplayer,
                screenshotDir,
                knownScreenshotFiles,
                artifacts,
                "chain_on_fb_on_bs_no_rescue_targeting",
                Blocks.IRON_CHAIN.getDefaultState().with(net.minecraft.block.ChainBlock.AXIS, Direction.Axis.Y),
                new BlockPos(32, 0, 0),
                "Chain must not be promoted as an owner-style rescue target above the slab hit.");
    }

    static void runCraftingTableNoRescueTargetingProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        runNoRescueTargetingProof(
                ctx,
                singleplayer,
                screenshotDir,
                knownScreenshotFiles,
                artifacts,
                "crafting_table_on_bs_no_rescue_targeting",
                Blocks.CRAFTING_TABLE.getDefaultState(),
                new BlockPos(36, 0, 0),
                "Crafting table must not be promoted as an owner-style rescue target above the slab hit.");
    }

    static void runNoRescueTargetingProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts,
            String testId,
            BlockState aboveState,
            BlockPos originOffset,
            String expectedInvariant
    ) {
        final BlockPos supportPos = FIXTURE_ORIGIN.add(originOffset.getX(), originOffset.getY(), originOffset.getZ());
        final BlockPos fullPos = supportPos.up();
        final BlockPos placePos = fullPos.east();
        final BlockPos slabPos = supportPos;
        final BlockHitResult slabHit = new BlockHitResult(
                new Vec3d(fullPos.getX() + 1.0, fullPos.getY() - 0.30, fullPos.getZ() + 0.5),
                Direction.EAST,
                fullPos,
                false,
                false);
        AtomicReference<String> vanillaHitText = new AtomicReference<>();
        AtomicReference<String> finalHitText = new AtomicReference<>();
        AtomicReference<String> aboveStateText = new AtomicReference<>();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, aboveState, Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer server player list is empty for no-rescue proof");
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 4));
        });

        ctx.runOnClient(mc -> {
            if (mc.player == null) {
                throw new RuntimeException("client player is null during no-rescue proof setup");
            }
            mc.player.refreshPositionAndAngles(
                    fullPos.getX() + 0.5,
                    fullPos.getY() + 1.95,
                    fullPos.getZ() + 3.25,
                    180.0f,
                    24.0f);
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 4));
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        captureScreenshotAndRecord(
                ctx,
                testId + "_setup",
                screenshotDir,
                knownScreenshotFiles,
                artifacts);

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("client not ready for no-rescue raycast proof");
            }
            BlockState slabState = mc.world.getBlockState(slabPos);
            BlockState above = mc.world.getBlockState(fullPos);
            if (!slabState.isOf(Blocks.STONE_SLAB) || !slabState.contains(SlabBlock.TYPE)
                    || slabState.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException(
                        "no-rescue proof expected support slab at " + slabPos.toShortString()
                        + ", found " + slabState);
            }
            if (!above.getBlock().equals(aboveState.getBlock())) {
                throw new RuntimeException(
                        "no-rescue proof expected " + aboveState.getBlock().getTranslationKey()
                        + " at " + fullPos.toShortString() + ", found " + above);
            }
            if (SlabSupport.isLoweredBlockEntityVisual(mc.world, fullPos, above)) {
                throw new RuntimeException(
                        "no-rescue proof setup unexpectedly qualifies as lowered block-entity visual");
            }

            Vec3d eye = new Vec3d(
                    fullPos.getX() + 0.5,
                    fullPos.getY() + 1.12,
                    fullPos.getZ() + 3.0);
            Vec3d target = new Vec3d(
                    fullPos.getX() + 0.5,
                    fullPos.getY() - 0.30,
                    fullPos.getZ() + 0.5);
            Vec3d dir = target.subtract(eye).normalize();
            Vec3d end = eye.add(dir.multiply(6.0));

            BlockHitResult vanillaHit = mc.world.raycast(new RaycastContext(
                    eye, end,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            if (vanillaHit.getType() != HitResult.Type.BLOCK) {
                throw new RuntimeException(
                        "no-rescue proof vanilla raycast missed; expected slab hit at "
                        + fullPos.toShortString() + ", got " + vanillaHit.getType());
            }
            if (!vanillaHit.getBlockPos().equals(slabPos)) {
                throw new RuntimeException(
                        "no-rescue proof vanilla raycast expected slab hit at "
                        + slabPos.toShortString() + ", got " + vanillaHit.getBlockPos().toShortString());
            }
            vanillaHitText.set(vanillaHit.getBlockPos().toShortString());

            BlockHitResult finalHit = vanillaHit;
            if (vanillaHit.getType() == HitResult.Type.BLOCK) {
                BlockPos hitPos = vanillaHit.getBlockPos();
                BlockPos abovePos = hitPos.up();
                BlockState aboveStateAtHit = mc.world.getBlockState(abovePos);
                boolean loweredOwner =
                        SlabSupport.isLoweredBlockEntityVisual(mc.world, abovePos, aboveStateAtHit)
                                || SlabSupport.isLoweredTorchVisual(mc.world, abovePos, aboveStateAtHit)
                                || SlabSupport.isLoweredBedVisual(mc.world, abovePos, aboveStateAtHit);
                if (!loweredOwner) {
                    Block block = aboveStateAtHit.getBlock();
                    loweredOwner = aboveStateAtHit.isSolidBlock(mc.world, abovePos)
                            && !(block instanceof net.minecraft.block.BlockEntityProvider)
                            && !(block instanceof net.minecraft.block.CraftingTableBlock)
                            && SlabSupport.getYOffset(mc.world, abovePos, aboveStateAtHit) == -0.5;
                }
                if (loweredOwner) {
                    VoxelShape aboveOutline = aboveStateAtHit.getOutlineShape(
                            mc.world, abovePos, ShapeContext.of(mc.player));
                    BlockHitResult retargetHit = aboveOutline.raycast(eye, end, abovePos);
                    if (retargetHit != null) {
                        double retargetDist2 = retargetHit.getPos().squaredDistanceTo(eye);
                        double originalDist2 = vanillaHit.getPos().squaredDistanceTo(eye);
                        if (retargetDist2 <= originalDist2 + 1.0e-6) {
                            finalHit = retargetHit;
                        }
                    }
                }
            }
            if (!finalHit.getBlockPos().equals(slabPos)) {
                throw new RuntimeException(
                        "no-rescue proof unexpectedly retargeted to "
                        + finalHit.getBlockPos().toShortString()
                        + " (" + finalHit.getBlockPos().up() + " owner path)"
                        + "; expected slab hit at " + slabPos.toShortString());
            }
            finalHitText.set(finalHit.getBlockPos().toShortString());
            aboveStateText.set(above.toString());
        });

        captureScreenshotAndRecord(
                ctx,
                testId,
                screenshotDir,
                knownScreenshotFiles,
                artifacts);

        writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "rescue guard no-go",
                expectedInvariant,
                testId + "_setup",
                testId,
                List.of(
                        new NoteField("supportPos", supportPos.toShortString()),
                        new NoteField("fullPos", fullPos.toShortString()),
                        new NoteField("slabHit", nullToEmpty(vanillaHitText.get())),
                        new NoteField("finalHit", nullToEmpty(finalHitText.get())),
                        new NoteField("aboveState", nullToEmpty(aboveStateText.get()))
                ),
                true);
    }

    static void runFullBlockBaselineGuardProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        final String testId = "full_block_on_full_block_baseline";
        final BlockPos basePos = FIXTURE_ORIGIN.add(24, 0, 0);
        final BlockPos hitPos = basePos.up();
        final BlockPos placePos = hitPos.east();
        final BlockHitResult hit = new BlockHitResult(
                new Vec3d(hitPos.getX() + 1.0, hitPos.getY() - 0.25, hitPos.getZ() + 0.5),
                Direction.EAST,
                hitPos,
                false,
                false);
        AtomicReference<String> actionResult = new AtomicReference<>();
        AtomicReference<String> placeState = new AtomicReference<>();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(basePos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(hitPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer server player list is empty for full-block baseline");
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE, 6));
        });

        ctx.runOnClient(mc -> {
            if (mc.player == null) {
                throw new RuntimeException("client player is null during full-block baseline setup");
            }
            mc.player.refreshPositionAndAngles(
                    hitPos.getX() + 0.5,
                    hitPos.getY() + 1.9,
                    hitPos.getZ() + 3.0,
                    180.0f,
                    24.0f);
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 6));
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        captureScreenshotAndRecord(
                ctx,
                testId + "_setup",
                screenshotDir,
                knownScreenshotFiles,
                artifacts);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for full-block baseline proof");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            actionResult.set(result.toString());
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world is null during full-block baseline assertion");
            }
            BlockState placed = mc.world.getBlockState(placePos);
            BlockState down = mc.world.getBlockState(placePos.down());
            if (!placed.isOf(Blocks.STONE)) {
                throw new RuntimeException(
                        "full-block baseline expected STONE at " + placePos.toShortString()
                        + ", found " + placed);
            }
            if (!down.isAir()) {
                throw new RuntimeException(
                        "full-block baseline must not remap downward; expected air at "
                        + placePos.down().toShortString() + ", found " + down);
            }
            placeState.set(placed.toString());
        });

        captureScreenshotAndRecord(
                ctx,
                testId,
                screenshotDir,
                knownScreenshotFiles,
                artifacts);

        writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "baseline guard",
                "Ordinary full-block-on-full-block side placement remains normal and unshifted.",
                testId + "_setup",
                testId,
                List.of(
                        new NoteField("targetPos", hitPos.toShortString()),
                        new NoteField("placePos", placePos.toShortString()),
                        new NoteField("actionResult", nullToEmpty(actionResult.get())),
                        new NoteField("observedState", nullToEmpty(placeState.get()))
                ),
                true);
    }

    static void runVanillaSlabBaselineGuardProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        final String testId = "slab_on_normal_vanilla_face_baseline";
        final BlockPos basePos = FIXTURE_ORIGIN.add(28, 0, 0);
        final BlockPos hitPos = basePos.up();
        final BlockPos placePos = hitPos.east();
        final BlockHitResult hit = new BlockHitResult(
                new Vec3d(hitPos.getX() + 1.0, hitPos.getY() - 0.25, hitPos.getZ() + 0.5),
                Direction.EAST,
                hitPos,
                false,
                false);
        AtomicReference<String> actionResult = new AtomicReference<>();
        AtomicReference<String> placeState = new AtomicReference<>();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(basePos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(hitPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer server player list is empty for slab baseline");
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 6));
        });

        ctx.runOnClient(mc -> {
            if (mc.player == null) {
                throw new RuntimeException("client player is null during slab baseline setup");
            }
            mc.player.refreshPositionAndAngles(
                    hitPos.getX() + 0.5,
                    hitPos.getY() + 1.9,
                    hitPos.getZ() + 3.0,
                    180.0f,
                    24.0f);
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 6));
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        captureScreenshotAndRecord(
                ctx,
                testId + "_setup",
                screenshotDir,
                knownScreenshotFiles,
                artifacts);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for slab baseline proof");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            actionResult.set(result.toString());
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world is null during slab baseline assertion");
            }
            BlockState placed = mc.world.getBlockState(placePos);
            BlockState down = mc.world.getBlockState(placePos.down());
            if (!placed.isOf(Blocks.STONE_SLAB)
                    || !placed.contains(SlabBlock.TYPE)
                    || placed.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException(
                        "slab baseline expected BOTTOM STONE_SLAB at " + placePos.toShortString()
                        + ", found " + placed);
            }
            if (!down.isAir()) {
                throw new RuntimeException(
                        "slab baseline must not remap downward; expected air at "
                        + placePos.down().toShortString() + ", found " + down);
            }
            placeState.set(placed.toString());
        });

        captureScreenshotAndRecord(
                ctx,
                testId,
                screenshotDir,
                knownScreenshotFiles,
                artifacts);

        writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "baseline guard",
                "Vanilla slab placement on normal full-block target remains unchanged.",
                testId + "_setup",
                testId,
                List.of(
                        new NoteField("targetPos", hitPos.toShortString()),
                        new NoteField("placePos", placePos.toShortString()),
                        new NoteField("actionResult", nullToEmpty(actionResult.get())),
                        new NoteField("observedState", nullToEmpty(placeState.get()))
                ),
                true);
    }

    static void writeInvariantProofNotes(
            Path screenshotDir,
            String noteFileName,
            String testId,
            String proofClass,
            String expectedInvariant,
            String setupProofId,
            String resultProofId,
            List<NoteField> observedFields,
            boolean pass
    ) {
        try {
            Files.createDirectories(screenshotDir);
            Path notesPath = screenshotDir.resolve(noteFileName);
            String setupFile = resolveScreenshotFileNameForProofId(screenshotDir, setupProofId);
            String resultFile = resolveScreenshotFileNameForProofId(screenshotDir, resultProofId);
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"testId\": \"").append(escapeJson(testId)).append("\",\n");
            sb.append("  \"proofClass\": \"").append(escapeJson(proofClass)).append("\",\n");
            sb.append("  \"expectedInvariant\": \"").append(escapeJson(expectedInvariant)).append("\",\n");
            sb.append("  \"screenshots\": {\n");
            sb.append("    \"setup\": \"").append(escapeJson(nullToEmpty(setupFile))).append("\",\n");
            sb.append("    \"result\": \"").append(escapeJson(nullToEmpty(resultFile))).append("\"\n");
            sb.append("  },\n");
            sb.append("  \"observed\": {\n");
            for (int i = 0; i < observedFields.size(); i++) {
                NoteField field = observedFields.get(i);
                sb.append("    \"")
                        .append(escapeJson(field.key()))
                        .append("\": \"")
                        .append(escapeJson(nullToEmpty(field.value())))
                        .append("\"");
                if (i + 1 < observedFields.size()) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("  },\n");
            sb.append("  \"pass\": ").append(pass).append("\n");
            sb.append("}\n");
            Files.writeString(notesPath, sb.toString());
        } catch (IOException ignored) {
            // Notes are auxiliary evidence; assertion failures remain authoritative.
        }
    }

    static void writeLoweredSideSlabPlacementNotes(
            Path screenshotDir,
            BlockPos supportPos,
            BlockPos fullPos,
            BlockPos placePos,
            String firstClickResult,
            String firstClickState,
            String firstClickAboveState,
            String secondClickResult,
            String secondClickState,
            String secondClickAboveState
    ) {
        try {
            Files.createDirectories(screenshotDir);
            Path notesPath = screenshotDir.resolve("fb_on_bs_lower_half_side_slab_intent_notes.json");
            String setupFile = resolveScreenshotFileNameForProofId(
                    screenshotDir,
                    "fb_on_bs_lower_half_side_slab_intent_setup");
            String firstFile = resolveScreenshotFileNameForProofId(
                    screenshotDir,
                    "fb_on_bs_lower_half_side_slab_intent");
            String secondFile = resolveScreenshotFileNameForProofId(
                    screenshotDir,
                    "fb_on_bs_repeat_click_no_ghost_face");

            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"testId\": \"fb_on_bs_lower_half_side_slab_intent\",\n");
            sb.append("  \"expectedBranch\": \"lowered-space side branch\",\n");
            sb.append("  \"supportPos\": \"").append(escapeJson(supportPos.toShortString())).append("\",\n");
            sb.append("  \"fullBlockPos\": \"").append(escapeJson(fullPos.toShortString())).append("\",\n");
            sb.append("  \"placePos\": \"").append(escapeJson(placePos.toShortString())).append("\",\n");
            sb.append("  \"screenshots\": {\n");
            sb.append("    \"setup\": \"").append(escapeJson(nullToEmpty(setupFile))).append("\",\n");
            sb.append("    \"firstClick\": \"").append(escapeJson(nullToEmpty(firstFile))).append("\",\n");
            sb.append("    \"repeatClick\": \"").append(escapeJson(nullToEmpty(secondFile))).append("\"\n");
            sb.append("  },\n");
            sb.append("  \"observed\": {\n");
            sb.append("    \"firstActionResult\": \"").append(escapeJson(nullToEmpty(firstClickResult))).append("\",\n");
            sb.append("    \"firstActionState\": \"").append(escapeJson(nullToEmpty(firstClickState))).append("\",\n");
            sb.append("    \"firstActionAbove\": \"").append(escapeJson(nullToEmpty(firstClickAboveState))).append("\",\n");
            sb.append("    \"secondActionResult\": \"").append(escapeJson(nullToEmpty(secondClickResult))).append("\",\n");
            sb.append("    \"secondActionState\": \"").append(escapeJson(nullToEmpty(secondClickState))).append("\",\n");
            sb.append("    \"secondActionAbove\": \"").append(escapeJson(nullToEmpty(secondClickAboveState))).append("\"\n");
            sb.append("  },\n");
            sb.append("  \"repeatCausedUpwardStack\": false,\n");
            sb.append("  \"ghostFaceRelapse\": false\n");
            sb.append("}\n");
            Files.writeString(notesPath, sb.toString());
        } catch (IOException ignored) {
            // Notes are auxiliary evidence; the test assertions remain authoritative.
        }
    }

    static void writeRepeatClickNoGhostFaceNotes(
            Path screenshotDir,
            BlockPos supportPos,
            BlockPos fullPos,
            BlockPos placePos,
            String firstClickResult,
            String firstClickState,
            String firstClickAboveState,
            String secondClickResult,
            String secondClickState,
            String secondClickAboveState
    ) {
        try {
            Files.createDirectories(screenshotDir);
            Path notesPath = screenshotDir.resolve("fb_on_bs_repeat_click_no_ghost_face_notes.json");
            String setupFile = resolveScreenshotFileNameForProofId(
                    screenshotDir,
                    "fb_on_bs_lower_half_side_slab_intent_setup");
            String firstFile = resolveScreenshotFileNameForProofId(
                    screenshotDir,
                    "fb_on_bs_lower_half_side_slab_intent");
            String secondFile = resolveScreenshotFileNameForProofId(
                    screenshotDir,
                    "fb_on_bs_repeat_click_no_ghost_face");

            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"testId\": \"fb_on_bs_repeat_click_no_ghost_face\",\n");
            sb.append("  \"expectedBranch\": \"repeat click remains in-place\",\n");
            sb.append("  \"supportPos\": \"").append(escapeJson(supportPos.toShortString())).append("\",\n");
            sb.append("  \"fullBlockPos\": \"").append(escapeJson(fullPos.toShortString())).append("\",\n");
            sb.append("  \"placePos\": \"").append(escapeJson(placePos.toShortString())).append("\",\n");
            sb.append("  \"screenshots\": {\n");
            sb.append("    \"setup\": \"").append(escapeJson(nullToEmpty(setupFile))).append("\",\n");
            sb.append("    \"firstClick\": \"").append(escapeJson(nullToEmpty(firstFile))).append("\",\n");
            sb.append("    \"repeatClick\": \"").append(escapeJson(nullToEmpty(secondFile))).append("\"\n");
            sb.append("  },\n");
            sb.append("  \"observed\": {\n");
            sb.append("    \"firstActionResult\": \"").append(escapeJson(nullToEmpty(firstClickResult))).append("\",\n");
            sb.append("    \"firstActionState\": \"").append(escapeJson(nullToEmpty(firstClickState))).append("\",\n");
            sb.append("    \"firstActionAbove\": \"").append(escapeJson(nullToEmpty(firstClickAboveState))).append("\",\n");
            sb.append("    \"secondActionResult\": \"").append(escapeJson(nullToEmpty(secondClickResult))).append("\",\n");
            sb.append("    \"secondActionState\": \"").append(escapeJson(nullToEmpty(secondClickState))).append("\",\n");
            sb.append("    \"secondActionAbove\": \"").append(escapeJson(nullToEmpty(secondClickAboveState))).append("\"\n");
            sb.append("  },\n");
            sb.append("  \"repeatCausedUpwardStack\": false,\n");
            sb.append("  \"ghostFaceRelapse\": false\n");
            sb.append("}\n");
            Files.writeString(notesPath, sb.toString());
        } catch (IOException ignored) {
            // Notes are auxiliary evidence; the test assertions remain authoritative.
        }
    }

    static Path resolveClientGameTestScreenshotDir() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path directScreenshots = gameDir.resolve("screenshots");
        if (Files.isDirectory(directScreenshots)) {
            return directScreenshots;
        }

        Path parent = gameDir.getParent();
        if (parent != null) {
            Path parentScreenshots = parent.resolve("screenshots");
            if (Files.isDirectory(parentScreenshots)) {
                return parentScreenshots;
            }
        }

        return directScreenshots;
    }

    static String resolveScreenshotFileNameForProofId(Path screenshotDir, String proofId) {
        if (!Files.isDirectory(screenshotDir)) {
            return null;
        }
        try (var stream = Files.list(screenshotDir)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".png"))
                    .filter(path -> path.getFileName().toString().contains(proofId + ".png"))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .findFirst()
                    .orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }

    static String labelForProofId(String proofId) {
        String[] parts = proofId.split("_");
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                label.append(part.substring(1));
            }
        }
        return label.toString();
    }

    static Set<String> listScreenshotFileNames(Path screenshotDir) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (!Files.isDirectory(screenshotDir)) {
            return names;
        }
        try (var stream = Files.list(screenshotDir)) {
            stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".png"))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .forEach(names::add);
        } catch (IOException ignored) {
            // Keep manifest output best-effort and avoid changing test assertions.
        }
        return names;
    }

    static void writeRunManifest(Path screenshotDir, List<ManifestArtifact> artifacts) {
        try {
            Files.createDirectories(screenshotDir);
            Path manifestPath = screenshotDir.resolve("run_manifest.json");
            RunProvenance provenance = readRunProvenance();
            List<ProofManifestEntry> proofEntries = buildLoweredSideSlabProofEntries(screenshotDir);
            String json = buildManifestJson(screenshotDir, artifacts, provenance, proofEntries);
            Files.writeString(manifestPath, json);
        } catch (IOException ignored) {
            // Manifest emission is auxiliary evidence; test correctness remains assertion-driven.
        }
    }

    static void writeProofSummary(Path screenshotDir) {
        try {
            Files.createDirectories(screenshotDir);
            RunProvenance provenance = readRunProvenance();
            List<ProofManifestEntry> proofEntries = buildLoweredSideSlabProofEntries(screenshotDir);
            String json = buildProofSummaryJson(provenance, proofEntries);
            Files.writeString(screenshotDir.resolve("proof_summary.json"), json);
        } catch (IOException ignored) {
            // Summary emission is auxiliary evidence; test correctness remains assertion-driven.
        }
    }

    static void writeProofIndex(Path screenshotDir) {
        try {
            Files.createDirectories(screenshotDir);
            RunProvenance provenance = readRunProvenance();
            List<ProofManifestEntry> proofEntries = buildLoweredSideSlabProofEntries(screenshotDir);
            String json = buildProofIndexJson(provenance, proofEntries);
            Files.writeString(screenshotDir.resolve("proof_index.json"), json);
        } catch (IOException ignored) {
            // Index emission is auxiliary evidence; test correctness remains assertion-driven.
        }
    }

    static void writeLatestProofRun(Path screenshotDir) {
        try {
            Files.createDirectories(screenshotDir);

            Path manifestPath = screenshotDir.resolve("run_manifest.json");
            Path summaryPath = screenshotDir.resolve("proof_summary.json");
            Path indexPath = screenshotDir.resolve("proof_index.json");
            if (!Files.isRegularFile(manifestPath)) {
                throw new RuntimeException("latest proof pointer missing manifest target: " + manifestPath.toAbsolutePath());
            }
            if (!Files.isRegularFile(summaryPath)) {
                throw new RuntimeException("latest proof pointer missing summary target: " + summaryPath.toAbsolutePath());
            }
            if (!Files.isRegularFile(indexPath)) {
                throw new RuntimeException("latest proof pointer missing index target: " + indexPath.toAbsolutePath());
            }

            RunProvenance provenance = readRunProvenance();
            List<ProofManifestEntry> proofEntries = buildLoweredSideSlabProofEntries(screenshotDir);
            String summaryJson = Files.readString(summaryPath);
            String overallStatus = extractJsonStringValue(summaryJson, "overallStatus");
            if (overallStatus == null || overallStatus.isBlank()) {
                throw new RuntimeException("latest proof pointer missing overallStatus in summary: " + summaryPath.toAbsolutePath());
            }

            String json = buildLatestProofRunJson(provenance, proofEntries.size(), overallStatus);
            Files.writeString(screenshotDir.resolve("latest_proof_run.json"), json);
        } catch (IOException ignored) {
            // Pointer emission is auxiliary evidence; test correctness remains assertion-driven.
        }
    }

    static void writeProofReceipt(Path screenshotDir) {
        try {
            Files.createDirectories(screenshotDir);

            Path manifestPath = screenshotDir.resolve("run_manifest.json");
            Path summaryPath = screenshotDir.resolve("proof_summary.json");
            Path indexPath = screenshotDir.resolve("proof_index.json");
            Path latestPath = screenshotDir.resolve("latest_proof_run.json");
            if (!Files.isRegularFile(manifestPath)) {
                throw new RuntimeException("proof receipt missing manifest target: " + manifestPath.toAbsolutePath());
            }
            if (!Files.isRegularFile(summaryPath)) {
                throw new RuntimeException("proof receipt missing summary target: " + summaryPath.toAbsolutePath());
            }
            if (!Files.isRegularFile(indexPath)) {
                throw new RuntimeException("proof receipt missing index target: " + indexPath.toAbsolutePath());
            }
            if (!Files.isRegularFile(latestPath)) {
                throw new RuntimeException("proof receipt missing latest pointer target: " + latestPath.toAbsolutePath());
            }

            RunProvenance provenance = readRunProvenance();
            List<ProofManifestEntry> proofEntries = buildLoweredSideSlabProofEntries(screenshotDir);
            String summaryJson = Files.readString(summaryPath);
            String latestJson = Files.readString(latestPath);
            String overallStatus = extractJsonStringValue(summaryJson, "overallStatus");
            if (overallStatus == null || overallStatus.isBlank()) {
                throw new RuntimeException("proof receipt missing overallStatus in summary: " + summaryPath.toAbsolutePath());
            }
            String latestOverallStatus = extractJsonStringValue(latestJson, "overallStatus");
            if (latestOverallStatus == null || latestOverallStatus.isBlank()) {
                throw new RuntimeException("proof receipt missing overallStatus in latest pointer: " + latestPath.toAbsolutePath());
            }
            String json = buildProofReceiptMarkdown(
                    provenance,
                    proofEntries,
                    overallStatus,
                    latestOverallStatus);
            Files.writeString(screenshotDir.resolve("proof_receipt.md"), json);
        } catch (IOException ignored) {
            // Receipt emission is auxiliary evidence; test correctness remains assertion-driven.
        }
    }

    static void assertLoweredSideSlabProofBundle(Path screenshotDir) {
        Path manifestPath = screenshotDir.resolve("run_manifest.json");
        Path summaryPath = screenshotDir.resolve("proof_summary.json");
        Path indexPath = screenshotDir.resolve("proof_index.json");
        Path latestPath = screenshotDir.resolve("latest_proof_run.json");
        Path receiptPath = screenshotDir.resolve("proof_receipt.md");
        if (!Files.isRegularFile(manifestPath)) {
            throw new RuntimeException("bundle smoke check missing manifest: " + manifestPath.toAbsolutePath());
        }
        if (!Files.isRegularFile(summaryPath)) {
            throw new RuntimeException("bundle smoke check missing summary: " + summaryPath.toAbsolutePath());
        }
        if (!Files.isRegularFile(indexPath)) {
            throw new RuntimeException("bundle smoke check missing index: " + indexPath.toAbsolutePath());
        }
        if (!Files.isRegularFile(latestPath)) {
            throw new RuntimeException("bundle smoke check missing latest pointer: " + latestPath.toAbsolutePath());
        }
        if (!Files.isRegularFile(receiptPath)) {
            throw new RuntimeException("bundle smoke check missing receipt: " + receiptPath.toAbsolutePath());
        }

        final String manifestJson;
        final String summaryJson;
        final String indexJson;
        final String latestJson;
        final String receiptText;
        try {
            manifestJson = Files.readString(manifestPath);
            summaryJson = Files.readString(summaryPath);
            indexJson = Files.readString(indexPath);
            latestJson = Files.readString(latestPath);
            receiptText = Files.readString(receiptPath);
        } catch (IOException e) {
            throw new RuntimeException("bundle smoke check failed to read proof artifacts", e);
        }

        String manifestHead = extractJsonStringValue(manifestJson, "gitHeadShort");
        String summaryHead = extractJsonStringValue(summaryJson, "gitHeadShort");
        String indexHead = extractJsonStringValue(indexJson, "gitHeadShort");
        String latestHead = extractJsonStringValue(latestJson, "gitHeadShort");
        String receiptHead = extractMarkdownLineValue(receiptText, "- gitHeadShort:");
        assertSharedBundleValue("gitHeadShort", manifestHead, summaryHead, indexHead, latestHead, receiptHead);

        String manifestBranch = extractJsonStringValue(manifestJson, "gitBranch");
        String summaryBranch = extractJsonStringValue(summaryJson, "gitBranch");
        String indexBranch = extractJsonStringValue(indexJson, "gitBranch");
        String latestBranch = extractJsonStringValue(latestJson, "gitBranch");
        String receiptBranch = extractMarkdownLineValue(receiptText, "- gitBranch:");
        assertSharedBundleValue("gitBranch", manifestBranch, summaryBranch, indexBranch, latestBranch, receiptBranch);

        String summaryOverallStatus = extractJsonStringValue(summaryJson, "overallStatus");
        String latestOverallStatus = extractJsonStringValue(latestJson, "overallStatus");
        String receiptOverallStatus = extractMarkdownLineValue(receiptText, "- overallStatus:");
        if (summaryOverallStatus == null || summaryOverallStatus.isBlank()) {
            throw new RuntimeException("bundle smoke check missing overallStatus in summary");
        }
        if (latestOverallStatus == null || latestOverallStatus.isBlank()) {
            throw new RuntimeException("bundle smoke check missing overallStatus in latest pointer");
        }
        if (receiptOverallStatus == null || receiptOverallStatus.isBlank()) {
            throw new RuntimeException("bundle smoke check missing overallStatus in receipt");
        }
        assertSharedBundleValue(
                "overallStatus",
                summaryOverallStatus,
                latestOverallStatus,
                receiptOverallStatus);

        String summaryExpectedCount = extractJsonNumericValue(summaryJson, "expectedProofCount");
        String indexProofCount = extractJsonNumericValue(indexJson, "proofCount");
        String latestExpectedCount = extractJsonNumericValue(latestJson, "expectedProofCount");
        String receiptExpectedCount = extractMarkdownLineValue(receiptText, "- expectedProofCount:");
        if (summaryExpectedCount == null || summaryExpectedCount.isBlank()) {
            throw new RuntimeException("bundle smoke check missing expectedProofCount in summary");
        }
        if (indexProofCount == null || indexProofCount.isBlank()) {
            throw new RuntimeException("bundle smoke check missing proofCount in index");
        }
        if (latestExpectedCount == null || latestExpectedCount.isBlank()) {
            throw new RuntimeException("bundle smoke check missing expectedProofCount in latest pointer");
        }
        if (receiptExpectedCount == null || receiptExpectedCount.isBlank()) {
            throw new RuntimeException("bundle smoke check missing expectedProofCount in receipt");
        }
        assertSharedBundleValue(
                "expectedProofCount",
                summaryExpectedCount,
                indexProofCount,
                latestExpectedCount,
                receiptExpectedCount);

        String latestManifestFile = extractJsonStringValue(latestJson, "manifestFile");
        String latestSummaryFile = extractJsonStringValue(latestJson, "summaryFile");
        String latestIndexFile = extractJsonStringValue(latestJson, "indexFile");
        if (!"run_manifest.json".equals(latestManifestFile)) {
            throw new RuntimeException("bundle smoke check latest pointer manifestFile drifted: " + latestManifestFile);
        }
        if (!"proof_summary.json".equals(latestSummaryFile)) {
            throw new RuntimeException("bundle smoke check latest pointer summaryFile drifted: " + latestSummaryFile);
        }
        if (!"proof_index.json".equals(latestIndexFile)) {
            throw new RuntimeException("bundle smoke check latest pointer indexFile drifted: " + latestIndexFile);
        }

        String manifestProofCount = Integer.toString(countOccurrences(manifestJson, "\"proofId\": \""));
        if (!Integer.toString(LOWERED_SIDE_SLAB_EXPECTED_PROOF_IDS.size()).equals(manifestProofCount)) {
            throw new RuntimeException(
                    "bundle smoke check manifest proof count drifted: "
                    + manifestProofCount
                    + " expected="
                    + LOWERED_SIDE_SLAB_EXPECTED_PROOF_IDS.size());
        }

        for (String proofId : LOWERED_SIDE_SLAB_EXPECTED_PROOF_IDS) {
            if (!receiptText.contains(proofId)) {
                throw new RuntimeException("bundle smoke check receipt missing proof row: " + proofId);
            }
        }
    }

    static void assertLoweredSideSlabProofArtifacts(Path screenshotDir) {
        Path manifestPath = screenshotDir.resolve("run_manifest.json");
        if (!Files.isRegularFile(manifestPath)) {
            throw new RuntimeException(
                    "lowered-side-slab proof manifest missing: " + manifestPath.toAbsolutePath());
        }

        final String manifestJson;
        try {
            manifestJson = Files.readString(manifestPath);
        } catch (IOException e) {
            throw new RuntimeException(
                    "failed to read lowered-side-slab proof manifest: "
                    + manifestPath.toAbsolutePath(), e);
        }

        List<String> missingNotes = new ArrayList<>();
        List<String> missingPrimaryScreenshots = new ArrayList<>();
        List<String> missingManifestProofIds = new ArrayList<>();
        List<String> missingProvenanceKeys = new ArrayList<>();
        for (String key : List.of("generatedAtUtc", "gitHeadShort", "gitBranch", "gitTagsAtHead")) {
            if (!manifestJson.contains("\"" + key + "\"")) {
                missingProvenanceKeys.add(key);
            }
        }
        for (String proofId : LOWERED_SIDE_SLAB_EXPECTED_PROOF_IDS) {
            Path notesPath = screenshotDir.resolve(proofId + "_notes.json");
            if (!Files.isRegularFile(notesPath)) {
                missingNotes.add(notesPath.getFileName().toString());
            }
            String primaryScreenshotFile = resolveScreenshotFileNameForProofId(screenshotDir, proofId);
            if (primaryScreenshotFile == null
                    || !manifestJson.contains("\"primaryScreenshotFile\": \"" + primaryScreenshotFile + "\"")) {
                missingPrimaryScreenshots.add(proofId);
            }
            if (countOccurrences(manifestJson, "\"proofId\": \"" + proofId + "\"") != 1) {
                missingManifestProofIds.add(proofId);
            }
        }

        if (!missingNotes.isEmpty()
                || !missingPrimaryScreenshots.isEmpty()
                || !missingManifestProofIds.isEmpty()
                || !missingProvenanceKeys.isEmpty()) {
            StringBuilder message = new StringBuilder("lowered-side-slab proof ladder is incomplete");
            if (!missingNotes.isEmpty()) {
                message.append("; missing notes: ").append(missingNotes);
            }
            if (!missingPrimaryScreenshots.isEmpty()) {
                message.append("; missing primary screenshots: ").append(missingPrimaryScreenshots);
            }
            if (!missingManifestProofIds.isEmpty()) {
                message.append("; missing manifest proof ids: ").append(missingManifestProofIds);
            }
            if (!missingProvenanceKeys.isEmpty()) {
                message.append("; missing provenance keys: ").append(missingProvenanceKeys);
            }
            throw new RuntimeException(message.toString());
        }
    }

    static String buildManifestJson(
            Path screenshotDir,
            List<ManifestArtifact> artifacts,
            RunProvenance provenance,
            List<ProofManifestEntry> proofEntries
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"generatedAtUtc\": \"")
                .append(escapeJson(provenance.generatedAtUtc()))
                .append("\",\n");
        sb.append("  \"gitHeadShort\": \"")
                .append(escapeJson(provenance.gitHeadShort()))
                .append("\",\n");
        sb.append("  \"gitBranch\": \"")
                .append(escapeJson(provenance.gitBranch()))
                .append("\",\n");
        appendStringArrayField(sb, "gitTagsAtHead", provenance.gitTagsAtHead());
        sb.append("  \"scenario\": \"SlabbedLabClientGameTest.runTest\",\n");
        sb.append("  \"outputDirectory\": \"")
                .append(escapeJson(screenshotDir.toAbsolutePath().toString()))
                .append("\",\n");
        appendProofsJsonArray(sb, proofEntries);
        sb.append(",\n");
        appendArtifactsJsonArray(sb, artifacts);
        sb.append("\n}\n");
        return sb.toString();
    }

    static String buildProofSummaryJson(
            RunProvenance provenance,
            List<ProofManifestEntry> proofEntries
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"generatedAtUtc\": \"")
                .append(escapeJson(provenance.generatedAtUtc()))
                .append("\",\n");
        sb.append("  \"gitHeadShort\": \"")
                .append(escapeJson(provenance.gitHeadShort()))
                .append("\",\n");
        sb.append("  \"gitBranch\": \"")
                .append(escapeJson(provenance.gitBranch()))
                .append("\",\n");
        appendStringArrayField(sb, "gitTagsAtHead", provenance.gitTagsAtHead());
        sb.append("  \"overallStatus\": \"PASS\",\n");
        sb.append("  \"expectedProofCount\": ").append(LOWERED_SIDE_SLAB_EXPECTED_PROOF_IDS.size()).append(",\n");
        sb.append("  \"actualProofCount\": ").append(proofEntries.size()).append(",\n");
        sb.append("  \"proofs\": [");
        for (int i = 0; i < proofEntries.size(); i++) {
            ProofManifestEntry proof = proofEntries.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("{\"proofId\": \"")
                    .append(escapeJson(proof.proofId()))
                    .append("\", \"label\": \"")
                    .append(escapeJson(proof.label()))
                    .append("\", \"status\": \"PASS\", \"notesFile\": \"")
                    .append(escapeJson(proof.notesFile()))
                    .append("\", \"primaryScreenshotFile\": \"")
                    .append(escapeJson(proof.primaryScreenshotFile()))
                    .append("\"}");
        }
        sb.append("]\n");
        sb.append("}\n");
        return sb.toString();
    }

    static String buildProofIndexJson(
            RunProvenance provenance,
            List<ProofManifestEntry> proofEntries
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"generatedAtUtc\": \"")
                .append(escapeJson(provenance.generatedAtUtc()))
                .append("\",\n");
        sb.append("  \"gitHeadShort\": \"")
                .append(escapeJson(provenance.gitHeadShort()))
                .append("\",\n");
        sb.append("  \"gitBranch\": \"")
                .append(escapeJson(provenance.gitBranch()))
                .append("\",\n");
        appendStringArrayField(sb, "gitTagsAtHead", provenance.gitTagsAtHead());
        sb.append("  \"manifestFile\": \"run_manifest.json\",\n");
        sb.append("  \"summaryFile\": \"proof_summary.json\",\n");
        sb.append("  \"proofCount\": ").append(proofEntries.size()).append(",\n");
        sb.append("  \"proofs\": [");
        for (int i = 0; i < proofEntries.size(); i++) {
            ProofManifestEntry proof = proofEntries.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("{\"proofId\": \"")
                    .append(escapeJson(proof.proofId()))
                    .append("\", \"label\": \"")
                    .append(escapeJson(proof.label()))
                    .append("\", \"notesFile\": \"")
                    .append(escapeJson(proof.notesFile()))
                    .append("\", \"primaryScreenshotFile\": \"")
                    .append(escapeJson(proof.primaryScreenshotFile()))
                    .append("\"}");
        }
        sb.append("]\n");
        sb.append("}\n");
        return sb.toString();
    }

    static String buildLatestProofRunJson(
            RunProvenance provenance,
            int expectedProofCount,
            String overallStatus
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"generatedAtUtc\": \"")
                .append(escapeJson(provenance.generatedAtUtc()))
                .append("\",\n");
        sb.append("  \"gitHeadShort\": \"")
                .append(escapeJson(provenance.gitHeadShort()))
                .append("\",\n");
        sb.append("  \"gitBranch\": \"")
                .append(escapeJson(provenance.gitBranch()))
                .append("\",\n");
        appendStringArrayField(sb, "gitTagsAtHead", provenance.gitTagsAtHead());
        sb.append("  \"manifestFile\": \"run_manifest.json\",\n");
        sb.append("  \"summaryFile\": \"proof_summary.json\",\n");
        sb.append("  \"indexFile\": \"proof_index.json\",\n");
        sb.append("  \"expectedProofCount\": ").append(expectedProofCount).append(",\n");
        sb.append("  \"overallStatus\": \"")
                .append(escapeJson(overallStatus))
                .append("\"\n");
        sb.append("}\n");
        return sb.toString();
    }

    static String buildProofReceiptMarkdown(
            RunProvenance provenance,
            List<ProofManifestEntry> proofEntries,
            String overallStatus,
            String latestOverallStatus
    ) {
        if (!overallStatus.equals(latestOverallStatus)) {
            throw new RuntimeException(
                    "proof receipt status mismatch between summary and latest pointer: "
                    + overallStatus + " vs " + latestOverallStatus);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Lowered-Side-Slab Proof Ladder Receipt\n\n");
        sb.append("- generatedAtUtc: ").append(escapeMarkdownCell(provenance.generatedAtUtc())).append("\n");
        sb.append("- gitHeadShort: ").append(escapeMarkdownCell(provenance.gitHeadShort())).append("\n");
        sb.append("- gitBranch: ").append(escapeMarkdownCell(provenance.gitBranch())).append("\n");
        sb.append("- gitTagsAtHead: ").append(escapeMarkdownCell(formatMarkdownTags(provenance.gitTagsAtHead()))).append("\n");
        sb.append("- overallStatus: ").append(escapeMarkdownCell(overallStatus)).append("\n");
        sb.append("- expectedProofCount: ").append(proofEntries.size()).append("\n\n");
        sb.append("| proofId | label | status | notesFile | primaryScreenshotFile |\n");
        sb.append("| --- | --- | --- | --- | --- |\n");
        for (ProofManifestEntry proof : proofEntries) {
            sb.append("| ")
                    .append(escapeMarkdownCell(proof.proofId()))
                    .append(" | ")
                    .append(escapeMarkdownCell(proof.label()))
                    .append(" | PASS | ")
                    .append(escapeMarkdownCell(proof.notesFile()))
                    .append(" | ")
                    .append(escapeMarkdownCell(proof.primaryScreenshotFile()))
                    .append(" |\n");
        }
        return sb.toString();
    }

    static void appendStringArrayField(StringBuilder sb, String key, List<String> values) {
        sb.append("  \"")
                .append(escapeJson(key))
                .append("\": [");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("\"").append(escapeJson(values.get(i))).append("\"");
        }
        sb.append("],\n");
    }

    static void appendArtifactsJsonArray(StringBuilder sb, List<ManifestArtifact> artifacts) {
        List<ManifestArtifact> normalized = new ArrayList<>();
        for (ManifestArtifact artifact : artifacts) {
            if (artifact.file() != null && !artifact.file().isBlank()) {
                normalized.add(artifact);
            }
        }
        normalized.sort((a, b) -> {
            int proofCmp = a.proofId().compareTo(b.proofId());
            if (proofCmp != 0) {
                return proofCmp;
            }
            return a.file().compareTo(b.file());
        });

        LinkedHashSet<ManifestArtifact> uniqueArtifacts = new LinkedHashSet<>(normalized);
        sb.append("  \"artifacts\": [");
        int i = 0;
        for (ManifestArtifact artifact : uniqueArtifacts) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("{\"file\": \"")
                    .append(escapeJson(artifact.file()))
                    .append("\", \"label\": \"")
                    .append(escapeJson(artifact.label()))
                    .append("\"}");
            i++;
        }
        sb.append("]");
    }

    static void appendProofsJsonArray(StringBuilder sb, List<ProofManifestEntry> proofs) {
        sb.append("  \"proofs\": [");
        for (int i = 0; i < proofs.size(); i++) {
            ProofManifestEntry proof = proofs.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("{\"proofId\": \"")
                    .append(escapeJson(proof.proofId()))
                    .append("\", \"label\": \"")
                    .append(escapeJson(proof.label()))
                    .append("\", \"notesFile\": \"")
                    .append(escapeJson(proof.notesFile()))
                    .append("\", \"primaryScreenshotFile\": \"")
                    .append(escapeJson(proof.primaryScreenshotFile()))
                    .append("\"}");
        }
        sb.append("]");
    }

    static RunProvenance readRunProvenance() {
        String generatedAtUtc = Instant.now().toString();
        String gitHeadShort = normalizeGitField(runGitCommand("rev-parse", "--short", "HEAD"));
        String gitBranch = normalizeGitField(runGitCommand("rev-parse", "--abbrev-ref", "HEAD"));
        List<String> gitTagsAtHead = readGitTagsAtHead();
        if (gitTagsAtHead.isEmpty()) {
            gitTagsAtHead = List.of();
        }
        return new RunProvenance(generatedAtUtc, gitHeadShort, gitBranch, gitTagsAtHead);
    }

    static List<String> readGitTagsAtHead() {
        String output = runGitCommand("tag", "--points-at", "HEAD");
        if (output == null) {
            return List.of("unknown");
        }
        List<String> tags = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) {
                tags.add(line.trim());
            }
        }
        tags.sort(String::compareTo);
        return tags;
    }

    static String normalizeGitField(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim();
    }

    static String runGitCommand(String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command(buildGitCommand(args));
            pb.directory(Path.of(System.getProperty("user.dir", ".")).toFile());
            Process process = pb.start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            if (exit != 0) {
                return null;
            }
            if (!stderr.isBlank()) {
                // Git can emit diagnostics to stderr even on success in some environments,
                // but we only care about the command output. Keep the data path simple.
            }
            return stdout.trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    static List<String> buildGitCommand(String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        for (String arg : args) {
            command.add(arg);
        }
        return command;
    }

    static List<ProofManifestEntry> buildLoweredSideSlabProofEntries(Path screenshotDir) {
        LinkedHashMap<String, ProofManifestEntry> entries = new LinkedHashMap<>();
        for (String proofId : LOWERED_SIDE_SLAB_EXPECTED_PROOF_IDS) {
            if (entries.containsKey(proofId)) {
                throw new RuntimeException(
                        "duplicate proofId in lowered-side-slab manifest ladder: " + proofId);
            }

            String notesFile = proofId + "_notes.json";
            Path notesPath = screenshotDir.resolve(notesFile);
            if (!Files.isRegularFile(notesPath)) {
                throw new RuntimeException(
                        "missing notes artifact for proofId " + proofId
                        + ": " + notesPath.toAbsolutePath());
            }

            String primaryScreenshotFile = resolveScreenshotFileNameForProofId(screenshotDir, proofId);
            if (primaryScreenshotFile == null) {
                throw new RuntimeException(
                        "missing primary screenshot artifact for proofId " + proofId
                        + " in " + screenshotDir.toAbsolutePath());
            }

            entries.put(
                    proofId,
                    new ProofManifestEntry(
                            proofId,
                            labelForProofId(proofId),
                            notesFile,
                            primaryScreenshotFile));
        }
        return new ArrayList<>(entries.values());
    }

    static String escapeJson(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static int countOccurrences(String haystack, String needle) {
        if (haystack == null || haystack.isEmpty() || needle == null || needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static String extractJsonStringValue(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyIndex = json.indexOf(needle);
        if (keyIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', keyIndex + needle.length());
        if (colonIndex < 0) {
            return null;
        }
        int firstQuoteIndex = json.indexOf('"', colonIndex + 1);
        if (firstQuoteIndex < 0) {
            return null;
        }
        int secondQuoteIndex = json.indexOf('"', firstQuoteIndex + 1);
        if (secondQuoteIndex < 0) {
            return null;
        }
        return json.substring(firstQuoteIndex + 1, secondQuoteIndex);
    }

    static String extractMarkdownLineValue(String markdown, String prefix) {
        if (markdown == null || prefix == null) {
            return null;
        }
        for (String line : markdown.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    static String extractJsonNumericValue(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyIndex = json.indexOf(needle);
        if (keyIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', keyIndex + needle.length());
        if (colonIndex < 0) {
            return null;
        }
        int cursor = colonIndex + 1;
        while (cursor < json.length() && Character.isWhitespace(json.charAt(cursor))) {
            cursor++;
        }
        int end = cursor;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (!(Character.isDigit(c) || c == '-' || c == '+')) {
                break;
            }
            end++;
        }
        if (end == cursor) {
            return null;
        }
        return json.substring(cursor, end);
    }

    static void assertSharedBundleValue(String field, String... values) {
        String canonical = null;
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new RuntimeException("bundle smoke check missing " + field);
            }
            if (canonical == null) {
                canonical = value.trim();
            } else if (!canonical.equals(value.trim())) {
                throw new RuntimeException(
                        "bundle smoke check mismatch for " + field + ": " + java.util.Arrays.toString(values));
            }
        }
    }

    static String escapeMarkdownCell(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
    }

    static String formatMarkdownTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "[]";
        }
        return "[" + String.join(", ", tags) + "]";
    }

    static record NoteField(String key, String value) {
    }

    static record RunProvenance(
            String generatedAtUtc,
            String gitHeadShort,
            String gitBranch,
            List<String> gitTagsAtHead
    ) {
    }

    // -------------------------------------------------------------------------
    // Live placement repro: compares Camera-A (proof screenshot angle) and
    // Camera-B (east-side, vanilla hitbox) raycasts against the synthetic hit
    // result used by the existing proof, to expose the live/proof divergence.
    // This method does NOT throw; it records findings in a JSON audit note.
    // -------------------------------------------------------------------------
    static void runLoweredSidePlacementLiveRepro(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts) {

        final BlockPos supportPos = FIXTURE_ORIGIN.add(8, 0, 0);
        final BlockPos fullPos    = supportPos.up();
        final BlockPos placePos   = fullPos.east();

        // Synthetic hit from existing proof: Y is below the vanilla block floor,
        // targeting the lowered visual space. The proof constructs this by hand and
        // calls interactBlock directly — the live crosshair never produces this hit.
        final double syntheticHitY = fullPos.getY() - 0.25;
        final BlockHitResult syntheticHit = new BlockHitResult(
                new Vec3d(fullPos.getX() + 1.0, syntheticHitY, fullPos.getZ() + 0.5),
                Direction.EAST, fullPos, false, false);

        // Camera-A: exact camera used for proof screenshots (yaw=180, pitch=24).
        // This looks north from the south side — it cannot physically aim at the
        // east face of the full block. Screenshot only; the proof's click is separate.
        final double camAFeetX = fullPos.getX() + 0.5;
        final double camAFeetY = fullPos.getY() + 1.95;
        final double camAFeetZ = fullPos.getZ() + 3.25;
        final float  camAYaw   = 180.0f;
        final float  camAPitch = 24.0f;
        // Eye offset for a standing player (~1.62 in vanilla survival).
        final double eyeOffset = 1.62;
        final Vec3d camAEye = new Vec3d(camAFeetX, camAFeetY + eyeOffset, camAFeetZ);
        // Direction for yaw=180, pitch=24: straight north, slightly down.
        final Vec3d camADir = new Vec3d(
                -Math.sin(Math.toRadians(camAYaw))  * Math.cos(Math.toRadians(camAPitch)),
                -Math.sin(Math.toRadians(camAPitch)),
                 Math.cos(Math.toRadians(camAYaw))  * Math.cos(Math.toRadians(camAPitch)));

        // Camera-B: positioned east of block, looking west (yaw=90, pitch=0).
        // Eye at vanilla midpoint Y+0.5 — this is what a player east of the block
        // would realistically use to target the east face.
        final double camBEyeX  = fullPos.getX() + 2.5;
        final double camBEyeY  = fullPos.getY() + 0.5;   // vanilla midpoint of east face
        final double camBEyeZ  = fullPos.getZ() + 0.5;
        final double camBFeetY = camBEyeY - eyeOffset;
        final float  camBYaw   = 90.0f;
        final float  camBPitch = 0.0f;
        final Vec3d camBEye = new Vec3d(camBEyeX, camBEyeY, camBEyeZ);
        final Vec3d camBDir = new Vec3d(-1.0, 0.0, 0.0); // purely west

        // Natural south-facing approach used to probe the live feel path that the
        // existing synthetic proof did not exercise.
        final double southEyeX = fullPos.getX() + 0.5;
        final double southEyeY = fullPos.getY() + 1.95;
        final double southEyeZ = fullPos.getZ() + 3.25;
        final float southYaw = 180.0f;
        final float southPitch = 16.0f;
        final Vec3d southEye = new Vec3d(southEyeX, southEyeY + eyeOffset, southEyeZ);
        final Vec3d southDir = new Vec3d(0.0, 0.0, -1.0);
        final BlockPos southPlacePos = fullPos.south();

        // Eye-height boundary probes: one that should comfortably intersect the
        // lowered visual space and one that mimics a natural horizontal aim.
        final double probeMidEyeY = fullPos.getY() + 1.15;
        final double probeNaturalEyeY = fullPos.getY() + 1.95;
        final Vec3d probeMidEye = new Vec3d(fullPos.getX() + 0.5, probeMidEyeY, fullPos.getZ() + 3.25);
        final Vec3d probeNaturalEye = new Vec3d(fullPos.getX() + 0.5, probeNaturalEyeY, fullPos.getZ() + 3.25);
        final Vec3d probeDir = new Vec3d(0.0, 0.0, -1.0);

        AtomicReference<String> cameraAHitDesc  = new AtomicReference<>("pending");
        AtomicReference<String> cameraBHitDesc  = new AtomicReference<>("pending");
        AtomicReference<BlockHitResult> cameraBHit = new AtomicReference<>(null);
        AtomicReference<String> clickResultStr  = new AtomicReference<>("not_run");
        AtomicReference<String> placedStateStr  = new AtomicReference<>("not_checked");
        AtomicReference<String> auditVerdict    = new AtomicReference<>("INDETERMINATE");

        AtomicReference<String> southHitDesc = new AtomicReference<>("pending");
        AtomicReference<BlockHitResult> southHit = new AtomicReference<>(null);
        AtomicReference<String> southFirstClickResult = new AtomicReference<>("not_run");
        AtomicReference<String> southFirstPlacedState = new AtomicReference<>("not_checked");
        AtomicReference<String> southSecondClickResult = new AtomicReference<>("not_run");
        AtomicReference<String> southSecondPlacedState = new AtomicReference<>("not_checked");
        AtomicReference<String> probeMidHitDesc = new AtomicReference<>("pending");
        AtomicReference<String> probeNaturalHitDesc = new AtomicReference<>("pending");
        List<String[]> southPitchAuditRows = new ArrayList<>();

        // World setup — same scenario as existing proof
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                server.getPlayerManager().getPlayerList().get(0)
                        .setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            }
        });

        // --- Camera-A screenshot (proof screenshot angle) ---
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.refreshPositionAndAngles(camAFeetX, camAFeetY, camAFeetZ, camAYaw, camAPitch);
                mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        captureScreenshotAndRecord(ctx, "live_repro_side_slab_camera_a",
                screenshotDir, knownScreenshotFiles, artifacts);

        // Explicit raycast from Camera-A eye position
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                cameraAHitDesc.set("null_world_or_player");
                return;
            }
            Vec3d end = camAEye.add(camADir.normalize().multiply(4.5));
            BlockHitResult hit = mc.world.raycast(new RaycastContext(
                    camAEye, end,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) {
                cameraAHitDesc.set("MISS (no block within 4.5 reach)");
            } else {
                cameraAHitDesc.set("BLOCK blockPos=" + hit.getBlockPos().toShortString()
                        + " face=" + hit.getSide().asString()
                        + " hitY=" + String.format("%.4f", hit.getPos().y));
            }
        });

        // --- Camera-B screenshot (east-side, vanilla hitbox angle) ---
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.refreshPositionAndAngles(
                        camBEyeX, camBFeetY, camBEyeZ, camBYaw, camBPitch);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        captureScreenshotAndRecord(ctx, "live_repro_side_slab_camera_b",
                screenshotDir, knownScreenshotFiles, artifacts);

        // Explicit raycast from Camera-B eye position
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                cameraBHitDesc.set("null_world_or_player");
                return;
            }
            Vec3d end = camBEye.add(camBDir.multiply(4.5));
            BlockHitResult hit = mc.world.raycast(new RaycastContext(
                    camBEye, end,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) {
                cameraBHitDesc.set("MISS");
            } else {
                cameraBHitDesc.set("BLOCK blockPos=" + hit.getBlockPos().toShortString()
                        + " face=" + hit.getSide().asString()
                        + " hitY=" + String.format("%.4f", hit.getPos().y));
                if (hit.getType() == HitResult.Type.BLOCK) {
                    cameraBHit.set(hit);
                }
            }
        });

        // --- Click using Camera-B live hit (or synthetic fallback) ---
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) return;
            BlockHitResult hitToUse = cameraBHit.get() != null ? cameraBHit.get() : syntheticHit;
            String hitSource = cameraBHit.get() != null ? "live_camera_b" : "synthetic_fallback";
            ActionResult result = mc.interactionManager.interactBlock(
                    mc.player, Hand.MAIN_HAND, hitToUse);
            clickResultStr.set(result + " [source=" + hitSource + "]");
            placedStateStr.set(mc.world.getBlockState(placePos).toString());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        captureScreenshotAndRecord(ctx, "live_repro_side_slab_after_click",
                screenshotDir, knownScreenshotFiles, artifacts);

        // --- Natural south-facing approach screenshot + raycast + click ---
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.refreshPositionAndAngles(southEyeX, southEyeY, southEyeZ, southYaw, southPitch);
                mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        captureScreenshotAndRecord(ctx, "natural_south_face_after_click",
                screenshotDir, knownScreenshotFiles, artifacts);

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                southHitDesc.set("null_world_or_player");
                return;
            }
            Vec3d end = southEye.add(southDir.multiply(4.5));
            BlockHitResult hit = mc.world.raycast(new RaycastContext(
                    southEye, end,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) {
                southHitDesc.set("MISS");
            } else {
                southHitDesc.set("BLOCK blockPos=" + hit.getBlockPos().toShortString()
                        + " face=" + hit.getSide().asString()
                        + " hitY=" + String.format("%.4f", hit.getPos().y));
                southHit.set(hit);
            }
        });

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                return;
            }
            BlockHitResult hitToUse = southHit.get();
            if (hitToUse == null) {
                southFirstClickResult.set("MISS_NO_CLICK");
                southFirstPlacedState.set(mc.world.getBlockState(southPlacePos).toString());
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitToUse);
            southFirstClickResult.set(result.toString());
            southFirstPlacedState.set(mc.world.getBlockState(southPlacePos).toString());
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        captureScreenshotAndRecord(ctx, "natural_south_repeat_after_click",
                screenshotDir, knownScreenshotFiles, artifacts);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                return;
            }
            BlockHitResult hitToUse = southHit.get();
            if (hitToUse == null) {
                southSecondClickResult.set("MISS_NO_REPEAT_CLICK");
                southSecondPlacedState.set(mc.world.getBlockState(southPlacePos).toString());
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitToUse);
            southSecondClickResult.set(result.toString());
            southSecondPlacedState.set(mc.world.getBlockState(southPlacePos).toString());
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                probeMidHitDesc.set("null_world_or_player");
                probeNaturalHitDesc.set("null_world_or_player");
                return;
            }

            Vec3d midEnd = probeMidEye.add(probeDir.multiply(4.5));
            BlockHitResult midHit = mc.world.raycast(new RaycastContext(
                    probeMidEye, midEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, mc.player));
            if (midHit.getType() == HitResult.Type.MISS) {
                probeMidHitDesc.set("MISS");
            } else {
                probeMidHitDesc.set("BLOCK blockPos=" + midHit.getBlockPos().toShortString()
                        + " face=" + midHit.getSide().asString()
                        + " hitY=" + String.format("%.4f", midHit.getPos().y));
            }

            Vec3d naturalEnd = probeNaturalEye.add(probeDir.multiply(4.5));
            BlockHitResult naturalHit = mc.world.raycast(new RaycastContext(
                    probeNaturalEye, naturalEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, mc.player));
            if (naturalHit.getType() == HitResult.Type.MISS) {
                probeNaturalHitDesc.set("MISS");
            } else {
                probeNaturalHitDesc.set("BLOCK blockPos=" + naturalHit.getBlockPos().toShortString()
                        + " face=" + naturalHit.getSide().asString()
                        + " hitY=" + String.format("%.4f", naturalHit.getPos().y));
            }
        });

        captureScreenshotAndRecord(ctx, "eye_height_offset_hit",
                screenshotDir, knownScreenshotFiles, artifacts);
        captureScreenshotAndRecord(ctx, "eye_height_natural_horizontal",
                screenshotDir, knownScreenshotFiles, artifacts);

        writeLoweredSideNaturalAngleAuditNotes(
                screenshotDir,
                supportPos,
                fullPos,
                placePos,
                southPlacePos,
                cameraBHit.get(),
                cameraBHitDesc.get(),
                clickResultStr.get(),
                placedStateStr.get(),
                southHit.get(),
                southHitDesc.get(),
                southFirstClickResult.get(),
                southFirstPlacedState.get(),
                southSecondClickResult.get(),
                southSecondPlacedState.get(),
                probeMidHitDesc.get(),
                probeNaturalHitDesc.get(),
                artifacts);

        final float[] southPitchSweep = new float[] {0.0f, 18.0f, 42.0f, 52.0f};
        final String[] southPitchLabels = new String[] {
                "south_pitch_horizontal",
                "south_pitch_slight_down",
                "south_pitch_center_down",
                "south_pitch_clear_down"};

        for (int i = 0; i < southPitchSweep.length; i++) {
            final float sweepPitch = southPitchSweep[i];
            final String sweepLabel = southPitchLabels[i];
            final Vec3d sweepEye = southEye;
            final Vec3d sweepDir = lookDirection(southYaw, sweepPitch);
            AtomicReference<String> sweepHitDesc = new AtomicReference<>("pending");
            AtomicReference<BlockHitResult> sweepHit = new AtomicReference<>(null);
            AtomicReference<String> sweepClickResult = new AtomicReference<>("not_run");
            AtomicReference<String> sweepPlacedState = new AtomicReference<>("not_checked");

            singleplayer.getServer().runOnServer(server -> {
                var world = server.getOverworld();
                world.setBlockState(supportPos,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        Block.NOTIFY_LISTENERS);
                world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(southPlacePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(southPlacePos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                    server.getPlayerManager().getPlayerList().get(0)
                            .setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
                }
            });

            ctx.runOnClient(mc -> {
                if (mc.player != null) {
                    mc.player.refreshPositionAndAngles(southEyeX, southEyeY, southEyeZ, southYaw, sweepPitch);
                    mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
                }
            });
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            ctx.runOnClient(mc -> {
                if (mc.world == null || mc.player == null) {
                    sweepHitDesc.set("null_world_or_player");
                    return;
                }
                Vec3d end = sweepEye.add(sweepDir.normalize().multiply(4.5));
                BlockHitResult hit = mc.world.raycast(new RaycastContext(
                        sweepEye, end,
                        RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE, mc.player));
                if (hit.getType() == HitResult.Type.MISS) {
                    sweepHitDesc.set("MISS");
                } else {
                    sweepHitDesc.set("BLOCK blockPos=" + hit.getBlockPos().toShortString()
                            + " face=" + hit.getSide().asString()
                            + " hitY=" + String.format("%.4f", hit.getPos().y));
                    sweepHit.set(hit);
                }
            });

            ctx.runOnClient(mc -> {
                if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                    sweepClickResult.set("NOT_RUN");
                    sweepPlacedState.set("not_checked");
                    return;
                }
                BlockHitResult hitToUse = sweepHit.get();
                if (hitToUse == null) {
                    sweepClickResult.set("MISS_NO_CLICK");
                    sweepPlacedState.set(mc.world.getBlockState(southPlacePos).toString());
                    return;
                }
                ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitToUse);
                sweepClickResult.set(result.toString());
                sweepPlacedState.set(mc.world.getBlockState(southPlacePos).toString());
            });

            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            captureScreenshotAndRecord(ctx, sweepLabel, screenshotDir, knownScreenshotFiles, artifacts);

            String verdict;
            if (sweepHit.get() == null) {
                verdict = "MISS";
            } else if (sweepPlacedState.get() != null
                    && sweepPlacedState.get().contains("stone_slab")
                    && sweepPlacedState.get().contains("type=bottom")) {
                verdict = "PASS";
            } else {
                verdict = "FAIL";
            }

            southPitchAuditRows.add(new String[] {
                    sweepLabel,
                    Float.toString(sweepPitch),
                    String.format("%.4f,%.4f,%.4f", sweepDir.x, sweepDir.y, sweepDir.z),
                    sweepHitDesc.get(),
                    sweepHit.get() == null ? "null" : sweepHit.get().getBlockPos().toShortString(),
                    sweepHit.get() == null ? "null" : sweepHit.get().getSide().asString(),
                    sweepHit.get() == null ? "null" : String.format("%.4f", sweepHit.get().getPos().y),
                    southPlacePos.toShortString(),
                    sweepClickResult.get(),
                    sweepPlacedState.get(),
                    verdict,
                    resolveScreenshotFileNameForProofId(screenshotDir, sweepLabel)
            });
        }

        writeLoweredSideSouthPitchAuditNotes(
                screenshotDir,
                supportPos,
                fullPos,
                southPlacePos,
                southEye,
                southYaw,
                southPitchAuditRows,
                artifacts);

        // --- Verdict ---
        ctx.runOnClient(mc -> {
            BlockHitResult bHit = cameraBHit.get();
            String aDesc = cameraAHitDesc.get();
            boolean aMisses = aDesc.startsWith("MISS");

            if (aMisses && bHit != null) {
                double liveY = bHit.getPos().y;
                double diff  = liveY - syntheticHitY;
                auditVerdict.set(String.format(
                        "REPRO CONFIRMED:"
                        + " Camera-A (proof screenshot angle) = MISS — proof camera cannot hit east face;"
                        + " Camera-B live raycast hitY=%.4f vs synthetic hitY=%.4f (diff=+%.4f);"
                        + " live hit is in vanilla block space [blockY, blockY+1];"
                        + " synthetic hit is below vanilla floor (lowered visual space);"
                        + " root cause: proof bypasses live raycast with a synthetic hit the player cannot reproduce;"
                        + " real hitbox is vanilla, visual is lowered by 0.5 — no hitbox offset applied.",
                        liveY, syntheticHitY, diff));
            } else if (aMisses) {
                auditVerdict.set(
                        "PARTIAL: Camera-A = MISS (confirms proof camera decoupled from click);"
                        + " Camera-B = null/MISS;"
                        + " live hit path unverified; check if block was set up correctly or reach was too short.");
            } else {
                auditVerdict.set(
                        "UNEXPECTED: Camera-A hit something (" + aDesc + ");"
                        + " Camera-B=" + cameraBHitDesc.get()
                        + "; further analysis needed.");
            }
        });

        writeLiveSidePlacementAuditNotes(screenshotDir, supportPos, fullPos, placePos,
                syntheticHitY, cameraAHitDesc.get(), cameraBHitDesc.get(),
                clickResultStr.get(), placedStateStr.get(), auditVerdict.get(), artifacts);
    }

    private static Vec3d lookDirection(float yaw, float pitch) {
        return new Vec3d(
                -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)),
                -Math.sin(Math.toRadians(pitch)),
                 Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
    }

    private static void writeLoweredSideNaturalAngleAuditNotes(
            Path screenshotDir,
            BlockPos supportPos,
            BlockPos fullPos,
            BlockPos eastPlacePos,
            BlockPos southPlacePos,
            BlockHitResult cameraBHit,
            String cameraBDesc,
            String cameraBClickResult,
            String cameraBPlacedState,
            BlockHitResult southHit,
            String southHitDesc,
            String southFirstClickResult,
            String southFirstPlacedState,
            String southSecondClickResult,
            String southSecondPlacedState,
            String probeMidHitDesc,
            String probeNaturalHitDesc,
            List<ManifestArtifact> artifacts) {
        try {
            Files.createDirectories(screenshotDir);
            Path notesPath = screenshotDir.resolve("lowered_side_natural_angle_audit.json");
            String cameraBHitFace = cameraBHit == null ? "null" : cameraBHit.getSide().asString();
            String cameraBHitPos = cameraBHit == null ? "null" : cameraBHit.getBlockPos().toShortString();
            String cameraBHitY = cameraBHit == null ? "null" : String.format("%.4f", cameraBHit.getPos().y);
            String southHitFace = southHit == null ? "null" : southHit.getSide().asString();
            String southHitPos = southHit == null ? "null" : southHit.getBlockPos().toShortString();
            String southHitY = southHit == null ? "null" : String.format("%.4f", southHit.getPos().y);

            String cameraBVerdict;
            if (cameraBHit != null && cameraBDesc.startsWith("BLOCK")) {
                cameraBVerdict = "PASS";
            } else if (cameraBHit == null) {
                cameraBVerdict = "MISS";
            } else {
                cameraBVerdict = "FAIL";
            }

            String southVerdict;
            if (southHit == null) {
                southVerdict = "MISS";
            } else if (southFirstPlacedState != null && southFirstPlacedState.contains("stone_slab")) {
                southVerdict = southFirstPlacedState.contains("type=bottom") || southFirstPlacedState.contains("type=double")
                        ? "PASS"
                        : "FAIL";
            } else {
                southVerdict = "FAIL";
            }

            String midVerdict = probeMidHitDesc.startsWith("BLOCK") ? "PASS" : "FAIL";
            String naturalVerdict = probeNaturalHitDesc.startsWith("BLOCK") ? "PASS" : "MISS";
            String repeatVerdict = southSecondPlacedState != null && southSecondPlacedState.contains("type=double")
                    ? "PASS"
                    : "FAIL";

            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"testId\": \"lowered_side_natural_angle_audit\",\n");
            sb.append("  \"supportPos\": \"").append(escapeJson(supportPos.toShortString())).append("\",\n");
            sb.append("  \"fullBlockPos\": \"").append(escapeJson(fullPos.toShortString())).append("\",\n");
            sb.append("  \"eastPlacementPos\": \"").append(escapeJson(eastPlacePos.toShortString())).append("\",\n");
            sb.append("  \"southPlacementPos\": \"").append(escapeJson(southPlacePos.toShortString())).append("\",\n");
            sb.append("  \"conclusion\": {\n");
            sb.append("    \"eastFacePath\": \"").append(escapeJson(cameraBVerdict)).append("\",\n");
            sb.append("    \"southFacePath\": \"").append(escapeJson(southVerdict)).append("\",\n");
            sb.append("    \"naturalEyeHeightHorizontal\": \"").append(escapeJson(naturalVerdict)).append("\",\n");
            sb.append("    \"repeatClickPath\": \"").append(escapeJson(repeatVerdict)).append("\",\n");
            sb.append("    \"boundaryProbeMid\": \"").append(escapeJson(midVerdict)).append("\"\n");
            sb.append("  },\n");
            sb.append("  \"cases\": [\n");
            sb.append("    {\n");
            sb.append("      \"caseName\": \"camera_b_east_face_after_click\",\n");
            sb.append("      \"camera\": {\n");
            sb.append("        \"position\": \"").append(escapeJson("east-of-block, looking west, vanilla midpoint eye")).append("\",\n");
            sb.append("        \"yaw\": 90.0,\n");
            sb.append("        \"pitch\": 0.0\n");
            sb.append("      },\n");
            sb.append("      \"raycast\": {\n");
            sb.append("        \"result\": \"").append(escapeJson(cameraBDesc)).append("\",\n");
            sb.append("        \"hitFace\": \"").append(escapeJson(cameraBHitFace)).append("\",\n");
            sb.append("        \"hitPos\": \"").append(escapeJson(cameraBHitPos)).append("\",\n");
            sb.append("        \"hitY\": \"").append(escapeJson(cameraBHitY)).append("\"\n");
            sb.append("      },\n");
            sb.append("      \"targetBlockPos\": \"").append(escapeJson(eastPlacePos.toShortString())).append("\",\n");
            sb.append("      \"actualPlacementResult\": \"").append(escapeJson(nullToEmpty(cameraBClickResult))).append("\",\n");
            sb.append("      \"actualPlacedBlock\": \"").append(escapeJson(nullToEmpty(cameraBPlacedState))).append("\",\n");
            sb.append("      \"verdict\": \"").append(escapeJson(cameraBVerdict)).append("\"\n");
            sb.append("    },\n");
            sb.append("    {\n");
            sb.append("      \"caseName\": \"natural_south_face_after_click\",\n");
            sb.append("      \"camera\": {\n");
            sb.append("        \"position\": \"").append(escapeJson("south-of-block, looking north-ish, natural approach")).append("\",\n");
            sb.append("        \"yaw\": 180.0,\n");
            sb.append("        \"pitch\": 16.0\n");
            sb.append("      },\n");
            sb.append("      \"raycast\": {\n");
            sb.append("        \"result\": \"").append(escapeJson(southHitDesc)).append("\",\n");
            sb.append("        \"hitFace\": \"").append(escapeJson(southHitFace)).append("\",\n");
            sb.append("        \"hitPos\": \"").append(escapeJson(southHitPos)).append("\",\n");
            sb.append("        \"hitY\": \"").append(escapeJson(southHitY)).append("\"\n");
            sb.append("      },\n");
            sb.append("      \"targetBlockPos\": \"").append(escapeJson(southPlacePos.toShortString())).append("\",\n");
            sb.append("      \"actualPlacementResult\": \"").append(escapeJson(nullToEmpty(southFirstClickResult))).append("\",\n");
            sb.append("      \"actualPlacedBlock\": \"").append(escapeJson(nullToEmpty(southFirstPlacedState))).append("\",\n");
            sb.append("      \"verdict\": \"").append(escapeJson(southVerdict)).append("\"\n");
            sb.append("    },\n");
            sb.append("    {\n");
            sb.append("      \"caseName\": \"eye_height_offset_hit\",\n");
            sb.append("      \"camera\": {\n");
            sb.append("        \"position\": \"near lowered-space middle/top\",\n");
            sb.append("        \"yaw\": 180.0,\n");
            sb.append("        \"pitch\": 16.0\n");
            sb.append("      },\n");
            sb.append("      \"raycast\": {\n");
            sb.append("        \"result\": \"").append(escapeJson(probeMidHitDesc)).append("\"\n");
            sb.append("      },\n");
            sb.append("      \"verdict\": \"").append(escapeJson(midVerdict)).append("\"\n");
            sb.append("    },\n");
            sb.append("    {\n");
            sb.append("      \"caseName\": \"eye_height_natural_horizontal\",\n");
            sb.append("      \"camera\": {\n");
            sb.append("        \"position\": \"normal-ish player eye height, horizontal aim\",\n");
            sb.append("        \"yaw\": 180.0,\n");
            sb.append("        \"pitch\": 0.0\n");
            sb.append("      },\n");
            sb.append("      \"raycast\": {\n");
            sb.append("        \"result\": \"").append(escapeJson(probeNaturalHitDesc)).append("\"\n");
            sb.append("      },\n");
            sb.append("      \"verdict\": \"").append(escapeJson(naturalVerdict)).append("\"\n");
            sb.append("    },\n");
            sb.append("    {\n");
            sb.append("      \"caseName\": \"natural_south_repeat_after_click\",\n");
            sb.append("      \"camera\": {\n");
            sb.append("        \"position\": \"south-of-block repeat click\",\n");
            sb.append("        \"yaw\": 180.0,\n");
            sb.append("        \"pitch\": 16.0\n");
            sb.append("      },\n");
            sb.append("      \"raycast\": {\n");
            sb.append("        \"result\": \"").append(escapeJson(southHitDesc)).append("\"\n");
            sb.append("      },\n");
            sb.append("      \"actualPlacementResult\": \"").append(escapeJson(nullToEmpty(southSecondClickResult))).append("\",\n");
            sb.append("      \"actualPlacedBlock\": \"").append(escapeJson(nullToEmpty(southSecondPlacedState))).append("\",\n");
            sb.append("      \"verdict\": \"").append(escapeJson(repeatVerdict)).append("\"\n");
            sb.append("    }\n");
            sb.append("  ]\n");
            sb.append("}\n");
            Files.writeString(notesPath, sb.toString());
            artifacts.add(new ManifestArtifact(
                    notesPath.getFileName().toString(),
                    "lowered_side_natural_angle_audit",
                    "natural-angle-audit-notes"));
        } catch (Exception e) {
            System.err.println("[Slabbed] lowered_side_natural_angle_audit write failed: " + e);
        }
    }

    private static void writeLoweredSideSouthPitchAuditNotes(
            Path screenshotDir,
            BlockPos supportPos,
            BlockPos fullPos,
            BlockPos southPlacePos,
            Vec3d southEye,
            float southYaw,
            List<String[]> southPitchAuditRows,
            List<ManifestArtifact> artifacts) {
        try {
            Files.createDirectories(screenshotDir);
            Path notesPath = screenshotDir.resolve("lowered_side_south_pitch_audit.json");

            boolean horizontalPass = false;
            boolean slightDownPass = false;
            boolean centerDownPass = false;
            boolean clearDownPass = false;
            for (String[] row : southPitchAuditRows) {
                if (row[0].equals("south_pitch_horizontal")) {
                    horizontalPass = "PASS".equals(row[10]);
                } else if (row[0].equals("south_pitch_slight_down")) {
                    slightDownPass = "PASS".equals(row[10]);
                } else if (row[0].equals("south_pitch_center_down")) {
                    centerDownPass = "PASS".equals(row[10]);
                } else if (row[0].equals("south_pitch_clear_down")) {
                    clearDownPass = "PASS".equals(row[10]);
                }
            }

            String conclusion;
            if (centerDownPass || clearDownPass) {
                conclusion = "Downward pitch can hit the lowered south face and place correctly; this is likely an aim/instruction boundary, not a production placement bug.";
            } else if (horizontalPass || slightDownPass) {
                conclusion = "South face can be hit at shallow pitch; the boundary is finer than the original natural aim and should be documented for live retest.";
            } else {
                conclusion = "Even downward south pitches missed; this suggests a possible south-face raycast/outline issue that needs focused production diagnosis.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"testId\": \"lowered_side_south_pitch_audit\",\n");
            sb.append("  \"supportPos\": \"").append(escapeJson(supportPos.toShortString())).append("\",\n");
            sb.append("  \"fullBlockPos\": \"").append(escapeJson(fullPos.toShortString())).append("\",\n");
            sb.append("  \"southPlacementPos\": \"").append(escapeJson(southPlacePos.toShortString())).append("\",\n");
            sb.append("  \"camera\": {\n");
            sb.append("    \"position\": \"").append(escapeJson(southEye.toString())).append("\",\n");
            sb.append("    \"yaw\": ").append(southYaw).append("\n");
            sb.append("  },\n");
            sb.append("  \"conclusion\": \"").append(escapeJson(conclusion)).append("\",\n");
            sb.append("  \"cases\": [\n");
            for (int i = 0; i < southPitchAuditRows.size(); i++) {
                String[] row = southPitchAuditRows.get(i);
                sb.append("    {\n");
                sb.append("      \"caseName\": \"").append(escapeJson(row[0])).append("\",\n");
                sb.append("      \"pitch\": ").append(row[1]).append(",\n");
                sb.append("      \"rayDirection\": \"").append(escapeJson(row[2])).append("\",\n");
                sb.append("      \"raycast\": {\n");
                sb.append("        \"result\": \"").append(escapeJson(row[3])).append("\",\n");
                sb.append("        \"hitBlockPos\": \"").append(escapeJson(row[4])).append("\",\n");
                sb.append("        \"hitFace\": \"").append(escapeJson(row[5])).append("\",\n");
                sb.append("        \"hitY\": \"").append(escapeJson(row[6])).append("\"\n");
                sb.append("      },\n");
                sb.append("      \"intendedPlacementPos\": \"").append(escapeJson(row[7])).append("\",\n");
                sb.append("      \"actualPlacementResult\": \"").append(escapeJson(row[8])).append("\",\n");
                sb.append("      \"actualPlacedBlock\": \"").append(escapeJson(row[9])).append("\",\n");
                sb.append("      \"verdict\": \"").append(escapeJson(row[10])).append("\",\n");
                sb.append("      \"screenshot\": \"").append(escapeJson(row[11])).append("\"\n");
                sb.append("    }");
                if (i + 1 < southPitchAuditRows.size()) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("  ]\n");
            sb.append("}\n");
            Files.writeString(notesPath, sb.toString());
            artifacts.add(new ManifestArtifact(
                    notesPath.getFileName().toString(),
                    "lowered_side_south_pitch_audit",
                    "south-pitch-audit-notes"));
        } catch (Exception e) {
            System.err.println("[Slabbed] lowered_side_south_pitch_audit write failed: " + e);
        }
    }

    private static void writeLiveSidePlacementAuditNotes(
            Path screenshotDir,
            BlockPos supportPos, BlockPos fullPos, BlockPos placePos,
            double syntheticHitY,
            String cameraADesc, String cameraBDesc,
            String clickResult, String placedState, String verdict,
            List<ManifestArtifact> artifacts) {
        try {
            Path notesPath = screenshotDir.resolve("live_repro_side_placement_audit.json");
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"testId\": \"live_repro_side_placement\",\n");
            sb.append("  \"supportPos\": \"").append(supportPos.toShortString()).append("\",\n");
            sb.append("  \"fullPos\": \"").append(fullPos.toShortString()).append("\",\n");
            sb.append("  \"placePos\": \"").append(placePos.toShortString()).append("\",\n");
            sb.append("  \"syntheticHitY\": ").append(String.format("%.4f", syntheticHitY)).append(",\n");
            sb.append("  \"syntheticHitNote\": \"below vanilla block floor; targets lowered visual space; proof constructs this hit manually\",\n");
            sb.append("  \"cameraA_proofScreenshotAngle\": \"").append(escapeJson(cameraADesc)).append("\",\n");
            sb.append("  \"cameraA_note\": \"yaw=180 looking north; the proof screenshot camera; cannot physically aim at the east face\",\n");
            sb.append("  \"cameraB_eastSideLookingWest\": \"").append(escapeJson(cameraBDesc)).append("\",\n");
            sb.append("  \"cameraB_note\": \"yaw=90 looking west from east of block; eye at vanilla midpoint Y+0.5; what a live player would use\",\n");
            sb.append("  \"clickResult\": \"").append(escapeJson(nullToEmpty(clickResult))).append("\",\n");
            sb.append("  \"placedStateAtPlacePos\": \"").append(escapeJson(nullToEmpty(placedState))).append("\",\n");
            sb.append("  \"auditVerdict\": \"").append(escapeJson(verdict)).append("\"\n");
            sb.append("}\n");
            java.nio.file.Files.writeString(notesPath, sb.toString());
            artifacts.add(new ManifestArtifact(
                    notesPath.getFileName().toString(),
                    "live_repro_side_placement_audit",
                    "live-repro-audit-notes"));
        } catch (Exception e) {
            System.err.println("[Slabbed] live_repro_side_placement_audit write failed: " + e);
        }
    }

    static record ProofManifestEntry(
            String proofId,
            String label,
            String notesFile,
            String primaryScreenshotFile
    ) {
    }

    static record ManifestArtifact(String file, String proofId, String label) {
    }
}
