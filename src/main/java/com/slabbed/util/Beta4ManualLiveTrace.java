package com.slabbed.util;

import com.slabbed.anchor.SlabAnchorAttachment;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class Beta4ManualLiveTrace {
    private static final String OPT_IN = "slabbed.beta4ManualLiveTrace";
    private static final double EPSILON = 1.0e-6d;
    private static final int[] DELAY_TICKS = {1, 5, 20, 40};
    private static final AtomicInteger NEXT_CLICK_INDEX = new AtomicInteger();
    private static final ThreadLocal<Integer> ACTIVE_CLICK_INDEX = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUPPRESS_SUPPORT_TRACE = ThreadLocal.withInitial(() -> false);
    private static final List<DelayedClick> PENDING_DELAYED_CLICKS = new ArrayList<>();
    private static volatile int lastClickIndex;

    private Beta4ManualLiveTrace() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(OPT_IN);
    }

    public static ClickSnapshot startClientClick(Level world, ItemStack heldStack, BlockHitResult hit, HitResult crosshairTarget) {
        if (!enabled() || world == null || hit == null || !heldIsSlab(heldStack)) {
            return null;
        }
        int clickIndex = NEXT_CLICK_INDEX.incrementAndGet();
        lastClickIndex = clickIndex;
        ACTIVE_CLICK_INDEX.set(clickIndex);
        String classification = classify(world, hit, heldStack);
        SlabSupport.CompoundSlabRemapDecision decision = predictedDecision(world, hit);
        BlockPos expectedCandidate = expectedCandidate(world, hit, decision);
        ClickSnapshot snapshot = new ClickSnapshot(
                clickIndex,
                hit,
                heldItem(heldStack),
                expectedCandidate,
                classification,
                cellAt(world, hit.getBlockPos()),
                snapshotAround(world, hit, expectedCandidate));
        System.out.println("[JULIA_BETA4_MANUAL_LIVE_CLICK_START]"
                + baseFields(clickIndex, world, hit, heldStack)
                + " crosshairTargetBefore=" + formatHit(world, crosshairTarget)
                + " classification=" + classification
                + " predictedCandidate=" + formatPos(expectedCandidate)
                + " predictedReason=" + (decision == null ? "none" : decision.reason()));
        System.out.println("[JULIA_BETA4_MANUAL_LIVE_TARGET]"
                + baseFields(clickIndex, world, hit, heldStack)
                + " crosshairTargetBefore=" + formatHit(world, crosshairTarget)
                + " classification=" + classification
                + " reason=" + targetReason(world, hit, decision));
        return snapshot;
    }

    public static void finishClientClick(Level world, ClickSnapshot snapshot, InteractionResult result) {
        if (!enabled() || world == null || snapshot == null) {
            return;
        }
        try {
            Map<BlockPos, Cell> after = snapshotAround(world, snapshot.hit(), snapshot.expectedCandidate());
            Delta delta = diff(snapshot.before(), after);
            BlockPos expected = snapshot.expectedCandidate();
            BlockState expectedState = expected == null ? null : world.getBlockState(expected);
            boolean wrongDelta = delta.changedCount() > 0 && expected != null && !delta.changedPositions().contains(expected);
            System.out.println("[JULIA_BETA4_MANUAL_LIVE_DELTA]"
                    + baseFields(snapshot.clickIndex(), world, snapshot.hit(), snapshot.heldItem())
                    + " expectedCandidate=" + formatPos(expected)
                    + " changedPos=" + delta.changedPositionsText()
                    + " changedCount=" + delta.changedCount()
                    + " wrongDelta=" + wrongDelta
                    + " changed=" + delta.changedCellsText());
            System.out.println("[JULIA_BETA4_MANUAL_LIVE_FINAL]"
                    + baseFields(snapshot.clickIndex(), world, snapshot.hit(), snapshot.heldItem())
                    + " result=" + result
                    + " accepted=" + (result != null && result.consumesAction())
                    + " expectedCandidate=" + formatPos(expected)
                    + " expectedFinalState=" + formatState(expectedState)
                    + " expectedFinalDy=" + formatDy(world, expected, expectedState)
                    + " changedPos=" + delta.changedPositionsText());
            System.out.println("[JULIA_BETA4_MANUAL_LIVE_SUMMARY]"
                    + " clickIndex=" + snapshot.clickIndex()
                    + " heldItem=" + snapshot.heldItem()
                    + " classification=" + snapshot.classification()
                    + " targetPos=" + formatPos(snapshot.hit().getBlockPos())
                    + " face=" + snapshot.hit().getDirection()
                    + " predictedCandidate=" + formatPos(expected)
                    + " result=" + result
                    + " changedCount=" + delta.changedCount()
                    + " wrongDelta=" + wrongDelta
                    + " releaseReady=false"
                    + " reason=manual_trace_capture_only");
            scheduleDelayed(snapshot, result, delta, after);
        } finally {
            ACTIVE_CLICK_INDEX.remove();
        }
    }

    public static void tickClient(Level world) {
        if (!enabled() || world == null) {
            PENDING_DELAYED_CLICKS.clear();
            return;
        }
        Iterator<DelayedClick> iterator = PENDING_DELAYED_CLICKS.iterator();
        while (iterator.hasNext()) {
            DelayedClick pending = iterator.next();
            pending.ageTicks++;
            while (pending.nextDelayIndex < DELAY_TICKS.length
                    && pending.ageTicks >= DELAY_TICKS[pending.nextDelayIndex]) {
                logDelayed(world, pending, DELAY_TICKS[pending.nextDelayIndex]);
                pending.nextDelayIndex++;
            }
            if (pending.nextDelayIndex >= DELAY_TICKS.length) {
                iterator.remove();
            }
        }
    }

    public static void logPlacementIntent(UseOnContext incoming, UseOnContext outgoing, String decision) {
        if (!enabled() || incoming == null || incoming.getLevel() == null || !heldIsSlab(incoming.getItemInHand())) {
            return;
        }
        Level world = incoming.getLevel();
        int clickIndex = currentClickIndex();
        BlockHitResult incomingHit = new BlockHitResult(
                incoming.getClickLocation(),
                incoming.getClickedFace(),
                incoming.getClickedPos(),
                incoming.isInside());
        BlockPos outgoingPos = outgoing == null ? null : outgoing.getClickedPos();
        BlockState outgoingState = outgoingPos == null ? null : outgoing.getLevel().getBlockState(outgoingPos);
        SlabSupport.CompoundSlabRemapDecision predicted = predictedDecision(world, incomingHit);
        System.out.println("[JULIA_BETA4_MANUAL_LIVE_PLACEMENT_INTENT]"
                + baseFields(clickIndex, world, incomingHit, incoming.getItemInHand())
                + " ran=true"
                + " side=" + (world.isClientSide() ? "CLIENT" : "SERVER")
                + " outgoingPos=" + formatPos(outgoingPos)
                + " outgoingFace=" + (outgoing == null ? "null" : outgoing.getClickedFace())
                + " outgoingHit=" + (outgoing == null ? "null" : formatVec(outgoing.getClickLocation()))
                + " outgoingState=" + formatState(outgoingState)
                + " outgoingDy=" + formatDy(outgoing == null ? world : outgoing.getLevel(), outgoingPos, outgoingState)
                + " predictedCandidate=" + formatPos(predicted == null ? null : predicted.candidatePlacementPos())
                + " predictedReason=" + (predicted == null ? "none" : predicted.reason())
                + " result=pending"
                + " reason=" + decision);
    }

    public static void logServerTolerance(
            Level world,
            BlockHitResult hit,
            ItemStack heldStack,
            Vec3 centerBefore,
            Vec3 centerAfter,
            String reason
    ) {
        if (!enabled() || world == null || hit == null || !heldIsSlab(heldStack)) {
            return;
        }
        System.out.println("[JULIA_BETA4_MANUAL_LIVE_SERVER_TOLERANCE]"
                + baseFields(currentClickIndex(), world, hit, heldStack)
                + " ran=true"
                + " centerBefore=" + formatVec(centerBefore)
                + " centerAfter=" + formatVec(centerAfter)
                + " result=" + (sameVec(centerBefore, centerAfter) ? "unchanged" : "rewritten")
                + " reason=" + reason);
    }

    public static void logSlabSupportDecision(
            BlockGetter world,
            BlockPos sourcePos,
            BlockState sourceState,
            Direction intendedDirection,
            Vec3 hitPos,
            SlabSupport.CompoundSlabRemapDecision decision
    ) {
        if (!enabled() || SUPPRESS_SUPPORT_TRACE.get() || world == null || decision == null) {
            return;
        }
        String side = world instanceof Level realWorld ? (realWorld.isClientSide() ? "CLIENT" : "SERVER") : "BLOCK_VIEW";
        System.out.println("[JULIA_BETA4_MANUAL_LIVE_SLAB_SUPPORT_DECISION]"
                + " clickIndex=" + currentClickIndex()
                + " side=" + side
                + " targetPos=" + formatPos(sourcePos)
                + " targetState=" + formatState(sourceState)
                + " targetDy=" + formatDy(world, sourcePos, sourceState)
                + " face=" + intendedDirection
                + " hit=" + formatVec(hitPos)
                + " localX=" + local(sourcePos, hitPos, 'x')
                + " localY=" + local(sourcePos, hitPos, 'y')
                + " localZ=" + local(sourcePos, hitPos, 'z')
                + " heldItem=unknown"
                + " predictedCandidate=" + formatPos(decision.candidatePlacementPos())
                + " candidateState=" + formatState(candidateState(world, decision.candidatePlacementPos()))
                + " candidateDy=" + candidateDy(world, decision.candidatePlacementPos())
                + " legalLanePos=" + formatPos(decision.legalLanePos())
                + " result=" + (decision.legal() ? "LEGAL" : "REJECT")
                + " classification=" + classify(world, sourcePos, sourceState, intendedDirection, hitPos, decision)
                + " reason=" + decision.reason());
    }

    public static int currentClickIndex() {
        Integer active = ACTIVE_CLICK_INDEX.get();
        return active == null ? lastClickIndex : active;
    }

    public static boolean heldIsSlab(ItemStack stack) {
        return stack != null
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof SlabBlock;
    }

    public static String formatHit(Level world, HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return hit == null ? "null" : hit.getType().toString();
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = world == null ? null : world.getBlockState(pos);
        return "{pos=" + formatPos(pos)
                + " face=" + blockHit.getDirection()
                + " hit=" + formatVec(blockHit.getLocation())
                + " state=" + formatState(state)
                + " dy=" + formatDy(world, pos, state)
                + "}";
    }

    private static String baseFields(int clickIndex, Level world, BlockHitResult hit, ItemStack heldStack) {
        BlockPos targetPos = hit.getBlockPos();
        BlockState targetState = world.getBlockState(targetPos);
        SlabSupport.CompoundSlabRemapDecision predicted = predictedDecision(world, hit);
        return " clickIndex=" + clickIndex
                + " side=" + (world.isClientSide() ? "CLIENT" : "SERVER")
                + " heldItem=" + heldItem(heldStack)
                + " targetPos=" + formatPos(targetPos)
                + " targetState=" + formatState(targetState)
                + " targetDy=" + formatDy(world, targetPos, targetState)
                + " face=" + hit.getDirection()
                + " hit=" + formatVec(hit.getLocation())
                + " localX=" + local(targetPos, hit.getLocation(), 'x')
                + " localY=" + local(targetPos, hit.getLocation(), 'y')
                + " localZ=" + local(targetPos, hit.getLocation(), 'z')
                + " visualLocalY=" + visualLocalY(world, targetPos, targetState, hit.getLocation())
                + " band=" + band(world, targetPos, targetState, hit.getLocation())
                + " ownerClass=" + ownerClass(world, targetPos, targetState)
                + " sourceCompoundAnchor=" + SlabAnchorAttachment.isCompoundFullBlockAnchor(world, targetPos)
                + " expectedCandidate=" + formatPos(expectedCandidate(world, hit, predicted));
    }

    private static String baseFields(int clickIndex, Level world, BlockHitResult hit, String heldItem) {
        BlockPos targetPos = hit.getBlockPos();
        BlockState targetState = world.getBlockState(targetPos);
        return " clickIndex=" + clickIndex
                + " side=" + (world.isClientSide() ? "CLIENT" : "SERVER")
                + " heldItem=" + heldItem
                + " targetPos=" + formatPos(targetPos)
                + " targetState=" + formatState(targetState)
                + " targetDy=" + formatDy(world, targetPos, targetState)
                + " face=" + hit.getDirection()
                + " hit=" + formatVec(hit.getLocation())
                + " localX=" + local(targetPos, hit.getLocation(), 'x')
                + " localY=" + local(targetPos, hit.getLocation(), 'y')
                + " localZ=" + local(targetPos, hit.getLocation(), 'z')
                + " visualLocalY=" + visualLocalY(world, targetPos, targetState, hit.getLocation())
                + " band=" + band(world, targetPos, targetState, hit.getLocation())
                + " ownerClass=" + ownerClass(world, targetPos, targetState)
                + " sourceCompoundAnchor=" + SlabAnchorAttachment.isCompoundFullBlockAnchor(world, targetPos);
    }

    private static Map<BlockPos, Cell> snapshotAround(Level world, BlockHitResult hit, BlockPos expectedCandidate) {
        Map<BlockPos, Cell> cells = new LinkedHashMap<>();
        BlockPos center = hit.getBlockPos();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
            BlockPos immutable = pos.immutable();
            cells.put(immutable, cellAt(world, immutable));
        }
        BlockPos candidate = expectedCandidate == null ? defaultCandidate(hit) : expectedCandidate;
        if (candidate != null && !cells.containsKey(candidate)) {
            cells.put(candidate.immutable(), cellAt(world, candidate));
        }
        return cells;
    }

    private static Cell cellAt(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return new Cell("null", Double.NaN);
        }
        BlockState state = world.getBlockState(pos);
        return new Cell(formatState(state), SlabSupport.getYOffset(world, pos, state));
    }

    private static Delta diff(Map<BlockPos, Cell> before, Map<BlockPos, Cell> after) {
        Map<BlockPos, Change> changes = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, Cell> entry : before.entrySet()) {
            Cell afterCell = after.get(entry.getKey());
            if (afterCell != null && !entry.getValue().equals(afterCell)) {
                changes.put(entry.getKey(), new Change(entry.getValue(), afterCell));
            }
        }
        return new Delta(changes);
    }

    private static SlabSupport.CompoundSlabRemapDecision predictedDecision(Level world, BlockHitResult hit) {
        if (world == null || hit == null) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        SUPPRESS_SUPPORT_TRACE.set(true);
        try {
            return SlabSupport.findLegalCompoundSlabRemap(world, pos, state, hit.getDirection(), hit.getLocation());
        } finally {
            SUPPRESS_SUPPORT_TRACE.set(false);
        }
    }

    private static boolean sameVec(Vec3 first, Vec3 second) {
        return first == null ? second == null : first.equals(second);
    }

    private static BlockPos expectedCandidate(
            Level world,
            BlockHitResult hit,
            SlabSupport.CompoundSlabRemapDecision decision
    ) {
        if (decision != null && decision.candidatePlacementPos() != null) {
            return decision.candidatePlacementPos();
        }
        if (world != null && hit != null && world.getBlockState(hit.getBlockPos()).getBlock() instanceof SlabBlock) {
            return hit.getBlockPos();
        }
        return defaultCandidate(hit);
    }

    private static BlockPos defaultCandidate(BlockHitResult hit) {
        return hit == null ? null : hit.getBlockPos().relative(hit.getDirection());
    }

    private static String targetReason(
            Level world,
            BlockHitResult hit,
            SlabSupport.CompoundSlabRemapDecision decision
    ) {
        if (decision != null && decision.legal()) {
            return decision.reason();
        }
        BlockState state = world.getBlockState(hit.getBlockPos());
        if (state.getBlock() instanceof SlabBlock) {
            return "target_existing_slab";
        }
        return decision == null ? "no_prediction" : decision.reason();
    }

    private static String classify(Level world, BlockHitResult hit, ItemStack heldStack) {
        return classify(world, hit.getBlockPos(), world.getBlockState(hit.getBlockPos()), hit.getDirection(), hit.getLocation(),
                heldIsSlab(heldStack) ? predictedDecision(world, hit) : null);
    }

    private static String classify(Level world, BlockHitResult hit, String heldItem) {
        return classify(world, hit.getBlockPos(), world.getBlockState(hit.getBlockPos()), hit.getDirection(), hit.getLocation(),
                predictedDecision(world, hit));
    }

    private static String classify(
            BlockGetter world,
            BlockPos pos,
            BlockState state,
            Direction face,
            Vec3 hitPos,
            SlabSupport.CompoundSlabRemapDecision decision
    ) {
        if (decision != null && "COMPOUND_SUPPORT_MISSING_VISIBLE_OWNER_SIDE_SLAB".equals(decision.reason())) {
            return face == Direction.UP ? "SUPPORT_MISSING_TOP" : "SUPPORT_MISSING_SIDE";
        }
        if (decision != null && "compound_visible_owner_top_candidate_not_air".equals(decision.reason())) {
            return "TOP_FACE_AFTER_EXISTING_TOP_SLAB";
        }
        if (decision != null && decision.reason().contains("candidate_not_air")) {
            return face == Direction.UP ? "TOP_FACE_AFTER_EXISTING_TOP_SLAB" : "SIDE_AFTER_EXISTING_SIDE_SLAB";
        }
        if (decision != null && "COMPOUND_VISIBLE_OWNER_TOP_SLAB".equals(decision.reason())) {
            return "TOP_FACE";
        }
        if (state != null && state.getBlock() instanceof SlabBlock) {
            return "REPEAT_SIDE";
        }
        if (face == Direction.UP) {
            return "TOP_FACE";
        }
        if (face != null && face.getAxis().isHorizontal()) {
            double visualLocalY = visualLocalYValue(world, pos, state, hitPos);
            if (!Double.isNaN(visualLocalY)) {
                return visualLocalY < 0.5d ? "LOWER_SIDE" : "UPPER_SIDE";
            }
            return "UNKNOWN";
        }
        return "UNKNOWN";
    }

    private static String ownerClass(BlockGetter world, BlockPos pos, BlockState state) {
        if (state == null) {
            return "none";
        }
        if (SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)) {
            return "compound_full_block_anchor";
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            return "anchored";
        }
        if (state.getBlock() instanceof SlabBlock) {
            return "slab";
        }
        if (state.isAir()) {
            return "air";
        }
        return state.isSolidRender(world, pos) ? "solid" : "other";
    }

    private static String band(BlockGetter world, BlockPos pos, BlockState state, Vec3 hitPos) {
        double value = visualLocalYValue(world, pos, state, hitPos);
        if (Double.isNaN(value)) {
            return "unknown";
        }
        if (value < 0.0d || value > 1.0d) {
            return "outside";
        }
        return value < 0.5d ? "lower" : "upper";
    }

    private static String visualLocalY(BlockGetter world, BlockPos pos, BlockState state, Vec3 hitPos) {
        double value = visualLocalYValue(world, pos, state, hitPos);
        return Double.isNaN(value) ? "NaN" : String.format("%.6f", value);
    }

    private static double visualLocalYValue(BlockGetter world, BlockPos pos, BlockState state, Vec3 hitPos) {
        if (world == null || pos == null || state == null || hitPos == null) {
            return Double.NaN;
        }
        return hitPos.y - (pos.getY() + SlabSupport.getYOffset(world, pos, state));
    }

    private static String local(BlockPos pos, Vec3 hitPos, char axis) {
        if (pos == null || hitPos == null) {
            return "NaN";
        }
        double value = switch (axis) {
            case 'x' -> hitPos.x - pos.getX();
            case 'y' -> hitPos.y - pos.getY();
            case 'z' -> hitPos.z - pos.getZ();
            default -> Double.NaN;
        };
        return String.format("%.6f", value);
    }

    private static String heldItem(ItemStack stack) {
        return stack == null ? "null" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String formatPos(BlockPos pos) {
        return pos == null ? "null" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String formatVec(Vec3 vec) {
        return vec == null ? "null" : String.format("%.6f,%.6f,%.6f", vec.x, vec.y, vec.z);
    }

    private static String formatState(BlockState state) {
        return state == null ? "null" : state.toString().replace(' ', '_');
    }

    private static BlockState candidateState(BlockGetter world, BlockPos pos) {
        return world == null || pos == null ? null : world.getBlockState(pos);
    }

    private static String candidateDy(BlockGetter world, BlockPos pos) {
        BlockState state = candidateState(world, pos);
        return formatDy(world, pos, state);
    }

    private static String formatDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return "NaN";
        }
        return String.format("%.6f", SlabSupport.getYOffset(world, pos, state));
    }

    private static void scheduleDelayed(ClickSnapshot snapshot, InteractionResult result, Delta immediateDelta, Map<BlockPos, Cell> immediateAfter) {
        PENDING_DELAYED_CLICKS.add(new DelayedClick(
                snapshot,
                result == null ? "null" : result.toString(),
                result != null && result.consumesAction(),
                immediateDelta,
                immediateAfter));
    }

    private static void logDelayed(Level world, DelayedClick pending, int delayTicks) {
        ClickSnapshot snapshot = pending.snapshot;
        BlockPos expected = snapshot.expectedCandidate();
        Cell immediateExpected = pending.immediateAfter.get(expected);
        Cell delayedExpected = cellAt(world, expected);
        Map<BlockPos, Cell> delayedAfter = snapshotAround(world, snapshot.hit(), expected);
        Delta delayedDelta = diff(snapshot.before(), delayedAfter);
        boolean ghostPersisted = anyImmediateChangeStillDifferent(snapshot.before(), pending.immediateDelta, delayedAfter);
        boolean ghostResolved = pending.immediateDelta.changedCount() > 0 && !ghostPersisted;
        boolean delayedMatchesExpected = sameCell(immediateExpected, delayedExpected);
        boolean durable = pending.immediateDelta.changedCount() > 0 && delayedMatchesExpected && ghostPersisted;
        boolean ghost = pending.immediateDelta.changedCount() > 0 && (!delayedMatchesExpected || ghostResolved);
        String reason = delayedReason(pending, delayedMatchesExpected, ghostResolved, ghostPersisted);
        System.out.println("[JULIA_BETA4_MANUAL_LIVE_DELAYED_FINAL]"
                + " clickIndex=" + snapshot.clickIndex()
                + " delayTicks=" + delayTicks
                + " classification=" + snapshot.classification()
                + " originalTargetPos=" + formatPos(snapshot.hit().getBlockPos())
                + " originalTargetState=" + snapshot.targetAtStart().state()
                + " originalTargetDy=" + snapshot.targetAtStart().dyText()
                + " originalFace=" + snapshot.hit().getDirection()
                + " predictedCandidate=" + formatPos(expected)
                + " expectedFinalState=" + (immediateExpected == null ? "null" : immediateExpected.state())
                + " expectedFinalDy=" + (immediateExpected == null ? "NaN" : immediateExpected.dyText())
                + " immediateChangedPos=" + pending.immediateDelta.changedPositionsText()
                + " immediateChangedCount=" + pending.immediateDelta.changedCount()
                + " candidateState=" + delayedExpected.state()
                + " candidateDy=" + delayedExpected.dyText()
                + " originallyChangedNow=" + changedPositionsNowText(world, pending.immediateDelta)
                + " delayedChangedPos=" + delayedDelta.changedPositionsText()
                + " delayedChangedCount=" + delayedDelta.changedCount()
                + " delayedChanged=" + delayedDelta.changedCellsText()
                + " ghostResolved=" + ghostResolved
                + " ghostPersisted=" + ghostPersisted
                + " serverAcceptedKnown=unknown"
                + " mismatchReason=" + reason);
        System.out.println("[JULIA_BETA4_MANUAL_LIVE_DELAYED_SUMMARY]"
                + " clickIndex=" + snapshot.clickIndex()
                + " delayTicks=" + delayTicks
                + " classification=" + snapshot.classification()
                + " immediateResult=" + pending.immediateResult
                + " immediateAccepted=" + pending.immediateAccepted
                + " immediateChangedCount=" + pending.immediateDelta.changedCount()
                + " delayedCandidateState=" + delayedExpected.state()
                + " delayedCandidateDy=" + delayedExpected.dyText()
                + " delayedMatchesExpected=" + delayedMatchesExpected
                + " ghost=" + ghost
                + " durable=" + durable
                + " reason=" + reason);
    }

    private static String delayedReason(
            DelayedClick pending,
            boolean delayedMatchesExpected,
            boolean ghostResolved,
            boolean ghostPersisted
    ) {
        if (pending.immediateDelta.changedCount() == 0) {
            return "no_immediate_client_delta";
        }
        if (pending.snapshot.expectedCandidate() == null) {
            return "no_expected_candidate";
        }
        if (ghostResolved) {
            return "immediate_delta_reverted_or_vanished";
        }
        if (!delayedMatchesExpected) {
            return "delayed_candidate_mismatch";
        }
        if (ghostPersisted) {
            return "immediate_delta_still_present";
        }
        return "no_mismatch_detected";
    }

    private static boolean anyImmediateChangeStillDifferent(
            Map<BlockPos, Cell> before,
            Delta immediateDelta,
            Map<BlockPos, Cell> delayedAfter
    ) {
        for (BlockPos pos : immediateDelta.changedPositions()) {
            Cell beforeCell = before.get(pos);
            Cell delayedCell = delayedAfter.get(pos);
            if (delayedCell != null && !sameCell(beforeCell, delayedCell)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameCell(Cell first, Cell second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.state().equals(second.state()) && Math.abs(first.dy() - second.dy()) <= EPSILON;
    }

    private static String changedPositionsNowText(Level world, Delta immediateDelta) {
        if (immediateDelta.changedCount() == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (BlockPos pos : immediateDelta.changedPositions()) {
            if (!first) {
                builder.append(',');
            }
            builder.append(formatPos(pos)).append('=').append(cellAt(world, pos));
            first = false;
        }
        return builder.append(']').toString();
    }

    public record ClickSnapshot(
            int clickIndex,
            BlockHitResult hit,
            String heldItem,
            BlockPos expectedCandidate,
            String classification,
            Cell targetAtStart,
            Map<BlockPos, Cell> before
    ) {
    }

    private static final class DelayedClick {
        private final ClickSnapshot snapshot;
        private final String immediateResult;
        private final boolean immediateAccepted;
        private final Delta immediateDelta;
        private final Map<BlockPos, Cell> immediateAfter;
        private int ageTicks;
        private int nextDelayIndex;

        private DelayedClick(
                ClickSnapshot snapshot,
                String immediateResult,
                boolean immediateAccepted,
                Delta immediateDelta,
                Map<BlockPos, Cell> immediateAfter
        ) {
            this.snapshot = snapshot;
            this.immediateResult = immediateResult;
            this.immediateAccepted = immediateAccepted;
            this.immediateDelta = immediateDelta;
            this.immediateAfter = immediateAfter;
        }
    }

    private record Cell(String state, double dy) {
        String dyText() {
            return String.format("%.6f", dy);
        }

        @Override
        public String toString() {
            return "{state=" + state + ",dy=" + dyText() + "}";
        }
    }

    private record Change(Cell before, Cell after) {
        @Override
        public String toString() {
            return "{before=" + before + ",after=" + after + "}";
        }
    }

    private record Delta(Map<BlockPos, Change> changes) {
        int changedCount() {
            return changes.size();
        }

        java.util.Set<BlockPos> changedPositions() {
            return changes.keySet();
        }

        String changedPositionsText() {
            if (changes.isEmpty()) {
                return "[]";
            }
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (BlockPos pos : changes.keySet()) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(formatPos(pos));
                first = false;
            }
            return builder.append(']').toString();
        }

        String changedCellsText() {
            if (changes.isEmpty()) {
                return "[]";
            }
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Map.Entry<BlockPos, Change> entry : changes.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(formatPos(entry.getKey())).append('=').append(entry.getValue());
                first = false;
            }
            return builder.append(']').toString();
        }
    }
}
