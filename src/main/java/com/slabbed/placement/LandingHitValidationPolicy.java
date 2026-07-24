package com.slabbed.placement;

import com.slabbed.compat.CompatHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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
    // Derive the runtime-mapped name from Minecraft's base declaration instead of hardcoding the
    // deobfuscated name. Ambiguous or unavailable reflection metadata fails closed.
    private static final String DIRECT_NO_ITEM_USE_METHOD_NAME = directNoItemUseMethodName();
    private static final ClassValue<Boolean> HAS_DIRECT_NO_ITEM_USE_OVERRIDE =
            new ClassValue<>() {
                @Override
                protected Boolean computeValue(Class<?> type) {
                    return declaresDirectNoItemUseOverride(type);
                }
            };

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
        boolean statefulObjectTargetUse = ownerFamily == LandingResolver.Family.OBJECT
                && (ownerState.hasProperty(BlockStateProperties.OPEN)
                || ownerState.hasProperty(BlockStateProperties.POWERED))
                && HAS_DIRECT_NO_ITEM_USE_OVERRIDE.get(ownerState.getBlock().getClass());
        boolean targetOwnedUse = ordinaryTargetUse
                || statefulObjectTargetUse
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
        if (heldFamily == LandingResolver.Family.UNSUPPORTED) {
            return Double.NaN;
        }
        if (ownerState.getBlock() instanceof SlabBlock) {
            return insideTranslatedSlabShape(ownerPos, ownerState, ownerDy, hitPos)
                    ? ownerDy
                    : Double.NaN;
        }
        if (ownerFamily != LandingResolver.Family.FULL_BLOCK) {
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

    private static String directNoItemUseMethodName() {
        try {
            String methodName = null;
            for (Method method : BlockBehaviour.class.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        || !hasDirectNoItemUseSignature(method)) {
                    continue;
                }
                if (methodName != null) {
                    return null;
                }
                methodName = method.getName();
            }
            return methodName;
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    private static boolean declaresDirectNoItemUseOverride(Class<?> blockClass) {
        if (DIRECT_NO_ITEM_USE_METHOD_NAME == null
                || blockClass == null
                || !BlockBehaviour.class.isAssignableFrom(blockClass)) {
            return false;
        }
        try {
            for (Class<?> type = blockClass;
                    type != BlockBehaviour.class;
                    type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    int modifiers = method.getModifiers();
                    if (method.getName().equals(DIRECT_NO_ITEM_USE_METHOD_NAME)
                            && !Modifier.isStatic(modifiers)
                            && !Modifier.isPrivate(modifiers)
                            && hasDirectNoItemUseSignature(method)) {
                        return true;
                    }
                }
            }
        } catch (RuntimeException | LinkageError exception) {
            // Metadata failures must preserve vanilla validation, never widen shifted-hit authority.
            return false;
        }
        return false;
    }

    private static boolean hasDirectNoItemUseSignature(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return method.getReturnType() == InteractionResult.class
                && parameterTypes.length == 5
                && parameterTypes[0] == BlockState.class
                && parameterTypes[1] == Level.class
                && parameterTypes[2] == BlockPos.class
                && parameterTypes[3] == Player.class
                && parameterTypes[4] == BlockHitResult.class;
    }

    private static boolean insideTranslatedSlabShape(
            BlockPos ownerPos,
            BlockState ownerState,
            double ownerDy,
            Vec3 hitPos
    ) {
        if (!ownerState.hasProperty(SlabBlock.TYPE)) {
            return false;
        }
        SlabType type = ownerState.getValue(SlabBlock.TYPE);
        double minY = type == SlabType.TOP ? 0.5d : 0.0d;
        double maxY = type == SlabType.BOTTOM ? 0.5d : 1.0d;
        return hitPos.x >= ownerPos.getX() - EPSILON
                && hitPos.x <= ownerPos.getX() + 1.0d + EPSILON
                && hitPos.y >= ownerPos.getY() + ownerDy + minY - EPSILON
                && hitPos.y <= ownerPos.getY() + ownerDy + maxY + EPSILON
                && hitPos.z >= ownerPos.getZ() - EPSILON
                && hitPos.z <= ownerPos.getZ() + 1.0d + EPSILON;
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
