package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.management.ManagementFactory;

/**
 * PERF hygiene gate for the frozen-store read on the RENDER path (render-path fix-round F1; sibling of
 * {@link FrozenStoreCollisionAllocationGateTest}, which gates the same store on the entity-COLLISION
 * path, and of {@link ModelStaleSentinelAllocationGateTest}).
 *
 * <p>WHAT THIS GUARDS. Every Fabric block-state model in the game is wrapped by
 * {@code OffsetBlockStateModel}, so its {@code emitQuads} runs for every non-air block during chunk
 * -section meshing, on the mesh worker threads. Each of those blocks resolves its own frozen dy, then
 * up to six neighbour dys for the step-seam probe, then up to six more for the offset-aware cull test —
 * roughly a dozen {@code SlabSupport.getYOffset} calls per block per section compile, each of which,
 * with {@code FROZEN_DY_ENABLED} (shipped default ON), lands in
 * {@code SlabAnchorAttachment.storedPlacementDy} → {@code rawPlacementDyFact}. Ordinary terrain has no
 * stored fact, so the ABSENT answer is what that path returns for the overwhelming majority of those
 * calls. It used to allocate a fresh {@code PlacementDyFact} record every single time. This gate makes
 * that regression a deterministic red instead of a "the game got laggy again" report — the class of bug
 * this project has shipped to users twice.
 *
 * <p>ASSERTION SHAPE: DELTA, not absolute. The read legitimately does one {@code getChunk} plus one
 * attachment lookup, and this JVM's cost for those is not something a gametest should hard-code. The
 * baseline lane is {@link SlabAnchorAttachment#anyStoredOwnerOverflowsInto}, the already-shipped
 * MAJOR-2 probe that performs exactly that same chunk + attachment work and is structurally
 * allocation-free. A per-call record costs >= 16 B, i.e. >= 3.2 MB over the measured epoch — more than
 * triple the slack.
 *
 * <p>ESCAPE-ANALYSIS HARDENING: every lane sinks its result into a {@code volatile} field, so all five
 * are structurally identical (one call + one volatile store) and the JIT cannot scalar-replace away an
 * allocation the gate exists to see. A false green here is worse than no gate.
 *
 * <p><b>WHY THERE ARE FIVE LANES.</b> The first revision of this gate compared {@code getYOffset}
 * directly against the bare chunk probe and measured a clean, reproducible <b>24 bytes per call</b>.
 * That was never the frozen store — the {@code storedDy} and {@code backingFact} lanes measure the
 * entire F1 surface at zero. It was {@link CompatHooks#shouldSkipOffset}, which {@code getYOffset}
 * calls once before reaching the frozen branch: in the GAMETEST JVM ONLY,
 * {@code TerrainSlabsCompat.isLoaded()} was instrumented by a cancellable RETURN {@code @Inject},
 * and such an injector makes Mixin allocate one {@code CallbackInfoReturnable} per invocation
 * (12-byte header + two compressed-oop refs + two booleans = 22 B, padded to 24 B — exactly the
 * figure observed). That injector is now a MixinExtras {@code @ModifyReturnValue}, which passes the
 * primitive straight through and allocates nothing, so every lane here measures zero and NO lane
 * carries an allowance. Keep it that way: the {@code compatGate} lane exists so that if harness
 * instrumentation ever reappears on this path, the failure names it instead of being absorbed.
 */
public final class FrozenStoreRenderReadAllocationGateTest {

    private static final int WARMUP_CALLS = 50_000;
    private static final int MEASURED_CALLS = 200_000;
    // 1 MiB, matching the collision-path sibling: measured run-to-run JIT/GC baseline variance across
    // suite configurations is ~1.2 MB, which dwarfs a tighter bound and makes pass/fail noise. A real
    // per-call regression is >= 3.2 MB over this epoch, still 3x the slack.
    private static final long SLACK_BYTES = 1024 * 1024;
    /** No harness allowance: the compat gate's injector no longer allocates. See the class javadoc. */
    private static final long HARNESS_ALLOWANCE = 0L;

    // Volatile sinks: the results must genuinely escape, or scalar replacement hides a real allocation.
    private static volatile SlabAnchorAttachment.PlacementDyFact factSink;
    private static volatile double dySink;
    private static volatile boolean flagSink;

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

