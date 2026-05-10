package com.slabbed.util;

import com.slabbed.anchor.SlabAnchorAttachment;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class Beta4ManualLiveTrace {
    private static final String OPT_IN = "slabbed.beta4ManualLiveTrace";
    private static final double EPSILON = 1.0e-6d;
    private static final AtomicInteger NEXT_CLICK_INDEX = new AtomicInteger();
    private static final ThreadLocal<Integer> ACTIVE_CLICK_INDEX = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUPPRESS_SUPPORT_TRACE = ThreadLocal.withInitial(() -> false);
    private static volatile int lastClickIndex;

    private Beta4ManualLiveTrace() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(OPT_IN);
    }

    public static ClickSnapshot startClientClick(World world, ItemStack heldStack, BlockHitResult hit, HitResult crosshairTarget) {
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

    public static void finishClientClick(World world, ClickSnapshot snapshot, ActionResult result) {
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
                    + " accepted=" + (result != null && result.isAccepted())
                    + " expectedCandidate=" + formatPos(expected)
                    + " expectedFinalState=" + formatState(expectedState)
                    + " expectedFinalDy=" + formatDy(world, expected, expectedState)
                    + " changedPos=" + delta.changedPositionsText());
            System.out.println("[JULIA_BETA4_MANUAL_LIVE_SUMMARY]"
                    + " clickIndex=" + snapshot.clickIndex()
                    + " heldItem=" + snapshot.heldItem()
                    + " classification=" + classify(world, snapshot.hit(), snapshot.heldItem())
                    + " targetPos=" + formatPos(snapshot.hit().getBlockPos())
                    + " face=" + snapshot.hit().getSide()
                    + " predictedCandidate=" + formatPos(expected)
                    + " result=" + result
                    + " changedCount=" + delta.changedCount()
                    + " wrongDelta=" + wrongDelta
                    + " releaseReady=false"
                    + " reason=manual_trace_capture_only");
        } finally {
            ACTIVE_CLICK_INDEX.remove();
        }
    }

    public static void logPlacementIntent(ItemUsageContext incoming, ItemUsageContext outgoing, String decision) {
        if (!enabled() || incoming == null || incoming.getWorld() == null || !heldIsSlab(incoming.getStack())) {
            return;
        }
        World world = incoming.getWorld();
        int clickIndex = currentClickIndex();
        BlockHitResult incomingHit = new BlockHitResult(
                incoming.getHitPos(),
                incoming.getSide(),
                incoming.getBlockPos(),
                incoming.hitsInsideBlock());
        BlockPos outgoingPos = outgoing == null ? null : outgoing.getBlockPos();
        BlockState outgoingState = outgoingPos == null ? null : outgoing.getWorld().getBlockState(outgoingPos);
        SlabSupport.CompoundSlabRemapDecision predicted = predictedDecision(world, incomingHit);
        System.out.println("[JULIA_BETA4_MANUAL_LIVE_PLACEMENT_INTENT]"
                + baseFields(clickIndex, world, incomingHit, incoming.getStack())
                + " ran=true"
                + " side=" + (world.isClient() ? "CLIENT" : "SERVER")
                + " outgoingPos=" + formatPos(outgoingPos)
                + " outgoingFace=" + (outgoing == null ? "null" : outgoing.getSide())
                + " outgoingHit=" + (outgoing == null ? "null" : formatVec(outgoing.getHitPos()))
                + " outgoingState=" + formatState(outgoingState)
                + " outgoingDy=" + formatDy(outgoing == null ? world : outgoing.getWorld(), outgoingPos, outgoingState)
                + " predictedCandidate=" + formatPos(predicted == null ? null : predicted.candidatePlacementPos())
                + " predictedReason=" + (predicted == null ? "none" : predicted.reason())
                + " result=pending"
                + " reason=" + decision);
    }

    public static void logServerTolerance(
            World world,
            BlockHitResult hit,
            ItemStack heldStack,
            Vec3d centerBefore,
            Vec3d centerAfter,
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
            BlockView world,
            BlockPos sourcePos,
            BlockState sourceState,
            Direction intendedDirection,
            Vec3d hitPos,
            SlabSupport.CompoundSlabRemapDecision decision
    ) {
        if (!enabled() || SUPPRESS_SUPPORT_TRACE.get() || world == null || decision == null) {
            return;
        }
        String side = world instanceof World realWorld ? (realWorld.isClient() ? "CLIENT" : "SERVER") : "BLOCK_VIEW";
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

    public static String formatHit(World world, HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return hit == null ? "null" : hit.getType().toString();
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = world == null ? null : world.getBlockState(pos);
        return "{pos=" + formatPos(pos)
                + " face=" + blockHit.getSide()
                + " hit=" + formatVec(blockHit.getPos())
                + " state=" + formatState(state)
                + " dy=" + formatDy(world, pos, state)
                + "}";
    }

    private static String baseFields(int clickIndex, World world, BlockHitResult hit, ItemStack heldStack) {
        BlockPos targetPos = hit.getBlockPos();
        BlockState targetState = world.getBlockState(targetPos);
        SlabSupport.CompoundSlabRemapDecision predicted = predictedDecision(world, hit);
        return " clickIndex=" + clickIndex
                + " side=" + (world.isClient() ? "CLIENT" : "SERVER")
                + " heldItem=" + heldItem(heldStack)
                + " targetPos=" + formatPos(targetPos)
                + " targetState=" + formatState(targetState)
                + " targetDy=" + formatDy(world, targetPos, targetState)
                + " face=" + hit.getSide()
                + " hit=" + formatVec(hit.getPos())
                + " localX=" + local(targetPos, hit.getPos(), 'x')
                + " localY=" + local(targetPos, hit.getPos(), 'y')
                + " localZ=" + local(targetPos, hit.getPos(), 'z')
                + " visualLocalY=" + visualLocalY(world, targetPos, targetState, hit.getPos())
                + " band=" + band(world, targetPos, targetState, hit.getPos())
                + " ownerClass=" + ownerClass(world, targetPos, targetState)
                + " sourceCompoundAnchor=" + SlabAnchorAttachment.isCompoundFullBlockAnchor(world, targetPos)
                + " expectedCandidate=" + formatPos(expectedCandidate(world, hit, predicted));
    }

    private static String baseFields(int clickIndex, World world, BlockHitResult hit, String heldItem) {
        BlockPos targetPos = hit.getBlockPos();
        BlockState targetState = world.getBlockState(targetPos);
        return " clickIndex=" + clickIndex
                + " side=" + (world.isClient() ? "CLIENT" : "SERVER")
                + " heldItem=" + heldItem
                + " targetPos=" + formatPos(targetPos)
                + " targetState=" + formatState(targetState)
                + " targetDy=" + formatDy(world, targetPos, targetState)
                + " face=" + hit.getSide()
                + " hit=" + formatVec(hit.getPos())
                + " localX=" + local(targetPos, hit.getPos(), 'x')
                + " localY=" + local(targetPos, hit.getPos(), 'y')
                + " localZ=" + local(targetPos, hit.getPos(), 'z')
                + " visualLocalY=" + visualLocalY(world, targetPos, targetState, hit.getPos())
                + " band=" + band(world, targetPos, targetState, hit.getPos())
                + " ownerClass=" + ownerClass(world, targetPos, targetState)
                + " sourceCompoundAnchor=" + SlabAnchorAttachment.isCompoundFullBlockAnchor(world, targetPos);
    }

    private static Map<BlockPos, Cell> snapshotAround(World world, BlockHitResult hit, BlockPos expectedCandidate) {
        Map<BlockPos, Cell> cells = new LinkedHashMap<>();
        BlockPos center = hit.getBlockPos();
        for (BlockPos pos : BlockPos.iterate(center.add(-2, -2, -2), center.add(2, 2, 2))) {
            BlockPos immutable = pos.toImmutable();
            BlockState state = world.getBlockState(immutable);
            cells.put(immutable, new Cell(formatState(state), SlabSupport.getYOffset(world, immutable, state)));
        }
        BlockPos candidate = expectedCandidate == null ? defaultCandidate(hit) : expectedCandidate;
        if (candidate != null && !cells.containsKey(candidate)) {
            BlockState state = world.getBlockState(candidate);
            cells.put(candidate.toImmutable(), new Cell(formatState(state), SlabSupport.getYOffset(world, candidate, state)));
        }
        return cells;
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

    private static SlabSupport.CompoundSlabRemapDecision predictedDecision(World world, BlockHitResult hit) {
        if (world == null || hit == null) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        SUPPRESS_SUPPORT_TRACE.set(true);
        try {
            return SlabSupport.findLegalCompoundSlabRemap(world, pos, state, hit.getSide(), hit.getPos());
        } finally {
            SUPPRESS_SUPPORT_TRACE.set(false);
        }
    }

    private static boolean sameVec(Vec3d first, Vec3d second) {
        return first == null ? second == null : first.equals(second);
    }

    private static BlockPos expectedCandidate(
            World world,
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
        return hit == null ? null : hit.getBlockPos().offset(hit.getSide());
    }

    private static String targetReason(
            World world,
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

    private static String classify(World world, BlockHitResult hit, ItemStack heldStack) {
        return classify(world, hit.getBlockPos(), world.getBlockState(hit.getBlockPos()), hit.getSide(), hit.getPos(),
                heldIsSlab(heldStack) ? predictedDecision(world, hit) : null);
    }

    private static String classify(World world, BlockHitResult hit, String heldItem) {
        return classify(world, hit.getBlockPos(), world.getBlockState(hit.getBlockPos()), hit.getSide(), hit.getPos(),
                predictedDecision(world, hit));
    }

    private static String classify(
            BlockView world,
            BlockPos pos,
            BlockState state,
            Direction face,
            Vec3d hitPos,
            SlabSupport.CompoundSlabRemapDecision decision
    ) {
        if (decision != null && "COMPOUND_SUPPORT_MISSING_VISIBLE_OWNER_SIDE_SLAB".equals(decision.reason())) {
            return face == Direction.UP ? "SUPPORT_MISSING_TOP" : "SUPPORT_MISSING_SIDE";
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

    private static String ownerClass(BlockView world, BlockPos pos, BlockState state) {
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
        return state.isSolidBlock(world, pos) ? "solid" : "other";
    }

    private static String band(BlockView world, BlockPos pos, BlockState state, Vec3d hitPos) {
        double value = visualLocalYValue(world, pos, state, hitPos);
        if (Double.isNaN(value)) {
            return "unknown";
        }
        if (value < 0.0d || value > 1.0d) {
            return "outside";
        }
        return value < 0.5d ? "lower" : "upper";
    }

    private static String visualLocalY(BlockView world, BlockPos pos, BlockState state, Vec3d hitPos) {
        double value = visualLocalYValue(world, pos, state, hitPos);
        return Double.isNaN(value) ? "NaN" : String.format("%.6f", value);
    }

    private static double visualLocalYValue(BlockView world, BlockPos pos, BlockState state, Vec3d hitPos) {
        if (world == null || pos == null || state == null || hitPos == null) {
            return Double.NaN;
        }
        return hitPos.y - (pos.getY() + SlabSupport.getYOffset(world, pos, state));
    }

    private static String local(BlockPos pos, Vec3d hitPos, char axis) {
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
        return stack == null ? "null" : Registries.ITEM.getId(stack.getItem()).toString();
    }

    private static String formatPos(BlockPos pos) {
        return pos == null ? "null" : pos.toShortString();
    }

    private static String formatVec(Vec3d vec) {
        return vec == null ? "null" : String.format("%.6f,%.6f,%.6f", vec.x, vec.y, vec.z);
    }

    private static String formatState(BlockState state) {
        return state == null ? "null" : state.toString().replace(' ', '_');
    }

    private static String formatDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return "NaN";
        }
        return String.format("%.6f", SlabSupport.getYOffset(world, pos, state));
    }

    public record ClickSnapshot(
            int clickIndex,
            BlockHitResult hit,
            String heldItem,
            BlockPos expectedCandidate,
            Map<BlockPos, Cell> before
    ) {
    }

    private record Cell(String state, double dy) {
        @Override
        public String toString() {
            return "{state=" + state + ",dy=" + String.format("%.6f", dy) + "}";
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
                builder.append(pos.toShortString());
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
                builder.append(entry.getKey().toShortString()).append('=').append(entry.getValue());
                first = false;
            }
            return builder.append(']').toString();
        }
    }
}
