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
- Current operating base HEAD: `ec650eb`
- Current operating base tag: `save/visible-face-placement-intent-authority`
- Stale, provenance-confusing tag: `save/real-placed-lowered-bottom-slab-persistence`

The current operating base is `ec650eb` / `save/visible-face-placement-intent-authority`.
The current savepoint target is `save/slab-held-retarget-parity-improvements`.

## Current tracked tree state

Tracked tree is clean.
`tmp/` may remain intentionally untracked.

## Current product goal

Preserve the live-proven slab-held retarget parity improvement before any follow-up screenshot issue work.

## Current proof note

Slab-held anchored-FB parity improved: slab-held targeting can now select the same visible anchored lowered full-block owner as block-held targeting where proven.
Side-owner parity improved: proven `sideOwnerWouldWin` paths now flow through `scan-side-slab-fired`, including the adjacent-lane initial-MISS rescue family.
Latest Julia live result: much better overall and worth saving; small residual janks remain and are not part of this savepoint.
Automation passed before savepoint: `compileJava compileGametestJava`, `runClientGameTest`, `git diff --check`.
No intentional placement-intent, survival, collision, model, outline, or non-targeting raycast authority changes.

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
- Pending savepoint: `save/slab-held-retarget-parity-improvements`. Preserves the live-proven targeting improvement while leaving residual screenshot janks for a later narrow slice.
- `save/real-lowered-bottom-slab-under-placement-persistence`
- `save/real-placed-lowered-bottom-slab-persistence` is historical only and should not be treated as final truth.

## Current next action

Narrow follow-up slice for the remaining screenshot issue after this savepoint.

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