    /** Baseline: the same getChunk + getAttached work the measured lanes do, no allocation by construction. */
    private static long measureChunkProbe(
            com.sun.management.ThreadMXBean bean, ServerLevel w, BlockPos pos, BlockState state) {
        for (int i = 0; i < WARMUP_CALLS; i++) {
            flagSink = SlabAnchorAttachment.anyStoredOwnerOverflowsInto(w, pos, 1, 1);
        }
        long before = bean.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < MEASURED_CALLS; i++) {
            flagSink = SlabAnchorAttachment.anyStoredOwnerOverflowsInto(w, pos, 1, 1);
        }
        return bean.getCurrentThreadAllocatedBytes() - before;
    }

    /** Lane: the authoritative backing read itself, on a cell with no stored fact. */
    private static long measureBackingFact(
            com.sun.management.ThreadMXBean bean, ServerLevel w, BlockPos pos, BlockState state) {
        for (int i = 0; i < WARMUP_CALLS; i++) {
            factSink = SlabAnchorAttachment.rawPlacementDyFact(w, pos);
        }
        long before = bean.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < MEASURED_CALLS; i++) {
            factSink = SlabAnchorAttachment.rawPlacementDyFact(w, pos);
        }
        return bean.getCurrentThreadAllocatedBytes() - before;
    }

    /**
     * Lane: the ENTIRE F1 surface — the exact call {@code getYOffset}'s frozen branch makes, including
     * the client-view branch test and the fact-to-double unwrap. Pinned with no allowance whatsoever.
     */
    private static long measureStoredPlacementDy(
            com.sun.management.ThreadMXBean bean, ServerLevel w, BlockPos pos, BlockState state) {
        for (int i = 0; i < WARMUP_CALLS; i++) {
            dySink = SlabAnchorAttachment.storedPlacementDy(w, pos);
        }
        long before = bean.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < MEASURED_CALLS; i++) {
            dySink = SlabAnchorAttachment.storedPlacementDy(w, pos);
        }
        return bean.getCurrentThreadAllocatedBytes() - before;
    }

    /** Lane: the compat gate in isolation — the evidence lane that localises the harness artifact. */
    private static long measureCompatGate(
            com.sun.management.ThreadMXBean bean, ServerLevel w, BlockPos pos, BlockState state) {
        for (int i = 0; i < WARMUP_CALLS; i++) {
            flagSink = CompatHooks.shouldSkipOffset(state);
        }
        long before = bean.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < MEASURED_CALLS; i++) {
            flagSink = CompatHooks.shouldSkipOffset(state);
        }
        return bean.getCurrentThreadAllocatedBytes() - before;
    }

    /** Lane: the whole chain the mesher actually walks, frozen-ON. */
    private static long measureFrozenModelDy(
            com.sun.management.ThreadMXBean bean, ServerLevel w, BlockPos pos, BlockState state) {
        for (int i = 0; i < WARMUP_CALLS; i++) {
            dySink = SlabSupport.getYOffset(w, pos, state);
        }
        long before = bean.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < MEASURED_CALLS; i++) {
            dySink = SlabSupport.getYOffset(w, pos, state);
        }
        return bean.getCurrentThreadAllocatedBytes() - before;
    }

    /**
     * The common case the render read must not tax: an ordinary block with NO stored placement fact,
     * frozen-ON. The backing read, the full frozen-store read, and the whole {@code getYOffset} chain
     * must each cost the same as the bare chunk + attachment probe (the last one modulo the documented
     * gametest-harness artifact).
     *
     * <p>The subject is deliberately placed with {@code setBlock} rather than the real {@code useOn}
     * path: a real placement WRITES a stored fact, and the absent-fact answer is precisely what this
     * gate exists to keep free. The premise is asserted below rather than assumed.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void frozenRenderReadOfAnUnstoredCellAllocatesNothingOverTheChunkProbe(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        w.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        BlockState state = w.getBlockState(pos);
        if (state.isAir()) {
            throw helper.assertionException(helper.relativePos(pos),
                    "premise: the subject cell must hold a non-air block");
        }
        if (SlabAnchorAttachment.rawPlacementDyFact(w, pos).present()) {
            throw helper.assertionException(helper.relativePos(pos),
                    "premise: the subject cell must carry NO stored placement fact — this gate measures "
                            + "the absent-fact read that ordinary terrain takes");
        }

        var bean = allocationBean(helper);
        boolean prev = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        long chunkProbeBytes = Long.MAX_VALUE;
        long backingFactBytes = Long.MAX_VALUE;
        long storedDyBytes = Long.MAX_VALUE;
        long compatGateBytes = Long.MAX_VALUE;
        long modelDyBytes = Long.MAX_VALUE;
        try {
            // The gametest JVM pins the flag OFF (build.gradle) but the SHIPPED default is ON, and the
            // frozen branch is the render path being gated — so force it ON for the measurement.
            SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
            if (SlabSupport.getYOffset(w, pos, state) != 0.0d) {
                throw helper.assertionException(helper.relativePos(pos),
                        "premise: a cell with no stored fact must resolve stable-flat 0.0 through the "
                                + "frozen branch — the measured lane is not reading what it claims");
            }
            if (!Double.isNaN(SlabAnchorAttachment.storedPlacementDy(w, pos))) {
                throw helper.assertionException(helper.relativePos(pos),
                        "premise: the frozen store must report NaN (no stored height) for this cell");
            }
            // Best-of-3 epochs, INTERLEAVED (the collision sibling's amendment A1, take 2): a single
            // epoch set is noise — JIT recompilation and GC land between measurements. Comparing
            // per-lane MINIMA filters that structurally, and a real per-call cost survives a minimum
            // by construction.
            for (int epoch = 0; epoch < 3; epoch++) {
                chunkProbeBytes = Math.min(chunkProbeBytes, measureChunkProbe(bean, w, pos, state));
                backingFactBytes = Math.min(backingFactBytes, measureBackingFact(bean, w, pos, state));
                storedDyBytes = Math.min(storedDyBytes, measureStoredPlacementDy(bean, w, pos, state));
                compatGateBytes = Math.min(compatGateBytes, measureCompatGate(bean, w, pos, state));
                modelDyBytes = Math.min(modelDyBytes, measureFrozenModelDy(bean, w, pos, state));
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = prev;
        }

        // Printed on every run so the accounting is auditable without re-deriving it: getYOffset should
        // equal chunkProbe + compatGate, and the two F1 lanes should each equal chunkProbe.
        com.slabbed.Slabbed.LOGGER.info(
                "PERF-GATE | frozen store render read over {} calls: chunkProbe={} B, backingFact={} B, "
                        + "storedDy={} B, compatGate={} B (gametest-only mixin CallbackInfoReturnable), "
                        + "getYOffset={} B",
                MEASURED_CALLS, chunkProbeBytes, backingFactBytes, storedDyBytes, compatGateBytes,
                modelDyBytes);

        if (backingFactBytes > chunkProbeBytes + SLACK_BYTES) {
            throw helper.assertionException(helper.relativePos(pos),
                    "PERF (render-path fix-round F1): the absent-fact backing read allocated "
                            + backingFactBytes + " bytes vs the bare chunk-probe baseline " + chunkProbeBytes
                            + " over " + MEASURED_CALLS + " calls (slack " + SLACK_BYTES + ") — a per-call "
                            + "PlacementDyFact (or boxing) crept back into the mesh-thread read path");
        }
        if (storedDyBytes > chunkProbeBytes + SLACK_BYTES) {
            throw helper.assertionException(helper.relativePos(pos),
                    "PERF (render-path fix-round F1): storedPlacementDy — the whole frozen-store read the "
                            + "chunk mesher walks ~13x per block per section — allocated " + storedDyBytes
                            + " bytes vs the bare chunk-probe baseline " + chunkProbeBytes + " over "
                            + MEASURED_CALLS + " calls (slack " + SLACK_BYTES + ")");
        }
        if (compatGateBytes > HARNESS_ALLOWANCE + SLACK_BYTES) {
            throw helper.assertionException(helper.relativePos(pos),
                    "PERF: CompatHooks.shouldSkipOffset allocated " + compatGateBytes + " bytes over "
                            + MEASURED_CALLS + " calls — more than the one-object-per-call ceiling ("
                            + HARNESS_ALLOWANCE + ") that the gametest-only TerrainSlabsCompat isLoaded() "
                            + "injector accounts for. Either that injector changed or the compat gate "
                            + "itself started allocating; the getYOffset allowance below is no longer "
                            + "justified and must not simply be raised");
        }
        if (modelDyBytes > chunkProbeBytes + HARNESS_ALLOWANCE + SLACK_BYTES) {
            throw helper.assertionException(helper.relativePos(pos),
                    "PERF (render-path fix-round F1): the frozen-ON getYOffset read allocated "
                            + modelDyBytes + " bytes vs chunk-probe baseline " + chunkProbeBytes
                            + " + gametest-harness allowance " + HARNESS_ALLOWANCE + " over "
                            + MEASURED_CALLS + " calls (slack " + SLACK_BYTES + "). The measured compat "
                            + "gate was " + compatGateBytes + " B; anything beyond that is real "
                            + "allocation inside the frozen branch itself");
        }
        helper.succeed();
    }
}
