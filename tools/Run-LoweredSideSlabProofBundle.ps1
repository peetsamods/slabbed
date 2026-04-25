#Requires -Version 5.1
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot  = Split-Path -Parent $scriptDir
Set-Location $repoRoot

$branch   = git rev-parse --abbrev-ref HEAD
$headShort = git rev-parse --short HEAD
Write-Host "branch: $branch"
Write-Host "head:   $headShort"

# ---------------------------------------------------------------------------
# Stage 1: compile game-test sources
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "=== step: compileGametestJava ==="
& .\gradlew.bat compileGametestJava
if ($LASTEXITCODE -ne 0) { throw "compileGametestJava failed (exit $LASTEXITCODE)" }

# ---------------------------------------------------------------------------
# Stage 2: run client game test
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "=== step: runClientGameTest ==="
& .\gradlew.bat runClientGameTest
if ($LASTEXITCODE -ne 0) { throw "runClientGameTest failed (exit $LASTEXITCODE)" }

# ---------------------------------------------------------------------------
# Stage 3: verify proof bundle
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "=== step: verify bundle ==="

$artifactDir = "build\run\clientGameTest\screenshots"

$python = $null
if (Get-Command python -ErrorAction SilentlyContinue) {
    $python = 'python'
} elseif (Get-Command py -ErrorAction SilentlyContinue) {
    $python = 'py'
} else {
    throw "Python not found. Install Python 3 and ensure it is on PATH."
}

& $python tools\verify_lowered_side_slab_proof_bundle.py $artifactDir
if ($LASTEXITCODE -ne 0) { throw "Proof bundle verification failed (exit $LASTEXITCODE)" }

# ---------------------------------------------------------------------------
# Artifact paths
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "=== artifacts ==="
$artifacts = @(
    "build\run\clientGameTest\screenshots\run_manifest.json",
    "build\run\clientGameTest\screenshots\proof_summary.json",
    "build\run\clientGameTest\screenshots\proof_index.json",
    "build\run\clientGameTest\screenshots\latest_proof_run.json",
    "build\run\clientGameTest\screenshots\proof_receipt.md"
)
foreach ($path in $artifacts) {
    if (-not (Test-Path $path)) {
        throw "Required artifact missing: $path"
    }
    Write-Host $path
}

Write-Host ""
Write-Host "PASS: lowered-side-slab proof bundle runner complete."
