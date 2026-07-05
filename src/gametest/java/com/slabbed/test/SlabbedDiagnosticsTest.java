package com.slabbed.test;

import com.slabbed.dev.SlabbedDiagnostics;
import com.slabbed.dev.audit.LiveCursorIntentRecorder;
import com.slabbed.util.RuntimeDiagnostics;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Proves the {@link SlabbedDiagnostics} detectors actually fire — both the pure predicates
 * (fed the exact bug values) and an integration pass over a real fence-on-slab. Ported (adapted to
 * the 1.21.1 {@code net.minecraft.test.GameTest}/{@code templateName} gametest API) from the
 * 1.21.11 sibling's {@code SlabbedDiagnosticsTest}, 2026-07-05. This is the headlessly-verifiable
 * half of the enriched recorder; the live capture wiring (firing on crosshair-target change,
 * reading the client render trace) is live-only.
 *
 * <p>The one predicate whose signature differs from the sibling is {@code smooshRisk}, which takes
 * {@code (world, pos, state, dy)} here because 1.21.1's full-cube check
 * ({@code isOpaqueFullCube(world,pos)}) is context-sensitive — the pure tests feed it a real
 * world/pos.
 */
public final class SlabbedDiagnosticsTest {

    // ── pure predicate unit tests ────────────────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void triadMismatchDetectorFiresOnTheBugValues(TestContext ctx) {
        BlockState fence = Blocks.OAK_FENCE.getDefaultState();
        BlockState bottomSlab = Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState topSlab = Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
        BlockState bed = Blocks.WHITE_BED.getDefaultState();
        BlockState chain = Blocks.CHAIN.getDefaultState();
        BlockState lantern = Blocks.LANTERN.getDefaultState();

