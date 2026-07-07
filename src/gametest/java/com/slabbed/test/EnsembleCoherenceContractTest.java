package com.slabbed.test;

import com.slabbed.util.SlabEnsembleCoherence;
import com.slabbed.util.SlabEnsembleCoherence.Kind;
import com.slabbed.util.SlabEnsembleCoherence.Verdict;
import com.slabbed.util.SlabModelStaleSentinel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Phase 1 contract suite for ENSEMBLE_COHERENCE_DESIGN.md — the classifier that makes the video-proven
 * row-silent class (mixed-dy neighbors clipping/gapping/occluding) visible as recorder rows. Truth
 * table over real states + injected dys, the by-design-gap immunity (a bottom slab under a block must
 * NEVER be flagged), the TS failure-mode-4 guard, and the end-to-end sentinel wiring (one verdict per
 * placement, once).
 */
public final class EnsembleCoherenceContractTest {

    private static void expect(GameTestHelper helper, Verdict got, Kind kind, double depth, String label) {
        if (got.kind() != kind || Math.abs(got.depth() - depth) > 1.0e-6) {
            throw helper.assertionException(label + ": expected " + kind + "/" + depth
                    + ", got " + got.kind() + "/" + got.depth());
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chestOverFlushHopperInterpenetratesByHalf(GameTestHelper helper) {
        // The video's t=130s scene: flush FROZEN-FLAT hopper, ANCHORED -0.5 chest above.
        ServerLevel w = helper.getLevel();
        BlockPos lower = helper.absolutePos(new BlockPos(2, 2, 2));
        w.setBlock(lower, Blocks.HOPPER.defaultBlockState(), 2);
        w.setBlock(lower.above(), Blocks.CHEST.defaultBlockState(), 2);
        expect(helper, SlabEnsembleCoherence.classifyVerticalPair(w, lower, 0.0, -0.5),
                Kind.INTERPENETRATION, 0.5, "chest sinks into hopper");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deepUnderShallowLeavesGapAndUniformIsCoherent(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos lower = helper.absolutePos(new BlockPos(2, 2, 2));
        w.setBlock(lower, Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(lower.above(), Blocks.STONE.defaultBlockState(), 2);
        expect(helper, SlabEnsembleCoherence.classifyVerticalPair(w, lower, -1.0, -0.5),
                Kind.GAP, 0.5, "-1.0 under -0.5 gaps");
        expect(helper, SlabEnsembleCoherence.classifyVerticalPair(w, lower, -0.5, -0.5),
                Kind.COHERENT, 0.0, "uniform lowering is coherent");
        expect(helper, SlabEnsembleCoherence.classifyVerticalPair(w, lower, 0.0, 0.0),
                Kind.COHERENT, 0.0, "flush stack is coherent");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void byDesignVanillaGapsAreNeverFlagged(GameTestHelper helper) {
        // A BOTTOM slab under a block has a half-cell vanilla gap BY DESIGN — dys must not matter.
        ServerLevel w = helper.getLevel();
        BlockPos lower = helper.absolutePos(new BlockPos(2, 2, 2));
        w.setBlock(lower, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(lower.above(), Blocks.STONE.defaultBlockState(), 2);
        expect(helper, SlabEnsembleCoherence.classifyVerticalPair(w, lower, 0.0, -0.5),
                Kind.COHERENT, 0.0, "bottom-slab vanilla gap is not a clash");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void occludedOccupancySingleBlockRule(GameTestHelper helper) {
        // The t=98s trapdoor case: a bottom slab at dy=-0.5 renders entirely below its own cell floor.
        ServerLevel w = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        w.setBlock(pos, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        if (!SlabEnsembleCoherence.isOccludedOccupancy(w, pos, -0.5)) {
            throw helper.assertionException("bottom slab at -0.5 occupies zero visible volume in its cell");
        }
        if (SlabEnsembleCoherence.isOccludedOccupancy(w, pos, 0.0)) {
            throw helper.assertionException("flush slab is visibly present");
        }
        w.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        if (SlabEnsembleCoherence.isOccludedOccupancy(w, pos, -0.5)) {
            throw helper.assertionException("a -0.5 full cube still shows half a block in its cell");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabsBlocksAreGuarded(GameTestHelper helper) {
        // Failure-mode-4: TS-owned blocks are outside Slabbed's offset authority — never classified.
        Block tsSlab = BuiltInRegistries.BLOCK.getValue(
                Identifier.fromNamespaceAndPath("terrain_slabs", "geometric_remesh_scheduler_test_slab"));
        if (tsSlab == Blocks.AIR) {
            throw helper.assertionException("TS fixture slab missing from registry (entrypoint not run?)");
        }
        // The documented seam (shouldSkipOffset has none — HANDOFF test-seam note): force the TS-owned
        // verdict for the terrain_slabs namespace, the same pattern every *TerrainSlabsGuardTest uses.
        com.slabbed.compat.CompatHooks.shouldSkipSlabSupportTestOverride = s ->
                BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals("terrain_slabs");
        try {
            ServerLevel w = helper.getLevel();
            BlockPos lower = helper.absolutePos(new BlockPos(2, 2, 2));
            w.setBlock(lower, Blocks.STONE.defaultBlockState(), 2);
            w.setBlock(lower.above(), tsSlab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 2);
            expect(helper, SlabEnsembleCoherence.classifyVerticalPair(w, lower, 0.0, -0.5),
                    Kind.COHERENT, 0.0, "TS upper block must be skipped even with clashing dys");
            helper.succeed();
        } finally {
            com.slabbed.compat.CompatHooks.shouldSkipSlabSupportTestOverride = null;
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void reallyLoweredBlockShapesAreNotDoubleCounted(GameTestHelper helper) {
        // TEST (5) live regression, caught by the gate's own first outing: getShape is the OUTLINE leg
        // of the triad — ALREADY dy-offset for a genuinely lowered block — so classifier math that adds
        // the caller's dy on top double-applies it (the /slabdy 0bf59d56 disease, F9). 30 of 76 live
        // rows were false OCCLUDED verdicts on -0.5 full cubes. Repro needs a REAL lowered block
        // (anchored, live dy -0.5), not injected dys over flush geometry — which is exactly why the
        // original suite stayed green while live lied.
        ServerLevel w = helper.getLevel();
        BlockPos support = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos log = support.above();
        w.setBlock(support, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(log, Blocks.OAK_LOG.defaultBlockState(), 2);
        com.slabbed.anchor.SlabAnchorAttachment.addAnchor(w, log, w.getBlockState(log));
        com.slabbed.anchor.SlabAnchorAttachment.freezeLoweredOnPlace(w, log, w.getBlockState(log));
        double liveDy = com.slabbed.util.SlabSupport.getYOffset(w, log, w.getBlockState(log));
        if (Math.abs(liveDy + 0.5) > 1.0e-6) {
            throw helper.assertionException("premise: log on a bottom slab must be genuinely lowered -0.5, got " + liveDy);
        }
        if (SlabEnsembleCoherence.isOccludedOccupancy(w, log, liveDy)) {
            throw helper.assertionException(
                    "a genuinely lowered -0.5 FULL CUBE still shows half a block in its cell — flagging it occluded means dy was double-counted (offset outline shape + dy again)");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void breakNeighborhoodClassifiesPairsOnceAndNeverYellows(GameTestHelper helper) {
        // Phase 1.5: breaks reshuffle neighbor dys; the break neighborhood must be ensemble-classified
        // (each vertical pair exactly once, via its lower member) and must NEVER produce NO_BAKE
        // yellows (an unchanged far neighbor legitimately never re-bakes).
        List<LinkedHashMap<String, String>> rows = new ArrayList<>();
        SlabModelStaleSentinel.resetCold();
        SlabModelStaleSentinel.resetLiveDyPolicyForTest();
        SlabModelStaleSentinel.testSessionOverride = true;
        try {
            ServerLevel w = helper.getLevel();
            BlockPos hopper = helper.absolutePos(new BlockPos(2, 2, 2));
            w.setBlock(hopper, Blocks.HOPPER.defaultBlockState(), 2);
            w.setBlock(hopper.above(), Blocks.CHEST.defaultBlockState(), 2);
            SlabModelStaleSentinel.setLiveDyPolicy((level, pos, state) ->
                    state.getBlock() == Blocks.CHEST ? -0.5 : 0.0);
            // The "break" happened one cell east — both stack members fall inside its radius-2 box.
            SlabModelStaleSentinel.armBreakNeighborhood(w, hopper.east(), 1_000_000L);
            for (long t = 1_000_020L; t <= 1_000_020L + 3L * SlabModelStaleSentinel.RED_PERSIST_TICKS; t += 20) {
                SlabModelStaleSentinel.samplePass(w, t, pos -> true, rows::add);
            }
            long clashes = rows.stream().filter(r -> r.getOrDefault("kind", "").startsWith("ENSEMBLE_")).count();
            long yellows = rows.stream().filter(r ->
                    SlabModelStaleSentinel.KIND_NO_BAKE_YELLOW.equals(r.get("kind"))).count();
            if (clashes != 1) {
                throw helper.assertionException("break neighborhood must judge the clashing pair exactly ONCE, got "
                        + clashes + " in " + rows);
            }
            if (yellows != 0) {
                throw helper.assertionException("break-armed entries must never yellow, got " + yellows);
            }
            helper.succeed();
        } finally {
            SlabModelStaleSentinel.testSessionOverride = false;
            SlabModelStaleSentinel.resetLiveDyPolicyForTest();
            SlabModelStaleSentinel.resetCold();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sentinelEmitsEnsembleRowOncePerPlacement(GameTestHelper helper) {
        List<LinkedHashMap<String, String>> rows = new ArrayList<>();
        SlabModelStaleSentinel.resetCold();
        SlabModelStaleSentinel.resetLiveDyPolicyForTest();
        SlabModelStaleSentinel.testSessionOverride = true;
        try {
            ServerLevel w = helper.getLevel();
            BlockPos hopper = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockPos chest = hopper.above();
            w.setBlock(hopper, Blocks.HOPPER.defaultBlockState(), 2);
            w.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
            // Inject the video's dys (chest anchored -0.5, hopper frozen flush).
            SlabModelStaleSentinel.setLiveDyPolicy((level, pos, state) ->
                    state.getBlock() == Blocks.CHEST ? -0.5 : 0.0);
            SlabModelStaleSentinel.armForTest(w, chest, SlabModelStaleSentinel.REASON_PLACEMENT, 1_000_000L);
            SlabModelStaleSentinel.recordBake(chest, -0.5f); // mesh agrees — staleness stays green
            SlabModelStaleSentinel.samplePass(w, 1_000_020L, pos -> true, rows::add);
            List<LinkedHashMap<String, String>> clashes = rows.stream()
                    .filter(r -> r.getOrDefault("kind", "").startsWith("ENSEMBLE_")).toList();
            if (clashes.size() != 1
                    || !("ENSEMBLE_" + Kind.INTERPENETRATION).equals(clashes.get(0).get("kind"))
                    || !clashes.get(0).get("depth").startsWith("0.5")) {
                throw helper.assertionException("expected exactly 1 INTERPENETRATION/0.5 row, got " + rows);
            }
            SlabModelStaleSentinel.samplePass(w, 1_000_040L, pos -> true, rows::add);
            long total = rows.stream().filter(r -> r.getOrDefault("kind", "").startsWith("ENSEMBLE_")).count();
            if (total != 1) {
                throw helper.assertionException("ensemble verdicts must emit ONCE per placement, got " + total);
            }
            helper.succeed();
        } finally {
            SlabModelStaleSentinel.testSessionOverride = false;
            SlabModelStaleSentinel.resetLiveDyPolicyForTest();
            SlabModelStaleSentinel.resetCold();
        }
    }
}
