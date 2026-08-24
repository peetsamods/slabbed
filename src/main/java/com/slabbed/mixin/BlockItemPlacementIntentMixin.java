package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.network.PlacementDyCorrectionServer;
import com.slabbed.network.PlacementDyPredictionBridge;
import com.slabbed.placement.ConnectorPlacementSettle;
import com.slabbed.placement.LandingHitValidationPolicy;
import com.slabbed.placement.LandingResolver;
import com.slabbed.util.SlabbedDiagnosticsBridge;
import com.slabbed.util.PlacementIntentState;
import com.slabbed.util.SlabEnsembleCoherence;
import com.slabbed.util.SlabSupport;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private static final ThreadLocal<PointedDripstoneContinuationIntent>
            POINTED_DRIPSTONE_CONTINUATION_INTENT = new ThreadLocal<>();

    /**
     * One placement-time intent window for a translated pointed-dripstone side hit. Vanilla derives
     * the candidate's tip direction from the player's look direction, so a remapped side hit can be
     * re-derived as the OPPOSING tip; this pins the direction the aim actually named. Placement-time
     * only: it never revisits an already-placed block.
     */
    private record PointedDripstoneContinuationIntent(
            BlockPos ownerPos,
            BlockPos candidatePos,
            Direction tipDirection
    ) {
    }

    private static final ThreadLocal<Integer> C3_USE_ON_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<RootAim> C3_ROOT_AIM = new ThreadLocal<>();
    private static final ThreadLocal<Deque<PlacementFrame>> C3_PLACE_FRAMES =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<OccludedOccupancyFrame> OCCLUDED_OCCUPANCY_FRAME =
            new ThreadLocal<>();

    private record RootAim(
            LandingResolver.PlacementAim resolverAim,
            Level level,
            net.minecraft.world.InteractionHand hand,
            String heldItemId
    ) {
    }

    private record OccludedOccupancyFrame(VoxelShape translatedShape) {
    }

    private record CellSnapshot(
            BlockState priorState,
            SlabAnchorAttachment.PlacementDyFact priorBacking
    ) {
    }

    private record PendingCapture(Map<BlockPos, Long> rawBitsByPos) {
        private PendingCapture {
            rawBitsByPos = Map.copyOf(rawBitsByPos);
        }
    }

    private static final class PlacementFrame {
        final RootAim rootAim;
        final boolean aimless;
        final LinkedHashMap<BlockPos, CellSnapshot> snapshots = new LinkedHashMap<>();
        BlockPlaceContext actualContext;
        BlockState placementState;
        PendingCapture pending;
        boolean actualTargetSeen;
        boolean itemRelocated;
        boolean pendingComputed;
        boolean markerAuthorsCompleted;
        boolean setPlacedBySeen;
        boolean published;
        boolean recordAllowed;

        PlacementFrame(RootAim rootAim) {
            this.rootAim = rootAim;
            this.aimless = rootAim == null;
        }
    }

    private static PlacementFrame slabbed$c3Frame() {
        Deque<PlacementFrame> frames = C3_PLACE_FRAMES.get();
        return frames.isEmpty() ? null : frames.peek();
    }

    @WrapMethod(method = "useOn")
    private InteractionResult slabbed$c3RootAimScope(
            UseOnContext context,
            Operation<InteractionResult> original
    ) {
        int depth = C3_USE_ON_DEPTH.get();
        if (depth == 0) {
            C3_ROOT_AIM.set(slabbed$c3CaptureRootAim(context));
        }
        C3_USE_ON_DEPTH.set(depth + 1);
        try {
            return original.call(context);
        } finally {
            int remaining = C3_USE_ON_DEPTH.get() - 1;
            if (remaining <= 0) {
                C3_USE_ON_DEPTH.remove();
                C3_ROOT_AIM.remove();
                COMPOUND_VISIBLE_SIDE_LOWER_INTENT.remove();
                COMPOUND_VISIBLE_SIDE_UPPER_INTENT.remove();
                COMPOUND_VISIBLE_SIDE_DOUBLE_INTENT.remove();
                COMPOUND_VISIBLE_OWNER_TOP_INTENT.remove();
                POINTED_DRIPSTONE_CONTINUATION_INTENT.remove();
            } else {
                C3_USE_ON_DEPTH.set(remaining);
            }
        }
    }

    @WrapMethod(method = "place")
    private InteractionResult slabbed$c3PlaceScope(
            BlockPlaceContext context,
            Operation<InteractionResult> original
    ) {
        PlacementFrame frame = new PlacementFrame(C3_ROOT_AIM.get());
        Deque<PlacementFrame> frames = C3_PLACE_FRAMES.get();
        frames.push(frame);
        try {
            InteractionResult result = original.call(context);
            BlockPlaceContext evidenceContext = frame.actualContext == null ? context : frame.actualContext;
            PriorPlaceState prior = slabbed$placePriorState.get();
            slabbed$placePriorState.remove();
            frame.recordAllowed = true;
            boolean deferredClientRecorder = false;
            if (result != null && result.consumesAction()) {
                if (frame.actualTargetSeen && frame.pendingComputed && frame.markerAuthorsCompleted) {
                    deferredClientRecorder = slabbed$c3Publish(frame,
                            () -> slabbed$recordLiveCursorIntentPlacementAction(
                                    evidenceContext, result, frame, prior));
                } else {
                    com.slabbed.Slabbed.LOGGER.warn(
                            "[C3] successful place had incomplete capture state target={} pending={} markers={}",
                            frame.actualTargetSeen, frame.pendingComputed, frame.markerAuthorsCompleted);
                }
            }
            if (!deferredClientRecorder) {
                slabbed$recordLiveCursorIntentPlacementAction(evidenceContext, result, frame, prior);
            }
            return result;
        } finally {
            frames.pop();
            if (frames.isEmpty()) {
                C3_PLACE_FRAMES.remove();
                slabbed$placePriorState.remove();
                COMPOUND_VISIBLE_SIDE_LOWER_INTENT.remove();
                COMPOUND_VISIBLE_SIDE_UPPER_INTENT.remove();
                COMPOUND_VISIBLE_SIDE_DOUBLE_INTENT.remove();
                COMPOUND_VISIBLE_OWNER_TOP_INTENT.remove();
                POINTED_DRIPSTONE_CONTINUATION_INTENT.remove();
            }
        }
    }

    @WrapOperation(
            method = "place",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/BlockItem;updatePlacementContext(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/item/context/BlockPlaceContext;")
    )
    private BlockPlaceContext slabbed$c3CaptureActualContext(
            BlockItem instance,
            BlockPlaceContext context,
            Operation<BlockPlaceContext> original
    ) {
        BlockPlaceContext actual = original.call(instance, context);
        PlacementFrame frame = slabbed$c3Frame();
        if (frame != null && actual != null) {
            frame.actualContext = actual;
            frame.actualTargetSeen = true;
            // The ONE place an item's own relocation is observable: what this method was handed
            // versus what it returned. See slabbed$aimForResolve for why nothing downstream can
            // re-derive this from geometry.
            frame.itemRelocated = !actual.getClickedPos().equals(context.getClickedPos());
            PlacementDyCorrectionServer.observe(actual.getClickedPos());
        }
        return actual;
    }

    /**
     * Pins the pointed-dripstone continuation candidate's TIP_DIRECTION before vanilla's OWN
     * internal {@code canPlace(context, state)} call (inside {@code getPlacementState}) evaluates
     * it. Patching only {@code getPlacementState}'s return value is too late: vanilla vetoes on the
     * raw, possibly opposing-direction/differently-shaped state first, so the interpenetration
     * guard would judge the wrong geometry and refuse a candidate this pin would otherwise fix.
     */
    @WrapOperation(
            method = "getPlacementState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;getStateForPlacement(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;")
    )
    private BlockState slabbed$c3PinBeforeCanPlace(
            Block block,
            BlockPlaceContext context,
            Operation<BlockState> original
    ) {
        return slabbed$pinPointedDripstoneContinuationDirection(context, original.call(block, context));
    }

    @WrapOperation(
            method = "place",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/BlockItem;getPlacementState(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;")
    )
    private BlockState slabbed$c3CapturePlacementState(
            BlockItem instance,
            BlockPlaceContext context,
            Operation<BlockState> original
    ) {
        BlockState placementState = original.call(instance, context);
        PlacementFrame frame = slabbed$c3Frame();
        if (frame != null && placementState != null) {
            frame.actualContext = context;
            frame.actualTargetSeen = true;
            frame.placementState = placementState;
            slabbed$c3SnapshotCandidates(frame, context, placementState);
            slabbed$armModelStaleSentinelAtActualTarget(context);
        }
        return placementState;
    }

    @WrapOperation(
            method = "place",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z")
    )
    private boolean slabbed$c3PlaceBlock(
            BlockItem instance,
            BlockPlaceContext context,
            BlockState state,
            Operation<Boolean> original
    ) {
        return original.call(instance, context, state);
    }

    @WrapOperation(
            method = "place",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V")
    )
    private void slabbed$c3ObserveSetPlacedBy(
            Block block,
            Level level,
            BlockPos pos,
            BlockState state,
            LivingEntity placer,
            ItemStack stack,
            Operation<Void> original
    ) {
        original.call(block, level, pos, state, placer, stack);
        PlacementFrame frame = slabbed$c3Frame();
        if (frame != null) {
            frame.setPlacedBySeen = true;
        }
    }

    @WrapOperation(
            method = "place",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V")
    )
    private void slabbed$c3ComputeBeforeConsume(
            ItemStack stack,
            int amount,
            LivingEntity entity,
            Operation<Void> original
    ) {
        PlacementFrame frame = slabbed$c3Frame();
        if (frame != null) {
            try {
                slabbed$c3ComputePending(frame);
            } catch (Throwable t) {
                frame.pending = new PendingCapture(Map.of());
                frame.pendingComputed = true;
                com.slabbed.Slabbed.LOGGER.warn("[C3] capture computation failed closed", t);
            }
        }
        original.call(stack, amount, entity);
    }

    /**
     * Consumes the pointed-dripstone continuation intent exactly once, pinning only TIP_DIRECTION.
     * Thickness stays vanilla's job: the new segment is the column's tip, so it merges only when it
     * meets an opposing tip. Nothing here reads or writes an already-placed block's dy.
     */
    private static BlockState slabbed$pinPointedDripstoneContinuationDirection(
            BlockPlaceContext context,
            BlockState placementState
    ) {
        PointedDripstoneContinuationIntent intent = POINTED_DRIPSTONE_CONTINUATION_INTENT.get();
        if (intent == null
                || placementState == null
                || !context.getClickedPos().equals(intent.candidatePos())
                || !(placementState.getBlock() instanceof PointedDripstoneBlock)
                || !placementState.hasProperty(SpeleothemBlock.TIP_DIRECTION)
                || !placementState.hasProperty(SpeleothemBlock.THICKNESS)) {
            return placementState;
        }
        POINTED_DRIPSTONE_CONTINUATION_INTENT.remove();
        if (placementState.getValue(SpeleothemBlock.TIP_DIRECTION) == intent.tipDirection()) {
            return placementState;
        }
        BlockState beyondTip = context.getLevel()
                .getBlockState(intent.candidatePos().relative(intent.tipDirection()));
        boolean opposingTipBeyond = beyondTip.getBlock() instanceof PointedDripstoneBlock
                && beyondTip.hasProperty(SpeleothemBlock.TIP_DIRECTION)
                && beyondTip.getValue(SpeleothemBlock.TIP_DIRECTION)
                == intent.tipDirection().getOpposite();
        return placementState
                .setValue(SpeleothemBlock.TIP_DIRECTION, intent.tipDirection())
                .setValue(SpeleothemBlock.THICKNESS,
                        opposingTipBeyond ? SpeleothemThickness.TIP_MERGE : SpeleothemThickness.TIP);
    }

    private static RootAim slabbed$c3CaptureRootAim(UseOnContext context) {
        LandingResolver.PlacementAim aim = LandingResolver.captureAim(context);
        return new RootAim(
                aim,
                context.getLevel(),
                context.getHand(),
                BuiltInRegistries.ITEM.getKey(context.getItemInHand().getItem()).toString());
    }

    private static void slabbed$c3SnapshotCandidates(
            PlacementFrame frame,
            BlockPlaceContext context,
            BlockState placementState
    ) {
        Level world = context.getLevel();
        BlockPos primary = context.getClickedPos().immutable();
        ArrayList<BlockPos> positions = new ArrayList<>();
        positions.add(primary);
        if (placementState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            positions.add(primary.above());
            positions.add(primary.below());
        }
        if (placementState.getBlock() instanceof BedBlock
                || placementState.hasProperty(BlockStateProperties.BED_PART)) {
            positions.add(primary.north());
            positions.add(primary.south());
            positions.add(primary.west());
            positions.add(primary.east());
        }
        for (BlockPos pos : positions) {
            BlockPos immutable = pos.immutable();
            frame.snapshots.putIfAbsent(immutable, new CellSnapshot(
                    world.getBlockState(immutable),
                    SlabAnchorAttachment.rawPlacementDyFact(world, immutable)));
            PlacementDyCorrectionServer.observe(immutable);
        }
    }

    /**
     * THE AIM THAT DESCRIBES THE FILLED CELL. The root aim is evidence about the CLICKED cell. An
     * item's own {@code updatePlacementContext} may RELOCATE the placement away from it — vanilla
     * scaffolding walks its column and builds at the far end — and pairing the stale aim with the
     * far cell makes the resolver answer with the DISTANCE between two unrelated cells, which LAW 1
     * then freezes (measured on this line: a 3-cell walk minted {@code -3.0}, a sideways extension
     * {@code +1.0}, a lowered column {@code -3.5}).
     *
     * <p><b>DO NOT re-derive the relocation from geometry.</b> The upstream line keys this on "the
     * filled cell is neither the aim's cell nor its clicked-face neighbour", and that predicate is
     * WRONG here: this line's targeting seats legitimately into a cell several steps down a column
     * with no item relocation at all, so the geometric form rewrites the aim of an ordinary deep
     * placement. Attempted 2026-08-22 and reverted the same day — it silenced the occupancy-theft
     * refusal ({@code LandingRuleLawTest} TEST 29 went red at exactly the fixture that gate exists
     * for). Only the OBSERVED relocation is the discriminator, and only the wrap around
     * {@code updatePlacementContext} can observe it.
     */
    private static LandingResolver.PlacementAim slabbed$aimForResolve(PlacementFrame frame) {
        LandingResolver.PlacementAim aim = frame.rootAim == null ? null : frame.rootAim.resolverAim();
        return frame.itemRelocated && aim != null && frame.actualContext != null
                ? LandingResolver.captureRelocatedAim(frame.actualContext)
                : aim;
    }

    private static void slabbed$c3ComputePending(PlacementFrame frame) {
        frame.pendingComputed = true;
        if (!frame.actualTargetSeen || frame.actualContext == null) {
            frame.pending = new PendingCapture(Map.of());
            return;
        }
        Level world = frame.actualContext.getLevel();
        BlockPos primary = frame.actualContext.getClickedPos().immutable();
        BlockState finalState = world.getBlockState(primary);
        if (finalState.isAir() || slabbed$c3CompatOwns(finalState)) {
            frame.pending = new PendingCapture(Map.of());
            return;
        }
        List<BlockPos> group = slabbed$c3ValidatedGroup(frame, world, primary, finalState);
        boolean paired = finalState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || (finalState.getBlock() instanceof BedBlock
                && finalState.hasProperty(BlockStateProperties.BED_PART));
        if (paired && group.isEmpty()) {
            frame.pending = new PendingCapture(Map.of());
            com.slabbed.Slabbed.LOGGER.warn("[C3] malformed reciprocal pair at {}; publishing no dy", primary);
            return;
        }
        if (group.isEmpty()) {
            group = List.of(primary);
        }

        long rawBits;
        CellSnapshot primarySnapshot = frame.snapshots.get(primary);
        boolean occupiedSingleSlabBecameDouble = finalState.getBlock() instanceof SlabBlock
                && finalState.hasProperty(SlabBlock.TYPE)
                && finalState.getValue(SlabBlock.TYPE) == SlabType.DOUBLE
                && primarySnapshot != null
                && primarySnapshot.priorState().getBlock() == finalState.getBlock()
                && primarySnapshot.priorState().hasProperty(SlabBlock.TYPE)
                && primarySnapshot.priorState().getValue(SlabBlock.TYPE) != SlabType.DOUBLE;
        if (occupiedSingleSlabBecameDouble) {
            SlabAnchorAttachment.PlacementDyFact priorBacking = primarySnapshot.priorBacking();
            double priorDy = priorBacking.present()
                    ? Double.longBitsToDouble(priorBacking.rawBits())
                    : Double.NaN;
            rawBits = priorBacking.present() && Double.isFinite(priorDy)
                    ? priorBacking.rawBits()
                    : Double.doubleToRawLongBits(0.0d);
        } else {
            LandingResolver.Family family = LandingResolver.classify(finalState);
            LandingResolver.PlacementResolution resolution = frame.rootAim == null
                    || family == LandingResolver.Family.UNSUPPORTED
                    ? null
                    : LandingResolver.resolve(
                            world, slabbed$aimForResolve(frame), primary, finalState, family);
            if (family == LandingResolver.Family.PAIRED_FLOOR_SEAT && resolution == null) {
                frame.pending = new PendingCapture(Map.of());
                return;
            }
            double dy = resolution == null
                    ? SlabSupport.getUnstoredYOffset(world, primary, finalState)
                    : resolution.landingDy();
            if (!Double.isFinite(dy)) {
                frame.pending = new PendingCapture(Map.of());
                return;
            }
            rawBits = Double.doubleToRawLongBits(dy);
        }
        LinkedHashMap<BlockPos, Long> facts = new LinkedHashMap<>();
        for (BlockPos pos : group) {
            facts.put(pos.immutable(), rawBits);
            PlacementDyCorrectionServer.observe(pos);
        }
        frame.pending = new PendingCapture(facts);
    }

    private static List<BlockPos> slabbed$c3ValidatedGroup(
            PlacementFrame frame,
            Level world,
            BlockPos primary,
            BlockState state
    ) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            BlockPos partnerPos = half == DoubleBlockHalf.LOWER ? primary.above() : primary.below();
            BlockState partner = world.getBlockState(partnerPos);
            if (!frame.snapshots.containsKey(partnerPos)
                    || partner.getBlock() != state.getBlock()
                    || !partner.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    || partner.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == half) {
                return List.of();
            }
            return primary.asLong() <= partnerPos.asLong()
                    ? List.of(primary, partnerPos)
                    : List.of(partnerPos, primary);
        }
        if (state.getBlock() instanceof BedBlock
                && state.hasProperty(BlockStateProperties.BED_PART)) {
            BlockPos partnerPos = primary.relative(BedBlock.getConnectedDirection(state));
            BlockState partner = world.getBlockState(partnerPos);
            if (!frame.snapshots.containsKey(partnerPos)
                    || partner.getBlock() != state.getBlock()
                    || !partner.hasProperty(BlockStateProperties.BED_PART)
                    || partner.getValue(BlockStateProperties.BED_PART)
                    == state.getValue(BlockStateProperties.BED_PART)
                    || !primary.equals(partnerPos.relative(BedBlock.getConnectedDirection(partner)))) {
                return List.of();
            }
            return primary.asLong() <= partnerPos.asLong()
                    ? List.of(primary, partnerPos)
                    : List.of(partnerPos, primary);
        }
        return List.of(primary);
    }

    private static boolean slabbed$c3Publish(PlacementFrame frame, Runnable postInstallAction) {
        frame.published = true;
        PlacementDyPredictionBridge.markTestPhase("publish:setPlacedBy=" + frame.setPlacedBySeen);
        if (frame.pending == null || frame.pending.rawBitsByPos().isEmpty() || frame.actualContext == null) {
            return false;
        }
        Level world = frame.actualContext.getLevel();
        if (!world.isClientSide()) {
            SlabAnchorAttachment.writePlacementDyBatch(world, frame.pending.rawBitsByPos());
            // The published fact is only now visible to reads. Connector arms (fence/wall/pane) were
            // already decided earlier in this same place() call — at getStateForPlacement and at the
            // setBlock-driven neighbour updateShape — while this cell still had no fact and read the
            // stable-flat stand-in. Re-run vanilla's own arm derivation for the published cells and
            // their horizontal neighbours so both ends decide from the published height. Heights are
            // untouched; see ConnectorPlacementSettle for the LAW.md reasoning.
            ConnectorPlacementSettle.settlePublishedPlacements(world, frame.pending.rawBitsByPos().keySet());
            return false;
        }
        int sequence = PlacementDyPredictionBridge.currentSequence();
        if (sequence < 0 || frame.rootAim == null) {
            return false;
        }
        try {
            LandingResolver.PlacementAim aim = frame.rootAim.resolverAim();
            PlacementDyPredictionBridge.GroupSignature signature =
                    new PlacementDyPredictionBridge.GroupSignature(
                            world.dimension().toString(),
                            sequence,
                            frame.rootAim.hand(),
                            frame.rootAim.heldItemId(),
                            aim.ownerPos().asLong(),
                            aim.clickedFace());
            ArrayList<PlacementDyPredictionBridge.PredictedCell> cells = new ArrayList<>();
            for (Map.Entry<BlockPos, Long> entry : frame.pending.rawBitsByPos().entrySet()) {
                CellSnapshot snapshot = frame.snapshots.get(entry.getKey());
                if (snapshot == null) {
                    snapshot = new CellSnapshot(
                            world.getBlockState(entry.getKey()),
                            SlabAnchorAttachment.rawPlacementDyFact(world, entry.getKey()));
                }
                cells.add(new PlacementDyPredictionBridge.PredictedCell(
                        entry.getKey(),
                        snapshot.priorState(),
                        snapshot.priorBacking(),
                        world.getBlockState(entry.getKey()),
                        entry.getValue()));
            }
            PlacementDyPredictionBridge.publishClientBatch(
                    new PlacementDyPredictionBridge.PredictedBatch(signature, cells, postInstallAction));
        } catch (RuntimeException exception) {
            com.slabbed.Slabbed.LOGGER.warn(
                    "[C3] client prediction batch construction failed; recorder action dropped", exception);
        }
        return true;
    }

    private static boolean slabbed$c3CompatOwns(BlockState state) {
        return LandingResolver.compatOwnsFinalState(state);
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

    /**
     * Gate for the vertical/below-support half of {@link SlabAnchorAttachment#updatePersistentLoweredSlabCarrier}'s
     * call site (the horizontal side-click half is {@link #slabbed$isPersistentLoweredSideSlabCarrierCandidate}).
     *
     * <p>PRE-L8 HISTORY: this gate required {@code SlabType.BOTTOM} and only recognised "the block
     * below is a lowered FULL BLOCK" ({@code isOrdinaryFullBlockAnchorCandidate} below) — so a slab
     * placed on top of a lowered TOP/DOUBLE slab support (the L8 scenario) could NEVER reach
     * {@code updatePersistentLoweredSlabCarrier} at all, no matter what the qualifier or the live-dy
     * lane did. Confirmed by a throwaway empirical probe (deleted): with the whole three-layer chain
     * unfixed the upper slab live-derived dy=0.0 and never persisted, so breaking its support popped
     * it flush.
     *
     * <p>Widened to delegate directly to
     * {@link SlabAnchorAttachment#qualifiesForPersistentLoweredSlabCarrier}, which already encodes the
     * correct full disjunction for ANY slab type (horizontal side-lane, bottom-on-lowered-full-block,
     * bottom-on-adjacent-bridge-support, and the new L8 vertical-on-lowered-TOP/DOUBLE-slab lane) —
     * this is the layer that makes the whole persistence dispatcher actually reachable from real
     * player placement for TOP/DOUBLE slabs, not just from direct test calls.
     */
    private static boolean slabbed$isPersistentLoweredBottomSlabCarrierCandidate(Level world, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(SlabBlock.TYPE)
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        return SlabAnchorAttachment.qualifiesForPersistentLoweredSlabCarrier(world, pos, state);
    }

    private static boolean slabbed$isPersistentLoweredSideSlabCarrierCandidate(
            BlockPlaceContext context,
            BlockPos placePos,
            BlockState placedState
    ) {
        // F5c: fluid-blind on BOTH the placed state and the lane source — an underwater lane
        // extension (or one beside a bucketed source) must author its carrier like the dry twin;
        // the side lane has no live read fallback, so the missing marker was a real never-pop hole.
        if (context.getClickedFace().getAxis().isVertical()
                || !(placedState.getBlock() instanceof SlabBlock)
                || !placedState.hasProperty(SlabBlock.TYPE)) {
            return false;
        }
        Level world = context.getLevel();
        BlockPos sourcePos = placePos.relative(context.getClickedFace().getOpposite());
        BlockState sourceState = world.getBlockState(sourcePos);
        if (!(sourceState.getBlock() instanceof SlabBlock)
                || !sourceState.hasProperty(SlabBlock.TYPE)
                || !SlabSupport.isCompatibleLoweredSlabLane(
                sourceState.getValue(SlabBlock.TYPE),
                placedState.getValue(SlabBlock.TYPE))) {
            return false;
        }
        boolean sourceNamedLoweredSideLane =
                SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, sourcePos, sourceState)
                        || SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, sourcePos, sourceState)
                        || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, sourcePos, sourceState)
                        || SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, sourcePos, sourceState);
        return sourceNamedLoweredSideLane
                && SlabAnchorAttachment.qualifiesForPersistentLoweredSlabCarrier(world, placePos, placedState);
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
        // F5c: fluid-blind — the slab placed into a water cell arrives WATERLOGGED and must still
        // receive its side-LOWER marker (reads have been fluid-blind since F5; without the marker
        // the underwater placement read lowered but never persisted).
        if (context.getClickedFace().getAxis().isVertical()
                || !placedState.is(Blocks.STONE_SLAB)
                || !placedState.hasProperty(SlabBlock.TYPE)
                || placedState.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
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
        // F5c: fluid-blind (see the side-LOWER predicate above — same authoring symmetry).
        if (context.getClickedFace().getAxis().isVertical()
                || !placedState.is(Blocks.STONE_SLAB)
                || !placedState.hasProperty(SlabBlock.TYPE)
                || placedState.getValue(SlabBlock.TYPE) != SlabType.TOP) {
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
        // F5c NOTE: the fluid term below is UNREACHABLE-wet (vanilla slab-merge forces
        // WATERLOGGED=false on the DOUBLE result, so this placed state can never carry fluid) —
        // kept under the surviving-mutant rule (no honest wet scene is constructible to prove its
        // removal; the F5b isLoweredTopLikeSlabCarrier precedent).
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
            BlockPos targetPos,
            double yOffset,
            Vec3 originalHitPos
    ) {
        double translatedMidY = targetPos.getY() + yOffset + 0.5d;
        boolean exactMid = Math.abs(originalHitPos.y - translatedMidY) <= LOWERED_VISUAL_BOUNDARY_EPSILON;
        return (originalHitPos.y < translatedMidY || exactMid) ? SlabType.BOTTOM : SlabType.TOP;
    }

    private static SlabType slabbed$getLoweredDoubleHitIntentType(
            BlockPos targetPos,
            double yOffset,
            Vec3 hitPos
    ) {
        double loweredMidY = targetPos.getY() + yOffset + 0.5d;
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

    /**
     * Phase 2b — OCCLUDED-surface placement remap (ENSEMBLE_COHERENCE_DESIGN.md, the t=98s
     * five-refused-clicks scene): a lowered support can carry an occupant that renders ENTIRELY below
     * its own cell floor ({@code SlabEnsembleCoherence.isOccludedOccupancy}) — the occupant's cell
     * LOOKS like free space above the visible surface, so an up-face click targets it and vanilla
     * refuses (occupied). Remap the click one cell up onto the occluded occupant, so the placement
     * lands in the APPARENT space and deep-follows the stack (the existing compound lanes assign
     * −1.0; nothing existing moves).
     *
     * <p>SURGICAL by the S11 hijack law — fires ONLY when ALL hold: up-face click; vanilla placement
     * would FAIL ({@code BlockPlaceContext.canPlace()} false — a working placement is NEVER touched);
     * the blocking occupant is genuinely occluded (TS-guarded inside the classifier); and the cell
     * above the occupant is plain air. Recursion terminates: the remapped click's own placement can
     * place, so the guard fails on re-entry.
     */
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void slabbed$remapClickOnOccludedSurface(
            UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (context.getClickedFace() != Direction.UP) {
            return;
        }
        Level level = context.getLevel();
        BlockPos occupied = context.getClickedPos().above();
        BlockState occupant = level.getBlockState(occupied);
        if (occupant.isAir() || !level.getBlockState(occupied.above()).isAir()) {
            return;
        }
        if (new BlockPlaceContext(context).canPlace()) {
            return;   // vanilla placement works — never hijack a working placement
        }
        double occupantDy = SlabSupport.getYOffset(level, occupied, occupant);
        if (!Double.isFinite(occupantDy)
                || !SlabEnsembleCoherence.isOccludedOccupancy(level, occupied, occupantDy)) {
            return;
        }
        VoxelShape translatedOccupantShape = occupant.getCollisionShape(
                        level, occupied, CollisionContext.empty())
                .move(occupied.getX(), occupied.getY() + occupantDy, occupied.getZ());
        BlockItem self = (BlockItem) (Object) this;
        Vec3 click = context.getClickLocation();
        BlockHitResult liftedHit = new BlockHitResult(
                new Vec3(click.x, occupied.getY() + 1.0, click.z), Direction.UP, occupied, false);
        UseOnContext remapped = new UseOnContext(
                context.getLevel(), context.getPlayer(), context.getHand(),
                context.getItemInHand(), liftedHit) {
        };
        OccludedOccupancyFrame priorOccupancyFrame = OCCLUDED_OCCUPANCY_FRAME.get();
        RootAim priorRootAim = C3_ROOT_AIM.get();
        OccludedOccupancyFrame occupancyFrame =
                new OccludedOccupancyFrame(translatedOccupantShape);
        boolean rebindRootAim = slabbed$isTranslatedUpTopHit(click, translatedOccupantShape);
        RootAim remappedRootAim =
                rebindRootAim ? slabbed$c3CaptureRootAim(remapped) : priorRootAim;
        try {
            OCCLUDED_OCCUPANCY_FRAME.set(occupancyFrame);
            if (rebindRootAim) {
                C3_ROOT_AIM.set(remappedRootAim);
            }
            cir.setReturnValue(self.useOn(remapped));
        } finally {
            if (priorOccupancyFrame == null) {
                OCCLUDED_OCCUPANCY_FRAME.remove();
            } else {
                OCCLUDED_OCCUPANCY_FRAME.set(priorOccupancyFrame);
            }
            if (priorRootAim == null) {
                C3_ROOT_AIM.remove();
            } else {
                C3_ROOT_AIM.set(priorRootAim);
            }
        }
    }

    private static boolean slabbed$isTranslatedUpTopHit(Vec3 hit, VoxelShape translatedShape) {
        if (hit == null || translatedShape == null || translatedShape.isEmpty()) {
            return false;
        }
        double localTopY = Double.NEGATIVE_INFINITY;
        for (var bounds : translatedShape.toAabbs()) {
            if (hit.x >= bounds.minX - LOWERED_VISUAL_BOUNDARY_EPSILON
                    && hit.x <= bounds.maxX + LOWERED_VISUAL_BOUNDARY_EPSILON
                    && hit.z >= bounds.minZ - LOWERED_VISUAL_BOUNDARY_EPSILON
                    && hit.z <= bounds.maxZ + LOWERED_VISUAL_BOUNDARY_EPSILON) {
                localTopY = Math.max(localTopY, bounds.maxY);
            }
        }
        return Double.isFinite(localTopY)
                && Math.abs(hit.y - localTopY) <= LOWERED_VISUAL_BOUNDARY_EPSILON;
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

    /**
     * WYSIWYG side-click follow (LAW 1 (the placement law), 2026-06-19): when the player places a SLAB by clicking the
     * SIDE face of a -0.5 lowered block, mark the predicted placement cell so {@code freezeLoweredOnPlace}
     * anchors it on the lowered surface (where the crosshair clicked) instead of freezing it flat at grid
     * height (the reported "lands 0.5 high"). Gated to a -0.5 lowered click so it does not touch the
     * compound (-1.0) side-placement path, which the RC3 compound-visible markers own. The companion
     * RETURN inject clears the marker so a cancelled/mismatched placement never leaks.
     */
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
        if (Math.abs(clickedDy + 0.5d) < 1.0e-6d) {   // clicked a -0.5 lowered surface
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
        System.out.println("[SLABBED_BETA4_REPEAT_SEAM_PLACEMENT_CONTEXT]"
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
            System.out.println("[SLABBED_BETA4_REPEAT_SEAM_PLACEMENT_EXIT]"
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
        System.out.println("[SLABBED_BETA4_REPEAT_SEAM_PLACEMENT_EXIT]"
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

    @WrapMethod(method = "canPlace")
    private boolean slabbed$rejectTranslatedPlacementOccupancy(
            BlockPlaceContext context,
            BlockState state,
            Operation<Boolean> original
    ) {
        boolean admitted = original.call(context, state);
        if (!admitted) {
            return admitted;
        }

        RootAim rootAim = C3_ROOT_AIM.get();
        LandingResolver.PlacementAim aim = rootAim == null ? null : rootAim.resolverAim();
        if (aim == null || state == null) {
            return true;
        }

        LandingResolver.Family family = LandingResolver.classify(state);
        if (family == LandingResolver.Family.UNSUPPORTED
                || LandingResolver.compatOwnsFinalState(state)) {
            return true;
        }

        // This gate runs AFTER updatePlacementContext, so a relocated placement reaches it with the
        // same stale aim the mint had. It takes the same substitution, keyed on the same observed
        // flag, so the gate and the mint never judge one placement at two different depths.
        //
        // SCOPE, stated honestly because a wrong reason here would outlive the code: no veto defect
        // was OBSERVED from the stale pairing at this site (measured 2026-08-22, scaffolding scenes).
        // Why it does not veto is NOT established — do not repeat any mechanism for it as fact. The
        // substitution is here so the two sites cannot diverge, not to fix a proven defect, and no
        // test can currently turn RED on reverting this site alone; the coverage that exists bounds
        // the mint. Treat this site as unpinned and re-measure before relying on its behaviour.
        // Substitute ONLY on relocation: this method's aim comes from C3_ROOT_AIM, which the remap
        // and continuation lanes deliberately rebind after the frame was built, so it is not always
        // frame.rootAim and must not be replaced by it.
        PlacementFrame relocationFrame = slabbed$c3Frame();
        LandingResolver.PlacementAim resolveAim = relocationFrame != null
                && relocationFrame.itemRelocated
                && relocationFrame.actualContext != null
                ? LandingResolver.captureRelocatedAim(relocationFrame.actualContext)
                : aim;
        LandingResolver.PlacementResolution resolution = LandingResolver.resolve(
                context.getLevel(), resolveAim, context.getClickedPos(), state, family);
        if (resolution == null
                || !Double.isFinite(resolution.landingDy())) {
            return true;
        }
        if (aim.replacementSameCell() && aim.ownerPos().equals(resolution.targetCell())) {
            return true;
        }

        Level world = context.getLevel();
        BlockPos placePos = resolution.targetCell();
        if (SlabAnchorAttachment.FROZEN_DY_ENABLED) {
            if (slabbed$hasUnsafeVerticalTranslationOverlap(
                    world, placePos, state, resolution.landingDy())) {
                return false;
            }
            if (slabbed$overlapsVerticalOpenTransitionEnvelope(
                    world, placePos, state, resolution.landingDy())) {
                return false;
            }
        }

        OccludedOccupancyFrame occupancyFrame = OCCLUDED_OCCUPANCY_FRAME.get();
        if (occupancyFrame == null) {
            return true;
        }
        VoxelShape candidateShape = state.getCollisionShape(
                        world, placePos, CollisionContext.empty())
                .move(placePos.getX(), placePos.getY() + resolution.landingDy(), placePos.getZ());
        if (candidateShape.isEmpty()) {
            return true;
        }

        return !Shapes.joinIsNotEmpty(
                candidateShape,
                occupancyFrame.translatedShape(),
                BooleanOp.AND);
    }

    private static boolean slabbed$hasUnsafeVerticalTranslationOverlap(
            Level world,
            BlockPos candidatePos,
            BlockState candidateState,
            double candidateDy
    ) {
        BlockPos belowPos = candidatePos.below();
        BlockState belowState = world.getBlockState(belowPos);
        double belowDy = SlabSupport.getYOffset(world, belowPos, belowState);
        boolean overlapsBelow = SlabEnsembleCoherence.relativeTranslationIncreasesBodyOverlap(
                belowState, belowPos, belowDy,
                candidateState, candidatePos, candidateDy);

        BlockPos abovePos = candidatePos.above();
        BlockState aboveState = world.getBlockState(abovePos);
        double aboveDy = SlabSupport.getYOffset(world, abovePos, aboveState);
        boolean overlapsAbove = SlabEnsembleCoherence.relativeTranslationIncreasesBodyOverlap(
                candidateState, candidatePos, candidateDy,
                aboveState, abovePos, aboveDy);

        return overlapsBelow || overlapsAbove;
    }

    private static boolean slabbed$overlapsVerticalOpenTransitionEnvelope(
            Level world,
            BlockPos candidatePos,
            BlockState candidateState,
            double candidateDy
    ) {
        return slabbed$overlapsOpenTransitionNeighbor(
                world, candidatePos, candidateState, candidateDy, candidatePos.below())
                || slabbed$overlapsOpenTransitionNeighbor(
                world, candidatePos, candidateState, candidateDy, candidatePos.above());
    }

    private static boolean slabbed$overlapsOpenTransitionNeighbor(
            Level world,
            BlockPos candidatePos,
            BlockState candidateState,
            double candidateDy,
            BlockPos neighborPos
    ) {
        BlockState neighborState = world.getBlockState(neighborPos);
        if (neighborState.isAir()) {
            return false;
        }
        double neighborDy = SlabSupport.getYOffset(world, neighborPos, neighborState);
        return SlabEnsembleCoherence.canonicalOpenTransitionEnvelopesOverlap(
                candidateState, candidatePos, candidateDy,
                neighborState, neighborPos, neighborDy);
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
        BlockState heldState = ((BlockItem) (Object) this).getBlock().defaultBlockState();
        double targetDy = SlabSupport.getYOffset(context.getLevel(), targetPos, targetState);
        Direction pointedDripstoneContinuation =
                LandingHitValidationPolicy.pointedDripstoneSideContinuationDirection(
                        targetPos,
                        targetState,
                        targetDy,
                        originalSide,
                        originalHitPos,
                        heldState);
        if (pointedDripstoneContinuation != null) {
            double continuationY = pointedDripstoneContinuation == Direction.DOWN
                    ? targetPos.getY() + targetDy
                    : targetPos.getY() + targetDy + 1.0d;
            Vec3 continuationHitPos = new Vec3(
                    originalHitPos.x, continuationY, originalHitPos.z);
            BlockHitResult continuationHit = new BlockHitResult(
                    continuationHitPos,
                    pointedDripstoneContinuation,
                    targetPos,
                    context.isInside(),
                    false);
            UseOnContext continuationContext = new UseOnContext(
                    context.getLevel(),
                    context.getPlayer(),
                    context.getHand(),
                    context.getItemInHand(),
                    continuationHit) {
            };
            // The C3 root aim was captured from the ORIGINAL horizontal click, before this remap.
            // Left stale, every downstream authority (the translated-occupancy guard and the dy
            // freeze) resolves the horizontal formula and lands dy 0.0 — a body that coincides with
            // the owner's own translated body, so the guard correctly refuses its own continuation.
            // Re-aiming here, and only here, puts this placement on the vertical end-face path that
            // already resolves the owner's frozen dy. Root use only: a nested useOn must not
            // reinstall the aim, and depth-0 exit still clears it.
            if (C3_USE_ON_DEPTH.get() == 1) {
                C3_ROOT_AIM.set(slabbed$c3CaptureRootAim(continuationContext));
            }
            BlockPos continuationCandidatePos = targetPos.relative(pointedDripstoneContinuation);
            POINTED_DRIPSTONE_CONTINUATION_INTENT.set(new PointedDripstoneContinuationIntent(
                    targetPos.immutable(),
                    continuationCandidatePos.immutable(),
                    pointedDripstoneContinuation));
            slabbed$recordRemapAttempt(
                    context,
                    true,
                    true,
                    targetState.isSolidRender(),
                    false,
                    false,
                    targetDy,
                    false,
                    true,
                    "pointed_dripstone_side_continuation",
                    continuationHitPos,
                    pointedDripstoneContinuation,
                    "pointed_dripstone_side_continuation");
            return slabbed$inspectReturn(
                    context, continuationContext, "pointed_dripstone_side_continuation");
        }
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
            // The "visible lane" walk to originalSide.getOpposite() only existed to chase the OLD post-hoc
            // retargeter's lie about the visible owner. With the offset-aware raycast active the crosshair hit
            // is honest, so a click on a given face must place a slab on THAT side, never the opposite — this
            // guard would otherwise flip a compound side placement to the wrong side once neighbour lanes
            // exist. Restrict it to the legacy retarget regime (-Dslabbed.offsetRaycast=false).
            if (!com.slabbed.util.SlabbedOffsetRaycast.ENABLED
                    && remappedOwnerOccupiedNonReplaceable
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
            // F10: the placement target moves one cell up — the WYSIWYG follow marker moves with it
            // (orphaned, it freeze-flats the hopped slab 0.5 above the aimed lowered surface when the
            // post-hop cell sits on solid flush ground).
            SlabAnchorAttachment.liftWysiwygFollowMarkerForUpperVisibleHop();
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
                expectedType = slabbed$getLoweredDoubleHitIntentType(targetPos, yOffset, originalHitPos);
            } else {
                expectedType = slabbed$getExpectedLoweredSidePlacementType(
                        targetPos,
                        yOffset,
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
        boolean remapIntoAdjacentSlabCell = targetState.getBlock() instanceof SlabBlock
                && targetState.getValue(SlabBlock.TYPE) != SlabType.DOUBLE
                && effectiveSide.getAxis().isHorizontal();
        BlockPos remappedTargetPos = remapIntoAdjacentSlabCell
                ? targetPos.relative(effectiveSide)
                : targetPos;
        BlockState remappedTargetState = context.getLevel().getBlockState(remappedTargetPos);
        boolean remappedTargetIsCompatibleSingleSlab = remapIntoAdjacentSlabCell
                && remappedTargetState.getBlock() == heldState.getBlock()
                && remappedTargetState.getBlock() instanceof SlabBlock
                && remappedTargetState.getValue(SlabBlock.TYPE) != SlabType.DOUBLE;
        if (remappedTargetIsCompatibleSingleSlab) {
            SlabType occupiedType = remappedTargetState.getValue(SlabBlock.TYPE);
            SlabType complementaryType = occupiedType == SlabType.BOTTOM ? SlabType.TOP : SlabType.BOTTOM;
            remappedY = slabbed$placementYForType(remappedTargetPos, complementaryType);
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
                remappedTargetPos,
                context.isInside(),
                false
        );

        UseOnContext remappedContext = new UseOnContext(context.getLevel(), context.getPlayer(), context.getHand(), context.getItemInHand(), remappedHit) {
        };
        slabbed$traceRepeatPlacementContext("placement-exit", context, remappedContext,
                "exit=remapped expectedType=" + expectedType + " remappedY=" + remappedY);
        return slabbed$inspectReturn(context, remappedContext, "remapped");
    }

    /**
     * Arms the recorder/sentinel from vanilla's transformed placement target, after
     * {@code updatePlacementContext} and {@code getPlacementState} but before world mutation.
     */
    private void slabbed$armModelStaleSentinelAtActualTarget(BlockPlaceContext context) {
        // Diagnostic-only: must NEVER interfere with placement (BuildStamp precedent). armPlacement runs
        // up to 124 dy reads under a recorder session; any exotic throw is swallowed and warned once.
        try {
            Level world = context.getLevel();
            // Capture the transformed target's prior state and dy on both logical sides. The outer
            // C3 place frame consumes it once, after marker publication and dy publication.
            if (SlabbedDiagnosticsBridge.enabled() && world != null) {
                BlockPos priorPos = context.getClickedPos();
                BlockState prior = world.getBlockState(priorPos);
                slabbed$placePriorState.set(new PriorPlaceState(prior.toString(),
                        String.format("%.6f", SlabSupport.getYOffset(world, priorPos, prior))));
            }
            if (world.isClientSide()) {
                SlabbedDiagnosticsBridge.armPlacement(
                        world, context.getClickedPos(), world.getGameTime());
            }
        } catch (Throwable t) {
            if (!slabbed$sentinelArmWarned) {
                slabbed$sentinelArmWarned = true;
                com.slabbed.Slabbed.LOGGER.warn("[MODEL_STALE sentinel] arming failed; sentinel coverage degraded for this session", t);
            }
        }
    }

    private static boolean slabbed$sentinelArmWarned;

    private record PriorPlaceState(String state, String dy) {
    }

    /** Per-thread transformed-target pre-place snapshot, consumed by the outer C3 place frame. */
    private static final ThreadLocal<PriorPlaceState> slabbed$placePriorState = new ThreadLocal<>();

    @Inject(method = "place", at = @At("RETURN"))
    private void slabbed$c3RunLegacyMarkerAuthors(
            BlockPlaceContext context,
            CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir
    ) {
        PlacementFrame frame = slabbed$c3Frame();
        BlockPlaceContext actualContext = frame == null || frame.actualContext == null
                ? context
                : frame.actualContext;
        slabbed$runLegacyMarkerAuthors(actualContext, cir);
        PlacementDyPredictionBridge.markTestPhase("markers_complete");
        if (frame != null) {
            frame.markerAuthorsCompleted = true;
        }
    }

    private void slabbed$runLegacyMarkerAuthors(
            BlockPlaceContext context,
            CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir
    ) {
        BlockItem self = (BlockItem) (Object) this;
        boolean heldIsSlab = self.getBlock() instanceof SlabBlock;
        if (!cir.getReturnValue().consumesAction()) {
            // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
            slabbed$recordLiveCursorIntentPlacementAction(context, cir.getReturnValue());
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
            } else if (slabbed$isPersistentLoweredSideSlabCarrierCandidate(context, placePos, placedState)
                    || slabbed$isPersistentLoweredBottomSlabCarrierCandidate(world, placePos, placedState)) {
                boolean compoundBefore = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos);
                boolean persistentBefore = SlabAnchorAttachment.isAnchored(world, placePos);
                if (world.isClientSide()) {
                    SlabAnchorAttachment.updateClientPredictedPersistentLoweredSlabCarrier(world, placePos,
                            placedState);
                } else {
                    SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, placePos, placedState);
                }
                boolean compoundAfter = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, placePos);
                boolean persistentAfter = SlabAnchorAttachment.isAnchored(world, placePos);
                // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
            } else {
                // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
            }
            slabbed$recordLiveCursorIntentPlacementAction(context, cir.getReturnValue());
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
                slabbed$recordLiveCursorIntentPlacementAction(context, cir.getReturnValue());
                return;
            }
        }

        if (context.getClickedFace().getAxis().isVertical()) {
            // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
            slabbed$recordLiveCursorIntentPlacementAction(context, cir.getReturnValue());
            return;
        }

        if (!SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, placePos, placedState)) {
            // 26.1.2 port: diagnostic side effect deferred until core compile is restored.
            slabbed$recordLiveCursorIntentPlacementAction(context, cir.getReturnValue());
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
        slabbed$recordLiveCursorIntentPlacementAction(context, cir.getReturnValue());
    }

    private static void slabbed$recordLiveCursorIntentPlacementAction(
            BlockPlaceContext context,
            InteractionResult result
    ) {
        PlacementFrame frame = slabbed$c3Frame();
        if (frame != null && !frame.recordAllowed) {
            return;
        }
        // Consume the HEAD snapshot unconditionally so a mid-placement toggle can't leak it.
        PriorPlaceState prior = slabbed$placePriorState.get();
        slabbed$placePriorState.remove();
        slabbed$recordLiveCursorIntentPlacementAction(context, result, frame, prior);
    }

    private static void slabbed$recordLiveCursorIntentPlacementAction(
            BlockPlaceContext context,
            InteractionResult result,
            PlacementFrame frame,
            PriorPlaceState prior
    ) {
        if (!SlabbedDiagnosticsBridge.enabled() || context == null || context.getLevel() == null) {
            return;
        }
        Level world = context.getLevel();
        boolean transformedTargetPresent = frame == null || frame.actualTargetSeen;
        BlockPos placePos = transformedTargetPresent ? context.getClickedPos() : null;
        RootAim rootAim = frame == null ? null : frame.rootAim;
        LandingResolver.PlacementAim resolverAim = rootAim == null ? null : rootAim.resolverAim();
        Direction clickedFace = resolverAim == null ? context.getClickedFace() : resolverAim.clickedFace();
        BlockPos clickedOwnerPos = resolverAim == null
                ? context.getClickedPos().relative(clickedFace.getOpposite())
                : resolverAim.ownerPos();
        Vec3 clickedHit = resolverAim == null ? context.getClickLocation() : resolverAim.hitLocation();
        BlockState beforeState = resolverAim == null
                ? world.getBlockState(clickedOwnerPos)
                : resolverAim.ownerState();
        BlockState afterState = placePos == null ? null : world.getBlockState(placePos);
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("actionType", result != null && result.consumesAction() ? "place_block" : "use_block");
        // Side + player disambiguate the interleaved client/server row pairs an integrated-SP session
        // writes (schema-5 capability the TEST (3) triage had to reconstruct from millisecond pairing).
        row.put("side", world.isClientSide() ? "client" : "server");
        row.put("player", context.getPlayer() == null ? "none" : context.getPlayer().getName().getString());
        row.put("heldItem", rootAim == null
                ? BuiltInRegistries.ITEM.getKey(context.getItemInHand().getItem()).toString()
                : rootAim.heldItemId());
        row.put("clickedOwnerPos", clickedOwnerPos.toShortString());
        row.put("clickedFace", clickedFace.toString());
        row.put("clickedHitVec", slabbed$placementIntentVec(clickedHit));
        row.put("placementPos", placePos == null ? "none" : placePos.toShortString());
        row.put("placeBeforeState", placePos == null ? "none" : prior == null ? "unrecorded" : prior.state());
        row.put("placeBeforeDy", placePos == null ? "none" : prior == null ? "unrecorded" : prior.dy());
        row.put("beforeState", beforeState.toString());
        row.put("beforeDy", String.format("%.6f", resolverAim == null
                ? SlabSupport.getYOffset(world, clickedOwnerPos, beforeState)
                : resolverAim.ownerVisibleDy()));
        row.put("beforeLaneKind", slabbed$liveCursorLaneKind(world, clickedOwnerPos, beforeState));
        row.put("beforeAttachments", slabbed$liveCursorAttachmentFacts(world, clickedOwnerPos, beforeState));
        row.put("clickedOwnerLaneKind", slabbed$liveCursorLaneKind(world, clickedOwnerPos, beforeState));
        row.put("afterState", afterState == null ? "none" : afterState.toString());
        row.put("afterDy", afterState == null
                ? "none"
                : String.format("%.6f", SlabSupport.getYOffset(world, placePos, afterState)));
        row.put("afterLaneKind", afterState == null
                ? "none"
                : slabbed$liveCursorLaneKind(world, placePos, afterState));
        row.put("afterPersistentLoweredSlabCarrier", afterState == null
                ? "none"
                : Boolean.toString(SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, placePos, afterState)));
        slabbed$c3AppendRecorderPairFacts(row, world, placePos, afterState);
        row.put("actualResult", result == null ? "null" : result.toString());
        SlabbedDiagnosticsBridge.recordAction(row);
    }

    private static void slabbed$c3AppendRecorderPairFacts(
            LinkedHashMap<String, String> row,
            Level world,
            BlockPos primary,
            BlockState primaryState
    ) {
        if (primary == null || primaryState == null) {
            slabbed$c3PutRecorderFact(row, "afterStoredDy", "afterStoredDyBits",
                    SlabAnchorAttachment.PlacementDyFact.absent());
            slabbed$c3PutNoRecorderPair(row);
            return;
        }
        slabbed$c3PutRecorderFact(row, "afterStoredDy", "afterStoredDyBits",
                slabbed$c3RecorderFact(world, primary));
        BlockPos pair = slabbed$c3RecorderPair(world, primary, primaryState);
        if (pair == null) {
            slabbed$c3PutNoRecorderPair(row);
            return;
        }
        BlockState pairState = world.getBlockState(pair);
        row.put("pairPos", pair.toShortString());
        row.put("pairPart", slabbed$c3RecorderPart(pairState));
        row.put("pairState", pairState.toString());
        row.put("pairAfterDy", Double.toString(SlabSupport.getYOffset(world, pair, pairState)));
        slabbed$c3PutRecorderFact(row, "pairStoredDy", "pairStoredDyBits",
                slabbed$c3RecorderFact(world, pair));
    }

    private static SlabAnchorAttachment.PlacementDyFact slabbed$c3RecorderFact(
            Level world,
            BlockPos pos
    ) {
        double effective = SlabAnchorAttachment.storedPlacementDy(world, pos);
        return Double.isFinite(effective)
                ? SlabAnchorAttachment.PlacementDyFact.present(effective)
                : SlabAnchorAttachment.PlacementDyFact.absent();
    }

    private static BlockPos slabbed$c3RecorderPair(Level world, BlockPos primary, BlockState state) {
        BlockPos pair;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            pair = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                    ? primary.above()
                    : primary.below();
            BlockState pairState = world.getBlockState(pair);
            return pairState.getBlock() == state.getBlock()
                    && pairState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && pairState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    != state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    ? pair
                    : null;
        }
        if (state.getBlock() instanceof BedBlock && state.hasProperty(BlockStateProperties.BED_PART)) {
            pair = primary.relative(BedBlock.getConnectedDirection(state));
            BlockState pairState = world.getBlockState(pair);
            return pairState.getBlock() == state.getBlock()
                    && pairState.hasProperty(BlockStateProperties.BED_PART)
                    && pairState.getValue(BlockStateProperties.BED_PART)
                    != state.getValue(BlockStateProperties.BED_PART)
                    && primary.equals(pair.relative(BedBlock.getConnectedDirection(pairState)))
                    ? pair
                    : null;
        }
        return null;
    }

    private static String slabbed$c3RecorderPart(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    .toString().toLowerCase(java.util.Locale.ROOT);
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)) {
            return state.getValue(BlockStateProperties.BED_PART)
                    .toString().toLowerCase(java.util.Locale.ROOT);
        }
        return "none";
    }

    private static void slabbed$c3PutRecorderFact(
            LinkedHashMap<String, String> row,
            String valueField,
            String bitsField,
            SlabAnchorAttachment.PlacementDyFact fact
    ) {
        if (fact == null || !fact.present()) {
            row.put(valueField, "none");
            row.put(bitsField, "none");
            return;
        }
        row.put(valueField, Double.toString(Double.longBitsToDouble(fact.rawBits())));
        row.put(bitsField, String.format(java.util.Locale.ROOT, "%016x", fact.rawBits()));
    }

    private static void slabbed$c3PutNoRecorderPair(LinkedHashMap<String, String> row) {
        row.put("pairPos", "none");
        row.put("pairPart", "none");
        row.put("pairState", "none");
        row.put("pairAfterDy", "none");
        row.put("pairStoredDy", "none");
        row.put("pairStoredDyBits", "none");
    }

    private static String slabbed$liveCursorAttachmentFacts(Level world, BlockPos pos, BlockState state) {
        return "anchored=" + SlabAnchorAttachment.isAnchored(world, pos)
                + ",compoundFullBlockAnchor=" + SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)
                + ",persistentLoweredSlabCarrier="
                + SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)
                + ",persistentLoweredBottomSlabCarrier="
                + SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state);
    }

    private static String slabbed$liveCursorLaneKind(Level world, BlockPos pos, BlockState state) {
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)) {
            return "persistent_lowered_slab_carrier";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)) {
            return "compound_visible_side_lower_slab";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)) {
            return "compound_visible_side_upper_slab";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)) {
            return "compound_visible_side_double_slab";
        }
        if (SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)) {
            return "compound_visible_owner_top_slab";
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            return "anchored_full_block";
        }
        return state.getBlock() instanceof SlabBlock ? "unnamed_or_vanilla_slab" : "none";
    }
}
