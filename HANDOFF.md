# HANDOFF - Forge 1.20.1 Book IV live/client triad RED capture setup (2026-06-29)

This is the current handoff for the first Slabbed Forge project foundation.
The older NeoForge handoff below is donor context only on this branch.

## Current dirty continuation - `/slabdy` Forge 1.20.1 wiring

- Current local HEAD: `bd23a736` /
  `save/forge-1-20-1-post-live-client-triad-route-alignment`.
- Active dirty source/build slice: `/slabdy` target-dy overlay ported onto
  Forge 1.20.1 client events and deliberately added to the compile allowlist.
- `/slabdy` remains default-off via `slabbed.targetDyOverlay=false`; it can be
  toggled in-game with `/slabdy` after a rebuilt/reobfuscated jar is staged.
- Current overlay fields: target registry id, target dy, lowered/raised/flush
  status, hit side, target half, dy source, hit Y, and immediate below block
  registry id/dy/source.
- Proof so far: `./gradlew --no-daemon compileJava compileGametestJava jar
  reobfJar` is green for the dirty slice; the reobfuscated jar was staged into
  the Modrinth `SLABBED FORGE 1.20.1` profile and loaded into
  `Minecraft* Forge 1.20.1 - Singleplayer`.
- `/slabdy` live status: the command toggles on and renders target rows in the
  Forge profile after `TargetDyOverlay` registration moved to
  `MinecraftForge.EVENT_BUS`. The current staged jar was loaded through the
  Modrinth profile's Worlds tab into
  `ServerLevel[Test Slabbed 1.20.1 Forge]`, avoiding unreliable Minecraft title
  menu navigation.
- Continuation evidence: direct slash/type/return command control is proven
  against the real profile log, and
  `/Users/joolmac/.codex/tmp/slabbed-forge-1.20.1-exact-fixture-topface-before-click.png`
  captures the exact support slab top-face target (`side=up`) while holding
  stone. Two bounded automated right-click attempts did not place the stone
  object, so this is a live-control/capture blocker, not a Slabbed behavior RED.
- Follow-up capture attempt at fresh coordinate `40 -60 40` also captured the
  support slab top-face target after a safe air-click focus-grab sequence, but
  the automated right-click still did not place the stone object. Treat this as
  repeated live input/capture blockage until a manual click or stronger input
  route is authorized.
- Input-control continuation: `/slabdy use` was added as a proof-helper
  subcommand that invokes Minecraft's normal client `useItemOn` path against
  the current block hit result and main hand. It compile/reobf-proves and the
  jar staged into the Modrinth profile hashes to
  `1b781b0a3afc803e1f92c10e4593269aa77538cde715e26894d6577a48818e35`.
- Latest exact fixture attempt after Worlds-tab relaunch:
  `/Users/joolmac/.codex/tmp/slabbed-forge-1.20.1-slabdy-use-topface-before.png`
  captures the top face of `70 -60 70 minecraft:stone_slab`, `dy=0.000`,
  `side=up`, while holding `minecraft:stone`; `/slabdy use` then reports
  `Slabbed target dy use: FAIL`, but the world does place
  `70 -59 70 minecraft:stone`.
- Latest live `/slabdy` row:
  `/Users/joolmac/.codex/tmp/slabbed-forge-1.20.1-slabdy-use-after-fail.png`
  shows target `70 -59 70 minecraft:stone`, `dy=-0.500`, `LOWERED`,
  `src=ANCHORED`, with below `70 -60 70 minecraft:stone_slab`, `dy=0.000`.
  Vanilla command proof also confirms
  `SLABDY_USE_PLACED_STONE` for block `70 -59 70 minecraft:stone`.
- Live `/slabdy row` classification after the row instrumentation was extended:
  top-down aim on the same existing fixture produced target owner
  `70 -59 70 minecraft:stone`, `dy=-0.500`, `src=ANCHORED`, while side-view
  lower aim produced target owner `70 -60 70 minecraft:stone_slab`, `dy=0.000`,
  and corrected side-center aim visually on the rendered stone produced
  `Slabbed target dy row: target is not a block`. Evidence screenshots:
  `/Users/joolmac/.codex/tmp/slabbed-forge-1.20.1-ordinary-stone70-pre-row.png`,
  `/Users/joolmac/.codex/tmp/slabbed-forge-1.20.1-ordinary-stone70-side-pre-row.png`,
  and
  `/Users/joolmac/.codex/tmp/slabbed-forge-1.20.1-ordinary-stone70-side-center-pre-row.png`.
  This is a narrow ordinary full-block side-raycast/targeting RED candidate,
  not a full Visual Triad proof or behavior parity claim.
- Dirty side-raycast fix status:
  `OffsetTargetingEvents` now refreshes the client `hitResult` through
  `SlabbedOffsetRaycast` on Forge client events, with entity-target protection
  and the existing `slabbed.offsetRaycast=false` A/B escape hatch. The offset
  helper now applies `SlabSupport` dy directly to tested shapes instead of
  depending on an inactive shape mixin. Proof jar staged to the Modrinth profile
  has SHA-256
  `d9acb29011750bad115a16db24e00fa9a3750d600a07c9a6804a017635d1f6ea`.
  After Worlds-tab relaunch, the corrected side-center row now targets
  `70 -59 70 minecraft:stone`, `dy=-0.500`, `src=ANCHORED`, face `south`, hit
  `70.500,-59.095,71.000`. This proves the narrow side-raycast owner RED green
  for the exact fixture only. The same row still reports outline bounds
  `[0.000,0.000,0.000 -> 1.000,1.000,1.000]`, so full Visual Triad and outline
  alignment remain unproven.
