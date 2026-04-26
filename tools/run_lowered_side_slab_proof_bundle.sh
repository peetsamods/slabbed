#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
artifact_dir="${1:-build/run/clientGameTest/screenshots}"
jdk_home="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"

cd "$repo_root"

branch="$(git rev-parse --abbrev-ref HEAD)"
head_short="$(git rev-parse --short HEAD)"

echo "branch: ${branch}"
echo "head: ${head_short}"
echo "step: compileGametestJava"
bash ./gradlew -Dorg.gradle.java.home="$jdk_home" compileGametestJava

echo "step: runClientGameTest"
bash ./gradlew -Dorg.gradle.java.home="$jdk_home" runClientGameTest

echo "step: verify bundle"
python3 tools/verify_lowered_side_slab_proof_bundle.py "$artifact_dir"

echo "artifacts:"
printf '%s\n' \
  "build/run/clientGameTest/screenshots/run_manifest.json" \
  "build/run/clientGameTest/screenshots/proof_summary.json" \
  "build/run/clientGameTest/screenshots/proof_index.json" \
  "build/run/clientGameTest/screenshots/latest_proof_run.json" \
  "build/run/clientGameTest/screenshots/proof_receipt.md"
