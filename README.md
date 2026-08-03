# Slabbed

Slabbed is a Fabric mod for Minecraft 26.2 that makes slab-supported placement, stored height,
targeting, and rendering agree. Its governing invariant is simple: a placed block keeps the height
chosen when it was placed; a later neighbor edit must not move it. See `LAW.md` for the exact rule.

## Current development state

- Source version: `0.5.0-beta.9+26.2`.
- Minecraft: 26.2; Fabric Loader and Fabric API are required.
- Java: 25 for this checkout's build and test tasks.
- This is an active development candidate, not a release recommendation. The current live RED is an
  iron-chain render-refresh defect: chain data and outlines can move to their stored height while the
  baked chain model remains at its prior height. The active handoff records the exact boundary.

## What Slabbed changes

- Places and stores supported blocks at the aimed visible height, then preserves that stored height.
- Keeps client targeting, outline, collision, and model paths aligned with the stored position where
  that path is implemented and proven.
- Supplies compatibility and test tooling for slab, deep-stack, attachment, and interaction cases.

This does not mean every item family or rendering path is fully accepted. A green build is not a live
acceptance result; the current status and known open work live in `HANDOFF.md` and `SLABBED_SPINE.md`.

## Installation

Install the normal Slabbed jar and Fabric API on the environment appropriate for the feature being used.
For a real multiplayer deployment, keep client and server Slabbed versions aligned. Do not use a TEST
jar as a release artifact.

## Diagnostics and testing

The normal build intentionally excludes diagnostic and GameTest-only implementation. TEST 35 packages
the existing diagnostics as a separately declared nested test mod, so `/slabdev`, the live recorder,
the model-stale Sentinel, and the target overlay are available only in the named diagnostic test jar.
Its evidence is never a release claim by itself.

Use `docs/process/LIVE_DRIVE_PREFLIGHT.md` before a live session, and
`docs/process/MODEL_STALE_RUNBOOK.md` to interpret a Sentinel row.

## License and feedback

Slabbed is licensed under **MIT**. Report bugs or ideas at
<https://github.com/joolbits/slabbed/issues>.
