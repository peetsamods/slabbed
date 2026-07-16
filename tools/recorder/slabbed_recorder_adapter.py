#!/usr/bin/env python3
"""Strict, deterministic adapter for Slabbed 26.2 recorder schema 3."""

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
C3_CAPABLE_RECORDER_VERSIONS = {
    C3_RECORDER_VERSION,
    C4_RECORDER_VERSION,
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
        if schema == SCHEMA_VERSION:
            candidates.append(_candidate(root, mode, requested, evidence_dir))
        try:
            children = sorted(
                child for child in root.iterdir()
                if child.is_dir() and child.name.startswith("schema-3-")
            )
        except OSError as exc:
            raise DiscoveryError("cannot inspect %s: %s" % (root, exc))
        for child in children:
            if _manifest_schema(child) == SCHEMA_VERSION:
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
        if path.name.startswith("schema-3-"):
            basename_id = path.name[len("schema-3-"):]
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
        raise DiscoveryError("multiple schema-3 recorder sessions; use --run-id: %s" % details)

    if run_id is not None:
        raise DiscoveryError("no schema-3 recorder session matched --run-id %s" % run_id)
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
    raise DiscoveryError("no schema-3 recorder session found beneath %s" % requested)


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
    if schema != SCHEMA_VERSION:
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


def _parse_summary(path, recorder_version):
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise IntegrityError("cannot read summary.md: %s" % exc)
    counters = {}
    for line in lines:
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise IntegrityError("invalid summary.md line: %s" % line)
        key, value = line.split("=", 1)
        if key in counters:
            raise IntegrityError("duplicate summary counter: %s" % key)
        if key not in SUMMARY_KEYS:
            raise IntegrityError("unknown summary counter: %s" % key)
        if not re.fullmatch(r"[0-9]+", value):
            raise IntegrityError("summary counter must be nonnegative integer: %s=%s" % (key, value))
        counters[key] = int(value)
    missing = [key for key in SUMMARY_KEYS if key not in counters]
    allowed_legacy_missing = (
        {"placementUnclassifiedFailureRows"}
        if isinstance(recorder_version, str)
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


def _parse_actions_tsv(path):
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


def _parse_session(path):
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise IntegrityError("cannot read session.jsonl: %s" % exc)
    rows = []
    previous_time = None
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
        row_id = _row_id(row)
        if row_id != line_number:
            raise IntegrityError(
                "session global ids must be contiguous in file order: line %d has id %d"
                % (line_number, row_id)
            )
        recorded = _parse_instant(row.get("recordedAt"), "session row %d" % row_id)
        if previous_time is not None and recorded < previous_time:
            raise IntegrityError("session recordedAt decreases at global id %d" % row_id)
        previous_time = recorded
        row["_globalId"] = row_id
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


def _validate_c3_action_fields(row):
    action_id = row.get("actionId", "unknown")
    label = "action %s C3 primary store" % action_id
    primary_fact = _validate_store_fact(
        row, "afterStoredDy", "afterStoredDyBits", label
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
            primary_fact["bits"] == pair_fact["bits"]
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
    if row_type in ("action", "cursor", "rendered_outline"):
        return any(token in RED_MARKERS_BY_TYPE[row_type] for token in _marker_tokens(marker))
    return False


def _derived_summary(rows):
    counters = {key: 0 for key in SUMMARY_KEYS}
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


def _validate_actions(session_rows, action_tsv, summary, action_header):
    session_actions = [row for row in session_rows if row["type"] == "action"]
    if len(session_actions) != len(action_tsv):
        raise IntegrityError("actions.tsv/session action count mismatch")
    extended = action_header == C3_ACTIONS_HEADER
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
        for key in [
            "side", "player", "actionType", "heldItem", "clickedOwnerPos", "clickedFace",
            "clickedHitVec", "recordedAt", "actualResult",
        ]:
            _require_string(row, key, "action %s" % row.get("actionId"), allow_none=False)
        action_id = row.get("actionId")
        if row.get("side") not in ("client", "server"):
            raise IntegrityError("action %s has invalid side %r" % (action_id, row.get("side")))
        _canonical_int_pos(row.get("clickedOwnerPos"), "action %s clickedOwnerPos" % action_id)
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
            _require_string(row, "afterDy", "action %s" % action_id, allow_none=False)
            _require_string(row, "afterLaneKind", "action %s" % action_id, allow_none=True)
            if row.get("actionType") == "place_block":
                _require_string(row, "afterState", "action %s" % action_id, allow_none=False)
                _finite_decimal(row.get("afterDy"), "action %s afterDy" % action_id)
            _validate_explicit_green_action(row)
        if extended:
            row["_c3"] = _validate_c3_action_fields(row)
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
        return {
            "clickedOwnerPos": _canonical_int_pos(
                row.get("clickedOwnerPos"), "action %s clickedOwnerPos" % action_id
            ),
            "clickedHitVec": _canonical_hit_vec(
                row.get("clickedHitVec"), "action %s clickedHitVec" % action_id
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


def _validate_mismatches(session_rows, mismatch_rows):
    index = {}
    expected = {}
    for row in session_rows:
        row_type = row["type"]
        if row_type not in MARKER_FIELD_BY_TYPE:
            continue
        key = (row_type, str(row["_globalId"]))
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
            "rowOrActionId": int(mirror["rowOrActionId"]),
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
    return sorted(resolved, key=lambda row: (row["rowOrActionId"], row["type"]))


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


def _adapter_action_markers(row):
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
        and "LIVE_PLACEMENT_UNCLASSIFIED_FAILURE" not in producer_tokens
    ):
        markers.append("ADAPTER_UNCLASSIFIED_PLACEMENT_FAILURE")
    return markers


def _action_audit(actions):
    marker_counts = Counter()
    red_rows = []
    for row in actions:
        markers = _adapter_action_markers(row)
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


def _pair_record(run_id, client, server, signature):
    delta = server["_recordedAtParsed"] - client["_recordedAtParsed"]
    latency_micros = int(delta.total_seconds() * 1_000_000)
    client_tokens = _marker_tokens(client.get("marker", "none"))
    server_tokens = _marker_tokens(server.get("marker", "none"))
    red_markers = sorted(set(
        token for token in client_tokens + server_tokens if _is_red_marker(token)
    ))
    red_markers.extend(_adapter_action_markers(client))
    red_markers.extend(_adapter_action_markers(server))
    dy_match = _decimal_equal(client.get("afterDy", ""), server.get("afterDy", ""))
    if not dy_match:
        red_markers.append("ADAPTER_SIDE_DY_SPLIT")
    client_success = client.get("actualResult", "").startswith("Success[")
    server_success = server.get("actualResult", "").startswith("Success[")
    if client_success != server_success:
        red_markers.append("ADAPTER_SIDE_RESULT_SPLIT")
    elif not client_success:
        red_markers.append("ADAPTER_PLACEMENT_FAILED")
    elif client.get("actualResult") != server.get("actualResult"):
        red_markers.append("ADAPTER_SIDE_RESULT_DETAIL_SPLIT")
    if client.get("afterState") != server.get("afterState"):
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
            for field in C3_PAIR_FIELDS if client.get(field) != server.get(field)
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


def _proxy_report(actions):
    proxy = [row for row in actions if row["actionOrigin"] == PROXY_ORIGIN]
    marker_counts = Counter()
    red_rows = []
    producer_red_row_count = 0
    adapter_red_row_count = 0
    for row in proxy:
        producer_tokens = _marker_tokens(row.get("marker", "none"))
        adapter_tokens = _adapter_action_markers(row)
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
        and not _adapter_action_markers(row)
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
    summary, legacy_missing_summary_keys = _parse_summary(
        recorder_dir / "summary.md",
        manifest.get("recorderVersion"),
    )
    if session_present:
        session_rows = _parse_session(session_path)
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
    action_tsv, action_header = _parse_actions_tsv(recorder_dir / "actions.tsv")
    mismatch_tsv = _parse_tsv(recorder_dir / "mismatches.tsv", MISMATCH_HEADER)
    outline_tsv = _parse_tsv(recorder_dir / "rendered-outlines.tsv", OUTLINE_HEADER)

    actions = _validate_actions(session_rows, action_tsv, summary, action_header)
    derived = _derived_summary(session_rows)
    for key in SUMMARY_KEYS:
        if key in ("sentinelArmedTotal", "sentinelSamplePasses"):
            continue
        if summary[key] != derived[key]:
            raise IntegrityError("summary/session counter mismatch %s: %d != %d" % (
                key, summary[key], derived[key]
            ))
    _validate_outlines(session_rows, outline_tsv)
    mismatch_rows = _validate_mismatches(session_rows, mismatch_tsv)

    if session_path.is_file() != session_present:
        raise IntegrityError("recorder artifacts changed during analysis: session.jsonl presence")
    artifacts_after = {
        name: _artifact_info(recorder_dir / name) for name in sorted(present_artifacts)
    }
    if artifacts_after != artifacts:
        raise IntegrityError("recorder artifacts changed during analysis")

    player = _pair_player_actions(actions, manifest["runId"])
    action_audit = _action_audit(actions)
    proxy = _proxy_report(actions)
    verdict = _verdict(player, proxy, mismatch_rows, action_audit)
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
        action_header == C3_ACTIONS_HEADER
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
        "c3PairFields": {
            "capable": c3_capable,
            "actionHeader": "extended" if action_header == C3_ACTIONS_HEADER else "legacy",
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
            "Only uniquely paired schema-3 PLAYER_AUTHORED placement rows are player evidence; "
            "AUTO_USEON_PROXY is diagnostic only. Adapter action reds require a finite numeric "
            "expectedAfterDy disagreement or an untyped Fail[] result; unknown expectations stay "
            "unclassified, and no category completeness is inferred."
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
            lines.append("| %d | %s | %s | %s | %s | %s |" % (
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
    parser.add_argument("--run-id", help="select one schema-3 run id")
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
