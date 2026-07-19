#!/usr/bin/env python3
"""Strict, deterministic adapter for Slabbed 26.2 recorder schemas 3 and 6."""

import argparse
import csv
import hashlib
import json
import math
import os
import re
import struct
import sys
import tempfile
import uuid
from collections import Counter, defaultdict, deque
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path


TRIAGE_SCHEMA_VERSION = 1
SCHEMA_VERSION = "3"
SCHEMA6_VERSION = "6"
SUPPORTED_SCHEMA_VERSIONS = {SCHEMA_VERSION, SCHEMA6_VERSION}
RECORDER_NAME = "LiveCursorIntentRecorder"
ORIGIN_CONTRACT = "PLAYER_AUTHORED|AUTO_USEON_PROXY"
PLAYER_ORIGIN = "PLAYER_AUTHORED"
PROXY_ORIGIN = "AUTO_USEON_PROXY"
PAIR_WINDOW_MICROSECONDS = 1_000_000

LEGACY_ACTIONS_HEADER = [
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
C3_ACTIONS_HEADER = LEGACY_ACTIONS_HEADER + C3_PAIR_FIELDS
C3_RECORDER_VERSION = "26.2-recorder-truth-v3-origin-c3-pair-fields"
C4_RECORDER_VERSION = "26.2-recorder-truth-v4-c4-action-failure-audit"
SCHEMA6_RECORDER_VERSION = "26.2-recorder-truth-v8-logical-attempts"
PLACEMENT_VERDICT_CONTRACT = "PlacementVerificationVerdict-v3"
LOGICAL_ATTEMPT_CONTRACT = "LogicalPlacementAttempt-v1"
SCHEMA6_ACTION_FIELDS = [
    "logicalAttemptId",
    "phase",
    "playerProof",
]
SCHEMA6_ACTIONS_HEADER = C3_ACTIONS_HEADER + SCHEMA6_ACTION_FIELDS
C3_CAPABLE_RECORDER_VERSIONS = {
    C3_RECORDER_VERSION,
    C4_RECORDER_VERSION,
    SCHEMA6_RECORDER_VERSION,
}
C3_ADAPTER_MARKERS = [
    "ADAPTER_C3_PAIR_FIELDS_MISSING",
    "ADAPTER_C3_PAIR_ONE_CELL",
    "ADAPTER_C3_PRIMARY_PAIR_BITS_SPLIT",
    "ADAPTER_C3_SIDE_PAIR_FIELD_SPLIT",
]
ACTION_ADAPTER_MARKERS = [
    "ADAPTER_EXPECTED_DY_MISMATCH",
    "ADAPTER_UNCLASSIFIED_PLACEMENT_FAILURE",
]
# Compatibility alias for callers that imported the original schema-3 header constant.
ACTIONS_HEADER = LEGACY_ACTIONS_HEADER
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
FINAL_VERDICTS = {
    "GREEN",
    "RED",
    "INCONCLUSIVE",
    "EXPECTED_REFUSAL",
    "UNCLASSIFIED_FAILURE",
}
FINAL_VERDICT_MARKERS = {
    "GREEN": "LIVE_PLACEMENT_VERDICT_GREEN",
    "RED": "LIVE_PLACEMENT_VERDICT_RED",
    "INCONCLUSIVE": "LIVE_PLACEMENT_VERDICT_INCONCLUSIVE",
    "EXPECTED_REFUSAL": "LIVE_PLACEMENT_VERDICT_EXPECTED_REFUSAL",
    "UNCLASSIFIED_FAILURE": "LIVE_PLACEMENT_VERDICT_UNCLASSIFIED_FAILURE",
}
COMPONENT_VERDICT_FIELDS = [
    "placedVerdict",
    "anchorVerdict",
    "modelVerdict",
    "collisionVerdict",
    "raycastVerdict",
    "outlineVerdict",
    "stabilityVerdict",
]
COMPONENT_NAMES = [
    "PLACED",
    "ANCHOR",
    "MODEL",
    "COLLISION",
    "RAYCAST",
    "OUTLINE",
    "STABILITY",
]
COMPONENT_STATUSES = {
    "PASS",
    "FAIL",
    "UNKNOWN",
    "MISSING",
    "NOT_RUN",
    "NOT_APPLICABLE",
}
MISSING_COMPONENT_STATUSES = {
    "UNKNOWN",
    "MISSING",
    "NOT_RUN",
    "NOT_APPLICABLE",
}
CANONICAL_EVIDENCE_FIELDS = [
    "intentDy",
    "storedDy",
    "modelDy",
    "collisionDy",
    "raycastDy",
    "outlineDy",
    "expectedSupportPlane",
    "actualContactPlane",
    "seatError",
    "placementRoute",
    "landingAuthority",
    "rigCaseId",
    "expectedRefusalReason",
    "actualRefusalReason",
    "missingRequiredComponents",
    "failureClasses",
]
CANONICAL_NUMERIC_EVIDENCE_FIELDS = {
    "intentDy",
    "storedDy",
    "modelDy",
    "collisionDy",
    "raycastDy",
    "outlineDy",
    "expectedSupportPlane",
    "actualContactPlane",
    "seatError",
}
LOGICAL_ATTEMPT_STATUSES = {
    "MERGED_CLIENT_SERVER",
    "AUTO_PROXY",
    "SERVER_ONLY",
    "CLIENT_ONLY",
}
RAW_PHASES = {
    "CLIENT_PREDICTION",
    "SERVER_AUTHORITY",
    "AUTO_PROXY",
}
TERMINAL_FORBIDDEN_FIELDS = {
    "attemptRowId",
    "actionOrigin",
    "phase",
    "componentCount",
    "componentActionIds",
    "componentPhases",
    "componentVerdicts",
}
ARTIFACT_NAMES = [
    "manifest.json",
    "session.jsonl",
    "actions.tsv",
    "rendered-outlines.tsv",
    "mismatches.tsv",
    "summary.md",
]
BOOTSTRAP_ARTIFACT_NAMES = [name for name in ARTIFACT_NAMES if name != "session.jsonl"]
LAWFUL_LOWERED_LANES = {
    "persistent_lowered_slab_carrier",
    "compound_visible_side_lower_slab",
    "compound_visible_side_upper_slab",
    "compound_visible_side_double_slab",
    "compound_visible_owner_top_slab",
    "anchored_full_block",
}
CURSOR_RED_MARKERS = {
    "LIVE_CURSOR_OUTLINE_RAYCAST_SPLIT",
    "LIVE_CURSOR_GHOST_SURFACE",
    "LIVE_COLLISION_ITERATOR_TARGET_MISS",
    "LIVE_CURSOR_HIDDEN_OWNER",
}
ACTION_RED_MARKERS = {
    "LIVE_PLACEMENT_HIDDEN_OWNER",
    "LIVE_PLACEMENT_EXPECTED_DY_MISMATCH",
    "LIVE_PLACEMENT_UNCLASSIFIED_FAILURE",
    "LIVE_PLACEMENT_EXPECTED_LANE_MISMATCH",
    "LIVE_PLACEMENT_VANILLA_DY_FROM_LOWERED_OWNER",
    "LIVE_PLACEMENT_SIDE_DY_SPLIT",
    "LIVE_PLACEMENT_VERDICT_RED",
}
OUTLINE_RED_MARKERS = {
    "LIVE_RENDERED_OUTLINE_LARGE_BOUNDS",
    "LIVE_RENDERED_OUTLINE_REPLAY_BOUNDS_SPLIT",
    "LIVE_RENDERED_OUTLINE_TARGET_SPLIT",
}
SENTINEL_RED_KINDS = {
    "MODEL_STALE_DIVERGENT",
    "MODEL_STALE_ABSENT",
    "ENSEMBLE_INTERPENETRATION",
    "ENSEMBLE_GAP",
}
SENTINEL_INFO_KINDS = {"ENSEMBLE_OCCLUDED_OCCUPANCY"}
GREEN_MARKER_BY_TYPE = {
    "cursor": "LIVE_GREEN_CURSOR_TRIAD",
    "action": "LIVE_GREEN_PLACEMENT_AUTHORING",
}
RED_MARKERS_BY_TYPE = {
    "cursor": CURSOR_RED_MARKERS,
    "action": ACTION_RED_MARKERS,
    "rendered_outline": OUTLINE_RED_MARKERS,
    "placement_attempt": {
        FINAL_VERDICT_MARKERS["RED"],
        FINAL_VERDICT_MARKERS["UNCLASSIFIED_FAILURE"],
    },
}
ID_FIELD_BY_TYPE = {
    "action": "actionId",
    "cursor": "rowId",
    "rendered_outline": "outlineRenderId",
    "model_stale_sentinel": "rowId",
    "break": "rowId",
}
MARKER_FIELD_BY_TYPE = {
    "action": "marker",
    "cursor": "mismatchMarker",
    "rendered_outline": "marker",
    "model_stale_sentinel": "marker",
    "placement_attempt": "marker",
}
SENSITIVE_ARG_RE = re.compile(
    r"--(?:accessToken|uuid|xuid|clientId|session)(?:\s+|=)(\"[^\"]*\"|'[^']*'|\S+)",
    re.IGNORECASE,
)


class AdapterError(Exception):
    exit_code = 5


class UsageError(AdapterError):
    exit_code = 2


class DiscoveryError(AdapterError):
    exit_code = 3


class UnsupportedSchemaError(AdapterError):
    exit_code = 4


class IntegrityError(AdapterError):
    exit_code = 5


class OutputError(AdapterError):
    exit_code = 6


def _no_duplicate_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise IntegrityError("duplicate JSON key: %s" % key)
        result[key] = value
    return result


def _load_json(path):
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise DiscoveryError("cannot read %s: %s" % (path, exc))
    try:
        return json.loads(text, object_pairs_hook=_no_duplicate_object)
    except AdapterError:
        raise
    except (ValueError, json.JSONDecodeError) as exc:
        raise IntegrityError("invalid JSON in %s: %s" % (path, exc))


def _manifest_schema(path):
    manifest_path = path / "manifest.json"
    if not manifest_path.is_file():
        return None
    manifest = _load_json(manifest_path)
    if not isinstance(manifest, dict):
        raise IntegrityError("manifest.json must contain one JSON object: %s" % manifest_path)
    return str(manifest.get("schemaVersion", ""))


def _candidate(path, mode, requested, evidence_dir):
    manifest = _load_json(path / "manifest.json")
    return {
        "requestedInput": str(requested),
        "recorderDir": str(path.resolve()),
        "discoveryMode": mode,
        "evidenceDir": str(evidence_dir.resolve()) if evidence_dir is not None else None,
        "manifest": manifest,
    }


def discover(input_path, run_id=None):
    requested = Path(input_path).expanduser()
    try:
        requested = requested.resolve(strict=True)
    except OSError as exc:
        raise DiscoveryError("input does not exist: %s (%s)" % (input_path, exc))
    if not requested.is_dir():
        raise DiscoveryError("input is not a directory: %s" % requested)

    direct_schema = _manifest_schema(requested)
    candidates = []
    roots = []
    for name, mode in [
        ("live-cursor-recorder", "evidence-live-cursor-recorder"),
        ("recorder", "evidence-recorder"),
    ]:
        child = requested / name
        if child.is_dir():
            roots.append((child, mode, requested))
    roots.append((requested, "direct-recorder", None))

    seen = set()
    for root, mode, evidence_dir in roots:
        if root in seen:
            continue
        seen.add(root)
        schema = _manifest_schema(root)
        if schema in SUPPORTED_SCHEMA_VERSIONS:
            candidates.append(_candidate(root, mode, requested, evidence_dir))
        try:
            children = sorted(
                child for child in root.iterdir()
                if child.is_dir()
                and (
                    child.name.startswith("schema-3-")
                    or child.name.startswith("schema-6-")
                )
            )
        except OSError as exc:
            raise DiscoveryError("cannot inspect %s: %s" % (root, exc))
        for child in children:
            if _manifest_schema(child) in SUPPORTED_SCHEMA_VERSIONS:
                child_mode = mode + "-schema-child"
                candidates.append(_candidate(child, child_mode, requested, evidence_dir))

    unique = {}
    for item in candidates:
        unique[item["recorderDir"]] = item
    candidates = [unique[key] for key in sorted(unique)]

    if run_id is not None:
        try:
            normalized = str(uuid.UUID(str(run_id)))
        except ValueError:
            raise UsageError("invalid --run-id UUID: %s" % run_id)
        candidates = [item for item in candidates if item["manifest"].get("runId") == normalized]

    if len(candidates) == 1:
        item = candidates[0]
        path = Path(item["recorderDir"])
        child_match = re.fullmatch(r"schema-(3|6)-(.+)", path.name)
        if child_match is not None:
            child_schema, basename_id = child_match.groups()
            manifest_schema = item["manifest"].get("schemaVersion")
            if child_schema != manifest_schema:
                raise IntegrityError(
                    "schema child dirname/manifest schema mismatch: %s vs %s"
                    % (child_schema, manifest_schema)
                )
            if basename_id != item["manifest"].get("runId"):
                raise IntegrityError(
                    "schema child basename/runId mismatch: %s vs %s"
                    % (basename_id, item["manifest"].get("runId"))
                )
        return item

    if len(candidates) > 1:
        details = ", ".join(
            "%s [%s]" % (item["recorderDir"], item["manifest"].get("runId", "missing"))
            for item in candidates
        )
        raise DiscoveryError("multiple supported recorder sessions; use --run-id: %s" % details)

    if run_id is not None:
        raise DiscoveryError("no supported recorder session matched --run-id %s" % run_id)
    schemas = []
    if direct_schema:
        schemas.append(direct_schema)
    for name in ["live-cursor-recorder", "recorder"]:
        child = requested / name
        if child.is_dir():
            schema = _manifest_schema(child)
            if schema:
                schemas.append(schema)
    if schemas:
        raise UnsupportedSchemaError("unsupported recorder schema(s): %s" % ", ".join(sorted(set(schemas))))
    raise DiscoveryError("no supported recorder session found beneath %s" % requested)


def _require_string(mapping, key, label, allow_none=False):
    value = mapping.get(key)
    if not isinstance(value, str) or not value or (not allow_none and value == "none"):
        raise IntegrityError("%s missing nonempty string %s" % (label, key))
    return value


def _validate_manifest(manifest):
    if not isinstance(manifest, dict):
        raise IntegrityError("manifest.json must be a JSON object")
    for key, value in manifest.items():
        if not isinstance(key, str) or not isinstance(value, str):
            raise IntegrityError("manifest values must be strings: %s" % key)
    schema = _require_string(manifest, "schemaVersion", "manifest")
    if schema not in SUPPORTED_SCHEMA_VERSIONS:
        raise UnsupportedSchemaError("unsupported recorder schema: %s" % schema)
    if _require_string(manifest, "recorder", "manifest") != RECORDER_NAME:
        raise IntegrityError("unexpected recorder implementation")
    if _require_string(manifest, "actionOriginContract", "manifest") != ORIGIN_CONTRACT:
        raise IntegrityError("unexpected actionOriginContract")
    run_id = _require_string(manifest, "runId", "manifest")
    try:
        if str(uuid.UUID(run_id)) != run_id:
            raise ValueError("non-canonical UUID")
    except ValueError:
        raise IntegrityError("manifest runId is not a canonical UUID: %s" % run_id)
    for key in [
        "recorderVersion", "createdAt", "gitSha", "buildTime", "jarFile", "gameDir",
        "javaCommand",
    ]:
        _require_string(manifest, key, "manifest", allow_none=False)
    if schema == SCHEMA6_VERSION:
        if manifest["recorderVersion"] != SCHEMA6_RECORDER_VERSION:
            raise IntegrityError(
                "unexpected schema-6 recorderVersion: %s" % manifest["recorderVersion"]
            )
        if _require_string(
            manifest, "placementVerdictContract", "manifest"
        ) != PLACEMENT_VERDICT_CONTRACT:
            raise IntegrityError("unexpected placementVerdictContract")
        if _require_string(
            manifest, "logicalAttemptContract", "manifest"
        ) != LOGICAL_ATTEMPT_CONTRACT:
            raise IntegrityError("unexpected logicalAttemptContract")
    java_command = manifest.get("javaCommand", "")
    for match in SENSITIVE_ARG_RE.finditer(java_command):
        if match.group(1).strip("\"'") != "[REDACTED]":
            raise IntegrityError("manifest javaCommand contains an unredacted sensitive launcher value")


def _artifact_info(path):
    try:
        data = path.read_bytes()
    except OSError as exc:
        raise IntegrityError("cannot read artifact %s: %s" % (path, exc))
    return {
        "path": str(path.resolve()),
        "bytes": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
    }


def _parse_summary(path, schema, recorder_version):
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise IntegrityError("cannot read summary.md: %s" % exc)
    expected_keys = (
        SCHEMA6_SUMMARY_KEYS if schema == SCHEMA6_VERSION else SUMMARY_KEYS
    )
    counters = {}
    for line in lines:
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise IntegrityError("invalid summary.md line: %s" % line)
        key, value = line.split("=", 1)
        if key in counters:
            raise IntegrityError("duplicate summary counter: %s" % key)
        if key not in expected_keys:
            raise IntegrityError("unknown summary counter: %s" % key)
        if not re.fullmatch(r"[0-9]+", value):
            raise IntegrityError("summary counter must be nonnegative integer: %s=%s" % (key, value))
        counters[key] = int(value)
    missing = [key for key in expected_keys if key not in counters]
    allowed_legacy_missing = (
        {"placementUnclassifiedFailureRows"}
        if schema == SCHEMA_VERSION
        and isinstance(recorder_version, str)
        and recorder_version.startswith("26.2-recorder-truth-v3-")
        else set()
    )
    disallowed_missing = [key for key in missing if key not in allowed_legacy_missing]
    if disallowed_missing:
        raise IntegrityError(
            "summary.md missing counters: %s" % ", ".join(disallowed_missing)
        )
    for key in missing:
        counters[key] = 0
    return counters, missing


def _parse_tsv_headers(path, expected_headers):
    try:
        with path.open("r", encoding="utf-8", newline="") as handle:
            rows = list(csv.reader(handle, delimiter="\t"))
    except (OSError, csv.Error) as exc:
        raise IntegrityError("cannot parse %s: %s" % (path.name, exc))
    if not rows:
        raise IntegrityError("%s is empty" % path.name)
    matched_header = next((header for header in expected_headers if rows[0] == header), None)
    if matched_header is None:
        raise IntegrityError(
            "%s header mismatch: expected one of [%s], got %s"
            % (
                path.name,
                "] or [".join("\t".join(header) for header in expected_headers),
                "\t".join(rows[0]),
            )
        )
    result = []
    for index, values in enumerate(rows[1:], start=2):
        if not values or values == [""]:
            continue
        if len(values) != len(matched_header):
            raise IntegrityError("%s line %d has %d columns, expected %d" % (
                path.name, index, len(values), len(matched_header)
            ))
        result.append(dict(zip(matched_header, values)))
    return result, matched_header


def _parse_tsv(path, expected_header):
    rows, _ = _parse_tsv_headers(path, [expected_header])
    return rows


def _parse_actions_tsv(path, schema):
    if schema == SCHEMA6_VERSION:
        return _parse_tsv_headers(path, [SCHEMA6_ACTIONS_HEADER])
    return _parse_tsv_headers(path, [LEGACY_ACTIONS_HEADER, C3_ACTIONS_HEADER])


def _parse_instant(value, label):
    if not isinstance(value, str):
        raise IntegrityError("%s timestamp is not a string" % label)
    normalized = value
    normalized = re.sub(r"(\.\d{6})\d+(?=Z|[+-])", r"\1", normalized)
    if normalized.endswith("Z"):
        normalized = normalized[:-1] + "+00:00"
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError:
        raise IntegrityError("invalid recordedAt for %s: %s" % (label, value))
    if parsed.tzinfo is None:
        raise IntegrityError("recordedAt lacks timezone for %s: %s" % (label, value))
    return parsed.astimezone(timezone.utc)


def _row_id(row):
    row_type = row.get("type")
    id_field = ID_FIELD_BY_TYPE.get(row_type)
    if id_field is None:
        raise IntegrityError("unsupported session row type: %s" % row_type)
    raw = row.get(id_field)
    if not isinstance(raw, str) or not re.fullmatch(r"[1-9][0-9]*", raw):
        raise IntegrityError("%s row has invalid %s: %r" % (row_type, id_field, raw))
    return int(raw)


def _parse_session(path, schema):
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise IntegrityError("cannot read session.jsonl: %s" % exc)
    rows = []
    previous_time = None
    next_numeric_id = 1
    for line_number, line in enumerate(lines, start=1):
        if not line:
            raise IntegrityError("blank session.jsonl line %d" % line_number)
        try:
            row = json.loads(line, object_pairs_hook=_no_duplicate_object)
        except AdapterError:
            raise
        except (ValueError, json.JSONDecodeError) as exc:
            raise IntegrityError("invalid session.jsonl line %d: %s" % (line_number, exc))
        if not isinstance(row, dict):
            raise IntegrityError("session.jsonl line %d is not an object" % line_number)
        for key, value in row.items():
            if not isinstance(key, str) or not isinstance(value, str):
                raise IntegrityError("session row %d values must be strings" % line_number)
        terminal = (
            schema == SCHEMA6_VERSION
            and row.get("type") == "placement_attempt"
        )
        if terminal:
            row_id = _require_string(
                row,
                "rowId",
                "session placement_attempt line %d" % line_number,
            )
        else:
            row_id = _row_id(row)
            if row_id != next_numeric_id:
                raise IntegrityError(
                    "session numeric ids must be contiguous independent of terminal rows: "
                    "line %d has id %d, expected %d"
                    % (line_number, row_id, next_numeric_id)
                )
            next_numeric_id += 1
        recorded = _parse_instant(
            row.get("recordedAt"),
            "session row %s" % row_id,
        )
        if previous_time is not None and recorded < previous_time:
            raise IntegrityError("session recordedAt decreases at row %s" % row_id)
        previous_time = recorded
        row["_globalId"] = row_id
        row["_sessionIndex"] = line_number
        row["_recordedAtParsed"] = recorded
        rows.append(row)
    return rows


def _canonical_int_pos(value, label):
    if not isinstance(value, str):
        raise IntegrityError("%s is not a position string" % label)
    parts = [part for part in re.split(r"[,\s]+", value.strip()) if part]
    if len(parts) != 3:
        raise IntegrityError("%s is not an integer XYZ position: %s" % (label, value))
    try:
        numbers = [int(part) for part in parts]
    except ValueError:
        raise IntegrityError("%s is not an integer XYZ position: %s" % (label, value))
    return ",".join(str(number) for number in numbers)


def _canonical_hit_vec(value, label):
    if not isinstance(value, str):
        raise IntegrityError("%s is not a hit-vector string" % label)
    parts = [part for part in re.split(r"[,\s]+", value.strip()) if part]
    if len(parts) != 3:
        raise IntegrityError("%s is not a finite XYZ hit vector: %s" % (label, value))
    result = []
    for part in parts:
        try:
            number = Decimal(part)
        except InvalidOperation:
            raise IntegrityError("%s is not a finite XYZ hit vector: %s" % (label, value))
        if not number.is_finite():
            raise IntegrityError("%s is not a finite XYZ hit vector: %s" % (label, value))
        result.append(format(number, ".6f"))
    return ",".join(result)


def _finite_decimal(value, label):
    if not isinstance(value, str):
        raise IntegrityError("%s is not a finite decimal: %r" % (label, value))
    try:
        number = Decimal(value)
    except InvalidOperation:
        raise IntegrityError("%s is not a finite decimal: %s" % (label, value))
    if not number.is_finite():
        raise IntegrityError("%s is not a finite decimal: %s" % (label, value))
    return number


def _has_evidence(value):
    return (
        isinstance(value, str)
        and bool(value)
        and value not in {"none", "unknown", "NaN"}
    )


def _java_has_evidence(value):
    if not isinstance(value, str) or not value.strip():
        return False
    return value.strip().lower() not in {
        "unknown",
        "none",
        "missing",
        "not_run",
        "not_applicable",
        "null",
    }


def _java_finite_number(value):
    if not isinstance(value, str):
        return None
    try:
        number = float(value)
    except (TypeError, ValueError, OverflowError):
        return None
    return number if math.isfinite(number) else None


def _java_same_number(left, right):
    left_number = _java_finite_number(left)
    right_number = _java_finite_number(right)
    return (
        left_number is not None
        and right_number is not None
        and abs(left_number - right_number) <= 1.0e-6
    )


def _java_same_evidence(left, right):
    left_present = _java_has_evidence(left)
    right_present = _java_has_evidence(right)
    if not left_present or not right_present:
        return left_present == right_present
    if _java_finite_number(left) is not None and _java_finite_number(right) is not None:
        return _java_same_number(left, right)
    return left == right


def _java_first_evidence(row, *keys):
    for key in keys:
        value = row.get(key)
        if _java_has_evidence(value):
            return value.strip()
    return "unknown"


def _comma_tokens(value, label, allowed=None):
    if value == "none":
        return []
    if not isinstance(value, str) or not value:
        raise IntegrityError("%s must be a nonempty string" % label)
    tokens = value.split(",")
    if (
        any(not token for token in tokens)
        or len(tokens) != len(set(tokens))
    ):
        raise IntegrityError("%s has malformed or duplicate comma tokens: %s" % (label, value))
    if allowed is not None:
        unknown = [token for token in tokens if token not in allowed]
        if unknown:
            raise IntegrityError("%s has unknown token(s): %s" % (label, ", ".join(unknown)))
    elif any(re.fullmatch(r"[A-Z][A-Z0-9_]*", token) is None for token in tokens):
        raise IntegrityError("%s has malformed token(s): %s" % (label, value))
    return tokens


def _logical_attempt_number(value, run_id, label):
    if not isinstance(value, str):
        raise IntegrityError("%s logicalAttemptId is not a string" % label)
    match = re.fullmatch(
        re.escape(run_id) + r"-attempt-([1-9][0-9]*)",
        value,
    )
    if match is None:
        raise IntegrityError("%s has malformed logicalAttemptId: %r" % (label, value))
    return int(match.group(1))


def _validate_pass_component_evidence(row, label, statuses):
    tolerance = Decimal("0.000001")

    def require_intent_match(status_field, evidence_field):
        if statuses[status_field] != "PASS":
            return
        intent = _finite_decimal(row.get("intentDy"), "%s intentDy" % label)
        actual = _finite_decimal(
            row.get(evidence_field),
            "%s %s" % (label, evidence_field),
        )
        if abs(actual - intent) > tolerance:
            raise IntegrityError(
                "%s %s PASS evidence disagrees with intentDy"
                % (label, evidence_field)
            )

    if statuses["placedVerdict"] == "PASS":
        actual_result = str(row.get("actualResult", "")).strip().lower()
        if not (
            actual_result == "success"
            or actual_result.startswith("success[")
            or actual_result == "consume"
            or actual_result.startswith("consume[")
        ):
            raise IntegrityError("%s placed PASS lacks a successful actualResult" % label)
        if _java_finite_number(row.get("afterDy")) is not None:
            require_intent_match("placedVerdict", "afterDy")

    require_intent_match("anchorVerdict", "storedDy")
    require_intent_match("modelVerdict", "modelDy")
    require_intent_match("raycastVerdict", "raycastDy")
    require_intent_match("outlineVerdict", "outlineDy")

    if statuses["collisionVerdict"] == "PASS":
        passed_observable = False
        if _has_evidence(row.get("collisionDy")):
            require_intent_match("collisionVerdict", "collisionDy")
            passed_observable = True

        expected_plane_present = _has_evidence(row.get("expectedSupportPlane"))
        actual_plane_present = _has_evidence(row.get("actualContactPlane"))
        if expected_plane_present or actual_plane_present:
            if not (expected_plane_present and actual_plane_present):
                raise IntegrityError(
                    "%s collision PASS has incomplete contact-plane evidence" % label
                )
            expected_plane = _finite_decimal(
                row["expectedSupportPlane"],
                "%s expectedSupportPlane" % label,
            )
            actual_plane = _finite_decimal(
                row["actualContactPlane"],
                "%s actualContactPlane" % label,
            )
            if abs(expected_plane - actual_plane) > tolerance:
                raise IntegrityError(
                    "%s collision PASS contact planes disagree" % label
                )
            passed_observable = True

        if _has_evidence(row.get("seatError")):
            seat_error = _finite_decimal(
                row["seatError"],
                "%s seatError" % label,
            )
            if abs(seat_error) > tolerance:
                raise IntegrityError(
                    "%s collision PASS has nonzero seatError" % label
                )
            passed_observable = True

        if not passed_observable:
            raise IntegrityError("%s collision PASS lacks primitive evidence" % label)


def _java_explicit_component_status(row, component_name):
    field = COMPONENT_VERDICT_FIELDS[COMPONENT_NAMES.index(component_name)]
    value = row.get(field)
    if not isinstance(value, str) or not value.strip():
        return None
    normalized = value.strip().upper().replace("-", "_").replace(" ", "_")
    if normalized == "UNKNOWN/MISSING":
        return "MISSING"
    return normalized if normalized in COMPONENT_STATUSES else None


def _java_successful_result(value):
    normalized = value.strip().lower() if isinstance(value, str) else ""
    return (
        normalized == "success"
        or normalized.startswith("success[")
        or normalized == "consume"
        or normalized.startswith("consume[")
    )


def _java_actual_refusal_reason(actual_result):
    if (
        not isinstance(actual_result, str)
        or not actual_result.startswith("Fail[")
        or not actual_result.endswith("]")
    ):
        return "unknown"
    captured = actual_result[len("Fail["):-1]
    return captured.strip() if _java_has_evidence(captured) else "unknown"


def _java_unavailable_status(explicit, actual):
    if explicit in {"UNKNOWN", "MISSING", "NOT_RUN"}:
        return explicit
    return "MISSING" if actual == "unknown" else "UNKNOWN"


def _append_unique(values, value):
    if value not in values:
        values.append(value)


def _derive_java_placement_verdict(row):
    explicit_intent_dy = _java_first_evidence(row, "intentDy")
    expected_after_dy = _java_first_evidence(row, "expectedAfterDy")
    intent_alias_conflict = (
        explicit_intent_dy != "unknown"
        and expected_after_dy != "unknown"
        and not _java_same_evidence(explicit_intent_dy, expected_after_dy)
    )
    intent_dy = (
        "unknown"
        if intent_alias_conflict
        else explicit_intent_dy
        if explicit_intent_dy != "unknown"
        else expected_after_dy
    )
    stored_dy = _java_first_evidence(row, "storedDy", "afterStoredDy")
    model_dy = _java_first_evidence(row, "modelDy")
    collision_dy = _java_first_evidence(row, "collisionDy")
    raycast_dy = _java_first_evidence(row, "raycastDy")
    outline_dy = _java_first_evidence(row, "outlineDy")
    expected_support_plane = _java_first_evidence(row, "expectedSupportPlane")
    actual_contact_plane = _java_first_evidence(row, "actualContactPlane")
    seat_error = _java_first_evidence(row, "seatError")
    placement_route = _java_first_evidence(row, "placementRoute")
    landing_authority = _java_first_evidence(row, "landingAuthority")
    rig_case_id = _java_first_evidence(row, "rigCaseId")
    expected_refusal_reason = _java_first_evidence(row, "expectedRefusalReason")
    actual_result = row.get("actualResult")
    if not isinstance(actual_result, str):
        actual_result = ""
    explicit_actual_refusal_reason = _java_first_evidence(row, "actualRefusalReason")
    parsed_actual_refusal_reason = _java_actual_refusal_reason(actual_result)
    actual_refusal_reason_conflict = (
        explicit_actual_refusal_reason != "unknown"
        and parsed_actual_refusal_reason != "unknown"
        and explicit_actual_refusal_reason != parsed_actual_refusal_reason
    )
    actual_refusal_reason = (
        "unknown"
        if actual_refusal_reason_conflict
        else explicit_actual_refusal_reason
        if explicit_actual_refusal_reason != "unknown"
        else parsed_actual_refusal_reason
    )

    statuses = {}
    failure_classes = []
    expected_refusal = any(
        row.get(field) == "MUST_REFUSE_VANILLA"
        for field in ("expectedResult", "placementContract", "refusalContract")
    )
    refusal_occurred = actual_result.startswith("Fail[")

    def canonical_result(final_verdict, missing_components):
        result = {
            "finalVerdict": final_verdict,
            **{
                field: statuses[name]
                for name, field in zip(COMPONENT_NAMES, COMPONENT_VERDICT_FIELDS)
            },
            "intentDy": intent_dy,
            "storedDy": stored_dy,
            "modelDy": model_dy,
            "collisionDy": collision_dy,
            "raycastDy": raycast_dy,
            "outlineDy": outline_dy,
            "expectedSupportPlane": expected_support_plane,
            "actualContactPlane": actual_contact_plane,
            "seatError": seat_error,
            "placementRoute": placement_route,
            "landingAuthority": landing_authority,
            "rigCaseId": rig_case_id,
            "expectedRefusalReason": expected_refusal_reason,
            "actualRefusalReason": actual_refusal_reason,
            "missingRequiredComponents": (
                "none" if not missing_components else ",".join(missing_components)
            ),
            "failureClasses": (
                "none" if not failure_classes else ",".join(failure_classes)
            ),
        }
        return result

    def all_status(status):
        for component_name in COMPONENT_NAMES:
            statuses[component_name] = status

    if refusal_occurred and actual_refusal_reason_conflict:
        all_status("NOT_APPLICABLE")
        statuses["PLACED"] = "FAIL"
        _append_unique(failure_classes, "ACTUAL_REFUSAL_REASON_CONFLICT")
        if not expected_refusal:
            _append_unique(failure_classes, "UNDECLARED_PLACEMENT_FAILURE")
        return canonical_result("UNCLASSIFIED_FAILURE", [])

    if expected_refusal and refusal_occurred:
        all_status("NOT_APPLICABLE")
        if (
            expected_refusal_reason == "unknown"
            or actual_refusal_reason == "unknown"
        ):
            statuses["PLACED"] = "FAIL"
            _append_unique(failure_classes, "EXPECTED_REFUSAL_REASON_MISSING")
            return canonical_result("UNCLASSIFIED_FAILURE", [])
        if expected_refusal_reason != actual_refusal_reason:
            statuses["PLACED"] = "FAIL"
            _append_unique(failure_classes, "EXPECTED_REFUSAL_REASON_MISMATCH")
            return canonical_result("UNCLASSIFIED_FAILURE", [])
        return canonical_result("EXPECTED_REFUSAL", [])

    if not expected_refusal and refusal_occurred:
        all_status("NOT_APPLICABLE")
        statuses["PLACED"] = "FAIL"
        _append_unique(failure_classes, "UNDECLARED_PLACEMENT_FAILURE")
        return canonical_result("UNCLASSIFIED_FAILURE", [])

    placement_succeeded = (
        row.get("actionType") == "place_block"
        and _java_successful_result(actual_result)
    )
    placed_failure = False
    if intent_alias_conflict:
        _append_unique(failure_classes, "INTENT_DY_ALIAS_CONFLICT")
        placed_failure = True
    if expected_refusal and placement_succeeded:
        _append_unique(failure_classes, "EXPECTED_REFUSAL_DID_NOT_OCCUR")
        placed_failure = True
    elif (
        not intent_alias_conflict
        and placement_succeeded
        and _java_finite_number(intent_dy) is not None
        and _java_finite_number(row.get("afterDy")) is not None
        and not _java_same_number(intent_dy, row.get("afterDy"))
    ):
        _append_unique(failure_classes, "PLACED_ACTION_DY_MISMATCH")
        placed_failure = True
    statuses["PLACED"] = (
        "FAIL" if placed_failure else "PASS" if placement_succeeded else "MISSING"
    )

    def dy_component(component_name, actual_dy):
        explicit = _java_explicit_component_status(row, component_name)
        if explicit == "FAIL":
            _append_unique(
                failure_classes,
                "%s_COMPONENT_FAILURE" % component_name,
            )
            return explicit
        if actual_dy == "unknown" and (
            explicit == "NOT_APPLICABLE"
            or component_name in {"MODEL", "RAYCAST", "OUTLINE"}
        ):
            return "MISSING"
        if actual_dy != "unknown":
            if (
                _java_finite_number(actual_dy) is not None
                and _java_finite_number(intent_dy) is not None
            ):
                if _java_same_number(actual_dy, intent_dy):
                    return "PASS"
                _append_unique(
                    failure_classes,
                    (
                        "ANCHOR_STORED_DY_MISMATCH"
                        if component_name == "ANCHOR"
                        else "%s_DY_MISMATCH" % component_name
                    ),
                )
                return "FAIL"
            return "UNKNOWN"
        if explicit == "NOT_APPLICABLE":
            return explicit
        return _java_unavailable_status(explicit, actual_dy)

    statuses["ANCHOR"] = dy_component("ANCHOR", stored_dy)
    statuses["MODEL"] = dy_component("MODEL", model_dy)

    explicit_collision = _java_explicit_component_status(row, "COLLISION")
    if explicit_collision == "FAIL":
        _append_unique(failure_classes, "COLLISION_COMPONENT_FAILURE")
        collision_status = "FAIL"
    else:
        actual_observable = (
            collision_dy != "unknown"
            or actual_contact_plane != "unknown"
            or seat_error != "unknown"
        )
        passed_observable = False
        unresolved_observable = False
        collision_status = None
        if _java_finite_number(collision_dy) is not None:
            if _java_finite_number(intent_dy) is not None:
                if not _java_same_number(intent_dy, collision_dy):
                    _append_unique(failure_classes, "COLLISION_DY_MISMATCH")
                    collision_status = "FAIL"
                else:
                    passed_observable = True
            else:
                unresolved_observable = True
        elif collision_dy != "unknown":
            unresolved_observable = True

        if collision_status is None:
            if (
                _java_finite_number(expected_support_plane) is not None
                and _java_finite_number(actual_contact_plane) is not None
            ):
                if not _java_same_number(
                    expected_support_plane,
                    actual_contact_plane,
                ):
                    _append_unique(
                        failure_classes,
                        "COLLISION_CONTACT_PLANE_MISMATCH",
                    )
                    collision_status = "FAIL"
                else:
                    passed_observable = True
            elif (
                expected_support_plane != "unknown"
                or actual_contact_plane != "unknown"
            ):
                unresolved_observable = True

        if collision_status is None:
            if _java_finite_number(seat_error) is not None:
                if abs(float(seat_error)) > 1.0e-6:
                    _append_unique(failure_classes, "COLLISION_SEAT_ERROR")
                    collision_status = "FAIL"
                else:
                    passed_observable = True
            elif seat_error != "unknown":
                unresolved_observable = True

        if collision_status is None:
            if actual_observable:
                collision_status = (
                    "UNKNOWN"
                    if unresolved_observable
                    else "PASS"
                    if passed_observable
                    else "UNKNOWN"
                )
            elif explicit_collision == "NOT_APPLICABLE":
                collision_status = "MISSING"
            elif unresolved_observable:
                collision_status = "UNKNOWN"
            else:
                collision_status = _java_unavailable_status(
                    explicit_collision,
                    collision_dy,
                )
    statuses["COLLISION"] = collision_status

    statuses["RAYCAST"] = dy_component("RAYCAST", raycast_dy)
    statuses["OUTLINE"] = dy_component("OUTLINE", outline_dy)
    explicit_stability = _java_explicit_component_status(row, "STABILITY")
    if explicit_stability == "FAIL":
        _append_unique(failure_classes, "STABILITY_COMPONENT_FAILURE")
    statuses["STABILITY"] = (
        "MISSING"
        if explicit_stability == "NOT_APPLICABLE"
        else "NOT_RUN"
        if explicit_stability is None
        else explicit_stability
    )

    missing_components = [
        component_name
        for component_name in COMPONENT_NAMES
        if statuses[component_name] in MISSING_COMPONENT_STATUSES
    ]
    final_verdict = (
        "RED"
        if "FAIL" in statuses.values()
        else "GREEN"
        if not missing_components
        else "INCONCLUSIVE"
    )
    return canonical_result(final_verdict, missing_components)


def _validate_canonical_verdict(row, label):
    final_verdict = _require_string(row, "finalVerdict", label)
    if final_verdict not in FINAL_VERDICTS:
        raise IntegrityError("%s has invalid finalVerdict %r" % (label, final_verdict))

    statuses = {}
    for field in COMPONENT_VERDICT_FIELDS:
        status = _require_string(row, field, label)
        if status not in COMPONENT_STATUSES:
            raise IntegrityError("%s has invalid %s=%r" % (label, field, status))
        statuses[field] = status

    for field in CANONICAL_EVIDENCE_FIELDS:
        value = _require_string(row, field, label, allow_none=True)
        if field in CANONICAL_NUMERIC_EVIDENCE_FIELDS and _has_evidence(value):
            _finite_decimal(value, "%s %s" % (label, field))

    expected_marker = FINAL_VERDICT_MARKERS[final_verdict]
    if _require_string(row, "verdictMarker", label) != expected_marker:
        raise IntegrityError("%s verdictMarker disagrees with finalVerdict" % label)

    missing_tokens = _comma_tokens(
        row["missingRequiredComponents"],
        "%s missingRequiredComponents" % label,
        allowed=set(COMPONENT_NAMES),
    )
    missing_by_status = [
        component
        for component, field in zip(COMPONENT_NAMES, COMPONENT_VERDICT_FIELDS)
        if statuses[field] in MISSING_COMPONENT_STATUSES
    ]
    if final_verdict == "INCONCLUSIVE" and missing_tokens != missing_by_status:
        raise IntegrityError(
            "%s missingRequiredComponents disagrees with component statuses: %s vs %s"
            % (label, missing_tokens, missing_by_status)
        )
    if any(
        statuses[COMPONENT_VERDICT_FIELDS[COMPONENT_NAMES.index(component)]]
        not in MISSING_COMPONENT_STATUSES
        for component in missing_tokens
    ):
        raise IntegrityError("%s names a non-missing required component" % label)

    failure_classes = _comma_tokens(
        row["failureClasses"],
        "%s failureClasses" % label,
    )
    _validate_pass_component_evidence(row, label, statuses)
    has_fail = any(status == "FAIL" for status in statuses.values())
    if final_verdict == "GREEN":
        if missing_tokens or failure_classes or any(
            status != "PASS"
            for status in statuses.values()
        ):
            raise IntegrityError("%s GREEN verdict does not have PASS for every component" % label)
    elif final_verdict == "RED":
        if not has_fail or not failure_classes:
            raise IntegrityError("%s RED verdict lacks failed component/failure class" % label)
    elif final_verdict == "INCONCLUSIVE":
        if has_fail or not missing_tokens or failure_classes:
            raise IntegrityError("%s INCONCLUSIVE verdict has contradictory component evidence" % label)
    return {
        "finalVerdict": final_verdict,
        "componentStatuses": statuses,
        "missingRequiredComponents": missing_tokens,
        "failureClasses": failure_classes,
    }


def _validate_store_fact(row, value_field, bits_field, label, allow_missing_pair=False):
    value = row.get(value_field)
    bits = row.get(bits_field)
    if value == "none" and bits == "none":
        return {"present": False, "value": "none", "bits": "none"}
    if value == "none" or bits == "none":
        if allow_missing_pair:
            return None
        raise IntegrityError("%s must use both decimal and raw bits or two explicit none values" % label)
    if not isinstance(bits, str) or not re.fullmatch(r"[0-9a-f]{16}", bits):
        raise IntegrityError("%s raw bits must be exactly 16 lowercase hexadecimal digits" % label)
    decimal = _finite_decimal(value, "%s decimal" % label)
    try:
        double_value = float(value)
    except (OverflowError, ValueError):
        raise IntegrityError("%s is not a finite double: %s" % (label, value))
    if not math.isfinite(double_value):
        raise IntegrityError("%s is not a finite double: %s" % (label, value))
    actual_bits = struct.pack(">d", double_value).hex()
    if actual_bits != bits:
        raise IntegrityError(
            "%s decimal/raw-bits mismatch: %s encodes %s, not %s"
            % (label, value, actual_bits, bits)
        )
    return {"present": True, "value": value, "decimal": decimal, "bits": bits}


def _position_tuple(value, label):
    canonical = _canonical_int_pos(value, label)
    return tuple(int(part) for part in canonical.split(","))


def _block_state_id(value):
    if not isinstance(value, str):
        return None
    match = re.match(r"^Block\{([^}]+)\}", value)
    return match.group(1) if match else None


def _state_property(value, key):
    if not isinstance(value, str):
        return None
    match = re.search(r"(?:\[|,)" + re.escape(key) + r"=([^,\]]+)", value)
    return match.group(1).lower() if match else None


def _c3_pair_family(row, pair_pos):
    primary_state = row.get("afterState")
    pair_state = row.get("pairState")
    primary_id = _block_state_id(primary_state)
    pair_id = _block_state_id(pair_state)
    primary_pos = _position_tuple(row.get("placementPos"), "C3 primary placementPos")
    pair_part = str(row.get("pairPart", "")).lower()
    errors = []

    primary_half = _state_property(primary_state, "half")
    pair_half = _state_property(pair_state, "half")
    if primary_half in {"lower", "upper"} or pair_half in {"lower", "upper"}:
        family = "door" if primary_id and primary_id.endswith("_door") else "double_block"
        expected = "upper" if primary_half == "lower" else "lower"
        if primary_id is None or pair_id != primary_id:
            errors.append("state_block")
        if primary_half not in {"lower", "upper"} or pair_half != expected or pair_part != expected:
            errors.append("state_part")
        if not (
            primary_pos[0] == pair_pos[0]
            and primary_pos[2] == pair_pos[2]
            and abs(primary_pos[1] - pair_pos[1]) == 1
        ):
            errors.append("position")
        return family, errors

    primary_bed_part = _state_property(primary_state, "part")
    pair_bed_part = _state_property(pair_state, "part")
    if primary_bed_part in {"foot", "head"} or pair_bed_part in {"foot", "head"}:
        expected = "head" if primary_bed_part == "foot" else "foot"
        if primary_id is None or not primary_id.endswith("_bed") or pair_id != primary_id:
            errors.append("state_block")
        if primary_bed_part not in {"foot", "head"} or pair_bed_part != expected or pair_part != expected:
            errors.append("state_part")
        if not (
            primary_pos[1] == pair_pos[1]
            and abs(primary_pos[0] - pair_pos[0]) + abs(primary_pos[2] - pair_pos[2]) == 1
        ):
            errors.append("position")
        return "bed", errors

    return None, ["state_family"]


def _schema6_store_fact(row, value_field, bits_field, label):
    value = row.get(value_field)
    bits = row.get(bits_field)
    if _has_evidence(value) and bits == "none":
        decimal = _finite_decimal(value, "%s decimal" % label)
        return {
            "present": True,
            "value": value,
            "decimal": decimal,
            "bits": "none",
        }
    return _validate_store_fact(row, value_field, bits_field, label)


def _validate_c3_action_fields(row, schema6=False):
    action_id = row.get("actionId", "unknown")
    label = "action %s C3 primary store" % action_id
    primary_fact = (
        _schema6_store_fact(
            row, "afterStoredDy", "afterStoredDyBits", label
        )
        if schema6
        else _validate_store_fact(
            row, "afterStoredDy", "afterStoredDyBits", label
        )
    )
    pair_values = [row.get(field) for field in C3_PAIR_FIELDS[2:]]
    pair_claim = any(value != "none" for value in pair_values)
    pair_complete = all(value != "none" for value in pair_values)
    markers = set()
    details = {}
    pair_fact = {"present": False, "value": "none", "bits": "none"}
    family = None

    if not pair_claim:
        return {
            "pairClaim": False,
            "pairComplete": False,
            "primaryFact": primary_fact,
            "pairFact": pair_fact,
            "family": None,
            "markers": [],
            "details": {},
        }

    if not pair_complete:
        markers.add("ADAPTER_C3_PAIR_FIELDS_MISSING")
        details["missingFields"] = [
            field for field in C3_PAIR_FIELDS[2:] if row.get(field) == "none"
        ]

    pair_pos = None
    if row.get("pairPos") != "none":
        pair_pos = _position_tuple(row.get("pairPos"), "action %s pairPos" % action_id)
    if row.get("pairAfterDy") != "none":
        _finite_decimal(row.get("pairAfterDy"), "action %s pairAfterDy" % action_id)

    pair_value = row.get("pairStoredDy")
    pair_bits = row.get("pairStoredDyBits")
    if pair_value != "none" and pair_bits != "none":
        pair_fact = _validate_store_fact(
            row,
            "pairStoredDy",
            "pairStoredDyBits",
            "action %s C3 pair store" % action_id,
        )
    elif schema6 and _has_evidence(pair_value) and pair_bits == "none":
        pair_fact = _schema6_store_fact(
            row,
            "pairStoredDy",
            "pairStoredDyBits",
            "action %s C3 pair store" % action_id,
        )
    elif pair_value != "none" or pair_bits != "none":
        markers.add("ADAPTER_C3_PAIR_FIELDS_MISSING")

    one_cell = pair_pos is not None and _canonical_int_pos(
        row.get("pairPos"), "action %s pairPos" % action_id
    ) == _canonical_int_pos(row.get("placementPos"), "action %s placementPos" % action_id)
    if one_cell:
        markers.add("ADAPTER_C3_PAIR_ONE_CELL")

    if pair_complete and pair_pos is not None:
        family, semantic_errors = _c3_pair_family(row, pair_pos)
        if semantic_errors:
            details["semanticDifferences"] = semantic_errors
            if not (one_cell and semantic_errors == ["position"]):
                markers.add("ADAPTER_C3_SIDE_PAIR_FIELD_SPLIT")

    if pair_complete and (not primary_fact["present"] or not pair_fact["present"]):
        markers.add("ADAPTER_C3_PAIR_FIELDS_MISSING")
    elif pair_complete and primary_fact["present"] and pair_fact["present"]:
        primary_live = _finite_decimal(row.get("afterDy"), "action %s afterDy" % action_id)
        pair_live = _finite_decimal(row.get("pairAfterDy"), "action %s pairAfterDy" % action_id)
        exact_pair = (
            primary_fact["bits"] != "none"
            and pair_fact["bits"] != "none"
            and primary_fact["bits"] == pair_fact["bits"]
            and primary_live == primary_fact["decimal"]
            and pair_live == pair_fact["decimal"]
            and primary_live == pair_live
        )
        if not exact_pair:
            markers.add("ADAPTER_C3_PRIMARY_PAIR_BITS_SPLIT")
            details["primaryPairAuthority"] = {
                "primaryLive": row.get("afterDy"),
                "primaryStored": primary_fact["value"],
                "primaryBits": primary_fact["bits"],
                "pairLive": row.get("pairAfterDy"),
                "pairStored": pair_fact["value"],
                "pairBits": pair_fact["bits"],
            }

    return {
        "pairClaim": pair_claim,
        "pairComplete": pair_complete,
        "primaryFact": primary_fact,
        "pairFact": pair_fact,
        "family": family if not details.get("semanticDifferences") else None,
        "markers": sorted(markers),
        "details": details,
    }


def _validate_explicit_green_action(row):
    marker = row.get("marker", "none")
    if "LIVE_GREEN_PLACEMENT_AUTHORING" not in _marker_tokens(marker):
        return
    action_id = row.get("actionId", "unknown")
    if marker != "LIVE_GREEN_PLACEMENT_AUTHORING":
        raise IntegrityError("explicit-green action %s has combined marker %s" % (action_id, marker))
    if row.get("actionOrigin") != PLAYER_ORIGIN or row.get("actionType") != "place_block":
        raise IntegrityError("explicit-green action %s is not player-authored placement" % action_id)
    if row.get("actualResult", "").startswith("Fail["):
        raise IntegrityError("explicit-green action %s has an unclassified Fail[] result" % action_id)
    expected = _finite_decimal(row.get("expectedAfterDy"), "action %s expectedAfterDy" % action_id)
    after = _finite_decimal(row.get("afterDy"), "action %s afterDy" % action_id)
    if expected >= Decimal("-0.000001") or expected != after:
        raise IntegrityError("explicit-green action %s violates its dy predicate" % action_id)
    lane = row.get("afterLaneKind")
    client_exception = row.get("side") == "client" and lane == "unnamed_or_vanilla_slab"
    if lane not in LAWFUL_LOWERED_LANES and not client_exception:
        raise IntegrityError("explicit-green action %s has unlawful lane %s" % (action_id, lane))


def _marker_tokens(value):
    if not value or value == "none":
        return []
    return [token for token in value.split("|") if token and token != "none"]


def _validate_marker_contract(row):
    row_type = row["type"]
    if row_type == "break":
        return
    marker_field = MARKER_FIELD_BY_TYPE.get(row_type)
    if marker_field is None or marker_field not in row:
        raise IntegrityError("%s row is missing its marker field" % row_type)
    marker = row[marker_field]
    if not isinstance(marker, str) or not marker:
        raise IntegrityError("%s row has missing marker" % row_type)
    if row_type == "placement_attempt":
        final_verdict = _require_string(row, "finalVerdict", "placement_attempt")
        expected = FINAL_VERDICT_MARKERS.get(final_verdict)
        if expected is None or marker != expected:
            raise IntegrityError(
                "placement_attempt marker/finalVerdict contradiction: %s/%s"
                % (marker, final_verdict)
            )
        return
    if row_type == "model_stale_sentinel":
        kind = _require_string(row, "kind", "sentinel")
        severity = _require_string(row, "severity", "sentinel")
        if kind in SENTINEL_RED_KINDS:
            expected_severity = "red"
        elif kind in SENTINEL_INFO_KINDS:
            expected_severity = "info"
        else:
            expected_severity = "yellow"
        expected_marker = ("INFO_" if expected_severity == "info" else "LIVE_") + kind
        if severity != expected_severity or marker != expected_marker:
            raise IntegrityError(
                "sentinel kind/severity/marker contradiction: %s/%s/%s"
                % (kind, severity, marker)
            )
        return
    allowed_red = RED_MARKERS_BY_TYPE.get(row_type)
    if allowed_red is None:
        return
    if marker == "none":
        return
    tokens = marker.split("|")
    if any(not token or token == "none" for token in tokens) or len(tokens) != len(set(tokens)):
        raise IntegrityError("%s row has malformed marker list: %s" % (row_type, marker))
    green = GREEN_MARKER_BY_TYPE.get(row_type)
    if green in tokens:
        if marker != green:
            raise IntegrityError("%s row combines green with another marker: %s" % (row_type, marker))
        return
    unknown = sorted(set(tokens) - allowed_red)
    if unknown:
        raise IntegrityError("%s row has unknown marker(s): %s" % (row_type, ", ".join(unknown)))


def _require_nonnegative_integer(row, key, label):
    value = _require_string(row, key, label)
    if not re.fullmatch(r"[0-9]+", value):
        raise IntegrityError("%s has invalid nonnegative integer %s=%r" % (label, key, value))
    return int(value)


def _validate_cursor_row(row):
    label = "cursor %s" % row.get("rowId", "unknown")
    for key in [
        "heldItem", "finalHitType", "finalHitPos", "finalHitFace", "finalHitVec",
        "finalHitState", "finalDy", "finalOwnerLaneKind", "finalOutlineReplayHit",
        "finalRaycastReplayHit", "outlineBounds",
    ]:
        _require_string(row, key, label, allow_none=True)
    hit_type = row["finalHitType"]
    if hit_type not in {"BLOCK", "MISS", "ENTITY", "null"}:
        raise IntegrityError("%s has invalid finalHitType %r" % (label, hit_type))
    if hit_type == "BLOCK":
        _canonical_int_pos(row["finalHitPos"], "%s finalHitPos" % label)
        _canonical_hit_vec(row["finalHitVec"], "%s finalHitVec" % label)
        if row["finalHitFace"].lower() not in {"up", "down", "north", "south", "east", "west"}:
            raise IntegrityError("%s has invalid finalHitFace %r" % (label, row["finalHitFace"]))
        _require_string(row, "finalHitState", label)
        _finite_decimal(row["finalDy"], "%s finalDy" % label)
        _require_string(row, "finalOwnerLaneKind", label, allow_none=True)
    elif hit_type == "null":
        null_fields = [
            "finalHitPos", "finalHitFace", "finalHitVec", "finalHitState",
            "finalOwnerLaneKind", "finalOutlineReplayHit", "finalRaycastReplayHit",
        ]
        if any(row[key] != "none" for key in null_fields) or row["finalDy"] != "NaN":
            raise IntegrityError("%s has malformed null-target geometry" % label)
    if row["mismatchMarker"] == "LIVE_GREEN_CURSOR_TRIAD" and not (
        hit_type == "BLOCK"
        and row["finalOutlineReplayHit"].startswith("hit")
        and row["finalRaycastReplayHit"].startswith("hit")
    ):
        raise IntegrityError("%s violates the explicit-green cursor predicate" % label)


def _validate_outline_row(row):
    label = "rendered outline %s" % row.get("outlineRenderId", "unknown")
    for key in OUTLINE_HEADER:
        _require_string(row, key, label, allow_none=True)
    if not re.fullmatch(r"[0-9]+", row["cursorRowId"]):
        raise IntegrityError("%s has invalid cursorRowId %r" % (label, row["cursorRowId"]))
    _canonical_int_pos(row["renderedOutlinePos"], "%s renderedOutlinePos" % label)
    _canonical_hit_vec(row["renderedOutlineHitVec"], "%s renderedOutlineHitVec" % label)
    _require_string(row, "renderedOutlineState", label)
    for key in [
        "renderedOutlineBounds", "cursorOutlineBounds", "renderedOutlineWorldBounds",
        "renderedOutlineCameraRelativeBounds",
    ]:
        _require_string(row, key, label, allow_none=True)
    if row["cursorFinalHitPos"] != "none":
        _canonical_int_pos(row["cursorFinalHitPos"], "%s cursorFinalHitPos" % label)


def _validate_sentinel_row(row):
    label = "sentinel %s" % row.get("rowId", "unknown")
    _canonical_int_pos(_require_string(row, "pos", label), "%s pos" % label)
    _require_string(row, "armedReason", label)
    _require_nonnegative_integer(row, "ticksSinceArm", label)
    kind = row["kind"]
    if kind.startswith("ENSEMBLE_"):
        _canonical_int_pos(_require_string(row, "pairPos", label), "%s pairPos" % label)
        for key in ["dyLower", "dyUpper", "depth"]:
            _finite_decimal(_require_string(row, key, label), "%s %s" % (label, key))
        _require_string(row, "lowerState", label)
        _require_string(row, "upperState", label)
        return
    _canonical_int_pos(_require_string(row, "section", label), "%s section" % label)
    baked = _require_string(row, "bakedDy", label, allow_none=True)
    if baked != "NO_BAKE":
        _finite_decimal(baked, "%s bakedDy" % label)
    baseline = _require_string(row, "baselineDy", label, allow_none=True)
    if baseline != "none":
        _finite_decimal(baseline, "%s baselineDy" % label)
    _finite_decimal(_require_string(row, "liveDy", label), "%s liveDy" % label)
    _require_string(row, "blockState", label)
    _require_nonnegative_integer(row, "mismatchSamples", label)


def _validate_break_row(row):
    label = "break %s" % row.get("rowId", "unknown")
    if _require_string(row, "side", label) not in ("client", "server"):
        raise IntegrityError("%s has invalid side %r" % (label, row.get("side")))
    _require_string(row, "player", label, allow_none=True)
    _canonical_int_pos(_require_string(row, "pos", label), "%s pos" % label)
    for key in ["state", "aboveState", "belowState"]:
        _require_string(row, key, label)
    for key in ["dy", "aboveDy", "belowDy"]:
        _finite_decimal(_require_string(row, key, label), "%s %s" % (label, key))


def _validate_session_row_contract(row):
    _validate_marker_contract(row)
    validators = {
        "cursor": _validate_cursor_row,
        "rendered_outline": _validate_outline_row,
        "model_stale_sentinel": _validate_sentinel_row,
        "break": _validate_break_row,
    }
    validator = validators.get(row["type"])
    if validator is not None:
        validator(row)


def _is_red_marker(token):
    return token.startswith("LIVE_") and not token.startswith("LIVE_GREEN_")


def _source_marker(row):
    field = MARKER_FIELD_BY_TYPE.get(row.get("type"))
    return row.get(field, "none") if field else "none"


def _expected_mismatch(row):
    row_type = row.get("type")
    marker = _source_marker(row)
    if row_type == "model_stale_sentinel":
        return row.get("severity") == "red"
    if row_type in RED_MARKERS_BY_TYPE:
        return any(token in RED_MARKERS_BY_TYPE[row_type] for token in _marker_tokens(marker))
    return False


def _derived_summary(rows, schema):
    keys = SCHEMA6_SUMMARY_KEYS if schema == SCHEMA6_VERSION else SUMMARY_KEYS
    counters = {key: 0 for key in keys}
    for row in rows:
        row_type = row["type"]
        marker = _source_marker(row)
        tokens = _marker_tokens(marker)
        if row_type == "cursor":
            counters["cursorRows"] += 1
            counters["ghostSurfaceRows"] += int("LIVE_CURSOR_GHOST_SURFACE" in tokens)
            counters["hiddenOwnerRows"] += int("LIVE_CURSOR_HIDDEN_OWNER" in tokens)
            counters["outlineRaycastSplitRows"] += int("LIVE_CURSOR_OUTLINE_RAYCAST_SPLIT" in tokens)
            counters["collisionIteratorTargetMissRows"] += int("LIVE_COLLISION_ITERATOR_TARGET_MISS" in tokens)
            counters["collisionIteratorTargetPresentRows"] += int(
                row.get("playerBlockCollisionTargetIntersectsReturned", "false") == "true"
            )
            counters["liveGreenCursorTriadRows"] += int(marker == "LIVE_GREEN_CURSOR_TRIAD")
        elif row_type == "rendered_outline":
            counters["renderedOutlineRows"] += 1
            counters["renderedOutlineLargeBoundsRows"] += int("LIVE_RENDERED_OUTLINE_LARGE_BOUNDS" in tokens)
            counters["renderedOutlineReplayBoundsSplitRows"] += int(
                "LIVE_RENDERED_OUTLINE_REPLAY_BOUNDS_SPLIT" in tokens
            )
            counters["renderedOutlineTargetSplitRows"] += int("LIVE_RENDERED_OUTLINE_TARGET_SPLIT" in tokens)
        elif row_type == "action":
            counters["actionRows"] += 1
            origin = row.get("actionOrigin")
            counters["playerAuthoredActionRows"] += int(origin == PLAYER_ORIGIN)
            counters["autoUseOnProxyActionRows"] += int(origin == PROXY_ORIGIN)
            counters["placementExpectedDyMismatchRows"] += int(
                "LIVE_PLACEMENT_EXPECTED_DY_MISMATCH" in tokens
            )
            counters["placementUnclassifiedFailureRows"] += int(
                "LIVE_PLACEMENT_UNCLASSIFIED_FAILURE" in tokens
            )
            counters["placementExpectedLaneMismatchRows"] += int(
                "LIVE_PLACEMENT_EXPECTED_LANE_MISMATCH" in tokens
            )
            counters["loweredSideSlabPlacementVanillaDyRows"] += int(
                "LIVE_PLACEMENT_VANILLA_DY_FROM_LOWERED_OWNER" in tokens
            )
            counters["liveGreenPlacementRows"] += int(marker == "LIVE_GREEN_PLACEMENT_AUTHORING")
            counters["placementSideDySplitRows"] += int("LIVE_PLACEMENT_SIDE_DY_SPLIT" in tokens)
            if schema == SCHEMA6_VERSION:
                verdict_counter = {
                    "GREEN": "placementVerdictGreenRows",
                    "RED": "placementVerdictRedRows",
                    "INCONCLUSIVE": "placementVerdictInconclusiveRows",
                    "EXPECTED_REFUSAL": "placementVerdictExpectedRefusalRows",
                    "UNCLASSIFIED_FAILURE": "placementVerdictUnclassifiedFailureRows",
                }.get(row.get("finalVerdict"))
                if verdict_counter is None:
                    raise IntegrityError(
                        "schema-6 action %s has invalid finalVerdict for summary"
                        % row.get("actionId")
                    )
                counters[verdict_counter] += 1
        elif row_type == "placement_attempt":
            if schema != SCHEMA6_VERSION:
                raise IntegrityError("placement_attempt row is not valid in schema 3")
            counters["logicalAttemptRows"] += 1
            status_counter = {
                "MERGED_CLIENT_SERVER": "mergedClientServerAttemptRows",
                "AUTO_PROXY": "autoProxyLogicalAttemptRows",
                "SERVER_ONLY": "serverOnlyLogicalAttemptRows",
                "CLIENT_ONLY": "clientOnlyLogicalAttemptRows",
            }.get(row.get("attemptStatus"))
            if status_counter is None:
                raise IntegrityError(
                    "placement_attempt has invalid status for summary: %r"
                    % row.get("attemptStatus")
                )
            counters[status_counter] += 1
            if row.get("playerProof") == "PRESENT":
                counters["playerProofLogicalAttemptRows"] += 1
            verdict_counter = {
                "GREEN": "logicalAttemptVerdictGreenRows",
                "RED": "logicalAttemptVerdictRedRows",
                "INCONCLUSIVE": "logicalAttemptVerdictInconclusiveRows",
                "EXPECTED_REFUSAL": "logicalAttemptVerdictExpectedRefusalRows",
                "UNCLASSIFIED_FAILURE": "logicalAttemptVerdictUnclassifiedFailureRows",
            }.get(row.get("finalVerdict"))
            if verdict_counter is None:
                raise IntegrityError(
                    "placement_attempt has invalid finalVerdict for summary"
                )
            counters[verdict_counter] += 1
            if (
                row.get("playerProof") == "PRESENT"
                and row.get("finalVerdict") == "GREEN"
            ):
                counters["playerProofGreenLogicalAttemptRows"] += 1
        elif row_type == "break":
            counters["breakRows"] += 1
        elif row_type == "model_stale_sentinel":
            kind = row.get("kind", "")
            severity = row.get("severity", "")
            if severity == "red":
                if kind.startswith("ENSEMBLE_"):
                    counters["ensembleClashRows"] += 1
                elif kind == "MODEL_STALE_DIVERGENT":
                    counters["modelStaleDivergentRows"] += 1
                elif kind == "MODEL_STALE_ABSENT":
                    counters["modelStaleAbsentRows"] += 1
            elif severity == "info":
                counters["ensembleOccludedOccupancyInfoRows"] += 1
            elif severity == "yellow":
                counters["modelStaleYellowRows"] += 1
            else:
                raise IntegrityError("unknown sentinel severity at global id %d: %s" % (
                    row["_globalId"], severity
                ))
    return counters


def _validate_actions(
    session_rows,
    action_tsv,
    summary,
    action_header,
    schema,
    run_id,
):
    session_actions = [row for row in session_rows if row["type"] == "action"]
    if len(session_actions) != len(action_tsv):
        raise IntegrityError("actions.tsv/session action count mismatch")
    schema6 = schema == SCHEMA6_VERSION
    extended = action_header in (C3_ACTIONS_HEADER, SCHEMA6_ACTIONS_HEADER)
    if schema6 != (action_header == SCHEMA6_ACTIONS_HEADER):
        raise IntegrityError("actions.tsv header does not match manifest schema")
    tsv_by_id = {}
    for row in action_tsv:
        raw_id = row.get("actionId")
        if not re.fullmatch(r"[1-9][0-9]*", raw_id or ""):
            raise IntegrityError("actions.tsv has invalid actionId: %r" % raw_id)
        action_id = int(raw_id)
        if action_id in tsv_by_id:
            raise IntegrityError("actions.tsv duplicate actionId: %d" % action_id)
        tsv_by_id[action_id] = row
    for row in session_actions:
        origin = row.get("actionOrigin")
        if origin not in (PLAYER_ORIGIN, PROXY_ORIGIN):
            raise IntegrityError("unknown actionOrigin at action %s: %r" % (row.get("actionId"), origin))
        present_c3_fields = [field for field in C3_PAIR_FIELDS if field in row]
        if present_c3_fields and len(present_c3_fields) != len(C3_PAIR_FIELDS):
            raise IntegrityError(
                "action %s has partial extended JSON fields: %s"
                % (row.get("actionId"), ", ".join(present_c3_fields))
            )
        if extended and len(present_c3_fields) != len(C3_PAIR_FIELDS):
            raise IntegrityError("extended actions.tsv requires complete extended JSON action rows")
        if not extended and present_c3_fields:
            raise IntegrityError("legacy actions.tsv cannot accompany extended JSON action rows")
        present_schema6_fields = [
            field for field in SCHEMA6_ACTION_FIELDS if field in row
        ]
        if schema6 and len(present_schema6_fields) != len(SCHEMA6_ACTION_FIELDS):
            raise IntegrityError(
                "schema-6 actions.tsv requires complete logical-attempt JSON fields"
            )
        if not schema6 and present_schema6_fields:
            raise IntegrityError(
                "schema-3 action row cannot contain schema-6 logical-attempt fields"
            )
        required_action_fields = [
            "side",
            "actionType",
            "heldItem",
            "clickedOwnerPos",
            "clickedFace",
            "recordedAt",
            "actualResult",
        ]
        if not schema6:
            required_action_fields.extend(["player", "clickedHitVec"])
        for key in required_action_fields:
            _require_string(row, key, "action %s" % row.get("actionId"), allow_none=False)
        action_id = row.get("actionId")
        if row.get("side") not in ("client", "server"):
            raise IntegrityError("action %s has invalid side %r" % (action_id, row.get("side")))
        _canonical_int_pos(row.get("clickedOwnerPos"), "action %s clickedOwnerPos" % action_id)
        if schema6:
            player = row.get("player")
            if player is not None and not isinstance(player, str):
                raise IntegrityError("action %s player is not a string" % action_id)
            clicked_hit_vec = row.get("clickedHitVec")
            if _java_has_evidence(clicked_hit_vec):
                _canonical_hit_vec(
                    clicked_hit_vec,
                    "action %s clickedHitVec" % action_id,
                )
        else:
            _canonical_hit_vec(row.get("clickedHitVec"), "action %s clickedHitVec" % action_id)
        if row.get("clickedFace").lower() not in {"up", "down", "north", "south", "east", "west"}:
            raise IntegrityError("action %s has invalid clickedFace %r" % (action_id, row.get("clickedFace")))
        placement_pos = _require_string(
            row, "placementPos", "action %s" % row.get("actionId"), allow_none=True
        )
        _require_string(row, "marker", "action %s" % row.get("actionId"), allow_none=True)
        if placement_pos == "none":
            if row.get("actionType") == "place_block" or row.get("actualResult", "").startswith("Success["):
                raise IntegrityError(
                    "successful place_block action %s cannot use the early non-success none shape"
                    % action_id
                )
            none_fields = [
                "placeBeforeState",
                "placeBeforeDy",
                "afterState",
                "afterDy",
                "afterLaneKind",
                "afterPersistentLoweredSlabCarrier",
            ]
            if any(row.get(field) != "none" for field in none_fields):
                raise IntegrityError(
                    "early non-success action %s has non-none transformed/final fields" % action_id
                )
            if extended and any(row.get(field) != "none" for field in C3_PAIR_FIELDS):
                raise IntegrityError(
                    "early non-success action %s has non-none store/pair fields" % action_id
                )
        else:
            _canonical_int_pos(placement_pos, "action %s placementPos" % action_id)
            _require_string(row, "afterLaneKind", "action %s" % action_id, allow_none=True)
            if not schema6:
                _require_string(row, "afterDy", "action %s" % action_id, allow_none=False)
                if row.get("actionType") == "place_block":
                    _require_string(row, "afterState", "action %s" % action_id, allow_none=False)
                    _finite_decimal(row.get("afterDy"), "action %s afterDy" % action_id)
            _validate_explicit_green_action(row)
        if extended:
            row["_c3"] = _validate_c3_action_fields(row, schema6=schema6)
        if schema6:
            label = "schema-6 action %s" % action_id
            logical_attempt_id = _require_string(
                row, "logicalAttemptId", label
            )
            row["_logicalAttemptNumber"] = _logical_attempt_number(
                logical_attempt_id,
                run_id,
                label,
            )
            phase = _require_string(row, "phase", label)
            player_proof = _require_string(row, "playerProof", label)
            if phase not in RAW_PHASES:
                raise IntegrityError("%s has invalid phase %r" % (label, phase))
            expected_phase = (
                "AUTO_PROXY"
                if origin == PROXY_ORIGIN
                else "CLIENT_PREDICTION"
                if row["side"] == "client"
                else "SERVER_AUTHORITY"
            )
            expected_proof = "ABSENT" if origin == PROXY_ORIGIN else "PRESENT"
            if phase != expected_phase or player_proof != expected_proof:
                raise IntegrityError(
                    "%s origin/side/phase/playerProof contradiction: %s/%s/%s/%s"
                    % (
                        label,
                        origin,
                        row["side"],
                        phase,
                        player_proof,
                    )
                )
            if origin == PROXY_ORIGIN and row["side"] != "server":
                raise IntegrityError("%s AUTO_PROXY must be a server action row" % label)
            row["_canonicalVerdict"] = _validate_canonical_verdict(row, label)
            if (
                _has_evidence(row.get("storedDy"))
                and _has_evidence(row.get("afterStoredDy"))
                and _finite_decimal(row["storedDy"], "%s storedDy" % label)
                != _finite_decimal(row["afterStoredDy"], "%s afterStoredDy" % label)
            ):
                raise IntegrityError("%s storedDy disagrees with afterStoredDy" % label)
            if (
                row["finalVerdict"] == "RED"
                and row["verdictMarker"] not in _marker_tokens(row["marker"])
            ):
                raise IntegrityError("%s RED verdict marker is absent from action marker" % label)
        mirror = tsv_by_id.get(int(row["actionId"]))
        if mirror is None:
            raise IntegrityError("session action missing from actions.tsv: %s" % row["actionId"])
        for field in action_header:
            if row.get(field, "") != mirror.get(field, ""):
                raise IntegrityError("actions.tsv/session mismatch action %s field %s" % (
                    row["actionId"], field
                ))
    if summary["actionRows"] != len(session_actions):
        raise IntegrityError("summary actionRows disagrees with session/actions.tsv")
    player = sum(row["actionOrigin"] == PLAYER_ORIGIN for row in session_actions)
    proxy = sum(row["actionOrigin"] == PROXY_ORIGIN for row in session_actions)
    if summary["playerAuthoredActionRows"] != player:
        raise IntegrityError("summary playerAuthoredActionRows disagrees with session")
    if summary["autoUseOnProxyActionRows"] != proxy:
        raise IntegrityError("summary autoUseOnProxyActionRows disagrees with session")
    return session_actions


def _has_attempt_evidence(value):
    return _java_has_evidence(value)


def _logical_conflict_name(field):
    snake = re.sub(r"([a-z])([A-Z])", r"\1_\2", field).upper()
    return "LOGICAL_ATTEMPT_%s_CONFLICT" % snake


def _attempt_evidence(row, field):
    return None if row is None else row.get(field)


def _first_attempt_evidence(row, *fields):
    if row is None:
        return None
    for field in fields:
        value = row.get(field)
        if _has_attempt_evidence(value):
            return value
    return None


def _logical_attempt_evidence(client, server):
    evidence = {}
    conflicts = []

    def detect_conflict(field, preferred_value, alternate_value, component):
        if (
            _has_attempt_evidence(preferred_value)
            and _has_attempt_evidence(alternate_value)
            and not _java_same_evidence(preferred_value, alternate_value)
        ):
            conflict_name = _logical_conflict_name(field)
            if not any(name == conflict_name for name, _ in conflicts):
                conflicts.append((conflict_name, component))

    def select(field, preferred, alternate, component):
        preferred_value = _attempt_evidence(preferred, field)
        alternate_value = _attempt_evidence(alternate, field)
        detect_conflict(
            field,
            preferred_value,
            alternate_value,
            component,
        )
        selected = (
            preferred_value
            if _has_attempt_evidence(preferred_value)
            else alternate_value
            if _has_attempt_evidence(alternate_value)
            else None
        )
        if _has_attempt_evidence(selected):
            evidence[field] = selected

    for field in [
        "actionType",
        "heldItem",
        "clickedOwnerPos",
        "clickedFace",
        "placementPos",
        "rigCaseId",
        "placementRoute",
        "landingAuthority",
        "expectedAfterDy",
        "intentDy",
        "expectedAfterLaneKind",
        "expectedResult",
        "placementContract",
        "refusalContract",
        "expectedRefusalReason",
        "clickedOwnerLaneKind",
        "beforeDy",
    ]:
        select(field, server, client, "PLACED")

    for field in [
        "actualResult",
        "actualRefusalReason",
        "afterDy",
        "afterState",
        "afterLaneKind",
        "stabilityVerdict",
    ]:
        component = "STABILITY" if field == "stabilityVerdict" else "PLACED"
        select(field, server, client, component)

    server_stored_dy = _first_attempt_evidence(
        server,
        "storedDy",
        "afterStoredDy",
    )
    client_stored_dy = _first_attempt_evidence(
        client,
        "storedDy",
        "afterStoredDy",
    )
    detect_conflict(
        "storedDy",
        server_stored_dy,
        client_stored_dy,
        "ANCHOR",
    )
    stored_dy = (
        server_stored_dy
        if _has_attempt_evidence(server_stored_dy)
        else client_stored_dy
    )
    if _has_attempt_evidence(stored_dy):
        evidence["afterStoredDy"] = stored_dy

    for field in [
        "modelDy",
        "collisionDy",
        "raycastDy",
        "outlineDy",
        "expectedSupportPlane",
        "actualContactPlane",
        "seatError",
    ]:
        component = {
            "modelDy": "MODEL",
            "raycastDy": "RAYCAST",
            "outlineDy": "OUTLINE",
        }.get(field, "COLLISION")
        select(field, client, server, component)
    return evidence, conflicts


def _apply_java_logical_conflicts(derived, conflicts):
    if not conflicts:
        return derived
    expected = dict(derived)
    expected["finalVerdict"] = "RED"
    for _, component in conflicts:
        field = COMPONENT_VERDICT_FIELDS[COMPONENT_NAMES.index(component)]
        expected[field] = "FAIL"
    missing_components = [
        component
        for component, field in zip(COMPONENT_NAMES, COMPONENT_VERDICT_FIELDS)
        if expected[field] in {"UNKNOWN", "MISSING", "NOT_RUN"}
    ]
    expected["missingRequiredComponents"] = (
        "none" if not missing_components else ",".join(missing_components)
    )
    failure_classes = (
        []
        if expected["failureClasses"] == "none"
        else expected["failureClasses"].split(",")
    )
    for conflict_name, _ in conflicts:
        _append_unique(failure_classes, conflict_name)
    expected["failureClasses"] = (
        "none" if not failure_classes else ",".join(failure_classes)
    )
    return expected


def _crosscheck_terminal_evidence(terminal, client, server):
    label = "placement_attempt %s" % terminal["logicalAttemptId"]
    evidence, conflicts = _logical_attempt_evidence(client, server)
    derived = _apply_java_logical_conflicts(
        _derive_java_placement_verdict(evidence),
        conflicts,
    )

    direct_evidence_fields = [
        "actionType",
        "heldItem",
        "clickedOwnerPos",
        "clickedFace",
        "placementPos",
        "expectedAfterDy",
        "expectedAfterLaneKind",
        "expectedResult",
        "placementContract",
        "refusalContract",
        "clickedOwnerLaneKind",
        "beforeDy",
        "actualResult",
        "afterDy",
        "afterState",
        "afterLaneKind",
        "afterStoredDy",
    ]
    for field in direct_evidence_fields:
        expected_value = evidence.get(field)
        if expected_value is None:
            if field in terminal:
                raise IntegrityError(
                    "%s terminal invents raw evidence field %s" % (label, field)
                )
            continue
        if terminal.get(field) != expected_value:
            raise IntegrityError(
                "%s terminal/raw evidence mismatch %s: %r != %r"
                % (label, field, terminal.get(field), expected_value)
            )

    for field, expected_value in derived.items():
        if terminal.get(field) != expected_value:
            raise IntegrityError(
                "%s derived terminal verdict/evidence mismatch %s: %r != %r"
                % (label, field, terminal.get(field), expected_value)
            )
    terminal["_derivedCanonicalVerdict"] = derived
    terminal["_logicalConflicts"] = [name for name, _ in conflicts]


def _java_correlation_value(row, key):
    value = row.get(key)
    return value.strip() if _java_has_evidence(value) else "none"


def _java_first_correlation_value(row, *keys):
    for key in keys:
        value = row.get(key)
        if _java_has_evidence(value):
            return value.strip()
    return "none"


def _schema6_attempt_key(row):
    return (
        _java_correlation_value(row, "actionType"),
        _java_correlation_value(row, "heldItem"),
        _java_correlation_value(row, "clickedOwnerPos"),
        _java_correlation_value(row, "clickedFace"),
        _java_correlation_value(row, "placementPos"),
        _java_correlation_value(row, "rigCaseId"),
        _java_first_correlation_value(
            row,
            "playerUuid",
            "playerId",
            "player",
            "playerName",
        ),
        _java_first_correlation_value(
            row,
            "dimensionId",
            "dimension",
            "level",
            "world",
        ),
    )


def _validate_logical_attempts(session_rows, actions, manifest):
    terminals = [
        row for row in session_rows if row["type"] == "placement_attempt"
    ]
    actions_by_id = {row["actionId"]: row for row in actions}
    actions_by_logical = defaultdict(list)
    for row in actions:
        actions_by_logical[row["logicalAttemptId"]].append(row)

    terminals_by_logical = {}
    referenced_action_ids = set()
    run_id = manifest["runId"]
    for terminal in terminals:
        label = "placement_attempt line %s" % terminal["_sessionIndex"]
        forbidden = sorted(TERMINAL_FORBIDDEN_FIELDS.intersection(terminal))
        if forbidden:
            raise IntegrityError(
                "%s contains forbidden invented fields: %s"
                % (label, ", ".join(forbidden))
            )
        logical_attempt_id = _require_string(
            terminal,
            "logicalAttemptId",
            label,
        )
        terminal["_logicalAttemptNumber"] = _logical_attempt_number(
            logical_attempt_id,
            run_id,
            label,
        )
        if terminal.get("rowId") != "attempt:" + logical_attempt_id:
            raise IntegrityError("%s has invalid terminal rowId" % label)
        if terminal.get("terminal") != "true":
            raise IntegrityError("%s must use terminal=true" % label)
        if logical_attempt_id in terminals_by_logical:
            raise IntegrityError(
                "duplicate placement_attempt terminal for %s" % logical_attempt_id
            )
        terminals_by_logical[logical_attempt_id] = terminal

        attempt_status = _require_string(terminal, "attemptStatus", label)
        if attempt_status not in LOGICAL_ATTEMPT_STATUSES:
            raise IntegrityError("%s has invalid attemptStatus %r" % (label, attempt_status))
        player_proof = _require_string(terminal, "playerProof", label)
        client_action_id = _require_string(
            terminal, "clientActionId", label, allow_none=True
        )
        server_action_id = _require_string(
            terminal, "serverActionId", label, allow_none=True
        )
        action_count = _require_nonnegative_integer(
            terminal,
            "actionCount",
            label,
        )
        expected_shape = {
            "MERGED_CLIENT_SERVER": ("PRESENT", True, True, 2),
            "AUTO_PROXY": ("ABSENT", False, True, 1),
            "SERVER_ONLY": ("PRESENT", False, True, 1),
            "CLIENT_ONLY": ("PRESENT", True, False, 1),
        }[attempt_status]
        expected_proof, has_client, has_server, expected_count = expected_shape
        if (
            player_proof != expected_proof
            or (client_action_id != "none") != has_client
            or (server_action_id != "none") != has_server
            or action_count != expected_count
        ):
            raise IntegrityError(
                "%s status/action-reference/playerProof shape is inconsistent" % label
            )
        referenced_ids = [
            action_id
            for action_id in (client_action_id, server_action_id)
            if action_id != "none"
        ]
        if len(referenced_ids) != len(set(referenced_ids)):
            raise IntegrityError("%s references the same raw action twice" % label)
        for action_id in referenced_ids:
            if not re.fullmatch(r"[1-9][0-9]*", action_id):
                raise IntegrityError("%s has malformed action reference %r" % (label, action_id))
            if action_id in referenced_action_ids:
                raise IntegrityError(
                    "raw action %s is referenced by multiple terminals" % action_id
                )
            referenced_action_ids.add(action_id)
            source = actions_by_id.get(action_id)
            if source is None:
                raise IntegrityError(
                    "%s references missing raw action %s" % (label, action_id)
                )
            if source["logicalAttemptId"] != logical_attempt_id:
                raise IntegrityError(
                    "%s action reference has mismatched logicalAttemptId" % label
                )
            if source["_sessionIndex"] >= terminal["_sessionIndex"]:
                raise IntegrityError(
                    "%s appears before its referenced raw action" % label
                )

        raw_rows = actions_by_logical.get(logical_attempt_id, [])
        if {row["actionId"] for row in raw_rows} != set(referenced_ids):
            raise IntegrityError(
                "%s does not reference exactly its logical raw action rows" % label
            )
        phase_rows = {row["phase"]: row for row in raw_rows}
        if len(phase_rows) != len(raw_rows):
            raise IntegrityError("%s has duplicate raw phases" % label)
        expected_phases = {
            "MERGED_CLIENT_SERVER": {"CLIENT_PREDICTION", "SERVER_AUTHORITY"},
            "AUTO_PROXY": {"AUTO_PROXY"},
            "SERVER_ONLY": {"SERVER_AUTHORITY"},
            "CLIENT_ONLY": {"CLIENT_PREDICTION"},
        }[attempt_status]
        if set(phase_rows) != expected_phases:
            raise IntegrityError("%s raw phases disagree with attemptStatus" % label)
        origins = {row["actionOrigin"] for row in raw_rows}
        expected_origin = PROXY_ORIGIN if attempt_status == "AUTO_PROXY" else PLAYER_ORIGIN
        if origins != {expected_origin}:
            raise IntegrityError("%s terminal/raw origin conflict" % label)
        client = phase_rows.get("CLIENT_PREDICTION")
        server = (
            phase_rows.get("SERVER_AUTHORITY")
            or phase_rows.get("AUTO_PROXY")
        )
        if (
            attempt_status == "MERGED_CLIENT_SERVER"
            and _schema6_attempt_key(client) != _schema6_attempt_key(server)
        ):
            raise IntegrityError(
                "%s terminal-linked rows disagree on Java PlacementAttemptKey"
                % label
            )

        terminal["_canonicalVerdict"] = _validate_canonical_verdict(
            terminal,
            "placement_attempt %s" % logical_attempt_id,
        )
        if terminal["marker"] != terminal["verdictMarker"]:
            raise IntegrityError("%s marker disagrees with verdictMarker" % label)
        _crosscheck_terminal_evidence(terminal, client, server)
        terminal["_rawActionsCrossChecked"] = True

    if set(actions_by_logical) != set(terminals_by_logical):
        missing = sorted(set(actions_by_logical) - set(terminals_by_logical))
        extra = sorted(set(terminals_by_logical) - set(actions_by_logical))
        raise IntegrityError(
            "logical action/terminal coverage mismatch; missing=%s extra=%s"
            % (missing, extra)
        )
    if referenced_action_ids != set(actions_by_id):
        raise IntegrityError("not every schema-6 raw action is referenced by one terminal")

    attempt_numbers = sorted(
        terminal["_logicalAttemptNumber"] for terminal in terminals
    )
    if attempt_numbers != list(range(1, len(attempt_numbers) + 1)):
        raise IntegrityError(
            "logicalAttemptId sequence must be contiguous from attempt-1: %s"
            % attempt_numbers
        )
    return sorted(terminals, key=lambda row: row["_logicalAttemptNumber"])


def _validate_outlines(session_rows, outline_tsv):
    session_outlines = [row for row in session_rows if row["type"] == "rendered_outline"]
    if len(session_outlines) != len(outline_tsv):
        raise IntegrityError("rendered-outlines.tsv/session row count mismatch")
    tsv_by_id = {}
    for row in outline_tsv:
        raw_id = row.get("outlineRenderId")
        if not re.fullmatch(r"[1-9][0-9]*", raw_id or ""):
            raise IntegrityError("rendered-outlines.tsv has invalid outlineRenderId: %r" % raw_id)
        outline_id = int(raw_id)
        if outline_id in tsv_by_id:
            raise IntegrityError("rendered-outlines.tsv duplicate outlineRenderId: %d" % outline_id)
        tsv_by_id[outline_id] = row
    for row in session_outlines:
        mirror = tsv_by_id.get(int(row["outlineRenderId"]))
        if mirror is None:
            raise IntegrityError(
                "session rendered outline missing from rendered-outlines.tsv: %s"
                % row["outlineRenderId"]
            )
        for field in OUTLINE_HEADER:
            if row.get(field, "") != mirror.get(field, ""):
                raise IntegrityError(
                    "rendered-outlines.tsv/session mismatch outline %s field %s"
                    % (row["outlineRenderId"], field)
                )
    return session_outlines


def _authoritative_pos(row):
    row_type = row["type"]
    candidates = {
        "action": ["clickedOwnerPos"],
        "placement_attempt": ["clickedOwnerPos"],
        "cursor": ["finalHitPos"],
        "rendered_outline": ["renderedOutlinePos"],
        "model_stale_sentinel": ["pos"],
    }.get(row_type, [])
    for field in candidates:
        value = row.get(field)
        if value and value != "none":
            return _canonical_int_pos(value, "%s.%s" % (row_type, field)), "session.%s" % field
    return None, "unresolved"


def _mismatch_context(row):
    row_type = row["type"]
    if row_type == "action":
        action_id = row.get("actionId", "unknown")
        placement_pos = row.get("placementPos")
        clicked_hit_vec = row.get("clickedHitVec")
        return {
            "clickedOwnerPos": _canonical_int_pos(
                row.get("clickedOwnerPos"), "action %s clickedOwnerPos" % action_id
            ),
            "clickedHitVec": (
                "none"
                if not _java_has_evidence(clicked_hit_vec)
                else _canonical_hit_vec(
                    clicked_hit_vec,
                    "action %s clickedHitVec" % action_id,
                )
            ),
            "clickedFace": _require_string(
                row, "clickedFace", "action %s" % action_id
            ).lower(),
            "placementPos": (
                "none"
                if placement_pos == "none"
                else _canonical_int_pos(
                    placement_pos, "action %s placementPos" % action_id
                )
            ),
        }
    if row_type == "placement_attempt":
        row_id = row.get("rowId", "unknown")
        placement_pos = row.get("placementPos")
        clicked_hit_vec = row.get("clickedHitVec")
        return {
            "clickedOwnerPos": _canonical_int_pos(
                row.get("clickedOwnerPos"),
                "placement_attempt %s clickedOwnerPos" % row_id,
            ),
            "clickedHitVec": (
                "none"
                if not _has_evidence(clicked_hit_vec)
                else _canonical_hit_vec(
                    clicked_hit_vec,
                    "placement_attempt %s clickedHitVec" % row_id,
                )
            ),
            "clickedFace": _require_string(
                row,
                "clickedFace",
                "placement_attempt %s" % row_id,
            ).lower(),
            "placementPos": (
                "none"
                if placement_pos == "none"
                else _canonical_int_pos(
                    placement_pos,
                    "placement_attempt %s placementPos" % row_id,
                )
            ),
        }
    if row_type == "cursor":
        return {
            "finalHitPos": _canonical_int_pos(row.get("finalHitPos"), "cursor finalHitPos"),
            "finalHitVec": _canonical_hit_vec(row.get("finalHitVec"), "cursor finalHitVec"),
            "finalHitFace": _require_string(row, "finalHitFace", "cursor").lower(),
        }
    if row_type == "rendered_outline":
        return {
            "renderedOutlinePos": _canonical_int_pos(
                row.get("renderedOutlinePos"), "rendered outline pos"
            ),
            "renderedOutlineHitVec": _canonical_hit_vec(
                row.get("renderedOutlineHitVec"), "rendered outline hit vec"
            ),
            "cursorFinalHitPos": row.get("cursorFinalHitPos", "none"),
        }
    if row_type == "model_stale_sentinel":
        return {
            "pos": _canonical_int_pos(row.get("pos"), "sentinel pos"),
            "pairPos": (
                _canonical_int_pos(row.get("pairPos"), "sentinel pairPos")
                if row.get("pairPos") and row.get("pairPos") != "none"
                else "none"
            ),
        }
    return {}


def _mismatch_join_id(row):
    if row["type"] == "placement_attempt":
        return row["rowId"]
    return str(row["_globalId"])


def _validate_mismatches(session_rows, mismatch_rows, schema):
    index = {}
    expected = {}
    for row in session_rows:
        row_type = row["type"]
        if row_type not in MARKER_FIELD_BY_TYPE:
            continue
        key = (row_type, _mismatch_join_id(row))
        if key in index:
            raise IntegrityError("duplicate mismatch join key: %s/%s" % key)
        index[key] = row
        if _expected_mismatch(row):
            expected[key] = _source_marker(row)

    seen = set()
    resolved = []
    for mirror in mismatch_rows:
        key = (mirror["type"], mirror["rowOrActionId"])
        if key in seen:
            raise IntegrityError("duplicate mismatch row: %s/%s" % key)
        seen.add(key)
        source = index.get(key)
        if source is None:
            raise IntegrityError("mismatch row does not join to exactly one session row: %s/%s" % key)
        source_marker = _source_marker(source)
        if mirror["marker"] != source_marker:
            raise IntegrityError("mismatch marker disagrees with session row %s/%s" % key)
        expected_held = source.get("heldItem", source.get("cursorHeldItem", "none"))
        if mirror["heldItem"] != expected_held:
            raise IntegrityError("mismatch heldItem disagrees with session row %s/%s" % key)
        if schema == SCHEMA6_VERSION:
            expected_failures = source.get("failureClasses", "none")
            if mirror.get("failureClasses") != expected_failures:
                raise IntegrityError(
                    "mismatch failureClasses disagrees with session row %s/%s" % key
                )
        resolved_pos, source_name = _authoritative_pos(source)
        if mirror["pos"] != "none":
            tsv_pos = _canonical_int_pos(mirror["pos"], "mismatches.tsv pos")
            if resolved_pos is not None and tsv_pos != resolved_pos:
                raise IntegrityError("mismatch TSV/session position disagreement for %s/%s" % key)
            resolved_pos = tsv_pos
            source_name = "mismatches.tsv"
        elif source["type"] != "model_stale_sentinel":
            raise IntegrityError(
                "non-sentinel mismatch TSV position is missing for %s/%s" % key
            )
        if resolved_pos is None:
            raise IntegrityError("red mismatch position is unresolved for %s/%s" % key)
        pair_pos = None
        if source.get("pairPos") and source.get("pairPos") != "none":
            pair_pos = _canonical_int_pos(source["pairPos"], "session pairPos")
        resolved.append({
            "type": mirror["type"],
            "rowOrActionId": (
                int(mirror["rowOrActionId"])
                if re.fullmatch(r"[1-9][0-9]*", mirror["rowOrActionId"])
                else mirror["rowOrActionId"]
            ),
            "marker": mirror["marker"],
            "heldItem": mirror["heldItem"],
            "tsvPos": mirror["pos"],
            "resolvedPos": resolved_pos,
            "resolvedPairPos": pair_pos,
            "positionSource": source_name,
            "severity": source.get("severity", "red"),
            "context": _mismatch_context(source),
        })
    actual = {(row["type"], str(row["rowOrActionId"])): row["marker"] for row in resolved}
    if actual != expected:
        missing = sorted(set(expected) - set(actual))
        extra = sorted(set(actual) - set(expected))
        raise IntegrityError("mismatch mirror does not equal red session set; missing=%s extra=%s" % (
            missing, extra
        ))
    return sorted(
        resolved,
        key=lambda row: (
            0 if isinstance(row["rowOrActionId"], int) else 1,
            str(row["rowOrActionId"]),
            row["type"],
        ),
    )


def _action_signature(row):
    action_id = row.get("actionId", "unknown")
    player = _require_string(row, "player", "action %s" % action_id)
    action_type = _require_string(row, "actionType", "action %s" % action_id)
    held = _require_string(row, "heldItem", "action %s" % action_id)
    owner = _canonical_int_pos(row.get("clickedOwnerPos"), "action %s clickedOwnerPos" % action_id)
    face = _require_string(row, "clickedFace", "action %s" % action_id).lower()
    hit = _canonical_hit_vec(row.get("clickedHitVec"), "action %s clickedHitVec" % action_id)
    placement = _canonical_int_pos(row.get("placementPos"), "action %s placementPos" % action_id)
    return (player, action_type, held, owner, face, hit, placement)


def _signature_dict(signature):
    if len(signature) == 8:
        return dict(zip(
            [
                "actionType",
                "heldItem",
                "clickedOwnerPos",
                "clickedFace",
                "placementPos",
                "rigCaseId",
                "playerId",
                "dimensionId",
            ],
            signature,
        ))
    return dict(zip(
        ["player", "actionType", "heldItem", "clickedOwnerPos", "clickedFace", "clickedHitVec", "placementPos"],
        signature,
    ))


def _component_nodes(clients, servers, edges):
    adjacency = defaultdict(set)
    for client_index, server_index in edges:
        cnode = ("c", client_index)
        snode = ("s", server_index)
        adjacency[cnode].add(snode)
        adjacency[snode].add(cnode)
    all_nodes = [("c", index) for index in range(len(clients))] + [
        ("s", index) for index in range(len(servers))
    ]
    components = []
    visited = set()
    for node in all_nodes:
        if node in visited:
            continue
        queue = deque([node])
        visited.add(node)
        component = set()
        while queue:
            current = queue.popleft()
            component.add(current)
            for neighbor in adjacency.get(current, ()):
                if neighbor not in visited:
                    visited.add(neighbor)
                    queue.append(neighbor)
        components.append(component)
    return components


def _one_perfect_matching(client_indexes, server_indexes, edge_set, forbidden_edge=None):
    clients = sorted(client_indexes)
    servers = set(server_indexes)
    if len(clients) != len(servers):
        return None
    server_to_client = {}

    def augment(client_index, seen_servers):
        choices = sorted(
            server_index for server_index in servers
            if (client_index, server_index) in edge_set
            and (client_index, server_index) != forbidden_edge
        )
        for server_index in choices:
            if server_index in seen_servers:
                continue
            seen_servers.add(server_index)
            previous_client = server_to_client.get(server_index)
            if previous_client is None or augment(previous_client, seen_servers):
                server_to_client[server_index] = client_index
                return True
        return False

    for client_index in clients:
        if not augment(client_index, set()):
            return None
    return sorted((client_index, server_index) for server_index, client_index in server_to_client.items())


def _perfect_matchings(client_indexes, server_indexes, edge_set, limit=2):
    """Return zero, one, or two witnesses without factorial matching enumeration."""
    first = _one_perfect_matching(client_indexes, server_indexes, edge_set)
    if first is None:
        return []
    if limit <= 1:
        return [first]
    for matched_edge in first:
        alternative = _one_perfect_matching(
            client_indexes, server_indexes, edge_set, forbidden_edge=matched_edge
        )
        if alternative is not None:
            return [first, alternative]
    return [first]


def _decimal_equal(left, right):
    return _finite_decimal(left, "paired client afterDy") == _finite_decimal(
        right, "paired server afterDy"
    )


def _optional_finite_decimal(value):
    if not isinstance(value, str):
        return None
    try:
        number = Decimal(value)
    except InvalidOperation:
        return None
    return number if number.is_finite() else None


def _adapter_action_markers(row, schema6=False):
    producer_tokens = set(_marker_tokens(row.get("marker", "none")))
    markers = []
    expected = _optional_finite_decimal(row.get("expectedAfterDy"))
    after = _optional_finite_decimal(row.get("afterDy"))
    if (
        expected is not None
        and after is not None
        and abs(expected - after) > Decimal("0.000001")
        and "LIVE_PLACEMENT_EXPECTED_DY_MISMATCH" not in producer_tokens
    ):
        markers.append("ADAPTER_EXPECTED_DY_MISMATCH")
    if (
        row.get("actualResult", "").startswith("Fail[")
        and not (
            schema6
            and row.get("finalVerdict") == "EXPECTED_REFUSAL"
        )
        and "LIVE_PLACEMENT_UNCLASSIFIED_FAILURE" not in producer_tokens
    ):
        markers.append("ADAPTER_UNCLASSIFIED_PLACEMENT_FAILURE")
    return markers


def _action_audit(actions, schema6=False):
    marker_counts = Counter()
    red_rows = []
    for row in actions:
        markers = _adapter_action_markers(row, schema6=schema6)
        marker_counts.update(markers)
        if not markers:
            continue
        placement = row.get("placementPos", "none")
        red_rows.append({
            "actionId": int(row["actionId"]),
            "actionOrigin": row.get("actionOrigin"),
            "side": row.get("side"),
            "heldItem": row.get("heldItem"),
            "placementPos": (
                "none"
                if placement == "none"
                else _canonical_int_pos(placement, "adapter action audit placementPos")
            ),
            "expectedAfterDy": row.get("expectedAfterDy"),
            "afterDy": row.get("afterDy"),
            "actualResult": row.get("actualResult"),
            "markers": markers,
        })
    return {
        "rowCount": len(actions),
        "redRows": sorted(red_rows, key=lambda row: row["actionId"]),
        "markerCounts": {
            marker: marker_counts.get(marker, 0) for marker in ACTION_ADAPTER_MARKERS
        },
    }


def _pair_record(run_id, client, server, signature, schema6=False):
    delta = server["_recordedAtParsed"] - client["_recordedAtParsed"]
    latency_micros = int(delta.total_seconds() * 1_000_000)
    client_tokens = _marker_tokens(client.get("marker", "none"))
    server_tokens = _marker_tokens(server.get("marker", "none"))
    red_markers = sorted(set(
        token for token in client_tokens + server_tokens if _is_red_marker(token)
    ))
    red_markers.extend(_adapter_action_markers(client, schema6=schema6))
    red_markers.extend(_adapter_action_markers(server, schema6=schema6))
    dy_match = (
        _java_same_evidence(client.get("afterDy", ""), server.get("afterDy", ""))
        if schema6
        else _decimal_equal(client.get("afterDy", ""), server.get("afterDy", ""))
    )
    if not dy_match:
        red_markers.append("ADAPTER_SIDE_DY_SPLIT")
    client_success = (
        _java_successful_result(client.get("actualResult", ""))
        if schema6
        else client.get("actualResult", "").startswith("Success[")
    )
    server_success = (
        _java_successful_result(server.get("actualResult", ""))
        if schema6
        else server.get("actualResult", "").startswith("Success[")
    )
    if client_success != server_success:
        red_markers.append("ADAPTER_SIDE_RESULT_SPLIT")
    elif (
        not client_success
        and not (
            schema6
            and client.get("finalVerdict") == "EXPECTED_REFUSAL"
            and server.get("finalVerdict") == "EXPECTED_REFUSAL"
        )
    ):
        red_markers.append("ADAPTER_PLACEMENT_FAILED")
    elif client.get("actualResult") != server.get("actualResult"):
        red_markers.append("ADAPTER_SIDE_RESULT_DETAIL_SPLIT")
    state_match = (
        _java_same_evidence(client.get("afterState"), server.get("afterState"))
        if schema6
        else client.get("afterState") == server.get("afterState")
    )
    if not state_match:
        red_markers.append("ADAPTER_SIDE_STATE_SPLIT")
    client_c3 = client.get("_c3")
    server_c3 = server.get("_c3")
    c3_markers = set()
    c3_field_differences = {}
    c3_family = None
    c3_capable = client_c3 is not None and server_c3 is not None
    if c3_capable:
        c3_markers.update(client_c3["markers"])
        c3_markers.update(server_c3["markers"])
        c3_field_differences = {
            field: {"client": client.get(field), "server": server.get(field)}
            for field in C3_PAIR_FIELDS
            if (
                (
                    _java_has_evidence(client.get(field))
                    and _java_has_evidence(server.get(field))
                    and not _java_same_evidence(
                        client.get(field),
                        server.get(field),
                    )
                )
                if schema6
                else client.get(field) != server.get(field)
            )
        }
        if c3_field_differences:
            c3_markers.add("ADAPTER_C3_SIDE_PAIR_FIELD_SPLIT")
        if client_c3["family"] == server_c3["family"]:
            c3_family = client_c3["family"]
        else:
            c3_markers.add("ADAPTER_C3_SIDE_PAIR_FIELD_SPLIT")
    elif client_c3 is not None or server_c3 is not None:
        c3_markers.update({
            "ADAPTER_C3_PAIR_FIELDS_MISSING",
            "ADAPTER_C3_SIDE_PAIR_FIELD_SPLIT",
        })
    red_markers.extend(c3_markers)
    red_markers = sorted(set(red_markers))
    explicit_green = (
        "LIVE_GREEN_PLACEMENT_AUTHORING" in client_tokens
        and "LIVE_GREEN_PLACEMENT_AUTHORING" in server_tokens
    )
    if red_markers:
        verdict = "RED"
    elif explicit_green:
        verdict = "EXPLICIT_GREEN"
    else:
        verdict = "OBSERVED_UNCLASSIFIED"
    diff_fields = [
        "placeBeforeState", "placeBeforeDy", "beforeState", "beforeDy", "beforeLaneKind",
        "beforeAttachments", "afterState", "afterDy", "afterLaneKind",
        "afterPersistentLoweredSlabCarrier", "actualResult", "marker",
    ]
    differences = {
        field: {"client": client.get(field), "server": server.get(field)}
        for field in diff_fields if client.get(field) != server.get(field)
    }
    return {
        "pairId": "%s:%s:%s" % (run_id, client["actionId"], server["actionId"]),
        "clientActionId": int(client["actionId"]),
        "serverActionId": int(server["actionId"]),
        "latencyMs": format(Decimal(latency_micros) / Decimal(1000), ".3f"),
        "signature": _signature_dict(signature),
        "clientAfterDy": client.get("afterDy"),
        "serverAfterDy": server.get("afterDy"),
        "dyMatch": dy_match,
        "clientLane": client.get("afterLaneKind"),
        "serverLane": server.get("afterLaneKind"),
        "clientMarker": client.get("marker", "none"),
        "serverMarker": server.get("marker", "none"),
        "clientResult": client.get("actualResult"),
        "serverResult": server.get("actualResult"),
        "redMarkers": red_markers,
        "verdict": verdict,
        "differences": differences,
        "c3PairFieldDifferences": c3_field_differences,
        "c3PairFields": {
            "capable": c3_capable,
            "family": c3_family,
            "qualifying": (
                c3_capable
                and c3_family in {"door", "bed"}
                and client_c3["pairComplete"]
                and server_c3["pairComplete"]
                and not c3_markers
            ),
            "client": None if client_c3 is None else {
                "pairClaim": client_c3["pairClaim"],
                "pairComplete": client_c3["pairComplete"],
                "family": client_c3["family"],
                "markers": client_c3["markers"],
                "details": client_c3["details"],
            },
            "server": None if server_c3 is None else {
                "pairClaim": server_c3["pairClaim"],
                "pairComplete": server_c3["pairComplete"],
                "family": server_c3["family"],
                "markers": server_c3["markers"],
                "details": server_c3["details"],
            },
        },
        "clientRow": _public_row(client),
        "serverRow": _public_row(server),
    }


def _public_row(row):
    return {key: value for key, value in row.items() if not key.startswith("_")}


def _pair_player_actions(actions, run_id):
    authored = [
        row for row in actions
        if row["actionOrigin"] == PLAYER_ORIGIN and row["actionType"] == "place_block"
    ]
    nonplacement = [
        row for row in actions
        if row["actionOrigin"] == PLAYER_ORIGIN and row["actionType"] != "place_block"
    ]
    by_signature = defaultdict(list)
    for row in authored:
        side = row.get("side")
        if side not in ("client", "server"):
            raise IntegrityError("player-authored action %s has invalid side %r" % (
                row.get("actionId"), side
            ))
        by_signature[_action_signature(row)].append(row)

    pairs = []
    unpaired = list(nonplacement)
    ambiguous = []
    for signature in sorted(by_signature):
        group = by_signature[signature]
        clients = [row for row in group if row["side"] == "client"]
        servers = [row for row in group if row["side"] == "server"]
        edge_set = set()
        for client_index, client in enumerate(clients):
            for server_index, server in enumerate(servers):
                delta = server["_recordedAtParsed"] - client["_recordedAtParsed"]
                micros = int(delta.total_seconds() * 1_000_000)
                if int(client["actionId"]) < int(server["actionId"]) and 0 <= micros < PAIR_WINDOW_MICROSECONDS:
                    edge_set.add((client_index, server_index))
        components = _component_nodes(clients, servers, edge_set)
        for component in components:
            client_indexes = {index for kind, index in component if kind == "c"}
            server_indexes = {index for kind, index in component if kind == "s"}
            component_rows = [clients[index] for index in client_indexes] + [
                servers[index] for index in server_indexes
            ]
            if not client_indexes or not server_indexes:
                unpaired.extend(component_rows)
                continue
            if len(client_indexes) != len(server_indexes):
                unpaired.extend(component_rows)
                continue
            matchings = _perfect_matchings(client_indexes, server_indexes, edge_set, limit=2)
            if len(matchings) != 1:
                ambiguous.extend(component_rows)
                continue
            for client_index, server_index in matchings[0]:
                pairs.append(_pair_record(
                    run_id, clients[client_index], servers[server_index], signature
                ))
    pairs.sort(key=lambda pair: (pair["clientActionId"], pair["serverActionId"]))
    unpaired = sorted({_row_id(row): row for row in unpaired}.values(), key=_row_id)
    ambiguous = sorted({_row_id(row): row for row in ambiguous}.values(), key=_row_id)
    return {
        "rowCount": len([row for row in actions if row["actionOrigin"] == PLAYER_ORIGIN]),
        "pairCount": len(pairs),
        "pairs": pairs,
        "unpairedRows": [dict(actionId=int(row["actionId"]), side=row["side"], reason="UNPAIRED") for row in unpaired],
        "ambiguousRows": [dict(actionId=int(row["actionId"]), side=row["side"], reason="AMBIGUOUS_REPEAT") for row in ambiguous],
    }


def _player_actions_from_logical_attempts(actions, terminals, run_id):
    actions_by_id = {row["actionId"]: row for row in actions}
    pairs = []
    unpaired = []
    for terminal in terminals:
        if terminal["playerProof"] != "PRESENT":
            continue
        if terminal["attemptStatus"] == "MERGED_CLIENT_SERVER":
            client = actions_by_id[terminal["clientActionId"]]
            server = actions_by_id[terminal["serverActionId"]]
            attempt_key = _schema6_attempt_key(client)
            if attempt_key != _schema6_attempt_key(server):
                raise IntegrityError(
                    "terminal-linked client/server rows disagree on Java PlacementAttemptKey for %s"
                    % terminal["logicalAttemptId"]
                )
            if (
                client["actionType"] == "place_block"
                and server["actionType"] == "place_block"
            ):
                pair = _pair_record(
                    run_id,
                    client,
                    server,
                    attempt_key,
                    schema6=True,
                )
                pair.update({
                    "logicalAttemptId": terminal["logicalAttemptId"],
                    "terminalRowId": terminal["rowId"],
                    "terminalFinalVerdict": terminal["finalVerdict"],
                    "terminalPlayerProof": terminal["playerProof"],
                    "terminalRawActionsCrossChecked": terminal[
                        "_rawActionsCrossChecked"
                    ],
                })
                if terminal["finalVerdict"] in {"RED", "UNCLASSIFIED_FAILURE"}:
                    pair["redMarkers"] = sorted(set(
                        pair["redMarkers"] + [terminal["verdictMarker"]]
                    ))
                pair["verdict"] = (
                    "RED"
                    if pair["redMarkers"]
                    else "EXPLICIT_GREEN"
                    if terminal["finalVerdict"] == "GREEN"
                    else "OBSERVED_UNCLASSIFIED"
                )
                pairs.append(pair)
                continue
        for field in ("clientActionId", "serverActionId"):
            action_id = terminal[field]
            if action_id == "none":
                continue
            row = actions_by_id[action_id]
            unpaired.append({
                "actionId": int(action_id),
                "side": row["side"],
                "reason": terminal["attemptStatus"],
                "logicalAttemptId": terminal["logicalAttemptId"],
            })
    pairs.sort(key=lambda pair: (pair["clientActionId"], pair["serverActionId"]))
    unpaired.sort(key=lambda row: row["actionId"])
    return {
        "rowCount": sum(
            row["actionOrigin"] == PLAYER_ORIGIN for row in actions
        ),
        "pairCount": len(pairs),
        "pairs": pairs,
        "unpairedRows": unpaired,
        "ambiguousRows": [],
    }


def _proxy_report(actions, schema6=False):
    proxy = [row for row in actions if row["actionOrigin"] == PROXY_ORIGIN]
    marker_counts = Counter()
    red_rows = []
    producer_red_row_count = 0
    adapter_red_row_count = 0
    for row in proxy:
        producer_tokens = _marker_tokens(row.get("marker", "none"))
        adapter_tokens = _adapter_action_markers(row, schema6=schema6)
        marker_counts.update(producer_tokens or ["none"])
        marker_counts.update(adapter_tokens)
        producer_red_tokens = [token for token in producer_tokens if _is_red_marker(token)]
        red_tokens = producer_red_tokens + adapter_tokens
        if red_tokens:
            producer_red_row_count += int(bool(producer_red_tokens))
            adapter_red_row_count += int(bool(adapter_tokens))
            red_rows.append({
                "actionId": int(row["actionId"]),
                "markers": sorted(red_tokens),
                "sourceMarker": row.get("marker", "none"),
                "heldItem": row.get("heldItem"),
                "placementPos": (
                    "none"
                    if row.get("placementPos") == "none"
                    else _canonical_int_pos(row.get("placementPos"), "proxy placementPos")
                ),
            })
    sample = [
        {
            "actionId": int(row["actionId"]),
            "side": row.get("side"),
            "actionType": row.get("actionType"),
            "heldItem": row.get("heldItem"),
            "marker": row.get("marker", "none"),
        }
        for row in proxy
        if not any(_is_red_marker(token) for token in _marker_tokens(row.get("marker", "none")))
        and not _adapter_action_markers(row, schema6=schema6)
    ][:12]
    return {
        "rowCount": len(proxy),
        "actionTypeCounts": dict(sorted(Counter(row["actionType"] for row in proxy).items())),
        "markerCounts": dict(sorted(marker_counts.items())),
        "redRows": sorted(red_rows, key=lambda row: row["actionId"]),
        "producerRedRowCount": producer_red_row_count,
        "adapterRedRowCount": adapter_red_row_count,
        "sample": sample,
        "omittedCleanSampleRows": max(0, len(proxy) - len(red_rows) - len(sample)),
    }


def _verdict(player, proxy, mismatches, action_audit):
    pair_red = any(pair["verdict"] == "RED" for pair in player["pairs"])
    pair_adapter_red = any(
        marker.startswith("ADAPTER_")
        for pair in player["pairs"] for marker in pair["redMarkers"]
    )
    pair_producer_red = any(
        not marker.startswith("ADAPTER_")
        for pair in player["pairs"] for marker in pair["redMarkers"]
    )
    player_action_red = any(
        row["actionOrigin"] == PLAYER_ORIGIN for row in action_audit["redRows"]
    )
    has_producer_red = bool(
        mismatches or proxy["producerRedRowCount"] or pair_producer_red
    )
    has_adapter_red = bool(pair_adapter_red or action_audit["redRows"])
    has_red = has_producer_red or has_adapter_red
    if pair_red or player_action_red:
        player_proof = "RED"
    elif player["unpairedRows"] or player["ambiguousRows"]:
        player_proof = "INCOMPLETE"
    elif player["pairCount"] and all(pair["verdict"] == "EXPLICIT_GREEN" for pair in player["pairs"]):
        player_proof = "EXPLICIT_GREEN"
    elif player["pairCount"]:
        player_proof = "OBSERVED_UNCLASSIFIED"
    else:
        player_proof = "ABSENT"
    reasons = []
    if mismatches:
        reasons.append("producer red mismatch rows present")
    if proxy["redRows"]:
        reasons.append("proxy red action rows present")
    if action_audit["redRows"]:
        reasons.append("adapter-derived action red rows present")
    if pair_red:
        reasons.append("player pair has producer or adapter-derived red")
    if has_red:
        status = "RED"
    elif player["unpairedRows"] or player["ambiguousRows"]:
        status = "PAIRING_INCOMPLETE"
        reasons.append("player-authored rows are unpaired or ambiguous")
    elif player["pairCount"] and all(pair["verdict"] == "EXPLICIT_GREEN" for pair in player["pairs"]):
        status = "EXPLICIT_GREEN"
    elif player["pairCount"]:
        status = "OBSERVED_UNCLASSIFIED"
        reasons.append("paired player actions have no explicit producer oracle")
    else:
        status = "NO_PLAYER_EVIDENCE"
        reasons.append("no uniquely paired PLAYER_AUTHORED placement actions")
    return {
        "status": status,
        "hasRed": has_red,
        "hasProducerRed": has_producer_red,
        "hasAdapterRed": has_adapter_red,
        "playerProof": player_proof,
        "reasons": reasons,
    }


def _verdict_schema6(
    player,
    proxy,
    mismatches,
    action_audit,
    actions,
    terminals,
):
    terminal_red = any(
        row["finalVerdict"] in {"RED", "UNCLASSIFIED_FAILURE"}
        for row in terminals
    )
    raw_canonical_red = any(
        row["finalVerdict"] in {"RED", "UNCLASSIFIED_FAILURE"}
        for row in actions
    )
    pair_red = any(pair["verdict"] == "RED" for pair in player["pairs"])
    pair_adapter_red = any(
        marker.startswith("ADAPTER_")
        for pair in player["pairs"] for marker in pair["redMarkers"]
    )
    pair_producer_red = any(
        not marker.startswith("ADAPTER_")
        for pair in player["pairs"] for marker in pair["redMarkers"]
    )
    has_producer_red = bool(
        terminal_red
        or raw_canonical_red
        or mismatches
        or proxy["producerRedRowCount"]
        or pair_producer_red
    )
    has_adapter_red = bool(
        proxy["adapterRedRowCount"]
        or action_audit["redRows"]
        or pair_adapter_red
    )
    has_red = has_producer_red or has_adapter_red

    player_terminals = [
        row for row in terminals if row["playerProof"] == "PRESENT"
    ]
    explicit_green_terminals = [
        row
        for row in player_terminals
        if (
            row["attemptStatus"] == "MERGED_CLIENT_SERVER"
            and row["finalVerdict"] == "GREEN"
            and row["_rawActionsCrossChecked"]
            and all(row[field] == "PASS" for field in COMPONENT_VERDICT_FIELDS)
        )
    ]
    incomplete_player = any(
        row["attemptStatus"] in {"SERVER_ONLY", "CLIENT_ONLY"}
        for row in player_terminals
    )
    player_red = any(
        row["finalVerdict"] in {"RED", "UNCLASSIFIED_FAILURE"}
        for row in player_terminals
    ) or any(
        row["actionOrigin"] == PLAYER_ORIGIN
        for row in action_audit["redRows"]
    ) or pair_red

    if player_red:
        player_proof = "RED"
    elif incomplete_player:
        player_proof = "INCOMPLETE"
    elif (
        player_terminals
        and len(explicit_green_terminals) == len(player_terminals)
    ):
        player_proof = "EXPLICIT_GREEN"
    elif player_terminals:
        player_proof = "OBSERVED_UNCLASSIFIED"
    else:
        player_proof = "ABSENT"

    reasons = []
    if terminal_red:
        reasons.append("schema-6 terminal attempt has a red final verdict")
    if raw_canonical_red:
        reasons.append("schema-6 raw action has a red final verdict")
    if mismatches:
        reasons.append("producer red mismatch rows present")
    if proxy["redRows"]:
        reasons.append("proxy red action rows present")
    if action_audit["redRows"]:
        reasons.append("adapter-derived action red rows present")
    if pair_red:
        reasons.append("player pair has producer or adapter-derived red")

    if has_red:
        status = "RED"
    elif incomplete_player:
        status = "PAIRING_INCOMPLETE"
        reasons.append("player logical attempt is client-only or server-only")
    elif player_proof == "EXPLICIT_GREEN":
        status = "EXPLICIT_GREEN"
    elif player_terminals:
        status = "OBSERVED_UNCLASSIFIED"
        reasons.append(
            "player terminal lacks a merged all-PASS GREEN evidence surface"
        )
    else:
        status = "NO_PLAYER_EVIDENCE"
        reasons.append(
            "no schema-6 terminal carries PRESENT player proof"
        )
    return {
        "status": status,
        "hasRed": has_red,
        "hasProducerRed": has_producer_red,
        "hasAdapterRed": has_adapter_red,
        "playerProof": player_proof,
        "reasons": reasons,
    }


def analyze(input_path, run_id=None):
    discovery = discover(input_path, run_id=run_id)
    recorder_dir = Path(discovery["recorderDir"])
    discovered_manifest = discovery.pop("manifest")

    missing = [name for name in BOOTSTRAP_ARTIFACT_NAMES if not (recorder_dir / name).is_file()]
    if missing:
        raise IntegrityError("missing recorder artifacts: %s" % ", ".join(missing))
    session_path = recorder_dir / "session.jsonl"
    if session_path.exists() and not session_path.is_file():
        raise IntegrityError("session.jsonl exists but is not a file")
    session_present = session_path.is_file()
    present_artifacts = [
        name for name in ARTIFACT_NAMES if (recorder_dir / name).is_file()
    ]
    artifacts = {
        name: _artifact_info(recorder_dir / name) for name in sorted(present_artifacts)
    }
    manifest = _load_json(recorder_dir / "manifest.json")
    if manifest != discovered_manifest:
        raise IntegrityError("manifest.json changed during discovery/analysis")
    _validate_manifest(manifest)
    schema = manifest["schemaVersion"]
    summary, legacy_missing_summary_keys = _parse_summary(
        recorder_dir / "summary.md",
        schema,
        manifest.get("recorderVersion"),
    )
    if session_present:
        session_rows = _parse_session(session_path, schema)
        for row in session_rows:
            _validate_session_row_contract(row)
        session_state = "present"
    else:
        nonzero = sorted(
            key for key, value in summary.items()
            if key not in ("sentinelArmedTotal", "sentinelSamplePasses") and value != 0
        )
        if nonzero:
            raise IntegrityError(
                "session.jsonl missing but summary has activity: %s" % ", ".join(nonzero)
            )
        session_rows = []
        session_state = "absent_valid_zero_row_bootstrap"
    action_tsv, action_header = _parse_actions_tsv(
        recorder_dir / "actions.tsv",
        schema,
    )
    mismatch_header = (
        SCHEMA6_MISMATCH_HEADER
        if schema == SCHEMA6_VERSION
        else MISMATCH_HEADER
    )
    mismatch_tsv = _parse_tsv(
        recorder_dir / "mismatches.tsv",
        mismatch_header,
    )
    outline_tsv = _parse_tsv(recorder_dir / "rendered-outlines.tsv", OUTLINE_HEADER)

    actions = _validate_actions(
        session_rows,
        action_tsv,
        summary,
        action_header,
        schema,
        manifest["runId"],
    )
    logical_attempts = (
        _validate_logical_attempts(session_rows, actions, manifest)
        if schema == SCHEMA6_VERSION
        else []
    )
    derived = _derived_summary(session_rows, schema)
    summary_keys = (
        SCHEMA6_SUMMARY_KEYS
        if schema == SCHEMA6_VERSION
        else SUMMARY_KEYS
    )
    for key in summary_keys:
        if key in ("sentinelArmedTotal", "sentinelSamplePasses"):
            continue
        if summary[key] != derived[key]:
            raise IntegrityError("summary/session counter mismatch %s: %d != %d" % (
                key, summary[key], derived[key]
            ))
    _validate_outlines(session_rows, outline_tsv)
    mismatch_rows = _validate_mismatches(
        session_rows,
        mismatch_tsv,
        schema,
    )

    if session_path.is_file() != session_present:
        raise IntegrityError("recorder artifacts changed during analysis: session.jsonl presence")
    artifacts_after = {
        name: _artifact_info(recorder_dir / name) for name in sorted(present_artifacts)
    }
    if artifacts_after != artifacts:
        raise IntegrityError("recorder artifacts changed during analysis")

    player = (
        _player_actions_from_logical_attempts(
            actions,
            logical_attempts,
            manifest["runId"],
        )
        if schema == SCHEMA6_VERSION
        else _pair_player_actions(actions, manifest["runId"])
    )
    schema6 = schema == SCHEMA6_VERSION
    action_audit = _action_audit(actions, schema6=schema6)
    proxy = _proxy_report(actions, schema6=schema6)
    verdict = (
        _verdict_schema6(
            player,
            proxy,
            mismatch_rows,
            action_audit,
            actions,
            logical_attempts,
        )
        if schema == SCHEMA6_VERSION
        else _verdict(player, proxy, mismatch_rows, action_audit)
    )
    adapter_counters = {
        marker: sum(
            marker in pair["redMarkers"] for pair in player["pairs"]
        )
        for marker in C3_ADAPTER_MARKERS
    }
    adapter_counters.update(action_audit["markerCounts"])
    qualifying_families = Counter(
        pair["c3PairFields"]["family"]
        for pair in player["pairs"] if pair["c3PairFields"]["qualifying"]
    )
    c3_capable = (
        action_header in (C3_ACTIONS_HEADER, SCHEMA6_ACTIONS_HEADER)
        and manifest.get("recorderVersion") in C3_CAPABLE_RECORDER_VERSIONS
    )
    return {
        "triageSchemaVersion": TRIAGE_SCHEMA_VERSION,
        "verdict": verdict,
        "source": discovery,
        "run": {
            key: manifest.get(key, "") for key in [
                "schemaVersion", "runId", "recorderVersion", "createdAt", "gitSha",
                "buildTime", "jarFile", "gameDir",
            ]
        },
        "artifacts": artifacts,
        "counters": {
            "producer": summary,
            "derived": derived,
            "adapter": adapter_counters,
            "compatibility": {
                "legacyMissingProducerCounters": legacy_missing_summary_keys,
            },
        },
        "liveness": {
            "sessionJsonl": session_state,
            "sentinelArmedTotal": summary["sentinelArmedTotal"],
            "sentinelSamplePasses": summary["sentinelSamplePasses"],
            "sentinelJudgmentObserved": (
                summary["sentinelArmedTotal"] > 0 and summary["sentinelSamplePasses"] > 0
            ),
            "runCompletion": "unknown_no_session_end_event",
        },
        "playerAuthored": player,
        "logicalAttempts": {
            "capable": schema == SCHEMA6_VERSION,
            "rowCount": len(logical_attempts),
            "playerProofRowCount": sum(
                row["playerProof"] == "PRESENT"
                for row in logical_attempts
            ),
            "rows": [
                dict(
                    _public_row(row),
                    rawActionsCrossChecked=row.get(
                        "_rawActionsCrossChecked",
                        False,
                    ),
                )
                for row in logical_attempts
            ],
        },
        "c3PairFields": {
            "capable": c3_capable,
            "actionHeader": (
                "schema6"
                if action_header == SCHEMA6_ACTIONS_HEADER
                else "extended"
                if action_header == C3_ACTIONS_HEADER
                else "legacy"
            ),
            "recorderVersion": manifest.get("recorderVersion"),
            "qualifyingFamilies": {
                "bed": qualifying_families.get("bed", 0),
                "door": qualifying_families.get("door", 0),
            },
            "missingRequiredFamilies": [
                family for family in ("door", "bed")
                if qualifying_families.get(family, 0) == 0
            ],
        },
        "autoUseOnProxy": proxy,
        "adapterActionAudit": action_audit,
        "mismatches": mismatch_rows,
        "breaks": {
            "rowCount": summary["breakRows"],
            "rows": [_public_row(row) for row in session_rows if row["type"] == "break"],
        },
        "coverage": {
            "status": "unknown",
            "reason": "no validated category/topology/hanging case manifest",
        },
        "evidenceBoundary": (
            (
                "Schema-6 terminal placement attempts are authoritative for player proof and final "
                "verdicts; raw client/server rows are detailed only after exact terminal-reference "
                "cross-checking. AUTO_PROXY with playerProof=ABSENT never creates player proof."
                if schema == SCHEMA6_VERSION
                else
                "Only uniquely paired schema-3 PLAYER_AUTHORED placement rows are player evidence; "
                "AUTO_USEON_PROXY is diagnostic only. Adapter action reds require a finite numeric "
                "expectedAfterDy disagreement or an untyped Fail[] result; unknown expectations stay "
                "unclassified, and no category completeness is inferred."
            )
        ),
    }


def render_json(triage):
    return json.dumps(triage, indent=2, sort_keys=True) + "\n"


def _markdown_cell(value):
    return str(value).replace("|", "/").replace("\n", " ")


def render_markdown(triage):
    lines = [
        "# Slabbed Recorder Triage",
        "",
        "## Verdict",
        "",
        "- status: `%s`" % triage["verdict"]["status"],
        "- playerProof: `%s`" % triage["verdict"]["playerProof"],
        "- hasRed: `%s`" % str(triage["verdict"]["hasRed"]).lower(),
        "- hasProducerRed: `%s`" % str(triage["verdict"]["hasProducerRed"]).lower(),
        "- hasAdapterRed: `%s`" % str(triage["verdict"]["hasAdapterRed"]).lower(),
    ]
    for reason in triage["verdict"]["reasons"]:
        lines.append("- reason: %s" % reason)
    lines.extend([
        "",
        "## Source",
        "",
        "- requested: `%s`" % triage["source"]["requestedInput"],
        "- recorder: `%s`" % triage["source"]["recorderDir"],
        "- discovery: `%s`" % triage["source"]["discoveryMode"],
        "",
        "## Run Identity",
        "",
        "- schema/run: `%s` / `%s`" % (triage["run"]["schemaVersion"], triage["run"]["runId"]),
        "- build: `%s`" % triage["run"]["gitSha"],
        "- jar: `%s`" % triage["run"]["jarFile"],
        "- session.jsonl: `%s`" % triage["liveness"]["sessionJsonl"],
        "- run completion: `%s`" % triage["liveness"]["runCompletion"],
        "",
        "## Artifact Integrity",
        "",
        "| artifact | bytes | sha256 |",
        "|---|---:|---|",
    ])
    for name, info in sorted(triage["artifacts"].items()):
        lines.append("| %s | %s | `%s` |" % (name, info["bytes"], info["sha256"]))
    lines.extend([
        "",
        "## Counter Reconciliation",
        "",
        "- action rows: `%d` (player `%d`, proxy `%d`)" % (
            triage["counters"]["producer"]["actionRows"],
            triage["counters"]["producer"]["playerAuthoredActionRows"],
            triage["counters"]["producer"]["autoUseOnProxyActionRows"],
        ),
        "- mismatches: `%d`" % len(triage["mismatches"]),
        "- producer expected-dy / unclassified-failure rows: `%d` / `%d`" % (
            triage["counters"]["producer"]["placementExpectedDyMismatchRows"],
            triage["counters"]["producer"]["placementUnclassifiedFailureRows"],
        ),
        "- adapter expected-dy / unclassified-failure rows: `%d` / `%d`" % (
            triage["counters"]["adapter"]["ADAPTER_EXPECTED_DY_MISMATCH"],
            triage["counters"]["adapter"]["ADAPTER_UNCLASSIFIED_PLACEMENT_FAILURE"],
        ),
        "- legacy missing producer counters: `%s`" % json.dumps(
            triage["counters"]["compatibility"]["legacyMissingProducerCounters"]
        ),
        "- sentinel armed/samples: `%d` / `%d`" % (
            triage["liveness"]["sentinelArmedTotal"],
            triage["liveness"]["sentinelSamplePasses"],
        ),
        "",
        "## C3 Pair Fields",
        "",
        "- capable: `%s`" % str(triage["c3PairFields"]["capable"]).lower(),
        "- actions header: `%s`" % triage["c3PairFields"]["actionHeader"],
        "- qualifying families: `%s`" % json.dumps(
            triage["c3PairFields"]["qualifyingFamilies"], sort_keys=True
        ),
        "- missing required families: `%s`" % json.dumps(
            triage["c3PairFields"]["missingRequiredFamilies"]
        ),
        "- adapter counters: `%s`" % json.dumps(
            triage["counters"]["adapter"], sort_keys=True
        ),
        "",
        "## Player-Authored Placement Pairs",
        "",
    ])
    if triage["playerAuthored"]["pairs"]:
        lines.extend([
            "| client | server | item | placement | latency ms | dy | C3 family | markers | verdict |",
            "|---:|---:|---|---|---:|---|---|---|---|",
        ])
        for pair in triage["playerAuthored"]["pairs"][:50]:
            lines.append("| %d | %d | %s | %s | %s | %s/%s | %s | %s | %s |" % (
                pair["clientActionId"], pair["serverActionId"],
                _markdown_cell(pair["signature"]["heldItem"]),
                _markdown_cell(pair["signature"]["placementPos"]), pair["latencyMs"],
                pair["clientAfterDy"], pair["serverAfterDy"],
                pair["c3PairFields"]["family"] or "none",
                _markdown_cell(",".join(pair["redMarkers"]) or "none"),
                pair["verdict"],
            ))
        if len(triage["playerAuthored"]["pairs"]) > 50:
            lines.append("\nomitted %d pairs; full rows are in triage.json" % (
                len(triage["playerAuthored"]["pairs"]) - 50
            ))
    else:
        lines.append("none — no uniquely paired PLAYER_AUTHORED placement evidence")
    lines.extend([
        "",
        "## Unpaired or Ambiguous Player Rows",
        "",
        "- unpaired: `%s`" % json.dumps(triage["playerAuthored"]["unpairedRows"], sort_keys=True),
        "- ambiguous: `%s`" % json.dumps(triage["playerAuthored"]["ambiguousRows"], sort_keys=True),
        "",
        "## AUTO_USEON_PROXY Actions",
        "",
        "- rows: `%d`" % triage["autoUseOnProxy"]["rowCount"],
        "- red rows: `%d`" % len(triage["autoUseOnProxy"]["redRows"]),
        "- marker counts: `%s`" % json.dumps(triage["autoUseOnProxy"]["markerCounts"], sort_keys=True),
    ])
    if triage["autoUseOnProxy"]["omittedCleanSampleRows"]:
        lines.append("- omitted clean sample rows: `%d`; full counts are in triage.json" % (
            triage["autoUseOnProxy"]["omittedCleanSampleRows"]
        ))
    lines.extend([
        "",
        "## Adapter Action Audit",
        "",
        "- rows inspected: `%d`" % triage["adapterActionAudit"]["rowCount"],
        "- adapter red rows: `%d`" % len(triage["adapterActionAudit"]["redRows"]),
        "- marker counts: `%s`" % json.dumps(
            triage["adapterActionAudit"]["markerCounts"], sort_keys=True
        ),
        "",
        "## Red Mismatches",
        "",
    ])
    if triage["mismatches"]:
        lines.extend([
            "| id | type | marker | resolved pos | pair pos | context |",
            "|---:|---|---|---|---|---|",
        ])
        for row in triage["mismatches"][:50]:
            lines.append("| %s | %s | %s | %s | %s | %s |" % (
                row["rowOrActionId"], row["type"], row["marker"], row["resolvedPos"],
                row["resolvedPairPos"] or "none",
                _markdown_cell(json.dumps(row["context"], sort_keys=True)),
            ))
        if len(triage["mismatches"]) > 50:
            lines.append("\nomitted %d mismatches; full rows are in triage.json" % (
                len(triage["mismatches"]) - 50
            ))
    else:
        lines.append("none")
    lines.extend([
        "",
        "## Evidence Boundary",
        "",
        "- %s" % triage["evidenceBoundary"],
        "- coverage: `%s` — %s" % (
            triage["coverage"]["status"], triage["coverage"]["reason"]
        ),
        "",
    ])
    return "\n".join(lines)


def exit_code_for(triage, require_player_pairs=False, require_c3_pair_fields=False):
    if triage["verdict"]["status"] == "RED":
        return 1
    if (require_player_pairs or require_c3_pair_fields) and (
        triage["playerAuthored"]["pairCount"] == 0
        or triage["playerAuthored"]["unpairedRows"]
        or triage["playerAuthored"]["ambiguousRows"]
    ):
        return 7
    if require_c3_pair_fields and (
        not triage["c3PairFields"]["capable"]
        or triage["c3PairFields"]["missingRequiredFamilies"]
    ):
        return 7
    return 0


def _atomic_write(path, text):
    path = Path(path).expanduser()
    parent = path.parent.resolve()
    try:
        parent.mkdir(parents=True, exist_ok=True)
        fd, temp_name = tempfile.mkstemp(prefix=".%s." % path.name, dir=str(parent))
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as handle:
                handle.write(text)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temp_name, str(path))
        except Exception:
            try:
                os.unlink(temp_name)
            except OSError:
                pass
            raise
    except OSError as exc:
        raise OutputError("cannot write output %s: %s" % (path, exc))


