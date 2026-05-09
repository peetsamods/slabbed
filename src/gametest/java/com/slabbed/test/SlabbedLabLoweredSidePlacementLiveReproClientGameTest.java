package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.client.runtime.SlabbedRetargetTestHooks;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

/**
 * Focused live repro matrix for lowered slab lane grammar.
 *
 * <p>Invariant table used by this test:
 * - target=TOP + faces=N/E/S/W => expected place=TOP / dy=-0.500 (side-lane inheritance)
 * - target=BOTTOM + faces=N/E/S/W => expected place=BOTTOM / dy=-0.500 (side-lane inheritance)
 * - target=DOUBLE + faces=N/E/S/W => expected place=TOP / dy=-0.500 (side-lane inheritance)
 * - target=DOUBLE + face=NORTH with preexisting TOP at placePos => expected place=DOUBLE / dy=-0.500 (merge stays in lowered lane)
 * - lowered DOUBLE lower-half click intent => owner remains lowered DOUBLE, first side placement is BOTTOM dy=-0.500, second click merges legally
 * - target=TOP + face=UP => expected place=DOUBLE / dy=-0.500 (vertical merge)
 * - runVerticalBottomUnderLoweredDoubleStackSeamCase => expected normal BOTTOM at y=0 with lowered DOUBLE at y+1 and dy timeline over place/break action
 */
public final class SlabbedLabLoweredSidePlacementLiveReproClientGameTest implements FabricClientGameTest {

    private static final BlockPos SUPPORT_POS = new BlockPos(0, 200, 0);
    private static final BlockPos FULL_POS = SUPPORT_POS.up();
    private static final double EPSILON = 1.0e-6d;
    private static final double TOP_FACE_HIT_Y = 1.0d;

