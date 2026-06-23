# SLABBED_SPINE

This file is the current repo-local truth for Codex. Keep it short. Update it after every proof-confirmed or live-confirmed savepoint.

## Current active root

```text
/Users/joolmac/CascadeProjects/Slabbed-neoforge-1.21.1-port
```

This is a dedicated NeoForge 1.21.1 port worktree. Do not mutate these older Slabbed roots from this checkout:

```text
/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate
/Users/joolmac/CascadeProjects/Slabbed
/Users/joolmac/CascadeProjects/Slabbed-countered-compat-latest
```

Stop if the actual root does not match the intended current root.

## Current branch

```text
port/neoforge-1.21.1
```

## Current known-good savepoint

Commit:

```text
3dcab83c
```

Tag:

```text
none at port start
```

Pushed branch: no port push authorized
Pushed tag: no tag authorized

Port baseline created from the 1.21.1 beta.3 behavior line. No NeoForge
savepoint exists yet. Current NeoForge implementation proof is still unsaved WIP.

## Current proof-candidate state

```text
Broad unstaged NeoForge WIP across build/src plus repo-local truth docs.
No staged files. No tag at HEAD. No push authorized.
```

## Current objective

Hold the current NeoForge 1.21.1 functional WIP at an honest proof boundary
and decide savepoint readiness without overclaiming release readiness, source
parity, or clean server exit proof.

## Current blocker

Visible symptom:

```text
Repo-local truth and savepoint boundary lag the actual worktree.
```

Failing layer:

```text
proof/reporting boundary, not a new gameplay red
```

Protected invariant:

```text
Do not assume Fabric entrypoints work as NeoForge entrypoints. Use a
NeoForge-native @Mod shell and prove every retained Fabric API surface through
compile. Do not call this port file-for-file `26.2` complete or savepoint-final
unless the evidence supports that exact claim. Rendering remains a mandatory
later proof because the ghost-window fix depends on the FRAPI RenderContext/model
path and Sodium behavior.
```

Latest proof:

```text
2026-06-22/23 current-tree proof bundle:
- compile: tmp/neoforge-port-20260623/compile-p26-dripstone-chain-green2.log
  -> BUILD SUCCESSFUL
- client: tmp/neoforge-port-20260623/run-client-neoforge-offset-raycast-current-after-dripstone.log
  -> GREEN triad/cull proof with diagnosticsOnly=true and releaseReady=false
- server: tmp/neoforge-port-20260623/run-server-gametest-final-current.log
  and run-server-gametest-final-current-cleanexit.log
  -> All 71 required tests passed :)
- caveat: the fresh server rerun lingered after the green footer and was
  interrupted manually, so there is still no natural zero-exit server proof log
- path audit: source tag 8ba3414f touched many files not mirrored path-for-path
  here; treat full file-path parity as false/unproven, not as the current claim
```

Live status:

```text
No live NeoForge Minecraft proof has been run in this worktree. Existing proof
is compile/client/server-log only.
```

## NeoForge port bootstrap notes (2026-06-15)

Read first:

```text
HANDOFF.md
build.gradle
settings.gradle
gradle.properties
src/main/resources/fabric.mod.json
src/main/java/com/slabbed/Slabbed.java
src/client/java/com/slabbed/client/SlabbedClient.java
src/client/java/com/slabbed/client/SlabbedModelLoadingPlugin.java
src/main/java/com/slabbed/anchor/SlabAnchorAttachment.java
src/client/java/com/slabbed/client/SlabAnchorClientSync.java
```

Known starting facts:

```text
The worktree is still Fabric Loom/Yarn/Fabric-loader based. Main/client
entrypoints are Fabric ModInitializer/ClientModInitializer. The model hook uses
Fabric ModelLoadingPlugin. Anchor storage and client sync use Fabric attachment
and client-event APIs. Existing gametest wiring is Fabric-specific and must be
treated as unproven for NeoForge.
```

Stale descriptor warning:

```text
Top-level fabric.mod.json says 0.1.2-alpha, MIT, and minecraft 1.21.11. Do not
use it as the NeoForge port descriptor.
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
neoforge-savepoint-readiness-closure
```

Allowed files:

```text
HANDOFF.md, SLABBED_SPINE.md, proof logs under tmp/, and only the narrowest
proof-boundary commands needed to classify the current WIP honestly
```

Forbidden files:

```text
Original source checkout roots, release/version/changelog files, publishing
metadata, tags, pushes, deletion of evidence, live Minecraft, and broad
gameplay rewrites unless a fresh red proof reopens a concrete mechanism.
```

Required proof:

```text
Use the existing compile/client proof bundle under tmp/neoforge-port-20260623/.
If trying to strengthen the server lane, preserve the new full output under the
same evidence folder and report whether the wrapper exits naturally or lingers
after the green footer.
```

Stop condition:

```text
Wrong root/branch/HEAD, staged changes, a concrete uncovered functional gap in
the current WIP, proof regression, a return to bootstrap-only claims, or any
push/tag/publish action.
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
