#!/usr/bin/env python3
import argparse
import json
import re
import sys
from pathlib import Path


PACKAGE_RE = re.compile(r"^\s*package\s+([A-Za-z_][\w.]*)\s*;", re.MULTILINE)
CLIENT_GAMETEST_RE = re.compile(
    r"\bclass\s+([A-Za-z_$][\w$]*)\b[^{;]*\bimplements\b[^{;]*\bFabricClientGameTest\b",
    re.DOTALL,
)


def fail(message: str) -> int:
    print(f"FAIL: {message}")
    return 1


def load_registered_client_gametests(manifest_path: Path) -> set[str]:
    with manifest_path.open("r", encoding="utf-8") as handle:
        manifest = json.load(handle)
    entrypoints = manifest.get("entrypoints", {})
    client_gametests = entrypoints.get("fabric-client-gametest", [])
    if not isinstance(client_gametests, list):
        raise ValueError("entrypoints.fabric-client-gametest is not a list")
    return {entry for entry in client_gametests if isinstance(entry, str)}


def client_gametest_implementations(source_dir: Path) -> list[tuple[str, Path, int]]:
    found = []
    for path in sorted(source_dir.rglob("*ClientGameTest.java")):
        text = path.read_text(encoding="utf-8")
        package_match = PACKAGE_RE.search(text)
        package_name = package_match.group(1) if package_match else ""
        for match in CLIENT_GAMETEST_RE.finditer(text):
            class_name = match.group(1)
            fqn = f"{package_name}.{class_name}" if package_name else class_name
            line = text.count("\n", 0, match.start()) + 1
            found.append((fqn, path, line))
    return found


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify Fabric client GameTest implementations are registered.",
    )
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path("src/gametest/resources/fabric.mod.json"),
    )
    parser.add_argument(
        "--source-dir",
        type=Path,
        default=Path("src/gametest/java"),
    )
    args = parser.parse_args()

    root = args.root.resolve()
    manifest_path = args.manifest if args.manifest.is_absolute() else root / args.manifest
    source_dir = args.source_dir if args.source_dir.is_absolute() else root / args.source_dir

    try:
        registered = load_registered_client_gametests(manifest_path)
        implementations = client_gametest_implementations(source_dir)
    except Exception as exc:
        return fail(str(exc))

    unregistered = [
        (fqn, path, line)
        for fqn, path, line in implementations
        if fqn not in registered
    ]
    if unregistered:
        print("FAIL: unregistered FabricClientGameTest implementation(s):")
        for fqn, path, line in unregistered:
            rel = path.relative_to(root) if path.is_relative_to(root) else path
            print(f"- {fqn} ({rel}:{line})")
        return 1

    print(
        "PASS: all "
        f"{len(implementations)} FabricClientGameTest implementation(s) are registered"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
