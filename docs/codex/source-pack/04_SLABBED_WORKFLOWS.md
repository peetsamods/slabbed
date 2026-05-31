# SLABBED Workflows

Status: consolidated replacement for old workflow notes, Windsurf codemaps, and repeated handoff procedures.
Updated: 2026-05-04.

## Workflow 0 — Universal Preflight

Run before any coding, build, proof, or savepoint.

```bash
git rev-parse --show-toplevel
git status -sb
git branch --show-current
git rev-parse --short HEAD
git tag --points-at HEAD
```

Hard stop unless root is the canonical active folder:

```text
/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate
```

Known archive/recovery folder only when explicitly needed:

```text
/Users/joolmac/CascadeProjects/Slabbed
```

Do not stage or stash unrelated files.

## Workflow 1 — One-Slice Regression Fix

Use for current core-contract defects.

1. Name the visible symptom in player terms.
2. Name the exact failing layer:
   - state authority
   - placement
   - collision
   - survival
   - model
   - outline
   - raycast
   - rescue
   - proof gap
3. Confirm the legal state being protected.
4. Write or identify a red proof when possible.
5. Patch only that layer.
6. Run compile and relevant gametest.
7. Live test if bug involves feel/targeting/ghosting.
8. Commit, annotated tag, push branch, push tag.
9. Verify tree.

Stop after two failed attempts. The next slice is audit-only.

## Workflow 2 — Savepoint and Push

Goal: make a recoverable milestone.

Required gate:

```bash
./gradlew --no-daemon compileJava compileGametestJava
./gradlew --no-daemon runClientGameTest --console plain
```

If release-related, also run release jar purity / hard-reference scans.

Steps:

1. Verify root, branch, HEAD, status.
2. Verify intended dirty files only.
3. Run required build/proof.
4. Stage intended files only.
5. Commit with concise message.
6. Create annotated tag: `save/<specific-desc>`.
7. Push branch.
8. Push tag.
9. Verify final status.
10. Report commit, tag, branch push, tag push, final tree.

Never call a Bug Blaster “Fixed” before this is complete.

## Workflow 3 — Goblin / Live Testing Loop

Use when live play shows ghosting, weird hitboxes, moving-up behavior, targeting theft, or no meaningful difference after an automated pass.

Rule: live testing is final authority for feel bugs.

Loop:

1. Record exact shape, held item, aim location, and observed wrong behavior.
2. Extract one repeated illegal state or contradiction.
3. Add/identify red proof for that one mechanism.
4. Fix one layer only.
5. Run compile/gametest.
6. Re-goblin the same shape.
7. Savepoint immediately after one confirmed live win.

Always include both commands in goblin instructions:

```bash
./gradlew --no-daemon runClientGameTest --console plain
```

Log pull template:

```bash
rg -n "GREEN|RED|FAIL|ERROR|SLABBED|LOWERED|PHASE|GOBLIN|RETARGET|dy=|owner|target|MISS|StackOverflow" build/run/clientGameTest/logs/latest.log build/run/clientGameTest/logs/*.log
```

Adjust log path if current Gradle run writes elsewhere.

## Workflow 4 — Add Category on Slabs

Status: paused until core building contract is stable.

Use only after explicit decision to resume category expansion.

Inputs:

- category name
- exact block/item list
- expected behavior notes
- placement methods
- survival methods
- visual path if needed

Hard constraints:

- one category per branch
- one or two commits max
- build before commit
- strict visual audit
- regression sweep if shared hook used

Branch:

```text
feat/<category>-on-slabs
```

Required lanes:

- full blocks
- bottom slab
- top slab
- neighbor update
- chunk unload/reload

Do not tag if visuals fail.

## Workflow 5 — Pop-Off Debugging

Use when something places and later breaks.

Goal: identify exact removal path.

Suspects:

- `canPlaceAt`
- `getStateForNeighborUpdate`
- `scheduledTick`
- `neighborUpdate`
- random tick / shape update

Proof must show:

1. placement succeeds
2. neighbor update occurs
3. survival predicate passes/fails as expected
4. unsupported case still fails

Forbidden:

- disabling survival globally
- broad slab solidity shortcut
- declaring success from placement alone

## Workflow 6 — Strict Visual Triad Audit

Use for any dy/lowered behavior.

Check:

- model height
- outline height
- raycast target
- click/interact result
- chunk reload stability

Authority:

```text
ClientDy.dyFor(world, pos, state)
```

Relevant surfaces:

- model/quads: `OffsetBlockStateModel` or wrapper
- outline: target block outline hook
- raycast: crosshair targeting / shape getter path

Stop if only one or two surfaces are updated.

## Workflow 7 — Rescue Expansion Gate

Use before adding or widening crosshair rescue.

Required proof:

1. bug is targeting ownership
2. model/outline/intended interaction disagree
3. dy/shape/placement cannot explain it
4. target has ownership signal
5. candidate is closer or otherwise legally higher priority
6. rescue does not steal unrelated targets

Known protected rescue targets:

- lowered block entities
- torch family
- bed family
- explicitly proven chain case
- anchored/lowered full-block ownership cases
- lowered slab visible-face cases

Known danger zones:

- crafting table no-rescue boundaries
- generic slab support
- generic lowered visuals
- packet/interact rewrites

## Workflow 8 — Release Artifact Closure

Use before public release or any jar purity claim.

Run normal proof first, then jar scans.

```bash
./gradlew --no-daemon clean build
./gradlew --no-daemon runClientGameTest --console plain
jar tf build/libs/slabbed-*.jar | rg "debug|dev|audit|gametest|test|proof|fixture|lab"
jdeps -recursive -verbose:class build/libs/slabbed-*.jar | rg "com\.slabbed\.(debug|dev)|SlabbedDebug|slabbed\.debug\.mixins"
```

A release jar must be closed over its runtime dependencies.

## Workflow 9 — Static Audit After Two Failed Attempts

If two fixes fail or guessing starts, stop implementation.

Audit must verify:

- fix exists in current branch
- correct root
- correct mixin JSON
- `fabric.mod.json` registers needed mixins
- produced jars contain expected classes/resources
- source-set wiring is correct
- release jars exclude dev/debug/test/proof tooling
- packaged bytecode does not hard-link excluded classes

Only after a concrete mismatch is found may implementation resume.

## Workflow 10 — `/claude` Handoff

When Julia types `/claude`, produce a single-pass Claude Opus handoff that:

- incorporates pasted instructions
- updates status as done / partial / not started
- states invariants and non-negotiables
- lists remaining work as ordered slices
- identifies likely systems/files without speculative code
- outputs a Windsurf-ready plan: skills, branches, tags, tests, stop conditions
- optimizes for first-pass correctness, minimal iteration, and zero guesswork

## Workflow 11 — `/cc` Handoff

When Julia types `/cc`, produce a concise copy-pasteable handoff block with:

- root / branch / HEAD / tag
- tree status
- latest savepoints
- current WIP and stop condition
- exact next action
- relevant bug blasters
- validation commands
- Notion/source update instructions if relevant

No extra chatter outside the block.
