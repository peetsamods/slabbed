package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.ClientRenderDyPrediction;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.util.PlacementDepthPolicy;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.RuntimeDiagnostics;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemPlacementIntentMixin {

    @org.spongepowered.asm.mixin.Shadow
    protected abstract BlockState getPlacementState(BlockPlaceContext context);

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
    private static final ThreadLocal<Deque<Object[]>> PLACEMENT_HEIGHT_USE_ON_CALLS =
            new ThreadLocal<>();
    private static final ThreadLocal<Deque<Object[]>> PLACEMENT_HEIGHT_PLACE_FRAMES =
            new ThreadLocal<>();
    private static final ThreadLocal<Deque<RootPlacementAim>> ROOT_PLACEMENT_AIMS =
            new ThreadLocal<>();

    private record CompoundVisibleSideLowerIntent(BlockPos sourcePos, BlockPos candidatePos) {
    }

    private record CompoundVisibleSideUpperIntent(BlockPos sourcePos, BlockPos candidatePos) {
    }

    private record CompoundVisibleSideDoubleIntent(BlockPos sourcePos, BlockPos candidatePos) {
    }

    private record CompoundVisibleOwnerTopIntent(BlockPos sourcePos, BlockPos candidatePos) {
    }

    private record RootPlacementAim(
            BlockPos ownerPos,
            Direction clickedFace,
            Vec3 hitLocation,
            double ownerDy,
            PlacementDepthPolicy.Decision decision
    ) {
        private static RootPlacementAim capture(UseOnContext context) {
            Level world = context.getLevel();
            BlockPos ownerPos = context.getClickedPos().immutable();
            BlockState ownerState = world.getBlockState(ownerPos);
            double ownerDy = SlabSupport.getYOffset(world, ownerPos, ownerState);
            return new RootPlacementAim(
                    ownerPos,
                    context.getClickedFace(),
                    context.getClickLocation(),
                    ownerDy,
                    PlacementDepthPolicy.classify(ownerDy));
        }

        private double landingDy(BlockPos placedPos) {
            if (ownerPos.equals(placedPos)) {
                return ownerDy;
            }
            if (clickedFace == Direction.UP) {
                return hitLocation.y - placedPos.getY();
            }
            if (clickedFace == Direction.DOWN) {
                return hitLocation.y - (placedPos.getY() + 1.0d);
            }
            return ownerPos.getY() + ownerDy - placedPos.getY();
        }
    }

    private static boolean slabbed$isOrdinaryLoweredFullBlock(UseOnContext context, BlockPos pos, BlockState state) {
        return state.isSolidRender(context.getLevel(), pos)
                && !(state.getBlock() instanceof EntityBlock)
                && !(state.getBlock() instanceof CraftingTableBlock)
                && SlabSupport.getYOffset(context.getLevel(), pos, state) < 0.0d;
    }

    private static boolean slabbed$isLoweredSlab(BlockState state, Level world, BlockPos pos) {
        return state.getBlock() instanceof SlabBlock
                && SlabSupport.getYOffset(world, pos, state) < 0.0d;
    }

    private static boolean slabbed$isLoweredBottomSlabUndersideBand(
            UseOnContext context,
            BlockPos targetPos,
            BlockState targetState
    ) {
        if (context.getClickedFace().getAxis().isVertical()
                || !(targetState.getBlock() instanceof SlabBlock)
                || !targetState.hasProperty(SlabBlock.TYPE)
                || targetState.getValue(SlabBlock.TYPE) != SlabType.BOTTOM
                || !targetState.getFluidState().isEmpty()
                || Math.abs(SlabSupport.getYOffset(context.getLevel(), targetPos, targetState) + 0.5d)
                > LOWERED_VISUAL_BOUNDARY_EPSILON) {
            return false;
        }
        double hitY = context.getClickLocation().y;
        return hitY >= targetPos.getY() - 0.5d - LOWERED_VISUAL_BOUNDARY_EPSILON
                && hitY <= targetPos.getY() + LOWERED_VISUAL_BOUNDARY_EPSILON;
    }

    private static UseOnContext slabbed$remapLoweredBottomSlabUnderside(UseOnContext context) {
        BlockPos targetPos = context.getClickedPos();
        BlockState targetState = context.getLevel().getBlockState(targetPos);
        if (!slabbed$isLoweredBottomSlabUndersideBand(context, targetPos, targetState)) {
            return context;
        }
        Vec3 originalHit = context.getClickLocation();
        Vec3 remappedHitPos = new Vec3(
                originalHit.x,
                targetPos.getY() - 0.001d,
                originalHit.z);
        BlockHitResult remappedHit = new BlockHitResult(
                remappedHitPos,
                Direction.DOWN,
                targetPos,
                context.isInside());
        return new UseOnContext(
                context.getLevel(),
                context.getPlayer(),
                context.getHand(),
                context.getItemInHand(),
                remappedHit) {
        };
    }

    private static UseOnContext slabbed$remapPointedDripstoneSideHitToColumnContinuation(UseOnContext context) {
        if (context.getClickedFace().getAxis().isVertical()) {
            return context;
        }
        BlockPos targetPos = context.getClickedPos();
        BlockState targetState = context.getLevel().getBlockState(targetPos);
        if (!(targetState.getBlock() instanceof PointedDripstoneBlock)
                || !targetState.hasProperty(BlockStateProperties.VERTICAL_DIRECTION)) {
            return context;
        }
        Direction continuationFace = targetState.getValue(BlockStateProperties.VERTICAL_DIRECTION);
        double targetDy = SlabSupport.getYOffset(context.getLevel(), targetPos, targetState);
        double continuationY = continuationFace == Direction.UP
                ? targetPos.getY() + 1.0d + targetDy
                : targetPos.getY() + targetDy;
        Vec3 originalHit = context.getClickLocation();
        Vec3 remappedHitPos = new Vec3(originalHit.x, continuationY, originalHit.z);
        BlockHitResult remappedHit = new BlockHitResult(
                remappedHitPos,
                continuationFace,
                targetPos,
                context.isInside());
        return new UseOnContext(
                context.getLevel(),
                context.getPlayer(),
                context.getHand(),
                context.getItemInHand(),
                remappedHit) {
        };
    }

    private static boolean slabbed$isCompoundSideHit(UseOnContext context, BlockPos pos, BlockState state) {
        if (context.getClickedFace().getAxis().isVertical()) {
            return false;
        }
        double yOffset = SlabSupport.getYOffset(context.getLevel(), pos, state);
        if (state.getBlock() instanceof SlabBlock) {
            return SlabSupport.isCompoundVisibleSlabLaneOwner(context.getLevel(), pos, state)
                    && Math.abs(yOffset + 1.0d) <= LOWERED_VISUAL_BOUNDARY_EPSILON;
        }
        return SlabSupport.isCompoundVisibleFullBlockSource(context.getLevel(), pos, state);
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
        return SlabAnchorAttachment.isLoweredFullBlockSlabCarrierSupport(world, belowPos, below);
    }

    private static boolean slabbed$isCompoundVisibleOwnerTopSlabResult(
            BlockPlaceContext context,
            BlockPos placePos,
            BlockState placedState
    ) {
        if (context.getClickedFace() != Direction.UP
                || !(placedState.getBlock() instanceof SlabBlock)
                || !placedState.hasProperty(SlabBlock.TYPE)
                || placedState.getValue(SlabBlock.TYPE) != SlabType.BOTTOM
                || !placedState.getFluidState().isEmpty()) {
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
                || !(placedState.getBlock() instanceof SlabBlock)
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
                && SlabSupport.isCompoundVisibleFullBlockSource(context.getLevel(), sourcePos, sourceState);
    }

    private static boolean slabbed$isCompoundVisibleSideUpperSlabResult(
            BlockPlaceContext context,
            BlockPos placePos,
            BlockState placedState
    ) {
        if (context.getClickedFace().getAxis().isVertical()
                || !(placedState.getBlock() instanceof SlabBlock)
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
                && SlabSupport.isCompoundVisibleFullBlockSource(context.getLevel(), sourcePos, sourceState);
    }

    private static boolean slabbed$isCompoundVisibleSideDoubleSlabResult(
            BlockPlaceContext context,
            BlockPos placePos,
            BlockState placedState
    ) {
        if (context.getClickedFace().getAxis().isVertical()
                || !(placedState.getBlock() instanceof SlabBlock)
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
                && SlabSupport.isCompoundVisibleFullBlockSource(context.getLevel(), sourcePos, sourceState);
    }


    private static SlabType slabbed$getExpectedLoweredSidePlacementType(
            Level world,
            BlockPos targetPos,
            BlockState targetState,
            Vec3 hitPos
    ) {
        if (!targetState.hasProperty(SlabBlock.TYPE)) {
            return SlabType.BOTTOM;
        }
        SlabType targetType = targetState.getValue(SlabBlock.TYPE);
        if (targetType == SlabType.BOTTOM) {
            return SlabType.BOTTOM;
        }
        if (targetType == SlabType.TOP) {
            return SlabType.TOP;
        }
        return SlabType.TOP;
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
        slabbed$traceRepeatPlacementContext("useOnBlock-head", context, context, "head");
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
        RuntimeDiagnostics.invoke(
                "recordRemapAttempt",
                REMAP_ATTEMPT_PARAM_TYPES,
                context,
                itemIsSlab,
                faceHorizontal,
                targetIsSolid,
                targetHasBlockEntity,
                targetIsCraftingTable,
                yOffset,
                ordinaryLoweredFullBlockGuard,
                remapped,
                rejectionReason,
                remappedHitPos,
                effectiveSide,
                hitDescriptor);
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
        Level outgoingWorld = outgoing == null ? world : outgoing.getLevel();
        BlockState outgoingState = outgoingWorld.getBlockState(outgoingPos);
        Slabbed.LOGGER.info("[BETA4_REPEAT_SEAM_PLACEMENT_CONTEXT]"
                + " phase=" + phase
                + " side=" + (world.isClientSide() ? "CLIENT" : "SERVER")
                + " incomingPos=" + slabbed$shortPos(incomingPos)
                + " incomingFace=" + incoming.getClickedFace()
                + " incomingHit=" + incoming.getClickLocation()
                + " incomingState=" + incomingState
                + " incomingDy=" + SlabSupport.getYOffset(world, incomingPos, incomingState)
                + " outgoingPos=" + slabbed$shortPos(outgoingPos)
                + " outgoingFace=" + (outgoing == null ? "null" : outgoing.getClickedFace())
                + " outgoingHit=" + (outgoing == null ? "null" : outgoing.getClickLocation())
                + " outgoingState=" + outgoingState
                + " outgoingDy=" + SlabSupport.getYOffset(outgoingWorld, outgoingPos, outgoingState)
                + " heldItem=" + BuiltInRegistries.ITEM.getKey(incoming.getItemInHand().getItem())
                + " decision=" + decision);
        if (phase.contains("exit")) {
            Slabbed.LOGGER.info("[BETA4_REPEAT_SEAM_PLACEMENT_EXIT]"
                    + " phase=" + phase
                    + " side=" + (world.isClientSide() ? "CLIENT" : "SERVER")
                    + " incomingPos=" + slabbed$shortPos(incomingPos)
                    + " outgoingPos=" + slabbed$shortPos(outgoingPos)
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
        Slabbed.LOGGER.info("[BETA4_REPEAT_SEAM_PLACEMENT_EXIT]"
                + " phase=finalization-return"
                + " side=" + (world.isClientSide() ? "CLIENT" : "SERVER")
                + " result=" + result
                + " accepted=" + (result != null && result.consumesAction())
                + " placePos=" + slabbed$shortPos(placePos)
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
        RuntimeDiagnostics.logManualPlacementIntent(incoming, outgoing, reason);
        RuntimeDiagnostics.logInspectIntent(incoming, outgoing, reason);
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

        VoxelShape placementShape = state.getCollisionShape(world, placePos, shapeContext)
                .move(placePos.getX(), placePos.getY(), placePos.getZ());
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

    @WrapMethod(method = "useOn")
    private InteractionResult slabbed$applyPlacementDepthPolicy(
            UseOnContext context,
            Operation<InteractionResult> original
    ) {
        if (context == null) {
            return original.call(context);
        }

        RootPlacementAim aim = RootPlacementAim.capture(context);
        if (aim.decision().refusesPlacement()) {
            try {
                slabbed$emitRootDepthRefusalDiagnostics(context, aim);
            } catch (Throwable ignored) {
                // Optional observation must never replace the typed refusal.
            }
            COMPOUND_VISIBLE_SIDE_LOWER_INTENT.remove();
            COMPOUND_VISIBLE_SIDE_UPPER_INTENT.remove();
            COMPOUND_VISIBLE_SIDE_DOUBLE_INTENT.remove();
            COMPOUND_VISIBLE_OWNER_TOP_INTENT.remove();
            SlabAnchorAttachment.clearWysiwygFollowClickedLoweredFace();
            return InteractionResult.FAIL;
        }

        Deque<RootPlacementAim> aims = ROOT_PLACEMENT_AIMS.get();
        if (aims == null) {
            aims = new ArrayDeque<>();
            ROOT_PLACEMENT_AIMS.set(aims);
        }
        aims.push(aim);
        try {
            return original.call(context);
        } finally {
            aims.pop();
            if (aims.isEmpty()) {
                ROOT_PLACEMENT_AIMS.remove();
            }
        }
    }

    private static void slabbed$emitRootDepthRefusalDiagnostics(
            UseOnContext context,
            RootPlacementAim aim
    ) {
        if (!com.slabbed.util.SlabbedDiagnosticsBridge.enabled()) {
            return;
        }
        Level world = context.getLevel();
        BlockState ownerState = world.getBlockState(aim.ownerPos());
        java.util.LinkedHashMap<String, String> row = new java.util.LinkedHashMap<>();
        row.put("actionType", "place_block");
        row.put("side", world.isClientSide() ? "client" : "server");
        row.put("player", context.getPlayer() == null
                ? "none"
                : context.getPlayer().getName().getString());
        row.put("heldItem", String.valueOf(BuiltInRegistries.ITEM
                .getKey(context.getItemInHand().getItem())));
        row.put("clickedOwnerPos", aim.ownerPos().toShortString());
        row.put("clickedFace", aim.clickedFace().getSerializedName());
        row.put("clickedHitVec", slabbed$diagnosticVec(aim.hitLocation()));
        row.put("placementPos", "none");
        row.put("beforeState", ownerState.toString());
        row.put("beforeDy", slabbed$diagnosticDy(aim.ownerDy()));
        row.put("clickedOwnerLaneKind", slabbed$diagnosticLaneKind(
                world, aim.ownerPos(), ownerState));
        row.put("intentDy", slabbed$diagnosticDy(aim.ownerDy()));
        row.put("expectedResult", com.slabbed.util.PlacementVerificationVerdict.MUST_REFUSE_VANILLA);
        row.put("expectedRefusalReason", "below_targetable_floor");
        row.put("actualRefusalReason", "below_targetable_floor");
        row.put("actualResult", "Fail[below_targetable_floor]");
        row.put("placementRoute", "root_depth_policy");
        row.put("landingAuthority", "root_owner");
        com.slabbed.util.SlabbedDiagnosticsBridge.recordAction(row);
    }

    @Inject(method = "useOn", at = @At("HEAD"))
    private void slabbed$markWysiwygSideClickFollow(
            UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        BlockItem self = (BlockItem) (Object) this;
        boolean heldIsSlab = self.getBlock() instanceof SlabBlock;
        boolean heldIsConnector = SlabSupport.isBeta35FenceWallVariantContactObject(self.getBlock().defaultBlockState());
        if (!heldIsSlab && !heldIsConnector) {
            return;
        }
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clicked);
        Direction face = context.getClickedFace();
        double clickedDy = SlabSupport.getYOffset(level, clicked, clickedState);
        // Any STORABLE lowered face arms the follow, not only −0.5 (maintainer ruling,
        // 2026-09-01: WYSIWYG at any depth). Arming matters beyond the height itself: the
        // consume in freezeLoweredOnPlace is what keeps the structural FROZEN_FLAT stamp off a
        // landing whose exact deep fact the capture is about to record — unarmed, the two
        // writers of one transaction disagree and every follower of the stamped face floats.
        //
        // Storability is part of the arming predicate, not a downstream check: a face at a
        // non-half-step height (a slab seated on an enchanting table's 12/16 top, for example)
        // has no exact fact the capture could store, so an armed consume would leave the
        // landing with no anchor, no FLAT stamp, and no fact — unfrozen against LAW 1. Those
        // faces stay unarmed and land exactly as they did before the ruling. Connectors arm
        // alongside slabs because the consume site accepts them: unarmed, their deep landings
        // took a FLAT stamp from the freeze while the capture stored a deep fact — the exact
        // two-writer disagreement above. The UP-face branch stays at exactly −0.5: a stacked
        // slab's height comes from the seat derivation reading the real top face below it, and
        // the wider arming gave the consume a depth the capture never stores for stacks.
        boolean storableDepth = clickedDy < -1.0e-6d
                && SlabPlacementHeightAttachment.exactHalfSteps(clickedDy).isPresent();
        if (storableDepth) {
            if ((heldIsSlab || heldIsConnector) && face.getAxis().isHorizontal()) {
                SlabAnchorAttachment.markWysiwygFollowClickedLoweredFace(clicked.relative(face), clickedDy);
            } else if (heldIsSlab && face == Direction.UP
                    && clickedState.getBlock() instanceof SlabBlock
                    && Math.abs(clickedDy + 0.5d) < 1.0e-6d) {
                SlabAnchorAttachment.markWysiwygFollowClickedLoweredFace(clicked.above(), clickedDy);
            }
        } else if (Math.abs(clickedDy) < 1.0e-6d
                && heldIsSlab
                && face.getAxis().isHorizontal()
                && !clickedState.isAir()) {
            // The flush mirror of the branch above. A cantilever hung off a face at grid height
            // belongs at grid height: the clicked owner is the whole gesture, and whatever lies
            // under the landing cell was never pointed at. Armed only for a horizontal face, so
            // seating a slab ON something is untouched.
            SlabAnchorAttachment.markWysiwygFlatClickedFlushFace(clicked.relative(face));
        }
    }

    @Inject(method = "useOn", at = @At("RETURN"))
    private void slabbed$clearWysiwygSideClickFollow(
            UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        SlabAnchorAttachment.clearWysiwygFollowClickedLoweredFace();
        SlabAnchorAttachment.clearWysiwygFlatClickedFlushFace();
    }

    @Redirect(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/BlockItem;place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;"
            )
    )
    private InteractionResult slabbed$placeWithPlacementHeightFrame(
            BlockItem item,
            BlockPlaceContext context
    ) {
        Deque<Object[]> calls = PLACEMENT_HEIGHT_USE_ON_CALLS.get();
        if (calls == null) {
            calls = new ArrayDeque<>();
            PLACEMENT_HEIGHT_USE_ON_CALLS.set(calls);
        }
        Object[] call = {context, Boolean.FALSE};
        calls.push(call);
        try {
            return item.place(context);
        } finally {
            calls.pop();
            if (calls.isEmpty()) {
                PLACEMENT_HEIGHT_USE_ON_CALLS.remove();
            }
        }
    }

    @WrapMethod(method = "place")
    private InteractionResult slabbed$capturePlacementHeightPlace(
            BlockPlaceContext context,
            Operation<InteractionResult> original
    ) {
        Deque<Object[]> calls = PLACEMENT_HEIGHT_USE_ON_CALLS.get();
        boolean capture = false;
        if (calls != null && !calls.isEmpty()) {
            Object[] call = calls.peek();
            if (call[0] == context && call[1] == Boolean.FALSE) {
                call[1] = Boolean.TRUE;
                capture = true;
            }
        }

        Deque<Object[]> frames = PLACEMENT_HEIGHT_PLACE_FRAMES.get();
        if (frames == null) {
            frames = new ArrayDeque<>();
            PLACEMENT_HEIGHT_PLACE_FRAMES.set(frames);
        }
        Object[] frame = {capture, null, null, Boolean.FALSE, null, null};
        frames.push(frame);
        try {
            BlockState convertedTrampledSupport = capture
                    ? slabbed$convertTrampledSupportBeforePlacement(context)
                    : null;
            InteractionResult result = original.call(context);
            if (frame[3] == Boolean.TRUE) {
                result = InteractionResult.FAIL;
            }
            if (convertedTrampledSupport != null && !result.consumesAction()) {
                // The placement failed after the conversion (an obstruction discovered inside
                // place, or any later refusal). The conversion must not outlive the placement
                // it was made for: restore the exact prior state, moisture and all.
                context.getLevel().setBlockAndUpdate(
                        context.getClickedPos().below(), convertedTrampledSupport);
            }
            if (capture) {
                slabbed$capturePlacementHeight(
                        (BlockPlaceContext) frame[1],
                        result,
                        frame[2] instanceof Double admittedDeepDy ? admittedDeepDy : null,
                        frame[4] instanceof BlockState priorState ? priorState : null,
                        frame[5] instanceof String refusalReason ? refusalReason : null);
            }
            return result;
        } finally {
            frames.pop();
            if (frames.isEmpty()) {
                PLACEMENT_HEIGHT_PLACE_FRAMES.remove();
            }
        }
    }

    /**
     * A trampled sub-full support (dirt path / farmland, 15/16 tall) converts to dirt under a
     * placement this transaction manages, before the block lands (maintainer ruling,
     * 2026-09-01). Vanilla converts only under solid full blocks; everything else seated on the
     * trampled block's REAL face per FLUSH WINS, and the freeze hook then read that 1/16 sink
     * as "lowered" and anchored the piece a HALF BLOCK down. Converting first means every later
     * height decision in the same transaction sees a full block: seat 0, stamp FLAT, no anchor.
     * Runs on both logical sides so the client predicts the same landing the server stores.
     *
     * <p>The conversion is VIABILITY-GATED and REVERSIBLE, because it runs before the placement
     * it serves and {@code canPlace()} tests only replaceability, not survival: without the
     * gates, right-clicking farmland with seeds converted the farmland, the crop then refused
     * to sit on dirt, and every planting click destroyed one farmland block. Three rules:
     * a placement that is not viable at all converts nothing; a placement that NEEDS the
     * trampled block to survive (crops on farmland) converts nothing and proceeds vanilla; and
     * a conversion whose placement still fails afterwards is rolled back to the exact prior
     * state by the caller.
     *
     * @return the support state that was converted away, for the caller's rollback, or null
     *         when nothing was converted
     */
    @Nullable
    private BlockState slabbed$convertTrampledSupportBeforePlacement(BlockPlaceContext context) {
        if (!context.canPlace()) {
            return null;
        }
        Level world = context.getLevel();
        BlockPos supportPos = context.getClickedPos().below();
        BlockState support = world.getBlockState(supportPos);
        if (!(support.getBlock() instanceof net.minecraft.world.level.block.DirtPathBlock
                || support.getBlock() instanceof net.minecraft.world.level.block.FarmBlock)) {
            return null;
        }
        if (getPlacementState(context) == null) {
            return null;
        }
        world.setBlockAndUpdate(supportPos, Blocks.DIRT.defaultBlockState());
        if (getPlacementState(context) == null) {
            world.setBlockAndUpdate(supportPos, support);
            return null;
        }
        return support;
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
        BlockItem self = (BlockItem) (Object) this;
        boolean itemIsSlab = self.getBlock() instanceof SlabBlock;
        boolean itemIsPointedDripstone = self.getBlock() instanceof PointedDripstoneBlock;
        boolean itemIsConnector = SlabSupport.isBeta35FenceWallVariantContactObject(self.getBlock().defaultBlockState());
        if (!itemIsSlab) {
            if (itemIsPointedDripstone) {
                UseOnContext remappedDripstoneContext =
                        slabbed$remapPointedDripstoneSideHitToColumnContinuation(context);
                if (remappedDripstoneContext != context) {
                    slabbed$recordRemapAttempt(
                            context,
                            false,
                            true,
                            false,
                            false,
                            false,
                            SlabSupport.getYOffset(
                                    context.getLevel(),
                                    context.getClickedPos(),
                                    context.getLevel().getBlockState(context.getClickedPos())),
                            false,
                            true,
                            "pointed_dripstone_side_column_continuation",
                            remappedDripstoneContext.getClickLocation(),
                            remappedDripstoneContext.getClickedFace(),
                            "pointed_dripstone_side_column_continuation");
                    return slabbed$inspectReturn(
                            context,
                            remappedDripstoneContext,
                            "pointed_dripstone_side_column_continuation");
                }
            }
            if (itemIsConnector) {
                UseOnContext remappedConnectorContext =
                        slabbed$remapLoweredBottomSlabUnderside(context);
                if (remappedConnectorContext != context) {
                    slabbed$recordRemapAttempt(
                            context,
                            false,
                            true,
                            false,
                            false,
                            false,
                            SlabSupport.getYOffset(
                                    context.getLevel(),
                                    context.getClickedPos(),
                                    context.getLevel().getBlockState(context.getClickedPos())),
                            false,
                            true,
                            "connector_lowered_bottom_slab_underside",
                            remappedConnectorContext.getClickLocation(),
                            Direction.DOWN,
                            "connector_lowered_bottom_slab_underside");
                    return slabbed$inspectReturn(
                            context,
                            remappedConnectorContext,
                            "connector_lowered_bottom_slab_underside");
                }
            }
            if (self.getBlock() instanceof TrapDoorBlock) {
                UseOnContext remappedTrapdoorContext =
                        slabbed$remapLoweredBottomSlabUnderside(context);
                if (remappedTrapdoorContext != context) {
                    slabbed$recordRemapAttempt(
                            context,
                            false,
                            true,
                            false,
                            false,
                            false,
                            SlabSupport.getYOffset(
                                    context.getLevel(),
                                    context.getClickedPos(),
                                    context.getLevel().getBlockState(context.getClickedPos())),
                            false,
                            true,
                            "trapdoor_lowered_bottom_slab_underside",
                            remappedTrapdoorContext.getClickLocation(),
                            Direction.DOWN,
                            "trapdoor_lowered_bottom_slab_underside");
                    return slabbed$inspectReturn(
                            context,
                            remappedTrapdoorContext,
                            "trapdoor_lowered_bottom_slab_underside");
                }
            }
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

        Direction originalSide = context.getClickedFace();
        Vec3 originalHitPos = context.getClickLocation();
        Direction effectiveSide = originalSide;
        String remapMode = originalSide.getAxis().isHorizontal() ? "horizontal_face" : "top_face";

        BlockPos targetPos = context.getClickedPos();
        BlockState targetState = context.getLevel().getBlockState(targetPos);
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
                        targetState.isSolidRender(context.getLevel(), targetPos),
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
                    remapDecision.legalLanePos(),
                    context.isInside()
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
        boolean targetIsSolid = targetState.isSolidRender(context.getLevel(), targetPos);
        boolean targetIsLoweredSlab = slabbed$isLoweredSlab(targetState, context.getLevel(), targetPos);
        boolean targetSupportsTopMerge = targetState.getBlock() instanceof SlabBlock
                && targetState.getValue(SlabBlock.TYPE) == SlabType.TOP
                && originalSide == Direction.UP
                && !targetIsLoweredSlab;
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
                expectedType = slabbed$getExpectedLoweredSidePlacementType(
                        context.getLevel(),
                        targetPos,
                        targetState,
                        originalHitPos);
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
        BlockPos remappedBlockPos = targetPos;
        if (targetState.getBlock() instanceof SlabBlock
                && targetState.hasProperty(SlabBlock.TYPE)
                && targetState.getValue(SlabBlock.TYPE) == SlabType.TOP
                && expectedType == SlabType.BOTTOM
                && effectiveSide.getAxis().isHorizontal()) {
            remappedBlockPos = targetPos.relative(effectiveSide);
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
                remappedBlockPos,
                context.isInside()
        );

        UseOnContext remappedContext = new UseOnContext(context.getLevel(), context.getPlayer(), context.getHand(), context.getItemInHand(), remappedHit) {
        };
        slabbed$traceRepeatPlacementContext("placement-exit", context, remappedContext,
                "exit=remapped expectedType=" + expectedType + " remappedY=" + remappedY);
        return slabbed$inspectReturn(context, remappedContext, "remapped");
    }

    @WrapOperation(
            method = "place",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z"
            )
    )
    private boolean slabbed$admitActualPlacement(
            BlockItem item,
            BlockPlaceContext context,
            BlockState state,
            Operation<Boolean> original
    ) {
        Deque<Object[]> frames = PLACEMENT_HEIGHT_PLACE_FRAMES.get();
        Object[] frame = frames == null || frames.isEmpty() ? null : frames.peek();
        if (frame != null && frame[1] == null) {
            frame[1] = context;
            frame[4] = context.getLevel().getBlockState(context.getClickedPos());
        }

        Deque<RootPlacementAim> aims = ROOT_PLACEMENT_AIMS.get();
        RootPlacementAim aim = aims == null || aims.isEmpty() ? null : aims.peek();
        if (aim != null) {
            // The raw aim classifies against the ENVELOPE (an out-of-envelope aim still refuses
            // before any mutation), but the ADMITTED landing is bounded by the resolved floor
            // (maintainer ruling, 2026-08-21, matching the reference line): flush decides where
            // a course seats, the floor decides how deep derivation may carry it, and consent
            // deepens the floor to the envelope. Unfloored, a mixed slab/block column descends
            // per slab course; worse, once the flush value passed the floor the burial
            // exclusion bounced the placement to the legacy lane and the column turned
            // incoherent (-0.5, -1.0, -1.0, -0.5).
            double rawLandingDy = aim.landingDy(context.getClickedPos());
            PlacementDepthPolicy.Decision landingDecision = PlacementDepthPolicy.classify(rawLandingDy);
            double landingDy = Math.max(rawLandingDy, SlabSupport.minResolvedDy());
            // An aim that is not on the half-step lattice is not a refusal, it is an absence of
            // opinion. A crosshair landing on farmland, a path, a chest or an enchanting table
            // lands on that block's real top face, which is simply not a half step. Treating that
            // as a refusal does not decline to LOWER the placement - this wraps placeBlock, so
            // returning false means the block is never placed at all. Hand it back to vanilla.
            if (landingDecision == PlacementDepthPolicy.Decision.REFUSED_NON_CANONICAL) {
                return original.call(item, context, state);
            }
            if (landingDecision.refusesPlacement()) {
                if (frame != null) {
                    frame[3] = Boolean.TRUE;
                    // Report the decision that actually fired. This read
                    // "below_targetable_floor" for every refusal, so the recorder could not
                    // distinguish an out-of-envelope aim from any other refusal.
                    frame[5] = landingDecision.name();
                }
                return false;
            }
            // The aim is the landing authority for lowered-owner placements (maintainer ruling,
            // 2026-08-17: flush wins). Restricting this to owners deeper than -1.0 silently
            // discarded the correct flush answer for -0.5/-1.0 owners and let the legacy
            // resolver re-derive a clamped or single-step height into the fact store. Two lanes
            // keep their own height semantics and stay out of the admission: underside
            // attachments (a DOWN-face placement seats under, not on, the owner — its flat or
            // hanging lane owns the height) and same-block vertical continuations (a column
            // member inherits its column's height rather than seat arithmetic).
            boolean undersideAttachment = context.getClickedFace() == Direction.DOWN;
            BlockPos admissionPlacePos = context.getClickedPos();
            BlockState admissionOwnerState = context.getLevel().getBlockState(aim.ownerPos());
            // Slabs are seat-followers, never height-inheriting columns: a same-material slab
            // stacked above another slab must still be admitted, or it falls to the legacy
            // single-step lane. The continuation carve-out covers only true column blocks.
            // Compound-visible slab placements author their own -1.0 lane and must not have a
            // seat-arithmetic fact frozen over it.
            // The carve-out matches the lane's own scope: compound-visible intents author a
            // height for SLAB placements only, so only a slab placement may be excluded on
            // their account. An armed intent used to exclude ANY placement - clicking the top
            // of a -1.0 slab with a full block armed the owner-top intent, the admission
            // stepped aside, the compound lane authored nothing for the stone, and the
            // legacy lane seated it at -0.5, a full block above its support (the audited
            // position-blind-guard family, met live in the mixed-column repro, 2026-08-21).
            boolean compoundVisibleIntentArmed =
                    state.getBlock() instanceof SlabBlock
                            && (COMPOUND_VISIBLE_SIDE_LOWER_INTENT.get() != null
                                    || COMPOUND_VISIBLE_SIDE_UPPER_INTENT.get() != null
                                    || COMPOUND_VISIBLE_SIDE_DOUBLE_INTENT.get() != null
                                    || COMPOUND_VISIBLE_OWNER_TOP_INTENT.get() != null);
            boolean sameBlockVerticalContinuation =
                    !(state.getBlock() instanceof SlabBlock)
                            && state.getBlock() == admissionOwnerState.getBlock()
                            && (admissionPlacePos.equals(aim.ownerPos().above())
                                    || admissionPlacePos.equals(aim.ownerPos().below()));
            if (frame != null
                    && aim.decision().shiftedOwner()
                    && landingDecision == PlacementDepthPolicy.Decision.SUPPORTED
                    && !undersideAttachment
                    && !sameBlockVerticalContinuation
                    && !compoundVisibleIntentArmed
                    && !slabbed$landingBuriesIntoNeighborBelow(
                            context, state, admissionPlacePos, landingDy)) {
                frame[2] = landingDy;
            }
        }
        return original.call(item, context, state);
    }

    /**
     * A side-lane landing derives its height from the CLICKED owner, but the landing cell may
     * stand over its own, shallower support (a lowered owner at the edge of flush ground). An
     * admitted height that would sink the placed block below the top of the block under the
     * landing cell is a burial, not WYSIWYG — decline the admission and let the legacy lanes
     * seat the block against its real neighborhood.
     */
    private static boolean slabbed$landingBuriesIntoNeighborBelow(
            BlockPlaceContext context,
            BlockState placedState,
            BlockPos placePos,
            double landingDy
    ) {
        BlockPos belowPos = placePos.below();
        BlockState belowState = context.getLevel().getBlockState(belowPos);
        if (belowState.isAir()) {
            return false;
        }
        net.minecraft.world.phys.shapes.VoxelShape belowShape =
                belowState.getShape(context.getLevel(), belowPos);
        if (belowShape.isEmpty()) {
            return false;
        }
        double belowTopInLandingFrame = belowShape.bounds().maxY - 1.0d;
        net.minecraft.world.phys.shapes.VoxelShape placedShape =
                placedState.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        double placedRawBottom = placedShape.isEmpty() ? 0.0d : placedShape.bounds().minY;
        return landingDy + placedRawBottom < belowTopInLandingFrame - 1.0e-6d;
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void slabbed$anchorLoweredFullBlockSidePlacement(
            BlockPlaceContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        BlockItem self = (BlockItem) (Object) this;
        boolean heldIsSlab = self.getBlock() instanceof SlabBlock;
        if (!cir.getReturnValue().consumesAction()) {
            RuntimeDiagnostics.recordPlace(
                    "finalization-return",
                    BuiltInRegistries.ITEM.getKey(self),
                    heldIsSlab,
                    context,
                    cir.getReturnValue(),
                    "anchorFinalization=skipped_result_not_accepted");
            RuntimeDiagnostics.recordCompoundFinalization(
                    "finalization-return",
                    BuiltInRegistries.ITEM.getKey(self),
                    heldIsSlab,
                    context,
                    cir.getReturnValue(),
                    "skipped_result_not_accepted",
                    context.getClickedPos().relative(context.getClickedFace().getOpposite()),
                    false,
                    false,
                    false,
                    false,
                    "placement_result_not_accepted");
            // An armed compound intent must not outlive its gesture: a non-consuming result has
            // no finalization pass to clear it, and a stale intent would silently decline the
            // next placement's admission.
            COMPOUND_VISIBLE_SIDE_LOWER_INTENT.remove();
            COMPOUND_VISIBLE_SIDE_UPPER_INTENT.remove();
            COMPOUND_VISIBLE_SIDE_DOUBLE_INTENT.remove();
            COMPOUND_VISIBLE_OWNER_TOP_INTENT.remove();
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
                RuntimeDiagnostics.recordPlace(
                        "finalization-return",
                        BuiltInRegistries.ITEM.getKey(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "anchorFinalization=ran_compound_visible_side_lower_slab markerBefore="
                                + markerBefore
                                + " markerAfter=" + markerAfter
                                + " sourcePos=" + slabbed$shortPos(sourcePos)
                                + " sourceDy=" + SlabSupport.getYOffset(world, sourcePos, sourceState));
                RuntimeDiagnostics.recordCompoundFinalization(
                        "finalization-return",
                        BuiltInRegistries.ITEM.getKey(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "ran_compound_visible_side_lower_slab",
                        sourcePos,
                        markerBefore,
                        markerAfter,
                        false,
                        false,
                        "COMPOUND_VISIBLE_SIDE_LOWER_SLAB");
            } else if (slabbed$isCompoundVisibleSideUpperSlabResult(context, placePos, placedState)) {
                BlockPos sourcePos = placePos.relative(context.getClickedFace().getOpposite());
                BlockState sourceState = world.getBlockState(sourcePos);
                boolean markerBefore = SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, placePos,
                        placedState);
                SlabAnchorAttachment.addCompoundVisibleSideUpperSlab(world, placePos, placedState, sourcePos,
                        sourceState);
                boolean markerAfter = SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, placePos,
                        placedState);
                RuntimeDiagnostics.recordPlace(
                        "finalization-return",
                        BuiltInRegistries.ITEM.getKey(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "anchorFinalization=ran_compound_visible_side_upper_slab markerBefore="
                                + markerBefore
                                + " markerAfter=" + markerAfter
                                + " sourcePos=" + slabbed$shortPos(sourcePos)
                                + " sourceDy=" + SlabSupport.getYOffset(world, sourcePos, sourceState));
                RuntimeDiagnostics.recordCompoundFinalization(
                        "finalization-return",
                        BuiltInRegistries.ITEM.getKey(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "ran_compound_visible_side_upper_slab",
                        sourcePos,
                        markerBefore,
                        markerAfter,
                        false,
                        false,
                        "COMPOUND_VISIBLE_SIDE_UPPER_SLAB");
            } else if (slabbed$isCompoundVisibleSideDoubleSlabResult(context, placePos, placedState)) {
                BlockPos sourcePos = placePos.relative(context.getClickedFace().getOpposite());
                BlockState sourceState = world.getBlockState(sourcePos);
                boolean markerBefore = SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, placePos,
                        placedState);
                SlabAnchorAttachment.addCompoundVisibleSideDoubleSlab(world, placePos, placedState, sourcePos,
                        sourceState);
                boolean markerAfter = SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, placePos,
                        placedState);
                RuntimeDiagnostics.recordPlace(
                        "finalization-return",
                        BuiltInRegistries.ITEM.getKey(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "anchorFinalization=ran_compound_visible_side_double_slab markerBefore="
                                + markerBefore
                                + " markerAfter=" + markerAfter
                                + " sourcePos=" + slabbed$shortPos(sourcePos)
                                + " sourceDy=" + SlabSupport.getYOffset(world, sourcePos, sourceState));
                RuntimeDiagnostics.recordCompoundFinalization(
                        "finalization-return",
                        BuiltInRegistries.ITEM.getKey(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "ran_compound_visible_side_double_slab",
                        sourcePos,
                        markerBefore,
                        markerAfter,
                        false,
                        false,
                        "COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB");
            } else if (slabbed$isCompoundVisibleOwnerTopSlabResult(context, placePos, placedState)) {
                BlockPos sourcePos = placePos.below();
                BlockState sourceState = world.getBlockState(sourcePos);
                boolean markerBefore = SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, placePos,
                        placedState);
                SlabAnchorAttachment.addCompoundVisibleOwnerTopSlab(world, placePos, placedState, sourcePos,
                        sourceState);
                boolean markerAfter = SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, placePos,
                        placedState);
                RuntimeDiagnostics.recordPlace(
                        "finalization-return",
                        BuiltInRegistries.ITEM.getKey(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "anchorFinalization=ran_compound_visible_owner_top_slab markerBefore="
                                + markerBefore
                                + " markerAfter=" + markerAfter
                                + " sourcePos=" + slabbed$shortPos(sourcePos)
                                + " sourceDy=" + SlabSupport.getYOffset(world, sourcePos, sourceState));
                RuntimeDiagnostics.recordCompoundFinalization(
                        "finalization-return",
                        BuiltInRegistries.ITEM.getKey(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "ran_compound_visible_owner_top_slab",
                        sourcePos,
                        markerBefore,
                        markerAfter,
                        false,
                        false,
                        "COMPOUND_VISIBLE_OWNER_TOP_SLAB");
            } else if (slabbed$isPersistentLoweredBottomSlabCarrierCandidate(world, placePos, placedState)) {
                boolean compoundBefore = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos);
                boolean persistentBefore = SlabAnchorAttachment.isAnchored(world, placePos);
                SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, placePos, placedState);
                boolean compoundAfter = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos);
                boolean persistentAfter = SlabAnchorAttachment.isAnchored(world, placePos);
                RuntimeDiagnostics.recordPlace(
                        "finalization-return",
                        BuiltInRegistries.ITEM.getKey(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "anchorFinalization=ran_update_persistent_lowered_slab_carrier carrierAfter="
                                + SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, placePos, placedState));
                RuntimeDiagnostics.recordCompoundFinalization(
                        "finalization-return",
                        BuiltInRegistries.ITEM.getKey(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "ran_update_persistent_lowered_slab_carrier",
                        placePos.below(),
                        compoundBefore,
                        compoundAfter,
                        persistentBefore,
                        persistentAfter,
                        "held_slab_persistent_bottom_carrier_candidate");
            } else {
                RuntimeDiagnostics.recordPlace(
                        "finalization-return",
                        BuiltInRegistries.ITEM.getKey(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "anchorFinalization=skipped_slab_not_persistent_carrier_candidate");
                RuntimeDiagnostics.recordCompoundFinalization(
                        "finalization-return",
                        BuiltInRegistries.ITEM.getKey(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "rejected_compound_slab_side",
                        placePos.relative(context.getClickedFace().getOpposite()),
                        SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos),
                        SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos),
                        SlabAnchorAttachment.isAnchored(world, placePos),
                        SlabAnchorAttachment.isAnchored(world, placePos),
                        "held_slab_not_persistent_bottom_carrier_candidate");
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
                RuntimeDiagnostics.recordPlace(
                        "finalization-return",
                        BuiltInRegistries.ITEM.getKey(self),
                        false,
                        context,
                        cir.getReturnValue(),
                        "anchorFinalization=ran_top_of_compound_full_anchor anchorBefore="
                                + anchorBefore
                                + " anchorAfter=" + anchorAfter
                                + " compoundAnchorAfter=" + compoundAnchorAfter
                                + " sourcePos=" + slabbed$shortPos(sourcePos)
                                + " sourceDy=" + SlabSupport.getYOffset(world, sourcePos, sourceState));
                RuntimeDiagnostics.recordCompoundFinalization(
                        "finalization-return",
                        BuiltInRegistries.ITEM.getKey(self),
                        false,
                        context,
                        cir.getReturnValue(),
                        "ran_top_of_compound_full_anchor",
                        sourcePos,
                        compoundBefore,
                        compoundAnchorAfter,
                        anchorBefore,
                        anchorAfter,
                        "top_of_compound_full_anchor_attempt");
                return;
            }
        }

        if (context.getClickedFace().getAxis().isVertical()) {
            RuntimeDiagnostics.recordPlace(
                    "finalization-return",
                    BuiltInRegistries.ITEM.getKey(self),
                    false,
                    context,
                    cir.getReturnValue(),
                    "anchorFinalization=skipped_vertical_face");
            RuntimeDiagnostics.recordCompoundFinalization(
                    "finalization-return",
                    BuiltInRegistries.ITEM.getKey(self),
                    false,
                    context,
                    cir.getReturnValue(),
                    "skipped_vertical_face",
                    placePos.below(),
                    SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos),
                    SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos),
                    SlabAnchorAttachment.isAnchored(world, placePos),
                    SlabAnchorAttachment.isAnchored(world, placePos),
                    "vertical_face_not_side_finalization");
            return;
        }

        if (!SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, placePos, placedState)) {
            RuntimeDiagnostics.recordPlace(
                    "finalization-return",
                    BuiltInRegistries.ITEM.getKey(self),
                    false,
                    context,
                    cir.getReturnValue(),
                    "anchorFinalization=skipped_not_ordinary_full_block_anchor_candidate");
            RuntimeDiagnostics.recordCompoundFinalization(
                    "finalization-return",
                    BuiltInRegistries.ITEM.getKey(self),
                    false,
                    context,
                    cir.getReturnValue(),
                    "skipped_not_ordinary_full_block_anchor_candidate",
                    placePos.relative(context.getClickedFace().getOpposite()),
                    SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos),
                    SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos),
                    SlabAnchorAttachment.isAnchored(world, placePos),
                    SlabAnchorAttachment.isAnchored(world, placePos),
                    "placed_state_not_ordinary_full_block_anchor_candidate");
            return;
        }

        BlockPos sourcePos = placePos.relative(context.getClickedFace().getOpposite());
        BlockState sourceState = world.getBlockState(sourcePos);
        boolean anchorBefore = SlabAnchorAttachment.isAnchored(world, placePos);
        boolean compoundBefore = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos);
        SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(world, placePos, placedState, sourcePos, sourceState);
        boolean anchorAfter = SlabAnchorAttachment.isAnchored(world, placePos);
        boolean compoundAfter = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos);
        RuntimeDiagnostics.recordPlace(
                "finalization-return",
                BuiltInRegistries.ITEM.getKey(self),
                false,
                context,
                cir.getReturnValue(),
                "anchorFinalization=ran_side_adjacent_lowered_full_anchor anchorBefore="
                        + anchorBefore
                        + " anchorAfter=" + anchorAfter
                        + " sourcePos=" + slabbed$shortPos(sourcePos)
                        + " sourceDy=" + SlabSupport.getYOffset(world, sourcePos, sourceState));
        RuntimeDiagnostics.recordCompoundFinalization(
                "finalization-return",
                BuiltInRegistries.ITEM.getKey(self),
                false,
                context,
                cir.getReturnValue(),
                compoundAfter ? "ran_side_adjacent_compound_full_anchor" : "ran_side_adjacent_lowered_full_anchor",
                sourcePos,
                compoundBefore,
                compoundAfter,
                anchorBefore,
                anchorAfter,
                "side_adjacent_lowered_full_anchor_attempt");
    }

    private static void slabbed$capturePlacementHeight(
            BlockPlaceContext context,
            InteractionResult result,
            Double admittedDeepDy,
            BlockState priorState,
            String refusalReason
    ) {
        if (context == null || result == null) {
            return;
        }
        Level world = context.getLevel();
        BlockPos placePos = context.getClickedPos();
        BlockState placedState = world.getBlockState(placePos);
        boolean accepted = result.consumesAction();
        boolean storageExcluded = placedState.isAir()
                || placedState.hasProperty(BlockStateProperties.BED_PART)
                || placedState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || placedState.getBlock() instanceof PowderSnowBlock
                || SlabSupport.isThinTopLayer(placedState)
                // A compat-owned block keeps its own height UNLESS it is a slab, and a slab that
                // a player just placed is an ordinary slab whoever registered it (LAW.md clause
                // 2). Recording the fact here is what later distinguishes it from the same block
                // laid down by world generation, which never runs a placement transaction.
                || (CompatHooks.shouldSkipOffset(placedState)
                        && !SlabSupport.isTaggedSlab(placedState))
                || SlabSupport.isDynamicCeilingFollower(world, placePos, placedState);
        boolean storageEligible = accepted && !storageExcluded;
        // Hand the gesture to the seat derivation. The server also reaches the WYSIWYG answer via
        // the FROZEN_FLAT stamp its on-place writer lays down, but that writer never runs on the
        // client - so without the face here the client predicts a below-derived height, draws it,
        // and the placement visibly snaps when the server's stamp syncs.
        double captureDy = admittedDeepDy != null
                ? admittedDeepDy
                : SlabSupport.placementSeatDy(
                        world, placePos, placedState, context.getClickedFace());
        if (world.isClientSide() && storageEligible) {
            // The client resolved the same height the server is about to store. Keep it for the
            // chunk mesh alone until the real fact arrives, so the block does not draw the live
            // fallback height for a frame and then jump. Never read outside the mesh lookup.
            var predicted = SlabPlacementHeightAttachment.exactHalfSteps(captureDy);
            if (predicted.isPresent()) {
                ClientRenderDyPrediction.record(placePos.asLong(), predicted.getAsInt());
            }
        }
        if (!world.isClientSide() && storageEligible) {
            var halfSteps = SlabPlacementHeightAttachment.exactHalfSteps(captureDy);
            if (halfSteps.isPresent()) {
                LevelChunk ownerChunk = world.getChunk(placePos.getX() >> 4, placePos.getZ() >> 4);
                SlabPlacementHeightAttachment.putHalfSteps(ownerChunk, placePos, halfSteps.getAsInt());
                if (world instanceof ServerLevel serverLevel
                        && SlabPlacementHeightAttachment.storedHalfSteps(ownerChunk, placePos)
                                .orElse(Integer.MIN_VALUE) == halfSteps.getAsInt()) {
                    slabbed$settleHorizontalConnections(serverLevel, placePos);
                    placedState = world.getBlockState(placePos);
                }
            }
        }
        try {
            slabbed$emitPlacementDiagnostics(
                    context,
                    result,
                    placePos,
                    placedState,
                priorState,
                captureDy,
                admittedDeepDy,
                storageEligible,
                refusalReason);
        } catch (Throwable ignored) {
            // Placement and storage already completed; diagnostics cannot change that result.
        }
    }

    /**
     * Dev-diagnostics seam: a no-op unless the dev bridge provider is installed and the
     * recorder is enabled. Zero allocation before the enabled check — this project has
     * shipped a per-call-site lag bug from getting that ordering wrong twice.
     */
    private static void slabbed$emitPlacementDiagnostics(
            BlockPlaceContext context,
            InteractionResult result,
            BlockPos placePos,
            BlockState placedState,
            BlockState priorState,
            double captureDy,
            Double admittedDeepDy,
            boolean intentEligible,
            String refusalReason
    ) {
        if (!com.slabbed.util.SlabbedDiagnosticsBridge.enabled()) {
            return;
        }
        Level world = context.getLevel();
        if (!world.isClientSide() && result.consumesAction()) {
            com.slabbed.util.SlabbedDiagnosticsBridge.armPlacement(world, placePos, world.getGameTime());
        }
        Deque<RootPlacementAim> aims = ROOT_PLACEMENT_AIMS.get();
        RootPlacementAim aim = aims == null || aims.isEmpty() ? null : aims.peek();
        BlockPos ownerPos = aim == null ? context.getClickedPos() : aim.ownerPos();
        Direction clickedFace = aim == null ? context.getClickedFace() : aim.clickedFace();
        Vec3 hitLocation = aim == null ? context.getClickLocation() : aim.hitLocation();
        BlockState ownerState = world.getBlockState(ownerPos);
        Double intentDy = slabbed$diagnosticExpectedDy(
                context, aim, ownerState, placedState, admittedDeepDy, intentEligible);
        java.util.LinkedHashMap<String, String> row = new java.util.LinkedHashMap<>();
        row.put("actionType", "place_block");
        row.put("side", world.isClientSide() ? "client" : "server");
        row.put("player", context.getPlayer() == null
                ? "none"
                : context.getPlayer().getName().getString());
        row.put("heldItem", String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(context.getItemInHand().getItem())));
        row.put("clickedOwnerPos", ownerPos.toShortString());
        row.put("clickedFace", clickedFace.getSerializedName());
        row.put("clickedHitVec", slabbed$diagnosticVec(hitLocation));
        row.put("placementPos", placePos.toShortString());
        row.put("beforeState", ownerState.toString());
        row.put("beforeDy", slabbed$diagnosticDy(
                aim == null ? SlabSupport.getYOffset(world, ownerPos, ownerState) : aim.ownerDy()));
        row.put("placeBeforeState", priorState == null ? "unknown" : priorState.toString());
        row.put("placedState", placedState.toString());
        row.put("placedBlockId", BuiltInRegistries.BLOCK.getKey(placedState.getBlock()).toString());
        row.put("afterState", placedState.toString());
        // A client holding a prediction for this cell cannot observe post-placement geometry: the
        // prediction is deliberately invisible to Level views so a guess can never reach collision
        // or targeting. Every Level-read height below therefore reports PRE-fact geometry on such a
        // row - on correct placements as much as wrong ones - so reporting it under a post-placement
        // field name states a number that is guaranteed not to be the answer. clientDrawnDy carries
        // the one height this side can honestly claim; the rest stay unknown so that every consumer,
        // present and future, reads MISSING rather than a false mismatch. Do not re-add a numeric
        // fallback here: substituting per-consumer is what left sibling lanes reading the stale value
        // after the split lane alone was corrected.
        boolean clientPredictionPending =
                world.isClientSide() && !"none".equals(slabbed$clientDrawnDy(placePos));
        row.put("afterDy", clientPredictionPending
                ? "unknown"
                : slabbed$diagnosticDy(
                        result.consumesAction()
                                ? SlabSupport.getYOffset(world, placePos, placedState)
                                : captureDy));
        row.put("afterLaneKind", world.isClientSide()
                ? "client_pending_server_fact"
                : slabbed$diagnosticLaneKind(world, placePos, placedState));
        // What the CLIENT will actually draw, which is not what afterDy above reports. afterDy is
        // a Level-view read, and a client prediction is deliberately invisible to Level views so a
        // guess can never reach collision or targeting - so before the fact arrives, afterDy
        // reports the pre-placement geometry while the mesh already draws the resolved height.
        // Comparing the two sides on afterDy alone therefore calls every unsynced placement a
        // client/server split. This field is the one that may be compared against the server.
        row.put("clientDrawnDy",
                world.isClientSide() ? slabbed$clientDrawnDy(placePos) : "none");
        row.put("clickedOwnerLaneKind", slabbed$diagnosticLaneKind(world, ownerPos, ownerState));
        row.put("intentDy", intentDy == null ? "unknown" : slabbed$diagnosticDy(intentDy));
        row.put("expectedAfterDy", intentDy == null ? "unknown" : slabbed$diagnosticDy(intentDy));
        row.put("placementRoute", "block_item_use_on");
        // The product floor for a SUCCESSFUL placement is the targetable envelope, not the
        // consent cache: flush landings between -1.0 and -2.0 are legal without consent
        // (maintainer ruling, 2026-08-17), so reporting the cache here would false-red them.
        row.put("resolvedFloorDy", slabbed$diagnosticDy(SlabSupport.minResolvedDy()));
        row.put("landingAuthority", slabbed$diagnosticLandingAuthority(
                context, aim, ownerState, placedState, admittedDeepDy, intentEligible));
        row.put("actualResult", refusalReason == null
                ? result.toString()
                : "Fail[" + refusalReason + "]");
        if (refusalReason != null) {
            row.put("expectedResult", com.slabbed.util.PlacementVerificationVerdict.MUST_REFUSE_VANILLA);
            row.put("expectedRefusalReason", refusalReason);
            row.put("actualRefusalReason", refusalReason);
        }
        slabbed$putDiagnosticStoredFact(row, world, placePos);
        slabbed$putDiagnosticGeometry(
                row,
                world,
                placePos,
                placedState,
                aim,
                slabbed$diagnosticContactApplicable(
                        context, aim, ownerState, placedState, intentEligible),
                clientPredictionPending);
        row.put("stabilityVerdict", "NOT_RUN");
        com.slabbed.util.SlabbedDiagnosticsBridge.recordAction(row);
    }

    private static void slabbed$putDiagnosticStoredFact(
            java.util.LinkedHashMap<String, String> row,
            Level world,
            BlockPos pos
    ) {
        LevelChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        var halfSteps = SlabPlacementHeightAttachment.storedHalfSteps(chunk, pos);
        if (halfSteps.isEmpty()) {
            row.put("afterStoredDy", "none");
            row.put("afterStoredDyBits", "none");
            return;
        }
        double stored = halfSteps.getAsInt() * 0.5d;
        row.put("afterStoredDy", slabbed$diagnosticDy(stored));
        row.put("afterStoredDyBits", String.format(
                java.util.Locale.ROOT,
                "%016x",
                Double.doubleToRawLongBits(stored)));
    }

    private static void slabbed$putDiagnosticGeometry(
            java.util.LinkedHashMap<String, String> row,
            Level world,
            BlockPos pos,
            BlockState state,
            RootPlacementAim aim,
            boolean contactApplicable,
            boolean clientPredictionPending
    ) {
        // Same invariant as afterDy above: outline, collision and the contact plane are all Level
        // reads, so while the client holds an unsynced prediction they describe the cell BEFORE the
        // placement. An honest unknown keeps the verdict lanes MISSING; a number here reads as an
        // observation and makes correct placements look like height mismatches.
        if (clientPredictionPending) {
            row.put("outlineDy", "unknown");
            row.put("collisionDy", "unknown");
            return;
        }
        VoxelShape liveOutline = state.getShape(world, pos, CollisionContext.empty());
        VoxelShape rawOutline = state.getShape(
                EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        VoxelShape liveCollision = SlabSupport.collisionShapeForBroadphaseCell(
                state, world, pos, CollisionContext.empty());
        VoxelShape rawCollision = state.getCollisionShape(
                EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        row.put("outlineDy", slabbed$diagnosticShapeShift(rawOutline, liveOutline));
        row.put("collisionDy", slabbed$diagnosticShapeShift(rawCollision, liveCollision));
        if (!contactApplicable || aim == null || liveCollision.isEmpty()) {
            return;
        }
        AABB bounds = liveCollision.bounds();
        double actualPlane;
        if (aim.clickedFace() == Direction.UP) {
            actualPlane = pos.getY() + bounds.minY;
        } else if (aim.clickedFace() == Direction.DOWN) {
            actualPlane = pos.getY() + bounds.maxY;
        } else {
            return;
        }
        double expectedPlane = aim.hitLocation().y;
        row.put("expectedSupportPlane", slabbed$diagnosticDy(expectedPlane));
        row.put("actualContactPlane", slabbed$diagnosticDy(actualPlane));
        row.put("seatError", slabbed$diagnosticDy(actualPlane - expectedPlane));
    }

    private static String slabbed$diagnosticShapeShift(VoxelShape raw, VoxelShape live) {
        if (raw == null || live == null || raw.isEmpty() || live.isEmpty()) {
            return "unknown";
        }
        AABB rawBounds = raw.bounds();
        AABB liveBounds = live.bounds();
        double minShift = liveBounds.minY - rawBounds.minY;
        double maxShift = liveBounds.maxY - rawBounds.maxY;
        return Math.abs(minShift - maxShift) <= LOWERED_VISUAL_BOUNDARY_EPSILON
                ? slabbed$diagnosticDy(minShift)
                : "unknown";
    }

    private static String slabbed$diagnosticLaneKind(Level world, BlockPos pos, BlockState state) {
        LevelChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (SlabPlacementHeightAttachment.storedHalfSteps(chunk, pos).isPresent()) {
            return "stored_placement_height";
        }
        if (SlabAnchorAttachment.isFrozenFlat(world, pos)) {
            return "frozen_flat";
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            return "anchored_full_block";
        }
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)) {
            return "persistent_lowered_slab_carrier";
        }
        return "geometric_or_flush";
    }

    private static Double slabbed$diagnosticExpectedDy(
            BlockPlaceContext context,
            RootPlacementAim aim,
            BlockState ownerState,
            BlockState placedState,
            Double admittedDeepDy,
            boolean intentEligible
    ) {
        if (admittedDeepDy != null && Double.isFinite(admittedDeepDy)) {
            return admittedDeepDy;
        }
        if (slabbed$diagnosticUnderSlabConnector(context, aim, ownerState, placedState)) {
            return 0.0d;
        }
        return intentEligible && aim != null ? aim.landingDy(context.getClickedPos()) : null;
    }

    private static String slabbed$diagnosticLandingAuthority(
            BlockPlaceContext context,
            RootPlacementAim aim,
            BlockState ownerState,
            BlockState placedState,
            Double admittedDeepDy,
            boolean intentEligible
    ) {
        if (admittedDeepDy != null && Double.isFinite(admittedDeepDy)) {
            return "deep_admission_fact";
        }
        if (slabbed$diagnosticUnderSlabConnector(context, aim, ownerState, placedState)) {
            return "under_slab_connector_flat_lane";
        }
        return intentEligible && aim != null
                ? "root_placement_aim"
                : "undeclared_observation_only";
    }

    private static boolean slabbed$diagnosticContactApplicable(
            BlockPlaceContext context,
            RootPlacementAim aim,
            BlockState ownerState,
            BlockState placedState,
            boolean intentEligible
    ) {
        return intentEligible
                && aim != null
                && !slabbed$diagnosticUnderSlabConnector(context, aim, ownerState, placedState);
    }

    private static boolean slabbed$diagnosticUnderSlabConnector(
            BlockPlaceContext context,
            RootPlacementAim aim,
            BlockState ownerState,
            BlockState placedState
    ) {
        return aim != null
                && aim.clickedFace() == Direction.DOWN
                && ownerState.getBlock() instanceof SlabBlock
                && SlabSupport.isBeta35FenceWallVariantContactObject(placedState);
    }

    private static String slabbed$diagnosticDy(double value) {
        return Double.isFinite(value)
                ? String.format(java.util.Locale.ROOT, "%.6f", value)
                : "unknown";
    }

    /**
     * The height this client's chunk mesh will draw for a cell whose authoritative fact has not
     * arrived, or {@code none} when it holds no prediction for that cell and therefore draws the
     * live answer already reported as {@code afterDy}.
     */
    private static String slabbed$clientDrawnDy(BlockPos pos) {
        int halfSteps = ClientRenderDyPrediction.halfStepsOrAbsent(pos.asLong());
        return halfSteps == SlabPlacementHeightAttachment.ABSENT_HALF_STEPS
                        || halfSteps < Byte.MIN_VALUE
                        || halfSteps > Byte.MAX_VALUE
                ? "none"
                : slabbed$diagnosticDy(
                        SlabPlacementHeightAttachment.offsetForHalfSteps((byte) halfSteps));
    }

    private static String slabbed$diagnosticVec(Vec3 value) {
        return value == null
                ? "unknown"
                : slabbed$diagnosticDy(value.x) + ','
                        + slabbed$diagnosticDy(value.y) + ','
                        + slabbed$diagnosticDy(value.z);
    }

    private static void slabbed$settleHorizontalConnections(
            ServerLevel world,
            BlockPos placedPos
    ) {
        LevelChunk placedChunk = world.getChunkSource().getChunkNow(
                placedPos.getX() >> 4, placedPos.getZ() >> 4);
        if (placedChunk == null) {
            return;
        }
        BlockState originalPlaced = placedChunk.getBlockState(placedPos);
        if (!SlabSupport.hasHorizontalConnectionGeometry(originalPlaced)) {
            return;
        }

        BlockState settledPlaced = originalPlaced;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = placedPos.relative(direction);
            LevelChunk neighborChunk = world.getChunkSource().getChunkNow(
                    neighborPos.getX() >> 4, neighborPos.getZ() >> 4);
            if (neighborChunk == null) {
                continue;
            }
            BlockState candidate = settledPlaced.updateShape(
                    direction,
                    neighborChunk.getBlockState(neighborPos),
                    world,
                    placedPos,
                    neighborPos);
            if (candidate.getBlock() == originalPlaced.getBlock()) {
                settledPlaced = candidate;
            }
        }
        if (settledPlaced != originalPlaced) {
            world.setBlock(
                    placedPos,
                    settledPlaced,
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        BlockState publishedPlaced = placedChunk.getBlockState(placedPos);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = placedPos.relative(direction);
            LevelChunk neighborChunk = world.getChunkSource().getChunkNow(
                    neighborPos.getX() >> 4, neighborPos.getZ() >> 4);
            if (neighborChunk == null) {
                continue;
            }
            BlockState originalNeighbor = neighborChunk.getBlockState(neighborPos);
            if (!SlabSupport.hasHorizontalConnectionGeometry(originalNeighbor)) {
                continue;
            }
            BlockState settledNeighbor = originalNeighbor.updateShape(
                    direction.getOpposite(),
                    publishedPlaced,
                    world,
                    neighborPos,
                    placedPos);
            if (settledNeighbor != originalNeighbor
                    && settledNeighbor.getBlock() == originalNeighbor.getBlock()) {
                world.setBlock(
                        neighborPos,
                        settledNeighbor,
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    private static String slabbed$shortPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
