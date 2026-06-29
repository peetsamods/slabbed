# SLABBED_SPINE

This file is the current repo-local truth for Codex. Keep it short. Update it after every proof-confirmed or live-confirmed savepoint.

## Current active root

```text
/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate
```

This checkout is now being used as the Forge 1.20.1 backport foundation branch.
Do not mutate these other Slabbed roots from this checkout unless Julia explicitly
opens that lane:

```text
/Users/joolmac/CascadeProjects/Slabbed
/Users/joolmac/CascadeProjects/Slabbed-countered-compat-latest
/Users/joolmac/CascadeProjects/Slabbed-neoforge-1.21.1-port
```

Stop if the actual root does not match the intended current root.

## Current branch

```text
codex/forge-1.20.1-backport-from-neoforge-042-beta2
```

## Current known-good savepoint

Commit:

```text
022320b8
```

Tag:

```text
save/forge-1-20-1-post-registration-proof-roadmap-alignment
```

Pushed branch: yes, through the Forge post-registration-proof roadmap alignment savepoint.
Pushed tag: yes, `save/forge-1-20-1-model-wrapper-render-view-proof`.

Donor release tag at parent foundation base:

```text
release/neoforge-1.21.1-0.4.2-beta.2
```

This branch intentionally starts from the NeoForge 1.21.1 beta.2 release tag,
because Julia requested the Forge 1.20.1 backport begin from the latest
`0.4.2-beta.2+1.21.1` NeoForge work.

## Current foundation state

```text
Book III entrypoint/lifecycle scaffold is compile-proven. The server-side Forge
LevelChunk anchor store capability scaffold is compile-proven. The
gameplay-facing SlabAnchorAttachment storage facade is compile-proven and
savepointed. The view-truth order decision is docs-proven: networking/client
mirror sync must come before the non-Level render-view bridge. The Forge
client anchor mirror/network sync slice is compile-proven and savepointed. The
non-Level render-view anchor lookup slice is compile-proven and savepointed. The
Forge model-loading/render-path decision is docs-proven and savepointed. The
Forge model-wrapper registration scaffold is compile-proven and savepointed. The
post-model-wrapper roadmap alignment is docs-proven and savepointed. The
model-wrapper registration proof gate is source-backed `registration-proven` and
savepointed. The post-registration-proof roadmap alignment is docs-proven and
savepointed. The model-wrapper render-view proof is now source-backed `render-view-proven` and savepointed at `a3f5bed4` / `save/forge-1-20-1-model-wrapper-render-view-proof`. The active Book III lane is post-render-view-proof roadmap alignment before any culling, rendered-model, or Visual Triad route. The Forge
regression-risk checklist remains required before render-view proof, culling,
mixin, gametest, behavior parity, live, or release slices. Mixins, gametest,
runtime behavior, release, and live-profile work remain out of scope until
separately authorized.
```

## Current objective

Set the first Forge project lane for Slabbed:

- target Minecraft: 1.20.1
- target loader: Forge
- donor source: NeoForge 1.21.1 `0.4.2-beta.2+1.21.1` release tag
- current work type: Book III post-render-view-proof roadmap alignment
- implementation status: persistence truth, model-render path decision, model-wrapper scaffold, source-backed registration proof, post-registration roadmap alignment, and source-backed render-view proof savepointed; rendered behavior, culling, and Visual Triad are not yet proven

## Current blocker

Visible symptom:

```text
Forge 1.20.1 scaffold is being isolated to entrypoint/lifecycle plus the
server-side anchor store capability, gameplay-facing storage facade, and client
mirror sync. The non-Level render-view anchor lookup bridge is now savepointed.
The Forge model-wrapper registration scaffold is now savepointed. The
source-backed registration proof gate is also savepointed. The active docs-only
lane proves only the model-wrapper render-view context path before any culling
change, mixin, gametest, Visual Triad proof, or behavior parity work.
```

Failing layer:

```text
proof/scaffold boundary, not a gameplay red
```

Protected invariant:

```text
The Forge backport must preserve Slabbed's named legal state grammar, Visual
Triad law, survival/placement/collision agreement, and release hygiene. Loader
porting is not permission to change product behavior.
```

