#!/usr/bin/env python3
"""Print the number of executable GameTest annotations in the selected test source set."""

from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD_FILE = ROOT / "build.gradle"
SOURCE_ROOT = ROOT / "src" / "gametest" / "java"
INVENTORY_FILE = ROOT / "tools" / "gametest-inventory.json"
EXPECTED_EXECUTABLE_COUNT = 261
REQUIRED_LAW_TEST_PATH = "com/slabbed/test/NeighborUpdateInvarianceTest.java"
EXPECTED_REGISTERED_SERVER_PATHS = frozenset(
    {
        "com/slabbed/test/CeilingFlushRulingTest.java",
        "com/slabbed/test/ChainColumnLoweredCapCoherenceTest.java",
        "com/slabbed/test/ChainSurvivalReproTest.java",
        "com/slabbed/test/CombinedSlabChainingMatrixTest.java",
        "com/slabbed/test/DeepDyConsentTest.java",
        "com/slabbed/test/DySpecificationTest.java",
        "com/slabbed/test/LegacySupportSeatResolutionTest.java",
        "com/slabbed/test/Mc1211GoblinRouteCanaryGameTest.java",
        REQUIRED_LAW_TEST_PATH,
        "com/slabbed/test/NeverPopBreadthTest.java",
        "com/slabbed/test/OpenMinus1MeasurementTest.java",
        "com/slabbed/test/P6InteractionParityTest.java",
        "com/slabbed/test/P7VisualParityTest.java",
        "com/slabbed/test/SlabPlacementHeightPersistenceSyncTest.java",
        "com/slabbed/test/SlabPlacementHeightLifecycleTest.java",
        "com/slabbed/test/SlabPlacementHeightResolverTest.java",
        "com/slabbed/test/SlabPlacementHeightSchemaTest.java",
        "com/slabbed/test/SlabbedHotPathAllocationRegressionTest.java",
        "com/slabbed/test/SlabHeightStepCullTest.java",
        "com/slabbed/test/SlabbedLabFixtureTest.java",
        "com/slabbed/test/TerrainSlabsHeadlessClassifierTest.java",
        "com/slabbed/test/UpFaceEdgeCombineGuardTest.java",
        "com/slabbed/test/VanillaDownstreamOwnershipTest.java",
    }
)
VALID_CLASSIFICATIONS = frozenset({"registered_server", "client_only"})
RAW_JAVA_UNICODE_ESCAPE = re.compile(r"\\u+[0-9A-Fa-f]{4}")
GAME_TEST_ANNOTATION = re.compile(
    r"@\s*(?:"
    r"GameTest"
    r"|net\s*\.\s*minecraft\s*\.\s*gametest\s*\.\s*framework\s*\.\s*GameTest"
    r")(?![\w$]|\s*\.)"
)


def java_code(source: str) -> str:
    """Blank Java comments and literals while preserving code positions and line breaks."""
    result: list[str] = []
    index = 0
    state = "code"

    def blank(text: str) -> str:
        return "".join(character if character in "\r\n" else " " for character in text)

    while index < len(source):
        if state == "code":
            if source.startswith("//", index):
                result.append("  ")
                index += 2
                state = "line_comment"
            elif source.startswith("/*", index):
                result.append("  ")
                index += 2
                state = "block_comment"
            elif source.startswith('"""', index):
                result.append("   ")
                index += 3
                state = "text_block"
            elif source[index] == '"':
                result.append(" ")
                index += 1
                state = "string"
            elif source[index] == "'":
                result.append(" ")
                index += 1
                state = "character"
            else:
                result.append(source[index])
                index += 1
        elif state == "line_comment":
            if source[index] in "\r\n":
                result.append(source[index])
                state = "code"
            else:
                result.append(" ")
            index += 1
        elif state == "block_comment":
            if source.startswith("*/", index):
                result.append("  ")
                index += 2
                state = "code"
            else:
                result.append(blank(source[index]))
                index += 1
        elif state == "text_block":
            if source.startswith('"""', index):
                result.append("   ")
                index += 3
                state = "code"
            elif source[index] == "\\" and index + 1 < len(source):
                result.append(blank(source[index:index + 2]))
                index += 2
            else:
                result.append(blank(source[index]))
                index += 1
        else:
            delimiter = '"' if state == "string" else "'"
            if source[index] == "\\" and index + 1 < len(source):
                result.append(blank(source[index:index + 2]))
                index += 2
            elif source[index] == delimiter:
                result.append(" ")
                index += 1
                state = "code"
            else:
                result.append(blank(source[index]))
                index += 1

    return "".join(result)


def executable_annotation_count(source: str) -> int:
    if RAW_JAVA_UNICODE_ESCAPE.search(source):
        raise SystemExit(
            "GameTest source contains a raw Java Unicode escape; "
            "refusing to lex GameTest annotations"
        )
    return len(GAME_TEST_ANNOTATION.findall(java_code(source)))


