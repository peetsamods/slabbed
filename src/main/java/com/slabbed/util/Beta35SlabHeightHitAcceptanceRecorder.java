package com.slabbed.util;

import com.slabbed.Slabbed;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

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

    public static void logStartup(World world, PlayerEntity player) {
        if (!enabled() || startupLogged) {
            return;
        }
        Slabbed.LOGGER.info(
                "[JULIA_BETA35_SLAB_HEIGHT_HIT_ACCEPTANCE] enabled=true phase=startup flag=-D{}=true world={} player={} scope=generic_held_item_visible_target diagnosticsOnly=true releaseAudit=NOT_RUN releaseTagMoved=false allItemClaim=false",
                FLAG,
                world == null ? "null" : world.getRegistryKey().getValue(),
                player == null ? "null" : player.getName().getString());
        startupLogged = true;
    }

    public static void recordNoTarget(World world, PlayerEntity player, HitResult finalTarget) {
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
            World world,
            Entity camera,
            PlayerEntity player,
            Vec3d eye,
            Vec3d rayEnd,
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
        ShapeContext shapeContext = player == null ? ShapeContext.absent() : ShapeContext.of(player);
        Box objectBox = worldBox(visibleObjectState.getOutlineShape(world, visibleObjectPos, shapeContext),
                visibleObjectPos);
        double objectModelBottomY = objectBox == null ? Double.NaN : objectBox.minY;
        double contactGap = Double.isFinite(objectModelBottomY) && Double.isFinite(supportVisibleTopY)
                ? objectModelBottomY - supportVisibleTopY
                : Double.NaN;

        boolean finalIsObject = finalTarget instanceof BlockHitResult finalBlock
                && finalTarget.getType() == HitResult.Type.BLOCK
                && finalBlock.getBlockPos().equals(visibleObjectPos);
        boolean finalIsSupport = finalTarget instanceof BlockHitResult finalBlock
                && finalTarget.getType() == HitResult.Type.BLOCK
                && finalBlock.getBlockPos().equals(supportCandidatePos);
        boolean miss = finalTarget == null || finalTarget.getType() == HitResult.Type.MISS;
        String classification = finalIsObject ? "HIT_ACCEPTANCE_GREEN"
                : (miss ? "HIT_ACCEPTANCE_MISS"
                : (finalIsSupport ? "HIT_ACCEPTANCE_SUPPORT_STEAL" : "HIT_ACCEPTANCE_OWNER_GAP"));
        String failureLayer = finalIsObject ? "NONE" : classification;
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
                        + " visibleObjectCategory=" + visibleObjectCategory(visibleObjectState)
                        + " supportCandidatePos=" + supportCandidatePos.toShortString()
                        + " supportCandidateState=" + supportCandidateState
                        + " supportDy=" + fmt(supportDy)
                        + " targetDy=" + fmt(targetDy)
                        + " objectDy=" + fmt(objectDy)
                        + " slabHeightCategory=" + slabHeightCategory
                        + " contactGap=" + fmt(contactGap)
                        + " sideFaceGap=N/A"
                        + " eye=" + formatVec(eye)
                        + " rayEnd=" + formatVec(rayEnd)
                        + " diagnosticsOnly=true releaseAudit=NOT_RUN releaseTagMoved=false allItemClaim=false");
    }

    public static void logServerTolerance(
            World world,
            PlayerEntity player,
            BlockHitResult hit,
            ItemStack held,
            Vec3d validationCenter,
            Vec3d shiftedValidationCenter
    ) {
        if (!enabled() || world == null || hit == null || validationCenter == null) {
            return;
        }
        logStartup(world, player);
        Vec3d hitVec = hit.getPos();
        Vec3d delta = hitVec.subtract(validationCenter);
        Vec3d shiftedDelta = shiftedValidationCenter == null ? null : hitVec.subtract(shiftedValidationCenter);
        boolean tooFar = Math.abs(delta.x) >= SERVER_HIT_TOLERANCE
                || Math.abs(delta.y) >= SERVER_HIT_TOLERANCE
                || Math.abs(delta.z) >= SERVER_HIT_TOLERANCE;
        boolean shiftedGreen = shiftedDelta != null
                && Math.abs(shiftedDelta.x) < SERVER_HIT_TOLERANCE
                && Math.abs(shiftedDelta.y) < SERVER_HIT_TOLERANCE
                && Math.abs(shiftedDelta.z) < SERVER_HIT_TOLERANCE;
        String classification = shiftedGreen ? "HIT_ACCEPTANCE_GREEN"
                : (tooFar ? "HIT_ACCEPTANCE_SERVER_REJECT" : "HIT_ACCEPTANCE_GREEN");
        String failureLayer = tooFar && !shiftedGreen ? "HIT_ACCEPTANCE_SERVER_REJECT" : "NONE";
        BlockState targetState = world.getBlockState(hit.getBlockPos());
        double targetDy = SlabSupport.getYOffset(world, hit.getBlockPos(), targetState);
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
            World world,
            PlayerEntity player,
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

    private static BlockPos resolveVisibleObjectPos(World world, HitResult initialTarget, HitResult finalTarget) {
        BlockPos finalPos = hitBlockPos(finalTarget);
        if (finalPos != null && !world.getBlockState(finalPos).isAir()) {
            return finalPos;
        }
        BlockPos initialPos = hitBlockPos(initialTarget);
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
        if (state.getBlock() instanceof StairsBlock) {
            return "STAIRS";
        }
        if (state.getBlock() instanceof TrapdoorBlock) {
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
        if (Registries.BLOCK.getId(state.getBlock()).toString().contains("button")) {
            return "BUTTON";
        }
        return Registries.BLOCK.getId(state.getBlock()).toString();
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
                && supportState.contains(Properties.SLAB_TYPE)) {
            SlabType type = supportState.get(Properties.SLAB_TYPE);
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

    private static Box worldBox(VoxelShape shape, BlockPos pos) {
        if (shape == null || shape.isEmpty()) {
            return null;
        }
        return shape.getBoundingBox().offset(pos);
    }

    private static String heldItemId(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "minecraft:air" : Registries.ITEM.getId(stack.getItem()).toString();
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

    private static String formatVec(Vec3d vec) {
        if (vec == null) {
            return "null";
        }
        return String.format("%.6f,%.6f,%.6f", vec.x, vec.y, vec.z);
    }

    private static String fmt(double value) {
        return Double.isFinite(value) ? String.format("%.6f", value) : "N/A";
    }
}