- Dirty outline alignment status:
  `OffsetOutlineEvents` now handles Forge `RenderHighlightEvent.Block` for
  nonzero-dy targets, shifts the selected block shape through `ClientDy`, and
  redraws it with `LevelRenderer.renderVoxelShape`. `TargetDyOverlay` now
  reports dy-shifted outline bounds in `/slabdy row`. Compile/server proof
  `./gradlew --no-daemon compileJava compileGametestJava runGameTestServer --console plain`
  is green; `./gradlew --no-daemon jar reobfJar` is green; staged profile jar
  SHA-256 is
  `b820c6b99a065faaedc1179ade667851528509ff701f68173b6281f742bcdfae`. After
  Worlds-tab relaunch, the corrected side-center row targets
  `70 -59 70 minecraft:stone`, `dy=-0.500`, `src=ANCHORED`, face `south`, and
  reports outline bounds
  `[0.000,-0.500,0.000 -> 1.000,0.500,1.000]` with
  `outlineMinY=-0.500`, `outlineMaxY=0.500`. Evidence screenshot:
  `/Users/joolmac/.codex/tmp/minecraft-live-control/mc-chat-after-20260629-124022-pid23867.png`.
  Clean context screenshot:
  `/Users/joolmac/.codex/tmp/slabbed-forge-1.20.1-outline-clean-after-row-2.png`.
  Follow-up model trace row: `/slabdy row` now reads
  `OffsetBlockStateModel.ModelDyOwnerSample`, arms the current target, and
  requests a one-block renderer rebuild so the next row can prove live rendered
  model participation for the same target. After another Worlds-tab relaunch,
  the staged jar SHA-256 is
  `cf851d11456ef7d1d76192948a2aa8be26b8186cd27c5b05d9af431f8f5441fa`, compile
  and exact server fixture proof remain green, and the live row screenshot
  `/Users/joolmac/.codex/tmp/minecraft-live-control/mc-chat-after-20260629-130329-pid65939.png`
  reports `modelTrace=seen`, `view=net.minecraft.client.renderer.chunk.RenderChunkRegion`,
  `appliedCalls=7`, `totalAppliedDy=-3.500`, `lastDy=-0.500`, with the same
  `target=70, -59, 70`, `dy=-0.500`, face `south`, and outline bounds
  `[0.000,-0.500,0.000 -> 1.000,0.500,1.000]`. This is exact-fixture live
  client triad evidence for model/outline/raycast agreement only; it is not
  culling proof, fence proof, lag root cause, survival/reload proof, broad
  behavior parity, release readiness, or Book IV completion.
- Current caveat: the helper return label is misleading (`FAIL` despite the
  state-changing placement and anchored row). Treat this as a proof-helper
  result-reporting mismatch, not as evidence that the exact fixture rejected
  placement. It still does not prove full Visual Triad agreement.
- Evidence note:
  `tmp/book-iv-slabdy-live-fixture-setup-partial.md`.
- Preserve existing untracked `tmp/` evidence and the untracked
  `src/main/resources/pack.mcmeta` live-profile resource-pack metadata fix.
- This is not a behavior fix, fence/raycast fix, lag root cause, culling proof,
  release proof, broad behavior parity proof, or Book IV completion. Its Visual
  Triad claim is limited to the exact live `minecraft:stone` over bottom slab
  fixture row described above.

## Repo / branch / HEAD

- Root: `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate`
- Branch: `codex/forge-1.20.1-backport-from-neoforge-042-beta2`
- HEAD: `613ff98e`
- Tag at HEAD: `save/forge-1-20-1-book-iv-live-client-triad-route`
- Target: Minecraft `1.20.1`, Forge
- Donor version: NeoForge `0.4.2-beta.2+1.21.1`
- Pushed branch: yes
- Code changes this slice: none; route/status docs savepointed

## Current state

Book III entrypoint/lifecycle scaffold and server-side anchor store capability
scaffold are compile-proven. The gameplay-facing `SlabAnchorAttachment` storage
facade now uses the Forge chunk capability store and is compile-proven at the
storage-facade savepoint. The current docs-only decision chooses
networking/client mirror sync before the non-`Level` render-view bridge, because
the render-view fallback has no truthful mirror to read until client sync exists.
The client mirror/network sync slice is compile-proven and savepointed. The
active implementation slice wires the existing non-`Level` fallback predicates
to the client mirror. It keeps server capability storage authoritative and does
not add model hooks, baked/model wrappers, mixins, gametests, behavior parity,
Visual Triad proof, live proof, or release work, and it is savepointed at
`c69d8665`. The post-render-lookup roadmap alignment is savepointed at
`709a50bd`. The model-loading/render-path decision is savepointed at
`68c2c330`. The model-wrapper registration scaffold is compile-proven and
savepointed at `721e589f`. The post-model-wrapper roadmap alignment is
savepointed at `139a15d2`. The source-backed model-wrapper registration proof is
`registration-proven` and savepointed at `d029de0c`. The post-registration-proof
roadmap alignment is savepointed at `022320b8`. The model-wrapper render-view
proof is savepointed at `a3f5bed4`, the post-render-view-proof roadmap alignment
is savepointed at `70e90d94`, the rendered-model/culling/triad decision is
savepointed at `23251ced`, and the rendered block-model evidence proof is
savepointed at `d435fcbc` /
`save/forge-1-20-1-rendered-block-model-evidence-proof`. The Book IV first route
selection is savepointed at `496d967d`; the ordinary full-block proof harness
audit is savepointed at `59baef9c`; and the exact Forge server fixture is green
and savepointed at `6f05c9e8` /
`save/forge-1-20-1-ordinary-full-block-anchor-behavior`. That server fixture
proves only `minecraft:stone` placed through Forge item placement over
`minecraft:stone_slab[type=bottom]`, with `anchored=true`, `dy=-0.500000`,
`supportDy=0.000000`, and `overlap=0.000000`. The client/runtime Visual Triad
proof-harness audit is savepointed at `192b0098` /
`save/forge-1-20-1-client-runtime-triad-harness-audit`. Julia later rejected
the automatic/dev-client `runClient` scaffold route for this Book IV Visual
Triad lane; it must not be savepointed, revived, or treated as useful proof.
The live-client triad route correction is savepointed at `613ff98e` /
`save/forge-1-20-1-book-iv-live-client-triad-route`. The active lane is now a
real client/live-authority triad RED/proof capture for the same fixture, before
any client harness implementation, culling, mixin migration, release, broad
gametest, behavior parity, or full Visual Triad claim. The branch is
intentionally based at the NeoForge beta.2 release tag because Julia requested
the Forge 1.20.1 backport use the latest NeoForge `0.4.2-beta.2+1.21.1` work as
the donor.

Existing untracked `tmp/` evidence folders are present in the worktree. Treat
them as pre-existing evidence noise and do not delete, stage, or rely on them
for Forge proof.

Live-proof rule:
Julia is going to bed and cannot provide manual live runs. When this port later
needs live validation, Codex must cruise-control the real Minecraft/Modrinth
profile/world and preserve exact jar/profile/window evidence. Auto/dev runs do
not count as live proof. For this Book IV Visual Triad lane, `runClient`,
`runServer`, `runGameTest`, `runServerGameTest`, `runClientGameTest`, and
similar automatic/dev-client paths must not be treated as useful proof or used
to drive the next route.

## What changed this slice

