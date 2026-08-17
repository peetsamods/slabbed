package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import com.slabbed.placement.LandingResolver;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedAuditBridge;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(BlockItem.class)
public abstract class BlockItemPlacementIntentMixin {

    private static final ThreadLocal<Integer> ROOT_USE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<LandingResolver.PlacementAim> ROOT_AIM = new ThreadLocal<>();
    private static final ThreadLocal<Deque<PlacementFrame>> PLACEMENT_FRAMES =
            ThreadLocal.withInitial(ArrayDeque::new);

    private record CellSnapshot(BlockState priorState, SlabPlacementDyAttachment.PlacementDyFact priorFact) {
    }

    private static final class PlacementFrame {
        final LandingResolver.PlacementAim rootAim;
        final LinkedHashMap<BlockPos, CellSnapshot> snapshots = new LinkedHashMap<>();
        ItemPlacementContext actualContext;
        Map<BlockPos, Long> pending = Map.of();
        boolean actualTargetSeen;
        boolean pendingComputed;
        boolean clientPredictionPublished;

        PlacementFrame(LandingResolver.PlacementAim rootAim) {
            this.rootAim = rootAim;
        }
    }

    @WrapMethod(method = "useOnBlock(Lnet/minecraft/item/ItemUsageContext;)Lnet/minecraft/util/ActionResult;")
    private ActionResult slabbed$captureRootAim(ItemUsageContext context, Operation<ActionResult> original) {
        int depth = ROOT_USE_DEPTH.get();
        ROOT_USE_DEPTH.set(depth + 1);
        if (depth == 0) {
            ROOT_AIM.set(LandingResolver.captureAim(context));
        }
        try {
            if (depth == 0 && slabbed$refusesOutOfEnvelopeSlabPlacement()) {
                return ActionResult.FAIL;
            }
            return original.call(context);
        } finally {
            if (depth == 0) {
                ROOT_AIM.remove();
                ROOT_USE_DEPTH.remove();
            } else {
                ROOT_USE_DEPTH.set(depth);
            }
        }
    }

