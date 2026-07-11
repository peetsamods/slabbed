package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.lang.management.ManagementFactory;

/**
 * PERF hygiene gate for the A-1 store-aware air-gap probe on the entity-COLLISION path (fix-round
 * MAJOR-2; sibling of {@link ModelStaleSentinelAllocationGateTest} — the machine-enforced version of the
 * rule this project has shipped lag by breaking TWICE). The GOES C2 A-1 fix makes
 * {@code SlabSupport.withHangingLoweredCollisionFromAbove} consult the frozen store when it meets an AIR
 * cell, under the now-default-ON {@code FROZEN_DY_ENABLED}. That probe runs for every entity collision
 * query on every block with air above — the most common shape in the world — so it must resolve the
 * chunk's PLACEMENT_DY map ONCE (early-outing when the chunk carries none) and add ~zero per-call
 * allocation over the frozen-OFF walk.
 *
 * <p>Assertion shape: DELTA, not absolute — the pre-existing walk allocates (a {@code pos.above(k)} per
 * iteration, predating C2), so the gate measures the frozen-OFF baseline and asserts the frozen-ON run
 * allocates no more than baseline + slack. A per-call chunk-map COPY or boxing in the probe would cost
 * ≥16 bytes/call ≈ ≥3 MB over the measured calls — three orders of magnitude above the slack.
 */
public final class FrozenStoreCollisionAllocationGateTest {

    private static final int WARMUP_CALLS = 50_000;
    private static final int MEASURED_CALLS = 200_000;
    // 1 MiB slack (reviewer amendment A1): run-to-run JIT/GC baseline variance measured ~1.2 MB across
    // suite configurations, which dwarfed the original 64 KiB and made pass/fail noise. A real
    // per-call regression costs >= 3 MB over the 200k-call epoch (>= 16 B/call), still 3x the slack.
    private static final long SLACK_BYTES = 1024 * 1024;

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

    private static long measureCollisionWalk(com.sun.management.ThreadMXBean bean, ServerLevel w, BlockPos pos) {
        VoxelShape own = Shapes.block();
        long blackhole = 0;
        for (int i = 0; i < WARMUP_CALLS; i++) {
            blackhole += SlabSupport.withHangingLoweredCollisionFromAbove(own, w, pos).isEmpty() ? 1 : 0;
        }
        long before = bean.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < MEASURED_CALLS; i++) {
            blackhole += SlabSupport.withHangingLoweredCollisionFromAbove(own, w, pos).isEmpty() ? 1 : 0;
        }
        long allocated = bean.getCurrentThreadAllocatedBytes() - before;
        if (blackhole == Long.MIN_VALUE) {
            throw new IllegalStateException("unreachable");
        }
        return allocated;
    }

    /**
     * The common case the probe must not tax: a plain block with AIR above, in a chunk with NO
     * PLACEMENT_DY entries, frozen-ON. The store-aware probe must early-out on the absent chunk map —
     * one getChunk + one getAttached, zero extra allocation versus the frozen-OFF walk.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void frozenOnCollisionProbeAddsNoAllocationOverFrozenOff(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        w.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        // Premise: air above (the probe's trigger cell) — the arena above the subject must be empty.
        if (!w.getBlockState(pos.above()).isAir()) {
            throw helper.assertionException(helper.relativePos(pos.above()),
                    "premise: the cell above the subject must be AIR");
        }
        var bean = allocationBean(helper);

        // Best-of-3 epochs per flag state, INTERLEAVED (reviewer amendment A1, take 2): a single
        // epoch pair is noise — JIT recompilation and GC land between the two measurements, and the
        // observed run-to-run swings (up to ~2 MB either direction across suite configurations)
        // dwarf any honest slack. Interleaving and comparing per-state MINIMA filters that
        // structurally: the minimum epoch is the closest to steady-state for each flag, and a REAL
        // per-call cost (>= 16 B/call = >= 3 MB/epoch) survives a minimum by construction.
        boolean prev = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        long offBytes = Long.MAX_VALUE;
        long onBytes = Long.MAX_VALUE;
        try {
            for (int epoch = 0; epoch < 3; epoch++) {
                SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
                offBytes = Math.min(offBytes, measureCollisionWalk(bean, w, pos));
                SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
                onBytes = Math.min(onBytes, measureCollisionWalk(bean, w, pos));
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = prev;
        }

        com.slabbed.Slabbed.LOGGER.info(
                "PERF-GATE | frozen store collision probe: frozenOff={} bytes, frozenOn={} bytes over {} calls",
                offBytes, onBytes, MEASURED_CALLS);
        if (onBytes > offBytes + SLACK_BYTES) {
            throw helper.assertionException(helper.relativePos(pos),
                    "PERF (fix-round MAJOR-2): the frozen-ON store-aware collision probe allocated "
                            + onBytes + " bytes vs frozen-OFF baseline " + offBytes + " over " + MEASURED_CALLS
                            + " calls (slack " + SLACK_BYTES + ") — per-call chunk-attachment churn crept "
                            + "back into the entity-collision path");
        }
        helper.succeed();
    }
}
