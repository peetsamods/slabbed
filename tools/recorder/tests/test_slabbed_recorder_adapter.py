#!/usr/bin/env python3
import copy
import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock


REPO_ROOT = Path(__file__).resolve().parents[3]
ADAPTER_PATH = Path(os.environ.get(
    "SLABBED_ADAPTER_PATH",
    str(REPO_ROOT / "tools/recorder/slabbed_recorder_adapter.py"),
))
SPEC = importlib.util.spec_from_file_location("slabbed_recorder_adapter", ADAPTER_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"cannot load adapter spec: {ADAPTER_PATH}")
adapter = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(adapter)


ACTIONS_HEADER = [
    "actionId",
    "cursorRowId",
    "actionType",
    "actionOrigin",
    "heldItem",
    "clickedOwnerPos",
    "clickedFace",
    "placementPos",
    "expectedAfterDy",
    "afterDy",
    "expectedAfterLaneKind",
    "afterLaneKind",
    "marker",
]

MISMATCH_HEADER = ["type", "rowOrActionId", "marker", "pos", "heldItem"]

OUTLINE_HEADER = [
    "outlineRenderId",
    "cursorRowId",
    "renderedOutlinePos",
    "cursorFinalHitPos",
    "renderedOutlineState",
    "renderedOutlineBounds",
    "cursorOutlineBounds",
    "renderedOutlineWorldBounds",
    "renderedOutlineCameraRelativeBounds",
    "renderedOutlineHitVec",
    "marker",
]

SUMMARY_KEYS = [
    "cursorRows",
    "actionRows",
    "playerAuthoredActionRows",
    "autoUseOnProxyActionRows",
    "ghostSurfaceRows",
    "hiddenOwnerRows",
    "outlineRaycastSplitRows",
    "renderedOutlineRows",
    "renderedOutlineLargeBoundsRows",
    "renderedOutlineReplayBoundsSplitRows",
    "renderedOutlineTargetSplitRows",
    "placementExpectedDyMismatchRows",
    "placementExpectedLaneMismatchRows",
    "loweredSideSlabPlacementVanillaDyRows",
    "collisionIteratorTargetMissRows",
    "collisionIteratorTargetPresentRows",
    "liveGreenCursorTriadRows",
    "liveGreenPlacementRows",
    "modelStaleDivergentRows",
    "modelStaleAbsentRows",
    "modelStaleYellowRows",
    "breakRows",
    "placementSideDySplitRows",
    "ensembleClashRows",
    "ensembleOccludedOccupancyInfoRows",
    "sentinelArmedTotal",
    "sentinelSamplePasses",
]


def action(
    action_id,
    side,
    *,
    origin="PLAYER_AUTHORED",
    at="2026-07-11T07:00:00.000000Z",
    held="minecraft:stone_slab",
    owner="10, 64, 10",
    face="south",
    hit="10.500000,64.500000,11.000000",
    placement="10, 64, 11",
    expected_dy="-1.000000",
    after_dy="-1.000000",
    expected_lane="lawful_lowered_lane",
    after_lane="anchored_full_block",
    marker="LIVE_GREEN_PLACEMENT_AUTHORING",
    result="Success[swingSource=CLIENT]",
):
    return {
        "actionType": "place_block",
        "side": side,
        "player": "Peetsa",
        "heldItem": held,
        "clickedOwnerPos": owner,
        "clickedFace": face,
        "clickedHitVec": hit,
        "placementPos": placement,
        "placeBeforeState": "Block{minecraft:air}",
        "placeBeforeDy": "0.000000",
        "beforeState": "Block{minecraft:stone}",
        "beforeDy": "-1.000000",
        "beforeLaneKind": "anchored_full_block",
        "beforeAttachments": "anchored=true",
        "clickedOwnerLaneKind": "anchored_full_block",
        "afterState": "Block{minecraft:stone_slab}[type=top,waterlogged=false]",
        "afterDy": after_dy,
        "afterLaneKind": after_lane,
        "afterPersistentLoweredSlabCarrier": "false",
        "actualResult": result,
        "type": "action",
        "actionId": str(action_id),
        "cursorRowId": "0",
        "recordedAt": at,
        "actionOrigin": origin,
        "expectedAfterDy": expected_dy,
        "expectedAfterLaneKind": expected_lane,
        "expectedResult": "lowered_side_lane_continuation" if expected_dy != "unknown" else "unknown",
        "marker": marker,
    }