    @WrapMethod(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;")
    private ActionResult slabbed$placementTransaction(
            ItemPlacementContext context,
            Operation<ActionResult> original
    ) {
        PlacementFrame frame = new PlacementFrame(ROOT_AIM.get());
        Deque<PlacementFrame> frames = PLACEMENT_FRAMES.get();
        frames.push(frame);
        SlabPlacementDyAttachment.beginBlockItemTransaction();
        boolean accepted = false;
        try {
            ActionResult result = original.call(context);
            accepted = result != null && result.isAccepted();
            if (accepted) {
                if (frame.actualTargetSeen && frame.pendingComputed) {
                    slabbed$publish(frame);
                } else {
                    Slabbed.LOGGER.warn(
                            "[PLACEMENT] accepted placement had incomplete capture target={} pending={}",
                            frame.actualTargetSeen, frame.pendingComputed);
                }
            }
            return result;
        } finally {
            if (!accepted && frame.clientPredictionPublished) {
                SlabPlacementDyAttachment.clearClientPrediction(frame.pending.keySet());
            }
            SlabPlacementDyAttachment.endBlockItemTransaction();
            frames.pop();
            if (frames.isEmpty()) {
                PLACEMENT_FRAMES.remove();
            }
        }
    }

    @WrapOperation(
            method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/BlockItem;getPlacementContext(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/item/ItemPlacementContext;")
    )
    private ItemPlacementContext slabbed$captureActualContext(
            BlockItem instance,
            ItemPlacementContext context,
            Operation<ItemPlacementContext> original
    ) {
        ItemPlacementContext actual = original.call(instance, context);
        PlacementFrame frame = slabbed$currentFrame();
        if (frame != null && actual != null) {
            frame.actualContext = actual;
            frame.actualTargetSeen = true;
        }
        return actual;
    }

    @WrapOperation(
            method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/BlockItem;getPlacementState(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/block/BlockState;")
    )
    private BlockState slabbed$snapshotFinalCandidates(
            BlockItem instance,
            ItemPlacementContext context,
            Operation<BlockState> original
    ) {
        BlockState placementState = original.call(instance, context);
        PlacementFrame frame = slabbed$currentFrame();
        if (frame != null && placementState != null) {
            frame.actualContext = context;
            frame.actualTargetSeen = true;
            slabbed$snapshotCandidates(frame, context, placementState);
        }
        return placementState;
    }

    @WrapOperation(
            method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;decrementUnlessCreative(ILnet/minecraft/entity/LivingEntity;)V")
    )
    private void slabbed$computeBeforeConsume(
            ItemStack stack,
            int amount,
            LivingEntity entity,
            Operation<Void> original
    ) {
        PlacementFrame frame = slabbed$currentFrame();
        if (frame != null) {
            try {
                slabbed$computePending(frame);
                slabbed$publishClientPrediction(frame);
            } catch (RuntimeException failure) {
                frame.pending = Map.of();
                frame.pendingComputed = true;
                Slabbed.LOGGER.warn("[PLACEMENT] height capture failed closed", failure);
            }
        }
        original.call(stack, amount, entity);
    }

    private static PlacementFrame slabbed$currentFrame() {
        Deque<PlacementFrame> frames = PLACEMENT_FRAMES.get();
        return frames.isEmpty() ? null : frames.peek();
    }

    private static void slabbed$snapshotCandidates(
            PlacementFrame frame,
            ItemPlacementContext context,
            BlockState placementState
    ) {
        ArrayList<BlockPos> positions = new ArrayList<>();
        BlockPos primary = context.getBlockPos().toImmutable();
        positions.add(primary);
        if (placementState.contains(Properties.DOUBLE_BLOCK_HALF)) {
            positions.add(primary.up());
            positions.add(primary.down());
        }
        if (placementState.getBlock() instanceof BedBlock || placementState.contains(Properties.BED_PART)) {
            positions.add(primary.north());
            positions.add(primary.south());
            positions.add(primary.west());
            positions.add(primary.east());
        }
        for (BlockPos pos : positions) {
            BlockPos immutable = pos.toImmutable();
            frame.snapshots.putIfAbsent(immutable, new CellSnapshot(
                    context.getWorld().getBlockState(immutable),
                    SlabPlacementDyAttachment.rawFact(context.getWorld(), immutable)));
        }
    }

    private static void slabbed$computePending(PlacementFrame frame) {
        frame.pendingComputed = true;
        if (!frame.actualTargetSeen || frame.actualContext == null) {
            frame.pending = Map.of();
            return;
        }
        World world = frame.actualContext.getWorld();
        BlockPos primary = frame.actualContext.getBlockPos().toImmutable();
        BlockState finalState = world.getBlockState(primary);
        boolean authoredCustomSlabPlacement = slabbed$isAuthoredCustomSlabFinal(finalState);
        boolean terrainOwnedOnTopObject = slabbed$terrainOwnsOnTopObject(frame.rootAim, finalState);
        if (finalState.isAir()
                || ((LandingResolver.compatOwnsFinalState(finalState) || terrainOwnedOnTopObject)
                && !authoredCustomSlabPlacement)) {
            frame.pending = Map.of();
            return;
        }

        List<BlockPos> group = slabbed$validatedGroup(frame, world, primary, finalState);
        boolean paired = finalState.contains(Properties.DOUBLE_BLOCK_HALF)
                || (finalState.getBlock() instanceof BedBlock && finalState.contains(Properties.BED_PART));
        if (paired && group.isEmpty()) {
            frame.pending = Map.of();
            Slabbed.LOGGER.warn("[PLACEMENT] malformed linked placement at {}; publishing no height", primary);
            return;
        }
        if (group.isEmpty()) {
            group = List.of(primary);
        }

        long rawBits;
        CellSnapshot prior = frame.snapshots.get(primary);
        boolean sameCellSlabUpgrade = finalState.getBlock() instanceof SlabBlock
                && finalState.contains(SlabBlock.TYPE)
                && finalState.get(SlabBlock.TYPE) == SlabType.DOUBLE
                && prior != null
                && prior.priorState().getBlock() == finalState.getBlock()
                && prior.priorState().contains(SlabBlock.TYPE)
                && prior.priorState().get(SlabBlock.TYPE) != SlabType.DOUBLE;
        if (sameCellSlabUpgrade && prior.priorFact().present()) {
            rawBits = prior.priorFact().rawBits();
        } else {
            LandingResolver.Family family = LandingResolver.classify(finalState);
            LandingResolver.PlacementResolution resolution = LandingResolver.resolve(
                    slabbed$aimForResolve(frame, primary), primary, finalState, family,
                    authoredCustomSlabPlacement);
            double dy = resolution == null
                    ? SlabSupport.getUnstoredYOffset(world, primary, finalState)
                    : resolution.landingDy();
            if (!Double.isFinite(dy)) {
                frame.pending = Map.of();
                return;
            }
            rawBits = Double.doubleToRawLongBits(dy);
        }

        LinkedHashMap<BlockPos, Long> pending = new LinkedHashMap<>();
        for (BlockPos pos : group) {
            pending.put(pos.toImmutable(), rawBits);
        }
        frame.pending = Map.copyOf(pending);
    }

    /**
     * The root aim is valid evidence about the placement cell only while that cell is the aim's
     * own cell or its clicked-face neighbor. An item's own placement-context override may
     * RELOCATE the placement several cells away (a multi-cell walk); the discriminator is that
     * observed relocation, never the item's class. A relocated placement rebuilds its aim from
     * the relocated context itself so {@link LandingResolver#resolve} sees a self-consistent
     * aim/cell pair; every non-relocated placement keeps the root aim untouched.
     */
    private static LandingResolver.PlacementAim slabbed$aimForResolve(
            PlacementFrame frame,
            BlockPos primary
    ) {
        LandingResolver.PlacementAim aim = frame.rootAim;
        if (aim == null
                || frame.actualContext == null
                || primary.equals(aim.ownerPos())
                || primary.equals(aim.ownerPos().offset(aim.clickedFace()))) {
            return aim;
        }
        return LandingResolver.captureRelocatedAim(frame.actualContext);
    }

    /** A player-authored custom slab final joins the same immutable transaction as vanilla slabs. */
    private static boolean slabbed$isAuthoredCustomSlabFinal(BlockState finalState) {
        return finalState != null
                && finalState.getBlock() instanceof SlabBlock
                && CompatHooks.customSlabSurfaceKind(finalState) != CompatSlabSurfaceKind.NONE;
    }

    private static boolean slabbed$terrainOwnsOnTopObject(
            LandingResolver.PlacementAim aim,
            BlockState finalState
    ) {
        return aim != null
                && aim.clickedFace() == Direction.UP
                && CompatHooks.customSlabSurfaceKind(aim.ownerState())
                == CompatSlabSurfaceKind.BOTTOM_LIKE
                && CompatHooks.terrainSlabsHandlesObjectOffset(finalState);
    }

    private static List<BlockPos> slabbed$validatedGroup(
            PlacementFrame frame,
            World world,
            BlockPos primary,
            BlockState state
    ) {
        if (state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
            BlockPos partnerPos = half == DoubleBlockHalf.LOWER ? primary.up() : primary.down();
            BlockState partner = world.getBlockState(partnerPos);
            if (!frame.snapshots.containsKey(partnerPos)
                    || partner.getBlock() != state.getBlock()
                    || !partner.contains(Properties.DOUBLE_BLOCK_HALF)
                    || partner.get(Properties.DOUBLE_BLOCK_HALF) == half) {
                return List.of();
            }
            return primary.asLong() <= partnerPos.asLong()
                    ? List.of(primary, partnerPos)
                    : List.of(partnerPos, primary);
        }
        if (state.getBlock() instanceof BedBlock && state.contains(Properties.BED_PART)) {
            BlockPos partnerPos = primary.offset(BedBlock.getOppositePartDirection(state));
            BlockState partner = world.getBlockState(partnerPos);
            if (!frame.snapshots.containsKey(partnerPos)
                    || partner.getBlock() != state.getBlock()
                    || !partner.contains(Properties.BED_PART)
                    || partner.get(Properties.BED_PART) == state.get(Properties.BED_PART)
                    || !primary.equals(partnerPos.offset(BedBlock.getOppositePartDirection(partner)))) {
                return List.of();
            }
            return primary.asLong() <= partnerPos.asLong()
                    ? List.of(primary, partnerPos)
                    : List.of(partnerPos, primary);
        }
        return List.of(primary);
    }

    private static void slabbed$publish(PlacementFrame frame) {
        if (frame.pending.isEmpty() || frame.actualContext == null) {
            return;
        }
        World world = frame.actualContext.getWorld();
        if (!world.isClient()) {
            SlabPlacementDyAttachment.writeBatch(world, frame.pending);
        }
    }

    private static void slabbed$publishClientPrediction(PlacementFrame frame) {
        if (frame.pending.isEmpty()
                || frame.actualContext == null
                || !frame.actualContext.getWorld().isClient()) {
            return;
        }
        frame.clientPredictionPublished = SlabPlacementDyAttachment.publishClientPrediction(frame.pending);
    }

    private static final double LOWERED_VISUAL_BOUNDARY_EPSILON = 1.0e-6d;

    /** A slab cannot mint a new permanent height below the active targeting envelope. */
    private boolean slabbed$refusesOutOfEnvelopeSlabPlacement() {
        BlockItem self = (BlockItem) (Object) this;
        BlockState heldState = self.getBlock().getDefaultState();
        if (!(heldState.getBlock() instanceof SlabBlock)
                && CompatHooks.customSlabSurfaceKind(heldState) == CompatSlabSurfaceKind.NONE) {
            return false;
        }
        LandingResolver.PlacementAim aim = ROOT_AIM.get();
        if (aim == null || !Double.isFinite(aim.ownerVisibleDy())) {
            return false;
        }
        BlockPos previewTarget = aim.replacementSameCell()
                ? aim.ownerPos()
                : aim.ownerPos().offset(aim.clickedFace());
        boolean authoredCustomSlab = slabbed$isAuthoredCustomSlabFinal(heldState);
        LandingResolver.PlacementResolution preview = LandingResolver.resolve(
                aim,
                previewTarget,
                heldState,
                LandingResolver.classify(heldState),
                authoredCustomSlab);
        double proposedDy = preview == null ? aim.ownerVisibleDy() : preview.landingDy();
        return proposedDy < SlabSupport.minResolvedDy() - LOWERED_VISUAL_BOUNDARY_EPSILON;
    }

    private static boolean slabbed$isSupportedLoweredHalfStep(double yOffset) {
        double doubledYOffset = yOffset * 2.0d;
        return Double.isFinite(yOffset)
                && yOffset <= -0.5d
                && yOffset >= SlabSupport.minResolvedDy()
                && Math.abs(doubledYOffset - Math.rint(doubledYOffset))
                        <= LOWERED_VISUAL_BOUNDARY_EPSILON;
    }

    private static boolean slabbed$isOrdinaryFullBlockPlacementItem(BlockItem item, ItemUsageContext context) {
        Block block = item.getBlock();
        if (block instanceof SlabBlock || block instanceof BlockEntityProvider || block instanceof CraftingTableBlock) {
            return false;
        }
        BlockState state = block.getDefaultState();
        return state.getFluidState().isEmpty()
                && state.isSolidBlock(context.getWorld(), context.getBlockPos());
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
        SlabbedAuditBridge.invokeRecorder(
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

    @ModifyArg(
            method = "useOnBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemPlacementContext;<init>(Lnet/minecraft/item/ItemUsageContext;)V"
            )
    )
    private ItemUsageContext slabbed$remapLoweredFullBlockSideHit(ItemUsageContext context) {
        BlockItem self = (BlockItem) (Object) this;
        boolean itemIsSlab = self.getBlock() instanceof SlabBlock;
        boolean itemIsOrdinaryFullBlock = slabbed$isOrdinaryFullBlockPlacementItem(self, context);
        boolean itemEligible = itemIsSlab || itemIsOrdinaryFullBlock;
        if (!itemEligible) {
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
            return context;
        }

        Direction originalSide = context.getSide();
        Vec3d originalHitPos = context.getHitPos();
        BlockPos targetPos = context.getBlockPos();
        BlockState targetState = context.getWorld().getBlockState(targetPos);
        boolean targetIsSolid = targetState.isSolidBlock(context.getWorld(), targetPos);
        double yOffset = SlabSupport.getVisualYOffset(context.getWorld(), targetPos, targetState);
        boolean targetIsLoweredSlab = itemIsSlab
                && targetState.getBlock() instanceof SlabBlock
                && slabbed$isSupportedLoweredHalfStep(yOffset);
        boolean targetAcceptsLoweredSidePlacement = targetIsSolid || targetIsLoweredSlab;
        boolean targetHasBlockEntity = targetState.getBlock() instanceof BlockEntityProvider;
        boolean targetIsCraftingTable = targetState.getBlock() instanceof CraftingTableBlock;
        boolean ordinaryLoweredFullBlockGuard = targetIsSolid
                && !targetHasBlockEntity
                && !targetIsCraftingTable
                && yOffset == -0.5d;
        Direction effectiveSide = originalSide;
        String hitDescriptor = originalSide.getAxis().isHorizontal() ? "horizontal_face" : "none";

        boolean faceHorizontal = effectiveSide.getAxis().isHorizontal();
        if (!faceHorizontal) {
            slabbed$recordRemapAttempt(
                    context,
                    itemEligible,
                    false,
                    targetIsSolid,
                    targetHasBlockEntity,
                    targetIsCraftingTable,
                    yOffset,
                    ordinaryLoweredFullBlockGuard,
                    false,
                    "face_not_horizontal",
                    null,
                    null,
                    "none");
            return context;
        }

        if (!targetAcceptsLoweredSidePlacement) {
            slabbed$recordRemapAttempt(
                    context,
                    itemEligible,
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
                    hitDescriptor);
            return context;
        }
        if (targetHasBlockEntity) {
            slabbed$recordRemapAttempt(
                    context,
                    itemEligible,
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
                    hitDescriptor);
            return context;
        }
        if (targetIsCraftingTable) {
            slabbed$recordRemapAttempt(
                    context,
                    itemEligible,
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
                    hitDescriptor);
            return context;
        }
        if (!slabbed$isSupportedLoweredHalfStep(yOffset)) {
            slabbed$recordRemapAttempt(
                    context,
                    itemEligible,
                    true,
                    true,
                    false,
                    false,
                    yOffset,
                    ordinaryLoweredFullBlockGuard,
                    false,
                    "y_offset_not_supported_half_step",
                    null,
                    effectiveSide,
                    hitDescriptor);
            return context;
        }

        BlockPos sidePlacePos = targetPos.offset(effectiveSide);
        BlockState sidePlaceState = context.getWorld().getBlockState(sidePlacePos);
        if (itemIsOrdinaryFullBlock && !sidePlaceState.isReplaceable()) {
            slabbed$recordRemapAttempt(
                    context,
                    itemEligible,
                    true,
                    targetIsSolid,
                    targetHasBlockEntity,
                    targetIsCraftingTable,
                    yOffset,
                    ordinaryLoweredFullBlockGuard,
                    false,
                    "ordinary_full_block_side_not_replaceable",
                    null,
                    effectiveSide,
                    hitDescriptor);
            return context;
        }

        // Select the slab half from the immutable visible hit, then encode only that half into
        // the synthetic in-cell hit used by vanilla's placement-state decision.
        double loweredVisualHalfSplit = targetPos.getY() + yOffset + 0.5d;
        double loweredVisualUpperBoundary = targetPos.getY() + yOffset + 1.0d;
        boolean exactLoweredVisualBoundary = Math.abs(originalHitPos.y - loweredVisualUpperBoundary)
                <= LOWERED_VISUAL_BOUNDARY_EPSILON;
        boolean upperHalfIntent = originalHitPos.y >= loweredVisualHalfSplit
                && !exactLoweredVisualBoundary;
        double remappedY = upperHalfIntent
                ? targetPos.getY() + 0.501d   // > 0.5 → vanilla → TOP
                : targetPos.getY() + 0.499d;  // ≤ 0.5 → vanilla → BOTTOM
        Vec3d remappedHitPos = new Vec3d(originalHitPos.x, remappedY, originalHitPos.z);
        BlockHitResult remappedHit = new BlockHitResult(
                remappedHitPos,
                effectiveSide,
                targetPos,
                context.hitsInsideBlock(),
                false
        );

        slabbed$recordRemapAttempt(
                context,
                itemEligible,
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
                hitDescriptor);

        return new ItemUsageContext(context.getWorld(), context.getPlayer(), context.getHand(), context.getStack(), remappedHit) {
        };
    }
}
