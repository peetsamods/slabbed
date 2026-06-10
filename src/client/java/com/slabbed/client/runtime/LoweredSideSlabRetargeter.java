package com.slabbed.client.runtime;

import com.slabbed.Slabbed;
import com.slabbed.util.RuntimeDiagnostics;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

public final class LoweredSideSlabRetargeter {
    private static final double COMFORT_MISS_THRESHOLD = 0.05d;
    private static final String COMFORT_TRACE_OPT_IN = "slabbed.loweredSideSlabComfortTrace";

    private LoweredSideSlabRetargeter() {
    }

    public static BlockHitResult findLoweredSideSlabRetarget(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, HitResult currentHit, boolean slabHeld
    ) {
        if (world == null || cam == null || eye == null || end == null) {
            return null;
        }
        // Underside-aim intent guard: when the player deliberately aims at the DOWN
        // face (bottom edge) of a block, they intend to place BELOW it, not to extend
        // the lowered side-slab lane. Without this, a bottom-edge aim on a lowered slab
        // is stolen into the lowered lane (sideOwnerWouldWin), so the placed slab lands
        // 0.5 lower than intended. Mirrors the Direction.DOWN guard in
        // GameRendererCrosshairRetargetMixin#isInitialHitOnLoweredFullBlockPlacementIntent.
        if (currentHit != null
                && currentHit.getType() == HitResult.Type.BLOCK
                && currentHit instanceof BlockHitResult currentDownHit
                && currentDownHit.getSide() == Direction.DOWN) {
            return null;
        }
        Vec3d dir = end.subtract(eye);
        double reach = dir.length();
        if (reach <= 0.0d) {
            return null;
        }
        dir = dir.normalize();
        BlockHitResult compoundVisibleOwner =
                SlabSupport.findCompoundVisibleSlabLaneOwnerTarget(world, cam, eye, end);
        if (compoundVisibleOwner != null) {
            return compoundVisibleOwner;
        }
        double currentDist2 = Double.POSITIVE_INFINITY;
        if (currentHit != null && currentHit.getType() == HitResult.Type.BLOCK) {
            currentDist2 = currentHit.getPos().squaredDistanceTo(eye);
            if (slabHeld
                    && currentHit instanceof BlockHitResult currentBlockHit
                    && currentBlockHit.getSide().getAxis() != Direction.Axis.Y
                    && isLoweredSideSlabCandidate(world, currentBlockHit.getBlockPos())) {
                return currentBlockHit;
            }
        }
        int steps = Math.max(16, (int) Math.ceil(reach / 0.05));

        BlockHitResult bestHit = null;
        double bestDist2 = currentDist2;
        for (int i = 1; i <= steps; i++) {
            double t = reach * i / steps;
            if (t * t > bestDist2 + 1.0e-6) {
                break;
            }
            Vec3d sample = eye.add(dir.multiply(t));
            BlockPos samplePos = BlockPos.ofFloored(sample);

            BlockHitResult hit = raycastLoweredSideSlab(world, cam, eye, end, samplePos, slabHeld, currentHit);
            if (hit != null) {
                double dist2 = hit.getPos().squaredDistanceTo(eye);
                if (dist2 <= bestDist2 + 1.0e-6) {
                    bestHit = hit;
                    bestDist2 = dist2;
                }
            }

            hit = raycastLoweredSideSlab(world, cam, eye, end, samplePos.up(), slabHeld, currentHit);
            if (hit != null) {
                double dist2 = hit.getPos().squaredDistanceTo(eye);
                if (dist2 <= bestDist2 + 1.0e-6) {
                    bestHit = hit;
                    bestDist2 = dist2;
                }
            }
        }

        return bestHit;
    }

    private static boolean isLoweredSideSlabCandidate(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof SlabBlock
                && state.contains(SlabBlock.TYPE)
                && (state.get(SlabBlock.TYPE) == SlabType.BOTTOM || state.get(SlabBlock.TYPE) == SlabType.DOUBLE)
                && state.getFluidState().isEmpty()
                && SlabSupport.getYOffset(world, pos, state) == -0.5;
    }

