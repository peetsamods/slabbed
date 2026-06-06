# SLABBED_SPINE

This file is the current repo-local truth for Codex. Keep it short. Update it after every proof-confirmed or live-confirmed savepoint.

## Current active root

```text
/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate
```

Known alternate active port root when explicitly working MC 26.1.2:

```text
/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2
```

Stop if the actual root does not match the intended current root.

## Current branch

```text
port/mc-1.21.1
```

## Current known-good savepoint

Commit:

```text
94a5643e
```

Tag:

```text
save/mc1211-decorative-hanger-followdown-live-confirmed
```

Pushed branch: yes
Pushed tag: yes

Live-confirmed 2026-06-03: decorative-hanger follow-down under lowered FULL blocks
AND lowered TOP slabs (SlabSupport.java; lantern/soul lantern/spore blossom/
hanging roots/pale hanging moss, chains excluded). Supersedes prior tagged+pushed
savepoint `eab0880a` (tag `save/mc1211-sbbs-underside-pre-manual-testing`), which
remains in history. Note: the working tree still carries the uncommitted
`LoweredSideSlabRetargeter.java` WIP (deliberately not in 94a5643e).

## Current objective

Close and verify the hanger follow-down savepoint, reconcile canon, then return
to the active raycast RED and SBBS manual-live proof gap without absorbing
unrelated retarget WIP.

## Current blocker

Visible symptom:

```text
lowered bottom side-extension underside trapdoor targeting RED:
TARGETING_DID_NOT_HIT_BOTTOM_SLAB_UNDERSIDE, targetFace=east
```

Failing layer:

```text
raycast
```

Protected invariant:

```text
Model, outline, raycast, placement, and post-settle behavior must agree on the
same live-equivalent dy and owner. No render/culling production patch is allowed
unless a fresh RED names culling and proves the active runtime render path first.
```

Latest proof:

```text
2026-06-06 closure proof passed from the branch-local Gradle dev client:
compileJava compileGametestJava plus focused SBBS runClientGameTest with
goblinOnly/sbbsFinalSlabTargetingRed/disableNetworkSynchronizer. Result GREEN,
failureLayer=NONE, lanternUnderDy=-0.5, chainLanternDy=0.0.
```

Live status:

```text
hanger local-live confirmed; active raycast RED and SBBS manual-live proof gap remain open
```

## Next legal slice

Type:

```text
savepoint-then-raycast-proof
```

Allowed files:

```text
SLABBED_SPINE.md, docs/codex/06-bug-blaster-case-law.md,
docs/codex/source-pack/02_SLABBED_ACTIVE_STATUS.md, proof logs under tmp/
```

Forbidden files:

```text
src/**, build.gradle, settings.gradle, gradle.properties, fabric.mod.json, *.mixins.json, release/version/changelog files
```

Required proof:

```text
Use the branch-local current-HEAD Gradle dev client only. Do not use
Applications/Minecraft, stale jars, wrong-head jars, or the vanilla launcher.
After hanger closure, first bug slice is raycast-only proof for
TARGETING_DID_NOT_HIT_BOTTOM_SLAB_UNDERSIDE.
```

Stop condition:

```text
Unexpected tracked dirt in the savepoint worktree, stale/wrong-head proof
source, production render/culling edit without fresh culling RED, or any attempt
to call the port release-ready before the release gate.
```

## Do not touch boundaries

- Do not touch culling unless fresh RED says culling.
- Do not promote Terrain Slabs into generic Slabbed support.
- Do not broaden rescue without RED proof.
- Do not move release tags unless explicitly running release correction.
- Do not use dirty/archive roots unless recovery is explicitly requested.
- Do not edit multiple layers in one slice.
