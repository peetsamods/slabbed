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
            Path evidenceRoot = Path.of(System.getProperty(
                    "slabbed.liveCursorIntentRecorderContractDir",
                    "tmp/live-cursor-intent-recorder-contract"));
            Path evidenceDir = freshEvidenceDir(
                    evidenceRoot.resolve("contract-" + System.nanoTime()));
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
            cursor.put("outlineBounds", "min=(0.000000,0.000000,0.000000),max=(1.000000,1.000000,1.000000)");
            cursor.put("finalHitVec", "4.500000,-60.000000,30.500000");
            LiveCursorIntentRecorder.recordCursor(cursor);

            LinkedHashMap<String, String> renderedOutline = new LinkedHashMap<>();
            renderedOutline.put("renderedOutlinePos", "4,-60,30");
            renderedOutline.put("renderedOutlineState", "minecraft:stone_slab[type=bottom]");
            renderedOutline.put("renderedOutlineBounds",
                    "min=(0.000000,0.000000,0.000000),max=(3.000000,1.000000,1.000000)");
            renderedOutline.put("renderedOutlineWorldBounds",
                    "min=(4.000000,-60.000000,30.000000),max=(7.000000,-59.000000,31.000000)");
            renderedOutline.put("renderedOutlineCameraRelativeBounds",
                    "min=(1.000000,-1.000000,2.000000),max=(4.000000,0.000000,3.000000)");
            renderedOutline.put("renderedOutlineHitVec", "4.500000,-60.000000,30.500000");
            LiveCursorIntentRecorder.recordRenderedOutline(renderedOutline);

            // Exact TEST 19R shape: the client prediction can report the generic slab lane while the
            // authoritative server reports the anchored lane. Both are geometrically lawful at dy=-2.
            LiveCursorIntentRecorder.recordAction(loweredSideAction(
                    "client", "104,-39,30", "105,-39,30", "-2.000000", "unnamed_or_vanilla_slab"));
            LiveCursorIntentRecorder.recordAction(loweredSideAction(
                    "server", "114,-39,30", "115,-39,30", "-2.000000", "anchored_full_block"));

            LinkedHashMap<String, String> cleanProxy = loweredSideAction(
                    "server", "116,-39,30", "117,-39,30", "-2.000000", "anchored_full_block");
            cleanProxy.put("actionOrigin", "PLAYER_AUTHORED"); // hostile caller spoof attempt
            LiveCursorIntentRecorder.withActionOrigin(
                    LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                    () -> LiveCursorIntentRecorder.recordAction(cleanProxy));
            LinkedHashMap<String, String> redProxy = loweredSideAction(
                    "server", "118,-39,30", "119,-39,30", "-1.500000", "anchored_full_block");
            redProxy.put("actionOrigin", "BOGUS_ORIGIN"); // unknown values cannot fall through as proxy
            LiveCursorIntentRecorder.withActionOrigin(
                    LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                    () -> LiveCursorIntentRecorder.recordAction(redProxy));

            // Same dy on an authoritative server row is still lane-red when the server has no lawful
            // lowered ownership. This must be a LANE mismatch only, never mislabeled as a dy mismatch.
            LiveCursorIntentRecorder.recordAction(loweredSideAction(
                    "server", "124,-39,30", "125,-39,30", "-2.000000", "unnamed_or_vanilla_slab"));

            // A real dy error in an otherwise lawful lane remains a dy-only verdict.
            LiveCursorIntentRecorder.recordAction(loweredSideAction(
                    "server", "134,-39,30", "135,-39,30", "-1.500000", "anchored_full_block"));

            // Preserve the stronger vanilla-height marker alongside the dy mismatch.
            LiveCursorIntentRecorder.recordAction(loweredSideAction(
                    "server", "144,-39,30", "145,-39,30", "0.000000", "anchored_full_block"));

            // A non-consuming use row may share the same geometry snapshot, but it did not author a
            // placement and must never inflate the green-placement counter.
            LinkedHashMap<String, String> refused = loweredSideAction(
                    "client", "154,-39,30", "155,-39,30", "-2.000000", "unnamed_or_vanilla_slab");
            refused.put("actionType", "use_block");
            refused.put("actualResult", "PASS");
            LiveCursorIntentRecorder.recordAction(refused);

            // Live-shaped C4 truth rows. The recorder must honor any trustworthy finite numeric
            // expectation, including zero, instead of limiting mismatch detection to negative dy.
            LiveCursorIntentRecorder.recordAction(ordinaryAction(
                    "minecraft:bamboo_button", "164,-39,30", "164,-38,30",
                    "-1.500000", "-0.500000", "SUCCESS"));
            LiveCursorIntentRecorder.recordAction(ordinaryAction(
                    "minecraft:flower_pot", "174,-39,30", "174,-38,30",
                    "-1.500000", "-1.000000", "SUCCESS"));
            LiveCursorIntentRecorder.withActionOrigin(
                    LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                    () -> LiveCursorIntentRecorder.recordAction(ordinaryAction(
                            "minecraft:oak_fence", "184,-39,30", "184,-38,30",
                            "0.000000", "-0.500000", "SUCCESS")));
            LiveCursorIntentRecorder.withActionOrigin(
                    LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                    () -> LiveCursorIntentRecorder.recordAction(ordinaryAction(
                            "minecraft:conduit", "194,-39,30", "194,-38,30",
                            "-0.500000", "0.000000", "SUCCESS")));

            // Unknown expectations stay unknown and cannot be promoted to a generic green.
            LinkedHashMap<String, String> unknown = ordinaryAction(
                    "minecraft:lantern", "204,-39,30", "204,-38,30",
                    "unknown", "-1.000000", "SUCCESS");
            unknown.remove("expectedAfterDy");
            LiveCursorIntentRecorder.recordAction(unknown);

            // Until a future typed expected-refusal contract exists, Fail[] is unclassified failure
            // evidence and must be red even when no placement snapshot exists.
            LinkedHashMap<String, String> failed = ordinaryAction(
                    "minecraft:stone", "214,-39,30", "none",
                    "unknown", "none", "Fail[]");
            failed.put("actionType", "use_block");
            failed.put("afterState", "none");
            failed.put("afterLaneKind", "none");
            LiveCursorIntentRecorder.withActionOrigin(
                    LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                    () -> LiveCursorIntentRecorder.recordAction(failed));

            LiveCursorIntentRecorder.recordSentinel(sentinel(
                    "ENSEMBLE_OCCLUDED_OCCUPANCY", "200 -39 30"));
            LiveCursorIntentRecorder.recordSentinel(sentinel("ENSEMBLE_GAP", "210 -39 30"));
            LiveCursorIntentRecorder.recordSentinel(sentinel("ENSEMBLE_INTERPENETRATION", "215 -39 30"));
            LiveCursorIntentRecorder.recordSentinel(sentinel("MODEL_STALE_DIVERGENT", "216 -39 30"));
            LiveCursorIntentRecorder.recordSentinel(sentinel("MODEL_STALE_ABSENT", "217 -39 30"));
            LiveCursorIntentRecorder.recordSentinel(sentinel(
                    "MODEL_STALE_NO_BAKE_YELLOW", "220 -39 30"));
            LiveCursorIntentRecorder.recordSentinel(sentinel("UNKNOWN_DIAGNOSTIC", "230 -39 30"));
            LiveCursorIntentRecorder.flushSummaryForTests();

            assertContains(evidenceDir.resolve("session.jsonl"), "\"type\":\"cursor\"");
            assertContains(evidenceDir.resolve("session.jsonl"), "\"type\":\"rendered_outline\"");
            assertContains(evidenceDir.resolve("session.jsonl"), "LIVE_CURSOR_GHOST_SURFACE");
            assertContains(evidenceDir.resolve("rendered-outlines.tsv"), "LIVE_RENDERED_OUTLINE_LARGE_BOUNDS");
            assertContains(evidenceDir.resolve("rendered-outlines.tsv"), "LIVE_RENDERED_OUTLINE_REPLAY_BOUNDS_SPLIT");
            assertContains(evidenceDir.resolve("manifest.json"), "\"schemaVersion\":\"3\"");
            assertContains(evidenceDir.resolve("manifest.json"),
                    "\"recorderVersion\":\"26.2-recorder-truth-v4-c4-action-failure-audit\"");
            assertContains(evidenceDir.resolve("manifest.json"),
                    "\"actionOriginContract\":\"PLAYER_AUTHORED|AUTO_USEON_PROXY\"");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "actionId\tcursorRowId\tactionType\tactionOrigin\theldItem");
            assertContains(evidenceDir.resolve("actions.tsv"), "3\t1\tplace_block\tPLAYER_AUTHORED");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "place_block\tAUTO_USEON_PROXY\tminecraft:stone_slab\t116,-39,30");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "117,-39,30\t-2.000000\t-2.000000\tlawful_lowered_lane\tanchored_full_block\tnone");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "place_block\tAUTO_USEON_PROXY\tminecraft:stone_slab\t118,-39,30");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "119,-39,30\t-2.000000\t-1.500000\tlawful_lowered_lane\tanchored_full_block\tLIVE_PLACEMENT_EXPECTED_DY_MISMATCH");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "105,-39,30\t-2.000000\t-2.000000\tlawful_lowered_lane\tunnamed_or_vanilla_slab\tLIVE_GREEN_PLACEMENT_AUTHORING");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "115,-39,30\t-2.000000\t-2.000000\tlawful_lowered_lane\tanchored_full_block\tLIVE_GREEN_PLACEMENT_AUTHORING");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "125,-39,30\t-2.000000\t-2.000000\tlawful_lowered_lane\tunnamed_or_vanilla_slab\tLIVE_PLACEMENT_EXPECTED_LANE_MISMATCH");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "135,-39,30\t-2.000000\t-1.500000\tlawful_lowered_lane\tanchored_full_block\tLIVE_PLACEMENT_EXPECTED_DY_MISMATCH");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "155,-39,30\tunknown\t-2.000000\tunknown\tunnamed_or_vanilla_slab\tnone");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "minecraft:bamboo_button\t164,-39,30\tUP\t164,-38,30\t-1.500000\t-0.500000");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "minecraft:flower_pot\t174,-39,30\tUP\t174,-38,30\t-1.500000\t-1.000000");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "minecraft:oak_fence\t184,-39,30\tUP\t184,-38,30\t0.000000\t-0.500000");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "minecraft:conduit\t194,-39,30\tUP\t194,-38,30\t-0.500000\t0.000000");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "minecraft:lantern\t204,-39,30\tUP\t204,-38,30\tunknown\t-1.000000"
                            + "\tunknown\tanchored_full_block\tnone");
            assertContains(evidenceDir.resolve("actions.tsv"),
                    "minecraft:stone\t214,-39,30\tUP\tnone\tunknown\tnone\tunknown\tnone"
                            + "\tLIVE_PLACEMENT_UNCLASSIFIED_FAILURE");
            assertContains(evidenceDir.resolve("actions.tsv"), "LIVE_PLACEMENT_VANILLA_DY_FROM_LOWERED_OWNER");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_PLACEMENT_EXPECTED_DY_MISMATCH");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_PLACEMENT_EXPECTED_LANE_MISMATCH");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_PLACEMENT_UNCLASSIFIED_FAILURE");
            assertNotContains(evidenceDir.resolve("mismatches.tsv"), "116,-39,30");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "118,-39,30");
            assertNotContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_GREEN_PLACEMENT_AUTHORING");
            assertContains(evidenceDir.resolve("session.jsonl"),
                    "\"severity\":\"info\",\"marker\":\"INFO_ENSEMBLE_OCCLUDED_OCCUPANCY\"");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_ENSEMBLE_GAP");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_ENSEMBLE_INTERPENETRATION");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_MODEL_STALE_DIVERGENT");
            assertContains(evidenceDir.resolve("mismatches.tsv"), "LIVE_MODEL_STALE_ABSENT");
            assertNotContains(evidenceDir.resolve("mismatches.tsv"), "ENSEMBLE_OCCLUDED_OCCUPANCY");
            assertNotContains(evidenceDir.resolve("mismatches.tsv"), "MODEL_STALE_NO_BAKE_YELLOW");
            assertNotContains(evidenceDir.resolve("mismatches.tsv"), "UNKNOWN_DIAGNOSTIC");
            assertContains(evidenceDir.resolve("summary.md"), "ghostSurfaceRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "renderedOutlineRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "renderedOutlineLargeBoundsRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "renderedOutlineReplayBoundsSplitRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "loweredSideSlabPlacementVanillaDyRows=2");
            assertContains(evidenceDir.resolve("summary.md"), "placementExpectedDyMismatchRows=7");
            assertContains(evidenceDir.resolve("summary.md"), "placementUnclassifiedFailureRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "placementExpectedLaneMismatchRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "liveGreenPlacementRows=2");
            assertContains(evidenceDir.resolve("summary.md"), "playerAuthoredActionRows=9");
            assertContains(evidenceDir.resolve("summary.md"), "autoUseOnProxyActionRows=5");
            assertContains(evidenceDir.resolve("summary.md"), "modelStaleDivergentRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "modelStaleAbsentRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "ensembleOccludedOccupancyInfoRows=1");
            assertContains(evidenceDir.resolve("summary.md"), "ensembleClashRows=2");
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

    private static void assertNotContains(Path path, String needle) throws Exception {
        String text = Files.readString(path);
        if (text.contains(needle)) {
            throw new RuntimeException("unexpected '" + needle + "' in " + path);
        }
    }

    private static LinkedHashMap<String, String> loweredSideAction(
            String side, String ownerPos, String placementPos, String afterDy, String afterLane) {
        LinkedHashMap<String, String> action = new LinkedHashMap<>();
        action.put("actionType", "place_block");
        action.put("side", side);
        action.put("heldItem", "minecraft:stone_slab");
        action.put("clickedOwnerPos", ownerPos);
        action.put("clickedFace", "SOUTH");
        action.put("clickedOwnerLaneKind", "anchored_full_block");
        action.put("beforeDy", "-2.000000");
        action.put("placementPos", placementPos);
        action.put("afterState", "minecraft:stone_slab[type=bottom]");
        action.put("afterDy", afterDy);
        action.put("afterLaneKind", afterLane);
        action.put("actualResult", "SUCCESS");
        return action;
    }

    private static LinkedHashMap<String, String> ordinaryAction(
            String heldItem,
            String ownerPos,
            String placementPos,
            String expectedDy,
            String afterDy,
            String actualResult) {
        LinkedHashMap<String, String> action = new LinkedHashMap<>();
        action.put("actionType", "place_block");
        action.put("side", "server");
        action.put("heldItem", heldItem);
        action.put("clickedOwnerPos", ownerPos);
        action.put("clickedFace", "UP");
        action.put("clickedOwnerLaneKind", "anchored_full_block");
        action.put("beforeDy", "-1.500000");
        action.put("placementPos", placementPos);
        action.put("expectedAfterDy", expectedDy);
        action.put("expectedAfterLaneKind", "unknown");
        action.put("afterState", "Block{" + heldItem + "}");
        action.put("afterDy", afterDy);
        action.put("afterLaneKind", "anchored_full_block");
        action.put("actualResult", actualResult);
        return action;
    }

    private static LinkedHashMap<String, String> sentinel(String kind, String pos) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("kind", kind);
        row.put("pos", pos);
        return row;
    }

    private static Path freshEvidenceDir(Path evidenceDir) throws Exception {
        Files.createDirectories(evidenceDir);
        for (String artifact : new String[]{
                "manifest.json", "session.jsonl", "actions.tsv", "rendered-outlines.tsv",
                "mismatches.tsv", "summary.md"}) {
            Files.deleteIfExists(evidenceDir.resolve(artifact));
        }
        return evidenceDir;
    }
}
