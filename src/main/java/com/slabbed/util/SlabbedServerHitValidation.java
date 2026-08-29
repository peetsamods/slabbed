package com.slabbed.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Computes the center used by vanilla server-side interaction-distance validation. */
public final class SlabbedServerHitValidation {
    private SlabbedServerHitValidation() {
    }

    public static PlacementDepthPolicy.Decision classifyOwner(
            BlockGetter world,
            BlockPos ownerPos,
            BlockHitResult hit
    ) {
        if (world == null || ownerPos == null || hit == null || !ownerPos.equals(hit.getBlockPos())) {
            return PlacementDepthPolicy.Decision.VANILLA_OWNED;
        }
        BlockState state = world.getBlockState(ownerPos);
        return PlacementDepthPolicy.classify(SlabSupport.getYOffset(world, ownerPos, state));
    }

    public static Vec3 validationCenter(
            BlockGetter world,
            BlockPos ownerPos,
            BlockHitResult hit,
            Vec3 vanillaCenter
    ) {
        if (world == null || ownerPos == null || hit == null || vanillaCenter == null
                || !ownerPos.equals(hit.getBlockPos())) {
            return vanillaCenter;
        }
        BlockState state = world.getBlockState(ownerPos);
        double ownerDy = SlabSupport.getYOffset(world, ownerPos, state);
        return validationCenter(vanillaCenter, ownerDy, hit.getLocation());
    }

    /** Vanilla's per-axis use-packet tolerance (strictly-less-than gate). */
    public static final double VANILLA_HIT_TOLERANCE = 1.0000001d;

    /**
     * Pure policy seam used by component tests and the packet-validation bridge. The packet gate
     * accepts a hit inside EITHER the vanilla envelope or the dy-shifted one: shifting exists to
     * make offset geometry reachable and must never re-reject a hit vanilla itself would accept
     * (maintainer ruling, 2026-08-17). A moved-only window does exactly that — a bridged chain's
     * top face at local y 1.5, and any hit whose server-side dy lags the client's. Inside the
     * vanilla envelope the vanilla center is returned verbatim; only an outside hit is judged
     * against the shifted surface.
     */
    public static Vec3 validationCenter(Vec3 vanillaCenter, double ownerDy, Vec3 hitLocation) {
        if (vanillaCenter == null) {
            return null;
        }
        PlacementDepthPolicy.Decision decision = PlacementDepthPolicy.classify(ownerDy);
        if (!decision.shiftedOwner()) {
            return vanillaCenter;
        }
        if (hitLocation != null && withinEnvelope(hitLocation, vanillaCenter)) {
            return vanillaCenter;
        }
        return vanillaCenter.add(0.0d, ownerDy, 0.0d);
    }

    private static boolean withinEnvelope(Vec3 hit, Vec3 center) {
        return Math.abs(hit.x - center.x) < VANILLA_HIT_TOLERANCE
                && Math.abs(hit.y - center.y) < VANILLA_HIT_TOLERANCE
                && Math.abs(hit.z - center.z) < VANILLA_HIT_TOLERANCE;
    }
}
