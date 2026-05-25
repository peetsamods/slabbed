package com.slabbed.util;

import com.slabbed.Slabbed;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.Level;

/**
 * Evidence-only tracer for Julia's Beta 3.5 slab-height hit-acceptance gap.
 */
public final class Beta35SlabHeightHitAcceptanceRecorder {
    private static final String FLAG = "slabbed.beta35SlabHeightHitAcceptance";
    private static final double EPSILON = 1.0e-6d;
    private static final double SERVER_HIT_TOLERANCE = 1.0000001d;
    private static final int LOG_INTERVAL_TICKS = 10;

    private static boolean startupLogged;
    private static long lastNoTargetWorldTime = Long.MIN_VALUE;
    private static long lastSampleWorldTime = Long.MIN_VALUE;
    private static String lastSampleSignature = "n/a";

    private Beta35SlabHeightHitAcceptanceRecorder() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(FLAG);
    }

    public static void logStartup(Level world, Player player) {
        if (!enabled() || startupLogged) {
            return;
        }
        Slabbed.LOGGER.info(
                "[JULIA_BETA35_SLAB_HEIGHT_HIT_ACCEPTANCE] enabled=true phase=startup flag=-D{}=true world={} player={} scope=generic_held_item_visible_target diagnosticsOnly=true releaseAudit=NOT_RUN releaseTagMoved=false allItemClaim=false",
                FLAG,
                world == null ? "null" : world.getResourceKey().getValue(),
                player == null ? "null" : player.getName().getString());
        startupLogged = true;
    }

    public static void recordNoTarget(Level world, Player player, HitResult finalTarget) {
        if (!enabled()) {
            return;
        }
        logStartup(world, player);
        if (world == null || finalTarget != null) {
            return;
        }
        long now = world.getTime();
        if (now - lastNoTargetWorldTime < LOG_INTERVAL_TICKS) {
            return;
        }
        lastNoTargetWorldTime = now;
        Slabbed.LOGGER.info(
                "[JULIA_BETA35_SLAB_HEIGHT_HIT_ACCEPTANCE_SAMPLE] phase=client-tick classification=TRACE_ACTIVE_NO_TARGET failureLayer=TRACE_ACTIVE_NO_TARGET heldItem={} heldItemCategory={} finalTarget=null diagnosticsOnly=true",
                heldItemId(player == null ? ItemStack.EMPTY : player.getMainHandStack()),
                heldItemCategory(player == null ? ItemStack.EMPTY : player.getMainHandStack()));
    }

    public static void recordClientTarget(
            Level world,
            Entity camera,
            Player player,
            Vec3 eye,
            Vec3 rayEnd,
            ItemStack held,
            HitResult initialTarget,
            HitResult finalTarget,
            String finalDecision
    ) {
        if (!enabled()) {
            return;
        }
        logStartup(world, player);
        if (world == null || camera == null || eye == null || rayEnd == null) {
            return;
        }

        BlockPos visibleObjectPos = resolveVisibleObjectPos(world, initialTarget, finalTarget);
        if (visibleObjectPos == null) {
            logTraceActive(world, player, held, initialTarget, finalTarget, finalDecision);
            return;
        }

        BlockState visibleObjectState = world.getBlockState(visibleObjectPos);
        BlockPos supportCandidatePos = visibleObjectPos.down();
        BlockState supportCandidateState = world.getBlockState(supportCandidatePos);
        double objectDy = SlabSupport.getYOffset(world, visibleObjectPos, visibleObjectState);
        double supportDy = SlabSupport.getYOffset(world, supportCandidatePos, supportCandidateState);
        double targetDy = finalTarget instanceof BlockHitResult finalBlock
                ? SlabSupport.getYOffset(world, finalBlock.getBlockPos(), world.getBlockState(finalBlock.getBlockPos()))
                : Double.NaN;
        double supportVisibleTopY = supportVisibleTopY(supportCandidatePos, supportCandidateState, supportDy);
        CollisionContext shapeContext = player == null ? CollisionContext.absent() : CollisionContext.of(player);
        VoxelShape objectShape = visibleObjectState.getOutlineShape(world, visibleObjectPos, shapeContext);
        AABB objectAABB = worldAABB(objectShape, visibleObjectPos);
        boolean rayIntersectsVisibleObject = rayIntersectsShape(objectShape, visibleObjectPos, eye, rayEnd);
        double objectModelBottomY = objectAABB == null ? Double.NaN : objectAABB.minY;
        double contactGap = Double.isFinite(objectModelBottomY) && Double.isFinite(supportVisibleTopY)
                ? objectModelBottomY - supportVisibleTopY
                : Double.NaN;

        String visibleObjectCategory = visibleObjectCategory(visibleObjectState);
        String metricType = contactMetricType(visibleObjectState);
        double sideFaceGap = Double.NaN;
        double axisGap = Double.NaN;
        boolean finalIsObject = finalTarget instanceof BlockHitResult finalBlock
                && finalTarget.getType() == HitResult.Type.BLOCK
                && finalBlock.getBlockPos().equals(visibleObjectPos);
        boolean finalIsSupport = finalTarget instanceof BlockHitResult finalBlock
                && finalTarget.getType() == HitResult.Type.BLOCK
                && finalBlock.getBlockPos().equals(supportCandidatePos);
        boolean miss = finalTarget == null || finalTarget.getType() == HitResult.Type.MISS;
        String classification = classifyClientHit(
                finalIsObject,
                finalIsSupport,
                miss,
                visibleObjectCategory,
                metricType,
                rayIntersectsVisibleObject,
                contactGap,
                sideFaceGap,
                axisGap);
        String failureLayer = failureLayerFor(classification, visibleObjectCategory, metricType);
        String metricLayer = metricLayerFor(classification, metricType);
        String slabHeightCategory = slabHeightCategory(supportCandidateState, supportDy, objectDy);
        String targetOwner = finalIsObject ? "visible_object"
                : (finalIsSupport ? "support_slab" : (miss ? "MISS" : "unrelated_neighbor"));
        String signature = heldItemId(held) + "|" + formatHit(finalTarget) + "|" + visibleObjectPos.toShortString()
                + "|" + classification + "|" + slabHeightCategory + "|" + finalDecision;
        long now = world.getTime();
        if (signature.equals(lastSampleSignature) && now - lastSampleWorldTime < LOG_INTERVAL_TICKS) {
            return;
        }
        lastSampleSignature = signature;
        lastSampleWorldTime = now;

        Slabbed.LOGGER.info(
                "[JULIA_BETA35_SLAB_HEIGHT_HIT_ACCEPTANCE_SAMPLE]"
                        + " phase=client-crosshair classification=" + classification
                        + " failureLayer=" + failureLayer
                        + " heldItem=" + heldItemId(held)
                        + " heldItemCategory=" + heldItemCategory(held)
                        + " initialTarget=" + formatHit(initialTarget)
                        + " finalTarget=" + formatHit(finalTarget)
                        + " finalDecision=" + finalDecision
                        + " targetOwner=" + targetOwner
                        + " visibleObjectPos=" + visibleObjectPos.toShortString()
                        + " visibleObjectState=" + visibleObjectState
                        + " visibleObjectCategory=" + visibleObjectCategory
                        + " supportCandidatePos=" + supportCandidatePos.toShortString()
                        + " supportCandidateState=" + supportCandidateState
                        + " supportDy=" + fmt(supportDy)
                        + " targetDy=" + fmt(targetDy)
                        + " objectDy=" + fmt(objectDy)
                        + " slabHeightCategory=" + slabHeightCategory
                        + " metricType=" + metricType
                        + " metricLayer=" + metricLayer
                        + " rayIntersectsVisibleObject=" + rayIntersectsVisibleObject
                        + " contactGap=" + fmt(contactGap)
                        + " sideFaceGap=" + fmt(sideFaceGap)
                        + " axisGap=" + fmt(axisGap)
                        + " eye=" + formatVec(eye)
                        + " rayEnd=" + formatVec(rayEnd)
                        + " diagnosticsOnly=true releaseAudit=NOT_RUN releaseTagMoved=false allItemClaim=false");
    }

    public static void logServerTolerance(
            Level world,
            Player player,
            BlockHitResult hit,
            ItemStack held,
            Vec3 validationCenter,
            Vec3 shiftedValidationCenter
    ) {
        if (!enabled() || world == null || hit == null || validationCenter == null) {
            return;
        }
        logStartup(world, player);
        Vec3 hitVec = hit.getPos();
        Vec3 delta = hitVec.subtract(validationCenter);
        Vec3 shiftedDelta = shiftedValidationCenter == null ? null : hitVec.subtract(shiftedValidationCenter);
        boolean tooFar = Math.abs(delta.x) >= SERVER_HIT_TOLERANCE
                || Math.abs(delta.y) >= SERVER_HIT_TOLERANCE
                || Math.abs(delta.z) >= SERVER_HIT_TOLERANCE;
        boolean shiftedGreen = shiftedDelta != null
                && Math.abs(shiftedDelta.x) < SERVER_HIT_TOLERANCE
                && Math.abs(shiftedDelta.y) < SERVER_HIT_TOLERANCE
                && Math.abs(shiftedDelta.z) < SERVER_HIT_TOLERANCE;
        String classification = shiftedGreen ? "SERVER_SHIFTED_HIT_GREEN"
                : (tooFar ? "HIT_ACCEPTANCE_SERVER_REJECT" : "HIT_ACCEPTANCE_GREEN");
        BlockState targetState = world.getBlockState(hit.getBlockPos());
        double targetDy = SlabSupport.getBeta35ShiftedServerValidationYOffset(
                world, hit.getBlockPos(), targetState);
        String failureLayer = "NONE";
        if (tooFar && !shiftedGreen) {
            if (SlabSupport.isBeta35LoweredBottomTrapdoorServerHitTarget(
                    world, hit.getBlockPos(), targetState)) {
                failureLayer = "LOWERED_TRAPDOOR_SERVER_SHIFTED_VALIDATION_GAP";
            } else if (SlabSupport.isBeta35LoweredRegularDoorServerHitTarget(
                    world, hit.getBlockPos(), targetState)) {
                failureLayer = "REGULAR_DOOR_SERVER_SHIFTED_VALIDATION_GAP";
            } else {
                failureLayer = "HIT_ACCEPTANCE_SERVER_REJECT";
            }
        }
        Slabbed.LOGGER.info(
                "[JULIA_BETA35_SLAB_HEIGHT_HIT_ACCEPTANCE_SAMPLE] phase=server-hit classification={} failureLayer={} heldItem={} heldItemCategory={} packetBlockPos={} hitFace={} hitVec={} validationCenter={} validationDelta={} shiftedValidationCenter={} shiftedValidationDelta={} tolerance={} targetState={} targetDy={} slabHeightCategory={} diagnosticsOnly=true releaseAudit=NOT_RUN releaseTagMoved=false allItemClaim=false",
                classification,
                failureLayer,
                heldItemId(held),
                heldItemCategory(held),
                hit.getBlockPos().toShortString(),
                hit.getSide(),
                formatVec(hitVec),
                formatVec(validationCenter),
                formatVec(delta),
                formatVec(shiftedValidationCenter),
                formatVec(shiftedDelta),
                fmt(SERVER_HIT_TOLERANCE),
                targetState,
                fmt(targetDy),
                slabHeightCategory(targetState, targetDy, targetDy));
    }

    private static void logTraceActive(
            Level world,
            Player player,
            ItemStack held,
            HitResult initialTarget,
            HitResult finalTarget,
            String finalDecision
    ) {
        long now = world.getTime();
        if (now - lastNoTargetWorldTime < LOG_INTERVAL_TICKS) {
            return;
        }
        lastNoTargetWorldTime = now;
        Slabbed.LOGGER.info(
                "[JULIA_BETA35_SLAB_HEIGHT_HIT_ACCEPTANCE_SAMPLE] phase=client-crosshair classification=TRACE_ACTIVE_NO_TARGET failureLayer=TRACE_ACTIVE_NO_TARGET heldItem={} heldItemCategory={} initialTarget={} finalTarget={} finalDecision={} player={} diagnosticsOnly=true",
                heldItemId(held),
                heldItemCategory(held),
                formatHit(initialTarget),
                formatHit(finalTarget),
                finalDecision,
                player == null ? "null" : player.getName().getString());
    }

    private static BlockPos resolveVisibleObjectPos(Level world, HitResult initialTarget, HitResult finalTarget) {
        BlockPos finalPos = hitBlockPos(finalTarget);
        BlockPos finalAbove = visibleObjectAboveSupport(world, finalPos);
        if (finalAbove != null) {
            return finalAbove;
        }
        if (finalPos != null && !world.getBlockState(finalPos).isAir()) {
            return finalPos;
        }
        BlockPos initialPos = hitBlockPos(initialTarget);
        BlockPos initialAbove = visibleObjectAboveSupport(world, initialPos);
        if (initialAbove != null) {
            return initialAbove;
        }
        if (initialPos != null && !world.getBlockState(initialPos).isAir()) {
            return initialPos;
        }
        if (finalPos != null && !world.getBlockState(finalPos.up()).isAir()) {
            return finalPos.up();
        }
        if (initialPos != null && !world.getBlockState(initialPos.up()).isAir()) {
            return initialPos.up();
        }
        return null;
    }

    private static BlockPos visibleObjectAboveSupport(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        BlockPos abovePos = pos.up();
        BlockState aboveState = world.getBlockState(abovePos);
        if (aboveState.isAir() || SlabSupport.isSupportingSlab(aboveState)) {
            return null;
        }
        return SlabSupport.isSupportingSlab(state) ? abovePos : null;
    }

    private static BlockPos hitBlockPos(HitResult hit) {
        return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                ? blockHit.getBlockPos()
                : null;
    }

    private static String heldItemCategory(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "OTHER";
        }
        String id = heldItemId(stack);
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return id.contains("redstone") ? "REDSTONE" : "OTHER";
        }
        BlockState state = blockItem.getBlock().getDefaultState();
        if (state.getBlock() instanceof SlabBlock) {
            return "SLAB";
        }
        if (state.getBlock() instanceof StairBlock) {
            return "STAIRS";
        }
        if (state.getBlock() instanceof TrapDoorBlock) {
            return "TRAPDOOR";
        }
        if (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock) {
            return "FENCE_WALL";
        }
        if (state.getBlock() instanceof LanternBlock) {
            return "LANTERN";
        }
        if (state.getBlock() instanceof ChainBlock) {
            return "CHAIN";
        }
        if (id.contains("button")) {
            return "BUTTON";
        }
        if (state.getBlock() instanceof RedstoneTorchBlock || id.contains("redstone")) {
            return "REDSTONE";
        }
        if (state.isSolidBlock(null, BlockPos.ORIGIN)) {
            return "INERT_BLOCK";
        }
        return id.contains("torch") || id.contains("candle") || id.contains("flower_pot")
                ? "OTHER_ATTACHABLE"
                : "OTHER";
    }

    private static String visibleObjectCategory(BlockState state) {
        if (state == null) {
            return "OTHER";
        }
        if (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock) {
            return "FENCE_WALL";
        }
        if (state.isOf(Blocks.ANVIL)) {
            return "ANVIL";
        }
        if (state.getBlock() instanceof LanternBlock) {
            return "LANTERN";
        }
        if (state.getBlock() instanceof ChainBlock) {
            return "CHAIN";
        }
        if (BuiltInRegistries.BLOCK.getId(state.getBlock()).toString().contains("button")) {
            return "BUTTON";
        }
        return BuiltInRegistries.BLOCK.getId(state.getBlock()).toString();
    }

    private static String contactMetricType(BlockState state) {
        if (state == null) {
            return "OWNER_ONLY";
        }
        if (SlabSupport.isSupportingSlab(state) || state.getBlock() instanceof SlabBlock) {
            return "SUPPORT_TARGET";
        }
        if (state.getBlock() instanceof ChainBlock) {
            return "AXIS_CONTACT";
        }
        String id = BuiltInRegistries.BLOCK.getId(state.getBlock()).toString();
        if (id.contains("button")) {
            if (state.contains(BlockStateProperties.BLOCK_FACE)
                    && state.get(BlockStateProperties.BLOCK_FACE) == AttachFace.FLOOR) {
                return "FLOOR_CONTACT";
            }
            return "SIDE_FACE_CONTACT";
        }
        if (state.isOf(Blocks.TORCH) || state.isOf(Blocks.CANDLE) || state.isOf(Blocks.FLOWER_POT)) {
            return "FLOOR_CONTACT";
        }
        if (state.getBlock() instanceof TrapDoorBlock) {
            return "SIDE_FACE_CONTACT";
        }
        return "OWNER_ONLY";
    }

    private static boolean isRegularDoorCategory(String visibleObjectCategory) {
        return visibleObjectCategory != null
                && visibleObjectCategory.endsWith("_door")
                && !visibleObjectCategory.contains("trapdoor");
    }

    private static boolean isStairsCategory(String visibleObjectCategory) {
        return visibleObjectCategory != null && visibleObjectCategory.endsWith("_stairs");
    }

    private static String classifyClientHit(
            boolean finalIsObject,
            boolean finalIsSupport,
            boolean miss,
            String visibleObjectCategory,
            String metricType,
            boolean rayIntersectsVisibleObject,
            double contactGap,
            double sideFaceGap,
            double axisGap
    ) {
        if ("AXIS_CONTACT".equals(metricType) && "CHAIN".equals(visibleObjectCategory)) {
            if (finalIsObject) {
                return "CHAIN_AXIS_METRIC_ONLY_DEFERRED";
            }
            if (!rayIntersectsVisibleObject) {
                return "CHAIN_AXIS_MISS";
            }
            if (miss) {
                return "CHAIN_AXIS_MISS";
            }
            return finalIsSupport ? "CHAIN_AXIS_SUPPORT_STEAL" : "HIT_ACCEPTANCE_OWNER_GAP";
        }
        if ("FLOOR_CONTACT".equals(metricType)
                && Double.isFinite(contactGap)
                && Math.abs(contactGap) > EPSILON) {
            return "BUTTON".equals(visibleObjectCategory) ? "BUTTON_CONTACT_GAP" : "HIT_ACCEPTANCE_CONTACT_GAP";
        }
        if ("SIDE_FACE_CONTACT".equals(metricType)
                && Double.isFinite(sideFaceGap)
                && Math.abs(sideFaceGap) > EPSILON) {
            return "HIT_ACCEPTANCE_SIDE_ATTACHMENT_GAP";
        }
        if ("AXIS_CONTACT".equals(metricType)) {
            if (!Double.isFinite(axisGap)
                    || (Double.isFinite(axisGap) && Math.abs(axisGap) > EPSILON)) {
                return "CHAIN".equals(visibleObjectCategory)
                        ? "CHAIN_AXIS_CONTACT_METRIC_MISSING"
                        : "HIT_ACCEPTANCE_SIDE_ATTACHMENT_GAP";
            }
        }
        if (!finalIsObject) {
            if (!rayIntersectsVisibleObject && finalIsSupport) {
                return "HIT_ACCEPTANCE_SUPPORT_AIM";
            }
            if (miss) {
                return "HIT_ACCEPTANCE_MISS";
            }
            if (finalIsSupport && isStairsCategory(visibleObjectCategory)) {
                return "STAIRS_VISIBLE_OWNER_DEFERRED";
            }
            return finalIsSupport ? "HIT_ACCEPTANCE_SUPPORT_STEAL" : "HIT_ACCEPTANCE_OWNER_GAP";
        }
        if (isRegularDoorCategory(visibleObjectCategory)) {
            return "REGULAR_DOOR_OWNER_GREEN";
        }
        if ("BUTTON".equals(visibleObjectCategory) && "FLOOR_CONTACT".equals(metricType)) {
            return "BUTTON_CONTACT_GREEN";
        }
        if (visibleObjectCategory.contains("trapdoor") && "SIDE_FACE_CONTACT".equals(metricType)) {
            return "TRAPDOOR_OWNER_GREEN";
        }
        return "HIT_ACCEPTANCE_GREEN";
    }

    private static String failureLayerFor(String classification, String visibleObjectCategory, String metricType) {
        if ("HIT_ACCEPTANCE_GREEN".equals(classification)
                || "BUTTON_CONTACT_GREEN".equals(classification)
                || "TRAPDOOR_OWNER_GREEN".equals(classification)
                || "REGULAR_DOOR_OWNER_GREEN".equals(classification)
                || "CHAIN_AXIS_OWNER_GREEN".equals(classification)
                || "CHAIN_AXIS_METRIC_ONLY_DEFERRED".equals(classification)
                || "CHAIN_AXIS_MISS".equals(classification)
                || "HIT_ACCEPTANCE_SUPPORT_AIM".equals(classification)
                || "SERVER_SHIFTED_HIT_GREEN".equals(classification)) {
            return "NONE";
        }
        if ("BUTTON_CONTACT_GAP".equals(classification)) {
            return "BUTTON_FLOOR_CONTACT_DY_MISSING";
        }
        if ("HIT_ACCEPTANCE_CONTACT_GAP".equals(classification)) {
            return "SLAB_HEIGHT_CONTACT_DY_MISSING";
        }
        if ("CHAIN_AXIS_CONTACT_METRIC_MISSING".equals(classification)) {
            return "CHAIN_AXIS_CONTACT_METRIC_MISSING";
        }
        if ("CHAIN_AXIS_SUPPORT_STEAL".equals(classification)) {
            return "CHAIN_AXIS_VISIBLE_OWNER_UNSTABLE";
        }
        if ("HIT_ACCEPTANCE_SIDE_ATTACHMENT_GAP".equals(classification)) {
            return "SIDE_ATTACHMENT_ACCEPTANCE_GAP";
        }
        if ("STAIRS_VISIBLE_OWNER_DEFERRED".equals(classification)) {
            return "STAIRS_VISIBLE_OWNER_DEFERRED";
        }
        if ("HIT_ACCEPTANCE_SUPPORT_STEAL".equals(classification)
                && isRegularDoorCategory(visibleObjectCategory)) {
            return "REGULAR_DOOR_VISIBLE_OWNER_SUPPORT_STEAL";
        }
        if ("SUPPORT_TARGET".equals(metricType)) {
            return "TRACER_METRIC_NOISE";
        }
        return classification;
    }

    private static String metricLayerFor(String classification, String metricType) {
        if ("CHAIN_AXIS_METRIC_ONLY_DEFERRED".equals(classification)) {
            return "CHAIN_AXIS_METRIC_DEFERRED";
        }
        return "AXIS_CONTACT".equals(metricType) ? "AXIS_CONTACT_NOT_DEFERRED" : "NONE";
    }

    private static String slabHeightCategory(BlockState supportState, double supportDy, double objectDy) {
        if (Double.isFinite(supportDy)) {
            if (Math.abs(supportDy) <= EPSILON) {
                return "SUPPORT_DY_0";
            }
            if (Math.abs(supportDy + 0.5d) <= EPSILON) {
                return "SUPPORT_DY_NEG_0_5";
            }
            if (Math.abs(supportDy + 1.0d) <= EPSILON) {
                return "SUPPORT_DY_NEG_1";
            }
        }
        if (supportState != null && supportState.getBlock() instanceof SlabBlock
                && supportState.contains(BlockStateProperties.SLAB_TYPE)) {
            SlabType type = supportState.get(BlockStateProperties.SLAB_TYPE);
            if (type == SlabType.TOP) {
                return "LOWERED_TOP";
            }
            if (type == SlabType.BOTTOM) {
                return "LOWERED_BOTTOM";
            }
            if (type == SlabType.DOUBLE) {
                return "LOWERED_DOUBLE";
            }
        }
        if (Double.isFinite(objectDy) && objectDy <= -1.0d + EPSILON) {
            return "COMPOUND_LOWERED";
        }
        return "UNKNOWN";
    }

    private static double supportVisibleTopY(BlockPos supportPos, BlockState supportState, double supportDy) {
        if (supportPos == null || supportState == null || !Double.isFinite(supportDy)) {
            return Double.NaN;
        }
        double supportTop = SlabSupport.isSupportingSlab(supportState)
                ? SlabSupport.getSupportYOffset(supportState)
                : 1.0d;
        return supportPos.getY() + supportDy + supportTop;
    }

    private static AABB worldAABB(VoxelShape shape, BlockPos pos) {
        if (shape == null || shape.isEmpty()) {
            return null;
        }
        return shape.getBoundingAABB().offset(pos);
    }

    private static boolean rayIntersectsShape(VoxelShape shape, BlockPos pos, Vec3 eye, Vec3 rayEnd) {
        return shape != null && !shape.isEmpty() && shape.raycast(eye, rayEnd, pos) != null;
    }

    private static String heldItemId(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getId(stack.getItem()).toString();
    }

    private static String formatHit(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return hit == null ? "null" : hit.getType().name();
        }
        return hit.getType().name()
                + " pos=" + blockHit.getBlockPos().toShortString()
                + " face=" + blockHit.getSide()
                + " hitVec=" + formatVec(blockHit.getPos());
    }

    private static String formatVec(Vec3 vec) {
        if (vec == null) {
            return "null";
        }
        return String.format("%.6f,%.6f,%.6f", vec.x, vec.y, vec.z);
    }

    private static String fmt(double value) {
        return Double.isFinite(value) ? String.format("%.6f", value) : "N/A";
    }
}
