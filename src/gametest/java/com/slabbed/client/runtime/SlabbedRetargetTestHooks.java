package com.slabbed.client.runtime;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public final class SlabbedRetargetTestHooks {
    private SlabbedRetargetTestHooks() {
    }

    /**
     * The lowered-side rescue lane (LoweredSideSlabRetargeter) was removed by the
     * targeting overhaul — the offset-aware nearest-hit raycast
     * ({@code GameRendererPickOffsetRaycastMixin} / {@code SlabbedOffsetRaycast})
     * now resolves these targets geometrically, so there is no separate retarget to
     * report. This hook is retained (returning null) only so the legacy client
     * gametests that reference it still compile; those tests do not run on 1.21.1
     * (fabric-client-gametest is broken here) and are slated for cleanup.
     */
    public static BlockHitResult findLoweredSideSlabRetarget(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, HitResult currentHit, boolean slabHeld
    ) {
        return null;
    }
}
