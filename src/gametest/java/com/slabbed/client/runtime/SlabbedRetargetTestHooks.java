package com.slabbed.client.runtime;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public final class SlabbedRetargetTestHooks {
    private SlabbedRetargetTestHooks() {
    }

    public static BlockHitResult findLoweredSideSlabRetarget(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, HitResult currentHit, boolean slabHeld
    ) {
        return LoweredSideSlabRetargeter.findLoweredSideSlabRetarget(world, cam, eye, end, currentHit, slabHeld);
    }
}
