# 02_SLABBED_ACTIVE_STATUS

Status snapshot for the current active Slabbed phase. Keep this file short and current.

## Current root

```text
/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate
```

## Current branch

```text
port/mc-1.21.1
```

## Current HEAD / savepoint

Behavior commit:

```text
94a5643e
```

Spine/docs head:

```text
20a5ac28
```

Closure tag:

```text
save/mc1211-decorative-hanger-followdown-live-confirmed
```

Pushed branch:

```text
yes
```

Pushed tag:

```text
yes
```

## Current phase

Decorative hanger follow-down is live-confirmed and savepoint-closed for
lowered FULL blocks and lowered TOP slabs on the current branch-local dev
client. This supersedes the older SBBS underside savepoint `eab0880a` as
current truth, but does not make the port release-ready.

## Pending WIP

- Lowered bottom side-extension underside trapdoor targeting RED:
  `TARGETING_DID_NOT_HIT_BOTTOM_SLAB_UNDERSIDE`, `targetFace=east`,
  `failureLayer=raycast`.
- Separate bottom side-extension slab authoring RED.
- SBBS manual-live proof gap: manual `runClient` rerun still needs to prove
  the slab-held lane live.
- Stale Proof Artifact Index gap: June hanger/SBBS closure artifacts still need
  their narrow index/canon sync after the savepoint is pushed.
- The workspace already contains unrelated tracked edits and untracked evidence; preserve them.

## Next action

First verify and push the hanger closure tag. Then resume the active raycast RED
before any production render/culling work. Manual/live proof must use the
branch-local current-HEAD Gradle dev client, never Applications/Minecraft, a
stale jar, a wrong-head jar, or the vanilla launcher.
