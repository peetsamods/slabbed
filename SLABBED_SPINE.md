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
b634da07
```

Tag:

```text
save/neoforge-1-21-1-wysiwyg-parity-green
```

Pushed branch: yes, `origin/port/neoforge-1.21.1` matches HEAD
Pushed tag: local tag present at HEAD; verify remote tag before relying on remote savepoint

This is the current NeoForge 1.21.1 proof-backed WYSIWYG parity savepoint. Later
local release-readiness hygiene changes may be dirty until committed.

## Current proof-candidate state

```text
Release-readiness candidate is dirty after the WYSIWYG savepoint:
- gameplay/parity source changes from the WYSIWYG slice
- release hygiene cleanup in build.gradle and SlabbedClient.java
- repo-local truth doc updates
- evidence logs under tmp/
No staged files expected unless Julia explicitly asks for a commit/savepoint.
```

## Current objective

Reach honest NeoForge 1.21.1 release-readiness for Slabbed
`0.4.2-beta.1+26.2`: clean public jar, correct Modrinth staged candidate, green
available GameTest proof, and no public upload without Julia's explicit release
authorization.

## Current blocker

Visible symptom:

```text
Standard Fabric-style `runClientGameTest` / `runGameTest` tasks do not exist in
this NeoForge Gradle task graph; use the available `runServerGameTest` gate and
report that deviation explicitly.
```

Failing layer:

```text
proof/release-boundary, not a new gameplay red
```

Protected invariant:

```text
Do not ship proof/debug/test tooling in the public jar. Release claims require
clean jar contents, clean bytecode hard-reference scans, compile/build proof,
available GameTest proof, and clear caveats for any unavailable or lingering
proof route.
```

Latest proof:

```text
2026-06-26 release-readiness hygiene bundle:
- compile: tmp/neoforge-port-20260626/release-readiness/compile-release-hygiene-available.log
  -> BUILD SUCCESSFUL
- build: tmp/neoforge-port-20260626/release-readiness/clean-build-release-hygiene.log
  -> BUILD SUCCESSFUL
- server GameTest: tmp/neoforge-port-20260626/release-readiness/run-server-gametest-release-readiness.log
  -> All 95 required tests passed :)
- caveat: the server GameTest wrapper lingered after the green footer and had to
  be interrupted, so this is not a natural zero-exit server proof.
- jar/source/jdeps/javap leak scans under tmp/neoforge-port-20260626/release-readiness/
  -> actual proof/debug/dev/test/trace leak scans are zero-line clean.
- Modrinth candidate staged:
  /Users/joolmac/Library/Application Support/ModrinthApp/profiles/SLABBED neoforge 1.21.1/mods/slabbed-0.4.2-beta.1+26.2.jar
  SHA-256: 4afb4d8ae9c508498db07e954d351cbcf697151d3f93f25f769b0e1f39b7186c
- post-hygiene live smoke:
  tmp/neoforge-port-20260626/release-readiness/live-post-hygiene-first-window.png
  tmp/neoforge-port-20260626/release-readiness/live-post-hygiene-dripstone-fixture.png
  -> intended Modrinth profile/world launched, staged jar loaded, dripstone visual
     smoke captured, proof/debug/error log scans clean.
```

Live status:

```text
Latest local live/cruise proof before release hygiene confirmed the major
reported WYSIWYG/dripstone/redstone-torch fixes in the Modrinth profile.
Post-hygiene smoke also confirms the cleaned staged jar launches in the intended
profile/world and renders existing dripstone fixtures without proof/debug/error
log leakage. This is a smoke proof, not a full manual replay of every scenario.
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
neoforge-release-readiness-closure
```

Allowed files:

```text
Only the dirty release-readiness candidate files already in scope, proof logs
under tmp/, and savepoint/release metadata if Julia explicitly authorizes the
next gate.
```

Forbidden files:

```text
Original source checkout roots, unrelated gameplay rewrites, deletion of
evidence, public upload/release, and any tag/push/commit not explicitly
authorized for the next savepoint.
```

Required proof:

```text
Before savepoint or public release, rerun/confirm final preflight, diff scope,
clean build, available GameTest, jar contents scan, jdeps scan, staged-jar SHA,
and any Julia-requested live Modrinth smoke.
```

Stop condition:

```text
Wrong root/branch/HEAD, staged changes, unexpected dirty files, proof
regression, public-jar leak, profile jar SHA mismatch, or any public release
action without Julia's explicit authorization.
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
