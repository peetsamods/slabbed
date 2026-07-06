package com.slabbed.test;

import com.slabbed.util.SlabModelStaleSentinel;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Headless contract suite for the MODEL_STALE sentinel's RULE ENGINE ({@link SlabModelStaleSentinel}) —
 * arming, baselines, the divergence rule, the absence rule, persistence windows, latching, suppression,
 * eviction, and the session gate. These drive the REAL bookkeeping (the same {@code armPlacement}/
 * {@code recordBake}/{@code samplePass} calls production uses), with bake events injected synthetically:
 * gametest runs no client renderer, so the one thing this suite CANNOT prove is that
 * {@code OffsetBlockStateModel.emitQuads} feeds real bakes — that is exactly lesson S7's blind spot, and
 * it is covered by the live self-check instead. Everything else about the instrument is pinned here so
 * a red row from a live session can be trusted as a verdict, not vibes (the /slabdy double-offset
 * incident is why an evidence tool ships only with tests of its own readouts).
 *
 * <p>The absence-rule scenes use the floor-torch lane (torch standing on a BOTTOM slab reads dy=-0.5 via
 * {@code floorTorchBottomSlabSupportDy}, already pinned by {@code FloorTorchSupportDyTest}) because it is
 * a pure geometric read of the support below — a support swap changes live dy with no anchor calls, which
 * is precisely the "live dy moved, nothing re-baked" shape the rule exists for. Each scene asserts its
 * own dy premise before judging (lesson S10: assert the scene, not assumptions).
 */
public final class ModelStaleSentinelContractTest {

    private static final double EPS = 1.0e-6;
    private static final long T0 = 1_000_000L;

    private static List<LinkedHashMap<String, String>> freshSentinel() {
        SlabModelStaleSentinel.resetCold();
        SlabModelStaleSentinel.testSessionOverride = true;
        return new ArrayList<>();
    }

    private static void teardown() {
        SlabModelStaleSentinel.testSessionOverride = false;
        SlabModelStaleSentinel.resetCold();
    }

    private static void pass(GameTestHelper helper, long tick, List<LinkedHashMap<String, String>> sink) {
        SlabModelStaleSentinel.samplePass(helper.getLevel(), tick, pos -> true, sink::add);
    }

    private static double liveDy(ServerLevel w, BlockPos abs) {
        return SlabSupport.getYOffset(w, abs, w.getBlockState(abs));
    }

