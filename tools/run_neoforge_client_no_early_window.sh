#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
config_path="$repo_root/run/config/fml.toml"
evidence_dir="$repo_root/tmp/neoforge-port-20260615"
stamp="$(date +%H%M%S)"
backup_path="$evidence_dir/fml-before-run-client-no-early-window-${stamp}.toml"

cd "$repo_root"
mkdir -p "$evidence_dir"

if [[ ! -f "$config_path" ]]; then
    ./gradlew --no-daemon prepareClientRun --console plain
fi

cp "$config_path" "$backup_path"

restore_config() {
    if [[ -f "$backup_path" ]]; then
        cp "$backup_path" "$config_path"
    fi
}

trap 'status=$?; restore_config; exit "$status"' EXIT
trap 'restore_config; exit 129' HUP
trap 'restore_config; exit 130' INT
trap 'restore_config; exit 143' TERM

if rg -q '^earlyWindowControl = true$' "$config_path"; then
    /usr/bin/perl -0pi -e 's/^earlyWindowControl = true$/earlyWindowControl = false/m' "$config_path"
elif ! rg -q '^earlyWindowControl = false$' "$config_path"; then
    echo "Could not find earlyWindowControl in $config_path" >&2
    exit 1
fi

echo "Using temporary NeoForge earlyWindowControl=false"
echo "config: $config_path"
echo "backup: $backup_path"
./gradlew --no-daemon runClient --console plain "$@"
