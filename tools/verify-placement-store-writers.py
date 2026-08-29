#!/usr/bin/env python3
"""Require the placement-height record to keep exactly its approved production owners.

The record's value is that one code path decides a placement's height once, at capture
time, and nothing else rewrites it afterwards. That invariant is invisible in a passing
suite: a second owner added anywhere in production would leave every GameTest green while
quietly making the frozen height a value more than one caller can change.

WHAT THIS GATE COVERS. Every production route that can reach the record, not just the two
obvious method names:

  * the mutators themselves (`putHalfSteps`, `remove`);
  * `clearPlacementTruth`, the public wrapper that re-exports `remove` - the number of
    removal owners is set at the outer edge, not the inner one;
  * `PLACEMENT_DY_TYPE` used outside its owning class, which hands out the backing map by
    reference and so allows an in-place `put` with no gated call at all;
  * `installClientRenderHalfStepsLookup`, which owns the height the chunk renderer sees.

Each is matched in qualified, static-import and method-reference form, per CALL SITE rather
than per file, so a second call inside an already-approved file still trips the gate.

WHAT IT DOES NOT COVER, stated so the gate is not mistaken for more than it is: a brand-new
mutator method added to the owning class itself is invisible here, because the gate
enumerates uses from other files. Reflection is likewise out of reach - string literals are
blanked before matching, deliberately, so prose never counts.

SCOPE. Both production source roots. Some scanned files are excluded from the main compile
and never ship; an approved entry for one of those would be over-strict, never unsafe.
Test sources are out of scope on purpose: fixtures author scenes by design.
"""

from __future__ import annotations

import importlib.util
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
PRODUCTION_ROOTS = (ROOT / "src" / "main" / "java", ROOT / "src" / "client" / "java")
RAW_JAVA_UNICODE_ESCAPE = re.compile(r"\\u+[0-9A-Fa-f]{4}")

# owner symbol -> {relative path: (exact call count, reason a second owner would need)}
APPROVED_OWNERS: dict[str, dict[str, tuple[int, str]]] = {
    "SlabPlacementHeightAttachment.putHalfSteps": {
        "com/slabbed/mixin/BlockItemPlacementIntentMixin.java":
            (1, "the capture publish - the single initial writer of a placement's height"),
    },
    "SlabPlacementHeightAttachment.remove": {
        "com/slabbed/anchor/SlabAnchorAttachment.java":
            (1, "removal clears only the removed block's record"),
    },
    "SlabAnchorAttachment.clearPlacementTruth": {
        "com/slabbed/mixin/BlockOnStateReplacedAnchorMixin.java":
            (1, "the removal hook, the sole outer-edge caller of the removal wrapper"),
    },
    "SlabPlacementHeightAttachment.PLACEMENT_DY_TYPE": {
        "com/slabbed/client/SlabPlacementHeightClientSync.java":
            (1, "the client sync reads the synchronized map to publish a render snapshot"),
    },
    "SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup": {
        "com/slabbed/client/SlabPlacementHeightClientSync.java":
            (1, "installs the render-side lookup the chunk renderer consults"),
    },
}


def load_shared_lexer():
    """Reuse the sibling gate's Java lexer rather than keeping a second copy of it."""
    path = ROOT / "tools" / "expected-gametest-count.py"
    spec = importlib.util.spec_from_file_location("slabbed_expected_gametest_count", path)
    if spec is None or spec.loader is None:
        raise SystemExit("cannot load the shared Java lexer from " + str(path))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    lexer = module.java_code
    # A lexer that silently stopped blanking would turn this gate into a no-op that still
    # prints success, so prove it still works before trusting a single result.
    probe = lexer('String s = "x.y(";\n// z.w(\nint a = 1;\n')
    if "x.y(" in probe or "z.w(" in probe or "int a = 1;" not in probe:
        raise SystemExit("the shared Java lexer no longer blanks comments and literals")
    return lexer


def patterns_for(symbol: str) -> list[re.Pattern[str]]:
    owner, member = symbol.split(".", 1)
    o, m = re.escape(owner), re.escape(member)
    return [
        re.compile(o + r"\s*\.\s*" + m + r"\b"),          # qualified use and field access
        re.compile(o + r"\s*::\s*" + m + r"\b"),          # method reference
        re.compile(r"(?<![\w.])" + m + r"\s*\("),         # bare call via static import
    ]


def count_sites(code: str, symbol: str) -> int:
    """Count call sites, taking the widest matching form so one site is never double-counted."""
    return max(len(pattern.findall(code)) for pattern in patterns_for(symbol))


def scan(lexer) -> dict[str, dict[str, int]]:
    found: dict[str, dict[str, int]] = {symbol: {} for symbol in APPROVED_OWNERS}
    owning_files = {symbol.split(".", 1)[0] + ".java" for symbol in APPROVED_OWNERS}
    for root in PRODUCTION_ROOTS:
        if not root.is_dir():
            continue
        for source in sorted(root.rglob("*.java")):
            relative = source.relative_to(root).as_posix()
            try:
                text = source.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                raise SystemExit("source is not valid UTF-8 and cannot be checked: " + relative)
            if RAW_JAVA_UNICODE_ESCAPE.search(text):
                raise SystemExit(
                    "source contains a raw Java unicode escape and cannot be lexed "
                    "reliably: " + relative)
            code = lexer(text)
            for symbol in APPROVED_OWNERS:
                # A class's own unqualified self-calls are out of reach by construction; skip
                # its file so the bare-call pattern does not report its declaration.
                if source.name in owning_files and source.name.startswith(symbol.split(".", 1)[0]):
                    continue
                hits = count_sites(code, symbol)
                if hits:
                    found[symbol][relative] = hits
    return found


def main() -> None:
    lexer = load_shared_lexer()
    found = scan(lexer)

    if "--report" in sys.argv:
        for symbol, sites in found.items():
            print(symbol + ":")
            for path, count in sorted(sites.items()):
                print("  " + str(count) + "  " + path)
        return

    problems: list[str] = []
    for symbol, approved in APPROVED_OWNERS.items():
        for path, count in sorted(found[symbol].items()):
            if path not in approved:
                problems.append(
                    "unapproved " + symbol + " use: " + path + " (" + str(count) + ") - a second "
                    "owner of the frozen height needs an entry in "
                    "tools/verify-placement-store-writers.py stating why")
            elif count != approved[path][0]:
                problems.append(
                    symbol + " call count changed in " + path + ": approved "
                    + str(approved[path][0]) + ", found " + str(count)
                    + " - update the entry with the reason for the extra owner")
        for path in sorted(set(approved) - set(found[symbol])):
            problems.append(
                "approved " + symbol + " use no longer exists: " + path
                + " - remove the stale entry")

    if problems:
        raise SystemExit("\n".join(problems))

    total = sum(count for sites in found.values() for count in sites.values())
    print("placement record owners verified: " + str(total)
          + " approved call sites across " + str(len(APPROVED_OWNERS)) + " routes")


if __name__ == "__main__":
    main()
