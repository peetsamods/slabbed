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
- Current operating base HEAD: `f81202e`
- Current operating base tag: `save/slabbed-agent-spine-rules`
- Stale, provenance-confusing tag: `save/real-placed-lowered-bottom-slab-persistence`

The current operating base is `f81202e` / `save/slabbed-agent-spine-rules`.
The latest proven gameplay savepoint remains `435cd1a` / `save/real-lowered-bottom-slab-under-placement-persistence`.

## Current tracked tree state

Tracked tree is clean.
`tmp/` may remain intentionally untracked.

## Current product goal

Keep the lowered-bottom slab placement/persistence slice stable and document the live operating state without widening scope.

## Non-negotiable invariants

- Preserve the current phase19 branch/HEAD/tag truth.
- Do not drift into broad goblin chaos by default.
- Do not chase Phase20 or Ultra2 unless a fresh live failure points there.
- Treat the spine as current context, but verify against Git before edits.

## Latest fixed Bug Blaster

Latest validated state is the lowered-bottom slab placement persistence savepoint at `435cd1a`.
Live micro-test result: PASSED. Julia tested the 8:16 PM under-placement setup on clean HEAD `435cd1a`; the legally lowered survivor slab did not jump during under-placement.

## Recent relevant savepoints

- `save/real-lowered-bottom-slab-under-placement-persistence`
- `save/real-placed-lowered-bottom-slab-persistence` is historical only and should not be treated as final truth.

## Current next action

Save this documentation spine as its own doc-only savepoint, then use future live testing only for newly observed symptoms. Do not broad goblin-test by default.

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
