package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Test-mod-only comparison of block-entity matrices before dispatch and at the renderer call. */
public final class BlockEntityRenderAudit {

    private static final float EPSILON = 1.0e-5f;
    private static final boolean LOG = Boolean.getBoolean("slabbed.blockEntityRenderAudit");
    private static final ThreadLocal<ArrayDeque<Scope>> ACTIVE =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<Long, MutableStats> BY_POS = new HashMap<>();

    private BlockEntityRenderAudit() {
    }

    public static void begin(BlockEntity blockEntity, MatrixStack matrices) {
        BlockPos pos = blockEntity.getPos().toImmutable();
        double expectedDy = blockEntity.getWorld() == null ? 0.0d
                : SlabSupport.getYOffset(blockEntity.getWorld(), pos, blockEntity.getCachedState());
        ACTIVE.get().push(new Scope(pos, new Matrix4f(matrices.peek().getPositionMatrix()), expectedDy));
    }

    public static void observe(BlockEntity blockEntity, MatrixStack matrices) {
        Scope scope = ACTIVE.get().peek();
        if (scope == null || !scope.pos.equals(blockEntity.getPos())) {
            return;
        }
        Matrix4f expected = new Matrix4f(scope.initial)
                .translate(0.0f, (float) scope.expectedDy, 0.0f);
        Matrix4f actual = matrices.peek().getPositionMatrix();
        boolean matches = actual.equals(expected, EPSILON);

        MutableStats stats;
        synchronized (BY_POS) {
            stats = BY_POS.computeIfAbsent(scope.pos.asLong(), ignored -> new MutableStats());
            stats.seenFrames++;
            if (!matches) {
                stats.mismatchCount++;
            }
            stats.minExpectedDy = Math.min(stats.minExpectedDy, scope.expectedDy);
            stats.maxExpectedDy = Math.max(stats.maxExpectedDy, scope.expectedDy);
        }
        if (LOG) {
            Slabbed.LOGGER.info(
                    "[BLOCK_ENTITY_RENDER] pos={} seen={} mismatches={} expectedDy={}",
                    scope.pos.toShortString(), stats.seenFrames, stats.mismatchCount, scope.expectedDy);
        }
    }

    public static void end() {
        ArrayDeque<Scope> scopes = ACTIVE.get();
        if (!scopes.isEmpty()) {
            scopes.pop();
        }
        if (scopes.isEmpty()) {
            ACTIVE.remove();
        }
    }

    public static Snapshot snapshot(BlockPos pos) {
        synchronized (BY_POS) {
            MutableStats stats = BY_POS.get(pos.asLong());
            return stats == null
                    ? new Snapshot(0L, 0L, Double.NaN, Double.NaN)
                    : new Snapshot(stats.seenFrames, stats.mismatchCount,
                            stats.minExpectedDy, stats.maxExpectedDy);
        }
    }

    public static void reset() {
        synchronized (BY_POS) {
            BY_POS.clear();
        }
        ACTIVE.remove();
    }

    public record Snapshot(
            long seenFrames, long mismatchCount, double minExpectedDy, double maxExpectedDy
    ) {
        public boolean exactMatrixGreen() {
            return seenFrames > 0L && mismatchCount == 0L;
        }
    }

    private record Scope(BlockPos pos, Matrix4f initial, double expectedDy) {
    }

    private static final class MutableStats {
        private long seenFrames;
        private long mismatchCount;
        private double minExpectedDy = Double.POSITIVE_INFINITY;
        private double maxExpectedDy = Double.NEGATIVE_INFINITY;
    }
}
