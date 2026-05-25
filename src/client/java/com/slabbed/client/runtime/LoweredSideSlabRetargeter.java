package com.slabbed.client.runtime;

import com.slabbed.util.SlabSupport;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LoweredSideSlabRetargeter {
    private static final double COMFORT_MISS_THRESHOLD = 0.05d;

    private LoweredSideSlabRetargeter() {
    }

    public static BlockHitResult findLoweredSideSlabRetarget(
            ClientLevel world, Entity cam, Vec3 eye, Vec3 end, HitResult currentHit, boolean slabHeld
    ) {
        if (world == null || cam == null || eye == null || end == null) {
            return null;
        }
        Vec3 dir = end.subtract(eye);
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
                currentDist2 = currentHit.getLocation().distanceToSqr(eye);
            }
        int steps = Math.max(16, (int) Math.ceil(reach / 0.05));

        BlockHitResult bestHit = null;
        double bestDist2 = currentDist2;
        for (int i = 1; i <= steps; i++) {
            double t = reach * i / steps;
            if (t * t > bestDist2 + 1.0e-6) {
                break;
            }
            Vec3 sample = eye.add(dir.scale(t));
            BlockPos samplePos = BlockPos.containing(sample);

            BlockHitResult hit = raycastLoweredSideSlab(world, cam, eye, end, samplePos, slabHeld, currentHit);
            if (hit != null) {
                double dist2 = hit.getLocation().distanceToSqr(eye);
                if (dist2 <= bestDist2 + 1.0e-6) {
                    bestHit = hit;
                    bestDist2 = dist2;
                }
            }

            hit = raycastLoweredSideSlab(world, cam, eye, end, samplePos.above(), slabHeld, currentHit);
            if (hit != null) {
                double dist2 = hit.getLocation().distanceToSqr(eye);
                if (dist2 <= bestDist2 + 1.0e-6) {
                    bestHit = hit;
                    bestDist2 = dist2;
                }
            }
        }

        return bestHit;
    }

    private static BlockHitResult raycastLoweredSideSlab(
            ClientLevel world, Entity cam, Vec3 eye, Vec3 end, BlockPos pos, boolean slabHeld, HitResult currentHit
    ) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(SlabBlock.TYPE)
                || (state.getValue(SlabBlock.TYPE) != SlabType.BOTTOM && state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE)
                || SlabSupport.getYOffset(world, pos, state) != -0.5) {
            return null;
        }

        BlockPos supportPos = pos.below();
        BlockState supportState = world.getBlockState(supportPos);
        VoxelShape shape = state.getShape(world, pos, CollisionContext.of(cam));
        boolean canComfortScan = state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                && (currentHit == null || currentHit.getType() == HitResult.Type.MISS)
                && SlabSupport.isAnchoredLoweredFullBlock(world, supportPos, supportState)
                && SlabSupport.getYOffset(world, supportPos, supportState) < 0.0
                && !supportState.is(Blocks.AIR);
        BlockHitResult hit = shape.clip(eye, end, pos);
        if (hit == null && canComfortScan) {
            hit = comfortRaycastLoweredSideSlab(world, cam, eye, end, pos, state, shape);
        }
        if (hit == null) {
            return null;
        }
        if (slabHeld
                && currentHit instanceof BlockHitResult currentBlockHit
                && state.getInteractionShape(world, pos).clip(eye, end, pos) == null) {
            // Keep slab-held face stability on the same block, but do not
            // suppress legitimate retargeting to a different lowered owner.
            if (currentBlockHit.getBlockPos().equals(pos)) {
                return null;
            }
        }
        return hit.getLocation().distanceToSqr(eye) <= end.distanceToSqr(eye) + 1.0e-6 ? hit : null;
    }

    private static BlockHitResult comfortRaycastLoweredSideSlab(
            ClientLevel world, Entity cam, Vec3 eye, Vec3 end, BlockPos pos, BlockState state, VoxelShape shape
    ) {
        if (shape == null || shape.isEmpty()) {
            return null;
        }

        AABB box = shape.bounds().inflate(COMFORT_MISS_THRESHOLD);
        if (box.getXsize() <= 0.0
                || box.getYsize() <= 0.0
                || box.getZsize() <= 0.0) {
            return null;
        }
        VoxelShape comfort = Shapes.create(
                box.minX,
                box.minY,
                box.minZ,
                box.maxX,
                box.maxY,
                box.maxZ
        );
        BlockHitResult comfortHit = comfort.clip(eye, end, pos);
        if (comfortHit == null) {
            return null;
        }

        Vec3 localHit = comfortHit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        AABB outlineBox = shape.bounds();
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
            return null;
        }

        return comfortHit;
    }
}
