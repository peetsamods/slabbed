# MC 1.20.1 Forge Model Loading Render Path Decision

## Scope

Book III docs/audit decision slice:

```text
forge-1.20.1-model-loading-render-path-decision
```

This slice classifies the Forge 1.20.1 model-loading and block-model render path
before Java implementation. It does not add model hooks, baked/model wrappers,
mixins, gametests, culling changes, behavior parity, Visual Triad proof, live
proof, release work, commit, tag, or push.

## Question

After the verified non-`Level` render-view lookup savepoint, decide which Forge
model-loading path should be used by the next implementation slice and what
proof is required before any model, culling, or Visual Triad claim.

## Inputs Inspected

Roadmap and canon:

- `AGENTS.md`
- `RULES.md`
- `SLABBED_SPINE.md`
- `HANDOFF.md`
- `docs/codex/03-visual-triad.md`
- `docs/porting/mc-1.20.1-forge-foundation.md`
- `docs/porting/mc-1.20.1-forge-regression-risk-checklist.md`
- `docs/porting/mc-1.20.1-forge-view-truth-order-decision.md`
- `docs/porting/mc-1.21.1-port-notes.md`
- `docs/porting/mc-1.21.1-proof-harness-strategy.md`

Current branch source:

- `build.gradle`
- `src/main/java/com/slabbed/Slabbed.java`
- `src/main/java/com/slabbed/anchor/SlabAnchorAttachment.java`
- `src/main/java/com/slabbed/util/SlabSupport.java`
- `src/main/java/com/slabbed/util/RuntimeDiagnostics.java`
- `src/client/java/com/slabbed/client/SlabbedClient.java`
- `src/client/java/com/slabbed/client/SlabbedModelLoadingPlugin.java`
- `src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java`

Local Forge 1.20.1 evidence:

- `.gradle/caches/forge_gradle/maven_downloader/net/minecraftforge/forge/1.20.1-47.4.20/forge-1.20.1-47.4.20-sources.jar`
- `.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.20_mapped_official_1.20.1/forge-1.20.1-47.4.20_mapped_official_1.20.1.jar`

## Forge 1.20.1 API Evidence

Forge `ModelEvent.ModifyBakingResult` is the primary model-wrapper hook:

- local Forge source `net/minecraftforge/client/event/ModelEvent.java`
- fired on the mod-specific event bus, client side only
- exposes a modifiable `Map<ResourceLocation, BakedModel>` through `getModels()`
- is explicitly the event to use when modifying the model registry before the
  `BlockModelShaper` caches it
- warning: it fires from a worker thread; handlers must not access arbitrary
  client state or the not-yet-current `ModelManager`

Forge `BakedModelWrapper` is the primary wrapper base:

- local Forge source `net/minecraftforge/client/model/BakedModelWrapper.java`
- delegates the vanilla and Forge-extended `BakedModel` methods
- exposes the Forge block-model context methods needed by Slabbed:
  `getModelData(BlockAndTintGetter, BlockPos, BlockState, ModelData)`,
  `getQuads(BlockState, Direction, RandomSource, ModelData, RenderType)`, and
  `getRenderTypes(BlockState, RandomSource, ModelData)`

Forge's mapped 1.20.1 classes confirm the render path:

- `BakedModel` extends `IForgeBakedModel`
- `IForgeBakedModel` provides Forge extension hooks for `ModelData`,
  `RenderType`, and `BlockAndTintGetter` model data
- `ModelBlockRenderer.tesselateBlock(...)` receives `BlockAndTintGetter`,
  `BakedModel`, `BlockState`, `BlockPos`, `ModelData`, and `RenderType`
- `RenderChunkRegion` implements `BlockAndTintGetter`, not `Level`, and exposes
  a `ModelDataManager`

This matches the preceding view-truth work: chunk render paths can present a
non-`Level` view, so model code must use the savepointed non-`Level` lookup
plumbing and must not assume direct `LevelChunk` capability access.

## Current Slabbed Source Findings

The donor client source still exists under `src/client/java`, but it is outside
the current Forge compile gate. The current `build.gradle` compiles only the
Book III scaffold subset under `src/main/java`.

`src/client/java/com/slabbed/client/SlabbedModelLoadingPlugin.java` already has
the correct conceptual shape for Forge:

- it registers a model-bake listener on the mod event bus
- it uses NeoForge `ModelEvent.ModifyBakingResult`
- it replaces baked models with `OffsetBlockStateModel`
- it guards double wrapping
- it also registers `ChainCeilingGeometry::registerAdditional`, which must not
  be pulled into the first Forge model-wrapper slice unless explicitly scoped

`src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java` is the
current block-model offset owner:

- it extends NeoForge `BakedModelWrapper<BakedModel>`
- it stores render context through NeoForge `ModelData`
- it applies model dy from `ClientDy.dyFor(view, pos, state)`
- it records default-off diagnostic samples for model dy and mesh bounds
- it also contains slab-height cull-face relocation logic

Because the class mixes three responsibilities, the next implementation must
keep the first Forge slice narrow:

1. port model registration and the block-model wrapper compile surface
2. preserve `ClientDy` as the model dy authority
3. avoid enabling or changing culling behavior in the same slice

## Decision

Chosen Forge model-loading path:

```text
ModelEvent.ModifyBakingResult on the Forge mod event bus
```

Chosen Forge block-model wrapper base:

```text
net.minecraftforge.client.model.BakedModelWrapper<BakedModel>
```

Chosen render context route:

```text
Forge ModelData / IForgeBakedModel context:
getModelData(BlockAndTintGetter, BlockPos, BlockState, ModelData)
getQuads(BlockState, Direction, RandomSource, ModelData, RenderType)
```

Rejected for the first implementation slice:

