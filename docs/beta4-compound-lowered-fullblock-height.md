# Beta4 Compound Lowered Full Block Height

Current base: `9bf3bdc` / `save/beta4-placement-authoring-recorder`.

## Named legal state

**Compound Lowered Full Block on Lowered Bottom Slab Carrier.**

An ordinary full block placed directly above a legal lowered bottom slab
carrier must sit on the slab carrier's visual top surface. The carrier itself
already carries `dy=-0.5` via persistent lowered carrier semantics
(`SlabAnchorAttachment.isPersistentLoweredSlabCarrier`), so the placed full
block must drop an additional `-0.5` to align with the carrier top.

Required formula for any ordinary full block placed directly above a legal
bottom slab carrier:

```
fullBlockDy = sourceBottomSlabDy - 0.5
```

So:

- normal bottom slab `dy=0.0` -> full block `dy=-0.5`
- lowered bottom slab carrier `dy=-0.5` -> full block `dy=-1.0`

This is a named, documented, proven legal state. It is not a render workaround
and not a retarget hack.

## Live evidence at 9bf3bdc

Live recorder run captured at `tmp/beta4-placement-authoring-recorder-9bf3bdc`
(see `docs/beta4-live-placement-authoring-proof-gap.md`) recorded the exact
collapse:

- `heldItem=minecraft:stone`, `heldIsSlab=false`
- `clickedPos=14,-58,0`, `clickedFace=up`,
  `clicked/source block=stone_slab[type=bottom]`,
  `clicked/source dy=-0.5`,
  `clicked/source persistentLoweredSlabCarrier=true`,
  `clicked/source persistentLoweredBottomSlabCarrier=true`
- `vanillaPlacePos=14,-57,0`, `finalPlacedPos=14,-57,0`
- `place-return` (CLIENT, pre-anchor mirror): `placedDy=-1.0`,
  `placedPersistentFullBlockAnchor=false`,
  `placedSourceMode=normal`,
  `anchorFinalization=deferred_to_finalization_mixin`
- `server-after-queued-tick` / `finalization-return` (SERVER, after
  `Block.onPlaced` records the persistent full-block anchor): `placedDy=-0.5`,
  `placedPersistentFullBlockAnchor=true`,
  `placedSourceMode=dynamicLoweredOrAnchored`

Visual symptom: the freshly placed block initially renders at the correct
compound dy=-1.0 and then jumps up to dy=-0.5 once the anchor attachment
syncs to the client.

## Root cause

`SlabSupport.getYOffsetInner` returned `-0.5` from the
`SlabAnchorAttachment.isAnchored` branch before the existing compound branch
(non-slab block above an `isAdjacentSideSlabLowered` bottom slab) could
return `-1.0`. The anchor branch did not consider whether the slab below was
itself a lowered carrier, so any anchored full block sitting on a lowered
bottom slab collapsed to the generic anchored dy.

This is a placement / finalization / state-law bug, not a retarget bug, not
a model dy bug, and not a reload persistence bug.

## Fix

`SlabSupport.getYOffsetInner` now folds the same
`isAdjacentSideSlabLowered`-aware compound check into the anchor branch for
non-slab blocks. When the block below is a bottom slab and that slab is in
the lowered lane (persistent lowered slab carrier or adjacent-side-slab
lowered), the anchor branch returns `-1.0` instead of `-0.5`. Otherwise it
keeps returning `-0.5` for normal anchored full blocks.

The fix uses the existing central `isAdjacentSideSlabLowered` authority and
does not invent local mixin-only dy logic, does not broaden generic
full-block anchoring, does not change retarget/rescue, and does not change
raycast / outline / model behavior directly. Existing torch and non-anchored
full-block compound paths already return `-1.0` via the same predicate; this
extends that legal compound state to the anchored full-block path so model,
outline, raycast, and live feel agree.

## RED / GREEN proof

Opt-in property: `slabbed.beta4CompoundLoweredFullBlockCollapseRedOnly`.

Markers:

- `[BETA4_COMPOUND_LOWERED_FULL_BLOCK_COLLAPSE_RED]`
- `[BETA4_COMPOUND_LOWERED_FULL_BLOCK_COLLAPSE_GREEN]`

The proof in
`SlabbedLabBeta4CompoundLoweredFullBlockCollapseClientGameTest` seeds a
legal lowered bottom slab carrier (bottom slab on top of an anchored
ordinary full block), then sets and anchors an ordinary stone block directly
above it through the same `SlabAnchorAttachment.addAnchor` call that
`BlockOnPlacedAnchorMixin` uses on `Block.onPlaced`. It waits for sync,
asserts the source slab is a legal `persistentLoweredBottomSlabCarrier` with
`dy=-0.5` on both server and client, and then verifies the placed block
stable dy is `-1.0` on both sides.

If post-finalization stable dy is `-0.5`, the proof emits
`[BETA4_COMPOUND_LOWERED_FULL_BLOCK_COLLAPSE_RED]` and throws. After the
fix it emits `[BETA4_COMPOUND_LOWERED_FULL_BLOCK_COLLAPSE_GREEN]` and
passes.

## Non-negotiables

- Named legal state, not a fake dy.
- Normal anchored full block dy=-0.5 stays GREEN.
- Vertical chain proofs, lowered slab carrier persistence, retarget rescue
  rules, raycast/outline/model behavior all stay unchanged.
- Release remains blocked pending Julia live retest of the freshly placed
  ordinary block on a lowered bottom slab carrier.
