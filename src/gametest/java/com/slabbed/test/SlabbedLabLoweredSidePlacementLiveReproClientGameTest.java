package com.slabbed.test;

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
            runLoweredTopUpMergeCase(ctx, singleplayer);
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
