package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.debug.SlabbedRetargetTestHooks;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class SlabbedLabUltraGoblin2StressClientGameTest implements FabricClientGameTest {
    private static final String PREFIX = "[SBSB-ULTRA2]";
    private static final BlockPos SOURCE = new BlockPos(24, 201, 0);
    private static final BlockPos SUPPORT = new BlockPos(24, 200, 0);
    private static final BlockPos UPPER_DOUBLE = SOURCE.up();
    private static final Vec3d EYE = new Vec3d(21.6347d, 203.4287d, 0.8327d);
    private static final Vec3d HIT = new Vec3d(24.3566d, 201.5000d, 0.5941d);

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            String mode = System.getProperty("slabbed.ultra2.mode", "phase19");
            if (!"phase19".equals(mode)) {
                throw new RuntimeException(PREFIX + " expected slabbed.ultra2.mode=phase19, got " + mode);
            }
            runPhase19(ctx, singleplayer);
        }
    }

    private static void runPhase19(ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        System.out.println(PREFIX + "[MODE] mode=phase19");
        System.out.println(PREFIX + "[PHASE_BEGIN] PHASE_19_LIVE_RECORDER_SLAB_HELD_RETARGET_OVERREACH_REPRO");

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = 20; x <= 27; x++) {
                for (int y = 199; y <= 204; y++) {
                    for (int z = -2; z <= 2; z++) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
            world.setBlockState(
                    SUPPORT,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(SOURCE, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, SOURCE, world.getBlockState(SOURCE));
            world.setBlockState(
                    UPPER_DOUBLE,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, UPPER_DOUBLE, world.getBlockState(UPPER_DOUBLE));
        });
        waitForPlacementSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();

        syncPlayerAim(ctx, singleplayer, EYE, HIT);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null) {
                throw new RuntimeException(PREFIX + " client not ready for phase19");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
        });
        ctx.waitTick();

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null) {
                throw new RuntimeException(PREFIX + " client not ready to resolve phase19");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            HitResult vanillaTarget = mc.player.raycast(5.0d, 0.0f, false);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult finalTarget = mc.crosshairTarget;

            System.out.println(PREFIX + "[LIVE_RETARGET_REPRO] phase=PHASE_19_LIVE_RECORDER_SLAB_HELD_RETARGET_OVERREACH_REPRO"
                    + " stage=resolved"
                    + " vanilla=" + describeHit(vanillaTarget)
                    + " final=" + describeHit(finalTarget)
                    + " expectedInitial=" + SOURCE.toShortString() + "/up"
                    + " forbiddenFinal=" + UPPER_DOUBLE.toShortString() + "/west");

            if (!(vanillaTarget instanceof BlockHitResult vanillaHit)
                    || !vanillaHit.getBlockPos().equals(SOURCE)
                    || vanillaHit.getSide() != Direction.UP) {
                throw new RuntimeException("RED: phase19 fixture expected vanilla target="
                        + SOURCE.toShortString() + "/up, got " + describeHit(vanillaTarget));
            }
            if (!(finalTarget instanceof BlockHitResult finalHit)) {
                throw new RuntimeException("RED: phase19 final target was not a block: " + describeHit(finalTarget));
            }
            if (finalHit.getBlockPos().equals(UPPER_DOUBLE) && finalHit.getSide() == Direction.WEST) {
                throw new RuntimeException("RED: live recorder slab-held retarget should not steal anchored lowered full-block UP hit:"
                        + " initial=" + SOURCE.toShortString() + "/up"
                        + " final=" + UPPER_DOUBLE.toShortString() + "/west"
                        + " decision=scan-side-slab-fired-slab-held-tiebreak");
            }
            if (!finalHit.getBlockPos().equals(SOURCE) || finalHit.getSide() != Direction.UP) {
                throw new RuntimeException("RED: live recorder slab-held retarget should preserve anchored lowered full-block UP hit:"
                        + " initial=" + SOURCE.toShortString() + "/up"
                        + " final=" + describeHit(finalTarget));
            }
        });

        runVisibleLoweredSlabMissAngleOwnerGapProof(ctx, singleplayer);

        System.out.println(PREFIX + "[PHASE_END] PHASE_19_LIVE_RECORDER_SLAB_HELD_RETARGET_OVERREACH_REPRO");
        System.out.println(PREFIX + "[GREEN] phase=SUMMARY raysTested=1 raysOwned=1");
    }

    private static void runVisibleLoweredSlabMissAngleOwnerGapProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        String proofName = "RED_VISIBLE_LOWERED_SLAB_MISS_ANGLE_OWNER_GAP";
        BlockPos targetPos = new BlockPos(9, -58, -17);
        BlockPos supportPos = targetPos.down();
        BlockPos abovePos = targetPos.up();
        BlockPos eastPos = targetPos.east();
        Vec3d missAngleEye = new Vec3d(11.859d, -58.380d, -14.611d);
        Vec3d missAngleEnd = new Vec3d(7.340d, -57.398d, -18.433d);
        Vec3d hitAngleEnd = new Vec3d(7.575d, -57.522d, -18.723d);

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = targetPos.getX() - 2; x <= targetPos.getX() + 2; x++) {
                for (int y = supportPos.getY() - 2; y <= targetPos.getY() + 2; y++) {
                    for (int z = targetPos.getZ() - 2; z <= targetPos.getZ() + 2; z++) {
                        BlockPos clearPos = new BlockPos(x, y, z);
                        SlabAnchorAttachment.removeAnchor(world, clearPos);
                        SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, clearPos);
                        world.setBlockState(clearPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }
            BlockPos supportBase = supportPos.down();
            BlockPos supportBaseBelow = supportBase.down();
            world.setBlockState(
                    supportBaseBelow,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    supportBase,
                    Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, supportBase, world.getBlockState(supportBase));
            world.setBlockState(supportPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, supportPos, world.getBlockState(supportPos));
            world.setBlockState(
                    targetPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(abovePos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(eastPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        });

        waitForPlacementSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();
        syncPlayerAim(ctx, singleplayer, missAngleEye, missAngleEnd);

        System.out.println("[SBSB-TRACE][MISS_ANGLE_OWNER_GAP_ROUTE] running visible lowered slab MISS-angle owner gap proof");

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException(proofName + " client not ready");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            var cam = mc.getCameraEntity();
            if (cam == null) {
                throw new RuntimeException(proofName + " client camera missing");
            }

            BlockState targetState = mc.world.getBlockState(targetPos);
            BlockState supportState = mc.world.getBlockState(supportPos);
            double targetDy = SlabSupport.getYOffset(mc.world, targetPos, targetState);
            double supportDy = SlabSupport.getYOffset(mc.world, supportPos, supportState);
            boolean supportAnchored = SlabAnchorAttachment.isAnchored(mc.world, supportPos);
            if (!targetState.isOf(Blocks.STONE_SLAB) || !targetState.contains(SlabBlock.TYPE)
                    || targetState.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("RED: " + proofName
                        + " target slab is not a bottom slab: pos=" + targetPos.toShortString()
                        + " state=" + targetState);
            }
            if (!supportState.isOf(Blocks.STONE)) {
                throw new RuntimeException("RED: " + proofName
                        + " support block is not stone: pos=" + supportPos.toShortString()
                        + " state=" + supportState);
            }

            BlockHitResult missOutline = targetState.getOutlineShape(mc.world, targetPos, ShapeContext.of(cam))
                    .raycast(missAngleEye, missAngleEnd, targetPos);
            BlockHitResult missNative = targetState.getRaycastShape(mc.world, targetPos).raycast(
                    missAngleEye,
                    missAngleEnd,
                    targetPos);
            BlockHitResult hitOutline = targetState.getOutlineShape(mc.world, targetPos, ShapeContext.of(cam))
                    .raycast(missAngleEye, hitAngleEnd, targetPos);
            BlockHitResult activeRetarget = SlabbedRetargetTestHooks.findLoweredSideSlabRetarget(
                    mc.world, cam, missAngleEye, missAngleEnd, null, true);
            String activeOwner = activeRetarget == null ? "MISS" : describeHit(activeRetarget);

            System.out.println("[SBSB-TRACE][MISS_ANGLE_OWNER_GAP_ROUTE] ownerScan"
                    + " target=" + targetPos.toShortString()
                    + " support=" + supportPos.toShortString()
                    + " supportDy=" + supportDy
                    + " supportAnchored=" + supportAnchored
                    + " targetDy=" + targetDy
                    + " missOutline=" + describeHit(missOutline)
                    + " native=" + describeHit(missNative)
                    + " hitOutline=" + describeHit(hitOutline)
                    + " activeOwner=" + activeOwner);

            if (Math.abs(targetDy + 0.5d) > 1.0e-6d
                    || Math.abs(supportDy + 0.5d) > 1.0e-6d
                    || !supportAnchored
                    || missOutline != null
                    || hitOutline == null) {
                throw new RuntimeException("RED: " + proofName
                        + " setup check failed for visible lowered slab MISS-angle owner gap proof"
                        + " target=" + targetPos.toShortString()
                        + " support=" + supportPos.toShortString()
                        + " targetDy=" + targetDy
                        + " supportDy=" + supportDy
                        + " supportAnchored=" + supportAnchored
                        + " missOutline=" + describeHit(missOutline)
                        + " hitOutline=" + describeHit(hitOutline));
            }

            if (activeRetarget == null || !activeRetarget.getBlockPos().equals(targetPos)) {
                throw new RuntimeException("RED: " + proofName
                        + " visible lowered slab MISS-angle owner gap"
                        + " expectedOwner=" + targetPos.toShortString()
                        + " actual=" + activeOwner
                        + " ray=MISS-angle eye=" + missAngleEye
                        + " end=" + missAngleEnd
                        + " outline=" + describeHit(missOutline)
                        + " nativeRaycast=" + describeHit(missNative));
            }

            System.out.println("[GREEN] " + proofName
                    + " visible lowered slab MISS-angle owner gap"
                    + " expectedOwner=" + targetPos.toShortString()
                    + " actual=" + activeOwner
                    + " ray=MISS-angle eye=" + missAngleEye
                    + " end=" + missAngleEnd
                    + " hitAngleOutline=" + describeHit(hitOutline));
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
        singleplayer.getServer().runOnServer(server -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                return;
            }
            var player = server.getPlayerManager().getPlayerList().get(0);
            player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            player.setVelocity(Vec3d.ZERO);
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.player == null) {
                throw new RuntimeException(PREFIX + " client not ready to sync phase19 camera");
            }
            mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            mc.player.setVelocity(Vec3d.ZERO);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.player == null) {
                throw new RuntimeException(PREFIX + " client not ready to finish phase19 camera sync");
            }
            mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            mc.player.setVelocity(Vec3d.ZERO);
        });
    }

    private static void waitForPlacementSync(ClientGameTestContext ctx) {
        for (int i = 0; i < 3; i++) {
            ctx.waitTick();
        }
    }

    private static String describeHit(HitResult hit) {
        if (hit instanceof BlockHitResult blockHit) {
            return "pos=" + blockHit.getBlockPos().toShortString()
                    + " face=" + blockHit.getSide().asString()
                    + " hit=" + blockHit.getPos();
        }
        return hit == null ? "null" : hit.getType().name();
    }
}