- Started the correct Forge foundation branch from `release/neoforge-1.21.1-0.4.2-beta.2`.
- Re-pointed `SLABBED_SPINE.md` at the Forge 1.20.1 foundation lane.
- Added `docs/porting/mc-1.20.1-forge-foundation.md` as the compact Project Canon/toolchain/contract map for this existing Slabbed repo.
- Locked Forge 1.20.1 toolchain truth from primary Forge Maven/MDK evidence.
- Deferred scaffold file patching because compile proof would require Book III Java loader API migration.
- Started the first Book III scaffold slice by converting build metadata and the minimal mod entrypoint only.
- Proved the first Forge scaffold with `./gradlew --no-daemon compileJava`.
- Ran `git diff --check` clean.
- Documented the attachment/persistence decision: replace NeoForge chunk data attachments with a Forge `LevelChunk` capability, not primary `SavedData`.
- Added the server-side Forge `LevelChunk` anchor store capability scaffold.
- Mirrored the donor's eight anchor marker buckets inside one per-chunk `SlabAnchorStore`.
- Wired capability type registration on the Forge mod event bus and `LevelChunk` capability attachment on the Forge event bus.
- Proved the server capability scaffold with `./gradlew --no-daemon compileJava`.
- Added `docs/porting/mc-1.20.1-forge-regression-risk-checklist.md` as the canon regression-risk/proof gate before later risky Forge slices.
- Closed the Forge foundation/risk-map savepoint at `a32cfa66` and pushed branch/tag.
- Migrated the gameplay-facing `SlabAnchorAttachment` storage facade from NeoForge attachment tokens to the Forge `LevelChunk` capability store.
- Kept the existing `SlabSupport` semantics unchanged and only brought its unchanged dependency chain into the temporary compile gate.
- Updated Terrain Slabs compat from NeoForge `ModList` to Forge `ModList` without broadening compat law.
- Proved the storage facade with `./gradlew --no-daemon compileJava`.
- Closed the storage-facade savepoint at `c7a57620` and pushed branch/tag.
- Decided the next Book III view-truth order: networking/client mirror sync before non-`Level` render-view bridge lookup.
- Added `docs/porting/mc-1.20.1-forge-view-truth-order-decision.md` as the decision record.
- Closed the view-truth order decision savepoint at `39d523cb` and pushed branch/tag.
- Started `forge-1.20.1-client-anchor-mirror-sync`.
- Added a Forge `anchor_sync` channel for complete per-marker chunk bucket snapshots.
- Added a dimension/chunk/marker-keyed client anchor mirror.
- Synced marker buckets on server mutation and chunk watch/unwatch.
- Routed client `Level` anchor reads through the client mirror while keeping non-`Level` fallback predicates unwired for the next slice.
- Closed the client anchor mirror sync savepoint at `07776aad` and pushed branch/tag.
- Started `forge-1.20.1-non-level-render-view-anchor-lookup`.
- Wired the existing non-`Level` anchor fallback predicates to the client mirror through the client-only mirror event bridge.
- Proved the non-`Level` lookup bridge with `./gradlew --no-daemon compileJava` and `git diff --check`.
- Closed the non-`Level` render-view anchor lookup savepoint at `c69d8665` and pushed branch/tag.
- Started docs-only `forge-1.20.1-post-render-lookup-roadmap-alignment` to remove stale active-slice pointers.
- Closed the post-render-lookup roadmap alignment savepoint at `709a50bd` and pushed branch/tag.
- Started docs-only `forge-1.20.1-model-loading-render-path-decision`.
- Closed the model-loading/render-path decision savepoint at `68c2c330` and pushed branch/tag.
- Started bounded implementation slice `forge-1.20.1-model-wrapper-registration-scaffold`.
- Closed the model-wrapper registration scaffold savepoint at `721e589f` and pushed branch/tag.
- Started docs-only `forge-1.20.1-post-model-wrapper-scaffold-roadmap-alignment`.
- Closed the post-model-wrapper roadmap alignment savepoint at `139a15d2` and pushed branch/tag.
- Started proof gate `forge-1.20.1-model-wrapper-registration-proof-gate`.
- Closed the model-wrapper registration proof savepoint at `d029de0c` and pushed branch/tag.
- Started docs-only `forge-1.20.1-post-registration-proof-roadmap-alignment`.
- Closed the post-registration-proof roadmap alignment savepoint at `022320b8` and pushed branch/tag.
- Completed proof gate `forge-1.20.1-post-render-view-proof-roadmap-alignment` and savepointed it at `a3f5bed4` / `save/forge-1-20-1-model-wrapper-render-view-proof`.
- Recorded source-backed `render-view-proven` evidence in `docs/porting/mc-1.20.1-forge-model-wrapper-render-view-proof.md`; rendered model behavior, culling, Visual Triad, live proof, and behavior parity remain unproven.
- Closed the rendered-model/culling/triad decision savepoint at `23251ced` and pushed branch/tag.
- Recorded `rendered-block-model-evidence-proven` for the named
  `minecraft:stone` ordinary full block anchored on a bottom slab fixture at
  `d435fcbc` / `save/forge-1-20-1-rendered-block-model-evidence-proof`.
- Started docs-only Book IV proof-route selection and chose the ordinary
  full-block-on-bottom-slab triad/behavior route as the first behavior proof
  lane, with execution blocked until a later proof-gap/harness slice.
- Closed the Book IV first route docs savepoint at `496d967d`.
- Recorded the ordinary full-block proof harness audit and closed its savepoint
  at `59baef9c`.
- Added the minimal Forge server gametest harness and exact server fixture
  behavior proof, savepointed at `6f05c9e8`.
- Started this docs-only client/runtime triad harness audit. It found no active
  Forge-compatible client/runtime Visual Triad proof harness for the saved
  `minecraft:stone` over bottom stone slab fixture.
- Closed the client-runtime triad harness audit savepoint at `192b0098` /
  `save/forge-1-20-1-client-runtime-triad-harness-audit`.
- Abandoned the later automatic/dev-client `runClient` scaffold route by Julia
  correction. The dirty scaffold was removed without savepoint; preserved
  `tmp/` evidence remains untracked evidence noise.
- Updated route/status docs so Book IV proceeds through a real client/live-
  authority Visual Triad RED/proof capture, not another automatic harness.
- Closed the live-client triad route correction savepoint at `613ff98e` /
  `save/forge-1-20-1-book-iv-live-client-triad-route`.
- Mixins, client runtime harness implementation, behavior parity, full Visual
  Triad proof, culling, release, and live-profile work remain untouched.

## Next owner actions

1. Next worker should start
   `forge-1.20.1-ordinary-full-block-outline-alignment-red`.
2. Do not start culling, cull-face relocation, mixin migration, behavior parity,
   live proof, release work, broad gametest migration, or extra fixtures from
   this route gate.

## Do not start yet

- Do not port Java behavior beyond the already savepointed exact server fixture path.
- Do not claim mixins, client runtime triad harness, broad gametests, behavior parity, Visual Triad proof, or live proof are migrated yet.
- Do not revive or savepoint the abandoned automatic/dev-client `runClient`
  scaffold route for this Book IV Visual Triad lane.
- Do not start later model loading, mixin, gametest, behavior parity, live-proof, or release slices without applying the Forge risk checklist.
- Do not migrate model loading, mixins, or gametests in this slice.
- Do not run release gates or stage jars.
- Do not claim NeoForge proof as Forge proof.
- Do not claim auto/dev runs as live proof.
- Do not touch other Slabbed roots.

