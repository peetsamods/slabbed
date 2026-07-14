package com.slabbed.network;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment.PlacementDyFact;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Common/server-safe boundary between vanilla prediction and the client-only C3 journal. */
public final class PlacementDyPredictionBridge {
    private static final ThreadLocal<Integer> CURRENT_SEQUENCE = new ThreadLocal<>();
    private static volatile Consumer<PredictedBatch> clientBatchConsumer;
    private static final ThreadLocal<BlockItemTestFixture> BLOCK_ITEM_TEST_FIXTURE = new ThreadLocal<>();
    private static boolean testPhaseTraceEnabled;
    private static final ArrayList<String> TEST_PHASE_TRACE = new ArrayList<>();
    private static boolean testWireTraceEnabled;
    private static GroupSignature testWireSignature;
    private static final ArrayList<String> TEST_WIRE_PHASES = new ArrayList<>();

    private PlacementDyPredictionBridge() {
    }

    /** Dormant fixture seam for package-authorized GameTests that must not register synthetic BlockItems. */
    public interface BlockItemTestFixture {
        default BlockPlaceContext updatePlacementContext(BlockItem item, BlockPlaceContext context) {
            return null;
        }

        default BlockState getPlacementState(BlockItem item, BlockPlaceContext context) {
            return null;
        }

        default Boolean placeBlock(BlockItem item, BlockPlaceContext context, BlockState state) {
            return null;
        }
    }

    public record GroupSignature(
            String dimension,
            int sequence,
            InteractionHand hand,
            String heldItemId,
            long originalHitBlockPos,
            Direction originalHitFace
    ) {
        public GroupSignature {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(hand, "hand");
            Objects.requireNonNull(heldItemId, "heldItemId");
            Objects.requireNonNull(originalHitFace, "originalHitFace");
            if (sequence < 0) {
                throw new IllegalArgumentException("negative prediction sequence");
            }
        }
    }

    public record PredictedCell(
            BlockPos pos,
            BlockState priorState,
            PlacementDyFact capturedBacking,
            BlockState expectedState,
            long predictedRawBits
    ) {
        public PredictedCell {
            pos = Objects.requireNonNull(pos, "pos").immutable();
            Objects.requireNonNull(priorState, "priorState");
            Objects.requireNonNull(capturedBacking, "capturedBacking");
            Objects.requireNonNull(expectedState, "expectedState");
            if (!Double.isFinite(Double.longBitsToDouble(predictedRawBits))) {
                throw new IllegalArgumentException("predicted dy must be finite");
            }
        }
    }

    public record PredictedBatch(
            GroupSignature signature,
            List<PredictedCell> cells,
            Runnable postInstallAction
    ) {
        public PredictedBatch(GroupSignature signature, List<PredictedCell> cells) {
            this(signature, cells, null);
        }

        public PredictedBatch {
            Objects.requireNonNull(signature, "signature");
            if (cells == null || cells.isEmpty() || cells.size() > 2) {
                throw new IllegalArgumentException("prediction batch must contain one or two cells");
            }
            ArrayList<PredictedCell> sorted = new ArrayList<>(cells);
            sorted.sort(Comparator.comparingLong(cell -> cell.pos().asLong()));
            for (int i = 1; i < sorted.size(); i++) {
                if (sorted.get(i - 1).pos().equals(sorted.get(i).pos())) {
                    throw new IllegalArgumentException("duplicate prediction cell");
                }
            }
            cells = List.copyOf(sorted);
        }
    }

    public static void openSequence(int sequence) {
        if (CURRENT_SEQUENCE.get() != null) {
            throw new IllegalStateException("prediction sequence already open");
        }
        CURRENT_SEQUENCE.set(sequence);
    }

    public static void closeSequence() {
        CURRENT_SEQUENCE.remove();
    }

    public static int currentSequence() {
        Integer sequence = CURRENT_SEQUENCE.get();
        return sequence == null ? -1 : sequence;
    }

