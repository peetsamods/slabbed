#!/usr/bin/env python3
import json
import sys
from pathlib import Path


CANONICAL_PROOF_IDS = [
    "fb_on_bs_lower_half_owner_targeting",
    "fb_on_bs_lower_half_side_slab_intent",
    "fb_on_bs_repeat_click_no_ghost_face",
    "torch_on_fb_on_bs_rescue_targeting",
    "bed_on_bs_rescue_targeting",
    "full_block_on_full_block_baseline",
    "slab_on_normal_vanilla_face_baseline",
    "chain_on_fb_on_bs_no_rescue_targeting",
    "crafting_table_on_bs_no_rescue_targeting",
]


def fail(message: str) -> int:
    print(f"FAIL: {message}")
    return 1


def load_json(path: Path, label: str):
    if not path.is_file():
        raise FileNotFoundError(f"{label} missing: {path}")
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def get_string(data, key: str, label: str) -> str:
    value = data.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{label} missing {key}")
    return value.strip()


def get_int(data, key: str, label: str) -> int:
    value = data.get(key)
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError(f"{label} missing {key}")
    return value


def require_same(field: str, *values: str) -> None:
    normalized = [value.strip() for value in values]
    if len(set(normalized)) != 1:
        raise ValueError(f"{field} mismatch")


def require_files_exist(root: Path, rows, label: str) -> None:
    for row in rows:
        notes_file = row.get("notesFile")
        screenshot_file = row.get("primaryScreenshotFile")
        if not isinstance(notes_file, str) or not notes_file.strip():
            raise ValueError(f"{label} missing notesFile")
        if not isinstance(screenshot_file, str) or not screenshot_file.strip():
            raise ValueError(f"{label} missing primaryScreenshotFile")
        if not (root / notes_file).is_file():
            raise ValueError(f"missing notes file: {notes_file}")
        if not (root / screenshot_file).is_file():
            raise ValueError(f"missing screenshot file: {screenshot_file}")


