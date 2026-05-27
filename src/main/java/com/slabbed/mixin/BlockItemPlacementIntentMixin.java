package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.PlacementIntentState;
import com.slabbed.util.SlabSupport;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemPlacementIntentMixin {

    private static final double LOWERED_VISUAL_BOUNDARY_EPSILON = 1.0e-6d;
    private static final String REPEAT_SEAM_TRACE_OPT_IN = "slabbed.beta4RepeatMergeTrace";
    private static final ThreadLocal<CompoundVisibleSideLowerIntent> COMPOUND_VISIBLE_SIDE_LOWER_INTENT =
            new ThreadLocal<>();
    private static final ThreadLocal<CompoundVisibleSideUpperIntent> COMPOUND_VISIBLE_SIDE_UPPER_INTENT =
            new ThreadLocal<>();
    private static final ThreadLocal<CompoundVisibleSideDoubleIntent> COMPOUND_VISIBLE_SIDE_DOUBLE_INTENT =
            new ThreadLocal<>();
    private static final ThreadLocal<CompoundVisibleOwnerTopIntent> COMPOUND_VISIBLE_OWNER_TOP_INTENT =
            new ThreadLocal<>();

    private record CompoundVisibleSideLowerIntent(BlockPos sourcePos, BlockPos candidatePos) {
    }

    private record CompoundVisibleSideUpperIntent(BlockPos sourcePos, BlockPos candidatePos) {
    }

    private record CompoundVisibleSideDoubleIntent(BlockPos sourcePos, BlockPos candidatePos) {
    }

    private record CompoundVisibleOwnerTopIntent(BlockPos sourcePos, BlockPos candidatePos) {
    }

    private static boolean slabbed$isOrdinaryLoweredFullBlock(UseOnContext context, BlockPos pos, BlockState state) {
        return state.isSolidRender()
                && !(state.getBlock() instanceof EntityBlock)
                && !(state.getBlock() instanceof CraftingTableBlock)
                && SlabSupport.getYOffset(context.getLevel(), pos, state) < 0.0d;
    }

    private static boolean slabbed$isLoweredSlab(BlockState state, Level world, BlockPos pos) {
        return state.getBlock() instanceof SlabBlock
                && SlabSupport.getYOffset(world, pos, state) < 0.0d;
    }

    private static boolean slabbed$isCompoundSideHit(UseOnContext context, BlockPos pos, BlockState state) {
        if (context.getClickedFace().getAxis().isVertical()
                || state.getBlock() instanceof SlabBlock
                || !SlabAnchorAttachment.isCompoundFullBlockAnchor(context.getLevel(), pos)) {
            return false;
        }
        double yOffset = SlabSupport.getYOffset(context.getLevel(), pos, state);
        return Math.abs(yOffset + 1.0d) <= LOWERED_VISUAL_BOUNDARY_EPSILON;
    }

    private static boolean slabbed$isCompoundTopHit(UseOnContext context, BlockPos pos, BlockState state) {
        if (context.getClickedFace() != Direction.UP
                || state.getBlock() instanceof SlabBlock
                || !SlabAnchorAttachment.isCompoundFullBlockAnchor(context.getLevel(), pos)) {
            return false;
        }
        double yOffset = SlabSupport.getYOffset(context.getLevel(), pos, state);
        return Math.abs(yOffset + 1.0d) <= LOWERED_VISUAL_BOUNDARY_EPSILON;
    }

    private static boolean slabbed$isPersistentLoweredBottomSlabCarrierCandidate(Level world, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(SlabBlock.TYPE)
                || state.getValue(SlabBlock.TYPE) != SlabType.BOTTOM
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        BlockPos belowPos = pos.below();
        BlockState below = world.getBlockState(belowPos);
        return SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, belowPos, below)
                && (SlabAnchorAttachment.isAnchored(world, belowPos)
                || SlabSupport.getYOffset(world, belowPos, below) < 0.0d);
    }

    private static boolean slabbed$isCompoundVisibleOwnerTopSlabResult(
            BlockPlaceContext context,
            BlockPos placePos,
            BlockState placedState
    ) {
        if (context.getClickedFace() != Direction.UP
                || !(placedState.getBlock() instanceof SlabBlock)
                || !placedState.hasProperty(SlabBlock.TYPE)
                || placedState.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
            return false;
        }
        CompoundVisibleOwnerTopIntent intent = COMPOUND_VISIBLE_OWNER_TOP_INTENT.get();
        return intent != null && placePos.equals(intent.candidatePos());
    }

    private static boolean slabbed$isCompoundVisibleSideLowerSlabResult(
            BlockPlaceContext context,
            BlockPos placePos,
            BlockState placedState
    ) {
        if (context.getClickedFace().getAxis().isVertical()
                || !placedState.is(Blocks.STONE_SLAB)
                || !placedState.hasProperty(SlabBlock.TYPE)
                || placedState.getValue(SlabBlock.TYPE) != SlabType.BOTTOM
                || !placedState.getFluidState().isEmpty()) {
            return false;
        }
        BlockPos sourcePos = placePos.relative(context.getClickedFace().getOpposite());
        BlockState sourceState = context.getLevel().getBlockState(sourcePos);
        CompoundVisibleSideLowerIntent intent = COMPOUND_VISIBLE_SIDE_LOWER_INTENT.get();
        return intent != null
                && sourcePos.equals(intent.sourcePos())
                && placePos.equals(intent.candidatePos())
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(context.getLevel(), sourcePos, sourceState)
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(context.getLevel(), sourcePos)
                && Math.abs(SlabSupport.getYOffset(context.getLevel(), sourcePos, sourceState) + 1.0d)
                <= LOWERED_VISUAL_BOUNDARY_EPSILON;
    }

    private static boolean slabbed$isCompoundVisibleSideUpperSlabResult(
            BlockPlaceContext context,
            BlockPos placePos,
            BlockState placedState
    ) {
        if (context.getClickedFace().getAxis().isVertical()
                || !placedState.is(Blocks.STONE_SLAB)
                || !placedState.hasProperty(SlabBlock.TYPE)
                || placedState.getValue(SlabBlock.TYPE) != SlabType.TOP
                || !placedState.getFluidState().isEmpty()) {
            return false;
        }
        BlockPos sourcePos = placePos.relative(context.getClickedFace().getOpposite());
        BlockState sourceState = context.getLevel().getBlockState(sourcePos);
        CompoundVisibleSideUpperIntent intent = COMPOUND_VISIBLE_SIDE_UPPER_INTENT.get();
        return intent != null
                && sourcePos.equals(intent.sourcePos())
                && placePos.equals(intent.candidatePos())
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(context.getLevel(), sourcePos, sourceState)
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(context.getLevel(), sourcePos)
                && Math.abs(SlabSupport.getYOffset(context.getLevel(), sourcePos, sourceState) + 1.0d)
                <= LOWERED_VISUAL_BOUNDARY_EPSILON;
    }

    private static boolean slabbed$isCompoundVisibleSideDoubleSlabResult(
            BlockPlaceContext context,
            BlockPos placePos,
            BlockState placedState
    ) {
        if (context.getClickedFace().getAxis().isVertical()
                || !placedState.is(Blocks.STONE_SLAB)
                || !placedState.hasProperty(SlabBlock.TYPE)
                || placedState.getValue(SlabBlock.TYPE) != SlabType.DOUBLE
                || !placedState.getFluidState().isEmpty()) {
            return false;
        }
        BlockPos sourcePos = placePos.relative(context.getClickedFace().getOpposite());
        BlockState sourceState = context.getLevel().getBlockState(sourcePos);
        CompoundVisibleSideDoubleIntent intent = COMPOUND_VISIBLE_SIDE_DOUBLE_INTENT.get();
        return intent != null
                && sourcePos.equals(intent.sourcePos())
                && placePos.equals(intent.candidatePos())
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(context.getLevel(), sourcePos, sourceState)
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(context.getLevel(), sourcePos)
                && Math.abs(SlabSupport.getYOffset(context.getLevel(), sourcePos, sourceState) + 1.0d)
                <= LOWERED_VISUAL_BOUNDARY_EPSILON;
    }


    private static SlabType slabbed$getExpectedLoweredSidePlacementType(BlockState targetState) {
        if (!targetState.hasProperty(SlabBlock.TYPE)) {
            return SlabType.BOTTOM;
        }
        return targetState.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                ? SlabType.BOTTOM
                : SlabType.TOP;
    }

    private static SlabType slabbed$getLoweredDoubleHitIntentType(BlockPos targetPos, Vec3 hitPos) {
        // Lowered DOUBLE occupies [y-0.5, y+0.5]. Its visual half split is at block y.
        double loweredMidY = targetPos.getY();
        boolean exactMid = Math.abs(hitPos.y - loweredMidY) <= LOWERED_VISUAL_BOUNDARY_EPSILON;
        return (hitPos.y < loweredMidY || exactMid) ? SlabType.BOTTOM : SlabType.TOP;
    }

    private static double slabbed$placementYForType(BlockPos targetPos, SlabType expectedType) {
        return targetPos.getY() + (expectedType == SlabType.BOTTOM ? 0.499d : 0.501d);
    }

    private static Vec3 slabbed$hitPosOnFace(BlockPos targetPos, Direction side, Vec3 originalHitPos, double y) {
        double x = originalHitPos.x;
        double z = originalHitPos.z;
        if (side == Direction.EAST) {
            x = targetPos.getX() + 1.0d;
        } else if (side == Direction.WEST) {
            x = targetPos.getX();
        } else if (side == Direction.SOUTH) {
            z = targetPos.getZ() + 1.0d;
        } else if (side == Direction.NORTH) {
            z = targetPos.getZ();
        }
        return new Vec3(x, y, z);
    }

    private static boolean slabbed$canPlaceInClickedCell(
            UseOnContext context,
            BlockPos pos,
            Direction side,
            Vec3 hitPos
    ) {
        BlockState state = context.getLevel().getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        BlockHitResult hit = new BlockHitResult(hitPos, side, pos, context.isInside(), false);
        UseOnContext useContext = new UseOnContext(
                context.getLevel(),
                context.getPlayer(),
                context.getHand(),
                context.getItemInHand(),
                hit) {
        };
        return state.canBeReplaced(new BlockPlaceContext(useContext));
    }

    private static boolean slabbed$hasVisibleLaneOwnerSignal(UseOnContext context, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        double yOffset = SlabSupport.getYOffset(context.getLevel(), pos, state);
        return yOffset < 0.0d
                || SlabAnchorAttachment.isAnchored(context.getLevel(), pos)
                || SlabAnchorAttachment.isCompoundFullBlockAnchor(context.getLevel(), pos);
    }

    private static UseOnContext slabbed$replaceabilityAwareVisibleLaneContext(
            UseOnContext context,
            BlockPos finalOwnerPos,
            BlockState finalOwnerState,
            Direction side,
            Vec3 hitPos
    ) {
        if (side.getAxis().isVertical() || finalOwnerState.isAir()) {
            return null;
        }
        Vec3 finalOwnerHitPos = slabbed$hitPosOnFace(finalOwnerPos, side, hitPos, hitPos.y);
        if (slabbed$canPlaceInClickedCell(context, finalOwnerPos, side, finalOwnerHitPos)) {
            return null;
        }

        BlockPos expectedVisibleLanePos = finalOwnerPos.relative(side.getOpposite());
        BlockPos visibleOwnerPos = expectedVisibleLanePos.relative(side.getOpposite());
        BlockState visibleOwnerState = context.getLevel().getBlockState(visibleOwnerPos);
        if (!slabbed$hasVisibleLaneOwnerSignal(context, visibleOwnerPos, visibleOwnerState)) {
            return null;
        }

        Vec3 visibleLaneHitPos = slabbed$hitPosOnFace(expectedVisibleLanePos, side, hitPos, hitPos.y);
        if (!context.getLevel().getBlockState(expectedVisibleLanePos).isAir()
                || !slabbed$canPlaceInClickedCell(context, expectedVisibleLanePos, side, visibleLaneHitPos)) {
            return null;
        }

        BlockPos outwardPlacementPos = finalOwnerPos.relative(side);
        if (outwardPlacementPos.equals(expectedVisibleLanePos)) {
            return null;
        }
        Vec3 outwardHitPos = slabbed$hitPosOnFace(outwardPlacementPos, side, hitPos, hitPos.y);
        if (!slabbed$canPlaceInClickedCell(context, outwardPlacementPos, side, outwardHitPos)) {
            return null;
        }

        BlockHitResult remappedHit = new BlockHitResult(
                visibleLaneHitPos,
                side,
                expectedVisibleLanePos,
                context.isInside(),
                false
        );
        return new UseOnContext(
                context.getLevel(),
                context.getPlayer(),
                context.getHand(),
                context.getItemInHand(),
                remappedHit) {
        };
    }

    private static UseOnContext slabbed$finalTargetUnknownVisibleLaneContext(
            UseOnContext context,
            BlockPos finalOwnerPos,
            BlockState finalOwnerState,
            Direction side,
            Vec3 hitPos
    ) {
        if (slabbed$hasVisibleLaneOwnerSignal(context, finalOwnerPos, finalOwnerState)) {
            return null;
        }
        UseOnContext sideLaneContext = slabbed$replaceabilityAwareVisibleLaneContext(
                context,
                finalOwnerPos,
                finalOwnerState,
                side,
                hitPos);
        if (sideLaneContext != null) {
            return sideLaneContext;
        }
        return slabbed$finalTargetUnknownUpperVisibleLaneContext(
                context,
                finalOwnerPos,
                finalOwnerState,
                side,
                hitPos);
    }

    private static UseOnContext slabbed$finalTargetUnknownUpperVisibleLaneContext(
            UseOnContext context,
            BlockPos finalOwnerPos,
            BlockState finalOwnerState,
            Direction side,
            Vec3 hitPos
    ) {
        if (side.getAxis().isVertical() || finalOwnerState.isAir()) {
            return null;
        }
        if (hitPos.y < finalOwnerPos.getY() + 0.5d
                || hitPos.y > finalOwnerPos.getY() + 1.0d + LOWERED_VISUAL_BOUNDARY_EPSILON) {
            return null;
        }

        BlockPos currentOutwardPos = finalOwnerPos.relative(side);
        if (!context.getLevel().getBlockState(currentOutwardPos).isAir()) {
            return null;
        }
        if (context.getLevel().getBlockState(currentOutwardPos.below()).isAir()) {
            return null;
        }

        BlockPos backAboveOwnerPos = finalOwnerPos.relative(side.getOpposite()).above();
        BlockState backAboveOwnerState = context.getLevel().getBlockState(backAboveOwnerPos);
        if (!slabbed$hasVisibleLaneOwnerSignal(context, backAboveOwnerPos, backAboveOwnerState)) {
            return null;
        }

        BlockPos expectedVisibleLanePos = finalOwnerPos.above();
        double localY = hitPos.y - finalOwnerPos.getY();
        double remappedY = expectedVisibleLanePos.getY()
                + Math.max(LOWERED_VISUAL_BOUNDARY_EPSILON,
                Math.min(1.0d - LOWERED_VISUAL_BOUNDARY_EPSILON, localY));
        Vec3 visibleLaneHitPos = slabbed$hitPosOnFace(expectedVisibleLanePos, side, hitPos, remappedY);
        if (!slabbed$canPlaceInClickedCell(context, expectedVisibleLanePos, side, visibleLaneHitPos)) {
            return null;
        }

        BlockHitResult remappedHit = new BlockHitResult(
                visibleLaneHitPos,
                side,
                expectedVisibleLanePos,
                context.isInside(),
                false
        );
        return new UseOnContext(
                context.getLevel(),
                context.getPlayer(),
                context.getHand(),
                context.getItemInHand(),
                remappedHit) {
        };
    }

    private static String slabbed$dimensionId(Level level) {
        return level == null ? "null" : level.dimension().toString();
    }

    private static String slabbed$heldItemId(UseOnContext context) {
        return BuiltInRegistries.ITEM.getKey(context.getItemInHand().getItem()).toString();
    }

    private static String slabbed$placementIntentContextDetails(
            UseOnContext context,
            BlockPos finalOwnerPos,
            BlockState finalOwnerState,
            Direction finalOwnerFace,
            Boolean itemMatches,
            Boolean worldMatches,
            Boolean finalTargetMatches,
            Boolean finalOwnerOccupiedNonReplaceable,
            BlockPos expectedPlacePos,
            Boolean expectedPlaceEmptyOrReplaceable,
            String validation,
            String extra
    ) {
        StringBuilder line = new StringBuilder(512);
        line.append(" contextClickedPos=").append(slabbed$placementIntentPos(context == null ? null : context.getClickedPos()));
        line.append(" contextClickedFace=").append(slabbed$placementIntentSafe(context == null ? null : context.getClickedFace()));
        line.append(" contextHit=").append(slabbed$placementIntentVec(context == null ? null : context.getClickLocation()));
        line.append(" contextHeld=").append(context == null ? "null" : slabbed$placementIntentSafe(slabbed$heldItemId(context)));
        line.append(" contextWorld=").append(context == null ? "null" : slabbed$placementIntentSafe(slabbed$dimensionId(context.getLevel())));
        line.append(" finalTargetPos=").append(slabbed$placementIntentPos(finalOwnerPos));
        line.append(" finalTargetFace=").append(slabbed$placementIntentSafe(finalOwnerFace));
        line.append(" finalTargetState=").append(slabbed$placementIntentSafe(finalOwnerState));
        line.append(" itemValidation=").append(slabbed$placementIntentValidation(itemMatches));
        line.append(" worldValidation=").append(slabbed$placementIntentValidation(worldMatches));
        line.append(" finalTargetValidation=").append(slabbed$placementIntentValidation(finalTargetMatches));
        line.append(" finalOwnerOccupiedNonReplaceable=").append(slabbed$placementIntentTri(finalOwnerOccupiedNonReplaceable));
        line.append(" expectedPlacePos=").append(slabbed$placementIntentPos(expectedPlacePos));
        line.append(" expectedPlace=").append(slabbed$placementIntentPos(expectedPlacePos));
        line.append(" expectedPlaceEmptyOrReplaceable=").append(slabbed$placementIntentTri(expectedPlaceEmptyOrReplaceable));
        line.append(" validation=").append(slabbed$placementIntentSafe(validation));
        line.append(" consumePath=BlockItemPlacementIntentMixin#slabbed$placementIntentVisibleLaneContext");
        line.append(PlacementIntentState.lastProducerDetails());
        if (extra != null && !extra.isBlank()) {
            line.append(' ').append(extra);
        }
        return line.toString();
    }

    private static String slabbed$placementIntentValidation(Boolean value) {
        if (value == null) {
            return "unknown";
        }
        return value ? "match" : "mismatch";
    }

    private static String slabbed$placementIntentTri(Boolean value) {
        return value == null ? "unknown" : value.toString();
    }

    private static String slabbed$placementIntentPos(BlockPos pos) {
        return pos == null ? "null" : pos.toShortString();
    }

    private static String slabbed$placementIntentVec(Vec3 vec) {
        if (vec == null) {
            return "null";
        }
        return String.format("%.6f,%.6f,%.6f", vec.x, vec.y, vec.z);
    }

    private static String slabbed$placementIntentSafe(Object value) {
        return value == null ? "null" : value.toString().replace(' ', '_');
    }

    private static UseOnContext slabbed$placementIntentVisibleLaneContext(
            UseOnContext context,
            BlockPos finalOwnerPos,
            BlockState finalOwnerState,
            Direction side,
            Vec3 hitPos
    ) {
        PlacementIntentState.auditBoundary("USE_ON_CONTEXT", "USE_ON_CONTEXT",
                slabbed$placementIntentContextDetails(
                        context,
                        finalOwnerPos,
                        finalOwnerState,
                        side,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "use_on_context_start",
                        "method=BlockItemPlacementIntentMixin#slabbed$placementIntentVisibleLaneContext"));
        PlacementIntentState.Snapshot snapshot = PlacementIntentState.snapshot();
        if (snapshot == null) {
            String details = slabbed$placementIntentContextDetails(
                    context,
                    finalOwnerPos,
                    finalOwnerState,
                    side,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "no_snapshot",
                    "reject=NO_SNAPSHOT");
            PlacementIntentState.auditConsume(null, details);
            PlacementIntentState.auditReject("NO_SNAPSHOT", null, details);
            return null;
        }

        boolean valid = false;
        String clearReason = null;
        try {
            String contextDimensionId = slabbed$dimensionId(context.getLevel());
            String contextHeldItemId = slabbed$heldItemId(context);
            boolean itemMatches = snapshot.heldItemId().equals(contextHeldItemId);
            boolean worldMatches = snapshot.dimensionId().equals(contextDimensionId);
            boolean finalTargetUnknownExpectedPlaceKnown =
                    snapshot.mode() == PlacementIntentState.Mode.FINAL_TARGET_UNKNOWN_EXPECTED_PLACE_KNOWN;
            boolean normalFinalTargetMode = snapshot.mode() == PlacementIntentState.Mode.NORMAL_FINAL_TARGET;
            boolean finalTargetMatches = normalFinalTargetMode
                    && finalOwnerPos.equals(snapshot.finalTargetPos())
                    && side == snapshot.finalTargetFace();
            boolean originalContextMatches = finalTargetUnknownExpectedPlaceKnown
                    && finalOwnerPos.equals(snapshot.originalVisibleOwnerPos())
                    && side == snapshot.originalVisibleFace();
            boolean placementIntentTargetMatches = normalFinalTargetMode ? finalTargetMatches : originalContextMatches;
            Boolean finalTargetValidation = normalFinalTargetMode ? finalTargetMatches : null;
            String baseDetails = slabbed$placementIntentContextDetails(
                    context,
                    finalOwnerPos,
                    finalOwnerState,
                    side,
                    itemMatches,
                    worldMatches,
                    finalTargetValidation,
                    null,
                    snapshot.expectedPlacePos(),
                    null,
                    "start",
                    "mode=" + slabbed$placementIntentSafe(snapshot.mode())
                            + " originalContextValidation=" + slabbed$placementIntentValidation(originalContextMatches));
            PlacementIntentState.auditConsume(snapshot, baseDetails);

            if (side.getAxis().isVertical()) {
                PlacementIntentState.auditReject("CONTEXT_NOT_RELEVANT", snapshot,
                        baseDetails + " reject=CONTEXT_NOT_RELEVANT");
                clearReason = "VALIDATION_REJECT";
                return null;
            }
            if (finalOwnerState.isAir()) {
                PlacementIntentState.auditReject("FINAL_OWNER_NOT_OCCUPIED", snapshot,
                        baseDetails + " reject=FINAL_OWNER_NOT_OCCUPIED");
                clearReason = "VALIDATION_REJECT";
                return null;
            }
            if (!worldMatches) {
                PlacementIntentState.auditReject("WORLD_MISMATCH", snapshot,
                        baseDetails + " reject=WORLD_MISMATCH");
                clearReason = "WORLD_MISMATCH";
                return null;
            }
            if (!itemMatches) {
                PlacementIntentState.auditReject("HELD_ITEM_UNRELATED", snapshot,
                        baseDetails + " reject=HELD_ITEM_UNRELATED");
                clearReason = "HELD_ITEM_UNRELATED";
                return null;
            }
            if (!placementIntentTargetMatches) {
                String reject = finalTargetUnknownExpectedPlaceKnown
                        ? "ORIGINAL_CONTEXT_MISMATCH"
                        : "FINAL_TARGET_MISMATCH";
                PlacementIntentState.auditReject(reject, snapshot,
                        baseDetails + " reject=" + reject);
                clearReason = finalTargetUnknownExpectedPlaceKnown ? "VALIDATION_REJECT" : "FINAL_TARGET_MISMATCH";
                return null;
            }
            if (side != snapshot.originalVisibleFace()
                    || side != snapshot.expectedPlaceFace()
                    || snapshot.expectedPlacePos() == null
                    || snapshot.expectedPlacePos().equals(finalOwnerPos)
                    || !snapshot.expectedPlacePos().equals(snapshot.originalVisibleOwnerPos().relative(side))) {
                PlacementIntentState.auditReject("EXPECTED_PLACE_UNKNOWN", snapshot,
                        baseDetails + " reject=EXPECTED_PLACE_UNKNOWN");
                clearReason = "VALIDATION_REJECT";
                return null;
            }

            BlockState originalVisibleState = context.getLevel().getBlockState(snapshot.originalVisibleOwnerPos());
            if (!snapshot.originalVisibleState().equals(originalVisibleState.toString())) {
                PlacementIntentState.auditReject("ORIGINAL_STATE_MISMATCH", snapshot,
                        baseDetails + " reject=ORIGINAL_STATE_MISMATCH"
                                + " observedOriginalVisibleState=" + slabbed$placementIntentSafe(originalVisibleState));
                clearReason = "VALIDATION_REJECT";
                return null;
            }
            if (finalTargetUnknownExpectedPlaceKnown) {
                if (finalOwnerState.isAir()) {
                    PlacementIntentState.auditReject("FINAL_OWNER_NOT_OCCUPIED", snapshot,
                            baseDetails + " reject=FINAL_OWNER_NOT_OCCUPIED");
                    clearReason = "VALIDATION_REJECT";
                    return null;
                }
            } else if (!slabbed$hasVisibleLaneOwnerSignal(
                    context,
                    snapshot.originalVisibleOwnerPos(),
                    originalVisibleState)) {
                PlacementIntentState.auditReject("CONTEXT_NOT_RELEVANT", snapshot,
                        baseDetails + " reject=CONTEXT_NOT_RELEVANT"
                                + " originalVisibleOwnerSignal=false");
                clearReason = "VALIDATION_REJECT";
                return null;
            }

            Vec3 ownerValidationHit = finalTargetUnknownExpectedPlaceKnown
                    ? snapshot.originalVisibleHit()
                    : snapshot.finalTargetHit();
            Vec3 finalOwnerHitPos = slabbed$hitPosOnFace(finalOwnerPos, side, ownerValidationHit, ownerValidationHit.y);
            boolean finalOwnerReplaceable = slabbed$canPlaceInClickedCell(context, finalOwnerPos, side, finalOwnerHitPos);
            boolean finalOwnerOccupiedNonReplaceable = !finalOwnerState.isAir() && !finalOwnerReplaceable;
            if (finalOwnerReplaceable) {
                PlacementIntentState.auditReject("FINAL_OWNER_REPLACEABLE", snapshot,
                        slabbed$placementIntentContextDetails(
                                context,
                                finalOwnerPos,
                                finalOwnerState,
                                side,
                                true,
                                true,
                                finalTargetValidation,
                                finalOwnerOccupiedNonReplaceable,
                                snapshot.expectedPlacePos(),
                                null,
                                "final_owner_replaceable",
                                "reject=FINAL_OWNER_REPLACEABLE"));
                clearReason = "VALIDATION_REJECT";
                return null;
            }

            Vec3 expectedHitPos = slabbed$hitPosOnFace(
                    snapshot.expectedPlacePos(),
                    side,
                    snapshot.originalVisibleHit(),
                    snapshot.originalVisibleHit().y);
            BlockState expectedPlaceState = context.getLevel().getBlockState(snapshot.expectedPlacePos());
            boolean expectedPlaceReplaceable =
                    slabbed$canPlaceInClickedCell(context, snapshot.expectedPlacePos(), side, expectedHitPos);
            boolean expectedPlaceEmptyOrReplaceable = expectedPlaceState.isAir() || expectedPlaceReplaceable;

            if (finalTargetUnknownExpectedPlaceKnown) {
                UseOnContext visibleLaneContext = slabbed$finalTargetUnknownVisibleLaneContext(
                        context,
                        finalOwnerPos,
                        finalOwnerState,
                        side,
                        snapshot.originalVisibleHit());
                if (visibleLaneContext != null) {
                    BlockPos visibleLanePos = visibleLaneContext.getClickedPos();
                    Vec3 visibleLaneHit = visibleLaneContext.getClickLocation();
                    BlockState visibleLaneState = context.getLevel().getBlockState(visibleLanePos);
                    boolean visibleLaneReplaceable =
                            slabbed$canPlaceInClickedCell(context, visibleLanePos, side, visibleLaneHit);
                    boolean visibleLaneEmptyOrReplaceable = visibleLaneState.isAir() || visibleLaneReplaceable;
                    if (!expectedPlaceEmptyOrReplaceable || !visibleLaneEmptyOrReplaceable) {
                        PlacementIntentState.auditReject("EXPECTED_PLACE_NOT_REPLACEABLE", snapshot,
                                slabbed$placementIntentContextDetails(
                                        context,
                                        finalOwnerPos,
                                        finalOwnerState,
                                        side,
                                        true,
                                        true,
                                        finalTargetValidation,
                                        finalOwnerOccupiedNonReplaceable,
                                        snapshot.expectedPlacePos(),
                                        false,
                                        "expected_place_not_replaceable",
                                        "reject=EXPECTED_PLACE_NOT_REPLACEABLE"
                                                + " expectedPlaceState="
                                                + slabbed$placementIntentSafe(expectedPlaceState)
                                                + " visibleLanePos=" + slabbed$placementIntentPos(visibleLanePos)
                                                + " visibleLaneState=" + slabbed$placementIntentSafe(visibleLaneState)));
                        clearReason = "VALIDATION_REJECT";
                        return null;
                    }
                    valid = true;
                    PlacementIntentState.auditApply(snapshot,
                            slabbed$placementIntentContextDetails(
                                    context,
                                    finalOwnerPos,
                                    finalOwnerState,
                                    side,
                                    true,
                                    true,
                                    finalTargetValidation,
                                    finalOwnerOccupiedNonReplaceable,
                                    snapshot.expectedPlacePos(),
                                    null,
                                    "apply",
                                    "remapStrategy=final_target_unknown_visible_lane"
                                            + " visibleLanePos=" + slabbed$placementIntentPos(visibleLanePos)
                                            + " visibleLaneEmptyOrReplaceable="
                                            + slabbed$placementIntentTri(visibleLaneEmptyOrReplaceable)
                                            + " remappedClickedPos=" + slabbed$placementIntentPos(visibleLanePos)
                                            + " remappedClickedFace=" + slabbed$placementIntentSafe(side)
                                            + " remappedHit=" + slabbed$placementIntentVec(visibleLaneHit)));
                    return visibleLaneContext;
                }
            }

            if (!expectedPlaceReplaceable) {
                PlacementIntentState.auditReject("EXPECTED_PLACE_NOT_REPLACEABLE", snapshot,
                        slabbed$placementIntentContextDetails(
                                context,
                                finalOwnerPos,
                                finalOwnerState,
                                side,
                                true,
                                true,
                                finalTargetValidation,
                                finalOwnerOccupiedNonReplaceable,
                                snapshot.expectedPlacePos(),
                                expectedPlaceEmptyOrReplaceable,
                                "expected_place_not_replaceable",
                                "reject=EXPECTED_PLACE_NOT_REPLACEABLE"
                                        + " expectedPlaceState=" + slabbed$placementIntentSafe(expectedPlaceState)));
                clearReason = "VALIDATION_REJECT";
                return null;
            }

            BlockHitResult remappedHit = new BlockHitResult(
                    expectedHitPos,
                    side,
                    snapshot.expectedPlacePos(),
                    context.isInside(),
                    false
            );
            valid = true;
            PlacementIntentState.auditApply(snapshot,
                    slabbed$placementIntentContextDetails(
                            context,
                            finalOwnerPos,
                            finalOwnerState,
                            side,
                            true,
                            true,
                            finalTargetValidation,
                            finalOwnerOccupiedNonReplaceable,
                            snapshot.expectedPlacePos(),
                            expectedPlaceEmptyOrReplaceable,
                            "apply",
                            "remappedClickedPos=" + slabbed$placementIntentPos(snapshot.expectedPlacePos())
                                    + " remappedClickedFace=" + slabbed$placementIntentSafe(side)
                                    + " remappedHit=" + slabbed$placementIntentVec(expectedHitPos)));
            return new UseOnContext(
                    context.getLevel(),
                    context.getPlayer(),
                    context.getHand(),
                    context.getItemInHand(),
                    remappedHit) {
            };
        } finally {
            if (valid) {
                PlacementIntentState.clear("AFTER_CONSUME");
            } else if (clearReason != null) {
                PlacementIntentState.clear(clearReason);
            }
        }
    }

    private static boolean slabbed$isLoweredSlabFacePlacement(BlockPlaceContext context, BlockState state) {
        if (!(state.getBlock() instanceof SlabBlock)
                || context.getClickedFace().getAxis().isVertical()) {
            return false;
        }
        Level world = context.getLevel();
        BlockPos targetPos = context.getClickedPos().relative(context.getClickedFace().getOpposite());
        BlockState targetState = world.getBlockState(targetPos);
        return slabbed$isLoweredSlab(targetState, world, targetPos)
                && state.hasProperty(SlabBlock.TYPE)
                && targetState.hasProperty(SlabBlock.TYPE)
                && SlabSupport.isCompatibleLoweredSlabLane(
                        targetState.getValue(SlabBlock.TYPE),
                        state.getValue(SlabBlock.TYPE));
    }

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void slabbed$rejectCompoundSlabSidePlacement(
            UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        BlockItem self = (BlockItem) (Object) this;
        slabbed$traceRepeatPlacementContext("useOn-head", context, context, "head");
        if (!(self.getBlock() instanceof SlabBlock)) {
            return;
        }
        BlockPos targetPos = context.getClickedPos();
        BlockState targetState = context.getLevel().getBlockState(targetPos);
        if (slabbed$isCompoundTopHit(context, targetPos, targetState)) {
            SlabSupport.CompoundSlabRemapDecision remapDecision = SlabSupport.findLegalCompoundSlabRemap(
                    context.getLevel(),
                    targetPos,
                    targetState,
                    context.getClickedFace(),
                    context.getClickLocation());
            if (remapDecision.legal()) {
                return;
            }
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }
        if (slabbed$isCompoundSideHit(context, targetPos, targetState)) {
            SlabSupport.CompoundSlabRemapDecision remapDecision = SlabSupport.findLegalCompoundSlabRemap(
                    context.getLevel(),
                    targetPos,
                    targetState,
                    context.getClickedFace(),
                    context.getClickLocation());
            if (remapDecision.legal()) {
                return;
            }
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    private static final Class<?>[] REMAP_ATTEMPT_PARAM_TYPES = new Class<?>[]{
            UseOnContext.class,
            boolean.class,
            boolean.class,
            boolean.class,
            boolean.class,
            boolean.class,
            double.class,
            boolean.class,
            boolean.class,
            String.class,
            Vec3.class,
            Direction.class,
            String.class
    };

    private static void slabbed$recordRemapAttempt(
            UseOnContext context,
            boolean itemIsSlab,
            boolean faceHorizontal,
            boolean targetIsSolid,
            boolean targetHasBlockEntity,
            boolean targetIsCraftingTable,
            double yOffset,
            boolean ordinaryLoweredFullBlockGuard,
            boolean remapped,
            String rejectionReason,
            Vec3 remappedHitPos,
            Direction effectiveSide,
            String hitDescriptor) {
        // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
    }

    private static boolean slabbed$repeatSeamTraceEnabled() {
        return Boolean.getBoolean(REPEAT_SEAM_TRACE_OPT_IN);
    }

    private static void slabbed$traceRepeatPlacementContext(
            String phase,
            UseOnContext incoming,
            UseOnContext outgoing,
            String decision
    ) {
        if (!slabbed$repeatSeamTraceEnabled() || incoming == null || incoming.getLevel() == null) {
            return;
        }
        Level world = incoming.getLevel();
        BlockPos incomingPos = incoming.getClickedPos();
        BlockState incomingState = world.getBlockState(incomingPos);
        BlockPos outgoingPos = outgoing == null ? incomingPos : outgoing.getClickedPos();
        Level outgoingLevel = outgoing == null ? world : outgoing.getLevel();
        BlockState outgoingState = outgoingLevel.getBlockState(outgoingPos);
        System.out.println("[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_CONTEXT]"
                + " phase=" + phase
                + " side=" + (world.isClientSide() ? "CLIENT" : "SERVER")
                + " incomingPos=" + incomingPos.toShortString()
                + " incomingFace=" + incoming.getClickedFace()
                + " incomingHit=" + incoming.getClickLocation()
                + " incomingState=" + incomingState
                + " incomingDy=" + SlabSupport.getYOffset(world, incomingPos, incomingState)
                + " outgoingPos=" + outgoingPos.toShortString()
                + " outgoingFace=" + (outgoing == null ? "null" : outgoing.getClickedFace())
                + " outgoingHit=" + (outgoing == null ? "null" : outgoing.getClickLocation())
                + " outgoingState=" + outgoingState
                + " outgoingDy=" + SlabSupport.getYOffset(outgoingLevel, outgoingPos, outgoingState)
                + " heldItem=" + BuiltInRegistries.ITEM.getKey(incoming.getItemInHand().getItem())
                + " decision=" + decision);
        if (phase.contains("exit")) {
            System.out.println("[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_EXIT]"
                    + " phase=" + phase
                    + " side=" + (world.isClientSide() ? "CLIENT" : "SERVER")
                    + " incomingPos=" + incomingPos.toShortString()
                    + " outgoingPos=" + outgoingPos.toShortString()
                    + " decision=" + decision);
        }
    }

    private static void slabbed$traceRepeatFinalization(
            BlockPlaceContext context,
            InteractionResult result,
            BlockState placedState
    ) {
        if (!slabbed$repeatSeamTraceEnabled() || context == null || context.getLevel() == null) {
            return;
        }
        Level world = context.getLevel();
        BlockPos placePos = context.getClickedPos();
        boolean durableDouble = placedState.getBlock() instanceof SlabBlock
                && placedState.hasProperty(SlabBlock.TYPE)
                && placedState.getValue(SlabBlock.TYPE) == SlabType.DOUBLE
                && Math.abs(SlabSupport.getYOffset(world, placePos, placedState) + 0.5d)
                <= LOWERED_VISUAL_BOUNDARY_EPSILON;
        System.out.println("[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_EXIT]"
                + " phase=finalization-return"
                + " side=" + (world.isClientSide() ? "CLIENT" : "SERVER")
                + " result=" + result
                + " accepted=" + (result != null && result.consumesAction())
                + " placePos=" + placePos.toShortString()
                + " face=" + context.getClickedFace()
                + " hit=" + context.getClickLocation()
                + " placedState=" + placedState
                + " placedDy=" + SlabSupport.getYOffset(world, placePos, placedState)
                + " durableDouble=" + durableDouble
                + " setBlockStateDurable=" + (durableDouble ? "YES" : "NO_OR_NOT_DOUBLE"));
    }

    private static UseOnContext slabbed$inspectReturn(
            UseOnContext incoming, UseOnContext outgoing, String reason
    ) {
        // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
        return outgoing;
    }

    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void slabbed$allowLoweredSlabLanePlayerOverlap(
            BlockPlaceContext context,
            BlockState state,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!slabbed$isLoweredSlabFacePlacement(context, state)) {
            return;
        }

        Level world = context.getLevel();
        Player player = context.getPlayer();
        BlockPos placePos = context.getClickedPos();
        if (player == null
                || !context.canPlace()
                || !state.canSurvive(world, placePos)) {
            return;
        }

        CollisionContext shapeContext = CollisionContext.of(player);
        if (world.isUnobstructed(state, placePos, shapeContext)) {
            return;
        }

        VoxelShape placementShape = state.getCollisionShape(world, placePos, shapeContext).move(placePos);
        if (placementShape.isEmpty()) {
            return;
        }

        boolean hitsPlacingPlayer = Shapes.joinIsNotEmpty(
                placementShape,
                Shapes.create(player.getBoundingBox()),
                BooleanOp.AND);
        if (hitsPlacingPlayer && world.isUnobstructed(player, placementShape)) {
            cir.setReturnValue(true);
        }
    }

    @ModifyArg(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/context/BlockPlaceContext;<init>(Lnet/minecraft/world/item/context/UseOnContext;)V"
            )
    )
    private UseOnContext slabbed$remapLoweredFullBlockSideHit(UseOnContext context) {
        COMPOUND_VISIBLE_SIDE_LOWER_INTENT.remove();
        COMPOUND_VISIBLE_SIDE_UPPER_INTENT.remove();
        COMPOUND_VISIBLE_SIDE_DOUBLE_INTENT.remove();
        COMPOUND_VISIBLE_OWNER_TOP_INTENT.remove();
        Direction originalSide = context.getClickedFace();
        Vec3 originalHitPos = context.getClickLocation();
        Direction effectiveSide = originalSide;
        String remapMode = originalSide.getAxis().isHorizontal() ? "horizontal_face" : "top_face";

        BlockPos targetPos = context.getClickedPos();
        BlockState targetState = context.getLevel().getBlockState(targetPos);
        UseOnContext visibleLaneContext = slabbed$placementIntentVisibleLaneContext(
                context,
                targetPos,
                targetState,
                originalSide,
                originalHitPos);
        if (visibleLaneContext != null) {
            slabbed$recordRemapAttempt(
                    context,
                    true,
                    true,
                    targetState.isSolidRender(),
                    targetState.getBlock() instanceof EntityBlock,
                    targetState.getBlock() instanceof CraftingTableBlock,
                    SlabSupport.getYOffset(context.getLevel(), targetPos, targetState),
                    false,
                    true,
                    "placement_intent_visible_lane",
                    visibleLaneContext.getClickLocation(),
                    originalSide,
                    remapMode);
            slabbed$traceRepeatPlacementContext("placement-exit", context, visibleLaneContext,
                    "exit=placement_intent_visible_lane");
            return slabbed$inspectReturn(context, visibleLaneContext, "placement_intent_visible_lane");
        }

        boolean itemIsSlab = ((BlockItem) (Object) this).getBlock() instanceof SlabBlock;
        if (!itemIsSlab) {
            slabbed$recordRemapAttempt(
                    context,
                    false,
                    false,
                    false,
                    false,
                    false,
                    0.0d,
                    false,
                    false,
                    "item_not_slab",
                    null,
                    null,
                    "none");
            return slabbed$inspectReturn(context, context, "item_not_slab");
        }

        slabbed$traceRepeatPlacementContext("placement-context", context, context,
                "initial targetState=" + targetState
                        + " targetDy=" + SlabSupport.getYOffset(context.getLevel(), targetPos, targetState));
        if (slabbed$isCompoundTopHit(context, targetPos, targetState)) {
            SlabSupport.CompoundSlabRemapDecision remapDecision = SlabSupport.findLegalCompoundSlabRemap(
                    context.getLevel(),
                    targetPos,
                    targetState,
                    originalSide,
                    originalHitPos);
            if (remapDecision.legal()
                    && "COMPOUND_VISIBLE_OWNER_TOP_SLAB".equals(remapDecision.reason())) {
                COMPOUND_VISIBLE_OWNER_TOP_INTENT.set(new CompoundVisibleOwnerTopIntent(
                        remapDecision.sourcePos(),
                        remapDecision.candidatePlacementPos()));
                slabbed$recordRemapAttempt(
                        context,
                        true,
                        false,
                        true,
                        false,
                        false,
                        SlabSupport.getYOffset(context.getLevel(), targetPos, targetState),
                        true,
                        true,
                        remapDecision.reason(),
                        originalHitPos,
                        originalSide,
                        "compound_visible_owner_top_slab");
            }
            return slabbed$inspectReturn(context, context, "compound_visible_owner_top_slab");
        }
        if (slabbed$isCompoundSideHit(context, targetPos, targetState)) {
            SlabSupport.CompoundSlabRemapDecision remapDecision = SlabSupport.findLegalCompoundSlabRemap(
                    context.getLevel(),
                    targetPos,
                    targetState,
                    originalSide,
                    originalHitPos);
            if (!remapDecision.legal()) {
                slabbed$recordRemapAttempt(
                        context,
                        true,
                        originalSide.getAxis().isHorizontal(),
                        targetState.isSolidRender(),
                        targetState.getBlock() instanceof EntityBlock,
                        targetState.getBlock() instanceof CraftingTableBlock,
                        SlabSupport.getYOffset(context.getLevel(), targetPos, targetState),
                        true,
                        false,
                        remapDecision.reason(),
                        null,
                        originalSide,
                        "compound_slab_remap");
                return slabbed$inspectReturn(context, context, remapDecision.reason());
            }
            if ("COMPOUND_VISIBLE_SIDE_LOWER_SLAB".equals(remapDecision.reason())) {
                COMPOUND_VISIBLE_SIDE_LOWER_INTENT.set(new CompoundVisibleSideLowerIntent(
                        remapDecision.sourcePos(),
                        remapDecision.candidatePlacementPos()));
            } else if ("COMPOUND_VISIBLE_SIDE_UPPER_SLAB".equals(remapDecision.reason())) {
                COMPOUND_VISIBLE_SIDE_UPPER_INTENT.set(new CompoundVisibleSideUpperIntent(
                        remapDecision.sourcePos(),
                        remapDecision.candidatePlacementPos()));
            } else if ("COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB".equals(remapDecision.reason())) {
                COMPOUND_VISIBLE_SIDE_DOUBLE_INTENT.set(new CompoundVisibleSideDoubleIntent(
                        remapDecision.sourcePos(),
                        remapDecision.candidatePlacementPos()));
            }

            double remappedY = slabbed$placementYForType(remapDecision.legalLanePos(), remapDecision.resultType());
            Vec3 remappedHitPos = slabbed$hitPosOnFace(
                    remapDecision.legalLanePos(),
                    originalSide,
                    originalHitPos,
                    remappedY);
            BlockPos remappedOwnerPos = remapDecision.legalLanePos();
            BlockPos outwardPlacementPos = remappedOwnerPos.relative(originalSide);
            BlockPos expectedVisibleLanePos = remappedOwnerPos.relative(originalSide.getOpposite());
            double visibleLaneY = slabbed$placementYForType(expectedVisibleLanePos, remapDecision.resultType());
            Vec3 visibleLaneHitPos = slabbed$hitPosOnFace(
                    expectedVisibleLanePos,
                    originalSide,
                    originalHitPos,
                    visibleLaneY);
            BlockState remappedOwnerState = context.getLevel().getBlockState(remappedOwnerPos);
            boolean remappedOwnerOccupiedNonReplaceable =
                    !remappedOwnerState.isAir()
                            && !slabbed$canPlaceInClickedCell(context, remappedOwnerPos, originalSide, remappedHitPos);
            boolean expectedVisibleLaneKnown = !expectedVisibleLanePos.equals(remappedOwnerPos)
                    && !expectedVisibleLanePos.equals(outwardPlacementPos);
            boolean currentOwnerWouldSecondHopAwayFromVisibleLane = outwardPlacementPos.equals(remapDecision.candidatePlacementPos())
                    && !outwardPlacementPos.equals(expectedVisibleLanePos);
            boolean replaceabilityGuardRemapped = false;
            if (remappedOwnerOccupiedNonReplaceable
                    && expectedVisibleLaneKnown
                    && currentOwnerWouldSecondHopAwayFromVisibleLane
                    && slabbed$canPlaceInClickedCell(context, expectedVisibleLanePos, originalSide, visibleLaneHitPos)) {
                remappedY = visibleLaneY;
                remappedHitPos = visibleLaneHitPos;
                replaceabilityGuardRemapped = true;
            }
            slabbed$recordRemapAttempt(
                    context,
                    true,
                    true,
                    true,
                    false,
                    false,
                    SlabSupport.getYOffset(context.getLevel(), targetPos, targetState),
                    true,
                    true,
                    remapDecision.reason(),
                    remappedHitPos,
                    originalSide,
                    "compound_slab_remap");
            BlockHitResult remappedHit = new BlockHitResult(
                    remappedHitPos,
                    originalSide,
                    replaceabilityGuardRemapped ? expectedVisibleLanePos : remapDecision.legalLanePos(),
                    context.isInside(),
                    false
            );
            UseOnContext remappedContext = new UseOnContext(
                    context.getLevel(),
                    context.getPlayer(),
                    context.getHand(),
                    context.getItemInHand(),
                    remappedHit) {
            };
            return slabbed$inspectReturn(context, remappedContext, "compound_slab_legal_lane_remap");
        }
        boolean targetIsSolid = targetState.isSolidRender();
        boolean targetIsLoweredSlab = slabbed$isLoweredSlab(targetState, context.getLevel(), targetPos);
        boolean targetSupportsTopMerge = targetState.getBlock() instanceof SlabBlock
                && targetState.getValue(SlabBlock.TYPE) == SlabType.TOP
                && originalSide == Direction.UP;
        if (targetSupportsTopMerge) {
            effectiveSide = Direction.DOWN;
        }
        boolean faceHorizontal = effectiveSide.getAxis().isHorizontal();
        if (!faceHorizontal && !targetSupportsTopMerge) {
            slabbed$recordRemapAttempt(
                    context,
                    true,
                    false,
                    false,
                    false,
                    false,
                    0.0d,
                    false,
                    false,
                    "face_not_horizontal",
                    null,
                    null,
                    "none");
            slabbed$traceRepeatPlacementContext("placement-exit", context, context,
                    "exit=face_not_horizontal targetSupportsTopMerge=" + targetSupportsTopMerge);
            return slabbed$inspectReturn(context, context, "face_not_horizontal");
        }
        boolean targetHasBlockEntity = targetState.getBlock() instanceof EntityBlock;
        boolean targetIsCraftingTable = targetState.getBlock() instanceof CraftingTableBlock;
        double yOffset = SlabSupport.getYOffset(context.getLevel(), targetPos, targetState);
        boolean ordinaryLoweredFullBlockGuard = targetIsSolid
                && !targetHasBlockEntity
                && !targetIsCraftingTable
                && yOffset < 0.0d;

        if (!targetIsSolid && !targetIsLoweredSlab) {
            slabbed$recordRemapAttempt(
                    context,
                    true,
                    true,
                    false,
                    targetHasBlockEntity,
                    targetIsCraftingTable,
                    yOffset,
                    ordinaryLoweredFullBlockGuard,
                    false,
                    "target_not_solid",
                    null,
                    effectiveSide,
                    remapMode);
            return slabbed$inspectReturn(context, context, "target_not_solid");
        }
        if (targetHasBlockEntity) {
            slabbed$recordRemapAttempt(
                    context,
                    true,
                    true,
                    true,
                    true,
                    targetIsCraftingTable,
                    yOffset,
                    ordinaryLoweredFullBlockGuard,
                    false,
                    "target_has_block_entity",
                    null,
                    effectiveSide,
                    remapMode);
            return slabbed$inspectReturn(context, context, "target_has_block_entity");
        }
        if (targetIsCraftingTable) {
            slabbed$recordRemapAttempt(
                    context,
                    true,
                    true,
                    true,
                    false,
                    true,
                    yOffset,
                    ordinaryLoweredFullBlockGuard,
                    false,
                    "target_is_crafting_table",
                    null,
                    effectiveSide,
                    remapMode);
            return slabbed$inspectReturn(context, context, "target_is_crafting_table");
        }
        if (yOffset >= 0.0d) {
            slabbed$recordRemapAttempt(
                    context,
                    true,
                    true,
                    true,
                    false,
                    false,
                    yOffset,
                    ordinaryLoweredFullBlockGuard,
                    false,
                    "y_offset_not_negative",
                    null,
                    effectiveSide,
                    remapMode);
            return slabbed$inspectReturn(context, context, "y_offset_not_negative");
        }

        BlockPos abovePos = targetPos.above();
        BlockState aboveState = context.getLevel().getBlockState(abovePos);
        boolean upperVisibleHitBelongsToAboveLoweredFullBlock =
                originalHitPos.y >= abovePos.getY()
                        && originalHitPos.y <= abovePos.getY() + 0.5d + LOWERED_VISUAL_BOUNDARY_EPSILON
                        && slabbed$isOrdinaryLoweredFullBlock(context, abovePos, aboveState);
        if (upperVisibleHitBelongsToAboveLoweredFullBlock) {
            targetPos = abovePos;
            targetState = aboveState;
            yOffset = SlabSupport.getYOffset(context.getLevel(), targetPos, targetState);
            ordinaryLoweredFullBlockGuard = true;
        }

        // Resolve legal state intent:
        // - lowered slab target: lane semantics (TOP/BOTTOM/DOUBLE) are source of truth.
        // - full block target: keep legacy geometric intent for 0.5S vs 1S law.
        SlabType expectedType;
        double remappedY;
        if (targetState.getBlock() instanceof SlabBlock) {
            if (originalSide == Direction.UP
                    && targetState.getValue(SlabBlock.TYPE) == SlabType.TOP) {
                expectedType = SlabType.DOUBLE;
            } else if (targetState.getValue(SlabBlock.TYPE) == SlabType.DOUBLE
                    && effectiveSide.getAxis().isHorizontal()) {
                expectedType = slabbed$getLoweredDoubleHitIntentType(targetPos, originalHitPos);
            } else {
                expectedType = slabbed$getExpectedLoweredSidePlacementType(targetState);
            }
            remappedY = slabbed$placementYForType(targetPos, expectedType);
        } else {
            double loweredVisualMidline = targetPos.getY() + yOffset + 0.5d;
            double loweredVisualUpperBoundary = targetPos.getY() + yOffset + 1.0d;
            boolean exactLoweredVisualBoundary = Math.abs(originalHitPos.y - loweredVisualUpperBoundary)
                    <= LOWERED_VISUAL_BOUNDARY_EPSILON;
            boolean upperHalfIntent = originalHitPos.y >= loweredVisualMidline && !exactLoweredVisualBoundary;
            expectedType = upperHalfIntent ? SlabType.TOP : SlabType.BOTTOM;
            remappedY = slabbed$placementYForType(targetPos, expectedType);
        }
        if (originalSide == Direction.UP && expectedType == SlabType.BOTTOM) {
            remappedY = targetPos.getY() + 0.501d;
        }
        Vec3 remappedHitPos = new Vec3(originalHitPos.x, remappedY, originalHitPos.z);
        slabbed$recordRemapAttempt(
                context,
                true,
                true,
                true,
                false,
                false,
                yOffset,
                ordinaryLoweredFullBlockGuard,
                true,
                "none",
                remappedHitPos,
                effectiveSide,
                remapMode);
        BlockHitResult remappedHit = new BlockHitResult(
                remappedHitPos,
                effectiveSide,
                targetPos,
                context.isInside(),
                false
        );

        UseOnContext remappedContext = new UseOnContext(context.getLevel(), context.getPlayer(), context.getHand(), context.getItemInHand(), remappedHit) {
        };
        slabbed$traceRepeatPlacementContext("placement-exit", context, remappedContext,
                "exit=remapped expectedType=" + expectedType + " remappedY=" + remappedY);
        return slabbed$inspectReturn(context, remappedContext, "remapped");
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void slabbed$anchorLoweredFullBlockSidePlacement(
            BlockPlaceContext context,
            CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir
    ) {
        BlockItem self = (BlockItem) (Object) this;
        boolean heldIsSlab = self.getBlock() instanceof SlabBlock;
        if (!cir.getReturnValue().consumesAction()) {
            // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
            return;
        }

        Level world = context.getLevel();
        BlockPos placePos = context.getClickedPos();
        BlockState placedState = world.getBlockState(placePos);
        slabbed$traceRepeatFinalization(context, cir.getReturnValue(), placedState);
        if (heldIsSlab) {
            if (slabbed$isCompoundVisibleSideLowerSlabResult(context, placePos, placedState)) {
                BlockPos sourcePos = placePos.relative(context.getClickedFace().getOpposite());
                BlockState sourceState = world.getBlockState(sourcePos);
                boolean markerBefore = SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, placePos,
                        placedState);
                SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(world, placePos, placedState, sourcePos,
                        sourceState);
                boolean markerAfter = SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, placePos,
                        placedState);
                // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
            } else if (slabbed$isCompoundVisibleSideUpperSlabResult(context, placePos, placedState)) {
                BlockPos sourcePos = placePos.relative(context.getClickedFace().getOpposite());
                BlockState sourceState = world.getBlockState(sourcePos);
                boolean markerBefore = SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, placePos,
                        placedState);
                SlabAnchorAttachment.addCompoundVisibleSideUpperSlab(world, placePos, placedState, sourcePos,
                        sourceState);
                boolean markerAfter = SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, placePos,
                        placedState);
                // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
            } else if (slabbed$isCompoundVisibleSideDoubleSlabResult(context, placePos, placedState)) {
                BlockPos sourcePos = placePos.relative(context.getClickedFace().getOpposite());
                BlockState sourceState = world.getBlockState(sourcePos);
                boolean markerBefore = SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, placePos,
                        placedState);
                SlabAnchorAttachment.addCompoundVisibleSideDoubleSlab(world, placePos, placedState, sourcePos,
                        sourceState);
                boolean markerAfter = SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, placePos,
                        placedState);
                // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
            } else if (slabbed$isCompoundVisibleOwnerTopSlabResult(context, placePos, placedState)) {
                BlockPos sourcePos = placePos.below();
                BlockState sourceState = world.getBlockState(sourcePos);
                boolean markerBefore = SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, placePos,
                        placedState);
                SlabAnchorAttachment.addCompoundVisibleOwnerTopSlab(world, placePos, placedState, sourcePos,
                        sourceState);
                boolean markerAfter = SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, placePos,
                        placedState);
                // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
            } else if (slabbed$isPersistentLoweredBottomSlabCarrierCandidate(world, placePos, placedState)) {
                boolean compoundBefore = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos);
                boolean persistentBefore = SlabAnchorAttachment.isAnchored(world, placePos);
                SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, placePos, placedState);
                boolean compoundAfter = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos);
                boolean persistentAfter = SlabAnchorAttachment.isAnchored(world, placePos);
                // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
            } else {
                // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
            }
            COMPOUND_VISIBLE_SIDE_LOWER_INTENT.remove();
            COMPOUND_VISIBLE_SIDE_UPPER_INTENT.remove();
            COMPOUND_VISIBLE_SIDE_DOUBLE_INTENT.remove();
            COMPOUND_VISIBLE_OWNER_TOP_INTENT.remove();
            return;
        }

        if (context.getClickedFace() == Direction.UP) {
            BlockPos sourcePos = placePos.below();
            BlockState sourceState = world.getBlockState(sourcePos);
            boolean anchorBefore = SlabAnchorAttachment.isAnchored(world, placePos);
            boolean compoundBefore = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos);
            SlabAnchorAttachment.addTopOfCompoundFullAnchor(world, placePos, placedState, sourcePos, sourceState);
            boolean anchorAfter = SlabAnchorAttachment.isAnchored(world, placePos);
            boolean compoundAnchorAfter = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos);
            if (compoundAnchorAfter) {
                // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
                return;
            }
        }

        if (context.getClickedFace().getAxis().isVertical()) {
            // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
            return;
        }

        if (!SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, placePos, placedState)) {
            // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
            return;
        }

        BlockPos sourcePos = placePos.relative(context.getClickedFace().getOpposite());
        BlockState sourceState = world.getBlockState(sourcePos);
        boolean anchorBefore = SlabAnchorAttachment.isAnchored(world, placePos);
        boolean compoundBefore = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos);
        SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(world, placePos, placedState, sourcePos, sourceState);
        boolean anchorAfter = SlabAnchorAttachment.isAnchored(world, placePos);
        boolean compoundAfter = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos);
        // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
    }
}
