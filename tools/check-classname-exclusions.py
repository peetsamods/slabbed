#!/usr/bin/env python3
"""Classname-exclusion tripwire — the exclude-by-behavior law, machine-gated.

This project's most-repeated regression class is a block-CLASS test standing in for a
geometry/role question ("instanceof FenceBlock", the snow exclusion, the carpet carve-out,
"rejected every slab by class"). Per LAW 2 (LAW.md), eligibility follows GEOMETRY — a classname
carve-out is presumed a bug until consciously ruled otherwise.

This script extracts every `instanceof <X>Block` test in the height-resolution core
(SlabSupport.java) and diffs the sorted multiset against the committed baseline
(tools/classname-exclusions-baseline.txt). Any NEW class check fails the build until the
baseline is updated in the same commit — turning an unconscious carve-out into a reviewed,
one-line diff with a reason.

Usage:
    python3 tools/check-classname-exclusions.py            # verify (exit 1 on drift)
    python3 tools/check-classname-exclusions.py --update   # rewrite the baseline

The baseline lists `<count>\t<ClassName>` pairs. A REMOVED check also fails verification:
deletions are usually good news, but the baseline must record them so the history of the
exclusion surface stays reviewable.
"""
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "src/main/java/com/slabbed/util/SlabSupport.java"
BASELINE = ROOT / "tools/classname-exclusions-baseline.txt"
PATTERN = re.compile(r"instanceof\s+([A-Z][A-Za-z]*Block)\b")


def measured() -> Counter:
    counts = Counter()
    for line in SOURCE.read_text().splitlines():
        stripped = line.strip()
        # comments and javadoc discuss historical checks; only live code counts
        if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
            continue
        for m in PATTERN.finditer(line):
            counts[m.group(1)] += 1
    return counts


def render(counts: Counter) -> str:
    lines = [f"{n}\t{cls}" for cls, n in sorted(counts.items())]
    return "\n".join(lines) + "\n"


def main() -> int:
    counts = measured()
    if "--update" in sys.argv:
        BASELINE.write_text(render(counts))
        print(f"baseline updated: {sum(counts.values())} checks across {len(counts)} classes")
        return 0
    if not BASELINE.exists():
        print(f"MISSING BASELINE: {BASELINE} — run with --update to create it (then review the diff).")
        return 1
    expected = Counter()
    for line in BASELINE.read_text().splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        n, cls = line.split("\t")
        expected[cls] = int(n)
    if counts == expected:
        print(f"classname-exclusion tripwire: OK — {sum(counts.values())} checks across "
              f"{len(counts)} classes, matching the baseline.")
        return 0
    print("classname-exclusion tripwire: DRIFT — the instanceof-Block surface of SlabSupport "
          "changed. Per LAW 2 a class test standing in for geometry is the recurring bug class; "
          "if this change is a conscious ruling, update the baseline IN THIS COMMIT:")
    for cls in sorted(set(counts) | set(expected)):
        if counts[cls] != expected[cls]:
            print(f"  {cls}: baseline {expected[cls]} -> now {counts[cls]}")
    print("  (python3 tools/check-classname-exclusions.py --update)")
    return 1


if __name__ == "__main__":
    sys.exit(main())