## Proof references

Preflight foundation state:

```text
root: /Users/joolmac/CascadeProjects/Slabbed-phase19-integrate
branch: codex/forge-1.20.1-backport-from-neoforge-042-beta2
HEAD: 613ff98e
tag at HEAD: save/forge-1-20-1-book-iv-live-client-triad-route
```

Branch donor evidence:

```text
gradle.properties:
- minecraft_version=1.21.1
- neo_version=21.1.233
- mod_version=0.4.2-beta.2+1.21.1
- ffapi_version=0.116.7+2.2.4+1.21.1

build.gradle:
- net.neoforged.moddev 2.0.141
- JavaLanguageVersion.of(21)
- runServerGameTest exists as the NeoForge proof task
```

Forge 1.20.1 Book II evidence:

```text
Primary sources checked:
- https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml
- https://maven.minecraftforge.net/net/minecraftforge/gradle/ForgeGradle/maven-metadata.xml
- https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.20/forge-1.20.1-47.4.20-mdk.zip

Decisions:
- Forge: 1.20.1-47.4.20
- ForgeGradle: 6.0.54 exact pin; official MDK uses [6.0,6.2)
- Gradle wrapper: 8.8
- Java: 17
- mappings: official / 1.20.1
- descriptor: src/main/resources/META-INF/mods.toml
- mod loader: javafml
- loader range: [47,)
- Forge dependency range: [47,)
- Minecraft dependency range: [1.20.1,1.21)
- compile proof command: ./gradlew --no-daemon compileJava
```

Book III scaffold proof:

```text
./gradlew --no-daemon compileJava
-> BUILD SUCCESSFUL

git diff --check
-> clean
```

Book III server anchor capability proof:

```text
./gradlew --no-daemon compileJava
-> BUILD SUCCESSFUL

Warnings only:
- FMLJavaModLoadingContext.get() is deprecated/marked for removal in Forge 1.20.1 APIs.
- ResourceLocation(String,String) is deprecated/marked for removal in the mapped Minecraft API.
```

Current scaffold boundary:

```text
build.gradle intentionally compiles only src/main/java/com/slabbed/Slabbed.java
plus the new server-side anchor capability scaffold classes under
src/main/java/com/slabbed/anchor/.

This proves the Forge 1.20.1 entrypoint/lifecycle scaffold and the isolated
server-side chunk capability storage scaffold only. It does not prove the
gameplay-facing SlabAnchorAttachment facade, Slabbed behavior, client sync,
model loading, mixins, networking, gametests, or live behavior.
```

Book III storage facade proof:

```text
./gradlew --no-daemon compileJava
-> BUILD SUCCESSFUL

git diff --check
-> pending final rerun after this handoff update
```

Attachment/persistence decision:

```text
Decision doc:
docs/porting/mc-1.20.1-forge-attachment-persistence-decision.md

Chosen path:
Forge LevelChunk capability, one Slabbed anchor store per chunk.

Why:
The donor stores eight LongOpenHashSet marker sets per LevelChunk. Forge chunk
capabilities preserve chunk ownership, chunk save locality, and a direct future
port of SlabAnchorAttachment's internal storage plumbing better than a global
Dimension SavedData map.

Important caveat:
Forge capabilities do not replace NeoForge's synced data attachments. Client
sync/networking is a later explicit Book III slice, not part of server-side
persistence.
```

View-truth order decision:

```text
Decision doc:
docs/porting/mc-1.20.1-forge-view-truth-order-decision.md

Chosen order:
1. networking/client mirror sync
2. non-Level render-view bridge lookup

Why:
SlabAnchorAttachment already has non-Level fallback predicate readers for chunk
render views, but this Forge branch has no client mirror writer/sync surface
feeding those predicates. A render-view bridge before client sync would either
read no truth, duplicate server logic, or reach for unsafe client Level state
from a render-view context.
```

Client mirror/network sync contract:

```text
Active slice:
forge-1.20.1-client-anchor-mirror-sync, complete at 07776aad

Shape:
- server LevelChunk capability remains authoritative
- Forge SimpleChannel `slabbed:anchor_sync`
- one complete bucket snapshot packet per dimension/chunk/marker
- server mutation syncs the changed marker bucket to chunk watchers
- ChunkWatchEvent.Watch sends all eight marker buckets to the watching player
- ChunkWatchEvent.UnWatch sends empty buckets to clear the client's chunk mirror
- client mirror is keyed by dimension, chunk X/Z, and SlabAnchorMarker
- client Level queries read the client mirror
- non-Level render-view predicates remain unwired until the next slice
```

Non-Level render-view lookup contract:

```text
Active slice:
forge-1.20.1-non-level-render-view-anchor-lookup, complete at c69d8665

Shape:
- SlabAnchorAttachment keeps the existing non-Level fallback predicate fields
- SlabAnchorClientMirrorEvents installs those predicates on client login
- each predicate reads the savepointed SlabAnchorClientMirror for the current client dimension
- predicates are cleared on client logout
- no SlabSupport, dy, model loading, baked/model wrapper, mixin, gametest, behavior, or live-proof claim is made
```

Model-loading/render-path decision:

```text
Decision doc:
docs/porting/mc-1.20.1-forge-model-loading-render-path-decision.md

Chosen model-loading hook:
Forge ModelEvent.ModifyBakingResult on the mod event bus.

Chosen wrapper base:
net.minecraftforge.client.model.BakedModelWrapper<BakedModel>.

Chosen render context:
Forge ModelData / IForgeBakedModel path, where getModelData receives
BlockAndTintGetter + BlockPos and getQuads receives ModelData + RenderType.

Rejected for the first implementation:
- RegisterGeometryLoaders / IGeometryLoader as the primary route
- BakingCompleted as the mutation hook
- culling changes or cull-face relocation
- block-entity/entity-renderer parity claims
- Visual Triad or live-proof claims

Next legal implementation slice:
forge-1.20.1-model-wrapper-registration-scaffold

Completed:
savepointed at 721e589f / save/forge-1-20-1-model-wrapper-scaffold.

Registration proof:
source-backed registration-proven and savepointed at
d029de0c / save/forge-1-20-1-model-wrapper-registration-proof

Rendered block-model evidence proof:
source/bytecode-backed rendered-block-model-evidence-proven and savepointed at
d435fcbc / save/forge-1-20-1-rendered-block-model-evidence-proof

Next legal route after rendered block-model evidence proof is savepointed:
forge-1.20.1-book-iv-ordinary-full-block-anchor-proof-selection

Book IV route doc:
docs/porting/mc-1.20.1-forge-book-iv-first-proof-route.md

Registration proof doc:
docs/porting/mc-1.20.1-forge-model-wrapper-registration-proof.md

Render-view proof:
source-backed render-view-proven; savepoint complete at a3f5bed4 / save/forge-1-20-1-model-wrapper-render-view-proof

Render-view proof doc:
docs/porting/mc-1.20.1-forge-model-wrapper-render-view-proof.md
```

