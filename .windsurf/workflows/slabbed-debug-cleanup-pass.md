# Slabbed — Workflow: Debug Cleanup Pass

## Goal
Disable all debug instrumentation for production readiness.

## Steps
1) Run skill: slabbed-debug-cleanup-pass
2) Run skill: slabbed-release-sanity-check
3) Commit: chore: debug cleanup pass
4) Tag: slabbed-debug-cleanup-pass
