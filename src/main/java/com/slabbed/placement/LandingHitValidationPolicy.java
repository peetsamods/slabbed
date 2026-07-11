package com.slabbed.placement;

import com.slabbed.compat.CompatHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Pure C2 policy for the server's shifted hit-validation center.
 *
 * <p>This does not widen Minecraft's distance tolerance. It only says when an already-lowered,
 * ordinary full-block owner may move that validation center by its frozen visible depth: the held
 * block must belong to one of the two families {@link LandingResolver} owns in C2 (slab or ordinary
 * full block), and the packet hit must remain inside the pre-existing compound-owner envelope.
 * Unsupported C3+ families, flat owners, partial-block owners and out-of-envelope hits fall through
 * to vanilla validation.
 */
public final class LandingHitValidationPolicy {

    private static final double EPSILON = 1.0e-6d;

    private LandingHitValidationPolicy() {
    }

    /**
     * @return the owner's dy for the shifted validation center, or {@link Double#NaN} when vanilla
     *         validation must remain authoritative
     */
    public static double shiftedCenterDy(
            BlockPos ownerPos,
            BlockState ownerState,
            double ownerDy,
            Direction hitFace,
            Vec3 hitPos,
            BlockState heldState
    ) {
        if (ownerPos == null
                || ownerState == null
                || heldState == null
                || hitFace == null
                || hitPos == null
                || !Double.isFinite(ownerDy)
                || !(ownerDy < -EPSILON)
                || CompatHooks.shouldSkipOffset(ownerState)
                || CompatHooks.shouldSkipOffset(heldState)
                || LandingResolver.classify(ownerState) != LandingResolver.Family.FULL_BLOCK) {
            return Double.NaN;
        }

        LandingResolver.Family heldFamily = LandingResolver.classify(heldState);
        if (heldFamily != LandingResolver.Family.SLAB
                && heldFamily != LandingResolver.Family.FULL_BLOCK) {
            return Double.NaN;
        }

        // No face branch is intentional: C2's resolver owns these two families on all six faces.

        // Preserve the existing compound-owner envelope exactly; the redirect changes only which
        // C2 held families qualify, while Minecraft's own 1.0000001 component tolerance stays intact.
        boolean insideOwnerEnvelope = hitPos.x >= ownerPos.getX() - EPSILON
                && hitPos.x <= ownerPos.getX() + 1.0d + EPSILON
                && hitPos.y >= ownerPos.getY() + ownerDy - EPSILON
                && hitPos.y <= ownerPos.getY() + EPSILON
                && hitPos.z >= ownerPos.getZ() - EPSILON
                && hitPos.z <= ownerPos.getZ() + 1.0d + EPSILON;
        return insideOwnerEnvelope ? ownerDy : Double.NaN;
    }
}
