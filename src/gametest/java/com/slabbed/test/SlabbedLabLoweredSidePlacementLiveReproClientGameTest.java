package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

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
            runLoweredDoubleLowerHalfBoundaryCase(ctx, singleplayer, Direction.EAST);
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
