package com.slabbed.test;

import com.slabbed.dev.SlabbedLabFixtures;
import com.slabbed.anchor.SlabAnchorAttachment;
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

        // compound dy: torch on adjacent-lowered bottom slab (RED — expected -1.0, currently returns -0.5)
        runTorchOnAdjacentLoweredSlabDyProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

        // comfort selection: floor torch on BS-FB-0.5S must be selectable from natural side aim
        runTorchOnBsFb05sComfortSelectableProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

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

        // BS-FB-0.5S interaction integrity (release-gate audit; side-channel notes only,
        // intentionally does NOT emit manifest/ladder artifacts so the canonical 9-ID
        // bundle count and verifier stay green until product-decision fixes land).
        runBsFb05sInteractionIntegrityProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

        // BS-FB-0.5S live placement intent (RED PROOF — captures Julia's live
        // path where an intended 0.5S side slab ray hits vanilla block space
        // and places TOP instead of BOTTOM).
        // Side-channel notes only.
        runBsFb05sLivePlacementIntentProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

        // BS-FB-1S top-half placement law (RED PROOF — capture missing behavior).
        // Side-channel notes only; deliberately does NOT emit manifest/ladder artifacts.
        runBsFb1sTopHalfPlacementLawProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

        // BS-FB-1S live regression after BS break (RED PROOF candidate — captures
        // Julia's live observation that breaking the original support causes the
        // BS-FB system to rise and fresh 0.5S placements to jump into 1S).
        // Side-channel notes only.
        runBsFb1sLiveRegressionProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

        // BSFB+ top-support law (RED PROOF — captures missing top-of-lowered-FB
        // inheritance: object placed on top of a lowered FB should sit on the FB's
        // visible lowered top surface, not float at vanilla y+1).
        // Side-channel notes only.
        runBsfbPlusTopSupportLawProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

        // BS-FB-1S top-support law (RED PROOF — captures missing top-face support
        // on the lowered 1S side slab: object on top of a lowered BS-FB-1S slab
        // should sit on the slab's visible lowered top, not float at vanilla y+1).
        // Side-channel notes only.
        runBsFb1sTopSupportLawProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

        // BS-FB-0.5S top-support law (RED PROOF — captures missing top-face support
        // on the lowered BOTTOM 0.5S side slab: object on top of a lowered BS-FB-0.5S
        // slab should seat on the slab's visible lowered top, not float a full block
        // higher at vanilla y+1).
        // Side-channel notes only.
        runBsFb05sTopSupportLawProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);

        // BS-FB-0.5S+ visual triad (RED PROOF — live-real BOTTOM side slab
        // plus chain-on-top must align dy, outline, and raycast ownership).
        // Side-channel notes only.
        runBsFb05sTopSupportTriadProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);
    }

    /**
     * BS-FB-0.5S interaction integrity proof.
     *
     * <p>Fixture: bottom slab (BS) at supportPos, full block (FB) at supportPos.up()
     * lowered onto BS, side slab (0.5S) at fullPos.east() sitting lowered at half-height.
     *
     * <p>Captures three subcases observed in live testing after the 0.2.0-beta.2 private
     * launch. This is an audit-only probe — it writes a single side-channel notes file
     * {@code bs_fb_05s_interaction_integrity_notes.json} and does not call
     * {@link #captureScreenshotAndRecord(ClientGameTestContext, String, Path, Set, List)}
     * so the canonical {@link #LOWERED_SIDE_SLAB_EXPECTED_PROOF_IDS} ladder and the
     * Python bundle verifier remain unaffected.
     *
     * <p>Subcases:
     * <ul>
     *   <li><b>A — top-half placement boundary</b>: synthesizes a click on the top half
     *       of the lowered FB's east face. Records whether the side slab branch placed
     *       at placePos. Audit-only (product-decision: top-half routing intent is
     *       undecided at release time).</li>
     *   <li><b>B — visible side slab target</b>: natural side ray at the visible side
     *       slab's center. RED if resolved BlockPos != slabPos — that matches the live
     *       "limited/picky hitbox coverage" symptom and the "wrong block breaks
     *       repeatedly" symptom when the side slab is aimed at.</li>
     *   <li><b>C — supporting BS removal</b>: confirms initial FB dy = -0.5 and side
     *       slab dy = -0.5, removes BS, waits, re-reads FB dy and side-slab dy.
     *       RED if FB dy drifts to 0.0 or the side slab moves back up after support
     *       removal.</li>
     * </ul>
     */
    static void runBsFb05sInteractionIntegrityProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        final String testId = "bs_fb_05s_interaction_integrity";
        final BlockPos supportPos = FIXTURE_ORIGIN.add(40, 0, 0);
        final BlockPos fullPos = supportPos.up();
        final BlockPos slabPos = fullPos.east();
        final BlockPos placePos = fullPos.east();

        AtomicReference<String> caseAPlacedState = new AtomicReference<>("");
        AtomicReference<String> caseAActionResult = new AtomicReference<>("");
        AtomicReference<String> caseACenterResolved = new AtomicReference<>("");
        AtomicReference<String> caseBCenterResolved = new AtomicReference<>("");
        AtomicReference<String> caseBUpperResolved = new AtomicReference<>("");
        AtomicReference<String> caseBLowerResolved = new AtomicReference<>("");
        AtomicReference<String> caseCInitialFbDy = new AtomicReference<>("");
        AtomicReference<String> caseCInitialSlabDy = new AtomicReference<>("");
        AtomicReference<String> caseCInitialFbEffectiveVisualY = new AtomicReference<>("");
        AtomicReference<String> caseCInitialSlabEffectiveVisualY = new AtomicReference<>("");
        AtomicReference<String> caseCInitialAssemblyVisibleY = new AtomicReference<>("");
        AtomicReference<String> caseCPostBreakFbDy = new AtomicReference<>("");
        AtomicReference<String> caseCPostBreakSlabDy = new AtomicReference<>("");
        AtomicReference<String> caseCPostBreakFbEffectiveVisualY = new AtomicReference<>("");
        AtomicReference<String> caseCPostBreakSlabEffectiveVisualY = new AtomicReference<>("");
        AtomicReference<String> caseCPostBreakAssemblyVisibleY = new AtomicReference<>("");
        AtomicReference<String> caseCAssemblyVisibleDeltaY = new AtomicReference<>("");
        AtomicReference<String> caseCAssemblyShiftedUp = new AtomicReference<>("");
        AtomicReference<String> caseCPostBreakFbState = new AtomicReference<>("");
        // Server-side anchor observations (split proof)
        AtomicReference<String> caseCServerAnchorAfterPlace = new AtomicReference<>("");
        AtomicReference<String> caseCServerFbStateAfterPlace = new AtomicReference<>("");
        AtomicReference<String> caseCServerAnchorAfterBreak = new AtomicReference<>("");
        AtomicReference<String> caseCServerFbStateAfterBreak = new AtomicReference<>("");
        // Client-side dy timing observations (split proof)
        AtomicReference<String> caseCClientDyImmediateAfterPlace = new AtomicReference<>("");
        AtomicReference<String> caseCClientAnchoredImmediateAfterPlace = new AtomicReference<>("");
        AtomicReference<String> caseCClientDyAfter1Tick = new AtomicReference<>("");
        AtomicReference<String> caseCClientAnchoredAfter1Tick = new AtomicReference<>("");
        AtomicReference<String> caseCClientDyAfterRender = new AtomicReference<>("");
        AtomicReference<String> caseCClientAnchoredAfterRender = new AtomicReference<>("");
        AtomicReference<String> caseCClientDyImmediateAfterBreak = new AtomicReference<>("");
        AtomicReference<String> caseCClientAnchoredImmediateAfterBreak = new AtomicReference<>("");
        AtomicReference<String> caseCClientDyAfter1TickBreak = new AtomicReference<>("");
        AtomicReference<String> caseCClientAnchoredAfter1TickBreak = new AtomicReference<>("");
        AtomicReference<String> caseDLowerCenterResolved = new AtomicReference<>("");
        AtomicReference<String> caseDLowerEdgeResolved = new AtomicReference<>("");
        AtomicReference<String> caseFSlabHeldLowerCenterResolved = new AtomicReference<>("");
        AtomicReference<String> caseFSlabHeldLowerEdgeResolved = new AtomicReference<>("");
        AtomicReference<String> caseFSlabHeldGuardCause = new AtomicReference<>("audit-only");
        AtomicReference<String> caseFSlabHeldVerdict = new AtomicReference<>("audit-only");
        AtomicReference<String> caseECenterResolved = new AtomicReference<>("");
        AtomicReference<String> caseELowerFrontResolved = new AtomicReference<>("");
        AtomicReference<String> caseEUndersideResolved = new AtomicReference<>("");
        AtomicReference<String> caseECenterVerdict = new AtomicReference<>("audit-only");
        AtomicReference<String> caseELowerFrontVerdict = new AtomicReference<>("audit-only");
        AtomicReference<String> caseEUndersideVerdict = new AtomicReference<>("audit-only");
        AtomicReference<String> caseEVerdict = new AtomicReference<>("audit-only");
        AtomicReference<String> caseAVerdict = new AtomicReference<>("audit-only");
        AtomicReference<String> caseBVerdict = new AtomicReference<>("audit-only");
        AtomicReference<String> caseCVerdict = new AtomicReference<>("audit-only");
        AtomicReference<String> caseDSelectionVerdict = new AtomicReference<>("audit-only");

        // ── Case A: top-half placement boundary ───────────────────────────────
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
                throw new RuntimeException("singleplayer server player list empty for bs-fb-05s integrity proof");
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 4));
        });

        ctx.runOnClient(mc -> {
            if (mc.player == null) {
                throw new RuntimeException("client player null during bs-fb-05s integrity setup");
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

        // Synthesize click on top half of the lowered FB's east face.
        // Lowered FB visual spans world Y ∈ [fullPos.y - 0.5, fullPos.y + 0.5];
        // top half center is world Y = fullPos.y + 0.25.
        final BlockHitResult topHalfHit = new BlockHitResult(
                new Vec3d(fullPos.getX() + 1.0, fullPos.getY() + 0.25, fullPos.getZ() + 0.5),
                Direction.EAST,
                fullPos,
                false,
                false);

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for case A");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, topHalfHit);
            caseAActionResult.set(result.toString());
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during case A readback");
            }
            BlockState placed = mc.world.getBlockState(placePos);
            caseACenterResolved.set(fullPos.toShortString());
            caseAPlacedState.set(placed.toString());
            if (placed.isOf(Blocks.STONE_SLAB)) {
                caseAVerdict.set("audit-only: side slab placed from top-half aim (product-decision — "
                        + "top-half routing intent undecided)");
            } else {
                caseAVerdict.set("audit-only: top-half aim did not produce side slab (" + placed + ")");
            }
        });

        // Clear for Case B.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(placePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        // ── Case B: visible side slab target ──────────────────────────────────
        // Place the side slab (0.5S) manually; its dy must inherit -0.5 via the
        // adjacent-lowered rule (proven by the existing side-slab intent proof).
        singleplayer.getServer().runOnServer(server -> {
            server.getOverworld().setBlockState(
                    slabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("client not ready for case B");
            }
            // Visible side slab body at world Y ∈ [slabPos.y - 0.5, slabPos.y].
            // Center = slabPos.y - 0.25. Upper visible = slabPos.y - 0.05.
            // Lower visible = slabPos.y - 0.45.
            //
            // Eye placement: must be ABOVE the slab's native voxel floor (Y ≥ slabPos.y)
            // so the DDA ray actually enters the Y=slabPos.y block column when approaching
            // from south. A player standing at ground level south of the fixture has eye
            // height ≈ slabPos.y + 1.5 (feet on the BS top = slabPos.y - 0.5, eye = +1.62
            // above feet ≈ slabPos.y + 1.12). Using slabPos.y + 1.5 is a conservative
            // natural eye height that ensures DDA enters the Y=slabPos.y voxel and tests
            // the -0.5-offset slab outline.
            double centerY = slabPos.getY() - 0.25;
            double upperY  = slabPos.getY() - 0.05;
            double lowerY  = slabPos.getY() - 0.45;
            double eyeY = slabPos.getY() + 1.5;
            // Approach from south (Z+), looking north-and-down at the slab's south face.
            Vec3d eyeCenter = new Vec3d(slabPos.getX() + 0.5, eyeY, slabPos.getZ() + 2.5);
            Vec3d tgtCenter = new Vec3d(slabPos.getX() + 0.5, centerY, slabPos.getZ() + 0.5);
            Vec3d tgtUpper  = new Vec3d(slabPos.getX() + 0.5, upperY,  slabPos.getZ() + 0.5);
            Vec3d tgtLower  = new Vec3d(slabPos.getX() + 0.5, lowerY,  slabPos.getZ() + 0.5);
            BlockPos resolvedCenter = resolveVisibleOutlineHit(mc, eyeCenter, tgtCenter, 6.0);
            BlockPos resolvedUpper  = resolveVisibleOutlineHit(mc, eyeCenter, tgtUpper,  6.0);
            BlockPos resolvedLower  = resolveVisibleOutlineHit(mc, eyeCenter, tgtLower,  6.0);
            caseBCenterResolved.set(resolvedCenter == null ? "MISS" : resolvedCenter.toShortString());
            caseBUpperResolved.set(resolvedUpper  == null ? "MISS" : resolvedUpper.toShortString());
            caseBLowerResolved.set(resolvedLower  == null ? "MISS" : resolvedLower.toShortString());
            if (resolvedCenter == null || !resolvedCenter.equals(slabPos)) {
                caseBVerdict.set("RED: center aim at visible side slab resolved to "
                        + (resolvedCenter == null ? "MISS" : resolvedCenter.toShortString())
                        + "; expected " + slabPos.toShortString()
                        + " (release-blocking: hitbox coverage / wrong-break targeting)");
            } else {
                caseBVerdict.set("audit-only: center aim hit side slab (upper="
                        + caseBUpperResolved.get() + ", lower=" + caseBLowerResolved.get() + ")");
            }
        });

        // ── Case C: persistent anchor split proof ─────────────────────────────
        // Keep the side slab in place for the persistence read; only clear the FB
        // so Block.onPlaced can re-anchor the lowered full block.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(fullPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                        Hand.MAIN_HAND,
                        new ItemStack(Items.STONE, 4));
            }
        });
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 4));
                mc.player.refreshPositionAndAngles(
                        supportPos.getX() + 0.5,
                        supportPos.getY() + 2.5,
                        supportPos.getZ() + 0.5,
                        0.0f,
                        90.0f);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        // Synthetic UP-face click places STONE at fullPos via Block.onPlaced.
        final BlockHitResult fbPlaceHit = new BlockHitResult(
                new Vec3d(supportPos.getX() + 0.5,
                          supportPos.getY() + 0.5,
                          supportPos.getZ() + 0.5),
                Direction.UP,
                supportPos,
                false,
                false);
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for case C natural placement");
            }
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, fbPlaceHit);
        });

        // ── C-A: client dy immediately after placement (before sync/render settles) ──
        ctx.runOnClient(mc -> {
            if (mc.world == null) return;
            BlockState fbS = mc.world.getBlockState(fullPos);
            double dy = SlabSupport.getYOffset(mc.world, fullPos, fbS);
            boolean anchored = SlabAnchorAttachment.isAnchored(mc.world, fullPos);
            caseCClientDyImmediateAfterPlace.set(Double.toString(dy));
            caseCClientAnchoredImmediateAfterPlace.set(Boolean.toString(anchored));
        });

        ctx.waitTick();

        // ── C-A after 1 tick ──
        ctx.runOnClient(mc -> {
            if (mc.world == null) return;
            BlockState fbS = mc.world.getBlockState(fullPos);
            double dy = SlabSupport.getYOffset(mc.world, fullPos, fbS);
            boolean anchored = SlabAnchorAttachment.isAnchored(mc.world, fullPos);
            caseCClientDyAfter1Tick.set(Double.toString(dy));
            caseCClientAnchoredAfter1Tick.set(Boolean.toString(anchored));
        });

        singleplayer.getClientWorld().waitForChunksRender();

        // ── C-A after render settled ──
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during case C post-place render read");
            }
            BlockState fbState0 = mc.world.getBlockState(fullPos);
            BlockState slabState0 = mc.world.getBlockState(slabPos);
            double dy = SlabSupport.getYOffset(mc.world, fullPos, fbState0);
            double slabDy0 = SlabSupport.getYOffset(mc.world, slabPos, slabState0);
            VoxelShape fbOutline0 = fbState0.getOutlineShape(mc.world, fullPos, ShapeContext.absent());
            VoxelShape slabOutline0 = slabState0.getOutlineShape(mc.world, slabPos, ShapeContext.absent());
            double fbMinY0 = fullPos.getY() + fbOutline0.getBoundingBox().minY;
            double fbMaxY0 = fullPos.getY() + fbOutline0.getBoundingBox().maxY;
            double slabMinY0 = slabPos.getY() + slabOutline0.getBoundingBox().minY;
            double slabMaxY0 = slabPos.getY() + slabOutline0.getBoundingBox().maxY;
            double assemblyMinY0 = Math.min(fbMinY0, slabMinY0);
            double assemblyMaxY0 = Math.max(fbMaxY0, slabMaxY0);
            boolean anchored = SlabAnchorAttachment.isAnchored(mc.world, fullPos);
            caseCClientDyAfterRender.set(Double.toString(dy));
            caseCClientAnchoredAfterRender.set(Boolean.toString(anchored));
            caseCInitialFbDy.set(Double.toString(dy));
            caseCInitialSlabDy.set(Double.toString(slabDy0));
            caseCInitialFbEffectiveVisualY.set(formatYRange(fbMinY0, fbMaxY0));
            caseCInitialSlabEffectiveVisualY.set(formatYRange(slabMinY0, slabMaxY0));
            caseCInitialAssemblyVisibleY.set(formatYRange(assemblyMinY0, assemblyMaxY0));
        });

        // ── C-B: server anchor creation (server-authoritative read) ──────────────
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState fbSrv = world.getBlockState(fullPos);
            boolean anchored = SlabAnchorAttachment.isAnchored(world, fullPos);
            caseCServerAnchorAfterPlace.set(Boolean.toString(anchored));
            caseCServerFbStateAfterPlace.set(fbSrv.toString());
        });

        // ── C-C: break supporting BS ─────────────────────────────────────────────
        singleplayer.getServer().runOnServer(server -> {
            server.getOverworld().breakBlock(supportPos, false);
        });

        // ── C-C client dy immediately after break (before sync/render settles) ──
        ctx.runOnClient(mc -> {
            if (mc.world == null) return;
            BlockState fbS = mc.world.getBlockState(fullPos);
            double dy = SlabSupport.getYOffset(mc.world, fullPos, fbS);
            boolean anchored = SlabAnchorAttachment.isAnchored(mc.world, fullPos);
            caseCClientDyImmediateAfterBreak.set(Double.toString(dy));
            caseCClientAnchoredImmediateAfterBreak.set(Boolean.toString(anchored));
        });

        ctx.waitTick();

        // ── C-C after 1 tick ──
        ctx.runOnClient(mc -> {
            if (mc.world == null) return;
            BlockState fbS = mc.world.getBlockState(fullPos);
            double dy = SlabSupport.getYOffset(mc.world, fullPos, fbS);
            boolean anchored = SlabAnchorAttachment.isAnchored(mc.world, fullPos);
            caseCClientDyAfter1TickBreak.set(Double.toString(dy));
            caseCClientAnchoredAfter1TickBreak.set(Boolean.toString(anchored));
        });

        // Let neighbor updates and any revalidation settle fully.
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        // ── C-C server anchor after break (authoritative) ────────────────────────
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState fbSrv = world.getBlockState(fullPos);
            boolean anchored = SlabAnchorAttachment.isAnchored(world, fullPos);
            caseCServerAnchorAfterBreak.set(Boolean.toString(anchored));
            caseCServerFbStateAfterBreak.set(fbSrv.toString());
        });

        // ── C-C client dy after full settle ──────────────────────────────────────
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during case C post-break read");
            }
            BlockState fbState1 = mc.world.getBlockState(fullPos);
            BlockState slabState1 = mc.world.getBlockState(slabPos);
            double fbDy1 = SlabSupport.getYOffset(mc.world, fullPos, fbState1);
            double slabDy1 = SlabSupport.getYOffset(mc.world, slabPos, slabState1);
            VoxelShape fbOutline1 = fbState1.getOutlineShape(mc.world, fullPos, ShapeContext.absent());
            VoxelShape slabOutline1 = slabState1.getOutlineShape(mc.world, slabPos, ShapeContext.absent());
            double fbMinY1 = fullPos.getY() + fbOutline1.getBoundingBox().minY;
            double fbMaxY1 = fullPos.getY() + fbOutline1.getBoundingBox().maxY;
            double slabMinY1 = slabPos.getY() + slabOutline1.getBoundingBox().minY;
            double slabMaxY1 = slabPos.getY() + slabOutline1.getBoundingBox().maxY;
            double assemblyMinY1 = Math.min(fbMinY1, slabMinY1);
            double assemblyMaxY1 = Math.max(fbMaxY1, slabMaxY1);
            double assemblyMinY0 = parseRangeMin(caseCInitialAssemblyVisibleY.get(), assemblyMinY1);
            double assemblyMaxY0 = parseRangeMax(caseCInitialAssemblyVisibleY.get(), assemblyMaxY1);
            double assemblyMinDelta = assemblyMinY1 - assemblyMinY0;
            double assemblyMaxDelta = assemblyMaxY1 - assemblyMaxY0;
            boolean assemblyShiftedUp = assemblyMinDelta > 0.001 || assemblyMaxDelta > 0.001;
            caseCPostBreakFbDy.set(Double.toString(fbDy1));
            caseCPostBreakSlabDy.set(Double.toString(slabDy1));
            caseCPostBreakFbEffectiveVisualY.set(formatYRange(fbMinY1, fbMaxY1));
            caseCPostBreakSlabEffectiveVisualY.set(formatYRange(slabMinY1, slabMaxY1));
            caseCPostBreakAssemblyVisibleY.set(formatYRange(assemblyMinY1, assemblyMaxY1));
            caseCAssemblyVisibleDeltaY.set(formatYRange(assemblyMinDelta, assemblyMaxDelta));
            caseCAssemblyShiftedUp.set(Boolean.toString(assemblyShiftedUp));
            caseCPostBreakFbState.set(fbState1.toString());

            // Verdict based on settled state.
            double fbDy0 = parseDoubleSafe(caseCInitialFbDy.get(), 0.0);
            double slabDy0 = parseDoubleSafe(caseCInitialSlabDy.get(), 0.0);
            boolean serverAnchorCreated = Boolean.parseBoolean(caseCServerAnchorAfterPlace.get());
            boolean serverAnchorPersisted = Boolean.parseBoolean(caseCServerAnchorAfterBreak.get());
            if (fbDy0 != -0.5) {
                caseCVerdict.set("audit-only: pre-break FB dy was " + fbDy0
                        + " (not the expected lowered -0.5 baseline)");
            } else if (slabDy0 != -0.5) {
                caseCVerdict.set("RED: pre-break side slab dy was " + slabDy0
                        + " instead of -0.5");
            } else if (!serverAnchorCreated) {
                caseCVerdict.set("RED: server anchor not created after FB placement"
                        + " — Block.onPlaced mixin or qualifiesForDirectAnchor failed");
            } else if (!serverAnchorPersisted) {
                caseCVerdict.set("RED: server anchor was removed after BS break"
                        + " — onStateReplaced mixin fired at wrong pos or removeAnchor called incorrectly");
            } else if (!fbState1.isOf(Blocks.STONE)) {
                caseCVerdict.set("RED: FB block is no longer STONE after BS break (fbState=" + fbState1 + ")");
            } else if (fbDy1 != -0.5) {
                String clientTiming =
                        " clientDyImmediate=" + caseCClientDyImmediateAfterBreak.get()
                        + " clientDy1Tick=" + caseCClientDyAfter1TickBreak.get()
                        + " clientAnchoredImmediate=" + caseCClientAnchoredImmediateAfterBreak.get()
                        + " clientAnchored1Tick=" + caseCClientAnchoredAfter1TickBreak.get();
                caseCVerdict.set("RED: server anchor persisted but client dy drifted -0.5→" + fbDy1
                        + " after BS break — client render refresh race suspected." + clientTiming);
            } else if (slabDy1 != -0.5) {
                caseCVerdict.set("RED: side slab dy drifted -0.5→" + slabDy1
                        + " after BS break — side slab moved back up with the support removed");
            } else if (assemblyShiftedUp) {
                caseCVerdict.set("RED: assembly visible Y shifted upward after BS break"
                        + " initialAssemblyY=" + caseCInitialAssemblyVisibleY.get()
                        + " postBreakAssemblyY=" + caseCPostBreakAssemblyVisibleY.get()
                        + " deltaY=" + caseCAssemblyVisibleDeltaY.get());
            } else if (Math.abs(assemblyMinDelta) > 0.001 || Math.abs(assemblyMaxDelta) > 0.001) {
                caseCVerdict.set("RED: assembly visible Y changed after BS break"
                        + " initialAssemblyY=" + caseCInitialAssemblyVisibleY.get()
                        + " postBreakAssemblyY=" + caseCPostBreakAssemblyVisibleY.get()
                        + " deltaY=" + caseCAssemblyVisibleDeltaY.get());
            } else {
                caseCVerdict.set("GREEN: FB stayed STONE with dy=-0.5 after supporting BS break"
                        + " (slab dy=" + slabDy1
                        + ", assemblyY=" + caseCPostBreakAssemblyVisibleY.get() + ")");
            }
        });

        // ── Case D: lower-half selection comfort after BS break ───────────────
        // The FB is still visually lowered, but the live ray must now resolve the
        // anchored FB itself from the lower visible half and not MISS / stale slab.
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("client not ready for case D lower-half selection probe");
            }

            Vec3d centerLowerTarget = new Vec3d(
                    fullPos.getX() + 0.5,
                    fullPos.getY() - 0.30,
                    fullPos.getZ() + 0.5);
            Vec3d lowerEdgeTarget = new Vec3d(
                    fullPos.getX() + 0.5,
                    fullPos.getY() - 0.38,
                    fullPos.getZ() + 0.5);

            resolvePlayerRaycast(mc, centerLowerTarget, 6.0);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult centerLowerHit = mc.crosshairTarget;
            String centerLowerActual = centerLowerHit == null || centerLowerHit.getType() == HitResult.Type.MISS
                    ? "MISS"
                    : ((BlockHitResult) centerLowerHit).getBlockPos().toShortString();
            caseDLowerCenterResolved.set(centerLowerActual);
            if (centerLowerHit == null
                    || centerLowerHit.getType() != HitResult.Type.BLOCK
                    || !((BlockHitResult) centerLowerHit).getBlockPos().equals(fullPos)) {
                caseDSelectionVerdict.set("RED: [anchored_fb_lower_half_selection] expected anchored FB at "
                        + fullPos.toShortString() + ", got " + centerLowerActual + " (center-lower)");
                throw new RuntimeException(caseDSelectionVerdict.get());
            }

            resolvePlayerRaycast(mc, lowerEdgeTarget, 6.0);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult lowerEdgeHit = mc.crosshairTarget;
            String lowerEdgeActual = lowerEdgeHit == null || lowerEdgeHit.getType() == HitResult.Type.MISS
                    ? "MISS"
                    : ((BlockHitResult) lowerEdgeHit).getBlockPos().toShortString();
            caseDLowerEdgeResolved.set(lowerEdgeActual);
            if (lowerEdgeHit == null
                    || lowerEdgeHit.getType() != HitResult.Type.BLOCK
                    || !((BlockHitResult) lowerEdgeHit).getBlockPos().equals(fullPos)) {
                caseDSelectionVerdict.set("RED: [anchored_fb_lower_half_selection] expected anchored FB at "
                        + fullPos.toShortString() + ", got " + lowerEdgeActual + " (lower-edge)");
                throw new RuntimeException(caseDSelectionVerdict.get());
            }

            caseDSelectionVerdict.set("GREEN: anchored FB resolved from lower-half rays");
        });

        // ── Case F: slab-held lower-half FB selection audit ───────────────────
        // Holding a slab must preserve 0.5S side-slab placement, but this audit
        // proves whether that placement guard also suppresses anchored FB rescue.
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("client not ready for case F slab-held FB selection probe");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 4));

            Vec3d centerLowerTarget = new Vec3d(
                    fullPos.getX() + 0.5,
                    fullPos.getY() - 0.30,
                    fullPos.getZ() + 0.5);
            Vec3d lowerEdgeTarget = new Vec3d(
                    fullPos.getX() + 0.5,
                    fullPos.getY() - 0.38,
                    fullPos.getZ() + 0.5);

            resolvePlayerRaycast(mc, centerLowerTarget, 6.0);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult centerLowerHit = mc.crosshairTarget;
            String centerLowerActual = centerLowerHit == null || centerLowerHit.getType() == HitResult.Type.MISS
                    ? "MISS"
                    : ((BlockHitResult) centerLowerHit).getBlockPos().toShortString();
            caseFSlabHeldLowerCenterResolved.set(centerLowerActual);

            resolvePlayerRaycast(mc, lowerEdgeTarget, 6.0);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult lowerEdgeHit = mc.crosshairTarget;
            String lowerEdgeActual = lowerEdgeHit == null || lowerEdgeHit.getType() == HitResult.Type.MISS
                    ? "MISS"
                    : ((BlockHitResult) lowerEdgeHit).getBlockPos().toShortString();
            caseFSlabHeldLowerEdgeResolved.set(lowerEdgeActual);

            boolean slabHeldFailure = !fullPos.toShortString().equals(centerLowerActual)
                    || !fullPos.toShortString().equals(lowerEdgeActual);
            if (slabHeldFailure) {
                caseFSlabHeldGuardCause.set("LIKELY: slab-held placement guard skipped anchored-FB rescue; non-slab Case D passed with same lower-half target");
                caseFSlabHeldVerdict.set("RED: slab-held lower-half FB targeting resolved center="
                        + centerLowerActual + ", lowerEdge=" + lowerEdgeActual
                        + "; expected " + fullPos.toShortString());
            } else {
                caseFSlabHeldGuardCause.set("DISPROVEN: slab-held lower-half FB targeting still resolved to anchored FB");
                caseFSlabHeldVerdict.set("GREEN: slab-held lower-half FB rays resolved to anchored FB");
            }
        });

        // ── Case E: side slab hitbox comfort after BS break ────────────────────
        // What we prove: rays aimed at visibly lowered side-slab points must own
        // the side slab BlockPos. If one of those rays instead resolves to the FB
        // behind it, the comfort/selection surface is still too limited.
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("client not ready for case E side slab comfort probe");
            }

            Vec3d eye = new Vec3d(
                    slabPos.getX() + 0.5,
                    slabPos.getY() + 1.50,
                    slabPos.getZ() + 2.50);

            Vec3d centerTarget = new Vec3d(
                    slabPos.getX() + 0.5,
                    slabPos.getY() - 0.25,
                    slabPos.getZ() + 0.5);
            Vec3d lowerFrontTarget = new Vec3d(
                    slabPos.getX() + 0.5,
                    slabPos.getY() - 0.40,
                    slabPos.getZ() + 0.5);
            Vec3d undersideTarget = new Vec3d(
                    slabPos.getX() + 0.5,
                    slabPos.getY() - 0.48,
                    slabPos.getZ() + 0.5);

            resolvePlayerRaycastFromEye(mc, eye, centerTarget, 6.0);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult centerHit = mc.crosshairTarget;
            String centerActual = centerHit == null || centerHit.getType() == HitResult.Type.MISS
                    ? "MISS"
                    : ((BlockHitResult) centerHit).getBlockPos().toShortString();
            caseECenterResolved.set(centerActual);
            caseECenterVerdict.set(slabPos.toShortString().equals(centerActual)
                    ? "PASS" : "RED");

            resolvePlayerRaycastFromEye(mc, eye, lowerFrontTarget, 6.0);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult lowerFrontHit = mc.crosshairTarget;
            String lowerFrontActual = lowerFrontHit == null || lowerFrontHit.getType() == HitResult.Type.MISS
                    ? "MISS"
                    : ((BlockHitResult) lowerFrontHit).getBlockPos().toShortString();
            caseELowerFrontResolved.set(lowerFrontActual);
            caseELowerFrontVerdict.set(slabPos.toShortString().equals(lowerFrontActual)
                    ? "PASS" : "RED");

            resolvePlayerRaycastFromEye(mc, eye, undersideTarget, 6.0);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult undersideHit = mc.crosshairTarget;
            String undersideActual = undersideHit == null || undersideHit.getType() == HitResult.Type.MISS
                    ? "MISS"
                    : ((BlockHitResult) undersideHit).getBlockPos().toShortString();
            caseEUndersideResolved.set(undersideActual);
            caseEUndersideVerdict.set(slabPos.toShortString().equals(undersideActual)
                    ? "PASS" : "RED");

            if (!slabPos.toShortString().equals(centerActual)) {
                caseEVerdict.set("RED: center visible side-slab ray resolved to " + centerActual
                        + "; expected " + slabPos.toShortString());
            } else if (!slabPos.toShortString().equals(lowerFrontActual)) {
                caseEVerdict.set("RED: lower-front visible side-slab ray resolved to " + lowerFrontActual
                        + "; expected " + slabPos.toShortString());
            } else if (!slabPos.toShortString().equals(undersideActual)) {
                caseEVerdict.set("RED: underside-adjacent visible side-slab ray resolved to " + undersideActual
                        + "; expected " + slabPos.toShortString());
            } else {
                caseEVerdict.set("GREEN: visible side-slab rays resolved to slabPos across the comfort sweep");
            }
        });

        writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "bs-fb-05s interaction integrity",
                "BS-FB-0.5S side aim resolves to side slab; supporting BS break keeps FB and side slab lowered.",
                testId,
                testId,
                List.of(
                        new NoteField("supportPos", supportPos.toShortString()),
                        new NoteField("fullPos", fullPos.toShortString()),
                        new NoteField("slabPos", slabPos.toShortString()),
                        new NoteField("caseA_topHalfActionResult", caseAActionResult.get()),
                        new NoteField("caseA_topHalfPlacedState", caseAPlacedState.get()),
                        new NoteField("caseA_verdict", caseAVerdict.get()),
                        new NoteField("caseB_centerResolved", caseBCenterResolved.get()),
                        new NoteField("caseB_upperResolved", caseBUpperResolved.get()),
                        new NoteField("caseB_lowerResolved", caseBLowerResolved.get()),
                        new NoteField("caseB_verdict", caseBVerdict.get()),
                        new NoteField("caseC_initialFbDy", caseCInitialFbDy.get()),
                        new NoteField("caseC_initialSlabDy", caseCInitialSlabDy.get()),
                        new NoteField("caseC_initialFbEffectiveVisualY", caseCInitialFbEffectiveVisualY.get()),
                        new NoteField("caseC_initialSlabEffectiveVisualY", caseCInitialSlabEffectiveVisualY.get()),
                        new NoteField("caseC_initialAssemblyVisibleY", caseCInitialAssemblyVisibleY.get()),
                        new NoteField("caseC_postBreakFbDy", caseCPostBreakFbDy.get()),
                        new NoteField("caseC_postBreakSlabDy", caseCPostBreakSlabDy.get()),
                        new NoteField("caseC_postBreakFbEffectiveVisualY", caseCPostBreakFbEffectiveVisualY.get()),
                        new NoteField("caseC_postBreakSlabEffectiveVisualY", caseCPostBreakSlabEffectiveVisualY.get()),
                        new NoteField("caseC_postBreakAssemblyVisibleY", caseCPostBreakAssemblyVisibleY.get()),
                        new NoteField("caseC_assemblyVisibleDeltaY", caseCAssemblyVisibleDeltaY.get()),
                        new NoteField("caseC_assemblyShiftedUp", caseCAssemblyShiftedUp.get()),
                        new NoteField("caseC_postBreakFbState", caseCPostBreakFbState.get()),
                        new NoteField("caseC_verdict", caseCVerdict.get()),
                        new NoteField("caseC_serverAnchorAfterPlace", caseCServerAnchorAfterPlace.get()),
                        new NoteField("caseC_serverFbStateAfterPlace", caseCServerFbStateAfterPlace.get()),
                        new NoteField("caseC_serverAnchorAfterBreak", caseCServerAnchorAfterBreak.get()),
                        new NoteField("caseC_serverFbStateAfterBreak", caseCServerFbStateAfterBreak.get()),
                        new NoteField("caseC_clientDyImmediateAfterPlace", caseCClientDyImmediateAfterPlace.get()),
                        new NoteField("caseC_clientAnchoredImmediateAfterPlace", caseCClientAnchoredImmediateAfterPlace.get()),
                        new NoteField("caseC_clientDyAfter1Tick", caseCClientDyAfter1Tick.get()),
                        new NoteField("caseC_clientAnchoredAfter1Tick", caseCClientAnchoredAfter1Tick.get()),
                        new NoteField("caseC_clientDyAfterRender", caseCClientDyAfterRender.get()),
                        new NoteField("caseC_clientAnchoredAfterRender", caseCClientAnchoredAfterRender.get()),
                        new NoteField("caseC_clientDyImmediateAfterBreak", caseCClientDyImmediateAfterBreak.get()),
                        new NoteField("caseC_clientAnchoredImmediateAfterBreak", caseCClientAnchoredImmediateAfterBreak.get()),
                        new NoteField("caseC_clientDyAfter1TickBreak", caseCClientDyAfter1TickBreak.get()),
                        new NoteField("caseC_clientAnchoredAfter1TickBreak", caseCClientAnchoredAfter1TickBreak.get()),
                        new NoteField("caseD_lowerCenterResolved", caseDLowerCenterResolved.get()),
                        new NoteField("caseD_lowerEdgeResolved", caseDLowerEdgeResolved.get()),
                        new NoteField("caseD_verdict", caseDSelectionVerdict.get()),
                        new NoteField("caseF_slabHeldLowerCenterResolved", caseFSlabHeldLowerCenterResolved.get()),
                        new NoteField("caseF_slabHeldLowerEdgeResolved", caseFSlabHeldLowerEdgeResolved.get()),
                        new NoteField("caseF_slabHeldGuardCause", caseFSlabHeldGuardCause.get()),
                        new NoteField("caseF_slabHeldVerdict", caseFSlabHeldVerdict.get()),
                        new NoteField("caseE_centerResolved", caseECenterResolved.get()),
                        new NoteField("caseE_centerVerdict", caseECenterVerdict.get()),
                        new NoteField("caseE_lowerFrontResolved", caseELowerFrontResolved.get()),
                        new NoteField("caseE_lowerFrontVerdict", caseELowerFrontVerdict.get()),
                        new NoteField("caseE_undersideResolved", caseEUndersideResolved.get()),
                        new NoteField("caseE_undersideVerdict", caseEUndersideVerdict.get()),
                        new NoteField("caseE_verdict", caseEVerdict.get())
                ),
                !caseBVerdict.get().startsWith("RED")
                        && !caseCVerdict.get().startsWith("RED")
                        && !caseDSelectionVerdict.get().startsWith("RED")
                        && !caseFSlabHeldVerdict.get().startsWith("RED")
                        && !caseEVerdict.get().startsWith("RED"));

        // Fail after notes are written so observations are persisted even when red.
        StringBuilder failMsg = new StringBuilder();
        if (caseBVerdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] caseB ").append(caseBVerdict.get()).append("\n");
        }
        if (caseCVerdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] caseC expected FB dy stays at -0.5 across BS removal;"
                    + " initialFbDy=").append(caseCInitialFbDy.get())
                    .append(" initialSlabDy=").append(caseCInitialSlabDy.get())
                    .append(" initialFbEffectiveVisualY=").append(caseCInitialFbEffectiveVisualY.get())
                    .append(" initialSlabEffectiveVisualY=").append(caseCInitialSlabEffectiveVisualY.get())
                    .append(" postBreakFbDy=").append(caseCPostBreakFbDy.get())
                    .append(" postBreakSlabDy=").append(caseCPostBreakSlabDy.get())
                    .append(" postBreakFbEffectiveVisualY=").append(caseCPostBreakFbEffectiveVisualY.get())
                    .append(" postBreakSlabEffectiveVisualY=").append(caseCPostBreakSlabEffectiveVisualY.get())
                    .append(" assemblyVisibleDeltaY=").append(caseCAssemblyVisibleDeltaY.get())
                    .append(" assemblyShiftedUp=").append(caseCAssemblyShiftedUp.get())
                    .append(" postBreakFbState=").append(caseCPostBreakFbState.get()).append("\n");
        }
        if (caseDSelectionVerdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] caseD ").append(caseDSelectionVerdict.get()).append("\n");
        }
        if (caseFSlabHeldVerdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] caseF ").append(caseFSlabHeldVerdict.get())
                    .append(" guardCause=").append(caseFSlabHeldGuardCause.get()).append("\n");
        }
        if (caseEVerdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] caseE ").append(caseEVerdict.get())
                    .append(" center=").append(caseECenterResolved.get()).append(" (").append(caseECenterVerdict.get()).append(")")
                    .append(" lowerFront=").append(caseELowerFrontResolved.get()).append(" (").append(caseELowerFrontVerdict.get()).append(")")
                    .append(" underside=").append(caseEUndersideResolved.get()).append(" (").append(caseEUndersideVerdict.get()).append(")").append("\n");
        }
        if (failMsg.length() > 0) {
            throw new RuntimeException(failMsg.toString().trim());
        }
    }

    /**
     * BS-FB-1S top-half placement law proof (RED PROOF — captures missing
     * behavior).
     *
     * <p>Fixture: bottom slab (BS) at supportPos, ordinary full block (FB) at
     * supportPos.up() lowered onto BS (anchored, dy=-0.5). Player holds a stone
     * slab item and clicks the side face of the lowered FB at two heights.
     *
     * <p>Product law under test:
     * <ul>
     *   <li><b>Lower-half side click</b> (world Y = fullPos.y - 0.25, EAST face)
     *       must place a BOTTOM stone_slab at placePos = fullPos.east()
     *       inheriting dy = -0.5 (BS-FB-0.5S baseline; already implemented).
     *       Visual Y span: [placePos.y - 0.5, placePos.y].</li>
     *   <li><b>Upper-half side click</b> (world Y = fullPos.y + 0.25, EAST face)
     *       must place a TOP stone_slab at placePos = fullPos.east()
     *       inheriting dy = -0.5 (BS-FB-1S desired law).
     *       Visual Y span: [placePos.y, placePos.y + 0.5] — i.e. the upper
     *       half band beside the lowered FB, completing a 1-block visual
     *       column on the slab top.</li>
     * </ul>
     *
     * <p>Current implementation status: {@link com.slabbed.mixin.BlockItemPlacementIntentMixin}
     * remaps every horizontal-face hit on a lowered FB to {@code targetPos.y + 0.499}
     * unconditionally, forcing BOTTOM placement regardless of upper/lower half
     * intent. The upper-half case is therefore expected RED until a routing
     * branch differentiates the two intents.
     *
     * <p>Side-channel only: writes notes to
     * {@code bs_fb_1s_top_half_placement_law_notes.json}; does not register a
     * manifest artifact, so the canonical 9-ID bundle stays unaffected.
     *
     * <p>This proof intentionally fails the gametest suite when the upper-half
     * verdict is RED — that is the captured product-law gap.
     */
    static void runBsFb1sTopHalfPlacementLawProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        final String testId = "bs_fb_1s_top_half_placement_law";
        final BlockPos supportPos = FIXTURE_ORIGIN.add(48, 0, 0);
        final BlockPos fullPos = supportPos.up();
        final BlockPos placePos = fullPos.east();

        AtomicReference<String> lowerHalfActionResult = new AtomicReference<>("");
        AtomicReference<String> lowerHalfPlacedState = new AtomicReference<>("");
        AtomicReference<String> lowerHalfPlacedPos = new AtomicReference<>("");
        AtomicReference<String> lowerHalfSlabType = new AtomicReference<>("");
        AtomicReference<String> lowerHalfDy = new AtomicReference<>("");
        AtomicReference<String> lowerHalfVisualY = new AtomicReference<>("");
        AtomicReference<String> lowerHalfVerdict = new AtomicReference<>("audit-only");

        AtomicReference<String> upperHalfActionResult = new AtomicReference<>("");
        AtomicReference<String> upperHalfPlacedState = new AtomicReference<>("");
        AtomicReference<String> upperHalfPlacedPos = new AtomicReference<>("");
        AtomicReference<String> upperHalfSlabType = new AtomicReference<>("");
        AtomicReference<String> upperHalfDy = new AtomicReference<>("");
        AtomicReference<String> upperHalfVisualY = new AtomicReference<>("");
        AtomicReference<String> upperHalfVerdict = new AtomicReference<>("audit-only");

        // ── Setup: BS support, lowered FB, slab in hand, clean placePos ─────────
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
                throw new RuntimeException("singleplayer server player list empty for bs-fb-1s placement proof");
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });

        ctx.runOnClient(mc -> {
            if (mc.player == null) {
                throw new RuntimeException("client player null during bs-fb-1s placement setup");
            }
            mc.player.refreshPositionAndAngles(
                    fullPos.getX() + 0.5,
                    fullPos.getY() + 1.95,
                    fullPos.getZ() + 3.25,
                    180.0f,
                    24.0f);
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        // ── Lower-half side click → expect BS-FB-0.5S (BOTTOM at placePos, dy=-0.5) ─
        // Lowered FB visual spans world Y ∈ [fullPos.y - 0.5, fullPos.y + 0.5];
        // lower half center is world Y = fullPos.y - 0.25.
        final BlockHitResult lowerHalfHit = new BlockHitResult(
                new Vec3d(fullPos.getX() + 1.0, fullPos.getY() - 0.25, fullPos.getZ() + 0.5),
                Direction.EAST,
                fullPos,
                false,
                false);

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for bs-fb-1s lower-half click");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, lowerHalfHit);
            lowerHalfActionResult.set(result.toString());
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during bs-fb-1s lower-half readback");
            }
            BlockState placed = mc.world.getBlockState(placePos);
            lowerHalfPlacedState.set(placed.toString());
            lowerHalfPlacedPos.set(placePos.toShortString());
            String typeLabel;
            if (placed.contains(SlabBlock.TYPE)) {
                typeLabel = placed.get(SlabBlock.TYPE).toString();
            } else {
                typeLabel = "none";
            }
            lowerHalfSlabType.set(typeLabel);
            double dy = SlabSupport.getYOffset(mc.world, placePos, placed);
            lowerHalfDy.set(Double.toString(dy));
            VoxelShape outline = placed.getOutlineShape(mc.world, placePos, ShapeContext.absent());
            if (outline.isEmpty()) {
                lowerHalfVisualY.set("empty");
            } else {
                double minY = placePos.getY() + outline.getBoundingBox().minY;
                double maxY = placePos.getY() + outline.getBoundingBox().maxY;
                lowerHalfVisualY.set(formatYRange(minY, maxY));
            }

            boolean isStoneSlab = placed.isOf(Blocks.STONE_SLAB);
            boolean isBottom = placed.contains(SlabBlock.TYPE)
                    && placed.get(SlabBlock.TYPE) == SlabType.BOTTOM;
            boolean dyOk = Math.abs(dy - (-0.5)) < 1.0e-6;
            if (isStoneSlab && isBottom && dyOk) {
                lowerHalfVerdict.set("GREEN: BS-FB-0.5S baseline — BOTTOM stone_slab at "
                        + placePos.toShortString() + " with dy=-0.5");
            } else {
                lowerHalfVerdict.set("RED: BS-FB-0.5S baseline regressed — placed=" + placed
                        + " type=" + typeLabel + " dy=" + dy);
            }
        });

        // ── Reset placePos for upper-half click ────────────────────────────────
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(placePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        // ── Upper-half side click → expect BS-FB-1S (TOP at placePos, dy=-0.5) ──
        // Upper half center is world Y = fullPos.y + 0.25.
        final BlockHitResult upperHalfHit = new BlockHitResult(
                new Vec3d(fullPos.getX() + 1.0, fullPos.getY() + 0.25, fullPos.getZ() + 0.5),
                Direction.EAST,
                fullPos,
                false,
                false);

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for bs-fb-1s upper-half click");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, upperHalfHit);
            upperHalfActionResult.set(result.toString());
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during bs-fb-1s upper-half readback");
            }
            // Probe both placePos and placePos.up() — current behavior may push
            // the slab into placePos.up() if vanilla face routing kicks in for
            // the upper-half hit.
            BlockState atPlace = mc.world.getBlockState(placePos);
            BlockState atPlaceUp = mc.world.getBlockState(placePos.up());
            BlockPos resolvedPos;
            BlockState resolvedState;
            if (atPlace.isOf(Blocks.STONE_SLAB)) {
                resolvedPos = placePos;
                resolvedState = atPlace;
            } else if (atPlaceUp.isOf(Blocks.STONE_SLAB)) {
                resolvedPos = placePos.up();
                resolvedState = atPlaceUp;
            } else {
                resolvedPos = placePos;
                resolvedState = atPlace;
            }
            upperHalfPlacedState.set(resolvedState.toString());
            upperHalfPlacedPos.set(resolvedPos.toShortString());
            String typeLabel;
            if (resolvedState.contains(SlabBlock.TYPE)) {
                typeLabel = resolvedState.get(SlabBlock.TYPE).toString();
            } else {
                typeLabel = "none";
            }
            upperHalfSlabType.set(typeLabel);
            double dy = SlabSupport.getYOffset(mc.world, resolvedPos, resolvedState);
            upperHalfDy.set(Double.toString(dy));
            VoxelShape outline = resolvedState.getOutlineShape(mc.world, resolvedPos, ShapeContext.absent());
            if (outline.isEmpty()) {
                upperHalfVisualY.set("empty");
            } else {
                double minY = resolvedPos.getY() + outline.getBoundingBox().minY;
                double maxY = resolvedPos.getY() + outline.getBoundingBox().maxY;
                upperHalfVisualY.set(formatYRange(minY, maxY));
            }

            boolean isStoneSlab = resolvedState.isOf(Blocks.STONE_SLAB);
            boolean isTop = resolvedState.contains(SlabBlock.TYPE)
                    && resolvedState.get(SlabBlock.TYPE) == SlabType.TOP;
            boolean atExpectedPos = resolvedPos.equals(placePos);
            boolean dyOk = Math.abs(dy - (-0.5)) < 1.0e-6;
            if (isStoneSlab && isTop && atExpectedPos && dyOk) {
                upperHalfVerdict.set("PASS: BS-FB-1S desired — TOP stone_slab at "
                        + placePos.toShortString() + " with dy=-0.5");
            } else if (!isStoneSlab) {
                upperHalfVerdict.set("RED: BS-FB-1S not implemented — upper-half click did not place a stone_slab"
                        + " (resolvedPos=" + resolvedPos.toShortString() + " resolvedState=" + resolvedState + ")");
            } else if (!atExpectedPos) {
                upperHalfVerdict.set("RED: BS-FB-1S not implemented — slab placed at wrong position "
                        + resolvedPos.toShortString() + " (expected " + placePos.toShortString()
                        + "); type=" + typeLabel + " dy=" + dy);
            } else if (!isTop) {
                upperHalfVerdict.set("RED: BS-FB-1S not implemented — upper-half click placed "
                        + typeLabel + " slab at " + resolvedPos.toShortString()
                        + " (expected TOP for 1S law); dy=" + dy);
            } else {
                upperHalfVerdict.set("RED: BS-FB-1S dy mismatch — TOP slab at "
                        + resolvedPos.toShortString() + " has dy=" + dy + " (expected -0.5)");
            }
        });

        writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "bs-fb-1s top-half placement law",
                "Lower-half side click places BOTTOM slab (0.5S, dy=-0.5); "
                        + "upper-half side click places TOP slab (1S, dy=-0.5) at the same side position.",
                testId,
                testId,
                List.of(
                        new NoteField("supportPos", supportPos.toShortString()),
                        new NoteField("fullPos", fullPos.toShortString()),
                        new NoteField("placePos", placePos.toShortString()),
                        new NoteField("lowerHalf_actionResult", lowerHalfActionResult.get()),
                        new NoteField("lowerHalf_placedPos", lowerHalfPlacedPos.get()),
                        new NoteField("lowerHalf_placedState", lowerHalfPlacedState.get()),
                        new NoteField("lowerHalf_slabType", lowerHalfSlabType.get()),
                        new NoteField("lowerHalf_dy", lowerHalfDy.get()),
                        new NoteField("lowerHalf_visualY", lowerHalfVisualY.get()),
                        new NoteField("lowerHalf_verdict", lowerHalfVerdict.get()),
                        new NoteField("upperHalf_actionResult", upperHalfActionResult.get()),
                        new NoteField("upperHalf_placedPos", upperHalfPlacedPos.get()),
                        new NoteField("upperHalf_placedState", upperHalfPlacedState.get()),
                        new NoteField("upperHalf_slabType", upperHalfSlabType.get()),
                        new NoteField("upperHalf_dy", upperHalfDy.get()),
                        new NoteField("upperHalf_visualY", upperHalfVisualY.get()),
                        new NoteField("upperHalf_verdict", upperHalfVerdict.get())
                ),
                !lowerHalfVerdict.get().startsWith("RED")
                        && !upperHalfVerdict.get().startsWith("RED"));

        // Fail after notes are written so observations are persisted even when red.
        StringBuilder failMsg = new StringBuilder();
        if (lowerHalfVerdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] lowerHalf ").append(lowerHalfVerdict.get())
                    .append(" actionResult=").append(lowerHalfActionResult.get())
                    .append(" placedState=").append(lowerHalfPlacedState.get())
                    .append(" slabType=").append(lowerHalfSlabType.get())
                    .append(" dy=").append(lowerHalfDy.get())
                    .append(" visualY=").append(lowerHalfVisualY.get()).append("\n");
        }
        if (upperHalfVerdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] upperHalf ").append(upperHalfVerdict.get())
                    .append(" actionResult=").append(upperHalfActionResult.get())
                    .append(" placedPos=").append(upperHalfPlacedPos.get())
                    .append(" placedState=").append(upperHalfPlacedState.get())
                    .append(" slabType=").append(upperHalfSlabType.get())
                    .append(" dy=").append(upperHalfDy.get())
                    .append(" visualY=").append(upperHalfVisualY.get()).append("\n");
        }
        if (failMsg.length() > 0) {
            throw new RuntimeException(failMsg.toString().trim());
        }
    }

    /**
     * BS-FB-0.5S live placement intent proof.
     *
     * <p>Uses the same real east-side player ray that produced the live repro
     * {@code hitY=201.5000}: BS support, anchored lowered FB, slab in hand,
     * crosshair/raycast against the vanilla outline. RED if the player-intended
     * 0.5S side-slab placement lands as TOP instead of BOTTOM.
     */
    static void runBsFb05sLivePlacementIntentProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        final String testId = "bs_fb_05s_live_placement_intent";
        final BlockPos supportPos = FIXTURE_ORIGIN.add(44, 0, 0);
        final BlockPos fullPos = supportPos.up();
        final BlockPos placePos = fullPos.east();
        final double eyeOffset = 1.62;
        final double eyeX = fullPos.getX() + 2.5;
        final double eyeY = fullPos.getY() + 0.5;
        final double eyeZ = fullPos.getZ() + 0.5;
        final double feetY = eyeY - eyeOffset;
        final Vec3d eye = new Vec3d(eyeX, eyeY, eyeZ);
        final Vec3d dir = new Vec3d(-1.0, 0.0, 0.0);

        AtomicReference<String> initialTarget = new AtomicReference<>("pending");
        AtomicReference<String> hitVec = new AtomicReference<>("pending");
        AtomicReference<String> hitY = new AtomicReference<>("pending");
        AtomicReference<String> intendedClassification = new AtomicReference<>(
                "player-intended 0.5S side slab -> expected BOTTOM");
        AtomicReference<BlockHitResult> liveHit = new AtomicReference<>(null);
        AtomicReference<String> actionResult = new AtomicReference<>("not_run");
        AtomicReference<String> placedPos = new AtomicReference<>(placePos.toShortString());
        AtomicReference<String> placedState = new AtomicReference<>("not_checked");
        AtomicReference<String> slabType = new AtomicReference<>("not_checked");
        AtomicReference<String> slabDy = new AtomicReference<>("not_checked");
        AtomicReference<String> fbAnchored = new AtomicReference<>("not_checked");
        AtomicReference<String> verdict = new AtomicReference<>("BLOCKED");

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, fullPos, world.getBlockState(fullPos));
            fbAnchored.set(Boolean.toString(SlabAnchorAttachment.isAnchored(world, fullPos)));
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer server player list empty for " + testId);
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });

        ctx.runOnClient(mc -> {
            if (mc.player == null) {
                throw new RuntimeException("client player null during " + testId + " setup");
            }
            mc.player.refreshPositionAndAngles(eyeX, feetY, eyeZ, 90.0f, 0.0f);
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                initialTarget.set("null_world_or_player");
                verdict.set("BLOCKED: client world/player unavailable for live ray");
                return;
            }
            Vec3d end = eye.add(dir.multiply(4.5));
            BlockHitResult hit = mc.world.raycast(new RaycastContext(
                    eye, end,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) {
                initialTarget.set("MISS");
                hitVec.set("MISS");
                hitY.set("MISS");
                verdict.set("BLOCKED: live ray missed lowered FB");
                return;
            }
            liveHit.set(hit);
            initialTarget.set(hit.getBlockPos().toShortString() + " face=" + hit.getSide().asString());
            hitVec.set(String.format("%.4f, %.4f, %.4f", hit.getPos().x, hit.getPos().y, hit.getPos().z));
            hitY.set(String.format("%.4f", hit.getPos().y));
            if (Math.abs(hit.getPos().y - (fullPos.getY() + 0.5)) < 1.0e-6) {
                intendedClassification.set(
                        "player-intended 0.5S/BOTTOM, but real ray hits vanilla midpoint/top boundary");
            }
        });

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                verdict.set("BLOCKED: client interaction path unavailable");
                return;
            }
            BlockHitResult hit = liveHit.get();
            if (hit == null) {
                actionResult.set("MISS_NO_CLICK");
                placedState.set(mc.world.getBlockState(placePos).toString());
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            actionResult.set(result.toString());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during " + testId + " readback");
            }
            BlockState placed = mc.world.getBlockState(placePos);
            placedState.set(placed.toString());
            if (placed.contains(SlabBlock.TYPE)) {
                slabType.set(placed.get(SlabBlock.TYPE).toString());
            } else {
                slabType.set("none");
            }
            double dy = SlabSupport.getYOffset(mc.world, placePos, placed);
            slabDy.set(Double.toString(dy));

            boolean isBottom = placed.isOf(Blocks.STONE_SLAB)
                    && placed.contains(SlabBlock.TYPE)
                    && placed.get(SlabBlock.TYPE) == SlabType.BOTTOM;
            boolean isTop = placed.isOf(Blocks.STONE_SLAB)
                    && placed.contains(SlabBlock.TYPE)
                    && placed.get(SlabBlock.TYPE) == SlabType.TOP;
            if (isBottom) {
                verdict.set("PASS: live intended 0.5S path placed BOTTOM");
            } else if (isTop) {
                verdict.set("RED: live intended 0.5S path placed TOP");
            } else {
                verdict.set("RED: live intended 0.5S path did not place BOTTOM; placed=" + placed);
            }
        });

        writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "bs-fb-0.5s live placement intent",
                "A live player-intended 0.5S side-slab ray against the anchored lowered FB should place "
                        + "BOTTOM at the side position; TOP means the real ray is being classified by vanilla block space.",
                testId,
                testId,
                List.of(
                        new NoteField("supportPos", supportPos.toShortString()),
                        new NoteField("fullPos", fullPos.toShortString()),
                        new NoteField("fbAnchored", fbAnchored.get()),
                        new NoteField("initialTarget", initialTarget.get()),
                        new NoteField("hitVec", hitVec.get()),
                        new NoteField("hitY", hitY.get()),
                        new NoteField("intendedClassification", intendedClassification.get()),
                        new NoteField("actionResult", actionResult.get()),
                        new NoteField("placedPos", placedPos.get()),
                        new NoteField("placedState", placedState.get()),
                        new NoteField("slabType", slabType.get()),
                        new NoteField("slabDy", slabDy.get()),
                        new NoteField("verdict", verdict.get())
                ),
                !verdict.get().startsWith("RED")
                        && !verdict.get().startsWith("BLOCKED"));

        if (verdict.get().startsWith("RED") || verdict.get().startsWith("BLOCKED")) {
            throw new RuntimeException("[" + testId + "] " + verdict.get()
                    + " initialTarget=" + initialTarget.get()
                    + " hitVec=" + hitVec.get()
                    + " hitY=" + hitY.get()
                    + " placedPos=" + placedPos.get()
                    + " placedState=" + placedState.get()
                    + " slabType=" + slabType.get()
                    + " slabDy=" + slabDy.get());
        }
    }

    /**
     * BS-FB-1S live-regression proof: post-support-removal stability + fresh
     * 0.5S placement after the original BS support has been broken.
     *
     * <p>Captures Julia's live observation after b72629c
     * (save/bs-fb-1s-top-half-placement):
     * <ol>
     *   <li>Existing BS-FB-0.5S systems should remain visually stable when the
     *       supporting BS is broken (FB and side slab dy stay -0.5, slab type
     *       stays BOTTOM, assembly visual Y does not rise).</li>
     *   <li>A fresh BOTTOM (0.5S) side slab placed via lower-half side click
     *       on the still-anchored lowered FB should remain BOTTOM with dy=-0.5
     *       across 1, 2, 5, and 10 ticks of settle (no BOTTOM→TOP type drift,
     *       no upward dy/visualY jump).</li>
     * </ol>
     *
     * <p>Side-channel only: writes
     * {@code bs_fb_1s_live_regression_after_bs_break_notes.json}; does not
     * register manifest artifacts.
     *
     * <p>Stops the gametest suite (RED) if the regression is reproduced — that
     * is the captured proof of Julia's live failure.
     */
    static void runBsFb1sLiveRegressionProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        final String testId = "bs_fb_1s_live_regression_after_bs_break";
        final BlockPos supportPos = FIXTURE_ORIGIN.add(56, 0, 0);
        final BlockPos fullPos = supportPos.up();
        final BlockPos slabPos = fullPos.east();

        // ── Case 1 state ───────────────────────────────────────────────────────
        AtomicReference<String> case1FbDyPre = new AtomicReference<>("");
        AtomicReference<String> case1FbVisualYPre = new AtomicReference<>("");
        AtomicReference<String> case1SlabDyPre = new AtomicReference<>("");
        AtomicReference<String> case1SlabTypePre = new AtomicReference<>("");
        AtomicReference<String> case1SlabVisualYPre = new AtomicReference<>("");
        AtomicReference<String> case1AssemblyYPre = new AtomicReference<>("");
        AtomicReference<String> case1FbDyPost = new AtomicReference<>("");
        AtomicReference<String> case1FbVisualYPost = new AtomicReference<>("");
        AtomicReference<String> case1SlabDyPost = new AtomicReference<>("");
        AtomicReference<String> case1SlabTypePost = new AtomicReference<>("");
        AtomicReference<String> case1SlabVisualYPost = new AtomicReference<>("");
        AtomicReference<String> case1AssemblyYPost = new AtomicReference<>("");
        AtomicReference<String> case1AssemblyDeltaY = new AtomicReference<>("");
        AtomicReference<String> case1ServerAnchorPost = new AtomicReference<>("");
        AtomicReference<String> case1Verdict = new AtomicReference<>("audit-only");

        // ── Case 2 state ───────────────────────────────────────────────────────
        AtomicReference<String> case2FbAnchoredPostBreak = new AtomicReference<>("");
        AtomicReference<String> case2FbDyPostBreak = new AtomicReference<>("");
        AtomicReference<String> case2FbStatePostBreak = new AtomicReference<>("");
        AtomicReference<String> case2FbStatePreClick = new AtomicReference<>("");
        AtomicReference<String> case2FbStateAfterPlace = new AtomicReference<>("");
        AtomicReference<String> case2FbStateBeforeBreak = new AtomicReference<>("");
        AtomicReference<String> case2FbStateImmediatePostBreak = new AtomicReference<>("");
        AtomicReference<String> case2FbStateBreakTick1 = new AtomicReference<>("");
        AtomicReference<String> case2FbStateBreakTick2 = new AtomicReference<>("");
        AtomicReference<String> case2FbStateBreakTick3 = new AtomicReference<>("");
        AtomicReference<String> case2FbStateBreakTick4 = new AtomicReference<>("");
        AtomicReference<String> case2FbStateBreakTick5 = new AtomicReference<>("");
        AtomicReference<String> case2FbStateBreakTick6 = new AtomicReference<>("");
        AtomicReference<String> case2PlaceActionResult = new AtomicReference<>("");
        AtomicReference<String> case2Tick0Type = new AtomicReference<>("");
        AtomicReference<String> case2Tick0Dy = new AtomicReference<>("");
        AtomicReference<String> case2Tick0VisualY = new AtomicReference<>("");
        AtomicReference<String> case2Tick1Type = new AtomicReference<>("");
        AtomicReference<String> case2Tick1Dy = new AtomicReference<>("");
        AtomicReference<String> case2Tick1VisualY = new AtomicReference<>("");
        AtomicReference<String> case2Tick2Type = new AtomicReference<>("");
        AtomicReference<String> case2Tick2Dy = new AtomicReference<>("");
        AtomicReference<String> case2Tick2VisualY = new AtomicReference<>("");
        AtomicReference<String> case2Tick5Type = new AtomicReference<>("");
        AtomicReference<String> case2Tick5Dy = new AtomicReference<>("");
        AtomicReference<String> case2Tick5VisualY = new AtomicReference<>("");
        AtomicReference<String> case2Tick10Type = new AtomicReference<>("");
        AtomicReference<String> case2Tick10Dy = new AtomicReference<>("");
        AtomicReference<String> case2Tick10VisualY = new AtomicReference<>("");
        AtomicReference<String> case2Verdict = new AtomicReference<>("audit-only");
        AtomicReference<String> case2SlabScanT0 = new AtomicReference<>("");
        AtomicReference<String> case2SlabScanT10 = new AtomicReference<>("");

        // ───────────────────────────────────────────────────────────────────────
        // Case 1: existing BS-FB-0.5S post-break stability (natural placement
        // path so Block.onPlaced creates the FB anchor — mirrors live play).
        // ───────────────────────────────────────────────────────────────────────
        // 1a) BS support via setBlockState (vanilla slabs need no anchor).
        //     Clear fullPos and slabPos so subsequent natural placements land cleanly.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(slabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(slabPos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        });

        // 1b) Place FB via natural up-face click on the BS so Block.onPlaced
        //     fires and SlabAnchorAttachment.addAnchor records the anchor.
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.refreshPositionAndAngles(
                        supportPos.getX() + 0.5,
                        supportPos.getY() + 2.5,
                        supportPos.getZ() + 0.5,
                        0.0f,
                        90.0f);
                mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 4));
            }
        });
        singleplayer.getServer().runOnServer(server -> {
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                        Hand.MAIN_HAND, new ItemStack(Items.STONE, 4));
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockHitResult fbPlaceHit = new BlockHitResult(
                new Vec3d(supportPos.getX() + 0.5,
                        supportPos.getY() + 0.5,
                        supportPos.getZ() + 0.5),
                Direction.UP,
                supportPos,
                false,
                false);
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for case 1 natural FB placement");
            }
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, fbPlaceHit);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        // 1c) Place BOTTOM side slab at slabPos via natural east-face click on
        //     the now-anchored lowered FB (lower-half hit → BlockItemPlacement
        //     IntentMixin remaps to BOTTOM at slabPos with adjacent-rule dy=-0.5).
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
                mc.player.refreshPositionAndAngles(
                        fullPos.getX() + 0.5,
                        fullPos.getY() + 1.95,
                        fullPos.getZ() + 3.25,
                        180.0f,
                        24.0f);
            }
        });
        singleplayer.getServer().runOnServer(server -> {
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                        Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockHitResult sidePlaceHit = new BlockHitResult(
                new Vec3d(fullPos.getX() + 1.0, fullPos.getY() - 0.25, fullPos.getZ() + 0.5),
                Direction.EAST,
                fullPos,
                false,
                false);
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for case 1 natural side slab placement");
            }
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, sidePlaceHit);
        });

        // Settle: 4 ticks + chunk render.
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during case 1 pre-break read");
            }
            BlockState fbState = mc.world.getBlockState(fullPos);
            BlockState slabState = mc.world.getBlockState(slabPos);
            double fbDy = SlabSupport.getYOffset(mc.world, fullPos, fbState);
            double slabDy = SlabSupport.getYOffset(mc.world, slabPos, slabState);
            VoxelShape fbOutline = fbState.getOutlineShape(mc.world, fullPos, ShapeContext.absent());
            VoxelShape slabOutline = slabState.getOutlineShape(mc.world, slabPos, ShapeContext.absent());
            double fbMinY = fullPos.getY() + (fbOutline.isEmpty() ? 0.0 : fbOutline.getBoundingBox().minY);
            double fbMaxY = fullPos.getY() + (fbOutline.isEmpty() ? 0.0 : fbOutline.getBoundingBox().maxY);
            double slabMinY = slabPos.getY() + (slabOutline.isEmpty() ? 0.0 : slabOutline.getBoundingBox().minY);
            double slabMaxY = slabPos.getY() + (slabOutline.isEmpty() ? 0.0 : slabOutline.getBoundingBox().maxY);
            case1FbDyPre.set(Double.toString(fbDy));
            case1FbVisualYPre.set(formatYRange(fbMinY, fbMaxY));
            case1SlabDyPre.set(Double.toString(slabDy));
            case1SlabTypePre.set(slabState.contains(SlabBlock.TYPE)
                    ? slabState.get(SlabBlock.TYPE).toString() : "none");
            case1SlabVisualYPre.set(formatYRange(slabMinY, slabMaxY));
            case1AssemblyYPre.set(formatYRange(Math.min(fbMinY, slabMinY), Math.max(fbMaxY, slabMaxY)));
        });

        // Break supporting BS.
        singleplayer.getServer().runOnServer(server -> {
            server.getOverworld().breakBlock(supportPos, false);
        });

        // Settle: 6 ticks + chunk render.
        for (int i = 0; i < 6; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            case1ServerAnchorPost.set(Boolean.toString(SlabAnchorAttachment.isAnchored(server.getOverworld(), fullPos)));
        });

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during case 1 post-break read");
            }
            BlockState fbState = mc.world.getBlockState(fullPos);
            BlockState slabState = mc.world.getBlockState(slabPos);
            double fbDy = SlabSupport.getYOffset(mc.world, fullPos, fbState);
            double slabDy = SlabSupport.getYOffset(mc.world, slabPos, slabState);
            VoxelShape fbOutline = fbState.getOutlineShape(mc.world, fullPos, ShapeContext.absent());
            VoxelShape slabOutline = slabState.getOutlineShape(mc.world, slabPos, ShapeContext.absent());
            double fbMinY = fullPos.getY() + (fbOutline.isEmpty() ? 0.0 : fbOutline.getBoundingBox().minY);
            double fbMaxY = fullPos.getY() + (fbOutline.isEmpty() ? 0.0 : fbOutline.getBoundingBox().maxY);
            double slabMinY = slabPos.getY() + (slabOutline.isEmpty() ? 0.0 : slabOutline.getBoundingBox().minY);
            double slabMaxY = slabPos.getY() + (slabOutline.isEmpty() ? 0.0 : slabOutline.getBoundingBox().maxY);
            case1FbDyPost.set(Double.toString(fbDy));
            case1FbVisualYPost.set(formatYRange(fbMinY, fbMaxY));
            case1SlabDyPost.set(Double.toString(slabDy));
            case1SlabTypePost.set(slabState.contains(SlabBlock.TYPE)
                    ? slabState.get(SlabBlock.TYPE).toString() : "none");
            case1SlabVisualYPost.set(formatYRange(slabMinY, slabMaxY));
            double assemblyMinPost = Math.min(fbMinY, slabMinY);
            double assemblyMaxPost = Math.max(fbMaxY, slabMaxY);
            case1AssemblyYPost.set(formatYRange(assemblyMinPost, assemblyMaxPost));
            double assemblyMinPre = parseRangeMin(case1AssemblyYPre.get(), assemblyMinPost);
            double assemblyMaxPre = parseRangeMax(case1AssemblyYPre.get(), assemblyMaxPost);
            case1AssemblyDeltaY.set(formatYRange(assemblyMinPost - assemblyMinPre, assemblyMaxPost - assemblyMaxPre));

            String preType = case1SlabTypePre.get();
            String postType = case1SlabTypePost.get();
            boolean assemblyRose = (assemblyMinPost - assemblyMinPre) > 0.001
                    || (assemblyMaxPost - assemblyMaxPre) > 0.001;
            boolean fbDyRegressed = Math.abs(fbDy - (-0.5)) > 1.0e-6;
            boolean slabDyRegressed = Math.abs(slabDy - (-0.5)) > 1.0e-6;
            boolean slabTypeChanged = !preType.equals(postType);

            if (fbDyRegressed) {
                case1Verdict.set("RED: FB dy drifted from -0.5 to " + fbDy + " after BS break");
            } else if (slabDyRegressed) {
                case1Verdict.set("RED: side slab dy drifted from -0.5 to " + slabDy + " after BS break");
            } else if (slabTypeChanged) {
                case1Verdict.set("RED: side slab type changed " + preType + "→" + postType + " after BS break");
            } else if (assemblyRose) {
                case1Verdict.set("RED: assembly visual Y rose after BS break"
                        + " preY=" + case1AssemblyYPre.get()
                        + " postY=" + case1AssemblyYPost.get()
                        + " deltaY=" + case1AssemblyDeltaY.get());
            } else {
                case1Verdict.set("GREEN: BS-FB-0.5S stable across BS break — FB dy=-0.5, slab dy=-0.5,"
                        + " slab type=" + postType + ", assemblyY=" + case1AssemblyYPost.get());
            }
        });

        // ───────────────────────────────────────────────────────────────────────
        // Case 2: fresh 0.5S placement after support removal
        // ───────────────────────────────────────────────────────────────────────
        // Setup uses setBlockState + an explicit SlabAnchorAttachment.addAnchor
        // call to mirror the post-natural-placement state without depending on
        // player positioning surviving from Case 1. This is exactly the
        // server-side state Block.onPlaced + BlockOnPlacedAnchorMixin would
        // produce after the player places STONE on the BS naturally.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(slabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(slabPos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            // Mimic Block.onPlaced → BlockOnPlacedAnchorMixin → addAnchor.
            SlabAnchorAttachment.addAnchor(world, fullPos, world.getBlockState(fullPos));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        // Capture FB state IMMEDIATELY after the synthetic anchor setup.
        singleplayer.getServer().runOnServer(server -> {
            case2FbStateAfterPlace.set(server.getOverworld().getBlockState(fullPos).toString());
        });

        // Settle anchor sync + render.
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        // Teleport the player FAR away so any harness auto-tick interaction
        // cannot target the FB or its column. Isolates the FB-removal path
        // from any player-position-driven interaction.
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
                mc.player.refreshPositionAndAngles(
                        fullPos.getX() + 100.0,
                        fullPos.getY() + 50.0,
                        fullPos.getZ() + 100.0,
                        180.0f,
                        0.0f);
                mc.player.setVelocity(Vec3d.ZERO);
            }
        });
        singleplayer.getServer().runOnServer(server -> {
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                var p = server.getPlayerManager().getPlayerList().get(0);
                p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
                p.refreshPositionAndAngles(
                        fullPos.getX() + 100.0,
                        fullPos.getY() + 50.0,
                        fullPos.getZ() + 100.0,
                        180.0f,
                        0.0f);
                p.setVelocity(Vec3d.ZERO);
            }
        });
        ctx.waitTick();

        // FB state right before BS break.
        singleplayer.getServer().runOnServer(server -> {
            case2FbStateBeforeBreak.set(server.getOverworld().getBlockState(fullPos).toString());
        });

        // Break BS support; FB should remain anchored at dy=-0.5.
        singleplayer.getServer().runOnServer(server -> {
            server.getOverworld().breakBlock(supportPos, false);
        });

        // Capture FB state immediately after the breakBlock call (still in same
        // server tick).
        singleplayer.getServer().runOnServer(server -> {
            case2FbStateImmediatePostBreak.set(server.getOverworld().getBlockState(fullPos).toString());
        });

        // Per-tick captures during the settle window.
        ctx.waitTick();
        singleplayer.getServer().runOnServer(server -> {
            case2FbStateBreakTick1.set(server.getOverworld().getBlockState(fullPos).toString());
        });
        ctx.waitTick();
        singleplayer.getServer().runOnServer(server -> {
            case2FbStateBreakTick2.set(server.getOverworld().getBlockState(fullPos).toString());
        });
        ctx.waitTick();
        singleplayer.getServer().runOnServer(server -> {
            case2FbStateBreakTick3.set(server.getOverworld().getBlockState(fullPos).toString());
        });
        ctx.waitTick();
        singleplayer.getServer().runOnServer(server -> {
            case2FbStateBreakTick4.set(server.getOverworld().getBlockState(fullPos).toString());
        });
        ctx.waitTick();
        singleplayer.getServer().runOnServer(server -> {
            case2FbStateBreakTick5.set(server.getOverworld().getBlockState(fullPos).toString());
        });
        ctx.waitTick();
        singleplayer.getServer().runOnServer(server -> {
            case2FbStateBreakTick6.set(server.getOverworld().getBlockState(fullPos).toString());
        });
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during case 2 post-break-pre-place read");
            }
            BlockState fbState = mc.world.getBlockState(fullPos);
            case2FbAnchoredPostBreak.set(Boolean.toString(SlabAnchorAttachment.isAnchored(mc.world, fullPos)));
            case2FbDyPostBreak.set(Double.toString(SlabSupport.getYOffset(mc.world, fullPos, fbState)));
            case2FbStatePostBreak.set(fbState.toString());
        });

        // Synthesize a lower-half side click. Hit Y is the *original* world-Y on
        // the lowered visual lower half: targetPos.y - 0.25.
        final BlockHitResult lowerHalfHit = new BlockHitResult(
                new Vec3d(fullPos.getX() + 1.0, fullPos.getY() - 0.25, fullPos.getZ() + 0.5),
                Direction.EAST,
                fullPos,
                false,
                false);

        // Reposition the player right before the click to defeat any fall
        // accumulation from the post-break settle window — keep player within
        // reach of the synthesized hit so the server accepts the placement.
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.refreshPositionAndAngles(
                        fullPos.getX() + 0.5,
                        fullPos.getY() + 1.95,
                        fullPos.getZ() + 3.25,
                        180.0f,
                        24.0f);
                mc.player.setVelocity(Vec3d.ZERO);
            }
        });
        singleplayer.getServer().runOnServer(server -> {
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                var p = server.getPlayerManager().getPlayerList().get(0);
                p.refreshPositionAndAngles(
                        fullPos.getX() + 0.5,
                        fullPos.getY() + 1.95,
                        fullPos.getZ() + 3.25,
                        180.0f,
                        24.0f);
                p.setVelocity(Vec3d.ZERO);
            }
        });
        ctx.waitTick();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null pre-click in case 2");
            }
            case2FbStatePreClick.set(mc.world.getBlockState(fullPos).toString());
        });

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for case 2 lower-half click");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, lowerHalfHit);
            case2PlaceActionResult.set(result.toString());
        });

        // Sample at tick 0 (immediate, no waitTick yet beyond the click submission).
        ctx.runOnClient(mc -> slabbed$captureSlabSample(mc, slabPos,
                case2Tick0Type, case2Tick0Dy, case2Tick0VisualY));
        ctx.runOnClient(mc -> case2SlabScanT0.set(slabbed$scanForStoneSlabs(mc, fullPos, 5, 3)));

        ctx.waitTick();
        ctx.runOnClient(mc -> slabbed$captureSlabSample(mc, slabPos,
                case2Tick1Type, case2Tick1Dy, case2Tick1VisualY));

        ctx.waitTick();
        ctx.runOnClient(mc -> slabbed$captureSlabSample(mc, slabPos,
                case2Tick2Type, case2Tick2Dy, case2Tick2VisualY));

        for (int i = 0; i < 3; i++) {
            ctx.waitTick();
        }
        ctx.runOnClient(mc -> slabbed$captureSlabSample(mc, slabPos,
                case2Tick5Type, case2Tick5Dy, case2Tick5VisualY));

        for (int i = 0; i < 5; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> slabbed$captureSlabSample(mc, slabPos,
                case2Tick10Type, case2Tick10Dy, case2Tick10VisualY));
        ctx.runOnClient(mc -> case2SlabScanT10.set(slabbed$scanForStoneSlabs(mc, fullPos, 5, 3)));

        // Case 2 verdict — distinguish the live-mechanism failure (FB removed
        // post-BS-break, anchor lingers without block, fresh slab lands AT the
        // now-empty fullPos with dy=0.0) from a pure type/visual drift.
        ctx.runOnClient(mc -> {
            String[] tickLabels = {"0", "1", "2", "5", "10"};
            String[] types = {case2Tick0Type.get(), case2Tick1Type.get(), case2Tick2Type.get(),
                    case2Tick5Type.get(), case2Tick10Type.get()};
            String[] dys = {case2Tick0Dy.get(), case2Tick1Dy.get(), case2Tick2Dy.get(),
                    case2Tick5Dy.get(), case2Tick10Dy.get()};
            String[] visualYs = {case2Tick0VisualY.get(), case2Tick1VisualY.get(),
                    case2Tick2VisualY.get(), case2Tick5VisualY.get(), case2Tick10VisualY.get()};

            String fbStatePostBreak = case2FbStatePostBreak.get();
            String fbStatePreClick = case2FbStatePreClick.get();
            boolean fbRemovedAfterBreak = fbStatePostBreak.contains("minecraft:air")
                    || fbStatePreClick.contains("minecraft:air");

            String reason = null;
            for (int i = 0; i < tickLabels.length; i++) {
                if (!"bottom".equals(types[i])) {
                    reason = "tick " + tickLabels[i] + " type=" + types[i] + " (expected bottom)";
                    break;
                }
                if (!"-0.5".equals(dys[i])) {
                    reason = "tick " + tickLabels[i] + " dy=" + dys[i] + " (expected -0.5)";
                    break;
                }
                double minY = parseRangeMin(visualYs[i], Double.NaN);
                double maxY = parseRangeMax(visualYs[i], Double.NaN);
                double expectedMin = slabPos.getY() - 0.5;
                double expectedMax = slabPos.getY();
                if (Math.abs(minY - expectedMin) > 0.001 || Math.abs(maxY - expectedMax) > 0.001) {
                    reason = "tick " + tickLabels[i] + " visualY=" + visualYs[i]
                            + " (expected " + formatYRange(expectedMin, expectedMax) + ")";
                    break;
                }
            }

            if (reason != null) {
                String prefix = fbRemovedAfterBreak
                        ? "RED: FB at fullPos was REMOVED after BS break (live-regression mechanism) — "
                                + "fbStatePostBreak=" + fbStatePostBreak
                                + " fbStatePreClick=" + fbStatePreClick
                                + " — "
                        : "RED: fresh 0.5S placement drifted across settle — ";
                case2Verdict.set(prefix + reason
                        + " | tick0=" + types[0] + "/" + dys[0] + "/" + visualYs[0]
                        + " tick1=" + types[1] + "/" + dys[1] + "/" + visualYs[1]
                        + " tick2=" + types[2] + "/" + dys[2] + "/" + visualYs[2]
                        + " tick5=" + types[3] + "/" + dys[3] + "/" + visualYs[3]
                        + " tick10=" + types[4] + "/" + dys[4] + "/" + visualYs[4]);
            } else if (fbRemovedAfterBreak) {
                case2Verdict.set("RED: FB at fullPos was REMOVED after BS break even though slab placement "
                        + "happened to read consistent values — fbStatePostBreak=" + fbStatePostBreak
                        + " fbStatePreClick=" + fbStatePreClick);
            } else {
                case2Verdict.set("GREEN: fresh 0.5S placement stayed BOTTOM with dy=-0.5 and "
                        + "visualY=" + (slabPos.getY() - 0.5) + ".." + slabPos.getY() + " across 0/1/2/5/10 ticks; "
                        + "FB stayed STONE at fullPos throughout (fbStatePostBreak=" + fbStatePostBreak + ")");
            }
        });

        writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "bs-fb-1s live regression after bs break",
                "BS-FB-0.5S stays stable across BS break (no rise, no type change); fresh 0.5S "
                        + "placement after BS break stays BOTTOM with dy=-0.5 across 0/1/2/5/10 ticks.",
                testId,
                testId,
                List.of(
                        new NoteField("supportPos", supportPos.toShortString()),
                        new NoteField("fullPos", fullPos.toShortString()),
                        new NoteField("slabPos", slabPos.toShortString()),
                        new NoteField("case1_fbDyPre", case1FbDyPre.get()),
                        new NoteField("case1_fbVisualYPre", case1FbVisualYPre.get()),
                        new NoteField("case1_slabDyPre", case1SlabDyPre.get()),
                        new NoteField("case1_slabTypePre", case1SlabTypePre.get()),
                        new NoteField("case1_slabVisualYPre", case1SlabVisualYPre.get()),
                        new NoteField("case1_assemblyYPre", case1AssemblyYPre.get()),
                        new NoteField("case1_serverAnchorPost", case1ServerAnchorPost.get()),
                        new NoteField("case1_fbDyPost", case1FbDyPost.get()),
                        new NoteField("case1_fbVisualYPost", case1FbVisualYPost.get()),
                        new NoteField("case1_slabDyPost", case1SlabDyPost.get()),
                        new NoteField("case1_slabTypePost", case1SlabTypePost.get()),
                        new NoteField("case1_slabVisualYPost", case1SlabVisualYPost.get()),
                        new NoteField("case1_assemblyYPost", case1AssemblyYPost.get()),
                        new NoteField("case1_assemblyDeltaY", case1AssemblyDeltaY.get()),
                        new NoteField("case1_verdict", case1Verdict.get()),
                        new NoteField("case2_fbStateAfterPlace", case2FbStateAfterPlace.get()),
                        new NoteField("case2_fbStateBeforeBreak", case2FbStateBeforeBreak.get()),
                        new NoteField("case2_fbStateImmediatePostBreak", case2FbStateImmediatePostBreak.get()),
                        new NoteField("case2_fbStateBreakTick1", case2FbStateBreakTick1.get()),
                        new NoteField("case2_fbStateBreakTick2", case2FbStateBreakTick2.get()),
                        new NoteField("case2_fbStateBreakTick3", case2FbStateBreakTick3.get()),
                        new NoteField("case2_fbStateBreakTick4", case2FbStateBreakTick4.get()),
                        new NoteField("case2_fbStateBreakTick5", case2FbStateBreakTick5.get()),
                        new NoteField("case2_fbStateBreakTick6", case2FbStateBreakTick6.get()),
                        new NoteField("case2_fbAnchoredPostBreak", case2FbAnchoredPostBreak.get()),
                        new NoteField("case2_fbDyPostBreak", case2FbDyPostBreak.get()),
                        new NoteField("case2_fbStatePostBreak", case2FbStatePostBreak.get()),
                        new NoteField("case2_fbStatePreClick", case2FbStatePreClick.get()),
                        new NoteField("case2_placeActionResult", case2PlaceActionResult.get()),
                        new NoteField("case2_tick0_type", case2Tick0Type.get()),
                        new NoteField("case2_tick0_dy", case2Tick0Dy.get()),
                        new NoteField("case2_tick0_visualY", case2Tick0VisualY.get()),
                        new NoteField("case2_tick1_type", case2Tick1Type.get()),
                        new NoteField("case2_tick1_dy", case2Tick1Dy.get()),
                        new NoteField("case2_tick1_visualY", case2Tick1VisualY.get()),
                        new NoteField("case2_tick2_type", case2Tick2Type.get()),
                        new NoteField("case2_tick2_dy", case2Tick2Dy.get()),
                        new NoteField("case2_tick2_visualY", case2Tick2VisualY.get()),
                        new NoteField("case2_tick5_type", case2Tick5Type.get()),
                        new NoteField("case2_tick5_dy", case2Tick5Dy.get()),
                        new NoteField("case2_tick5_visualY", case2Tick5VisualY.get()),
                        new NoteField("case2_tick10_type", case2Tick10Type.get()),
                        new NoteField("case2_tick10_dy", case2Tick10Dy.get()),
                        new NoteField("case2_tick10_visualY", case2Tick10VisualY.get()),
                        new NoteField("case2_slabScan_tick0", case2SlabScanT0.get()),
                        new NoteField("case2_slabScan_tick10", case2SlabScanT10.get()),
                        new NoteField("case2_verdict", case2Verdict.get())
                ),
                !case1Verdict.get().startsWith("RED")
                        && !case2Verdict.get().startsWith("RED"));

        StringBuilder failMsg = new StringBuilder();
        if (case1Verdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] case1 ").append(case1Verdict.get()).append("\n");
        }
        if (case2Verdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] case2 ").append(case2Verdict.get()).append("\n");
        }
        if (failMsg.length() > 0) {
            throw new RuntimeException(failMsg.toString().trim());
        }
    }

    /**
     * BSFB+ top-support law proof (RED PROOF — captures missing inheritance for
     * objects placed on top of a lowered FB).
     *
     * <p>Fixture: bottom slab (BS) at supportPos, ordinary stone full block (FB)
     * at supportPos.up() anchored/lowered (dy=-0.5), object placed at fullPos.up().
     *
     * <p>Product law under test: an object placed on top of a lowered FB should
     * sit on the FB's visible lowered top surface (i.e. inherit dy=-0.5 so its
     * visual bottom lands at fullPos.y + 0.5), not float at the vanilla y+1
     * surface (visual bottom at fullPos.y + 1.0).
     *
     * <p>Representative set:
     * <ul>
     *   <li><b>Case A — stacked full block</b>: stone block at fullPos.up().
     *       Records dy, anchored, outline minY/maxY at +2 ticks; then breaks BS
     *       and re-reads at +5 ticks. RED if topDy != -0.5 (current vanilla
     *       behavior expected).</li>
     *   <li><b>Case B — torch on top</b>: floor torch at fullPos.up(). Same
     *       observation cadence. RED if topDy != -0.5.</li>
     * </ul>
     *
     * <p>Existing BS-FB law verification: Case A also records FB anchored/dy
     * before and after BS break. The existing fix should keep FB dy=-0.5 and
     * anchored=true after BS removal (post-7429aec law). Documented but not
     * gated as a verdict here — that is the BS-FB-0.5S baseline tested elsewhere.
     *
     * <p>Side-channel only: writes
     * {@code bsfb_plus_top_support_law_notes.json}; does not register manifest
     * artifacts so the canonical proof bundle stays unaffected.
     *
     * <p>Stops the gametest suite (RED) if either case fails — that is the
     * captured product-law gap.
     */
    static void runBsfbPlusTopSupportLawProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        final String testId = "bsfb_plus_top_support_law";
        final BlockPos supportPos = FIXTURE_ORIGIN.add(64, 0, 0);
        final BlockPos fullPos = supportPos.up();
        final BlockPos topPos = fullPos.up();

        // Case A state
        AtomicReference<String> caseAFbDy = new AtomicReference<>("");
        AtomicReference<String> caseAFbAnchoredPre = new AtomicReference<>("");
        AtomicReference<String> caseAFbVisualY = new AtomicReference<>("");
        AtomicReference<String> caseATopState = new AtomicReference<>("");
        AtomicReference<String> caseATopDy = new AtomicReference<>("");
        AtomicReference<String> caseATopAnchoredPre = new AtomicReference<>("");
        AtomicReference<String> caseATopOutlineY = new AtomicReference<>("");
        AtomicReference<String> caseATopVisualYTick2 = new AtomicReference<>("");
        AtomicReference<String> caseAFbDyPostBreak = new AtomicReference<>("");
        AtomicReference<String> caseAFbAnchoredPostBreak = new AtomicReference<>("");
        AtomicReference<String> caseATopDyPostBreak = new AtomicReference<>("");
        AtomicReference<String> caseATopVisualYPostBreak = new AtomicReference<>("");
        AtomicReference<String> caseAVerdict = new AtomicReference<>("audit-only");

        // Case B state
        AtomicReference<String> caseBTopState = new AtomicReference<>("");
        AtomicReference<String> caseBTopDy = new AtomicReference<>("");
        AtomicReference<String> caseBTopOutlineY = new AtomicReference<>("");
        AtomicReference<String> caseBTopVisualYTick2 = new AtomicReference<>("");
        AtomicReference<String> caseBTopDyPostBreak = new AtomicReference<>("");
        AtomicReference<String> caseBTopVisualYPostBreak = new AtomicReference<>("");
        AtomicReference<String> caseBVerdict = new AtomicReference<>("audit-only");

        // ── Case A setup: BS + anchored FB ───────────────────────────────────
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(topPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            // Mimic Block.onPlaced → BlockOnPlacedAnchorMixin → addAnchor.
            SlabAnchorAttachment.addAnchor(world, fullPos, world.getBlockState(fullPos));
        });
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        // Place stacked full block on top.
        singleplayer.getServer().runOnServer(server -> {
            server.getOverworld().setBlockState(topPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        });
        for (int i = 0; i < 2; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during BSFB+ caseA tick2 read");
            }
            BlockState fbState = mc.world.getBlockState(fullPos);
            BlockState topState = mc.world.getBlockState(topPos);
            double fbDy = SlabSupport.getYOffset(mc.world, fullPos, fbState);
            double topDy = SlabSupport.getYOffset(mc.world, topPos, topState);
            VoxelShape fbOut = fbState.getOutlineShape(mc.world, fullPos, ShapeContext.absent());
            VoxelShape topOut = topState.getOutlineShape(mc.world, topPos, ShapeContext.absent());
            double fbMinY = fullPos.getY() + (fbOut.isEmpty() ? 0.0 : fbOut.getBoundingBox().minY);
            double fbMaxY = fullPos.getY() + (fbOut.isEmpty() ? 0.0 : fbOut.getBoundingBox().maxY);
            double topMinY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().minY);
            double topMaxY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().maxY);
            caseAFbDy.set(Double.toString(fbDy));
            caseAFbVisualY.set(formatYRange(fbMinY, fbMaxY));
            caseATopState.set(topState.toString());
            caseATopDy.set(Double.toString(topDy));
            caseATopOutlineY.set(formatYRange(
                    topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().minY,
                    topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().maxY));
            caseATopVisualYTick2.set(formatYRange(topMinY, topMaxY));
        });
        singleplayer.getServer().runOnServer(server -> {
            caseAFbAnchoredPre.set(Boolean.toString(SlabAnchorAttachment.isAnchored(server.getOverworld(), fullPos)));
            caseATopAnchoredPre.set(Boolean.toString(SlabAnchorAttachment.isAnchored(server.getOverworld(), topPos)));
        });

        // Break BS support; observe FB anchor persistence + top object state.
        singleplayer.getServer().runOnServer(server -> {
            server.getOverworld().breakBlock(supportPos, false);
        });
        for (int i = 0; i < 5; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during BSFB+ caseA postBreak read");
            }
            BlockState fbState = mc.world.getBlockState(fullPos);
            BlockState topState = mc.world.getBlockState(topPos);
            double fbDy = SlabSupport.getYOffset(mc.world, fullPos, fbState);
            double topDy = SlabSupport.getYOffset(mc.world, topPos, topState);
            VoxelShape topOut = topState.getOutlineShape(mc.world, topPos, ShapeContext.absent());
            double topMinY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().minY);
            double topMaxY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().maxY);
            caseAFbDyPostBreak.set(Double.toString(fbDy));
            caseATopDyPostBreak.set(Double.toString(topDy));
            caseATopVisualYPostBreak.set(formatYRange(topMinY, topMaxY));
        });
        singleplayer.getServer().runOnServer(server -> {
            caseAFbAnchoredPostBreak.set(Boolean.toString(SlabAnchorAttachment.isAnchored(server.getOverworld(), fullPos)));
        });

        // Verdict A: top FB should inherit dy=-0.5 both while BS supports the FB
        // AND after BS removal (since the FB itself stays anchored at dy=-0.5).
        try {
            double topDyA = Double.parseDouble(caseATopDy.get());
            double topDyAPost = Double.parseDouble(caseATopDyPostBreak.get());
            boolean preOk = Math.abs(topDyA - (-0.5)) < 1.0e-6;
            boolean postOk = Math.abs(topDyAPost - (-0.5)) < 1.0e-6;
            if (preOk && postOk) {
                caseAVerdict.set("PASS: stacked FB inherits dy=-0.5 pre AND post BS-break — sits on visible top of lowered FB");
            } else if (!preOk) {
                caseAVerdict.set("RED: stacked FB has dy=" + caseATopDy.get()
                        + " pre-break (expected -0.5); visualY=" + caseATopVisualYTick2.get()
                        + " — top FB floats 0.5 above visible lowered FB top");
            } else {
                caseAVerdict.set("RED: stacked FB inheritance lost after BS break — pre dy=-0.5"
                        + " but post dy=" + caseATopDyPostBreak.get()
                        + " (expected -0.5 since FB stays anchored=" + caseAFbAnchoredPostBreak.get()
                        + " dy=" + caseAFbDyPostBreak.get() + ");"
                        + " preVisualY=" + caseATopVisualYTick2.get()
                        + " postVisualY=" + caseATopVisualYPostBreak.get()
                        + " — top FB rises 0.5 after BS break");
            }
        } catch (NumberFormatException e) {
            caseAVerdict.set("RED: caseA dy parse failure topDy=" + caseATopDy.get()
                    + " topDyPostBreak=" + caseATopDyPostBreak.get());
        }

        // ── Case B setup: same BS + anchored FB, torch on top ────────────────
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(topPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, fullPos, world.getBlockState(fullPos));
        });
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        // Place torch on top via direct setBlockState (skips placement-context
        // support checks but yields the same final state as a natural placement).
        singleplayer.getServer().runOnServer(server -> {
            server.getOverworld().setBlockState(topPos, Blocks.TORCH.getDefaultState(), Block.NOTIFY_LISTENERS);
        });
        for (int i = 0; i < 2; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during BSFB+ caseB tick2 read");
            }
            BlockState topState = mc.world.getBlockState(topPos);
            double topDy = SlabSupport.getYOffset(mc.world, topPos, topState);
            VoxelShape topOut = topState.getOutlineShape(mc.world, topPos, ShapeContext.absent());
            double topMinY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().minY);
            double topMaxY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().maxY);
            caseBTopState.set(topState.toString());
            caseBTopDy.set(Double.toString(topDy));
            caseBTopOutlineY.set(formatYRange(
                    topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().minY,
                    topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().maxY));
            caseBTopVisualYTick2.set(formatYRange(topMinY, topMaxY));
        });

        singleplayer.getServer().runOnServer(server -> {
            server.getOverworld().breakBlock(supportPos, false);
        });
        for (int i = 0; i < 5; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during BSFB+ caseB postBreak read");
            }
            BlockState topState = mc.world.getBlockState(topPos);
            double topDy = SlabSupport.getYOffset(mc.world, topPos, topState);
            VoxelShape topOut = topState.getOutlineShape(mc.world, topPos, ShapeContext.absent());
            double topMinY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().minY);
            double topMaxY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().maxY);
            caseBTopDyPostBreak.set(Double.toString(topDy));
            caseBTopVisualYPostBreak.set(formatYRange(topMinY, topMaxY));
        });

        try {
            double topDyB = Double.parseDouble(caseBTopDy.get());
            double topDyBPost = Double.parseDouble(caseBTopDyPostBreak.get());
            boolean preOk = Math.abs(topDyB - (-0.5)) < 1.0e-6;
            boolean postOk = Math.abs(topDyBPost - (-0.5)) < 1.0e-6;
            if (preOk && postOk) {
                caseBVerdict.set("PASS: torch on lowered FB inherits dy=-0.5 pre AND post BS-break");
            } else if (!preOk) {
                caseBVerdict.set("RED: torch has dy=" + caseBTopDy.get()
                        + " pre-break (expected -0.5); visualY=" + caseBTopVisualYTick2.get()
                        + " — torch sits at vanilla support top instead of visible lowered FB top");
            } else {
                caseBVerdict.set("RED: torch inheritance lost after BS break — pre dy=-0.5"
                        + " but post dy=" + caseBTopDyPostBreak.get() + " (expected -0.5);"
                        + " preVisualY=" + caseBTopVisualYTick2.get()
                        + " postVisualY=" + caseBTopVisualYPostBreak.get()
                        + " — torch rises 0.5 after BS break");
            }
        } catch (NumberFormatException e) {
            caseBVerdict.set("RED: caseB dy parse failure topDy=" + caseBTopDy.get()
                    + " topDyPostBreak=" + caseBTopDyPostBreak.get());
        }

        // ── Cleanup so subsequent fixtures start clean ───────────────────────
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(topPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(supportPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.removeAnchor(world, fullPos);
        });

        writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "bsfb-plus top-support law",
                "Object placed on top of a lowered FB should sit on the FB's visible lowered "
                        + "top surface (inherit dy=-0.5), not float at the vanilla y+1 surface.",
                testId,
                testId,
                List.of(
                        new NoteField("supportPos", supportPos.toShortString()),
                        new NoteField("fullPos", fullPos.toShortString()),
                        new NoteField("topPos", topPos.toShortString()),
                        new NoteField("caseA_fb_dy", caseAFbDy.get()),
                        new NoteField("caseA_fb_anchored_pre", caseAFbAnchoredPre.get()),
                        new NoteField("caseA_fb_visualY", caseAFbVisualY.get()),
                        new NoteField("caseA_top_state", caseATopState.get()),
                        new NoteField("caseA_top_dy", caseATopDy.get()),
                        new NoteField("caseA_top_anchored_pre", caseATopAnchoredPre.get()),
                        new NoteField("caseA_top_outlineY_local", caseATopOutlineY.get()),
                        new NoteField("caseA_top_visualY_tick2", caseATopVisualYTick2.get()),
                        new NoteField("caseA_fb_dy_postBreak_5t", caseAFbDyPostBreak.get()),
                        new NoteField("caseA_fb_anchored_postBreak_5t", caseAFbAnchoredPostBreak.get()),
                        new NoteField("caseA_top_dy_postBreak_5t", caseATopDyPostBreak.get()),
                        new NoteField("caseA_top_visualY_postBreak_5t", caseATopVisualYPostBreak.get()),
                        new NoteField("caseA_verdict", caseAVerdict.get()),
                        new NoteField("caseB_top_state", caseBTopState.get()),
                        new NoteField("caseB_top_dy", caseBTopDy.get()),
                        new NoteField("caseB_top_outlineY_local", caseBTopOutlineY.get()),
                        new NoteField("caseB_top_visualY_tick2", caseBTopVisualYTick2.get()),
                        new NoteField("caseB_top_dy_postBreak_5t", caseBTopDyPostBreak.get()),
                        new NoteField("caseB_top_visualY_postBreak_5t", caseBTopVisualYPostBreak.get()),
                        new NoteField("caseB_verdict", caseBVerdict.get())
                ),
                !caseAVerdict.get().startsWith("RED")
                        && !caseBVerdict.get().startsWith("RED"));

        StringBuilder failMsg = new StringBuilder();
        if (caseAVerdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] caseA ").append(caseAVerdict.get()).append("\n");
        }
        if (caseBVerdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] caseB ").append(caseBVerdict.get()).append("\n");
        }
        if (failMsg.length() > 0) {
            throw new RuntimeException(failMsg.toString().trim());
        }
    }

    /**
     * BS-FB-1S top-support law proof (RED PROOF — captures missing top-face
     * support on the lowered full-height side slab).
     *
     * <p>Fixture geometry (Julia shorthand: BS-FB-1S+):
     * <ul>
     *   <li>{@code bsPos}   — bottom slab (BS) at FIXTURE_ORIGIN+(72,0,0).</li>
     *   <li>{@code fbPos}   — stone full block (FB) at bsPos.up(), anchored
     *       (dy=-0.5, visible top at fbPos.y+0.5).</li>
     *   <li>{@code slabPos} — TOP stone slab at fbPos.east() — the BS-FB-1S
     *       slab, lowered by adjacent-slab law (dy=-0.5, visible top at
     *       slabPos.y+0.5 = same height as FB visible top).</li>
     *   <li>{@code topPos}  — object placed at slabPos.up(), under test.</li>
     * </ul>
     *
     * <p>Product law under test: an object placed on top of the lowered 1S
     * slab should sit on the slab's visible lowered top face (dy=-0.5, visual
     * bottom at slabPos.y+0.5), not float at the vanilla slabPos.y+1 surface.
     * The law must hold both while the original BS exists and after it is
     * broken (since the FB stays anchored and the 1S slab stays lowered).
     *
     * <p>Representative set:
     * <ul>
     *   <li><b>Case A — chain</b>: CHAIN, AXIS=Y at topPos.</li>
     *   <li><b>Case B — torch</b>: floor torch at topPos.</li>
     * </ul>
     *
     * <p>Side-channel only: writes {@code bs_fb_1s_top_support_law_notes.json}.
     * Does not register manifest artifacts.
     *
     * <p>Stops the gametest suite (RED) if either case fails — that is the
     * captured product-law gap.
     */
    static void runBsFb1sTopSupportLawProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        final String testId  = "bs_fb_1s_top_support_law";
        final BlockPos bsPos   = FIXTURE_ORIGIN.add(72, 0, 0);
        final BlockPos fbPos   = bsPos.up();
        final BlockPos slabPos = fbPos.east();
        final BlockPos topPos  = slabPos.up();

        // Case A state
        AtomicReference<String> caseASlabDyPre          = new AtomicReference<>("");
        AtomicReference<String> caseASlabVisualYPre      = new AtomicReference<>("");
        AtomicReference<String> caseATopState            = new AtomicReference<>("");
        AtomicReference<String> caseATopDy               = new AtomicReference<>("");
        AtomicReference<String> caseATopOutlineY         = new AtomicReference<>("");
        AtomicReference<String> caseATopVisualYTick2     = new AtomicReference<>("");
        AtomicReference<String> caseASlabDyPostBreak     = new AtomicReference<>("");
        AtomicReference<String> caseAFbAnchoredPostBreak = new AtomicReference<>("");
        AtomicReference<String> caseATopDyPostBreak      = new AtomicReference<>("");
        AtomicReference<String> caseATopVisualYPostBreak = new AtomicReference<>("");
        AtomicReference<String> caseAVerdict             = new AtomicReference<>("audit-only");

        // Case B state
        AtomicReference<String> caseBTopState            = new AtomicReference<>("");
        AtomicReference<String> caseBTopDy               = new AtomicReference<>("");
        AtomicReference<String> caseBTopOutlineY         = new AtomicReference<>("");
        AtomicReference<String> caseBTopVisualYTick2     = new AtomicReference<>("");
        AtomicReference<String> caseBTopDyPostBreak      = new AtomicReference<>("");
        AtomicReference<String> caseBTopVisualYPostBreak = new AtomicReference<>("");
        AtomicReference<String> caseBVerdict             = new AtomicReference<>("audit-only");

        // ── Setup: BS + anchored FB + 1S slab ───────────────────────────
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(bsPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fbPos,   Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(slabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(topPos,  Blocks.AIR.getDefaultState(),   Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, fbPos, world.getBlockState(fbPos));
        });
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        // Baseline 1S slab dy (must be -0.5 for the proof to be meaningful).
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during 1S top support baseline read");
            }
            BlockState slab = mc.world.getBlockState(slabPos);
            double slabDy = SlabSupport.getYOffset(mc.world, slabPos, slab);
            VoxelShape slabOut = slab.getOutlineShape(mc.world, slabPos, ShapeContext.absent());
            double slabMinY = slabPos.getY() + (slabOut.isEmpty() ? 0.0 : slabOut.getBoundingBox().minY);
            double slabMaxY = slabPos.getY() + (slabOut.isEmpty() ? 0.0 : slabOut.getBoundingBox().maxY);
            caseASlabDyPre.set(Double.toString(slabDy));
            caseASlabVisualYPre.set(formatYRange(slabMinY, slabMaxY));
        });

        // ── Case A: chain on top of the lowered 1S slab ─────────────────
        singleplayer.getServer().runOnServer(server -> {
            server.getOverworld().setBlockState(topPos,
                    Blocks.IRON_CHAIN.getDefaultState().with(net.minecraft.block.ChainBlock.AXIS, Direction.Axis.Y),
                    Block.NOTIFY_LISTENERS);
        });
        for (int i = 0; i < 2; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during 1S top support caseA tick2 read");
            }
            BlockState top = mc.world.getBlockState(topPos);
            double topDy = SlabSupport.getYOffset(mc.world, topPos, top);
            VoxelShape topOut = top.getOutlineShape(mc.world, topPos, ShapeContext.absent());
            double topMinY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().minY);
            double topMaxY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().maxY);
            caseATopState.set(top.toString());
            caseATopDy.set(Double.toString(topDy));
            caseATopOutlineY.set(formatYRange(
                    topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().minY,
                    topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().maxY));
            caseATopVisualYTick2.set(formatYRange(topMinY, topMaxY));
        });

        singleplayer.getServer().runOnServer(server -> {
            server.getOverworld().breakBlock(bsPos, false);
        });
        for (int i = 0; i < 5; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during 1S top support caseA postBreak read");
            }
            BlockState slab = mc.world.getBlockState(slabPos);
            BlockState top  = mc.world.getBlockState(topPos);
            double slabDy = SlabSupport.getYOffset(mc.world, slabPos, slab);
            double topDy  = SlabSupport.getYOffset(mc.world, topPos, top);
            VoxelShape topOut = top.getOutlineShape(mc.world, topPos, ShapeContext.absent());
            double topMinY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().minY);
            double topMaxY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().maxY);
            caseASlabDyPostBreak.set(Double.toString(slabDy));
            caseATopDyPostBreak.set(Double.toString(topDy));
            caseATopVisualYPostBreak.set(formatYRange(topMinY, topMaxY));
        });
        singleplayer.getServer().runOnServer(server -> {
            caseAFbAnchoredPostBreak.set(Boolean.toString(
                    SlabAnchorAttachment.isAnchored(server.getOverworld(), fbPos)));
        });

        try {
            double topDyPre  = Double.parseDouble(caseATopDy.get());
            double topDyPost = Double.parseDouble(caseATopDyPostBreak.get());
            boolean preOk  = Math.abs(topDyPre  - (-0.5)) < 1.0e-6;
            boolean postOk = Math.abs(topDyPost - (-0.5)) < 1.0e-6;
            if (preOk && postOk) {
                caseAVerdict.set("PASS: chain on 1S slab has dy=-0.5 pre AND post BS-break");
            } else if (!preOk) {
                caseAVerdict.set("RED: chain has dy=" + caseATopDy.get()
                        + " pre-break (expected -0.5); slabDy=" + caseASlabDyPre.get()
                        + " slabVisualY=" + caseASlabVisualYPre.get()
                        + " objectVisualY=" + caseATopVisualYTick2.get()
                        + " — chain floats above visible 1S slab top");
            } else {
                caseAVerdict.set("RED: chain loses dy after BS break — pre dy=-0.5 but post dy="
                        + caseATopDyPostBreak.get()
                        + " (slab dy=" + caseASlabDyPostBreak.get()
                        + " fb anchored=" + caseAFbAnchoredPostBreak.get() + ");"
                        + " postVisualY=" + caseATopVisualYPostBreak.get()
                        + " — chain rises 0.5 after BS break");
            }
        } catch (NumberFormatException e) {
            caseAVerdict.set("RED: caseA dy parse failure topDy=" + caseATopDy.get());
        }

        // ── Case B: rebuild + torch ───────────────────────────────────────
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(bsPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fbPos,   Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(slabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(topPos,  Blocks.AIR.getDefaultState(),   Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, fbPos, world.getBlockState(fbPos));
        });
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            server.getOverworld().setBlockState(topPos, Blocks.TORCH.getDefaultState(), Block.NOTIFY_LISTENERS);
        });
        for (int i = 0; i < 2; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during 1S top support caseB tick2 read");
            }
            BlockState top = mc.world.getBlockState(topPos);
            double topDy = SlabSupport.getYOffset(mc.world, topPos, top);
            VoxelShape topOut = top.getOutlineShape(mc.world, topPos, ShapeContext.absent());
            double topMinY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().minY);
            double topMaxY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().maxY);
            caseBTopState.set(top.toString());
            caseBTopDy.set(Double.toString(topDy));
            caseBTopOutlineY.set(formatYRange(
                    topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().minY,
                    topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().maxY));
            caseBTopVisualYTick2.set(formatYRange(topMinY, topMaxY));
        });

        singleplayer.getServer().runOnServer(server -> {
            server.getOverworld().breakBlock(bsPos, false);
        });
        for (int i = 0; i < 5; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during 1S top support caseB postBreak read");
            }
            BlockState top = mc.world.getBlockState(topPos);
            double topDy = SlabSupport.getYOffset(mc.world, topPos, top);
            VoxelShape topOut = top.getOutlineShape(mc.world, topPos, ShapeContext.absent());
            double topMinY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().minY);
            double topMaxY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().maxY);
            caseBTopDyPostBreak.set(Double.toString(topDy));
            caseBTopVisualYPostBreak.set(formatYRange(topMinY, topMaxY));
        });

        try {
            double topDyPre  = Double.parseDouble(caseBTopDy.get());
            double topDyPost = Double.parseDouble(caseBTopDyPostBreak.get());
            boolean preOk  = Math.abs(topDyPre  - (-0.5)) < 1.0e-6;
            boolean postOk = Math.abs(topDyPost - (-0.5)) < 1.0e-6;
            if (preOk && postOk) {
                caseBVerdict.set("PASS: torch on 1S slab has dy=-0.5 pre AND post BS-break");
            } else if (!preOk) {
                caseBVerdict.set("RED: torch has dy=" + caseBTopDy.get()
                        + " pre-break (expected -0.5); objectVisualY=" + caseBTopVisualYTick2.get()
                        + " — torch floats above visible 1S slab top");
            } else {
                caseBVerdict.set("RED: torch loses dy after BS break — pre dy=-0.5 but post dy="
                        + caseBTopDyPostBreak.get()
                        + "; postVisualY=" + caseBTopVisualYPostBreak.get());
            }
        } catch (NumberFormatException e) {
            caseBVerdict.set("RED: caseB dy parse failure topDy=" + caseBTopDy.get());
        }

        // ── Case C: full block (stone) directly on lowered 1S slab ─────
        // Observational only. Records actual dy for FB-on-lowered-1S; not a
        // hard product-law assertion in this slice.
        AtomicReference<String> caseCTopDy           = new AtomicReference<>("");
        AtomicReference<String> caseCTopVisualYPre   = new AtomicReference<>("");
        AtomicReference<String> caseCTopDyPostBreak  = new AtomicReference<>("");
        AtomicReference<String> caseCTopVisualYPost  = new AtomicReference<>("");
        AtomicReference<String> caseCVerdict         = new AtomicReference<>("");
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(bsPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fbPos,   Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(slabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(topPos,  Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, fbPos, world.getBlockState(fbPos));
        });
        for (int i = 0; i < 4; i++) ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        singleplayer.getServer().runOnServer(server -> {
            server.getOverworld().setBlockState(topPos,
                    Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        });
        for (int i = 0; i < 2; i++) ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during caseC pre-break read");
            }
            BlockState top = mc.world.getBlockState(topPos);
            double topDy = SlabSupport.getYOffset(mc.world, topPos, top);
            VoxelShape topOut = top.getOutlineShape(mc.world, topPos, ShapeContext.absent());
            double topMinY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().minY);
            double topMaxY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().maxY);
            caseCTopDy.set(Double.toString(topDy));
            caseCTopVisualYPre.set(formatYRange(topMinY, topMaxY));
        });
        singleplayer.getServer().runOnServer(server -> server.getOverworld().breakBlock(bsPos, false));
        for (int i = 0; i < 5; i++) ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during caseC post-break read");
            }
            BlockState top = mc.world.getBlockState(topPos);
            double topDy = SlabSupport.getYOffset(mc.world, topPos, top);
            VoxelShape topOut = top.getOutlineShape(mc.world, topPos, ShapeContext.absent());
            double topMinY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().minY);
            double topMaxY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().maxY);
            caseCTopDyPostBreak.set(Double.toString(topDy));
            caseCTopVisualYPost.set(formatYRange(topMinY, topMaxY));
        });
        caseCVerdict.set("OBSERVED: stone on 1S dy=" + caseCTopDy.get()
                + " visualY=" + caseCTopVisualYPre.get()
                + "; postBreak dy=" + caseCTopDyPostBreak.get()
                + " visualY=" + caseCTopVisualYPost.get());

        // ── Case D: stone on 1S + chain on top of stone (stacked inheritance) ──
        // Captures whether a chain on a FB that itself sits on a lowered 1S slab
        // inherits the lowered surface or floats. RED if the chain does not
        // inherit dy=-0.5 — this is the live-gap probe.
        final BlockPos stackTopPos = topPos.up();
        AtomicReference<String> caseDStoneDy         = new AtomicReference<>("");
        AtomicReference<String> caseDStoneVisualYPre = new AtomicReference<>("");
        AtomicReference<String> caseDChainDy         = new AtomicReference<>("");
        AtomicReference<String> caseDChainVisualYPre = new AtomicReference<>("");
        AtomicReference<String> caseDStoneDyPost     = new AtomicReference<>("");
        AtomicReference<String> caseDChainDyPost     = new AtomicReference<>("");
        AtomicReference<String> caseDChainVisualYPost = new AtomicReference<>("");
        AtomicReference<String> caseDVerdict         = new AtomicReference<>("");
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(bsPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fbPos,   Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(slabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(topPos,      Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(stackTopPos, Blocks.AIR.getDefaultState(),   Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, fbPos, world.getBlockState(fbPos));
        });
        for (int i = 0; i < 4; i++) ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        singleplayer.getServer().runOnServer(server -> {
            server.getOverworld().setBlockState(stackTopPos,
                    Blocks.IRON_CHAIN.getDefaultState().with(net.minecraft.block.ChainBlock.AXIS, Direction.Axis.Y),
                    Block.NOTIFY_LISTENERS);
        });
        for (int i = 0; i < 2; i++) ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during caseD pre-break read");
            }
            BlockState stone = mc.world.getBlockState(topPos);
            BlockState chain = mc.world.getBlockState(stackTopPos);
            double stoneDy = SlabSupport.getYOffset(mc.world, topPos, stone);
            double chainDy = SlabSupport.getYOffset(mc.world, stackTopPos, chain);
            VoxelShape stoneOut = stone.getOutlineShape(mc.world, topPos, ShapeContext.absent());
            VoxelShape chainOut = chain.getOutlineShape(mc.world, stackTopPos, ShapeContext.absent());
            double stoneMinY = topPos.getY() + (stoneOut.isEmpty() ? 0.0 : stoneOut.getBoundingBox().minY);
            double stoneMaxY = topPos.getY() + (stoneOut.isEmpty() ? 0.0 : stoneOut.getBoundingBox().maxY);
            double chainMinY = stackTopPos.getY() + (chainOut.isEmpty() ? 0.0 : chainOut.getBoundingBox().minY);
            double chainMaxY = stackTopPos.getY() + (chainOut.isEmpty() ? 0.0 : chainOut.getBoundingBox().maxY);
            caseDStoneDy.set(Double.toString(stoneDy));
            caseDStoneVisualYPre.set(formatYRange(stoneMinY, stoneMaxY));
            caseDChainDy.set(Double.toString(chainDy));
            caseDChainVisualYPre.set(formatYRange(chainMinY, chainMaxY));
        });
        singleplayer.getServer().runOnServer(server -> server.getOverworld().breakBlock(bsPos, false));
        for (int i = 0; i < 5; i++) ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during caseD post-break read");
            }
            BlockState stone = mc.world.getBlockState(topPos);
            BlockState chain = mc.world.getBlockState(stackTopPos);
            double stoneDy = SlabSupport.getYOffset(mc.world, topPos, stone);
            double chainDy = SlabSupport.getYOffset(mc.world, stackTopPos, chain);
            VoxelShape chainOut = chain.getOutlineShape(mc.world, stackTopPos, ShapeContext.absent());
            double chainMinY = stackTopPos.getY() + (chainOut.isEmpty() ? 0.0 : chainOut.getBoundingBox().minY);
            double chainMaxY = stackTopPos.getY() + (chainOut.isEmpty() ? 0.0 : chainOut.getBoundingBox().maxY);
            caseDStoneDyPost.set(Double.toString(stoneDy));
            caseDChainDyPost.set(Double.toString(chainDy));
            caseDChainVisualYPost.set(formatYRange(chainMinY, chainMaxY));
        });
        try {
            double chainDyPre  = Double.parseDouble(caseDChainDy.get());
            double chainDyPost = Double.parseDouble(caseDChainDyPost.get());
            boolean preOk  = Math.abs(chainDyPre  - (-0.5)) < 1.0e-6;
            boolean postOk = Math.abs(chainDyPost - (-0.5)) < 1.0e-6;
            if (preOk && postOk) {
                caseDVerdict.set("PASS: chain on stone-on-1S inherits dy=-0.5 pre AND post BS-break"
                        + " (stoneDy pre=" + caseDStoneDy.get() + " post=" + caseDStoneDyPost.get() + ")");
            } else {
                caseDVerdict.set("RED: chain on stone-on-1S floats — chainDy pre="
                        + caseDChainDy.get() + " post=" + caseDChainDyPost.get()
                        + " (expected -0.5); stoneDy pre=" + caseDStoneDy.get()
                        + " post=" + caseDStoneDyPost.get()
                        + "; chainVisualY pre=" + caseDChainVisualYPre.get()
                        + " post=" + caseDChainVisualYPost.get()
                        + " — stacked inheritance gap");
            }
        } catch (NumberFormatException e) {
            caseDVerdict.set("RED: caseD dy parse failure chainDy=" + caseDChainDy.get());
        }

        // ── Case E: BSFB+ regression — chain on FB stacked on anchored FB ──
        // Different column to keep state isolated from cases A-D.
        final BlockPos eBsPos    = FIXTURE_ORIGIN.add(74, 0, 0);
        final BlockPos eFbLowPos = eBsPos.up();
        final BlockPos eFbHighPos = eFbLowPos.up();
        final BlockPos eChainPos = eFbHighPos.up();
        AtomicReference<String> caseEChainDy         = new AtomicReference<>("");
        AtomicReference<String> caseEChainVisualYPre = new AtomicReference<>("");
        AtomicReference<String> caseEChainDyPost     = new AtomicReference<>("");
        AtomicReference<String> caseEChainVisualYPost = new AtomicReference<>("");
        AtomicReference<String> caseEVerdict         = new AtomicReference<>("");
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(eBsPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(eFbLowPos,  Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(eFbHighPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(eChainPos,  Blocks.AIR.getDefaultState(),   Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, eFbLowPos, world.getBlockState(eFbLowPos));
        });
        for (int i = 0; i < 4; i++) ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        singleplayer.getServer().runOnServer(server -> {
            server.getOverworld().setBlockState(eChainPos,
                    Blocks.IRON_CHAIN.getDefaultState().with(net.minecraft.block.ChainBlock.AXIS, Direction.Axis.Y),
                    Block.NOTIFY_LISTENERS);
        });
        for (int i = 0; i < 2; i++) ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during caseE pre-break read");
            }
            BlockState chain = mc.world.getBlockState(eChainPos);
            double chainDy = SlabSupport.getYOffset(mc.world, eChainPos, chain);
            VoxelShape chainOut = chain.getOutlineShape(mc.world, eChainPos, ShapeContext.absent());
            double minY = eChainPos.getY() + (chainOut.isEmpty() ? 0.0 : chainOut.getBoundingBox().minY);
            double maxY = eChainPos.getY() + (chainOut.isEmpty() ? 0.0 : chainOut.getBoundingBox().maxY);
            caseEChainDy.set(Double.toString(chainDy));
            caseEChainVisualYPre.set(formatYRange(minY, maxY));
        });
        singleplayer.getServer().runOnServer(server -> server.getOverworld().breakBlock(eBsPos, false));
        for (int i = 0; i < 5; i++) ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world null during caseE post-break read");
            }
            BlockState chain = mc.world.getBlockState(eChainPos);
            double chainDy = SlabSupport.getYOffset(mc.world, eChainPos, chain);
            VoxelShape chainOut = chain.getOutlineShape(mc.world, eChainPos, ShapeContext.absent());
            double minY = eChainPos.getY() + (chainOut.isEmpty() ? 0.0 : chainOut.getBoundingBox().minY);
            double maxY = eChainPos.getY() + (chainOut.isEmpty() ? 0.0 : chainOut.getBoundingBox().maxY);
            caseEChainDyPost.set(Double.toString(chainDy));
            caseEChainVisualYPost.set(formatYRange(minY, maxY));
        });
        try {
            double pre  = Double.parseDouble(caseEChainDy.get());
            double post = Double.parseDouble(caseEChainDyPost.get());
            boolean preOk  = Math.abs(pre  - (-0.5)) < 1.0e-6;
            boolean postOk = Math.abs(post - (-0.5)) < 1.0e-6;
            if (preOk && postOk) {
                caseEVerdict.set("PASS: BSFB+ chain on FB-on-anchored-FB has dy=-0.5 pre AND post");
            } else {
                caseEVerdict.set("RED: BSFB+ regression — chainDy pre=" + caseEChainDy.get()
                        + " post=" + caseEChainDyPost.get()
                        + " (expected -0.5); visualY pre=" + caseEChainVisualYPre.get()
                        + " post=" + caseEChainVisualYPost.get());
            }
        } catch (NumberFormatException e) {
            caseEVerdict.set("RED: caseE dy parse failure chainDy=" + caseEChainDy.get());
        }
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(eChainPos,  Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(eFbHighPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(eFbLowPos,  Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(eBsPos,     Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.removeAnchor(world, eFbLowPos);
        });

        // ── Cleanup ──────────────────────────────────────────────────────
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(stackTopPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(topPos,  Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(slabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(fbPos,   Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(bsPos,   Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.removeAnchor(world, fbPos);
        });

        writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "bs-fb-1s top-support law",
                "Object placed on top of a lowered 1S (BS-FB-1S) side slab should sit on the "
                        + "slab's visible lowered top face (dy=-0.5), not float at vanilla y+1.",
                testId,
                testId,
                List.of(
                        new NoteField("bsPos",                       bsPos.toShortString()),
                        new NoteField("fbPos",                       fbPos.toShortString()),
                        new NoteField("slabPos",                     slabPos.toShortString()),
                        new NoteField("topPos",                      topPos.toShortString()),
                        new NoteField("caseA_slab_dy_pre",           caseASlabDyPre.get()),
                        new NoteField("caseA_slab_visualY_pre",      caseASlabVisualYPre.get()),
                        new NoteField("caseA_top_state",             caseATopState.get()),
                        new NoteField("caseA_top_dy",                caseATopDy.get()),
                        new NoteField("caseA_top_outlineY_local",    caseATopOutlineY.get()),
                        new NoteField("caseA_top_visualY_tick2",     caseATopVisualYTick2.get()),
                        new NoteField("caseA_slab_dy_postBreak",     caseASlabDyPostBreak.get()),
                        new NoteField("caseA_fb_anchored_postBreak", caseAFbAnchoredPostBreak.get()),
                        new NoteField("caseA_top_dy_postBreak",      caseATopDyPostBreak.get()),
                        new NoteField("caseA_top_visualY_postBreak", caseATopVisualYPostBreak.get()),
                        new NoteField("caseA_verdict",               caseAVerdict.get()),
                        new NoteField("caseB_top_state",             caseBTopState.get()),
                        new NoteField("caseB_top_dy",                caseBTopDy.get()),
                        new NoteField("caseB_top_outlineY_local",    caseBTopOutlineY.get()),
                        new NoteField("caseB_top_visualY_tick2",     caseBTopVisualYTick2.get()),
                        new NoteField("caseB_top_dy_postBreak",      caseBTopDyPostBreak.get()),
                        new NoteField("caseB_top_visualY_postBreak", caseBTopVisualYPostBreak.get()),
                        new NoteField("caseB_verdict",               caseBVerdict.get()),
                        new NoteField("caseC_top_dy",                caseCTopDy.get()),
                        new NoteField("caseC_top_visualY_pre",       caseCTopVisualYPre.get()),
                        new NoteField("caseC_top_dy_postBreak",      caseCTopDyPostBreak.get()),
                        new NoteField("caseC_top_visualY_postBreak", caseCTopVisualYPost.get()),
                        new NoteField("caseC_verdict",               caseCVerdict.get()),
                        new NoteField("caseD_stackTopPos",           stackTopPos.toShortString()),
                        new NoteField("caseD_stone_dy_pre",          caseDStoneDy.get()),
                        new NoteField("caseD_stone_visualY_pre",     caseDStoneVisualYPre.get()),
                        new NoteField("caseD_chain_dy_pre",          caseDChainDy.get()),
                        new NoteField("caseD_chain_visualY_pre",     caseDChainVisualYPre.get()),
                        new NoteField("caseD_stone_dy_postBreak",    caseDStoneDyPost.get()),
                        new NoteField("caseD_chain_dy_postBreak",    caseDChainDyPost.get()),
                        new NoteField("caseD_chain_visualY_postBreak", caseDChainVisualYPost.get()),
                        new NoteField("caseD_verdict",               caseDVerdict.get()),
                        new NoteField("caseE_eBsPos",                eBsPos.toShortString()),
                        new NoteField("caseE_eFbLowPos",             eFbLowPos.toShortString()),
                        new NoteField("caseE_eFbHighPos",            eFbHighPos.toShortString()),
                        new NoteField("caseE_eChainPos",             eChainPos.toShortString()),
                        new NoteField("caseE_chain_dy_pre",          caseEChainDy.get()),
                        new NoteField("caseE_chain_visualY_pre",     caseEChainVisualYPre.get()),
                        new NoteField("caseE_chain_dy_postBreak",    caseEChainDyPost.get()),
                        new NoteField("caseE_chain_visualY_postBreak", caseEChainVisualYPost.get()),
                        new NoteField("caseE_verdict",               caseEVerdict.get())
                ),
                !caseAVerdict.get().startsWith("RED")
                        && !caseBVerdict.get().startsWith("RED")
                        && !caseDVerdict.get().startsWith("RED")
                        && !caseEVerdict.get().startsWith("RED"));

        StringBuilder failMsg = new StringBuilder();
        if (caseAVerdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] caseA ").append(caseAVerdict.get()).append("\n");
        }
        if (caseBVerdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] caseB ").append(caseBVerdict.get()).append("\n");
        }
        if (caseDVerdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] caseD ").append(caseDVerdict.get()).append("\n");
        }
        if (caseEVerdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] caseE ").append(caseEVerdict.get()).append("\n");
        }
        if (failMsg.length() > 0) {
            throw new RuntimeException(failMsg.toString().trim());
        }
    }

    /**
     * 0.5S top-support law: object directly on top of a lowered BOTTOM
     * (BS-FB-0.5S) side slab should seat on the slab's visible lowered top
     * face. Slab cell at y+1 holds the BOTTOM slab visually shifted to
     * [y+0.5, y+1.0]; the object cell is one above, so the expected dy is
     * -1.0 (object visualY collapses onto [y+1.0, y+2.0]).
     *
     * <p>Cases:
     * <ul>
     *   <li>A — chain on lowered 0.5S (RED if dy ≠ -1.0)</li>
     *   <li>B — torch on lowered 0.5S (RED if dy ≠ -1.0)</li>
     *   <li>C — stone on lowered 0.5S (OBSERVED; record only)</li>
     * </ul>
     * Each case is read pre-BS-break and post-BS-break + settle ticks.
     * Side-channel notes only.
     */
    static void runBsFb05sTopSupportLawProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        final String testId = "bs_fb_05s_top_support_law";
        final BlockPos bsPos   = FIXTURE_ORIGIN.add(80, 0, 0);
        final BlockPos fbPos   = bsPos.up();
        final BlockPos slabPos = fbPos.east();
        final BlockPos topPos  = slabPos.up();

        AtomicReference<String> slabDyPre        = new AtomicReference<>("");
        AtomicReference<String> slabVisualYPre   = new AtomicReference<>("");
        AtomicReference<String> slabDyPost       = new AtomicReference<>("");
        AtomicReference<String> fbAnchoredPost   = new AtomicReference<>("");

        AtomicReference<String> caseATopState    = new AtomicReference<>("");
        AtomicReference<String> caseATopDy       = new AtomicReference<>("");
        AtomicReference<String> caseATopVisualY  = new AtomicReference<>("");
        AtomicReference<String> caseATopDyPost   = new AtomicReference<>("");
        AtomicReference<String> caseATopVisualYPost = new AtomicReference<>("");
        AtomicReference<String> caseAVerdict     = new AtomicReference<>("");

        AtomicReference<String> caseBTopState    = new AtomicReference<>("");
        AtomicReference<String> caseBTopDy       = new AtomicReference<>("");
        AtomicReference<String> caseBTopVisualY  = new AtomicReference<>("");
        AtomicReference<String> caseBTopDyPost   = new AtomicReference<>("");
        AtomicReference<String> caseBTopVisualYPost = new AtomicReference<>("");
        AtomicReference<String> caseBVerdict     = new AtomicReference<>("");

        AtomicReference<String> caseCTopState    = new AtomicReference<>("");
        AtomicReference<String> caseCTopDy       = new AtomicReference<>("");
        AtomicReference<String> caseCTopVisualY  = new AtomicReference<>("");
        AtomicReference<String> caseCTopDyPost   = new AtomicReference<>("");
        AtomicReference<String> caseCTopVisualYPost = new AtomicReference<>("");
        AtomicReference<String> caseCVerdict     = new AtomicReference<>("");

        // Reusable setup: BS + anchored FB + lowered BOTTOM 0.5S slab.
        Runnable setupFixture = () -> singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(bsPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fbPos,   Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(slabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(topPos,  Blocks.AIR.getDefaultState(),   Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, fbPos, world.getBlockState(fbPos));
        });

        // Helper to capture pre/post readings for a single case.
        java.util.function.BiConsumer<AtomicReference<String>[], BlockState> captureCase =
                (refs, placed) -> {
            // refs: 0=topState 1=topDy 2=topVisualY 3=topDyPost 4=topVisualYPost
            setupFixture.run();
            for (int i = 0; i < 4; i++) ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            singleplayer.getServer().runOnServer(server ->
                    server.getOverworld().setBlockState(topPos, placed, Block.NOTIFY_LISTENERS));
            for (int i = 0; i < 2; i++) ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world null during 0.5s pre-break read");
                }
                BlockState slab = mc.world.getBlockState(slabPos);
                BlockState top  = mc.world.getBlockState(topPos);
                double slabDy = SlabSupport.getYOffset(mc.world, slabPos, slab);
                double topDy  = SlabSupport.getYOffset(mc.world, topPos, top);
                VoxelShape slabOut = slab.getOutlineShape(mc.world, slabPos, ShapeContext.absent());
                VoxelShape topOut  = top.getOutlineShape(mc.world, topPos, ShapeContext.absent());
                double slabMinY = slabPos.getY() + (slabOut.isEmpty() ? 0.0 : slabOut.getBoundingBox().minY);
                double slabMaxY = slabPos.getY() + (slabOut.isEmpty() ? 0.0 : slabOut.getBoundingBox().maxY);
                double topMinY  = topPos.getY()  + (topOut.isEmpty()  ? 0.0 : topOut.getBoundingBox().minY);
                double topMaxY  = topPos.getY()  + (topOut.isEmpty()  ? 0.0 : topOut.getBoundingBox().maxY);
                slabDyPre.set(Double.toString(slabDy));
                slabVisualYPre.set(formatYRange(slabMinY, slabMaxY));
                refs[0].set(top.toString());
                refs[1].set(Double.toString(topDy));
                refs[2].set(formatYRange(topMinY, topMaxY));
            });
            singleplayer.getServer().runOnServer(server -> server.getOverworld().breakBlock(bsPos, false));
            for (int i = 0; i < 5; i++) ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world null during 0.5s post-break read");
                }
                BlockState slab = mc.world.getBlockState(slabPos);
                BlockState top  = mc.world.getBlockState(topPos);
                double slabDy = SlabSupport.getYOffset(mc.world, slabPos, slab);
                double topDy  = SlabSupport.getYOffset(mc.world, topPos, top);
                VoxelShape topOut = top.getOutlineShape(mc.world, topPos, ShapeContext.absent());
                double topMinY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().minY);
                double topMaxY = topPos.getY() + (topOut.isEmpty() ? 0.0 : topOut.getBoundingBox().maxY);
                slabDyPost.set(Double.toString(slabDy));
                refs[3].set(Double.toString(topDy));
                refs[4].set(formatYRange(topMinY, topMaxY));
            });
            singleplayer.getServer().runOnServer(server ->
                    fbAnchoredPost.set(Boolean.toString(
                            SlabAnchorAttachment.isAnchored(server.getOverworld(), fbPos))));
        };

        @SuppressWarnings("unchecked")
        AtomicReference<String>[] aRefs = new AtomicReference[]{
                caseATopState, caseATopDy, caseATopVisualY, caseATopDyPost, caseATopVisualYPost};
        captureCase.accept(aRefs, Blocks.IRON_CHAIN.getDefaultState()
                .with(net.minecraft.block.ChainBlock.AXIS, Direction.Axis.Y));
        try {
            double pre  = Double.parseDouble(caseATopDy.get());
            double post = Double.parseDouble(caseATopDyPost.get());
            boolean preOk  = Math.abs(pre  - (-1.0)) < 1.0e-6;
            boolean postOk = Math.abs(post - (-1.0)) < 1.0e-6;
            if (preOk && postOk) {
                caseAVerdict.set("PASS: chain on 0.5S has dy=-1.0 pre AND post BS-break");
            } else {
                caseAVerdict.set("RED: chain on 0.5S floats — dy pre=" + caseATopDy.get()
                        + " post=" + caseATopDyPost.get() + " (expected -1.0);"
                        + " slabDy=" + slabDyPre.get() + " slabVisualY=" + slabVisualYPre.get()
                        + " chainVisualY pre=" + caseATopVisualY.get()
                        + " post=" + caseATopVisualYPost.get());
            }
        } catch (NumberFormatException e) {
            caseAVerdict.set("RED: caseA dy parse failure topDy=" + caseATopDy.get());
        }

        @SuppressWarnings("unchecked")
        AtomicReference<String>[] bRefs = new AtomicReference[]{
                caseBTopState, caseBTopDy, caseBTopVisualY, caseBTopDyPost, caseBTopVisualYPost};
        captureCase.accept(bRefs, Blocks.TORCH.getDefaultState());
        try {
            double pre  = Double.parseDouble(caseBTopDy.get());
            double post = Double.parseDouble(caseBTopDyPost.get());
            boolean preOk  = Math.abs(pre  - (-1.0)) < 1.0e-6;
            boolean postOk = Math.abs(post - (-1.0)) < 1.0e-6;
            if (preOk && postOk) {
                caseBVerdict.set("PASS: torch on 0.5S has dy=-1.0 pre AND post BS-break");
            } else {
                caseBVerdict.set("RED: torch on 0.5S floats — dy pre=" + caseBTopDy.get()
                        + " post=" + caseBTopDyPost.get() + " (expected -1.0);"
                        + " torchVisualY pre=" + caseBTopVisualY.get()
                        + " post=" + caseBTopVisualYPost.get());
            }
        } catch (NumberFormatException e) {
            caseBVerdict.set("RED: caseB dy parse failure topDy=" + caseBTopDy.get());
        }

        @SuppressWarnings("unchecked")
        AtomicReference<String>[] cRefs = new AtomicReference[]{
                caseCTopState, caseCTopDy, caseCTopVisualY, caseCTopDyPost, caseCTopVisualYPost};
        captureCase.accept(cRefs, Blocks.STONE.getDefaultState());
        caseCVerdict.set("OBSERVED: stone on 0.5S dy pre=" + caseCTopDy.get()
                + " post=" + caseCTopDyPost.get()
                + "; stoneVisualY pre=" + caseCTopVisualY.get()
                + " post=" + caseCTopVisualYPost.get());

        // Cleanup.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(topPos,  Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(slabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(fbPos,   Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(bsPos,   Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.removeAnchor(world, fbPos);
        });

        writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "bs-fb-0.5s top-support law",
                "Object placed on top of a lowered 0.5S (BS-FB-0.5S BOTTOM) side slab should "
                        + "sit on the slab's visible lowered top face (dy=-1.0), not float at "
                        + "vanilla y+1 with a full-block gap.",
                testId,
                testId,
                List.of(
                        new NoteField("bsPos",                       bsPos.toShortString()),
                        new NoteField("fbPos",                       fbPos.toShortString()),
                        new NoteField("slabPos",                     slabPos.toShortString()),
                        new NoteField("topPos",                      topPos.toShortString()),
                        new NoteField("slab_dy_pre",                 slabDyPre.get()),
                        new NoteField("slab_visualY_pre",            slabVisualYPre.get()),
                        new NoteField("slab_dy_postBreak",           slabDyPost.get()),
                        new NoteField("fb_anchored_postBreak",       fbAnchoredPost.get()),
                        new NoteField("caseA_top_state",             caseATopState.get()),
                        new NoteField("caseA_top_dy",                caseATopDy.get()),
                        new NoteField("caseA_top_visualY",           caseATopVisualY.get()),
                        new NoteField("caseA_top_dy_postBreak",      caseATopDyPost.get()),
                        new NoteField("caseA_top_visualY_postBreak", caseATopVisualYPost.get()),
                        new NoteField("caseA_verdict",               caseAVerdict.get()),
                        new NoteField("caseB_top_state",             caseBTopState.get()),
                        new NoteField("caseB_top_dy",                caseBTopDy.get()),
                        new NoteField("caseB_top_visualY",           caseBTopVisualY.get()),
                        new NoteField("caseB_top_dy_postBreak",      caseBTopDyPost.get()),
                        new NoteField("caseB_top_visualY_postBreak", caseBTopVisualYPost.get()),
                        new NoteField("caseB_verdict",               caseBVerdict.get()),
                        new NoteField("caseC_top_state",             caseCTopState.get()),
                        new NoteField("caseC_top_dy",                caseCTopDy.get()),
                        new NoteField("caseC_top_visualY",           caseCTopVisualY.get()),
                        new NoteField("caseC_top_dy_postBreak",      caseCTopDyPost.get()),
                        new NoteField("caseC_top_visualY_postBreak", caseCTopVisualYPost.get()),
                        new NoteField("caseC_verdict",               caseCVerdict.get())
                ),
                !caseAVerdict.get().startsWith("RED")
                        && !caseBVerdict.get().startsWith("RED"));

        StringBuilder failMsg = new StringBuilder();
        if (caseAVerdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] caseA ").append(caseAVerdict.get()).append("\n");
        }
        if (caseBVerdict.get().startsWith("RED")) {
            failMsg.append("[").append(testId).append("] caseB ").append(caseBVerdict.get()).append("\n");
        }
        if (failMsg.length() > 0) {
            throw new RuntimeException(failMsg.toString().trim());
        }
    }

    /**
     * BS-FB-0.5S+ visual triad proof.
     *
     * <p>Uses the live-real side-placement ray to create the BOTTOM 0.5S slab,
     * then places a vertical chain on top and verifies dy, outline, and OUTLINE
     * raycast ownership agree across the chain's visible lower/center/top body.
     */
    static void runBsFb05sTopSupportTriadProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        final String testId = "bs_fb_05s_top_support_triad";
        final BlockPos bsPos = FIXTURE_ORIGIN.add(84, 0, 0);
        final BlockPos fbPos = bsPos.up();
        final BlockPos slabPos = fbPos.east();
        final BlockPos chainPos = slabPos.up();
        final double eyeOffset = 1.62;
        final Vec3d slabEye = new Vec3d(fbPos.getX() + 2.5, fbPos.getY() + 0.5, fbPos.getZ() + 0.5);
        final Vec3d slabRayEnd = slabEye.add(new Vec3d(-1.0, 0.0, 0.0).multiply(4.5));

        AtomicReference<String> slabHitDesc = new AtomicReference<>("pending");
        AtomicReference<BlockHitResult> slabHit = new AtomicReference<>(null);
        AtomicReference<String> slabActionResult = new AtomicReference<>("not_run");
        AtomicReference<String> slabStateText = new AtomicReference<>("not_checked");
        AtomicReference<String> slabTypeText = new AtomicReference<>("not_checked");
        AtomicReference<String> slabDyText = new AtomicReference<>("not_checked");
        AtomicReference<String> slabVisualYText = new AtomicReference<>("not_checked");

        AtomicReference<String> chainStateText = new AtomicReference<>("not_checked");
        AtomicReference<String> chainDyText = new AtomicReference<>("not_checked");
        AtomicReference<String> chainOutlineYText = new AtomicReference<>("not_checked");
        AtomicReference<String> chainVisualYText = new AtomicReference<>("not_checked");
        AtomicReference<String> lowerRayText = new AtomicReference<>("not_run");
        AtomicReference<String> centerRayText = new AtomicReference<>("not_run");
        AtomicReference<String> topRayText = new AtomicReference<>("not_run");
        AtomicReference<String> modelSeenText = new AtomicReference<>("not_checked");
        AtomicReference<String> modelViewClassText = new AtomicReference<>("not_checked");
        AtomicReference<String> modelStateText = new AtomicReference<>("not_checked");
        AtomicReference<String> modelDyText = new AtomicReference<>("not_checked");
        AtomicReference<String> modelClientDyText = new AtomicReference<>("not_checked");
        AtomicReference<String> modelSlabSupportDyText = new AtomicReference<>("not_checked");
        AtomicReference<String> modelExcludedText = new AtomicReference<>("not_checked");
        AtomicReference<String> verdict = new AtomicReference<>("BLOCKED");

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(bsPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fbPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(slabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(chainPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, fbPos, world.getBlockState(fbPos));
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer server player list empty for " + testId);
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("client unavailable during " + testId + " slab ray");
            }
            mc.player.refreshPositionAndAngles(slabEye.x, slabEye.y - eyeOffset, slabEye.z, 90.0f, 0.0f);
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            BlockHitResult hit = mc.world.raycast(new RaycastContext(
                    slabEye,
                    slabRayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            if (hit.getType() == HitResult.Type.MISS) {
                slabHitDesc.set("MISS");
                return;
            }
            slabHit.set(hit);
            slabHitDesc.set("BLOCK blockPos=" + hit.getBlockPos().toShortString()
                    + " face=" + hit.getSide().asString()
                    + " hitY=" + String.format("%.4f", hit.getPos().y));
        });

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                verdict.set("BLOCKED: client interaction path unavailable");
                return;
            }
            BlockHitResult hit = slabHit.get();
            if (hit == null) {
                slabActionResult.set("MISS_NO_CLICK");
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            slabActionResult.set(result.toString());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            System.setProperty("slabbed.render.offset.trace", "true");
            com.slabbed.client.model.OffsetBlockStateModel.resetRenderOffsetTrace(chainPos);
        });
        singleplayer.getServer().runOnServer(server -> server.getOverworld().setBlockState(
                chainPos,
                Blocks.IRON_CHAIN.getDefaultState().with(net.minecraft.block.ChainBlock.AXIS, Direction.Axis.Y),
                Block.NOTIFY_LISTENERS));
        for (int i = 0; i < 2; i++) ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        captureScreenshotAndRecord(
                ctx,
                testId + "_model_path",
                screenshotDir,
                knownScreenshotFiles,
                artifacts);

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("client unavailable during " + testId + " readback");
            }

            BlockState slabState = mc.world.getBlockState(slabPos);
            BlockState chainState = mc.world.getBlockState(chainPos);
            slabStateText.set(slabState.toString());
            slabTypeText.set(slabState.contains(SlabBlock.TYPE) ? slabState.get(SlabBlock.TYPE).toString() : "none");
            double slabDy = SlabSupport.getYOffset(mc.world, slabPos, slabState);
            slabDyText.set(Double.toString(slabDy));
            VoxelShape slabOutline = slabState.getOutlineShape(mc.world, slabPos, ShapeContext.absent());
            if (slabOutline.isEmpty()) {
                slabVisualYText.set("empty");
            } else {
                slabVisualYText.set(formatYRange(
                        slabPos.getY() + slabOutline.getBoundingBox().minY,
                        slabPos.getY() + slabOutline.getBoundingBox().maxY));
            }

            chainStateText.set(chainState.toString());
            double chainDy = SlabSupport.getYOffset(mc.world, chainPos, chainState);
            chainDyText.set(Double.toString(chainDy));
            mc.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            VoxelShape chainOutline = chainState.getOutlineShape(mc.world, chainPos, ShapeContext.absent());
            if (chainOutline.isEmpty()) {
                chainOutlineYText.set("empty");
                chainVisualYText.set("empty");
                verdict.set("BLOCKED: chain outline empty");
                return;
            }

            double outlineMinY = chainPos.getY() + chainOutline.getBoundingBox().minY;
            double outlineMaxY = chainPos.getY() + chainOutline.getBoundingBox().maxY;
            boolean outlineAlreadyOffset = chainOutline.getBoundingBox().minY < -1.0e-6
                    || chainOutline.getBoundingBox().maxY > 1.0d + 1.0e-6;
            double visualMinY = outlineAlreadyOffset ? outlineMinY : outlineMinY + chainDy;
            double visualMaxY = outlineAlreadyOffset ? outlineMaxY : outlineMaxY + chainDy;
            chainOutlineYText.set(formatYRange(outlineMinY, outlineMaxY));
            chainVisualYText.set(formatYRange(visualMinY, visualMaxY));

            double lowerY = visualMinY + 0.15d * (visualMaxY - visualMinY);
            double centerY = (visualMinY + visualMaxY) * 0.5d;
            double topY = visualMinY + 0.85d * (visualMaxY - visualMinY);
            lowerRayText.set(slabbed$describeHorizontalOutlineRay(
                    mc, chainPos, lowerY, "lower"));
            centerRayText.set(slabbed$describeHorizontalOutlineRay(
                    mc, chainPos, centerY, "center"));
            topRayText.set(slabbed$describeHorizontalOutlineRay(
                    mc, chainPos, topY, "top"));

            com.slabbed.client.model.OffsetBlockStateModel.RenderOffsetTrace modelTrace =
                    com.slabbed.client.model.OffsetBlockStateModel.snapshotRenderOffsetTrace();
            modelSeenText.set(Boolean.toString(modelTrace.seen()));
            modelViewClassText.set(modelTrace.viewClass());
            modelStateText.set(modelTrace.state());
            modelDyText.set(Double.toString(modelTrace.modelDy()));
            modelClientDyText.set(Double.toString(modelTrace.clientDy()));
            modelSlabSupportDyText.set(Double.toString(modelTrace.slabSupportDy()));
            modelExcludedText.set(Boolean.toString(modelTrace.excludedByWrapper()));
            System.clearProperty("slabbed.render.offset.trace");

            boolean slabOk = slabState.isOf(Blocks.STONE_SLAB)
                    && slabState.contains(SlabBlock.TYPE)
                    && slabState.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && Math.abs(slabDy - (-0.5d)) < 1.0e-6;
            boolean chainDyOk = Math.abs(chainDy - (-1.0d)) < 1.0e-6;
            boolean outlineMatchesVisual = Math.abs(outlineMinY - visualMinY) < 1.0e-6
                    && Math.abs(outlineMaxY - visualMaxY) < 1.0e-6;
            boolean lowerHitsChain = lowerRayText.get().contains("blockPos=" + chainPos.toShortString());
            boolean centerHitsChain = centerRayText.get().contains("blockPos=" + chainPos.toShortString());
            boolean topHitsChain = topRayText.get().contains("blockPos=" + chainPos.toShortString());

            if (!slabOk) {
                verdict.set("BLOCKED: live-real 0.5S slab setup failed");
            } else if (chainDyOk && !outlineMatchesVisual) {
                verdict.set("RED: chain dy is lowered but outline does not match model/effective visual Y");
            } else if (!lowerHitsChain || !centerHitsChain) {
                verdict.set("RED: chain selection ownership is top-only or misses lower/center visible body");
            } else if (!topHitsChain) {
                verdict.set("RED: chain top visible body ray missed");
            } else if (!modelTrace.seen()) {
                verdict.set("RED: OffsetBlockStateModel never saw lowered chain");
            } else if (chainDyOk && Math.abs(modelTrace.modelDy() - (-1.0d)) > 1.0e-6) {
                verdict.set("RED: logical/outline/raycast lowered but model path dy=" + modelTrace.modelDy());
            } else if (Math.abs(modelTrace.clientDy() - modelTrace.slabSupportDy()) > 1.0e-6) {
                verdict.set("RED: ClientDy disagrees with SlabSupport in model path");
            } else {
                verdict.set("PASS: chain dy, outline, and lower/center/top raycast agree");
            }
        });

        writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "bs-fb-0.5s-plus visual triad",
                "Live-real 0.5S+ chain must align dy, model/outline visual Y, and lower/center/top OUTLINE raycast ownership.",
                testId,
                testId,
                List.of(
                        new NoteField("bsPos", bsPos.toShortString()),
                        new NoteField("fbPos", fbPos.toShortString()),
                        new NoteField("slabHit", slabHitDesc.get()),
                        new NoteField("slabActionResult", slabActionResult.get()),
                        new NoteField("slabPos", slabPos.toShortString()),
                        new NoteField("slabState", slabStateText.get()),
                        new NoteField("slabType", slabTypeText.get()),
                        new NoteField("slabDy", slabDyText.get()),
                        new NoteField("slabVisualY", slabVisualYText.get()),
                        new NoteField("chainPos", chainPos.toShortString()),
                        new NoteField("chainState", chainStateText.get()),
                        new NoteField("chainDy", chainDyText.get()),
                        new NoteField("chainOutlineY", chainOutlineYText.get()),
                        new NoteField("chainVisualY", chainVisualYText.get()),
                        new NoteField("lowerRay", lowerRayText.get()),
                        new NoteField("centerRay", centerRayText.get()),
                        new NoteField("topRay", topRayText.get()),
                        new NoteField("modelSeen", modelSeenText.get()),
                        new NoteField("modelViewClass", modelViewClassText.get()),
                        new NoteField("modelState", modelStateText.get()),
                        new NoteField("modelDy", modelDyText.get()),
                        new NoteField("modelClientDy", modelClientDyText.get()),
                        new NoteField("modelSlabSupportDy", modelSlabSupportDyText.get()),
                        new NoteField("modelExcludedByWrapper", modelExcludedText.get()),
                        new NoteField("verdict", verdict.get())
                ),
                !verdict.get().startsWith("RED")
                        && !verdict.get().startsWith("BLOCKED"));

        if (verdict.get().startsWith("RED") || verdict.get().startsWith("BLOCKED")) {
            throw new RuntimeException("[" + testId + "] " + verdict.get()
                    + " slabType=" + slabTypeText.get()
                    + " slabDy=" + slabDyText.get()
                    + " chainDy=" + chainDyText.get()
                    + " chainOutlineY=" + chainOutlineYText.get()
                    + " lowerRay=" + lowerRayText.get()
                    + " centerRay=" + centerRayText.get()
                    + " topRay=" + topRayText.get()
                    + " modelSeen=" + modelSeenText.get()
                    + " modelDy=" + modelDyText.get()
                    + " modelView=" + modelViewClassText.get());
        }
    }

    private static String slabbed$describeHorizontalOutlineRay(
            net.minecraft.client.MinecraftClient mc,
            BlockPos targetPos,
            double y,
            String label
    ) {
        Vec3d eye = new Vec3d(targetPos.getX() + 2.5d, y, targetPos.getZ() + 0.5d);
        Vec3d target = new Vec3d(targetPos.getX() + 0.5d, y, targetPos.getZ() + 0.5d);
        resolvePlayerRaycastFromEye(mc, eye, target, 6.0);
        mc.gameRenderer.updateCrosshairTarget(0.0f);
        HitResult crosshair = mc.crosshairTarget;
        if (crosshair == null || crosshair.getType() == HitResult.Type.MISS) {
            return label + ":MISS y=" + String.format("%.4f", y);
        }
        if (!(crosshair instanceof BlockHitResult hit)) {
            return label + ":" + crosshair.getType() + " y=" + String.format("%.4f", y);
        }
        return label + ":BLOCK blockPos=" + hit.getBlockPos().toShortString()
                + " face=" + hit.getSide().asString()
                + " hitY=" + String.format("%.4f", hit.getPos().y);
    }

    /**
     * Diagnostic scan that walks an XZ {@code radius} square × {@code yRadius}
     * vertical band centred on {@code center} and reports every stone_slab it
     * finds. Used to locate where a synthesised side-click placement actually
     * landed when the expected position is empty.
     */
    private static String slabbed$scanForStoneSlabs(
            net.minecraft.client.MinecraftClient mc, BlockPos center, int radius, int yRadius
    ) {
        if (mc.world == null) {
            return "client_world_null";
        }
        StringBuilder sb = new StringBuilder();
        int found = 0;
        for (int dy = -yRadius; dy <= yRadius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos probe = center.add(dx, dy, dz);
                    BlockState state = mc.world.getBlockState(probe);
                    if (!state.isOf(Blocks.STONE_SLAB)) {
                        continue;
                    }
                    if (found > 0) sb.append(';');
                    String type = state.contains(SlabBlock.TYPE)
                            ? state.get(SlabBlock.TYPE).toString() : "none";
                    double slabDy = SlabSupport.getYOffset(mc.world, probe, state);
                    sb.append(probe.toShortString())
                            .append(":type=").append(type)
                            .append(",dy=").append(slabDy);
                    found++;
                }
            }
        }
        if (found == 0) {
            return "no_stone_slabs_in_radius=" + radius + ",yRadius=" + yRadius;
        }
        return sb.toString();
    }

    /**
     * Captures a slab placement sample (type + dy + outline-derived visual Y
     * range) for the live-regression proof's tick sweep.
     */
    private static void slabbed$captureSlabSample(
            net.minecraft.client.MinecraftClient mc,
            BlockPos slabPos,
            AtomicReference<String> typeOut,
            AtomicReference<String> dyOut,
            AtomicReference<String> visualYOut
    ) {
        if (mc.world == null) {
            typeOut.set("client_world_null");
            dyOut.set("");
            visualYOut.set("");
            return;
        }
        BlockState state = mc.world.getBlockState(slabPos);
        if (!state.isOf(Blocks.STONE_SLAB)) {
            typeOut.set("not_stone_slab(" + state.getBlock().getTranslationKey() + ")");
            dyOut.set("");
            VoxelShape outline = state.getOutlineShape(mc.world, slabPos, ShapeContext.absent());
            if (outline.isEmpty()) {
                visualYOut.set("empty");
            } else {
                visualYOut.set(formatYRange(
                        slabPos.getY() + outline.getBoundingBox().minY,
                        slabPos.getY() + outline.getBoundingBox().maxY));
            }
            return;
        }
        typeOut.set(state.contains(SlabBlock.TYPE) ? state.get(SlabBlock.TYPE).toString() : "none");
        dyOut.set(Double.toString(SlabSupport.getYOffset(mc.world, slabPos, state)));
        VoxelShape outline = state.getOutlineShape(mc.world, slabPos, ShapeContext.absent());
        if (outline.isEmpty()) {
            visualYOut.set("empty");
        } else {
            visualYOut.set(formatYRange(
                    slabPos.getY() + outline.getBoundingBox().minY,
                    slabPos.getY() + outline.getBoundingBox().maxY));
        }
    }

    /**
     * Vanilla OUTLINE raycast (no torch-visual retarget) — used to probe what the
     * crosshair naturally resolves to for visible side-slab aims.
     */
    private static BlockPos resolveVisibleOutlineHit(
            net.minecraft.client.MinecraftClient mc, Vec3d eye, Vec3d target, double rayLen
    ) {
        Vec3d dir = target.subtract(eye).normalize();
        Vec3d end = eye.add(dir.multiply(rayLen));
        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eye, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    private static HitResult resolvePlayerRaycast(
            net.minecraft.client.MinecraftClient mc, Vec3d target, double reach
    ) {
        if (mc.player == null) {
            return null;
        }

        Vec3d eye = mc.player.getCameraPosVec(0.0f);
        Vec3d delta = target.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        mc.player.refreshPositionAndAngles(mc.player.getX(), mc.player.getY(), mc.player.getZ(), yaw, pitch);
        return mc.player.raycast(reach, 0.0f, false);
    }

    private static HitResult resolvePlayerRaycastFromEye(
            net.minecraft.client.MinecraftClient mc, Vec3d eye, Vec3d target, double reach
    ) {
        if (mc.player == null) {
            return null;
        }

        Vec3d delta = target.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - mc.player.getStandingEyeHeight();
        mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        return mc.player.raycast(reach, 0.0f, false);
    }

    private static double parseDoubleSafe(String s, double fallback) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String formatYRange(double minY, double maxY) {
        return Double.toString(minY) + ".." + Double.toString(maxY);
    }

    private static double parseRangeMin(String range, double fallback) {
        int split = range == null ? -1 : range.indexOf("..");
        if (split < 0) {
            return fallback;
        }
        return parseDoubleSafe(range.substring(0, split), fallback);
    }

    private static double parseRangeMax(String range, double fallback) {
        int split = range == null ? -1 : range.indexOf("..");
        if (split < 0) {
            return fallback;
        }
        return parseDoubleSafe(range.substring(split + 2), fallback);
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
            // Assert visual dy inheritance: adjacent side slab must align with lowered full block neighbor
            double placedDy = com.slabbed.util.SlabSupport.getYOffset(mc.world, reproPlacePos, placed);
            if (placedDy != -0.5) {
                throw new RuntimeException(
                        "adjacent side slab visual dy expected -0.5 at " + reproPlacePos.toShortString()
                        + " to align with lowered full block neighbor, found " + placedDy
                        + " for placed state " + placed);
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
            // Assert visual dy inheritance: adjacent side double slab must retain lowered dy after repeat click
            double repeatedDy = com.slabbed.util.SlabSupport.getYOffset(mc.world, reproPlacePos, placed);
            if (repeatedDy != -0.5) {
                throw new RuntimeException(
                        "adjacent side double slab visual dy expected -0.5 at " + reproPlacePos.toShortString()
                        + " to retain lowered visual alignment after repeat click, found " + repeatedDy
                        + " for placed state " + placed);
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

    /**
     * RED proof — torch compound dy bug.
     *
     * <p>Geometry: bottom slab at supportPos, full solid block at fullPos (one above),
     * adjacent bottom slab at slabPos (east of fullPos, inherits -0.5 dy from adjacent side-slab V2B),
     * floor torch at torchPos (directly above slabPos).
     *
     * <p>The torch sits on a bottom slab whose own visual dy is -0.5 (adjacent side-slab rule).
     * The torch therefore needs getYOffset == -1.0 to align with the slab's visual top surface.
     * Current code caps at -0.5, causing a 0.5 visual float.
     *
     * <p>Expected: FAIL with "torch compound dy expected -1.0 ... found -0.5"
     */
    static void runTorchOnAdjacentLoweredSlabDyProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        final BlockPos supportPos = FIXTURE_ORIGIN.add(32, 0, 0);
        final BlockPos fullPos = supportPos.up();
        final BlockPos slabPos = fullPos.east();
        final BlockPos torchPos = slabPos.up();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(net.minecraft.block.SlabBlock.TYPE, net.minecraft.block.enums.SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(slabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(net.minecraft.block.SlabBlock.TYPE, net.minecraft.block.enums.SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(torchPos, Blocks.TORCH.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world is null during torch compound dy assertion");
            }
            BlockState torchState = mc.world.getBlockState(torchPos);
            if (!torchState.isOf(Blocks.TORCH)) {
                throw new RuntimeException(
                        "torch compound dy proof: expected TORCH at " + torchPos.toShortString()
                        + ", found " + torchState.getBlock().getTranslationKey());
            }
            double dy = com.slabbed.util.SlabSupport.getYOffset(mc.world, torchPos, torchState);
            if (dy != -1.0) {
                throw new RuntimeException(
                        "torch compound dy expected -1.0 at " + torchPos.toShortString()
                        + " (torch above adjacent-lowered bottom slab), found " + dy
                        + " for state " + torchState);
            }
        });
    }

    /**
     * Floor torch on BS-FB-0.5S — selection comfort proof.
     *
     * <p>Geometry (BS-FB-0.5S):
     * <ul>
     *   <li>supportPos = bottom slab support</li>
     *   <li>fullPos    = supportPos.up() — STONE (lowered onto support, dy=-0.5 visual)</li>
     *   <li>slabPos    = fullPos.east()  — adjacent bottom slab (V2B inherits dy=-0.5)</li>
     *   <li>torchPos   = slabPos.up()    — TORCH (compound dy=-1.0)</li>
     * </ul>
     *
     * <p>Visual top of slabPos is at world Y=slabPos.y (V2B -0.5 over native +0.5).
     * Vanilla {@code AbstractTorchBlock.SHAPE} = {@code Block.createCuboidShape(6,0,6,10,10,10)}
     * (X/Z 0.375–0.625, Y 0.0–0.625). With dy=-1.0 the torch outline sits at world
     * Y=torchPos.y-1.0 to torchPos.y-0.375 — that means the visible portion above the
     * slab visual top (world Y=slabPos.y) is only 0.125 blocks tall. Players aiming
     * at the visible torch from natural side angles routinely miss this thin sliver
     * or hit the slab below.
     *
     * <p>This proof simulates a natural player aim from south of the fixture toward
     * the visible torch. The resolved hit (after vanilla DDA + the
     * {@code GameRendererCrosshairRetargetMixin} TAIL replay) must equal {@code torchPos},
     * not {@code slabPos} below.
     *
     * <p>Expected: RED before comfort fix — natural side aim at the visible torch
     * either hits the slab below or misses; resolved pos != torchPos.
     */
    static void runTorchOnBsFb05sComfortSelectableProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<ManifestArtifact> artifacts
    ) {
        final String testId = "torch_on_bs_fb_0_5s_comfort_selectable";
        final BlockPos supportPos = FIXTURE_ORIGIN.add(36, 0, 0);
        final BlockPos fullPos = supportPos.up();
        final BlockPos slabPos = fullPos.east();
        final BlockPos torchPos = slabPos.up();
        final BlockPos slabBelowTorch = slabPos;

        AtomicReference<String> aim1Resolved = new AtomicReference<>("");
        AtomicReference<String> aim2Resolved = new AtomicReference<>("");

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(slabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(torchPos, Blocks.TORCH.getDefaultState(), Block.NOTIFY_LISTENERS);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("client not ready for torch comfort proof");
            }
            BlockState ts = mc.world.getBlockState(torchPos);
            if (!ts.isOf(Blocks.TORCH)) {
                throw new RuntimeException(
                        "torch comfort proof: expected TORCH at " + torchPos.toShortString()
                        + ", found " + ts.getBlock().getTranslationKey());
            }

            // Aim 1 — natural side aim: player standing south, eye near slab visual top,
            // looking at the centre of the visible torch sliver above the slab top.
            // Visible torch sliver is world Y=slabPos.y to slabPos.y+0.125 ≈ centre at Y=slabPos.y+0.06.
            Vec3d eye1 = new Vec3d(
                    torchPos.getX() + 0.5,
                    slabBelowTorch.getY() + 0.30,
                    torchPos.getZ() - 2.5);
            Vec3d target1 = new Vec3d(
                    torchPos.getX() + 0.5,
                    slabBelowTorch.getY() + 0.06,
                    torchPos.getZ() + 0.5);
            BlockPos resolved1 = resolveTorchAimWithRescue(mc, eye1, target1, 6.0);
            aim1Resolved.set(resolved1 == null ? "MISS" : resolved1.toShortString());
            if (resolved1 == null || !resolved1.equals(torchPos)) {
                throw new RuntimeException(
                        "[" + testId + "] aim1 (natural side) resolved to "
                        + (resolved1 == null ? "MISS" : resolved1.toShortString())
                        + "; expected torch at " + torchPos.toShortString()
                        + " — comfort selection failing on visible torch sliver above slab top");
            }

            // Aim 2 — slightly downward aim toward torch tip: player standing south on
            // adjacent ground (full block top), eye 1.62 above feet, looking at the
            // upper portion of the visible torch.
            Vec3d eye2 = new Vec3d(
                    torchPos.getX() + 0.5,
                    fullPos.getY() + 1.0 + 1.62,
                    torchPos.getZ() - 2.0);
            Vec3d target2 = new Vec3d(
                    torchPos.getX() + 0.5,
                    slabBelowTorch.getY() + 0.10,
                    torchPos.getZ() + 0.5);
            BlockPos resolved2 = resolveTorchAimWithRescue(mc, eye2, target2, 6.0);
            aim2Resolved.set(resolved2 == null ? "MISS" : resolved2.toShortString());
            if (resolved2 == null || !resolved2.equals(torchPos)) {
                throw new RuntimeException(
                        "[" + testId + "] aim2 (downward) resolved to "
                        + (resolved2 == null ? "MISS" : resolved2.toShortString())
                        + "; expected torch at " + torchPos.toShortString()
                        + " — comfort selection failing on downward aim at torch tip");
            }
        });

        writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "torch comfort selection",
                "Floor torch on BS-FB-0.5S resolves to torchPos under natural player aim angles.",
                testId,
                testId,
                List.of(
                        new NoteField("supportPos", supportPos.toShortString()),
                        new NoteField("fullPos", fullPos.toShortString()),
                        new NoteField("slabPos", slabPos.toShortString()),
                        new NoteField("torchPos", torchPos.toShortString()),
                        new NoteField("aim1Resolved", aim1Resolved.get()),
                        new NoteField("aim2Resolved", aim2Resolved.get())
                ),
                true);
    }

    /**
     * Mirrors the production crosshair-resolve path: vanilla DDA OUTLINE raycast
     * followed by the {@code GameRendererCrosshairRetargetMixin} TAIL replay
     * (one-level-up lowered owner, outline shape, ShapeContext.of(player),
     * ≤ distance predicate). Returns the resolved BlockPos, or {@code null} on miss.
     */
    private static BlockPos resolveTorchAimWithRescue(
            net.minecraft.client.MinecraftClient mc, Vec3d eye, Vec3d target, double rayLen
    ) {
        Vec3d dir = target.subtract(eye).normalize();
        Vec3d end = eye.add(dir.multiply(rayLen));
        BlockHitResult vanillaHit = mc.world.raycast(new RaycastContext(
                eye, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player));
        if (vanillaHit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockHitResult finalHit = vanillaHit;
        BlockPos hitPos = vanillaHit.getBlockPos();
        BlockPos abovePos = hitPos.up();
        BlockState aboveState = mc.world.getBlockState(abovePos);
        if (SlabSupport.isLoweredTorchVisual(mc.world, abovePos, aboveState)) {
            double origDist = vanillaHit.getPos().subtract(eye).length();
            if (origDist > 0.0) {
                Vec3d extEnd = eye.add(dir.multiply(origDist + 0.5));
                VoxelShape aboveOutline = aboveState.getOutlineShape(
                        mc.world, abovePos, ShapeContext.of(mc.player));
                if (!aboveOutline.isEmpty()) {
                    BlockHitResult retargetHit = aboveOutline.raycast(eye, extEnd, abovePos);
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
        return finalHit.getBlockPos();
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