def sentinel(row_id, *, marker="LIVE_ENSEMBLE_GAP", severity="red"):
    return {
        "kind": "ENSEMBLE_GAP" if marker == "LIVE_ENSEMBLE_GAP" else "ENSEMBLE_OCCLUDED_OCCUPANCY",
        "pos": "10 64 10",
        "pairPos": "10 65 10",
        "dyLower": "-1.0",
        "dyUpper": "0.0",
        "depth": "1.0",
        "lowerState": "Block{minecraft:stone}",
        "upperState": "Block{minecraft:flower_pot}",
        "armedReason": "placement",
        "ticksSinceArm": "7",
        "type": "model_stale_sentinel",
        "rowId": str(row_id),
        "recordedAt": "2026-07-11T07:00:00.500000Z",
        "severity": severity,
        "marker": marker,
    }


def outline(row_id):
    return {
        "type": "rendered_outline",
        "outlineRenderId": str(row_id),
        "cursorRowId": "0",
        "renderedOutlinePos": "10, 64, 10",
        "cursorFinalHitPos": "10, 64, 10",
        "renderedOutlineState": "Block{minecraft:stone}",
        "renderedOutlineBounds": "min=(0,0,0),max=(1,1,1)",
        "cursorOutlineBounds": "min=(0,0,0),max=(1,1,1)",
        "renderedOutlineWorldBounds": "min=(10,64,10),max=(11,65,11)",
        "renderedOutlineCameraRelativeBounds": "min=(0,0,0),max=(1,1,1)",
        "renderedOutlineHitVec": "10.500000,64.500000,10.500000",
        "marker": "none",
        "recordedAt": "2026-07-11T07:00:00.500000Z",
    }


def cursor(row_id, marker="LIVE_GREEN_CURSOR_TRIAD"):
    return {
        "type": "cursor",
        "rowId": str(row_id),
        "recordedAt": "2026-07-11T07:00:00.500000Z",
        "heldItem": "minecraft:stone_slab",
        "finalHitType": "BLOCK",
        "finalHitPos": "10, 64, 10",
        "finalHitFace": "south",
        "finalHitVec": "10.500000,64.500000,11.000000",
        "finalHitState": "Block{minecraft:stone}",
        "finalDy": "-1.000000",
        "finalOwnerLaneKind": "anchored_full_block",
        "finalOutlineReplayHit": "hit=true pos=10,64,10 side=south",
        "finalRaycastReplayHit": "hit=true pos=10,64,10 side=south",
        "outlineBounds": "min=(0,0,0),max=(1,1,1)",
        "mismatchMarker": marker,
    }


def base_actions():
    client = action(1, "client", after_lane="unnamed_or_vanilla_slab")
    server = action(2, "server", at="2026-07-11T07:00:00.050000Z")
    proxy = action(
        3,
        "server",
        origin="AUTO_USEON_PROXY",
        at="2026-07-11T07:00:00.100000Z",
        held="minecraft:stone",
        owner="20, 64, 20",
        hit="20.500000,65.000000,20.500000",
        placement="20, 65, 20",
        expected_dy="unknown",
        expected_lane="unknown",
        marker="none",
    )
    return [client, server, proxy]


def summary_for(rows, extra_rows=(), overrides=None):
    counters = {key: 0 for key in SUMMARY_KEYS}
    actions = [row for row in rows if row.get("type") == "action"]
    counters["actionRows"] = len(actions)
    counters["playerAuthoredActionRows"] = sum(
        row.get("actionOrigin") == "PLAYER_AUTHORED" for row in actions
    )
    counters["autoUseOnProxyActionRows"] = sum(
        row.get("actionOrigin") == "AUTO_USEON_PROXY" for row in actions
    )
    counters["liveGreenPlacementRows"] = sum(
        row.get("marker") == "LIVE_GREEN_PLACEMENT_AUTHORING" for row in actions
    )
    counters["sentinelArmedTotal"] = 1
    counters["sentinelSamplePasses"] = 10
    for row in list(rows) + list(extra_rows):
        marker = row.get("marker", "")
        if row.get("type") == "cursor":
            marker = row.get("mismatchMarker", "")
            counters["cursorRows"] += 1
            counters["ghostSurfaceRows"] += int("LIVE_CURSOR_GHOST_SURFACE" in marker)
            counters["hiddenOwnerRows"] += int("LIVE_CURSOR_HIDDEN_OWNER" in marker)
            counters["outlineRaycastSplitRows"] += int("LIVE_CURSOR_OUTLINE_RAYCAST_SPLIT" in marker)
            counters["collisionIteratorTargetMissRows"] += int(
                "LIVE_COLLISION_ITERATOR_TARGET_MISS" in marker
            )
            counters["liveGreenCursorTriadRows"] += int(marker == "LIVE_GREEN_CURSOR_TRIAD")
        if row.get("type") == "break":
            counters["breakRows"] += 1
        if row.get("type") == "rendered_outline":
            counters["renderedOutlineRows"] += 1
            counters["renderedOutlineLargeBoundsRows"] += int(
                "LIVE_RENDERED_OUTLINE_LARGE_BOUNDS" in marker
            )
            counters["renderedOutlineReplayBoundsSplitRows"] += int(
                "LIVE_RENDERED_OUTLINE_REPLAY_BOUNDS_SPLIT" in marker
            )
            counters["renderedOutlineTargetSplitRows"] += int(
                "LIVE_RENDERED_OUTLINE_TARGET_SPLIT" in marker
            )
        if "LIVE_PLACEMENT_EXPECTED_DY_MISMATCH" in marker:
            counters["placementExpectedDyMismatchRows"] += 1
        if "LIVE_PLACEMENT_EXPECTED_LANE_MISMATCH" in marker:
            counters["placementExpectedLaneMismatchRows"] += 1
        if "LIVE_PLACEMENT_SIDE_DY_SPLIT" in marker:
            counters["placementSideDySplitRows"] += 1
        if row.get("type") == "model_stale_sentinel":
            if row.get("severity") == "red":
                counters["ensembleClashRows"] += 1
            if marker == "INFO_ENSEMBLE_OCCLUDED_OCCUPANCY":
                counters["ensembleOccludedOccupancyInfoRows"] += 1
    if overrides:
        counters.update(overrides)
    return counters