## Stop condition reached

Reached for the Book IV first proof-route selection, scoped to choosing the
ordinary full-block-on-bottom-slab triad/behavior route from the clean Book III
loader/API savepoint. Dirty proof-route docs must be savepointed after
`git diff --check`; Java, Gradle, mixin, gametest/proof harness migration,
culling, full Visual Triad execution, live, behavior, and release work remain
separate routes.

---

# Historical donor handoff - NeoForge 1.21.1 release-readiness candidate (2026-06-26)

> Current handoff for the dedicated NeoForge 1.21.1 port worktree. This section supersedes the older 2026-06-23 port-boundary handoff and the historical 2026-06-12 Fabric/release-line handoff below for any work in this checkout.

## Repo / branch / HEAD
- Root: `/Users/joolmac/CascadeProjects/Slabbed-neoforge-1.21.1-port`
- Branch: `port/neoforge-1.21.1`
- Current HEAD: `8f1819b0` (2026-06-28: version bump beta.2). Lineage on top of the
  `255f9c84` 0.4.2 parity candidate: `24aea038` render-trace hygiene fix → `8f1819b0` version bump.
- Version: `0.4.2-beta.2+1.21.1` (was `0.4.2-beta.1+26.2`; build metadata corrected to the target MC version).
- Tag at HEAD: none yet (push + release tag pending Julia's go).
- Created from: `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate` on `release/mc1.21.1-0.4.0-beta.3`
- Current tracked dirt is the release-readiness candidate: WYSIWYG/parity source changes, release jar hygiene in `build.gradle` and `SlabbedClient.java`, repo-local truth doc updates, and evidence logs under `tmp/`. Staged changes are not expected unless Julia explicitly authorizes a savepoint.
- Do not touch the original 1.21.1 checkout (`/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate`) or the canonical 1.21.11 Fabric source (`/Users/joolmac/CascadeProjects/Slabbed`).

## Current state (2026-06-28 — beta.2 render-perf cleanup)
- Builds on the WYSIWYG/parity work (`save/neoforge-1-21-1-wysiwyg-parity-green`) + the `255f9c84`
  0.4.2 parity candidate. Two new commits on top: `24aea038` (render-trace hygiene) + `8f1819b0` (version bump).
- **Lag investigation (CurseForge report "similar lag to Fabric 1.21.1"):** the exact Fabric bug
  (per-block `Class.forName` on a release-excluded class → CNFE storm) is **structurally absent** on
  NeoForge — `RuntimeDiagnostics` is a no-op stub, the render path does zero reflection, the excluded
  `ModelDyTranslateTraceBridge` has zero callers. Found + fixed the generalized cousin: always-on
  per-block work in `OffsetBlockStateModel.getQuads` gated AFTER it ran (`recordMc1211FullMeshBoundsSample`
  registry lookup + ~6 string allocs + atomic; the `measureBounds` per-vertex loop; 3–4 uncached
  `Boolean.getBoolean`). Fixed in `24aea038` (cache flags at class-load, gate `measureBounds`+sampler on
  `slabbed$fullMeshBoundsTraceArmed()`; `render.offset.trace` left live but reordered cheap-first because
  client gametests setProperty it). Zero behavior change; build green. Matters most under Sodium (bypasses
  the mixin path → `getQuads` is the sole per-block cost).
- **Tick spikes:** two Spark profiles (superflat + normal, decoded from bytebin protobuf) showed the
  **Server thread mostly idle/parked**; Slabbed self-time **0.02% superflat / 2.2% normal**, the 2.2%
  almost entirely the collision→getYOffset path (`BlockCollisionsLoweredAboveMixin` →
  `withHangingLoweredCollisionFromAbove` → `getYOffset`, fired per collision-block while moving). NOT a
  spike cause. Julia then couldn't reproduce the spikes → concluded transient worldgen/chunk-load hitches,
  not Slabbed. A cheap inline gate for the collision path is UNSOUND (geometric column-lowering means the
  answer needs the column scan); the only sound optimization is memoizing `getYOffset` (deferred — modest
  2.2%, not justified yet). If spikes recur: `/spark profiler --timeout 60 --only-ticks-over 50` during it.
- Active jar in the Modrinth profile (`SLABBED neoforge 1.21.1/mods/`): `slabbed-0.4.2-beta.2+1.21.1.jar`
  (render fix verified present). BEFORE/AFTER + original jars in `mods/_ab-backup/`.
- **PENDING (Julia's go):** push `port/neoforge-1.21.1` + tag the release (proposed
  `release/neoforge-1.21.1-0.4.2-beta.2`), then upload to Modrinth/CurseForge.

## Preflight for next chat
Run before any mutation:

```bash
git rev-parse --show-toplevel
git status -sb
git branch --show-current
git rev-parse --short HEAD
git tag --points-at HEAD
```

Expected:

```text
root: /Users/joolmac/CascadeProjects/Slabbed-neoforge-1.21.1-port
branch: port/neoforge-1.21.1
HEAD: b634da07 unless Julia creates a newer release-readiness savepoint
tracked dirt allowed at handoff start: the current release-readiness candidate files; no staged files
tags at HEAD: save/neoforge-1-21-1-wysiwyg-parity-green unless Julia creates a newer savepoint
```

Stop if the root is any of these: `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate`, `/Users/joolmac/CascadeProjects/Slabbed`, `/Users/joolmac/CascadeProjects/Slabbed-countered-compat-latest`, or any `.windsurf` worktree.

## Release-readiness proof bundle
- Compile: `tmp/neoforge-port-20260626/release-readiness/compile-release-hygiene-available.log` → `BUILD SUCCESSFUL`
- Build: `tmp/neoforge-port-20260626/release-readiness/clean-build-release-hygiene.log` → `BUILD SUCCESSFUL`
- Server GameTest: `tmp/neoforge-port-20260626/release-readiness/run-server-gametest-release-readiness.log` → `All 95 required tests passed :)`
- Server caveat: the wrapper lingered after the green footer and was interrupted, so this is not a natural zero-exit proof.
- Public jar actual leak scan: `tmp/neoforge-port-20260626/release-readiness/jar-actual-leak-scan-after-hygiene.txt` → zero lines
- Sources jar actual leak scan: `tmp/neoforge-port-20260626/release-readiness/sources-jar-actual-leak-scan-after-hygiene.txt` → zero lines
- Specific removed-class scan: `tmp/neoforge-port-20260626/release-readiness/specific-removed-classes-scan-after-hygiene.txt` → zero lines
- Bytecode hard-reference scan: `tmp/neoforge-port-20260626/release-readiness/jdeps-suspicious-refs-after-hygiene.txt` → zero lines
- `SlabbedClient` bytecode scan: `tmp/neoforge-port-20260626/release-readiness/javap-slabbedclient-suspicious-after-hygiene.txt` → zero lines
- Fabric/Quilt scans: `tmp/neoforge-port-20260626/release-readiness/*fabric-scan-after-hygiene.txt` → zero lines
- Style: `git diff --check` and `git diff --cached --check` → clean
- Post-hygiene live smoke:
  - `tmp/neoforge-port-20260626/release-readiness/live-post-hygiene-first-window.png`
  - `tmp/neoforge-port-20260626/release-readiness/live-post-hygiene-dripstone-fixture.png`
  - `tmp/neoforge-port-20260626/release-readiness/live-post-hygiene-profile-log-markers.txt`
  - `tmp/neoforge-port-20260626/release-readiness/live-post-hygiene-proof-debug-log-scan.txt` → zero lines
  - `tmp/neoforge-port-20260626/release-readiness/live-post-hygiene-error-scan.txt` → zero lines

## Important caveats
- This NeoForge Gradle task graph does not expose `runClientGameTest` or `runGameTest`; the available release proof route is `runServerGameTest`.
- Post-hygiene live smoke confirms the cleaned staged jar launches and renders existing dripstone fixtures in the intended Modrinth world. This is smoke coverage, not a full replay of every prior manual scenario.
- Do not claim public release complete until Julia authorizes the release/upload gate.

## Files inspected before stop
- `build.gradle`
- `settings.gradle`
- `gradle.properties`
- `fabric.mod.json`
- `src/main/resources/fabric.mod.json`
- `src/main/resources/slabbed.mixins.json`
- `src/main/resources/slabbed.debug.mixins.json`
- `src/client/resources/slabbed.client.mixins.json`
- `src/main/java/com/slabbed/Slabbed.java`
- `src/client/java/com/slabbed/client/SlabbedClient.java`
- `src/client/java/com/slabbed/client/SlabbedModelLoadingPlugin.java`
- `src/main/java/com/slabbed/anchor/SlabAnchorAttachment.java`
- `src/client/java/com/slabbed/client/SlabAnchorClientSync.java`
- `src/main/java/com/slabbed/dev/SlabbedDevCommands.java`
- `src/main/java/com/slabbed/dev/SlabbedLab.java`

## Key findings from inspection
- The project is still a Fabric Loom build: `fabric-loom`, Yarn mappings, Fabric loader, Fabric API, split client source sets, and Fabric gametest setup.
- Main and client entrypoints are Fabric-specific:
  - `com.slabbed.Slabbed` implements `net.fabricmc.api.ModInitializer`.
  - `com.slabbed.client.SlabbedClient` implements `net.fabricmc.api.ClientModInitializer`.
- The current render/model hook is FFAPI-shaped:
  - `SlabbedModelLoadingPlugin` uses `net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin` and `ModelModifier.WRAP_PHASE`.
- The anchor persistence layer depends on Fabric attachments:
  - `SlabAnchorAttachment` uses `AttachmentRegistry`, `AttachmentType`, and `AttachmentSyncPredicate`.
- Client rerender/sync logic depends on Fabric client events and attachment reads:
  - `ClientChunkEvents`, `ClientTickEvents`, and `chunk.getAttached(...)`.
- Dev commands depend on Fabric command registration and Fabric loader game-dir lookup.

## Suggested next slice
1. If Julia accepts this candidate, do a final diff-scope review.
2. Commit only the intended release-readiness source/doc files.
3. Create an annotated release-readiness/savepoint tag.
4. Push branch and tag if Julia authorizes.
5. Only after that, ask separately before any public Modrinth/GitHub release upload.

## Stop condition reached
Yes. Release-readiness candidate is staged and proven locally; public release/upload remains approval-gated.

---

# Historical handoff — 1.21.1 adversarial bug-hunt (2026-06-12)

> Running handoff for the autonomous adversarial session. Updated as commits land so context survives compaction. Companion to `SLABBED_SPINE.md`.

## Repo / branch / HEAD
- Root: `~/CascadeProjects/Slabbed-phase19-integrate`
- Branch: `release/mc1.21.1-0.4.0-beta.3`
- HEAD at session start: `9745adb8` (renderer-agnostic ghost-window cull fix)
- **Nothing is pushed.** Local commits only.

## Mandate (Julia, going to work, unavailable)
Build the messy multi-combined structures players actually build — many slabs + blocks, mixed lowered + vanilla objects — and hunt for: **face-culls, popping, merging-glitches, placement disobedience.** We want BUGS, not validation. Then do the same to get 1.21.11 port-ready. Update this handoff per commit.

## Method
1. Background worktree workflow `slabbed-1211-adversarial-hunt` (task w4d81ztnp): 4 adversarial agents author+run gametests trying to break dy/cull/freeze/merge invariants → skeptic verify → confirmed bugs.
2. Live: Modrinth `Slabbed 1.21.1` profile (Sodium + terrain_slabs). `Use`→`r` rebind for real placement. `/slabdy` overlay. Build structures, screenshot, hunt visual bugs.
3. Triage → write failing gametest (or live repro) → fix → re-verify → commit → update this handoff.

## Live-test rig (see LIVE-DRIVE-GUIDE.md)
- Modrinth Stop/Play; computer set to never sleep (live capture works).
- `Use` is bound to `r` (revert to `key.mouse.right` in profile options.txt line 91 when done; MC must be stopped to edit).
- Aim with `/tp @s x y z yaw pitch`; read `/slabdy` overlay; place with `r`; break with left-click.

## Bugs found (running log)
- **LOGIC SIDE (headless): NO real 1.21.1 bugs found.** 7 adversarial scenarios (compound-stack float, grounded-sink, cantilever 2-out consistency, stale-anchor-after-air, refill-same-cell corruption, geometric recompute after source break, adjacent-compound-column homogenization) all PASS on 1.21.1 @ `062f771f`. The background worktree hunt's "bugs" were against a STALE commit (`6da1643e` = a 1.21.11 line @ `0.3.0-beta.1`, NOT my branch — the linked-worktree isolation branched off the shared repo's HEAD) and do not reproduce on 1.21.1. Notable property confirmed: adjacent compound columns HOMOGENIZE to the same dy (flush, no step) — so the "cull-miss on compound step" concern is moot on 1.21.1 (the dy-difference predicate is also correct regardless).
- **VISUAL SIDE: NOT TESTED — live-blocked** (notifications, see above). Face-cull render / render-popping / merge-render / placement-disobedience / cull-fix visual confirm remain for a live session.