        // Base-0 block, outline followed the dy → no flag.
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(fence, -0.5, -0.5),
                "a fence whose outline followed the dy must NOT flag");
        // Base-0 block, outline pinned at grid while visual lowered → real bug (the raycast class).
        ctx.assertTrue(SlabbedDiagnostics.triadMismatch(fence, -0.5, 0.0),
                "a fence outline pinned at grid while visual lowered MUST flag (the raycast bug)");
        // A BOTTOM slab (base 0) is decidable and flags the bug value.
        ctx.assertTrue(SlabbedDiagnostics.triadMismatch(bottomSlab, -0.5, 0.0),
                "a bottom slab with outline at grid while visual lowered MUST flag");
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(bed, -0.5, -0.5),
                "a bed whose outline tracks the dy must NOT flag");
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(fence, -0.5, Double.NaN),
                "an empty outline must not produce a spurious mismatch");

        // A lowered TOP slab has outline base 0.5, so outlineMinY 0.0 at visualDy -0.5 is CORRECT,
        // not a mismatch. Top slabs and hanging decorations (chain, lantern base) are excluded.
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(topSlab, -0.5, 0.0),
                "a lowered TOP slab (base 0.5 -> outlineMinY 0.0) must NOT flag — recorded false positive");
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(chain, -0.5, -0.094),
                "a chain (nonzero outline base) must NOT flag — this was a false positive");
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(lantern, 0.5, 0.563),
                "a hanging lantern (nonzero base) must NOT flag");
        ctx.assertTrue(!SlabbedDiagnostics.triadMismatch(chain, -0.5, 0.0),
                "even an extreme chain value must not flag — chains are not decidable here");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void dodoDetectorFiresOnLoweredOpaqueCube(TestContext ctx) {
        ctx.assertTrue(SlabbedDiagnostics.dodoRisk(true, -0.5),
                "an opaque full cube at a nonzero dy is a DODO (see-through hole) risk");
        ctx.assertTrue(!SlabbedDiagnostics.dodoRisk(true, 0.0),
                "a flush opaque cube is fine");
        ctx.assertTrue(!SlabbedDiagnostics.dodoRisk(false, -0.5),
                "a non-opaque block lowered is normal, not a DODO");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void smooshDetectorFiresOnDoubleOffsetDecoration(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        // A decoration (lantern), a slab, and a full cube (stone), each placed so the full-cube
        // check has a real (world,pos) to resolve against.
        BlockPos lanternPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 2, 2);
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(4, 2, 2);
        BlockPos stonePos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(6, 2, 2);
        w.setBlockState(lanternPos, Blocks.LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(slabPos, Blocks.OAK_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(stonePos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockState lantern = w.getBlockState(lanternPos);
        BlockState oakSlab = w.getBlockState(slabPos);
        BlockState stone = w.getBlockState(stonePos);

        ctx.assertTrue(SlabbedDiagnostics.smooshRisk(w, lanternPos, lantern, -1.0),
                "a decoration lowered a FULL block is a smoosh (double-offset) risk");
        ctx.assertTrue(!SlabbedDiagnostics.smooshRisk(w, lanternPos, lantern, -0.5),
                "a decoration lowered a single half-step is normal");
        ctx.assertTrue(!SlabbedDiagnostics.smooshRisk(w, slabPos, oakSlab, -1.0),
                "a slab is not a smoosh subject (compound stacks are legitimate)");
        ctx.assertTrue(!SlabbedDiagnostics.smooshRisk(w, stonePos, stone, -1.0),
                "an opaque full cube is a DODO, not a smoosh (classified separately)");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void gapDetectorFiresOnChainLanternDyMismatch(TestContext ctx) {
        BlockState chain = Blocks.CHAIN.getDefaultState();
        BlockState lantern = Blocks.LANTERN.getDefaultState();
        BlockState stone = Blocks.STONE.getDefaultState();
        ctx.assertTrue(SlabbedDiagnostics.dyDiscontinuity(chain, lantern, -0.5, 0.0),
                "a chain and lantern at different dy is a visible vertical gap");
        ctx.assertTrue(!SlabbedDiagnostics.dyDiscontinuity(chain, lantern, -0.5, -0.5),
                "a chain and lantern at the SAME dy connect cleanly (no gap)");
        ctx.assertTrue(!SlabbedDiagnostics.dyDiscontinuity(chain, stone, -0.5, 0.0),
                "a chain next to a non-decoration is not a decoration-gap");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void modelMismatchDetectorFiresWhenModelDivergesFromVisual(TestContext ctx) {
        ctx.assertTrue(SlabbedDiagnostics.modelMismatch(-0.5, 0.0),
                "a model rendered at grid while visual is lowered must flag (the pre-fix fence render)");
        ctx.assertTrue(!SlabbedDiagnostics.modelMismatch(-0.5, -0.5),
                "model tracking visual is clean");
        ctx.assertTrue(!SlabbedDiagnostics.modelMismatch(-0.5, Double.NaN),
                "an unknown (server-side) model dy must not flag");
        ctx.complete();
    }

    // ── integration: a real fence-on-slab must analyze clean ─────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void realFenceOnSlabAnalyzesAsAConsistentLoweredTriad(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(slab, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos fence = slab.up();
        w.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

        SlabbedDiagnostics.Sample s = SlabbedDiagnostics.analyze(w, fence, w.getBlockState(fence));

        ctx.assertTrue(s.visualDy() < -1.0e-6,
                "fence on a vanilla slab must resolve a lowered dy, got " + SlabbedDiagnostics.format(s.visualDy()));
        ctx.assertTrue(!s.triadMismatch(),
                "the fence outline follows the dy (offset mixin), so no triad mismatch — got flags "
                        + s.flagSummary());
        ctx.assertTrue(!s.dodoRisk(), "a fence is not an opaque cube, so no DODO risk");
        ctx.assertTrue(!s.anySuspect(),
                "a correctly-lowered fence must analyze completely clean, got flags: " + s.flagSummary());
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void flatBlockAnalyzesClean(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        SlabbedDiagnostics.Sample s = SlabbedDiagnostics.analyze(w, pos, w.getBlockState(pos));
        ctx.assertTrue(Math.abs(s.visualDy()) < 1.0e-6, "flat stone must have dy 0");
        ctx.assertTrue(!s.anySuspect(), "flat stone on the ground must analyze clean, got: " + s.flagSummary());
        ctx.complete();
    }

    /**
     * Closes the Phase 7 loop: {@code RuntimeDiagnostics}'s reflective bridge into the dev-only
     * {@code SlabbedDiagnostics} + recorder must actually RESOLVE (not dangle). In this dev/test build
     * both classes are present, so {@code isVisualDiagnosticsAvailable()} must be true and
     * {@code analyzeVisualDiagnostic(...)} must return a real Sample whose flag summary matches a
     * direct {@code SlabbedDiagnostics.analyze} call — proving the always-shipped caller
     * ({@code SlabbedClient}'s per-tick hook) can reach {@code recordVisualDiagnostic} through this
     * bridge exactly as the 1.21.11 sibling's {@code maybeRecordTargetDiagnostic} does.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void runtimeDiagnosticsBridgeResolvesToTheDiagnosticsLayer(TestContext ctx) {
        ctx.assertTrue(RuntimeDiagnostics.isVisualDiagnosticsAvailable(),
                "in a dev/test build the SlabbedDiagnostics layer is present, so the reflective bridge "
                        + "must resolve (recordVisualDiagnostic is reachable, not dangling)");

        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(slab, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos fence = slab.up();
        w.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

        Object bridged = RuntimeDiagnostics.analyzeVisualDiagnostic(w, fence, w.getBlockState(fence), Double.NaN);
        ctx.assertTrue(bridged instanceof SlabbedDiagnostics.Sample,
                "the reflective analyze bridge must return a real SlabbedDiagnostics.Sample, got "
                        + (bridged == null ? "null" : bridged.getClass().getName()));

        SlabbedDiagnostics.Sample direct = SlabbedDiagnostics.analyze(w, fence, w.getBlockState(fence), Double.NaN);
        String bridgedFlags = RuntimeDiagnostics.visualDiagnosticFlagSummary(bridged);
        // A clean fence trips no flags, so the summary bridge returns null (its "no suspect" signal),
        // matching the direct sample's empty flag summary.
        ctx.assertTrue(!direct.anySuspect(), "a clean lowered fence must trip no flags");
        ctx.assertTrue(bridgedFlags == null,
                "flag-summary bridge must return null for a clean sample, got: " + bridgedFlags);
        ctx.complete();
    }

    /**
     * Perf-hygiene regression (cross-phase review, 2026-07-05): {@code isRecorderEnabled()} used to
     * re-resolve {@code LiveCursorIntentRecorder.isEnabled}'s {@link Method} handle via a fresh
     * {@code getMethod()} call on every invocation instead of caching it like every sibling
     * reflective forwarder in {@code RuntimeDiagnostics} does. This is a pure perf fix — the
     * returned boolean must be byte-for-byte identical before and after — so the proof here is
     * twofold: (1) repeated calls still track {@code LiveCursorIntentRecorder}'s real state
     * (including after a toggle), and (2) the backing {@code IS_ENABLED_METHOD} field is populated
     * exactly once and stays the SAME {@link Method} reference across calls, i.e. it is genuinely
     * cached rather than re-looked-up.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void isRecorderEnabledTracksLiveStateViaACachedMethodHandle(TestContext ctx) throws ReflectiveOperationException {
        boolean before = LiveCursorIntentRecorder.isEnabled();
        try {
            LiveCursorIntentRecorder.toggle();
            boolean toggled = LiveCursorIntentRecorder.isEnabled();
            ctx.assertTrue(RuntimeDiagnostics.isRecorderEnabled() == toggled,
                    "isRecorderEnabled() must reflect the recorder's real (toggled) state, not a stale read");
            ctx.assertTrue(RuntimeDiagnostics.isRecorderEnabled() == toggled,
                    "a second call must return the identical value as the first (no per-call drift)");

            LiveCursorIntentRecorder.toggle();
            boolean toggledBack = LiveCursorIntentRecorder.isEnabled();
            ctx.assertTrue(toggledBack == before, "toggle must be a pure flip, restoring the original state");
            ctx.assertTrue(RuntimeDiagnostics.isRecorderEnabled() == toggledBack,
                    "isRecorderEnabled() must track the state change back too, proving the cached handle "
                            + "still dispatches live (it is cached, not stale/frozen)");
        } finally {
            // Defensive: guarantee we leave the recorder exactly as we found it even if an assertion above throws.
            if (LiveCursorIntentRecorder.isEnabled() != before) {
                LiveCursorIntentRecorder.toggle();
            }
        }

        // Reach into RuntimeDiagnostics' cached field via reflection (test-only introspection) and
        // confirm it is populated once and is the SAME Method reference across repeated accesses —
        // the actual "resolved once, not per-call" claim this fix makes.
        Field methodField = RuntimeDiagnostics.class.getDeclaredField("IS_ENABLED_METHOD");
        methodField.setAccessible(true);
        Object firstRead = methodField.get(null);
        ctx.assertTrue(firstRead instanceof Method,
                "IS_ENABLED_METHOD must be populated in a dev/test build where the recorder class is present");
        RuntimeDiagnostics.isRecorderEnabled();
        RuntimeDiagnostics.isRecorderEnabled();
        Object secondRead = methodField.get(null);
        ctx.assertTrue(firstRead == secondRead,
                "IS_ENABLED_METHOD must be the SAME cached Method instance across calls, not re-resolved per call");
        ctx.complete();
    }
}
