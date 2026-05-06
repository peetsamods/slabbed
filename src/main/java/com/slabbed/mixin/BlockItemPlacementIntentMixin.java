package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedAuditBridge;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
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

    private static final double UP_FACE_EDGE_BAND = 0.20d;
    private static final double LOWERED_VISUAL_BOUNDARY_EPSILON = 1.0e-6d;

    private static boolean slabbed$isOrdinaryLoweredFullBlock(ItemUsageContext context, BlockPos pos, BlockState state) {
        return state.isSolidBlock(context.getWorld(), pos)
                && !(state.getBlock() instanceof BlockEntityProvider)
                && !(state.getBlock() instanceof CraftingTableBlock)
                && SlabSupport.getYOffset(context.getWorld(), pos, state) == -0.5d;
    }

    private static boolean slabbed$isLoweredSlab(BlockState state, World world, BlockPos pos) {
        return state.getBlock() instanceof SlabBlock
                && SlabSupport.getYOffset(world, pos, state) < 0.0d;
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
                || SlabSupport.getYOffset(world, belowPos, below) == -0.5d);
    }

    private static SlabType slabbed$getExpectedLoweredSidePlacementType(BlockState targetState) {
        if (!targetState.contains(SlabBlock.TYPE)) {
            return SlabType.BOTTOM;
        }
        return targetState.get(SlabBlock.TYPE) == SlabType.BOTTOM
                ? SlabType.BOTTOM
                : SlabType.TOP;
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

    private static Direction slabbed$inferLoweredSideFromUpFaceHit(Vec3d hitPos, BlockPos targetPos) {
        double localX = hitPos.x - targetPos.getX();
        double localZ = hitPos.z - targetPos.getZ();
        if (localX < 0.0d || localX > 1.0d || localZ < 0.0d || localZ > 1.0d) {
            return null;
        }

        double distWest = localX;
        double distEast = 1.0d - localX;
        double distNorth = localZ;
        double distSouth = 1.0d - localZ;

        double min = distWest;
        Direction nearest = Direction.WEST;
        if (distEast < min) {
            min = distEast;
            nearest = Direction.EAST;
        }
        if (distNorth < min) {
            min = distNorth;
            nearest = Direction.NORTH;
        }
        if (distSouth < min) {
            min = distSouth;
            nearest = Direction.SOUTH;
        }

        return min <= UP_FACE_EDGE_BAND ? nearest : null;
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
        SlabbedAuditBridge.invoke(
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

    private static ItemUsageContext slabbed$inspectReturn(
            ItemUsageContext incoming, ItemUsageContext outgoing, String reason
    ) {
        SlabbedAuditBridge.logInspectIntent(incoming, outgoing, reason);
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

        ShapeContext shapeContext = ShapeContext.ofPlacement(player);
        if (world.canPlace(state, placePos, shapeContext)) {
            return;
        }

        VoxelShape placementShape = state.getCollisionShape(world, placePos, shapeContext).offset(placePos);
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

        Direction originalSide = context.getSide();
        Vec3d originalHitPos = context.getHitPos();
        Direction effectiveSide = originalSide;
        boolean inferredUpFaceLoweredSide = false;
        if (originalSide == Direction.UP) {
            Direction inferred = slabbed$inferLoweredSideFromUpFaceHit(originalHitPos, context.getBlockPos());
            if (inferred != null) {
                effectiveSide = inferred;
                inferredUpFaceLoweredSide = true;
            }
        }

        BlockPos targetPos = context.getBlockPos();
        BlockState targetState = context.getWorld().getBlockState(targetPos);
        boolean targetIsSolid = targetState.isSolidBlock(context.getWorld(), targetPos);
        boolean targetIsLoweredSlab = slabbed$isLoweredSlab(targetState, context.getWorld(), targetPos);
        boolean targetSupportsTopMerge = targetState.getBlock() instanceof SlabBlock
                && targetState.get(SlabBlock.TYPE) == SlabType.TOP
                && originalSide == Direction.UP;
        if (targetSupportsTopMerge && !inferredUpFaceLoweredSide) {
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
            return slabbed$inspectReturn(context, context, "face_not_horizontal");
        }
        boolean targetHasBlockEntity = targetState.getBlock() instanceof BlockEntityProvider;
        boolean targetIsCraftingTable = targetState.getBlock() instanceof CraftingTableBlock;
        double yOffset = SlabSupport.getYOffset(context.getWorld(), targetPos, targetState);
        boolean ordinaryLoweredFullBlockGuard = targetIsSolid
                && !targetHasBlockEntity
                && !targetIsCraftingTable
                && yOffset == -0.5d;

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
                    inferredUpFaceLoweredSide ? "up_face_edge" : "horizontal_face");
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
                    inferredUpFaceLoweredSide ? "up_face_edge" : "horizontal_face");
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
                    inferredUpFaceLoweredSide ? "up_face_edge" : "horizontal_face");
            return slabbed$inspectReturn(context, context, "target_is_crafting_table");
        }
        if (yOffset != -0.5d) {
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
                    "y_offset_not_-0.5",
                    null,
                    effectiveSide,
                    inferredUpFaceLoweredSide ? "up_face_edge" : "horizontal_face");
            return slabbed$inspectReturn(context, context, "y_offset_not_-0.5");
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
        if (targetState.getBlock() instanceof SlabBlock) {
            if (originalSide == Direction.UP
                    && targetState.get(SlabBlock.TYPE) == SlabType.TOP) {
                expectedType = SlabType.DOUBLE;
            } else if (targetState.get(SlabBlock.TYPE) == SlabType.DOUBLE
                    && effectiveSide.getAxis().isHorizontal()) {
                expectedType = slabbed$getLoweredDoubleHitIntentType(targetPos, originalHitPos);
            } else {
                expectedType = slabbed$getExpectedLoweredSidePlacementType(targetState);
            }
            remappedY = slabbed$placementYForType(targetPos, expectedType);
        } else {
            double loweredVisualUpperBoundary = targetPos.getY() + 0.5d;
            boolean exactLoweredVisualBoundary = Math.abs(originalHitPos.y - loweredVisualUpperBoundary)
                    <= LOWERED_VISUAL_BOUNDARY_EPSILON;
            boolean upperHalfIntent = originalHitPos.y >= targetPos.getY() && !exactLoweredVisualBoundary;
            expectedType = upperHalfIntent ? SlabType.TOP : SlabType.BOTTOM;
            remappedY = slabbed$placementYForType(targetPos, expectedType);
        }
        if (originalSide == Direction.UP && expectedType == SlabType.BOTTOM) {
            remappedY = targetPos.getY() + 0.501d;
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
                inferredUpFaceLoweredSide ? "up_face_edge" : "horizontal_face");
        BlockHitResult remappedHit = new BlockHitResult(
                remappedHitPos,
                effectiveSide,
                targetPos,
                context.hitsInsideBlock(),
                false
        );

        ItemUsageContext remappedContext = new ItemUsageContext(context.getWorld(), context.getPlayer(), context.getHand(), context.getStack(), remappedHit) {
        };
        return slabbed$inspectReturn(context, remappedContext, "remapped");
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void slabbed$anchorLoweredFullBlockSidePlacement(
            ItemPlacementContext context,
            CallbackInfoReturnable<net.minecraft.util.ActionResult> cir
    ) {
        if (!cir.getReturnValue().isAccepted()) {
            return;
        }

        World world = context.getWorld();
        BlockPos placePos = context.getBlockPos();
        BlockState placedState = world.getBlockState(placePos);
        if (((BlockItem) (Object) this).getBlock() instanceof SlabBlock) {
            if (slabbed$isPersistentLoweredBottomSlabCarrierCandidate(world, placePos, placedState)) {
                SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, placePos, placedState);
            }
            return;
        }

        if (context.getSide().getAxis().isVertical()) {
            return;
        }

        if (!SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, placePos, placedState)) {
            return;
        }

        BlockPos sourcePos = placePos.offset(context.getSide().getOpposite());
        BlockState sourceState = world.getBlockState(sourcePos);
        SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(world, placePos, placedState, sourcePos, sourceState);
    }
}