    private static BlockHitResult raycastLoweredSideSlab(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, BlockPos pos, boolean slabHeld, HitResult currentHit
    ) {
        BlockState state = world.getBlockState(pos);
        if (!isLoweredSideSlabCandidate(world, pos)) {
            return null;
        }

        BlockPos supportPos = pos.down();
        BlockState supportState = world.getBlockState(supportPos);
        VoxelShape shape = state.getOutlineShape(world, pos, ShapeContext.of(cam));
        boolean canComfortScan = state.get(SlabBlock.TYPE) == SlabType.BOTTOM
                && (currentHit == null || currentHit.getType() == HitResult.Type.MISS)
                && SlabSupport.isAnchoredLoweredFullBlock(world, supportPos, supportState)
                && SlabSupport.getYOffset(world, supportPos, supportState) < 0.0
                && !supportState.isOf(Blocks.AIR);
        if (canComfortScan) {
            slabbed$traceComfort("[SBSB-TRACE][LOWERED_SIDE_SLAB_COMFORT] reason=comfort_scan_attempt "
                    + "pos=" + pos.toShortString()
                    + " support=" + supportPos.toShortString()
                    + " currentDist2=" + (currentHit == null ? "INF" : currentHit.getPos().squaredDistanceTo(eye))
                    + " endDist2=" + end.squaredDistanceTo(eye));
            RuntimeDiagnostics.recordLiveTorchComfortTrace(
                    world, "comfort_scan_attempt", pos, supportPos);
        }
        BlockHitResult hit = shape.raycast(eye, end, pos);
        if (hit == null && canComfortScan) {
            hit = comfortRaycastLoweredSideSlab(world, cam, eye, end, pos, state, shape);
            if (hit != null) {
                slabbed$traceComfort("[SBSB-TRACE][LOWERED_SIDE_SLAB_COMFORT] reason=comfort_miss_angle_owner_gap "
                        + "pos=" + pos.toShortString() + " hit=" + hit.getPos());
                RuntimeDiagnostics.recordLiveTorchComfortTrace(
                        world, "comfort_miss_angle_owner_gap", pos, supportPos);
            }
        }
        if (hit == null) {
            return null;
        }
        // MISS-path owner guard: if a real visible object above the support
        // slab is on-ray, do not steal ownership to the support slab.
        if (currentHit != null
                && currentHit.getType() == HitResult.Type.MISS
                && slabbed$hasVisibleOwnerAboveSupport(world, cam, eye, end, supportPos, hit)) {
            return null;
        }
        if (slabHeld
                && currentHit instanceof BlockHitResult currentBlockHit
                && state.getRaycastShape(world, pos).raycast(eye, end, pos) == null) {
            // Keep vertical same-block stability, but allow visible lowered
            // side faces to be confirmed as the side-slab owner.
            if (currentBlockHit.getBlockPos().equals(pos)
                    && currentBlockHit.getSide().getAxis() == Direction.Axis.Y) {
                return null;
            }
        }
        return hit.getPos().squaredDistanceTo(eye) <= end.squaredDistanceTo(eye) + 1.0e-6 ? hit : null;
    }

    private static boolean slabbed$hasVisibleOwnerAboveSupport(
            ClientWorld world,
            Entity cam,
            Vec3d eye,
            Vec3d end,
            BlockPos supportPos,
            BlockHitResult supportHit
    ) {
        BlockPos ownerPos = supportPos.up();
        BlockState ownerState = world.getBlockState(ownerPos);
        if (ownerState.isAir()
                || ownerState.getBlock() instanceof SlabBlock
                || SlabSupport.isSupportingSlab(ownerState)) {
            return false;
        }
        VoxelShape ownerOutline = ownerState.getOutlineShape(world, ownerPos, ShapeContext.of(cam));
        if (ownerOutline == null || ownerOutline.isEmpty()) {
            return false;
        }
        BlockHitResult ownerHit = ownerOutline.raycast(eye, end, ownerPos);
        if (ownerHit == null) {
            return false;
        }
        double ownerDist2 = ownerHit.getPos().squaredDistanceTo(eye);
        double supportDist2 = supportHit.getPos().squaredDistanceTo(eye);
        return ownerDist2 <= supportDist2 + 1.0e-6d;
    }

