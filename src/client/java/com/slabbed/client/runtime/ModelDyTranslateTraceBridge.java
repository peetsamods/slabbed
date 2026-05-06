package com.slabbed.client.runtime;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;

public final class ModelDyTranslateTraceBridge {
    private static volatile BlockPos slabbed$tracePos = null;
    private static volatile Trace slabbed$lastTrace = Trace.missing();

    private ModelDyTranslateTraceBridge() {
    }

    public record Trace(
            boolean seen,
            String viewClass,
            String pos,
            String state,
            String lastMethod,
            int observedCalls,
            int appliedCalls,
            double totalAppliedDy,
            double lastDy
    ) {
        static Trace missing() {
            return new Trace(false, "none", "none", "none", "none", 0, 0, 0.0, 0.0);
        }
    }

    public static void reset(BlockPos pos) {
        slabbed$tracePos = pos == null ? null : pos.toImmutable();
        slabbed$lastTrace = Trace.missing();
    }

    public static Trace snapshot() {
        return slabbed$lastTrace;
    }

    public static void record(String method, BlockRenderView world, BlockPos pos, BlockState state, double dy) {
        BlockPos target = slabbed$tracePos;
        if (target == null || !target.equals(pos)) {
            return;
        }
        Trace prev = slabbed$lastTrace;
        boolean applied = dy != 0.0d;
        slabbed$lastTrace = new Trace(
                true,
                world.getClass().getName(),
                pos.toShortString(),
                state.toString(),
                method,
                prev.observedCalls() + 1,
                prev.appliedCalls() + (applied ? 1 : 0),
                prev.totalAppliedDy() + (applied ? dy : 0.0d),
                dy);
    }
}
