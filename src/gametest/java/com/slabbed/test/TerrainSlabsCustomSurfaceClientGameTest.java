package com.slabbed.test;

import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Default-off red proof for Terrain Slabs custom-surface failures reported in live video.
 */
public final class TerrainSlabsCustomSurfaceClientGameTest implements FabricClientGameTest {
    private static final String RED_ONLY_PROPERTY = "slabbed.terrainSlabsCustomSurfaceRedOnly";
    private static final String VISUAL_PROOF_PROPERTY = "slabbed.terrainSlabsCustomSurfaceVisualProof";
    private static final Path ARTIFACT = Path.of("build/run/clientGameTest/terrain-slabs-custom-surface-red-proof.txt");
    private static final Path VISUAL_ARTIFACT = Path.of("build/run/clientGameTest/terrain-slabs-custom-surface-visual-proof.txt");
    private static final BlockPos BASE = new BlockPos(40, 200, 0);
    private static final BlockPos UNIVERSAL_SUPPORT_POS = BASE;
    private static final BlockPos BED_FOOT_SUPPORT_POS = BASE.add(6, 0, 0);
    private static final BlockPos CHEST_SUPPORT_POS = BASE.add(12, 0, 0);
    private static final BlockPos UNDERSIDE_SUPPORT_POS = BASE.add(18, 1, 0);
    private static final BlockPos GHOST_SUPPORT_POS = BASE.add(24, 0, 0);
    private static final BlockPos LOG_FAMILY_SUPPORT_START_POS = BASE.add(30, 0, 0);
    private static final BlockPos VANILLA_SLAB_SUPPORT_POS = BASE.add(30, 0, 4);
    private static final BlockPos FENCE_SUPPORT_POS = BASE.add(36, 0, 4);
    private static final BlockPos WALL_SUPPORT_POS = BASE.add(42, 0, 4);
    private static final BlockPos PANE_SUPPORT_POS = BASE.add(48, 0, 4);
    private static final BlockPos MATRIX_START_POS = BASE.add(0, 0, 12);
    private static final int MATRIX_SUBJECT_SPACING = 3;
    private static final int MATRIX_SUPPORT_SPACING = 3;
    private static final int MATRIX_SUPPORT_ROWS_PER_BATCH = 8;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!Boolean.getBoolean(RED_ONLY_PROPERTY)) {
            return;
        }
        if (!FabricLoader.getInstance().isModLoaded(TerrainSlabsCompat.MOD_ID)) {
            String line = "TERRAIN_SLABS_CUSTOM_SURFACE_BLOCKED reason=terrainslabs_not_loaded";
            System.out.println(line);
            throw new AssertionError(line);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            BlockState bottom = terrainDirtSlabState(SlabType.BOTTOM);
            BlockState top = terrainDirtSlabState(SlabType.TOP);

            BlockPos universalPos = UNIVERSAL_SUPPORT_POS.up();
            BlockPos bedFootPos = BED_FOOT_SUPPORT_POS.up();
            BlockPos bedHeadSupportPos = BED_FOOT_SUPPORT_POS.south();
            BlockPos bedHeadPos = bedFootPos.south();
            BlockPos chestPos = CHEST_SUPPORT_POS.up();
            BlockPos chainPos = UNDERSIDE_SUPPORT_POS.down();
            BlockPos ghostPos = GHOST_SUPPORT_POS.up();
            List<LogFamilyCase> logFamilyCases = logFamilyCases();
            BlockPos vanillaSlabPos = VANILLA_SLAB_SUPPORT_POS.up();
            BlockPos fencePos = FENCE_SUPPORT_POS.up();
            BlockPos wallPos = WALL_SUPPORT_POS.up();
            BlockPos panePos = PANE_SUPPORT_POS.up();
            List<MatrixSupportCase> matrixSupports = terrainSlabMatrixSupportCases();
            List<MatrixSubjectCase> matrixSubjects = matrixSubjectCases();

            singleplayer.getServer().runOnServer(server -> {
                var world = server.getOverworld();
                clearColumn(world, UNIVERSAL_SUPPORT_POS);
                clearColumn(world, BED_FOOT_SUPPORT_POS);
                clearColumn(world, bedHeadSupportPos);
                clearColumn(world, CHEST_SUPPORT_POS);
                clearColumn(world, UNDERSIDE_SUPPORT_POS);
                clearColumn(world, GHOST_SUPPORT_POS);
                for (LogFamilyCase logFamilyCase : logFamilyCases) {
                    clearColumn(world, logFamilyCase.supportPos());
                }
                clearColumn(world, VANILLA_SLAB_SUPPORT_POS);
                clearColumn(world, FENCE_SUPPORT_POS);
                clearColumn(world, WALL_SUPPORT_POS);
                clearColumn(world, PANE_SUPPORT_POS);
                clearMatrixArea(world, matrixSubjects);

                world.setBlockState(UNIVERSAL_SUPPORT_POS, bottom, Block.NOTIFY_LISTENERS);
                world.setBlockState(universalPos, floorPointedDripstone(), Block.NOTIFY_LISTENERS);

                world.setBlockState(BED_FOOT_SUPPORT_POS, bottom, Block.NOTIFY_LISTENERS);
                world.setBlockState(bedHeadSupportPos, bottom, Block.NOTIFY_LISTENERS);
                world.setBlockState(bedFootPos, bedState(BedPart.FOOT), Block.NOTIFY_LISTENERS);
                world.setBlockState(bedHeadPos, bedState(BedPart.HEAD), Block.NOTIFY_LISTENERS);

                world.setBlockState(CHEST_SUPPORT_POS, bottom, Block.NOTIFY_LISTENERS);
                world.setBlockState(chestPos, Blocks.CHEST.getDefaultState(), Block.NOTIFY_LISTENERS);

                world.setBlockState(UNDERSIDE_SUPPORT_POS, top, Block.NOTIFY_LISTENERS);
                world.setBlockState(chainPos, Blocks.IRON_CHAIN.getDefaultState()
                        .with(ChainBlock.AXIS, Direction.Axis.Y), Block.NOTIFY_LISTENERS);

                world.setBlockState(GHOST_SUPPORT_POS, bottom, Block.NOTIFY_LISTENERS);
                world.setBlockState(ghostPos, Blocks.DEAD_BUSH.getDefaultState(), Block.NOTIFY_LISTENERS);

                for (LogFamilyCase logFamilyCase : logFamilyCases) {
                    world.setBlockState(logFamilyCase.supportPos(), bottom, Block.NOTIFY_LISTENERS);
                    world.setBlockState(logFamilyCase.subjectPos(), logFamilyCase.state(), Block.NOTIFY_LISTENERS);
                }

                world.setBlockState(VANILLA_SLAB_SUPPORT_POS, bottom, Block.NOTIFY_LISTENERS);
                world.setBlockState(vanillaSlabPos,
                        Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        Block.NOTIFY_LISTENERS);

                world.setBlockState(FENCE_SUPPORT_POS, bottom, Block.NOTIFY_LISTENERS);
                world.setBlockState(fencePos, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

                world.setBlockState(WALL_SUPPORT_POS, bottom, Block.NOTIFY_LISTENERS);
                world.setBlockState(wallPos, Blocks.COBBLESTONE_WALL.getDefaultState(), Block.NOTIFY_LISTENERS);

                world.setBlockState(PANE_SUPPORT_POS, bottom, Block.NOTIFY_LISTENERS);
                world.setBlockState(panePos, Blocks.GLASS_PANE.getDefaultState(), Block.NOTIFY_LISTENERS);

                placeObjectCategoryMatrix(world,
                        matrixSupports.subList(0, Math.min(MATRIX_SUPPORT_ROWS_PER_BATCH, matrixSupports.size())),
                        matrixSubjects);
            });

            ctx.waitTick();
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            if (Boolean.getBoolean(VISUAL_PROOF_PROPERTY)) {
                captureVisualProof(ctx, singleplayer);
            }

            List<LaneResult> matrixLanes = probeObjectCategoryMatrixBatched(
                    ctx,
                    singleplayer,
                    matrixSupports,
                    matrixSubjects);

            ctx.runOnClient(mc -> {
                if (mc.world == null || mc.player == null) {
                    throw new AssertionError("client world/player missing for Terrain Slabs custom-surface proof");
                }
                List<LaneResult> lanes = new ArrayList<>();
                lanes.add(probeUniversalObjectLane(mc, UNIVERSAL_SUPPORT_POS, universalPos));
                lanes.add(probeBedLane(mc, BED_FOOT_SUPPORT_POS, bedFootPos, bedHeadSupportPos, bedHeadPos));
                lanes.add(probeChestLane(mc, CHEST_SUPPORT_POS, chestPos));
                lanes.add(probeUndersideLane(mc, UNDERSIDE_SUPPORT_POS, chainPos));
                lanes.add(probeGhostLane(mc, GHOST_SUPPORT_POS, ghostPos));
                for (LogFamilyCase logFamilyCase : logFamilyCases) {
                    lanes.add(probeLogFamilyLane(mc, logFamilyCase));
                }
                lanes.add(probeVanillaSlabSubjectLane(mc, VANILLA_SLAB_SUPPORT_POS, vanillaSlabPos));
                lanes.add(probeFenceSubjectLane(mc, FENCE_SUPPORT_POS, fencePos));
                lanes.add(probeWallSubjectLane(mc, WALL_SUPPORT_POS, wallPos));
                lanes.add(probePaneSubjectLane(mc, PANE_SUPPORT_POS, panePos));
                lanes.addAll(matrixLanes);

                List<String> artifactLines = new ArrayList<>();
                artifactLines.add("Terrain Slabs custom-surface red proof");
                artifactLines.add("supportId=" + supportId(mc.world.getBlockState(UNIVERSAL_SUPPORT_POS)));
                artifactLines.add("objectCategoryMatrix.supportCount=" + matrixSupports.size());
                artifactLines.add("objectCategoryMatrix.subjectCount=" + matrixSubjects.size());
                artifactLines.add("artifact=" + ARTIFACT.toAbsolutePath());

                List<String> redNames = new ArrayList<>();
                for (LaneResult lane : lanes) {
                    System.out.println(lane.marker());
                    artifactLines.add(lane.marker());
                    if (lane.red()) {
                        redNames.add(lane.name() + ":" + lane.reason());
                    }
                }
                writeArtifact(artifactLines);

                if (!redNames.isEmpty()) {
                    String line = "TERRAIN_SLABS_CUSTOM_SURFACE_RED lanes=" + String.join(",", redNames)
                            + " artifact=" + ARTIFACT.toAbsolutePath();
                    System.out.println(line);
                    throw new AssertionError(line);
                }

                System.out.println("TERRAIN_SLABS_CUSTOM_SURFACE_GREEN artifact=" + ARTIFACT.toAbsolutePath());
            });
        }
    }

    private static void captureVisualProof(ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        Path screenshotDir = SlabbedLabClientGameTest.resolveClientGameTestScreenshotDir();
        Set<String> knownScreenshots = SlabbedLabClientGameTest.listScreenshotFileNames(screenshotDir);
        List<String> lines = new ArrayList<>();
        lines.add("Terrain Slabs custom-surface visual proof");
        lines.add("screenshotDir=" + screenshotDir.toAbsolutePath());
        captureVisualShot(ctx, singleplayer, screenshotDir, knownScreenshots, lines,
                "terrain_slabs_custom_surface_five_left",
                new Vec3d(46.0d, 202.15d, 7.0d),
                new Vec3d(46.0d, 200.45d, 0.0d),
                "original lanes: universal object, bed, chest");
        // Prime the mid-row camera before the log-family capture so the later row resolves before screenshot.
        warmVisualCamera(ctx, singleplayer,
                new Vec3d(61.0d, 202.15d, 7.0d),
                new Vec3d(61.0d, 200.45d, 0.0d));
        captureVisualShot(ctx, singleplayer, screenshotDir, knownScreenshots, lines,
                "terrain_slabs_custom_surface_log_family",
                new Vec3d(74.5d, 202.15d, 7.0d),
                new Vec3d(74.5d, 200.45d, 0.0d),
                "log family lanes: oak_log, spruce_log, stripped_oak_wood, crimson_stem");
        captureVisualShot(ctx, singleplayer, screenshotDir, knownScreenshots, lines,
                "terrain_slabs_custom_surface_ghost_object",
                new Vec3d(64.0d, 201.8d, 4.0d),
                new Vec3d(64.0d, 200.55d, 0.0d),
                "original lane: ghost object");
        captureVisualShot(ctx, singleplayer, screenshotDir, knownScreenshots, lines,
                "terrain_slabs_custom_surface_fence_subject",
                new Vec3d(76.0d, 202.15d, 10.0d),
                new Vec3d(76.0d, 200.45d, 4.0d),
                "fence subject lane: oak_fence");
        captureVisualShot(ctx, singleplayer, screenshotDir, knownScreenshots, lines,
                "terrain_slabs_custom_surface_wall_pane_subjects",
                new Vec3d(88.0d, 202.15d, 10.0d),
                new Vec3d(88.0d, 200.45d, 4.0d),
                "wall/pane subject lanes: cobblestone_wall, glass_pane");
        captureVisualShot(ctx, singleplayer, screenshotDir, knownScreenshots, lines,
                "terrain_slabs_custom_surface_object_matrix_near",
                new Vec3d(59.0d, 205.0d, 25.0d),
                new Vec3d(59.0d, 200.35d, 16.0d),
                "object category matrix: first Terrain Slabs support rows");
        captureVisualShot(ctx, singleplayer, screenshotDir, knownScreenshots, lines,
                "terrain_slabs_custom_surface_object_matrix_far",
                new Vec3d(59.0d, 207.0d, 38.0d),
                new Vec3d(59.0d, 200.35d, 22.0d),
                "object category matrix: first batch wide overview");
        captureVisualShot(ctx, singleplayer, screenshotDir, knownScreenshots, lines,
                "terrain_slabs_custom_surface_object_matrix_bookshelf_kelp",
                new Vec3d(83.5d, 205.0d, 25.0d),
                new Vec3d(83.5d, 200.35d, 16.0d),
                "object category matrix: bookshelf and kelp lanes");
        writeArtifact(VISUAL_ARTIFACT, lines);
    }

    private static void warmVisualCamera(ClientGameTestContext ctx, TestSingleplayerContext singleplayer, Vec3d eye, Vec3d target) {
        ctx.runOnClient(mc -> positionCamera(mc, eye, target));
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
    }

    private static void captureVisualShot(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshots,
            List<String> lines,
            String proofId,
            Vec3d eye,
            Vec3d target,
            String label
    ) {
        ctx.runOnClient(mc -> positionCamera(mc, eye, target));
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.takeScreenshot(proofId);

        String resolved = SlabbedLabClientGameTest.resolveScreenshotFileNameForProofId(screenshotDir, proofId);
        Set<String> afterCapture = SlabbedLabClientGameTest.listScreenshotFileNames(screenshotDir);
        if (resolved == null) {
            for (String fileName : afterCapture) {
                if (!knownScreenshots.contains(fileName)) {
                    resolved = fileName;
                    break;
                }
            }
        }
        knownScreenshots.clear();
        knownScreenshots.addAll(afterCapture);

        String line = "VISUAL_PROOF_SCREENSHOT proofId=" + proofId
                + " file=" + (resolved == null ? "MISSING" : screenshotDir.resolve(resolved).toAbsolutePath())
                + " label=\"" + label + "\""
                + " eye=" + vec(eye)
                + " target=" + vec(target);
        System.out.println(line);
        lines.add(line);
    }

    private static void positionCamera(MinecraftClient mc, Vec3d eye, Vec3d target) {
        if (mc.player == null) {
            throw new AssertionError("client player missing for Terrain Slabs visual proof");
        }
        mc.options.setPerspective(Perspective.FIRST_PERSON);
        mc.options.hudHidden = true;
        double feetY = eye.y - mc.player.getEyeHeight(mc.player.getPose());
        mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw(eye, target), pitch(eye, target));
        mc.player.setVelocity(Vec3d.ZERO);
    }

    private static float yaw(Vec3d eye, Vec3d target) {
        Vec3d delta = target.subtract(eye);
        return (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
    }

    private static float pitch(Vec3d eye, Vec3d target) {
        Vec3d delta = target.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        return (float) (-Math.toDegrees(Math.atan2(delta.y, horizontal)));
    }

    private static String vec(Vec3d vec) {
        return String.format("%.2f,%.2f,%.2f", vec.x, vec.y, vec.z);
    }

    private static LaneResult probeUniversalObjectLane(MinecraftClient mc, BlockPos supportPos, BlockPos objectPos) {
        BlockState support = mc.world.getBlockState(supportPos);
        BlockState subject = mc.world.getBlockState(objectPos);
        double supportDy = SlabSupport.getYOffset(mc.world, supportPos, support);
        double subjectDy = SlabSupport.getYOffset(mc.world, objectPos, subject);
        ShapeFacts shape = shapeFacts(mc, objectPos, subject, objectPos.getY() - 0.25d);
        String fields = commonSupportFields(mc, supportPos, support)
                + commonSubjectFields(mc, "minecraft:pointed_dripstone", objectPos, subject, subjectDy, shape)
                + " expectedSubjectDy=-0.5 expectedModelMinY=-0.5 expectedOutlineMinY=-0.5 supportDy=" + supportDy;
        String reason = firstRedReason(
                customPolicyReason(support),
                mismatch("subject_dy_mismatch", subjectDy, -0.5d),
                mismatch("model_min_y_mismatch", shape.modelMinY(), -0.5d),
                mismatch("outline_min_y_mismatch", shape.outlineMinY(), -0.5d),
                !hitPosEquals(shape.resolvedHit(), objectPos) ? "lowered_visible_target_mismatch" : null);
        return lane("TERRAIN_SLABS_UNIVERSAL_OBJECT", reason, fields);
    }

    private static LaneResult probeBedLane(
            MinecraftClient mc, BlockPos footSupportPos, BlockPos footPos, BlockPos headSupportPos, BlockPos headPos
    ) {
        BlockState footSupport = mc.world.getBlockState(footSupportPos);
        BlockState headSupport = mc.world.getBlockState(headSupportPos);
        BlockState foot = mc.world.getBlockState(footPos);
        BlockState head = mc.world.getBlockState(headPos);
        double footDy = SlabSupport.getYOffset(mc.world, footPos, foot);
        double headDy = SlabSupport.getYOffset(mc.world, headPos, head);
        ShapeFacts footShape = shapeFacts(mc, footPos, foot, footPos.getY() - 0.25d);
        ShapeFacts headShape = shapeFacts(mc, headPos, head, headPos.getY() - 0.25d);
        String fields = commonSupportFields(mc, footSupportPos, footSupport)
                + " headSupportPos=" + headSupportPos.toShortString()
                + " headSupportState=" + headSupport
                + " headSupportCustomKind=" + customSurfaceKind(headSupport)
                + " subjectFootState=" + foot
                + " subjectHeadState=" + head
                + " footPos=" + footPos.toShortString()
                + " headPos=" + headPos.toShortString()
                + " footDy=" + footDy
                + " headDy=" + headDy
                + " footOutlineMinY=" + footShape.outlineMinY()
                + " headOutlineMinY=" + headShape.outlineMinY()
                + " expectedFootDy=-0.5 expectedHeadDy=-0.5 expectedOutlineMinY=-0.5";
        String reason = firstRedReason(
                customPolicyReason(footSupport),
                customPolicyReason(headSupport),
                mismatch("foot_dy_mismatch", footDy, -0.5d),
                mismatch("head_dy_mismatch", headDy, -0.5d),
                mismatch("foot_outline_min_y_mismatch", footShape.outlineMinY(), -0.5d),
                mismatch("head_outline_min_y_mismatch", headShape.outlineMinY(), -0.5d));
        return lane("TERRAIN_SLABS_BED_CUTTING", reason, fields);
    }

    private static LaneResult probeChestLane(MinecraftClient mc, BlockPos supportPos, BlockPos chestPos) {
        BlockState support = mc.world.getBlockState(supportPos);
        BlockState chest = mc.world.getBlockState(chestPos);
        double chestDy = SlabSupport.getYOffset(mc.world, chestPos, chest);
        ShapeFacts shape = shapeFacts(mc, chestPos, chest, chestPos.getY() - 0.25d);
        boolean loweredVisual = SlabSupport.isLoweredBlockEntityVisual(mc.world, chestPos, chest);
        String fields = commonSupportFields(mc, supportPos, support)
                + commonSubjectFields(mc, "minecraft:chest", chestPos, chest, chestDy, shape)
                + " loweredBlockEntityVisual=" + loweredVisual
                + " expectedChestDy=-0.5 expectedLoweredBlockEntityVisual=true expectedOutlineMinY=-0.5";
        String reason = firstRedReason(
                customPolicyReason(support),
                mismatch("chest_dy_mismatch", chestDy, -0.5d),
                !loweredVisual ? "lowered_block_entity_visual_false" : null,
                mismatch("outline_min_y_mismatch", shape.outlineMinY(), -0.5d),
                !hitPosEquals(shape.resolvedHit(), chestPos) ? "lowered_chest_target_mismatch" : null);
        return lane("TERRAIN_SLABS_CHEST_CUTTING", reason, fields);
    }

    private static LaneResult probeUndersideLane(MinecraftClient mc, BlockPos supportPos, BlockPos chainPos) {
        BlockState support = mc.world.getBlockState(supportPos);
        BlockState chain = mc.world.getBlockState(chainPos);
        double chainDy = SlabSupport.getYOffset(mc.world, chainPos, chain);
        boolean undersideFullSquare = support.isSideSolidFullSquare(mc.world, supportPos, Direction.DOWN);
        boolean ceilingSupport = SlabSupport.isCeilingSupportBottomSurface(mc.world, supportPos);
        ShapeFacts shape = shapeFacts(mc, chainPos, chain, chainPos.getY() + 0.75d);
        String fields = commonSupportFields(mc, supportPos, support)
                + commonSubjectFields(mc, "minecraft:iron_chain", chainPos, chain, chainDy, shape)
                + " undersideFullSquare=" + undersideFullSquare
                + " ceilingSupportBottomSurface=" + ceilingSupport
                + " expectedCustomKind=TOP_LIKE expectedChainDy=0.5 expectedUndersideFullSquare=true";
        String reason = firstRedReason(
                !"TOP_LIKE".equals(customSurfaceKind(support)) ? "custom_top_surface_kind_missing" : null,
                customSkipSupportMissingReason(support),
                mismatch("chain_dy_mismatch", chainDy, 0.5d),
                !undersideFullSquare ? "underside_full_square_false" : null,
                !ceilingSupport ? "ceiling_support_bottom_surface_false" : null);
        return lane("TERRAIN_SLABS_UNDERSIDE_ANCHOR", reason, fields);
    }

    private static LaneResult probeGhostLane(MinecraftClient mc, BlockPos supportPos, BlockPos ghostPos) {
        BlockState support = mc.world.getBlockState(supportPos);
        BlockState subject = mc.world.getBlockState(ghostPos);
        double subjectDy = SlabSupport.getYOffset(mc.world, ghostPos, subject);
        ShapeFacts lowerShape = shapeFacts(mc, ghostPos, subject, ghostPos.getY() - 0.25d);
        RayProbe upperProbe = rayProbeAtY(mc, ghostPos, ghostPos.getY() + 0.75d);
        String fields = commonSupportFields(mc, supportPos, support)
                + commonSubjectFields(mc, "minecraft:dead_bush", ghostPos, subject, subjectDy, lowerShape)
                + " upperEmptyProbeHit=" + describeHit(upperProbe.worldHit())
                + " upperEmptyProbeBreakOwner=" + breakOwner(mc, upperProbe.worldHit())
                + " upperEmptyProbeResolvedHit=" + describeHit(upperProbe.resolvedHit())
                + " upperEmptyProbeResolvedBreakOwner=" + breakOwner(mc, upperProbe.resolvedHit())
                + " expectedSubjectDy=-0.5 expectedUpperEmptyProbeHit=MISS expectedBreakOwner=none";
        String reason = firstRedReason(
                customPolicyReason(support),
                mismatch("ghost_subject_dy_mismatch", subjectDy, -0.5d),
                !hitPosEquals(lowerShape.resolvedHit(), ghostPos) ? "visible_lower_space_not_owned_by_subject" : null,
                hitPosEquals(upperProbe.resolvedHit(), ghostPos) ? "ghost_upper_empty_space_owned_by_subject" : null);
        return lane("TERRAIN_SLABS_GHOST_OBJECT", reason, fields);
    }

    private static LaneResult probeLogFamilyLane(MinecraftClient mc, LogFamilyCase logFamilyCase) {
        BlockPos supportPos = logFamilyCase.supportPos();
        BlockPos subjectPos = logFamilyCase.subjectPos();
        BlockState support = mc.world.getBlockState(supportPos);
        BlockState subject = mc.world.getBlockState(subjectPos);
        double subjectDy = SlabSupport.getYOffset(mc.world, subjectPos, subject);
        ShapeFacts shape = shapeFacts(mc, subjectPos, subject, subjectPos.getY() - 0.25d);
        String directSupported = directCustomSupportedObject(mc, subjectPos, subject);
        String fields = commonSupportFields(mc, supportPos, support)
                + commonSubjectFields(mc, logFamilyCase.expectedId(), subjectPos, subject, subjectDy, shape)
                + " logFamilyCase=" + logFamilyCase.markerSuffix()
                + " expectedDirectCustomSupportedObject=true"
                + " expectedSubjectDy=-0.5 expectedModelMinY=-0.5 expectedOutlineMinY=-0.5";
        String reason = firstRedReason(
                customPolicyReason(support),
                !"true".equals(directSupported) ? "direct_custom_supported_object_false" : null,
                mismatch("subject_dy_mismatch", subjectDy, -0.5d),
                mismatch("model_min_y_mismatch", shape.modelMinY(), -0.5d),
                mismatch("outline_min_y_mismatch", shape.outlineMinY(), -0.5d),
                !hitPosEquals(shape.resolvedHit(), subjectPos) ? "lowered_visible_target_mismatch" : null);
        return lane("TERRAIN_SLABS_LOG_FAMILY_" + logFamilyCase.markerSuffix(), reason, fields);
    }

    private static LaneResult probeVanillaSlabSubjectLane(MinecraftClient mc, BlockPos supportPos, BlockPos slabPos) {
        BlockState support = mc.world.getBlockState(supportPos);
        BlockState subject = mc.world.getBlockState(slabPos);
        double subjectDy = SlabSupport.getYOffset(mc.world, slabPos, subject);
        ShapeFacts shape = shapeFacts(mc, slabPos, subject, slabPos.getY() - 0.25d);
        String directSupported = directCustomSupportedObject(mc, slabPos, subject);
        String fields = commonSupportFields(mc, supportPos, support)
                + commonSubjectFields(mc, "minecraft:oak_slab", slabPos, subject, subjectDy, shape)
                + " expectedDirectCustomSupportedObject=true"
                + " expectedSubjectDy=-0.5 expectedModelMinY=-0.5 expectedOutlineMinY=-0.5";
        String reason = firstRedReason(
                customPolicyReason(support),
                !"true".equals(directSupported) ? "direct_custom_supported_object_false" : null,
                mismatch("subject_dy_mismatch", subjectDy, -0.5d),
                mismatch("model_min_y_mismatch", shape.modelMinY(), -0.5d),
                mismatch("outline_min_y_mismatch", shape.outlineMinY(), -0.5d),
                !hitPosEquals(shape.resolvedHit(), slabPos) ? "lowered_visible_target_mismatch" : null);
        return lane("TERRAIN_SLABS_VANILLA_SLAB_SUBJECT", reason, fields);
    }

    private static LaneResult probeFenceSubjectLane(MinecraftClient mc, BlockPos supportPos, BlockPos fencePos) {
        BlockState support = mc.world.getBlockState(supportPos);
        BlockState subject = mc.world.getBlockState(fencePos);
        double subjectDy = SlabSupport.getYOffset(mc.world, fencePos, subject);
        ShapeFacts shape = shapeFacts(mc, fencePos, subject, fencePos.getY() - 0.25d);
        String directSupported = directCustomSupportedObject(mc, fencePos, subject);
        String fields = commonSupportFields(mc, supportPos, support)
                + commonSubjectFields(mc, "minecraft:oak_fence", fencePos, subject, subjectDy, shape)
                + " expectedDirectCustomSupportedObject=true"
                + " expectedSubjectDy=-0.5 expectedModelMinY=-0.5 expectedOutlineMinY=-0.5";
        String reason = firstRedReason(
                customPolicyReason(support),
                !"true".equals(directSupported) ? "direct_custom_supported_object_false" : null,
                mismatch("subject_dy_mismatch", subjectDy, -0.5d),
                mismatch("model_min_y_mismatch", shape.modelMinY(), -0.5d),
                mismatch("outline_min_y_mismatch", shape.outlineMinY(), -0.5d),
                !hitPosEquals(shape.resolvedHit(), fencePos) ? "lowered_visible_target_mismatch" : null);
        return lane("TERRAIN_SLABS_FENCE_SUBJECT", reason, fields);
    }

    private static LaneResult probeWallSubjectLane(MinecraftClient mc, BlockPos supportPos, BlockPos wallPos) {
        BlockState support = mc.world.getBlockState(supportPos);
        BlockState subject = mc.world.getBlockState(wallPos);
        double subjectDy = SlabSupport.getYOffset(mc.world, wallPos, subject);
        ShapeFacts shape = shapeFacts(mc, wallPos, subject, wallPos.getY() - 0.25d);
        String directSupported = directCustomSupportedObject(mc, wallPos, subject);
        String fields = commonSupportFields(mc, supportPos, support)
                + commonSubjectFields(mc, "minecraft:cobblestone_wall", wallPos, subject, subjectDy, shape)
                + " expectedDirectCustomSupportedObject=true"
                + " expectedSubjectDy=-0.5 expectedModelMinY=-0.5 expectedOutlineMinY=-0.5";
        String reason = firstRedReason(
                customPolicyReason(support),
                !"true".equals(directSupported) ? "direct_custom_supported_object_false" : null,
                mismatch("subject_dy_mismatch", subjectDy, -0.5d),
                mismatch("model_min_y_mismatch", shape.modelMinY(), -0.5d),
                mismatch("outline_min_y_mismatch", shape.outlineMinY(), -0.5d),
                !hitPosEquals(shape.resolvedHit(), wallPos) ? "lowered_visible_target_mismatch" : null);
        return lane("TERRAIN_SLABS_WALL_SUBJECT", reason, fields);
    }

    private static LaneResult probePaneSubjectLane(MinecraftClient mc, BlockPos supportPos, BlockPos panePos) {
        BlockState support = mc.world.getBlockState(supportPos);
        BlockState subject = mc.world.getBlockState(panePos);
        double subjectDy = SlabSupport.getYOffset(mc.world, panePos, subject);
        ShapeFacts shape = shapeFacts(mc, panePos, subject, panePos.getY() - 0.25d);
        String directSupported = directCustomSupportedObject(mc, panePos, subject);
        String fields = commonSupportFields(mc, supportPos, support)
                + commonSubjectFields(mc, "minecraft:glass_pane", panePos, subject, subjectDy, shape)
                + " expectedDirectCustomSupportedObject=true"
                + " expectedSubjectDy=-0.5 expectedModelMinY=-0.5 expectedOutlineMinY=-0.5";
        String reason = firstRedReason(
                customPolicyReason(support),
                !"true".equals(directSupported) ? "direct_custom_supported_object_false" : null,
                mismatch("subject_dy_mismatch", subjectDy, -0.5d),
                mismatch("model_min_y_mismatch", shape.modelMinY(), -0.5d),
                mismatch("outline_min_y_mismatch", shape.outlineMinY(), -0.5d),
                !hitPosEquals(shape.resolvedHit(), panePos) ? "lowered_visible_target_mismatch" : null);
        return lane("TERRAIN_SLABS_PANE_SUBJECT", reason, fields);
    }

    private static List<LaneResult> probeObjectCategoryMatrixBatched(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            List<MatrixSupportCase> supports,
            List<MatrixSubjectCase> subjects
    ) {
        List<LaneResult> lanes = new ArrayList<>();
        for (int start = 0; start < supports.size(); start += MATRIX_SUPPORT_ROWS_PER_BATCH) {
            int end = Math.min(start + MATRIX_SUPPORT_ROWS_PER_BATCH, supports.size());
            List<MatrixSupportCase> batch = supports.subList(start, end);
            singleplayer.getServer().runOnServer(server -> {
                var world = server.getOverworld();
                clearMatrixArea(world, subjects);
                placeObjectCategoryMatrix(world, batch, subjects);
            });
            ctx.waitTick();
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            ctx.runOnClient(mc -> lanes.addAll(probeObjectCategoryMatrix(mc, batch, subjects)));
        }
        return lanes;
    }

    private static List<LaneResult> probeObjectCategoryMatrix(
            MinecraftClient mc,
            List<MatrixSupportCase> supports,
            List<MatrixSubjectCase> subjects
    ) {
        List<LaneResult> lanes = new ArrayList<>();
        for (MatrixSupportCase supportCase : supports) {
            for (MatrixSubjectCase subjectCase : subjects) {
                BlockPos supportPos = supportCase.pos(subjectCase);
                BlockState support = mc.world.getBlockState(supportPos);
                String actualSupportKind = customSurfaceKind(support);
                BlockPos subjectPos = supportCase.subjectPos(subjectCase);
                BlockState subject = mc.world.getBlockState(subjectPos);
                double subjectDy = SlabSupport.getYOffset(mc.world, subjectPos, subject);
                double expectedDy = subjectCase.expectedDy(actualSupportKind);
                double expectedVisualMinY = subjectCase.expectedVisualMinY(actualSupportKind);
                ShapeFacts shape = shapeFacts(mc, subjectPos, subject,
                        subjectCase.lowerProbeTargetY(subjectPos, actualSupportKind));
                boolean canPlaceAt = subject.canPlaceAt(mc.world, subjectPos);
                String directSupported = directCustomSupportedObject(mc, subjectPos, subject);

                String fields = commonSupportFields(mc, supportPos, support)
                        + commonSubjectFields(mc, subjectCase.expectedId(), subjectPos, subject, subjectDy, shape)
                        + " matrixSupportIndex=" + supportCase.supportIndex()
                        + " matrixSubjectIndex=" + subjectCase.subjectIndex()
                        + " matrixSupportId=" + supportCase.supportId()
                        + " matrixExpectedSupportKind=" + supportCase.expectedKind()
                        + " matrixActualSupportKind=" + actualSupportKind
                        + " matrixSubjectCategory=" + subjectCase.name()
                        + " canPlaceAt=" + canPlaceAt
                        + " expectedCanPlaceAt=" + subjectCase.expectedCanPlaceAt(actualSupportKind)
                        + " expectedSubjectDy=" + expectedDy
                        + " expectedVisualMinY=" + expectedVisualMinY
                        + " expectedDirectCustomSupportedObject=" + subjectCase.expectedDirectCustomSupportedObject(actualSupportKind);

                String reason = firstRedReason(
                        !supportCase.expectedKind().equals(actualSupportKind) ? "matrix_support_kind_mismatch" : null,
                        Math.abs(skipNaN(directTopOffset(support)) - supportCase.expectedTopOffset()) > 1.0e-6d
                                ? "matrix_direct_top_offset_mismatch" : null,
                        subjectCase.expectedCanPlaceAt(actualSupportKind) && !canPlaceAt
                                ? "matrix_can_place_at_false" : null,
                        subjectCase.expectedDirectCustomSupportedObject(actualSupportKind) && !"true".equals(directSupported)
                                ? "matrix_direct_custom_supported_object_false" : null,
                        subjectCase.checkSubjectDy(actualSupportKind)
                                ? mismatch("matrix_subject_dy_mismatch", subjectDy, expectedDy)
                                : null,
                        subjectCase.checkModel(actualSupportKind)
                                ? mismatch("matrix_model_min_y_mismatch", shape.modelMinY(), expectedVisualMinY)
                                : null,
                        subjectCase.checkOutline(actualSupportKind)
                                ? mismatch("matrix_outline_min_y_mismatch", shape.outlineMinY(), expectedVisualMinY)
                                : null,
                        subjectCase.expectVisibleTarget(actualSupportKind) && !hitPosEquals(shape.resolvedHit(), subjectPos)
                                ? "matrix_lowered_visible_target_mismatch"
                                : null);
                lanes.add(lane("TERRAIN_SLABS_OBJECT_MATRIX_" + supportCase.markerSuffix()
                        + "_" + subjectCase.markerSuffix(), reason, fields));
            }
        }
        return lanes;
    }

    private static String commonSupportFields(MinecraftClient mc, BlockPos supportPos, BlockState support) {
        return " supportId=" + supportId(support)
                + " supportPos=" + supportPos.toShortString()
                + " supportState=" + support
                + " shouldSkipOffset=" + CompatHooks.shouldSkipOffset(support)
                + " shouldSkipSlabSupport=" + compatSkipSlabSupport(support)
                + " customSurfaceKind=" + customSurfaceKind(support)
                + " isSupportingSlab=" + SlabSupport.isSupportingSlab(support)
                + " isBottomSlab=" + SlabSupport.isBottomSlab(support)
                + " isTopSlab=" + SlabSupport.isTopSlab(support)
                + " directObjectSupportSurface=" + directObjectSupportSurface(mc, supportPos, support)
                + " directTopOffset=" + directTopOffset(support)
                + " supportSelfDy=" + SlabSupport.getYOffset(mc.world, supportPos, support);
    }

    private static String commonSubjectFields(
            MinecraftClient mc, String expectedId, BlockPos pos, BlockState state, double dy, ShapeFacts shape
    ) {
        return " expectedSubjectId=" + expectedId
                + " subjectId=" + supportId(state)
                + " subjectPos=" + pos.toShortString()
                + " subjectState=" + state
                + " subjectDy=" + dy
                + " directCustomSupportedObject=" + directCustomSupportedObject(mc, pos, state)
                + " modelMinY=" + shape.modelMinY()
                + " outlineMinY=" + shape.outlineMinY()
                + " raycastShapeMinY=" + shape.raycastMinY()
                + " lowerProbeWorldHit=" + describeHit(shape.worldHit())
                + " lowerProbeBreakOwner=" + breakOwner(mc, shape.worldHit())
                + " lowerProbeResolvedHit=" + describeHit(shape.resolvedHit())
                + " lowerProbeResolvedBreakOwner=" + breakOwner(mc, shape.resolvedHit());
    }

    private static String customPolicyReason(BlockState support) {
        return firstRedReason(
                customSkipSupportMissingReason(support),
                !"BOTTOM_LIKE".equals(customSurfaceKind(support)) ? "custom_bottom_surface_kind_missing" : null,
                SlabSupport.isSupportingSlab(support) ? "terrain_slab_still_generic_support" : null,
                Math.abs(skipNaN(directTopOffset(support)) - 0.5d) > 1.0e-6d ? "direct_top_offset_mismatch" : null);
    }

    private static String customSkipSupportMissingReason(BlockState support) {
        ReflectBool skipSupport = compatSkipSlabSupport(support);
        if (!skipSupport.present()) {
            return "missing_shouldSkipSlabSupport_hook";
        }
        return !skipSupport.value() ? "terrain_slab_generic_support_not_skipped" : null;
    }

    private static LaneResult lane(String markerPrefix, String reason, String fields) {
        boolean red = reason != null;
        String marker = markerPrefix + (red ? "_RED reason=" + reason : "_GREEN") + fields;
        return new LaneResult(markerPrefix, red, red ? reason : "green", marker);
    }

    private static String firstRedReason(String... reasons) {
        for (String reason : reasons) {
            if (reason != null && !reason.isBlank()) {
                return reason;
            }
        }
        return null;
    }

    private static String mismatch(String name, double actual, double expected) {
        if (Double.isNaN(actual)) {
            return name + "_nan";
        }
        return Math.abs(actual - expected) > 1.0e-6d ? name : null;
    }

    private static double skipNaN(double value) {
        return Double.isNaN(value) ? Double.POSITIVE_INFINITY : value;
    }

    private static ShapeFacts shapeFacts(MinecraftClient mc, BlockPos pos, BlockState state, double targetY) {
        VoxelShape outline = state.getOutlineShape(mc.world, pos, ShapeContext.of(mc.player));
        VoxelShape raycast = state.getRaycastShape(mc.world, pos);
        return new ShapeFacts(
                modelMinY(mc.world, pos, state),
                outline.isEmpty() ? Double.NaN : outline.getBoundingBox().minY,
                raycast.isEmpty() ? Double.NaN : raycast.getBoundingBox().minY,
                rayProbeAtY(mc, pos, targetY));
    }

    private static RayProbe rayProbeAtY(MinecraftClient mc, BlockPos pos, double targetY) {
        Vec3d eye = new Vec3d(pos.getX() + 0.5d, targetY, pos.getZ() + 2.5d);
        Vec3d end = new Vec3d(pos.getX() + 0.5d, targetY, pos.getZ() - 0.5d);
        HitResult worldHit = mc.world.raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player));
        return new RayProbe(worldHit, resolveLoweredVisibleOwner(mc, worldHit, eye, end));
    }

    private static HitResult resolveLoweredVisibleOwner(MinecraftClient mc, HitResult worldHit, Vec3d eye, Vec3d end) {
        if (!(worldHit instanceof BlockHitResult blockHit) || worldHit.getType() != HitResult.Type.BLOCK) {
            return worldHit;
        }

        BlockPos abovePos = blockHit.getBlockPos().up();
        BlockState aboveState = mc.world.getBlockState(abovePos);
        boolean loweredOwner =
                SlabSupport.isLoweredBlockEntityVisual(mc.world, abovePos, aboveState)
                        || SlabSupport.isLoweredTorchVisual(mc.world, abovePos, aboveState)
                        || SlabSupport.isLoweredBedVisual(mc.world, abovePos, aboveState)
                        || SlabSupport.isLoweredCustomSupportedObjectVisual(mc.world, abovePos, aboveState);
        if (!loweredOwner) {
            Block block = aboveState.getBlock();
            loweredOwner = aboveState.isSolidBlock(mc.world, abovePos)
                    && !(block instanceof net.minecraft.block.BlockEntityProvider)
                    && !(block instanceof net.minecraft.block.CraftingTableBlock)
                    && SlabSupport.getYOffset(mc.world, abovePos, aboveState) == -0.5;
        }
        if (!loweredOwner) {
            return worldHit;
        }

        Vec3d dir = end.subtract(eye).normalize();
        double originalDistance = blockHit.getPos().distanceTo(eye);
        if (originalDistance <= 0.0d) {
            return worldHit;
        }
        Vec3d extendedEnd = eye.add(dir.multiply(originalDistance + 0.5d));
        VoxelShape aboveOutline = aboveState.getOutlineShape(mc.world, abovePos, ShapeContext.of(mc.player));
        BlockHitResult retargetHit = aboveOutline.raycast(eye, extendedEnd, abovePos);
        if (retargetHit == null) {
            return worldHit;
        }
        return retargetHit.getPos().squaredDistanceTo(eye) <= blockHit.getPos().squaredDistanceTo(eye) + 1.0e-6d
                ? retargetHit
                : worldHit;
    }

    private static boolean hitPosEquals(HitResult hit, BlockPos pos) {
        return hit instanceof BlockHitResult blockHit
                && hit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(pos);
    }

    private static String breakOwner(MinecraftClient mc, HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return "none";
        }
        BlockState state = mc.world.getBlockState(blockHit.getBlockPos());
        return supportId(state) + "@" + blockHit.getBlockPos().toShortString();
    }

    private static String describeHit(HitResult hit) {
        if (hit == null) {
            return "null";
        }
        if (hit instanceof BlockHitResult blockHit) {
            return hit.getType() + ":" + blockHit.getBlockPos().toShortString() + ":" + blockHit.getSide();
        }
        return hit.getType().toString();
    }

    private static double modelMinY(net.minecraft.world.BlockRenderView world, BlockPos pos, BlockState state) {
        try {
            BlockStateModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(state);
            if (!(model instanceof FabricBlockStateModel fabricModel)) {
                return Double.NaN;
            }
            Renderer renderer = Renderer.get();
            if (renderer == null) {
                return Double.NaN;
            }
            MutableMesh mesh = renderer.mutableMesh();
            fabricModel.emitQuads(mesh.emitter(), world, pos, state, Random.create(0x51abbEDL), direction -> false);
            if (mesh.size() <= 0) {
                return Double.NaN;
            }
            ModelBounds bounds = new ModelBounds();
            mesh.forEach(bounds::accept);
            return bounds.minY;
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    private static BlockState terrainDirtSlabState(SlabType type) {
        Block block = terrainDirtSlabBlock();
        BlockState state = block.getDefaultState();
        if (!state.contains(SlabBlock.TYPE)) {
            throw new AssertionError("Terrain Slabs dirt-like slab has no SlabBlock.TYPE: "
                    + supportId(state) + " state=" + state);
        }
        state = state.with(SlabBlock.TYPE, type);
        state = withPropertyValue(state, "generated", "false");
        state = withPropertyValue(state, "snowy", "false");
        return state;
    }

    private static Block terrainDirtSlabBlock() {
        Optional<Identifier> dirt = Registries.BLOCK.getIds().stream()
                .filter(id -> TerrainSlabsCompat.MOD_ID.equals(id.getNamespace()))
                .filter(id -> id.getPath().contains("dirt") && id.getPath().contains("slab"))
                .filter(id -> Registries.BLOCK.get(id).getDefaultState().contains(SlabBlock.TYPE))
                .findFirst();
        Optional<Identifier> any = Registries.BLOCK.getIds().stream()
                .filter(id -> TerrainSlabsCompat.MOD_ID.equals(id.getNamespace()))
                .filter(id -> id.getPath().contains("slab"))
                .filter(id -> Registries.BLOCK.get(id).getDefaultState().contains(SlabBlock.TYPE))
                .findFirst();
        Identifier id = dirt.or(() -> any)
                .orElseThrow(() -> new AssertionError("no Terrain Slabs slab block with SlabBlock.TYPE is loaded"));
        return Registries.BLOCK.get(id);
    }

    private static List<MatrixSupportCase> terrainSlabMatrixSupportCases() {
        List<MatrixSupportCase> cases = new ArrayList<>();
        List<Identifier> ids = Registries.BLOCK.getIds().stream()
                .filter(id -> TerrainSlabsCompat.MOD_ID.equals(id.getNamespace()))
                .filter(id -> id.getPath().contains("slab"))
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
        int index = 0;
        for (Identifier id : ids) {
            Block block = Registries.BLOCK.get(id);
            BlockState state = block.getDefaultState();
            if (!state.contains(SlabBlock.TYPE)) {
                continue;
            }
            state = withPropertyValue(state, "generated", "false");
            state = withPropertyValue(state, "snowy", "false");
            state = withPropertyValue(state, "waterlogged", "false");
            for (SlabType type : List.of(SlabType.BOTTOM, SlabType.TOP, SlabType.DOUBLE)) {
                BlockState typed = state.with(SlabBlock.TYPE, type);
                if (!typed.getFluidState().isEmpty()) {
                    continue;
                }
                String expectedKind = switch (type) {
                    case BOTTOM -> "BOTTOM_LIKE";
                    case TOP -> "TOP_LIKE";
                    case DOUBLE -> "DOUBLE_LIKE";
                };
                double expectedTopOffset = type == SlabType.BOTTOM ? 0.5d : 1.0d;
                cases.add(new MatrixSupportCase(index, id.toString(), typed, expectedKind, expectedTopOffset));
                index++;
            }
        }
        return cases;
    }

    private static List<MatrixSubjectCase> matrixSubjectCases() {
        return List.of(
                MatrixSubjectCase.control(0, "PLAIN_SOLID_CONTROL", Blocks.STONE.getDefaultState()),
                MatrixSubjectCase.lowering(1, "BLOCK_ENTITY_CHEST", Blocks.CHEST.getDefaultState(), false, true),
                MatrixSubjectCase.lowering(2, "CRAFTING_TABLE", Blocks.CRAFTING_TABLE.getDefaultState(), true, true),
                MatrixSubjectCase.lowering(3, "LOG_FAMILY", Blocks.OAK_LOG.getDefaultState(), true, true),
                MatrixSubjectCase.lowering(4, "VANILLA_SLAB", Blocks.OAK_SLAB.getDefaultState()
                        .with(SlabBlock.TYPE, SlabType.BOTTOM), true, true),
                MatrixSubjectCase.lowering(5, "FENCE", Blocks.OAK_FENCE.getDefaultState(), true, true),
                MatrixSubjectCase.lowering(6, "WALL", Blocks.COBBLESTONE_WALL.getDefaultState(), true, true),
                MatrixSubjectCase.lowering(7, "PANE", Blocks.GLASS_PANE.getDefaultState(), true, true),
                MatrixSubjectCase.lowering(8, "FLOOR_TORCH", Blocks.TORCH.getDefaultState(), true, true),
                MatrixSubjectCase.lowering(9, "FLOOR_LANTERN", Blocks.LANTERN.getDefaultState()
                        .with(Properties.HANGING, false), true, true),
                MatrixSubjectCase.lowering(10, "COBWEB", Blocks.COBWEB.getDefaultState(), true, true),
                MatrixSubjectCase.lowProfileLowering(11, "PRESSURE_PLATE",
                        Blocks.OAK_PRESSURE_PLATE.getDefaultState(), true, true),
                MatrixSubjectCase.thinLayer(12, "CARPET", Blocks.WHITE_CARPET.getDefaultState()),
                MatrixSubjectCase.thinLayer(13, "PALE_MOSS_CARPET", Blocks.PALE_MOSS_CARPET.getDefaultState()),
                MatrixSubjectCase.lowering(14, "BOOKSHELF", Blocks.BOOKSHELF.getDefaultState(), true, true),
                MatrixSubjectCase.lowering(15, "CHISELED_BOOKSHELF", Blocks.CHISELED_BOOKSHELF.getDefaultState(), true, true),
                MatrixSubjectCase.lowering(16, "DRIED_KELP_BLOCK", Blocks.DRIED_KELP_BLOCK.getDefaultState(), true, true),
                MatrixSubjectCase.aquaticLowering(17, "KELP", Blocks.KELP.getDefaultState(), true, true),
                MatrixSubjectCase.aquaticLowering(18, "KELP_PLANT", Blocks.KELP_PLANT.getDefaultState(), true, true)
        );
    }

    private static void clearMatrixArea(net.minecraft.world.World world, List<MatrixSubjectCase> subjects) {
        for (int row = 0; row < MATRIX_SUPPORT_ROWS_PER_BATCH; row++) {
            for (MatrixSubjectCase subjectCase : subjects) {
                clearColumn(world, matrixPos(row, subjectCase));
            }
        }
    }

    private static void placeObjectCategoryMatrix(
            net.minecraft.world.World world,
            List<MatrixSupportCase> supports,
            List<MatrixSubjectCase> subjects
    ) {
        for (MatrixSupportCase supportCase : supports) {
            for (MatrixSubjectCase subjectCase : subjects) {
                BlockPos supportPos = supportCase.pos(subjectCase);
                world.setBlockState(supportPos.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(supportPos, supportCase.state(), Block.NOTIFY_LISTENERS);
                if (subjectCase.aquatic) {
                    world.setBlockState(supportPos.up(), Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
                    world.setBlockState(supportPos.up(2), Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                world.setBlockState(supportPos.up(), subjectCase.state(), Block.NOTIFY_LISTENERS);
            }
        }
    }

    private static BlockState withPropertyValue(BlockState state, String propertyName, String expectedValue) {
        for (Property<?> property : state.getProperties()) {
            if (propertyName.equals(property.getName())) {
                return withTypedPropertyValue(state, property, expectedValue);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withTypedPropertyValue(
            BlockState state, Property<T> property, String expectedValue
    ) {
        for (T value : property.getValues()) {
            if (expectedValue.equals(property.name(value))) {
                return state.with(property, value);
            }
        }
        return state;
    }

    private static BlockState floorPointedDripstone() {
        BlockState state = Blocks.POINTED_DRIPSTONE.getDefaultState();
        if (state.contains(Properties.VERTICAL_DIRECTION)) {
            state = state.with(Properties.VERTICAL_DIRECTION, Direction.UP);
        }
        return state;
    }

    private static BlockState bedState(BedPart part) {
        return Blocks.RED_BED.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.SOUTH)
                .with(Properties.BED_PART, part);
    }

    private static List<LogFamilyCase> logFamilyCases() {
        return List.of(
                new LogFamilyCase("OAK_LOG", LOG_FAMILY_SUPPORT_START_POS, Blocks.OAK_LOG.getDefaultState()),
                new LogFamilyCase("SPRUCE_LOG", LOG_FAMILY_SUPPORT_START_POS.add(3, 0, 0),
                        Blocks.SPRUCE_LOG.getDefaultState()),
                new LogFamilyCase("STRIPPED_OAK_WOOD", LOG_FAMILY_SUPPORT_START_POS.add(6, 0, 0),
                        Blocks.STRIPPED_OAK_WOOD.getDefaultState()),
                new LogFamilyCase("CRIMSON_STEM", LOG_FAMILY_SUPPORT_START_POS.add(9, 0, 0),
                        Blocks.CRIMSON_STEM.getDefaultState()));
    }

    private static void clearColumn(net.minecraft.world.World world, BlockPos pos) {
        for (int dy = -2; dy <= 4; dy++) {
            world.setBlockState(pos.up(dy), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }

    private static ReflectBool compatSkipSlabSupport(BlockState state) {
        try {
            Method method = CompatHooks.class.getMethod("shouldSkipSlabSupport", BlockState.class);
            Object result = method.invoke(null, state);
            return new ReflectBool(true, Boolean.TRUE.equals(result));
        } catch (NoSuchMethodException e) {
            return new ReflectBool(false, false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to invoke CompatHooks.shouldSkipSlabSupport", e);
        }
    }

    private static String customSurfaceKind(BlockState state) {
        try {
            Method method = CompatHooks.class.getMethod("customSlabSurfaceKind", BlockState.class);
            Object result = method.invoke(null, state);
            return String.valueOf(result);
        } catch (NoSuchMethodException e) {
            return "MISSING";
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to invoke CompatHooks.customSlabSurfaceKind", e);
        }
    }

    private static String directObjectSupportSurface(MinecraftClient mc, BlockPos pos, BlockState state) {
        try {
            Method method = SlabSupport.class.getMethod(
                    "isDirectObjectSupportSurface",
                    net.minecraft.world.BlockView.class,
                    BlockPos.class,
                    BlockState.class);
            return Boolean.toString(Boolean.TRUE.equals(method.invoke(null, mc.world, pos, state)));
        } catch (NoSuchMethodException e) {
            return "MISSING";
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to invoke SlabSupport.isDirectObjectSupportSurface", e);
        }
    }

    private static String directCustomSupportedObject(MinecraftClient mc, BlockPos pos, BlockState state) {
        try {
            Method method = SlabSupport.class.getMethod(
                    "isDirectCustomSlabSupportedObject",
                    net.minecraft.world.BlockView.class,
                    BlockPos.class,
                    BlockState.class);
            return Boolean.toString(Boolean.TRUE.equals(method.invoke(null, mc.world, pos, state)));
        } catch (NoSuchMethodException e) {
            return "MISSING";
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to invoke SlabSupport.isDirectCustomSlabSupportedObject", e);
        }
    }

    private static double directTopOffset(BlockState state) {
        try {
            Method method = SlabSupport.class.getMethod("getDirectObjectSupportTopOffset", BlockState.class);
            Object result = method.invoke(null, state);
            return result instanceof Number number ? number.doubleValue() : Double.NaN;
        } catch (NoSuchMethodException e) {
            return Double.NaN;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to invoke SlabSupport.getDirectObjectSupportTopOffset", e);
        }
    }

    private static String supportId(BlockState state) {
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        return id == null ? "unknown" : id.toString();
    }

    private static void writeArtifact(List<String> lines) {
        writeArtifact(ARTIFACT, lines);
    }

    private static void writeArtifact(Path artifact, List<String> lines) {
        try {
            Files.createDirectories(artifact.getParent());
            Files.writeString(artifact, String.join(System.lineSeparator(), lines) + System.lineSeparator());
        } catch (Exception e) {
            throw new AssertionError("failed to write Terrain Slabs proof artifact " + artifact.toAbsolutePath(), e);
        }
    }

    private record ReflectBool(boolean present, boolean value) {
        @Override
        public String toString() {
            return present ? Boolean.toString(value) : "MISSING";
        }
    }

    private record RayProbe(HitResult worldHit, HitResult resolvedHit) {
    }

    private record ShapeFacts(
            double modelMinY, double outlineMinY, double raycastMinY, HitResult worldHit, HitResult resolvedHit
    ) {
        private ShapeFacts(double modelMinY, double outlineMinY, double raycastMinY, RayProbe rayProbe) {
            this(modelMinY, outlineMinY, raycastMinY, rayProbe.worldHit(), rayProbe.resolvedHit());
        }
    }

    private record LogFamilyCase(String markerSuffix, BlockPos supportPos, BlockState state) {
        private BlockPos subjectPos() {
            return supportPos.up();
        }

        private String expectedId() {
            return supportId(state);
        }
    }

    private record LaneResult(String name, boolean red, String reason, String marker) {
    }

    private record MatrixSupportCase(
            int supportIndex,
            String supportId,
            BlockState state,
            String expectedKind,
            double expectedTopOffset
    ) {
        private BlockPos pos(MatrixSubjectCase subjectCase) {
            return matrixPos(displayRow(), subjectCase);
        }

        private BlockPos subjectPos(MatrixSubjectCase subjectCase) {
            return pos(subjectCase).up();
        }

        private int displayRow() {
            return Math.floorMod(supportIndex, MATRIX_SUPPORT_ROWS_PER_BATCH);
        }

        private String markerSuffix() {
            return "S" + supportIndex + "_" + sanitize(supportId);
        }
    }

    private record MatrixSubjectCase(
            int subjectIndex,
            String name,
            BlockState state,
            boolean lowersOnBottomLike,
            boolean thinLayer,
            boolean lowProfileProbe,
            boolean modelCheck,
            boolean placementCheck,
            boolean aquatic
    ) {
        private static MatrixSubjectCase control(int index, String name, BlockState state) {
            return new MatrixSubjectCase(index, name, state, false, false, false, true, false, false);
        }

        private static MatrixSubjectCase lowering(
                int index, String name, BlockState state, boolean modelCheck, boolean placementCheck
        ) {
            return new MatrixSubjectCase(index, name, state, true, false, false, modelCheck, placementCheck, false);
        }

        private static MatrixSubjectCase lowProfileLowering(
                int index, String name, BlockState state, boolean modelCheck, boolean placementCheck
        ) {
            return new MatrixSubjectCase(index, name, state, true, false, true, modelCheck, placementCheck, false);
        }

        private static MatrixSubjectCase thinLayer(int index, String name, BlockState state) {
            return new MatrixSubjectCase(index, name, state, true, true, true, true, true, false);
        }

        private static MatrixSubjectCase aquaticLowering(
                int index, String name, BlockState state, boolean modelCheck, boolean placementCheck
        ) {
            return new MatrixSubjectCase(index, name, state, true, false, false, modelCheck, placementCheck, true);
        }

        private String expectedId() {
            return supportId(state);
        }

        private String markerSuffix() {
            return sanitize(name);
        }

        private boolean isBottomLike(String supportKind) {
            return "BOTTOM_LIKE".equals(supportKind);
        }

        private double expectedDy(String supportKind) {
            return lowersOnBottomLike && isBottomLike(supportKind) ? -0.5d : 0.0d;
        }

        private double expectedVisualMinY(String supportKind) {
            return expectedDy(supportKind);
        }

        private double lowerProbeTargetY(BlockPos pos, String supportKind) {
            double inset = lowProfileProbe ? 1.0d / 32.0d : 0.25d;
            return pos.getY() + expectedVisualMinY(supportKind) + inset;
        }

        private boolean expectedCanPlaceAt(String supportKind) {
            return placementCheck && !"NONE".equals(supportKind) && !"UNKNOWN".equals(supportKind);
        }

        private boolean expectedDirectCustomSupportedObject(String supportKind) {
            return lowersOnBottomLike && !thinLayer && isBottomLike(supportKind);
        }

        private boolean checkSubjectDy(String supportKind) {
            return !thinLayer && (lowersOnBottomLike || !isBottomLike(supportKind));
        }

        private boolean checkModel(String supportKind) {
            return modelCheck && (lowersOnBottomLike || !isBottomLike(supportKind));
        }

        private boolean checkOutline(String supportKind) {
            return lowersOnBottomLike || !isBottomLike(supportKind);
        }

        private boolean expectVisibleTarget(String supportKind) {
            return lowersOnBottomLike && !thinLayer && isBottomLike(supportKind);
        }
    }

    private static String sanitize(String value) {
        return value.replace(':', '_')
                .replace('/', '_')
                .replace('[', '_')
                .replace(']', '_')
                .replace('=', '_')
                .replace(',', '_')
                .toUpperCase();
    }

    private static BlockPos matrixPos(int displayRow, MatrixSubjectCase subjectCase) {
        return MATRIX_START_POS.add(
                subjectCase.subjectIndex() * MATRIX_SUBJECT_SPACING,
                0,
                displayRow * MATRIX_SUPPORT_SPACING);
    }

    private static final class ModelBounds {
        double minY = Double.POSITIVE_INFINITY;

        void accept(QuadView quad) {
            minY = Math.min(minY, Math.min(Math.min(quad.y(0), quad.y(1)), Math.min(quad.y(2), quad.y(3))));
        }
    }
}
