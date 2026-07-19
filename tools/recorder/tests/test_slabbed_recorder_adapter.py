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

C3_PAIR_FIELDS = [
    "afterStoredDy",
    "afterStoredDyBits",
    "pairPos",
    "pairPart",
    "pairState",
    "pairAfterDy",
    "pairStoredDy",
    "pairStoredDyBits",
]
C3_ACTIONS_HEADER = ACTIONS_HEADER + C3_PAIR_FIELDS
C3_RECORDER_VERSION = "26.2-recorder-truth-v3-origin-c3-pair-fields"
C4_RECORDER_VERSION = "26.2-recorder-truth-v4-c4-action-failure-audit"
SCHEMA6_RECORDER_VERSION = "26.2-recorder-truth-v8-logical-attempts"
PLACEMENT_VERDICT_CONTRACT = "PlacementVerificationVerdict-v3"
LOGICAL_ATTEMPT_CONTRACT = "LogicalPlacementAttempt-v1"
SCHEMA6_run_id="redacted"
SCHEMA6_ACTIONS_HEADER = C3_ACTIONS_HEADER + [
    "logicalAttemptId",
    "phase",
    "playerProof",
]

MISMATCH_HEADER = ["type", "rowOrActionId", "marker", "pos", "heldItem"]
SCHEMA6_MISMATCH_HEADER = MISMATCH_HEADER + ["failureClasses"]

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
    "placementUnclassifiedFailureRows",
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
SCHEMA6_SUMMARY_KEYS = SUMMARY_KEYS[:19] + [
    "placementVerdictGreenRows",
    "placementVerdictRedRows",
    "placementVerdictInconclusiveRows",
    "placementVerdictExpectedRefusalRows",
    "placementVerdictUnclassifiedFailureRows",
    "logicalAttemptRows",
    "mergedClientServerAttemptRows",
    "autoProxyLogicalAttemptRows",
    "serverOnlyLogicalAttemptRows",
    "clientOnlyLogicalAttemptRows",
    "playerProofLogicalAttemptRows",
    "logicalAttemptVerdictGreenRows",
    "logicalAttemptVerdictRedRows",
    "logicalAttemptVerdictInconclusiveRows",
    "logicalAttemptVerdictExpectedRefusalRows",
    "logicalAttemptVerdictUnclassifiedFailureRows",
    "playerProofGreenLogicalAttemptRows",
] + SUMMARY_KEYS[19:]
COMPONENT_VERDICT_FIELDS = [
    "placedVerdict",
    "anchorVerdict",
    "modelVerdict",
    "collisionVerdict",
    "raycastVerdict",
    "outlineVerdict",
    "stabilityVerdict",
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


def c3_action(
    action_id,
    side,
    *,
    family="door",
    at="2026-07-11T07:00:00.000000Z",
    placement="10, 64, 11",
    pair_pos="10, 65, 11",
):
    if family == "door":
        held = "minecraft:oak_door"
        owner = "10, 64, 10"
        hit = "10.500000,65.000000,10.500000"
        after_state = (
            "Block{minecraft:oak_door}[facing=south,half=lower,hinge=left,open=false,powered=false]"
        )
        pair_part = "upper"
        pair_state = (
            "Block{minecraft:oak_door}[facing=south,half=upper,hinge=left,open=false,powered=false]"
        )
    elif family == "bed":
        held = "minecraft:red_bed"
        owner = "20, 64, 20"
        hit = "20.500000,65.000000,20.500000"
        after_state = "Block{minecraft:red_bed}[facing=east,occupied=false,part=foot]"
        pair_part = "head"
        pair_state = "Block{minecraft:red_bed}[facing=east,occupied=false,part=head]"
    else:
        raise ValueError("unknown C3 fixture family: %s" % family)
    row = action(
        action_id,
        side,
        at=at,
        held=held,
        owner=owner,
        hit=hit,
        placement=placement,
        expected_dy="-1.0",
        after_dy="-1.0",
        after_lane="anchored_full_block",
    )
    row.update({
        "afterState": after_state,
        "afterStoredDy": "-1.0",
        "afterStoredDyBits": "bff0000000000000",
        "pairPos": pair_pos,
        "pairPart": pair_part,
        "pairState": pair_state,
        "pairAfterDy": "-1.0",
        "pairStoredDy": "-1.0",
        "pairStoredDyBits": "bff0000000000000",
    })
    return row


def early_none_action(action_id=1, *, origin="PLAYER_AUTHORED", success=False):
    row = action(
        action_id,
        "server",
        origin=origin,
        held="minecraft:stone",
        owner="30, 64, 30",
        hit="30.500000,65.000000,30.500000",
        placement="none",
        expected_dy="unknown",
        after_dy="none",
        expected_lane="unknown",
        after_lane="none",
        marker="none",
        result="Success[swingSource=CLIENT]" if success else "Fail[]",
    )
    row.update({
        "actionType": "place_block" if success else "use_block",
        "placeBeforeState": "none",
        "placeBeforeDy": "none",
        "afterState": "none",
        "afterDy": "none",
        "afterLaneKind": "none",
        "afterPersistentLoweredSlabCarrier": "none",
        **{field: "none" for field in C3_PAIR_FIELDS},
    })
    return row


def c3_door_and_bed_rows():
    return [
        c3_action(1, "client", at="2026-07-11T07:00:00.000000Z"),
        c3_action(2, "server", at="2026-07-11T07:00:00.050000Z"),
        c3_action(
            3,
            "client",
            family="bed",
            at="2026-07-11T07:00:00.100000Z",
            placement="20, 64, 21",
            pair_pos="21, 64, 21",
        ),
        c3_action(
            4,
            "server",
            family="bed",
            at="2026-07-11T07:00:00.150000Z",
            placement="20, 64, 21",
            pair_pos="21, 64, 21",
        ),
    ]


def live_c3_door_and_bed_rows():
    rows = c3_door_and_bed_rows()
    for row in rows:
        row.update({
            "expectedAfterDy": "unknown",
            "expectedAfterLaneKind": "unknown",
            "expectedResult": "unknown",
            "marker": "none",
        })
    return rows


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
        if "LIVE_PLACEMENT_UNCLASSIFIED_FAILURE" in marker:
            counters["placementUnclassifiedFailureRows"] += 1
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


def schema6_summary_for(rows, terminal):
    counters = summary_for(
        rows,
        overrides={
            "sentinelArmedTotal": 0,
            "sentinelSamplePasses": 0,
        },
    )
    counters.update({
        key: 0 for key in SCHEMA6_SUMMARY_KEYS if key not in counters
    })
    for row in rows:
        verdict_key = {
            "GREEN": "placementVerdictGreenRows",
            "RED": "placementVerdictRedRows",
            "INCONCLUSIVE": "placementVerdictInconclusiveRows",
            "EXPECTED_REFUSAL": "placementVerdictExpectedRefusalRows",
            "UNCLASSIFIED_FAILURE": "placementVerdictUnclassifiedFailureRows",
        }[row["finalVerdict"]]
        counters[verdict_key] += 1

    counters["logicalAttemptRows"] = 1
    status_key = {
        "MERGED_CLIENT_SERVER": "mergedClientServerAttemptRows",
        "AUTO_PROXY": "autoProxyLogicalAttemptRows",
        "SERVER_ONLY": "serverOnlyLogicalAttemptRows",
        "CLIENT_ONLY": "clientOnlyLogicalAttemptRows",
    }[terminal["attemptStatus"]]
    counters[status_key] = 1
    if terminal["playerProof"] == "PRESENT":
        counters["playerProofLogicalAttemptRows"] = 1
    verdict_key = {
        "GREEN": "logicalAttemptVerdictGreenRows",
        "RED": "logicalAttemptVerdictRedRows",
        "INCONCLUSIVE": "logicalAttemptVerdictInconclusiveRows",
        "EXPECTED_REFUSAL": "logicalAttemptVerdictExpectedRefusalRows",
        "UNCLASSIFIED_FAILURE": "logicalAttemptVerdictUnclassifiedFailureRows",
    }[terminal["finalVerdict"]]
    counters[verdict_key] = 1
    if terminal["playerProof"] == "PRESENT" and terminal["finalVerdict"] == "GREEN":
        counters["playerProofGreenLogicalAttemptRows"] = 1
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
    mismatch_header=None,
    java_command="launcher --accessToken [REDACTED] --uuid [REDACTED]",
    recorder_version="26.2-recorder-truth-v3-origin",
    omit_summary_keys=None,
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
        "recorderVersion": recorder_version,
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

    mismatch_lines = ["\t".join(mismatch_header or MISMATCH_HEADER)]
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
    omitted = set(omit_summary_keys or ())
    summary = "# Slabbed Live Cursor Intent Recorder Summary\n\n" + "".join(
        f"{key}={counters[key]}\n" for key in SUMMARY_KEYS if key not in omitted
    )
    (root / "summary.md").write_text(summary, encoding="utf-8")
    return root


def schema6_action(
    action_id,
    side,
    *,
    logical_attempt_id,
    phase,
    player_proof,
    origin="PLAYER_AUTHORED",
    at="2026-07-11T07:00:00.000000Z",
):
    row = action(
        action_id,
        side,
        origin=origin,
        at=at,
        expected_dy="-1.000000",
        expected_lane="unknown",
        marker="none",
        result="SUCCESS",
    )
    row.update({
        "afterStoredDy": "none",
        "afterStoredDyBits": "none",
        "pairPos": "none",
        "pairPart": "none",
        "pairState": "none",
        "pairAfterDy": "none",
        "pairStoredDy": "none",
        "pairStoredDyBits": "none",
        "logicalAttemptId": logical_attempt_id,
        "phase": phase,
        "playerProof": player_proof,
        "expectedResult": "unknown",
        "placementRoute": "TOP_SEAT",
        "landingAuthority": "CANONICAL_STORED_DY",
        "rigCaseId": "schema6-adapter-fixture",
        "intentDy": "-1.000000",
        "expectedRefusalReason": "unknown",
        "actualRefusalReason": "unknown",
        "failureClasses": "none",
        "marker": "none",
    })
    if phase == "CLIENT_PREDICTION":
        row.update({
            "modelDy": "-1.000000",
            "collisionDy": "-1.000000",
            "raycastDy": "-1.000000",
            "outlineDy": "-1.000000",
            "expectedSupportPlane": "64.000000",
            "actualContactPlane": "64.000000",
            "seatError": "0.000000",
            "finalVerdict": "INCONCLUSIVE",
            "placedVerdict": "PASS",
            "anchorVerdict": "MISSING",
            "modelVerdict": "PASS",
            "collisionVerdict": "PASS",
            "raycastVerdict": "PASS",
            "outlineVerdict": "PASS",
            "stabilityVerdict": "NOT_RUN",
            "storedDy": "unknown",
            "missingRequiredComponents": "ANCHOR,STABILITY",
            "verdictMarker": "LIVE_PLACEMENT_VERDICT_INCONCLUSIVE",
        })
    elif phase == "SERVER_AUTHORITY":
        row.update({
            "afterStoredDy": "-1.000000",
            "finalVerdict": "INCONCLUSIVE",
            "placedVerdict": "PASS",
            "anchorVerdict": "PASS",
            "modelVerdict": "MISSING",
            "collisionVerdict": "MISSING",
            "raycastVerdict": "MISSING",
            "outlineVerdict": "MISSING",
            "stabilityVerdict": "PASS",
            "storedDy": "-1.000000",
            "modelDy": "unknown",
            "collisionDy": "unknown",
            "raycastDy": "unknown",
            "outlineDy": "unknown",
            "expectedSupportPlane": "unknown",
            "actualContactPlane": "unknown",
            "seatError": "unknown",
            "missingRequiredComponents": "MODEL,COLLISION,RAYCAST,OUTLINE",
            "verdictMarker": "LIVE_PLACEMENT_VERDICT_INCONCLUSIVE",
        })
    elif phase == "AUTO_PROXY":
        row.update({
            "afterStoredDy": "-1.000000",
            "modelDy": "-1.000000",
            "collisionDy": "-1.000000",
            "raycastDy": "-1.000000",
            "outlineDy": "-1.000000",
            "expectedSupportPlane": "64.000000",
            "actualContactPlane": "64.000000",
            "seatError": "0.000000",
            "finalVerdict": "GREEN",
            **{field: "PASS" for field in COMPONENT_VERDICT_FIELDS},
            "storedDy": "-1.000000",
            "missingRequiredComponents": "none",
            "verdictMarker": "LIVE_PLACEMENT_VERDICT_GREEN",
        })
    else:
        raise ValueError("unknown schema-6 phase: %s" % phase)
    return row


def placement_attempt(
    logical_attempt_id,
    *,
    attempt_status,
    client_action_id,
    server_action_id,
    player_proof,
    at,
):
    return {
        "type": "placement_attempt",
        "rowId": "attempt:" + logical_attempt_id,
        "recordedAt": at,
        "logicalAttemptId": logical_attempt_id,
        "attemptStatus": attempt_status,
        "terminal": "true",
        "clientActionId": client_action_id,
        "serverActionId": server_action_id,
        "actionCount": "2" if client_action_id != "none" and server_action_id != "none" else "1",
        "playerProof": player_proof,
        "actionType": "place_block",
        "heldItem": "minecraft:stone_slab",
        "clickedOwnerPos": "10, 64, 10",
        "clickedFace": "south",
        "placementPos": "10, 64, 11",
        "rigCaseId": "schema6-adapter-fixture",
        "placementRoute": "TOP_SEAT",
        "landingAuthority": "CANONICAL_STORED_DY",
        "expectedAfterDy": "-1.000000",
        "intentDy": "-1.000000",
        "clickedOwnerLaneKind": "anchored_full_block",
        "beforeDy": "-1.000000",
        "actualResult": "SUCCESS",
        "afterDy": "-1.000000",
        "afterState": "Block{minecraft:stone_slab}[type=top,waterlogged=false]",
        "afterLaneKind": "anchored_full_block",
        "stabilityVerdict": "PASS",
        "afterStoredDy": "-1.000000",
        "modelDy": "-1.000000",
        "collisionDy": "-1.000000",
        "raycastDy": "-1.000000",
        "outlineDy": "-1.000000",
        "expectedSupportPlane": "64.000000",
        "actualContactPlane": "64.000000",
        "seatError": "0.000000",
        "finalVerdict": "GREEN",
        "placedVerdict": "PASS",
        "anchorVerdict": "PASS",
        "modelVerdict": "PASS",
        "collisionVerdict": "PASS",
        "raycastVerdict": "PASS",
        "outlineVerdict": "PASS",
        "storedDy": "-1.000000",
        "expectedRefusalReason": "unknown",
        "actualRefusalReason": "unknown",
        "missingRequiredComponents": "none",
        "failureClasses": "none",
        "verdictMarker": "LIVE_PLACEMENT_VERDICT_GREEN",
        "marker": "LIVE_PLACEMENT_VERDICT_GREEN",
    }


def write_schema6_fixture(root, *, rows, terminal, mismatches=None):
    recorder = write_fixture(
        root,
        rows=rows,
        mismatches=mismatches,
        schema="6",
        run_id=SCHEMA6_RUN_ID,
        actions_header=SCHEMA6_ACTIONS_HEADER,
        mismatch_header=SCHEMA6_MISMATCH_HEADER,
        recorder_version=SCHEMA6_RECORDER_VERSION,
    )
    manifest_path = recorder / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    exact_manifest = {}
    for key, value in manifest.items():
        exact_manifest[key] = value
        if key == "actionOriginContract":
            exact_manifest["placementVerdictContract"] = PLACEMENT_VERDICT_CONTRACT
            exact_manifest["logicalAttemptContract"] = LOGICAL_ATTEMPT_CONTRACT
    manifest_path.write_text(json.dumps(exact_manifest) + "\n", encoding="utf-8")
    with (recorder / "session.jsonl").open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(terminal, separators=(",", ":")) + "\n")
    counters = schema6_summary_for(rows, terminal)
    summary = "# Slabbed Live Cursor Intent Recorder Summary\n\n" + "".join(
        f"{key}={counters[key]}\n" for key in SCHEMA6_SUMMARY_KEYS
    )
    (recorder / "summary.md").write_text(summary, encoding="utf-8")
    return recorder


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

    def test_live_shaped_numeric_height_and_unclassified_failure_rows_are_adapter_red(self):
        rows = [
            action(
                1,
                "server",
                origin="AUTO_USEON_PROXY",
                held="minecraft:bamboo_button",
                expected_dy="-1.500000",
                after_dy="-0.500000",
                marker="none",
            ),
            action(
                2,
                "server",
                origin="AUTO_USEON_PROXY",
                held="minecraft:flower_pot",
                expected_dy="-1.500000",
                after_dy="-1.000000",
                marker="none",
            ),
            action(
                3,
                "server",
                origin="AUTO_USEON_PROXY",
                held="minecraft:oak_fence",
                expected_dy="0.000000",
                after_dy="-0.500000",
                marker="none",
            ),
            action(
                4,
                "server",
                origin="AUTO_USEON_PROXY",
                held="minecraft:conduit",
                expected_dy="-0.500000",
                after_dy="0.000000",
                marker="none",
            ),
            action(
                5,
                "server",
                origin="AUTO_USEON_PROXY",
                held="minecraft:stone",
                expected_dy="unknown",
                expected_lane="unknown",
                marker="none",
                result="Fail[]",
            ),
        ]
        triage = adapter.analyze(write_fixture(self.root / "recorder", rows=rows))
        self.assertEqual("RED", triage["verdict"]["status"])
        self.assertTrue(triage["verdict"]["hasAdapterRed"])
        self.assertEqual(
            4,
            triage["counters"]["adapter"]["ADAPTER_EXPECTED_DY_MISMATCH"],
        )
        self.assertEqual(
            1,
            triage["counters"]["adapter"]["ADAPTER_UNCLASSIFIED_PLACEMENT_FAILURE"],
        )
        self.assertEqual(
            [1, 2, 3, 4, 5],
            [row["actionId"] for row in triage["autoUseOnProxy"]["redRows"]],
        )
        self.assertEqual([], triage["autoUseOnProxy"]["sample"])

    def test_producer_unclassified_failure_marker_reconciles_and_is_red(self):
        row = action(
            1,
            "server",
            origin="AUTO_USEON_PROXY",
            expected_dy="unknown",
            expected_lane="unknown",
            marker="LIVE_PLACEMENT_UNCLASSIFIED_FAILURE",
            result="Fail[]",
        )
        mismatch = [(
            "action",
            "1",
            "LIVE_PLACEMENT_UNCLASSIFIED_FAILURE",
            "10, 64, 10",
            "minecraft:stone_slab",
        )]
        triage = adapter.analyze(write_fixture(
            self.root / "recorder",
            rows=[row],
            mismatches=mismatch,
            recorder_version=C4_RECORDER_VERSION,
        ))
        self.assertEqual("RED", triage["verdict"]["status"])
        self.assertEqual(1, triage["counters"]["producer"]["placementUnclassifiedFailureRows"])
        self.assertEqual(1, triage["counters"]["derived"]["placementUnclassifiedFailureRows"])
        self.assertEqual(
            0,
            triage["counters"]["adapter"]["ADAPTER_UNCLASSIFIED_PLACEMENT_FAILURE"],
        )

    def test_producer_early_none_failure_mismatch_is_valid_red(self):
        row = early_none_action()
        row["marker"] = "LIVE_PLACEMENT_UNCLASSIFIED_FAILURE"
        mismatch = [(
            "action",
            "1",
            "LIVE_PLACEMENT_UNCLASSIFIED_FAILURE",
            "30, 64, 30",
            "minecraft:stone",
        )]
        triage = adapter.analyze(write_fixture(
            self.root / "recorder",
            rows=[row],
            mismatches=mismatch,
            actions_header=C3_ACTIONS_HEADER,
            recorder_version=C4_RECORDER_VERSION,
        ))
        self.assertEqual("RED", triage["verdict"]["status"])
        self.assertEqual("none", triage["mismatches"][0]["context"]["placementPos"])
        self.assertEqual(1, triage["counters"]["producer"]["placementUnclassifiedFailureRows"])

    def test_legacy_summary_without_failure_counter_is_accepted_and_identified(self):
        triage = adapter.analyze(write_fixture(
            self.root / "recorder",
            omit_summary_keys={"placementUnclassifiedFailureRows"},
        ))
        self.assertEqual(
            ["placementUnclassifiedFailureRows"],
            triage["counters"]["compatibility"]["legacyMissingProducerCounters"],
        )
        self.assertEqual(0, triage["counters"]["producer"]["placementUnclassifiedFailureRows"])

    def test_c4_summary_requires_failure_counter(self):
        recorder = write_fixture(
            self.root / "recorder",
            recorder_version=C4_RECORDER_VERSION,
            omit_summary_keys={"placementUnclassifiedFailureRows"},
        )
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(recorder)

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