## Commits this session
- `062f771f` docs(1.21.1): handoff savepoint (live-blocker note)
- `b26c1007` test(1.21.1): 7 adversarial regression guards (37/37) — 1.21.1 dy/cull/freeze/merge logic robust, no real bugs

## Session conclusion (1.21.1)
- **Logic side: HARDENED + robust.** Hunted hard (7 adversarial scenarios); 1.21.1 held on all. No real logic bugs.
- **Visual side: NOT done — live-blocked** (recurring macOS notifications stole focus all session). The cull-fix visual confirm + render-cull/pop/merge + placement-disobedience hunt need a live session (enable a Focus/DND mode first).
- MC stopped; keybind reverted to `mouse.right`; hunt worktrees pruned.
- Then moved to 1.21.11 (separate repo `Slabbed-countered-compat-latest`): found+fixed the vanilla/compound ghost-window cull gap (`3a3f57e7`); found+deferred the vanilla vertical-compound FLOAT (`13e42ae3`, characterized). See that repo's HANDOFF.

## ✅ CRUISE UPDATE (2026-06-12 evening, LIVE — visual block resolved)
The "live blocked" notifications were macOS asking Julia to approve each computer-use action (she was at work). Solved by `request_access` ONE-TIME grant up front (Minecraft/Modrinth/Finder, full tier) — then smooth solo driving, no per-action prompts. **LESSON: on every live "Cruise", do `request_access` FIRST thing so the user isn't stuck approving.**

