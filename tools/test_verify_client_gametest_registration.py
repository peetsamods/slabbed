#!/usr/bin/env python3
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
SCRIPT = REPO_ROOT / "tools" / "verify_client_gametest_registration.py"


class ClientGameTestRegistrationVerifierTest(unittest.TestCase):
    def run_verifier(self, root: Path) -> subprocess.CompletedProcess:
        return subprocess.run(
            ["python3", str(SCRIPT), "--root", str(root)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )

    def write_manifest(self, root: Path, registered: list[str]) -> None:
        manifest = {
            "entrypoints": {
                "fabric-client-gametest": registered,
            },
        }
        path = root / "src/gametest/resources/fabric.mod.json"
        path.parent.mkdir(parents=True)
        path.write_text(json.dumps(manifest), encoding="utf-8")

    def write_client_test(self, root: Path, class_name: str, implements: bool) -> None:
        body = [
            "package com.example;",
            "",
            "import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;",
            "",
        ]
        suffix = " implements FabricClientGameTest" if implements else ""
        body.append(f"public final class {class_name}{suffix} {{")
        body.append("}")
        path = root / f"src/gametest/java/com/example/{class_name}.java"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("\n".join(body) + "\n", encoding="utf-8")

    def test_passes_when_all_client_gametest_implementations_are_registered(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write_manifest(root, ["com.example.RegisteredClientGameTest"])
            self.write_client_test(root, "RegisteredClientGameTest", implements=True)
            self.write_client_test(root, "LegacyHelperClientGameTest", implements=False)

            result = self.run_verifier(root)

            self.assertEqual(result.returncode, 0, result.stdout)
            self.assertIn("PASS", result.stdout)

    def test_fails_when_client_gametest_implementation_is_unregistered(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write_manifest(root, ["com.example.RegisteredClientGameTest"])
            self.write_client_test(root, "RegisteredClientGameTest", implements=True)
            self.write_client_test(root, "UnregisteredClientGameTest", implements=True)

            result = self.run_verifier(root)

            self.assertNotEqual(result.returncode, 0, result.stdout)
            self.assertIn("FAIL", result.stdout)
            self.assertIn("com.example.UnregisteredClientGameTest", result.stdout)


if __name__ == "__main__":
    unittest.main()
