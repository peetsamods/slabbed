package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import java.lang.management.ManagementFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Rule 19 (RULES.md §19) enforcement automation, item 1: allocation-regression gate for the
 * per-block dy hot path.
 *
 * <p>The SAME lag bug class shipped twice (Fabric 1.21.1 per-block Class.forName CNFE storm;
 * NeoForge getQuads gate-after-work). Both incidents were per-call diagnostic work on a hot
 * path with all trace flags off — invisible to logging hygiene and dev testing. This test
 * would have caught both: it calls {@link SlabSupport#getYOffset} many times on a PLAIN
 * full block (plain stone on plain stone — deliberately NOT a slab lane, so functional
 * slab-column walks cannot false-RED the budget) with all trace flags off, and asserts a
 * per-call allocated-bytes budget via {@code com.sun.management.ThreadMXBean} plus a very
 * generous ns/call sanity budget.
 *
 * <p>Server-harness scope note: the quad-emission half of the Rule-19 design
 * (OffsetBlockStateModel.getQuads) is a CLIENT path and cannot execute under
 * runServerGameTest; it stays manual (release-jar Spark lane, RULES.md §19 item 3) until a
 * client harness exists on this branch.
 *
 * <p>Flag discipline: this test does NOT setProperty any flag (so class-load cached flags
 * stay exact, per the §19 cache-vs-live rule); it asserts the relevant dy-path trace flags
 * are absent from the launch before measuring.
 */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class SlabbedHotPathAllocationRegressionTest {

    /** Calls per measured pass; large enough to average out one-off lazy init. */
    private static final int CALLS = 10_000;

    /**
     * Allocated-bytes-per-call budget. Clean baseline measured on this branch (2026-07-02,
     * HEAD fed17a19, JDK 21 HotSpot): 144 B/call — functional BlockPos/walk allocation on
     * the plain-block path, printed by the [ALLOC_REGRESSION] line every run. Budget is
     * ~2.7x that baseline: tolerant of JIT/escape-analysis drift, but far below the failure
     * signatures this gate exists to catch (per-call exception construction is >500 B/call,
     * the getQuads registry-lookup + ~6 string allocs incident was in the hundreds B/call).
     * If this budget ever fails on an intentional FUNCTIONAL change, re-measure and re-set
     * it in the same commit with the new baseline recorded here — never widen it to smuggle
     * diagnostic work past the gate.
     */
    private static final long BYTES_PER_CALL_BUDGET = 384;

    /**
     * Generous ns/call sanity net (measured baseline ~1.7 us/call; wall-clock is noisy on
     * shared rigs, so this only catches egregious storms — the byte budget is the precise
     * signal; a CNFE storm's exception+stack-trace allocation trips it long before this).
     */
    private static final long NANOS_PER_CALL_BUDGET = 50_000;

    private static final String[] DY_PATH_TRACE_FLAGS = {
            "slabbed.bottomPersistentTrace",
            "slabbed.anchor.trace",
            "slabbed.disableStepCull"
    };

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void hotPathAllocationRegression(GameTestHelper ctx) {
        for (String flag : DY_PATH_TRACE_FLAGS) {
            ctx.assertTrue(System.getProperty(flag) == null,
                    "precondition: dy-path trace flag " + flag + " must be unset for the allocation gate");
        }

        ServerLevel world = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(1, 1, 1));
        BlockPos target = support.above();
        world.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(target, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockState state = world.getBlockState(target);

        java.lang.management.ThreadMXBean raw = ManagementFactory.getThreadMXBean();
        if (!(raw instanceof com.sun.management.ThreadMXBean bean)
                || !bean.isThreadAllocatedMemorySupported()) {
            // Non-HotSpot JVM without per-thread allocation counters: the gate cannot run.
            // Visible SKIP (never a silent pass) — the release-jar Spark lane still applies.
            System.out.println("[ALLOC_REGRESSION] result=SKIP reason=thread_allocated_bytes_unsupported jvm="
                    + System.getProperty("java.vm.name"));
            ctx.succeed();
            return;
        }
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        long tid = Thread.currentThread().threadId();

        // Warmup: JIT + lazy class init + first-call caches, excluded from the measurement.
        // 3x the measured pass so C2 (default ~10k invocation threshold) compiles BEFORE
        // measurement — otherwise the baseline drifts run-to-run with compile timing.
        double warmupSum = 0.0;
        for (int i = 0; i < 3 * CALLS; i++) {
            warmupSum += SlabSupport.getYOffset(world, target, state);
        }

        long bytesBefore = bean.getThreadAllocatedBytes(tid);
        long nanosBefore = System.nanoTime();
        // Accumulate into a sum both to defeat dead-code elimination and to pin behavior:
        // plain stone on plain stone must never be offset, so every call returns 0.0.
        double sum = 0.0;
        for (int i = 0; i < CALLS; i++) {
            sum += SlabSupport.getYOffset(world, target, state);
        }
        long nanosAfter = System.nanoTime();
        long bytesAfter = bean.getThreadAllocatedBytes(tid);

        long bytesPerCall = Math.max(0, bytesAfter - bytesBefore) / CALLS;
        long nanosPerCall = Math.max(0, nanosAfter - nanosBefore) / CALLS;

        System.out.println("[ALLOC_REGRESSION] result=MEASURED calls=" + CALLS
                + " bytesPerCall=" + bytesPerCall
                + " nanosPerCall=" + nanosPerCall
                + " budgetBytesPerCall=" + BYTES_PER_CALL_BUDGET
                + " budgetNanosPerCall=" + NANOS_PER_CALL_BUDGET
                + " dySum=" + sum
                + " warmupDySum=" + warmupSum);

        ctx.assertTrue(sum == 0.0 && warmupSum == 0.0,
                "plain stone on plain stone must have dy=0.0 on every call, got sum=" + sum);
        ctx.assertTrue(bytesPerCall <= BYTES_PER_CALL_BUDGET,
                "Rule 19 allocation regression: getYOffset allocated " + bytesPerCall
                        + " B/call on a plain block with traces off (budget " + BYTES_PER_CALL_BUDGET
                        + " B/call) — per-block diagnostic work is back on the hot path");
        ctx.assertTrue(nanosPerCall <= NANOS_PER_CALL_BUDGET,
                "Rule 19 time regression: getYOffset took " + nanosPerCall
                        + " ns/call on a plain block with traces off (budget " + NANOS_PER_CALL_BUDGET
                        + " ns/call)");
        ctx.succeed();
    }
}
