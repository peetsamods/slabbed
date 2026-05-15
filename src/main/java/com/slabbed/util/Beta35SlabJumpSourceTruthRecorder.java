package com.slabbed.util;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Beta 3.5 slab-jump source-truth audit tracer.
 *
 * <p>Gated by {@code -Dslabbed.beta35SlabJumpSourceTruth=true}. Off by default.
 *
 * <p>Records every relevant anchor/marker mutation in a chronological event list so a
 * gametest probe can correlate break/place actions with which marker types (and which
 * positions) were touched, then prints classification rows for downstream triage.
 *
 * <p>This is audit-only. It does not change anchor or placement behavior.
 */
public final class Beta35SlabJumpSourceTruthRecorder {

    public static final String PROPERTY = "slabbed.beta35SlabJumpSourceTruth";

    public enum EventAction { ADD, REMOVE, CLEAR, UPDATE }

    public enum MarkerType {
        ANCHOR,
        COMPOUND_FULL_BLOCK_ANCHOR,
        COMPOUND_VISIBLE_SIDE_LOWER_SLAB,
        COMPOUND_VISIBLE_SIDE_UPPER_SLAB,
        COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB,
        COMPOUND_VISIBLE_OWNER_TOP_SLAB,
        LOWERED_SLAB_CARRIER,
        UNKNOWN
    }

    public record Event(
            EventAction action,
            MarkerType marker,
            long packedPos,
            String reason,
            String worldSide
    ) {
        public BlockPos pos() {
            return BlockPos.fromLong(packedPos);
        }
    }

    public record MarkerFlags(
            boolean anchor,
            boolean compoundFullBlockAnchor,
            boolean compoundVisibleSideLowerSlab,
            boolean compoundVisibleSideUpperSlab,
            boolean compoundVisibleSideDoubleSlab,
            boolean compoundVisibleOwnerTopSlab,
            boolean loweredSlabCarrier
    ) {
        public static MarkerFlags none() {
            return new MarkerFlags(false, false, false, false, false, false, false);
        }
    }

    public record DySample(
            String posLabel,
            long packedPos,
            String state,
            double dy,
            MarkerFlags flags,
            String supportState,
            double supportDy
    ) {
        public BlockPos pos() {
            return BlockPos.fromLong(packedPos);
        }
    }

    private static final List<Event> EVENTS = new ArrayList<>();
    private static volatile String currentReason = "unknown";

    private Beta35SlabJumpSourceTruthRecorder() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void clear() {
        synchronized (EVENTS) {
            EVENTS.clear();
        }
    }

    public static synchronized void setReason(String reason) {
        currentReason = reason == null ? "unknown" : reason;
    }

    public static List<Event> snapshotEvents() {
        synchronized (EVENTS) {
            return new ArrayList<>(EVENTS);
        }
    }

    public static void recordAnchorEvent(
            World world,
            EventAction action,
            AttachmentType<LongOpenHashSet> type,
            BlockPos pos,
            BlockState oldState,
            BlockState newState
    ) {
        if (!isEnabled() || world == null || pos == null || type == null) {
            return;
        }
        MarkerType marker = resolveMarker(type);
        String reason = currentReason;
        String side = world.isClient() ? "CLIENT" : "SERVER";
        Event event = new Event(action, marker, pos.asLong(), reason, side);
        synchronized (EVENTS) {
            EVENTS.add(event);
        }
        Slabbed.LOGGER.info(
                "[JULIA_BETA35_SLAB_JUMP_ANCHOR_EVENT] action={} marker={} pos={} oldState={} newState={} reason={} worldSide={}",
                action.name(),
                marker.name(),
                pos.toShortString(),
                formatState(oldState),
                formatState(newState),
                reason,
                side);
    }

    private static MarkerType resolveMarker(AttachmentType<LongOpenHashSet> type) {
        if (type == SlabAnchorAttachment.ANCHOR_TYPE) return MarkerType.ANCHOR;
        if (type == SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE) return MarkerType.COMPOUND_FULL_BLOCK_ANCHOR;
        if (type == SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE)
            return MarkerType.COMPOUND_VISIBLE_SIDE_LOWER_SLAB;
        if (type == SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE)
            return MarkerType.COMPOUND_VISIBLE_SIDE_UPPER_SLAB;
        if (type == SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE)
            return MarkerType.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB;
        if (type == SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE)
            return MarkerType.COMPOUND_VISIBLE_OWNER_TOP_SLAB;
        if (type == SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE) return MarkerType.LOWERED_SLAB_CARRIER;
        return MarkerType.UNKNOWN;
    }

