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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
            BlockPos foundHead = null;
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos neighbor = footPos.offset(dir);
                BlockState neighborState = mc.world.getBlockState(neighbor);
                if (neighborState.isOf(Blocks.RED_BED)
                        && neighborState.contains(BedBlock.PART)
                        && neighborState.get(BedBlock.PART) == BedPart.HEAD) {
                    foundHead = neighbor;
                    headStateText.set(neighborState.toString());
                    break;
                }
            }
            if (foundHead == null) {
                throw new RuntimeException(
                        "bed rescue proof expected adjacent RED_BED HEAD near "
                        + footPos.toShortString() + ", none found");
            }
            footStateText.set(footState.toString());
            headPosText.set(foundHead.toShortString());
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
            String json = buildManifestJson(screenshotDir, artifacts);
            Files.writeString(manifestPath, json);
        } catch (IOException ignored) {
            // Manifest emission is auxiliary evidence; test correctness remains assertion-driven.
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
        List<String> missingManifestProofIds = new ArrayList<>();
        for (String proofId : LOWERED_SIDE_SLAB_EXPECTED_PROOF_IDS) {
            Path notesPath = screenshotDir.resolve(proofId + "_notes.json");
            if (!Files.isRegularFile(notesPath)) {
                missingNotes.add(notesPath.getFileName().toString());
            }
            if (!manifestJson.contains("\"proofId\": \"" + proofId + "\"")) {
                missingManifestProofIds.add(proofId);
            }
        }

        if (!missingNotes.isEmpty() || !missingManifestProofIds.isEmpty()) {
            StringBuilder message = new StringBuilder("lowered-side-slab proof ladder is incomplete");
            if (!missingNotes.isEmpty()) {
                message.append("; missing notes: ").append(missingNotes);
            }
            if (!missingManifestProofIds.isEmpty()) {
                message.append("; missing manifest proof ids: ").append(missingManifestProofIds);
            }
            throw new RuntimeException(message.toString());
        }
    }

    static String buildManifestJson(Path screenshotDir, List<ManifestArtifact> artifacts) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"timestamp\": \"").append(escapeJson(Instant.now().toString())).append("\",\n");
        appendOptionalRuntimeGitField(sb, "gitBranch", System.getProperty("slabbed.git.branch"));
        appendOptionalRuntimeGitField(sb, "gitShortHash", System.getProperty("slabbed.git.shortHash"));
        sb.append("  \"scenario\": \"SlabbedLabClientGameTest.runTest\",\n");
        sb.append("  \"outputDirectory\": \"")
                .append(escapeJson(screenshotDir.toAbsolutePath().toString()))
                .append("\",\n");
        appendArtifactsJsonArray(sb, artifacts);
        sb.append("\n}\n");
        return sb.toString();
    }

    static void appendOptionalRuntimeGitField(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("  \"")
                    .append(escapeJson(key))
                    .append("\": \"")
                    .append(escapeJson(value))
                    .append("\",\n");
        }
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
                    .append("\", \"proofId\": \"")
                    .append(escapeJson(artifact.proofId()))
                    .append("\", \"label\": \"")
                    .append(escapeJson(artifact.label()))
                    .append("\"}");
            i++;
        }
        sb.append("]");
    }

    static String escapeJson(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static record NoteField(String key, String value) {
    }

    static record ManifestArtifact(String file, String proofId, String label) {
    }
}
