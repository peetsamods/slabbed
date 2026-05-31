# 02 — Legal State Grammar

Slabbed must define legal states before implementation. A placement hook must produce a canonical legal Slabbed state, preserve a canonical vanilla state, or reject/defer the placement. It must not create a state that only works because rendering, outline, raycast, collision, or rescue compensates afterward.

## Legal vanilla slab states

These use vanilla dy and vanilla geometry:

- bottom slab, `dy=0`
- top slab, `dy=0`
- double slab, `dy=0`

## Legal lowered full-block states

Known legal forms:

- ordinary full block anchored on a bottom slab with `dy=-0.5`
- ordinary full block in a proven lowered vertical chain with `dy=-0.5`
- ordinary full block preserved by a valid persistent anchor after original support changes
- ordinary full block side-adjacent to a valid anchored/lowered full-block neighbor when authoring creates the legal lowered state

## Legal lowered slab lane states

Legal only inside named lowered-lane grammar:

- side-lane `TOP` slab with `dy=-0.5`
- side-lane `BOTTOM` slab with `dy=-0.5`
- side-lane `DOUBLE` slab with `dy=-0.5`
- lowered `DOUBLE` side target lower-half placement producing `BOTTOM dy=-0.5`
- lowered `DOUBLE` side target upper-half placement producing `TOP dy=-0.5`
- lowered side-lane merge producing `DOUBLE dy=-0.5`
- lowered `TOP` up-click merge producing `DOUBLE dy=-0.5` only when proof-backed
- real-placed legal lowered `BOTTOM` slab above an anchored/lowered ordinary full block when persistent carrier truth is proven

## Suspect or illegal states

Illegal unless promoted by a future architecture slice:

- slab type + dy pair not listed above
- slab type whose vanilla vertical meaning conflicts with assigned dy outside lowered lane grammar
- lowered air as support truth
- lowered state inferred only from neighboring visuals without canonical support relationship
- normal-lane `dy=0` slab produced from valid lowered-lane interaction
- any state that exists only because rescue rewrites player targeting later
- compound `dy=-1.0` slab lanes unless a current decision contract explicitly legalizes them

## Compound depth caution

A `dy=-1.0` full-block lane is not merely “one more offset.” It is a second authored visual lane and needs explicit source/depth law. Do not legalize compound slab lanes casually. If no current contract names the state, reject or audit.

## Required answer before patching

Before editing Java, state:

```text
Legal state being protected:
Block/state:
Expected dy:
Support source:
Persistence rule:
Placement rule:
Survival rule:
Model/outline/raycast rule:
Live-feel expectation:
```
