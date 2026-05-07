# SLABBED_SPINE.md -- Active Project Spine

This is the active operating spine for Slabbed. It is not the Git working tree, not a full archive, and not a replacement for source doctrine.

Use it to know the current root, branch, HEAD, savepoint, active slice, invariants, proof gates, and next safe step.

## Read order

1. `00_SLABBED_SOURCE_INDEX.md`
2. `01_SLABBED_CANONICAL_DOCTRINE.md`
3. `02_SLABBED_ACTIVE_STATUS.md`
4. `SLABBED_SPINE.md`

## Canonical root

`/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate`

The older `/Users/joolmac/CascadeProjects/Slabbed` checkout is archive/recovery only unless Julia explicitly says otherwise.

## Current branch / HEAD / tag

- Branch: `integrate/phase19-into-side-slab-top-support`
- Current operating base HEAD before savepoint: `571ba89`
- Current operating base tag before savepoint: `save/slab-held-retarget-parity-improvements`
- Stale, provenance-confusing tag: `save/real-placed-lowered-bottom-slab-persistence`

The current savepoint target is `save/lowered-slab-face-placement-inheritance`.

## Current tracked tree state

Tracked tree is clean.
`tmp/` may remain intentionally untracked.

## Current product goal

Save the live-proven lowered slab face placement, full-block authoring, and post-placement slab-held targeting fix before any fresh issue work.

## Current proof note

Julia live result: "Perfect! Save."

Live retest confirmed:

- Stone slab placed against lowered slab face joins the lowered lane, with no ghost face.
- Ordinary full blocks and logs placed against lowered slab face place lowered/aligned.
- Newly placed full block becomes targetable with normal block held.
- Newly placed full block also becomes targetable with stone slab held.
- Slab-held targeting no longer jumps back to the old lowered slab face.
- Ghost-face / above-placement issue is resolved for this repro.

Mechanisms saved:

- Slab placement against visible lowered slab face now authors a non-persistent lowered slab lane: placed slab `dy=-0.5`, `persistentLoweredSlabCarrier=false`, and orphan teardown remains valid.
- Ordinary full-block placement against a valid non-persistent lowered bottom slab source now authors the placed full block through the established anchored full-block path: source slab remains non-persistent, placed full block `dy=-0.5`, placed full block `anchored=true`.
- Post-placement slab-held targeting now respects the newly placed anchored lowered full-block owner: slab-held no longer preserves the old lowered slab face before comparing a valid anchored full-block candidate, so final slab-held target can resolve to the newly placed anchored full block.
- Existing protections preserved: Phase19 true-top proof green, orphan lowered-lane teardown green, saved slab-held retarget parity preserved, no targeting/rescue broadening outside proven branches, and no model/outline/raycast dy authority changes.

Proof markers:

- `LIVE_CLICK_PAIR_BOTTOM_SLAB_LANE_INHERITANCE`
- `LIVE_CLICK_PAIR_FULL_BLOCK_LANE_INHERITANCE`
- Orphan teardown still green.
- Slab-held post-place anchored owner live retest passed.

## Non-negotiable invariants

- Preserve the current phase19 branch/HEAD/tag truth.
- Do not drift into broad goblin chaos by default.
- Do not chase Phase20 or Ultra2 unless a fresh live failure points there.
- Treat the spine as current context, but verify against Git before edits.

## Latest fixed Bug Blaster

Latest validated state is the lowered-bottom slab placement persistence savepoint at `435cd1a`.
Live micro-test result: PASSED. Julia tested the 8:16 PM under-placement setup on clean HEAD `435cd1a`; the legally lowered survivor slab did not jump during under-placement.

Proof-only reconfirmation on HEAD `65d4c0e` / `save/slabbed-spine-current-base`:

- Run command: `./gradlew --no-daemon runClientGameTest --console plain`
- Result: `BUILD SUCCESSFUL in 1m 46s`
- Proof case: `REAL_PLACED_LOWERED_BOTTOM_SLAB_UNDER_PLACEMENT_DOES_NOT_JUMP`
- Survivor slab result: `stone_slab[type=bottom]`, `dy=-0.5`, `modelDy=-0.5`, `outlineDy=-0.5`, `targetDy=-0.5`, `jumpDelta=0.0`
- Placed slab result: `stone_slab[type=bottom]`, `dy=-0.5`, `legalLoweredLane=true`, `legalVanillaLane=false`
- Spine-aligned trace showed `dy=-0.5 anchored=true lowered=true` after sync
- No lowered-slab jump contradiction was found
- Non-fatal environment noise appeared, including failed user properties fetch and anisotropic filtering warning; do not treat those as Slabbed failures
- Conclusion: the current saved state still preserves the no-jump lowered-bottom slab behavior around the 8:16 PM under-placement setup

## Recent relevant savepoints

- Pending savepoint: debug helper classpath closure. Packaging/classpath blocker fixed by removing or bridging production/runtime hard-links to excluded debug helpers. `compileJava compileGametestJava`, `runClientGameTest`, `clean build`, release jar leakage scan, `jdeps` hard-reference scan, and source direct-import scan passed. No gameplay behavior was intentionally changed.
- Pending savepoint: `save/lowered-slab-face-placement-inheritance`. Preserves the live-proven lowered slab face placement inheritance, full-block lane inheritance, and slab-held post-place anchored owner targeting.
- `save/slab-held-retarget-parity-improvements`
- `save/real-lowered-bottom-slab-under-placement-persistence`
- `save/real-placed-lowered-bottom-slab-persistence` is historical only and should not be treated as final truth.

## Current next action

Stop, or begin only a fresh slice if Julia reports a new distinct issue.

## Suggested live run command

`./gradlew runClientGameTest --console plain`

## Suggested log extractor

`rg -n "under-placement|8:16 PM|\\[SLABBED\\]|\\[GAME_TEST\\]"`

## Live-note format

Capture the exact setup, the observed placement result, the first failure point if any, and whether the run stayed on the current phase19 slice.

## Do not chase by default

- Do not widen into unrelated gameplay cleanup.
- Do not treat old branches or old savepoints as current just because they are nearby.
- Do not chase Phase20 or Ultra2 without a fresh live contradiction.

## Proof required before declaring fixed

- Clean root and correct branch/HEAD/tag.
- Live micro-test result for the 8:16 PM under-placement setup.
- Diff check or equivalent proof that no unrelated files changed.

## Savepoint rule

If the slice changes, update the source pack and spine together so the current operating state stays explicit.

## Notion/source update targets

- `00_SLABBED_SOURCE_INDEX.md`
- `01_SLABBED_CANONICAL_DOCTRINE.md`
- `02_SLABBED_ACTIVE_STATUS.md`
- `SLABBED_SPINE.md`
