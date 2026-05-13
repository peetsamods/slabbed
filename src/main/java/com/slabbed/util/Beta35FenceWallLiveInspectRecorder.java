package com.slabbed.util;

import com.slabbed.Slabbed;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.WallBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * Evidence-only live recorder for Beta 3.5 fence/wall/anvil hitbox acceptance.
 */
public final class Beta35FenceWallLiveInspectRecorder {
    private static final String FLAG = "slabbed.beta35FenceWallLiveInspect";
    private static final double EPSILON = 1.0e-6d;
    private static final double SERVER_HIT_TOLERANCE = 1.0000001d;
    private static final int LOG_INTERVAL_TICKS = 10;
    private static final int RAY_STEPS = 120;

    private static boolean startupLogged;
    private static long lastClientLogWorldTime = Long.MIN_VALUE;
    private static String lastClientSignature = "n/a";
    private static long lastNoTargetWorldTime = Long.MIN_VALUE;

    private Beta35FenceWallLiveInspectRecorder() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(FLAG);
    }

    public static void logStartup(World world, PlayerEntity player) {
        if (!enabled() || startupLogged) {
            return;
        }
        Slabbed.LOGGER.info(
                "[JULIA_BETA35_FENCE_WALL_LIVE_INSPECT] enabled=true phase=startup flag=-D{}=true world={} player={} scope=FenceBlock_WallBlock_anvil diagnosticsOnly=true releaseAudit=NOT_RUN releaseTagMoved=false",
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
                "[JULIA_BETA35_FENCE_WALL_LIVE_INSPECT] phase=client-tick classification=TRACE_ACTIVE_NO_TARGET failureLayer=TRACE_ACTIVE_NO_TARGET heldItem={} finalTarget=null",
                heldItemId(player == null ? ItemStack.EMPTY : player.getMainHandStack()));
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

        BlockPos objectPos = resolveObjectPos(world, camera, player, eye, rayEnd, initialTarget, finalTarget);
        if (objectPos == null) {
            recordTraceActiveNoTarget(world, player, held, initialTarget, finalTarget, finalDecision);
            return;
        }

        BlockState objectState = world.getBlockState(objectPos);
        BlockPos supportPos = objectPos.down();
        BlockState supportState = world.getBlockState(supportPos);
        double objectDy = SlabSupport.getYOffset(world, objectPos, objectState);
        double supportDy = SlabSupport.getYOffset(world, supportPos, supportState);
        double supportVisibleTopY = supportVisibleTopY(supportPos, supportState, supportDy);

        ShapeContext shapeContext = player == null ? ShapeContext.absent() : ShapeContext.of(player);
        Box outlineBox = worldBox(objectState.getOutlineShape(world, objectPos, shapeContext), objectPos);
        Box raycastBox = worldBox(objectState.getRaycastShape(world, objectPos), objectPos);
        Box collisionBox = worldBox(objectState.getCollisionShape(world, objectPos, shapeContext), objectPos);
        Box modelBox = collisionBox != null ? collisionBox : outlineBox;
        double objectModelBottomY = modelBox == null ? Double.NaN : modelBox.minY;
        double contactGap = Double.isFinite(objectModelBottomY) && Double.isFinite(supportVisibleTopY)
                ? objectModelBottomY - supportVisibleTopY
                : Double.NaN;

        boolean contactGreen = Double.isFinite(contactGap) && Math.abs(contactGap) <= EPSILON;
        boolean triadGreen = sameBox(modelBox, outlineBox) && sameBox(modelBox, raycastBox)
                && (collisionBox == null || sameBox(modelBox, collisionBox));
        boolean connectedWallShapeLimit = contactGreen
                && !triadGreen
                && isConnectedVanillaWallShapeLimit(objectState, objectDy, supportDy, modelBox, outlineBox, raycastBox);
        boolean ownerGreen = finalTarget instanceof BlockHitResult finalBlock
                && finalTarget.getType() == HitResult.Type.BLOCK
                && finalBlock.getBlockPos().equals(objectPos);
        boolean outOfScopeHeldItemOwnerGap = contactGreen
                && (triadGreen || connectedWallShapeLimit)
                && !ownerGreen
                && !isRelevantHeldItem(held);

        String contactClassification = contactGreen ? "LIVE_CONTACT_GREEN" : "LIVE_CONTACT_GAP";
        String triadClassification = triadGreen
                ? "LIVE_TRIAD_GREEN"
                : (connectedWallShapeLimit ? "TRACE_SHAPE_FALSE_POSITIVE" : "LIVE_TRIAD_MISMATCH");
        String ownerClassification = ownerGreen
                ? "LIVE_OWNER_GREEN"
                : (outOfScopeHeldItemOwnerGap ? "OUT_OF_SCOPE_HELD_ITEM_OWNER_GAP" : "LIVE_OWNER_GAP");
        String classification;
        String failureLayer;
        if (!contactGreen) {
            classification = "LIVE_CONTACT_GAP";
            failureLayer = "LIVE_CONTACT_GAP";
        } else if (connectedWallShapeLimit) {
            classification = "LIVE_CONNECTED_WALL_TRIAD_FALSE_POSITIVE";
            failureLayer = ownerGreen ? "TRACE_SHAPE_FALSE_POSITIVE" : ownerClassification;
        } else if (!triadGreen) {
            classification = "LIVE_TRIAD_MISMATCH";
            failureLayer = "LIVE_TRIAD_MISMATCH";
        } else if (outOfScopeHeldItemOwnerGap) {
            classification = "OUT_OF_SCOPE_HELD_ITEM_OWNER_GAP";
            failureLayer = "OUT_OF_SCOPE_HELD_ITEM_OWNER_GAP";
        } else if (!ownerGreen) {
            classification = "LIVE_OWNER_GAP";
            failureLayer = "LIVE_OWNER_GAP";
        } else {
            classification = "LIVE_CONTACT_GREEN";
            failureLayer = "NONE";
        }

        String signature = objectPos.toShortString() + "|" + classification + "|" + finalDecision + "|"
                + fmt(contactGap) + "|" + formatHit(finalTarget);
        long now = world.getTime();
        if (signature.equals(lastClientSignature) && now - lastClientLogWorldTime < LOG_INTERVAL_TICKS) {
            return;
        }
        lastClientSignature = signature;
        lastClientLogWorldTime = now;

        Slabbed.LOGGER.info(
                "[JULIA_BETA35_FENCE_WALL_LIVE_INSPECT] phase=client-crosshair"
                        + " classification=" + classification
                        + " failureLayer=" + failureLayer
                        + " contactClassification=" + contactClassification
                        + " triadClassification=" + triadClassification
                        + " ownerClassification=" + ownerClassification
                        + " finalDecision=" + finalDecision
                        + " heldItem=" + heldItemId(held)
                        + " initialTarget=" + formatHit(initialTarget)
                        + " finalTarget=" + formatHit(finalTarget)
                        + " eye=" + formatVec(eye)
                        + " rayEnd=" + formatVec(rayEnd)
                        + " objectBlockId=" + Registries.BLOCK.getId(objectState.getBlock())
                        + " objectPos=" + objectPos.toShortString()
                        + " objectState=" + objectState
                        + " objectDy=" + fmt(objectDy)
                        + " modelMinY=" + fmt(modelBox == null ? Double.NaN : modelBox.minY)
                        + " modelMaxY=" + fmt(modelBox == null ? Double.NaN : modelBox.maxY)
                        + " outlineMinY=" + fmt(outlineBox == null ? Double.NaN : outlineBox.minY)
                        + " outlineMaxY=" + fmt(outlineBox == null ? Double.NaN : outlineBox.maxY)
                        + " raycastMinY=" + fmt(raycastBox == null ? Double.NaN : raycastBox.minY)
                        + " raycastMaxY=" + fmt(raycastBox == null ? Double.NaN : raycastBox.maxY)
                        + " collisionMinY=" + fmt(collisionBox == null ? Double.NaN : collisionBox.minY)
                        + " collisionMaxY=" + fmt(collisionBox == null ? Double.NaN : collisionBox.maxY)
                        + " supportPos=" + supportPos.toShortString()
                        + " supportState=" + supportState
                        + " supportDy=" + fmt(supportDy)
                        + " supportVisibleTopY=" + fmt(supportVisibleTopY)
                        + " objectModelBottomY=" + fmt(objectModelBottomY)
                        + " contactGap=" + fmt(contactGap)
                        + " outlineBounds=" + formatBox(outlineBox)
                        + " raycastBounds=" + formatBox(raycastBox)
                        + " modelBounds=" + formatBox(modelBox)
                        + " triadCoLocated=" + (triadGreen ? "yes" : "no")
                        + " diagnosticsOnly=true releaseAudit=NOT_RUN releaseTagMoved=false");
    }

    public static void logServerTolerance(
            World world,
            PlayerEntity player,
            BlockHitResult hit,
            ItemStack held,
            Vec3d validationCenter
    ) {
        logServerTolerance(world, player, hit, held, validationCenter, null);
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

        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        BlockPos objectPos = relevantObjectPos(world, pos);
        boolean heldRelevant = isRelevantHeldItem(held);
        if (objectPos == null && !heldRelevant) {
            return;
        }

        BlockState objectState = objectPos == null ? state : world.getBlockState(objectPos);
        BlockPos supportPos = objectPos == null ? pos.down() : objectPos.down();
        BlockState supportState = world.getBlockState(supportPos);
        double objectDy = objectPos == null ? SlabSupport.getYOffset(world, pos, state)
                : SlabSupport.getYOffset(world, objectPos, objectState);
        double supportDy = SlabSupport.getYOffset(world, supportPos, supportState);
        double supportVisibleTopY = supportVisibleTopY(supportPos, supportState, supportDy);
        Box objectBox = objectPos == null ? null : worldBox(objectState.getOutlineShape(world, objectPos), objectPos);
        double objectModelBottomY = objectBox == null ? Double.NaN : objectBox.minY;
        double contactGap = Double.isFinite(objectModelBottomY) && Double.isFinite(supportVisibleTopY)
                ? objectModelBottomY - supportVisibleTopY
                : Double.NaN;

        Vec3d hitVec = hit.getPos();
        Vec3d delta = hitVec.subtract(validationCenter);
        boolean tooFar = Math.abs(delta.x) >= SERVER_HIT_TOLERANCE
                || Math.abs(delta.y) >= SERVER_HIT_TOLERANCE
                || Math.abs(delta.z) >= SERVER_HIT_TOLERANCE;
        Vec3d shiftedDelta = shiftedValidationCenter == null ? null : hitVec.subtract(shiftedValidationCenter);
        boolean shiftedWithinTolerance = shiftedDelta != null
                && Math.abs(shiftedDelta.x) < SERVER_HIT_TOLERANCE
                && Math.abs(shiftedDelta.y) < SERVER_HIT_TOLERANCE
                && Math.abs(shiftedDelta.z) < SERVER_HIT_TOLERANCE;
        String classification = shiftedWithinTolerance
                ? "SERVER_SHIFTED_HIT_GREEN"
                : (tooFar ? "SERVER_HIT_TOO_FAR" : "SERVER_HIT_WITHIN_TOLERANCE");

        Slabbed.LOGGER.info(
                "[JULIA_BETA35_FENCE_WALL_LIVE_SERVER] classification={} failureLayer={} heldItem={} player={} packetBlockPos={} hitFace={} hitVec={} validationCenter={} validationDelta={} shiftedValidationCenter={} shiftedValidationDelta={} tolerance={} targetState={} targetDy={} objectPos={} objectState={} objectDy={} supportPos={} supportState={} supportDy={} supportVisibleTopY={} objectModelBottomY={} contactGap={} diagnosticsOnly=true releaseAudit=NOT_RUN releaseTagMoved=false",
                classification,
                shiftedWithinTolerance || !tooFar ? "NONE" : "SERVER_HIT_TOO_FAR",
                heldItemId(held),
                player == null ? "null" : player.getName().getString(),
                pos.toShortString(),
                hit.getSide(),
                formatVec(hitVec),
                formatVec(validationCenter),
                formatVec(delta),
                formatVec(shiftedValidationCenter),
                formatVec(shiftedDelta),
                fmt(SERVER_HIT_TOLERANCE),
                state,
                fmt(SlabSupport.getYOffset(world, pos, state)),
                objectPos == null ? "null" : objectPos.toShortString(),
                objectState,
                fmt(objectDy),
                supportPos.toShortString(),
                supportState,
                fmt(supportDy),
                fmt(supportVisibleTopY),
                fmt(objectModelBottomY),
                fmt(contactGap));
    }

    private static void recordTraceActiveNoTarget(
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
                "[JULIA_BETA35_FENCE_WALL_LIVE_INSPECT] phase=client-crosshair classification=TRACE_ACTIVE_NO_TARGET failureLayer=TRACE_ACTIVE_NO_TARGET heldItem={} initialTarget={} finalTarget={} finalDecision={} player={}",
                heldItemId(held),
                formatHit(initialTarget),
                formatHit(finalTarget),
                finalDecision,
                player == null ? "null" : player.getName().getString());
    }

    private static BlockPos resolveObjectPos(
            World world,
            Entity camera,
            PlayerEntity player,
            Vec3d eye,
            Vec3d rayEnd,
            HitResult initialTarget,
            HitResult finalTarget
    ) {
        BlockPos finalPos = objectPosFromHit(world, finalTarget);
        if (finalPos != null) {
            return finalPos;
        }
        BlockPos initialPos = objectPosFromHit(world, initialTarget);
        if (initialPos != null) {
            return initialPos;
        }

        BlockPos bestPos = null;
        double bestDist2 = Double.POSITIVE_INFINITY;
        Vec3d ray = rayEnd.subtract(eye);
        ShapeContext shapeContext = player == null ? ShapeContext.absent() : ShapeContext.of(player);
        for (int i = 1; i <= RAY_STEPS; i++) {
            Vec3d sample = eye.add(ray.multiply((double) i / RAY_STEPS));
            BlockPos samplePos = BlockPos.ofFloored(sample);
            bestPos = chooseCloserCandidate(world, shapeContext, eye, rayEnd, samplePos, bestPos, bestDist2);
            if (bestPos != null) {
                Box box = worldBox(world.getBlockState(bestPos).getOutlineShape(world, bestPos, shapeContext), bestPos);
                if (box != null) {
                    bestDist2 = box.getCenter().squaredDistanceTo(eye);
                }
            }
            bestPos = chooseCloserCandidate(world, shapeContext, eye, rayEnd, samplePos.up(), bestPos, bestDist2);
            if (bestPos != null) {
                Box box = worldBox(world.getBlockState(bestPos).getOutlineShape(world, bestPos, shapeContext), bestPos);
                if (box != null) {
                    bestDist2 = box.getCenter().squaredDistanceTo(eye);
                }
            }
        }
        return bestPos;
    }

    private static BlockPos chooseCloserCandidate(
            World world,
            ShapeContext shapeContext,
            Vec3d eye,
            Vec3d rayEnd,
            BlockPos candidate,
            BlockPos bestPos,
            double bestDist2
    ) {
        BlockState state = world.getBlockState(candidate);
        if (!isRelevantObject(state)) {
            return bestPos;
        }
        VoxelShape outline = state.getOutlineShape(world, candidate, shapeContext);
        if (outline == null || outline.isEmpty() || outline.raycast(eye, rayEnd, candidate) == null) {
            return bestPos;
        }
        double dist2 = outline.raycast(eye, rayEnd, candidate).getPos().squaredDistanceTo(eye);
        return dist2 < bestDist2 + EPSILON ? candidate : bestPos;
    }

    private static BlockPos objectPosFromHit(World world, HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return relevantObjectPos(world, blockHit.getBlockPos());
    }

    private static BlockPos relevantObjectPos(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        if (isRelevantObject(world.getBlockState(pos))) {
            return pos;
        }
        if (isRelevantObject(world.getBlockState(pos.up()))) {
            return pos.up();
        }
        return null;
    }

    private static boolean isRelevantHeldItem(ItemStack stack) {
        return stack != null
                && stack.getItem() instanceof BlockItem blockItem
                && isRelevantObject(blockItem.getBlock().getDefaultState());
    }

    private static boolean isRelevantObject(BlockState state) {
        return state != null
                && (state.getBlock() instanceof FenceBlock
                        || state.getBlock() instanceof WallBlock
                        || state.isOf(Blocks.ANVIL));
    }

    private static boolean isConnectedVanillaWallShapeLimit(
            BlockState state,
            double objectDy,
            double supportDy,
            Box modelBox,
            Box outlineBox,
            Box raycastBox
    ) {
        if (state == null
                || !(state.getBlock() instanceof WallBlock)
                || Math.abs(objectDy) > EPSILON
                || Math.abs(supportDy) > EPSILON
                || modelBox == null
                || outlineBox == null
                || raycastBox != null) {
            return false;
        }
        String encodedState = state.toString();
        return encodedState.contains("=low") || encodedState.contains("=tall");
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

    private static boolean sameBox(Box a, Box b) {
        if (a == null || b == null) {
            return false;
        }
        return Math.abs(a.minX - b.minX) <= EPSILON
                && Math.abs(a.minY - b.minY) <= EPSILON
                && Math.abs(a.minZ - b.minZ) <= EPSILON
                && Math.abs(a.maxX - b.maxX) <= EPSILON
                && Math.abs(a.maxY - b.maxY) <= EPSILON
                && Math.abs(a.maxZ - b.maxZ) <= EPSILON;
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

    private static String formatBox(Box box) {
        if (box == null) {
            return "null";
        }
        return "min=(" + fmt(box.minX) + "," + fmt(box.minY) + "," + fmt(box.minZ)
                + "),max=(" + fmt(box.maxX) + "," + fmt(box.maxY) + "," + fmt(box.maxZ) + ")";
    }

    private static String fmt(double value) {
        return Double.isFinite(value) ? String.format("%.6f", value) : "NaN";
    }
}