class Schema6LogicalAttemptTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    @staticmethod
    def _set_absent_representation(row, field, representation):
        if representation == "present":
            return
        if representation == "omitted":
            row.pop(field, None)
        else:
            row[field] = representation

    def _schema6_after_evidence_fixture(
        self,
        *,
        final_verdict,
        field,
        client_representation,
        server_representation,
        suffix,
    ):
        logical_attempt_id = SCHEMA6_RUN_ID + "-attempt-1"
        rows = [
            schema6_action(
                1,
                "client",
                logical_attempt_id=logical_attempt_id,
                phase="CLIENT_PREDICTION",
                player_proof="PRESENT",
            ),
            schema6_action(
                2,
                "server",
                logical_attempt_id=logical_attempt_id,
                phase="SERVER_AUTHORITY",
                player_proof="PRESENT",
                at="2026-07-11T07:00:00.050000Z",
            ),
        ]
        terminal = placement_attempt(
            logical_attempt_id,
            attempt_status="MERGED_CLIENT_SERVER",
            client_action_id="1",
            server_action_id="2",
            player_proof="PRESENT",
            at="2026-07-11T07:00:00.060000Z",
        )
        if final_verdict == "EXPECTED_REFUSAL":
            for row in rows:
                row.update({
                    "expectedResult": "MUST_REFUSE_VANILLA",
                    "actualResult": "Fail[BLOCKED_BY_VANILLA]",
                    "expectedRefusalReason": "BLOCKED_BY_VANILLA",
                    "actualRefusalReason": "BLOCKED_BY_VANILLA",
                    "finalVerdict": "EXPECTED_REFUSAL",
                    **{
                        component_field: "NOT_APPLICABLE"
                        for component_field in COMPONENT_VERDICT_FIELDS
                    },
                    "missingRequiredComponents": "none",
                    "failureClasses": "none",
                    "verdictMarker": "LIVE_PLACEMENT_VERDICT_EXPECTED_REFUSAL",
                    "marker": "none",
                })
            terminal.update({
                "expectedResult": "MUST_REFUSE_VANILLA",
                "actualResult": "Fail[BLOCKED_BY_VANILLA]",
                "expectedRefusalReason": "BLOCKED_BY_VANILLA",
                "actualRefusalReason": "BLOCKED_BY_VANILLA",
                "finalVerdict": "EXPECTED_REFUSAL",
                **{
                    component_field: "NOT_APPLICABLE"
                    for component_field in COMPONENT_VERDICT_FIELDS
                },
                "missingRequiredComponents": "none",
                "failureClasses": "none",
                "verdictMarker": "LIVE_PLACEMENT_VERDICT_EXPECTED_REFUSAL",
                "marker": "LIVE_PLACEMENT_VERDICT_EXPECTED_REFUSAL",
            })

        self._set_absent_representation(
            rows[0], field, client_representation
        )
        self._set_absent_representation(
            rows[1], field, server_representation
        )
        both_absent = (
            client_representation in {"omitted", "none", "unknown"}
            and server_representation in {"omitted", "none", "unknown"}
        )
        if both_absent:
            terminal.pop(field, None)

        return adapter.analyze(write_schema6_fixture(
            self.root / suffix,
            rows=rows,
            terminal=terminal,
        ))

    def _assert_schema6_absent_after_evidence_parity(self, final_verdict):
        absent_pairs = [
            ("omitted", "none"),
            ("none", "omitted"),
            ("omitted", "unknown"),
            ("unknown", "omitted"),
            ("none", "unknown"),
            ("unknown", "none"),
        ]
        expected_status = (
            "EXPLICIT_GREEN"
            if final_verdict == "GREEN"
            else "OBSERVED_UNCLASSIFIED"
        )
        for field, split_marker in (
            ("afterDy", "ADAPTER_SIDE_DY_SPLIT"),
            ("afterState", "ADAPTER_SIDE_STATE_SPLIT"),
        ):
            for client_representation, server_representation in absent_pairs:
                label = (
                    f"{final_verdict.lower()}-{field}-"
                    f"{client_representation}-{server_representation}"
                )
                with self.subTest(
                    verdict=final_verdict,
                    field=field,
                    client=client_representation,
                    server=server_representation,
                ):
                    triage = self._schema6_after_evidence_fixture(
                        final_verdict=final_verdict,
                        field=field,
                        client_representation=client_representation,
                        server_representation=server_representation,
                        suffix=label,
                    )
                    pair = triage["playerAuthored"]["pairs"][0]
                    self.assertEqual(expected_status, triage["verdict"]["status"])
                    self.assertNotIn(split_marker, pair["redMarkers"])

            for absent_representation in ("omitted", "none", "unknown"):
                label = (
                    f"{final_verdict.lower()}-{field}-"
                    f"{absent_representation}-present"
                )
                with self.subTest(
                    verdict=final_verdict,
                    field=field,
                    client=absent_representation,
                    server="present",
                ):
                    triage = self._schema6_after_evidence_fixture(
                        final_verdict=final_verdict,
                        field=field,
                        client_representation=absent_representation,
                        server_representation="present",
                        suffix=label,
                    )
                    pair = triage["playerAuthored"]["pairs"][0]
                    self.assertEqual("RED", triage["verdict"]["status"])
                    self.assertIn(split_marker, pair["redMarkers"])

    def test_schema6_green_absent_after_evidence_representation_parity(self):
        self._assert_schema6_absent_after_evidence_parity("GREEN")

    def test_schema6_expected_refusal_absent_after_evidence_representation_parity(self):
        self._assert_schema6_absent_after_evidence_parity("EXPECTED_REFUSAL")

    def test_schema6_v8_player_terminal_merges_client_server_attempt(self):
        logical_attempt_id = SCHEMA6_RUN_ID + "-attempt-1"
        rows = [
            schema6_action(
                1,
                "client",
                logical_attempt_id=logical_attempt_id,
                phase="CLIENT_PREDICTION",
                player_proof="PRESENT",
            ),
            schema6_action(
                2,
                "server",
                logical_attempt_id=logical_attempt_id,
                phase="SERVER_AUTHORITY",
                player_proof="PRESENT",
                at="2026-07-11T07:00:00.050000Z",
            ),
        ]
        terminal = placement_attempt(
            logical_attempt_id,
            attempt_status="MERGED_CLIENT_SERVER",
            client_action_id="1",
            server_action_id="2",
            player_proof="PRESENT",
            at="2026-07-11T07:00:00.060000Z",
        )

        triage = adapter.analyze(write_schema6_fixture(
            self.root / "player",
            rows=rows,
            terminal=terminal,
        ))

        self.assertEqual("EXPLICIT_GREEN", triage["verdict"]["status"])
        self.assertEqual("EXPLICIT_GREEN", triage["verdict"]["playerProof"])
        self.assertEqual(1, triage["playerAuthored"]["pairCount"])
        pair = triage["playerAuthored"]["pairs"][0]
        self.assertEqual([1, 2], [pair["clientActionId"], pair["serverActionId"]])
        self.assertEqual("EXPLICIT_GREEN", pair["verdict"])
        self.assertNotIn("ADAPTER_C3_SIDE_PAIR_FIELD_SPLIT", pair["redMarkers"])
        self.assertEqual(
            [logical_attempt_id, logical_attempt_id],
            [
                pair["clientRow"]["logicalAttemptId"],
                pair["serverRow"]["logicalAttemptId"],
            ],
        )
        self.assertEqual(
            ["CLIENT_PREDICTION", "SERVER_AUTHORITY"],
            [pair["clientRow"]["phase"], pair["serverRow"]["phase"]],
        )
        self.assertEqual(
            ["PRESENT", "PRESENT"],
            [pair["clientRow"]["playerProof"], pair["serverRow"]["playerProof"]],
        )
        self.assertTrue(all(
            row["marker"] == "none" and row["expectedAfterDy"] == "-1.000000"
            for row in (pair["clientRow"], pair["serverRow"])
        ))
        self.assertEqual(
            ["INCONCLUSIVE", "INCONCLUSIVE"],
            [
                pair["clientRow"]["finalVerdict"],
                pair["serverRow"]["finalVerdict"],
            ],
        )
        self.assertEqual(logical_attempt_id, pair["logicalAttemptId"])
        self.assertEqual(
            "attempt:" + logical_attempt_id,
            pair["terminalRowId"],
        )
        attempt = triage["logicalAttempts"]["rows"][0]
        self.assertEqual("MERGED_CLIENT_SERVER", attempt["attemptStatus"])
        self.assertEqual("GREEN", attempt["finalVerdict"])
        self.assertEqual("PRESENT", attempt["playerProof"])
        self.assertTrue(attempt["rawActionsCrossChecked"])
        self.assertTrue(all(
            attempt[field] == "PASS" for field in COMPONENT_VERDICT_FIELDS
        ))
        self.assertEqual(0, triage["autoUseOnProxy"]["rowCount"])

    def test_schema6_accepts_java_rows_without_optional_player_or_hit_vector(self):
        logical_attempt_id = SCHEMA6_RUN_ID + "-attempt-1"
        rows = [
            schema6_action(
                1,
                "client",
                logical_attempt_id=logical_attempt_id,
                phase="CLIENT_PREDICTION",
                player_proof="PRESENT",
            ),
            schema6_action(
                2,
                "server",
                logical_attempt_id=logical_attempt_id,
                phase="SERVER_AUTHORITY",
                player_proof="PRESENT",
                at="2026-07-11T07:00:00.050000Z",
            ),
        ]
        for row in rows:
            row.pop("player")
            row.pop("clickedHitVec")
        terminal = placement_attempt(
            logical_attempt_id,
            attempt_status="MERGED_CLIENT_SERVER",
            client_action_id="1",
            server_action_id="2",
            player_proof="PRESENT",
            at="2026-07-11T07:00:00.060000Z",
        )

        triage = adapter.analyze(write_schema6_fixture(
            self.root / "missing-optional-player-hit",
            rows=rows,
            terminal=terminal,
        ))

        self.assertEqual("EXPLICIT_GREEN", triage["verdict"]["status"])
        self.assertEqual("EXPLICIT_GREEN", triage["verdict"]["playerProof"])
        pair = triage["playerAuthored"]["pairs"][0]
        self.assertNotIn("player", pair["clientRow"])
        self.assertNotIn("clickedHitVec", pair["clientRow"])
        self.assertNotIn("player", pair["serverRow"])
        self.assertNotIn("clickedHitVec", pair["serverRow"])

    def test_schema6_expected_refusal_pair_is_not_adapter_red(self):
        logical_attempt_id = SCHEMA6_RUN_ID + "-attempt-1"
        rows = [
            schema6_action(
                1,
                "client",
                logical_attempt_id=logical_attempt_id,
                phase="CLIENT_PREDICTION",
                player_proof="PRESENT",
            ),
            schema6_action(
                2,
                "server",
                logical_attempt_id=logical_attempt_id,
                phase="SERVER_AUTHORITY",
                player_proof="PRESENT",
                at="2026-07-11T07:00:00.050000Z",
            ),
        ]
        for row in rows:
            row.update({
                "expectedResult": "MUST_REFUSE_VANILLA",
                "actualResult": "Fail[BLOCKED_BY_VANILLA]",
                "expectedRefusalReason": "BLOCKED_BY_VANILLA",
                "actualRefusalReason": "BLOCKED_BY_VANILLA",
                "finalVerdict": "EXPECTED_REFUSAL",
                **{field: "NOT_APPLICABLE" for field in COMPONENT_VERDICT_FIELDS},
                "missingRequiredComponents": "none",
                "failureClasses": "none",
                "verdictMarker": "LIVE_PLACEMENT_VERDICT_EXPECTED_REFUSAL",
                "marker": "none",
            })
        terminal = placement_attempt(
            logical_attempt_id,
            attempt_status="MERGED_CLIENT_SERVER",
            client_action_id="1",
            server_action_id="2",
            player_proof="PRESENT",
            at="2026-07-11T07:00:00.060000Z",
        )
        terminal.update({
            "expectedResult": "MUST_REFUSE_VANILLA",
            "actualResult": "Fail[BLOCKED_BY_VANILLA]",
            "expectedRefusalReason": "BLOCKED_BY_VANILLA",
            "actualRefusalReason": "BLOCKED_BY_VANILLA",
            "finalVerdict": "EXPECTED_REFUSAL",
            **{field: "NOT_APPLICABLE" for field in COMPONENT_VERDICT_FIELDS},
            "missingRequiredComponents": "none",
            "failureClasses": "none",
            "verdictMarker": "LIVE_PLACEMENT_VERDICT_EXPECTED_REFUSAL",
            "marker": "LIVE_PLACEMENT_VERDICT_EXPECTED_REFUSAL",
        })

        triage = adapter.analyze(write_schema6_fixture(
            self.root / "expected-refusal",
            rows=rows,
            terminal=terminal,
        ))

        self.assertEqual("OBSERVED_UNCLASSIFIED", triage["verdict"]["status"])
        self.assertEqual("OBSERVED_UNCLASSIFIED", triage["verdict"]["playerProof"])
        self.assertFalse(triage["verdict"]["hasRed"])
        pair = triage["playerAuthored"]["pairs"][0]
        self.assertEqual("OBSERVED_UNCLASSIFIED", pair["verdict"])
        self.assertNotIn("ADAPTER_PLACEMENT_FAILED", pair["redMarkers"])
        self.assertNotIn(
            "ADAPTER_UNCLASSIFIED_PLACEMENT_FAILURE",
            pair["redMarkers"],
        )

    def test_schema6_green_allows_missing_after_dy_and_after_state(self):
        logical_attempt_id = SCHEMA6_RUN_ID + "-attempt-1"
        rows = [
            schema6_action(
                1,
                "client",
                logical_attempt_id=logical_attempt_id,
                phase="CLIENT_PREDICTION",
                player_proof="PRESENT",
            ),
            schema6_action(
                2,
                "server",
                logical_attempt_id=logical_attempt_id,
                phase="SERVER_AUTHORITY",
                player_proof="PRESENT",
                at="2026-07-11T07:00:00.050000Z",
            ),
        ]
        for row in rows:
            row.pop("afterDy")
            row.pop("afterState")
        terminal = placement_attempt(
            logical_attempt_id,
            attempt_status="MERGED_CLIENT_SERVER",
            client_action_id="1",
            server_action_id="2",
            player_proof="PRESENT",
            at="2026-07-11T07:00:00.060000Z",
        )
        terminal.pop("afterDy")
        terminal.pop("afterState")

        triage = adapter.analyze(write_schema6_fixture(
            self.root / "green-without-after-dy-or-state",
            rows=rows,
            terminal=terminal,
        ))

        self.assertEqual("EXPLICIT_GREEN", triage["verdict"]["status"])
        self.assertEqual("EXPLICIT_GREEN", triage["verdict"]["playerProof"])
        attempt = triage["logicalAttempts"]["rows"][0]
        self.assertEqual("GREEN", attempt["finalVerdict"])
        self.assertEqual("PASS", attempt["placedVerdict"])
        self.assertNotIn("afterDy", attempt)
        self.assertNotIn("afterState", attempt)

    def test_schema6_uses_java_attempt_key_when_hit_vectors_differ(self):
        logical_attempt_id = SCHEMA6_RUN_ID + "-attempt-1"
        rows = [
            schema6_action(
                1,
                "client",
                logical_attempt_id=logical_attempt_id,
                phase="CLIENT_PREDICTION",
                player_proof="PRESENT",
            ),
            schema6_action(
                2,
                "server",
                logical_attempt_id=logical_attempt_id,
                phase="SERVER_AUTHORITY",
                player_proof="PRESENT",
                at="2026-07-11T07:00:00.050000Z",
            ),
        ]
        rows[1]["clickedHitVec"] = "10.750000,64.500000,11.000000"
        terminal = placement_attempt(
            logical_attempt_id,
            attempt_status="MERGED_CLIENT_SERVER",
            client_action_id="1",
            server_action_id="2",
            player_proof="PRESENT",
            at="2026-07-11T07:00:00.060000Z",
        )

        triage = adapter.analyze(write_schema6_fixture(
            self.root / "producer-key-ignores-hit-vector",
            rows=rows,
            terminal=terminal,
        ))

        self.assertEqual("EXPLICIT_GREEN", triage["verdict"]["status"])
        self.assertEqual(1, triage["playerAuthored"]["pairCount"])
        pair = triage["playerAuthored"]["pairs"][0]
        self.assertNotEqual(
            pair["clientRow"]["clickedHitVec"],
            pair["serverRow"]["clickedHitVec"],
        )

    def test_schema6_java_numeric_tolerance_does_not_invent_a_raw_conflict(self):
        logical_attempt_id = SCHEMA6_RUN_ID + "-attempt-1"
        rows = [
            schema6_action(
                1,
                "client",
                logical_attempt_id=logical_attempt_id,
                phase="CLIENT_PREDICTION",
                player_proof="PRESENT",
            ),
            schema6_action(
                2,
                "server",
                logical_attempt_id=logical_attempt_id,
                phase="SERVER_AUTHORITY",
                player_proof="PRESENT",
                at="2026-07-11T07:00:00.050000Z",
            ),
        ]
        rows[0]["expectedAfterDy"] = "-1.0"
        terminal = placement_attempt(
            logical_attempt_id,
            attempt_status="MERGED_CLIENT_SERVER",
            client_action_id="1",
            server_action_id="2",
            player_proof="PRESENT",
            at="2026-07-11T07:00:00.060000Z",
        )

        triage = adapter.analyze(write_schema6_fixture(
            self.root / "numeric-equivalent-evidence",
            rows=rows,
            terminal=terminal,
        ))

        self.assertEqual("EXPLICIT_GREEN", triage["verdict"]["status"])
        self.assertEqual("GREEN", triage["logicalAttempts"]["rows"][0]["finalVerdict"])

    def test_schema6_accepts_partial_valid_collision_green_without_route_metadata(self):
        logical_attempt_id = SCHEMA6_RUN_ID + "-attempt-1"
        rows = [
            schema6_action(
                1,
                "client",
                logical_attempt_id=logical_attempt_id,
                phase="CLIENT_PREDICTION",
                player_proof="PRESENT",
            ),
            schema6_action(
                2,
                "server",
                logical_attempt_id=logical_attempt_id,
                phase="SERVER_AUTHORITY",
                player_proof="PRESENT",
                at="2026-07-11T07:00:00.050000Z",
            ),
        ]
        for row in rows:
            row["placementRoute"] = "unknown"
            row["landingAuthority"] = "unknown"
            row["rigCaseId"] = "none"
        rows[0]["collisionDy"] = "unknown"
        rows[0]["expectedSupportPlane"] = "unknown"
        rows[0]["actualContactPlane"] = "unknown"
        terminal = placement_attempt(
            logical_attempt_id,
            attempt_status="MERGED_CLIENT_SERVER",
            client_action_id="1",
            server_action_id="2",
            player_proof="PRESENT",
            at="2026-07-11T07:00:00.060000Z",
        )
        terminal.update({
            "collisionDy": "unknown",
            "expectedSupportPlane": "unknown",
            "actualContactPlane": "unknown",
            "placementRoute": "unknown",
            "landingAuthority": "unknown",
            "rigCaseId": "unknown",
        })

        triage = adapter.analyze(write_schema6_fixture(
            self.root / "partial-collision-green",
            rows=rows,
            terminal=terminal,
        ))

        self.assertEqual("EXPLICIT_GREEN", triage["verdict"]["status"])
        attempt = triage["logicalAttempts"]["rows"][0]
        self.assertEqual("PASS", attempt["collisionVerdict"])
        self.assertEqual("0.000000", attempt["seatError"])
        self.assertEqual("unknown", attempt["collisionDy"])

    def test_schema6_rejects_forged_red_without_evidence_backed_failure(self):
        logical_attempt_id = SCHEMA6_RUN_ID + "-attempt-1"
        rows = [
            schema6_action(
                1,
                "client",
                logical_attempt_id=logical_attempt_id,
                phase="CLIENT_PREDICTION",
                player_proof="PRESENT",
            ),
            schema6_action(
                2,
                "server",
                logical_attempt_id=logical_attempt_id,
                phase="SERVER_AUTHORITY",
                player_proof="PRESENT",
                at="2026-07-11T07:00:00.050000Z",
            ),
        ]
        terminal = placement_attempt(
            logical_attempt_id,
            attempt_status="MERGED_CLIENT_SERVER",
            client_action_id="1",
            server_action_id="2",
            player_proof="PRESENT",
            at="2026-07-11T07:00:00.060000Z",
        )
        terminal.update({
            "finalVerdict": "RED",
            "placedVerdict": "FAIL",
            "failureClasses": "PLACED_ACTION_DY_MISMATCH",
            "verdictMarker": "LIVE_PLACEMENT_VERDICT_RED",
            "marker": "LIVE_PLACEMENT_VERDICT_RED",
        })
        mismatch = [[
            "placement_attempt",
            "attempt:" + logical_attempt_id,
            "LIVE_PLACEMENT_VERDICT_RED",
            "10, 64, 10",
            "minecraft:stone_slab",
            "PLACED_ACTION_DY_MISMATCH",
        ]]

        with self.assertRaisesRegex(
            adapter.IntegrityError,
            "evidence-backed|derived terminal verdict",
        ):
            adapter.analyze(write_schema6_fixture(
                self.root / "forged-red",
                rows=rows,
                terminal=terminal,
                mismatches=mismatch,
            ))

    def test_schema6_v8_green_auto_proxy_terminal_keeps_player_proof_absent(self):
        logical_attempt_id = SCHEMA6_RUN_ID + "-attempt-1"
        rows = [
            schema6_action(
                1,
                "server",
                logical_attempt_id=logical_attempt_id,
                phase="AUTO_PROXY",
                player_proof="ABSENT",
                origin="AUTO_USEON_PROXY",
            ),
        ]
        terminal = placement_attempt(
            logical_attempt_id,
            attempt_status="AUTO_PROXY",
            client_action_id="none",
            server_action_id="1",
            player_proof="ABSENT",
            at="2026-07-11T07:00:00.010000Z",
        )

        triage = adapter.analyze(write_schema6_fixture(
            self.root / "auto-proxy",
            rows=rows,
            terminal=terminal,
        ))

        self.assertEqual("NO_PLAYER_EVIDENCE", triage["verdict"]["status"])
        self.assertEqual("ABSENT", triage["verdict"]["playerProof"])
        self.assertFalse(triage["verdict"]["hasRed"])
        self.assertEqual(0, triage["playerAuthored"]["pairCount"])
        self.assertEqual(1, triage["autoUseOnProxy"]["rowCount"])
        attempt = triage["logicalAttempts"]["rows"][0]
        self.assertEqual("AUTO_PROXY", attempt["attemptStatus"])
        self.assertEqual("GREEN", attempt["finalVerdict"])
        self.assertEqual("ABSENT", attempt["playerProof"])
        self.assertTrue(attempt["rawActionsCrossChecked"])

    def test_schema6_rejects_terminal_evidence_missing_from_selected_raw_rows(self):
        logical_attempt_id = SCHEMA6_RUN_ID + "-attempt-1"
        rows = [
            schema6_action(
                1,
                "client",
                logical_attempt_id=logical_attempt_id,
                phase="CLIENT_PREDICTION",
                player_proof="PRESENT",
            ),
            schema6_action(
                2,
                "server",
                logical_attempt_id=logical_attempt_id,
                phase="SERVER_AUTHORITY",
                player_proof="PRESENT",
                at="2026-07-11T07:00:00.050000Z",
            ),
        ]
        rows[0]["outlineDy"] = "unknown"
        rows[0]["outlineVerdict"] = "MISSING"
        rows[0]["missingRequiredComponents"] = "ANCHOR,OUTLINE,STABILITY"
        terminal = placement_attempt(
            logical_attempt_id,
            attempt_status="MERGED_CLIENT_SERVER",
            client_action_id="1",
            server_action_id="2",
            player_proof="PRESENT",
            at="2026-07-11T07:00:00.060000Z",
        )

        with self.assertRaisesRegex(
            adapter.IntegrityError,
            "derived terminal verdict/evidence mismatch|terminal/raw evidence mismatch outlineDy",
        ):
            adapter.analyze(write_schema6_fixture(
                self.root / "invented-terminal-outline",
                rows=rows,
                terminal=terminal,
            ))

    def test_schema6_rejects_all_pass_dy_evidence_that_disagrees_with_intent(self):
        logical_attempt_id = SCHEMA6_RUN_ID + "-attempt-1"
        rows = [
            schema6_action(
                1,
                "client",
                logical_attempt_id=logical_attempt_id,
                phase="CLIENT_PREDICTION",
                player_proof="PRESENT",
            ),
            schema6_action(
                2,
                "server",
                logical_attempt_id=logical_attempt_id,
                phase="SERVER_AUTHORITY",
                player_proof="PRESENT",
                at="2026-07-11T07:00:00.050000Z",
            ),
        ]
        for field in ("modelDy", "collisionDy", "raycastDy", "outlineDy"):
            rows[0][field] = "-0.750000"
        rows[1]["afterStoredDy"] = "-0.750000"
        rows[1]["storedDy"] = "-0.750000"
        terminal = placement_attempt(
            logical_attempt_id,
            attempt_status="MERGED_CLIENT_SERVER",
            client_action_id="1",
            server_action_id="2",
            player_proof="PRESENT",
            at="2026-07-11T07:00:00.060000Z",
        )
        terminal["afterStoredDy"] = "-0.750000"
        terminal["storedDy"] = "-0.750000"
        for field in ("modelDy", "collisionDy", "raycastDy", "outlineDy"):
            terminal[field] = "-0.750000"

        with self.assertRaisesRegex(
            adapter.IntegrityError,
            "PASS evidence disagrees with intentDy",
        ):
            adapter.analyze(write_schema6_fixture(
                self.root / "forged-pass-dy",
                rows=rows,
                terminal=terminal,
            ))

    def test_schema6_accepts_java_red_terminal_conflict_outside_attempt_key(self):
        logical_attempt_id = SCHEMA6_RUN_ID + "-attempt-1"
        rows = [
            schema6_action(
                1,
                "client",
                logical_attempt_id=logical_attempt_id,
                phase="CLIENT_PREDICTION",
                player_proof="PRESENT",
            ),
            schema6_action(
                2,
                "server",
                logical_attempt_id=logical_attempt_id,
                phase="SERVER_AUTHORITY",
                player_proof="PRESENT",
                at="2026-07-11T07:00:00.050000Z",
            ),
        ]
        rows[0]["placementRoute"] = "CLIENT_ROUTE"
        rows[1]["placementRoute"] = "SERVER_ROUTE"
        terminal = placement_attempt(
            logical_attempt_id,
            attempt_status="MERGED_CLIENT_SERVER",
            client_action_id="1",
            server_action_id="2",
            player_proof="PRESENT",
            at="2026-07-11T07:00:00.060000Z",
        )
        terminal.update({
            "placementRoute": "SERVER_ROUTE",
            "finalVerdict": "RED",
            "placedVerdict": "FAIL",
            "failureClasses": "LOGICAL_ATTEMPT_PLACEMENT_ROUTE_CONFLICT",
            "verdictMarker": "LIVE_PLACEMENT_VERDICT_RED",
            "marker": "LIVE_PLACEMENT_VERDICT_RED",
        })
        mismatch = [[
            "placement_attempt",
            "attempt:" + logical_attempt_id,
            "LIVE_PLACEMENT_VERDICT_RED",
            "10, 64, 10",
            "minecraft:stone_slab",
            "LOGICAL_ATTEMPT_PLACEMENT_ROUTE_CONFLICT",
        ]]

        triage = adapter.analyze(write_schema6_fixture(
            self.root / "java-red-terminal",
            rows=rows,
            terminal=terminal,
            mismatches=mismatch,
        ))

        self.assertEqual("RED", triage["verdict"]["status"])
        self.assertTrue(triage["verdict"]["hasRed"])
        self.assertEqual(1, len(triage["mismatches"]))
        self.assertEqual(
            "10,64,10",
            triage["mismatches"][0]["context"]["clickedOwnerPos"],
        )
        self.assertEqual(
            "none",
            triage["mismatches"][0]["context"]["clickedHitVec"],
        )

    def test_schema6_java_collision_conflict_rebuilds_missing_components(self):
        logical_attempt_id = SCHEMA6_RUN_ID + "-attempt-1"
        rows = [
            schema6_action(
                1,
                "client",
                logical_attempt_id=logical_attempt_id,
                phase="CLIENT_PREDICTION",
                player_proof="PRESENT",
            ),
            schema6_action(
                2,
                "server",
                logical_attempt_id=logical_attempt_id,
                phase="SERVER_AUTHORITY",
                player_proof="PRESENT",
                at="2026-07-11T07:00:00.050000Z",
            ),
        ]
        for row, actual_contact_plane in zip(
            rows,
            ("64.500000", "64.750000"),
        ):
            row.update({
                "afterStoredDy": "none",
                "storedDy": "unknown",
                "modelDy": "unknown",
                "collisionDy": "unknown",
                "raycastDy": "unknown",
                "outlineDy": "unknown",
                "expectedSupportPlane": "unknown",
                "actualContactPlane": actual_contact_plane,
                "seatError": "unknown",
                "finalVerdict": "INCONCLUSIVE",
                "placedVerdict": "PASS",
                "anchorVerdict": "MISSING",
                "modelVerdict": "MISSING",
                "collisionVerdict": "UNKNOWN",
                "raycastVerdict": "MISSING",
                "outlineVerdict": "MISSING",
                "stabilityVerdict": "NOT_RUN",
                "missingRequiredComponents": (
                    "ANCHOR,MODEL,COLLISION,RAYCAST,OUTLINE,STABILITY"
                ),
                "failureClasses": "none",
                "verdictMarker": "LIVE_PLACEMENT_VERDICT_INCONCLUSIVE",
                "marker": "none",
            })

        terminal = placement_attempt(
            logical_attempt_id,
            attempt_status="MERGED_CLIENT_SERVER",
            client_action_id="1",
            server_action_id="2",
            player_proof="PRESENT",
            at="2026-07-11T07:00:00.060000Z",
        )
        terminal.pop("afterStoredDy")
        terminal.update({
            "storedDy": "unknown",
            "modelDy": "unknown",
            "collisionDy": "unknown",
            "raycastDy": "unknown",
            "outlineDy": "unknown",
            "expectedSupportPlane": "unknown",
            "actualContactPlane": "64.500000",
            "seatError": "unknown",
            "finalVerdict": "RED",
            "placedVerdict": "PASS",
            "anchorVerdict": "MISSING",
            "modelVerdict": "MISSING",
            "collisionVerdict": "FAIL",
            "raycastVerdict": "MISSING",
            "outlineVerdict": "MISSING",
            "stabilityVerdict": "NOT_RUN",
            "missingRequiredComponents": "ANCHOR,MODEL,RAYCAST,OUTLINE,STABILITY",
            "failureClasses": "LOGICAL_ATTEMPT_ACTUAL_CONTACT_PLANE_CONFLICT",
            "verdictMarker": "LIVE_PLACEMENT_VERDICT_RED",
            "marker": "LIVE_PLACEMENT_VERDICT_RED",
        })
        mismatch = [[
            "placement_attempt",
            "attempt:" + logical_attempt_id,
            "LIVE_PLACEMENT_VERDICT_RED",
            "10, 64, 10",
            "minecraft:stone_slab",
            "LOGICAL_ATTEMPT_ACTUAL_CONTACT_PLANE_CONFLICT",
        ]]

        triage = adapter.analyze(write_schema6_fixture(
            self.root / "java-collision-conflict-missing",
            rows=rows,
            terminal=terminal,
            mismatches=mismatch,
        ))

        self.assertEqual("RED", triage["verdict"]["status"])
        self.assertTrue(triage["verdict"]["hasRed"])
        self.assertEqual(1, triage["playerAuthored"]["pairCount"])
        attempt = triage["logicalAttempts"]["rows"][0]
        self.assertEqual("FAIL", attempt["collisionVerdict"])
        self.assertEqual(
            "ANCHOR,MODEL,RAYCAST,OUTLINE,STABILITY",
            attempt["missingRequiredComponents"],
        )
        self.assertEqual(
            "LOGICAL_ATTEMPT_ACTUAL_CONTACT_PLANE_CONFLICT",
            attempt["failureClasses"],
        )

    def test_schema6_c3_pair_red_reaches_top_level_verdict(self):
        logical_attempt_id = SCHEMA6_RUN_ID + "-attempt-1"
        rows = [
            schema6_action(
                1,
                "client",
                logical_attempt_id=logical_attempt_id,
                phase="CLIENT_PREDICTION",
                player_proof="PRESENT",
            ),
            schema6_action(
                2,
                "server",
                logical_attempt_id=logical_attempt_id,
                phase="SERVER_AUTHORITY",
                player_proof="PRESENT",
                at="2026-07-11T07:00:00.050000Z",
            ),
        ]
        c3_rows = [
            c3_action(1, "client"),
            c3_action(2, "server", at="2026-07-11T07:00:00.050000Z"),
        ]
        copied_fields = [
            "heldItem",
            "clickedOwnerPos",
            "clickedFace",
            "clickedHitVec",
            "placementPos",
            "afterState",
            "afterStoredDy",
            "afterStoredDyBits",
            *C3_PAIR_FIELDS,
        ]
        for row, c3_row in zip(rows, c3_rows):
            row.update({field: c3_row[field] for field in copied_fields})
            row.update({
                "anchorVerdict": "PASS",
                "storedDy": "-1.000000",
                "missingRequiredComponents": (
                    "STABILITY"
                    if row["phase"] == "CLIENT_PREDICTION"
                    else "MODEL,COLLISION,RAYCAST,OUTLINE"
                ),
            })
        rows[1]["pairState"] = rows[1]["pairState"].replace(
            "open=false",
            "open=true",
        )
        terminal = placement_attempt(
            logical_attempt_id,
            attempt_status="MERGED_CLIENT_SERVER",
            client_action_id="1",
            server_action_id="2",
            player_proof="PRESENT",
            at="2026-07-11T07:00:00.060000Z",
        )
        terminal.update({
            "heldItem": rows[0]["heldItem"],
            "clickedOwnerPos": rows[0]["clickedOwnerPos"],
            "clickedFace": rows[0]["clickedFace"],
            "placementPos": rows[0]["placementPos"],
            "afterState": rows[0]["afterState"],
        })

        triage = adapter.analyze(write_schema6_fixture(
            self.root / "c3-pair-red",
            rows=rows,
            terminal=terminal,
        ))

        pair = triage["playerAuthored"]["pairs"][0]
        self.assertEqual("RED", pair["verdict"])
        self.assertIn(
            "ADAPTER_C3_SIDE_PAIR_FIELD_SPLIT",
            pair["redMarkers"],
        )
        self.assertEqual("RED", triage["verdict"]["status"])
        self.assertEqual("RED", triage["verdict"]["playerProof"])
        self.assertTrue(triage["verdict"]["hasAdapterRed"])
        self.assertIn(
            "player pair has producer or adapter-derived red",
            triage["verdict"]["reasons"],
        )


