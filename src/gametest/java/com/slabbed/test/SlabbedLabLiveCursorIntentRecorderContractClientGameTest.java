package com.slabbed.test;

import com.slabbed.util.LiveCursorIntentRecorder;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public final class SlabbedLabLiveCursorIntentRecorderContractClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try {
            Path evidenceDir = Path.of(System.getProperty(
                    "slabbed.liveCursorIntentRecorderContractDir",
                    "tmp/live-cursor-intent-recorder-contract"));
            System.setProperty("slabbed.liveCursorIntentRecorder", "true");
            System.setProperty("slabbed.liveCursorIntentRecorderDir", evidenceDir.toString());
            LiveCursorIntentRecorder.resetForTests();

            LinkedHashMap<String, String> cursor = new LinkedHashMap<>();
            cursor.put("tick", "1");
            cursor.put("time", "contract");
            cursor.put("heldItem", "minecraft:stone");
            cursor.put("finalHitType", "BLOCK");
            cursor.put("finalHitPos", "4,-60,30");
            cursor.put("finalHitFace", "EAST");
            cursor.put("finalHitState", "minecraft:stone_slab[type=bottom]");
            cursor.put("finalDy", "-0.500000");
            cursor.put("finalOwnerLaneKind", "persistent_lowered_slab_carrier");
            cursor.put("finalOutlineReplayHit", "hit=true pos=4,-60,30 side=east");
            cursor.put("finalRaycastReplayHit", "miss(empty)");
            LiveCursorIntentRecorder.recordCursor(cursor);

            LinkedHashMap<String, String> action = new LinkedHashMap<>();
            action.put("actionType", "place_block");
            action.put("heldItem", "minecraft:stone_slab");
            action.put("clickedOwnerPos", "4,-60,30");
            action.put("clickedFace", "EAST");
            action.put("clickedOwnerLaneKind", "persistent_lowered_slab_carrier");
            action.put("placementPos", "5,-60,30");
            action.put("afterState", "minecraft:stone_slab[type=bottom]");
            action.put("afterDy", "0.000000");
            action.put("actualResult", "SUCCESS");
            LiveCursorIntentRecorder.recordAction(action);
            LiveCursorIntentRecorder.flushSummaryForTests();

            assertContains(evidenceDir.resolve("session.jsonl"), "\"type\":\"cursor\"");
            assertContains(evidenceDir.resolve("session.jsonl"), "LIVE_CURSOR_GHOST_SURFACE");
            assertContains(evidenceDir.resolve("actions.tsv"), "2\t1\tplace_block");
            assertContains(evidenceDir.resolve("actions.tsv"), "LIVE_PLACEMENT_VANILLA_DY_FROM_LOWERED_OWNER");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_PLACEMENT_EXPECTED_DY_MISMATCH");
            assertContains(evidenceDir.resolve("summary.md"), "ghostSurfaceRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "loweredSideSlabPlacementVanillaDyRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "placementExpectedDyMismatchRows=1");
        } catch (Exception e) {
            throw new RuntimeException("[LIVE_CURSOR_INTENT_RECORDER_CONTRACT_RED] " + e.getMessage(), e);
        } finally {
            System.clearProperty("slabbed.liveCursorIntentRecorder");
            System.clearProperty("slabbed.liveCursorIntentRecorderDir");
            LiveCursorIntentRecorder.resetForTests();
        }
    }

    private static void assertContains(Path path, String needle) throws Exception {
        String text = Files.readString(path);
        if (!text.contains(needle)) {
            throw new RuntimeException("missing '" + needle + "' in " + path);
        }
    }
}
