# MC 1.20.1 Forge Model Wrapper Registration Proof

## Scope

Book III proof gate:

```text
forge-1.20.1-model-wrapper-registration-proof-gate
```

This is a source-backed registration proof. It does not add diagnostics, Java
implementation, culling changes, custom geometry, mixins, gametests, Visual
Triad proof, rendered-model proof, live proof, behavior parity, release work,
commit, tag, or push.

Savepoint status:

```text
complete at d029de0c / save/forge-1-20-1-model-wrapper-registration-proof
```

## Inputs Inspected

Roadmap and canon:

- `AGENTS.md`
- `RULES.md`
- `SLABBED_SPINE.md`
- `HANDOFF.md`
- `docs/porting/mc-1.20.1-forge-foundation.md`
- `docs/porting/mc-1.20.1-forge-regression-risk-checklist.md`
- `docs/porting/mc-1.20.1-forge-model-loading-render-path-decision.md`

Source and local Forge evidence:

- `build.gradle`
- `src/main/java/com/slabbed/Slabbed.java`
- `src/client/java/com/slabbed/client/SlabbedClient.java`
- `src/client/java/com/slabbed/client/SlabbedModelLoadingPlugin.java`
- `src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java`
- local Forge source `net/minecraftforge/client/event/ModelEvent.java`
- local Forge source `net/minecraftforge/client/model/BakedModelWrapper.java`

## Source-Backed Registration Evidence

Forge API facts:

- Forge `ModelEvent.ModifyBakingResult` is fired on the mod-specific event bus,
  client side only, before the `BlockModelShaper` caches the model registry.
- Forge `ModelEvent.ModifyBakingResult#getModels()` returns a modifiable
  `Map<ResourceLocation, BakedModel>`.
- Forge `BakedModelWrapper<T extends BakedModel>` is the supported wrapper base
  for delegating baked-model behavior.

Slabbed compile-gate facts:

- `build.gradle` includes `src/client/java` and includes `SlabbedClient`,
  `SlabbedModelLoadingPlugin`, and `OffsetBlockStateModel` in the current Forge
  compile gate.
- `Slabbed` obtains the Forge mod event bus from
  `FMLJavaModLoadingContext.get().getModEventBus()`.
- `Slabbed` calls `SlabbedClient.init(modEventBus)` only on `Dist.CLIENT`.
- `SlabbedClient.init(...)` calls `SlabbedModelLoadingPlugin.init(modEventBus)`.

Slabbed listener and wrapper facts:

- `SlabbedModelLoadingPlugin.init(...)` registers
  `SlabbedModelLoadingPlugin::modifyBakingResult` with the provided Forge mod
  event bus.
- `modifyBakingResult(ModelEvent.ModifyBakingResult event)` mutates
  `event.getModels()` with `replaceAll(SlabbedModelLoadingPlugin::wrapModel)`.
- The default-off proof-count branch uses the same `wrapModel(...)` path, so the
  counted diagnostic route and normal route share the same wrapping rule.
- `wrapModel(...)` returns the original model for `null` values and for models
  that are already `OffsetBlockStateModel`.
- `wrapModel(...)` wraps every other baked model by constructing
  `new OffsetBlockStateModel(model)`.
- `OffsetBlockStateModel` extends Forge `BakedModelWrapper<BakedModel>`.

## Risk Checklist Application

Applicable rows:

- `SlabSupport` as single support/dy source: registration only; no dy authority
  changed.
- Server capability vs client mirror vs non-`Level` render-view persistence:
  registration only; persistence truth remains the prior server capability,
  client mirror, and non-`Level` lookup path.
- Visual Triad: model registration surface is touched conceptually, but no
  model/outline/raycast alignment claim is made.
- Culling/render-path classification: no culling code or proof changed.
- Performance and hot-path budget: source proof only; no new runtime work.
- One-slice savepoint discipline: proof-clean dirty docs must stop for a
  separate savepoint closure.

Rows not touched:

- Legal state grammar
- Placement, survival, neighbor, or reload behavior
- Targeting/rescue ownership
- Terrain Slabs compat law
- Release jar and bytecode hygiene
- Exact live profile, jar, and world proof

## Verdict

The source-backed registration proof supports this limited claim:

```text
registration-proven
```

This means:

- the Forge client model listener is wired to the Forge mod event bus
- the listener receives the Forge `ModifyBakingResult` event type
- the listener mutates the modifiable baked-model registry map
- the wrapper path constructs Forge `BakedModelWrapper`-based
  `OffsetBlockStateModel` instances for not-yet-wrapped models
- double wrapping is prevented by the `model instanceof OffsetBlockStateModel`
  guard

## Not Proven

This proof does not prove:

- rendered-model dy
- culling or cull-face relocation
- Visual Triad agreement
- outline or raycast behavior
- block-entity or entity renderer parity
- gametest behavior
- live Minecraft behavior
- release readiness

## Next Smallest Route

Recommended next route after post-registration-proof roadmap alignment is
savepointed:

```text
forge-1.20.1-model-wrapper-render-view-proof-gate
```

That route should prove the model wrapper receives the expected Forge model-data
render context and can read the savepointed non-`Level` lookup truth. It must
still stop before culling, Visual Triad, behavior parity, live proof, and release
work unless separately authorized.