Latest proof/foundation evidence:

```text
Preflight on this branch:
- root: /Users/joolmac/CascadeProjects/Slabbed-phase19-integrate
- branch: codex/forge-1.20.1-backport-from-neoforge-042-beta2
- HEAD: 2a3be274
- tag at HEAD: release/neoforge-1.21.1-0.4.2-beta.2

Donor build truth at HEAD:
- Minecraft 1.21.1
- NeoForge ModDev plugin 2.0.141
- NeoForge 21.1.233
- Java toolchain/release 21
- Forgified Fabric API 0.116.7+2.2.4+1.21.1
- mod_version 0.4.2-beta.2+1.21.1

Forge 1.20.1 toolchain truth:
- Forge 1.20.1-47.4.20
- ForgeGradle 6.0.54 exact pin; official MDK allows [6.0,6.2)
- Gradle wrapper 8.8
- Java 17
- Mojang official mappings 1.20.1
- descriptor: META-INF/mods.toml
- mod loader: javafml, loader range [47,)
- Book III scaffold proof: ./gradlew --no-daemon compileJava -> BUILD SUCCESSFUL
- Book III server anchor capability proof: ./gradlew --no-daemon compileJava -> BUILD SUCCESSFUL
- Book III storage facade proof: ./gradlew --no-daemon compileJava -> BUILD SUCCESSFUL
- style proof: git diff --check -> clean

Book III attachment/persistence decision:
- decision doc: docs/porting/mc-1.20.1-forge-attachment-persistence-decision.md
- chosen path: Forge LevelChunk capability
- not chosen as primary store: SavedData
- client sync: separate later slice, not part of server persistence decision
- server capability scaffold: one SlabAnchorStore per LevelChunk capability, eight long-set marker buckets
- gameplay-facing SlabAnchorAttachment storage facade: compile-proven and savepointed at c7a57620
- client mirror/network sync: compile-proven and savepointed at 07776aad
- non-Level render fallback lookup: compile-proven and savepointed at c69d8665
- view-truth order decision: client mirror/network sync before non-Level render-view bridge
- decision doc: docs/porting/mc-1.20.1-forge-view-truth-order-decision.md
- model-loading/render-path decision: docs-proven and savepointed at 68c2c330
- model-wrapper registration scaffold: compile-proven and savepointed at 721e589f
- post-model-wrapper roadmap alignment: docs-proven and savepointed at 139a15d2
- model-wrapper registration proof: source-backed registration-proven and
  savepointed at d029de0c
- post-registration-proof roadmap alignment: docs-proven and savepointed at
  a3f5bed4
- completed proof savepoint: save/forge-1-20-1-model-wrapper-render-view-proof
- active docs slice: forge-1.20.1-post-render-view-proof-roadmap-alignment
- model-wrapper render-view proof: source-backed render-view-proven, savepoint complete
- required risk gate: docs/porting/mc-1.20.1-forge-regression-risk-checklist.md
```

Live status:

```text
No Forge 1.20.1 live proof exists yet. NeoForge 1.21.1 proof does not prove the
Forge 1.20.1 backport. It is donor evidence only.

Julia cannot be the live runner while asleep. When a later slice reaches a live
gate, Codex must use cruise-control style live driving against the exact real
Minecraft/Modrinth profile, jar, and world. Auto/dev runs do not count as live
proof: runClient, runServer, runGameTest, runServerGameTest, runClientGameTest,
or other automated dev-client paths are technical support evidence only.
```

## Forge 1.20.1 bootstrap notes (2026-06-28)

Read first:

```text
HANDOFF.md
docs/porting/mc-1.20.1-forge-foundation.md
AGENTS.md
SLABBED_SPINE.md
build.gradle
settings.gradle
gradle.properties
src/main/resources/META-INF/neoforge.mods.toml
src/main/resources/META-INF/mods.toml
src/main/java/com/slabbed/Slabbed.java
src/client/java/com/slabbed/client/SlabbedClient.java
src/client/java/com/slabbed/client/SlabbedModelLoadingPlugin.java
src/main/java/com/slabbed/anchor/SlabAnchorAttachment.java
src/client/java/com/slabbed/client/SlabAnchorClientSync.java
```

