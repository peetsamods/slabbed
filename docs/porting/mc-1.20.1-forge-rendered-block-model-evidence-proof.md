# MC 1.20.1 Forge Rendered Block Model Evidence Proof

## Scope

Book III proof gate:

```text
forge-1.20.1-rendered-block-model-evidence-proof-gate
```

This slice answers one loader/API question: whether the Forge model wrapper path
can affect rendered block-model output for a named lowered fixture. It does not
add Java implementation, diagnostics, source/build/resource/test changes,
culling changes, cull-face relocation, custom geometry, mixins, gametest
migration, full Visual Triad proof, live proof, behavior parity, release work,
or next-slice implementation.

## Named Fixture

Fixture:

```text
minecraft:stone ordinary full block at a position carrying Slabbed ANCHOR_TYPE,
with a bottom slab directly below as the source support.
```

Expected dy:

```text
-0.5
```

Why this fixture is legal for this proof:

- It is the ordinary full-block-on-bottom-slab lane named in Slabbed legal state
  grammar.
- `ClientDy.dyFor(view, pos, state)` delegates non-carpet states to
  `SlabSupport.getYOffset(view, pos, state)`.
- `SlabSupport` returns `-0.5` for an anchored ordinary full block unless a
  special compound/contact branch applies.
- For non-`Level` render views, `SlabAnchorAttachment.isAnchored(...)` delegates
  to the savepointed client mirror lookup instead of direct chunk capability
  state.

## Evidence Inspected

Repo source:

- `build.gradle`
- `src/main/java/com/slabbed/Slabbed.java`
- `src/client/java/com/slabbed/client/SlabbedClient.java`
- `src/client/java/com/slabbed/client/SlabbedModelLoadingPlugin.java`
- `src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java`
- `src/client/java/com/slabbed/client/ClientDy.java`
- `src/main/java/com/slabbed/util/SlabSupport.java`
- `src/main/java/com/slabbed/anchor/SlabAnchorAttachment.java`

Local Forge/Minecraft bytecode evidence:

- `/Users/joolmac/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.20_mapped_official_1.20.1/forge-1.20.1-47.4.20_mapped_official_1.20.1.jar`
- `javap -classpath ... -c -p net.minecraft.client.renderer.block.ModelBlockRenderer`
- `javap -classpath ... -c -p net.minecraftforge.client.model.BakedModelWrapper`
- `javap -classpath ... -p net.minecraftforge.client.event.ModelEvent$ModifyBakingResult`

Canon/proof docs:

- `AGENTS.md`
- `RULES.md`
- `SLABBED_SPINE.md`
- `HANDOFF.md`
- `docs/porting/mc-1.20.1-forge-foundation.md`
- `docs/porting/mc-1.20.1-forge-regression-risk-checklist.md`
- `docs/porting/mc-1.20.1-forge-rendered-model-culling-triad-decision.md`

## Source-Backed Rendered Block-Model Chain

1. `Slabbed` initializes client setup through `DistExecutor` on the Forge client
   side and passes the Forge mod event bus to `SlabbedClient.init(...)`.
2. `SlabbedClient.init(...)` calls `SlabbedModelLoadingPlugin.init(modEventBus)`.
3. `SlabbedModelLoadingPlugin.init(...)` registers
   `SlabbedModelLoadingPlugin::modifyBakingResult` on the Forge mod event bus.
4. `modifyBakingResult(ModelEvent.ModifyBakingResult event)` replaces baked
   models in `event.getModels()` with `OffsetBlockStateModel`, while skipping
   nulls and already wrapped `OffsetBlockStateModel` instances.
5. `OffsetBlockStateModel` extends Forge
   `BakedModelWrapper<BakedModel>` and overrides the Forge extended
   `getQuads(BlockState, Direction, RandomSource, ModelData, RenderType)`
   signature.
6. Local Forge/Minecraft bytecode shows `ModelBlockRenderer` invokes that same
   Forge `BakedModel.getQuads(..., ModelData, RenderType)` signature during
   both AO and non-AO tessellation, then sends the returned `BakedQuad` list into
   face rendering and `VertexConsumer.putBulkData(...)`.
7. `OffsetBlockStateModel#getQuads(...)` obtains the delegate quads with
   `super.getQuads(...)`, recovers the render context from Forge `ModelData`,
   and passes the view/pos/state plus delegate quads into `slabbed$forgeQuads(...)`.
8. `slabbed$forgeQuads(...)` computes `dy` through `ClientDy.dyFor(view, pos,
   state)`. For the named fixture, that route reaches `SlabSupport.getYOffset`
   and the anchored full-block lane, yielding `-0.5`.
9. When `dy != 0.0f` and delegate quads are present,
   `slabbed$forgeQuads(...)` returns `translateQuads(baseQuads, dy)` instead of
   the delegate list.
10. `translateQuad(...)` clones each `BakedQuad` vertex array and adds `dy` to
    each vertex Y coordinate before constructing the replacement `BakedQuad`.
11. Therefore, for the named lowered fixture, the Forge renderer receives a
    changed `BakedQuad` list whose Y coordinates are lowered by `-0.5`.

## Verdict

The source/bytecode evidence supports this limited claim:

```text
rendered-block-model-evidence-proven
```

Meaning:

- the Forge wrapper participates in the block-model rendered-output path through
  `BakedModel#getQuads(..., ModelData, RenderType)`
- for a named lowered ordinary full-block fixture, the wrapper returns translated
  baked quads rather than merely recording dy or touching proxy state
- no additional default-off diagnostic is needed for this Book III loader/API
  proof gate

## Risk Checklist Application

Applicable rows:

- `SlabSupport` as single support/dy source: preserved. The model proof routes
  dy through `ClientDy` and `SlabSupport`.
- Server capability vs client mirror vs non-`Level` render-view persistence:
  preserved from `render-view-proven`; non-`Level` render views read client
  mirror fallback truth.
- Visual Triad: model surface only. Outline and raycast are not claimed.
- Culling/render-path classification: no culling change or proof claim.
- Source-truth fixture parity: fixture is named as ordinary stone anchored on a
  bottom slab with expected `dy=-0.5`.
- Performance and hot-path budget: no runtime diagnostics or source changes were
  added.
- One-slice savepoint discipline: proof-clean docs must be savepointed before
  the next gate.

Rows intentionally not touched:

- placement, survival, neighbor, reload, collision, outline, raycast, or rescue
  behavior
- Terrain Slabs compat law
- gametest/proof harness migration
- exact live profile, jar, and world proof
- release jar and bytecode hygiene

## Not Proven

This proof does not prove:

- culling or cull-face relocation
- outline behavior
- raycast or targeting ownership
- full Visual Triad agreement
- block-entity or entity-renderer parity
- gametest behavior
- live Minecraft/Modrinth behavior
- behavior parity
- release readiness

## Book III Completion Status

This proof closes the remaining block-model wrapper participation question for
Book III loader/API migration. After this proof is savepointed, the remaining
work appears to be Book IV behavior/proof work unless a later concrete slice
finds a loader/API gap:

- culling needs a fresh culling RED or named render-path failure before any
  cull-face change
- full Visual Triad proof needs model, outline, and raycast evidence for the
  same fixture
- live proof and behavior parity remain separate Book IV gates

## Next Legal Route

After this proof is savepointed, the next concrete route should be Book IV
behavior/proof planning or the first narrow behavior proof gate, not another
post-proof roadmap alignment, unless front-door docs truly conflict.
