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
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

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

    private Beta35LiveTorchCaptureRecorder() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("slabbed.beta35LiveTorchCapture")
                || Boolean.getBoolean("slabbed.beta35FloorTorchSbsbsSourceTruthRed");
    }

    private static boolean sourceTruthCaptureEnabled() {
        return Boolean.getBoolean("slabbed.beta35FloorTorchSbsbsSourceTruthRed");
    }

    public static void recordFrame(World world, Entity camera, PlayerEntity player, HitResult crosshairTarget, float tickProgress) {
        if (!enabled() || world == null || crosshairTarget == null) {
            return;
        }

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

    private record FoundTorch(BlockPos pos, double distance, boolean found, String source) {}
}