    public static MarkerFlags sampleFlags(World world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return MarkerFlags.none();
        }
        return new MarkerFlags(
                SlabAnchorAttachment.isAnchored(world, pos),
                SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos),
                SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state),
                SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state),
                SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state),
                SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state),
                SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)
        );
    }

    public static DySample sample(World world, BlockPos pos, String label) {
        if (!isEnabled() || world == null || pos == null) {
            return new DySample(label, pos == null ? 0L : pos.asLong(), "null", Double.NaN,
                    MarkerFlags.none(), "null", Double.NaN);
        }
        BlockState state = world.getBlockState(pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        MarkerFlags flags = sampleFlags(world, pos, state);
        BlockPos supportPos = pos.down();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = SlabSupport.getYOffset(world, supportPos, supportState);
        return new DySample(label, pos.asLong(), formatState(state), dy, flags,
                formatState(supportState), supportDy);
    }

    public static void logDySample(String rowPhase, String rowId, DySample before, DySample after,
                                   String classification, String expectedLane, String actualLane,
                                   boolean visualJumpDetected) {
        if (!isEnabled()) return;
        Slabbed.LOGGER.info(
                "[JULIA_BETA35_SLAB_JUMP_DY_SAMPLE] rowPhase={} rowId={} pos={} state={} dyBefore={} dyAfter={} "
                        + "anchorBefore={} compoundFullBefore={} compVisLowerBefore={} compVisUpperBefore={} "
                        + "compVisDoubleBefore={} compVisOwnerTopBefore={} loweredCarrierBefore={} "
                        + "anchorAfter={} compoundFullAfter={} compVisLowerAfter={} compVisUpperAfter={} "
                        + "compVisDoubleAfter={} compVisOwnerTopAfter={} loweredCarrierAfter={} "
                        + "supportStateBefore={} supportDyBefore={} supportStateAfter={} supportDyAfter={} "
                        + "expectedLane={} actualLane={} visualJumpDetected={} classification={}",
                rowPhase, rowId, before.pos().toShortString(), before.state(),
                formatDouble(before.dy()), formatDouble(after.dy()),
                before.flags().anchor(), before.flags().compoundFullBlockAnchor(),
                before.flags().compoundVisibleSideLowerSlab(), before.flags().compoundVisibleSideUpperSlab(),
                before.flags().compoundVisibleSideDoubleSlab(), before.flags().compoundVisibleOwnerTopSlab(),
                before.flags().loweredSlabCarrier(),
                after.flags().anchor(), after.flags().compoundFullBlockAnchor(),
                after.flags().compoundVisibleSideLowerSlab(), after.flags().compoundVisibleSideUpperSlab(),
                after.flags().compoundVisibleSideDoubleSlab(), after.flags().compoundVisibleOwnerTopSlab(),
                after.flags().loweredSlabCarrier(),
                before.supportState(), formatDouble(before.supportDy()),
                after.supportState(), formatDouble(after.supportDy()),
                expectedLane, actualLane, visualJumpDetected, classification);
    }

    public static void logRow(String rowPhase, String rowId, BlockPos brokenOrPlacedPos,
                              BlockPos jumpingPos, String classification, String reason,
                              boolean visualJumpDetected) {
        if (!isEnabled()) return;
        Slabbed.LOGGER.info(
                "[JULIA_BETA35_SLAB_JUMP_SOURCE_TRUTH] rowPhase={} rowId={} brokenOrPlacedPos={} "
                        + "jumpingPos={} classification={} reason={} visualJumpDetected={}",
                rowPhase, rowId,
                brokenOrPlacedPos == null ? "n/a" : brokenOrPlacedPos.toShortString(),
                jumpingPos == null ? "n/a" : jumpingPos.toShortString(),
                classification, reason, visualJumpDetected);
    }

    public static void logSummary(
            int rows,
            int sourceMarkerRemovedRows,
            int adjacentDependentLostSourceTruthRows,
            int placementAuthoredNormalLaneRows,
            int neighborDyRenormalizationRows,
            int expectedPlacementRows,
            int noJumpRows,
            String recommendedNextFix,
            boolean releaseBlocking
    ) {
        if (!isEnabled()) return;
        Slabbed.LOGGER.info(
                "[JULIA_BETA35_SLAB_JUMP_SUMMARY] rows={} sourceMarkerRemovedRows={} "
                        + "adjacentDependentLostSourceTruthRows={} placementAuthoredNormalLaneRows={} "
                        + "neighborDyRenormalizationRows={} expectedPlacementRows={} noJumpRows={} "
                        + "recommendedNextFix={} releaseBlocking={} releaseAudit=NOT_RUN releaseTagMoved=false "
                        + "allItemClaim=false",
                rows, sourceMarkerRemovedRows, adjacentDependentLostSourceTruthRows,
                placementAuthoredNormalLaneRows, neighborDyRenormalizationRows,
                expectedPlacementRows, noJumpRows, recommendedNextFix,
                releaseBlocking ? "yes" : "no");
    }

    private static String formatState(BlockState state) {
        return state == null ? "null" : state.toString();
    }

    private static String formatDouble(double v) {
        return Double.isFinite(v) ? String.format("%.6f", v) : "NaN";
    }
}