    private static BlockHitResult comfortRaycastLoweredSideSlab(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, BlockPos pos, BlockState state, VoxelShape shape
    ) {
        if (shape == null || shape.isEmpty()) {
            return null;
        }

        net.minecraft.util.math.Box box = shape.getBoundingBox().expand(COMFORT_MISS_THRESHOLD);
        if (box.getLengthX() <= 0.0
                || box.getLengthY() <= 0.0
                || box.getLengthZ() <= 0.0) {
            return null;
        }
        VoxelShape comfort = VoxelShapes.cuboid(
                box.minX,
                box.minY,
                box.minZ,
                box.maxX,
                box.maxY,
                box.maxZ
        );
        BlockHitResult comfortHit = comfort.raycast(eye, end, pos);
        if (comfortHit == null) {
            slabbed$traceComfort("[SBSB-TRACE][LOWERED_SIDE_SLAB_COMFORT] reason=comfort_miss_angle_owner_gap"
                    + " no-box-intersection pos=" + pos.toShortString()
                    + " box=" + box);
            RuntimeDiagnostics.recordLiveTorchComfortTrace(
                    world, "comfort_miss_angle_owner_gap no-box-intersection", pos, pos.down());
            return null;
        }

        Vec3d localHit = comfortHit.getPos().subtract(pos.getX(), pos.getY(), pos.getZ());
        net.minecraft.util.math.Box outlineBox = shape.getBoundingBox();
        double maxAxisDelta = 0.0d;
        if (localHit.x < outlineBox.minX) {
            double d = outlineBox.minX - localHit.x;
            maxAxisDelta = Math.max(maxAxisDelta, Math.abs(d));
        } else if (localHit.x > outlineBox.maxX) {
            double d = localHit.x - outlineBox.maxX;
            maxAxisDelta = Math.max(maxAxisDelta, Math.abs(d));
        }
        if (localHit.y < outlineBox.minY) {
            double d = outlineBox.minY - localHit.y;
            maxAxisDelta = Math.max(maxAxisDelta, Math.abs(d));
        } else if (localHit.y > outlineBox.maxY) {
            double d = localHit.y - outlineBox.maxY;
            maxAxisDelta = Math.max(maxAxisDelta, Math.abs(d));
        }
        if (localHit.z < outlineBox.minZ) {
            double d = outlineBox.minZ - localHit.z;
            maxAxisDelta = Math.max(maxAxisDelta, Math.abs(d));
        } else if (localHit.z > outlineBox.maxZ) {
            double d = localHit.z - outlineBox.maxZ;
            maxAxisDelta = Math.max(maxAxisDelta, Math.abs(d));
        }
        if (maxAxisDelta > COMFORT_MISS_THRESHOLD + 1.0e-9) {
            slabbed$traceComfort("[SBSB-TRACE][LOWERED_SIDE_SLAB_COMFORT] reason=comfort_miss_angle_owner_gap"
                    + " distance-reject pos=" + pos.toShortString()
                    + " hit=" + comfortHit.getPos()
                    + " axisDelta=" + maxAxisDelta);
            RuntimeDiagnostics.recordLiveTorchComfortTrace(
                    world, "comfort_miss_angle_owner_gap distance-reject", pos, pos.down());
            return null;
        }

        return comfortHit;
    }

    private static void slabbed$traceComfort(String message) {
        if (Boolean.getBoolean(COMFORT_TRACE_OPT_IN)) {
            Slabbed.LOGGER.info(message);
        }
    }
}
