package com.slabbed.mixin;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.RuntimeDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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

    private static UseOnContext slabbed$remapTrapdoorLoweredBottomSlabUnderside(UseOnContext context) {
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

    private static boolean slabbed$isCompoundSideHit(UseOnContext context, BlockPos pos, BlockState state) {
        if (context.getClickedFace().getAxis().isVertical()) {
            return false;
        }
        double yOffset = SlabSupport.getYOffset(context.getLevel(), pos, state);
        if (state.getBlock() instanceof SlabBlock) {
            return SlabSupport.isCompoundVisibleSlabLaneOwner(context.getLevel(), pos, state)
                    && Math.abs(yOffset + 1.0d) <= LOWERED_VISUAL_BOUNDARY_EPSILON;
        }
        if (!SlabAnchorAttachment.isCompoundFullBlockAnchor(context.getLevel(), pos)) {
            return false;
        }
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
        Slabbed.LOGGER.info("[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_CONTEXT]"
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
            Slabbed.LOGGER.info("[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_EXIT]"
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
        Slabbed.LOGGER.info("[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_EXIT]"
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

    @Inject(method = "useOn", at = @At("HEAD"))
    private void slabbed$markWysiwygSideClickFollow(
            UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        BlockItem self = (BlockItem) (Object) this;
        if (!(self.getBlock() instanceof SlabBlock)) {
            return;
        }
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clicked);
        Direction face = context.getClickedFace();
        double clickedDy = SlabSupport.getYOffset(level, clicked, clickedState);
        if (Math.abs(clickedDy + 0.5d) < 1.0e-6d) {
            if (face.getAxis().isHorizontal()) {
                SlabAnchorAttachment.markWysiwygFollowClickedLoweredFace(clicked.relative(face));
            } else if (face == Direction.UP && clickedState.getBlock() instanceof SlabBlock) {
                SlabAnchorAttachment.markWysiwygFollowClickedLoweredFace(clicked.above());
            }
        }
    }

    @Inject(method = "useOn", at = @At("RETURN"))
    private void slabbed$clearWysiwygSideClickFollow(
            UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        SlabAnchorAttachment.clearWysiwygFollowClickedLoweredFace();
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
        if (!itemIsSlab) {
            if (self.getBlock() instanceof TrapDoorBlock) {
                UseOnContext remappedTrapdoorContext =
                        slabbed$remapTrapdoorLoweredBottomSlabUnderside(context);
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

    private static String slabbed$shortPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
