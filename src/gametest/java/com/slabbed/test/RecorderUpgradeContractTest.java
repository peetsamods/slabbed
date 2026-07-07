package com.slabbed.test;

import com.slabbed.util.LiveCursorIntentRecorder;
import com.slabbed.util.SlabModelStaleSentinel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/**
 * Contract suite for the TEST (3)-triage recorder upgrade: side/player fields + placementPos
 * prior-state on action rows, the same-instant client/server afterDy pair rule
 * ({@code LIVE_PLACEMENT_SIDE_DY_SPLIT} — the measured L3 first-frame snap, previously found only by
 * hand-mining millisecond row pairs), break-event capture (the blindness behind the
 * "data-destructive downgrade" false alarm and the un-evidenced tower-churn report), and the sentinel
 * liveness counters (zero red rows must be green-by-evidence, not green-by-absence).
 */
public final class RecorderUpgradeContractTest {

    private static Path setup(String name) {
        Path dir = Path.of("tmp", "recorder-upgrade-" + name);
        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, dir.toString());
        LiveCursorIntentRecorder.resetForTests();
        return dir;
    }

    private static void teardown() {
        System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
        System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
        LiveCursorIntentRecorder.resetForTests();
    }

    private static String read(Path dir, String file) {
        try {
            return Files.readString(dir.resolve(file));
        } catch (Exception e) {
            throw new RuntimeException("cannot read " + file + " in " + dir, e);
        }
    }

    private static LinkedHashMap<String, String> actionRow(String side, String placementPos,
                                                           String heldItem, String afterDy) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("side", side);
        row.put("placementPos", placementPos);
        row.put("heldItem", heldItem);
        row.put("afterDy", afterDy);
        row.put("actualResult", "SUCCESS");
        return row;
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sideDySplitPairRuleFires(GameTestHelper helper) {
        Path dir = setup("split");
        try {
            LiveCursorIntentRecorder.recordAction(actionRow("client", "1, 2, 3", "minecraft:oak_slab", "-0.500000"));
            LiveCursorIntentRecorder.recordAction(actionRow("server", "1, 2, 3", "minecraft:oak_slab", "0.000000"));
            String session = read(dir, "session.jsonl");
            if (!session.contains("LIVE_PLACEMENT_SIDE_DY_SPLIT") || !session.contains("\"clientAfterDy\":\"-0.500000\"")) {
                throw helper.assertionException("server row disagreeing with its client pair must carry the split marker + clientAfterDy");
            }
            if (!read(dir, "summary.md").contains("placementSideDySplitRows=1")) {
                throw helper.assertionException("split counter must be 1");
            }
            if (!read(dir, "mismatches.tsv").contains("LIVE_PLACEMENT_SIDE_DY_SPLIT")) {
                throw helper.assertionException("split must land in mismatches.tsv (it is a verdict, not a breadcrumb)");
            }
            helper.succeed();
        } finally {
            teardown();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void matchingPairStaysUnflagged(GameTestHelper helper) {
        Path dir = setup("nosplit");
        try {
            LiveCursorIntentRecorder.recordAction(actionRow("client", "1, 2, 3", "minecraft:oak_slab", "-0.500000"));
            LiveCursorIntentRecorder.recordAction(actionRow("server", "1, 2, 3", "minecraft:oak_slab", "-0.500000"));
            if (read(dir, "session.jsonl").contains("LIVE_PLACEMENT_SIDE_DY_SPLIT")
                    || !read(dir, "summary.md").contains("placementSideDySplitRows=0")) {
                throw helper.assertionException("agreeing client/server pair must NOT be flagged");
            }
            helper.succeed();
        } finally {
            teardown();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void breakEventRecordsPopDetectionCells(GameTestHelper helper) {
        Path dir = setup("break");
        try {
            ServerLevel w = helper.getLevel();
            BlockPos support = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockPos torch = support.above();
            w.setBlock(support, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
            w.setBlock(torch, Blocks.TORCH.defaultBlockState(), 2);
            // Simulate the event payload for breaking the SLAB — the row must capture the torch above
            // (the pop-detection cell, dy=-0.5 via the floor-torch lane) pre-break.
            LiveCursorIntentRecorder.recordBreakEvent(w, support, w.getBlockState(support), "tester");
            String session = read(dir, "session.jsonl");
            if (!session.contains("\"type\":\"break\"")
                    || !session.contains("\"player\":\"tester\"")
                    || !session.contains("\"aboveDy\":\"-0.500000\"")) {
                throw helper.assertionException("break row must record the broken cell + above/below neighbor dys; got " + session);
            }
            if (!read(dir, "summary.md").contains("breakRows=1")) {
                throw helper.assertionException("breakRows counter must be 1");
            }
            helper.succeed();
        } finally {
            teardown();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void realPlacementCarriesSideAndPriorState(GameTestHelper helper) {
        Path dir = setup("prior");
        try {
            ServerLevel w = helper.getLevel();
            BlockPos ground = helper.absolutePos(new BlockPos(2, 2, 2));
            w.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
            // REAL useOn path (mock player), never a setBlock shortcut: the HEAD inject must snapshot
            // the placement cell (air) pre-mutation and the RETURN row must carry it.
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack stack = new ItemStack(Items.TORCH);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
            stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            String session = read(dir, "session.jsonl");
            String lastAction = null;
            for (String line : session.split("\n")) {
                if (line.contains("\"type\":\"action\"")) {
                    lastAction = line;
                }
            }
            if (lastAction == null) {
                throw helper.assertionException("real useOn placement must produce an action row");
            }
            if (!lastAction.contains("\"side\":\"server\"")) {
                throw helper.assertionException("gametest placement must be tagged side=server; got " + lastAction);
            }
            if (lastAction.contains("\"player\":\"none\"")) {
                throw helper.assertionException("mock-player placement must carry a player name");
            }
            if (!lastAction.contains("\"placeBeforeState\":\"Block{minecraft:air}\"")
                    || !lastAction.contains("\"placeBeforeDy\":\"0.000000\"")) {
                throw helper.assertionException("row must carry the pre-place snapshot of the placement cell (air); got " + lastAction);
            }
            helper.succeed();
        } finally {
            teardown();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sentinelLivenessCountersSurfaceInSummary(GameTestHelper helper) {
        Path dir = setup("liveness");
        try {
            long armedBefore = SlabModelStaleSentinel.armedTotalCount();
            long passesBefore = SlabModelStaleSentinel.samplePassCount();
            SlabModelStaleSentinel.resetCold();
            SlabModelStaleSentinel.testSessionOverride = true;
            SlabModelStaleSentinel.armForTest(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)),
                    SlabModelStaleSentinel.REASON_PLACEMENT, 1_000_000L);
            SlabModelStaleSentinel.samplePass(helper.getLevel(), 1_000_020L, pos -> true, row -> { });
            // Any recorder row rewrites summary.md — feed one and read the liveness lines.
            LiveCursorIntentRecorder.recordAction(actionRow("client", "9, 9, 9", "minecraft:stone", "0.000000"));
            String summary = read(dir, "summary.md");
            long armedNow = parse(summary, "sentinelArmedTotal=");
            long passesNow = parse(summary, "sentinelSamplePasses=");
            if (armedNow <= armedBefore || passesNow <= passesBefore) {
                throw helper.assertionException("liveness counters must prove the probe armed+judged: armed "
                        + armedBefore + "->" + armedNow + ", passes " + passesBefore + "->" + passesNow);
            }
            helper.succeed();
        } finally {
            SlabModelStaleSentinel.testSessionOverride = false;
            SlabModelStaleSentinel.resetCold();
            teardown();
        }
    }

    private static long parse(String summary, String key) {
        for (String line : summary.split("\n")) {
            if (line.startsWith(key)) {
                return Long.parseLong(line.substring(key.length()).trim());
            }
        }
        return -1L;
    }
}
