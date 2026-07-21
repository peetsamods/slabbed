package com.slabbed.placement;

import com.slabbed.compat.CompatHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Pure C2 policy for the server's shifted hit-validation center.
 *
 * <p>This does not widen Minecraft's distance tolerance. It only says when an already-lowered
 * target may move that validation center by its frozen visible depth. Target-owned use validates
 * against the target's translated cell even when vanilla needs a held item for the interaction;
 * block placement retains its existing resolver-family and compound-owner-envelope gates. Flat
 * owners and out-of-envelope hits fall through to vanilla.
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
        return shiftedCenterDy(
                ownerPos, ownerState, ownerDy, hitFace, hitPos, heldState, false);
    }

    public static double shiftedCenterDy(
            BlockPos ownerPos,
            BlockState ownerState,
            double ownerDy,
            Direction hitFace,
            Vec3 hitPos,
            BlockState heldState,
            boolean ordinaryTargetUse
    ) {
        if (ownerPos == null
                || ownerState == null
                || hitFace == null
                || hitPos == null
                || !Double.isFinite(ownerDy)
                || !(ownerDy < -EPSILON)
                || CompatHooks.shouldSkipOffset(ownerState)
                || CompatHooks.shouldSkipSlabSupport(ownerState)
                || LandingResolver.compatOwnsFinalState(heldState)) {
            return Double.NaN;
        }

        LandingResolver.Family ownerFamily = LandingResolver.classify(ownerState);
        boolean targetOwnedUse = ordinaryTargetUse
                || ownerState.getBlock() instanceof EntityBlock
                || ownerState.getBlock() instanceof BedBlock
                || ownerState.getBlock() instanceof FlowerPotBlock;
        if (targetOwnedUse) {
            boolean insideTranslatedTargetCell = insideTranslatedCell(ownerPos, ownerDy, hitPos);
            return insideTranslatedTargetCell ? ownerDy : Double.NaN;
        }

        if (heldState == null) {
            return Double.NaN;
        }

        LandingResolver.Family heldFamily = LandingResolver.classify(heldState);
        if (heldFamily == LandingResolver.Family.USE_CREATED_FULL_CUBE_CONTACT) {
            boolean supportedOwner = ownerState.getBlock() instanceof SlabBlock
                    || ownerState.getBlock() instanceof EntityBlock
                    || ownerState.isSolidRender();
            return supportedOwner && insideTranslatedCell(ownerPos, ownerDy, hitPos)
                    ? ownerDy
                    : Double.NaN;
        }
        if (heldFamily == LandingResolver.Family.PAIRED_FLOOR_SEAT
                || heldFamily == LandingResolver.Family.AIM_KEYED_FLOOR_SEAT) {
            boolean supportedOwner = ownerState.getBlock() instanceof SlabBlock
                    || ownerState.getBlock() instanceof EntityBlock
                    || ownerState.isSolidRender();
            if (hitFace != Direction.UP || !supportedOwner) {
                return Double.NaN;
            }
            boolean insideFloorSeatEnvelope = hitPos.x >= ownerPos.getX() - EPSILON
                    && hitPos.x <= ownerPos.getX() + 1.0d + EPSILON
                    && hitPos.y >= ownerPos.getY() + ownerDy - EPSILON
                    && hitPos.y <= ownerPos.getY() + 1.0d + EPSILON
                    && hitPos.z >= ownerPos.getZ() - EPSILON
                    && hitPos.z <= ownerPos.getZ() + 1.0d + EPSILON;
            return insideFloorSeatEnvelope ? ownerDy : Double.NaN;
        }
        if (ownerFamily != LandingResolver.Family.FULL_BLOCK) {
            return Double.NaN;
        }
        if (heldFamily == LandingResolver.Family.UNSUPPORTED) {
            return Double.NaN;
        }

        // No face branch is intentional: the placement resolver owns these families on all six faces.

        // Preserve the existing compound-owner envelope exactly; the redirect changes only which
        // resolver-held families qualify, while Minecraft's own 1.0000001 tolerance stays intact.
        boolean insideOwnerEnvelope = hitPos.x >= ownerPos.getX() - EPSILON
                && hitPos.x <= ownerPos.getX() + 1.0d + EPSILON
                && hitPos.y >= ownerPos.getY() + ownerDy - EPSILON
                && hitPos.y <= ownerPos.getY() + EPSILON
                && hitPos.z >= ownerPos.getZ() - EPSILON
                && hitPos.z <= ownerPos.getZ() + 1.0d + EPSILON;
        return insideOwnerEnvelope ? ownerDy : Double.NaN;
    }

    private static boolean insideTranslatedCell(BlockPos ownerPos, double ownerDy, Vec3 hitPos) {
        return hitPos.x >= ownerPos.getX() - EPSILON
                && hitPos.x <= ownerPos.getX() + 1.0d + EPSILON
                && hitPos.y >= ownerPos.getY() + ownerDy - EPSILON
                && hitPos.y <= ownerPos.getY() + ownerDy + 1.0d + EPSILON
                && hitPos.z >= ownerPos.getZ() - EPSILON
                && hitPos.z <= ownerPos.getZ() + 1.0d + EPSILON;
    }
}
