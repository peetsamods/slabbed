package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.Beta4ManualLiveTrace;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.RuntimeDiagnostics;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
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

    private static boolean slabbed$isOrdinaryLoweredFullBlock(ItemUsageContext context, BlockPos pos, BlockState state) {
        return state.isSolidBlock(context.getWorld(), pos)
                && !(state.getBlock() instanceof BlockEntityProvider)
                && !(state.getBlock() instanceof CraftingTableBlock)
                && SlabSupport.getYOffset(context.getWorld(), pos, state) < 0.0d;
    }

    private static boolean slabbed$isLoweredSlab(BlockState state, World world, BlockPos pos) {
        return state.getBlock() instanceof SlabBlock
                && SlabSupport.getYOffset(world, pos, state) < 0.0d;
    }

    private static boolean slabbed$isLoweredBottomSlabUndersideBand(
            ItemUsageContext context,
            BlockPos targetPos,
            BlockState targetState
    ) {
        if (context.getSide().getAxis().isVertical()
                || !(targetState.getBlock() instanceof SlabBlock)
                || !targetState.contains(SlabBlock.TYPE)
                || targetState.get(SlabBlock.TYPE) != SlabType.BOTTOM
                || !targetState.getFluidState().isEmpty()
                || Math.abs(SlabSupport.getYOffset(context.getWorld(), targetPos, targetState) + 0.5d)
                > LOWERED_VISUAL_BOUNDARY_EPSILON) {
            return false;
        }
        double hitY = context.getHitPos().y;
        return hitY >= targetPos.getY() - 0.5d - LOWERED_VISUAL_BOUNDARY_EPSILON
                && hitY <= targetPos.getY() + LOWERED_VISUAL_BOUNDARY_EPSILON;
    }

    private static ItemUsageContext slabbed$remapTrapdoorLoweredBottomSlabUnderside(ItemUsageContext context) {
        BlockPos targetPos = context.getBlockPos();
        BlockState targetState = context.getWorld().getBlockState(targetPos);
        if (!slabbed$isLoweredBottomSlabUndersideBand(context, targetPos, targetState)) {
            return context;
        }
        Vec3d originalHit = context.getHitPos();
        Vec3d remappedHitPos = new Vec3d(
                originalHit.x,
                targetPos.getY() - 0.001d,
                originalHit.z);
        BlockHitResult remappedHit = new BlockHitResult(
                remappedHitPos,
                Direction.DOWN,
                targetPos,
                context.hitsInsideBlock());
        return new ItemUsageContext(
                context.getWorld(),
                context.getPlayer(),
                context.getHand(),
                context.getStack(),
                remappedHit) {
        };
    }

    private static boolean slabbed$isCompoundSideHit(ItemUsageContext context, BlockPos pos, BlockState state) {
        if (context.getSide().getAxis().isVertical()
                || state.getBlock() instanceof SlabBlock
                || !SlabAnchorAttachment.isCompoundFullBlockAnchor(context.getWorld(), pos)) {
            return false;
        }
        double yOffset = SlabSupport.getYOffset(context.getWorld(), pos, state);
        return Math.abs(yOffset + 1.0d) <= LOWERED_VISUAL_BOUNDARY_EPSILON;
    }

    private static boolean slabbed$isCompoundTopHit(ItemUsageContext context, BlockPos pos, BlockState state) {
        if (context.getSide() != Direction.UP
                || state.getBlock() instanceof SlabBlock
                || !SlabAnchorAttachment.isCompoundFullBlockAnchor(context.getWorld(), pos)) {
            return false;
        }
        double yOffset = SlabSupport.getYOffset(context.getWorld(), pos, state);
        return Math.abs(yOffset + 1.0d) <= LOWERED_VISUAL_BOUNDARY_EPSILON;
    }

    private static boolean slabbed$isPersistentLoweredBottomSlabCarrierCandidate(World world, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || state.get(SlabBlock.TYPE) != SlabType.BOTTOM
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);
        return SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, belowPos, below)
                && (SlabAnchorAttachment.isAnchored(world, belowPos)
                || SlabSupport.getYOffset(world, belowPos, below) < 0.0d);
    }

    private static boolean slabbed$isCompoundVisibleOwnerTopSlabResult(
            ItemPlacementContext context,
            BlockPos placePos,
            BlockState placedState
    ) {
        if (context.getSide() != Direction.UP
                || !(placedState.getBlock() instanceof SlabBlock)
                || !placedState.contains(SlabBlock.TYPE)
                || placedState.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
            return false;
        }
        CompoundVisibleOwnerTopIntent intent = COMPOUND_VISIBLE_OWNER_TOP_INTENT.get();
        return intent != null && placePos.equals(intent.candidatePos());
    }

    private static boolean slabbed$isCompoundVisibleSideLowerSlabResult(
            ItemPlacementContext context,
            BlockPos placePos,
            BlockState placedState
    ) {
        if (context.getSide().getAxis().isVertical()
                || !placedState.isOf(Blocks.STONE_SLAB)
                || !placedState.contains(SlabBlock.TYPE)
                || placedState.get(SlabBlock.TYPE) != SlabType.BOTTOM
                || !placedState.getFluidState().isEmpty()) {
            return false;
        }
        BlockPos sourcePos = placePos.offset(context.getSide().getOpposite());
        BlockState sourceState = context.getWorld().getBlockState(sourcePos);
        CompoundVisibleSideLowerIntent intent = COMPOUND_VISIBLE_SIDE_LOWER_INTENT.get();
        return intent != null
                && sourcePos.equals(intent.sourcePos())
                && placePos.equals(intent.candidatePos())
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(context.getWorld(), sourcePos, sourceState)
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(context.getWorld(), sourcePos)
                && Math.abs(SlabSupport.getYOffset(context.getWorld(), sourcePos, sourceState) + 1.0d)
                <= LOWERED_VISUAL_BOUNDARY_EPSILON;
    }

    private static boolean slabbed$isCompoundVisibleSideUpperSlabResult(
            ItemPlacementContext context,
            BlockPos placePos,
            BlockState placedState
    ) {
        if (context.getSide().getAxis().isVertical()
                || !placedState.isOf(Blocks.STONE_SLAB)
                || !placedState.contains(SlabBlock.TYPE)
                || placedState.get(SlabBlock.TYPE) != SlabType.TOP
                || !placedState.getFluidState().isEmpty()) {
            return false;
        }
        BlockPos sourcePos = placePos.offset(context.getSide().getOpposite());
        BlockState sourceState = context.getWorld().getBlockState(sourcePos);
        CompoundVisibleSideUpperIntent intent = COMPOUND_VISIBLE_SIDE_UPPER_INTENT.get();
        return intent != null
                && sourcePos.equals(intent.sourcePos())
                && placePos.equals(intent.candidatePos())
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(context.getWorld(), sourcePos, sourceState)
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(context.getWorld(), sourcePos)
                && Math.abs(SlabSupport.getYOffset(context.getWorld(), sourcePos, sourceState) + 1.0d)
                <= LOWERED_VISUAL_BOUNDARY_EPSILON;
    }

    private static boolean slabbed$isCompoundVisibleSideDoubleSlabResult(
            ItemPlacementContext context,
            BlockPos placePos,
            BlockState placedState
    ) {
        if (context.getSide().getAxis().isVertical()
                || !placedState.isOf(Blocks.STONE_SLAB)
                || !placedState.contains(SlabBlock.TYPE)
                || placedState.get(SlabBlock.TYPE) != SlabType.DOUBLE
                || !placedState.getFluidState().isEmpty()) {
            return false;
        }
        BlockPos sourcePos = placePos.offset(context.getSide().getOpposite());
        BlockState sourceState = context.getWorld().getBlockState(sourcePos);
        CompoundVisibleSideDoubleIntent intent = COMPOUND_VISIBLE_SIDE_DOUBLE_INTENT.get();
        return intent != null
                && sourcePos.equals(intent.sourcePos())
                && placePos.equals(intent.candidatePos())
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(context.getWorld(), sourcePos, sourceState)
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(context.getWorld(), sourcePos)
                && Math.abs(SlabSupport.getYOffset(context.getWorld(), sourcePos, sourceState) + 1.0d)
                <= LOWERED_VISUAL_BOUNDARY_EPSILON;
    }


    private static SlabType slabbed$getExpectedLoweredSidePlacementType(
            World world,
            BlockPos targetPos,
            BlockState targetState,
            Vec3d hitPos
    ) {
        if (!targetState.contains(SlabBlock.TYPE)) {
            return SlabType.BOTTOM;
        }
        SlabType targetType = targetState.get(SlabBlock.TYPE);
        if (targetType == SlabType.BOTTOM) {
            return SlabType.BOTTOM;
        }
        if (targetType == SlabType.TOP) {
            return SlabType.TOP;
        }
        return SlabType.TOP;
    }

    private static SlabType slabbed$getLoweredDoubleHitIntentType(BlockPos targetPos, Vec3d hitPos) {
        // Lowered DOUBLE occupies [y-0.5, y+0.5]. Its visual half split is at block y.
        double loweredMidY = targetPos.getY();
        boolean exactMid = Math.abs(hitPos.y - loweredMidY) <= LOWERED_VISUAL_BOUNDARY_EPSILON;
        return (hitPos.y < loweredMidY || exactMid) ? SlabType.BOTTOM : SlabType.TOP;
    }

    private static double slabbed$placementYForType(BlockPos targetPos, SlabType expectedType) {
        return targetPos.getY() + (expectedType == SlabType.BOTTOM ? 0.499d : 0.501d);
    }

    private static Vec3d slabbed$hitPosOnFace(BlockPos targetPos, Direction side, Vec3d originalHitPos, double y) {
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
        return new Vec3d(x, y, z);
    }

    private static boolean slabbed$isLoweredSlabFacePlacement(ItemPlacementContext context, BlockState state) {
        if (!(state.getBlock() instanceof SlabBlock)
                || context.getSide().getAxis().isVertical()) {
            return false;
        }
        World world = context.getWorld();
        BlockPos targetPos = context.getBlockPos().offset(context.getSide().getOpposite());
        BlockState targetState = world.getBlockState(targetPos);
        return slabbed$isLoweredSlab(targetState, world, targetPos)
                && state.contains(SlabBlock.TYPE)
                && targetState.contains(SlabBlock.TYPE)
                && SlabSupport.isCompatibleLoweredSlabLane(
                        targetState.get(SlabBlock.TYPE),
                        state.get(SlabBlock.TYPE));
    }

    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void slabbed$rejectCompoundSlabSidePlacement(
            ItemUsageContext context,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        BlockItem self = (BlockItem) (Object) this;
        slabbed$traceRepeatPlacementContext("useOnBlock-head", context, context, "head");
        if (!(self.getBlock() instanceof SlabBlock)) {
            return;
        }
        BlockPos targetPos = context.getBlockPos();
        BlockState targetState = context.getWorld().getBlockState(targetPos);
        if (slabbed$isCompoundTopHit(context, targetPos, targetState)) {
            SlabSupport.CompoundSlabRemapDecision remapDecision = SlabSupport.findLegalCompoundSlabRemap(
                    context.getWorld(),
                    targetPos,
                    targetState,
                    context.getSide(),
                    context.getHitPos());
            if (remapDecision.legal()) {
                return;
            }
            cir.setReturnValue(ActionResult.PASS);
            return;
        }
        if (slabbed$isCompoundSideHit(context, targetPos, targetState)) {
            SlabSupport.CompoundSlabRemapDecision remapDecision = SlabSupport.findLegalCompoundSlabRemap(
                    context.getWorld(),
                    targetPos,
                    targetState,
                    context.getSide(),
                    context.getHitPos());
            if (remapDecision.legal()) {
                return;
            }
            cir.setReturnValue(ActionResult.PASS);
        }
    }

    private static final Class<?>[] REMAP_ATTEMPT_PARAM_TYPES = new Class<?>[]{
            ItemUsageContext.class,
            boolean.class,
            boolean.class,
            boolean.class,
            boolean.class,
            boolean.class,
            double.class,
            boolean.class,
            boolean.class,
            String.class,
            Vec3d.class,
            Direction.class,
            String.class
    };

    private static void slabbed$recordRemapAttempt(
            ItemUsageContext context,
            boolean itemIsSlab,
            boolean faceHorizontal,
            boolean targetIsSolid,
            boolean targetHasBlockEntity,
            boolean targetIsCraftingTable,
            double yOffset,
            boolean ordinaryLoweredFullBlockGuard,
            boolean remapped,
            String rejectionReason,
            Vec3d remappedHitPos,
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
            ItemUsageContext incoming,
            ItemUsageContext outgoing,
            String decision
    ) {
        if (!slabbed$repeatSeamTraceEnabled() || incoming == null || incoming.getWorld() == null) {
            return;
        }
        World world = incoming.getWorld();
        BlockPos incomingPos = incoming.getBlockPos();
        BlockState incomingState = world.getBlockState(incomingPos);
        BlockPos outgoingPos = outgoing == null ? incomingPos : outgoing.getBlockPos();
        World outgoingWorld = outgoing == null ? world : outgoing.getWorld();
        BlockState outgoingState = outgoingWorld.getBlockState(outgoingPos);
        System.out.println("[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_CONTEXT]"
                + " phase=" + phase
                + " side=" + (world.isClient() ? "CLIENT" : "SERVER")
                + " incomingPos=" + incomingPos.toShortString()
                + " incomingFace=" + incoming.getSide()
                + " incomingHit=" + incoming.getHitPos()
                + " incomingState=" + incomingState
                + " incomingDy=" + SlabSupport.getYOffset(world, incomingPos, incomingState)
                + " outgoingPos=" + outgoingPos.toShortString()
                + " outgoingFace=" + (outgoing == null ? "null" : outgoing.getSide())
                + " outgoingHit=" + (outgoing == null ? "null" : outgoing.getHitPos())
                + " outgoingState=" + outgoingState
                + " outgoingDy=" + SlabSupport.getYOffset(outgoingWorld, outgoingPos, outgoingState)
                + " heldItem=" + Registries.ITEM.getId(incoming.getStack().getItem())
                + " decision=" + decision);
        if (phase.contains("exit")) {
            System.out.println("[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_EXIT]"
                    + " phase=" + phase
                    + " side=" + (world.isClient() ? "CLIENT" : "SERVER")
                    + " incomingPos=" + incomingPos.toShortString()
                    + " outgoingPos=" + outgoingPos.toShortString()
                    + " decision=" + decision);
        }
    }

    private static void slabbed$traceRepeatFinalization(
            ItemPlacementContext context,
            ActionResult result,
            BlockState placedState
    ) {
        if (!slabbed$repeatSeamTraceEnabled() || context == null || context.getWorld() == null) {
            return;
        }
        World world = context.getWorld();
        BlockPos placePos = context.getBlockPos();
        boolean durableDouble = placedState.getBlock() instanceof SlabBlock
                && placedState.contains(SlabBlock.TYPE)
                && placedState.get(SlabBlock.TYPE) == SlabType.DOUBLE
                && Math.abs(SlabSupport.getYOffset(world, placePos, placedState) + 0.5d)
                <= LOWERED_VISUAL_BOUNDARY_EPSILON;
        System.out.println("[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_EXIT]"
                + " phase=finalization-return"
                + " side=" + (world.isClient() ? "CLIENT" : "SERVER")
                + " result=" + result
                + " accepted=" + (result != null && result.isAccepted())
                + " placePos=" + placePos.toShortString()
                + " face=" + context.getSide()
                + " hit=" + context.getHitPos()
                + " placedState=" + placedState
                + " placedDy=" + SlabSupport.getYOffset(world, placePos, placedState)
                + " durableDouble=" + durableDouble
                + " setBlockStateDurable=" + (durableDouble ? "YES" : "NO_OR_NOT_DOUBLE"));
    }

    private static ItemUsageContext slabbed$inspectReturn(
            ItemUsageContext incoming, ItemUsageContext outgoing, String reason
    ) {
        Beta4ManualLiveTrace.logPlacementIntent(incoming, outgoing, reason);
        RuntimeDiagnostics.logInspectIntent(incoming, outgoing, reason);
        return outgoing;
    }

    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void slabbed$allowLoweredSlabLanePlayerOverlap(
            ItemPlacementContext context,
            BlockState state,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!slabbed$isLoweredSlabFacePlacement(context, state)) {
            return;
        }

        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        BlockPos placePos = context.getBlockPos();
        if (player == null
                || !context.canPlace()
                || !state.canPlaceAt(world, placePos)) {
            return;
        }

        ShapeContext shapeContext = ShapeContext.of(player);
        if (world.canPlace(state, placePos, shapeContext)) {
            return;
        }

        VoxelShape placementShape = state.getCollisionShape(world, placePos, shapeContext)
                .offset(placePos.getX(), placePos.getY(), placePos.getZ());
        if (placementShape.isEmpty()) {
            return;
        }

        boolean hitsPlacingPlayer = VoxelShapes.matchesAnywhere(
                placementShape,
                VoxelShapes.cuboid(player.getBoundingBox()),
                BooleanBiFunction.AND);
        if (hitsPlacingPlayer && world.doesNotIntersectEntities(player, placementShape)) {
            cir.setReturnValue(true);
        }
    }

    @ModifyArg(
            method = "useOnBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemPlacementContext;<init>(Lnet/minecraft/item/ItemUsageContext;)V"
            )
    )
    private ItemUsageContext slabbed$remapLoweredFullBlockSideHit(ItemUsageContext context) {
        COMPOUND_VISIBLE_SIDE_LOWER_INTENT.remove();
        COMPOUND_VISIBLE_SIDE_UPPER_INTENT.remove();
        COMPOUND_VISIBLE_SIDE_DOUBLE_INTENT.remove();
        COMPOUND_VISIBLE_OWNER_TOP_INTENT.remove();
        BlockItem self = (BlockItem) (Object) this;
        boolean itemIsSlab = self.getBlock() instanceof SlabBlock;
        if (!itemIsSlab) {
            if (self.getBlock() instanceof TrapdoorBlock) {
                ItemUsageContext remappedTrapdoorContext =
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
                                    context.getWorld(),
                                    context.getBlockPos(),
                                    context.getWorld().getBlockState(context.getBlockPos())),
                            false,
                            true,
                            "trapdoor_lowered_bottom_slab_underside",
                            remappedTrapdoorContext.getHitPos(),
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

        Direction originalSide = context.getSide();
        Vec3d originalHitPos = context.getHitPos();
        Direction effectiveSide = originalSide;
        String remapMode = originalSide.getAxis().isHorizontal() ? "horizontal_face" : "top_face";

        BlockPos targetPos = context.getBlockPos();
        BlockState targetState = context.getWorld().getBlockState(targetPos);
        slabbed$traceRepeatPlacementContext("placement-context", context, context,
                "initial targetState=" + targetState
                        + " targetDy=" + SlabSupport.getYOffset(context.getWorld(), targetPos, targetState));
        if (slabbed$isCompoundTopHit(context, targetPos, targetState)) {
            SlabSupport.CompoundSlabRemapDecision remapDecision = SlabSupport.findLegalCompoundSlabRemap(
                    context.getWorld(),
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
                        SlabSupport.getYOffset(context.getWorld(), targetPos, targetState),
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
                    context.getWorld(),
                    targetPos,
                    targetState,
                    originalSide,
                    originalHitPos);
            if (!remapDecision.legal()) {
                slabbed$recordRemapAttempt(
                        context,
                        true,
                        originalSide.getAxis().isHorizontal(),
                        targetState.isSolidBlock(context.getWorld(), targetPos),
                        targetState.getBlock() instanceof BlockEntityProvider,
                        targetState.getBlock() instanceof CraftingTableBlock,
                        SlabSupport.getYOffset(context.getWorld(), targetPos, targetState),
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
            Vec3d remappedHitPos = slabbed$hitPosOnFace(
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
                    SlabSupport.getYOffset(context.getWorld(), targetPos, targetState),
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
                    context.hitsInsideBlock()
            );
            ItemUsageContext remappedContext = new ItemUsageContext(
                    context.getWorld(),
                    context.getPlayer(),
                    context.getHand(),
                    context.getStack(),
                    remappedHit) {
            };
            return slabbed$inspectReturn(context, remappedContext, "compound_slab_legal_lane_remap");
        }
        boolean targetIsSolid = targetState.isSolidBlock(context.getWorld(), targetPos);
        boolean targetIsLoweredSlab = slabbed$isLoweredSlab(targetState, context.getWorld(), targetPos);
        boolean targetSupportsTopMerge = targetState.getBlock() instanceof SlabBlock
                && targetState.get(SlabBlock.TYPE) == SlabType.TOP
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
        boolean targetHasBlockEntity = targetState.getBlock() instanceof BlockEntityProvider;
        boolean targetIsCraftingTable = targetState.getBlock() instanceof CraftingTableBlock;
        double yOffset = SlabSupport.getYOffset(context.getWorld(), targetPos, targetState);
        // A lowered connecting block (fence/wall/pane standing on a lowered carrier) is NOT a solid
        // block, so the solid-only guard below used to bail here and let vanilla place the slab at
        // grid height — leaving it floating beside the visually-lowered fence. It IS a legitimate
        // lowered side-support carrier (the slab placed beside it inherits the -0.5 via getYOffset),
        // so engage the same lowered-carrier remap path the ordinary lowered full block uses.
        boolean targetIsLoweredConnectingCarrier = yOffset < 0.0d
                && (targetState.getBlock() instanceof FenceBlock
                        || targetState.getBlock() instanceof WallBlock
                        || targetState.getBlock() instanceof PaneBlock);
        boolean ordinaryLoweredFullBlockGuard = targetIsSolid
                && !targetHasBlockEntity
                && !targetIsCraftingTable
                && yOffset < 0.0d;

        if (!targetIsSolid && !targetIsLoweredSlab && !targetIsLoweredConnectingCarrier) {
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

        BlockPos abovePos = targetPos.up();
        BlockState aboveState = context.getWorld().getBlockState(abovePos);
        boolean upperVisibleHitBelongsToAboveLoweredFullBlock =
                originalHitPos.y >= abovePos.getY()
                        && originalHitPos.y <= abovePos.getY() + 0.5d + LOWERED_VISUAL_BOUNDARY_EPSILON
                        && slabbed$isOrdinaryLoweredFullBlock(context, abovePos, aboveState);
        if (upperVisibleHitBelongsToAboveLoweredFullBlock) {
            targetPos = abovePos;
            targetState = aboveState;
            yOffset = SlabSupport.getYOffset(context.getWorld(), targetPos, targetState);
            ordinaryLoweredFullBlockGuard = true;
        }

        // Resolve legal state intent:
        // - lowered slab target: lane semantics (TOP/BOTTOM/DOUBLE) are source of truth.
        // - full block target: keep legacy geometric intent for 0.5S vs 1S law.
        SlabType expectedType;
        double remappedY;
        BlockPos fullBlockSideCell = targetPos;
        if (targetState.getBlock() instanceof SlabBlock) {
            if (originalSide == Direction.UP
                    && targetState.get(SlabBlock.TYPE) == SlabType.TOP) {
                expectedType = SlabType.DOUBLE;
            } else if (targetState.get(SlabBlock.TYPE) == SlabType.DOUBLE
                    && effectiveSide.getAxis().isHorizontal()) {
                expectedType = slabbed$getLoweredDoubleHitIntentType(targetPos, originalHitPos);
            } else {
                expectedType = slabbed$getExpectedLoweredSidePlacementType(
                        context.getWorld(),
                        targetPos,
                        targetState,
                        originalHitPos);
            }
            remappedY = slabbed$placementYForType(targetPos, expectedType);
        } else {
            // Ordinary lowered full block at cell Y (rendered dy = yOffset; visual occupies
            // world-y [Y+yOffset, Y+yOffset+1]). The player aimed at one visual HALF; place the
            // slab so its RENDERED result fills exactly that half. The catch that bit us twice:
            // the slab's OWN dy matters. A vanilla slab placed beside a lowered block itself drops
            // to yOffset (it inherits the lowering); a Terrain Slabs slab stays at grid height.
            // So ask getYOffset — the render source of truth (safe to call here; we are NOT inside
            // getYOffsetInner) — what the slab will render at, then pick cell+type so the two agree:
            //   slab inherits (dy<0)   : upper→TOP cell Y, lower→BOTTOM cell Y (it drops into the half)
            //   slab grid-height (dy 0): upper→BOTTOM cell Y, lower→TOP cell Y-1 (fills the half as-is)
            double loweredVisualMidline = targetPos.getY() + yOffset + 0.5d;
            boolean upperHalfIntent = originalHitPos.y >= loweredVisualMidline;
            BlockPos sideNeighbor = targetPos.offset(effectiveSide);
            double predictedSlabDy = SlabSupport.getYOffset(
                    context.getWorld(), sideNeighbor, self.getBlock().getDefaultState());
            boolean slabInheritsLowering = predictedSlabDy < -1.0e-6;
            if (slabInheritsLowering) {
                expectedType = upperHalfIntent ? SlabType.TOP : SlabType.BOTTOM;
                remappedY = slabbed$placementYForType(targetPos, expectedType);
            } else if (upperHalfIntent) {
                expectedType = SlabType.BOTTOM;
                remappedY = slabbed$placementYForType(targetPos, SlabType.BOTTOM);
            } else {
                expectedType = SlabType.TOP;
                fullBlockSideCell = targetPos.down();
                remappedY = slabbed$placementYForType(fullBlockSideCell, SlabType.TOP);
            }
        }
        if (originalSide == Direction.UP && expectedType == SlabType.BOTTOM) {
            remappedY = targetPos.getY() + 0.501d;
        }
        BlockPos remappedBlockPos = fullBlockSideCell;
        if (targetState.getBlock() instanceof SlabBlock
                && targetState.contains(SlabBlock.TYPE)
                && targetState.get(SlabBlock.TYPE) == SlabType.TOP
                && expectedType == SlabType.BOTTOM
                && effectiveSide.getAxis().isHorizontal()) {
            remappedBlockPos = targetPos.offset(effectiveSide);
        }
        Vec3d remappedHitPos = new Vec3d(originalHitPos.x, remappedY, originalHitPos.z);
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
                context.hitsInsideBlock()
        );

        ItemUsageContext remappedContext = new ItemUsageContext(context.getWorld(), context.getPlayer(), context.getHand(), context.getStack(), remappedHit) {
        };
        slabbed$traceRepeatPlacementContext("placement-exit", context, remappedContext,
                "exit=remapped expectedType=" + expectedType + " remappedY=" + remappedY);
        return slabbed$inspectReturn(context, remappedContext, "remapped");
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void slabbed$anchorLoweredFullBlockSidePlacement(
            ItemPlacementContext context,
            CallbackInfoReturnable<net.minecraft.util.ActionResult> cir
    ) {
        BlockItem self = (BlockItem) (Object) this;
        boolean heldIsSlab = self.getBlock() instanceof SlabBlock;
        if (!cir.getReturnValue().isAccepted()) {
            RuntimeDiagnostics.recordPlace(
                    "finalization-return",
                    Registries.ITEM.getId(self),
                    heldIsSlab,
                    context,
                    cir.getReturnValue(),
                    "anchorFinalization=skipped_result_not_accepted");
            RuntimeDiagnostics.recordCompoundFinalization(
                    "finalization-return",
                    Registries.ITEM.getId(self),
                    heldIsSlab,
                    context,
                    cir.getReturnValue(),
                    "skipped_result_not_accepted",
                    context.getBlockPos().offset(context.getSide().getOpposite()),
                    false,
                    false,
                    false,
                    false,
                    "placement_result_not_accepted");
            return;
        }

        World world = context.getWorld();
        BlockPos placePos = context.getBlockPos();
        BlockState placedState = world.getBlockState(placePos);
        slabbed$traceRepeatFinalization(context, cir.getReturnValue(), placedState);
        if (heldIsSlab) {
            if (slabbed$isCompoundVisibleSideLowerSlabResult(context, placePos, placedState)) {
                BlockPos sourcePos = placePos.offset(context.getSide().getOpposite());
                BlockState sourceState = world.getBlockState(sourcePos);
                boolean markerBefore = SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, placePos,
                        placedState);
                SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(world, placePos, placedState, sourcePos,
                        sourceState);
                boolean markerAfter = SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, placePos,
                        placedState);
                RuntimeDiagnostics.recordPlace(
                        "finalization-return",
                        Registries.ITEM.getId(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "anchorFinalization=ran_compound_visible_side_lower_slab markerBefore="
                                + markerBefore
                                + " markerAfter=" + markerAfter
                                + " sourcePos=" + sourcePos.toShortString()
                                + " sourceDy=" + SlabSupport.getYOffset(world, sourcePos, sourceState));
                RuntimeDiagnostics.recordCompoundFinalization(
                        "finalization-return",
                        Registries.ITEM.getId(self),
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
                BlockPos sourcePos = placePos.offset(context.getSide().getOpposite());
                BlockState sourceState = world.getBlockState(sourcePos);
                boolean markerBefore = SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, placePos,
                        placedState);
                SlabAnchorAttachment.addCompoundVisibleSideUpperSlab(world, placePos, placedState, sourcePos,
                        sourceState);
                boolean markerAfter = SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, placePos,
                        placedState);
                RuntimeDiagnostics.recordPlace(
                        "finalization-return",
                        Registries.ITEM.getId(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "anchorFinalization=ran_compound_visible_side_upper_slab markerBefore="
                                + markerBefore
                                + " markerAfter=" + markerAfter
                                + " sourcePos=" + sourcePos.toShortString()
                                + " sourceDy=" + SlabSupport.getYOffset(world, sourcePos, sourceState));
                RuntimeDiagnostics.recordCompoundFinalization(
                        "finalization-return",
                        Registries.ITEM.getId(self),
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
                BlockPos sourcePos = placePos.offset(context.getSide().getOpposite());
                BlockState sourceState = world.getBlockState(sourcePos);
                boolean markerBefore = SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, placePos,
                        placedState);
                SlabAnchorAttachment.addCompoundVisibleSideDoubleSlab(world, placePos, placedState, sourcePos,
                        sourceState);
                boolean markerAfter = SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, placePos,
                        placedState);
                RuntimeDiagnostics.recordPlace(
                        "finalization-return",
                        Registries.ITEM.getId(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "anchorFinalization=ran_compound_visible_side_double_slab markerBefore="
                                + markerBefore
                                + " markerAfter=" + markerAfter
                                + " sourcePos=" + sourcePos.toShortString()
                                + " sourceDy=" + SlabSupport.getYOffset(world, sourcePos, sourceState));
                RuntimeDiagnostics.recordCompoundFinalization(
                        "finalization-return",
                        Registries.ITEM.getId(self),
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
                BlockPos sourcePos = placePos.down();
                BlockState sourceState = world.getBlockState(sourcePos);
                boolean markerBefore = SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, placePos,
                        placedState);
                SlabAnchorAttachment.addCompoundVisibleOwnerTopSlab(world, placePos, placedState, sourcePos,
                        sourceState);
                boolean markerAfter = SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, placePos,
                        placedState);
                RuntimeDiagnostics.recordPlace(
                        "finalization-return",
                        Registries.ITEM.getId(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "anchorFinalization=ran_compound_visible_owner_top_slab markerBefore="
                                + markerBefore
                                + " markerAfter=" + markerAfter
                                + " sourcePos=" + sourcePos.toShortString()
                                + " sourceDy=" + SlabSupport.getYOffset(world, sourcePos, sourceState));
                RuntimeDiagnostics.recordCompoundFinalization(
                        "finalization-return",
                        Registries.ITEM.getId(self),
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
                        Registries.ITEM.getId(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "anchorFinalization=ran_update_persistent_lowered_slab_carrier carrierAfter="
                                + SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, placePos, placedState));
                RuntimeDiagnostics.recordCompoundFinalization(
                        "finalization-return",
                        Registries.ITEM.getId(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "ran_update_persistent_lowered_slab_carrier",
                        placePos.down(),
                        compoundBefore,
                        compoundAfter,
                        persistentBefore,
                        persistentAfter,
                        "held_slab_persistent_bottom_carrier_candidate");
            } else {
                RuntimeDiagnostics.recordPlace(
                        "finalization-return",
                        Registries.ITEM.getId(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "anchorFinalization=skipped_slab_not_persistent_carrier_candidate");
                RuntimeDiagnostics.recordCompoundFinalization(
                        "finalization-return",
                        Registries.ITEM.getId(self),
                        true,
                        context,
                        cir.getReturnValue(),
                        "rejected_compound_slab_side",
                        placePos.offset(context.getSide().getOpposite()),
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

        if (context.getSide() == Direction.UP) {
            BlockPos sourcePos = placePos.down();
            BlockState sourceState = world.getBlockState(sourcePos);
            boolean anchorBefore = SlabAnchorAttachment.isAnchored(world, placePos);
            boolean compoundBefore = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos);
            SlabAnchorAttachment.addTopOfCompoundFullAnchor(world, placePos, placedState, sourcePos, sourceState);
            boolean anchorAfter = SlabAnchorAttachment.isAnchored(world, placePos);
            boolean compoundAnchorAfter = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos);
            if (compoundAnchorAfter) {
                RuntimeDiagnostics.recordPlace(
                        "finalization-return",
                        Registries.ITEM.getId(self),
                        false,
                        context,
                        cir.getReturnValue(),
                        "anchorFinalization=ran_top_of_compound_full_anchor anchorBefore="
                                + anchorBefore
                                + " anchorAfter=" + anchorAfter
                                + " compoundAnchorAfter=" + compoundAnchorAfter
                                + " sourcePos=" + sourcePos.toShortString()
                                + " sourceDy=" + SlabSupport.getYOffset(world, sourcePos, sourceState));
                RuntimeDiagnostics.recordCompoundFinalization(
                        "finalization-return",
                        Registries.ITEM.getId(self),
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

        if (context.getSide().getAxis().isVertical()) {
            RuntimeDiagnostics.recordPlace(
                    "finalization-return",
                    Registries.ITEM.getId(self),
                    false,
                    context,
                    cir.getReturnValue(),
                    "anchorFinalization=skipped_vertical_face");
            RuntimeDiagnostics.recordCompoundFinalization(
                    "finalization-return",
                    Registries.ITEM.getId(self),
                    false,
                    context,
                    cir.getReturnValue(),
                    "skipped_vertical_face",
                    placePos.down(),
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
                    Registries.ITEM.getId(self),
                    false,
                    context,
                    cir.getReturnValue(),
                    "anchorFinalization=skipped_not_ordinary_full_block_anchor_candidate");
            RuntimeDiagnostics.recordCompoundFinalization(
                    "finalization-return",
                    Registries.ITEM.getId(self),
                    false,
                    context,
                    cir.getReturnValue(),
                    "skipped_not_ordinary_full_block_anchor_candidate",
                    placePos.offset(context.getSide().getOpposite()),
                    SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos),
                    SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos),
                    SlabAnchorAttachment.isAnchored(world, placePos),
                    SlabAnchorAttachment.isAnchored(world, placePos),
                    "placed_state_not_ordinary_full_block_anchor_candidate");
            return;
        }

        BlockPos sourcePos = placePos.offset(context.getSide().getOpposite());
        BlockState sourceState = world.getBlockState(sourcePos);
        boolean anchorBefore = SlabAnchorAttachment.isAnchored(world, placePos);
        boolean compoundBefore = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos);
        SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(world, placePos, placedState, sourcePos, sourceState);
        boolean anchorAfter = SlabAnchorAttachment.isAnchored(world, placePos);
        boolean compoundAfter = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos);
        RuntimeDiagnostics.recordPlace(
                "finalization-return",
                Registries.ITEM.getId(self),
                false,
                context,
                cir.getReturnValue(),
                "anchorFinalization=ran_side_adjacent_lowered_full_anchor anchorBefore="
                        + anchorBefore
                        + " anchorAfter=" + anchorAfter
                        + " sourcePos=" + sourcePos.toShortString()
                        + " sourceDy=" + SlabSupport.getYOffset(world, sourcePos, sourceState));
        RuntimeDiagnostics.recordCompoundFinalization(
                "finalization-return",
                Registries.ITEM.getId(self),
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
}
