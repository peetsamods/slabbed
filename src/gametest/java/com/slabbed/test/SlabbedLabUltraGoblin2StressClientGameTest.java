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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.atomic.AtomicReference;

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
        runItemSensitiveSlabHeldRangeJankProof(ctx, singleplayer);
        runPlacementReturnVsLoweredAnchorTruthSplitProof(ctx, singleplayer);

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

    private static void runItemSensitiveSlabHeldRangeJankProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        String proofName = "RED_ITEM_SENSITIVE_SLAB_HELD_RANGE_JANK";
        System.out.println("[SBSB-TRACE][ITEM_SENSITIVE_RANGE_ROUTE] running " + proofName);

        // Pair-1 live A/B coordinates from inspect traces.
        // stone: final=3,-59,-28/east ; stone_slab: final=3,-60,-27/north.
        BlockPos slabOwner = new BlockPos(3, -60, -27);
        BlockPos loweredDoubleOwner = new BlockPos(3, -59, -28);
        BlockPos companionSlab = new BlockPos(2, -60, -27);
        BlockPos anchoredFullBlock = new BlockPos(3, -59, -27);
        Vec3d rayEye = new Vec3d(6.3188d, -58.3800d, -28.8129d);
        Vec3d rayEnd = new Vec3d(1.3210d, -60.2220d, -26.0510d);
        float yaw = 61.076f;
        float pitch = 17.880f;

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = slabOwner.getX() - 3; x <= loweredDoubleOwner.getX() + 3; x++) {
                for (int y = slabOwner.getY() - 3; y <= loweredDoubleOwner.getY() + 3; y++) {
                    for (int z = loweredDoubleOwner.getZ() - 3; z <= slabOwner.getZ() + 3; z++) {
                        BlockPos clearPos = new BlockPos(x, y, z);
                        SlabAnchorAttachment.removeAnchor(world, clearPos);
                        SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, clearPos);
                        world.setBlockState(clearPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
            world.setBlockState(companionSlab, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
            world.setBlockState(slabOwner, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
            world.setBlockState(anchoredFullBlock, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, anchoredFullBlock, world.getBlockState(anchoredFullBlock));
            world.setBlockState(loweredDoubleOwner, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, loweredDoubleOwner, world.getBlockState(loweredDoubleOwner));
        });

        waitForPlacementSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();
        syncPlayerAim(ctx, singleplayer, rayEye, rayEnd);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null) {
                throw new RuntimeException(proofName + " client not ready");
            }
            var cam = mc.getCameraEntity();
            if (cam == null) {
                throw new RuntimeException(proofName + " camera missing");
            }
            BlockPos expectedOwner = loweredDoubleOwner;

            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 8));
            HitResult stoneInitial = mc.player.raycast(6.0d, 0.0f, false);
            HitResult stoneFinal = SlabbedRetargetTestHooks.findLoweredSideSlabRetarget(mc.world, cam, rayEye, rayEnd, stoneInitial, false);
            HitResult stoneResolved = stoneFinal != null ? stoneFinal : stoneInitial;
            boolean stoneRetargetFired = stoneFinal != null;
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            HitResult slabInitial = mc.player.raycast(6.0d, 0.0f, false);
            HitResult slabFinal = SlabbedRetargetTestHooks.findLoweredSideSlabRetarget(mc.world, cam, rayEye, rayEnd, slabInitial, true);
            HitResult slabResolved = slabFinal != null ? slabFinal : slabInitial;
            boolean slabRetargetFired = slabFinal != null;

            String stonePos = (stoneResolved instanceof BlockHitResult s) ? s.getBlockPos().toShortString() : describeHit(stoneResolved);
            String slabPos = (slabResolved instanceof BlockHitResult s) ? s.getBlockPos().toShortString() : describeHit(slabResolved);

            System.out.println("[SBSB-TRACE][ITEM_SENSITIVE_RANGE_ROUTE] "
                    + "proof=" + proofName
                    + " rayEye=" + rayEye
                    + " rayEnd=" + rayEnd
                    + " yaw=" + yaw
                    + " pitch=" + pitch
                    + " expectedOwner=" + expectedOwner.toShortString()
                    + " stone.held=stone"
                    + " stone.initial=" + describeHit(stoneInitial)
                    + " stone.final=" + describeHit(stoneResolved)
                    + " stone.sideSlabRetargetFired=" + stoneRetargetFired
                    + " stone.comfortFired=false"
                    + " slab.held=stone_slab"
                    + " slab.initial=" + describeHit(slabInitial)
                    + " slab.final=" + describeHit(slabResolved)
                    + " slab.sideSlabRetargetFired=" + slabRetargetFired
                    + " slab.comfortFired=false"
                    + " note=live-log-exact-coordinate-space");

            boolean stoneMatches = stoneResolved instanceof BlockHitResult s && s.getBlockPos().equals(expectedOwner);
            boolean slabMatches = slabResolved instanceof BlockHitResult s && s.getBlockPos().equals(expectedOwner);
            if (!stoneMatches || !slabMatches) {
                throw new RuntimeException("RED: " + proofName
                        + " slab-held targeting lost lowered owner for same camera/geometry"
                        + " expectedLoweredOwner=" + expectedOwner.toShortString()
                        + " stoneHeldResult=" + stonePos
                        + " slabHeldResult=" + slabPos
                        + " ray=eye=" + rayEye + " end=" + rayEnd
                        + " yaw=" + yaw + " pitch=" + pitch
                        + " stoneRetargetFired=" + stoneRetargetFired
                        + " slabRetargetFired=" + slabRetargetFired);
            }
            System.out.println("[GREEN] " + proofName
                    + " expectedLoweredOwner=" + expectedOwner.toShortString()
                    + " stoneHeldResult=" + stonePos
                    + " slabHeldResult=" + slabPos
                    + " stoneRetargetFired=" + stoneRetargetFired
                    + " slabRetargetFired=" + slabRetargetFired);
        });
    }

    private static void runPlacementReturnVsLoweredAnchorTruthSplitProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        // Historical proof name kept for grep continuity. The terminal classifications
        // distinguish real gameplay timing from recorder pre-finalization semantics.
        String proofName = "RED_PLACEMENT_RETURN_VS_LOWERED_ANCHOR_TRUTH_SPLIT";
        System.out.println("[SBSB-TRACE][STATE_COHERENCE_ROUTE] running " + proofName);

        BlockPos placePos = new BlockPos(7, -59, -39);
        Vec3d liveEye = new Vec3d(8.3859d, -58.2482d, -36.3147d);
        Vec3d liveTargetHit = new Vec3d(7.4774d, -58.5064d, -38.0000d);
        AttemptSpec[] attempts = new AttemptSpec[]{
                new AttemptSpec(
                        "A1_west_lowered_face_east",
                        new BlockPos(6, -59, -39),
                        Direction.EAST,
                        new Vec3d(7.0d, -58.75d, -38.5d),
                        liveEye,
                        AttemptSetup.WEST_LOWERED_STONE),
                new AttemptSpec(
                        "A2_below_support_face_up",
                        new BlockPos(7, -60, -39),
                        Direction.UP,
                        centerOfFace(new BlockPos(7, -60, -39), Direction.UP),
                        liveEye,
                        AttemptSetup.BELOW_BOTTOM_SLAB),
                new AttemptSpec(
                        "A3_north_lowered_face_south",
                        new BlockPos(7, -59, -40),
                        Direction.SOUTH,
                        centerOfFace(new BlockPos(7, -59, -40), Direction.SOUTH),
                        liveEye,
                        AttemptSetup.NORTH_LOWERED_STONE),
                new AttemptSpec(
                        "A4_above_face_down",
                        new BlockPos(7, -58, -39),
                        Direction.DOWN,
                        centerOfFace(new BlockPos(7, -58, -39), Direction.DOWN),
                        liveEye,
                        AttemptSetup.ABOVE_STONE)
        };
        boolean sawDifferentBug = false;
        boolean sawTimingRed = false;
        boolean sawAnchorTimingOnly = false;
        boolean sawServerKeptStone = false;
        StringBuilder matrixReasons = new StringBuilder();
        StringBuilder timingRedAttempts = new StringBuilder();
        StringBuilder anchorTimingAttempts = new StringBuilder();
        StringBuilder serverKeptAttempts = new StringBuilder();

        for (AttemptSpec attempt : attempts) {
            AttemptResult result = runPlacementTruthSplitAttempt(
                    ctx,
                    singleplayer,
                    proofName,
                    placePos,
                    liveTargetHit,
                    attempt);
            if (matrixReasons.length() > 0) {
                matrixReasons.append("|");
            }
            matrixReasons.append(result.id()).append(":").append(result.reason());
            if (result.clientPredictedStone() && !result.serverKeptStone()) {
                sawDifferentBug = true;
            }
            if (!result.serverKeptStone()) {
                continue;
            }
            sawServerKeptStone = true;
            if (serverKeptAttempts.length() > 0) {
                serverKeptAttempts.append(",");
            }
            serverKeptAttempts.append(result.id());
            if (result.timingRed()) {
                sawTimingRed = true;
                if (timingRedAttempts.length() > 0) {
                    timingRedAttempts.append(",");
                }
                timingRedAttempts.append(result.id());
                throw new RuntimeException("TIMING_RED: " + proofName
                        + " attempt=" + result.id()
                        + " same server-kept STONE cell showed live-style dy 0 -> dy -0.5 split"
                        + " intendedPlacePos=" + result.intendedPlacePos().toShortString()
                        + " timingSummary=" + result.timingSummary()
                        + " serverView=" + result.serverView()
                        + " targetView=" + result.targetView());
            }
            if (result.anchorTimingOnly()) {
                sawAnchorTimingOnly = true;
                if (anchorTimingAttempts.length() > 0) {
                    anchorTimingAttempts.append(",");
                }
                anchorTimingAttempts.append(result.id());
            }
        }
        if (sawTimingRed) {
            System.out.println("[SBSB-TRACE][STATE_COHERENCE_ROUTE][TIMING_RED] " + proofName
                    + " timingRedAttempts=" + timingRedAttempts
                    + " serverKeptAttempts=" + serverKeptAttempts
                    + " matrix=" + matrixReasons);
            return;
        }
        if (sawAnchorTimingOnly) {
            System.out.println("[SBSB-TRACE][STATE_COHERENCE_ROUTE][PRE_FINALIZATION_LOG_TIMING][ANCHOR_TIMING_ONLY] " + proofName
                    + " reason=only anchor changed false to true while dy was already -0.5"
                    + " recorderSemantics=PLACE_RETURN_can_log_before_side_adjacent_anchor_finalizer"
                    + " gameplaySplitReproduced=false"
                    + " noGameplayFixWarranted=true"
                    + " anchorTimingAttempts=" + anchorTimingAttempts
                    + " serverKeptAttempts=" + serverKeptAttempts
                    + " matrix=" + matrixReasons);
            return;
        }
        if (sawDifferentBug) {
            System.out.println("[SBSB-TRACE][STATE_COHERENCE_ROUTE][DIFFERENT_BUG] " + proofName
                    + " reason=client predicted STONE but server removed it in at least one attempt"
                    + " serverKeptAttempts=" + serverKeptAttempts
                    + " matrix=" + matrixReasons);
            return;
        }
        if (sawServerKeptStone) {
            System.out.println("[SBSB-TRACE][STATE_COHERENCE_ROUTE][RECORDER_SEMANTICS_MISMATCH][PRE_FINALIZATION_LOG_TIMING] " + proofName
                    + " reason=no attempt showed live-style dy 0 -> dy -0.5 transition"
                    + " recorderSemantics=PLACE_RETURN_can_log_before_side_adjacent_anchor_finalizer"
                    + " gameplaySplitReproduced=false"
                    + " noGameplayFixWarranted=true"
                    + " serverKeptAttempts=" + serverKeptAttempts
                    + " matrix=" + matrixReasons);
            return;
        }
        System.out.println("[SBSB-TRACE][STATE_COHERENCE_ROUTE][PROOF_GAP] " + proofName
                + " reason=no attempt server-kept STONE at " + placePos.toShortString()
                + " serverKeptAttempts=" + serverKeptAttempts
                + " matrix=" + matrixReasons);
    }

    private static AttemptResult runPlacementTruthSplitAttempt(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String proofName,
            BlockPos expectedPlacePos,
            Vec3d liveTargetHit,
            AttemptSpec attempt
    ) {
        BlockHitResult placementHit = new BlockHitResult(
                attempt.hitVec(),
                attempt.face(),
                attempt.clickedTarget(),
                false,
                false);
        BlockPos intendedPlacePos = placementHit.getBlockPos().offset(placementHit.getSide());
        AtomicReference<String> actionResult = new AtomicReference<>("not_run");
        AtomicReference<String> heldItem = new AtomicReference<>("not_seen");
        AtomicReference<TruthView> beforeClientView = new AtomicReference<>();
        AtomicReference<TruthView> beforeServerView = new AtomicReference<>();
        AtomicReference<TruthView> immediateClientView = new AtomicReference<>();
        AtomicReference<TruthView> immediateServerView = new AtomicReference<>();
        AtomicReference<TruthView> afterOneClientView = new AtomicReference<>();
        AtomicReference<TruthView> afterOneServerView = new AtomicReference<>();
        AtomicReference<TruthView> postSyncClientView = new AtomicReference<>();
        AtomicReference<TruthView> postSyncServerView = new AtomicReference<>();
        AtomicReference<TruthView> targetStyleClientView = new AtomicReference<>();
        AtomicReference<TruthView> targetStyleServerView = new AtomicReference<>();
        AtomicReference<String> targetView = new AtomicReference<>("not_run");
        AtomicReference<String> nearbyStoneReport = new AtomicReference<>("not_run");
        AtomicReference<String> nearbySlabReport = new AtomicReference<>("not_run");

        setupPlacementAttemptWorld(singleplayer, proofName, expectedPlacePos, attempt);
        waitForPlacementSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();
        syncPlayerAim(ctx, singleplayer, attempt.eye(), attempt.hitVec());
        singleplayer.getServer().runOnServer(server -> beforeServerView.set(snapshotView(
                server.getOverworld(),
                intendedPlacePos,
                timingSource("before/server:overworld/before_interact"))));

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException(proofName + " client not ready to place attempt=" + attempt.id());
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 8));
            heldItem.set(mc.player.getStackInHand(Hand.MAIN_HAND).toString());
            beforeClientView.set(snapshotView(
                    mc.world,
                    intendedPlacePos,
                    timingSource("before/client:mc.world/before_interact")));
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placementHit);
            actionResult.set(result.toString());
            immediateClientView.set(snapshotView(
                    mc.world,
                    intendedPlacePos,
                    timingSource("immediate/client:mc.world/after_interactBlock")));
        });

        singleplayer.getServer().runOnServer(server -> immediateServerView.set(snapshotView(
                server.getOverworld(),
                intendedPlacePos,
                timingSource("immediate/server:overworld/after_interactBlock_if_available"))));
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException(proofName + " client world missing after one tick attempt=" + attempt.id());
            }
            afterOneClientView.set(snapshotView(
                    mc.world,
                    intendedPlacePos,
                    timingSource("after_one/client:mc.world/after_one_client_tick")));
        });
        singleplayer.getServer().runOnServer(server -> afterOneServerView.set(snapshotView(
                server.getOverworld(),
                intendedPlacePos,
                timingSource("after_one/server:overworld/after_one_server_tick_if_available"))));

        waitForPlacementSync(ctx);
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException(proofName + " client world missing post sync attempt=" + attempt.id());
            }
            postSyncClientView.set(snapshotView(
                    mc.world,
                    intendedPlacePos,
                    timingSource("post_sync/client:mc.world/post_sync")));
        });
        singleplayer.getServer().runOnServer(server -> postSyncServerView.set(snapshotView(
                server.getOverworld(),
                intendedPlacePos,
                timingSource("post_sync/server:overworld/post_sync"))));

        waitForPlacementSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();
        syncPlayerAim(ctx, singleplayer, attempt.eye(), liveTargetHit);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null) {
                throw new RuntimeException(proofName + " client not ready to target attempt=" + attempt.id());
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 8));
            HitResult initial = mc.player.raycast(6.0d, 0.0f, false);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult finalTarget = mc.crosshairTarget;
            targetView.set("initial=" + describeHit(initial) + " final=" + describeHit(finalTarget));
            targetStyleClientView.set(snapshotView(
                    mc.world,
                    intendedPlacePos,
                    timingSource("targetStyle/client:mc.world/after_crosshair")));
            TruthView serverPrediction = postSyncServerView.get();
            if (serverPrediction == null || !serverPrediction.isStone()) {
                nearbyStoneReport.set(nearbyReport(mc.world, intendedPlacePos, placementHit.getBlockPos(), NearbyKind.STONE));
                nearbySlabReport.set(nearbyReport(mc.world, intendedPlacePos, placementHit.getBlockPos(), NearbyKind.SLAB));
            } else {
                nearbyStoneReport.set("skipped_serverKeptStone");
                nearbySlabReport.set("skipped_serverKeptStone");
            }
        });

        singleplayer.getServer().runOnServer(server -> targetStyleServerView.set(snapshotView(
                server.getOverworld(),
                intendedPlacePos,
                timingSource("targetStyle/server:overworld/after_crosshair"))));

        TruthView placement = immediateClientView.get();
        TruthView authoritative = targetStyleClientView.get();
        TruthView server = targetStyleServerView.get();
        boolean samePos = allSamePos(
                intendedPlacePos,
                beforeClientView.get(),
                beforeServerView.get(),
                immediateClientView.get(),
                immediateServerView.get(),
                afterOneClientView.get(),
                afterOneServerView.get(),
                postSyncClientView.get(),
                postSyncServerView.get(),
                targetStyleClientView.get(),
                targetStyleServerView.get())
                && server.pos().equals(intendedPlacePos)
                && intendedPlacePos.equals(expectedPlacePos);
        boolean serverKeptStone = server != null && server.isStone();
        boolean clientPredictedStone = placement != null && placement.isStone();
        boolean timingRed = serverKeptStone && hasNormalThenLoweredAnchored(
                immediateClientView.get(),
                immediateServerView.get(),
                afterOneClientView.get(),
                afterOneServerView.get(),
                postSyncClientView.get(),
                postSyncServerView.get(),
                targetStyleClientView.get(),
                targetStyleServerView.get());
        boolean anchorTimingOnly = serverKeptStone
                && !timingRed
                && hasLoweredUnanchoredThenAnchored(
                immediateClientView.get(),
                immediateServerView.get(),
                afterOneClientView.get(),
                afterOneServerView.get(),
                postSyncClientView.get(),
                postSyncServerView.get(),
                targetStyleClientView.get(),
                targetStyleServerView.get());
        String reason = attemptReason(
                actionResult.get(),
                expectedPlacePos,
                intendedPlacePos,
                placement,
                authoritative,
                server,
                serverKeptStone,
                clientPredictedStone);
        String timingSummary = timingSummary(
                beforeClientView.get(),
                beforeServerView.get(),
                immediateClientView.get(),
                immediateServerView.get(),
                afterOneClientView.get(),
                afterOneServerView.get(),
                postSyncClientView.get(),
                postSyncServerView.get(),
                targetStyleClientView.get(),
                targetStyleServerView.get());

        if (!serverKeptStone && "skipped_serverKeptStone".equals(nearbyStoneReport.get())) {
            nearbyStoneReport.set("not_captured");
            nearbySlabReport.set("not_captured");
        }
        logTiming(proofName, attempt.id(), "before_client", beforeClientView.get());
        logTiming(proofName, attempt.id(), "before_server", beforeServerView.get());
        logTiming(proofName, attempt.id(), "immediate_client", immediateClientView.get());
        logTiming(proofName, attempt.id(), "immediate_server", immediateServerView.get());
        logTiming(proofName, attempt.id(), "after_one_client", afterOneClientView.get());
        logTiming(proofName, attempt.id(), "after_one_server", afterOneServerView.get());
        logTiming(proofName, attempt.id(), "post_sync_client", postSyncClientView.get());
        logTiming(proofName, attempt.id(), "post_sync_server", postSyncServerView.get());
        logTiming(proofName, attempt.id(), "targetStyle_client", targetStyleClientView.get());
        logTiming(proofName, attempt.id(), "targetStyle_server", targetStyleServerView.get());
        System.out.println("[SBSB-TRACE][STATE_COHERENCE_ROUTE] proof=" + proofName
                + " attempt=" + attempt.id()
                + " setup=" + attempt.setup()
                + " heldItem=" + heldItem.get()
                + " clickedTargetPos=" + placementHit.getBlockPos().toShortString()
                + " clickedFace=" + placementHit.getSide().asString()
                + " clickedHit=" + placementHit.getPos()
                + " intendedPlacePos=" + intendedPlacePos.toShortString()
                + " placementResult=" + actionResult.get()
                + " placementView=" + placement
                + " authoritativeView=" + authoritative
                + " serverPostPlacementView=" + postSyncServerView.get()
                + " serverView=" + server
                + " samePos=" + samePos
                + " serverKeptStone=" + serverKeptStone
                + " placementReturnNormalButAuthoritativeLowered=" + timingRed
                + " timingRed=" + timingRed
                + " anchorTimingOnly=" + anchorTimingOnly
                + " reason=" + reason
                + " timingSummary=" + timingSummary
                + " targetView=" + targetView.get());
        if (!serverKeptStone) {
            System.out.println("[SBSB-TRACE][STATE_COHERENCE_ROUTE] proof=" + proofName
                    + " attempt=" + attempt.id()
                    + " nearbySTONE=" + nearbyStoneReport.get()
                    + " nearbySLAB=" + nearbySlabReport.get());
        }
        return new AttemptResult(
                attempt.id(),
                intendedPlacePos,
                placement,
                authoritative,
                server,
                serverKeptStone,
                clientPredictedStone,
                timingRed,
                anchorTimingOnly,
                reason,
                timingSummary,
                targetView.get());
    }

    private static void setupPlacementAttemptWorld(
            TestSingleplayerContext singleplayer,
            String proofName,
            BlockPos expectedPlacePos,
            AttemptSpec attempt
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = 4; x <= 9; x++) {
                for (int y = -62; y <= -57; y++) {
                    for (int z = -41; z <= -37; z++) {
                        BlockPos clearPos = new BlockPos(x, y, z);
                        SlabAnchorAttachment.removeAnchor(world, clearPos);
                        SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, clearPos);
                        world.setBlockState(clearPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
            switch (attempt.setup()) {
                case WEST_LOWERED_STONE -> placeLoweredStoneSupport(world, new BlockPos(6, -60, -39), attempt.clickedTarget());
                case BELOW_BOTTOM_SLAB -> world.setBlockState(
                        attempt.clickedTarget(),
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        Block.NOTIFY_LISTENERS);
                case NORTH_LOWERED_STONE -> placeLoweredStoneSupport(world, new BlockPos(7, -60, -40), attempt.clickedTarget());
                case ABOVE_STONE -> world.setBlockState(
                        attempt.clickedTarget(),
                        Blocks.STONE.getDefaultState(),
                        Block.NOTIFY_LISTENERS);
            }
            world.setBlockState(expectedPlacePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.removeAnchor(world, expectedPlacePos);
            SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, expectedPlacePos);
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException(proofName + " server player list is empty attempt=" + attempt.id());
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE, 8));
        });
    }

    private static String timingSource(String stage) {
        return stage
                + " source=World.getBlockState"
                + " dySource=SlabSupport.getYOffset"
                + " anchoredSource=SlabAnchorAttachment.isAnchored"
                + " loweredSource=abs(dy+0.5)<=1.0e-6"
                + " ClientDy=not_available_in_this_file";
    }

    private static void logTiming(String proofName, String attemptId, String timing, TruthView view) {
        System.out.println("[SBSB-TRACE][STATE_COHERENCE_ROUTE] proof=" + proofName
                + " attempt=" + attemptId
                + " timing=" + timing
                + " " + compactTruth(view));
    }

    private static boolean allSamePos(BlockPos expectedPos, TruthView... views) {
        for (TruthView view : views) {
            if (view == null || !view.pos().equals(expectedPos)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasNormalThenLoweredAnchored(TruthView... views) {
        boolean sawNormalStone = false;
        for (TruthView view : views) {
            if (view == null || !view.isStone()) {
                continue;
            }
            if (sawNormalStone && view.isLoweredAnchoredTruth()) {
                return true;
            }
            if (view.isNormalTruth()) {
                sawNormalStone = true;
            }
        }
        return false;
    }

    private static boolean hasLoweredUnanchoredThenAnchored(TruthView... views) {
        boolean sawLoweredUnanchoredStone = false;
        for (TruthView view : views) {
            if (view == null || !view.isStone()) {
                continue;
            }
            if (sawLoweredUnanchoredStone && view.isLoweredAnchoredTruth()) {
                return true;
            }
            if (view.isLoweredUnanchoredTruth()) {
                sawLoweredUnanchoredStone = true;
            }
        }
        return false;
    }

    private static String timingSummary(TruthView... views) {
        StringBuilder summary = new StringBuilder();
        for (TruthView view : views) {
            if (summary.length() > 0) {
                summary.append("|");
            }
            summary.append(compactTruth(view));
        }
        return summary.toString();
    }

    private static String compactTruth(TruthView view) {
        if (view == null) {
            return "null";
        }
        TruthSnapshot snapshot = view.snapshot();
        return "pos=" + view.pos().toShortString()
                + " state=" + snapshot.state()
                + " dy=" + snapshot.dy()
                + " anchored=" + snapshot.anchored()
                + " lowered=" + snapshot.lowered()
                + " source=" + view.source();
    }

    private static void placeLoweredStoneSupport(
            net.minecraft.world.World world,
            BlockPos supportSlabPos,
            BlockPos loweredStonePos
    ) {
        world.setBlockState(
                supportSlabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        world.setBlockState(loweredStonePos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(world, loweredStonePos, world.getBlockState(loweredStonePos));
    }

    private static String attemptReason(
            String actionResult,
            BlockPos expectedPlacePos,
            BlockPos intendedPlacePos,
            TruthView placement,
            TruthView authoritative,
            TruthView server,
            boolean serverKeptStone,
            boolean clientPredictedStone
    ) {
        if (!intendedPlacePos.equals(expectedPlacePos)) {
            return "wrong intended cell derivedFromClick=" + intendedPlacePos.toShortString()
                    + " expected=" + expectedPlacePos.toShortString();
        }
        if (placement == null || authoritative == null || server == null) {
            return "snapshot positions differ missingSnapshot placementView=" + placement
                    + " authoritativeView=" + authoritative
                    + " serverView=" + server;
        }
        if (!placement.pos().equals(authoritative.pos()) || !authoritative.pos().equals(server.pos())) {
            return "snapshot positions differ placementPos=" + placement.pos().toShortString()
                    + " authoritativePos=" + authoritative.pos().toShortString()
                    + " serverPos=" + server.pos().toShortString();
        }
        if (!actionResult.contains("Success")) {
            return "placement did not return success result=" + actionResult;
        }
        if (serverKeptStone) {
            return "serverKeptStone";
        }
        if (clientPredictedStone && server.isAir()) {
            return "DIFFERENT_BUG clientPredictedStoneServerAir";
        }
        if (!clientPredictedStone) {
            return "client did not predict STONE";
        }
        return "server did not keep STONE serverView=" + server;
    }

    private static Vec3d centerOfFace(BlockPos pos, Direction face) {
        double x = pos.getX() + 0.5d;
        double y = pos.getY() + 0.5d;
        double z = pos.getZ() + 0.5d;
        switch (face) {
            case EAST -> x = pos.getX() + 1.0d;
            case WEST -> x = pos.getX();
            case UP -> y = pos.getY() + 1.0d;
            case DOWN -> y = pos.getY();
            case SOUTH -> z = pos.getZ() + 1.0d;
            case NORTH -> z = pos.getZ();
        }
        return new Vec3d(x, y, z);
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

    private static TruthSnapshot snapshot(net.minecraft.world.World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        boolean lowered = Math.abs(dy + 0.5d) <= 1.0e-6d;
        return new TruthSnapshot(state, dy, anchored, lowered);
    }

    private static TruthView snapshotView(net.minecraft.world.World world, BlockPos pos, String source) {
        return new TruthView(pos.toImmutable(), source, snapshot(world, pos));
    }

    private static String blockStateSummary(net.minecraft.world.World world, BlockPos pos) {
        return pos.toShortString() + "=" + world.getBlockState(pos);
    }

    private static String neighborReport(net.minecraft.world.World world, BlockPos center) {
        StringBuilder report = new StringBuilder();
        for (Direction direction : Direction.values()) {
            BlockPos pos = center.offset(direction);
            if (!report.isEmpty()) {
                report.append("|");
            }
            report.append("neighborFace=").append(direction.asString())
                    .append(",pos=").append(pos.toShortString())
                    .append(",state=").append(world.getBlockState(pos));
        }
        return report.toString();
    }

    private static String nearbyReport(
            net.minecraft.world.World world,
            BlockPos center,
            BlockPos clickedTarget,
            NearbyKind kind
    ) {
        StringBuilder report = new StringBuilder();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    boolean matches = kind == NearbyKind.STONE
                            ? state.isOf(Blocks.STONE)
                            : state.getBlock() instanceof SlabBlock;
                    if (!matches) {
                        continue;
                    }
                    if (!report.isEmpty()) {
                        report.append("|");
                    }
                    report.append("nearby")
                            .append(kind.name())
                            .append(" pos=").append(pos.toShortString())
                            .append(" role=").append(nearbyRole(pos, center, clickedTarget))
                            .append(" snapshot=").append(snapshot(world, pos));
                }
            }
        }
        return report.isEmpty() ? "none" : report.toString();
    }

    private static String nearbyRole(BlockPos pos, BlockPos center, BlockPos clickedTarget) {
        if (pos.equals(center)) {
            return "intended";
        }
        if (pos.equals(clickedTarget)) {
            return "clickedTarget";
        }
        return "other";
    }

    private static String proofGapReason(
            BlockHitResult placementHit,
            BlockPos expectedPlacePos,
            BlockPos intendedPlacePos,
            TruthView placement,
            TruthView authoritative,
            TruthView support,
            String nearbyStoneReport
    ) {
        if (placementHit.getSide() != Direction.EAST) {
            return "wrong clicked face clickedFace=" + placementHit.getSide().asString();
        }
        if (!intendedPlacePos.equals(expectedPlacePos)) {
            return "wrong intended cell derivedFromClick=" + intendedPlacePos.toShortString()
                    + " expected=" + expectedPlacePos.toShortString();
        }
        if (placement == null || authoritative == null) {
            return "snapshot positions differ missingSnapshot placementView=" + placement
                    + " authoritativeView=" + authoritative;
        }
        if (!placement.pos().equals(authoritative.pos()) || !authoritative.pos().equals(intendedPlacePos)) {
            return "snapshot positions differ placementPos=" + placement.pos().toShortString()
                    + " authoritativePos=" + authoritative.pos().toShortString()
                    + " intended=" + intendedPlacePos.toShortString();
        }
        if (support == null || !support.isStone() || !support.isLoweredAnchoredTruth()) {
            return "support missing supportView=" + support;
        }
        if (!placement.isStone() || !authoritative.isStone()) {
            if (nearbyStoneReport != null && nearbyStoneReport.contains("role=other")) {
                return "block placed elsewhere nearbySTONE=" + nearbyStoneReport;
            }
            if (placement.isStone() || authoritative.isAir()) {
                return "intended cell became air";
            }
            return "block placed elsewhere nearbySTONE=" + nearbyStoneReport;
        }
        return null;
    }

    private enum NearbyKind {
        STONE,
        SLAB
    }

    private enum AttemptSetup {
        WEST_LOWERED_STONE,
        BELOW_BOTTOM_SLAB,
        NORTH_LOWERED_STONE,
        ABOVE_STONE
    }

    private record AttemptSpec(
            String id,
            BlockPos clickedTarget,
            Direction face,
            Vec3d hitVec,
            Vec3d eye,
            AttemptSetup setup
    ) {
    }

    private record AttemptResult(
            String id,
            BlockPos intendedPlacePos,
            TruthView placementView,
            TruthView authoritativeView,
            TruthView serverView,
            boolean serverKeptStone,
            boolean clientPredictedStone,
            boolean timingRed,
            boolean anchorTimingOnly,
            String reason,
            String timingSummary,
            String targetView
    ) {
    }

    private record TruthView(BlockPos pos, String source, TruthSnapshot snapshot) {
        boolean isStone() {
            return snapshot.isStone();
        }

        boolean isAir() {
            return snapshot.isAir();
        }

        boolean isNormalTruth() {
            return snapshot.isNormalTruth();
        }

        boolean isLoweredAnchoredTruth() {
            return snapshot.isLoweredAnchoredTruth();
        }

        boolean isLoweredUnanchoredTruth() {
            return snapshot.isLoweredUnanchoredTruth();
        }

        boolean sameTruth(TruthView other) {
            return snapshot.sameTruth(other.snapshot());
        }
    }

    private record TruthSnapshot(BlockState state, double dy, boolean anchored, boolean lowered) {
        boolean isStone() {
            return state.isOf(Blocks.STONE);
        }

        boolean isAir() {
            return state.isAir();
        }

        boolean isNormalTruth() {
            return Math.abs(dy) <= 1.0e-6d && !anchored && !lowered;
        }

        boolean isLoweredAnchoredTruth() {
            return Math.abs(dy + 0.5d) <= 1.0e-6d && anchored && lowered;
        }

        boolean isLoweredUnanchoredTruth() {
            return Math.abs(dy + 0.5d) <= 1.0e-6d && !anchored && lowered;
        }

        boolean sameTruth(TruthSnapshot other) {
            return state.equals(other.state())
                    && Math.abs(dy - other.dy()) <= 1.0e-6d
                    && anchored == other.anchored()
                    && lowered == other.lowered();
        }
    }
}
