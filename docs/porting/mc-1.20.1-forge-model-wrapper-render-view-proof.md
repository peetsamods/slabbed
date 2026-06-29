# MC 1.20.1 Forge Model Wrapper Render-View Proof

## Scope

Book III proof gate:

```text
forge-1.20.1-model-wrapper-render-view-proof-gate
```

This is a source-backed proof of the Forge block-model wrapper render-view
context path. It does not add diagnostics, Java implementation, culling changes,
custom geometry, mixins, gametests, Visual Triad proof, rendered-model proof,
live proof, behavior parity, release work, commit, tag, or push.

Savepoint status:

```text
complete at a3f5bed4 / save/forge-1-20-1-model-wrapper-render-view-proof
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
- `docs/porting/mc-1.20.1-forge-model-wrapper-registration-proof.md`

Source evidence:

- `src/client/java/com/slabbed/client/SlabbedModelLoadingPlugin.java`
- `src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java`
- `src/client/java/com/slabbed/client/ClientDy.java`
- `src/main/java/com/slabbed/anchor/SlabAnchorAttachment.java`
- `src/main/java/com/slabbed/anchor/SlabAnchorClientMirror.java`
- `src/main/java/com/slabbed/anchor/SlabAnchorClientMirrorEvents.java`
- `src/main/java/com/slabbed/util/SlabSupport.java`

Existing Forge API decision evidence:

- `docs/porting/mc-1.20.1-forge-model-loading-render-path-decision.md`

Read-only limitation:

- The older local Forge cache paths recorded in the decision note were not present
  at their recorded `.gradle/caches/forge_gradle/...` locations in this checkout.
  This proof therefore uses the savepointed repo source plus the already-recorded
  Forge API decision evidence, not a fresh `javap` or local Forge class dump.

## Source-Backed Render-View Evidence

Forge render context route already chosen by the decision gate:

- Forge `BakedModelWrapper` exposes `getModelData(BlockAndTintGetter, BlockPos,
  BlockState, ModelData)`.
- Forge `BakedModelWrapper` exposes `getQuads(BlockState, Direction,
  RandomSource, ModelData, RenderType)`.
- The decision note records that Forge's mapped render path passes
  `BlockAndTintGetter`, `BakedModel`, `BlockState`, `BlockPos`, `ModelData`, and
  `RenderType`, and that `RenderChunkRegion` is a non-`Level`
  `BlockAndTintGetter`.

Savepointed Slabbed wrapper context path:

- `OffsetBlockStateModel` extends Forge `BakedModelWrapper<BakedModel>`.
- `OffsetBlockStateModel#getModelData(...)` receives `BlockAndTintGetter view`,
  `BlockPos pos`, `BlockState state`, and `ModelData modelData`.
- `getModelData(...)` stores `new RenderContextInfo(view, pos.immutable(),
  state)` in a Forge `ModelProperty` inside the returned `ModelData`.
- `OffsetBlockStateModel#getQuads(...)` receives the Forge `ModelData` and
  retrieves the stored `RenderContextInfo`.
- `getQuads(...)` passes that same `view`, `pos`, and `state` into
  `slabbed$forgeQuads(...)`.
- `slabbed$forgeQuads(...)` calls `ClientDy.dyFor(view, pos, state)`.
- `ClientDy.dyFor(...)` delegates non-carpet dy to
  `SlabSupport.getYOffset(world, pos, state)`, preserving `SlabSupport` as the dy
  authority.

Savepointed non-`Level` anchor truth path:

- `SlabAnchorAttachment.isAnchored(...)` checks `!(world instanceof Level)` and
  reads `clientAnchorLookup`.
- `SlabAnchorAttachment.isCompoundFullBlockAnchor(...)` checks non-`Level`
  views and reads `clientCompoundFullBlockAnchorLookup`.
- `SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(...)`,
  `isCompoundVisibleSideUpperSlab(...)`,
  `isCompoundVisibleSideDoubleSlab(...)`, and
  `isCompoundVisibleOwnerTopSlab(...)` check non-`Level` views and read their
  corresponding client lookup predicates.
- `SlabAnchorAttachment.isPersistentLoweredSlabCarrier(...)` and
  `isPersistentLoweredBottomSlabCarrierNonRecursive(...)` check non-`Level`
  views and read `clientLoweredSlabCarrierLookup`.
- `SlabAnchorClientMirrorEvents` installs all eight marker lookup predicates on
  client login and clears them on client logout.
- Each installed lookup calls `SlabAnchorClientMirror.contains(currentDimension(),
  marker, pos)`.
- `SlabAnchorClientMirror.contains(...)` reads the same dimension, chunk, marker,
  and packed-position buckets populated by the savepointed client mirror/network
  sync path.

## Risk Checklist Application

Applicable rows:

- `SlabSupport` as single support/dy source: unchanged. The wrapper route calls
  `ClientDy`, and `ClientDy` delegates non-carpet dy to `SlabSupport`.
- Server capability vs client mirror vs non-`Level` render-view persistence:
  source-backed proof shows the wrapper can pass a non-`Level`
  `BlockAndTintGetter` into the same anchor predicates that read the client mirror
  fallback lookups.
- Visual Triad: model route only. No outline or raycast proof is claimed.
- Culling/render-path classification: no culling code or cull-face relocation is
  changed or proven.
- Performance and hot-path budget: no new runtime work is added.
- One-slice savepoint discipline: this dirty proof note must stop for a separate
  savepoint closure if `git diff --check` is clean.

Rows not touched:

- Legal state grammar
- Placement, survival, neighbor, reload, collision, outline, raycast, or rescue
- Terrain Slabs compat law
- Gametest/proof harness migration
- Exact live profile, jar, and world proof
- Release jar and bytecode hygiene

## Verdict

The source-backed proof supports this limited claim:

```text
render-view-proven
```

This means:

- the savepointed Forge model wrapper carries the Forge `BlockAndTintGetter`
  render-view context from `getModelData(...)` into `getQuads(...)`
- model dy calculation receives that same view through `ClientDy.dyFor(...)`
- non-`Level` anchor lookups used by `SlabSupport` can read the savepointed client
  mirror predicates instead of direct `LevelChunk` capability state

## Not Proven

This proof does not prove:

- rendered-model pixels or visual lowering in a live frame
- culling or cull-face relocation
- Visual Triad agreement
- outline or raycast behavior
- block-entity or entity renderer parity
- gametest behavior
- live Minecraft behavior
- behavior parity
- release readiness

## Next Smallest Route

Immediate next route:

```text
forge-1.20.1-rendered-model-culling-triad-decision
```

After the post-render-view-proof roadmap alignment is savepointed, a docs/audit decision gate must classify rendered-model, culling, and Visual Triad proof order before any implementation. Culling, rendered-model/Visual Triad proof, block-entity
renderer coverage, gametest migration, live proof, and release work remain
separate routes requiring fresh authorization.