- `ModelEvent.RegisterGeometryLoaders` / `IGeometryLoader`: this is for custom
  JSON geometry, not the primary Slabbed block-model offset wrapper
- `ModelEvent.BakingCompleted`: this exposes the registry after caching and is
  not the correct mutation hook
- render-worker global client lookups as the primary model truth: the model path
  receives a `BlockAndTintGetter` and already has savepointed non-`Level` lookup
  plumbing
- culling changes: culling needs a fresh, explicit culling/render-path decision
  or RED; the first model wrapper slice may not smuggle cull-face relocation in
  as part of "just porting the model"
- block-entity/entity renderer offsets: these bypass the block-model wrapper and
  need separate coverage

## Culling and Render-Path Risk Classification

Risk checklist rows applicable to the next implementation:

- `SlabSupport` as single support/dy source
- Server capability vs client mirror vs non-`Level` render-view persistence
- Visual Triad
- Culling/render-path classification
- Source-truth fixture parity
- Performance and hot-path budget
- One-slice savepoint discipline

Culling classification:

```text
high risk, not part of the first Forge model-wrapper implementation
```

Reason:

`SlabSupport.isSlabHeightStepFace(...)` exists and is documented as the cull
relaxation predicate, while donor `OffsetBlockStateModel` contains step-cull
relocation logic. The Forge port has not yet produced a Forge-specific culling
RED, renderer-path proof, or Visual Triad proof. The next model-wrapper slice
must therefore port only the model dy wrapper scaffold and stop if culling logic
is inseparable from compile.

Performance classification:

```text
render hot path
```

The next implementation must avoid per-block system-property reads, reflection,
blocking chunk access, broad scans, packet spam, or direct server capability
reads from model/render worker contexts. Default-off diagnostics may exist only
behind cheap-first gates and may not become release-facing behavior.

## Visual Triad Proof Ladder

Compile proof is not model proof.

The first implementation may claim only:

```text
model-wrapper-scaffold-proven
```

Required proof ladder before stronger claims:

1. `compile-proven`: `./gradlew --no-daemon compileJava` passes with the Forge
   model wrapper included in the compile gate.
2. `registration-proven`: a default-off diagnostic or source-backed proof shows
   `ModelEvent.ModifyBakingResult` registered and wrapped the intended baked
   block models without double wrapping.
3. `render-view-proven`: a render-path row shows model code received a
   non-`Level` `RenderChunkRegion`/`BlockAndTintGetter` view and read the same
   anchor truth through the savepointed client mirror/non-`Level` lookup.
4. `block-model-triad-proven`: for a named fixture, one row ties the same
   position/state/support source to `clientDy`, `modelDy`, `outlineDy`,
   `targetDy`, target owner, and rendered-model evidence.
5. `family-proven`: repeat by declared family. Representative proof must not
   become a family claim.
6. `live-proven`: exact real profile, jar, world, action, screenshot/log
   evidence. Auto/dev runs remain support evidence only.

Block-entity and entity-renderer parity are separate proof families because they
do not pass through the block-model wrapper.

## Completed Implementation Slice

Recommended next implementation slice:

```text
forge-1.20.1-model-wrapper-registration-scaffold
```

Status:

```text
complete at 721e589f / save/forge-1-20-1-model-wrapper-scaffold
```

Scope:

- port only the Forge client model registration scaffold and the minimum
  `OffsetBlockStateModel` block-model wrapper surface needed to compile
- register `ModelEvent.ModifyBakingResult` on the Forge mod event bus
- use Forge `BakedModelWrapper`, `ModelData`, and `RenderType` APIs
- preserve `ClientDy.dyFor(...)` and existing `SlabSupport` authority
- keep server capability, client mirror, and non-`Level` lookup truth as the
  persistence path

Forbidden in that slice:

- culling behavior changes or cull-face relocation proof claims
- `ChainCeilingGeometry` custom geometry unless compile proves it is unavoidable;
  if unavoidable, stop and route a dedicated chain/custom-geometry slice
- mixin migration
- gametest/proof harness migration
- block-entity/entity renderer migration
- broad behavior parity
- Visual Triad or live proof claims
- release/upload/staging

Proof for that slice:

```text
./gradlew --no-daemon compileJava
git diff --check
```

Plus a report that explicitly says what remains unproven: rendered model dy,
culling, outline, raycast, block entities, entity renderers, gametests, live
behavior, and release readiness.

## Next Legal Route After Scaffold

Recommended next route:

```text
forge-1.20.1-model-wrapper-registration-proof-gate
```

Scope:

- prove the savepointed Forge `ModelEvent.ModifyBakingResult` listener registers
  on the Forge mod event bus
- prove the wrapper touches the intended baked block models without double
  wrapping
- use source-backed evidence or a default-off diagnostic route only
- keep culling, custom geometry, mixins, gametests, Visual Triad proof, live
  proof, behavior parity, and release work out of scope

This route may claim only `registration-proven` if its evidence is strong enough.
It may not claim rendered-model, culling, triad, live, behavior, or release proof.

Status:

```text
source-backed registration-proven; savepoint pending
```

Proof record:

```text
docs/porting/mc-1.20.1-forge-model-wrapper-registration-proof.md
```

Next route after this proof is savepointed:

```text
forge-1.20.1-model-wrapper-render-view-proof-gate
```

## Stop Conditions for Later Implementation

Stop partial if:

- Forge API use requires `RegisterGeometryLoaders` or custom geometry before the
  wrapper scaffold can compile
- the donor culling logic cannot be separated from the first wrapper compile
  boundary
- model code would duplicate `SlabSupport` or `ClientDy` logic
- model code needs direct `Level`/server capability access from a non-`Level`
  render view
- proof would require mixins, gametests, live proof, behavior parity, or product
  judgment