    // ── divergence rule ─────────────────────────────────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void divergentBakeRedsAfterPersistenceWindowAndNotBefore(GameTestHelper helper) {
        List<LinkedHashMap<String, String>> rows = freshSentinel();
        try {
            ServerLevel w = helper.getLevel();
            BlockPos subject = helper.absolutePos(new BlockPos(2, 2, 2));
            w.setBlock(subject, Blocks.STONE.defaultBlockState(), 2);
            if (Math.abs(liveDy(w, subject)) > EPS) {
                throw helper.assertionException("scene premise: plain stone must read live dy 0.0");
            }
            SlabModelStaleSentinel.armForTest(w, subject, SlabModelStaleSentinel.REASON_PLACEMENT, T0);
            // The I3 shape: the mesh baked a value the logic no longer agrees with, and no re-bake comes.
            SlabModelStaleSentinel.recordBake(subject, -0.5f);

            pass(helper, T0 + 20, rows);  // mismatch sample #1 — window opens here
            pass(helper, T0 + 40, rows);  // sample #2 — but only 20 ticks elapsed
            if (!rows.isEmpty()) {
                throw helper.assertionException("red fired before RED_PERSIST_TICKS elapsed — persistence window broken");
            }
            pass(helper, T0 + 20 + SlabModelStaleSentinel.RED_PERSIST_TICKS, rows);
            if (rows.size() != 1) {
                throw helper.assertionException("expected exactly 1 red row after >=2 samples over >=RED_PERSIST_TICKS, got " + rows.size());
            }
            LinkedHashMap<String, String> row = rows.get(0);
            if (!SlabModelStaleSentinel.KIND_DIVERGENT.equals(row.get("kind"))
                    || !row.get("bakedDy").startsWith("-0.5")
                    || !row.get("liveDy").startsWith("0.0")) {
                throw helper.assertionException("divergent row fields wrong: " + row);
            }
            helper.succeed();
        } finally {
            teardown();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void redLatchesOncePerArming(GameTestHelper helper) {
        List<LinkedHashMap<String, String>> rows = freshSentinel();
        try {
            ServerLevel w = helper.getLevel();
            BlockPos subject = helper.absolutePos(new BlockPos(2, 2, 2));
            w.setBlock(subject, Blocks.STONE.defaultBlockState(), 2);
            SlabModelStaleSentinel.armForTest(w, subject, SlabModelStaleSentinel.REASON_PLACEMENT, T0);
            SlabModelStaleSentinel.recordBake(subject, -0.5f);
            for (long t = T0 + 20; t <= T0 + 400; t += 20) {
                pass(helper, t, rows);
            }
            if (rows.size() != 1) {
                throw helper.assertionException("red must latch once per arming; got " + rows.size() + " rows");
            }
            helper.succeed();
        } finally {
            teardown();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void healedDivergenceResetsTheStreakAndNeverReds(GameTestHelper helper) {
        List<LinkedHashMap<String, String>> rows = freshSentinel();
        try {
            ServerLevel w = helper.getLevel();
            BlockPos subject = helper.absolutePos(new BlockPos(2, 2, 2));
            w.setBlock(subject, Blocks.STONE.defaultBlockState(), 2);
            SlabModelStaleSentinel.armForTest(w, subject, SlabModelStaleSentinel.REASON_PLACEMENT, T0);
            SlabModelStaleSentinel.recordBake(subject, -0.5f);
            pass(helper, T0 + 20, rows);                       // mismatch sample #1 (window would open at +20)
            SlabModelStaleSentinel.recordBake(subject, 0.0f);  // the mesh re-baked correctly — healed
            pass(helper, T0 + 40, rows);                       // healed sample: MUST reset the streak
            // Goes stale AGAIN much later. Without the streak reset, this second mismatch would inherit
            // sample #1 + the +20 window and red immediately — an intermittent-flicker false positive.
            SlabModelStaleSentinel.recordBake(subject, -0.5f);
            pass(helper, T0 + 220, rows);                      // fresh mismatch sample #1 (fresh window @ +220)
            pass(helper, T0 + 240, rows);                      // sample #2, but only 20 ticks into the window
            if (!rows.isEmpty()) {
                throw helper.assertionException(
                        "a healed streak must NOT carry into a later divergence (intermittent flicker must open a fresh window); got " + rows);
            }
            pass(helper, T0 + 220 + SlabModelStaleSentinel.RED_PERSIST_TICKS, rows);
            if (rows.size() != 1) {
                throw helper.assertionException("the re-staleness must red after its OWN full window, got " + rows.size());
            }
            helper.succeed();
        } finally {
            teardown();
        }
    }

    // ── absence rule (the I1/I2/I5 family shape) ────────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void absentBaselineShiftRedsWithNoBake(GameTestHelper helper) {
        List<LinkedHashMap<String, String>> rows = freshSentinel();
        try {
            ServerLevel w = helper.getLevel();
            BlockPos support = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockPos torch = helper.absolutePos(new BlockPos(2, 3, 2));
            w.setBlock(support, Blocks.STONE.defaultBlockState(), 2);
            w.setBlock(torch, Blocks.TORCH.defaultBlockState(), 2);
            if (Math.abs(liveDy(w, torch)) > EPS) {
                throw helper.assertionException("scene premise: torch on stone must read live dy 0.0");
            }
            // Arm with the flush baseline (pre-change), as armPlacement would.
            SlabModelStaleSentinel.armForTest(w, torch, SlabModelStaleSentinel.REASON_NEIGHBORHOOD, T0);
            // The neighbor change that lowers the torch geometrically — and (the bug family) NOTHING
            // re-bakes: no recordBake call ever arrives.
            w.setBlock(support, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
            if (Math.abs(liveDy(w, torch) + 0.5) > EPS) {
                throw helper.assertionException("scene premise: torch on bottom slab must read live dy -0.5 (floor-torch lane)");
            }
            pass(helper, T0 + 20, rows);
            pass(helper, T0 + 40, rows);
            if (!rows.isEmpty()) {
                throw helper.assertionException("absence red fired before the persistence window");
            }
            pass(helper, T0 + 20 + SlabModelStaleSentinel.RED_PERSIST_TICKS, rows);
            if (rows.size() != 1) {
                throw helper.assertionException("expected exactly 1 absence red row, got " + rows.size());
            }
            LinkedHashMap<String, String> row = rows.get(0);
            if (!SlabModelStaleSentinel.KIND_ABSENT.equals(row.get("kind"))
                    || !"NO_BAKE".equals(row.get("bakedDy"))
                    || !row.get("baselineDy").startsWith("0.0")
                    || !row.get("liveDy").startsWith("-0.5")) {
                throw helper.assertionException("absent row fields wrong: " + row);
            }
            helper.succeed();
        } finally {
            teardown();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void armPlacementNeighborhoodCapturesBaselinesEndToEnd(GameTestHelper helper) {
        List<LinkedHashMap<String, String>> rows = freshSentinel();
        try {
            ServerLevel w = helper.getLevel();
            BlockPos support = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockPos torch = helper.absolutePos(new BlockPos(2, 3, 2));
            // The "placement" cell is within ARM_RADIUS of the torch — the real pre-mutation arming path
            // must sweep the torch up as a baselined neighborhood entry.
            BlockPos placeCell = helper.absolutePos(new BlockPos(3, 3, 2));
            w.setBlock(support, Blocks.STONE.defaultBlockState(), 2);
            w.setBlock(torch, Blocks.TORCH.defaultBlockState(), 2);
            SlabModelStaleSentinel.armPlacement(w, placeCell, T0);
            if (SlabModelStaleSentinel.armedCount() < 2) {
                throw helper.assertionException("armPlacement must arm the placed cell plus non-air neighbors; armed="
                        + SlabModelStaleSentinel.armedCount());
            }
            w.setBlock(support, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
            for (long t = T0 + 20; t <= T0 + 20 + SlabModelStaleSentinel.RED_PERSIST_TICKS; t += 20) {
                pass(helper, t, rows);
            }
            boolean torchRed = rows.stream().anyMatch(r ->
                    SlabModelStaleSentinel.KIND_ABSENT.equals(r.get("kind"))
                            && r.get("pos").equals(torch.getX() + " " + torch.getY() + " " + torch.getZ()));
            if (!torchRed) {
                throw helper.assertionException("the torch neighbor must red via the baseline captured by armPlacement; rows=" + rows);
            }
            helper.succeed();
        } finally {
            teardown();
        }
    }

    // ── green paths, immunities, bounds ─────────────────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bakeMatchingLiveStaysGreenAndTtlEvicts(GameTestHelper helper) {
        List<LinkedHashMap<String, String>> rows = freshSentinel();
        try {
            ServerLevel w = helper.getLevel();
            BlockPos subject = helper.absolutePos(new BlockPos(2, 2, 2));
            w.setBlock(subject, Blocks.STONE.defaultBlockState(), 2);
            SlabModelStaleSentinel.armForTest(w, subject, SlabModelStaleSentinel.REASON_PLACEMENT, T0);
            SlabModelStaleSentinel.recordBake(subject, 0.0f);
            for (long t = T0 + 20; t < T0 + SlabModelStaleSentinel.ARMED_TTL_TICKS; t += 100) {
                pass(helper, t, rows);
            }
            if (!rows.isEmpty()) {
                throw helper.assertionException("a bake matching live dy must never produce rows; got " + rows);
            }
            if (SlabModelStaleSentinel.armedCount() != 1) {
                throw helper.assertionException("entry must survive until TTL");
            }
            pass(helper, T0 + SlabModelStaleSentinel.ARMED_TTL_TICKS, rows);
            if (SlabModelStaleSentinel.armedCount() != 0) {
                throw helper.assertionException("entry must be evicted at TTL");
            }
            helper.succeed();
        } finally {
            teardown();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void blockChangeDisarmsSilently(GameTestHelper helper) {
        List<LinkedHashMap<String, String>> rows = freshSentinel();
        try {
            ServerLevel w = helper.getLevel();
            BlockPos subject = helper.absolutePos(new BlockPos(2, 2, 2));
            w.setBlock(subject, Blocks.STONE.defaultBlockState(), 2);
            SlabModelStaleSentinel.armForTest(w, subject, SlabModelStaleSentinel.REASON_NEIGHBORHOOD, T0);
            // Break/replace is NOT the mesh-staleness family (the recorder break-blindness false alarm):
            // the armed block changing must disarm silently, never red.
            w.setBlock(subject, Blocks.COBBLESTONE.defaultBlockState(), 2);
            pass(helper, T0 + 20, rows);
            if (SlabModelStaleSentinel.armedCount() != 0 || !rows.isEmpty()) {
                throw helper.assertionException("block change must disarm silently; armed="
                        + SlabModelStaleSentinel.armedCount() + " rows=" + rows);
            }
            helper.succeed();
        } finally {
            teardown();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void noBaselineNoBakeGoesYellowNotRed(GameTestHelper helper) {
        List<LinkedHashMap<String, String>> rows = freshSentinel();
        try {
            ServerLevel w = helper.getLevel();
            BlockPos subject = helper.absolutePos(new BlockPos(2, 2, 2));
            w.setBlock(subject, Blocks.STONE.defaultBlockState(), 2);
            SlabModelStaleSentinel.armForTest(w, subject, SlabModelStaleSentinel.REASON_PLACEMENT, T0);
            // No bake ever arrives, and there is no baseline: ambiguous — one YELLOW, never red.
            for (long t = T0 + 20; t <= T0 + 400; t += 20) {
                pass(helper, t, rows);
            }
            if (rows.size() != 1 || !SlabModelStaleSentinel.KIND_NO_BAKE_YELLOW.equals(rows.get(0).get("kind"))) {
                throw helper.assertionException("expected exactly 1 yellow row, got " + rows);
            }
            helper.succeed();
        } finally {
            teardown();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void armedCapBoundsTheSet(GameTestHelper helper) {
        freshSentinel();
        try {
            ServerLevel w = helper.getLevel();
            for (int i = 0; i < SlabModelStaleSentinel.ARMED_CAP + 88; i++) {
                BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2)).offset(i % 32, (i / 32) % 8, i / 256);
                SlabModelStaleSentinel.armForTest(w, pos, SlabModelStaleSentinel.REASON_PLACEMENT, T0);
            }
            if (SlabModelStaleSentinel.armedCount() != SlabModelStaleSentinel.ARMED_CAP) {
                throw helper.assertionException("armed set must be capped at " + SlabModelStaleSentinel.ARMED_CAP
                        + ", got " + SlabModelStaleSentinel.armedCount());
            }
            helper.succeed();
        } finally {
            teardown();
        }
    }

    // ── session gate + suppression ──────────────────────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sessionGateBlocksArming(GameTestHelper helper) {
        SlabModelStaleSentinel.resetCold();
        SlabModelStaleSentinel.testSessionOverride = false;
        try {
            ServerLevel w = helper.getLevel();
            // Recorder off + no override: arming must be a no-op (normal play pays one volatile read).
            SlabModelStaleSentinel.armPlacement(w, helper.absolutePos(new BlockPos(2, 2, 2)), T0);
            if (SlabModelStaleSentinel.armedCount() != 0) {
                throw helper.assertionException("armPlacement must no-op without an active recorder session");
            }
            helper.succeed();
        } finally {
            teardown();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void suppressionGraceDefersJudging(GameTestHelper helper) {
        List<LinkedHashMap<String, String>> rows = freshSentinel();
        try {
            ServerLevel w = helper.getLevel();
            BlockPos subject = helper.absolutePos(new BlockPos(2, 2, 2));
            w.setBlock(subject, Blocks.STONE.defaultBlockState(), 2);
            // Level change opens the grace window; join-time mesh churn must never be judged.
            SlabModelStaleSentinel.reset(T0);
            SlabModelStaleSentinel.armForTest(w, subject, SlabModelStaleSentinel.REASON_PLACEMENT, T0);
            SlabModelStaleSentinel.recordBake(subject, -0.5f);
            long grace = SlabModelStaleSentinel.LEVEL_CHANGE_GRACE_TICKS;
            for (long t = T0 + 20; t < T0 + grace; t += 20) {
                pass(helper, t, rows); // inside grace: no judging, no streak accumulation
            }
            // First judged mismatch can only happen at/after T0+grace; red needs its own full window.
            pass(helper, T0 + grace, rows);
            pass(helper, T0 + grace + 20, rows);
            if (!rows.isEmpty()) {
                throw helper.assertionException("grace window must defer judging (no red until a fresh window elapses after grace)");
            }
            pass(helper, T0 + grace + SlabModelStaleSentinel.RED_PERSIST_TICKS, rows);
            if (rows.size() != 1) {
                throw helper.assertionException("expected the red only after grace + a full persistence window, got " + rows.size());
            }
            helper.succeed();
        } finally {
            teardown();
        }
    }
}