def _reject_evidence_output_target(triage, output_path):
    try:
        target = Path(output_path).expanduser().resolve()
        recorder_dir = Path(triage["source"]["recorderDir"])
        protected = {(recorder_dir / name).resolve() for name in ARTIFACT_NAMES}
    except OSError as exc:
        raise OutputError("cannot resolve output target %s: %s" % (output_path, exc))
    if target in protected or target.name in ARTIFACT_NAMES:
        raise OutputError("refusing to overwrite recorder evidence artifact: %s" % target)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", help="recorder directory, evidence root, or recorder parent")
    parser.add_argument("--run-id", help="select one supported recorder run id")
    parser.add_argument("--require-player-pairs", action="store_true")
    parser.add_argument(
        "--require-c3-pair-fields",
        action="store_true",
        help="require complete door and bed C3 pair fields; implies --require-player-pairs",
    )
    parser.add_argument("--format", choices=("json", "markdown"), default="json")
    parser.add_argument("--output", help="atomically write report instead of stdout")
    try:
        args = parser.parse_args(argv)
        triage = analyze(args.input, run_id=args.run_id)
        text = render_json(triage) if args.format == "json" else render_markdown(triage)
        if args.output:
            _reject_evidence_output_target(triage, args.output)
            _atomic_write(args.output, text)
        else:
            sys.stdout.write(text)
        return exit_code_for(
            triage,
            require_player_pairs=args.require_player_pairs,
            require_c3_pair_fields=args.require_c3_pair_fields,
        )
    except AdapterError as exc:
        sys.stderr.write("slabbed-recorder-adapter: %s\n" % exc)
        return exc.exit_code


if __name__ == "__main__":
    raise SystemExit(main())
