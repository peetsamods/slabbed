# SLABBED Canonical Doctrine

Status: consolidated replacement for `SLABBED_Constitution_v2.1.md`, old `RULES.md`, scattered codemap invariants, and protected historical notes.
Updated: 2026-05-04.

## Core product intent

Slabbed exists to make slab-supported building feel physically coherent, visually correct, and mechanically trustworthy.

The central promise:

- Full blocks and other supported objects should sit where slab support says they sit.
- The model, outline, raycast, collision, placement, survival, and live feel must describe the same object.
- Slabbed behavior must come from deliberate slab-state law, not broad lies about vanilla solidity or shape semantics.

## Canonical policy: global slab support

Global slab support is intended product behavior.

Ordinary full blocks should be able to anchor to slabs. This is part of the core product, not a side experiment.

Superseded interpretation:

> “Current slab-sit categories are selective, not broad full-block anchoring.”

That statement is stale. Treat any old file implying selective-only support as historical unless it is explicitly scoped to a future category slice.

Global slab support does **not** authorize:

- global solidity lies
- blanket expansion to stairs/fences/walls/panes/trapdoors
- careless rescue broadening
- skipping visual/raycast proof
- unnamed hybrid states
- downstream rescue as a substitute for canonical state normalization

## Canonical state law

Every slab-supported placement must normalize into one legal Slabbed state before it is allowed to complete.

A placement hook must do exactly one of these:

1. produce a canonical legal Slabbed state
2. preserve a canonical vanilla state
3. reject or defer the placement

It must not create a state that only appears to work because model, outline, raycast, collision, or rescue layers compensate afterward.

A state that looks contradictory in vanilla terms may be legal in Slabbed only if it is named, documented, and proven.

## SlabSupport is semantic authority

All slab support semantics route through `SlabSupport` or another explicitly designated Slabbed authority.

Mixin code may gather context and call authority. It must not independently invent:

- lowered lanes
- slab inheritance
- anchor legality
- dy compatibility
- support eligibility
- merge outcomes
- rescue ownership rules

If a mixin needs new semantics, those semantics belong in the authority layer first.

## Legal state categories

### Legal vanilla slab states

These use vanilla dy and vanilla geometry:

- bottom slab, `dy=0`
- top slab, `dy=0`
- double slab, `dy=0`

### Legal Slabbed-lowered full-block states

Known legal forms:

- ordinary full block anchored on a bottom slab with `dy=-0.5`
- ordinary full block in a proven lowered vertical chain with `dy=-0.5`
- ordinary full block preserved by a valid persistent anchor after original support changes
- ordinary full block side-adjacent to a valid anchored/lowered full-block neighbor when authoring creates the legal lowered state

### Legal Slabbed-lowered slab lane states

Legal only inside Slabbed’s lowered lane grammar:

- side-lane `TOP` slab with `dy=-0.5`
- side-lane `BOTTOM` slab with `dy=-0.5`
- side-lane `DOUBLE` slab with `dy=-0.5`
- lowered `DOUBLE` side target lower-half placement producing `BOTTOM dy=-0.5`
- lowered `DOUBLE` side target upper-half placement producing `TOP dy=-0.5`
- lowered side-lane merge producing `DOUBLE dy=-0.5`
- lowered `TOP` up-click merge producing `DOUBLE dy=-0.5` when explicitly covered by proof
- real-placed legal lowered `BOTTOM` slab above an anchored/lowered ordinary full block, when persistent carrier truth is explicitly proven

### Illegal or suspect states

Illegal or suspect unless promoted by a future architecture slice:

- any slab type + dy pairing not listed above
- slab type whose vanilla vertical meaning conflicts with assigned dy outside lowered lane grammar
- lowered air as support truth
- lowered state inferred only from neighboring visuals without a canonical support relationship
- normal-lane `dy=0` slab produced from a valid lowered-lane interaction
- any state that exists only because rescue rewrites player targeting later

## Visual Triad Law

For any slab-lowered or slab-shifted object, these three surfaces must agree:

1. Model — rendered mesh
2. Outline — selection wireframe
3. Raycast — targeting / interaction result

`ClientDy.dyFor(world, pos, state)` is the authoritative source where client dy logic is needed.

No duplicate dy logic. No shared mutable “current dy.” If contextual state is needed, isolate it and clear it safely.

A fix that updates only one or two triad surfaces must stop.

## Collision, placement, and survival must share the same state

Placement success does not prove collision correctness.
Collision correctness does not prove survival correctness.
Survival correctness does not prove visual correctness.

Every legal Slabbed state must reason about:

- placement predicate
- collision acceptance
- survival predicate
- neighbor update behavior
- reload/relog stability
- model/outline/raycast alignment
- live feel

Acting-player-only collision exceptions may exist only as narrow placement finalization tools. They must not become broad collision lies.

## No global lies

Forbidden unless explicitly proven and regression-defended:

- broad `isSideSolid` rewrites
- broad sturdy-face lies
- broad shape/support redirects outside slab context
- global collision changes that silently accept unrelated behavior
- rescue based only on generic slab support or generic lowered visuals

A central Slabbed authority is not a global lie. A central authority defines legal Slabbed states and lets guarded hooks consult them.

## Baseline lane is sacred

The baseline includes ordinary full-block anchoring on slabs. Do not damage it.

If a change regresses ordinary full-block anchoring, stop, revert if needed, reduce scope, and re-approach.

## Manual live verification outranks automation for feel bugs

Automated proof is required, but live play is the final authority for:

- lower-half interaction feel
- rescue / crosshair targeting feel
- ghost blocks
- weird hitboxes
- moving-up behavior
- “no meaningful live difference” after a supposed fix

If automation passes but live play still fails, write a red proof that fails for the same reason live play failed before attempting another patch.

## Category discipline

Category expansion is frozen until the Slabbed Core Building Contract is stable.

When category work resumes, each category needs:

- scoped branch
- one primary support strategy
- placement hook(s)
- survival hook(s)
- visual audit
- regression sweep if shared hooks are used
- savepoint discipline

Do not mix category expansion with core contract stabilization.

## Research-first before novel architecture

Before new architecture, new category family, or novel support behavior, perform prior-art research.

Required output:

- existing prior art
- solution patterns used
- what they avoid
- known constraints and failure modes
- decision gate: direct precedent, adjacent precedent, or novel exploration

No code or hook proposal before that research summary.

## Tooling is evidence, not product intent

Slabbed Lab, gametests, screenshot capture, repro worlds, inspect logs, and audit runners exist to produce evidence. They do not define product intent.

Tooling must be dev-gated and must not leak into production behavior unless explicitly intended.

Inspect warnings must reflect current law. If a once-suspect state becomes legal, inspect tooling must stop warning on it as illegal.

## Release artifact closure

A release jar is not clean merely because debug files are absent. Packaged runtime bytecode must also not hard-link excluded dev/debug classes.

Suggested gates:

```bash
jar tf build/libs/slabbed-*.jar | rg "debug|dev|audit|gametest|test|proof|fixture|lab"
jdeps -recursive -verbose:class build/libs/slabbed-*.jar | rg "com\.slabbed\.(debug|dev)|SlabbedDebug|slabbed\.debug\.mixins"
```

## Protected historical invariants

### Carpet + global model dy coexistence

Protected pattern:

- global model shift remains in `OffsetBlockStateModel`
- carpets are a special model-dy override inside that path
- carpet outline recursion remains blocked in `SlabSupportStateMixin`
- no second competing global model-translate path without proof

Regression check order:

1. verify carpet override in `OffsetBlockStateModel`
2. verify carpet skip/protection in `SlabSupportStateMixin`
3. check for competing/double dy paths

### Lowered slab lane grammar

Protected outcomes from `save/lowered-slab-lane-grammar`:

- lowered slab chains inherit lowered lane truth through compatible slab states
- `DOUBLE` may act as compatible lowered slab-chain carrier
- lowered `DOUBLE` side-lane slabs own visible lower half
- lower-half side clicks on lowered `DOUBLE` produce `BOTTOM dy=-0.5`
- upper-half side clicks on lowered `DOUBLE` produce `TOP dy=-0.5`
- second click / merge may produce `DOUBLE dy=-0.5`
- continued lowered slab-chain placements stay at `dy=-0.5`

### Persistent carrier visibility across views

Any persistent anchor or persistent lowered slab carrier used for model/triad behavior must be readable by every view that needs it:

- real client `World`
- non-`World` render views such as chunk render regions
- model path
- outline path
- raycast path
- targeting/rescue path

A proof that only queries real `World` is insufficient when live rendering uses a different `BlockView`.

## Non-negotiable stop conditions

Stop immediately if:

- non-canonical root
- dirty staging or unrelated dirty files cannot be isolated
- build fails and cause is unclear
- mixin target/signature mismatch appears
- baseline lane changes unexpectedly
- only part of triad updated
- collision and placement disagree
- survival and placement disagree
- live play contradicts claimed fix after two serious attempts
- illegal or unnamed hybrid state is created
- legal state cannot be stated before implementation
- speculative architecture begins without research
- category scope expands mid-slice
- debug/inspect/release tooling leaks into production behavior
- release jar excludes debug classes but packaged bytecode hard-links them
- proof is obtained but savepoint is not completed and someone tries to call it final

## What done means

Done means:

- build passes
- relevant proofs pass
- live test passes when bug class requires it
- all relevant lanes pass
- model, outline, raycast, placement, collision, survival, and feel agree
- no illegal/unnamed hybrid state exists
- only intended files are committed
- tag exists and is pushed when required
- tree is clean or dirt is documented as intentionally untracked
