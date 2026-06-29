# MC 1.20.1 Forge Regression Risk Checklist

## Scope

Book III canon gate:

```text
forge-1.20.1-canon-regression-risk-map
```

This checklist is required before any later Forge 1.20.1 slice that touches:

- networking or client sync
- model loading or rendering hooks
- mixins
- gametest or proof harness migration
- placement, survival, collision, outline, raycast, rescue, or behavior parity
- release readiness

It is not permission to make those changes. It is the risk map that must be
answered before a later slice is authorized.

## Authority Inputs

Active authority:

- `AGENTS.md`
- `RULES.md`
- `SLABBED_SPINE.md`
- `HANDOFF.md`
- `docs/codex/00-authority-order.md`
- `docs/codex/01-canon-law-purpose.md`
- `docs/codex/02-legal-state-grammar.md`
- `docs/codex/03-visual-triad.md`
- `docs/codex/04-slice-contracts.md`
- `docs/codex/05-preflight-savepoint.md`
- `docs/codex/06-bug-blaster-case-law.md`
- `docs/codex/07-live-test-log-recipes.md`
- `docs/codex/08-compat-contracts.md`
- `docs/codex/09-release-gate.md`
- `docs/codex/10-troubleshooting-when-stuck.md`
- `docs/porting/mc-1.20.1-forge-foundation.md`
- `docs/porting/mc-1.20.1-forge-attachment-persistence-decision.md`

Historical lessons used as case-law inputs:

- `docs/beta35-live-object-coverage-false-green.md`
- `docs/beta4-live-placement-authoring-proof-gap.md`
- `docs/beta35-floor-torch-sbsbs-source-truth-fix.md`
- `docs/beta35-release-readiness-audit.md`
- `docs/porting/mc-1.21.1-proof-harness-strategy.md`

## Current Forge Port Status

Proven on this Forge branch:

- Forge 1.20.1 toolchain truth
- entrypoint/lifecycle compile scaffold
- server-side `LevelChunk` anchor store capability scaffold
- gameplay-facing `SlabAnchorAttachment` storage facade
- view-truth order decision: client mirror/network sync before non-`Level` render-view bridge lookup
- `git diff --check` for completed scaffold/docs slices

Not proven on this Forge branch:

- proof-clean client chunk mirror or networking sync
- non-`Level` render-view anchor lookup
- model loading/render hooks
- mixins
- gametest/proof harness migration
- placement, survival, collision, outline, raycast, rescue, or behavior parity
- live Minecraft/Modrinth profile behavior
- release jar hygiene

## Required Slice Header

Every later risky Forge slice must begin by filling this header:

```text
Slice:
Primary layer:
Legal state being protected:
SlabSupport or dy authority touched: yes/no
Persistence path touched: server capability / client mirror / non-World render-view / none
Visual Triad surface touched: model / outline / raycast / none
Placement/survival/reload touched: placement / survival / neighbor / reload / none
Rescue/targeting touched: yes/no
Compat family touched:
Proof family scope:
Live proof required: yes/no
Release-facing risk: yes/no
Savepoint plan:
Stop condition:
```

If a row cannot be answered, the slice is audit-only.

## Regression Risk Map

