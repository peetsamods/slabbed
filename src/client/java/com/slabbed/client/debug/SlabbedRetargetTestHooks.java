package com.slabbed.client.debug;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
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

public final class SlabbedRetargetTestHooks {
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
        VoxelShape shape = state.getOutlineShape(world, pos, ShapeContext.of(cam));
        BlockHitResult hit = shape.raycast(eye, end, pos);
        if (hit == null) {
            return null;
        }
        if (slabHeld && currentHit != null && currentHit.getType() == HitResult.Type.BLOCK
                && state.getRaycastShape(world, pos).raycast(eye, end, pos) == null) {
            return null;
        }
        return hit.getPos().squaredDistanceTo(eye) <= end.squaredDistanceTo(eye) + 1.0e-6 ? hit : null;
    }
}
