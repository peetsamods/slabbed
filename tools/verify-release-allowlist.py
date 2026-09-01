#!/usr/bin/env python3
"""Require each release archive to match its exact allowlist entry inventory."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys
import zipfile


ROOT = Path(__file__).resolve().parents[1]
ALLOWLIST = ROOT / "RELEASE_ALLOWLIST.md"
MARKERS = {
    "runtime": (
        "<!-- release-allowlist:runtime:start -->",
        "<!-- release-allowlist:runtime:end -->",
    ),
    # The jar-in-jar artifact. On Forge 1.20.1 the -all jar is what players install - it is
    # the only archive carrying MixinExtras - so it is allowlisted as its own closed world
    # rather than sharing the bare runtime block it is a superset of.
    "runtime-all": (
        "<!-- release-allowlist:runtime-all:start -->",
        "<!-- release-allowlist:runtime-all:end -->",
    ),
    "sources": (
        "<!-- release-allowlist:sources:start -->",
        "<!-- release-allowlist:sources:end -->",
    ),
}


def approved_entries(artifact_kind: str) -> set[str]:
    text = ALLOWLIST.read_text(encoding="utf-8")
    start, end = MARKERS[artifact_kind]
    try:
        body = text.split(start, 1)[1].split(end, 1)[0]
    except IndexError as error:
        raise SystemExit("RELEASE_ALLOWLIST.md markers are missing for " + artifact_kind) from error
    entries = [
        line.strip()
        for line in body.splitlines()
        if line.strip() and not line.startswith("```")
    ]
    if any("*" in entry for entry in entries):
        raise SystemExit("RELEASE_ALLOWLIST.md must list exact archive entries")
    if len(entries) != len(set(entries)):
        raise SystemExit("RELEASE_ALLOWLIST.md contains duplicate archive entries for " + artifact_kind)
    return set(entries)


def unique_entries(entries: list[str]) -> set[str]:
    duplicates = sorted(entry for entry in set(entries) if entries.count(entry) > 1)
    if duplicates:
        raise SystemExit(
            "Release allowlist rejected duplicate archive entries: "
            + ", ".join(duplicates)
        )
    return set(entries)


def archive_entries(artifact: Path) -> set[str]:
    with zipfile.ZipFile(artifact) as archive:
        return unique_entries([entry.filename for entry in archive.infolist()])


def classify_artifact(artifact: Path) -> str:
    if artifact.name.endswith("-sources.jar"):
        return "sources"
    return "runtime"


def differences(actual: set[str], approved: set[str]) -> tuple[list[str], list[str]]:
    return sorted(actual - approved), sorted(approved - actual)


def verify(artifact: Path, artifact_kind: str) -> tuple[list[str], list[str]]:
    actual = archive_entries(artifact)
    approved = approved_entries(artifact_kind)
    return differences(actual, approved)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact", required=True, type=Path)
    parser.add_argument("--kind", choices=sorted(MARKERS))
    args = parser.parse_args()
    artifact_kind = args.kind or classify_artifact(args.artifact)
    unexpected, missing = verify(args.artifact, artifact_kind)
    if unexpected or missing:
        print("Release allowlist rejected: " + args.artifact.name, file=sys.stderr)
        if unexpected:
            print("unexpected entries:", file=sys.stderr)
            print("\n".join(unexpected), file=sys.stderr)
        if missing:
            print("missing entries:", file=sys.stderr)
            print("\n".join(missing), file=sys.stderr)
        raise SystemExit(1)
    print("Release allowlist verified: " + args.artifact.name + " (" + artifact_kind + ")")


if __name__ == "__main__":
    main()
