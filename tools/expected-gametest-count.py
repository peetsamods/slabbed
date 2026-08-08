#!/usr/bin/env python3
"""Expected gametest suite count — the anti-false-green check.

A stale persisted world in build/run/gameTest can make the suite silently drop
tests while still reporting BUILD SUCCESSFUL (observed 2026-08-06: 166 reported,
~65 lost, no warning). A green run is therefore NOT proof on its own: the
reported "All N required tests passed" count must match this script's output.

Counts every literal '@GameTest' occurrence in every class registered under the
'fabric-gametest' entrypoint, plus 1 harness-contributed test. Because it counts
LITERAL occurrences (annotation or not), never write the bare '@GameTest' token
in a registered class's comments or javadoc.

Usage (from the repo root):
    python3 tools/expected-gametest-count.py

If the live run reports fewer:  rm -rf build/run/gameTest  and re-run.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOD_JSON = ROOT / "src/gametest/resources/fabric.mod.json"
HARNESS_TESTS = 0  # this line has no harness-contributed test (unlike the 1.21.11 parity line)


def main() -> int:
    entrypoints = json.loads(MOD_JSON.read_text())["entrypoints"]["fabric-gametest"]
    total = 0
    for cls in entrypoints:
        src = ROOT / "src/gametest/java" / (cls.replace(".", "/") + ".java")
        count = src.read_text().count("@GameTest")
        total += count
    expected = total + HARNESS_TESTS
    print(f"{total} @GameTest occurrences in {len(entrypoints)} registered classes "
          f"+ {HARNESS_TESTS} harness test = expected suite count {expected}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