Known starting facts:

```text
The donor release tag is NeoForge 1.21.1, not Forge 1.20.1. It uses ModDev,
Java 21, NeoForge metadata, and FFAPI as a compatibility bridge. Every loader,
mapping, entrypoint, model hook, attachment/capability, networking, and proof
harness decision must be re-verified for Forge 1.20.1 before code moves.
The current Forge scaffold intentionally compiles only com/slabbed/Slabbed.java
and the isolated server anchor capability classes until later slices port each
loader API surface deliberately.

Before any later networking/client sync, model loading, mixin, gametest,
behavior parity, live-proof, or release slice, read and apply:
docs/porting/mc-1.20.1-forge-regression-risk-checklist.md
```

Descriptor warning:

```text
Do not use the NeoForge descriptor, FFAPI dependency, or Java 21 toolchain as
Forge 1.20.1 truth. They are donor facts only.
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
forge-1.20.1-rendered-model-culling-triad-decision
```

Allowed files:

```text
Savepoint closure for the source-backed render-view proof note. Do not start
culling, Visual Triad, gametest, live, or behavior parity proof.
```

Forbidden files:

```text
Culling behavior changes, ChainCeilingGeometry/custom geometry, mixin migration,
gametest migration, block-entity/entity renderer migration, behavior parity,
Visual Triad proof claims, live proof, release metadata, Modrinth/GitHub
publishing, commits/tags/pushes, and unrelated docs/source/build/resource/test
edits unless a later route explicitly authorizes that scope.
```

Required proof:

```text
For the next savepoint slice, rerun git diff --check, stage only the intended
docs, commit, annotated tag, push branch/tag, and verify remote refs. Do not
claim culling, Visual Triad, behavior, or live proof from this gate.
```

Stop condition:

```text
Wrong root/branch/HEAD, staged changes, unexpected tracked dirty files, missing
Forge version truth, code edit before scaffold plan, or any attempt to claim
Forge runtime proof from NeoForge evidence or auto/dev runs.
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

## 2026-06-28 (Claude) — NeoForge lag investigation + beta.2 render-perf cleanup

CurseForge reporter hit "similar lag to Fabric 1.21.1." Fanned out a hunt: the EXACT Fabric bug
(per-block `Class.forName` on a release-excluded class → CNFE storm) is **structurally absent** on
NeoForge (`RuntimeDiagnostics` no-op stub, no render-path reflection, excluded bridge has zero callers).
Found the generalized cousin and fixed it (`24aea038`): always-on per-block work in
`OffsetBlockStateModel.getQuads` gated AFTER it ran — `recordMc1211FullMeshBoundsSample`
(registry lookup + ~6 string allocs + atomic) + the `measureBounds` per-vertex loop + 3–4 uncached
`Boolean.getBoolean`. Now flags are class-load cached and the sampler/measureBounds gate on
`slabbed$fullMeshBoundsTraceArmed()`; `render.offset.trace` stays a live read (client gametests
setProperty it) but reordered cheap-first. Zero behavior change, build green, biggest win under Sodium.

Two Spark profiles (decoded straight from bytebin protobuf): Server thread mostly idle; Slabbed
self-time 0.02% (superflat) / 2.2% (normal), the 2.2% almost all the collision→getYOffset path
(`BlockCollisionsLoweredAboveMixin` → `withHangingLoweredCollisionFromAbove` → `getYOffset`). Not a
spike cause. Julia couldn't reproduce the spikes afterward → transient worldgen/chunk-load hitches, not
Slabbed. A cheap inline gate for that collision path is UNSOUND (geometric column-lowering needs the
column scan); sound option is memoizing getYOffset (deferred). Bumped `0.4.2-beta.1+26.2` →
`0.4.2-beta.2+1.21.1` (`8f1819b0`; build metadata corrected to the target MC version). Jar staged in
the `SLABBED neoforge 1.21.1` profile. Push + release tag pending Julia's go.
