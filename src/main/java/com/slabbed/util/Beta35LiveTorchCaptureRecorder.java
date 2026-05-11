package com.slabbed.util;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.Slabbed;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.TorchBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

import java.util.HashSet;
import java.util.Set;

/**
 * Evidence-only recorder for Beta 3.5 floor-torch live mismatch diagnostics.
 */
public final class Beta35LiveTorchCaptureRecorder {
    private static final int LOG_INTERVAL_TICKS = 20;
    private static final int SEARCH_RADIUS = 2;
    private static final double CONTACT_GAP_EPSILON = 1.0e-6d;

    private static long lastLogWorldTime = Long.MIN_VALUE;
    private static String lastTargetSignature = "n/a";
    private static boolean startLogged;
    private static boolean dualStartLogged;
    private static long placementSequence;
    private static ComfortEvent lastComfortEvent;

    private Beta35LiveTorchCaptureRecorder() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("slabbed.beta35LiveTorchCapture")
                || dualTraceEnabled()
                || Boolean.getBoolean("slabbed.beta35FloorTorchSbsbsSourceTruthRed");
    }

    public static boolean dualTraceEnabled() {
        return Boolean.getBoolean("slabbed.beta35LiveTorchDualTrace");
    }

    private static boolean sourceTruthCaptureEnabled() {
        return Boolean.getBoolean("slabbed.beta35FloorTorchSbsbsSourceTruthRed");
    }

    public static void recordFrame(World world, Entity camera, PlayerEntity player, HitResult crosshairTarget, float tickProgress) {
        if (!enabled() || world == null || crosshairTarget == null) {
            return;
        }

        logDualStartIfNeeded(world, player);

        String signature = targetSignature(world, crosshairTarget, player);
        long now = world.getTime();
        boolean targetChanged = !signature.equals(lastTargetSignature);
        if (!targetChanged && now - lastLogWorldTime < LOG_INTERVAL_TICKS) {
            return;
        }

        lastTargetSignature = signature;
        lastLogWorldTime = now;

        if (!startLogged) {
            Slabbed.LOGGER.info(
                    "[JULIA_BETA35_LIVE_TORCH_CAPTURE_START] enabled={} world={} player={}",
                    true,
                    world.getRegistryKey().getValue(),
                    player == null ? "null" : player.getName().getString()
            );
            startLogged = true;
        }

        CaptureContext ctx = capture(world, crosshairTarget, player, camera);
        boolean sourceTruthCapture = sourceTruthCaptureEnabled();
        boolean supportCarrier = sourceTruthCapture && ctx.supportCarrier();
        boolean supportCompoundUpper = sourceTruthCapture && ctx.supportCompoundUpper();
        boolean supportCompoundLower = sourceTruthCapture && ctx.supportCompoundLower();
        boolean supportHasBottomSlabBelow = sourceTruthCapture && ctx.supportHasBottomSlabBelow();
        boolean supportAnchoredFullBlockBelow = sourceTruthCapture && ctx.supportAnchoredFullBlockBelow();
        boolean isVanillaPosition = sourceTruthCapture && ctx.isVanillaPosition();
        Slabbed.LOGGER.info(
                "[JULIA_BETA35_LIVE_TORCH_CAPTURE] markerVersion=2"
                        + " measurementFormulaVersion=" + ctx.measurementFormula()
                        + " tickProgress=" + String.format("%.3f", tickProgress)
                        + " classification=" + ctx.classification()
                        + " targetType=" + ctx.targetType()
                        + " targetPos=" + ctx.targetPos()
                        + " targetFace=" + ctx.targetFace()
                        + " targetState=" + ctx.targetState()
                        + " torchPos=" + ctx.torchPos()
                        + " nearestTorchPos=" + ctx.nearestTorchPos()
                        + " searchRadius=" + ctx.searchRadius()
                        + " torchState=" + ctx.torchState()
                        + " supportCandidatePos=" + ctx.supportCandidatePos()
                        + " supportCandidateState=" + ctx.supportCandidateState()
                        + " torchDy=" + ctx.torchDy()
                        + " supportDy=" + ctx.supportDy()
                        + " rawSupportTopY=" + ctx.rawSupportTopY()
                        + " supportVisibleTopY=" + ctx.supportVisibleTopY()
                        + " rawTorchShapeMinY=" + ctx.rawTorchShapeMinY()
                        + " torchModelBottomY=" + ctx.torchModelBottomY()
                        + " contactGap=" + ctx.contactGap()
                        + " contactGapV1=" + ctx.contactGapV1()
                        + " outlineMinY=" + ctx.outlineMinY()
                        + " outlineMaxY=" + ctx.outlineMaxY()
                        + " raycastMinY=" + ctx.raycastMinY()
                        + " raycastMaxY=" + ctx.raycastMaxY()
                        + " supportCarrier=" + supportCarrier
                        + " supportCompoundVisibleUpper=" + supportCompoundUpper
                        + " supportCompoundVisibleLower=" + supportCompoundLower
                        + " supportHasBottomSlabBelow=" + supportHasBottomSlabBelow
                        + " supportAnchoredFullBlockBelow=" + supportAnchoredFullBlockBelow
                        + " isVanillaPosition=" + isVanillaPosition
                        + " heldItem=" + ctx.heldItem()
                        + " playerPos=" + ctx.playerPos()
                        + " playerYaw=" + ctx.playerYaw()
                        + " playerPitch=" + ctx.playerPitch()
                        + " cameraPos=" + ctx.cameraPos()
                        + " worldCoords=" + ctx.worldCoords()
        );

        if (sourceTruthCapture) {
            Slabbed.LOGGER.info(
                    "[JULIA_BETA35_LIVE_TORCH_CAPTURE_SOURCE_TRUTH]"
                            + " supportPos=" + ctx.supportCandidatePos()
                            + " supportState=" + ctx.supportCandidateState()
                            + " supportDy=" + ctx.supportDy()
                            + " supportCarrier=" + supportCarrier
                            + " isCompoundVisibleSideUpperSlab=" + supportCompoundUpper
                            + " isCompoundVisibleSideLowerSlab=" + supportCompoundLower
                            + " hasBottomSlabBelow=" + supportHasBottomSlabBelow
                            + " anchoredFullBlockBelow=" + supportAnchoredFullBlockBelow
                            + " torchPos=" + ctx.torchPos()
                            + " torchDy=" + ctx.torchDy()
                            + " contactGap=" + ctx.contactGap()
                            + " targetType=" + ctx.targetType()
                            + " targetPos=" + ctx.targetPos()
                            + " targetFace=" + ctx.targetFace()
                            + " targetState=" + ctx.targetState()
                            + " heldItem=" + ctx.heldItem()
            );
        }
        if (dualTraceEnabled()) {
            Slabbed.LOGGER.info(existingContactLogLine(world, crosshairTarget, player, camera));
        }
    }

    public static PlacementAttemptSnapshot startPlacementAttempt(
            World world,
            PlayerEntity player,
            ItemStack heldStack,
            BlockHitResult hitResult,
            HitResult crosshairTarget
    ) {
        if (!dualTraceEnabled() || world == null || player == null || !isHeldFloorTorch(heldStack)) {
            return null;
        }
        logDualStartIfNeeded(world, player);
        long seq = ++placementSequence;
        BlockPos targetPos = hitResult == null ? null : hitResult.getBlockPos();
        Direction side = hitResult == null ? null : hitResult.getSide();
        BlockPos intendedTorchPos = intendedTorchPos(targetPos, side);
        BlockPos supportCandidatePos = intendedSupportPos(targetPos, side, intendedTorchPos);
        return new PlacementAttemptSnapshot(
                seq,
                world.getTime(),
                formatItem(heldStack),
                hitResult == null ? "null" : "BLOCK",
                shortPos(targetPos),
                side == null ? "n/a" : side.name(),
                hitResult == null ? "n/a" : formatVec(hitResult.getPos()),
                hitResult == null ? "n/a" : formatDouble(hitResult.getPos().y),
                player == null ? "n/a" : formatVec(player.getEyePos()),
                shortPos(intendedTorchPos),
                shortPos(supportCandidatePos),
                supportCandidatePos == null ? "n/a" : formatState(world.getBlockState(supportCandidatePos)),
                supportCandidatePos == null ? "n/a" : formatDouble(SlabSupport.getYOffset(world, supportCandidatePos, world.getBlockState(supportCandidatePos))),
                supportCandidatePos == null ? "n/a" : supportSourceType(world, supportCandidatePos, world.getBlockState(supportCandidatePos)),
                crosshairTargetType(crosshairTarget),
                crosshairTargetPos(crosshairTarget),
                recentComfortEvent(world),
                floorTorchPositionsAround(world, targetPos == null ? player.getBlockPos() : targetPos, SEARCH_RADIUS)
        );
    }

    public static void finishPlacementAttempt(World world, PlacementAttemptSnapshot snapshot, ActionResult result) {
        if (!dualTraceEnabled() || world == null || snapshot == null) {
            return;
        }
        TorchAppearance appearance = findNewOrNearbyTorch(world, snapshot);
        String resultText = String.valueOf(result);
        String classification = classifyPlacementAttempt(snapshot, resultText, appearance);
        ComfortEvent comfort = snapshot.comfortEvent();
        Slabbed.LOGGER.info(
                "[JULIA_BETA35_LIVE_TORCH_PLACEMENT_ATTEMPT]"
                        + " sequence=" + snapshot.sequence()
                        + " tick=" + snapshot.worldTime()
                        + " heldItem=" + snapshot.heldItem()
                        + " crosshairTargetType=" + snapshot.crosshairTargetType()
                        + " crosshairTargetBlockPos=" + snapshot.crosshairTargetPos()
                        + " interactTargetType=" + snapshot.interactTargetType()
                        + " interactTargetBlockPos=" + snapshot.interactTargetPos()
                        + " targetSide=" + snapshot.targetSide()
                        + " hitVec=" + snapshot.hitVec()
                        + " hitY=" + snapshot.hitY()
                        + " playerEyePos=" + snapshot.playerEyePos()
                        + " intendedTorchPos=" + snapshot.intendedTorchPos()
                        + " intendedSupportCandidatePos=" + snapshot.intendedSupportCandidatePos()
                        + " intendedSupportCandidateState=" + snapshot.intendedSupportCandidateState()
                        + " intendedSupportSourceType=" + snapshot.intendedSupportSourceType()
                        + " intendedSupportDy=" + snapshot.intendedSupportDy()
                        + " loweredSideSlabComfortPathFired=" + (comfort != null)
                        + " comfortReason=" + (comfort == null ? "n/a" : comfort.reason())
                        + " comfortPos=" + (comfort == null ? "n/a" : shortPos(comfort.pos()))
                        + " comfortSupportPos=" + (comfort == null ? "n/a" : shortPos(comfort.supportPos()))
                        + " finalInteractResult=" + resultText
                        + " torchBlockAppearedAfterAttempt=" + appearance.appeared()
                        + " appearedTorchPos=" + appearance.pos()
                        + " appearedTorchState=" + appearance.state()
                        + " classification=" + classification
        );
        Slabbed.LOGGER.info(
                "[JULIA_BETA35_LIVE_TORCH_DUAL_SUMMARY]"
                        + " sequence=" + snapshot.sequence()
                        + " placementClassification=" + classification
                        + " existingContactClassification=" + existingContactClassification(world, appearance.blockPos())
                        + " beta35ReleaseStatus=PAUSED_JULIA_LIVE_TRACE_REQUIRED"
                        + " scope=floor_torch_only"
        );
    }

    public static void recordComfortTrace(World world, String reason, BlockPos pos, BlockPos supportPos) {
        if (!dualTraceEnabled() || world == null) {
            return;
        }
        lastComfortEvent = new ComfortEvent(world.getTime(), reason, pos == null ? null : pos.toImmutable(),
                supportPos == null ? null : supportPos.toImmutable());
    }

    private static CaptureContext capture(World world, HitResult crosshairTarget, PlayerEntity player, Entity cameraEntity) {
        if (crosshairTarget.getType() != HitResult.Type.BLOCK || !(crosshairTarget instanceof BlockHitResult blockHit)) {
            return noTorchTarget(crosshairTarget.getType().name(), player);
        }

        BlockPos targetPos = blockHit.getBlockPos();
        BlockState targetState = world.getBlockState(targetPos);
        String targetFace = blockHit.getSide() == null ? "n/a" : blockHit.getSide().name();
        String targetType = "BLOCK";
        String targetStateText = formatState(targetState);
        String heldItem = player == null ? "n/a" : formatItem(player.getMainHandStack());

        if (isTorch(targetState)) {
            return captureTorch(world, targetPos, true, targetType, targetFace, targetStateText, heldItem, player, cameraEntity);
        }

        FoundTorch nearestTorch = findNearestTorch(world, targetPos, SEARCH_RADIUS);
        if (!nearestTorch.found()) {
            return noTorchTarget(
                    captureTorchTargetText(targetType, targetPos, targetFace, targetStateText, heldItem),
                    player
            );
        }

        return captureTorch(
                world,
                nearestTorch.pos(),
                false,
                targetType,
                targetFace,
                targetStateText,
                heldItem,
                player,
                cameraEntity
        ).withNearestTorch(nearestTorch.pos(), nearestTorch.distance());
    }

    private static CaptureContext captureTorch(
            World world,
            BlockPos torchPos,
            boolean targetIsTorch,
            String targetType,
            String targetFace,
            String targetStateText,
            String heldItem,
            PlayerEntity player,
            Entity cameraEntity
    ) {
        BlockState torchState = world.getBlockState(torchPos);
        BlockPos supportCandidatePos = torchPos.down();
        BlockState supportCandidateState = world.getBlockState(supportCandidatePos);

        double torchDy = SlabSupport.getYOffset(world, torchPos, torchState);
        double supportDy = SlabSupport.getYOffset(world, supportCandidatePos, supportCandidateState);
        double supportTopOffset = SlabSupport.isSupportingSlab(supportCandidateState)
                ? SlabSupport.getSupportYOffset(supportCandidateState)
                : 1.0d;
        double rawSupportTopY = supportCandidatePos.getY() + supportTopOffset;
        double supportVisibleTopY = rawSupportTopY + supportDy;
        double supportVisibleTopYV1 = supportCandidatePos.getY() + 1.0d + supportDy;

        double torchModelBottomY = Double.NaN;
        double outlineMinY = Double.NaN;
        double outlineMaxY = Double.NaN;
        double raycastMinY = Double.NaN;
        double raycastMaxY = Double.NaN;
        double contactGap = Double.NaN;
        double rawTorchShapeMinY = Double.NaN;
        double contactGapV1 = Double.NaN;

        VoxelShape outlineShape = safeOutlineShape(world, torchPos, torchState, cameraEntity);
        if (outlineShape != null && !outlineShape.isEmpty()) {
            double outlineMin = shapeMinY(outlineShape);
            double outlineMax = shapeMaxY(outlineShape);
            rawTorchShapeMinY = outlineMin;
            outlineMinY = torchPos.getY() + outlineMin;
            outlineMaxY = torchPos.getY() + outlineMax;
            torchModelBottomY = outlineMinY;
        }

        VoxelShape raycastShape = safeRaycastShape(world, torchPos, torchState);
        if (raycastShape != null && !raycastShape.isEmpty()) {
            raycastMinY = torchPos.getY() + shapeMinY(raycastShape);
            raycastMaxY = torchPos.getY() + shapeMaxY(raycastShape);
        }

        if (Double.isFinite(torchModelBottomY) && Double.isFinite(supportVisibleTopY)) {
            contactGap = torchModelBottomY - supportVisibleTopY;
        }
        if (Double.isFinite(rawTorchShapeMinY) && Double.isFinite(supportVisibleTopYV1)) {
            contactGapV1 = (torchPos.getY() + rawTorchShapeMinY + torchDy) - supportVisibleTopYV1;
        }

        String classification = targetIsTorch
                ? classificationForGap(contactGap)
                : "TORCH_FOUND_NEAR_TARGET";
        if (Double.isFinite(contactGap) && !targetIsTorch) {
            if (Math.abs(contactGap) > CONTACT_GAP_EPSILON) {
                classification = "CONTACT_GAP";
            }
        }
        if (classification == null || classification.isBlank()) {
            classification = "UNKNOWN";
        }

        boolean supportCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                world, supportCandidatePos, supportCandidateState);
        boolean supportCompoundUpper = SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(
                world, supportCandidatePos, supportCandidateState);
        boolean supportCompoundLower = SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(
                world, supportCandidatePos, supportCandidateState);
        boolean supportHasBottomSlabBelow = SlabSupport.hasBottomSlabBelow(world, supportCandidatePos);
        boolean supportAnchoredFullBlockBelow = SlabAnchorAttachment.isAnchored(world, supportCandidatePos.down());
        boolean isVanillaPosition = targetIsTorch && Math.abs(torchDy) < CONTACT_GAP_EPSILON;

        return new CaptureContext(
                classification,
                targetType,
                "n/a",
                targetFace,
                targetStateText,
                targetIsTorch ? shortPos(torchPos) : "n/a",
                "n/a",
                String.valueOf(SEARCH_RADIUS),
                formatState(torchState),
                shortPos(supportCandidatePos),
                formatState(supportCandidateState),
                formatDouble(torchDy),
                formatDouble(supportDy),
                formatDouble(supportVisibleTopY),
                formatDouble(torchModelBottomY),
                formatDouble(contactGap),
                formatDouble(outlineMinY),
                formatDouble(outlineMaxY),
                formatDouble(raycastMinY),
                formatDouble(raycastMaxY),
                heldItem,
                player == null ? "n/a" : formatVec(player.getX(), player.getY(), player.getZ()),
                player == null ? "n/a" : formatDouble(player.getYaw()),
                player == null ? "n/a" : formatDouble(player.getPitch()),
                cameraEntity == null ? "n/a" : formatVec(cameraEntity.getX(), cameraEntity.getY(), cameraEntity.getZ()),
                world.getRegistryKey().getValue().toString(),
                "v2",
                formatDouble(rawSupportTopY),
                formatDouble(supportVisibleTopYV1),
                formatDouble(rawTorchShapeMinY),
                formatDouble(contactGapV1),
                supportCarrier,
                supportCompoundUpper,
                supportCompoundLower,
                supportHasBottomSlabBelow,
                supportAnchoredFullBlockBelow,
                isVanillaPosition
        );
    }

    private static String classificationForGap(double contactGap) {
        if (!Double.isFinite(contactGap)) {
            return "UNKNOWN";
        }
        return Math.abs(contactGap) > CONTACT_GAP_EPSILON ? "CONTACT_GAP" : "LIVE_CAPTURE_OK";
    }

    private static String existingContactLogLine(World world, HitResult crosshairTarget, PlayerEntity player, Entity camera) {
        if (world == null) {
            return "[JULIA_BETA35_LIVE_TORCH_EXISTING_CONTACT] classification=NO_TORCH_NEAR_TARGET";
        }
        BlockPos center = player == null ? null : player.getBlockPos();
        if (crosshairTarget instanceof BlockHitResult blockHit) {
            center = blockHit.getBlockPos();
        }
        FoundTorch nearestTorch = center == null ? new FoundTorch(null, Double.NaN, false, "none")
                : findNearestFloorTorch(world, center, SEARCH_RADIUS);
        if (!nearestTorch.found()) {
            return "[JULIA_BETA35_LIVE_TORCH_EXISTING_CONTACT]"
                    + " torchPos=n/a torchState=n/a torchSourceType=n/a torchDy=n/a"
                    + " supportCandidatePos=n/a supportCandidateState=n/a supportSourceType=n/a supportDy=n/a"
                    + " supportVisibleTopY=n/a torchModelBottomY=n/a contactGap=n/a"
                    + " modelMinY=n/a modelMaxY=n/a outlineMinY=n/a outlineMaxY=n/a raycastMinY=n/a raycastMaxY=n/a"
                    + " triadCoLocated=no classification=NO_TORCH_NEAR_TARGET";
        }
        return existingContactLogLineForTorch(world, nearestTorch.pos(), camera);
    }

    private static String existingContactLogLineForTorch(World world, BlockPos torchPos, Entity camera) {
        ContactMeasurement measurement = measureContact(world, torchPos, camera);
        return "[JULIA_BETA35_LIVE_TORCH_EXISTING_CONTACT]"
                + " torchPos=" + shortPos(torchPos)
                + " torchState=" + measurement.torchState()
                + " torchSourceType=" + measurement.torchSourceType()
                + " torchDy=" + measurement.torchDy()
                + " supportCandidatePos=" + measurement.supportCandidatePos()
                + " supportCandidateState=" + measurement.supportCandidateState()
                + " supportSourceType=" + measurement.supportSourceType()
                + " supportDy=" + measurement.supportDy()
                + " supportVisibleTopY=" + measurement.supportVisibleTopY()
                + " torchModelBottomY=" + measurement.torchModelBottomY()
                + " contactGap=" + measurement.contactGap()
                + " modelMinY=" + measurement.modelMinY()
                + " modelMaxY=" + measurement.modelMaxY()
                + " outlineMinY=" + measurement.outlineMinY()
                + " outlineMaxY=" + measurement.outlineMaxY()
                + " raycastMinY=" + measurement.raycastMinY()
                + " raycastMaxY=" + measurement.raycastMaxY()
                + " triadCoLocated=" + measurement.triadCoLocated()
                + " classification=" + measurement.classification();
    }

    private static ContactMeasurement measureContact(World world, BlockPos torchPos, Entity camera) {
        if (world == null || torchPos == null) {
            return ContactMeasurement.empty("NO_TORCH_NEAR_TARGET");
        }
        BlockState torchState = world.getBlockState(torchPos);
        if (!isFloorTorch(torchState)) {
            return ContactMeasurement.empty("NO_TORCH_NEAR_TARGET");
        }
        BlockPos supportCandidatePos = torchPos.down();
        BlockState supportCandidateState = world.getBlockState(supportCandidatePos);
        if (supportCandidateState == null || supportCandidateState.isAir()) {
            return ContactMeasurement.emptyForTorch(world, torchPos, torchState, "NO_SUPPORT_CANDIDATE");
        }

        double torchDy = SlabSupport.getYOffset(world, torchPos, torchState);
        double supportDy = SlabSupport.getYOffset(world, supportCandidatePos, supportCandidateState);
        double supportTopOffset = SlabSupport.isSupportingSlab(supportCandidateState)
                ? SlabSupport.getSupportYOffset(supportCandidateState)
                : 1.0d;
        double rawSupportTopY = supportCandidatePos.getY() + supportTopOffset;
        double supportVisibleTopY = rawSupportTopY + supportDy;

        VoxelShape outlineShape = safeOutlineShape(world, torchPos, torchState, camera);
        VoxelShape raycastShape = safeRaycastShape(world, torchPos, torchState);
        double outlineMinY = shapeWorldMinY(torchPos, outlineShape);
        double outlineMaxY = shapeWorldMaxY(torchPos, outlineShape);
        double raycastMinY = shapeWorldMinY(torchPos, raycastShape);
        double raycastMaxY = shapeWorldMaxY(torchPos, raycastShape);
        double modelMinY = outlineMinY;
        double modelMaxY = outlineMaxY;
        double torchModelBottomY = modelMinY;
        double contactGap = Double.isFinite(torchModelBottomY)
                ? torchModelBottomY - supportVisibleTopY
                : Double.NaN;
        boolean triad = close(modelMinY, outlineMinY)
                && close(modelMaxY, outlineMaxY)
                && close(outlineMinY, raycastMinY)
                && close(outlineMaxY, raycastMaxY);
        String classification;
        if (!Double.isFinite(contactGap)) {
            classification = "WRONG_SOURCE_TYPE";
        } else if (Math.abs(contactGap) > CONTACT_GAP_EPSILON) {
            classification = "PLACED_CONTACT_GAP";
        } else {
            classification = "PLACED_CONTACT_GREEN";
        }
        return new ContactMeasurement(
                formatState(torchState),
                "FLOOR_TORCH",
                formatDouble(torchDy),
                shortPos(supportCandidatePos),
                formatState(supportCandidateState),
                supportSourceType(world, supportCandidatePos, supportCandidateState),
                formatDouble(supportDy),
                formatDouble(supportVisibleTopY),
                formatDouble(torchModelBottomY),
                formatDouble(contactGap),
                formatDouble(modelMinY),
                formatDouble(modelMaxY),
                formatDouble(outlineMinY),
                formatDouble(outlineMaxY),
                formatDouble(raycastMinY),
                formatDouble(raycastMaxY),
                triad ? "yes" : "no",
                classification
        );
    }

    private static String existingContactClassification(World world, BlockPos torchPos) {
        return measureContact(world, torchPos, null).classification();
    }

    private static void logDualStartIfNeeded(World world, PlayerEntity player) {
        if (!dualTraceEnabled() || dualStartLogged) {
            return;
        }
        Slabbed.LOGGER.info("[JULIA_BETA35_LIVE_TORCH_DUAL_TRACE] enabled=true world={} player={}",
                world == null ? "n/a" : world.getRegistryKey().getValue(),
                player == null ? "null" : player.getName().getString());
        dualStartLogged = true;
    }

    private static String classifyPlacementAttempt(
            PlacementAttemptSnapshot snapshot,
            String resultText,
            TorchAppearance appearance
    ) {
        ComfortEvent comfort = snapshot.comfortEvent();
        if (appearance.appeared()) {
            return "PLACEMENT_ATTEMPT_OK";
        }
        if (comfort != null && comfort.reason().contains("no-box-intersection")) {
            return "COMFORT_NO_BOX_INTERSECTION";
        }
        if (comfort != null && comfort.reason().contains("owner")) {
            return "WRONG_TARGET_OWNER";
        }
        if ("MISS".equals(snapshot.crosshairTargetType()) || "n/a".equals(snapshot.crosshairTargetPos())) {
            return "PLACEMENT_TARGET_MISS";
        }
        if (resultText != null && resultText.contains("FAIL")) {
            return "PLACEMENT_REJECTED";
        }
        return "PLACEMENT_RESULT_UNKNOWN";
    }

    private static boolean isHeldFloorTorch(ItemStack stack) {
        return stack != null && stack.isOf(Items.TORCH);
    }

    private static boolean isFloorTorch(BlockState state) {
        return state != null && state.isOf(Blocks.TORCH);
    }

    private static BlockPos intendedTorchPos(BlockPos targetPos, Direction side) {
        if (targetPos == null || side == null) {
            return null;
        }
        return targetPos.offset(side);
    }

    private static BlockPos intendedSupportPos(BlockPos targetPos, Direction side, BlockPos intendedTorchPos) {
        if (side == Direction.UP) {
            return targetPos;
        }
        return intendedTorchPos == null ? targetPos : intendedTorchPos.down();
    }

    private static Set<String> floorTorchPositionsAround(World world, BlockPos center, int radius) {
        Set<String> positions = new HashSet<>();
        if (world == null || center == null) {
            return positions;
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos candidate = center.add(dx, dy, dz);
                    if (isFloorTorch(world.getBlockState(candidate))) {
                        positions.add(candidate.toShortString());
                    }
                }
            }
        }
        return positions;
    }

    private static TorchAppearance findNewOrNearbyTorch(World world, PlacementAttemptSnapshot snapshot) {
        BlockPos center = parseShortPos(snapshot.intendedTorchPos());
        if (center == null) {
            center = parseShortPos(snapshot.interactTargetPos());
        }
        FoundTorch nearest = center == null ? new FoundTorch(null, Double.NaN, false, "none")
                : findNearestFloorTorch(world, center, SEARCH_RADIUS);
        if (!nearest.found()) {
            return new TorchAppearance(false, null, "n/a", "n/a");
        }
        String shortPos = nearest.pos().toShortString();
        boolean appeared = !snapshot.preTorchPositions().contains(shortPos);
        return new TorchAppearance(appeared, nearest.pos(), shortPos, formatState(world.getBlockState(nearest.pos())));
    }

    private static FoundTorch findNearestFloorTorch(WorldView world, BlockPos targetPos, int radius) {
        double bestSqDist = Double.POSITIVE_INFINITY;
        BlockPos bestPos = null;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos candidate = targetPos.add(dx, dy, dz);
                    BlockState candidateState = world.getBlockState(candidate);
                    if (!isFloorTorch(candidateState)) {
                        continue;
                    }
                    double sq = blockCenterDistanceSq(candidate, targetPos);
                    if (sq < bestSqDist) {
                        bestSqDist = sq;
                        bestPos = candidate.toImmutable();
                    }
                }
            }
        }
        return new FoundTorch(bestPos, bestSqDist, bestPos != null, "floor_torch_search");
    }

    private static ComfortEvent recentComfortEvent(World world) {
        if (world == null || lastComfortEvent == null) {
            return null;
        }
        return world.getTime() - lastComfortEvent.worldTime() <= 5L ? lastComfortEvent : null;
    }

    private static String supportSourceType(World world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return "n/a";
        }
        if (state.isAir()) {
            return "AIR";
        }
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)) {
            return "PERSISTENT_LOWERED_SLAB_CARRIER";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)) {
            return "COMPOUND_VISIBLE_SIDE_LOWER_SLAB";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)) {
            return "COMPOUND_VISIBLE_SIDE_UPPER_SLAB";
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            return "ANCHORED_FULL_BLOCK";
        }
        return "PLAIN_STATE";
    }

    private static String crosshairTargetType(HitResult target) {
        return target == null ? "n/a" : target.getType().name();
    }

    private static String crosshairTargetPos(HitResult target) {
        return target instanceof BlockHitResult blockHit ? shortPos(blockHit.getBlockPos()) : "n/a";
    }

    private static double shapeWorldMinY(BlockPos pos, VoxelShape shape) {
        return shape == null || shape.isEmpty() ? Double.NaN : pos.getY() + shapeMinY(shape);
    }

    private static double shapeWorldMaxY(BlockPos pos, VoxelShape shape) {
        return shape == null || shape.isEmpty() ? Double.NaN : pos.getY() + shapeMaxY(shape);
    }

    private static boolean close(double a, double b) {
        return Double.isFinite(a) && Double.isFinite(b) && Math.abs(a - b) <= CONTACT_GAP_EPSILON;
    }

    private static String formatVec(Vec3d vec) {
        return vec == null ? "n/a" : formatVec(vec.x, vec.y, vec.z);
    }

    private static BlockPos parseShortPos(String value) {
        if (value == null || value.equals("n/a")) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String targetSignature(World world, HitResult crosshairTarget, PlayerEntity player) {
        if (crosshairTarget == null) {
            return "null";
        }
        if (crosshairTarget.getType() != HitResult.Type.BLOCK || !(crosshairTarget instanceof BlockHitResult blockHit)) {
            return crosshairTarget.getType() + "|" + (player == null ? "n/a" : player.getName().getString()) + "|" + crosshairTarget;
        }
        return "BLOCK|"
                + world.getRegistryKey().getValue()
                + "|" + blockHit.getBlockPos().toShortString()
                + "|" + blockHit.getSide();
    }

    private static boolean isTorch(BlockState state) {
        return state != null && (
                state.getBlock() instanceof TorchBlock
                        || state.isOf(Blocks.TORCH)
                        || state.isOf(Blocks.WALL_TORCH)
                        || state.isOf(Blocks.SOUL_TORCH)
                        || state.isOf(Blocks.SOUL_WALL_TORCH)
        );
    }

    private static FoundTorch findNearestTorch(WorldView world, BlockPos targetPos, int radius) {
        double bestSqDist = Double.POSITIVE_INFINITY;
        BlockPos bestPos = null;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos candidate = targetPos.add(dx, dy, dz);
                    BlockState candidateState = world.getBlockState(candidate);
                    if (!isTorch(candidateState)) {
                        continue;
                    }
                    double sq = blockCenterDistanceSq(candidate, targetPos);
                    if (sq < bestSqDist) {
                        bestSqDist = sq;
                        bestPos = candidate.toImmutable();
                    }
                }
            }
        }
        return new FoundTorch(bestPos, bestSqDist, bestPos != null, "search");
    }

    private static double blockCenterDistanceSq(BlockPos a, BlockPos b) {
        double dx = (double) a.getX() + 0.5d - ((double) b.getX() + 0.5d);
        double dy = (double) a.getY() + 0.5d - ((double) b.getY() + 0.5d);
        double dz = (double) a.getZ() + 0.5d - ((double) b.getZ() + 0.5d);
        return dx * dx + dy * dy + dz * dz;
    }

    private static VoxelShape safeOutlineShape(World world, BlockPos pos, BlockState state, Entity camera) {
        try {
            return state.getOutlineShape(world, pos, camera == null ? ShapeContext.absent() : ShapeContext.of(camera));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static VoxelShape safeRaycastShape(World world, BlockPos pos, BlockState state) {
        try {
            return state.getRaycastShape(world, pos);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static double shapeMinY(VoxelShape shape) {
        Box bounds = shape.getBoundingBox();
        return bounds.minY;
    }

    private static double shapeMaxY(VoxelShape shape) {
        Box bounds = shape.getBoundingBox();
        return bounds.maxY;
    }

    private static String formatState(BlockState state) {
        return state == null ? "null" : state.toString();
    }

    private static String formatItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        Item item = stack.getItem();
        Identifier id = Registries.ITEM.getId(item);
        return id == null ? "unknown" : id.toString();
    }

    private static String formatDouble(double value) {
        return Double.isFinite(value) ? String.format("%.6f", value) : "n/a";
    }

    private static String formatVec(double x, double y, double z) {
        return String.format("%.3f,%.3f,%.3f", x, y, z);
    }

    private static String shortPos(BlockPos pos) {
        return pos == null ? "n/a" : pos.toShortString();
    }

    private static String captureTorchTargetText(String targetType, BlockPos targetPos, String targetFace, String targetStateText, String heldItem) {
        return "classification=NO_TORCH_TARGET type=" + targetType + " targetPos=" + shortPos(targetPos)
                + " targetFace=" + targetFace + " targetState=" + targetStateText + " held=" + heldItem;
    }

    private static CaptureContext noTorchTarget(String details, PlayerEntity player) {
        return new CaptureContext(
                "NO_TORCH_TARGET",
                "MISS",
                "n/a",
                "n/a",
                details,
                "n/a",
                "n/a",
                String.valueOf(SEARCH_RADIUS),
                "n/a",
                "n/a",
                "n/a",
                "n/a",
                "n/a",
                "n/a",
                "n/a",
                "n/a",
                "n/a",
                "n/a",
                "n/a",
                "n/a",
                "n/a",
                player == null ? "n/a" : formatItem(player.getMainHandStack()),
                "n/a",
                "n/a",
                "n/a",
                "n/a",
                "v2",
                "n/a",
                "n/a",
                "n/a",
                "n/a",
                false,
                false,
                false,
                false,
                false,
                false
        );
    }

    private record CaptureContext(
            String classification,
            String targetType,
            String targetPos,
            String targetFace,
            String targetState,
            String torchPos,
            String nearestTorchPos,
            String searchRadius,
            String torchState,
            String supportCandidatePos,
            String supportCandidateState,
            String torchDy,
            String supportDy,
            String supportVisibleTopY,
            String torchModelBottomY,
            String contactGap,
            String outlineMinY,
            String outlineMaxY,
            String raycastMinY,
            String raycastMaxY,
            String heldItem,
            String playerPos,
            String playerYaw,
            String playerPitch,
            String cameraPos,
            String worldCoords,
            String measurementFormula,
            String rawSupportTopY,
            String supportVisibleTopYV1,
            String rawTorchShapeMinY,
            String contactGapV1,
            boolean supportCarrier,
            boolean supportCompoundUpper,
            boolean supportCompoundLower,
            boolean supportHasBottomSlabBelow,
            boolean supportAnchoredFullBlockBelow,
            boolean isVanillaPosition
    ) {
        CaptureContext withNearestTorch(BlockPos torchPos, double distance) {
            return new CaptureContext(
                    classification,
                    targetType,
                    targetPos,
                    targetFace,
                    targetState,
                    torchPos.toShortString(),
                    shortPos(torchPos),
                    String.valueOf(distance),
                    torchState,
                    supportCandidatePos,
                    supportCandidateState,
                    torchDy,
                    supportDy,
                    supportVisibleTopY,
                    torchModelBottomY,
                    contactGap,
                    outlineMinY,
                    outlineMaxY,
                    raycastMinY,
                    raycastMaxY,
                    heldItem,
                    playerPos,
                    playerYaw,
                    playerPitch,
                    cameraPos,
                    worldCoords,
                    measurementFormula,
                    rawSupportTopY,
                    supportVisibleTopYV1,
                    rawTorchShapeMinY,
                    contactGapV1,
                    supportCarrier,
                    supportCompoundUpper,
                    supportCompoundLower,
                    supportHasBottomSlabBelow,
                    supportAnchoredFullBlockBelow,
                    isVanillaPosition
            );
        }
    }

    public record PlacementAttemptSnapshot(
            long sequence,
            long worldTime,
            String heldItem,
            String interactTargetType,
            String interactTargetPos,
            String targetSide,
            String hitVec,
            String hitY,
            String playerEyePos,
            String intendedTorchPos,
            String intendedSupportCandidatePos,
            String intendedSupportCandidateState,
            String intendedSupportDy,
            String intendedSupportSourceType,
            String crosshairTargetType,
            String crosshairTargetPos,
            ComfortEvent comfortEvent,
            Set<String> preTorchPositions
    ) {}

    public record ComfortEvent(long worldTime, String reason, BlockPos pos, BlockPos supportPos) {}

    private record TorchAppearance(boolean appeared, BlockPos blockPos, String pos, String state) {}

    private record ContactMeasurement(
            String torchState,
            String torchSourceType,
            String torchDy,
            String supportCandidatePos,
            String supportCandidateState,
            String supportSourceType,
            String supportDy,
            String supportVisibleTopY,
            String torchModelBottomY,
            String contactGap,
            String modelMinY,
            String modelMaxY,
            String outlineMinY,
            String outlineMaxY,
            String raycastMinY,
            String raycastMaxY,
            String triadCoLocated,
            String classification
    ) {
        static ContactMeasurement empty(String classification) {
            return new ContactMeasurement(
                    "n/a", "n/a", "n/a", "n/a", "n/a", "n/a", "n/a", "n/a", "n/a",
                    "n/a", "n/a", "n/a", "n/a", "n/a", "n/a", "n/a", "no", classification);
        }

        static ContactMeasurement emptyForTorch(World world, BlockPos torchPos, BlockState torchState, String classification) {
            double torchDy = SlabSupport.getYOffset(world, torchPos, torchState);
            return new ContactMeasurement(
                    formatState(torchState),
                    "FLOOR_TORCH",
                    formatDouble(torchDy),
                    shortPos(torchPos.down()),
                    formatState(world.getBlockState(torchPos.down())),
                    "AIR",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "no",
                    classification);
        }
    }

    private record FoundTorch(BlockPos pos, double distance, boolean found, String source) {}
}