| Risk family | Required gate before patching | Stop if |
|---|---|---|
| Legal state grammar | Name the exact legal Slabbed or vanilla state. Include block/state, expected dy, support source, persistence rule, placement rule, survival rule, triad rule, and live-feel expectation. | The state is unnamed, inferred from visuals only, or depends on downstream rescue to become legal. |
| `SlabSupport` as single support/dy source | State whether the slice touches `SlabSupport`, `ClientDy`, or any dy caller. All new support semantics must route through the designated authority. | A mixin or renderer invents local slab support, dy, lane, or eligibility logic. |
| Server capability vs client mirror vs render-view persistence | Classify the persistence surface as server chunk capability, client mirror, or non-`Level` render view. Prove each view that needs anchor/carrier truth can read it. | A proof reads real `Level`/`World` while the live render path uses `ChunkRendererRegion` or another non-`World` view that cannot see the same truth. |
| Visual Triad | Capture model, outline, and raycast/target owner from the same position/state and support source. Rendered-model evidence is required for model claims. | Only model, only outline, only raycast, or proxy dy logs are green. |
| Placement vs survival vs neighbor/reload | Separate placement acceptance, survival predicate, neighbor update behavior, support break behavior, and reload/relog stability. | Placement succeeds but survival, neighbor update, or reload is untested. |
| Culling/render-path classification | Identify the active renderer path and whether the issue is model dy mismatch, cull-context state leakage, custom compat promotion, render-view lookup mismatch, or delegate state leakage. | The slice changes culling before a fresh RED names culling as the failing layer. |
| Targeting/rescue ownership and held-item seams | Record held item, face, hit vector, initial target, final target, expected owner, and no-rescue boundary. | Rescue broadens from generic lowered visuals or generic anchored support rather than class-owned target evidence. |
| Family coverage, not representative-only proof | Declare exact block list, variant family, and excluded variants. A representative proof must not become a family claim. | A green proof covers one representative but the report claims all variants or all related families. |
| Terrain Slabs named compat law | Keep Terrain Slabs/Countered support named, dry-bottom scoped, direct, and culling-safe. | Custom slabs are promoted into generic Slabbed support, recursive carrier scans, generic lane inheritance, or culling-sensitive paths. |
| Source-truth fixture parity | Compare source block state, anchor/carrier truth, support below/around source, held item, face, hit vector, teardown, and reload expectations against the live repro. | A proof manually grants persistent anchor/carrier truth that live did not create. |
| Release jar and bytecode hygiene | For release-facing work, run clean build, jar contents scan, and bytecode hard-reference scan. | Jar contents are clean but production bytecode still hard-links excluded dev/debug/proof classes. |
| Exact live profile, jar, and world proof | For live claims, record exact Modrinth profile, jar filename/path, Minecraft window/profile, world/save, action, and logs/screenshots. Auto/dev runs are support evidence only. | `runClient`, `runServer`, gametest, or a stale/wrong jar is called live proof. |
| Performance and hot-path budget | For render/tick/client sync changes, identify per-block, per-quad, per-frame, per-tick, packet, chunk-load, and worker-thread costs. Debug/proof logic must be default-off and cheap-first. | A slice adds broad scans, packet spam, per-block diagnostics, reflection, system-property reads, or blocking chunk access on render/light worker paths without a gate and proof. |
| One-slice savepoint discipline | One visible win or proof-complete slice must stop for an intentional savepoint when savepoints are authorized. Reports must separate proof-pending from final. | Multiple wins are stacked in one dirty tree, or a proof-only state is called fixed/final without commit/tag/push when those are required. |

## Minimum Proof Language

Use these labels instead of overclaiming:

- `compile-proven`: Java compiled only.
- `scaffold-proven`: loader/build surface compiled, no gameplay behavior proven.
- `server-persistence-proven`: server storage compiles or passes save/load proof, no client mirror implied.
- `client-mirror-proven`: client mirror receives the same truth as the server for the named route.
- `render-view-proven`: non-`Level` render views can read the needed truth.
- `triad-proven`: model, outline, and raycast agree for the named state and fixture.
- `behavior-proven`: placement, survival, neighbor update, reload, and triad proof passed for the named family.
- `live-proven`: exact real profile, jar, world, action, and evidence were captured.
- `release-ready`: release gate passed and Julia authorized release work.

Do not substitute NeoForge proof for Forge proof.
Do not substitute auto/dev runs for live proof.
Do not substitute representative proof for family proof.

## Report Template

```text
Risk checklist verdict:
Rows applicable:
Rows not applicable:
Rows blocked:
Exact legal state:
Primary layer:
Files touched:
Files intentionally untouched:
Proof run:
Proof result:
Live proof status:
Release status:
Next smallest slice:
Stop condition reached:
```