- **✅ 1.21.1 cull fix CONFIRMED working under Sodium.** Built a lowered-vs-flat step against open sky; the step seam renders SOLID stone from every window-exposing angle (SE + NW). Since Sodium *does* cull faces between adjacent opaque cubes, a solid seam proves the model-path `cullFace`-clearing IS honored by Sodium (the key uncertainty). Ghost-window gone. (Gold-standard kill-switch A/B not run — needs a rebuild/relaunch; evidence already strong.)
- **✅ Broad visual hunt — clean SAMPLE (not exhaustive).** One varied mixed structure (lowered stone wall + lowered glass + lantern + flat wall behind, on slabs): glass shows solid interior (no sky-window), lantern seated, step seam solid, no gaps/floats/z-fighting. No bugs. NOTE: only one structure — "build many" sweep + placement-disobedience (needs `r`-keybind) still open.

## State (end of session)
- 1.21.1: HEAD `1b71ccd9`, clean, nothing pushed. Logic hardened (37/37) + cull fix live-confirmed.
- MC: still RUNNING (New World scratch). Keybind reverted to `mouse.right`. Hunt worktrees pruned.
- **Open for next thread:** exhaustive broad visual hunt (more player structures), placement-disobedience live (re-enable `r`), the gold-standard cull kill-switch A/B, and the 1.21.11 cull-gap live confirm (needs compat jar in a TS profile).
- **UPDATE 2026-06-12 (opus):** the deferred 1.21.11 vanilla-compound FLOAT fix is **DONE + LIVE-CONFIRMED** on the compat repo (`Slabbed-countered-compat-latest` @ `21af4243`, new `loweredBottomSlabSupportDyForCompound` porting 1.21.1's `floorTorchBottomSlabSupportDy`; headless 40/40 + 5-lens adversarial-clean + live `/slabdy dy=-1.000` via the Modrinth `Slabbed+Terrain Slabs` profile). NOT pushed. See that repo's HANDOFF. Live-test route that works = Modrinth jar swap, NOT `runClient` (bare `java`, ungrantable to computer-use).

## Current decision update (2026-06-29)

The active concrete proof-selection slice is
`forge-1.20.1-book-iv-ordinary-full-block-anchor-proof-selection`.
It preserves `registration-proven`, `render-view-proven`, and
`rendered-block-model-evidence-proven` as limited source-backed claims and
chooses the first Book IV behavior route for the same named ordinary full-block
fixture.

Next legal route after this docs slice is savepointed:
`forge-1.20.1-ordinary-full-block-on-bottom-slab-triad-and-behavior-proof`,
starting as a proof-gap/harness audit before Java behavior patching.

## Book IV live `/slabdy` continuation update (2026-06-29)

Current Forge 1.20.1 savepoint baseline remains:

```text
root: /Users/joolmac/CascadeProjects/Slabbed-phase19-integrate
branch: codex/forge-1.20.1-backport-from-neoforge-042-beta2
HEAD: bd23a736
tag: save/forge-1-20-1-post-live-client-triad-route-alignment
```

Dirty local Book IV proof-helper work exists and is not savepoint-ready:

- `build.gradle`
- `src/client/java/com/slabbed/client/SlabbedClient.java`
- `src/client/java/com/slabbed/client/TargetDyOverlay.java`
- `src/main/resources/pack.mcmeta` (untracked)
- `tmp/book-iv-slabdy-live-fixture-setup-partial.md` (untracked evidence)
- `tmp/current-red.md` (untracked fence-raycast live RED classification)

`/slabdy` remains useful as a live target/dy overlay, but `/slabdy use` is not
an accepted proof route. Three helper variants were tried against the exact
ordinary full-block fixture:

1. direct `gameMode.useItemOn`
2. tick-delayed direct `gameMode.useItemOn`
3. queued `KeyMapping.click(client.options.keyUse.getKey())`

The latest staged profile jar after the queued key-use helper has SHA-256:

```text
0021dfef5856e327f1bce85749293f2ee7c6c541f62d4bb6510f42c22fff91b4
```

Latest exact fixture:

```text
support: 100 -60 100 minecraft:stone_slab[type=bottom]
object position: 100 -59 100
held item: minecraft:stone
expected dy if placed: -0.5
profile: SLABBED FORGE 1.20.1
world launch route: Modrinth profile Worlds tab -> Test Slabbed 1.20.1 Forge
window: Minecraft* Forge 1.20.1 - Singleplayer
```

Latest live chat evidence:

```text
Slabbed target dy use: queued keyUse target=100, -60, 100 face=up expectedPlace=100, -59, 100 before=minecraft:air
Slabbed target dy use: keyUse observed target=100, -60, 100 face=up expectedPlace=100, -59, 100 before=minecraft:air after=minecraft:air
```

Latest screenshots:

- `/Users/joolmac/.codex/tmp/slabbed-forge-1.20.1-slabdy-use-keyuse-before.png`
- `/Users/joolmac/.codex/tmp/slabbed-forge-1.20.1-slabdy-use-keyuse-after.png`

Interpretation:

- The helper now reports honestly, including `after=minecraft:air`.
- The direct, delayed-direct, and queued key-use helper routes did not
  reproduce placement in the current live setup.
- This is a proof-helper/live-control blocker, not evidence that Slabbed's
  legal placement path rejected ordinary `minecraft:stone` over a bottom slab.
- Do not savepoint the current `/slabdy use` scaffold as a Book IV proof win.
- Next ordinary-full-block live route should use Julia/manual right-click
  placement or a separately routed input-control audit before another helper
  patch.
- Julia's process lesson for this profile: when available, use the Modrinth
  profile `Worlds` tab to launch `Test Slabbed 1.20.1 Forge` directly instead
  of navigating from Minecraft's title screen.

Still unproven:

- full Visual Triad agreement
- outline dy
- raycast/target owner dy
- fence raycast behavior
- lag root cause
- culling/cull-face relocation
- broad behavior parity
- release readiness

## Book IV `/slabdy row` diagnostic update (2026-06-29)

After the failed `/slabdy use` helper attempts, the active safe progress was to
make `/slabdy` a better live classification tool. This is diagnostics only, not
a behavior fix.

New command:

```text
/slabdy row
```

The row reports:

```text
target owner, block id, dy, dy source, face, hit half, world hit vector,
local hit vector, outline box, outline min/max Y, held item, expected placement
cell, below block id, below dy, and below dy source
```

Proof:

```text
./gradlew --no-daemon compileJava compileGametestJava -> BUILD SUCCESSFUL
./gradlew --no-daemon jar reobfJar -> BUILD SUCCESSFUL
```

Profile jar staged for the next live capture:

```text
/Users/joolmac/Library/Application Support/ModrinthApp/profiles/SLABBED FORGE 1.20.1/mods/slabbed-0.4.2-beta.2+1.20.1-forge.jar
sha256: 704d7f1d2173ac88b6dd674c4e22e480fb488285f6dcb571f695ced3fcede345
```

Important:

- The currently running game must be relaunched before `/slabdy row` is
  available.
- Use the Modrinth profile `Worlds` tab and launch
  `Test Slabbed 1.20.1 Forge` directly when available.
- For the fence raycast RED, capture `/slabdy row` while aiming at the suspect
  fence target and preserve a screenshot with the overlay visible.
- This row is classification evidence only. It does not prove a fence raycast
  fix, full Visual Triad, lag root cause, culling, broad parity, or release
  readiness.

## Book IV fence raycast live row (2026-06-29)

The next live classification step was completed after relaunching through the
Modrinth profile `Worlds` tab.

Profile/world:

```text
profile: SLABBED FORGE 1.20.1
world: Test Slabbed 1.20.1 Forge
launch: Modrinth Worlds tab quick-play
window: Minecraft* Forge 1.20.1 - Singleplayer
staged jar sha256: 704d7f1d2173ac88b6dd674c4e22e480fb488285f6dcb571f695ced3fcede345
```

Evidence:

- relaunch screenshot:
  `/Users/joolmac/.codex/tmp/slabbed-forge-1.20.1-row-relaunch-first-window.png`
- `/slabdy row` availability proof:
  `/Users/joolmac/.codex/tmp/minecraft-live-control/mc-chat-after-20260629-115327-pid1831.png`
- fence pre-row screenshot:
  `/Users/joolmac/.codex/tmp/slabbed-forge-1.20.1-fence-topdown-pre-row.png`
- fence row screenshot:
  `/Users/joolmac/.codex/tmp/minecraft-live-control/mc-chat-after-20260629-115734-pid6475.png`

Fence fixture:

```text
support: 120 -60 120 minecraft:oak_slab[type=bottom]
target/object: 120 -59 120 minecraft:oak_fence
held item: minecraft:oak_fence
```

Captured row:

```text
Slabbed target dy row: [slabdy] target=120, -59, 120 minecraft:oak_fence |
  owner=120, -59, 120 * VANILLA * dy=-0.500 LOWERED * src=geometric |
  face=up * half=UPPER * hit=120.500,-58.000,120.500 * local=0.500,1.000,0.500 |
  outline=[0.375,0.000,0.375 -> 0.625,1.000,0.625] * outlineMinY=0.000 * outlineMaxY=1.000 |
  held=minecraft:oak_fence * expectedPlace=120, -58, 120 minecraft:air |
  below=120, -60, 120 minecraft:oak_slab * dy=0.000 * src=-
```

Classification:

- The live raycast owner is the fence at `120 -59 120`.
- `ClientDy` reports `dy=-0.500 LOWERED` from the `geometric` fallback.
- The outline remains vanilla-relative, `outlineMinY=0.000` and
  `outlineMaxY=1.000`.
- This is a Visual Triad mismatch/classification for the fence-family partial
  block, not ordinary full-block proof.
- Fences are outside the current authorized ordinary full-block family. Do not
  silently expand fence support while finishing the ordinary full-block Book IV
  lane.

Next route choice:

- `forge-1.20.1-fence-on-slab-raycast-category-decision` if Julia wants the
  fence RED handled now.
- Otherwise resume the ordinary full-block Book IV route with a manual/right-
  click live placement proof and use `/slabdy row` for the target row.

## Book IV ordinary full-block live row (2026-06-29)

The ordinary full-block route was resumed after the fence classification was
parked as a separate category decision.

Evidence:

- existing object proof:
  `ORDINARY_STONE_70_EXISTS` emitted for
  `70 -59 70 minecraft:stone`
- pre-row screenshot:
  `/Users/joolmac/.codex/tmp/slabbed-forge-1.20.1-ordinary-stone70-pre-row.png`
- row screenshot:
  `/Users/joolmac/.codex/tmp/minecraft-live-control/mc-chat-after-20260629-120311-pid21192.png`

Captured row:

```text
Slabbed target dy row: [slabdy] target=70, -59, 70 minecraft:stone |
  owner=70, -59, 70 * VANILLA * dy=-0.500 LOWERED * src=ANCHORED |
  face=up * half=UPPER * hit=70.500,-58.000,70.500 * local=0.500,1.000,0.500 |
  outline=[0.000,0.000,0.000 -> 1.000,1.000,1.000] * outlineMinY=0.000 * outlineMaxY=1.000 |
  held=minecraft:oak_fence * expectedPlace=70, -58, 70 minecraft:air |
  below=70, -60, 70 minecraft:stone_slab * dy=0.000 * src=-
```

Classification:

- The live raycast owner is the ordinary full block at `70 -59 70`.
- Client dy reports `dy=-0.500 LOWERED` from `src=ANCHORED`.
- The support below is `70 -60 70 minecraft:stone_slab`, `dy=0.000`.
- This is useful first-family Book IV live classification evidence.

Limitations:

- The object was already present from the earlier proof-helper route; this is
  not a fresh manual right-click placement proof.
- This row does not by itself prove rendered-model alignment, actual drawn
  outline alignment, culling, full Visual Triad, broad parity, or release
  readiness.

Next ordinary full-block proof route:

- Capture side-view live evidence for the same anchored `minecraft:stone`
  fixture with `/slabdy row`, visible rendered model, and selection outline in
  one view.
- Classify the next layer from that evidence: model, outline, or raycast.