def extract_receipt_rows(receipt_text: str):
    rows = []
    for line in receipt_text.splitlines():
        if not line.startswith("| "):
            continue
        if line.startswith("| proofId |"):
            continue
        if line.startswith("| --- |"):
            continue
        parts = [part.strip() for part in line.strip().strip("|").split("|")]
        if len(parts) == 5:
            rows.append(parts)
    return rows


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("build/run/clientGameTest/screenshots")
    root = root.expanduser()
    if not root.is_absolute():
        root = (Path.cwd() / root).resolve()

    manifest_path = root / "run_manifest.json"
    summary_path = root / "proof_summary.json"
    index_path = root / "proof_index.json"
    latest_path = root / "latest_proof_run.json"
    receipt_path = root / "proof_receipt.md"

    try:
        manifest = load_json(manifest_path, "run_manifest.json")
        summary = load_json(summary_path, "proof_summary.json")
        index = load_json(index_path, "proof_index.json")
        latest = load_json(latest_path, "latest_proof_run.json")
        receipt_text = receipt_path.read_text(encoding="utf-8")
    except Exception as exc:
        return fail(str(exc))

    for path, label in [
        (manifest_path, "run_manifest.json"),
        (summary_path, "proof_summary.json"),
        (index_path, "proof_index.json"),
        (latest_path, "latest_proof_run.json"),
        (receipt_path, "proof_receipt.md"),
    ]:
        if not path.is_file():
            return fail(f"{label} missing")

    try:
        manifest_head = get_string(manifest, "gitHeadShort", "run_manifest.json")
        summary_head = get_string(summary, "gitHeadShort", "proof_summary.json")
        index_head = get_string(index, "gitHeadShort", "proof_index.json")
        latest_head = get_string(latest, "gitHeadShort", "latest_proof_run.json")
        require_same("gitHeadShort", manifest_head, summary_head, index_head, latest_head)

        manifest_branch = get_string(manifest, "gitBranch", "run_manifest.json")
        summary_branch = get_string(summary, "gitBranch", "proof_summary.json")
        index_branch = get_string(index, "gitBranch", "proof_index.json")
        latest_branch = get_string(latest, "gitBranch", "latest_proof_run.json")
        require_same("gitBranch", manifest_branch, summary_branch, index_branch, latest_branch)

        summary_expected = get_int(summary, "expectedProofCount", "proof_summary.json")
        index_count = get_int(index, "proofCount", "proof_index.json")
        latest_expected = get_int(latest, "expectedProofCount", "latest_proof_run.json")
        receipt_expected = None
        for line in receipt_text.splitlines():
            if line.startswith("- expectedProofCount:"):
                receipt_expected = int(line.split(":", 1)[1].strip())
                break
        if receipt_expected is None:
            return fail("proof_receipt.md missing expectedProofCount")
        if summary_expected != 9:
            return fail(f"proof_summary.json expectedProofCount != 9 ({summary_expected})")
        if index_count != 9:
            return fail(f"proof_index.json proofCount != 9 ({index_count})")
        if latest_expected != 9:
            return fail(f"latest_proof_run.json expectedProofCount != 9 ({latest_expected})")
        if receipt_expected != 9:
            return fail(f"proof_receipt.md expectedProofCount != 9 ({receipt_expected})")

        summary_status = get_string(summary, "overallStatus", "proof_summary.json")
        latest_status = get_string(latest, "overallStatus", "latest_proof_run.json")
        if summary_status != "PASS":
            return fail(f"proof_summary.json overallStatus != PASS ({summary_status})")
        if latest_status != "PASS":
            return fail(f"latest_proof_run.json overallStatus != PASS ({latest_status})")
        if "- overallStatus: PASS" not in receipt_text:
            return fail("proof_receipt.md overallStatus != PASS")

        if latest.get("manifestFile") != "run_manifest.json":
            return fail("latest_proof_run.json manifestFile drifted")
        if latest.get("summaryFile") != "proof_summary.json":
            return fail("latest_proof_run.json summaryFile drifted")
        if latest.get("indexFile") != "proof_index.json":
            return fail("latest_proof_run.json indexFile drifted")

        summary_rows = summary.get("proofs")
        index_rows = index.get("proofs")
        if not isinstance(summary_rows, list) or not isinstance(index_rows, list):
            return fail("proof tables missing")
        if len(summary_rows) != 9:
            return fail(f"proof_summary.json proofs length != 9 ({len(summary_rows)})")
        if len(index_rows) != 9:
            return fail(f"proof_index.json proofs length != 9 ({len(index_rows)})")

        summary_ids = [row.get("proofId") for row in summary_rows]
        index_ids = [row.get("proofId") for row in index_rows]
        if summary_ids != CANONICAL_PROOF_IDS:
            return fail("proof_summary.json proof order drifted")
        if index_ids != CANONICAL_PROOF_IDS:
            return fail("proof_index.json proof order drifted")

        summary_map = {row.get("proofId"): row for row in summary_rows}
        index_map = {row.get("proofId"): row for row in index_rows}
        for proof_id in CANONICAL_PROOF_IDS:
            srow = summary_map.get(proof_id)
            irow = index_map.get(proof_id)
            if not isinstance(srow, dict):
                return fail(f"proof_summary.json missing proof {proof_id}")
            if not isinstance(irow, dict):
                return fail(f"proof_index.json missing proof {proof_id}")
            if srow.get("status") != "PASS":
                return fail(f"proof_summary.json proof not PASS: {proof_id}")
            if srow.get("label") != irow.get("label"):
                return fail(f"label mismatch for {proof_id}")
            if srow.get("notesFile") != irow.get("notesFile"):
                return fail(f"notesFile mismatch for {proof_id}")
            if srow.get("primaryScreenshotFile") != irow.get("primaryScreenshotFile"):
                return fail(f"primaryScreenshotFile mismatch for {proof_id}")

        receipt_rows = extract_receipt_rows(receipt_text)
        if len(receipt_rows) != 9:
            return fail(f"proof_receipt.md table rows != 9 ({len(receipt_rows)})")
        receipt_ids = [row[0] for row in receipt_rows]
        if receipt_ids != CANONICAL_PROOF_IDS:
            return fail("proof_receipt.md proof order drifted")
        for row in receipt_rows:
            if row[2] != "PASS":
                return fail(f"proof_receipt.md proof not PASS: {row[0]}")

        require_files_exist(root, summary_rows, "proof_summary.json")
        require_files_exist(root, index_rows, "proof_index.json")

    except Exception as exc:
        return fail(str(exc))

    print(f"PASS: lowered-side-slab proof bundle verified at {root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