class C3PairFieldTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def write_c3(self, name, rows):
        return write_fixture(
            self.root / name,
            rows=rows,
            actions_header=C3_ACTIONS_HEADER,
            recorder_version=C3_RECORDER_VERSION,
        )

    def marker_pair(self, marker, mutate):
        rows = c3_door_and_bed_rows()[:2]
        mutate(rows)
        triage = adapter.analyze(self.write_c3(marker.lower(), rows))
        pair = triage["playerAuthored"]["pairs"][0]
        self.assertEqual("RED", triage["verdict"]["status"])
        self.assertIn(marker, pair["redMarkers"])
        self.assertEqual(1, triage["counters"]["adapter"][marker])
        return triage, pair

    def test_legacy_schema3_accepted_without_strict_gate(self):
        triage = adapter.analyze(write_fixture(self.root / "legacy"))
        self.assertEqual(0, adapter.exit_code_for(triage))
        self.assertEqual(7, adapter.exit_code_for(triage, require_c3_pair_fields=True))
        self.assertFalse(triage["c3PairFields"]["capable"])

    def test_complete_door_and_bed_pair_fields_satisfy_gate(self):
        triage = adapter.analyze(self.write_c3("green", c3_door_and_bed_rows()))
        self.assertEqual(0, adapter.exit_code_for(triage, require_c3_pair_fields=True))
        self.assertEqual({"bed": 1, "door": 1}, triage["c3PairFields"]["qualifyingFamilies"])
        self.assertEqual({
            "ADAPTER_C3_PAIR_FIELDS_MISSING": 0,
            "ADAPTER_C3_PAIR_ONE_CELL": 0,
            "ADAPTER_C3_PRIMARY_PAIR_BITS_SPLIT": 0,
            "ADAPTER_C3_SIDE_PAIR_FIELD_SPLIT": 0,
            "ADAPTER_EXPECTED_DY_MISMATCH": 0,
            "ADAPTER_UNCLASSIFIED_PLACEMENT_FAILURE": 0,
        }, triage["counters"]["adapter"])

    def test_c4_recorder_version_preserves_c3_pair_capability(self):
        triage = adapter.analyze(write_fixture(
            self.root / "c4-green",
            rows=c3_door_and_bed_rows(),
            actions_header=C3_ACTIONS_HEADER,
            recorder_version=C4_RECORDER_VERSION,
        ))
        self.assertTrue(triage["c3PairFields"]["capable"])
        self.assertEqual(0, adapter.exit_code_for(triage, require_c3_pair_fields=True))

    def test_live_shaped_pairs_pass_c3_gate_without_generic_green_oracle(self):
        triage = adapter.analyze(self.write_c3("live-shaped", live_c3_door_and_bed_rows()))
        self.assertEqual(0, adapter.exit_code_for(triage, require_c3_pair_fields=True))
        self.assertEqual("OBSERVED_UNCLASSIFIED", triage["verdict"]["status"])
        self.assertFalse(triage["verdict"]["hasRed"])
        self.assertTrue(triage["c3PairFields"]["capable"])
        self.assertEqual([], triage["c3PairFields"]["missingRequiredFamilies"])
        self.assertEqual({"bed": 1, "door": 1}, triage["c3PairFields"]["qualifyingFamilies"])
        self.assertTrue(all(value == 0 for value in triage["counters"]["adapter"].values()))
        for pair in triage["playerAuthored"]["pairs"]:
            self.assertEqual("OBSERVED_UNCLASSIFIED", pair["verdict"])
            self.assertTrue(pair["c3PairFields"]["qualifying"])
            self.assertIn(pair["c3PairFields"]["family"], {"door", "bed"})
            self.assertEqual("-1.0", pair["clientAfterDy"])
            self.assertEqual("-1.0", pair["serverAfterDy"])
            for side in ("clientRow", "serverRow"):
                self.assertEqual("-1.0", pair[side]["afterStoredDy"])
                self.assertEqual("-1.0", pair[side]["pairAfterDy"])
                self.assertEqual("-1.0", pair[side]["pairStoredDy"])
                self.assertEqual("bff0000000000000", pair[side]["afterStoredDyBits"])
                self.assertEqual("bff0000000000000", pair[side]["pairStoredDyBits"])

    def test_partial_extended_header_is_integrity_exit_five(self):
        recorder = write_fixture(
            self.root / "partial-header",
            rows=c3_door_and_bed_rows(),
            actions_header=C3_ACTIONS_HEADER[:-1],
            recorder_version=C3_RECORDER_VERSION,
        )
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(recorder)

    def test_legacy_header_with_extended_json_is_integrity_error(self):
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(write_fixture(
                self.root / "mixed-json",
                rows=c3_door_and_bed_rows(),
                actions_header=ACTIONS_HEADER,
                recorder_version=C3_RECORDER_VERSION,
            ))

    def test_store_decimal_hex_mismatch_is_integrity_error(self):
        rows = c3_door_and_bed_rows()
        rows[0]["afterStoredDyBits"] = "0000000000000000"
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(self.write_c3("bits-mismatch", rows))

    def test_uppercase_or_short_store_hex_is_integrity_error(self):
        for index, bits in enumerate(("BFF0000000000000", "bff000000000000")):
            rows = c3_door_and_bed_rows()
            rows[0]["pairStoredDyBits"] = bits
            with self.subTest(bits=bits), self.assertRaises(adapter.IntegrityError):
                adapter.analyze(self.write_c3("bad-bits-%d" % index, rows))

    def test_pair_fields_missing_marker_counter_and_markdown(self):
        triage, _ = self.marker_pair(
            "ADAPTER_C3_PAIR_FIELDS_MISSING",
            lambda rows: [row.__setitem__("pairState", "none") for row in rows],
        )
        self.assertIn("ADAPTER_C3_PAIR_FIELDS_MISSING", adapter.render_markdown(triage))

    def test_pair_one_cell_marker_counter(self):
        self.marker_pair(
            "ADAPTER_C3_PAIR_ONE_CELL",
            lambda rows: [row.__setitem__("pairPos", row["placementPos"]) for row in rows],
        )

    def test_primary_pair_bits_split_marker_counter(self):
        def split_bits(rows):
            for row in rows:
                row["pairAfterDy"] = "0.0"
                row["pairStoredDy"] = "0.0"
                row["pairStoredDyBits"] = "0000000000000000"

        self.marker_pair("ADAPTER_C3_PRIMARY_PAIR_BITS_SPLIT", split_bits)

    def test_client_server_pair_field_split_marker_counter(self):
        def split_side(rows):
            rows[1]["pairState"] = rows[1]["pairState"].replace("open=false", "open=true")

        triage, pair = self.marker_pair("ADAPTER_C3_SIDE_PAIR_FIELD_SPLIT", split_side)
        self.assertIn("pairState", pair["c3PairFieldDifferences"])
        self.assertIn("ADAPTER_C3_SIDE_PAIR_FIELD_SPLIT", adapter.render_json(triage))

    def test_extended_nonpair_explicit_none_is_valid(self):
        row = action(1, "server", marker="none", expected_dy="unknown", expected_lane="unknown")
        row.update({
            "afterStoredDy": "-1.0",
            "afterStoredDyBits": "bff0000000000000",
            **{field: "none" for field in C3_PAIR_FIELDS[2:]},
        })
        triage = adapter.analyze(self.write_c3("nonpair", [row]))
        self.assertEqual(0, adapter.exit_code_for(triage))

    def test_extended_gate_without_bed_family_exits_seven(self):
        triage = adapter.analyze(self.write_c3("door-only", c3_door_and_bed_rows()[:2]))
        self.assertEqual(7, adapter.exit_code_for(triage, require_c3_pair_fields=True))

    def test_early_non_success_none_shape_is_unclassified_failure_red(self):
        triage = adapter.analyze(self.write_c3("early-none", [early_none_action()]))
        self.assertEqual(1, adapter.exit_code_for(triage))
        self.assertEqual("RED", triage["verdict"]["status"])
        self.assertEqual(
            1,
            triage["counters"]["adapter"]["ADAPTER_UNCLASSIFIED_PLACEMENT_FAILURE"],
        )

    def test_success_place_block_none_shape_rejected(self):
        with self.assertRaises(adapter.IntegrityError):
            adapter.analyze(self.write_c3("success-none", [early_none_action(success=True)]))

    def test_cli_require_c3_pair_fields_implies_player_pairs_and_families(self):
        green = self.write_c3("cli-green", c3_door_and_bed_rows())
        result = subprocess.run(
            [sys.executable, str(ADAPTER_PATH), str(green), "--require-c3-pair-fields"],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

        legacy = write_fixture(self.root / "cli-legacy")
        result = subprocess.run(
            [sys.executable, str(ADAPTER_PATH), str(legacy), "--require-c3-pair-fields"],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(7, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
