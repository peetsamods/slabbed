package com.slabbed.placement;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

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
                // The ONE shared ownership gate, on both ends of the gesture. Its tagged-slab
                // carve-out is load-bearing here: a held compat SLAB must reach the tolerance shift
                // like any vanilla slab, or the un-widened vanilla distance check silently rejects
                // the use packet for every aim below -0.5 — the live "stubborn" refusal. A factless
                // compat OWNER never arrives with ownerDy < 0, so worldgen terrain cannot reach the
                // shift through this carve-out either way.
                || LandingResolver.compatOwnsFinalState(ownerState)
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
        if (pointedDripstoneSideContinuationDirection(
                ownerPos, ownerState, ownerDy, hitFace, hitPos, heldState) != null) {
            return ownerDy;
        }
        if (isPointedDripstoneOwnerFacingHit(ownerState, hitFace)
                && insideTranslatedCell(ownerPos, ownerDy, hitPos)) {
            return ownerDy;
        }
        if (SlabSupport.isBeta35VerticalChainVisibleOwnerObject(ownerState)
                && hitFace == Direction.DOWN
                && insideTranslatedCell(ownerPos, ownerDy, hitPos)) {
            return ownerDy;
        }
        if (ownerState.getBlock() instanceof SlabBlock) {
            return insideTranslatedSlabShape(ownerPos, ownerState, ownerDy, hitPos)
                    ? ownerDy
                    : Double.NaN;
        }
        // RESCUE LANE (maintainer ruling, 2026-08-26). Continues the 26e9b907 precedent, which
        // broadened this policy for chain and dripstone owners on exactly this failure: vanilla's own
        // hit-distance tolerance rejecting a use packet, before useOn runs, for a legitimate click on
        // a body Slabbed renders lower than its cell.
        //
        // The gate BELOW keys on Family.FULL_BLOCK, which resolves through isSolidRender() — an
        // OCCLUSION property ("do I hide my neighbours"), not a geometry one. LAW.md clause 2 forbids
        // exactly that shape of proxy. Scaffolding is the counter-example that exposed it live: it does
        // not occlude, yet getInteractionShape returns a FULL CUBE unconditionally, so the player
        // genuinely aims at and clicks a full-cube face that the server then refuses.
        //
        // Scope is deliberately narrow on both axes, and each half is load-bearing:
        //  - GEOMETRY, not class: the owner must PRESENT a full-cube interaction face. Vanilla's
        //    default interaction shape is empty, so fences, walls, bars and chains do not qualify and
        //    keep vanilla's centre exactly as UpwardContinuationValidationTest pins them.
        //  - RESCUE ONLY: fire only where vanilla's own per-axis check would REJECT. A widening that
        //    also fired where vanilla already accepts would change an admitted decision, which is not
        //    what a tolerance rescue is for, and would redden those same pins — they assert
        //    componentDistance <= tolerance together with a NaN shift.
        // Reach is NOT widened by this: isWithinBlockInteractionRange is checked separately and
        // earlier in handleUseItemOn, so a shifted centre cannot extend how far a player can build.
        if (presentsFullCubeInteractionFace(ownerState)
                && vanillaWouldRejectOnYAlone(ownerPos, hitPos)
                && insideTranslatedCell(ownerPos, ownerDy, hitPos)) {
            return ownerDy;
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

    /**
     * Shared admission contract for a horizontal hit on a translated pointed-dripstone owner.
     *
     * @return the owner's vertical continuation direction, or {@code null} when vanilla remains
     *         authoritative
     */
    public static Direction pointedDripstoneSideContinuationDirection(
            BlockPos ownerPos,
            BlockState ownerState,
            double ownerDy,
            Direction hitFace,
            Vec3 hitPos,
            BlockState heldState
    ) {
        if (ownerPos == null
                || ownerState == null
                || hitFace == null
                || hitPos == null
                || heldState == null
                || !Double.isFinite(ownerDy)
                || !(ownerDy < -EPSILON)
                || !hitFace.getAxis().isHorizontal()
                || !(ownerState.getBlock() instanceof PointedDripstoneBlock)
                || !(heldState.getBlock() instanceof PointedDripstoneBlock)
                || !ownerState.hasProperty(PointedDripstoneBlock.TIP_DIRECTION)
                || !insideTranslatedCell(ownerPos, ownerDy, hitPos)) {
            return null;
        }
        return ownerState.getValue(PointedDripstoneBlock.TIP_DIRECTION);
    }

    private static boolean isPointedDripstoneOwnerFacingHit(BlockState state, Direction hitFace) {
        return state.getBlock() instanceof PointedDripstoneBlock
                && state.hasProperty(PointedDripstoneBlock.TIP_DIRECTION)
                && state.getValue(PointedDripstoneBlock.TIP_DIRECTION) == hitFace;
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

    /**
     * Vanilla's own per-axis packet tolerance, read from {@code ServerGamePacketListenerImpl
     * .handleUseItemOn}: it accepts iff every component of {@code hit - Vec3.atCenterOf(pos)} is
     * strictly under this. Mirrored here so the rescue lane can ask "would vanilla reject this?"
     * rather than guessing a threshold.
     */
    private static final double VANILLA_COMPONENT_TOLERANCE = 1.0000001d;

    /**
     * True when vanilla's unshifted check would reject this hit on the Y axis ALONE. X and Z are
     * deliberately not consulted: the shift only moves Y, so a hit out of range on X or Z is
     * unrescuable and must keep falling through to vanilla.
     */
    private static boolean vanillaWouldRejectOnYAlone(BlockPos ownerPos, Vec3 hitPos) {
        return Math.abs(hitPos.y - (ownerPos.getY() + 0.5d)) >= VANILLA_COMPONENT_TOLERANCE;
    }

    /**
     * Does this owner PRESENT a full-cube face to the player? Asked of the state's own interaction
     * geometry, never of its class or its occlusion flag (LAW.md clause 2). Queried context-free
     * against {@link EmptyBlockGetter} so the dy lanes see no support context and return the
     * UNSHIFTED shape in both live and headless environments — the same discipline
     * {@code SlabEnsembleCoherence.vanillaShape} uses, and the reason the bounds test below is a
     * plain 0..1 comparison. Fails CLOSED: anything that is empty, partial, or unexpectedly
     * translated answers false and the caller keeps vanilla's centre.
     */
    private static boolean presentsFullCubeInteractionFace(BlockState state) {
        if (state == null) {
            return false;
        }
        VoxelShape interaction = state.getInteractionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (interaction == null || interaction.isEmpty()) {
            return false;
        }
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (interaction.min(axis) > EPSILON || interaction.max(axis) < 1.0d - EPSILON) {
                return false;
            }
        }
        return true;
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