def inventory_entries() -> list[dict[str, str]]:
    try:
        raw_entries = json.loads(INVENTORY_FILE.read_text(encoding="utf-8"))["entries"]
    except (OSError, ValueError, KeyError, TypeError) as error:
        raise SystemExit("GameTest inventory is unreadable") from error
    if not isinstance(raw_entries, list):
        raise SystemExit("GameTest inventory entries must be a list")

    entries: list[dict[str, str]] = []
    for entry in raw_entries:
        if not isinstance(entry, dict):
            raise SystemExit("GameTest inventory entry must be an object")
        path = entry.get("path")
        classification = entry.get("classification")
        if not isinstance(path, str) or not isinstance(classification, str):
            raise SystemExit("GameTest inventory entry is missing path or classification")
        if classification not in VALID_CLASSIFICATIONS:
            raise SystemExit("GameTest inventory has an unknown classification: " + classification)
        entries.append({"path": path, "classification": classification})
    return entries


def verify_inventory(entries: list[dict[str, str]]) -> list[Path]:
    manifest_paths = [entry["path"] for entry in entries]
    if len(manifest_paths) != len(set(manifest_paths)):
        raise SystemExit("GameTest inventory contains duplicate paths")

    actual_paths = {
        source.relative_to(SOURCE_ROOT).as_posix()
        for source in SOURCE_ROOT.rglob("*.java")
    }
    if set(manifest_paths) != actual_paths:
        missing = sorted(actual_paths - set(manifest_paths))
        stale = sorted(set(manifest_paths) - actual_paths)
        details = []
        if missing:
            details.append("unclassified: " + ", ".join(missing))
        if stale:
            details.append("missing files: " + ", ".join(stale))
        raise SystemExit("GameTest inventory mismatch: " + "; ".join(details))

    registered_paths = {
        entry["path"]
        for entry in entries
        if entry["classification"] == "registered_server"
    }
    if registered_paths != EXPECTED_REGISTERED_SERVER_PATHS:
        missing = sorted(EXPECTED_REGISTERED_SERVER_PATHS - registered_paths)
        unexpected = sorted(registered_paths - EXPECTED_REGISTERED_SERVER_PATHS)
        details = []
        if missing:
            details.append("missing required registrations: " + ", ".join(missing))
        if unexpected:
            details.append("unexpected registrations: " + ", ".join(unexpected))
        raise SystemExit("GameTest registration floor mismatch: " + "; ".join(details))
    if REQUIRED_LAW_TEST_PATH not in registered_paths:
        raise SystemExit("GameTest registration omits the required law test")

    registered = [
        SOURCE_ROOT / entry["path"]
        for entry in entries
        if entry["classification"] == "registered_server"
    ]
    if not registered:
        raise SystemExit("GameTest inventory has no registered server tests")

    for entry in entries:
        source = SOURCE_ROOT / entry["path"]
        found = executable_annotation_count(source.read_text(encoding="utf-8"))
        if entry["classification"] == "registered_server" and found == 0:
            raise SystemExit("Registered server test has no executable annotations: " + entry["path"])
        if entry["classification"] != "registered_server" and found != 0:
            raise SystemExit("Unregistered source has executable annotations: " + entry["path"])
    return registered


def selected_sources(registered: list[Path]) -> list[Path]:
    build_text = BUILD_FILE.read_text(encoding="utf-8")
    try:
        game_test_block = build_text.split("    gametest {", 1)[1].split("\n    }\n}\n\nrepositories", 1)[0]
    except IndexError as error:
        raise SystemExit("The gametest source-set block could not be found") from error
    selected = re.findall(r'"([^"]+\.java)"', game_test_block)
    selected_test_paths = {
        SOURCE_ROOT / path
        for path in selected
        if path.startswith("com/slabbed/test/")
    }
    registered_paths = set(registered)
    if selected_test_paths != registered_paths:
        omitted = sorted(path.relative_to(SOURCE_ROOT).as_posix() for path in registered_paths - selected_test_paths)
        unexpected = sorted(path.relative_to(SOURCE_ROOT).as_posix() for path in selected_test_paths - registered_paths)
        details = []
        if omitted:
            details.append("omitted registrations: " + ", ".join(omitted))
        if unexpected:
            details.append("unclassified registrations: " + ", ".join(unexpected))
        raise SystemExit("GameTest Gradle registration mismatch: " + "; ".join(details))
    if not selected_test_paths:
        raise SystemExit("No GameTest Java sources were selected by build.gradle")
    missing = [str(path.relative_to(ROOT)) for path in selected_test_paths if not path.is_file()]
    if missing:
        raise SystemExit("Selected GameTest sources are missing: " + ", ".join(missing))
    return sorted(selected_test_paths)


def main() -> None:
    registered = verify_inventory(inventory_entries())
    total = sum(
        executable_annotation_count(path.read_text(encoding="utf-8"))
        for path in selected_sources(registered)
    )
    if total != EXPECTED_EXECUTABLE_COUNT:
        raise SystemExit(
            "GameTest executable count floor mismatch: expected "
            + str(EXPECTED_EXECUTABLE_COUNT)
            + ", found "
            + str(total)
        )
    print(total)


if __name__ == "__main__":
    main()