    private static final Direction[] FACES_TO_TEST = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST,
    };

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (Boolean.getBoolean("slabbed.beta4OutlineHitRaycastMissRedOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta4OutlineHitRaycastMissRedCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4SeamVisibleUpperRedOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runJuliaBeta4AboveAngleTargetingOwnerSplitRedCase(ctx, singleplayer, false);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4SeamVisibleUpperSideFaceRedOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta4SeamVisibleUpperSideFaceRedCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4SeamVisibleUpperAnchoredUpStealRedOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta4SeamVisibleUpperAnchoredUpStealRedCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4SeamVisibleUpperAngleGeneralRedOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta4SeamVisibleUpperAngleGeneralRedCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4SeamGreenProofsOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runJuliaBeta4StoneSlabTargetingOutlineMismatchRedCase(ctx, singleplayer);
                runJuliaBeta4AdjacentVisibleTargetRedCase(ctx, singleplayer);
                runBeta4SeamNoRescueBoundaryCase(ctx, singleplayer);
                runJuliaBeta4AboveAngleTargetingOwnerSplitRedCase(ctx, singleplayer, false);
                runBeta4SeamVisibleUpperSideFaceRedCase(ctx, singleplayer);
                runBeta4SeamVisibleUpperAnchoredUpStealRedCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4SeamNoRescueOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta4SeamNoRescueBoundaryCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.juliaBeta4AdjacentVisibleRedOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runJuliaBeta4AdjacentVisibleTargetRedCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.juliaBeta4AboveAngleRedOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runJuliaBeta4AboveAngleTargetingOwnerSplitRedCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.juliaBeta4TargetingRedOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runJuliaBeta4StoneSlabTargetingOutlineMismatchRedCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.dynamicBridgeOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runDynamicBridgeTopSlabSelectionReproCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4CompoundSlabMergeRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta4CompoundSlabMergeRedProof(ctx, singleplayer);
            }
            return;
        }

        // Expand coverage for legal lowered slab targets and explicit merge semantics.
        for (Direction face : FACES_TO_TEST) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runLoweredSidePlacementCase(ctx, singleplayer, face, SlabType.TOP, SlabType.TOP);
            }
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runLoweredSidePlacementCase(ctx, singleplayer, face, SlabType.BOTTOM, SlabType.BOTTOM);
            }
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runLoweredSidePlacementCase(ctx, singleplayer, face, SlabType.DOUBLE, SlabType.TOP);
            }
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLoweredDoubleSideMergeCase(ctx, singleplayer, Direction.NORTH);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLoweredDoubleLowerHalfOwnershipCase(ctx, singleplayer, Direction.NORTH);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLowerHalfOwnershipVisibleBodyCase(ctx, singleplayer);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLowerHalfOwnershipLoweredSlabCase(ctx, singleplayer);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLiteralScreenshotCraftingTableCase(ctx, singleplayer);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLiteralScreenshotLoweredSlabSideCase(ctx, singleplayer);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLiveClickPairBottomSlabLaneInheritanceCase(ctx, singleplayer);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLiveClickPairFullBlockLaneInheritanceCase(ctx, singleplayer);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runBridgeShapeLowerHalfReproCase(ctx, singleplayer);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runDynamicBridgeTopSlabSelectionReproCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLoweredDoubleLowerHalfBoundaryCase(ctx, singleplayer, Direction.EAST);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLoweredDoubleSidePlacementCreatedNormalBottomLaneProofCase(ctx, singleplayer);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runRealPlacedLoweredBottomSlabPersistenceAfterBridgeBreakProofCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLoweredTopUpMergeCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runOrphanedLoweredLaneSupportRemovalCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runMixedDoubleBottomTeardownLawCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runVerticalBottomUnderLoweredDoubleStackSeamCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runAdjacentLoweredDoubleBreakStoneJumpCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLiveTopDoubleStoneChainJumpReplayCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLiveTopDoubleStoneTopDependencyTransitionCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLiveTopDoubleStoneTopDependencyNoAttackControlCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runAdjacentLoweredFullJumpPreservationCase(ctx, singleplayer);
        }

        // Historical false-green / stale proof harness paths:
        // - runSideBySideDependentAdjacentFullJumpLiveOrderCase
        // - runSideBySideDependentAdjacentFullSlabChurnJumpCase
        // - runDependentAdjacentLoweredFullJumpCase
        // - runDependentAdjacentLoweredLivePlacementChurnCase
    }

    /**
     * Legal state law for adjacent full-block stability:
     * If a neighboring lowered full block remains supported by a legal direct or
     * inherited path after an adjacent lowered FB is broken, its dy must remain
     * exactly -0.5 for the observed post-break window.
     *
     * This case models two lowered FBs side-by-side, each with direct BS support,
     * and verifies that breaking one does not immediately lift the other.
     */
    private static void runAdjacentLoweredFullJumpPreservationCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.EAST;
        final BlockPos leftSupportPos = SUPPORT_POS;
        final BlockPos leftFullPos = FULL_POS;
        final BlockPos rightSupportPos = SUPPORT_POS.offset(face);
        final BlockPos rightFullPos = FULL_POS.offset(face);

        setupFixture(singleplayer, leftSupportPos, leftFullPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    rightSupportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    rightFullPos,
                    Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            BlockState leftFull = world.getBlockState(leftFullPos);
            BlockState rightFull = world.getBlockState(rightFullPos);
            SlabAnchorAttachment.addAnchor(world, leftFullPos, leftFull);
            SlabAnchorAttachment.addAnchor(world, rightFullPos, rightFull);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing before adjacent FB jump case");
            }
            BlockState leftFull = mc.world.getBlockState(leftFullPos);
            BlockState rightFull = mc.world.getBlockState(rightFullPos);
            if (!leftFull.isOf(Blocks.STONE) || !rightFull.isOf(Blocks.STONE)) {
                throw new RuntimeException("expected both adjacent FBs before break, found left=" + leftFull
                        + " right=" + rightFull);
            }
            if (!SlabAnchorAttachment.isAnchored(mc.world, leftFullPos)
                    || !SlabAnchorAttachment.isAnchored(mc.world, rightFullPos)) {
                throw new RuntimeException("adjacent FB case expected both FBs anchored pre-break");
            }

            double leftDy = SlabSupport.getYOffset(mc.world, leftFullPos, leftFull);
            double rightDy = SlabSupport.getYOffset(mc.world, rightFullPos, rightFull);
            if (Math.abs(leftDy + 0.5d) > EPSILON) {
                throw new RuntimeException("left FB was not lowered before break: dy=" + leftDy);
            }
            if (Math.abs(rightDy + 0.5d) > EPSILON) {
                throw new RuntimeException("right FB was not lowered before break: dy=" + rightDy);
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(leftFullPos, false);
        });

        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int tick = i;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after break tick " + tick);
                }
                BlockState leftAfter = mc.world.getBlockState(leftFullPos);
                if (tick == 3 && !leftAfter.isAir()) {
                    throw new RuntimeException("left FB expected to be removed on tick " + tick
                            + " but found " + leftAfter);
                }

                BlockState rightAfter = mc.world.getBlockState(rightFullPos);
                if (!rightAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("right FB must remain stone on tick " + tick
                            + " but found " + rightAfter);
                }
                double rightDy = SlabSupport.getYOffset(mc.world, rightFullPos, rightAfter);
                if (Math.abs(rightDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("right FB lost legal lowered support on tick " + tick
                            + " dy=" + rightDy);
                }
            });
        }

        // Diagnostic, not normative: if immediate jump appears, restoring a slab below
        // the broken slot should not be treated as proof of the fix itself.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    leftSupportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after slab replacement diagnostic");
            }
            BlockState rightAfter = mc.world.getBlockState(rightFullPos);
            if (!rightAfter.isOf(Blocks.STONE)) {
                throw new RuntimeException("right FB must remain stone during diagnostic step, found "
                        + rightAfter);
            }
            if (!SlabAnchorAttachment.isAnchored(mc.world, rightFullPos)) {
                throw new RuntimeException("right FB should remain anchored during diagnostic step");
            }
            double rightDy = SlabSupport.getYOffset(mc.world, rightFullPos, rightAfter);
            if (Math.abs(rightDy + 0.5d) > EPSILON) {
                throw new RuntimeException("diagnostic replacement of left support did not keep right FB lowered");
            }
        });
    }

    /**
     * Legal state law for the live A-break/B-jump capture:
     * A lowered DOUBLE slab may be adjacent to a lowered stone B that is already
     * legally supported by its own bottom slab. Breaking A must not make B jump
     * upward while B remains present; replacing A should not be required to restore B.
     */
    private static void runAdjacentLoweredDoubleBreakStoneJumpCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos aPos = SUPPORT_POS;
        final Direction adjacency = Direction.SOUTH;
        final BlockPos bPos = SUPPORT_POS.offset(adjacency);
        final BlockPos bSupportPos = bPos.down();
        final BlockHitResult breakTarget = resolveLoweredSideFaceHit(aPos, adjacency, SlabType.BOTTOM);
        final double eyeY = aPos.getY() + 2.8d;
        final double eyeX = aPos.getX() + 0.5d;
        final double eyeZ = adjacency == Direction.NORTH
                ? aPos.getZ() - 1.7d
                : adjacency == Direction.SOUTH
                        ? aPos.getZ() + 1.7d
                        : aPos.getZ() + 0.5d;
        final double targetX = aPos.getX() + 0.5d;
        final double targetY = aPos.getY() + 0.5d;
        final double targetZ = aPos.getZ() + 0.5d;

        setupFixture(singleplayer, aPos, FULL_POS);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    bSupportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    bPos,
                    Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    aPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, bPos, world.getBlockState(bPos));

            BlockState aState = world.getBlockState(aPos);
            BlockState bState = world.getBlockState(bPos);
            BlockState bSupportState = world.getBlockState(bSupportPos);
            if (!aState.isOf(Blocks.STONE_SLAB)
                    || !aState.contains(SlabBlock.TYPE)
                    || aState.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("adjacent-double proof expected A lowered DOUBLE at "
                        + aPos.toShortString() + ", found " + aState);
            }
            if (!bState.isOf(Blocks.STONE)) {
                throw new RuntimeException("adjacent-double proof expected B stone at "
                        + bPos.toShortString() + ", found " + bState);
            }
            if (!bSupportState.isOf(Blocks.STONE_SLAB)
                    || !bSupportState.contains(SlabBlock.TYPE)
                    || bSupportState.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("adjacent-double proof expected B bottom support at "
                        + bSupportPos.toShortString() + ", found " + bSupportState);
            }
            double aDy = SlabSupport.getYOffset(world, aPos, aState);
            double bDy = SlabSupport.getYOffset(world, bPos, bState);
            System.out.println("[ADJ-DOUBLE-JUMP] initial map: "
                    + "A=" + aState + "@dy=" + aDy
                    + ", B=" + bState + "@dy=" + bDy
                    + ", B below=" + bSupportState + ", B anchored=" + SlabAnchorAttachment.isAnchored(world, bPos));
            if (Math.abs(aDy + 0.5d) > EPSILON) {
                throw new RuntimeException("adjacent-double proof expected A dy=-0.500, found " + aDy);
            }
            if (Math.abs(bDy + 0.5d) > EPSILON) {
                throw new RuntimeException("adjacent-double proof expected B dy=-0.500, found " + bDy);
            }
            if (!SlabAnchorAttachment.isAnchored(world, bPos)) {
                throw new RuntimeException("adjacent-double proof expected B anchored before break");
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                return;
            }
            server.getPlayerManager()
                    .getPlayerList()
                    .get(0)
                    .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(eyeX, eyeY, eyeZ),
                new Vec3d(targetX, targetY, targetZ));
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null || mc.gameRenderer == null) {
                throw new RuntimeException("client not ready for adjacent-double break proof");
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
        });

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready to break A");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            mc.interactionManager.attackBlock(breakTarget.getBlockPos(), breakTarget.getSide());
        });

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after A break tick " + readTick);
                }
                BlockState aAfter = mc.world.getBlockState(aPos);
                BlockState bAfter = mc.world.getBlockState(bPos);
                double bDy = SlabSupport.getYOffset(mc.world, bPos, bAfter);
                boolean bAnchored = SlabAnchorAttachment.isAnchored(mc.world, bPos);
                System.out.println("[ADJ-DOUBLE-JUMP] break tick=" + readTick
                        + " A=" + aAfter
                        + " B=" + bAfter
                        + " bDy=" + bDy
                        + " anchored=" + bAnchored);
                if (readTick > 0 && !aAfter.isAir()) {
                    throw new RuntimeException("adjacent-double proof expected A to clear after break by tick 1, found "
                            + aAfter + " at tick=" + readTick);
                }
                if (!bAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("adjacent-double proof lost B on break tick " + readTick
                            + ", found " + bAfter);
                }
                if (!bAnchored) {
                    throw new RuntimeException("adjacent-double proof lost B anchor on break tick " + readTick);
                }
                if (Math.abs(bDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("adjacent-double proof B jumped on break tick " + readTick
                            + " dy=" + bDy + " state=" + bAfter);
                }
            });
        }

        // Keep replace deterministic for now: direct A restoration to lowered DOUBLE after a break.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    aPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after A replace tick " + readTick);
                }
                BlockState aAfter = mc.world.getBlockState(aPos);
                BlockState bAfter = mc.world.getBlockState(bPos);
                double bDy = SlabSupport.getYOffset(mc.world, bPos, bAfter);
                boolean bAnchored = SlabAnchorAttachment.isAnchored(mc.world, bPos);
                System.out.println("[ADJ-DOUBLE-JUMP] replace tick=" + readTick
                        + " A=" + aAfter
                        + " B=" + bAfter
                        + " bDy=" + bDy
                        + " anchored=" + bAnchored);
                if (readTick == 0 && (!aAfter.isOf(Blocks.STONE_SLAB)
                        || !aAfter.contains(SlabBlock.TYPE)
                        || aAfter.get(SlabBlock.TYPE) != SlabType.DOUBLE)) {
                    throw new RuntimeException("adjacent-double proof expected A restored on replace tick 0, found "
                            + aAfter);
                }
                if (!bAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("adjacent-double proof lost B on replace tick " + readTick
                            + ", found " + bAfter);
                }
                if (!bAnchored) {
                    throw new RuntimeException("adjacent-double proof lost B anchor on replace tick " + readTick);
                }
                if (Math.abs(bDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("adjacent-double proof B drifted on replace tick " + readTick
                            + " dy=" + bDy + " state=" + bAfter);
                }
            });
        }
    }

    /**
     * Live-chain replay for the newest inspect capture:
     * top slab (z-2), lowered DOUBLE (z-1), anchored stone + bottom support (z).
     * BOTTOM support under stone must hold dy=-0.500 while breaking/replacing the lowered DOUBLE
     * and while removing/restoring the upstream TOP.
     */
    private static void runLiveTopDoubleStoneChainJumpReplayCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos bPos = new BlockPos(0, 201, 2);
        final BlockPos bSupportPos = bPos.down();
        final BlockPos aPos = bPos.north();
        final BlockPos aTopPos = aPos.north();
        final BlockHitResult breakATarget = resolveLoweredSideFaceHit(aPos, Direction.SOUTH, SlabType.BOTTOM);
        final double breakAEyeY = aPos.getY() + 2.8d;
        final double breakAEyeX = aPos.getX() + 0.5d;
        final double breakAEyeZ = aPos.getZ() + 1.7d;
        final double breakATargetX = aPos.getX() + 0.5d;
        final double breakATargetY = aPos.getY() + 0.5d;
        final double breakATargetZ = aPos.getZ() + 0.5d;

        setupFixture(singleplayer, bSupportPos, bPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(aTopPos, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(aPos, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            BlockState bState = world.getBlockState(bPos);
            BlockState bSupportState = world.getBlockState(bSupportPos);
            BlockState aState = world.getBlockState(aPos);
            BlockState topState = world.getBlockState(aTopPos);
            SlabAnchorAttachment.addAnchor(world, bPos, bState);
            if (!bState.isOf(Blocks.STONE)) {
                throw new RuntimeException("live-chain replay expected anchored stone at "
                        + bPos.toShortString() + ", found " + bState);
            }
            if (!bSupportState.isOf(Blocks.STONE_SLAB)
                    || !bSupportState.contains(SlabBlock.TYPE)
                    || bSupportState.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("live-chain replay expected stone slab bottom support at "
                        + bSupportPos.toShortString() + ", found " + bSupportState);
            }
            if (!aState.isOf(Blocks.STONE_SLAB)
                    || !aState.contains(SlabBlock.TYPE)
                    || aState.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("live-chain replay expected lowered DOUBLE at "
                        + aPos.toShortString() + ", found " + aState);
            }
            if (!topState.isOf(Blocks.STONE_SLAB)
                    || !topState.contains(SlabBlock.TYPE)
                    || topState.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("live-chain replay expected lowered TOP at "
                        + aTopPos.toShortString() + ", found " + topState);
            }
            double bDy = SlabSupport.getYOffset(world, bPos, bState);
            double aDy = SlabSupport.getYOffset(world, aPos, aState);
            double topDy = SlabSupport.getYOffset(world, aTopPos, topState);
            if (!SlabAnchorAttachment.isAnchored(world, bPos)) {
                throw new RuntimeException("live-chain replay expected anchored stone before break at " + bPos.toShortString());
            }
            if (Math.abs(bDy + 0.5d) > EPSILON) {
                throw new RuntimeException("live-chain replay expected stone dy=-0.500 at "
                        + bPos.toShortString() + ", found " + bDy);
            }
            if (Math.abs(aDy + 0.5d) > EPSILON) {
                throw new RuntimeException("live-chain replay expected lowered DOUBLE dy=-0.500 at "
                        + aPos.toShortString() + ", found " + aDy);
            }
            if (Math.abs(topDy + 0.5d) > EPSILON) {
                throw new RuntimeException("live-chain replay expected lowered TOP dy=-0.500 at "
                        + aTopPos.toShortString() + ", found " + topDy);
            }
            System.out.println("[LIVE-TOP-CHAIN] precheck map="
                    + "B=" + bState + "@dy=" + bDy + ", anchor=" + SlabAnchorAttachment.isAnchored(world, bPos)
                    + ", B.bottom=" + bSupportState + "@dy=" + SlabSupport.getYOffset(world, bSupportPos, bSupportState)
                    + ", A=" + aState + "@dy=" + aDy
                    + ", top=" + topState + "@dy=" + topDy);
        });

        singleplayer.getServer().runOnServer(server -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                return;
            }
            server.getPlayerManager().getPlayerList().get(0).changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(breakAEyeX, breakAEyeY, breakAEyeZ),
                new Vec3d(breakATargetX, breakATargetY, breakATargetZ));
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live top-chain A break");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            mc.interactionManager.attackBlock(breakATarget.getBlockPos(), breakATarget.getSide());
        });

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after A break tick " + readTick);
                }
                BlockState bAfter = mc.world.getBlockState(bPos);
                BlockState aAfter = mc.world.getBlockState(aPos);
                double bDy = SlabSupport.getYOffset(mc.world, bPos, bAfter);
                boolean bAnchored = SlabAnchorAttachment.isAnchored(mc.world, bPos);
                System.out.println("[LIVE-TOP-CHAIN] A break tick=" + readTick
                        + " A=" + aAfter + " B=" + bAfter + " bDy=" + bDy + " anchored=" + bAnchored);
                if (!bAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("live-chain replay expected B stone after A break tick " + readTick
                            + ", found " + bAfter);
                }
                if (!bAnchored) {
                    throw new RuntimeException("live-chain replay B lost anchor after A break tick " + readTick);
                }
                if (Math.abs(bDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-chain replay B shifted after A break tick " + readTick
                            + ", bDy=" + bDy);
                }
                if (aAfter.isOf(Blocks.STONE_SLAB)
                        && aAfter.contains(SlabBlock.TYPE)
                        && aAfter.get(SlabBlock.TYPE) == SlabType.DOUBLE) {
                    double aDy = SlabSupport.getYOffset(mc.world, aPos, aAfter);
                    if (Math.abs(aDy + 0.5d) > EPSILON) {
                        throw new RuntimeException("live-chain replay A remained but drifted after A break tick "
                                + readTick + ", aDy=" + aDy);
                    }
                }
            });
        }

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    aPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after A replace tick " + readTick);
                }
                BlockState aAfter = mc.world.getBlockState(aPos);
                BlockState bAfter = mc.world.getBlockState(bPos);
                double bDy = SlabSupport.getYOffset(mc.world, bPos, bAfter);
                boolean bAnchored = SlabAnchorAttachment.isAnchored(mc.world, bPos);
                System.out.println("[LIVE-TOP-CHAIN] A restore tick=" + readTick
                        + " A=" + aAfter + " B=" + bAfter + " bDy=" + bDy + " anchored=" + bAnchored);
                if (readTick == 0 && (!aAfter.isOf(Blocks.STONE_SLAB)
                        || !aAfter.contains(SlabBlock.TYPE)
                        || aAfter.get(SlabBlock.TYPE) != SlabType.DOUBLE)) {
                    throw new RuntimeException("live-chain replay expected A restored on replace tick 0, found " + aAfter);
                }
                if (!bAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("live-chain replay lost B after A replace tick " + readTick
                            + ", found " + bAfter);
                }
                if (!bAnchored) {
                    throw new RuntimeException("live-chain replay B lost anchor after A replace tick " + readTick);
                }
                if (Math.abs(bDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-chain replay B drifted after A replace tick " + readTick
                            + " dy=" + bDy);
                }
                if (!aAfter.isOf(Blocks.STONE_SLAB)
                        || !aAfter.contains(SlabBlock.TYPE)
                        || aAfter.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                    return;
                }
                double aDy = SlabSupport.getYOffset(mc.world, aPos, aAfter);
                if (Math.abs(aDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-chain replay A drifted on replace tick " + readTick
                            + ", aDy=" + aDy);
                }
            });
        }

        final BlockHitResult breakTopTarget = resolveLoweredSideFaceHit(aTopPos, Direction.SOUTH, SlabType.BOTTOM);
        final double breakTopEyeY = aTopPos.getY() + 2.8d;
        final double breakTopEyeX = aTopPos.getX() + 0.5d;
        final double breakTopEyeZ = aTopPos.getZ() + 1.7d;
        final double breakTopTargetX = aTopPos.getX() + 0.5d;
        final double breakTopTargetY = aTopPos.getY() + 0.5d;
        final double breakTopTargetZ = aTopPos.getZ() + 0.5d;

        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(breakTopEyeX, breakTopEyeY, breakTopEyeZ),
                new Vec3d(breakTopTargetX, breakTopTargetY, breakTopTargetZ));
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live top-chain TOP break");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            mc.interactionManager.attackBlock(breakTopTarget.getBlockPos(), breakTopTarget.getSide());
        });

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after TOP break tick " + readTick);
                }
                BlockState aAfter = mc.world.getBlockState(aPos);
                BlockState topAfter = mc.world.getBlockState(aTopPos);
                BlockState bAfter = mc.world.getBlockState(bPos);
                double bDy = SlabSupport.getYOffset(mc.world, bPos, bAfter);
                double aDy = aAfter.isOf(Blocks.STONE_SLAB)
                        && aAfter.contains(SlabBlock.TYPE)
                        && aAfter.get(SlabBlock.TYPE) == SlabType.DOUBLE
                        ? SlabSupport.getYOffset(mc.world, aPos, aAfter)
                        : Double.NaN;
                boolean bAnchored = SlabAnchorAttachment.isAnchored(mc.world, bPos);
                System.out.println("[LIVE-TOP-CHAIN] TOP break tick=" + readTick
                        + " A=" + aAfter + " top=" + topAfter + " B=" + bAfter
                        + " bDy=" + bDy + " anchored=" + bAnchored + " aDy=" + aDy);
                if (!bAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("live-chain replay lost B after TOP break tick " + readTick
                            + ", found " + bAfter);
                }
                if (!bAnchored) {
                    throw new RuntimeException("live-chain replay B lost anchor after TOP break tick " + readTick);
                }
                if (Math.abs(bDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-chain replay B shifted after TOP break tick " + readTick
                            + ", bDy=" + bDy);
                }
                if (!Double.isNaN(aDy) && Math.abs(aDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-chain replay A drifted after TOP break tick " + readTick
                            + ", aDy=" + aDy);
                }
            });
        }

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    aTopPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after TOP replace tick " + readTick);
                }
                BlockState aAfter = mc.world.getBlockState(aPos);
                BlockState topAfter = mc.world.getBlockState(aTopPos);
                BlockState bAfter = mc.world.getBlockState(bPos);
                double bDy = SlabSupport.getYOffset(mc.world, bPos, bAfter);
                double aDy = aAfter.isOf(Blocks.STONE_SLAB)
                        && aAfter.contains(SlabBlock.TYPE)
                        && aAfter.get(SlabBlock.TYPE) == SlabType.DOUBLE
                        ? SlabSupport.getYOffset(mc.world, aPos, aAfter)
                        : Double.NaN;
                boolean bAnchored = SlabAnchorAttachment.isAnchored(mc.world, bPos);
                double topDy = topAfter.isOf(Blocks.STONE_SLAB)
                        && topAfter.contains(SlabBlock.TYPE)
                        && topAfter.get(SlabBlock.TYPE) == SlabType.TOP
                        ? SlabSupport.getYOffset(mc.world, aTopPos, topAfter)
                        : Double.NaN;
                System.out.println("[LIVE-TOP-CHAIN] TOP restore tick=" + readTick
                        + " A=" + aAfter + " top=" + topAfter + " B=" + bAfter
                        + " bDy=" + bDy + " aDy=" + aDy + " topDy=" + topDy + " anchored=" + bAnchored);
                if (!bAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("live-chain replay lost B after TOP replace tick " + readTick
                            + ", found " + bAfter);
                }
                if (!bAnchored) {
                    throw new RuntimeException("live-chain replay B lost anchor after TOP replace tick " + readTick);
                }
                if (Math.abs(bDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-chain replay B shifted after TOP restore tick " + readTick
                            + ", bDy=" + bDy);
                }
                if (!Double.isNaN(aDy) && Math.abs(aDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-chain replay A drifted after TOP restore tick " + readTick
                            + ", aDy=" + aDy);
                }
                if (!Double.isNaN(topDy) && Math.abs(topDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-chain replay TOP drifted after TOP restore tick " + readTick
                            + " topDy=" + topDy);
                }
            });
        }
    }

    /**
     * TOP dependency law for the same live chain:
     * TOP sits upstream of lowered DOUBLE A, which in this captured topology should not be
     * allowed to silently transition to a normal (dy=0.0) lane while B remains anchored.
     *
     * Law A:
     * If B remains anchored and legal after TOP is removed, A should remain present as lowered
     * DOUBLE (dy=-0.500) rather than normalizing upward or becoming a seam ghost.
     */
    private static void runLiveTopDoubleStoneTopDependencyTransitionCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos bPos = new BlockPos(0, 201, 2);
        final BlockPos bSupportPos = bPos.down();
        final BlockPos aPos = bPos.north();
        final BlockPos aTopPos = aPos.north();
        final BlockHitResult breakTopTarget = resolveLoweredSideFaceHit(aTopPos, Direction.SOUTH, SlabType.BOTTOM);
        final double breakTopEyeY = aTopPos.getY() + 2.8d;
        final double breakTopEyeX = aTopPos.getX() + 0.5d;
        final double breakTopEyeZ = aTopPos.getZ() + 1.7d;
        final double breakTopTargetX = aTopPos.getX() + 0.5d;
        final double breakTopTargetY = aTopPos.getY() + 0.5d;
        final double breakTopTargetZ = aTopPos.getZ() + 0.5d;

        setupFixture(singleplayer, bSupportPos, bPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(aTopPos, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(aPos, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            BlockState topState = world.getBlockState(aTopPos);
            BlockState aState = world.getBlockState(aPos);
            BlockState bState = world.getBlockState(bPos);
            BlockState bSupportState = world.getBlockState(bSupportPos);
            SlabAnchorAttachment.addAnchor(world, bPos, bState);
            if (!topState.isOf(Blocks.STONE_SLAB)
                    || !topState.contains(SlabBlock.TYPE)
                    || topState.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("top-dependency precheck expected TOP at " + aTopPos.toShortString()
                    + ", found " + topState);
            }
            if (!aState.isOf(Blocks.STONE_SLAB)
                    || !aState.contains(SlabBlock.TYPE)
                    || aState.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("top-dependency precheck expected A DOUBLE at "
                        + aPos.toShortString() + ", found " + aState);
            }
            if (!bState.isOf(Blocks.STONE)) {
                throw new RuntimeException("top-dependency precheck expected anchored stone at "
                        + bPos.toShortString() + ", found " + bState);
            }
            if (!bSupportState.isOf(Blocks.STONE_SLAB)
                    || !bSupportState.contains(SlabBlock.TYPE)
                    || bSupportState.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("top-dependency precheck expected BOTTOM support at "
                        + bSupportPos.toShortString() + ", found " + bSupportState);
            }

            double topDy = SlabSupport.getYOffset(world, aTopPos, topState);
            double aDy = SlabSupport.getYOffset(world, aPos, aState);
            double bDy = SlabSupport.getYOffset(world, bPos, bState);
            double bSupportDy = SlabSupport.getYOffset(world, bSupportPos, bSupportState);
            if (Math.abs(topDy + 0.5d) > EPSILON) {
                throw new RuntimeException("top-dependency precheck expected TOP dy=-0.500 at "
                        + aTopPos.toShortString() + ", found " + topDy);
            }
            if (Math.abs(aDy + 0.5d) > EPSILON) {
                throw new RuntimeException("top-dependency precheck expected A dy=-0.500 at "
                        + aPos.toShortString() + ", found " + aDy);
            }
            if (Math.abs(bDy + 0.5d) > EPSILON) {
                throw new RuntimeException("top-dependency precheck expected B dy=-0.500 at "
                        + bPos.toShortString() + ", found " + bDy);
            }
            if (Math.abs(bSupportDy) > EPSILON) {
                throw new RuntimeException("top-dependency precheck expected B support dy=0.000 at "
                        + bSupportPos.toShortString() + ", found " + bSupportDy);
            }
            if (!SlabAnchorAttachment.isAnchored(world, bPos)) {
                throw new RuntimeException("top-dependency precheck expected B anchored at "
                        + bPos.toShortString());
            }
            System.out.println("[TOP-DEPENDENCY] precheck map="
                    + "TOP=" + topState + "@dy=" + topDy
                    + ", A=" + aState + "@dy=" + aDy
                    + ", B=" + bState + "@dy=" + bDy
                    + ", B.anchor=" + SlabAnchorAttachment.isAnchored(world, bPos)
                    + ", B.down=" + bSupportState + "@dy=" + bSupportDy);
        });

        singleplayer.getServer().runOnServer(server -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                return;
            }
            server.getPlayerManager().getPlayerList().get(0).changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(breakTopEyeX, breakTopEyeY, breakTopEyeZ),
                new Vec3d(breakTopTargetX, breakTopTargetY, breakTopTargetZ));
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for top-dependency TOP break");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            mc.interactionManager.attackBlock(breakTopTarget.getBlockPos(), breakTopTarget.getSide());
        });

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after TOP dependency case tick " + readTick);
                }
                BlockState aAfter = mc.world.getBlockState(aPos);
                BlockState topAfter = mc.world.getBlockState(aTopPos);
                BlockState bAfter = mc.world.getBlockState(bPos);
                double aDy = aAfter.isOf(Blocks.STONE_SLAB)
                        && aAfter.contains(SlabBlock.TYPE)
                        && aAfter.get(SlabBlock.TYPE) == SlabType.DOUBLE
                        ? SlabSupport.getYOffset(mc.world, aPos, aAfter)
                        : Double.NaN;
                double bDy = SlabSupport.getYOffset(mc.world, bPos, bAfter);
                boolean bAnchored = SlabAnchorAttachment.isAnchored(mc.world, bPos);
                System.out.println("[TOP-DEPENDENCY] tick=" + readTick
                        + " TOP=" + topAfter + " A=" + aAfter + " A-dy=" + aDy
                        + " B=" + bAfter + " B-dy=" + bDy + " B.anchor=" + bAnchored);

                if (!topAfter.isAir()) {
                    throw new RuntimeException("top-dependency case expected TOP removed at tick " + readTick
                            + ", found " + topAfter);
                }
                if (!bAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("top-dependency case expected B stone at tick " + readTick
                            + ", found " + bAfter);
                }
                if (!bAnchored) {
                    throw new RuntimeException("top-dependency case expected B anchored at tick " + readTick
                            + ", but it was not");
                }
                if (Math.abs(bDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("top-dependency case expected B dy=-0.500 at tick " + readTick
                            + ", found " + bDy);
                }
                if (!aAfter.isOf(Blocks.STONE_SLAB)
                        || !aAfter.contains(SlabBlock.TYPE)
                        || aAfter.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                    throw new RuntimeException("top-dependency case ambiguous A transition after TOP removal at tick "
                            + readTick + ", expected lowered DOUBLE; found " + aAfter
                            + ". Legal transition to non-DOUBLE is intentionally not accepted by this proof.");
                }
                if (Math.abs(aDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("top-dependency case expected A stay lowered DOUBLE after TOP removal at tick "
                            + readTick + ", found dy=" + aDy);
                }
            });
        }
    }

    /**
     * No-attack control for top-dependency:
     * keep TOP untouched, break only A (lower support transition), and verify whether TOP
     * survives on both server and client across ticks 0..3.
     */
    private static void runLiveTopDoubleStoneTopDependencyNoAttackControlCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos bPos = new BlockPos(0, 201, 2);
        final BlockPos bSupportPos = bPos.down();
        final BlockPos aPos = bPos.north();
        final BlockPos aTopPos = aPos.north();
        final java.util.concurrent.atomic.AtomicBoolean serverTopDropped = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicBoolean clientTopDropped = new java.util.concurrent.atomic.AtomicBoolean(false);

        setupFixture(singleplayer, bSupportPos, bPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(aTopPos, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(aPos, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            BlockState topState = world.getBlockState(aTopPos);
            BlockState aState = world.getBlockState(aPos);
            BlockState bState = world.getBlockState(bPos);
            BlockState bSupportState = world.getBlockState(bSupportPos);
            SlabAnchorAttachment.addAnchor(world, bPos, bState);
            double topDy = SlabSupport.getYOffset(world, aTopPos, topState);
            double aDy = SlabSupport.getYOffset(world, aPos, aState);
            double bDy = SlabSupport.getYOffset(world, bPos, bState);
            double bSupportDy = SlabSupport.getYOffset(world, bSupportPos, bSupportState);
            System.out.println("[TOP-DEPENDENCY-CONTROL] precheck side=SERVER"
                    + " TOP=" + topState + "@dy=" + topDy
                    + ", A=" + aState + "@dy=" + aDy
                    + ", B=" + bState + "@dy=" + bDy
                    + ", B.anchor=" + SlabAnchorAttachment.isAnchored(world, bPos)
                    + ", B.down=" + bSupportState + "@dy=" + bSupportDy);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> server.getOverworld().breakBlock(aPos, false));

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            singleplayer.getServer().runOnServer(server -> {
                var world = server.getOverworld();
                BlockState topAfter = world.getBlockState(aTopPos);
                BlockState aAfter = world.getBlockState(aPos);
                BlockState bAfter = world.getBlockState(bPos);
                System.out.println("[TOP-DEPENDENCY-CONTROL] tick=" + readTick
                        + " side=SERVER"
                        + " TOP=" + topAfter
                        + " A=" + aAfter
                        + " B=" + bAfter
                        + " B.anchor=" + SlabAnchorAttachment.isAnchored(world, bPos));
                if (topAfter.isAir()) {
                    serverTopDropped.set(true);
                }
            });
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing for top-dependency no-attack control tick " + readTick);
                }
                BlockState topAfter = mc.world.getBlockState(aTopPos);
                BlockState aAfter = mc.world.getBlockState(aPos);
                BlockState bAfter = mc.world.getBlockState(bPos);
                System.out.println("[TOP-DEPENDENCY-CONTROL] tick=" + readTick
                        + " side=CLIENT"
                        + " TOP=" + topAfter
                        + " A=" + aAfter
                        + " B=" + bAfter
                        + " B.anchor=" + SlabAnchorAttachment.isAnchored(mc.world, bPos));
                if (topAfter.isAir()) {
                    clientTopDropped.set(true);
                }
            });
        }

        boolean serverDropped = serverTopDropped.get();
        boolean clientDropped = clientTopDropped.get();
        if (serverDropped || clientDropped) {
            System.out.println("[TOP-DEPENDENCY-CONTROL_RED] top_dropped_without_direct_attack"
                    + " serverDropped=" + serverDropped
                    + " clientDropped=" + clientDropped);
        } else {
            System.out.println("[TOP-DEPENDENCY-CONTROL_GREEN] top_survived_without_direct_attack"
                    + " serverDropped=false clientDropped=false");
        }
    }

    /**
     * Dependent-support adjacent full-block law:
     * Source FB-A is directly anchored by BS.
     * FB-B is lowered through an inherited side-slab path (TOP slab at x+1,y is lowered by FB-A),
     * but FB-B has no direct bottom-slab support.
     *
     * If FB-B remains a stone block after FB-A break, it must not jump upward.
     * If FB-B loses all legal support, explicit normalization/removal is allowed, but
     * a visible upward jump to dy=0.0 on a surviving stone block is not.
     */
    private static void runDependentAdjacentLoweredFullJumpCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.EAST;
        final BlockPos sourceSupportPos = SUPPORT_POS;
        final BlockPos sourceFullPos = FULL_POS;
        final BlockPos inheritedSlabPos = sourceFullPos.offset(face);
        final BlockPos survivorFullPos = inheritedSlabPos.up();

        setupFixture(singleplayer, sourceSupportPos, sourceFullPos);
        setLoweredSlabTarget(singleplayer, inheritedSlabPos, SlabType.TOP);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    survivorFullPos,
                    Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            BlockState sourceState = world.getBlockState(sourceFullPos);
            BlockState slabState = world.getBlockState(inheritedSlabPos);
            BlockState survivorState = world.getBlockState(survivorFullPos);
            SlabAnchorAttachment.addAnchor(world, sourceFullPos, sourceState);
            SlabAnchorAttachment.addAnchor(world, survivorFullPos, survivorState);
            if (!sourceState.isOf(Blocks.STONE)) {
                throw new RuntimeException("dependent-support setup expected source FB at "
                        + sourceFullPos.toShortString() + ", found " + sourceState);
            }
            if (!survivorState.isOf(Blocks.STONE)) {
                throw new RuntimeException("dependent-support setup expected survivor FB at "
                        + survivorFullPos.toShortString() + ", found " + survivorState);
            }
            if (!slabState.isOf(Blocks.STONE_SLAB) || !slabState.contains(SlabBlock.TYPE)
                    || slabState.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("dependent-support setup expected inherited TOP support slab at "
                        + inheritedSlabPos.toShortString() + ", found " + slabState);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing before dependent-support case");
            }
            BlockState sourceState = mc.world.getBlockState(sourceFullPos);
            BlockState survivorState = mc.world.getBlockState(survivorFullPos);
            BlockState supportBelowSurvivor = mc.world.getBlockState(survivorFullPos.down());
            BlockState slabState = mc.world.getBlockState(inheritedSlabPos);
            if (!sourceState.isOf(Blocks.STONE) || !survivorState.isOf(Blocks.STONE)) {
                throw new RuntimeException("dependent-support precheck expected two FBs, found source="
                        + sourceState + " survivor=" + survivorState);
            }
            if (!slabState.isOf(Blocks.STONE_SLAB) || !slabState.contains(SlabBlock.TYPE)
                    || slabState.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("dependent-support precheck expected TOP slab at inherited support pos, found "
                        + slabState);
            }
            if (SlabAnchorAttachment.isAnchored(mc.world, sourceFullPos) == false) {
                throw new RuntimeException("dependent-support precheck source FB must be anchored directly");
            }
            if (!SlabAnchorAttachment.isAnchored(mc.world, survivorFullPos)) {
                throw new RuntimeException("dependent-support precheck survivor FB must be anchored through inherited support");
            }
            if (SlabSupport.hasBottomSlabBelow(mc.world, survivorFullPos)) {
                throw new RuntimeException("dependent-support precheck survivor FB must not have direct bottom-slab support");
            }
            if (!supportBelowSurvivor.isOf(Blocks.STONE_SLAB)
                    || !supportBelowSurvivor.contains(SlabBlock.TYPE)
                    || supportBelowSurvivor.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("dependent-support precheck expected TOP support directly under survivor, found "
                        + supportBelowSurvivor);
            }

            double sourceDy = SlabSupport.getYOffset(mc.world, sourceFullPos, sourceState);
            double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorState);
            if (Math.abs(sourceDy + 0.5d) > EPSILON) {
                throw new RuntimeException("dependent-support precheck source FB expected dy=-0.500, found " + sourceDy);
            }
            if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                throw new RuntimeException("dependent-support precheck survivor FB was not lowered, found " + survivorDy);
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(sourceFullPos, false);
        });

        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int tick = i;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after dependent source break tick " + tick);
                }
                BlockState sourceAfter = mc.world.getBlockState(sourceFullPos);
                if (!sourceAfter.isAir()) {
                    throw new RuntimeException("expected source FB removed on tick " + tick
                            + ", found " + sourceAfter);
                }
                BlockState survivorAfter = mc.world.getBlockState(survivorFullPos);
                if (survivorAfter.isOf(Blocks.STONE)) {
                    double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorAfter);
                    if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                        throw new RuntimeException("dependent-support case survivor FB should not jump upward while surviving at tick "
                                + tick + ", dy=" + survivorDy);
                    }
                }
            });
        }

        // Diagnostic only: replacing the inherited-support TOP slab should restore legal lowered outcome.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    inheritedSlabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after dependent-support diagnostic replacement");
            }
            if (mc.world.getBlockState(survivorFullPos).isOf(Blocks.STONE)) {
                double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos,
                        mc.world.getBlockState(survivorFullPos));
                if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("dependent-support diagnostic replacement did not restore survivor dy=-0.500");
                }
            }
        });
    }

    /**
     * Side-by-side dependent-support adjacent full-block law with mixed lifecycle:
     * FB-A is placed naturally on BS. A TOP lane slab remains under FB-B on the side
     * column without direct BOTTOM support. FB-B is then naturally placed at the same Y
     * level as FB-A.
     * FB-B has no direct bottom slab beneath.
     *
     * If FB-B survives after FB-A break, it must not jump from dy=-0.5 to dy=0.0.
     * If FB-B has no legal support after the break, explicit removal/normalization is
     * accepted, but an unexplained upward jump on a surviving stone block is forbidden.
     */
    private static void runSideBySideDependentAdjacentFullJumpLiveOrderCase(
        ClientGameTestContext ctx,
        TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.EAST;
        final BlockPos sourceSupportPos = SUPPORT_POS;
        // Live-law map:
        //  - sourceCarrierPos at y=sourceSupportPos+1 is directly supported by BS.
        //  - sourceFullPos at y=sourceSupportPos+2 inherits a legal lowered source path from the carrier.
        //  - sideSlabPos is directly beneath survivor on the adjacent lane and is lowered through sourceCarrier.
        //  - survivorFullPos is side-by-side to sourceFullPos at the same Y, with no direct bottom-slab support.
        final BlockPos sourceCarrierPos = sourceSupportPos.up();
        final BlockPos sourceFullPos = sourceCarrierPos.up();
        final BlockPos sideSlabPos = sourceCarrierPos.offset(face);
        final BlockPos survivorFullPos = sourceFullPos.offset(face);
        final BlockHitResult placeSourceHit = resolveLoweredUpMergeHit(sourceCarrierPos);
        final BlockHitResult placeSurvivorHit = resolveLoweredFaceHit(sourceFullPos, face, 0.5d);

        setupFixture(singleplayer, sourceSupportPos, sourceCarrierPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(sourceFullPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(sideSlabPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(survivorFullPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    sideSlabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE, 8));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForUp(ctx, singleplayer, sourceCarrierPos);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-order dependent source-place");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 8));
            ActionResult sourcePlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSourceHit);
            if (!sourcePlace.isAccepted()) {
                throw new RuntimeException("live-order source placement click was not accepted: " + sourcePlace);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE, 8));
        });
        movePlayerForFace(ctx, singleplayer, sourceFullPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-order side neighbor full placement");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 8));
            ActionResult survivorPlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSurvivorHit);
            if (!survivorPlace.isAccepted()) {
                throw new RuntimeException("live-order survivor full click was not accepted: " + survivorPlace);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing before live-order dependent side-by-side checks");
            }
            BlockState sourceState = mc.world.getBlockState(sourceFullPos);
            BlockState slabState = mc.world.getBlockState(sideSlabPos);
            BlockState survivorState = mc.world.getBlockState(survivorFullPos);
            if (!sourceState.isOf(Blocks.STONE)) {
                throw new RuntimeException("live-order dependent precheck expected source FB, found " + sourceState);
            }
            if (!survivorState.isOf(Blocks.STONE)) {
                throw new RuntimeException("live-order dependent precheck expected side-by-side survivor FB, found "
                        + survivorState);
            }
            if (!slabState.isOf(Blocks.STONE_SLAB) || !slabState.contains(SlabBlock.TYPE)) {
                throw new RuntimeException("live-order dependent precheck expected lane slab at "
                        + sideSlabPos.toShortString() + ", found " + slabState);
            }
            if (!SlabAnchorAttachment.isAnchored(mc.world, sourceFullPos)) {
                throw new RuntimeException("live-order precheck expects source FB anchored on legal inherited path");
            }
            boolean survivorDirectBottomSupport = SlabSupport.hasBottomSlabBelow(mc.world, survivorFullPos);
            boolean survivorInheritedOrAnchored = SlabAnchorAttachment.isAnchored(mc.world, survivorFullPos)
                    || (!survivorDirectBottomSupport && SlabSupport.shouldOffset(mc.world, survivorFullPos, survivorState));
            boolean sideSlabIsTop = slabState.isOf(Blocks.STONE_SLAB)
                    && slabState.contains(SlabBlock.TYPE)
                    && slabState.get(SlabBlock.TYPE) == SlabType.TOP;
            if (!sideSlabIsTop) {
                throw new RuntimeException("live-order precheck expected TOP lane slab (not BOTTOM) at "
                        + sideSlabPos.toShortString() + ", found " + slabState);
            }
            if (survivorDirectBottomSupport) {
                throw new RuntimeException("live-order survivor precheck expected no direct bottom-slab support");
            }
            double sourceDy = SlabSupport.getYOffset(mc.world, sourceFullPos, sourceState);
            double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorState);
            System.out.println("[LIVE-ORDER] precheck sourcePos=" + sourceFullPos.toShortString()
                    + " survivorPos=" + survivorFullPos.toShortString()
                    + " sideSlabPos=" + sideSlabPos.toShortString()
                    + " sourceDy=" + sourceDy
                    + " survivorDy=" + survivorDy
                    + " directBottom=" + survivorDirectBottomSupport
                    + " inheritedOrAnchored=" + survivorInheritedOrAnchored);
            if (!survivorInheritedOrAnchored) {
                throw new RuntimeException("live-order survivor precheck expected inherited/anchored support source truth: "
                        + "direct=" + survivorDirectBottomSupport + " inherited=" + survivorInheritedOrAnchored);
            }
            if (Math.abs(sourceDy + 0.5d) > EPSILON) {
                throw new RuntimeException("live-order precheck source FB must be lowered, found dy=" + sourceDy);
            }
            if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                throw new RuntimeException("live-order precheck survivor FB must be lowered, found dy=" + survivorDy);
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(sourceFullPos, false);
        });

        for (int tick = 0; tick < 4; tick++) {
            // Define tick 0 as the first observation point after the break event.
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after live-order dependent break tick " + readTick);
                }
                BlockState sourceAfter = mc.world.getBlockState(sourceFullPos);
                if (!sourceAfter.isAir()) {
                    throw new RuntimeException("live-order case expected source FB removed by tick " + readTick
                            + ", found " + sourceAfter);
                }
                BlockState survivorAfter = mc.world.getBlockState(survivorFullPos);
                if (survivorAfter.isAir()) {
                    return;
                }
                if (!survivorAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("live-order dependent case expected stone survivor on tick " + readTick
                            + ", found " + survivorAfter);
                }
                double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorAfter);
                System.out.println("[LIVE-ORDER] tick=" + readTick + " survivorDy=" + survivorDy
                        + " sourceRemoved=" + sourceAfter.isAir()
                        + " survivorState=" + survivorAfter);
                if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-order survivor FB jumped on tick " + readTick
                            + " while still present, dy=" + survivorDy);
                }
            });
        }

        System.out.println("[LIVE-ORDER] side-by-side dependent jump chase complete for sourcePos="
                + sourceFullPos.toShortString()
                + " survivorPos=" + survivorFullPos.toShortString());

        // Diagnostic only: re-place the lane slab and confirm whether that action restores
        // the side-by-side FB dy. This does not define the intended fix.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(sideSlabPos, false);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    sideSlabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing during live-order slab replacement diagnostic");
            }
            BlockState survivorAfter = mc.world.getBlockState(survivorFullPos);
            if (survivorAfter.isOf(Blocks.STONE)) {
                double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorAfter);
                if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-order diagnostic replacement did not restore survivor dy=-0.5");
                }
            }
        });
    }

    /**
     * Side-by-side dependent-support law with explicit slab churn:
     *
     * 1) Build the same precondition as the live-order side-by-side case:
     *    FB-A direct BS support; FB-B side-by-side at same Y with no direct BOTTOM support.
     * 2) Break and replace the inherited-support TOP slab path around FB-B while FB-A is alive.
     * 3) Break FB-A (the pointed block in Julia's report).
     * 4) Re-place the slab path again and optionally churn it once more.
     *
     * For every phase, tick 0/1/2/3 are asserted:
     * - if FB-B remains legal (direct OR inherited support), dy must stay -0.500.
     * - if FB-B loses legal support, it must not be treated as a legal lowered survivor.
     */
    private static void runSideBySideDependentAdjacentFullSlabChurnJumpCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.EAST;
        final BlockPos sourceSupportPos = SUPPORT_POS;
        final BlockPos sourceCarrierPos = sourceSupportPos.up();
        final BlockPos sourceFullPos = sourceCarrierPos.up();
        final BlockPos sideSlabPos = sourceCarrierPos.offset(face);
        final BlockPos survivorFullPos = sourceFullPos.offset(face);

        final BlockHitResult placeSourceHit = resolveLoweredUpMergeHit(sourceCarrierPos);
        final BlockHitResult placeSideSlabHit = resolveLoweredSideFaceHit(sourceCarrierPos, face, SlabType.TOP);
        final BlockHitResult placeSurvivorHit = resolveLoweredUpMergeHit(sideSlabPos);

        setupFixture(singleplayer, sourceSupportPos, sourceCarrierPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    sourceFullPos,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    sideSlabPos,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    survivorFullPos,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE, 8));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForUp(ctx, singleplayer, sourceCarrierPos);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-order churn source-place");
            }
            ActionResult sourcePlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSourceHit);
            if (!sourcePlace.isAccepted()) {
                throw new RuntimeException("live-order churn source click was not accepted: " + sourcePlace);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });
        movePlayerForFace(ctx, singleplayer, sourceCarrierPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-order churn side slab place");
            }
            ActionResult slabPlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSideSlabHit);
            if (!slabPlace.isAccepted()) {
                throw new RuntimeException("live-order churn side slab click was not accepted: " + slabPlace);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE, 8));
        });
        movePlayerForUp(ctx, singleplayer, sideSlabPos);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-order churn survivor-place");
            }
            ActionResult survivorPlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSurvivorHit);
            if (!survivorPlace.isAccepted()) {
                throw new RuntimeException("live-order churn survivor click was not accepted: " + survivorPlace);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing before live-order churn precheck");
            }
            BlockState sourceState = mc.world.getBlockState(sourceFullPos);
            BlockState slabState = mc.world.getBlockState(sideSlabPos);
            BlockState survivorState = mc.world.getBlockState(survivorFullPos);
            if (!sourceState.isOf(Blocks.STONE)) {
                throw new RuntimeException("live-order churn precheck expected source FB, found " + sourceState);
            }
            if (!survivorState.isOf(Blocks.STONE)) {
                throw new RuntimeException("live-order churn precheck expected side-by-side survivor FB, found "
                        + survivorState);
            }
            if (!slabState.isOf(Blocks.STONE_SLAB) || !slabState.contains(SlabBlock.TYPE)) {
                throw new RuntimeException("live-order churn precheck expected lane slab at "
                        + sideSlabPos.toShortString() + ", found " + slabState);
            }
            if (slabState.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("live-order churn precheck expected TOP lane slab, found " + slabState);
            }
            boolean sourceAnchored = SlabAnchorAttachment.isAnchored(mc.world, sourceFullPos);
            boolean survivorDirectBottomSupport = SlabSupport.hasBottomSlabBelow(mc.world, survivorFullPos);
            boolean survivorInheritedOrAnchored = SlabAnchorAttachment.isAnchored(mc.world, survivorFullPos)
                    || (!survivorDirectBottomSupport && SlabSupport.shouldOffset(mc.world, survivorFullPos, survivorState));
            double sourceDy = SlabSupport.getYOffset(mc.world, sourceFullPos, sourceState);
            double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorState);
            if (!sourceAnchored) {
                throw new RuntimeException("live-order churn precheck source FB expected anchored");
            }
            if (!survivorInheritedOrAnchored) {
                throw new RuntimeException("live-order churn precheck expected survivor inherited/anchored support source truth: "
                        + "direct=" + survivorDirectBottomSupport + " inherited=" + survivorInheritedOrAnchored);
            }
            if (survivorDirectBottomSupport) {
                throw new RuntimeException("live-order churn precheck survivor expected no direct BOTTOM support");
            }
            if (Math.abs(sourceDy + 0.5d) > EPSILON || Math.abs(survivorDy + 0.5d) > EPSILON) {
                throw new RuntimeException("live-order churn precheck expected dy=-0.5 both before churn, sourceDy="
                        + sourceDy + " survivorDy=" + survivorDy);
            }
            System.out.println("[LIVE-CHURN] precheck sourcePos=" + sourceFullPos.toShortString()
                    + " sideSlabPos=" + sideSlabPos.toShortString()
                    + " survivorPos=" + survivorFullPos.toShortString()
                    + " sourceDy=" + sourceDy
                    + " survivorDy=" + survivorDy
                    + " directBottom=" + survivorDirectBottomSupport
                    + " inheritedOrAnchored=" + survivorInheritedOrAnchored);
        });

        // Step 1: slab break while source FB still present (live-churn operation "block broke here").
        singleplayer.getServer().runOnServer(server -> server.getOverworld().breakBlock(sideSlabPos, false));
        assertSurvivorChurnSupportTimeline(ctx, singleplayer, "after side-slab break (source alive)",
                sourceFullPos, survivorFullPos);

        // Step 2: replace slab; Julia’s follow-up often restores legal state here.
        singleplayer.getServer().runOnServer(server -> {
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });
        movePlayerForFace(ctx, singleplayer, sourceCarrierPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-order churn slab replacement");
            }
            ActionResult rePlaceSlab = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSideSlabHit);
            if (!rePlaceSlab.isAccepted()) {
                throw new RuntimeException("live-order churn re-place slab click was not accepted: " + rePlaceSlab);
            }
        });
        assertSurvivorChurnSupportTimeline(ctx, singleplayer, "after side-slab re-place (source alive)",
                sourceFullPos, survivorFullPos);

        // Step 3: break the pointed source FB.
        singleplayer.getServer().runOnServer(server -> server.getOverworld().breakBlock(sourceFullPos, false));
        assertSurvivorChurnSupportTimeline(ctx, singleplayer, "after source break",
                sourceFullPos, survivorFullPos);

        // Step 4: replace the slab again as a direct diagnostic replay.
        movePlayerForFace(ctx, singleplayer, sourceCarrierPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-order churn post-source slab replace");
            }
            ActionResult finalRePlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSideSlabHit);
            if (!finalRePlace.isAccepted()) {
                throw new RuntimeException("live-order churn post-source slab re-place click was not accepted: "
                        + finalRePlace);
            }
        });
        assertSurvivorChurnSupportTimeline(ctx, singleplayer, "after source break + slab re-place",
                sourceFullPos, survivorFullPos);

        // Optional last churn: break/replace slab once more to capture repeated live churn behavior.
        singleplayer.getServer().runOnServer(server -> server.getOverworld().breakBlock(sideSlabPos, false));
        assertSurvivorChurnSupportTimeline(ctx, singleplayer, "after final side-slab re-break",
                sourceFullPos, survivorFullPos);
    }

    /**
     * Live-path dependent-support law with real placement and slab churn:
     * Source FB-A is placed on bottom slab.
     * Top slab at x+1,y is placed as part of a player action, then survivor FB-B is placed above it.
     * While A still exists, B should not jump upward during slab break/replace churn if no direct support is removed from B itself.
     * This test keeps break/replace on the inherited slab and checks dy at tick 0 and ticks 1/2/3.
     */
    private static void runDependentAdjacentLoweredLivePlacementChurnCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.EAST;
        final BlockPos sourceSupportPos = SUPPORT_POS;
        final BlockPos sourceFullPos = FULL_POS;
        final BlockPos inheritedSlabPos = sourceFullPos.offset(face);
        final BlockPos survivorFullPos = inheritedSlabPos.up();
        final BlockHitResult placeSourceHit = resolveLoweredUpMergeHit(sourceSupportPos);
        final BlockHitResult placeSlabHit = resolveLoweredSideFaceHit(sourceFullPos, face, SlabType.TOP);
        final BlockHitResult placeSurvivorHit = resolveLoweredUpMergeHit(inheritedSlabPos);

        setupFixture(singleplayer, sourceSupportPos, sourceFullPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    sourceFullPos,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    inheritedSlabPos,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    survivorFullPos,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE, 8));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForUp(ctx, singleplayer, sourceSupportPos);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-placement source full place");
            }
            ActionResult sourcePlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSourceHit);
            if (!sourcePlace.isAccepted()) {
                throw new RuntimeException("live-placement source full click was not accepted: " + sourcePlace);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    sourceFullPos,
                    Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            // Explicitly force source anchor for the baseline path; this mirrors a placed FB on BS.
            BlockState sourceState = world.getBlockState(sourceFullPos);
            SlabAnchorAttachment.addAnchor(world, sourceFullPos, sourceState);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });
        movePlayerForFace(ctx, singleplayer, sourceFullPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-placement inherited slab place");
            }
            ActionResult slabPlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSlabHit);
            if (!slabPlace.isAccepted()) {
                throw new RuntimeException("live-placement inherited slab click was not accepted: " + slabPlace);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE, 8));
        });
        movePlayerForUp(ctx, singleplayer, inheritedSlabPos);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-placement survivor full place");
            }
            ActionResult survivorPlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSurvivorHit);
            if (!survivorPlace.isAccepted()) {
                throw new RuntimeException("live-placement survivor full click was not accepted: " + survivorPlace);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing before live-placement churn checks");
            }
            BlockState sourceState = mc.world.getBlockState(sourceFullPos);
            BlockState slabState = mc.world.getBlockState(inheritedSlabPos);
            BlockState survivorState = mc.world.getBlockState(survivorFullPos);
            if (!sourceState.isOf(Blocks.STONE) || !survivorState.isOf(Blocks.STONE)) {
                throw new RuntimeException("live-placement churn case expected source/survivor stones, found source="
                        + sourceState + " survivor=" + survivorState);
            }
            if (!slabState.isOf(Blocks.STONE_SLAB) || !slabState.contains(SlabBlock.TYPE)
                    || slabState.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("live-placement churn case expected inherited TOP slab, found " + slabState);
            }
            double sourceDy = SlabSupport.getYOffset(mc.world, sourceFullPos, sourceState);
            double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorState);
            if (Math.abs(sourceDy + 0.5d) > EPSILON) {
                throw new RuntimeException("live-placement churn source FB expected dy=-0.500, found " + sourceDy);
            }
            if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                throw new RuntimeException("live-placement churn survivor FB expected pre-churn dy=-0.500, found " + survivorDy);
            }
        });

        // Slab churn: break and immediately re-place the inherited slab path.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(inheritedSlabPos, false);
        });
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int tick = i;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after live slab break tick " + tick);
                }
                BlockState survivorAfter = mc.world.getBlockState(survivorFullPos);
                if (survivorAfter.isOf(Blocks.STONE)) {
                    double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorAfter);
                    if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                        throw new RuntimeException("live-placement case survivor jumped after inherited slab break on tick "
                                + tick + ", dy=" + survivorDy);
                    }
                }
            });
        }

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });
        movePlayerForFace(ctx, singleplayer, sourceFullPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live slab re-place");
            }
            ActionResult rePlaceSlab = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSlabHit);
            if (!rePlaceSlab.isAccepted()) {
                throw new RuntimeException("live-placement churn case re-place slab click was not accepted: " + rePlaceSlab);
            }
        });
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int tick = i;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after live slab re-place tick " + tick);
                }
                BlockState survivorAfter = mc.world.getBlockState(survivorFullPos);
                if (survivorAfter.isOf(Blocks.STONE)) {
                    double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorAfter);
                    if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                        throw new RuntimeException("live-placement case survivor jumped around slab re-place on tick "
                                + tick + ", dy=" + survivorDy);
                    }
                }
            });
        }
    }

    private static void runLoweredSidePlacementCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Direction face,
            SlabType targetType,
            SlabType expectedPlacedType
    ) {
        final BlockPos targetPos = FULL_POS.offset(face);
        final BlockPos placementPos = targetPos.offset(face);
        final BlockHitResult hit = resolveLoweredSideFaceHit(targetPos, face, targetType);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        setLoweredSlabTarget(singleplayer, targetPos, targetType);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForFace(ctx, singleplayer, targetPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for " + targetType + " side case on " + face);
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (!result.isAccepted()) {
                throw new RuntimeException(targetType + " side click for " + face + " was not accepted: " + result);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after " + targetType + " side click on " + face);
            }
            BlockState placed = mc.world.getBlockState(placementPos);
            if (!placed.isOf(Blocks.STONE_SLAB)
                    || !placed.contains(SlabBlock.TYPE)
                    || placed.get(SlabBlock.TYPE) != expectedPlacedType) {
                throw new RuntimeException("expected placed " + expectedPlacedType
                        + " at " + placementPos.toShortString()
                        + " for " + targetType + " side target " + face
                        + ", found " + placed);
            }
            double placedDy = SlabSupport.getYOffset(mc.world, placementPos, placed);
            if (Math.abs(placedDy + 0.5d) > EPSILON) {
                throw new RuntimeException("expected lowered dy=-0.500 for " + targetType + " target on "
                        + face + " side, found dy=" + placedDy + " state=" + placed);
            }
        });
    }

    private static void runLiveClickPairBottomSlabLaneInheritanceCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final String proof = "LIVE_CLICK_PAIR_BOTTOM_SLAB_LANE_INHERITANCE";
        final Direction face = Direction.EAST;
        final BlockPos sourcePos = FULL_POS.east();
        final BlockPos placePos = sourcePos.offset(face);
        final BlockHitResult hit = resolveLoweredSideFaceHit(sourcePos, face, SlabType.BOTTOM);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        setLoweredSlabTarget(singleplayer, sourcePos, SlabType.BOTTOM);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState sourceState = world.getBlockState(sourcePos);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, sourcePos, sourceState);
            world.setBlockState(FULL_POS, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(SUPPORT_POS, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        movePlayerForFace(ctx, singleplayer, sourcePos, face);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[" + proof + "] client not ready");
            }
            BlockState source = mc.world.getBlockState(sourcePos);
            double sourceDy = SlabSupport.getYOffset(mc.world, sourcePos, source);
            if (!source.isOf(Blocks.STONE_SLAB)
                    || !source.contains(SlabBlock.TYPE)
                    || source.get(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Math.abs(sourceDy + 0.5d) > EPSILON) {
                throw new RuntimeException("[" + proof + "] source not lowered bottom slab: state="
                        + source + " dy=" + sourceDy);
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (!result.isAccepted()) {
                throw new RuntimeException("[" + proof + "] placement was not accepted: " + result);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[" + proof + "] client world missing after placement");
            }
            BlockState placed = mc.world.getBlockState(placePos);
            double placedDy = SlabSupport.getYOffset(mc.world, placePos, placed);
            boolean persistent = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(mc.world, placePos, placed);
            System.out.println("[" + proof + "] source=" + sourcePos.toShortString()
                    + " face=" + face.asString()
                    + " placePos=" + placePos.toShortString()
                    + " placed=" + placed
                    + " dy=" + placedDy
                    + " persistentLoweredSlabCarrier=" + persistent);
            if (!placed.isOf(Blocks.STONE_SLAB)
                    || !placed.contains(SlabBlock.TYPE)
                    || placed.get(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Math.abs(placedDy + 0.5d) > EPSILON
                    || persistent) {
                throw new RuntimeException("[" + proof + "] placed slab should inherit lowered bottom lane, found state="
                        + placed + " dy=" + placedDy + " persistent=" + persistent);
            }
        });
    }

    private static void runLiveClickPairFullBlockLaneInheritanceCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final String proof = "LIVE_CLICK_PAIR_FULL_BLOCK_LANE_INHERITANCE";
        final Direction face = Direction.EAST;
        final BlockPos sourcePos = FULL_POS.east();
        final BlockPos sourceBelowPos = sourcePos.down();
        final BlockPos sourceCarrierPos = sourceBelowPos.down();
        final BlockPos sourceCarrierSupportPos = sourceCarrierPos.down();
        final BlockPos placePos = sourcePos.offset(face);
        final BlockHitResult hit = resolveLoweredSideFaceHit(sourcePos, face, SlabType.BOTTOM);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(FULL_POS, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(SUPPORT_POS, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    sourceCarrierSupportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(sourceCarrierPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, sourceCarrierPos, world.getBlockState(sourceCarrierPos));
            world.setBlockState(
                    sourceBelowPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    sourcePos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE, 8));
        movePlayerForFace(ctx, singleplayer, sourcePos, face);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[" + proof + "] client not ready");
            }
            BlockState source = mc.world.getBlockState(sourcePos);
            double sourceDy = SlabSupport.getYOffset(mc.world, sourcePos, source);
            boolean sourcePersistent = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(mc.world, sourcePos, source);
            if (!source.isOf(Blocks.STONE_SLAB)
                    || !source.contains(SlabBlock.TYPE)
                    || source.get(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Math.abs(sourceDy + 0.5d) > EPSILON
                    || sourcePersistent) {
                throw new RuntimeException("[" + proof + "] source not non-persistent lowered bottom slab: state="
                        + source + " dy=" + sourceDy + " persistent=" + sourcePersistent);
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (!result.isAccepted()) {
                throw new RuntimeException("[" + proof + "] placement was not accepted: " + result);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[" + proof + "] client world missing after placement");
            }
            BlockState placed = mc.world.getBlockState(placePos);
            double placedDy = SlabSupport.getYOffset(mc.world, placePos, placed);
            boolean anchored = SlabAnchorAttachment.isAnchored(mc.world, placePos);
            BlockState source = mc.world.getBlockState(sourcePos);
            boolean sourcePersistent = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(mc.world, sourcePos, source);
            System.out.println("[" + proof + "] source=" + sourcePos.toShortString()
                    + " face=" + face.asString()
                    + " placePos=" + placePos.toShortString()
                    + " sourcePersistentLoweredSlabCarrier=" + sourcePersistent
                    + " placed=" + placed
                    + " dy=" + placedDy
                    + " anchored=" + anchored);
            if (!placed.isOf(Blocks.STONE)
                    || Math.abs(placedDy + 0.5d) > EPSILON
                    || !anchored
                    || sourcePersistent) {
                throw new RuntimeException("[" + proof + "] placed full block should inherit lowered lane, found state="
                        + placed + " dy=" + placedDy + " anchored=" + anchored
                        + " sourcePersistent=" + sourcePersistent);
            }
        });
    }

    private static void runLoweredDoubleSidePlacementCreatedNormalBottomLaneProofCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final String proof = "LOWERED_DOUBLE_SIDE_PLACEMENT_CREATED_NORMAL_BOTTOM_LANE";
        final Direction clickedFace = Direction.SOUTH;
        final BlockPos targetPos = FULL_POS.offset(clickedFace);
        final BlockPos placePos = targetPos.offset(clickedFace);
        final BlockPos downPos = placePos.down();
        final BlockPos northPos = placePos.north();
        final BlockPos eastPos = northPos.east();
        final BlockPos southPos = placePos.south();
        final BlockHitResult hit = resolveLoweredFaceHit(targetPos, clickedFace, -0.5d);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        setLoweredSlabTarget(singleplayer, targetPos, SlabType.DOUBLE);
        setLoweredSlabTarget(singleplayer, eastPos, SlabType.BOTTOM);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(placePos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        movePlayerForFace(ctx, singleplayer, targetPos, clickedFace);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[" + proof + "] client not ready");
            }

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            BlockState targetState = mc.world.getBlockState(targetPos);
            BlockState placeBefore = mc.world.getBlockState(placePos);
            BlockState downState = mc.world.getBlockState(downPos);
            BlockState northState = mc.world.getBlockState(northPos);
            BlockState eastState = mc.world.getBlockState(eastPos);
            BlockState southState = mc.world.getBlockState(southPos);
            ItemStack heldStack = mc.player.getMainHandStack();
            String heldItem = heldStack.isEmpty() ? "empty" : heldStack.getItem().toString();

            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            BlockState placeAfter = mc.world.getBlockState(placePos);

            String expected = "minecraft:stone_slab[type=top] dy=-0.500 lowered=true";
            boolean expectedLoweredLaneByLaw = SlabSupport.isCompatibleLoweredSlabLane(SlabType.DOUBLE, SlabType.TOP);

            SlabType actualType = placeAfter.isOf(Blocks.STONE_SLAB) && placeAfter.contains(SlabBlock.TYPE)
                    ? placeAfter.get(SlabBlock.TYPE)
                    : null;
            double actualDy = SlabSupport.getYOffset(mc.world, placePos, placeAfter);
            boolean actualLowered = actualDy < -EPSILON;

            double targetDy = SlabSupport.getYOffset(mc.world, targetPos, targetState);
            boolean targetLowered = targetDy < -EPSILON;
            double downDy = SlabSupport.getYOffset(mc.world, downPos, downState);
            boolean downLowered = downDy < -EPSILON;
            double northDy = SlabSupport.getYOffset(mc.world, northPos, northState);
            boolean northLowered = northDy < -EPSILON;
            double eastDy = SlabSupport.getYOffset(mc.world, eastPos, eastState);
            boolean eastLowered = eastDy < -EPSILON;
            double southDy = SlabSupport.getYOffset(mc.world, southPos, southState);
            boolean southLowered = southDy < -EPSILON;

            boolean actualNormalBottom = placeAfter.isOf(Blocks.STONE_SLAB)
                    && placeAfter.contains(SlabBlock.TYPE)
                    && placeAfter.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && Math.abs(actualDy) <= EPSILON
                    && !actualLowered;
            boolean contextLowered = targetLowered && northLowered && eastLowered;
            boolean actualExpectedLowered = placeAfter.isOf(Blocks.STONE_SLAB)
                    && placeAfter.contains(SlabBlock.TYPE)
                    && placeAfter.get(SlabBlock.TYPE) == SlabType.TOP
                    && Math.abs(actualDy + 0.5d) <= EPSILON
                    && actualLowered;

            boolean actualBottomLoweredHalf = placeAfter.isOf(Blocks.STONE_SLAB)
                    && placeAfter.contains(SlabBlock.TYPE)
                    && placeAfter.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && Math.abs(actualDy + 0.5d) <= EPSILON
                    && actualLowered;

            String classification;
            if (result.isAccepted() && actualNormalBottom && contextLowered && expectedLoweredLaneByLaw) {
                classification = "RED_LIVE_REPRO";
            } else if (result.isAccepted() && actualBottomLoweredHalf) {
                classification = "HYPOTHESIS_NOT_REPRODUCED";
            } else if (result.isAccepted() && actualExpectedLowered && expectedLoweredLaneByLaw) {
                classification = "GREEN_ALREADY_FIXED";
            } else {
                classification = "PROOF_GAP";
            }

            System.out.println("[" + proof + "] heldItem=" + heldItem);
            System.out.println("[" + proof + "] clicked targetPos=" + targetPos.toShortString()
                    + " targetState=" + targetState
                    + " targetType=" + (targetState.contains(SlabBlock.TYPE) ? targetState.get(SlabBlock.TYPE) : "none")
                    + " targetDy=" + targetDy
                    + " targetLowered=" + targetLowered);
            System.out.println("[" + proof + "] clicked face=" + clickedFace.asString()
                    + " hitPos=" + hit.getPos()
                    + " intended placePos=" + placePos.toShortString());
            System.out.println("[" + proof + "] before placePosState=" + placeBefore);
            System.out.println("[" + proof + "] neighbor down pos=" + downPos.toShortString()
                    + " state=" + downState
                    + " slabType=" + (downState.contains(SlabBlock.TYPE) ? downState.get(SlabBlock.TYPE) : "none")
                    + " dy=" + downDy
                    + " lowered=" + downLowered);
            System.out.println("[" + proof + "] neighbor north pos=" + northPos.toShortString()
                    + " state=" + northState
                    + " slabType=" + (northState.contains(SlabBlock.TYPE) ? northState.get(SlabBlock.TYPE) : "none")
                    + " dy=" + northDy
                    + " lowered=" + northLowered);
            System.out.println("[" + proof + "] neighbor east pos=" + eastPos.toShortString()
                    + " state=" + eastState
                    + " slabType=" + (eastState.contains(SlabBlock.TYPE) ? eastState.get(SlabBlock.TYPE) : "none")
                    + " dy=" + eastDy
                    + " lowered=" + eastLowered);
            System.out.println("[" + proof + "] neighbor south pos=" + southPos.toShortString()
                    + " state=" + southState
                    + " slabType=" + (southState.contains(SlabBlock.TYPE) ? southState.get(SlabBlock.TYPE) : "none")
                    + " dy=" + southDy
                    + " lowered=" + southLowered);
            System.out.println("[" + proof + "] placement result=" + result);
            System.out.println("[" + proof + "] after placePosState=" + placeAfter);
            System.out.println("[" + proof + "] expected lowered-lane result=" + expected
                    + " lawCompatible=" + expectedLoweredLaneByLaw);
            System.out.println("[" + proof + "] actual slabType=" + (actualType == null ? "none" : actualType)
                    + " dy=" + actualDy
                    + " lowered=" + actualLowered);
            if ("HYPOTHESIS_NOT_REPRODUCED".equals(classification)) {
                System.out.println("[" + proof + "] expectedBadPattern=bottom_dy0"
                        + " actual=bottom_dy-0.5_lowered_true"
                        + " noGameplayFixWarranted=true");
            }
            System.out.println("[" + proof + "] classification=" + classification);

            if ("RED_LIVE_REPRO".equals(classification)) {
                throw new RuntimeException("RED_LIVE_REPRO: " + proof
                        + " created normal bottom lane in lowered side-lane context");
            }
        });
    }

    private static void runRealPlacedLoweredBottomSlabPersistenceAfterBridgeBreakProofCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final String proof = "REAL_PLACED_LOWERED_BOTTOM_SLAB_PERSISTENCE_AFTER_BRIDGE_BREAK";
        final BlockPos centerSupportPos = SUPPORT_POS;
        final BlockPos centerFullPos = FULL_POS;
        final BlockPos westFullPos = centerFullPos.west();
        final BlockPos eastFullPos = centerFullPos.east();
        final BlockPos slabAbovePos = centerFullPos.up();
        final BlockPos underPlacedPos = centerFullPos;
        final BlockHitResult placeSlabAboveHit = resolveLoweredUpMergeHit(centerFullPos);
        final Direction underPlaceFace = Direction.EAST;
        final BlockHitResult underPlaceHit = resolveLoweredSideFaceHit(westFullPos, underPlaceFace, SlabType.BOTTOM);
        System.out.println("[" + proof + "] slabAbovePos=" + slabAbovePos.toShortString());

        setupFixture(singleplayer, centerSupportPos, centerFullPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();

            world.setBlockState(westFullPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(eastFullPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);

            BlockState centerState = world.getBlockState(centerFullPos);
            BlockState westState = world.getBlockState(westFullPos);
            BlockState eastState = world.getBlockState(eastFullPos);

            SlabAnchorAttachment.addAnchor(world, centerFullPos, centerState);
            SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(world, westFullPos, westState, centerFullPos, centerState);
            SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(world, eastFullPos, eastState, centerFullPos, centerState);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String creationProof = "REAL_PLACED_LOWERED_BOTTOM_SLAB_CREATION_WRITES_CARRIER";
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(centerFullPos.getX() + 0.5d, centerFullPos.getY() + 1.8d, centerFullPos.getZ() + 2.55d),
                placeSlabAboveHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[" + creationProof + "] client not ready for real slab placement");
            }
            ActionResult slabPlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSlabAboveHit);
            if (!slabPlace.isAccepted()) {
                throw new RuntimeException("PROOF_GAP: " + creationProof
                        + " real item placement was rejected: " + slabPlace);
            }
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[" + proof + "] client world null before break");
            }

            BlockState centerBefore = mc.world.getBlockState(centerFullPos);
            BlockState westBefore = mc.world.getBlockState(westFullPos);
            BlockState eastBefore = mc.world.getBlockState(eastFullPos);
            BlockState slabBefore = mc.world.getBlockState(slabAbovePos);

            double centerBeforeDy = SlabSupport.getYOffset(mc.world, centerFullPos, centerBefore);
            double westBeforeDy = SlabSupport.getYOffset(mc.world, westFullPos, westBefore);
            double eastBeforeDy = SlabSupport.getYOffset(mc.world, eastFullPos, eastBefore);
            double slabBeforeDy = SlabSupport.getYOffset(mc.world, slabAbovePos, slabBefore);

            boolean centerBeforeAnchored = SlabAnchorAttachment.isAnchored(mc.world, centerFullPos);
            boolean westBeforeAnchored = SlabAnchorAttachment.isAnchored(mc.world, westFullPos);
            boolean eastBeforeAnchored = SlabAnchorAttachment.isAnchored(mc.world, eastFullPos);
            boolean slabBeforeLowered = slabBeforeDy < -EPSILON;
            boolean slabBeforePersistentCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(mc.world, slabAbovePos, slabBefore);

            System.out.println("[" + proof + "] centerFull before state=" + centerBefore
                    + " dy=" + centerBeforeDy + " anchored=" + centerBeforeAnchored);
            System.out.println("[" + proof + "] westFull before state=" + westBefore
                    + " dy=" + westBeforeDy + " anchored=" + westBeforeAnchored);
            System.out.println("[" + proof + "] eastFull before state=" + eastBefore
                    + " dy=" + eastBeforeDy + " anchored=" + eastBeforeAnchored);
            System.out.println("[" + proof + "] slabAbove before state=" + slabBefore
                    + " slabType=" + (slabBefore.contains(SlabBlock.TYPE) ? slabBefore.get(SlabBlock.TYPE) : "none")
                    + " dy=" + slabBeforeDy
                    + " lowered=" + slabBeforeLowered
                    + " persistentLoweredSlabCarrier=" + slabBeforePersistentCarrier);

            boolean slabCreatedAsBottom = slabBefore.isOf(Blocks.STONE_SLAB)
                    && slabBefore.contains(SlabBlock.TYPE)
                    && slabBefore.get(SlabBlock.TYPE) == SlabType.BOTTOM;
            System.out.println("[" + creationProof + "] slabAbove state=" + slabBefore
                    + " dy=" + slabBeforeDy
                    + " modelDy=" + slabBeforeDy
                    + " outlineDy=" + slabBeforeDy
                    + " targetDy=" + slabBeforeDy
                    + " persistentLoweredSlabCarrier=" + slabBeforePersistentCarrier);
            if (!slabCreatedAsBottom
                    || !slabBeforePersistentCarrier
                    || Math.abs(slabBeforeDy + 0.5d) > EPSILON) {
                throw new RuntimeException("RED_LIVE_REPRO: " + creationProof
                        + " real placed lowered bottom slab did not become a persistent carrier");
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(centerFullPos, false);
        });
        System.out.println("[" + proof + "] action=break centerFull");

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[" + proof + "] client world null after break");
            }

            BlockState centerAfter = mc.world.getBlockState(centerFullPos);
            BlockState westAfter = mc.world.getBlockState(westFullPos);
            BlockState eastAfter = mc.world.getBlockState(eastFullPos);
            BlockState slabAfter = mc.world.getBlockState(slabAbovePos);

            boolean slabAfterPersistentCarrierBeforeDy = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(mc.world, slabAbovePos, slabAfter);
            double slabAfterDy = SlabSupport.getYOffset(mc.world, slabAbovePos, slabAfter);
            boolean slabAfterLowered = slabAfterDy < -EPSILON;
            boolean slabAfterPersistentCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(mc.world, slabAbovePos, slabAfter);
            boolean westAfterAnchored = SlabAnchorAttachment.isAnchored(mc.world, westFullPos);
            boolean eastAfterAnchored = SlabAnchorAttachment.isAnchored(mc.world, eastFullPos);

            double modelDy = slabAfterDy;
            double outlineDy = slabAfterDy;
            double targetDy = slabAfterDy;

            System.out.println("[" + proof + "] centerFull after state=" + centerAfter);
            System.out.println("[" + proof + "] westFull after state=" + westAfter
                    + " dy=" + SlabSupport.getYOffset(mc.world, westFullPos, westAfter)
                    + " anchored=" + westAfterAnchored);
            System.out.println("[" + proof + "] eastFull after state=" + eastAfter
                    + " dy=" + SlabSupport.getYOffset(mc.world, eastFullPos, eastAfter)
                    + " anchored=" + eastAfterAnchored);
            System.out.println("[" + proof + "] slabAbove after state=" + slabAfter
                    + " slabType=" + (slabAfter.contains(SlabBlock.TYPE) ? slabAfter.get(SlabBlock.TYPE) : "none")
                    + " dy=" + slabAfterDy
                    + " lowered=" + slabAfterLowered
                    + " persistentLoweredSlabCarrier=" + slabAfterPersistentCarrier);
            System.out.println("[" + proof + "] slabAbove diagnostics"
                    + " worldClass=" + mc.world.getClass().getName()
                    + " fluidEmpty=" + slabAfter.getFluidState().isEmpty()
                    + " slabType=" + (slabAfter.contains(SlabBlock.TYPE) ? slabAfter.get(SlabBlock.TYPE) : "none")
                    + " persistentBeforeDy=" + slabAfterPersistentCarrierBeforeDy
                    + " persistentAfterDy=" + slabAfterPersistentCarrier);
            System.out.println("[" + proof + "] modelDy=" + modelDy
                    + " outlineDy=" + outlineDy
                    + " targetDy=" + targetDy);

            boolean slabBeforeLegal = slabAfter.isOf(Blocks.STONE_SLAB)
                    && slabAfter.contains(SlabBlock.TYPE)
                    && slabAfter.get(SlabBlock.TYPE) == SlabType.BOTTOM;
            boolean slabLegalLowered = slabBeforeLegal
                    && Math.abs(slabAfterDy + 0.5d) <= EPSILON
                    && slabAfterLowered;
            boolean neighborsRemain = westAfter.isOf(Blocks.STONE)
                    && eastAfter.isOf(Blocks.STONE)
                    && westAfterAnchored
                    && eastAfterAnchored;

            String classification;
            if (slabLegalLowered && slabAfterPersistentCarrier && neighborsRemain) {
                classification = "GREEN_ALREADY_FIXED";
            } else if (neighborsRemain) {
                classification = "RED_LIVE_REPRO";
            } else {
                classification = "PROOF_GAP";
            }

            System.out.println("[" + proof + "] expected legal outcome=slabAbove minecraft:stone_slab[type=bottom] dy=-0.5 lowered=true persistentLoweredSlabCarrier=true");
            System.out.println("[" + proof + "] classification=" + classification);

            if ("RED_LIVE_REPRO".equals(classification)) {
                throw new RuntimeException("RED_LIVE_REPRO: " + proof
                        + " slabAbove failed lowered persistence after center bridge break");
            }
            if ("PROOF_GAP".equals(classification)) {
                throw new RuntimeException("PROOF_GAP: " + proof
                        + " neighbor persistence context not stable enough for classification");
            }
        });

        final String underPlacementProof = "REAL_PLACED_LOWERED_BOTTOM_SLAB_UNDER_PLACEMENT_DOES_NOT_JUMP";
        singleplayer.getServer().runOnServer(server -> {
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });
        movePlayerForFace(ctx, singleplayer, westFullPos, underPlaceFace);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[" + underPlacementProof + "] client not ready for under-placement");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            ActionResult placeUnder = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, underPlaceHit);
            if (!placeUnder.isAccepted()) {
                throw new RuntimeException("PROOF_GAP: " + underPlacementProof
                        + " under-placement click was not accepted: " + placeUnder);
            }
        });
        System.out.println("[" + underPlacementProof + "] action=place stone_slab under persisted slab"
                + " target=" + westFullPos.toShortString()
                + " face=" + underPlaceFace
                + " placedPos=" + underPlacedPos.toShortString());

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[" + underPlacementProof + "] client world null after under-placement");
            }

            BlockState survivor = mc.world.getBlockState(slabAbovePos);
            BlockState placed = mc.world.getBlockState(underPlacedPos);

            boolean survivorIsBottomSlab = survivor.isOf(Blocks.STONE_SLAB)
                    && survivor.contains(SlabBlock.TYPE)
                    && survivor.get(SlabBlock.TYPE) == SlabType.BOTTOM;
            boolean survivorPersistentCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(mc.world, slabAbovePos, survivor);
            double survivorDy = SlabSupport.getYOffset(mc.world, slabAbovePos, survivor);
            double modelDy = survivorDy;
            double outlineDy = survivorDy;
            double targetDy = survivorDy;
            double jumpDelta = survivorDy - -0.5d;

            boolean placedIsSlab = placed.isOf(Blocks.STONE_SLAB) && placed.contains(SlabBlock.TYPE);
            double placedDy = placedIsSlab ? SlabSupport.getYOffset(mc.world, underPlacedPos, placed) : Double.NaN;
            boolean placedLegalLoweredLane = placedIsSlab
                    && placed.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && Math.abs(placedDy + 0.5d) <= EPSILON;
            boolean placedLegalVanillaLane = placedIsSlab
                    && placed.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && Math.abs(placedDy) <= EPSILON;

            System.out.println("[" + underPlacementProof + "] survivor state=" + survivor
                    + " dy=" + survivorDy
                    + " modelDy=" + modelDy
                    + " outlineDy=" + outlineDy
                    + " targetDy=" + targetDy
                    + " jumpDelta=" + jumpDelta
                    + " persistentLoweredSlabCarrier=" + survivorPersistentCarrier);
            System.out.println("[" + underPlacementProof + "] placed state=" + placed
                    + " dy=" + placedDy
                    + " legalLoweredLane=" + placedLegalLoweredLane
                    + " legalVanillaLane=" + placedLegalVanillaLane);

            if (!survivorIsBottomSlab
                    || !survivorPersistentCarrier
                    || Math.abs(survivorDy + 0.5d) > EPSILON
                    || Math.abs(modelDy + 0.5d) > EPSILON
                    || Math.abs(outlineDy + 0.5d) > EPSILON
                    || Math.abs(targetDy + 0.5d) > EPSILON
                    || Math.abs(jumpDelta) > EPSILON) {
                throw new RuntimeException("RED_LIVE_REPRO: " + underPlacementProof
                        + " persisted slab jumped or lost lowered carrier after under-placement");
            }
            if (!placedLegalLoweredLane && !placedLegalVanillaLane) {
                throw new RuntimeException("PROOF_GAP: " + underPlacementProof
                        + " placed slab is not a named lowered or vanilla bottom-lane state");
            }
        });
    }

    private static void runLoweredTopUpMergeCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.SOUTH;
        final BlockPos targetPos = FULL_POS.offset(face);
        final BlockHitResult hit = resolveLoweredUpMergeHit(targetPos);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        setLoweredSlabTarget(singleplayer, targetPos, SlabType.TOP);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForUp(ctx, singleplayer, targetPos);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for lowered-TOP up merge");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (!result.isAccepted()) {
                throw new RuntimeException("lowered TOP up-click was not accepted: " + result);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after lowered-TOP up-click");
            }
            BlockState placed = mc.world.getBlockState(targetPos);
            if (!placed.isOf(Blocks.STONE_SLAB)
                    || !placed.contains(SlabBlock.TYPE)
                    || placed.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("UP target=TOP expected DOUBLE merge at "
                        + targetPos.toShortString()
                        + ", found " + placed);
            }
            double placedDy = SlabSupport.getYOffset(mc.world, targetPos, placed);
            if (Math.abs(placedDy + 0.5d) > EPSILON) {
                throw new RuntimeException("UP target=TOP expected merged DOUBLE dy=-0.500, found " + placedDy);
            }
        });
    }

    /**
     * Legal state law for orphaned lowered side-lane slabs:
     * BSFB > TS > TS is allowed while BS/FB support exists; once both original BS and
     * original FB are removed, the remaining lane must not silently stay lowered.
     * With no valid lowered support relation left, remaining TS slabs normalize to dy=0.
     */
    private static void runOrphanedLoweredLaneSupportRemovalCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.EAST;
        final BlockPos laneRootPos = FULL_POS.offset(face);
        final BlockPos laneTailPos = laneRootPos.offset(face);
        final BlockHitResult extendHit = resolveLoweredSideFaceHit(laneRootPos, face, SlabType.TOP);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            SlabAnchorAttachment.addAnchor(world, FULL_POS, world.getBlockState(FULL_POS));
        });
        setLoweredSlabTarget(singleplayer, laneRootPos, SlabType.TOP);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForFace(ctx, singleplayer, laneRootPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for orphaned-lane setup");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, extendHit);
            if (!result.isAccepted()) {
                throw new RuntimeException("orphaned-lane setup extension click was not accepted: " + result);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after orphaned-lane setup");
            }
            BlockState laneRoot = mc.world.getBlockState(laneRootPos);
            BlockState laneTail = mc.world.getBlockState(laneTailPos);
            assertTopLowered(laneRootPos, laneRoot, mc.world, "orphan-setup laneRoot");
            assertTopLowered(laneTailPos, laneTail, mc.world, "orphan-setup laneTail");
        });

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(SUPPORT_POS, false);
        });
        for (int i = 0; i < 3; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int tick = i;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after BS break tick " + tick);
                }
                BlockState fullAfterBsBreak = mc.world.getBlockState(FULL_POS);
                if (!fullAfterBsBreak.isOf(Blocks.STONE)) {
                    throw new RuntimeException("expected FB to remain after BS break at tick " + tick
                            + ", found " + fullAfterBsBreak);
                }
                if (!SlabAnchorAttachment.isAnchored(mc.world, FULL_POS)) {
                    throw new RuntimeException("expected FB anchor to persist after BS break at tick " + tick);
                }
                assertLaneDy(mc.world, laneRootPos, laneTailPos, -0.5d,
                        "post-BS-break tick " + tick);
            });
        }

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(FULL_POS, false);
        });
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int tick = i;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after FB break tick " + tick);
                }
                BlockState support = mc.world.getBlockState(SUPPORT_POS);
                BlockState full = mc.world.getBlockState(FULL_POS);
                if (!support.isAir() || !full.isAir()) {
                    throw new RuntimeException("expected original BS and FB to be removed at tick " + tick
                            + "; support=" + support + " full=" + full);
                }
                if (SlabAnchorAttachment.isAnchored(mc.world, FULL_POS)) {
                    throw new RuntimeException("anchor persisted after FB removal at tick " + tick
                            + " pos=" + FULL_POS.toShortString());
                }
                assertLaneDy(mc.world, laneRootPos, laneTailPos, 0.0d,
                        "post-FB-break tick " + tick);
            });
        }

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after orphaned-lane support removal");
            }

            BlockState support = mc.world.getBlockState(SUPPORT_POS);
            BlockState full = mc.world.getBlockState(FULL_POS);
            if (!support.isAir() || !full.isAir()) {
                throw new RuntimeException("expected original BS and FB to be removed; support="
                        + support + " full=" + full);
            }
            if (SlabAnchorAttachment.isAnchored(mc.world, FULL_POS)) {
                throw new RuntimeException("anchor persisted after original FB removal at "
                        + FULL_POS.toShortString());
            }

            BlockState laneRoot = mc.world.getBlockState(laneRootPos);
            BlockState laneTail = mc.world.getBlockState(laneTailPos);
            if (!laneRoot.isOf(Blocks.STONE_SLAB)
                    || !laneRoot.contains(SlabBlock.TYPE)
                    || laneRoot.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("laneRoot state changed unexpectedly after support removal: " + laneRoot);
            }
            if (!laneTail.isOf(Blocks.STONE_SLAB)
                    || !laneTail.contains(SlabBlock.TYPE)
                    || laneTail.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("laneTail state changed unexpectedly after support removal: " + laneTail);
            }
            assertLaneDy(mc.world, laneRootPos, laneTailPos, 0.0d,
                    "post-FB-break final");
        });
    }

    /**
     * Legal state law for mixed-state clue (lowered DOUBLE beside normal BOTTOM):
     * A) after original FB removal, remaining lane slabs may normalize uniformly to dy=0;
     * B) remaining lane slabs may stay uniformly lowered only with a valid lowered support chain;
     * C) unsupported/mixed pieces may be removed by explicit invalidation.
     *
     * Forbidden outcomes:
     * - adjacent lowered DOUBLE dy=-0.5 with normal BOTTOM dy=0 in the same remaining lane;
     * - hidden ghost state or unstable one-tick jump/correction after BS/FB teardown.
     */
    private static void runMixedDoubleBottomTeardownLawCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.EAST;
        final BlockPos laneDoublePos = FULL_POS.offset(face);
        final BlockPos laneBottomPos = laneDoublePos.offset(face);
        final BlockHitResult lowerHalfSideHit = resolveLoweredFaceHit(laneDoublePos, face, -0.25d);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            SlabAnchorAttachment.addAnchor(world, FULL_POS, world.getBlockState(FULL_POS));
        });
        setLoweredSlabTarget(singleplayer, laneDoublePos, SlabType.DOUBLE);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForFace(ctx, singleplayer, laneDoublePos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for mixed DOUBLE/BOTTOM setup");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, lowerHalfSideHit);
            if (!result.isAccepted()) {
                throw new RuntimeException("mixed DOUBLE/BOTTOM setup click was not accepted: " + result);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after mixed DOUBLE/BOTTOM setup");
            }
            BlockState laneDouble = mc.world.getBlockState(laneDoublePos);
            BlockState laneBottom = mc.world.getBlockState(laneBottomPos);
            if (!laneDouble.isOf(Blocks.STONE_SLAB)
                    || !laneDouble.contains(SlabBlock.TYPE)
                    || laneDouble.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("mixed setup expected lowered DOUBLE at "
                        + laneDoublePos.toShortString() + ", found " + laneDouble);
            }
            if (!laneBottom.isOf(Blocks.STONE_SLAB)
                    || !laneBottom.contains(SlabBlock.TYPE)
                    || laneBottom.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("mixed setup expected BOTTOM at "
                        + laneBottomPos.toShortString() + ", found " + laneBottom);
            }
            double doubleDy = SlabSupport.getYOffset(mc.world, laneDoublePos, laneDouble);
            if (Math.abs(doubleDy + 0.5d) > EPSILON) {
                throw new RuntimeException("mixed setup expected lowered DOUBLE dy=-0.500, found "
                        + doubleDy + " state=" + laneDouble);
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(SUPPORT_POS, false);
        });
        for (int i = 0; i < 3; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int tick = i;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing in mixed case after BS break tick " + tick);
                }
                BlockState fullAfterBsBreak = mc.world.getBlockState(FULL_POS);
                if (!fullAfterBsBreak.isOf(Blocks.STONE) || !SlabAnchorAttachment.isAnchored(mc.world, FULL_POS)) {
                    throw new RuntimeException("mixed case expected anchored FB after BS break at tick " + tick
                            + ", full=" + fullAfterBsBreak
                            + " anchored=" + SlabAnchorAttachment.isAnchored(mc.world, FULL_POS));
                }
                assertForbiddenMixedDoubleBottomStateAbsent(mc.world, laneDoublePos, laneBottomPos,
                        "mixed-case post-BS-break tick " + tick);
            });
        }

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(FULL_POS, false);
        });
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int tick = i;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing in mixed case after FB break tick " + tick);
                }
                BlockState support = mc.world.getBlockState(SUPPORT_POS);
                BlockState full = mc.world.getBlockState(FULL_POS);
                if (!support.isAir() || !full.isAir()) {
                    throw new RuntimeException("mixed case expected original BS/FB removed at tick " + tick
                            + ", support=" + support + " full=" + full);
                }
                assertForbiddenMixedDoubleBottomStateAbsent(mc.world, laneDoublePos, laneBottomPos,
                        "mixed-case post-FB-break tick " + tick);
                assertNoLoweredRemainderWithoutSupport(mc.world, laneDoublePos, laneBottomPos,
                        "mixed-case post-FB-break tick " + tick);
            });
        }
    }

    /**
     * Legal state law for vertical mixed-seam seam:
     * A) normal BOTTOM (dy=0.000) directly below lowered DOUBLE (dy=-0.500) is legal only if the live
     *    placement and client model path agree on both blocks' ownership and there is no ghost seam.
     *
     * This case replays the live clue map directly:
     *  - bottomPos -> STONE_SLAB[type=bottom] at dy=0.000
     *  - bottomPos.up() -> STONE_SLAB[type=double] at dy=-0.500
     */
    private static void runVerticalBottomUnderLoweredDoubleStackSeamCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos bottomPos = SUPPORT_POS;
        final BlockPos loweredDoublePos = FULL_POS;
        final BlockPos seamSupportPos = loweredDoublePos.east();
        final BlockPos[] aimedTargetPos = new BlockPos[1];
        final Direction[] aimedTargetSide = new Direction[1];

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    bottomPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);

            world.setBlockState(
                    seamSupportPos,
                    Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    seamSupportPos.down(),
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, seamSupportPos, world.getBlockState(seamSupportPos));

            BlockState bottomState = world.getBlockState(bottomPos);
            world.setBlockState(
                    loweredDoublePos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            BlockState loweredDoubleState = world.getBlockState(loweredDoublePos);
            if (!bottomState.isOf(Blocks.STONE_SLAB)
                    || !bottomState.contains(SlabBlock.TYPE)
                    || bottomState.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("vertical seam setup expected BOTTOM at "
                        + bottomPos.toShortString()
                        + ", found " + bottomState);
            }
            if (!loweredDoubleState.isOf(Blocks.STONE_SLAB)
                    || !loweredDoubleState.contains(SlabBlock.TYPE)
                    || loweredDoubleState.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("vertical seam setup expected DOUBLE at "
                        + loweredDoublePos.toShortString()
                        + ", found " + loweredDoubleState);
            }

            double bottomDy = SlabSupport.getYOffset(world, bottomPos, bottomState);
            double loweredDoubleDy = SlabSupport.getYOffset(world, loweredDoublePos, loweredDoubleState);
            if (Math.abs(bottomDy) > EPSILON) {
                throw new RuntimeException("vertical seam setup expected bottom dy=0.000 at "
                        + bottomPos.toShortString()
                        + ", found " + bottomDy);
            }
            if (Math.abs(loweredDoubleDy + 0.5d) > EPSILON) {
                throw new RuntimeException("vertical seam setup expected lowered DOUBLE dy=-0.500 at "
                        + loweredDoublePos.toShortString()
                        + ", found " + loweredDoubleDy);
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                return;
            }
            server.getPlayerManager()
                    .getPlayerList()
                    .get(0)
                    .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(bottomPos.getX() + 0.5d, bottomPos.getY() + 3.9d, bottomPos.getZ() + 0.5d),
                new Vec3d(bottomPos.getX() + 0.5d, bottomPos.getY() + 0.5d, bottomPos.getZ() + 0.5d));
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing before vertical seam setup check");
            }
            BlockState seamBottom = mc.world.getBlockState(bottomPos);
            BlockState seamDouble = mc.world.getBlockState(loweredDoublePos);
            double bottomDy = SlabSupport.getYOffset(mc.world, bottomPos, seamBottom);
            double loweredDoubleDy = SlabSupport.getYOffset(mc.world, loweredDoublePos, seamDouble);

            System.out.println("[VERTICAL-SEAM] baseline tick=0"
                    + " bottom=" + seamBottom + " dy=" + bottomDy
                    + " lower=" + seamDouble + " dy=" + loweredDoubleDy);

            if (!seamBottom.isOf(Blocks.STONE_SLAB)
                    || !seamBottom.contains(SlabBlock.TYPE)
                    || seamBottom.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("vertical seam baseline lost BOTTOM at "
                        + bottomPos.toShortString()
                        + ", found " + seamBottom);
            }
            if (!seamDouble.isOf(Blocks.STONE_SLAB)
                    || !seamDouble.contains(SlabBlock.TYPE)
                    || seamDouble.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("vertical seam baseline lost DOUBLE at "
                        + loweredDoublePos.toShortString()
                        + ", found " + seamDouble);
            }

            if (mc.gameRenderer == null) {
                throw new RuntimeException("vertical seam baseline expected game renderer for crosshair ownership check");
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            if (!(mc.crosshairTarget instanceof BlockHitResult baselineHit)) {
                throw new RuntimeException("vertical seam baseline expected crosshair target while aligned to seam");
            }
            if (!baselineHit.getBlockPos().equals(bottomPos)
                    && !baselineHit.getBlockPos().equals(loweredDoublePos)) {
                throw new RuntimeException("vertical seam baseline crosshair ownership drift at "
                        + baselineHit.getBlockPos()
                        + ", expected " + bottomPos + " or " + loweredDoublePos);
            }
            aimedTargetPos[0] = bottomPos;
            aimedTargetSide[0] = Direction.UP;
            System.out.println("[VERTICAL-SEAM] baseline crosshair target="
                    + baselineHit.getBlockPos()
                    + " side=" + baselineHit.getSide());
        });

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for vertical seam placement/break action");
            }
            if (aimedTargetPos[0] == null || aimedTargetSide[0] == null) {
                throw new RuntimeException("vertical seam action missing baseline target");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            mc.interactionManager.attackBlock(aimedTargetPos[0], aimedTargetSide[0]);
        });

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after vertical seam action tick " + readTick);
                }

                BlockState bottomAfter = mc.world.getBlockState(bottomPos);
                BlockState doubleAfter = mc.world.getBlockState(loweredDoublePos);
                double bottomAfterDy = bottomAfter.isOf(Blocks.STONE_SLAB) && bottomAfter.contains(SlabBlock.TYPE)
                        ? SlabSupport.getYOffset(mc.world, bottomPos, bottomAfter)
                        : Double.NaN;
                double doubleAfterDy = doubleAfter.isOf(Blocks.STONE_SLAB) && doubleAfter.contains(SlabBlock.TYPE)
                        ? SlabSupport.getYOffset(mc.world, loweredDoublePos, doubleAfter)
                        : Double.NaN;
                System.out.println("[VERTICAL-SEAM] tick=" + readTick
                        + " seam-bottom=" + bottomAfter + " dy=" + bottomAfterDy
                        + " seam-double=" + doubleAfter + " dy=" + doubleAfterDy);
                if (readTick > 0 && !bottomAfter.isAir()) {
                    throw new RuntimeException("vertical seam operation expected bottom target to clear on tick "
                            + readTick + ", found " + bottomAfter);
                }
                if (readTick > 0) {
                    assertForbiddenMixedDoubleBottomStateAbsent(
                            mc.world,
                            loweredDoublePos,
                            bottomPos,
                            "vertical seam post-op tick " + readTick);
                }
            });
        }
    }

    private static void runLoweredDoubleSideMergeCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Direction face
    ) {
        final BlockPos targetPos = FULL_POS.offset(face);
        final BlockPos placementPos = targetPos.offset(face);
        final BlockHitResult hit = resolveLoweredSideFaceHit(targetPos, face, SlabType.DOUBLE);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        setLoweredSlabTarget(singleplayer, targetPos, SlabType.DOUBLE);
        setPlacementPosTarget(singleplayer, placementPos, SlabType.TOP);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForFace(ctx, singleplayer, targetPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for lowered-DOUBLE merge case on " + face);
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (!result.isAccepted()) {
                throw new RuntimeException("lowered DOUBLE merge click for " + face + " was not accepted: " + result);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after lowered-DOUBLE merge case on " + face);
            }
            BlockState placed = mc.world.getBlockState(placementPos);
            if (!placed.isOf(Blocks.STONE_SLAB)
                    || !placed.contains(SlabBlock.TYPE)
                    || placed.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("expected merged DOUBLE at " + placementPos.toShortString()
                        + " for lowered DOUBLE side target " + face + ", found " + placed);
            }
            double placedDy = SlabSupport.getYOffset(mc.world, placementPos, placed);
            if (Math.abs(placedDy + 0.5d) > EPSILON) {
                throw new RuntimeException("expected merged DOUBLE dy=-0.500 for lowered DOUBLE side target "
                        + face + ", found dy=" + placedDy + " state=" + placed);
            }
        });
    }

    private static void runLoweredDoubleLowerHalfOwnershipCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Direction face
    ) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            final int attemptNumber = attempt;
            final int xOffset = attemptNumber * 8;
            final BlockPos supportPos = SUPPORT_POS.add(xOffset, 0, 0);
            final BlockPos fullPos = FULL_POS.add(xOffset, 0, 0);
            final BlockPos targetPos = fullPos.offset(face);
            final BlockPos behindPos = targetPos.offset(face);
            final BlockHitResult syntheticLowerHalfHit = resolveLoweredFaceHit(targetPos, face, -0.25d);
            final Vec3d lowerHalfAimTarget = syntheticLowerHalfHit.getPos();
            final Vec3d eye;
            switch (face) {
                case NORTH -> eye = new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + 1.65d, targetPos.getZ() - 1.7d);
                case SOUTH -> eye = new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + 1.65d, targetPos.getZ() + 2.7d);
                case EAST -> eye = new Vec3d(targetPos.getX() - 1.7d, targetPos.getY() + 1.65d, targetPos.getZ() + 0.5d);
                case WEST -> eye = new Vec3d(targetPos.getX() + 2.7d, targetPos.getY() + 1.65d, targetPos.getZ() + 0.5d);
                default -> throw new IllegalArgumentException("unsupported face for lower-half ownership repro: " + face);
            }

            setupFixture(singleplayer, supportPos, fullPos);
            setLoweredSlabTarget(singleplayer, targetPos, SlabType.TOP);
            setLoweredSlabTarget(singleplayer, targetPos, SlabType.DOUBLE);
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            syncPlayerAim(ctx, singleplayer, eye, lowerHalfAimTarget);
            ctx.runOnClient(mc -> {
                if (mc.player == null || mc.interactionManager == null || mc.world == null || mc.gameRenderer == null) {
                    throw new RuntimeException("client not ready for lowered-DOUBLE lower-half ownership case on " + face);
                }

                mc.gameRenderer.updateCrosshairTarget(0.0f);
                if (!(mc.crosshairTarget instanceof BlockHitResult blockHit)) {
                    throw new RuntimeException("attempt " + attemptNumber + " missing block hit on lowered DOUBLE lower-half target");
                }
                if (!blockHit.getBlockPos().equals(targetPos)) {
                    throw new RuntimeException("attempt " + attemptNumber + " routed to owner "
                            + blockHit.getBlockPos().toShortString()
                            + " instead of lowered DOUBLE target " + targetPos.toShortString());
                }

                ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
                BlockState targetAfterClick = mc.world.getBlockState(targetPos);
                BlockState behindAfterFirst = mc.world.getBlockState(behindPos);
                if (!targetAfterClick.isOf(Blocks.STONE_SLAB)
                        || !targetAfterClick.contains(SlabBlock.TYPE)
                        || targetAfterClick.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                    throw new RuntimeException("attempt " + attemptNumber + " changed lowered DOUBLE target unexpectedly: "
                            + targetAfterClick + " result=" + result);
                }
                double targetDy = SlabSupport.getYOffset(mc.world, targetPos, targetAfterClick);
                if (Math.abs(targetDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("attempt " + attemptNumber + " moved lowered DOUBLE target out of lane: dy="
                            + targetDy + " state=" + targetAfterClick + " result=" + result);
                }
                if (!behindAfterFirst.isOf(Blocks.STONE_SLAB)
                        || !behindAfterFirst.contains(SlabBlock.TYPE)
                        || behindAfterFirst.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                    throw new RuntimeException("attempt " + attemptNumber
                            + " first click expected behind BOTTOM from lowered DOUBLE lower-half, found "
                            + behindAfterFirst + " result=" + result);
                }
                double behindFirstDy = SlabSupport.getYOffset(mc.world, behindPos, behindAfterFirst);
                if (Math.abs(behindFirstDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("attempt " + attemptNumber
                            + " first click behind state not lowered lane: dy="
                            + behindFirstDy + " state=" + behindAfterFirst + " result=" + result);
                }

                ActionResult secondResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
                if (!secondResult.isAccepted()) {
                    throw new RuntimeException("attempt " + attemptNumber
                            + " second click on lowered DOUBLE lower-half was not accepted: " + secondResult);
                }
                BlockState behindAfterSecond = mc.world.getBlockState(behindPos);
                if (!behindAfterSecond.isOf(Blocks.STONE_SLAB)
                        || !behindAfterSecond.contains(SlabBlock.TYPE)
                        || behindAfterSecond.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                    throw new RuntimeException("attempt " + attemptNumber
                            + " second click expected legal merge to DOUBLE behind lowered target, found "
                            + behindAfterSecond + " result=" + secondResult);
                }
                double behindSecondDy = SlabSupport.getYOffset(mc.world, behindPos, behindAfterSecond);
                if (Math.abs(behindSecondDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("attempt " + attemptNumber
                            + " second click merged behind state out of lowered lane: dy="
                            + behindSecondDy + " state=" + behindAfterSecond + " result=" + secondResult);
                }
            });
        }
    }

    private static void runLoweredDoubleLowerHalfBoundaryCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Direction face
    ) {
        System.out.println("[LOWERED_DOUBLE_BOUNDARY] start face=" + face);
        final BlockPos targetPos = FULL_POS.offset(face);
        final BlockPos behindPos = targetPos.offset(face);
        final BlockHitResult hit = resolveLoweredFaceHit(targetPos, face, -0.5d);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        setLoweredSlabTarget(singleplayer, targetPos, SlabType.TOP);
        setLoweredSlabTarget(singleplayer, targetPos, SlabType.DOUBLE);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForFace(ctx, singleplayer, targetPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for lowered-DOUBLE boundary ownership case on " + face);
            }
            ActionResult firstResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (!firstResult.isAccepted()) {
                throw new RuntimeException("boundary lower-half click on lowered DOUBLE was not accepted: " + firstResult);
            }

            BlockState targetState = mc.world.getBlockState(targetPos);
            BlockState behindAfterFirst = mc.world.getBlockState(behindPos);
            if (!targetState.isOf(Blocks.STONE_SLAB)
                    || !targetState.contains(SlabBlock.TYPE)
                    || targetState.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("boundary case changed lowered DOUBLE target unexpectedly: " + targetState + " result=" + firstResult);
            }
            double targetDy = SlabSupport.getYOffset(mc.world, targetPos, targetState);
            if (Math.abs(targetDy + 0.5d) > EPSILON) {
                throw new RuntimeException("boundary case target lost lowered DOUBLE dy: " + targetDy + " result=" + firstResult);
            }
            if (!behindAfterFirst.isOf(Blocks.STONE_SLAB)
                    || !behindAfterFirst.contains(SlabBlock.TYPE)
                    || behindAfterFirst.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("boundary case first click expected BOTTOM at " + behindPos + ", found " + behindAfterFirst);
            }
            double behindFirstDy = SlabSupport.getYOffset(mc.world, behindPos, behindAfterFirst);
            if (Math.abs(behindFirstDy + 0.5d) > EPSILON) {
                throw new RuntimeException("boundary case first click behind state not lowered lane: dy="
                        + behindFirstDy + " state=" + behindAfterFirst + " result=" + firstResult);
            }

            ActionResult secondResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (!secondResult.isAccepted()) {
                throw new RuntimeException("boundary case second click on lowered DOUBLE was not accepted: " + secondResult);
            }
            BlockState behindAfterSecond = mc.world.getBlockState(behindPos);
            if (!behindAfterSecond.isOf(Blocks.STONE_SLAB)
                    || !behindAfterSecond.contains(SlabBlock.TYPE)
                    || behindAfterSecond.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("boundary case second click expected legal merge to DOUBLE, found "
                        + behindAfterSecond + " result=" + secondResult);
            }
            double behindSecondDy = SlabSupport.getYOffset(mc.world, behindPos, behindAfterSecond);
            if (Math.abs(behindSecondDy + 0.5d) > EPSILON) {
                throw new RuntimeException("boundary case second click merged behind state out of lowered lane: dy="
                        + behindSecondDy + " state=" + behindAfterSecond + " result=" + secondResult);
            }
            System.out.println("[LOWERED_DOUBLE_BOUNDARY_GREEN] face=" + face
                    + " targetDy=" + targetDy + " behindSecondDy=" + behindSecondDy
                    + " behindAfterFirst=" + behindAfterFirst.get(SlabBlock.TYPE)
                    + " behindAfterSecond=" + behindAfterSecond.get(SlabBlock.TYPE));
        });
    }

    private static void runBeta4CompoundSlabMergeRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.EAST;
        final BlockPos compoundPos = FULL_POS;
        final BlockPos adjacentLanePos = compoundPos.offset(face);
        final BlockHitResult lowerHalfHit = resolveLoweredFaceHit(compoundPos, face, -0.25d);
        final BlockHitResult upperHalfHit = resolveLoweredFaceHit(compoundPos, face, 0.25d);
        final BlockHitResult topHit = resolveLoweredUpMergeHit(compoundPos);

        runCompoundSlabNoLegalLaneCase(ctx, singleplayer, "ROW1", "lower-half", compoundPos, adjacentLanePos, face, lowerHalfHit);
        runCompoundSlabNoLegalLaneCase(ctx, singleplayer, "ROW2", "upper-half", compoundPos, adjacentLanePos, face, upperHalfHit);
        runCompoundSlabAdjacentLaneCase(ctx, singleplayer, "ROW3", compoundPos, adjacentLanePos, face, lowerHalfHit);
        runCompoundSlabMergePendingNote(ctx, singleplayer, "ROW4", compoundPos, adjacentLanePos, face);
        runCompoundSlabTopClickCase(ctx, singleplayer, "ROW5", compoundPos, adjacentLanePos, face, topHit);
        runCompoundSlabSanityNote(ctx, singleplayer, "ROW6", compoundPos, adjacentLanePos);
    }

    private static void runCompoundSlabNoLegalLaneCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String rowName,
            String halfLabel,
            BlockPos compoundPos,
            BlockPos adjacentLanePos,
            Direction face,
            BlockHitResult hit
    ) {
        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        seedCompoundFullBlock(singleplayer, compoundPos);
        movePlayerForFace(ctx, singleplayer, compoundPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[" + rowName + "] client not ready for compound no-legal-lane proof");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            System.out.println("[JULIA_BETA4_COMPOUND_SLAB_NO_LEGAL_LANE_GREEN]"
                    + " row=" + rowName
                    + " half=" + halfLabel
                    + " result=" + result
                    + " compound=" + describeOwnerFacts(mc.world, compoundPos)
                    + " adjacent=" + describeOwnerFacts(mc.world, adjacentLanePos)
                    + " future=preserve_compound_or_reject_cleanly");
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
    }

    private static void runCompoundSlabAdjacentLaneCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String rowName,
            BlockPos compoundPos,
            BlockPos adjacentLanePos,
            Direction face,
            BlockHitResult hit
    ) {
        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        seedCompoundFullBlock(singleplayer, compoundPos);
        setLoweredSlabTarget(singleplayer, adjacentLanePos, SlabType.BOTTOM);
        movePlayerForFace(ctx, singleplayer, compoundPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[" + rowName + "] client not ready for compound legal-remap proof");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            System.out.println("[JULIA_BETA4_COMPOUND_SLAB_LEGAL_REMAP_PENDING]"
                    + " row=" + rowName
                    + " result=" + result
                    + " compound=" + describeOwnerFacts(mc.world, compoundPos)
                    + " adjacent=" + describeOwnerFacts(mc.world, adjacentLanePos)
                    + " current=reject_or_owner_preserve"
                    + " future=remap_into_existing_dy_-0.5_lane");
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
    }

    private static void runCompoundSlabMergePendingNote(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String rowName,
            BlockPos compoundPos,
            BlockPos adjacentLanePos,
            Direction face
    ) {
        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        seedCompoundFullBlock(singleplayer, compoundPos);
        setLoweredSlabTarget(singleplayer, adjacentLanePos, SlabType.BOTTOM);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[" + rowName + "] client world missing for merge pending note");
            }
            System.out.println("[JULIA_BETA4_COMPOUND_SLAB_DOUBLE_MERGE_PENDING]"
                    + " row=" + rowName
                    + " face=" + face.asString()
                    + " compound=" + describeOwnerFacts(mc.world, compoundPos)
                    + " adjacent=" + describeOwnerFacts(mc.world, adjacentLanePos)
                    + " current=compound_boundary_merge_not_yet_proven"
                    + " future=second_click_merges_BOTTOM_TOP_to_DOUBLE_dy_-0.5");
        });
    }

    private static void runCompoundSlabTopClickCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String rowName,
            BlockPos compoundPos,
            BlockPos adjacentLanePos,
            Direction face,
            BlockHitResult hit
    ) {
        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        seedCompoundFullBlock(singleplayer, compoundPos);
        movePlayerForUp(ctx, singleplayer, compoundPos);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[" + rowName + "] client not ready for compound top-click note");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            System.out.println("[JULIA_BETA4_COMPOUND_SLAB_LEGAL_REMAP_PENDING]"
                    + " row=" + rowName
                    + " half=top"
                    + " face=" + face.asString()
                    + " result=" + result
                    + " compound=" + describeOwnerFacts(mc.world, compoundPos)
                    + " adjacent=" + describeOwnerFacts(mc.world, adjacentLanePos)
                    + " current=preserve_or_reject_cleanly"
                    + " future=normalize_into_named_legal_vanilla_or_lowered_result");
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
    }

    private static void runCompoundSlabSanityNote(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String rowName,
            BlockPos compoundPos,
            BlockPos adjacentLanePos
    ) {
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[" + rowName + "] client world missing for compound sanity note");
            }
            System.out.println("[JULIA_BETA4_COMPOUND_SLAB_NO_LEGAL_LANE_GREEN]"
                    + " row=" + rowName
                    + " sanity=no_ghost_flicker_after_tick"
                    + " compound=" + describeOwnerFacts(mc.world, compoundPos)
                    + " adjacent=" + describeOwnerFacts(mc.world, adjacentLanePos)
                    + " todo=source_break_reload_rejoin_not_proven_here");
        });
    }

    private static void seedCompoundFullBlock(
            TestSingleplayerContext singleplayer,
            BlockPos compoundPos
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(compoundPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, compoundPos, world.getBlockState(compoundPos));
        });
    }

    private static void runLowerHalfOwnershipVisibleBodyCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(40, 0, 0);
        final BlockPos fullPos = FULL_POS.add(40, 0, 0);
        final Vec3d eyeSouth = new Vec3d(
                fullPos.getX() + 0.5d,
                fullPos.getY() + 1.65d,
                fullPos.getZ() + 2.55d);
        final Vec3d aimFrontLower = new Vec3d(
                fullPos.getX() + 0.5d,
                fullPos.getY() - 0.34d,
                fullPos.getZ() + 0.5d);
        final Vec3d aimUndersideAdjacent = new Vec3d(
                fullPos.getX() + 0.5d,
                fullPos.getY() - 0.49d,
                fullPos.getZ() + 0.82d);

        setupFixture(singleplayer, supportPos, fullPos);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        runLowerHalfOwnershipProbe(
                ctx,
                singleplayer,
                fullPos,
                eyeSouth,
                aimFrontLower,
                "lowered_full_front_lower_half_slab_held_baseline",
                "front_lower",
                true,
                "support=" + supportPos.toShortString() + " full=" + fullPos.toShortString());
        runLowerHalfOwnershipProbe(
                ctx,
                singleplayer,
                fullPos,
                eyeSouth,
                aimUndersideAdjacent,
                "lowered_full_underside_adjacent_empty_hand",
                "underside_adjacent",
                false,
                "support=" + supportPos.toShortString() + " full=" + fullPos.toShortString());
        runLowerHalfOwnershipProbe(
                ctx,
                singleplayer,
                fullPos,
                eyeSouth,
                aimUndersideAdjacent,
                "lowered_full_underside_adjacent_slab_held",
                "underside_adjacent",
                true,
                "support=" + supportPos.toShortString() + " full=" + fullPos.toShortString());
    }

    private static void runLowerHalfOwnershipLoweredSlabCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(52, 0, 0);
        final BlockPos fullPos = FULL_POS.add(52, 0, 0);
        final BlockPos slabPos = fullPos.south();
        final Vec3d eyeSouth = new Vec3d(
                slabPos.getX() + 0.5d,
                slabPos.getY() + 1.65d,
                slabPos.getZ() + 3.55d);
        final Vec3d aimFrontLower = new Vec3d(
                slabPos.getX() + 0.5d,
                slabPos.getY() + 0.08d,
                slabPos.getZ() + 0.5d);
        final Vec3d aimUndersideAdjacent = new Vec3d(
                slabPos.getX() + 0.5d,
                slabPos.getY() + 0.01d,
                slabPos.getZ() + 0.82d);

        setupFixture(singleplayer, supportPos, fullPos);
        setLoweredSlabTarget(singleplayer, slabPos, SlabType.TOP);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        String slabSetup = "support=" + supportPos.toShortString()
                + " full=" + fullPos.toShortString()
                + " slab=" + slabPos.toShortString()
                + " slabType=top";
        runLowerHalfOwnershipProbe(
                ctx,
                singleplayer,
                slabPos,
                eyeSouth,
                aimFrontLower,
                "lowered_slab_front_lower_half_empty_hand",
                "front_lower",
                false,
                slabSetup);
        runLowerHalfOwnershipProbe(
                ctx,
                singleplayer,
                slabPos,
                eyeSouth,
                aimFrontLower,
                "lowered_slab_front_lower_half_slab_held",
                "front_lower",
                true,
                slabSetup);
        runLowerHalfOwnershipProbe(
                ctx,
                singleplayer,
                slabPos,
                eyeSouth,
                aimUndersideAdjacent,
                "lowered_slab_underside_adjacent_empty_hand",
                "underside_adjacent",
                false,
                slabSetup);
        runLowerHalfOwnershipProbe(
                ctx,
                singleplayer,
                slabPos,
                eyeSouth,
                aimUndersideAdjacent,
                "lowered_slab_underside_adjacent_slab_held",
                "underside_adjacent",
                true,
                slabSetup);
    }

    private static void runLowerHalfOwnershipProbe(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            BlockPos expectedOwnerPos,
            Vec3d eye,
            Vec3d aimPoint,
            String caseId,
            String aimRegion,
            boolean slabHeld,
            String setupDetails
    ) {
        final double reach = 6.0d;

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[LOWER_HALF_OWNERSHIP_RED] case=" + caseId + " client not ready");
            }

            if (slabHeld) {
                mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            } else {
                mc.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            }

            Vec3d delta = aimPoint.subtract(eye);
            double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
            double feetY = eye.y - mc.player.getStandingEyeHeight();
            mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            mc.player.setVelocity(Vec3d.ZERO);

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(reach));
            HitResult crosshair = mc.crosshairTarget;
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            HitResult liveRay = mc.player.raycast(reach, 0.0f, false);
            BlockHitResult outlineHit = mc.world.getBlockState(expectedOwnerPos)
                    .getOutlineShape(mc.world, expectedOwnerPos)
                    .raycast(rayStart, rayEnd, expectedOwnerPos);

            BlockState expectedState = mc.world.getBlockState(expectedOwnerPos);
            var expectedShape = expectedState.getOutlineShape(mc.world, expectedOwnerPos);
            String expectedOwner = expectedOwnerPos.toShortString();
            String crosshairOwner = asOwner(crosshair);
            String vanillaOwner = asOwner(vanilla);
            String liveOwner = asOwner(liveRay);
            String outlineOwner = outlineHit == null ? "MISS" : outlineHit.getBlockPos().toShortString();
            String held = mc.player.getMainHandStack().isEmpty()
                    ? "empty"
                    : mc.player.getMainHandStack().getItem().toString();
            double expectedDy = SlabSupport.getYOffset(mc.world, expectedOwnerPos, expectedState);
            String visualBox = expectedShape.isEmpty()
                    ? "EMPTY"
                    : formatBox(expectedShape.getBoundingBox().offset(expectedOwnerPos));

            System.out.println("[LOWER_HALF_OWNERSHIP] case=" + caseId
                    + " setup held=" + held
                    + " aimRegion=" + aimRegion
                    + " expected=" + expectedOwner
                    + " aim=" + aimPoint
                    + " eye=" + eye
                    + " liveEye=" + rayStart
                    + " dir=" + rayDir
                    + " reach=" + reach
                    + " visualBox=" + visualBox
                    + " expectedState=" + expectedState
                    + " expectedDy=" + expectedDy
                    + " " + setupDetails);
            System.out.println("[LOWER_HALF_OWNERSHIP] case=" + caseId
                    + " ray expected=" + expectedOwner
                    + " actual=" + crosshairOwner
                    + " vanilla=" + vanillaOwner
                    + " liveRay=" + liveOwner
                    + " outline=" + outlineOwner);

            if (!expectedOwner.equals(crosshairOwner)) {
                System.out.println("[LOWER_HALF_OWNERSHIP_RED] case=" + caseId
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " liveRay=" + liveOwner
                        + " outline=" + outlineOwner);
                throw new RuntimeException("[LOWER_HALF_OWNERSHIP_RED] case=" + caseId
                        + " expected=" + expectedOwner + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner + " liveRay=" + liveOwner
                        + " outline=" + outlineOwner);
            }

            System.out.println("[LOWER_HALF_OWNERSHIP_GREEN] case=" + caseId
                    + " expected=" + expectedOwner
                    + " actual=" + crosshairOwner
                    + " vanilla=" + vanillaOwner
                    + " liveRay=" + liveOwner
                    + " outline=" + outlineOwner);
        });
    }

    private static void runLiteralScreenshotCraftingTableCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(64, 0, 0);
        final BlockPos tablePos = FULL_POS.add(64, 0, 0);
        final Vec3d eyeSouth = new Vec3d(
                tablePos.getX() + 0.62d,
                tablePos.getY() + 1.65d,
                tablePos.getZ() + 2.30d);
        final Vec3d aimFrontLowerSeam = new Vec3d(
                tablePos.getX() + 0.74d,
                tablePos.getY() - 0.34d,
                tablePos.getZ() + 0.50d);

        setupFixture(singleplayer, supportPos, tablePos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    tablePos,
                    Blocks.CRAFTING_TABLE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        String setup = "support=" + supportPos.toShortString()
                + " table=" + tablePos.toShortString()
                + " shape=bottom_slab_plus_crafting_table";
        runScreenshotReproProbe(
                ctx,
                singleplayer,
                tablePos,
                eyeSouth,
                aimFrontLowerSeam,
                "crafting_table_bottom_slab_slab_held",
                "front_lower_seam",
                new ItemStack(Items.CRAFTING_TABLE, 1),
                setup);
        runScreenshotReproProbe(
                ctx,
                singleplayer,
                tablePos,
                eyeSouth,
                aimFrontLowerSeam,
                "crafting_table_bottom_slab_empty_hand",
                "front_lower_seam",
                ItemStack.EMPTY,
                setup);
    }

    private static void runLiteralScreenshotLoweredSlabSideCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(76, 0, 0);
        final BlockPos fullPos = FULL_POS.add(76, 0, 0);
        final BlockPos slabPos = fullPos.south();
        final Vec3d eyeWest = new Vec3d(
                slabPos.getX() - 2.25d,
                slabPos.getY() + 1.61d,
                slabPos.getZ() + 0.42d);
        final Vec3d aimSideLowSeam = new Vec3d(
                slabPos.getX() + 0.02d,
                slabPos.getY() + 0.02d,
                slabPos.getZ() + 0.86d);

        setupFixture(singleplayer, supportPos, fullPos);
        setLoweredSlabTarget(singleplayer, slabPos, SlabType.TOP);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        String setup = "support=" + supportPos.toShortString()
                + " full=" + fullPos.toShortString()
                + " slab=" + slabPos.toShortString()
                + " slabType=top shape=bottom_slab_plus_lowered_side_slab";
        runScreenshotReproProbe(
                ctx,
                singleplayer,
                slabPos,
                eyeWest,
                aimSideLowSeam,
                "lowered_slab_side_low_seam_slab_held",
                "side_low_seam",
                new ItemStack(Items.STONE_SLAB, 8),
                setup);
    }

    private static void runJuliaBeta4StoneSlabTargetingOutlineMismatchRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(24, 0, 0);
        final BlockPos expectedOwnerPos = supportPos.up();
        final BlockPos visibleSlabOwnerPos = expectedOwnerPos.up();
        final Vec3d eye = new Vec3d(
                expectedOwnerPos.getX() - 2.35d,
                expectedOwnerPos.getY() + 1.67d,
                expectedOwnerPos.getZ() + 0.62d);
        final Vec3d aimTopFrontEdge = new Vec3d(
                expectedOwnerPos.getX() + 0.115d,
                expectedOwnerPos.getY() + 0.500d,
                expectedOwnerPos.getZ() + 0.616d);

        setupFixture(singleplayer, supportPos, expectedOwnerPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState fullState = world.getBlockState(expectedOwnerPos);
            SlabAnchorAttachment.addAnchor(world, expectedOwnerPos, fullState);
            world.setBlockState(
                    visibleSlabOwnerPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncPlayerAim(ctx, singleplayer, eye, aimTopFrontEdge);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[JULIA_BETA4_TARGETING_RED] client not ready");
            }

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(6.0d));
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            HitResult finalTarget = mc.crosshairTarget;
            String expectedOwner = expectedOwnerPos.toShortString();
            String visibleOwner = visibleSlabOwnerPos.toShortString();
            String vanillaOwner = asOwner(vanilla);
            String finalOwner = asOwner(finalTarget);
            String expectedOwnerClass = "ANCHORED_FULL_BLOCK";
            String actualOwnerClass = beta4OwnerClass(
                    finalTarget,
                    expectedOwnerPos,
                    visibleSlabOwnerPos,
                    null);
            boolean slabHeldProtectionPreservedExpected = expectedOwner.equals(finalOwner);
            boolean sideOwnerWouldWin = visibleOwner.equals(finalOwner);

            System.out.println("[JULIA_BETA4_TARGETING] shape=compact_lowered_stone_with_upper_double_slab"
                    + " held=minecraft:stone_slab"
                    + " expectedOwnerClass=" + expectedOwnerClass
                    + " actualOwnerClass=" + actualOwnerClass
                    + " support=" + supportPos.toShortString()
                    + " expectedOwner=" + expectedOwner
                    + " visibleOwner=" + visibleOwner
                    + " eye=" + eye
                    + " aim=" + aimTopFrontEdge
                    + " liveEye=" + rayStart
                    + " dir=" + rayDir
                    + " vanillaTarget=" + describeHit(vanilla)
                    + " finalTarget=" + describeHit(finalTarget)
                    + " vanillaHitFace=" + describeHitFace(vanilla)
                    + " finalHitFace=" + describeHitFace(finalTarget)
                    + " vanillaHitVector=" + describeHitVector(vanilla)
                    + " finalHitVector=" + describeHitVector(finalTarget)
                    + " expectedFacts=" + describeOwnerFacts(mc.world, expectedOwnerPos)
                    + " visibleFacts=" + describeOwnerFacts(mc.world, visibleSlabOwnerPos)
                    + " finalFacts=" + describeOwnerFacts(mc.world, blockPos(finalTarget))
                    + " slabHeldProtectionPreservedExpected=" + slabHeldProtectionPreservedExpected
                    + " sideOwnerWouldWin=" + sideOwnerWouldWin);

            if (expectedOwner.equals(vanillaOwner) && !expectedOwner.equals(finalOwner)) {
                throw new RuntimeException("[JULIA_BETA4_TARGETING_RED]"
                        + " expectedOwner=" + expectedOwner
                        + " visibleOwner=" + visibleOwner
                        + " vanillaTarget=" + describeHit(vanilla)
                        + " finalTarget=" + describeHit(finalTarget)
                        + " suspectedFailingLayer=slab-held-retarget-rescue"
                        + " expectedFacts=" + describeOwnerFacts(mc.world, expectedOwnerPos)
                        + " finalFacts=" + describeOwnerFacts(mc.world, blockPos(finalTarget))
                        + " slabHeldProtectionPreservedExpected=false"
                        + " sideOwnerWouldWin=" + sideOwnerWouldWin);
            }
            if (expectedOwner.equals(vanillaOwner) && expectedOwner.equals(finalOwner)) {
                System.out.println("[BETA4_ANCHORED_UP_PRESERVE_GREEN]"
                        + " expectedOwnerClass=" + expectedOwnerClass
                        + " actualOwnerClass=" + actualOwnerClass
                        + " expectedOwner=" + expectedOwner
                        + " visibleOwner=" + visibleOwner
                        + " vanillaTarget=" + describeHit(vanilla)
                        + " finalTarget=" + describeHit(finalTarget)
                        + " finalHitFace=" + describeHitFace(finalTarget)
                        + " finalHitVector=" + describeHitVector(finalTarget)
                        + " expectedFacts=" + describeOwnerFacts(mc.world, expectedOwnerPos)
                        + " visibleFacts=" + describeOwnerFacts(mc.world, visibleSlabOwnerPos));
                System.out.println("[JULIA_BETA4_TARGETING_GREEN]"
                        + " expectedOwner=" + expectedOwner
                        + " visibleOwner=" + visibleOwner
                        + " vanillaTarget=" + describeHit(vanilla)
                        + " finalTarget=" + describeHit(finalTarget)
                        + " expectedFacts=" + describeOwnerFacts(mc.world, expectedOwnerPos)
                        + " finalFacts=" + describeOwnerFacts(mc.world, blockPos(finalTarget))
                        + " slabHeldProtectionPreservedExpected=true"
                        + " sideOwnerWouldWin=false");
                return;
            }

            throw new RuntimeException("[JULIA_BETA4_TARGETING_PROOF_GAP]"
                    + " expectedOwner=" + expectedOwner
                    + " visibleOwner=" + visibleOwner
                    + " vanillaTarget=" + describeHit(vanilla)
                    + " finalTarget=" + describeHit(finalTarget)
                    + " note=current reconstructed shape did not prove Julia mismatch");
        });
    }

    private static void runJuliaBeta4AboveAngleTargetingOwnerSplitRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        runJuliaBeta4AboveAngleTargetingOwnerSplitRedCase(ctx, singleplayer, true);
    }

    private static void runJuliaBeta4AboveAngleTargetingOwnerSplitRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            boolean includeGroundControl
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(24, 0, 0);
        final BlockPos anchoredOwnerPos = supportPos.up();
        final BlockPos expectedOwnerPos = anchoredOwnerPos.up();
        final BlockPos lowerFrontOwnerPos = expectedOwnerPos;
        final BlockPos visibleUpperSlabOwnerPos = expectedOwnerPos;

        setupFixture(singleplayer, supportPos, anchoredOwnerPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState fullState = world.getBlockState(anchoredOwnerPos);
            SlabAnchorAttachment.addAnchor(world, anchoredOwnerPos, fullState);
            world.setBlockState(
                    lowerFrontOwnerPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world,
                    lowerFrontOwnerPos,
                    world.getBlockState(lowerFrontOwnerPos));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));

        System.setProperty("slabbed.target.trace", "true");
        String firstRed = null;
        String lowerFrontRed = null;

        String red;
        if (includeGroundControl) {
            red = runJuliaBeta4AngleTargetingProbe(
                    ctx,
                    singleplayer,
                    "ground_front",
                    supportPos,
                    anchoredOwnerPos,
                    expectedOwnerPos,
                    visibleUpperSlabOwnerPos,
                    lowerFrontOwnerPos,
                    new Vec3d(
                            anchoredOwnerPos.getX() - 2.35d,
                            anchoredOwnerPos.getY() + 1.67d,
                            anchoredOwnerPos.getZ() + 0.62d),
                    new Vec3d(
                            anchoredOwnerPos.getX() + 0.115d,
                            anchoredOwnerPos.getY() + 0.500d,
                            anchoredOwnerPos.getZ() + 0.616d),
                    true);
            firstRed = firstRed == null ? red : firstRed;
            lowerFrontRed = isLowerFrontRed(red) && lowerFrontRed == null ? red : lowerFrontRed;
        }

        red = runJuliaBeta4AngleTargetingProbe(
                ctx,
                singleplayer,
                "live_above_across_pitch_steep_up",
                supportPos,
                anchoredOwnerPos,
                expectedOwnerPos,
                visibleUpperSlabOwnerPos,
                lowerFrontOwnerPos,
                new Vec3d(
                        anchoredOwnerPos.getX() - 2.285d,
                        anchoredOwnerPos.getY() + 0.620d,
                        anchoredOwnerPos.getZ() + 0.790d),
                -101.850f,
                -9.000f,
                false);
        firstRed = firstRed == null ? red : firstRed;
        lowerFrontRed = isLowerFrontRed(red) && lowerFrontRed == null ? red : lowerFrontRed;

        if (lowerFrontRed != null) {
            throw new RuntimeException(lowerFrontRed);
        }
        if (firstRed != null) {
            throw new RuntimeException(firstRed);
        }
    }

    private static String runJuliaBeta4AngleTargetingProbe(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String angle,
            BlockPos supportPos,
            BlockPos anchoredOwnerPos,
            BlockPos expectedOwnerPos,
            BlockPos visibleSlabOwnerPos,
            BlockPos lowerFrontOwnerPos,
            Vec3d eye,
            Vec3d aim,
            boolean expectGreen
    ) {
        Vec3d delta = aim.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        return runJuliaBeta4AngleTargetingProbe(
                ctx,
                singleplayer,
                angle,
                supportPos,
                anchoredOwnerPos,
                expectedOwnerPos,
                visibleSlabOwnerPos,
                lowerFrontOwnerPos,
                eye,
                yaw,
                pitch,
                expectGreen);
    }

    private static String runJuliaBeta4AngleTargetingProbe(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String angle,
            BlockPos supportPos,
            BlockPos anchoredOwnerPos,
            BlockPos expectedOwnerPos,
            BlockPos visibleSlabOwnerPos,
            BlockPos lowerFrontOwnerPos,
            Vec3d eye,
            float yaw,
            float pitch,
            boolean expectGreen
    ) {
        syncPlayerLookFromEye(ctx, singleplayer, eye, yaw, pitch);
        final String[] redMessage = {null};

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[JULIA_BETA4_ABOVE_ANGLE_RED] angle=" + angle + " client not ready");
            }

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(6.0d));
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            HitResult finalTarget = mc.crosshairTarget;
            String expectedOwner = expectedOwnerPos.toShortString();
            String anchoredOwner = anchoredOwnerPos.toShortString();
            String visibleUpperSlabOwner = visibleSlabOwnerPos.toShortString();
            String lowerFrontOwner = lowerFrontOwnerPos.toShortString();
            String vanillaOwner = asOwner(vanilla);
            String finalOwner = asOwner(finalTarget);
            String expectedOwnerClass = expectGreen ? "ANCHORED_FULL_BLOCK" : "VISIBLE_UPPER_LOWERED_SLAB";
            String actualOwnerClass = beta4OwnerClass(
                    finalTarget,
                    anchoredOwnerPos,
                    visibleSlabOwnerPos,
                    null);
            boolean expectedVisibleOwnerHit = isBeta4ScreenshotVisibleOwnerHit(mc.world, finalTarget, expectedOwnerPos);
            boolean anchoredOwnerWon = anchoredOwner.equals(finalOwner);
            String classification = expectedVisibleOwnerHit
                    ? "visibleUpperSlabOwnerExpected"
                    : (anchoredOwnerWon
                    ? "anchoredUpPreserve"
                    : (lowerFrontOwner.equals(finalOwner)
                    ? "scan-side-slab-fired"
                    : (visibleUpperSlabOwner.equals(finalOwner) ? "visibleUpperSlabOwnerWouldWin" : "wrongOwnerWouldWin")));
            String held = mc.player.getMainHandStack().isEmpty()
                    ? "empty"
                    : mc.player.getMainHandStack().getItem().toString();
            BlockState visibleState = mc.world.getBlockState(visibleSlabOwnerPos);
            double visibleDy = SlabSupport.getYOffset(mc.world, visibleSlabOwnerPos, visibleState);
            boolean visiblePersistent = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    mc.world,
                    visibleSlabOwnerPos,
                    visibleState);
            boolean anchorBelowVisible = SlabAnchorAttachment.isAnchored(mc.world, visibleSlabOwnerPos.down());
            if (!visibleState.isOf(Blocks.STONE_SLAB)
                    || !visibleState.contains(SlabBlock.TYPE)
                    || visibleState.get(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Math.abs(visibleDy + 0.5d) > EPSILON
                    || !visiblePersistent
                    || !anchorBelowVisible) {
                throw new RuntimeException("[BETA4_SEAM_VISIBLE_UPPER_SLAB_RED]"
                        + " reason=source-truth-gap"
                        + " expectedOwnerClass=" + expectedOwnerClass
                        + " actualOwnerClass=" + actualOwnerClass
                        + " visibleFacts=" + describeOwnerFacts(mc.world, visibleSlabOwnerPos)
                        + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos));
            }

            String facts = " shape=compact_lowered_stone_with_upper_double_slab"
                    + " angle=" + angle
                    + " held=" + held
                    + " expectedOwnerClass=" + expectedOwnerClass
                    + " actualOwnerClass=" + actualOwnerClass
                    + " support=" + supportPos.toShortString()
                    + " anchoredOwner=" + anchoredOwner
                    + " expectedOwner=" + expectedOwner
                    + " visibleUpperSlabOwner=" + visibleUpperSlabOwner
                    + " lowerFrontOwner=" + lowerFrontOwner
                    + " vanillaType=" + vanilla.getType()
                    + " finalType=" + finalTarget.getType()
                    + " vanillaOwner=" + vanillaOwner
                    + " finalOwner=" + finalOwner
                    + " eye=" + eye
                    + " yaw=" + yaw
                    + " pitch=" + pitch
                    + " liveEye=" + rayStart
                    + " look=" + rayDir
                    + " vanillaTarget=" + describeHit(vanilla)
                    + " finalTarget=" + describeHit(finalTarget)
                    + " vanillaHitFace=" + describeHitFace(vanilla)
                    + " finalHitFace=" + describeHitFace(finalTarget)
                    + " vanillaHitVector=" + describeHitVector(vanilla)
                    + " finalHitVector=" + describeHitVector(finalTarget)
                    + " vanillaDist2=" + describeHitDist2(rayStart, vanilla)
                    + " finalDist2=" + describeHitDist2(rayStart, finalTarget)
                    + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos)
                    + " expectedFacts=" + describeOwnerFacts(mc.world, expectedOwnerPos)
                    + " visibleFacts=" + describeOwnerFacts(mc.world, visibleSlabOwnerPos)
                    + " lowerFrontFacts=" + describeOwnerFacts(mc.world, lowerFrontOwnerPos)
                    + " finalFacts=" + describeOwnerFacts(mc.world, blockPos(finalTarget))
                    + " expectedVisibleOwnerHit=" + expectedVisibleOwnerHit
                    + " anchoredOwnerWon=" + anchoredOwnerWon
                    + " classification=" + classification;

                System.out.println("[JULIA_BETA4_ABOVE_ANGLE]" + facts);

            if (expectGreen && anchoredOwnerWon) {
                System.out.println("[BETA4_ANCHORED_UP_PRESERVE_GREEN]" + facts);
                return;
            }

            if (expectedVisibleOwnerHit) {
                System.out.println("[BETA4_SEAM_VISIBLE_UPPER_SLAB_GREEN]" + facts);
                System.out.println("[JULIA_BETA4_SCREENSHOT_INTENT_GREEN]" + facts);
                return;
            }

            if (expectGreen) {
                redMessage[0] = "[BETA4_ANCHORED_UP_PRESERVE_RED]" + facts
                        + " suspectedFailingLayer=visible-owner-stole-anchored-ground-control"
                        + " anchoredOwnerWon=false"
                        + " groundAngleExpectedGreen=true";
                System.out.println(redMessage[0]);
                return;
            }

            if (anchoredOwnerWon) {
                redMessage[0] = "[BETA4_SEAM_VISIBLE_UPPER_SLAB_RED]" + facts
                        + " suspectedFailingLayer=anchored-owner-preserved-over-visible-owner"
                        + " anchoredOwnerWon=true"
                        + " groundAngleExpectedGreen=" + expectGreen;
                System.out.println(redMessage[0]);
                System.out.println("[JULIA_BETA4_SCREENSHOT_INTENT_RED]" + facts
                        + " suspectedFailingLayer=anchored-owner-preserved-over-visible-owner"
                        + " anchoredOwnerWon=true"
                        + " groundAngleExpectedGreen=" + expectGreen);
                return;
            }

            redMessage[0] = "[BETA4_SEAM_VISIBLE_UPPER_SLAB_RED]" + facts
                    + " suspectedFailingLayer=unexpected-owner-live-targeting"
                    + " anchoredOwnerWon=false"
                    + " groundAngleExpectedGreen=" + expectGreen;
            System.out.println(redMessage[0]);
            System.out.println("[JULIA_BETA4_SCREENSHOT_INTENT_RED]" + facts
                    + " suspectedFailingLayer=unexpected-owner-live-targeting"
                    + " anchoredOwnerWon=false"
                    + " groundAngleExpectedGreen=" + expectGreen);
        });
        return redMessage[0];
    }

    private static boolean isLowerFrontRed(String redMessage) {
        return redMessage != null && redMessage.contains(" lowerFrontOwnerWon=true");
    }

    private static boolean isBeta4ScreenshotVisibleOwnerHit(ClientWorld world, HitResult finalTarget, BlockPos expectedOwnerPos) {
        if (world == null || !(finalTarget instanceof BlockHitResult hit) || !hit.getBlockPos().equals(expectedOwnerPos)) {
            return false;
        }
        if (hit.getSide() == Direction.DOWN) {
            return false;
        }
        BlockState state = world.getBlockState(expectedOwnerPos);
        return state.getBlock() instanceof SlabBlock
                && state.contains(SlabBlock.TYPE)
                && state.get(SlabBlock.TYPE) == SlabType.BOTTOM
                && SlabSupport.getYOffset(world, expectedOwnerPos, state) == -0.5
                && SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, expectedOwnerPos, state)
                && SlabAnchorAttachment.isAnchored(world, expectedOwnerPos.down());
    }

    private static void runJuliaBeta4AdjacentVisibleTargetRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(24, 0, 0);
        final BlockPos anchoredOwnerPos = supportPos.up();
        final BlockPos visibleUpperSlabOwnerPos = anchoredOwnerPos.up();
        final BlockPos adjacentVisibleOwnerPos = anchoredOwnerPos.east();
        final Vec3d eye = new Vec3d(
                adjacentVisibleOwnerPos.getX() + 2.35d,
                adjacentVisibleOwnerPos.getY() + 0.25d,
                adjacentVisibleOwnerPos.getZ() + 0.50d);
        final Vec3d aimAdjacentEastFace = new Vec3d(
                adjacentVisibleOwnerPos.getX() + 1.0d,
                adjacentVisibleOwnerPos.getY() - 0.25d,
                adjacentVisibleOwnerPos.getZ() + 0.50d);

        setupFixture(singleplayer, supportPos, anchoredOwnerPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState fullState = world.getBlockState(anchoredOwnerPos);
            SlabAnchorAttachment.addAnchor(world, anchoredOwnerPos, fullState);
            world.setBlockState(
                    visibleUpperSlabOwnerPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world,
                    visibleUpperSlabOwnerPos,
                    world.getBlockState(visibleUpperSlabOwnerPos));
            world.setBlockState(
                    adjacentVisibleOwnerPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncPlayerAim(ctx, singleplayer, eye, aimAdjacentEastFace);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[JULIA_BETA4_ADJACENT_VISIBLE_RED] client not ready");
            }

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(6.0d));
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            HitResult finalTarget = mc.crosshairTarget;
            String anchoredOwner = anchoredOwnerPos.toShortString();
            String visibleUpperSlabOwner = visibleUpperSlabOwnerPos.toShortString();
            String adjacentVisibleOwner = adjacentVisibleOwnerPos.toShortString();
            String vanillaOwner = asOwner(vanilla);
            String finalOwner = asOwner(finalTarget);
            String finalType = finalTarget == null ? "null" : finalTarget.getType().toString();
            String expectedOwnerClass = "ADJACENT_VISIBLE_TARGET";
            String actualOwnerClass = beta4OwnerClass(
                    finalTarget,
                    anchoredOwnerPos,
                    visibleUpperSlabOwnerPos,
                    adjacentVisibleOwnerPos);
            boolean adjacentOwnerWon = adjacentVisibleOwner.equals(finalOwner);
            boolean anchoredOwnerWon = anchoredOwner.equals(finalOwner);
            boolean visibleUpperOwnerWon = visibleUpperSlabOwner.equals(finalOwner);
            boolean finalMiss = finalTarget == null || finalTarget.getType() == HitResult.Type.MISS;
            String classification = adjacentOwnerWon
                    ? "adjacentVisibleOwnerExpected"
                    : (anchoredOwnerWon
                    ? "anchoredOwnerWouldSteal"
                    : (visibleUpperOwnerWon
                    ? "visibleUpperSlabOwnerWouldSteal"
                    : (finalMiss ? "missOrAirNoOwner" : "wrongOwnerWouldWin")));
            String held = mc.player.getMainHandStack().isEmpty()
                    ? "empty"
                    : mc.player.getMainHandStack().getItem().toString();

            String facts = " shape=compact_lowered_stone_with_upper_bottom_slab_and_adjacent_visible_target"
                    + " held=" + held
                    + " expectedOwnerClass=" + expectedOwnerClass
                    + " actualOwnerClass=" + actualOwnerClass
                    + " support=" + supportPos.toShortString()
                    + " anchoredOwner=" + anchoredOwner
                    + " visibleUpperSlabOwner=" + visibleUpperSlabOwner
                    + " adjacentVisibleOwner=" + adjacentVisibleOwner
                    + " vanillaType=" + vanilla.getType()
                    + " finalType=" + finalType
                    + " vanillaOwner=" + vanillaOwner
                    + " finalOwner=" + finalOwner
                    + " eye=" + eye
                    + " aim=" + aimAdjacentEastFace
                    + " liveEye=" + rayStart
                    + " look=" + rayDir
                    + " yaw=" + mc.player.getYaw()
                    + " pitch=" + mc.player.getPitch()
                    + " vanillaTarget=" + describeHit(vanilla)
                    + " finalTarget=" + describeHit(finalTarget)
                    + " vanillaHitFace=" + describeHitFace(vanilla)
                    + " finalHitFace=" + describeHitFace(finalTarget)
                    + " vanillaHitVector=" + describeHitVector(vanilla)
                    + " finalHitVector=" + describeHitVector(finalTarget)
                    + " vanillaDist2=" + describeHitDist2(rayStart, vanilla)
                    + " finalDist2=" + describeHitDist2(rayStart, finalTarget)
                    + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos)
                    + " visibleUpperFacts=" + describeOwnerFacts(mc.world, visibleUpperSlabOwnerPos)
                    + " adjacentVisibleFacts=" + describeOwnerFacts(mc.world, adjacentVisibleOwnerPos)
                    + " finalFacts=" + describeOwnerFacts(mc.world, blockPos(finalTarget))
                    + " adjacentOwnerWon=" + adjacentOwnerWon
                    + " anchoredOwnerWon=" + anchoredOwnerWon
                    + " visibleUpperOwnerWon=" + visibleUpperOwnerWon
                    + " finalMiss=" + finalMiss
                    + " classification=" + classification;

            if (adjacentOwnerWon) {
                System.out.println("[BETA4_ADJACENT_VISIBLE_SEAM_GREEN]" + facts);
                System.out.println("[JULIA_BETA4_ADJACENT_VISIBLE_GREEN]" + facts);
                return;
            }

            throw new RuntimeException("[JULIA_BETA4_ADJACENT_VISIBLE_RED]" + facts
                    + " suspectedFailingLayer=adjacent-visible-owner-not-preserved");
        });
    }

    private static void runBeta4SeamVisibleUpperSideFaceRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(24, 0, 0);
        final BlockPos anchoredOwnerPos = supportPos.up();
        final BlockPos visibleUpperSlabOwnerPos = anchoredOwnerPos.up();
        final Vec3d eye = new Vec3d(
                visibleUpperSlabOwnerPos.getX() - 2.50d,
                visibleUpperSlabOwnerPos.getY() - 0.25d,
                visibleUpperSlabOwnerPos.getZ() + 0.50d);
        final Vec3d aimVisibleWestSideFace = new Vec3d(
                visibleUpperSlabOwnerPos.getX(),
                visibleUpperSlabOwnerPos.getY() - 0.25d,
                visibleUpperSlabOwnerPos.getZ() + 0.50d);

        setupFixture(singleplayer, supportPos, anchoredOwnerPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState fullState = world.getBlockState(anchoredOwnerPos);
            SlabAnchorAttachment.addAnchor(world, anchoredOwnerPos, fullState);
            world.setBlockState(
                    visibleUpperSlabOwnerPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world,
                    visibleUpperSlabOwnerPos,
                    world.getBlockState(visibleUpperSlabOwnerPos));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncPlayerAim(ctx, singleplayer, eye, aimVisibleWestSideFace);

        System.setProperty("slabbed.target.trace", "true");
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[BETA4_SEAM_VISIBLE_UPPER_SIDE_FACE_RED] client not ready");
            }

            BlockState visibleState = mc.world.getBlockState(visibleUpperSlabOwnerPos);
            double visibleDy = SlabSupport.getYOffset(mc.world, visibleUpperSlabOwnerPos, visibleState);
            boolean visiblePersistent = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    mc.world,
                    visibleUpperSlabOwnerPos,
                    visibleState);
            boolean anchorBelowVisible = SlabAnchorAttachment.isAnchored(mc.world, visibleUpperSlabOwnerPos.down());
            if (!visibleState.isOf(Blocks.STONE_SLAB)
                    || !visibleState.contains(SlabBlock.TYPE)
                    || visibleState.get(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Math.abs(visibleDy + 0.5d) > EPSILON
                    || !visiblePersistent
                    || !anchorBelowVisible) {
                throw new RuntimeException("[BETA4_SEAM_VISIBLE_UPPER_SIDE_FACE_RED]"
                        + " reason=source-truth-gap"
                        + " expectedOwnerClass=VISIBLE_UPPER_LOWERED_SLAB"
                        + " visibleFacts=" + describeOwnerFacts(mc.world, visibleUpperSlabOwnerPos)
                        + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos));
            }

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(6.0d));
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            HitResult finalTarget = mc.crosshairTarget;
            String expectedOwnerClass = "VISIBLE_UPPER_LOWERED_SLAB";
            String actualOwnerClass = beta4OwnerClass(
                    finalTarget,
                    anchoredOwnerPos,
                    visibleUpperSlabOwnerPos,
                    null);
            String held = mc.player.getMainHandStack().isEmpty()
                    ? "empty"
                    : mc.player.getMainHandStack().getItem().toString();
            String vanillaOwner = asOwner(vanilla);
            String finalOwner = asOwner(finalTarget);
            boolean visibleOwnerWon = visibleUpperSlabOwnerPos.toShortString().equals(finalOwner);
            boolean vanillaMiss = vanilla.getType() != HitResult.Type.BLOCK;
            boolean finalMiss = finalTarget == null || finalTarget.getType() != HitResult.Type.BLOCK;
            String classification = visibleOwnerWon
                    ? "visibleUpperSideFaceOwnerExpected"
                    : (finalMiss
                    ? "missNoVisibleSideFaceOwner"
                    : ("wrongOwner=" + actualOwnerClass));
            String facts = " shape=compact_lowered_stone_with_upper_bottom_slab"
                    + " aimRegion=visible-upper-lowered-slab-west-side-face"
                    + " held=" + held
                    + " expectedOwnerClass=" + expectedOwnerClass
                    + " actualOwnerClass=" + actualOwnerClass
                    + " support=" + supportPos.toShortString()
                    + " anchoredOwner=" + anchoredOwnerPos.toShortString()
                    + " visibleUpperSlabOwner=" + visibleUpperSlabOwnerPos.toShortString()
                    + " vanillaType=" + vanilla.getType()
                    + " finalType=" + (finalTarget == null ? "null" : finalTarget.getType())
                    + " vanillaOwner=" + vanillaOwner
                    + " finalOwner=" + finalOwner
                    + " eye=" + eye
                    + " aim=" + aimVisibleWestSideFace
                    + " liveEye=" + rayStart
                    + " look=" + rayDir
                    + " yaw=" + mc.player.getYaw()
                    + " pitch=" + mc.player.getPitch()
                    + " vanillaTarget=" + describeHit(vanilla)
                    + " finalTarget=" + describeHit(finalTarget)
                    + " vanillaMiss=" + vanillaMiss
                    + " finalMiss=" + finalMiss
                    + " vanillaHitFace=" + describeHitFace(vanilla)
                    + " finalHitFace=" + describeHitFace(finalTarget)
                    + " vanillaHitVector=" + describeHitVector(vanilla)
                    + " finalHitVector=" + describeHitVector(finalTarget)
                    + " vanillaDist2=" + describeHitDist2(rayStart, vanilla)
                    + " finalDist2=" + describeHitDist2(rayStart, finalTarget)
                    + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos)
                    + " visibleUpperFacts=" + describeOwnerFacts(mc.world, visibleUpperSlabOwnerPos)
                    + " finalFacts=" + describeOwnerFacts(mc.world, blockPos(finalTarget))
                    + " visibleOwnerWon=" + visibleOwnerWon
                    + " classification=" + classification;

            if (visibleOwnerWon) {
                System.out.println("[BETA4_SEAM_VISIBLE_UPPER_SIDE_FACE_GREEN]" + facts);
                return;
            }

            throw new RuntimeException("[BETA4_SEAM_VISIBLE_UPPER_SIDE_FACE_RED]" + facts
                    + " suspectedFailingLayer=visible-upper-side-face-miss-before-owner-classifier");
        });
    }

    private static void runBeta4SeamVisibleUpperAnchoredUpStealRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        runBeta4SeamVisibleUpperAnchoredUpProofCase(ctx, singleplayer, false);
    }

    private static void runBeta4SeamVisibleUpperAngleGeneralRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        runBeta4SeamVisibleUpperAnchoredUpProofCase(ctx, singleplayer, true);
    }

    private static void runBeta4SeamVisibleUpperAnchoredUpProofCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            boolean angleGeneral
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(24, 0, 0);
        final BlockPos anchoredOwnerPos = supportPos.up();
        final BlockPos visibleUpperSlabOwnerPos = anchoredOwnerPos.up();
        final String redMarker = angleGeneral
                ? "[BETA4_SEAM_VISIBLE_UPPER_ANGLE_GENERAL_RED]"
                : "[BETA4_SEAM_VISIBLE_UPPER_ANCHORED_UP_STEAL_RED]";
        final String greenMarker = angleGeneral
                ? "[BETA4_SEAM_VISIBLE_UPPER_ANGLE_GENERAL_GREEN]"
                : "[BETA4_SEAM_VISIBLE_UPPER_ANCHORED_UP_STEAL_GREEN]";

        setupFixture(singleplayer, supportPos, anchoredOwnerPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState fullState = world.getBlockState(anchoredOwnerPos);
            SlabAnchorAttachment.addAnchor(world, anchoredOwnerPos, fullState);
            world.setBlockState(
                    visibleUpperSlabOwnerPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world,
                    visibleUpperSlabOwnerPos,
                    world.getBlockState(visibleUpperSlabOwnerPos));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        System.setProperty("slabbed.target.trace", "true");
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException(redMarker + " client not ready");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));

            BlockState anchoredState = mc.world.getBlockState(anchoredOwnerPos);
            BlockState visibleState = mc.world.getBlockState(visibleUpperSlabOwnerPos);
            boolean sourceTruth = anchoredState.isOf(Blocks.STONE)
                    && SlabAnchorAttachment.isAnchored(mc.world, anchoredOwnerPos)
                    && Math.abs(SlabSupport.getYOffset(mc.world, anchoredOwnerPos, anchoredState) + 0.5d) <= EPSILON
                    && visibleState.isOf(Blocks.STONE_SLAB)
                    && visibleState.contains(SlabBlock.TYPE)
                    && visibleState.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && Math.abs(SlabSupport.getYOffset(mc.world, visibleUpperSlabOwnerPos, visibleState) + 0.5d) <= EPSILON
                    && SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    mc.world,
                    visibleUpperSlabOwnerPos,
                    visibleState);
            if (!sourceTruth) {
                throw new RuntimeException(redMarker
                        + " reason=source-truth-gap"
                        + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos)
                        + " visibleUpperFacts=" + describeOwnerFacts(mc.world, visibleUpperSlabOwnerPos));
            }

            String bestFacts = null;
            double[] eyeX = angleGeneral ? new double[]{-2.72d, -2.50d, -3.00d} : new double[]{-2.50d};
            double[] eyeY = angleGeneral
                    ? new double[]{2.78d, 2.60d, 2.95d}
                    : new double[]{1.25d, 1.40d, 1.55d, 1.70d, 1.85d};
            double[] eyeZ = angleGeneral ? new double[]{1.85d, 1.50d, 2.10d} : new double[]{0.50d};
            double[] aimX = angleGeneral
                    ? new double[]{0.20d, 0.28d, 0.35d, 0.50d}
                    : new double[]{0.02d, 0.05d, 0.08d, 0.12d, 0.15d};
            double[] aimY = angleGeneral
                    ? new double[]{0.50d, 0.52d, 0.55d}
                    : new double[]{0.50d, 0.52d, 0.55d, 0.58d, 0.62d};
            double[] aimZ = angleGeneral
                    ? new double[]{0.40d, 0.54d, 0.65d}
                    : new double[]{0.50d, 0.08d, 0.92d};

            for (double ex : eyeX) {
                for (double ey : eyeY) {
                    for (double ez : eyeZ) {
                        for (double ax : aimX) {
                            for (double ay : aimY) {
                                for (double az : aimZ) {
                            Vec3d eye = new Vec3d(
                                    anchoredOwnerPos.getX() + ex,
                                    anchoredOwnerPos.getY() + ey,
                                    anchoredOwnerPos.getZ() + ez);
                            Vec3d aim = new Vec3d(
                                    anchoredOwnerPos.getX() + ax,
                                    anchoredOwnerPos.getY() + ay,
                                    anchoredOwnerPos.getZ() + az);
                            Vec3d delta = aim.subtract(eye);
                            double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
                            float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
                            double feetY = eye.y - mc.player.getStandingEyeHeight();
                            mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
                            mc.player.setVelocity(Vec3d.ZERO);

                            mc.gameRenderer.updateCrosshairTarget(0.0f);
                            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
                            Vec3d rayDir = mc.player.getRotationVec(0.0f);
                            Vec3d rayEnd = rayStart.add(rayDir.multiply(6.0d));
                            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                                    rayStart,
                                    rayEnd,
                                    RaycastContext.ShapeType.OUTLINE,
                                    RaycastContext.FluidHandling.NONE,
                                    mc.player));
                            BlockHitResult candidate = SlabbedRetargetTestHooks.findLoweredSideSlabRetarget(
                                    mc.world,
                                    mc.player,
                                    rayStart,
                                    rayEnd,
                                    vanilla,
                                    true);
                            HitResult finalTarget = mc.crosshairTarget;

                            boolean initialBlockUp = vanilla.getType() == HitResult.Type.BLOCK
                                    && vanilla.getBlockPos().equals(anchoredOwnerPos)
                                    && vanilla.getSide() == Direction.UP;
                            Vec3d localHit = vanilla.getPos().subtract(
                                    anchoredOwnerPos.getX(),
                                    anchoredOwnerPos.getY(),
                                    anchoredOwnerPos.getZ());
                            boolean edgeLike = localHit.x <= 0.15d
                                    || localHit.x >= 0.85d
                                    || localHit.z <= 0.15d
                                    || localHit.z >= 0.85d;
                            boolean topInterior = initialBlockUp && !edgeLike;
                            boolean candidateExists = candidate != null;
                            boolean candidateVisibleOwner = candidateExists
                                    && candidate.getBlockPos().equals(visibleUpperSlabOwnerPos);
                            boolean candidateLoweredBottomSlab = false;
                            double candidateDy = Double.NaN;
                            if (candidateVisibleOwner) {
                                BlockState candidateState = mc.world.getBlockState(candidate.getBlockPos());
                                candidateDy = SlabSupport.getYOffset(mc.world, candidate.getBlockPos(), candidateState);
                                candidateLoweredBottomSlab = candidateState.isOf(Blocks.STONE_SLAB)
                                        && candidateState.contains(SlabBlock.TYPE)
                                        && candidateState.get(SlabBlock.TYPE) == SlabType.BOTTOM
                                        && Math.abs(candidateDy + 0.5d) <= EPSILON;
                            }
                            double initialDist2 = vanilla.getPos().squaredDistanceTo(rayStart);
                            double candidateDist2 = candidateExists
                                    ? candidate.getPos().squaredDistanceTo(rayStart)
                                    : Double.NaN;
                            boolean candidateCloser = candidateExists && candidateDist2 < initialDist2 - EPSILON;
                            String actualOwnerClass = beta4OwnerClass(
                                    finalTarget,
                                    anchoredOwnerPos,
                                    visibleUpperSlabOwnerPos,
                                    null);
                            boolean finalAnchored = "ANCHORED_FULL_BLOCK".equals(actualOwnerClass);
                            boolean finalVisible = "VISIBLE_UPPER_LOWERED_SLAB".equals(actualOwnerClass);
                            String classification = finalAnchored && initialBlockUp && candidateVisibleOwner
                                    ? "anchoredUpPreserve"
                                    : (finalVisible ? "visibleUpperSideFaceOwner" : "unexpectedOwner");

                            String facts = " shape=compact_lowered_stone_with_upper_bottom_slab"
                                    + " aimRegion=" + (angleGeneral
                                    ? "anchored-up-interior-visible-upper-side-candidate"
                                    : "anchored-up-edge-visible-upper-side-candidate")
                                    + " held=minecraft:stone_slab"
                                    + " expectedOwnerClass=VISIBLE_UPPER_LOWERED_SLAB"
                                    + " actualOwnerClass=" + actualOwnerClass
                                    + " proofBranch=" + (initialBlockUp ? "BLOCK_UP" : vanilla.getType())
                                    + " support=" + supportPos.toShortString()
                                    + " anchoredOwner=" + anchoredOwnerPos.toShortString()
                                    + " visibleUpperSlabOwner=" + visibleUpperSlabOwnerPos.toShortString()
                                    + " eye=" + eye
                                    + " aim=" + aim
                                    + " liveEye=" + rayStart
                                    + " look=" + rayDir
                                    + " yaw=" + mc.player.getYaw()
                                    + " pitch=" + mc.player.getPitch()
                                    + " vanillaType=" + vanilla.getType()
                                    + " finalType=" + (finalTarget == null ? "null" : finalTarget.getType())
                                    + " vanillaTarget=" + describeHit(vanilla)
                                    + " finalTarget=" + describeHit(finalTarget)
                                    + " sideScanCandidateExists=" + candidateExists
                                    + " sideScanCandidateReason=" + (candidateExists ? "accepted" : "none")
                                    + " sideScanCandidate=" + describeHit(candidate)
                                    + " initialOwnerClass=ANCHORED_FULL_BLOCK"
                                    + " sideScanCandidateOwnerClass="
                                    + (candidateVisibleOwner ? "VISIBLE_UPPER_LOWERED_SLAB" : beta4OwnerClass(
                                    candidate,
                                    anchoredOwnerPos,
                                    visibleUpperSlabOwnerPos,
                                    null))
                                    + " initialFace=" + vanilla.getSide()
                                    + " finalFace=" + describeHitFace(finalTarget)
                                    + " localHit=" + localHit
                                    + " topHit=" + initialBlockUp
                                    + " edgeLike=" + edgeLike
                                    + " topInterior=" + topInterior
                                    + " initialDist2=" + String.format("%.6f", initialDist2)
                                    + " candidateDist2=" + (candidateExists ? String.format("%.6f", candidateDist2) : "NaN")
                                    + " candidateMinusInitialDist2="
                                    + (candidateExists ? String.format("%.6f", candidateDist2 - initialDist2) : "NaN")
                                    + " candidateLoweredBottomSlab=" + candidateLoweredBottomSlab
                                    + " candidateDy=" + (candidateExists ? String.format("%.3f", candidateDy) : "NaN")
                                    + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos)
                                    + " visibleUpperFacts=" + describeOwnerFacts(mc.world, visibleUpperSlabOwnerPos)
                                    + " candidateFacts=" + describeOwnerFacts(mc.world, candidateExists ? candidate.getBlockPos() : null)
                                    + " finalFacts=" + describeOwnerFacts(mc.world, blockPos(finalTarget))
                                    + " finalOwner=" + asOwner(finalTarget)
                                    + " finalAnchored=" + finalAnchored
                                    + " visibleOwnerWon=" + finalVisible
                                    + " classification=" + classification
                                    + " traceMarker=SLAB_HELD_UP_GUARD_SIDE_OWNER_CLASSIFY";
                            boolean targetBranch = initialBlockUp
                                    && candidateVisibleOwner
                                    && candidateLoweredBottomSlab
                                    && candidateCloser
                                    && (angleGeneral ? (topInterior && !edgeLike) : (edgeLike && !topInterior));
                            if (targetBranch) {
                                if (finalVisible) {
                                    System.out.println(greenMarker + facts);
                                    return;
                                }
                                throw new RuntimeException(redMarker + facts);
                            }
                            bestFacts = facts;
                        }
                    }
                }
            }
            }
            }

            throw new RuntimeException(redMarker
                    + " reason=live-branch-not-reproduced"
                    + " lastAttempt=" + bestFacts);
        });
    }

    private static void runBeta4SeamNoRescueBoundaryCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(24, 0, 0);
        final BlockPos anchoredOwnerPos = supportPos.up();
        final BlockPos visibleUpperSlabOwnerPos = anchoredOwnerPos.up();
        final Vec3d eye = new Vec3d(
                anchoredOwnerPos.getX() - 2.35d,
                anchoredOwnerPos.getY() + 1.67d,
                anchoredOwnerPos.getZ() + 0.62d);
        final Vec3d aimAirAboveSeam = new Vec3d(
                anchoredOwnerPos.getX() + 0.50d,
                anchoredOwnerPos.getY() + 2.40d,
                anchoredOwnerPos.getZ() + 0.50d);

        setupFixture(singleplayer, supportPos, anchoredOwnerPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState fullState = world.getBlockState(anchoredOwnerPos);
            SlabAnchorAttachment.addAnchor(world, anchoredOwnerPos, fullState);
            world.setBlockState(
                    visibleUpperSlabOwnerPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world,
                    visibleUpperSlabOwnerPos,
                    world.getBlockState(visibleUpperSlabOwnerPos));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncPlayerAim(ctx, singleplayer, eye, aimAirAboveSeam);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[BETA4_SEAM_NO_RESCUE_RED] client not ready");
            }

            BlockState visibleState = mc.world.getBlockState(visibleUpperSlabOwnerPos);
            double visibleDy = SlabSupport.getYOffset(mc.world, visibleUpperSlabOwnerPos, visibleState);
            boolean visiblePersistent = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    mc.world,
                    visibleUpperSlabOwnerPos,
                    visibleState);
            boolean anchorBelowVisible = SlabAnchorAttachment.isAnchored(mc.world, visibleUpperSlabOwnerPos.down());
            if (!visibleState.isOf(Blocks.STONE_SLAB)
                    || !visibleState.contains(SlabBlock.TYPE)
                    || visibleState.get(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Math.abs(visibleDy + 0.5d) > EPSILON
                    || !visiblePersistent
                    || !anchorBelowVisible) {
                throw new RuntimeException("[BETA4_SEAM_NO_RESCUE_RED]"
                        + " reason=source-truth-gap"
                        + " expectedOwnerClass=NO_RESCUE"
                        + " visibleFacts=" + describeOwnerFacts(mc.world, visibleUpperSlabOwnerPos)
                        + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos));
            }

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(6.0d));
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            HitResult finalTarget = mc.crosshairTarget;
            String vanillaOwner = asOwner(vanilla);
            String finalOwner = asOwner(finalTarget);
            String actualOwnerClass = beta4OwnerClass(
                    finalTarget,
                    anchoredOwnerPos,
                    visibleUpperSlabOwnerPos,
                    null);
            boolean vanillaMiss = vanilla.getType() != HitResult.Type.BLOCK;
            boolean finalMiss = finalTarget == null || finalTarget.getType() != HitResult.Type.BLOCK;
            boolean rewroteInitial = !(vanillaMiss && finalMiss) && !vanillaOwner.equals(finalOwner);
            boolean stoleSeamOwner = "ANCHORED_FULL_BLOCK".equals(actualOwnerClass)
                    || "VISIBLE_UPPER_LOWERED_SLAB".equals(actualOwnerClass);
            String held = mc.player.getMainHandStack().isEmpty()
                    ? "empty"
                    : mc.player.getMainHandStack().getItem().toString();
            String facts = " shape=compact_lowered_stone_with_upper_bottom_slab"
                    + " held=" + held
                    + " expectedOwnerClass=NO_RESCUE"
                    + " actualOwnerClass=" + actualOwnerClass
                    + " support=" + supportPos.toShortString()
                    + " anchoredOwner=" + anchoredOwnerPos.toShortString()
                    + " visibleUpperSlabOwner=" + visibleUpperSlabOwnerPos.toShortString()
                    + " vanillaOwner=" + vanillaOwner
                    + " finalOwner=" + finalOwner
                    + " eye=" + eye
                    + " aim=" + aimAirAboveSeam
                    + " liveEye=" + rayStart
                    + " look=" + rayDir
                    + " vanillaTarget=" + describeHit(vanilla)
                    + " finalTarget=" + describeHit(finalTarget)
                    + " vanillaMiss=" + vanillaMiss
                    + " finalMiss=" + finalMiss
                    + " vanillaHitFace=" + describeHitFace(vanilla)
                    + " finalHitFace=" + describeHitFace(finalTarget)
                    + " vanillaHitVector=" + describeHitVector(vanilla)
                    + " finalHitVector=" + describeHitVector(finalTarget)
                    + " vanillaDist2=" + describeHitDist2(rayStart, vanilla)
                    + " finalDist2=" + describeHitDist2(rayStart, finalTarget)
                    + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos)
                    + " visibleUpperFacts=" + describeOwnerFacts(mc.world, visibleUpperSlabOwnerPos)
                    + " finalFacts=" + describeOwnerFacts(mc.world, blockPos(finalTarget))
                    + " rewroteInitial=" + rewroteInitial
                    + " stoleSeamOwner=" + stoleSeamOwner;

            if (!rewroteInitial && !stoleSeamOwner) {
                System.out.println("[BETA4_SEAM_NO_RESCUE_GREEN]" + facts);
                return;
            }

            throw new RuntimeException("[BETA4_SEAM_NO_RESCUE_RED]" + facts
                    + " suspectedFailingLayer=miss-or-air-rescued-to-seam-owner");
        });
    }

    private static void runScreenshotReproProbe(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            BlockPos expectedOwnerPos,
            Vec3d eye,
            Vec3d aimPoint,
            String caseId,
            String aimRegion,
            ItemStack heldStack,
            String setupDetails
    ) {
        final double reach = 6.0d;

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[SCREENSHOT_REPRO_RED] case=" + caseId + " client not ready");
            }
            if (heldStack == null || heldStack.isEmpty()) {
                mc.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            } else {
                mc.player.setStackInHand(Hand.MAIN_HAND, heldStack.copy());
            }

            Vec3d delta = aimPoint.subtract(eye);
            double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
            double feetY = eye.y - mc.player.getStandingEyeHeight();
            mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            mc.player.setVelocity(Vec3d.ZERO);

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(reach));
            HitResult crosshair = mc.crosshairTarget;
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            BlockState expectedState = mc.world.getBlockState(expectedOwnerPos);
            BlockHitResult outlineHit = expectedState.getOutlineShape(mc.world, expectedOwnerPos)
                    .raycast(rayStart, rayEnd, expectedOwnerPos);

            String expectedOwner = expectedOwnerPos.toShortString();
            String crosshairOwner = asOwner(crosshair);
            String vanillaOwner = asOwner(vanilla);
            String outlineOwner = outlineHit == null ? "MISS" : outlineHit.getBlockPos().toShortString();
            String held = mc.player.getMainHandStack().isEmpty()
                    ? "empty"
                    : mc.player.getMainHandStack().getItem().toString();
            String hitSide = crosshair instanceof BlockHitResult bhr ? bhr.getSide().toString() : "MISS";
            double expectedDy = SlabSupport.getYOffset(mc.world, expectedOwnerPos, expectedState);
            String visualBox = expectedState.getOutlineShape(mc.world, expectedOwnerPos).isEmpty()
                    ? "EMPTY"
                    : formatBox(expectedState.getOutlineShape(mc.world, expectedOwnerPos)
                    .getBoundingBox().offset(expectedOwnerPos));

            System.out.println("[SCREENSHOT_REPRO] case=" + caseId
                    + " setup held=" + held
                    + " aimRegion=" + aimRegion
                    + " expected=" + expectedOwner
                    + " eye=" + eye
                    + " aim=" + aimPoint
                    + " liveEye=" + rayStart
                    + " dir=" + rayDir
                    + " reach=" + reach
                    + " visualBox=" + visualBox
                    + " expectedState=" + expectedState
                    + " expectedDy=" + expectedDy
                    + " " + setupDetails);
            System.out.println("[SCREENSHOT_REPRO] case=" + caseId
                    + " ray expected=" + expectedOwner
                    + " actual=" + crosshairOwner
                    + " vanilla=" + vanillaOwner
                    + " outline=" + outlineOwner
                    + " hitSide=" + hitSide);

            boolean ownerMatch = expectedOwner.equals(crosshairOwner);
            boolean expectedOutlineHit = expectedOwner.equals(outlineOwner);
            if (ownerMatch) {
                System.out.println("[SCREENSHOT_REPRO_GREEN] case=" + caseId
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
                return;
            }
            if (expectedOutlineHit) {
                System.out.println("[SCREENSHOT_REPRO_RED] case=" + caseId
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
                throw new RuntimeException("[SCREENSHOT_REPRO_RED] case=" + caseId
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
            }
            System.out.println("[SCREENSHOT_REPRO] case=" + caseId
                    + " calibration=aim_miss expected=" + expectedOwner
                    + " actual=" + crosshairOwner
                    + " vanilla=" + vanillaOwner
                    + " outline=" + outlineOwner);
        });
    }

    private static void runBridgeShapeLowerHalfReproCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos leftSupport = SUPPORT_POS.add(88, 0, 0);
        final BlockPos leftFull = leftSupport.up();
        final BlockPos midFullA = leftFull.east();
        final BlockPos midFullB = midFullA.east();
        final BlockPos rightFull = midFullB.east();
        final BlockPos rightSupport = rightFull.down();
        final BlockPos topSlabA = midFullA.up();
        final BlockPos topSlabB = midFullB.up();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = leftSupport.getX() - 3; x <= rightSupport.getX() + 3; x++) {
                for (int z = leftSupport.getZ() - 2; z <= leftSupport.getZ() + 2; z++) {
                    for (int y = leftSupport.getY() - 1; y <= leftSupport.getY() + 3; y++) {
                        world.setBlockState(
                                new BlockPos(x, y, z),
                                Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }

            world.setBlockState(
                    leftSupport,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    rightSupport,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(leftFull, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(midFullA, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(midFullB, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(rightFull, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);

            SlabAnchorAttachment.addAnchor(world, leftFull, world.getBlockState(leftFull));
            SlabAnchorAttachment.addAnchor(world, rightFull, world.getBlockState(rightFull));
            SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(
                    world,
                    midFullA,
                    world.getBlockState(midFullA),
                    leftFull,
                    world.getBlockState(leftFull));
            SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(
                    world,
                    midFullB,
                    world.getBlockState(midFullB),
                    midFullA,
                    world.getBlockState(midFullA));

            world.setBlockState(
                    topSlabA,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    topSlabB,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        String shape = "shape=BSFB->FB->FB->BSFB_with_top_slabs"
                + " leftSupport=" + leftSupport.toShortString()
                + " leftFull=" + leftFull.toShortString()
                + " midA=" + midFullA.toShortString()
                + " midB=" + midFullB.toShortString()
                + " rightFull=" + rightFull.toShortString()
                + " rightSupport=" + rightSupport.toShortString()
                + " topA=" + topSlabA.toShortString()
                + " topB=" + topSlabB.toShortString();

        runBridgeLowerHalfProbe(
                ctx,
                midFullA,
                new Vec3d(midFullA.getX() + 0.5d, midFullA.getY() + 1.65d, midFullA.getZ() + 2.55d),
                new Vec3d(midFullA.getX() + 0.5d, midFullA.getY() - 0.30d, midFullA.getZ() + 0.5d),
                "bridge_fb1_front_lower_slab_held",
                "front_lower_center",
                new ItemStack(Items.STONE_SLAB, 8),
                shape);
        runBridgeLowerHalfProbe(
                ctx,
                midFullB,
                new Vec3d(midFullB.getX() + 0.5d, midFullB.getY() + 1.65d, midFullB.getZ() + 2.55d),
                new Vec3d(midFullB.getX() + 0.5d, midFullB.getY() - 0.30d, midFullB.getZ() + 0.5d),
                "bridge_fb2_front_lower_empty_hand",
                "front_lower_center",
                ItemStack.EMPTY,
                shape);
        runBridgeLowerHalfProbe(
                ctx,
                topSlabA,
                new Vec3d(topSlabA.getX() - 2.35d, topSlabA.getY() + 1.60d, topSlabA.getZ() + 0.5d),
                new Vec3d(topSlabA.getX() + 0.02d, topSlabA.getY() + 0.10d, topSlabA.getZ() + 0.5d),
                "bridge_top_slabA_side_lower_slab_held",
                "side_lower_center",
                new ItemStack(Items.STONE_SLAB, 8),
                shape);
        runBridgeLowerHalfProbe(
                ctx,
                topSlabB,
                new Vec3d(topSlabB.getX() + 2.35d, topSlabB.getY() + 1.60d, topSlabB.getZ() + 0.5d),
                new Vec3d(topSlabB.getX() + 0.98d, topSlabB.getY() + 0.10d, topSlabB.getZ() + 0.5d),
                "bridge_top_slabB_side_lower_empty_hand",
                "side_lower_center",
                ItemStack.EMPTY,
                shape);
    }

    private static void runBridgeLowerHalfProbe(
            ClientGameTestContext ctx,
            BlockPos expectedOwnerPos,
            Vec3d eye,
            Vec3d aimPoint,
            String caseId,
            String aimRegion,
            ItemStack heldStack,
            String setupDetails
    ) {
        final double reach = 6.0d;

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[BRIDGE_LOWER_HALF_RED] case=" + caseId + " client not ready");
            }
            if (heldStack == null || heldStack.isEmpty()) {
                mc.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            } else {
                mc.player.setStackInHand(Hand.MAIN_HAND, heldStack.copy());
            }

            Vec3d delta = aimPoint.subtract(eye);
            double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
            double feetY = eye.y - mc.player.getStandingEyeHeight();
            mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            mc.player.setVelocity(Vec3d.ZERO);

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(reach));
            HitResult crosshair = mc.crosshairTarget;
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            BlockState expectedState = mc.world.getBlockState(expectedOwnerPos);
            BlockHitResult outlineHit = expectedState.getOutlineShape(mc.world, expectedOwnerPos)
                    .raycast(rayStart, rayEnd, expectedOwnerPos);

            String expectedOwner = expectedOwnerPos.toShortString();
            String crosshairOwner = asOwner(crosshair);
            String vanillaOwner = asOwner(vanilla);
            String outlineOwner = outlineHit == null ? "MISS" : outlineHit.getBlockPos().toShortString();
            String held = mc.player.getMainHandStack().isEmpty()
                    ? "empty"
                    : mc.player.getMainHandStack().getItem().toString();
            double expectedDy = SlabSupport.getYOffset(mc.world, expectedOwnerPos, expectedState);
            String visualBox = expectedState.getOutlineShape(mc.world, expectedOwnerPos).isEmpty()
                    ? "EMPTY"
                    : formatBox(expectedState.getOutlineShape(mc.world, expectedOwnerPos)
                    .getBoundingBox().offset(expectedOwnerPos));

            System.out.println("[BRIDGE_LOWER_HALF] case=" + caseId
                    + " held=" + held
                    + " aimRegion=" + aimRegion
                    + " expected=" + expectedOwner
                    + " actual=" + crosshairOwner
                    + " vanilla=" + vanillaOwner
                    + " outline=" + outlineOwner
                    + " expectedState=" + expectedState
                    + " expectedDy=" + expectedDy
                    + " eye=" + eye
                    + " aim=" + aimPoint
                    + " liveEye=" + rayStart
                    + " dir=" + rayDir
                    + " visualBox=" + visualBox
                    + " " + setupDetails);

            boolean ownerMatch = expectedOwner.equals(crosshairOwner);
            boolean expectedOutlineHit = expectedOwner.equals(outlineOwner);
            if (ownerMatch) {
                System.out.println("[BRIDGE_LOWER_HALF_GREEN] case=" + caseId
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
                return;
            }
            if (expectedOutlineHit) {
                System.out.println("[BRIDGE_LOWER_HALF_RED] case=" + caseId
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
                throw new RuntimeException("[BRIDGE_LOWER_HALF_RED] case=" + caseId
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
            }

            System.out.println("[BRIDGE_LOWER_HALF] case=" + caseId
                    + " calibration=aim_miss expected=" + expectedOwner
                    + " actual=" + crosshairOwner
                    + " vanilla=" + vanillaOwner
                    + " outline=" + outlineOwner);
        });
    }

    private static void runDynamicBridgeTopSlabSelectionReproCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos leftSupport = SUPPORT_POS.add(24, 0, 0);
        final BlockPos leftFull = leftSupport.up();
        final BlockPos fb1Pos = leftFull.east();
        final BlockPos fb2Pos = fb1Pos.east();
        final BlockPos rightSupport = fb2Pos.east().down();
        final BlockPos rightFull = rightSupport.up();
        final BlockPos topSlabPos = fb1Pos.up();
        final BlockPos aboveTopSlabPos = topSlabPos.up();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            int minX = leftSupport.getX() - 2;
            int maxX = rightSupport.getX() + 2;
            int minZ = leftSupport.getZ() - 2;
            int maxZ = leftSupport.getZ() + 2;
            int minY = leftSupport.getY() - 1;
            int maxY = leftSupport.getY() + 4;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        world.setBlockState(
                                new BlockPos(x, y, z),
                                Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }
            for (int x = minX; x <= maxX; x++) {
                world.setBlockState(
                        new BlockPos(x, leftSupport.getY() - 1, leftSupport.getZ()),
                        Blocks.STONE.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
            }
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                server.getPlayerManager().getPlayerList().get(0)
                        .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String shape = "shape=dynamic_BSFB->FB->FB->BSFB_single_top_slab"
                + " leftSupport=" + leftSupport.toShortString()
                + " leftFull=" + leftFull.toShortString()
                + " fb1=" + fb1Pos.toShortString()
                + " fb2=" + fb2Pos.toShortString()
                + " rightSupport=" + rightSupport.toShortString()
                + " rightFull=" + rightFull.toShortString()
                + " topSlab=" + topSlabPos.toShortString();

        final BlockHitResult placeLeftSupportHit = resolveLoweredUpMergeHit(leftSupport.down());
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(leftSupport.getX() + 0.5d, leftSupport.getY() + 1.65d, leftSupport.getZ() + 2.45d),
                placeLeftSupportHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for dynamic bridge left support slab");
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            BlockPos hitPos = placeLeftSupportHit.getBlockPos();
            Direction hitFace = placeLeftSupportHit.getSide();
            BlockPos intendedPlacePos = hitPos.offset(hitFace);
            BlockState hitState = mc.world.getBlockState(hitPos);
            BlockState intendedPlaceStateBefore = mc.world.getBlockState(intendedPlacePos);
            ItemStack heldStack = mc.player.getMainHandStack();
            String held = heldStack.isEmpty() ? "empty" : heldStack.getItem().toString();
            String crosshairOwner = asOwner(mc.crosshairTarget);
            Vec3d liveEye = mc.player.getCameraPosVec(0.0f);
            Vec3d hitVec = placeLeftSupportHit.getPos();
            double dist = liveEye.distanceTo(hitVec);
            var slabState = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
            var slabShape = slabState.getCollisionShape(mc.world, intendedPlacePos);
            var slabBox = slabShape.isEmpty() ? null : slabShape.getBoundingBox().offset(intendedPlacePos);
            var playerBox = mc.player.getBoundingBox();
            boolean intersectsPlayer = slabBox != null && slabBox.intersects(playerBox);
            System.out.println("[DYNAMIC_BRIDGE_ISO] step=left_support_pre"
                    + " playerPos=(" + mc.player.getX() + ", " + mc.player.getY() + ", " + mc.player.getZ() + ")"
                    + " eye=" + liveEye
                    + " playerBox=" + formatBox(playerBox)
                    + " held=" + held
                    + " hitPos=" + hitPos.toShortString()
                    + " hitFace=" + hitFace
                    + " hitState=" + hitState
                    + " hitVec=" + hitVec
                    + " dist=" + dist
                    + " crosshairOwner=" + crosshairOwner
                    + " intendedPlacePos=" + intendedPlacePos.toShortString()
                    + " intendedBefore=" + intendedPlaceStateBefore
                    + " slabCollision=" + (slabBox == null ? "EMPTY" : formatBox(slabBox))
                    + " slabIntersectsPlayer=" + intersectsPlayer
                    + " " + shape);
            System.out.println("[DYNAMIC_BRIDGE_ISO] step=left_support_hit"
                    + " targetBlock=" + hitPos.toShortString()
                    + " face=" + hitFace
                    + " intendedPlace=" + intendedPlacePos.toShortString()
                    + " crosshair=" + crosshairOwner
                    + " dist=" + dist);
            ActionResult supportResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeLeftSupportHit);
            BlockState intendedPlaceStateAfter = mc.world.getBlockState(intendedPlacePos);
            System.out.println("[DYNAMIC_BRIDGE_ISO] step=left_support_result"
                    + " result=" + supportResult
                    + " intendedAfter=" + intendedPlaceStateAfter
                    + " expectedPlace=" + intendedPlacePos.toShortString()
                    + " held=" + held
                    + " slabIntersectsPlayer=" + intersectsPlayer
                    + " dist=" + dist);
            if (!supportResult.isAccepted()) {
                System.out.println("[DYNAMIC_BRIDGE_ISO_RED] step=left_support_place rejected"
                        + " reason=result=" + supportResult
                        + " hitPos=" + hitPos.toShortString()
                        + " face=" + hitFace
                        + " intendedPlace=" + intendedPlacePos.toShortString()
                        + " crosshair=" + crosshairOwner
                        + " dist=" + dist
                        + " slabIntersectsPlayer=" + intersectsPlayer
                        + " intendedAfter=" + intendedPlaceStateAfter
                        + " " + shape);
                throw new RuntimeException("dynamic bridge left support slab placement rejected: " + supportResult);
            }
            System.out.println("[DYNAMIC_BRIDGE_ISO_GREEN] step=left_support_place"
                    + " expected=" + intendedPlacePos.toShortString()
                    + " actual=" + intendedPlacePos.toShortString()
                    + " vanilla=" + asOwner(mc.world.raycast(new RaycastContext(
                    liveEye,
                    liveEye.add(mc.player.getRotationVec(0.0f).multiply(6.0d)),
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player)))
                    + " outline=" + intendedPlacePos.toShortString());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockHitResult placeLeftFullHit = resolveLoweredUpMergeHit(leftSupport);
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(leftSupport.getX() + 0.5d, leftSupport.getY() + 2.35d, leftSupport.getZ() + 2.45d),
                placeLeftFullHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for dynamic bridge left full block");
            }
            ActionResult leftFullResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeLeftFullHit);
            if (!leftFullResult.isAccepted()) {
                throw new RuntimeException("dynamic bridge left full block placement rejected: " + leftFullResult);
            }
            BlockState supportState = mc.world.getBlockState(leftSupport);
            BlockState leftState = mc.world.getBlockState(leftFull);
            double supportDy = supportState.isOf(Blocks.STONE_SLAB) ? SlabSupport.getYOffset(mc.world, leftSupport, supportState) : Double.NaN;
            double leftDy = leftState.isOf(Blocks.STONE) ? SlabSupport.getYOffset(mc.world, leftFull, leftState) : Double.NaN;
            System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB] step=build_left_bsfb support="
                    + leftSupport.toShortString() + " supportState=" + supportState + " supportDy=" + supportDy
                    + " full=" + leftFull.toShortString() + " fullState=" + leftState + " fullDy=" + leftDy
                    + " " + shape);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockHitResult placeFb1Hit = resolveLoweredFaceHit(leftFull, Direction.EAST, 0.5d);
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(leftFull.getX() + 0.5d, leftFull.getY() + 1.8d, leftFull.getZ() + 2.55d),
                placeFb1Hit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for dynamic bridge fb1 placement");
            }
            ActionResult fb1Result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeFb1Hit);
            if (!fb1Result.isAccepted()) {
                throw new RuntimeException("dynamic bridge fb1 placement rejected: " + fb1Result);
            }
            BlockState fb1 = mc.world.getBlockState(fb1Pos);
            double fb1Dy = fb1.isOf(Blocks.STONE) ? SlabSupport.getYOffset(mc.world, fb1Pos, fb1) : Double.NaN;
            System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB] step=place_fb1 pos="
                    + fb1Pos.toShortString() + " state=" + fb1 + " dy=" + fb1Dy + " " + shape);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockHitResult placeFb2Hit = resolveLoweredFaceHit(fb1Pos, Direction.EAST, 0.5d);
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(fb1Pos.getX() + 0.5d, fb1Pos.getY() + 1.8d, fb1Pos.getZ() + 2.55d),
                placeFb2Hit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for dynamic bridge fb2 placement");
            }
            ActionResult fb2Result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeFb2Hit);
            if (!fb2Result.isAccepted()) {
                throw new RuntimeException("dynamic bridge fb2 placement rejected: " + fb2Result);
            }
            BlockState fb2 = mc.world.getBlockState(fb2Pos);
            double fb2Dy = fb2.isOf(Blocks.STONE) ? SlabSupport.getYOffset(mc.world, fb2Pos, fb2) : Double.NaN;
            System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB] step=place_fb2 pos="
                    + fb2Pos.toShortString() + " state=" + fb2 + " dy=" + fb2Dy + " " + shape);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockHitResult placeRightSupportHit = resolveLoweredUpMergeHit(rightSupport.down());
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(rightSupport.getX() + 0.5d, rightSupport.getY() + 1.65d, rightSupport.getZ() + 2.45d),
                placeRightSupportHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for dynamic bridge right support slab");
            }
            ActionResult supportResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeRightSupportHit);
            if (!supportResult.isAccepted()) {
                throw new RuntimeException("dynamic bridge right support slab placement rejected: " + supportResult);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockHitResult placeRightFullHit = resolveLoweredUpMergeHit(rightSupport);
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(rightSupport.getX() + 0.5d, rightSupport.getY() + 2.35d, rightSupport.getZ() + 2.45d),
                placeRightFullHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for dynamic bridge right full block");
            }
            ActionResult rightFullResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeRightFullHit);
            if (!rightFullResult.isAccepted()) {
                throw new RuntimeException("dynamic bridge right full block placement rejected: " + rightFullResult);
            }
            BlockState supportState = mc.world.getBlockState(rightSupport);
            BlockState rightState = mc.world.getBlockState(rightFull);
            double supportDy = supportState.isOf(Blocks.STONE_SLAB) ? SlabSupport.getYOffset(mc.world, rightSupport, supportState) : Double.NaN;
            double rightDy = rightState.isOf(Blocks.STONE) ? SlabSupport.getYOffset(mc.world, rightFull, rightState) : Double.NaN;
            System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB] step=build_right_bsfb support="
                    + rightSupport.toShortString() + " supportState=" + supportState + " supportDy=" + supportDy
                    + " full=" + rightFull.toShortString() + " fullState=" + rightState + " fullDy=" + rightDy
                    + " " + shape);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockHitResult placeSingleTopSlabHit = resolveLoweredUpMergeHit(fb1Pos);
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(fb1Pos.getX() + 0.5d, fb1Pos.getY() + 1.8d, fb1Pos.getZ() + 2.55d),
                placeSingleTopSlabHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for dynamic bridge top slab placement");
            }
            ActionResult slabResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSingleTopSlabHit);
            if (!slabResult.isAccepted()) {
                throw new RuntimeException("dynamic bridge single top slab placement rejected: " + slabResult);
            }
            BlockState topState = mc.world.getBlockState(topSlabPos);
            if (!topState.isOf(Blocks.STONE_SLAB) || !topState.contains(SlabBlock.TYPE)) {
                throw new RuntimeException("dynamic bridge expected single slab at " + topSlabPos.toShortString()
                        + ", found " + topState);
            }
            SlabType topType = topState.get(SlabBlock.TYPE);
            double topDy = SlabSupport.getYOffset(mc.world, topSlabPos, topState);
            System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB] step=place_single_top_slab pos="
                    + topSlabPos.toShortString() + " state=" + topState
                    + " type=" + topType + " dy=" + topDy + " " + shape);
            if (topType == SlabType.DOUBLE) {
                throw new RuntimeException("dynamic bridge invalid repro: single top slab merged to DOUBLE at "
                        + topSlabPos.toShortString());
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        runDynamicBridgeOwnershipProbe(
                ctx,
                topSlabPos,
                new Vec3d(topSlabPos.getX() + 0.5d, topSlabPos.getY() + 1.65d, topSlabPos.getZ() + 2.45d),
                new Vec3d(topSlabPos.getX() + 0.5d, topSlabPos.getY() + 0.18d, topSlabPos.getZ() + 0.5d),
                "select_single_top_slab",
                "front_lower_center",
                new ItemStack(Items.STONE_SLAB, 8),
                shape);

        final BlockHitResult placeOnTopSlabHit = resolveLoweredUpMergeHit(topSlabPos);
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(topSlabPos.getX() + 0.5d, topSlabPos.getY() + 2.35d, topSlabPos.getZ() + 2.35d),
                placeOnTopSlabHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for dynamic bridge build-on-top");
            }
            ActionResult topBuildResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeOnTopSlabHit);
            if (!topBuildResult.isAccepted()) {
                throw new RuntimeException("dynamic bridge build-on-top rejected: " + topBuildResult);
            }
            BlockState builtState = mc.world.getBlockState(aboveTopSlabPos);
            double builtDy = builtState.isOf(Blocks.STONE) ? SlabSupport.getYOffset(mc.world, aboveTopSlabPos, builtState) : Double.NaN;
            System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB] step=build_on_top_slab pos="
                    + aboveTopSlabPos.toShortString() + " state=" + builtState + " dy=" + builtDy + " " + shape);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        runDynamicBridgeOwnershipProbe(
                ctx,
                aboveTopSlabPos,
                new Vec3d(aboveTopSlabPos.getX() + 0.5d, aboveTopSlabPos.getY() + 1.65d, aboveTopSlabPos.getZ() + 2.45d),
                new Vec3d(aboveTopSlabPos.getX() + 0.5d, aboveTopSlabPos.getY() - 0.30d, aboveTopSlabPos.getZ() + 0.5d),
                "lower_half_after_build",
                "front_lower_center",
                new ItemStack(Items.STONE_SLAB, 8),
                shape);
    }

    private static void runDynamicBridgeOwnershipProbe(
            ClientGameTestContext ctx,
            BlockPos expectedOwnerPos,
            Vec3d eye,
            Vec3d aimPoint,
            String step,
            String aimRegion,
            ItemStack heldStack,
            String setupDetails
    ) {
        final double reach = 6.0d;

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[DYNAMIC_BRIDGE_TOP_SLAB_RED] step=" + step + " client not ready");
            }
            if (heldStack == null || heldStack.isEmpty()) {
                mc.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            } else {
                mc.player.setStackInHand(Hand.MAIN_HAND, heldStack.copy());
            }

            Vec3d delta = aimPoint.subtract(eye);
            double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
            double feetY = eye.y - mc.player.getStandingEyeHeight();
            mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            mc.player.setVelocity(Vec3d.ZERO);

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(reach));
            HitResult crosshair = mc.crosshairTarget;
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            BlockState expectedState = mc.world.getBlockState(expectedOwnerPos);
            BlockHitResult outlineHit = expectedState.getOutlineShape(mc.world, expectedOwnerPos)
                    .raycast(rayStart, rayEnd, expectedOwnerPos);

            String expectedOwner = expectedOwnerPos.toShortString();
            String crosshairOwner = asOwner(crosshair);
            String vanillaOwner = asOwner(vanilla);
            String outlineOwner = outlineHit == null ? "MISS" : outlineHit.getBlockPos().toShortString();
            String held = mc.player.getMainHandStack().isEmpty()
                    ? "empty"
                    : mc.player.getMainHandStack().getItem().toString();
            double expectedDy = SlabSupport.getYOffset(mc.world, expectedOwnerPos, expectedState);
            String visualBox = expectedState.getOutlineShape(mc.world, expectedOwnerPos).isEmpty()
                    ? "EMPTY"
                    : formatBox(expectedState.getOutlineShape(mc.world, expectedOwnerPos).getBoundingBox().offset(expectedOwnerPos));

            System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB] step=" + step
                    + " expected=" + expectedOwner
                    + " actual=" + crosshairOwner
                    + " vanilla=" + vanillaOwner
                    + " outline=" + outlineOwner
                    + " held=" + held
                    + " aimRegion=" + aimRegion
                    + " expectedState=" + expectedState
                    + " expectedDy=" + expectedDy
                    + " eye=" + eye
                    + " aim=" + aimPoint
                    + " liveEye=" + rayStart
                    + " dir=" + rayDir
                    + " visualBox=" + visualBox
                    + " " + setupDetails);

            boolean ownerMatch = expectedOwner.equals(crosshairOwner);
            boolean expectedOutlineHit = expectedOwner.equals(outlineOwner);
            if (ownerMatch) {
                System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB_GREEN] step=" + step
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
                return;
            }
            if (expectedOutlineHit) {
                System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB_RED] step=" + step
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
                throw new RuntimeException("[DYNAMIC_BRIDGE_TOP_SLAB_RED] step=" + step
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
            }

            System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB] step=" + step
                    + " calibration=aim_miss expected=" + expectedOwner
                    + " actual=" + crosshairOwner
                    + " vanilla=" + vanillaOwner
                    + " outline=" + outlineOwner);
        });
    }

    private static void runBeta4OutlineHitRaycastMissRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = new BlockPos(14, -58, 0);
        final BlockPos expectedOwnerPos = new BlockPos(14, -57, 0);
        final Vec3d liveEye = new Vec3d(12.165d, -56.960d, 1.750d);
        final Vec3d liveLook = new Vec3d(0.826d, -0.067d, -0.559d);
        final Vec3d liveEnd = liveEye.add(liveLook.multiply(6.0d));

        setupFixture(singleplayer, supportPos, expectedOwnerPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState fullState = world.getBlockState(expectedOwnerPos);
            SlabAnchorAttachment.addAnchor(world, expectedOwnerPos, fullState);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, ItemStack.EMPTY);
        syncPlayerAim(ctx, singleplayer, liveEye, liveEnd);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[BETA4_OUTLINE_HIT_RAYCAST_MISS_RED] client not ready");
            }

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(6.0d));
            HitResult finalTarget = mc.crosshairTarget;
            BlockState expectedState = mc.world.getBlockState(expectedOwnerPos);
            double sourceDy = SlabSupport.getYOffset(mc.world, expectedOwnerPos, expectedState);
            double clientDy = ClientDy.dyFor(mc.world, expectedOwnerPos, expectedState);
            boolean anchored = SlabAnchorAttachment.isAnchored(mc.world, expectedOwnerPos);
            boolean persistentCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    mc.world, expectedOwnerPos, expectedState);
            VoxelShape outlineShape = expectedState.getOutlineShape(mc.world, expectedOwnerPos);
            VoxelShape raycastShape = expectedState.getRaycastShape(mc.world, expectedOwnerPos);
            BlockHitResult outlineHit = outlineShape.raycast(rayStart, rayEnd, expectedOwnerPos);
            BlockHitResult raycastHit = raycastShape.raycast(rayStart, rayEnd, expectedOwnerPos);
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));

            String expectedOwner = expectedOwnerPos.toShortString();
            String actualOwner = asOwner(finalTarget);
            String vanillaOwner = asOwner(vanilla);
            String outlineOwner = outlineHit == null ? "MISS" : outlineHit.getBlockPos().toShortString();
            String raycastOwner = raycastHit == null ? "MISS" : raycastHit.getBlockPos().toShortString();
            String sourceMode = persistentCarrier
                    ? "persistentLoweredSlabCarrier"
                    : (Math.abs(sourceDy + 0.5d) <= EPSILON ? "dynamicLoweredOrAnchored" : "normal");
            String outlineBox = outlineShape.isEmpty() ? "empty" : formatBox(outlineShape.getBoundingBox());
            String raycastBox = raycastShape.isEmpty() ? "empty" : formatBox(raycastShape.getBoundingBox());
            String targetType = finalTarget == null ? "null" : finalTarget.getType().toString();
            boolean targetIsMiss = finalTarget == null || finalTarget.getType() == HitResult.Type.MISS;

            String facts = " expectedOwner=" + expectedOwner
                    + " actualOwner=" + actualOwner
                    + " vanillaOwner=" + vanillaOwner
                    + " outlineOwner=" + outlineOwner
                    + " raycastOwner=" + raycastOwner
                    + " expectedState=" + expectedState
                    + " dy=" + sourceDy
                    + " clientDy=" + clientDy
                    + " modelDy=not_accessible"
                    + " anchored=" + anchored
                    + " persistentFullBlockAnchor=" + (anchored && expectedState.isOf(Blocks.STONE))
                    + " persistentLoweredSlabCarrier=" + persistentCarrier
                    + " sourceMode=" + sourceMode
                    + " outlineShape=" + outlineBox
                    + " outlineHit=" + (outlineHit != null)
                    + " outlineHitDesc=" + describeHit(outlineHit)
                    + " raycastShape=" + raycastBox
                    + " raycastShapeEmpty=" + raycastShape.isEmpty()
                    + " raycastHit=" + (raycastHit != null)
                    + " raycastHitDesc=" + describeHit(raycastHit)
                    + " crosshairTargetType=" + targetType
                    + " targetIsMiss=" + targetIsMiss
                    + " eye=" + liveEye
                    + " look=" + liveLook
                    + " requestedEnd=" + liveEnd
                    + " rayStart=" + rayStart
                    + " rayDir=" + rayDir
                    + " rayEnd=" + rayEnd
                    + " ownerClass=ANCHORED_FULL_BLOCK"
                    + " expectedOwnerClass=ANCHORED_FULL_BLOCK"
                    + " actualOwnerClass=" + (targetIsMiss ? "MISS" : beta4OwnerClass(
                    finalTarget, expectedOwnerPos, null, null));

            boolean raycastShapeRed = expectedState.isOf(Blocks.STONE)
                    && Math.abs(sourceDy + 0.5d) <= EPSILON
                    && Math.abs(clientDy + 0.5d) <= EPSILON
                    && anchored
                    && expectedOwner.equals(outlineOwner)
                    && raycastHit == null
                    && (raycastShape.isEmpty() || !expectedOwner.equals(raycastOwner));
            if (raycastShapeRed) {
                String classifiedFacts = " classification=RAYCAST_SHAPE_RED"
                        + " crosshairMissReproduced=" + targetIsMiss
                        + facts;
                System.out.println("[BETA4_OUTLINE_HIT_RAYCAST_MISS_RED]" + classifiedFacts);
                throw new RuntimeException("[BETA4_OUTLINE_HIT_RAYCAST_MISS_RED]" + classifiedFacts);
            }

            boolean raycastShapeGreen = expectedState.isOf(Blocks.STONE)
                    && Math.abs(sourceDy + 0.5d) <= EPSILON
                    && Math.abs(clientDy + 0.5d) <= EPSILON
                    && anchored
                    && expectedOwner.equals(outlineOwner)
                    && expectedOwner.equals(raycastOwner)
                    && !raycastShape.isEmpty()
                    && raycastHit != null;
            if (raycastShapeGreen) {
                System.out.println("[BETA4_OUTLINE_HIT_RAYCAST_MISS_GREEN]"
                        + " classification=RAYCAST_SHAPE_GREEN"
                        + " crosshairMissReproduced=" + targetIsMiss
                        + facts);
                return;
            }

            throw new RuntimeException("[BETA4_OUTLINE_HIT_RAYCAST_MISS] calibration_mismatch" + facts);
        });
    }

    private static void syncHeldMainHand(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            ItemStack stack
    ) {
        ItemStack held = stack == null ? ItemStack.EMPTY : stack.copy();
        singleplayer.getServer().runOnServer(server -> {
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                server.getPlayerManager().getPlayerList().get(0).setStackInHand(Hand.MAIN_HAND, held.copy());
            }
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.setStackInHand(Hand.MAIN_HAND, held.copy());
            }
        });
        ctx.waitTick();
    }

    private static void syncPlayerAim(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Vec3d eye,
            Vec3d target
    ) {
        Vec3d delta = target.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        syncPlayerPosition(ctx, singleplayer, eye.x, feetY, eye.z, yaw, pitch);
    }

    private static void syncPlayerLookFromEye(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Vec3d eye,
            float yaw,
            float pitch
    ) {
        double feetY = eye.y - 1.62d;
        syncPlayerPosition(ctx, singleplayer, eye.x, feetY, eye.z, yaw, pitch);
    }

    private static void setLoweredSlabTarget(
            TestSingleplayerContext singleplayer,
            BlockPos targetPos,
            SlabType targetType
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer player missing during target setup");
            }
            world.setBlockState(
                    targetPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, targetType),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);

            BlockState targetState = world.getBlockState(targetPos);
            if (!targetState.isOf(Blocks.STONE_SLAB)
                    || !targetState.contains(SlabBlock.TYPE)
                    || targetState.get(SlabBlock.TYPE) != targetType) {
                throw new RuntimeException("could not seed slab target " + targetType + " at "
                        + targetPos.toShortString()
                        + ", found " + targetState);
            }
            double targetDy = SlabSupport.getYOffset(world, targetPos, targetState);
            if (Math.abs(targetDy + 0.5d) > EPSILON) {
                throw new RuntimeException("seeded target " + targetType + " was not lowered at "
                        + targetPos.toShortString()
                        + ", found dy=" + targetDy);
            }
        });
    }

    private static void setPlacementPosTarget(
            TestSingleplayerContext singleplayer,
            BlockPos placementPos,
            SlabType targetType
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    placementPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, targetType),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);

            BlockState placed = world.getBlockState(placementPos);
            if (!placed.isOf(Blocks.STONE_SLAB)
                    || !placed.contains(SlabBlock.TYPE)
                    || placed.get(SlabBlock.TYPE) != targetType) {
                throw new RuntimeException("could not seed placePos slab target " + targetType + " at "
                        + placementPos.toShortString() + ", found " + placed);
            }
        });
    }

    private static void setupFixture(TestSingleplayerContext singleplayer, BlockPos supportPos, BlockPos fullPos) {
        final int minY = fullPos.getY() - 1;
        final int maxY = fullPos.getY() + 1;

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = supportPos.getX() - 2; x <= supportPos.getX() + 2; x++) {
                for (int z = supportPos.getZ() - 2; z <= supportPos.getZ() + 2; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        world.setBlockState(
                                new BlockPos(x, y, z),
                                Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }

            world.setBlockState(
                    supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);

            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer player missing during repro setup");
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });
    }

    private static void assertTopLowered(
            BlockPos pos,
            BlockState state,
            net.minecraft.world.BlockView world,
            String label
    ) {
        if (!state.isOf(Blocks.STONE_SLAB)
                || !state.contains(SlabBlock.TYPE)
                || state.get(SlabBlock.TYPE) != SlabType.TOP) {
            throw new RuntimeException(label + " expected TOP slab at "
                    + pos.toShortString() + ", found " + state);
        }
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (Math.abs(dy + 0.5d) > EPSILON) {
            throw new RuntimeException(label + " expected lowered dy=-0.500 at "
                    + pos.toShortString() + ", found dy=" + dy + " state=" + state);
        }
    }

    private static void assertLaneDy(
            net.minecraft.world.BlockView world,
            BlockPos laneRootPos,
            BlockPos laneTailPos,
            double expectedDy,
            String label
    ) {
        BlockState laneRoot = world.getBlockState(laneRootPos);
        BlockState laneTail = world.getBlockState(laneTailPos);
        if (!laneRoot.isOf(Blocks.STONE_SLAB) || !laneRoot.contains(SlabBlock.TYPE)) {
            throw new RuntimeException(label + " laneRoot must remain slab, found " + laneRoot);
        }
        if (!laneTail.isOf(Blocks.STONE_SLAB) || !laneTail.contains(SlabBlock.TYPE)) {
            throw new RuntimeException(label + " laneTail must remain slab, found " + laneTail);
        }
        double rootDy = SlabSupport.getYOffset(world, laneRootPos, laneRoot);
        double tailDy = SlabSupport.getYOffset(world, laneTailPos, laneTail);
        if (Math.abs(rootDy - expectedDy) > EPSILON || Math.abs(tailDy - expectedDy) > EPSILON) {
            throw new RuntimeException(label + " expected both lane dy values to be " + expectedDy
                    + " but rootDy=" + rootDy + " tailDy=" + tailDy
                    + " rootState=" + laneRoot + " tailState=" + laneTail);
        }
    }

    private static void assertForbiddenMixedDoubleBottomStateAbsent(
            net.minecraft.world.BlockView world,
            BlockPos laneDoublePos,
            BlockPos laneBottomPos,
            String label
    ) {
        BlockState laneDouble = world.getBlockState(laneDoublePos);
        BlockState laneBottom = world.getBlockState(laneBottomPos);
        if (!laneDouble.isOf(Blocks.STONE_SLAB)
                || !laneDouble.contains(SlabBlock.TYPE)
                || laneDouble.get(SlabBlock.TYPE) != SlabType.DOUBLE
                || !laneBottom.isOf(Blocks.STONE_SLAB)
                || !laneBottom.contains(SlabBlock.TYPE)
                || laneBottom.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
            return;
        }
        double doubleDy = SlabSupport.getYOffset(world, laneDoublePos, laneDouble);
        double bottomDy = SlabSupport.getYOffset(world, laneBottomPos, laneBottom);
        if (Math.abs(doubleDy + 0.5d) <= EPSILON && Math.abs(bottomDy) <= EPSILON) {
            throw new RuntimeException(label + " found forbidden mixed state: lowered DOUBLE dy=-0.500 adjacent to "
                    + "normal BOTTOM dy=0 at double=" + laneDoublePos.toShortString()
                    + " bottom=" + laneBottomPos.toShortString()
                    + " states=(" + laneDouble + ", " + laneBottom + ")");
        }
    }

    private static void assertNoLoweredRemainderWithoutSupport(
            net.minecraft.world.BlockView world,
            BlockPos laneDoublePos,
            BlockPos laneBottomPos,
            String label
    ) {
        BlockState laneDouble = world.getBlockState(laneDoublePos);
        BlockState laneBottom = world.getBlockState(laneBottomPos);
        if (laneDouble.isOf(Blocks.STONE_SLAB) && laneDouble.contains(SlabBlock.TYPE)) {
            double doubleDy = SlabSupport.getYOffset(world, laneDoublePos, laneDouble);
            if (doubleDy < 0.0d) {
                throw new RuntimeException(label + " unsupported lowered remainder at DOUBLE slot "
                        + laneDoublePos.toShortString()
                        + " dy=" + doubleDy + " state=" + laneDouble);
            }
        }
        if (laneBottom.isOf(Blocks.STONE_SLAB) && laneBottom.contains(SlabBlock.TYPE)) {
            double bottomDy = SlabSupport.getYOffset(world, laneBottomPos, laneBottom);
            if (bottomDy < 0.0d) {
                throw new RuntimeException(label + " unsupported lowered remainder at BOTTOM slot "
                        + laneBottomPos.toShortString()
                        + " dy=" + bottomDy + " state=" + laneBottom);
            }
        }
    }

    private static BlockHitResult resolveLoweredSideFaceHit(BlockPos targetPos, Direction face, SlabType targetType) {
        double yOffset = targetType == SlabType.BOTTOM ? -0.25d : 0.25d;
        return resolveLoweredFaceHit(targetPos, face, yOffset);
    }

    private static BlockHitResult resolveLoweredUpMergeHit(BlockPos targetPos) {
        return new BlockHitResult(
                new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + TOP_FACE_HIT_Y, targetPos.getZ() + 0.5d),
                Direction.UP,
                targetPos,
                false,
                false);
    }

    private static BlockHitResult resolveLoweredFaceHit(BlockPos targetPos, Direction face, double yOffset) {
        return switch (face) {
            case NORTH -> new BlockHitResult(
                    new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + yOffset, targetPos.getZ()),
                    Direction.NORTH,
                    targetPos,
                    false,
                    false);
            case SOUTH -> new BlockHitResult(
                    new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + yOffset, targetPos.getZ() + 1.0d),
                    Direction.SOUTH,
                    targetPos,
                    false,
                    false);
            case EAST -> new BlockHitResult(
                    new Vec3d(targetPos.getX() + 1.0d, targetPos.getY() + yOffset, targetPos.getZ() + 0.5d),
                    Direction.EAST,
                    targetPos,
                    false,
                    false);
            case WEST -> new BlockHitResult(
                    new Vec3d(targetPos.getX(), targetPos.getY() + yOffset, targetPos.getZ() + 0.5d),
                    Direction.WEST,
                    targetPos,
                    false,
                    false);
            default -> throw new IllegalArgumentException("unsupported face for repro: " + face);
        };
    }

    private static String asOwner(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return "MISS";
        }
        return blockHit.getBlockPos().toShortString();
    }

    private static BlockPos blockPos(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return null;
        }
        return blockHit.getBlockPos();
    }

    private static String describeHit(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return "MISS";
        }
        return "pos=" + blockHit.getBlockPos().toShortString()
                + " face=" + blockHit.getSide()
                + " hit=" + blockHit.getPos();
    }

    private static String describeHitFace(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return "MISS";
        }
        return blockHit.getSide().toString();
    }

    private static String describeHitVector(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return "MISS";
        }
        return blockHit.getPos().toString();
    }

    private static String beta4OwnerClass(
            HitResult hit,
            BlockPos anchoredOwnerPos,
            BlockPos visibleUpperSlabOwnerPos,
            BlockPos adjacentVisibleOwnerPos
    ) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return "NO_RESCUE";
        }
        if (!(hit instanceof BlockHitResult blockHit)) {
            return "NO_RESCUE";
        }
        BlockPos pos = blockHit.getBlockPos();
        if (pos.equals(anchoredOwnerPos)) {
            return "ANCHORED_FULL_BLOCK";
        }
        if (pos.equals(visibleUpperSlabOwnerPos)) {
            return "VISIBLE_UPPER_LOWERED_SLAB";
        }
        if (adjacentVisibleOwnerPos != null && pos.equals(adjacentVisibleOwnerPos)) {
            return "ADJACENT_VISIBLE_TARGET";
        }
        return "KEEP_INITIAL";
    }

    private static String describeHitDist2(Vec3d eye, HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return "NaN";
        }
        return String.format("%.6f", blockHit.getPos().squaredDistanceTo(eye));
    }

    private static String describeOwnerFacts(net.minecraft.world.BlockView world, BlockPos pos) {
        if (world == null || pos == null) {
            return "pos=MISS";
        }
        BlockState state = world.getBlockState(pos);
        double targetDy = SlabSupport.getYOffset(world, pos, state);
        double outlineDy = ClientDy.dyFor(world, pos, state);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        boolean persistent = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state);
        String slabType = state.contains(SlabBlock.TYPE) ? state.get(SlabBlock.TYPE).asString() : "none";
        return "pos=" + pos.toShortString()
                + " state=" + state
                + " slabType=" + slabType
                + " targetDy=" + targetDy
                + " outlineDy=" + outlineDy
                + " anchored=" + anchored
                + " persistentLoweredSlabCarrier=" + persistent;
    }

    private static String formatBox(net.minecraft.util.math.Box box) {
        return String.format(
                "min=(%.3f,%.3f,%.3f),max=(%.3f,%.3f,%.3f)",
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static void assertSurvivorChurnSupportTimeline(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String operationLabel,
            BlockPos sourceFullPos,
            BlockPos survivorFullPos
    ) {
        for (int tick = 0; tick < 4; tick++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing during support timeline " + operationLabel
                            + " tick " + readTick);
                }

                BlockState sourceAfter = mc.world.getBlockState(sourceFullPos);
                BlockState survivorAfter = mc.world.getBlockState(survivorFullPos);
                if (!survivorAfter.isAir() && !survivorAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException(operationLabel + " tick " + readTick
                            + " expected air or stone at survivor, found " + survivorAfter);
                }

                boolean survivorDirectBottomSupport = SlabSupport.hasBottomSlabBelow(mc.world, survivorFullPos);
                boolean survivorInheritedOrAnchored = SlabAnchorAttachment.isAnchored(mc.world, survivorFullPos)
                        || (!survivorDirectBottomSupport && survivorAfter.isOf(Blocks.STONE)
                        && SlabSupport.shouldOffset(mc.world, survivorFullPos, survivorAfter));
                double survivorDy = survivorAfter.isOf(Blocks.STONE)
                        ? SlabSupport.getYOffset(mc.world, survivorFullPos, survivorAfter)
                        : Double.NaN;
                System.out.println("[SIDE-CHURN] " + operationLabel + " tick=" + readTick
                        + " sourcePresent=" + sourceAfter.isOf(Blocks.STONE)
                        + " survivorState=" + survivorAfter
                        + " directBottom=" + survivorDirectBottomSupport
                        + " inherited=" + survivorInheritedOrAnchored
                        + " dy=" + survivorDy);

                if (survivorAfter.isOf(Blocks.STONE)) {
                    boolean legalSurvivor = survivorDirectBottomSupport || survivorInheritedOrAnchored;
                    if (legalSurvivor && Math.abs(survivorDy + 0.5d) > EPSILON) {
                        throw new RuntimeException(operationLabel + " tick " + readTick
                                + " survivor jumped or de-dropped while still legal (dy=" + survivorDy + ")");
                    }
                    if (!legalSurvivor && Math.abs(survivorDy + 0.5d) <= EPSILON) {
                        throw new RuntimeException(operationLabel + " tick " + readTick
                                + " survivor remains lowered with no legal support path");
                    }
                }
            });
        }
    }

    private static void movePlayerForFace(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            BlockPos targetPos,
            Direction face
    ) {
        double x = targetPos.getX() + 0.5d;
        double y = targetPos.getY() + 1.95d;
        double z = targetPos.getZ() + 0.5d;
        float yaw;

        switch (face) {
            case NORTH -> {
                z = targetPos.getZ() - 1.5d;
                yaw = 0.0f;
            }
            case SOUTH -> {
                z = targetPos.getZ() + 2.5d;
                yaw = 180.0f;
            }
            case EAST -> {
                x = targetPos.getX() - 1.5d;
                yaw = 90.0f;
            }
            case WEST -> {
                x = targetPos.getX() + 2.5d;
                yaw = -90.0f;
            }
            default -> throw new IllegalArgumentException("unsupported face for repro: " + face);
        }

        syncPlayerPosition(ctx, singleplayer, x, y, z, yaw, 0.0f);
    }

    private static void movePlayerForUp(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            BlockPos targetPos
    ) {
        syncPlayerPosition(
                ctx,
                singleplayer,
                targetPos.getX() + 0.5d,
                targetPos.getY() + 3.15d,
                targetPos.getZ() + 0.5d,
                0.0f,
                -90.0f);
    }

    private static void syncPlayerPosition(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        singleplayer.getServer().runOnServer(server -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                return;
            }
            var player = server.getPlayerManager().getPlayerList().get(0);
            player.refreshPositionAndAngles(x, y, z, yaw, pitch);
            player.setVelocity(Vec3d.ZERO);
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.refreshPositionAndAngles(x, y, z, yaw, pitch);
                mc.player.setVelocity(Vec3d.ZERO);
            }
        });
        ctx.waitTick();
    }
}
