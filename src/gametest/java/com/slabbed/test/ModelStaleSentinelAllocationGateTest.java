package com.slabbed.test;

import com.slabbed.util.SlabModelStaleSentinel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

import java.lang.management.ManagementFactory;

/**
 * PERF hygiene gate for the MODEL_STALE sentinel's mesh-thread fast path — the machine-enforced version
 * of the rule this project has shipped lag by breaking TWICE (the 1.21.1 CNFE render storm and the
 * NeoForge gate-after-work bug): per-block diagnostic code must be gate-first and allocation-free when
 * idle. {@code shouldCapture()}/{@code isArmed()} run for EVERY block emission on chunk-mesh worker
 * threads whenever a recorder session is live, so "~0 bytes/call" here is a shipping constraint, not a
 * nicety. Any boxing or lambda capture on these paths costs ≥16 bytes/call ≈ ≥16 MB per million — the
 * 64 KB threshold is three orders of magnitude below the failure mode while far above JIT noise.
 *
 * <p>This is the first implemented instance of the allocation-regression gate the perf-hygiene plan
 * specified (audit finding F6: "specified, never implemented on either root").
 */
public final class ModelStaleSentinelAllocationGateTest {

    private static final int WARMUP_CALLS = 200_000;
    private static final int MEASURED_CALLS = 1_000_000;
    private static final long MAX_BYTES = 64 * 1024;

    private static com.sun.management.ThreadMXBean allocationBean(GameTestHelper helper) {
        var bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean hotspot)
                || !hotspot.isThreadAllocatedMemorySupported()) {
            // Fail LOUDLY: a silently-skipped gate reads as "covered" while covering nothing (S15).
            throw helper.assertionException(
                    "thread-allocation measurement unsupported on this JVM — the perf gate cannot run");
        }
        if (!hotspot.isThreadAllocatedMemoryEnabled()) {
            hotspot.setThreadAllocatedMemoryEnabled(true);
        }
        return hotspot;
    }

    private static long measureFastPath(com.sun.management.ThreadMXBean bean, long missKey) {
        long blackhole = 0;
        for (int i = 0; i < WARMUP_CALLS; i++) {
            blackhole += SlabModelStaleSentinel.shouldCapture() ? 1 : 0;
            blackhole += SlabModelStaleSentinel.isArmed(missKey) ? 1 : 0;
        }
        long before = bean.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < MEASURED_CALLS; i++) {
            if (SlabModelStaleSentinel.shouldCapture() && SlabModelStaleSentinel.isArmed(missKey)) {
                blackhole++;
            }
        }
        long allocated = bean.getCurrentThreadAllocatedBytes() - before;
        // Consume the blackhole so the JIT cannot eliminate the measured calls.
        if (blackhole == Long.MIN_VALUE) {
            throw new IllegalStateException("unreachable");
        }
        return allocated;
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void disarmedFastPathAllocatesNothing(GameTestHelper helper) {
        SlabModelStaleSentinel.resetCold();
        SlabModelStaleSentinel.testSessionOverride = false;
        try {
            long allocated = measureFastPath(allocationBean(helper), BlockPos.ZERO.asLong());
            if (allocated > MAX_BYTES) {
                throw helper.assertionException("disarmed fast path allocated " + allocated
                        + " bytes over " + MEASURED_CALLS + " calls (limit " + MAX_BYTES + ")");
            }
            helper.succeed();
        } finally {
            SlabModelStaleSentinel.testSessionOverride = false;
            SlabModelStaleSentinel.resetCold();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sessionOnEmptyArmedSetAllocatesNothing(GameTestHelper helper) {
        SlabModelStaleSentinel.resetCold();
        SlabModelStaleSentinel.testSessionOverride = true;
        try {
            long allocated = measureFastPath(allocationBean(helper), BlockPos.ZERO.asLong());
            if (allocated > MAX_BYTES) {
                throw helper.assertionException("session-on/armed-empty fast path allocated " + allocated
                        + " bytes over " + MEASURED_CALLS + " calls (limit " + MAX_BYTES + ")");
            }
            helper.succeed();
        } finally {
            SlabModelStaleSentinel.testSessionOverride = false;
            SlabModelStaleSentinel.resetCold();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void armedMissPathAllocatesNothing(GameTestHelper helper) {
        SlabModelStaleSentinel.resetCold();
        SlabModelStaleSentinel.testSessionOverride = true;
        try {
            // A realistic live session: many armed cells, and the emitted block is NOT one of them —
            // the overwhelmingly common case while capture is on. Binary search must stay allocation-free.
            for (int i = 0; i < 128; i++) {
                SlabModelStaleSentinel.armForTest(helper.getLevel(),
                        helper.absolutePos(new BlockPos(i % 16, 2 + (i / 64), (i / 16) % 4)),
                        SlabModelStaleSentinel.REASON_PLACEMENT, 1_000_000L);
            }
            long missKey = helper.absolutePos(new BlockPos(15, 15, 15)).asLong();
            if (SlabModelStaleSentinel.isArmed(missKey)) {
                throw helper.assertionException("probe key unexpectedly armed — pick a different miss key");
            }
            long allocated = measureFastPath(allocationBean(helper), missKey);
            if (allocated > MAX_BYTES) {
                throw helper.assertionException("armed-miss fast path allocated " + allocated
                        + " bytes over " + MEASURED_CALLS + " calls (limit " + MAX_BYTES + ")");
            }
            helper.succeed();
        } finally {
            SlabModelStaleSentinel.testSessionOverride = false;
            SlabModelStaleSentinel.resetCold();
        }
    }
}
