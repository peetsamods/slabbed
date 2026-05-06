package com.slabbed.client.runtime;

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
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

public final class SlabbedRetargetTestHooks {
    private static final double COMFORT_MISS_THRESHOLD = 0.05d;

    private SlabbedRetargetTestHooks() {
    }

    public static BlockHitResult findLoweredSideSlabRetarget(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, HitResult currentHit, boolean slabHeld
    ) {
        if (world == null || cam == null || eye == null || end == null) {
            return null;
        }
        Vec3d dir = end.subtract(eye);
        double reach = dir.length();
        if (reach <= 0.0d) {
            return null;
        }
        dir = dir.normalize();
        double currentDist2 = Double.POSITIVE_INFINITY;
            if (currentHit != null && currentHit.getType() == HitResult.Type.BLOCK) {
                currentDist2 = currentHit.getPos().squaredDistanceTo(eye);
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

    private static BlockHitResult raycastLoweredSideSlab(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, BlockPos pos, boolean slabHeld, HitResult currentHit
    ) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || (state.get(SlabBlock.TYPE) != SlabType.BOTTOM && state.get(SlabBlock.TYPE) != SlabType.DOUBLE)
                || SlabSupport.getYOffset(world, pos, state) != -0.5) {
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
            System.out.println("[SBSB-TRACE][LOWERED_SIDE_SLAB_COMFORT] reason=comfort_scan_attempt "
                    + "pos=" + pos.toShortString()
                    + " support=" + supportPos.toShortString()
                    + " currentDist2=" + (currentHit == null ? "INF" : currentHit.getPos().squaredDistanceTo(eye))
                    + " endDist2=" + end.squaredDistanceTo(eye));
        }
        BlockHitResult hit = shape.raycast(eye, end, pos);
        if (hit == null && canComfortScan) {
            hit = comfortRaycastLoweredSideSlab(world, cam, eye, end, pos, state, shape);
            if (hit != null) {
                System.out.println("[SBSB-TRACE][LOWERED_SIDE_SLAB_COMFORT] reason=comfort_miss_angle_owner_gap "
                        + "pos=" + pos.toShortString() + " hit=" + hit.getPos());
            }
        }
        if (hit == null) {
            return null;
        }
        if (slabHeld
                && currentHit instanceof BlockHitResult currentBlockHit
                && state.getRaycastShape(world, pos).raycast(eye, end, pos) == null) {
            // Keep slab-held face stability on the same block, but do not
            // suppress legitimate retargeting to a different lowered owner.
            if (currentBlockHit.getBlockPos().equals(pos)) {
                return null;
            }
        }
        return hit.getPos().squaredDistanceTo(eye) <= end.squaredDistanceTo(eye) + 1.0e-6 ? hit : null;
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
            System.out.println("[SBSB-TRACE][LOWERED_SIDE_SLAB_COMFORT] reason=comfort_miss_angle_owner_gap"
                    + " no-box-intersection pos=" + pos.toShortString()
                    + " box=" + box);
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
            System.out.println("[SBSB-TRACE][LOWERED_SIDE_SLAB_COMFORT] reason=comfort_miss_angle_owner_gap"
                    + " distance-reject pos=" + pos.toShortString()
                    + " hit=" + comfortHit.getPos()
                    + " axisDelta=" + maxAxisDelta);
            return null;
        }

        return comfortHit;
    }
}
