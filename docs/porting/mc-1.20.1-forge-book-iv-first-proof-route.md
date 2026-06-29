# MC 1.20.1 Forge Book IV First Proof Route

## Scope

Book IV docs/proof-selection gate:

```text
forge-1.20.1-book-iv-ordinary-full-block-anchor-proof-selection
```

This slice chooses the first narrow Book IV behavior/proof route after the clean
Book III loader/API savepoint:

```text
d435fcbc / save/forge-1-20-1-rendered-block-model-evidence-proof
```

It does not authorize Java, Gradle, resource, mixin, gametest, live-client,
release, commit, tag, or push work.

## Book IV Phase Goal

Prove Forge 1.20.1 behavior parity one legal state at a time, starting from the
lowest-risk global Slabbed lane already used by the Book III rendered-model
proof, without mistaking model evidence for full behavior or live proof.

## Chosen First Route

Route:

```text
forge-1.20.1-ordinary-full-block-on-bottom-slab-triad-and-behavior-proof
```

Legal state being protected:

```text
ordinary full block anchored on a bottom slab with dy=-0.5
```

Exact fixture:

```text
block/state: minecraft:stone ordinary full block
support source: bottom slab directly below
anchor truth: Slabbed ANCHOR_TYPE / Forge chunk capability truth created by the
same route a real placement would use, not manually granted proof-only truth
expected dy: -0.5
```

Primary layer for the next worker:

```text
proof gap
```

Reason:
Book III proved the Forge model wrapper returns translated baked quads for this
same named lowered fixture. Book IV must now prove behavior surfaces that Book
III deliberately did not claim: placement, survival, neighbor/reload behavior,
outline, raycast/targeting, and only then family behavior parity. Slabbed Visual
Triad law requires model, outline, and raycast to agree for the same
position/state/support source.

## Why This Is Next By Canon

- `docs/codex/02-legal-state-grammar.md` names ordinary full block on bottom
  slab with `dy=-0.5` as a legal lowered full-block state.
- `docs/codex/03-visual-triad.md` requires model, outline, and raycast to agree.
- `docs/porting/mc-1.20.1-forge-rendered-block-model-evidence-proof.md` already
  proved the model-output side for this named fixture only.
- `docs/porting/mc-1.20.1-forge-regression-risk-checklist.md` defines
  `triad-proven` and `behavior-proven` as stronger labels than
  `rendered-block-model-evidence-proven`.
- Culling is not first by default. It still needs a fresh culling RED or named
  render-path failure before any cull-face or culling patch.

## Execution Status

The first behavior proof route is selected, but it is not safe to execute from
this planning gate.

Current blocker:

```text
The Forge branch has only Book III compile-limited source inclusion and Forge
client/server run configs. Mixins, gametest/proof harness migration, and full
behavior parity proof surfaces are not yet migrated or authorized.
```

Therefore the next executable Book IV worker should start as an audit/proof-gap
slice that identifies or migrates the narrow Forge-compatible proof harness for
the exact fixture above before patching behavior.

## Required Header For Next Worker

```text
Slice: forge-1.20.1-ordinary-full-block-on-bottom-slab-triad-and-behavior-proof
Primary layer: proof gap until the Forge proof harness is named
Legal state being protected: ordinary full block anchored on a bottom slab with dy=-0.5
SlabSupport or dy authority touched: no by default
Persistence path touched: server capability, client mirror, and non-Level render-view only as evidence surfaces
Visual Triad surface touched: model / outline / raycast evidence, not model-only
Placement/survival/reload touched: placement / survival / neighbor / reload evidence before behavior-proven
Rescue/targeting touched: evidence only unless a targeting RED names rescue as the failing layer
Compat family touched: none
Proof family scope: minecraft:stone ordinary full block fixture only
Live proof required: later, if targeting feel, ghosting, moving-up behavior, or player-visible contradiction appears
Release-facing risk: no
Savepoint plan: separate savepoint only after proof-clean route or proven implementation
Stop condition: stop if proof harness migration, mixin migration, culling, live, or behavior patching broadens beyond the named fixture
```

## Allowed Dirt For This Planning Gate

- `SLABBED_SPINE.md`
- `HANDOFF.md`
- `docs/porting/mc-1.20.1-forge-foundation.md`
- `docs/porting/mc-1.20.1-forge-regression-risk-checklist.md`
- `docs/porting/mc-1.20.1-forge-rendered-model-culling-triad-decision.md`
- this proof-route document

## Forbidden Lanes

- Java, Gradle, resource, mixin, gametest, or proof-harness implementation
- culling or cull-face relocation
- custom geometry / ChainCeilingGeometry work
- full Visual Triad proof execution
- behavior parity claim
- live Minecraft/Modrinth driving
- release metadata, jars, upload, tag, push, or commit work
- other Slabbed roots

## Proof For This Planning Gate

```text
git diff --check
```

No compile, gametest, live, or release command is required or authorized for
this docs/proof-selection gate.

## Stop Condition

Stop after recording the selected Book IV route and leave any dirty docs for a
separate savepoint closure. Do not proceed into implementation from this gate.