def write_fixture(
    root,
    *,
    rows=None,
    extra_rows=None,
    mismatches=None,
    schema="3",
    run_id="redacted",
    summary_overrides=None,
    actions_header=None,
    java_command="launcher --accessToken [REDACTED] --uuid [REDACTED]",
):
    root = Path(root)
    root.mkdir(parents=True, exist_ok=True)
    rows = list(base_actions() if rows is None else rows)
    extra_rows = list(extra_rows or [])
    mismatches = list(mismatches or [])
    manifest = {
        "schemaVersion": schema,
        "runId": run_id,
        "recorder": "LiveCursorIntentRecorder",
        "recorderVersion": "26.2-recorder-truth-v3-origin",
        "actionOriginContract": "PLAYER_AUTHORED|AUTO_USEON_PROXY",
        "enabled": "true",
        "createdAt": "2026-07-11T06:59:59.000000Z",
        "dir": "/copied/live/path",
        "gameDir": "/copied/profile",
        "minecraftVersion": "26.2",
        "gitSha": "deadbeef00",
        "buildTime": "2026-07-11T06:58:00.000000Z",
        "jarFile": "TEST fixture.jar",
        "javaCommand": java_command,
    }
    (root / "manifest.json").write_text(json.dumps(manifest) + "\n", encoding="utf-8")

    combined = sorted(
        rows + extra_rows,
        key=lambda row: int(row.get("actionId", row.get("rowId", row.get("outlineRenderId", 0)))),
    )
    (root / "session.jsonl").write_text(
        "".join(json.dumps(row, separators=(",", ":")) + "\n" for row in combined),
        encoding="utf-8",
    )

    header = list(actions_header or ACTIONS_HEADER)
    action_lines = ["\t".join(header)]
    for row in rows:
        action_lines.append("\t".join(row.get(field, "") for field in header))
    (root / "actions.tsv").write_text("\n".join(action_lines) + "\n", encoding="utf-8")

    mismatch_lines = ["\t".join(MISMATCH_HEADER)]
    mismatch_lines.extend("\t".join(row) for row in mismatches)
    (root / "mismatches.tsv").write_text("\n".join(mismatch_lines) + "\n", encoding="utf-8")
    outline_lines = ["\t".join(OUTLINE_HEADER)]
    outline_lines.extend(
        "\t".join(row.get(field, "") for field in OUTLINE_HEADER)
        for row in extra_rows if row.get("type") == "rendered_outline"
    )
    (root / "rendered-outlines.tsv").write_text(
        "\n".join(outline_lines) + "\n",
        encoding="utf-8",
    )

    counters = summary_for(rows, extra_rows, summary_overrides)
    summary = "# Slabbed Live Cursor Intent Recorder Summary\n\n" + "".join(
        f"{key}={counters[key]}\n" for key in SUMMARY_KEYS
    )
    (root / "summary.md").write_text(summary, encoding="utf-8")
    return root


class AdapterTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_direct_schema3_pair_and_proxy_are_separate(self):
        recorder = write_fixture(self.root / "recorder")
        triage = adapter.analyze(recorder)
        self.assertEqual("EXPLICIT_GREEN", triage["verdict"]["status"])
        self.assertEqual(1, triage["playerAuthored"]["pairCount"])
        self.assertEqual([1, 2], [
            triage["playerAuthored"]["pairs"][0]["clientActionId"],
            triage["playerAuthored"]["pairs"][0]["serverActionId"],
        ])
        self.assertEqual(1, triage["autoUseOnProxy"]["rowCount"])
        self.assertEqual("unknown", triage["coverage"]["status"])
        self.assertTrue(all(item["sha256"] for item in triage["artifacts"].values()))

    def test_proxy_only_is_no_player_evidence_not_green(self):
        rows = [
            action(1, "server", origin="AUTO_USEON_PROXY", marker="none", expected_dy="unknown", expected_lane="unknown")
        ]
        triage = adapter.analyze(write_fixture(self.root / "recorder", rows=rows))
        self.assertEqual("NO_PLAYER_EVIDENCE", triage["verdict"]["status"])
        self.assertEqual(0, triage["playerAuthored"]["pairCount"])
        self.assertEqual(1, triage["autoUseOnProxy"]["rowCount"])

    def test_unknown_oracle_pair_is_observed_unclassified(self):
        rows = [
            action(1, "client", expected_dy="unknown", expected_lane="unknown", marker="none"),
            action(2, "server", at="2026-07-11T07:00:00.050000Z", expected_dy="unknown", expected_lane="unknown", marker="none"),
        ]
        triage = adapter.analyze(write_fixture(self.root / "recorder", rows=rows))
        self.assertEqual("OBSERVED_UNCLASSIFIED", triage["verdict"]["status"])
        self.assertEqual("OBSERVED_UNCLASSIFIED", triage["playerAuthored"]["pairs"][0]["verdict"])

    def test_adapter_derived_dy_split_is_red(self):
        rows = [
            action(
                1,
                "client",
                after_dy="-1.000000",
                expected_dy="unknown",
                expected_lane="unknown",
                marker="none",
            ),
            action(
                2,
                "server",
                at="2026-07-11T07:00:00.050000Z",
                after_dy="-0.500000",
                expected_dy="unknown",
                expected_lane="unknown",
                marker="none",
            ),
        ]
        triage = adapter.analyze(write_fixture(self.root / "recorder", rows=rows))
        self.assertEqual("RED", triage["verdict"]["status"])
        self.assertFalse(triage["playerAuthored"]["pairs"][0]["dyMatch"])
        self.assertIn("ADAPTER_SIDE_DY_SPLIT", triage["playerAuthored"]["pairs"][0]["redMarkers"])

    def test_timeout_leaves_player_rows_unpaired(self):
        rows = [
            action(1, "client"),
            action(2, "server", at="2026-07-11T07:00:01.000000Z"),
        ]
        triage = adapter.analyze(write_fixture(self.root / "recorder", rows=rows))
        self.assertEqual("PAIRING_INCOMPLETE", triage["verdict"]["status"])
        self.assertEqual([1, 2], [row["actionId"] for row in triage["playerAuthored"]["unpairedRows"]])

    def test_repeated_click_with_two_perfect_matchings_is_ambiguous(self):
        rows = [
            action(1, "client", at="2026-07-11T07:00:00.000000Z"),
            action(2, "client", at="2026-07-11T07:00:00.010000Z"),
            action(3, "server", at="2026-07-11T07:00:00.020000Z"),
            action(4, "server", at="2026-07-11T07:00:00.030000Z"),
        ]
        triage = adapter.analyze(write_fixture(self.root / "recorder", rows=rows))
        self.assertEqual("PAIRING_INCOMPLETE", triage["verdict"]["status"])
        self.assertEqual(4, len(triage["playerAuthored"]["ambiguousRows"]))
        self.assertEqual(0, triage["playerAuthored"]["pairCount"])

    def test_proxy_rows_never_pair_even_when_client_and_server_match(self):
        rows = [
            action(1, "client", origin="AUTO_USEON_PROXY", marker="none"),
            action(2, "server", origin="AUTO_USEON_PROXY", at="2026-07-11T07:00:00.050000Z", marker="none"),
        ]
        triage = adapter.analyze(write_fixture(self.root / "recorder", rows=rows))
        self.assertEqual(0, triage["playerAuthored"]["pairCount"])
        self.assertEqual(2, triage["autoUseOnProxy"]["rowCount"])

    def test_proxy_red_is_visible_but_never_player_proof(self):
        rows = [action(
            1,
            "server",
            origin="AUTO_USEON_PROXY",
            marker="LIVE_PLACEMENT_EXPECTED_DY_MISMATCH",
            after_dy="0.000000",
        )]
        mismatch = [(
            "action",
            "1",
            "LIVE_PLACEMENT_EXPECTED_DY_MISMATCH",
            "10, 64, 10",
            "minecraft:stone_slab",
        )]
        triage = adapter.analyze(write_fixture(
            self.root / "recorder",
            rows=rows,
            mismatches=mismatch,
        ))
        self.assertEqual("RED", triage["verdict"]["status"])
        self.assertEqual("ABSENT", triage["verdict"]["playerProof"])
        self.assertEqual(0, triage["playerAuthored"]["pairCount"])
        self.assertEqual([1], [row["actionId"] for row in triage["autoUseOnProxy"]["redRows"]])
        self.assertEqual("10,64,11", triage["mismatches"][0]["context"]["placementPos"])

    def test_paired_failed_results_are_adapter_red(self):
        rows = [
            action(1, "client", expected_dy="unknown", expected_lane="unknown", marker="none", result="Fail[]"),
            action(2, "server", at="2026-07-11T07:00:00.050000Z", expected_dy="unknown", expected_lane="unknown", marker="none", result="Fail[]"),
        ]
        triage = adapter.analyze(write_fixture(self.root / "recorder", rows=rows))
        self.assertEqual("RED", triage["verdict"]["status"])
        self.assertIn("ADAPTER_PLACEMENT_FAILED", triage["playerAuthored"]["pairs"][0]["redMarkers"])

    def test_pair_state_and_success_detail_splits_are_red(self):
        state_rows = [
            action(1, "client"),
            action(2, "server", at="2026-07-11T07:00:00.050000Z"),
        ]
        state_rows[1]["afterState"] = "Block{minecraft:dirt}"
        triage = adapter.analyze(write_fixture(self.root / "state", rows=state_rows))
        self.assertEqual("RED", triage["verdict"]["status"])
        self.assertIn("ADAPTER_SIDE_STATE_SPLIT", triage["playerAuthored"]["pairs"][0]["redMarkers"])

        result_rows = [
            action(1, "client", result="Success[swingSource=CLIENT]"),
            action(2, "server", at="2026-07-11T07:00:00.050000Z", result="Success[swingSource=SERVER]"),
        ]
        triage = adapter.analyze(write_fixture(self.root / "result", rows=result_rows))
        self.assertEqual("RED", triage["verdict"]["status"])
        self.assertIn(
            "ADAPTER_SIDE_RESULT_DETAIL_SPLIT",
            triage["playerAuthored"]["pairs"][0]["redMarkers"],
        )

    def test_malformed_or_semantically_impossible_green_dy_fails_closed(self):
        cases = [
            ("unknown", "unknown"),
            ("-2.000000", "-1.000000"),
            ("-Infinity", "-Infinity"),
        ]
        for index, (expected, after) in enumerate(cases):
            with self.subTest(expected=expected, after=after):
                rows = [
                    action(1, "client", expected_dy=expected, after_dy=after),
                    action(
                        2,
                        "server",
                        at="2026-07-11T07:00:00.050000Z",
                        expected_dy=expected,
                        after_dy=after,
                    ),
                ]
                with self.assertRaises(adapter.IntegrityError):
                    adapter.analyze(write_fixture(self.root / ("green%d" % index), rows=rows))

    def test_non_green_placement_after_dy_must_still_be_finite(self):
        rows = [
            action(1, "client", expected_dy="unknown", after_dy="unknown", marker="none"),
            action(
                2,
                "server",
                at="2026-07-11T07:00:00.050000Z",
                expected_dy="unknown",
                after_dy="unknown",
                marker="none",
            ),
        ]
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(write_fixture(self.root / "unknown-dy", rows=rows))

    def test_sentinel_none_position_resolves_from_session(self):
        rows = base_actions()[:2]
        red = sentinel(3)
        mismatch = [("model_stale_sentinel", "3", "LIVE_ENSEMBLE_GAP", "none", "none")]
        triage = adapter.analyze(write_fixture(self.root / "recorder", rows=rows, extra_rows=[red], mismatches=mismatch))
        self.assertEqual("RED", triage["verdict"]["status"])
        self.assertEqual("EXPLICIT_GREEN", triage["verdict"]["playerProof"])
        self.assertEqual("10,64,10", triage["mismatches"][0]["resolvedPos"])
        self.assertEqual("10,65,10", triage["mismatches"][0]["resolvedPairPos"])
        self.assertEqual("session.pos", triage["mismatches"][0]["positionSource"])

    def test_schema2_is_explicitly_unsupported(self):
        recorder = write_fixture(self.root / "recorder", schema="2")
        with self.assertRaises(adapter.UnsupportedSchemaError):
            adapter.analyze(recorder)

    def test_old_actions_header_fails_closed(self):
        old_header = [field for field in ACTIONS_HEADER if field != "actionOrigin"]
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(write_fixture(self.root / "recorder", actions_header=old_header))

    def test_summary_counter_drift_fails_closed(self):
        recorder = write_fixture(self.root / "recorder", summary_overrides={"actionRows": 999})
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(recorder)

    def test_unknown_origin_fails_closed(self):
        rows = base_actions()
        rows[0]["actionOrigin"] = "SPOOFED_PLAYER"
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(write_fixture(self.root / "recorder", rows=rows))

    def test_every_proxy_action_keeps_canonical_side_and_geometry(self):
        corruptions = {
            "side": "bogus",
            "clickedOwnerPos": "x",
            "clickedHitVec": "x",
            "placementPos": "x",
            "clickedFace": "diagonal",
        }
        for index, (field, value) in enumerate(corruptions.items()):
            with self.subTest(field=field):
                row = action(1, "server", origin="AUTO_USEON_PROXY", marker="none")
                row[field] = value
                with self.assertRaises(adapter.IntegrityError):
                    adapter.analyze(write_fixture(self.root / ("proxy-geometry%d" % index), rows=[row]))

    def test_green_marker_cannot_hide_a_red_marker_token(self):
        impossible = {
            "type": "cursor",
            "rowId": "4",
            "recordedAt": "2026-07-11T07:00:00.500000Z",
            "mismatchMarker": "LIVE_GREEN_CURSOR_TRIAD|LIVE_CURSOR_GHOST_SURFACE",
        }
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(write_fixture(self.root / "recorder", extra_rows=[impossible]))

    def test_cursor_and_outline_marker_fields_cannot_be_missing(self):
        cursor = {
            "type": "cursor",
            "rowId": "4",
            "recordedAt": "2026-07-11T07:00:00.500000Z",
        }
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(write_fixture(self.root / "cursor", extra_rows=[cursor]))

        rendered = outline(4)
        del rendered["marker"]
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(write_fixture(self.root / "outline", extra_rows=[rendered]))

    def test_complete_cursor_row_passes_its_type_contract(self):
        triage = adapter.analyze(write_fixture(self.root / "recorder", extra_rows=[cursor(4)]))
        self.assertEqual(1, triage["counters"]["derived"]["cursorRows"])
        self.assertEqual(1, triage["counters"]["derived"]["liveGreenCursorTriadRows"])

    def test_producer_shaped_null_cursor_target_is_valid(self):
        row = cursor(4, marker="none")
        row.update({
            "finalHitType": "null",
            "finalHitPos": "none",
            "finalHitFace": "none",
            "finalHitVec": "none",
            "finalHitState": "none",
            "finalDy": "NaN",
            "finalOwnerLaneKind": "none",
            "finalOutlineReplayHit": "none",
            "finalRaycastReplayHit": "none",
            "outlineBounds": "none",
        })
        triage = adapter.analyze(write_fixture(self.root / "recorder", extra_rows=[row]))
        self.assertEqual(1, triage["counters"]["derived"]["cursorRows"])
        self.assertEqual(0, triage["counters"]["derived"]["liveGreenCursorTriadRows"])

    def test_incomplete_non_action_rows_fail_closed(self):
        incomplete_cursor = {
            "type": "cursor",
            "rowId": "4",
            "recordedAt": "2026-07-11T07:00:00.500000Z",
            "mismatchMarker": "none",
        }
        incomplete_outline = {
            "type": "rendered_outline",
            "outlineRenderId": "4",
            "recordedAt": "2026-07-11T07:00:00.500000Z",
            "marker": "none",
        }
        incomplete_info = {
            "type": "model_stale_sentinel",
            "rowId": "4",
            "recordedAt": "2026-07-11T07:00:00.500000Z",
            "kind": "ENSEMBLE_OCCLUDED_OCCUPANCY",
            "severity": "info",
            "marker": "INFO_ENSEMBLE_OCCLUDED_OCCUPANCY",
        }
        incomplete_break = {
            "type": "break",
            "rowId": "4",
            "recordedAt": "2026-07-11T07:00:00.500000Z",
        }
        for index, row in enumerate([
            incomplete_cursor, incomplete_outline, incomplete_info, incomplete_break
        ]):
            with self.subTest(row_type=row["type"]):
                with self.assertRaises(adapter.IntegrityError):
                    adapter.analyze(write_fixture(self.root / ("incomplete%d" % index), extra_rows=[row]))

    def test_sentinel_kind_severity_and_marker_must_agree(self):
        impossible = sentinel(4, marker="LIVE_ENSEMBLE_GAP", severity="info")
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(write_fixture(self.root / "recorder", extra_rows=[impossible]))

    def test_unredacted_sensitive_manifest_value_fails_closed(self):
        commands = [
            "launcher --accessToken secret-token --uuid [REDACTED]",
            "launcher --session=secret-token --credential=example"[REDACTED]\"",
        ]
        for index, command in enumerate(commands):
            with self.subTest(command=command):
                recorder = write_fixture(
                    self.root / ("recorder%d" % index), java_command=command
                )
                with self.assertRaises(adapter.IntegrityError):
                    adapter.analyze(recorder)

    def test_duplicate_or_gapped_global_ids_fail_closed(self):
        rows = base_actions()
        rows[2]["actionId"] = "4"
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(write_fixture(self.root / "recorder", rows=rows))

    def test_orphan_or_marker_disagreeing_mismatch_fails_closed(self):
        mismatch = [("model_stale_sentinel", "99", "LIVE_ENSEMBLE_GAP", "none", "none")]
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(write_fixture(self.root / "recorder", mismatches=mismatch))

        red = sentinel(4)
        mismatch = [("model_stale_sentinel", "4", "LIVE_OTHER", "none", "none")]
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(write_fixture(self.root / "recorder2", extra_rows=[red], mismatches=mismatch))

    def test_non_sentinel_mismatch_must_keep_its_projected_position(self):
        rows = [action(
            1,
            "server",
            origin="AUTO_USEON_PROXY",
            marker="LIVE_PLACEMENT_EXPECTED_DY_MISMATCH",
        )]
        mismatch = [(
            "action",
            "1",
            "LIVE_PLACEMENT_EXPECTED_DY_MISMATCH",
            "none",
            "minecraft:stone_slab",
        )]
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(write_fixture(self.root / "recorder", rows=rows, mismatches=mismatch))

    def test_info_sentinel_is_forbidden_from_red_mismatch_stream(self):
        info = sentinel(4, marker="INFO_ENSEMBLE_OCCLUDED_OCCUPANCY", severity="info")
        mismatch = [("model_stale_sentinel", "4", "INFO_ENSEMBLE_OCCLUDED_OCCUPANCY", "none", "none")]
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(write_fixture(
                self.root / "recorder",
                extra_rows=[info],
                mismatches=mismatch,
            ))

    def test_duplicate_summary_key_and_tsv_session_drift_fail_closed(self):
        recorder = write_fixture(self.root / "recorder")
        with (recorder / "summary.md").open("a", encoding="utf-8") as handle:
            handle.write("actionRows=3\n")
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(recorder)

        recorder2 = write_fixture(self.root / "recorder2")
        text = (recorder2 / "actions.tsv").read_text(encoding="utf-8")
        (recorder2 / "actions.tsv").write_text(
            text.replace("LIVE_GREEN_PLACEMENT_AUTHORING", "none", 1),
            encoding="utf-8",
        )
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(recorder2)

    def test_outline_tsv_must_match_session_projection(self):
        recorder = write_fixture(self.root / "recorder", extra_rows=[outline(4)])
        triage = adapter.analyze(recorder)
        self.assertEqual(1, triage["counters"]["producer"]["renderedOutlineRows"])
        text = (recorder / "rendered-outlines.tsv").read_text(encoding="utf-8")
        (recorder / "rendered-outlines.tsv").write_text(
            text.replace("Block{minecraft:stone}", "Block{minecraft:dirt}"),
            encoding="utf-8",
        )
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(recorder)

    def test_missing_artifact_never_becomes_plausible_zero(self):
        recorder = write_fixture(self.root / "recorder")
        (recorder / "session.jsonl").unlink()
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(recorder)

    def test_zero_row_bootstrap_may_legitimately_omit_session_jsonl(self):
        recorder = write_fixture(
            self.root / "recorder",
            rows=[],
        )
        (recorder / "session.jsonl").unlink()
        triage = adapter.analyze(recorder)
        self.assertEqual("NO_PLAYER_EVIDENCE", triage["verdict"]["status"])
        self.assertEqual("absent_valid_zero_row_bootstrap", triage["liveness"]["sessionJsonl"])
        self.assertEqual(1, triage["liveness"]["sentinelArmedTotal"])
        self.assertEqual(10, triage["liveness"]["sentinelSamplePasses"])
        self.assertNotIn("session.jsonl", triage["artifacts"])

    def test_multiple_schema3_children_require_run_id(self):
        parent = self.root / "live-cursor-recorder"
        first_id = "11111111-2222-4333-8444-555555555555"
        second_id = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        write_fixture(parent / f"schema-3-{first_id}", run_id=first_id)
        write_fixture(parent / f"schema-3-{second_id}", run_id=second_id)
        with self.assertRaises(adapter.DiscoveryError):
            adapter.analyze(parent)
        triage = adapter.analyze(parent, run_id=second_id)
        self.assertEqual(second_id, triage["run"]["runId"])

    def test_direct_schema3_plus_child_is_ambiguous_and_selectable(self):
        parent = self.root / "live-cursor-recorder"
        second_id = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        write_fixture(parent)
        write_fixture(parent / ("schema-3-" + second_id), run_id=second_id)
        with self.assertRaises(adapter.DiscoveryError):
            adapter.analyze(parent)
        triage = adapter.analyze(parent, run_id=second_id)
        self.assertEqual(second_id, triage["run"]["runId"])

    def test_wrong_run_id_on_exact_schema3_dir_is_discovery_error(self):
        recorder = write_fixture(self.root / "recorder")
        with self.assertRaises(adapter.DiscoveryError):
            adapter.discover(recorder, run_id="redacted")

    def test_hall_deficient_pair_graph_is_rejected_in_polynomial_time(self):
        clients = set(range(24))
        servers = set(range(24))
        edges = {(client, server) for client in range(23) for server in range(22)}
        edges.update({(23, 21), (23, 22), (23, 23)})
        started = time.monotonic()
        self.assertEqual([], adapter._perfect_matchings(clients, servers, edges))
        self.assertLess(time.monotonic() - started, 0.5)

    def test_frozen_evidence_recorder_child_is_discovered(self):
        evidence = self.root / "evidence"
        write_fixture(evidence / "recorder")
        triage = adapter.analyze(evidence)
        self.assertEqual("evidence-recorder", triage["source"]["discoveryMode"])

    def test_schema_child_basename_must_match_manifest_run_id(self):
        run_id="redacted"
        child = self.root / "schema-3-aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        write_fixture(child, run_id=run_id)
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(child)

    def test_json_and_markdown_are_deterministic(self):
        triage = adapter.analyze(write_fixture(self.root / "recorder"))
        first_json = adapter.render_json(triage)
        second_json = adapter.render_json(triage)
        self.assertEqual(first_json, second_json)
        self.assertTrue(first_json.endswith("\n"))
        self.assertNotIn("generatedAt", first_json)
        first_md = adapter.render_markdown(triage)
        self.assertEqual(first_md, adapter.render_markdown(triage))
        self.assertIn("## Evidence Boundary", first_md)
        self.assertIn("AUTO_USEON_PROXY", first_md)

    def test_analysis_fails_if_artifact_changes_between_hash_passes(self):
        recorder = write_fixture(self.root / "recorder")
        original_artifact_info = adapter._artifact_info
        calls = {"count": 0}

        def racing_artifact_info(path):
            calls["count"] += 1
            if calls["count"] == len(adapter.ARTIFACT_NAMES) + 1:
                summary = recorder / "summary.md"
                summary.write_text(summary.read_text(encoding="utf-8") + "# raced\n", encoding="utf-8")
            return original_artifact_info(path)

        with mock.patch.object(adapter, "_artifact_info", side_effect=racing_artifact_info):
            with self.assertRaises(adapter.IntegrityError):
                adapter.analyze(recorder)

    def test_output_refuses_to_overwrite_raw_recorder_artifact(self):
        recorder = write_fixture(self.root / "recorder")
        summary = recorder / "summary.md"
        before = summary.read_bytes()
        result = subprocess.run(
            [sys.executable, str(ADAPTER_PATH), str(recorder), "--output", str(summary)],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(6, result.returncode)
        self.assertIn("refusing to overwrite recorder evidence artifact", result.stderr)
        self.assertEqual(before, summary.read_bytes())

        parent = self.root / "isolated-parent"
        child_id = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        write_fixture(parent, schema="2")
        write_fixture(parent / ("schema-3-" + child_id), run_id=child_id)
        parent_summary = parent / "summary.md"
        parent_before = parent_summary.read_bytes()
        result = subprocess.run(
            [sys.executable, str(ADAPTER_PATH), str(parent), "--output", str(parent_summary)],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(6, result.returncode)
        self.assertEqual(parent_before, parent_summary.read_bytes())

    def test_wrappers_preserve_valid_and_unsupported_exit_codes(self):
        recorder = write_fixture(self.root / "recorder")
        summarize = REPO_ROOT / "tools/recorder/slabbed-recorder-summarize"
        capsule = REPO_ROOT / "tools/recorder/slabbed-recorder-capsule-json"
        result = subprocess.run([str(summarize), str(recorder)], text=True, capture_output=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("# Slabbed Recorder Triage", result.stdout)
        result = subprocess.run([str(capsule), str(recorder)], text=True, capture_output=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(1, json.loads(result.stdout)["triageSchemaVersion"])

        result = subprocess.run(
            [str(capsule), str(recorder), "--require-player-pairs"],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

        proxy = write_fixture(self.root / "proxy", rows=[
            action(1, "server", origin="AUTO_USEON_PROXY", marker="none")
        ])
        result = subprocess.run(
            [str(capsule), str(proxy), "--require-player-pairs"],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(7, result.returncode)
        self.assertIn("NO_PLAYER_EVIDENCE", result.stdout)

        legacy = write_fixture(self.root / "legacy", schema="2")
        result = subprocess.run([str(capsule), str(legacy)], text=True, capture_output=True, check=False)
        self.assertEqual(4, result.returncode)
        self.assertEqual("", result.stdout)


if __name__ == "__main__":
    unittest.main()
