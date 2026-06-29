# MC 1.20.1 Forge View Truth Order Decision

## Scope

Book III decision slice:

```text
forge-1.20.1-view-truth-order-decision
```

This is a docs/audit decision only. No Java source, networking code,
render-view bridge code, model hooks, mixins, gametests, behavior parity, live
proof, release work, commit, tag, or push happened in this slice.

## Question

After the storage-facade savepoint, choose the next Book III order between:

1. networking/client mirror sync
2. non-`Level` render-view bridge lookup

## Inputs Inspected

Roadmap/front doors:

- `AGENTS.md`
- `RULES.md`
- `SLABBED_SPINE.md`
- `HANDOFF.md`
- `docs/porting/mc-1.20.1-forge-foundation.md`
- `docs/porting/mc-1.20.1-forge-regression-risk-checklist.md`
- `docs/porting/mc-1.20.1-forge-attachment-persistence-decision.md`

Source surfaces:

- `src/main/java/com/slabbed/anchor/SlabAnchorAttachment.java`
- `build.gradle`
- source search for client lookup writers/readers, networking/sync APIs, and
  render-view/model/dy surfaces under `src/main/java/com/slabbed`

## Source Findings

Current storage truth:

- server storage is a Forge `LevelChunk` capability with one
  `SlabAnchorStore` per chunk
- Forge capabilities do not replace NeoForge synced data attachments
- client mirror truth is not proven
- non-`Level` render-view anchor lookup is not proven

Current query shape:

- `SlabAnchorAttachment` already has client fallback predicate fields for the
  eight anchor marker buckets
- non-`Level` `BlockGetter` paths, such as chunk render views, use those
  fallback predicates instead of reading a `LevelChunk` capability directly
- the current source search found reads of those fallback predicates, but no
  current Forge writer/initializer for them
- the current source search found no Forge networking/channel sync surface for
  Slabbed anchor marker truth

Current compile scaffold:

- `build.gradle` still intentionally compiles a narrow Book III source subset
- model loading, mixins, client networking, and render implementation surfaces
  remain outside the current compile gate

## Risk Checklist Application

Applicable rows:

- Server capability vs client mirror vs render-view persistence
- Visual Triad
- Culling/render-path classification
- Performance and hot-path budget
- Source-truth fixture parity
- One-slice savepoint discipline

Rows not touched by this docs-only slice:

- Legal state grammar
- `SlabSupport` or dy authority
- Placement, survival, neighbor, or reload behavior
- Targeting/rescue ownership
- Terrain Slabs compat law
- Release jar and bytecode hygiene
- Exact live profile, jar, and world proof

Checklist verdict:

```text
The non-Level render-view bridge cannot be made truthful before a client mirror
exists. A bridge without mirror truth would either return false, duplicate
server logic locally, or reach for client Level state from a render-view context.
All three choices fail the persistence/view-truth row or create hot-path risk.
```

## Decision

Chosen order:

```text
1. networking/client mirror sync
2. non-Level render-view bridge lookup
```

The next implementation slice should build the smallest Forge client mirror and
network sync path for the existing eight marker buckets. It must not claim model
or render-view proof yet.

The client mirror design must still be shaped by the later render-view bridge:

- expose marker lookup by `BlockPos`
- preserve chunk/world ownership so stale world data cannot leak
- define chunk unload, disconnect, dimension-change, and removal behavior
- avoid per-block scans, packet spam, blocking chunk reads, or render-worker
  access to server-only truth
- leave model loading, culling changes, and behavior parity out of scope

Only after that mirror exists should the render-view bridge wire the existing
non-`Level` fallback predicates to the client mirror and prove that chunk render
views can read the same anchor/carrier truth as client `Level` queries.

## Next Smallest Slice

Recommended next implementation slice:

```text
complete: forge-1.20.1-client-anchor-mirror-sync
```

Active implementation route:

```text
complete at 07776aad / save/forge-1-20-1-client-anchor-mirror-sync
```

Boundaries for that slice:

- implement only the client mirror/network sync contract for the eight marker
  buckets
- no model loading
- no non-`Level` render bridge implementation beyond interfaces or acceptance
  constraints strictly needed by the mirror
- no mixin migration
- no gametest migration
- no behavior parity
- no live proof
- prove with `./gradlew --no-daemon compileJava` and `git diff --check`

Next slice after that:

```text
complete: forge-1.20.1-non-level-render-view-anchor-lookup
```

That later slice should wire the non-`Level` fallback predicates to the proven
client mirror and prove render-view lookup separately before model loading or
Visual Triad claims.

Active implementation route after client mirror savepoint:

```text
complete at c69d8665 / save/forge-1-20-1-non-level-render-view-anchor-lookup
```

Boundary:

- wire existing fallback predicates to the client mirror
- do not add model loading hooks
- do not add baked/model wrappers
- do not migrate mixins
- do not claim Visual Triad proof

Post-lookup next legal route:

```text
complete or active decision:
forge-1.20.1-model-loading-render-path-decision
```

This is a docs/audit decision gate, not Java implementation. It should classify
the Forge model-loading/render path, culling risk, and Visual Triad proof
boundary before any model hooks, baked/model wrappers, mixins, gametest
migration, behavior parity, or live-proof claims.

Decision record:

```text
docs/porting/mc-1.20.1-forge-model-loading-render-path-decision.md
```

## Proof Status

Proven in this decision slice:

- roadmap/front doors authorize the ordering decision after storage-facade
  savepoint
- current source has non-`Level` fallback readers but no Forge client mirror
  writer/sync surface
- current source has no Forge anchor networking surface
- safe order is client mirror/network sync before render-view bridge lookup

Not proven:

- model loading/render hooks
- Visual Triad agreement
- save/reload behavior
- live behavior
