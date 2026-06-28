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
remains in history. Historical note: 94a5643e deliberately excluded the
then-uncommitted `LoweredSideSlabRetargeter.java` WIP; that later work is
tracked in commit `817f1cc0` and is not part of the hanger savepoint tag.

## Current objective

Keep the MC 1.21.1 port on the pushed hanger closure plus tracked side-carrier
commits, with the VS/lowered-full-block merge issue accepted as resolved by
Julia's 2026-06-10 manual branch-local check. The remaining pictured face
culling/shadow artifact is deferred for post-beta follow-up. Current work is
the beta.3 release/manual-Modrinth-test gate Julia explicitly requested.

## Current blocker

Visible symptom:

```text
deferred pictured face culling/shadow artifact beside VS/lowered full-block
checkerboard setup
```

Failing layer:

```text
render/culling surface; not placement/rescue/survival
```

Protected invariant:

```text
Model, outline, raycast, placement, and post-settle behavior must agree on the
same live-equivalent dy and owner. Julia accepted the former merge issue as
resolved; the pictured face culling/shadow artifact is explicitly deferred.
No render/culling production patch is allowed unless a fresh RED names culling
and proves the active runtime render path first.
```

Latest proof:

```text
2026-06-10 release hygiene and beta.2 version proof passed on port/mc-1.21.1.
Julia later reported the merge issue resolved and requested beta.3 parity with
the MC 1.21.11 port, with the pictured culling/shadow issue deferred.
```

Live status:

```text
hanger local-live confirmed; merge issue resolved by Julia; pictured
render/culling artifact deferred
```

## Deferred visual/culling REDs

### MC 1.21.1 VS/lowered full-block adjacency

Visible symptom:

```text
Branch-local manual screenshots on 2026-06-10 originally showed
VS/lowered-full-block checkerboards and full-block-adjacent setups visually
merging. Julia later accepted the merge issue as resolved. The remaining
pictured issue is the culled/missing face / shadow artifact at the lowered
full-block / vanilla slab boundary.
```

Failing layer:

```text
render/culling surface, not placement/rescue/survival
```

Status:

```text
Merge issue resolved by Julia for MC 1.21.1. The remaining pictured
culling/shadow artifact is deferred. The render-worker outline-shape experiment
in SlabSupportStateMixin.java was reversed and must not be restored unless a
fresh culling-specific RED proves the active runtime render path and names the
exact hook.
```

Artifacts:

```text
tmp/mc1211-vbvs-shadow-checkerboard/
tmp/mc1211-vbvs-shadow-audit-20260610/
tmp/mc1211-vbvs-vsvb-merge-red-20260610/
tmp/mc1211-vbvs-vsvb-merge-red-20260610-rerun/
tmp/mc1211-vbvs-vsvb-merge-red-20260610-matrix-red/
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

- Do not touch culling or shadow/render-worker shape unless fresh RED says culling.
- Do not promote Terrain Slabs into generic Slabbed support.
- Do not broaden rescue without RED proof.
- Do not move release tags unless explicitly running release correction.
- Do not use dirty/archive roots unless recovery is explicitly requested.
- Do not edit multiple layers in one slice.

## 2026-06-12 (Claude, autonomous adversarial + live Cruise)

Branch `release/mc1.21.1-0.4.0-beta.3`, HEAD `1b71ccd9` (clean, NOT pushed). **1.21.1 LOGIC HARDENED:** hunted hard with 7 adversarial gametests (`b26c1007`, in SlabbedLabFixtureTest — compound-stack float, grounded-sink, cantilever 2-out consistency, stale-anchor, refill corruption, geometric recompute, adjacent-compound homogenization); ALL PASS (37/37). No real logic bugs. **CULL FIX LIVE-CONFIRMED under Sodium:** lowered-vs-flat step seam renders SOLID from every window-exposing angle → the model-path `cullFace`-clear engages under Sodium; ghost-window gone. Broad visual hunt = one clean varied structure (more structures + placement-disobedience still open). GOTCHA: this repo is a linked worktree sharing `Slabbed/.git`, so `isolation:'worktree'` agents branched off the SHARED HEAD (`6da1643e`, a 1.21.11 commit) not this branch — verify worktree HEAD or run gametests in-repo. CRUISE GOTCHA: do `request_access` FIRST so the user isn't stuck approving each action. Remaining to release-ready (next thread, opus ultracode): exhaustive visual sweep, placement live, gold-standard cull A/B, then 1.21.11 (see that repo). See HANDOFF.md Cruise update.
