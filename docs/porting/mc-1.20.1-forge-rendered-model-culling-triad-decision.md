# MC 1.20.1 Forge Rendered Model, Culling, and Visual Triad Decision

## Scope

Book III docs/audit decision slice:

```text
forge-1.20.1-rendered-model-culling-triad-decision
```

This slice classifies proof order after the Forge model-wrapper registration and
render-view proof gates. It does not add Java implementation, diagnostics,
source/build/resource/test changes, culling changes, cull-face relocation, custom
geometry, mixins, gametest migration, rendered-model proof execution, Visual
Triad proof execution, live proof, behavior parity, release work, commit, tag,
or push.

## Inputs Inspected

Roadmap and canon:

- `AGENTS.md`
- `RULES.md`
- `SLABBED_SPINE.md`
- `HANDOFF.md`
- `docs/codex/03-visual-triad.md`
- `docs/porting/mc-1.20.1-forge-foundation.md`
- `docs/porting/mc-1.20.1-forge-regression-risk-checklist.md`
- `docs/porting/mc-1.20.1-forge-model-loading-render-path-decision.md`
- `docs/porting/mc-1.20.1-forge-model-wrapper-render-view-proof.md`

## Proven Limits Preserved

Completed proof limits:

- `registration-proven`: the Forge `ModelEvent.ModifyBakingResult` registration
  path and wrapper/double-wrap guard are source-backed and savepointed.
- `render-view-proven`: the Forge wrapper carries the `BlockAndTintGetter`
  render-view context into model dy lookup, and the non-`Level` lookup path can
  read the savepointed client mirror truth.

These limits do not prove:

- rendered-model pixels or baked-quad output in a frame
- culling or cull-face behavior
- outline alignment
- raycast or targeting ownership
- full Visual Triad agreement
- block-entity or entity-renderer parity
- gametest, live behavior, behavior parity, or release readiness

## Decision

The next proof order is:

1. Rendered block-model evidence proof.
2. Culling/render-path classification only after rendered-model evidence, or
   earlier only if the rendered-model gate produces a culling-specific RED.
3. Full Visual Triad proof only after the model evidence and culling boundary are
   known.

Reason:

The current Forge branch proves that the wrapper is registered and can receive
render-view truth. It does not prove what the player sees. Slabbed's Visual Triad
law requires the model, outline, and raycast surfaces to agree, but the model
surface must first have real rendered-model evidence. Culling is adjacent to the
model path, but culling may not be changed just because the model wrapper exists.
A culling/cull-face change needs a fresh culling RED or a render-path
classification that names culling as the failing layer.

## Required Proof Order

### 1. Rendered block-model evidence proof

Next legal route:

```text
forge-1.20.1-rendered-block-model-evidence-proof-gate
```

Allowed claim if proven:

```text
rendered-block-model-evidence-proven
```

Required evidence:

- named fixture position/state/support source
- proof that the intended Forge block-model wrapper path is used for that
  fixture
- rendered-model evidence, such as baked-quad/mesh evidence tied to the wrapper
  path or exact screenshot/pixel evidence from the rendered frame
- explicit statement that outline, raycast, culling, full Visual Triad, live
  behavior, and release readiness are not proven by this gate

If source-backed evidence cannot honestly prove rendered-model output, the route
must stop and ask for the smallest default-off diagnostic plan. Do not add the
diagnostic in the decision route.

### 2. Culling/render-path classification

Culling work is not next by default.

Before any culling or cull-face relocation patch, the route must provide one of:

- a fresh culling RED that names culling as the failing layer, or
- a render-path classification showing the active issue is cull-context state
  leakage, delegate-state leakage, custom compat promotion, or another named
  culling/render-path failure.

Forbidden before that evidence:

- cull-face relocation
- broad culling changes
- custom geometry or `ChainCeilingGeometry` work
- treating a model-dy or render-view proof as culling proof

### 3. Full Visual Triad proof

Full Visual Triad proof remains separate from model proof.

A later triad route must capture the same position/state/support source across:

```text
model evidence
outlineDy
targetDy / final target owner
held item
face
hit vector
expected owner
actual owner
```

The triad route may not claim success from model-only, outline-only, raycast-only,
or proxy dy evidence. Live proof is still required for feel, targeting theft,
ghosting, moving-up behavior, or any player-visible contradiction.

## Prerequisites Before Any Java Patch

A later implementation/proof route must name these before editing Java:

- exact primary layer: model, culling, outline, raycast, or proof gap
- exact fixture and legal state being protected
- support source and dy authority, with `ClientDy`/`SlabSupport` unchanged unless
  explicitly authorized
- whether the route touches server capability, client mirror, non-`Level`
  render view, or none
- whether the route needs a default-off diagnostic, and why source-backed proof
  is insufficient
- proof command or evidence artifact required for the claim
- stop condition if the proof exposes culling, mixin, gametest, live, or behavior
  work outside the route

No Java patch is authorized if these inputs are ambiguous.

## Risk Checklist Application

Applicable rows:

- `SlabSupport` as single support/dy source: future model proof must keep dy
  routed through `ClientDy` and `SlabSupport` unless separately authorized.
- Server capability vs client mirror vs non-`Level` render-view persistence:
  already source-backed as `render-view-proven`; do not broaden the claim.
- Visual Triad: model, outline, and raycast must remain separate proof surfaces
  until a dedicated triad route captures all three together.
- Culling/render-path classification: culling requires a fresh RED or named
  render-path failure before any cull-face change.
- Source-truth fixture parity: rendered-model evidence must use a fixture whose
  support source and anchor/carrier truth match live source truth.
- Performance and hot-path budget: diagnostics must be default-off, cheap-first,
  and non-release-facing.
- One-slice savepoint discipline: this docs/audit decision must stop dirty for a
  separate savepoint closure if proof-clean.

Rows intentionally not touched:

- placement, survival, neighbor, reload, collision, outline, raycast, or rescue
  behavior
- Terrain Slabs compat law
- gametest/proof harness migration
- exact live profile, jar, and world proof
- release jar and bytecode hygiene

## Next Legal Route

If this decision slice is proof-clean and later savepointed, the next legal route
is:

```text
forge-1.20.1-rendered-block-model-evidence-proof-gate
```

That route is a proof gate for rendered block-model evidence only. It may not
change culling, claim full Visual Triad agreement, run live proof, broaden model
hooks, migrate mixins or gametests, or make behavior/release claims.

## Stop Conditions

Stop partial if:

- rendered-model output cannot be proven from source-backed evidence and a
  default-off diagnostic plan is needed
- culling appears necessary before a fresh culling RED or render-path
  classification exists
- the route would touch outline, raycast, placement, survival, collision, rescue,
  gametests, live proof, behavior parity, release, or source/build/resource/test
  files
- front-door docs disagree on the next route