    public static void installBlockItemTestFixture(BlockItemTestFixture fixture) {
        if (BLOCK_ITEM_TEST_FIXTURE.get() != null) {
            throw new IllegalStateException("BlockItem test fixture already installed");
        }
        BLOCK_ITEM_TEST_FIXTURE.set(Objects.requireNonNull(fixture, "fixture"));
    }

    public static void clearBlockItemTestFixture() {
        BLOCK_ITEM_TEST_FIXTURE.remove();
    }

    public static BlockPlaceContext testFixturePlacementContext(
            BlockItem item,
            BlockPlaceContext context
    ) {
        BlockItemTestFixture fixture = BLOCK_ITEM_TEST_FIXTURE.get();
        return fixture == null ? null : fixture.updatePlacementContext(item, context);
    }

    public static BlockState testFixturePlacementState(BlockItem item, BlockPlaceContext context) {
        BlockItemTestFixture fixture = BLOCK_ITEM_TEST_FIXTURE.get();
        return fixture == null ? null : fixture.getPlacementState(item, context);
    }

    public static Boolean testFixturePlaceBlock(
            BlockItem item,
            BlockPlaceContext context,
            BlockState state
    ) {
        BlockItemTestFixture fixture = BLOCK_ITEM_TEST_FIXTURE.get();
        return fixture == null ? null : fixture.placeBlock(item, context, state);
    }

    public static void installClientBatchConsumer(Consumer<PredictedBatch> consumer) {
        clientBatchConsumer = Objects.requireNonNull(consumer, "consumer");
    }

    public static void clearClientBatchConsumer() {
        clientBatchConsumer = null;
    }

    public static boolean publishClientBatch(PredictedBatch batch) {
        synchronized (PlacementDyPredictionBridge.class) {
            if (testWireTraceEnabled && testWireSignature == null) {
                testWireSignature = batch.signature();
            }
        }
        Consumer<PredictedBatch> consumer = clientBatchConsumer;
        if (consumer == null) {
            Slabbed.LOGGER.warn("[C3] client prediction batch dropped: no installed client callback");
            return false;
        }
        try {
            consumer.accept(batch);
            return true;
        } catch (RuntimeException exception) {
            Slabbed.LOGGER.warn("[C3] client prediction callback failed; no overlay may be committed", exception);
            return false;
        }
    }

    public static synchronized void beginTestPhaseTrace() {
        TEST_PHASE_TRACE.clear();
        testPhaseTraceEnabled = true;
    }

    public static synchronized void markTestPhase(String phase) {
        if (testPhaseTraceEnabled) {
            TEST_PHASE_TRACE.add(phase);
        }
    }

    public static synchronized List<String> snapshotTestPhaseTrace() {
        return List.copyOf(TEST_PHASE_TRACE);
    }

    public static synchronized void endTestPhaseTrace() {
        testPhaseTraceEnabled = false;
        TEST_PHASE_TRACE.clear();
    }

    public static synchronized void beginTestWireTrace() {
        testWireTraceEnabled = true;
        testWireSignature = null;
        TEST_WIRE_PHASES.clear();
    }

    public static synchronized void traceCorrectionWire(String phase, GroupSignature signature) {
        if (!testWireTraceEnabled || testWireSignature == null || !testWireSignature.equals(signature)) {
            return;
        }
        TEST_WIRE_PHASES.add(phase);
        Slabbed.LOGGER.info("C3_CORRECTION_WIRE | {} | {}", testWireId(signature), phase);
    }

    public static synchronized List<String> snapshotTestWirePhases() {
        return List.copyOf(TEST_WIRE_PHASES);
    }

    public static synchronized GroupSignature testWireSignature() {
        return testWireSignature;
    }

    public static synchronized void endTestWireTrace() {
        testWireTraceEnabled = false;
        testWireSignature = null;
        TEST_WIRE_PHASES.clear();
    }

    private static String testWireId(GroupSignature signature) {
        return "c3-" + Integer.toUnsignedString(signature.hashCode(), 36)
                + "-" + signature.sequence();
    }
}
